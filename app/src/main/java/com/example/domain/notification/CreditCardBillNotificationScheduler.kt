package com.example.domain.notification

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CreditCardBillReminder
import com.example.ui.components.CurrencyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object CreditCardBillNotificationScheduler {
  private const val CHANNEL_ID = "credit_card_bill_due"
  private const val ACTION_SHOW_BILL_REMINDER = "com.example.action.SHOW_CARD_BILL_REMINDER"
  private const val EXTRA_ACCOUNT_ID = "account_id"
  private const val EXTRA_ACCOUNT_NAME = "account_name"
  private const val EXTRA_AMOUNT = "amount"
  private const val EXTRA_DAYS_BEFORE = "days_before"

  fun schedule(context: Context, reminder: CreditCardBillReminder, accountName: String) {
    cancel(context, reminder.accountId)
    if (!reminder.notificationsEnabled || reminder.paidDateEpochDay >= 0L) return

    createNotificationChannel(context)
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    listOf(3, 0).forEach { daysBefore ->
      val alertDate = LocalDate.ofEpochDay(reminder.dueDateEpochDay).minusDays(daysBefore.toLong())
      val triggerAt = alertDate.atTime(LocalTime.of(9, 0))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
      if (triggerAt > System.currentTimeMillis()) {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAt,
          reminderPendingIntent(context, reminder, accountName, daysBefore)
        )
      }
    }
  }

  fun cancel(context: Context, accountId: Long) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    listOf(3, 0).forEach { daysBefore ->
      val intent = Intent(context, CreditCardBillNotificationReceiver::class.java).apply {
        action = ACTION_SHOW_BILL_REMINDER
        data = Uri.parse("omni-calc://card-bill/$accountId/$daysBefore")
      }
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        accountId.hashCode() * 10 + daysBefore,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
      )
      if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
      }
    }
  }

  internal fun rescheduleAll(context: Context) {
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val dao = AppDatabase.getDatabase(context).financeDao()
        val reminders = dao.getAllCreditCardBillReminders().first()
        val accounts = dao.getAllBankAccounts().first().associateBy { it.id }
        reminders.forEach { reminder ->
          accounts[reminder.accountId]?.let { account ->
            schedule(context, reminder, account.bankName)
          } ?: cancel(context, reminder.accountId)
        }
      } catch (error: Exception) {
        Log.e("CardBillReminder", "Unable to restore credit-card reminders", error)
      }
    }
  }

  internal fun showNotification(
    context: Context,
    accountId: Long,
    accountName: String,
    amount: Double,
    daysBefore: Int
  ) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val notificationManager = context.getSystemService(NotificationManager::class.java)
    if (!notificationManager.areNotificationsEnabled()) return
    createNotificationChannel(context)

    val dueText = when (daysBefore) {
      0 -> context.getString(R.string.card_bill_due_today)
      1 -> context.getString(R.string.card_bill_due_in_one_day)
      else -> context.getString(R.string.card_bill_due_in_days, daysBefore)
    }
    val contentIntent = PendingIntent.getActivity(
      context,
      accountId.hashCode(),
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(context, CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(context)
    }
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.card_bill_notification_title, accountName))
      .setContentText("$dueText · ${CurrencyFormatter.format(amount)}")
      .setStyle(
        Notification.BigTextStyle()
          .bigText("$dueText · ${CurrencyFormatter.format(amount)}")
      )
      .setContentIntent(contentIntent)
      .setAutoCancel(true)
      .setCategory(Notification.CATEGORY_REMINDER)
      .setPriority(Notification.PRIORITY_HIGH)
      .build()

    notificationManager.notify(accountId.hashCode(), notification)
  }

  private fun reminderPendingIntent(
    context: Context,
    reminder: CreditCardBillReminder,
    accountName: String,
    daysBefore: Int
  ): PendingIntent {
    val intent = Intent(context, CreditCardBillNotificationReceiver::class.java).apply {
      action = ACTION_SHOW_BILL_REMINDER
      data = Uri.parse("omni-calc://card-bill/${reminder.accountId}/$daysBefore")
      putExtra(EXTRA_ACCOUNT_ID, reminder.accountId)
      putExtra(EXTRA_ACCOUNT_NAME, accountName)
      putExtra(EXTRA_AMOUNT, reminder.amount)
      putExtra(EXTRA_DAYS_BEFORE, daysBefore)
    }
    return PendingIntent.getBroadcast(
      context,
      reminder.accountId.hashCode() * 10 + daysBefore,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.card_bill_notification_channel),
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = context.getString(R.string.card_bill_notification_channel_description)
      }
      context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
  }

  internal fun isReminderAction(action: String?): Boolean = action == ACTION_SHOW_BILL_REMINDER
  internal fun accountId(intent: Intent): Long = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L)
  internal fun accountName(intent: Intent): String = intent.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty()
  internal fun amount(intent: Intent): Double = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
  internal fun daysBefore(intent: Intent): Int = intent.getIntExtra(EXTRA_DAYS_BEFORE, 0)
}

class CreditCardBillNotificationReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (CreditCardBillNotificationScheduler.isReminderAction(intent.action)) {
      CreditCardBillNotificationScheduler.showNotification(
        context,
        CreditCardBillNotificationScheduler.accountId(intent),
        CreditCardBillNotificationScheduler.accountName(intent),
        CreditCardBillNotificationScheduler.amount(intent),
        CreditCardBillNotificationScheduler.daysBefore(intent)
      )
    } else {
      CreditCardBillNotificationScheduler.rescheduleAll(context.applicationContext)
    }
  }
}
