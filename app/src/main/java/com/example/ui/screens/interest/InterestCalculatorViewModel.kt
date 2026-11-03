package com.example.ui.screens.interest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CalculationRecord
import com.example.domain.calculator.InterestCalculator
import com.example.ui.components.CurrencyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InterestMethod(val title: String) {
  SIMPLE("Simple Interest"),
  COMPOUND("Compound Interest"),
  DOUBLING("Rule of 72"),
  EAR("Effective APY"),
  FLAT_VS_REDUCING("Flat vs Reducing")
}

data class InterestUiState(
  val selectedMethod: InterestMethod = InterestMethod.SIMPLE,
  // Simple Interest
  val siPrincipal: String = "100000",
  val siRate: String = "8.5",
  val siTime: String = "3",
  val siTimeUnit: InterestCalculator.TimeUnit = InterestCalculator.TimeUnit.YEARS,
  val siResult: InterestCalculator.SimpleInterestResult = InterestCalculator.calculateSimpleInterest(
    100000.0, 8.5, 3.0, InterestCalculator.TimeUnit.YEARS
  ),
  // Compound Interest
  val ciPrincipal: String = "100000",
  val ciRate: String = "8.5",
  val ciYears: String = "5",
  val ciFrequency: InterestCalculator.CompoundingFrequency = InterestCalculator.CompoundingFrequency.ANNUALLY,
  val ciResult: InterestCalculator.CompoundInterestResult = InterestCalculator.calculateCompoundInterest(
    100000.0, 8.5, 5.0, InterestCalculator.CompoundingFrequency.ANNUALLY
  ),
  // Doubling Period
  val doublingRate: String = "12",
  val doublingResult: InterestCalculator.DoublingResult = InterestCalculator.calculateDoubling(12.0),
  // EAR (Effective Annual Rate)
  val earNominalRate: String = "10",
  val earFrequency: InterestCalculator.CompoundingFrequency = InterestCalculator.CompoundingFrequency.MONTHLY,
  val earResult: InterestCalculator.EarResult = InterestCalculator.calculateEffectiveRate(
    10.0, InterestCalculator.CompoundingFrequency.MONTHLY
  ),
  // Flat vs Reducing
  val flatPrincipal: String = "500000",
  val flatTenureMonths: String = "36",
  val flatRate: String = "10",
  val flatVsReducingResult: InterestCalculator.FlatVsReducingResult = InterestCalculator.compareFlatVsReducing(
    500000.0, 36, 10.0
  ),
  val toastMessage: String? = null
)

class InterestCalculatorViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(InterestUiState())
  val uiState: StateFlow<InterestUiState> = _uiState.asStateFlow()

  fun selectMethod(method: InterestMethod) {
    _uiState.update { it.copy(selectedMethod = method) }
  }

  // --- Simple Interest ---
  fun updateSi(
    principal: String = _uiState.value.siPrincipal,
    rate: String = _uiState.value.siRate,
    time: String = _uiState.value.siTime,
    unit: InterestCalculator.TimeUnit = _uiState.value.siTimeUnit
  ) {
    val p = principal.toDoubleOrNull() ?: 0.0
    val r = rate.toDoubleOrNull() ?: 0.0
    val t = time.toDoubleOrNull() ?: 0.0
    val res = InterestCalculator.calculateSimpleInterest(p, r, t, unit)
    _uiState.update {
      it.copy(siPrincipal = principal, siRate = rate, siTime = time, siTimeUnit = unit, siResult = res)
    }
  }

  // --- Compound Interest ---
  fun updateCi(
    principal: String = _uiState.value.ciPrincipal,
    rate: String = _uiState.value.ciRate,
    years: String = _uiState.value.ciYears,
    freq: InterestCalculator.CompoundingFrequency = _uiState.value.ciFrequency
  ) {
    val p = principal.toDoubleOrNull() ?: 0.0
    val r = rate.toDoubleOrNull() ?: 0.0
    val y = years.toDoubleOrNull() ?: 0.0
    val res = InterestCalculator.calculateCompoundInterest(p, r, y, freq)
    _uiState.update {
      it.copy(ciPrincipal = principal, ciRate = rate, ciYears = years, ciFrequency = freq, ciResult = res)
    }
  }

  // --- Doubling ---
  fun updateDoubling(rate: String) {
    val r = rate.toDoubleOrNull() ?: 0.0
    val res = InterestCalculator.calculateDoubling(r)
    _uiState.update { it.copy(doublingRate = rate, doublingResult = res) }
  }

  // --- EAR ---
  fun updateEar(
    rate: String = _uiState.value.earNominalRate,
    freq: InterestCalculator.CompoundingFrequency = _uiState.value.earFrequency
  ) {
    val r = rate.toDoubleOrNull() ?: 0.0
    val res = InterestCalculator.calculateEffectiveRate(r, freq)
    _uiState.update { it.copy(earNominalRate = rate, earFrequency = freq, earResult = res) }
  }

  // --- Flat vs Reducing ---
  fun updateFlatVsReducing(
    principal: String = _uiState.value.flatPrincipal,
    tenure: String = _uiState.value.flatTenureMonths,
    rate: String = _uiState.value.flatRate
  ) {
    val p = principal.toDoubleOrNull() ?: 0.0
    val t = tenure.toIntOrNull() ?: 0
    val r = rate.toDoubleOrNull() ?: 0.0
    val res = InterestCalculator.compareFlatVsReducing(p, t, r)
    _uiState.update {
      it.copy(flatPrincipal = principal, flatTenureMonths = tenure, flatRate = rate, flatVsReducingResult = res)
    }
  }

  fun clearToast() {
    _uiState.update { it.copy(toastMessage = null) }
  }

  fun saveCalculation(context: Context) {
    val state = _uiState.value
    viewModelScope.launch {
      val db = AppDatabase.getDatabase(context)
      val record = when (state.selectedMethod) {
        InterestMethod.SIMPLE -> {
          val res = state.siResult
          CalculationRecord(
            category = "INTEREST",
            title = "Simple Interest: ${CurrencyFormatter.format(res.interestEarned)}",
            summary = "Principal: ${CurrencyFormatter.format(res.principal)} @ ${res.annualRate}% for ${state.siTime} ${state.siTimeUnit.title}",
            detailsText = "Total Amount: ${CurrencyFormatter.format(res.totalAmount)}\nInterest: ${CurrencyFormatter.format(res.interestEarned)}",
            timestamp = System.currentTimeMillis()
          )
        }
        InterestMethod.COMPOUND -> {
          val res = state.ciResult
          CalculationRecord(
            category = "INTEREST",
            title = "Compound Interest: ${CurrencyFormatter.format(res.totalInterest)}",
            summary = "Principal: ${CurrencyFormatter.format(res.principal)} @ ${res.annualRate}% (${res.frequency.title})",
            detailsText = "Total Amount: ${CurrencyFormatter.format(res.totalAmount)}\nCompounded Interest: ${CurrencyFormatter.format(res.totalInterest)}",
            timestamp = System.currentTimeMillis()
          )
        }
        InterestMethod.DOUBLING -> {
          val res = state.doublingResult
          CalculationRecord(
            category = "INTEREST",
            title = "Rule of 72: Doubling in ${String.format(java.util.Locale.US, "%.1f", res.exactDoublingYears)} Yrs",
            summary = "At ${res.annualRate}% annual return",
            detailsText = "Rule of 72: ${String.format(java.util.Locale.US, "%.1f", res.ruleOf72Years)} Yrs\nExact Doubling: ${String.format(java.util.Locale.US, "%.2f", res.exactDoublingYears)} Yrs\nTripling: ${String.format(java.util.Locale.US, "%.2f", res.triplingYears)} Yrs",
            timestamp = System.currentTimeMillis()
          )
        }
        InterestMethod.EAR -> {
          val res = state.earResult
          CalculationRecord(
            category = "INTEREST",
            title = "Effective APY: ${String.format(java.util.Locale.US, "%.2f", res.effectiveRatePercent)}%",
            summary = "Nominal ${res.nominalRate}% compounded ${res.frequency.title}",
            detailsText = "Extra yield from compounding: +${String.format(java.util.Locale.US, "%.2f", res.extraYieldPercent)}%",
            timestamp = System.currentTimeMillis()
          )
        }
        InterestMethod.FLAT_VS_REDUCING -> {
          val res = state.flatVsReducingResult
          CalculationRecord(
            category = "INTEREST",
            title = "Flat ${res.flatRate}% = Reducing ${String.format(java.util.Locale.US, "%.2f", res.equivalentReducingRate)}%",
            summary = "Loan: ${CurrencyFormatter.format(res.principal)} for ${res.tenureMonths} Mo",
            detailsText = "Flat Interest: ${CurrencyFormatter.format(res.flatTotalInterest)}\nTrue Equivalent Reducing Rate: ${String.format(java.util.Locale.US, "%.2f", res.equivalentReducingRate)}%",
            timestamp = System.currentTimeMillis()
          )
        }
      }
      db.calculationHistoryDao().insert(record)
      _uiState.update { it.copy(toastMessage = "Interest calculation saved to history!") }
    }
  }
}
