package com.example.ui.screens.finance

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.CreditCardBillReminder
import com.example.data.local.entity.DebtRecord
import com.example.data.local.entity.ExpenseCategory
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.LoanRecord
import com.example.data.local.entity.TripRecord
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.example.domain.backup.BackupParseResult
import com.example.domain.backup.FinanceBackupManager
import com.example.domain.backup.FinanceBackupPayload
import com.example.data.local.entity.SubscriptionRecord
import com.example.domain.notification.CreditCardBillNotificationScheduler
import com.example.domain.finance.BankSmsParser
import org.json.JSONObject
import java.util.Calendar
import android.net.Uri
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

enum class FinanceTab(val title: String) {
  EXPENSES("Expenses & Accounts"),
  CREDIT_CARDS("Credit Cards"),
  SUBSCRIPTIONS("Recurring & Bills"),
  DEBTS("Lend & Borrow"),
  TRIPS("Trip Manager")
}

data class CategorySpend(
  val categoryName: String,
  val colorHex: String,
  val totalAmount: Double,
  val percentage: Double
)

data class FinanceUiState(
  val selectedTab: FinanceTab = FinanceTab.EXPENSES,
  val showAddCategoryDialog: Boolean = false,
  val showAddExpenseDialog: Boolean = false,
  val showAddIncomeDialog: Boolean = false,
  val showAddBankDialog: Boolean = false,
  val showAddTripDialog: Boolean = false,
  val showAddSubscriptionDialog: Boolean = false,
  val showSetBudgetDialog: Boolean = false,
  val selectedTripForCategoryCap: TripRecord? = null,
  val selectedAccountFilterId: Long? = null,
  val showBackupModal: Boolean = false,
  val showPasteJsonDialog: Boolean = false,
  val replaceExistingOnImport: Boolean = false,
  val toastMessage: String? = null
)

class FinanceHubViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = com.example.data.repository.FinanceRepository.getInstance(application)
  private val database = AppDatabase.getDatabase(application)
  private val dao = database.financeDao()
  private val loanDao = database.loanDao()
  private val debtDao = database.debtDao()

  private val _uiState = MutableStateFlow(FinanceUiState())
  val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

  val categories: StateFlow<List<ExpenseCategory>> = dao.getAllCategories()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val expenses: StateFlow<List<ExpenseRecord>> = repository.allExpenses
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val bankAccounts: StateFlow<List<BankAccount>> = repository.rawBankAccounts
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val creditCardBillReminders: StateFlow<List<CreditCardBillReminder>> =
    dao.getAllCreditCardBillReminders()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val trips: StateFlow<List<TripRecord>> = dao.getAllTrips()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val subscriptions: StateFlow<List<SubscriptionRecord>> = dao.getAllSubscriptions()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allLoans: StateFlow<List<com.example.data.local.entity.LoanRecord>> = repository.allLoans
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allDebts: StateFlow<List<com.example.data.local.entity.DebtRecord>> = repository.allDebts
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val currentMonthCategorySpend: StateFlow<List<CategorySpend>> = combine(
    repository.allExpenses,
    categories
  ) { exps, cats ->
    val cal = Calendar.getInstance()
    val currMonth = cal.get(Calendar.MONTH)
    val currYear = cal.get(Calendar.YEAR)
    val monthExpenses = exps.filter { item ->
      if (item.type != "EXPENSE") return@filter false
      val itemCal = Calendar.getInstance().apply { timeInMillis = item.dateTimestamp }
      itemCal.get(Calendar.MONTH) == currMonth && itemCal.get(Calendar.YEAR) == currYear
    }
    val total = monthExpenses.sumOf { it.amount }
    if (total <= 0.0) return@combine emptyList()

    val colorMap = cats.associate { it.name to it.colorHex }

    monthExpenses.groupBy { it.categoryName }
      .map { (catName, list) ->
        val sum = list.sumOf { it.amount }
        val pct = (sum / total) * 100.0
        CategorySpend(
          categoryName = catName,
          colorHex = colorMap[catName] ?: "#6366F1",
          totalAmount = sum,
          percentage = pct
        )
      }
      .sortedByDescending { it.totalAmount }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val prefs = application.getSharedPreferences("omni_calc_finance_prefs", Context.MODE_PRIVATE)
  private val _monthlyBudgetLimit = MutableStateFlow(prefs.getFloat("monthly_budget_limit", 50000f).toDouble())
  val monthlyBudgetLimit: StateFlow<Double> = _monthlyBudgetLimit.asStateFlow()

  // Centralized accounts with live balances from FinanceRepository
  val accountsWithBalances: StateFlow<List<com.example.data.local.entity.AccountWithBalance>> =
    repository.accountsWithBalances

  val totalNetBalance: StateFlow<Double> = repository.totalNetBalance
  val totalIncome: StateFlow<Double> = repository.totalIncome
  val totalExpense: StateFlow<Double> = repository.totalExpense
  val currentMonthExpenses: StateFlow<Double> = repository.currentMonthExpenses

  // Ensure default categories exist if empty
  init {
    viewModelScope.launch {
      val existing = dao.getAllCategories().first()
      if (existing.isEmpty()) {
        val defaults = listOf(
          ExpenseCategory(name = "Food & Dining", colorHex = "#EF4444", iconName = "restaurant"),
          ExpenseCategory(name = "Shopping", colorHex = "#EC4899", iconName = "shopping_bag"),
          ExpenseCategory(name = "Fuel & Transport", colorHex = "#F59E0B", iconName = "directions_car"),
          ExpenseCategory(name = "Bills & Utilities", colorHex = "#3B82F6", iconName = "receipt_long"),
          ExpenseCategory(name = "Entertainment", colorHex = "#8B5CF6", iconName = "movie"),
          ExpenseCategory(name = "Health & Medical", colorHex = "#10B981", iconName = "medical_services"),
          ExpenseCategory(name = "Travel & Trips", colorHex = "#06B6D4", iconName = "flight"),
          ExpenseCategory(name = "Investments", colorHex = "#4F46E5", iconName = "trending_up")
        )
        dao.insertDefaultCategories(defaults)
      }
    }
  }

  fun selectTab(tab: FinanceTab) {
    _uiState.update { it.copy(selectedTab = tab) }
  }

  fun setShowAddCategory(show: Boolean) {
    _uiState.update { it.copy(showAddCategoryDialog = show) }
  }

  fun setShowAddExpense(show: Boolean) {
    _uiState.update { it.copy(showAddExpenseDialog = show) }
  }

  fun setShowAddIncome(show: Boolean) {
    _uiState.update { it.copy(showAddIncomeDialog = show) }
  }

  fun setSelectedAccountFilter(id: Long?) {
    _uiState.update { it.copy(selectedAccountFilterId = id) }
  }

  fun setShowAddBank(show: Boolean) {
    _uiState.update { it.copy(showAddBankDialog = show) }
  }

  fun setShowAddTrip(show: Boolean) {
    _uiState.update { it.copy(showAddTripDialog = show) }
  }

  fun setShowAddSubscription(show: Boolean) {
    _uiState.update { it.copy(showAddSubscriptionDialog = show) }
  }

  fun setShowSetBudget(show: Boolean) {
    _uiState.update { it.copy(showSetBudgetDialog = show) }
  }

  fun setSelectedTripForCategoryCap(trip: TripRecord?) {
    _uiState.update { it.copy(selectedTripForCategoryCap = trip) }
  }

  fun setMonthlyBudgetLimit(limit: Double) {
    if (limit > 0) {
      prefs.edit().putFloat("monthly_budget_limit", limit.toFloat()).apply()
      _monthlyBudgetLimit.value = limit
      _uiState.update { it.copy(showSetBudgetDialog = false, toastMessage = "Monthly budget set to ₹${limit.toLong()}") }
    }
  }

  fun clearToast() {
    _uiState.update { it.copy(toastMessage = null) }
  }

  fun showToast(msg: String) {
    _uiState.update { it.copy(toastMessage = msg) }
  }

  fun logFromClipboardSms(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val smsText = clipboard?.primaryClip
      ?.takeIf { it.itemCount > 0 }
      ?.getItemAt(0)
      ?.coerceToText(context)
      ?.toString()
      .orEmpty()
    val parsed = BankSmsParser.parseSms(smsText, bankAccounts.value)
    if (parsed == null) {
      showToast("No valid bank/UPI SMS found in clipboard")
      return
    }

    if (parsed.isIncome) {
      addIncome(
        parsed.merchantOrTitle,
        parsed.amount,
        "Income",
        parsed.matchedAccountId,
        parsed.matchedAccountName,
        parsed.rawNotes
      )
    } else {
      addExpense(
        parsed.merchantOrTitle,
        parsed.amount,
        parsed.suggestedCategory,
        parsed.matchedAccountId,
        parsed.matchedAccountName,
        parsed.rawNotes
      )
    }
  }

  // --- Category Actions ---
  fun addCategory(name: String, colorHex: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      dao.insertCategory(
        ExpenseCategory(
          name = name.trim(),
          colorHex = colorHex,
          iconName = "category",
          isCustom = true
        )
      )
      _uiState.update { it.copy(showAddCategoryDialog = false, toastMessage = "Category '$name' added!") }
    }
  }

  fun deleteCategory(category: ExpenseCategory) {
    viewModelScope.launch {
      dao.deleteCategory(category)
      _uiState.update { it.copy(toastMessage = "Category '${category.name}' removed") }
    }
  }

  // --- Expense & Income Actions ---
  fun addExpense(
    title: String,
    amount: Double,
    category: String,
    bankAccountId: Long?,
    bankAccountName: String,
    notes: String,
    tripId: Long? = null
  ) {
    if (title.isBlank() || amount <= 0.0) return
    viewModelScope.launch {
      dao.insertExpense(
        ExpenseRecord(
          title = title.trim(),
          amount = amount,
          categoryName = category,
          type = "EXPENSE",
          bankAccountId = bankAccountId,
          bankAccountName = bankAccountName,
          notes = notes,
          tripId = tripId
        )
      )
      _uiState.update { it.copy(showAddExpenseDialog = false, toastMessage = "Expense logged successfully!") }
    }
  }

  fun addIncome(
    title: String,
    amount: Double,
    category: String,
    bankAccountId: Long?,
    bankAccountName: String,
    notes: String
  ) {
    if (title.isBlank() || amount <= 0.0) return
    viewModelScope.launch {
      dao.insertExpense(
        ExpenseRecord(
          title = title.trim(),
          amount = amount,
          categoryName = category.ifBlank { "Income" },
          type = "INCOME",
          bankAccountId = bankAccountId,
          bankAccountName = bankAccountName,
          notes = notes
        )
      )
      _uiState.update { it.copy(showAddIncomeDialog = false, toastMessage = "Income added to $bankAccountName!") }
    }
  }

  fun transferBetweenAccounts(
    fromAccount: BankAccount,
    toAccount: BankAccount,
    amount: Double,
    notes: String = ""
  ) {
    if (!amount.isFinite() || amount <= 0.0) {
      showToast("Enter a transfer amount greater than zero.")
      return
    }
    if (fromAccount.id == toAccount.id) {
      showToast("Choose two different accounts to transfer between.")
      return
    }

    viewModelScope.launch {
      val currentAccounts = bankAccounts.value
      val source = currentAccounts.firstOrNull { it.id == fromAccount.id }
      val destination = currentAccounts.firstOrNull { it.id == toAccount.id }
      if (source == null || destination == null) {
        _uiState.update { it.copy(toastMessage = "Choose two existing accounts to transfer between.") }
        return@launch
      }

      val timestamp = System.currentTimeMillis()
      dao.insertExpenses(
        listOf(
          ExpenseRecord(
            title = "Transfer to ${destination.bankName}",
            amount = amount,
            categoryName = "Transfer",
            type = "TRANSFER_OUT",
            bankAccountId = source.id,
            bankAccountName = source.bankName,
            dateTimestamp = timestamp,
            notes = notes
          ),
          ExpenseRecord(
            title = "Transfer from ${source.bankName}",
            amount = amount,
            categoryName = "Transfer",
            type = "TRANSFER_IN",
            bankAccountId = destination.id,
            bankAccountName = destination.bankName,
            dateTimestamp = timestamp + 1,
            notes = notes
          )
        )
      )
      _uiState.update {
        it.copy(
          toastMessage = "Transferred ₹${amount.toLong()} from ${source.bankName} to ${destination.bankName}"
        )
      }
    }
  }

  fun deleteExpense(expense: ExpenseRecord) {
    viewModelScope.launch {
      repository.deleteExpense(expense)
      _uiState.update { it.copy(toastMessage = "Transaction deleted") }
    }
  }

  fun updateExpense(
    expense: ExpenseRecord,
    title: String,
    amount: Double,
    category: String
  ) {
    val cleanTitle = title.trim()
    if (cleanTitle.isBlank() || !amount.isFinite() || amount <= 0.0 || category.isBlank()) {
      showToast("Enter a title, valid amount, and category.")
      return
    }

    viewModelScope.launch {
      try {
        dao.insertExpense(
          expense.copy(
            title = cleanTitle,
            amount = amount,
            categoryName = category
          )
        )
        _uiState.update { it.copy(toastMessage = "Transaction updated") }
      } catch (error: Exception) {
        _uiState.update {
          it.copy(toastMessage = "Could not update transaction: ${error.localizedMessage ?: "Unknown error"}")
        }
      }
    }
  }

  // --- Bank Account Actions ---
  fun addBankAccount(
    bankName: String,
    accountType: String,
    openingBalance: Double,
    accountNumber: String = "",
    ifscOrRouting: String = "",
    holderName: String = "",
    notes: String = ""
  ) {
    if (bankName.isBlank()) return
    viewModelScope.launch {
      dao.insertBankAccount(
        BankAccount(
          bankName = bankName.trim(),
          accountType = accountType,
          accountNumber = accountNumber.trim(),
          ifscOrRouting = ifscOrRouting.trim(),
          holderName = holderName.trim(),
          openingBalance = openingBalance,
          balance = openingBalance,
          notes = notes
        )
      )
      _uiState.update { it.copy(showAddBankDialog = false, toastMessage = "Account '$bankName' added!") }
    }
  }

  fun addCreditCard(
    bankName: String,
    currentOutstanding: Double,
    billAmount: Double?,
    dueDateEpochDay: Long?,
    accountNumber: String = "",
    notes: String = ""
  ) {
    if (bankName.isBlank()) return
    viewModelScope.launch {
      val accId = dao.insertBankAccount(
        BankAccount(
          bankName = bankName.trim(),
          accountType = "Credit Card",
          accountNumber = accountNumber.trim(),
          openingBalance = -kotlin.math.abs(currentOutstanding),
          balance = -kotlin.math.abs(currentOutstanding),
          notes = notes.trim()
        )
      )
      if (billAmount != null && billAmount > 0 && dueDateEpochDay != null) {
        val reminder = CreditCardBillReminder(
          accountId = accId,
          amount = billAmount,
          dueDateEpochDay = dueDateEpochDay,
          notificationsEnabled = true
        )
        dao.insertCreditCardBillReminder(reminder)
        CreditCardBillNotificationScheduler.schedule(getApplication(), reminder, bankName.trim())
      }
      _uiState.update { it.copy(toastMessage = "Credit card '$bankName' added!") }
    }
  }

  fun updateAccountOpeningBalance(account: BankAccount, newOpening: Double) {
    viewModelScope.launch {
      dao.insertBankAccount(account.copy(openingBalance = newOpening))
      _uiState.update { it.copy(toastMessage = "Opening balance updated!") }
    }
  }

  fun deleteBankAccount(account: BankAccount) {
    viewModelScope.launch {
      dao.deleteCreditCardBillReminder(account.id)
      CreditCardBillNotificationScheduler.cancel(getApplication(), account.id)
      dao.deleteBankAccount(account)
      _uiState.update { it.copy(toastMessage = "Account removed") }
    }
  }

  fun saveCreditCardBillReminder(reminder: CreditCardBillReminder, accountName: String) {
    viewModelScope.launch {
      dao.insertCreditCardBillReminder(reminder)
      CreditCardBillNotificationScheduler.schedule(getApplication(), reminder, accountName)
      _uiState.update { it.copy(toastMessage = "Payment reminder saved for $accountName") }
    }
  }

  fun markCreditCardBillPaid(reminder: CreditCardBillReminder) {
    viewModelScope.launch {
      val today = LocalDate.now().toEpochDay()
      dao.markCreditCardBillPaid(reminder.accountId, today)
      CreditCardBillNotificationScheduler.cancel(getApplication(), reminder.accountId)
      _uiState.update { it.copy(toastMessage = "Credit card bill marked as paid") }
    }
  }

  fun deleteCreditCardBillReminder(accountId: Long) {
    viewModelScope.launch {
      dao.deleteCreditCardBillReminder(accountId)
      CreditCardBillNotificationScheduler.cancel(getApplication(), accountId)
      _uiState.update { it.copy(toastMessage = "Credit card reminder removed") }
    }
  }

  fun copyBankDetails(context: Context, account: BankAccount) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = "Bank: ${account.bankName}\n" +
      "Account Holder: ${account.holderName}\n" +
      "Account Number: ${account.accountNumber}\n" +
      "IFSC / Routing: ${account.ifscOrRouting}\n" +
      "Type: ${account.accountType}"
    val clip = ClipData.newPlainText("Bank Details", text)
    clipboard.setPrimaryClip(clip)
    _uiState.update { it.copy(toastMessage = "Bank details copied to clipboard!") }
  }

  // --- Trip Actions ---
  fun addTrip(
    name: String,
    destination: String,
    budget: Double,
    startDate: String,
    endDate: String,
    notes: String
  ) {
    if (name.isBlank() || budget <= 0.0) return
    viewModelScope.launch {
      dao.insertTrip(
        TripRecord(
          tripName = name.trim(),
          destination = destination.trim(),
          budget = budget,
          startDate = startDate,
          endDate = endDate,
          notes = notes
        )
      )
      _uiState.update { it.copy(showAddTripDialog = false, toastMessage = "Trip '$name' created!") }
    }
  }

  fun deleteTrip(trip: TripRecord) {
    viewModelScope.launch {
      dao.deleteTrip(trip)
      _uiState.update { it.copy(toastMessage = "Trip removed") }
    }
  }

  // --- Subscriptions & Bills Actions ---
  fun addSubscription(
    name: String,
    amount: Double,
    billingCycle: String,
    dueDayOfMonth: Int,
    dueMonth: Int,
    dueDayOfWeek: Int,
    categoryName: String,
    bankAccountId: Long?,
    bankAccountName: String,
    notes: String
  ) {
    if (name.isBlank() || amount <= 0.0) return
    viewModelScope.launch {
      dao.insertSubscription(
        SubscriptionRecord(
          name = name.trim(),
          amount = amount,
          billingCycle = billingCycle,
          dueDayOfMonth = dueDayOfMonth.coerceIn(1, 31),
          dueMonth = dueMonth.coerceIn(1, 12),
          dueDayOfWeek = dueDayOfWeek.coerceIn(1, 7),
          categoryName = categoryName,
          bankAccountId = bankAccountId,
          bankAccountName = bankAccountName,
          notes = notes
        )
      )
      _uiState.update { it.copy(showAddSubscriptionDialog = false, toastMessage = "Subscription '$name' added!") }
    }
  }

  fun deleteSubscription(sub: SubscriptionRecord) {
    viewModelScope.launch {
      dao.deleteSubscription(sub)
      _uiState.update { it.copy(toastMessage = "Subscription '${sub.name}' removed") }
    }
  }

  fun markSubscriptionPaid(sub: SubscriptionRecord, bankAccount: BankAccount?) {
    viewModelScope.launch {
      try {
        val paidAt = System.currentTimeMillis()
        val accountId = bankAccount?.id
        val accountName = bankAccount?.bankName.orEmpty()
        database.withTransaction {
          dao.insertExpense(
            ExpenseRecord(
              title = "${sub.name} Bill",
              amount = sub.amount,
              categoryName = sub.categoryName,
              type = "EXPENSE",
              bankAccountId = accountId,
              bankAccountName = accountName,
              dateTimestamp = paidAt,
              notes = "Auto-logged from recurring subscription"
            )
          )
          dao.insertSubscription(
            sub.copy(
              bankAccountId = accountId,
              bankAccountName = accountName,
              lastPaidTimestamp = paidAt
            )
          )
        }
        _uiState.update {
          it.copy(toastMessage = "Paid ₹${sub.amount.toLong()} for ${sub.name} & recorded in ledger!")
        }
      } catch (error: Exception) {
        _uiState.update {
          it.copy(toastMessage = "Could not record subscription payment: ${error.localizedMessage ?: "Unknown error"}")
        }
      }
    }
  }

  fun markSubscriptionPaid(sub: SubscriptionRecord) {
    val account = bankAccounts.value.firstOrNull { it.id == sub.bankAccountId }
      ?: bankAccounts.value.firstOrNull()
    markSubscriptionPaid(sub, account)
  }

  // --- Trip Category Caps Actions ---
  fun updateTripCategoryCap(trip: TripRecord, categoryName: String, capAmount: Double) {
    viewModelScope.launch {
      val json = try {
        if (trip.categoryCapsJson.isNotBlank()) JSONObject(trip.categoryCapsJson) else JSONObject()
      } catch (e: Exception) {
        JSONObject()
      }
      json.put(categoryName, capAmount)
      dao.updateTrip(trip.copy(categoryCapsJson = json.toString()))
      _uiState.update { it.copy(selectedTripForCategoryCap = null, toastMessage = "Sub-budget set: $categoryName → ₹${capAmount.toLong()}") }
    }
  }

  fun removeTripCategoryCap(trip: TripRecord, categoryName: String) {
    viewModelScope.launch {
      val json = try {
        if (trip.categoryCapsJson.isNotBlank()) JSONObject(trip.categoryCapsJson) else JSONObject()
      } catch (e: Exception) {
        JSONObject()
      }
      json.remove(categoryName)
      dao.updateTrip(trip.copy(categoryCapsJson = json.toString()))
      _uiState.update { it.copy(toastMessage = "Removed cap for $categoryName") }
    }
  }

  fun deleteExpensesWithUndo(
    expensesToDelete: List<ExpenseRecord>,
    onUndoPrompt: (undo: () -> Unit) -> Unit
  ) {
    val deletedExpenses = expensesToDelete.distinctBy { it.id }
    if (deletedExpenses.isEmpty()) return

    viewModelScope.launch {
      try {
        database.withTransaction {
          deletedExpenses.forEach { repository.deleteExpense(it) }
        }
        onUndoPrompt {
          viewModelScope.launch {
            try {
              database.withTransaction {
                deletedExpenses.forEach { repository.restoreExpense(it) }
              }
              val restoredTitles = deletedExpenses.joinToString { it.title }
              _uiState.update { it.copy(toastMessage = "Restored '$restoredTitles'") }
            } catch (error: Exception) {
              if (error is CancellationException) throw error
              showToast("Could not restore transaction: ${error.localizedMessage ?: "Unknown error"}")
            }
          }
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        showToast("Could not delete transaction: ${error.localizedMessage ?: "Unknown error"}")
      }
    }
  }

  // --- Backup & Restore Actions ---
  fun setShowBackupModal(show: Boolean) {
    _uiState.update { it.copy(showBackupModal = show) }
  }

  fun setShowPasteJsonDialog(show: Boolean) {
    _uiState.update { it.copy(showPasteJsonDialog = show) }
  }

  fun setReplaceExistingOnImport(replace: Boolean) {
    _uiState.update { it.copy(replaceExistingOnImport = replace) }
  }

  fun getBackupJsonString(): String {
    return FinanceBackupManager.exportToJson(
      categories = categories.value,
      bankAccounts = bankAccounts.value,
      trips = trips.value,
      expenses = expenses.value,
      loans = allLoans.value,
      debts = allDebts.value,
      subscriptions = subscriptions.value,
      creditCardReminders = creditCardBillReminders.value,
      monthlyBudgetLimit = monthlyBudgetLimit.value,
      userUpiId = prefs.getString("user_upi_id", "") ?: ""
    )
  }

  fun exportToUri(context: Context, uri: Uri) {
    viewModelScope.launch {
      val json = getBackupJsonString()
      val success = FinanceBackupManager.writeToUri(context, uri, json)
      _uiState.update {
        it.copy(
          toastMessage = if (success) "Universal backup exported successfully to file!" else "Failed to write backup file.",
          showBackupModal = false
        )
      }
    }
  }

  fun shareBackup(context: Context) {
    val json = getBackupJsonString()
    FinanceBackupManager.shareBackupFile(context, json)
  }

  fun copyBackupJson(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OmniCalc Universal Finance Backup", getBackupJsonString())
    clipboard.setPrimaryClip(clip)
    _uiState.update { it.copy(toastMessage = "Universal Backup JSON copied to clipboard!") }
  }

  fun importFromUri(context: Context, uri: Uri, replaceExisting: Boolean) {
    viewModelScope.launch {
      val json = FinanceBackupManager.readFromUri(context, uri)
      if (json.isNullOrBlank()) {
        _uiState.update { it.copy(toastMessage = "Could not read data from selected file.") }
        return@launch
      }
      importFromJson(json, replaceExisting)
    }
  }

  fun importFromJson(jsonString: String, replaceExisting: Boolean) {
    viewModelScope.launch {
      when (val result = FinanceBackupManager.parseFromJson(jsonString)) {
        is BackupParseResult.Error -> {
          _uiState.update { it.copy(toastMessage = result.message) }
        }
        is BackupParseResult.Success -> {
          try {
            restorePayload(result.payload, replaceExisting)
            _uiState.update {
              it.copy(
                showBackupModal = false,
                showPasteJsonDialog = false,
                toastMessage = "Universal backup restored: ${result.summary}!"
              )
            }
          } catch (error: Exception) {
            _uiState.update {
              it.copy(toastMessage = "Backup restore failed: ${error.localizedMessage ?: "Unable to write data"}")
            }
          }
        }
      }
    }
  }

  private suspend fun restorePayload(payload: FinanceBackupPayload, replaceExisting: Boolean) {
    val oldReminders = dao.getAllCreditCardBillReminders().first()
    val oldLoans = loanDao.getAllLoans().first()
    val oldDebts = debtDao.getAllDebts().first()
    val existingCategories = dao.getAllCategories().first()
    val existingAccounts = dao.getAllBankAccounts().first()
    val existingTrips = dao.getAllTrips().first()
    val existingLoans = oldLoans
    val existingDebts = oldDebts
    val existingSubscriptions = dao.getAllSubscriptions().first()
    val existingReminders = oldReminders
    val context = getApplication<Application>()

    database.withTransaction {
      if (replaceExisting) {
        dao.deleteAllCreditCardBillReminders()
        dao.deleteAllExpenses()
        dao.deleteAllSubscriptions()
        dao.deleteAllTrips()
        debtDao.deleteAllDebts()
        loanDao.deleteAllLoans()
        dao.deleteAllBankAccounts()
        dao.deleteAllCategories()
      }

      fun accountKey(account: BankAccount) = listOf(
        account.bankName.trim().lowercase(),
        account.accountType.trim().lowercase(),
        account.accountNumber.trim().lowercase(),
        if (account.accountNumber.isBlank()) account.holderName.trim().lowercase() else ""
      ).joinToString("|")
      val accountsByIdentity = (if (replaceExisting) emptyList() else existingAccounts)
        .associateBy(::accountKey)
      val accountIdMap = mutableMapOf<Long, Long>()
      val accountNamesById = mutableMapOf<String, Long>()
      if (!replaceExisting) {
        existingAccounts.forEach { account ->
          accountNamesById[account.bankName.trim().lowercase()] = account.id
          if (account.id > 0L) accountIdMap[account.id] = account.id
        }
      }
      payload.bankAccounts.forEach { account ->
        val existing = if (!replaceExisting) {
          accountsByIdentity[accountKey(account)]
        } else null
        val targetId = existing?.id ?: dao.insertBankAccount(
          if (replaceExisting) account else account.copy(id = 0L)
        )
        if (account.id > 0L) accountIdMap[account.id] = targetId
        accountNamesById[account.bankName.trim().lowercase()] = targetId
      }

      val categoryNames = (if (replaceExisting) emptyList() else existingCategories)
        .map { it.name.trim().lowercase() }.toMutableSet()
      payload.categories.forEach { category ->
        if (categoryNames.add(category.name.trim().lowercase())) {
          dao.insertCategory(if (replaceExisting) category else category.copy(id = 0L))
        }
      }

      fun tripKey(trip: TripRecord) = listOf(
        trip.tripName.trim().lowercase(),
        trip.destination.trim().lowercase(),
        trip.startDate.trim(),
        trip.endDate.trim()
      ).joinToString("|")
      val tripsByIdentity = (if (replaceExisting) emptyList() else existingTrips)
        .associateBy(::tripKey)
      val tripIdMap = mutableMapOf<Long, Long>()
      if (!replaceExisting) existingTrips.forEach { if (it.id > 0L) tripIdMap[it.id] = it.id }
      payload.trips.forEach { trip ->
        val existing = if (replaceExisting) null else tripsByIdentity[tripKey(trip)]
        val targetId = existing?.id ?: dao.insertTrip(if (replaceExisting) trip else trip.copy(id = 0L))
        if (trip.id > 0L) tripIdMap[trip.id] = targetId
      }

      fun loanKey(loan: LoanRecord) = listOf(
        loan.loanName.trim().lowercase(),
        loan.loanCategory.trim().lowercase(),
        loan.principalAmount,
        loan.annualInterestRate,
        loan.tenureMonths,
        loan.emiAmount,
        loan.startDate.trim()
      ).joinToString("|")
      val loansByIdentity = (if (replaceExisting) emptyList() else existingLoans)
        .associateBy(::loanKey)
      val loanIdMap = mutableMapOf<Long, Long>()
      if (!replaceExisting) existingLoans.forEach { if (it.id > 0L) loanIdMap[it.id] = it.id }
      payload.loans.forEach { loan ->
        val existing = if (replaceExisting) null else loansByIdentity[loanKey(loan)]
        val targetId = existing?.id ?: loanDao.insertLoan(
          loan.copy(
            id = if (replaceExisting) loan.id else 0L,
            bankAccountId = loan.bankAccountId?.let { accountIdMap[it] }
              ?: accountNamesById[loan.bankAccountName.trim().lowercase()]
          )
        )
        if (loan.id > 0L) loanIdMap[loan.id] = targetId
      }

      fun debtKey(debt: DebtRecord) =
        listOf(
          debt.personName.trim().lowercase(),
          debt.amount,
          debt.type.trim().uppercase(),
          debt.dateTimestamp,
          debt.dueDateTimestamp,
          debt.isSettled,
          debt.settledDateTimestamp,
          debt.bankAccountName.trim().lowercase(),
          debt.notes.trim()
        ).joinToString("|")
      val debtsByKey = (if (replaceExisting) emptyList() else existingDebts).associateBy(::debtKey)
      val debtIdMap = mutableMapOf<Long, Long>()
      if (!replaceExisting) existingDebts.forEach { if (it.id > 0L) debtIdMap[it.id] = it.id }
      payload.debts.forEach { debt ->
        val existing = if (replaceExisting) null else debtsByKey[debtKey(debt)]
        val targetId = existing?.id ?: debtDao.insertDebt(
          debt.copy(
            id = if (replaceExisting) debt.id else 0L,
            bankAccountId = debt.bankAccountId?.let { accountIdMap[it] }
              ?: accountNamesById[debt.bankAccountName.trim().lowercase()]
          )
        )
        if (debt.id > 0L) debtIdMap[debt.id] = targetId
      }

      payload.expenses.forEach { expense ->
        dao.insertExpense(
          expense.copy(
            id = if (replaceExisting) expense.id else 0L,
            bankAccountId = expense.bankAccountId?.let { accountIdMap[it] }
              ?: accountNamesById[expense.bankAccountName.trim().lowercase()],
            tripId = expense.tripId?.let { tripIdMap[it] },
            loanId = expense.loanId?.let { loanIdMap[it] },
            debtId = expense.debtId?.let { debtIdMap[it] }
          )
        )
      }

      fun subscriptionKey(subscription: SubscriptionRecord) = listOf(
        subscription.name.trim().lowercase(),
        subscription.amount,
        subscription.categoryName.trim().lowercase(),
        subscription.billingCycle.trim().uppercase(),
        subscription.dueDayOfMonth,
        subscription.dueMonth,
        subscription.dueDayOfWeek,
        subscription.bankAccountName.trim().lowercase(),
        subscription.lastPaidTimestamp,
        subscription.notes.trim()
      ).joinToString("|")
      val subscriptionsByIdentity = (if (replaceExisting) emptyList() else existingSubscriptions)
        .associateBy(::subscriptionKey)
      payload.subscriptions.forEach { subscription ->
        val existing = if (replaceExisting) null else subscriptionsByIdentity[subscriptionKey(subscription)]
        if (existing == null) {
          dao.insertSubscription(
            subscription.copy(
              id = if (replaceExisting) subscription.id else 0L,
              bankAccountId = subscription.bankAccountId?.let { accountIdMap[it] }
                ?: accountNamesById[subscription.bankAccountName.trim().lowercase()]
            )
          )
        }
      }

      val reminderAccountIds = (if (replaceExisting) emptyList() else existingReminders)
        .map { it.accountId }.toMutableSet()
      payload.creditCardReminders.forEach { reminder ->
        val accountName = payload.creditCardReminderAccountNames[reminder.accountId].orEmpty()
        val targetAccountId = accountIdMap[reminder.accountId]
          ?: accountNamesById[accountName.trim().lowercase()]
        if (targetAccountId != null && (replaceExisting || reminderAccountIds.add(targetAccountId))) {
          dao.insertCreditCardBillReminder(reminder.copy(accountId = targetAccountId))
        }
      }
    }

    if (replaceExisting) {
      val restoredBudget = payload.monthlyBudgetLimit ?: 50_000.0
      prefs.edit()
        .putFloat("monthly_budget_limit", restoredBudget.toFloat())
        .apply()
      _monthlyBudgetLimit.value = restoredBudget

      val preferencesEditor = prefs.edit()
      if (payload.userUpiId != null) {
        preferencesEditor.putString("user_upi_id", payload.userUpiId)
      } else {
        preferencesEditor.remove("user_upi_id")
      }
      preferencesEditor.apply()
    }

    oldReminders.forEach { CreditCardBillNotificationScheduler.cancel(context, it.accountId) }
    oldDebts.forEach { CreditCardBillNotificationScheduler.cancelDebt(context, it.id) }
    oldLoans.forEach { CreditCardBillNotificationScheduler.cancelLoan(context, it.id) }
    CreditCardBillNotificationScheduler.rescheduleAll(context)
  }
}
