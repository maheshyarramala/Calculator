package com.example.domain.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.CreditCardBillReminder
import com.example.data.local.entity.DebtRecord
import com.example.data.local.entity.ExpenseCategory
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.LoanRecord
import com.example.data.local.entity.SubscriptionRecord
import com.example.data.local.entity.TripRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FinanceBackupPayload(
  val categories: List<ExpenseCategory>,
  val bankAccounts: List<BankAccount>,
  val trips: List<TripRecord>,
  val expenses: List<ExpenseRecord>,
  val loans: List<LoanRecord> = emptyList(),
  val debts: List<DebtRecord> = emptyList(),
  val subscriptions: List<SubscriptionRecord> = emptyList(),
  val creditCardReminders: List<CreditCardBillReminder> = emptyList(),
  val creditCardReminderAccountNames: Map<Long, String> = emptyMap(),
  val monthlyBudgetLimit: Double? = null,
  val userUpiId: String? = null
)

sealed class BackupParseResult {
  data class Success(val payload: FinanceBackupPayload, val summary: String) : BackupParseResult()
  data class Error(val message: String) : BackupParseResult()
}

object FinanceBackupManager {

  private const val BACKUP_VERSION = 3
  private const val APP_IDENTIFIER = "OmniCalc"

  fun exportToJson(
    categories: List<ExpenseCategory>,
    bankAccounts: List<BankAccount>,
    trips: List<TripRecord>,
    expenses: List<ExpenseRecord> = emptyList(),
    loans: List<LoanRecord> = emptyList(),
    debts: List<DebtRecord> = emptyList(),
    subscriptions: List<SubscriptionRecord> = emptyList(),
    creditCardReminders: List<CreditCardBillReminder> = emptyList(),
    monthlyBudgetLimit: Double? = null,
    userUpiId: String? = null
  ): String {
    val root = JSONObject()
    root.put("version", BACKUP_VERSION)
    root.put("app", APP_IDENTIFIER)
    root.put("exportedAt", System.currentTimeMillis())
    root.put("exportedAtFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
    if (monthlyBudgetLimit != null) root.put("monthlyBudgetLimit", monthlyBudgetLimit)
    if (userUpiId != null) root.put("userUpiId", userUpiId)

    // Categories
    val catArray = JSONArray()
    categories.forEach { cat ->
      val obj = JSONObject()
      obj.put("id", cat.id)
      obj.put("name", cat.name)
      obj.put("colorHex", cat.colorHex)
      obj.put("iconName", cat.iconName)
      obj.put("isCustom", cat.isCustom)
      catArray.put(obj)
    }
    root.put("categories", catArray)

    // Bank Accounts
    val bankArray = JSONArray()
    bankAccounts.forEach { acc ->
      val obj = JSONObject()
      obj.put("id", acc.id)
      obj.put("bankName", acc.bankName)
      obj.put("accountType", acc.accountType)
      obj.put("accountNumber", acc.accountNumber)
      obj.put("ifscOrRouting", acc.ifscOrRouting)
      obj.put("holderName", acc.holderName)
      obj.put("openingBalance", acc.openingBalance)
      obj.put("balance", acc.balance)
      obj.put("notes", acc.notes)
      bankArray.put(obj)
    }
    root.put("bankAccounts", bankArray)

    // Trips
    val tripArray = JSONArray()
    trips.forEach { trip ->
      val obj = JSONObject()
      obj.put("id", trip.id)
      obj.put("tripName", trip.tripName)
      obj.put("destination", trip.destination)
      obj.put("budget", trip.budget)
      obj.put("startDate", trip.startDate)
      obj.put("endDate", trip.endDate)
      obj.put("notes", trip.notes)
      obj.put("categoryCapsJson", trip.categoryCapsJson)
      tripArray.put(obj)
    }
    root.put("trips", tripArray)

    // Expenses
    val expArray = JSONArray()
    expenses.forEach { exp ->
      val obj = JSONObject()
      obj.put("id", exp.id)
      obj.put("title", exp.title)
      obj.put("amount", exp.amount)
      obj.put("categoryName", exp.categoryName)
      obj.put("type", exp.type)
      obj.put("bankAccountId", exp.bankAccountId ?: -1L)
      obj.put("bankAccountName", exp.bankAccountName)
      obj.put("dateTimestamp", exp.dateTimestamp)
      obj.put("notes", exp.notes)
      if (exp.loanId != null) obj.put("loanId", exp.loanId)
      if (exp.debtId != null) obj.put("debtId", exp.debtId)
      if (exp.tripId != null) obj.put("tripId", exp.tripId)
      expArray.put(obj)
    }
    root.put("expenses", expArray)

    // Active Loans
    val loanArray = JSONArray()
    loans.forEach { loan ->
      val obj = JSONObject()
      obj.put("id", loan.id)
      obj.put("loanName", loan.loanName)
      obj.put("loanCategory", loan.loanCategory)
      obj.put("principalAmount", loan.principalAmount)
      obj.put("annualInterestRate", loan.annualInterestRate)
      obj.put("tenureMonths", loan.tenureMonths)
      obj.put("emiAmount", loan.emiAmount)
      obj.put("startDate", loan.startDate)
      obj.put("paidEmisCount", loan.paidEmisCount)
      obj.put("dueDayOfMonth", loan.dueDayOfMonth)
      obj.put("notes", loan.notes)
      if (loan.bankAccountId != null) obj.put("bankAccountId", loan.bankAccountId)
      obj.put("bankAccountName", loan.bankAccountName)
      loanArray.put(obj)
    }
    root.put("loans", loanArray)

    // Debts & Udhaar Ledger
    val debtArray = JSONArray()
    debts.forEach { debt ->
      val obj = JSONObject()
      obj.put("id", debt.id)
      obj.put("personName", debt.personName)
      obj.put("amount", debt.amount)
      obj.put("type", debt.type)
      obj.put("dateTimestamp", debt.dateTimestamp)
      if (debt.dueDateTimestamp != null) obj.put("dueDateTimestamp", debt.dueDateTimestamp)
      obj.put("isSettled", debt.isSettled)
      if (debt.settledDateTimestamp != null) obj.put("settledDateTimestamp", debt.settledDateTimestamp)
      obj.put("notes", debt.notes)
      if (debt.bankAccountId != null) obj.put("bankAccountId", debt.bankAccountId)
      obj.put("bankAccountName", debt.bankAccountName)
      debtArray.put(obj)
    }
    root.put("debts", debtArray)

    // Recurring Subscriptions
    val subArray = JSONArray()
    subscriptions.forEach { sub ->
      val obj = JSONObject()
      obj.put("id", sub.id)
      obj.put("name", sub.name)
      obj.put("amount", sub.amount)
      obj.put("categoryName", sub.categoryName)
      obj.put("billingCycle", sub.billingCycle)
      obj.put("dueDayOfMonth", sub.dueDayOfMonth)
      obj.put("dueMonth", sub.dueMonth)
      obj.put("dueDayOfWeek", sub.dueDayOfWeek)
      obj.put("notes", sub.notes)
      if (sub.bankAccountId != null) obj.put("bankAccountId", sub.bankAccountId)
      obj.put("bankAccountName", sub.bankAccountName)
      obj.put("lastPaidTimestamp", sub.lastPaidTimestamp)
      subArray.put(obj)
    }
    root.put("subscriptions", subArray)

    // Credit Card Reminders
    val ccArray = JSONArray()
    creditCardReminders.forEach { cc ->
      val obj = JSONObject()
      obj.put("accountId", cc.accountId)
      obj.put("accountName", bankAccounts.firstOrNull { it.id == cc.accountId }?.bankName.orEmpty())
      obj.put("amount", cc.amount)
      obj.put("dueDateEpochDay", cc.dueDateEpochDay)
      obj.put("paidDateEpochDay", cc.paidDateEpochDay)
      obj.put("notificationsEnabled", cc.notificationsEnabled)
      ccArray.put(obj)
    }
    root.put("creditCardReminders", ccArray)

    return root.toString(2)
  }

  fun parseFromJson(jsonString: String): BackupParseResult {
    if (jsonString.isBlank()) {
      return BackupParseResult.Error("JSON data is empty.")
    }

    return try {
      val root = JSONObject(jsonString)

      val categories = mutableListOf<ExpenseCategory>()
      if (root.has("categories")) {
        val catArray = root.getJSONArray("categories")
        for (i in 0 until catArray.length()) {
          val obj = catArray.getJSONObject(i)
          val name = obj.optString("name", "").trim()
          if (name.isNotEmpty()) {
            categories.add(
              ExpenseCategory(
                id = obj.optLong("id", 0L),
                name = name,
                colorHex = obj.optString("colorHex", "#6366F1"),
                iconName = obj.optString("iconName", "category"),
                isCustom = obj.optBoolean("isCustom", true)
              )
            )
          }
        }
      }

      val bankAccounts = mutableListOf<BankAccount>()
      if (root.has("bankAccounts")) {
        val bankArray = root.getJSONArray("bankAccounts")
        for (i in 0 until bankArray.length()) {
          val obj = bankArray.getJSONObject(i)
          val bankName = obj.optString("bankName", "").trim()
          if (bankName.isNotEmpty()) {
            val balanceVal = obj.optDouble("balance", 0.0)
            val openingVal = obj.optDouble("openingBalance", balanceVal)
            bankAccounts.add(
              BankAccount(
                id = obj.optLong("id", 0L),
                bankName = bankName,
                accountType = obj.optString("accountType", "Savings"),
                accountNumber = obj.optString("accountNumber", ""),
                ifscOrRouting = obj.optString("ifscOrRouting", ""),
                holderName = obj.optString("holderName", ""),
                openingBalance = openingVal,
                balance = balanceVal,
                notes = obj.optString("notes", "")
              )
            )
          }
        }
      }

      val trips = mutableListOf<TripRecord>()
      if (root.has("trips")) {
        val tripArray = root.getJSONArray("trips")
        for (i in 0 until tripArray.length()) {
          val obj = tripArray.getJSONObject(i)
          val tripName = obj.optString("tripName", "").trim()
          if (tripName.isNotEmpty()) {
            trips.add(
              TripRecord(
                id = obj.optLong("id", 0L),
                tripName = tripName,
                destination = obj.optString("destination", ""),
                budget = obj.optDouble("budget", 0.0),
                startDate = obj.optString("startDate", ""),
                endDate = obj.optString("endDate", ""),
                notes = obj.optString("notes", ""),
                categoryCapsJson = obj.optString("categoryCapsJson", "{}")
              )
            )
          }
        }
      }

      val expenses = mutableListOf<ExpenseRecord>()
      if (root.has("expenses")) {
        val expArray = root.getJSONArray("expenses")
        for (i in 0 until expArray.length()) {
          val obj = expArray.getJSONObject(i)
          val title = obj.optString("title", "").trim()
          val amount = obj.optDouble("amount", 0.0)
          if (title.isNotEmpty() && amount > 0.0) {
            val accId = if (obj.has("bankAccountId") && obj.getLong("bankAccountId") > 0) obj.getLong("bankAccountId") else null
            val lId = if (obj.has("loanId") && obj.getLong("loanId") > 0) obj.getLong("loanId") else null
            val dId = if (obj.has("debtId") && obj.getLong("debtId") > 0) obj.getLong("debtId") else null
            val tId = if (obj.has("tripId") && obj.getLong("tripId") > 0) obj.getLong("tripId") else null
            expenses.add(
              ExpenseRecord(
                id = obj.optLong("id", 0L),
                title = title,
                amount = amount,
                categoryName = obj.optString("categoryName", "General"),
                type = obj.optString("type", "EXPENSE"),
                bankAccountId = accId,
                bankAccountName = obj.optString("bankAccountName", ""),
                dateTimestamp = obj.optLong("dateTimestamp", System.currentTimeMillis()),
                notes = obj.optString("notes", ""),
                loanId = lId,
                debtId = dId,
                tripId = tId
              )
            )
          }
        }
      }

      // Parse Loans
      val loans = mutableListOf<LoanRecord>()
      if (root.has("loans")) {
        val loanArray = root.getJSONArray("loans")
        for (i in 0 until loanArray.length()) {
          val obj = loanArray.getJSONObject(i)
          val loanName = obj.optString("loanName", "").trim()
          val principal = obj.optDouble("principalAmount", 0.0)
          if (loanName.isNotEmpty() && principal > 0.0) {
            val bId = if (obj.has("bankAccountId") && obj.getLong("bankAccountId") > 0) obj.getLong("bankAccountId") else null
            loans.add(
              LoanRecord(
                id = obj.optLong("id", 0L),
                loanName = loanName,
                loanCategory = obj.optString("loanCategory", "PERSONAL"),
                principalAmount = principal,
                annualInterestRate = obj.optDouble("annualInterestRate", 10.0),
                tenureMonths = obj.optInt("tenureMonths", 12),
                emiAmount = obj.optDouble("emiAmount", 0.0),
                startDate = obj.optString("startDate", ""),
                paidEmisCount = obj.optInt("paidEmisCount", 0),
                dueDayOfMonth = obj.optInt("dueDayOfMonth", 1).coerceIn(1, 31),
                notes = obj.optString("notes", ""),
                bankAccountId = bId,
                bankAccountName = obj.optString("bankAccountName", "")
              )
            )
          }
        }
      }

      // Parse Debts
      val debts = mutableListOf<DebtRecord>()
      if (root.has("debts")) {
        val debtArray = root.getJSONArray("debts")
        for (i in 0 until debtArray.length()) {
          val obj = debtArray.getJSONObject(i)
          val personName = obj.optString("personName", "").trim()
          val amount = obj.optDouble("amount", 0.0)
          if (personName.isNotEmpty() && amount > 0.0) {
            val due = if (obj.has("dueDateTimestamp") && obj.getLong("dueDateTimestamp") > 0) obj.getLong("dueDateTimestamp") else null
            val settled = if (obj.has("settledDateTimestamp") && obj.getLong("settledDateTimestamp") > 0) obj.getLong("settledDateTimestamp") else null
            debts.add(
              DebtRecord(
                id = obj.optLong("id", 0L),
                personName = personName,
                amount = amount,
                type = obj.optString("type", "LENT"),
                dateTimestamp = obj.optLong("dateTimestamp", System.currentTimeMillis()),
                dueDateTimestamp = due,
                isSettled = obj.optBoolean("isSettled", false),
                notes = obj.optString("notes", ""),
                settledDateTimestamp = settled,
                bankAccountId = if (obj.optLong("bankAccountId", -1L) > 0L) obj.optLong("bankAccountId") else null,
                bankAccountName = obj.optString("bankAccountName", "")
              )
            )
          }
        }
      }

      // Parse Subscriptions
      val subscriptions = mutableListOf<SubscriptionRecord>()
      if (root.has("subscriptions")) {
        val subArray = root.getJSONArray("subscriptions")
        for (i in 0 until subArray.length()) {
          val obj = subArray.getJSONObject(i)
          val title = obj.optString("name", obj.optString("title", "")).trim()
          val amount = obj.optDouble("amount", 0.0)
          if (title.isNotEmpty() && amount > 0.0) {
            subscriptions.add(
              SubscriptionRecord(
                id = obj.optLong("id", 0L),
                name = title,
                amount = amount,
                categoryName = obj.optString("categoryName", "Subscriptions"),
                billingCycle = obj.optString("billingCycle", obj.optString("frequency", "MONTHLY")),
                dueDayOfMonth = obj.optInt("dueDayOfMonth", 1),
                dueMonth = obj.optInt("dueMonth", 1),
                dueDayOfWeek = obj.optInt("dueDayOfWeek", 1),
                notes = obj.optString("notes", ""),
                bankAccountId = if (obj.optLong("bankAccountId", -1L) > 0L) obj.optLong("bankAccountId") else null,
                bankAccountName = obj.optString("bankAccountName", ""),
                lastPaidTimestamp = obj.optLong("lastPaidTimestamp", 0L)
              )
            )
          }
        }
      }

      // Parse Credit Card Reminders
      val creditCardReminders = mutableListOf<CreditCardBillReminder>()
      val creditCardReminderAccountNames = mutableMapOf<Long, String>()
      if (root.has("creditCardReminders")) {
        val ccArray = root.getJSONArray("creditCardReminders")
        for (i in 0 until ccArray.length()) {
          val obj = ccArray.getJSONObject(i)
          val accId = obj.optLong("accountId", -1L)
          if (accId > 0) {
            creditCardReminderAccountNames[accId] = obj.optString("accountName", "")
            creditCardReminders.add(
              CreditCardBillReminder(
                accountId = accId,
                amount = obj.optDouble("amount", 0.0),
                dueDateEpochDay = obj.optLong("dueDateEpochDay", 0L),
                paidDateEpochDay = obj.optLong("paidDateEpochDay", -1L),
                notificationsEnabled = obj.optBoolean("notificationsEnabled", true)
              )
            )
          }
        }
      }
      val monthlyBudgetLimit = if (root.has("monthlyBudgetLimit")) {
        root.optDouble("monthlyBudgetLimit", Double.NaN).takeIf { it.isFinite() && it >= 0.0 }
      } else null
      val userUpiId = if (root.has("userUpiId")) root.optString("userUpiId").trim() else null

      if (
        categories.isEmpty() && bankAccounts.isEmpty() && trips.isEmpty() && expenses.isEmpty() &&
        loans.isEmpty() && debts.isEmpty() && subscriptions.isEmpty() && creditCardReminders.isEmpty() &&
        monthlyBudgetLimit == null && userUpiId == null
      ) {
        return BackupParseResult.Error("No valid financial data found in JSON.")
      }

      val summary = listOfNotNull(
        categories.size.takeIf { it > 0 }?.let { "$it Categories" },
        bankAccounts.size.takeIf { it > 0 }?.let { "$it Accounts" },
        trips.size.takeIf { it > 0 }?.let { "$it Trips" },
        expenses.size.takeIf { it > 0 }?.let { "$it Expenses" },
        loans.size.takeIf { it > 0 }?.let { "$it Loans" },
        debts.size.takeIf { it > 0 }?.let { "$it Debts" },
        subscriptions.size.takeIf { it > 0 }?.let { "$it Subscriptions" },
        creditCardReminders.size.takeIf { it > 0 }?.let { "$it Card Bills" },
        monthlyBudgetLimit?.let { "Budget" },
        userUpiId?.takeIf { it.isNotBlank() }?.let { "UPI ID" }
      ).joinToString(", ")

      BackupParseResult.Success(
        payload = FinanceBackupPayload(
          categories = categories,
          bankAccounts = bankAccounts,
          trips = trips,
          expenses = expenses,
          loans = loans,
          debts = debts,
          subscriptions = subscriptions,
          creditCardReminders = creditCardReminders,
          creditCardReminderAccountNames = creditCardReminderAccountNames,
          monthlyBudgetLimit = monthlyBudgetLimit,
          userUpiId = userUpiId
        ),
        summary = summary
      )
    } catch (e: Exception) {
      BackupParseResult.Error("Failed to parse JSON file: ${e.localizedMessage ?: "Invalid format"}")
    }
  }

  fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
    return try {
      val outputStream = context.contentResolver.openOutputStream(uri) ?: return false
      outputStream.use { stream ->
        stream.write(content.toByteArray(Charsets.UTF_8))
        stream.flush()
      }
      true
    } catch (e: Exception) {
      false
    }
  }

  fun readFromUri(context: Context, uri: Uri): String? {
    return try {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
          reader.readText()
        }
      }
    } catch (e: Exception) {
      null
    }
  }

  fun shareBackupFile(context: Context, jsonContent: String) {
    try {
      val dir = File(context.cacheDir, "backups")
      if (!dir.exists()) dir.mkdirs()
      val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
      val file = File(dir, "OmniCalc_Backup_$dateStr.json")
      file.writeText(jsonContent, Charsets.UTF_8)

      val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, fileUri)
        putExtra(Intent.EXTRA_SUBJECT, "OmniCalc Finance Backup ($dateStr)")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(intent, "Share OmniCalc Backup")
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    } catch (e: Exception) {
      // Fallback to plain text sharing
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, jsonContent)
        putExtra(Intent.EXTRA_SUBJECT, "OmniCalc Finance Backup")
      }
      val chooser = Intent.createChooser(intent, "Share OmniCalc Backup JSON")
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    }
  }
}
