package com.example.ui.screens.emi
import com.example.ui.theme.performConfiguredHapticFeedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AmortizationMonth
import com.example.ui.components.ChartSlice
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.DonutChart
import com.example.ui.components.NumberInputField
import com.example.ui.theme.ChartInterest
import com.example.ui.theme.ChartPrincipal
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.screens.loan.LoanTrackerScreen
import com.example.ui.screens.loan.LoanTrackerViewModel

enum class EmiSubTab(val title: String, val icon: ImageVector) {
  CALCULATOR("EMI Calculator", Icons.Default.Calculate),
  TRACKER("Active Loans Tracker", Icons.Default.AccountBalance)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmiScreen(
  viewModel: EmiViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val loanViewModel: LoanTrackerViewModel = viewModel()
  var selectedSubTab by rememberSaveable { mutableStateOf(EmiSubTab.CALCULATOR) }

  // Filter schedule if filter query is provided
  val filteredSchedule = remember(state.emiResult.schedule, state.scheduleFilterQuery) {
    if (state.scheduleFilterQuery.isBlank()) {
      state.emiResult.schedule
    } else {
      val query = state.scheduleFilterQuery.trim()
      state.emiResult.schedule.filter { month ->
        month.monthIndex.toString().contains(query) ||
          (month.monthIndex % 12 == 0 && "yr".contains(query, ignoreCase = true))
      }
    }
  }

  // Prepayment dialog state
  if (state.showPrepaymentDialog) {
    PrepaymentDialog(
      totalMonths = state.emiResult.originalTenureMonths,
      onDismiss = { viewModel.setShowPrepaymentDialog(false) },
      onConfirm = { month, amount ->
        haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.addPrepayment(month, amount)
      }
    )
  }

  Column(modifier = modifier.fillMaxSize()) {
    PrimaryTabRow(
      selectedTabIndex = selectedSubTab.ordinal,
      containerColor = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth().testTag("emi_sub_tabs")
    ) {
      EmiSubTab.values().forEach { subTab ->
        Tab(
          selected = selectedSubTab == subTab,
          onClick = {
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
            selectedSubTab = subTab
          },
          icon = { Icon(subTab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
          text = { Text(subTab.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
          modifier = Modifier.testTag("emi_tab_${subTab.name.lowercase()}")
        )
      }
    }

    when (selectedSubTab) {
      EmiSubTab.CALCULATOR -> {
        Scaffold(
          contentWindowInsets = WindowInsets(0, 0, 0, 0),
          snackbarHost = {
      state.toastMessage?.let { msg ->
        Snackbar(
          modifier = Modifier.padding(16.dp),
          action = {
            TextButton(onClick = { viewModel.clearToast() }) {
              Text("OK", color = MaterialTheme.colorScheme.inversePrimary)
            }
          }
        ) {
          Text(msg)
        }
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Input Form Card
      item {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Loan Parameters",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                  onClick = {
                    haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                      val p = state.principalInput.toDoubleOrNull() ?: 1000000.0
                      val r = state.interestRateInput.toDoubleOrNull() ?: 8.5
                      val t = state.emiResult.tenureMonths
                      val pdf = com.example.domain.report.PdfReportGenerator.generateEmiReport(context, p, r, t, state.emiResult)
                      com.example.domain.report.PdfReportGenerator.sharePdf(context, pdf)
                    } catch (e: Exception) {
                      android.widget.Toast.makeText(context, "Could not generate PDF: ${e.localizedMessage ?: "Unknown error"}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                  },
                  modifier = Modifier.testTag("export_emi_pdf_button")
                ) {
                  Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF Statement")
                }
                FilledTonalIconButton(
                  onClick = {
                    haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.saveCalculation(context)
                  },
                  modifier = Modifier.testTag("save_emi_button")
                ) {
                  Icon(Icons.Default.BookmarkAdd, contentDescription = "Save EMI calculation")
                }
                FilledTonalIconButton(
                  onClick = {
                    haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.shareAmortization(context)
                  },
                  modifier = Modifier.testTag("share_emi_button")
                ) {
                  Icon(Icons.Default.Share, contentDescription = "Share amortization schedule")
                }
              }
            }

            // Principal
            NumberInputField(
              value = state.principalInput,
              onValueChange = { viewModel.updatePrincipal(it) },
              label = "Loan Amount (Principal)",
              prefix = "₹",
              sliderValue = (state.principalInput.toFloatOrNull() ?: 0f).coerceIn(10000f, 10000000f),
              sliderRange = 10000f..10000000f,
              onSliderChange = { viewModel.updatePrincipal(it.toLong().toString()) },
              presets = listOf(
                "+50,000" to 50000.0,
                "+100,000" to 100000.0,
                "+500,000" to 500000.0,
                "+1,000,000" to 1000000.0
              ),
              onPresetClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.addPrincipalPreset(it)
              },
              testTag = "principal_input"
            )

            // Interest Rate with Quick Slabs
            NumberInputField(
              value = state.interestRateInput,
              onValueChange = { viewModel.updateInterestRate(it) },
              label = "Interest Rate (% p.a.)",
              suffix = "%",
              sliderValue = (state.interestRateInput.toFloatOrNull() ?: 8.5f).coerceIn(1f, 30f),
              sliderRange = 1f..30f,
              onSliderChange = { viewModel.updateInterestRate(String.format(java.util.Locale.US, "%.1f", it)) },
              presets = listOf(
                "7.0%" to 7.0,
                "8.5%" to 8.5,
                "10.0%" to 10.0,
                "12.0%" to 12.0
              ),
              onPresetClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.updateInterestRate(String.format(java.util.Locale.US, "%.1f", it))
              },
              testTag = "interest_rate_input"
            )

            // Tenure with unit toggle
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Tenure Duration",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                  selected = true,
                  onClick = {
                    haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleTenureUnit()
                  },
                  label = {
                    Text(if (state.isTenureYears) "Years (Switch to Mo)" else "Months (Switch to Yr)")
                  },
                  leadingIcon = {
                    Icon(
                      Icons.Default.SwapHoriz,
                      contentDescription = "Switch tenure unit",
                      modifier = Modifier.size(16.dp)
                    )
                  },
                  modifier = Modifier.testTag("toggle_tenure_unit_button")
                )
              }

              val maxRange = if (state.isTenureYears) 30f else 360f
              NumberInputField(
                value = state.tenureInput,
                onValueChange = { viewModel.updateTenure(it) },
                label = if (state.isTenureYears) "Tenure in Years" else "Tenure in Months",
                suffix = if (state.isTenureYears) "Years" else "Months",
                sliderValue = (state.tenureInput.toFloatOrNull() ?: 5f).coerceIn(1f, maxRange),
                sliderRange = 1f..maxRange,
                onSliderChange = { viewModel.updateTenure(it.toInt().toString()) },
                presets = if (state.isTenureYears) {
                  listOf("5 Years" to 5.0, "10 Years" to 10.0, "15 Years" to 15.0, "20 Years" to 20.0)
                } else {
                  listOf("60 Months" to 60.0, "120 Months" to 120.0, "180 Months" to 180.0, "240 Months" to 240.0)
                },
                onPresetClick = {
                  haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                  viewModel.updateTenure(it.toInt().toString())
                },
                testTag = "tenure_input"
              )
            }
          }
        }
      }

      // EMI Summary & Donut Chart Card
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "Monthly EMI Payment",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = CurrencyFormatter.format(state.emiResult.monthlyEmi),
              style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp
              ),
              color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            DonutChart(
              slices = listOf(
                ChartSlice("Principal", state.emiResult.principal, ChartPrincipal),
                ChartSlice("Total Interest", state.emiResult.totalInterest, ChartInterest)
              ),
              centerTitle = CurrencyFormatter.formatCompact(state.emiResult.totalPayment),
              centerSubtitle = "Total Payment",
              modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            // Stat Cards Grid
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              StatBox(
                title = "Total Interest",
                value = CurrencyFormatter.format(state.emiResult.totalInterest),
                color = ChartInterest,
                modifier = Modifier.weight(1f)
              )
              StatBox(
                title = "Total Amount",
                value = CurrencyFormatter.format(state.emiResult.totalPayment),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }

      // Pre-Payment / Part-Payment Section
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                  text = "Pre-Payments & Part-Payments",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Simulate early loan payoffs and interest savings",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Button(
                onClick = { viewModel.setShowPrepaymentDialog(true) },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_prepayment_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
              }
            }

