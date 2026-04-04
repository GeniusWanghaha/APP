package com.photosentinel.health.application.usecase

import com.photosentinel.health.application.service.HealthAgentService
import com.photosentinel.health.domain.model.HealthResult

class SendChatMessageUseCase(
    private val service: HealthAgentService
) {
    suspend operator fun invoke(
        message: String,
        sessionId: String
    ): HealthResult<String> {
        return service.chat(message = message, sessionId = sessionId)
    }
}
