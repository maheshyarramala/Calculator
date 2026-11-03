package com.example.ui.theme

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

fun HapticFeedback.performConfiguredHapticFeedback(type: HapticFeedbackType) {
  when (ThemeManager.state.value.hapticStrength) {
    HapticStrength.OFF -> Unit
    HapticStrength.SUBTLE -> performHapticFeedback(
      if (type == HapticFeedbackType.LongPress) HapticFeedbackType.TextHandleMove else type
    )
    HapticStrength.CRISP -> performHapticFeedback(type)
  }
}
