package com.photosentinel.health.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosentinel.health.data.ElasticityLevel
import com.photosentinel.health.ui.components.GlowDivider
import com.photosentinel.health.ui.components.MetricCard
import com.photosentinel.health.ui.components.PulseRing
import com.photosentinel.health.ui.components.PwvTrendChart
import com.photosentinel.health.ui.components.RealtimeWaveformChart
import com.photosentinel.health.ui.components.SectionHeader
import com.photosentinel.health.ui.components.StatusChip
import com.photosentinel.health.ui.theme.AccentBlue
import com.photosentinel.health.ui.theme.AccentCyan
import com.photosentinel.health.ui.theme.AccentIndigo
import com.photosentinel.health.ui.theme.AccentOrange
import com.photosentinel.health.ui.theme.AccentTeal
import com.photosentinel.health.ui.theme.BgCard
import com.photosentinel.health.ui.theme.BgPrimary
import com.photosentinel.health.ui.theme.DividerColor
import com.photosentinel.health.ui.theme.StatusExcellent
import com.photosentinel.health.ui.theme.StatusFair
import com.photosentinel.health.ui.theme.StatusGood
import com.photosentinel.health.ui.theme.StatusPoor
import com.photosentinel.health.ui.theme.TextPrimary
import com.photosentinel.health.ui.theme.TextSecondary
import com.photosentinel.health.ui.theme.TextTertiary
import com.photosentinel.health.ui.viewmodel.HomeViewModel
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState = viewModel.uiState
    val detection = uiState.detection
    val advanced = uiState.advancedIndicators
    val trendData = uiState.trendData

    val collecting = uiState.isStreaming && !uiState.measurementCompleted
    val measurementReady = uiState.measurementCompleted && uiState.hasRealData

    val sessionStatusText = when {
        collecting -> "采集中 ${uiState.measurementElapsedSec}/${uiState.measurementTargetSec} 秒"
        measurementReady -> "本轮 60 秒测量已完成"
        uiState.isStreaming -> "设备已连接，等待开始测量"
        else -> "设备未连接"
    }
    val measureButtonText = when {
        collecting -> "测量中..."
        uiState.isStreaming -> "重新测量 60 秒"
        else -> "连接并开始 60 秒测量"
    }

    val statusColor = when (detection.elasticityLevel) {
        ElasticityLevel.EXCELLENT -> StatusExcellent
        ElasticityLevel.GOOD -> StatusGood
        ElasticityLevel.FAIR -> StatusFair
        ElasticityLevel.POOR -> StatusPoor
    }

    val heartRateText = if (measurementReady) detection.heartRate.toString() else "--"
    val spo2Text = if (measurementReady) "%.1f".format(detection.spo2Percent) else "--"
    val pttText = if (measurementReady) detection.pttMs.roundToInt().toString() else "--"
    val pwvText = if (measurementReady) "%.2f".format(detection.pwvValue) else "--"
    val vascularAgeText = if (measurementReady) detection.vascularAge.toString() else "--"
    val elasticityText = if (measurementReady) detection.elasticityScore.toString() else "--"
    val sqiText = if (measurementReady) "${detection.signalQualityPercent}" else "--"
    val outputTierText = if (measurementReady) uiState.outputTierLabel else "--"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AccentCyan,
                trackColor = DividerColor
            )
        }

        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = StatusFair,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        if (!measurementReady && !collecting) {
            Text(
                text = "点击下方按钮开始 60 秒标准采集，完成后统一输出 ECG+PPG 真实结果。",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Hero Card — Apple Health 风格
            HeroOverviewCard(
                collecting = collecting,
                measurementReady = measurementReady,
                sessionStatusText = sessionStatusText,
                outputTierText = if (measurementReady) uiState.outputTierLabel else "待输出",
                sqiText = if (measurementReady) "SQI ${detection.signalQualityPercent}%" else "SQI --",
                statusColor = statusColor
            )

            // 测量控制
            SectionHeader(title = "测量控制")
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(uiState.deviceStatus, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "会话: ${uiState.sessionId ?: "未开始"} | 层级: ${uiState.outputTierLabel}",
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (uiState.droppedFrames > 0) {
                            Text(
                                "累计丢帧: ${uiState.droppedFrames}",
                                color = StatusFair,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.startMeasurement() },
                        enabled = !collecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (collecting) Icons.Outlined.Timer else Icons.Outlined.MonitorHeart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(measureButtonText)
                    }
                    OutlinedButton(
                        onClick = { viewModel.disconnectHardware() },
                        enabled = uiState.isStreaming,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("断开设备")
                    }
                }

                if (collecting) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { uiState.measurementProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentCyan,
                        trackColor = DividerColor
                    )
                    Text(
                        text = "正在进行 60 秒窗口采集，结束后统一批处理并输出最终结果。",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 波形
            SectionHeader(title = "波形结果")
            CardBlock {
                if (!measurementReady) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "完成 60 秒测量后显示 ECG 与 PPG 波形。",
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    RealtimeWaveformChart(
                        title = "ECG 波形",
                        points = uiState.ecgWaveform,
                        accentColor = AccentCyan,
                        sampleRateHz = 250
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RealtimeWaveformChart(
                        title = "PPG-IR 波形",
                        points = uiState.ppgWaveform,
                        accentColor = AccentBlue,
                        sampleRateHz = 400
                    )
                }
            }

            // 核心指标
            SectionHeader(title = "60 秒测量结果")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Favorite,
                    label = "联合心率",
                    value = heartRateText,
                    unit = "次/分",
                    accentColor = AccentCyan
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.MonitorHeart,
                    label = "血氧饱和度",
                    value = spo2Text,
                    unit = "%",
                    accentColor = AccentBlue
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Timer,
                    label = "PTT",
                    value = pttText,
                    unit = "ms",
                    accentColor = AccentTeal
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Speed,
                    label = "PWV",
                    value = pwvText,
                    unit = "m/s",
                    accentColor = AccentOrange
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.MonitorHeart,
                    label = "血管年龄",
                    value = vascularAgeText,
                    unit = "岁",
                    accentColor = AccentIndigo
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Favorite,
                    label = "弹性评分",
                    value = elasticityText,
                    unit = "/100",
                    accentColor = statusColor
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Speed,
                    label = "信号质量 SQI",
                    value = sqiText,
                    unit = "%",
                    accentColor = StatusExcellent
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Timer,
                    label = "输出层级",
                    value = outputTierText,
                    accentColor = AccentTeal
                )
            }

            // 高级指标
            SectionHeader(title = "高级指标")
            CardBlock {
                MetricLine("SDNN: ${formatMetric(advanced.sdnnMs, "ms")}", "RMSSD: ${formatMetric(advanced.rmssdMs, "ms")}")
                MetricLine("pNN50: ${formatMetric(advanced.pnn50Percent, "%")}", "Mean RR: ${formatMetric(advanced.rrMeanMs, "ms")}")
                MetricLine("RR CV: ${formatMetric(advanced.rrCv, "")}", "AF概率: ${formatMetric(advanced.afProbabilityPercent, "%")}")
                MetricLine("样本熵: ${formatMetric(advanced.sampleEntropy, "")}", "节律风险指数: ${formatMetric(advanced.arrhythmiaIndex, "")}")
                GlowDivider()
                MetricLine("Poincare SD1: ${formatMetric(advanced.sd1Ms, "ms")}", "Poincare SD2: ${formatMetric(advanced.sd2Ms, "ms")}")
                MetricLine("SD1/SD2: ${formatMetric(advanced.sd1Sd2Ratio, "")}", "异常心搏占比: ${formatMetric(advanced.arrhythmiaBeatRatioPercent, "%")}")
                GlowDivider()
                MetricLine("PAT: ${formatMetric(advanced.patMs, "ms")}", "PWTT: ${formatMetric(advanced.pwttMs, "ms")}")
                MetricLine("PTT有效搏动: ${formatMetric(advanced.pttValidBeatRatio?.times(100.0), "%")}", "心搏-脉搏一致性: ${formatMetric(advanced.beatPulseConsistency?.times(100.0), "%")}")
                MetricLine("灌注指数PI: ${formatMetric(advanced.perfusionIndex, "")}", "反射指数RI: ${formatMetric(advanced.reflectionIndex, "")}")
                MetricLine("上升时间: ${formatMetric(advanced.riseTimeMs, "ms")}", "半高宽: ${formatMetric(advanced.halfWidthMs, "ms")}")
                GlowDivider()
                MetricLine("呼吸率(ECG): ${formatMetric(advanced.ecgRespRateBpm, "bpm")}", "呼吸率(PPG): ${formatMetric(advanced.ppgRespRateBpm, "bpm")}")
                MetricLine("QRS宽度: ${formatMetric(advanced.qrsWidthMs, "ms")}", "QT: ${formatMetric(advanced.qtMs, "ms")}")
                MetricLine("QTc: ${formatMetric(advanced.qtcMs, "ms")}", "P波可靠度: ${formatMetric(advanced.pWaveQualityPercent, "%")}")
            }

            // 质量与风险
            SectionHeader(title = "质量门控与风险提示")
            CardBlock {
                Text("门控建议", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                uiState.qualityTips.forEach { tip ->
                    Text("  $tip", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                GlowDivider()
                Text(
                    "风险摘要: ${uiState.riskDigest}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 趋势
            SectionHeader(title = "联合指标趋势")
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                if (trendData.isEmpty()) {
                    Text(
                        text = "暂无趋势数据，完成测量后会自动生成。",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    PwvTrendChart(
                        data = trendData,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            // AI 报告
            SectionHeader(title = "AI 报告")
            CardBlock {
                Text(
                    text = if (measurementReady && uiState.aiSummary.isNotBlank()) {
                        uiState.aiSummary
                    } else {
                        "完成 60 秒测量后生成本轮 AI 解读。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 22.sp
                )
            }

            uiState.exportHint?.let { hint ->
                Text(
                    text = "最近导出: $hint",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HeroOverviewCard(
    collecting: Boolean,
    measurementReady: Boolean,
    sessionStatusText: String,
    outputTierText: String,
    sqiText: String,
    statusColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PulseRing(
                size = 140.dp,
                color = AccentCyan
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "血管弹性评估",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "ECG + PPG 联合采集，60 秒输出血管弹性指标",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(
                    text = sessionStatusText,
                    color = if (collecting) StatusGood else TextTertiary
                )
                StatusChip(text = outputTierText, color = AccentBlue)
                StatusChip(
                    text = sqiText,
                    color = if (measurementReady) statusColor else TextTertiary
                )
            }
        }
    }
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun MetricLine(left: String, right: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = left,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = right,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatMetric(value: Double?, unit: String): String {
    if (value == null) return "--"
    val number = if (value >= 100) {
        "%.0f".format(value)
    } else {
        "%.1f".format(value)
    }
    return if (unit.isBlank()) number else "$number $unit"
}
