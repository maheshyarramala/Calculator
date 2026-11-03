package com.example.ui.screens.finance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.domain.report.PdfReportGenerator
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.ExpenseCategory
import com.example.data.local.entity.ExpenseRecord
import com.example.data.local.entity.TripRecord
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.NumberInputField
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceHubScreen(
  viewModel: FinanceHubViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val categories by viewModel.categories.collectAsState()
  val expenses by viewModel.expenses.collectAsState()
  val bankAccounts by viewModel.bankAccounts.collectAsState()
  val trips by viewModel.trips.collectAsState()

  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  var preselectedTripIdForExpense by remember { mutableStateOf<Long?>(null) }

  // Storage Access Framework (SAF) Launchers for JSON Export & Import
  val exportFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json")
  ) { uri: Uri? ->
    uri?.let { viewModel.exportToUri(context, it) }
  }

  val importFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    uri?.let { viewModel.importFromUri(context, it, uiState.replaceExistingOnImport) }
  }

  // Backup & Restore Dialogs
  if (uiState.showBackupModal) {
    BackupRestoreBottomSheet(
      categoriesCount = categories.size,
      banksCount = bankAccounts.size,
      tripsCount = trips.size,
      expensesCount = expenses.size,
      replaceExisting = uiState.replaceExistingOnImport,
      onReplaceExistingChange = { viewModel.setReplaceExistingOnImport(it) },
      onExportToFile = {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        exportFileLauncher.launch("OmniCalc_Finance_Backup_$dateStr.json")
      },
      onShareBackup = { viewModel.shareBackup(context) },
      onCopyJson = { viewModel.copyBackupJson(context) },
      onImportFromFile = {
        importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
      },
      onPasteJsonClick = {
        viewModel.setShowBackupModal(false)
        viewModel.setShowPasteJsonDialog(true)
      },
      onDismiss = { viewModel.setShowBackupModal(false) }
    )
  }

  if (uiState.showPasteJsonDialog) {
    PasteJsonBackupDialog(
      replaceExisting = uiState.replaceExistingOnImport,
      onDismiss = { viewModel.setShowPasteJsonDialog(false) },
      onConfirm = { json, replace ->
        viewModel.importFromJson(json, replace)
      }
    )
  }

  // Dialogs
  if (uiState.showAddCategoryDialog) {
    AddCategoryDialog(
      onDismiss = { viewModel.setShowAddCategory(false) },
      onConfirm = { name, colorHex -> viewModel.addCategory(name, colorHex) }
    )
  }

  if (uiState.showAddExpenseDialog) {
    AddExpenseDialog(
      categories = categories,
      trips = trips,
      initialTripId = preselectedTripIdForExpense,
      onDismiss = {
        viewModel.setShowAddExpense(false)
        preselectedTripIdForExpense = null
      },
      onConfirm = { title, amount, category, notes, tripId ->
        viewModel.addExpense(title, amount, category, notes, tripId)
        preselectedTripIdForExpense = null
      }
    )
  }

  if (uiState.showAddBankDialog) {
    AddBankDialog(
      onDismiss = { viewModel.setShowAddBank(false) },
      onConfirm = { name, type, num, ifsc, holder, bal, notes ->
        viewModel.addBankAccount(name, type, num, ifsc, holder, bal, notes)
      }
    )
  }

  if (uiState.showAddTripDialog) {
    AddTripDialog(
      onDismiss = { viewModel.setShowAddTrip(false) },
      onConfirm = { name, dest, budget, start, end, notes ->
        viewModel.addTrip(name, dest, budget, start, end, notes)
      }
    )
  }

  Scaffold(
    snackbarHost = {
      uiState.toastMessage?.let { msg ->
        Snackbar(
          modifier = Modifier.padding(16.dp),
          action = {
            TextButton(onClick = { viewModel.clearToast() }) {
              Text("OK", color = MaterialTheme.colorScheme.inversePrimary)
            }
          }
        ) {
          Text(msg)
        }
      }
    },
    floatingActionButton = {
      when (uiState.selectedTab) {
        FinanceTab.EXPENSES -> {
          FloatingActionButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.setShowAddExpense(true)
            },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("add_expense_fab")
          ) {
            Icon(Icons.Default.Add, contentDescription = "Add Expense")
          }
        }
        FinanceTab.BANK_ACCOUNTS -> {
          FloatingActionButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.setShowAddBank(true)
            },
            containerColor = Color(0xFF1E3A8A),
            contentColor = Color.White,
            modifier = Modifier.testTag("add_bank_fab")
          ) {
            Icon(Icons.Default.AddCard, contentDescription = "Add Bank Account")
          }
        }
        FinanceTab.TRIPS -> {
          FloatingActionButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.setShowAddTrip(true)
            },
            containerColor = Color(0xFF0D9488),
            contentColor = Color.White,
            modifier = Modifier.testTag("add_trip_fab")
          ) {
            Icon(Icons.Default.AddLocationAlt, contentDescription = "Add Trip")
          }
        }
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Top Header with Backup / Restore Quick Action
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Finance & Money",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Custom categories, bank cards & trips",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        FilledTonalButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.setShowBackupModal(true)
          },
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.testTag("backup_restore_header_btn")
        ) {
          Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Backup / Restore", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
      }

      // Modern Tab Bar
      TabRow(
        selectedTabIndex = uiState.selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().testTag("finance_tabs")
      ) {
        FinanceTab.values().forEach { tab ->
          val icon = when (tab) {
            FinanceTab.EXPENSES -> Icons.Default.ReceiptLong
            FinanceTab.BANK_ACCOUNTS -> Icons.Default.AccountBalance
            FinanceTab.TRIPS -> Icons.Default.FlightTakeoff
          }
          Tab(
            selected = uiState.selectedTab == tab,
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.selectTab(tab)
            },
            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
            text = { Text(tab.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
            modifier = Modifier.testTag("finance_tab_${tab.name.lowercase()}")
          )
        }
      }

      when (uiState.selectedTab) {
        FinanceTab.EXPENSES -> ExpensesSection(
          expenses = expenses,
          categories = categories,
          onAddCategoryClick = { viewModel.setShowAddCategory(true) },
          onDeleteCategory = { viewModel.deleteCategory(it) },
          onDeleteExpense = { viewModel.deleteExpense(it) }
        )
        FinanceTab.BANK_ACCOUNTS -> BankAccountsSection(
          bankAccounts = bankAccounts,
          onCopyDetails = { viewModel.copyBankDetails(context, it) },
          onDeleteAccount = { viewModel.deleteBankAccount(it) }
        )
        FinanceTab.TRIPS -> TripsSection(
          trips = trips,
          expenses = expenses,
          onAddExpenseForTrip = { trip ->
            preselectedTripIdForExpense = trip.id
            viewModel.setShowAddExpense(true)
          },
          onExportTripPdf = { trip, tripExpenses ->
            try {
              val pdfFile = PdfReportGenerator.generateTripReport(context, trip, tripExpenses)
              PdfReportGenerator.sharePdf(context, pdfFile)
            } catch (e: Exception) {
              viewModel.setToast("Failed to generate PDF: ${e.message}")
            }
          },
          onDeleteTrip = { viewModel.deleteTrip(it) }
        )
      }
    }
  }
}

