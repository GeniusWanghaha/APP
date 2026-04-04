package com.photosentinel.health.infrastructure.signal

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class MetricOutputTier {
    BASELINE,
    ADVANCED,
    RESEARCH
}

enum class SignalQualityGrade {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR
}

enum class RiskLevel {
    INFO,
    WARN,
    HIGH
}

data class RiskEvent(
    val code: String,
    val title: String,
    val confidence: Double,
    val level: RiskLevel
)

data class RealtimeCardioMetrics(
    val heartRateBpm: Int,
    val spo2Percent: Double,
    val pttMs: Double,
    val pwvMs: Double,
    val elasticityScore: Int,
    val vascularAge: Int,
    val signalQuality: Double,
    val reflectionIndex: Double,
    val outputTier: MetricOutputTier,
    val qualityGrade: SignalQualityGrade,
    val qualityTips: List<String>,
    val rrMeanMs: Double?,
    val sdnnMs: Double?,
    val rmssdMs: Double?,
    val pnn50Percent: Double?,
    val perfusionIndex: Double?,
    val riseTimeMs: Double?,
    val halfWidthMs: Double?,
    val patMs: Double?,
    val pwttMs: Double?,
    val beatPulseConsistency: Double?,
    val arrhythmiaIndex: Double?,
    val riskEvents: List<RiskEvent>,
    val bloodPressureTrend: String
)

class CardiovascularSignalProcessor {
    private data class TimedValue(
        val timestampMicros: Long,
        val value: Double
    )

    private data class EcgFeaturePoint(
        val timestampMicros: Long,
        val bandAbs: Double,
        val integrated: Double
    )

    private data class Spo2Estimate(
        val spo2Percent: Double,
        val ratio: Double,
        val correlation: Double
    )

    private val ecgNotch = NotchFilter(
        centerFrequencyHz = ECG_NOTCH_HZ,
        sampleRateHz = ECG_SAMPLE_RATE_HZ,
        qFactor = 12.0
    )
    private val ecgHighPass = HighPassFilter(cutoffHz = 0.5, sampleRateHz = ECG_SAMPLE_RATE_HZ)
    private val ecgLowPass = LowPassFilter(cutoffHz = 40.0, sampleRateHz = ECG_SAMPLE_RATE_HZ)

    private val ppgDcFilter = LowPassFilter(cutoffHz = 0.4, sampleRateHz = PPG_SAMPLE_RATE_HZ)
    private val ppgHighPass = HighPassFilter(cutoffHz = 0.5, sampleRateHz = PPG_SAMPLE_RATE_HZ)
    private val ppgLowPass = LowPassFilter(cutoffHz = 8.0, sampleRateHz = PPG_SAMPLE_RATE_HZ)

    private val ecgHistory = ArrayDeque<TimedValue>()
    private val ecgIntegratedHistory = ArrayDeque<TimedValue>()
    private val redRawHistory = ArrayDeque<TimedValue>()
    private val irRawHistory = ArrayDeque<TimedValue>()
    private val ppgAcHistory = ArrayDeque<TimedValue>()

    private val rPeakTimestamps = ArrayDeque<Long>()
    private val rrIntervalsMs = ArrayDeque<Double>()
    private val pttHistoryMs = ArrayDeque<Double>()
    private val patHistoryMs = ArrayDeque<Double>()
    private val riseTimeHistoryMs = ArrayDeque<Double>()
    private val halfWidthHistoryMs = ArrayDeque<Double>()
    private val ppgPulseAmplitudeHistory = ArrayDeque<Double>()

    private val ecgIntegratorWindow = ArrayDeque<Double>()
    private var ecgIntegratorSum = 0.0
    private var lastEcgBandValue = 0.0

    private var ecgPrevFeature2: EcgFeaturePoint? = null
    private var ecgPrevFeature1: EcgFeaturePoint? = null

    private var ppgPrev2: TimedValue? = null
    private var ppgPrev1: TimedValue? = null

    private var lastRPeakMicros = Long.MIN_VALUE
    private var lastPpgFootMicros = Long.MIN_VALUE
    private var pendingFoot: TimedValue? = null
    private var rrMissTimeoutMicros = ECG_DEFAULT_MISSED_BEAT_TIMEOUT_US

    private var signalLevelIntegrated = 0.0
    private var noiseLevelIntegrated = 0.0
    private var signalLevelBand = 0.0
    private var noiseLevelBand = 0.0
    private var adaptiveThresholdInitialized = false

    private var latestPpgCorrelation = 0.0
    private var latestSpo2Ratio = 0.0

    private var smoothedHeartRate = 72.0
    private var smoothedSpo2 = 98.0
    private var smoothedPttMs = 130.0

