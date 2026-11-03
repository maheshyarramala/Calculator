package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.agecalendar.AgeCalendarScreen
import com.example.ui.screens.agecalendar.AgeCalendarViewModel
import com.example.ui.screens.calculator.CalculatorScreen
import com.example.ui.screens.calculator.CalculatorViewModel
import com.example.ui.screens.emi.EmiScreen
import com.example.ui.screens.emi.EmiViewModel
import com.example.ui.screens.history.HistoryBottomSheet
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.screens.investment.InvestmentScreen
import com.example.ui.screens.investment.InvestmentViewModel
import com.example.ui.screens.utility.UtilityScreen
import com.example.ui.screens.utility.UtilityViewModel

enum class MainDestination(
  val label: String,
  val icon: ImageVector
) {
  EMI("EMI", Icons.Default.AccountBalance),
  INVESTMENTS("Wealth", Icons.Default.TrendingUp),
  CALCULATOR("Calculator", Icons.Default.Calculate),
  AGE_CALENDAR("Age & Date", Icons.Default.CalendarMonth),
  UTILITIES("Utility", Icons.Default.Widgets)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  modifier: Modifier = Modifier
) {
  var currentDestination by remember { mutableStateOf(MainDestination.EMI) }
  var showHistorySheet by remember { mutableStateOf(false) }

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

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row {
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
          }
        },
        actions = {
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
