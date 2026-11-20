#include "LoopbackDetector.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace hearlink {

namespace {
constexpr float kTwoPi = 6.28318530717958647692f;
}

void LoopbackDetector::prepare(int32_t inputSampleRate, int32_t outputSampleRate) {
    inputSampleRate_ = std::max(8000, inputSampleRate);
    outputSampleRate_ = std::max(8000, outputSampleRate);

    emitLength_ = std::clamp((outputSampleRate_ * 15) / 1000, 120, kMaxReferenceSamples);
    refLength_ = std::clamp((inputSampleRate_ * 15) / 1000, 120, kMaxReferenceSamples);
    captureTarget_ = std::clamp((inputSampleRate_ * 850) / 1000, refLength_ * 2, kMaxCaptureSamples);

    // Safe -12 dBFS (amplitude 0.25) Hann-windowed 1800 Hz ping generated at both rates
    constexpr float kAmplitude = 0.25f;
    constexpr float kFreqHz = 1800.0f;

    for (int32_t i = 0; i < emitLength_; ++i) {
        const float hann = 0.5f * (1.0f - std::cos(kTwoPi * static_cast<float>(i) / static_cast<float>(emitLength_ - 1)));
        const float tone = std::sin(kTwoPi * kFreqHz * static_cast<float>(i) / static_cast<float>(outputSampleRate_));
        emitBurst_[i] = kAmplitude * hann * tone;
    }

    for (int32_t i = 0; i < refLength_; ++i) {
        const float hann = 0.5f * (1.0f - std::cos(kTwoPi * static_cast<float>(i) / static_cast<float>(refLength_ - 1)));
        const float tone = std::sin(kTwoPi * kFreqHz * static_cast<float>(i) / static_cast<float>(inputSampleRate_));
        correlationRef_[i] = kAmplitude * hann * tone;
    }
}

void LoopbackDetector::startMeasurement() {
    emitCursor_ = 0;
    captureCursor_ = 0;
    emitTimestampNs_.store(0, std::memory_order_relaxed);
    firstCaptureTimestampNs_.store(0, std::memory_order_relaxed);
    measuredLatencyMs_ = -1.0f;
    confidence_ = 0.0f;
    state_.store(static_cast<int32_t>(LoopbackState::PendingEmit), std::memory_order_release);
}

void LoopbackDetector::cancelMeasurement() {
    state_.store(static_cast<int32_t>(LoopbackState::Idle), std::memory_order_release);
}

bool LoopbackDetector::processOutput(float* outputBuffer, int32_t numFrames, int64_t nowNs) {
    const int32_t s = state_.load(std::memory_order_acquire);
    if (s != static_cast<int32_t>(LoopbackState::PendingEmit) &&
        s != static_cast<int32_t>(LoopbackState::Capturing)) {
        return false;
    }

    if (emitCursor_ == 0) {
        emitTimestampNs_.store(nowNs, std::memory_order_release);
        state_.store(static_cast<int32_t>(LoopbackState::Capturing), std::memory_order_release);
    }

    std::memset(outputBuffer, 0, static_cast<size_t>(numFrames) * sizeof(float));

    if (emitCursor_ < emitLength_) {
        const int32_t remaining = emitLength_ - emitCursor_;
        const int32_t toCopy = std::min(numFrames, remaining);
        std::memcpy(outputBuffer, emitBurst_.data() + emitCursor_, static_cast<size_t>(toCopy) * sizeof(float));
        emitCursor_ += toCopy;
    }
    return true;
}

void LoopbackDetector::processInput(const float* inputBuffer, int32_t numFrames, int64_t nowNs) {
    const int32_t s = state_.load(std::memory_order_acquire);
    if (s != static_cast<int32_t>(LoopbackState::Capturing)) {
        return;
    }

    if (captureCursor_ == 0) {
        const int64_t blockDurationNs = (static_cast<int64_t>(numFrames) * 1000000000LL) / inputSampleRate_;
        firstCaptureTimestampNs_.store(nowNs - blockDurationNs, std::memory_order_release);
    }

    const int32_t remaining = captureTarget_ - captureCursor_;
    if (remaining > 0) {
        const int32_t toCopy = std::min(numFrames, remaining);
        std::memcpy(captureBuffer_.data() + captureCursor_, inputBuffer, static_cast<size_t>(toCopy) * sizeof(float));
        captureCursor_ += toCopy;
    }

    if (captureCursor_ >= captureTarget_) {
        state_.store(static_cast<int32_t>(LoopbackState::ReadyToAnalyze), std::memory_order_release);
    }
}

LoopbackResult LoopbackDetector::pollAndAnalyze() {
    const int32_t s = state_.load(std::memory_order_acquire);
    if (s == static_cast<int32_t>(LoopbackState::ReadyToAnalyze)) {
        analyzeCapturedWindow();
    }
    LoopbackResult result;
    result.state = state_.load(std::memory_order_acquire);
    result.measuredLatencyMs = measuredLatencyMs_;
    result.correlationConfidence = confidence_;
    return result;
}

void LoopbackDetector::analyzeCapturedWindow() {
    double refEnergy = 0.0;
    for (int32_t j = 0; j < refLength_; ++j) {
        refEnergy += static_cast<double>(correlationRef_[j]) * correlationRef_[j];
    }
    if (refEnergy <= 1e-9) {
        state_.store(static_cast<int32_t>(LoopbackState::Failed), std::memory_order_release);
        return;
    }

    const int32_t searchLimit = captureTarget_ - refLength_;
    const int32_t minLag = std::clamp((inputSampleRate_ * 5) / 1000, 0, searchLimit);

    double bestCorr = 0.0;
    int32_t bestOffset = -1;

    for (int32_t offset = minLag; offset < searchLimit; ++offset) {
        double dot = 0.0;
        double winEnergy = 0.0;
        for (int32_t j = 0; j < refLength_; ++j) {
            const double sample = captureBuffer_[offset + j];
            dot += sample * correlationRef_[j];
            winEnergy += sample * sample;
        }
        if (winEnergy > 1e-7) {
            const double normCorr = dot / std::sqrt(refEnergy * winEnergy);
            if (normCorr > bestCorr) {
                bestCorr = normCorr;
                bestOffset = offset;
            }
        }
    }

    if (bestOffset >= 0 && bestCorr >= 0.42) {
        const int64_t emitNs = emitTimestampNs_.load(std::memory_order_acquire);
        const int64_t capStartNs = firstCaptureTimestampNs_.load(std::memory_order_acquire);
        const int64_t peakOffsetNs = (static_cast<int64_t>(bestOffset) * 1000000000LL) / inputSampleRate_;
        const double totalMs = static_cast<double>((capStartNs + peakOffsetNs) - emitNs) / 1e6;

        if (totalMs >= 5.0 && totalMs <= 900.0) {
            measuredLatencyMs_ = static_cast<float>(totalMs);
            confidence_ = static_cast<float>(bestCorr);
            state_.store(static_cast<int32_t>(LoopbackState::Completed), std::memory_order_release);
            return;
        }
    }

    measuredLatencyMs_ = -1.0f;
    confidence_ = static_cast<float>(bestCorr);
    state_.store(static_cast<int32_t>(LoopbackState::Failed), std::memory_order_release);
}

} // namespace hearlink
