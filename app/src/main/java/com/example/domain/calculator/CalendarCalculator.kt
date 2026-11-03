package com.example.domain.calculator

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object CalendarCalculator {

  data class DateDifferenceResult(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val includeEndDay: Boolean,
    val years: Int,
    val months: Int,
    val days: Int,
    val totalDays: Long,
    val weeks: Long,
    val remainingDays: Long,
    val workingDays: Long,
    val weekendDays: Long,
    val totalHours: Long
  )

  fun calculateDifference(
    start: LocalDate,
    end: LocalDate,
    includeEndDay: Boolean = false
  ): DateDifferenceResult {
    val actualStart = if (start.isAfter(end)) end else start
    val actualEnd = if (start.isAfter(end)) start else end

    val adjustedEnd = if (includeEndDay) actualEnd.plusDays(1) else actualEnd
    val totalDays = ChronoUnit.DAYS.between(actualStart, adjustedEnd)
    val period = Period.between(actualStart, adjustedEnd)

    val weeks = totalDays / 7
    val remainingDays = totalDays % 7

    // Working days vs weekend days calculation
    var workingDaysCount = 0L
    var weekendDaysCount = 0L
    var cur = actualStart
    while (cur.isBefore(adjustedEnd)) {
      if (cur.dayOfWeek == DayOfWeek.SATURDAY || cur.dayOfWeek == DayOfWeek.SUNDAY) {
        weekendDaysCount++
      } else {
        workingDaysCount++
      }
      cur = cur.plusDays(1)
    }

    return DateDifferenceResult(
      startDate = actualStart,
      endDate = actualEnd,
      includeEndDay = includeEndDay,
      years = period.years,
      months = period.months,
      days = period.days,
      totalDays = totalDays,
      weeks = weeks,
      remainingDays = remainingDays,
      workingDays = workingDaysCount,
      weekendDays = weekendDaysCount,
      totalHours = totalDays * 24
    )
  }

  data class DateAdjustmentResult(
    val baseDate: LocalDate,
    val isAddition: Boolean,
    val years: Int,
    val months: Int,
    val weeks: Int,
    val days: Int,
    val targetDate: LocalDate,
    val dayOfWeek: String,
    val isLeapYear: Boolean,
    val daysFromToday: Long
  )

  fun adjustDate(
    baseDate: LocalDate,
    isAddition: Boolean,
    years: Int = 0,
    months: Int = 0,
    weeks: Int = 0,
    days: Int = 0
  ): DateAdjustmentResult {
    var result = baseDate
    val totalDaysToAdd = (weeks * 7) + days

    result = if (isAddition) {
      result.plusYears(years.toLong())
        .plusMonths(months.toLong())
        .plusDays(totalDaysToAdd.toLong())
    } else {
      result.minusYears(years.toLong())
        .minusMonths(months.toLong())
        .minusDays(totalDaysToAdd.toLong())
    }

    val dayOfWeek = result.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val isLeapYear = result.isLeapYear
    val daysFromToday = ChronoUnit.DAYS.between(LocalDate.now(), result)

    return DateAdjustmentResult(
      baseDate = baseDate,
      isAddition = isAddition,
      years = years,
      months = months,
      weeks = weeks,
      days = days,
      targetDate = result,
      dayOfWeek = dayOfWeek,
      isLeapYear = isLeapYear,
      daysFromToday = daysFromToday
    )
  }
}
