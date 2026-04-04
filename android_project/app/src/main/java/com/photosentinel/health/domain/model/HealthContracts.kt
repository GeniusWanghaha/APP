package com.photosentinel.health.domain.model

import java.time.Instant

data class DetectionInput(
    val timestamp: String? = null,
    val sessionId: String? = null,
    val algorithmVersion: String? = null,
    val modelVersion: String? = null,
    val outputTier: String? = null,
    val frameId: Int? = null,
    val baseTimestampMicros: Long? = null,
    val ecgSamples: List<Int>? = null,
    val ppgRedSamples: List<Int>? = null,
    val ppgIrSamples: List<Int>? = null,
    val stateFlags: Int? = null,
    val crc: Int? = null,
    val userAge: Int? = null,
    val userGender: String? = null,
    val pwvMs: Double? = null,
    val pulseWaveVelocity: Double? = null,
    val heartRate: Int? = null,
    val hr: Int? = null,
    val spo2Percent: Double? = null,
    val spo2: Double? = null,
    val bloodOxygen: Double? = null,
    val elasticityScore: Int? = null,
    val elasticity: Int? = null,
    val vascularAge: Int? = null,
    val vesselAge: Int? = null,
    val riseTimeMs: Int? = null,
    val riseTime: Int? = null,
    val reflectionIndex: Double? = null,
    val ri: Double? = null,
    val signalQuality: Double? = null,
    val quality: Double? = null
)

data class UserProfile(
    val age: Int,
    val gender: String
)

data class HealthMetrics(
    val pwvMs: Double,
    val heartRate: Int,
    val spo2Percent: Double,
    val elasticityScore: Int,
    val vascularAge: Int,
    val riseTimeMs: Int,
    val reflectionIndex: Double,
    val signalQuality: Double
)

data class StandardizedDetection(
    val timestamp: String,
    val userProfile: UserProfile,
    val metrics: HealthMetrics
)

enum class SafetyLevel {
    OK,
    WARN,
    DANGER,
    BLOCKED
}

data class SafetyResult(
    val level: SafetyLevel,
    val warnings: List<String>,
    val blockReason: String? = null
)

data class SingleAnalysis(
    val recordId: Long,
    val safety: SafetyResult,
    val report: String
)

data class TrendAnalysis(
    val historyCount: Int,
    val report: String
)

enum class ChatRole {
    USER,
    ASSISTANT
}

data class ChatEntry(
    val role: ChatRole,
    val content: String,
    val timestamp: Instant = Instant.now()
)

data class StoredRecord(
    val id: Long = 0,
    val detection: StandardizedDetection,
    val safetyLevel: SafetyLevel,
    val warnings: List<String>,
    val report: String,
    val sessionId: String? = null,
    val algorithmVersion: String = "algo-v2.0",
    val modelVersion: String = "llm-v2.0",
    val outputTier: String = "BASELINE",
    val createdAt: Instant = Instant.now()
)

sealed interface HealthError {
    val message: String
}

data class ValidationError(override val message: String) : HealthError

data class SafetyBlockedError(override val message: String) : HealthError

data class NotEnoughHistoryError(override val message: String) : HealthError

data class PersistenceError(override val message: String) : HealthError

data class UnexpectedError(
    override val message: String,
    val cause: Throwable? = null
) : HealthError

sealed interface HealthResult<out T> {
    data class Success<T>(val value: T) : HealthResult<T>
    data class Failure(val error: HealthError) : HealthResult<Nothing>
}

inline fun <T, R> HealthResult<T>.map(transform: (T) -> R): HealthResult<R> {
    return when (this) {
        is HealthResult.Success -> HealthResult.Success(transform(value))
        is HealthResult.Failure -> this
    }
}
