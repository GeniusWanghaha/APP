package com.photosentinel.health.domain.repository

import com.photosentinel.health.domain.model.BleRuntimeConfig
import com.photosentinel.health.domain.model.HealthResult
import kotlinx.coroutines.flow.StateFlow

interface UserSettingsRepository {
    val bleConfig: StateFlow<BleRuntimeConfig>

    val privacyConsentGranted: StateFlow<Boolean>

    fun currentBleConfig(): BleRuntimeConfig

    suspend fun updateBleConfig(config: BleRuntimeConfig): HealthResult<Unit>

    suspend fun resetBleConfig(): HealthResult<Unit>

    suspend fun setPrivacyConsentGranted(granted: Boolean): HealthResult<Unit>
}
