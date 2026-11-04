package com.example.domain.report

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.TripRecord
import com.example.data.model.EmiResult
import com.example.ui.components.CurrencyFormatter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

  fun generateEmiReport(
    context: Context,
    principal: Double,
    rate: Double,
    tenureMonths: Int,
    result: EmiResult
  ): File {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard (595 x 842 pts)
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val titlePaint = Paint().apply {
      color = Color.rgb(79, 70, 229) // Indigo primary
      textSize = 20f
      isFakeBoldText = true
      isAntiAlias = true
    }

    val subtitlePaint = Paint().apply {
      color = Color.DKGRAY
      textSize = 10f
      isAntiAlias = true
    }

    val textPaint = Paint().apply {
      color = Color.BLACK
      textSize = 11f
      isAntiAlias = true
    }

    val boldPaint = Paint().apply {
      color = Color.BLACK
      textSize = 11f
      isFakeBoldText = true
      isAntiAlias = true
    }

    val boxPaint = Paint().apply {
      color = Color.rgb(243, 244, 246)
      style = Paint.Style.FILL
    }

    val linePaint = Paint().apply {
      color = Color.LTGRAY
      strokeWidth = 1f
    }

    var y = 50f

    // Header
    canvas.drawText("OmniCalc", 40f, y, titlePaint)
    y += 18f
    canvas.drawText("OFFICIAL LOAN & EMI AMORTIZATION STATEMENT", 40f, y, subtitlePaint)
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
    canvas.drawText("Generated on: $dateStr", 400f, y, subtitlePaint)
    y += 15f
    canvas.drawLine(40f, y, 555f, y, linePaint)
    y += 25f

    // Loan Parameters Summary Box
    canvas.drawRoundRect(40f, y, 555f, y + 100f, 12f, 12f, boxPaint)
    val boxY = y + 25f
    canvas.drawText("Loan Principal:", 55f, boxY, boldPaint)
    canvas.drawText(CurrencyFormatter.formatWhole(principal), 160f, boxY, textPaint)

    canvas.drawText("Interest Rate:", 320f, boxY, boldPaint)
    canvas.drawText("$rate% p.a.", 420f, boxY, textPaint)

    canvas.drawText("Loan Tenure:", 55f, boxY + 24f, boldPaint)
    canvas.drawText("${tenureMonths / 12} Years ($tenureMonths Mo)", 160f, boxY + 24f, textPaint)

    canvas.drawText("Monthly EMI:", 320f, boxY + 24f, boldPaint)
    canvas.drawText(CurrencyFormatter.formatWhole(result.monthlyEmi), 420f, boxY + 24f, boldPaint)

    canvas.drawText("Total Interest:", 55f, boxY + 48f, boldPaint)
    canvas.drawText(CurrencyFormatter.formatWhole(result.totalInterest), 160f, boxY + 48f, textPaint)

    canvas.drawText("Total Payment:", 320f, boxY + 48f, boldPaint)
    canvas.drawText(CurrencyFormatter.formatWhole(result.totalPayment), 420f, boxY + 48f, boldPaint)

    y += 125f

    // Schedule Table Header
    canvas.drawText("Amortization Schedule Breakdown (${result.schedule.size} Months)", 40f, y, boldPaint)
    y += 15f

    val headerBg = Paint().apply {
      color = Color.rgb(229, 231, 235)
      style = Paint.Style.FILL
    }
    val thPaint = Paint().apply {
      color = Color.rgb(31, 41, 55)
      textSize = 10f
      isFakeBoldText = true
      isAntiAlias = true
    }
    fun drawScheduleHeader(targetCanvas: android.graphics.Canvas, top: Float) {
      targetCanvas.drawRect(40f, top, 555f, top + 22f, headerBg)
      val headerY = top + 15f
      targetCanvas.drawText("Mo.", 50f, headerY, thPaint)
      targetCanvas.drawText("Opening", 110f, headerY, thPaint)
      targetCanvas.drawText("Principal", 220f, headerY, thPaint)
      targetCanvas.drawText("Interest", 330f, headerY, thPaint)
      targetCanvas.drawText("Closing Balance", 430f, headerY, thPaint)
    }

    drawScheduleHeader(canvas, y)

    y += 24f

    val rowPaint = Paint().apply {
      textSize = 9.5f
      color = Color.rgb(55, 65, 81)
      isAntiAlias = true
    }

    var currentPage = page
    var currentCanvas = canvas
    var pageNumber = 1
    result.schedule.forEachIndexed { index, m ->
      if (y + 20f > 780f) {
        currentCanvas.drawLine(40f, 800f, 555f, 800f, linePaint)
        currentCanvas.drawText("OmniCalc Financial Tool • Confidential & Personal Record", 40f, 815f, subtitlePaint)
        currentCanvas.drawText("Page $pageNumber", 500f, 815f, subtitlePaint)
        pdfDocument.finishPage(currentPage)

        pageNumber++
        val nextPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        currentPage = pdfDocument.startPage(nextPageInfo)
        currentCanvas = currentPage.canvas
        y = 50f
        drawScheduleHeader(currentCanvas, y)
        y += 24f
      }

      val ry = y + 16f
      if (index % 2 == 1) {
        val altBg = Paint().apply { color = Color.rgb(249, 250, 251) }
        currentCanvas.drawRect(40f, y, 555f, y + 20f, altBg)
      }
      currentCanvas.drawText(m.monthIndex.toString(), 50f, ry, rowPaint)
      currentCanvas.drawText(CurrencyFormatter.formatWhole(m.openingBalance, ""), 110f, ry, rowPaint)
      currentCanvas.drawText(CurrencyFormatter.formatWhole(m.principalPaid, ""), 220f, ry, rowPaint)
      currentCanvas.drawText(CurrencyFormatter.formatWhole(m.interestPaid, ""), 330f, ry, rowPaint)
      currentCanvas.drawText(CurrencyFormatter.formatWhole(m.closingBalance, ""), 430f, ry, rowPaint)
      y += 20f
    }

    // Footer
    currentCanvas.drawLine(40f, 800f, 555f, 800f, linePaint)
    currentCanvas.drawText("OmniCalc Financial Tool • Confidential & Personal Record", 40f, 815f, subtitlePaint)
    currentCanvas.drawText("Page $pageNumber", 500f, 815f, subtitlePaint)

    pdfDocument.finishPage(currentPage)

    val dir = File(context.cacheDir, "reports")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "EMI_Statement_${System.currentTimeMillis()}.pdf")
    FileOutputStream(file).use { out ->
      pdfDocument.writeTo(out)
    }
    pdfDocument.close()
    return file
  }

  fun generateTripReport(
    context: Context,
    trip: TripRecord,
    expenses: List<ExpenseRecord>
  ): File {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val titlePaint = Paint().apply {
      color = Color.rgb(13, 148, 136) // Teal
      textSize = 20f
      isFakeBoldText = true
      isAntiAlias = true
    }
    val subtitlePaint = Paint().apply {
      color = Color.DKGRAY
      textSize = 10f
      isAntiAlias = true
    }
    val textPaint = Paint().apply {
      color = Color.BLACK
      textSize = 10.5f
      isAntiAlias = true
    }
    val boldPaint = Paint().apply {
      color = Color.BLACK
      textSize = 11f
      isFakeBoldText = true
      isAntiAlias = true
    }
    val boxPaint = Paint().apply {
      color = Color.rgb(240, 253, 250)
      style = Paint.Style.FILL
    }
    val linePaint = Paint().apply {
      color = Color.LTGRAY
      strokeWidth = 1f
    }

    var y = 50f

    // Header
    canvas.drawText("OmniCalc", 40f, y, titlePaint)
    y += 18f
    canvas.drawText("TRIP & VACATION EXPENSE LEDGER STATEMENT", 40f, y, subtitlePaint)
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
    canvas.drawText("Generated on: $dateStr", 400f, y, subtitlePaint)
    y += 15f
    canvas.drawLine(40f, y, 555f, y, linePaint)
    y += 25f

    // Trip Info Box
    val totalSpent = expenses.sumOf { it.amount }
    val remaining = trip.budget - totalSpent

    canvas.drawRoundRect(40f, y, 555f, y + 90f, 12f, 12f, boxPaint)
    val boxY = y + 25f
    canvas.drawText("Trip / Vacation:", 55f, boxY, boldPaint)
    canvas.drawText(trip.tripName, 160f, boxY, textPaint)

    canvas.drawText("Destination:", 320f, boxY, boldPaint)
    canvas.drawText(trip.destination.ifBlank { "N/A" }, 410f, boxY, textPaint)

    canvas.drawText("Dates:", 55f, boxY + 24f, boldPaint)
    canvas.drawText("${trip.startDate} - ${trip.endDate}", 160f, boxY + 24f, textPaint)

    canvas.drawText("Total Budget:", 320f, boxY + 24f, boldPaint)
    canvas.drawText(CurrencyFormatter.formatWhole(trip.budget), 410f, boxY + 24f, boldPaint)

    canvas.drawText("Total Spent:", 55f, boxY + 48f, boldPaint)
    canvas.drawText(CurrencyFormatter.formatWhole(totalSpent), 160f, boxY + 48f, textPaint)

    canvas.drawText("Balance Remaining:", 320f, boxY + 48f, boldPaint)
    canvas.drawText(CurrencyFormatter.formatWhole(remaining), 440f, boxY + 48f, boldPaint)

    y += 115f

    // Itemized Ledger Table
    canvas.drawText("Itemized Trip Expenses (${expenses.size} records)", 40f, y, boldPaint)
    y += 15f

    val headerBg = Paint().apply {
      color = Color.rgb(204, 251, 241)
      style = Paint.Style.FILL
    }
    val thPaint = Paint().apply {
      color = Color.rgb(19, 78, 74)
      textSize = 10f
      isFakeBoldText = true
      isAntiAlias = true
    }
    fun drawExpenseTableHeader(targetCanvas: android.graphics.Canvas, top: Float) {
      targetCanvas.drawRect(40f, top, 555f, top + 22f, headerBg)
      val headerY = top + 15f
      targetCanvas.drawText("Date", 50f, headerY, thPaint)
      targetCanvas.drawText("Expense Description", 160f, headerY, thPaint)
      targetCanvas.drawText("Category", 350f, headerY, thPaint)
      targetCanvas.drawText("Amount", 470f, headerY, thPaint)
    }

    drawExpenseTableHeader(canvas, y)

    y += 24f

    val rowPaint = Paint().apply {
      textSize = 9.5f
      color = Color.rgb(55, 65, 81)
      isAntiAlias = true
    }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    var currentPage = page
    var currentCanvas = canvas
    var pageNumber = 1
    expenses.forEachIndexed { index, exp ->
      if (y + 20f > 780f) {
        currentCanvas.drawLine(40f, 800f, 555f, 800f, linePaint)
        currentCanvas.drawText("OmniCalc Trip Expense Statement • Generated for personal tracking", 40f, 815f, subtitlePaint)
        currentCanvas.drawText("Page $pageNumber", 500f, 815f, subtitlePaint)
        pdfDocument.finishPage(currentPage)

        pageNumber++
        val nextPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        currentPage = pdfDocument.startPage(nextPageInfo)
        currentCanvas = currentPage.canvas
        y = 50f
        drawExpenseTableHeader(currentCanvas, y)
        y += 24f
      }

      val ry = y + 16f
      if (index % 2 == 1) {
        val altBg = Paint().apply { color = Color.rgb(249, 250, 251) }
        currentCanvas.drawRect(40f, y, 555f, y + 20f, altBg)
      }
      val d = dateFormat.format(Date(exp.dateTimestamp))
      currentCanvas.drawText(d, 50f, ry, rowPaint)
      currentCanvas.drawText(exp.title.take(30), 160f, ry, rowPaint)
      currentCanvas.drawText(exp.categoryName, 350f, ry, rowPaint)
      currentCanvas.drawText(CurrencyFormatter.formatWhole(exp.amount), 470f, ry, boldPaint)
      y += 20f
    }

    // Footer
    currentCanvas.drawLine(40f, 800f, 555f, 800f, linePaint)
    currentCanvas.drawText("OmniCalc Trip Expense Statement • Generated for personal tracking", 40f, 815f, subtitlePaint)
    currentCanvas.drawText("Page $pageNumber", 500f, 815f, subtitlePaint)

    pdfDocument.finishPage(currentPage)

    val dir = File(context.cacheDir, "reports")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "Trip_Statement_${System.currentTimeMillis()}.pdf")
    FileOutputStream(file).use { out ->
      pdfDocument.writeTo(out)
    }
    pdfDocument.close()
    return file
  }

  fun sharePdf(context: Context, pdfFile: File) {
    try {
      val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, fileUri)
        putExtra(Intent.EXTRA_SUBJECT, pdfFile.nameWithoutExtension)
        clipData = android.content.ClipData.newRawUri(pdfFile.name, fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(intent, "Share PDF Statement").apply {
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
      android.widget.Toast.makeText(context, "PDF Export: ${e.localizedMessage ?: "Failed to share"}", android.widget.Toast.LENGTH_LONG).show()
    }
  }

  fun savePdfToUri(context: Context, pdfFile: File, destinationUri: Uri): Boolean {
    return try {
      context.contentResolver.openOutputStream(destinationUri)?.use { out ->
        pdfFile.inputStream().use { input ->
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
