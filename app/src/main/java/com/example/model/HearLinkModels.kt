package com.example.model

import java.util.Locale

enum class AudioPreset(
    val displayNamePl: String,
    val descriptionPl: String,
) {
    STANDARD(
        displayNamePl = "Standardowy",
        descriptionPl = "Zrównoważona mowa do codziennej rozmowy w gabinecie",
    ),
    SPEECH_CLARITY(
        displayNamePl = "Wyraźna mowa",
        descriptionPl = "Wzmocnione spółgłoski i redukcja tła przy szumie otoczenia",
    ),
    QUIET_ROOM(
        displayNamePl = "Cichy pokój",
        descriptionPl = "Minimalna ingerencja DSP i bardzo naturalne brzmienie",
    ),
}

enum class ThemeMode(val displayNamePl: String) {
    SYSTEM("Systemowy"),
    LIGHT("Jasny"),
    DARK("Ciemny"),
}

enum class LatencyProfile(
    val labelPl: String,
    val descriptionPl: String,
    val isUltraLow: Boolean,
) {
    ULTRA_LOW(
        labelPl = "Ultra-Niski (cel 10–20 ms)",
        descriptionPl = "Bufor 1× burst, zero-lag sync oraz Auto-XRun Guard",
        isUltraLow = true,
    ),
    BALANCED(
        labelPl = "Zrównoważony (25–40 ms)",
        descriptionPl = "Większy zapas bufora przy zakłóceniach Bluetooth",
        isUltraLow = false,
    ),
}

enum class MicInputPreset(
    val labelPl: String,
    val descriptionPl: String,
    val nativeCode: Int,
) {
    UNPROCESSED(
        labelPl = "Unprocessed (Najniższe opóźnienie)",
        descriptionPl = "Omija filtry HAL producenta (zalecane dla 10–20 ms)",
        nativeCode = 0,
    ),
    GENERIC(
        labelPl = "Generic (Surowe zgodne)",
        descriptionPl = "Neutralna ścieżka wejścia dla każdego smartfona",
        nativeCode = 3,
    ),
    VOICE_RECOGNITION(
        labelPl = "Voice Recognition",
        descriptionPl = "Lekka ścieżka mowy (może dodać ~15–30 ms na części OEM)",
        nativeCode = 1,
    ),
    VOICE_COMMUNICATION(
        labelPl = "Voice Communication (AEC/NS)",
        descriptionPl = "Sprzętowa redukcja echa i szumów telefonu",
        nativeCode = 2,
    ),
}

enum class OutputUsagePreference(
    val labelPl: String,
    val nativeUsageCode: Int,
) {
    GAME_LOW_LATENCY("Game Low-Latency (zalecane dla A2DP)", 1),
    MEDIA_STANDARD("Media (standardowe A2DP)", 2),
}

data class MicrophoneOption(
    val deviceId: Int,
    val labelPl: String,
    val locationLabelPl: String,
    val directionalityLabelPl: String,
    val frequencyRangeHz: String? = null,
    val sampleRatesHz: List<Int> = emptyList(),
    val isBuiltIn: Boolean = true,
    val address: String? = null,
)

