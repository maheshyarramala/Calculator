package com.example.ui.screens.debt

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AccountWithBalance
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.DebtRecord
import com.example.ui.components.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtTrackerScreen(
  viewModel: DebtTrackerViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val userMessage by viewModel.userMessage.collectAsState()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(userMessage) {
    userMessage?.let {
      snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
      viewModel.clearUserMessage()
    }
  }

  val userUpiId by viewModel.userUpiId.collectAsState()
  var showUpiDialog by remember { mutableStateOf(false) }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { viewModel.setShowAddDebtDialog(true) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        modifier = Modifier.testTag("btn_add_debt_record")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Lend/Borrow Transaction")
      }
    },
    modifier = modifier.fillMaxSize()
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // 1. Dual Summary Card: Receivable (Green) vs Payable (Red)
      item {
        DebtSummaryOverviewCard(
          totalLent = state.totalLentReceivable,
          totalBorrowed = state.totalBorrowedPayable,
          netBalance = state.netBalance
        )
      }

      // 2. Tab Navigation: "People Who Owe Me" (Lent) vs "People I Owe" (Borrowed) vs "Settled"
      item {
        SingleChoiceSegmentedTabs(
          selectedTab = state.selectedTab,
          onSelectTab = { viewModel.selectTab(it) }
        )
      }

      // 2b. 1-Tap UPI Payment Links Banner for "People Who Owe Me"
      if (state.selectedTab == DebtTab.LENT) {
        item {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showUpiDialog = true }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  Icons.Default.QrCode,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    "1-Tap UPI Repayment Links",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  )
                  Text(
                    if (userUpiId.isNotBlank()) "Your UPI: $userUpiId (Included in WhatsApp reminders)"
                    else "Tap to add your UPI ID (GPay/PhonePe/Paytm)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              FilledTonalIconButton(
                onClick = { showUpiDialog = true },
                modifier = Modifier.size(36.dp)
              ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit UPI ID", modifier = Modifier.size(16.dp))
              }
            }
          }
        }
      }

      // 3. Search Bar
      item {
        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = { viewModel.setSearchQuery(it) },
          placeholder = { Text("Search by person or note...", fontSize = 14.sp) },
          leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          },
          trailingIcon = {
            if (state.searchQuery.isNotBlank()) {
              IconButton(onClick = { viewModel.setSearchQuery("") }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear search")
              }
            }
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }

      // 4. Empty State or Debt Cards List
      if (state.allDebts.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                when (state.selectedTab) {
                  DebtTab.LENT -> Icons.Default.CallMade
                  DebtTab.BORROWED -> Icons.Default.CallReceived
                  DebtTab.SETTLED -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = when (state.selectedTab) {
                  DebtTab.LENT -> "No Pending Receivables"
                  DebtTab.BORROWED -> "No Pending Payables"
                  DebtTab.SETTLED -> "No Settled Records Yet"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = when (state.selectedTab) {
                  DebtTab.LENT -> "People who borrowed money from you will appear here."
                  DebtTab.BORROWED -> "Money you borrowed from friends or colleagues will appear here."
                  DebtTab.SETTLED -> "Transactions marked as settled will be archived here."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              if (state.selectedTab != DebtTab.SETTLED) {
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(onClick = { viewModel.setShowAddDebtDialog(true) }) {
                  Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Add Transaction")
                }
              }
            }
          }
        }
      } else {
        items(state.allDebts, key = { it.id }) { debt ->
          DebtItemCard(
            debt = debt,
            onSendReminder = { viewModel.sendReminder(context, debt) },
            onMarkSettled = { viewModel.setDebtToSettle(debt) },
            onDelete = { viewModel.deleteDebt(debt) }
          )
        }
      }
    }
  }

  // Add Debt Dialog
  if (state.showAddDebtDialog) {
    AddDebtDialog(
      bankAccounts = state.bankAccounts,
      initialType = if (state.selectedTab == DebtTab.BORROWED) "BORROWED" else "LENT",
      onDismiss = { viewModel.setShowAddDebtDialog(false) },
      onConfirm = { name, amount, type, dueDate, notes, bankId, bankName, adjustBalance ->
        viewModel.addDebt(
          personName = name,
          amount = amount,
          type = type,
          dueDateTimestamp = dueDate,
          notes = notes,
          bankAccountId = bankId,
          bankAccountName = bankName,
          adjustBankBalanceNow = adjustBalance
        )
      }
    )
  }

  // Settle Debt Dialog
  state.debtToSettle?.let { debt ->
    SettleDebtDialog(
      debt = debt,
      bankAccounts = state.bankAccounts,
      onDismiss = { viewModel.setDebtToSettle(null) },
      onConfirm = { adjustBalance, bankId, bankName ->
        viewModel.markAsSettled(debt, adjustBalance, bankId, bankName)
      }
    )
  }

  // Configure UPI ID Dialog
  if (showUpiDialog) {
    ConfigureUpiIdDialog(
      currentUpiId = userUpiId,
      onDismiss = { showUpiDialog = false },
      onSave = { newUpi ->
        viewModel.setUserUpiId(newUpi)
        showUpiDialog = false
      }
    )
  }
}

