package com.example.ui.screens.home

import com.example.ui.theme.performConfiguredHapticFeedback

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.example.data.local.entity.TripRecord
import com.example.data.local.entity.BankAccount
import com.example.data.local.entity.CreditCardBillReminder
import com.example.ui.components.CreditCardBillReminderDialog
import com.example.ui.components.CurrencyFormatter
import com.example.domain.calculator.SubscriptionDueDateCalculator
import com.example.R
import com.example.ui.screens.emi.EmiViewModel
import com.example.ui.screens.finance.FinanceHubViewModel
import com.example.ui.screens.finance.FinanceTab
import com.example.ui.screens.utility.UtilityTab
import com.example.ui.screens.utility.UtilityViewModel
import java.time.Instant
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
  financeViewModel: FinanceHubViewModel,
  emiViewModel: EmiViewModel,
  utilityViewModel: UtilityViewModel,
  onNavigateToEmi: () -> Unit,
  onNavigateToInvest: () -> Unit,
  onNavigateToFinance: (FinanceTab) -> Unit,
  onNavigateToCalc: () -> Unit,
  onNavigateToUtility: (UtilityTab) -> Unit,
  onLogExpenseClick: () -> Unit,
  onAddIncomeClick: () -> Unit,
  onAddTripClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val haptic = LocalHapticFeedback.current
  val totalNetBalance by financeViewModel.totalNetBalance.collectAsState()
  val totalIncome by financeViewModel.totalIncome.collectAsState()
  val totalExpense by financeViewModel.totalExpense.collectAsState()
  val monthlyBudgetLimit by financeViewModel.monthlyBudgetLimit.collectAsState()
  val currentMonthExpenses by financeViewModel.currentMonthExpenses.collectAsState()
  val trips by financeViewModel.trips.collectAsState()
  val expenses by financeViewModel.expenses.collectAsState()
  val subscriptions by financeViewModel.subscriptions.collectAsState()
  val accounts by financeViewModel.accountsWithBalances.collectAsState()
  val creditCardBillReminders by financeViewModel.creditCardBillReminders.collectAsState()
  val context = LocalContext.current
  val creditCardAccounts = remember(accounts) {
    accounts.map { it.account }.filter { it.accountType.equals("Credit Card", ignoreCase = true) }
  }
  var showCreditCardReminderDialog by rememberSaveable { mutableStateOf(false) }
  var editingCreditCardAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
  var pendingCreditCardReminder by remember { mutableStateOf<CreditCardBillReminder?>(null) }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    pendingCreditCardReminder?.let { reminder ->
      val account = creditCardAccounts.firstOrNull { it.id == reminder.accountId }
      if (account != null) {
        financeViewModel.saveCreditCardBillReminder(
          reminder.copy(notificationsEnabled = granted),
          account.bankName
        )
        if (!granted) {
          Toast.makeText(
            context,
            context.getString(R.string.card_bill_notification_permission_denied),
            Toast.LENGTH_LONG
          ).show()
        }
      }
    }
    pendingCreditCardReminder = null
  }
  val saveCreditCardReminder: (CreditCardBillReminder) -> Unit = { reminder ->
    val account = creditCardAccounts.firstOrNull { it.id == reminder.accountId }
    if (account != null) {
      val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
      if (reminder.notificationsEnabled && !notificationsAllowed) {
        pendingCreditCardReminder = reminder
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      } else {
        financeViewModel.saveCreditCardBillReminder(reminder, account.bankName)
      }
    }
  }

  // Format today's date
  val todayFormatted = remember {
    SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date())
  }

  // Active or most recent trip
  val activeTrip = trips.firstOrNull()
  val activeTripExpenses = remember(activeTrip, expenses) {
    if (activeTrip != null) expenses.filter { it.tripId == activeTrip.id } else emptyList()
  }
  val activeTripSpent = remember(activeTripExpenses) { activeTripExpenses.sumOf { it.amount } }

  // Upcoming subscription
  val today = LocalDate.now()
  val upcomingSub = remember(subscriptions, today) {
    subscriptions.minByOrNull { SubscriptionDueDateCalculator.nextDueDate(it, today) }
  }

  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = modifier
      .fillMaxSize()
      .testTag("home_widgets_grid")
  ) {
    // 1. Top Header & Welcome Banner (Full Width)
    item(span = { GridItemSpan(maxLineSpan) }) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Omni Dashboard",
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = todayFormatted,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                Icons.Default.Widgets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Actions Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilledTonalButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              onLogExpenseClick()
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("home_quick_log_expense_btn")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.home_quick_log_expense), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
          }

          FilledTonalButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              onAddIncomeClick()
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("home_quick_add_income_btn")
          ) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.home_quick_add_income), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
          }

          FilledTonalButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              onNavigateToUtility(UtilityTab.TIP_SPLIT)
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("home_quick_split_bill_btn")
          ) {
            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.home_quick_split_bill), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
          }
        }
      }
    }

    // 2. HERO WIDGET: Total Net Balance & Funds Glance (Full Width Span)
    item(span = { GridItemSpan(maxLineSpan) }) {
      HomeNetBalanceHeroWidget(
        totalBalance = totalNetBalance,
        totalIncome = totalIncome,
        totalExpense = totalExpense,
        accountsCount = accounts.size,
        onClick = { onNavigateToFinance(FinanceTab.EXPENSES) }
      )
    }

    // 3. WIDGET: Monthly Budget Meter & Alert
    item {
      HomeMonthlyBudgetWidget(
        spent = currentMonthExpenses,
        limit = monthlyBudgetLimit,
        onClick = { onNavigateToFinance(FinanceTab.EXPENSES) }
      )
    }

    item {
      HomeCreditCardBillsWidget(
        cardAccounts = creditCardAccounts,
        reminders = creditCardBillReminders,
        today = today,
        onAddReminder = {
          editingCreditCardAccountId = null
          showCreditCardReminderDialog = true
        },
        onEditReminder = { reminder ->
          editingCreditCardAccountId = reminder.accountId
          showCreditCardReminderDialog = true
        },
        onMarkPaid = financeViewModel::markCreditCardBillPaid,
        onOpenFinance = { onNavigateToFinance(FinanceTab.EXPENSES) }
      )
    }

    // 4. WIDGET: Trip Manager Speedometer & Burn Rate
    item {
      HomeTripSpeedometerWidget(
        trip = activeTrip,
        spent = activeTripSpent,
        onAddTrip = onAddTripClick,
        onClick = { onNavigateToFinance(FinanceTab.TRIPS) }
      )
    }

    // 5. WIDGET: Recurring Subscriptions & Bills Countdown
    item {
      HomeSubscriptionsWidget(
        upcomingSub = upcomingSub,
        today = today,
        totalCount = subscriptions.size,
        onMarkPaid = { sub -> financeViewModel.markSubscriptionPaid(sub) },
        onClick = { onNavigateToFinance(FinanceTab.SUBSCRIPTIONS) }
      )
    }

    // 6. WIDGET: Quick Loan & EMI Calculator
    item {
      HomeEmiCalculatorWidget(
        onClick = onNavigateToEmi
      )
    }

    // 7. WIDGET: Split Bill & Group Diner Share
    item {
      HomeSplitBillWidget(
        onClick = { onNavigateToUtility(UtilityTab.TIP_SPLIT) }
      )
    }

    // 8. WIDGET: Currency & FX Rates
    item {
      HomeCurrencyWidget(
        onClick = { onNavigateToUtility(UtilityTab.CURRENCY) }
      )
    }

    // 9. WIDGET: Investment & SIP Compounding Growth
    item {
      HomeInvestWidget(
        onClick = onNavigateToInvest
      )
    }

    // 10. WIDGET: Scientific & Basic Calculator
    item {
      HomeQuickCalcWidget(
        onClick = onNavigateToCalc
      )
    }
  }

  if (showCreditCardReminderDialog) {
    CreditCardBillReminderDialog(
      cardAccounts = creditCardAccounts,
      reminders = creditCardBillReminders,
      editingAccountId = editingCreditCardAccountId,
      onDismiss = { showCreditCardReminderDialog = false },
      onSave = { reminder ->
        showCreditCardReminderDialog = false
        saveCreditCardReminder(reminder)
      }
    )
  }
}

