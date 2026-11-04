package com.example

import com.example.domain.calculator.LoanPrepaymentCalculator
import com.example.data.local.entity.LoanRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanPrepaymentCalculatorTest {

  @Test
  fun prepaymentReducesRemainingTenureAndInterest() {
    val principal = 100_000.0
    val emi = LoanRecord.calculateEmi(principal, 12.0, 24)
    val result = LoanPrepaymentCalculator.calculate(
      remainingPrincipal = principal,
      annualInterestRate = 12.0,
      monthlyEmi = emi,
      remainingMonths = 24,
      extraPrepayment = 20_000.0
    )

    assertTrue(result.isValid)
    assertFalse(result.isForeclosed)
    assertEquals(20_000.0, result.prepaymentApplied, 0.001)
    assertTrue(result.monthsAfter < result.monthsBefore)
    assertTrue(result.interestSaved > 0.0)
  }

  @Test
  fun prepaymentThatCoversPrincipalForeclosesLoan() {
    val principal = 50_000.0
    val emi = LoanRecord.calculateEmi(principal, 8.0, 12)
    val result = LoanPrepaymentCalculator.calculate(
      remainingPrincipal = principal,
      annualInterestRate = 8.0,
      monthlyEmi = emi,
      remainingMonths = 12,
      extraPrepayment = 60_000.0
    )

    assertTrue(result.isValid)
    assertTrue(result.isForeclosed)
    assertEquals(50_000.0, result.prepaymentApplied, 0.001)
    assertEquals(0, result.monthsAfter)
    assertEquals(0.0, result.interestAfter, 0.0)
  }

  @Test
  fun nonAmortizingEmiReturnsInvalidEstimate() {
    val result = LoanPrepaymentCalculator.calculate(
      remainingPrincipal = 100_000.0,
      annualInterestRate = 24.0,
      monthlyEmi = 1_000.0,
      remainingMonths = 12,
      extraPrepayment = 10_000.0
    )

    assertFalse(result.isValid)
  }
}
