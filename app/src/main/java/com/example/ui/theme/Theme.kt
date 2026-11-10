package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

private val BaseDarkColorScheme =
  darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = IndigoOnPrimaryDark,
    primaryContainer = IndigoPrimaryContainerDark,
    onPrimaryContainer = IndigoOnPrimaryContainerDark,
    secondary = EmeraldSecondaryDark,
    onSecondary = EmeraldOnSecondaryDark,
    secondaryContainer = EmeraldSecondaryContainerDark,
    onSecondaryContainer = EmeraldOnSecondaryContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = AmberOnTertiaryDark,
    tertiaryContainer = AmberTertiaryContainerDark,
    onTertiaryContainer = AmberOnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
  )

private val BaseLightColorScheme =
  lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = IndigoOnPrimaryLight,
    primaryContainer = IndigoPrimaryContainerLight,
    onPrimaryContainer = IndigoPrimaryContainerLight,
    secondary = EmeraldSecondaryLight,
    onSecondary = EmeraldOnSecondaryLight,
    secondaryContainer = EmeraldSecondaryContainerLight,
    onSecondaryContainer = EmeraldOnSecondaryContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = AmberOnTertiaryLight,
    tertiaryContainer = AmberTertiaryContainerLight,
    onTertiaryContainer = AmberOnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
  )

// Shared Material 3 shape scale for consistent cards, dialogs, and controls.
private val OmniCalcShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OmniCalcTheme(
  content: @Composable () -> Unit,
) {
  val themeState by ThemeManager.state.collectAsState()
  val isSystemDark = isSystemInDarkTheme()

  val isDark = when (themeState.themeMode) {
    AppThemeMode.SYSTEM -> isSystemDark
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK, AppThemeMode.AMOLED -> true
  }

  val baseScheme = if (isDark) BaseDarkColorScheme else BaseLightColorScheme

  val primaryColor = if (isDark) themeState.accentTheme.primaryDark else themeState.accentTheme.primaryLight
  val primaryContainerColor = primaryColor.copy(alpha = if (isDark) 0.25f else 0.15f)

  val colorScheme = if (themeState.themeMode == AppThemeMode.AMOLED) {
    baseScheme.copy(
      primary = primaryColor,
      primaryContainer = primaryContainerColor,
      background = Color(0xFF000000),
      surface = Color(0xFF000000),
      surfaceVariant = Color(0xFF121212),
      onBackground = Color(0xFFFFFFFF),
      onSurface = Color(0xFFFFFFFF)
    )
  } else {
    baseScheme.copy(
      primary = primaryColor,
      primaryContainer = primaryContainerColor
    )
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = OmniCalcShapes, content = content)
}
