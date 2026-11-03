package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CalculationHistoryDao
import com.example.data.local.dao.FinanceDao
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.CalculationRecord
import com.example.data.local.entity.ExpenseCategory
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.TripRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
  entities = [
    CalculationRecord::class,
    ExpenseCategory::class,
    ExpenseRecord::class,
    BankAccount::class,
    TripRecord::class
  ],
  version = 2,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun calculationHistoryDao(): CalculationHistoryDao
  abstract fun financeDao(): FinanceDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "omni_calc_db"
        )
          .fallbackToDestructiveMigration(true)
          .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
              super.onCreate(db)
              CoroutineScope(Dispatchers.IO).launch {
                INSTANCE?.let { database ->
                  val defaultCategories = listOf(
                    ExpenseCategory(name = "Food & Dining", colorHex = "#EF4444", iconName = "restaurant"),
                    ExpenseCategory(name = "Shopping", colorHex = "#EC4899", iconName = "shopping_bag"),
                    ExpenseCategory(name = "Fuel & Transport", colorHex = "#F59E0B", iconName = "directions_car"),
                    ExpenseCategory(name = "Bills & Utilities", colorHex = "#3B82F6", iconName = "receipt_long"),
                    ExpenseCategory(name = "Entertainment", colorHex = "#8B5CF6", iconName = "movie"),
                    ExpenseCategory(name = "Health & Medical", colorHex = "#10B981", iconName = "medical_services"),
                    ExpenseCategory(name = "Travel & Trips", colorHex = "#06B6D4", iconName = "flight"),
                    ExpenseCategory(name = "Investments", colorHex = "#4F46E5", iconName = "trending_up")
                  )
                  database.financeDao().insertDefaultCategories(defaultCategories)
                }
              }
            }
          })
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
