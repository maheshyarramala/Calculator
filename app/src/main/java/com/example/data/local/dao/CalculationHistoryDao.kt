package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CalculationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculationHistoryDao {
  @Query("SELECT * FROM calculation_history ORDER BY timestamp DESC")
  fun getAll(): Flow<List<CalculationRecord>>

  @Query("SELECT * FROM calculation_history WHERE category = :category ORDER BY timestamp DESC")
  fun getByCategory(category: String): Flow<List<CalculationRecord>>

  @Query("SELECT * FROM calculation_history WHERE isFavorite = 1 ORDER BY timestamp DESC")
  fun getFavorites(): Flow<List<CalculationRecord>>

  @Query("UPDATE calculation_history SET isFavorite = :isFav WHERE id = :id")
  suspend fun updateFavorite(id: Long, isFav: Boolean)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(record: CalculationRecord): Long

  @Delete
  suspend fun delete(record: CalculationRecord)

  @Query("DELETE FROM calculation_history WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM calculation_history")
  suspend fun clearAll()
}
