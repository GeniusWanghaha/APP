package com.photosentinel.health.infrastructure.report

import com.photosentinel.health.application.service.HealthContentProvider
import com.photosentinel.health.domain.model.ChatEntry
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.SafetyLevel
import com.photosentinel.health.domain.model.SafetyResult
import com.photosentinel.health.domain.model.StandardizedDetection
import com.photosentinel.health.domain.model.StoredRecord
import kotlin.math.absoluteValue

class ReportComposer : HealthContentProvider {
    override suspend fun singleReport(
        detection: StandardizedDetection,
        safety: SafetyResult
    ): HealthResult<String> {
        val metrics = detection.metrics
        val safetyLevelText = when (safety.level) {
            SafetyLevel.OK -> "良好"
            SafetyLevel.WARN -> "需关注"
            SafetyLevel.DANGER -> "高风险提示"
            SafetyLevel.BLOCKED -> "未通过质控"
        }
        val warningText = if (safety.warnings.isEmpty()) {
            "无"
        } else {
            safety.warnings.joinToString(separator = "；")
        }

        val structuredSummary = buildString {
            appendLine("【结构化摘要包】")
            appendLine("平均心率=${metrics.heartRate}次/分")
            appendLine("血氧=${"%.1f".format(metrics.spo2Percent)}%")
            appendLine("PWV=${"%.2f".format(metrics.pwvMs)}m/s")
            appendLine("节律稳定度=${metrics.elasticityScore}/100")
            appendLine("血管年龄=${metrics.vascularAge}")
            appendLine("反射指数=${"%.2f".format(metrics.reflectionIndex)}")
            appendLine("信号质量=${(metrics.signalQuality * 100).toInt()}%")
            appendLine("风险条目=$warningText")
        }

        return HealthResult.Success(
            """
            一、状态概述
            本次综合结论：$safetyLevelText。
            $structuredSummary
            
            二、风险提示
            $warningText
            
            三、可能影响因素
            1. 电极接触稳定性、手指按压深度、环境光变化会影响 ECG/PPG 质量。
            2. 测量时的体位变化、说话和动作会引入节律与波形伪迹。
            
            四、生活方式建议
            1. 建议固定时间与姿势连续测量，提高趋势可比性。
            2. 若出现连续异常提示，建议减少高盐饮食并保证睡眠。
            
            五、复测建议
            建议 30 分钟后在静息状态复测；若持续异常，请咨询专业医生。
            
            六、边界声明
            本报告仅用于健康提示与风险筛查，不替代临床诊断结论。
            BoundaryTag: NON_DIAGNOSTIC
            """.trimIndent()
        )
    }

    override suspend fun trendReport(records: List<StoredRecord>): HealthResult<String> {
        val first = records.first()
        val last = records.last()
        val pwvDelta = last.detection.metrics.pwvMs - first.detection.metrics.pwvMs
        val heartRateDelta = last.detection.metrics.heartRate - first.detection.metrics.heartRate

        val trendLabel = when {
            pwvDelta < -0.2 && heartRateDelta <= 5 -> "趋势改善"
            pwvDelta > 0.2 || heartRateDelta > 10 -> "趋势偏高"
            else -> "趋势稳定"
        }

        val volatility = records.zipWithNext()
            .map { (left, right) -> (right.detection.metrics.pwvMs - left.detection.metrics.pwvMs).absoluteValue }
            .average()

        val qualityAvg = records.map { it.detection.metrics.signalQuality }.average() * 100.0
        val eventCount = records.sumOf { it.warnings.size }

        return HealthResult.Success(
            """
            一、趋势概览
            过去 ${records.size} 次记录总体判断：$trendLabel。
            
            二、关键变化
            1. PWV 变化：${formatDelta(pwvDelta)} m/s
            2. 心率变化：${formatDelta(heartRateDelta.toDouble())} 次/分
            3. 波动幅度：${"%.2f".format(volatility)} m/s
            4. 平均信号质量：${"%.1f".format(qualityAvg)}%
            5. 累计风险事件：$eventCount 条
            
            三、建议动作
            1. 固定检测时段，保持同姿势采集，减少跨天误差。
            2. 若趋势连续恶化，建议增加复测频次并进行专业评估。
            3. 血压相关结果仅作趋势参考，不替代袖带测量。
            
            四、追溯信息
            sessionId=${last.sessionId ?: "unknown"}，algorithmVersion=${last.algorithmVersion}，modelVersion=${last.modelVersion}。
            """.trimIndent()
        )
    }

    override suspend fun chatReply(
        message: String,
        recentRecord: StoredRecord?,
        history: List<ChatEntry>
    ): HealthResult<String> {
        val lower = message.lowercase()
        val metrics = recentRecord?.detection?.metrics
        val context = if (metrics == null) {
            "当前还没有最近一次测量记录，建议先开始 30~60 秒采集。"
        } else {
            "最近一次：HR ${metrics.heartRate} 次/分，SpO₂ ${"%.1f".format(metrics.spo2Percent)}%，PWV ${"%.2f".format(metrics.pwvMs)} m/s。"
        }

        val reply = when {
            "为什么不输出" in message || "没输出" in message || "门控" in message -> {
                "$context 某些高阶指标会受 SQI 质量门控影响。若接触不稳、丢包或运动伪迹较高，系统会自动降级为基础输出，避免误导。"
            }

            "房颤" in message || "早搏" in message || "异常" in message -> {
                "$context 当前异常事件属于风险提示或初筛，不代表确诊。建议在静息状态复测，若持续异常请咨询专业医生。"
            }

            "ptt" in lower || "pwtt" in lower || "pat" in lower || "脉搏传导" in message -> {
                "$context PAT/PTT/PWTT 主要用于时序趋势观察，可反映血管状态变化，但不能直接替代临床血压诊断值。"
            }

            "血氧" in message || "spo2" in lower || "ppg" in lower -> {
                "$context 若血氧结果波动大，请先确保手指稳定按压和环境遮光，再进行复测。"
            }

            "追溯" in message || "版本" in message || "session" in lower -> {
                val session = recentRecord?.sessionId ?: "unknown"
                val algo = recentRecord?.algorithmVersion ?: "algo-v2.0"
                val model = recentRecord?.modelVersion ?: "llm-v2.0"
                "$context 最近记录追溯字段：sessionId=$session，algorithmVersion=$algo，modelVersion=$model。"
            }

            history.isNotEmpty() -> {
                "$context 你可以继续问我：为什么某指标没输出、异常提示如何理解，或 PWV/PTT 趋势该怎么改进。"
            }

            else -> {
                "$context 我可以帮你解释 ECG/PPG 指标（含 PWV/PTT/PAT）、趋势变化、风险提示和复测建议。"
            }
        }

        return HealthResult.Success(reply)
    }

    override suspend fun healthCheck(): HealthResult<Unit> {
        return HealthResult.Success(Unit)
    }

    private fun formatDelta(value: Double): String {
        return if (value >= 0) {
            "+${"%.2f".format(value)}"
        } else {
            "%.2f".format(value)
        }
    }
}
