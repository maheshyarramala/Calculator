package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
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

    // Export to JSON
    val json = com.example.domain.backup.FinanceBackupManager.exportToJson(
      categories = sampleCategories,
      bankAccounts = sampleBanks,
      trips = sampleTrips
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

    // Test invalid JSON handling
    val invalidResult = com.example.domain.backup.FinanceBackupManager.parseFromJson("not valid json")
    org.junit.Assert.assertTrue("Invalid JSON returns Error", invalidResult is com.example.domain.backup.BackupParseResult.Error)
  }
}