            // Savings Highlight if prepayment exists
            if (state.emiResult.interestSaved > 0.0 || state.emiResult.tenureMonthsSaved > 0) {
              Spacer(modifier = Modifier.height(12.dp))
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                  containerColor = Color(0xFFECFDF5)
                ),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    Icons.Default.Celebration,
                    contentDescription = null,
                    tint = Color(0xFF059669),
                    modifier = Modifier.size(28.dp)
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Column {
                    Text(
                      text = "Savings With Prepayment",
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = Color(0xFF065F46)
                    )
                    Text(
                      text = "Interest Saved: ${CurrencyFormatter.format(state.emiResult.interestSaved)} | Tenure cut by ${state.emiResult.tenureMonthsSaved} months!",
                      style = MaterialTheme.typography.bodySmall,
                      color = Color(0xFF047857)
                    )
                  }
                }
              }
            }

            // List of added prepayments
            if (state.prepayments.isNotEmpty()) {
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "Active Prepayments (${state.prepayments.size}):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(6.dp))
              state.prepayments.forEach { p ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Month ${p.month}: ${CurrencyFormatter.format(p.amount)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                  )
                  IconButton(
                    onClick = {
                      haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                      viewModel.removePrepayment(p.id)
                    },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(
                      Icons.Default.DeleteOutline,
                      contentDescription = "Remove prepayment",
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Amortization Schedule Header & Filter
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Amortization Schedule",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "${filteredSchedule.size} months breakdown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            OutlinedTextField(
              value = state.scheduleFilterQuery,
              onValueChange = { viewModel.setFilterQuery(it) },
              placeholder = { Text("Filter Mo#", fontSize = 12.sp) },
              singleLine = true,
              leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
              },
              trailingIcon = {
                if (state.scheduleFilterQuery.isNotEmpty()) {
                  IconButton(onClick = { viewModel.setFilterQuery("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                  }
                }
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .width(150.dp)
                .height(48.dp)
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Table column headers
          AmortizationTableHeader()
        }
      }

      // Month-Wise Amortization Items
      items(
        items = filteredSchedule,
        key = { it.monthIndex }
      ) { monthItem ->
        AmortizationRow(item = monthItem)
      }

      item {
        Spacer(modifier = Modifier.height(32.dp))
      }
    }
  }
      }
      EmiSubTab.TRACKER -> {
        LoanTrackerScreen(
          viewModel = loanViewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}

@Composable
private fun StatBox(
  title: String,
  value: String,
  color: Color,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = color
      )
    }
  }
}

@Composable
private fun AmortizationTableHeader() {
  Card(
    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Mo.", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(32.dp))
      Text("Opening", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
      Text("Principal", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.1f))
      Text("Interest", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.1f))
      Text("Closing", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
    }
  }
}

