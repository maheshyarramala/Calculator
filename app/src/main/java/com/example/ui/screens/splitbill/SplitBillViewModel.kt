package com.example.ui.screens.splitbill

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.SplitBillRecord
import com.example.domain.splitbill.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SplitBillUiState(
  val billId: String = UUID.randomUUID().toString(),
  val billTitle: String = "Split Bill",
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
  val activeTab: Int = 0,
  val splitMode: SplitMode = SplitMode.ITEMIZED
)

class SplitBillViewModel(application: Application) : AndroidViewModel(application) {
  private val dao = AppDatabase.getDatabase(application).splitBillDao()
  private val _uiState = MutableStateFlow(SplitBillUiState())
  val uiState: StateFlow<SplitBillUiState> = _uiState.asStateFlow()
  val savedBills: StateFlow<List<SplitBillRecord>> = dao.observeBills()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
  private val _isRestored = MutableStateFlow(false)
  val isRestored: StateFlow<Boolean> = _isRestored.asStateFlow()

  init {
    viewModelScope.launch {
      val active = dao.getActiveBill()
      if (active != null) {
        runCatching { decodeState(active) }
          .onSuccess { _uiState.value = it }
      }
      _isRestored.value = true
      if (active == null) persistCurrent()
    }
  }

  fun persistCurrent() {
    if (!_isRestored.value) return
    val state = _uiState.value
    viewModelScope.launch {
      runCatching {
        val existing = dao.getBill(state.billId)
        dao.clearActiveBill()
        dao.upsertBill(
          SplitBillRecord(
            id = state.billId,
            title = state.billTitle,
            currencyCode = state.currencyCode,
            currencySymbol = state.currencySymbol,
            payloadJson = encodeState(state),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
          )
        )
      }
    }
  }

  fun startNewBill() {
    _uiState.value = SplitBillUiState()
    persistCurrent()
  }

  fun openSavedBill(id: String) {
    viewModelScope.launch {
      val record = dao.getBill(id) ?: return@launch
      runCatching { decodeState(record) }.onSuccess {
        _uiState.value = it
        persistCurrent()
      }
    }
  }

  fun deleteSavedBill(id: String) {
    viewModelScope.launch {
      dao.deleteBill(id)
      if (_uiState.value.billId == id) startNewBill()
    }
  }

  fun setTab(index: Int) { _uiState.update { it.copy(activeTab = index.coerceIn(0, 2)) }; persistCurrent() }
  fun setBillTitle(title: String) { _uiState.update { it.copy(billTitle = title.take(80)) }; persistCurrent() }
  fun setCurrency(code: String, symbol: String) { _uiState.update { it.copy(currencyCode = code, currencySymbol = symbol) }; persistCurrent() }
  fun setTaxAmount(tax: Double) { _uiState.update { it.copy(taxAmount = nonNegative(tax)) }; persistCurrent() }
  fun setTipAmount(tip: Double) { _uiState.update { it.copy(tipAmount = nonNegative(tip)) }; persistCurrent() }
  fun setDiscountAmount(discount: Double) { _uiState.update { it.copy(discountAmount = nonNegative(discount)) }; persistCurrent() }
  fun setSplitMode(mode: SplitMode) { _uiState.update { it.copy(splitMode = mode) }; persistCurrent() }
  fun toggleSimplifyDebts() { _uiState.update { it.copy(simplifyDebtsEnabled = !it.simplifyDebtsEnabled) }; persistCurrent() }
  fun setShowOcrDialog(show: Boolean) { _uiState.update { it.copy(showOcrDialog = show) } }
  fun setShowAddParticipantDialog(show: Boolean) { _uiState.update { it.copy(showAddParticipantDialog = show) } }
  fun setShowAddItemDialog(show: Boolean) { _uiState.update { it.copy(showAddItemDialog = show) } }