    fun ingest(frame: ReconstructedFrame): RealtimeCardioMetrics? {
        if (frame.ecgTimeline.isEmpty() || frame.ppgIrTimeline.isEmpty()) {
            return null
        }

        processEcg(frame)
        processPpg(frame)

        val nowMicros = max(
            frame.ecgTimeline.lastOrNull()?.timestampMicros ?: frame.frame.baseTimestampMicros,
            frame.ppgIrTimeline.lastOrNull()?.timestampMicros ?: frame.frame.baseTimestampMicros
        )
        pruneOldData(nowMicros)

        val heartRate = computeHeartRate() ?: smoothedHeartRate
        val spo2 = computeSpo2(nowMicros) ?: smoothedSpo2
        val pttMs = computePttMs() ?: smoothedPttMs

        smoothedHeartRate = smooth(smoothedHeartRate, heartRate, alpha = 0.25)
        smoothedSpo2 = smooth(smoothedSpo2, spo2, alpha = 0.2)
        smoothedPttMs = smooth(smoothedPttMs, pttMs, alpha = 0.2)

        val rrMeanMs = rrIntervalsMs.takeIf { it.size >= 2 }?.average()
        val sdnnMs = rrIntervalsMs.takeIf { it.size >= 4 }?.stdDev()
        val rmssdMs = rrIntervalsMs.takeIf { it.size >= 4 }?.rmssd()
        val pnn50Percent = rrIntervalsMs.takeIf { it.size >= 6 }?.pnn50()

        val perfusionIndex = computePerfusionIndex(nowMicros)
        val riseTimeMs = riseTimeHistoryMs.takeIf { it.isNotEmpty() }?.average()
        val halfWidthMs = halfWidthHistoryMs.takeIf { it.isNotEmpty() }?.average()
        val patMs = patHistoryMs.takeIf { it.isNotEmpty() }?.average()
        val pwttMs = pttHistoryMs.takeIf { it.isNotEmpty() }?.average()

        val beatPulseConsistency = computeBeatPulseConsistency()
        val arrhythmiaIndex = computeArrhythmiaIndex(rrMeanMs, sdnnMs)

        val quality = estimateSignalQuality(
            stateFlags = frame.frame.stateFlags,
            metricsUsable = frame.metricsUsable,
            beatPulseConsistency = beatPulseConsistency
        )
        val qualityTips = buildQualityTips(frame.frame.stateFlags, quality, frame.metricsUsable)
        val qualityGrade = qualityToGrade(quality)
        val outputTier = qualityToTier(quality, frame.metricsUsable)

        val boundedPtt = smoothedPttMs.coerceIn(80.0, 400.0)
        val pwv = (PTT_REFERENCE_DISTANCE_METERS / (boundedPtt / 1_000.0)).coerceIn(3.0, 15.0)
        val elasticity = (
            100.0 -
                (pwv - 6.0) * 12.0 -
                abs(smoothedHeartRate - 72.0) * 0.25 +
                quality * 10.0
            ).roundToInt().coerceIn(0, 100)
        val vascularAge = (
            23.0 +
                (pwv - 5.0) * 8.0 +
                if (smoothedHeartRate > 90.0) 2.0 else 0.0
            ).roundToInt().coerceIn(18, 80)

        val reflectionIndex = computeReflectionIndex(nowMicros)
        val riskEvents = evaluateRiskEvents(
            heartRate = smoothedHeartRate,
            rrMeanMs = rrMeanMs,
            arrhythmiaIndex = arrhythmiaIndex,
            beatPulseConsistency = beatPulseConsistency,
            quality = quality
        )
        val bpTrendText = buildBloodPressureTrend(pwttMs)

        val baselineOnly = outputTier == MetricOutputTier.BASELINE

        return RealtimeCardioMetrics(
            heartRateBpm = smoothedHeartRate.roundToInt().coerceIn(35, 190),
            spo2Percent = smoothedSpo2.coerceIn(80.0, 100.0),
            pttMs = boundedPtt,
            pwvMs = pwv,
            elasticityScore = elasticity,
            vascularAge = vascularAge,
            signalQuality = quality.coerceIn(0.0, 1.0),
            reflectionIndex = reflectionIndex,
            outputTier = outputTier,
            qualityGrade = qualityGrade,
            qualityTips = qualityTips,
            rrMeanMs = rrMeanMs,
            sdnnMs = if (baselineOnly) null else sdnnMs,
            rmssdMs = if (baselineOnly) null else rmssdMs,
            pnn50Percent = if (baselineOnly) null else pnn50Percent,
            perfusionIndex = perfusionIndex,
            riseTimeMs = if (baselineOnly) null else riseTimeMs,
            halfWidthMs = if (baselineOnly) null else halfWidthMs,
            patMs = if (baselineOnly) null else patMs,
            pwttMs = if (baselineOnly) null else pwttMs,
            beatPulseConsistency = if (baselineOnly) null else beatPulseConsistency,
            arrhythmiaIndex = if (baselineOnly) null else arrhythmiaIndex,
            riskEvents = riskEvents,
            bloodPressureTrend = bpTrendText
        )
    }

    private fun processEcg(frame: ReconstructedFrame) {
        frame.ecgTimeline.forEach { sample ->
            val filtered = ecgLowPass.process(
                ecgHighPass.process(
                    ecgNotch.process(sample.value.toDouble())
                )
            )
            val point = TimedValue(sample.timestampMicros, filtered)
            ecgHistory.addLast(point)
            val feature = buildEcgFeature(point)
            ecgIntegratedHistory.addLast(
                TimedValue(
                    timestampMicros = feature.timestampMicros,
                    value = feature.integrated
                )
            )
            detectRPeak(feature)
        }
    }

    private fun processPpg(frame: ReconstructedFrame) {
        val size = minOf(frame.ppgRedTimeline.size, frame.ppgIrTimeline.size)
        repeat(size) { index ->
            val red = frame.ppgRedTimeline[index]
            val ir = frame.ppgIrTimeline[index]

            redRawHistory.addLast(TimedValue(red.timestampMicros, red.value.toDouble()))
            irRawHistory.addLast(TimedValue(ir.timestampMicros, ir.value.toDouble()))

            val dc = ppgDcFilter.process(ir.value.toDouble())
            val ac = ir.value - dc
            val filtered = ppgLowPass.process(ppgHighPass.process(ac))
            val point = TimedValue(ir.timestampMicros, filtered)
            ppgAcHistory.addLast(point)
            detectPpgFootAndPeak(point)
        }
    }

    private fun buildEcgFeature(point: TimedValue): EcgFeaturePoint {
        val derivative = point.value - lastEcgBandValue
        lastEcgBandValue = point.value

        val squared = derivative * derivative
        ecgIntegratorWindow.addLast(squared)
        ecgIntegratorSum += squared
        while (ecgIntegratorWindow.size > ECG_MWI_WINDOW_SAMPLES) {
            ecgIntegratorSum -= ecgIntegratorWindow.removeFirst()
        }

        val integrated = if (ecgIntegratorWindow.size < ECG_MWI_MIN_SAMPLES) {
            0.0
        } else {
            ecgIntegratorSum / ecgIntegratorWindow.size.toDouble()
        }

        return EcgFeaturePoint(
            timestampMicros = point.timestampMicros,
            bandAbs = abs(point.value),
            integrated = integrated
        )
    }

