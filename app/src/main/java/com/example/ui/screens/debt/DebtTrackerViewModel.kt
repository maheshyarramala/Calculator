package com.example.ui.screens.debt

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.DebtRecord
import com.example.data.local.entity.ExpenseRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DebtTab(val title: String) {
  LENT("People Who Owe Me"),
  BORROWED("People I Owe"),
  SETTLED("Settled History")
}

data class DebtTrackerUiState(
  val allDebts: List<DebtRecord> = emptyList(),
  val activeLentDebts: List<DebtRecord> = emptyList(),
  val activeBorrowedDebts: List<DebtRecord> = emptyList(),
  val settledDebts: List<DebtRecord> = emptyList(),
  val totalLentReceivable: Double = 0.0,
  val totalBorrowedPayable: Double = 0.0,
  val netBalance: Double = 0.0,
  val selectedTab: DebtTab = DebtTab.LENT,
  val searchQuery: String = "",
  val bankAccounts: List<BankAccount> = emptyList(),
  val showAddDebtDialog: Boolean = false,
  val debtToSettle: DebtRecord? = null,
  val userMessage: String? = null
)

class DebtTrackerViewModel(application: Application) : AndroidViewModel(application) {

  private val database = AppDatabase.getDatabase(application)
  private val debtDao = database.debtDao()
  private val financeDao = database.financeDao()

  private val _selectedTab = MutableStateFlow(DebtTab.LENT)
  private val _searchQuery = MutableStateFlow("")
  private val _showAddDebtDialog = MutableStateFlow(false)
  private val _debtToSettle = MutableStateFlow<DebtRecord?>(null)
  private val _userMessage = MutableStateFlow<String?>(null)

  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  private data class LocalFilterParams(
    val tab: DebtTab,
    val query: String,
    val showAdd: Boolean,
    val settleDebt: DebtRecord?
  )

  private val filterParamsFlow = combine(
    _selectedTab,
    _searchQuery,
    _showAddDebtDialog,
    _debtToSettle
  ) { tab, query, showAdd, settleDebt ->
    LocalFilterParams(tab, query, showAdd, settleDebt)
  }

