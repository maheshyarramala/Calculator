package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.CreditCardBillReminder
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

  @Query("DELETE FROM expense_categories")
  suspend fun deleteAllCategories()

  // --- Expenses ---
  @Query("SELECT * FROM expenses ORDER BY dateTimestamp DESC")
  fun getAllExpenses(): Flow<List<ExpenseRecord>>

  @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY dateTimestamp DESC")
  fun getExpensesForTrip(tripId: Long): Flow<List<ExpenseRecord>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertExpense(expense: ExpenseRecord): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertExpenses(expenses: List<ExpenseRecord>)

  @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
  suspend fun getExpenseById(id: Long): ExpenseRecord?

  @Query("SELECT * FROM expenses WHERE loanId = :loanId ORDER BY dateTimestamp DESC")
  suspend fun getExpensesByLoanId(loanId: Long): List<ExpenseRecord>

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

  @Query("SELECT * FROM credit_card_bill_reminders ORDER BY dueDateEpochDay ASC")
  fun getAllCreditCardBillReminders(): Flow<List<CreditCardBillReminder>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCreditCardBillReminder(reminder: CreditCardBillReminder)

  @Query("UPDATE credit_card_bill_reminders SET paidDateEpochDay = :paidDateEpochDay WHERE accountId = :accountId")
  suspend fun markCreditCardBillPaid(accountId: Long, paidDateEpochDay: Long)

  @Query("DELETE FROM credit_card_bill_reminders WHERE accountId = :accountId")
  suspend fun deleteCreditCardBillReminder(accountId: Long)

  @Query("DELETE FROM credit_card_bill_reminders")
  suspend fun deleteAllCreditCardBillReminders()

  @Query("SELECT * FROM trips ORDER BY id DESC")
  fun getAllTrips(): Flow<List<TripRecord>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTrip(trip: TripRecord): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTrips(trips: List<TripRecord>)

  @Update
  suspend fun updateTrip(trip: TripRecord)

  @Delete
  suspend fun deleteTrip(trip: TripRecord)

  @Query("DELETE FROM trips")
  suspend fun deleteAllTrips()

  // --- Subscriptions & Recurring Bills ---
  @Query("SELECT * FROM subscriptions ORDER BY dueDayOfMonth ASC")
  fun getAllSubscriptions(): Flow<List<com.example.data.local.entity.SubscriptionRecord>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSubscription(sub: com.example.data.local.entity.SubscriptionRecord): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSubscriptions(subs: List<com.example.data.local.entity.SubscriptionRecord>)

  @Delete
  suspend fun deleteSubscription(sub: com.example.data.local.entity.SubscriptionRecord)

  @Query("DELETE FROM subscriptions")
  suspend fun deleteAllSubscriptions()
}
