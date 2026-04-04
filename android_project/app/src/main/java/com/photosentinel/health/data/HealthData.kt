package com.photosentinel.health.data

data class DetectionResult(
    val pwvValue: Float,
    val heartRate: Int,
    val vascularAge: Int,
    val elasticityLevel: ElasticityLevel,
    val elasticityScore: Int,
    val pttMs: Float,
    val spo2Percent: Float,
    val signalQualityPercent: Int
)

enum class ElasticityLevel(val label: String) {
    EXCELLENT("优秀"),
    GOOD("良好"),
    FAIR("一般"),
    POOR("较差")
}

data class TrendPoint(
    val date: String,
    val pwvValue: Float,
    val elasticityScore: Int
)
