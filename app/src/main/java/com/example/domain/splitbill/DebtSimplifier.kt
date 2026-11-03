package com.example.domain.splitbill

import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

object DebtSimplifier {

  /**
   * Calculates individual totals and net balances for all participants based on the selected mode.
   * Ensures deterministic rounding cents handling so the sum of all individual payments matches grandTotal.
   */
  fun calculateBreakdowns(
    mode: SplitMode,
    participants: List<Participant>,
    items: List<ReceiptItem>,
    tax: Double,
    tip: Double,
    discount: Double,
    manualBillTotal: Double
  ): List<PersonBreakdown> {
    if (participants.isEmpty()) return emptyList()

    val safeTax = if (tax < 0) 0.0 else tax
    val safeTip = if (tip < 0) 0.0 else tip
    val safeDiscount = if (discount < 0) 0.0 else discount

    return when (mode) {
      SplitMode.ITEMIZED -> {
        calculateItemizedBreakdowns(participants, items, safeTax, safeTip, safeDiscount)
      }
      SplitMode.EQUAL -> {
        val total = (manualBillTotal + safeTax + safeTip - safeDiscount).coerceAtLeast(0.0)
        val perPerson = if (participants.isNotEmpty()) total / participants.size else 0.0
        participants.map { p ->
          val rounded = roundToTwoDecimals(perPerson)
          PersonBreakdown(
            participant = p,
            subtotal = roundToTwoDecimals(manualBillTotal / participants.size),
            taxShare = roundToTwoDecimals(safeTax / participants.size),
            tipShare = roundToTwoDecimals(safeTip / participants.size),
            discountShare = roundToTwoDecimals(safeDiscount / participants.size),
            totalToPay = rounded,
            paidAmount = p.paidAmount,
            netBalance = roundToTwoDecimals(p.paidAmount - rounded)
          )
        }
      }
      SplitMode.EXACT -> {
        participants.map { p ->
          val exact = p.exactAmount.coerceAtLeast(0.0)
          PersonBreakdown(
            participant = p,
            subtotal = exact,
            taxShare = 0.0,
            tipShare = 0.0,
            discountShare = 0.0,
            totalToPay = roundToTwoDecimals(exact),
            paidAmount = p.paidAmount,
            netBalance = roundToTwoDecimals(p.paidAmount - exact)
          )
        }
      }
      SplitMode.PERCENTAGE -> {
        val total = (manualBillTotal + safeTax + safeTip - safeDiscount).coerceAtLeast(0.0)
        participants.map { p ->
          val share = (total * (p.percentage / 100.0)).coerceAtLeast(0.0)
          val rounded = roundToTwoDecimals(share)
          PersonBreakdown(
            participant = p,
            subtotal = roundToTwoDecimals(manualBillTotal * (p.percentage / 100.0)),
            taxShare = roundToTwoDecimals(safeTax * (p.percentage / 100.0)),
            tipShare = roundToTwoDecimals(safeTip * (p.percentage / 100.0)),
            discountShare = roundToTwoDecimals(safeDiscount * (p.percentage / 100.0)),
            totalToPay = rounded,
            paidAmount = p.paidAmount,
            netBalance = roundToTwoDecimals(p.paidAmount - rounded)
          )
        }
      }
      SplitMode.SHARES -> {
        val totalShares = participants.sumOf { it.shares.coerceAtLeast(1) }.toDouble()
        val total = (manualBillTotal + safeTax + safeTip - safeDiscount).coerceAtLeast(0.0)
        participants.map { p ->
          val ratio = if (totalShares > 0) p.shares.toDouble() / totalShares else 0.0
          val share = total * ratio
          val rounded = roundToTwoDecimals(share)
          PersonBreakdown(
            participant = p,
            subtotal = roundToTwoDecimals(manualBillTotal * ratio),
            taxShare = roundToTwoDecimals(safeTax * ratio),
            tipShare = roundToTwoDecimals(safeTip * ratio),
            discountShare = roundToTwoDecimals(safeDiscount * ratio),
            totalToPay = rounded,
            paidAmount = p.paidAmount,
            netBalance = roundToTwoDecimals(p.paidAmount - rounded)
          )
        }
      }
    }
  }

