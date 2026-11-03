package com.example.domain.calculator

object UnitConverter {

  enum class Category(val title: String) {
    LENGTH("Length"),
    WEIGHT("Weight & Mass"),
    TEMPERATURE("Temperature"),
    CURRENCY("Currency")
  }

  data class UnitItem(val id: String, val name: String, val symbol: String, val factorToBase: Double = 1.0)

  val lengthUnits = listOf(
    UnitItem("m", "Meter", "m", 1.0),
    UnitItem("km", "Kilometer", "km", 1000.0),
    UnitItem("cm", "Centimeter", "cm", 0.01),
    UnitItem("mm", "Millimeter", "mm", 0.001),
    UnitItem("mi", "Mile", "mi", 1609.344),
    UnitItem("yd", "Yard", "yd", 0.9144),
    UnitItem("ft", "Foot", "ft", 0.3048),
    UnitItem("in", "Inch", "in", 0.0254)
  )

  val weightUnits = listOf(
    UnitItem("kg", "Kilogram", "kg", 1.0),
    UnitItem("g", "Gram", "g", 0.001),
    UnitItem("mg", "Milligram", "mg", 0.000001),
    UnitItem("t", "Metric Ton", "t", 1000.0),
    UnitItem("lb", "Pound", "lb", 0.45359237),
    UnitItem("oz", "Ounce", "oz", 0.028349523)
  )

  val temperatureUnits = listOf(
    UnitItem("C", "Celsius", "°C"),
    UnitItem("F", "Fahrenheit", "°F"),
    UnitItem("K", "Kelvin", "K")
  )

  // Relative to USD base = 1.0
  val currencyUnits = listOf(
    UnitItem("USD", "US Dollar", "$", 1.0),
    UnitItem("EUR", "Euro", "€", 0.92),
    UnitItem("GBP", "British Pound", "£", 0.79),
    UnitItem("INR", "Indian Rupee", "₹", 86.40),
    UnitItem("JPY", "Japanese Yen", "¥", 154.20),
    UnitItem("CAD", "Canadian Dollar", "C$", 1.38),
    UnitItem("AUD", "Australian Dollar", "A$", 1.52),
    UnitItem("CHF", "Swiss Franc", "CHF", 0.88),
    UnitItem("CNY", "Chinese Yuan", "¥", 7.24),
    UnitItem("AED", "UAE Dirham", "AED", 3.67)
  )

  fun convert(
    category: Category,
    value: Double,
    fromUnit: UnitItem,
    toUnit: UnitItem
  ): Double {
    if (category == Category.TEMPERATURE) {
      // Temperature special case
      val celsius = when (fromUnit.id) {
        "C" -> value
        "F" -> (value - 32.0) * 5.0 / 9.0
        "K" -> value - 273.15
        else -> value
      }
      return when (toUnit.id) {
        "C" -> celsius
        "F" -> (celsius * 9.0 / 5.0) + 32.0
        "K" -> celsius + 273.15
        else -> celsius
      }
    }

    if (category == Category.CURRENCY) {
      // currency: factorToBase is how many unit in 1 USD
      // e.g. from USD to INR: value * 86.40
      // from EUR to INR: (value / 0.92) * 86.40
      val inUsd = value / fromUnit.factorToBase
      return inUsd * toUnit.factorToBase
    }

    // Standard linear conversion (factorToBase is value in 1 base unit, e.g. 1 km = 1000m)
    val baseValue = value * fromUnit.factorToBase
    return baseValue / toUnit.factorToBase
  }
}
