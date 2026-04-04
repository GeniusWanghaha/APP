package com.photosentinel.health.presentation.repository

import com.photosentinel.health.application.usecase.GetHealthPlansUseCase
import com.photosentinel.health.application.usecase.GetMallProductsUseCase
import com.photosentinel.health.application.usecase.UpdateHealthPlanCompletionUseCase
import com.photosentinel.health.domain.model.HealthPlan
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MallProduct
import com.photosentinel.health.infrastructure.di.AppContainer

class LifestyleCatalogRepository(
    private val getHealthPlansUseCase: GetHealthPlansUseCase =
        AppContainer.getHealthPlansUseCase,
    private val getMallProductsUseCase: GetMallProductsUseCase =
        AppContainer.getMallProductsUseCase,
    private val updateHealthPlanCompletionUseCase: UpdateHealthPlanCompletionUseCase =
        AppContainer.updateHealthPlanCompletionUseCase
) : LifestyleCatalogDataSource {
    override suspend fun healthPlans(): HealthResult<List<HealthPlan>> {
        return getHealthPlansUseCase()
    }

    override suspend fun mallProducts(): HealthResult<List<MallProduct>> {
        return getMallProductsUseCase()
    }

    override suspend fun updateHealthPlanCompletion(
        planId: String,
        isCompleted: Boolean
    ): HealthResult<Unit> {
        return updateHealthPlanCompletionUseCase(planId, isCompleted)
    }
}
