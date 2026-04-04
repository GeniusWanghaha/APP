package com.photosentinel.health.infrastructure.prompt

import com.photosentinel.health.domain.model.ChatEntry
import com.photosentinel.health.domain.model.ChatRole
import com.photosentinel.health.domain.model.SafetyResult
import com.photosentinel.health.domain.model.StandardizedDetection
import com.photosentinel.health.domain.model.StoredRecord
import com.photosentinel.health.infrastructure.llm.LlmMessage

class PromptFactory {
    fun buildSingleAnalysisMessages(
        detection: StandardizedDetection,
        safety: SafetyResult
    ): List<LlmMessage> {
        val metrics = detection.metrics
        val warningsText = if (safety.warnings.isEmpty()) {
            "无"
        } else {
            safety.warnings.joinToString(separator = "；")
        }

        val systemPrompt = """
            你是 ECG+PPG 健康辅助解释模型。
            仅根据结构化摘要输出健康提示，不可输出确诊结论。
            必须使用“可能/提示/建议复测”语气，并给出明确边界声明。
            输出结构：
            1. 状态概述
            2. 风险提示
            3. 影响因素
            4. 生活建议
            5. 复测建议
        """.trimIndent()

        val userPrompt = """
            结构化摘要包：
            - 年龄=${detection.userProfile.age}
            - 性别=${detection.userProfile.gender}
            - HR=${metrics.heartRate}
            - SpO2=${metrics.spo2Percent}
            - PWV=${metrics.pwvMs}
            - 节律稳定度=${metrics.elasticityScore}
            - 血管年龄=${metrics.vascularAge}
            - PTT相关上升时间=${metrics.riseTimeMs}
            - 反射指数=${metrics.reflectionIndex}
            - 信号质量=${(metrics.signalQuality * 100).toInt()}%
            - 风险条目=$warningsText
            请生成报告并附“仅供健康提示，不替代医疗诊断”声明。
        """.trimIndent()

        return listOf(
            LlmMessage(role = "system", content = systemPrompt),
            LlmMessage(role = "user", content = userPrompt)
        )
    }

    fun buildTrendAnalysisMessages(records: List<StoredRecord>): List<LlmMessage> {
        val systemPrompt = """
            你是趋势分析助手。
            请仅基于结构化历史记录输出趋势结论，不做诊断。
            输出结构：
            1. 趋势结论
            2. 关键变化
            3. 下一步建议
            4. 边界声明
        """.trimIndent()

        val history = records.joinToString(separator = "\n") { record ->
            val m = record.detection.metrics
            "${record.detection.timestamp}, HR=${m.heartRate}, SpO2=${m.spo2Percent}, PWV=${m.pwvMs}, SQI=${m.signalQuality}"
        }

        val userPrompt = """
            历史结构化记录：
            $history
            请输出可复述的趋势摘要，说明是否建议复测或就医咨询。
        """.trimIndent()

        return listOf(
            LlmMessage(role = "system", content = systemPrompt),
            LlmMessage(role = "user", content = userPrompt)
        )
    }

    fun buildChatMessages(
        userMessage: String,
        recentRecord: StoredRecord?,
        history: List<ChatEntry>
    ): List<LlmMessage> {
        val context = recentRecord?.let {
            val m = it.detection.metrics
            "最近记录：HR=${m.heartRate}, SpO2=${m.spo2Percent}, PWV=${m.pwvMs}, sessionId=${it.sessionId ?: "unknown"}。"
        } ?: "暂无最近记录。"

        val messages = mutableListOf(
            LlmMessage(
                role = "system",
                content = """
                    你是 ECG+PPG 健康问答助手。
                    只解释结构化结果，不诊断疾病。
                    如果用户问“为什么不输出某指标”，请优先解释 SQI 门控和数据质量。
                    $context
                """.trimIndent()
            )
        )

        history.takeLast(10).forEach { entry ->
            val role = if (entry.role == ChatRole.USER) "user" else "assistant"
            messages += LlmMessage(role = role, content = entry.content)
        }
        messages += LlmMessage(role = "user", content = userMessage)
        return messages
    }
}
