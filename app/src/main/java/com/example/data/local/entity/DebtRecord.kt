package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtRecord(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val personName: String,
  val amount: Double,
  val type: String = "LENT", // "LENT" (I gave money) or "BORROWED" (I took money)
  val dateTimestamp: Long = System.currentTimeMillis(),
  val dueDateTimestamp: Long? = null,
  val isSettled: Boolean = false,
  val settledDateTimestamp: Long? = null,
  val notes: String = "",
  val bankAccountId: Long? = null,
  val bankAccountName: String = ""
) {
  val isOverdue: Boolean
    get() = !isSettled && dueDateTimestamp != null && dueDateTimestamp < System.currentTimeMillis()
}
