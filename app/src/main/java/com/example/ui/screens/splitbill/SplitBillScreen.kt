package com.example.ui.screens.splitbill
import com.example.ui.theme.performConfiguredHapticFeedback

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.splitbill.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitBillScreen(
  viewModel: SplitBillViewModel,
  financeViewModel: com.example.ui.screens.finance.FinanceHubViewModel? = null,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val clipboard = LocalClipboardManager.current
  val isRestored by viewModel.isRestored.collectAsState()
  val savedBills by viewModel.savedBills.collectAsState()
  val billCreatedAt = savedBills.firstOrNull { it.id == state.billId }?.createdAt ?: System.currentTimeMillis()
  var showBillHistory by remember { mutableStateOf(false) }
  var showNewBillConfirm by remember { mutableStateOf(false) }
  var showRenameBill by remember { mutableStateOf(false) }
  var billTitleDraft by remember(state.billTitle) { mutableStateOf(state.billTitle) }
  LaunchedEffect(state, isRestored) { if (isRestored) viewModel.persistCurrent() }

  if (showRenameBill) {
    AlertDialog(
      onDismissRequest = { showRenameBill = false },
      title = { Text("Rename bill") },
      text = {
        OutlinedTextField(
          value = billTitleDraft,
          onValueChange = { billTitleDraft = it.take(80) },
          label = { Text("Bill name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(onClick = {
          if (billTitleDraft.isNotBlank()) viewModel.setBillTitle(billTitleDraft.trim())
          showRenameBill = false
        }, enabled = billTitleDraft.isNotBlank()) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { showRenameBill = false }) { Text("Cancel") } }
    )
  }

  if (showNewBillConfirm) {
    AlertDialog(
      onDismissRequest = { showNewBillConfirm = false },
      title = { Text("Start a new bill?") },
      text = { Text("Your current bill will remain saved in local history. A new blank bill will be created.") },
      confirmButton = { Button(onClick = { viewModel.startNewBill(); showNewBillConfirm = false }) { Text("New bill") } },
      dismissButton = { TextButton(onClick = { showNewBillConfirm = false }) { Text("Cancel") } }
    )
  }

  if (showBillHistory) {
    AlertDialog(
      onDismissRequest = { showBillHistory = false },
      title = { Text("Saved bills", fontWeight = FontWeight.Bold) },
      text = {
        if (savedBills.isEmpty()) Text("No saved bills yet. Your bills are saved automatically on this device.")
        else LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(savedBills, key = { it.id }) { bill ->
            Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.openSavedBill(bill.id); showBillHistory = false }) {
              Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text(bill.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(bill.currencyCode + " · " + java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(java.util.Date(bill.updatedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.deleteSavedBill(bill.id) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete saved bill", tint = MaterialTheme.colorScheme.error) }
              }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { showBillHistory = false }) { Text("Close") } },
      dismissButton = { TextButton(onClick = { viewModel.startNewBill(); showBillHistory = false }) { Text("New bill") } }
    )
  }
  val financeAccounts by financeViewModel?.bankAccounts?.collectAsState()
    ?: remember { mutableStateOf(emptyList<com.example.data.local.entity.BankAccount>()) }

  // Calculate dynamic breakdowns and debt settlements using DebtSimplifier
  val breakdowns = remember(
    state.participants,
    state.items,
    state.splitMode,
    state.manualBillTotal
  ) {
    DebtSimplifier.calculateBreakdowns(
      mode = state.splitMode,
      participants = state.participants,
      items = state.items,
      tax = 0.0,
      tip = 0.0,
      discount = 0.0,
      manualBillTotal = state.manualBillTotal
    )
  }

  val totalBill = remember(breakdowns) {
    breakdowns.sumOf { it.totalToPay }
  }

  val totalPaid = remember(breakdowns) {
    breakdowns.sumOf { it.paidAmount }
  }

  val simplifiedTransfers = remember(breakdowns, state.simplifyDebtsEnabled) {
    if (state.simplifyDebtsEnabled) {
      DebtSimplifier.simplifyDebts(breakdowns)
    } else {
      DebtSimplifier.directTransfers(breakdowns)
    }
  }

  // Modals
  if (state.showOcrDialog) {
    OcrScannerBottomSheet(
      onDismiss = { viewModel.setShowOcrDialog(false) },
      onApplyReceipt = { viewModel.applyParsedReceipt(it) },
      onLoadDemo = { text, title -> viewModel.loadDemo(text, title) }
    )
  }

  if (state.showAddParticipantDialog) {
    AddParticipantDialog(
      onDismiss = { viewModel.setShowAddParticipantDialog(false) },
      onConfirm = { name, upi, paypal, paid ->
        viewModel.addParticipant(name, upi, paypal, paid)
        viewModel.setShowAddParticipantDialog(false)
      }
    )
  }

  if (state.showAddItemDialog) {
    AddReceiptItemDialog(
      participants = state.participants,
      currencySymbol = state.currencySymbol,
      onDismiss = { viewModel.setShowAddItemDialog(false) },
      onConfirm = { title, price, qty, claimedIds, paidById ->
        viewModel.addItem(title, price, qty, claimedIds, paidById)
        viewModel.setShowAddItemDialog(false)
      }
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    // Top Bar: Bill Title, Currency & OCR Action
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        Text(
          text = state.billTitle,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.clickable { billTitleDraft = state.billTitle; showRenameBill = true }
        )
        Text(
          text = "${state.participants.size} participants · ${java.text.SimpleDateFormat("dd MMM", Locale.getDefault()).format(java.util.Date(billCreatedAt))}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { showBillHistory = true }) {
          Icon(Icons.Default.History, contentDescription = "Saved bills")
        }
        IconButton(onClick = { showNewBillConfirm = true }) {
          Icon(Icons.Default.Add, contentDescription = "New bill")
        }
        // AI Receipt Scan / OCR Button
        FilledTonalButton(
          onClick = {
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.setShowOcrDialog(true)
          },
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier.testTag("scan_receipt_btn")
        ) {
          Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Scan Receipt", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Grand Total Overview Card (High Contrast & Clear Visibility)
    Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
      ),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Total Group Bill",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", totalBill)}",
              style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = MaterialTheme.colorScheme.primary
            )
          }

          // Currency Switcher Dropdown
          var showCurrencyMenu by remember { mutableStateOf(false) }
          Box {
            FilledTonalButton(
              onClick = { showCurrencyMenu = true },
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text("${state.currencyCode} (${state.currencySymbol})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
              Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = showCurrencyMenu, onDismissRequest = { showCurrencyMenu = false }) {
              listOf(
                "INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£",
                "AED" to "د.إ", "CAD" to "$", "AUD" to "$", "JPY" to "¥"
              ).forEach { (code, sym) ->
                DropdownMenuItem(
                  text = { Text("$code ($sym)", fontWeight = FontWeight.Medium) },
                  onClick = {
                    viewModel.setCurrency(code, sym)
                    showCurrencyMenu = false
                  }
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val diff = totalPaid - totalBill
        Text(
          text = when {
            totalBill <= 0.0 -> "No Items"
            Math.abs(diff) < 0.005 -> "Fully Paid ✓"
            diff < 0.0 -> "Owed: ${state.currencySymbol}${String.format(Locale.US, "%,.2f", -diff)}"
            else -> "Overpaid: ${state.currencySymbol}${String.format(Locale.US, "%,.2f", diff)}"
          },
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = when {
            totalBill <= 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
            Math.abs(diff) < 0.005 -> Color(0xFF059669)
            else -> Color(0xFFDC2626)
          }
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Five-step Split Bill workspace: Expenses, People, Split, Settlement, History.
    ScrollableTabRow(
      selectedTabIndex = state.activeTab,
      edgePadding = 0.dp,
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
      modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
    ) {
      val sections = listOf("Expenses", "People", "Split", "Settlement", "History")
      val sectionIcons = listOf(Icons.AutoMirrored.Filled.ReceiptLong, Icons.Default.Group, Icons.Default.PieChart, Icons.Default.AccountBalanceWallet, Icons.Default.History)
      sections.forEachIndexed { index, label ->
        Tab(
          selected = state.activeTab == index,
          onClick = { viewModel.setTab(index) },
          text = { Text(if (index == 1) "People (${state.participants.size})" else label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
          icon = { Icon(sectionIcons[index], contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    when (state.activeTab) {
      0 -> ItemsAndBillTab(state = state, viewModel = viewModel, onAddItemClick = { viewModel.setShowAddItemDialog(true) })
      1 -> DinersAndClaimsTab(state = state, breakdowns = breakdowns, transfers = simplifiedTransfers, viewModel = viewModel, onAddDinerClick = { viewModel.setShowAddParticipantDialog(true) })
      2 -> SplitMethodTab(state = state, viewModel = viewModel, breakdowns = breakdowns)
      3 -> SettlementLedgerTab(
        state = state, breakdowns = breakdowns, transfers = simplifiedTransfers, totalBill = totalBill,
        viewModel = viewModel, bankAccounts = financeAccounts,
        onRecordPayment = { title, amount, accountId, accountName, method, settlementReference ->
          financeViewModel?.logSplitBillPayment(title, amount, accountId, accountName, method, settlementReference)
        },
        onRecordIncome = { title, amount, accountId, accountName, method, settlementReference ->
          financeViewModel?.logSplitBillSettlementIncome(title, amount, accountId, accountName, method, settlementReference)
        }
      )
      4 -> BillHistoryTab(savedBills = savedBills, currentBillId = state.billId, onOpen = { viewModel.openSavedBill(it); viewModel.setTab(0) }, onDelete = viewModel::deleteSavedBill, onNew = { viewModel.startNewBill(); viewModel.setTab(0) })
    }
  }
}

@Composable
private fun SplitMethodTab(
  state: SplitBillUiState,
  viewModel: SplitBillViewModel,
  breakdowns: List<PersonBreakdown>
) {
  val modes = listOf(SplitMode.EQUAL, SplitMode.PERCENTAGE, SplitMode.CUSTOM, SplitMode.ITEMIZED)
  val totalPercentage = state.participants.sumOf { it.sharePercentage }
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 80.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Choose how to split", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Select a fair allocation method for this group bill.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        modes.forEach { mode ->
          val selected = state.splitMode == mode
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(if (selected) 1.5.dp else 0.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().clickable { viewModel.setSplitMode(mode) }
          ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
              RadioButton(selected = selected, onClick = { viewModel.setSplitMode(mode) })
              Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(mode.title, fontWeight = FontWeight.SemiBold)
                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }
    if (state.splitMode != SplitMode.ITEMIZED) {
      Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Bill amount", fontWeight = FontWeight.Bold)
          OutlinedTextField(
            value = amountInputText(state.manualBillTotal),
            onValueChange = { value -> if (value.isEmpty() || value.matches(Regex("^\\\\d*\\\\.?\\\\d{0,2}$"))) viewModel.setManualBillTotal(value.toDoubleOrNull() ?: 0.0) },
            label = { Text("Total before tax, tip and discount") },
            prefix = { Text(state.currencySymbol) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          when (state.splitMode) {
            SplitMode.PERCENTAGE -> {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Assign percentage per person", fontWeight = FontWeight.SemiBold)
                Text("${String.format(Locale.US, "%.1f", totalPercentage)}%", color = if (kotlin.math.abs(totalPercentage - 100.0) < 0.01) Color(0xFF059669) else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
              }
              state.participants.forEach { person ->
                OutlinedTextField(
                  value = amountInputText(person.sharePercentage),
                  onValueChange = { value -> if (value.isEmpty() || value.matches(Regex("^\\\\d*\\\\.?\\\\d{0,2}$"))) viewModel.setParticipantSharePercentage(person.id, value.toDoubleOrNull() ?: 0.0) },
                  label = { Text(person.name) },
                  suffix = { Text("%") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
              }
              if (kotlin.math.abs(totalPercentage - 100.0) >= 0.01) Text("Percentages should add up to 100%. Shares are normalized until then.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            SplitMode.CUSTOM -> {
              Text("Set relative share weights. Example: 2, 1, 1 means 50%, 25%, 25%.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              state.participants.forEach { person ->
                OutlinedTextField(
                  value = amountInputText(person.customShareWeight),
                  onValueChange = { value -> if (value.isEmpty() || value.matches(Regex("^\\\\d*\\\\.?\\\\d{0,2}$"))) viewModel.setParticipantCustomWeight(person.id, value.toDoubleOrNull() ?: 0.0) },
                  label = { Text("${person.name} share weight") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }
            else -> Text("The bill is divided equally among all participants.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    } else {
      Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
          Text("Item-by-item split", fontWeight = FontWeight.Bold)
          Text("Go to Expenses to add dishes, scan a receipt and choose who shared each item. Each person's share is calculated from their claimed items.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          TextButton(onClick = { viewModel.setTab(0) }) { Text("Go to Expenses") }
        }
      }
    }
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Share preview", fontWeight = FontWeight.Bold)
        breakdowns.forEach { row ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.participant.name, style = MaterialTheme.typography.bodyMedium)
            Text("${state.currencySymbol}${String.format(Locale.US, "%,.2f", row.totalToPay)}", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }
}

@Composable
private fun BillHistoryTab(
  savedBills: List<com.example.data.local.entity.SplitBillRecord>,
  currentBillId: String,
  onOpen: (String) -> Unit,
  onDelete: (String) -> Unit,
  onNew: () -> Unit
) {
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Column {
        Text("Your saved bills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("${savedBills.size} bills stored on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      FilledTonalButton(onClick = onNew) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("New bill") }
    }
    if (savedBills.isEmpty()) {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
          Text("No bill history yet", fontWeight = FontWeight.Bold)
          Text("Create a bill and it will be saved automatically here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
        items(savedBills, key = { it.id }) { bill ->
          Card(Modifier.fillMaxWidth().clickable { onOpen(bill.id) }, border = if (bill.id == currentBillId) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(bill.title, fontWeight = FontWeight.SemiBold)
                Text("${bill.currencyCode} · ${java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(java.util.Date(bill.updatedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (bill.id == currentBillId) Text("Currently open", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
              }
              IconButton(onClick = { onDelete(bill.id) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete bill", tint = MaterialTheme.colorScheme.error) }
              Icon(Icons.Default.ChevronRight, contentDescription = "Open bill")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ItemsAndBillTab(
  state: SplitBillUiState,
  viewModel: SplitBillViewModel,
  onAddItemClick: () -> Unit
) {
  val haptic = LocalHapticFeedback.current

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    contentPadding = PaddingValues(bottom = 80.dp)
  ) {
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Your expenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text("Add what the group spent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

      }
    }

    // Items Section Header
    if (state.splitMode == SplitMode.ITEMIZED) item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Dishes & Line Items (${state.items.size})",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Tap diner avatar to assign who ate/drank each item",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
          )
          FilledTonalButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              onAddItemClick()
            },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add dish", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
          }
        }
      }
    }

    // Empty state when no items exist
    if (state.splitMode == SplitMode.ITEMIZED && state.items.isEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              Icons.Default.RestaurantMenu,
              contentDescription = null,
              modifier = Modifier.size(44.dp),
              tint = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "No dishes added yet",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Add dishes manually or scan your receipt to claim items",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = onAddItemClick,
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Dish")
              }
              OutlinedButton(
                onClick = { viewModel.setShowOcrDialog(true) },
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan Receipt")
              }
            }
          }
        }
      }
    }

    // Itemized Line Items Cards
    if (state.splitMode == SplitMode.ITEMIZED) items(state.items, key = { it.id }) { item ->
      val itemTotal = item.price * item.quantity
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text(
                text = "${if (item.quantity > 1) "${item.quantity}x " else ""}${item.title}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", itemTotal)}",
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp
              )
              IconButton(
                onClick = { viewModel.removeItem(item.id) },
                modifier = Modifier.size(28.dp).padding(start = 4.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Delete item", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
              }
            }
          }

          val payerName = state.participants.firstOrNull {
            it.id == item.paidByParticipantId
          }?.name
          if (payerName != null) {
            Text(
              text = "Paid by $payerName",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Avatar Claim Chips: Tap to toggle claim for each person
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Claimed by:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              TextButton(
                onClick = { viewModel.claimItemForAll(item.id) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text("All Split", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
          ) {
            items(state.participants) { p ->
              val isClaimed = item.claimedParticipantIds.contains(p.id) || item.claimedParticipantIds.isEmpty()
              val color = try {
                Color(android.graphics.Color.parseColor(p.avatarColorHex))
              } catch (e: Exception) {
                MaterialTheme.colorScheme.primary
              }

              Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isClaimed) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.5.dp, if (isClaimed) color else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.clickable {
                  haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                  viewModel.toggleItemClaim(item.id, p.id)
                }
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(12.dp)
                      .clip(CircleShape)
                      .background(if (isClaimed) color else MaterialTheme.colorScheme.onSurfaceVariant)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = p.name,
                    fontSize = 11.sp,
                    fontWeight = if (isClaimed) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }

            // Quick Add Person Chip
            item {
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.clickable {
                  haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                  viewModel.setShowAddParticipantDialog(true)
                }
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "Add Person",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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
private fun DinersAndClaimsTab(
  state: SplitBillUiState,
  breakdowns: List<PersonBreakdown>,
  transfers: List<DebtTransfer>,
  viewModel: SplitBillViewModel,
  onAddDinerClick: () -> Unit
) {
  val haptic = LocalHapticFeedback.current

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    contentPadding = PaddingValues(bottom = 80.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(
          modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
          Text(
            text = "Diners & Individual Shares",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 2
          )
          Text(
            text = "Track consumed share, who paid, and net balance",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
          )
        }

        FilledTonalButton(
          onClick = {
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
            onAddDinerClick()
          },
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("+ Diner", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    // Inline Quick Add Person Input
    item {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        var quickName by remember { mutableStateOf("") }
        Row(
          modifier = Modifier.fillMaxWidth().padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = quickName,
            onValueChange = { quickName = it },
            placeholder = { Text("Add diner name (e.g. Rahul, Priya)...", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
          )
          Button(
            onClick = {
              if (quickName.isNotBlank()) {
                viewModel.addParticipant(quickName.trim(), "", "", 0.0)
                quickName = ""
              }
            },
            enabled = quickName.isNotBlank(),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    if (breakdowns.isNotEmpty()) {
      item {
        ShareDistributionChart(
          breakdowns = breakdowns,
          currencySymbol = state.currencySymbol
        )
      }
    }

    if (breakdowns.isEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No Diners Added", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("Type a name above or tap '+ Diner' to add people to split this bill.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
          }
        }
      }
    }

    items(breakdowns, key = { it.participant.id }) { b ->
      val p = b.participant
      var paidAmountInput by remember(p.id) {
        mutableStateOf(amountInputText(p.paidAmount))
      }
      val avatarColor = try {
        Color(android.graphics.Color.parseColor(p.avatarColorHex))
      } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
      }

      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(avatarColor),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = p.name.take(1).uppercase(),
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                if (p.upiId.isNotBlank()) {
                  Text("UPI: ${p.upiId}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", b.totalToPay)}",
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 16.sp,
                  color = MaterialTheme.colorScheme.primary
                )
                Text("Total Share", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Spacer(modifier = Modifier.width(6.dp))
              IconButton(
                onClick = {
                  haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                  viewModel.removeParticipant(p.id)
                },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  Icons.Default.DeleteOutline,
                  contentDescription = "Remove diner",
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Spacer(modifier = Modifier.height(8.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          Spacer(modifier = Modifier.height(8.dp))

          // Amount Paid Input (Did this person pay the restaurant?)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text(
                text = "Paid ${state.currencySymbol}${String.format(Locale.US, "%,.2f", b.paidAmount)} of ${state.currencySymbol}${String.format(Locale.US, "%,.2f", b.totalToPay)} share",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              val outgoingTransfers = transfers.filter { it.fromParticipant.id == p.id }
              val incomingTransfers = transfers.filter { it.toParticipant.id == p.id }
              outgoingTransfers.forEach { transfer ->
                Text(
                  text = "Owes ${transfer.toParticipant.name} ${state.currencySymbol}${String.format(Locale.US, "%,.2f", transfer.amount)}",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFDC2626)
                )
              }
              incomingTransfers.forEach { transfer ->
                Text(
                  text = "Gets back ${state.currencySymbol}${String.format(Locale.US, "%,.2f", transfer.amount)} from ${transfer.fromParticipant.name}",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF059669)
                )
              }
              if (outgoingTransfers.isEmpty() && incomingTransfers.isEmpty()) {
                Text(
                  text = "Settled",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            OutlinedTextField(
              value = paidAmountInput,
              onValueChange = {
                paidAmountInput = it
                it.toDoubleOrNull()?.let { amount -> viewModel.updateParticipantPaid(p.id, amount) }
              },
              label = { Text("Other paid", fontSize = 10.sp) },
              prefix = { Text(state.currencySymbol, fontSize = 11.sp) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.width(115.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ShareDistributionChart(
  breakdowns: List<PersonBreakdown>,
  currencySymbol: String
) {
  val total = breakdowns.sumOf { it.totalToPay }
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Share distribution", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
          text = "$currencySymbol${String.format(Locale.US, "%,.2f", total)} total",
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.primary
        )
      }
      breakdowns.forEach { breakdown ->
        val fraction = if (total > 0.0) {
          (breakdown.totalToPay / total).toFloat().coerceIn(0f, 1f)
        } else {
          0f
        }
        val color = try {
          Color(android.graphics.Color.parseColor(breakdown.participant.avatarColorHex))
        } catch (_: IllegalArgumentException) {
          MaterialTheme.colorScheme.primary
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = breakdown.participant.name,
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
            Text(
              text = "$currencySymbol${String.format(Locale.US, "%,.2f", breakdown.totalToPay)}  ${String.format(Locale.US, "%.1f", fraction * 100)}%",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surface)
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(color)
            )
          }
        }
      }
    }
  }
}

private fun amountInputText(amount: Double): String =
  if (amount <= 0.0 || !amount.isFinite()) "" else amount.toString().removeSuffix(".0")

@Composable
private fun SettlementLedgerTab(
  state: SplitBillUiState,
  breakdowns: List<PersonBreakdown>,
  transfers: List<DebtTransfer>,
  totalBill: Double,
  viewModel: SplitBillViewModel,
  bankAccounts: List<com.example.data.local.entity.BankAccount>,
  onRecordPayment: (String, Double, Long?, String, String, String) -> Unit,
  onRecordIncome: (String, Double, Long?, String, String, String) -> Unit
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  var pendingSettlement by remember { mutableStateOf<DebtTransfer?>(null) }
  var pendingTransferKey by remember { mutableStateOf("") }
  var pendingOriginalAmount by remember { mutableStateOf(0.0) }
  var settlementAmountText by remember { mutableStateOf("") }
  var selectedPaymentAccountId by remember { mutableStateOf<Long?>(null) }
  var paymentSource by remember { mutableStateOf("Cash / Wallet") }
  var showAccountMenu by remember { mutableStateOf(false) }

  pendingSettlement?.let { transfer ->
    val isCurrentUserPayer = transfer.fromParticipant.name.equals("You (Me)", ignoreCase = true) ||
      transfer.fromParticipant.name.equals("You", ignoreCase = true) ||
      transfer.fromParticipant.name.equals("Me", ignoreCase = true)
    val isCurrentUserPayee = transfer.toParticipant.name.equals("You (Me)", ignoreCase = true) ||
      transfer.toParticipant.name.equals("You", ignoreCase = true) ||
      transfer.toParticipant.name.equals("Me", ignoreCase = true)
    val currentUserInvolved = isCurrentUserPayer || isCurrentUserPayee
    AlertDialog(
      onDismissRequest = { pendingSettlement = null },
      title = { Text(when { isCurrentUserPayer -> "Record payment"; isCurrentUserPayee -> "Record received payment"; else -> "Confirm settlement" }) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("${transfer.fromParticipant.name} pays ${transfer.toParticipant.name} ${state.currencySymbol}${String.format(Locale.US, "%,.2f", transfer.amount)}")
          OutlinedTextField(
            value = settlementAmountText,
            onValueChange = { value -> if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) settlementAmountText = value },
            label = { Text("Amount paid now (max ${state.currencySymbol}${String.format(Locale.US, "%.2f", transfer.amount)})") },
            prefix = { Text(state.currencySymbol) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          if (currentUserInvolved) {
            Text(if (isCurrentUserPayer) "Choose the account or wallet you paid from. This will be added to Finance > Expenses." else "Choose the account where you received the reimbursement. This will be added to Finance > Income.")
            Box {
              OutlinedButton(onClick = { showAccountMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(paymentSource, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
              }
              DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                DropdownMenuItem(
                  text = { Text("Cash / Wallet (not linked)") },
                  onClick = {
                    paymentSource = "Cash / Wallet"
                    selectedPaymentAccountId = null
                    showAccountMenu = false
                  }
                )
                bankAccounts.filterNot { it.accountType.equals("Credit Card", ignoreCase = true) }.forEach { account ->
                  DropdownMenuItem(
                    text = { Text(account.bankName) },
                    onClick = {
                      paymentSource = account.bankName
                      selectedPaymentAccountId = account.id
                      showAccountMenu = false
                    }
                  )
                }
              }
            }
            if (bankAccounts.isEmpty()) {
              Text("No bank accounts found. Cash / Wallet will still be recorded in your expense history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          } else {
            Text("This payment is from another participant. Confirm only after you have received the payment.")
          }
        }
      },
      confirmButton = {
        Button(onClick = {
          val settlementAmount = (settlementAmountText.toDoubleOrNull() ?: transfer.amount).coerceIn(0.0, transfer.amount)
          if (settlementAmount <= 0.0) return@Button
          val cumulative = (state.transferPaidAmounts[pendingTransferKey] ?: 0.0) + settlementAmount
          val settlementReference = "${state.billId}:$pendingTransferKey:${String.format(Locale.US, "%.2f", cumulative)}"
          val selectedAccountName = bankAccounts.firstOrNull { it.id == selectedPaymentAccountId }?.bankName ?: ""
          if (isCurrentUserPayer) {
            onRecordPayment(state.billTitle, settlementAmount, selectedPaymentAccountId, selectedAccountName, paymentSource, settlementReference)
          } else if (isCurrentUserPayee) {
            onRecordIncome(state.billTitle, settlementAmount, selectedPaymentAccountId, selectedAccountName, paymentSource, settlementReference)
          }
          viewModel.addTransferPayment(pendingTransferKey, settlementAmount, pendingOriginalAmount)
          pendingSettlement = null
        }) { Text(when { isCurrentUserPayer -> "Record & settle"; isCurrentUserPayee -> "Record received"; else -> "Confirm paid" }) }
      },
      dismissButton = { TextButton(onClick = { pendingSettlement = null }) { Text("Cancel") } }
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(bottom = 80.dp)
  ) {
    // Debt Simplification Toggle Banner
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text("Simplify Debts (Graph Minimization)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Text("Minimizes total number of payments across group", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          Switch(
            checked = state.simplifyDebtsEnabled,
            onCheckedChange = { viewModel.toggleSimplifyDebts() }
          )
        }
      }
    }

    // Settlement Actions Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Who Owes Whom (${transfers.size} transactions)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

        // WhatsApp Share Button
        FilledTonalButton(
          onClick = {
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
            PaymentDeepLinkHelper.shareBreakdown(
              context = context,
              billTitle = state.billTitle,
              currencySymbol = state.currencySymbol,
              grandTotal = totalBill,
              breakdowns = breakdowns,
              transfers = transfers
            )
          },
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF25D366).copy(alpha = 0.15f),
            contentColor = Color(0xFF15803D)
          )
        ) {
          Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Share WhatsApp", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    if (transfers.isEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("All Debts Settled!", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Everyone has paid their exact share or no debts remain.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    } else {
      items(transfers) { transfer ->
        val transferKey = "${transfer.fromParticipant.id}_${transfer.toParticipant.id}_${transfer.amount}"
        val paidSoFar = state.transferPaidAmounts[transferKey] ?: 0.0
        val remainingAmount = (transfer.amount - paidSoFar).coerceAtLeast(0.0)
        val isSettled = state.settledTransferKeys.contains(transferKey) || remainingAmount <= 0.005

        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (isSettled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
          ),
          border = if (isSettled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(transfer.fromParticipant.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurface)
                  Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp).padding(horizontal = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(transfer.toParticipant.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                if (transfer.toParticipant.upiId.isNotBlank()) {
                  Text("UPI: ${transfer.toParticipant.upiId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }

              Text(
                text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", remainingAmount)}",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSettled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
              )
            }

            if (paidSoFar > 0.0) {
              Text("Paid: ${state.currencySymbol}${String.format(Locale.US, "%,.2f", paidSoFar)} · Remaining: ${state.currencySymbol}${String.format(Locale.US, "%,.2f", remainingAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Pay UPI | PayPal | Nudge | Mark Settled
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Direct UPI Deep Link
              if (transfer.toParticipant.upiId.isNotBlank()) {
                FilledTonalButton(
                  onClick = {
                    PaymentDeepLinkHelper.launchUpiPayment(
                      context = context,
                      upiId = transfer.toParticipant.upiId,
                      payeeName = transfer.toParticipant.name,
                      amount = remainingAmount,
                      currency = state.currencyCode,
                      note = "OmniCalc: ${state.billTitle}"
                    )
                  },
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Text("Pay UPI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }

              // PayPal Deep Link
              if (transfer.toParticipant.paypalUsername.isNotBlank()) {
                FilledTonalButton(
                  onClick = {
                    PaymentDeepLinkHelper.launchPayPal(
                      context = context,
                      paypalUsername = transfer.toParticipant.paypalUsername,
                      amount = remainingAmount
                    )
                  },
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Text("PayPal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }

              // Nudge Reminder
              OutlinedButton(
                onClick = {
                  PaymentDeepLinkHelper.sendNudgeReminder(
                    context = context,
                    debtorName = transfer.fromParticipant.name,
                    creditorName = transfer.toParticipant.name,
                    amount = transfer.amount,
                    currencySymbol = state.currencySymbol,
                    billTitle = state.billTitle,
                    creditorUpiId = transfer.toParticipant.upiId
                  )
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Nudge", fontSize = 10.5.sp)
              }

              Spacer(modifier = Modifier.weight(1f))

              // Prominent Mark Settled Button
              Button(
                enabled = !isSettled,
                onClick = {
                  haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                  paymentSource = "Cash / Wallet"
                  selectedPaymentAccountId = null
                  pendingTransferKey = transferKey
                  pendingOriginalAmount = transfer.amount
                  settlementAmountText = amountInputText(remainingAmount)
                  pendingSettlement = transfer.copy(amount = remainingAmount)
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (isSettled) Color(0xFF10B981) else MaterialTheme.colorScheme.primaryContainer,
                  contentColor = if (isSettled) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Icon(
                  if (isSettled) Icons.Default.CheckCircle else Icons.Default.Check,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (isSettled) "Settled ✓" else if (paidSoFar > 0.0) "Pay balance" else "Settle",
                  fontSize = 11.5.sp,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrScannerBottomSheet(
  onDismiss: () -> Unit,
  onApplyReceipt: (ParsedReceipt) -> Unit,
  onLoadDemo: (String, String) -> Unit
) {
  val context = LocalContext.current
  var pastedText by remember { mutableStateOf("") }
  var isParsing by remember { mutableStateOf(false) }
  val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
  DisposableEffect(recognizer) { onDispose { recognizer.close() } }

  // On-device OCR: the image stays on the phone; extracted text is parsed locally.
  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      isParsing = true
      runCatching { InputImage.fromFilePath(context, uri) }
        .onSuccess { image ->
          recognizer.process(image)
            .addOnSuccessListener { result ->
              isParsing = false
              val parsed = ReceiptOcrParser.parseReceiptText(result.text, "Scanned Receipt")
              if (parsed.items.isEmpty()) {
                Toast.makeText(context, "No receipt line items detected. Try a clearer photo or paste the receipt text.", Toast.LENGTH_LONG).show()
              } else {
                onApplyReceipt(parsed)
                onDismiss()
              }
            }
            .addOnFailureListener { error ->
              isParsing = false
              Toast.makeText(context, "Could not read this receipt: ${error.localizedMessage ?: "Try another image."}", Toast.LENGTH_LONG).show()
            }
        }
        .onFailure { error ->
          isParsing = false
          Toast.makeText(context, "Could not open this image: ${error.localizedMessage ?: "Try another image."}", Toast.LENGTH_LONG).show()
        }
    }
  }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 10.dp)
        .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text("AI Receipt Scanner & OCR", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          Text("On-device text recognition; review parsed items before splitting", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      // Quick Upload / Snap Options
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        FilledTonalButton(
          onClick = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          },
          enabled = !isParsing,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          if (isParsing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          else Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (isParsing) "Reading receipt…" else "Photo / Receipt", fontSize = 12.sp)
        }
      }

      // 1-Tap Demo Receipts
      Text("Or test instantly with sample receipts:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilledTonalButton(
          onClick = {
            onLoadDemo(ReceiptOcrParser.demoReceiptBistro, "Italian Bistro")
            onDismiss()
          },
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          modifier = Modifier.weight(1f)
        ) {
          Text("🍕 Italian Bistro", fontSize = 11.sp)
        }

        FilledTonalButton(
          onClick = {
            onLoadDemo(ReceiptOcrParser.demoReceiptCafe, "Artisan Cafe")
            onDismiss()
          },
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          modifier = Modifier.weight(1f)
        ) {
          Text("☕ Cafe & Brunch", fontSize = 11.sp)
        }

        FilledTonalButton(
          onClick = {
            onLoadDemo(ReceiptOcrParser.demoReceiptSushi, "Sushi Kitchen")
            onDismiss()
          },
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          modifier = Modifier.weight(1f)
        ) {
          Text("🍣 Sushi Dinner", fontSize = 11.sp)
        }
      }

      // Paste raw text option
      OutlinedTextField(
        value = pastedText,
        onValueChange = { pastedText = it },
        placeholder = { Text("Or paste raw receipt text here...") },
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(12.dp)
      )

      Button(
        onClick = {
          if (pastedText.isNotBlank()) {
            val parsed = ReceiptOcrParser.parseReceiptText(pastedText, "Pasted Receipt")
            onApplyReceipt(parsed)
            onDismiss()
          }
        },
        enabled = pastedText.isNotBlank(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Parse Pasted Receipt Text", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun AddParticipantDialog(
  onDismiss: () -> Unit,
  onConfirm: (name: String, upi: String, paypal: String, paid: Double) -> Unit
) {
  var name by remember { mutableStateOf("") }
  var upi by remember { mutableStateOf("") }
  var paypal by remember { mutableStateOf("") }
  var paidStr by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Participant / Diner") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Diner Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = upi,
          onValueChange = { upi = it },
          label = { Text("UPI ID (Optional for GPay/PhonePe)") },
          placeholder = { Text("e.g. name@okhdfcbank") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = paypal,
          onValueChange = { paypal = it },
          label = { Text("PayPal Username (Optional)") },
          placeholder = { Text("e.g. johndoe") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = paidStr,
          onValueChange = { paidStr = it },
          label = { Text("Amount Paid upfront (if any)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(onClick = { onConfirm(name, upi, paypal, paidStr.toDoubleOrNull() ?: 0.0) }) {
        Text("Add Diner")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
private fun AddReceiptItemDialog(
  participants: List<Participant>,
  currencySymbol: String,
  onDismiss: () -> Unit,
  onConfirm: (
    title: String,
    price: Double,
    qty: Int,
    claimedIds: List<String>,
    paidByParticipantId: String?
  ) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var priceStr by remember { mutableStateOf("") }
  var qtyStr by remember { mutableStateOf("1") }
  var selectedParticipantIds by remember(participants) { mutableStateOf(participants.map { it.id }) }
  var selectedPayerId by remember { mutableStateOf(participants.firstOrNull()?.id) }
  var payerMenuExpanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(20.dp),
    title = { Text("Add Dish / Expense", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 420.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Dish / Item Name") },
          placeholder = { Text("e.g. Biryani, Drinks, Pizza") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = priceStr,
            onValueChange = { priceStr = it },
            label = { Text("Price ($currencySymbol)") },
            placeholder = { Text("0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = qtyStr,
            onValueChange = { qtyStr = it },
            label = { Text("Qty") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(0.5f)
          )
        }

        // Active Prompt for Who Paid
        Text(
          text = "Who Paid for this Item?",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary
        )

        Box(modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = { payerMenuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
          ) {
            val payerName = participants.firstOrNull { it.id == selectedPayerId }?.name ?: "Select Payer"
            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Paid by: $payerName", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
          }

          DropdownMenu(
            expanded = payerMenuExpanded,
            onDismissRequest = { payerMenuExpanded = false }
          ) {
            participants.forEach { p ->
              DropdownMenuItem(
                text = { Text(p.name, fontWeight = if (p.id == selectedPayerId) FontWeight.Bold else FontWeight.Normal) },
                onClick = {
                  selectedPayerId = p.id
                  payerMenuExpanded = false
                }
              )
            }
          }
        }

        // Who Shared this Dish
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Who Shared this Dish?",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
          )
          TextButton(
            onClick = {
              selectedParticipantIds = if (selectedParticipantIds.size == participants.size) {
                listOfNotNull(participants.firstOrNull()?.id)
              } else {
                participants.map { it.id }
              }
            }
          ) {
            Text(if (selectedParticipantIds.size == participants.size) "Clear" else "Select All", fontSize = 11.sp)
          }
        }

        participants.forEach { participant ->
          val isSelected = participant.id in selectedParticipantIds
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .clickable {
                selectedParticipantIds = if (isSelected) {
                  if (selectedParticipantIds.size > 1) selectedParticipantIds - participant.id else selectedParticipantIds
                } else {
                  selectedParticipantIds + participant.id
                }
              }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = isSelected,
              onCheckedChange = { checked ->
                selectedParticipantIds = if (checked) {
                  selectedParticipantIds + participant.id
                } else {
                  if (selectedParticipantIds.size > 1) selectedParticipantIds - participant.id else selectedParticipantIds
                }
              }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = participant.name,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
          }
        }
      }
    },
    confirmButton = {
      val parsedPrice = priceStr.toDoubleOrNull() ?: 0.0
      val canAdd = title.isNotBlank() && parsedPrice > 0.0
      Button(
        onClick = {
          val qty = qtyStr.toIntOrNull() ?: 1
          onConfirm(title.trim(), parsedPrice, qty, selectedParticipantIds, selectedPayerId)
        },
        enabled = canAdd,
        shape = RoundedCornerShape(10.dp)
      ) {
        Text("Add Dish")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
