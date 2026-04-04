package com.photosentinel.health.application.service

import com.photosentinel.health.domain.model.ChatEntry
import com.photosentinel.health.domain.model.ChatRole
import com.photosentinel.health.domain.model.DetectionInput
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.NotEnoughHistoryError
import com.photosentinel.health.domain.model.SafetyLevel
import com.photosentinel.health.domain.model.SafetyBlockedError
import com.photosentinel.health.domain.model.SingleAnalysis
import com.photosentinel.health.domain.model.StoredRecord
import com.photosentinel.health.domain.model.TrendAnalysis
import com.photosentinel.health.domain.model.ValidationError
import com.photosentinel.health.domain.repository.HealthRepository
import com.photosentinel.health.infrastructure.adapter.DetectionAdapter
import com.photosentinel.health.infrastructure.report.ReportComposer
import com.photosentinel.health.infrastructure.safety.SafetyEvaluator

class HealthAgentService(
    private val repository: HealthRepository,
    private val adapter: DetectionAdapter = DetectionAdapter(),
    private val safetyEvaluator: SafetyEvaluator = SafetyEvaluator(),
    private val contentProvider: HealthContentProvider = ReportComposer()
) {
    suspend fun analyzeSingle(input: DetectionInput): HealthResult<SingleAnalysis> {
        val detection = when (val adapted = adapter.adapt(input)) {
            is HealthResult.Success -> adapted.value
            is HealthResult.Failure -> return adapted
        }

        val safety = safetyEvaluator.evaluate(detection)
        if (safety.level == SafetyLevel.BLOCKED) {
            return HealthResult.Failure(
                SafetyBlockedError(
                    safety.blockReason ?: "安全校验未通过，本次检测已拦截"
                )
            )
        }

        val report = when (val reportResult = contentProvider.singleReport(detection, safety)) {
            is HealthResult.Success -> reportResult.value
            is HealthResult.Failure -> return reportResult
        }

        val storedRecord = StoredRecord(
            detection = detection,
            safetyLevel = safety.level,
            warnings = safety.warnings,
            report = report,
            sessionId = input.sessionId,
            algorithmVersion = input.algorithmVersion ?: "algo-v2.0",
            modelVersion = input.modelVersion ?: "llm-v2.0",
            outputTier = input.outputTier ?: "BASELINE"
        )

        val recordId = when (val saved = repository.saveRecord(storedRecord)) {
            is HealthResult.Success -> saved.value
            is HealthResult.Failure -> return saved
        }

        return HealthResult.Success(
            SingleAnalysis(
                recordId = recordId,
                safety = safety,
                report = report
            )
        )
    }

    suspend fun analyzeTrend(): HealthResult<TrendAnalysis> {
        val records = when (val historyResult = repository.allRecords()) {
            is HealthResult.Success -> historyResult.value
            is HealthResult.Failure -> return historyResult
        }

        if (records.size < 2) {
            return HealthResult.Failure(
                NotEnoughHistoryError("趋势分析至少需要 2 条历史记录")
            )
        }

        val report = when (val reportResult = contentProvider.trendReport(records)) {
            is HealthResult.Success -> reportResult.value
            is HealthResult.Failure -> return reportResult
        }

        return HealthResult.Success(
            TrendAnalysis(
                historyCount = records.size,
                report = report
            )
        )
    }

    suspend fun chat(message: String, sessionId: String): HealthResult<String> {
        if (message.isBlank()) {
            return HealthResult.Failure(ValidationError("消息内容不能为空"))
        }

        val recentRecord = when (val recent = repository.recentRecords(1)) {
            is HealthResult.Success -> recent.value.lastOrNull()
            is HealthResult.Failure -> null
        }

        val history = when (val chatHistory = repository.readChatHistory(sessionId)) {
            is HealthResult.Success -> chatHistory.value
            is HealthResult.Failure -> emptyList()
        }

        val reply = when (
            val chatResult = contentProvider.chatReply(
                message = message,
                recentRecord = recentRecord,
                history = history
            )
        ) {
            is HealthResult.Success -> chatResult.value
            is HealthResult.Failure -> return chatResult
        }

        repository.saveChatEntry(
            sessionId = sessionId,
            entry = ChatEntry(role = ChatRole.USER, content = message)
        )
        repository.saveChatEntry(
            sessionId = sessionId,
            entry = ChatEntry(role = ChatRole.ASSISTANT, content = reply)
        )

        return HealthResult.Success(reply)
    }

    suspend fun recentRecords(limit: Int): HealthResult<List<StoredRecord>> {
        return repository.recentRecords(limit)
    }

    suspend fun healthCheck(): HealthResult<Unit> = contentProvider.healthCheck()
}
