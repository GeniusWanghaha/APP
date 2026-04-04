package com.photosentinel.health.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photosentinel.health.data.DetectionResult
import com.photosentinel.health.data.ElasticityLevel
import com.photosentinel.health.data.TrendPoint
import com.photosentinel.health.domain.model.DetectionInput
import com.photosentinel.health.domain.model.DeviceLinkState
import com.photosentinel.health.domain.model.ExportFormat
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MeasurementSession
import com.photosentinel.health.domain.model.SessionEvent
import com.photosentinel.health.domain.model.SessionEventType
import com.photosentinel.health.domain.model.SessionQualityGrade
import com.photosentinel.health.domain.model.StoredRecord
import com.photosentinel.health.domain.repository.SessionAuditRepository
import com.photosentinel.health.domain.repository.UserSettingsRepository
import com.photosentinel.health.infrastructure.di.AppContainer
import com.photosentinel.health.infrastructure.signal.BatchAlignedMetrics
import com.photosentinel.health.infrastructure.signal.BatchCardioAnalyzer
import com.photosentinel.health.infrastructure.signal.CardiovascularSignalProcessor
import com.photosentinel.health.infrastructure.signal.MetricOutputTier
import com.photosentinel.health.infrastructure.signal.RealtimeCardioMetrics
import com.photosentinel.health.infrastructure.signal.SignalQualityGrade
import com.photosentinel.health.infrastructure.signal.TimelineReconstructor
import com.photosentinel.health.presentation.repository.HealthAssistantDataSource
import com.photosentinel.health.presentation.repository.HealthAssistantRepository
import com.photosentinel.health.presentation.repository.HardwareBridgeDataSource
import com.photosentinel.health.presentation.repository.HardwareBridgeRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

data class HomeUiState(
    val detection: DetectionResult = emptyDetection(),
    val trendData: List<TrendPoint> = emptyList(),
    val aiSummary: String = emptySummary(),
    val deviceStatus: String = "硬件未连接",
    val lastFrameDigest: String = "等待 BLE 数据帧",
    val statusDigest: String = "等待状态包",
    val outputTierLabel: String = "基础稳定输出",
    val qualityTips: List<String> = listOf("等待采集"),
    val riskDigest: String = "暂无异常事件",
    val sessionId: String? = null,
    val droppedFrames: Int = 0,
    val ecgWaveform: List<Float> = emptyList(),
    val ppgWaveform: List<Float> = emptyList(),
    val advancedIndicators: AdvancedIndicatorsUi = AdvancedIndicatorsUi(),
    val measurementProgress: Float = 0f,
    val measurementElapsedSec: Int = 0,
    val measurementTargetSec: Int = MEASUREMENT_TARGET_SECONDS,
    val measurementCompleted: Boolean = false,
    val exportHint: String? = null,
    val isStreaming: Boolean = false,
    val isLoading: Boolean = true,
    val hasRealData: Boolean = false,
    val errorMessage: String? = null
)

data class AdvancedIndicatorsUi(
    val sdnnMs: Double? = null,
    val rmssdMs: Double? = null,
    val pnn50Percent: Double? = null,
    val rrMeanMs: Double? = null,
    val rrCv: Double? = null,
    val patMs: Double? = null,
    val pttMs: Double? = null,
    val pwttMs: Double? = null,
    val pttValidBeatRatio: Double? = null,
    val perfusionIndex: Double? = null,
    val riseTimeMs: Double? = null,
    val halfWidthMs: Double? = null,
    val beatPulseConsistency: Double? = null,
    val arrhythmiaIndex: Double? = null,
    val reflectionIndex: Double? = null,
    val afProbabilityPercent: Double? = null,
    val sampleEntropy: Double? = null,
    val sd1Ms: Double? = null,
    val sd2Ms: Double? = null,
    val sd1Sd2Ratio: Double? = null,
    val arrhythmiaBeatRatioPercent: Double? = null,
    val ecgRespRateBpm: Double? = null,
    val ppgRespRateBpm: Double? = null,
    val qrsWidthMs: Double? = null,
    val qtMs: Double? = null,
    val qtcMs: Double? = null,
    val pWaveQualityPercent: Double? = null
)

