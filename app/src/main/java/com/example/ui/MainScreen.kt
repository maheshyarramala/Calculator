package com.example.ui

import com.example.ui.theme.performConfiguredHapticFeedback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.screens.agecalendar.AgeCalendarScreen
import com.example.ui.screens.agecalendar.AgeCalendarTab
import com.example.ui.screens.agecalendar.AgeCalendarViewModel
import com.example.ui.screens.calculator.CalculatorScreen
import com.example.ui.screens.calculator.CalculatorViewModel
import com.example.ui.screens.emi.EmiScreen
import com.example.ui.screens.emi.EmiViewModel
import com.example.ui.screens.finance.FinanceHubScreen
import com.example.ui.screens.finance.FinanceHubViewModel
import com.example.ui.screens.finance.FinanceTab
import com.example.ui.screens.history.HistoryBottomSheet
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.screens.interest.InterestCalculatorScreen
import com.example.ui.screens.interest.InterestCalculatorViewModel
import com.example.ui.screens.investment.InvestmentScreen
import com.example.ui.screens.investment.InvestmentTab
import com.example.ui.screens.investment.InvestmentViewModel
import com.example.ui.screens.utility.UtilityScreen
import com.example.ui.screens.utility.UtilityTab
import com.example.ui.screens.utility.UtilityViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.components.AppInfoModalDialog
import com.example.ui.components.AppInfoType

import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import com.example.ui.components.CurrencyFormatter
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.HapticStrength
import com.example.ui.theme.ThemeManager

enum class MainDestination(
  val labelRes: Int,
  val icon: ImageVector,
  val isBottomTab: Boolean = true
) {
  FINANCE(R.string.navigation_finance, Icons.Default.Savings),
  EMI(R.string.navigation_emi, Icons.Default.AccountBalance),
  CALCULATOR(R.string.navigation_calculator, Icons.Default.Calculate),
  INVESTMENTS(R.string.navigation_investments, Icons.AutoMirrored.Filled.TrendingUp),
  UTILITIES(R.string.navigation_utility, Icons.Default.Widgets),
  HOME(R.string.navigation_home, Icons.Default.Dashboard, isBottomTab = false),
  INTEREST(R.string.navigation_interest, Icons.Default.Percent, isBottomTab = false),
  AGE_CALENDAR(R.string.navigation_age_date, Icons.Default.CalendarMonth, isBottomTab = false)
}

