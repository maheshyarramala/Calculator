package com.example.ui.screens.investment
import com.example.ui.theme.performConfiguredHapticFeedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.calculator.InvestmentCalculators
import com.example.ui.components.*
import com.example.ui.theme.ChartInvested
import com.example.ui.theme.ChartWealthGain

@Composable
private fun TradeChargeRow(label: String, value: String, isBold: Boolean = false) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold
      ),
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentScreen(
  viewModel: InvestmentViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  var tradeBuyPrice by remember { mutableStateOf("500") }
  var tradeSellPrice by remember { mutableStateOf("525") }
  var tradeQuantity by remember { mutableStateOf("100") }
  var isIntraday by remember { mutableStateOf(false) }
  val parsedBuyPrice = tradeBuyPrice.toDoubleOrNull()
  val parsedSellPrice = tradeSellPrice.toDoubleOrNull()
  val parsedQuantity = tradeQuantity.toIntOrNull()
  val isTradeInputValid = parsedBuyPrice?.let { it.isFinite() && it > 0.0 } == true &&
    parsedSellPrice?.let { it.isFinite() && it > 0.0 } == true &&
    parsedQuantity?.let { it > 0 } == true
  val tradeResult = remember(tradeBuyPrice, tradeSellPrice, tradeQuantity, isIntraday) {
    InvestmentCalculators.calculateEquityTradePnl(
      buyPrice = parsedBuyPrice ?: 0.0,
      sellPrice = parsedSellPrice ?: 0.0,
      quantity = parsedQuantity ?: 0,
      isIntraday = isIntraday
    )
  }

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
      // Top Tab Selector
      item {
        ScrollableTabRow(
          selectedTabIndex = state.selectedTab.ordinal,
          edgePadding = 0.dp,
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("investment_tabs")
        ) {
          InvestmentTab.values().forEach { tab ->
            val icon = when (tab) {
              InvestmentTab.SIP -> Icons.AutoMirrored.Filled.TrendingUp
              InvestmentTab.LUMPSUM -> Icons.Default.Payments
              InvestmentTab.COMPOUND -> Icons.Default.Percent
              InvestmentTab.PPF -> Icons.Default.AccountBalance
              InvestmentTab.FD -> Icons.Default.Savings
              InvestmentTab.TRADE -> Icons.AutoMirrored.Filled.TrendingUp
            }
            Tab(
              selected = state.selectedTab == tab,
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.selectTab(tab)
              },
              icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
              text = {
                Text(
                  text = tab.title,
                  fontWeight = if (state.selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                  fontSize = 11.sp
                )
              }
            )
          }
        }
      }

      // Input Form for Active Investment Tab
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                text = "${state.selectedTab.title} Calculator",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              if (state.selectedTab != InvestmentTab.TRADE) {
                FilledTonalIconButton(
                  onClick = {
                    haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.saveCalculation(context)
                  },
                  modifier = Modifier.testTag("save_investment_button")
                ) {
                  Icon(Icons.Default.BookmarkAdd, contentDescription = "Save investment")
                }
              }
            }

            when (state.selectedTab) {
              InvestmentTab.SIP -> {
                NumberInputField(
                  value = state.sipMonthly,
                  onValueChange = { viewModel.updateSip(monthly = it) },
                  label = "Monthly Investment (SIP)",
                  prefix = "₹",
                  sliderValue = (state.sipMonthly.toFloatOrNull() ?: 1000f).coerceIn(500f, 200000f),
                  sliderRange = 500f..200000f,
                  onSliderChange = { viewModel.updateSip(monthly = it.toLong().toString()) },
                  presets = listOf("+1,000" to 1000.0, "+5,000" to 5000.0, "+10,000" to 10000.0, "+25,000" to 25000.0),
                  onPresetClick = {
                    val cur = state.sipMonthly.toDoubleOrNull() ?: 0.0
                    viewModel.updateSip(monthly = (cur + it).toLong().toString())
                  },
                  testTag = "sip_monthly_input"
                )

                NumberInputField(
                  value = state.sipRate,
                  onValueChange = { viewModel.updateSip(rate = it) },
                  label = "Expected Return Rate (% p.a.)",
                  suffix = "%",
                  sliderValue = (state.sipRate.toFloatOrNull() ?: 12f).coerceIn(1f, 30f),
                  sliderRange = 1f..30f,
                  onSliderChange = { viewModel.updateSip(rate = String.format(java.util.Locale.US, "%.1f", it)) },
                  presets = listOf("10.0%" to 10.0, "12.0%" to 12.0, "15.0%" to 15.0, "18.0%" to 18.0),
                  onPresetClick = { viewModel.updateSip(rate = String.format(java.util.Locale.US, "%.1f", it)) },
                  testTag = "sip_rate_input"
                )

                NumberInputField(
                  value = state.sipYears,
                  onValueChange = { viewModel.updateSip(years = it) },
                  label = "Time Period (Years)",
                  suffix = "Yr",
                  sliderValue = (state.sipYears.toFloatOrNull() ?: 10f).coerceIn(1f, 40f),
                  sliderRange = 1f..40f,
                  onSliderChange = { viewModel.updateSip(years = it.toInt().toString()) },
                  presets = listOf("5 Years" to 5.0, "10 Years" to 10.0, "15 Years" to 15.0, "20 Years" to 20.0),
                  onPresetClick = { viewModel.updateSip(years = it.toInt().toString()) },
                  testTag = "sip_years_input"
                )
              }

              InvestmentTab.LUMPSUM -> {
                NumberInputField(
                  value = state.lumpPrincipal,
                  onValueChange = { viewModel.updateLump(principal = it) },
                  label = "Total Investment Amount",
                  prefix = "₹",
                  sliderValue = (state.lumpPrincipal.toFloatOrNull() ?: 10000f).coerceIn(5000f, 5000000f),
                  sliderRange = 5000f..5000000f,
                  onSliderChange = { viewModel.updateLump(principal = it.toLong().toString()) },
                  presets = listOf("+25,000" to 25000.0, "+50,000" to 50000.0, "+100,000" to 100000.0, "+500,000" to 500000.0),
                  onPresetClick = {
                    val cur = state.lumpPrincipal.toDoubleOrNull() ?: 0.0
                    viewModel.updateLump(principal = (cur + it).toLong().toString())
                  },
                  testTag = "lump_principal_input"
                )

                NumberInputField(
                  value = state.lumpRate,
                  onValueChange = { viewModel.updateLump(rate = it) },
                  label = "Expected Return Rate (% p.a.)",
                  suffix = "%",
                  sliderValue = (state.lumpRate.toFloatOrNull() ?: 12f).coerceIn(1f, 30f),
                  sliderRange = 1f..30f,
                  onSliderChange = { viewModel.updateLump(rate = String.format(java.util.Locale.US, "%.1f", it)) },
                  presets = listOf("10.0%" to 10.0, "12.0%" to 12.0, "15.0%" to 15.0, "18.0%" to 18.0),
                  onPresetClick = { viewModel.updateLump(rate = String.format(java.util.Locale.US, "%.1f", it)) },
                  testTag = "lump_rate_input"
                )

                NumberInputField(
                  value = state.lumpYears,
                  onValueChange = { viewModel.updateLump(years = it) },
                  label = "Time Period (Years)",
                  suffix = "Yr",
                  sliderValue = (state.lumpYears.toFloatOrNull() ?: 5f).coerceIn(1f, 40f),
                  sliderRange = 1f..40f,
                  onSliderChange = { viewModel.updateLump(years = it.toInt().toString()) },
                  testTag = "lump_years_input"
                )
              }

              InvestmentTab.COMPOUND -> {
                NumberInputField(
                  value = state.ciPrincipal,
                  onValueChange = { viewModel.updateCi(principal = it) },
                  label = "Principal Deposit",
                  prefix = "₹",
                  sliderValue = (state.ciPrincipal.toFloatOrNull() ?: 10000f).coerceIn(1000f, 5000000f),
                  sliderRange = 1000f..5000000f,
                  onSliderChange = { viewModel.updateCi(principal = it.toLong().toString()) },
                  testTag = "ci_principal_input"
                )

                NumberInputField(
                  value = state.ciRate,
                  onValueChange = { viewModel.updateCi(rate = it) },
                  label = "Annual Interest Rate (%)",
                  suffix = "%",
                  sliderValue = (state.ciRate.toFloatOrNull() ?: 8f).coerceIn(1f, 30f),
                  sliderRange = 1f..30f,
                  onSliderChange = { viewModel.updateCi(rate = String.format(java.util.Locale.US, "%.1f", it)) },
                  testTag = "ci_rate_input"
                )

                NumberInputField(
                  value = state.ciYears,
                  onValueChange = { viewModel.updateCi(years = it) },
                  label = "Time Period (Years)",
                  suffix = "Yr",
                  sliderValue = (state.ciYears.toFloatOrNull() ?: 5f).coerceIn(1f, 40f),
                  sliderRange = 1f..40f,
                  onSliderChange = { viewModel.updateCi(years = it.toInt().toString()) },
                  testTag = "ci_years_input"
                )

                Text("Compounding Frequency", style = MaterialTheme.typography.bodySmall)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  InvestmentCalculators.CompoundingFrequency.values().forEach { freq ->
                    FilterChip(
                      selected = state.ciFrequency == freq,
                      onClick = {
                        haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updateCi(freq = freq)
                      },
                      label = { Text(freq.title, fontSize = 11.sp) }
                    )
                  }
                }
              }

              InvestmentTab.PPF -> {
                NumberInputField(
                  value = state.ppfAnnualDeposit,
                  onValueChange = { viewModel.updatePpf(annual = it) },
                  label = "Annual Investment (Max ₹1.5L / yr)",
                  prefix = "₹",
                  sliderValue = (state.ppfAnnualDeposit.toFloatOrNull() ?: 50000f).coerceIn(500f, 150000f),
                  sliderRange = 500f..150000f,
                  onSliderChange = { viewModel.updatePpf(annual = it.toLong().toString()) },
                  presets = listOf("₹50,000" to 50000.0, "₹100,000" to 100000.0, "₹150,000" to 150000.0),
                  onPresetClick = { viewModel.updatePpf(annual = it.toLong().toString()) },
                  testTag = "ppf_deposit_input"
                )

                NumberInputField(
                  value = state.ppfYears,
                  onValueChange = { viewModel.updatePpf(years = it) },
                  label = "Tenure (Statutory 15 Years)",
                  suffix = "Yr",
                  sliderValue = (state.ppfYears.toFloatOrNull() ?: 15f).coerceIn(15f, 25f),
                  sliderRange = 15f..25f,
                  onSliderChange = { viewModel.updatePpf(years = it.toInt().toString()) },
                  testTag = "ppf_years_input"
                )

                Text(
                  text = "Statutory Government PPF interest rate is fixed at 7.1% p.a. (Tax-free under EEE exemption).",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              InvestmentTab.FD -> {
                NumberInputField(
                  value = state.fdDeposit,
                  onValueChange = { viewModel.updateFd(deposit = it) },
                  label = "Deposit Amount",
                  prefix = "₹",
                  sliderValue = (state.fdDeposit.toFloatOrNull() ?: 50000f).coerceIn(5000f, 5000000f),
                  sliderRange = 5000f..5000000f,
                  onSliderChange = { viewModel.updateFd(deposit = it.toLong().toString()) },
                  presets = listOf("+50,000" to 50000.0, "+100,000" to 100000.0, "+500,000" to 500000.0),
                  onPresetClick = {
                    val cur = state.fdDeposit.toDoubleOrNull() ?: 0.0
                    viewModel.updateFd(deposit = (cur + it).toLong().toString())
                  },
                  testTag = "fd_deposit_input"
                )

                NumberInputField(
                  value = state.fdRate,
                  onValueChange = { viewModel.updateFd(rate = it) },
                  label = "Interest Rate (% p.a.)",
                  suffix = "%",
                  sliderValue = (state.fdRate.toFloatOrNull() ?: 7.5f).coerceIn(1f, 15f),
                  sliderRange = 1f..15f,
                  onSliderChange = { viewModel.updateFd(rate = String.format(java.util.Locale.US, "%.1f", it)) },
                  testTag = "fd_rate_input"
                )

                NumberInputField(
                  value = state.fdYears,
                  onValueChange = { viewModel.updateFd(years = it) },
                  label = "Tenure Duration (Years)",
                  suffix = "Yr",
                  sliderValue = (state.fdYears.toFloatOrNull() ?: 3f).coerceIn(0.5f, 10f),
                  sliderRange = 0.5f..10f,
                  onSliderChange = { viewModel.updateFd(years = String.format(java.util.Locale.US, "%.1f", it)) },
                  testTag = "fd_years_input"
                )
              }

              InvestmentTab.TRADE -> {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  FilterChip(
                    selected = !isIntraday,
                    onClick = { isIntraday = false },
                    label = { Text("Delivery (CNC)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                  )
                  FilterChip(
                    selected = isIntraday,
                    onClick = { isIntraday = true },
                    label = { Text("Intraday (MIS)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                  )
                }

                NumberInputField(
                  value = tradeBuyPrice,
                  onValueChange = { tradeBuyPrice = it },
                  label = "Buy Price",
                  prefix = "₹"
                )
                NumberInputField(
                  value = tradeSellPrice,
                  onValueChange = { tradeSellPrice = it },
                  label = "Sell Price",
                  prefix = "₹"
                )
                NumberInputField(
                  value = tradeQuantity,
                  onValueChange = { tradeQuantity = it },
                  label = "Quantity (Shares)"
                )
              }
            }
          }
        }
      }

      // Output Results & Donut Chart
      if (state.selectedTab == InvestmentTab.TRADE) {
        item {
          val isProfit = tradeResult.netPnl >= 0.0
          val profitGreen = Color(0xFF10B981)
          val lossRed = Color(0xFFEF4444)
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (!isTradeInputValid) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              } else if (isProfit) {
                profitGreen.copy(alpha = 0.1f)
              } else {
                lossRed.copy(alpha = 0.1f)
              }
            ),
            border = BorderStroke(
              1.dp,
              if (!isTradeInputValid) {
                MaterialTheme.colorScheme.outlineVariant
              } else if (isProfit) {
                profitGreen.copy(alpha = 0.35f)
              } else {
                lossRed.copy(alpha = 0.35f)
              }
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(20.dp)) {
              Text(
                text = if (!isTradeInputValid) "ENTER VALID TRADE DETAILS"
                  else if (isProfit) "ESTIMATED NET PROFIT" else "ESTIMATED NET LOSS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (!isTradeInputValid) MaterialTheme.colorScheme.onSurfaceVariant
                  else if (isProfit) profitGreen else lossRed
              )
              if (isTradeInputValid) {
                Text(
                  text = CurrencyFormatter.format(tradeResult.netPnl),
                  style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp
                  ),
                  color = if (isProfit) profitGreen else lossRed
                )
              }
              Spacer(modifier = Modifier.height(14.dp))
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              Spacer(modifier = Modifier.height(14.dp))

              if (isTradeInputValid) {
                TradeChargeRow("Gross P&L", CurrencyFormatter.format(tradeResult.grossPnl))
                TradeChargeRow("Brokerage Fees", CurrencyFormatter.format(tradeResult.brokerage))
                TradeChargeRow("STT / CTT", CurrencyFormatter.format(tradeResult.stt))
                TradeChargeRow("Exchange Txn Charges", CurrencyFormatter.format(tradeResult.exchangeTxnCharge))
                TradeChargeRow("SEBI Turnover Fee", CurrencyFormatter.format(tradeResult.sebiCharge))
                TradeChargeRow("Stamp Duty", CurrencyFormatter.format(tradeResult.stampDuty))
                TradeChargeRow("GST (18%)", CurrencyFormatter.format(tradeResult.gst))
                if (!isIntraday) {
                  TradeChargeRow("DP Charges", CurrencyFormatter.format(tradeResult.dpCharge))
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))
                TradeChargeRow(
                  "Total Government & Broker Charges",
                  CurrencyFormatter.format(tradeResult.totalCharges),
                  isBold = true
                )
                TradeChargeRow(
                  "Breakeven Price per Share",
                  CurrencyFormatter.format(tradeResult.breakevenPerShare),
                  isBold = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Estimated charges; actual rates may vary by broker and current regulations.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      } else item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = Color(0xFF10B981).copy(alpha = 0.08f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "Total Maturity Value",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = CurrencyFormatter.format(state.result.totalValue),
              style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp
              ),
              color = Color(0xFF059669)
            )

            Spacer(modifier = Modifier.height(16.dp))

            DonutChart(
              slices = listOf(
                ChartSlice("Invested", state.result.investedAmount, ChartInvested),
                ChartSlice("Wealth Gain", state.result.estimatedReturns, ChartWealthGain)
              ),
              centerTitle = CurrencyFormatter.formatCompact(state.result.totalValue),
              centerSubtitle = "Total Return",
              modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text("Total Invested", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = CurrencyFormatter.format(state.result.investedAmount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ChartInvested
                  )
                }
              }

              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text("Est. Returns", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = CurrencyFormatter.format(state.result.estimatedReturns),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ChartWealthGain
                  )
                }
              }

            }
          }
        }
      }

      // Graphical Growth Projection Line Chart
      if (state.result.yearlyBreakdown.isNotEmpty()) {
        item {
          GrowthLineChart(breakdown = state.result.yearlyBreakdown)
        }

        // Yearly Compounding Table
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "Yearly Compounding Breakdown",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Spacer(modifier = Modifier.height(8.dp))

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                  .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Year", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                Text("Invested", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.4f))
                Text("Returns", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.4f))
                Text("Balance", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
              }

              state.result.yearlyBreakdown.forEach { item ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("Yr ${item.year}", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.8f))
                  Text(CurrencyFormatter.formatCompact(item.invested, ""), fontSize = 11.sp, color = ChartInvested, modifier = Modifier.weight(1.4f))
                  Text(CurrencyFormatter.formatCompact(item.returns, ""), fontSize = 11.sp, color = ChartWealthGain, modifier = Modifier.weight(1.4f))
                  Text(CurrencyFormatter.formatCompact(item.balance, ""), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                }
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(32.dp))
      }
    }
  }
}
