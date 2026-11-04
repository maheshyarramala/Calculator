package com.example.domain.splitbill

import java.util.UUID

enum class SplitMode(val title: String, val description: String) {
  EQUAL("Equal", "Split total evenly"),
  ITEMIZED("Itemized & OCR", "Claim individual dishes & prorate tax/tip")
}

data class Participant(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val avatarColorHex: String = "#6366F1",
  val upiId: String = "",
  val paypalUsername: String = "",
  val paidAmount: Double = 0.0
)

data class ReceiptItem(
  val id: String = UUID.randomUUID().toString(),
  val title: String,
  val price: Double,
  val quantity: Int = 1,
  val claimedParticipantIds: List<String> = emptyList(),
  val paidByParticipantId: String? = null
)

data class PersonBreakdown(
  val participant: Participant,
  val subtotal: Double,
  val taxShare: Double,
  val tipShare: Double,
  val discountShare: Double,
  val totalToPay: Double,
  val paidAmount: Double = 0.0,
  val netBalance: Double = 0.0 // positive = is owed money, negative = owes money
)

data class DebtTransfer(
  val fromParticipant: Participant,
  val toParticipant: Participant,
  val amount: Double,
  val isSettled: Boolean = false
)

data class ParsedReceipt(
  val title: String,
  val items: List<ReceiptItem>,
  val subtotal: Double,
  val tax: Double,
  val tip: Double,
  val discount: Double,
  val grandTotal: Double
)