class HomeViewModel(
    private val repository: HealthAssistantDataSource = HealthAssistantRepository(),
    private val hardwareBridge: HardwareBridgeDataSource = HardwareBridgeRepository(),
    private val userSettingsRepository: UserSettingsRepository = AppContainer.userSettingsRepository,
    private val sessionAuditRepository: SessionAuditRepository = AppContainer.sessionAuditRepository
) : ViewModel() {
    private var signalProcessor = CardiovascularSignalProcessor()
    private var timelineReconstructor = TimelineReconstructor()
    private val batchAnalyzer = BatchCardioAnalyzer()

    private var latestDeviceName = "unknown-device"
    private var latestFirmwareVersion: String? = null
    private var latestStatusSnapshot: com.photosentinel.health.domain.model.HardwareStatusSnapshot? = null
    private var activeSession: MeasurementSession? = null
    private var droppedFrameCount = 0
    private var qualitySum = 0.0
    private var qualityCount = 0
    private var hasSessionRealtimeData = false
    private var hasSessionPublishedMetrics = false
    private var measurementStartMicros: Long? = null
    private var measurementCompleted = false
    private val ecgWaveformWindow = ArrayDeque<Float>()
    private val ppgWaveformWindow = ArrayDeque<Float>()
    private val ecgMeasurementWindow = ArrayDeque<Double>()
    private val ppgMeasurementWindow = ArrayDeque<Double>()

    var uiState by mutableStateOf(HomeUiState())
        private set

    init {
        observeHardwareBridge()
        refresh()
    }

    fun connectHardware() {
        startMeasurement()
    }

    fun startMeasurement() {
        viewModelScope.launch {
            if (uiState.isStreaming) {
                if (activeSession == null) {
                    startSessionIfNeeded()
                } else {
                    restartMeasurementWindow()
                }
                return@launch
            }

            when (val connect = hardwareBridge.connect()) {
                is HealthResult.Success -> {
                    when (val stream = hardwareBridge.startStreaming()) {
                        is HealthResult.Success -> {
                            startSessionIfNeeded()
                        }

                        is HealthResult.Failure -> {
                            uiState = uiState.copy(errorMessage = stream.error.message)
                        }
                    }
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(errorMessage = connect.error.message)
                }
            }
        }
    }

    fun disconnectHardware() {
        viewModelScope.launch {
            hardwareBridge.stopStreaming()
            hardwareBridge.disconnect()
            persistCurrentMeasurement()
            hasSessionRealtimeData = false
            hasSessionPublishedMetrics = false
            finishSession("用户手动停止采集")
            resetSignalPipelines()
            uiState = uiState.copy(
                ecgWaveform = emptyList(),
                ppgWaveform = emptyList(),
                advancedIndicators = AdvancedIndicatorsUi(),
                measurementProgress = 0f,
                measurementElapsedSec = 0,
                measurementCompleted = false
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            when (val recordsResult = repository.recentRecords(limit = 7)) {
                is HealthResult.Success -> {
                    val records = recordsResult.value
                    if (records.isEmpty()) {
                        uiState = uiState.copy(
                            detection = emptyDetection(),
                            trendData = emptyList(),
                            aiSummary = emptySummary(),
                            isLoading = false,
                            hasRealData = false,
                            errorMessage = "暂无检测数据，开始采集后将自动生成趋势报告"
                        )
                        return@launch
                    }

                    val latest = records.last()
                    val mappedDetection = latest.toDetectionResult()
                    val mappedTrend = records.map { it.toTrendPoint() }

                    val summary = if (records.size >= 2) {
                        when (val trendResult = repository.analyzeTrend()) {
                            is HealthResult.Success -> trendResult.value.report
                            is HealthResult.Failure -> fallbackSummary(latest)
                        }
                    } else {
                        fallbackSummary(latest)
                    }

                    uiState = uiState.copy(
                        detection = mappedDetection,
                        trendData = mappedTrend,
                        aiSummary = summary,
                        isLoading = false,
                        hasRealData = true,
                        errorMessage = null
                    )
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = recordsResult.error.message
                    )
                }
            }
        }
    }

    private suspend fun startSessionIfNeeded() {
        if (activeSession != null) {
            return
        }
        val config = userSettingsRepository.currentBleConfig()
        val sessionId = "session_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
        val session = MeasurementSession(
            sessionId = sessionId,
            deviceId = latestDeviceName,
            firmwareVersion = latestFirmwareVersion,
            ecgSampleRateHz = config.ecgSampleRateHz,
            ppgSampleRateHz = config.ppgSampleRateHz,
            startedAt = Instant.now(),
            algorithmVersion = "algo-v2.0",
            modelVersion = "llm-v2.0"
        )

        when (val result = sessionAuditRepository.startSession(session)) {
            is HealthResult.Success -> {
                activeSession = session
                restartMeasurementWindow()
                uiState = uiState.copy(sessionId = session.sessionId)
                sessionAuditRepository.appendEvent(
                    SessionEvent(
                        sessionId = session.sessionId,
                        eventType = SessionEventType.LINK,
                        message = "采集会话已启动"
                    )
                )
            }

            is HealthResult.Failure -> {
                uiState = uiState.copy(errorMessage = result.error.message)
            }
        }
    }

    private fun restartMeasurementWindow() {
        droppedFrameCount = 0
        qualitySum = 0.0
        qualityCount = 0
        hasSessionRealtimeData = false
        hasSessionPublishedMetrics = false
        resetSignalPipelines()

        uiState = uiState.copy(
            detection = emptyDetection(),
            aiSummary = "正在进行 60 秒采集，采集完成后将统一输出 ECG+PPG 指标。",
            hasRealData = false,
            droppedFrames = 0,
            ecgWaveform = emptyList(),
            ppgWaveform = emptyList(),
            advancedIndicators = AdvancedIndicatorsUi(),
            measurementProgress = 0f,
            measurementElapsedSec = 0,
            measurementCompleted = false,
            outputTierLabel = "采集中",
            qualityTips = listOf("请保持静止并持续贴合 ECG 电极与指尖 PPG 传感区。"),
            riskDigest = "采集中，60 秒后生成风险提示。",
            errorMessage = null
        )
    }

    private fun resetSignalPipelines() {
        signalProcessor = CardiovascularSignalProcessor()
        timelineReconstructor = TimelineReconstructor()
        measurementStartMicros = null
        measurementCompleted = false
        ecgWaveformWindow.clear()
        ppgWaveformWindow.clear()
        ecgMeasurementWindow.clear()
        ppgMeasurementWindow.clear()
    }

    private suspend fun finishSession(reason: String) {
        val session = activeSession ?: return
        val avgQuality = if (qualityCount == 0) 0.0 else qualitySum / qualityCount.toDouble()
        val grade = avgQuality.toSessionGrade()
        val finalSession = session.copy(
            endedAt = Instant.now(),
            qualityGrade = grade,
            droppedFrameCount = droppedFrameCount,
            notes = reason
        )
        sessionAuditRepository.finishSession(finalSession)
        sessionAuditRepository.appendEvent(
            SessionEvent(
                sessionId = session.sessionId,
                eventType = SessionEventType.USER_ACTION,
                message = "会话结束：$reason"
            )
        )

        val exportJson = sessionAuditRepository.exportSession(
            sessionId = session.sessionId,
            format = ExportFormat.JSON
        )
        val exportCsv = sessionAuditRepository.exportSession(
            sessionId = session.sessionId,
            format = ExportFormat.CSV
        )
        val exportHint = buildString {
            if (exportJson is HealthResult.Success) {
                append("JSON: ${exportJson.value} ")
            }
            if (exportCsv is HealthResult.Success) {
                append("CSV: ${exportCsv.value}")
            }
        }.trim()

        activeSession = null
        uiState = uiState.copy(
            exportHint = exportHint.ifBlank { null }
        )
    }

    private suspend fun persistCurrentMeasurement() {
        if (!hasSessionPublishedMetrics) {
            return
        }
        val sessionId = uiState.sessionId
        val detection = uiState.detection
        val input = DetectionInput(
            timestamp = Instant.now().toString(),
            sessionId = sessionId,
            algorithmVersion = "algo-v2.0",
            modelVersion = "llm-v2.0",
            outputTier = uiState.outputTierLabel,
            pwvMs = detection.pwvValue.toDouble(),
            heartRate = detection.heartRate,
            spo2Percent = detection.spo2Percent.toDouble(),
            elasticityScore = detection.elasticityScore,
            vascularAge = detection.vascularAge,
            riseTimeMs = detection.pttMs.roundToInt(),
            signalQuality = detection.signalQualityPercent / 100.0
        )
        val result = repository.analyzeMeasurement(input)
        if (result is HealthResult.Failure) {
            uiState = uiState.copy(errorMessage = result.error.message)
            return
        }
        if (sessionId != null) {
            sessionAuditRepository.appendEvent(
                SessionEvent(
                    sessionId = sessionId,
                    eventType = SessionEventType.METRIC_OUTPUT,
                    message = "会话结束后已保存检测结果"
                )
            )
        }
        hasSessionPublishedMetrics = false
    }

    private fun observeHardwareBridge() {
        viewModelScope.launch {
            hardwareBridge.connectionState.collect { state ->
                latestDeviceName = when (state) {
                    is DeviceLinkState.Connected -> state.deviceName
                    is DeviceLinkState.Connecting -> state.target
                    else -> latestDeviceName
                }

                uiState = uiState.copy(
                    deviceStatus = state.toStatusText(),
                    isStreaming = state is DeviceLinkState.Connected
                )
            }
        }

        viewModelScope.launch {
            hardwareBridge.statusSnapshots.collect { status ->
                latestStatusSnapshot = status
                latestFirmwareVersion = "protocol-${status.protocolVersion}"
                uiState = uiState.copy(
                    statusDigest = buildString {
                        append("MTU=")
                        append(status.mtu)
                        append(" | 队列=")
                        append(status.bleQueueItems)
                        append(" | 已发送=")
                        append(status.transmittedFrameCount)
                        append(" | 丢帧=")
                        append(status.bleDroppedFrameCount)
                        append(" | 手指接触=")
                        append(if (status.fingerDetected) "是" else "否")
                        append(" | 传感器就绪=")
                        append(if (status.sensorReady) "是" else "否")
                    }
                )
            }
        }

        viewModelScope.launch {
            hardwareBridge.rawFrames.collect { frame ->
                val reconstructed = timelineReconstructor.reconstruct(
                    frame = frame,
                    status = latestStatusSnapshot,
                    config = userSettingsRepository.currentBleConfig()
                )

                if (reconstructed.droppedFrameIds.isNotEmpty()) {
                    droppedFrameCount += reconstructed.droppedFrameIds.size
                    uiState = uiState.copy(droppedFrames = droppedFrameCount)
                    activeSession?.let { session ->
                        sessionAuditRepository.appendEvent(
                            SessionEvent(
                                sessionId = session.sessionId,
                                eventType = SessionEventType.PACKET_LOSS,
                                message = "检测到丢帧: ${reconstructed.droppedFrameIds.joinToString()}",
                                payloadJson = "{\"frameId\":${frame.frameId},\"droppedCount\":${reconstructed.droppedFrameIds.size}}"
                            )
                        )
                    }
                }

                val realtimeMetrics = signalProcessor.ingest(reconstructed)
                if (measurementStartMicros == null) {
                    measurementStartMicros =
                        reconstructed.ecgTimeline.firstOrNull()?.timestampMicros
                            ?: frame.baseTimestampMicros
                }
                val startMicros = measurementStartMicros ?: frame.baseTimestampMicros
                val elapsedMicros = (frame.baseTimestampMicros - startMicros).coerceAtLeast(0L)
                val elapsedSec = (elapsedMicros / 1_000_000L).toInt().coerceIn(0, MEASUREMENT_TARGET_SECONDS)
                val currentProgress = (elapsedMicros.toDouble() / MEASUREMENT_WINDOW_US.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)
                val reachedMeasurementWindow = elapsedMicros >= MEASUREMENT_WINDOW_US
                val updatedDetection = realtimeMetrics?.toDetectionResult(uiState.detection) ?: uiState.detection
                val updatedSummary = realtimeMetrics?.toRealtimeSummary(frame) ?: uiState.aiSummary
                val riskDigest = realtimeMetrics?.riskEvents
                    ?.joinToString(separator = "；") { "${it.title}(${(it.confidence * 100).roundToInt()}%)" }
                    ?.ifBlank { "暂无异常事件" }
                    ?: "暂无异常事件"

                if (realtimeMetrics != null) {
                    hasSessionRealtimeData = true
                    qualitySum += realtimeMetrics.signalQuality
                    qualityCount += 1
                    activeSession?.let { session ->
                        if (realtimeMetrics.signalQuality < 0.65) {
                            sessionAuditRepository.appendEvent(
                                SessionEvent(
                                    sessionId = session.sessionId,
                                    eventType = SessionEventType.QUALITY_GATE,
                                    message = "信号质量低于高阶门控阈值，降级为基础输出",
                                    payloadJson = "{\"quality\":${"%.3f".format(realtimeMetrics.signalQuality)},\"tier\":\"${realtimeMetrics.outputTier}\"}"
                                )
                            )
                        }
                    }
                }

                appendWindowValues(
                    target = ecgMeasurementWindow,
                    values = reconstructed.ecgTimeline.map { it.value.toDouble() },
                    maxSize = ECG_MEASUREMENT_MAX_POINTS
                )
                appendWindowValues(
                    target = ppgMeasurementWindow,
                    values = reconstructed.ppgIrTimeline.map { it.value.toDouble() },
                    maxSize = PPG_MEASUREMENT_MAX_POINTS
                )

                var shouldPublishMetrics =
                    reachedMeasurementWindow &&
                        !measurementCompleted
                var batchMetrics: BatchAlignedMetrics? = null
                if (shouldPublishMetrics) {
                    val config = userSettingsRepository.currentBleConfig()
                    batchMetrics = batchAnalyzer.analyze(
                        ecgRaw = ecgMeasurementWindow.toDoubleArray(),
                        ppgIrRaw = ppgMeasurementWindow.toDoubleArray(),
                        fsEcg = config.ecgSampleRateHz.toDouble(),
                        fsPpg = config.ppgSampleRateHz.toDouble()
                    )
                    shouldPublishMetrics = batchMetrics != null
                }
                if (shouldPublishMetrics && batchMetrics != null) {
                    measurementCompleted = true
                    hasSessionPublishedMetrics = true
                    activeSession?.let { session ->
                        sessionAuditRepository.appendEvent(
                            SessionEvent(
                                sessionId = session.sessionId,
                                eventType = SessionEventType.METRIC_OUTPUT,
                                message = "60秒采集完成，已生成本次测量指标"
                            )
                        )
                    }
                }

                appendWaveformValues(
                    target = ecgWaveformWindow,
                    values = reconstructed.ecgTimeline.map { it.value.toFloat() },
                    maxSize = ECG_WAVEFORM_MAX_POINTS
                )
                appendWaveformValues(
                    target = ppgWaveformWindow,
                    values = reconstructed.ppgIrTimeline.map { it.value.toFloat() },
                    maxSize = PPG_WAVEFORM_MAX_POINTS
                )
                val publishedDetection = if (shouldPublishMetrics && batchMetrics != null) {
                    batchMetrics.toDetectionResult(
                        previous = uiState.detection,
                        fallbackRealtime = updatedDetection
                    )
                } else {
                    uiState.detection
                }
                val publishedSummary = if (shouldPublishMetrics && batchMetrics != null) {
                    batchMetrics.toSummaryText(
                        frameId = frame.frameId,
                        baseTimestampMicros = frame.baseTimestampMicros
                    )
                } else if (shouldPublishMetrics) {
                    updatedSummary
                } else {
                    uiState.aiSummary
                }
                val publishedRiskDigest = if (shouldPublishMetrics && batchMetrics != null) {
                    batchMetrics.toRiskDigest()
                } else if (shouldPublishMetrics) {
                    riskDigest
                } else if (measurementCompleted) {
                    uiState.riskDigest
                } else {
                    "采集中，60 秒后生成风险提示"
                }
                val publishedOutputTier = if (shouldPublishMetrics && batchMetrics != null) {
                    batchMetrics.toOutputTierLabel()
                } else if (shouldPublishMetrics) {
                    realtimeMetrics?.outputTier?.toLabel() ?: uiState.outputTierLabel
                } else if (measurementCompleted) {
                    uiState.outputTierLabel
                } else {
                    "采集中"
                }
                val publishedQualityTips = if (shouldPublishMetrics && batchMetrics != null) {
                    batchMetrics.toQualityTips()
                } else if (shouldPublishMetrics) {
                    realtimeMetrics?.qualityTips ?: uiState.qualityTips
                } else if (measurementCompleted) {
                    uiState.qualityTips
                } else {
                    listOf("采集中，请保持静止并持续贴合电极与指尖光路")
                }
                val advancedIndicators = if (shouldPublishMetrics && batchMetrics != null) {
                    batchMetrics.toAdvancedIndicators(
                        previous = uiState.advancedIndicators,
                        fallbackRealtime = realtimeMetrics
                    )
                } else if (shouldPublishMetrics) {
                    realtimeMetrics?.toAdvancedIndicators(uiState.advancedIndicators) ?: uiState.advancedIndicators
                } else {
                    uiState.advancedIndicators
                }
                val measurementGateError =
                    if (reachedMeasurementWindow && !measurementCompleted && batchMetrics == null) {
                        "已采集 60 秒但信号质量不足，正在继续采集，请调整接触后保持静止。"
                    } else {
                        null
                    }

                uiState = uiState.copy(
                    detection = publishedDetection,
                    aiSummary = publishedSummary,
                    riskDigest = publishedRiskDigest,
                    outputTierLabel = publishedOutputTier,
                    qualityTips = publishedQualityTips,
                    advancedIndicators = advancedIndicators,
                    ecgWaveform = if (measurementCompleted) ecgWaveformWindow.toList() else emptyList(),
                    ppgWaveform = if (measurementCompleted) ppgWaveformWindow.toList() else emptyList(),
                    measurementProgress = if (measurementCompleted) 1f else currentProgress,
                    measurementElapsedSec = if (measurementCompleted) MEASUREMENT_TARGET_SECONDS else elapsedSec,
                    measurementCompleted = measurementCompleted,
                    isLoading = false,
                    hasRealData = uiState.hasRealData || shouldPublishMetrics,
                    errorMessage = measurementGateError,
                    lastFrameDigest = buildString {
                        append("FrameID=")
                        append(frame.frameId)
                        append(" | T_base=")
                        append(frame.baseTimestampMicros)
                        append("μs | ECG=")
                        append(frame.ecgSamples.size)
                        append("点 | PPG(R/IR)=")
                        append(frame.ppgRedSamples.size)
                        append("/")
                        append(frame.ppgIrSamples.size)
                        append("点 | Flags=0x")
                        append(frame.stateFlags.toString(16).padStart(4, '0').uppercase())
                        append(" | 丢帧补点=")
                        append(if (reconstructed.displayCompensationApplied) "是" else "否")
                        append(" | 指标有效窗=")
                        append(if (reconstructed.metricsUsable) "是" else "否")
                        append(" | 采集进度=")
                        append(elapsedSec)
                        append("/")
                        append(MEASUREMENT_TARGET_SECONDS)
                        append("s")
                        if (shouldPublishMetrics && batchMetrics != null) {
                            append(" | SQI=")
                            append((batchMetrics.ppgSqi * 100.0).roundToInt())
                            append("%")
                        } else if (shouldPublishMetrics && realtimeMetrics != null) {
                            append(" | SQI=")
                            append((realtimeMetrics.signalQuality * 100).roundToInt())
                            append("%(")
                            append(realtimeMetrics.qualityGrade.toChineseLabel())
                            append(")")
                        }
                    }
                )
            }
        }
    }

    private fun fallbackSummary(record: StoredRecord): String {
        val metrics = record.detection.metrics
        return """
            当前 ECG+PPG 联合监测结果：心率 ${metrics.heartRate} 次/分，血氧 ${"%.1f".format(metrics.spo2Percent)}%。
            建议在同一姿势下连续采集，以提高 PAT/PTT/PWTT 等时序特征稳定性。
            累积至少 7 天数据后，可获得更可靠的趋势结论与风险提示。
        """.trimIndent()
    }
}

