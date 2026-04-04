package com.photosentinel.health.infrastructure.signal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class BatchAlignedMetrics(
    val heartRateBpm: Int,
    val rrMeanMs: Double,
    val sdnnMs: Double,
    val rmssdMs: Double,
    val pnn50Percent: Double,
    val rrCv: Double,
    val arrhythmiaIndex: Double,
    val arrhythmiaBeatRatioPercent: Double,
    val afProbabilityPercent: Double,
    val sampleEntropy: Double,
    val sd1Ms: Double,
    val sd2Ms: Double,
    val sd1Sd2Ratio: Double,
    val ppgSqi: Double,
    val pttMs: Double,
    val patMs: Double,
    val riseTimeMs: Double,
    val pwttMs: Double,
    val pttValidBeatRatio: Double,
    val beatPulseConsistency: Double,
    val ecgRespRateBpm: Double?,
    val ppgRespRateBpm: Double?,
    val qrsWidthMs: Double?,
    val qtMs: Double?,
    val qtcMs: Double?,
    val pWaveQualityPercent: Double?
)

class BatchCardioAnalyzer {
    private data class DelayBeat(
        val valid: Boolean,
        val rToFootMs: Double,
        val rToPeakMs: Double,
        val footToPeakMs: Double
    )

    private data class DelaySummary(
        val rToFootMeanMs: Double,
        val rToPeakMeanMs: Double,
        val riseTimeMeanMs: Double,
        val rToFootSeriesMs: DoubleArray,
        val validBeatRatio: Double
    )

    private data class QtSummary(
        val qrsWidthMs: Double?,
        val qtMs: Double?,
        val qtcMs: Double?,
        val pWaveQualityPercent: Double?
    )

    private data class AfFeaturePack(
        val rrCv: Double,
        val sampleEntropy: Double,
        val sd1: Double,
        val sd2: Double,
        val sd1Sd2Ratio: Double
    )

    fun analyze(
        ecgRaw: DoubleArray,
        ppgIrRaw: DoubleArray,
        fsEcg: Double,
        fsPpg: Double
    ): BatchAlignedMetrics? {
        if (ecgRaw.size < (fsEcg * 20.0).toInt() || ppgIrRaw.size < (fsPpg * 20.0).toInt()) {
            return null
        }

        val ecgClean = preprocessEcg(ecgRaw, fsEcg)
        val ppgClean = preprocessPpg(ppgIrRaw, fsPpg)

        val rPeaks = detectRPeaksAdaptive(ecgClean, fsEcg)
        if (rPeaks.size < 4) {
            return null
        }
        val rrMs = rrIntervalsMs(rPeaks, fsEcg)
        val rrClean = rrMs.filter { it in 320.0..2000.0 }.toDoubleArray()
        if (rrClean.size < 3) {
            return null
        }

        val meanRr = rrClean.average()
        val meanHr = 60000.0 / meanRr
        val sdnn = rrClean.stdDevPopulation()
        val rmssd = rrClean.rmssd()
        val pnn50 = rrClean.pnn50()
        val arrhythmiaIndex = ((sdnn / meanRr) * 100.0).coerceIn(0.0, 100.0)
        val arrhythmiaBeatRatio = arrhythmiaBeatRatio(rrClean)

        val afFeatures = extractAfFeatures(rrClean)
        val afProb = estimateAfProbability(
            rrCv = afFeatures.rrCv,
            sampleEntropy = afFeatures.sampleEntropy,
            sd1 = afFeatures.sd1,
            sd2 = afFeatures.sd2,
            arrhythmiaBeatRatioPercent = arrhythmiaBeatRatio
        ) * 100.0

        val ppgPeaks = detectPpgPeaks(ppgClean, fsPpg)
        val ppgSqi = estimatePpgSqi(ppgClean, ppgPeaks, fsPpg)

        val delaySummary = computeConstrainedDelays(
            rPeaks = rPeaks,
            ppg = ppgClean,
            ppgPeaks = ppgPeaks,
            fsEcg = fsEcg,
            fsPpg = fsPpg
        ) ?: return null

        val pttSeries = delaySummary.rToFootSeriesMs
        val beatPulseConsistency = computeBeatPulseConsistency(rrClean, pttSeries)

        val ecgResp = estimateEcgDerivedResp(ecgClean, rPeaks, fsEcg)
        val ppgResp = estimatePpgDerivedResp(ppgClean, ppgPeaks, fsPpg)
        val qtSummary = estimateQtAndPwave(ecgClean, rPeaks, fsEcg)

        return BatchAlignedMetrics(
            heartRateBpm = meanHr.roundToInt().coerceIn(35, 190),
            rrMeanMs = meanRr,
            sdnnMs = sdnn,
            rmssdMs = rmssd,
            pnn50Percent = pnn50,
            rrCv = afFeatures.rrCv,
            arrhythmiaIndex = arrhythmiaIndex,
            arrhythmiaBeatRatioPercent = arrhythmiaBeatRatio,
            afProbabilityPercent = afProb.coerceIn(0.0, 100.0),
            sampleEntropy = afFeatures.sampleEntropy,
            sd1Ms = afFeatures.sd1,
            sd2Ms = afFeatures.sd2,
            sd1Sd2Ratio = afFeatures.sd1Sd2Ratio,
            ppgSqi = ppgSqi,
            pttMs = delaySummary.rToFootMeanMs,
            patMs = delaySummary.rToPeakMeanMs,
            riseTimeMs = delaySummary.riseTimeMeanMs,
            pwttMs = delaySummary.rToPeakMeanMs,
            pttValidBeatRatio = delaySummary.validBeatRatio,
            beatPulseConsistency = beatPulseConsistency,
            ecgRespRateBpm = ecgResp,
            ppgRespRateBpm = ppgResp,
            qrsWidthMs = qtSummary.qrsWidthMs,
            qtMs = qtSummary.qtMs,
            qtcMs = qtSummary.qtcMs,
            pWaveQualityPercent = qtSummary.pWaveQualityPercent
        )
    }

