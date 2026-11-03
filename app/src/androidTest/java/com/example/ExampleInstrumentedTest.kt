package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.theme.HapticStrength
import com.example.ui.theme.ThemeManager
import com.example.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @org.junit.Rule
  @JvmField
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun useAppContext() {
    // Context of the app under test.
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
  }

  @Test
  fun bottomNavigationChangesSelectedDestination() {
    composeRule.onNodeWithTag("nav_tab_home").assertDoesNotExist()
    composeRule.onNodeWithTag("nav_tab_finance").assertIsSelected()
    composeRule.onNodeWithTag("nav_tab_emi").performClick().assertIsSelected()
    composeRule.onNodeWithTag("nav_tab_finance").performClick().assertIsSelected()
  }

  @Test
  fun hapticSettingUpdatesConfiguredFeedbackPreference() {
    composeRule.onNodeWithTag("theme_settings_top_btn").performClick()
    composeRule.onNodeWithTag("haptic_strength_off").performClick()
    composeRule.runOnIdle {
      assertEquals(HapticStrength.OFF, ThemeManager.state.value.hapticStrength)
      ThemeManager.setHapticStrength(HapticStrength.CRISP)
    }
  }
}