  val uiState: StateFlow<DebtTrackerUiState> = combine(
    debtDao.getAllDebts(),
    financeDao.getAllBankAccounts(),
    filterParamsFlow
  ) { debts, bankAccounts, params ->
    val lentActive = debts.filter { it.type == "LENT" && !it.isSettled }
    val borrowedActive = debts.filter { it.type == "BORROWED" && !it.isSettled }
    val settledList = debts.filter { it.isSettled }

    val totalLent = lentActive.sumOf { it.amount }
    val totalBorrowed = borrowedActive.sumOf { it.amount }
    val net = totalLent - totalBorrowed

    // Apply filter and search
    val currentTabList = when (params.tab) {
      DebtTab.LENT -> lentActive
      DebtTab.BORROWED -> borrowedActive
      DebtTab.SETTLED -> settledList
    }

    val filteredList = if (params.query.isBlank()) {
      currentTabList
    } else {
      currentTabList.filter {
        it.personName.contains(params.query, ignoreCase = true) ||
            it.notes.contains(params.query, ignoreCase = true)
      }
    }

    DebtTrackerUiState(
      allDebts = filteredList,
      activeLentDebts = lentActive,
      activeBorrowedDebts = borrowedActive,
      settledDebts = settledList,
      totalLentReceivable = totalLent,
      totalBorrowedPayable = totalBorrowed,
      netBalance = net,
      selectedTab = params.tab,
      searchQuery = params.query,
      bankAccounts = bankAccounts,
      showAddDebtDialog = params.showAdd,
      debtToSettle = params.settleDebt
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = DebtTrackerUiState()
  )

  fun selectTab(tab: DebtTab) {
    _selectedTab.value = tab
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setShowAddDebtDialog(show: Boolean) {
    _showAddDebtDialog.value = show
  }

  fun setDebtToSettle(debt: DebtRecord?) {
    _debtToSettle.value = debt
  }

  fun clearUserMessage() {
    _userMessage.value = null
  }

  fun addDebt(
    personName: String,
    amount: Double,
    type: String, // "LENT" or "BORROWED"
    dueDateTimestamp: Long?,
    notes: String,
    bankAccountId: Long?,
    bankAccountName: String,
    adjustBankBalanceNow: Boolean
  ) {
    viewModelScope.launch {
      val debt = DebtRecord(
        personName = personName.trim(),
        amount = amount,
        type = type,
        dateTimestamp = System.currentTimeMillis(),
        dueDateTimestamp = dueDateTimestamp,
        isSettled = false,
        notes = notes.trim(),
        bankAccountId = bankAccountId,
        bankAccountName = bankAccountName
      )
      debtDao.insertDebt(debt)

      // Optionally adjust account balance and log to Finance Hub immediately
      if (adjustBankBalanceNow && bankAccountId != null) {
        val allAccounts = uiState.value.bankAccounts
        val target = allAccounts.find { it.id == bankAccountId }
        if (target != null) {
          if (type == "LENT") {
            // Money left my account to give to them
            val updated = target.copy(balance = target.balance - amount)
            financeDao.insertBankAccount(updated)
            financeDao.insertExpense(
              ExpenseRecord(
                title = "Lent to $personName",
                amount = amount,
                categoryName = "Bills & Utilities",
                type = "EXPENSE",
                bankAccountId = bankAccountId,
                bankAccountName = bankAccountName,
                notes = "Recorded in Personal Lend & Borrow Tracker"
              )
            )
          } else {
            // Money entered my account from them
            val updated = target.copy(balance = target.balance + amount)
            financeDao.insertBankAccount(updated)
            financeDao.insertExpense(
              ExpenseRecord(
                title = "Borrowed from $personName",
                amount = amount,
                categoryName = "Bills & Utilities",
                type = "INCOME",
                bankAccountId = bankAccountId,
                bankAccountName = bankAccountName,
                notes = "Recorded in Personal Lend & Borrow Tracker"
              )
            )
          }
        }
      }

      _showAddDebtDialog.value = false
      _userMessage.value = "Transaction with $personName saved successfully"
    }
  }

  fun markAsSettled(
    debt: DebtRecord,
    adjustBankBalance: Boolean,
    bankAccountId: Long?,
    bankAccountName: String
  ) {
    viewModelScope.launch {
      debtDao.markSettled(debt.id)

      if (adjustBankBalance && bankAccountId != null) {
        val allAccounts = uiState.value.bankAccounts
        val target = allAccounts.find { it.id == bankAccountId }
        if (target != null) {
          if (debt.type == "LENT") {
            // Settled: They returned the money to me -> Income into my account
            val updated = target.copy(balance = target.balance + debt.amount)
            financeDao.insertBankAccount(updated)
            financeDao.insertExpense(
              ExpenseRecord(
                title = "Settlement: ${debt.personName} repaid",
                amount = debt.amount,
                categoryName = "Bills & Utilities",
                type = "INCOME",
                bankAccountId = bankAccountId,
                bankAccountName = bankAccountName,
                notes = "Settled debt from Udhaar ledger"
              )
            )
          } else {
            // Settled: I paid them back -> Expense from my account
            val updated = target.copy(balance = target.balance - debt.amount)
            financeDao.insertBankAccount(updated)
            financeDao.insertExpense(
              ExpenseRecord(
                title = "Settlement: Repaid ${debt.personName}",
                amount = debt.amount,
                categoryName = "Bills & Utilities",
                type = "EXPENSE",
                bankAccountId = bankAccountId,
                bankAccountName = bankAccountName,
                notes = "Settled debt from Udhaar ledger"
              )
            )
          }
        }
      }

      _debtToSettle.value = null
      _userMessage.value = "Marked debt with ${debt.personName} as settled!"
    }
  }

  fun deleteDebt(debt: DebtRecord) {
    viewModelScope.launch {
      debtDao.deleteDebt(debt)
      _userMessage.value = "Transaction removed"
    }
  }

  /**
   * Generates a preformatted WhatsApp/SMS reminder message and triggers Android Share Sheet.
   */
  fun sendReminder(context: Context, debt: DebtRecord) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(Date(debt.dateTimestamp))
    val dueDateStr = debt.dueDateTimestamp?.let { " (Due: ${dateFormat.format(Date(it))})" } ?: ""

    val reminderMessage = if (debt.type == "LENT") {
      "Hi ${debt.personName},\n\nThis is a gentle reminder regarding the ₹${"%.0f".format(debt.amount)} lent on $dateStr$dueDateStr.\n\nPlease settle it when convenient. Thank you! 🙏"
    } else {
      "Hi ${debt.personName},\n\nJust a quick note regarding the ₹${"%.0f".format(debt.amount)} I borrowed on $dateStr$dueDateStr. Let me know the preferred payment method to settle this. Thanks! 🙏"
    }

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, "Payment Reminder from OmniCalc")
      putExtra(Intent.EXTRA_TEXT, reminderMessage)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Send Payment Reminder via"))
  }
}
