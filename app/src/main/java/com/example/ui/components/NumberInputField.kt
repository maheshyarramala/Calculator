package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberInputField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  prefix: String? = null,
  suffix: String? = null,
  sliderValue: Float? = null,
  sliderRange: ClosedFloatingPointRange<Float>? = null,
  sliderSteps: Int = 0,
  onSliderChange: ((Float) -> Unit)? = null,
  presets: List<Pair<String, Double>> = emptyList(),
  onPresetClick: ((Double) -> Unit)? = null,
  testTag: String = "number_input"
) {
  Column(modifier = modifier.fillMaxWidth()) {
    OutlinedTextField(
      value = value,
      onValueChange = { newText ->
        // allow digits and one decimal
        if (newText.isEmpty() || newText.matches(Regex("^\\d*\\.?\\d*$"))) {
          onValueChange(newText)
        }
      },
      label = { Text(label) },
      prefix = if (prefix != null) {
        { Text(prefix, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
      } else null,
      suffix = if (suffix != null) {
        { Text(suffix, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      } else null,
      trailingIcon = {
        if (value.isNotEmpty()) {
          IconButton(onClick = { onValueChange("") }) {
            Icon(Icons.Default.Clear, contentDescription = "Clear input")
          }
        }
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      shape = RoundedCornerShape(12.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag(testTag)
    )

    if (sliderValue != null && sliderRange != null && onSliderChange != null) {
      Slider(
        value = sliderValue.coerceIn(sliderRange.start, sliderRange.endInclusive),
        onValueChange = onSliderChange,
        valueRange = sliderRange,
        steps = sliderSteps,
        colors = SliderDefaults.colors(
          thumbColor = MaterialTheme.colorScheme.primary,
          activeTrackColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp)
      )
    }

    if (presets.isNotEmpty() && onPresetClick != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        presets.forEach { (title, addVal) ->
          SuggestionChip(
            onClick = { onPresetClick(addVal) },
            label = { Text(title, style = MaterialTheme.typography.labelSmall) },
            shape = RoundedCornerShape(8.dp),
            colors = SuggestionChipDefaults.suggestionChipColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = null,
            modifier = Modifier.height(28.dp)
          )
        }
      }
    }
  }
}
