package com.example.domain.calculator

import com.example.data.model.AmortizationMonth
import com.example.data.model.EmiResult
import com.example.data.model.PrepaymentEntry
import kotlin.math.pow
import kotlin.math.roundToInt

object EmiCalculator {

  fun calculate(
    principal: Double,
    annualRate: Double,
    tenureMonths: Int,
    prepayments: List<PrepaymentEntry> = emptyList()
  ): EmiResult {
    if (principal <= 0.0 || tenureMonths <= 0) {
      return EmiResult(
        principal = principal,
        annualRate = annualRate,
        tenureMonths = tenureMonths,
        monthlyEmi = 0.0,
        totalInterest = 0.0,
        totalPayment = 0.0,
        schedule = emptyList()
      )
    }

    val monthlyRate = (annualRate / 12.0) / 100.0

    // Standard EMI without prepayments
    val standardEmi = if (monthlyRate <= 0.0) {
      principal / tenureMonths
    } else {
      val factor = (1.0 + monthlyRate).pow(tenureMonths.toDouble())
      (principal * monthlyRate * factor) / (factor - 1.0)
    }

    // Baseline calculation without prepayment for interest saved comparison
    var baselineBalance = principal
    var originalTotalInterest = 0.0
    for (m in 1..tenureMonths) {
      val interest = baselineBalance * monthlyRate
      val principalComponent = (standardEmi - interest).coerceAtMost(baselineBalance)
      originalTotalInterest += interest
      baselineBalance -= principalComponent
      if (baselineBalance <= 0.001) break
    }

    // Actual Amortization schedule with prepayments
    val prepaymentMap = prepayments.groupBy { it.month }
      .mapValues { entry -> entry.value.sumOf { it.amount } }

    val schedule = mutableListOf<AmortizationMonth>()
    var balance = principal
    var totalInterestPaid = 0.0
    var totalPaid = 0.0

    var month = 1
    val maxMonthsSafety = tenureMonths * 2 // safety cap

    while (balance > 0.001 && month <= maxMonthsSafety) {
      val openingBalance = balance
      val interest = if (monthlyRate > 0.0) openingBalance * monthlyRate else 0.0
      val prepaymentAmount = prepaymentMap[month] ?: 0.0

      var emiForThisMonth = standardEmi
      var principalComponent: Double

      if (openingBalance + interest <= emiForThisMonth) {
        // Final month adjustment
        emiForThisMonth = openingBalance + interest
        principalComponent = openingBalance
        totalInterestPaid += interest
        val totalPaymentThisMonth = emiForThisMonth + prepaymentAmount
        totalPaid += totalPaymentThisMonth
        val closing = (openingBalance - principalComponent - prepaymentAmount).coerceAtLeast(0.0)

        schedule.add(
          AmortizationMonth(
            monthIndex = month,
            openingBalance = openingBalance,
            emiPaid = emiForThisMonth,
            principalPaid = principalComponent,
            interestPaid = interest,
            prepayment = prepaymentAmount,
            closingBalance = closing
          )
        )
        balance = closing
        break
      } else {
        principalComponent = emiForThisMonth - interest
        if (principalComponent > openingBalance) {
          principalComponent = openingBalance
          emiForThisMonth = principalComponent + interest
        }
        val closing = (openingBalance - principalComponent - prepaymentAmount).coerceAtLeast(0.0)
        totalInterestPaid += interest
        totalPaid += emiForThisMonth + prepaymentAmount

        schedule.add(
          AmortizationMonth(
            monthIndex = month,
            openingBalance = openingBalance,
            emiPaid = emiForThisMonth,
            principalPaid = principalComponent,
            interestPaid = interest,
            prepayment = prepaymentAmount,
            closingBalance = closing
          )
        )
        balance = closing
      }

      if (balance <= 0.001) break
      month++
    }

    val actualTenureMonths = schedule.size
    val interestSaved = (originalTotalInterest - totalInterestPaid).coerceAtLeast(0.0)
    val tenureMonthsSaved = (tenureMonths - actualTenureMonths).coerceAtLeast(0)

    return EmiResult(
      principal = principal,
      annualRate = annualRate,
      tenureMonths = actualTenureMonths,
      monthlyEmi = standardEmi,
      totalInterest = totalInterestPaid,
      totalPayment = totalPaid,
      schedule = schedule,
      originalTotalInterest = originalTotalInterest,
      originalTenureMonths = tenureMonths,
      interestSaved = interestSaved,
      tenureMonthsSaved = tenureMonthsSaved
    )
  }

  fun exportAmortizationAsText(result: EmiResult): String {
    val sb = StringBuilder()
    sb.appendLine("=============================================")
    sb.appendLine("           EMI AMORTIZATION SCHEDULE         ")
    sb.appendLine("=============================================")
    sb.appendLine("Principal Loan Amount : ${formatNumber(result.principal)}")
    sb.appendLine("Annual Interest Rate  : ${result.annualRate}%")
    sb.appendLine("Initial Tenure        : ${result.originalTenureMonths} months")
    sb.appendLine("Monthly EMI           : ${formatNumber(result.monthlyEmi)}")
    sb.appendLine("Total Interest        : ${formatNumber(result.totalInterest)}")
    sb.appendLine("Total Amount Payable  : ${formatNumber(result.totalPayment)}")

    if (result.interestSaved > 0.0 || result.tenureMonthsSaved > 0) {
      sb.appendLine("--- Prepayment Benefit ---")
      sb.appendLine("Tenure Reduced By     : ${result.tenureMonthsSaved} months")
      sb.appendLine("Interest Saved        : ${formatNumber(result.interestSaved)}")
    }

    sb.appendLine("\n---------------------------------------------------------------------------------------------")
    sb.appendLine(String.format("%-5s | %-14s | %-12s | %-12s | %-12s | %-12s | %-14s",
      "Month", "Opening", "EMI", "Principal", "Interest", "Prepay", "Closing"))
    sb.appendLine("---------------------------------------------------------------------------------------------")

    for (m in result.schedule) {
      sb.appendLine(
        String.format(
          "%-5d | %-14s | %-12s | %-12s | %-12s | %-12s | %-14s",
          m.monthIndex,
          formatNumber(m.openingBalance),
          formatNumber(m.emiPaid),
          formatNumber(m.principalPaid),
          formatNumber(m.interestPaid),
          if (m.prepayment > 0) formatNumber(m.prepayment) else "-",
          formatNumber(m.closingBalance)
        )
      )
    }
    sb.appendLine("=============================================")
    sb.appendLine("Generated via OmniCalc")
    return sb.toString()
  }

  private fun formatNumber(value: Double): String {
    return String.format(java.util.Locale.US, "%,.2f", value)
  }
}
