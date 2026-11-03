package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_categories")
data class ExpenseCategory(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val name: String,
  val colorHex: String,
  val iconName: String,
  val isCustom: Boolean = false
)

@Entity(tableName = "expenses")
data class ExpenseRecord(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val amount: Double,
  val categoryName: String,
  val dateTimestamp: Long = System.currentTimeMillis(),
  val notes: String = "",
  val tripId: Long? = null
)

@Entity(tableName = "bank_accounts")
data class BankAccount(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val bankName: String,
  val accountType: String, // Savings, Current, Salary, Credit Card, Fixed Deposit
  val accountNumber: String,
  val ifscOrRouting: String,
  val holderName: String,
  val balance: Double = 0.0,
  val notes: String = ""
)

@Entity(tableName = "trips")
data class TripRecord(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val tripName: String,
  val destination: String,
  val budget: Double,
  val startDate: String,
  val endDate: String,
  val notes: String = ""
)
