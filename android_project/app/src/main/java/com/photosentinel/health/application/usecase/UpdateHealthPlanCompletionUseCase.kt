package com.photosentinel.health.application.usecase

import com.photosentinel.health.application.service.LifestyleCatalogService
import com.photosentinel.health.domain.model.HealthResult

class UpdateHealthPlanCompletionUseCase(
    private val service: LifestyleCatalogService
) {
    suspend operator fun invoke(
        planId: String,
        isCompleted: Boolean
    ): HealthResult<Unit> {
        return service.updateHealthPlanCompletion(planId, isCompleted)
    }
}
