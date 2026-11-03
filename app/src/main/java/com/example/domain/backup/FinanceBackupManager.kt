package com.example.domain.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.ExpenseCategory
import com.example.data.local.entity.ExpenseRecord
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
  val expenses: List<ExpenseRecord>
)

sealed class BackupParseResult {
  data class Success(val payload: FinanceBackupPayload, val summary: String) : BackupParseResult()
  data class Error(val message: String) : BackupParseResult()
}

object FinanceBackupManager {

  private const val BACKUP_VERSION = 1
  private const val APP_IDENTIFIER = "OmniCalc"

  fun exportToJson(
    categories: List<ExpenseCategory>,
    bankAccounts: List<BankAccount>,
    trips: List<TripRecord>,
    expenses: List<ExpenseRecord> = emptyList()
  ): String {
    val root = JSONObject()
    root.put("version", BACKUP_VERSION)
    root.put("app", APP_IDENTIFIER)
    root.put("exportedAt", System.currentTimeMillis())
    root.put("exportedAtFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

    // Categories (include custom & default so full setup is preserved)
    val catArray = JSONArray()
    categories.forEach { cat ->
      val obj = JSONObject()
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
      obj.put("tripName", trip.tripName)
      obj.put("destination", trip.destination)
      obj.put("budget", trip.budget)
      obj.put("startDate", trip.startDate)
      obj.put("endDate", trip.endDate)
      obj.put("notes", trip.notes)
      tripArray.put(obj)
    }
    root.put("trips", tripArray)

    // Expenses (and income, with bank account links)
    val expArray = JSONArray()
    expenses.forEach { exp ->
      val obj = JSONObject()
      obj.put("title", exp.title)
      obj.put("amount", exp.amount)
      obj.put("categoryName", exp.categoryName)
      obj.put("type", exp.type)
      obj.put("bankAccountId", exp.bankAccountId ?: -1L)
      obj.put("bankAccountName", exp.bankAccountName)
      obj.put("dateTimestamp", exp.dateTimestamp)
      obj.put("notes", exp.notes)
      expArray.put(obj)
    }
    root.put("expenses", expArray)

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
                id = 0, // Reset for auto-increment
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
                id = 0, // Reset for auto-increment
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
                id = 0, // Reset for auto-increment
                tripName = tripName,
                destination = obj.optString("destination", ""),
                budget = obj.optDouble("budget", 0.0),
                startDate = obj.optString("startDate", ""),
                endDate = obj.optString("endDate", ""),
                notes = obj.optString("notes", "")
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
            expenses.add(
              ExpenseRecord(
                id = 0,
                title = title,
                amount = amount,
                categoryName = obj.optString("categoryName", "General"),
                type = obj.optString("type", "EXPENSE"),
                bankAccountId = accId,
                bankAccountName = obj.optString("bankAccountName", ""),
                dateTimestamp = obj.optLong("dateTimestamp", System.currentTimeMillis()),
                notes = obj.optString("notes", "")
              )
            )
          }
        }
      }

      if (categories.isEmpty() && bankAccounts.isEmpty() && trips.isEmpty() && expenses.isEmpty()) {
        return BackupParseResult.Error("No valid categories, bank accounts, or trips found in JSON.")
      }

      val summary = buildString {
        if (categories.isNotEmpty()) append("${categories.size} Categories, ")
        if (bankAccounts.isNotEmpty()) append("${bankAccounts.size} Bank Accounts, ")
        if (trips.isNotEmpty()) append("${trips.size} Trips, ")
        if (expenses.isNotEmpty()) append("${expenses.size} Expenses")
      }.trimEnd(' ', ',')

      BackupParseResult.Success(
        payload = FinanceBackupPayload(categories, bankAccounts, trips, expenses),
        summary = summary
      )
    } catch (e: Exception) {
      BackupParseResult.Error("Failed to parse JSON file: ${e.localizedMessage ?: "Invalid format"}")
    }
  }

  fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
    return try {
      context.contentResolver.openOutputStream(uri)?.use { stream ->
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
