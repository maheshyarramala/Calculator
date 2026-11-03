package com.example

import com.example.data.model.PrepaymentEntry
import com.example.domain.calculator.*
import com.example.data.local.entity.SubscriptionRecord
import com.example.ui.components.CurrencyFormatter
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

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

  @Test
  fun testInterestCalculatorMethods() {
    // 1. Simple Interest: P = 1,00,000, R = 10%, T = 3 years
    val si = InterestCalculator.calculateSimpleInterest(
      100000.0,
      10.0,
      3.0,
      InterestCalculator.TimeUnit.YEARS
    )
    assertEquals(30000.0, si.interestEarned, 0.01)
    assertEquals(130000.0, si.totalAmount, 0.01)

    // 2. Compound Interest: P = 1,00,000, R = 10%, T = 3 years, Annually
    val ci = InterestCalculator.calculateCompoundInterest(
      100000.0,
      10.0,
      3.0,
      InterestCalculator.CompoundingFrequency.ANNUALLY
    )
    assertEquals(133100.0, ci.totalAmount, 0.01)
    assertEquals(33100.0, ci.totalInterest, 0.01)

    // 3. Rule of 72 / Doubling
    val rule72 = InterestCalculator.calculateDoubling(8.0)
    assertEquals(9.0, rule72.ruleOf72Years, 0.01)
    assertTrue("Exact doubling years is close to 9", rule72.exactDoublingYears in 8.9..9.1)

    // 4. Effective Annual Rate (EAR / APY)
    val apy = InterestCalculator.calculateEffectiveRate(
      12.0,
      InterestCalculator.CompoundingFrequency.MONTHLY
    )
    assertTrue("Monthly 12% nominal gives APY ~12.68%", apy.effectiveRatePercent in 12.67..12.70)

    // 5. Flat vs Reducing Comparison
    val flatVsRed = InterestCalculator.compareFlatVsReducing(100000.0, 36, 10.0)
    assertTrue("Flat interest > Reducing interest", flatVsRed.flatTotalInterest > flatVsRed.reducingTotalInterest)
    assertTrue("Flat equivalent rate is higher", flatVsRed.equivalentReducingRate > 10.0)
  }

  @Test
  fun testSplitBillNoTip() {
    val split = UtilityCalculators.calculateSplit(1500.0, 3)
    assertEquals(1500.0, split.billAmount, 0.01)
    assertEquals(500.0, split.perPersonShare, 0.01)
    assertEquals(3, split.numPersons)
  }

  @Test
  fun subscriptionDueDatesRespectMonthLengthAndCycle() {
    val februaryDue = SubscriptionDueDateCalculator.nextDueDate(
      SubscriptionRecord(name = "Rent", amount = 1000.0, dueDayOfMonth = 31),
      LocalDate.of(2025, 2, 20)
    )
    assertEquals(LocalDate.of(2025, 2, 28), februaryDue)

    val leapYearDue = SubscriptionDueDateCalculator.nextDueDate(
      SubscriptionRecord(name = "Rent", amount = 1000.0, dueDayOfMonth = 31),
      LocalDate.of(2024, 2, 20)
    )
    assertEquals(LocalDate.of(2024, 2, 29), leapYearDue)

    val weeklyDue = SubscriptionDueDateCalculator.nextDueDate(
      SubscriptionRecord(
        name = "Service",
        amount = 100.0,
        billingCycle = "WEEKLY",
        dueDayOfWeek = 3
      ),
      LocalDate.of(2025, 1, 10)
    )
    assertEquals(LocalDate.of(2025, 1, 15), weeklyDue)

    val weeklyPaidDue = SubscriptionDueDateCalculator.nextDueDate(
      SubscriptionRecord(
        name = "Service",
        amount = 100.0,
        billingCycle = "WEEKLY",
        dueDayOfWeek = 1,
        lastPaidTimestamp = LocalDate.of(2025, 1, 6)
          .atStartOfDay(ZoneId.systemDefault())
          .toInstant()
          .toEpochMilli()
      ),
      LocalDate.of(2025, 1, 6)
    )
    assertEquals(LocalDate.of(2025, 1, 13), weeklyPaidDue)

    val yearlyDue = SubscriptionDueDateCalculator.nextDueDate(
      SubscriptionRecord(
        name = "Membership",
        amount = 100.0,
        billingCycle = "YEARLY",
        dueDayOfMonth = 20,
        dueMonth = 2
      ),
      LocalDate.of(2025, 2, 1)
    )
    assertEquals(LocalDate.of(2025, 2, 20), yearlyDue)

    val nextYearlyDue = SubscriptionDueDateCalculator.nextDueDate(
      SubscriptionRecord(
        name = "Membership",
        amount = 100.0,
        billingCycle = "YEARLY",
        dueDayOfMonth = 20,
        dueMonth = 2
      ),
      LocalDate.of(2025, 3, 1)
    )
    assertEquals(LocalDate.of(2026, 2, 20), nextYearlyDue)
  }

  @Test
  fun currencyFormattingUsesCurrentLocaleGrouping() {
    val originalLocale = Locale.getDefault()
    val originalPrivacyMode = CurrencyFormatter.isPrivacyModeEnabled
    try {
      Locale.setDefault(Locale("en", "IN"))
      CurrencyFormatter.isPrivacyModeEnabled = false
      assertEquals("₹ 1,23,45,678", CurrencyFormatter.format(12345678.0))
    } finally {
      Locale.setDefault(originalLocale)
      CurrencyFormatter.isPrivacyModeEnabled = originalPrivacyMode
    }
  }

  @Test
  fun subscriptionPaymentTimestampDeterminesPaidCycle() {
    val paidDate = LocalDate.of(2025, 2, 10)
      .atStartOfDay(ZoneId.systemDefault())
      .toInstant()
      .toEpochMilli()
    val monthlySub = SubscriptionRecord(
      name = "Internet",
      amount = 100.0,
      billingCycle = "MONTHLY",
      lastPaidTimestamp = paidDate
    )

    assertTrue(SubscriptionDueDateCalculator.isPaidInCurrentCycle(monthlySub, LocalDate.of(2025, 2, 28)))
    assertFalse(SubscriptionDueDateCalculator.isPaidInCurrentCycle(monthlySub, LocalDate.of(2025, 3, 1)))
  }
}
