package com.photosentinel.health.application.usecase

import com.photosentinel.health.application.service.HealthAgentService
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.TrendAnalysis

class AnalyzeTrendUseCase(
    private val service: HealthAgentService
) {
    suspend operator fun invoke(): HealthResult<TrendAnalysis> {
        return service.analyzeTrend()
    }
}
