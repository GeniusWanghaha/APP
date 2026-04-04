package com.photosentinel.health.infrastructure.safety

import com.photosentinel.health.domain.model.SafetyLevel
import com.photosentinel.health.domain.model.SafetyResult
import com.photosentinel.health.domain.model.StandardizedDetection

class SafetyEvaluator {
    fun evaluate(detection: StandardizedDetection): SafetyResult {
        val metrics = detection.metrics
        val warnings = mutableListOf<String>()
        var level = SafetyLevel.OK

        if (metrics.signalQuality < 0.5) {
            return SafetyResult(
                level = SafetyLevel.BLOCKED,
                warnings = emptyList(),
                blockReason = "信号质量过低（${(metrics.signalQuality * 100).toInt()}%），请重新采集"
            )
        }

        when {
            metrics.spo2Percent < 90 -> {
                warnings += "血氧饱和度偏低（${metrics.spo2Percent}%），建议尽快就医咨询"
                level = SafetyLevel.DANGER
            }

            metrics.spo2Percent < 94 -> {
                warnings += "血氧饱和度低于推荐区间（${metrics.spo2Percent}%）"
                if (level != SafetyLevel.DANGER) {
                    level = SafetyLevel.WARN
                }
            }
        }

        when {
            metrics.heartRate > 150 || metrics.heartRate < 40 -> {
                warnings += "心率处于危险区间（${metrics.heartRate} 次/分）"
                level = SafetyLevel.DANGER
            }

            metrics.heartRate > 100 || metrics.heartRate < 55 -> {
                warnings += "心率偏离静息推荐范围（${metrics.heartRate} 次/分）"
                if (level != SafetyLevel.DANGER) {
                    level = SafetyLevel.WARN
                }
            }
        }

        when {
            metrics.pwvMs > 13.0 -> {
                warnings += "PWV 显著升高（${metrics.pwvMs} m/s）"
                level = SafetyLevel.DANGER
            }

            metrics.pwvMs > 10.0 -> {
                warnings += "PWV 偏高（${metrics.pwvMs} m/s）"
                if (level != SafetyLevel.DANGER) {
                    level = SafetyLevel.WARN
                }
            }
        }

        return SafetyResult(
            level = level,
            warnings = warnings,
            blockReason = null
        )
    }
}
