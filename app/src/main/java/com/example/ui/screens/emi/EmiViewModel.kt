package com.example.ui.screens.emi

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CalculationRecord
import com.example.data.model.EmiResult
import com.example.data.model.PrepaymentEntry
import com.example.domain.calculator.EmiCalculator
import com.example.ui.components.CurrencyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmiUiState(
  val principalInput: String = "1000000",
  val interestRateInput: String = "8.5",
  val tenureInput: String = "10",
  val isTenureYears: Boolean = true,
  val prepayments: List<PrepaymentEntry> = emptyList(),
  val emiResult: EmiResult = EmiResult(1000000.0, 8.5, 120, 0.0, 0.0, 0.0, emptyList()),
  val scheduleFilterQuery: String = "",
  val showPrepaymentDialog: Boolean = false,
  val toastMessage: String? = null
)

class EmiViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(EmiUiState())
  val uiState: StateFlow<EmiUiState> = _uiState.asStateFlow()

  init {
    recalculate()
  }

  fun updatePrincipal(value: String) {
    _uiState.update { it.copy(principalInput = value) }
    recalculate()
  }

  fun addPrincipalPreset(amountToAdd: Double) {
    val current = _uiState.value.principalInput.toDoubleOrNull() ?: 0.0
    val newAmount = (current + amountToAdd).coerceAtLeast(0.0)
    _uiState.update { it.copy(principalInput = newAmount.toLong().toString()) }
    recalculate()
  }

  fun updateInterestRate(value: String) {
    _uiState.update { it.copy(interestRateInput = value) }
    recalculate()
  }

  fun updateTenure(value: String) {
    _uiState.update { it.copy(tenureInput = value) }
    recalculate()
  }

  fun toggleTenureUnit() {
    _uiState.update {
      val isYears = !it.isTenureYears
      val currentVal = it.tenureInput.toDoubleOrNull() ?: 0.0
      val converted = if (isYears) {
        (currentVal / 12.0).coerceAtLeast(1.0).let { v -> String.format(java.util.Locale.US, "%.1f", v).trimEnd('0').trimEnd('.') }
      } else {
        (currentVal * 12.0).toLong().toString()
      }
      it.copy(isTenureYears = isYears, tenureInput = converted)
    }
    recalculate()
  }

  fun setFilterQuery(query: String) {
    _uiState.update { it.copy(scheduleFilterQuery = query) }
  }

  fun setShowPrepaymentDialog(show: Boolean) {
    _uiState.update { it.copy(showPrepaymentDialog = show) }
  }

  fun addPrepayment(month: Int, amount: Double) {
    if (month <= 0 || amount <= 0.0) return
    val newEntry = PrepaymentEntry(month = month, amount = amount)
    _uiState.update {
      it.copy(
        prepayments = (it.prepayments + newEntry).sortedBy { p -> p.month },
        showPrepaymentDialog = false
      )
    }
    recalculate()
  }

  fun removePrepayment(id: String) {
    _uiState.update {
      it.copy(prepayments = it.prepayments.filterNot { p -> p.id == id })
    }
    recalculate()
  }

  fun clearPrepayments() {
    _uiState.update { it.copy(prepayments = emptyList()) }
    recalculate()
  }

  fun clearToast() {
    _uiState.update { it.copy(toastMessage = null) }
  }

  private fun recalculate() {
    val state = _uiState.value
    val principal = state.principalInput.toDoubleOrNull() ?: 0.0
    val rate = state.interestRateInput.toDoubleOrNull() ?: 0.0
    val tenureNum = state.tenureInput.toDoubleOrNull() ?: 0.0
    val totalMonths = if (state.isTenureYears) (tenureNum * 12).toInt() else tenureNum.toInt()

    val result = EmiCalculator.calculate(
      principal = principal,
      annualRate = rate,
      tenureMonths = totalMonths.coerceAtLeast(1),
      prepayments = state.prepayments
    )
    _uiState.update { it.copy(emiResult = result) }
  }

  fun saveCalculation(context: Context) {
    val state = _uiState.value
    val res = state.emiResult
    val summary = "EMI: ${CurrencyFormatter.format(res.monthlyEmi)}/mo | Total: ${CurrencyFormatter.format(res.totalPayment)}"
    val details = "Principal: ${CurrencyFormatter.format(res.principal)}\n" +
      "Rate: ${res.annualRate}%\n" +
      "Tenure: ${res.schedule.size} months\n" +
      "Interest: ${CurrencyFormatter.format(res.totalInterest)}\n" +
      (if (res.interestSaved > 0) "Interest Saved: ${CurrencyFormatter.format(res.interestSaved)}\n" else "")

    viewModelScope.launch {
      val db = AppDatabase.getDatabase(context)
      db.calculationHistoryDao().insert(
        CalculationRecord(
          category = "EMI",
          title = "Loan EMI (${CurrencyFormatter.formatCompact(res.principal)} @ ${res.annualRate}%)",
          summary = summary,
          detailsText = details,
          timestamp = System.currentTimeMillis()
        )
      )
      _uiState.update { it.copy(toastMessage = "EMI calculation saved to history!") }
    }
  }

  fun shareAmortization(context: Context) {
    val text = EmiCalculator.exportAmortizationAsText(_uiState.value.emiResult)
    val sendIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, text)
      putExtra(Intent.EXTRA_SUBJECT, "EMI Amortization Schedule - OmniCalc")
      type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share EMI Amortization Schedule")
    context.startActivity(shareIntent)
  }
}
