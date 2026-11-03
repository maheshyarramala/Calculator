package com.example.domain.calculator

object UtilityCalculators {

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
    tipPercent: Double,
    numPersons: Int
  ): TipResult {
    val persons = numPersons.coerceAtLeast(1)
    if (billAmount <= 0.0) {
      return TipResult(0.0, tipPercent, persons, 0.0, 0.0, 0.0, 0.0)
    }

    val tipAmount = billAmount * (tipPercent / 100.0)
    val totalAmount = billAmount + tipAmount
    val perPersonTip = tipAmount / persons
    val perPersonTotal = totalAmount / persons

    return TipResult(
      billAmount = billAmount,
      tipPercent = tipPercent,
      numPersons = persons,
      tipAmount = tipAmount,
      totalAmount = totalAmount,
      perPersonTip = perPersonTip,
      perPersonTotal = perPersonTotal
    )
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
