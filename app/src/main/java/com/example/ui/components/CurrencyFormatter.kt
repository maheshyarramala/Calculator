package com.example.ui.components

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {

  private val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 2
  }

  private val wholeNumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 0
  }

  fun format(amount: Double, prefix: String = "₹"): String {
    if (amount.isNaN() || amount.isInfinite()) return if (prefix.isEmpty()) "0" else "$prefix 0"
    val formatted = numberFormat.format(amount)
    return if (prefix.isEmpty()) formatted else "$prefix $formatted"
  }

  fun formatWhole(amount: Double, prefix: String = "₹"): String {
    if (amount.isNaN() || amount.isInfinite()) return if (prefix.isEmpty()) "0" else "$prefix 0"
    val formatted = wholeNumberFormat.format(amount)
    return if (prefix.isEmpty()) formatted else "$prefix $formatted"
  }

  fun formatPlain(amount: Double): String {
    if (amount.isNaN() || amount.isInfinite()) return "0"
    return numberFormat.format(amount)
  }

  // Instead of 10K / 5K / 1L abbreviations, return full formatted numeric values as requested by user
  fun formatCompact(amount: Double, prefix: String = "₹"): String {
    return format(amount, prefix)
  }
}
