package com.example.ui.screens.splitbill

import androidx.lifecycle.ViewModel
import com.example.domain.splitbill.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class SplitBillUiState(
  val billTitle: String = "Split 1",
  val activeGroupId: String = "group-1",
  val groups: List<SplitBillGroup> = listOf(SplitBillGroup("group-1", "Split 1", "INR", "₹", listOf(Participant(id = "p1", name = "You (Me)", avatarColorHex = "#4F46E5", paidAmount = 0.0)), emptyList(), emptySet(), emptyMap(), emptyMap())),
  val currencyCode: String = "INR",
  val currencySymbol: String = "₹",
  val participants: List<Participant> = listOf(
    Participant(id = "p1", name = "You (Me)", avatarColorHex = "#4F46E5", paidAmount = 0.0)
  ),
  val items: List<ReceiptItem> = emptyList(),
  val taxAmount: Double = 0.0,
  val tipAmount: Double = 0.0,
  val discountAmount: Double = 0.0,
  val simplifyDebtsEnabled: Boolean = true,
  val showOcrDialog: Boolean = false,
  val showAddParticipantDialog: Boolean = false,
  val showAddItemDialog: Boolean = false,
  val settledTransferKeys: Set<String> = emptySet(),
  val settledPaymentSources: Map<String, String> = emptyMap(),
  val settledPaymentReferences: Map<String, String> = emptyMap(),
  val activeTab: Int = 0 // 0: Items & Split, 1: Diners & Claims, 2: Settlement Ledger
)


data class SplitBillGroup(
  val id: String,
  val name: String,
  val currencyCode: String,
  val currencySymbol: String,
  val participants: List<Participant>,
  val items: List<ReceiptItem>,
  val settledTransferKeys: Set<String>,
  val settledPaymentSources: Map<String, String>,
  val settledPaymentReferences: Map<String, String>
)

class SplitBillViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(SplitBillUiState())
  val uiState: StateFlow<SplitBillUiState> = _uiState.asStateFlow()


  private fun currentGroup(state: SplitBillUiState) = SplitBillGroup(
    id = state.activeGroupId, name = state.billTitle, currencyCode = state.currencyCode,
    currencySymbol = state.currencySymbol, participants = state.participants, items = state.items,
    settledTransferKeys = state.settledTransferKeys, settledPaymentSources = state.settledPaymentSources,
    settledPaymentReferences = state.settledPaymentReferences
  )

  fun createNewGroup(name: String) {
    val cleanName = name.trim().ifBlank { "Split ${_uiState.value.groups.size + 1}" }
    _uiState.update { state ->
      val saved = currentGroup(state)
      val id = UUID.randomUUID().toString()
      val next = SplitBillGroup(id, cleanName, state.currencyCode, state.currencySymbol,
        listOf(Participant(id = "p1", name = "You (Me)", avatarColorHex = "#4F46E5")),
        emptyList(), emptySet(), emptyMap(), emptyMap())
      state.copy(
        billTitle = cleanName, activeGroupId = id, groups = state.groups.filterNot { it.id == saved.id } + saved + next,
        participants = next.participants, items = emptyList(), settledTransferKeys = emptySet(),
        settledPaymentSources = emptyMap(), settledPaymentReferences = emptyMap(), activeTab = 0
      )
    }
  }

  fun selectGroup(groupId: String) {
    _uiState.update { state ->
      if (groupId == state.activeGroupId) return@update state
      val savedCurrent = currentGroup(state)
      val target = state.groups.firstOrNull { it.id == groupId } ?: return@update state
      state.copy(
        billTitle = target.name, activeGroupId = target.id,
        groups = state.groups.filterNot { it.id == savedCurrent.id } + savedCurrent,
        currencyCode = target.currencyCode, currencySymbol = target.currencySymbol,
        participants = target.participants, items = target.items,
        settledTransferKeys = target.settledTransferKeys,
        settledPaymentSources = target.settledPaymentSources,
        settledPaymentReferences = target.settledPaymentReferences, activeTab = 0
      )
    }
  }

  fun setTab(index: Int) {
    _uiState.update { it.copy(activeTab = index) }
  }

  fun setBillTitle(title: String) {
    _uiState.update { it.copy(billTitle = title) }
  }

  fun setCurrency(code: String, symbol: String) {
    _uiState.update { it.copy(currencyCode = code, currencySymbol = symbol) }
  }

  fun setTaxAmount(tax: Double) {
    _uiState.update { it.copy(taxAmount = nonNegative(tax)) }
  }

  fun setTipAmount(tip: Double) {
    _uiState.update { it.copy(tipAmount = nonNegative(tip)) }
  }

  fun setDiscountAmount(discount: Double) {
    _uiState.update { it.copy(discountAmount = nonNegative(discount)) }
  }

  fun toggleSimplifyDebts() {
    _uiState.update { it.copy(simplifyDebtsEnabled = !it.simplifyDebtsEnabled) }
  }

  fun setShowOcrDialog(show: Boolean) {
    _uiState.update { it.copy(showOcrDialog = show) }
  }

  fun setShowAddParticipantDialog(show: Boolean) {
    _uiState.update { it.copy(showAddParticipantDialog = show) }
  }

  fun setShowAddItemDialog(show: Boolean) {
    _uiState.update { it.copy(showAddItemDialog = show) }
  }

  // Participants Management
  fun addParticipant(name: String, upiId: String, paypal: String, paid: Double) {
    val colors = listOf("#4F46E5", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6", "#06B6D4", "#EF4444")
    val randomColor = colors[(_uiState.value.participants.size) % colors.size]
    val newParticipant = Participant(
      id = UUID.randomUUID().toString(),
      name = name.ifBlank { "Diner ${_uiState.value.participants.size + 1}" },
      avatarColorHex = randomColor,
      upiId = upiId.trim(),
      paypalUsername = paypal.trim(),
      paidAmount = nonNegative(paid)
    )
    _uiState.update {
      it.copy(participants = it.participants + newParticipant)
    }
  }

  fun updateParticipant(id: String, name: String, upiId: String, paypal: String, paid: Double) {
    _uiState.update { state -> state.copy(participants = state.participants.map { p ->
      if (p.id == id) p.copy(name = name.trim().ifBlank { p.name }, upiId = upiId.trim(), paypalUsername = paypal.trim(), paidAmount = nonNegative(paid)) else p
    }) }
  }

  fun removeParticipant(id: String) {
    _uiState.update { state ->
      val updatedParticipants = state.participants.filter { it.id != id }
      val updatedItems = state.items.map { item ->
        item.copy(
          claimedParticipantIds = item.claimedParticipantIds.filter { it != id },
          paidByParticipantId = item.paidByParticipantId?.takeUnless { it == id }
        )
      }
      state.copy(participants = updatedParticipants, items = updatedItems)
    }
  }

  fun updateParticipantPaid(id: String, paid: Double) {
    _uiState.update { state ->
      state.copy(
        participants = state.participants.map { p ->
          if (p.id == id) p.copy(paidAmount = nonNegative(paid)) else p
        }
      )
    }
  }

  // Items Management
  fun addItem(
    title: String,
    price: Double,
    quantity: Int,
    claimedIds: List<String>,
    paidByParticipantId: String? = null,
    paymentSource: String = "Cash",
    paymentAccountId: Long? = null
  ): String {
    val newItem = ReceiptItem(
      id = UUID.randomUUID().toString(),
      title = title.ifBlank { "Item" },
      price = nonNegative(price),
      quantity = quantity.coerceAtLeast(1),
      claimedParticipantIds = claimedIds,
      paidByParticipantId = paidByParticipantId,
      paymentSource = paymentSource,
      paymentAccountId = paymentAccountId
    )
    _uiState.update { it.copy(items = it.items + newItem) }
    return newItem.id
  }

  fun importTripExpenses(expenses: List<com.example.data.local.entity.ExpenseRecord>) {
    val current = _uiState.value
    val meId = current.participants.firstOrNull()?.id
    val existingIds = current.items.map { it.id }.toSet()
    val imported = expenses.filter { "trip-expense:${it.id}" !in existingIds }.map { expense ->
      ReceiptItem(
        id = "trip-expense:${expense.id}", title = expense.title, price = nonNegative(expense.amount), quantity = 1,
        claimedParticipantIds = current.participants.map { it.id }, paidByParticipantId = meId,
        paymentSource = expense.bankAccountName.ifBlank { "Finance" }, paymentAccountId = expense.bankAccountId,
        isImportedFromFinance = true
      )
    }
    _uiState.update { it.copy(items = it.items + imported) }
  }

  fun updateItem(updated: ReceiptItem) {
    _uiState.update { state -> state.copy(items = state.items.map { if (it.id == updated.id) updated else it }) }
  }

  fun removeItem(id: String) {
    _uiState.update { state ->
      state.copy(items = state.items.filter { it.id != id })
    }
  }

  fun toggleItemClaim(itemId: String, participantId: String) {
    _uiState.update { state ->
      state.copy(
        items = state.items.map { item ->
          if (item.id == itemId) {
            val currentClaims = if (item.claimedParticipantIds.isEmpty()) {
              state.participants.map { it.id }
            } else {
              item.claimedParticipantIds
            }
            val isClaimed = participantId in currentClaims
            val updatedClaims = if (isClaimed) {
              currentClaims - participantId
            } else {
              currentClaims + participantId
            }
            item.copy(claimedParticipantIds = updatedClaims)
          } else {
            item
          }
        }
      )
    }
  }

  fun claimItemForAll(itemId: String) {
    val allIds = _uiState.value.participants.map { it.id }
    _uiState.update { state ->
      state.copy(
        items = state.items.map { item ->
          if (item.id == itemId) item.copy(claimedParticipantIds = allIds) else item
        }
      )
    }
  }

  // OCR & Receipt Parsing
  fun applyParsedReceipt(parsed: ParsedReceipt) {
    _uiState.update { state ->
      state.copy(
        billTitle = parsed.title,
        items = parsed.items,
        taxAmount = parsed.tax,
        tipAmount = parsed.tip,
        discountAmount = parsed.discount,
        showOcrDialog = false
      )
    }
  }

  fun loadDemo(receiptText: String, title: String) {
    val parsed = ReceiptOcrParser.parseReceiptText(receiptText, title)
    applyParsedReceipt(parsed)
  }

  fun toggleSettledTransfer(transferKey: String, paymentSource: String? = null, financeReference: String? = null) {
    _uiState.update { state ->
      val wasSettled = transferKey in state.settledTransferKeys
      val updatedKeys = if (wasSettled) state.settledTransferKeys - transferKey else state.settledTransferKeys + transferKey
      val updatedSources = if (wasSettled) state.settledPaymentSources - transferKey
        else state.settledPaymentSources + (transferKey to paymentSource.orEmpty())
      val updatedReferences = if (wasSettled) state.settledPaymentReferences - transferKey
        else state.settledPaymentReferences + (transferKey to financeReference.orEmpty())
      state.copy(settledTransferKeys = updatedKeys, settledPaymentSources = updatedSources, settledPaymentReferences = updatedReferences)
    }
  }

  private fun nonNegative(value: Double): Double =
    if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
}
