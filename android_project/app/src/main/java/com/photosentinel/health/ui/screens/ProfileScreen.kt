package com.photosentinel.health.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosentinel.health.domain.model.BleRuntimeConfig
import com.photosentinel.health.domain.model.ExportFormat
import com.photosentinel.health.domain.model.HardwareStatusSnapshot
import com.photosentinel.health.domain.model.SessionQualityGrade
import com.photosentinel.health.ui.components.SectionHeader
import com.photosentinel.health.ui.theme.AccentCyan
import com.photosentinel.health.ui.theme.BgCard
import com.photosentinel.health.ui.theme.BgPrimary
import com.photosentinel.health.ui.theme.StatusFair
import com.photosentinel.health.ui.theme.StatusGood
import com.photosentinel.health.ui.theme.TextPrimary
import com.photosentinel.health.ui.theme.TextSecondary
import com.photosentinel.health.ui.theme.TextTertiary
import com.photosentinel.health.ui.viewmodel.ProfileViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState = viewModel.uiState
    val config = uiState.editableConfig

    var sessionKeyword by rememberSaveable { mutableStateOf("") }
    var sessionGradeFilter by rememberSaveable { mutableStateOf("全部") }
    val sessions = uiState.sessions
        .filter { session ->
            val gradeMatched = sessionGradeFilter == "全部" || session.qualityGrade.name == sessionGradeFilter
            val keywordMatched =
                sessionKeyword.isBlank() ||
                    session.sessionId.contains(sessionKeyword, ignoreCase = true) ||
                    session.deviceId.contains(sessionKeyword, ignoreCase = true)
            gradeMatched && keywordMatched
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(title = "工程模式")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("BLE 配置中心", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text("快捷预设", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { applyPreset(viewModel, DevicePreset.STABLE_QUALITY) },
                        enabled = !uiState.isSaving
                    ) { Text("稳态采集") }
                    OutlinedButton(
                        onClick = { applyPreset(viewModel, DevicePreset.ANTI_NOISE) },
                        enabled = !uiState.isSaving
                    ) { Text("抗干扰") }
                    OutlinedButton(
                        onClick = { applyPreset(viewModel, DevicePreset.HIGH_RATE_DEBUG) },
                        enabled = !uiState.isSaving
                    ) { Text("高速调试") }
                }

                OutlinedTextField(
                    value = config.preferredDeviceNamePrefix,
                    onValueChange = viewModel::updateDeviceNamePrefix,
                    label = { Text("设备名前缀") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = config.serviceUuid,
                    onValueChange = viewModel::updateServiceUuid,
                    label = { Text("Service UUID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = config.dataCharacteristicUuid,
                    onValueChange = viewModel::updateDataUuid,
                    label = { Text("Data Characteristic UUID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = config.controlCharacteristicUuid,
                    onValueChange = viewModel::updateControlUuid,
                    label = { Text("Control Characteristic UUID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = config.statusCharacteristicUuid,
                    onValueChange = viewModel::updateStatusUuid,
                    label = { Text("Status Characteristic UUID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = config.preferredMtu.toString(),
                        onValueChange = viewModel::updatePreferredMtu,
                        label = { Text("MTU") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = config.ecgSampleRateHz.toString(),
                        onValueChange = viewModel::updateEcgSampleRate,
                        label = { Text("ECG Hz") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = config.ppgSampleRateHz.toString(),
                        onValueChange = viewModel::updatePpgSampleRate,
                        label = { Text("PPG Hz") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = config.ppgPhaseUs.toString(),
                        onValueChange = viewModel::updatePpgPhaseUs,
                        label = { Text("PPG 相位(us)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = config.ppgLatencyUs.toString(),
                        onValueChange = viewModel::updatePpgLatencyUs,
                        label = { Text("PPG 延时(us)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.saveBleConfig() },
                        enabled = !uiState.isSaving
                    ) {
                        Text("保存配置")
                    }
                    OutlinedButton(
                        onClick = { viewModel.resetBleConfig() },
                        enabled = !uiState.isSaving
                    ) {
                        Text("恢复默认")
                    }
                }
            }
        }

        SectionHeader(title = "设备诊断")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            val status = uiState.latestStatusSnapshot
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("连接状态：${uiState.deviceStatus}", color = TextSecondary)
                if (status == null) {
                    Text("暂未收到状态包", color = TextTertiary)
                } else {
                    val score = computeDiagnosticScore(status)
                    Text(
                        "诊断评分：$score / 100",
                        color = if (score >= 75) StatusGood else StatusFair,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "protocol=${status.protocolVersion} | mtu=${status.mtu} | tx=${status.transmittedFrameCount} | drop=${status.bleDroppedFrameCount}",
                        color = TextSecondary
                    )
                    Text(
                        "queue(ecg/ppg/ble)=${status.ecgRingItems}/${status.ppgRingItems}/${status.bleQueueItems} | hwMark=${status.bleQueueHighWatermark}",
                        color = TextSecondary
                    )
                    Text(
                        "传感状态：finger=${if (status.fingerDetected) "已检测" else "未检测"}，sensor=${if (status.sensorReady) "就绪" else "未就绪"}",
                        color = TextSecondary
                    )
                    decodeActiveStateFlags(status.stateFlags).ifEmpty { listOf("当前无告警状态位") }.forEach { tip ->
                        Text("• $tip", color = if (tip == "当前无告警状态位") StatusGood else StatusFair)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { viewModel.requestStatusSnapshot() }) { Text("GET_INFO") }
                    OutlinedButton(onClick = { viewModel.triggerSelfTest() }) { Text("SELF_TEST") }
                    OutlinedButton(onClick = { viewModel.injectSyncMark() }) { Text("SYNC_MARK") }
                }
            }
        }

        SectionHeader(title = "隐私与边界")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("允许上传脱敏摘要用于 AI 解释", color = TextPrimary)
                    Switch(
                        checked = uiState.privacyConsentGranted,
                        onCheckedChange = viewModel::setPrivacyConsent
                    )
                }
                Text(
                    text = "说明：系统仅上传结构化统计摘要，不上传原始 ECG/PPG 波形；AI 输出仅作为健康提示，不替代医疗诊断。",
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }
        }

        SectionHeader(title = "会话导出中心")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = sessionKeyword,
                    onValueChange = { sessionKeyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("按 SessionID / 设备ID 搜索") },
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("全部") + SessionQualityGrade.entries.map { it.name }).forEach { grade ->
                        OutlinedButton(
                            onClick = { sessionGradeFilter = grade },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (sessionGradeFilter == grade) AccentCyan else TextTertiary
                            )
                        ) {
                            Text(grade)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.refreshSessions() }) { Text("刷新会话列表") }
                    val latestSession = sessions.firstOrNull()
                    OutlinedButton(
                        onClick = { latestSession?.let { viewModel.exportSession(it.sessionId, ExportFormat.JSON) } },
                        enabled = latestSession != null
                    ) { Text("导出最新 JSON") }
                    OutlinedButton(
                        onClick = { latestSession?.let { viewModel.exportSession(it.sessionId, ExportFormat.CSV) } },
                        enabled = latestSession != null
                    ) { Text("导出最新 CSV") }
                }

                if (sessions.isEmpty()) {
                    Text("暂无会话记录", color = TextTertiary)
                } else {
                    sessions.forEach { session ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgPrimary)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("SessionID: ${session.sessionId}", color = TextPrimary)
                                Text(
                                    "设备: ${session.deviceId} | 质量: ${session.qualityGrade.name} | 丢帧: ${session.droppedFrameCount}",
                                    color = TextSecondary
                                )
                                Text(
                                    "开始: ${session.startedAt.toLocalText()} | 结束: ${session.endedAt?.toLocalText() ?: "--"}",
                                    color = TextTertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.exportSession(session.sessionId, ExportFormat.JSON) }
                                    ) { Text("JSON") }
                                    OutlinedButton(
                                        onClick = { viewModel.exportSession(session.sessionId, ExportFormat.CSV) }
                                    ) { Text("CSV") }
                                }
                            }
                        }
                    }
                }
            }
        }

        uiState.message?.let { message ->
            Text(message, color = StatusGood)
        }
        uiState.errorMessage?.let { error ->
            Text(error, color = StatusFair)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private enum class DevicePreset(
    val displayName: String,
    val config: BleRuntimeConfig
) {
    STABLE_QUALITY(
        displayName = "稳态采集",
        config = BleRuntimeConfig(
            preferredMtu = 247,
            ecgSampleRateHz = 250,
            ppgSampleRateHz = 400,
            ppgPhaseUs = 1_250,
            ppgLatencyUs = 0
        )
    ),
    ANTI_NOISE(
        displayName = "抗干扰",
        config = BleRuntimeConfig(
            preferredMtu = 180,
            ecgSampleRateHz = 250,
            ppgSampleRateHz = 300,
            ppgPhaseUs = 1_450,
            ppgLatencyUs = 400
        )
    ),
    HIGH_RATE_DEBUG(
        displayName = "高速调试",
        config = BleRuntimeConfig(
            preferredMtu = 320,
            ecgSampleRateHz = 500,
            ppgSampleRateHz = 500,
            ppgPhaseUs = 1_000,
            ppgLatencyUs = 0
        )
    )
}

private fun applyPreset(viewModel: ProfileViewModel, preset: DevicePreset) {
    viewModel.updatePreferredMtu(preset.config.preferredMtu.toString())
    viewModel.updateEcgSampleRate(preset.config.ecgSampleRateHz.toString())
    viewModel.updatePpgSampleRate(preset.config.ppgSampleRateHz.toString())
    viewModel.updatePpgPhaseUs(preset.config.ppgPhaseUs.toString())
    viewModel.updatePpgLatencyUs(preset.config.ppgLatencyUs.toString())
}

private fun computeDiagnosticScore(status: HardwareStatusSnapshot): Int {
    var score = 100
    score -= status.bleDroppedFrameCount.coerceAtMost(30).toInt()
    score -= status.ppgFifoOverflowCount.coerceAtMost(20).toInt()
    score -= status.ppgIntTimeoutCount.coerceAtMost(20).toInt()
    score -= status.bleBackpressureCount.coerceAtMost(20).toInt()
    if (!status.fingerDetected) score -= 10
    if (!status.sensorReady) score -= 10
    return score.coerceIn(0, 100)
}

private fun decodeActiveStateFlags(flags: Int): List<String> {
    val mappings = listOf(
        0x0001 to "bit0 ECG 导联异常，请检查电极接触",
        0x0002 to "bit1 ECG LO+ 异常",
        0x0004 to "bit2 ECG LO- 异常",
        0x0008 to "bit3 PPG FIFO 溢出，已触发门控",
        0x0010 to "bit4 PPG 中断超时，建议复测",
        0x0020 to "bit5 ADC 饱和，建议减小按压",
        0x0040 to "bit6 BLE 背压，链路拥塞",
        0x0200 to "bit9 SYNC_MARK 已注入（单帧标记）"
    )
    return mappings.filter { (bit, _) -> (flags and bit) != 0 }.map { it.second }
}

private fun java.time.Instant.toLocalText(): String {
    return atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
}
