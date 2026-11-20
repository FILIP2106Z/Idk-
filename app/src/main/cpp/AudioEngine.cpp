#include "AudioEngine.h"

#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

namespace hearlink {

namespace {
constexpr const char* kTag = "HearLinkAudio";

int64_t nowMonotonicNs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
}

float linearToDbfs(float linear) {
    if (linear <= 1e-5f) return -90.0f;
    return std::clamp(20.0f * std::log10(linear), -90.0f, 0.0f);
}
} // namespace

AudioEngine::AudioEngine() {
    inputCallback_ = std::make_unique<InputCallback>(*this);
    outputCallback_ = std::make_unique<OutputCallback>(*this);
    inputErrorCallback_ = std::make_unique<StreamErrorHandler>(*this, true);
    outputErrorCallback_ = std::make_unique<StreamErrorHandler>(*this, false);
}

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start(int32_t inputDeviceId, int32_t outputDeviceId, int32_t usageMode) {
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    closeStreamsLocked();

    ringBuffer_.reset();
    ringOverruns_.store(0, std::memory_order_relaxed);
    ringUnderruns_.store(0, std::memory_order_relaxed);
    dspAvgTimeUs_.store(0.0f, std::memory_order_relaxed);
    dspMaxTimeUs_.store(0.0f, std::memory_order_relaxed);
    streamErrorFlag_.store(false, std::memory_order_relaxed);
    lastInputError_.store(0, std::memory_order_relaxed);
    lastOutputError_.store(0, std::memory_order_relaxed);
    isMuted_.store(false, std::memory_order_relaxed);

    if (!openInputStream(inputDeviceId)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to open input stream");
        closeStreamsLocked();
        return false;
    }

    activeSampleRate_ = inputStream_->getSampleRate();
    if (activeSampleRate_ <= 0) {
        activeSampleRate_ = 48000;
    }

    if (!openOutputStream(outputDeviceId, activeSampleRate_, usageMode)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to open output stream");
        closeStreamsLocked();
        return false;
    }

    // Tune double-buffered output stream size for lowest stable latency
    const int32_t outBurst = std::max(64, outputStream_->getFramesPerBurst());
    const int32_t outCapacity = outputStream_->getBufferCapacityInFrames();
    const int32_t desiredOutBuffer = std::min(outBurst * 2, std::max(outBurst, outCapacity));
    outputStream_->setBufferSizeInFrames(desiredOutBuffer);

    const int32_t inBurst = std::max(64, inputStream_->getFramesPerBurst());
    const int32_t baseBurst = std::max(inBurst, outBurst);

    // Keep ring jitter target ~10 ms (or 2 bursts) and max ceiling ~40 ms (or 5 bursts)
    targetJitterFrames_ = std::max(baseBurst * 2, (activeSampleRate_ * 10) / 1000);
    maxJitterFrames_ = std::max(baseBurst * 5, (activeSampleRate_ * 40) / 1000);

    dspPipeline_.prepare(static_cast<float>(activeSampleRate_));
    dspPipeline_.triggerSoftStart();
    loopbackDetector_.prepare(activeSampleRate_);

    // Start input first so initial frames arrive before output drains
    oboe::Result inStart = inputStream_->requestStart();
    if (inStart != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Input requestStart failed: %s", oboe::convertToText(inStart));
        closeStreamsLocked();
        return false;
    }

    oboe::Result outStart = outputStream_->requestStart();
    if (outStart != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Output requestStart failed: %s", oboe::convertToText(outStart));
        closeStreamsLocked();
        return false;
    }

    isRunning_.store(true, std::memory_order_release);
    __android_log_print(
        ANDROID_LOG_INFO, kTag,
        "Started HearLink Oboe Engine: sr=%d, inAPI=%d, outAPI=%d, inBurst=%d, outBurst=%d",
        activeSampleRate_,
        static_cast<int>(inputStream_->getAudioApi()),
        static_cast<int>(outputStream_->getAudioApi()),
        inBurst,
        outBurst
    );
    return true;
}

