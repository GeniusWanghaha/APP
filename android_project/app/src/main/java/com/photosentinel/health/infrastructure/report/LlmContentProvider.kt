package com.photosentinel.health.infrastructure.report

import com.photosentinel.health.application.service.HealthContentProvider
import com.photosentinel.health.domain.model.ChatEntry
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.SafetyResult
import com.photosentinel.health.domain.model.StandardizedDetection
import com.photosentinel.health.domain.model.StoredRecord
import com.photosentinel.health.infrastructure.llm.DoubaoLlmClient
import com.photosentinel.health.infrastructure.prompt.PromptFactory

class LlmContentProvider(
    private val llmClient: DoubaoLlmClient = DoubaoLlmClient(),
    private val promptFactory: PromptFactory = PromptFactory()
) : HealthContentProvider {
    override suspend fun singleReport(
        detection: StandardizedDetection,
        safety: SafetyResult
    ): HealthResult<String> {
        val messages = promptFactory.buildSingleAnalysisMessages(
            detection = detection,
            safety = safety
        )

        return llmClient.chat(messages, temperature = 0.3)
    }

    override suspend fun trendReport(records: List<StoredRecord>): HealthResult<String> {
        val messages = promptFactory.buildTrendAnalysisMessages(records)
        return llmClient.chat(messages, temperature = 0.3)
    }

    override suspend fun chatReply(
        message: String,
        recentRecord: StoredRecord?,
        history: List<ChatEntry>
    ): HealthResult<String> {
        val messages = promptFactory.buildChatMessages(
            userMessage = message,
            recentRecord = recentRecord,
            history = history
        )
        return llmClient.chat(messages, temperature = 0.7)
    }

    override suspend fun healthCheck(): HealthResult<Unit> {
        return llmClient.healthCheck()
    }
}
