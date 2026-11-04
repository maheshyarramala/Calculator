package com.example.domain.splitbill

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

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

    val safeTax = nonNegative(tax)
    val safeTip = nonNegative(tip)
    val safeDiscount = nonNegative(discount)

    return when (mode) {
      SplitMode.ITEMIZED -> calculateItemizedBreakdowns(
        participants, items, safeTax, safeTip, safeDiscount
      )
      SplitMode.EQUAL -> {
        val weights = List(participants.size) { 1.0 }
        val subtotals = distribute(nonNegative(manualBillTotal), weights)
        buildBreakdowns(participants, subtotals, safeTax, safeTip, safeDiscount, weights)
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
    val subtotalCentsByParticipant = participants.associate { it.id to 0L }.toMutableMap()
    val allParticipantIds = participants.map { it.id }
    items.forEach { item ->
      val claimants = if (item.claimedParticipantIds.isEmpty()) {
        allParticipantIds
      } else {
        item.claimedParticipantIds.filter { id -> id in subtotalCentsByParticipant }
      }
      val effectiveClaimants = if (claimants.isEmpty()) allParticipantIds else claimants
      if (effectiveClaimants.isNotEmpty()) {
        val itemCents = toCents(nonNegative(item.price) * item.quantity.coerceAtLeast(1))
        val itemShares = distributeCents(itemCents, List(effectiveClaimants.size) { 1.0 })
        effectiveClaimants.forEachIndexed { index, participantId ->
          subtotalCentsByParticipant[participantId] =
            (subtotalCentsByParticipant[participantId] ?: 0L) + itemShares[index]
        }
      }
    }

    val subtotals = participants.map { (subtotalCentsByParticipant[it.id] ?: 0L) / 100.0 }
    val weights = subtotals.map { it.coerceAtLeast(0.0) }
    val paymentsByParticipant = participants.associate { participant ->
      val itemPayments = items
        .filter { it.paidByParticipantId == participant.id }
        .sumOf { toCents(nonNegative(it.price) * it.quantity.coerceAtLeast(1)) / 100.0 }
      participant.id to (nonNegative(participant.paidAmount) + itemPayments)
    }
    return buildBreakdowns(participants, subtotals, tax, tip, discount, weights, paymentsByParticipant)
  }

  private fun buildBreakdowns(
    participants: List<Participant>,
    subtotals: List<Double>,
    tax: Double,
    tip: Double,
    discount: Double,
    weights: List<Double>,
    paymentsByParticipant: Map<String, Double> = participants.associate { it.id to it.paidAmount }
  ): List<PersonBreakdown> {
    val baseTotal = subtotals.sum()
    val safeDiscount = nonNegative(discount).coerceAtMost(baseTotal + tax + tip)
    val taxShares = distribute(tax, weights)
    val tipShares = distribute(tip, weights)
    val discountShares = distribute(safeDiscount, weights)
    val grandTotal = (baseTotal + tax + tip - safeDiscount).coerceAtLeast(0.0)
    val totalShares = distribute(grandTotal, weights)

    return participants.mapIndexed { index, participant ->
      val paid = paymentsByParticipant[participant.id] ?: 0.0
      val totalToPay = totalShares[index]
      PersonBreakdown(
        participant = participant,
        subtotal = subtotals[index],
        taxShare = taxShares[index],
        tipShare = tipShares[index],
        discountShare = discountShares[index],
        totalToPay = totalToPay,
        paidAmount = roundToTwoDecimals(paid),
        netBalance = roundToTwoDecimals(paid - totalToPay)
      )
    }
  }

  private fun distribute(amount: Double, weights: List<Double>): List<Double> {
    if (weights.isEmpty()) return emptyList()
    val cents = toCents(amount)
    return distributeCents(cents, weights).map { it / 100.0 }
  }

  private fun distributeCents(amountCents: Long, weights: List<Double>): List<Long> {
    if (weights.isEmpty()) return emptyList()
    val safeWeights = weights.map { nonNegative(it) }
    val weightTotal = safeWeights.sum()
    val actualWeights = if (weightTotal > 0.0) safeWeights else List(weights.size) { 1.0 }
    val actualTotal = actualWeights.sum()
    val exactShares = actualWeights.map { amountCents * it / actualTotal }
    val cents = exactShares.map { floor(it).toLong() }.toMutableList()
    var remainder = amountCents - cents.sum()
    val priority = exactShares.indices.sortedByDescending { exactShares[it] - cents[it] }
    var priorityIndex = 0
    while (remainder > 0) {
      cents[priority[priorityIndex % priority.size]]++
      remainder--
      priorityIndex++
    }
    return cents
  }

  private fun nonNegative(value: Double): Double =
    if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0

  private fun toCents(amount: Double): Long =
    BigDecimal.valueOf(nonNegative(amount))
      .setScale(2, RoundingMode.HALF_UP)
      .movePointRight(2)
      .toLong()

  /**
   * Greedy Debt Minimization Algorithm.
   * Reduces an N-person network of debts into the minimal number of direct peer-to-peer transfers.
   * e.g., if A owes B $20 and B owes C $20, simplifies to: A pays C $20.
   */
  fun simplifyDebts(breakdowns: List<PersonBreakdown>): List<DebtTransfer> {
    return calculateTransfers(breakdowns, optimize = true)
  }

  fun directTransfers(breakdowns: List<PersonBreakdown>): List<DebtTransfer> {
    return calculateTransfers(breakdowns, optimize = false)
  }

  private fun calculateTransfers(
    breakdowns: List<PersonBreakdown>,
    optimize: Boolean
  ): List<DebtTransfer> {
    if (breakdowns.size < 2) return emptyList()

    // Map each participant to their net balance (paid - owed)
    // Positive = Creditor (should receive money)
    // Negative = Debtor (needs to pay money)
    data class BalanceEntry(val participant: Participant, var balance: Double)

    val creditors = mutableListOf<BalanceEntry>()
    val debtors = mutableListOf<BalanceEntry>()

    breakdowns.forEach { b ->
      val net = roundToTwoDecimals(b.paidAmount - b.totalToPay)
      if (net > 0.005) {
        creditors.add(BalanceEntry(b.participant, net))
      } else if (net < -0.005) {
        debtors.add(BalanceEntry(b.participant, abs(net)))
      }
    }

    if (optimize) {
      creditors.sortByDescending { it.balance }
      debtors.sortByDescending { it.balance }
    }

    val transfers = mutableListOf<DebtTransfer>()

    var cIndex = 0
    var dIndex = 0

    while (cIndex < creditors.size && dIndex < debtors.size) {
      val creditor = creditors[cIndex]
      val debtor = debtors[dIndex]

      val amountToTransfer = roundToTwoDecimals(min(creditor.balance, debtor.balance))

      if (amountToTransfer > 0.005) {
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

      if (creditor.balance <= 0.005) {
        cIndex++
      }
      if (debtor.balance <= 0.005) {
        dIndex++
      }
    }

    return transfers
  }

  fun roundToTwoDecimals(value: Double): Double {
    if (!value.isFinite()) return 0.0
    return BigDecimal.valueOf(value)
      .setScale(2, RoundingMode.HALF_UP)
      .toDouble()
  }

  fun formatCurrency(amount: Double, symbol: String = "₹"): String {
    return String.format(Locale.US, "%s%,.2f", symbol, amount)
  }
}
