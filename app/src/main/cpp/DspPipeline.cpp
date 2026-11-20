#include "DspPipeline.h"

#include <algorithm>
#include <cmath>

namespace hearlink {

namespace {
constexpr float kPi = 3.14159265358979323846f;
constexpr float kEpsilon = 1e-9f;

inline float dbToLinear(float db) {
    return std::pow(10.0f, db / 20.0f);
}

inline float timeConstantCoeff(float sampleRate, float timeMs) {
    if (timeMs <= 0.01f) return 1.0f;
    const float samples = sampleRate * (timeMs * 0.001f);
    return 1.0f - std::exp(-1.0f / std::max(1.0f, samples));
}
} // namespace

void BiquadFilter::reset() {
    z1_ = 0.0f;
    z2_ = 0.0f;
}

void BiquadFilter::configureHighPass(float sampleRate, float cutoffHz, float q) {
    const float clampedFreq = std::clamp(cutoffHz, 20.0f, sampleRate * 0.45f);
    const float w0 = 2.0f * kPi * clampedFreq / sampleRate;
    const float cosW0 = std::cos(w0);
    const float sinW0 = std::sin(w0);
    const float alpha = sinW0 / (2.0f * q);

    const float a0 = 1.0f + alpha;
    b0_ = ((1.0f + cosW0) * 0.5f) / a0;
    b1_ = (-(1.0f + cosW0)) / a0;
    b2_ = ((1.0f + cosW0) * 0.5f) / a0;
    a1_ = (-2.0f * cosW0) / a0;
    a2_ = (1.0f - alpha) / a0;
}

void BiquadFilter::configurePeaking(float sampleRate, float centerHz, float q, float gainDb) {
    const float clampedFreq = std::clamp(centerHz, 40.0f, sampleRate * 0.45f);
    const float a = std::pow(10.0f, gainDb / 40.0f);
    const float w0 = 2.0f * kPi * clampedFreq / sampleRate;
    const float cosW0 = std::cos(w0);
    const float sinW0 = std::sin(w0);
    const float alpha = sinW0 / (2.0f * q);

    const float a0 = 1.0f + alpha / a;
    b0_ = (1.0f + alpha * a) / a0;
    b1_ = (-2.0f * cosW0) / a0;
    b2_ = (1.0f - alpha * a) / a0;
    a1_ = (-2.0f * cosW0) / a0;
    a2_ = (1.0f - alpha / a) / a0;
}

void BiquadFilter::configureHighShelf(float sampleRate, float freqHz, float q, float gainDb) {
    const float clampedFreq = std::clamp(freqHz, 100.0f, sampleRate * 0.45f);
    const float a = std::pow(10.0f, gainDb / 40.0f);
    const float w0 = 2.0f * kPi * clampedFreq / sampleRate;
    const float cosW0 = std::cos(w0);
    const float sinW0 = std::sin(w0);
    const float alpha = sinW0 / (2.0f * q);
    const float twoSqrtAAlpha = 2.0f * std::sqrt(a) * alpha;

    const float a0 = (a + 1.0f) - (a - 1.0f) * cosW0 + twoSqrtAAlpha;
    b0_ = (a * ((a + 1.0f) + (a - 1.0f) * cosW0 + twoSqrtAAlpha)) / a0;
    b1_ = (-2.0f * a * ((a - 1.0f) + (a + 1.0f) * cosW0)) / a0;
    b2_ = (a * ((a + 1.0f) + (a - 1.0f) * cosW0 - twoSqrtAAlpha)) / a0;
    a1_ = (2.0f * ((a - 1.0f) - (a + 1.0f) * cosW0)) / a0;
    a2_ = ((a + 1.0f) - (a - 1.0f) * cosW0 - twoSqrtAAlpha) / a0;
}

inline float BiquadFilter::process(float x) {
    const float y = b0_ * x + z1_;
    z1_ = b1_ * x - a1_ * y + z2_;
    z2_ = b2_ * x - a2_ * y;
    return y;
}

void DspPipeline::prepare(float sampleRate) {
    sampleRate_ = std::max(8000.0f, sampleRate);
    splitCoeff_ = 1.0f - std::exp(-2.0f * kPi * 1100.0f / sampleRate_);
    lastHpCutoff_ = -1.0f;
    lastClarityStrength_ = -1.0f;
    resetState();
}

void DspPipeline::resetState() {
    hpf_.reset();
    presencePeak_.reset();
    airDeHarshShelf_.reset();
    splitLowState_ = 0.0f;
    lowBandEnv_ = 0.0f;
    highBandEnv_ = 0.0f;
    lowNoiseFloor_ = 0.002f;
    highNoiseFloor_ = 0.001f;
    nsGainSmoothed_ = 1.0f;
    agcRmsSq_ = 0.0001f;
    agcCurrentGain_ = 1.0f;
    limiterEnv_ = 0.0f;
    softStartProgress_ = 0.0f;
}

void DspPipeline::triggerSoftStart() {
    softStartProgress_ = 0.0f;
}

void DspPipeline::updateFiltersIfNeeded(
    bool hpEnabled, float hpCutoffHz,
    bool clarityEnabled, float clarityStrength) {
    if (hpEnabled && std::abs(hpCutoffHz - lastHpCutoff_) > 0.5f) {
        hpf_.configureHighPass(sampleRate_, hpCutoffHz);
        lastHpCutoff_ = hpCutoffHz;
    }
    if (clarityEnabled != lastClarityEnabled_ ||
        std::abs(clarityStrength - lastClarityStrength_) > 0.01f) {
        const float strength = std::clamp(clarityStrength, 0.0f, 1.0f);
        const float presenceFreq = std::min(2400.0f, sampleRate_ * 0.30f);
        const float shelfGainDb = (sampleRate_ >= 20000.0f) ? (-strength * 2.5f) : 0.0f;
        // Subtle presence boost (+0 to +5.5 dB) + gentle >6.2 kHz roll-off on wideband routes
        presencePeak_.configurePeaking(sampleRate_, presenceFreq, 0.85f, strength * 5.5f);
        airDeHarshShelf_.configureHighShelf(sampleRate_, 6200.0f, 0.707f, shelfGainDb);
        lastClarityEnabled_ = clarityEnabled;
        lastClarityStrength_ = clarityStrength;
    }
}

DspMetrics DspPipeline::processBlock(float* samples, int32_t numFrames, const DspAtomicConfig& config) {
    DspMetrics metrics;
    if (numFrames <= 0 || samples == nullptr) {
        return metrics;
    }

    const float micGain = dbToLinear(std::clamp(config.micSensitivityDb.load(std::memory_order_relaxed), -12.0f, 24.0f));
    const float outputGain = std::clamp(config.outputVolumeGain.load(std::memory_order_relaxed), 0.0f, 1.25f);
    const float limitLinear = dbToLinear(std::clamp(config.maxOutputLimitDb.load(std::memory_order_relaxed), -24.0f, 0.0f));
    const float rampMs = std::clamp(config.softStartRampMs.load(std::memory_order_relaxed), 50.0f, 3000.0f);

    const bool hpEnabled = config.highPassEnabled.load(std::memory_order_relaxed);
    const float hpCutoff = std::clamp(config.highPassCutoffHz.load(std::memory_order_relaxed), 40.0f, 300.0f);
    const bool nsEnabled = config.noiseSuppressionEnabled.load(std::memory_order_relaxed);
    const float nsStrength = std::clamp(config.noiseSuppressionStrength.load(std::memory_order_relaxed), 0.0f, 1.0f);
    const bool clarityEnabled = config.speechClarityEnabled.load(std::memory_order_relaxed);
    const float clarityStrength = std::clamp(config.speechClarityStrength.load(std::memory_order_relaxed), 0.0f, 1.0f);
    const bool agcEnabled = config.agcEnabled.load(std::memory_order_relaxed);
    const float agcTargetLinear = dbToLinear(std::clamp(config.agcTargetDb.load(std::memory_order_relaxed), -28.0f, -8.0f));

    updateFiltersIfNeeded(hpEnabled, hpCutoff, clarityEnabled, clarityStrength);

    // Precompute smoothing coefficients
    const float envAttack = timeConstantCoeff(sampleRate_, 8.0f);
    const float envRelease = timeConstantCoeff(sampleRate_, 80.0f);
    const float floorRise = timeConstantCoeff(sampleRate_, 1800.0f);
    const float floorFall = timeConstantCoeff(sampleRate_, 120.0f);
    const float nsGainSmoothCoeff = timeConstantCoeff(sampleRate_, 25.0f);

    const float agcRmsCoeff = timeConstantCoeff(sampleRate_, 45.0f);
    const float agcGainAttack = timeConstantCoeff(sampleRate_, 35.0f);
    const float agcGainRelease = timeConstantCoeff(sampleRate_, 450.0f);

    const float limiterAttack = timeConstantCoeff(sampleRate_, 0.5f);
    const float limiterRelease = timeConstantCoeff(sampleRate_, 80.0f);
    const float softStartStep = 1.0f / std::max(1.0f, sampleRate_ * (rampMs * 0.001f));

    double inputSqSum = 0.0;
    double outputSqSum = 0.0;
    float inputPeak = 0.0f;
    float outputPeak = 0.0f;
    bool limiterEngaged = false;

    // Max stationary attenuation: at strength=1.0 allow up to ~11 dB attenuation (minGain ~ 0.28)
    // so quiet speech syllables are never abruptly gated out.
    const float nsFloorGain = 1.0f - (nsStrength * 0.72f);

    for (int32_t i = 0; i < numFrames; ++i) {
        // 1. Input sensitivity gain & input metering
        float x = samples[i] * micGain;
        const float absIn = std::abs(x);
        if (absIn > inputPeak) inputPeak = absIn;
        inputSqSum += static_cast<double>(x) * static_cast<double>(x);

        // 2. High-pass filter (removes room rumble / handling noise)
        if (hpEnabled) {
            x = hpf_.process(x);
        }

        // 3. Gentle two-band stationary noise suppression (preserves consonants)
        if (nsEnabled && nsStrength > 0.01f) {
            splitLowState_ += splitCoeff_ * (x - splitLowState_);
            const float lowBand = splitLowState_;
            const float highBand = x - lowBand;

            const float absLow = std::abs(lowBand);
            const float absHigh = std::abs(highBand);

            lowBandEnv_ += (absLow > lowBandEnv_ ? envAttack : envRelease) * (absLow - lowBandEnv_);
            highBandEnv_ += (absHigh > highBandEnv_ ? envAttack : envRelease) * (absHigh - highBandEnv_);

            lowNoiseFloor_ += (lowBandEnv_ > lowNoiseFloor_ ? floorRise : floorFall) * (lowBandEnv_ - lowNoiseFloor_);
            highNoiseFloor_ += (highBandEnv_ > highNoiseFloor_ ? floorRise : floorFall) * (highBandEnv_ - highNoiseFloor_);

            const float snrLow = lowBandEnv_ / std::max(lowNoiseFloor_, 1e-5f);
            const float snrHigh = highBandEnv_ / std::max(highNoiseFloor_, 1e-5f);
            // Consonant preservation: high-band speech presence lifts overall gate quickly
            const float speechPresence = std::max(snrLow, snrHigh * 1.35f);

            float targetNsGain = 1.0f;
            if (speechPresence < 2.2f) {
                const float ratio = std::clamp((speechPresence - 1.05f) / 1.15f, 0.0f, 1.0f);
                targetNsGain = nsFloorGain + ratio * (1.0f - nsFloorGain);
            }
            nsGainSmoothed_ += nsGainSmoothCoeff * (targetNsGain - nsGainSmoothed_);

            // Apply full suppression to low HVAC band, lighter suppression (max 50%) to high consonants
            const float highGain = 0.5f + 0.5f * nsGainSmoothed_;
            x = (lowBand * nsGainSmoothed_) + (highBand * highGain);
        }

        // 4. Speech clarity shaping (2.4 kHz presence + gentle upper-end smoothing)
        if (clarityEnabled) {
            x = presencePeak_.process(x);
            x = airDeHarshShelf_.process(x);
        }

        // 5. Smooth, gated Automatic Gain Control
        if (agcEnabled) {
            agcRmsSq_ += agcRmsCoeff * ((x * x) - agcRmsSq_);
            const float rms = std::sqrt(std::max(agcRmsSq_, kEpsilon));

            float targetAgcGain = agcCurrentGain_;
            if (rms > 0.0025f) { // -52 dBFS speech gate: do not pump room silence
                targetAgcGain = std::clamp(agcTargetLinear / rms, 0.5f, 5.0f); // -6 dB to +14 dB
            } else {
                targetAgcGain = 1.0f;
            }
            const float coeff = (targetAgcGain < agcCurrentGain_) ? agcGainAttack : agcGainRelease;
            agcCurrentGain_ += coeff * (targetAgcGain - agcCurrentGain_);
            x *= agcCurrentGain_;
        }

        // 6. Output volume + gradual clinical soft-start ramp
        if (softStartProgress_ < 1.0f) {
            softStartProgress_ = std::min(1.0f, softStartProgress_ + softStartStep);
        }
        // Smooth S-curve startup ramp
        const float rampGain = softStartProgress_ * softStartProgress_ * (3.0f - 2.0f * softStartProgress_);
        x *= outputGain * rampGain;

        // 7. Patient safety limiter (attack/release envelope + strict peak ceiling)
        const float absOutPre = std::abs(x);
        limiterEnv_ += (absOutPre > limiterEnv_ ? limiterAttack : limiterRelease) * (absOutPre - limiterEnv_);
        if (limiterEnv_ > limitLinear) {
            const float reduction = limitLinear / std::max(limiterEnv_, kEpsilon);
            x *= reduction;
            limiterEngaged = true;
        }
        if (std::abs(x) > limitLinear) {
            x = std::clamp(x, -limitLinear, limitLinear);
            limiterEngaged = true;
        }

        samples[i] = x;

        const float absOut = std::abs(x);
        if (absOut > outputPeak) outputPeak = absOut;
        outputSqSum += static_cast<double>(x) * static_cast<double>(x);
    }

    const float invN = 1.0f / static_cast<float>(numFrames);
    metrics.inputPeakLinear = inputPeak;
    metrics.inputRmsLinear = std::sqrt(static_cast<float>(inputSqSum * invN));
    metrics.outputPeakLinear = outputPeak;
    metrics.outputRmsLinear = std::sqrt(static_cast<float>(outputSqSum * invN));
    metrics.limiterEngaged = limiterEngaged;
    return metrics;
}

} // namespace hearlink
