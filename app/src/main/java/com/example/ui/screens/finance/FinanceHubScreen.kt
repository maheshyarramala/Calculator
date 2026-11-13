package com.example.ui.screens.finance
import com.example.ui.theme.performConfiguredHapticFeedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.domain.report.PdfReportGenerator
import com.example.ui.screens.debt.DebtTrackerScreen
import com.example.ui.screens.debt.DebtTrackerViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AccountWithBalance
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.CreditCardBillReminder
import com.example.data.local.entity.ExpenseCategory
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.TripRecord
import com.example.data.local.entity.SubscriptionRecord
import com.example.domain.calculator.SubscriptionDueDateCalculator
import com.example.domain.finance.BankSmsParser
import org.json.JSONObject
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import java.util.Calendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import com.example.ui.components.CreditCardBillReminderDialog
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.NumberInputField
import android.net.Uri
import java.io.File
import com.example.domain.backup.FinanceBackupManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private val EXPENSE_ITEM_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())
private val EXPENSE_DETAIL_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault())
private val TRIP_PICKER_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
private val BACKUP_FILE_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.getDefault())
private val CC_DUE_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
private val TRANSACTION_PERIOD_OPTIONS = listOf(
  "ALL" to "Any date",
  "WEEK" to "7 days",
  "MONTH" to "This month",
  "LAST_MONTH" to "Last month"
)
private val TRANSACTION_TYPE_OPTIONS = listOf(
  null to "All",
  "EXPENSE" to "Expenses",
  "INCOME" to "Income",
  "TRANSFER" to "Transfers"
)
private val EXPENSE_CARD_SHAPE = RoundedCornerShape(16.dp)
private val EXPENSE_ICON_BOX_SHAPE = RoundedCornerShape(10.dp)
private val EXPENSE_PILL_SHAPE = RoundedCornerShape(6.dp)
private val PROGRESS_BAR_SHAPE = RoundedCornerShape(3.dp)
private val EXPENSE_CC_COLOR = Color(0xFFF43F5E)
private val EXPENSE_CC_BG = Color(0xFFF43F5E).copy(alpha = 0.15f)
private val EXPENSE_CC_BORDER = BorderStroke(0.5.dp, Color(0xFFF43F5E).copy(alpha = 0.45f))
private val EXPENSE_CC_BILL_COLOR = Color(0xFF10B981)
private val EXPENSE_CC_BILL_BG = Color(0xFF10B981).copy(alpha = 0.15f)
private val EXPENSE_CC_BILL_BORDER = BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.45f))
private val EXPENSE_INCOME_COLOR = Color(0xFF059669)
private val EXPENSE_INCOME_BG = Color(0xFF10B981).copy(alpha = 0.15f)
private val EXPENSE_EXPENSE_COLOR = Color(0xFFDC2626)

@Immutable
private data class SpendingAnalyticsRow(
  val categoryName: String,
  val amount: Double,
  val color: Color,
  val percent: Float,
  val percentInt: Int,
  val formattedPercent: String,
  val formattedAmount: String
)

private fun formatFinanceTimestamp(timestamp: Long, formatter: DateTimeFormatter): String =
  Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(formatter)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceHubScreen(
  viewModel: FinanceHubViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()

  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  var preselectedTripIdForExpense by remember { mutableStateOf<Long?>(null) }
  var accountForDetails by remember { mutableStateOf<BankAccount?>(null) }
  var accountPendingDelete by remember { mutableStateOf<BankAccount?>(null) }
  var editingAccount by remember { mutableStateOf<BankAccount?>(null) }
  var selectedExpenseForDetails by remember { mutableStateOf<ExpenseRecord?>(null) }
  var selectedSubscriptionForDetails by remember { mutableStateOf<SubscriptionRecord?>(null) }
  var editingSubscription by remember { mutableStateOf<SubscriptionRecord?>(null) }
  var editingTrip by remember { mutableStateOf<TripRecord?>(null) }
  var subscriptionToDelete by remember { mutableStateOf<SubscriptionRecord?>(null) }
  var editingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
  var subscriptionPendingPayment by remember { mutableStateOf<SubscriptionRecord?>(null) }
  var showTransferDialog by remember { mutableStateOf(false) }
  var showCreditCardReminderDialog by remember { mutableStateOf(false) }
  var editingCreditCardAccountId by remember { mutableStateOf<Long?>(null) }
  var showAddCreditCardDialog by remember { mutableStateOf(false) }
  var payingCreditCardAccount by remember { mutableStateOf<BankAccount?>(null) }
  var payingCreditCardReminder by remember { mutableStateOf<CreditCardBillReminder?>(null) }

  LaunchedEffect(uiState.toastMessage) {
    uiState.toastMessage?.let { msg ->
      val snackbarJob = launch {
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Indefinite)
      }
      snapshotFlow { snackbarHostState.currentSnackbarData }
        .first { it?.visuals?.message == msg }
      delay(1800)
      if (snackbarHostState.currentSnackbarData?.visuals?.message == msg) {
        snackbarHostState.currentSnackbarData?.dismiss()
      }
      snackbarJob.join()
      viewModel.clearToast()
    }
  }

  LaunchedEffect(uiState.isFinanceLocked) {
    if (uiState.isFinanceLocked) {
      val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
      if (fragmentActivity != null) {
        com.example.domain.security.BiometricLockManager.authenticate(
          activity = fragmentActivity,
          onSuccess = { viewModel.unlockFinance() },
          onError = { /* Keep locked */ }
        )
      }
    }
  }

  var editingAccountForOpeningBalance by remember { mutableStateOf<BankAccount?>(null) }

  // Storage Access Framework (SAF) Launchers for JSON Export & Import
  val exportFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("*/*")
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.exportToUri(context, uri)
    }
  }

  val importFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.importFromUri(context, uri, uiState.replaceExistingOnImport)
    }
  }

  val getContentImportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.importFromUri(context, uri, uiState.replaceExistingOnImport)
    }
  }

  // Backup & Restore Dialogs
  if (uiState.showBackupModal) {
    val categories by viewModel.categories.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val allLoans by viewModel.allLoans.collectAsState()
    val allDebts by viewModel.allDebts.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val creditCardBillReminders by viewModel.creditCardBillReminders.collectAsState()
    BackupRestoreBottomSheet(
      categoriesCount = categories.size,
      banksCount = bankAccounts.size,
      tripsCount = trips.size,
      expensesCount = expenses.size,
      loansCount = allLoans.size,
      debtsCount = allDebts.size,
      subscriptionsCount = subscriptions.size,
      cardRemindersCount = creditCardBillReminders.size,
      replaceExisting = uiState.replaceExistingOnImport,
      onReplaceExistingChange = { viewModel.setReplaceExistingOnImport(it) },
      onExportToFile = {
        val dateStr = LocalDateTime.now().format(BACKUP_FILE_DATE_FORMATTER)
        try {
          exportFileLauncher.launch("OmniCalc_Backup_$dateStr.json")
        } catch (e: Exception) {
          viewModel.showToast("Could not open folder picker: ${e.localizedMessage ?: "File picker error"}")
        }
      },
      onShareBackup = { viewModel.shareBackup(context) },
      onCopyJson = { viewModel.copyBackupJson(context) },
      onImportFromFile = {
        try {
          importFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        } catch (_: Exception) {
          try {
            getContentImportLauncher.launch("*/*")
          } catch (e2: Exception) {
            viewModel.showToast("Could not open file browser: ${e2.localizedMessage ?: "File browser error"}")
          }
        }
      },
      onPasteJsonClick = {
        viewModel.setShowBackupModal(false)
        viewModel.setShowPasteJsonDialog(true)
      },
      onDismiss = { viewModel.setShowBackupModal(false) }
    )
  }

  if (uiState.showPasteJsonDialog) {
    PasteJsonBackupDialog(
      replaceExisting = uiState.replaceExistingOnImport,
      onDismiss = { viewModel.setShowPasteJsonDialog(false) },
      onConfirm = { json, replace ->
        viewModel.importFromJson(json, replace)
      }
    )
  }

  // Dialogs
  if (uiState.showAddCategoryDialog) {
    AddCategoryDialog(
      onDismiss = { viewModel.setShowAddCategory(false) },
      onConfirm = { name, colorHex -> viewModel.addCategory(name, colorHex) }
    )
  }

  if (uiState.showAddExpenseDialog) {
    val categories by viewModel.categories.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    val trips by viewModel.trips.collectAsState()
    AddExpenseDialog(
      categories = categories,
      bankAccounts = bankAccounts,
      trips = trips,
      initialTripId = preselectedTripIdForExpense,
      onDismiss = {
        viewModel.setShowAddExpense(false)
        preselectedTripIdForExpense = null
      },
      onConfirm = { title, amount, category, accountId, accountName, notes, tripId ->
        viewModel.addExpense(title, amount, category, accountId, accountName, notes, tripId)
        preselectedTripIdForExpense = null
      }
    )
  }

  if (uiState.showAddIncomeDialog) {
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    AddIncomeDialog(
      bankAccounts = bankAccounts,
      onDismiss = { viewModel.setShowAddIncome(false) },
      onConfirm = { title, amount, category, accountId, accountName, notes ->
        viewModel.addIncome(title, amount, category, accountId, accountName, notes)
      }
    )
  }

  // Transaction Details Modal (Tap to Inspect)
  selectedExpenseForDetails?.let { expense ->
    val categories by viewModel.categories.collectAsState()
    TransactionDetailsDialog(
      expense = expense,
      categories = categories,
      onDismiss = { selectedExpenseForDetails = null },
      onEdit = {
        val target = expense
        selectedExpenseForDetails = null
        editingExpense = target
      },
      onDelete = {
        val target = expense
        selectedExpenseForDetails = null
        viewModel.deleteExpensesWithUndo(listOf(target)) { undo ->
          coroutineScope.launch {
            val res = snackbarHostState.showSnackbar(
              message = "Deleted '${target.title}'",
              actionLabel = "Undo",
              duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
              undo()
            }
          }
        }
      }
    )
  }

  editingExpense?.let { expense ->
    val categories by viewModel.categories.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    EditExpenseDialog(
      expense = expense,
      categories = categories,
      bankAccounts = bankAccounts,
      onDismiss = { editingExpense = null },
      onConfirm = { title, amount, category, accountId, accountName, notes, dateTimestamp ->
        viewModel.updateExpense(expense, title, amount, category, accountId, accountName, notes, dateTimestamp)
        editingExpense = null
      }
    )
  }

  // Subscription Details Modal (Tap to Inspect)
  selectedSubscriptionForDetails?.let { sub ->
    SubscriptionDetailsDialog(
      sub = sub,
      today = LocalDate.now(),
      onDismiss = { selectedSubscriptionForDetails = null },
      onEdit = {
        val target = sub
        selectedSubscriptionForDetails = null
        editingSubscription = target
      },
      onMarkPaid = {
        val target = sub
        selectedSubscriptionForDetails = null
        subscriptionPendingPayment = target
      },
      onDelete = {
        val target = sub
        selectedSubscriptionForDetails = null
        subscriptionToDelete = target
      }
    )
  }

  // Edit Subscription Dialog
  editingSubscription?.let { sub ->
    val categories by viewModel.categories.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    EditSubscriptionDialog(
      sub = sub,
      categories = categories,
      bankAccounts = bankAccounts,
      onDismiss = { editingSubscription = null },
      onConfirm = { updatedSub ->
        viewModel.updateSubscription(updatedSub)
        editingSubscription = null
      }
    )
  }

  // Delete Subscription Confirmation Dialog
  subscriptionToDelete?.let { sub ->
    AlertDialog(
      onDismissRequest = { subscriptionToDelete = null },
      title = { Text("Delete Subscription?", fontWeight = FontWeight.Bold) },
      text = {
        Text("Are you sure you want to delete '${sub.name}' (${CurrencyFormatter.format(sub.amount)}/${sub.billingCycle})?")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteSubscription(sub)
            subscriptionToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { subscriptionToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // Edit Trip Dialog
  editingTrip?.let { trip ->
    EditTripDialog(
      trip = trip,
      onDismiss = { editingTrip = null },
      onConfirm = { updatedTrip ->
        viewModel.updateTrip(updatedTrip)
        editingTrip = null
      }
    )
  }

  subscriptionPendingPayment?.let { subscription ->
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    MarkSubscriptionPaidDialog(
      sub = subscription,
      bankAccounts = bankAccounts,
      onDismiss = { subscriptionPendingPayment = null },
      onConfirm = { account ->
        viewModel.markSubscriptionPaid(subscription, account)
        subscriptionPendingPayment = null
      }
    )
  }

  if (showTransferDialog) {
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    TransferBetweenAccountsDialog(
      accounts = bankAccounts,
      onDismiss = { showTransferDialog = false },
      onConfirm = { from, to, amount, notes ->
        viewModel.transferBetweenAccounts(from, to, amount, notes)
        showTransferDialog = false
      }
    )
  }

  if (uiState.showAddBankDialog) {
    AddAccountDialog(
      onDismiss = { viewModel.setShowAddBank(false) },
      onConfirm = { name, type, openingBal, num, ifsc, holder, notes ->
        viewModel.addBankAccount(name, type, openingBal, num, ifsc, holder, notes)
      }
    )
  }

  if (showAddCreditCardDialog) {
    AddCreditCardDialog(
      onDismiss = { showAddCreditCardDialog = false },
      onConfirm = { name, outstanding, bill, dueDate, num, notes ->
        viewModel.addCreditCard(name, outstanding, bill, dueDate, num, notes)
        showAddCreditCardDialog = false
      }
    )
  }

  payingCreditCardAccount?.let { cardAccount ->
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    PayCreditCardBillDialog(
      cardAccount = cardAccount,
      reminder = payingCreditCardReminder,
      bankAccounts = bankAccounts,
      onDismiss = {
        payingCreditCardAccount = null
        payingCreditCardReminder = null
      },
      onConfirm = { fromBankAccountId, amount ->
        viewModel.payCreditCardBill(cardAccount.id, fromBankAccountId, amount)
        payingCreditCardAccount = null
        payingCreditCardReminder = null
      }
    )
  }

  editingAccountForOpeningBalance?.let { account ->
    EditOpeningBalanceDialog(
      account = account,
      onDismiss = { editingAccountForOpeningBalance = null },
      onConfirm = { newBal ->
        viewModel.updateAccountOpeningBalance(account, newBal)
        editingAccountForOpeningBalance = null
      }
    )
  }

  accountForDetails?.let { account ->
    val accountsWithBalances by viewModel.accountsWithBalances.collectAsState()
    val creditCardBillReminders by viewModel.creditCardBillReminders.collectAsState()
    val currentBalance = accountsWithBalances
      .firstOrNull { it.account.id == account.id }
      ?.currentBalance
      ?: account.openingBalance
    val cardReminder = creditCardBillReminders.firstOrNull { it.accountId == account.id }
    AccountDetailsDialog(
      account = account,
      currentBalance = currentBalance,
      billReminder = cardReminder,
      onDismiss = { accountForDetails = null },
      onEdit = {
        val target = account
        accountForDetails = null
        editingAccount = target
      },
      onDelete = {
        accountForDetails = null
        accountPendingDelete = account
      },
      onShowTransactions = {
        viewModel.setSelectedAccountFilter(account.id)
        accountForDetails = null
      },
      onSetBillReminder = {
        val targetId = account.id
        accountForDetails = null
        editingCreditCardAccountId = targetId
        showCreditCardReminderDialog = true
      },
      onMarkBillPaid = cardReminder?.let { rem ->
        {
          payingCreditCardAccount = account
          payingCreditCardReminder = rem
          accountForDetails = null
        }
      }
    )
  }

  // Edit Account / Credit Card Dialog
  editingAccount?.let { account ->
    if (account.accountType.equals("Credit Card", ignoreCase = true)) {
      EditCreditCardDialog(
        account = account,
        onDismiss = { editingAccount = null },
        onConfirm = { updated ->
          viewModel.updateBankAccount(updated)
          editingAccount = null
        }
      )
    } else {
      EditAccountDialog(
        account = account,
        onDismiss = { editingAccount = null },
        onConfirm = { updated ->
          viewModel.updateBankAccount(updated)
          editingAccount = null
        }
      )
    }
  }

  if (showCreditCardReminderDialog) {
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    val creditCardBillReminders by viewModel.creditCardBillReminders.collectAsState()
    val cardAccounts = remember(bankAccounts) {
      bankAccounts.filter { it.accountType.equals("Credit Card", ignoreCase = true) }
    }
    CreditCardBillReminderDialog(
      cardAccounts = cardAccounts,
      reminders = creditCardBillReminders,
      editingAccountId = editingCreditCardAccountId,
      onDismiss = {
        showCreditCardReminderDialog = false
        editingCreditCardAccountId = null
      },
      onSave = { reminder ->
        val accName = cardAccounts.firstOrNull { it.id == reminder.accountId }?.bankName ?: "Credit Card"
        viewModel.saveCreditCardBillReminder(reminder, accName)
        showCreditCardReminderDialog = false
        editingCreditCardAccountId = null
      }
    )
  }

  accountPendingDelete?.let { account ->
    AlertDialog(
      onDismissRequest = { accountPendingDelete = null },
      title = { Text("Delete account?") },
      text = {
        Text("Are you sure you want to delete \"${account.bankName}\"? This action cannot be undone.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (uiState.selectedAccountFilterId == account.id) {
              viewModel.setSelectedAccountFilter(null)
            }
            viewModel.deleteBankAccount(account)
            accountPendingDelete = null
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { accountPendingDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }

  if (uiState.showAddTripDialog) {
    AddTripDialog(
      onDismiss = { viewModel.setShowAddTrip(false) },
      onConfirm = { name, dest, budget, start, end, notes ->
        viewModel.addTrip(name, dest, budget, start, end, notes)
      }
    )
  }

  if (uiState.showAddSubscriptionDialog) {
    val categories by viewModel.categories.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    AddSubscriptionDialog(
      categories = categories,
      bankAccounts = bankAccounts,
      onDismiss = { viewModel.setShowAddSubscription(false) },
      onConfirm = { name, amount, cycle, day, month, weekday, cat, accId, accName, notes ->
        viewModel.addSubscription(name, amount, cycle, day, month, weekday, cat, accId, accName, notes)
      }
    )
  }

  if (uiState.showSetBudgetDialog) {
    val monthlyBudgetLimit by viewModel.monthlyBudgetLimit.collectAsState()
    SetMonthlyBudgetDialog(
      currentLimit = monthlyBudgetLimit,
      onDismiss = { viewModel.setShowSetBudget(false) },
      onConfirm = { newLimit ->
        viewModel.setMonthlyBudgetLimit(newLimit)
      }
    )
  }

  uiState.selectedTripForCategoryCap?.let { trip ->
    val categories by viewModel.categories.collectAsState()
    SetTripCategoryCapDialog(
      trip = trip,
      categories = categories,
      onDismiss = { viewModel.setSelectedTripForCategoryCap(null) },
      onConfirm = { catName, capAmount ->
        viewModel.updateTripCategoryCap(trip, catName, capAmount)
      }
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState)
    },
    floatingActionButton = {
      when (uiState.selectedTab) {
        FinanceTab.EXPENSES -> {
          FloatingActionButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.setShowAddExpense(true)
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.testTag("add_expense_fab")
          ) {
            Icon(Icons.Default.Add, contentDescription = "Log Expense")
          }
        }
        FinanceTab.CREDIT_CARDS -> {
          FloatingActionButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              showAddCreditCardDialog = true
            },
            containerColor = Color(0xFFE11D48),
            contentColor = Color.White,
            modifier = Modifier.testTag("add_credit_card_fab")
          ) {
            Icon(Icons.Default.AddCard, contentDescription = "Add Credit Card")
          }
        }
        FinanceTab.SUBSCRIPTIONS -> {
          FloatingActionButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.setShowAddSubscription(true)
            },
            containerColor = Color(0xFF6366F1),
            contentColor = Color.White,
            modifier = Modifier.testTag("add_sub_fab")
          ) {
            Icon(Icons.Default.Add, contentDescription = "Add Subscription")
          }
        }
        FinanceTab.TRIPS -> {
          FloatingActionButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.setShowAddTrip(true)
            },
            containerColor = Color(0xFF0D9488),
            contentColor = Color.White,
            modifier = Modifier.testTag("add_trip_fab")
          ) {
            Icon(Icons.Default.AddLocationAlt, contentDescription = "Add Trip")
          }
        }
        FinanceTab.SPLIT_BILL -> null
        FinanceTab.DEBTS -> null
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (uiState.isFinanceLocked) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth(0.85f)
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Box(
            modifier = Modifier
              .size(80.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.Fingerprint,
              contentDescription = "Finance Hub Locked",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(46.dp)
            )
          }

          Text(
            text = "Finance Hub is Locked",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )

          Text(
            text = "Biometric Privacy Shield is active to protect your personal bank accounts, credit cards, and ledger records.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Button(
            onClick = {
              val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
              if (fragmentActivity != null) {
                com.example.domain.security.BiometricLockManager.authenticate(
                  activity = fragmentActivity,
                  onSuccess = { viewModel.unlockFinance() },
                  onError = { /* Keep locked */ }
                )
              } else {
                viewModel.unlockFinance()
              }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
          ) {
            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Unlock with Fingerprint / PIN")
          }
        }
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .pointerInput(uiState.selectedTab) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
              awaitFirstDown(requireUnconsumed = false)
              var totalDx = 0f
              var totalDy = 0f
              var trackingHorizontal = false
              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) {
                  if (trackingHorizontal && abs(totalDx) > 90f) {
                    val tabs = FinanceTab.values()
                    val direction = if (totalDx < 0f) 1 else -1
                    val nextIndex = (uiState.selectedTab.ordinal + direction).coerceIn(tabs.indices)
                    if (nextIndex != uiState.selectedTab.ordinal) viewModel.selectTab(tabs[nextIndex])
                  }
                  break
                }
                if (change.isConsumed && !trackingHorizontal) {
                  break
                }
                val delta = change.positionChange()
                totalDx += delta.x
                totalDy += delta.y
                if (!trackingHorizontal) {
                  if (abs(totalDy) > touchSlop && abs(totalDy) >= abs(totalDx)) {
                    // Vertical scroll takes priority; exit immediately without interfering.
                    break
                  }
                  if (abs(totalDx) > touchSlop * 2f && abs(totalDx) > abs(totalDy) * 1.75f) {
                    trackingHorizontal = true
                  }
                }
                if (trackingHorizontal) {
                  change.consume()
                }
              }
            }
          }
      ) {
        // Modern Tab Bar (Expenses & Accounts, Credit Cards, Subscriptions, Lend & Borrow, Split Bill, Trip Manager)
        ScrollableTabRow(
        selectedTabIndex = uiState.selectedTab.ordinal,
        edgePadding = 12.dp,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().testTag("finance_tabs")
      ) {
        FinanceTab.values().forEach { tab ->
          val icon = when (tab) {
            FinanceTab.EXPENSES -> Icons.AutoMirrored.Filled.ReceiptLong
            FinanceTab.CREDIT_CARDS -> Icons.Default.CreditCard
            FinanceTab.SUBSCRIPTIONS -> Icons.Default.EventRepeat
            FinanceTab.DEBTS -> Icons.Default.SwapHoriz
            FinanceTab.SPLIT_BILL -> Icons.Default.CallSplit
            FinanceTab.TRIPS -> Icons.Default.FlightTakeoff
          }
          Tab(
            selected = uiState.selectedTab == tab,
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.selectTab(tab)
            },
            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(tab.title, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("finance_tab_${tab.name.lowercase()}")
          )
        }
      }

      when (uiState.selectedTab) {
        FinanceTab.EXPENSES -> ExpensesTabHost(
          viewModel = viewModel,
          selectedAccountFilterId = uiState.selectedAccountFilterId,
          snackbarHostState = snackbarHostState,
          onEditOpeningBalance = { editingAccountForOpeningBalance = it },
          onOpenAccountDetails = { accountForDetails = it },
          onTransferClick = { showTransferDialog = true },
          onEditExpense = { editingExpense = it },
          onSelectExpense = { selectedExpenseForDetails = it },
          onSetCreditCardBill = { accountId ->
            editingCreditCardAccountId = accountId
            showCreditCardReminderDialog = true
          },
          onPayCreditCardReminder = { account, reminder ->
            payingCreditCardAccount = account
            payingCreditCardReminder = reminder
          }
        )
        FinanceTab.CREDIT_CARDS -> CreditCardsTabHost(
          viewModel = viewModel,
          onAddCardClick = { showAddCreditCardDialog = true },
          onCardClick = { accountForDetails = it },
          onSetBillReminder = { accountId ->
            editingCreditCardAccountId = accountId
            showCreditCardReminderDialog = true
          },
          onPayCreditCardReminder = { account, reminder ->
            payingCreditCardAccount = account
            payingCreditCardReminder = reminder
          },
          onDeleteCard = { account -> accountPendingDelete = account }
        )
        FinanceTab.SUBSCRIPTIONS -> SubscriptionsTabHost(
          viewModel = viewModel,
          onSelectSubscription = { selectedSubscriptionForDetails = it },
          onMarkPaid = { subscriptionPendingPayment = it }
        )
        FinanceTab.DEBTS -> {
          val debtViewModel: DebtTrackerViewModel = viewModel()
          DebtTrackerScreen(
            viewModel = debtViewModel,
            modifier = Modifier.fillMaxSize()
          )
        }
        FinanceTab.SPLIT_BILL -> SplitBillTabHost(viewModel = viewModel)
        FinanceTab.TRIPS -> TripsTabHost(
          viewModel = viewModel,
          onAddExpenseForTrip = { trip ->
            preselectedTripIdForExpense = trip.id
            viewModel.setShowAddExpense(true)
          },
          onEditTrip = { editingTrip = it },
          onTripExpenseClick = { selectedExpenseForDetails = it }
        )
      }
    }
  }
}
}

