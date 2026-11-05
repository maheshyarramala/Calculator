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
import androidx.compose.foundation.verticalScroll
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
import com.example.data.local.entity.AccountWithBalance
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.LoanRecord
import com.example.domain.calculator.LoanPrepaymentCalculator
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
  var loanPendingEmiPayment by remember { mutableStateOf<LoanRecord?>(null) }

  LaunchedEffect(userMessage) {
    userMessage?.let { msg ->
      if (msg.startsWith("Paid EMI #")) {
        val result = snackbarHostState.showSnackbar(
          message = msg,
          actionLabel = "UNDO",
          duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
          viewModel.undoLastPaidEmi()
        }
      } else {
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
      }
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
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
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
            onPayEmi = { loanPendingEmiPayment = loan },
            onUndoEmi = {
              viewModel.undoLastPaidEmi(loan.id)
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
      onConfirm = { name, category, principal, rate, tenure, customEmi, startDate, paidCount, notes, bankId, bankName, dueDayOfMonth ->
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
          bankAccountName = bankName,
          dueDayOfMonth = dueDayOfMonth
        )
      }
    )
  }

  loanPendingEmiPayment?.let { loan ->
    AlertDialog(
      onDismissRequest = { loanPendingEmiPayment = null },
      title = { Text("Select Account for EMI") },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("Choose where to deduct ${CurrencyFormatter.format(loan.emiAmount)}:")
          if (state.bankAccounts.isEmpty()) {
            Text(
              "No accounts are available. You can still record this EMI without an account.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
              onClick = {
                viewModel.payMonthlyEmi(loan, null)
                loanPendingEmiPayment = null
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("Pay without an account", maxLines = 1, softWrap = false)
            }
          } else {
            state.bankAccounts.forEach { account ->
              OutlinedButton(
                onClick = {
                  viewModel.payMonthlyEmi(loan, account.account.id)
                  loanPendingEmiPayment = null
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalAlignment = Alignment.Start
                ) {
                  Text(
                    account.account.bankName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    "Balance: ${CurrencyFormatter.format(account.currentBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { loanPendingEmiPayment = null }) { Text("Cancel") }
      }
    )
  }

  // Loan Detail Modal
  state.selectedLoanDetail?.let { loan ->
    LoanDetailDialog(
      loan = loan,
      bankAccounts = state.bankAccounts,
      onDismiss = { viewModel.selectLoanForDetail(null) },
      onPayEmi = { bankId, _ ->
        viewModel.payMonthlyEmi(loan, bankId)
      },
      onUndoEmi = {
        viewModel.undoLastPaidEmi(loan.id)
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
        .padding(16.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
          ) {
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
              style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
              ),
              color = Color(0xFFE0E7FF),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
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
              color = Color.White,
              maxLines = 1,
              softWrap = false
            )
          }
        }

        Text(
          text = CurrencyFormatter.format(totalRemainingPrincipal),
          style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
          ),
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.35f)),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(
                text = "Monthly EMI Outflow",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = CurrencyFormatter.format(monthlyOutflow),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFF87171),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.35f)),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(
                text = "Principal Paid",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = CurrencyFormatter.format(totalPaid),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF34D399),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
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
// ENHANCED LOAN ITEM CARD
// -------------------------------------------------------------
@Composable
private fun LoanItemCard(
  loan: LoanRecord,
  onClick: () -> Unit,
  onPayEmi: () -> Unit,
  onUndoEmi: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
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
      // Top Row: Category Icon, Title, Status & Interest Rate Chips, and EMI Amount
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Row(
          modifier = Modifier.weight(1f).padding(end = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
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
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = loan.loanName,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              // Status Badge
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (loan.isFullyPaid) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
              ) {
                Text(
                  text = if (loan.isFullyPaid) "Closed" else "Active • ${loan.remainingEmisCount} left",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (loan.isFullyPaid) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  maxLines = 1,
                  softWrap = false
                )
              }

              // Interest Rate Chip
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
              ) {
                Text(
                  text = "${loan.annualInterestRate}% p.a.",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  maxLines = 1,
                  softWrap = false
                )
              }

              Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
              ) {
                Text(
                  text = "Due day ${loan.dueDayOfMonth}",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  maxLines = 1,
                  softWrap = false
                )
              }

              // Linked Account Chip
              if (loan.bankAccountName.isNotBlank()) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                  Text(
                    text = loan.bankAccountName,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }
          }
        }

        // Monthly EMI Amount Badge
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = CurrencyFormatter.format(loan.emiAmount),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false
          )
          Text(
            text = "/month",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Exact Breakdown of EMIs Paid vs Pending
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.DoneAll,
            contentDescription = null,
            tint = if (loan.isFullyPaid) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "${loan.paidEmisCount}/${loan.tenureMonths} EMIs Paid",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
          Text(
            text = "${(loan.progressFraction * 100).toInt()}% Paid • ${loan.remainingEmisCount} EMIs Pending",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (loan.isFullyPaid) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Progress Bar representation
      LinearProgressIndicator(
        progress = { loan.progressFraction },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = if (loan.isFullyPaid) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Dual Principal Reporting Cards: Principal Paid vs. Remaining Principal
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Principal Paid Card (Emerald)
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF10B981).copy(alpha = 0.10f),
          border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Principal Paid",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF047857)
              )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = CurrencyFormatter.format(loan.cumulativePrincipalPaid),
              fontSize = 13.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFF059669)
            )
          }
        }

        // Remaining Principal Card (Rose/Coral)
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (loan.isFullyPaid) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFFEF4444).copy(alpha = 0.08f),
          border = BorderStroke(1.dp, if (loan.isFullyPaid) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f)),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                if (loan.isFullyPaid) Icons.Default.Check else Icons.Default.Pending,
                contentDescription = null,
                tint = if (loan.isFullyPaid) Color(0xFF059669) else Color(0xFFDC2626),
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Remaining Principal",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (loan.isFullyPaid) Color(0xFF047857) else Color(0xFFB91C1C)
              )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = CurrencyFormatter.format(loan.remainingPrincipal),
              fontSize = 13.sp,
              fontWeight = FontWeight.ExtraBold,
              color = if (loan.isFullyPaid) Color(0xFF059669) else Color(0xFFDC2626)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action row: "Undo EMI" & "Pay Monthly EMI"
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (loan.paidEmisCount > 0) {
          OutlinedButton(
            onClick = onUndoEmi,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              "Undo EMI (#${loan.paidEmisCount})",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false
            )
          }
        } else {
          Text(
            text = "Tenure: ${loan.tenureMonths} Mos (${loan.startDate})",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
        }

        if (!loan.isFullyPaid) {
          FilledTonalButton(
            onClick = onPayEmi,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Pay Monthly EMI", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
          }
        } else {
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
  bankAccounts: List<AccountWithBalance>,
  onDismiss: () -> Unit,
  onPayEmi: (Long?, String) -> Unit,
  onUndoEmi: () -> Unit,
  onDelete: () -> Unit
) {
  var selectedBankId by remember { mutableStateOf(loan.bankAccountId) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  val selectedAccount = bankAccounts.find { it.account.id == selectedBankId }

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
          .verticalScroll(rememberScrollState())
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
          DetailItem("Principal Paid", CurrencyFormatter.format(loan.cumulativePrincipalPaid))
          DetailItem("Principal Pending", CurrencyFormatter.format(loan.remainingPrincipal))
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        if (!loan.isFullyPaid) {
          LoanPrepaymentSimulatorSection(loan = loan)
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
              value = selectedAccount?.let { "${it.account.bankName} (${CurrencyFormatter.format(it.currentBalance)})" } ?: "Select payment account",
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
                  text = { Text("${acc.account.bankName} - Balance: ${CurrencyFormatter.format(acc.currentBalance)}") },
                  onClick = {
                    selectedBankId = acc.account.id
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
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (loan.paidEmisCount > 0) {
          OutlinedButton(
            onClick = {
              onUndoEmi()
              onDismiss()
            }
          ) {
            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Undo #${loan.paidEmisCount}")
          }
        }
        if (!loan.isFullyPaid) {
          Button(
            onClick = {
              onPayEmi(selectedBankId, selectedAccount?.account?.bankName ?: "")
              onDismiss()
            },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
          ) {
            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Pay Monthly EMI", maxLines = 1, softWrap = false, fontSize = 12.sp)
          }
        } else {
          Button(onClick = onDismiss) {
            Text("Close")
          }
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
  bankAccounts: List<AccountWithBalance>,
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
    bankName: String,
    dueDayOfMonth: Int
  ) -> Unit
) {
  var loanName by rememberSaveable { mutableStateOf("") }
  var loanCategory by rememberSaveable { mutableStateOf("PERSONAL") }
  var principalText by rememberSaveable { mutableStateOf("") }
  var interestRateText by rememberSaveable { mutableStateOf("10.5") }
  var tenureMonthsText by rememberSaveable { mutableStateOf("36") }
  var initialPaidEmisText by rememberSaveable { mutableStateOf("0") }
  var dueDayOfMonthText by rememberSaveable { mutableStateOf("1") }
  var notes by rememberSaveable { mutableStateOf("") }
  var selectedBankId by rememberSaveable { mutableStateOf<Long?>(bankAccounts.firstOrNull()?.account?.id) }

  val principalVal = principalText.toDoubleOrNull() ?: 0.0
  val rateVal = interestRateText.toDoubleOrNull() ?: 0.0
  val tenureVal = tenureMonthsText.toIntOrNull() ?: 0
  val dueDayOfMonth = dueDayOfMonthText.toIntOrNull()?.takeIf { it in 1..31 }

  val calculatedEmi = remember(principalVal, rateVal, tenureVal) {
    LoanRecord.calculateEmi(principalVal, rateVal, tenureVal)
  }

  val selectedAccount = bankAccounts.find { it.account.id == selectedBankId }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Loan or EMI Tracker", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp)
          .verticalScroll(rememberScrollState())
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

        // Optional Linked Bank Account
        if (bankAccounts.isNotEmpty()) {
          var expanded by remember { mutableStateOf(false) }
          ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
          ) {
            OutlinedTextField(
              value = selectedAccount?.let { "${it.account.bankName} (${CurrencyFormatter.format(it.currentBalance)})" } ?: "Select Linked Account (Optional)",
              onValueChange = {},
              readOnly = true,
              label = { Text("Deduct EMI from Account") },
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
                  text = { Text("${acc.account.bankName} (${CurrencyFormatter.format(acc.currentBalance)})") },
                  onClick = {
                    selectedBankId = acc.account.id
                    expanded = false
                  }
                )
              }
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
          value = dueDayOfMonthText,
          onValueChange = {
            if (it.length <= 2 && it.all { char -> char.isDigit() }) dueDayOfMonthText = it
          },
          label = { Text("EMI Due Date (Day of Month, 1-31)") },
          placeholder = { Text("e.g. 5 or 10") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          isError = dueDayOfMonth == null,
          supportingText = if (dueDayOfMonth == null) {
            { Text("Enter a day from 1 to 31") }
          } else {
            null
          }
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
              selectedAccount?.account?.bankName ?: "",
              dueDayOfMonth ?: return@Button
            )
          }
        },
        enabled = loanName.isNotBlank() && principalVal > 0 && tenureVal > 0 && dueDayOfMonth != null
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

@Composable
private fun LoanPrepaymentSimulatorSection(loan: LoanRecord) {
  var prepayText by remember { mutableStateOf("50000") }
  val prepayAmount = prepayText.toDoubleOrNull() ?: 0.0
  val remainingMonths = (loan.tenureMonths - loan.paidEmisCount).coerceAtLeast(1)
  val currentPrincipal = loan.remainingPrincipal
  val result = remember(loan, prepayAmount) {
    LoanPrepaymentCalculator.calculate(
      remainingPrincipal = currentPrincipal,
      annualInterestRate = loan.annualInterestRate,
      monthlyEmi = loan.emiAmount,
      remainingMonths = remainingMonths,
      extraPrepayment = prepayAmount
    )
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Default.Speed,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            "Prepayment & Foreclosure Simulator",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            "Simulate prepaying extra lumpsum towards principal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Quick chips
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf(
          25000.0 to "+ ₹25,000",
          50000.0 to "+ ₹50,000",
          100000.0 to "+ ₹1 Lakh",
          200000.0 to "+ ₹2 Lakhs"
        ).forEach { (amt, label) ->
          AssistChip(
            onClick = { prepayText = "%.0f".format(amt) },
            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedTextField(
        value = prepayText,
        onValueChange = { prepayText = it },
        label = { Text("Extra Prepayment Amount (₹)") },
        leadingIcon = { Text("₹", fontWeight = FontWeight.Bold) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Impact Result Cards
      if (!result.isValid) {
        Text(
          "Unable to estimate savings because the current EMI does not pay off the remaining balance within the tracked tenure.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error
        )
      } else if (result.isForeclosed) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF10B981).copy(alpha = 0.18f),
          border = BorderStroke(1.dp, Color(0xFF10B981)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                "🎉 Loan Fully Foreclosed & Closed!",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981),
                fontSize = 13.sp
              )
              Text(
                "You become debt-free immediately and save ${CurrencyFormatter.format(result.interestSaved)} in estimated interest!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(
                "Tenure Reduced By",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                if (result.monthsSaved > 0) "${result.monthsSaved} Months" else "0 Months",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = if (result.monthsSaved > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                )
              )
              Text(
                "New tenure: ${result.monthsAfter} mos",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(
                "Total Interest Saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                CurrencyFormatter.format(result.interestSaved),
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = if (result.interestSaved > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                )
              )
              Text(
                "Pure direct savings",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "Estimate assumes the same EMI and excludes lender prepayment fees or rate changes.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
