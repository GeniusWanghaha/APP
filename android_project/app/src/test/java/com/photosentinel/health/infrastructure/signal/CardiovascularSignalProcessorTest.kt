package com.photosentinel.health.infrastructure.signal

import com.photosentinel.health.domain.model.HardwareFrame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class CardiovascularSignalProcessorTest {
    @Test
    fun ingest_lowQualityFlags_shouldFallbackToBaselineTier() {
        val processor = CardiovascularSignalProcessor()
        var output: RealtimeCardioMetrics? = null

        repeat(80) { index ->
            output = processor.ingest(
                frame = createFrame(
                    frameId = index,
                    baseMicros = 1_000_000L + index * 40_000L,
                    stateFlags = 0 // no sensor-ready / finger-detected
                )
            )
        }

        val finalOutput = requireNotNull(output)
        assertNotNull(finalOutput)
        assertTrue(finalOutput.outputTier == MetricOutputTier.BASELINE)
        assertTrue(finalOutput.signalQuality < 0.65)
    }

    @Test
    fun ingest_stableQuality_shouldEnableAdvancedOrResearchTier() {
        val processor = CardiovascularSignalProcessor()
        var output: RealtimeCardioMetrics? = null

        repeat(120) { index ->
            output = processor.ingest(
                frame = createFrame(
                    frameId = index,
                    baseMicros = 2_000_000L + index * 40_000L,
                    stateFlags = 0x0180 // sensor ready + finger detected
                )
            )
        }

        val finalOutput = requireNotNull(output)
        assertNotNull(finalOutput)
        assertTrue(
            finalOutput.outputTier == MetricOutputTier.ADVANCED ||
                finalOutput.outputTier == MetricOutputTier.RESEARCH
        )
        assertTrue(finalOutput.signalQuality >= 0.55)
    }

    @Test
    fun ingest_mixedHardwareFlags_shouldNotCollapseToFloorValue() {
        val processor = CardiovascularSignalProcessor()
        var output: RealtimeCardioMetrics? = null

        repeat(120) { index ->
            output = processor.ingest(
                frame = createFrame(
                    frameId = index,
                    baseMicros = 3_000_000L + index * 40_000L,
                    stateFlags = 0x00A7 // leads-off + ADC sat + sensor-ready, finger bit absent
                )
            )
        }

        val finalOutput = requireNotNull(output)
        assertNotNull(finalOutput)
        assertTrue(finalOutput.signalQuality > 0.12)
        assertTrue(finalOutput.outputTier == MetricOutputTier.BASELINE)
    }

    @Test
    fun ingest_decorrelatedPpg_shouldLowerSignalQualityThanCorrelatedPpg() {
        val correlatedProcessor = CardiovascularSignalProcessor()
        val decorrelatedProcessor = CardiovascularSignalProcessor()
        var correlatedOutput: RealtimeCardioMetrics? = null
        var decorrelatedOutput: RealtimeCardioMetrics? = null

        repeat(140) { index ->
            correlatedOutput = correlatedProcessor.ingest(
                frame = createFrame(
                    frameId = index,
                    baseMicros = 4_000_000L + index * 40_000L,
                    stateFlags = 0x0180
                )
            )
            decorrelatedOutput = decorrelatedProcessor.ingest(
                frame = createFrame(
                    frameId = index,
                    baseMicros = 5_000_000L + index * 40_000L,
                    stateFlags = 0x0180,
                    redFrequencyScale = 1.2,
                    redPhase = 1.3
                )
            )
        }

        val stable = requireNotNull(correlatedOutput)
        val noisy = requireNotNull(decorrelatedOutput)
        assertTrue(stable.signalQuality > noisy.signalQuality)
    }

    private fun createFrame(
        frameId: Int,
        baseMicros: Long,
        stateFlags: Int,
        redFrequencyScale: Double = 0.25,
        redPhase: Double = 0.0
    ): ReconstructedFrame {
        val ecgPattern = listOf(120, 180, 260, 950, 300, 210, 180, 220, 260, 180)
        val ppgRed = List(16) { index ->
            val t = (frameId * 16 + index) / 16.0
            (52_000 + sin(t * redFrequencyScale + redPhase) * 1_200).toInt()
        }
        val ppgIr = List(16) { index ->
            val t = (frameId * 16 + index) / 16.0
            (50_500 + sin(t * 0.25 + 0.2) * 1_000).toInt()
        }

        val frame = HardwareFrame(
            frameId = frameId,
            baseTimestampMicros = baseMicros,
            ecgSamples = ecgPattern,
            ppgRedSamples = ppgRed,
            ppgIrSamples = ppgIr,
            stateFlags = stateFlags,
            crc = 0
        )

        val ecgTimeline = ecgPattern.mapIndexed { index, value ->
            TimedIntSample(
                timestampMicros = baseMicros + index * 4_000L,
                value = value
            )
        }
        val ppgRedTimeline = ppgRed.mapIndexed { index, value ->
            TimedIntSample(
                timestampMicros = baseMicros + 1_250L + index * 2_500L,
                value = value
            )
        }
        val ppgIrTimeline = ppgIr.mapIndexed { index, value ->
            TimedIntSample(
                timestampMicros = baseMicros + 1_250L + index * 2_500L,
                value = value
            )
        }

        return ReconstructedFrame(
            frame = frame,
            ecgTimeline = ecgTimeline,
            ppgRedTimeline = ppgRedTimeline,
            ppgIrTimeline = ppgIrTimeline,
            droppedFrameIds = emptyList(),
            displayCompensationApplied = false,
            metricsUsable = true
        )
    }
}