private fun StoredRecord.toDetectionResult(): DetectionResult {
    val metrics = detection.metrics
    return DetectionResult(
        pwvValue = metrics.pwvMs.toFloat(),
        heartRate = metrics.heartRate,
        vascularAge = metrics.vascularAge,
        elasticityLevel = metrics.elasticityScore.toElasticityLevel(),
        elasticityScore = metrics.elasticityScore,
        pttMs = metrics.riseTimeMs.toFloat(),
        spo2Percent = metrics.spo2Percent.toFloat(),
        signalQualityPercent = (metrics.signalQuality * 100).toInt().coerceIn(0, 100)
    )
}

private fun StoredRecord.toTrendPoint(): TrendPoint {
    val label = runCatching {
        val instant = Instant.parse(detection.timestamp)
        instant.atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("MM/dd"))
    }.getOrElse {
        detection.timestamp.take(10)
    }

    return TrendPoint(
        date = label,
        pwvValue = detection.metrics.pwvMs.toFloat(),
        elasticityScore = detection.metrics.elasticityScore
    )
}

private fun Int.toElasticityLevel(): ElasticityLevel {
    return when {
        this >= 90 -> ElasticityLevel.EXCELLENT
        this >= 75 -> ElasticityLevel.GOOD
        this >= 55 -> ElasticityLevel.FAIR
        else -> ElasticityLevel.POOR
    }
}

