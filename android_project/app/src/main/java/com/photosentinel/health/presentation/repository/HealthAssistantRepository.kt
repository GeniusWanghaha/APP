package com.photosentinel.health.presentation.repository

import com.photosentinel.health.application.usecase.AnalyzeMeasurementUseCase
import com.photosentinel.health.application.usecase.AnalyzeTrendUseCase
import com.photosentinel.health.application.usecase.CheckServiceHealthUseCase
import com.photosentinel.health.application.usecase.GetRecentRecordsUseCase
import com.photosentinel.health.application.usecase.SendChatMessageUseCase
import com.photosentinel.health.domain.model.DetectionInput
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.SingleAnalysis
import com.photosentinel.health.domain.model.StoredRecord
import com.photosentinel.health.domain.model.TrendAnalysis
import com.photosentinel.health.infrastructure.di.AppContainer

class HealthAssistantRepository(
    private val checkServiceHealthUseCase: CheckServiceHealthUseCase =
        AppContainer.checkServiceHealthUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase =
        AppContainer.sendChatMessageUseCase,
    private val analyzeMeasurementUseCase: AnalyzeMeasurementUseCase =
        AppContainer.analyzeMeasurementUseCase,
    private val getRecentRecordsUseCase: GetRecentRecordsUseCase =
        AppContainer.getRecentRecordsUseCase,
    private val analyzeTrendUseCase: AnalyzeTrendUseCase =
        AppContainer.analyzeTrendUseCase
) : HealthAssistantDataSource {
    override suspend fun checkService(): HealthResult<Unit> {
        return checkServiceHealthUseCase()
    }

    override suspend fun sendMessage(
        message: String,
        sessionId: String
    ): HealthResult<String> {
        return sendChatMessageUseCase(message, sessionId)
    }

    override suspend fun analyzeMeasurement(input: DetectionInput): HealthResult<SingleAnalysis> {
        return analyzeMeasurementUseCase(input)
    }

    override suspend fun analyzeTrend(): HealthResult<TrendAnalysis> {
        return analyzeTrendUseCase()
    }

    override suspend fun recentRecords(limit: Int): HealthResult<List<StoredRecord>> {
        return getRecentRecordsUseCase(limit)
    }
}
