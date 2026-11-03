package com.example.ui.components

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {

  var isPrivacyModeEnabled: Boolean = false

  fun format(amount: Double, prefix: String = "₹"): String {
    if (isPrivacyModeEnabled) return "••••••"
    if (amount.isNaN() || amount.isInfinite()) return if (prefix.isEmpty()) "0" else "$prefix 0"
    val formatted = numberFormat(maximumFractionDigits = 2).format(amount)
    return if (prefix.isEmpty()) formatted else "$prefix $formatted"
  }

  fun formatWhole(amount: Double, prefix: String = "₹"): String {
    if (isPrivacyModeEnabled) return "••••••"
    if (amount.isNaN() || amount.isInfinite()) return if (prefix.isEmpty()) "0" else "$prefix 0"
    val formatted = numberFormat(maximumFractionDigits = 0).format(amount)
    return if (prefix.isEmpty()) formatted else "$prefix $formatted"
  }

  fun formatPlain(amount: Double): String {
    if (isPrivacyModeEnabled) return "••••••"
    if (amount.isNaN() || amount.isInfinite()) return "0"
    return numberFormat(maximumFractionDigits = 2).format(amount)
  }

  // Instead of 10K / 5K / 1L abbreviations, return full formatted numeric values as requested by user
  fun formatCompact(amount: Double, prefix: String = "₹"): String {
    return format(amount, prefix)
  }

  private fun numberFormat(maximumFractionDigits: Int): NumberFormat =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
      minimumFractionDigits = 0
      this.maximumFractionDigits = maximumFractionDigits
    }
}