private val mainDestinationSaver = Saver<MainDestination, String>(
  save = { it.name },
  restore = { savedName ->
    MainDestination.values()
      .firstOrNull { it.name == savedName && it.isBottomTab }
      ?: MainDestination.FINANCE
  }
)

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
  var currentDestination by rememberSaveable(stateSaver = mainDestinationSaver) {
    mutableStateOf(MainDestination.FINANCE)
  }
  var showHistorySheet by rememberSaveable { mutableStateOf(false) }
  var showFavoritesSheet by rememberSaveable { mutableStateOf(false) }
  var showHubSheet by rememberSaveable { mutableStateOf(false) }
  var showThemeSheet by rememberSaveable { mutableStateOf(false) }
  var menuExpanded by remember { mutableStateOf(false) }
  var showAppInfoDialog by remember { mutableStateOf<AppInfoType?>(null) }
  var isPrivacyMode by remember { mutableStateOf(CurrencyFormatter.isPrivacyModeEnabled) }
  val saveableStateHolder = rememberSaveableStateHolder()

  val emiViewModel: EmiViewModel = viewModel()
  val investmentViewModel: InvestmentViewModel = viewModel()
  val financeViewModel: FinanceHubViewModel = viewModel()
  val interestViewModel: InterestCalculatorViewModel = viewModel()
  val calculatorViewModel: CalculatorViewModel = viewModel()
  val ageCalendarViewModel: AgeCalendarViewModel = viewModel()
  val utilityViewModel: UtilityViewModel = viewModel()
  val historyViewModel: HistoryViewModel = viewModel()

  val haptic = LocalHapticFeedback.current

  showAppInfoDialog?.let { info ->
    AppInfoModalDialog(
      type = info,
      onDismiss = { showAppInfoDialog = null }
    )
  }

  // BackHandler: return to the first bottom tab from secondary destinations.
  BackHandler(enabled = currentDestination != MainDestination.FINANCE) {
    currentDestination = MainDestination.FINANCE
  }

  if (showThemeSheet) {
    ThemeSettingsBottomSheet(
      onDismiss = { showThemeSheet = false }
    )
  }

  if (showFavoritesSheet) {
    com.example.ui.screens.history.FavoritesBottomSheet(
      viewModel = historyViewModel,
      onOpenTool = { key ->
        when (key) {
          "EMI" -> currentDestination = MainDestination.EMI
          "INVEST" -> currentDestination = MainDestination.INVESTMENTS
          "EXPENSE" -> currentDestination = MainDestination.FINANCE
          "CALC" -> currentDestination = MainDestination.CALCULATOR
        }
      },
      onDismiss = { showFavoritesSheet = false }
    )
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
        CalculatorHubItem("Interest Calc", "All 5 Interest Methods", Icons.Default.Percent, Color(0xFF8B5CF6)) {
          currentDestination = MainDestination.INTEREST
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
        CalculatorHubItem("Expenses & Accounts", "Budget & bank balances", Icons.Default.Savings, Color(0xFF10B981)) {
          currentDestination = MainDestination.FINANCE
          financeViewModel.selectTab(FinanceTab.EXPENSES)
          showHubSheet = false
        },
        CalculatorHubItem("Credit Cards", "Cards, limits & bill alerts", Icons.Default.CreditCard, Color(0xFF6366F1)) {
          currentDestination = MainDestination.FINANCE
          financeViewModel.selectTab(FinanceTab.CREDIT_CARDS)
          showHubSheet = false
        },
        CalculatorHubItem("Lend & Borrow", "Udhaar & personal debt ledger", Icons.Default.SwapHoriz, Color(0xFF059669)) {
          currentDestination = MainDestination.FINANCE
          financeViewModel.selectTab(FinanceTab.DEBTS)
          showHubSheet = false
        },
        CalculatorHubItem("Trip Manager", "Vacation budgets & caps", Icons.Default.FlightTakeoff, Color(0xFF0D9488)) {
          currentDestination = MainDestination.FINANCE
          financeViewModel.selectTab(FinanceTab.TRIPS)
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
        CalculatorHubItem("Calculator", "Scientific & Basic", Icons.Default.Calculate, Color(0xFF7C3AED)) {
          currentDestination = MainDestination.CALCULATOR
          showHubSheet = false
        },
        CalculatorHubItem("Currency", "Accurate Live Rates", Icons.Default.CurrencyExchange, Color(0xFFD97706)) {
          currentDestination = MainDestination.UTILITIES
          utilityViewModel.selectTab(UtilityTab.CURRENCY)
          showHubSheet = false
        },
        CalculatorHubItem("Split Bill", "Group expense share", Icons.Default.Group, Color(0xFFEC4899)) {
          currentDestination = MainDestination.UTILITIES
          utilityViewModel.selectTab(UtilityTab.TIP_SPLIT)
          showHubSheet = false
        },
        CalculatorHubItem("Unit Converter", "Length, weight, temp", Icons.Default.Straighten, Color(0xFF059669)) {
          currentDestination = MainDestination.UTILITIES
          utilityViewModel.selectTab(UtilityTab.UNIT_CONVERTER)
          showHubSheet = false
        },
        CalculatorHubItem("GST & Tax", "Inclusive & exclusive", Icons.AutoMirrored.Filled.ReceiptLong, Color(0xFF6366F1)) {
          currentDestination = MainDestination.UTILITIES
          utilityViewModel.selectTab(UtilityTab.GST_TAX)
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
              color = MaterialTheme.colorScheme.primary,
              fontSize = 20.sp
            )
            Text(
              text = "Calc",
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 20.sp
            )
          }
        },
        actions = {
          Box {
            IconButton(
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                menuExpanded = true
              },
              modifier = Modifier.testTag("top_overflow_menu_button")
            ) {
              Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false }
            ) {
              DropdownMenuItem(
                text = { Text("Favorites") },
                onClick = {
                  menuExpanded = false
                  showFavoritesSheet = true
                }
              )
              DropdownMenuItem(
                text = { Text("About") },
                onClick = {
                  menuExpanded = false
                  showAppInfoDialog = AppInfoType.ABOUT
                }
              )
              DropdownMenuItem(
                text = { Text("Terms & Conditions") },
                onClick = {
                  menuExpanded = false
                  showAppInfoDialog = AppInfoType.TERMS
                }
              )
              DropdownMenuItem(
                text = { Text("Privacy Policy") },
                onClick = {
                  menuExpanded = false
                  showAppInfoDialog = AppInfoType.PRIVACY
                }
              )
              DropdownMenuItem(
                text = { Text("Feedback") },
                onClick = {
                  menuExpanded = false
                  showAppInfoDialog = AppInfoType.CONTACT
                }
              )
              DropdownMenuItem(
                text = { Text("Contact Us") },
                onClick = {
                  menuExpanded = false
                  showAppInfoDialog = AppInfoType.CONTACT
                }
              )
              DropdownMenuItem(
                text = { Text("Donate") },
                onClick = {
                  menuExpanded = false
                  showAppInfoDialog = AppInfoType.DONATE
                }
              )
            }
          }

          // History Button (Dedicated section)
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              showHistorySheet = true
            },
            modifier = Modifier.testTag("history_top_button")
          ) {
            Icon(Icons.Default.History, contentDescription = "Saved history")
          }

          // Theme Settings Button
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              showThemeSheet = true
            },
            modifier = Modifier.testTag("theme_settings_top_btn")
          ) {
            Icon(Icons.Default.Palette, contentDescription = "Theme and Customization")
          }

          // Hub Button
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              showHubSheet = true
            },
            modifier = Modifier.testTag("hub_top_button")
          ) {
            Icon(Icons.Default.GridView, contentDescription = "Choose calculator by icon")
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
        MainDestination.values().filter { it.isBottomTab }.forEach { destination ->
          NavigationBarItem(
            selected = currentDestination == destination,
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              currentDestination = destination
            },
            icon = {
              Icon(destination.icon, contentDescription = stringResource(destination.labelRes))
            },
            label = {
              Text(
                text = stringResource(destination.labelRes),
                fontWeight = if (currentDestination == destination) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
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
      saveableStateHolder.SaveableStateProvider(destination.name) {
        when (destination) {
        MainDestination.HOME -> HomeScreen(
          financeViewModel = financeViewModel,
          emiViewModel = emiViewModel,
          utilityViewModel = utilityViewModel,
          onNavigateToEmi = { currentDestination = MainDestination.EMI },
          onNavigateToInvest = { currentDestination = MainDestination.INVESTMENTS },
          onNavigateToFinance = { tab ->
            currentDestination = MainDestination.FINANCE
            financeViewModel.selectTab(tab)
          },
          onNavigateToCalc = { currentDestination = MainDestination.CALCULATOR },
          onNavigateToUtility = { tab ->
            currentDestination = MainDestination.UTILITIES
            utilityViewModel.selectTab(tab)
          },
          onLogExpenseClick = {
            currentDestination = MainDestination.FINANCE
            financeViewModel.selectTab(FinanceTab.EXPENSES)
            financeViewModel.setShowAddExpense(true)
          },
          onAddIncomeClick = {
            currentDestination = MainDestination.FINANCE
            financeViewModel.selectTab(FinanceTab.EXPENSES)
            financeViewModel.setShowAddIncome(true)
          },
          onAddTripClick = {
            currentDestination = MainDestination.FINANCE
            financeViewModel.selectTab(FinanceTab.TRIPS)
            financeViewModel.setShowAddTrip(true)
          }
        )
        MainDestination.EMI -> EmiScreen(viewModel = emiViewModel)
        MainDestination.INVESTMENTS -> InvestmentScreen(viewModel = investmentViewModel)
        MainDestination.FINANCE -> FinanceHubScreen(viewModel = financeViewModel)
        MainDestination.CALCULATOR -> CalculatorScreen(viewModel = calculatorViewModel)
        MainDestination.UTILITIES -> UtilityScreen(viewModel = utilityViewModel)
        MainDestination.INTEREST -> InterestCalculatorScreen(viewModel = interestViewModel)
        MainDestination.AGE_CALENDAR -> AgeCalendarScreen(viewModel = ageCalendarViewModel)
        }
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
  var searchQuery by rememberSaveable { mutableStateOf("") }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val filteredItems = remember(items, searchQuery) {
    if (searchQuery.isBlank()) items
    else items.filter {
      it.title.contains(searchQuery, ignoreCase = true) ||
          it.subtitle.contains(searchQuery, ignoreCase = true)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.fillMaxHeight(),
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp)
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

      Spacer(modifier = Modifier.height(12.dp))

      // Search bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search by name (tax, car, currency, age...)", fontSize = 13.sp) },
        leadingIcon = {
          Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingIcon = {
          if (searchQuery.isNotBlank()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("hub_search_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      if (filteredItems.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No calculator matching \"$searchQuery\"",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
          )
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
          items(filteredItems) { item ->
            Card(
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingsBottomSheet(
  onDismiss: () -> Unit
) {
  val themeState by ThemeManager.state.collectAsState()
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
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = stringResource(R.string.theme_settings_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = stringResource(R.string.theme_settings_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      // 1. Dark Mode Options
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.theme_mode_label),
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth()
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          AppThemeMode.values().forEach { mode ->
            val isSelected = themeState.themeMode == mode
            FilterChip(
              selected = isSelected,
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                ThemeManager.setThemeMode(mode)
              },
              label = {
                Text(
                  when (mode) {
                    AppThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                    AppThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                    AppThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
                    AppThemeMode.AMOLED -> stringResource(R.string.theme_mode_amoled)
                  },
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
              },
              leadingIcon = if (mode == AppThemeMode.AMOLED) {
                { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(14.dp)) }
              } else null,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      // 2. Accent Palette
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.theme_accent_label),
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth()
        )
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(AccentTheme.values().toList()) { accent ->
            val isSelected = themeState.accentTheme == accent
            Card(
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                ThemeManager.setAccentTheme(accent)
              },
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(
                containerColor = if (isSelected) accent.primaryLight.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              ),
              modifier = Modifier
                .width(100.dp)
                .then(
                  if (isSelected) Modifier.border(2.dp, accent.primaryLight, RoundedCornerShape(14.dp))
                  else Modifier
                )
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.primaryLight)
                )
                Text(
                  text = accent.title.replace(" ", "\n"),
                  fontSize = 10.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurface,
                  lineHeight = 12.sp
                )
              }
            }
          }
        }
      }

      // 3. Tactile Vibration Haptics
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.theme_haptics_label),
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth()
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          HapticStrength.values().forEach { strength ->
            val isSelected = themeState.hapticStrength == strength
            FilterChip(
              selected = isSelected,
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
                ThemeManager.setHapticStrength(strength)
              },
              label = {
                Text(
                  when (strength) {
                    HapticStrength.CRISP -> stringResource(R.string.haptic_crisp)
                    HapticStrength.SUBTLE -> stringResource(R.string.haptic_subtle)
                    HapticStrength.OFF -> stringResource(R.string.haptic_off)
                  },
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
              },
              modifier = Modifier
                .weight(1f)
                .testTag("haptic_strength_${strength.name.lowercase()}")
            )
          }
        }
      }
    }
  }
}
