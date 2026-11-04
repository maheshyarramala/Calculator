package com.example.domain.calculator

import kotlin.math.max
import kotlin.math.min

data class LoanPrepaymentResult(
  val monthsBefore: Int,
  val monthsAfter: Int,
  val interestBefore: Double,
  val interestAfter: Double,
  val prepaymentApplied: Double,
  val isForeclosed: Boolean,
  val isValid: Boolean
) {
  val monthsSaved: Int
    get() = (monthsBefore - monthsAfter).coerceAtLeast(0)

  val interestSaved: Double
    get() = (interestBefore - interestAfter).coerceAtLeast(0.0)
}

object LoanPrepaymentCalculator {
  private data class Amortization(
    val months: Int,
    val interest: Double,
    val isPaidOff: Boolean
  )

  fun calculate(
    remainingPrincipal: Double,
    annualInterestRate: Double,
    monthlyEmi: Double,
    remainingMonths: Int,
    extraPrepayment: Double
  ): LoanPrepaymentResult {
    val invalidResult = LoanPrepaymentResult(0, 0, 0.0, 0.0, 0.0, false, false)
    if (
      !remainingPrincipal.isFinite() || remainingPrincipal <= 0.0 ||
      !annualInterestRate.isFinite() || annualInterestRate < 0.0 ||
      !monthlyEmi.isFinite() || monthlyEmi <= 0.0 ||
      remainingMonths <= 0 ||
      !extraPrepayment.isFinite() || extraPrepayment < 0.0
    ) return invalidResult

    val monthlyRate = annualInterestRate / 1200.0
    val original = amortize(remainingPrincipal, monthlyRate, monthlyEmi, remainingMonths)
    if (!original.isPaidOff) return invalidResult

    val appliedPrepayment = min(extraPrepayment, remainingPrincipal)
    val newPrincipal = max(0.0, remainingPrincipal - appliedPrepayment)
    if (newPrincipal <= 0.005) {
      return LoanPrepaymentResult(
        monthsBefore = original.months,
        monthsAfter = 0,
        interestBefore = original.interest,
        interestAfter = 0.0,
        prepaymentApplied = appliedPrepayment,
        isForeclosed = true,
        isValid = true
      )
    }

    val afterPrepayment = amortize(newPrincipal, monthlyRate, monthlyEmi, MAX_SIMULATION_MONTHS)
    if (!afterPrepayment.isPaidOff) return invalidResult

    return LoanPrepaymentResult(
      monthsBefore = original.months,
      monthsAfter = afterPrepayment.months,
      interestBefore = original.interest,
      interestAfter = afterPrepayment.interest,
      prepaymentApplied = appliedPrepayment,
      isForeclosed = false,
      isValid = true
    )
  }

  private fun amortize(
    principal: Double,
    monthlyRate: Double,
    emi: Double,
    maxMonths: Int
  ): Amortization {
    var balance = principal
    var totalInterest = 0.0
    var months = 0

    while (balance > 0.005 && months < maxMonths) {
      val interest = balance * monthlyRate
      val payment = min(emi, balance + interest)
      val principalPaid = payment - interest
      if (principalPaid <= 0.0) return Amortization(months, totalInterest, false)

      totalInterest += interest
      balance = max(0.0, balance - principalPaid)
      months++
    }

    return Amortization(months, totalInterest, balance <= 0.005)
  }

  private const val MAX_SIMULATION_MONTHS = 1200
}