  fun addParticipant(name: String, upiId: String, paypal: String, paid: Double) {
    val colors = listOf("#4F46E5", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6", "#06B6D4", "#EF4444")
    val state = _uiState.value
    val participant = Participant(
      id = UUID.randomUUID().toString(),
      name = name.trim().ifBlank { "Diner ${state.participants.size + 1}" }.take(50),
      avatarColorHex = colors[state.participants.size % colors.size],
      upiId = upiId.trim(),
      paypalUsername = paypal.trim(),
      paidAmount = nonNegative(paid)
    )
    _uiState.update { it.copy(participants = it.participants + participant) }
    persistCurrent()
  }

  fun removeParticipant(id: String) {
    _uiState.update { state ->
      val remaining = state.participants.filterNot { it.id == id }
      state.copy(
        participants = remaining,
        items = state.items.map { item ->
          item.copy(
            claimedParticipantIds = item.claimedParticipantIds.filterNot { it == id },
            paidByParticipantId = item.paidByParticipantId?.takeUnless { it == id }
          )
        }
      )
    }
    persistCurrent()
  }

  fun updateParticipantPaid(id: String, paid: Double) {
    _uiState.update { state -> state.copy(participants = state.participants.map { if (it.id == id) it.copy(paidAmount = nonNegative(paid)) else it }) }
    persistCurrent()
  }

  fun addItem(title: String, price: Double, quantity: Int, claimedIds: List<String>, paidByParticipantId: String? = null) {
    if (title.isBlank() || !price.isFinite() || price <= 0.0) return
    val item = ReceiptItem(
      id = UUID.randomUUID().toString(),
      title = title.trim().take(80),
      price = price,
      quantity = quantity.coerceIn(1, 999),
      claimedParticipantIds = claimedIds.distinct(),
      paidByParticipantId = paidByParticipantId
    )
    _uiState.update { it.copy(items = it.items + item) }
    persistCurrent()
  }

  fun removeItem(id: String) { _uiState.update { it.copy(items = it.items.filterNot { item -> item.id == id }) }; persistCurrent() }

  fun toggleItemClaim(itemId: String, participantId: String) {
    _uiState.update { state ->
      state.copy(items = state.items.map { item ->
        if (item.id != itemId) item else {
          val current = if (item.claimedParticipantIds.isEmpty()) state.participants.map { it.id } else item.claimedParticipantIds
          val next = if (participantId in current) current - participantId else current + participantId
          item.copy(claimedParticipantIds = next)
        }
      })
    }
    persistCurrent()
  }

  fun claimItemForAll(itemId: String) {
    val allIds = _uiState.value.participants.map { it.id }
    _uiState.update { state -> state.copy(items = state.items.map { if (it.id == itemId) it.copy(claimedParticipantIds = allIds) else it }) }
    persistCurrent()
  }

  fun applyParsedReceipt(parsed: ParsedReceipt) {
    _uiState.update { state ->
      state.copy(
        billTitle = parsed.title.take(80),
        items = parsed.items,
        taxAmount = nonNegative(parsed.tax),
        tipAmount = nonNegative(parsed.tip),
        discountAmount = nonNegative(parsed.discount),
        showOcrDialog = false
      )
    }
    persistCurrent()
  }

  fun loadDemo(receiptText: String, title: String) { applyParsedReceipt(ReceiptOcrParser.parseReceiptText(receiptText, title)) }

  fun toggleSettledTransfer(transferKey: String) {
    _uiState.update { state ->
      val next = if (transferKey in state.settledTransferKeys) state.settledTransferKeys - transferKey else state.settledTransferKeys + transferKey
      state.copy(settledTransferKeys = next)
    }
    persistCurrent()
  }

  private fun encodeState(state: SplitBillUiState): String {
    val root = JSONObject()
      .put("version", 1)
      .put("billId", state.billId)
      .put("billTitle", state.billTitle)
      .put("currencyCode", state.currencyCode)
      .put("currencySymbol", state.currencySymbol)
      .put("taxAmount", state.taxAmount)
      .put("tipAmount", state.tipAmount)
      .put("discountAmount", state.discountAmount)
      .put("simplifyDebtsEnabled", state.simplifyDebtsEnabled)
      .put("activeTab", state.activeTab)
      .put("splitMode", state.splitMode.name)
    val people = JSONArray()
    state.participants.forEach { p ->
      people.put(JSONObject().put("id", p.id).put("name", p.name).put("avatarColorHex", p.avatarColorHex)
        .put("upiId", p.upiId).put("paypalUsername", p.paypalUsername).put("paidAmount", p.paidAmount))
    }
    val items = JSONArray()
    state.items.forEach { item ->
      val claims = JSONArray()
      item.claimedParticipantIds.forEach { claims.put(it) }
      items.put(JSONObject().put("id", item.id).put("title", item.title).put("price", item.price)
        .put("quantity", item.quantity).put("claims", claims).put("paidByParticipantId", item.paidByParticipantId))
    }
    val settled = JSONArray()
    state.settledTransferKeys.forEach { settled.put(it) }
    return root.put("participants", people).put("items", items).put("settledTransferKeys", settled).toString()
  }

  private fun decodeState(record: SplitBillRecord): SplitBillUiState {
    val root = JSONObject(record.payloadJson)
    val peopleJson = root.optJSONArray("participants") ?: JSONArray()
    val people = (0 until peopleJson.length()).mapNotNull { i ->
      val p = peopleJson.optJSONObject(i) ?: return@mapNotNull null
      Participant(
        id = p.optString("id", UUID.randomUUID().toString()),
        name = p.optString("name", "Diner"),
        avatarColorHex = p.optString("avatarColorHex", "#6366F1"),
        upiId = p.optString("upiId", ""),
        paypalUsername = p.optString("paypalUsername", ""),
        paidAmount = nonNegative(p.optDouble("paidAmount", 0.0))
      )
    }.ifEmpty { listOf(Participant(id = "p1", name = "You (Me)")) }
    val itemJson = root.optJSONArray("items") ?: JSONArray()
    val items = (0 until itemJson.length()).mapNotNull { i ->
      val item = itemJson.optJSONObject(i) ?: return@mapNotNull null
      val claimsJson = item.optJSONArray("claims") ?: JSONArray()
      ReceiptItem(
        id = item.optString("id", UUID.randomUUID().toString()),
        title = item.optString("title", "Item"),
        price = nonNegative(item.optDouble("price", 0.0)),
        quantity = item.optInt("quantity", 1).coerceAtLeast(1),
        claimedParticipantIds = (0 until claimsJson.length()).map { claimsJson.optString(it) },
        paidByParticipantId = item.optString("paidByParticipantId").takeIf { it.isNotBlank() && it != "null" }
      )
    }
    val settledJson = root.optJSONArray("settledTransferKeys") ?: JSONArray()
    val settled = (0 until settledJson.length()).map { settledJson.optString(it) }.toSet()
    val mode = runCatching { SplitMode.valueOf(root.optString("splitMode", SplitMode.ITEMIZED.name)) }.getOrDefault(SplitMode.ITEMIZED)
    return SplitBillUiState(
      billId = record.id,
      billTitle = root.optString("billTitle", record.title),
      currencyCode = root.optString("currencyCode", record.currencyCode),
      currencySymbol = root.optString("currencySymbol", record.currencySymbol),
      participants = people,
      items = items,
      taxAmount = nonNegative(root.optDouble("taxAmount", 0.0)),
      tipAmount = nonNegative(root.optDouble("tipAmount", 0.0)),
      discountAmount = nonNegative(root.optDouble("discountAmount", 0.0)),
      simplifyDebtsEnabled = root.optBoolean("simplifyDebtsEnabled", true),
      activeTab = root.optInt("activeTab", 0).coerceIn(0, 2),
      settledTransferKeys = settled,
      splitMode = mode
    )
  }

  private fun nonNegative(value: Double): Double = if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
}
