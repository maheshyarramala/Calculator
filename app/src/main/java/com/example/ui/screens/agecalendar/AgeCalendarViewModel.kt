package com.example.ui.screens.agecalendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CalculationRecord
import com.example.domain.calculator.AgeCalculator
import com.example.domain.calculator.CalendarCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class AgeCalendarTab(val title: String) {
  AGE("Age Calculator"),
  DATE_DIFF("Date Difference"),
  ADD_SUBTRACT("Add / Subtract Days")
}

data class AgeCalendarUiState(
  val selectedTab: AgeCalendarTab = AgeCalendarTab.AGE,
  // Age Calculator
  val birthDate: LocalDate = LocalDate.of(1998, 5, 15),
  val ageAsOfDate: LocalDate = LocalDate.now(),
  val ageResult: AgeCalculator.AgeResult = AgeCalculator.calculateAge(
    LocalDate.of(1998, 5, 15),
    LocalDate.now()
  ),
  // Date Difference
  val diffStartDate: LocalDate = LocalDate.now(),
  val diffEndDate: LocalDate = LocalDate.now().plusMonths(1),
  val includeEndDay: Boolean = false,
  val diffResult: CalendarCalculator.DateDifferenceResult = CalendarCalculator.calculateDifference(
    LocalDate.now(),
    LocalDate.now().plusMonths(1),
    false
  ),
  // Date Add / Subtract
  val adjBaseDate: LocalDate = LocalDate.now(),
  val isAddition: Boolean = true,
  val adjYearsInput: String = "0",
  val adjMonthsInput: String = "1",
  val adjWeeksInput: String = "2",
  val adjDaysInput: String = "15",
  val adjResult: CalendarCalculator.DateAdjustmentResult = CalendarCalculator.adjustDate(
    LocalDate.now(),
    true,
    0, 1, 2, 15
  ),
  val toastMessage: String? = null
)

class AgeCalendarViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(AgeCalendarUiState())
  val uiState: StateFlow<AgeCalendarUiState> = _uiState.asStateFlow()

  fun selectTab(tab: AgeCalendarTab) {
    _uiState.update { it.copy(selectedTab = tab) }
  }

  // --- Age ---
  fun updateBirthDate(date: LocalDate) {
    _uiState.update {
      val res = AgeCalculator.calculateAge(date, it.ageAsOfDate)
      it.copy(birthDate = date, ageResult = res)
    }
  }

  fun updateAgeAsOfDate(date: LocalDate) {
    _uiState.update {
      val res = AgeCalculator.calculateAge(it.birthDate, date)
      it.copy(ageAsOfDate = date, ageResult = res)
    }
  }

  // --- Date Difference ---
  fun updateDiffStartDate(date: LocalDate) {
    _uiState.update {
      val res = CalendarCalculator.calculateDifference(date, it.diffEndDate, it.includeEndDay)
      it.copy(diffStartDate = date, diffResult = res)
    }
  }

  fun updateDiffEndDate(date: LocalDate) {
    _uiState.update {
      val res = CalendarCalculator.calculateDifference(it.diffStartDate, date, it.includeEndDay)
      it.copy(diffEndDate = date, diffResult = res)
    }
  }

  fun toggleIncludeEndDay(include: Boolean) {
    _uiState.update {
      val res = CalendarCalculator.calculateDifference(it.diffStartDate, it.diffEndDate, include)
      it.copy(includeEndDay = include, diffResult = res)
    }
  }

  // --- Date Add / Subtract ---
  fun updateAdjBaseDate(date: LocalDate) {
    _uiState.update { it.copy(adjBaseDate = date) }
    recalculateAdjustment()
  }

  fun toggleAdjOperation(isAddition: Boolean) {
    _uiState.update { it.copy(isAddition = isAddition) }
    recalculateAdjustment()
  }

  fun updateAdjValues(
    years: String = _uiState.value.adjYearsInput,
    months: String = _uiState.value.adjMonthsInput,
    weeks: String = _uiState.value.adjWeeksInput,
    days: String = _uiState.value.adjDaysInput
  ) {
    _uiState.update {
      it.copy(
        adjYearsInput = years,
        adjMonthsInput = months,
        adjWeeksInput = weeks,
        adjDaysInput = days
      )
    }
    recalculateAdjustment()
  }

  private fun recalculateAdjustment() {
    val state = _uiState.value
    val y = state.adjYearsInput.toIntOrNull() ?: 0
    val m = state.adjMonthsInput.toIntOrNull() ?: 0
    val w = state.adjWeeksInput.toIntOrNull() ?: 0
    val d = state.adjDaysInput.toIntOrNull() ?: 0

    val res = CalendarCalculator.adjustDate(
      baseDate = state.adjBaseDate,
      isAddition = state.isAddition,
      years = y,
      months = m,
      weeks = w,
      days = d
    )
    _uiState.update { it.copy(adjResult = res) }
  }

  fun clearToast() {
    _uiState.update { it.copy(toastMessage = null) }
  }

  fun saveCalculation(context: Context) {
    val state = _uiState.value
    viewModelScope.launch {
      val db = AppDatabase.getDatabase(context)
      val record = when (state.selectedTab) {
        AgeCalendarTab.AGE -> {
          val res = state.ageResult
          CalculationRecord(
            category = "AGE",
            title = "Age: ${res.years} Years, ${res.months} Mo, ${res.days} Days",
            summary = "Born: ${res.birthDate} (${res.bornDayOfWeek}) | Next Birthday: in ${res.nextBirthdayMonths}m ${res.nextBirthdayDays}d",
            detailsText = "Birth Date: ${res.birthDate}\n" +
              "Age: ${res.years} Years, ${res.months} Months, ${res.days} Days\n" +
              "Total Days: ${res.totalDays} days\n" +
              "Next Birthday: in ${res.nextBirthdayTotalDays} days (${res.nextBirthdayDayOfWeek})",
            timestamp = System.currentTimeMillis()
          )
        }
        AgeCalendarTab.DATE_DIFF -> {
          val res = state.diffResult
          CalculationRecord(
            category = "CALENDAR",
            title = "Date Difference: ${res.totalDays} Days",
            summary = "Between ${res.startDate} and ${res.endDate} (${res.years}y ${res.months}m ${res.days}d)",
            detailsText = "From: ${res.startDate}\nTo: ${res.endDate}\n" +
              "Difference: ${res.years} Years, ${res.months} Months, ${res.days} Days\n" +
              "Working Days: ${res.workingDays}\nWeekend Days: ${res.weekendDays}",
            timestamp = System.currentTimeMillis()
          )
        }
        AgeCalendarTab.ADD_SUBTRACT -> {
          val res = state.adjResult
          val op = if (res.isAddition) "Add" else "Subtract"
          CalculationRecord(
            category = "CALENDAR",
            title = "Target Date: ${res.targetDate} (${res.dayOfWeek})",
            summary = "$op from ${res.baseDate}: ${res.years}y ${res.months}m ${res.weeks}w ${res.days}d",
            detailsText = "Base Date: ${res.baseDate}\nOperation: $op\nTarget: ${res.targetDate} (${res.dayOfWeek})\nLeap Year: ${res.isLeapYear}",
            timestamp = System.currentTimeMillis()
          )
        }
      }
      db.calculationHistoryDao().insert(record)
      _uiState.update { it.copy(toastMessage = "${state.selectedTab.title} saved to history!") }
    }
  }
}
