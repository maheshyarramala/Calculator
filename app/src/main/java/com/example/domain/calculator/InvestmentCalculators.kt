package com.example.domain.calculator

import com.example.data.model.InvestmentResult
import com.example.data.model.YearlyBreakdown
import kotlin.math.pow

object InvestmentCalculators {

  data class StockAverageResult(
    val totalQuantity: Int,
    val totalInvested: Double,
    val averagePrice: Double,
    val targetSellPrice: Double = 0.0,
    val netProfitAtTarget: Double = 0.0
  )

  data class TradeChargeBreakdown(
    val grossPnl: Double,
    val brokerage: Double,
    val stt: Double,
    val exchangeTxnCharge: Double,
    val sebiCharge: Double,
    val stampDuty: Double,
    val gst: Double,
    val dpCharge: Double,
    val totalCharges: Double,
    val netPnl: Double,
    val breakevenPerShare: Double
  )

  enum class CompoundingFrequency(val title: String, val timesPerYear: Int) {
    ANNUALLY("Annually", 1),
    SEMI_ANNUALLY("Semi-Annually", 2),
    QUARTERLY("Quarterly", 4),
    MONTHLY("Monthly", 12)
  }

  fun calculateStockAverage(
    qty1: Int,
    price1: Double,
    qty2: Int,
    price2: Double,
    currentMarketPrice: Double = 0.0
  ): StockAverageResult {
    val firstQuantity = qty1.coerceAtLeast(0)
    val secondQuantity = qty2.coerceAtLeast(0)
    val totalQuantityLong = firstQuantity.toLong() + secondQuantity.toLong()
    if (
      totalQuantityLong == 0L ||
      totalQuantityLong > Int.MAX_VALUE ||
      (firstQuantity > 0 && (!price1.isFinite() || price1 <= 0.0)) ||
      (secondQuantity > 0 && (!price2.isFinite() || price2 <= 0.0))
    ) {
      return StockAverageResult(0, 0.0, 0.0)
    }

    val totalQuantity = totalQuantityLong.toInt()
    val totalInvested = firstQuantity * price1 + secondQuantity * price2
    if (!totalInvested.isFinite()) return StockAverageResult(0, 0.0, 0.0)

    val averagePrice = totalInvested / totalQuantity
    val targetPrice = currentMarketPrice.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val profitAtTarget = if (targetPrice > 0.0) {
      targetPrice * totalQuantity - totalInvested
    } else {
      0.0
    }

    return StockAverageResult(
      totalQuantity = totalQuantity,
      totalInvested = totalInvested,
      averagePrice = averagePrice,
      targetSellPrice = targetPrice,
      netProfitAtTarget = profitAtTarget
    )
  }