    private fun preprocessEcg(ecg: DoubleArray, fs: Double): DoubleArray {
        if (ecg.isEmpty()) {
            return ecg
        }
        var x = ecg.copyOf()
        val mean = x.average()
        for (index in x.indices) {
            x[index] -= mean
        }
        x = filtfiltHighPass(x, fs, cutoffHz = 0.5)
        if (fs >= 90.0) {
            x = filtfiltNotch(x, fs, centerHz = 50.0, q = 25.0)
        }
        x = filtfiltHighPass(x, fs, cutoffHz = 0.5)
        x = filtfiltLowPass(x, fs, cutoffHz = min(40.0, fs / 2.2))
        return x
    }

    private fun preprocessPpg(ppg: DoubleArray, fs: Double): DoubleArray {
        if (ppg.isEmpty()) {
            return ppg
        }
        var x = suppressOutliers(ppg, z = 5.0)
        x = detrendLinear(x)
        x = filtfiltHighPass(x, fs, cutoffHz = 0.5)
        x = filtfiltLowPass(x, fs, cutoffHz = 8.0)
        val mean = x.average()
        val std = x.stdDevPopulation()
        if (std <= 1e-9) {
            for (index in x.indices) {
                x[index] -= mean
            }
            return x
        }
        for (index in x.indices) {
            x[index] = (x[index] - mean) / std
        }
        return x
    }

    private fun detectRPeaksAdaptive(
        ecg: DoubleArray,
        fs: Double,
        thresholdScale: Double = 0.8,
        minDistanceSec: Double = 0.25
    ): IntArray {
        if (ecg.size < 4) {
            return IntArray(0)
        }
        val deriv = DoubleArray(ecg.size)
        deriv[0] = 0.0
        for (index in 1 until ecg.size) {
            deriv[index] = ecg[index] - ecg[index - 1]
        }
        val sq = DoubleArray(ecg.size) { deriv[it] * deriv[it] }
        val mwiWindow = max(1, (0.15 * fs).toInt())
        val mwi = movingAverage(sq, mwiWindow)
        val threshold = mwi.median() + thresholdScale * mwi.stdDevPopulation()
        val minDistance = max(1, (minDistanceSec * fs).toInt())
        val coarse = findLocalMaxima(
            values = mwi,
            minDistance = minDistance,
            minHeight = threshold,
            minProminence = 0.0
        )
        val radius = max(1, (0.08 * fs).toInt())
        val peaks = mutableListOf<Int>()
        coarse.forEach { candidate ->
            val left = max(0, candidate - radius)
            val right = min(ecg.lastIndex, candidate + radius)
            var localIndex = left
            var localMax = ecg[left]
            for (index in left..right) {
                if (ecg[index] > localMax) {
                    localMax = ecg[index]
                    localIndex = index
                }
            }
            peaks += localIndex
        }
        return peaks.distinct().sorted().toIntArray()
    }

