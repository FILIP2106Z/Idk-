package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioSessionHub
import com.example.audio.AudioTransmissionService
import com.example.audio.PermissionChecker
import com.example.data.HearLinkPreferencesRepository
import com.example.model.AudioOutputDevice
import com.example.model.AudioPreset
import com.example.model.AudioRouteState
import com.example.model.DspConfig
import com.example.model.EngineTelemetry
import com.example.model.HardwareSpecificationReport
import com.example.model.LatencyProfile
import com.example.model.LoopbackMeasurement
import com.example.model.MicInputPreset
import com.example.model.MicrophoneOption
import com.example.model.OutputUsagePreference
import com.example.model.SafetyAndPermissionState
import com.example.model.ThemeMode
import com.example.model.TransmissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HearLinkUiState(
    val settingsLoaded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val safetyState: SafetyAndPermissionState = SafetyAndPermissionState(),
    val dspConfig: DspConfig = DspConfig(),
    val routeState: AudioRouteState = AudioRouteState(),
    val transmissionStatus: TransmissionStatus = TransmissionStatus.STOPPED,
    val telemetry: EngineTelemetry = EngineTelemetry(),
    val loopbackMeasurement: LoopbackMeasurement = LoopbackMeasurement(),
    val microphones: List<MicrophoneOption> = emptyList(),
    val hardwareReport: HardwareSpecificationReport = HardwareSpecificationReport(),
    val errorMessagePl: String? = null,
    val showDeviceSheet: Boolean = false,
) {
    val isTransmissionActiveOrStarting: Boolean
        get() = transmissionStatus == TransmissionStatus.ACTIVE ||
            transmissionStatus == TransmissionStatus.STARTING ||
            transmissionStatus == TransmissionStatus.RECONNECTING ||
            transmissionStatus == TransmissionStatus.PAUSED_CALL_OR_FOCUS

    val canStartTransmission: Boolean
        get() = safetyState.isReadyForFirstSession && routeState.selectedDevice != null
}

class HearLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = HearLinkPreferencesRepository(application)
    private val safetyAcceptedFlow = MutableStateFlow(false)
    private val permissionRefreshTrigger = MutableStateFlow(0)
    private val showDeviceSheetFlow = MutableStateFlow(false)

    private val safetyAndPermissionsFlow = combine(
        preferencesRepository.settingsFlow,
        permissionRefreshTrigger,
    ) { settings, _ ->
        safetyAcceptedFlow.value = settings.hasAcceptedSafetyNotice
        PermissionChecker.checkPermissions(
            context = getApplication(),
            hasAcceptedSafetyNotice = settings.hasAcceptedSafetyNotice,
        )
    }

    private val coreSessionFlow = combine(
        preferencesRepository.settingsFlow,
        AudioSessionHub.routeState,
        AudioSessionHub.transmissionStatus,
        AudioSessionHub.telemetry,
        AudioSessionHub.loopbackMeasurement,
    ) { settings, route, status, telemetry, loopback ->
        SessionBundle(settings, route, status, telemetry, loopback)
    }

    private val hardwareBundleFlow = combine(
        AudioSessionHub.microphones,
        AudioSessionHub.hardwareReport,
    ) { mics, report ->
        mics to report
    }

    val uiState: StateFlow<HearLinkUiState> = combine(
        coreSessionFlow,
        safetyAndPermissionsFlow,
        hardwareBundleFlow,
        AudioSessionHub.lastErrorMessagePl,
        showDeviceSheetFlow,
    ) { bundle, safety, hwBundle, errorMsg, showSheet ->
        HearLinkUiState(
            settingsLoaded = true,
            themeMode = bundle.settings.themeMode,
            safetyState = safety,
            dspConfig = bundle.settings.dspConfig,
            routeState = bundle.route,
            transmissionStatus = bundle.status,
            telemetry = bundle.telemetry,
            loopbackMeasurement = bundle.loopback,
            microphones = hwBundle.first,
            hardwareReport = hwBundle.second,
            errorMessagePl = errorMsg,
            showDeviceSheet = showSheet,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HearLinkUiState(),
    )

    init {
        viewModelScope.launch {
            preferencesRepository.settingsFlow.collect { stored ->
                AudioSessionHub.applyDspConfig(stored.dspConfig)
                AudioSessionHub.activeService?.routeManager?.setPreferredDeviceHint(
                    stored.lastPreferredDeviceName,
                    stored.lastPreferredDeviceAddress,
                )
            }
        }
    }

    fun refreshPermissionsAndDevices() {
        permissionRefreshTrigger.value++
        runCatching { AudioTransmissionService.startMonitorOnly(getApplication()) }
        AudioSessionHub.refreshDevices()
    }

    fun refreshHardwareSpecification() {
        runCatching { AudioTransmissionService.startMonitorOnly(getApplication()) }
        AudioSessionHub.refreshHardwareReport()
    }

    fun acceptSafetyDisclaimer() {
        viewModelScope.launch {
            preferencesRepository.setSafetyNoticeAccepted(true)
            permissionRefreshTrigger.value++
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun startTransmission() {
        refreshPermissionsAndDevices()
        val state = uiState.value
        if (!state.safetyState.criticalPermissionsGranted) {
            AudioSessionHub.updateStatus(
                TransmissionStatus.ERROR,
                "Brak wymaganych uprawnień (mikrofon lub Bluetooth). Uzupełnij je na liście bezpieczeństwa.",
            )
            return
        }
        if (state.routeState.selectedDevice == null) {
            AudioSessionHub.updateStatus(
                TransmissionStatus.ERROR,
                "Nie podłączono słuchawek pacjenta. Połącz słuchawki Bluetooth i spróbuj ponownie.",
            )
            return
        }
        AudioSessionHub.applyDspConfig(state.dspConfig)
        AudioTransmissionService.startTransmission(getApplication())
    }

    fun stopTransmission() {
        AudioTransmissionService.stopTransmission(getApplication())
    }

    fun openDeviceSelector() {
        refreshPermissionsAndDevices()
        showDeviceSheetFlow.value = true
    }

    fun closeDeviceSelector() {
        showDeviceSheetFlow.value = false
    }

    fun selectAudioDevice(device: AudioOutputDevice) {
        AudioSessionHub.selectDevice(device)
        viewModelScope.launch {
            preferencesRepository.saveLastPreferredDevice(device.name, device.address)
        }
        showDeviceSheetFlow.value = false
    }

    fun selectPreset(preset: AudioPreset) {
        val updated = DspConfig.forPreset(preset, uiState.value.dspConfig)
        updateDspConfig(updated)
    }

    fun selectMicrophone(deviceId: Int) {
        updateDspConfig(uiState.value.dspConfig.copy(selectedMicDeviceId = deviceId))
    }

    fun selectMicInputPreset(preset: MicInputPreset) {
        updateDspConfig(uiState.value.dspConfig.copy(micInputPreset = preset))
    }

    fun selectLatencyProfile(profile: LatencyProfile) {
        updateDspConfig(uiState.value.dspConfig.copy(latencyProfile = profile))
    }

    fun selectA2dpUsagePreference(preference: OutputUsagePreference) {
        updateDspConfig(uiState.value.dspConfig.copy(a2dpUsagePreference = preference))
    }

    fun updateMicSensitivity(db: Float) {
        updateDspConfig(uiState.value.dspConfig.copy(micSensitivityDb = db.coerceIn(-12f, 24f)))
    }

    fun updateOutputVolume(gain: Float) {
        updateDspConfig(uiState.value.dspConfig.copy(outputVolumeGain = gain.coerceIn(0f, 1.0f)))
    }

    fun updateNoiseSuppression(enabled: Boolean) {
        updateDspConfig(uiState.value.dspConfig.copy(noiseSuppressionEnabled = enabled))
    }

    fun updateNoiseSuppressionStrength(strength: Float) {
        updateDspConfig(uiState.value.dspConfig.copy(noiseSuppressionStrength = strength.coerceIn(0f, 1f)))
    }

    fun updateSpeechClarity(enabled: Boolean) {
        updateDspConfig(uiState.value.dspConfig.copy(speechClarityEnabled = enabled))
    }

    fun updateSpeechClarityStrength(strength: Float) {
        updateDspConfig(uiState.value.dspConfig.copy(speechClarityStrength = strength.coerceIn(0f, 1f)))
    }

    fun updateHighPass(enabled: Boolean, cutoffHz: Float = uiState.value.dspConfig.highPassCutoffHz) {
        updateDspConfig(
            uiState.value.dspConfig.copy(
                highPassEnabled = enabled,
                highPassCutoffHz = cutoffHz.coerceIn(50f, 250f),
            ),
        )
    }

    fun updateAgc(enabled: Boolean, targetDb: Float = uiState.value.dspConfig.agcTargetDb) {
        updateDspConfig(
            uiState.value.dspConfig.copy(
                agcEnabled = enabled,
                agcTargetDb = targetDb.coerceIn(-26f, -10f),
            ),
        )
    }

    fun updateMaxOutputLimit(limitDb: Float) {
        updateDspConfig(uiState.value.dspConfig.copy(maxOutputLimitDb = limitDb.coerceIn(-18f, 0f)))
    }

    fun updateSoftStartRampMs(rampMs: Float) {
        updateDspConfig(uiState.value.dspConfig.copy(softStartRampMs = rampMs.coerceIn(200f, 2000f)))
    }

    private fun updateDspConfig(config: DspConfig) {
        AudioSessionHub.applyDspConfig(config)
        viewModelScope.launch {
            preferencesRepository.saveDspConfig(config)
        }
    }

    fun startLoopbackTest() {
        AudioSessionHub.triggerLoopbackTest()
    }

    fun cancelLoopbackTest() {
        AudioSessionHub.cancelLoopbackTest()
    }

    private data class SessionBundle(
        val settings: com.example.data.StoredAppSettings,
        val route: AudioRouteState,
        val status: TransmissionStatus,
        val telemetry: EngineTelemetry,
        val loopback: LoopbackMeasurement,
    )
}
