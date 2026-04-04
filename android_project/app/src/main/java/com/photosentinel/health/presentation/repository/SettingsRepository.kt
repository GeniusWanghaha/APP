package com.photosentinel.health.presentation.repository

import com.photosentinel.health.domain.model.BleRuntimeConfig
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.repository.UserSettingsRepository
import com.photosentinel.health.infrastructure.di.AppContainer
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(
    private val repository: UserSettingsRepository = AppContainer.userSettingsRepository
) : SettingsDataSource {
    override val bleConfig: StateFlow<BleRuntimeConfig> = repository.bleConfig

    override val privacyConsentGranted: StateFlow<Boolean> = repository.privacyConsentGranted

    override fun currentBleConfig(): BleRuntimeConfig {
        return repository.currentBleConfig()
    }

    override suspend fun updateBleConfig(config: BleRuntimeConfig): HealthResult<Unit> {
        return repository.updateBleConfig(config)
    }

    override suspend fun resetBleConfig(): HealthResult<Unit> {
        return repository.resetBleConfig()
    }

    override suspend fun setPrivacyConsentGranted(granted: Boolean): HealthResult<Unit> {
        return repository.setPrivacyConsentGranted(granted)
    }
}
