package com.photosentinel.health.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.photosentinel.health.data.repository.ble.BleFrameDecoder
import com.photosentinel.health.data.repository.ble.FrameSegmentAssembler
import com.photosentinel.health.domain.model.BleRuntimeConfig
import com.photosentinel.health.domain.model.DeviceLinkState
import com.photosentinel.health.domain.model.HardwareFrame
import com.photosentinel.health.domain.model.HardwareStatusSnapshot
import com.photosentinel.health.domain.model.HealthResult
import com.photosentinel.health.domain.model.ValidationError
import com.photosentinel.health.domain.repository.HardwareInterfaceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class BleHardwareBridgeRepository(
    context: Context,
    private val configProvider: () -> BleRuntimeConfig = { BleRuntimeConfig() }
) : HardwareInterfaceRepository {
    private val appContext = context.applicationContext
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)

    private val mutableConnectionState = MutableStateFlow<DeviceLinkState>(
        DeviceLinkState.Disconnected("硬件未连接")
    )
    private val mutableRawFrames = MutableSharedFlow<HardwareFrame>(
        replay = 0,
        extraBufferCapacity = 128
    )
    private val mutableStatusSnapshots = MutableSharedFlow<HardwareStatusSnapshot>(
        replay = 1,
        extraBufferCapacity = 8
    )

    override val connectionState: StateFlow<DeviceLinkState> = mutableConnectionState.asStateFlow()
    override val rawFrames: Flow<HardwareFrame> = mutableRawFrames.asSharedFlow()
    override val statusSnapshots: Flow<HardwareStatusSnapshot> = mutableStatusSnapshots.asSharedFlow()

    private val segmentAssembler = FrameSegmentAssembler()

    @Volatile
    private var bluetoothGatt: BluetoothGatt? = null

    @Volatile
    private var dataCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var controlCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var pendingConnect: CompletableDeferred<HealthResult<Unit>>? = null

    @Volatile
    private var connectedDeviceName: String = ""

    @Volatile
    private var manualDisconnect = false

    @Volatile
    private var activeConfig: BleRuntimeConfig = configProvider().sanitized()
    private val controlWriteMutex = Mutex()

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceId: String?): HealthResult<Unit> = withContext(Dispatchers.IO) {
        if (mutableConnectionState.value is DeviceLinkState.Connected && bluetoothGatt != null) {
            return@withContext HealthResult.Success(Unit)
        }

        activeConfig = configProvider().sanitized()
        activeConfig.validatedOrError()?.let { error ->
            val failure = HealthResult.Failure(ValidationError(error.message))
            mutableConnectionState.value = DeviceLinkState.Disconnected(error.message)
            return@withContext failure
        }

        validateBlePermissions()?.let { error ->
            val failure = HealthResult.Failure(ValidationError(error))
            mutableConnectionState.value = DeviceLinkState.Disconnected(error)
            return@withContext failure
        }

        val adapter = bluetoothAdapter()
            ?: return@withContext HealthResult.Failure(ValidationError("设备不支持蓝牙")).also {
                mutableConnectionState.value = DeviceLinkState.Disconnected("设备不支持蓝牙")
            }
        if (!adapter.isEnabled) {
            val message = "蓝牙未开启，请先打开系统蓝牙"
            mutableConnectionState.value = DeviceLinkState.Disconnected(message)
            return@withContext HealthResult.Failure(ValidationError(message))
        }

        mutableConnectionState.value = DeviceLinkState.Scanning
        logBle(
            "开始扫描，目标 deviceId=${deviceId ?: "null"}，前缀=${activeConfig.preferredDeviceNamePrefix}，" +
                "serviceUuid=${activeConfig.serviceUuid}"
        )
        val targetDevice = resolveTargetDevice(adapter, deviceId)
            ?: run {
                val message = "未找到目标设备，请确认已上电并进入广播"
                mutableConnectionState.value = DeviceLinkState.Disconnected(message)
                logBle(message)
                return@withContext HealthResult.Failure(ValidationError(message))
            }

        logBle("扫描命中设备：${deviceLabel(targetDevice)}")
        connectedDeviceName = targetDevice.name ?: targetDevice.address
        mutableConnectionState.value = DeviceLinkState.Connecting(connectedDeviceName)
        manualDisconnect = false

        val result = connectGatt(targetDevice)
        when (result) {
            is HealthResult.Success -> mutableConnectionState.value = DeviceLinkState.Connected(connectedDeviceName)
            is HealthResult.Failure -> mutableConnectionState.value = DeviceLinkState.Disconnected(result.error.message)
        }
        result
    }

    override suspend fun disconnect(): HealthResult<Unit> = withContext(Dispatchers.IO) {
        manualDisconnect = true
        pendingConnect?.complete(HealthResult.Failure(ValidationError("连接已取消")))
        pendingConnect = null
        closeGatt("已断开连接")
        HealthResult.Success(Unit)
    }

    override suspend fun startStreaming(): HealthResult<Unit> = withContext(Dispatchers.IO) {
        if (mutableConnectionState.value !is DeviceLinkState.Connected) {
            return@withContext HealthResult.Failure(ValidationError("硬件未连接，无法开始采集"))
        }
        activeConfig = configProvider().sanitized()
        if (!pushRuntimeConfig()) {
            return@withContext HealthResult.Failure(ValidationError("运行参数下发失败，请检查 BLE 链路"))
        }
        if (!writeControlWithRetry(byteArrayOf(BLE_CONTROL_OP_START_STREAM), attempts = START_STREAM_ATTEMPTS)) {
            return@withContext HealthResult.Failure(ValidationError("启动采集命令发送失败"))
        }
        HealthResult.Success(Unit)
    }

    override suspend fun stopStreaming(): HealthResult<Unit> = withContext(Dispatchers.IO) {
        if (mutableConnectionState.value !is DeviceLinkState.Connected) {
            return@withContext HealthResult.Success(Unit)
        }
        if (!writeControlWithRetry(byteArrayOf(BLE_CONTROL_OP_STOP_STREAM))) {
            return@withContext HealthResult.Failure(ValidationError("停止采集命令发送失败"))
        }
        HealthResult.Success(Unit)
    }

    override suspend fun requestStatusSnapshot(): HealthResult<Unit> = withContext(Dispatchers.IO) {
        if (mutableConnectionState.value !is DeviceLinkState.Connected) {
            return@withContext HealthResult.Failure(ValidationError("硬件未连接，无法读取状态"))
        }
        if (!writeControlWithRetry(byteArrayOf(BLE_CONTROL_OP_GET_INFO))) {
            return@withContext HealthResult.Failure(ValidationError("GET_INFO 命令发送失败"))
        }
        HealthResult.Success(Unit)
    }

    override suspend fun triggerSelfTest(): HealthResult<Unit> = withContext(Dispatchers.IO) {
        if (mutableConnectionState.value !is DeviceLinkState.Connected) {
            return@withContext HealthResult.Failure(ValidationError("硬件未连接，无法触发自检"))
        }
        if (!writeControlWithRetry(byteArrayOf(BLE_CONTROL_OP_SELF_TEST))) {
            return@withContext HealthResult.Failure(ValidationError("SELF_TEST 命令发送失败"))
        }
        HealthResult.Success(Unit)
    }

    override suspend fun injectSyncMark(): HealthResult<Unit> = withContext(Dispatchers.IO) {
        if (mutableConnectionState.value !is DeviceLinkState.Connected) {
            return@withContext HealthResult.Failure(ValidationError("硬件未连接，无法注入同步标记"))
        }
        if (!writeControlWithRetry(byteArrayOf(BLE_CONTROL_OP_SYNC_MARK))) {
            return@withContext HealthResult.Failure(ValidationError("SYNC_MARK 命令发送失败"))
        }
        HealthResult.Success(Unit)
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        return bluetoothManager?.adapter
    }

    private fun validateBlePermissions(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)
            val connectGranted = isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
            if (!scanGranted || !connectGranted) {
                "缺少蓝牙权限，请在系统设置中授予“附近设备”权限"
            } else {
                null
            }
        } else {
            val locationGranted = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!locationGranted) {
                "缺少定位权限（Android 11 及以下 BLE 扫描需要）"
            } else {
                null
            }
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolveTargetDevice(
        adapter: BluetoothAdapter,
        deviceId: String?
    ): BluetoothDevice? {
        val target = deviceId?.trim()?.takeIf { it.isNotBlank() }
        if (target != null && target.contains(":")) {
            runCatching { adapter.getRemoteDevice(target) }.getOrNull()?.let { return it }
        }
        return scanForDevice(target)
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanForDevice(target: String?): BluetoothDevice? {
        val scanner: BluetoothLeScanner = bluetoothAdapter()?.bluetoothLeScanner ?: return null
        return suspendCancellableCoroutine { continuation ->
            val done = AtomicBoolean(false)
            var callback: ScanCallback? = null

            fun finish(device: BluetoothDevice?) {
                if (!done.compareAndSet(false, true)) {
                    return
                }
                callback?.let { runCatching { scanner.stopScan(it) } }
                logBle("扫描结束，结果=${deviceLabel(device)}")
                if (continuation.isActive) {
                    continuation.resume(device)
                }
            }

            val activeCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    if (matchesTargetScanResult(result, target)) {
                        logBle(
                            "扫描命中：callbackType=$callbackType, device=${deviceLabel(device)}, " +
                                "advName=${result.scanRecord?.deviceName.orEmpty()}"
                        )
                        finish(device)
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.firstOrNull { matchesTargetScanResult(it, target) }?.let { match ->
                        logBle(
                            "批量扫描命中：device=${deviceLabel(match.device)}, " +
                                "advName=${match.scanRecord?.deviceName.orEmpty()}"
                        )
                        finish(match.device)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    logBle("扫描失败 errorCode=$errorCode")
                    finish(null)
                }
            }
            callback = activeCallback

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            runCatching { scanner.startScan(null, settings, activeCallback) }
                .onFailure { finish(null) }

            val timeoutJob = repositoryScope.launch {
                delay(SCAN_TIMEOUT_MS)
                finish(null)
            }

            continuation.invokeOnCancellation {
                timeoutJob.cancel()
                runCatching { scanner.stopScan(activeCallback) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun matchesTargetScanResult(result: ScanResult, target: String?): Boolean {
        val device = result.device ?: return false
        val deviceName = device.name.orEmpty()
        val advertisedName = result.scanRecord?.deviceName.orEmpty()
        if (target.isNullOrBlank()) {
            val expectedPrefix = activeConfig.preferredDeviceNamePrefix.trim()
            val nameMatched = expectedPrefix.isNotBlank() &&
                (
                    deviceName.startsWith(expectedPrefix, ignoreCase = true) ||
                        advertisedName.startsWith(expectedPrefix, ignoreCase = true)
                    )
            if (nameMatched) {
                return true
            }

            // OEM ROMs may hide BluetoothDevice.name in scan callbacks; fall back to service UUID.
            val expectedServiceUuid = runCatching {
                UUID.fromString(activeConfig.serviceUuid.trim())
            }.getOrNull() ?: return false
            val serviceUuids = result.scanRecord?.serviceUuids.orEmpty()
            return serviceUuids.any { it.uuid == expectedServiceUuid }
        }

        if (device.address.equals(target, ignoreCase = true)) {
            return true
        }
        if (
            deviceName.contains(target, ignoreCase = true) ||
            advertisedName.contains(target, ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectGatt(device: BluetoothDevice): HealthResult<Unit> {
        closeGatt(null)
        segmentAssembler.clear()

        val deferred = CompletableDeferred<HealthResult<Unit>>()
        pendingConnect = deferred

        logBle("开始 connectGatt：${deviceLabel(device)}")
        val callback = object : BluetoothGattCallback() {
            private val discoveryStarted = AtomicBoolean(false)
            private val cachePollingStarted = AtomicBoolean(false)
            private val finalized = AtomicBoolean(false)
            private val postSubscribeStarted = AtomicBoolean(false)

            private fun completeFailure(message: String) {
                if (pendingConnect?.isCompleted == false) {
                    pendingConnect?.complete(HealthResult.Failure(ValidationError(message)))
                }
                closeGatt(message)
            }

            private fun tryFinalizeConnection(
                gatt: BluetoothGatt,
                source: String,
                strict: Boolean
            ): Boolean {
                if (pendingConnect?.isCompleted != false) {
                    return true
                }
                val bundle = locateCharacteristics(gatt)
                if (bundle == null) {
                    if (strict) {
                        completeFailure("未发现目标 BLE 特征，请检查固件版本")
                    } else {
                        logBle("轮询未发现目标特征（source=$source）")
                    }
                    return false
                }
                if (!finalized.compareAndSet(false, true)) {
                    return true
                }

                dataCharacteristic = bundle.data
                controlCharacteristic = bundle.control
                logBle(
                    "特征定位完成（source=$source），data=${bundle.data.uuid}, control=${bundle.control.uuid}, " +
                        "status=${bundle.status?.uuid}"
                )

                if (!enableNotifications(gatt, bundle.data)) {
                    completeFailure("数据通知订阅失败")
                    return false
                }

                if (bundle.status != null) {
                    enableNotifications(gatt, bundle.status)
                }
                logBle("通知订阅完成（source=$source）")
                schedulePostSubscribeCommands(source)
                return true
            }

            private fun schedulePostSubscribeCommands(source: String) {
                if (!postSubscribeStarted.compareAndSet(false, true)) {
                    return
                }
                repositoryScope.launch {
                    delay(NOTIFICATION_SETTLE_DELAY_MS)
                    if (pendingConnect?.isCompleted != false) {
                        return@launch
                    }
                    if (!pushRuntimeConfig()) {
                        completeFailure("运行参数下发失败，请检查固件控制通道")
                        return@launch
                    }
                    writeControlWithRetry(byteArrayOf(BLE_CONTROL_OP_GET_INFO))
                    logBle("已下发初始控制命令 GET_INFO（source=$source）")
                    if (pendingConnect?.isCompleted == false) {
                        pendingConnect?.complete(HealthResult.Success(Unit))
                    }
                }
            }

            private fun startServiceCachePolling(gatt: BluetoothGatt) {
                if (!cachePollingStarted.compareAndSet(false, true)) {
                    return
                }
                repositoryScope.launch {
                    repeat(SERVICE_CACHE_POLL_RETRY_COUNT) { index ->
                        delay(SERVICE_CACHE_POLL_INTERVAL_MS)
                        if (pendingConnect?.isCompleted != false) {
                            return@launch
                        }
                        val serviceCount = runCatching { gatt.services.size }.getOrDefault(0)
                        if (serviceCount <= 0) {
                            if (index == 0 || (index + 1) % 5 == 0 || index == SERVICE_CACHE_POLL_RETRY_COUNT - 1) {
                                logBle("服务缓存轮询中（attempt=${index + 1}）仍未拿到服务列表")
                            }
                            return@repeat
                        }
                        logBle(
                            "服务缓存轮询命中（attempt=${index + 1}, serviceCount=$serviceCount），尝试收敛连接"
                        )
                        if (tryFinalizeConnection(
                                gatt = gatt,
                                source = "service_cache_poll_${index + 1}",
                                strict = false
                            )
                        ) {
                            return@launch
                        }
                    }
                }
            }

            private fun startServiceDiscovery(gatt: BluetoothGatt, trigger: String) {
                if (!discoveryStarted.compareAndSet(false, true)) {
                    return
                }
                val started = runCatching { gatt.discoverServices() }
                    .onFailure { throwable ->
                        logBle("服务发现触发异常（trigger=$trigger, error=${throwable.message}）")
                    }
                    .getOrDefault(false)
                logBle("服务发现触发（trigger=$trigger, started=$started）")
                if (started) {
                    return
                }
                repositoryScope.launch {
                    delay(SERVICE_DISCOVERY_RETRY_DELAY_MS)
                    val retryStarted = runCatching { gatt.discoverServices() }
                        .onFailure { throwable ->
                            logBle("服务发现重试异常（trigger=$trigger, error=${throwable.message}）")
                        }
                        .getOrDefault(false)
                    logBle("服务发现重试（trigger=$trigger, started=$retryStarted）")
                    if (!retryStarted) {
                        if (pendingConnect?.isCompleted == false) {
                            pendingConnect?.complete(
                                HealthResult.Failure(ValidationError("服务发现启动失败，请重试"))
                            )
                        }
                        closeGatt("服务发现启动失败")
                    }
                }
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                logBle("连接状态变化 status=$status, newState=$newState")
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                        startServiceCachePolling(gatt)

                        val mtuRequested = runCatching { gatt.requestMtu(activeConfig.preferredMtu) }
                            .onFailure { throwable ->
                                logBle("请求 MTU 异常（mtu=${activeConfig.preferredMtu}, error=${throwable.message}）")
                            }
                            .getOrDefault(false)
                        logBle("请求 MTU=${activeConfig.preferredMtu}，结果=$mtuRequested")

                        if (!mtuRequested) {
                            startServiceDiscovery(gatt, trigger = "request_mtu_failed")
                        } else {
                            repositoryScope.launch {
                                delay(MTU_DISCOVERY_FALLBACK_DELAY_MS)
                                startServiceDiscovery(gatt, trigger = "mtu_callback_timeout")
                            }
                        }
                    }

                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        val message = if (manualDisconnect) {
                            "已断开连接"
                        } else {
                            "连接已断开（status=$status）"
                        }
                        completeFailure(message)
                    }

                    status != BluetoothGatt.GATT_SUCCESS -> {
                        completeFailure("连接失败（status=$status）")
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                logBle("MTU 回调 mtu=$mtu, status=$status")
                startServiceCachePolling(gatt)
                startServiceDiscovery(gatt, trigger = "mtu_changed")
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                logBle("服务发现回调 status=$status, serviceCount=${gatt.services.size}")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    completeFailure("服务发现失败（status=$status）")
                    return
                }
                tryFinalizeConnection(gatt = gatt, source = "onServicesDiscovered", strict = true)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                logBle("描述符写入回调 uuid=${descriptor.uuid}, status=$status")
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleCharacteristicChanged(characteristic.uuid, value)
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                handleCharacteristicChanged(
                    characteristic.uuid,
                    characteristic.value ?: byteArrayOf()
                )
            }
        }

        val gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            pendingConnect = null
            return HealthResult.Failure(ValidationError("GATT 连接创建失败"))
        }
        bluetoothGatt = gatt

        val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            deferred.await()
        } ?: HealthResult.Failure(ValidationError("连接超时，请确认设备距离与供电状态"))
        logBle("connectGatt 完成：$result")

        pendingConnect = null
        if (result is HealthResult.Failure) {
            closeGatt(result.error.message)
        }
        return result
    }

    private fun handleCharacteristicChanged(uuid: UUID, value: ByteArray) {
        val dataUuid = configuredUuidOrDefault(
            rawUuid = activeConfig.dataCharacteristicUuid,
            fallback = DEFAULT_DATA_UUID
        )
        val statusUuid = configuredUuidOrDefault(
            rawUuid = activeConfig.statusCharacteristicUuid,
            fallback = DEFAULT_STATUS_UUID
        )
        when (uuid) {
            dataUuid -> {
                val segment = BleFrameDecoder.parseSegmentPacket(value) ?: return
                val fullFrame = segmentAssembler.push(segment) ?: return
                val decoded = BleFrameDecoder.decodeFrame(fullFrame) ?: return
                mutableRawFrames.tryEmit(decoded)
            }

            statusUuid -> {
                parseStatusSnapshot(value)?.let { mutableStatusSnapshots.tryEmit(it) }
            }
        }
    }

    private fun parseStatusSnapshot(payload: ByteArray): HardwareStatusSnapshot? {
        if (payload.size != STATUS_PAYLOAD_BYTES) {
            return null
        }

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val protocolVersion = buffer.get().toInt() and 0xFF
        val streamingEnabled = (buffer.get().toInt() and 0xFF) != 0
        val stateFlags = buffer.short.toInt() and 0xFFFF

        fun readU32(): Long = buffer.int.toLong() and 0xFFFF_FFFFL

        return HardwareStatusSnapshot(
            protocolVersion = protocolVersion,
            streamingEnabled = streamingEnabled,
            stateFlags = stateFlags,
            selfTestPassBitmap = readU32(),
            selfTestFailBitmap = readU32(),
            ppgFifoOverflowCount = readU32(),
            ppgIntTimeoutCount = readU32(),
            bleBackpressureCount = readU32(),
            bleDroppedFrameCount = readU32(),
            i2cErrorCount = readU32(),
            adcSaturationCount = readU32(),
            ecgRingDropCount = readU32(),
            ppgRingDropCount = readU32(),
            generatedFrameCount = readU32(),
            transmittedFrameCount = readU32(),
            frameSequenceErrorCount = readU32(),
            ecgRingItems = buffer.short.toInt() and 0xFFFF,
            ppgRingItems = buffer.short.toInt() and 0xFFFF,
            bleQueueItems = buffer.short.toInt() and 0xFFFF,
            ecgRingHighWatermark = buffer.short.toInt() and 0xFFFF,
            ppgRingHighWatermark = buffer.short.toInt() and 0xFFFF,
            bleQueueHighWatermark = buffer.short.toInt() and 0xFFFF,
            mtu = buffer.short.toInt() and 0xFFFF,
            redLedPa = buffer.get().toInt() and 0xFF,
            irLedPa = buffer.get().toInt() and 0xFF,
            ppgPhaseUs = buffer.int,
            ppgLatencyUs = buffer.int,
            temperatureEnabled = (buffer.get().toInt() and 0xFF) != 0,
            logLevel = buffer.get().toInt() and 0xFF,
            sensorReady = (buffer.get().toInt() and 0xFF) != 0,
            fingerDetected = (buffer.get().toInt() and 0xFF) != 0
        )
    }

    private data class CharacteristicBundle(
        val data: BluetoothGattCharacteristic,
        val control: BluetoothGattCharacteristic,
        val status: BluetoothGattCharacteristic?
    )

    private fun locateCharacteristics(gatt: BluetoothGatt): CharacteristicBundle? {
        val service = gatt.getService(
            configuredUuidOrDefault(
                rawUuid = activeConfig.serviceUuid,
                fallback = DEFAULT_SERVICE_UUID
            )
        )
        if (service != null) {
            val data = service.getCharacteristic(
                configuredUuidOrDefault(
                    rawUuid = activeConfig.dataCharacteristicUuid,
                    fallback = DEFAULT_DATA_UUID
                )
            )
            val control = service.getCharacteristic(
                configuredUuidOrDefault(
                    rawUuid = activeConfig.controlCharacteristicUuid,
                    fallback = DEFAULT_CONTROL_UUID
                )
            )
            val status = service.getCharacteristic(
                configuredUuidOrDefault(
                    rawUuid = activeConfig.statusCharacteristicUuid,
                    fallback = DEFAULT_STATUS_UUID
                )
            )
            if (data != null && control != null) {
                return CharacteristicBundle(data = data, control = control, status = status)
            }
        }

        gatt.services.forEach { candidateService ->
            var data: BluetoothGattCharacteristic? = null
            var control: BluetoothGattCharacteristic? = null
            var status: BluetoothGattCharacteristic? = null

            candidateService.characteristics.forEach { characteristic ->
                val canRead = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
                val canWrite =
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                val canNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

                when {
                    canNotify && !canRead && !canWrite -> data = characteristic
                    canRead && canWrite -> control = characteristic
                    canNotify && canRead && !canWrite -> status = characteristic
                }
            }

            if (data != null && control != null) {
                return CharacteristicBundle(
                    data = data!!,
                    control = control!!,
                    status = status
                )
            }
        }
        return null
    }

    private fun configuredUuidOrDefault(rawUuid: String, fallback: UUID): UUID {
        return runCatching { UUID.fromString(rawUuid.trim()) }.getOrDefault(fallback)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            return false
        }
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun writeControlWithRetry(
        payload: ByteArray,
        attempts: Int = CONTROL_WRITE_ATTEMPTS
    ): Boolean {
        val normalizedAttempts = attempts.coerceAtLeast(1)
        return controlWriteMutex.withLock {
            repeat(normalizedAttempts) { index ->
                val attempt = index + 1
                if (writeControlSingle(payload, attempt, normalizedAttempts)) {
                    delay(CONTROL_WRITE_SPACING_MS)
                    return@withLock true
                }
                if (attempt < normalizedAttempts) {
                    delay(CONTROL_WRITE_RETRY_DELAY_MS)
                }
            }
            false
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun writeControlSingle(
        payload: ByteArray,
        attempt: Int,
        totalAttempts: Int
    ): Boolean {
        val gatt = bluetoothGatt ?: return false
        val characteristic = controlCharacteristic ?: return false
        val properties = characteristic.properties
        val supportsWriteNoResponse =
            properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val supportsWrite = properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        if (!supportsWriteNoResponse && !supportsWrite) {
            logBle("控制特征不支持写入，properties=$properties")
            return false
        }
        val opcode = payload.firstOrNull()?.toInt()?.and(0xFF) ?: -1
        val preferredWriteType = if (supportsWrite) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        fun performWrite(writeType: Int): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, payload, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = writeType
                characteristic.value = payload
                gatt.writeCharacteristic(characteristic)
            }
        }

        val firstAttempt = performWrite(preferredWriteType)
        logBle(
            "控制写入 opcode=0x${if (opcode >= 0) "%02X".format(opcode) else "--"} " +
                "type=$preferredWriteType result=$firstAttempt (attempt=$attempt/$totalAttempts)"
        )
        if (firstAttempt) {
            return true
        }
        if (preferredWriteType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT && supportsWriteNoResponse) {
            val fallbackAttempt = performWrite(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            logBle(
                "控制写入回退 opcode=0x${if (opcode >= 0) "%02X".format(opcode) else "--"} " +
                    "type=${BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE} result=$fallbackAttempt (attempt=$attempt/$totalAttempts)"
            )
            return fallbackAttempt
        }
        return false
    }

    private suspend fun pushRuntimeConfig(): Boolean {
        val config = activeConfig.sanitized()
        val commands = listOf(
            ByteBuffer.allocate(5)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(BLE_CONTROL_OP_SET_PPG_PHASE)
                .putInt(config.ppgPhaseUs)
                .array(),
            ByteBuffer.allocate(5)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(BLE_CONTROL_OP_SET_PPG_LATENCY)
                .putInt(config.ppgLatencyUs)
                .array()
        )
        val pushed = commands.withIndex().all { (index, command) ->
            val sent = writeControlWithRetry(command)
            logBle("运行参数命令#${index + 1} 下发=$sent")
            sent
        }
        logBle(
            "运行参数下发结果=$pushed（ppgPhaseUs=${config.ppgPhaseUs}, ppgLatencyUs=${config.ppgLatencyUs}）"
        )
        return pushed
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(disconnectedReason: String?) {
        segmentAssembler.clear()
        dataCharacteristic = null
        controlCharacteristic = null

        val gatt = bluetoothGatt
        bluetoothGatt = null

        if (gatt != null) {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }

        if (disconnectedReason != null) {
            logBle("关闭 GATT：$disconnectedReason")
            mutableConnectionState.value = DeviceLinkState.Disconnected(disconnectedReason)
        }
    }

    @SuppressLint("MissingPermission")
    private fun deviceLabel(device: BluetoothDevice?): String {
        if (device == null) {
            return "null"
        }
        val name = runCatching { device.name }.getOrNull().orEmpty().ifBlank { "unknown" }
        return "$name(${device.address})"
    }

    private fun logBle(message: String) {
        Log.i(LOG_TAG, message)
    }

    private companion object {
        private const val LOG_TAG = "BleBridge"
        private const val BLE_CONTROL_OP_START_STREAM: Byte = 0x01
        private const val BLE_CONTROL_OP_STOP_STREAM: Byte = 0x02
        private const val BLE_CONTROL_OP_SET_PPG_PHASE: Byte = 0x11
        private const val BLE_CONTROL_OP_SET_PPG_LATENCY: Byte = 0x12
        private const val BLE_CONTROL_OP_GET_INFO: Byte = 0x20
        private const val BLE_CONTROL_OP_SELF_TEST: Byte = 0x21
        private const val BLE_CONTROL_OP_SYNC_MARK: Byte = 0x22
        private const val SCAN_TIMEOUT_MS = 12_000L
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val MTU_DISCOVERY_FALLBACK_DELAY_MS = 2_500L
        private const val SERVICE_DISCOVERY_RETRY_DELAY_MS = 450L
        private const val SERVICE_CACHE_POLL_INTERVAL_MS = 300L
        private const val SERVICE_CACHE_POLL_RETRY_COUNT = 20
        private const val NOTIFICATION_SETTLE_DELAY_MS = 220L
        private const val CONTROL_WRITE_SPACING_MS = 40L
        private const val CONTROL_WRITE_RETRY_DELAY_MS = 80L
        private const val CONTROL_WRITE_ATTEMPTS = 3
        private const val START_STREAM_ATTEMPTS = 6
        private const val STATUS_PAYLOAD_BYTES = 84

        private val DEFAULT_SERVICE_UUID: UUID =
            UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
        private val DEFAULT_DATA_UUID: UUID =
            UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
        private val DEFAULT_CONTROL_UUID: UUID =
            UUID.fromString("12345678-1234-5678-1234-56789abcdef2")
        private val DEFAULT_STATUS_UUID: UUID =
            UUID.fromString("12345678-1234-5678-1234-56789abcdef3")
        private val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
