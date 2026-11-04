package com.example.ui.screens.finance

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.CreditCardBillReminder
import com.example.data.local.entity.ExpenseCategory
import com.example.data.local.entity.ExpenseRecord
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

enum class FinanceTab(val title: String) {
  EXPENSES("Expenses & Accounts"),
  SUBSCRIPTIONS("Recurring & Bills"),
  DEBTS("Lend & Borrow"),
  TRIPS("Trip Manager")
}

data class AccountWithBalance(
  val account: BankAccount,
  val openingBalance: Double,
  val totalIncome: Double,
  val totalExpense: Double,
  val currentBalance: Double
)

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

  private val dao = AppDatabase.getDatabase(application).financeDao()

  private val _uiState = MutableStateFlow(FinanceUiState())
  val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

  val categories: StateFlow<List<ExpenseCategory>> = dao.getAllCategories()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val expenses: StateFlow<List<ExpenseRecord>> = dao.getAllExpenses()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val bankAccounts: StateFlow<List<BankAccount>> = dao.getAllBankAccounts()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val creditCardBillReminders: StateFlow<List<CreditCardBillReminder>> =
    dao.getAllCreditCardBillReminders()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val trips: StateFlow<List<TripRecord>> = dao.getAllTrips()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val subscriptions: StateFlow<List<SubscriptionRecord>> = dao.getAllSubscriptions()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val prefs = application.getSharedPreferences("omni_calc_finance_prefs", Context.MODE_PRIVATE)
  private val _monthlyBudgetLimit = MutableStateFlow(prefs.getFloat("monthly_budget_limit", 50000f).toDouble())
  val monthlyBudgetLimit: StateFlow<Double> = _monthlyBudgetLimit.asStateFlow()

  // Computed accounts with live balances based on opening balance, income, and expenses
  val accountsWithBalances: StateFlow<List<AccountWithBalance>> = combine(bankAccounts, expenses) { accs, exps ->
    accs.map { acc ->
      val accExpenses = exps.filter {
        (it.type == "EXPENSE" || it.type == "TRANSFER_OUT") && it.bankAccountId == acc.id
      }.sumOf { it.amount }
      val accIncome = exps.filter {
        (it.type == "INCOME" || it.type == "TRANSFER_IN") && it.bankAccountId == acc.id
      }.sumOf { it.amount }
      val current = acc.openingBalance + accIncome - accExpenses
      AccountWithBalance(
        account = acc,
        openingBalance = acc.openingBalance,
        totalIncome = accIncome,
        totalExpense = accExpenses,
        currentBalance = current
      )
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val totalNetBalance: StateFlow<Double> = accountsWithBalances.map { list ->
    list.sumOf { it.currentBalance }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

  val totalIncome: StateFlow<Double> = expenses.map { list ->
    list.filter { it.type == "INCOME" }.sumOf { it.amount }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

  val totalExpense: StateFlow<Double> = expenses.map { list ->
    list.filter { it.type == "EXPENSE" }.sumOf { it.amount }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

  val currentMonthExpenses: StateFlow<Double> = expenses.map { list ->
    val cal = Calendar.getInstance()
    val currMonth = cal.get(Calendar.MONTH)
    val currYear = cal.get(Calendar.YEAR)
    list.filter { item ->
      if (item.type != "EXPENSE") return@filter false
      val itemCal = Calendar.getInstance().apply { timeInMillis = item.dateTimestamp }
      itemCal.get(Calendar.MONTH) == currMonth && itemCal.get(Calendar.YEAR) == currYear
    }.sumOf { it.amount }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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
      dao.deleteExpense(expense)
      _uiState.update { it.copy(toastMessage = "Transaction deleted") }
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

  fun markSubscriptionPaid(sub: SubscriptionRecord) {
    viewModelScope.launch {
      val paidAt = System.currentTimeMillis()
      dao.insertExpense(
        ExpenseRecord(
          title = "${sub.name} Bill",
          amount = sub.amount,
          categoryName = sub.categoryName,
          type = "EXPENSE",
          bankAccountId = sub.bankAccountId,
          bankAccountName = sub.bankAccountName,
          dateTimestamp = paidAt,
          notes = "Auto-logged from recurring subscription"
        )
      )
      dao.insertSubscription(sub.copy(lastPaidTimestamp = paidAt))
      _uiState.update { it.copy(toastMessage = "Paid ₹${sub.amount.toLong()} for ${sub.name} & recorded in ledger!") }
    }
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

  // --- Swipe-to-Delete with Undo ---
  var lastDeletedExpense: ExpenseRecord? = null
    private set

  fun deleteExpenseWithUndo(expense: ExpenseRecord, onUndoPrompt: () -> Unit) {
    lastDeletedExpense = expense
    viewModelScope.launch {
      dao.deleteExpense(expense)
      onUndoPrompt()
    }
  }

  fun restoreLastDeletedExpense() {
    val exp = lastDeletedExpense ?: return
    viewModelScope.launch {
      dao.insertExpense(exp)
      lastDeletedExpense = null
      _uiState.update { it.copy(toastMessage = "Restored '${exp.title}'") }
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
      expenses = expenses.value
    )
  }

  fun exportToUri(context: Context, uri: Uri) {
    viewModelScope.launch {
      val json = getBackupJsonString()
      val success = FinanceBackupManager.writeToUri(context, uri, json)
      _uiState.update {
        it.copy(
          toastMessage = if (success) "Backup exported successfully to file!" else "Failed to write backup file.",
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
    val clip = ClipData.newPlainText("OmniCalc Finance Backup", getBackupJsonString())
    clipboard.setPrimaryClip(clip)
    _uiState.update { it.copy(toastMessage = "Backup JSON copied to clipboard!") }
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
          val payload = result.payload
          if (replaceExisting) {
            dao.deleteAllCustomCategories()
            creditCardBillReminders.value.forEach {
              CreditCardBillNotificationScheduler.cancel(getApplication(), it.accountId)
            }
            dao.deleteAllCreditCardBillReminders()
            dao.deleteAllBankAccounts()
            dao.deleteAllTrips()
            dao.deleteAllExpenses()

            if (payload.categories.isNotEmpty()) dao.insertCategories(payload.categories)
            if (payload.bankAccounts.isNotEmpty()) dao.insertBankAccounts(payload.bankAccounts)
            if (payload.trips.isNotEmpty()) dao.insertTrips(payload.trips)
            if (payload.expenses.isNotEmpty()) dao.insertExpenses(payload.expenses)
          } else {
            // Merge strategy: skip duplicate names/numbers
            val currentCats = categories.value.map { it.name.lowercase().trim() }.toSet()
            val newCats = payload.categories.filter { it.name.lowercase().trim() !in currentCats }
            if (newCats.isNotEmpty()) dao.insertCategories(newCats)

            val currentAccs = bankAccounts.value.map { it.accountNumber.trim() }.toSet()
            val newAccs = payload.bankAccounts.filter { it.accountNumber.trim() !in currentAccs }
            if (newAccs.isNotEmpty()) dao.insertBankAccounts(newAccs)

            val currentTrips = trips.value.map { it.tripName.lowercase().trim() }.toSet()
            val newTrips = payload.trips.filter { it.tripName.lowercase().trim() !in currentTrips }
            if (newTrips.isNotEmpty()) dao.insertTrips(newTrips)

            if (payload.expenses.isNotEmpty()) {
              dao.insertExpenses(payload.expenses)
            }
          }

          _uiState.update {
            it.copy(
              showBackupModal = false,
              showPasteJsonDialog = false,
              toastMessage = "Restored successfully: ${result.summary}!"
            )
          }
        }
      }
    }
  }
}
