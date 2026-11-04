package com.example

import com.example.domain.calculator.UtilityCalculators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoCostEmiCalculatorTest {

  @Test
  fun estimatesHiddenInterestGstAndProcessingFee() {
    val result = UtilityCalculators.calculateTrueNoCostEmi(
      productPrice = 6000.0,
      annualBankRatePercent = 16.0,
      tenureMonths = 6,
      processingFee = 199.0,
      gstRatePercent = 18.0
    )

    assertEquals(1000.0, result.monthlyBaseEmi, 0.001)
    assertTrue(result.effectiveLoanPrincipal in 0.0..6000.0)
    assertEquals(
      6000.0 - result.effectiveLoanPrincipal,
      result.upfrontMerchantDiscount,
      0.001
    )
    assertEquals(result.totalInterestComponent * 0.18, result.gstOnInterest18Percent, 0.001)
    assertEquals(199.0 * 1.18, result.processingFeeWithGst, 0.001)
    assertEquals(
      result.gstOnInterest18Percent + result.processingFeeWithGst,
      result.totalHiddenCost,
      0.001
    )
    assertEquals(6000.0 + result.totalHiddenCost, result.actualTotalOutflow, 0.001)
  }

  @Test
  fun zeroInterestHasNoDiscountButStillIncludesProcessingFee() {
    val result = UtilityCalculators.calculateTrueNoCostEmi(
      productPrice = 12000.0,
      annualBankRatePercent = 0.0,
      tenureMonths = 12,
      processingFee = 200.0
    )

    assertEquals(12000.0, result.effectiveLoanPrincipal, 0.001)
    assertEquals(0.0, result.totalInterestComponent, 0.0)
    assertEquals(236.0, result.processingFeeWithGst, 0.001)
    assertEquals(12236.0, result.actualTotalOutflow, 0.001)
  }

  @Test
  fun invalidInputsReturnZeroResult() {
    val result = UtilityCalculators.calculateTrueNoCostEmi(
      productPrice = Double.NaN,
      tenureMonths = 6
    )

    assertEquals(0.0, result.productPrice, 0.0)
    assertEquals(0.0, result.totalHiddenCost, 0.0)
    assertEquals(0.0, result.actualTotalOutflow, 0.0)
  }
}
