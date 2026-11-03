package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.MainScreen
import com.example.ui.theme.OmniCalcTheme
import com.example.ui.theme.ThemeManager
import com.example.domain.notification.CreditCardBillNotificationScheduler

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ThemeManager.initialize(applicationContext)
    CreditCardBillNotificationScheduler.rescheduleAll(applicationContext)
    enableEdgeToEdge()
    setContent {
      OmniCalcTheme {
        MainScreen()
      }
    }
  }
}
