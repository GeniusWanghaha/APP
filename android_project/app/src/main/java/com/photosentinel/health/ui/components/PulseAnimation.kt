package com.photosentinel.health.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photosentinel.health.ui.theme.AccentCyan
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 脉搏波传导动画 — 模拟血管脉搏波传播
 * 外圈弹性环代表血管，内部脉搏波代表 PWV 检测
 * 符合心血管弹性检测主题
 */
@Composable
fun PulseRing(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    color: Color = AccentCyan,
    @Suppress("UNUSED_PARAMETER") ringCount: Int = 3
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vascular")

    // 血管弹性脉动 — 外环缩放
    val vesselScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0.92f at 0 using LinearEasing
                1f at 200 using FastOutSlowInEasing
                0.95f at 500 using FastOutSlowInEasing
                0.92f at 1400 using LinearEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "vesselScale"
    )

    // 脉搏波流动相位
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // 内环光晕呼吸
    val innerGlow by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0.06f at 0
                0.18f at 200
                0.10f at 500
                0.06f at 1400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "innerGlow"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val baseRadius = (w.coerceAtMost(h)) / 2f * 0.82f

        // 最外圈 — 淡色辅助环
        drawCircle(
            color = color.copy(alpha = 0.06f),
            radius = baseRadius * 1.05f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )

        // 血管弹性主环 — 随脉搏缩放
        val mainRadius = baseRadius * vesselScale
        drawCircle(
            color = color.copy(alpha = 0.25f),
            radius = mainRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // 内部柔光填充
        drawCircle(
            color = color.copy(alpha = innerGlow),
            radius = mainRadius * 0.85f,
            center = Offset(cx, cy)
        )

        // 中心标识 — 血管截面示意（双层同心圆）
        val coreRadius = baseRadius * 0.22f
        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = coreRadius * 1.6f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = color.copy(alpha = 0.35f),
            radius = coreRadius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = color,
            radius = coreRadius * 0.45f,
            center = Offset(cx, cy)
        )

        // 脉搏波沿环传播 — 弧形上滚动的波
        val wavePath = Path()
        val waveSteps = 120
        val waveAmplitude = 5.dp.toPx()
        val waveRadius = mainRadius * 0.92f

        for (i in 0..waveSteps) {
            val t = i.toFloat() / waveSteps
            val angle = t * 2f * PI.toFloat()
            val phase = (t + wavePhase) % 1f

            // 模拟脉搏波形 — 在圆环上起伏
            val pulseOffset = when {
                phase in 0.0f..0.08f -> waveAmplitude * sin(phase / 0.08f * PI.toFloat())
                phase in 0.08f..0.15f -> -waveAmplitude * 0.3f * sin((phase - 0.08f) / 0.07f * PI.toFloat())
                phase in 0.35f..0.50f -> waveAmplitude * 0.5f * sin((phase - 0.35f) / 0.15f * PI.toFloat())
                else -> 0f
            }

            val r = waveRadius + pulseOffset
            val x = cx + r * cos(angle - PI.toFloat() / 2f)
            val y = cy + r * sin(angle - PI.toFloat() / 2f)

            if (i == 0) wavePath.moveTo(x, y)
            else wavePath.lineTo(x, y)
        }
        wavePath.close()

        drawPath(
            path = wavePath,
            color = color.copy(alpha = 0.6f),
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 传导点 — 表示脉搏波传播位置
        val dotAngle = wavePhase * 2f * PI.toFloat() - PI.toFloat() / 2f
        val dotX = cx + waveRadius * cos(dotAngle)
        val dotY = cy + waveRadius * sin(dotAngle)
        drawCircle(
            color = color.copy(alpha = 0.3f),
            radius = 6.dp.toPx(),
            center = Offset(dotX, dotY)
        )
        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = Offset(dotX, dotY)
        )
    }
}