    private fun detectRPeak(current: EcgFeaturePoint) {
        val prev1 = ecgPrevFeature1
        val prev2 = ecgPrevFeature2
        if (prev1 != null && prev2 != null) {
            val isLocalMax = prev1.integrated > prev2.integrated && prev1.integrated >= current.integrated
            if (isLocalMax) {
                evaluateEcgPeakCandidate(prev1)
            }
        }
        ecgPrevFeature2 = ecgPrevFeature1
        ecgPrevFeature1 = current
    }

    private fun evaluateEcgPeakCandidate(candidate: EcgFeaturePoint) {
        if (!adaptiveThresholdInitialized) {
            noiseLevelIntegrated = candidate.integrated.coerceAtLeast(ECG_MIN_LEVEL)
            signalLevelIntegrated = noiseLevelIntegrated * 3.0
            noiseLevelBand = candidate.bandAbs.coerceAtLeast(ECG_MIN_LEVEL)
            signalLevelBand = noiseLevelBand * 2.0
            adaptiveThresholdInitialized = true
        }

        val thresholdIntegrated = dynamicThreshold(signalLevelIntegrated, noiseLevelIntegrated)
        val thresholdBand = dynamicThreshold(signalLevelBand, noiseLevelBand)
        val abovePrimaryThreshold =
            candidate.integrated >= thresholdIntegrated &&
                candidate.bandAbs >= thresholdBand

        val aboveSecondaryThreshold =
            candidate.integrated >= thresholdIntegrated * ECG_SEARCHBACK_THRESHOLD_SCALE &&
                candidate.bandAbs >= thresholdBand * ECG_SEARCHBACK_THRESHOLD_SCALE

        val passedRefractory = candidate.timestampMicros - lastRPeakMicros >= R_PEAK_REFRACTORY_US
        val overdueForBeat =
            lastRPeakMicros != Long.MIN_VALUE &&
                candidate.timestampMicros - lastRPeakMicros > rrMissTimeoutMicros

        if (passedRefractory && (abovePrimaryThreshold || (overdueForBeat && aboveSecondaryThreshold))) {
            registerRPeak(candidate.timestampMicros)
            updateSignalLevels(candidate)
        } else {
            updateNoiseLevels(candidate)
        }
    }

    private fun updateSignalLevels(candidate: EcgFeaturePoint) {
        signalLevelIntegrated = if (signalLevelIntegrated == 0.0) {
            candidate.integrated
        } else {
            ECG_LEVEL_FORGET_FACTOR * signalLevelIntegrated +
                (1.0 - ECG_LEVEL_FORGET_FACTOR) * candidate.integrated
        }
        signalLevelBand = if (signalLevelBand == 0.0) {
            candidate.bandAbs
        } else {
            ECG_LEVEL_FORGET_FACTOR * signalLevelBand +
                (1.0 - ECG_LEVEL_FORGET_FACTOR) * candidate.bandAbs
        }
    }

    private fun updateNoiseLevels(candidate: EcgFeaturePoint) {
        noiseLevelIntegrated = if (noiseLevelIntegrated == 0.0) {
            candidate.integrated
        } else {
            ECG_LEVEL_FORGET_FACTOR * noiseLevelIntegrated +
                (1.0 - ECG_LEVEL_FORGET_FACTOR) * candidate.integrated
        }
        noiseLevelBand = if (noiseLevelBand == 0.0) {
            candidate.bandAbs
        } else {
            ECG_LEVEL_FORGET_FACTOR * noiseLevelBand +
                (1.0 - ECG_LEVEL_FORGET_FACTOR) * candidate.bandAbs
        }
    }

    private fun dynamicThreshold(signal: Double, noise: Double): Double {
        val safeNoise = noise.coerceAtLeast(ECG_MIN_LEVEL)
        val safeSignal = signal.coerceAtLeast(safeNoise)
        return safeNoise + ECG_THRESHOLD_GAIN * (safeSignal - safeNoise)
    }

    private fun detectPpgFootAndPeak(current: TimedValue) {
        val prev1 = ppgPrev1
        val prev2 = ppgPrev2
        if (prev1 != null && prev2 != null) {
            val isLocalMin = prev1.value < prev2.value && prev1.value <= current.value
            val isLocalMax = prev1.value > prev2.value && prev1.value >= current.value
            val noise = valuesWithin(
                source = ppgAcHistory,
                nowMicros = current.timestampMicros,
                windowMicros = PPG_FOOT_WINDOW_US
            ).stdDev()
            val minSlopeThreshold = max(PPG_MIN_SLOPE_THRESHOLD, noise * PPG_SLOPE_NOISE_GAIN)
            val passedRefractory = prev1.timestampMicros - lastPpgFootMicros >= PPG_FOOT_REFRACTORY_US

            if (isLocalMin && passedRefractory && current.value - prev1.value > minSlopeThreshold) {
                registerPpgFoot(prev1)
            }

            if (isLocalMax && pendingFoot != null) {
                val foot = pendingFoot!!
                val pulseAmplitude = prev1.value - foot.value
                val minPulseAmplitude = ppgPulseMinAmplitudeThreshold()
                if (
                    pulseAmplitude >= minPulseAmplitude &&
                    pulseAmplitude <= PPG_MAX_PULSE_AMPLITUDE
                ) {
                    ppgPulseAmplitudeHistory.pushLimited(pulseAmplitude, MORPH_HISTORY_LIMIT)
                    registerPpgPeak(prev1)
                }
            } else if (pendingFoot != null) {
                val elapsed = current.timestampMicros - pendingFoot!!.timestampMicros
                if (elapsed > PPG_MAX_PULSE_WIDTH_US) {
                    pendingFoot = null
                }
            }
        }
        ppgPrev2 = ppgPrev1
        ppgPrev1 = current
    }

    private fun registerRPeak(timestampMicros: Long) {
        if (lastRPeakMicros != Long.MIN_VALUE) {
            val rrMs = (timestampMicros - lastRPeakMicros) / 1_000.0
            if (rrMs in 320.0..2_000.0) {
                rrIntervalsMs.pushLimited(rrMs, RR_HISTORY_LIMIT)
                val rrAvgMicros = rrIntervalsMs.average() * 1_000.0
                rrMissTimeoutMicros = (rrAvgMicros * ECG_MISSED_BEAT_FACTOR)
                    .toLong()
                    .coerceIn(ECG_MIN_MISSED_BEAT_TIMEOUT_US, ECG_MAX_MISSED_BEAT_TIMEOUT_US)
            }
        }
        lastRPeakMicros = timestampMicros
        rPeakTimestamps.pushLimited(timestampMicros, R_PEAK_HISTORY_LIMIT)
    }

