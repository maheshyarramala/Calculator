package com.example.ui.screens.investment

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CalculationRecord
import com.example.data.model.InvestmentResult
import com.example.domain.calculator.InvestmentCalculators
import com.example.ui.components.CurrencyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InvestmentTab(val title: String) {
  SIP("SIP"),
  LUMPSUM("Lump Sum"),
  COMPOUND("Compound Int."),
  PPF("PPF"),
  FD("Fixed Deposit"),
  TRADE("Trade P&L")
}

data class InvestmentUiState(
  val selectedTab: InvestmentTab = InvestmentTab.SIP,
  // SIP
  val sipMonthly: String = "10000",
  val sipRate: String = "12",
  val sipYears: String = "10",
  // Lump sum
  val lumpPrincipal: String = "100000",
  val lumpRate: String = "12",
  val lumpYears: String = "5",
  // Compound Interest
  val ciPrincipal: String = "100000",
  val ciRate: String = "9.5",
  val ciYears: String = "5",
  val ciFrequency: InvestmentCalculators.CompoundingFrequency = InvestmentCalculators.CompoundingFrequency.QUARTERLY,
  // PPF
  val ppfAnnualDeposit: String = "150000",
  val ppfYears: String = "15",
  // FD
  val fdDeposit: String = "200000",
  val fdRate: String = "7.5",
  val fdYears: String = "3",
  // Result
  val result: InvestmentResult = InvestmentResult("SIP", 0.0, 0.0, 0.0, emptyList()),
  val toastMessage: String? = null
)

class InvestmentViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(InvestmentUiState())
  val uiState: StateFlow<InvestmentUiState> = _uiState.asStateFlow()

  init {
    recalculate()
  }

  fun selectTab(tab: InvestmentTab) {
    _uiState.update { it.copy(selectedTab = tab) }
    recalculate()
  }

  fun updateSip(monthly: String = _uiState.value.sipMonthly, rate: String = _uiState.value.sipRate, years: String = _uiState.value.sipYears) {
    _uiState.update { it.copy(sipMonthly = monthly, sipRate = rate, sipYears = years) }
    recalculate()
  }

  fun updateLump(principal: String = _uiState.value.lumpPrincipal, rate: String = _uiState.value.lumpRate, years: String = _uiState.value.lumpYears) {
    _uiState.update { it.copy(lumpPrincipal = principal, lumpRate = rate, lumpYears = years) }
    recalculate()
  }

  fun updateCi(principal: String = _uiState.value.ciPrincipal, rate: String = _uiState.value.ciRate, years: String = _uiState.value.ciYears, freq: InvestmentCalculators.CompoundingFrequency = _uiState.value.ciFrequency) {
    _uiState.update { it.copy(ciPrincipal = principal, ciRate = rate, ciYears = years, ciFrequency = freq) }
    recalculate()
  }

  fun updatePpf(annual: String = _uiState.value.ppfAnnualDeposit, years: String = _uiState.value.ppfYears) {
    _uiState.update { it.copy(ppfAnnualDeposit = annual, ppfYears = years) }
    recalculate()
  }

  fun updateFd(deposit: String = _uiState.value.fdDeposit, rate: String = _uiState.value.fdRate, years: String = _uiState.value.fdYears) {
    _uiState.update { it.copy(fdDeposit = deposit, fdRate = rate, fdYears = years) }
    recalculate()
  }

  fun clearToast() {
    _uiState.update { it.copy(toastMessage = null) }
  }

  private fun recalculate() {
    val state = _uiState.value
    val res = when (state.selectedTab) {
      InvestmentTab.SIP -> {
        val m = state.sipMonthly.toDoubleOrNull() ?: 0.0
        val r = state.sipRate.toDoubleOrNull() ?: 0.0
        val y = state.sipYears.toIntOrNull() ?: 1
        InvestmentCalculators.calculateSip(m, r, y)
      }
      InvestmentTab.LUMPSUM -> {
        val p = state.lumpPrincipal.toDoubleOrNull() ?: 0.0
        val r = state.lumpRate.toDoubleOrNull() ?: 0.0
        val y = state.lumpYears.toIntOrNull() ?: 1
        InvestmentCalculators.calculateLumpSum(p, r, y)
      }
      InvestmentTab.COMPOUND -> {
        val p = state.ciPrincipal.toDoubleOrNull() ?: 0.0
        val r = state.ciRate.toDoubleOrNull() ?: 0.0
        val y = state.ciYears.toIntOrNull() ?: 1
        InvestmentCalculators.calculateCompoundInterest(p, r, y, state.ciFrequency)
      }
      InvestmentTab.PPF -> {
        val a = state.ppfAnnualDeposit.toDoubleOrNull() ?: 0.0
        val y = state.ppfYears.toIntOrNull() ?: 15
        InvestmentCalculators.calculatePpf(a, 7.1, y)
      }
      InvestmentTab.FD -> {
        val d = state.fdDeposit.toDoubleOrNull() ?: 0.0
        val r = state.fdRate.toDoubleOrNull() ?: 0.0
        val y = state.fdYears.toDoubleOrNull() ?: 1.0
        InvestmentCalculators.calculateFd(d, r, y)
      }
      InvestmentTab.TRADE -> InvestmentResult("Trade P&L", 0.0, 0.0, 0.0, emptyList())
    }
    _uiState.update { it.copy(result = res) }
  }

  fun saveCalculation(context: Context) {
    val state = _uiState.value
    val res = state.result
    val summary = "Invested: ${CurrencyFormatter.format(res.investedAmount)} | Total: ${CurrencyFormatter.format(res.totalValue)}"
    val details = "Category: ${state.selectedTab.title}\n" +
      "Invested: ${CurrencyFormatter.format(res.investedAmount)}\n" +
      "Estimated Returns: ${CurrencyFormatter.format(res.estimatedReturns)}\n" +
      "Total Maturity Value: ${CurrencyFormatter.format(res.totalValue)}"

    viewModelScope.launch {
      val db = AppDatabase.getDatabase(context)
      db.calculationHistoryDao().insert(
        CalculationRecord(
          category = state.selectedTab.name,
          title = "${state.selectedTab.title} Plan (${CurrencyFormatter.formatCompact(res.totalValue)})",
          summary = summary,
          detailsText = details,
          timestamp = System.currentTimeMillis()
        )
      )
      _uiState.update { it.copy(toastMessage = "${state.selectedTab.title} saved to history!") }
    }
  }
}
