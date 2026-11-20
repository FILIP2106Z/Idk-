#include <jni.h>
#include <memory>
#include "AudioEngine.h"

namespace {
std::unique_ptr<hearlink::AudioEngine> gEngine;

hearlink::AudioEngine* getOrCreateEngine() {
    if (!gEngine) {
        gEngine = std::make_unique<hearlink::AudioEngine>();
    }
    return gEngine.get();
}
} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioBridge_nativeStart(
    JNIEnv*,
    jobject,
    jint inputDeviceId,
    jint outputDeviceId,
    jint usageMode
) {
    return getOrCreateEngine()->start(inputDeviceId, outputDeviceId, usageMode) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeStop(JNIEnv*, jobject) {
    if (gEngine) {
        gEngine->stop();
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeSetMuted(JNIEnv*, jobject, jboolean muted) {
    if (gEngine) {
        gEngine->setOutputMuted(muted == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeUpdateDspConfig(
    JNIEnv*,
    jobject,
    jfloat micSensitivityDb,
    jfloat outputVolumeGain,
    jfloat maxOutputLimitDb,
    jfloat softStartRampMs,
    jboolean highPassEnabled,
    jfloat highPassCutoffHz,
    jboolean noiseSuppressionEnabled,
    jfloat noiseSuppressionStrength,
    jboolean speechClarityEnabled,
    jfloat speechClarityStrength,
    jboolean agcEnabled,
    jfloat agcTargetDb
) {
    getOrCreateEngine()->updateDspConfig(
        micSensitivityDb,
        outputVolumeGain,
        maxOutputLimitDb,
        softStartRampMs,
        highPassEnabled == JNI_TRUE,
        highPassCutoffHz,
        noiseSuppressionEnabled == JNI_TRUE,
        noiseSuppressionStrength,
        speechClarityEnabled == JNI_TRUE,
        speechClarityStrength,
        agcEnabled == JNI_TRUE,
        agcTargetDb
    );
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeStartLoopbackTest(JNIEnv*, jobject) {
    if (gEngine) {
        gEngine->startLoopbackTest();
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioBridge_nativeCancelLoopbackTest(JNIEnv*, jobject) {
    if (gEngine) {
        gEngine->cancelLoopbackTest();
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_audio_NativeAudioBridge_nativePollLoopbackTest(JNIEnv* env, jobject) {
    jfloatArray arr = env->NewFloatArray(3);
    if (arr == nullptr) return nullptr;
    float values[3] = {0.0f, -1.0f, 0.0f};
    if (gEngine) {
        const auto res = gEngine->pollLoopbackTest();
        values[0] = static_cast<float>(res.state);
        values[1] = res.measuredLatencyMs;
        values[2] = res.correlationConfidence;
    }
    env->SetFloatArrayRegion(arr, 0, 3, values);
    return arr;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_audio_NativeAudioBridge_nativeGetTelemetry(JNIEnv* env, jobject) {
    constexpr int kFieldCount = 23;
    jfloatArray arr = env->NewFloatArray(kFieldCount);
    if (arr == nullptr) return nullptr;

    float out[kFieldCount] = {};
    if (gEngine) {
        const auto s = gEngine->getTelemetry();
        out[0] = s.isRunning ? 1.0f : 0.0f;
        out[1] = s.inputStreamOpen ? 1.0f : 0.0f;
        out[2] = s.outputStreamOpen ? 1.0f : 0.0f;
        out[3] = s.inputIsAAudio ? 1.0f : 0.0f;
        out[4] = s.outputIsAAudio ? 1.0f : 0.0f;
        out[5] = s.inputIsExclusive ? 1.0f : 0.0f;
        out[6] = s.outputIsExclusive ? 1.0f : 0.0f;
        out[7] = static_cast<float>(s.sampleRate);
        out[8] = static_cast<float>(s.inputFramesPerBurst);
        out[9] = static_cast<float>(s.outputFramesPerBurst);
        out[10] = static_cast<float>(s.outputBufferSizeFrames);
        out[11] = static_cast<float>(s.ringBufferedFrames);
        out[12] = static_cast<float>(s.inputXRunCount);
        out[13] = static_cast<float>(s.outputXRunCount);
        out[14] = static_cast<float>(s.ringOverruns);
        out[15] = static_cast<float>(s.ringUnderruns);
        out[16] = s.dspAvgTimeUs;
        out[17] = s.dspMaxTimeUs;
        out[18] = s.estimatedPipelineLatencyMs;
        out[19] = s.inputLevelDbfs;
        out[20] = s.outputLevelDbfs;
        out[21] = s.limiterActive ? 1.0f : 0.0f;
        out[22] = s.streamErrorOccurred ? 1.0f : 0.0f;
    }
    env->SetFloatArrayRegion(arr, 0, kFieldCount, out);
    return arr;
}

} // extern "C"