data class DspConfig(
    val preset: AudioPreset = AudioPreset.STANDARD,
    val latencyProfile: LatencyProfile = LatencyProfile.ULTRA_LOW,
    val micInputPreset: MicInputPreset = MicInputPreset.UNPROCESSED,
    val selectedMicDeviceId: Int = 0,
    val a2dpUsagePreference: OutputUsagePreference = OutputUsagePreference.GAME_LOW_LATENCY,
    val micSensitivityDb: Float = 4f,
    val outputVolumeGain: Float = 0.80f,
    val maxOutputLimitDb: Float = -3f,
    val softStartRampMs: Float = 800f,
    val highPassEnabled: Boolean = true,
    val highPassCutoffHz: Float = 90f,
    val noiseSuppressionEnabled: Boolean = true,
    val noiseSuppressionStrength: Float = 0.45f,
    val speechClarityEnabled: Boolean = true,
    val speechClarityStrength: Float = 0.50f,
    val agcEnabled: Boolean = true,
    val agcTargetDb: Float = -18f,
) {
    companion object {
        fun forPreset(preset: AudioPreset, current: DspConfig = DspConfig()): DspConfig {
            return when (preset) {
                AudioPreset.STANDARD ->
                    current.copy(
                        preset = AudioPreset.STANDARD,
                        highPassEnabled = true,
                        highPassCutoffHz = 90f,
                        noiseSuppressionEnabled = true,
                        noiseSuppressionStrength = 0.45f,
                        speechClarityEnabled = true,
                        speechClarityStrength = 0.50f,
                        agcEnabled = true,
                        agcTargetDb = -18f,
                    )
                AudioPreset.SPEECH_CLARITY ->
                    current.copy(
                        preset = AudioPreset.SPEECH_CLARITY,
                        highPassEnabled = true,
                        highPassCutoffHz = 120f,
                        noiseSuppressionEnabled = true,
                        noiseSuppressionStrength = 0.72f,
                        speechClarityEnabled = true,
                        speechClarityStrength = 0.85f,
                        agcEnabled = true,
                        agcTargetDb = -16f,
                    )
                AudioPreset.QUIET_ROOM ->
                    current.copy(
                        preset = AudioPreset.QUIET_ROOM,
                        highPassEnabled = true,
                        highPassCutoffHz = 70f,
                        noiseSuppressionEnabled = false,
                        noiseSuppressionStrength = 0.20f,
                        speechClarityEnabled = true,
                        speechClarityStrength = 0.25f,
                        agcEnabled = true,
                        agcTargetDb = -20f,
                    )
            }
        }
    }
}

enum class BluetoothProfileType(val labelPl: String, val isLowLatencyCandidate: Boolean) {
    LE_AUDIO("Bluetooth LE Audio", true),
    HFP_SCO("Bluetooth HFP (Komunikacja)", true),
    A2DP("Bluetooth A2DP (Tryb awaryjny)", false),
    WIRED_HEADSET("Zestaw przewodowy / USB", true),
    UNKNOWN("Nieznany profil", false),
}

enum class CompatibilityRating(val labelPl: String) {
    EXCELLENT("Doskonała"),
    ACCEPTABLE("Akceptowalna"),
    NOT_RECOMMENDED("Niezalecana"),
    UNAVAILABLE("Brak połączenia"),
}

data class AudioOutputDevice(
    val id: Int,
    val name: String,
    val address: String? = null,
    val profileType: BluetoothProfileType,
    val isCommunicationDevice: Boolean,
    val batteryPercent: Int? = null,
    val isConnected: Boolean = true,
    val supportedSampleRates: List<Int> = emptyList(),
    val supportedEncodings: List<String> = emptyList(),
)

data class AudioRouteState(
    val availableDevices: List<AudioOutputDevice> = emptyList(),
    val selectedDevice: AudioOutputDevice? = null,
    val activeRouteLabelPl: String = "Brak wybranego urządzenia",
    val compatibilityRating: CompatibilityRating = CompatibilityRating.UNAVAILABLE,
    val compatibilityDetailPl: String = "Podłącz słuchawki kostne Bluetooth pacjenta",
    val isA2dpFallbackWarning: Boolean = false,
    val isSpeakerBlocked: Boolean = true,
    val isBluetoothEnabled: Boolean = true,
    val isRestoringConnection: Boolean = false,
    val lastPreferredDeviceName: String? = null,
)

enum class TransmissionStatus(val labelPl: String) {
    STOPPED("Zatrzymana"),
    STARTING("Uruchamianie…"),
    ACTIVE("Transmisja aktywna"),
    RECONNECTING("Oczekiwanie na słuchawki Bluetooth…"),
    PAUSED_CALL_OR_FOCUS("Wstrzymana (połączenie lub fokus audio)"),
    ERROR("Błąd strumienia audio"),
}

