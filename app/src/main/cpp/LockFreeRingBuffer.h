#pragma once

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstring>

namespace hearlink {

/**
 * Lock-free Single-Producer Single-Consumer (SPSC) ring buffer for real-time audio frames.
 * Zero heap allocation and zero mutex locks in read/write paths.
 */
template <size_t CapacityPow2>
class LockFreeRingBuffer {
    static_assert((CapacityPow2 & (CapacityPow2 - 1)) == 0, "Capacity must be a power of 2");

public:
    LockFreeRingBuffer() = default;

    void reset() {
        writeIndex_.store(0, std::memory_order_relaxed);
        readIndex_.store(0, std::memory_order_relaxed);
    }

    size_t availableToRead() const {
        const size_t w = writeIndex_.load(std::memory_order_acquire);
        const size_t r = readIndex_.load(std::memory_order_acquire);
        return w - r;
    }

    size_t availableToWrite() const {
        return CapacityPow2 - availableToRead();
    }

    /**
     * Producer-only write. Returns number of frames actually written.
     */
    size_t write(const float* src, size_t count) {
        const size_t r = readIndex_.load(std::memory_order_acquire);
        const size_t w = writeIndex_.load(std::memory_order_relaxed);
        const size_t freeSpace = CapacityPow2 - (w - r);
        const size_t toWrite = std::min(count, freeSpace);
        if (toWrite == 0) {
            return 0;
        }

        const size_t offset = w & kMask;
        const size_t firstChunk = std::min(toWrite, CapacityPow2 - offset);
        std::memcpy(buffer_ + offset, src, firstChunk * sizeof(float));

        const size_t secondChunk = toWrite - firstChunk;
        if (secondChunk > 0) {
            std::memcpy(buffer_, src + firstChunk, secondChunk * sizeof(float));
        }

        writeIndex_.store(w + toWrite, std::memory_order_release);
        return toWrite;
    }

    /**
     * Consumer-only read. Returns number of frames actually read.
     */
    size_t read(float* dst, size_t count) {
        const size_t w = writeIndex_.load(std::memory_order_acquire);
        const size_t r = readIndex_.load(std::memory_order_relaxed);
        const size_t available = w - r;
        const size_t toRead = std::min(count, available);
        if (toRead == 0) {
            return 0;
        }

        const size_t offset = r & kMask;
        const size_t firstChunk = std::min(toRead, CapacityPow2 - offset);
        std::memcpy(dst, buffer_ + offset, firstChunk * sizeof(float));

        const size_t secondChunk = toRead - firstChunk;
        if (secondChunk > 0) {
            std::memcpy(dst + firstChunk, buffer_, secondChunk * sizeof(float));
        }

        readIndex_.store(r + toRead, std::memory_order_release);
        return toRead;
    }

    /**
     * Consumer-only drift control: drops excess oldest frames so end-to-end latency stays bounded.
     */
    size_t dropOldest(size_t framesToDrop) {
        const size_t w = writeIndex_.load(std::memory_order_acquire);
        const size_t r = readIndex_.load(std::memory_order_relaxed);
        const size_t available = w - r;
        const size_t drop = std::min(framesToDrop, available);
        readIndex_.store(r + drop, std::memory_order_release);
        return drop;
    }

private:
    static constexpr size_t kMask = CapacityPow2 - 1;
    alignas(64) std::atomic<size_t> writeIndex_{0};
    alignas(64) std::atomic<size_t> readIndex_{0};
    alignas(64) float buffer_[CapacityPow2]{};
};

} // namespace hearlink
