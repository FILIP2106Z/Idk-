package com.example.audio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import java.io.File

class SystemDiagnosticsMonitor(private val context: Context) {

    private var lastWallTimeMs: Long = 0L
    private var lastCpuTicks: Long = 0L
    private var baselineBatteryPercent: Int? = null

    fun resetSessionBaseline() {
        baselineBatteryPercent = readBatteryPercent()
        lastWallTimeMs = SystemClock.elapsedRealtime()
        lastCpuTicks = readProcessCpuTimeMs()
    }

    fun readBatteryPercent(): Int? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            return (level * 100) / scale
        }
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val cap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return if (cap in 0..100) cap else null
    }

    fun readBatteryDrainPercent(): Int {
        val current = readBatteryPercent() ?: return 0
        val base = baselineBatteryPercent ?: current
        return (base - current).coerceAtLeast(0)
    }

    fun sampleCpuLoadPercent(): Float? {
        val nowWall = SystemClock.elapsedRealtime()
        val nowCpuMs = readProcessCpuTimeMs()
        if (nowCpuMs < 0L) return null

        val prevWall = lastWallTimeMs
        val prevCpu = lastCpuTicks
        lastWallTimeMs = nowWall
        lastCpuTicks = nowCpuMs

        if (prevWall == 0L || nowWall <= prevWall) return null
        val wallDelta = (nowWall - prevWall).toFloat()
        val cpuDelta = (nowCpuMs - prevCpu).coerceAtLeast(0L).toFloat()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return ((cpuDelta / (wallDelta * cores)) * 100f).coerceIn(0f, 100f)
    }

    private fun readProcessCpuTimeMs(): Long {
        return try {
            // /proc/self/stat fields 14 (utime) and 15 (stime) in clock ticks;
            // Fallback to android.os.Process.getElapsedCpuTime() which is directly in milliseconds!
            val elapsedMs = Process.getElapsedCpuTime()
            if (elapsedMs >= 0L) return elapsedMs

            val statText = File("/proc/self/stat").readText()
            val closeParen = statText.lastIndexOf(')')
            if (closeParen < 0) return -1L
            val parts = statText.substring(closeParen + 2).trim().split(' ')
            if (parts.size > 12) {
                val utime = parts[11].toLongOrNull() ?: 0L
                val stime = parts[12].toLongOrNull() ?: 0L
                (utime + stime) * 10L
            } else {
                -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }
}
