package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A locally stored Split Bill snapshot. The payload is versioned JSON so new split
 * options can be added without destructive Room migrations for every field.
 */
@Entity(tableName = "split_bills")
data class SplitBillRecord(
  @PrimaryKey val id: String,
  val title: String,
  val currencyCode: String,
  val currencySymbol: String,
  val payloadJson: String,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val isActive: Boolean = false
)
