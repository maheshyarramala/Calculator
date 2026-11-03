package com.example.ui.screens.home

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TripRecord
import com.example.ui.components.CurrencyFormatter
import com.example.ui.screens.emi.EmiViewModel
import com.example.ui.screens.finance.FinanceHubViewModel
import com.example.ui.screens.finance.FinanceTab
import com.example.ui.screens.utility.UtilityTab
import com.example.ui.screens.utility.UtilityViewModel
import java.text.SimpleDateFormat
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
  val upcomingSub = remember(subscriptions) {
    val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    subscriptions.sortedBy {
      val diff = it.dueDayOfMonth - todayDay
      if (diff >= 0) diff else diff + 31
    }.firstOrNull()
  }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 160.dp),
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
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onLogExpenseClick()
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("home_quick_log_expense_btn")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Log Expense", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
          }

          FilledTonalButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onAddIncomeClick()
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("home_quick_add_income_btn")
          ) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Income", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
          }

          FilledTonalButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onNavigateToUtility(UtilityTab.TIP_SPLIT)
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("home_quick_split_bill_btn")
          ) {
            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Split Bill", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("widget_net_balance")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.AccountBalanceWallet,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Total Net Balance",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ) {
          Text(
            text = "$accountsCount ${if (accountsCount == 1) "Account" else "Accounts"}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = CurrencyFormatter.format(totalBalance),
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(text = "Total Inflow", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = "+${CurrencyFormatter.format(totalIncome)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF10B981)
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(text = "Total Spent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = "-${CurrencyFormatter.format(totalExpense)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFEF4444)
          )
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
// WIDGET 4: RECURRING SUBSCRIPTIONS & BILLS
// -------------------------------------------------------------
@Composable
private fun HomeSubscriptionsWidget(
  upcomingSub: com.example.data.local.entity.SubscriptionRecord?,
  totalCount: Int,
  onMarkPaid: (com.example.data.local.entity.SubscriptionRecord) -> Unit,
  onClick: () -> Unit
) {
  val todayDay = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }

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
        val daysLeft = upcomingSub.dueDayOfMonth - todayDay
        val statusText = when {
          daysLeft == 0 -> "Due Today"
          daysLeft in 1..3 -> "Due in $daysLeft ${if (daysLeft == 1) "day" else "days"}"
          daysLeft < 0 -> "Day ${upcomingSub.dueDayOfMonth} of mo"
          else -> "In $daysLeft days"
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
            color = if (daysLeft in 0..2) Color(0xFFFEF3C7) else Color(0xFFEEF2FF)
          ) {
            Text(
              text = statusText,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (daysLeft in 0..2) Color(0xFFB45309) else Color(0xFF4F46E5),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          FilledTonalButton(
            onClick = { onMarkPaid(upcomingSub) },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(26.dp)
          ) {
            Text("Pay", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
