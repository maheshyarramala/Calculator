package com.example.ui.screens.interest
import com.example.ui.theme.performConfiguredHapticFeedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.calculator.InterestCalculator
import com.example.ui.components.ChartSlice
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.DonutChart
import com.example.ui.components.NumberInputField
import com.example.ui.theme.ChartInterest
import com.example.ui.theme.ChartPrincipal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterestCalculatorScreen(
  viewModel: InterestCalculatorViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current

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
      // Sub-Tabs for all Interest Methods with icons
      item {
        ScrollableTabRow(
          selectedTabIndex = state.selectedMethod.ordinal,
          edgePadding = 0.dp,
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("interest_methods_tabs")
        ) {
          InterestMethod.values().forEach { method ->
            val icon = when (method) {
              InterestMethod.SIMPLE -> Icons.Default.Savings
              InterestMethod.COMPOUND -> Icons.AutoMirrored.Filled.TrendingUp
              InterestMethod.DOUBLING -> Icons.Default.Speed
              InterestMethod.EAR -> Icons.Default.Percent
              InterestMethod.FLAT_VS_REDUCING -> Icons.AutoMirrored.Filled.CompareArrows
            }
            Tab(
              selected = state.selectedMethod == method,
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.selectMethod(method)
              },
              icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
              text = {
                Text(
                  text = method.title,
                  fontWeight = if (state.selectedMethod == method) FontWeight.Bold else FontWeight.Normal,
                  fontSize = 11.sp
                )
              }
            )
          }
        }
      }

      // Method Content
      when (state.selectedMethod) {
        InterestMethod.SIMPLE -> {
          item { SimpleInterestSection(viewModel = viewModel) }
        }
        InterestMethod.COMPOUND -> {
          item { CompoundInterestSection(viewModel = viewModel) }
        }
        InterestMethod.DOUBLING -> {
          item { DoublingPeriodSection(viewModel = viewModel) }
        }
        InterestMethod.EAR -> {
          item { EffectiveRateSection(viewModel = viewModel) }
        }
        InterestMethod.FLAT_VS_REDUCING -> {
          item { FlatVsReducingSection(viewModel = viewModel) }
        }
      }

      item {
        Spacer(modifier = Modifier.height(32.dp))
      }
    }
  }
}

@Composable
private fun SimpleInterestSection(viewModel: InterestCalculatorViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val res = state.siResult

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Simple Interest (SI)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.saveCalculation(context)
            }
          ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save calculation")
          }
        }

        NumberInputField(
          value = state.siPrincipal,
          onValueChange = { viewModel.updateSi(principal = it) },
          label = "Principal Amount",
          prefix = "₹",
          presets = listOf("+50,000" to 50000.0, "+100,000" to 100000.0, "+500,000" to 500000.0),
          onPresetClick = {
            val cur = state.siPrincipal.toDoubleOrNull() ?: 0.0
            viewModel.updateSi(principal = (cur + it).toLong().toString())
          }
        )

        NumberInputField(
          value = state.siRate,
          onValueChange = { viewModel.updateSi(rate = it) },
          label = "Interest Rate (% p.a.)",
          suffix = "%"
        )

        NumberInputField(
          value = state.siTime,
          onValueChange = { viewModel.updateSi(time = it) },
          label = "Time Duration",
          modifier = Modifier.fillMaxWidth()
        )

        Column(modifier = Modifier.fillMaxWidth()) {
          Text("Time Unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            InterestCalculator.TimeUnit.values().forEach { unit ->
              FilterChip(
                selected = state.siTimeUnit == unit,
                onClick = {
                  haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                  viewModel.updateSi(unit = unit)
                },
                label = {
                  Text(
                    text = unit.title,
                    fontSize = 12.sp,
                    fontWeight = if (state.siTimeUnit == unit) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                  )
                },
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }
    }

    // Result Card with Donut Chart
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Total Maturity Amount", style = MaterialTheme.typography.labelMedium)
        Text(
          text = CurrencyFormatter.format(res.totalAmount),
          style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        )

        Spacer(modifier = Modifier.height(16.dp))

        DonutChart(
          slices = listOf(
            ChartSlice("Principal", res.principal, ChartPrincipal),
            ChartSlice("Interest Earned", res.interestEarned, ChartInterest)
          ),
          centerTitle = CurrencyFormatter.format(res.interestEarned),
          centerSubtitle = "Interest Earned",
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

@Composable
private fun CompoundInterestSection(viewModel: InterestCalculatorViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val res = state.ciResult

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Compound Interest (CI)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.saveCalculation(context)
            }
          ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save calculation")
          }
        }

        NumberInputField(
          value = state.ciPrincipal,
          onValueChange = { viewModel.updateCi(principal = it) },
          label = "Principal Deposit",
          prefix = "₹"
        )

        NumberInputField(
          value = state.ciRate,
          onValueChange = { viewModel.updateCi(rate = it) },
          label = "Annual Interest Rate (% p.a.)",
          suffix = "%"
        )

        NumberInputField(
          value = state.ciYears,
          onValueChange = { viewModel.updateCi(years = it) },
          label = "Time Period (Years)",
          suffix = "Years"
        )

        Text("Compounding Frequency", style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          InterestCalculator.CompoundingFrequency.values().forEach { freq ->
            FilterChip(
              selected = state.ciFrequency == freq,
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.updateCi(freq = freq)
              },
              label = { Text(freq.title.split(" ").first(), fontSize = 11.sp) }
            )
          }
        }
      }
    }

    // Result Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Total Compounded Amount", style = MaterialTheme.typography.labelMedium)
        Text(
          text = CurrencyFormatter.format(res.totalAmount),
          style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Compound Interest Earned:", style = MaterialTheme.typography.bodyMedium)
          Text(CurrencyFormatter.format(res.totalInterest), fontWeight = FontWeight.Bold, color = Color(0xFF059669))
        }
      }
    }
  }
}

