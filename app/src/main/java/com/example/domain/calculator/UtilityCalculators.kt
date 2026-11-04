package com.example.domain.calculator

import kotlin.math.pow

object UtilityCalculators {

  data class NoCostEmiAnalysis(
    val productPrice: Double,
    val upfrontMerchantDiscount: Double,
    val effectiveLoanPrincipal: Double,
    val monthlyBaseEmi: Double,
    val totalInterestComponent: Double,
    val gstOnInterest18Percent: Double,
    val processingFeeWithGst: Double,
    val totalHiddenCost: Double,
    val actualTotalOutflow: Double,
    val effectiveAnnualCostPercent: Double
  )

  fun calculateTrueNoCostEmi(
    productPrice: Double,
    annualBankRatePercent: Double = 16.0,
    tenureMonths: Int = 6,
    processingFee: Double = 199.0,
    gstRatePercent: Double = 18.0
  ): NoCostEmiAnalysis {
    val zeroResult = NoCostEmiAnalysis(
      productPrice = 0.0,
      upfrontMerchantDiscount = 0.0,
      effectiveLoanPrincipal = 0.0,
      monthlyBaseEmi = 0.0,
      totalInterestComponent = 0.0,
      gstOnInterest18Percent = 0.0,
      processingFeeWithGst = 0.0,
      totalHiddenCost = 0.0,
      actualTotalOutflow = 0.0,
      effectiveAnnualCostPercent = 0.0
    )
    if (
      !productPrice.isFinite() || productPrice <= 0.0 ||
      !annualBankRatePercent.isFinite() || annualBankRatePercent < 0.0 ||
      tenureMonths <= 0 ||
      !processingFee.isFinite() || processingFee < 0.0 ||
      !gstRatePercent.isFinite() || gstRatePercent < 0.0
    ) {
      return zeroResult
    }

    val monthlyRate = annualBankRatePercent / 1200.0
    val monthlyBaseEmi = productPrice / tenureMonths
    val effectivePrincipal = if (monthlyRate == 0.0) {
      productPrice
    } else {
      val factor = (1.0 + monthlyRate).pow(tenureMonths.toDouble())
      monthlyBaseEmi * (factor - 1.0) / (monthlyRate * factor)
    }
    if (!effectivePrincipal.isFinite()) return zeroResult

    val upfrontDiscount = (productPrice - effectivePrincipal).coerceAtLeast(0.0)
    val gstMultiplier = gstRatePercent / 100.0
    val gstOnInterest = upfrontDiscount * gstMultiplier
    val processingFeeWithGst = processingFee * (1.0 + gstMultiplier)
    val hiddenCost = gstOnInterest + processingFeeWithGst
    val actualTotalOutflow = productPrice + hiddenCost
    val annualizedCostPercent = (hiddenCost / productPrice) * (12.0 / tenureMonths) * 100.0
    if (
      !hiddenCost.isFinite() ||
      !actualTotalOutflow.isFinite() ||
      !annualizedCostPercent.isFinite()
    ) {
      return zeroResult
    }

    return NoCostEmiAnalysis(
      productPrice = productPrice,
      upfrontMerchantDiscount = upfrontDiscount,
      effectiveLoanPrincipal = effectivePrincipal,
      monthlyBaseEmi = monthlyBaseEmi,
      totalInterestComponent = upfrontDiscount,
      gstOnInterest18Percent = gstOnInterest,
      processingFeeWithGst = processingFeeWithGst,
      totalHiddenCost = hiddenCost,
      actualTotalOutflow = actualTotalOutflow,
      effectiveAnnualCostPercent = annualizedCostPercent
    )
  }

  data class BillSplitResult(
    val billAmount: Double,
    val numPersons: Int,
    val perPersonShare: Double
  )

  fun calculateSplit(
    billAmount: Double,
    numPersons: Int
  ): BillSplitResult {
    val persons = numPersons.coerceAtLeast(1)
    if (billAmount <= 0.0) {
      return BillSplitResult(0.0, persons, 0.0)
    }
    val perPerson = billAmount / persons
    return BillSplitResult(
      billAmount = billAmount,
      numPersons = persons,
      perPersonShare = perPerson
    )
  }

  // Deprecated compatibility wrapper for existing tests
  data class TipResult(
    val billAmount: Double,
    val tipPercent: Double,
    val numPersons: Int,
    val tipAmount: Double,
    val totalAmount: Double,
    val perPersonTip: Double,
    val perPersonTotal: Double
  )

  fun calculateTip(
    billAmount: Double,
    tipPercent: Double = 0.0,
    numPersons: Int = 1
  ): TipResult {
    val persons = numPersons.coerceAtLeast(1)
    if (billAmount <= 0.0) {
      return TipResult(0.0, tipPercent, persons, 0.0, 0.0, 0.0, 0.0)
    }
    val tipAmount = billAmount * (tipPercent / 100.0)
    val totalAmount = billAmount + tipAmount
    val perPersonTip = tipAmount / persons
    val perPersonTotal = totalAmount / persons
    return TipResult(billAmount, tipPercent, persons, tipAmount, totalAmount, perPersonTip, perPersonTotal)
  }

  data class GstResult(
    val initialAmount: Double,
    val ratePercent: Double,
    val isExclusive: Boolean,
    val baseAmount: Double,
    val gstAmount: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val finalTotalAmount: Double
  )

  fun calculateGst(
    amount: Double,
    ratePercent: Double,
    isExclusive: Boolean
  ): GstResult {
    if (amount <= 0.0) {
      return GstResult(0.0, ratePercent, isExclusive, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    if (isExclusive) {
      // Add GST: Original amount is Net Base
      val gst = amount * (ratePercent / 100.0)
      val total = amount + gst
      return GstResult(
        initialAmount = amount,
        ratePercent = ratePercent,
        isExclusive = true,
        baseAmount = amount,
        gstAmount = gst,
        cgstAmount = gst / 2.0,
        sgstAmount = gst / 2.0,
        finalTotalAmount = total
      )
    } else {
      // Remove GST: Original amount is Gross (Includes GST)
      val base = amount / (1.0 + (ratePercent / 100.0))
      val gst = amount - base
      return GstResult(
        initialAmount = amount,
        ratePercent = ratePercent,
        isExclusive = false,
        baseAmount = base,
        gstAmount = gst,
        cgstAmount = gst / 2.0,
        sgstAmount = gst / 2.0,
        finalTotalAmount = amount
      )
    }
  }
}
