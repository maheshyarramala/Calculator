package com.example

import com.example.domain.calculator.InvestmentCalculators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentTradeCalculatorTest {

  @Test
  fun calculatesWeightedStockAverageAndTargetProfit() {
    val result = InvestmentCalculators.calculateStockAverage(
      qty1 = 50,
      price1 = 420.0,
      qty2 = 30,
      price2 = 380.0,
      currentMarketPrice = 400.0
    )

    assertEquals(80, result.totalQuantity)
    assertEquals(32_400.0, result.totalInvested, 0.001)
    assertEquals(405.0, result.averagePrice, 0.001)
    assertEquals(400.0, result.targetSellPrice, 0.001)
    assertEquals(-400.0, result.netProfitAtTarget, 0.001)
  }

  @Test
  fun equityTradeProfitIncludesChargesForIntradayAndDelivery() {
    val intraday = InvestmentCalculators.calculateEquityTradePnl(
      buyPrice = 250.0,
      sellPrice = 265.0,
      quantity = 100,
      isIntraday = true
    )
    val delivery = InvestmentCalculators.calculateEquityTradePnl(
      buyPrice = 250.0,
      sellPrice = 265.0,
      quantity = 100,
      isIntraday = false
    )

    assertEquals(1500.0, intraday.grossPnl, 0.001)
    assertTrue(intraday.totalCharges > 0.0)
    assertTrue(intraday.netPnl < intraday.grossPnl)
    assertTrue(delivery.dpCharge > 0.0)
    assertTrue(delivery.stt > intraday.stt)
    assertTrue(delivery.netPnl < delivery.grossPnl)
  }

  @Test
  fun invalidInputsReturnZeroResults() {
    val average = InvestmentCalculators.calculateStockAverage(0, 0.0, -1, 100.0)
    val trade = InvestmentCalculators.calculateEquityTradePnl(
      buyPrice = -1.0,
      sellPrice = 100.0,
      quantity = 1,
      isIntraday = true
    )

    assertEquals(0, average.totalQuantity)
    assertEquals(0.0, average.averagePrice, 0.0)
    assertEquals(0.0, trade.totalCharges, 0.0)
    assertEquals(0.0, trade.netPnl, 0.0)
  }
}
