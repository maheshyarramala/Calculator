package com.example.domain.calculator

object CurrencyCalculator {

  data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String,
    val flag: String,
    val ratePerUsd: Double // How many of this currency = 1 USD
  )

  // Accurate global benchmark rates relative to 1 USD
  val defaultCurrencies = listOf(
    CurrencyInfo("USD", "US Dollar", "$", "🇺🇸", 1.0),
    CurrencyInfo("INR", "Indian Rupee", "₹", "🇮🇳", 86.85),
    CurrencyInfo("EUR", "Euro", "€", "🇪🇺", 0.952),
    CurrencyInfo("GBP", "British Pound", "£", "🇬🇧", 0.795),
    CurrencyInfo("AED", "UAE Dirham", "AED", "🇦🇪", 3.6725),
    CurrencyInfo("SAR", "Saudi Riyal", "﷼", "🇸🇦", 3.75),
    CurrencyInfo("JPY", "Japanese Yen", "¥", "🇯🇵", 152.80),
    CurrencyInfo("CAD", "Canadian Dollar", "C$", "🇨🇦", 1.405),
    CurrencyInfo("AUD", "Australian Dollar", "A$", "🇦🇺", 1.542),
    CurrencyInfo("CHF", "Swiss Franc", "CHF", "🇨🇭", 0.902),
    CurrencyInfo("CNY", "Chinese Yuan", "¥", "🇨🇳", 7.285),
    CurrencyInfo("SGD", "Singapore Dollar", "S$", "🇸🇬", 1.348),
    CurrencyInfo("KWD", "Kuwaiti Dinar", "KD", "🇰🇼", 0.3075),
    CurrencyInfo("QAR", "Qatari Riyal", "QR", "🇶🇦", 3.64),
    CurrencyInfo("MYR", "Malaysian Ringgit", "RM", "🇲🇾", 4.45),
    CurrencyInfo("NZD", "New Zealand Dollar", "NZ$", "🇳🇿", 1.72),
    CurrencyInfo("KRW", "South Korean Won", "₩", "🇰🇷", 1420.0),
    CurrencyInfo("BRL", "Brazilian Real", "R$", "🇧🇷", 5.75)
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
