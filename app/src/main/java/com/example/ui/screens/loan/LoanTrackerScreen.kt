package com.example.ui.screens.loan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.LoanRecord
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.NumberInputField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanTrackerScreen(
  viewModel: LoanTrackerViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val userMessage by viewModel.userMessage.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(userMessage) {
    userMessage?.let {
      snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
      viewModel.clearUserMessage()
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { viewModel.setShowAddLoanDialog(true) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        modifier = Modifier.testTag("btn_add_new_loan")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add New Loan")
      }
    },
    modifier = modifier.fillMaxSize()
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Hero Dashboard Card
      item {
        LoanDashboardCard(
          activeLoansCount = state.totalActiveLoansCount,
          totalRemainingPrincipal = state.totalPrincipalRemaining,
          monthlyOutflow = state.totalMonthlyEmiOutflow,
          totalPaid = state.totalPrincipalPaid
        )
      }

      // 2. Category Filter Chips
      item {
        LoanCategoryFilterRow(
          selectedCategory = state.selectedCategoryFilter,
          onSelectCategory = { viewModel.setCategoryFilter(it) }
        )
      }

      // 3. Loans Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (state.selectedCategoryFilter != null) "${state.selectedCategoryFilter} Loans" else "Active & Tracked Loans",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = "${state.loans.size} ${if (state.loans.size == 1) "Loan" else "Loans"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // 4. Empty State or Loans List
      if (state.loans.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "No Loans Tracked Yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Tap + to track your Home Loan, Car EMI, or Gadget financing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(16.dp))
              FilledTonalButton(onClick = { viewModel.setShowAddLoanDialog(true) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Your First Loan")
              }
            }
          }
        }
      } else {
        items(state.loans, key = { it.id }) { loan ->
          LoanItemCard(
            loan = loan,
            onClick = { viewModel.selectLoanForDetail(loan) },
            onPayEmi = {
              viewModel.payMonthlyEmi(
                loan = loan,
                selectedBankAccountId = loan.bankAccountId,
                selectedBankAccountName = loan.bankAccountName
              )
            }
          )
        }
      }
    }
  }

  // Add Loan Dialog
  if (state.showAddLoanDialog) {
    AddLoanDialog(
      bankAccounts = state.bankAccounts,
      onDismiss = { viewModel.setShowAddLoanDialog(false) },
      onConfirm = { name, category, principal, rate, tenure, customEmi, startDate, paidCount, notes, bankId, bankName ->
        viewModel.addLoan(
          name = name,
          category = category,
          principal = principal,
          annualRate = rate,
          tenureMonths = tenure,
          customEmi = customEmi,
          startDate = startDate,
          paidEmis = paidCount,
          notes = notes,
          bankAccountId = bankId,
          bankAccountName = bankName
        )
      }
    )
  }

  // Loan Detail Modal
  state.selectedLoanDetail?.let { loan ->
    LoanDetailDialog(
      loan = loan,
      bankAccounts = state.bankAccounts,
      onDismiss = { viewModel.selectLoanForDetail(null) },
      onPayEmi = { bankId, bankName ->
        viewModel.payMonthlyEmi(loan, bankId, bankName)
      },
      onDelete = {
        viewModel.deleteLoan(loan)
      }
    )
  }
}

// -------------------------------------------------------------
// DASHBOARD HERO CARD
// -------------------------------------------------------------
@Composable
private fun LoanDashboardCard(
  activeLoansCount: Int,
  totalRemainingPrincipal: Double,
  monthlyOutflow: Double,
  totalPaid: Double
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.linearGradient(
            colors = listOf(
              Color(0xFF1E1B4B), // Deep midnight indigo
              Color(0xFF312E81), // Royal indigo
              Color(0xFF4338CA)  // Electric indigo
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
          )
        )
        .padding(20.dp)
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.AccountBalance,
                contentDescription = null,
                tint = Color(0xFFA5B4FC),
                modifier = Modifier.size(17.dp)
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Total Principal Remaining",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
              ),
              color = Color(0xFFE0E7FF)
            )
          }

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
          ) {
            Text(
              text = "$activeLoansCount Active",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              color = Color.White
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large Principal Remaining
        Text(
          text = CurrencyFormatter.format(totalRemainingPrincipal),
          style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
          ),
          color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Two Stat Badges: Monthly EMI Outflow & Principal Paid
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Monthly Outflow Badge
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.35f)),
            modifier = Modifier.weight(1f)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(28.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFDC2626).copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  Icons.Default.CalendarMonth,
                  contentDescription = null,
                  tint = Color(0xFFF87171),
                  modifier = Modifier.size(16.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "Monthly EMI Outflow",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color.White.copy(alpha = 0.75f)
                )
                Text(
                  text = CurrencyFormatter.format(monthlyOutflow),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFFF87171)
                )
              }
            }
          }

          // Principal Paid Badge
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.35f)),
            modifier = Modifier.weight(1f)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(28.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF059669).copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = Color(0xFF34D399),
                  modifier = Modifier.size(16.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "Principal Paid",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color.White.copy(alpha = 0.75f)
                )
                Text(
                  text = CurrencyFormatter.format(totalPaid),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFF34D399)
                )
              }
            }
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// CATEGORY FILTER ROW
// -------------------------------------------------------------
@Composable
private fun LoanCategoryFilterRow(
  selectedCategory: String?,
  onSelectCategory: (String?) -> Unit
) {
  val categories = listOf(
    null to "All",
    "HOME" to "Home",
    "CAR" to "Car / Auto",
    "PERSONAL" to "Personal",
    "MOBILE" to "Mobile / Gadget",
    "EDUCATION" to "Education",
    "OTHER" to "Other"
  )

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    categories.forEach { (catKey, label) ->
      val isSelected = selectedCategory == catKey
      FilterChip(
        selected = isSelected,
        onClick = { onSelectCategory(catKey) },
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = if (catKey != null) {
          {
            Icon(
              getLoanCategoryIcon(catKey),
              contentDescription = null,
              modifier = Modifier.size(14.dp)
            )
          }
        } else null,
        shape = RoundedCornerShape(12.dp)
      )
    }
  }
}