    private fun rrIntervalsMs(rPeaks: IntArray, fs: Double): DoubleArray {
        if (rPeaks.size < 2) {
            return DoubleArray(0)
        }
        val rr = DoubleArray(rPeaks.size - 1)
        for (index in 1 until rPeaks.size) {
            rr[index - 1] = (rPeaks[index] - rPeaks[index - 1]) * 1000.0 / fs
        }
        return rr
    }

    private fun detectPpgPeaks(ppg: DoubleArray, fs: Double): IntArray {
        val minDistance = max(1, (0.35 * fs).toInt())
        val prominence = 0.2 * ppg.stdDevPopulation()
        return findLocalMaxima(
            values = ppg,
            minDistance = minDistance,
            minHeight = Double.NEGATIVE_INFINITY,
            minProminence = prominence
        )
    }

    private fun detectPpgFeet(ppg: DoubleArray, fs: Double): IntArray {
        val inv = DoubleArray(ppg.size) { -ppg[it] }
        val minDistance = max(1, (0.35 * fs).toInt())
        val prominence = 0.1 * inv.stdDevPopulation()
        return findLocalMaxima(
            values = inv,
            minDistance = minDistance,
            minHeight = Double.NEGATIVE_INFINITY,
            minProminence = prominence
        )
    }

    private fun estimatePpgSqi(ppg: DoubleArray, peaks: IntArray, fs: Double): Double {
        if (peaks.size < 3) {
            return 0.0
        }
        val rr = DoubleArray(peaks.size - 1) { index -> (peaks[index + 1] - peaks[index]) / fs }
        val rrValid = rr.filter { it > 0.3 && it < 2.0 }.toDoubleArray()
        if (rrValid.size < 2) {
            return 0.1
        }
        val periodicity = 1.0 - min(rrValid.stdDevPopulation() / rrValid.average().coerceAtLeast(1e-6), 1.0)
        val bandEnergy = ppg.map { it * it }.average()
        val dom = dominantSpectrumRatio(ppg)
        val score = 0.5 * periodicity + 0.25 * (dom * 10.0).coerceIn(0.0, 1.0) + 0.25 * bandEnergy.coerceIn(0.0, 1.0)
        return score.coerceIn(0.0, 1.0)
    }

    private fun computeConstrainedDelays(
        rPeaks: IntArray,
        ppg: DoubleArray,
        ppgPeaks: IntArray,
        fsEcg: Double,
        fsPpg: Double
    ): DelaySummary? {
        if (rPeaks.isEmpty() || ppgPeaks.isEmpty()) {
            return null
        }
        val rTimes = DoubleArray(rPeaks.size) { rPeaks[it] / fsEcg }
        val peakTimes = DoubleArray(ppgPeaks.size) { ppgPeaks[it] / fsPpg }
        val footIndices = deriveFeetBeforePeaks(
            ppg = ppg,
            ppgPeaks = ppgPeaks,
            fsPpg = fsPpg,
            minPrePeakMs = 40.0,
            maxPrePeakMs = 350.0
        )
        val footTimes = DoubleArray(footIndices.size) { footIndices[it] / fsPpg }

        val beats = mutableListOf<DelayBeat>()
        for (rTime in rTimes) {
            val peakPos = peakTimes.indexOfFirst {
                it > rTime + 0.12 && it < rTime + 0.5
            }
            if (peakPos < 0) {
                beats += DelayBeat(valid = false, rToFootMs = Double.NaN, rToPeakMs = Double.NaN, footToPeakMs = Double.NaN)
                continue
            }
            val peakTime = peakTimes[peakPos]
            val footTime = if (peakPos < footTimes.size) footTimes[peakPos] else Double.NaN
            val rToPeakMs = (peakTime - rTime) * 1000.0
            val rToFootMs = if (footTime.isFinite()) (footTime - rTime) * 1000.0 else Double.NaN
            val footToPeakMs = if (footTime.isFinite()) (peakTime - footTime) * 1000.0 else Double.NaN

            val valid = footTime.isFinite() &&
                (rTime < footTime && footTime < peakTime) &&
                (rToFootMs in 80.0..400.0) &&
                (rToPeakMs in 120.0..500.0) &&
                (footToPeakMs > 0.0)
            beats += DelayBeat(valid = valid, rToFootMs = rToFootMs, rToPeakMs = rToPeakMs, footToPeakMs = footToPeakMs)
        }

        val filtered = applyDelayJumpGuard(beats)
        val validBeats = filtered.filter { it.valid }
        if (validBeats.isEmpty()) {
            return null
        }
        val validR2f = validBeats.map { it.rToFootMs }.toDoubleArray()
        return DelaySummary(
            rToFootMeanMs = validR2f.average(),
            rToPeakMeanMs = validBeats.map { it.rToPeakMs }.average(),
            riseTimeMeanMs = validBeats.map { it.footToPeakMs }.average(),
            rToFootSeriesMs = validR2f,
            validBeatRatio = validBeats.size.toDouble() / beats.size.toDouble()
        )
    }

