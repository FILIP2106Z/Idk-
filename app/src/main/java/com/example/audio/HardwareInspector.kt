package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MicrophoneInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.AudioRouteState
import com.example.model.DspConfig
import com.example.model.EngineTelemetry
import com.example.model.HardwareSpecificationReport
import com.example.model.LoopbackMeasurement
import com.example.model.MicrophoneOption
import kotlin.math.roundToInt

class HardwareInspector(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private var a2dpProxy: BluetoothA2dp? = null
    private var isProxyRegistered = false

    private val a2dpListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP && proxy is BluetoothA2dp) {
                a2dpProxy = proxy
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = null
            }
        }
    }

    fun registerProfileProxy() {
        if (isProxyRegistered) return
        val adapter = bluetoothManager?.adapter ?: return
        runCatching {
            adapter.getProfileProxy(context, a2dpListener, BluetoothProfile.A2DP)
            isProxyRegistered = true
        }
    }

    fun unregisterProfileProxy() {
        if (!isProxyRegistered) return
        val adapter = bluetoothManager?.adapter ?: return
        a2dpProxy?.let {
            runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, it) }
        }
        a2dpProxy = null
        isProxyRegistered = false
    }

    fun enumerateMicrophones(): List<MicrophoneOption> {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        val physicalMics: List<MicrophoneInfo> = runCatching { audioManager.microphones }.getOrDefault(emptyList())

        val mapped = inputs.filter { isSelectableInputType(it.type) }.map { info ->
            val matchedMeta = physicalMics.firstOrNull { it.id == info.id }
                ?: physicalMics.firstOrNull { !info.address.isNullOrBlank() && it.address == info.address }

            val isBuiltIn = info.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
            val locationLabel = buildLocationLabel(info, matchedMeta)
            val directionLabel = mapDirectionality(matchedMeta?.directionality)
            val freqRange = matchedMeta?.frequencyResponse
                ?.takeIf { it.isNotEmpty() }
                ?.let { points ->
                    val minHz = points.minOf { it.first }
                    val maxHz = points.maxOf { it.first }
                    if (maxHz > minHz && maxHz > 0f) {
                        "${minHz.roundToInt()}–${maxHz.roundToInt()} Hz"
                    } else {
                        null
                    }
                }
            val friendlyName = when {
                isBuiltIn -> locationLabel
                info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                    "${info.productName ?: "Słuchawki BT"} (mikrofon BT — niezalecany)"
                else -> info.productName?.toString()?.takeIf { it.isNotBlank() } ?: locationLabel
            }

            MicrophoneOption(
                deviceId = info.id,
                labelPl = friendlyName,
                locationLabelPl = locationLabel,
                directionalityLabelPl = directionLabel,
                frequencyRangeHz = freqRange,
                sampleRatesHz = info.sampleRates?.toList()?.sorted() ?: emptyList(),
                isBuiltIn = isBuiltIn,
                address = info.address.takeIf { !it.isNullOrBlank() },
            )
        }.sortedWith(
            compareBy<MicrophoneOption> { if (it.isBuiltIn) 0 else if (it.labelPl.contains("BT")) 2 else 1 }
                .thenBy { it.deviceId },
        )

        val autoOption = MicrophoneOption(
            deviceId = 0,
            labelPl = "Automatyczny (główny mikrofon telefonu)",
            locationLabelPl = "Wybór automatyczny wbudowanego mikrofonu",
            directionalityLabelPl = "Domyślna dla modelu",
            isBuiltIn = true,
        )
        return listOf(autoOption) + mapped
    }

    @SuppressLint("MissingPermission")
    fun collectFullSpecification(
        routeState: AudioRouteState,
        telemetry: EngineTelemetry,
        loopback: LoopbackMeasurement,
        dspConfig: DspConfig,
        microphones: List<MicrophoneOption>,
    ): HardwareSpecificationReport {
        val pm = context.packageManager
        val adapter = bluetoothManager?.adapter
        val hasBtPerm = hasBluetoothPermission()

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it != Build.UNKNOWN && it.isNotBlank() } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }

        val nativeSr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        val nativeBurst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
        val unprocessedProp = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

        val leAudioLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && adapter != null) {
            when (adapter.isLeAudioSupported) {
                BluetoothStatusCodes.FEATURE_SUPPORTED -> "Wspierane (Android LE Audio)"
                BluetoothStatusCodes.FEATURE_NOT_SUPPORTED -> "Nie wspierane przez telefon"
                else -> "Brak informacji / wymaga włączonego BT"
            }
        } else {
            "Brak API LE Audio (wymaga Android 13+)"
        }

        val selectedOut = routeState.selectedDevice
        val a2dpCodec = inspectA2dpCodec(adapter, selectedOut?.address, hasBtPerm)

        val oboeSummary = if (telemetry.isRunning) {
            val inApi = if (telemetry.inputIsAAudio) "AAudio" else "OpenSL ES"
            val outApi = if (telemetry.outputIsAAudio) "AAudio" else "OpenSL ES"
            val inMode = if (telemetry.inputIsExclusive) "Exclusive" else "Shared"
            val outMode = if (telemetry.outputIsExclusive) "Exclusive" else "Shared"
            "Wejście: $inApi ($inMode, ${telemetry.inputSampleRate} Hz, burst ${telemetry.inputFramesPerBurst}) | " +
                "Wyjście: $outApi ($outMode, ${telemetry.outputSampleRate} Hz, burst ${telemetry.outputFramesPerBurst}, bufor ${telemetry.outputBufferSizeFrames}, Guard ${telemetry.autoGuardBufferMultiplier}×)"
        } else {
            "Silnik zatrzymany (uruchom transmisję, aby zobaczyć aktywne parametry Oboe)"
        }

        val selectedMicLabel = microphones.firstOrNull { it.deviceId == dspConfig.selectedMicDeviceId }?.labelPl
            ?: "Automatyczny (ID ${dspConfig.selectedMicDeviceId})"

        return HardwareSpecificationReport(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            deviceCodename = Build.DEVICE,
            hardwareBoard = Build.BOARD,
            socModel = socModel,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            hasLowLatencyAudioFeature = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY),
            hasProAudioFeature = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO),
            supportsUnprocessedAudioSource = unprocessedProp,
            halNativeOutputSampleRateHz = nativeSr,
            halNativeOutputFramesPerBurst = nativeBurst,
            microphones = microphones.filter { it.deviceId != 0 },
            bluetoothEnabled = adapter?.isEnabled == true,
            leAudioSupportedLabel = leAudioLabel,
            le2MPhySupported = adapter?.isLe2MPhySupported == true,
            leCodedPhySupported = adapter?.isLeCodedPhySupported == true,
            leExtendedAdvSupported = adapter?.isLeExtendedAdvertisingSupported == true,
            leMultiAdvSupported = adapter?.isMultipleAdvertisementSupported == true,
            connectedHeadsetName = selectedOut?.name,
            connectedHeadsetAddress = selectedOut?.address,
            connectedProfileLabel = selectedOut?.profileType?.labelPl,
            headsetBatteryPercent = selectedOut?.batteryPercent,
            negotiatedA2dpCodec = a2dpCodec.codecName,
            negotiatedA2dpSampleRate = a2dpCodec.sampleRate,
            negotiatedA2dpBitsPerSample = a2dpCodec.bitsPerSample,
            negotiatedA2dpChannelMode = a2dpCodec.channelMode,
            selectableA2dpCodecs = a2dpCodec.selectableCodecs,
            outputHalEncodings = selectedOut?.supportedEncodings ?: emptyList(),
            outputHalSampleRates = selectedOut?.supportedSampleRates ?: emptyList(),
            activeOboeSummary = oboeSummary,
            activeMicConfigSummary = "$selectedMicLabel · ${dspConfig.micInputPreset.labelPl} · ${dspConfig.latencyProfile.labelPl}",
            lastPhonePipelineLatencyMs = telemetry.estimatedPipelineLatencyMs,
            lastAcousticEndToEndMs = loopback.measuredEndToEndMs,
        )
    }

    private data class A2dpCodecInfo(
        val codecName: String? = null,
        val sampleRate: String? = null,
        val bitsPerSample: String? = null,
        val channelMode: String? = null,
        val selectableCodecs: List<String> = emptyList(),
    )

    @SuppressLint("MissingPermission")
    private fun inspectA2dpCodec(
        adapter: BluetoothAdapter?,
        targetAddress: String?,
        hasBtPerm: Boolean,
    ): A2dpCodecInfo {
        val proxy = a2dpProxy ?: return A2dpCodecInfo()
        if (!hasBtPerm || adapter == null) return A2dpCodecInfo()

        return runCatching {
            val connected = proxy.connectedDevices
            val device: BluetoothDevice? = connected.firstOrNull {
                targetAddress != null && it.address.equals(targetAddress, ignoreCase = true)
            } ?: connected.firstOrNull()

            if (device == null) return@runCatching A2dpCodecInfo()

            val getCodecStatus = proxy.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            val status = getCodecStatus.invoke(proxy, device) ?: return@runCatching A2dpCodecInfo()

            val codecConfig = status.javaClass.getMethod("getCodecConfig").invoke(status)
            val selectableList = (status.javaClass.getMethod("getCodecsSelectableCapabilities").invoke(status) as? List<*>)
                ?.mapNotNull { cap ->
                    cap?.let {
                        val type = it.javaClass.getMethod("getCodecType").invoke(it) as? Int
                        mapCodecTypeName(type)
                    }
                }
                ?.distinct()
                ?: emptyList()

            if (codecConfig == null) {
                return@runCatching A2dpCodecInfo(selectableCodecs = selectableList)
            }

            val codecType = codecConfig.javaClass.getMethod("getCodecType").invoke(codecConfig) as? Int
            val sampleRateMask = codecConfig.javaClass.getMethod("getSampleRate").invoke(codecConfig) as? Int ?: 0
            val bitsMask = codecConfig.javaClass.getMethod("getBitsPerSample").invoke(codecConfig) as? Int ?: 0
            val channelMask = codecConfig.javaClass.getMethod("getChannelMode").invoke(codecConfig) as? Int ?: 0

            A2dpCodecInfo(
                codecName = mapCodecTypeName(codecType),
                sampleRate = mapCodecSampleRate(sampleRateMask),
                bitsPerSample = mapCodecBits(bitsMask),
                channelMode = mapCodecChannelMode(channelMask),
                selectableCodecs = selectableList,
            )
        }.getOrDefault(A2dpCodecInfo())
    }

    private fun mapCodecTypeName(type: Int?): String? = when (type) {
        0 -> "SBC"
        1 -> "AAC"
        2 -> "aptX"
        3 -> "aptX HD"
        4 -> "LDAC"
        5 -> "LC3 / Opus"
        null -> null
        else -> "Kodek #$type"
    }

    private fun mapCodecSampleRate(mask: Int): String = when {
        mask and 0x20 != 0 -> "192 kHz"
        mask and 0x10 != 0 -> "176,4 kHz"
        mask and 0x08 != 0 -> "96 kHz"
        mask and 0x04 != 0 -> "88,2 kHz"
        mask and 0x02 != 0 -> "48 kHz"
        mask and 0x01 != 0 -> "44,1 kHz"
        else -> "Auto"
    }

    private fun mapCodecBits(mask: Int): String = when {
        mask and 0x04 != 0 -> "32-bit"
        mask and 0x02 != 0 -> "24-bit"
        mask and 0x01 != 0 -> "16-bit"
        else -> "Auto"
    }

    private fun mapCodecChannelMode(mask: Int): String = when {
        mask and 0x02 != 0 -> "Stereo"
        mask and 0x01 != 0 -> "Mono"
        else -> "Auto"
    }

    private fun isSelectableInputType(type: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            return true
        }
        return type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    }

    private fun buildLocationLabel(info: AudioDeviceInfo, meta: MicrophoneInfo?): String {
        return when (info.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> {
                if (meta == null) {
                    "Wbudowany mikrofon (#${info.id})"
                } else {
                    val pos = meta.position
                    val orient = meta.orientation
                    when {
                        meta.location == MicrophoneInfo.LOCATION_MAINBODY &&
                            pos != MicrophoneInfo.POSITION_UNKNOWN && pos.y < 0.05f ->
                            "Mikrofon dolny (główny, #${info.id})"
                        meta.location == MicrophoneInfo.LOCATION_MAINBODY &&
                            pos != MicrophoneInfo.POSITION_UNKNOWN && pos.y > 0.09f ->
                            "Mikrofon górny (pomocniczy, #${info.id})"
                        orient != MicrophoneInfo.ORIENTATION_UNKNOWN && orient.z < -0.5f ->
                            "Mikrofon tylny (kamera, #${info.id})"
                        else -> "Mikrofon wbudowany (#${info.id})"
                    }
                }
            }
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Mikrofon zestawu jack 3,5 mm"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "Mikrofon USB-C / zewnętrzny"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Mikrofon Bluetooth HFP"
            else -> "Wejście audio (#${info.id})"
        }
    }

    private fun mapDirectionality(dir: Int?): String = when (dir) {
        MicrophoneInfo.DIRECTIONALITY_OMNI -> "Dookólny (Omni)"
        MicrophoneInfo.DIRECTIONALITY_BI_DIRECTIONAL -> "Dwukierunkowy"
        MicrophoneInfo.DIRECTIONALITY_CARDIOID -> "Kardioidalny"
        MicrophoneInfo.DIRECTIONALITY_HYPER_CARDIOID -> "Hiperkardioidalny"
        MicrophoneInfo.DIRECTIONALITY_SUPER_CARDIOID -> "Superkardioidalny"
        else -> "Nieokreślona"
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

    companion object {
        fun mapEncodingNames(encodings: IntArray?): List<String> {
            if (encodings == null || encodings.isEmpty()) return emptyList()
            return encodings.toList().mapNotNull { enc ->
                when (enc) {
                    AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
                    AudioFormat.ENCODING_PCM_FLOAT -> "PCM Float32"
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit"
                    AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
                    else -> "Enc #$enc"
                }
            }.distinct()
        }
    }
}
