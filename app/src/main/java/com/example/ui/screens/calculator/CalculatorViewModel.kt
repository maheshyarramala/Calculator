package com.example.ui.screens.calculator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CalculationRecord
import com.example.domain.calculator.ScientificEvaluator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalculatorUiState(
  val expression: String = "",
  val resultPreview: String = "",
  val isScientific: Boolean = false,
  val isRadians: Boolean = false,
  val historyList: List<Pair<String, String>> = emptyList(),
  val toastMessage: String? = null
)

class CalculatorViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(CalculatorUiState())
  val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

  fun append(token: String) {
    _uiState.update { current ->
      val newExpr = when (token) {
        "sin", "cos", "tan", "log", "ln", "sqrt" -> "${current.expression}$token("
        "π" -> "${current.expression}π"
        "e" -> "${current.expression}e"
        "^" -> "${current.expression}^"
        else -> "${current.expression}$token"
      }
      val preview = evaluateExpression(newExpr, current.isRadians)
      current.copy(expression = newExpr, resultPreview = preview)
    }
  }

  fun backspace() {
    _uiState.update { current ->
      if (current.expression.isNotEmpty()) {
        val newExpr = current.expression.dropLast(1)
        val preview = evaluateExpression(newExpr, current.isRadians)
        current.copy(expression = newExpr, resultPreview = preview)
      } else {
        current.copy(expression = "", resultPreview = "")
      }
    }
  }

  fun clear() {
    _uiState.update { it.copy(expression = "", resultPreview = "") }
  }

  fun equals(context: Context? = null) {
    val state = _uiState.value
    if (state.expression.isBlank()) return

    val eval = ScientificEvaluator.evaluate(state.expression)
    when (eval) {
      is ScientificEvaluator.EvalResult.Success -> {
        val finalResult = eval.formatted
        val expr = state.expression
        _uiState.update {
          it.copy(
            expression = finalResult,
            resultPreview = "",
            historyList = listOf(expr to finalResult) + it.historyList.take(20)
          )
        }
        // Save to DB if context provided
        if (context != null) {
          viewModelScope.launch {
            val db = AppDatabase.getDatabase(context)
            db.calculationHistoryDao().insert(
              CalculationRecord(
                category = "CALCULATOR",
                title = "$expr = $finalResult",
                summary = "Expression: $expr",
                detailsText = "Input: $expr\nResult: $finalResult\nEvaluated using OmniCalc Engine",
                timestamp = System.currentTimeMillis()
              )
            )
          }
        }
      }
      is ScientificEvaluator.EvalResult.Error -> {
        _uiState.update { it.copy(resultPreview = "Error: ${eval.message}") }
      }
    }
  }

  fun toggleScientific() {
    _uiState.update { it.copy(isScientific = !it.isScientific) }
  }

  fun toggleAngleUnit() {
    _uiState.update {
      val newRad = !it.isRadians
      ScientificEvaluator.isRadians = newRad
      it.copy(
        isRadians = newRad,
        resultPreview = evaluateExpression(it.expression, newRad)
      )
    }
  }

  fun restoreFromHistory(expr: String) {
    _uiState.update {
      it.copy(
        expression = expr,
        resultPreview = evaluateExpression(expr, it.isRadians)
      )
    }
  }

  private fun evaluateExpression(expr: String, isRad: Boolean): String {
    if (expr.isBlank()) return ""
    ScientificEvaluator.isRadians = isRad
    return when (val res = ScientificEvaluator.evaluate(expr)) {
      is ScientificEvaluator.EvalResult.Success -> "= ${res.formatted}"
      is ScientificEvaluator.EvalResult.Error -> ""
    }
  }
}