    private fun registerPpgFoot(foot: TimedValue) {
        lastPpgFootMicros = foot.timestampMicros
        pendingFoot = foot
    }

    private fun registerPpgPeak(peak: TimedValue) {
        val foot = pendingFoot ?: return
        pendingFoot = null
        if (peak.timestampMicros <= foot.timestampMicros) {
            return
        }

        val footToPeakUs = peak.timestampMicros - foot.timestampMicros
        if (footToPeakUs !in FOOT_TO_PEAK_MIN_US..FOOT_TO_PEAK_MAX_US) {
            return
        }

        val matchingRPeak = findMatchingRPeakForPulse(
            footTimestampMicros = foot.timestampMicros,
            peakTimestampMicros = peak.timestampMicros
        ) ?: return

        val pttMs = (foot.timestampMicros - matchingRPeak) / 1_000.0
        val patMs = (peak.timestampMicros - matchingRPeak) / 1_000.0
        if (!isDelayStable(pttHistoryMs, pttMs) || !isDelayStable(patHistoryMs, patMs)) {
            return
        }

        pttHistoryMs.pushLimited(pttMs, PTT_HISTORY_LIMIT)
        patHistoryMs.pushLimited(patMs, PTT_HISTORY_LIMIT)

        val riseTimeMs = footToPeakUs / 1_000.0
        riseTimeHistoryMs.pushLimited(riseTimeMs, MORPH_HISTORY_LIMIT)
        halfWidthHistoryMs.pushLimited(riseTimeMs * 1.6, MORPH_HISTORY_LIMIT)
    }

    private fun ppgPulseMinAmplitudeThreshold(): Double {
        val adaptive = ppgPulseAmplitudeHistory.toList().medianOrNull()
            ?.times(PPG_PULSE_ADAPTIVE_GAIN)
            ?: 0.0
        return max(PPG_MIN_PULSE_AMPLITUDE, adaptive)
    }

    private fun findMatchingRPeakForPulse(
        footTimestampMicros: Long,
        peakTimestampMicros: Long
    ): Long? {
        for (rPeak in rPeakTimestamps.reversed()) {
            val rToFoot = footTimestampMicros - rPeak
            val rToPeak = peakTimestampMicros - rPeak
            val isOrdered = rPeak < footTimestampMicros && footTimestampMicros < peakTimestampMicros
            if (
                isOrdered &&
                rToFoot in R_TO_FOOT_MIN_US..R_TO_FOOT_MAX_US &&
                rToPeak in R_TO_PEAK_MIN_US..R_TO_PEAK_MAX_US
            ) {
                return rPeak
            }
        }
        return null
    }

    private fun isDelayStable(history: ArrayDeque<Double>, candidateMs: Double): Boolean {
        if (history.size < DELAY_JUMP_GUARD_MIN_HISTORY) {
            return true
        }
        val median = history.toList().medianOrNull() ?: return true
        val mad = history.map { abs(it - median) }.medianOrNull() ?: 0.0
        val threshold = max(DELAY_JUMP_MAD_SCALE * mad, DELAY_JUMP_MIN_MS)
        return abs(candidateMs - median) <= threshold
    }

    private fun computeHeartRate(): Double? {
        if (rrIntervalsMs.size < 2) {
            return null
        }
        val rrAvg = rrIntervalsMs.average()
        if (rrAvg <= 0.0) {
            return null
        }
        return 60_000.0 / rrAvg
    }

    private fun computeSpo2(nowMicros: Long): Double? {
        val redWindow = timedValuesWithin(redRawHistory, nowMicros, SPO2_WINDOW_US)
        val irWindow = timedValuesWithin(irRawHistory, nowMicros, SPO2_WINDOW_US)
        val size = minOf(redWindow.size, irWindow.size)
        if (size < SPO2_MIN_SAMPLE_COUNT) {
            latestPpgCorrelation = smooth(latestPpgCorrelation, 0.0, alpha = 0.15)
            return null
        }

        val red = DoubleArray(size) { redWindow[it].value }
        val ir = DoubleArray(size) { irWindow[it].value }

        val estimate = estimateSpo2FromWindow(red, ir)
        if (estimate == null) {
            latestPpgCorrelation = smooth(latestPpgCorrelation, 0.0, alpha = 0.15)
            return null
        }

        latestPpgCorrelation = estimate.correlation.coerceIn(0.0, 1.0)
        latestSpo2Ratio = estimate.ratio
        return estimate.spo2Percent
    }

