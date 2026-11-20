package com.example.audio

import com.example.model.DspConfig
import com.example.model.EngineTelemetry
import com.example.model.LoopbackTestPhase

object NativeAudioBridge {

    private var libraryLoaded = false

    init {
        libraryLoaded = try {
            System.loadLibrary("hearlink_audio")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun isAvailable(): Boolean = libraryLoaded

    fun start(inputDeviceId: Int, outputDeviceId: Int, isA2dpMode: Boolean): Boolean {
        if (!libraryLoaded) return false
        val usageMode = if (isA2dpMode) 1 else 0
        return nativeStart(inputDeviceId, outputDeviceId, usageMode)
    }

    fun stop() {
        if (!libraryLoaded) return
        nativeStop()
    }

    fun setMuted(muted: Boolean) {
        if (!libraryLoaded) return
        nativeSetMuted(muted)
    }

    fun updateDspConfig(config: DspConfig) {
        if (!libraryLoaded) return
        nativeUpdateDspConfig(
            micSensitivityDb = config.micSensitivityDb,
            outputVolumeGain = config.outputVolumeGain,
            maxOutputLimitDb = config.maxOutputLimitDb,
            softStartRampMs = config.softStartRampMs,
            highPassEnabled = config.highPassEnabled,
            highPassCutoffHz = config.highPassCutoffHz,
            noiseSuppressionEnabled = config.noiseSuppressionEnabled,
            noiseSuppressionStrength = config.noiseSuppressionStrength,
            speechClarityEnabled = config.speechClarityEnabled,
            speechClarityStrength = config.speechClarityStrength,
            agcEnabled = config.agcEnabled,
            agcTargetDb = config.agcTargetDb,
        )
    }

    fun startLoopbackTest() {
        if (!libraryLoaded) return
        nativeStartLoopbackTest()
    }

    fun cancelLoopbackTest() {
        if (!libraryLoaded) return
        nativeCancelLoopbackTest()
    }

    data class NativeLoopbackPoll(
        val phase: LoopbackTestPhase,
        val measuredMs: Float?,
        val confidence: Float,
    )

    fun pollLoopbackTest(): NativeLoopbackPoll {
        if (!libraryLoaded) {
            return NativeLoopbackPoll(LoopbackTestPhase.FAILED, null, 0f)
        }
        val arr = nativePollLoopbackTest()
        if (arr.size < 3) {
            return NativeLoopbackPoll(LoopbackTestPhase.IDLE, null, 0f)
        }
        val stateCode = arr[0].toInt()
        val phase = when (stateCode) {
            1, 2, 3 -> LoopbackTestPhase.MEASURING
            4 -> LoopbackTestPhase.COMPLETED
            5 -> LoopbackTestPhase.FAILED
            else -> LoopbackTestPhase.IDLE
        }
        val ms = if (arr[1] >= 0f) arr[1] else null
        return NativeLoopbackPoll(phase = phase, measuredMs = ms, confidence = arr[2])
    }

    fun getTelemetry(): EngineTelemetry {
        if (!libraryLoaded) return EngineTelemetry()
        val arr = nativeGetTelemetry()
        if (arr.size < 23) return EngineTelemetry()
        val running = arr[0] > 0.5f
        val estLatency = if (running && arr[18] >= 0f) arr[18] else null
        return EngineTelemetry(
            isRunning = running,
            inputStreamOpen = arr[1] > 0.5f,
            outputStreamOpen = arr[2] > 0.5f,
            inputIsAAudio = arr[3] > 0.5f,
            outputIsAAudio = arr[4] > 0.5f,
            inputIsExclusive = arr[5] > 0.5f,
            outputIsExclusive = arr[6] > 0.5f,
            sampleRate = arr[7].toInt(),
            inputFramesPerBurst = arr[8].toInt(),
            outputFramesPerBurst = arr[9].toInt(),
            outputBufferSizeFrames = arr[10].toInt(),
            ringBufferedFrames = arr[11].toInt(),
            inputXRunCount = arr[12].toLong(),
            outputXRunCount = arr[13].toLong(),
            ringOverruns = arr[14].toLong(),
            ringUnderruns = arr[15].toLong(),
            dspAvgTimeUs = arr[16],
            dspMaxTimeUs = arr[17],
            estimatedPipelineLatencyMs = estLatency,
            inputLevelDbfs = arr[19],
            outputLevelDbfs = arr[20],
            limiterActive = arr[21] > 0.5f,
            streamErrorOccurred = arr[22] > 0.5f,
        )
    }

    private external fun nativeStart(inputDeviceId: Int, outputDeviceId: Int, usageMode: Int): Boolean
    private external fun nativeStop()
    private external fun nativeSetMuted(muted: Boolean)
    private external fun nativeUpdateDspConfig(
        micSensitivityDb: Float,
        outputVolumeGain: Float,
        maxOutputLimitDb: Float,
        softStartRampMs: Float,
        highPassEnabled: Boolean,
        highPassCutoffHz: Float,
        noiseSuppressionEnabled: Boolean,
        noiseSuppressionStrength: Float,
        speechClarityEnabled: Boolean,
        speechClarityStrength: Float,
        agcEnabled: Boolean,
        agcTargetDb: Float,
    )
    private external fun nativeStartLoopbackTest()
    private external fun nativeCancelLoopbackTest()
    private external fun nativePollLoopbackTest(): FloatArray
    private external fun nativeGetTelemetry(): FloatArray
}