@Composable
private fun DoublingPeriodSection(viewModel: InterestCalculatorViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val res = state.doublingResult

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Rule of 72 & Doubling Time", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.saveCalculation(context)
            }
          ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save calculation")
          }
        }

        NumberInputField(
          value = state.doublingRate,
          onValueChange = { viewModel.updateDoubling(it) },
          label = "Annual Return / Interest Rate (% p.a.)",
          suffix = "%"
        )
      }
    }

    // Results Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Time to Double Your Money", style = MaterialTheme.typography.labelMedium)
        Text(
          text = "${String.format(java.util.Locale.US, "%.1f", res.exactDoublingYears)} Years",
          style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        )
        Text(
          text = "(Rule of 72 Estimate: ${String.format(java.util.Locale.US, "%.1f", res.ruleOf72Years)} Years)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Time to Triple (3x):", style = MaterialTheme.typography.bodyMedium)
          Text("${String.format(java.util.Locale.US, "%.1f", res.triplingYears)} Years", fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Time to Quadruple (4x):", style = MaterialTheme.typography.bodyMedium)
          Text("${String.format(java.util.Locale.US, "%.1f", res.quadruplingYears)} Years", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun EffectiveRateSection(viewModel: InterestCalculatorViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val res = state.earResult

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Effective Annual Rate (APY)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.saveCalculation(context)
            }
          ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save calculation")
          }
        }

        NumberInputField(
          value = state.earNominalRate,
          onValueChange = { viewModel.updateEar(rate = it) },
          label = "Nominal Stated Rate (% p.a.)",
          suffix = "%"
        )

        Text("Compounding Frequency", style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          InterestCalculator.CompoundingFrequency.values().forEach { freq ->
            FilterChip(
              selected = state.earFrequency == freq,
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.updateEar(freq = freq)
              },
              label = { Text(freq.title.split(" ").first(), fontSize = 11.sp) }
            )
          }
        }
      }
    }

    // Results Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("True Effective Annual Rate (APY)", style = MaterialTheme.typography.labelMedium)
        Text(
          text = "${String.format(java.util.Locale.US, "%.3f", res.effectiveRatePercent)}%",
          style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        )
        Text(
          text = "Extra yield earned: +${String.format(java.util.Locale.US, "%.3f", res.extraYieldPercent)}% due to compounding",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF059669)
        )
      }
    }
  }
}

@Composable
private fun FlatVsReducingSection(viewModel: InterestCalculatorViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val res = state.flatVsReducingResult

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Flat vs Reducing Balance Rate", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.saveCalculation(context)
            }
          ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save calculation")
          }
        }

        NumberInputField(
          value = state.flatPrincipal,
          onValueChange = { viewModel.updateFlatVsReducing(principal = it) },
          label = "Loan Principal Amount",
          prefix = "₹"
        )

        NumberInputField(
          value = state.flatTenureMonths,
          onValueChange = { viewModel.updateFlatVsReducing(tenure = it) },
          label = "Loan Tenure (Months)",
          suffix = "Months"
        )

        NumberInputField(
          value = state.flatRate,
          onValueChange = { viewModel.updateFlatVsReducing(rate = it) },
          label = "Lender's Quoted Flat Rate (% p.a.)",
          suffix = "%"
        )
      }
    }

    // Results Comparison Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("True Interest Comparison", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Quoted Flat Rate:", style = MaterialTheme.typography.bodyMedium)
          Text("${res.flatRate}%", fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("True Reducing Balance Rate:", style = MaterialTheme.typography.bodyMedium)
          Text("${String.format(java.util.Locale.US, "%.2f", res.equivalentReducingRate)}%", fontWeight = FontWeight.ExtraBold, color = Color(0xFFEF4444))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Total Interest Paid:", style = MaterialTheme.typography.bodyMedium)
          Text(CurrencyFormatter.format(res.flatTotalInterest), fontWeight = FontWeight.Bold, color = ChartInterest)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Monthly Installment (EMI):", style = MaterialTheme.typography.bodyMedium)
          Text(CurrencyFormatter.format(res.flatMonthlyPayment), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
      }
    }
  }
}