    private fun estimateSpo2FromWindow(redRaw: DoubleArray, irRaw: DoubleArray): Spo2Estimate? {
        val n = minOf(redRaw.size, irRaw.size)
        if (n < SPO2_MIN_SAMPLE_COUNT) {
            return null
        }

        val irMean = irRaw.average()
        val redMean = redRaw.average()
        if (irMean <= 0.0 || redMean <= 0.0) {
            return null
        }

        val irDetrended = removeLinearTrend(DoubleArray(n) { irRaw[it] - irMean })
        val redDetrended = removeLinearTrend(DoubleArray(n) { redRaw[it] - redMean })
        val correlation = pearsonCorrelation(irDetrended, redDetrended)
        if (correlation < SPO2_MIN_CORRELATION) {
            return null
        }

        val smoothedIr = movingAverage(irDetrended, SPO2_MOVING_AVERAGE_WINDOW)
        val invertedIr = DoubleArray(smoothedIr.size) { index -> -smoothedIr[index] }
        val valleyThreshold = (
            invertedIr.average() + invertedIr.stdDev() * SPO2_VALLEY_THRESHOLD_STD_GAIN
            ).coerceAtLeast(0.0)
        val valleys = findPeaks(
            values = invertedIr,
            minHeight = valleyThreshold,
            minDistance = SPO2_MIN_VALLEY_DISTANCE_SAMPLES,
            maxCount = SPO2_MAX_VALLEY_COUNT
        )
        if (valleys.size < 2) {
            return null
        }

        val ratios = mutableListOf<Double>()
        valleys.zipWithNext().forEach { (start, end) ->
            val span = end - start
            if (span < SPO2_MIN_SEGMENT_SAMPLES) {
                return@forEach
            }
            val irPeakIndex = indexOfMax(irRaw, start, end)
            val redPeakIndex = indexOfMax(redRaw, start, end)
            if (irPeakIndex < 0 || redPeakIndex < 0) {
                return@forEach
            }

            val irDc = irRaw[irPeakIndex]
            val redDc = redRaw[redPeakIndex]
            if (irDc <= 0.0 || redDc <= 0.0) {
                return@forEach
            }

            val irBase = linearBaseline(irRaw[start], irRaw[end], irPeakIndex - start, span)
            val redBase = linearBaseline(redRaw[start], redRaw[end], redPeakIndex - start, span)
            val irAc = irRaw[irPeakIndex] - irBase
            val redAc = redRaw[redPeakIndex] - redBase
            if (irAc <= 0.0 || redAc <= 0.0) {
                return@forEach
            }

            val ratio = (redAc * irDc) / (irAc * redDc)
            if (ratio in SPO2_RATIO_MIN..SPO2_RATIO_MAX) {
                ratios += ratio
            }
        }

        val ratioMedian = ratios.medianOrNull() ?: return null
        val spo2 = (
            (SPO2_POLY_A * ratioMedian + SPO2_POLY_B) * ratioMedian + SPO2_POLY_C
            ).coerceIn(80.0, 100.0)
        return Spo2Estimate(
            spo2Percent = spo2,
            ratio = ratioMedian,
            correlation = correlation.coerceIn(0.0, 1.0)
        )
    }

    private fun removeLinearTrend(source: DoubleArray): DoubleArray {
        val n = source.size
        if (n <= 2) {
            return source.copyOf()
        }
        val xMean = (n - 1) / 2.0
        var sumX2 = 0.0
        var betaNumerator = 0.0
        for (index in source.indices) {
            val x = index - xMean
            sumX2 += x * x
            betaNumerator += x * source[index]
        }
        if (sumX2 <= 0.0) {
            return source.copyOf()
        }
        val beta = betaNumerator / sumX2
        return DoubleArray(n) { index ->
            val x = index - xMean
            source[index] - beta * x
        }
    }

    private fun movingAverage(source: DoubleArray, window: Int): DoubleArray {
        if (window <= 1 || source.isEmpty()) {
            return source.copyOf()
        }
        val output = DoubleArray(source.size)
        var running = 0.0
        for (index in source.indices) {
            running += source[index]
            if (index >= window) {
                running -= source[index - window]
            }
            val count = minOf(window, index + 1)
            output[index] = running / count.toDouble()
        }
        return output
    }

    private fun findPeaks(
        values: DoubleArray,
        minHeight: Double,
        minDistance: Int,
        maxCount: Int
    ): List<Int> {
        if (values.size < 3) {
            return emptyList()
        }

        val candidates = mutableListOf<Int>()
        for (index in 1 until values.lastIndex) {
            val isPeak = values[index] > values[index - 1] && values[index] >= values[index + 1]
            if (isPeak && values[index] >= minHeight) {
                candidates += index
            }
        }
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val selected = mutableListOf<Int>()
        for (candidate in candidates.sortedByDescending { values[it] }) {
            val tooClose = selected.any { abs(it - candidate) < minDistance }
            if (!tooClose) {
                selected += candidate
            }
            if (selected.size >= maxCount) {
                break
            }
        }
        return selected.sorted()
    }

    private fun indexOfMax(values: DoubleArray, start: Int, endInclusive: Int): Int {
        if (start < 0 || endInclusive >= values.size || start > endInclusive) {
            return -1
        }
        var maxIndex = start
        var maxValue = values[start]
        for (index in (start + 1)..endInclusive) {
            if (values[index] > maxValue) {
                maxValue = values[index]
                maxIndex = index
            }
        }
        return maxIndex
    }

    private fun linearBaseline(
        startValue: Double,
        endValue: Double,
        relativeIndex: Int,
        span: Int
    ): Double {
        if (span <= 0) {
            return startValue
        }
        return startValue + (endValue - startValue) * (relativeIndex.toDouble() / span.toDouble())
    }

    private fun pearsonCorrelation(left: DoubleArray, right: DoubleArray): Double {
        val size = minOf(left.size, right.size)
        if (size < 3) {
            return 0.0
        }
        val leftMean = left.take(size).average()
        val rightMean = right.take(size).average()

        var numerator = 0.0
        var leftEnergy = 0.0
        var rightEnergy = 0.0
        for (index in 0 until size) {
            val leftDelta = left[index] - leftMean
            val rightDelta = right[index] - rightMean
            numerator += leftDelta * rightDelta
            leftEnergy += leftDelta * leftDelta
            rightEnergy += rightDelta * rightDelta
        }
        val denominator = sqrt(leftEnergy * rightEnergy)
        if (denominator <= 1e-9) {
            return 0.0
        }
        return (numerator / denominator).coerceIn(-1.0, 1.0)
    }

    private fun computePttMs(): Double? {
        if (pttHistoryMs.isEmpty()) {
            return null
        }
        return pttHistoryMs.average()
    }

    private fun computePerfusionIndex(nowMicros: Long): Double? {
        val red = valuesWithin(redRawHistory, nowMicros, SPO2_WINDOW_US)
        if (red.size < 60) {
            return null
        }
        val ac = red.stdDev()
        val dc = red.average()
        if (dc <= 0.0) {
            return null
        }
        return ((ac / dc) * 100.0).coerceIn(0.1, 30.0)
    }

    private fun computeReflectionIndex(nowMicros: Long): Double {
        val redWindow = valuesWithin(redRawHistory, nowMicros, SPO2_WINDOW_US)
        if (redWindow.isEmpty()) {
            return 0.2
        }
        val mean = redWindow.averageOrZero()
        val std = redWindow.stdDev()
        val ratio = if (mean > 0.0) std / mean else 0.0
        return (ratio * 120.0).coerceIn(0.15, 0.85)
    }