void AudioEngine::stop() {
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    closeStreamsLocked();
}

void AudioEngine::closeStreamsLocked() {
    isRunning_.store(false, std::memory_order_release);
    loopbackDetector_.cancelMeasurement();

    if (outputStream_) {
        outputStream_->requestStop();
        outputStream_->close();
        outputStream_.reset();
    }
    if (inputStream_) {
        inputStream_->requestStop();
        inputStream_->close();
        inputStream_.reset();
    }
    inputLevelLinear_.store(0.0f, std::memory_order_relaxed);
    outputLevelLinear_.store(0.0f, std::memory_order_relaxed);
    limiterActive_.store(false, std::memory_order_relaxed);
}

bool AudioEngine::openInputStream(int32_t deviceId) {
    auto tryOpen = [&](oboe::SharingMode sharingMode) -> oboe::Result {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(sharingMode)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setInputPreset(oboe::InputPreset::VoiceRecognition)
            ->setDataCallback(inputCallback_.get())
            ->setErrorCallback(inputErrorCallback_.get());

        if (deviceId > 0) {
            builder.setDeviceId(deviceId);
        }
        return builder.openStream(inputStream_);
    };

    oboe::Result result = tryOpen(oboe::SharingMode::Exclusive);
    if (result != oboe::Result::OK) {
        result = tryOpen(oboe::SharingMode::Shared);
    }
    return result == oboe::Result::OK && inputStream_ != nullptr;
}

