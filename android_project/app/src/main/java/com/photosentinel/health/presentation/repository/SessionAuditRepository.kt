package com.photosentinel.health.presentation.repository

import com.photosentinel.health.domain.model.ExportFormat
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MeasurementSession
import com.photosentinel.health.domain.repository.SessionAuditRepository
import com.photosentinel.health.infrastructure.di.AppContainer

class SessionAuditPresentationRepository(
    private val repository: SessionAuditRepository = AppContainer.sessionAuditRepository
) : SessionAuditDataSource {
    override suspend fun recentSessions(limit: Int): HealthResult<List<MeasurementSession>> {
        return repository.recentSessions(limit)
    }

    override suspend fun exportSession(
        sessionId: String,
        format: ExportFormat
    ): HealthResult<String> {
        return repository.exportSession(sessionId, format)
    }
}
