package com.photosentinel.health.infrastructure.signal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class KotlinParityExportTest {
    @Test
    fun exportParityMetricsFixture() {
        val rrMs = doubleArrayOf(
            780.0, 810.0, 795.0, 830.0, 770.0, 760.0,
            820.0, 805.0, 790.0, 815.0, 785.0, 800.0
        )
        val pttMs = doubleArrayOf(
            218.0, 224.0, 221.0, 229.0, 216.0, 214.0,
            226.0, 223.0, 220.0, 227.0, 219.0, 222.0
        )
        val patMs = doubleArrayOf(
            392.0, 401.0, 398.0, 407.0, 390.0, 387.0,
            403.0, 400.0, 396.0, 405.0, 394.0, 399.0
        )

        val meanRr = rrMs.average()
        val meanHr = if (meanRr > 0.0) 60_000.0 / meanRr else Double.NaN
        val sdnn = rrMs.stdPopulation()
        val rrCv = if (meanRr > 0.0) sdnn / meanRr else Double.NaN
        val rmssd = rrMs.rmssd()
        val pnn50 = rrMs.pnn50()
        val (sd1, sd2) = rrMs.poincare()
        val sd1Sd2Ratio = if (sd2 > 1e-9) sd1 / sd2 else Double.NaN
        val pttMean = pttMs.average()
        val patMean = patMs.average()
        val riseTimeMean = patMs.zip(pttMs).map { (pat, ptt) -> pat - ptt }.average()
        val rrCvForConsistency = sdnn / meanRr.coerceAtLeast(1.0)
        val pttCv = pttMs.stdPopulation() / pttMean.coerceAtLeast(1.0)
        val beatPulseConsistency = (1.0 - abs(rrCvForConsistency - pttCv) * 4.0).coerceIn(0.0, 1.0)
        val arrhythmiaIndex = ((sdnn / meanRr) * 100.0).coerceIn(0.0, 100.0)

        val rows = listOf(
            "metric,value",
            "mean_rr_ms,$meanRr",
            "mean_hr_bpm,$meanHr",
            "sdnn_ms,$sdnn",
            "rr_cv,$rrCv",
            "rmssd_ms,$rmssd",
            "pnn50_percent,$pnn50",
            "sd1_ms,$sd1",
            "sd2_ms,$sd2",
            "sd1_sd2_ratio,$sd1Sd2Ratio",
            "ptt_mean_ms,$pttMean",
            "pat_mean_ms,$patMean",
            "rise_time_mean_ms,$riseTimeMean",
            "beat_pulse_consistency,$beatPulseConsistency",
            "arrhythmia_index,$arrhythmiaIndex"
        )

        val projectRoot = locateProjectRoot()
        val output = projectRoot
            .resolve("outputs")
            .resolve("metrics")
            .resolve("kotlin_parity_metrics.csv")
        Files.createDirectories(output.parent)
        Files.write(output, rows, StandardCharsets.UTF_8)

        assertTrue(Files.exists(output))
    }

    private fun locateProjectRoot(): Path {
        var cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(8) {
            val direct = cursor.resolve("project_root")
            if (Files.isDirectory(direct)) {
                return direct
            }
            val sibling = cursor.parent?.resolve("project_root")
            if (sibling != null && Files.isDirectory(sibling)) {
                return sibling
            }
            cursor = cursor.parent ?: return@repeat
        }
        throw IllegalStateException("Cannot locate project_root from user.dir=${System.getProperty("user.dir")}")
    }
}

private fun DoubleArray.stdPopulation(): Double {
    if (isEmpty()) {
        return Double.NaN
    }
    val mean = average()
    val variance = sumOf { (it - mean).pow(2) } / size.toDouble()
    return sqrt(variance)
}

private fun DoubleArray.rmssd(): Double {
    if (size < 2) {
        return Double.NaN
    }
    var sumSquare = 0.0
    for (index in 1 until size) {
        val diff = this[index] - this[index - 1]
        sumSquare += diff * diff
    }
    val meanSquare = sumSquare / (size - 1).toDouble()
    return sqrt(meanSquare)
}

private fun DoubleArray.pnn50(): Double {
    if (size < 2) {
        return Double.NaN
    }
    var over50 = 0
    for (index in 1 until size) {
        if (abs(this[index] - this[index - 1]) > 50.0) {
            over50 += 1
        }
    }
    return over50.toDouble() / (size - 1).toDouble() * 100.0
}

private fun DoubleArray.poincare(): Pair<Double, Double> {
    if (size < 3) {
        return Double.NaN to Double.NaN
    }
    val diff = DoubleArray(size - 1)
    val sum = DoubleArray(size - 1)
    for (index in 0 until size - 1) {
        diff[index] = (this[index + 1] - this[index]) / sqrt(2.0)
        sum[index] = (this[index + 1] + this[index]) / sqrt(2.0)
    }
    return diff.stdPopulation() to sum.stdPopulation()
}
