package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.YearlyBreakdown

@Composable
fun GrowthLineChart(
  breakdown: List<YearlyBreakdown>,
  modifier: Modifier = Modifier
) {
  if (breakdown.isEmpty()) return

  val maxVal = breakdown.maxOfOrNull { it.balance } ?: 1.0
  val maxValue = if (maxVal <= 0.0) 1.0 else maxVal

  val progress by animateFloatAsState(
    targetValue = 1f,
    animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
    label = "lineChartAnim"
  )

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Wealth Growth Projection",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = "Year-by-year compounding balance",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(16.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(140.dp)
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val w = size.width
          val h = size.height
          val count = breakdown.size

          if (count < 2) return@Canvas

          val stepX = w / (count - 1)
          val totalPath = Path()
          val investedPath = Path()
          val fillPath = Path()

          val pointsTotal = mutableListOf<Offset>()
          val pointsInvested = mutableListOf<Offset>()

          for (i in 0 until count) {
            val item = breakdown[i]
            val x = i * stepX
            val yTotal = h - ((item.balance / maxValue).toFloat() * h * 0.9f * progress)
            val yInvested = h - ((item.invested / maxValue).toFloat() * h * 0.9f * progress)

            pointsTotal.add(Offset(x, yTotal))
            pointsInvested.add(Offset(x, yInvested))
          }

          // Build lines
          totalPath.moveTo(pointsTotal[0].x, pointsTotal[0].y)
          fillPath.moveTo(pointsTotal[0].x, h)
          fillPath.lineTo(pointsTotal[0].x, pointsTotal[0].y)

          for (i in 1 until pointsTotal.size) {
            val prev = pointsTotal[i - 1]
            val curr = pointsTotal[i]
            val midX = (prev.x + curr.x) / 2
            totalPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            fillPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
          }

          fillPath.lineTo(pointsTotal.last().x, h)
          fillPath.close()

          investedPath.moveTo(pointsInvested[0].x, pointsInvested[0].y)
          for (i in 1 until pointsInvested.size) {
            val prev = pointsInvested[i - 1]
            val curr = pointsInvested[i]
            val midX = (prev.x + curr.x) / 2
            investedPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
          }

          // Draw fill gradient
          drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
              colors = listOf(
                Color(0xFF10B981).copy(alpha = 0.35f),
                Color(0xFF10B981).copy(alpha = 0.02f)
              ),
              startY = 0f,
              endY = h
            )
          )

          // Draw Total line
          drawPath(
            path = totalPath,
            color = Color(0xFF10B981),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
          )

          // Draw Invested line
          drawPath(
            path = investedPath,
            color = Color(0xFF6366F1),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
          )

          // Draw dots for last point
          drawCircle(
            color = Color(0xFF10B981),
            radius = 5.dp.toPx(),
            center = pointsTotal.last()
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Labels
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Yr 1",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (breakdown.size > 2) {
          Text(
            text = "Yr ${breakdown.size / 2}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Text(
          text = "Yr ${breakdown.size}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
