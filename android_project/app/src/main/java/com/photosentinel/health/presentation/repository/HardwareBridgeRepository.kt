package com.photosentinel.health.presentation.repository

import com.photosentinel.health.domain.model.DeviceLinkState
import com.photosentinel.health.domain.model.HardwareFrame
import com.photosentinel.health.domain.model.HardwareStatusSnapshot
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.repository.HardwareInterfaceRepository
import com.photosentinel.health.infrastructure.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class HardwareBridgeRepository(
    private val repository: HardwareInterfaceRepository = AppContainer.hardwareInterfaceRepository
) : HardwareBridgeDataSource {
    override val connectionState: StateFlow<DeviceLinkState> = repository.connectionState

    override val rawFrames: Flow<HardwareFrame> = repository.rawFrames

    override val statusSnapshots: Flow<HardwareStatusSnapshot> = repository.statusSnapshots

    override suspend fun connect(deviceId: String?): HealthResult<Unit> {
        return repository.connect(deviceId)
    }

    override suspend fun disconnect(): HealthResult<Unit> {
        return repository.disconnect()
    }

    override suspend fun startStreaming(): HealthResult<Unit> {
        return repository.startStreaming()
    }

    override suspend fun stopStreaming(): HealthResult<Unit> {
        return repository.stopStreaming()
    }

    override suspend fun requestStatusSnapshot(): HealthResult<Unit> {
        return repository.requestStatusSnapshot()
    }

    override suspend fun triggerSelfTest(): HealthResult<Unit> {
        return repository.triggerSelfTest()
    }

    override suspend fun injectSyncMark(): HealthResult<Unit> {
        return repository.injectSyncMark()
    }
}