    private fun computeBeatPulseConsistency(): Double? {
        if (rrIntervalsMs.isEmpty() || pttHistoryMs.isEmpty()) {
            return null
        }
        val rrCv = rrIntervalsMs.stdDev() / rrIntervalsMs.average().coerceAtLeast(1.0)
        val pttCv = pttHistoryMs.stdDev() / pttHistoryMs.average().coerceAtLeast(1.0)
        val mismatch = abs(rrCv - pttCv)
        return (1.0 - mismatch * 4.0).coerceIn(0.0, 1.0)
    }

    private fun computeArrhythmiaIndex(rrMeanMs: Double?, sdnnMs: Double?): Double? {
        if (rrMeanMs == null || sdnnMs == null || rrMeanMs <= 0.0) {
            return null
        }
        return ((sdnnMs / rrMeanMs) * 100.0).coerceIn(0.0, 100.0)
    }

    private fun estimateSignalQuality(
        stateFlags: Int,
        metricsUsable: Boolean,
        beatPulseConsistency: Double?
    ): Double {
        val nowMicros = currentTimeMicros()
        val ecgWindow = valuesWithin(ecgHistory, nowMicros, QUALITY_WINDOW_US)
        val ecgIntegratedWindow = valuesWithin(ecgIntegratedHistory, nowMicros, QUALITY_WINDOW_US)
        val irWindow = valuesWithin(irRawHistory, nowMicros, QUALITY_WINDOW_US)
        val ecgStd = ecgWindow.stdDev()
        val ecgEnergy = ecgIntegratedWindow.averageOrZero()
        val irStd = irWindow.stdDev()
        val irMean = irWindow.averageOrZero()

        // Hardware flags are still the primary trust source, but in practice some boards report
        // transient false negatives on contact bits. We use waveform evidence for graceful fallback.
        val inferredFingerContact =
            irWindow.size >= QUALITY_MIN_SAMPLE_COUNT &&
                irMean >= FINGER_CONTACT_MEAN_THRESHOLD &&
                irStd >= FINGER_CONTACT_STD_THRESHOLD
        val inferredEcgContact =
            ecgWindow.size >= QUALITY_MIN_SAMPLE_COUNT &&
                ecgStd >= ECG_CONTACT_STD_THRESHOLD &&
                ecgEnergy >= ECG_CONTACT_ENERGY_THRESHOLD

        var quality = 1.0
        if (stateFlags and FLAG_ECG_LEADS_OFF_ANY != 0) {
            quality *= if (inferredEcgContact) 0.72 else 0.55
        }
        if (stateFlags and FLAG_SENSOR_READY == 0) {
            quality *= 0.70
        }
        if (stateFlags and FLAG_FINGER_DETECTED == 0) {
            quality *= if (inferredFingerContact) 0.85 else 0.75
        }
        if (stateFlags and FLAG_PPG_FIFO_OVERFLOW != 0) {
            quality -= 0.08
        }
        if (stateFlags and FLAG_PPG_INT_TIMEOUT != 0) {
            quality -= 0.07
        }
        if (stateFlags and FLAG_ADC_SATURATED != 0) {
            quality -= 0.10
        }
        if (stateFlags and FLAG_BLE_BACKPRESSURE != 0) {
            quality -= 0.06
        }
        if (!metricsUsable) {
            quality -= 0.08
        }

        val ecgFactor = ((ecgStd / 20.0) * (ecgEnergy / 200.0).coerceIn(0.6, 1.2)).coerceIn(0.45, 1.0)
        val irFactor = (irStd / 95.0).coerceIn(0.50, 1.0)
        quality *= (ecgFactor + irFactor) * 0.5

        // Correlation between red/IR plethysmography is a robust indicator borrowed from
        // open-source MAX30102 processing flows.
        val ppgCorrelationFactor = if (latestPpgCorrelation <= 0.0) {
            0.72
        } else {
            (0.55 + latestPpgCorrelation * 0.45).coerceIn(0.55, 1.0)
        }
        quality *= ppgCorrelationFactor

        if (latestSpo2Ratio !in SPO2_RATIO_MIN..SPO2_RATIO_MAX && latestSpo2Ratio > 0.0) {
            quality *= 0.82
        }

        if (beatPulseConsistency != null) {
            quality *= (0.65 + beatPulseConsistency * 0.35)
        }

        return quality.coerceIn(0.12, 1.0)
    }

    private fun buildQualityTips(
        stateFlags: Int,
        quality: Double,
        metricsUsable: Boolean
    ): List<String> {
        val tips = mutableListOf<String>()
        if (stateFlags and FLAG_ECG_LEADS_OFF_ANY != 0) {
            tips += "ECG 电极接触不良，请稳定按压电极区域"
        }
        if (stateFlags and FLAG_FINGER_DETECTED == 0) {
            tips += "PPG 未检测到有效手指接触，请持续按压透光窗"
        }
        if (stateFlags and FLAG_ECG_LEADS_OFF_ANY != 0 && stateFlags and FLAG_FINGER_DETECTED == 0) {
            tips += "当前未形成稳定的人体接触回路，质量评估会保守偏低"
        }
        if (!metricsUsable) {
            tips += "检测到短时丢包，本窗口仅用于显示，已暂停高阶指标输出"
        }
        if (quality < 0.65) {
            tips += "当前信号质量偏低，建议保持静止并减少环境光干扰"
        }
        return if (tips.isEmpty()) {
            listOf("信号质量达标，高阶指标已开启")
        } else {
            tips
        }
    }

