package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
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
        RequestNotificationPermissionEffect()
        MainScreen()
      }
    }
  }
}

@Composable
private fun RequestNotificationPermissionEffect() {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
      val permissionNotGranted =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
      if (permissionNotGranted) {
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }
}
