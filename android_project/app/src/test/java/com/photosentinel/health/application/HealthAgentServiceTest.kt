package com.photosentinel.health.application

import com.photosentinel.health.application.service.HealthAgentService
import com.photosentinel.health.data.repository.InMemoryHealthRepository
import com.photosentinel.health.domain.model.DetectionInput
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.NotEnoughHistoryError
import com.photosentinel.health.domain.model.SafetyBlockedError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthAgentServiceTest {
    @Test
    fun analyzeSingle_blocksLowSignalQuality() = runBlocking {
        val service = HealthAgentService(InMemoryHealthRepository())

        val result = service.analyzeSingle(
            DetectionInput(
                pwvMs = 7.1,
                heartRate = 72,
                spo2Percent = 98.0,
                signalQuality = 0.2
            )
        )

        assertTrue(result is HealthResult.Failure)
        val error = (result as HealthResult.Failure).error
        assertTrue(error is SafetyBlockedError)
    }

    @Test
    fun analyzeSingle_returnsPersistedRecord() = runBlocking {
        val service = HealthAgentService(InMemoryHealthRepository())

        val result = service.analyzeSingle(
            DetectionInput(
                pwvMs = 7.2,
                heartRate = 72,
                spo2Percent = 98.0,
                signalQuality = 0.9
            )
        )

        assertTrue(result is HealthResult.Success)
        val analysis = (result as HealthResult.Success).value
        assertEquals(1L, analysis.recordId)
        assertTrue(analysis.report.isNotBlank())
    }

    @Test
    fun analyzeTrend_requiresAtLeastTwoRecords() = runBlocking {
        val service = HealthAgentService(InMemoryHealthRepository())

        service.analyzeSingle(
            DetectionInput(
                pwvMs = 7.2,
                heartRate = 72,
                spo2Percent = 98.0,
                signalQuality = 0.9
            )
        )

        val trend = service.analyzeTrend()
        assertTrue(trend is HealthResult.Failure)
        val error = (trend as HealthResult.Failure).error
        assertTrue(error is NotEnoughHistoryError)
    }

    @Test
    fun chat_returnsAssistantReply() = runBlocking {
        val service = HealthAgentService(InMemoryHealthRepository())

        val reply = service.chat(
            message = "可以解释一下脉搏波速度吗？",
            sessionId = "unit-test-session"
        )

        assertTrue(reply is HealthResult.Success)
        val content = (reply as HealthResult.Success).value
        assertTrue(content.contains("PWV") || content.contains("PTT"))
    }
}
