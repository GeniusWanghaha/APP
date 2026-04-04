package com.photosentinel.health.infrastructure.signal

import com.photosentinel.health.domain.model.BleRuntimeConfig
import com.photosentinel.health.domain.model.HardwareFrame
import com.photosentinel.health.domain.model.HardwareStatusSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineReconstructorTest {
    @Test
    fun reconstruct_withoutGap_shouldKeepMetricsUsable() {
        val reconstructor = TimelineReconstructor()
        val frame = createFrame(frameId = 100, baseMicros = 1_000_000L)

        val result = reconstructor.reconstruct(
            frame = frame,
            status = createStatus(ppgPhaseUs = 1_250, ppgLatencyUs = 100),
            config = BleRuntimeConfig()
        )

        assertTrue(result.metricsUsable)
        assertFalse(result.displayCompensationApplied)
        assertTrue(result.droppedFrameIds.isEmpty())
        assertEquals(10, result.ecgTimeline.size)
        assertEquals(16, result.ppgRedTimeline.size)
        assertEquals(1_001_350L, result.ppgRedTimeline.first().timestampMicros)
    }

    @Test
    fun reconstruct_withShortGap_shouldInterpolateForDisplayAndGateMetrics() {
        val reconstructor = TimelineReconstructor()
        reconstructor.reconstruct(
            frame = createFrame(frameId = 100, baseMicros = 1_000_000L),
            status = createStatus(ppgPhaseUs = 1_250, ppgLatencyUs = 0),
            config = BleRuntimeConfig()
        )

        val result = reconstructor.reconstruct(
            frame = createFrame(frameId = 102, baseMicros = 1_008_000L),
            status = createStatus(ppgPhaseUs = 1_250, ppgLatencyUs = 0),
            config = BleRuntimeConfig()
        )

        assertFalse(result.metricsUsable)
        assertTrue(result.displayCompensationApplied)
        assertEquals(listOf(101), result.droppedFrameIds)
        assertTrue(result.ecgTimeline.size > 10)
    }

    private fun createFrame(
        frameId: Int,
        baseMicros: Long
    ): HardwareFrame {
        return HardwareFrame(
            frameId = frameId,
            baseTimestampMicros = baseMicros,
            ecgSamples = List(10) { 900 + it },
            ppgRedSamples = List(16) { 52_000 + it },
            ppgIrSamples = List(16) { 50_000 + it },
            stateFlags = 0x0180,
            crc = 0
        )
    }

    private fun createStatus(
        ppgPhaseUs: Int,
        ppgLatencyUs: Int
    ): HardwareStatusSnapshot {
        return HardwareStatusSnapshot(
            protocolVersion = 1,
            streamingEnabled = true,
            stateFlags = 0,
            selfTestPassBitmap = 0,
            selfTestFailBitmap = 0,
            ppgFifoOverflowCount = 0,
            ppgIntTimeoutCount = 0,
            bleBackpressureCount = 0,
            bleDroppedFrameCount = 0,
            i2cErrorCount = 0,
            adcSaturationCount = 0,
            ecgRingDropCount = 0,
            ppgRingDropCount = 0,
            generatedFrameCount = 0,
            transmittedFrameCount = 0,
            frameSequenceErrorCount = 0,
            ecgRingItems = 0,
            ppgRingItems = 0,
            bleQueueItems = 0,
            ecgRingHighWatermark = 0,
            ppgRingHighWatermark = 0,
            bleQueueHighWatermark = 0,
            mtu = 247,
            redLedPa = 0,
            irLedPa = 0,
            ppgPhaseUs = ppgPhaseUs,
            ppgLatencyUs = ppgLatencyUs,
            temperatureEnabled = false,
            logLevel = 0,
            sensorReady = true,
            fingerDetected = true
        )
    }
}
