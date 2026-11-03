package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChartSlice(
  val label: String,
  val value: Double,
  val color: Color
)

@Composable
fun DonutChart(
  slices: List<ChartSlice>,
  modifier: Modifier = Modifier,
  size: Dp = 180.dp,
  strokeWidth: Dp = 26.dp,
  centerTitle: String? = null,
  centerSubtitle: String? = null
) {
  val total = slices.sumOf { it.value }
  val progress by animateFloatAsState(
    targetValue = 1f,
    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
    label = "donutProgress"
  )

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier.size(size),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val strokePx = strokeWidth.toPx()
        val arcSize = Size(size.toPx() - strokePx, size.toPx() - strokePx)
        val topLeft = Offset(strokePx / 2f, strokePx / 2f)

        if (total <= 0.0) {
          drawArc(
            color = Color.LightGray.copy(alpha = 0.3f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
          )
        } else {
          var startAngle = -90f
          for (slice in slices) {
            val sweep = ((slice.value / total) * 360.0).toFloat() * progress
            if (sweep > 0f) {
              drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Butt)
              )
              startAngle += sweep
            }
          }
        }
      }

      if (centerTitle != null || centerSubtitle != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          if (centerSubtitle != null) {
            Text(
              text = centerSubtitle,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          if (centerTitle != null) {
            Text(
              text = centerTitle,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Legend
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      slices.forEach { slice ->
        val percent = if (total > 0) (slice.value / total * 100).toInt() else 0
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 4.dp)
        ) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(slice.color)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Column {
            Text(
              text = slice.label,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "$percent% (${CurrencyFormatter.formatCompact(slice.value)})",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }
  }
}
