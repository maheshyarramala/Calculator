package com.example.domain.calculator

import kotlin.math.ln
import kotlin.math.pow

object InterestCalculator {

  enum class TimeUnit(val title: String) {
    YEARS("Years"),
    MONTHS("Months"),
    DAYS("Days")
  }

  enum class CompoundingFrequency(val title: String, val timesPerYear: Double) {
    ANNUALLY("Annually (1/yr)", 1.0),
    SEMI_ANNUALLY("Semi-Annually (2/yr)", 2.0),
    QUARTERLY("Quarterly (4/yr)", 4.0),
    MONTHLY("Monthly (12/yr)", 12.0),
    DAILY("Daily (365/yr)", 365.0)
  }

  data class SimpleInterestResult(
    val principal: Double,
    val annualRate: Double,
    val timeYears: Double,
    val interestEarned: Double,
    val totalAmount: Double
  )

  fun calculateSimpleInterest(
    principal: Double,
    annualRate: Double,
    timeValue: Double,
    unit: TimeUnit
  ): SimpleInterestResult {
    if (principal <= 0.0 || annualRate <= 0.0 || timeValue <= 0.0) {
      return SimpleInterestResult(principal, annualRate, 0.0, 0.0, principal)
    }

    val timeYears = when (unit) {
      TimeUnit.YEARS -> timeValue
      TimeUnit.MONTHS -> timeValue / 12.0
      TimeUnit.DAYS -> timeValue / 365.0
    }

    val interest = (principal * annualRate * timeYears) / 100.0
    val total = principal + interest
    return SimpleInterestResult(principal, annualRate, timeYears, interest, total)
  }

  data class CompoundInterestResult(
    val principal: Double,
    val annualRate: Double,
    val timeYears: Double,
    val frequency: CompoundingFrequency,
    val totalAmount: Double,
    val totalInterest: Double,
    val yearlyBreakdown: List<YearlyGrowth>
  )

  data class YearlyGrowth(
    val year: Int,
    val interestEarnedSoFar: Double,
    val balance: Double
  )

  fun calculateCompoundInterest(
    principal: Double,
    annualRate: Double,
    timeYears: Double,
    frequency: CompoundingFrequency
  ): CompoundInterestResult {
    if (principal <= 0.0 || annualRate <= 0.0 || timeYears <= 0.0) {
      return CompoundInterestResult(principal, annualRate, timeYears, frequency, principal, 0.0, emptyList())
    }

    val n = frequency.timesPerYear
    val r = (annualRate / 100.0) / n
    val totalPeriods = n * timeYears
    val totalAmount = principal * (1.0 + r).pow(totalPeriods)
    val totalInterest = (totalAmount - principal).coerceAtLeast(0.0)

    val wholeYears = timeYears.toInt().coerceAtLeast(1)
    val yearly = mutableListOf<YearlyGrowth>()
    for (y in 1..wholeYears) {
      val p = n * y
      val bal = principal * (1.0 + r).pow(p)
      yearly.add(YearlyGrowth(y, (bal - principal).coerceAtLeast(0.0), bal))
    }

    return CompoundInterestResult(
      principal = principal,
      annualRate = annualRate,
      timeYears = timeYears,
      frequency = frequency,
      totalAmount = totalAmount,
      totalInterest = totalInterest,
      yearlyBreakdown = yearly
    )
  }

  data class DoublingResult(
    val annualRate: Double,
    val ruleOf72Years: Double,
    val exactDoublingYears: Double,
    val triplingYears: Double,
    val quadruplingYears: Double
  )

  fun calculateDoubling(annualRate: Double): DoublingResult {
    if (annualRate <= 0.0) {
      return DoublingResult(annualRate, 0.0, 0.0, 0.0, 0.0)
    }

    val rule72 = 72.0 / annualRate
    val r = annualRate / 100.0
    val exactDouble = ln(2.0) / ln(1.0 + r)
    val exactTriple = ln(3.0) / ln(1.0 + r)
    val exactQuadruple = ln(4.0) / ln(1.0 + r)

    return DoublingResult(
      annualRate = annualRate,
      ruleOf72Years = rule72,
      exactDoublingYears = exactDouble,
      triplingYears = exactTriple,
      quadruplingYears = exactQuadruple
    )
  }

  data class EarResult(
    val nominalRate: Double,
    val frequency: CompoundingFrequency,
    val effectiveRatePercent: Double,
    val extraYieldPercent: Double
  )

  fun calculateEffectiveRate(nominalRate: Double, frequency: CompoundingFrequency): EarResult {
    if (nominalRate <= 0.0) {
      return EarResult(nominalRate, frequency, 0.0, 0.0)
    }

    val n = frequency.timesPerYear
    val r = (nominalRate / 100.0) / n
    val ear = ((1.0 + r).pow(n) - 1.0) * 100.0
    val extra = (ear - nominalRate).coerceAtLeast(0.0)

    return EarResult(nominalRate, frequency, ear, extra)
  }

  data class FlatVsReducingResult(
    val principal: Double,
    val tenureMonths: Int,
    val flatRate: Double,
    val flatTotalInterest: Double,
    val flatMonthlyPayment: Double,
    val equivalentReducingRate: Double,
    val reducingTotalInterest: Double,
    val reducingMonthlyPayment: Double
  )

  fun compareFlatVsReducing(
    principal: Double,
    tenureMonths: Int,
    flatRatePercent: Double
  ): FlatVsReducingResult {
    if (principal <= 0.0 || tenureMonths <= 0 || flatRatePercent <= 0.0) {
      return FlatVsReducingResult(principal, tenureMonths, flatRatePercent, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    val tenureYears = tenureMonths / 12.0
    val flatInterest = (principal * flatRatePercent * tenureYears) / 100.0
    val flatTotal = principal + flatInterest
    val flatEmi = flatTotal / tenureMonths

    // Standard reducing balance EMI at the same nominal rate (e.g. 10%)
    val monthlyReducingR = (flatRatePercent / 12.0) / 100.0
    val factor = (1.0 + monthlyReducingR).pow(tenureMonths.toDouble())
    val reducingEmi = (principal * monthlyReducingR * factor) / (factor - 1.0)
    val reducingTotalInterest = (reducingEmi * tenureMonths) - principal

    // Equivalent reducing balance rate that matches the flat rate payment
    val approxReducingRate = (2.0 * tenureMonths / (tenureMonths + 1.0)) * flatRatePercent

    return FlatVsReducingResult(
      principal = principal,
      tenureMonths = tenureMonths,
      flatRate = flatRatePercent,
      flatTotalInterest = flatInterest,
      flatMonthlyPayment = flatEmi,
      equivalentReducingRate = approxReducingRate,
      reducingTotalInterest = reducingTotalInterest,
      reducingMonthlyPayment = reducingEmi
    )
  }
}
