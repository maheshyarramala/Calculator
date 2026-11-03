package com.example.ui.screens.finance

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.ExpenseCategory
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.TripRecord
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.example.domain.backup.BackupParseResult
import com.example.domain.backup.FinanceBackupManager
import com.example.domain.backup.FinanceBackupPayload
import android.net.Uri

enum class FinanceTab(val title: String) {
  EXPENSES("Expenses"),
  BANK_ACCOUNTS("Bank Accounts"),
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
  val showAddBankDialog: Boolean = false,
  val showAddTripDialog: Boolean = false,
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

  val trips: StateFlow<List<TripRecord>> = dao.getAllTrips()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

  fun setShowAddBank(show: Boolean) {
    _uiState.update { it.copy(showAddBankDialog = show) }
  }

  fun setShowAddTrip(show: Boolean) {
    _uiState.update { it.copy(showAddTripDialog = show) }
  }

  fun clearToast() {
    _uiState.update { it.copy(toastMessage = null) }
  }

  fun showToast(msg: String) {
    _uiState.update { it.copy(toastMessage = msg) }
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

  // --- Expense Actions ---
  fun addExpense(title: String, amount: Double, category: String, notes: String, tripId: Long? = null) {
    if (title.isBlank() || amount <= 0.0) return
    viewModelScope.launch {
      dao.insertExpense(
        ExpenseRecord(
          title = title.trim(),
          amount = amount,
          categoryName = category,
          notes = notes,
          tripId = tripId
        )
      )
      _uiState.update { it.copy(showAddExpenseDialog = false, toastMessage = "Expense logged successfully!") }
    }
  }

  fun deleteExpense(expense: ExpenseRecord) {
    viewModelScope.launch {
      dao.deleteExpense(expense)
      _uiState.update { it.copy(toastMessage = "Expense deleted") }
    }
  }

  // --- Bank Account Actions ---
  fun addBankAccount(
    bankName: String,
    accountType: String,
    accountNumber: String,
    ifscOrRouting: String,
    holderName: String,
    balance: Double,
    notes: String
  ) {
    if (bankName.isBlank() || accountNumber.isBlank()) return
    viewModelScope.launch {
      dao.insertBankAccount(
        BankAccount(
          bankName = bankName.trim(),
          accountType = accountType,
          accountNumber = accountNumber.trim(),
          ifscOrRouting = ifscOrRouting.trim(),
          holderName = holderName.trim(),
          balance = balance,
          notes = notes
        )
      )
      _uiState.update { it.copy(showAddBankDialog = false, toastMessage = "Bank details saved securely!") }
    }
  }

  fun deleteBankAccount(account: BankAccount) {
    viewModelScope.launch {
      dao.deleteBankAccount(account)
      _uiState.update { it.copy(toastMessage = "Account removed") }
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