// -------------------------------------------------------------
// WIDGET 1: NET BALANCE HERO CARD
// -------------------------------------------------------------
@Composable
private fun HomeNetBalanceHeroWidget(
  totalBalance: Double,
  totalIncome: Double,
  totalExpense: Double,
  accountsCount: Int,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("widget_net_balance")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.linearGradient(
            colors = listOf(
              Color(0xFF1E1B4B), // Deep midnight indigo
              Color(0xFF312E81), // Rich royal indigo
              Color(0xFF4338CA)  // Radiant electric indigo
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
          )
        )
        .padding(18.dp)
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
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
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = Color(0xFFA5B4FC),
                modifier = Modifier.size(17.dp)
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Total Net Balance",
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
              text = "$accountsCount ${if (accountsCount == 1) "Account" else "Accounts"}",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              color = Color.White
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = CurrencyFormatter.format(totalBalance),
          style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
          ),
          color = Color.White
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text(
              text = "Total Inflow",
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = Color.White.copy(alpha = 0.75f)
            )
            Text(
              text = "+${CurrencyFormatter.format(totalIncome)}",
              fontSize = 14.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFF34D399) // Bright radiant emerald
            )
          }

          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "Total Spent",
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = Color.White.copy(alpha = 0.75f)
            )
            Text(
              text = "-${CurrencyFormatter.format(totalExpense)}",
              fontSize = 14.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFFF87171) // Bright radiant coral
            )
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// WIDGET 2: MONTHLY BUDGET METER & ALERT
// -------------------------------------------------------------
@Composable
private fun HomeMonthlyBudgetWidget(
  spent: Double,
  limit: Double,
  onClick: () -> Unit
) {
  val ratio = if (limit > 0) (spent / limit).toFloat() else 0f
  val progress = ratio.coerceIn(0f, 1f)
  val isOver80 = ratio >= 0.8f && ratio < 1.0f
  val isOver100 = ratio >= 1.0f

  val progressColor = when {
    isOver100 -> Color(0xFFEF4444)
    isOver80 -> Color(0xFFF59E0B)
    else -> Color(0xFF10B981)
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 180.dp)
      .clickable(onClick = onClick)
      .testTag("widget_monthly_budget")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(progressColor.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Speed, contentDescription = null, tint = progressColor, modifier = Modifier.size(18.dp))
        }

        if (isOver100) {
          Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFEE2E2)) {
            Text("Over Budget", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
          }
        } else if (isOver80) {
          Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFEF3C7)) {
            Text("80%+ Alert", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Column {
        Text(text = "Monthly Budget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          text = CurrencyFormatter.format(spent),
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "of ${CurrencyFormatter.format(limit)} target",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Column {
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
          color = progressColor,
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "${(ratio * 100).toInt()}% utilized",
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          color = progressColor
        )
      }
    }
  }
}

