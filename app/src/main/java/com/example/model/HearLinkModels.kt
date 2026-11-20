package com.example.model

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

data class DspConfig(
    val preset: AudioPreset = AudioPreset.STANDARD,
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
    val sampleRate: Int = 0,
    val inputFramesPerBurst: Int = 0,
    val outputFramesPerBurst: Int = 0,
    val outputBufferSizeFrames: Int = 0,
    val ringBufferedFrames: Int = 0,
    val inputXRunCount: Long = 0L,
    val outputXRunCount: Long = 0L,
    val ringOverruns: Long = 0L,
    val ringUnderruns: Long = 0L,
    val dspAvgTimeUs: Float = 0f,
    val dspMaxTimeUs: Float = 0f,
    val estimatedPipelineLatencyMs: Float? = null,
    val inputLevelDbfs: Float = -90f,
    val outputLevelDbfs: Float = -90f,
    val limiterActive: Boolean = false,
    val streamErrorOccurred: Boolean = false,
    val cpuLoadPercent: Float? = null,
    val batteryPercent: Int? = null,
    val batteryDrainPercent: Int = 0,
    val sessionDurationSeconds: Long = 0L,
)

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
        val unstableStream = telemetry.streamErrorOccurred || telemetry.ringUnderruns > 12

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
