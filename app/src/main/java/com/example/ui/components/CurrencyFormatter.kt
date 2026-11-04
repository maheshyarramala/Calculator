package com.example.ui.components

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

object CurrencyFormatter {

  var isPrivacyModeEnabled: Boolean = false

  /**
   * Formats an amount using Indian Numbering System (e.g. ₹ 55,000, ₹ 1,00,000, ₹ 10,00,000).
   * Automatically eliminates floating-point noise (.99, .01) by rounding near-integers.
   */
  fun format(amount: Double, prefix: String = "₹"): String {
    if (isPrivacyModeEnabled) return "••••••"
    if (amount.isNaN() || amount.isInfinite()) return if (prefix.isEmpty()) "0" else "$prefix 0"

    val formattedNumber = formatIndianNumber(amount, allowDecimals = true)
    return formatWithPrefix(formattedNumber, amount < 0, prefix)
  }

  /**
   * Formats an amount strictly as a whole integer in Indian numbering format (e.g. ₹ 55,000).
   */
  fun formatWhole(amount: Double, prefix: String = "₹"): String {
    if (isPrivacyModeEnabled) return "••••••"
    if (amount.isNaN() || amount.isInfinite()) return if (prefix.isEmpty()) "0" else "$prefix 0"

    val formattedNumber = formatIndianNumber(amount, allowDecimals = false)
    return formatWithPrefix(formattedNumber, amount < 0, prefix)
  }

  /**
   * Formats an amount without currency prefix (e.g. 55,000).
   */
  fun formatPlain(amount: Double): String {
    if (isPrivacyModeEnabled) return "••••••"
    if (amount.isNaN() || amount.isInfinite()) return "0"
    return formatIndianNumber(amount, allowDecimals = true)
  }

  fun formatCompact(amount: Double, prefix: String = "₹"): String {
    return format(amount, prefix)
  }

  private fun formatWithPrefix(formattedNumber: String, isNegative: Boolean, prefix: String): String {
    if (prefix.isEmpty()) return formattedNumber
    return if (isNegative) {
      val absStr = formattedNumber.removePrefix("-")
      "-$prefix $absStr"
    } else {
      "$prefix $formattedNumber"
    }
  }

  /**
   * Pure Indian Numbering System formatter:
   * Last 3 digits grouped, then groups of 2 (e.g. 12,34,567).
   * Automatically detects and eliminates floating-point noise like .9999 or .0001.
   */
  private fun formatIndianNumber(amount: Double, allowDecimals: Boolean): String {
    val isNeg = amount < 0
    val absVal = abs(amount)

    // Detect and snap near-whole numbers to eliminate floating point math noise (e.g. 54999.99999 -> 55000)
    val nearestWhole = absVal.roundToLong()
    val isNearWhole = abs(absVal - nearestWhole) < 0.005

    val wholePart: Long
    val fractionPart: Int

    if (isNearWhole || !allowDecimals) {
      wholePart = nearestWhole
      fractionPart = 0
    } else {
      wholePart = floor(absVal).toLong()
      val fraction = absVal - wholePart
      fractionPart = (fraction * 100.0 + 0.5).toInt()
    }

    val wholeStr = wholePart.toString()
    val len = wholeStr.length

    val sb = StringBuilder()
    if (len <= 3) {
      sb.append(wholeStr)
    } else {
      val lastThree = wholeStr.substring(len - 3)
      val leading = wholeStr.substring(0, len - 3)

      val leadingBuilder = StringBuilder()
      val leadLen = leading.length
      for (i in 0 until leadLen) {
        if (i > 0 && (leadLen - i) % 2 == 0) {
          leadingBuilder.append(',')
        }
        leadingBuilder.append(leading[i])
      }
      leadingBuilder.append(',').append(lastThree)
      sb.append(leadingBuilder)
    }

    if (isNeg && (wholePart > 0 || fractionPart > 0)) {
      sb.insert(0, '-')
    }

    if (allowDecimals && fractionPart > 0) {
      sb.append('.').append(String.format(Locale.US, "%02d", fractionPart))
    }

    return sb.toString()
  }
}