bool AudioEngine::openOutputStream(int32_t deviceId, int32_t sampleRate, int32_t usageMode) {
    const oboe::Usage oboeUsage = (usageMode == static_cast<int32_t>(OutputUsageMode::Media))
        ? oboe::Usage::Media
        : oboe::Usage::VoiceCommunication;
    const oboe::ContentType contentType = oboe::ContentType::Speech;

    auto tryOpen = [&](oboe::SharingMode sharingMode) -> oboe::Result {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(sharingMode)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(sampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setUsage(oboeUsage)
            ->setContentType(contentType)
            ->setDataCallback(outputCallback_.get())
            ->setErrorCallback(outputErrorCallback_.get());

        if (deviceId > 0) {
            builder.setDeviceId(deviceId);
        }
        return builder.openStream(outputStream_);
    };

    oboe::Result result = tryOpen(oboe::SharingMode::Exclusive);
    if (result != oboe::Result::OK) {
        result = tryOpen(oboe::SharingMode::Shared);
    }
    return result == oboe::Result::OK && outputStream_ != nullptr;
}

void AudioEngine::setOutputMuted(bool muted) {
    const bool prev = isMuted_.exchange(muted, std::memory_order_acq_rel);
    if (prev && !muted) {
        dspPipeline_.triggerSoftStart();
    }
}

void AudioEngine::updateDspConfig(
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
) {
    dspConfig_.micSensitivityDb.store(micSensitivityDb, std::memory_order_relaxed);
    dspConfig_.outputVolumeGain.store(outputVolumeGain, std::memory_order_relaxed);
    dspConfig_.maxOutputLimitDb.store(maxOutputLimitDb, std::memory_order_relaxed);
    dspConfig_.softStartRampMs.store(softStartRampMs, std::memory_order_relaxed);
    dspConfig_.highPassEnabled.store(highPassEnabled, std::memory_order_relaxed);
    dspConfig_.highPassCutoffHz.store(highPassCutoffHz, std::memory_order_relaxed);
    dspConfig_.noiseSuppressionEnabled.store(noiseSuppressionEnabled, std::memory_order_relaxed);
    dspConfig_.noiseSuppressionStrength.store(noiseSuppressionStrength, std::memory_order_relaxed);
    dspConfig_.speechClarityEnabled.store(speechClarityEnabled, std::memory_order_relaxed);
    dspConfig_.speechClarityStrength.store(speechClarityStrength, std::memory_order_relaxed);
    dspConfig_.agcEnabled.store(agcEnabled, std::memory_order_relaxed);
    dspConfig_.agcTargetDb.store(agcTargetDb, std::memory_order_relaxed);
}

void AudioEngine::startLoopbackTest() {
    if (isRunning_.load(std::memory_order_acquire)) {
        loopbackDetector_.startMeasurement();
    }
}

void AudioEngine::cancelLoopbackTest() {
    loopbackDetector_.cancelMeasurement();
}

LoopbackResult AudioEngine::pollLoopbackTest() {
    return loopbackDetector_.pollAndAnalyze();
}

oboe::DataCallbackResult AudioEngine::InputCallback::onAudioReady(
    oboe::AudioStream*,
    void* audioData,
    int32_t numFrames
) {
    if (!engine_.isRunning_.load(std::memory_order_acquire) || audioData == nullptr || numFrames <= 0) {
        return oboe::DataCallbackResult::Continue;
    }

    const auto* input = static_cast<const float*>(audioData);
    engine_.loopbackDetector_.processInput(input, numFrames, nowMonotonicNs());

    const size_t written = engine_.ringBuffer_.write(input, static_cast<size_t>(numFrames));
    if (written < static_cast<size_t>(numFrames)) {
        engine_.ringOverruns_.fetch_add(1, std::memory_order_relaxed);
    }

    return oboe::DataCallbackResult::Continue;
}

oboe::DataCallbackResult AudioEngine::OutputCallback::onAudioReady(
    oboe::AudioStream*,
    void* audioData,
    int32_t numFrames
) {
    if (audioData == nullptr || numFrames <= 0) {
        return oboe::DataCallbackResult::Continue;
    }

    auto* out = static_cast<float*>(audioData);

    if (!engine_.isRunning_.load(std::memory_order_acquire) ||
        engine_.isMuted_.load(std::memory_order_acquire)) {
        std::memset(out, 0, static_cast<size_t>(numFrames) * sizeof(float));
        engine_.outputLevelLinear_.store(0.0f, std::memory_order_relaxed);
        engine_.limiterActive_.store(false, std::memory_order_relaxed);
        return oboe::DataCallbackResult::Continue;
    }

    const int64_t startNs = nowMonotonicNs();

    if (engine_.loopbackDetector_.processOutput(out, numFrames, startNs)) {
        return oboe::DataCallbackResult::Continue;
    }

    // Adaptive drift control: keep queue bounded to prevent creeping latency
    const size_t available = engine_.ringBuffer_.availableToRead();
    if (available > static_cast<size_t>(engine_.maxJitterFrames_ + numFrames)) {
        const size_t drop = available - static_cast<size_t>(engine_.targetJitterFrames_ + numFrames);
        engine_.ringBuffer_.dropOldest(drop);
    }

    const size_t readCount = engine_.ringBuffer_.read(out, static_cast<size_t>(numFrames));
    if (readCount < static_cast<size_t>(numFrames)) {
        std::memset(out + readCount, 0, (static_cast<size_t>(numFrames) - readCount) * sizeof(float));
        engine_.ringUnderruns_.fetch_add(1, std::memory_order_relaxed);
    }

    const DspMetrics metrics = engine_.dspPipeline_.processBlock(out, numFrames, engine_.dspConfig_);

    const int64_t endNs = nowMonotonicNs();
    const float elapsedUs = static_cast<float>(endNs - startNs) / 1000.0f;

    // Exponential moving average for DSP execution time
    const float prevAvg = engine_.dspAvgTimeUs_.load(std::memory_order_relaxed);
    const float newAvg = (prevAvg <= 0.01f) ? elapsedUs : (0.9f * prevAvg + 0.1f * elapsedUs);
    engine_.dspAvgTimeUs_.store(newAvg, std::memory_order_relaxed);

    const float prevMax = engine_.dspMaxTimeUs_.load(std::memory_order_relaxed);
    if (elapsedUs > prevMax) {
        engine_.dspMaxTimeUs_.store(elapsedUs, std::memory_order_relaxed);
    }

    engine_.inputLevelLinear_.store(metrics.inputRmsLinear, std::memory_order_relaxed);
    engine_.outputLevelLinear_.store(metrics.outputRmsLinear, std::memory_order_relaxed);
    engine_.limiterActive_.store(metrics.limiterEngaged, std::memory_order_relaxed);

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::StreamErrorHandler::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
    __android_log_print(
        ANDROID_LOG_WARN, kTag,
        "%s stream disconnected/error: %s",
        isInput_ ? "Input" : "Output",
        oboe::convertToText(error)
    );
    if (isInput_) {
        engine_.lastInputError_.store(static_cast<int32_t>(error), std::memory_order_relaxed);
    } else {
        engine_.lastOutputError_.store(static_cast<int32_t>(error), std::memory_order_relaxed);
    }
    engine_.streamErrorFlag_.store(true, std::memory_order_release);
}

EngineTelemetrySnapshot AudioEngine::getTelemetry() {
    EngineTelemetrySnapshot snap;
    snap.isRunning = isRunning_.load(std::memory_order_acquire);
    snap.ringOverruns = ringOverruns_.load(std::memory_order_relaxed);
    snap.ringUnderruns = ringUnderruns_.load(std::memory_order_relaxed);
    snap.dspAvgTimeUs = dspAvgTimeUs_.load(std::memory_order_relaxed);
    snap.dspMaxTimeUs = dspMaxTimeUs_.load(std::memory_order_relaxed);
    snap.inputLevelDbfs = linearToDbfs(inputLevelLinear_.load(std::memory_order_relaxed));
    snap.outputLevelDbfs = linearToDbfs(outputLevelLinear_.load(std::memory_order_relaxed));
    snap.limiterActive = limiterActive_.load(std::memory_order_relaxed);
    snap.streamErrorOccurred = streamErrorFlag_.load(std::memory_order_acquire);
    snap.lastInputErrorCode = lastInputError_.load(std::memory_order_relaxed);
    snap.lastOutputErrorCode = lastOutputError_.load(std::memory_order_relaxed);
    snap.ringBufferedFrames = static_cast<int32_t>(ringBuffer_.availableToRead());

    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    if (inputStream_) {
        snap.inputStreamOpen = (inputStream_->getState() == oboe::StreamState::Started ||
                                inputStream_->getState() == oboe::StreamState::Starting);
        snap.inputIsAAudio = (inputStream_->getAudioApi() == oboe::AudioApi::AAudio);
        snap.inputIsExclusive = (inputStream_->getSharingMode() == oboe::SharingMode::Exclusive);
        snap.sampleRate = inputStream_->getSampleRate();
        snap.inputFramesPerBurst = inputStream_->getFramesPerBurst();
        auto xruns = inputStream_->getXRunCount();
        snap.inputXRunCount = xruns ? xruns.value() : 0;
    }
    if (outputStream_) {
        snap.outputStreamOpen = (outputStream_->getState() == oboe::StreamState::Started ||
                                 outputStream_->getState() == oboe::StreamState::Starting);
        snap.outputIsAAudio = (outputStream_->getAudioApi() == oboe::AudioApi::AAudio);
        snap.outputIsExclusive = (outputStream_->getSharingMode() == oboe::SharingMode::Exclusive);
        snap.outputFramesPerBurst = outputStream_->getFramesPerBurst();
        snap.outputBufferSizeFrames = outputStream_->getBufferSizeInFrames();
        auto xruns = outputStream_->getXRunCount();
        snap.outputXRunCount = xruns ? xruns.value() : 0;
    }

    if (snap.sampleRate > 0 && snap.isRunning) {
        // Software/audio-hal buffer latency ONLY (explicitly excluding unknown Bluetooth radio codec latency)
        const int32_t totalPipelineFrames = snap.inputFramesPerBurst + snap.outputBufferSizeFrames + snap.ringBufferedFrames;
        snap.estimatedPipelineLatencyMs = (static_cast<float>(totalPipelineFrames) * 1000.0f) / static_cast<float>(snap.sampleRate);
    }

    return snap;
}

} // namespace hearlink
