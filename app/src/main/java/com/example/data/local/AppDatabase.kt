package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CalculationHistoryDao
import com.example.data.local.dao.FinanceDao
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.CalculationRecord
import com.example.data.local.entity.CreditCardBillReminder
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
    CreditCardBillReminder::class,
    TripRecord::class,
    com.example.data.local.entity.SubscriptionRecord::class
  ],
  version = 6,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun calculationHistoryDao(): CalculationHistoryDao
  abstract fun financeDao(): FinanceDao

  companion object {
    private val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN dueMonth INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN dueDayOfWeek INTEGER NOT NULL DEFAULT 1")
      }
    }
    private val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS credit_card_bill_reminders (" +
            "accountId INTEGER NOT NULL PRIMARY KEY, " +
            "amount REAL NOT NULL, " +
            "dueDateEpochDay INTEGER NOT NULL, " +
            "paidDateEpochDay INTEGER NOT NULL DEFAULT -1, " +
            "notificationsEnabled INTEGER NOT NULL DEFAULT 1)"
        )
      }
    }

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "omni_calc_db"
        )
          .addMigrations(MIGRATION_4_5)
          .addMigrations(MIGRATION_5_6)
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

                  val defaultAccounts = listOf(
                    BankAccount(bankName = "Primary Bank", accountType = "Savings", openingBalance = 0.0),
                    BankAccount(bankName = "Cash Wallet", accountType = "Cash", openingBalance = 0.0)
                  )
                  database.financeDao().insertBankAccounts(defaultAccounts)
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
