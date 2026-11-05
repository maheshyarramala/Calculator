package com.example.ui.screens.loan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountWithBalance
import com.example.data.local.entity.LoanRecord
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoanTrackerUiState(
  val loans: List<LoanRecord> = emptyList(),
  val activeLoans: List<LoanRecord> = emptyList(),
  val completedLoans: List<LoanRecord> = emptyList(),
  val totalActiveLoansCount: Int = 0,
  val totalPrincipalRemaining: Double = 0.0,
  val totalMonthlyEmiOutflow: Double = 0.0,
  val totalPrincipalPaid: Double = 0.0,
  val totalInterestPayable: Double = 0.0,
  val bankAccounts: List<AccountWithBalance> = emptyList(),
  val selectedCategoryFilter: String? = null,
  val showAddLoanDialog: Boolean = false,
  val selectedLoanDetail: LoanRecord? = null
)

class LoanTrackerViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = FinanceRepository.getInstance(application)
  private val database = AppDatabase.getDatabase(application)
  private val loanDao = database.loanDao()

  private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
  private val _showAddLoanDialog = MutableStateFlow(false)
  private val _selectedLoanDetail = MutableStateFlow<LoanRecord?>(null)
  private val _userMessage = MutableStateFlow<String?>(null)

  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  val uiState: StateFlow<LoanTrackerUiState> = combine(
    repository.allLoans,
    repository.accountsWithBalances,
    _selectedCategoryFilter,
    _showAddLoanDialog,
    _selectedLoanDetail
  ) { loans, bankAccounts, categoryFilter, showAdd, selectedLoan ->
    val active = loans.filter { !it.isFullyPaid }
    val completed = loans.filter { it.isFullyPaid }

    val filteredLoans = if (categoryFilter.isNullOrBlank()) {
      loans
    } else {
      loans.filter { it.loanCategory.equals(categoryFilter, ignoreCase = true) }
    }

    // Dynamic Summary Boxes:
    // - Monthly EMI Outflow: Sums installments of strictly active loans
    // - Principal Paid: Aggregates cumulative principal component from paid EMIs across all loans
    val totalRemainingPrincipal = active.sumOf { it.remainingPrincipal }
    val totalMonthlyOutflow = active.sumOf { it.activeMonthlyOutflow }
    val totalPaid = loans.sumOf { it.cumulativePrincipalPaid }
    val totalInterest = loans.sumOf { it.totalInterestPayable }

    // Keep selected loan fresh if updated
    val refreshedSelectedLoan = selectedLoan?.let { current ->
      loans.find { it.id == current.id }
    }

    LoanTrackerUiState(
      loans = filteredLoans,
      activeLoans = active,
      completedLoans = completed,
      totalActiveLoansCount = active.size,
      totalPrincipalRemaining = totalRemainingPrincipal,
      totalMonthlyEmiOutflow = totalMonthlyOutflow,
      totalPrincipalPaid = totalPaid,
      totalInterestPayable = totalInterest,
      bankAccounts = bankAccounts,
      selectedCategoryFilter = categoryFilter,
      showAddLoanDialog = showAdd,
      selectedLoanDetail = refreshedSelectedLoan
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = LoanTrackerUiState()
  )

  fun setCategoryFilter(category: String?) {
    _selectedCategoryFilter.value = category
  }

  fun setShowAddLoanDialog(show: Boolean) {
    _showAddLoanDialog.value = show
  }

  fun selectLoanForDetail(loan: LoanRecord?) {
    _selectedLoanDetail.value = loan
  }

  fun clearUserMessage() {
    _userMessage.value = null
  }

  fun addLoan(
    name: String,
    category: String,
    principal: Double,
    annualRate: Double,
    tenureMonths: Int,
    customEmi: Double?,
    startDate: String,
    paidEmis: Int = 0,
    notes: String = "",
    bankAccountId: Long? = null,
    bankAccountName: String = "",
    dueDayOfMonth: Int = 1
  ) {
    viewModelScope.launch {
      val calculatedEmi = customEmi ?: LoanRecord.calculateEmi(principal, annualRate, tenureMonths)
      val newLoan = LoanRecord(
        loanName = name.trim(),
        loanCategory = category,
        principalAmount = principal,
        annualInterestRate = annualRate,
        tenureMonths = tenureMonths,
        emiAmount = calculatedEmi,
        startDate = startDate.ifBlank { "Today" },
        paidEmisCount = paidEmis.coerceIn(0, tenureMonths),
        notes = notes.trim(),
        bankAccountId = bankAccountId,
        bankAccountName = bankAccountName,
        dueDayOfMonth = dueDayOfMonth.coerceIn(1, 31)
      )
      val insertedId = loanDao.insertLoan(newLoan)
      val createdLoan = newLoan.copy(id = insertedId)
      com.example.domain.notification.CreditCardBillNotificationScheduler.scheduleLoanReminder(
        getApplication(),
        createdLoan
      )
      _showAddLoanDialog.value = false
      _userMessage.value = "Loan '${newLoan.loanName}' added successfully"
    }
  }

  fun deleteLoan(loan: LoanRecord) {
    viewModelScope.launch {
      com.example.domain.notification.CreditCardBillNotificationScheduler.cancelLoan(getApplication(), loan.id)
      loanDao.deleteLoan(loan)
      if (_selectedLoanDetail.value?.id == loan.id) {
        _selectedLoanDetail.value = null
      }
      _userMessage.value = "Loan '${loan.loanName}' deleted"
    }
  }

  data class LastPaidEmi(
    val loanId: Long,
    val expenseId: Long,
    val emiNumber: Int,
    val loanName: String
  )

  var lastPaidEmi: LastPaidEmi? = null
    private set

  /**
   * Auto-increments paid EMI count, logs corresponding expense into Finance Hub ledger,
   * and updates real-time balance across the entire app.
   */
  fun payMonthlyEmi(
    loan: LoanRecord,
    selectedBankAccountId: Long?
  ) {
    viewModelScope.launch {
      val result = repository.payLoanEmi(loan, selectedBankAccountId)
      result.onSuccess { info ->
        lastPaidEmi = LastPaidEmi(
          loanId = info.loanId,
          expenseId = info.expenseId,
          emiNumber = info.emiNumber,
          loanName = info.loanName
        )
        loanDao.getLoanById(info.loanId)?.let { updatedLoan ->
          if (updatedLoan.isFullyPaid) {
            com.example.domain.notification.CreditCardBillNotificationScheduler.cancelLoan(
              getApplication(),
              updatedLoan.id
            )
          } else {
            com.example.domain.notification.CreditCardBillNotificationScheduler.scheduleLoanReminder(
              getApplication(),
              updatedLoan
            )
          }
        }
        _userMessage.value = info.message
      }.onFailure { err ->
        _userMessage.value = err.message ?: "Failed to record EMI payment"
      }
    }
  }

  /**
   * Undoes the last paid EMI for a loan or the latest overall payment:
   * Decrements paid count, removes corresponding expense from Finance Hub,
   * and reactively refreshes all dashboards and balances.
   */
  fun undoLastPaidEmi(loanId: Long? = null) {
    val targetLoanId = loanId ?: lastPaidEmi?.loanId ?: return
    val targetExpenseId = if (targetLoanId == lastPaidEmi?.loanId) lastPaidEmi?.expenseId else null
    viewModelScope.launch {
      val result = repository.undoPayLoanEmi(targetLoanId, targetExpenseId)
      lastPaidEmi = null
      result.onSuccess { msg ->
        loanDao.getLoanById(targetLoanId)?.let { updatedLoan ->
          com.example.domain.notification.CreditCardBillNotificationScheduler.scheduleLoanReminder(
            getApplication(),
            updatedLoan
          )
        }
        _userMessage.value = msg
      }.onFailure { err ->
        _userMessage.value = err.message ?: "Failed to undo EMI payment"
      }
    }
  }
}
