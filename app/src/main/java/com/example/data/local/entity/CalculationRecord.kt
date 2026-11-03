package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calculation_history")
data class CalculationRecord(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val category: String, // EMI, SIP, LUMPSUM, COMPOUND_INTEREST, PPF, FD, CALCULATOR, TIP, GST, UNIT
  val title: String,
  val summary: String,
  val detailsText: String,
  val timestamp: Long = System.currentTimeMillis(),
  val isFavorite: Boolean = false
)
