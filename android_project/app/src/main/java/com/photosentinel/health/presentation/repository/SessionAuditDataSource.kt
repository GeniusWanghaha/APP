package com.photosentinel.health.presentation.repository

import com.photosentinel.health.domain.model.ExportFormat
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MeasurementSession

interface SessionAuditDataSource {
    suspend fun recentSessions(limit: Int): HealthResult<List<MeasurementSession>>

    suspend fun exportSession(
        sessionId: String,
        format: ExportFormat
    ): HealthResult<String>
}
