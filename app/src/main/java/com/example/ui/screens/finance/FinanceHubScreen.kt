package com.example.ui.screens.finance
import com.example.ui.theme.performConfiguredHapticFeedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.example.ui.components.CreditCardBillReminderDialog
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.NumberInputField
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceHubScreen(
  viewModel: FinanceHubViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val categories by viewModel.categories.collectAsState()
  val expenses by viewModel.expenses.collectAsState()
  val bankAccounts by viewModel.bankAccounts.collectAsState()
  val accountsWithBalances by viewModel.accountsWithBalances.collectAsState()
  val totalNetBalance by viewModel.totalNetBalance.collectAsState()
  val totalIncome by viewModel.totalIncome.collectAsState()
  val totalExpense by viewModel.totalExpense.collectAsState()
  val trips by viewModel.trips.collectAsState()
  val subscriptions by viewModel.subscriptions.collectAsState()
  val allLoans by viewModel.allLoans.collectAsState()
  val allDebts by viewModel.allDebts.collectAsState()
  val creditCardBillReminders by viewModel.creditCardBillReminders.collectAsState()
  val monthlyBudgetLimit by viewModel.monthlyBudgetLimit.collectAsState()
  val currentMonthExpenses by viewModel.currentMonthExpenses.collectAsState()

  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  var preselectedTripIdForExpense by remember { mutableStateOf<Long?>(null) }
  var accountForDetails by remember { mutableStateOf<BankAccount?>(null) }
  var accountPendingDelete by remember { mutableStateOf<BankAccount?>(null) }
  var editingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
  var subscriptionPendingPayment by remember { mutableStateOf<SubscriptionRecord?>(null) }
  var showTransferDialog by remember { mutableStateOf(false) }
  var showCreditCardReminderDialog by remember { mutableStateOf(false) }
  var editingCreditCardAccountId by remember { mutableStateOf<Long?>(null) }
  var showAddCreditCardDialog by remember { mutableStateOf(false) }

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
  var editingAccountForOpeningBalance by remember { mutableStateOf<BankAccount?>(null) }

  // Storage Access Framework (SAF) Launchers for JSON Export & Import
  val exportFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json")
  ) { uri: Uri? ->
    uri?.let { viewModel.exportToUri(context, it) }
  }

  val importFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    uri?.let { viewModel.importFromUri(context, it, uiState.replaceExistingOnImport) }
  }

  // Backup & Restore Dialogs
  if (uiState.showBackupModal) {
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
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        exportFileLauncher.launch("OmniCalc_Finance_Backup_$dateStr.json")
      },
      onShareBackup = { viewModel.shareBackup(context) },
      onCopyJson = { viewModel.copyBackupJson(context) },
      onImportFromFile = {
        importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
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
    AddIncomeDialog(
      bankAccounts = bankAccounts,
      onDismiss = { viewModel.setShowAddIncome(false) },
      onConfirm = { title, amount, category, accountId, accountName, notes ->
        viewModel.addIncome(title, amount, category, accountId, accountName, notes)
      }
    )
  }

  editingExpense?.let { expense ->
    EditExpenseDialog(
      expense = expense,
      categories = categories,
      onDismiss = { editingExpense = null },
      onConfirm = { title, amount, category ->
        viewModel.updateExpense(expense, title, amount, category)
        editingExpense = null
      }
    )
  }

  subscriptionPendingPayment?.let { subscription ->
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
          viewModel.markCreditCardBillPaid(rem)
          accountForDetails = null
        }
      }
    )
  }

  if (showCreditCardReminderDialog) {
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
    SetMonthlyBudgetDialog(
      currentLimit = monthlyBudgetLimit,
      onDismiss = { viewModel.setShowSetBudget(false) },
      onConfirm = { newLimit ->
        viewModel.setMonthlyBudgetLimit(newLimit)
      }
    )
  }

  uiState.selectedTripForCategoryCap?.let { trip ->
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
        FinanceTab.DEBTS -> null
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Top Header with Backup / Restore Quick Action
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
          Text(
            text = "Finance & Money",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "Custom categories, bank accounts & trips",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        FilledTonalButton(
          onClick = {
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.setShowBackupModal(true)
          },
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.defaultMinSize(minHeight = 36.dp).testTag("backup_restore_header_btn")
        ) {
          Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = "Backup / Restore",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
          )
        }
      }

      // Modern Tab Bar (Expenses & Accounts, Credit Cards, Subscriptions, Lend & Borrow, Trip Manager)
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
        FinanceTab.EXPENSES -> ExpensesSection(
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
          selectedAccountFilterId = uiState.selectedAccountFilterId,
          onSelectAccountFilter = { viewModel.setSelectedAccountFilter(it) },
          onAddAccountClick = { viewModel.setShowAddBank(true) },
          onEditOpeningBalance = { editingAccountForOpeningBalance = it },
          onOpenAccountDetails = { accountForDetails = it },
          onAddExpenseClick = { viewModel.setShowAddExpense(true) },
          onAddIncomeClick = { viewModel.setShowAddIncome(true) },
          onPasteSmsClick = { viewModel.logFromClipboardSms(context) },
          onTransferClick = { showTransferDialog = true },
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
          onEditExpense = { editingExpense = it },
          onSetCreditCardBill = { accountId ->
            editingCreditCardAccountId = accountId
            showCreditCardReminderDialog = true
          },
          onMarkCreditCardBillPaid = { reminder ->
            viewModel.markCreditCardBillPaid(reminder)
          }
        )
        FinanceTab.CREDIT_CARDS -> CreditCardsSection(
          cardAccounts = accountsWithBalances.filter { it.account.accountType.equals("Credit Card", ignoreCase = true) },
          reminders = creditCardBillReminders,
          onAddCardClick = { showAddCreditCardDialog = true },
          onSetBillReminder = { accountId ->
            editingCreditCardAccountId = accountId
            showCreditCardReminderDialog = true
          },
          onMarkBillPaid = { reminder ->
            viewModel.markCreditCardBillPaid(reminder)
          },
          onShowTransactions = { accountId ->
            viewModel.setSelectedAccountFilter(accountId)
            viewModel.selectTab(FinanceTab.EXPENSES)
          },
          onDeleteCard = { account ->
            accountPendingDelete = account
          }
        )
        FinanceTab.SUBSCRIPTIONS -> SubscriptionsSection(
          subscriptions = subscriptions,
          onAddSubscriptionClick = { viewModel.setShowAddSubscription(true) },
          onMarkPaid = { subscriptionPendingPayment = it },
          onDeleteSubscription = { viewModel.deleteSubscription(it) }
        )
        FinanceTab.DEBTS -> {
          val debtViewModel: DebtTrackerViewModel = viewModel()
          DebtTrackerScreen(
            viewModel = debtViewModel,
            modifier = Modifier.fillMaxSize()
          )
        }
        FinanceTab.TRIPS -> TripsSection(
          trips = trips,
          expenses = expenses,
          onAddExpenseForTrip = { trip ->
            preselectedTripIdForExpense = trip.id
            viewModel.setShowAddExpense(true)
          },
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
    }
  }
}

