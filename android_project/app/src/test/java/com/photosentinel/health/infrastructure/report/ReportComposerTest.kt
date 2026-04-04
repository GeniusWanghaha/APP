package com.photosentinel.health.infrastructure.report

import com.photosentinel.health.domain.model.HealthMetrics
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.SafetyLevel
import com.photosentinel.health.domain.model.SafetyResult
import com.photosentinel.health.domain.model.StandardizedDetection
import com.photosentinel.health.domain.model.StoredRecord
import com.photosentinel.health.domain.model.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportComposerTest {
    @Test
    fun singleReport_shouldContainBoundaryTag() = runBlocking {
        val composer = ReportComposer()
        val report = composer.singleReport(
            detection = sampleDetection(),
            safety = SafetyResult(
                level = SafetyLevel.OK,
                warnings = emptyList()
            )
        )

        assertTrue(report is HealthResult.Success)
        val content = (report as HealthResult.Success).value
        assertTrue(content.contains("BoundaryTag: NON_DIAGNOSTIC"))
        assertTrue(content.contains("PWV="))
    }

    @Test
    fun trendReport_shouldContainTraceFields() = runBlocking {
        val composer = ReportComposer()
        val records = listOf(
            sampleRecord(sessionId = "s1", pwv = 7.5, hr = 70),
            sampleRecord(sessionId = "s2", pwv = 7.1, hr = 68)
        )

        val report = composer.trendReport(records)
        assertTrue(report is HealthResult.Success)
        val content = (report as HealthResult.Success).value
        assertTrue(content.contains("sessionId="))
        assertTrue(content.contains("algorithmVersion="))
        assertTrue(content.contains("modelVersion="))
    }

    private fun sampleDetection(): StandardizedDetection {
        return StandardizedDetection(
            timestamp = "2026-04-01T00:00:00Z",
            userProfile = UserProfile(age = 25, gender = "male"),
            metrics = HealthMetrics(
                pwvMs = 7.2,
                heartRate = 72,
                spo2Percent = 98.0,
                elasticityScore = 82,
                vascularAge = 28,
                riseTimeMs = 128,
                reflectionIndex = 0.4,
                signalQuality = 0.9
            )
        )
    }

    private fun sampleRecord(
        sessionId: String,
        pwv: Double,
        hr: Int
    ): StoredRecord {
        val baseDetection = sampleDetection()
        return StoredRecord(
            detection = baseDetection.copy(
                metrics = baseDetection.metrics.copy(
                    pwvMs = pwv,
                    heartRate = hr
                )
            ),
            safetyLevel = SafetyLevel.OK,
            warnings = emptyList(),
            report = "ok",
            sessionId = sessionId
        )
    }
}
