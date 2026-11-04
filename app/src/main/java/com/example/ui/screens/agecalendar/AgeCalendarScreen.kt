package com.example.ui.screens.agecalendar
import com.example.ui.theme.performConfiguredHapticFeedback

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.NumberInputField
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeCalendarScreen(
  viewModel: AgeCalendarViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = {
      state.toastMessage?.let { msg ->
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
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Sub-Tabs: Age Calculator / Date Difference / Add-Subtract
      item {
        TabRow(
          selectedTabIndex = state.selectedTab.ordinal,
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("age_calendar_tabs")
        ) {
          AgeCalendarTab.values().forEach { tab ->
            val icon = when (tab) {
              AgeCalendarTab.AGE -> Icons.Default.Cake
              AgeCalendarTab.DATE_DIFF -> Icons.Default.DateRange
              AgeCalendarTab.ADD_SUBTRACT -> Icons.Default.CalendarToday
            }
            Tab(
              selected = state.selectedTab == tab,
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.selectTab(tab)
              },
              icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
              text = {
                Text(
                  text = tab.title,
                  fontWeight = if (state.selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                  fontSize = 11.sp
                )
              }
            )
          }
        }
      }

      when (state.selectedTab) {
        AgeCalendarTab.AGE -> {
          item {
            AgeCalculatorSection(viewModel = viewModel)
          }
        }
        AgeCalendarTab.DATE_DIFF -> {
          item {
            DateDifferenceSection(viewModel = viewModel)
          }
        }
        AgeCalendarTab.ADD_SUBTRACT -> {
          item {
            DateAddSubtractSection(viewModel = viewModel)
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(32.dp))
      }
    }
  }
}

@Composable
private fun AgeCalculatorSection(viewModel: AgeCalendarViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val res = state.ageResult
  val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.getDefault()) }

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    // Inputs Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Age Calculator",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.saveCalculation(context)
            },
            modifier = Modifier.testTag("save_age_button")
          ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save Age")
          }
        }

        // Date of birth picker
        DatePickerField(
          label = "Date of Birth",
          date = state.birthDate,
          onDateSelected = { viewModel.updateBirthDate(it) },
          context = context
        )

        // Age As Of Date picker
        DatePickerField(
          label = "Age as of Date",
          date = state.ageAsOfDate,
          onDateSelected = { viewModel.updateAgeAsOfDate(it) },
          context = context
        )
      }
    }

    // Age Hero Result Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text("Your Exact Age", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.Bottom
        ) {
          AgeUnitBadge(value = res.years, unit = "Years")
          Spacer(modifier = Modifier.width(8.dp))
          AgeUnitBadge(value = res.months, unit = "Months")
          Spacer(modifier = Modifier.width(8.dp))
          AgeUnitBadge(value = res.days, unit = "Days")
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Day of birth: ${res.bornDayOfWeek}",
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(14.dp))

        // Next Birthday Box
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.Cake,
              contentDescription = null,
              tint = Color(0xFFF43F5E),
              modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Next Birthday",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "In ${res.nextBirthdayMonths} Months and ${res.nextBirthdayDays} Days (${res.nextBirthdayTotalDays} days total)",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Falls on a ${res.nextBirthdayDayOfWeek}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF059669)
              )
            }
          }
        }
      }
    }

    // Life Statistics Card
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Total Life Spent", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

        LifeStatRow("Total Months", String.format(Locale.US, "%,d", res.totalMonths))
        LifeStatRow("Total Weeks", String.format(Locale.US, "%,d", res.totalWeeks))
        LifeStatRow("Total Days", String.format(Locale.US, "%,d", res.totalDays))
        LifeStatRow("Total Hours", String.format(Locale.US, "%,d", res.totalHours))
        LifeStatRow("Total Minutes", String.format(Locale.US, "%,d", res.totalMinutes))
      }
    }

    // Upcoming 5 Birthdays Card
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text("Upcoming Birthdays", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(10.dp))

        res.upcomingBirthdays.forEach { bday ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = bday.date.format(dateFormatter),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
              )
              Text(
                text = bday.dayOfWeek,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            SuggestionChip(
              onClick = {},
              label = { Text("Turning ${bday.ageTurning}", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
              colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
              ),
              border = null
            )
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }
      }
    }
  }
}

@Composable
private fun AgeUnitBadge(value: Int, unit: String) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.padding(2.dp)
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = value.toString(),
        style = MaterialTheme.typography.headlineMedium.copy(
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.primary
        )
      )
      Text(
        text = unit,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun LifeStatRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
  }
}

