package com.photosentinel.health.application.service

import com.photosentinel.health.domain.model.HealthPlan
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MallProduct
import com.photosentinel.health.domain.repository.LifestyleRepository

class LifestyleCatalogService(
    private val repository: LifestyleRepository
) {
    suspend fun healthPlans(): HealthResult<List<HealthPlan>> {
        return repository.readHealthPlans()
    }

    suspend fun mallProducts(): HealthResult<List<MallProduct>> {
        return repository.readMallProducts()
    }

    suspend fun updateHealthPlanCompletion(
        planId: String,
        isCompleted: Boolean
    ): HealthResult<Unit> {
        return repository.updateHealthPlanCompletion(planId, isCompleted)
    }
}
