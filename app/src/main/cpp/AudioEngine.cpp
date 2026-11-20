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

int64_t nowMonotonicMs() {
    return nowMonotonicNs() / 1000000LL;
}

float linearToDbfs(float linear) {
    if (linear <= 1e-5f) return -90.0f;
    return std::clamp(20.0f * std::log10(linear), -90.0f, 0.0f);
}

oboe::InputPreset mapInputPreset(int32_t presetMode) {
    switch (static_cast<InputPresetMode>(presetMode)) {
        case InputPresetMode::Unprocessed:
            return oboe::InputPreset::Unprocessed;
        case InputPresetMode::VoiceRecognition:
            return oboe::InputPreset::VoiceRecognition;
        case InputPresetMode::VoiceCommunication:
            return oboe::InputPreset::VoiceCommunication;
        case InputPresetMode::Generic:
        default:
            return oboe::InputPreset::Generic;
    }
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

bool AudioEngine::start(
    int32_t inputDeviceId,
    int32_t outputDeviceId,
    int32_t usageMode,
    int32_t inputPresetMode,
    bool ultraLowLatencyMode
) {
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    closeStreamsLocked();

    ultraLowLatencyMode_ = ultraLowLatencyMode;
    ringBuffer_.reset();
    ringOverruns_.store(0, std::memory_order_relaxed);
    ringUnderruns_.store(0, std::memory_order_relaxed);
    dspAvgTimeUs_.store(0.0f, std::memory_order_relaxed);
    dspMaxTimeUs_.store(0.0f, std::memory_order_relaxed);
    streamErrorFlag_.store(false, std::memory_order_relaxed);
    isMuted_.store(false, std::memory_order_relaxed);
    needsStartupSync_.store(true, std::memory_order_release);

    resamplerPhase_ = 0.0;
    resamplerPrevSample_ = 0.0f;
    resamplerCurrSample_ = 0.0f;
    resamplerPrimed_ = false;

    if (!openInputStream(inputDeviceId, inputPresetMode)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to open input stream");
        closeStreamsLocked();
        return false;
    }

    inputSampleRate_ = std::max(8000, inputStream_->getSampleRate());

    if (!openOutputStream(outputDeviceId, usageMode)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Failed to open output stream");
        closeStreamsLocked();
        return false;
    }

    outputSampleRate_ = std::max(8000, outputStream_->getSampleRate());

    // Ultra-low latency starts at 1 burst; balanced mode starts at 2 bursts
    const int32_t outBurst = std::max(48, outputStream_->getFramesPerBurst());
    const int32_t outCapacity = std::max(outBurst, outputStream_->getBufferCapacityInFrames());
    bufferMultiplier_ = ultraLowLatencyMode_ ? 1 : 2;
    autoGuardAdjustments_ = 0;
    guardWindowStartMs_ = nowMonotonicMs();
    guardWindowBaseUnderruns_ = 0;

    const int32_t desiredOutBuffer = std::min(outBurst * bufferMultiplier_, outCapacity);
    outputStream_->setBufferSizeInFrames(desiredOutBuffer);

    const int32_t inBurst = std::max(48, inputStream_->getFramesPerBurst());
    // Set input stream buffer tight as well when HAL permits
    const int32_t inCap = std::max(inBurst, inputStream_->getBufferCapacityInFrames());
    inputStream_->setBufferSizeInFrames(std::min(inBurst * (ultraLowLatencyMode_ ? 1 : 2), inCap));

    const int32_t baseBurstIn = std::max(inBurst, (outBurst * inputSampleRate_) / outputSampleRate_);
    if (ultraLowLatencyMode_) {
        // Target ~1 burst (~2-4 ms), ceiling ~2 bursts + 64 frames (~6-10 ms)
        targetJitterFrames_ = baseBurstIn;
        maxJitterFrames_ = std::max(baseBurstIn * 2 + 64, (inputSampleRate_ * 8) / 1000);
    } else {
        targetJitterFrames_ = std::max(baseBurstIn * 2, (inputSampleRate_ * 10) / 1000);
        maxJitterFrames_ = std::max(baseBurstIn * 4, (inputSampleRate_ * 25) / 1000);
    }

    dspPipeline_.prepare(static_cast<float>(outputSampleRate_));
    dspPipeline_.triggerSoftStart();
    loopbackDetector_.prepare(inputSampleRate_, outputSampleRate_);

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
        "Started HearLink UltraLow=%d inSR=%d outSR=%d inAPI=%d outAPI=%d inEx=%d outEx=%d inBurst=%d outBurst=%d",
        ultraLowLatencyMode_ ? 1 : 0,
        inputSampleRate_, outputSampleRate_,
        static_cast<int>(inputStream_->getAudioApi()),
        static_cast<int>(outputStream_->getAudioApi()),
        inputStream_->getSharingMode() == oboe::SharingMode::Exclusive ? 1 : 0,
        outputStream_->getSharingMode() == oboe::SharingMode::Exclusive ? 1 : 0,
        inBurst, outBurst
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

bool AudioEngine::openInputStream(int32_t deviceId, int32_t inputPresetMode) {
    const oboe::InputPreset preset = mapInputPreset(inputPresetMode);
    auto tryOpen = [&](oboe::SharingMode sharingMode, oboe::InputPreset chosenPreset) -> oboe::Result {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(sharingMode)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setInputPreset(chosenPreset)
            ->setDataCallback(inputCallback_.get())
            ->setErrorCallback(inputErrorCallback_.get());

        if (deviceId > 0) {
            builder.setDeviceId(deviceId);
        }
        return builder.openStream(inputStream_);
    };

    // 1. Try Exclusive with requested preset (e.g. Unprocessed)
    oboe::Result result = tryOpen(oboe::SharingMode::Exclusive, preset);
    if (result != oboe::Result::OK) {
        // 2. Try Shared with requested preset
        result = tryOpen(oboe::SharingMode::Shared, preset);
    }
    if (result != oboe::Result::OK && preset != oboe::InputPreset::Generic) {
        // 3. Fallback to Generic preset if OEM HAL rejects Unprocessed on a specific mic
        result = tryOpen(oboe::SharingMode::Shared, oboe::InputPreset::Generic);
    }
    return result == oboe::Result::OK && inputStream_ != nullptr;
}

bool AudioEngine::openOutputStream(int32_t deviceId, int32_t usageMode) {
    const oboe::Usage oboeUsage = [&]() {
        switch (static_cast<OutputUsageMode>(usageMode)) {
            case OutputUsageMode::GameLowLatency:
                return oboe::Usage::Game;
            case OutputUsageMode::MediaStandard:
                return oboe::Usage::Media;
            case OutputUsageMode::Communication:
            default:
                return oboe::Usage::VoiceCommunication;
        }
    }();
    const oboe::ContentType contentType = (oboeUsage == oboe::Usage::Game)
        ? oboe::ContentType::Sonification
        : oboe::ContentType::Speech;

    auto tryOpen = [&](oboe::SharingMode sharingMode) -> oboe::Result {
        oboe::AudioStreamBuilder builder;
        // Do NOT force sample rate -> keeps output on native FastMixer/MMAP low-latency path
        builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(sharingMode)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
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
        needsStartupSync_.store(true, std::memory_order_release);
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

void AudioEngine::readFromRingWithResample(float* out, int32_t numFrames) {
    if (inputSampleRate_ == outputSampleRate_) {
        const size_t readCount = ringBuffer_.read(out, static_cast<size_t>(numFrames));
        if (readCount < static_cast<size_t>(numFrames)) {
            std::memset(out + readCount, 0, (static_cast<size_t>(numFrames) - readCount) * sizeof(float));
            ringUnderruns_.fetch_add(1, std::memory_order_relaxed);
        }
        return;
    }

    // Lock-free linear resampler when mic rate (e.g. 48kHz) differs from BT output rate (e.g. 16kHz HFP / 44.1kHz A2DP)
    const double step = static_cast<double>(inputSampleRate_) / static_cast<double>(outputSampleRate_);
    if (!resamplerPrimed_) {
        float initial[2] = {0.0f, 0.0f};
        const size_t got = ringBuffer_.read(initial, 2);
        if (got == 0) {
            std::memset(out, 0, static_cast<size_t>(numFrames) * sizeof(float));
            ringUnderruns_.fetch_add(1, std::memory_order_relaxed);
            return;
        }
        resamplerPrevSample_ = initial[0];
        resamplerCurrSample_ = (got > 1) ? initial[1] : initial[0];
        resamplerPhase_ = 0.0;
        resamplerPrimed_ = true;
    }

    bool underrunFlag = false;
    for (int32_t i = 0; i < numFrames; ++i) {
        while (resamplerPhase_ >= 1.0) {
            resamplerPhase_ -= 1.0;
            resamplerPrevSample_ = resamplerCurrSample_;
            float nextSample = 0.0f;
            if (ringBuffer_.read(&nextSample, 1) == 0) {
                nextSample = 0.0f;
                underrunFlag = true;
            }
            resamplerCurrSample_ = nextSample;
        }
        const float frac = static_cast<float>(resamplerPhase_);
        out[i] = resamplerPrevSample_ + frac * (resamplerCurrSample_ - resamplerPrevSample_);
        resamplerPhase_ += step;
    }
    if (underrunFlag) {
        ringUnderruns_.fetch_add(1, std::memory_order_relaxed);
    }
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

    const size_t neededInFrames = static_cast<size_t>(
        std::ceil(static_cast<double>(numFrames) * engine_.inputSampleRate_ / engine_.outputSampleRate_)
    );

    // Zero-Lag startup sync: drop accumulated pre-output frames on first callback
    if (engine_.needsStartupSync_.exchange(false, std::memory_order_acq_rel)) {
        const size_t avail = engine_.ringBuffer_.availableToRead();
        const size_t desiredStart = neededInFrames + static_cast<size_t>(engine_.targetJitterFrames_);
        if (avail > desiredStart) {
            engine_.ringBuffer_.dropOldest(avail - desiredStart);
        }
    }

    // Strict drift control to keep ring queue within 1-2 bursts
    const size_t available = engine_.ringBuffer_.availableToRead();
    if (available > static_cast<size_t>(engine_.maxJitterFrames_) + neededInFrames) {
        const size_t drop = available - (static_cast<size_t>(engine_.targetJitterFrames_) + neededInFrames);
        engine_.ringBuffer_.dropOldest(drop);
    }

    engine_.readFromRingWithResample(out, numFrames);

    const DspMetrics metrics = engine_.dspPipeline_.processBlock(out, numFrames, engine_.dspConfig_);

    const int64_t endNs = nowMonotonicNs();
    const float elapsedUs = static_cast<float>(endNs - startNs) / 1000.0f;

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
    engine_.streamErrorFlag_.store(true, std::memory_order_release);
}

void AudioEngine::evaluateAutoXRunGuardLocked(int64_t totalUnderrunsNow) {
    if (!ultraLowLatencyMode_ || !outputStream_ || !isRunning_.load(std::memory_order_acquire)) {
        return;
    }
    const int64_t nowMs = nowMonotonicMs();
    if (nowMs - guardWindowStartMs_ >= 2000) {
        const int64_t delta = totalUnderrunsNow - guardWindowBaseUnderruns_;
        if (delta >= 3 && bufferMultiplier_ < 4) {
            bufferMultiplier_++;
            autoGuardAdjustments_++;
            const int32_t outBurst = std::max(48, outputStream_->getFramesPerBurst());
            const int32_t outCap = std::max(outBurst, outputStream_->getBufferCapacityInFrames());
            const int32_t newSize = std::min(outBurst * bufferMultiplier_, outCap);
            outputStream_->setBufferSizeInFrames(newSize);

            const int32_t inBurst = inputStream_ ? std::max(48, inputStream_->getFramesPerBurst()) : outBurst;
            const int32_t stepIn = std::max(inBurst, (outBurst * inputSampleRate_) / outputSampleRate_);
            targetJitterFrames_ += stepIn / 2;
            maxJitterFrames_ += stepIn;

            __android_log_print(
                ANDROID_LOG_INFO, kTag,
                "Auto-XRun Guard stepped buffer to %dx (%d frames) after %lld underruns",
                bufferMultiplier_, newSize, static_cast<long long>(delta)
            );
        }
        guardWindowStartMs_ = nowMs;
        guardWindowBaseUnderruns_ = totalUnderrunsNow;
    }
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
    snap.ringBufferedFrames = static_cast<int32_t>(ringBuffer_.availableToRead());

    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    if (inputStream_) {
        snap.inputStreamOpen = (inputStream_->getState() == oboe::StreamState::Started ||
                                inputStream_->getState() == oboe::StreamState::Starting);
        snap.inputIsAAudio = (inputStream_->getAudioApi() == oboe::AudioApi::AAudio);
        snap.inputIsExclusive = (inputStream_->getSharingMode() == oboe::SharingMode::Exclusive);
        snap.inputSampleRate = inputStream_->getSampleRate();
        snap.inputFramesPerBurst = inputStream_->getFramesPerBurst();
        snap.inputBufferSizeFrames = inputStream_->getBufferSizeInFrames();
        auto xruns = inputStream_->getXRunCount();
        snap.inputXRunCount = xruns ? xruns.value() : 0;
    }
    if (outputStream_) {
        snap.outputStreamOpen = (outputStream_->getState() == oboe::StreamState::Started ||
                                 outputStream_->getState() == oboe::StreamState::Starting);
        snap.outputIsAAudio = (outputStream_->getAudioApi() == oboe::AudioApi::AAudio);
        snap.outputIsExclusive = (outputStream_->getSharingMode() == oboe::SharingMode::Exclusive);
        snap.outputSampleRate = outputStream_->getSampleRate();
        snap.outputFramesPerBurst = outputStream_->getFramesPerBurst();
        snap.outputBufferSizeFrames = outputStream_->getBufferSizeInFrames();
        auto xruns = outputStream_->getXRunCount();
        snap.outputXRunCount = xruns ? xruns.value() : 0;
    }

    evaluateAutoXRunGuardLocked(snap.ringUnderruns + snap.outputXRunCount);
    snap.autoGuardBufferMultiplier = bufferMultiplier_;
    snap.autoGuardAdjustments = autoGuardAdjustments_;

    if (snap.isRunning && snap.inputSampleRate > 0 && snap.outputSampleRate > 0) {
        const int32_t effInFrames = (snap.inputBufferSizeFrames > 0)
            ? std::min(snap.inputBufferSizeFrames, snap.inputFramesPerBurst * 2)
            : snap.inputFramesPerBurst;
        snap.inputBufferLatencyMs = (static_cast<float>(effInFrames) * 1000.0f) / static_cast<float>(snap.inputSampleRate);
        snap.ringBufferLatencyMs = (static_cast<float>(snap.ringBufferedFrames) * 1000.0f) / static_cast<float>(snap.inputSampleRate);
        snap.outputBufferLatencyMs = (static_cast<float>(snap.outputBufferSizeFrames) * 1000.0f) / static_cast<float>(snap.outputSampleRate);
        snap.estimatedPipelineLatencyMs =
            snap.inputBufferLatencyMs +
            snap.ringBufferLatencyMs +
            snap.outputBufferLatencyMs +
            (snap.dspAvgTimeUs / 1000.0f);
    }

    return snap;
}

} // namespace hearlink
