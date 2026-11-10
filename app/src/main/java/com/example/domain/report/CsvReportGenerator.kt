package com.example.domain.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.entity.AccountWithBalance
import com.example.data.local.entity.ExpenseRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvReportGenerator {

  private fun escapeCsv(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
      "\"$escaped\""
    } else {
      escaped
    }
  }

  fun generateExpensesCsv(context: Context, expenses: List<ExpenseRecord>): File {
    val dir = File(context.cacheDir, "csv_reports")
    if (!dir.exists()) dir.mkdirs()

    val file = File(dir, "OmniCalc_Expenses_${System.currentTimeMillis()}.csv")
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    FileOutputStream(file).bufferedWriter().use { writer ->
      // CSV Header
      writer.write("Transaction ID,Date,Type,Title,Category,Amount (INR),Payment Account,Notes\n")
      for (exp in expenses) {
        val dateStr = dateFormat.format(Date(exp.dateTimestamp))
        val row = listOf(
          exp.id.toString(),
          escapeCsv(dateStr),
          escapeCsv(exp.type),
          escapeCsv(exp.title),
          escapeCsv(exp.categoryName),
          String.format(Locale.US, "%.2f", exp.amount),
          escapeCsv(exp.bankAccountName),
          escapeCsv(exp.notes)
        ).joinToString(",")
        writer.write(row + "\n")
      }
    }
    return file
  }

  fun generateFullFinanceReportCsv(
    context: Context,
    accounts: List<AccountWithBalance>,
    expenses: List<ExpenseRecord>
  ): File {
    val dir = File(context.cacheDir, "csv_reports")
    if (!dir.exists()) dir.mkdirs()

    val file = File(dir, "OmniCalc_Finance_Statement_${System.currentTimeMillis()}.csv")
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    FileOutputStream(file).bufferedWriter().use { writer ->
      // 1. Bank & Cash Accounts Section
      writer.write("=== OMNICALC ACCOUNTS SUMMARY ===\n")
      writer.write("Account ID,Account Name,Account Type,Account Number,Opening Balance (INR),Live Balance (INR),Notes\n")
      for (accWithBal in accounts) {
        val acc = accWithBal.account
        val row = listOf(
          acc.id.toString(),
          escapeCsv(acc.bankName),
          escapeCsv(acc.accountType),
          escapeCsv(acc.accountNumber),
          String.format(Locale.US, "%.2f", acc.openingBalance),
          String.format(Locale.US, "%.2f", accWithBal.currentBalance),
          escapeCsv(acc.notes)
        ).joinToString(",")
        writer.write(row + "\n")
      }

      writer.write("\n")
      writer.write("=== ITEMISED TRANSACTION LEDGER ===\n")
      writer.write("Transaction ID,Date,Type,Title,Category,Amount (INR),Account,Notes\n")
      for (exp in expenses) {
        val dateStr = dateFormat.format(Date(exp.dateTimestamp))
        val row = listOf(
          exp.id.toString(),
          escapeCsv(dateStr),
          escapeCsv(exp.type),
          escapeCsv(exp.title),
          escapeCsv(exp.categoryName),
          String.format(Locale.US, "%.2f", exp.amount),
          escapeCsv(exp.bankAccountName),
          escapeCsv(exp.notes)
        ).joinToString(",")
        writer.write(row + "\n")
      }
    }
    return file
  }

  fun shareCsv(context: Context, csvFile: File, title: String = "Share Finance CSV") {
    try {
      val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, fileUri)
        putExtra(Intent.EXTRA_SUBJECT, csvFile.nameWithoutExtension)
        clipData = ClipData.newRawUri(csvFile.name, fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(intent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val resInfoList = context.packageManager.queryIntentActivities(chooser, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
      for (resolveInfo in resInfoList) {
        val pkg = resolveInfo.activityInfo.packageName
        context.grantUriPermission(pkg, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(chooser)
    } catch (e: Exception) {
      android.widget.Toast.makeText(context, "CSV Export: ${e.localizedMessage ?: "Failed to share"}", android.widget.Toast.LENGTH_LONG).show()
    }
  }

  fun saveCsvToUri(context: Context, csvFile: File, destinationUri: Uri): Boolean {
    return try {
      context.contentResolver.openOutputStream(destinationUri)?.use { out ->
        csvFile.inputStream().use { input ->
          input.copyTo(out)
        }
        out.flush()
      }
      true
    } catch (e: Exception) {
      false
    }
  }
}
