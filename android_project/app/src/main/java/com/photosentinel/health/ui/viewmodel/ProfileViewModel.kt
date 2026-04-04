package com.photosentinel.health.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photosentinel.health.domain.model.BleRuntimeConfig
import com.photosentinel.health.domain.model.DeviceLinkState
import com.photosentinel.health.domain.model.ExportFormat
import com.photosentinel.health.domain.model.HardwareStatusSnapshot
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.MeasurementSession
import com.photosentinel.health.presentation.repository.HardwareBridgeDataSource
import com.photosentinel.health.presentation.repository.HardwareBridgeRepository
import com.photosentinel.health.presentation.repository.SessionAuditDataSource
import com.photosentinel.health.presentation.repository.SessionAuditPresentationRepository
import com.photosentinel.health.presentation.repository.SettingsDataSource
import com.photosentinel.health.presentation.repository.SettingsRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ProfileUiState(
    val editableConfig: BleRuntimeConfig = BleRuntimeConfig(),
    val privacyConsentGranted: Boolean = false,
    val sessions: List<MeasurementSession> = emptyList(),
    val deviceStatus: String = "硬件未连接",
    val latestStatusSnapshot: HardwareStatusSnapshot? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val settingsRepository: SettingsDataSource = SettingsRepository(),
    private val sessionAuditRepository: SessionAuditDataSource = SessionAuditPresentationRepository(),
    private val hardwareBridge: HardwareBridgeDataSource = HardwareBridgeRepository()
) : ViewModel() {
    var uiState by mutableStateOf(ProfileUiState())
        private set

    init {
        observeSettings()
        observeHardwareStatus()
        refreshSessions()
    }

    fun updateDeviceNamePrefix(value: String) {
        uiState = uiState.copy(editableConfig = uiState.editableConfig.copy(preferredDeviceNamePrefix = value))
    }

    fun updateServiceUuid(value: String) {
        uiState = uiState.copy(editableConfig = uiState.editableConfig.copy(serviceUuid = value))
    }

    fun updateDataUuid(value: String) {
        uiState = uiState.copy(editableConfig = uiState.editableConfig.copy(dataCharacteristicUuid = value))
    }

    fun updateControlUuid(value: String) {
        uiState = uiState.copy(editableConfig = uiState.editableConfig.copy(controlCharacteristicUuid = value))
    }

    fun updateStatusUuid(value: String) {
        uiState = uiState.copy(editableConfig = uiState.editableConfig.copy(statusCharacteristicUuid = value))
    }

    fun updatePreferredMtu(value: String) {
        val mtu = value.toIntOrNull() ?: uiState.editableConfig.preferredMtu
        uiState = uiState.copy(editableConfig = uiState.editableConfig.copy(preferredMtu = mtu))
    }

    fun updatePpgPhaseUs(value: String) {
        val phase = value.toIntOrNull() ?: uiState.editableConfig.ppgPhaseUs
        uiState = uiState.copy(editableConfig = uiState.editableConfig.copy(ppgPhaseUs = phase))
    }

    fun updatePpgLatencyUs(value: String) {
        val latency = value.toIntOrNull() ?: uiState.editableConfig.ppgLatencyUs
        uiState = uiState.copy(editableConfig = uiState.editableConfig.copy(ppgLatencyUs = latency))
    }

    fun updateEcgSampleRate(value: String) {
        val sampleRate = value.toIntOrNull() ?: uiState.editableConfig.ecgSampleRateHz
        uiState = uiState.copy(editableConfig = uiState.editableConfig.copy(ecgSampleRateHz = sampleRate))
    }

    fun updatePpgSampleRate(value: String) {
        val sampleRate = value.toIntOrNull() ?: uiState.editableConfig.ppgSampleRateHz
        uiState = uiState.copy(editableConfig = uiState.editableConfig.copy(ppgSampleRateHz = sampleRate))
    }

    fun saveBleConfig() {
        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true, message = null, errorMessage = null)
            when (val result = settingsRepository.updateBleConfig(uiState.editableConfig)) {
                is HealthResult.Success -> {
                    uiState = uiState.copy(
                        isSaving = false,
                        message = "BLE 配置已保存"
                    )
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(
                        isSaving = false,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }

    fun resetBleConfig() {
        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true, message = null, errorMessage = null)
            when (val result = settingsRepository.resetBleConfig()) {
                is HealthResult.Success -> {
                    uiState = uiState.copy(
                        isSaving = false,
                        editableConfig = settingsRepository.currentBleConfig(),
                        message = "BLE 配置已恢复默认值"
                    )
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(
                        isSaving = false,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }

    fun setPrivacyConsent(granted: Boolean) {
        viewModelScope.launch {
            when (val result = settingsRepository.setPrivacyConsentGranted(granted)) {
                is HealthResult.Success -> {
                    uiState = uiState.copy(
                        privacyConsentGranted = granted,
                        message = if (granted) {
                            "已同意隐私说明（仅上传脱敏摘要）"
                        } else {
                            "已关闭隐私授权（仍可离线使用）"
                        },
                        errorMessage = null
                    )
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            when (val result = sessionAuditRepository.recentSessions(limit = 10)) {
                is HealthResult.Success -> {
                    uiState = uiState.copy(
                        sessions = result.value,
                        errorMessage = null
                    )
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    fun exportSession(
        sessionId: String,
        format: ExportFormat
    ) {
        viewModelScope.launch {
            when (val result = sessionAuditRepository.exportSession(sessionId, format)) {
                is HealthResult.Success -> {
                    uiState = uiState.copy(
                        message = "会话已导出：${result.value}",
                        errorMessage = null
                    )
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    fun requestStatusSnapshot() {
        viewModelScope.launch {
            when (val result = hardwareBridge.requestStatusSnapshot()) {
                is HealthResult.Success -> {
                    uiState = uiState.copy(message = "已请求最新状态包", errorMessage = null)
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    fun triggerSelfTest() {
        viewModelScope.launch {
            when (val result = hardwareBridge.triggerSelfTest()) {
                is HealthResult.Success -> {
                    uiState = uiState.copy(message = "已触发固件自检", errorMessage = null)
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    fun injectSyncMark() {
        viewModelScope.launch {
            when (val result = hardwareBridge.injectSyncMark()) {
                is HealthResult.Success -> {
                    uiState = uiState.copy(message = "已注入 SYNC_MARK 同步标记", errorMessage = null)
                }

                is HealthResult.Failure -> {
                    uiState = uiState.copy(errorMessage = result.error.message)
                }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.bleConfig.collectLatest { config ->
                uiState = uiState.copy(editableConfig = config)
            }
        }

        viewModelScope.launch {
            settingsRepository.privacyConsentGranted.collectLatest { granted ->
                uiState = uiState.copy(privacyConsentGranted = granted)
            }
        }
    }

    private fun observeHardwareStatus() {
        viewModelScope.launch {
            hardwareBridge.connectionState.collectLatest { state ->
                uiState = uiState.copy(deviceStatus = state.toStatusText())
            }
        }

        viewModelScope.launch {
            hardwareBridge.statusSnapshots.collectLatest { snapshot ->
                uiState = uiState.copy(latestStatusSnapshot = snapshot)
            }
        }
    }

    private fun DeviceLinkState.toStatusText(): String {
        return when (this) {
            is DeviceLinkState.Disconnected -> reason
            DeviceLinkState.Scanning -> "正在扫描硬件设备"
            is DeviceLinkState.Connecting -> "正在连接设备：$target"
            is DeviceLinkState.Connected -> "已连接：$deviceName（BLE）"
        }
    }
}