    private fun evaluateRiskEvents(
        heartRate: Double,
        rrMeanMs: Double?,
        arrhythmiaIndex: Double?,
        beatPulseConsistency: Double?,
        quality: Double
    ): List<RiskEvent> {
        val events = mutableListOf<RiskEvent>()

        if (heartRate >= 110.0) {
            events += RiskEvent(
                code = "tachycardia",
                title = "心动过速提示",
                confidence = (quality * 0.85).coerceIn(0.0, 1.0),
                level = RiskLevel.WARN
            )
        } else if (heartRate <= 52.0) {
            events += RiskEvent(
                code = "bradycardia",
                title = "心动过缓提示",
                confidence = (quality * 0.82).coerceIn(0.0, 1.0),
                level = RiskLevel.WARN
            )
        }

        if (arrhythmiaIndex != null && arrhythmiaIndex >= 11.0) {
            events += RiskEvent(
                code = "rhythm_irregular",
                title = "节律不齐提示",
                confidence = (quality * 0.78).coerceIn(0.0, 1.0),
                level = RiskLevel.WARN
            )
        }

        if (arrhythmiaIndex != null && arrhythmiaIndex >= 15.0 && rrMeanMs != null) {
            events += RiskEvent(
                code = "af_risk",
                title = "房颤风险提示（初筛）",
                confidence = (quality * 0.7).coerceIn(0.0, 1.0),
                level = RiskLevel.HIGH
            )
        }

        if (beatPulseConsistency != null && beatPulseConsistency < 0.45) {
            events += RiskEvent(
                code = "premature_beat_risk",
                title = "疑似早搏提示（需复测）",
                confidence = (quality * 0.65).coerceIn(0.0, 1.0),
                level = RiskLevel.INFO
            )
        }

        return events
    }

    private fun buildBloodPressureTrend(pwttMs: Double?): String {
        if (pwttMs == null) {
            return "趋势参考暂不可用（信号质量不足）"
        }
        return when {
            pwttMs < 120.0 -> "血压相关趋势：偏高风险，请结合连续测量观察"
            pwttMs > 220.0 -> "血压相关趋势：偏低风险，请结合体位与状态复测"
            else -> "血压相关趋势：当前波动处于个体参考区间"
        }
    }

    private fun qualityToGrade(quality: Double): SignalQualityGrade {
        return when {
            quality >= 0.85 -> SignalQualityGrade.EXCELLENT
            quality >= 0.7 -> SignalQualityGrade.GOOD
            quality >= 0.55 -> SignalQualityGrade.FAIR
            else -> SignalQualityGrade.POOR
        }
    }

    private fun qualityToTier(quality: Double, metricsUsable: Boolean): MetricOutputTier {
        if (!metricsUsable || quality < 0.65) {
            return MetricOutputTier.BASELINE
        }
        if (quality < 0.82) {
            return MetricOutputTier.ADVANCED
        }
        return MetricOutputTier.RESEARCH
    }

    private fun pruneOldData(nowMicros: Long) {
        removeOlderThan(ecgHistory, nowMicros - MAX_HISTORY_US)
        removeOlderThan(ecgIntegratedHistory, nowMicros - MAX_HISTORY_US)
        removeOlderThan(redRawHistory, nowMicros - MAX_HISTORY_US)
        removeOlderThan(irRawHistory, nowMicros - MAX_HISTORY_US)
        removeOlderThan(ppgAcHistory, nowMicros - MAX_HISTORY_US)
        while (rPeakTimestamps.size > R_PEAK_HISTORY_LIMIT) {
            rPeakTimestamps.removeFirst()
        }
    }

    private fun removeOlderThan(source: ArrayDeque<TimedValue>, thresholdMicros: Long) {
        while (source.isNotEmpty() && source.first().timestampMicros < thresholdMicros) {
            source.removeFirst()
        }
    }

    private fun currentTimeMicros(): Long {
        return listOfNotNull(
            ecgHistory.lastOrNull()?.timestampMicros,
            irRawHistory.lastOrNull()?.timestampMicros
        ).maxOrNull() ?: 0L
    }

    private fun timedValuesWithin(
        source: ArrayDeque<TimedValue>,
        nowMicros: Long,
        windowMicros: Long
    ): List<TimedValue> {
        val threshold = nowMicros - windowMicros
        return source
            .asSequence()
            .filter { it.timestampMicros >= threshold }
            .toList()
    }

    private fun valuesWithin(
        source: ArrayDeque<TimedValue>,
        nowMicros: Long,
        windowMicros: Long
    ): List<Double> {
        return timedValuesWithin(source, nowMicros, windowMicros).map { it.value }
    }

    private fun smooth(previous: Double, current: Double, alpha: Double): Double {
        return previous * (1.0 - alpha) + current * alpha
    }

    private class HighPassFilter(
        cutoffHz: Double,
        sampleRateHz: Double
    ) {
        private val alpha: Double
        private var prevInput = 0.0
        private var prevOutput = 0.0

        init {
            val dt = 1.0 / sampleRateHz
            val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
            alpha = rc / (rc + dt)
        }

        fun process(input: Double): Double {
            val output = alpha * (prevOutput + input - prevInput)
            prevInput = input
            prevOutput = output
            return output
        }
    }

    private class LowPassFilter(
        cutoffHz: Double,
        sampleRateHz: Double
    ) {
        private val alpha: Double
        private var output = 0.0
        private var initialized = false

        init {
            val dt = 1.0 / sampleRateHz
            val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
            alpha = dt / (rc + dt)
        }

        fun process(input: Double): Double {
            if (!initialized) {
                initialized = true
                output = input
                return output
            }
            output += alpha * (input - output)
            return output
        }
    }

    private class NotchFilter(
        centerFrequencyHz: Double,
        sampleRateHz: Double,
        qFactor: Double
    ) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double

        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        init {
            val omega = 2.0 * Math.PI * centerFrequencyHz / sampleRateHz
            val alpha = kotlin.math.sin(omega) / (2.0 * qFactor)
            val cosOmega = kotlin.math.cos(omega)

            val rawB0 = 1.0
            val rawB1 = -2.0 * cosOmega
            val rawB2 = 1.0
            val a0 = 1.0 + alpha
            val rawA1 = -2.0 * cosOmega
            val rawA2 = 1.0 - alpha

            b0 = rawB0 / a0
            b1 = rawB1 / a0
            b2 = rawB2 / a0
            a1 = rawA1 / a0
            a2 = rawA2 / a0
        }

