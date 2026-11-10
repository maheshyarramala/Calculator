package com.example.domain.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricLockManager {

  private const val PREFS_NAME = "omni_calc_finance_prefs"
  private const val KEY_BIOMETRIC_ENABLED = "finance_biometric_lock_enabled"

  fun isBiometricHardwareAvailable(context: Context): Boolean {
    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                         BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val status = biometricManager.canAuthenticate(authenticators)
    return status == BiometricManager.BIOMETRIC_SUCCESS || 
           status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
  }

  fun isLockEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
  }

  fun setLockEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
  }

  fun authenticate(
    activity: FragmentActivity,
    title: String = "Unlock Finance & Money Hub",
    subtitle: String = "Use fingerprint, face, or device PIN to view accounts & transactions",
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    val executor = ContextCompat.getMainExecutor(activity)

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or 
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
      )
      .build()

    val biometricPrompt = BiometricPrompt(
      activity,
      executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          onError(errString.toString())
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          // Biometric sensor failed match; system prompt handles retry automatically
        }
      }
    )

    try {
      biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
      onError(e.localizedMessage ?: "Biometric prompt error")
    }
  }
}