    private fun computeBeatPulseConsistency(rrMs: DoubleArray, pttMs: DoubleArray): Double {
        if (rrMs.size < 2 || pttMs.size < 2) {
            return 0.5
        }
        val rrCv = rrMs.stdDevPopulation() / rrMs.average().coerceAtLeast(1.0)
        val pttCv = pttMs.stdDevPopulation() / pttMs.average().coerceAtLeast(1.0)
        val mismatch = abs(rrCv - pttCv)
        return (1.0 - mismatch * 4.0).coerceIn(0.0, 1.0)
    }

    private fun estimateEcgDerivedResp(ecg: DoubleArray, rPeaks: IntArray, fsEcg: Double): Double? {
        if (rPeaks.size < 4) {
            return null
        }
        val amplitudes = DoubleArray(rPeaks.size) { ecg[rPeaks[it]] }
        val times = DoubleArray(rPeaks.size) { rPeaks[it] / fsEcg }
        val interpolated = linearInterpolate(times, amplitudes, fs = 4.0)
        if (interpolated.isEmpty()) {
            return null
        }
        return estimateRespRateFromSeries(interpolated, fs = 4.0)
    }

    private fun estimatePpgDerivedResp(ppg: DoubleArray, ppgPeaks: IntArray, fsPpg: Double): Double? {
        if (ppgPeaks.size < 4) {
            return null
        }
        val amplitudes = DoubleArray(ppgPeaks.size) { ppg[ppgPeaks[it]] }
        val times = DoubleArray(ppgPeaks.size) { ppgPeaks[it] / fsPpg }
        val interpolated = linearInterpolate(times, amplitudes, fs = 4.0)
        if (interpolated.isEmpty()) {
            return null
        }
        return estimateRespRateFromSeries(interpolated, fs = 4.0)
    }

    private fun estimateRespRateFromSeries(series: DoubleArray, fs: Double): Double? {
        if (series.size < (fs * 10.0).toInt()) {
            return null
        }
        val x = series.copyOf()
        val mean = x.average()
        for (index in x.indices) {
            x[index] -= mean
        }
        val dftN = min(nextPow2(x.size), 2048)
        if (dftN < 64) {
            return null
        }
        val windowed = DoubleArray(dftN)
        for (index in 0 until dftN) {
            val src = x[min(index, x.lastIndex)]
            val hann = 0.5 - 0.5 * cos(2.0 * PI * index / (dftN - 1).coerceAtLeast(1))
            windowed[index] = src * hann
        }
        var bestFreq = Double.NaN
        var bestPower = 0.0
        for (k in 1 until dftN / 2) {
            val freq = k * fs / dftN.toDouble()
            if (freq < 0.1 || freq > 0.6) {
                continue
            }
            var real = 0.0
            var imag = 0.0
            for (n in 0 until dftN) {
                val angle = -2.0 * PI * k * n / dftN.toDouble()
                real += windowed[n] * cos(angle)
                imag += windowed[n] * sin(angle)
            }
            val power = real * real + imag * imag
            if (power > bestPower) {
                bestPower = power
                bestFreq = freq
            }
        }
        if (!bestFreq.isFinite()) {
            return null
        }
        return bestFreq * 60.0
    }

