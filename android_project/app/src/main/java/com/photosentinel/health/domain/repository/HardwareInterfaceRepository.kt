package com.photosentinel.health.domain.repository

import com.photosentinel.health.domain.model.DeviceLinkState
import com.photosentinel.health.domain.model.HardwareFrame
import com.photosentinel.health.domain.model.HardwareStatusSnapshot
import com.photosentinel.health.domain.model.HealthResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface HardwareInterfaceRepository {
    val connectionState: StateFlow<DeviceLinkState>

    val rawFrames: Flow<HardwareFrame>

    val statusSnapshots: Flow<HardwareStatusSnapshot>

    suspend fun connect(deviceId: String? = null): HealthResult<Unit>

    suspend fun disconnect(): HealthResult<Unit>

    suspend fun startStreaming(): HealthResult<Unit>

    suspend fun stopStreaming(): HealthResult<Unit>

    suspend fun requestStatusSnapshot(): HealthResult<Unit>

    suspend fun triggerSelfTest(): HealthResult<Unit>

    suspend fun injectSyncMark(): HealthResult<Unit>
}
