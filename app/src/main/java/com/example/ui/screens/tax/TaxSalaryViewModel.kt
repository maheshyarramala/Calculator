package com.example.ui.screens.tax

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CalculationRecord
import com.example.domain.calculator.IncomeTaxCalculator
import com.example.domain.calculator.SalaryCalculator
import com.example.ui.components.CurrencyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaxSalaryTab(val title: String) {
  INCOME_TAX("Income Tax (Old vs New)"),
  SALARY_BREAKDOWN("In-Hand Salary (CTC)")
}

data class TaxSalaryUiState(
  val selectedTab: TaxSalaryTab = TaxSalaryTab.INCOME_TAX,
  
  // Income Tax inputs
  val taxAnnualGross: String = "1200000",
  val taxSection80C: String = "150000",
  val taxSection80D: String = "25000",
  val taxHra: String = "100000",
  val taxHomeLoan24b: String = "0",
  val taxOtherDeductions: String = "0",
  val taxFinancialYear: String = "FY 2024-25",
  
  // Tax calculation result
  val taxResult: IncomeTaxCalculator.TaxComparisonResult = IncomeTaxCalculator.calculateTax(1200000.0, 150000.0, 25000.0, 100000.0),

  // Salary Calculator inputs
  val salaryAnnualCtc: String = "1200000",
  val salaryBasicPercentage: Float = 40f,
  val salaryHraPercentage: Float = 50f,
  val salaryIncludeGratuity: Boolean = true,
  val salaryIncludeEmployerPf: Boolean = true,
  val salaryRegime: String = "New Regime",

  // Salary calculation result
  val salaryResult: SalaryCalculator.SalaryBreakdown = SalaryCalculator.calculate(1200000.0),

  val toastMessage: String? = null
)

class TaxSalaryViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(TaxSalaryUiState())
  val uiState: StateFlow<TaxSalaryUiState> = _uiState.asStateFlow()

  fun selectTab(tab: TaxSalaryTab) {
    _uiState.update { it.copy(selectedTab = tab) }
  }

  fun updateTaxInputs(
    gross: String = _uiState.value.taxAnnualGross,
    c80: String = _uiState.value.taxSection80C,
    d80: String = _uiState.value.taxSection80D,
    hra: String = _uiState.value.taxHra,
    loan24b: String = _uiState.value.taxHomeLoan24b,
    other: String = _uiState.value.taxOtherDeductions,
    fy: String = _uiState.value.taxFinancialYear
  ) {
    val parsedGross = gross.toDoubleOrNull() ?: 0.0
    val parsed80C = c80.toDoubleOrNull() ?: 0.0
    val parsed80D = d80.toDoubleOrNull() ?: 0.0
    val parsedHra = hra.toDoubleOrNull() ?: 0.0
    val parsed24b = loan24b.toDoubleOrNull() ?: 0.0
    val parsedOther = other.toDoubleOrNull() ?: 0.0

    val res = IncomeTaxCalculator.calculateTax(
      grossSalary = parsedGross,
      section80C = parsed80C,
      section80D = parsed80D,
      hraExemption = parsedHra,
      homeLoanInterest24b = parsed24b,
      otherDeductions = parsedOther,
      financialYear = fy
    )

    _uiState.update {
      it.copy(
        taxAnnualGross = gross,
        taxSection80C = c80,
        taxSection80D = d80,
        taxHra = hra,
        taxHomeLoan24b = loan24b,
        taxOtherDeductions = other,
        taxFinancialYear = fy,
        taxResult = res
      )
    }
  }

  fun updateSalaryInputs(
    ctc: String = _uiState.value.salaryAnnualCtc,
    basicPct: Float = _uiState.value.salaryBasicPercentage,
    hraPct: Float = _uiState.value.salaryHraPercentage,
    includeGratuity: Boolean = _uiState.value.salaryIncludeGratuity,
    includeEmployerPf: Boolean = _uiState.value.salaryIncludeEmployerPf,
    regime: String = _uiState.value.salaryRegime
  ) {
    val parsedCtc = ctc.toDoubleOrNull() ?: 0.0
    val res = SalaryCalculator.calculate(
      annualCtc = parsedCtc,
      basicPercentage = basicPct.toDouble(),
      hraPercentage = hraPct.toDouble(),
      includeGratuityInCtc = includeGratuity,
      includeEmployerPfInCtc = includeEmployerPf,
      regime = regime
    )

    _uiState.update {
      it.copy(
        salaryAnnualCtc = ctc,
        salaryBasicPercentage = basicPct,
        salaryHraPercentage = hraPct,
        salaryIncludeGratuity = includeGratuity,
        salaryIncludeEmployerPf = includeEmployerPf,
        salaryRegime = regime,
        salaryResult = res
      )
    }
  }

  fun clearToast() {
    _uiState.update { it.copy(toastMessage = null) }
  }

  fun saveTaxCalculation(context: Context) {
    val s = _uiState.value
    val res = s.taxResult
    val summary = "Gross: ${CurrencyFormatter.format(res.grossSalary)} | Rec: ${res.recommendedRegime} (Saves ${CurrencyFormatter.format(res.taxSavings)})"
    val details = "Gross Salary: ${res.grossSalary}\n" +
      "Old Regime Tax: ${CurrencyFormatter.format(res.oldRegime.totalTaxPayable)}\n" +
      "New Regime Tax: ${CurrencyFormatter.format(res.newRegime.totalTaxPayable)}\n" +
      "Recommendation: ${res.recommendationSummary}\n" +
      "80C: ${s.taxSection80C}\n80D: ${s.taxSection80D}\nHRA: ${s.taxHra}"

    viewModelScope.launch {
      val db = AppDatabase.getDatabase(context)
      db.calculationHistoryDao().insert(
        CalculationRecord(
          category = "TAX",
          title = "Income Tax Calculation (${res.recommendedRegime})",
          summary = summary,
          detailsText = details,
          timestamp = System.currentTimeMillis()
        )
      )
      _uiState.update { it.copy(toastMessage = "Income Tax calculation saved to history!") }
    }
  }

  fun saveSalaryCalculation(context: Context) {
    val s = _uiState.value
    val res = s.salaryResult
    val summary = "CTC: ${CurrencyFormatter.format(res.annualCtc)} | In-Hand: ${CurrencyFormatter.format(res.monthlyInHand)}/mo"
    val details = "Annual CTC: ${CurrencyFormatter.format(res.annualCtc)}\n" +
      "Monthly Gross: ${CurrencyFormatter.format(res.monthlyGrossSalary)}\n" +
      "Monthly In-Hand: ${CurrencyFormatter.format(res.monthlyInHand)}\n" +
      "Monthly EPF: ${CurrencyFormatter.format(res.monthlyEpf)}\n" +
      "Monthly TDS: ${CurrencyFormatter.format(res.monthlyTds)}\n" +
      "Regime: ${res.taxRegimeUsed}"

    viewModelScope.launch {
      val db = AppDatabase.getDatabase(context)
      db.calculationHistoryDao().insert(
        CalculationRecord(
          category = "SALARY",
          title = "Take-Home Salary (${CurrencyFormatter.format(res.monthlyInHand)}/mo)",
          summary = summary,
          detailsText = details,
          timestamp = System.currentTimeMillis()
        )
      )
      _uiState.update { it.copy(toastMessage = "Salary breakdown saved to history!") }
    }
  }
}
