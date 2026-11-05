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
    listOf(1, 0).forEach { daysBefore ->
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
    listOf(1, 0).forEach { daysBefore ->
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
        val db = AppDatabase.getDatabase(context)
        val dao = db.financeDao()
        val reminders = dao.getAllCreditCardBillReminders().first()
        val accounts = dao.getAllBankAccounts().first().associateBy { it.id }
        reminders.forEach { reminder ->
          accounts[reminder.accountId]?.let { account ->
            schedule(context, reminder, account.bankName)
          } ?: cancel(context, reminder.accountId)
        }

        // Reschedule Unsettled Debts
        val debts = db.debtDao().getUnsettledDebts().first()
        debts.forEach { debt ->
          if (debt.dueDateTimestamp != null && debt.dueDateTimestamp > 0L) {
            scheduleDebtReminder(context, debt)
          }
        }

        // Reschedule Active Loans
        val loans = db.loanDao().getActiveLoans().first()
        loans.forEach { loan ->
          scheduleLoanReminder(context, loan)
        }
      } catch (error: Exception) {
        Log.e("FinanceReminder", "Unable to restore reminders", error)
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

  private const val ACTION_SHOW_DEBT_REMINDER = "com.example.action.SHOW_DEBT_DUE_REMINDER"
  private const val ACTION_SHOW_LOAN_REMINDER = "com.example.action.SHOW_LOAN_EMI_REMINDER"

  private const val EXTRA_DEBT_ID = "debt_id"
  private const val EXTRA_PERSON_NAME = "person_name"
  private const val EXTRA_DEBT_TYPE = "debt_type"

  private const val EXTRA_LOAN_ID = "loan_id"
  private const val EXTRA_LOAN_NAME = "loan_name"
  private const val EXTRA_EMI_AMOUNT = "emi_amount"

  fun scheduleDebtReminder(context: Context, debt: com.example.data.local.entity.DebtRecord) {
    cancelDebt(context, debt.id)
    if (debt.isSettled || debt.dueDateTimestamp == null || debt.dueDateTimestamp <= 0L) return

    createNotificationChannel(context)
    val alarmManager = context.getSystemService(AlarmManager::class.java)

    // Schedule 1 day before and on due date
    listOf(1, 0).forEach { daysBefore ->
      val dueInstant = java.time.Instant.ofEpochMilli(debt.dueDateTimestamp)
      val dueDate = dueInstant.atZone(ZoneId.systemDefault()).toLocalDate()
      val alertDate = dueDate.minusDays(daysBefore.toLong())
      val triggerAt = alertDate.atTime(LocalTime.of(9, 0))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

      if (triggerAt > System.currentTimeMillis()) {
        val intent = Intent(context, CreditCardBillNotificationReceiver::class.java).apply {
          action = ACTION_SHOW_DEBT_REMINDER
          data = Uri.parse("omni-calc://debt-due/${debt.id}/$daysBefore")
          putExtra(EXTRA_DEBT_ID, debt.id)
          putExtra(EXTRA_PERSON_NAME, debt.personName)
          putExtra(EXTRA_AMOUNT, debt.amount)
          putExtra(EXTRA_DEBT_TYPE, debt.type)
          putExtra(EXTRA_DAYS_BEFORE, daysBefore)
        }
        val pendingIntent = PendingIntent.getBroadcast(
          context,
          (debt.id.hashCode() + 20000) * 10 + daysBefore,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
      }
    }
  }

  fun cancelDebt(context: Context, debtId: Long) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    listOf(1, 0).forEach { daysBefore ->
      val intent = Intent(context, CreditCardBillNotificationReceiver::class.java).apply {
        action = ACTION_SHOW_DEBT_REMINDER
        data = Uri.parse("omni-calc://debt-due/$debtId/$daysBefore")
      }
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        (debtId.hashCode() + 20000) * 10 + daysBefore,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
      )
      if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
      }
    }
  }

  fun scheduleLoanReminder(context: Context, loan: com.example.data.local.entity.LoanRecord) {
    cancelLoan(context, loan.id)
    if (loan.isFullyPaid) return

    createNotificationChannel(context)
    val alarmManager = context.getSystemService(AlarmManager::class.java)

    // Schedule a reminder the day before and on the recurring due date.
    val dueDay = loan.dueDayOfMonth.coerceIn(1, 31)
    val now = System.currentTimeMillis()
    val monthStart = LocalDate.now().withDayOfMonth(1)
    val nextReminder = (0L..2L)
      .flatMap { monthOffset ->
        val month = monthStart.plusMonths(monthOffset)
        val dueDate = month.withDayOfMonth(minOf(dueDay, month.lengthOfMonth()))
        listOf(1, 0).map { daysBefore ->
          val triggerAt = dueDate.minusDays(daysBefore.toLong()).atTime(LocalTime.of(9, 30))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
          triggerAt to daysBefore
        }
      }
      .filter { it.first > now }
      .minByOrNull { it.first }

    if (nextReminder != null) {
      val (triggerAt, daysBefore) = nextReminder
      val intent = Intent(context, CreditCardBillNotificationReceiver::class.java).apply {
        action = ACTION_SHOW_LOAN_REMINDER
        data = Uri.parse("omni-calc://loan-emi/${loan.id}/$daysBefore")
        putExtra(EXTRA_LOAN_ID, loan.id)
        putExtra(EXTRA_LOAN_NAME, loan.loanName)
        putExtra(EXTRA_EMI_AMOUNT, loan.emiAmount)
        putExtra(EXTRA_DAYS_BEFORE, daysBefore)
      }
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        loan.id.hashCode() * 10 + daysBefore + 50000,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
      alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }
  }

  fun cancelLoan(context: Context, loanId: Long) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    listOf(1, 0).forEach { daysBefore ->
      val intent = Intent(context, CreditCardBillNotificationReceiver::class.java).apply {
        action = ACTION_SHOW_LOAN_REMINDER
        data = Uri.parse("omni-calc://loan-emi/$loanId/$daysBefore")
      }
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        loanId.hashCode() * 10 + daysBefore + 50000,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
      )
      if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
      }
    }
    val legacyIntent = Intent(context, CreditCardBillNotificationReceiver::class.java).apply {
      action = ACTION_SHOW_LOAN_REMINDER
      data = Uri.parse("omni-calc://loan-emi/$loanId")
    }
    PendingIntent.getBroadcast(
      context,
      loanId.hashCode() + 50000,
      legacyIntent,
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )?.let {
      alarmManager.cancel(it)
      it.cancel()
    }
  }

  internal fun showDebtNotification(
    context: Context,
    debtId: Long,
    personName: String,
    amount: Double,
    type: String,
    daysBefore: Int
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val notificationManager = context.getSystemService(NotificationManager::class.java)
    if (!notificationManager.areNotificationsEnabled()) return
    createNotificationChannel(context)

    val title = if (type == "LENT") "Udhaar Reminder: $personName owes you" else "Payment Reminder: Pay $personName"
    val timing = if (daysBefore == 0) "due today!" else "due tomorrow."
    val text = "${CurrencyFormatter.format(amount)} is $timing"

    val contentIntent = PendingIntent.getActivity(
      context,
      debtId.hashCode() + 20000,
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
      .setContentTitle(title)
      .setContentText(text)
      .setContentIntent(contentIntent)
      .setAutoCancel(true)
      .setPriority(Notification.PRIORITY_HIGH)
      .build()

    notificationManager.notify((debtId.hashCode() + 20000), notification)
  }

  internal fun showLoanNotification(
    context: Context,
    loanId: Long,
    loanName: String,
    emiAmount: Double,
    daysBefore: Int
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val notificationManager = context.getSystemService(NotificationManager::class.java)
    if (!notificationManager.areNotificationsEnabled()) return
    createNotificationChannel(context)

    val title = "Upcoming Loan EMI: $loanName"
    val dueText = if (daysBefore == 0) "due today." else "due tomorrow."
    val text = "${CurrencyFormatter.format(emiAmount)} installment is $dueText"

    val contentIntent = PendingIntent.getActivity(
      context,
      loanId.hashCode() + 50000,
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
      .setContentTitle(title)
      .setContentText(text)
      .setContentIntent(contentIntent)
      .setAutoCancel(true)
      .setPriority(Notification.PRIORITY_HIGH)
      .build()

    notificationManager.notify((loanId.hashCode() + 50000), notification)
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
  internal fun isDebtAction(action: String?): Boolean = action == ACTION_SHOW_DEBT_REMINDER
  internal fun isLoanAction(action: String?): Boolean = action == ACTION_SHOW_LOAN_REMINDER

  internal fun accountId(intent: Intent): Long = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L)
  internal fun accountName(intent: Intent): String = intent.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty()
  internal fun amount(intent: Intent): Double = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
  internal fun daysBefore(intent: Intent): Int = intent.getIntExtra(EXTRA_DAYS_BEFORE, 0)

  internal fun debtId(intent: Intent): Long = intent.getLongExtra(EXTRA_DEBT_ID, -1L)
  internal fun personName(intent: Intent): String = intent.getStringExtra(EXTRA_PERSON_NAME).orEmpty()
  internal fun debtType(intent: Intent): String = intent.getStringExtra(EXTRA_DEBT_TYPE).orEmpty()

  internal fun loanId(intent: Intent): Long = intent.getLongExtra(EXTRA_LOAN_ID, -1L)
  internal fun loanName(intent: Intent): String = intent.getStringExtra(EXTRA_LOAN_NAME).orEmpty()
  internal fun emiAmount(intent: Intent): Double = intent.getDoubleExtra(EXTRA_EMI_AMOUNT, 0.0)
  internal fun loanDaysBefore(intent: Intent): Int = intent.getIntExtra(EXTRA_DAYS_BEFORE, 0)
}

class CreditCardBillNotificationReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when {
      CreditCardBillNotificationScheduler.isReminderAction(intent.action) -> {
        CreditCardBillNotificationScheduler.showNotification(
          context,
          CreditCardBillNotificationScheduler.accountId(intent),
          CreditCardBillNotificationScheduler.accountName(intent),
          CreditCardBillNotificationScheduler.amount(intent),
          CreditCardBillNotificationScheduler.daysBefore(intent)
        )
      }
      CreditCardBillNotificationScheduler.isDebtAction(intent.action) -> {
        CreditCardBillNotificationScheduler.showDebtNotification(
          context,
          CreditCardBillNotificationScheduler.debtId(intent),
          CreditCardBillNotificationScheduler.personName(intent),
          CreditCardBillNotificationScheduler.amount(intent),
          CreditCardBillNotificationScheduler.debtType(intent),
          CreditCardBillNotificationScheduler.daysBefore(intent)
        )
      }
      CreditCardBillNotificationScheduler.isLoanAction(intent.action) -> {
        CreditCardBillNotificationScheduler.showLoanNotification(
          context,
          CreditCardBillNotificationScheduler.loanId(intent),
          CreditCardBillNotificationScheduler.loanName(intent),
          CreditCardBillNotificationScheduler.emiAmount(intent),
          CreditCardBillNotificationScheduler.loanDaysBefore(intent)
        )
        CreditCardBillNotificationScheduler.rescheduleAll(context.applicationContext)
      }
      else -> {
        CreditCardBillNotificationScheduler.rescheduleAll(context.applicationContext)
      }
    }
  }
}
