package com.example.ui.screens.utility
import com.example.ui.theme.performConfiguredHapticFeedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Sub-Tabs with distinctive icons so users can choose visually
      TabRow(
        selectedTabIndex = state.selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("utility_tabs")
      ) {
        UtilityTab.values().forEach { tab ->
          val icon = when (tab) {
            UtilityTab.CURRENCY -> Icons.Default.CurrencyExchange
            UtilityTab.UNIT_CONVERTER -> Icons.Default.Straighten
            UtilityTab.TIP_SPLIT -> Icons.Default.Group
            UtilityTab.GST_TAX -> Icons.AutoMirrored.Filled.ReceiptLong
          }
          Tab(
            selected = state.selectedTab == tab,
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.selectTab(tab)
            },
            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
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

      // Main Content by active Tab
      when (state.selectedTab) {
        UtilityTab.CURRENCY -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            item {
              CurrencyCalculatorSection(viewModel = viewModel)
            }
            item {
              Spacer(modifier = Modifier.height(32.dp))
            }
          }
        }
        UtilityTab.UNIT_CONVERTER -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            item {
              UnitConverterSection(viewModel = viewModel)
            }
            item {
              Spacer(modifier = Modifier.height(32.dp))
            }
          }
        }
        UtilityTab.TIP_SPLIT -> {
          val splitBillViewModel: com.example.ui.screens.splitbill.SplitBillViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
          com.example.ui.screens.splitbill.SplitBillScreen(
            viewModel = splitBillViewModel,
            modifier = Modifier.fillMaxSize()
          )
        }
        UtilityTab.GST_TAX -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            item {
              GstTaxSection(viewModel = viewModel)
            }
            item {
              Spacer(modifier = Modifier.height(32.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CurrencyCalculatorSection(viewModel: UtilityViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current

  var showBaseCurrencyPicker by remember { mutableStateOf(false) }
  var showTargetCurrencyPicker by remember { mutableStateOf(false) }

  val baseCurrency = CurrencyCalculator.defaultCurrencies.find { it.code == state.currencyBaseCode }
    ?: CurrencyCalculator.defaultCurrencies.first()
  val targetCurrency = CurrencyCalculator.defaultCurrencies.find { it.code == state.currencyTargetCode }
    ?: CurrencyCalculator.defaultCurrencies[1]

  val baseRate = state.currencyRates[state.currencyBaseCode] ?: baseCurrency.ratePerUsd
  val targetRate = state.currencyRates[state.currencyTargetCode] ?: targetCurrency.ratePerUsd
  val rate1BaseInTarget = targetRate / baseRate
  val rate1TargetInBase = baseRate / targetRate

  // Currency Selection Dialogs with visual flag icons
  if (showBaseCurrencyPicker) {
    CurrencyPickerDialog(
      title = "Select Source Currency",
      selectedCode = state.currencyBaseCode,
      currencies = CurrencyCalculator.defaultCurrencies,
      onDismiss = { showBaseCurrencyPicker = false },
      onSelect = {
        viewModel.updateCurrencyBase(it)
        showBaseCurrencyPicker = false
      }
    )
  }

  if (showTargetCurrencyPicker) {
    CurrencyPickerDialog(
      title = "Select Target Currency",
      selectedCode = state.currencyTargetCode,
      currencies = CurrencyCalculator.defaultCurrencies,
      onDismiss = { showTargetCurrencyPicker = false },
      onSelect = {
        viewModel.updateCurrencyTarget(it)
        showTargetCurrencyPicker = false
      }
    )
  }

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
            Text("Real-time benchmark exchange rates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalIconButton(
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.resetRatesToDefault()
              }
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "Reset benchmark rates", modifier = Modifier.size(18.dp))
            }
            FilledTonalIconButton(
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.saveCalculation(context)
              },
              modifier = Modifier.testTag("save_currency_button")
            ) {
              Icon(Icons.Default.BookmarkAdd, contentDescription = "Save currency conversion")
            }
          }
        }

        // Amount Input Field
        NumberInputField(
          value = state.currencyAmountInput,
          onValueChange = { viewModel.updateCurrencyAmount(it) },
          label = "Amount in ${baseCurrency.code} to Convert",
          prefix = baseCurrency.symbol,
          testTag = "currency_amount_input"
        )

        // Pinned Favorite Currencies (1-tap switching)
        Text("Pinned Favorite Currencies (Tap to set target):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val favorites = listOf(
          Triple("USD", "$", "🇺🇸"),
          Triple("INR", "₹", "🇮🇳"),
          Triple("EUR", "€", "🇪🇺"),
          Triple("GBP", "£", "🇬🇧"),
          Triple("AED", "د.إ", "🇦🇪"),
          Triple("CAD", "C$", "🇨🇦"),
          Triple("JPY", "¥", "🇯🇵"),
          Triple("AUD", "A$", "🇦🇺"),
          Triple("SGD", "S$", "🇸🇬")
        )
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(favorites) { (code, symbol, flag) ->
            val isSelected = state.currencyTargetCode == code
            FilterChip(
              selected = isSelected,
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (state.currencyBaseCode == code) {
                  viewModel.swapCurrencies()
                } else {
                  viewModel.updateCurrencyTarget(code)
                }
              },
              leadingIcon = { Text(flag, fontSize = 14.sp) },
              label = { Text("$code ($symbol)", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) }
            )
          }
        }

        // Visual Currency Selection Cards (Instead of cramped side-by-side dropdowns)
        Text("Choose Currencies (Tap card to change)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // "From" Currency Card
        Card(
          onClick = { showBaseCurrencyPicker = true },
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(46.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
              ) {
                Text(baseCurrency.flag, fontSize = 26.sp)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("From (Source)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${baseCurrency.code} - ${baseCurrency.name}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
              }
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change currency", tint = MaterialTheme.colorScheme.primary)
          }
        }

        // Swap Icon Button
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          FilledIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.swapCurrencies()
            },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
          ) {
            Icon(Icons.Default.SwapVert, contentDescription = "Swap Currencies", modifier = Modifier.size(24.dp))
          }
        }

        // "To" Currency Card
        Card(
          onClick = { showTargetCurrencyPicker = true },
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(46.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
              ) {
                Text(targetCurrency.flag, fontSize = 26.sp)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("To (Target)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${targetCurrency.code} - ${targetCurrency.name}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
              }
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change currency", tint = MaterialTheme.colorScheme.primary)
          }
        }

        // Quick Currency Icon Chips row
        Text("Quick Currency Pick:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(listOf("USD", "INR", "EUR", "GBP", "AED", "SAR", "JPY", "CAD", "AUD", "SGD")) { code ->
            val info = CurrencyCalculator.defaultCurrencies.find { it.code == code }
            if (info != null) {
              FilterChip(
                selected = state.currencyTargetCode == code,
                onClick = {
                  haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                  viewModel.updateCurrencyTarget(code)
                },
                leadingIcon = { Text(info.flag, fontSize = 14.sp) },
                label = { Text(info.code, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
              )
            }
          }
        }

        // Prominent Converted Result Box (full values without truncation)
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
            text = "Live Exchange Rates Board",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = "Tap ✏️ to customize rate",
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

@Composable
private fun CurrencyPickerDialog(
  title: String,
  selectedCode: String,
  currencies: List<CurrencyCalculator.CurrencyInfo>,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }

  val filtered = remember(currencies, searchQuery) {
    if (searchQuery.isBlank()) currencies
    else currencies.filter {
      it.code.contains(searchQuery, ignoreCase = true) || it.name.contains(searchQuery, ignoreCase = true)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .height(350.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search currency...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          items(filtered) { curr ->
            val isSelected = curr.code == selectedCode
            Card(
              onClick = { onSelect(curr.code) },
              shape = RoundedCornerShape(10.dp),
              colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(curr.flag, fontSize = 24.sp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column {
                    Text(curr.code, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(curr.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                if (isSelected) {
                  Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    }
  )
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
    title = { Text("Set Live Rate for ${currency.code}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Enter the exact real-time rate per 1 US Dollar (USD):", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
          value = rateStr,
          onValueChange = { rateStr = it.filter { c -> c.isDigit() || c == '.' } },
          label = { Text("1 USD = how many ${currency.code}") },
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
        Text("Save Live Rate")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

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
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.saveCalculation(context)
          },
          modifier = Modifier.testTag("save_unit_button")
        ) {
          Icon(Icons.Default.BookmarkAdd, contentDescription = "Save conversion")
        }
      }

      // Visual Icon Selection for Category (Scrollable to prevent wrapping)
      Text("Category:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val categories = listOf(
          UnitConverter.Category.LENGTH to Icons.Default.Straighten,
          UnitConverter.Category.WEIGHT to Icons.Default.Scale,
          UnitConverter.Category.TEMPERATURE to Icons.Default.Thermostat,
          UnitConverter.Category.CURRENCY to Icons.Default.CurrencyExchange
        )
        items(categories) { (cat, icon) ->
          FilterChip(
            selected = state.unitCategory == cat,
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.selectCategory(cat)
            },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text(cat.title, fontSize = 12.sp, maxLines = 1, softWrap = false) }
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
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
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
      singleLine = true,
      maxLines = 1,
      label = { Text(label, fontSize = 12.sp, maxLines = 1) },
      textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
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
        Column {
          Text("Split Bill & Group Share", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          Text("Divide group dinner, rent, or outing expenses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(
          onClick = {
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
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
        label = "Total Bill / Expense Amount",
        prefix = "₹",
        testTag = "tip_bill_input"
      )

      Column {
        Text("Quick Split By People", style = MaterialTheme.typography.bodySmall)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(2, 3, 4, 5, 6, 8).forEach { count ->
            FilterChip(
              selected = state.numPersons == count,
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.updateNumPersons(count)
              },
              label = { Text("$count", fontWeight = FontWeight.SemiBold) },
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
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
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
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
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
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
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
            Text("Total Bill:", style = MaterialTheme.typography.bodySmall)
            Text(CurrencyFormatter.format(state.tipResult.billAmount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Number of People:", style = MaterialTheme.typography.bodySmall)
            Text("${state.numPersons} people", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Equal Share per Person:", style = MaterialTheme.typography.bodySmall)
            Text(CurrencyFormatter.format(state.tipResult.perPersonTotal), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.saveCalculation(context)
          },
          modifier = Modifier.testTag("save_gst_button")
        ) {
          Icon(Icons.Default.BookmarkAdd, contentDescription = "Save GST")
        }
      }

      // Visual Icon Selection Cards for GST Mode (Instead of plain side-by-side buttons)
      Text("Select Calculation Mode:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        SelectableIconCard(
          title = "Add GST",
          subtitle = "Exclusive Tax",
          icon = Icons.Default.AddCircle,
          isSelected = state.gstIsExclusive,
          onClick = {
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.updateGstExclusive(true)
          },
          modifier = Modifier.weight(1f)
        )

        SelectableIconCard(
          title = "Remove GST",
          subtitle = "Inclusive Tax",
          icon = Icons.Default.RemoveCircle,
          isSelected = !state.gstIsExclusive,
          onClick = {
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.updateGstExclusive(false)
          },
          modifier = Modifier.weight(1f)
        )
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
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
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

@Composable
fun SelectableIconCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ),
    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(
        icon,
        contentDescription = null,
        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(28.dp)
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
