#pragma once

#include <array>
#include <atomic>
#include <cstdint>

namespace hearlink {

enum class LoopbackState : int32_t {
    Idle = 0,
    PendingEmit = 1,
    Capturing = 2,
    ReadyToAnalyze = 3,
    Completed = 4,
    Failed = 5
};

struct LoopbackResult {
    int32_t state = 0;
    float measuredLatencyMs = -1.0f;
    float correlationConfidence = 0.0f;
};

/**
 * Controlled acoustic impulse loopback latency analyzer.
 * Supports independent input and output sample rates (e.g. 48 kHz mic + 16 kHz HFP or 44.1 kHz A2DP).
 */
class LoopbackDetector {
public:
    static constexpr int32_t kMaxReferenceSamples = 1024;
    static constexpr int32_t kMaxCaptureSamples = 48000; // 1.0 s at 48 kHz

    void prepare(int32_t inputSampleRate, int32_t outputSampleRate);
    void startMeasurement();
    void cancelMeasurement();

    bool processOutput(float* outputBuffer, int32_t numFrames, int64_t nowNs);
    void processInput(const float* inputBuffer, int32_t numFrames, int64_t nowNs);
    LoopbackResult pollAndAnalyze();

private:
    void analyzeCapturedWindow();

    int32_t inputSampleRate_ = 48000;
    int32_t outputSampleRate_ = 48000;
    int32_t emitLength_ = 720;       // 15 ms at outputSampleRate
    int32_t refLength_ = 720;        // 15 ms at inputSampleRate
    int32_t captureTarget_ = 40800;  // 850 ms window at inputSampleRate

    std::array<float, kMaxReferenceSamples> emitBurst_{};
    std::array<float, kMaxReferenceSamples> correlationRef_{};
    std::array<float, kMaxCaptureSamples> captureBuffer_{};

    std::atomic<int32_t> state_{static_cast<int32_t>(LoopbackState::Idle)};
    int32_t emitCursor_ = 0;
    int32_t captureCursor_ = 0;
    std::atomic<int64_t> emitTimestampNs_{0};
    std::atomic<int64_t> firstCaptureTimestampNs_{0};

    float measuredLatencyMs_ = -1.0f;
    float confidence_ = 0.0f;
};

} // namespace hearlink
