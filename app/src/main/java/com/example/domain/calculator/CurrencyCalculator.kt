package com.example.domain.calculator

object CurrencyCalculator {

  data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String,
    val flag: String,
    val ratePerUsd: Double // How many of this currency = 1 USD
  )

  val defaultCurrencies = listOf(
    CurrencyInfo("USD", "US Dollar", "$", "🇺🇸", 1.0),
    CurrencyInfo("INR", "Indian Rupee", "₹", "🇮🇳", 86.40),
    CurrencyInfo("EUR", "Euro", "€", "🇪🇺", 0.92),
    CurrencyInfo("GBP", "British Pound", "£", "🇬🇧", 0.79),
    CurrencyInfo("AED", "UAE Dirham", "AED", "🇦🇪", 3.67),
    CurrencyInfo("JPY", "Japanese Yen", "¥", "🇯🇵", 154.20),
    CurrencyInfo("CAD", "Canadian Dollar", "C$", "🇨🇦", 1.38),
    CurrencyInfo("AUD", "Australian Dollar", "A$", "🇦🇺", 1.52),
    CurrencyInfo("CHF", "Swiss Franc", "CHF", "🇨🇭", 0.88),
    CurrencyInfo("CNY", "Chinese Yuan", "¥", "🇨🇳", 7.24),
    CurrencyInfo("SGD", "Singapore Dollar", "S$", "🇸🇬", 1.34),
    CurrencyInfo("SAR", "Saudi Riyal", "﷼", "🇸🇦", 3.75),
    CurrencyInfo("KWD", "Kuwaiti Dinar", "KD", "🇰🇼", 0.31),
    CurrencyInfo("QAR", "Qatari Riyal", "QR", "🇶🇦", 3.64)
  )

  data class ConvertedCurrency(
    val info: CurrencyInfo,
    val convertedAmount: Double,
    val exchangeRateFromBase: Double
  )

  fun convertSingle(
    amount: Double,
    fromCode: String,
    toCode: String,
    rates: Map<String, Double>
  ): Double {
    val fromRate = rates[fromCode] ?: 1.0
    val toRate = rates[toCode] ?: 1.0
    val amountInUsd = amount / fromRate
    return amountInUsd * toRate
  }

  fun convertToAll(
    amount: Double,
    baseCode: String,
    currencies: List<CurrencyInfo>,
    rates: Map<String, Double>
  ): List<ConvertedCurrency> {
    val baseRate = rates[baseCode] ?: 1.0
    val amountInUsd = amount / baseRate

    return currencies.map { curr ->
      val targetRate = rates[curr.code] ?: curr.ratePerUsd
      val converted = amountInUsd * targetRate
      val rateFromBase = targetRate / baseRate
      ConvertedCurrency(
        info = curr,
        convertedAmount = converted,
        exchangeRateFromBase = rateFromBase
      )
    }
  }
}