@Composable
private fun AccountDetailsDialog(
  account: BankAccount,
  currentBalance: Double,
  billReminder: CreditCardBillReminder? = null,
  onDismiss: () -> Unit,
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
      FilledTonalButton(onClick = onShowTransactions) {
        Text("Transactions")
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

  val creditCardAccounts = remember(accountsWithBalances) {
    accountsWithBalances.filter { it.account.accountType.equals("Credit Card", ignoreCase = true) }
  }
  var selectedTransactionType by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedTransactionCategory by rememberSaveable { mutableStateOf<String?>(null) }
  var expensePendingDelete by remember { mutableStateOf<ExpenseRecord?>(null) }
  LaunchedEffect(selectedAccountFilterId) {
    selectedTransactionCategory = null
  }
  val filteredExpenses = remember(
    displayedExpenses,
    selectedTransactionType,
    selectedTransactionCategory
  ) {
    displayedExpenses.filter { expense ->
      val matchesType = when (selectedTransactionType) {
        "EXPENSE" -> expense.type == "EXPENSE"
        "INCOME" -> expense.type == "INCOME"
        "TRANSFER" -> expense.type == "TRANSFER_IN" || expense.type == "TRANSFER_OUT"
        else -> true
      }
      matchesType &&
        (selectedTransactionCategory == null ||
          expense.categoryName.equals(selectedTransactionCategory, ignoreCase = true))
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Total Net Balance Dashboard Banner
    item(key = "total_net_balance_card") {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
    item(key = "monthly_budget_card") {
      MonthlyBudgetCard(
        spent = currentMonthExpenses,
        limit = monthlyBudgetLimit,
        onEditBudget = onEditBudgetClick
      )
    }

    // Accounts & Balances Carousel
    item(key = "accounts_carousel_header") {
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

    item(key = "accounts_carousel_list") {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(accountsWithBalances, key = { it.account.id }) { accWithBal ->
          val isSelected = selectedAccountFilterId == accWithBal.account.id
          val isCreditCard = accWithBal.account.accountType.equals("Credit Card", ignoreCase = true)
          val billReminder = if (isCreditCard) {
            creditCardBillReminders.firstOrNull { it.accountId == accWithBal.account.id }
          } else {
            null
          }
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

                Row {
                  IconButton(
                    onClick = { onEditOpeningBalance(accWithBal.account) },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(
                      Icons.Default.Edit,
                      contentDescription = "Edit Opening Balance",
                      modifier = Modifier.size(14.dp),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  IconButton(
                    onClick = {
                      onSelectAccountFilter(if (isSelected) null else accWithBal.account.id)
                    },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(
                      Icons.AutoMirrored.Filled.ReceiptLong,
                      contentDescription = "Show account transactions",
                      modifier = Modifier.size(14.dp),
                      tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(6.dp))

              Text(
                text = accWithBal.account.bankName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
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

              if (isCreditCard) {
                val daysUntilDue = billReminder?.let {
                  (it.dueDateEpochDay - LocalDate.now().toEpochDay()).toInt()
                }
                val isBillPaid = billReminder?.paidDateEpochDay?.let { it >= 0L } == true
                val billStatusColor = when {
                  billReminder == null -> MaterialTheme.colorScheme.onSurfaceVariant
                  isBillPaid -> Color(0xFF059669)
                  daysUntilDue != null && daysUntilDue <= 3 -> Color(0xFFDC2626)
                  else -> MaterialTheme.colorScheme.primary
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = billStatusColor.copy(alpha = 0.12f),
                  border = BorderStroke(1.dp, billStatusColor.copy(alpha = 0.25f)),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                      onSetCreditCardBill(accWithBal.account.id)
                    }
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        "Credit Card Bill",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = billStatusColor,
                        maxLines = 1
                      )
                      Icon(
                        Icons.Default.EditCalendar,
                        contentDescription = "Edit bill",
                        modifier = Modifier.size(12.dp),
                        tint = billStatusColor
                      )
                    }
                    if (billReminder == null) {
                      Text(
                        "+ Set bill & due date",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = billStatusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    } else {
                      Text(
                        CurrencyFormatter.format(billReminder.amount),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = billStatusColor,
                        maxLines = 1
                      )
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(
                          when {
                            isBillPaid -> "Paid ✓"
                            daysUntilDue == 0 -> "Due today!"
                            daysUntilDue == 1 -> "Due tomorrow"
                            daysUntilDue != null && daysUntilDue < 0 -> "${-daysUntilDue}d overdue"
                            else -> "Due in ${daysUntilDue ?: 0} days"
                          },
                          fontSize = 10.sp,
                          fontWeight = FontWeight.SemiBold,
                          color = billStatusColor,
                          maxLines = 1
                        )
                        if (!isBillPaid) {
                          Text(
                            "Mark Paid",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                              onMarkCreditCardBillPaid(billReminder)
                            }
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

        // Add Account Card
        item(key = "add_account_card") {
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
      item(key = "credit_card_bills_tracker_widget") {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                val rem = creditCardBillReminders.firstOrNull { it.accountId == accWithBal.account.id }
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
    item(key = "quick_actions_bar") {
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

    val expenseTransactions = displayedExpenses.filter { it.type == "EXPENSE" }
    val totalSpentOnExpenses = expenseTransactions.sumOf { it.amount }
    if (totalSpentOnExpenses > 0) {
      item(key = "spending_analytics") {
        SpendingAnalyticsCard(
          expenses = expenseTransactions,
          categories = categories,
          totalSpent = totalSpentOnExpenses
        )
      }
    }

    // Category Chips Bar
    item(key = "categories_list") {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(categories, key = { it.id }) { cat ->
          val color = remember(cat.colorHex) {
            try {
              Color(android.graphics.Color.parseColor(cat.colorHex))
            } catch (e: Exception) {
              Color(0xFF6366F1)
            }
          }
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.padding(vertical = 2.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(color)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = cat.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
              )
              if (cat.isCustom) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  Icons.Default.Close,
                  contentDescription = "Remove custom category",
                  modifier = Modifier
                    .size(14.dp)
                    .clickable { onDeleteCategory(cat) },
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }

    // Recent Transactions Title and Filter indicator
    item(key = "expenses_title") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (selectedAccountFilterId != null) {
            val accName = accountsWithBalances.find { it.account.id == selectedAccountFilterId }?.account?.bankName ?: "Account"
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

    item(key = "transaction_filters") {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(
            listOf(
              null to "All",
              "EXPENSE" to "Expenses",
              "INCOME" to "Income",
              "TRANSFER" to "Transfers"
            ),
            key = { it.first ?: "all_types" }
          ) { (type, label) ->
            FilterChip(
              selected = selectedTransactionType == type,
              onClick = { selectedTransactionType = type },
              label = { Text(label, maxLines = 1, softWrap = false) }
            )
          }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item(key = "all_categories") {
            FilterChip(
              selected = selectedTransactionCategory == null,
              onClick = { selectedTransactionCategory = null },
              label = { Text("All Categories", maxLines = 1, softWrap = false) }
            )
          }
          items(transactionCategories, key = { it.lowercase() }) { category ->
            FilterChip(
              selected = selectedTransactionCategory.equals(category, ignoreCase = true),
              onClick = {
                selectedTransactionCategory =
                  if (selectedTransactionCategory.equals(category, ignoreCase = true)) null else category
              },
              label = { Text(category, maxLines = 1, softWrap = false) }
            )
          }
        }
      }
    }

    if (filteredExpenses.isEmpty()) {
      item(key = "empty_expenses") {
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
      items(filteredExpenses, key = { it.id }) { expense ->
        ExpenseItemCard(
          expense = expense,
          categories = categories,
          onDelete = { expensePendingDelete = expense },
          onEdit = { onEditExpense(expense) }
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
  categories: List<ExpenseCategory>,
  onDelete: () -> Unit,
  onEdit: () -> Unit
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
  val dateFormatted = remember(expense.dateTimestamp) {
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(expense.dateTimestamp))
  }
  val isTransfer = expense.type == "TRANSFER_IN" || expense.type == "TRANSFER_OUT"
  val isIncome = expense.type == "INCOME" || expense.type == "TRANSFER_IN"
  val amountColor = when {
    isTransfer -> MaterialTheme.colorScheme.primary
    isIncome -> Color(0xFF059669)
    else -> Color(0xFFDC2626)
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
            .clip(RoundedCornerShape(10.dp))
            .background(
              when {
                isTransfer -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                isIncome -> Color(0xFF10B981).copy(alpha = 0.15f)
                else -> catColor.copy(alpha = 0.15f)
              }
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = when {
              isTransfer -> Icons.Default.SwapHoriz
              isIncome -> Icons.Default.ArrowDownward
              else -> Icons.Default.Sell
            },
            contentDescription = null,
            tint = if (isTransfer) MaterialTheme.colorScheme.primary else if (isIncome) Color(0xFF059669) else catColor,
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
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = catColor.copy(alpha = 0.15f)
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
            if (expense.bankAccountName.isNotBlank()) {
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
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
          text = if (isIncome) {
            "+ ${CurrencyFormatter.formatWhole(expense.amount)}"
          } else {
            "- ${CurrencyFormatter.formatWhole(expense.amount)}"
          },
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = amountColor
        )
        Row {
          if (!isTransfer) {
            IconButton(
              onClick = onEdit,
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                Icons.Default.Edit,
                contentDescription = "Edit transaction",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
            }
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp)
          ) {
            Icon(
              Icons.Default.DeleteOutline,
              contentDescription = "Delete transaction",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditExpenseDialog(
  expense: ExpenseRecord,
  categories: List<ExpenseCategory>,
  onDismiss: () -> Unit,
  onConfirm: (title: String, amount: Double, category: String) -> Unit
) {
  var title by remember(expense.id) { mutableStateOf(expense.title) }
  var amountText by remember(expense.id) { mutableStateOf(expense.amount.toString()) }
  var selectedCategory by remember(expense.id) { mutableStateOf(expense.categoryName) }
  var categoryDropdownExpanded by remember { mutableStateOf(false) }
  val availableCategories = remember(categories, expense.categoryName) {
    (categories.map { it.name } + expense.categoryName).distinct()
  }
  val amount = amountText.toDoubleOrNull()
  val canSave = title.isNotBlank() && amount != null && amount.isFinite() && amount > 0.0

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Transaction") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text("Amount (₹)") },
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
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val parsedAmount = amount ?: return@TextButton
          onConfirm(title.trim(), parsedAmount, selectedCategory)
        },
        enabled = canSave
      ) {
        Text("Update")
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
  totalSpent: Double
) {
  val catSpendList = remember(expenses, categories, totalSpent) {
    val map = mutableMapOf<String, Double>()
    expenses.forEach { exp ->
      map[exp.categoryName] = (map[exp.categoryName] ?: 0.0) + exp.amount
    }
    map.entries.map { entry ->
      val matchedCat = categories.find { it.name.equals(entry.key, ignoreCase = true) }
      val catColor = try {
        matchedCat?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: Color(0xFF6366F1)
      } catch (e: Exception) {
        Color(0xFF6366F1)
      }
      val percent = if (totalSpent > 0) (entry.value / totalSpent * 100).toFloat() else 0f
      Triple(entry.key, entry.value, Pair(catColor, percent))
    }.sortedByDescending { it.second }
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
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

      // Donut Chart Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Compose Canvas Donut Chart
        Box(
          modifier = Modifier.size(125.dp),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            var startAngle = -90f
            catSpendList.forEach { (_, _, colorAndPercent) ->
              val sweepAngle = (colorAndPercent.second / 100f) * 360f
              if (sweepAngle > 0f) {
                drawArc(
                  color = colorAndPercent.first,
                  startAngle = startAngle,
                  sweepAngle = (sweepAngle - 2f).coerceAtLeast(1f),
                  useCenter = false,
                  style = Stroke(
                    width = 22.dp.toPx(),
                    cap = StrokeCap.Round
                  )
                )
                startAngle += sweepAngle
              }
            }
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "Top",
              fontSize = 9.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = catSpendList.firstOrNull()?.first ?: "",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }

        // Top Slices Quick Legend
        Column(
          modifier = Modifier.padding(start = 10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          catSpendList.take(3).forEach { (catName, _, colorAndPercent) ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(colorAndPercent.first)
              )
              Text(
                text = catName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 85.dp)
              )
              Text(
                text = "${colorAndPercent.second.toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

      // Category breakdown progress bars
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        catSpendList.forEach { (catName, amount, colorAndPercent) ->
          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                    .clip(CircleShape)
                    .background(colorAndPercent.first)
                )
                Text(
                  text = catName,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium
                )
              }
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = CurrencyFormatter.formatWhole(amount),
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "(${String.format(Locale.getDefault(), "%.1f", colorAndPercent.second)}%)",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            LinearProgressIndicator(
              progress = { colorAndPercent.second / 100f },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = colorAndPercent.first,
              trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
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
  onDeleteTrip: (TripRecord) -> Unit
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item(key = "trips_header") {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = Color(0xFF0D9488).copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Default.FlightTakeoff,
            contentDescription = null,
            tint = Color(0xFF0D9488),
            modifier = Modifier.size(32.dp)
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Trip & Vacation Budgets",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Set category caps, track live daily burn rate, and export PDF statements.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    if (trips.isEmpty()) {
      item(key = "empty_trips") {
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
      items(trips, key = { it.id }) { trip ->
        val tripExpenses = remember(expenses, trip.id) {
          expenses.filter { it.tripId == trip.id }
        }
        val spent = remember(tripExpenses) { tripExpenses.sumOf { it.amount } }
        TripCardItem(
          trip = trip,
          spent = spent,
          tripExpenses = tripExpenses,
          onAddExpense = { onAddExpenseForTrip(trip) },
          onSetCap = { onSetCategoryCap(trip) },
          onRemoveCap = { cat -> onRemoveCategoryCap(trip, cat) },
          onExportPdf = { onExportTripPdf(trip, tripExpenses) },
          onDelete = { onDeleteTrip(trip) }
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
  onDelete: () -> Unit
) {
  val progress = remember(trip.budget, spent) {
    if (trip.budget > 0) (spent / trip.budget).toFloat().coerceIn(0f, 1f) else 0f
  }
  val isOverBudget = spent > trip.budget
  val remaining = trip.budget - spent
  var showLedger by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
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

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            Icons.Default.DeleteOutline,
            contentDescription = "Delete trip",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
        }
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
                modifier = Modifier.fillMaxWidth(),
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
                Text(
                  text = CurrencyFormatter.formatWhole(exp.amount),
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  color = Color(0xFFDC2626)
                )
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
  var selectedAccountId by remember { mutableStateOf(bankAccounts.firstOrNull()?.id) }
  var notes by remember { mutableStateOf("") }
  var selectedTripId by remember { mutableStateOf<Long?>(initialTripId) }
  var categoryDropdownExpanded by remember { mutableStateOf(false) }
  var smsParseMessage by remember { mutableStateOf<String?>(null) }
  val clipboard = LocalClipboardManager.current

  val selectedAccountName = remember(selectedAccountId, bankAccounts) {
    bankAccounts.find { it.id == selectedAccountId }?.bankName ?: ""
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Log New Expense") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
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
                  if (bankAccounts.any { it.id == matchedId }) selectedAccountId = matchedId
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
          label = { Text("Expense Title") },
          placeholder = { Text("e.g. Grocery store, Uber, Rent") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = amountStr,
          onValueChange = { amountStr = it },
          label = { Text("Amount (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        // Select Paid From Bank Account
        if (bankAccounts.isNotEmpty()) {
          Column {
            Text("Deduct from Account:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              items(bankAccounts) { acc ->
                FilterChip(
                  selected = selectedAccountId == acc.id,
                  onClick = { selectedAccountId = acc.id },
                  label = { Text(acc.bankName, fontSize = 11.sp) }
                )
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
            item {
              FilterChip(
                selected = selectedTripId == null,
                onClick = { selectedTripId = null },
                label = { Text("None") }
              )
            }
            items(trips) { trip ->
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
          onConfirm(title, amt, selectedCategory, selectedAccountId, selectedAccountName, notes, selectedTripId)
        },
        enabled = title.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0
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
              items(bankAccounts) { acc ->
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
            items(incomeSources) { src ->
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

  val types = listOf("Savings", "Current", "Cash Wallet", "Checking", "Credit Card")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Bank / Cash Account") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        OutlinedTextField(
          value = bankName,
          onValueChange = { bankName = it },
          label = { Text("Account / Bank Name *") },
          placeholder = { Text("e.g. HDFC Bank, SBI, Cash Wallet") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = openingBalStr,
          onValueChange = { openingBalStr = it },
          label = { Text("Opening Balance (₹) *") },
          placeholder = { Text("Starting balance e.g. 50000") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Text("Account Type:", style = MaterialTheme.typography.bodySmall)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          types.forEach { type ->
            FilterChip(
              selected = accountType == type,
              onClick = { accountType = type },
              label = { Text(type, fontSize = 12.sp) }
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
        enabled = bankName.isNotBlank()
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
            startDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
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
            endDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
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
  val haptic = LocalHapticFeedback.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 36.dp),
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
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.CloudSync,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Data Backup & Restore",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Export and import your custom configurations via JSON",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      // Stats Chip / Banner
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
              Text(categoriesCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
              Text("Categories", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
              Text(banksCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E3A8A))
              Text("Bank Accounts", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
              Text(tripsCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0D9488))
              Text("Trips", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
              Text(expensesCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDC2626))
              Text("Expenses", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
              Text(loansCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF7C3AED))
              Text("Loans", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
              Text(debtsCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFEA580C))
              Text("Debts", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
              Text(subscriptionsCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0891B2))
              Text("Subscriptions", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
              Text(cardRemindersCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDB2777))
              Text("Card Bills", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }

      // EXPORT SECTION
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Data (Backup)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
          Text(
            text = "Generate an offline JSON file containing all your custom categories, bank details, and trips.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Button(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              onExportToFile()
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("export_to_file_btn")
          ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save JSON File to Device")
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                onShareBackup()
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("share_backup_btn")
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Share File", fontSize = 12.sp)
            }

            OutlinedButton(
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCopyJson()
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("copy_json_btn")
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Copy JSON", fontSize = 12.sp)
            }
          }
        }
      }

      // IMPORT SECTION
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Data (Restore)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }

          Text(
            text = "Select a JSON backup file to load categories, bank details, and trips back into OmniCalc.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          // Import Strategy Choice
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Import Strategy:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              FilterChip(
                selected = !replaceExisting,
                onClick = { onReplaceExistingChange(false) },
                label = { Text("Merge (Keep current)") },
                modifier = Modifier.weight(1f)
              )
              FilterChip(
                selected = replaceExisting,
                onClick = { onReplaceExistingChange(true) },
                label = { Text("Replace (Overwrite)") },
                modifier = Modifier.weight(1f)
              )
            }
          }

          Button(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              onImportFromFile()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("import_from_file_btn")
          ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select JSON Backup File")
          }

          OutlinedButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              onPasteJsonClick()
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("paste_json_btn")
          ) {
            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Paste JSON Text Directly")
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
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item(key = "subscriptions_header") {
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
      item(key = "empty_subscriptions") {
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
      items(subscriptions, key = { it.id }) { sub ->
        SubscriptionItemCard(
          sub = sub,
          today = today,
          onMarkPaid = { onMarkPaid(sub) },
          onDelete = { onDeleteSubscription(sub) }
        )
      }
    }
  }
}

@Composable
private fun SubscriptionItemCard(
  sub: SubscriptionRecord,
  today: LocalDate,
  onMarkPaid: () -> Unit,
  onDelete: () -> Unit
) {
  val daysLeft = ChronoUnit.DAYS.between(
    today,
    SubscriptionDueDateCalculator.nextDueDate(sub, today)
  ).toInt()
  val statusLabel = when {
    daysLeft == 0 -> stringResource(com.example.R.string.subscription_due_today)
    daysLeft == 1 -> stringResource(com.example.R.string.subscription_due_in_one_day)
    else -> stringResource(com.example.R.string.subscription_due_in_days, daysLeft)
  }

  val isUrgent = daysLeft in 0..3

  val isPaidThisCycle = SubscriptionDueDateCalculator.isPaidInCurrentCycle(sub, today)

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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

          Spacer(modifier = Modifier.width(4.dp))
          IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// TRIP SPEEDOMETER & DAILY SPENDING RATE TRACKER
// -------------------------------------------------------------
@Composable
private fun TripDailySpendingSpeedometer(
  trip: TripRecord,
  spent: Double
) {
  val totalDays = remember(trip.startDate, trip.endDate) {
    calculateTripDays(trip.startDate, trip.endDate)
  }
  val dailyBudget = remember(trip.budget, totalDays) {
    if (totalDays > 0) trip.budget / totalDays else trip.budget / 7.0
  }
  val elapsedDays = remember(trip.startDate) {
    calculateElapsedDays(trip.startDate).coerceAtLeast(1)
  }
  val dailyRate = remember(spent, elapsedDays) {
    spent / elapsedDays
  }

  val burnRatio = if (dailyBudget > 0) (dailyRate / dailyBudget).toFloat() else 0f
  val isExceeding = burnRatio > 1.05f

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "Daily Burn Rate Tracker",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isExceeding) Color(0xFFFEE2E2) else Color(0xFFCCFBF1)
          ) {
            Text(
              text = if (isExceeding) "Exceeding Daily Rate" else "On Track",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (isExceeding) Color(0xFFB91C1C) else Color(0xFF0F766E),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "You are spending ${CurrencyFormatter.format(dailyRate)}/day",
          fontSize = 13.sp,
          fontWeight = FontWeight.ExtraBold,
          color = if (isExceeding) Color(0xFFEF4444) else Color(0xFF0D9488)
        )
        Text(
          text = "(Target Budget: ${CurrencyFormatter.format(dailyBudget)}/day • Day $elapsedDays of $totalDays)",
          fontSize = 10.5.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Visual Speedometer Gauge Canvas
      Canvas(modifier = Modifier.size(76.dp, 44.dp)) {
        val strokeW = 6.dp.toPx()
        val arcSize = Size(size.width, size.height * 1.8f)
        val topLeft = Offset(0f, 0f)

        // Background arc
        drawArc(
          color = Color.LightGray.copy(alpha = 0.35f),
          startAngle = 180f,
          sweepAngle = 180f,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Burn Rate Arc
        val activeSweep = (burnRatio.coerceIn(0f, 1.5f) / 1.5f * 180f)
        val arcColor = when {
          burnRatio > 1.05f -> Color(0xFFEF4444)
          burnRatio > 0.8f -> Color(0xFFF59E0B)
          else -> Color(0xFF10B981)
        }
        drawArc(
          color = arcColor,
          startAngle = 180f,
          sweepAngle = activeSweep,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Needle pointer
        val needleAngle = (180f + activeSweep) * (PI / 180f).toFloat()
        val centerX = size.width / 2f
        val centerY = size.height * 0.9f
        val needleLen = size.width * 0.38f
        val endX = centerX + needleLen * cos(needleAngle)
        val endY = centerY + needleLen * sin(needleAngle)

        drawLine(
          color = Color(0xFF1E293B),
          start = Offset(centerX, centerY),
          end = Offset(endX, endY),
          strokeWidth = 3.dp.toPx(),
          cap = StrokeCap.Round
        )
        drawCircle(color = Color(0xFF1E293B), radius = 3.5.dp.toPx(), center = Offset(centerX, centerY))
      }
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
        val catSpent = remember(tripExpenses, catName) {
          tripExpenses.filter { it.categoryName.equals(catName, ignoreCase = true) }.sumOf { it.amount }
        }
        val ratio = if (capAmount > 0) (catSpent / capAmount).toFloat() else 0f
        val progress = ratio.coerceIn(0f, 1f)
        val isOver = catSpent > capAmount

        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
          ),
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
            items(bankAccounts) { acc ->
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
          items(categories) { cat ->
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
  onSetBillReminder: (Long) -> Unit,
  onMarkBillPaid: (CreditCardBillReminder) -> Unit,
  onShowTransactions: (Long) -> Unit,
  onDeleteCard: (BankAccount) -> Unit,
  modifier: Modifier = Modifier
) {
  val totalOutstanding = cardAccounts.sumOf { kotlin.math.abs(it.currentBalance) }
  val todayEpoch = LocalDate.now().toEpochDay()
  val unpaidReminders = reminders.filter { it.paidDateEpochDay < 0L }
  val totalUnpaidBills = unpaidReminders.sumOf { it.amount }
  val nextDueReminder = unpaidReminders.minByOrNull { it.dueDateEpochDay }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
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
      item {
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
      items(cardAccounts, key = { it.account.id }) { accWithBal ->
        val reminder = reminders.firstOrNull { it.accountId == accWithBal.account.id }
        val outstanding = kotlin.math.abs(accWithBal.currentBalance)
        val isPaid = reminder?.paidDateEpochDay?.let { it >= 0L } == true

        Card(
          modifier = Modifier.fillMaxWidth().testTag("credit_card_${accWithBal.account.id}"),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                  IconButton(
                    onClick = { onDeleteCard(accWithBal.account) },
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(
                      Icons.Default.DeleteOutline,
                      contentDescription = "Delete Card",
                      tint = Color.White.copy(alpha = 0.7f),
                      modifier = Modifier.size(18.dp)
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
                        "Due Date: ${dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))}",
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