private fun RealtimeCardioMetrics.toDetectionResult(previous: DetectionResult): DetectionResult {
    val score = elasticityScore.coerceIn(0, 100)
    return previous.copy(
        pwvValue = pwvMs.toFloat(),
        heartRate = heartRateBpm.coerceIn(35, 190),
        vascularAge = vascularAge.coerceIn(18, 85),
        elasticityLevel = score.toElasticityLevel(),
        elasticityScore = score,
        pttMs = pttMs.toFloat().coerceIn(80f, 400f),
        spo2Percent = spo2Percent.toFloat().coerceIn(80f, 100f),
        signalQualityPercent = (signalQuality * 100).toInt().coerceIn(0, 100)
    )
}

private fun RealtimeCardioMetrics.toAdvancedIndicators(previous: AdvancedIndicatorsUi): AdvancedIndicatorsUi {
    return previous.copy(
        sdnnMs = sdnnMs,
        rmssdMs = rmssdMs,
        pnn50Percent = pnn50Percent,
        rrMeanMs = rrMeanMs,
        patMs = patMs,
        pttMs = pttMs,
        pwttMs = pwttMs,
        perfusionIndex = perfusionIndex,
        riseTimeMs = riseTimeMs,
        halfWidthMs = halfWidthMs,
        beatPulseConsistency = beatPulseConsistency,
        arrhythmiaIndex = arrhythmiaIndex,
        reflectionIndex = reflectionIndex
    )
}

