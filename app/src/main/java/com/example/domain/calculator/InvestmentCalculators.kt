package com.example.domain.calculator

import com.example.data.model.InvestmentResult
import com.example.data.model.YearlyBreakdown
import kotlin.math.pow

object InvestmentCalculators {

  enum class CompoundingFrequency(val title: String, val timesPerYear: Int) {
    ANNUALLY("Annually", 1),
    SEMI_ANNUALLY("Semi-Annually", 2),
    QUARTERLY("Quarterly", 4),
    MONTHLY("Monthly", 12)
  }

  fun calculateSip(
    monthlyInvestment: Double,
    annualReturnRate: Double,
    tenureYears: Int
  ): InvestmentResult {
    if (monthlyInvestment <= 0.0 || tenureYears <= 0) {
      return InvestmentResult("SIP", 0.0, 0.0, 0.0, emptyList())
    }

    val monthlyRate = (annualReturnRate / 12.0) / 100.0
    val totalMonths = tenureYears * 12
    val investedAmount = monthlyInvestment * totalMonths

    val totalValue = if (monthlyRate <= 0.0) {
      investedAmount
    } else {
      monthlyInvestment * (((1.0 + monthlyRate).pow(totalMonths.toDouble()) - 1.0) / monthlyRate) * (1.0 + monthlyRate)
    }

    val estimatedReturns = (totalValue - investedAmount).coerceAtLeast(0.0)

    val yearlyBreakdown = mutableListOf<YearlyBreakdown>()
    for (year in 1..tenureYears) {
      val monthsSoFar = year * 12
      val investedSoFar = monthlyInvestment * monthsSoFar
      val valueSoFar = if (monthlyRate <= 0.0) {
        investedSoFar
      } else {
        monthlyInvestment * (((1.0 + monthlyRate).pow(monthsSoFar.toDouble()) - 1.0) / monthlyRate) * (1.0 + monthlyRate)
      }
      val returnsSoFar = (valueSoFar - investedSoFar).coerceAtLeast(0.0)
      yearlyBreakdown.add(YearlyBreakdown(year, investedSoFar, returnsSoFar, valueSoFar))
    }

    return InvestmentResult(
      title = "SIP Investment",
      investedAmount = investedAmount,
      estimatedReturns = estimatedReturns,
      totalValue = totalValue,
      yearlyBreakdown = yearlyBreakdown
    )
  }

  fun calculateLumpSum(
    initialInvestment: Double,
    annualReturnRate: Double,
    tenureYears: Int
  ): InvestmentResult {
    if (initialInvestment <= 0.0 || tenureYears <= 0) {
      return InvestmentResult("Lump Sum", 0.0, 0.0, 0.0, emptyList())
    }

    val rate = annualReturnRate / 100.0
    val totalValue = initialInvestment * (1.0 + rate).pow(tenureYears.toDouble())
    val estimatedReturns = (totalValue - initialInvestment).coerceAtLeast(0.0)

    val yearlyBreakdown = mutableListOf<YearlyBreakdown>()
    for (year in 1..tenureYears) {
      val valueSoFar = initialInvestment * (1.0 + rate).pow(year.toDouble())
      val returnsSoFar = (valueSoFar - initialInvestment).coerceAtLeast(0.0)
      yearlyBreakdown.add(YearlyBreakdown(year, initialInvestment, returnsSoFar, valueSoFar))
    }

    return InvestmentResult(
      title = "Lump Sum",
      investedAmount = initialInvestment,
      estimatedReturns = estimatedReturns,
      totalValue = totalValue,
      yearlyBreakdown = yearlyBreakdown
    )
  }

  fun calculateCompoundInterest(
    principal: Double,
    annualRate: Double,
    tenureYears: Int,
    frequency: CompoundingFrequency
  ): InvestmentResult {
    if (principal <= 0.0 || tenureYears <= 0) {
      return InvestmentResult("Compound Interest", 0.0, 0.0, 0.0, emptyList())
    }

    val n = frequency.timesPerYear.toDouble()
    val r = (annualRate / 100.0) / n

    val yearlyBreakdown = mutableListOf<YearlyBreakdown>()
    for (year in 1..tenureYears) {
      val periods = n * year
      val valueSoFar = principal * (1.0 + r).pow(periods)
      val returnsSoFar = (valueSoFar - principal).coerceAtLeast(0.0)
      yearlyBreakdown.add(YearlyBreakdown(year, principal, returnsSoFar, valueSoFar))
    }

    val finalTotal = yearlyBreakdown.lastOrNull()?.balance ?: principal
    val finalReturns = (finalTotal - principal).coerceAtLeast(0.0)

    return InvestmentResult(
      title = "Compound Interest (${frequency.title})",
      investedAmount = principal,
      estimatedReturns = finalReturns,
      totalValue = finalTotal,
      yearlyBreakdown = yearlyBreakdown
    )
  }

  fun calculatePpf(
    annualDeposit: Double,
    annualRate: Double = 7.1,
    tenureYears: Int = 15
  ): InvestmentResult {
    if (annualDeposit <= 0.0 || tenureYears <= 0) {
      return InvestmentResult("PPF", 0.0, 0.0, 0.0, emptyList())
    }

    val r = annualRate / 100.0
    var balance = 0.0
    var totalInvested = 0.0
    val yearlyBreakdown = mutableListOf<YearlyBreakdown>()

    for (year in 1..tenureYears) {
      totalInvested += annualDeposit
      val opening = balance + annualDeposit
      val interest = opening * r
      balance = opening + interest
      val returns = balance - totalInvested
      yearlyBreakdown.add(YearlyBreakdown(year, totalInvested, returns, balance))
    }

    return InvestmentResult(
      title = "PPF Account",
      investedAmount = totalInvested,
      estimatedReturns = (balance - totalInvested).coerceAtLeast(0.0),
      totalValue = balance,
      yearlyBreakdown = yearlyBreakdown
    )
  }

  fun calculateFd(
    depositAmount: Double,
    annualRate: Double,
    tenureYears: Double,
    frequency: CompoundingFrequency = CompoundingFrequency.QUARTERLY
  ): InvestmentResult {
    if (depositAmount <= 0.0 || tenureYears <= 0.0) {
      return InvestmentResult("Fixed Deposit", 0.0, 0.0, 0.0, emptyList())
    }

    val n = frequency.timesPerYear.toDouble()
    val r = (annualRate / 100.0) / n
    val totalPeriods = n * tenureYears
    val totalValue = depositAmount * (1.0 + r).pow(totalPeriods)
    val returns = (totalValue - depositAmount).coerceAtLeast(0.0)

    val wholeYears = tenureYears.toInt().coerceAtLeast(1)
    val yearlyBreakdown = mutableListOf<YearlyBreakdown>()
    for (year in 1..wholeYears) {
      val periods = n * year
      val valSoFar = depositAmount * (1.0 + r).pow(periods)
      yearlyBreakdown.add(YearlyBreakdown(year, depositAmount, (valSoFar - depositAmount).coerceAtLeast(0.0), valSoFar))
    }

    return InvestmentResult(
      title = "Fixed Deposit (FD)",
      investedAmount = depositAmount,
      estimatedReturns = returns,
      totalValue = totalValue,
      yearlyBreakdown = yearlyBreakdown
    )
  }
}
