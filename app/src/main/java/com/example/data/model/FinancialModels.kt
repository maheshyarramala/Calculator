package com.example.data.model

data class AmortizationMonth(
  val monthIndex: Int,
  val openingBalance: Double,
  val emiPaid: Double,
  val principalPaid: Double,
  val interestPaid: Double,
  val prepayment: Double = 0.0,
  val closingBalance: Double
)

data class PrepaymentEntry(
  val id: String = java.util.UUID.randomUUID().toString(),
  val month: Int,
  val amount: Double
)

data class EmiResult(
  val principal: Double,
  val annualRate: Double,
  val tenureMonths: Int,
  val monthlyEmi: Double,
  val totalInterest: Double,
  val totalPayment: Double,
  val schedule: List<AmortizationMonth>,
  val originalTotalInterest: Double = totalInterest,
  val originalTenureMonths: Int = tenureMonths,
  val interestSaved: Double = 0.0,
  val tenureMonthsSaved: Int = 0
)

data class YearlyBreakdown(
  val year: Int,
  val invested: Double,
  val returns: Double,
  val balance: Double
)

data class InvestmentResult(
  val title: String,
  val investedAmount: Double,
  val estimatedReturns: Double,
  val totalValue: Double,
  val yearlyBreakdown: List<YearlyBreakdown>
)
