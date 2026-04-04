package com.photosentinel.health.application.usecase

import com.photosentinel.health.application.service.HealthAgentService
import com.photosentinel.health.domain.model.DetectionInput
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.SingleAnalysis

class AnalyzeMeasurementUseCase(
    private val service: HealthAgentService
) {
    suspend operator fun invoke(input: DetectionInput): HealthResult<SingleAnalysis> {
        return service.analyzeSingle(input)
    }
}
