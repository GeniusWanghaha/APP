package com.photosentinel.health.application.usecase

import com.photosentinel.health.application.service.HealthAgentService
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.StoredRecord

class GetRecentRecordsUseCase(
    private val service: HealthAgentService
) {
    suspend operator fun invoke(limit: Int): HealthResult<List<StoredRecord>> {
        return service.recentRecords(limit)
    }
}