  /**
   * Estimates Indian equity charges using common discount-broker rates.
   * Actual charges vary by exchange, broker, and current regulations.
   */
  fun calculateEquityTradePnl(
    buyPrice: Double,
    sellPrice: Double,
    quantity: Int,
    isIntraday: Boolean,
    brokerageFlatOrMax: Double = 20.0,
    dpChargeOnDeliverySell: Double = 15.34
  ): TradeChargeBreakdown {
    val zeroResult = TradeChargeBreakdown(
      grossPnl = 0.0,
      brokerage = 0.0,
      stt = 0.0,
      exchangeTxnCharge = 0.0,
      sebiCharge = 0.0,
      stampDuty = 0.0,
      gst = 0.0,
      dpCharge = 0.0,
      totalCharges = 0.0,
      netPnl = 0.0,
      breakevenPerShare = 0.0
    )
    if (
      !buyPrice.isFinite() || buyPrice <= 0.0 ||
      !sellPrice.isFinite() || sellPrice <= 0.0 ||
      quantity <= 0 ||
      !brokerageFlatOrMax.isFinite() || brokerageFlatOrMax < 0.0 ||
      !dpChargeOnDeliverySell.isFinite() || dpChargeOnDeliverySell < 0.0
    ) {
      return zeroResult
    }

    val buyTurnover = buyPrice * quantity
    val sellTurnover = sellPrice * quantity
    val totalTurnover = buyTurnover + sellTurnover
    if (!totalTurnover.isFinite()) return zeroResult

    val grossPnl = sellTurnover - buyTurnover
    val brokerage = if (isIntraday) {
      (buyTurnover * 0.0003).coerceAtMost(brokerageFlatOrMax) +
        (sellTurnover * 0.0003).coerceAtMost(brokerageFlatOrMax)
    } else {
      0.0
    }
    val stt = if (isIntraday) sellTurnover * 0.00025 else totalTurnover * 0.001
    val exchangeTxnCharge = totalTurnover * 0.0000297
    val sebiCharge = totalTurnover * 0.000001
    val stampDuty = if (isIntraday) buyTurnover * 0.00003 else buyTurnover * 0.00015
    val gst = (brokerage + exchangeTxnCharge + sebiCharge) * 0.18
    val dpCharge = if (isIntraday) 0.0 else dpChargeOnDeliverySell
    val totalCharges = brokerage + stt + exchangeTxnCharge + sebiCharge + stampDuty + gst + dpCharge

    // Calculate exact breakeven price where Net PnL is 0 after factoring in sell-side charges
    var low = buyPrice
    var high = buyPrice * 2.0
    var exactBreakeven = buyPrice + totalCharges / quantity
    for (step in 0 until 24) {
      val mid = (low + high) / 2.0
      val testSellTurnover = mid * quantity
      val testGross = testSellTurnover - buyTurnover
      val testB = if (isIntraday) {
        (buyTurnover * 0.0003).coerceAtMost(brokerageFlatOrMax) +
          (testSellTurnover * 0.0003).coerceAtMost(brokerageFlatOrMax)
      } else 0.0
      val testTurnover = buyTurnover + testSellTurnover
      val testStt = if (isIntraday) testSellTurnover * 0.00025 else testTurnover * 0.001
      val testEx = testTurnover * 0.0000297
      val testSebi = testTurnover * 0.000001
      val testStamp = if (isIntraday) buyTurnover * 0.00003 else buyTurnover * 0.00015
      val testGst = (testB + testEx + testSebi) * 0.18
      val testDp = if (isIntraday) 0.0 else dpChargeOnDeliverySell
      val testCharges = testB + testStt + testEx + testSebi + testStamp + testGst + testDp
      val net = testGross - testCharges
      if (net >= 0.0) {
        exactBreakeven = mid
        high = mid
      } else {
        low = mid
      }
    }

    return TradeChargeBreakdown(
      grossPnl = grossPnl,
      brokerage = brokerage,
      stt = stt,
      exchangeTxnCharge = exchangeTxnCharge,
      sebiCharge = sebiCharge,
      stampDuty = stampDuty,
      gst = gst,
      dpCharge = dpCharge,
      totalCharges = totalCharges,
      netPnl = grossPnl - totalCharges,
      breakevenPerShare = exactBreakeven
    )
  }

  fun calculateSip(
    monthlyInvestment: Double,
    annualReturnRate: Double,
    tenureYears: Int
  ): InvestmentResult {
    if (monthlyInvestment <= 0.0 || tenureYears <= 0) {
      return InvestmentResult("SIP", 0.0, 0.0, 0.0, emptyList())
    }

    val monthlyRate = (annualReturnRate / 12.0) / 100.0
    val totalMonths = tenureYears * 12
    val investedAmount = monthlyInvestment * totalMonths

    val totalValue = if (monthlyRate <= 0.0) {
      investedAmount
    } else {
      monthlyInvestment * (((1.0 + monthlyRate).pow(totalMonths.toDouble()) - 1.0) / monthlyRate) * (1.0 + monthlyRate)
    }

    val estimatedReturns = (totalValue - investedAmount).coerceAtLeast(0.0)

    val yearlyBreakdown = mutableListOf<YearlyBreakdown>()
    for (year in 1..tenureYears) {
      val monthsSoFar = year * 12
      val investedSoFar = monthlyInvestment * monthsSoFar
      val valueSoFar = if (monthlyRate <= 0.0) {
        investedSoFar
      } else {
        monthlyInvestment * (((1.0 + monthlyRate).pow(monthsSoFar.toDouble()) - 1.0) / monthlyRate) * (1.0 + monthlyRate)
      }
      val returnsSoFar = (valueSoFar - investedSoFar).coerceAtLeast(0.0)
      yearlyBreakdown.add(YearlyBreakdown(year, investedSoFar, returnsSoFar, valueSoFar))
    }

    return InvestmentResult(
      title = "SIP Investment",
      investedAmount = investedAmount,
      estimatedReturns = estimatedReturns,
      totalValue = totalValue,
      yearlyBreakdown = yearlyBreakdown
    )
  }

