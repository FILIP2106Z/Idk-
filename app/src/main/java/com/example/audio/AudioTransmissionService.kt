package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.model.AudioOutputDevice
import com.example.model.AudioRouteState
import com.example.model.BluetoothProfileType
import com.example.model.DspConfig
import com.example.model.EngineTelemetry
import com.example.model.HardwareSpecificationReport
import com.example.model.LoopbackMeasurement
import com.example.model.LoopbackTestPhase
import com.example.model.MicrophoneOption
import com.example.model.TransmissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-wide audio session coordinator shared cleanly between ForegroundService and ViewModel.
 */
object AudioSessionHub {
    private val _transmissionStatus = MutableStateFlow(TransmissionStatus.STOPPED)
    val transmissionStatus: StateFlow<TransmissionStatus> = _transmissionStatus.asStateFlow()

    private val _telemetry = MutableStateFlow(EngineTelemetry())
    val telemetry: StateFlow<EngineTelemetry> = _telemetry.asStateFlow()

    private val _routeState = MutableStateFlow(AudioRouteState())
    val routeState: StateFlow<AudioRouteState> = _routeState.asStateFlow()

    private val _loopbackMeasurement = MutableStateFlow(LoopbackMeasurement())
    val loopbackMeasurement: StateFlow<LoopbackMeasurement> = _loopbackMeasurement.asStateFlow()

    private val _microphones = MutableStateFlow<List<MicrophoneOption>>(emptyList())
    val microphones: StateFlow<List<MicrophoneOption>> = _microphones.asStateFlow()

    private val _hardwareReport = MutableStateFlow(HardwareSpecificationReport())
    val hardwareReport: StateFlow<HardwareSpecificationReport> = _hardwareReport.asStateFlow()

    private val _lastErrorMessagePl = MutableStateFlow<String?>(null)
    val lastErrorMessagePl: StateFlow<String?> = _lastErrorMessagePl.asStateFlow()

    @Volatile
    internal var latestDspConfig: DspConfig = DspConfig()
    internal var activeService: AudioTransmissionService? = null

    fun updateStatus(status: TransmissionStatus, errorPl: String? = null) {
        _transmissionStatus.value = status
        _lastErrorMessagePl.value = errorPl
    }

    fun updateTelemetry(value: EngineTelemetry) {
        _telemetry.value = value
    }

    fun updateRouteState(value: AudioRouteState) {
        _routeState.value = value
    }

    fun updateLoopback(value: LoopbackMeasurement) {
        _loopbackMeasurement.value = value
    }

    fun updateMicrophones(list: List<MicrophoneOption>) {
        _microphones.value = list
    }

    fun updateHardwareReport(report: HardwareSpecificationReport) {
        _hardwareReport.value = report
    }

    fun selectDevice(device: AudioOutputDevice) {
        activeService?.routeManager?.selectDevice(device)
    }

    fun refreshDevices() {
        activeService?.routeManager?.refreshDevices()
        activeService?.refreshHardwareSpec()
    }

    fun refreshHardwareReport() {
        activeService?.refreshHardwareSpec()
    }

    fun applyDspConfig(config: DspConfig) {
        latestDspConfig = config
        NativeAudioBridge.updateDspConfig(config)
        val service = activeService
        if (service != null) {
            service.applyUpdatedConfig(config)
        }
    }

    fun triggerLoopbackTest() {
        activeService?.startLoopbackMeasurement()
    }

    fun cancelLoopbackTest() {
        NativeAudioBridge.cancelLoopbackTest()
        _loopbackMeasurement.value = _loopbackMeasurement.value.copy(phase = LoopbackTestPhase.IDLE)
    }
}

class AudioTransmissionService : Service() {

