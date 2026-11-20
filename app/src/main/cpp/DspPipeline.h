#pragma once

#include <atomic>
#include <cstdint>

namespace hearlink {

struct DspAtomicConfig {
    std::atomic<float> micSensitivityDb{4.0f};
    std::atomic<float> outputVolumeGain{0.80f};
    std::atomic<float> maxOutputLimitDb{-3.0f};
    std::atomic<float> softStartRampMs{800.0f};

    std::atomic<bool> highPassEnabled{true};
    std::atomic<float> highPassCutoffHz{90.0f};

    std::atomic<bool> noiseSuppressionEnabled{true};
    std::atomic<float> noiseSuppressionStrength{0.45f};

    std::atomic<bool> speechClarityEnabled{true};
    std::atomic<float> speechClarityStrength{0.50f};

    std::atomic<bool> agcEnabled{true};
    std::atomic<float> agcTargetDb{-18.0f};
};

struct DspMetrics {
    float inputPeakLinear = 0.0f;
    float inputRmsLinear = 0.0f;
    float outputPeakLinear = 0.0f;
    float outputRmsLinear = 0.0f;
    bool limiterEngaged = false;
};

class BiquadFilter {
public:
    void reset();
    void configureHighPass(float sampleRate, float cutoffHz, float q = 0.70710678f);
    void configurePeaking(float sampleRate, float centerHz, float q, float gainDb);
    void configureHighShelf(float sampleRate, float freqHz, float q, float gainDb);
    inline float process(float x);

private:
    float b0_ = 1.0f, b1_ = 0.0f, b2_ = 0.0f;
    float a1_ = 0.0f, a2_ = 0.0f;
    float z1_ = 0.0f, z2_ = 0.0f;
};

class DspPipeline {
public:
    void prepare(float sampleRate);
    void resetState();
    void triggerSoftStart();
    DspMetrics processBlock(float* samples, int32_t numFrames, const DspAtomicConfig& config);

private:
    void updateFiltersIfNeeded(
        bool hpEnabled, float hpCutoffHz,
        bool clarityEnabled, float clarityStrength);

    float sampleRate_ = 48000.0f;

    // Filter chain
    BiquadFilter hpf_;
    BiquadFilter presencePeak_;
    BiquadFilter airDeHarshShelf_;

    float lastHpCutoff_ = -1.0f;
    float lastClarityStrength_ = -1.0f;
    bool lastClarityEnabled_ = false;

    // Noise suppressor state (2-band stationary noise tracker)
    float splitLowState_ = 0.0f;
    float splitCoeff_ = 0.14f;
    float lowBandEnv_ = 0.0f;
    float highBandEnv_ = 0.0f;
    float lowNoiseFloor_ = 0.002f;
    float highNoiseFloor_ = 0.001f;
    float nsGainSmoothed_ = 1.0f;

    // AGC state
    float agcRmsSq_ = 0.0001f;
    float agcCurrentGain_ = 1.0f;

    // Safety limiter & soft start state
    float limiterEnv_ = 0.0f;
    float softStartProgress_ = 0.0f;
};

} // namespace hearlink
