package com.photosentinel.health.presentation.repository

import com.photosentinel.health.domain.model.HealthPlan
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MallProduct

interface LifestyleCatalogDataSource {
    suspend fun healthPlans(): HealthResult<List<HealthPlan>>

    suspend fun mallProducts(): HealthResult<List<MallProduct>>

    suspend fun updateHealthPlanCompletion(
        planId: String,
        isCompleted: Boolean
    ): HealthResult<Unit>
}
