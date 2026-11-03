package com.example

import com.example.data.model.PrepaymentEntry
import com.example.domain.calculator.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ExampleUnitTest {

  @Test
  fun testEmiCalculation() {
    // Principal: 10,00,000, 8.5% p.a., 10 years (120 months)
    val result = EmiCalculator.calculate(1000000.0, 8.5, 120)
    assertTrue("EMI should be around 12,398", result.monthlyEmi in 12390.0..12410.0)
    assertTrue("Total payment > principal", result.totalPayment > 1000000.0)
    assertEquals(120, result.schedule.size)
    assertEquals(0.0, result.schedule.last().closingBalance, 0.01)
  }

  @Test
  fun testEmiPrepaymentSavings() {
    // Prepayment in month 12 of 100,000
    val prepayments = listOf(PrepaymentEntry(month = 12, amount = 100000.0))
    val result = EmiCalculator.calculate(1000000.0, 8.5, 120, prepayments)
    assertTrue("Tenure should be reduced", result.tenureMonthsSaved > 0)
    assertTrue("Interest saved should be positive", result.interestSaved > 0.0)
    assertTrue("Schedule size should be less than 120", result.schedule.size < 120)
  }

  @Test
  fun testSipCalculation() {
    // 10,000/mo, 12% p.a., 10 years
    val res = InvestmentCalculators.calculateSip(10000.0, 12.0, 10)
    assertEquals(1200000.0, res.investedAmount, 0.01)
    assertTrue("Returns should be substantial", res.estimatedReturns > 1000000.0)
    assertEquals(10, res.yearlyBreakdown.size)
  }

  @Test
  fun testScientificEvaluator() {
    val r1 = ScientificEvaluator.evaluate("2 + 3 * 4")
    assertTrue(r1 is ScientificEvaluator.EvalResult.Success)
    assertEquals("14", (r1 as ScientificEvaluator.EvalResult.Success).formatted)

    val r2 = ScientificEvaluator.evaluate("sqrt(16) + 2^3")
    assertTrue(r2 is ScientificEvaluator.EvalResult.Success)
    assertEquals("12", (r2 as ScientificEvaluator.EvalResult.Success).formatted)

    val r3 = ScientificEvaluator.evaluate("sin(90)")
    assertTrue(r3 is ScientificEvaluator.EvalResult.Success)
    assertEquals("1", (r3 as ScientificEvaluator.EvalResult.Success).formatted)
  }

  @Test
  fun testUnitConverter() {
    val km = UnitConverter.lengthUnits.first { it.id == "km" }
    val m = UnitConverter.lengthUnits.first { it.id == "m" }
    val converted = UnitConverter.convert(UnitConverter.Category.LENGTH, 2.5, km, m)
    assertEquals(2500.0, converted, 0.001)

    val c = UnitConverter.temperatureUnits.first { it.id == "C" }
    val f = UnitConverter.temperatureUnits.first { it.id == "F" }
    val tempF = UnitConverter.convert(UnitConverter.Category.TEMPERATURE, 100.0, c, f)
    assertEquals(212.0, tempF, 0.001)
  }

  @Test
  fun testTipAndGstCalculators() {
    val tip = UtilityCalculators.calculateTip(1000.0, 10.0, 2)
    assertEquals(100.0, tip.tipAmount, 0.01)
    assertEquals(1100.0, tip.totalAmount, 0.01)
    assertEquals(550.0, tip.perPersonTotal, 0.01)

    val gstExclusive = UtilityCalculators.calculateGst(1000.0, 18.0, true)
    assertEquals(180.0, gstExclusive.gstAmount, 0.01)
    assertEquals(1180.0, gstExclusive.finalTotalAmount, 0.01)
  }

  @Test
  fun testAgeCalculator() {
    val birth = LocalDate.of(2000, 1, 1)
    val target = LocalDate.of(2025, 1, 1)
    val age = AgeCalculator.calculateAge(birth, target)
    assertEquals(25, age.years)
    assertEquals(0, age.months)
    assertEquals(0, age.days)
    assertTrue("Total days > 9000", age.totalDays > 9000)
    assertEquals(5, age.upcomingBirthdays.size)
  }

  @Test
  fun testCalendarCalculator() {
    val start = LocalDate.of(2025, 1, 1)
    val end = LocalDate.of(2025, 1, 11)
    val diff = CalendarCalculator.calculateDifference(start, end, includeEndDay = false)
    assertEquals(10L, diff.totalDays)
    assertEquals(1L, diff.weeks)
    assertEquals(3L, diff.remainingDays)

    val adj = CalendarCalculator.adjustDate(start, isAddition = true, days = 10)
    assertEquals(end, adj.targetDate)
  }

  @Test
  fun testCurrencyCalculator() {
    val rates = mapOf("USD" to 1.0, "INR" to 86.40, "EUR" to 0.92)
    val inr = CurrencyCalculator.convertSingle(100.0, "USD", "INR", rates)
    assertEquals(8640.0, inr, 0.01)

    val eur = CurrencyCalculator.convertSingle(100.0, "USD", "EUR", rates)
    assertEquals(92.0, eur, 0.01)
  }
}
