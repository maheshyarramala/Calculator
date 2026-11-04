package com.example.ui.screens.loan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.LoanRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
  val bankAccounts: List<BankAccount> = emptyList(),
  val selectedCategoryFilter: String? = null,
  val showAddLoanDialog: Boolean = false,
  val selectedLoanDetail: LoanRecord? = null,
  val userMessage: String? = null
)

class LoanTrackerViewModel(application: Application) : AndroidViewModel(application) {

  private val database = AppDatabase.getDatabase(application)
  private val loanDao = database.loanDao()
  private val financeDao = database.financeDao()

  private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
  private val _showAddLoanDialog = MutableStateFlow(false)
  private val _selectedLoanDetail = MutableStateFlow<LoanRecord?>(null)
  private val _userMessage = MutableStateFlow<String?>(null)

  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  val uiState: StateFlow<LoanTrackerUiState> = combine(
    loanDao.getAllLoans(),
    financeDao.getAllBankAccounts(),
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

    val totalRemainingPrincipal = active.sumOf { it.estimatedRemainingPrincipal }
    val totalMonthlyOutflow = active.sumOf { it.emiAmount }
    val totalPaid = loans.sumOf { it.estimatedPrincipalPaid }
    val totalInterest = loans.sumOf { it.totalInterestPayable }

    // Keep selected loan fresh if it was updated
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
    bankAccountName: String = ""
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
        bankAccountName = bankAccountName
      )
      loanDao.insertLoan(newLoan)
      _showAddLoanDialog.value = false
      _userMessage.value = "Loan '${newLoan.loanName}' added successfully"
    }
  }

  fun deleteLoan(loan: LoanRecord) {
    viewModelScope.launch {
      loanDao.deleteLoan(loan)
      if (_selectedLoanDetail.value?.id == loan.id) {
        _selectedLoanDetail.value = null
      }
      _userMessage.value = "Loan '${loan.loanName}' deleted"
    }
  }

  /**
   * Auto-increments paid EMI count and logs a corresponding expense into Finance Hub ledger.
   */
  fun payMonthlyEmi(
    loan: LoanRecord,
    selectedBankAccountId: Long?,
    selectedBankAccountName: String
  ) {
    viewModelScope.launch {
      if (loan.isFullyPaid) {
        _userMessage.value = "Loan '${loan.loanName}' is already fully paid!"
        return@launch
      }

      // 1. Increment paid count in Room
      loanDao.incrementPaidEmis(loan.id)

      // 2. Log expense in Finance Hub ledger
      val bankId = selectedBankAccountId ?: loan.bankAccountId
      val bankName = if (!selectedBankAccountName.isBlank()) selectedBankAccountName else loan.bankAccountName

      val expenseRecord = ExpenseRecord(
        title = "EMI: ${loan.loanName} (#${loan.paidEmisCount + 1})",
        amount = loan.emiAmount,
        categoryName = "Bills & Utilities",
        type = "EXPENSE",
        bankAccountId = bankId,
        bankAccountName = bankName,
        dateTimestamp = System.currentTimeMillis(),
        notes = "Auto-logged from Loan & EMI Tracker. ${loan.tenureMonths - (loan.paidEmisCount + 1)} EMIs left."
      )
      financeDao.insertExpense(expenseRecord)

      // 3. Update active bank account balance if linked
      if (bankId != null) {
        val allAccounts = uiState.value.bankAccounts
        val targetAccount = allAccounts.find { it.id == bankId }
        if (targetAccount != null) {
          val updatedAccount = targetAccount.copy(balance = targetAccount.balance - loan.emiAmount)
          financeDao.insertBankAccount(updatedAccount)
        }
      }

      _userMessage.value = "EMI of ₹${"%.0f".format(loan.emiAmount)} paid & recorded in Finance Hub!"
    }
  }
}
