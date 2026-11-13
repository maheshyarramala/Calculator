package com.example.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "expense_categories")
data class ExpenseCategory(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val name: String,
  val colorHex: String,
  val iconName: String,
  val isCustom: Boolean = false
)

@Immutable
@Entity(tableName = "expenses")
data class ExpenseRecord(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val amount: Double,
  val categoryName: String,
  @ColumnInfo(defaultValue = "'EXPENSE'")
  val type: String = "EXPENSE", // "EXPENSE" or "INCOME"
  val bankAccountId: Long? = null,
  @ColumnInfo(defaultValue = "''")
  val bankAccountName: String = "",
  val dateTimestamp: Long = System.currentTimeMillis(),
  val notes: String = "",
  val tripId: Long? = null,
  val loanId: Long? = null,
  val debtId: Long? = null
)

@Immutable
@Entity(tableName = "bank_accounts")
data class BankAccount(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val bankName: String,
  val accountType: String, // Bank, Savings, Current, Cash, Wallet, Credit Card
  val accountNumber: String = "",
  val ifscOrRouting: String = "",
  val holderName: String = "",
  @ColumnInfo(defaultValue = "0.0")
  val openingBalance: Double = 0.0,
  val balance: Double = 0.0,
  val notes: String = ""
)

@Immutable
@Entity(tableName = "credit_card_bill_reminders")
data class CreditCardBillReminder(
  @PrimaryKey
  val accountId: Long,
  val amount: Double,
  val dueDateEpochDay: Long,
  @ColumnInfo(defaultValue = "-1")
  val paidDateEpochDay: Long = -1L,
  @ColumnInfo(defaultValue = "1")
  val notificationsEnabled: Boolean = true
)

@Immutable
@Entity(tableName = "trips")
data class TripRecord(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val tripName: String,
  val destination: String,
  val budget: Double,
  val startDate: String,
  val endDate: String,
  val notes: String = "",
  @ColumnInfo(defaultValue = "'{}'")
  val categoryCapsJson: String = "{}"
)

@Immutable
@Entity(tableName = "subscriptions")
data class SubscriptionRecord(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val name: String,
  val amount: Double,
  val billingCycle: String = "MONTHLY", // MONTHLY, YEARLY, WEEKLY
  val dueDayOfMonth: Int = 1, // 1-31
  @ColumnInfo(defaultValue = "1")
  val dueMonth: Int = 1, // 1-12, used for yearly billing
  @ColumnInfo(defaultValue = "1")
  val dueDayOfWeek: Int = 1, // 1-7, Monday-Sunday, used for weekly billing
  val categoryName: String = "Bills & Utilities",
  val bankAccountId: Long? = null,
  val bankAccountName: String = "",
  val lastPaidTimestamp: Long = 0L,
  val notes: String = ""
)

@Immutable
data class AccountWithBalance(
  val account: BankAccount,
  val openingBalance: Double,
  val totalIncome: Double,
  val totalExpense: Double,
  val currentBalance: Double
)