        fun process(input: Double): Double {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }
    }

    private companion object {
        private const val ECG_SAMPLE_RATE_HZ = 250.0
        private const val PPG_SAMPLE_RATE_HZ = 400.0
        private const val ECG_NOTCH_HZ = 50.0

        private const val FLAG_ECG_LEADS_OFF_ANY = 1 shl 0
        private const val FLAG_PPG_FIFO_OVERFLOW = 1 shl 3
        private const val FLAG_PPG_INT_TIMEOUT = 1 shl 4
        private const val FLAG_ADC_SATURATED = 1 shl 5
        private const val FLAG_BLE_BACKPRESSURE = 1 shl 6
        private const val FLAG_SENSOR_READY = 1 shl 7
        private const val FLAG_FINGER_DETECTED = 1 shl 8

        private const val R_PEAK_REFRACTORY_US = 260_000L
        private const val PPG_FOOT_REFRACTORY_US = 220_000L
        private const val R_TO_FOOT_MIN_US = 80_000L
        private const val R_TO_FOOT_MAX_US = 400_000L
        private const val R_TO_PEAK_MIN_US = 120_000L
        private const val R_TO_PEAK_MAX_US = 500_000L
        private const val FOOT_TO_PEAK_MIN_US = 40_000L
        private const val FOOT_TO_PEAK_MAX_US = 350_000L
        private const val DELAY_JUMP_MAD_SCALE = 3.0
        private const val DELAY_JUMP_MIN_MS = 20.0
        private const val DELAY_JUMP_GUARD_MIN_HISTORY = 5

        private const val ECG_MWI_WINDOW_SAMPLES = 38
        private const val ECG_MWI_MIN_SAMPLES = 12
        private const val ECG_THRESHOLD_GAIN = 0.25
        private const val ECG_SEARCHBACK_THRESHOLD_SCALE = 0.5
        private const val ECG_LEVEL_FORGET_FACTOR = 0.875
        private const val ECG_MIN_LEVEL = 1e-6
        private const val ECG_DEFAULT_MISSED_BEAT_TIMEOUT_US = 1_600_000L
        private const val ECG_MIN_MISSED_BEAT_TIMEOUT_US = 700_000L
        private const val ECG_MAX_MISSED_BEAT_TIMEOUT_US = 2_400_000L
        private const val ECG_MISSED_BEAT_FACTOR = 1.66

        private const val PPG_FOOT_WINDOW_US = 2_000_000L
        private const val SPO2_WINDOW_US = 4_000_000L
        private const val QUALITY_WINDOW_US = 2_500_000L
        private const val MAX_HISTORY_US = 10_000_000L
        private const val SPO2_MIN_SAMPLE_COUNT = 320
        private const val SPO2_MOVING_AVERAGE_WINDOW = 4
        private const val SPO2_MIN_VALLEY_DISTANCE_SAMPLES = 110
        private const val SPO2_MIN_SEGMENT_SAMPLES = 70
        private const val SPO2_MAX_VALLEY_COUNT = 15
        private const val SPO2_MIN_CORRELATION = 0.45
        private const val SPO2_VALLEY_THRESHOLD_STD_GAIN = 0.35
        private const val SPO2_RATIO_MIN = 0.2
        private const val SPO2_RATIO_MAX = 1.84
        private const val SPO2_POLY_A = -45.060
        private const val SPO2_POLY_B = 30.354
        private const val SPO2_POLY_C = 94.845

        private const val PPG_MIN_SLOPE_THRESHOLD = 0.7
        private const val PPG_SLOPE_NOISE_GAIN = 0.15
        private const val PPG_MIN_PULSE_AMPLITUDE = 0.9
        private const val PPG_MAX_PULSE_AMPLITUDE = 5_000.0
        private const val PPG_PULSE_ADAPTIVE_GAIN = 0.35
        private const val PPG_MAX_PULSE_WIDTH_US = 350_000L

        private const val QUALITY_MIN_SAMPLE_COUNT = 40
        private const val ECG_CONTACT_STD_THRESHOLD = 10.0
        private const val ECG_CONTACT_ENERGY_THRESHOLD = 30.0
        private const val FINGER_CONTACT_MEAN_THRESHOLD = 8_000.0
        private const val FINGER_CONTACT_STD_THRESHOLD = 80.0

        private const val RR_HISTORY_LIMIT = 40
        private const val R_PEAK_HISTORY_LIMIT = 80
        private const val PTT_HISTORY_LIMIT = 60
        private const val MORPH_HISTORY_LIMIT = 40

        private const val PTT_REFERENCE_DISTANCE_METERS = 0.45
    }
}

private fun ArrayDeque<Double>.pushLimited(value: Double, limit: Int) {
    addLast(value)
    while (size > limit) {
        removeFirst()
    }
}

private fun ArrayDeque<Long>.pushLimited(value: Long, limit: Int) {
    addLast(value)
    while (size > limit) {
        removeFirst()
    }
}

private fun List<Double>.averageOrZero(): Double {
    return if (isEmpty()) 0.0 else average()
}

private fun List<Double>.stdDev(): Double {
    if (size < 2) {
        return 0.0
    }
    val mean = average()
    val variance = sumOf { (it - mean).pow(2) } / size
    return sqrt(variance)
}

private fun DoubleArray.stdDev(): Double {
    if (size < 2) {
        return 0.0
    }
    val mean = average()
    var sum = 0.0
    forEach { value ->
        sum += (value - mean).pow(2)
    }
    return sqrt(sum / size.toDouble())
}

private fun List<Double>.medianOrNull(): Double? {
    if (isEmpty()) {
        return null
    }
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2.0
    } else {
        sorted[mid]
    }
}

private fun List<Double>.rmssd(): Double {
    if (size < 3) {
        return 0.0
    }
    val diffs = zipWithNext().map { (left, right) -> right - left }
    val meanSquare = diffs.sumOf { it * it } / diffs.size.toDouble()
    return sqrt(meanSquare)
}

private fun List<Double>.pnn50(): Double {
    if (size < 3) {
        return 0.0
    }
    val diffs = zipWithNext().map { abs(it.second - it.first) }
    val over50 = diffs.count { it > 50.0 }
    return (over50.toDouble() / diffs.size.toDouble()) * 100.0
}
