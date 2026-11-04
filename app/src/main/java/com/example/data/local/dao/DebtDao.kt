package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.DebtRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {

  @Query("SELECT * FROM debts ORDER BY dateTimestamp DESC")
  fun getAllDebts(): Flow<List<DebtRecord>>

  @Query("SELECT * FROM debts WHERE isSettled = 0 ORDER BY dateTimestamp DESC")
  fun getUnsettledDebts(): Flow<List<DebtRecord>>

  @Query("SELECT * FROM debts WHERE type = :type AND isSettled = 0 ORDER BY dateTimestamp DESC")
  fun getUnsettledDebtsByType(type: String): Flow<List<DebtRecord>>

  @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
  suspend fun getDebtById(id: Long): DebtRecord?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDebt(debt: DebtRecord): Long

  @Update
  suspend fun updateDebt(debt: DebtRecord)

  @Delete
  suspend fun deleteDebt(debt: DebtRecord)

  @Query("UPDATE debts SET isSettled = 1, settledDateTimestamp = :settledTimestamp WHERE id = :id")
  suspend fun markSettled(id: Long, settledTimestamp: Long = System.currentTimeMillis())

  @Query("UPDATE debts SET isSettled = 0, settledDateTimestamp = NULL WHERE id = :id")
  suspend fun unmarkSettled(id: Long)

  @Query("DELETE FROM debts")
  suspend fun deleteAllDebts()
}