@Composable
private fun DateDifferenceSection(viewModel: AgeCalendarViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val res = state.diffResult

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Date Difference Calculator",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.saveCalculation(context)
            },
            modifier = Modifier.testTag("save_date_diff_button")
          ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save Difference")
          }
        }

        DatePickerField(
          label = "Start Date",
          date = state.diffStartDate,
          onDateSelected = { viewModel.updateDiffStartDate(it) },
          context = context
        )

        DatePickerField(
          label = "End Date",
          date = state.diffEndDate,
          onDateSelected = { viewModel.updateDiffEndDate(it) },
          context = context
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Include End Day", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text("Adds 1 day to include the final date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Switch(
            checked = state.includeEndDay,
            onCheckedChange = { viewModel.toggleIncludeEndDay(it) }
          )
        }
      }
    }

    // Difference Results Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Total Difference", style = MaterialTheme.typography.labelMedium)
        Text(
          text = "${String.format(Locale.US, "%,d", res.totalDays)} Days",
          style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
          )
        )
        Text(
          text = "${res.years} Years, ${res.months} Months, ${res.days} Days",
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(14.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text("Working Days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.height(4.dp))
              Text("${res.workingDays} Days", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF059669))
            }
          }
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text("Weekend Days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.height(4.dp))
              Text("${res.weekendDays} Days", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFF43F5E))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DateAddSubtractSection(viewModel: AgeCalendarViewModel) {
  val state by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val res = state.adjResult
  val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.getDefault()) }

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Add / Subtract Days",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
          FilledTonalIconButton(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
              viewModel.saveCalculation(context)
            },
            modifier = Modifier.testTag("save_date_adjust_button")
          ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save Adjustment")
          }
        }

        DatePickerField(
          label = "Starting Base Date",
          date = state.adjBaseDate,
          onDateSelected = { viewModel.updateAdjBaseDate(it) },
          context = context
        )

        // Add or Subtract Operation (Choose using visual icon cards)
        Text("Operation:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Card(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.toggleAdjOperation(true)
            },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (state.isAddition) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = if (state.isAddition) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.weight(1f)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                Icons.Default.AddCircle,
                contentDescription = null,
                tint = if (state.isAddition) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "Add (+)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (state.isAddition) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
              )
            }
          }

          Card(
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.toggleAdjOperation(false)
            },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (!state.isAddition) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = if (!state.isAddition) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.weight(1f)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                Icons.Default.RemoveCircle,
                contentDescription = null,
                tint = if (!state.isAddition) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "Subtract (-)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (!state.isAddition) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        // Quantities Grid
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          NumberInputField(
            value = state.adjYearsInput,
            onValueChange = { viewModel.updateAdjValues(years = it) },
            label = "Years",
            modifier = Modifier.weight(1f)
          )
          NumberInputField(
            value = state.adjMonthsInput,
            onValueChange = { viewModel.updateAdjValues(months = it) },
            label = "Months",
            modifier = Modifier.weight(1f)
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          NumberInputField(
            value = state.adjWeeksInput,
            onValueChange = { viewModel.updateAdjValues(weeks = it) },
            label = "Weeks",
            modifier = Modifier.weight(1f)
          )
          NumberInputField(
            value = state.adjDaysInput,
            onValueChange = { viewModel.updateAdjValues(days = it) },
            label = "Days",
            modifier = Modifier.weight(1f)
          )
        }

        Text(
          "Quick Warranty / Return Presets:",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          SuggestionChip(
            onClick = {
              viewModel.toggleAdjOperation(true)
              viewModel.updateAdjValues(years = "0", months = "0", weeks = "1", days = "0")
            },
            label = { Text("7-Day Return", fontSize = 10.sp) },
            modifier = Modifier.weight(1f)
          )
          SuggestionChip(
            onClick = {
              viewModel.toggleAdjOperation(true)
              viewModel.updateAdjValues(years = "0", months = "0", weeks = "0", days = "30")
            },
            label = { Text("30-Day Replacement", fontSize = 10.sp) },
            modifier = Modifier.weight(1f)
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          SuggestionChip(
            onClick = {
              viewModel.toggleAdjOperation(true)
              viewModel.updateAdjValues(years = "1", months = "0", weeks = "0", days = "0")
            },
            label = { Text("1-Year Warranty", fontSize = 10.sp) },
            modifier = Modifier.weight(1f)
          )
          SuggestionChip(
            onClick = {
              viewModel.toggleAdjOperation(true)
              viewModel.updateAdjValues(years = "3", months = "0", weeks = "0", days = "0")
            },
            label = { Text("3-Year Warranty", fontSize = 10.sp) },
            modifier = Modifier.weight(1f)
          )
        }
      }
    }

    // Adjustment Result Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Target Calculated Date", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = res.targetDate.format(dateFormatter),
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
          )
        )

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SuggestionChip(
            onClick = {},
            label = { Text(if (res.isLeapYear) "Leap Year" else "Normal Year") }
          )
          SuggestionChip(
            onClick = {},
            label = {
              Text(
                when {
                  res.daysFromToday > 0 -> "In ${res.daysFromToday} days"
                  res.daysFromToday < 0 -> "${-res.daysFromToday} days ago"
                  else -> "Today"
                }
              )
            }
          )
        }
      }
    }
  }
}

@Composable
private fun DatePickerField(
  label: String,
  date: LocalDate,
  onDateSelected: (LocalDate) -> Unit,
  context: android.content.Context
) {
  val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.getDefault()) }

  val datePickerDialog = remember(date) {
    DatePickerDialog(
      context,
      { _, year, month, dayOfMonth ->
        onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
      },
      date.year,
      date.monthValue - 1,
      date.dayOfMonth
    )
  }

  OutlinedCard(
    onClick = { datePickerDialog.show() },
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          text = date.format(dateFormatter),
          style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        )
      }
      Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = MaterialTheme.colorScheme.primary)
    }
  }
}
