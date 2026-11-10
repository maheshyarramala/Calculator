package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CalculationHistoryDao
import com.example.data.local.dao.DebtDao
import com.example.data.local.dao.FinanceDao
import com.example.data.local.dao.LoanDao
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.CalculationRecord
import com.example.data.local.entity.CreditCardBillReminder
import com.example.data.local.entity.DebtRecord
import com.example.data.local.entity.ExpenseCategory
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.LoanRecord
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
    com.example.data.local.entity.SubscriptionRecord::class,
    LoanRecord::class,
    DebtRecord::class
  ],
  version = 9,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun calculationHistoryDao(): CalculationHistoryDao
  abstract fun financeDao(): FinanceDao
  abstract fun loanDao(): LoanDao
  abstract fun debtDao(): DebtDao

  companion object {

    // Historical migrations reconstructed from the original v1-v4 Room entity definitions.
    // Keeping this chain explicit lets existing installations upgrade without destructive fallback.
    private val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS expense_categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, colorHex TEXT NOT NULL, iconName TEXT NOT NULL, isCustom INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amount REAL NOT NULL, categoryName TEXT NOT NULL, dateTimestamp INTEGER NOT NULL, notes TEXT NOT NULL, tripId INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bank_accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bankName TEXT NOT NULL, accountType TEXT NOT NULL, accountNumber TEXT NOT NULL, ifscOrRouting TEXT NOT NULL, holderName TEXT NOT NULL, balance REAL NOT NULL, notes TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS trips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripName TEXT NOT NULL, destination TEXT NOT NULL, budget REAL NOT NULL, startDate TEXT NOT NULL, endDate TEXT NOT NULL, notes TEXT NOT NULL)")
      }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
        db.execSQL("ALTER TABLE expenses ADD COLUMN bankAccountId INTEGER")
        db.execSQL("ALTER TABLE expenses ADD COLUMN bankAccountName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_accounts ADD COLUMN openingBalance REAL NOT NULL DEFAULT 0.0")
      }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trips ADD COLUMN categoryCapsJson TEXT NOT NULL DEFAULT '{}'")
        db.execSQL("CREATE TABLE IF NOT EXISTS subscriptions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, amount REAL NOT NULL, billingCycle TEXT NOT NULL, dueDayOfMonth INTEGER NOT NULL, categoryName TEXT NOT NULL, bankAccountId INTEGER, bankAccountName TEXT NOT NULL, lastPaidTimestamp INTEGER NOT NULL, notes TEXT NOT NULL)")
      }
    }
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
    private val MIGRATION_6_7 = object : Migration(6, 7) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS loans (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "loanName TEXT NOT NULL, " +
            "loanCategory TEXT NOT NULL, " +
            "principalAmount REAL NOT NULL, " +
            "annualInterestRate REAL NOT NULL, " +
            "tenureMonths INTEGER NOT NULL, " +
            "emiAmount REAL NOT NULL, " +
            "startDate TEXT NOT NULL, " +
            "paidEmisCount INTEGER NOT NULL DEFAULT 0, " +
            "notes TEXT NOT NULL DEFAULT '', " +
            "bankAccountId INTEGER, " +
            "bankAccountName TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS debts (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "personName TEXT NOT NULL, " +
            "amount REAL NOT NULL, " +
            "type TEXT NOT NULL, " +
            "dateTimestamp INTEGER NOT NULL, " +
            "dueDateTimestamp INTEGER, " +
            "isSettled INTEGER NOT NULL DEFAULT 0, " +
            "settledDateTimestamp INTEGER, " +
            "notes TEXT NOT NULL DEFAULT '', " +
            "bankAccountId INTEGER, " +
            "bankAccountName TEXT NOT NULL DEFAULT '')"
        )
      }
    }
    private val MIGRATION_7_8 = object : Migration(7, 8) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN loanId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE expenses ADD COLUMN debtId INTEGER DEFAULT NULL")
      }
    }
    private val MIGRATION_8_9 = object : Migration(8, 9) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE loans ADD COLUMN dueDayOfMonth INTEGER NOT NULL DEFAULT 1")
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
          .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
          .addMigrations(MIGRATION_4_5)
          .addMigrations(MIGRATION_5_6)
          .addMigrations(MIGRATION_6_7)
          .addMigrations(MIGRATION_7_8)
          .addMigrations(MIGRATION_8_9)
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