data class SafetyAndPermissionState(
    val hasAcceptedSafetyNotice: Boolean = false,
    val microphoneGranted: Boolean = false,
    val bluetoothGranted: Boolean = false,
    val notificationsGranted: Boolean = true,
    val batteryOptimizationIgnored: Boolean = false,
) {
    val criticalPermissionsGranted: Boolean
        get() = microphoneGranted && bluetoothGranted

    val isReadyForFirstSession: Boolean
        get() = hasAcceptedSafetyNotice && criticalPermissionsGranted
}

data class EngineTelemetry(
    val isRunning: Boolean = false,
    val inputStreamOpen: Boolean = false,
    val outputStreamOpen: Boolean = false,
    val inputIsAAudio: Boolean = false,
    val outputIsAAudio: Boolean = false,
    val inputIsExclusive: Boolean = false,
    val outputIsExclusive: Boolean = false,
    val inputSampleRate: Int = 0,
    val outputSampleRate: Int = 0,
    val inputFramesPerBurst: Int = 0,
    val outputFramesPerBurst: Int = 0,
    val inputBufferSizeFrames: Int = 0,
    val outputBufferSizeFrames: Int = 0,
    val ringBufferedFrames: Int = 0,
    val inputXRunCount: Long = 0L,
    val outputXRunCount: Long = 0L,
    val ringOverruns: Long = 0L,
    val ringUnderruns: Long = 0L,
    val dspAvgTimeUs: Float = 0f,
    val dspMaxTimeUs: Float = 0f,
    val inputBufferLatencyMs: Float = 0f,
    val ringBufferLatencyMs: Float = 0f,
    val outputBufferLatencyMs: Float = 0f,
    val estimatedPipelineLatencyMs: Float? = null,
    val inputLevelDbfs: Float = -90f,
    val outputLevelDbfs: Float = -90f,
    val limiterActive: Boolean = false,
    val streamErrorOccurred: Boolean = false,
    val autoGuardBufferMultiplier: Int = 1,
    val autoGuardAdjustments: Int = 0,
    val cpuLoadPercent: Float? = null,
    val batteryPercent: Int? = null,
    val batteryDrainPercent: Int = 0,
    val sessionDurationSeconds: Long = 0L,
) {
    val sampleRate: Int
        get() = if (inputSampleRate > 0) inputSampleRate else outputSampleRate
}

enum class LoopbackTestPhase {
    IDLE,
    MEASURING,
    COMPLETED,
    FAILED,
}

data class LoopbackMeasurement(
    val phase: LoopbackTestPhase = LoopbackTestPhase.IDLE,
    val measuredEndToEndMs: Float? = null,
    val confidence: Float = 0f,
    val timestampMillis: Long = 0L,
    val messagePl: String = "Nie wykonano jeszcze pomiaru akustycznego",
)