// -------------------------------------------------------------
// WIDGET 3: ACTIVE TRIP SPEEDOMETER & BURN RATE
// -------------------------------------------------------------
@Composable
private fun HomeTripSpeedometerWidget(
  trip: TripRecord?,
  spent: Double,
  onAddTrip: () -> Unit,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 180.dp)
      .clickable(onClick = if (trip != null) onClick else onAddTrip)
      .testTag("widget_trip_burn_rate")
  ) {
    if (trip == null) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFF0D9488).copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.FlightTakeoff, contentDescription = null, tint = Color(0xFF0D9488), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Trip Manager", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("No active trip", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text("+ Plan Vacation", fontSize = 11.sp, color = Color(0xFF0D9488), fontWeight = FontWeight.Bold)
      }
    } else {
      // Calculate daily rate
      val totalDays = remember(trip.startDate, trip.endDate) {
        calculateTripDays(trip.startDate, trip.endDate)
      }
      val dailyBudget = remember(trip.budget, totalDays) {
        if (totalDays > 0) trip.budget / totalDays else trip.budget / 7.0
      }
      val elapsedDays = remember(trip.startDate) {
        calculateElapsedDays(trip.startDate).coerceAtLeast(1)
      }
      val dailyRate = remember(spent, elapsedDays) {
        spent / elapsedDays
      }
      val burnRatio = if (dailyBudget > 0) (dailyRate / dailyBudget).toFloat() else 0f
      val isPacingHigh = burnRatio > 1.0f

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(Color(0xFF0D9488).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF0D9488), modifier = Modifier.size(18.dp))
          }

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isPacingHigh) Color(0xFFFEE2E2) else Color(0xFFCCFBF1)
          ) {
            Text(
              text = if (isPacingHigh) "Pacing High" else "On Track",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (isPacingHigh) Color(0xFFB91C1C) else Color(0xFF0F766E),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        // Mini Speedometer Gauge Arc Canvas
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.size(90.dp, 50.dp)) {
            val strokeW = 7.dp.toPx()
            val arcSize = Size(size.width, size.height * 1.8f)
            val topLeft = Offset(0f, 0f)

            // Background arc (180 to 360)
            drawArc(
              color = Color.LightGray.copy(alpha = 0.4f),
              startAngle = 180f,
              sweepAngle = 180f,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Active Arc
            val activeSweep = (burnRatio.coerceIn(0f, 1.5f) / 1.5f * 180f)
            val arcColor = when {
              burnRatio > 1.05f -> Color(0xFFEF4444)
              burnRatio > 0.8f -> Color(0xFFF59E0B)
              else -> Color(0xFF10B981)
            }
            drawArc(
              color = arcColor,
              startAngle = 180f,
              sweepAngle = activeSweep,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Needle pointer
            val needleAngle = (180f + activeSweep) * (PI / 180f).toFloat()
            val centerX = size.width / 2f
            val centerY = size.height * 0.9f
            val needleLen = size.width * 0.38f
            val endX = centerX + needleLen * cos(needleAngle)
            val endY = centerY + needleLen * sin(needleAngle)

            drawLine(
              color = Color(0xFF1E293B),
              start = Offset(centerX, centerY),
              end = Offset(endX, endY),
              strokeWidth = 3.dp.toPx(),
              cap = StrokeCap.Round
            )
            drawCircle(color = Color(0xFF1E293B), radius = 4.dp.toPx(), center = Offset(centerX, centerY))
          }
        }

        Column {
          Text(
            text = trip.tripName,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "${CurrencyFormatter.format(dailyRate)}/day",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = if (isPacingHigh) Color(0xFFEF4444) else Color(0xFF10B981)
          )
          Text(
            text = "Budget: ${CurrencyFormatter.format(dailyBudget)}/day",
            fontSize = 9.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

// -------------------------------------------------------------
// CREDIT CARD BILL DUE-DATE ALERTS
// -------------------------------------------------------------
@Composable
private fun HomeCreditCardBillsWidget(
  cardAccounts: List<BankAccount>,
  reminders: List<CreditCardBillReminder>,
  today: LocalDate,
  onAddReminder: () -> Unit,
  onEditReminder: (CreditCardBillReminder) -> Unit,
  onMarkPaid: (CreditCardBillReminder) -> Unit,
  onOpenFinance: () -> Unit
) {
  val accountNames = remember(cardAccounts) { cardAccounts.associate { it.id to it.bankName } }
  val configuredReminders = remember(reminders, accountNames) {
    reminders.filter { it.accountId in accountNames }.sortedBy { it.dueDateEpochDay }
  }
  val hasUnconfiguredCards = cardAccounts.any { account ->
    configuredReminders.none { it.accountId == account.id }
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 180.dp)
      .testTag("widget_credit_card_bills")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          Icons.Default.CreditCard,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(7.dp)
        )
        Text(
          text = stringResource(R.string.card_bill_due_alerts_title),
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          maxLines = 2,
          modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
      }

      if (configuredReminders.isEmpty()) {
        Text(
          text = if (cardAccounts.isEmpty()) {
            stringResource(R.string.card_bill_no_credit_cards)
          } else {
            stringResource(R.string.card_bill_due_alerts_description)
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
          onClick = if (cardAccounts.isEmpty()) onOpenFinance else onAddReminder,
          contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
          Text(
            if (cardAccounts.isEmpty()) stringResource(R.string.card_bill_add_credit_card)
            else stringResource(R.string.card_bill_add_reminder)
          )
        }
      } else {
        configuredReminders.take(2).forEach { reminder ->
          val daysLeft = (reminder.dueDateEpochDay - today.toEpochDay()).toInt()
          val isPaid = reminder.paidDateEpochDay >= 0L
          val alertColor = when {
            isPaid -> Color(0xFF10B981)
            daysLeft <= 3 -> Color(0xFFEF4444)
            else -> MaterialTheme.colorScheme.primary
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = accountNames[reminder.accountId].orEmpty(),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = CurrencyFormatter.format(reminder.amount),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
              color = alertColor,
              maxLines = 1
            )
            Text(
              text = when {
                isPaid -> stringResource(R.string.card_bill_paid)
                daysLeft < 0 -> stringResource(R.string.card_bill_overdue)
                daysLeft == 0 -> stringResource(R.string.card_bill_due_today_short)
                daysLeft == 1 -> stringResource(R.string.card_bill_due_in_one_day)
                else -> stringResource(R.string.card_bill_due_in_days_short, daysLeft)
              },
              style = MaterialTheme.typography.labelSmall,
              color = alertColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              TextButton(
                onClick = { onEditReminder(reminder) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.testTag("edit_card_bill_${reminder.accountId}")
              ) {
                Text(stringResource(R.string.card_bill_edit_reminder), fontSize = 10.sp)
              }
              if (!isPaid) {
                TextButton(
                  onClick = { onMarkPaid(reminder) },
                  contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                  modifier = Modifier.testTag("pay_card_bill_${reminder.accountId}")
                ) {
                  Text(stringResource(R.string.card_bill_mark_paid), fontSize = 10.sp)
                }
              }
            }
          }
        }
        if (hasUnconfiguredCards) {
          TextButton(
            onClick = onAddReminder,
            contentPadding = PaddingValues(horizontal = 0.dp)
          ) {
            Text(stringResource(R.string.card_bill_add_another), fontSize = 11.sp)
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// WIDGET 4: RECURRING SUBSCRIPTIONS & BILLS
// -------------------------------------------------------------
@Composable
private fun HomeSubscriptionsWidget(
  upcomingSub: com.example.data.local.entity.SubscriptionRecord?,
  today: LocalDate,
  totalCount: Int,
  onMarkPaid: (com.example.data.local.entity.SubscriptionRecord) -> Unit,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 180.dp)
      .clickable(onClick = onClick)
      .testTag("widget_subscriptions")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.EventRepeat, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(18.dp))
        }

        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
          Text("$totalCount Bills", fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
      }

      if (upcomingSub == null) {
        Column {
          Text("Recurring Bills", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Text("Track Netflix, Rent, SIP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(modifier = Modifier.height(4.dp))
          Text("+ Add Subscription", fontSize = 11.sp, color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
        }
      } else {
        val nextDueDate = SubscriptionDueDateCalculator.nextDueDate(upcomingSub, today)
        val daysLeft = ChronoUnit.DAYS.between(today, nextDueDate).toInt()
        val isPaidThisCycle = SubscriptionDueDateCalculator.isPaidInCurrentCycle(upcomingSub, today)
        val statusText = when {
          daysLeft == 0 -> stringResource(R.string.subscription_due_today)
          daysLeft == 1 -> stringResource(R.string.subscription_due_in_one_day)
          else -> stringResource(R.string.subscription_due_in_days, daysLeft)
        }

        Column {
          Text(text = "Next Upcoming Bill", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = upcomingSub.name,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = CurrencyFormatter.format(upcomingSub.amount),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = Color(0xFF6366F1)
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isPaidThisCycle) Color(0xFFD1FAE5) else if (daysLeft in 0..2) Color(0xFFFEF3C7) else Color(0xFFEEF2FF)
          ) {
            Text(
              text = if (isPaidThisCycle) "${stringResource(R.string.subscription_paid_this_cycle)} · $statusText" else statusText,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (isPaidThisCycle) Color(0xFF047857) else if (daysLeft in 0..2) Color(0xFFB45309) else Color(0xFF4F46E5),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          FilledTonalButton(
            onClick = { onMarkPaid(upcomingSub) },
            enabled = !isPaidThisCycle,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(26.dp)
          ) {
            Text(if (isPaidThisCycle) "Paid" else "Pay", fontSize = 10.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// WIDGET 5: QUICK LOAN & EMI CALCULATOR
// -------------------------------------------------------------
@Composable
private fun HomeEmiCalculatorWidget(
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 180.dp)
      .clickable(onClick = onClick)
      .testTag("widget_emi_calc")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color(0xFF4F46E5).copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp))
      }

      Column {
        Text("Loan & EMI Calc", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Home, Car & Personal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text("₹10L @ 8.5% = ₹12,399/m", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4F46E5))
      }

      Text(
        text = "Compute Amortization →",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF4F46E5)
      )
    }
  }
}

// -------------------------------------------------------------
// WIDGET 6: SPLIT BILL & DINING SHARE
// -------------------------------------------------------------
@Composable
private fun HomeSplitBillWidget(
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 180.dp)
      .clickable(onClick = onClick)
      .testTag("widget_split_bill")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color(0xFFEC4899).copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(18.dp))
      }

      Column {
        Text("Split Bill & Tip", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Equal, Unequal & Itemized", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text("OCR & UPI Settlement", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEC4899))
      }

      Text(
        text = "Start Group Split →",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFEC4899)
      )
    }
  }
}

// -------------------------------------------------------------
// WIDGET 7: CURRENCY & FX RATES
// -------------------------------------------------------------
@Composable
private fun HomeCurrencyWidget(
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 180.dp)
      .clickable(onClick = onClick)
      .testTag("widget_currency")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color(0xFFD97706).copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
      }

      Column {
        Text("Live Currency", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("USD, EUR, INR, GBP, AED", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text("1 USD ≈ ₹83.45", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD97706))
      }

      Text(
        text = "Convert Currencies →",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFD97706)
      )
    }
  }
}