private fun BatchAlignedMetrics.toDetectionResult(
    previous: DetectionResult,
    fallbackRealtime: DetectionResult
): DetectionResult {
    val boundedPtt = pttMs.coerceIn(80.0, 400.0)
    val signalQuality = ppgSqi.coerceIn(0.0, 1.0)
    val pwv = (0.45 / (boundedPtt / 1_000.0)).coerceIn(3.0, 15.0)
    val elasticity = (
        100.0 -
            (pwv - 6.0) * 12.0 -
            abs(heartRateBpm - 72.0) * 0.25 +
            signalQuality * 10.0
        ).roundToInt().coerceIn(0, 100)
    val vascularAge = (
        23.0 + (pwv - 5.0) * 8.0 + if (heartRateBpm > 90.0) 2.0 else 0.0
        ).roundToInt().coerceIn(18, 80)

    return previous.copy(
        pwvValue = pwv.toFloat(),
        heartRate = heartRateBpm.coerceIn(35, 190),
        vascularAge = vascularAge,
        elasticityLevel = elasticity.toElasticityLevel(),
        elasticityScore = elasticity,
        pttMs = boundedPtt.toFloat(),
        spo2Percent = fallbackRealtime.spo2Percent,
        signalQualityPercent = (signalQuality * 100).roundToInt().coerceIn(0, 100)
    )
}

