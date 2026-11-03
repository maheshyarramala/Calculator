package com.example.ui.screens.utility

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CalculationRecord
import com.example.domain.calculator.CurrencyCalculator
import com.example.domain.calculator.UnitConverter
import com.example.domain.calculator.UtilityCalculators
import com.example.ui.components.CurrencyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UtilityTab(val title: String) {
  CURRENCY("Currency"),
  UNIT_CONVERTER("Units"),
  TIP_SPLIT("Split Bill"),
  GST_TAX("GST / Tax")
}

data class UtilityUiState(
  val selectedTab: UtilityTab = UtilityTab.CURRENCY,
  // Currency Calculator
  val currencyBaseCode: String = "USD",
  val currencyTargetCode: String = "INR",
  val currencyAmountInput: String = "100",
  val currencySingleOutput: Double = 8640.0,
  val currencyRates: Map<String, Double> = CurrencyCalculator.defaultCurrencies.associate { it.code to it.ratePerUsd },
  val multiCurrencyList: List<CurrencyCalculator.ConvertedCurrency> = CurrencyCalculator.convertToAll(
    100.0,
    "USD",
    CurrencyCalculator.defaultCurrencies,
    CurrencyCalculator.defaultCurrencies.associate { it.code to it.ratePerUsd }
  ),
  val showRateEditDialog: Boolean = false,
  val editingCurrency: CurrencyCalculator.CurrencyInfo? = null,
  // Unit Converter
  val unitCategory: UnitConverter.Category = UnitConverter.Category.LENGTH,
  val fromUnit: UnitConverter.UnitItem = UnitConverter.lengthUnits[0],
  val toUnit: UnitConverter.UnitItem = UnitConverter.lengthUnits[1],
  val unitInput: String = "1",
  val unitOutput: Double = 0.001,
  // Split Bill (No Tip)
  val billAmount: String = "1500",
  val tipPercent: Double = 0.0,
  val numPersons: Int = 3,
  val tipResult: UtilityCalculators.TipResult = UtilityCalculators.calculateTip(1500.0, 0.0, 3),
  // GST / Tax
  val gstAmount: String = "1000",
  val gstRate: Double = 18.0,
  val gstIsExclusive: Boolean = true,
  val gstResult: UtilityCalculators.GstResult = UtilityCalculators.calculateGst(1000.0, 18.0, true),
  val toastMessage: String? = null
)

class UtilityViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(UtilityUiState())
  val uiState: StateFlow<UtilityUiState> = _uiState.asStateFlow()

  fun selectTab(tab: UtilityTab) {
    _uiState.update { it.copy(selectedTab = tab) }
  }

  // --- Currency Calculator ---
  fun updateCurrencyAmount(amountStr: String) {
    _uiState.update { it.copy(currencyAmountInput = amountStr) }
    recalculateCurrency()
  }

  fun updateCurrencyBase(code: String) {
    _uiState.update { it.copy(currencyBaseCode = code) }
    recalculateCurrency()
  }

  fun updateCurrencyTarget(code: String) {
    _uiState.update { it.copy(currencyTargetCode = code) }
    recalculateCurrency()
  }

  fun swapCurrencies() {
    _uiState.update {
      it.copy(
        currencyBaseCode = it.currencyTargetCode,
        currencyTargetCode = it.currencyBaseCode
      )
    }
    recalculateCurrency()
  }

  fun openEditRate(info: CurrencyCalculator.CurrencyInfo) {
    _uiState.update { it.copy(editingCurrency = info, showRateEditDialog = true) }
  }

  fun closeEditRate() {
    _uiState.update { it.copy(showRateEditDialog = false, editingCurrency = null) }
  }

  fun saveCustomRate(code: String, newRatePerUsd: Double) {
    if (newRatePerUsd <= 0.0) return
    val updatedRates = _uiState.value.currencyRates.toMutableMap()
    updatedRates[code] = newRatePerUsd
    _uiState.update {
      it.copy(
        currencyRates = updatedRates,
        showRateEditDialog = false,
        editingCurrency = null,
        toastMessage = "Updated rate for $code: 1 USD = $newRatePerUsd"
      )
    }
    recalculateCurrency()
  }

  fun resetRatesToDefault() {
    val defaults = CurrencyCalculator.defaultCurrencies.associate { it.code to it.ratePerUsd }
    _uiState.update {
      it.copy(currencyRates = defaults, toastMessage = "Exchange rates reset to standard market rates")
    }
    recalculateCurrency()
  }

  private fun recalculateCurrency() {
    val state = _uiState.value
    val amount = state.currencyAmountInput.toDoubleOrNull() ?: 0.0
    val single = CurrencyCalculator.convertSingle(
      amount = amount,
      fromCode = state.currencyBaseCode,
      toCode = state.currencyTargetCode,
      rates = state.currencyRates
    )
    val multi = CurrencyCalculator.convertToAll(
      amount = amount,
      baseCode = state.currencyBaseCode,
      currencies = CurrencyCalculator.defaultCurrencies,
      rates = state.currencyRates
    )
    _uiState.update {
      it.copy(
        currencySingleOutput = single,
        multiCurrencyList = multi
      )
    }
  }

  // --- Unit Converter ---
  fun selectCategory(cat: UnitConverter.Category) {
    val units = when (cat) {
      UnitConverter.Category.LENGTH -> UnitConverter.lengthUnits
      UnitConverter.Category.WEIGHT -> UnitConverter.weightUnits
      UnitConverter.Category.TEMPERATURE -> UnitConverter.temperatureUnits
      UnitConverter.Category.CURRENCY -> UnitConverter.currencyUnits
    }
    _uiState.update {
      it.copy(
        unitCategory = cat,
        fromUnit = units.first(),
        toUnit = if (units.size > 1) units[1] else units[0]
      )
    }
    recalculateUnit()
  }

  fun updateFromUnit(unit: UnitConverter.UnitItem) {
    _uiState.update { it.copy(fromUnit = unit) }
    recalculateUnit()
  }

  fun updateToUnit(unit: UnitConverter.UnitItem) {
    _uiState.update { it.copy(toUnit = unit) }
    recalculateUnit()
  }

  fun swapUnits() {
    _uiState.update { it.copy(fromUnit = it.toUnit, toUnit = it.fromUnit) }
    recalculateUnit()
  }

  fun updateUnitInput(input: String) {
    _uiState.update { it.copy(unitInput = input) }
    recalculateUnit()
  }

  private fun recalculateUnit() {
    val state = _uiState.value
    val num = state.unitInput.toDoubleOrNull() ?: 0.0
    val converted = UnitConverter.convert(state.unitCategory, num, state.fromUnit, state.toUnit)
    _uiState.update { it.copy(unitOutput = converted) }
  }

  // --- Tip & Split ---
  fun updateBillAmount(bill: String) {
    _uiState.update { it.copy(billAmount = bill) }
    recalculateTip()
  }

  fun updateTipPercent(percent: Double) {
    _uiState.update { it.copy(tipPercent = percent) }
    recalculateTip()
  }

  fun updateNumPersons(persons: Int) {
    _uiState.update { it.copy(numPersons = persons.coerceAtLeast(1)) }
    recalculateTip()
  }

  private fun recalculateTip() {
    val state = _uiState.value
    val bill = state.billAmount.toDoubleOrNull() ?: 0.0
    val res = UtilityCalculators.calculateTip(bill, state.tipPercent, state.numPersons)
    _uiState.update { it.copy(tipResult = res) }
  }

  // --- GST / Tax ---
  fun updateGstAmount(amount: String) {
    _uiState.update { it.copy(gstAmount = amount) }
    recalculateGst()
  }

  fun updateGstRate(rate: Double) {
    _uiState.update { it.copy(gstRate = rate) }
    recalculateGst()
  }

  fun updateGstExclusive(isExclusive: Boolean) {
    _uiState.update { it.copy(gstIsExclusive = isExclusive) }
    recalculateGst()
  }

  private fun recalculateGst() {
    val state = _uiState.value
    val amt = state.gstAmount.toDoubleOrNull() ?: 0.0
    val res = UtilityCalculators.calculateGst(amt, state.gstRate, state.gstIsExclusive)
    _uiState.update { it.copy(gstResult = res) }
  }

  fun clearToast() {
    _uiState.update { it.copy(toastMessage = null) }
  }

  fun saveCalculation(context: Context) {
    val state = _uiState.value
    viewModelScope.launch {
      val db = AppDatabase.getDatabase(context)
      val record = when (state.selectedTab) {
        UtilityTab.CURRENCY -> {
          val base = state.currencyBaseCode
          val target = state.currencyTargetCode
          val inAmt = state.currencyAmountInput
          val outAmt = String.format(java.util.Locale.US, "%,.2f", state.currencySingleOutput)
          CalculationRecord(
            category = "CURRENCY",
            title = "Currency: $inAmt $base = $outAmt $target",
            summary = "Base: $base -> Target: $target (Rate: ${String.format(java.util.Locale.US, "%.4f", state.currencySingleOutput / (state.currencyAmountInput.toDoubleOrNull() ?: 1.0))})",
            detailsText = "Amount: $inAmt $base\nConverted: $outAmt $target\nExchange Date: Today\nOmniCalc Currency Engine",
            timestamp = System.currentTimeMillis()
          )
        }
        UtilityTab.UNIT_CONVERTER -> {
          CalculationRecord(
            category = "UNIT",
            title = "${state.unitInput} ${state.fromUnit.symbol} = ${String.format(java.util.Locale.US, "%.4f", state.unitOutput)} ${state.toUnit.symbol}",
            summary = "Category: ${state.unitCategory.title}",
            detailsText = "${state.unitInput} ${state.fromUnit.name} converted to ${state.toUnit.name}: ${state.unitOutput}",
            timestamp = System.currentTimeMillis()
          )
        }
        UtilityTab.TIP_SPLIT -> {
          val res = state.tipResult
          CalculationRecord(
            category = "TIP",
            title = "Split Bill: ${CurrencyFormatter.format(res.perPersonTotal)} / person",
            summary = "Bill: ${CurrencyFormatter.format(res.billAmount)} + Tip: ${CurrencyFormatter.format(res.tipAmount)} (${res.tipPercent}%)",
            detailsText = "Bill Amount: ${res.billAmount}\nTip: ${res.tipAmount}\nPersons: ${res.numPersons}\nTotal: ${res.totalAmount}",
            timestamp = System.currentTimeMillis()
          )
        }
        UtilityTab.GST_TAX -> {
          val res = state.gstResult
          CalculationRecord(
            category = "GST",
            title = "GST ${res.ratePercent}%: Total ${CurrencyFormatter.format(res.finalTotalAmount)}",
            summary = "Base: ${CurrencyFormatter.format(res.baseAmount)} + Tax: ${CurrencyFormatter.format(res.gstAmount)}",
            detailsText = "Base: ${res.baseAmount}\nGST (${res.ratePercent}%): ${res.gstAmount}\nCGST: ${res.cgstAmount}\nSGST: ${res.sgstAmount}\nTotal: ${res.finalTotalAmount}",
            timestamp = System.currentTimeMillis()
          )
        }
      }
      db.calculationHistoryDao().insert(record)
      _uiState.update { it.copy(toastMessage = "${state.selectedTab.title} saved to history!") }
    }
  }
}
