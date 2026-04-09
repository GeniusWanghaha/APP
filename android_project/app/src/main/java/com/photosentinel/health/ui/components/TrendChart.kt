package com.photosentinel.health.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.photosentinel.health.data.TrendPoint
import com.photosentinel.health.ui.theme.*

@Composable
fun PwvTrendChart(
    data: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val width = size.width
            val height = size.height
            val paddingTop = 16.dp.toPx()
            val paddingBottom = 24.dp.toPx()
            val chartHeight = height - paddingTop - paddingBottom

            val maxPwv = data.maxOf { it.pwvValue } + 0.5f
            val minPwv = data.minOf { it.pwvValue } - 0.5f
            val range = maxPwv - minPwv

            val stepX = width / (data.size - 1).coerceAtLeast(1)

            for (i in 0..3) {
                val y = paddingTop + chartHeight * i / 3f
                drawLine(
                    color = DividerColor.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 0.5.dp.toPx()
                )
            }

            val path = Path()
            val points = data.mapIndexed { index, point ->
                val x = index * stepX
                val y = paddingTop + chartHeight * (1f - (point.pwvValue - minPwv) / range)
                Offset(x, y)
            }

            val fillPath = Path()
            points.forEachIndexed { index, offset ->
                if (index == 0) {
                    fillPath.moveTo(offset.x, offset.y)
                    path.moveTo(offset.x, offset.y)
                } else {
                    val prev = points[index - 1]
                    val cx1 = (prev.x + offset.x) / 2f
                    fillPath.cubicTo(cx1, prev.y, cx1, offset.y, offset.x, offset.y)
                    path.cubicTo(cx1, prev.y, cx1, offset.y, offset.x, offset.y)
                }
            }

            fillPath.lineTo(points.last().x, height)
            fillPath.lineTo(points.first().x, height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AccentCyan.copy(alpha = 0.12f),
                        AccentCyan.copy(alpha = 0.01f)
                    )
                )
            )

            drawPath(
                path = path,
                color = AccentCyan,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            points.forEach { offset ->
                drawCircle(
                    color = BgCard,
                    radius = 4.dp.toPx(),
                    center = offset
                )
                drawCircle(
                    color = AccentCyan,
                    radius = 3.dp.toPx(),
                    center = offset
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { point ->
                Text(
                    text = point.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}
