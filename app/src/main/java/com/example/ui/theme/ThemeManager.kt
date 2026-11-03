package com.example.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AppThemeMode(val title: String) {
  SYSTEM("System Default"),
  LIGHT("Light Mode"),
  DARK("Dark Mode"),
  AMOLED("Pure Black (AMOLED)")
}

enum class AccentTheme(
  val title: String,
  val primaryLight: Color,
  val primaryDark: Color
) {
  INDIGO("Deep Indigo", Color(0xFF4F46E5), Color(0xFF818CF8)),
  EMERALD("Emerald Wealth", Color(0xFF059669), Color(0xFF34D399)),
  PURPLE("Cyber Purple", Color(0xFF7C3AED), Color(0xFFA78BFA)),
  AMBER("Sunset Amber", Color(0xFFD97706), Color(0xFFFBBF24)),
  ROSE("Sunset Rose", Color(0xFFE11D48), Color(0xFFFB7185)),
  CYAN("Ocean Blue", Color(0xFF0284C7), Color(0xFF38BDF8))
}

enum class HapticStrength(val title: String) {
  CRISP("Crisp (Default)"),
  SUBTLE("Subtle"),
  OFF("Off")
}

data class AppThemeState(
  val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  val accentTheme: AccentTheme = AccentTheme.INDIGO,
  val hapticStrength: HapticStrength = HapticStrength.CRISP
)

object ThemeManager {
  private const val PREFERENCES_NAME = "omni_calc_user_preferences"
  private const val KEY_THEME_MODE = "theme_mode"
  private const val KEY_ACCENT_THEME = "accent_theme"
  private const val KEY_HAPTIC_STRENGTH = "haptic_strength"

  private val _state = MutableStateFlow(AppThemeState())
  val state: StateFlow<AppThemeState> = _state.asStateFlow()

  fun initialize(context: Context) {
    val savedPreferences = context.applicationContext
      .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    preferences = savedPreferences
    _state.value = AppThemeState(
      themeMode = savedPreferences.getString(KEY_THEME_MODE, null)
        ?.let { value -> AppThemeMode.values().firstOrNull { it.name == value } }
        ?: AppThemeMode.SYSTEM,
      accentTheme = savedPreferences.getString(KEY_ACCENT_THEME, null)
        ?.let { value -> AccentTheme.values().firstOrNull { it.name == value } }
        ?: AccentTheme.INDIGO,
      hapticStrength = savedPreferences.getString(KEY_HAPTIC_STRENGTH, null)
        ?.let { value -> HapticStrength.values().firstOrNull { it.name == value } }
        ?: HapticStrength.CRISP
    )
  }

  fun setThemeMode(mode: AppThemeMode) {
    _state.update { it.copy(themeMode = mode) }
    persist(KEY_THEME_MODE, mode.name)
  }

  fun setAccentTheme(accent: AccentTheme) {
    _state.update { it.copy(accentTheme = accent) }
    persist(KEY_ACCENT_THEME, accent.name)
  }

  fun setHapticStrength(strength: HapticStrength) {
    _state.update { it.copy(hapticStrength = strength) }
    persist(KEY_HAPTIC_STRENGTH, strength.name)
  }

  private fun persist(key: String, value: String) {
    preferences?.edit()?.putString(key, value)?.apply()
  }

  @Volatile
  private var preferences: android.content.SharedPreferences? = null
}