// -------------------------------------------------------------
// EXPENSES & CUSTOM CATEGORIES SECTION
// -------------------------------------------------------------
@Composable
private fun ExpensesSection(
  expenses: List<ExpenseRecord>,
  categories: List<ExpenseCategory>,
  onAddCategoryClick: () -> Unit,
  onDeleteCategory: (ExpenseCategory) -> Unit,
  onDeleteExpense: (ExpenseRecord) -> Unit
) {
  val totalSpent = remember(expenses) { expenses.sumOf { it.amount } }
  val haptic = LocalHapticFeedback.current

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Total Spent Summary Card
    item(key = "spending_summary") {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(20.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Total Expenses Logged", style = MaterialTheme.typography.labelMedium)
              Text(
                text = CurrencyFormatter.format(totalSpent),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )
            }
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
            }
          }
          Text(
            text = "${expenses.size} records • ${categories.size} categories",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
      }
    }

    // Spending Analytics Donut & Breakdown Card
    if (totalSpent > 0 && expenses.isNotEmpty()) {
      item(key = "spending_analytics") {
        SpendingAnalyticsCard(
          expenses = expenses,
          categories = categories,
          totalSpent = totalSpent
        )
      }
    }

    // Category Customization Bar
    item(key = "categories_header") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Categories & Slabs",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        FilledTonalButton(
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onAddCategoryClick()
          },
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.testTag("add_custom_category_btn")
        ) {
          Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("+ Add Category", fontSize = 12.sp)
        }
      }
    }

    item(key = "categories_list") {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(categories, key = { it.id }) { cat ->
          val color = remember(cat.colorHex) {
            try {
              Color(android.graphics.Color.parseColor(cat.colorHex))
            } catch (e: Exception) {
              Color(0xFF6366F1)
            }
          }
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.12f),
            border = null,
            modifier = Modifier.padding(vertical = 2.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(color)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = cat.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
              )
              if (cat.isCustom) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  Icons.Default.Close,
                  contentDescription = "Remove custom category",
                  modifier = Modifier
                    .size(14.dp)
                    .clickable { onDeleteCategory(cat) },
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }

    // Expense History List
    item(key = "expenses_title") {
      Text(
        text = "Recent Transactions",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 8.dp)
      )
    }

    if (expenses.isEmpty()) {
      item(key = "empty_expenses") {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              Icons.Default.ReceiptLong,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No expenses logged yet",
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Tap the + button below to record your first expense",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }
        }
      }
    } else {
      items(expenses, key = { it.id }) { expense ->
        ExpenseItemCard(
          expense = expense,
          categories = categories,
          onDelete = { onDeleteExpense(expense) }
        )
      }
    }
  }
}