@Composable
private fun ConfigureUpiIdDialog(
  currentUpiId: String,
  onDismiss: () -> Unit,
  onSave: (String) -> Unit
) {
  var upiInput by remember { mutableStateOf(currentUpiId) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Your UPI Payment ID", fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          "Enter your UPI ID (VPA) so that when you send WhatsApp or SMS reminders to people who owe you money, OmniCalc automatically attaches a 1-tap payment link (upi://pay?pa=...).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
          value = upiInput,
          onValueChange = { upiInput = it },
          label = { Text("UPI ID / VPA") },
          placeholder = { Text("e.g. yourname@oksbi, 9876543210@paytm") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp)
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(upiInput) },
        shape = RoundedCornerShape(10.dp)
      ) {
        Text("Save UPI ID")
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
// DUAL OVERVIEW SUMMARY CARD
// -------------------------------------------------------------
@Composable
private fun DebtSummaryOverviewCard(
  totalLent: Double,
  totalBorrowed: Double,
  netBalance: Double
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Personal Lend & Borrow",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          modifier = Modifier.weight(1f, fill = false),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        // Net balance tag
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (netBalance >= 0) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
        ) {
          Text(
            text = "Net: ${if (netBalance >= 0) "+" else ""}${CurrencyFormatter.format(netBalance)}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (netBalance >= 0) Color(0xFF059669) else Color(0xFFDC2626),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // People Who Owe Me (LENT - GREEN)
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFF10B981).copy(alpha = 0.12f),
          border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(22.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF059669).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  Icons.Default.ArrowDownward,
                  contentDescription = null,
                  tint = Color(0xFF059669),
                  modifier = Modifier.size(13.dp)
                )
              }
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "People Owe Me",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF059669),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = CurrencyFormatter.format(totalLent),
              fontSize = 16.sp,
              fontWeight = FontWeight.Black,
              color = Color(0xFF059669),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "Total Receivable",
              fontSize = 9.5.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // People I Owe (BORROWED - RED)
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFFEF4444).copy(alpha = 0.12f),
          border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(22.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFDC2626).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  Icons.Default.ArrowUpward,
                  contentDescription = null,
                  tint = Color(0xFFDC2626),
                  modifier = Modifier.size(13.dp)
                )
              }
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "People I Owe",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFDC2626),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = CurrencyFormatter.format(totalBorrowed),
              fontSize = 16.sp,
              fontWeight = FontWeight.Black,
              color = Color(0xFFDC2626),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "Total Payable",
              fontSize = 9.5.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// SEGMENTED TAB SELECTOR
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleChoiceSegmentedTabs(
  selectedTab: DebtTab,
  onSelectTab: (DebtTab) -> Unit
) {
  PrimaryTabRow(
    selectedTabIndex = selectedTab.ordinal,
    containerColor = Color.Transparent,
    modifier = Modifier.fillMaxWidth()
  ) {
    DebtTab.values().forEach { tab ->
      Tab(
        selected = selectedTab == tab,
        onClick = { onSelectTab(tab) },
        text = {
          Text(
            text = when (tab) {
              DebtTab.LENT -> "Who Owes Me"
              DebtTab.BORROWED -> "Who I Owe"
              DebtTab.SETTLED -> "Settled"
            },
            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            maxLines = 1
          )
        }
      )
    }
  }
}