    private fun estimateQtAndPwave(
        ecg: DoubleArray,
        rPeaks: IntArray,
        fs: Double
    ): QtSummary {
        if (rPeaks.size < 4) {
            return QtSummary(null, null, null, null)
        }
        val derivative = DoubleArray(ecg.size)
        for (index in 1 until ecg.size) {
            derivative[index] = ecg[index] - ecg[index - 1]
        }

        val qrsList = mutableListOf<Double>()
        val qtList = mutableListOf<Double>()
        var pDetected = 0
        var pTotal = 0

        for (r in rPeaks) {
            val qStart = max(1, r - (0.12 * fs).toInt())
            val sEnd = min(ecg.lastIndex - 1, r + (0.16 * fs).toInt())
            if (qStart >= sEnd) {
                continue
            }
            val qOn = indexOfMin(ecg, qStart, r)
            val sOff = indexOfMin(ecg, r, sEnd)
            if (qOn < 0 || sOff <= qOn) {
                continue
            }
            val qrsMs = (sOff - qOn) * 1000.0 / fs
            if (qrsMs in 40.0..220.0) {
                qrsList += qrsMs
            }

            val pStart = max(0, r - (0.22 * fs).toInt())
            val pEnd = max(pStart + 1, r - (0.08 * fs).toInt())
            if (pEnd > pStart) {
                pTotal += 1
                val pAmp = ecg.sliceArray(pStart until pEnd).maxOrNull() ?: 0.0
                val rAmp = abs(ecg[r]).coerceAtLeast(1e-6)
                if (abs(pAmp) / rAmp >= 0.05) {
                    pDetected += 1
                }
            }

            val tStart = min(ecg.lastIndex - 1, r + (0.12 * fs).toInt())
            val tEndWin = min(ecg.lastIndex - 1, r + (0.50 * fs).toInt())
            if (tEndWin <= tStart) {
                continue
            }
            val baselineLeft = max(0, qOn - (0.04 * fs).toInt())
            val baseline = ecg.sliceArray(baselineLeft..qOn).median()
            val tPeak = indexOfMaxAbs(ecg, tStart, tEndWin)
            if (tPeak < 0) {
                continue
            }
            val peakAmp = abs(ecg[tPeak] - baseline).coerceAtLeast(1e-6)
            var tEnd = -1
            val endThreshold = peakAmp * 0.12
            for (index in tPeak until tEndWin) {
                if (abs(ecg[index] - baseline) <= endThreshold && abs(derivative[index]) < peakAmp * 0.08) {
                    tEnd = index
                    break
                }
            }
            if (tEnd > qOn) {
                val qtMs = (tEnd - qOn) * 1000.0 / fs
                if (qtMs in 200.0..650.0) {
                    qtList += qtMs
                }
            }
        }

        if (qrsList.isEmpty() && qtList.isEmpty()) {
            return QtSummary(null, null, null, if (pTotal > 0) pDetected * 100.0 / pTotal else null)
        }
        val meanQrs = qrsList.average().takeIf { it.isFinite() }
        val meanQt = qtList.average().takeIf { it.isFinite() }
        val rrSec = rrIntervalsMs(rPeaks, fs).average() / 1000.0
        val qtc = if (meanQt != null && rrSec > 0.0) meanQt / sqrt(rrSec) else null
        val pWaveQuality = if (pTotal > 0) pDetected * 100.0 / pTotal else null
        return QtSummary(meanQrs, meanQt, qtc, pWaveQuality)
    }

    private fun extractAfFeatures(rrMs: DoubleArray): AfFeaturePack {
        val rrMean = rrMs.average().coerceAtLeast(1e-6)
        val rrStd = rrMs.stdDevPopulation()
        val rrCv = rrStd / rrMean
        val sampEn = sampleEntropy(rrMs, m = 2, rScale = 0.2)
        val (sd1, sd2) = poincare(rrMs)
        val ratio = if (sd2 > 1e-9) sd1 / sd2 else Double.NaN
        return AfFeaturePack(
            rrCv = rrCv,
            sampleEntropy = sampEn,
            sd1 = sd1,
            sd2 = sd2,
            sd1Sd2Ratio = ratio
        )
    }

    private fun estimateAfProbability(
        rrCv: Double,
        sampleEntropy: Double,
        sd1: Double,
        sd2: Double,
        arrhythmiaBeatRatioPercent: Double
    ): Double {
        val samp = if (sampleEntropy.isFinite()) sampleEntropy else 0.0
        val ratio = if (sd2 > 1e-9) sd1 / sd2 else 0.0
        val rrScore = ((rrCv - 0.08) / 0.25).coerceIn(0.0, 1.0)
        val entScore = ((samp - 0.8) / 1.2).coerceIn(0.0, 1.0)
        val ratioScore = ((abs(ratio - 0.6) - 0.1) / 0.5).coerceIn(0.0, 1.0)
        val arrScore = ((arrhythmiaBeatRatioPercent - 8.0) / 30.0).coerceIn(0.0, 1.0)
        val probability = rrScore * 0.35 + entScore * 0.35 + ratioScore * 0.15 + arrScore * 0.15
        return probability.coerceIn(0.0, 1.0)
    }