@Composable
private fun ExpensesTabHost(
  viewModel: FinanceHubViewModel,
  selectedAccountFilterId: Long?,
  snackbarHostState: SnackbarHostState,
  onEditOpeningBalance: (BankAccount) -> Unit,
  onOpenAccountDetails: (BankAccount) -> Unit,
  onTransferClick: () -> Unit,
  onEditExpense: (ExpenseRecord) -> Unit,
  onSelectExpense: (ExpenseRecord) -> Unit,
  onSetCreditCardBill: (Long) -> Unit,
  onPayCreditCardReminder: (BankAccount, CreditCardBillReminder) -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val expenses by viewModel.expenses.collectAsState()
  val categories by viewModel.categories.collectAsState()
  val accountsWithBalances by viewModel.accountsWithBalances.collectAsState()
  val creditCardBillReminders by viewModel.creditCardBillReminders.collectAsState()
  val totalNetBalance by viewModel.totalNetBalance.collectAsState()
  val totalIncome by viewModel.totalIncome.collectAsState()
  val totalExpense by viewModel.totalExpense.collectAsState()
  val monthlyBudgetLimit by viewModel.monthlyBudgetLimit.collectAsState()
  val currentMonthExpenses by viewModel.currentMonthExpenses.collectAsState()
  val bankAccounts by viewModel.bankAccounts.collectAsState()

  ExpensesSection(
    expenses = expenses,
    categories = categories,
    accountsWithBalances = accountsWithBalances,
    creditCardBillReminders = creditCardBillReminders,
    totalNetBalance = totalNetBalance,
    totalIncome = totalIncome,
    totalExpense = totalExpense,
    monthlyBudgetLimit = monthlyBudgetLimit,
    currentMonthExpenses = currentMonthExpenses,
    onEditBudgetClick = { viewModel.setShowSetBudget(true) },
    selectedAccountFilterId = selectedAccountFilterId,
    onSelectAccountFilter = { viewModel.setSelectedAccountFilter(it) },
    onAddAccountClick = { viewModel.setShowAddBank(true) },
    onEditOpeningBalance = onEditOpeningBalance,
    onOpenAccountDetails = onOpenAccountDetails,
    onAddExpenseClick = { viewModel.setShowAddExpense(true) },
    onAddIncomeClick = { viewModel.setShowAddIncome(true) },
    onPasteSmsClick = { viewModel.logFromClipboardSms(context) },
    onTransferClick = onTransferClick,
    onAddCategoryClick = { viewModel.setShowAddCategory(true) },
    onDeleteCategory = { viewModel.deleteCategory(it) },
    onDeleteExpenses = { deletedExpenses ->
      viewModel.deleteExpensesWithUndo(deletedExpenses) { undo ->
        coroutineScope.launch {
          val res = snackbarHostState.showSnackbar(
            message = if (deletedExpenses.size > 1) {
              "Deleted both entries for this transfer"
            } else {
              "Deleted '${deletedExpenses.first().title}'"
            },
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
          )
          if (res == SnackbarResult.ActionPerformed) {
            undo()
          }
        }
      }
    },
    onEditExpense = onEditExpense,
    onSelectExpense = onSelectExpense,
    onSetCreditCardBill = onSetCreditCardBill,
    onMarkCreditCardBillPaid = { reminder ->
      val cardAcc = bankAccounts.find { it.id == reminder.accountId }
      if (cardAcc != null) {
        onPayCreditCardReminder(cardAcc, reminder)
      } else {
        viewModel.markCreditCardBillPaid(reminder)
      }
    }
  )
}

@Composable
private fun CreditCardsTabHost(
  viewModel: FinanceHubViewModel,
  onAddCardClick: () -> Unit,
  onCardClick: (BankAccount) -> Unit,
  onSetBillReminder: (Long) -> Unit,
  onPayCreditCardReminder: (BankAccount, CreditCardBillReminder) -> Unit,
  onDeleteCard: (BankAccount) -> Unit
) {
  val accountsWithBalances by viewModel.accountsWithBalances.collectAsState()
  val reminders by viewModel.creditCardBillReminders.collectAsState()
  val bankAccounts by viewModel.bankAccounts.collectAsState()
  val cardAccounts = remember(accountsWithBalances) {
    accountsWithBalances.filter { it.account.accountType.equals("Credit Card", ignoreCase = true) }
  }

  CreditCardsSection(
    cardAccounts = cardAccounts,
    reminders = reminders,
    onAddCardClick = onAddCardClick,
    onCardClick = onCardClick,
    onSetBillReminder = onSetBillReminder,
    onMarkBillPaid = { reminder ->
      val cardAcc = bankAccounts.find { it.id == reminder.accountId }
      if (cardAcc != null) {
        onPayCreditCardReminder(cardAcc, reminder)
      } else {
        viewModel.markCreditCardBillPaid(reminder)
      }
    },
    onShowTransactions = { accountId ->
      viewModel.setSelectedAccountFilter(accountId)
      viewModel.selectTab(FinanceTab.EXPENSES)
    },
    onDeleteCard = onDeleteCard
  )
}

@Composable
private fun SubscriptionsTabHost(
  viewModel: FinanceHubViewModel,
  onSelectSubscription: (SubscriptionRecord) -> Unit,
  onMarkPaid: (SubscriptionRecord) -> Unit
) {
  val subscriptions by viewModel.subscriptions.collectAsState()
  SubscriptionsSection(
    subscriptions = subscriptions,
    onAddSubscriptionClick = { viewModel.setShowAddSubscription(true) },
    onSelectSubscription = onSelectSubscription,
    onMarkPaid = onMarkPaid,
    onDeleteSubscription = { viewModel.deleteSubscription(it) }
  )
}

@Composable
private fun SplitBillTabHost(viewModel: FinanceHubViewModel) {
  val bankAccounts by viewModel.bankAccounts.collectAsState()
  val trips by viewModel.trips.collectAsState()
  val expenses by viewModel.expenses.collectAsState()
  val splitBillViewModel: com.example.ui.screens.splitbill.SplitBillViewModel = viewModel()
  com.example.ui.screens.splitbill.SplitBillScreen(
    viewModel = splitBillViewModel,
    modifier = Modifier.fillMaxSize(),
    accounts = bankAccounts,
    trips = trips,
    tripExpenses = expenses,
    onRecordSettlement = { isIncome, amount, account, title, reference ->
      if (isIncome) {
        viewModel.addIncome(title, amount, "Split Bill", account.id, account.bankName, reference)
      } else {
        viewModel.addExpense(title, amount, "Split Bill", account.id, account.bankName, reference)
      }
    },
    onUndoSettlement = { reference ->
      expenses.firstOrNull { it.notes == reference && it.categoryName == "Split Bill" }?.let(viewModel::deleteExpense)
    },
    onSyncExpense = { reference, title, amount, account -> viewModel.syncSplitBillExpense(reference, title, amount, account) },
    onDeleteExpense = { reference -> viewModel.deleteSplitBillExpense(reference) }
  )
}

@Composable
private fun TripsTabHost(
  viewModel: FinanceHubViewModel,
  onAddExpenseForTrip: (TripRecord) -> Unit,
  onEditTrip: (TripRecord) -> Unit,
  onTripExpenseClick: (ExpenseRecord) -> Unit
) {
  val context = LocalContext.current
  val trips by viewModel.trips.collectAsState()
  val expenses by viewModel.expenses.collectAsState()
  TripsSection(
    trips = trips,
    expenses = expenses,
    onAddExpenseForTrip = onAddExpenseForTrip,
    onEditTrip = onEditTrip,
    onTripExpenseClick = onTripExpenseClick,
    onSetCategoryCap = { trip -> viewModel.setSelectedTripForCategoryCap(trip) },
    onRemoveCategoryCap = { trip, cat -> viewModel.removeTripCategoryCap(trip, cat) },
    onExportTripPdf = { trip, tripExpenses ->
      try {
        val pdfFile = PdfReportGenerator.generateTripReport(context, trip, tripExpenses)
        PdfReportGenerator.sharePdf(context, pdfFile)
      } catch (e: Exception) {
        viewModel.showToast("Failed to generate PDF: ${e.message}")
      }
    },
    onDeleteTrip = { viewModel.deleteTrip(it) }
  )
}

