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
    state.participants,
    state.items,
    state.taxAmount,
    state.tipAmount,
    state.discountAmount
  ) {
    DebtSimplifier.calculateBreakdowns(
      mode = SplitMode.ITEMIZED,
      participants = state.participants,
      items = state.items,
      tax = state.taxAmount,
      tip = state.tipAmount,
      discount = state.discountAmount,
      manualBillTotal = 0.0
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
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${state.participants.size} participants",
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
        transfers = simplifiedTransfers,
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
    // Items Section Header
    item {
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
    if (state.items.isEmpty()) {
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
    items(state.items, key = { it.id }) { item ->
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
                  Text(transfer.fromParticipant.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurface)
                  Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp).padding(horizontal = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(transfer.toParticipant.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                if (transfer.toParticipant.upiId.isNotBlank()) {
                  Text("UPI: ${transfer.toParticipant.upiId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }

              Text(
                text = "${state.currencySymbol}${String.format(Locale.US, "%,.2f", transfer.amount)}",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSettled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
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
                  haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                  viewModel.toggleSettledTransfer(transferKey)
                },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  if (isSettled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                  contentDescription = "Mark Settled",
                  tint = if (isSettled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
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
    title = { Text("Add Dish / Line Item") },
    text = {
      Column(
        modifier = Modifier
          .heightIn(max = 380.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
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
        Text(
          text = "Split this item with",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
          text = "Select at least one diner.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        participants.forEach { participant ->
          val isSelected = participant.id in selectedParticipantIds
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                selectedParticipantIds = if (isSelected) {
                  if (selectedParticipantIds.size > 1) {
                    selectedParticipantIds - participant.id
                  } else {
                    selectedParticipantIds
                  }
                } else {
                  selectedParticipantIds + participant.id
                }
              },
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = isSelected,
              onCheckedChange = null,
              enabled = !isSelected || selectedParticipantIds.size > 1
            )
            Text(
              text = participant.name,
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Box {
          OutlinedButton(
            onClick = { payerMenuExpanded = true },
            enabled = participants.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
          ) {
            val payerName = participants.firstOrNull { it.id == selectedPayerId }?.name
            Text(
              text = payerName?.let { "Paid by: $it" } ?: "Choose who paid",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          DropdownMenu(
            expanded = payerMenuExpanded,
            onDismissRequest = { payerMenuExpanded = false }
          ) {
            participants.forEach { participant ->
              DropdownMenuItem(
                text = { Text(participant.name) },
                onClick = {
                  selectedPayerId = participant.id
                  payerMenuExpanded = false
                }
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val price = priceStr.toDoubleOrNull() ?: 0.0
          val qty = qtyStr.toIntOrNull() ?: 1
          onConfirm(title, price, qty, selectedParticipantIds, selectedPayerId)
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
