// 文件：ui/components/PulseAnimation.kt

package com.photosentinel.health.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photosentinel.health.ui.theme.AccentCyan

@Composable
fun PulseRing(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    color: Color = AccentCyan,
    ringCount: Int = 3
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val animations = (0 until ringCount).map { index ->
        val delay = index * 600

        val scale by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1800,
                    delayMillis = delay,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "scale_$index"
        )

        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1800,
                    delayMillis = delay,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha_$index"
        )

        Pair(scale, alpha)
    }

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = this.size.minDimension / 2f

        // 中心实心圆点
        drawCircle(
            color = color,
            radius = 6.dp.toPx(),
            center = center
        )

        // 扩散环
        animations.forEach { (scale, alpha) ->
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = maxRadius * scale,
                center = center,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