// -------------------------------------------------------------
// DEBT ITEM CARD
// -------------------------------------------------------------
@Composable
private fun DebtItemCard(
  debt: DebtRecord,
  onSendReminder: () -> Unit,
  onMarkSettled: () -> Unit,
  onDelete: () -> Unit
) {
  val isLent = debt.type == "LENT"
  val primaryColor = if (isLent) Color(0xFF059669) else Color(0xFFDC2626)
  val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
  val dateCreated = dateFormat.format(Date(debt.dateTimestamp))
  val dueDateStr = debt.dueDateTimestamp?.let { dateFormat.format(Date(it)) }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("debt_card_${debt.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Person Avatar Initial
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(primaryColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = debt.personName.take(1).uppercase(),
              fontSize = 18.sp,
              fontWeight = FontWeight.Black,
              color = primaryColor
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = debt.personName,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "$dateCreated • ${if (isLent) "Lent" else "Borrowed"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              if (debt.isOverdue) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  shape = RoundedCornerShape(4.dp),
                  color = MaterialTheme.colorScheme.errorContainer
                ) {
                  Text(
                    text = "OVERDUE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                  )
                }
              }
            }
          }
        }

        // Amount Display
        Text(
          text = "${if (isLent) "+" else "-"}${CurrencyFormatter.formatWhole(debt.amount)}",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
          color = primaryColor
        )
      }

      // Due date & notes
      if (dueDateStr != null || debt.notes.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (dueDateStr != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Due: $dueDateStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          if (debt.bankAccountName.isNotBlank()) {
            Text(debt.bankAccountName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        if (debt.notes.isNotBlank()) {
          Text("“${debt.notes}”", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      Spacer(modifier = Modifier.height(10.dp))

      // Action Row: "Send Reminder" & "Mark as Settled"
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onDelete) {
          Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!debt.isSettled) {
            // Send Reminder via WhatsApp/SMS
            OutlinedButton(
              onClick = onSendReminder,
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Reminder", fontSize = 12.sp, maxLines = 1, softWrap = false)
            }

            // Settle button
            FilledTonalButton(
              onClick = onMarkSettled,
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Settle", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
          } else {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFF10B981).copy(alpha = 0.15f)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Settled", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
              }
            }
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// ADD DEBT DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDebtDialog(
  bankAccounts: List<AccountWithBalance>,
  initialType: String,
  onDismiss: () -> Unit,
  onConfirm: (
    name: String,
    amount: Double,
    type: String,
    dueDate: Long?,
    notes: String,
    bankId: Long?,
    bankName: String,
    adjustBalance: Boolean
  ) -> Unit
) {
  val context = LocalContext.current
  var personName by rememberSaveable { mutableStateOf("") }
  var amountText by rememberSaveable { mutableStateOf("") }
  var debtType by rememberSaveable { mutableStateOf(initialType) }
  var dueDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
  var notes by rememberSaveable { mutableStateOf("") }
  var adjustBalance by rememberSaveable { mutableStateOf(false) }
  var selectedBankId by rememberSaveable { mutableStateOf<Long?>(bankAccounts.firstOrNull()?.account?.id) }

  val amountVal = amountText.toDoubleOrNull() ?: 0.0
  val selectedAccount = bankAccounts.find { it.account.id == selectedBankId }

  val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Lend / Borrow Transaction", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Type Selector Tabs: "I Gave Money (Lent)" vs "I Took Money (Borrowed)"
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilterChip(
            selected = debtType == "LENT",
            onClick = { debtType = "LENT" },
            label = { Text("I Gave (Lent)", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.CallMade, contentDescription = null, modifier = Modifier.size(14.dp)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
          )
          FilterChip(
            selected = debtType == "BORROWED",
            onClick = { debtType = "BORROWED" },
            label = { Text("I Took (Borrowed)", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.CallReceived, contentDescription = null, modifier = Modifier.size(14.dp)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
          )
        }

        OutlinedTextField(
          value = personName,
          onValueChange = { personName = it },
          label = { Text("Person Name (e.g. Rahul, Priya)") },
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text("Amount (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth()
        )

        // Due Date Picker Button
        OutlinedButton(
          onClick = {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
              context,
              { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply {
                  set(year, month, dayOfMonth, 23, 59, 59)
                }
                dueDateMillis = chosen.timeInMillis
              },
              calendar.get(Calendar.YEAR),
              calendar.get(Calendar.MONTH),
              calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
          },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp)
        ) {
          Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = dueDateMillis?.let { "Due: ${dateFormat.format(Date(it))}" } ?: "Set Optional Due Date",
            fontSize = 13.sp
          )
        }

        // Adjust Bank Account Balance Toggle
        if (bankAccounts.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = adjustBalance,
              onCheckedChange = { adjustBalance = it }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Record in Finance Hub & Adjust Bank Balance",
              fontSize = 12.sp
            )
          }

          if (adjustBalance) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
              expanded = expanded,
              onExpandedChange = { expanded = it }
            ) {
              OutlinedTextField(
                value = selectedAccount?.let { "${it.account.bankName} (${CurrencyFormatter.format(it.currentBalance)})" } ?: "Select Account",
                onValueChange = {},
                readOnly = true,
                label = { Text("Bank / Wallet Account") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                  .fillMaxWidth()
                  .menuAnchor()
              )
              ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
              ) {
                bankAccounts.forEach { acc ->
                  DropdownMenuItem(
                    text = { Text("${acc.account.bankName} (${CurrencyFormatter.format(acc.currentBalance)})") },
                    onClick = {
                      selectedBankId = acc.account.id
                      expanded = false
                    }
                  )
                }
              }
            }
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Reason, split item, etc.)") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (personName.isNotBlank() && amountVal > 0) {
            onConfirm(
              personName,
              amountVal,
              debtType,
              dueDateMillis,
              notes,
              if (adjustBalance) selectedBankId else null,
              if (adjustBalance) selectedAccount?.account?.bankName ?: "" else "",
              adjustBalance
            )
          }
        },
        enabled = personName.isNotBlank() && amountVal > 0
      ) {
        Text("Save Record")
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
// SETTLE CONFIRMATION DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleDebtDialog(
  debt: DebtRecord,
  bankAccounts: List<AccountWithBalance>,
  onDismiss: () -> Unit,
  onConfirm: (adjustBalance: Boolean, bankId: Long?, bankName: String) -> Unit
) {
  var adjustBalance by rememberSaveable { mutableStateOf(false) }
  // Auto-select account with sufficient funds if available
  val preferredAccount = remember(bankAccounts, debt) {
    if (debt.type == "BORROWED") {
      bankAccounts.find { it.currentBalance >= debt.amount } ?: bankAccounts.firstOrNull()
    } else {
      bankAccounts.firstOrNull()
    }
  }
  var selectedBankId by rememberSaveable { mutableStateOf<Long?>(preferredAccount?.account?.id) }

  val selectedAccount = bankAccounts.find { it.account.id == selectedBankId }
  val totalLiquidFunds = remember(bankAccounts) { bankAccounts.sumOf { it.currentBalance } }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Settle Debt?") },
    text = {
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = if (debt.type == "LENT") {
            "Did ${debt.personName} repay ${CurrencyFormatter.formatWhole(debt.amount)} to you?"
          } else {
            "Did you repay ${CurrencyFormatter.formatWhole(debt.amount)} to ${debt.personName}?"
          },
          fontSize = 14.sp
        )

        if (bankAccounts.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = adjustBalance,
              onCheckedChange = { adjustBalance = it }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (debt.type == "LENT") "Deposit into my bank account" else "Deduct from my bank account",
              fontSize = 12.sp
            )
          }

          if (adjustBalance) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
              expanded = expanded,
              onExpandedChange = { expanded = it }
            ) {
              OutlinedTextField(
                value = selectedAccount?.let { "${it.account.bankName} (${CurrencyFormatter.format(it.currentBalance)})" } ?: "Select Account",
                onValueChange = {},
                readOnly = true,
                label = { Text("Account") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                  .fillMaxWidth()
                  .menuAnchor()
              )
              ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
              ) {
                bankAccounts.forEach { acc ->
                  DropdownMenuItem(
                    text = { Text("${acc.account.bankName} (${CurrencyFormatter.format(acc.currentBalance)})") },
                    onClick = {
                      selectedBankId = acc.account.id
                      expanded = false
                    }
                  )
                }
              }
            }

            // Liquid fund notice if selected account balance is low
            if (debt.type == "BORROWED" && selectedAccount != null && selectedAccount.currentBalance < debt.amount) {
              Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                  containerColor = if (totalLiquidFunds >= debt.amount) Color(0xFF3B82F6).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = if (totalLiquidFunds >= debt.amount) Icons.Default.Info else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (totalLiquidFunds >= debt.amount) Color(0xFF2563EB) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (totalLiquidFunds >= debt.amount) {
                      "Selected account balance (${CurrencyFormatter.format(selectedAccount.currentBalance)}) is below ${CurrencyFormatter.formatWhole(debt.amount)}. Available liquid funds (${CurrencyFormatter.format(totalLiquidFunds)}) will be safely utilized to prevent negative account balance."
                    } else {
                      "Warning: Total liquid funds (${CurrencyFormatter.format(totalLiquidFunds)}) are lower than settlement amount ${CurrencyFormatter.formatWhole(debt.amount)}."
                    },
                    fontSize = 11.5.sp,
                    color = if (totalLiquidFunds >= debt.amount) Color(0xFF1E40AF) else MaterialTheme.colorScheme.error
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(
            adjustBalance,
            if (adjustBalance) selectedBankId else null,
            if (adjustBalance) selectedAccount?.account?.bankName ?: "" else ""
          )
        }
      ) {
        Text("Mark as Settled")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
