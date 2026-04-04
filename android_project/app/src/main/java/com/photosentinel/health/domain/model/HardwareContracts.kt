package com.photosentinel.health.domain.model

data class HardwareFrame(
    val frameId: Int,
    val baseTimestampMicros: Long,
    val ecgSamples: List<Int>,
    val ppgRedSamples: List<Int>,
    val ppgIrSamples: List<Int>,
    val stateFlags: Int,
    val crc: Int
)

data class HardwareStatusSnapshot(
    val protocolVersion: Int,
    val streamingEnabled: Boolean,
    val stateFlags: Int,
    val selfTestPassBitmap: Long,
    val selfTestFailBitmap: Long,
    val ppgFifoOverflowCount: Long,
    val ppgIntTimeoutCount: Long,
    val bleBackpressureCount: Long,
    val bleDroppedFrameCount: Long,
    val i2cErrorCount: Long,
    val adcSaturationCount: Long,
    val ecgRingDropCount: Long,
    val ppgRingDropCount: Long,
    val generatedFrameCount: Long,
    val transmittedFrameCount: Long,
    val frameSequenceErrorCount: Long,
    val ecgRingItems: Int,
    val ppgRingItems: Int,
    val bleQueueItems: Int,
    val ecgRingHighWatermark: Int,
    val ppgRingHighWatermark: Int,
    val bleQueueHighWatermark: Int,
    val mtu: Int,
    val redLedPa: Int,
    val irLedPa: Int,
    val ppgPhaseUs: Int,
    val ppgLatencyUs: Int,
    val temperatureEnabled: Boolean,
    val logLevel: Int,
    val sensorReady: Boolean,
    val fingerDetected: Boolean
)

sealed interface DeviceLinkState {
    data class Disconnected(
        val reason: String = "硬件未连接"
    ) : DeviceLinkState

    data object Scanning : DeviceLinkState

    data class Connecting(
        val target: String
    ) : DeviceLinkState

    data class Connected(
        val deviceName: String
    ) : DeviceLinkState
}
