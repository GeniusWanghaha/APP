package com.photosentinel.health.infrastructure.signal

import com.photosentinel.health.domain.model.BleRuntimeConfig
import com.photosentinel.health.domain.model.HardwareFrame
import com.photosentinel.health.domain.model.HardwareStatusSnapshot
import kotlin.math.roundToInt

data class TimedIntSample(
    val timestampMicros: Long,
    val value: Int
)

data class ReconstructedFrame(
    val frame: HardwareFrame,
    val ecgTimeline: List<TimedIntSample>,
    val ppgRedTimeline: List<TimedIntSample>,
    val ppgIrTimeline: List<TimedIntSample>,
    val droppedFrameIds: List<Int>,
    val displayCompensationApplied: Boolean,
    val metricsUsable: Boolean
)

class TimelineReconstructor {
    private var lastFrameId: Int? = null
    private var lastEcgValue: Int? = null
    private var lastPpgRedValue: Int? = null
    private var lastPpgIrValue: Int? = null
    private var lastBaseTimestampMicros: Long = Long.MIN_VALUE

    fun reconstruct(
        frame: HardwareFrame,
        status: HardwareStatusSnapshot?,
        config: BleRuntimeConfig
    ): ReconstructedFrame {
        val safeConfig = config.sanitized()
        val ecgPeriodUs = (1_000_000.0 / safeConfig.ecgSampleRateHz.toDouble()).roundToInt().toLong()
        val ppgPeriodUs = (1_000_000.0 / safeConfig.ppgSampleRateHz.toDouble()).roundToInt().toLong()

        val stableBaseTimestamp = normalizeBaseTimestamp(frame.baseTimestampMicros, ecgPeriodUs)
        val ppgPhaseUs = status?.ppgPhaseUs?.takeIf { it >= 0 }?.toLong()
            ?: safeConfig.ppgPhaseUs.toLong()
        val ppgLatencyUs = status?.ppgLatencyUs?.takeIf { it >= 0 }?.toLong()
            ?: safeConfig.ppgLatencyUs.toLong()

        val droppedFrameIds = computeDroppedFrameIds(frame.frameId)
        val shortGap = droppedFrameIds.isNotEmpty() && droppedFrameIds.size <= SHORT_GAP_FRAME_THRESHOLD

        val ecgTimeline = buildTimeline(
            samples = frame.ecgSamples,
            baseMicros = stableBaseTimestamp,
            periodUs = ecgPeriodUs
        )
        val ppgBase = stableBaseTimestamp + ppgPhaseUs + ppgLatencyUs
        val ppgRedTimeline = buildTimeline(
            samples = frame.ppgRedSamples,
            baseMicros = ppgBase,
            periodUs = ppgPeriodUs
        )
        val ppgIrTimeline = buildTimeline(
            samples = frame.ppgIrSamples,
            baseMicros = ppgBase,
            periodUs = ppgPeriodUs
        )

        val compensatedEcg = if (shortGap) {
            prependInterpolatedGap(
                timeline = ecgTimeline,
                previousValue = lastEcgValue,
                gapFrameCount = droppedFrameIds.size,
                frameSampleCount = frame.ecgSamples.size,
                periodUs = ecgPeriodUs
            )
        } else {
            ecgTimeline
        }
        val compensatedRed = if (shortGap) {
            prependInterpolatedGap(
                timeline = ppgRedTimeline,
                previousValue = lastPpgRedValue,
                gapFrameCount = droppedFrameIds.size,
                frameSampleCount = frame.ppgRedSamples.size,
                periodUs = ppgPeriodUs
            )
        } else {
            ppgRedTimeline
        }
        val compensatedIr = if (shortGap) {
            prependInterpolatedGap(
                timeline = ppgIrTimeline,
                previousValue = lastPpgIrValue,
                gapFrameCount = droppedFrameIds.size,
                frameSampleCount = frame.ppgIrSamples.size,
                periodUs = ppgPeriodUs
            )
        } else {
            ppgIrTimeline
        }

        lastFrameId = frame.frameId
        lastBaseTimestampMicros = stableBaseTimestamp
        lastEcgValue = frame.ecgSamples.lastOrNull() ?: lastEcgValue
        lastPpgRedValue = frame.ppgRedSamples.lastOrNull() ?: lastPpgRedValue
        lastPpgIrValue = frame.ppgIrSamples.lastOrNull() ?: lastPpgIrValue

        return ReconstructedFrame(
            frame = frame,
            ecgTimeline = compensatedEcg,
            ppgRedTimeline = compensatedRed,
            ppgIrTimeline = compensatedIr,
            droppedFrameIds = droppedFrameIds,
            displayCompensationApplied = shortGap,
            metricsUsable = droppedFrameIds.isEmpty()
        )
    }

    fun reset() {
        lastFrameId = null
        lastEcgValue = null
        lastPpgRedValue = null
        lastPpgIrValue = null
        lastBaseTimestampMicros = Long.MIN_VALUE
    }

    private fun normalizeBaseTimestamp(
        currentBaseMicros: Long,
        ecgPeriodUs: Long
    ): Long {
        if (lastBaseTimestampMicros == Long.MIN_VALUE) {
            return currentBaseMicros
        }

        val expectedMin = lastBaseTimestampMicros + ecgPeriodUs
        return if (currentBaseMicros + JITTER_TOLERANCE_US < expectedMin) {
            expectedMin
        } else {
            currentBaseMicros
        }
    }

    private fun buildTimeline(
        samples: List<Int>,
        baseMicros: Long,
        periodUs: Long
    ): List<TimedIntSample> {
        return samples.mapIndexed { index, value ->
            TimedIntSample(
                timestampMicros = baseMicros + index * periodUs,
                value = value
            )
        }
    }

    private fun prependInterpolatedGap(
        timeline: List<TimedIntSample>,
        previousValue: Int?,
        gapFrameCount: Int,
        frameSampleCount: Int,
        periodUs: Long
    ): List<TimedIntSample> {
        val first = timeline.firstOrNull() ?: return timeline
        val prev = previousValue ?: return timeline
        val missingSamples = (gapFrameCount * frameSampleCount).coerceAtLeast(0)
        if (missingSamples == 0) {
            return timeline
        }

        val interpolation = (1..missingSamples).map { index ->
            val ratio = index.toDouble() / (missingSamples + 1)
            val interpolatedValue = prev + ((first.value - prev) * ratio).roundToInt()
            TimedIntSample(
                timestampMicros = first.timestampMicros - (missingSamples - index + 1) * periodUs,
                value = interpolatedValue
            )
        }
        return interpolation + timeline
    }

    private fun computeDroppedFrameIds(currentFrameId: Int): List<Int> {
        val previous = lastFrameId ?: return emptyList()
        val distance = if (currentFrameId >= previous) {
            currentFrameId - previous
        } else {
            currentFrameId + FRAME_ID_MOD - previous
        }

        val gap = (distance - 1).coerceAtLeast(0)
        if (gap == 0 || gap > MAX_TRACKABLE_GAP) {
            return emptyList()
        }

        return (1..gap).map { offset ->
            (previous + offset) % FRAME_ID_MOD
        }
    }

    private companion object {
        private const val FRAME_ID_MOD = 65_536
        private const val SHORT_GAP_FRAME_THRESHOLD = 2
        private const val MAX_TRACKABLE_GAP = 128
        private const val JITTER_TOLERANCE_US = 3_000L
    }
}
