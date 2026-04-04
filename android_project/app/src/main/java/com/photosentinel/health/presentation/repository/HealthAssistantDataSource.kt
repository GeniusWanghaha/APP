package com.photosentinel.health.presentation.repository

import com.photosentinel.health.domain.model.DetectionInput
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.SingleAnalysis
import com.photosentinel.health.domain.model.StoredRecord
import com.photosentinel.health.domain.model.TrendAnalysis

interface HealthAssistantDataSource {
    suspend fun checkService(): HealthResult<Unit>

    suspend fun sendMessage(
        message: String,
        sessionId: String
    ): HealthResult<String>

    suspend fun analyzeMeasurement(input: DetectionInput): HealthResult<SingleAnalysis>

    suspend fun analyzeTrend(): HealthResult<TrendAnalysis>

    suspend fun recentRecords(limit: Int): HealthResult<List<StoredRecord>>
}
