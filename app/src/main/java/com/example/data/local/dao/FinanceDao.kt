package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.ExpenseCategory
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.TripRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {

  // --- Categories ---
  @Query("SELECT * FROM expense_categories ORDER BY id ASC")
  fun getAllCategories(): Flow<List<ExpenseCategory>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCategory(category: ExpenseCategory): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertDefaultCategories(categories: List<ExpenseCategory>)

  @Delete
  suspend fun deleteCategory(category: ExpenseCategory)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCategories(categories: List<ExpenseCategory>)

  @Query("DELETE FROM expense_categories WHERE isCustom = 1")
  suspend fun deleteAllCustomCategories()

  // --- Expenses ---
  @Query("SELECT * FROM expenses ORDER BY dateTimestamp DESC")
  fun getAllExpenses(): Flow<List<ExpenseRecord>>

  @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY dateTimestamp DESC")
  fun getExpensesForTrip(tripId: Long): Flow<List<ExpenseRecord>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertExpense(expense: ExpenseRecord): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertExpenses(expenses: List<ExpenseRecord>)

  @Delete
  suspend fun deleteExpense(expense: ExpenseRecord)

  @Query("DELETE FROM expenses")
  suspend fun deleteAllExpenses()

  // --- Bank Accounts ---
  @Query("SELECT * FROM bank_accounts ORDER BY id ASC")
  fun getAllBankAccounts(): Flow<List<BankAccount>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBankAccount(account: BankAccount): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBankAccounts(accounts: List<BankAccount>)

  @Delete
  suspend fun deleteBankAccount(account: BankAccount)

  @Query("DELETE FROM bank_accounts")
  suspend fun deleteAllBankAccounts()

  // --- Trips ---
  @Query("SELECT * FROM trips ORDER BY id DESC")
  fun getAllTrips(): Flow<List<TripRecord>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTrip(trip: TripRecord): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTrips(trips: List<TripRecord>)

  @Delete
  suspend fun deleteTrip(trip: TripRecord)

  @Query("DELETE FROM trips")
  suspend fun deleteAllTrips()
}
