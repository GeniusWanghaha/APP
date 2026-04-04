package com.photosentinel.health.application.usecase

import com.photosentinel.health.application.service.LifestyleCatalogService
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MallProduct

class GetMallProductsUseCase(
    private val service: LifestyleCatalogService
) {
    suspend operator fun invoke(): HealthResult<List<MallProduct>> {
        return service.mallProducts()
    }
}
