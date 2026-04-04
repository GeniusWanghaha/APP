package com.photosentinel.health.domain.repository

import com.photosentinel.health.domain.model.ExportFormat
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MeasurementSession
import com.photosentinel.health.domain.model.SessionEvent

interface SessionAuditRepository {
    suspend fun startSession(session: MeasurementSession): HealthResult<Unit>

    suspend fun finishSession(session: MeasurementSession): HealthResult<Unit>

    suspend fun appendEvent(event: SessionEvent): HealthResult<Unit>

    suspend fun recentSessions(limit: Int): HealthResult<List<MeasurementSession>>

    suspend fun exportSession(
        sessionId: String,
        format: ExportFormat
    ): HealthResult<String>
}