  fun calculateLumpSum(
    initialInvestment: Double,
    annualReturnRate: Double,
    tenureYears: Int
  ): InvestmentResult {
    if (initialInvestment <= 0.0 || tenureYears <= 0) {
      return InvestmentResult("Lump Sum", 0.0, 0.0, 0.0, emptyList())
    }

    val rate = annualReturnRate / 100.0
    val totalValue = initialInvestment * (1.0 + rate).pow(tenureYears.toDouble())
    val estimatedReturns = (totalValue - initialInvestment).coerceAtLeast(0.0)

    val yearlyBreakdown = mutableListOf<YearlyBreakdown>()
    for (year in 1..tenureYears) {
      val valueSoFar = initialInvestment * (1.0 + rate).pow(year.toDouble())
      val returnsSoFar = (valueSoFar - initialInvestment).coerceAtLeast(0.0)
      yearlyBreakdown.add(YearlyBreakdown(year, initialInvestment, returnsSoFar, valueSoFar))
    }

    return InvestmentResult(
      title = "Lump Sum",
      investedAmount = initialInvestment,
      estimatedReturns = estimatedReturns,
      totalValue = totalValue,
      yearlyBreakdown = yearlyBreakdown
    )
  }

  fun calculateCompoundInterest(
    principal: Double,
    annualRate: Double,
    tenureYears: Int,
    frequency: CompoundingFrequency
  ): InvestmentResult {
    if (principal <= 0.0 || tenureYears <= 0) {
      return InvestmentResult("Compound Interest", 0.0, 0.0, 0.0, emptyList())
    }

    val n = frequency.timesPerYear.toDouble()
    val r = (annualRate / 100.0) / n

    val yearlyBreakdown = mutableListOf<YearlyBreakdown>()
    for (year in 1..tenureYears) {
      val periods = n * year
      val valueSoFar = principal * (1.0 + r).pow(periods)
      val returnsSoFar = (valueSoFar - principal).coerceAtLeast(0.0)
      yearlyBreakdown.add(YearlyBreakdown(year, principal, returnsSoFar, valueSoFar))
    }

    val finalTotal = yearlyBreakdown.lastOrNull()?.balance ?: principal
    val finalReturns = (finalTotal - principal).coerceAtLeast(0.0)

    return InvestmentResult(
      title = "Compound Interest (${frequency.title})",
      investedAmount = principal,
      estimatedReturns = finalReturns,
      totalValue = finalTotal,
      yearlyBreakdown = yearlyBreakdown
    )
  }

  fun calculatePpf(
    annualDeposit: Double,
    annualRate: Double = 7.1,
    tenureYears: Int = 15
  ): InvestmentResult {
    if (annualDeposit <= 0.0 || tenureYears <= 0) {
      return InvestmentResult("PPF", 0.0, 0.0, 0.0, emptyList())
    }

    val r = annualRate / 100.0
    var balance = 0.0
    var totalInvested = 0.0
    val yearlyBreakdown = mutableListOf<YearlyBreakdown>()

    for (year in 1..tenureYears) {
      totalInvested += annualDeposit
      val opening = balance + annualDeposit
      val interest = opening * r
      balance = opening + interest
      val returns = balance - totalInvested
      yearlyBreakdown.add(YearlyBreakdown(year, totalInvested, returns, balance))
    }

    return InvestmentResult(
      title = "PPF Account",
      investedAmount = totalInvested,
      estimatedReturns = (balance - totalInvested).coerceAtLeast(0.0),
      totalValue = balance,
      yearlyBreakdown = yearlyBreakdown
    )
  }

  fun calculateFd(
    depositAmount: Double,
    annualRate: Double,
    tenureYears: Double,
    frequency: CompoundingFrequency = CompoundingFrequency.QUARTERLY
  ): InvestmentResult {
    if (depositAmount <= 0.0 || tenureYears <= 0.0) {
      return InvestmentResult("Fixed Deposit", 0.0, 0.0, 0.0, emptyList())
    }

    val n = frequency.timesPerYear.toDouble()
    val r = (annualRate / 100.0) / n
    val totalPeriods = n * tenureYears
    val totalValue = depositAmount * (1.0 + r).pow(totalPeriods)
    val returns = (totalValue - depositAmount).coerceAtLeast(0.0)

    val wholeYears = tenureYears.toInt().coerceAtLeast(1)
    val yearlyBreakdown = mutableListOf<YearlyBreakdown>()
    for (year in 1..wholeYears) {
      val periods = n * year
      val valSoFar = depositAmount * (1.0 + r).pow(periods)
      yearlyBreakdown.add(YearlyBreakdown(year, depositAmount, (valSoFar - depositAmount).coerceAtLeast(0.0), valSoFar))
    }

    return InvestmentResult(
      title = "Fixed Deposit (FD)",
      investedAmount = depositAmount,
      estimatedReturns = returns,
      totalValue = totalValue,
      yearlyBreakdown = yearlyBreakdown
    )
  }
}