    private fun sampleEntropy(x: DoubleArray, m: Int, rScale: Double): Double {
        if (x.size < 20) {
            return Double.NaN
        }
        val r = rScale * x.stdDevPopulation()
        if (r <= 1e-9) {
            return 0.0
        }

        fun phi(order: Int): Int {
            val vectors = mutableListOf<DoubleArray>()
            for (index in 0..(x.size - order)) {
                vectors += x.sliceArray(index until index + order)
            }
            var count = 0
            for (index in vectors.indices) {
                for (next in (index + 1) until vectors.size) {
                    var maxAbs = 0.0
                    for (k in 0 until order) {
                        maxAbs = max(maxAbs, abs(vectors[index][k] - vectors[next][k]))
                    }
                    if (maxAbs <= r) {
                        count += 1
                    }
                }
            }
            return count
        }

        val b = phi(m)
        val a = phi(m + 1)
        if (b == 0 || a == 0) {
            return Double.NaN
        }
        return -ln(a.toDouble() / b.toDouble())
    }

    private fun poincare(rrMs: DoubleArray): Pair<Double, Double> {
        if (rrMs.size < 3) {
            return Pair(Double.NaN, Double.NaN)
        }
        val diff = DoubleArray(rrMs.size - 1)
        val sum = DoubleArray(rrMs.size - 1)
        for (index in 0 until rrMs.size - 1) {
            diff[index] = (rrMs[index + 1] - rrMs[index]) / sqrt(2.0)
            sum[index] = (rrMs[index + 1] + rrMs[index]) / sqrt(2.0)
        }
        return Pair(diff.stdDevPopulation(), sum.stdDevPopulation())
    }

    private fun arrhythmiaBeatRatio(rrMs: DoubleArray): Double {
        if (rrMs.size < 3) {
            return 0.0
        }
        val median = rrMs.median()
        val abnormal = rrMs.count { abs(it - median) > (0.2 * median) }
        return abnormal * 100.0 / rrMs.size
    }

    private fun deriveFeetBeforePeaks(
        ppg: DoubleArray,
        ppgPeaks: IntArray,
        fsPpg: Double,
        minPrePeakMs: Double,
        maxPrePeakMs: Double
    ): IntArray {
        if (ppg.isEmpty() || ppgPeaks.isEmpty()) {
            return IntArray(0)
        }
        val minOffset = max(1, (minPrePeakMs / 1000.0 * fsPpg).toInt())
        val maxOffset = max(minOffset + 1, (maxPrePeakMs / 1000.0 * fsPpg).toInt())
        val feet = mutableListOf<Int>()
        ppgPeaks.forEach { peak ->
            val left = max(0, peak - maxOffset)
            val right = max(left + 1, peak - minOffset)
            if (right <= left) {
                return@forEach
            }
            var minIndex = left
            var minValue = ppg[left]
            for (index in left until right) {
                if (ppg[index] < minValue) {
                    minValue = ppg[index]
                    minIndex = index
                }
            }
            feet += minIndex
        }
        return feet.toIntArray()
    }

    private fun applyDelayJumpGuard(beats: List<DelayBeat>): List<DelayBeat> {
        if (beats.isEmpty()) {
            return beats
        }
        val mutable = beats.map { it.copy() }.toMutableList()
        listOf("r2foot", "r2peak").forEach { key ->
            val values = mutable.filter { it.valid }.map {
                if (key == "r2foot") it.rToFootMs else it.rToPeakMs
            }.filter { it.isFinite() }
            if (values.size < 5) {
                return@forEach
            }
            val med = values.toDoubleArray().median()
            val mad = values.map { abs(it - med) }.toDoubleArray().median()
            val threshold = max(3.0 * mad, 20.0)
            for (index in mutable.indices) {
                val beat = mutable[index]
                val value = if (key == "r2foot") beat.rToFootMs else beat.rToPeakMs
                if (!value.isFinite() || !beat.valid) {
                    continue
                }
                if (abs(value - med) > threshold) {
                    mutable[index] = beat.copy(valid = false)
                }
            }
        }
        return mutable
    }

    private fun dominantSpectrumRatio(values: DoubleArray): Double {
        if (values.size < 16) {
            return 0.0
        }
        val n = min(nextPow2(values.size), 512)
        val x = DoubleArray(n) { index -> values[min(index, values.lastIndex)] }
        val mean = x.average()
        for (index in x.indices) {
            x[index] -= mean
        }
        val magnitudes = DoubleArray(n / 2 + 1)
        for (k in magnitudes.indices) {
            var real = 0.0
            var imag = 0.0
            for (m in 0 until n) {
                val angle = -2.0 * PI * k * m / n.toDouble()
                real += x[m] * cos(angle)
                imag += x[m] * sin(angle)
            }
            magnitudes[k] = sqrt(real * real + imag * imag)
        }
        if (magnitudes.size <= 1) {
            return 0.0
        }
        val tail = magnitudes.sliceArray(1 until magnitudes.size)
        val maxValue = tail.maxOrNull() ?: 0.0
        val sum = tail.sum().coerceAtLeast(1e-9)
        return maxValue / sum
    }

