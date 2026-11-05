package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.CreditCardBillReminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditCardBillReminderDialog(
  cardAccounts: List<BankAccount>,
  reminders: List<CreditCardBillReminder>,
  editingAccountId: Long?,
  onDismiss: () -> Unit,
  onSave: (CreditCardBillReminder) -> Unit
) {
  val today = LocalDate.now()
  var selectedAccountId by remember(editingAccountId, cardAccounts) {
    mutableStateOf(
      editingAccountId ?: cardAccounts.firstOrNull { account ->
        reminders.none { it.accountId == account.id }
      }?.id ?: cardAccounts.firstOrNull()?.id
    )
  }
  var amount by remember(editingAccountId) {
    mutableStateOf(reminders.firstOrNull { it.accountId == editingAccountId }?.amount?.toString().orEmpty())
  }
  var selectedDueDate by remember(editingAccountId) {
    mutableStateOf(
      reminders.firstOrNull { it.accountId == editingAccountId }?.dueDateEpochDay
        ?: today.plusDays(7).toEpochDay()
    )
  }
  var accountMenuExpanded by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }
  val initialPickerDate = reminders.firstOrNull { it.accountId == selectedAccountId }
    ?.dueDateEpochDay ?: today.plusDays(7).toEpochDay()
  val datePickerState = key(selectedAccountId, reminders) {
    rememberDatePickerState(
      initialSelectedDateMillis = LocalDate.ofEpochDay(initialPickerDate)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
    )
  }
  val selectedAccount = cardAccounts.firstOrNull { it.id == selectedAccountId }
  val enteredAmount = amount.toDoubleOrNull()
  val isDueDateValid = selectedDueDate >= today.toEpochDay()

  LaunchedEffect(selectedAccountId, reminders) {
    val existing = reminders.firstOrNull { it.accountId == selectedAccountId }
    amount = existing?.amount?.toString().orEmpty()
    selectedDueDate = existing?.dueDateEpochDay ?: today.plusDays(7).toEpochDay()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.card_bill_due_alerts_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
          OutlinedButton(
            onClick = { accountMenuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = editingAccountId == null
          ) {
            Text(
              selectedAccount?.bankName ?: stringResource(R.string.card_bill_select_account),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          DropdownMenu(
            expanded = accountMenuExpanded,
            onDismissRequest = { accountMenuExpanded = false }
          ) {
            cardAccounts.forEach { account ->
              DropdownMenuItem(
                text = { Text(account.bankName) },
                onClick = {
                  selectedAccountId = account.id
                  accountMenuExpanded = false
                }
              )
            }
          }
        }

        OutlinedTextField(
          value = amount,
          onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) amount = input
          },
          label = { Text(stringResource(R.string.card_bill_amount)) },
          prefix = { Text("₹") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
          onClick = { showDatePicker = true },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            stringResource(
              R.string.card_bill_due_date_value,
              LocalDate.ofEpochDay(selectedDueDate).format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
              )
            )
          )
        }

        Text(
          stringResource(R.string.card_bill_notification_schedule),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!isDueDateValid) {
          Text(
            stringResource(R.string.card_bill_due_date_must_be_future),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (selectedAccount != null && enteredAmount != null && enteredAmount > 0.0 && isDueDateValid) {
            onSave(
              CreditCardBillReminder(
                accountId = selectedAccount.id,
                amount = enteredAmount,
                dueDateEpochDay = selectedDueDate
              )
            )
          }
        },
        enabled = selectedAccount != null && enteredAmount != null && enteredAmount > 0.0 && isDueDateValid
      ) {
        Text(stringResource(R.string.card_bill_save_reminder))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
    }
  )

  if (showDatePicker) {
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { dateMillis ->
              selectedDueDate = Instant.ofEpochMilli(dateMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .toEpochDay()
            }
            showDatePicker = false
          }
        ) { Text(stringResource(R.string.common_confirm)) }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text(stringResource(R.string.common_cancel))
        }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }
}
