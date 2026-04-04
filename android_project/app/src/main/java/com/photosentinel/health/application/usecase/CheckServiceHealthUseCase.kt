package com.photosentinel.health.application.usecase

import com.photosentinel.health.application.service.HealthAgentService
import com.photosentinel.health.domain.model.HealthResult

class CheckServiceHealthUseCase(
    private val service: HealthAgentService
) {
    suspend operator fun invoke(): HealthResult<Unit> {
        return service.healthCheck()
    }
}
