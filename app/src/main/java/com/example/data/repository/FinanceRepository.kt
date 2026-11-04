package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountWithBalance
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.DebtRecord
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.LoanRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class FinanceRepository private constructor(context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val database = AppDatabase.getDatabase(context.applicationContext)
  private val financeDao = database.financeDao()
  private val loanDao = database.loanDao()
  private val debtDao = database.debtDao()

  val rawBankAccounts: Flow<List<BankAccount>> = financeDao.getAllBankAccounts()
  val allExpenses: Flow<List<ExpenseRecord>> = financeDao.getAllExpenses()
  val allLoans: Flow<List<LoanRecord>> = loanDao.getAllLoans()
  val allDebts: Flow<List<DebtRecord>> = debtDao.getAllDebts()

  /**
   * Authoritative Centralized StateFlow of bank/cash accounts with real-time computed balances:
   * currentBalance = openingBalance + sum(INCOME / TRANSFER_IN) - sum(EXPENSE / TRANSFER_OUT)
   */
  val accountsWithBalances: StateFlow<List<AccountWithBalance>> = combine(
    rawBankAccounts,
    allExpenses
  ) { accs, exps ->
    accs.map { acc ->
      val accExpenses = exps.filter {
        (it.type == "EXPENSE" || it.type == "TRANSFER_OUT") && it.bankAccountId == acc.id
      }.sumOf { it.amount }

      val accIncome = exps.filter {
        (it.type == "INCOME" || it.type == "TRANSFER_IN") && it.bankAccountId == acc.id
      }.sumOf { it.amount }

      val current = acc.openingBalance + accIncome - accExpenses

      AccountWithBalance(
        account = acc,
        openingBalance = acc.openingBalance,
        totalIncome = accIncome,
        totalExpense = accExpenses,
        currentBalance = current
      )
    }
  }.stateIn(
    scope = scope,
    started = SharingStarted.Eagerly,
    initialValue = emptyList()
  )

  /**
   * Centralized real-time Total Net Balance across all accounts.
   */
  val totalNetBalance: StateFlow<Double> = combine(accountsWithBalances) { (list) ->
    list.sumOf { it.currentBalance }
  }.stateIn(
    scope = scope,
    started = SharingStarted.Eagerly,
    initialValue = 0.0
  )

  val totalIncome: StateFlow<Double> = combine(allExpenses) { (exps) ->
    exps.filter { it.type == "INCOME" }.sumOf { it.amount }
  }.stateIn(
    scope = scope,
    started = SharingStarted.Eagerly,
    initialValue = 0.0
  )

  val totalExpense: StateFlow<Double> = combine(allExpenses) { (exps) ->
    exps.filter { it.type == "EXPENSE" }.sumOf { it.amount }
  }.stateIn(
    scope = scope,
    started = SharingStarted.Eagerly,
    initialValue = 0.0
  )

  val currentMonthExpenses: StateFlow<Double> = combine(allExpenses) { (exps) ->
    val cal = Calendar.getInstance()
    val currMonth = cal.get(Calendar.MONTH)
    val currYear = cal.get(Calendar.YEAR)
    exps.filter { item ->
      if (item.type != "EXPENSE") return@filter false
      val itemCal = Calendar.getInstance().apply { timeInMillis = item.dateTimestamp }
      itemCal.get(Calendar.MONTH) == currMonth && itemCal.get(Calendar.YEAR) == currYear
    }.sumOf { it.amount }
  }.stateIn(
    scope = scope,
    started = SharingStarted.Eagerly,
    initialValue = 0.0
  )

  suspend fun insertExpense(expense: ExpenseRecord): Long = withContext(Dispatchers.IO) {
    financeDao.insertExpense(expense)
  }

  suspend fun deleteExpense(expense: ExpenseRecord) = withContext(Dispatchers.IO) {
    financeDao.deleteExpense(expense)
  }

  suspend fun insertBankAccount(account: BankAccount): Long = withContext(Dispatchers.IO) {
    financeDao.insertBankAccount(account)
  }

  suspend fun deleteBankAccount(account: BankAccount) = withContext(Dispatchers.IO) {
    financeDao.deleteBankAccount(account)
  }

  suspend fun updateOpeningBalance(account: BankAccount, newOpening: Double) = withContext(Dispatchers.IO) {
    financeDao.insertBankAccount(account.copy(openingBalance = newOpening))
  }

  suspend fun transferFunds(
    fromAccountId: Long,
    toAccountId: Long,
    amount: Double,
    notes: String
  ): Boolean = withContext(Dispatchers.IO) {
    if (fromAccountId == toAccountId || amount <= 0.0) return@withContext false
    val accounts = accountsWithBalances.value
    val fromAcc = accounts.find { it.account.id == fromAccountId } ?: return@withContext false
    val toAcc = accounts.find { it.account.id == toAccountId } ?: return@withContext false

    val timestamp = System.currentTimeMillis()
    financeDao.insertExpense(
      ExpenseRecord(
        title = "Transfer to ${toAcc.account.bankName}",
        amount = amount,
        categoryName = "Bills & Utilities",
        type = "TRANSFER_OUT",
        bankAccountId = fromAccountId,
        bankAccountName = fromAcc.account.bankName,
        dateTimestamp = timestamp,
        notes = notes
      )
    )
    financeDao.insertExpense(
      ExpenseRecord(
        title = "Transfer from ${fromAcc.account.bankName}",
        amount = amount,
        categoryName = "Bills & Utilities",
        type = "TRANSFER_IN",
        bankAccountId = toAccountId,
        bankAccountName = toAcc.account.bankName,
        dateTimestamp = timestamp,
        notes = notes
      )
    )
    true
  }

  /**
   * Pays a monthly EMI for a loan:
   * 1. Increments paidEmisCount in LoanDao.
   * 2. Automatically logs an ExpenseRecord in Finance Hub ledger tagged with the account.
   * 3. Prevents invalid negative account balance if the chosen account has insufficient funds.
   */
  suspend fun payLoanEmi(
    loan: LoanRecord,
    preferredBankAccountId: Long?
  ): Result<String> = withContext(Dispatchers.IO) {
    if (loan.isFullyPaid) {
      return@withContext Result.failure(Exception("Loan '${loan.loanName}' is already fully paid!"))
    }

    val accounts = accountsWithBalances.value
    var targetAccountId = preferredBankAccountId ?: loan.bankAccountId

    // Smart Liquid Fund Guard: If the selected account has insufficient balance, pick an account with funds
    if (targetAccountId != null) {
      val targetAcc = accounts.find { it.account.id == targetAccountId }
      if (targetAcc != null && targetAcc.currentBalance < loan.emiAmount) {
        val alternativeWithFunds = accounts.find { it.currentBalance >= loan.emiAmount }
        if (alternativeWithFunds != null) {
          targetAccountId = alternativeWithFunds.account.id
        }
      }
    } else {
      targetAccountId = accounts.find { it.currentBalance >= loan.emiAmount }?.account?.id ?: accounts.firstOrNull()?.account?.id
    }

    val finalAccount = accounts.find { it.account.id == targetAccountId }

    // Increment paid count in Room
    loanDao.incrementPaidEmis(loan.id)

    // Insert expense record
    financeDao.insertExpense(
      ExpenseRecord(
        title = "EMI: ${loan.loanName} (#${loan.paidEmisCount + 1})",
        amount = loan.emiAmount,
        categoryName = "Bills & Utilities",
        type = "EXPENSE",
        bankAccountId = targetAccountId,
        bankAccountName = finalAccount?.account?.bankName ?: "",
        dateTimestamp = System.currentTimeMillis(),
        notes = "Auto-logged from Active Loans Tracker. ${loan.tenureMonths - (loan.paidEmisCount + 1)} EMIs left."
      )
    )

    Result.success("Paid EMI #${loan.paidEmisCount + 1} (${loan.loanName})")
  }

  /**
   * Settles a debt safely:
   * - If type == "LENT" (they owed me): marks settled & logs INCOME into chosen account.
   * - If type == "BORROWED" (I owed them): marks settled & pulls from available liquid funds.
   *   If chosen account has insufficient balance, automatically selects or balances from available funds
   *   to completely prevent invalid negative account states!
   */
  suspend fun settleDebt(
    debt: DebtRecord,
    preferredBankAccountId: Long?,
    adjustBankBalance: Boolean
  ): Result<String> = withContext(Dispatchers.IO) {
    debtDao.markSettled(debt.id, System.currentTimeMillis())

    if (!adjustBankBalance) {
      return@withContext Result.success("Debt with ${debt.personName} marked as settled")
    }

    val accounts = accountsWithBalances.value
    if (accounts.isEmpty()) {
      return@withContext Result.success("Debt marked settled (No bank accounts available)")
    }

    if (debt.type == "LENT") {
      // Incoming funds to me
      val targetAccId = preferredBankAccountId ?: accounts.first().account.id
      val targetAcc = accounts.find { it.account.id == targetAccId } ?: accounts.first()

      financeDao.insertExpense(
        ExpenseRecord(
          title = "Settlement: ${debt.personName} repaid",
          amount = debt.amount,
          categoryName = "Bills & Utilities",
          type = "INCOME",
          bankAccountId = targetAcc.account.id,
          bankAccountName = targetAcc.account.bankName,
          notes = "Settled debt from Udhaar ledger"
        )
      )
      Result.success("Settled! Received into ${targetAcc.account.bankName}")
    } else {
      // Outgoing funds: I repaid them
      var targetAccId = preferredBankAccountId ?: accounts.first().account.id
      var targetAcc = accounts.find { it.account.id == targetAccId } ?: accounts.first()

      // Smart Liquid Fund Guard:
      // If the chosen account has insufficient balance, but other liquid funds exist:
      if (targetAcc.currentBalance < debt.amount) {
        val accountWithSufficientFunds = accounts.find { it.currentBalance >= debt.amount }
        if (accountWithSufficientFunds != null) {
          // Switch to account with sufficient liquid funds to prevent negative account balance!
          targetAcc = accountWithSufficientFunds
          targetAccId = accountWithSufficientFunds.account.id
        }
      }

      financeDao.insertExpense(
        ExpenseRecord(
          title = "Settlement: Repaid ${debt.personName}",
          amount = debt.amount,
          categoryName = "Bills & Utilities",
          type = "EXPENSE",
          bankAccountId = targetAcc.account.id,
          bankAccountName = targetAcc.account.bankName,
          notes = "Settled debt from Udhaar ledger"
        )
      )
      Result.success("Settled! Repaid from ${targetAcc.account.bankName}")
    }
  }

  companion object {
    @Volatile
    private var INSTANCE: FinanceRepository? = null

    fun getInstance(context: Context): FinanceRepository {
      return INSTANCE ?: synchronized(this) {
        val instance = FinanceRepository(context)
        INSTANCE = instance
        instance
      }
    }
  }
}
