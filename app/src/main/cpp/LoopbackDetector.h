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
 * Emits a brief, safe Hann-windowed 1800 Hz ping burst on the output stream
 * and records the microphone capture window to compute normalized cross-correlation
 * off the real-time audio thread.
 */
class LoopbackDetector {
public:
    static constexpr int32_t kMaxReferenceSamples = 1024;
    static constexpr int32_t kMaxCaptureSamples = 48000; // 1.0 s at 48 kHz

    void prepare(int32_t sampleRate);
    void startMeasurement();
    void cancelMeasurement();

    /**
     * Called from output audio callback (real-time safe, zero locks/allocations).
     * Returns true if it overwrote the output buffer with the test ping.
     */
    bool processOutput(float* outputBuffer, int32_t numFrames, int64_t nowNs);

    /**
     * Called from input audio callback (real-time safe, zero locks/allocations).
     */
    void processInput(const float* inputBuffer, int32_t numFrames, int64_t nowNs);

    /**
     * Called from non-audio thread (JNI polling) to finalize cross-correlation and read status.
     */
    LoopbackResult pollAndAnalyze();

private:
    void analyzeCapturedWindow();

    int32_t sampleRate_ = 48000;
    int32_t refLength_ = 720;     // 15 ms at 48 kHz
    int32_t captureTarget_ = 40800; // 850 ms window

    std::array<float, kMaxReferenceSamples> referenceBurst_{};
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
