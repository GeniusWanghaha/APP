package com.photosentinel.health.application.service

import com.photosentinel.health.domain.model.ChatEntry
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.SafetyResult
import com.photosentinel.health.domain.model.StandardizedDetection
import com.photosentinel.health.domain.model.StoredRecord

interface HealthContentProvider {
    suspend fun singleReport(
        detection: StandardizedDetection,
        safety: SafetyResult
    ): HealthResult<String>

    suspend fun trendReport(records: List<StoredRecord>): HealthResult<String>

    suspend fun chatReply(
        message: String,
        recentRecord: StoredRecord?,
        history: List<ChatEntry>
    ): HealthResult<String>

    suspend fun healthCheck(): HealthResult<Unit>
}
