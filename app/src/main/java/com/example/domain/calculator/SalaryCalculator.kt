package com.example.domain.calculator

import kotlin.math.max
import kotlin.math.roundToLong

object SalaryCalculator {

  data class SalaryBreakdown(
    val annualCtc: Double,
    val monthlyCtc: Double,
    
    // Earnings (Annual & Monthly)
    val annualBasic: Double,
    val monthlyBasic: Double,
    val annualHra: Double,
    val monthlyHra: Double,
    val annualSpecialAllowance: Double,
    val monthlySpecialAllowance: Double,
    val annualGrossSalary: Double,
    val monthlyGrossSalary: Double,

    // Deductions (Annual & Monthly)
    val annualEpf: Double,
    val monthlyEpf: Double,
    val annualProfessionalTax: Double,
    val monthlyProfessionalTax: Double,
    val annualGratuity: Double,
    val monthlyGratuity: Double,
    val annualTds: Double,
    val monthlyTds: Double,
    val annualTotalDeductions: Double,
    val monthlyTotalDeductions: Double,

    // Net Take-Home (In-Hand)
    val annualInHand: Double,
    val monthlyInHand: Double,
    
    // Tax details
    val taxRegimeUsed: String,
    val effectiveTaxRate: Double
  )

  /**
   * Calculates monthly and annual in-hand salary from CTC.
   */
  fun calculate(
    annualCtc: Double,
    basicPercentage: Double = 40.0, // 40% of CTC by default
    hraPercentage: Double = 50.0, // 50% of Basic by default (20% of CTC)
    includeGratuityInCtc: Boolean = true,
    includeEmployerPfInCtc: Boolean = true,
    monthlyProfessionalTax: Double = 200.0,
    regime: String = "New Regime", // "New Regime" or "Old Regime"
    section80C: Double = 0.0,
    section80D: Double = 0.0
  ): SalaryBreakdown {
    val validCtc = max(0.0, annualCtc)

    // Basic salary calculation
    val basicPct = (basicPercentage.coerceIn(20.0, 70.0)) / 100.0
    val annualBasic = validCtc * basicPct
    val monthlyBasic = annualBasic / 12.0

    // HRA calculation (% of Basic)
    val hraPct = (hraPercentage.coerceIn(0.0, 50.0)) / 100.0
    val annualHra = annualBasic * hraPct
    val monthlyHra = annualHra / 12.0

    // Employer PF (12% of Basic, standard part of CTC package in India)
    val annualEmployerPf = if (includeEmployerPfInCtc) annualBasic * 0.12 else 0.0

    // Gratuity (~4.81% of Basic: 15/26 days per year)
    val annualGratuity = if (includeGratuityInCtc) annualBasic * (15.0 / 26.0 / 12.0) else 0.0
    val monthlyGratuity = annualGratuity / 12.0

    // Special Allowance fills the rest of the CTC
    val fixedBenefits = annualBasic + annualHra + annualEmployerPf + annualGratuity
    val annualSpecialAllowance = max(0.0, validCtc - fixedBenefits)
    val monthlySpecialAllowance = annualSpecialAllowance / 12.0

    // Gross Salary credited on paper (CTC minus employer-side retainers)
    val annualGrossSalary = annualBasic + annualHra + annualSpecialAllowance
    val monthlyGrossSalary = annualGrossSalary / 12.0

    // Employee Deductions:
    // 1. Employee PF (12% of Basic)
    val annualEpf = annualBasic * 0.12
    val monthlyEpf = annualEpf / 12.0

    // 2. Professional Tax
    val annualPt = monthlyProfessionalTax * 12.0

    // 3. TDS (Income Tax calculation using real tax slabs)
    // Pass employee PF into 80C automatically if old regime
    val effective80C = max(section80C, annualEpf)
    val taxResult = IncomeTaxCalculator.calculateTax(
      grossSalary = annualGrossSalary,
      section80C = effective80C,
      section80D = section80D,
      hraExemption = if (regime == "Old Regime") annualHra else 0.0
    )

    val annualTds = if (regime == "Old Regime") {
      taxResult.oldRegime.totalTaxPayable
    } else {
      taxResult.newRegime.totalTaxPayable
    }
    val monthlyTds = annualTds / 12.0

    // Total employee deductions from gross salary
    val annualTotalDeductions = annualEpf + annualPt + annualTds
    val monthlyTotalDeductions = annualTotalDeductions / 12.0

    // Net In-Hand Salary
    val annualInHand = max(0.0, annualGrossSalary - annualTotalDeductions)
    val monthlyInHand = max(0.0, monthlyGrossSalary - monthlyTotalDeductions)

    val effectiveTaxRate = if (annualGrossSalary > 0) (annualTds / annualGrossSalary) * 100.0 else 0.0

    return SalaryBreakdown(
      annualCtc = validCtc,
      monthlyCtc = validCtc / 12.0,
      annualBasic = annualBasic,
      monthlyBasic = monthlyBasic,
      annualHra = annualHra,
      monthlyHra = monthlyHra,
      annualSpecialAllowance = annualSpecialAllowance,
      monthlySpecialAllowance = monthlySpecialAllowance,
      annualGrossSalary = annualGrossSalary,
      monthlyGrossSalary = monthlyGrossSalary,
      annualEpf = annualEpf,
      monthlyEpf = monthlyEpf,
      annualProfessionalTax = annualPt,
      monthlyProfessionalTax = monthlyProfessionalTax,
      annualGratuity = annualGratuity,
      monthlyGratuity = monthlyGratuity,
      annualTds = annualTds,
      monthlyTds = monthlyTds,
      annualTotalDeductions = annualTotalDeductions,
      monthlyTotalDeductions = monthlyTotalDeductions,
      annualInHand = annualInHand,
      monthlyInHand = monthlyInHand,
      taxRegimeUsed = regime,
      effectiveTaxRate = effectiveTaxRate
    )
  }
}
