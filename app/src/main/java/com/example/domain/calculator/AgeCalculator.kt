package com.example.domain.calculator

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

object AgeCalculator {

  data class UpcomingBirthday(
    val year: Int,
    val date: LocalDate,
    val dayOfWeek: String,
    val ageTurning: Int
  )

  data class AgeResult(
    val birthDate: LocalDate,
    val targetDate: LocalDate,
    val years: Int,
    val months: Int,
    val days: Int,
    val totalMonths: Long,
    val totalWeeks: Long,
    val totalDays: Long,
    val totalHours: Long,
    val totalMinutes: Long,
    val totalSeconds: Long,
    val bornDayOfWeek: String,
    val nextBirthdayMonths: Int,
    val nextBirthdayDays: Int,
    val nextBirthdayTotalDays: Long,
    val nextBirthdayDayOfWeek: String,
    val upcomingBirthdays: List<UpcomingBirthday>
  )

  fun calculateAge(birthDate: LocalDate, targetDate: LocalDate = LocalDate.now()): AgeResult {
    val period = Period.between(birthDate, targetDate)
    val totalDays = ChronoUnit.DAYS.between(birthDate, targetDate).coerceAtLeast(0)
    val totalWeeks = totalDays / 7
    val totalMonths = ChronoUnit.MONTHS.between(birthDate, targetDate).coerceAtLeast(0)
    val totalHours = totalDays * 24
    val totalMinutes = totalHours * 60
    val totalSeconds = totalMinutes * 60

    val bornDayOfWeek = birthDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

    // Next Birthday calculation (safely handling Feb 29 for non-leap years)
    var nextBirthday = safeWithYear(birthDate, targetDate.year)
    if (nextBirthday.isBefore(targetDate) || nextBirthday.isEqual(targetDate)) {
      nextBirthday = safeWithYear(birthDate, targetDate.year + 1)
    }

    val daysToNextBirthday = ChronoUnit.DAYS.between(targetDate, nextBirthday)
    val nextBdayPeriod = Period.between(targetDate, nextBirthday)
    val nextBdayDayOfWeek = nextBirthday.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

    // Next 5 birthdays
    val upcoming = mutableListOf<UpcomingBirthday>()
    for (i in 0 until 5) {
      val targetYr = nextBirthday.year + i
      val bday = safeWithYear(birthDate, targetYr)
      val dayName = bday.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
      val turningAge = targetYr - birthDate.year
      upcoming.add(UpcomingBirthday(targetYr, bday, dayName, turningAge))
    }

    return AgeResult(
      birthDate = birthDate,
      targetDate = targetDate,
      years = period.years.coerceAtLeast(0),
      months = period.months.coerceAtLeast(0),
      days = period.days.coerceAtLeast(0),
      totalMonths = totalMonths,
      totalWeeks = totalWeeks,
      totalDays = totalDays,
      totalHours = totalHours,
      totalMinutes = totalMinutes,
      totalSeconds = totalSeconds,
      bornDayOfWeek = bornDayOfWeek,
      nextBirthdayMonths = nextBdayPeriod.months.coerceAtLeast(0),
      nextBirthdayDays = nextBdayPeriod.days.coerceAtLeast(0),
      nextBirthdayTotalDays = daysToNextBirthday,
      nextBirthdayDayOfWeek = nextBdayDayOfWeek,
      upcomingBirthdays = upcoming
    )
  }

  private fun safeWithYear(date: LocalDate, year: Int): LocalDate {
    return if (date.monthValue == 2 && date.dayOfMonth == 29 && !java.time.Year.isLeap(year.toLong())) {
      LocalDate.of(year, 2, 28)
    } else {
      date.withYear(year)
    }
  }
}
