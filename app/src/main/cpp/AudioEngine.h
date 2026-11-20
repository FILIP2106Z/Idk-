#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <oboe/Oboe.h>

#include "DspPipeline.h"
#include "LockFreeRingBuffer.h"
#include "LoopbackDetector.h"

namespace hearlink {

enum class OutputUsageMode : int32_t {
    Communication = 0, // LE Audio / HFP
    GameLowLatency = 1,// Low-latency A2DP / Media path
    MediaStandard = 2  // Standard Media path
};

enum class InputPresetMode : int32_t {
    Unprocessed = 0,
    VoiceRecognition = 1,
    VoiceCommunication = 2,
    Generic = 3
};

struct EngineTelemetrySnapshot {
    bool isRunning = false;
    bool inputStreamOpen = false;
    bool outputStreamOpen = false;
    bool inputIsAAudio = false;
    bool outputIsAAudio = false;
    bool inputIsExclusive = false;
    bool outputIsExclusive = false;
    int32_t inputSampleRate = 0;
    int32_t outputSampleRate = 0;
    int32_t inputFramesPerBurst = 0;
    int32_t outputFramesPerBurst = 0;
    int32_t inputBufferSizeFrames = 0;
    int32_t outputBufferSizeFrames = 0;
    int32_t ringBufferedFrames = 0;
    int64_t inputXRunCount = 0;
    int64_t outputXRunCount = 0;
    int64_t ringOverruns = 0;
    int64_t ringUnderruns = 0;
    float dspAvgTimeUs = 0.0f;
    float dspMaxTimeUs = 0.0f;
    float inputBufferLatencyMs = 0.0f;
    float ringBufferLatencyMs = 0.0f;
    float outputBufferLatencyMs = 0.0f;
    float estimatedPipelineLatencyMs = -1.0f;
    float inputLevelDbfs = -90.0f;
    float outputLevelDbfs = -90.0f;
    bool limiterActive = false;
    bool streamErrorOccurred = false;
    int32_t autoGuardBufferMultiplier = 1;
    int32_t autoGuardAdjustments = 0;
};

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool start(
        int32_t inputDeviceId,
        int32_t outputDeviceId,
        int32_t usageMode,
        int32_t inputPresetMode,
        bool ultraLowLatencyMode
    );
    void stop();
    void setOutputMuted(bool muted);

    void updateDspConfig(
        float micSensitivityDb,
        float outputVolumeGain,
        float maxOutputLimitDb,
        float softStartRampMs,
        bool highPassEnabled,
        float highPassCutoffHz,
        bool noiseSuppressionEnabled,
        float noiseSuppressionStrength,
        bool speechClarityEnabled,
        float speechClarityStrength,
        bool agcEnabled,
        float agcTargetDb
    );

    void startLoopbackTest();
    void cancelLoopbackTest();
    LoopbackResult pollLoopbackTest();

    EngineTelemetrySnapshot getTelemetry();

private:
    class InputCallback : public oboe::AudioStreamDataCallback {
    public:
        explicit InputCallback(AudioEngine& engine) : engine_(engine) {}
        oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* audioStream,
            void* audioData,
            int32_t numFrames) override;
    private:
        AudioEngine& engine_;
    };

    class OutputCallback : public oboe::AudioStreamDataCallback {
    public:
        explicit OutputCallback(AudioEngine& engine) : engine_(engine) {}
        oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* audioStream,
            void* audioData,
            int32_t numFrames) override;
    private:
        AudioEngine& engine_;
    };

    class StreamErrorHandler : public oboe::AudioStreamErrorCallback {
    public:
        StreamErrorHandler(AudioEngine& engine, bool isInput)
            : engine_(engine), isInput_(isInput) {}
        void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;
    private:
        AudioEngine& engine_;
        bool isInput_;
    };

    bool openInputStream(int32_t deviceId, int32_t inputPresetMode);
    bool openOutputStream(int32_t deviceId, int32_t usageMode);
    void closeStreamsLocked();
    void evaluateAutoXRunGuardLocked(int64_t totalUnderrunsNow);
    void readFromRingWithResample(float* out, int32_t numFrames);

    std::mutex lifecycleMutex_;
    std::shared_ptr<oboe::AudioStream> inputStream_;
    std::shared_ptr<oboe::AudioStream> outputStream_;

    std::unique_ptr<InputCallback> inputCallback_;
    std::unique_ptr<OutputCallback> outputCallback_;
    std::unique_ptr<StreamErrorHandler> inputErrorCallback_;
    std::unique_ptr<StreamErrorHandler> outputErrorCallback_;

    LockFreeRingBuffer<16384> ringBuffer_;
    DspPipeline dspPipeline_;
    DspAtomicConfig dspConfig_;
    LoopbackDetector loopbackDetector_;

    std::atomic<bool> isRunning_{false};
    std::atomic<bool> isMuted_{false};
    std::atomic<bool> needsStartupSync_{true};
    std::atomic<bool> streamErrorFlag_{false};

    int32_t inputSampleRate_ = 48000;
    int32_t outputSampleRate_ = 48000;
    int32_t targetJitterFrames_ = 128;
    int32_t maxJitterFrames_ = 384;
    bool ultraLowLatencyMode_ = true;

    // Resampler state (only used inside OutputCallback when inputSampleRate_ != outputSampleRate_)
    std::array<float, 4096> resamplerScratch_{};
    double resamplerPhase_ = 0.0;
    float resamplerPrevSample_ = 0.0f;
    float resamplerCurrSample_ = 0.0f;
    bool resamplerPrimed_ = false;

    // Auto-XRun Guard state (checked off-audio-thread in getTelemetry)
    int32_t bufferMultiplier_ = 1;
    int32_t autoGuardAdjustments_ = 0;
    int64_t guardWindowStartMs_ = 0;
    int64_t guardWindowBaseUnderruns_ = 0;

    std::atomic<int64_t> ringOverruns_{0};
    std::atomic<int64_t> ringUnderruns_{0};
    std::atomic<float> dspAvgTimeUs_{0.0f};
    std::atomic<float> dspMaxTimeUs_{0.0f};
    std::atomic<float> inputLevelLinear_{0.0f};
    std::atomic<float> outputLevelLinear_{0.0f};
    std::atomic<bool> limiterActive_{false};
};

} // namespace hearlink
