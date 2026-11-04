package com.example.domain.splitbill

import java.util.UUID
import java.util.regex.Pattern

object ReceiptOcrParser {

  /**
   * Parses raw receipt text into structured line items, tax, tip, discount, and grand total.
   */
  fun parseReceiptText(text: String, defaultTitle: String = "Dining Receipt"): ParsedReceipt {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

    val items = mutableListOf<ReceiptItem>()
    var detectedSubtotal = 0.0
    var detectedTax = 0.0
    var detectedTip = 0.0
    var detectedDiscount = 0.0
    var detectedTotal = 0.0

    // Price regex: matches amounts like 12.50, $12.50, ₹450, 450.00
    val pricePattern = Pattern.compile("""(?:[\$₹€£]|Rs\.?|INR)?\s*([0-9]+(?:[\.,][0-9]{2})?)""")

    lines.forEach { line ->
      val lower = line.lowercase()

      // Check for tax
      if (lower.contains("tax") || lower.contains("gst") || lower.contains("vat") || lower.contains("sales tax")) {
        extractFirstNumber(line)?.let { detectedTax = it }
        return@forEach
      }

      // Check for tip / service charge
      if (lower.contains("tip") || lower.contains("gratuity") || lower.contains("service charge")) {
        extractFirstNumber(line)?.let { detectedTip = it }
        return@forEach
      }

      // Check for discount
      if (lower.contains("discount") || lower.contains("coupon") || lower.contains("promo") || lower.contains("off")) {
        extractFirstNumber(line)?.let { detectedDiscount = it }
        return@forEach
      }

      // Check for total
      if (lower.startsWith("total") || lower.startsWith("grand total") || lower.startsWith("amount due") || lower.startsWith("final amount")) {
        extractFirstNumber(line)?.let { detectedTotal = it }
        return@forEach
      }

      // Check for subtotal
      if (lower.startsWith("subtotal") || lower.startsWith("sub total") || lower.startsWith("net total")) {
        extractFirstNumber(line)?.let { detectedSubtotal = it }
        return@forEach
      }

      // Skip non-item headers and dates
      if (lower.contains("date:") || lower.contains("table:") || lower.contains("server:") || lower.contains("cashier:") || lower.contains("thank you")) {
        return@forEach
      }

      // Attempt to extract item name and price
      val matcher = pricePattern.matcher(line)
      var lastFoundPrice: Double? = null
      var priceStart = -1

      while (matcher.find()) {
        val numStr = matcher.group(1)?.replace(",", ".")
        val parsed = numStr?.toDoubleOrNull()
        if (parsed != null && parsed > 0.0 && parsed < 1000000.0) {
          lastFoundPrice = parsed
          priceStart = matcher.start()
        }
      }

      if (lastFoundPrice != null && priceStart > 0) {
        val rawItemName = line.substring(0, priceStart).trim().trim('-', ':', '.')
        if (rawItemName.isNotBlank() && rawItemName.length > 1) {
          items.add(
            ReceiptItem(
              id = UUID.randomUUID().toString(),
              title = cleanItemName(rawItemName),
              price = lastFoundPrice,
              quantity = 1
            )
          )
        }
      }
    }

    val computedSubtotal = items.sumOf { it.price * it.quantity }
    val finalSubtotal = if (detectedSubtotal > 0.0) detectedSubtotal else computedSubtotal
    val finalTotal = if (detectedTotal > 0.0) detectedTotal else (finalSubtotal + detectedTax + detectedTip - detectedDiscount)

    return ParsedReceipt(
      title = defaultTitle,
      items = items,
      subtotal = finalSubtotal,
      tax = detectedTax,
      tip = detectedTip,
      discount = detectedDiscount,
      grandTotal = finalTotal
    )
  }

  private fun extractFirstNumber(text: String): Double? {
    val matcher = Pattern.compile("""(?:[\$₹€£]|Rs\.?|INR)?\s*([0-9]+(?:[\.,][0-9]{2})?)""").matcher(text)
    var result: Double? = null
    while (matcher.find()) {
      matcher.group(1)?.replace(",", ".")?.toDoubleOrNull()?.let {
        result = it
      }
    }
    return result
  }

  private fun cleanItemName(raw: String): String {
    // Remove leading quantity markers like "1x ", "2 ", etc.
    return raw.replace(Regex("""^\d+\s*x?\s*"""), "").trim()
  }

  // Pre-configured demo receipts for 1-tap testing
  val demoReceiptBistro = """
    Trattoria Bella Vista
    Table: 04  Guests: 4
    ----------------------------------------
    1x Bruschetta Pomodoro       280.00
    1x Crispy Calamari           450.00
    2x Truffle Pasta            1100.00
    1x Margherita Pizza          550.00
    2x Pinot Noir Red Wine       800.00
    2x Sparkling Water           160.00
    1x Tiramisu Tradizionale     320.00
    ----------------------------------------
    Subtotal:                   3660.00
    CGST & SGST (5%):            183.00
    Service Charge (10%):        366.00
    Discount Coupon:             200.00
    Grand Total:                4009.00
  """.trimIndent()

  val demoReceiptCafe = """
    Artisan Roasters Cafe
    Order #1029
    ----------------------------------------
    2x Flat White Coffee         440.00
    1x Almond Croissant          180.00
    1x Avocado Sourdough Toast   390.00
    1x Blueberry Acai Bowl       360.00
    1x Belgian Waffle            320.00
    ----------------------------------------
    Subtotal:                   1690.00
    GST (5%):                     84.50
    Tip (Gracious):              100.00
    Total Due:                  1874.50
  """.trimIndent()

  val demoReceiptSushi = """
    Sakura Japanese Kitchen
    ----------------------------------------
    1x Edamame Sea Salt          190.00
    1x Salmon Nigiri Platter     850.00
    1x Dragon Roll (8 pcs)       680.00
    1x Chicken Katsu Curry       480.00
    2x Asahi Japanese Beer       600.00
    1x Green Tea Mochi           220.00
    ----------------------------------------
    Subtotal:                   3020.00
    Tax:                         151.00
    Service Charge:              250.00
    Final Amount:               3421.00
  """.trimIndent()
}