// -------------------------------------------------------------
// WIDGET 8: SIP & INVESTMENT COMPOUNDING
// -------------------------------------------------------------
@Composable
private fun HomeInvestWidget(
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 180.dp)
      .clickable(onClick = onClick)
      .testTag("widget_invest_sip")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color(0xFF10B981).copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
      }

      Column {
        Text("SIP & Wealth", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Compound Interest & Goals", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text("₹5,000/m @ 12% in 15 yrs", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF10B981))
      }

      Text(
        text = "Plan Wealth Growth →",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF10B981)
      )
    }
  }
}

// -------------------------------------------------------------
// WIDGET 9: QUICK CALCULATOR
// -------------------------------------------------------------
@Composable
private fun HomeQuickCalcWidget(
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 180.dp)
      .clickable(onClick = onClick)
      .testTag("widget_quick_calc")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color(0xFF7C3AED).copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.Calculate, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(18.dp))
      }

      Column {
        Text("Calculator", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Scientific & Basic with History", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Constants, Memory & Trigonometry", fontSize = 9.5.sp, color = Color(0xFF7C3AED))
      }

      Text(
        text = "Open Calculator →",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF7C3AED)
      )
    }
  }
}

// -------------------------------------------------------------
// DATE CALCULATION HELPERS
// -------------------------------------------------------------
private fun calculateTripDays(startDateStr: String, endDateStr: String): Int {
  return try {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val start = sdf.parse(startDateStr.trim())
    val end = sdf.parse(endDateStr.trim())
    if (start != null && end != null) {
      val diffMs = end.time - start.time
      val days = (diffMs / (1000 * 60 * 60 * 24)).toInt() + 1
      days.coerceAtLeast(1)
    } else 7
  } catch (e: Exception) {
    7
  }
}

private fun calculateElapsedDays(startDateStr: String): Int {
  return try {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val start = sdf.parse(startDateStr.trim())
    if (start != null) {
      val now = System.currentTimeMillis()
      val diffMs = now - start.time
      val days = (diffMs / (1000 * 60 * 60 * 24)).toInt() + 1
      days.coerceAtLeast(1)
    } else 1
  } catch (e: Exception) {
    1
  }
}
