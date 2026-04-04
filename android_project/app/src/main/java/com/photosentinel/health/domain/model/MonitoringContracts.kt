package com.photosentinel.health.domain.model

import java.time.Instant
import java.util.UUID

data class BleRuntimeConfig(
    val preferredDeviceNamePrefix: String = DEFAULT_DEVICE_NAME_PREFIX,
    val serviceUuid: String = DEFAULT_SERVICE_UUID,
    val dataCharacteristicUuid: String = DEFAULT_DATA_UUID,
    val controlCharacteristicUuid: String = DEFAULT_CONTROL_UUID,
    val statusCharacteristicUuid: String = DEFAULT_STATUS_UUID,
    val preferredMtu: Int = DEFAULT_MTU,
    val ecgSampleRateHz: Int = DEFAULT_ECG_SAMPLE_RATE_HZ,
    val ppgSampleRateHz: Int = DEFAULT_PPG_SAMPLE_RATE_HZ,
    val ppgPhaseUs: Int = DEFAULT_PPG_PHASE_US,
    val ppgLatencyUs: Int = DEFAULT_PPG_LATENCY_US
) {
    fun sanitized(): BleRuntimeConfig {
        val deviceName = preferredDeviceNamePrefix.trim().ifBlank { DEFAULT_DEVICE_NAME_PREFIX }
        val service = serviceUuid.trim().ifBlank { DEFAULT_SERVICE_UUID }
        val data = dataCharacteristicUuid.trim().ifBlank { DEFAULT_DATA_UUID }
        val control = controlCharacteristicUuid.trim().ifBlank { DEFAULT_CONTROL_UUID }
        val status = statusCharacteristicUuid.trim().ifBlank { DEFAULT_STATUS_UUID }

        return copy(
            preferredDeviceNamePrefix = deviceName,
            serviceUuid = service,
            dataCharacteristicUuid = data,
            controlCharacteristicUuid = control,
            statusCharacteristicUuid = status,
            preferredMtu = preferredMtu.coerceIn(80, 517),
            ecgSampleRateHz = ecgSampleRateHz.coerceIn(100, 1_000),
            ppgSampleRateHz = ppgSampleRateHz.coerceIn(100, 1_000),
            ppgPhaseUs = ppgPhaseUs.coerceIn(0, 50_000),
            ppgLatencyUs = ppgLatencyUs.coerceIn(0, 200_000)
        )
    }

    fun validatedOrError(): ValidationError? {
        val targets = listOf(
            serviceUuid to "Service UUID",
            dataCharacteristicUuid to "Data Characteristic UUID",
            controlCharacteristicUuid to "Control Characteristic UUID",
            statusCharacteristicUuid to "Status Characteristic UUID"
        )

        val invalidField = targets.firstOrNull { (raw, _) ->
            runCatching { UUID.fromString(raw.trim()) }.isFailure
        }?.second

        return if (invalidField == null) {
            null
        } else {
            ValidationError("$invalidField 格式无效，请输入标准 UUID")
        }
    }

    companion object {
        const val DEFAULT_DEVICE_NAME_PREFIX = "ECG-PPG-Terminal"
        const val DEFAULT_SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0"
        const val DEFAULT_DATA_UUID = "12345678-1234-5678-1234-56789abcdef1"
        const val DEFAULT_CONTROL_UUID = "12345678-1234-5678-1234-56789abcdef2"
        const val DEFAULT_STATUS_UUID = "12345678-1234-5678-1234-56789abcdef3"
        const val DEFAULT_MTU = 247
        const val DEFAULT_ECG_SAMPLE_RATE_HZ = 250
        const val DEFAULT_PPG_SAMPLE_RATE_HZ = 400
        const val DEFAULT_PPG_PHASE_US = 1_250
        const val DEFAULT_PPG_LATENCY_US = 0
    }
}

enum class SessionQualityGrade {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    INVALID
}

data class MeasurementSession(
    val sessionId: String,
    val deviceId: String,
    val firmwareVersion: String?,
    val ecgSampleRateHz: Int,
    val ppgSampleRateHz: Int,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val qualityGrade: SessionQualityGrade = SessionQualityGrade.INVALID,
    val droppedFrameCount: Int = 0,
    val algorithmVersion: String = "algo-v2.0",
    val modelVersion: String = "llm-v2.0",
    val notes: String? = null
)

enum class SessionEventType {
    LINK,
    PACKET_LOSS,
    QUALITY_GATE,
    METRIC_OUTPUT,
    USER_ACTION,
    ERROR
}

data class SessionEvent(
    val sessionId: String,
    val eventType: SessionEventType,
    val message: String,
    val payloadJson: String? = null,
    val createdAt: Instant = Instant.now()
)

enum class ExportFormat {
    CSV,
    JSON
}
