package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.model.AudioOutputDevice
import com.example.model.AudioRouteState
import com.example.model.BluetoothProfileType
import com.example.model.CompatibilityEvaluator
import com.example.model.CompatibilityRating
import com.example.model.EngineTelemetry
import com.example.model.LoopbackMeasurement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

class BluetoothRouteManager(
    private val context: Context,
    private val onDeviceLostDuringTransmission: () -> Unit,
    private val onDeviceRestoredDuringTransmission: (AudioOutputDevice) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private val _routeState = MutableStateFlow(AudioRouteState())
    val routeState: StateFlow<AudioRouteState> = _routeState.asStateFlow()

    private val batteryMap = mutableMapOf<String, Int>()
    private var preferredDeviceName: String? = null
    private var preferredDeviceAddress: String? = null
    private var manualSelectedDeviceId: Int? = null
    private var isTransmissionActive: Boolean = false
    private var isRegistered: Boolean = false

    private var lastTelemetry = EngineTelemetry()
    private var lastLoopback = LoopbackMeasurement()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshDevices(fromReconnectEvent = true)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            val selectedId = _routeState.value.selectedDevice?.id
            val wasSelectedRemoved = selectedId != null && removedDevices.any { it.id == selectedId }
            refreshDevices(fromDisconnectEvent = wasSelectedRemoved)
        }
    }

    private var commDeviceListener: Any? = null

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    refreshDevices()
                }
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && level in 0..100) {
                        batteryMap[device.address] = level
                        refreshDevices()
                    }
                }
            }
        }
    }

    fun startMonitoring() {
        if (isRegistered) return
        isRegistered = true
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = AudioManager.OnCommunicationDeviceChangedListener {
                refreshDevices()
            }
            commDeviceListener = listener
            audioManager.addOnCommunicationDeviceChangedListener(callbackExecutor, listener)
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(btReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(btReceiver, filter)
        }

        refreshDevices()
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        isRegistered = false
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (commDeviceListener as? AudioManager.OnCommunicationDeviceChangedListener)?.let {
                runCatching { audioManager.removeOnCommunicationDeviceChangedListener(it) }
            }
            commDeviceListener = null
        }
        runCatching { context.unregisterReceiver(btReceiver) }
    }

    fun setPreferredDeviceHint(name: String?, address: String?) {
        preferredDeviceName = name
        preferredDeviceAddress = address
        refreshDevices()
    }

    fun setTransmissionState(active: Boolean) {
        isTransmissionActive = active
        if (!active) {
            clearCommunicationRouting()
        } else {
            _routeState.value.selectedDevice?.let { applyRoutingToDevice(it) }
        }
    }

    fun updateEvaluationContext(telemetry: EngineTelemetry, loopback: LoopbackMeasurement) {
        lastTelemetry = telemetry
        lastLoopback = loopback
        val current = _routeState.value
        val (rating, detail) = CompatibilityEvaluator.evaluate(
            device = current.selectedDevice,
            telemetry = telemetry,
            loopback = loopback,
        )
        if (rating != current.compatibilityRating || detail != current.compatibilityDetailPl) {
            _routeState.value = current.copy(
                compatibilityRating = rating,
                compatibilityDetailPl = detail,
            )
        }
    }

    fun selectDevice(device: AudioOutputDevice) {
        manualSelectedDeviceId = device.id
        preferredDeviceName = device.name
        preferredDeviceAddress = device.address
        if (isTransmissionActive) {
            applyRoutingToDevice(device)
        }
        refreshDevices()
    }

    /**
     * Finds the smartphone's built-in microphone so we capture the therapist's voice from
     * the phone rather than switching to the patient's remote Bluetooth microphone.
     */
    fun findBuiltInMicDeviceId(): Int {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val builtIn = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        return builtIn?.id ?: 0
    }

    fun applyRoutingToDevice(device: AudioOutputDevice): Boolean {
        // Never allow accidental speaker routing
        if (device.profileType == BluetoothProfileType.A2DP) {
            clearCommunicationRouting()
            audioManager.mode = AudioManager.MODE_NORMAL
            return true
        }

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val commDevices = audioManager.availableCommunicationDevices
            val target = commDevices.firstOrNull { it.id == device.id }
                ?: commDevices.firstOrNull {
                    it.productName?.toString() == device.name &&
                        mapProfileType(it) == device.profileType
                }
            if (target != null) {
                return audioManager.setCommunicationDevice(target)
            }
        } else if (device.profileType == BluetoothProfileType.HFP_SCO) {
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true
            return true
        }
        return false
    }

    fun clearCommunicationRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                if (audioManager.isBluetoothScoOn) {
                    audioManager.isBluetoothScoOn = false
                    audioManager.stopBluetoothSco()
                }
            }
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    fun refreshDevices(
        fromReconnectEvent: Boolean = false,
        fromDisconnectEvent: Boolean = false,
    ) {
        val btAdapter = bluetoothManager?.adapter
        val btEnabled = btAdapter?.isEnabled ?: false
        val hasBtPerm = hasBluetoothPermission()

        val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val commDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            emptyList()
        }

        // Filter out internal phone speaker / earpiece so audio never leaks to loudspeaker
        val rawCandidates = (allOutputs + commDevices)
            .filter { isPatientSafeOutputDevice(it) }
            .distinctBy { "${it.id}_${it.type}_${it.address}" }

        // Group same physical Bluetooth device across multiple profiles, preferring LE Audio > HFP > Wired > A2DP
        val mappedDevices = rawCandidates.map { info ->
            val profile = mapProfileType(info)
            val addr = info.address.takeIf { !it.isNullOrBlank() }
            val battery = addr?.let { readDeviceBattery(it, btAdapter, hasBtPerm) }
            val devName = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: profile.labelPl
            AudioOutputDevice(
                id = info.id,
                name = devName,
                address = addr,
                profileType = profile,
                isCommunicationDevice = commDevices.any { it.id == info.id } ||
                    profile == BluetoothProfileType.LE_AUDIO ||
                    profile == BluetoothProfileType.HFP_SCO,
                batteryPercent = battery,
                isConnected = true,
            )
        }.sortedWith(
            compareBy<AudioOutputDevice> { profilePriority(it.profileType) }
                .thenBy { it.name }
        )

        val previousSelected = _routeState.value.selectedDevice
        val selected: AudioOutputDevice? = selectBestMatchingDevice(mappedDevices)

        val activeRouteLabel = when {
            selected == null -> "Brak kompatybilnych słuchawek"
            else -> "${selected.name} (${selected.profileType.labelPl})"
        }

        val (rating, detail) = CompatibilityEvaluator.evaluate(
            device = selected,
            telemetry = lastTelemetry,
            loopback = lastLoopback,
        )

        val isRestoring = isTransmissionActive && selected == null
        _routeState.value = AudioRouteState(
            availableDevices = mappedDevices,
            selectedDevice = selected,
            activeRouteLabelPl = activeRouteLabel,
            compatibilityRating = rating,
            compatibilityDetailPl = detail,
            isA2dpFallbackWarning = selected?.profileType == BluetoothProfileType.A2DP,
            isSpeakerBlocked = true,
            isBluetoothEnabled = btEnabled,
            isRestoringConnection = isRestoring,
            lastPreferredDeviceName = preferredDeviceName,
        )

        if (isTransmissionActive) {
            if ((fromDisconnectEvent || selected == null) && previousSelected != null && selected == null) {
                onDeviceLostDuringTransmission()
            } else if (fromReconnectEvent && previousSelected == null && selected != null) {
                applyRoutingToDevice(selected)
                onDeviceRestoredDuringTransmission(selected)
            }
        }
    }

    private fun selectBestMatchingDevice(devices: List<AudioOutputDevice>): AudioOutputDevice? {
        if (devices.isEmpty()) return null

        // 1. Exact ID match if user manually selected a specific profile/device
        manualSelectedDeviceId?.let { id ->
            devices.firstOrNull { it.id == id }?.let { return it }
        }
        // 2. Reconnect match by preferred address (choosing highest priority profile first)
        preferredDeviceAddress?.let { addr ->
            devices.firstOrNull { it.address == addr }?.let { return it }
        }
        // 3. Reconnect match by preferred device name
        preferredDeviceName?.let { name ->
            devices.firstOrNull { it.name == name }?.let { return it }
        }
        // 4. Highest priority available profile (LE Audio > HFP > Wired > A2DP)
        return devices.firstOrNull()
    }

    private fun isPatientSafeOutputDevice(info: AudioDeviceInfo): Boolean {
        val t = info.type
        if (t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
            t == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
            t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE ||
            t == AudioDeviceInfo.TYPE_TELEPHONY
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && t == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && t == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
            return true
        }
        return t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            t == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            t == AudioDeviceInfo.TYPE_USB_HEADSET ||
            t == AudioDeviceInfo.TYPE_HEARING_AID
    }

    private fun mapProfileType(info: AudioDeviceInfo): BluetoothProfileType {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        ) {
            return BluetoothProfileType.LE_AUDIO
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        ) {
            return BluetoothProfileType.LE_AUDIO
        }
        return when (info.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> BluetoothProfileType.HFP_SCO
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> BluetoothProfileType.A2DP
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET -> BluetoothProfileType.WIRED_HEADSET
            AudioDeviceInfo.TYPE_HEARING_AID -> BluetoothProfileType.LE_AUDIO
            else -> BluetoothProfileType.UNKNOWN
        }
    }

    private fun profilePriority(profile: BluetoothProfileType): Int = when (profile) {
        BluetoothProfileType.LE_AUDIO -> 0
        BluetoothProfileType.HFP_SCO -> 1
        BluetoothProfileType.WIRED_HEADSET -> 2
        BluetoothProfileType.A2DP -> 3
        BluetoothProfileType.UNKNOWN -> 4
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun readDeviceBattery(
        address: String,
        adapter: BluetoothAdapter?,
        hasPermission: Boolean,
    ): Int? {
        batteryMap[address]?.let { return it }
        if (!hasPermission || adapter == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            return null
        }
        return runCatching {
            val remote = adapter.getRemoteDevice(address)
            val method = remote.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(remote) as? Int ?: -1
            if (level in 0..100) {
                batteryMap[address] = level
                level
            } else {
                null
            }
        }.getOrNull()
    }
}