@Composable
private fun AccountDetailsDialog(
  account: BankAccount,
  currentBalance: Double,
  billReminder: CreditCardBillReminder? = null,
  onDismiss: () -> Unit,
  onEdit: () -> Unit = {},
  onDelete: () -> Unit,
  onShowTransactions: () -> Unit,
  onSetBillReminder: (() -> Unit)? = null,
  onMarkBillPaid: (() -> Unit)? = null
) {
  val isCreditCard = account.accountType.equals("Credit Card", ignoreCase = true)
  val todayEpochDay = LocalDate.now().toEpochDay()
  val isBillPaid = billReminder?.paidDateEpochDay?.let { it >= 0L } == true
  val daysUntilDue = billReminder?.let { (it.dueDateEpochDay - todayEpochDay).toInt() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          if (isCreditCard) Icons.Default.CreditCard else Icons.Default.AccountBalance,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(24.dp)
        )
        Text(account.bankName, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
          Text(
            account.accountType,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }

        if (account.accountNumber.isNotBlank()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Account number", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              if (isCreditCard) "•••• " + account.accountNumber.takeLast(4) else account.accountNumber,
              fontWeight = FontWeight.Medium
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Opening balance", color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(CurrencyFormatter.formatWhole(account.openingBalance), fontWeight = FontWeight.SemiBold)
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(if (isCreditCard) "Outstanding balance" else "Current balance", color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            CurrencyFormatter.format(currentBalance),
            fontWeight = FontWeight.Bold,
            color = if (isCreditCard) {
              if (currentBalance < 0) Color(0xFFEF4444) else Color(0xFF10B981)
            } else {
              if (currentBalance >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
            }
          )
        }

        if (isCreditCard) {
          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          Text(
            "Credit Card Bill Details",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )

          if (billReminder != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Bill Amount", color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(CurrencyFormatter.format(billReminder.amount), fontWeight = FontWeight.Bold)
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Due Date", color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                LocalDate.ofEpochDay(billReminder.dueDateEpochDay).format(
                  DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                ),
                fontWeight = FontWeight.Medium
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Status", color = MaterialTheme.colorScheme.onSurfaceVariant)
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = when {
                  isBillPaid -> Color(0xFF10B981).copy(alpha = 0.15f)
                  daysUntilDue != null && daysUntilDue <= 3 -> Color(0xFFEF4444).copy(alpha = 0.15f)
                  else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                }
              ) {
                Text(
                  text = when {
                    isBillPaid -> "Paid ✓"
                    daysUntilDue == 0 -> "Due Today"
                    daysUntilDue == 1 -> "Due Tomorrow"
                    daysUntilDue != null && daysUntilDue < 0 -> "${-daysUntilDue} days overdue"
                    else -> "Due in ${daysUntilDue ?: 0} days"
                  },
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = when {
                    isBillPaid -> Color(0xFF10B981)
                    daysUntilDue != null && daysUntilDue <= 3 -> Color(0xFFEF4444)
                    else -> MaterialTheme.colorScheme.primary
                  },
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              onSetBillReminder?.let {
                OutlinedButton(
                  onClick = it,
                  modifier = Modifier.weight(1f),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                  Text("Update Bill", fontSize = 12.sp, maxLines = 1)
                }
              }

              if (!isBillPaid && onMarkBillPaid != null) {
                Button(
                  onClick = onMarkBillPaid,
                  modifier = Modifier.weight(1f),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                  Text("Mark Paid", fontSize = 12.sp, maxLines = 1)
                }
              }
            }
          } else {
            Text(
              "No bill reminder set for this card yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            onSetBillReminder?.let {
              OutlinedButton(
                onClick = it,
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.AddAlarm, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Set Bill Amount & Due Date")
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onEdit) {
          Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text(if (isCreditCard) "Edit Card" else "Edit Account")
        }
        FilledTonalButton(
          onClick = onShowTransactions,
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
        ) {
          Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Transactions", maxLines = 1, softWrap = false, fontSize = 12.sp)
        }
      }
    },
    dismissButton = {
      Row {
        TextButton(
          onClick = onDelete,
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Delete")
        }
        TextButton(onClick = onDismiss) {
          Text("Close")
        }
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAccountDialog(
  account: BankAccount,
  onDismiss: () -> Unit,
  onConfirm: (BankAccount) -> Unit
) {
  var bankName by remember(account.id) { mutableStateOf(account.bankName) }
  var accountType by remember(account.id) { mutableStateOf(account.accountType) }
  var accountNumber by remember(account.id) { mutableStateOf(account.accountNumber) }
  var openingBalText by remember(account.id) { mutableStateOf(account.openingBalance.toString()) }
  var notes by remember(account.id) { mutableStateOf(account.notes) }

  val openingBal = openingBalText.toDoubleOrNull() ?: 0.0

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Account", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = bankName,
          onValueChange = { bankName = it },
          label = { Text("Bank / Account Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf("Savings", "Current", "Cash Wallet", "Salary", "Other").forEach { type ->
            FilterChip(
              selected = accountType.equals(type, ignoreCase = true),
              onClick = { accountType = type },
              label = { Text(type, fontSize = 11.sp) }
            )
          }
        }

        OutlinedTextField(
          value = openingBalText,
          onValueChange = { openingBalText = it },
          label = { Text("Opening Balance (₹) *") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = accountNumber,
          onValueChange = { accountNumber = it },
          label = { Text("Account Number (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (bankName.isNotBlank()) {
            onConfirm(
              account.copy(
                bankName = bankName.trim(),
                accountType = accountType,
                openingBalance = openingBal,
                accountNumber = accountNumber.trim(),
                notes = notes.trim()
              )
            )
          }
        },
        enabled = bankName.isNotBlank()
      ) {
        Text("Save Changes")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCreditCardDialog(
  account: BankAccount,
  onDismiss: () -> Unit,
  onConfirm: (BankAccount) -> Unit
) {
  var cardName by remember(account.id) { mutableStateOf(account.bankName) }
  var lastDigits by remember(account.id) { mutableStateOf(account.accountNumber) }
  var openingBalText by remember(account.id) { mutableStateOf(kotlin.math.abs(account.openingBalance).toString()) }
  var notes by remember(account.id) { mutableStateOf(account.notes) }

  val openingBal = openingBalText.toDoubleOrNull() ?: 0.0

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Credit Card", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = cardName,
          onValueChange = { cardName = it },
          label = { Text("Card / Bank Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = lastDigits,
          onValueChange = { if (it.length <= 16) lastDigits = it.filter { ch -> ch.isDigit() } },
          label = { Text("Card Number / Last 4 Digits") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = openingBalText,
          onValueChange = { openingBalText = it },
          label = { Text("Opening Outstanding / Dues (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (cardName.isNotBlank()) {
            onConfirm(
              account.copy(
                bankName = cardName.trim(),
                openingBalance = -kotlin.math.abs(openingBal),
                accountNumber = lastDigits.trim(),
                notes = notes.trim()
              )
            )
          }
        },
        enabled = cardName.isNotBlank()
      ) {
        Text("Save Changes")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
private fun TransferBetweenAccountsDialog(
  accounts: List<BankAccount>,
  onDismiss: () -> Unit,
  onConfirm: (from: BankAccount, to: BankAccount, amount: Double, notes: String) -> Unit
) {
  var fromAccountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
  var toAccountId by remember(accounts) {
    mutableStateOf(accounts.firstOrNull { it.id != fromAccountId }?.id)
  }
  var amountText by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var fromMenuExpanded by remember { mutableStateOf(false) }
  var toMenuExpanded by remember { mutableStateOf(false) }
  val fromAccount = accounts.firstOrNull { it.id == fromAccountId }
  val toAccount = accounts.firstOrNull { it.id == toAccountId }
  val amount = amountText.toDoubleOrNull()
  val canTransfer = fromAccount != null && toAccount != null &&
    fromAccount.id != toAccount.id && amount != null && amount.isFinite() && amount > 0.0

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Transfer Between Accounts") },
    text = {
      Column(
        modifier = Modifier
          .heightIn(max = 420.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box {
          OutlinedButton(
            onClick = { fromMenuExpanded = true },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("From: ${fromAccount?.bankName ?: "Choose account"}")
          }
          DropdownMenu(
            expanded = fromMenuExpanded,
            onDismissRequest = { fromMenuExpanded = false }
          ) {
            accounts.forEach { account ->
              DropdownMenuItem(
                text = { Text(account.bankName) },
                onClick = {
                  fromAccountId = account.id
                  if (toAccountId == account.id) {
                    toAccountId = accounts.firstOrNull { it.id != account.id }?.id
                  }
                  fromMenuExpanded = false
                }
              )
            }
          }
        }

        Box {
          OutlinedButton(
            onClick = { toMenuExpanded = true },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("To: ${toAccount?.bankName ?: "Choose account"}")
          }
          DropdownMenu(
            expanded = toMenuExpanded,
            onDismissRequest = { toMenuExpanded = false }
          ) {
            accounts.filter { it.id != fromAccountId }.forEach { account ->
              DropdownMenuItem(
                text = { Text(account.bankName) },
                onClick = {
                  toAccountId = account.id
                  toMenuExpanded = false
                }
              )
            }
          }
        }

        NumberInputField(
          value = amountText,
          onValueChange = { amountText = it },
          label = "Amount",
          prefix = "₹"
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Text(
          text = "Transfers are excluded from income, expense, and budget totals.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val source = fromAccount
          val destination = toAccount
          val transferAmount = amount
          if (
            source != null && destination != null && transferAmount != null &&
            source.id != destination.id && transferAmount.isFinite() && transferAmount > 0.0
          ) {
            onConfirm(source, destination, transferAmount, notes)
          }
        },
        enabled = canTransfer
      ) {
        Text("Transfer")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

// -------------------------------------------------------------
// EXPENSES & CUSTOM CATEGORIES SECTION
// -------------------------------------------------------------
@Composable
private fun ExpensesSection(
  expenses: List<ExpenseRecord>,
  categories: List<ExpenseCategory>,
  accountsWithBalances: List<AccountWithBalance>,
  creditCardBillReminders: List<CreditCardBillReminder>,
  totalNetBalance: Double,
  totalIncome: Double,
  totalExpense: Double,
  monthlyBudgetLimit: Double,
  currentMonthExpenses: Double,
  onEditBudgetClick: () -> Unit,
  selectedAccountFilterId: Long?,
  onSelectAccountFilter: (Long?) -> Unit,
  onAddAccountClick: () -> Unit,
  onEditOpeningBalance: (BankAccount) -> Unit,
  onOpenAccountDetails: (BankAccount) -> Unit,
  onAddExpenseClick: () -> Unit,
  onAddIncomeClick: () -> Unit,
  onPasteSmsClick: () -> Unit,
  onTransferClick: () -> Unit,
  onAddCategoryClick: () -> Unit,
  onDeleteCategory: (ExpenseCategory) -> Unit,
  onDeleteExpenses: (List<ExpenseRecord>) -> Unit,
  onEditExpense: (ExpenseRecord) -> Unit,
  onSelectExpense: (ExpenseRecord) -> Unit = {},
  onSetCreditCardBill: (Long) -> Unit,
  onMarkCreditCardBillPaid: (CreditCardBillReminder) -> Unit
) {
  val haptic = LocalHapticFeedback.current

  // Filter transactions by selected bank account if filter is active
  val displayedExpenses = remember(expenses, selectedAccountFilterId) {
    if (selectedAccountFilterId == null) {
      expenses
    } else {
      expenses.filter { it.bankAccountId == selectedAccountFilterId }
    }
  }
  val transactionCategories = remember(displayedExpenses) {
    displayedExpenses
      .map { it.categoryName }
      .distinctBy { it.lowercase() }
      .sortedBy { it.lowercase() }
  }

  val creditCardAccountIds = remember(accountsWithBalances) {
    accountsWithBalances.asSequence()
      .filter { it.account.accountType.equals("Credit Card", ignoreCase = true) }
      .map { it.account.id }
      .toSet()
  }
  val creditCardAccounts = remember(accountsWithBalances) {
    accountsWithBalances.filter { it.account.id in creditCardAccountIds }
  }
  val bankAndCashAccounts = remember(accountsWithBalances) {
    accountsWithBalances.filter { !it.account.accountType.equals("Credit Card", ignoreCase = true) }
  }
  val categoryColorMap = remember(categories) {
    categories.associate { cat ->
      val parsed = try {
        Color(android.graphics.Color.parseColor(cat.colorHex))
      } catch (_: Exception) {
        Color(0xFF6366F1)
      }
      cat.name.lowercase() to parsed
    }
  }
  val remindersByAccountId = remember(creditCardBillReminders) {
    creditCardBillReminders.associateBy { it.accountId }
  }
  val accountNameById = remember(accountsWithBalances) {
    accountsWithBalances.associate { it.account.id to it.account.bankName }
  }
  val (expenseTransactions, totalSpentOnExpenses) = remember(displayedExpenses) {
    val expList = displayedExpenses.filter { it.type == "EXPENSE" }
    expList to expList.sumOf { it.amount }
  }
  var selectedTransactionType by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedTransactionCategory by rememberSaveable { mutableStateOf<String?>(null) }
  var transactionSearch by rememberSaveable { mutableStateOf("") }
  var selectedTransactionPeriod by rememberSaveable { mutableStateOf("ALL") }
  var expensePendingDelete by remember { mutableStateOf<ExpenseRecord?>(null) }
  LaunchedEffect(selectedAccountFilterId) {
    selectedTransactionCategory = null
  }
  val filteredExpenses = remember(
    displayedExpenses,
    selectedTransactionType,
    selectedTransactionCategory,
    transactionSearch,
    selectedTransactionPeriod
  ) {
    val now = Calendar.getInstance()
    val periodStart = when (selectedTransactionPeriod) {
      "WEEK" -> (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
      "MONTH" -> (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
      "LAST_MONTH" -> (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
      else -> null
    }
    val periodEnd = if (selectedTransactionPeriod == "LAST_MONTH") {
      (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    } else null
    val query = transactionSearch.trim()
    displayedExpenses.filter { expense ->
      val matchesType = when (selectedTransactionType) {
        "EXPENSE" -> expense.type == "EXPENSE"
        "INCOME" -> expense.type == "INCOME"
        "TRANSFER" -> expense.type == "TRANSFER_IN" || expense.type == "TRANSFER_OUT"
        else -> true
      }
      val matchesCategory = selectedTransactionCategory == null ||
        expense.categoryName.equals(selectedTransactionCategory, ignoreCase = true)
      val matchesSearch = query.isBlank() ||
        expense.title.contains(query, ignoreCase = true) ||
        expense.categoryName.contains(query, ignoreCase = true) ||
        expense.bankAccountName.contains(query, ignoreCase = true) ||
        expense.notes.contains(query, ignoreCase = true)
      val matchesPeriod = (periodStart == null || expense.dateTimestamp >= periodStart) &&
        (periodEnd == null || expense.dateTimestamp < periodEnd)
      matchesType && matchesCategory && matchesSearch && matchesPeriod
    }
  }

  val listState = rememberLazyListState()

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Total Net Balance Dashboard Banner
    item(key = "total_net_balance_card", contentType = "balance_hero") {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("total_net_balance_card")
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.linearGradient(
                colors = listOf(
                  Color(0xFF1E1B4B), // Deep midnight indigo
                  Color(0xFF312E81), // Rich royal indigo
                  Color(0xFF4338CA)  // Radiant electric indigo
                ),
                start = Offset(0f, 0f),
                end = Offset(1000f, 1000f)
              )
            )
            .padding(20.dp)
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            // Header row with Icon, Label and Live Indicator Pill
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(17.dp)
                  )
                }
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                  text = "Total Net Balance",
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                  ),
                  color = Color(0xFFE0E7FF)
                )
              }

              // Live Funds Badge
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF10B981).copy(alpha = 0.22f),
                border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.45f))
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(6.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF34D399))
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = "Live Funds",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6EE7B7)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Balance Number: Bright, glowing white typography
            Text(
              text = CurrencyFormatter.format(totalNetBalance),
              style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
              ),
              color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Income and Expense Stat Chips - high contrast, glowing vibrant badges
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // Total Income Chip
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.35f)),
                modifier = Modifier.weight(1f)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(28.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF059669).copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      Icons.Default.ArrowDownward,
                      contentDescription = null,
                      tint = Color(0xFF34D399),
                      modifier = Modifier.size(16.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Total Income",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Medium,
                      color = Color.White.copy(alpha = 0.75f)
                    )
                    Text(
                      text = "+${CurrencyFormatter.formatWhole(totalIncome)}",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = Color(0xFF34D399),
                      maxLines = 1,
                      softWrap = false,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              }

              // Total Expenses Chip
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.35f)),
                modifier = Modifier.weight(1f)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(28.dp)
                      .clip(CircleShape)
                      .background(Color(0xFFDC2626).copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      Icons.Default.ArrowUpward,
                      contentDescription = null,
                      tint = Color(0xFFF87171),
                      modifier = Modifier.size(16.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Total Expenses",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Medium,
                      color = Color.White.copy(alpha = 0.75f)
                    )
                    Text(
                      text = "-${CurrencyFormatter.formatWhole(totalExpense)}",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = Color(0xFFF87171),
                      maxLines = 1,
                      softWrap = false,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Monthly Expense Budget Progress Bar & Alerts
    item(key = "monthly_budget_card", contentType = "monthly_budget") {
      MonthlyBudgetCard(
        spent = currentMonthExpenses,
        limit = monthlyBudgetLimit,
        onEditBudget = onEditBudgetClick
      )
    }

    // Accounts & Balances Carousel
    item(key = "accounts_carousel_header", contentType = "section_header") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
          Text(
            text = "My Bank & Cash Accounts",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "Track opening balance & live account funds",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        FilledTonalButton(
          onClick = {
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
            onAddAccountClick()
          },
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.defaultMinSize(minHeight = 36.dp)
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("+ Account", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
      }
    }

    item(key = "accounts_carousel_list", contentType = "accounts_carousel") {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(bankAndCashAccounts, key = { it.account.id }, contentType = { "account_card" }) { accWithBal ->
          val isSelected = selectedAccountFilterId == accWithBal.account.id
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              }
            ),
            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
              .width(200.dp)
              .clickable {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenAccountDetails(accWithBal.account)
              }
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                  Text(
                    text = accWithBal.account.accountType,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }

                if (isSelected) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary
                  ) {
                    Text(
                      text = "Filtered",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onPrimary,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                } else {
                  Icon(
                    imageVector = when (accWithBal.account.accountType.lowercase()) {
                      "cash", "cash wallet" -> Icons.Default.Payments
                      "salary account" -> Icons.Default.Work
                      else -> Icons.Default.AccountBalance
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                  )
                }
              }

              Spacer(modifier = Modifier.height(6.dp))

              Text(
                text = accWithBal.account.bankName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )

              Spacer(modifier = Modifier.height(4.dp))

              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Opening: ",
                  fontSize = 10.5.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = CurrencyFormatter.formatWhole(accWithBal.openingBalance),
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }

              Spacer(modifier = Modifier.height(6.dp))

              Text(
                text = "Current Balance",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = CurrencyFormatter.format(accWithBal.currentBalance),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (accWithBal.currentBalance >= 0) Color(0xFF059669) else Color(0xFFDC2626)
              )
            }
          }
        }

        // Add Account Card
        item(key = "add_account_card", contentType = "add_account_card") {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
              .width(130.dp)
              .height(130.dp)
              .clickable { onAddAccountClick() }
          ) {
            Column(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                Icons.Default.AddCard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "+ Add Account",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      }
    }

    // Dedicated Credit Card Bill & Outstandings Tracker Section
    if (creditCardAccounts.isNotEmpty()) {
      item(key = "credit_card_bills_tracker_widget", contentType = "credit_card_bills_tracker") {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("credit_card_bills_tracker_widget")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(18.dp)
                  )
                }
                Column {
                  Text(
                    "Credit Card Bills & Alerts",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    "${creditCardAccounts.size} active card${if (creditCardAccounts.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              FilledTonalButton(
                onClick = {
                  haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onSetCreditCardBill(creditCardAccounts.first().account.id)
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.defaultMinSize(minHeight = 32.dp)
              ) {
                Text("+ Set Bill", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              creditCardAccounts.forEach { accWithBal ->
                val rem = remindersByAccountId[accWithBal.account.id]
                val isPaid = rem?.paidDateEpochDay?.let { it >= 0L } == true
                val daysDue = rem?.let { (it.dueDateEpochDay - LocalDate.now().toEpochDay()).toInt() }

                Card(
                  shape = RoundedCornerShape(12.dp),
                  colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAccountDetails(accWithBal.account) }
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        accWithBal.account.bankName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        "Outstanding: ${CurrencyFormatter.format(accWithBal.currentBalance)}",
                        fontSize = 11.sp,
                        color = if (accWithBal.currentBalance < 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                      if (rem != null) {
                        Text(
                          CurrencyFormatter.format(rem.amount),
                          fontWeight = FontWeight.ExtraBold,
                          fontSize = 13.sp,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                          Text(
                            when {
                              isPaid -> "Paid ✓"
                              daysDue == 0 -> "Due Today"
                              daysDue == 1 -> "Due Tomorrow"
                              daysDue != null && daysDue < 0 -> "${-daysDue}d overdue"
                              else -> "Due in ${daysDue ?: 0}d"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                              isPaid -> Color(0xFF10B981)
                              daysDue != null && daysDue <= 3 -> Color(0xFFEF4444)
                              else -> MaterialTheme.colorScheme.primary
                            }
                          )
                          IconButton(
                            onClick = { onSetCreditCardBill(accWithBal.account.id) },
                            modifier = Modifier.size(22.dp)
                          ) {
                            Icon(
                              Icons.Default.Edit,
                              contentDescription = "Edit reminder",
                              modifier = Modifier.size(13.dp),
                              tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                          }
                          if (!isPaid) {
                            FilledTonalButton(
                              onClick = {
                                haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                                onMarkCreditCardBillPaid(rem)
                              },
                              shape = RoundedCornerShape(6.dp),
                              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                              modifier = Modifier.defaultMinSize(minHeight = 24.dp)
                            ) {
                              Text("Pay", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                          }
                        }
                      } else {
                        OutlinedButton(
                          onClick = { onSetCreditCardBill(accWithBal.account.id) },
                          shape = RoundedCornerShape(8.dp),
                          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                          modifier = Modifier.defaultMinSize(minHeight = 28.dp)
                        ) {
                          Text("+ Set Bill", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // Quick Action Buttons (+ Expense, + Income, + Category)
    item(key = "quick_actions_bar", contentType = "quick_actions") {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilledTonalButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              onAddExpenseClick()
            },
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
              contentColor = Color(0xFFDC2626)
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text("- Expense", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, softWrap = false)
          }

          FilledTonalButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              onAddIncomeClick()
            },
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = Color(0xFF10B981).copy(alpha = 0.15f),
              contentColor = Color(0xFF059669)
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text("+ Income", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, softWrap = false)
          }

          OutlinedButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              onPasteSmsClick()
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text("Paste SMS", fontSize = 11.sp, maxLines = 1, softWrap = false)
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              onTransferClick()
            },
            enabled = accountsWithBalances.size >= 2,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text("Transfer", fontSize = 11.sp, maxLines = 1, softWrap = false)
          }

          OutlinedButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              onAddCategoryClick()
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.Sell, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text("Category", fontSize = 11.sp, maxLines = 1, softWrap = false)
          }
        }
      }
    }

    if (totalSpentOnExpenses > 0) {
      item(key = "spending_analytics", contentType = "spending_analytics") {
        SpendingAnalyticsCard(
          expenses = expenseTransactions,
          categories = categories,
          totalSpent = totalSpentOnExpenses,
          selectedCategory = selectedTransactionCategory,
          onSelectCategory = { selectedTransactionCategory = it }
        )
      }
    }

    // Category shortcut chips removed to reduce clutter above Recent Transactions.

    // Recent Transactions Title and Filter indicator
    item(key = "expenses_title", contentType = "section_header") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (selectedAccountFilterId != null) {
            val accName = accountNameById[selectedAccountFilterId] ?: "Account"
            "Transactions: $accName"
          } else {
            "Recent Transactions"
          },
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        if (selectedAccountFilterId != null) {
          TextButton(onClick = { onSelectAccountFilter(null) }) {
            Text("Show All Accounts", fontSize = 12.sp)
          }
        }
      }
    }

    item(key = "transaction_filters", contentType = "transaction_filters") {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
          OutlinedTextField(
            value = transactionSearch,
            onValueChange = { transactionSearch = it },
            modifier = Modifier
              .fillMaxWidth()
              .height(58.dp)
              .testTag("transaction_search"),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = {
              Text(
                "Search merchant, category, account or note",
                maxLines = 1,
                fontSize = 13.sp,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search transactions", modifier = Modifier.size(20.dp)) },
            trailingIcon = {
              if (transactionSearch.isNotEmpty()) {
                IconButton(onClick = { transactionSearch = "" }) {
                  Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
              }
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
          ) {
            TRANSACTION_PERIOD_OPTIONS.forEach { (period, label) ->
              val selected = selectedTransactionPeriod == period
              FilterChip(
                selected = selected,
                onClick = { selectedTransactionPeriod = period },
                label = { Text(label, maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = when (period) {
                    "WEEK" -> Color(0xFFDBEAFE)
                    "MONTH" -> Color(0xFFD1FAE5)
                    "LAST_MONTH" -> Color(0xFFFFEDD5)
                    else -> Color(0xFFEDE9FE)
                  },
                  selectedLabelColor = when (period) {
                    "WEEK" -> Color(0xFF1D4ED8)
                    "MONTH" -> Color(0xFF047857)
                    "LAST_MONTH" -> Color(0xFFC2410C)
                    else -> Color(0xFF5B21B6)
                  },
                  selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant)
              )
            }
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
          ) {
            TRANSACTION_TYPE_OPTIONS.forEach { (type, label) ->
              val selected = selectedTransactionType == type
              val chipColor = when (type) {
                "EXPENSE" -> Color(0xFFDC2626)
                "INCOME" -> Color(0xFF059669)
                "TRANSFER" -> Color(0xFF2563EB)
                else -> Color(0xFF5B4AE8)
              }
              FilterChip(
                selected = selected,
                onClick = { selectedTransactionType = type },
                label = { Text(label, maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = chipColor.copy(alpha = 0.14f),
                  selectedLabelColor = chipColor,
                  selectedLeadingIconColor = chipColor
                ),
                border = BorderStroke(1.dp, if (selected) chipColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant)
              )
            }
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
          ) {
            val allSelected = selectedTransactionCategory == null
            FilterChip(
              selected = allSelected,
              onClick = { selectedTransactionCategory = null },
              label = { Text("All Categories", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFEDE9FE),
                selectedLabelColor = Color(0xFF5B21B6)
              ),
              border = BorderStroke(1.dp, if (allSelected) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.outlineVariant)
            )
            transactionCategories.forEach { category ->
              val selected = selectedTransactionCategory.equals(category, ignoreCase = true)
              val categoryColor = categoryColorMap[category.lowercase()] ?: Color(0xFF0D9488)
              FilterChip(
                selected = selected,
                onClick = {
                  selectedTransactionCategory =
                    if (selected) null else category
                },
                label = { Text(category, maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = categoryColor.copy(alpha = 0.15f),
                  selectedLabelColor = categoryColor
                ),
                border = BorderStroke(1.dp, if (selected) categoryColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant)
              )
            }
          }
        }
      }
    }

    if (filteredExpenses.isEmpty()) {
      item(key = "empty_expenses", contentType = "empty_state") {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              Icons.AutoMirrored.Filled.ReceiptLong,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = if (displayedExpenses.isEmpty()) "No transactions logged yet" else "No matching transactions",
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = if (displayedExpenses.isEmpty()) {
                "Tap '+ Expense' or '+ Income' above to record transactions"
              } else {
                "Try changing the selected type or category filters."
              },
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }
        }
      }
    } else {
      items(
        items = filteredExpenses,
        key = { it.id },
        contentType = { "expense_item" }
      ) { expense ->
        val isCc = expense.bankAccountId in creditCardAccountIds
        val catColor = categoryColorMap[expense.categoryName.lowercase()] ?: Color(0xFF6366F1)
        val onItemClick = remember(expense.id, onSelectExpense) { { onSelectExpense(expense) } }
        ExpenseItemCard(
          expense = expense,
          catColor = catColor,
          isCreditCard = isCc,
          onClick = onItemClick
        )
      }
    }
  }

  expensePendingDelete?.let { expense ->
    val transferCounterparts = findTransferCounterparts(expense, expenses)
    val isTransfer = expense.type == "TRANSFER_IN" || expense.type == "TRANSFER_OUT"
    val isAmbiguousTransfer = isTransfer && transferCounterparts.size > 1
    val transactionsToDelete = listOf(expense) + transferCounterparts.takeIf { it.size == 1 }.orEmpty()
    AlertDialog(
      onDismissRequest = { expensePendingDelete = null },
      title = { Text("Delete Transaction?") },
      text = {
        when {
          isAmbiguousTransfer -> Text(
            "More than one matching transfer entry was found, so the linked transaction cannot be identified safely. No transactions were deleted."
          )
          transferCounterparts.size == 1 -> Text(
            "Delete this transfer and its matching entry from ${transferCounterparts.single().bankAccountName}? You can undo both briefly."
          )
          isTransfer -> Text(
            "No matching transfer entry was found. Delete '${expense.title}' " +
              "(${CurrencyFormatter.formatWhole(expense.amount)}) only? You can undo it briefly."
          )
          else -> Text(
            "Delete '${expense.title}' (${CurrencyFormatter.formatWhole(expense.amount)})? " +
              "You can undo this briefly after confirming."
          )
        }
      },
      confirmButton = {
        if (!isAmbiguousTransfer) {
          Button(
            onClick = {
              onDeleteExpenses(transactionsToDelete)
              expensePendingDelete = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          ) {
            Text("Delete")
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { expensePendingDelete = null }) {
          Text(if (isAmbiguousTransfer) "OK" else "Cancel")
        }
      }
    )
  }
}

@Composable
private fun ExpenseItemCard(
  expense: ExpenseRecord,
  catColor: Color = Color(0xFF6366F1),
  isCreditCard: Boolean = false,
  onClick: () -> Unit
) {
  val dateFormatted = remember(expense.dateTimestamp) {
    formatFinanceTimestamp(expense.dateTimestamp, EXPENSE_ITEM_DATE_FORMATTER)
  }
  val isTransfer = expense.type == "TRANSFER_IN" || expense.type == "TRANSFER_OUT"
  val isIncome = expense.type == "INCOME" || expense.type == "TRANSFER_IN"
  val isCcBillPayment = isTransfer && (expense.title.contains("Credit Card Bill", ignoreCase = true) || expense.title.contains("Bill Payment", ignoreCase = true))

  val amountColor = when {
    isTransfer -> MaterialTheme.colorScheme.primary
    isIncome -> EXPENSE_INCOME_COLOR
    else -> EXPENSE_EXPENSE_COLOR
  }
  val catBgColor = remember(catColor) { catColor.copy(alpha = 0.15f) }
  val iconBgColor = when {
    isCreditCard -> EXPENSE_CC_BG
    isCcBillPayment -> EXPENSE_CC_BILL_BG
    isTransfer -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    isIncome -> EXPENSE_INCOME_BG
    else -> catBgColor
  }
  val iconTintColor = when {
    isCreditCard -> EXPENSE_CC_COLOR
    isCcBillPayment -> EXPENSE_CC_BILL_COLOR
    isTransfer -> MaterialTheme.colorScheme.primary
    isIncome -> EXPENSE_INCOME_COLOR
    else -> catColor
  }

  val amountText = remember(expense.amount, isIncome) {
    val formatted = CurrencyFormatter.formatWhole(expense.amount)
    if (isIncome) "+ $formatted" else "- $formatted"
  }

  Surface(
    onClick = onClick,
    shape = EXPENSE_CARD_SHAPE,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .background(iconBgColor, EXPENSE_ICON_BOX_SHAPE),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = when {
              isCreditCard -> Icons.Default.CreditCard
              isCcBillPayment -> Icons.Default.CreditScore
              isTransfer -> Icons.Default.SwapHoriz
              isIncome -> Icons.Default.ArrowDownward
              else -> Icons.Default.Sell
            },
            contentDescription = null,
            tint = iconTintColor,
            modifier = Modifier.size(20.dp)
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(
            text = expense.title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(3.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier.background(catBgColor, EXPENSE_PILL_SHAPE)
            ) {
              Text(
                text = expense.categoryName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = catColor,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
            if (isCreditCard) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .background(EXPENSE_CC_BG, EXPENSE_PILL_SHAPE)
                  .border(EXPENSE_CC_BORDER, EXPENSE_PILL_SHAPE)
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Icon(
                  Icons.Default.CreditCard,
                  contentDescription = null,
                  tint = EXPENSE_CC_COLOR,
                  modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                  text = "Credit Card • ${expense.bankAccountName}",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = EXPENSE_CC_COLOR,
                  maxLines = 1
                )
              }
            } else if (isCcBillPayment) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .background(EXPENSE_CC_BILL_BG, EXPENSE_PILL_SHAPE)
                  .border(EXPENSE_CC_BILL_BORDER, EXPENSE_PILL_SHAPE)
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Icon(
                  Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = EXPENSE_CC_BILL_COLOR,
                  modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                  text = "CC Bill Payment",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = EXPENSE_CC_BILL_COLOR,
                  maxLines = 1
                )
              }
            } else if (expense.bankAccountName.isNotBlank()) {
              Box(
                modifier = Modifier.background(
                  MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                  EXPENSE_PILL_SHAPE
                )
              ) {
                Text(
                  text = expense.bankAccountName,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  maxLines = 1,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = dateFormatted,
            fontSize = 10.5.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (expense.notes.isNotBlank()) {
            Text(
              text = expense.notes,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
          }
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = amountText,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = amountColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
          Icons.Default.ChevronRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}

@Composable
private fun TransactionDetailsDialog(
  expense: ExpenseRecord,
  categories: List<ExpenseCategory>,
  onDismiss: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  val matchedCat = remember(expense.categoryName, categories) {
    categories.find { it.name.equals(expense.categoryName, ignoreCase = true) }
  }
  val catColor = remember(matchedCat) {
    try {
      matchedCat?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: Color(0xFF6366F1)
    } catch (e: Exception) {
      Color(0xFF6366F1)
    }
  }
  val isTransfer = expense.type == "TRANSFER_IN" || expense.type == "TRANSFER_OUT"
  val isIncome = expense.type == "INCOME" || expense.type == "TRANSFER_IN"
  val amountColor = when {
    isTransfer -> MaterialTheme.colorScheme.primary
    isIncome -> Color(0xFF059669)
    else -> Color(0xFFDC2626)
  }
  val amountSign = if (isIncome) "+" else "-"
  val dateFormatted = remember(expense.dateTimestamp) {
    formatFinanceTimestamp(expense.dateTimestamp, EXPENSE_DETAIL_DATE_FORMATTER)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(24.dp),
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Transaction Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = when {
            isTransfer -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            isIncome -> Color(0xFF10B981).copy(alpha = 0.15f)
            else -> Color(0xFFEF4444).copy(alpha = 0.12f)
          }
        ) {
          Text(
            text = when {
              isTransfer -> "Transfer"
              isIncome -> "Income"
              else -> "Expense"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = amountColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Amount hero box
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = amountColor.copy(alpha = 0.08f),
          border = BorderStroke(1.dp, amountColor.copy(alpha = 0.25f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "$amountSign ${CurrencyFormatter.format(expense.amount)}",
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.ExtraBold,
              color = amountColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = expense.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }

        // Details rows
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Category
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Category", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = catColor.copy(alpha = 0.15f)
            ) {
              Text(
                text = expense.categoryName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = catColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

          // Account
          if (expense.bankAccountName.isNotBlank()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Account / Mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                text = expense.bankAccountName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
          }

          // Date & Time
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
          ) {
            Text("Date & Time", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = dateFormatted,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              textAlign = androidx.compose.ui.text.style.TextAlign.End,
              modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
          }

          // Notes
          if (expense.notes.isNotBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Notes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = expense.notes,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(10.dp)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(
          onClick = onDelete,
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Delete")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!isTransfer) {
            FilledTonalButton(onClick = onEdit) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Edit")
            }
          }
          Button(onClick = onDismiss) {
            Text("Close")
          }
        }
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditExpenseDialog(
  expense: ExpenseRecord,
  categories: List<ExpenseCategory>,
  bankAccounts: List<BankAccount>,
  onDismiss: () -> Unit,
  onConfirm: (title: String, amount: Double, category: String, accountId: Long?, accountName: String, notes: String, dateTimestamp: Long) -> Unit
) {
  var title by remember(expense.id) { mutableStateOf(expense.title) }
  var amountText by remember(expense.id) { mutableStateOf(expense.amount.toString()) }
  var selectedCategory by remember(expense.id) { mutableStateOf(expense.categoryName) }
  var selectedAccountId by remember(expense.id) { mutableStateOf(expense.bankAccountId) }
  var selectedAccountName by remember(expense.id) { mutableStateOf(expense.bankAccountName) }
  var notes by remember(expense.id) { mutableStateOf(expense.notes) }
  var dateTimestamp by remember(expense.id) { mutableStateOf(expense.dateTimestamp) }

  var categoryDropdownExpanded by remember { mutableStateOf(false) }
  var accountDropdownExpanded by remember { mutableStateOf(false) }

  val availableCategories = remember(categories, expense.categoryName) {
    (categories.map { it.name } + expense.categoryName).distinct()
  }
  val amount = amountText.toDoubleOrNull()
  val canSave = title.isNotBlank() && amount != null && amount.isFinite() && amount > 0.0

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Transaction", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Title *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = amountText,
          onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) amountText = input
          },
          label = { Text("Amount (₹) *") },
          prefix = { Text("₹ ") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        ExposedDropdownMenuBox(
          expanded = categoryDropdownExpanded,
          onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
        ) {
          OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = {
              ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded)
            },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
              .fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = categoryDropdownExpanded,
            onDismissRequest = { categoryDropdownExpanded = false }
          ) {
            availableCategories.forEach { category ->
              DropdownMenuItem(
                text = { Text(category) },
                onClick = {
                  selectedCategory = category
                  categoryDropdownExpanded = false
                }
              )
            }
          }
        }

        // Account Dropdown
        ExposedDropdownMenuBox(
          expanded = accountDropdownExpanded,
          onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded }
        ) {
          OutlinedTextField(
            value = selectedAccountName.ifBlank { "None (Cash / Unlinked)" },
            onValueChange = {},
            readOnly = true,
            label = { Text("Account") },
            trailingIcon = {
              ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded)
            },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
              .fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = accountDropdownExpanded,
            onDismissRequest = { accountDropdownExpanded = false }
          ) {
            DropdownMenuItem(
              text = { Text("None (Cash / Unlinked)") },
              onClick = {
                selectedAccountId = null
                selectedAccountName = ""
                accountDropdownExpanded = false
              }
            )
            bankAccounts.forEach { acc ->
              DropdownMenuItem(
                text = { Text("${acc.bankName} (${acc.accountType})") },
                onClick = {
                  selectedAccountId = acc.id
                  selectedAccountName = acc.bankName
                  accountDropdownExpanded = false
                }
              )
            }
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedAmount = amount ?: return@Button
          onConfirm(
            title.trim(),
            parsedAmount,
            selectedCategory,
            selectedAccountId,
            selectedAccountName,
            notes.trim(),
            dateTimestamp
          )
        },
        enabled = canSave
      ) {
        Text("Save Changes")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

private fun findTransferCounterparts(
  transfer: ExpenseRecord,
  expenses: List<ExpenseRecord>
): List<ExpenseRecord> {
  val expectedType: String
  val expectedTitle: String
  val expectedAccountName: String

  when (transfer.type) {
    "TRANSFER_OUT" -> {
      expectedAccountName = transfer.title
        .takeIf { it.startsWith("Transfer to ", ignoreCase = true) }
        ?.substringAfter("Transfer to ")
        ?.takeIf { it.isNotBlank() }
        ?: return emptyList()
      expectedType = "TRANSFER_IN"
      expectedTitle = "Transfer from ${transfer.bankAccountName}"
    }
    "TRANSFER_IN" -> {
      expectedAccountName = transfer.title
        .takeIf { it.startsWith("Transfer from ", ignoreCase = true) }
        ?.substringAfter("Transfer from ")
        ?.takeIf { it.isNotBlank() }
        ?: return emptyList()
      expectedType = "TRANSFER_OUT"
      expectedTitle = "Transfer to ${transfer.bankAccountName}"
    }
    else -> return emptyList()
  }

  return expenses.filter { candidate ->
    candidate.id != transfer.id &&
      candidate.type == expectedType &&
      candidate.amount == transfer.amount &&
      kotlin.math.abs(candidate.dateTimestamp - transfer.dateTimestamp) <= 1L &&
      candidate.title.equals(expectedTitle, ignoreCase = true) &&
      candidate.bankAccountName.equals(expectedAccountName, ignoreCase = true)
  }
}

@Composable
private fun SpendingAnalyticsCard(
  expenses: List<ExpenseRecord>,
  categories: List<ExpenseCategory>,
  totalSpent: Double,
  selectedCategory: String? = null,
  onSelectCategory: (String?) -> Unit = {}
) {
  val haptic = LocalHapticFeedback.current
  val catSpendList = remember(expenses, categories, totalSpent) {
    val categoryMap = HashMap<String, Color>(categories.size)
    categories.forEach { cat ->
      val parsed = try {
        Color(android.graphics.Color.parseColor(cat.colorHex))
      } catch (_: Exception) {
        Color(0xFF6366F1)
      }
      categoryMap[cat.name.lowercase()] = parsed
    }
    val map = mutableMapOf<String, Double>()
    expenses.forEach { exp ->
      map[exp.categoryName] = (map[exp.categoryName] ?: 0.0) + exp.amount
    }
    map.entries.map { entry ->
      val catColor = categoryMap[entry.key.lowercase()] ?: Color(0xFF6366F1)
      val percent = if (totalSpent > 0) (entry.value / totalSpent * 100).toFloat() else 0f
      SpendingAnalyticsRow(
        categoryName = entry.key,
        amount = entry.value,
        color = catColor,
        percent = percent,
        percentInt = percent.toInt(),
        formattedPercent = String.format(Locale.getDefault(), "%.1f", percent),
        formattedAmount = CurrencyFormatter.formatWhole(entry.value)
      )
    }.sortedByDescending { it.amount }
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
        ) {
          Icon(
            Icons.Default.PieChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Spending Analytics",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        if (selectedCategory != null) {
          FilterChip(
            selected = true,
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              onSelectCategory(null)
            },
            label = { Text("Filter: $selectedCategory ✕", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
          )
        } else {
          Box(
            modifier = Modifier.background(
              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
              RoundedCornerShape(8.dp)
            )
          ) {
            Text(
              text = "${catSpendList.size} Categories",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              softWrap = false,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      }

      // Donut Chart Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Compose Canvas Donut Chart with Tap support
        Box(
          modifier = Modifier.size(135.dp),
          contentAlignment = Alignment.Center
        ) {
          Canvas(
            modifier = Modifier
              .fillMaxSize()
              .padding(10.dp)
              .pointerInput(catSpendList) {
                detectTapGestures { offset ->
                  val center = Offset(size.width / 2f, size.height / 2f)
                  val dx = offset.x - center.x
                  val dy = offset.y - center.y
                  val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                  val outerR = size.width / 2f
                  val innerR = outerR - 26.dp.toPx()
                  if (dist in innerR..outerR) {
                    val angle = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 450.0) % 360.0)
                    var acc = 0.0
                    for (item in catSpendList) {
                      val sweep = (item.percent / 100.0) * 360.0
                      if (angle >= acc && angle < acc + sweep) {
                        haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectCategory(if (selectedCategory == item.categoryName) null else item.categoryName)
                        break
                      }
                      acc += sweep
                    }
                  }
                }
              }
          ) {
            var startAngle = -90f
            catSpendList.forEach { row ->
              val isSelected = selectedCategory != null && selectedCategory.equals(row.categoryName, ignoreCase = true)
              val sweepAngle = (row.percent / 100f) * 360f
              if (sweepAngle > 0f) {
                drawArc(
                  color = if (selectedCategory == null || isSelected) row.color else row.color.copy(alpha = 0.25f),
                  startAngle = startAngle,
                  sweepAngle = (sweepAngle - 2f).coerceAtLeast(1f),
                  useCenter = false,
                  style = Stroke(
                    width = if (isSelected) 26.dp.toPx() else 20.dp.toPx(),
                    cap = StrokeCap.Round
                  )
                )
                startAngle += sweepAngle
              }
            }
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = if (selectedCategory != null) "Filtered" else "Total Spent",
              fontSize = 9.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = if (selectedCategory != null) selectedCategory else CurrencyFormatter.formatCompact(totalSpent),
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }

        // Top Slices Quick Legend with Clickable Pills
        Column(
          modifier = Modifier.padding(start = 10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          catSpendList.take(4).forEach { row ->
            val isSelected = selectedCategory != null && selectedCategory.equals(row.categoryName, ignoreCase = true)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier
                .background(
                  if (isSelected) row.color.copy(alpha = 0.15f) else Color.Transparent,
                  RoundedCornerShape(8.dp)
                )
                .then(if (isSelected) Modifier.border(1.dp, row.color, RoundedCornerShape(8.dp)) else Modifier)
                .clickable {
                  haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onSelectCategory(if (selectedCategory == row.categoryName) null else row.categoryName)
                }
                .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .background(row.color, CircleShape)
              )
              Text(
                text = row.categoryName,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 85.dp)
              )
              Text(
                text = "${row.percentInt}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) row.color else MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

      // Category breakdown progress bars (Tap any to filter!)
      val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        catSpendList.forEach { row ->
          val isSelected = selectedCategory != null && selectedCategory.equals(row.categoryName, ignoreCase = true)
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                if (isSelected) row.color.copy(alpha = 0.10f) else Color.Transparent,
                RoundedCornerShape(8.dp)
              )
              .clickable {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectCategory(if (selectedCategory == row.categoryName) null else row.categoryName)
              }
              .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .background(row.color, CircleShape)
                )
                Text(
                  text = row.categoryName,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) row.color else MaterialTheme.colorScheme.onSurface
                )
              }
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = row.formattedAmount,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "(${row.formattedPercent}%)",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(trackColor, PROGRESS_BAR_SHAPE)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth((row.percent / 100f).coerceIn(0f, 1f))
                  .fillMaxHeight()
                  .background(row.color, PROGRESS_BAR_SHAPE)
              )
            }
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// ACCOUNT MANAGEMENT DIALOGS
// -------------------------------------------------------------
@Composable
private fun EditOpeningBalanceDialog(
  account: BankAccount,
  onDismiss: () -> Unit,
  onConfirm: (Double) -> Unit
) {
  var openingStr by remember { mutableStateOf(if (account.openingBalance > 0) account.openingBalance.toString() else "") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Opening Balance") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Update opening funds for ${account.bankName}. Current balance will update automatically based on your income and expenses.")
        OutlinedTextField(
          value = openingStr,
          onValueChange = { openingStr = it },
          label = { Text("Opening Balance (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val bal = openingStr.toDoubleOrNull() ?: 0.0
          onConfirm(bal)
        }
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
private fun BankCardItem(
  account: BankAccount,
  onCopy: () -> Unit,
  onDelete: () -> Unit
) {
  var isMasked by remember { mutableStateOf(true) }

  // Modern Credit/Debit card aesthetic with sleek gradient
  Card(
    shape = RoundedCornerShape(22.dp),
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.linearGradient(
            colors = listOf(
              Color(0xFF1E293B),
              Color(0xFF0F172A),
              Color(0xFF334155)
            )
          )
        )
        .padding(20.dp)
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Top row: Bank Name and Type Badge
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.AccountBalance,
              contentDescription = null,
              tint = Color(0xFFFBBF24),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = account.bankName,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = Color.White
            )
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFBBF24).copy(alpha = 0.2f)
          ) {
            Text(
              text = account.accountType.uppercase(),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFFBBF24),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }

        // Account Number with mask toggle
        Column {
          Text(
            text = "ACCOUNT NUMBER",
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.sp
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
          ) {
            val displayedNum = if (isMasked && account.accountNumber.length > 4) {
              "•••• •••• " + account.accountNumber.takeLast(4)
            } else {
              account.accountNumber
            }
            Text(
              text = displayedNum,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
              onClick = { isMasked = !isMasked },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(
                if (isMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Toggle mask",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }

        // Holder Name & IFSC/Routing
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text(
              text = "HOLDER NAME",
              fontSize = 9.sp,
              color = Color.White.copy(alpha = 0.6f),
              letterSpacing = 1.sp
            )
            Text(
              text = account.holderName.ifBlank { "N/A" },
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp,
              color = Color.White
            )
          }

          if (account.ifscOrRouting.isNotBlank()) {
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "IFSC / ROUTING",
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 1.sp
              )
              Text(
                text = account.ifscOrRouting,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color(0xFF67E8F9)
              )
            }
          }
        }

        if (account.balance > 0.0) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Available Balance:",
              fontSize = 11.sp,
              color = Color.White.copy(alpha = 0.7f)
            )
            Text(
              text = CurrencyFormatter.format(account.balance),
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = Color(0xFF4ADE80)
            )
          }
        }

        // Action Buttons: Copy Details & Delete
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilledTonalButton(
            onClick = onCopy,
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = Color.White.copy(alpha = 0.18f),
              contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy Details", fontSize = 12.sp)
          }

          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              Icons.Default.DeleteOutline,
              contentDescription = "Delete account",
              tint = Color(0xFFF87171),
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BankVaultLockScreen(
  enteredLength: Int,
  pinError: Boolean,
  onDigitClick: (String) -> Unit,
  onBackspace: () -> Unit,
  onResetPin: () -> Unit
) {
  val haptic = LocalHapticFeedback.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp, vertical = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      modifier = Modifier
        .size(72.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        Icons.Default.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp)
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Bank Vault Protected",
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = "Enter your 4-digit security PIN to unlock account numbers & balances",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )

    Spacer(modifier = Modifier.height(28.dp))

    // 4 PIN Dots
    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      for (i in 0 until 4) {
        val isFilled = i < enteredLength
        Box(
          modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(
              when {
                pinError -> Color(0xFFEF4444)
                isFilled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
              }
            )
        )
      }
    }

    if (pinError) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "Incorrect PIN. Please try again.",
        color = Color(0xFFEF4444),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
      )
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Numeric Keypad
    val keypad = listOf(
      listOf("1", "2", "3"),
      listOf("4", "5", "6"),
      listOf("7", "8", "9"),
      listOf("Reset", "0", "Del")
    )

    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      keypad.forEach { row ->
        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          row.forEach { key ->
            when (key) {
              "Reset" -> {
                TextButton(
                  onClick = onResetPin,
                  modifier = Modifier.size(64.dp)
                ) {
                  Text("Reset", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
              }
              "Del" -> {
                FilledTonalIconButton(
                  onClick = {
                    haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBackspace()
                  },
                  modifier = Modifier.size(64.dp)
                ) {
                  Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete digit")
                }
              }
              else -> {
                FilledTonalButton(
                  onClick = {
                    haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDigitClick(key)
                  },
                  shape = CircleShape,
                  modifier = Modifier.size(64.dp),
                  contentPadding = PaddingValues(0.dp)
                ) {
                  Text(
                    text = key,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SetVaultPinDialog(
  currentPin: String,
  onDismiss: () -> Unit,
  onSavePin: (String) -> Unit
) {
  var pin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (currentPin.isEmpty()) "Set 4-Digit Security PIN" else "Change Vault Security PIN") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = "Choose a 4-digit PIN to safeguard your bank account details.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
          value = pin,
          onValueChange = {
            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
              pin = it
              errorText = null
            }
          },
          label = { Text("Enter 4-Digit PIN") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = confirmPin,
          onValueChange = {
            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
              confirmPin = it
              errorText = null
            }
          },
          label = { Text("Confirm 4-Digit PIN") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        errorText?.let {
          Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (currentPin.isNotEmpty()) {
          TextButton(
            onClick = { onSavePin("") },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Remove PIN Protection", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (pin.length != 4) {
            errorText = "PIN must be exactly 4 digits"
          } else if (pin != confirmPin) {
            errorText = "PINs do not match"
          } else {
            onSavePin(pin)
          }
        },
        enabled = pin.length == 4 && confirmPin.length == 4
      ) {
        Text("Save PIN")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

// -------------------------------------------------------------
// TRIPS & VACATION MANAGER SECTION
// -------------------------------------------------------------
@Composable
private fun TripsSection(
  trips: List<TripRecord>,
  expenses: List<ExpenseRecord>,
  onAddExpenseForTrip: (TripRecord) -> Unit,
  onSetCategoryCap: (TripRecord) -> Unit,
  onRemoveCategoryCap: (TripRecord, String) -> Unit,
  onExportTripPdf: (TripRecord, List<ExpenseRecord>) -> Unit,
  onDeleteTrip: (TripRecord) -> Unit,
  onEditTrip: (TripRecord) -> Unit = {},
  onTripExpenseClick: (ExpenseRecord) -> Unit = {}
) {
  val expensesByTripId = remember(expenses) {
    expenses.filter { it.tripId != null }.groupBy { it.tripId }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    if (trips.isEmpty()) {
      item(key = "empty_trips", contentType = "empty_state") {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              Icons.Default.Luggage,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No trips created yet",
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Tap the + trip button below to plan your next vacation budget",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }
        }
      }
    } else {
      items(trips, key = { it.id }, contentType = { "trip_card" }) { trip ->
        val tripExpenses = expensesByTripId[trip.id].orEmpty()
        val spent = remember(tripExpenses) { tripExpenses.sumOf { it.amount } }
        TripCardItem(
          trip = trip,
          spent = spent,
          tripExpenses = tripExpenses,
          onAddExpense = { onAddExpenseForTrip(trip) },
          onSetCap = { onSetCategoryCap(trip) },
          onRemoveCap = { cat -> onRemoveCategoryCap(trip, cat) },
          onExportPdf = { onExportTripPdf(trip, tripExpenses) },
          onDelete = { onDeleteTrip(trip) },
          onEdit = { onEditTrip(trip) },
          onTripExpenseClick = onTripExpenseClick
        )
      }
    }
  }
}

@Composable
private fun TripCardItem(
  trip: TripRecord,
  spent: Double,
  tripExpenses: List<ExpenseRecord>,
  onAddExpense: () -> Unit,
  onSetCap: () -> Unit,
  onRemoveCap: (String) -> Unit,
  onExportPdf: () -> Unit,
  onDelete: () -> Unit,
  onEdit: () -> Unit = {},
  onTripExpenseClick: (ExpenseRecord) -> Unit = {}
) {
  val progress = remember(trip.budget, spent) {
    if (trip.budget > 0) (spent / trip.budget).toFloat().coerceIn(0f, 1f) else 0f
  }
  val isOverBudget = spent > trip.budget
  val remaining = trip.budget - spent
  var showLedger by remember { mutableStateOf(false) }
  var showTripActions by remember { mutableStateOf(false) }
  var confirmTripDelete by remember { mutableStateOf(false) }

  if (showTripActions) {
    AlertDialog(
      onDismissRequest = { showTripActions = false },
      title = { Text(trip.tripName, fontWeight = FontWeight.Bold) },
      text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(trip.destination, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Dates: ${trip.startDate} - ${trip.endDate}")
        Text("Spent: ${CurrencyFormatter.format(spent)}")
        Text("Budget: ${CurrencyFormatter.format(trip.budget)}")
      } },
      confirmButton = { TextButton(onClick = { showTripActions = false; onEdit() }) { Text("Edit") } },
      dismissButton = { Row {
        TextButton(onClick = { showTripActions = false; confirmTripDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
        TextButton(onClick = { showTripActions = false }) { Text("Close") }
      } }
    )
  }
  if (confirmTripDelete) {
    AlertDialog(
      onDismissRequest = { confirmTripDelete = false },
      title = { Text("Delete trip?") },
      text = { Text("Delete ${trip.tripName}? Your recorded Finance transactions will remain in Finance.") },
      confirmButton = { TextButton(onClick = { confirmTripDelete = false; onDelete() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
      dismissButton = { TextButton(onClick = { confirmTripDelete = false }) { Text("Cancel") } }
    )
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth().clickable { showTripActions = true }
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(Color(0xFF0D9488).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.LocationOn,
              contentDescription = null,
              tint = Color(0xFF0D9488)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = trip.tripName,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
            Text(
              text = trip.destination,
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Icon(Icons.Default.ChevronRight, contentDescription = "Open trip details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      if (trip.startDate.isNotBlank() || trip.endDate.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${trip.startDate} - ${trip.endDate}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Live Remaining Budget Highlight Card
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isOverBudget) Color(0xFFFEE2E2) else Color(0xFFCCFBF1)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = if (isOverBudget) "OVER BUDGET" else "REMAINING BUDGET",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (isOverBudget) Color(0xFFB91C1C) else Color(0xFF0F766E),
              letterSpacing = 0.8.sp
            )
            Text(
              text = if (isOverBudget) CurrencyFormatter.format(spent - trip.budget) else CurrencyFormatter.format(remaining),
              fontSize = 16.sp,
              fontWeight = FontWeight.ExtraBold,
              color = if (isOverBudget) Color(0xFFB91C1C) else Color(0xFF0F766E)
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "Total Cap: ${CurrencyFormatter.format(trip.budget)}",
              fontSize = 11.sp,
              color = if (isOverBudget) Color(0xFF991B1B) else Color(0xFF115E59)
            )
            Text(
              text = "${(progress * 100).toInt()}% utilized",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (isOverBudget) Color(0xFF991B1B) else Color(0xFF115E59)
            )
          }
        }
      }

      // Budget Progress Bar
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Spent: ${CurrencyFormatter.format(spent)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          Text("Budget: ${CurrencyFormatter.format(trip.budget)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
          color = if (isOverBudget) Color(0xFFEF4444) else Color(0xFF0D9488),
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
      }

      // FEATURE: Daily Spending Rate Tracker with Speedometer Gauge
      TripDailySpendingSpeedometer(
        trip = trip,
        spent = spent
      )

      // FEATURE: Category Budget Caps per Trip
      TripCategoryCapsSection(
        trip = trip,
        tripExpenses = tripExpenses,
        onSetCap = onSetCap,
        onRemoveCap = onRemoveCap
      )

      if (trip.notes.isNotBlank()) {
        Text(
          text = trip.notes,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
      }

      // Actions: Add Expense to Trip & Export PDF Report
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onAddExpense,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("+ Log Expense", fontSize = 12.sp)
        }

        FilledTonalButton(
          onClick = onExportPdf,
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF0D9488).copy(alpha = 0.15f),
            contentColor = Color(0xFF0D9488)
          ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Export PDF", fontSize = 12.sp)
        }
      }

      // Expandable Trip Expense Ledger
      TextButton(
        onClick = { showLedger = !showLedger },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Trip Expense Ledger (${tripExpenses.size} items)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Icon(
            if (showLedger) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
        }
      }

      AnimatedVisibility(visible = showLedger) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (tripExpenses.isEmpty()) {
            Text(
              text = "No expenses logged for this trip yet. Tap '+ Log Expense' to record hotel, flight, or meal costs.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            tripExpenses.forEach { exp ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { onTripExpenseClick(exp) }
                  .padding(vertical = 4.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = exp.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                  )
                  Text(
                    text = exp.categoryName,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = CurrencyFormatter.formatWhole(exp.amount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFDC2626)
                  )
                  Spacer(Modifier.width(4.dp))
                  Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                  )
                }
              }
              if (exp != tripExpenses.last()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EditTripDialog(
  trip: TripRecord,
  onDismiss: () -> Unit,
  onConfirm: (TripRecord) -> Unit
) {
  var tripName by remember(trip.id) { mutableStateOf(trip.tripName) }
  var destination by remember(trip.id) { mutableStateOf(trip.destination) }
  var budgetStr by remember(trip.id) { mutableStateOf(trip.budget.toString()) }
  var startDate by remember(trip.id) { mutableStateOf(trip.startDate) }
  var endDate by remember(trip.id) { mutableStateOf(trip.endDate) }
  var notes by remember(trip.id) { mutableStateOf(trip.notes) }

  val parsedBudget = budgetStr.toDoubleOrNull()
  val canSave = tripName.isNotBlank() && destination.isNotBlank() && parsedBudget != null && parsedBudget > 0

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(24.dp),
    title = { Text("Edit Trip Details", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = tripName,
          onValueChange = { tripName = it },
          label = { Text("Trip Name *") },
          placeholder = { Text("e.g. Goa Vacation, Europe Tour") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = destination,
          onValueChange = { destination = it },
          label = { Text("Destination *") },
          placeholder = { Text("e.g. Goa, Paris, Manali") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = budgetStr,
          onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) budgetStr = input
          },
          label = { Text("Budget Cap (₹) *") },
          prefix = { Text("₹ ") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = startDate,
            onValueChange = { startDate = it },
            label = { Text("Start Date") },
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = endDate,
            onValueChange = { endDate = it },
            label = { Text("End Date") },
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val budget = parsedBudget ?: return@Button
          onConfirm(
            trip.copy(
              tripName = tripName.trim(),
              destination = destination.trim(),
              budget = budget,
              startDate = startDate.trim(),
              endDate = endDate.trim(),
              notes = notes.trim()
            )
          )
        },
        enabled = canSave
      ) {
        Text("Save Changes")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

// -------------------------------------------------------------
// DIALOGS: ADD CATEGORY, EXPENSE, BANK, TRIP
// -------------------------------------------------------------
@Composable
private fun AddCategoryDialog(
  onDismiss: () -> Unit,
  onConfirm: (name: String, colorHex: String) -> Unit
) {
  var name by remember { mutableStateOf("") }
  val colorPalette = listOf(
    "#EF4444", "#F59E0B", "#10B981", "#3B82F6",
    "#8B5CF6", "#EC4899", "#06B6D4", "#6366F1"
  )
  var selectedColor by remember { mutableStateOf(colorPalette[0]) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Expense Category") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Category Name") },
          placeholder = { Text("e.g. Gym, Pets, Groceries") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Text("Select Color Tag:", style = MaterialTheme.typography.bodySmall)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          colorPalette.forEach { hex ->
            val color = Color(android.graphics.Color.parseColor(hex))
            val isSelected = selectedColor == hex
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .clickable { selectedColor = hex }
                .padding(2.dp),
              contentAlignment = Alignment.Center
            ) {
              if (isSelected) {
                Icon(
                  Icons.Default.Check,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name, selectedColor) },
        enabled = name.isNotBlank()
      ) {
        Text("Add")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

private enum class ExpensePaymentMethod(val title: String) {
  CASH("Cash"),
  BANK("Bank Account"),
  CREDIT_CARD("Credit Card")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
  categories: List<ExpenseCategory>,
  bankAccounts: List<BankAccount>,
  trips: List<TripRecord>,
  initialTripId: Long? = null,
  onDismiss: () -> Unit,
  onConfirm: (title: String, amount: Double, category: String, accountId: Long?, accountName: String, notes: String, tripId: Long?) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var amountStr by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(categories.firstOrNull()?.name ?: "General") }
  var notes by remember { mutableStateOf("") }
  var selectedTripId by remember { mutableStateOf<Long?>(initialTripId) }
  var categoryDropdownExpanded by remember { mutableStateOf(false) }
  var smsParseMessage by remember { mutableStateOf<String?>(null) }
  val clipboard = LocalClipboardManager.current

  // Categorize accounts by liquid cash, bank accounts, and credit cards
  val cashAccounts = remember(bankAccounts) {
    bankAccounts.filter { it.accountType.equals("Cash Wallet", ignoreCase = true) || it.bankName.contains("Cash", ignoreCase = true) }
  }
  val bankAccountsOnly = remember(bankAccounts) {
    bankAccounts.filter { !it.accountType.equals("Credit Card", ignoreCase = true) && !it.accountType.equals("Cash Wallet", ignoreCase = true) && !it.bankName.contains("Cash", ignoreCase = true) }
  }
  val creditCardAccounts = remember(bankAccounts) {
    bankAccounts.filter { it.accountType.equals("Credit Card", ignoreCase = true) }
  }

  var paymentMethod by remember {
    mutableStateOf(
      when {
        bankAccountsOnly.isNotEmpty() -> ExpensePaymentMethod.BANK
        cashAccounts.isNotEmpty() -> ExpensePaymentMethod.CASH
        else -> ExpensePaymentMethod.CREDIT_CARD
      }
    )
  }

  var selectedAccountId by remember(paymentMethod) {
    mutableStateOf(
      when (paymentMethod) {
        ExpensePaymentMethod.CASH -> cashAccounts.firstOrNull()?.id
        ExpensePaymentMethod.BANK -> bankAccountsOnly.firstOrNull()?.id ?: bankAccounts.firstOrNull { !it.accountType.equals("Credit Card", ignoreCase = true) }?.id
        ExpensePaymentMethod.CREDIT_CARD -> creditCardAccounts.firstOrNull()?.id
      }
    )
  }

  val selectedAccountName = remember(selectedAccountId, bankAccounts, paymentMethod) {
    bankAccounts.find { it.id == selectedAccountId }?.bankName ?: when (paymentMethod) {
      ExpensePaymentMethod.CASH -> "Cash"
      ExpensePaymentMethod.CREDIT_CARD -> "Credit Card"
      ExpensePaymentMethod.BANK -> "Bank Account"
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Log New Expense") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        OutlinedButton(
          onClick = {
            val parsed = BankSmsParser.parseSms(clipboard.getText()?.text.orEmpty(), bankAccounts)
            when {
              parsed == null -> smsParseMessage = "Copy a bank SMS with an amount, then try again."
              parsed.isIncome -> smsParseMessage = "This SMS looks like income. Use Add Income to record it."
              else -> {
                title = parsed.merchantOrTitle
                amountStr = parsed.amount.toString()
                categories.firstOrNull {
                  it.name.equals(parsed.suggestedCategory, ignoreCase = true)
                }?.let { selectedCategory = it.name }
                parsed.matchedAccountId?.let { matchedId ->
                  val acc = bankAccounts.find { it.id == matchedId }
                  if (acc != null) {
                    if (acc.accountType.equals("Credit Card", ignoreCase = true)) {
                      paymentMethod = ExpensePaymentMethod.CREDIT_CARD
                    } else if (acc.accountType.equals("Cash Wallet", ignoreCase = true)) {
                      paymentMethod = ExpensePaymentMethod.CASH
                    } else {
                      paymentMethod = ExpensePaymentMethod.BANK
                    }
                    selectedAccountId = matchedId
                  }
                }
                notes = parsed.rawNotes
                smsParseMessage = "Expense details filled from SMS."
              }
            }
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Auto-Fill from SMS")
        }
        smsParseMessage?.let { message ->
          Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Expense Title *") },
          placeholder = { Text("e.g. Grocery store, Uber, Dinner") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("expense_title_input")
        )

        OutlinedTextField(
          value = amountStr,
          onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
              amountStr = input
            }
          },
          label = { Text("Amount (₹) *") },
          prefix = { Text("₹ ") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("expense_amount_input")
        )

        // Payment Method Selector
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Payment Method *",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            ExpensePaymentMethod.values().forEach { method ->
              FilterChip(
                selected = paymentMethod == method,
                onClick = {
                  paymentMethod = method
                  selectedAccountId = when (method) {
                    ExpensePaymentMethod.CASH -> cashAccounts.firstOrNull()?.id
                    ExpensePaymentMethod.BANK -> bankAccountsOnly.firstOrNull()?.id ?: bankAccounts.firstOrNull { !it.accountType.equals("Credit Card", ignoreCase = true) }?.id
                    ExpensePaymentMethod.CREDIT_CARD -> creditCardAccounts.firstOrNull()?.id
                  }
                },
                label = {
                  Text(
                    text = method.title,
                    fontSize = 11.sp,
                    fontWeight = if (paymentMethod == method) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                  )
                },
                leadingIcon = {
                  Icon(
                    imageVector = when (method) {
                      ExpensePaymentMethod.CASH -> Icons.Default.Payments
                      ExpensePaymentMethod.BANK -> Icons.Default.AccountBalance
                      ExpensePaymentMethod.CREDIT_CARD -> Icons.Default.CreditCard
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                  )
                },
                modifier = Modifier.weight(1f)
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Sub-account selection based on chosen payment method
          when (paymentMethod) {
            ExpensePaymentMethod.CASH -> {
              if (cashAccounts.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  items(cashAccounts, key = { it.id }, contentType = { "account_chip" }) { acc ->
                    FilterChip(
                      selected = selectedAccountId == acc.id,
                      onClick = { selectedAccountId = acc.id },
                      label = { Text(acc.bankName, fontSize = 11.sp) }
                    )
                  }
                }
              } else {
                Text(
                  text = "Logging as physical Cash (Wallet)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            ExpensePaymentMethod.BANK -> {
              val availableBanks = if (bankAccountsOnly.isNotEmpty()) bankAccountsOnly else bankAccounts.filter { !it.accountType.equals("Credit Card", ignoreCase = true) }
              if (availableBanks.isNotEmpty()) {
                Text(
                  text = "Select Bank Account:",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  items(availableBanks, key = { it.id }, contentType = { "account_chip" }) { acc ->
                    FilterChip(
                      selected = selectedAccountId == acc.id,
                      onClick = { selectedAccountId = acc.id },
                      label = { Text(acc.bankName, fontSize = 11.sp) }
                    )
                  }
                }
              } else {
                Text(
                  text = "No bank accounts added yet.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            ExpensePaymentMethod.CREDIT_CARD -> {
              if (creditCardAccounts.isNotEmpty()) {
                Text(
                  text = "Select Credit Card:",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  items(creditCardAccounts, key = { it.id }, contentType = { "credit_card_chip" }) { card ->
                    FilterChip(
                      selected = selectedAccountId == card.id,
                      onClick = { selectedAccountId = card.id },
                      label = { Text(card.bankName, fontSize = 11.sp) },
                      leadingIcon = {
                        Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(13.dp))
                      }
                    )
                  }
                }
              } else {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      Icons.Default.CreditCardOff,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "No credit cards found. Add a credit card in the Credit Cards tab to track dues.",
                      style = MaterialTheme.typography.bodySmall,
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onErrorContainer
                    )
                  }
                }
              }
            }
          }
        }

        // Category dropdown
        ExposedDropdownMenuBox(
          expanded = categoryDropdownExpanded,
          onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
        ) {
          OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
              .fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = categoryDropdownExpanded,
            onDismissRequest = { categoryDropdownExpanded = false }
          ) {
            categories.forEach { cat ->
              DropdownMenuItem(
                text = { Text(cat.name) },
                onClick = {
                  selectedCategory = cat.name
                  categoryDropdownExpanded = false
                }
              )
            }
          }
        }

        // Link to Trip (Optional)
        if (trips.isNotEmpty()) {
          Text("Assign to Trip (Optional):", style = MaterialTheme.typography.bodySmall)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "no_trip", contentType = "trip_chip") {
              FilterChip(
                selected = selectedTripId == null,
                onClick = { selectedTripId = null },
                label = { Text("None") }
              )
            }
            items(trips, key = { it.id }, contentType = { "trip_chip" }) { trip ->
              FilterChip(
                selected = selectedTripId == trip.id,
                onClick = { selectedTripId = trip.id },
                label = { Text(trip.tripName) }
              )
            }
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val amt = amountStr.toDoubleOrNull() ?: 0.0
          val finalAccountName = when {
            selectedAccountId != null -> selectedAccountName
            paymentMethod == ExpensePaymentMethod.CASH -> "Cash"
            paymentMethod == ExpensePaymentMethod.CREDIT_CARD -> "Credit Card"
            else -> "Bank Account"
          }
          val finalNotes = if (paymentMethod == ExpensePaymentMethod.CREDIT_CARD && !notes.contains("Credit Card", ignoreCase = true)) {
            if (notes.isBlank()) "Paid via Credit Card" else "$notes (Paid via Credit Card)"
          } else {
            notes
          }
          onConfirm(title, amt, selectedCategory, selectedAccountId, finalAccountName, finalNotes, selectedTripId)
        },
        enabled = title.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0,
        modifier = Modifier.testTag("confirm_save_expense_button")
      ) {
        Text("Save Expense")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
private fun AddIncomeDialog(
  bankAccounts: List<BankAccount>,
  onDismiss: () -> Unit,
  onConfirm: (title: String, amount: Double, category: String, accountId: Long?, accountName: String, notes: String) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var amountStr by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("Salary") }
  var selectedAccountId by remember { mutableStateOf(bankAccounts.firstOrNull()?.id) }
  var notes by remember { mutableStateOf("") }
  var smsParseMessage by remember { mutableStateOf<String?>(null) }
  val clipboard = LocalClipboardManager.current

  val incomeSources = listOf("Salary", "Freelance", "Business", "Investment", "Bonus", "Gift", "Other")

  val selectedAccountName = remember(selectedAccountId, bankAccounts) {
    bankAccounts.find { it.id == selectedAccountId }?.bankName ?: ""
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Income") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        OutlinedButton(
          onClick = {
            val smsText = clipboard.getText()?.text.orEmpty()
            val parsed = BankSmsParser.parseSms(smsText, bankAccounts)
            when {
              parsed == null -> smsParseMessage = "Copy a bank SMS with an amount, then try again."
              !parsed.isIncome -> smsParseMessage = "This SMS looks like an expense. Use Add Expense to record it."
              else -> {
                title = parsed.merchantOrTitle
                amountStr = parsed.amount.toString()
                val lowerSms = smsText.lowercase()
                selectedCategory = when {
                  lowerSms.contains("salary") || lowerSms.contains("payroll") -> "Salary"
                  lowerSms.contains("freelance") -> "Freelance"
                  lowerSms.contains("business") -> "Business"
                  lowerSms.contains("investment") || lowerSms.contains("interest") -> "Investment"
                  lowerSms.contains("bonus") -> "Bonus"
                  lowerSms.contains("gift") -> "Gift"
                  else -> "Other"
                }
                parsed.matchedAccountId?.let { matchedId ->
                  if (bankAccounts.any { it.id == matchedId }) selectedAccountId = matchedId
                }
                notes = parsed.rawNotes
                smsParseMessage = "Income details filled from SMS."
              }
            }
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Auto-Fill from SMS")
        }
        smsParseMessage?.let { message ->
          Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Income Description") },
          placeholder = { Text("e.g. Monthly Salary, Freelance project") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = amountStr,
          onValueChange = { amountStr = it },
          label = { Text("Income Amount (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        // Select Deposit Bank Account
        if (bankAccounts.isNotEmpty()) {
          Column {
            Text("Deposit into Account:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              items(bankAccounts, key = { it.id }, contentType = { "account_chip" }) { acc ->
                FilterChip(
                  selected = selectedAccountId == acc.id,
                  onClick = { selectedAccountId = acc.id },
                  label = { Text(acc.bankName, fontSize = 11.sp) }
                )
              }
            }
          }
        }

        // Source Category
        Column {
          Text("Source Category:", style = MaterialTheme.typography.bodySmall)
          Spacer(modifier = Modifier.height(4.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(incomeSources, key = { it }, contentType = { "income_source_chip" }) { src ->
              FilterChip(
                selected = selectedCategory == src,
                onClick = { selectedCategory = src },
                label = { Text(src, fontSize = 11.sp) }
              )
            }
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val amt = amountStr.toDoubleOrNull() ?: 0.0
          onConfirm(title, amt, selectedCategory, selectedAccountId, selectedAccountName, notes)
        },
        enabled = title.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0
      ) {
        Text("Save Income")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAccountDialog(
  onDismiss: () -> Unit,
  onConfirm: (name: String, type: String, openingBal: Double, num: String, ifsc: String, holder: String, notes: String) -> Unit
) {
  var bankName by remember { mutableStateOf("") }
  var accountType by remember { mutableStateOf("Savings") }
  var openingBalStr by remember { mutableStateOf("") }
  var accountNumber by remember { mutableStateOf("") }
  var ifsc by remember { mutableStateOf("") }
  var holderName by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }

  val types = listOf("Savings", "Current", "Cash Wallet", "Salary Account", "Other")

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(24.dp),
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    title = { Text("Add Bank / Cash Account", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        OutlinedTextField(
          value = bankName,
          onValueChange = { bankName = it },
          label = { Text("Account / Bank Name *") },
          placeholder = { Text("e.g. HDFC Bank, SBI, Cash Wallet") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("add_account_name_input")
        )

        OutlinedTextField(
          value = openingBalStr,
          onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
              openingBalStr = input
            }
          },
          label = { Text("Opening Balance (₹) *") },
          placeholder = { Text("Starting balance e.g. 50000") },
          prefix = { Text("₹ ") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("add_account_opening_balance_input")
        )

        Text("Account Type:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
        ) {
          types.forEach { type ->
            FilterChip(
              selected = accountType == type,
              onClick = { accountType = type },
              label = { Text(type, fontSize = 11.5.sp) }
            )
          }
        }

        OutlinedTextField(
          value = accountNumber,
          onValueChange = { accountNumber = it },
          label = { Text("Account Number (Optional)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val bal = openingBalStr.toDoubleOrNull() ?: 0.0
          onConfirm(bankName, accountType, bal, accountNumber, ifsc, holderName, notes)
        },
        enabled = bankName.isNotBlank(),
        modifier = Modifier.testTag("confirm_create_account_button")
      ) {
        Text("Create Account")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTripDialog(
  onDismiss: () -> Unit,
  onConfirm: (name: String, dest: String, budget: Double, start: String, end: String, notes: String) -> Unit
) {
  var tripName by remember { mutableStateOf("") }
  var destination by remember { mutableStateOf("") }
  var budgetStr by remember { mutableStateOf("") }
  var startDate by remember { mutableStateOf("") }
  var endDate by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }

  var showStartDatePicker by remember { mutableStateOf(false) }
  var showEndDatePicker by remember { mutableStateOf(false) }

  val startDatePickerState = rememberDatePickerState()
  val endDatePickerState = rememberDatePickerState()

  if (showStartDatePicker) {
    DatePickerDialog(
      onDismissRequest = { showStartDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          startDatePickerState.selectedDateMillis?.let { millis ->
            startDate = formatFinanceTimestamp(millis, TRIP_PICKER_DATE_FORMATTER)
          }
          showStartDatePicker = false
        }) {
          Text("OK")
        }
      },
      dismissButton = {
        TextButton(onClick = { showStartDatePicker = false }) {
          Text("Cancel")
        }
      }
    ) {
      DatePicker(state = startDatePickerState)
    }
  }

  if (showEndDatePicker) {
    DatePickerDialog(
      onDismissRequest = { showEndDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          endDatePickerState.selectedDateMillis?.let { millis ->
            endDate = formatFinanceTimestamp(millis, TRIP_PICKER_DATE_FORMATTER)
          }
          showEndDatePicker = false
        }) {
          Text("OK")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEndDatePicker = false }) {
          Text("Cancel")
        }
      }
    ) {
      DatePicker(state = endDatePickerState)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create Trip Budget") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        OutlinedTextField(
          value = tripName,
          onValueChange = { tripName = it },
          label = { Text("Trip / Vacation Name") },
          placeholder = { Text("e.g. Goa Trip, Euro Summer") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = destination,
          onValueChange = { destination = it },
          label = { Text("Destination") },
          placeholder = { Text("e.g. Paris, Tokyo, Bali") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = budgetStr,
          onValueChange = { budgetStr = it },
          label = { Text("Total Budget (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          OutlinedTextField(
            value = startDate,
            onValueChange = { startDate = it },
            readOnly = true,
            label = { Text("Start Date") },
            placeholder = { Text("Tap to Pick") },
            trailingIcon = {
              IconButton(onClick = { showStartDatePicker = true }) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Pick start date", modifier = Modifier.size(18.dp))
              }
            },
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .clickable { showStartDatePicker = true }
          )

          OutlinedTextField(
            value = endDate,
            onValueChange = { endDate = it },
            readOnly = true,
            label = { Text("End Date") },
            placeholder = { Text("Tap to Pick") },
            trailingIcon = {
              IconButton(onClick = { showEndDatePicker = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick end date", modifier = Modifier.size(18.dp))
              }
            },
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .clickable { showEndDatePicker = true }
          )
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Itinerary / Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val b = budgetStr.toDoubleOrNull() ?: 0.0
          onConfirm(tripName, destination, b, startDate, endDate, notes)
        },
        enabled = tripName.isNotBlank() && (budgetStr.toDoubleOrNull() ?: 0.0) > 0.0
      ) {
        Text("Create Trip")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

// -------------------------------------------------------------
// BACKUP & RESTORE MODAL & PASTE DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupRestoreBottomSheet(
  categoriesCount: Int,
  banksCount: Int,
  tripsCount: Int,
  expensesCount: Int,
  loansCount: Int,
  debtsCount: Int,
  subscriptionsCount: Int,
  cardRemindersCount: Int,
  replaceExisting: Boolean,
  onReplaceExistingChange: (Boolean) -> Unit,
  onExportToFile: () -> Unit,
  onShareBackup: () -> Unit,
  onCopyJson: () -> Unit,
  onImportFromFile: () -> Unit,
  onPasteJsonClick: () -> Unit,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 36.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text("Data Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Export & import offline JSON files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      // Export Section
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Data (Backup)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
          Text(
            text = "Choose a destination folder (Downloads, Drive, SD Card) to save your offline JSON backup.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Button(
            onClick = onExportToFile,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Backup to Device (Choose Folder)")
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = onShareBackup,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Share File", fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
            OutlinedButton(
              onClick = onCopyJson,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Copy JSON", fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
          }
        }
      }

      // Import Section
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color(0xFF059669))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Data (Restore)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
          Text(
            text = "Browse your phone storage to select an OmniCalc JSON file to restore.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FilterChip(
              selected = !replaceExisting,
              onClick = { onReplaceExistingChange(false) },
              label = { Text("Merge Data") },
              modifier = Modifier.weight(1f)
            )
            FilterChip(
              selected = replaceExisting,
              onClick = { onReplaceExistingChange(true) },
              label = { Text("Overwrite (Replace)") },
              modifier = Modifier.weight(1f)
            )
          }

          Button(
            onClick = onImportFromFile,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Backup File from Device")
          }

          OutlinedButton(
            onClick = onPasteJsonClick,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Paste JSON Text Directly", fontSize = 12.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun PasteJsonBackupDialog(
  replaceExisting: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (jsonText: String, replace: Boolean) -> Unit
) {
  var jsonInput by remember { mutableStateOf("") }
  var replaceMode by remember { mutableStateOf(replaceExisting) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Paste Backup JSON")
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = "Paste the exported JSON backup code below to restore your categories, bank details, and trips.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
          value = jsonInput,
          onValueChange = { jsonInput = it },
          placeholder = { Text("{\n  \"version\": 1,\n  \"categories\": [...],\n  \"bankAccounts\": [...]\n}") },
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
          textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
          maxLines = 10
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilterChip(
            selected = !replaceMode,
            onClick = { replaceMode = false },
            label = { Text("Merge", fontSize = 11.sp) },
            modifier = Modifier.weight(1f)
          )
          FilterChip(
            selected = replaceMode,
            onClick = { replaceMode = true },
            label = { Text("Replace", fontSize = 11.sp) },
            modifier = Modifier.weight(1f)
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(jsonInput, replaceMode) },
        enabled = jsonInput.isNotBlank()
      ) {
        Text("Restore")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

// -------------------------------------------------------------
// MONTHLY BUDGET PROGRESS BAR & ALERTS CARD
// -------------------------------------------------------------
@Composable
private fun MonthlyBudgetCard(
  spent: Double,
  limit: Double,
  onEditBudget: () -> Unit
) {
  val ratio = if (limit > 0) (spent / limit).toFloat() else 0f
  val progress = ratio.coerceIn(0f, 1f)
  val isOver80 = ratio >= 0.8f && ratio < 1.0f
  val isOver100 = ratio >= 1.0f
  val remaining = limit - spent

  val progressColor = when {
    isOver100 -> Color(0xFFEF4444)
    isOver80 -> Color(0xFFF59E0B)
    else -> Color(0xFF10B981)
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("monthly_budget_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(progressColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.Speed,
              contentDescription = null,
              tint = progressColor,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Monthly Expense Budget",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Target: ${CurrencyFormatter.format(limit)}",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        IconButton(
          onClick = onEditBudget,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            Icons.Default.Edit,
            contentDescription = "Set monthly target",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      // Spent vs Target row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        Column {
          Text(
            text = "Total Spent This Month",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = CurrencyFormatter.format(spent),
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isOver100) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = if (isOver100) "Over Budget" else "Remaining",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = if (isOver100) "+${CurrencyFormatter.format(spent - limit)}" else CurrencyFormatter.format(remaining),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isOver100) Color(0xFFEF4444) else Color(0xFF10B981)
          )
        }
      }

      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = progressColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
      )

      // Alert Warning Banner when >= 80% or >= 100%
      if (isOver100) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFFFEE2E2),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB91C1C), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "🚨 Warning: You have exceeded your monthly budget by ${CurrencyFormatter.format(spent - limit)} (${(ratio * 100).toInt()}%)!",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFFB91C1C)
            )
          }
        }
      } else if (isOver80) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFFFEF3C7),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "⚠️ Caution: You have reached ${(ratio * 100).toInt()}% of your monthly target limit.",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFFB45309)
            )
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// RECURRING SUBSCRIPTIONS & MONTHLY BILLS SECTION
// -------------------------------------------------------------
@Composable
private fun MarkSubscriptionPaidDialog(
  sub: SubscriptionRecord,
  bankAccounts: List<BankAccount>,
  onDismiss: () -> Unit,
  onConfirm: (BankAccount?) -> Unit
) {
  var selectedBank by remember(sub.id, bankAccounts) {
    mutableStateOf(bankAccounts.firstOrNull { it.id == sub.bankAccountId } ?: bankAccounts.firstOrNull())
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Pay Subscription: ${sub.name}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Select the account or wallet used to pay ${CurrencyFormatter.format(sub.amount)}:")
        if (bankAccounts.isNotEmpty()) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(bankAccounts, key = { it.id }) { account ->
              FilterChip(
                selected = selectedBank?.id == account.id,
                onClick = { selectedBank = account },
                label = { Text(account.bankName, fontSize = 11.sp) }
              )
            }
          }
        } else {
          Text(
            "No accounts are available. The payment will be recorded without an account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(selectedBank) },
        enabled = bankAccounts.isEmpty() || selectedBank != null
      ) {
        Text("Confirm Payment")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
private fun SubscriptionsSection(
  subscriptions: List<SubscriptionRecord>,
  onAddSubscriptionClick: () -> Unit,
  onSelectSubscription: (SubscriptionRecord) -> Unit = {},
  onMarkPaid: (SubscriptionRecord) -> Unit,
  onDeleteSubscription: (SubscriptionRecord) -> Unit
) {
  val haptic = LocalHapticFeedback.current
  val today = LocalDate.now()
  val totalMonthlyBills = remember(subscriptions) {
    subscriptions.sumOf { subscription ->
      when (subscription.billingCycle.uppercase()) {
        "WEEKLY" -> subscription.amount * 52.0 / 12.0
        "YEARLY" -> subscription.amount / 12.0
        else -> subscription.amount
      }
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item(key = "subscriptions_header", contentType = "subscriptions_header") {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = Color(0xFF6366F1).copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  Icons.Default.EventRepeat,
                  contentDescription = null,
                  tint = Color(0xFF6366F1),
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Recurring Bills & Subscriptions",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Track Netflix, Rent, Gym, SIP with due-date countdowns",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          HorizontalDivider(color = Color(0xFF6366F1).copy(alpha = 0.2f))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Total Monthly Commitments", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                text = CurrencyFormatter.format(totalMonthlyBills),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4F46E5)
              )
            }

            FilledTonalButton(
              onClick = onAddSubscriptionClick,
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("+ Add Bill", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }

    if (subscriptions.isEmpty()) {
      item(key = "empty_subscriptions", contentType = "empty_state") {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              Icons.Default.Schedule,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No recurring bills tracked yet",
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Tap '+ Add Bill' to track Netflix, Rent, SIP, WiFi, Gym, etc.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }
        }
      }
    } else {
      items(subscriptions, key = { it.id }, contentType = { "subscription_item" }) { sub ->
        val onSubClick = remember(sub.id, onSelectSubscription) { { onSelectSubscription(sub) } }
        val onSubPaidClick = remember(sub.id, onMarkPaid) { { onMarkPaid(sub) } }
        SubscriptionItemCard(
          sub = sub,
          today = today,
          onClick = onSubClick,
          onMarkPaid = onSubPaidClick
        )
      }
    }
  }
}

@Composable
private fun SubscriptionItemCard(
  sub: SubscriptionRecord,
  today: LocalDate,
  onClick: () -> Unit,
  onMarkPaid: () -> Unit
) {
  val daysLeft = remember(sub.billingCycle, sub.dueDayOfMonth, sub.dueMonth, sub.dueDayOfWeek, today) {
    ChronoUnit.DAYS.between(
      today,
      SubscriptionDueDateCalculator.nextDueDate(sub, today)
    ).toInt()
  }
  val statusLabel = when {
    daysLeft == 0 -> stringResource(com.example.R.string.subscription_due_today)
    daysLeft == 1 -> stringResource(com.example.R.string.subscription_due_in_one_day)
    else -> stringResource(com.example.R.string.subscription_due_in_days, daysLeft)
  }

  val isUrgent = daysLeft in 0..3

  val isPaidThisCycle = remember(sub.billingCycle, sub.dueDayOfMonth, sub.dueMonth, sub.dueDayOfWeek, sub.lastPaidTimestamp, today) {
    SubscriptionDueDateCalculator.isPaidInCurrentCycle(sub, today)
  }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.Receipt,
              contentDescription = null,
              tint = Color(0xFF6366F1),
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = sub.name,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${sub.categoryName} • ${sub.billingCycle}",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Text(
          text = CurrencyFormatter.format(sub.amount),
          fontWeight = FontWeight.ExtraBold,
          fontSize = 16.sp,
          color = Color(0xFF4F46E5)
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isPaidThisCycle) Color(0xFFD1FAE5) else if (isUrgent) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant
          ) {
            Text(
              text = if (isPaidThisCycle) stringResource(com.example.R.string.subscription_paid_this_cycle) else statusLabel,
              fontSize = 10.5.sp,
              fontWeight = FontWeight.Bold,
              color = if (isPaidThisCycle) Color(0xFF047857) else if (isUrgent) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }

          if (sub.bankAccountName.isNotBlank()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "via ${sub.bankAccountName}",
              fontSize = 10.5.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          FilledTonalButton(
            onClick = onMarkPaid,
            enabled = !isPaidThisCycle,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.height(28.dp)
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              if (isPaidThisCycle) "Paid" else "Mark Paid",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
          }

          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun SubscriptionDetailsDialog(
  sub: SubscriptionRecord,
  today: LocalDate,
  onDismiss: () -> Unit,
  onEdit: () -> Unit,
  onMarkPaid: () -> Unit,
  onDelete: () -> Unit
) {
  val nextDueDate = SubscriptionDueDateCalculator.nextDueDate(sub, today)
  val daysLeft = ChronoUnit.DAYS.between(today, nextDueDate).toInt()
  val isPaidThisCycle = SubscriptionDueDateCalculator.isPaidInCurrentCycle(sub, today)
  val isUrgent = daysLeft in 0..3

  val statusLabel = when {
    isPaidThisCycle -> "Paid for this cycle ✓"
    daysLeft < 0 -> "Overdue by ${-daysLeft} days"
    daysLeft == 0 -> "Due Today!"
    daysLeft == 1 -> "Due Tomorrow"
    else -> "Due in $daysLeft days"
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(24.dp),
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(
            text = "Bill & Subscription",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF6366F1).copy(alpha = 0.12f),
            modifier = Modifier.padding(top = 4.dp)
          ) {
            Text(
              text = sub.billingCycle,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF4F46E5),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onEdit,
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              Icons.Default.Edit,
              contentDescription = "Edit Bill",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              Icons.Default.DeleteOutline,
              contentDescription = "Delete Bill",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFF6366F1).copy(alpha = 0.08f),
          border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.25f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "${CurrencyFormatter.format(sub.amount)} / ${sub.billingCycle.lowercase()}",
              fontSize = 24.sp,
              fontWeight = FontWeight.Black,
              color = Color(0xFF4F46E5)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = sub.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Payment Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isPaidThisCycle) Color(0xFFD1FAE5) else if (isUrgent) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant
            ) {
              Text(
                text = statusLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPaidThisCycle) Color(0xFF047857) else if (isUrgent) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Next Due Date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = nextDueDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Category", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = sub.categoryName,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold
            )
          }

          if (sub.bankAccountName.isNotBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Payment Account", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                text = sub.bankAccountName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
              )
            }
          }

          if (sub.notes.isNotBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Notes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = sub.notes,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(10.dp)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (!isPaidThisCycle) {
        Button(
          onClick = onMarkPaid,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Mark Paid", fontWeight = FontWeight.Bold)
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", fontWeight = FontWeight.SemiBold)
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSubscriptionDialog(
  sub: SubscriptionRecord,
  categories: List<ExpenseCategory>,
  bankAccounts: List<BankAccount>,
  onDismiss: () -> Unit,
  onConfirm: (SubscriptionRecord) -> Unit
) {
  var name by remember(sub.id) { mutableStateOf(sub.name) }
  var amountStr by remember(sub.id) { mutableStateOf(sub.amount.toString()) }
  var billingCycle by remember(sub.id) { mutableStateOf(sub.billingCycle) }
  var categoryName by remember(sub.id) { mutableStateOf(sub.categoryName) }
  var selectedAccountId by remember(sub.id) { mutableStateOf(sub.bankAccountId) }
  var selectedAccountName by remember(sub.id) { mutableStateOf(sub.bankAccountName) }
  var notes by remember(sub.id) { mutableStateOf(sub.notes) }

  var cycleDropdownExpanded by remember { mutableStateOf(false) }
  var categoryDropdownExpanded by remember { mutableStateOf(false) }
  var accountDropdownExpanded by remember { mutableStateOf(false) }

  val cycles = listOf("MONTHLY", "YEARLY", "WEEKLY")
  val parsedAmount = amountStr.toDoubleOrNull()
  val canSave = name.isNotBlank() && parsedAmount != null && parsedAmount > 0

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(24.dp),
    title = { Text("Edit Subscription / Bill", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Bill / Service Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = amountStr,
          onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) amountStr = input
          },
          label = { Text("Amount (₹) *") },
          prefix = { Text("₹ ") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        // Billing Cycle Dropdown
        ExposedDropdownMenuBox(
          expanded = cycleDropdownExpanded,
          onExpandedChange = { cycleDropdownExpanded = !cycleDropdownExpanded }
        ) {
          OutlinedTextField(
            value = billingCycle,
            onValueChange = {},
            readOnly = true,
            label = { Text("Billing Cycle") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cycleDropdownExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = cycleDropdownExpanded,
            onDismissRequest = { cycleDropdownExpanded = false }
          ) {
            cycles.forEach { cycle ->
              DropdownMenuItem(
                text = { Text(cycle) },
                onClick = {
                  billingCycle = cycle
                  cycleDropdownExpanded = false
                }
              )
            }
          }
        }

        // Category Dropdown
        ExposedDropdownMenuBox(
          expanded = categoryDropdownExpanded,
          onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
        ) {
          OutlinedTextField(
            value = categoryName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = categoryDropdownExpanded,
            onDismissRequest = { categoryDropdownExpanded = false }
          ) {
            categories.forEach { cat ->
              DropdownMenuItem(
                text = { Text(cat.name) },
                onClick = {
                  categoryName = cat.name
                  categoryDropdownExpanded = false
                }
              )
            }
          }
        }

        // Bank Account Dropdown
        ExposedDropdownMenuBox(
          expanded = accountDropdownExpanded,
          onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded }
        ) {
          OutlinedTextField(
            value = selectedAccountName.ifBlank { "None (Manual / Cash)" },
            onValueChange = {},
            readOnly = true,
            label = { Text("Linked Payment Account") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = accountDropdownExpanded,
            onDismissRequest = { accountDropdownExpanded = false }
          ) {
            DropdownMenuItem(
              text = { Text("None (Manual / Cash)") },
              onClick = {
                selectedAccountId = null
                selectedAccountName = ""
                accountDropdownExpanded = false
              }
            )
            bankAccounts.forEach { acc ->
              DropdownMenuItem(
                text = { Text("${acc.bankName} (${acc.accountType})") },
                onClick = {
                  selectedAccountId = acc.id
                  selectedAccountName = acc.bankName
                  accountDropdownExpanded = false
                }
              )
            }
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val amt = parsedAmount ?: return@Button
          onConfirm(
            sub.copy(
              name = name.trim(),
              amount = amt,
              billingCycle = billingCycle,
              categoryName = categoryName,
              bankAccountId = selectedAccountId,
              bankAccountName = selectedAccountName,
              notes = notes.trim()
            )
          )
        },
        enabled = canSave
      ) {
        Text("Save Changes")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

// -------------------------------------------------------------
// TRIP SPEEDOMETER & DAILY SPENDING RATE TRACKER
// -------------------------------------------------------------
@Composable
private fun TripDailySpendingSpeedometer(
  trip: TripRecord,
  spent: Double
) {
  val totalDays = remember(trip.startDate, trip.endDate) { calculateTripDays(trip.startDate, trip.endDate) }
  val dailyBudget = remember(trip.budget, totalDays) {
    if (totalDays > 0) trip.budget / totalDays else trip.budget / 7.0
  }
  val elapsedDays = remember(trip.startDate) { calculateElapsedDays(trip.startDate).coerceAtLeast(1) }
  val dailyRate = remember(spent, elapsedDays) { spent / elapsedDays }
  val burnRatio = if (dailyBudget > 0) (dailyRate / dailyBudget).toFloat() else 0f
  val isExceeding = burnRatio > 1.05f

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Daily Burn Rate Tracker", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (isExceeding) Color(0xFFFEE2E2) else Color(0xFFCCFBF1)
        ) {
          Text(
            if (isExceeding) "Above target" else "On track",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isExceeding) Color(0xFFB91C1C) else Color(0xFF0F766E),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
          )
        }
      }
      Text(
        "Spending ${CurrencyFormatter.format(dailyRate)}/day",
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        color = if (isExceeding) Color(0xFFEF4444) else Color(0xFF0D9488)
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Daily target", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${CurrencyFormatter.format(dailyBudget)}/day", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
      }
      LinearProgressIndicator(
        progress = { burnRatio.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(5.dp)),
        color = if (isExceeding) Color(0xFFEF4444) else Color(0xFF0D9488),
        trackColor = MaterialTheme.colorScheme.surface
      )
      Text("Day $elapsedDays of $totalDays", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

// -------------------------------------------------------------
// TRIP CATEGORY BUDGET CAPS
// -------------------------------------------------------------
@Composable
private fun TripCategoryCapsSection(
  trip: TripRecord,
  tripExpenses: List<ExpenseRecord>,
  onSetCap: () -> Unit,
  onRemoveCap: (String) -> Unit
) {
  val caps = remember(trip.categoryCapsJson) {
    parseCategoryCaps(trip.categoryCapsJson)
  }
  val spentByCategory = remember(tripExpenses) {
    tripExpenses.groupBy { it.categoryName.lowercase() }
      .mapValues { (_, list) -> list.sumOf { it.amount } }
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Category Budget Caps",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      TextButton(
        onClick = onSetCap,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text("+ Set Sub-Budget", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
      }
    }

    if (caps.isEmpty()) {
      Text(
        text = "No sub-budgets set yet (e.g. Flights ₹20k, Hotels ₹15k, Food ₹10k)",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      caps.forEach { (catName, capAmount) ->
        val catSpent = spentByCategory[catName.lowercase()] ?: 0.0
        val ratio = if (capAmount > 0) (catSpent / capAmount).toFloat() else 0f
        val progress = ratio.coerceIn(0f, 1f)
        val isOver = catSpent > capAmount

        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(catName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "${CurrencyFormatter.format(catSpent)} / ${CurrencyFormatter.format(capAmount)}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isOver) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                  onClick = { onRemoveCap(catName) },
                  modifier = Modifier.size(20.dp)
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Remove cap", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            LinearProgressIndicator(
              progress = { progress },
              modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = if (isOver) Color(0xFFEF4444) else Color(0xFF0D9488),
              trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// DIALOG: ADD SUBSCRIPTION
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubscriptionDialog(
  categories: List<ExpenseCategory>,
  bankAccounts: List<BankAccount>,
  onDismiss: () -> Unit,
  onConfirm: (name: String, amount: Double, billingCycle: String, dueDayOfMonth: Int, dueMonth: Int, dueDayOfWeek: Int, categoryName: String, bankAccountId: Long?, bankAccountName: String, notes: String) -> Unit
) {
  var name by remember { mutableStateOf("") }
  var amountStr by remember { mutableStateOf("") }
  var cycle by remember { mutableStateOf("MONTHLY") }
  var dueDayStr by remember { mutableStateOf("1") }
  var dueMonth by remember { mutableIntStateOf(LocalDate.now().monthValue) }
  var dueDayOfWeek by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
  var isDueMonthMenuExpanded by remember { mutableStateOf(false) }
  var isDueWeekdayMenuExpanded by remember { mutableStateOf(false) }
  var selectedCategory by remember { mutableStateOf(categories.firstOrNull()?.name ?: "Bills & Utilities") }
  var selectedBank by remember { mutableStateOf(bankAccounts.firstOrNull()) }
  var notes by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.EventRepeat, contentDescription = null, tint = Color(0xFF6366F1))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Track Recurring Bill")
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Bill / Service Name") },
          placeholder = { Text("e.g. Netflix, Rent, Gym, SIP") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        NumberInputField(
          value = amountStr,
          onValueChange = { amountStr = it },
          label = "Amount per Cycle",
          prefix = "₹"
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf("MONTHLY", "YEARLY", "WEEKLY").forEach { c ->
            FilterChip(
              selected = cycle == c,
              onClick = { cycle = c },
              label = { Text(c, fontSize = 11.sp) },
              modifier = Modifier.weight(1f)
            )
          }
        }

        if (cycle == "WEEKLY") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(stringResource(com.example.R.string.subscription_due_weekday))
            Box {
              TextButton(onClick = { isDueWeekdayMenuExpanded = true }) {
                Text(
                  java.time.DayOfWeek.of(dueDayOfWeek).getDisplayName(
                    java.time.format.TextStyle.FULL,
                    Locale.getDefault()
                  )
                )
              }
              DropdownMenu(
                expanded = isDueWeekdayMenuExpanded,
                onDismissRequest = { isDueWeekdayMenuExpanded = false }
              ) {
                java.time.DayOfWeek.values().forEach { day ->
                  DropdownMenuItem(
                    text = {
                      Text(day.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()))
                    },
                    onClick = {
                      dueDayOfWeek = day.value
                      isDueWeekdayMenuExpanded = false
                    }
                  )
                }
              }
            }
          }
        } else {
          OutlinedTextField(
            value = dueDayStr,
            onValueChange = { str ->
              if (str.isEmpty() || str.all { it.isDigit() }) dueDayStr = str
            },
            label = { Text("Due Day of Month (1-31)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }

        if (cycle == "YEARLY") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(stringResource(com.example.R.string.subscription_due_month))
            Box {
              TextButton(onClick = { isDueMonthMenuExpanded = true }) {
                Text(
                  java.time.Month.of(dueMonth).getDisplayName(
                    java.time.format.TextStyle.FULL,
                    Locale.getDefault()
                  )
                )
              }
              DropdownMenu(
                expanded = isDueMonthMenuExpanded,
                onDismissRequest = { isDueMonthMenuExpanded = false }
              ) {
                java.time.Month.values().forEach { month ->
                  DropdownMenuItem(
                    text = {
                      Text(month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()))
                    },
                    onClick = {
                      dueMonth = month.value
                      isDueMonthMenuExpanded = false
                    }
                  )
                }
              }
            }
          }
        }

        if (bankAccounts.isNotEmpty()) {
          Text("Pay via Account:", style = MaterialTheme.typography.labelSmall)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(bankAccounts, key = { it.id }, contentType = { "account_chip" }) { acc ->
              FilterChip(
                selected = selectedBank?.id == acc.id,
                onClick = { selectedBank = acc },
                label = { Text(acc.bankName, fontSize = 11.sp) }
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val amt = amountStr.toDoubleOrNull() ?: 0.0
          val dueDay = dueDayStr.toIntOrNull()?.coerceIn(1, 31) ?: 1
          if (name.isNotBlank() && amt > 0.0) {
            onConfirm(name, amt, cycle, dueDay, dueMonth, dueDayOfWeek, selectedCategory, selectedBank?.id, selectedBank?.bankName ?: "", notes)
          }
        },
        enabled = name.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0
      ) {
        Text("Save Bill")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

// -------------------------------------------------------------
// DIALOG: SET MONTHLY BUDGET
// -------------------------------------------------------------
@Composable
private fun SetMonthlyBudgetDialog(
  currentLimit: Double,
  onDismiss: () -> Unit,
  onConfirm: (Double) -> Unit
) {
  var limitStr by remember { mutableStateOf(if (currentLimit > 0) currentLimit.toLong().toString() else "50000") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Monthly Expense Target")
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = "Set your total monthly spending target limit. OmniCalc will track your daily burn and warn you when you cross 80% and 100%.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        NumberInputField(
          value = limitStr,
          onValueChange = { limitStr = it },
          label = "Target Monthly Budget",
          prefix = "₹"
        )

        // Quick Preset Chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(25000L, 50000L, 75000L, 100000L).forEach { preset ->
            FilterChip(
              selected = limitStr == preset.toString(),
              onClick = { limitStr = preset.toString() },
              label = { Text("₹${preset / 1000}k", fontSize = 11.sp) },
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val lim = limitStr.toDoubleOrNull() ?: 0.0
          if (lim > 0) onConfirm(lim)
        },
        enabled = (limitStr.toDoubleOrNull() ?: 0.0) > 0.0
      ) {
        Text("Save Target")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

// -------------------------------------------------------------
// DIALOG: SET TRIP CATEGORY SUB-BUDGET CAP
// -------------------------------------------------------------
@Composable
private fun SetTripCategoryCapDialog(
  trip: TripRecord,
  categories: List<ExpenseCategory>,
  onDismiss: () -> Unit,
  onConfirm: (categoryName: String, capAmount: Double) -> Unit
) {
  var selectedCategory by remember { mutableStateOf(categories.firstOrNull()?.name ?: "Flights & Transport") }
  var capStr by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Sell, contentDescription = null, tint = Color(0xFF0D9488))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Set Sub-Budget Cap")
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = "Set a specific category budget ceiling inside '${trip.tripName}' (e.g. Flights ₹20,000, Hotels ₹15,000, Food ₹10,000).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Select Category:", style = MaterialTheme.typography.labelSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(categories, key = { "${it.id}_${it.name}" }, contentType = { "category_chip" }) { cat ->
            FilterChip(
              selected = selectedCategory == cat.name,
              onClick = { selectedCategory = cat.name },
              label = { Text(cat.name, fontSize = 11.sp) }
            )
          }
        }

        NumberInputField(
          value = capStr,
          onValueChange = { capStr = it },
          label = "Sub-Budget Ceiling",
          prefix = "₹"
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val amt = capStr.toDoubleOrNull() ?: 0.0
          if (selectedCategory.isNotBlank() && amt > 0.0) {
            onConfirm(selectedCategory, amt)
          }
        },
        enabled = (capStr.toDoubleOrNull() ?: 0.0) > 0.0
      ) {
        Text("Set Cap")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

// -------------------------------------------------------------
// HELPER FUNCTIONS
// -------------------------------------------------------------
private val tripDateFormatters = listOf(
  DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.getDefault()).withResolverStyle(ResolverStyle.STRICT),
  DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.getDefault()).withResolverStyle(ResolverStyle.STRICT),
  DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.getDefault()).withResolverStyle(ResolverStyle.STRICT)
)

private fun parseFlexibleTripDate(dateStr: String): LocalDate? {
  val trimmed = dateStr.trim()
  if (trimmed.isEmpty()) return null

  for (formatter in tripDateFormatters) {
    try {
      return LocalDate.parse(trimmed, formatter)
    } catch (_: DateTimeParseException) {
      // Try the next supported stored date format.
    }
  }
  return null
}

private fun calculateTripDays(startDateStr: String, endDateStr: String): Int {
  val start = parseFlexibleTripDate(startDateStr) ?: return 7
  val end = parseFlexibleTripDate(endDateStr) ?: return 7
  return (ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceAtLeast(1)
}

private fun calculateElapsedDays(startDateStr: String): Int {
  val start = parseFlexibleTripDate(startDateStr) ?: return 1
  return (ChronoUnit.DAYS.between(start, LocalDate.now()).toInt() + 1).coerceAtLeast(1)
}

private fun parseCategoryCaps(jsonStr: String): Map<String, Double> {
  if (jsonStr.isBlank() || jsonStr == "{}") return emptyMap()
  return try {
    val json = JSONObject(jsonStr)
    val map = mutableMapOf<String, Double>()
    val keys = json.keys()
    while (keys.hasNext()) {
      val k = keys.next()
      map[k] = json.optDouble(k, 0.0)
    }
    map
  } catch (e: Exception) {
    emptyMap()
  }
}

@Composable
private fun CreditCardsSection(
  cardAccounts: List<AccountWithBalance>,
  reminders: List<CreditCardBillReminder>,
  onAddCardClick: () -> Unit,
  onCardClick: (BankAccount) -> Unit = {},
  onSetBillReminder: (Long) -> Unit,
  onMarkBillPaid: (CreditCardBillReminder) -> Unit,
  onShowTransactions: (Long) -> Unit,
  onDeleteCard: (BankAccount) -> Unit,
  modifier: Modifier = Modifier
) {
  val totalOutstanding = remember(cardAccounts) { cardAccounts.sumOf { kotlin.math.abs(it.currentBalance) } }
  val todayEpoch = remember { LocalDate.now().toEpochDay() }
  val unpaidReminders = remember(reminders) { reminders.filter { it.paidDateEpochDay < 0L } }
  val totalUnpaidBills = remember(unpaidReminders) { unpaidReminders.sumOf { it.amount } }
  val nextDueReminder = remember(unpaidReminders) { unpaidReminders.minByOrNull { it.dueDateEpochDay } }
  val remindersByAccountId = remember(reminders) { reminders.associateBy { it.accountId } }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item(key = "credit_cards_summary", contentType = "credit_cards_summary") {
      // Header Summary Card
      Card(
        modifier = Modifier.fillMaxWidth().testTag("credit_cards_summary_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                "Total Credit Outstanding",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = CurrencyFormatter.format(totalOutstanding),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (totalOutstanding > 0) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurface
              )
            }
            FilledTonalButton(
              onClick = onAddCardClick,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("add_card_button")
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Add Card", fontWeight = FontWeight.SemiBold)
            }
          }

          Spacer(Modifier.height(14.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
          Spacer(Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                "Active Cards",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                "${cardAccounts.size} Cards",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                "Pending Bills",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = CurrencyFormatter.format(totalUnpaidBills),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (totalUnpaidBills > 0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
              )
            }

            Column(modifier = Modifier.weight(1.2f)) {
              Text(
                "Next Due Date",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              val dueText = if (nextDueReminder != null) {
                val diff = (nextDueReminder.dueDateEpochDay - todayEpoch).toInt()
                when {
                  diff < 0 -> "Overdue (${-diff}d ago)"
                  diff == 0 -> "Due Today!"
                  else -> "In $diff days (${LocalDate.ofEpochDay(nextDueReminder.dueDateEpochDay).dayOfMonth} ${LocalDate.ofEpochDay(nextDueReminder.dueDateEpochDay).month.name.take(3)})"
                }
              } else {
                "No pending bills"
              }
              Text(
                dueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }

    if (cardAccounts.isEmpty()) {
      item(key = "empty_credit_cards", contentType = "empty_state") {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
          ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.CreditCard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
              )
            }
            Text(
              "No Credit Cards Added",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              "Track your credit cards, outstanding balances, statement due dates, bill payment reminders, and keep your credit score healthy.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(
              onClick = onAddCardClick,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("empty_add_card_button")
            ) {
              Icon(Icons.Default.Add, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text("Add Your First Credit Card", fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    } else {
      items(cardAccounts, key = { it.account.id }, contentType = { "credit_card" }) { accWithBal ->
        val reminder = remindersByAccountId[accWithBal.account.id]
        val outstanding = kotlin.math.abs(accWithBal.currentBalance)
        val isPaid = reminder?.paidDateEpochDay?.let { it >= 0L } == true

        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("credit_card_${accWithBal.account.id}")
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCardClick(accWithBal.account) },
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            // Top Card Art Header
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .background(
                  Brush.horizontalGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF1E1B4B))
                  )
                )
                .padding(18.dp)
            ) {
              Column {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.CreditCard,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                      accWithBal.account.bankName,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = Color.White
                    )
                  }
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                  ) {
                    Text(
                      text = "CREDIT",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                      letterSpacing = 1.sp,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                  }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.Bottom
                ) {
                  Column {
                    Text(
                      "CARD NUMBER",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White.copy(alpha = 0.6f),
                      letterSpacing = 1.sp
                    )
                    Text(
                      if (accWithBal.account.accountNumber.isNotBlank())
                        "•••• •••• •••• " + accWithBal.account.accountNumber.takeLast(4)
                      else "•••• •••• •••• ••••",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Medium,
                      color = Color.White,
                      fontFamily = FontFamily.Monospace
                    )
                  }

                  Column(horizontalAlignment = Alignment.End) {
                    Text(
                      "OUTSTANDING DUES",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White.copy(alpha = 0.6f),
                      letterSpacing = 1.sp
                    )
                    Text(
                      CurrencyFormatter.format(outstanding),
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.Bold,
                      color = if (outstanding > 0) Color(0xFFFDA4AF) else Color(0xFF6EE7B7)
                    )
                  }
                }
              }
            }

            // Bill Reminder & Status Section
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              if (reminder != null) {
                val dueDate = LocalDate.ofEpochDay(reminder.dueDateEpochDay)
                val daysUntilDue = (reminder.dueDateEpochDay - todayEpoch).toInt()

                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                          "Statement Bill: ",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                          CurrencyFormatter.format(reminder.amount),
                          style = MaterialTheme.typography.bodyMedium,
                          fontWeight = FontWeight.Bold
                        )
                      }
                      Spacer(Modifier.height(2.dp))
                      Text(
                        "Due Date: ${dueDate.format(CC_DUE_DATE_FORMATTER)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }

                    // Status Badge
                    if (isPaid) {
                      AssistChip(
                        onClick = { onSetBillReminder(accWithBal.account.id) },
                        label = { Text("Paid ✓", fontSize = 11.sp, color = Color(0xFF10B981)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                      )
                    } else if (daysUntilDue < 0) {
                      AssistChip(
                        onClick = { onMarkBillPaid(reminder) },
                        label = { Text("Overdue (${-daysUntilDue}d)", fontSize = 11.sp, color = Color(0xFFEF4444)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
                      )
                    } else if (daysUntilDue == 0) {
                      AssistChip(
                        onClick = { onMarkBillPaid(reminder) },
                        label = { Text("Due Today!", fontSize = 11.sp, color = Color(0xFFF97316)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF97316).copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.4f))
                      )
                    } else {
                      AssistChip(
                        onClick = { onMarkBillPaid(reminder) },
                        label = { Text("Due in ${daysUntilDue}d", fontSize = 11.sp, color = Color(0xFF3B82F6)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF3B82F6).copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
                      )
                    }
                  }
                }
              }

              // Action Buttons Row
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedButton(
                  onClick = { onShowTransactions(accWithBal.account.id) },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f).testTag("card_txns_btn_${accWithBal.account.id}")
                ) {
                  Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Transactions", fontSize = 12.sp)
                }

                if (reminder != null && !isPaid) {
                  Button(
                    onClick = { onMarkBillPaid(reminder) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier.weight(1f).testTag("card_pay_bill_btn_${accWithBal.account.id}")
                  ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pay Bill", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                  }
                } else {
                  FilledTonalButton(
                    onClick = { onSetBillReminder(accWithBal.account.id) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("card_set_bill_btn_${accWithBal.account.id}")
                  ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (reminder == null) "Set Bill" else "Edit Bill", fontSize = 12.sp)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCreditCardDialog(
  onDismiss: () -> Unit,
  onConfirm: (name: String, outstanding: Double, billAmount: Double?, dueDateEpochDay: Long?, num: String, notes: String) -> Unit
) {
  var cardName by remember { mutableStateOf("") }
  var outstandingStr by remember { mutableStateOf("") }
  var billAmountStr by remember { mutableStateOf("") }
  var lastFourDigits by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }

  val today = LocalDate.now()
  var selectedDueDateEpochDay by remember { mutableStateOf(today.plusDays(15).toEpochDay()) }
  var showDatePicker by remember { mutableStateOf(false) }

  val datePickerState = rememberDatePickerState(
    initialSelectedDateMillis = LocalDate.ofEpochDay(selectedDueDateEpochDay)
      .atStartOfDay(ZoneOffset.UTC)
      .toInstant()
      .toEpochMilli()
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Add Credit Card")
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        OutlinedTextField(
          value = cardName,
          onValueChange = { cardName = it },
          label = { Text("Card / Bank Name *") },
          placeholder = { Text("e.g. HDFC Millennia, SBI SimplyCLICK") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("credit_card_name_input")
        )

        OutlinedTextField(
          value = outstandingStr,
          onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) outstandingStr = input
          },
          label = { Text("Current Outstanding Dues (₹)") },
          placeholder = { Text("0.00") },
          prefix = { Text("₹ ") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("credit_card_outstanding_input")
        )

        OutlinedTextField(
          value = billAmountStr,
          onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) billAmountStr = input
          },
          label = { Text("Latest Statement Bill (₹) (Optional)") },
          placeholder = { Text("Leave blank if no bill generated yet") },
          prefix = { Text("₹ ") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("credit_card_bill_amount_input")
        )

        OutlinedButton(
          onClick = { showDatePicker = true },
          modifier = Modifier.fillMaxWidth().testTag("credit_card_due_date_btn")
        ) {
          Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            "Due Date: ${LocalDate.ofEpochDay(selectedDueDateEpochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))}"
          )
        }

        OutlinedTextField(
          value = lastFourDigits,
          onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) lastFourDigits = it },
          label = { Text("Last 4 Digits of Card (Optional)") },
          placeholder = { Text("e.g. 4321") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("credit_card_digits_input")
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Benefits / Notes (Optional)") },
          placeholder = { Text("e.g. 5% cashback on Amazon & Flipkart") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val outstanding = outstandingStr.toDoubleOrNull() ?: 0.0
          val bill = billAmountStr.toDoubleOrNull()
          onConfirm(
            cardName,
            outstanding,
            bill,
            if (bill != null && bill > 0) selectedDueDateEpochDay else null,
            lastFourDigits,
            notes
          )
        },
        enabled = cardName.isNotBlank(),
        modifier = Modifier.testTag("confirm_add_credit_card_button")
      ) {
        Text("Add Card")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )

  if (showDatePicker) {
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { dateMillis ->
              selectedDueDateEpochDay = Instant.ofEpochMilli(dateMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .toEpochDay()
            }
            showDatePicker = false
          }
        ) { Text("Confirm") }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayCreditCardBillDialog(
  cardAccount: BankAccount,
  reminder: CreditCardBillReminder?,
  bankAccounts: List<BankAccount>,
  onDismiss: () -> Unit,
  onConfirm: (fromBankAccountId: Long, amount: Double) -> Unit
) {
  val liquidAccounts = remember(bankAccounts) {
    bankAccounts.filter { !it.accountType.equals("Credit Card", ignoreCase = true) }
  }
  var selectedFromAccountId by remember { mutableStateOf(liquidAccounts.firstOrNull()?.id) }
  var amountStr by remember {
    mutableStateOf(
      if (reminder != null && reminder.amount > 0) {
        String.format(java.util.Locale.US, "%.0f", reminder.amount)
      } else {
        ""
      }
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        Icons.Default.CreditScore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(28.dp)
      )
    },
    title = {
      Text(
        text = "Pay Credit Card Bill",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        // Target Card Summary Card
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.CreditCard,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = cardAccount.bankName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
              )
              if (reminder != null) {
                Text(
                  text = "Bill Due: ₹${CurrencyFormatter.formatWhole(reminder.amount)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // Amount Input
        OutlinedTextField(
          value = amountStr,
          onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
              amountStr = input
            }
          },
          label = { Text("Payment Amount (₹) *") },
          placeholder = { Text("e.g. 5000") },
          prefix = { Text("₹ ") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pay_cc_amount_input")
        )

        // Select Source Bank Account
        Text(
          text = "Pay From Bank / Cash Account *",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold
        )

        if (liquidAccounts.isEmpty()) {
          Text(
            text = "No bank or cash account found. Please add a bank account first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        } else {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(liquidAccounts, key = { it.id }, contentType = { "account_chip" }) { bank ->
              FilterChip(
                selected = selectedFromAccountId == bank.id,
                onClick = { selectedFromAccountId = bank.id },
                label = { Text(bank.bankName, fontSize = 11.sp) },
                leadingIcon = {
                  Icon(
                    if (bank.accountType.equals("Cash Wallet", ignoreCase = true)) Icons.Default.Payments else Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                  )
                }
              )
            }
          }
        }

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Liability Transfer: Paying your bill deducts from your bank balance and reduces card dues without inflating total expenses.",
              style = MaterialTheme.typography.bodySmall,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val amt = amountStr.toDoubleOrNull() ?: 0.0
          val fromId = selectedFromAccountId
          if (amt > 0.0 && fromId != null) {
            onConfirm(fromId, amt)
          }
        },
        enabled = (amountStr.toDoubleOrNull() ?: 0.0) > 0.0 && selectedFromAccountId != null,
        modifier = Modifier.testTag("confirm_pay_cc_bill_button")
      ) {
        val amt = amountStr.toDoubleOrNull() ?: 0.0
        Text(if (amt > 0) "Pay ₹${CurrencyFormatter.formatWhole(amt)}" else "Pay Bill")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