data class HardwareSpecificationReport(
    val manufacturer: String = "",
    val model: String = "",
    val deviceCodename: String = "",
    val hardwareBoard: String = "",
    val socModel: String = "Nie raportowane przez system",
    val androidVersion: String = "",
    val apiLevel: Int = 0,
    val securityPatch: String = "",
    val supportedAbis: String = "",
    val hasLowLatencyAudioFeature: Boolean = false,
    val hasProAudioFeature: Boolean = false,
    val supportsUnprocessedAudioSource: Boolean = false,
    val halNativeOutputSampleRateHz: Int? = null,
    val halNativeOutputFramesPerBurst: Int? = null,
    val microphones: List<MicrophoneOption> = emptyList(),
    val bluetoothEnabled: Boolean = false,
    val leAudioSupportedLabel: String = "Nieznane",
    val le2MPhySupported: Boolean = false,
    val leCodedPhySupported: Boolean = false,
    val leExtendedAdvSupported: Boolean = false,
    val leMultiAdvSupported: Boolean = false,
    val connectedHeadsetName: String? = null,
    val connectedHeadsetAddress: String? = null,
    val connectedProfileLabel: String? = null,
    val headsetBatteryPercent: Int? = null,
    val negotiatedA2dpCodec: String? = null,
    val negotiatedA2dpSampleRate: String? = null,
    val negotiatedA2dpBitsPerSample: String? = null,
    val negotiatedA2dpChannelMode: String? = null,
    val selectableA2dpCodecs: List<String> = emptyList(),
    val outputHalEncodings: List<String> = emptyList(),
    val outputHalSampleRates: List<Int> = emptyList(),
    val activeOboeSummary: String = "Silnik zatrzymany",
    val activeMicConfigSummary: String = "",
    val lastPhonePipelineLatencyMs: Float? = null,
    val lastAcousticEndToEndMs: Float? = null,
) {
    fun toClipboardReportText(): String = buildString {
        appendLine("=== HEARLINK — RAPORT SPECYFIKACJI SPRZĘTU I STANDARDÓW ===")
        appendLine()
        appendLine("## 1. Smartfon i system")
        appendLine("- Producent / Model: $manufacturer $model ($deviceCodename)")
        appendLine("- SoC / Płyta: $socModel ($hardwareBoard)")
        appendLine("- System: Android $androidVersion (API $apiLevel), poprawki: $securityPatch")
        appendLine("- Architektury ABI: $supportedAbis")
        appendLine()
        appendLine("## 2. Podsystem Audio HAL i Oboe")
        appendLine("- FEATURE_AUDIO_LOW_LATENCY: ${if (hasLowLatencyAudioFeature) "Tak" else "Nie"}")
        appendLine("- FEATURE_AUDIO_PRO: ${if (hasProAudioFeature) "Tak" else "Nie"}")
        appendLine("- Unprocessed Audio Source: ${if (supportsUnprocessedAudioSource) "Tak" else "Nie"}")
        appendLine("- Natywne wyjście HAL: ${halNativeOutputSampleRateHz?.let { "$it Hz" } ?: "Brak danych"} / ${halNativeOutputFramesPerBurst?.let { "$it klatek/burst" } ?: "Brak danych"}")
        appendLine("- Aktywny profil wejścia: $activeMicConfigSummary")
        appendLine("- Status Oboe: $activeOboeSummary")
        appendLine("- Ostatnie opóźnienie aplikacja+telefon: ${lastPhonePipelineLatencyMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "Niezmierzone (uruchom transmisję)"}")
        appendLine("- Ostatnie opóźnienie akustyczne end-to-end: ${lastAcousticEndToEndMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "Niezmierzone"}")
        appendLine()
        appendLine("## 3. Wykryte mikrofony fizyczne (${microphones.size})")
        if (microphones.isEmpty()) {
            appendLine("- Brak wykrytych mikrofonów")
        } else {
            microphones.forEach { mic ->
                val freq = mic.frequencyRangeHz?.let { " | pasmo: $it" } ?: ""
                val rates = if (mic.sampleRatesHz.isNotEmpty()) " | Hz: ${mic.sampleRatesHz.joinToString(",")}" else ""
                appendLine("- [ID ${mic.deviceId}] ${mic.labelPl} (${mic.locationLabelPl}, ${mic.directionalityLabelPl}$freq$rates)")
            }
        }
        appendLine()
        appendLine("## 4. Standardy Bluetooth telefonu")
        appendLine("- Bluetooth włączony: ${if (bluetoothEnabled) "Tak" else "Nie"}")
        appendLine("- LE Audio: $leAudioSupportedLabel")
        appendLine("- BLE 2M PHY (BT 5.0+): ${if (le2MPhySupported) "Tak" else "Nie"}")
        appendLine("- BLE Coded PHY (Long Range): ${if (leCodedPhySupported) "Tak" else "Nie"}")
        appendLine("- BLE Extended Advertising: ${if (leExtendedAdvSupported) "Tak" else "Nie"}")
        appendLine("- Multi-Advertisement: ${if (leMultiAdvSupported) "Tak" else "Nie"}")
        appendLine("- Uwaga API: Android udostępnia obsługiwane funkcje PHY/LE Audio zamiast marketingowego numeru wersji (np. BT 5.3/5.4).")
        appendLine()
        appendLine("## 5. Podłączone słuchawki i kodeki")
        appendLine("- Nazwa: ${connectedHeadsetName ?: "Brak połączenia"}")
        appendLine("- Adres MAC: ${connectedHeadsetAddress ?: "Niedostępny"}")
        appendLine("- Aktywny profil trasy: ${connectedProfileLabel ?: "Brak"}")
        appendLine("- Bateria słuchawek: ${headsetBatteryPercent?.let { "$it%" } ?: "Nie raportowana"}")
        appendLine("- Wynegocjowany kodek A2DP: ${negotiatedA2dpCodec ?: "Niedostępny / profil nie-A2DP"}")
        if (negotiatedA2dpCodec != null) {
            appendLine("  - Parametry A2DP: ${negotiatedA2dpSampleRate ?: "?"} | ${negotiatedA2dpBitsPerSample ?: "?"} | ${negotiatedA2dpChannelMode ?: "?"}")
        }
        if (selectableA2dpCodecs.isNotEmpty()) {
            appendLine("- Kodeki zgłaszane przez słuchawki: ${selectableA2dpCodecs.joinToString(", ")}")
        }
        if (outputHalEncodings.isNotEmpty()) {
            appendLine("- Formaty HAL wyjścia: ${outputHalEncodings.joinToString(", ")}")
        }
        if (outputHalSampleRates.isNotEmpty()) {
            appendLine("- Częstotliwości HAL wyjścia: ${outputHalSampleRates.joinToString(", ") { "$it Hz" }}")
        }
    }.trimEnd()
}

