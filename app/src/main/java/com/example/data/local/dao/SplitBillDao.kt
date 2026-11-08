package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SplitBillRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitBillDao {
  @Query("SELECT * FROM split_bills ORDER BY updatedAt DESC")
  fun observeBills(): Flow<List<SplitBillRecord>>

  @Query("SELECT * FROM split_bills WHERE id = :id LIMIT 1")
  suspend fun getBill(id: String): SplitBillRecord?

  @Query("SELECT * FROM split_bills WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
  suspend fun getActiveBill(): SplitBillRecord?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBill(bill: SplitBillRecord)

  @Query("UPDATE split_bills SET isActive = 0")
  suspend fun clearActiveBill()

  @Query("DELETE FROM split_bills WHERE id = :id")
  suspend fun deleteBill(id: String)
}
