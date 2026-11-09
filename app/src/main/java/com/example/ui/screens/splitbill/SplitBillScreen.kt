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
import com.example.data.local.entity.BankAccount
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitBillScreen(
  viewModel: SplitBillViewModel,
  modifier: Modifier = Modifier,
  accounts: List<BankAccount> = emptyList(),
  onRecordSettlement: (isIncome: Boolean, amount: Double, account: BankAccount, title: String, reference: String) -> Unit = { _, _, _, _, _ -> },
  onUndoSettlement: (reference: String) -> Unit = {}
) {
  val state by viewModel.uiState.collectAsState()
  val breakdowns = remember(state.participants, state.items) {
    DebtSimplifier.calculateBreakdowns(SplitMode.ITEMIZED, state.participants, state.items, 0.0, 0.0, 0.0, 0.0)
  }
  val total = breakdowns.sumOf { it.totalToPay }
  val transfers = remember(breakdowns) { DebtSimplifier.simplifyDebts(breakdowns) }
  val clipboard = LocalClipboardManager.current
  var currencyMenu by remember { mutableStateOf(false) }
  var selectedExpense by remember { mutableStateOf<ReceiptItem?>(null) }
  var editingExpense by remember { mutableStateOf<ReceiptItem?>(null) }
  var expensePendingDelete by remember { mutableStateOf<ReceiptItem?>(null) }
  var selectedPerson by remember { mutableStateOf<Participant?>(null) }
  var editingPerson by remember { mutableStateOf<Participant?>(null) }
  var personPendingDelete by remember { mutableStateOf<Participant?>(null) }
  var pendingTransfer by remember { mutableStateOf<PendingSplitTransfer?>(null) }
  var transferPaymentSource by remember { mutableStateOf("") }
  var transferAccountId by remember(accounts) { mutableStateOf(accounts.firstOrNull { !it.accountType.equals("Credit Card", true) }?.id) }
  var accountMenuExpanded by remember { mutableStateOf(false) }

  if (state.showAddParticipantDialog) AddParticipantDialog(
    onDismiss = { viewModel.setShowAddParticipantDialog(false) },
    onConfirm = { n, u, p, a -> viewModel.addParticipant(n, u, p, a); viewModel.setShowAddParticipantDialog(false) }
  )
  editingPerson?.let { person -> AddParticipantDialog(initialParticipant = person, onDismiss = { editingPerson = null }, onConfirm = { n, u, p, a -> viewModel.updateParticipant(person.id, n, u, p, a); editingPerson = null }) }
  if (state.showAddItemDialog) AddReceiptItemDialog(
    participants = state.participants, currencySymbol = state.currencySymbol,
    onDismiss = { viewModel.setShowAddItemDialog(false) },
    onConfirm = { t, p, q, ids, payer, source -> viewModel.addItem(t, p, q, ids, payer, source); viewModel.setShowAddItemDialog(false) }
  )
  editingExpense?.let { expense -> AddReceiptItemDialog(participants = state.participants, currencySymbol = state.currencySymbol, initialItem = expense, onDismiss = { editingExpense = null }, onConfirm = { t, p, q, ids, payer, source -> viewModel.updateItem(expense.copy(title = t, price = p, quantity = q, claimedParticipantIds = ids, paidByParticipantId = payer, paymentSource = source)); editingExpense = null }) }
  selectedExpense?.let { item -> AlertDialog(onDismissRequest = { selectedExpense = null }, title = { Text(item.title, fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Amount: ${state.currencySymbol}${String.format(Locale.US, "%,.2f", item.price * item.quantity)}"); Text("Quantity: ${item.quantity}"); Text("Paid by: ${state.participants.firstOrNull { it.id == item.paidByParticipantId }?.name ?: "Not specified"}"); Text("Payment source: ${item.paymentSource}"); Text("Shared with: ${state.participants.filter { it.id in item.claimedParticipantIds }.joinToString { it.name }.ifBlank { "Everyone" }}") } }, confirmButton = { TextButton(onClick = { selectedExpense = null; editingExpense = item }) { Text("Edit") } }, dismissButton = { Row { TextButton(onClick = { selectedExpense = null; expensePendingDelete = item }) { Text("Delete", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = { selectedExpense = null }) { Text("Close") } } } ) }
  expensePendingDelete?.let { item -> AlertDialog(onDismissRequest = { expensePendingDelete = null }, title = { Text("Delete transaction?") }, text = { Text("Are you sure you want to delete '${item.title}'? This cannot be undone.") }, confirmButton = { TextButton(onClick = { viewModel.removeItem(item.id); expensePendingDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = { expensePendingDelete = null }) { Text("Cancel") } }) }
  selectedPerson?.let { person -> AlertDialog(onDismissRequest = { selectedPerson = null }, title = { Text(person.name, fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("UPI: ${person.upiId.ifBlank { "Not added" }}"); Text("PayPal: ${person.paypalUsername.ifBlank { "Not added" }}"); Text("Amount paid: ${state.currencySymbol}${String.format(Locale.US, "%,.2f", person.paidAmount)}") } }, confirmButton = { TextButton(onClick = { selectedPerson = null; editingPerson = person }) { Text("Edit") } }, dismissButton = { Row { if (person.id != state.participants.firstOrNull()?.id && state.participants.size > 1) TextButton(onClick = { selectedPerson = null; personPendingDelete = person }) { Text("Delete", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = { selectedPerson = null }) { Text("Close") } } }) }
  personPendingDelete?.let { person -> AlertDialog(onDismissRequest = { personPendingDelete = null }, title = { Text("Delete person?") }, text = { Text("Remove ${person.name} from this bill? Their expense shares will also be updated.") }, confirmButton = { TextButton(onClick = { viewModel.removeParticipant(person.id); personPendingDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = { personPendingDelete = null }) { Text("Cancel") } }) }
  pendingTransfer?.let { transfer ->
    val currentUserId = state.participants.firstOrNull()?.id
    val receiving = transfer.toId == currentUserId
    val selectedAccount = accounts.firstOrNull { it.id == transferAccountId }
    AlertDialog(
      onDismissRequest = { pendingTransfer = null },
      title = { Text(if (receiving) "Confirm money received" else "Confirm payment") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(if (receiving) "Choose the bank or cash account where this repayment was received. It will be added as Finance income." else "Choose the bank or cash account used for this payment. It will be recorded as a Finance expense.")
          Box {
            OutlinedButton(onClick = { accountMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
              Text(selectedAccount?.let { "${it.bankName} • ${it.accountType}" } ?: "Choose bank / cash account", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
              Icon(Icons.Default.ExpandMore, null)
            }
            DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
              accounts.filterNot { it.accountType.equals("Credit Card", true) }.forEach { account ->
                DropdownMenuItem(text = { Text("${account.bankName} • ${account.accountType}") }, onClick = { transferAccountId = account.id; transferPaymentSource = account.bankName; accountMenuExpanded = false })
              }
            }
          }
          if (accounts.none { !it.accountType.equals("Credit Card", true) }) {
            Text("Add a bank or cash account in Finance before recording this settlement.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }
        }
      },
      confirmButton = {
        Button(enabled = selectedAccount != null, onClick = {
          val account = selectedAccount ?: return@Button
          val title = if (receiving) "Split Bill repayment from ${state.participants.firstOrNull { it.id == transfer.fromId }?.name ?: "friend"}" else "Split Bill payment to ${state.participants.firstOrNull { it.id == transfer.toId }?.name ?: "friend"}"
          onRecordSettlement(receiving, transfer.amount, account, title, "Split Bill settlement ${transfer.key}")
          viewModel.toggleSettledTransfer(transfer.key, account.bankName, "Split Bill settlement ${transfer.key}")
          pendingTransfer = null
        }) { Text(if (receiving) "Record received" else "Record payment") }
      },
      dismissButton = { TextButton(onClick = { pendingTransfer = null }) { Text("Cancel") } }
    )
  }

  if (state.showOcrDialog) OcrScannerBottomSheet(
    onDismiss = { viewModel.setShowOcrDialog(false) },
    onApplyReceipt = { viewModel.applyParsedReceipt(it.copy(tax = 0.0, tip = 0.0, discount = 0.0)) },
    onLoadDemo = { text, title -> viewModel.loadDemo(text, title) }
  )

  Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("Split Bill", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(state.billTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Box {
        FilledTonalButton(onClick = { currencyMenu = true }) { Text("${state.currencyCode} ${state.currencySymbol}"); Icon(Icons.Default.ExpandMore, null) }
        DropdownMenu(currencyMenu, { currencyMenu = false }) {
          listOf("INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£", "AED" to "د.إ").forEach { (c, s) ->
            DropdownMenuItem(text = { Text("$c ($s)") }, onClick = { viewModel.setCurrency(c, s); currencyMenu = false })
          }
        }
      }
    }
    Spacer(Modifier.height(14.dp))
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
      Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TOTAL BILL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f))
        Text("${state.currencySymbol}${String.format(Locale.US, "%,.2f", total)}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          SummaryMetric("PEOPLE", state.participants.size.toString(), MaterialTheme.colorScheme.onPrimary)
          SummaryMetric("EXPENSES", state.items.size.toString(), MaterialTheme.colorScheme.onPrimary)
          SummaryMetric("PAID", "${state.currencySymbol}${String.format(Locale.US, "%,.2f", breakdowns.sumOf { it.paidAmount })}", MaterialTheme.colorScheme.onPrimary)
        }
      }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      listOf("Expenses", "People", "Settle").forEachIndexed { i, label ->
        val selected = state.activeTab == i
        Surface(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { viewModel.setTab(i) }, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
          Text(label, Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    when (state.activeTab) {
      0 -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Your expenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Add what the group spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            FilledTonalButton(onClick = { viewModel.setShowAddItemDialog(true) }) { Icon(Icons.Default.Add, null); Text("Add") }
          }
        }
        if (state.items.isEmpty()) item { EmptySplitCard(Icons.AutoMirrored.Filled.ReceiptLong, "No expenses yet", "Add the first expense to start splitting the bill.", "Add expense") { viewModel.setShowAddItemDialog(true) } }
        items(state.items, key = { it.id }) { item ->
          val payer = state.participants.firstOrNull { it.id == item.paidByParticipantId }?.name ?: "Not specified"
          val names = state.participants.filter { it.id in item.claimedParticipantIds }.joinToString { it.name }.ifBlank { state.participants.joinToString { it.name } }
          Card(Modifier.fillMaxWidth().clickable { selectedExpense = item }, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Icon(Icons.Default.ReceiptLong, null, Modifier.padding(11.dp)) }
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Paid by $payer · ${item.paymentSource}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Shared: $names", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              Column(horizontalAlignment = Alignment.End) {
                Text("${state.currencySymbol}${String.format(Locale.US, "%,.2f", item.price * item.quantity)}", fontWeight = FontWeight.Bold)
                Text("View details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
        item { OutlinedButton(onClick = { viewModel.setShowOcrDialog(true) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan or import receipt") } }
      }
      1 -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Group members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Manage who shares this bill", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            FilledTonalButton(onClick = { viewModel.setShowAddParticipantDialog(true) }) { Icon(Icons.Default.PersonAdd, null); Text("Add") }
          }
        }
        items(breakdowns, key = { it.participant.id }) { person ->
          Card(Modifier.fillMaxWidth().clickable { selectedPerson = person.participant }, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              val avatar = runCatching { Color(android.graphics.Color.parseColor(person.participant.avatarColorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary)
              Surface(shape = CircleShape, color = avatar.copy(alpha = .16f)) { Text(person.participant.name.take(1).uppercase(), Modifier.padding(13.dp), color = avatar, fontWeight = FontWeight.Bold) }
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) { Text(person.participant.name, fontWeight = FontWeight.SemiBold); Text("Paid ${state.currencySymbol}${String.format(Locale.US, "%,.2f", person.paidAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
              Column(horizontalAlignment = Alignment.End) { Text("${state.currencySymbol}${String.format(Locale.US, "%,.2f", person.totalToPay)}", fontWeight = FontWeight.Bold); Text("Your share", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
              Text("Edit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
      else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Text("Settlement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("See who needs to pay whom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (transfers.isEmpty()) item { EmptySplitCard(Icons.Default.CheckCircle, "All settled", if (total <= 0.0) "Add expenses to calculate balances." else "There are no outstanding payments.", "Copy summary") {
          val lines = breakdowns.joinToString("\n") { "${it.participant.name}: ${state.currencySymbol}${String.format(Locale.US, "%.2f", it.totalToPay)}" }
          clipboard.setText(AnnotatedString("Split Bill\nTotal: ${state.currencySymbol}${String.format(Locale.US, "%.2f", total)}\n$lines"))
        } }
        items(transfers) { transfer ->
          val key = "${transfer.fromParticipant.id}_${transfer.toParticipant.id}_${transfer.amount}"
          val settled = key in state.settledTransferKeys
          Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) { Text(transfer.fromParticipant.name, fontWeight = FontWeight.SemiBold); Text("pays ${transfer.toParticipant.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
              Column(horizontalAlignment = Alignment.End) { Text("${state.currencySymbol}${String.format(Locale.US, "%,.2f", transfer.amount)}", fontWeight = FontWeight.Bold); TextButton(onClick = { if (settled) { state.settledPaymentReferences[key]?.let(onUndoSettlement); viewModel.toggleSettledTransfer(key) } else { transferPaymentSource = ""; pendingTransfer = PendingSplitTransfer(key, transfer.fromParticipant.id, transfer.toParticipant.id, transfer.amount) } }) { Text(if (settled) "Undo" else "Mark paid") }; state.settledPaymentSources[key]?.takeIf { settled }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
          }
        }
        item {
          OutlinedButton(onClick = {
            val lines = breakdowns.joinToString("\n") { "${it.participant.name}: ${state.currencySymbol}${String.format(Locale.US, "%.2f", it.totalToPay)}" }
            clipboard.setText(AnnotatedString("Split Bill - ${state.billTitle}\nTotal: ${state.currencySymbol}${String.format(Locale.US, "%.2f", total)}\n$lines"))
          }, Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("Copy split summary") }
        }
      }
    }
  }
}

private data class PendingSplitTransfer(val key: String, val fromId: String, val toId: String, val amount: Double)

@Composable
private fun SummaryMetric(label: String, value: String, contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
  Column { Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = .78f)); Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = contentColor) }
}

@Composable
private fun EmptySplitCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: String, onAction: () -> Unit) {
  Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      Button(onClick = onAction) { Text(action) }
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

  // Photo Picker
  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      // Simulate multimodal OCR extraction from image uri
      Toast.makeText(context, "Receipt captured! Reading expense items...", Toast.LENGTH_SHORT).show()
      onLoadDemo(ReceiptOcrParser.demoReceiptBistro, "Bistro Dinner (Scanned)")
      onDismiss()
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
          Text("Receipt scanner", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          Text("Extract expense names and prices from a receipt", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Photo / Receipt", fontSize = 12.sp)
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
  initialParticipant: Participant? = null,
  onDismiss: () -> Unit,
  onConfirm: (name: String, upi: String, paypal: String, paid: Double) -> Unit
) {
  var name by remember(initialParticipant) { mutableStateOf(initialParticipant?.name ?: "") }
  var upi by remember(initialParticipant) { mutableStateOf(initialParticipant?.upiId ?: "") }
  var paypal by remember(initialParticipant) { mutableStateOf(initialParticipant?.paypalUsername ?: "") }
  var paidStr by remember(initialParticipant) { mutableStateOf(initialParticipant?.paidAmount?.takeIf { it > 0 }?.toString() ?: "") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initialParticipant == null) "Add Participant / Diner" else "Edit Person") },
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
        Text(if (initialParticipant == null) "Add Diner" else "Save changes")
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
  initialItem: ReceiptItem? = null,
  onDismiss: () -> Unit,
  onConfirm: (title: String, price: Double, qty: Int, claimedIds: List<String>, paidByParticipantId: String?, paymentSource: String) -> Unit
) {
  var title by remember(initialItem) { mutableStateOf(initialItem?.title ?: "") }
  var priceStr by remember(initialItem) { mutableStateOf(initialItem?.price?.toString() ?: "") }
  var qtyStr by remember(initialItem) { mutableStateOf(initialItem?.quantity?.toString() ?: "1") }
  var paymentSource by remember(initialItem) { mutableStateOf(initialItem?.paymentSource ?: "Cash") }
  var paymentSourceExpanded by remember { mutableStateOf(false) }
  var selectedParticipantIds by remember(participants, initialItem) { mutableStateOf(initialItem?.claimedParticipantIds?.ifEmpty { participants.map { it.id } } ?: participants.map { it.id }) }
  var selectedPayerId by remember(initialItem, participants) { mutableStateOf(initialItem?.paidByParticipantId ?: participants.firstOrNull()?.id) }
  var payerMenuExpanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(20.dp),
    title = { Text(if (initialItem == null) "Add Dish / Expense" else "Edit Dish / Expense", fontWeight = FontWeight.Bold) },
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

        if (selectedPayerId == participants.firstOrNull()?.id) {
          Text("Payment source", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
          Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { paymentSourceExpanded = true }, modifier = Modifier.fillMaxWidth()) {
              Text(paymentSource, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
              Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = paymentSourceExpanded, onDismissRequest = { paymentSourceExpanded = false }) {
              listOf("Cash", "UPI", "Bank transfer", "Debit / Credit card", "Other").forEach { source ->
                DropdownMenuItem(text = { Text(source) }, onClick = { paymentSource = source; paymentSourceExpanded = false })
              }
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
          onConfirm(title.trim(), parsedPrice, qty, selectedParticipantIds, selectedPayerId, paymentSource)
        },
        enabled = canAdd,
        shape = RoundedCornerShape(10.dp)
      ) {
        Text(if (initialItem == null) "Add Dish" else "Save changes")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