// -------------------------------------------------------------
// LOAN ITEM CARD
// -------------------------------------------------------------
@Composable
private fun LoanItemCard(
  loan: LoanRecord,
  onClick: () -> Unit,
  onPayEmi: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("loan_card_${loan.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
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
              .clip(RoundedCornerShape(12.dp))
              .background(getLoanCategoryColor(loan.loanCategory).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              getLoanCategoryIcon(loan.loanCategory),
              contentDescription = null,
              tint = getLoanCategoryColor(loan.loanCategory),
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = loan.loanName,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "${loan.annualInterestRate}% p.a.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              if (loan.bankAccountName.isNotBlank()) {
                Text(
                  text = " • ${loan.bankAccountName}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // Monthly EMI Amount Badge
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = CurrencyFormatter.format(loan.emiAmount),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = "/month",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Progress Bar
      LinearProgressIndicator(
        progress = { loan.progressFraction },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = if (loan.isFullyPaid) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Percentage & EMI counts
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${(loan.progressFraction * 100).toInt()}% Paid (${loan.paidEmisCount}/${loan.tenureMonths} EMIs)",
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "Pending: ${CurrencyFormatter.format(loan.estimatedRemainingPrincipal)}",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = if (loan.isFullyPaid) Color(0xFF10B981) else MaterialTheme.colorScheme.error
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action row: "Pay Monthly EMI" button if active
      if (!loan.isFullyPaid) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          FilledTonalButton(
            onClick = onPayEmi,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Pay Monthly EMI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF10B981).copy(alpha = 0.15f)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Loan Fully Closed", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
            }
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// LOAN DETAIL DIALOG / MODAL
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanDetailDialog(
  loan: LoanRecord,
  bankAccounts: List<BankAccount>,
  onDismiss: () -> Unit,
  onPayEmi: (Long?, String) -> Unit,
  onDelete: () -> Unit
) {
  var selectedBankId by remember { mutableStateOf(loan.bankAccountId) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  val selectedAccount = bankAccounts.find { it.id == selectedBankId }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(loan.loanName, fontWeight = FontWeight.Bold)
        IconButton(onClick = { showDeleteConfirm = true }) {
          Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Loan", tint = MaterialTheme.colorScheme.error)
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
      ) {
        // Breakdown Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          DetailItem("Original Principal", CurrencyFormatter.format(loan.principalAmount))
          DetailItem("Interest Rate", "${loan.annualInterestRate}% p.a.")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          DetailItem("Monthly EMI", CurrencyFormatter.format(loan.emiAmount))
          DetailItem("Tenure", "${loan.tenureMonths} Months")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          DetailItem("Total Interest Payable", CurrencyFormatter.format(loan.totalInterestPayable))
          DetailItem("Total Repayment", CurrencyFormatter.format(loan.totalRepayment))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          DetailItem("Principal Paid", CurrencyFormatter.format(loan.estimatedPrincipalPaid))
          DetailItem("Principal Pending", CurrencyFormatter.format(loan.estimatedRemainingPrincipal))
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = "Payment & Bank Account",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Bank Account Selection if available
        if (bankAccounts.isNotEmpty()) {
          var expanded by remember { mutableStateOf(false) }
          ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
          ) {
            OutlinedTextField(
              value = selectedAccount?.let { "${it.bankName} (${CurrencyFormatter.format(it.balance)})" } ?: "Select payment account",
              onValueChange = {},
              readOnly = true,
              label = { Text("Deduct EMI from") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
              modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
            )
            ExposedDropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false }
            ) {
              bankAccounts.forEach { acc ->
                DropdownMenuItem(
                  text = { Text("${acc.bankName} - Balance: ${CurrencyFormatter.format(acc.balance)}") },
                  onClick = {
                    selectedBankId = acc.id
                    expanded = false
                  }
                )
              }
            }
          }
        }

        if (loan.notes.isNotBlank()) {
          Spacer(modifier = Modifier.height(8.dp))
          Text("Notes: ${loan.notes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    },
    confirmButton = {
      if (!loan.isFullyPaid) {
        Button(
          onClick = {
            onPayEmi(selectedBankId, selectedAccount?.bankName ?: "")
            onDismiss()
          }
        ) {
          Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Pay Monthly EMI")
        }
      } else {
        Button(onClick = onDismiss) {
          Text("Close")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Done")
      }
    }
  )

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Delete Loan?") },
      text = { Text("Are you sure you want to delete '${loan.loanName}'? This will remove its tracker history.") },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            onDelete()
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun DetailItem(label: String, value: String) {
  Column {
    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
  }
}

// -------------------------------------------------------------
// ADD LOAN DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLoanDialog(
  bankAccounts: List<BankAccount>,
  onDismiss: () -> Unit,
  onConfirm: (
    name: String,
    category: String,
    principal: Double,
    rate: Double,
    tenure: Int,
    customEmi: Double?,
    startDate: String,
    paidCount: Int,
    notes: String,
    bankId: Long?,
    bankName: String
  ) -> Unit
) {
  var loanName by rememberSaveable { mutableStateOf("") }
  var loanCategory by rememberSaveable { mutableStateOf("PERSONAL") }
  var principalText by rememberSaveable { mutableStateOf("") }
  var interestRateText by rememberSaveable { mutableStateOf("10.5") }
  var tenureMonthsText by rememberSaveable { mutableStateOf("36") }
  var initialPaidEmisText by rememberSaveable { mutableStateOf("0") }
  var notes by rememberSaveable { mutableStateOf("") }
  var selectedBankId by rememberSaveable { mutableStateOf<Long?>(bankAccounts.firstOrNull()?.id) }

  val principalVal = principalText.toDoubleOrNull() ?: 0.0
  val rateVal = interestRateText.toDoubleOrNull() ?: 0.0
  val tenureVal = tenureMonthsText.toIntOrNull() ?: 0

  val calculatedEmi = remember(principalVal, rateVal, tenureVal) {
    LoanRecord.calculateEmi(principalVal, rateVal, tenureVal)
  }

  val selectedAccount = bankAccounts.find { it.id == selectedBankId }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Loan or EMI Tracker", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = loanName,
          onValueChange = { loanName = it },
          label = { Text("Loan Name (e.g. HDFC Home Loan, Car EMI)") },
          modifier = Modifier.fillMaxWidth()
        )

        // Category Selection
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf("HOME", "CAR", "PERSONAL", "MOBILE", "EDUCATION", "OTHER").forEach { cat ->
            FilterChip(
              selected = loanCategory == cat,
              onClick = { loanCategory = cat },
              label = { Text(cat.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
              shape = RoundedCornerShape(10.dp)
            )
          }
        }

        OutlinedTextField(
          value = principalText,
          onValueChange = { principalText = it },
          label = { Text("Principal Amount (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = interestRateText,
            onValueChange = { interestRateText = it },
            label = { Text("Interest Rate (% p.a.)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = tenureMonthsText,
            onValueChange = { tenureMonthsText = it },
            label = { Text("Tenure (Months)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
          )
        }

        // Live Calculated EMI Preview Card
        if (calculatedEmi > 0) {
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Calculated Monthly EMI:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
              Text(
                CurrencyFormatter.format(calculatedEmi),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }

        OutlinedTextField(
          value = initialPaidEmisText,
          onValueChange = { initialPaidEmisText = it },
          label = { Text("Already Paid EMIs count (Default 0)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (Account No, Lender name, etc.)") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (loanName.isNotBlank() && principalVal > 0 && tenureVal > 0) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            onConfirm(
              loanName,
              loanCategory,
              principalVal,
              rateVal,
              tenureVal,
              calculatedEmi,
              dateFormat.format(Date()),
              initialPaidEmisText.toIntOrNull() ?: 0,
              notes,
              selectedBankId,
              selectedAccount?.bankName ?: ""
            )
          }
        },
        enabled = loanName.isNotBlank() && principalVal > 0 && tenureVal > 0
      ) {
        Text("Save Loan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

private fun getLoanCategoryIcon(category: String): ImageVector {
  return when (category.uppercase()) {
    "HOME" -> Icons.Default.Home
    "CAR" -> Icons.Default.DirectionsCar
    "MOBILE" -> Icons.Default.Smartphone
    "EDUCATION" -> Icons.Default.School
    "PERSONAL" -> Icons.Default.Person
    else -> Icons.Default.AccountBalance
  }
}

private fun getLoanCategoryColor(category: String): Color {
  return when (category.uppercase()) {
    "HOME" -> Color(0xFF3B82F6)
    "CAR" -> Color(0xFFF59E0B)
    "MOBILE" -> Color(0xFF8B5CF6)
    "EDUCATION" -> Color(0xFF06B6D4)
    "PERSONAL" -> Color(0xFF10B981)
    else -> Color(0xFF6366F1)
  }
}
