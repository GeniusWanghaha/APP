package com.photosentinel.health.domain.repository

import com.photosentinel.health.domain.model.HealthPlan
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MallProduct

interface LifestyleRepository {
    suspend fun readHealthPlans(): HealthResult<List<HealthPlan>>

    suspend fun readMallProducts(): HealthResult<List<MallProduct>>

    suspend fun updateHealthPlanCompletion(
        planId: String,
        isCompleted: Boolean
    ): HealthResult<Unit>
}
