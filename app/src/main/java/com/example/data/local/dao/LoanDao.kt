package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LoanRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {

  @Query("SELECT * FROM loans ORDER BY id DESC")
  fun getAllLoans(): Flow<List<LoanRecord>>

  @Query("SELECT * FROM loans WHERE paidEmisCount < tenureMonths ORDER BY id DESC")
  fun getActiveLoans(): Flow<List<LoanRecord>>

  @Query("SELECT * FROM loans WHERE id = :id LIMIT 1")
  suspend fun getLoanById(id: Long): LoanRecord?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLoan(loan: LoanRecord): Long

  @Update
  suspend fun updateLoan(loan: LoanRecord)

  @Delete
  suspend fun deleteLoan(loan: LoanRecord)

  @Query("UPDATE loans SET paidEmisCount = paidEmisCount + 1 WHERE id = :id AND paidEmisCount < tenureMonths")
  suspend fun incrementPaidEmis(id: Long)

  @Query("UPDATE loans SET paidEmisCount = CASE WHEN paidEmisCount > 0 THEN paidEmisCount - 1 ELSE 0 END WHERE id = :id")
  suspend fun decrementPaidEmis(id: Long)

  @Query("SELECT * FROM loans WHERE loanName = :name LIMIT 1")
  suspend fun getLoanByName(name: String): LoanRecord?

  @Query("DELETE FROM loans")
  suspend fun deleteAllLoans()
}
