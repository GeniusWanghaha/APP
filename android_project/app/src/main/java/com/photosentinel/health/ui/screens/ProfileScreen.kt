package com.photosentinel.health.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosentinel.health.domain.model.BleRuntimeConfig
import com.photosentinel.health.domain.model.ExportFormat
import com.photosentinel.health.domain.model.HardwareStatusSnapshot
import com.photosentinel.health.ui.theme.AccentBlue
import com.photosentinel.health.ui.theme.AccentCyan
import com.photosentinel.health.ui.theme.BgCard
import com.photosentinel.health.ui.theme.BgDeep
import com.photosentinel.health.ui.theme.BgPrimary
import com.photosentinel.health.ui.theme.DividerColor
import com.photosentinel.health.ui.theme.StatusExcellent
import com.photosentinel.health.ui.theme.StatusFair
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
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPrimary)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ═══════════ 用户头像区 ═══════════
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AccentCyan.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "未登录",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "登录后可同步健康数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    null, tint = TextTertiary, modifier = Modifier.size(20.dp)
                )
            }
        }

        // ═══════════ 设备管理 ═══════════
        SettingsGroup(
            title = "设备管理",
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            SettingsRow(
                icon = Icons.Outlined.Bluetooth,
                iconColor = AccentBlue,
                title = "连接状态",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.deviceStatus.contains("已连接")) StatusExcellent
                                    else TextTertiary
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            uiState.deviceStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            )
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 52.dp))

            val status = uiState.latestStatusSnapshot
            if (status != null) {
                val score = computeDiagnosticScore(status)
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    iconColor = if (score >= 75) StatusExcellent else StatusFair,
                    title = "诊断评分",
                    trailing = {
                        Text(
                            "$score / 100",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (score >= 75) StatusExcellent else StatusFair,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
                HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 52.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.requestStatusSnapshot() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("刷新状态") }
                OutlinedButton(
                    onClick = { viewModel.triggerSelfTest() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("自检") }
                OutlinedButton(
                    onClick = { viewModel.injectSyncMark() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("同步标记") }
            }
        }

        // ═══════════ 数据管理 ═══════════
        SettingsGroup(
            title = "数据管理",
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            SettingsRow(
                icon = Icons.Outlined.Storage,
                iconColor = AccentCyan,
                title = "会话记录",
                trailing = {
                    Text("${uiState.sessions.size} 条", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                },
                onClick = { viewModel.refreshSessions() }
            )
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 52.dp))

            SettingsRow(
                icon = Icons.Outlined.CloudUpload,
                iconColor = AccentBlue,
                title = "导出最新数据",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val latestSession = uiState.sessions.firstOrNull()
                        OutlinedButton(
                            onClick = { latestSession?.let { viewModel.exportSession(it.sessionId, ExportFormat.JSON) } },
                            enabled = latestSession != null,
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("JSON", style = MaterialTheme.typography.labelSmall) }
                        OutlinedButton(
                            onClick = { latestSession?.let { viewModel.exportSession(it.sessionId, ExportFormat.CSV) } },
                            enabled = latestSession != null,
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("CSV", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            )
        }

        // ═══════════ 隐私设置 ═══════════
        SettingsGroup(
            title = "隐私设置",
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusExcellent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Security, null, tint = StatusExcellent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("允许上传脱敏摘要", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text(
                        "仅上传统计摘要用于 AI 解释，不传输原始波形",
                        style = MaterialTheme.typography.bodySmall, color = TextTertiary
                    )
                }
                Switch(
                    checked = uiState.privacyConsentGranted,
                    onCheckedChange = viewModel::setPrivacyConsent,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = StatusExcellent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = DividerColor
                    )
                )
            }
        }

        // ═══════════ 高级配置 (可折叠) ═══════════
        SettingsGroup(
            title = "高级配置",
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            SettingsRow(
                icon = Icons.Outlined.Settings,
                iconColor = TextTertiary,
                title = "BLE 调试参数",
                trailing = {
                    Text(
                        if (showAdvanced) "收起" else "展开",
                        style = MaterialTheme.typography.bodySmall, color = AccentCyan
                    )
                },
                onClick = { showAdvanced = !showAdvanced }
            )

            AnimatedVisibility(visible = showAdvanced) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 预设
                    Text("快捷预设", color = TextTertiary, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PresetButton("稳态采集", !uiState.isSaving) { applyPreset(viewModel, DevicePreset.STABLE_QUALITY) }
                        PresetButton("抗干扰", !uiState.isSaving) { applyPreset(viewModel, DevicePreset.ANTI_NOISE) }
                        PresetButton("高速调试", !uiState.isSaving) { applyPreset(viewModel, DevicePreset.HIGH_RATE_DEBUG) }
                    }

                    // UUID 配置
                    ConfigField(config.preferredDeviceNamePrefix, "设备名前缀", viewModel::updateDeviceNamePrefix)
                    ConfigField(config.serviceUuid, "Service UUID", viewModel::updateServiceUuid)
                    ConfigField(config.dataCharacteristicUuid, "Data Characteristic UUID", viewModel::updateDataUuid)
                    ConfigField(config.controlCharacteristicUuid, "Control Characteristic UUID", viewModel::updateControlUuid)
                    ConfigField(config.statusCharacteristicUuid, "Status Characteristic UUID", viewModel::updateStatusUuid)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConfigField(config.preferredMtu.toString(), "MTU", viewModel::updatePreferredMtu, Modifier.weight(1f))
                        ConfigField(config.ecgSampleRateHz.toString(), "ECG Hz", viewModel::updateEcgSampleRate, Modifier.weight(1f))
                        ConfigField(config.ppgSampleRateHz.toString(), "PPG Hz", viewModel::updatePpgSampleRate, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConfigField(config.ppgPhaseUs.toString(), "PPG 相位(us)", viewModel::updatePpgPhaseUs, Modifier.weight(1f))
                        ConfigField(config.ppgLatencyUs.toString(), "PPG 延时(us)", viewModel::updatePpgLatencyUs, Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.saveBleConfig() }, enabled = !uiState.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("保存配置") }
                        OutlinedButton(
                            onClick = { viewModel.resetBleConfig() }, enabled = !uiState.isSaving,
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("恢复默认") }
                    }
                }
            }
        }

        // ═══════════ 关于 ═══════════
        SettingsGroup(
            title = "关于",
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            SettingsRow(
                icon = Icons.Outlined.Info,
                iconColor = TextTertiary,
                title = "版本号",
                trailing = {
                    Text("v1.0.0", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
            )
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 52.dp))
            SettingsRow(
                icon = Icons.Outlined.Build,
                iconColor = TextTertiary,
                title = "构建信息",
                trailing = {
                    Text("ECG+PPG Vascular Edition", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
            )
        }

        // 提示信息
        uiState.message?.let { message ->
            Text(message, color = StatusExcellent, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        uiState.errorMessage?.let { error ->
            Text(error, color = StatusFair, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp))
        }

        // 底部免责声明
        Text(
            "本应用仅提供健康趋势参考，所有输出不构成医疗诊断或治疗建议。如有健康问题请咨询专业医生。",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ─── 通用子组件 ────────────────────────────────────

@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun PresetButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, DividerColor)
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun ConfigField(
    value: String, label: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = TextTertiary, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier, singleLine = true, shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentCyan, unfocusedBorderColor = DividerColor
        )
    )
}

// ─── 保留原有功能逻辑 ─────────────────────────────

private enum class DevicePreset(
    val displayName: String,
    val config: BleRuntimeConfig
) {
    STABLE_QUALITY("稳态采集", BleRuntimeConfig(preferredMtu = 247, ecgSampleRateHz = 250, ppgSampleRateHz = 400, ppgPhaseUs = 1_250, ppgLatencyUs = 0)),
    ANTI_NOISE("抗干扰", BleRuntimeConfig(preferredMtu = 180, ecgSampleRateHz = 250, ppgSampleRateHz = 300, ppgPhaseUs = 1_450, ppgLatencyUs = 400)),
    HIGH_RATE_DEBUG("高速调试", BleRuntimeConfig(preferredMtu = 320, ecgSampleRateHz = 500, ppgSampleRateHz = 500, ppgPhaseUs = 1_000, ppgLatencyUs = 0))
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
