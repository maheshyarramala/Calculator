package com.example.domain.calculator

import kotlin.math.max
import kotlin.math.min

object IncomeTaxCalculator {

  data class RegimeTaxBreakdown(
    val regimeName: String,
    val grossIncome: Double,
    val standardDeduction: Double,
    val otherDeductions: Double,
    val totalDeductions: Double,
    val taxableIncome: Double,
    val slabTax: Double,
    val rebate87A: Double,
    val taxAfterRebate: Double,
    val cess: Double, // 4% Health & Education Cess
    val totalTaxPayable: Double,
    val effectiveTaxRate: Double
  )

  data class TaxComparisonResult(
    val grossSalary: Double,
    val financialYear: String, // "FY 2024-25" or "FY 2025-26"
    val oldRegime: RegimeTaxBreakdown,
    val newRegime: RegimeTaxBreakdown,
    val recommendedRegime: String, // "New Regime" or "Old Regime"
    val taxSavings: Double,
    val recommendationSummary: String
  )

  /**
   * Calculates Income Tax for FY 2024-25 / FY 2025-26 comparing Old vs New Tax Regimes.
   */
  fun calculateTax(
    grossSalary: Double,
    section80C: Double = 0.0, // Max 1,50,000 (EPF, PPF, ELSS, LIC)
    section80D: Double = 0.0, // Health insurance (Self & Parents, max 75k)
    hraExemption: Double = 0.0, // House rent allowance
    homeLoanInterest24b: Double = 0.0, // Max 2,00,000
    otherDeductions: Double = 0.0, // 80CCD(1B) NPS up to 50k, 80TTA, etc.
    financialYear: String = "FY 2024-25"
  ): TaxComparisonResult {
    val validGross = max(0.0, grossSalary)

    // ==========================================
    // 1. NEW TAX REGIME CALCULATION
    // ==========================================
    // New regime standard deduction: ₹75,000 for salaried employees (Budget 2024 onwards)
    val newRegimeStandardDeduction = if (validGross > 0.0) min(75000.0, validGross) else 0.0
    val newRegimeTaxable = max(0.0, validGross - newRegimeStandardDeduction)

    // New Regime Slabs:
    // 0 - 3,00,000 : 0%
    // 3,00,001 - 7,00,000 : 5%
    // 7,00,001 - 10,00,000 : 10%
    // 10,00,001 - 12,00,000 : 15%
    // 12,00,001 - 15,00,000 : 20%
    // Above 15,00,000 : 30%
    var newSlabTax = 0.0
    if (newRegimeTaxable > 300000.0) {
      newSlabTax += min(400000.0, newRegimeTaxable - 300000.0) * 0.05
    }
    if (newRegimeTaxable > 700000.0) {
      newSlabTax += min(300000.0, newRegimeTaxable - 700000.0) * 0.10
    }
    if (newRegimeTaxable > 1000000.0) {
      newSlabTax += min(200000.0, newRegimeTaxable - 1000000.0) * 0.15
    }
    if (newRegimeTaxable > 1200000.0) {
      newSlabTax += min(300000.0, newRegimeTaxable - 1200000.0) * 0.20
    }
    if (newRegimeTaxable > 1500000.0) {
      newSlabTax += (newRegimeTaxable - 1500000.0) * 0.30
    }

    // Section 87A Rebate in New Regime:
    // If taxable income <= 7,00,000, 100% tax rebate (max 25,000)
    // Marginal relief: If taxable income slightly exceeds 7,00,000, tax cannot exceed (taxable - 7,00,000)
    var newRebate = 0.0
    var newTaxAfterRebate = newSlabTax
    if (newRegimeTaxable <= 700000.0) {
      newRebate = newSlabTax
      newTaxAfterRebate = 0.0
    } else {
      // Marginal relief check
      val excessIncome = newRegimeTaxable - 700000.0
      if (newSlabTax > excessIncome) {
        newRebate = newSlabTax - excessIncome
        newTaxAfterRebate = excessIncome
      }
    }

    val newCess = newTaxAfterRebate * 0.04
    val newTotalTax = newTaxAfterRebate + newCess
    val newEffectiveRate = if (validGross > 0) (newTotalTax / validGross) * 100.0 else 0.0

    val newRegimeBreakdown = RegimeTaxBreakdown(
      regimeName = "New Tax Regime",
      grossIncome = validGross,
      standardDeduction = newRegimeStandardDeduction,
      otherDeductions = 0.0,
      totalDeductions = newRegimeStandardDeduction,
      taxableIncome = newRegimeTaxable,
      slabTax = newSlabTax,
      rebate87A = newRebate,
      taxAfterRebate = newTaxAfterRebate,
      cess = newCess,
      totalTaxPayable = newTotalTax,
      effectiveTaxRate = newEffectiveRate
    )

    // ==========================================
    // 2. OLD TAX REGIME CALCULATION
    // ==========================================
    // Old regime standard deduction: ₹50,000
    val oldRegimeStandardDeduction = if (validGross > 0.0) min(50000.0, validGross) else 0.0
    val capped80C = min(150000.0, max(0.0, section80C))
    val capped80D = min(100000.0, max(0.0, section80D))
    val capped24b = min(200000.0, max(0.0, homeLoanInterest24b))
    val validHra = max(0.0, hraExemption)
    val validOther = max(0.0, otherDeductions)

    val oldTotalOtherDeductions = capped80C + capped80D + capped24b + validHra + validOther
    val oldTotalDeductions = oldRegimeStandardDeduction + oldTotalOtherDeductions
    val oldRegimeTaxable = max(0.0, validGross - oldTotalDeductions)

    // Old Regime Slabs:
    // 0 - 2,50,000 : 0%
    // 2,50,001 - 5,00,000 : 5%
    // 5,00,001 - 10,00,000 : 20%
    // Above 10,00,000 : 30%
    var oldSlabTax = 0.0
    if (oldRegimeTaxable > 250000.0) {
      oldSlabTax += min(250000.0, oldRegimeTaxable - 250000.0) * 0.05
    }
    if (oldRegimeTaxable > 500000.0) {
      oldSlabTax += min(500000.0, oldRegimeTaxable - 500000.0) * 0.20
    }
    if (oldRegimeTaxable > 1000000.0) {
      oldSlabTax += (oldRegimeTaxable - 1000000.0) * 0.30
    }

    // Section 87A Rebate in Old Regime:
    // If taxable income <= 5,00,000, 100% tax rebate (max 12,500)
    var oldRebate = 0.0
    var oldTaxAfterRebate = oldSlabTax
    if (oldRegimeTaxable <= 500000.0) {
      oldRebate = oldSlabTax
      oldTaxAfterRebate = 0.0
    }

    val oldCess = oldTaxAfterRebate * 0.04
    val oldTotalTax = oldTaxAfterRebate + oldCess
    val oldEffectiveRate = if (validGross > 0) (oldTotalTax / validGross) * 100.0 else 0.0

    val oldRegimeBreakdown = RegimeTaxBreakdown(
      regimeName = "Old Tax Regime",
      grossIncome = validGross,
      standardDeduction = oldRegimeStandardDeduction,
      otherDeductions = oldTotalOtherDeductions,
      totalDeductions = oldTotalDeductions,
      taxableIncome = oldRegimeTaxable,
      slabTax = oldSlabTax,
      rebate87A = oldRebate,
      taxAfterRebate = oldTaxAfterRebate,
      cess = oldCess,
      totalTaxPayable = oldTotalTax,
      effectiveTaxRate = oldEffectiveRate
    )

    // Recommendation logic
    val taxDiff = kotlin.math.abs(oldTotalTax - newTotalTax)
    val (recommended, summary) = when {
      newTotalTax < oldTotalTax -> {
        "New Regime" to "New Regime saves you ₹${formatWhole(taxDiff)} in tax!"
      }
      oldTotalTax < newTotalTax -> {
        "Old Regime" to "Old Regime saves you ₹${formatWhole(taxDiff)} with your investments & deductions!"
      }
      else -> {
        "Equal Tax" to "Both regimes result in the exact same tax liability."
      }
    }

    return TaxComparisonResult(
      grossSalary = validGross,
      financialYear = financialYear,
      oldRegime = oldRegimeBreakdown,
      newRegime = newRegimeBreakdown,
      recommendedRegime = recommended,
      taxSavings = taxDiff,
      recommendationSummary = summary
    )
  }

  private fun formatWhole(amount: Double): String {
    val rounded = kotlin.math.round(amount).toLong()
    return java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(rounded)
  }
}
