package com.photosentinel.health.application.usecase

import com.photosentinel.health.application.service.LifestyleCatalogService
import com.photosentinel.health.domain.model.HealthPlan
import com.photosentinel.health.domain.model.HealthResult

class GetHealthPlansUseCase(
    private val service: LifestyleCatalogService
) {
    suspend operator fun invoke(): HealthResult<List<HealthPlan>> {
        return service.healthPlans()
    }
}