private fun BatchAlignedMetrics.toAdvancedIndicators(
    previous: AdvancedIndicatorsUi,
    fallbackRealtime: RealtimeCardioMetrics?
): AdvancedIndicatorsUi {
    return previous.copy(
        sdnnMs = sdnnMs,
        rmssdMs = rmssdMs,
        pnn50Percent = pnn50Percent,
        rrMeanMs = rrMeanMs,
        rrCv = rrCv,
        patMs = patMs,
        pttMs = pttMs,
        pwttMs = pwttMs,
        pttValidBeatRatio = pttValidBeatRatio,
        perfusionIndex = fallbackRealtime?.perfusionIndex,
        riseTimeMs = riseTimeMs,
        halfWidthMs = riseTimeMs * 1.6,
        beatPulseConsistency = beatPulseConsistency,
        arrhythmiaIndex = arrhythmiaIndex,
        reflectionIndex = fallbackRealtime?.reflectionIndex,
        afProbabilityPercent = afProbabilityPercent,
        sampleEntropy = sampleEntropy,
        sd1Ms = sd1Ms,
        sd2Ms = sd2Ms,
        sd1Sd2Ratio = sd1Sd2Ratio,
        arrhythmiaBeatRatioPercent = arrhythmiaBeatRatioPercent,
        ecgRespRateBpm = ecgRespRateBpm,
        ppgRespRateBpm = ppgRespRateBpm,
        qrsWidthMs = qrsWidthMs,
        qtMs = qtMs,
        qtcMs = qtcMs,
        pWaveQualityPercent = pWaveQualityPercent
    )
}

private fun BatchAlignedMetrics.toOutputTierLabel(): String {
    return when {
        ppgSqi >= 0.82 -> "鐮旂┒鎵╁睍杈撳嚭"
        ppgSqi >= 0.65 -> "楂橀樁鏉′欢杈撳嚭"
        else -> "鍩虹绋冲畾杈撳嚭"
    }
}