  private fun calculateItemizedBreakdowns(
    participants: List<Participant>,
    items: List<ReceiptItem>,
    tax: Double,
    tip: Double,
    discount: Double
  ): List<PersonBreakdown> {
    // 1. Calculate each participant's claimed subtotal
    val subtotalByParticipant = mutableMapOf<String, Double>()
    participants.forEach { subtotalByParticipant[it.id] = 0.0 }

    // If an item is claimed by nobody, split it evenly across all participants as common item
    val allParticipantIds = participants.map { it.id }

    items.forEach { item ->
      val claimants = if (item.claimedParticipantIds.isEmpty()) {
        allParticipantIds
      } else {
        item.claimedParticipantIds.filter { id -> participants.any { it.id == id } }
      }

      val effectiveClaimants = if (claimants.isEmpty()) allParticipantIds else claimants
      val itemTotal = item.price * item.quantity
      val perPersonPrice = itemTotal / effectiveClaimants.size

      effectiveClaimants.forEach { pId ->
        subtotalByParticipant[pId] = (subtotalByParticipant[pId] ?: 0.0) + perPersonPrice
      }
    }

    val totalItemsSubtotal = subtotalByParticipant.values.sum()
    val grandTotal = (totalItemsSubtotal + tax + tip - discount).coerceAtLeast(0.0)

    val breakdowns = mutableListOf<PersonBreakdown>()
    var runningSum = 0.0

    participants.forEachIndexed { index, participant ->
      val pSubtotal = subtotalByParticipant[participant.id] ?: 0.0
      val ratio = if (totalItemsSubtotal > 0.0001) pSubtotal / totalItemsSubtotal else (1.0 / participants.size)

      val pTax = tax * ratio
      val pTip = tip * ratio
      val pDiscount = discount * ratio
      var pTotal = (pSubtotal + pTax + pTip - pDiscount).coerceAtLeast(0.0)

      // Round to 2 decimals
      pTotal = roundToTwoDecimals(pTotal)

      // Handle the last participant to deterministically resolve any rounding cent drift
      if (index == participants.size - 1 && participants.size > 1) {
        val diff = roundToTwoDecimals(grandTotal - (runningSum + pTotal))
        pTotal = (pTotal + diff).coerceAtLeast(0.0)
      } else {
        runningSum += pTotal
      }

      breakdowns.add(
        PersonBreakdown(
          participant = participant,
          subtotal = roundToTwoDecimals(pSubtotal),
          taxShare = roundToTwoDecimals(pTax),
          tipShare = roundToTwoDecimals(pTip),
          discountShare = roundToTwoDecimals(pDiscount),
          totalToPay = pTotal,
          paidAmount = participant.paidAmount,
          netBalance = roundToTwoDecimals(participant.paidAmount - pTotal)
        )
      )
    }

    return breakdowns
  }

  /**
   * Greedy Debt Minimization Algorithm.
   * Reduces an N-person network of debts into the minimal number of direct peer-to-peer transfers.
   * e.g., if A owes B $20 and B owes C $20, simplifies to: A pays C $20.
   */
  fun simplifyDebts(breakdowns: List<PersonBreakdown>): List<DebtTransfer> {
    if (breakdowns.size < 2) return emptyList()

    // Map each participant to their net balance (paid - owed)
    // Positive = Creditor (should receive money)
    // Negative = Debtor (needs to pay money)
    data class BalanceEntry(val participant: Participant, var balance: Double)

    val creditors = mutableListOf<BalanceEntry>()
    val debtors = mutableListOf<BalanceEntry>()

    breakdowns.forEach { b ->
      val net = roundToTwoDecimals(b.paidAmount - b.totalToPay)
      if (net > 0.01) {
        creditors.add(BalanceEntry(b.participant, net))
      } else if (net < -0.01) {
        debtors.add(BalanceEntry(b.participant, abs(net)))
      }
    }

    // Sort to optimize matching (largest debtor pays largest creditor)
    creditors.sortByDescending { it.balance }
    debtors.sortByDescending { it.balance }

    val transfers = mutableListOf<DebtTransfer>()

    var cIndex = 0
    var dIndex = 0

    while (cIndex < creditors.size && dIndex < debtors.size) {
      val creditor = creditors[cIndex]
      val debtor = debtors[dIndex]

      val amountToTransfer = roundToTwoDecimals(min(creditor.balance, debtor.balance))

      if (amountToTransfer > 0.01) {
        transfers.add(
          DebtTransfer(
            fromParticipant = debtor.participant,
            toParticipant = creditor.participant,
            amount = amountToTransfer
          )
        )
      }

      creditor.balance = roundToTwoDecimals(creditor.balance - amountToTransfer)
      debtor.balance = roundToTwoDecimals(debtor.balance - amountToTransfer)

      if (creditor.balance <= 0.01) {
        cIndex++
      }
      if (debtor.balance <= 0.01) {
        dIndex++
      }
    }

    return transfers
  }

  fun roundToTwoDecimals(value: Double): Double {
    return round(value * 100.0) / 100.0
  }

  fun formatCurrency(amount: Double, symbol: String = "₹"): String {
    return String.format(Locale.US, "%s%,.2f", symbol, amount)
  }
}
