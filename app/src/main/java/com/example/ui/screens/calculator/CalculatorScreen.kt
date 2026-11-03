package com.example.ui.screens.calculator
import com.example.ui.theme.performConfiguredHapticFeedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalculatorScreen(
  viewModel: CalculatorViewModel,
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsState()
  val haptic = LocalHapticFeedback.current
  val context = LocalContext.current

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    // Top Display Section
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(bottom = 8.dp),
      verticalArrangement = Arrangement.Bottom
    ) {
      // Recent calculations row
      if (state.historyList.isNotEmpty()) {
        LazyRow(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(state.historyList) { (expr, res) ->
            SuggestionChip(
              onClick = {
                haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.restoreFromHistory(expr)
              },
              label = {
                Text(
                  text = "$expr = $res",
                  style = MaterialTheme.typography.labelSmall
                )
              },
              shape = RoundedCornerShape(8.dp)
            )
          }
        }
      }

      // Expression Input Display
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState(), reverseScrolling = true),
        horizontalArrangement = Arrangement.End
      ) {
        Text(
          text = if (state.expression.isEmpty()) "0" else state.expression,
          style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Light,
            fontSize = if (state.expression.length > 12) 34.sp else 44.sp
          ),
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.End,
          maxLines = 1,
          modifier = Modifier.testTag("calculator_expression_display")
        )
      }

      // Realtime Preview Display
      Text(
        text = state.resultPreview,
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.SemiBold,
          fontSize = 26.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.End,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp)
          .testTag("calculator_result_preview")
      )
    }

    // Mode Toggle Toolbar (Scientific / Rad / History)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = state.isScientific,
          onClick = {
            haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.toggleScientific()
          },
          label = { Text("Scientific", fontSize = 12.sp) },
          leadingIcon = {
            Icon(Icons.Default.Functions, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          modifier = Modifier.testTag("toggle_scientific_button")
        )

        if (state.isScientific) {
          FilterChip(
            selected = state.isRadians,
            onClick = {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.toggleAngleUnit()
            },
            label = { Text(if (state.isRadians) "RAD" else "DEG", fontSize = 12.sp) }
          )
        }
      }

      IconButton(
        onClick = {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.backspace()
        },
        modifier = Modifier.testTag("backspace_button")
      ) {
        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
      }
    }

    // Keypad Grid
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Scientific Extra Row (Animated)
      AnimatedVisibility(visible = state.isScientific) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            CalcButton(text = "sin", modifier = Modifier.weight(1f), isFunctional = true) {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.append("sin")
            }
            CalcButton(text = "cos", modifier = Modifier.weight(1f), isFunctional = true) {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.append("cos")
            }
            CalcButton(text = "tan", modifier = Modifier.weight(1f), isFunctional = true) {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.append("tan")
            }
            CalcButton(text = "π", modifier = Modifier.weight(1f), isFunctional = true) {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.append("π")
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            CalcButton(text = "log", modifier = Modifier.weight(1f), isFunctional = true) {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.append("log")
            }
            CalcButton(text = "ln", modifier = Modifier.weight(1f), isFunctional = true) {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.append("ln")
            }
            CalcButton(text = "√", modifier = Modifier.weight(1f), isFunctional = true) {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.append("sqrt")
            }
            CalcButton(text = "^", modifier = Modifier.weight(1f), isFunctional = true) {
              haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.append("^")
            }
          }
        }
      }

      // Standard Keypad Rows
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalcButton(text = "AC", modifier = Modifier.weight(1f), isAction = true) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
          viewModel.clear()
        }
        CalcButton(text = "(", modifier = Modifier.weight(1f), isFunctional = true) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("(")
        }
        CalcButton(text = ")", modifier = Modifier.weight(1f), isFunctional = true) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append(")")
        }
        CalcButton(text = "÷", modifier = Modifier.weight(1f), isOperator = true) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("÷")
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalcButton(text = "7", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("7")
        }
        CalcButton(text = "8", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("8")
        }
        CalcButton(text = "9", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("9")
        }
        CalcButton(text = "×", modifier = Modifier.weight(1f), isOperator = true) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("×")
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalcButton(text = "4", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("4")
        }
        CalcButton(text = "5", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("5")
        }
        CalcButton(text = "6", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("6")
        }
        CalcButton(text = "-", modifier = Modifier.weight(1f), isOperator = true) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("-")
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalcButton(text = "1", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("1")
        }
        CalcButton(text = "2", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("2")
        }
        CalcButton(text = "3", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("3")
        }
        CalcButton(text = "+", modifier = Modifier.weight(1f), isOperator = true) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("+")
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalcButton(text = "%", modifier = Modifier.weight(1f), isFunctional = true) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("%")
        }
        CalcButton(text = "0", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append("0")
        }
        CalcButton(text = ".", modifier = Modifier.weight(1f)) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.append(".")
        }
        CalcButton(text = "=", modifier = Modifier.weight(1f), isEqual = true) {
          haptic.performConfiguredHapticFeedback(HapticFeedbackType.LongPress)
          viewModel.equals(context)
        }
      }
    }
  }
}

@Composable
private fun CalcButton(
  text: String,
  modifier: Modifier = Modifier,
  isOperator: Boolean = false,
  isFunctional: Boolean = false,
  isAction: Boolean = false,
  isEqual: Boolean = false,
  onClick: () -> Unit
) {
  val containerColor = when {
    isEqual -> MaterialTheme.colorScheme.primary
    isAction -> Color(0xFFE11D48) // Red/Rose
    isOperator -> MaterialTheme.colorScheme.secondaryContainer
    isFunctional -> MaterialTheme.colorScheme.surfaceVariant
    else -> MaterialTheme.colorScheme.surface
  }

  val contentColor = when {
    isEqual -> MaterialTheme.colorScheme.onPrimary
    isAction -> Color.White
    isOperator -> MaterialTheme.colorScheme.onSecondaryContainer
    isFunctional -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
  }

  FilledTonalButton(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = containerColor,
      contentColor = contentColor
    ),
    contentPadding = PaddingValues(0.dp),
    modifier = modifier
      .height(58.dp)
      .testTag("calc_btn_$text")
  ) {
    Text(
      text = text,
      fontSize = if (text.length > 2) 16.sp else 22.sp,
      fontWeight = if (isEqual || isOperator || isAction) FontWeight.Bold else FontWeight.Medium
    )
  }
}