@Composable
private fun AmortizationRow(item: AmortizationMonth) {
  val isYearEnd = item.monthIndex % 12 == 0
  val bg = if (item.monthIndex % 2 == 0) {
    MaterialTheme.colorScheme.surface
  } else {
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
  }

  val openingStr = remember(item.openingBalance) { CurrencyFormatter.formatWhole(item.openingBalance, "") }
  val principalStr = remember(item.principalPaid) { CurrencyFormatter.formatWhole(item.principalPaid, "") }
  val interestStr = remember(item.interestPaid) { CurrencyFormatter.formatWhole(item.interestPaid, "") }
  val closingStr = remember(item.closingBalance) { CurrencyFormatter.formatWhole(item.closingBalance, "") }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(bg)
      .padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(modifier = Modifier.width(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = item.monthIndex.toString(),
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary
        )
      }
      Text(
        text = openingStr,
        fontSize = 11.sp,
        modifier = Modifier.weight(1.3f)
      )
      Text(
        text = principalStr,
        fontSize = 11.sp,
        color = ChartPrincipal,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.weight(1.1f)
      )
      Text(
        text = interestStr,
        fontSize = 11.sp,
        color = ChartInterest,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.weight(1.1f)
      )
      Text(
        text = closingStr,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1.3f)
      )
    }

    if (item.prepayment > 0.0) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.End
      ) {
        SuggestionChip(
          onClick = {},
          label = {
            Text(
              "Part-Payment: ${CurrencyFormatter.format(item.prepayment)}",
              fontSize = 10.sp,
              color = Color(0xFF047857)
            )
          },
          colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFD1FAE5)),
          border = null,
          modifier = Modifier.height(24.dp)
        )
      }
    }

    if (isYearEnd) {
      HorizontalDivider(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        thickness = 1.dp,
        modifier = Modifier.padding(top = 6.dp)
      )
    }
  }
}

@Composable
private fun PrepaymentDialog(
  totalMonths: Int,
  onDismiss: () -> Unit,
  onConfirm: (month: Int, amount: Double) -> Unit
) {
  var monthStr by remember { mutableStateOf("12") }
  var amountStr by remember { mutableStateOf("50000") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Prepayment / Part-Payment") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          "Enter the month index when you want to make a lump sum prepayment, and the amount.",
          style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
          value = monthStr,
          onValueChange = { monthStr = it.filter { c -> c.isDigit() } },
          label = { Text("Month Number (1 to $totalMonths)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = amountStr,
          onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
          label = { Text("Prepayment Amount (₹)") },
          prefix = { Text("₹ ") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val m = monthStr.toIntOrNull() ?: 1
          val a = amountStr.toDoubleOrNull() ?: 0.0
          if (m > 0 && a > 0.0) {
            onConfirm(m, a)
          }
        }
      ) {
        Text("Apply")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
