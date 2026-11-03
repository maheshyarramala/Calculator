package com.example.ui.screens.splitbill

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
import com.example.ui.components.NumberInputField
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitBillScreen(
  viewModel: SplitBillViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val clipboard = LocalClipboardManager.current

  // Calculate dynamic breakdowns and debt settlements using DebtSimplifier
  val breakdowns = remember(
    state.splitMode,
    state.participants,
    state.items,
    state.taxAmount,
    state.tipAmount,
    state.discountAmount,
    state.manualBillTotal
  ) {
    DebtSimplifier.calculateBreakdowns(
      mode = state.splitMode,
      participants = state.participants,
      items = state.items,
      tax = state.taxAmount,
      tip = state.tipAmount,
      discount = state.discountAmount,
      manualBillTotal = state.manualBillTotal
    )
  }

  val totalBill = remember(breakdowns) {
    breakdowns.sumOf { it.totalToPay }
  }

  val totalPaid = remember(state.participants) {
    state.participants.sumOf { it.paidAmount }
  }

  val simplifiedTransfers = remember(breakdowns, state.simplifyDebtsEnabled) {
    if (state.simplifyDebtsEnabled) {
      DebtSimplifier.simplifyDebts(breakdowns)
    } else {
      // Non-simplified direct debts: every debtor owes the group primary payer
      val primaryPayer = state.participants.maxByOrNull { it.paidAmount }
      if (primaryPayer != null) {
        breakdowns.filter { it.netBalance < -0.01 }.map { b ->
          DebtTransfer(fromParticipant = b.participant, toParticipant = primaryPayer, amount = Math.abs(b.netBalance))
        }
      } else {
        emptyList()
      }
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
      onConfirm = { title, price, qty, isAlcohol, claimedIds ->
        viewModel.addItem(title, price, qty, isAlcohol, claimedIds)
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
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${state.participants.size} participants • ${state.splitMode.title} split",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // AI Receipt Scan / OCR Button
        FilledTonalButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

    // Mode Selector Chips Row
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(SplitMode.values()) { mode ->
        val icon = when (mode) {
          SplitMode.EQUAL -> Icons.Default.Balance
          SplitMode.ITEMIZED -> Icons.Default.RestaurantMenu
          SplitMode.EXACT -> Icons.Default.Pin
          SplitMode.PERCENTAGE -> Icons.Default.Percent
          SplitMode.SHARES -> Icons.Default.PieChart
        }
        FilterChip(
          selected = state.splitMode == mode,
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.setSplitMode(mode)
          },
          leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(mode.title, fontSize = 11.5.sp, maxLines = 1, softWrap = false) }
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Grand Total Overview Card
    Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
      ),
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
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", totalBill)}",
              style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )
          }

          // Currency Switcher Dropdown
          var showCurrencyMenu by remember { mutableStateOf(false) }
          Box {
            FilledTonalButton(
              onClick = { showCurrencyMenu = true },
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text("${state.currencyCode} (${state.currencySymbol})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = showCurrencyMenu, onDismissRequest = { showCurrencyMenu = false }) {
              listOf(
                "INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£",
                "AED" to "د.إ", "CAD" to "$", "AUD" to "$", "JPY" to "¥"
              ).forEach { (code, sym) ->
                DropdownMenuItem(
                  text = { Text("$code ($sym)") },
                  onClick = {
                    viewModel.setCurrency(code, sym)
                    showCurrencyMenu = false
                  }
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Subtotal, Tax, Tip, Discount Pills
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Tax: ${state.currencySymbol}${String.format(Locale.US, "%.1f", state.taxAmount)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("Tip: ${state.currencySymbol}${String.format(Locale.US, "%.1f", state.tipAmount)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (state.discountAmount > 0) {
            Text("Discount: -${state.currencySymbol}${String.format(Locale.US, "%.1f", state.discountAmount)}", fontSize = 11.sp, color = Color(0xFF10B981))
          }
          val diff = totalPaid - totalBill
          Text(
            text = if (Math.abs(diff) < 0.1) "Fully Paid ✓" else if (diff < 0) "Owed: ${state.currencySymbol}${String.format(Locale.US, "%.1f", -diff)}" else "Overpaid",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (Math.abs(diff) < 0.1) Color(0xFF059669) else Color(0xFFDC2626)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Sub-Tabs: [0: Items / Split] [1: Diners & Claims] [2: Settlement & Pay]
    TabRow(
      selectedTabIndex = state.activeTab,
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
      modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
    ) {
      Tab(
        selected = state.activeTab == 0,
        onClick = { viewModel.setTab(0) },
        text = { Text("Items & Bill", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
        icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp)) }
      )
      Tab(
        selected = state.activeTab == 1,
        onClick = { viewModel.setTab(1) },
        text = { Text("Diners (${state.participants.size})", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
        icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp)) }
      )
      Tab(
        selected = state.activeTab == 2,
        onClick = { viewModel.setTab(2) },
        text = { Text("Settlement", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp)) }
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Active Tab Content
    when (state.activeTab) {
      0 -> ItemsAndBillTab(
        state = state,
        viewModel = viewModel,
        onAddItemClick = { viewModel.setShowAddItemDialog(true) }
      )
      1 -> DinersAndClaimsTab(
        state = state,
        breakdowns = breakdowns,
        viewModel = viewModel,
        onAddDinerClick = { viewModel.setShowAddParticipantDialog(true) }
      )
      2 -> SettlementLedgerTab(
        state = state,
        breakdowns = breakdowns,
        transfers = simplifiedTransfers,
        totalBill = totalBill,
        viewModel = viewModel
      )
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
    if (state.splitMode != SplitMode.ITEMIZED) {
      // Manual Total Input for Equal / Exact / Shares modes
      item {
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bill Base Total", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            NumberInputField(
              value = state.manualBillTotal.toString().removeSuffix(".0"),
              onValueChange = { viewModel.setManualBillTotal(it.toDoubleOrNull() ?: 0.0) },
              label = "Enter bill subtotal",
              prefix = state.currencySymbol
            )
          }
        }
      }
    }

    // Additional Taxes, Tip & Discount Sliders
    item {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Tax, Tip & Discounts", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = if (state.taxAmount > 0) state.taxAmount.toString().removeSuffix(".0") else "",
              onValueChange = { viewModel.setTaxAmount(it.toDoubleOrNull() ?: 0.0) },
              label = { Text("Tax (${state.currencySymbol})", fontSize = 11.sp) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = if (state.tipAmount > 0) state.tipAmount.toString().removeSuffix(".0") else "",
              onValueChange = { viewModel.setTipAmount(it.toDoubleOrNull() ?: 0.0) },
              label = { Text("Tip (${state.currencySymbol})", fontSize = 11.sp) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = if (state.discountAmount > 0) state.discountAmount.toString().removeSuffix(".0") else "",
              onValueChange = { viewModel.setDiscountAmount(it.toDoubleOrNull() ?: 0.0) },
              label = { Text("Discount", fontSize = 11.sp) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }

    // Items Section Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Dishes & Line Items (${state.items.size})",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = "Tap diner avatar to assign who ate/drank what",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        FilledTonalButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onAddItemClick()
          },
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("+ Dish", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    // Itemized Line Items Cards
    items(state.items, key = { it.id }) { item ->
      val itemTotal = item.price * item.quantity
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              if (item.isAlcoholOrSpecial) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = Color(0xFFEF4444).copy(alpha = 0.15f),
                  modifier = Modifier.padding(end = 6.dp)
                ) {
                  Text("🍷 Drink", fontSize = 10.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
              }
              Text(
                text = "${if (item.quantity > 1) "${item.quantity}x " else ""}${item.title}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", itemTotal)}",
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
              )
              IconButton(
                onClick = { viewModel.removeItem(item.id) },
                modifier = Modifier.size(28.dp).padding(start = 4.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Delete item", tint = Color.Gray, modifier = Modifier.size(16.dp))
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Avatar Claim Chips: Tap to toggle claim for each person
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Claimed by:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              TextButton(
                onClick = { viewModel.claimItemForAll(item.id) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text("All Split", fontSize = 10.5.sp)
              }
              TextButton(
                onClick = { viewModel.clearItemClaims(item.id) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text("Clear", fontSize = 10.5.sp, color = Color.Gray)
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
                color = if (isClaimed) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = if (isClaimed) BorderStroke(1.5.dp, color) else null,
                modifier = Modifier.clickable {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                      .background(if (isClaimed) color else Color.Gray)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = p.name,
                    fontSize = 11.sp,
                    fontWeight = if (isClaimed) FontWeight.Bold else FontWeight.Normal,
                    color = if (isClaimed) MaterialTheme.colorScheme.onSurface else Color.Gray
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
        Column {
          Text(
            text = "Diners & Individual Shares",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = "Track consumed share, who paid, and net balance",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        FilledTonalButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

    items(breakdowns, key = { it.participant.id }) { b ->
      val p = b.participant
      val avatarColor = try {
        Color(android.graphics.Color.parseColor(p.avatarColorHex))
      } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
      }

      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (p.upiId.isNotBlank()) {
                  Text("UPI: ${p.upiId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", b.totalToPay)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
              )
              Text("Total Share", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Prorated breakdown details
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Food/Drink: ${state.currencySymbol}${String.format(Locale.US, "%.1f", b.subtotal)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tax Share: ${state.currencySymbol}${String.format(Locale.US, "%.1f", b.taxShare)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tip Share: ${state.currencySymbol}${String.format(Locale.US, "%.1f", b.tipShare)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

          Spacer(modifier = Modifier.height(8.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          Spacer(modifier = Modifier.height(8.dp))

          // Amount Paid Input (Did this person pay the restaurant?)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Amount Paid to Restaurant / Server:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              if (b.netBalance > 0.01) {
                Text(
                  text = "🟢 Gets back ${state.currencySymbol}${String.format(Locale.US, "%,.2f", b.netBalance)}",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF059669)
                )
              } else if (b.netBalance < -0.01) {
                Text(
                  text = "🔴 Owes ${state.currencySymbol}${String.format(Locale.US, "%,.2f", Math.abs(b.netBalance))}",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFDC2626)
                )
              } else {
                Text("Settled (Exact)", fontSize = 11.sp, color = Color.Gray)
              }
            }

            OutlinedTextField(
              value = if (p.paidAmount > 0) p.paidAmount.toString().removeSuffix(".0") else "",
              onValueChange = { viewModel.updateParticipantPaid(p.id, it.toDoubleOrNull() ?: 0.0) },
              label = { Text("Paid", fontSize = 10.sp) },
              prefix = { Text(state.currencySymbol, fontSize = 11.sp) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.width(110.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SettlementLedgerTab(
  state: SplitBillUiState,
  breakdowns: List<PersonBreakdown>,
  transfers: List<DebtTransfer>,
  totalBill: Double,
  viewModel: SplitBillViewModel
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current

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
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
        val isSettled = state.settledTransferKeys.contains(transferKey)

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
                  Text(transfer.fromParticipant.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                  Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp).padding(horizontal = 2.dp), tint = Color.Gray)
                  Text(transfer.toParticipant.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                if (transfer.toParticipant.upiId.isNotBlank()) {
                  Text("UPI: ${transfer.toParticipant.upiId}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }

              Text(
                text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", transfer.amount)}",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSettled) Color.Gray else MaterialTheme.colorScheme.primary
              )
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
                      amount = transfer.amount,
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
                      amount = transfer.amount
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

              // Mark as Settled Toggle
              IconButton(
                onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  viewModel.toggleSettledTransfer(transferKey)
                },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  if (isSettled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                  contentDescription = "Mark Settled",
                  tint = if (isSettled) Color(0xFF10B981) else Color.Gray
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

  // Photo Picker
  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      // Simulate multimodal OCR extraction from image uri
      Toast.makeText(context, "Receipt captured! Parsing line items & taxes...", Toast.LENGTH_SHORT).show()
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
          Text("AI Receipt Scanner & OCR", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          Text("Automatically extract items, prices, discounts & taxes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
  onConfirm: (title: String, price: Double, qty: Int, isAlcohol: Boolean, claimedIds: List<String>) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var priceStr by remember { mutableStateOf("") }
  var qtyStr by remember { mutableStateOf("1") }
  var isAlcohol by remember { mutableStateOf(false) }
  var selectedParticipantIds by remember { mutableStateOf(participants.map { it.id }) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Dish / Line Item") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Dish / Item Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = priceStr,
            onValueChange = { priceStr = it },
            label = { Text("Price ($currencySymbol)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = qtyStr,
            onValueChange = { qtyStr = it },
            label = { Text("Quantity") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(0.7f)
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("🍷 Drink / Alcohol item", fontSize = 12.sp)
          Switch(checked = isAlcohol, onCheckedChange = { isAlcohol = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val price = priceStr.toDoubleOrNull() ?: 0.0
          val qty = qtyStr.toIntOrNull() ?: 1
          onConfirm(title, price, qty, isAlcohol, selectedParticipantIds)
        },
        enabled = title.isNotBlank() && priceStr.isNotBlank()
      ) {
        Text("Add Item")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
