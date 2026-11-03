package com.example.ui.screens.utility

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.domain.calculator.CurrencyCalculator
import com.example.domain.calculator.UnitConverter
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.NumberInputField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilityScreen(
  viewModel: UtilityViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current

  // Custom Rate Edit Dialog
  if (state.showRateEditDialog && state.editingCurrency != null) {
    EditRateDialog(
      currency = state.editingCurrency!!,
      currentRate = state.currencyRates[state.editingCurrency!!.code] ?: state.editingCurrency!!.ratePerUsd,
      onDismiss = { viewModel.closeEditRate() },
      onSave = { code, rate -> viewModel.saveCustomRate(code, rate) }
    )
  }

  Scaffold(
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
      // Sub-Tabs: Currency / Units / Tip & Split / GST
      item {
        TabRow(
          selectedTabIndex = state.selectedTab.ordinal,
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("utility_tabs")
        ) {
          UtilityTab.values().forEach { tab ->
            Tab(
              selected = state.selectedTab == tab,
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.selectTab(tab)
              },
              text = {
                Text(
                  text = tab.title,
                  fontWeight = if (state.selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                  fontSize = 12.sp
                )
              }
            )
          }
        }
      }

      // Main Content by active Tab
      when (state.selectedTab) {
        UtilityTab.CURRENCY -> {
          item {
            CurrencyCalculatorSection(viewModel = viewModel)
          }
        }
        UtilityTab.UNIT_CONVERTER -> {
          item {
            UnitConverterSection(viewModel = viewModel)
          }
        }
        UtilityTab.TIP_SPLIT -> {
          item {
            TipSplitSection(viewModel = viewModel)
          }
        }
        UtilityTab.GST_TAX -> {
          item {
            GstTaxSection(viewModel = viewModel)
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(32.dp))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyCalculatorSection(viewModel: UtilityViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current

  val baseCurrency = CurrencyCalculator.defaultCurrencies.find { it.code == state.currencyBaseCode }
    ?: CurrencyCalculator.defaultCurrencies.first()
  val targetCurrency = CurrencyCalculator.defaultCurrencies.find { it.code == state.currencyTargetCode }
    ?: CurrencyCalculator.defaultCurrencies[1]

  val baseRate = state.currencyRates[state.currencyBaseCode] ?: baseCurrency.ratePerUsd
  val targetRate = state.currencyRates[state.currencyTargetCode] ?: targetCurrency.ratePerUsd
  val rate1BaseInTarget = targetRate / baseRate
  val rate1TargetInBase = baseRate / targetRate

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    // 1-to-1 Currency Converter Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
          Column {
            Text("Currency Calculator", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text("Real-time exchange conversion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          FilledTonalIconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.saveCalculation(context)
            },
            modifier = Modifier.testTag("save_currency_button")
          ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save currency conversion")
          }
        }

        // Amount Input Field
        NumberInputField(
          value = state.currencyAmountInput,
          onValueChange = { viewModel.updateCurrencyAmount(it) },
          label = "Amount to Convert",
          prefix = baseCurrency.symbol,
          testTag = "currency_amount_input"
        )

        // Currency Selectors & Swap
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          CurrencyDropdown(
            selectedCode = state.currencyBaseCode,
            onSelect = { viewModel.updateCurrencyBase(it) },
            label = "From",
            modifier = Modifier.weight(1f)
          )

          IconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.swapCurrencies()
            },
            modifier = Modifier.padding(horizontal = 4.dp)
          ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = "Swap Currencies")
          }

          CurrencyDropdown(
            selectedCode = state.currencyTargetCode,
            onSelect = { viewModel.updateCurrencyTarget(it) },
            label = "To",
            modifier = Modifier.weight(1f)
          )
        }

        // Prominent Converted Result Box (full values without 10k/5k truncation)
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text("Converted Amount", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "${targetCurrency.flag} ${targetCurrency.symbol} ${String.format(java.util.Locale.US, "%,.2f", state.currencySingleOutput)}",
              style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "1 ${baseCurrency.code} = ${String.format(java.util.Locale.US, "%,.4f", rate1BaseInTarget)} ${targetCurrency.code}",
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "1 ${targetCurrency.code} = ${String.format(java.util.Locale.US, "%,.4f", rate1TargetInBase)} ${baseCurrency.code}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    // Multi-Currency Exchange Rates Board
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
          Text(
            text = "Exchange Rates Board",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = "Tap rate to customize",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        state.multiCurrencyList.forEach { item ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(item.info.flag, fontSize = 22.sp)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = item.info.code,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = item.info.name,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "${item.info.symbol} ${String.format(java.util.Locale.US, "%,.2f", item.convertedAmount)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (item.info.code == state.currencyBaseCode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "1 ${state.currencyBaseCode} = ${String.format(java.util.Locale.US, "%.4f", item.exchangeRateFromBase)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                  onClick = { viewModel.openEditRate(item.info) },
                  modifier = Modifier.size(24.dp).padding(start = 4.dp)
                ) {
                  Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Rate",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(
  selectedCode: String,
  onSelect: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }
  val selected = CurrencyCalculator.defaultCurrencies.find { it.code == selectedCode }
    ?: CurrencyCalculator.defaultCurrencies.first()

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = modifier
  ) {
    OutlinedTextField(
      value = "${selected.flag} ${selected.code}",
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.menuAnchor()
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      CurrencyCalculator.defaultCurrencies.forEach { curr ->
        DropdownMenuItem(
          text = { Text("${curr.flag} ${curr.code} - ${curr.name}") },
          onClick = {
            onSelect(curr.code)
            expanded = false
          }
        )
      }
    }
  }
}

@Composable
private fun EditRateDialog(
  currency: CurrencyCalculator.CurrencyInfo,
  currentRate: Double,
  onDismiss: () -> Unit,
  onSave: (code: String, rate: Double) -> Unit
) {
  var rateStr by remember { mutableStateOf(currentRate.toString()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Rate for ${currency.code}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Set how many ${currency.code} equal 1 US Dollar (USD):", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
          value = rateStr,
          onValueChange = { rateStr = it.filter { c -> c.isDigit() || c == '.' } },
          label = { Text("Rate per 1 USD") },
          prefix = { Text("${currency.symbol} ") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val r = rateStr.toDoubleOrNull() ?: currentRate
          if (r > 0) onSave(currency.code, r)
        }
      ) {
        Text("Save Rate")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitConverterSection(viewModel: UtilityViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current

  val availableUnits = when (state.unitCategory) {
    UnitConverter.Category.LENGTH -> UnitConverter.lengthUnits
    UnitConverter.Category.WEIGHT -> UnitConverter.weightUnits
    UnitConverter.Category.TEMPERATURE -> UnitConverter.temperatureUnits
    UnitConverter.Category.CURRENCY -> UnitConverter.currencyUnits
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
        Text("Unit Converter", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        FilledTonalIconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.saveCalculation(context)
          },
          modifier = Modifier.testTag("save_unit_button")
        ) {
          Icon(Icons.Default.BookmarkAdd, contentDescription = "Save conversion")
        }
      }

      // Category Chips
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        UnitConverter.Category.values().forEach { cat ->
          FilterChip(
            selected = state.unitCategory == cat,
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.selectCategory(cat)
            },
            label = { Text(cat.title, fontSize = 11.sp) }
          )
        }
      }

      // Input Value
      NumberInputField(
        value = state.unitInput,
        onValueChange = { viewModel.updateUnitInput(it) },
        label = "Enter value to convert",
        suffix = state.fromUnit.symbol,
        testTag = "unit_input_field"
      )

      // Unit Dropdowns & Swap Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        UnitDropdown(
          selected = state.fromUnit,
          items = availableUnits,
          onSelect = { viewModel.updateFromUnit(it) },
          label = "From",
          modifier = Modifier.weight(1f)
        )

        IconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.swapUnits()
          },
          modifier = Modifier.padding(horizontal = 4.dp)
        ) {
          Icon(Icons.Default.SwapHoriz, contentDescription = "Swap units")
        }

        UnitDropdown(
          selected = state.toUnit,
          items = availableUnits,
          onSelect = { viewModel.updateToUnit(it) },
          label = "To",
          modifier = Modifier.weight(1f)
        )
      }

      // Conversion Result Card (full values)
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text("Converted Result", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "${String.format(java.util.Locale.US, "%,.4f", state.unitOutput).trimEnd('0').trimEnd('.')} ${state.toUnit.symbol}",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "1 ${state.fromUnit.symbol} = ${String.format(java.util.Locale.US, "%,.4f", UnitConverter.convert(state.unitCategory, 1.0, state.fromUnit, state.toUnit))} ${state.toUnit.symbol}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
  selected: UnitConverter.UnitItem,
  items: List<UnitConverter.UnitItem>,
  onSelect: (UnitConverter.UnitItem) -> Unit,
  label: String,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = modifier
  ) {
    OutlinedTextField(
      value = "${selected.name} (${selected.symbol})",
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.menuAnchor()
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      items.forEach { item ->
        DropdownMenuItem(
          text = { Text("${item.name} (${item.symbol})") },
          onClick = {
            onSelect(item)
            expanded = false
          }
        )
      }
    }
  }
}

