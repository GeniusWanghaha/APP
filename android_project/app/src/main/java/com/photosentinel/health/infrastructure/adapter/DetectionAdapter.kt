package com.photosentinel.health.infrastructure.adapter

import com.photosentinel.health.domain.model.DetectionInput
import com.photosentinel.health.domain.model.HealthMetrics
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.StandardizedDetection
import com.photosentinel.health.domain.model.UserProfile
import com.photosentinel.health.domain.model.ValidationError
import java.time.Instant

class DetectionAdapter {
    fun adapt(input: DetectionInput): HealthResult<StandardizedDetection> {
        val pwvMs = input.pwvMs ?: input.pulseWaveVelocity
        val heartRate = input.heartRate ?: input.hr
        val spo2 = input.spo2Percent ?: input.spo2 ?: input.bloodOxygen

        if (pwvMs == null) {
            return HealthResult.Failure(ValidationError("缺少 PWV 数据"))
        }
        if (heartRate == null) {
            return HealthResult.Failure(ValidationError("缺少心率数据"))
        }
        if (spo2 == null) {
            return HealthResult.Failure(ValidationError("缺少血氧数据"))
        }
        if (heartRate <= 0) {
            return HealthResult.Failure(ValidationError("心率必须为正数"))
        }
        if (pwvMs <= 0.0) {
            return HealthResult.Failure(ValidationError("PWV 必须为正数"))
        }

        val profile = UserProfile(
            age = input.userAge ?: 25,
            gender = (input.userGender ?: "未知").lowercase()
        )
        val metrics = HealthMetrics(
            pwvMs = pwvMs,
            heartRate = heartRate,
            spo2Percent = spo2,
            elasticityScore = input.elasticityScore ?: input.elasticity ?: 0,
            vascularAge = input.vascularAge ?: input.vesselAge ?: 0,
            riseTimeMs = input.riseTimeMs ?: input.riseTime ?: 0,
            reflectionIndex = input.reflectionIndex ?: input.ri ?: 0.0,
            signalQuality = input.signalQuality ?: input.quality ?: 0.0
        )

        return HealthResult.Success(
            StandardizedDetection(
                timestamp = input.timestamp ?: Instant.now().toString(),
                userProfile = profile,
                metrics = metrics
            )
        )
    }
}