object CompatibilityEvaluator {
    fun evaluate(
        device: AudioOutputDevice?,
        telemetry: EngineTelemetry,
        loopback: LoopbackMeasurement,
    ): Pair<CompatibilityRating, String> {
        if (device == null || !device.isConnected) {
            return CompatibilityRating.UNAVAILABLE to
                "Brak podłączonych słuchawek pacjenta. Głośnik telefonu jest zablokowany."
        }

        val measuredMs = loopback.measuredEndToEndMs
        val unstableStream = telemetry.streamErrorOccurred || telemetry.ringUnderruns > 16

        if (unstableStream) {
            return CompatibilityRating.NOT_RECOMMENDED to
                "Wykryto niestabilność bufora audio lub rozłączenia. Sprawdź odległość i profil Bluetooth."
        }

        if (measuredMs != null) {
            return when {
                measuredMs < 100f && device.profileType != BluetoothProfileType.A2DP ->
                    CompatibilityRating.EXCELLENT to
                        "Zweryfikowano niskie opóźnienie (${measuredMs.toInt()} ms < 100 ms)."
                measuredMs <= 150f ->
                    CompatibilityRating.ACCEPTABLE to
                        "Zmierzono ${measuredMs.toInt()} ms. Jakość użyteczna; możliwe lekkie opóźnienie."
                else ->
                    CompatibilityRating.NOT_RECOMMENDED to
                        "Zmierzone opóźnienie (${measuredMs.toInt()} ms) przekracza próg 150 ms."
            }
        }

        return when (device.profileType) {
            BluetoothProfileType.LE_AUDIO ->
                CompatibilityRating.ACCEPTABLE to
                    "Profil LE Audio aktywny. Uruchom test akustyczny w Diagnostyce, aby potwierdzić < 100 ms."
            BluetoothProfileType.HFP_SCO ->
                CompatibilityRating.ACCEPTABLE to
                    "Profil komunikacyjny HFP aktywny. Uruchom test akustyczny, aby zmierzyć rzeczywiste opóźnienie."
            BluetoothProfileType.WIRED_HEADSET ->
                CompatibilityRating.ACCEPTABLE to
                    "Wyjście przewodowe/USB wykryte. Uruchom test akustyczny, aby potwierdzić opóźnienie."
            BluetoothProfileType.A2DP ->
                CompatibilityRating.ACCEPTABLE to
                    "Tryb awaryjny A2DP: możliwe wyższe opóźnienie i desynchronizacja mowy."
            BluetoothProfileType.UNKNOWN ->
                CompatibilityRating.NOT_RECOMMENDED to
                    "Nieznany profil wyjścia audio. Zalecany test opóźnienia przed sesją."
        }
    }
}
