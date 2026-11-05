package com.example

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.HapticStrength
import com.example.ui.theme.ThemeManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `verify app name constant`() {
    val appName = "OmniCalc"
    assertEquals("OmniCalc", appName)
  }

  @Test
  fun `finance backup export and import JSON`() {
    val sampleCategories = listOf(
      com.example.data.local.entity.ExpenseCategory(id = 1, name = "Gym & Fitness", colorHex = "#10B981", iconName = "category", isCustom = true),
      com.example.data.local.entity.ExpenseCategory(id = 2, name = "Gadgets", colorHex = "#3B82F6", iconName = "category", isCustom = true)
    )

    val sampleBanks = listOf(
      com.example.data.local.entity.BankAccount(
        id = 1,
        bankName = "Chase Bank",
        accountType = "Checking",
        accountNumber = "1234567890",
        ifscOrRouting = "CHAS0001234",
        holderName = "Jane Doe",
        balance = 5200.50,
        notes = "Main checking"
      )
    )

    val sampleTrips = listOf(
      com.example.data.local.entity.TripRecord(
        id = 1,
        tripName = "Tokyo Spring",
        destination = "Tokyo, Japan",
        budget = 150000.0,
        startDate = "01/04/2026",
        endDate = "12/04/2026",
        notes = "Cherry blossom season"
      )
    )

    val sampleExpenses = listOf(
      com.example.data.local.entity.ExpenseRecord(
        id = 21,
        title = "Loan payment",
        amount = 350.0,
        categoryName = "Bills & Utilities",
        bankAccountId = 1,
        bankAccountName = "Chase Bank",
        tripId = 1,
        loanId = 31,
        debtId = 41
      )
    )
    val sampleLoans = listOf(
      com.example.data.local.entity.LoanRecord(
        id = 31,
        loanName = "Car loan",
        principalAmount = 12000.0,
        annualInterestRate = 8.5,
        tenureMonths = 36,
        emiAmount = 379.0,
        startDate = "2026-01-01",
        bankAccountId = 1,
        bankAccountName = "Chase Bank"
      )
    )
    val sampleDebts = listOf(
      com.example.data.local.entity.DebtRecord(
        id = 41,
        personName = "Alex",
        amount = 250.0,
        dueDateTimestamp = 1_800_000_000_000L,
        bankAccountId = 1,
        bankAccountName = "Chase Bank"
      )
    )
    val sampleSubscriptions = listOf(
      com.example.data.local.entity.SubscriptionRecord(
        id = 51,
        name = "Internet",
        amount = 60.0,
        billingCycle = "MONTHLY",
        dueDayOfMonth = 15,
        categoryName = "Bills & Utilities",
        bankAccountId = 1,
        bankAccountName = "Chase Bank",
        lastPaidTimestamp = 1_700_000_000_000L
      )
    )
    val sampleCardReminders = listOf(
      com.example.data.local.entity.CreditCardBillReminder(
        accountId = 1,
        amount = 140.0,
        dueDateEpochDay = 20_000L,
        paidDateEpochDay = -1L,
        notificationsEnabled = true
      )
    )

    // Export to JSON
    val json = com.example.domain.backup.FinanceBackupManager.exportToJson(
      categories = sampleCategories,
      bankAccounts = sampleBanks,
      trips = sampleTrips,
      expenses = sampleExpenses,
      loans = sampleLoans,
      debts = sampleDebts,
      subscriptions = sampleSubscriptions,
      creditCardReminders = sampleCardReminders,
      monthlyBudgetLimit = 42_000.0,
      userUpiId = "jane@okaxis"
    )

    org.junit.Assert.assertNotNull(json)
    org.junit.Assert.assertTrue("JSON contains app identifier", json.contains("OmniCalc"))
    org.junit.Assert.assertTrue("JSON contains category", json.contains("Gym & Fitness"))
    org.junit.Assert.assertTrue("JSON contains bank", json.contains("Chase Bank"))
    org.junit.Assert.assertTrue("JSON contains trip", json.contains("Tokyo Spring"))

    // Parse back from JSON
    val parseResult = com.example.domain.backup.FinanceBackupManager.parseFromJson(json)
    org.junit.Assert.assertTrue("Parse result must be Success", parseResult is com.example.domain.backup.BackupParseResult.Success)

    val payload = (parseResult as com.example.domain.backup.BackupParseResult.Success).payload
    assertEquals(2, payload.categories.size)
    assertEquals("Gym & Fitness", payload.categories[0].name)
    assertEquals(1, payload.bankAccounts.size)
    assertEquals("Chase Bank", payload.bankAccounts[0].bankName)
    assertEquals("1234567890", payload.bankAccounts[0].accountNumber)
    assertEquals(1, payload.trips.size)
    assertEquals("Tokyo Spring", payload.trips[0].tripName)
    assertEquals(150000.0, payload.trips[0].budget, 0.01)
    assertEquals(1, payload.expenses.size)
    assertEquals(1L, payload.expenses[0].bankAccountId)
    assertEquals(31L, payload.expenses[0].loanId)
    assertEquals(41L, payload.expenses[0].debtId)
    assertEquals(31L, payload.loans[0].id)
    assertEquals(1L, payload.loans[0].bankAccountId)
    assertEquals(41L, payload.debts[0].id)
    assertEquals(1L, payload.debts[0].bankAccountId)
    assertEquals("Internet", payload.subscriptions[0].name)
    assertEquals("MONTHLY", payload.subscriptions[0].billingCycle)
    assertEquals(1L, payload.subscriptions[0].bankAccountId)
    assertEquals(1L, payload.creditCardReminders[0].accountId)
    assertEquals("Chase Bank", payload.creditCardReminderAccountNames[1L])
    assertEquals(42_000.0, payload.monthlyBudgetLimit!!, 0.001)
    assertEquals("jane@okaxis", payload.userUpiId)

    val subscriptionsOnly = com.example.domain.backup.FinanceBackupManager.parseFromJson(
      com.example.domain.backup.FinanceBackupManager.exportToJson(
        categories = emptyList(),
        bankAccounts = emptyList(),
        trips = emptyList(),
        subscriptions = sampleSubscriptions
      )
    )
    org.junit.Assert.assertTrue(
      "Subscription-only backup must parse",
      subscriptionsOnly is com.example.domain.backup.BackupParseResult.Success
    )

    // Test invalid JSON handling
    val invalidResult = com.example.domain.backup.FinanceBackupManager.parseFromJson("not valid json")
    org.junit.Assert.assertTrue("Invalid JSON returns Error", invalidResult is com.example.domain.backup.BackupParseResult.Error)
  }

  @Test
  fun `theme and haptic preferences persist across initialization`() {
    val context = RuntimeEnvironment.getApplication()
    ThemeManager.initialize(context)

    try {
      ThemeManager.setThemeMode(AppThemeMode.DARK)
      ThemeManager.setAccentTheme(AccentTheme.EMERALD)
      ThemeManager.setHapticStrength(HapticStrength.OFF)
      ThemeManager.initialize(context)

      assertEquals(AppThemeMode.DARK, ThemeManager.state.value.themeMode)
      assertEquals(AccentTheme.EMERALD, ThemeManager.state.value.accentTheme)
      assertEquals(HapticStrength.OFF, ThemeManager.state.value.hapticStrength)
    } finally {
      ThemeManager.setThemeMode(AppThemeMode.SYSTEM)
      ThemeManager.setAccentTheme(AccentTheme.INDIGO)
      ThemeManager.setHapticStrength(HapticStrength.CRISP)
    }
  }
}