    companion object {
        const val ACTION_START = "com.example.hearlink.ACTION_START"
        const val ACTION_STOP = "com.example.hearlink.ACTION_STOP"
        const val ACTION_SYNC_IDLE = "com.example.hearlink.ACTION_SYNC_IDLE"
        const val NOTIFICATION_CHANNEL_ID = "hearlink_transmission_channel"
        const val NOTIFICATION_ID = 1001

        fun startTransmission(context: Context) {
            val intent = Intent(context, AudioTransmissionService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTransmission(context: Context) {
            val intent = Intent(context, AudioTransmissionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun startMonitorOnly(context: Context) {
            val intent = Intent(context, AudioTransmissionService::class.java).apply {
                action = ACTION_SYNC_IDLE
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var telemetryJob: Job? = null
    private var loopbackPollJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private lateinit var audioManager: AudioManager
    private lateinit var diagnosticsMonitor: SystemDiagnosticsMonitor
    private lateinit var hardwareInspector: HardwareInspector

    internal lateinit var routeManager: BluetoothRouteManager
    internal var currentDspConfig: DspConfig = DspConfig()
    private var sessionStartMillis: Long = 0L
    private var isTransmitting: Boolean = false
    private var pausedByFocusLoss: Boolean = false
    private var pausedByDisconnect: Boolean = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isTransmitting) {
                    pausedByFocusLoss = true
                    NativeAudioBridge.setMuted(true)
                    AudioSessionHub.updateStatus(TransmissionStatus.PAUSED_CALL_OR_FOCUS)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isTransmitting && pausedByFocusLoss && !pausedByDisconnect) {
                    pausedByFocusLoss = false
                    NativeAudioBridge.setMuted(false)
                    AudioSessionHub.updateStatus(TransmissionStatus.ACTIVE)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (isTransmitting) {
                    stopStreamingImmediately()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AudioSessionHub.activeService = this
        currentDspConfig = AudioSessionHub.latestDspConfig
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        diagnosticsMonitor = SystemDiagnosticsMonitor(this)
        hardwareInspector = HardwareInspector(this)
        hardwareInspector.registerProfileProxy()
        createNotificationChannel()

        routeManager = BluetoothRouteManager(
            context = this,
            onDeviceLostDuringTransmission = {
                serviceScope.launch(Dispatchers.Main) {
                    handleBluetoothDisconnect()
                }
            },
            onDeviceRestoredDuringTransmission = { device ->
                serviceScope.launch(Dispatchers.Main) {
                    handleBluetoothReconnect(device)
                }
            },
        )
        routeManager.startMonitoring()
        refreshHardwareSpec()

        serviceScope.launch {
            routeManager.routeState.collect { state ->
                AudioSessionHub.updateRouteState(state)
                refreshHardwareSpec()
                if (isTransmitting) {
                    updateForegroundNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> beginStreaming()
            ACTION_STOP -> stopStreamingImmediately()
            ACTION_SYNC_IDLE -> {
                routeManager.refreshDevices()
                refreshHardwareSpec()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopStreamingImmediately()
        routeManager.stopMonitoring()
        hardwareInspector.unregisterProfileProxy()
        if (AudioSessionHub.activeService === this) {
            AudioSessionHub.activeService = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun beginStreaming() {
        if (isTransmitting) return

        AudioSessionHub.updateStatus(TransmissionStatus.STARTING)
        routeManager.refreshDevices()
        val selectedDevice = routeManager.routeState.value.selectedDevice

        if (selectedDevice == null) {
            AudioSessionHub.updateStatus(
                TransmissionStatus.ERROR,
                "Nie wykryto podłączonych słuchawek pacjenta. Podłącz słuchawki Bluetooth przed startem.",
            )
            return
        }

        // Start foreground immediately with microphone type before opening Oboe input stream
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(selectedDevice.name, reconnecting = false),
            fgsType,
        )

        requestAudioFocus()
        acquireWakeLock()

        routeManager.setTransmissionState(true)
        val started = startNativeEngineForDevice(selectedDevice, currentDspConfig)

        if (!started) {
            routeManager.setTransmissionState(false)
            abandonAudioFocus()
            releaseWakeLock()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            AudioSessionHub.updateStatus(
                TransmissionStatus.ERROR,
                "Nie udało się uruchomić strumienia audio Oboe/AAudio. Sprawdź uprawnienia mikrofonu i połączenie Bluetooth.",
            )
            return
        }

        isTransmitting = true
        pausedByFocusLoss = false
        pausedByDisconnect = false
        sessionStartMillis = System.currentTimeMillis()
        diagnosticsMonitor.resetSessionBaseline()

        AudioSessionHub.updateStatus(TransmissionStatus.ACTIVE)
        refreshHardwareSpec()
        startTelemetryLoop()
    }

    private fun stopStreamingImmediately() {
        telemetryJob?.cancel()
        loopbackPollJob?.cancel()
        isTransmitting = false
        pausedByFocusLoss = false
        pausedByDisconnect = false

        NativeAudioBridge.stop()
        routeManager.setTransmissionState(false)
        abandonAudioFocus()
        releaseWakeLock()

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        AudioSessionHub.updateStatus(TransmissionStatus.STOPPED)
        AudioSessionHub.updateTelemetry(
            EngineTelemetry(
                batteryPercent = diagnosticsMonitor.readBatteryPercent(),
            ),
        )
        refreshHardwareSpec()
    }

    private fun handleBluetoothDisconnect() {
        if (!isTransmitting) return
        pausedByDisconnect = true
        NativeAudioBridge.setMuted(true)
        NativeAudioBridge.stop()
        AudioSessionHub.updateStatus(TransmissionStatus.RECONNECTING)
        updateForegroundNotification()
        refreshHardwareSpec()
    }

    private fun handleBluetoothReconnect(device: AudioOutputDevice) {
        if (!isTransmitting) return
        pausedByDisconnect = false
        val restarted = startNativeEngineForDevice(device, currentDspConfig)
        if (restarted) {
            NativeAudioBridge.setMuted(pausedByFocusLoss)
            AudioSessionHub.updateStatus(
                if (pausedByFocusLoss) TransmissionStatus.PAUSED_CALL_OR_FOCUS else TransmissionStatus.ACTIVE,
            )
            updateForegroundNotification()
            refreshHardwareSpec()
        } else {
            AudioSessionHub.updateStatus(
                TransmissionStatus.ERROR,
                "Słuchawki połączyły się ponownie, ale restart strumienia audio nie powiódł się.",
            )
        }
    }

    internal fun applyUpdatedConfig(newConfig: DspConfig) {
        val prev = currentDspConfig
        currentDspConfig = newConfig
        val needsEngineRestart = prev.selectedMicDeviceId != newConfig.selectedMicDeviceId ||
            prev.micInputPreset != newConfig.micInputPreset ||
            prev.latencyProfile != newConfig.latencyProfile ||
            prev.a2dpUsagePreference != newConfig.a2dpUsagePreference

        if (needsEngineRestart && isTransmitting && !pausedByDisconnect) {
            val selected = routeManager.routeState.value.selectedDevice
            if (selected != null) {
                NativeAudioBridge.stop()
                routeManager.applyRoutingToDevice(selected)
                val restarted = startNativeEngineForDevice(selected, newConfig)
                if (restarted) {
                    NativeAudioBridge.setMuted(pausedByFocusLoss)
                } else {
                    AudioSessionHub.updateStatus(
                        TransmissionStatus.ERROR,
                        "Nie udało się zrestartować strumienia po zmianie mikrofonu lub trybu opóźnienia.",
                    )
                }
            }
        }
        if (needsEngineRestart) {
            refreshHardwareSpec()
        }
    }

    internal fun refreshHardwareSpec() {
        if (!::hardwareInspector.isInitialized) return
        val mics = hardwareInspector.enumerateMicrophones()
        AudioSessionHub.updateMicrophones(mics)
        val report = hardwareInspector.collectFullSpecification(
            routeState = routeManager.routeState.value,
            telemetry = AudioSessionHub.telemetry.value,
            loopback = AudioSessionHub.loopbackMeasurement.value,
            dspConfig = currentDspConfig,
            microphones = mics,
        )
        AudioSessionHub.updateHardwareReport(report)
    }

    private fun startNativeEngineForDevice(device: AudioOutputDevice, config: DspConfig): Boolean {
        val micId = if (config.selectedMicDeviceId > 0) {
            config.selectedMicDeviceId
        } else {
            routeManager.findBuiltInMicDeviceId()
        }
        val usageMode = if (device.profileType == BluetoothProfileType.A2DP) {
            config.a2dpUsagePreference.nativeUsageCode
        } else {
            0
        }
        NativeAudioBridge.updateDspConfig(config)
        return NativeAudioBridge.start(
            inputDeviceId = micId,
            outputDeviceId = device.id,
            usageMode = usageMode,
            inputPresetMode = config.micInputPreset.nativeCode,
            ultraLowLatencyMode = config.latencyProfile.isUltraLow,
        )
    }

    internal fun startLoopbackMeasurement() {
        if (!isTransmitting) {
            AudioSessionHub.updateLoopback(
                LoopbackMeasurement(
                    phase = LoopbackTestPhase.FAILED,
                    messagePl = "Najpierw uruchom transmisję do słuchawek, aby wykonać test akustyczny.",
                ),
            )
            return
        }
        loopbackPollJob?.cancel()
        AudioSessionHub.updateLoopback(
            LoopbackMeasurement(
                phase = LoopbackTestPhase.MEASURING,
                messagePl = "Wysyłanie bezpiecznego impulsu testowego i nasłuch echa…",
            ),
        )
        NativeAudioBridge.startLoopbackTest()

        loopbackPollJob = serviceScope.launch {
            var attempts = 0
            while (isActive && attempts < 30) {
                delay(75L)
                attempts++
                val poll = NativeAudioBridge.pollLoopbackTest()
                when (poll.phase) {
                    LoopbackTestPhase.COMPLETED -> {
                        val ms = poll.measuredMs
                        val result = LoopbackMeasurement(
                            phase = LoopbackTestPhase.COMPLETED,
                            measuredEndToEndMs = ms,
                            confidence = poll.confidence,
                            timestampMillis = System.currentTimeMillis(),
                            messagePl = "Pomiar zakończony (${ms?.toInt()} ms, pewność ${(poll.confidence * 100).toInt()}%).",
                        )
                        AudioSessionHub.updateLoopback(result)
                        routeManager.updateEvaluationContext(AudioSessionHub.telemetry.value, result)
                        return@launch
                    }
                    LoopbackTestPhase.FAILED -> {
                        val result = LoopbackMeasurement(
                            phase = LoopbackTestPhase.FAILED,
                            measuredEndToEndMs = null,
                            confidence = poll.confidence,
                            timestampMillis = System.currentTimeMillis(),
                            messagePl = "Nie wykryto wyraźnego echa impulsu. Zbliż słuchawki do mikrofonu w cichym pomieszczeniu i zwiększ głośność.",
                        )
                        AudioSessionHub.updateLoopback(result)
                        routeManager.updateEvaluationContext(AudioSessionHub.telemetry.value, result)
                        return@launch
                    }
                    else -> Unit
                }
            }
            NativeAudioBridge.cancelLoopbackTest()
            AudioSessionHub.updateLoopback(
                LoopbackMeasurement(
                    phase = LoopbackTestPhase.FAILED,
                    messagePl = "Przekroczono czas oczekiwania na odpowiedź akustyczną.",
                ),
            )
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            var tickCount = 0
            while (isActive && isTransmitting) {
                val nativeSnap = NativeAudioBridge.getTelemetry()

                // Auto-recover if Oboe reports stream disconnect while Bluetooth device is still present
                if (nativeSnap.streamErrorOccurred && !pausedByDisconnect) {
                    val selected = routeManager.routeState.value.selectedDevice
                    if (selected != null) {
                        NativeAudioBridge.stop()
                        routeManager.applyRoutingToDevice(selected)
                        startNativeEngineForDevice(selected, currentDspConfig)
                    }
                }

                val durationSec = ((System.currentTimeMillis() - sessionStartMillis) / 1000L).coerceAtLeast(0L)
                val fullTelemetry = nativeSnap.copy(
                    cpuLoadPercent = diagnosticsMonitor.sampleCpuLoadPercent(),
                    batteryPercent = diagnosticsMonitor.readBatteryPercent(),
                    batteryDrainPercent = diagnosticsMonitor.readBatteryDrainPercent(),
                    sessionDurationSeconds = durationSec,
                )
                AudioSessionHub.updateTelemetry(fullTelemetry)
                routeManager.updateEvaluationContext(fullTelemetry, AudioSessionHub.loopbackMeasurement.value)
                if (tickCount % 8 == 0) {
                    refreshHardwareSpec()
                }
                tickCount++
                delay(120L)
            }
        }
    }

    private fun requestAudioFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
        audioFocusRequest = req
        audioManager.requestAudioFocus(req)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        audioFocusRequest = null
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HearLink::AudioTransmissionWakeLock")
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(4 * 60 * 60 * 1000L) // Safety 4-hour max cap
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            runCatching { wakeLock?.release() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name = routeManager.routeState.value.selectedDevice?.name ?: "Słuchawki Bluetooth"
        nm.notify(NOTIFICATION_ID, buildNotification(name, reconnecting = pausedByDisconnect))
    }

    private fun buildNotification(deviceName: String, reconnecting: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioTransmissionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (reconnecting) {
            getString(R.string.notification_title_reconnecting)
        } else {
            getString(R.string.notification_title_active)
        }
        val body = if (reconnecting) {
            getString(R.string.notification_body_reconnecting)
        } else {
            getString(R.string.notification_body_active, deviceName)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }
}
