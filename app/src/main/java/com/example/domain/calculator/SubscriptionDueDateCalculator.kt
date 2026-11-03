package com.example.domain.calculator

import com.example.data.local.entity.SubscriptionRecord
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

object SubscriptionDueDateCalculator {
  fun nextDueDate(subscription: SubscriptionRecord, today: LocalDate = LocalDate.now()): LocalDate {
    val dueDay = subscription.dueDayOfMonth.coerceIn(1, 31)
    val lastPaidDate = subscription.lastPaidTimestamp
      .takeIf { it > 0L }
      ?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }

    return when (subscription.billingCycle.uppercase()) {
      "WEEKLY" -> nextWeeklyDate(subscription.dueDayOfWeek, lastPaidDate, today)
      "YEARLY" -> nextYearlyDate(dueDay, subscription.dueMonth, lastPaidDate, today)
      else -> nextMonthlyDate(dueDay, lastPaidDate, today)
    }
  }

  fun isPaidInCurrentCycle(subscription: SubscriptionRecord, today: LocalDate = LocalDate.now()): Boolean {
    if (subscription.lastPaidTimestamp <= 0L) return false
    val lastPaid = java.time.Instant.ofEpochMilli(subscription.lastPaidTimestamp)
      .atZone(java.time.ZoneId.systemDefault())
      .toLocalDate()
    return when (subscription.billingCycle.uppercase()) {
      "WEEKLY" -> {
        val dueDay = DayOfWeek.of(subscription.dueDayOfWeek.coerceIn(1, 7))
        val cycleStart = today.with(TemporalAdjusters.previousOrSame(dueDay))
        !lastPaid.isBefore(cycleStart) && !lastPaid.isAfter(today)
      }
      "YEARLY" -> lastPaid.year == today.year
      else -> lastPaid.year == today.year && lastPaid.month == today.month
    }
  }

  private fun nextMonthlyDate(dueDay: Int, lastPaid: LocalDate?, today: LocalDate): LocalDate {
    val thisMonth = today.withDayOfMonth(dueDay.coerceAtMost(today.lengthOfMonth()))
    val alreadyPaid = lastPaid?.let { it.year == today.year && it.month == today.month } == true
    if (alreadyPaid || thisMonth.isBefore(today)) {
      val nextMonth = today.plusMonths(1)
      return nextMonth.withDayOfMonth(dueDay.coerceAtMost(nextMonth.lengthOfMonth()))
    }
    return thisMonth
  }

  private fun nextYearlyDate(
    dueDay: Int,
    dueMonth: Int,
    lastPaid: LocalDate?,
    today: LocalDate
  ): LocalDate {
    val monthValue = dueMonth.coerceIn(1, 12)
    val firstDayOfMonth = LocalDate.of(today.year, monthValue, 1)
    var dueDate = firstDayOfMonth.withDayOfMonth(dueDay.coerceAtMost(firstDayOfMonth.lengthOfMonth()))
    val alreadyPaid = lastPaid?.year == today.year
    if (alreadyPaid || dueDate.isBefore(today)) {
      dueDate = dueDate.plusYears(1)
    }
    return dueDate
  }

  private fun nextWeeklyDate(dueDayOfWeek: Int, lastPaid: LocalDate?, today: LocalDate): LocalDate {
    val weekday = DayOfWeek.of(dueDayOfWeek.coerceIn(1, 7))
    val nextDue = today.with(TemporalAdjusters.nextOrSame(weekday))
    return if (
      nextDue == today &&
      lastPaid != null &&
      isPaidInCurrentWeek(lastPaid, weekday, today)
    ) {
      nextDue.plusWeeks(1)
    } else {
      nextDue
    }
  }

  private fun isPaidInCurrentWeek(lastPaid: LocalDate, dueDay: DayOfWeek, today: LocalDate): Boolean {
    val cycleStart = today.with(TemporalAdjusters.previousOrSame(dueDay))
    return !lastPaid.isBefore(cycleStart) && !lastPaid.isAfter(today)
  }
}