/*
private fun BatchAlignedMetrics.toRiskDigest(): String {
    val items = mutableListOf<String>()
    if (afProbabilityPercent >= 65.0) {
        items += "鎴块ⅳ椋庨櫓鎻愮ず(${afProbabilityPercent.roundToInt()}%)"
    } else if (arrhythmiaBeatRatioPercent >= 18.0) {
        items += "寮傚父鑺傚緥鎻愮ず(${arrhythmiaBeatRatioPercent.roundToInt()}%)"
    }
    if (ecgRespRateBpm != null && (ecgRespRateBpm < 8.0 || ecgRespRateBpm > 24.0)) {
        items += "鍛煎惛鐜囧紓甯告彁绀?ECG)"
    }
    return if (items.isEmpty()) { "No obvious risk event" /*
        "鏆傛棤鏄庣‘寮傚父浜嬩欢"
    */ } else {
        items.joinToString(" | ")
    }
}

private fun BatchAlignedMetrics.toQualityTips(): List<String> {
    val tips = mutableListOf<String>()
    if (ppgSqi < 0.65) {
        tips += "PPG SQI 杈冧綆锛岃绋冲畾鎸夊帇鎵嬫寚骞跺噺灏戠幆澧冨厜骞叉壈"
    }
    if (beatPulseConsistency < 0.45) {
        tips += "蹇冩悘-鑴夋悘涓€鑷存€у亸浣庯紝璇峰娴嬪苟妫€鏌ユ帴瑙︾姸鎬?
    }
    if ((qrsWidthMs == null || qtMs == null) && pWaveQualityPercent != null && pWaveQualityPercent < 50.0) {
        tips += "P 娉㈠彲闈犳€т笉瓒筹紝QT/P 娴佺▼鏈疆闄嶇骇"
    }
    if (tips.isEmpty()) {
        tips += "60 绉掔獥鍙ｆ壒澶勭悊瀹屾垚锛屾寚鏍囪川閲忚揪鏍?
    }
    return tips
}

private fun BatchAlignedMetrics.toSummaryText(frameId: Int, baseTimestampMicros: Long): String {
    val ecgRespText = ecgRespRateBpm?.let { "%.1f".format(it) } ?: "--"
    val ppgRespText = ppgRespRateBpm?.let { "%.1f".format(it) } ?: "--"
    return """
        宸叉牴鎹?60 绉掔獥鍙ｆ壒澶勭悊杈撳嚭 ECG+PPG 鎸囨爣銆?
        FrameID=$frameId锛孴_base=$baseTimestampMicros 渭s銆?
        HR $heartRateBpm 娆?鍒嗭紝PTT ${"%.1f".format(pttMs)} ms锛孭AT ${"%.1f".format(patMs)} ms锛孲QI ${(ppgSqi * 100).roundToInt()}%銆?
        HRV: SDNN ${"%.1f".format(sdnnMs)} ms, RMSSD ${"%.1f".format(rmssdMs)} ms, pNN50 ${"%.1f".format(pnn50Percent)}%銆?
        AF 鍒濇姒傜巼 ${"%.1f".format(afProbabilityPercent)}%锛涘懠鍚哥巼 ECG/PPG: $ecgRespText / $ppgRespText bpm銆?
        QT/QRS: QRS ${formatOrDash(qrsWidthMs)} ms, QT ${formatOrDash(qtMs)} ms, QTc ${formatOrDash(qtcMs)} ms銆?
    """.trimIndent()
}

*/
private fun BatchAlignedMetrics.toRiskDigest(): String {
    val items = mutableListOf<String>()
    if (afProbabilityPercent >= 65.0) {
        items += "AF risk (${afProbabilityPercent.roundToInt()}%)"
    } else if (arrhythmiaBeatRatioPercent >= 18.0) {
        items += "Rhythm irregularity (${arrhythmiaBeatRatioPercent.roundToInt()}%)"
    }
    if (ecgRespRateBpm != null && (ecgRespRateBpm < 8.0 || ecgRespRateBpm > 24.0)) {
        items += "Respiration out-of-range (ECG)"
    }
    return if (items.isEmpty()) {
        "No obvious risk event"
    } else {
        items.joinToString(" | ")
    }
}

private fun BatchAlignedMetrics.toQualityTips(): List<String> {
    val tips = mutableListOf<String>()
    if (ppgSqi < 0.65) {
        tips += "PPG SQI is low; keep finger contact stable and reduce ambient light noise."
    }
    if (beatPulseConsistency < 0.45) {
        tips += "Beat-pulse consistency is low; retest after stabilizing posture and contact."
    }
    if ((qrsWidthMs == null || qtMs == null) && pWaveQualityPercent != null && pWaveQualityPercent < 50.0) {
        tips += "P-wave reliability is low; QT/P-wave output is downgraded in this window."
    }
    if (tips.isEmpty()) {
        tips += "60-second batch analysis completed with usable signal quality."
    }
    return tips
}

private fun BatchAlignedMetrics.toSummaryText(frameId: Int, baseTimestampMicros: Long): String {
    val ecgRespText = ecgRespRateBpm?.let { "%.1f".format(it) } ?: "--"
    val ppgRespText = ppgRespRateBpm?.let { "%.1f".format(it) } ?: "--"
    return """
        60-second batch analysis completed for ECG+PPG.
        FrameID=$frameId, T_base=$baseTimestampMicros us.
        HR $heartRateBpm bpm, PTT ${"%.1f".format(pttMs)} ms, PAT ${"%.1f".format(patMs)} ms, SQI ${(ppgSqi * 100).roundToInt()}%.
        HRV: SDNN ${"%.1f".format(sdnnMs)} ms, RMSSD ${"%.1f".format(rmssdMs)} ms, pNN50 ${"%.1f".format(pnn50Percent)}%.
        AF probability ${"%.1f".format(afProbabilityPercent)}%; Resp ECG/PPG: $ecgRespText / $ppgRespText bpm.
        QT/QRS: QRS ${formatOrDash(qrsWidthMs)} ms, QT ${formatOrDash(qtMs)} ms, QTc ${formatOrDash(qtcMs)} ms.
    """.trimIndent()
}