@Composable
private fun ExpenseItemCard(
  expense: ExpenseRecord,
  categories: List<ExpenseCategory>,
  onDelete: () -> Unit
) {
  val matchedCat = remember(expense.categoryName, categories) {
    categories.find { it.name.equals(expense.categoryName, ignoreCase = true) }
  }
  val catColor = remember(matchedCat) {
    try {
      matchedCat?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: Color(0xFF6366F1)
    } catch (e: Exception) {
      Color(0xFF6366F1)
    }
  }
  val dateFormatted = remember(expense.dateTimestamp) {
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(expense.dateTimestamp))
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(catColor.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            Icons.Default.Sell,
            contentDescription = null,
            tint = catColor,
            modifier = Modifier.size(20.dp)
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = expense.title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = catColor.copy(alpha = 0.15f)
            ) {
              Text(
                text = expense.categoryName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = catColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = dateFormatted,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          if (expense.notes.isNotBlank()) {
            Text(
              text = expense.notes,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
          }
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "- ${CurrencyFormatter.format(expense.amount)}",
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = Color(0xFFDC2626)
        )
        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(28.dp)
        ) {
          Icon(
            Icons.Default.DeleteOutline,
            contentDescription = "Delete expense",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun SpendingAnalyticsCard(
  expenses: List<ExpenseRecord>,
  categories: List<ExpenseCategory>,
  totalSpent: Double
) {
  val catSpendList = remember(expenses, categories, totalSpent) {
    val map = mutableMapOf<String, Double>()
    expenses.forEach { exp ->
      map[exp.categoryName] = (map[exp.categoryName] ?: 0.0) + exp.amount
    }
    map.entries.map { entry ->
      val matchedCat = categories.find { it.name.equals(entry.key, ignoreCase = true) }
      val catColor = try {
        matchedCat?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: Color(0xFF6366F1)
      } catch (e: Exception) {
        Color(0xFF6366F1)
      }
      val percent = if (totalSpent > 0) (entry.value / totalSpent * 100).toFloat() else 0f
      Triple(entry.key, entry.value, Pair(catColor, percent))
    }.sortedByDescending { it.second }
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.PieChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Category Spending Analytics",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
          )
        }
        Text(
          text = "${catSpendList.size} Categories",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Donut Chart Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Compose Canvas Donut Chart
        Box(
          modifier = Modifier.size(125.dp),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            var startAngle = -90f
            catSpendList.forEach { (_, _, colorAndPercent) ->
              val sweepAngle = (colorAndPercent.second / 100f) * 360f
              if (sweepAngle > 0f) {
                drawArc(
                  color = colorAndPercent.first,
                  startAngle = startAngle,
                  sweepAngle = (sweepAngle - 2f).coerceAtLeast(1f),
                  useCenter = false,
                  style = Stroke(
                    width = 22.dp.toPx(),
                    cap = StrokeCap.Round
                  )
                )
                startAngle += sweepAngle
              }
            }
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "Top",
              fontSize = 9.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = catSpendList.firstOrNull()?.first ?: "",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }

        // Top Slices Quick Legend
        Column(
          modifier = Modifier.padding(start = 10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          catSpendList.take(3).forEach { (catName, _, colorAndPercent) ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(colorAndPercent.first)
              )
              Text(
                text = catName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 85.dp)
              )
              Text(
                text = "${colorAndPercent.second.toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

      // Category breakdown progress bars
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        catSpendList.forEach { (catName, amount, colorAndPercent) ->
          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colorAndPercent.first)
                )
                Text(
                  text = catName,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium
                )
              }
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = CurrencyFormatter.format(amount),
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "(${String.format(Locale.getDefault(), "%.1f", colorAndPercent.second)}%)",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            LinearProgressIndicator(
              progress = { colorAndPercent.second / 100f },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = colorAndPercent.first,
              trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// BANK DETAILS SECTION & PIN VAULT
// -------------------------------------------------------------
@Composable
private fun BankAccountsSection(
  bankAccounts: List<BankAccount>,
  onCopyDetails: (BankAccount) -> Unit,
  onDeleteAccount: (BankAccount) -> Unit
) {
  val haptic = LocalHapticFeedback.current
  var isVaultLocked by rememberSaveable { mutableStateOf(false) }
  var vaultPin by rememberSaveable { mutableStateOf("") }
  var enteredPin by remember { mutableStateOf("") }
  var showPinSetupDialog by remember { mutableStateOf(false) }
  var pinError by remember { mutableStateOf(false) }

  if (showPinSetupDialog) {
    SetVaultPinDialog(
      currentPin = vaultPin,
      onDismiss = { showPinSetupDialog = false },
      onSavePin = { newPin ->
        vaultPin = newPin
        isVaultLocked = newPin.isNotEmpty()
        enteredPin = ""
        showPinSetupDialog = false
      }
    )
  }

  // If Vault is locked and PIN is configured, show Lock Screen
  if (isVaultLocked && vaultPin.isNotEmpty()) {
    BankVaultLockScreen(
      enteredLength = enteredPin.length,
      pinError = pinError,
      onDigitClick = { digit ->
        if (enteredPin.length < 4) {
          val next = enteredPin + digit
          enteredPin = next
          pinError = false
          if (next.length == 4) {
            if (next == vaultPin) {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              isVaultLocked = false
              enteredPin = ""
            } else {
              haptic.performHapticFeedback(HapticFeedbackType.Reject)
              pinError = true
              enteredPin = ""
            }
          }
        }
      },
      onBackspace = {
        if (enteredPin.isNotEmpty()) {
          enteredPin = enteredPin.dropLast(1)
        }
      },
      onResetPin = {
        showPinSetupDialog = true
      }
    )
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item(key = "bank_header") {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Bank Details Vault",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = if (vaultPin.isEmpty()) "Account & card storage" else "🔒 PIN Protected Vault",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (vaultPin.isNotEmpty()) {
              FilledTonalButton(
                onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  isVaultLocked = true
                  enteredPin = ""
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("lock_vault_btn")
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Lock Vault", fontSize = 11.sp)
              }
            } else {
              OutlinedButton(
                onClick = { showPinSetupDialog = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("set_vault_pin_btn")
              ) {
                Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Set PIN", fontSize = 11.sp)
              }
            }
          }

          if (vaultPin.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End
            ) {
              TextButton(
                onClick = { showPinSetupDialog = true },
                contentPadding = PaddingValues(0.dp)
              ) {
                Text("Change / Remove PIN", fontSize = 11.sp)
              }
            }
          }
        }
      }
    }

    if (bankAccounts.isEmpty()) {
      item(key = "empty_banks") {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              Icons.Default.AccountBalance,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No bank accounts added",
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Tap the + card button to safely save your bank details",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }
        }
      }
    } else {
      items(bankAccounts, key = { it.id }) { account ->
        BankCardItem(
          account = account,
          onCopy = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onCopyDetails(account)
          },
          onDelete = { onDeleteAccount(account) }
        )
      }
    }
  }
}

