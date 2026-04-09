package com.photosentinel.health.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.photosentinel.health.ui.theme.BgDeep
import com.photosentinel.health.ui.theme.DividerColor
import com.photosentinel.health.ui.theme.TextSecondary
import com.photosentinel.health.ui.theme.TextTertiary

@Composable
fun RealtimeWaveformChart(
    title: String,
    points: List<Float>,
    accentColor: Color,
    sampleRateHz: Int,
    modifier: Modifier = Modifier
) {
    val displayPoints = remember(points) { downsample(points, 600) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$title  (${sampleRateHz}Hz)",
            color = TextSecondary,
            style = MaterialTheme.typography.titleMedium
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BgDeep),
            contentAlignment = Alignment.Center
        ) {
            if (displayPoints.size < 4) {
                Text(
                    text = "等待波形数据...",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    repeat(4) { row ->
                        val y = size.height * row / 3f
                        drawLine(
                            color = DividerColor.copy(alpha = 0.5f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 0.5f
                        )
                    }

                    val minValue = displayPoints.minOrNull() ?: 0f
                    val maxValue = displayPoints.maxOrNull() ?: 1f
                    val span = (maxValue - minValue).coerceAtLeast(1f)
                    val lastIndex = (displayPoints.size - 1).coerceAtLeast(1)
                    val path = Path()

                    displayPoints.forEachIndexed { index, value ->
                        val x = size.width * (index / lastIndex.toFloat())
                        val normalized = (value - minValue) / span
                        val y = size.height - normalized * size.height
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = accentColor,
                        alpha = 0.9f
                    )
                }
            }
        }
    }
}

private fun downsample(points: List<Float>, maxPoints: Int): List<Float> {
    if (points.size <= maxPoints || maxPoints <= 1) {
        return points
    }
    val step = points.size.toFloat() / maxPoints.toFloat()
    val sampled = ArrayList<Float>(maxPoints)
    var cursor = 0f
    while (cursor < points.size && sampled.size < maxPoints) {
        sampled += points[cursor.toInt().coerceIn(0, points.lastIndex)]
        cursor += step
    }
    return sampled
}