    private fun linearInterpolate(times: DoubleArray, values: DoubleArray, fs: Double): DoubleArray {
        if (times.size < 2 || values.size != times.size || fs <= 0.0) {
            return DoubleArray(0)
        }
        val start = times.first()
        val end = times.last()
        if (end <= start) {
            return DoubleArray(0)
        }
        val count = ((end - start) * fs).toInt().coerceAtLeast(1)
        val out = DoubleArray(count)
        var cursor = 0
        for (index in 0 until count) {
            val t = start + index / fs
            while (cursor < times.lastIndex - 1 && times[cursor + 1] < t) {
                cursor += 1
            }
            val t0 = times[cursor]
            val t1 = times[min(cursor + 1, times.lastIndex)]
            val y0 = values[cursor]
            val y1 = values[min(cursor + 1, values.lastIndex)]
            val w = if (t1 > t0) ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0) else 0.0
            out[index] = y0 + (y1 - y0) * w
        }
        return out
    }

    private fun movingAverage(values: DoubleArray, window: Int): DoubleArray {
        if (window <= 1 || values.isEmpty()) {
            return values.copyOf()
        }
        val out = DoubleArray(values.size)
        var running = 0.0
        for (index in values.indices) {
            running += values[index]
            if (index >= window) {
                running -= values[index - window]
            }
            val count = min(window, index + 1)
            out[index] = running / count.toDouble()
        }
        return out
    }

    private fun suppressOutliers(values: DoubleArray, z: Double): DoubleArray {
        val x = values.copyOf()
        val mean = x.average()
        val std = x.stdDevPopulation().coerceAtLeast(1e-8)
        val median = x.median()
        for (index in x.indices) {
            if (abs((x[index] - mean) / std) > z) {
                x[index] = median
            }
        }
        return x
    }

    private fun detrendLinear(values: DoubleArray): DoubleArray {
        if (values.size < 4) {
            return values.copyOf()
        }
        val n = values.size
        val xMean = (n - 1) / 2.0
        var sumX2 = 0.0
        var betaNum = 0.0
        for (index in values.indices) {
            val x = index - xMean
            sumX2 += x * x
            betaNum += x * values[index]
        }
        if (sumX2 <= 0.0) {
            return values.copyOf()
        }
        val beta = betaNum / sumX2
        val out = DoubleArray(n)
        for (index in values.indices) {
            val x = index - xMean
            out[index] = values[index] - beta * x
        }
        return out
    }

    private fun filtfiltHighPass(values: DoubleArray, fs: Double, cutoffHz: Double): DoubleArray {
        val forward = applyHighPass(values, fs, cutoffHz)
        val reversed = applyHighPass(forward.reversedArray(), fs, cutoffHz)
        return reversed.reversedArray()
    }

    private fun filtfiltLowPass(values: DoubleArray, fs: Double, cutoffHz: Double): DoubleArray {
        val forward = applyLowPass(values, fs, cutoffHz)
        val reversed = applyLowPass(forward.reversedArray(), fs, cutoffHz)
        return reversed.reversedArray()
    }

    private fun filtfiltNotch(values: DoubleArray, fs: Double, centerHz: Double, q: Double): DoubleArray {
        if (values.size < 8 || fs <= 2.0 || centerHz >= fs * 0.45) {
            return values
        }
        val forward = applyNotch(values, fs, centerHz, q)
        val reversed = applyNotch(forward.reversedArray(), fs, centerHz, q)
        return reversed.reversedArray()
    }

    private fun applyHighPass(values: DoubleArray, fs: Double, cutoffHz: Double): DoubleArray {
        if (values.isEmpty() || fs <= 2.0) {
            return values
        }
        val dt = 1.0 / fs
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val alpha = rc / (rc + dt)
        val out = DoubleArray(values.size)
        var prevInput = values.first()
        var prevOutput = 0.0
        out[0] = 0.0
        for (index in 1 until values.size) {
            val output = alpha * (prevOutput + values[index] - prevInput)
            out[index] = output
            prevInput = values[index]
            prevOutput = output
        }
        return out
    }

    private fun applyLowPass(values: DoubleArray, fs: Double, cutoffHz: Double): DoubleArray {
        if (values.isEmpty() || fs <= 2.0) {
            return values
        }
        val dt = 1.0 / fs
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val alpha = dt / (rc + dt)
        val out = DoubleArray(values.size)
        out[0] = values[0]
        for (index in 1 until values.size) {
            out[index] = out[index - 1] + alpha * (values[index] - out[index - 1])
        }
        return out
    }

    private fun applyNotch(values: DoubleArray, fs: Double, centerHz: Double, q: Double): DoubleArray {
        if (values.isEmpty()) {
            return values
        }
        val omega = 2.0 * PI * centerHz / fs
        val alpha = sin(omega) / (2.0 * q)
        val cosOmega = cos(omega)
        val rawB0 = 1.0
        val rawB1 = -2.0 * cosOmega
        val rawB2 = 1.0
        val a0 = 1.0 + alpha
        val rawA1 = -2.0 * cosOmega
        val rawA2 = 1.0 - alpha

        val b0 = rawB0 / a0
        val b1 = rawB1 / a0
        val b2 = rawB2 / a0
        val a1 = rawA1 / a0
        val a2 = rawA2 / a0

        val out = DoubleArray(values.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (index in values.indices) {
            val input = values[index]
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            out[index] = output
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
        }
        return out
    }

    private fun findLocalMaxima(
        values: DoubleArray,
        minDistance: Int,
        minHeight: Double,
        minProminence: Double
    ): IntArray {
        if (values.size < 3) {
            return IntArray(0)
        }
        val candidates = mutableListOf<Int>()
        for (index in 1 until values.lastIndex) {
            val isPeak = values[index] > values[index - 1] && values[index] >= values[index + 1]
            if (!isPeak) {
                continue
            }
            if (values[index] < minHeight) {
                continue
            }
            val left = max(0, index - minDistance)
            val right = min(values.lastIndex, index + minDistance)
            val leftMin = values.sliceArray(left..index).minOrNull() ?: values[index]
            val rightMin = values.sliceArray(index..right).minOrNull() ?: values[index]
            val prominence = values[index] - max(leftMin, rightMin)
            if (prominence >= minProminence) {
                candidates += index
            }
        }
        if (candidates.isEmpty()) {
            return IntArray(0)
        }
        val selected = mutableListOf<Int>()
        val sortedByHeight = candidates.sortedByDescending { values[it] }
        sortedByHeight.forEach { candidate ->
            val tooClose = selected.any { abs(it - candidate) < minDistance }
            if (!tooClose) {
                selected += candidate
            }
        }
        return selected.sorted().toIntArray()
    }

    private fun indexOfMin(values: DoubleArray, start: Int, end: Int): Int {
        if (start < 0 || end >= values.size || start > end) {
            return -1
        }
        var indexMin = start
        var valueMin = values[start]
        for (index in start..end) {
            if (values[index] < valueMin) {
                valueMin = values[index]
                indexMin = index
            }
        }
        return indexMin
    }

    private fun indexOfMaxAbs(values: DoubleArray, start: Int, end: Int): Int {
        if (start < 0 || end >= values.size || start > end) {
            return -1
        }
        var best = start
        var bestValue = abs(values[start])
        for (index in start..end) {
            val current = abs(values[index])
            if (current > bestValue) {
                bestValue = current
                best = index
            }
        }
        return best
    }

    private fun nextPow2(input: Int): Int {
        var value = 1
        while (value < input) {
            value = value shl 1
        }
        return value
    }
}

private fun DoubleArray.stdDevPopulation(): Double {
    if (isEmpty()) {
        return 0.0
    }
    val mean = average()
    var sum = 0.0
    forEach { value ->
        sum += (value - mean).pow(2)
    }
    return sqrt(sum / size.toDouble())
}

private fun DoubleArray.rmssd(): Double {
    if (size < 2) {
        return 0.0
    }
    var sumSquare = 0.0
    for (index in 1 until size) {
        val diff = this[index] - this[index - 1]
        sumSquare += diff * diff
    }
    return sqrt(sumSquare / (size - 1).toDouble())
}

private fun DoubleArray.pnn50(): Double {
    if (size < 2) {
        return 0.0
    }
    var over = 0
    for (index in 1 until size) {
        if (abs(this[index] - this[index - 1]) > 50.0) {
            over += 1
        }
    }
    return over * 100.0 / (size - 1).toDouble()
}

private fun DoubleArray.median(): Double {
    if (isEmpty()) {
        return Double.NaN
    }
    val sorted = sortedArray()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2.0
    } else {
        sorted[mid]
    }
}