@Composable
private fun TipSplitSection(viewModel: UtilityViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
        Text("Tip & Split Bill", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        FilledTonalIconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.saveCalculation(context)
          },
          modifier = Modifier.testTag("save_tip_button")
        ) {
          Icon(Icons.Default.BookmarkAdd, contentDescription = "Save split")
        }
      }

      NumberInputField(
        value = state.billAmount,
        onValueChange = { viewModel.updateBillAmount(it) },
        label = "Total Bill Amount",
        prefix = "₹",
        testTag = "tip_bill_input"
      )

      Column {
        Text("Tip Percentage (${state.tipPercent.toInt()}%)", style = MaterialTheme.typography.bodySmall)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(5.0, 10.0, 15.0, 20.0).forEach { tip ->
            FilterChip(
              selected = state.tipPercent == tip,
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.updateTipPercent(tip)
              },
              label = { Text("${tip.toInt()}%") },
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text("Split Between", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
          Text("${state.numPersons} people", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilledTonalIconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.updateNumPersons(state.numPersons - 1)
            },
            enabled = state.numPersons > 1
          ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease persons")
          }

          Text(
            text = state.numPersons.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp)
          )

          FilledTonalIconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.updateNumPersons(state.numPersons + 1)
            }
          ) {
            Icon(Icons.Default.Add, contentDescription = "Increase persons")
          }
        }
      }

      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text("Each Person Pays", style = MaterialTheme.typography.labelMedium)
          Text(
            text = CurrencyFormatter.format(state.tipResult.perPersonTotal),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )

          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Tip per person:", style = MaterialTheme.typography.bodySmall)
            Text(CurrencyFormatter.format(state.tipResult.perPersonTip), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Total Tip:", style = MaterialTheme.typography.bodySmall)
            Text(CurrencyFormatter.format(state.tipResult.tipAmount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Grand Total:", style = MaterialTheme.typography.bodySmall)
            Text(CurrencyFormatter.format(state.tipResult.totalAmount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}

@Composable
private fun GstTaxSection(viewModel: UtilityViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
        Text("GST & Tax Calculator", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        FilledTonalIconButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.saveCalculation(context)
          },
          modifier = Modifier.testTag("save_gst_button")
        ) {
          Icon(Icons.Default.BookmarkAdd, contentDescription = "Save GST")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.updateGstExclusive(true)
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (state.gstIsExclusive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (state.gstIsExclusive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
          ),
          modifier = Modifier.weight(1f)
        ) {
          Text("Add GST (Exclusive)", fontSize = 12.sp)
        }

        Button(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.updateGstExclusive(false)
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (!state.gstIsExclusive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (!state.gstIsExclusive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
          ),
          modifier = Modifier.weight(1f)
        ) {
          Text("Remove GST (Inclusive)", fontSize = 12.sp)
        }
      }

      NumberInputField(
        value = state.gstAmount,
        onValueChange = { viewModel.updateGstAmount(it) },
        label = if (state.gstIsExclusive) "Net Base Amount" else "Gross Total Amount (with GST)",
        prefix = "₹",
        testTag = "gst_amount_input"
      )

      Column {
        Text("GST Slab (${state.gstRate.toInt()}%)", style = MaterialTheme.typography.bodySmall)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(0.0, 5.0, 12.0, 18.0, 28.0).forEach { rate ->
            FilterChip(
              selected = state.gstRate == rate,
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.updateGstRate(rate)
              },
              label = { Text("${rate.toInt()}%", fontSize = 11.sp) },
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Actual Net Amount:", style = MaterialTheme.typography.bodyMedium)
            Text(CurrencyFormatter.format(state.gstResult.baseAmount), fontWeight = FontWeight.Bold)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total GST (${state.gstRate.toInt()}%):", style = MaterialTheme.typography.bodyMedium)
            Text(CurrencyFormatter.format(state.gstResult.gstAmount), fontWeight = FontWeight.Bold, color = Color(0xFF059669))
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("• CGST (${state.gstRate / 2}%):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(CurrencyFormatter.format(state.gstResult.cgstAmount), style = MaterialTheme.typography.bodySmall)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("• SGST (${state.gstRate / 2}%):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(CurrencyFormatter.format(state.gstResult.sgstAmount), style = MaterialTheme.typography.bodySmall)
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Final Total Amount:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(
              CurrencyFormatter.format(state.gstResult.finalTotalAmount),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }
    }
  }
}
