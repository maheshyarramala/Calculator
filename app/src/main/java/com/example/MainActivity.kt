package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.MainScreen
import com.example.ui.theme.OmniCalcTheme
import com.example.ui.theme.ThemeManager
import com.example.domain.notification.CreditCardBillNotificationScheduler

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ThemeManager.initialize(applicationContext)
    CreditCardBillNotificationScheduler.rescheduleAll(applicationContext)
    enableEdgeToEdge()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
      }
    }

    setContent {
      OmniCalcTheme {
        MainScreen()
      }
    }
  }
}
