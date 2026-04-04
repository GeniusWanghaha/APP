package com.photosentinel.health.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.photosentinel.health.domain.model.BleRuntimeConfig
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.PersistenceError
import com.photosentinel.health.domain.model.ValidationError
import com.photosentinel.health.domain.repository.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceUserSettingsRepository(
    context: Context
) : UserSettingsRepository {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences = appContext.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    private val mutableBleConfig = MutableStateFlow(loadBleConfig())
    private val mutablePrivacyConsent = MutableStateFlow(
        preferences.getBoolean(KEY_PRIVACY_CONSENT, false)
    )

    override val bleConfig: StateFlow<BleRuntimeConfig> = mutableBleConfig.asStateFlow()
    override val privacyConsentGranted: StateFlow<Boolean> = mutablePrivacyConsent.asStateFlow()

    override fun currentBleConfig(): BleRuntimeConfig {
        return mutableBleConfig.value
    }

    override suspend fun updateBleConfig(config: BleRuntimeConfig): HealthResult<Unit> {
        val sanitized = config.sanitized()
        val invalid = sanitized.validatedOrError()
        if (invalid != null) {
            return HealthResult.Failure(ValidationError(invalid.message))
        }

        return runCatching {
            preferences.edit()
                .putString(KEY_DEVICE_NAME_PREFIX, sanitized.preferredDeviceNamePrefix)
                .putString(KEY_SERVICE_UUID, sanitized.serviceUuid)
                .putString(KEY_DATA_UUID, sanitized.dataCharacteristicUuid)
                .putString(KEY_CONTROL_UUID, sanitized.controlCharacteristicUuid)
                .putString(KEY_STATUS_UUID, sanitized.statusCharacteristicUuid)
                .putInt(KEY_MTU, sanitized.preferredMtu)
                .putInt(KEY_ECG_RATE, sanitized.ecgSampleRateHz)
                .putInt(KEY_PPG_RATE, sanitized.ppgSampleRateHz)
                .putInt(KEY_PPG_PHASE_US, sanitized.ppgPhaseUs)
                .putInt(KEY_PPG_LATENCY_US, sanitized.ppgLatencyUs)
                .apply()
            mutableBleConfig.value = sanitized
            Unit
        }.fold(
            onSuccess = { HealthResult.Success(Unit) },
            onFailure = { HealthResult.Failure(PersistenceError(it.message ?: "更新 BLE 配置失败")) }
        )
    }

    override suspend fun resetBleConfig(): HealthResult<Unit> {
        return updateBleConfig(BleRuntimeConfig())
    }

    override suspend fun setPrivacyConsentGranted(granted: Boolean): HealthResult<Unit> {
        return runCatching {
            preferences.edit()
                .putBoolean(KEY_PRIVACY_CONSENT, granted)
                .apply()
            mutablePrivacyConsent.value = granted
            Unit
        }.fold(
            onSuccess = { HealthResult.Success(Unit) },
            onFailure = { HealthResult.Failure(PersistenceError(it.message ?: "更新隐私设置失败")) }
        )
    }

    private fun loadBleConfig(): BleRuntimeConfig {
        val loaded = BleRuntimeConfig(
            preferredDeviceNamePrefix = preferences.getString(
                KEY_DEVICE_NAME_PREFIX,
                BleRuntimeConfig.DEFAULT_DEVICE_NAME_PREFIX
            ).orEmpty(),
            serviceUuid = preferences.getString(
                KEY_SERVICE_UUID,
                BleRuntimeConfig.DEFAULT_SERVICE_UUID
            ).orEmpty(),
            dataCharacteristicUuid = preferences.getString(
                KEY_DATA_UUID,
                BleRuntimeConfig.DEFAULT_DATA_UUID
            ).orEmpty(),
            controlCharacteristicUuid = preferences.getString(
                KEY_CONTROL_UUID,
                BleRuntimeConfig.DEFAULT_CONTROL_UUID
            ).orEmpty(),
            statusCharacteristicUuid = preferences.getString(
                KEY_STATUS_UUID,
                BleRuntimeConfig.DEFAULT_STATUS_UUID
            ).orEmpty(),
            preferredMtu = preferences.getInt(KEY_MTU, BleRuntimeConfig.DEFAULT_MTU),
            ecgSampleRateHz = preferences.getInt(
                KEY_ECG_RATE,
                BleRuntimeConfig.DEFAULT_ECG_SAMPLE_RATE_HZ
            ),
            ppgSampleRateHz = preferences.getInt(
                KEY_PPG_RATE,
                BleRuntimeConfig.DEFAULT_PPG_SAMPLE_RATE_HZ
            ),
            ppgPhaseUs = preferences.getInt(KEY_PPG_PHASE_US, BleRuntimeConfig.DEFAULT_PPG_PHASE_US),
            ppgLatencyUs = preferences.getInt(
                KEY_PPG_LATENCY_US,
                BleRuntimeConfig.DEFAULT_PPG_LATENCY_US
            )
        ).sanitized()

        return if (loaded.validatedOrError() == null) {
            loaded
        } else {
            BleRuntimeConfig()
        }
    }

    private companion object {
        private const val PREF_NAME = "health_user_settings"

        private const val KEY_DEVICE_NAME_PREFIX = "ble_device_name_prefix"
        private const val KEY_SERVICE_UUID = "ble_service_uuid"
        private const val KEY_DATA_UUID = "ble_data_uuid"
        private const val KEY_CONTROL_UUID = "ble_control_uuid"
        private const val KEY_STATUS_UUID = "ble_status_uuid"
        private const val KEY_MTU = "ble_preferred_mtu"
        private const val KEY_ECG_RATE = "ble_ecg_sample_rate"
        private const val KEY_PPG_RATE = "ble_ppg_sample_rate"
        private const val KEY_PPG_PHASE_US = "ble_ppg_phase_us"
        private const val KEY_PPG_LATENCY_US = "ble_ppg_latency_us"

        private const val KEY_PRIVACY_CONSENT = "privacy_consent_granted"
    }
}
