package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.agecalendar.AgeCalendarScreen
import com.example.ui.screens.agecalendar.AgeCalendarTab
import com.example.ui.screens.agecalendar.AgeCalendarViewModel
import com.example.ui.screens.calculator.CalculatorScreen
import com.example.ui.screens.calculator.CalculatorViewModel
import com.example.ui.screens.emi.EmiScreen
import com.example.ui.screens.emi.EmiViewModel
import com.example.ui.screens.history.HistoryBottomSheet
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.screens.investment.InvestmentScreen
import com.example.ui.screens.investment.InvestmentTab
import com.example.ui.screens.investment.InvestmentViewModel
import com.example.ui.screens.utility.UtilityScreen
import com.example.ui.screens.utility.UtilityTab
import com.example.ui.screens.utility.UtilityViewModel

enum class MainDestination(
  val label: String,
  val icon: ImageVector
) {
  EMI("EMI", Icons.Default.AccountBalance),
  INVESTMENTS("Wealth", Icons.AutoMirrored.Filled.TrendingUp),
  CALCULATOR("Calculator", Icons.Default.Calculate),
  AGE_CALENDAR("Age & Date", Icons.Default.CalendarMonth),
  UTILITIES("Utility", Icons.Default.Widgets)
}

data class CalculatorHubItem(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val iconBgColor: Color,
  val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  modifier: Modifier = Modifier
) {
  var currentDestination by remember { mutableStateOf(MainDestination.EMI) }
  var showHistorySheet by remember { mutableStateOf(false) }
  var showHubSheet by remember { mutableStateOf(false) }

  val emiViewModel: EmiViewModel = viewModel()
  val investmentViewModel: InvestmentViewModel = viewModel()
  val calculatorViewModel: CalculatorViewModel = viewModel()
  val ageCalendarViewModel: AgeCalendarViewModel = viewModel()
  val utilityViewModel: UtilityViewModel = viewModel()
  val historyViewModel: HistoryViewModel = viewModel()

  val haptic = LocalHapticFeedback.current

  // BackHandler: If user is not on EMI tab, back key navigates to EMI tab
  BackHandler(enabled = currentDestination != MainDestination.EMI) {
    currentDestination = MainDestination.EMI
  }

  if (showHistorySheet) {
    HistoryBottomSheet(
      viewModel = historyViewModel,
      onDismiss = { showHistorySheet = false }
    )
  }

  if (showHubSheet) {
    AllCalculatorsHubSheet(
      onDismiss = { showHubSheet = false },
      items = listOf(
        CalculatorHubItem("Loan & EMI", "Amortization & Payoff", Icons.Default.AccountBalance, Color(0xFF4F46E5)) {
          currentDestination = MainDestination.EMI
          showHubSheet = false
        },
        CalculatorHubItem("SIP & Growth", "Wealth compounding", Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF10B981)) {
          currentDestination = MainDestination.INVESTMENTS
          investmentViewModel.selectTab(InvestmentTab.SIP)
          showHubSheet = false
        },
        CalculatorHubItem("Lump Sum", "One-time investment", Icons.Default.Payments, Color(0xFF3B82F6)) {
          currentDestination = MainDestination.INVESTMENTS
          investmentViewModel.selectTab(InvestmentTab.LUMPSUM)
          showHubSheet = false
        },
        CalculatorHubItem("Calculator", "Scientific & Basic", Icons.Default.Calculate, Color(0xFF8B5CF6)) {
          currentDestination = MainDestination.CALCULATOR
          showHubSheet = false
        },
        CalculatorHubItem("Age Calculator", "Exact age & birthday", Icons.Default.Cake, Color(0xFFF43F5E)) {
          currentDestination = MainDestination.AGE_CALENDAR
          ageCalendarViewModel.selectTab(AgeCalendarTab.AGE)
          showHubSheet = false
        },
        CalculatorHubItem("Calendar Date", "Difference & add days", Icons.Default.CalendarMonth, Color(0xFF0EA5E9)) {
          currentDestination = MainDestination.AGE_CALENDAR
          ageCalendarViewModel.selectTab(AgeCalendarTab.DATE_DIFF)
          showHubSheet = false
        },
        CalculatorHubItem("Currency", "Accurate Live Rates", Icons.Default.CurrencyExchange, Color(0xFFD97706)) {
          currentDestination = MainDestination.UTILITIES
          utilityViewModel.selectTab(UtilityTab.CURRENCY)
          showHubSheet = false
        },
        CalculatorHubItem("Unit Converter", "Length, weight, temp", Icons.Default.Straighten, Color(0xFF059669)) {
          currentDestination = MainDestination.UTILITIES
          utilityViewModel.selectTab(UtilityTab.UNIT_CONVERTER)
          showHubSheet = false
        },
        CalculatorHubItem("GST & Tax", "Inclusive & exclusive", Icons.Default.ReceiptLong, Color(0xFF6366F1)) {
          currentDestination = MainDestination.UTILITIES
          utilityViewModel.selectTab(UtilityTab.GST_TAX)
          showHubSheet = false
        },
        CalculatorHubItem("Tip & Split", "Per person share", Icons.Default.Restaurant, Color(0xFFEC4899)) {
          currentDestination = MainDestination.UTILITIES
          utilityViewModel.selectTab(UtilityTab.TIP_SPLIT)
          showHubSheet = false
        }
      )
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { showHubSheet = true }
          ) {
            Text(
              text = "Omni",
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Calc",
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              Icons.Default.ArrowDropDown,
              contentDescription = "Open calculator hub",
              tint = MaterialTheme.colorScheme.primary
            )
          }
        },
        actions = {
          FilledTonalIconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              showHubSheet = true
            },
            modifier = Modifier.testTag("hub_top_button")
          ) {
            Icon(Icons.Default.GridView, contentDescription = "Choose calculator by icon")
          }
          FilledTonalIconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              showHistorySheet = true
            },
            modifier = Modifier.testTag("history_top_button")
          ) {
            Icon(Icons.Default.History, contentDescription = "Saved history")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("bottom_nav_bar")
      ) {
        MainDestination.values().forEach { destination ->
          NavigationBarItem(
            selected = currentDestination == destination,
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              currentDestination = destination
            },
            icon = {
              Icon(destination.icon, contentDescription = destination.label)
            },
            label = {
              Text(
                text = destination.label,
                fontWeight = if (currentDestination == destination) FontWeight.Bold else FontWeight.Normal
              )
            },
            modifier = Modifier.testTag("nav_tab_${destination.name.lowercase()}")
          )
        }
      }
    },
    contentWindowInsets = WindowInsets.systemBars,
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Crossfade(
      targetState = currentDestination,
      label = "screenTransition",
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) { destination ->
      when (destination) {
        MainDestination.EMI -> EmiScreen(viewModel = emiViewModel)
        MainDestination.INVESTMENTS -> InvestmentScreen(viewModel = investmentViewModel)
        MainDestination.CALCULATOR -> CalculatorScreen(viewModel = calculatorViewModel)
        MainDestination.AGE_CALENDAR -> AgeCalendarScreen(viewModel = ageCalendarViewModel)
        MainDestination.UTILITIES -> UtilityScreen(viewModel = utilityViewModel)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllCalculatorsHubSheet(
  items: List<CalculatorHubItem>,
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
        .padding(bottom = 36.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Choose Calculator",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = "Tap any icon to open directly",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(items) { item ->
          Card(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              item.onClick()
            },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(item.iconBgColor),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  item.icon,
                  contentDescription = item.title,
                  tint = Color.White,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = item.title,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = item.subtitle,
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1
                )
              }
            }
          }
        }
      }
    }
  }
}