@Composable
private fun BankCardItem(
  account: BankAccount,
  onCopy: () -> Unit,
  onDelete: () -> Unit
) {
  var isMasked by remember { mutableStateOf(true) }

  // Modern Credit/Debit card aesthetic with sleek gradient
  Card(
    shape = RoundedCornerShape(22.dp),
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.linearGradient(
            colors = listOf(
              Color(0xFF1E293B),
              Color(0xFF0F172A),
              Color(0xFF334155)
            )
          )
        )
        .padding(20.dp)
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Top row: Bank Name and Type Badge
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.AccountBalance,
              contentDescription = null,
              tint = Color(0xFFFBBF24),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = account.bankName,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = Color.White
            )
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFBBF24).copy(alpha = 0.2f)
          ) {
            Text(
              text = account.accountType.uppercase(),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFFBBF24),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }

        // Account Number with mask toggle
        Column {
          Text(
            text = "ACCOUNT NUMBER",
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.sp
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
          ) {
            val displayedNum = if (isMasked && account.accountNumber.length > 4) {
              "•••• •••• " + account.accountNumber.takeLast(4)
            } else {
              account.accountNumber
            }
            Text(
              text = displayedNum,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
              onClick = { isMasked = !isMasked },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(
                if (isMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Toggle mask",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }

        // Holder Name & IFSC/Routing
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text(
              text = "HOLDER NAME",
              fontSize = 9.sp,
              color = Color.White.copy(alpha = 0.6f),
              letterSpacing = 1.sp
            )
            Text(
              text = account.holderName.ifBlank { "N/A" },
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp,
              color = Color.White
            )
          }

          if (account.ifscOrRouting.isNotBlank()) {
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "IFSC / ROUTING",
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 1.sp
              )
              Text(
                text = account.ifscOrRouting,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color(0xFF67E8F9)
              )
            }
          }
        }

        if (account.balance > 0.0) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Available Balance:",
              fontSize = 11.sp,
              color = Color.White.copy(alpha = 0.7f)
            )
            Text(
              text = CurrencyFormatter.format(account.balance),
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = Color(0xFF4ADE80)
            )
          }
        }

        // Action Buttons: Copy Details & Delete
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilledTonalButton(
            onClick = onCopy,
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = Color.White.copy(alpha = 0.18f),
              contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy Details", fontSize = 12.sp)
          }

          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              Icons.Default.DeleteOutline,
              contentDescription = "Delete account",
              tint = Color(0xFFF87171),
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BankVaultLockScreen(
  enteredLength: Int,
  pinError: Boolean,
  onDigitClick: (String) -> Unit,
  onBackspace: () -> Unit,
  onResetPin: () -> Unit
) {
  val haptic = LocalHapticFeedback.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp, vertical = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      modifier = Modifier
        .size(72.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        Icons.Default.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp)
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Bank Vault Protected",
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = "Enter your 4-digit security PIN to unlock account numbers & balances",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )

    Spacer(modifier = Modifier.height(28.dp))

    // 4 PIN Dots
    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      for (i in 0 until 4) {
        val isFilled = i < enteredLength
        Box(
          modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(
              when {
                pinError -> Color(0xFFEF4444)
                isFilled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
              }
            )
        )
      }
    }

    if (pinError) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "Incorrect PIN. Please try again.",
        color = Color(0xFFEF4444),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
      )
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Numeric Keypad
    val keypad = listOf(
      listOf("1", "2", "3"),
      listOf("4", "5", "6"),
      listOf("7", "8", "9"),
      listOf("Reset", "0", "Del")
    )

    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      keypad.forEach { row ->
        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          row.forEach { key ->
            when (key) {
              "Reset" -> {
                TextButton(
                  onClick = onResetPin,
                  modifier = Modifier.size(64.dp)
                ) {
                  Text("Reset", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
              }
              "Del" -> {
                FilledTonalIconButton(
                  onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBackspace()
                  },
                  modifier = Modifier.size(64.dp)
                ) {
                  Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete digit")
                }
              }
              else -> {
                FilledTonalButton(
                  onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDigitClick(key)
                  },
                  shape = CircleShape,
                  modifier = Modifier.size(64.dp),
                  contentPadding = PaddingValues(0.dp)
                ) {
                  Text(
                    text = key,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SetVaultPinDialog(
  currentPin: String,
  onDismiss: () -> Unit,
  onSavePin: (String) -> Unit
) {
  var pin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (currentPin.isEmpty()) "Set 4-Digit Security PIN" else "Change Vault Security PIN") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = "Choose a 4-digit PIN to safeguard your bank account details.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
          value = pin,
          onValueChange = {
            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
              pin = it
              errorText = null
            }
          },
          label = { Text("Enter 4-Digit PIN") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = confirmPin,
          onValueChange = {
            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
              confirmPin = it
              errorText = null
            }
          },
          label = { Text("Confirm 4-Digit PIN") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        errorText?.let {
          Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (currentPin.isNotEmpty()) {
          TextButton(
            onClick = { onSavePin("") },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Remove PIN Protection", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (pin.length != 4) {
            errorText = "PIN must be exactly 4 digits"
          } else if (pin != confirmPin) {
            errorText = "PINs do not match"
          } else {
            onSavePin(pin)
          }
        },
        enabled = pin.length == 4 && confirmPin.length == 4
      ) {
        Text("Save PIN")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

// -------------------------------------------------------------
// TRIPS & VACATION MANAGER SECTION
// -------------------------------------------------------------
@Composable
private fun TripsSection(
  trips: List<TripRecord>,
  expenses: List<ExpenseRecord>,
  onAddExpenseForTrip: (TripRecord) -> Unit,
  onExportTripPdf: (TripRecord, List<ExpenseRecord>) -> Unit,
  onDeleteTrip: (TripRecord) -> Unit
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item(key = "trips_header") {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = Color(0xFF0D9488).copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Default.FlightTakeoff,
            contentDescription = null,
            tint = Color(0xFF0D9488),
            modifier = Modifier.size(32.dp)
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Trip & Vacation Budgets",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Set budget caps, track live trip expenses, and export PDF statements.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    if (trips.isEmpty()) {
      item(key = "empty_trips") {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              Icons.Default.Luggage,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "No trips created yet",
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Tap the + trip button below to plan your next vacation budget",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }
        }
      }
    } else {
      items(trips, key = { it.id }) { trip ->
        val tripExpenses = remember(expenses, trip.id) {
          expenses.filter { it.tripId == trip.id }
        }
        val spent = remember(tripExpenses) { tripExpenses.sumOf { it.amount } }
        TripCardItem(
          trip = trip,
          spent = spent,
          tripExpenses = tripExpenses,
          onAddExpense = { onAddExpenseForTrip(trip) },
          onExportPdf = { onExportTripPdf(trip, tripExpenses) },
          onDelete = { onDeleteTrip(trip) }
        )
      }
    }
  }
}

@Composable
private fun TripCardItem(
  trip: TripRecord,
  spent: Double,
  tripExpenses: List<ExpenseRecord>,
  onAddExpense: () -> Unit,
  onExportPdf: () -> Unit,
  onDelete: () -> Unit
) {
  val progress = remember(trip.budget, spent) {
    if (trip.budget > 0) (spent / trip.budget).toFloat().coerceIn(0f, 1f) else 0f
  }
  val isOverBudget = spent > trip.budget
  val remaining = trip.budget - spent
  var showLedger by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(Color(0xFF0D9488).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.LocationOn,
              contentDescription = null,
              tint = Color(0xFF0D9488)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = trip.tripName,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
            Text(
              text = trip.destination,
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            Icons.Default.DeleteOutline,
            contentDescription = "Delete trip",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      if (trip.startDate.isNotBlank() || trip.endDate.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${trip.startDate} - ${trip.endDate}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Live Remaining Budget Highlight Card
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isOverBudget) Color(0xFFFEE2E2) else Color(0xFFCCFBF1)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = if (isOverBudget) "OVER BUDGET" else "REMAINING BUDGET",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (isOverBudget) Color(0xFFB91C1C) else Color(0xFF0F766E),
              letterSpacing = 0.8.sp
            )
            Text(
              text = if (isOverBudget) CurrencyFormatter.format(spent - trip.budget) else CurrencyFormatter.format(remaining),
              fontSize = 16.sp,
              fontWeight = FontWeight.ExtraBold,
              color = if (isOverBudget) Color(0xFFB91C1C) else Color(0xFF0F766E)
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "Total Cap: ${CurrencyFormatter.format(trip.budget)}",
              fontSize = 11.sp,
              color = if (isOverBudget) Color(0xFF991B1B) else Color(0xFF115E59)
            )
            Text(
              text = "${(progress * 100).toInt()}% utilized",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (isOverBudget) Color(0xFF991B1B) else Color(0xFF115E59)
            )
          }
        }
      }

      // Budget Progress Bar
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Spent: ${CurrencyFormatter.format(spent)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          Text("Budget: ${CurrencyFormatter.format(trip.budget)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
          color = if (isOverBudget) Color(0xFFEF4444) else Color(0xFF0D9488),
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
      }

      if (trip.notes.isNotBlank()) {
        Text(
          text = trip.notes,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
      }

      // Actions: Add Expense to Trip & Export PDF Report
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onAddExpense,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("+ Log Expense", fontSize = 12.sp)
        }

        FilledTonalButton(
          onClick = onExportPdf,
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF0D9488).copy(alpha = 0.15f),
            contentColor = Color(0xFF0D9488)
          ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Export PDF", fontSize = 12.sp)
        }
      }

      // Expandable Trip Expense Ledger
      TextButton(
        onClick = { showLedger = !showLedger },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Trip Expense Ledger (${tripExpenses.size} items)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Icon(
            if (showLedger) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
        }
      }

      AnimatedVisibility(visible = showLedger) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (tripExpenses.isEmpty()) {
            Text(
              text = "No expenses logged for this trip yet. Tap '+ Log Expense' to record hotel, flight, or meal costs.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            tripExpenses.forEach { exp ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = exp.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                  )
                  Text(
                    text = exp.categoryName,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Text(
                  text = CurrencyFormatter.format(exp.amount),
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  color = Color(0xFFDC2626)
                )
              }
              if (exp != tripExpenses.last()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
              }
            }
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// DIALOGS: ADD CATEGORY, EXPENSE, BANK, TRIP
// -------------------------------------------------------------
@Composable
private fun AddCategoryDialog(
  onDismiss: () -> Unit,
  onConfirm: (name: String, colorHex: String) -> Unit
) {
  var name by remember { mutableStateOf("") }
  val colorPalette = listOf(
    "#EF4444", "#F59E0B", "#10B981", "#3B82F6",
    "#8B5CF6", "#EC4899", "#06B6D4", "#6366F1"
  )
  var selectedColor by remember { mutableStateOf(colorPalette[0]) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Expense Category") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Category Name") },
          placeholder = { Text("e.g. Gym, Pets, Groceries") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Text("Select Color Tag:", style = MaterialTheme.typography.bodySmall)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          colorPalette.forEach { hex ->
            val color = Color(android.graphics.Color.parseColor(hex))
            val isSelected = selectedColor == hex
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .clickable { selectedColor = hex }
                .padding(2.dp),
              contentAlignment = Alignment.Center
            ) {
              if (isSelected) {
                Icon(
                  Icons.Default.Check,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name, selectedColor) },
        enabled = name.isNotBlank()
      ) {
        Text("Add")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
  categories: List<ExpenseCategory>,
  trips: List<TripRecord>,
  initialTripId: Long? = null,
  onDismiss: () -> Unit,
  onConfirm: (title: String, amount: Double, category: String, notes: String, tripId: Long?) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var amountStr by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(categories.firstOrNull()?.name ?: "General") }
  var notes by remember { mutableStateOf("") }
  var selectedTripId by remember { mutableStateOf<Long?>(initialTripId) }
  var categoryDropdownExpanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Log New Expense") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Expense Title") },
          placeholder = { Text("e.g. Grocery store, Uber") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = amountStr,
          onValueChange = { amountStr = it },
          label = { Text("Amount (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        // Category dropdown
        ExposedDropdownMenuBox(
          expanded = categoryDropdownExpanded,
          onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
        ) {
          OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
              .fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = categoryDropdownExpanded,
            onDismissRequest = { categoryDropdownExpanded = false }
          ) {
            categories.forEach { cat ->
              DropdownMenuItem(
                text = { Text(cat.name) },
                onClick = {
                  selectedCategory = cat.name
                  categoryDropdownExpanded = false
                }
              )
            }
          }
        }

        // Link to Trip (Optional)
        if (trips.isNotEmpty()) {
          Text("Assign to Trip (Optional):", style = MaterialTheme.typography.bodySmall)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
              FilterChip(
                selected = selectedTripId == null,
                onClick = { selectedTripId = null },
                label = { Text("None") }
              )
            }
            items(trips) { trip ->
              FilterChip(
                selected = selectedTripId == trip.id,
                onClick = { selectedTripId = trip.id },
                label = { Text(trip.tripName) }
              )
            }
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val amt = amountStr.toDoubleOrNull() ?: 0.0
          onConfirm(title, amt, selectedCategory, notes, selectedTripId)
        },
        enabled = title.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
private fun AddBankDialog(
  onDismiss: () -> Unit,
  onConfirm: (name: String, type: String, num: String, ifsc: String, holder: String, bal: Double, notes: String) -> Unit
) {
  var bankName by remember { mutableStateOf("") }
  var accountType by remember { mutableStateOf("Savings") }
  var accountNumber by remember { mutableStateOf("") }
  var ifsc by remember { mutableStateOf("") }
  var holderName by remember { mutableStateOf("") }
  var balanceStr by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }

  val types = listOf("Savings", "Current", "Salary", "Checking", "FD")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Save Bank Details") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        OutlinedTextField(
          value = bankName,
          onValueChange = { bankName = it },
          label = { Text("Bank Name") },
          placeholder = { Text("e.g. HDFC, Chase, SBI") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = holderName,
          onValueChange = { holderName = it },
          label = { Text("Account Holder Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = accountNumber,
          onValueChange = { accountNumber = it },
          label = { Text("Account Number") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = ifsc,
          onValueChange = { ifsc = it },
          label = { Text("IFSC / Routing / SWIFT Code") },
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Text("Account Type:", style = MaterialTheme.typography.bodySmall)
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          types.forEach { type ->
            FilterChip(
              selected = accountType == type,
              onClick = { accountType = type },
              label = { Text(type, fontSize = 11.sp) }
            )
          }
        }

        OutlinedTextField(
          value = balanceStr,
          onValueChange = { balanceStr = it },
          label = { Text("Current Balance (Optional)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val bal = balanceStr.toDoubleOrNull() ?: 0.0
          onConfirm(bankName, accountType, accountNumber, ifsc, holderName, bal, notes)
        },
        enabled = bankName.isNotBlank() && accountNumber.isNotBlank()
      ) {
        Text("Save Details")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
private fun AddTripDialog(
  onDismiss: () -> Unit,
  onConfirm: (name: String, dest: String, budget: Double, start: String, end: String, notes: String) -> Unit
) {
  var tripName by remember { mutableStateOf("") }
  var destination by remember { mutableStateOf("") }
  var budgetStr by remember { mutableStateOf("") }
  var startDate by remember { mutableStateOf("") }
  var endDate by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create Trip Budget") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        OutlinedTextField(
          value = tripName,
          onValueChange = { tripName = it },
          label = { Text("Trip / Vacation Name") },
          placeholder = { Text("e.g. Goa Trip, Euro Summer") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = destination,
          onValueChange = { destination = it },
          label = { Text("Destination") },
          placeholder = { Text("e.g. Paris, Tokyo, Bali") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = budgetStr,
          onValueChange = { budgetStr = it },
          label = { Text("Total Budget (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          OutlinedTextField(
            value = startDate,
            onValueChange = { startDate = it },
            label = { Text("Start Date") },
            placeholder = { Text("DD/MM/YYYY") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )

          OutlinedTextField(
            value = endDate,
            onValueChange = { endDate = it },
            label = { Text("End Date") },
            placeholder = { Text("DD/MM/YYYY") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Itinerary / Notes (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val b = budgetStr.toDoubleOrNull() ?: 0.0
          onConfirm(tripName, destination, b, startDate, endDate, notes)
        },
        enabled = tripName.isNotBlank() && (budgetStr.toDoubleOrNull() ?: 0.0) > 0.0
      ) {
        Text("Create Trip")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

// -------------------------------------------------------------
// BACKUP & RESTORE MODAL & PASTE DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupRestoreBottomSheet(
  categoriesCount: Int,
  banksCount: Int,
  tripsCount: Int,
  expensesCount: Int,
  replaceExisting: Boolean,
  onReplaceExistingChange: (Boolean) -> Unit,
  onExportToFile: () -> Unit,
  onShareBackup: () -> Unit,
  onCopyJson: () -> Unit,
  onImportFromFile: () -> Unit,
  onPasteJsonClick: () -> Unit,
  onDismiss: () -> Unit
) {
  val haptic = LocalHapticFeedback.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 36.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.CloudSync,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Data Backup & Restore",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Export and import your custom configurations via JSON",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      // Stats Chip / Banner
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(categoriesCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Text("Categories", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(banksCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E3A8A))
            Text("Bank Accounts", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tripsCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0D9488))
            Text("Trips", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(expensesCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDC2626))
            Text("Expenses", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      // EXPORT SECTION
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Data (Backup)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
          Text(
            text = "Generate an offline JSON file containing all your custom categories, bank details, and trips.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Button(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              onExportToFile()
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("export_to_file_btn")
          ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save JSON File to Device")
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onShareBackup()
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("share_backup_btn")
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Share File", fontSize = 12.sp)
            }

            OutlinedButton(
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCopyJson()
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("copy_json_btn")
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Copy JSON", fontSize = 12.sp)
            }
          }
        }
      }

      // IMPORT SECTION
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Data (Restore)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }

          Text(
            text = "Select a JSON backup file to load categories, bank details, and trips back into OmniCalc.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          // Import Strategy Choice
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Import Strategy:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              FilterChip(
                selected = !replaceExisting,
                onClick = { onReplaceExistingChange(false) },
                label = { Text("Merge (Keep current)") },
                modifier = Modifier.weight(1f)
              )
              FilterChip(
                selected = replaceExisting,
                onClick = { onReplaceExistingChange(true) },
                label = { Text("Replace (Overwrite)") },
                modifier = Modifier.weight(1f)
              )
            }
          }

          Button(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              onImportFromFile()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("import_from_file_btn")
          ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select JSON Backup File")
          }

          OutlinedButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onPasteJsonClick()
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("paste_json_btn")
          ) {
            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Paste JSON Text Directly")
          }
        }
      }
    }
  }
}

@Composable
private fun PasteJsonBackupDialog(
  replaceExisting: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (jsonText: String, replace: Boolean) -> Unit
) {
  var jsonInput by remember { mutableStateOf("") }
  var replaceMode by remember { mutableStateOf(replaceExisting) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Paste Backup JSON")
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = "Paste the exported JSON backup code below to restore your categories, bank details, and trips.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
          value = jsonInput,
          onValueChange = { jsonInput = it },
          placeholder = { Text("{\n  \"version\": 1,\n  \"categories\": [...],\n  \"bankAccounts\": [...]\n}") },
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
          textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
          maxLines = 10
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilterChip(
            selected = !replaceMode,
            onClick = { replaceMode = false },
            label = { Text("Merge", fontSize = 11.sp) },
            modifier = Modifier.weight(1f)
          )
          FilterChip(
            selected = replaceMode,
            onClick = { replaceMode = true },
            label = { Text("Replace", fontSize = 11.sp) },
            modifier = Modifier.weight(1f)
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(jsonInput, replaceMode) },
        enabled = jsonInput.isNotBlank()
      ) {
        Text("Restore")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

