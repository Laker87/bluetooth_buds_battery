package com.example.btbattery.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAssignedNumbers
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.btbattery.core.AppPreferences
import com.example.btbattery.data.parser.tws.TwsBatteryParserRegistry
import com.example.btbattery.domain.model.BluetoothBatterySnapshot
import com.example.btbattery.domain.repository.BluetoothBatteryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

class BluetoothBatteryRepositoryImpl(
    private val context: Context,
    private val ioContext: CoroutineContext,
) : BluetoothBatteryRepository {
    private val appPreferences = AppPreferences(context)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val audioManager: AudioManager? =
        context.getSystemService(AudioManager::class.java)

    private val cache = LinkedHashMap<String, BluetoothBatterySnapshot>()

    @Volatile
    private var headsetProxy: BluetoothHeadset? = null

    @Volatile
    private var a2dpProxy: BluetoothA2dp? = null

    private val lastBleReadAttempt = LinkedHashMap<String, Long>()
    private val bleReadsInFlight = LinkedHashSet<String>()

    init {
        appPreferences.lastKnownSnapshot?.let { snapshot ->
            cache[snapshot.deviceAddress] = snapshot
        }
    }

    override fun observeBatteryUpdates(): Flow<BluetoothBatterySnapshot> = callbackFlow {
        if (!hasConnectPermission()) {
            close(IllegalStateException("BLUETOOTH_CONNECT permission is missing"))
            return@callbackFlow
        }

        fun deliver(snapshot: BluetoothBatterySnapshot) {
            trySend(snapshot)
            if (snapshot.isConnected && snapshot.primaryLevel == null && shouldAttemptBleRead(snapshot.deviceAddress)) {
                launch {
                    val device = runCatching {
                        bluetoothAdapter?.getRemoteDevice(snapshot.deviceAddress)
                    }.getOrNull() ?: return@launch
                    val bleLevel = readBleBatteryLevel(device)
                    markBleReadFinished(snapshot.deviceAddress)
                    if (bleLevel == null) {
                        Log.d(LOG_TAG, "source=ble_gatt device=${snapshot.deviceName} battery=null")
                        return@launch
                    }
                    val updated = snapshot.copy(batteryLevel = bleLevel)
                    cache[updated.deviceAddress] = updated
                    logSnapshot("ble_gatt", updated)
                    trySend(updated)
                }
            }
        }

        val serviceListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                when (profile) {
                    BluetoothProfile.HEADSET -> headsetProxy = proxy as? BluetoothHeadset
                    BluetoothProfile.A2DP -> a2dpProxy = proxy as? BluetoothA2dp
                }
                refreshConnectedDevicesInternal { deliver(it) }
            }

            override fun onServiceDisconnected(profile: Int) {
                when (profile) {
                    BluetoothProfile.HEADSET -> headsetProxy = null
                    BluetoothProfile.A2DP -> a2dpProxy = null
                }
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val device = intent.getBluetoothDeviceExtra()
                    ?: return
                val snapshot = parseSnapshotFromIntent(device, intent) ?: return
                cache[snapshot.deviceAddress] = snapshot
                deliver(snapshot)
            }
        }
        val vendorReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val device = intent.getBluetoothDeviceExtra()
                    ?: return
                val snapshot = parseSnapshotFromIntent(device, intent) ?: return
                cache[snapshot.deviceAddress] = snapshot
                deliver(snapshot)
            }
        }
        val audioCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                emitFromAudioDevices(addedDevices, isConnected = true) { deliver(it) }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                emitFromAudioDevices(removedDevices, isConnected = false) { deliver(it) }
            }
        }
        val bleScanner = bluetoothAdapter?.bluetoothLeScanner
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                parseAppleContinuityScan(result)?.let { deliver(it) }
                parseTwsAdvertisementScan(result)?.let { deliver(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    parseAppleContinuityScan(result)?.let { deliver(it) }
                    parseTwsAdvertisementScan(result)?.let { deliver(it) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(LOG_TAG, "source=ble_scan failed=$errorCode")
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HEADSET)
        bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.A2DP)
        val registeredVendorFilters = mutableListOf<IntentFilter>()
        VENDOR_COMPANY_IDS.forEach { companyId ->
            val vendorFilter = IntentFilter().apply {
                addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
                // Each filter must contain exactly one category. If multiple categories are put into
                // a single filter, Android requires all of them and broadcast won't match.
                addCategory("${BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY}.$companyId")
            }
            registeredVendorFilters += vendorFilter
            ContextCompat.registerReceiver(
                context,
                vendorReceiver,
                vendorFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        audioManager?.registerAudioDeviceCallback(audioCallback, null)
        runCatching {
            bleScanner?.startScan(BLE_SCAN_FILTERS, BLE_SCAN_SETTINGS, scanCallback)
            Log.d(LOG_TAG, "source=ble_scan started=true")
        }.onFailure {
            Log.w(LOG_TAG, "source=ble_scan started=false error=${it.javaClass.simpleName}")
        }

        refreshConnectedDevicesInternal { deliver(it) }
        emitFromAudioDevices(
            devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty(),
            isConnected = true,
        ) { deliver(it) }

        val pollingJob = launch {
            while (isActive) {
                refreshConnectedDevicesInternal { deliver(it) }
                emitFromAudioDevices(
                    devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty(),
                    isConnected = true,
                ) { deliver(it) }
                delay(5_000)
            }
        }

        awaitClose {
            pollingJob.cancel()
            runCatching { bleScanner?.stopScan(scanCallback) }
            audioManager?.unregisterAudioDeviceCallback(audioCallback)
            context.unregisterReceiver(receiver)
            if (registeredVendorFilters.isNotEmpty()) {
                context.unregisterReceiver(vendorReceiver)
            }
            headsetProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
            a2dpProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
            headsetProxy = null
            a2dpProxy = null
        }
    }

    override suspend fun refreshConnectedDevices() {
        withContext(ioContext) {
            refreshConnectedDevicesInternal()
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshConnectedDevicesInternal(onSnapshot: ((BluetoothBatterySnapshot) -> Unit)? = null) {
        if (!hasConnectPermission()) return
        val connectedFromProfiles = buildList {
            addAll(runCatching { headsetProxy?.connectedDevices.orEmpty() }.getOrElse { emptyList() })
            addAll(runCatching { a2dpProxy?.connectedDevices.orEmpty() }.getOrElse { emptyList() })
        }
        val bonded = bluetoothAdapter?.bondedDevices?.toList().orEmpty()
        val devices = (connectedFromProfiles + bonded).distinctBy { it.address }
        val connectedAddresses = LinkedHashSet<String>()

        devices.forEach { device ->
            if (!isDeviceConnected(device)) return@forEach
            connectedAddresses += device.address.orEmpty()
            val previous = cache[device.address]
            val split = mergeSplitForConnectedSource(
                previous = previous,
                isConnected = true,
                current = metadataSplitLevels(device),
            )
            val snapshot = buildSnapshot(
                device = device,
                isConnected = true,
                level = safeBatteryLevel(device) ?: previous?.batteryLevel,
                left = split.left,
                right = split.right,
                caseLevel = split.caseLevel,
            )
            cache[snapshot.deviceAddress] = snapshot
            logSnapshot("profile_proxy", snapshot)
            onSnapshot?.invoke(snapshot)
        }

        cache.values
            .asSequence()
            .filter { it.isConnected }
            .filterNot { it.deviceAddress.startsWith("ble:") }
            .filterNot { connectedAddresses.contains(it.deviceAddress) }
            .map { stale -> stale.copy(isConnected = false) }
            .toList()
            .forEach { disconnected ->
                cache[disconnected.deviceAddress] = disconnected
                logSnapshot("profile_proxy_disconnect", disconnected)
                onSnapshot?.invoke(disconnected)
            }
    }

    @SuppressLint("MissingPermission")
    private fun parseAppleContinuityScan(result: ScanResult): BluetoothBatterySnapshot? {
        val data = result.scanRecord?.getManufacturerSpecificData(AppleContinuityBatteryParser.APPLE_COMPANY_ID)
        val parsed = AppleContinuityBatteryParser.parse(data) ?: return null
        val matched = findBestKnownDeviceFor(parsed.modelName)
        val address = matched?.address ?: "ble:${result.device.address}"
        val previous = cache[address]
        val snapshot = BluetoothBatterySnapshot(
            deviceAddress = address,
            deviceName = matched?.let { runCatching { it.alias ?: it.name }.getOrNull() }
                ?: previous?.deviceName
                ?: parsed.modelName,
            batteryLevel = mergeCoarseContinuityLevel(parsed.batteryLevel, previous?.batteryLevel),
            leftLevel = mergeCoarseContinuityLevel(parsed.leftLevel, previous?.leftLevel),
            rightLevel = mergeCoarseContinuityLevel(parsed.rightLevel, previous?.rightLevel),
            caseLevel = mergeCoarseContinuityLevel(parsed.caseLevel, previous?.caseLevel),
            isConnected = matched?.let { isDeviceConnected(it) } ?: (previous?.isConnected ?: true),
        )
        cache[address] = snapshot
        Log.d(
            LOG_TAG,
            "source=apple_continuity model=${parsed.modelName} code=0x${parsed.modelCode.toString(16)} " +
                "rssi=${result.rssi} main=${snapshot.batteryLevel} left=${snapshot.leftLevel} " +
                "right=${snapshot.rightLevel} case=${snapshot.caseLevel}",
        )
        return snapshot
    }

    @SuppressLint("MissingPermission")
    private fun parseTwsAdvertisementScan(result: ScanResult): BluetoothBatterySnapshot? {
        val parsed = TwsBatteryParserRegistry.parse(result) ?: return null
        val split = SplitLevels(
            left = parsed.leftLevel,
            right = parsed.rightLevel,
            caseLevel = parsed.caseLevel,
        )

        val advertisedName = parsed.advertisedName
        val normalizedName = advertisedName
            ?.substringBefore("-GFP")
            ?.trim()
            .orEmpty()
        val matchedByName = normalizedName
            .takeIf { it.isNotBlank() }
            ?.let(::findBestKnownDeviceFor)
        val matched = matchedByName ?: findBestConnectedClassicDevice()

        val address = matched?.address ?: "blefe2c:${result.device.address}"
        val previous = cache[address]
        val snapshot = BluetoothBatterySnapshot(
            deviceAddress = address,
            deviceName = matched?.let { runCatching { it.alias ?: it.name }.getOrNull() }
                ?: previous?.deviceName
                ?: advertisedName
                ?: "Fast Pair device",
            batteryLevel = previous?.batteryLevel,
            // Do not restore split levels from previous snapshot here.
            // FE2C payload can explicitly signal unavailable components (e.g. case closed),
            // and falling back to previous values causes stale case/bud levels to stick.
            leftLevel = split.left,
            rightLevel = split.right,
            caseLevel = split.caseLevel,
            isConnected = matched?.let { isDeviceConnected(it) } ?: (previous?.isConnected ?: true),
        )
        cache[address] = snapshot
        Log.d(
            LOG_TAG,
            "source=tws_adv parser=${parsed.parserId} addr=${result.device.address} name=${snapshot.deviceName} " +
                "left=${snapshot.leftLevel} right=${snapshot.rightLevel} case=${snapshot.caseLevel} " +
                "payload=${parsed.rawPayloadHex}",
        )
        return snapshot
    }

    @SuppressLint("MissingPermission")
    private fun parseSnapshotFromIntent(
        device: BluetoothDevice,
        intent: Intent,
    ): BluetoothBatterySnapshot? {
        val action = intent.action.orEmpty()
        val isConnected = when (action) {
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_CONNECTION_STATE,
                    BluetoothAdapter.STATE_DISCONNECTED,
                )
                state == BluetoothAdapter.STATE_CONNECTED
            }
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                state == BluetoothProfile.STATE_CONNECTED
            }

            else -> true
        }

        val totalLevel = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1).takeIf { it in 0..100 }
            ?: safeBatteryLevel(device)
            ?: parseVendorSpecificBatteryLevel(intent)

        val splitLevels = extractSplitLevels(intent)
        val previous = cache[device.address]
        val split = mergeSplitForConnectedSource(
            previous = previous,
            isConnected = isConnected,
            current = splitLevels,
        )
        val snapshot = buildSnapshot(
            device = device,
            isConnected = isConnected,
            level = totalLevel ?: previous?.batteryLevel,
            left = split.left,
            right = split.right,
            caseLevel = split.caseLevel,
        )
        logSnapshot(intent.action.orEmpty(), snapshot)
        return snapshot
    }

    @SuppressLint("MissingPermission")
    private fun buildSnapshot(
        device: BluetoothDevice,
        isConnected: Boolean,
        level: Int?,
        left: Int?,
        right: Int?,
        caseLevel: Int?,
    ): BluetoothBatterySnapshot {
        val name = device.alias ?: device.name ?: "Unknown device"
        return BluetoothBatterySnapshot(
            deviceAddress = device.address.orEmpty(),
            deviceName = name,
            batteryLevel = level,
            leftLevel = left,
            rightLevel = right,
            caseLevel = caseLevel,
            isConnected = isConnected,
        )
    }

    private fun extractSplitLevels(intent: Intent): SplitLevels {
        fun fromKey(key: String): Int? = intent.getIntExtra(key, -1).takeIf { it in 0..100 }

        val bundle = intent.extras
        fun fromBundle(keyword: String): Int? {
            if (bundle == null) return null
            for (key in bundle.keySet()) {
                if (key.contains(keyword, ignoreCase = true)) {
                    val value = bundle.get(key)
                    if (value is Int && value in 0..100) return value
                    if (value is String) {
                        value.toIntOrNull()?.takeIf { it in 0..100 }?.let { return it }
                    }
                }
            }
            return null
        }

        return SplitLevels(
            left = fromKey(EXTRA_BATTERY_LEVEL_LEFT) ?: fromBundle("left"),
            right = fromKey(EXTRA_BATTERY_LEVEL_RIGHT) ?: fromBundle("right"),
            caseLevel = fromKey(EXTRA_BATTERY_LEVEL_CASE) ?: fromBundle("case"),
        )
    }

    private fun parseVendorSpecificBatteryLevel(intent: Intent): Int? {
        if (intent.action != BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT) return null

        val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD)
            .orEmpty()
            .uppercase()
        val args = extractVendorArgs(intent)

        if (args.isEmpty()) return null

        // Apple/Beats often report battery as +IPHONEACCEV with key=1 and value in [0..9].
        if (cmd.contains("IPHONEACCEV")) {
            val count = args.firstOrNull()?.toIntOrNull() ?: 0
            var index = 1
            repeat(count) {
                val key = args.getOrNull(index)?.toIntOrNull()
                val value = args.getOrNull(index + 1)?.toIntOrNull()
                if (key == 1 && value != null) {
                    if (value in 0..9) return value * 10
                    if (value in 0..100) return value
                }
                index += 2
            }
        }

        // Generic fallback for vendor events like +XEVENT / BATTERY.
        val numericValues = args.mapNotNull { token ->
            token.trim().trim('"').toIntOrNull()?.takeIf { it in 0..100 }
        }
        return numericValues.firstOrNull()
    }

    private fun extractVendorArgs(intent: Intent): List<String> {
        val key = BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS
        val raw = intent.extras?.get(key) ?: return emptyList()
        return when (raw) {
            is Array<*> -> raw.mapNotNull { it?.toString() }
            is IntArray -> raw.map { it.toString() }
            is Iterable<*> -> raw.mapNotNull { it?.toString() }
            else -> listOf(raw.toString())
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeBatteryLevel(device: BluetoothDevice): Int? {
        if (!hasConnectPermission()) return null
        return readIntNoArgMethod(device, "getBatteryLevel")
            ?: readBluetoothMetadata(device, "METADATA_MAIN_BATTERY")
            ?: readBluetoothMetadata(device, "METADATA_UNTETHERED_LEFT_BATTERY")
            ?: readBluetoothMetadata(device, "METADATA_UNTETHERED_RIGHT_BATTERY")
            ?: readHeadsetBattery(device)
    }

    private fun hasConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun emitFromAudioDevices(
        devices: Array<out AudioDeviceInfo>,
        isConnected: Boolean,
        onSnapshot: (BluetoothBatterySnapshot) -> Unit,
    ) {
        if (!hasConnectPermission()) return
        devices.asSequence()
            .filter { it.isBluetoothAudioOutput() }
            .mapNotNull { it.toBluetoothDeviceOrNull() }
            .distinctBy { it.address }
            .forEach { device ->
                val previous = cache[device.address]
                val split = mergeSplitForConnectedSource(
                    previous = previous,
                    isConnected = isConnected,
                    current = metadataSplitLevels(device),
                )
                val snapshot = buildSnapshot(
                    device = device,
                    isConnected = isConnected,
                    level = safeBatteryLevel(device) ?: previous?.batteryLevel,
                    left = split.left,
                    right = split.right,
                    caseLevel = split.caseLevel,
                )
                cache[snapshot.deviceAddress] = snapshot
                logSnapshot("audio_devices", snapshot)
                onSnapshot(snapshot)
            }
    }

    @SuppressLint("MissingPermission")
    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        val headsetConnected = runCatching {
            headsetProxy?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
        }.getOrDefault(false)
        val a2dpConnected = runCatching {
            a2dpProxy?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
        }.getOrDefault(false)
        val gattConnected = bluetoothManager?.let { manager ->
            runCatching {
                manager.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
            }.getOrDefault(false)
        } == true
        return headsetConnected || a2dpConnected || gattConnected
    }

    private data class SplitLevels(
        val left: Int?,
        val right: Int?,
        val caseLevel: Int?,
    )

    @Suppress("DEPRECATION")
    private fun Intent.getBluetoothDeviceExtra(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun AudioDeviceInfo.toBluetoothDeviceOrNull(): BluetoothDevice? {
        val address = runCatching { address }.getOrNull().orEmpty()
        val productName = productName?.toString().orEmpty()
        val bonded = bluetoothAdapter?.bondedDevices.orEmpty()
        return bonded.firstOrNull { it.address.equals(address, ignoreCase = true) }
            ?: bonded.firstOrNull { bondedDevice ->
                runCatching {
                    bondedDevice.alias == productName || bondedDevice.name == productName
                }.getOrDefault(false)
            }
            ?: address.takeIf { it.isNotBlank() }
                ?.let { runCatching { bluetoothAdapter?.getRemoteDevice(it) }.getOrNull() }
    }

    private fun AudioDeviceInfo.isBluetoothAudioOutput(): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> true

            else -> false
        }
    }

    @SuppressLint("MissingPermission")
    private fun findBestKnownDeviceFor(modelName: String): BluetoothDevice? {
        val normalizedModel = modelName.normalizeDeviceName()
        return cache.values
            .firstOrNull { it.deviceName.normalizeDeviceName() == normalizedModel }
            ?.let { snapshot -> runCatching { bluetoothAdapter?.getRemoteDevice(snapshot.deviceAddress) }.getOrNull() }
            ?: bluetoothAdapter?.bondedDevices.orEmpty().firstOrNull { device ->
                val name = runCatching { device.alias ?: device.name }.getOrNull().orEmpty()
                name.normalizeDeviceName() == normalizedModel
            }
            ?: bluetoothAdapter?.bondedDevices.orEmpty().firstOrNull { device ->
                val name = runCatching { device.alias ?: device.name }.getOrNull().orEmpty()
                name.normalizeDeviceName().contains(normalizedModel) || normalizedModel.contains(name.normalizeDeviceName())
            }
    }

    @SuppressLint("MissingPermission")
    private fun findBestConnectedClassicDevice(): BluetoothDevice? {
        val connected = cache.values
            .asSequence()
            .filter { it.isConnected }
            .filterNot { it.deviceAddress.startsWith("ble:") || it.deviceAddress.startsWith("blefe2c:") }
            .mapNotNull { snapshot ->
                runCatching { bluetoothAdapter?.getRemoteDevice(snapshot.deviceAddress) }.getOrNull()
            }
            .distinctBy { it.address }
            .toList()
        return if (connected.size == 1) connected.first() else null
    }

    private fun String.normalizeDeviceName(): String {
        return lowercase().filter { it.isLetterOrDigit() }
    }

    private fun mergeCoarseContinuityLevel(coarseLevel: Int?, previousLevel: Int?): Int? {
        if (coarseLevel == null) return previousLevel
        if (previousLevel == null) return coarseLevel

        // Public Apple continuity payload provides only coarse 10% buckets.
        // Keep previously known 1% level while it still belongs to the same bucket.
        val isCoarseBucket = coarseLevel % 10 == 0
        val isPreviousPrecise = previousLevel % 10 != 0
        val sameBucket = previousLevel / 10 == coarseLevel / 10
        if (isCoarseBucket && isPreviousPrecise && sameBucket) {
            return previousLevel
        }
        return coarseLevel
    }

    private fun mergeSplitForConnectedSource(
        previous: BluetoothBatterySnapshot?,
        isConnected: Boolean,
        current: SplitLevels,
    ): SplitLevels {
        if (!isConnected) return current
        val hasCurrentSplit = current.left != null || current.right != null || current.caseLevel != null
        if (hasCurrentSplit) return current
        return SplitLevels(
            left = previous?.leftLevel,
            right = previous?.rightLevel,
            caseLevel = previous?.caseLevel,
        )
    }

    private fun logSnapshot(source: String, snapshot: BluetoothBatterySnapshot) {
        val device = runCatching { bluetoothAdapter?.getRemoteDevice(snapshot.deviceAddress) }.getOrNull()
        val headset = device?.let { runCatching { headsetProxy?.getConnectionState(it) }.getOrNull() }
        val a2dp = device?.let { runCatching { a2dpProxy?.getConnectionState(it) }.getOrNull() }
        val gatt = device?.let {
            bluetoothManager?.let { manager ->
                runCatching { manager.getConnectionState(it, BluetoothProfile.GATT) }.getOrNull()
            }
        }
        val metadataMain = device?.let { readBluetoothMetadata(it, "METADATA_MAIN_BATTERY") }
        Log.d(
            LOG_TAG,
            "source=$source device=${snapshot.deviceName} connected=${snapshot.isConnected} " +
                "main=${snapshot.batteryLevel} left=${snapshot.leftLevel} right=${snapshot.rightLevel} case=${snapshot.caseLevel} " +
                "headset=$headset a2dp=$a2dp gatt=$gatt metadataMain=$metadataMain",
        )
    }

    private fun metadataSplitLevels(device: BluetoothDevice): SplitLevels {
        return SplitLevels(
            left = readBluetoothMetadata(device, "METADATA_UNTETHERED_LEFT_BATTERY"),
            right = readBluetoothMetadata(device, "METADATA_UNTETHERED_RIGHT_BATTERY"),
            caseLevel = readBluetoothMetadata(device, "METADATA_UNTETHERED_CASE_BATTERY"),
        )
    }

    private fun readHeadsetBattery(device: BluetoothDevice): Int? {
        val proxy = headsetProxy ?: return null
        return readIntDeviceMethod(proxy, device, "getBatteryLevel")
            ?: readIntDeviceMethod(proxy, device, "getBatteryUsageHint")
    }

    private fun readIntNoArgMethod(target: Any, methodName: String): Int? {
        return runCatching {
            val method = target.javaClass.getMethod(methodName)
            method.invoke(target) as? Int
        }.getOrNull()?.takeIf { it in 0..100 }
    }

    private fun readIntDeviceMethod(target: Any, device: BluetoothDevice, methodName: String): Int? {
        return runCatching {
            val method = target.javaClass.getMethod(methodName, BluetoothDevice::class.java)
            method.invoke(target, device) as? Int
        }.getOrNull()?.takeIf { it in 0..100 }
    }

    private fun readBluetoothMetadata(device: BluetoothDevice, fieldName: String): Int? {
        return runCatching {
            val field = BluetoothDevice::class.java.getField(fieldName)
            val metadataKey = field.getInt(null)
            val method = BluetoothDevice::class.java.getMethod("getMetadata", Int::class.javaPrimitiveType)
            val bytes = method.invoke(device, metadataKey) as? ByteArray
            bytes?.toString(Charset.forName("UTF-8"))?.trim()?.toIntOrNull()
        }.getOrNull()?.takeIf { it in 0..100 }
    }

    private fun shouldAttemptBleRead(address: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(bleReadsInFlight) {
            if (bleReadsInFlight.contains(address)) return false
            val lastAttempt = lastBleReadAttempt[address] ?: 0L
            if (now - lastAttempt < BLE_READ_THROTTLE_MS) return false
            bleReadsInFlight += address
            lastBleReadAttempt[address] = now
            return true
        }
    }

    private fun markBleReadFinished(address: String) {
        synchronized(bleReadsInFlight) {
            bleReadsInFlight.remove(address)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun readBleBatteryLevel(device: BluetoothDevice): Int? {
        if (!hasConnectPermission()) return null
        return withTimeoutOrNull(BLE_READ_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                var gatt: BluetoothGatt? = null
                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            continuation.resumeIfActive(null)
                            closeGatt(gatt)
                            return
                        }
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            gatt.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            continuation.resumeIfActive(null)
                            closeGatt(gatt)
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            continuation.resumeIfActive(null)
                            closeGatt(gatt)
                            return
                        }
                        val characteristic = gatt
                            .getService(BATTERY_SERVICE_UUID)
                            ?.getCharacteristic(BATTERY_LEVEL_UUID)
                        if (characteristic == null) {
                            continuation.resumeIfActive(null)
                            closeGatt(gatt)
                            return
                        }
                        @Suppress("DEPRECATION")
                        val started = gatt.readCharacteristic(characteristic)
                        if (!started) {
                            continuation.resumeIfActive(null)
                            closeGatt(gatt)
                        }
                    }

                    @Deprecated("Deprecated by Android 13, still needed for older callback shape.")
                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int,
                    ) {
                        val level = if (status == BluetoothGatt.GATT_SUCCESS) {
                            @Suppress("DEPRECATION")
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                        } else {
                            null
                        }
                        continuation.resumeIfActive(level?.takeIf { it in 0..100 })
                        closeGatt(gatt)
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int,
                    ) {
                        val level = if (status == BluetoothGatt.GATT_SUCCESS) {
                            value.firstOrNull()?.toInt()?.and(0xFF)
                        } else {
                            null
                        }
                        continuation.resumeIfActive(level?.takeIf { it in 0..100 })
                        closeGatt(gatt)
                    }
                }

                continuation.invokeOnCancellation {
                    gatt?.let { closeGatt(it) }
                }

                gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    @Suppress("DEPRECATION")
                    device.connectGatt(context, false, callback)
                }
            }
        }
    }

    private fun kotlinx.coroutines.CancellableContinuation<Int?>.resumeIfActive(value: Int?) {
        if (isActive) resume(value)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt) {
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    companion object {
        private const val LOG_TAG = "BtBatteryRepo"
        private const val BLE_READ_THROTTLE_MS = 30_000L
        private const val BLE_READ_TIMEOUT_MS = 8_000L
        private val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        private val BLE_SCAN_FILTERS = listOf(
            ScanFilter.Builder()
                .setManufacturerData(
                    AppleContinuityBatteryParser.APPLE_COMPANY_ID,
                    byteArrayOf(0x07, 0x19),
                    byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                )
                .build(),
        ) + TwsBatteryParserRegistry.scanFilters
        private val BLE_SCAN_SETTINGS = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()
        private val VENDOR_COMPANY_IDS = intArrayOf(
            BluetoothAssignedNumbers.APPLE,
            BluetoothAssignedNumbers.BEATS_ELECTRONICS,
        )
        private const val ACTION_BATTERY_LEVEL_CHANGED =
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        private const val EXTRA_BATTERY_LEVEL_LEFT = "android.bluetooth.device.extra.BATTERY_LEVEL_LEFT"
        private const val EXTRA_BATTERY_LEVEL_RIGHT = "android.bluetooth.device.extra.BATTERY_LEVEL_RIGHT"
        private const val EXTRA_BATTERY_LEVEL_CASE = "android.bluetooth.device.extra.BATTERY_LEVEL_CASE"
    }
}
