package com.example

import com.example.domain.splitbill.DebtSimplifier
import com.example.domain.splitbill.Participant
import com.example.domain.splitbill.ReceiptItem
import com.example.domain.splitbill.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitBillCalculationTest {

  @Test
  fun equalSplitDistributesRoundingCentsExactly() {
    val participants = listOf(
      Participant(id = "a", name = "A"),
      Participant(id = "b", name = "B"),
      Participant(id = "c", name = "C")
    )

    val result = DebtSimplifier.calculateBreakdowns(
      mode = SplitMode.EQUAL,
      participants = participants,
      items = emptyList(),
      tax = 0.0,
      tip = 0.0,
      discount = 0.0,
      manualBillTotal = 10.0
    )

    assertEquals(listOf(3.34, 3.33, 3.33), result.map { it.totalToPay })
    assertEquals(10.0, result.sumOf { it.totalToPay }, 0.001)
  }

  @Test
  fun equalSplitIncludesTaxTipAndDiscount() {
    val participants = listOf(
      Participant(id = "a", name = "A"),
      Participant(id = "b", name = "B")
    )

    val result = DebtSimplifier.calculateBreakdowns(
      mode = SplitMode.EQUAL,
      participants = participants,
      items = emptyList(),
      tax = 10.0,
      tip = 5.0,
      discount = 3.0,
      manualBillTotal = 100.0
    )

    assertEquals(56.0, result[0].totalToPay, 0.001)
    assertEquals(56.0, result[1].totalToPay, 0.001)
    assertEquals(112.0, result.sumOf { it.totalToPay }, 0.001)
  }

  @Test
  fun itemPayerIsCreditedAndSharedItemCentsArePreserved() {
    val participants = listOf(
      Participant(id = "a", name = "A"),
      Participant(id = "b", name = "B"),
      Participant(id = "c", name = "C")
    )
    val item = ReceiptItem(
      title = "Shared item",
      price = 10.0,
      claimedParticipantIds = participants.map { it.id },
      paidByParticipantId = "a"
    )

    val result = DebtSimplifier.calculateBreakdowns(
      mode = SplitMode.ITEMIZED,
      participants = participants,
      items = listOf(item),
      tax = 0.0,
      tip = 0.0,
      discount = 0.0,
      manualBillTotal = 0.0
    )

    assertEquals(10.0, result.sumOf { it.totalToPay }, 0.001)
    assertEquals(10.0, result.first { it.participant.id == "a" }.paidAmount, 0.001)
    val transfers = DebtSimplifier.simplifyDebts(result)
    assertEquals(6.66, transfers.sumOf { it.amount }, 0.001)
    assertTrue(transfers.all { it.toParticipant.id == "a" })
  }

  @Test
  fun itemIsSplitOnlyAmongSelectedDiners() {
    val participants = listOf(
      Participant(id = "a", name = "A"),
      Participant(id = "b", name = "B"),
      Participant(id = "c", name = "C")
    )
    val item = ReceiptItem(
      title = "Shared item",
      price = 12.0,
      claimedParticipantIds = listOf("a", "c")
    )

    val result = DebtSimplifier.calculateBreakdowns(
      mode = SplitMode.ITEMIZED,
      participants = participants,
      items = listOf(item),
      tax = 0.0,
      tip = 0.0,
      discount = 0.0,
      manualBillTotal = 0.0
    )

    assertEquals(6.0, result.first { it.participant.id == "a" }.totalToPay, 0.001)
    assertEquals(0.0, result.first { it.participant.id == "b" }.totalToPay, 0.001)
    assertEquals(6.0, result.first { it.participant.id == "c" }.totalToPay, 0.001)
  }

  @Test
  fun settlementIncludesOneCentBalances() {
    val paidParticipant = Participant(id = "a", name = "A", paidAmount = 1.0)
    val owingParticipant = Participant(id = "b", name = "B", paidAmount = 1.0)
    val breakdowns = listOf(
      com.example.domain.splitbill.PersonBreakdown(
        participant = paidParticipant,
        subtotal = 0.99,
        taxShare = 0.0,
        tipShare = 0.0,
        discountShare = 0.0,
        totalToPay = 0.99,
        paidAmount = 1.0,
        netBalance = 0.01
      ),
      com.example.domain.splitbill.PersonBreakdown(
        participant = owingParticipant,
        subtotal = 1.01,
        taxShare = 0.0,
        tipShare = 0.0,
        discountShare = 0.0,
        totalToPay = 1.01,
        paidAmount = 1.0,
        netBalance = -0.01
      )
    )

    val transfers = DebtSimplifier.directTransfers(breakdowns)

    assertEquals(1, transfers.size)
    assertEquals(0.01, transfers.single().amount, 0.001)
  }
}