private fun formatOrDash(value: Double?): String {
    return value?.let { "%.1f".format(it) } ?: "--"
}

private fun appendWaveformValues(target: ArrayDeque<Float>, values: List<Float>, maxSize: Int) {
    values.forEach { value ->
        target.addLast(value)
    }
    while (target.size > maxSize) {
        target.removeFirst()
    }
}

private fun appendWindowValues(target: ArrayDeque<Double>, values: List<Double>, maxSize: Int) {
    values.forEach { value ->
        target.addLast(value)
    }
    while (target.size > maxSize) {
        target.removeFirst()
    }
}

private fun RealtimeCardioMetrics.toRealtimeSummary(
    frame: com.photosentinel.health.domain.model.HardwareFrame
): String {
    val riskText = if (riskEvents.isEmpty()) {
        "暂无明确异常事件"
    } else {
        riskEvents.joinToString(separator = "；") { "${it.title}(${(it.confidence * 100).roundToInt()}%)" }
    }
    val hrvText = if (sdnnMs != null && rmssdMs != null) {
        "SDNN ${"%.1f".format(sdnnMs)}ms, RMSSD ${"%.1f".format(rmssdMs)}ms"
    } else {
        "HRV 指标未达输出门限"
    }

    return """
        已接入 ESP32-S3 实时链路（BLE 原始帧 + 手机端滤波与门控）。
        最新帧 ${frame.frameId}，硬件时间戳 ${frame.baseTimestampMicros} μs（微秒级）。
        实时指标：HR ${heartRateBpm} 次/分，SpO₂ ${"%.1f".format(spo2Percent)}%，PTT ${"%.1f".format(pttMs)} ms，PWV ${"%.2f".format(pwvMs)} m/s。
        输出层级：${outputTier.toLabel()}，质量等级：${qualityGrade.toChineseLabel()}（${(signalQuality * 100).roundToInt()}%）。
        HRV/节律：$hrvText；风险提示：$riskText。
        血压相关趋势：$bloodPressureTrend（仅趋势参考，不替代医疗诊断）。
    """.trimIndent()
}

private fun DeviceLinkState.toStatusText(): String {
    return when (this) {
        is DeviceLinkState.Disconnected -> reason
        DeviceLinkState.Scanning -> "正在扫描硬件设备"
        is DeviceLinkState.Connecting -> "正在连接设备：$target"
        is DeviceLinkState.Connected -> "已连接：$deviceName（BLE）"
    }
}

private fun MetricOutputTier.toLabel(): String {
    return when (this) {
        MetricOutputTier.BASELINE -> "基础稳定输出"
        MetricOutputTier.ADVANCED -> "高阶条件输出"
        MetricOutputTier.RESEARCH -> "研究扩展输出"
    }
}

private fun SignalQualityGrade.toChineseLabel(): String {
    return when (this) {
        SignalQualityGrade.EXCELLENT -> "优秀"
        SignalQualityGrade.GOOD -> "良好"
        SignalQualityGrade.FAIR -> "一般"
        SignalQualityGrade.POOR -> "较差"
    }
}

private fun Double.toSessionGrade(): SessionQualityGrade {
    return when {
        this >= 0.85 -> SessionQualityGrade.EXCELLENT
        this >= 0.7 -> SessionQualityGrade.GOOD
        this >= 0.55 -> SessionQualityGrade.FAIR
        this > 0.0 -> SessionQualityGrade.POOR
        else -> SessionQualityGrade.INVALID
    }
}

private const val ECG_WAVEFORM_MAX_POINTS = 2_500
private const val PPG_WAVEFORM_MAX_POINTS = 3_200
private const val ECG_MEASUREMENT_MAX_POINTS = 30_000
private const val PPG_MEASUREMENT_MAX_POINTS = 48_000
private const val MEASUREMENT_TARGET_SECONDS = 60
private const val MEASUREMENT_WINDOW_US = 60_000_000L

private fun emptyDetection(): DetectionResult {
    return DetectionResult(
        pwvValue = 0f,
        heartRate = 0,
        vascularAge = 0,
        elasticityLevel = ElasticityLevel.FAIR,
        elasticityScore = 0,
        pttMs = 0f,
        spo2Percent = 0f,
        signalQualityPercent = 0
    )
}

@Suppress("UNREACHABLE_CODE")
private fun emptySummary(): String {
    return "暂无真实分析结论。请先连接硬件并完成采集。"
    return """
        系统采用手机壳式 ECG+PPG 同步采集架构：硬件端负责高质量采集与 BLE 传输，手机端负责滤波、门控、指标计算与 AI 解释。
        当前定位为健康监测与风险提示，不替代医疗诊断设备。
        当信号质量达标时，系统可输出 HRV、PAT/PTT/PWTT 与趋势参考等高阶指标。
    """.trimIndent()
}
