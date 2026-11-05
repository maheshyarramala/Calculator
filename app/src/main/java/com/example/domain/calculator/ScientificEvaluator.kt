package com.example.domain.calculator

import kotlin.math.*

object ScientificEvaluator {

  var isRadians: Boolean = false

  sealed class EvalResult {
    data class Success(val value: Double, val formatted: String) : EvalResult()
    data class Error(val message: String) : EvalResult()
  }

  fun evaluate(expression: String): EvalResult {
    if (expression.isBlank()) return EvalResult.Success(0.0, "0")

    return try {
      val sanitized = expression
        .replace("×", "*")
        .replace("÷", "/")
        .replace("√", "sqrt")

      val parser = Parser(sanitized, isRadians)
      val result = parser.parse()
      if (result.isNaN() || result.isInfinite()) {
        EvalResult.Error("Undefined result")
      } else {
        EvalResult.Success(result, formatResult(result))
      }
    } catch (e: Exception) {
      EvalResult.Error(e.message ?: "Invalid expression")
    }
  }

  fun formatResult(value: Double): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

    val roundedLong = value.toLong()
    return if (abs(value - roundedLong) < 1e-11) {
      roundedLong.toString()
    } else {
      val str = String.format(java.util.Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
      if (str.length > 15) {
        String.format(java.util.Locale.US, "%.6e", value)
      } else {
        str
      }
    }
  }

  private class Parser(private val input: String, private val isRadians: Boolean) {
    private var pos = -1
    private var ch = 0

    private fun nextChar() {
      ch = if (++pos < input.length) input[pos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
      while (ch == ' '.code) nextChar()
      if (ch == charToEat) {
        nextChar()
        return true
      }
      return false
    }

    private fun isImplicitMultiplyNext(): Boolean {
      while (ch == ' '.code) nextChar()
      return ch == '('.code || ch == 'π'.code || ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code
    }

    fun parse(): Double {
      nextChar()
      val x = parseExpression()
      if (pos < input.length) throw IllegalArgumentException("Unexpected: " + input[pos].toChar())
      return x
    }

    private fun parseExpression(): Double {
      var x = parseTerm()
      while (true) {
        when {
          eat('+'.code) -> x += parseTerm()
          eat('-'.code) -> x -= parseTerm()
          else -> return x
        }
      }
    }

    private fun parseTerm(): Double {
      var x = parseFactor()
      while (true) {
        when {
          eat('*'.code) -> x *= parseFactor()
          eat('/'.code) -> {
            val divisor = parseFactor()
            if (abs(divisor) < 1e-15) throw ArithmeticException("Division by zero")
            x /= divisor
          }
          eat('%'.code) -> x %= parseFactor()
          isImplicitMultiplyNext() -> x *= parseFactor()
          else -> return x
        }
      }
    }

    private fun parseFactor(): Double {
      if (eat('+'.code)) return parseFactor()
      if (eat('-'.code)) return -parseFactor()

      var x: Double
      val startPos = pos
      if (eat('('.code)) {
        x = parseExpression()
        if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis")
      } else if (eat('π'.code)) {
        x = Math.PI
      } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
        while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
        val numStr = input.substring(startPos, pos)
        x = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $numStr")
      } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
        while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
        val identifier = input.substring(startPos, pos).lowercase()
        x = when (identifier) {
          "pi" -> Math.PI
          "e" -> Math.E
          else -> {
            if (eat('('.code)) {
              val arg = parseExpression()
              if (!eat(')'.code)) throw IllegalArgumentException("Missing ')' after $identifier")
              evaluateFunction(identifier, arg)
            } else {
              throw IllegalArgumentException("Expected '(' after $identifier")
            }
          }
        }
      } else {
        throw IllegalArgumentException(
          "Unexpected character: " + if (ch != -1) ch.toChar().toString() else "end of input"
        )
      }

      if (eat('^'.code)) {
        val exponent = parseFactor()
        x = x.pow(exponent)
      }

      return x
    }

    private fun evaluateFunction(func: String, arg: Double): Double {
      return when (func) {
        "sqrt" -> {
          if (arg < 0) throw ArithmeticException("Square root of negative number")
          sqrt(arg)
        }
        "sin" -> {
          val rad = if (isRadians) arg else Math.toRadians(arg)
          val s = sin(rad)
          if (abs(s) < 1e-15) 0.0 else s
        }
        "cos" -> {
          val rad = if (isRadians) arg else Math.toRadians(arg)
          val c = cos(rad)
          if (abs(c) < 1e-15) 0.0 else c
        }
        "tan" -> {
          val rad = if (isRadians) arg else Math.toRadians(arg)
          val c = cos(rad)
          if (abs(c) < 1e-15) throw ArithmeticException("Tangent is undefined")
          val t = tan(rad)
          if (abs(t) < 1e-15) 0.0 else t
        }
        "log" -> {
          if (arg <= 0) throw ArithmeticException("Log of non-positive number")
          log10(arg)
        }
        "ln" -> {
          if (arg <= 0) throw ArithmeticException("Ln of non-positive number")
          ln(arg)
        }
        "abs" -> abs(arg)
        else -> throw IllegalArgumentException("Unknown function: $func")
      }
    }
  }
}