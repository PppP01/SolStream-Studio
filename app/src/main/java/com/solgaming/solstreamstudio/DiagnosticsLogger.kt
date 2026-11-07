/*
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class DiagnosticEvent(
    val time: String,
    val category: String,
    val plainEnglish: String,
    val technicalDetail: String
)

data class BenchmarkSummary(
    val label: String,
    val durationMinutes: Int,
    val startTempC: Float,
    val peakTempC: Float,
    val avgRamMb: Int,
    val worstThermalLevel: Int,
    val droppedFrames: Int,
    val reconnectCount: Int
)

object DiagnosticsLogger {
    private const val MAX_EVENTS = 260
    private const val MAX_CLIP_MARKERS = 60
    private const val BENCH_PREFS = "solstream_benchmarks"
    private const val KEY_LAST_CRASH = "last_privacy_safe_crash"

    private val events = ArrayDeque<DiagnosticEvent>(MAX_EVENTS)
    private val clipHighlights = ArrayList<String>(MAX_CLIP_MARKERS)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Volatile private var crashHandlerInstalled: Boolean = false

    @Volatile var sessionStartMs: Long = 0L
    @Volatile var startBatteryTempC: Float = -1f
    @Volatile var peakBatteryTempC: Float = -1f
    @Volatile var worstThermalStatus: Int = 0
    @Volatile var ramSampleSumMb: Long = 0L
    @Volatile var ramSampleCount: Int = 0
    @Volatile var droppedVideoFrames: Int = 0
    @Volatile var reconnectAttempts: Int = 0
    @Volatile var activeEncoderDesc: String = "Idle"
    @Volatile var activeAudioDspDesc: String = "32-Bit Float Studio"
    @Volatile var activeSimulcastDesc: String = "Single Primary"
    @Volatile var lastQueueBytes: Long = -1L
    @Volatile var lastQueueFrames: Int = -1
    @Volatile var lastQueueCapacity: Int = -1
    @Volatile var lastRttMs: Long = -1L
    @Volatile var lastEncodedFps: Int = 0
    @Volatile var freshCaptureFps: Int = -1
    @Volatile var repeatedFramesCount: Long = 0L
    @Volatile var lastAverageQp: Int = -1
    @Volatile var lastBitrateKbps: Int = 0
    @Volatile var avDriftMs: Long = 0L
    @Volatile var micStatusLabel: String = "IDLE"
    @Volatile var lastBatteryPercent: Int = -1

    private fun redactSecrets(input: String): String {
        return input
            .replace(
                Regex("(rtmps?://[^/\\s]+/[^/\\s?]+/)([^?\\s]+)", RegexOption.IGNORE_CASE),
                "$1[REDACTED_KEY]"
            )
            .replace(
                Regex("([a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4,})", RegexOption.IGNORE_CASE),
                "[REDACTED_KEY]"
            )
            .replace(
                Regex("(live_[0-9]+_[a-z0-9A-Z_]+)", RegexOption.IGNORE_CASE),
                "[REDACTED_KEY]"
            )
    }

    fun formatTempC(tempC: Float): String {
        return if (tempC > 0f && !tempC.isNaN()) {
            String.format(Locale.US, "%.1f°C", tempC)
        } else {
            "N/A"
        }
    }

    fun installPrivacySafeCrashHandler(context: Context) {
        if (crashHandlerInstalled) return
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val topFrame = throwable.stackTrace.firstOrNull {
                    it.className.startsWith("com.solgaming.solstreamstudio")
                } ?: throwable.stackTrace.firstOrNull()
                val summary = redactSecrets(
                    buildString {
                        append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                        append(" | thread=").append(thread.name)
                        append(" | ex=").append(throwable.javaClass.simpleName)
                        append(": ").append(throwable.message ?: "no-msg")
                        if (topFrame != null) {
                            append(" @ ").append(topFrame.fileName ?: topFrame.className)
                            append(":").append(topFrame.lineNumber)
                        }
                        append(" | pipe=").append(activeEncoderDesc)
                        append(" | therm=").append(thermalStatusName(readThermalStatus(appCtx)))
                        append(" | pss=").append(readAppMemoryMb()).append("MB")
                    }
                )
                appCtx.getSharedPreferences(BENCH_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, summary)
                    .commit()
            } catch (_: Exception) {
                // Never block default crash propagation
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
        crashHandlerInstalled = true
    }

    fun clearSavedCrashNote(context: Context) {
        context.applicationContext.getSharedPreferences(BENCH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_CRASH)
            .apply()
    }

    @Synchronized
    fun startNewSession(context: Context, modeLabel: String, encoderDesc: String) {
        installPrivacySafeCrashHandler(context)
        events.clear()
        clipHighlights.clear()
        sessionStartMs = System.currentTimeMillis()
        val temp = readBatteryTempC(context)
        startBatteryTempC = temp
        peakBatteryTempC = temp
        lastBatteryPercent = readBatteryPercent(context)
        worstThermalStatus = readThermalStatus(context)
        ramSampleSumMb = 0L
        ramSampleCount = 0
        droppedVideoFrames = 0
        reconnectAttempts = 0
        activeEncoderDesc = encoderDesc
        lastQueueBytes = -1L
        lastQueueFrames = -1
        lastQueueCapacity = -1
        lastRttMs = -1L
        freshCaptureFps = -1
        repeatedFramesCount = 0L
        lastAverageQp = -1
        avDriftMs = 0L
        log(
            "SESSION",
            "$modeLabel started ($encoderDesc).",
            "startTemp=${formatTempC(temp)} batt=${if (lastBatteryPercent >= 0) "$lastBatteryPercent%" else "N/A"} thermal=${thermalStatusName(worstThermalStatus)}"
        )
    }

    /**
     * Records an exact VOD / Shorts highlight timestamp when the streamer taps '★ Mark Clip' in-game.
     */
    @Synchronized
    fun markClipHighlight(streamElapsedSec: Long, label: String = "Clutch / Highlight"): String {
        val hh = streamElapsedSec / 3600L
        val mm = (streamElapsedSec % 3600L) / 60L
        val ss = streamElapsedSec % 60L
        val vodStamp = String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss)
        val wallClock = timeFmt.format(Date())
        val clipNum = clipHighlights.size + 1
        val entry = "Clip #$clipNum @ Stream Time $vodStamp (Wall Clock $wallClock) — $label"
        if (clipHighlights.size >= MAX_CLIP_MARKERS) {
            clipHighlights.removeAt(0)
        }
        clipHighlights.add(entry)
        log("CLIP_MARK", "Saved stream highlight marker #$clipNum at $vodStamp.", label)
        return "#$clipNum @ $vodStamp"
    }

    @Synchronized
    fun getClipHighlightCount(): Int = clipHighlights.size

    @Synchronized
    fun log(category: String, plainEnglish: String, technicalDetail: String = "") {
        if (events.size >= MAX_EVENTS) {
            events.removeFirst()
        }
        events.addLast(
            DiagnosticEvent(
                time = timeFmt.format(Date()),
                category = category,
                plainEnglish = redactSecrets(plainEnglish),
                technicalDetail = redactSecrets(technicalDetail)
            )
        )
    }

    fun sampleDeviceHealth(context: Context): Pair<Float, Int> {
        val tempC = readBatteryTempC(context)
        val thermal = readThermalStatus(context)
        val battPct = readBatteryPercent(context)
        val ramMb = readAppMemoryMb()
        synchronized(this) {
            if (tempC > 0f && (peakBatteryTempC <= 0f || tempC > peakBatteryTempC)) {
                peakBatteryTempC = tempC
            }
            if (tempC > 0f && startBatteryTempC <= 0f) {
                startBatteryTempC = tempC
            }
            if (battPct >= 0) lastBatteryPercent = battPct
            if (thermal > worstThermalStatus) worstThermalStatus = thermal
            ramSampleSumMb += ramMb
            ramSampleCount++
        }
        return tempC to thermal
    }

    /**
     * Reads sticky ACTION_BATTERY_CHANGED immediately; returns -1f (N/A) instead of fake 0.0°C.
     */
    fun readBatteryTempC(context: Context): Float {
        return try {
            val intent = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (tenths > 0) tenths / 10.0f else -1f
        } catch (_: Exception) {
            -1f
        }
    }

    fun readBatteryPercent(context: Context): Int {
        return try {
            val intent = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return -1
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) ((level * 100f) / scale.toFloat()).roundToInt() else -1
        } catch (_: Exception) {
            -1
        }
    }

    fun isBatteryChargingOrPlugged(context: Context): Boolean {
        return try {
            val intent = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return false
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            plugged != 0
        } catch (_: Exception) {
            false
        }
    }

    fun readThermalStatus(context: Context): Int {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.currentThermalStatus
        } catch (_: Exception) {
            PowerManager.THERMAL_STATUS_NONE
        }
    }

    fun readThermalHeadroom(context: Context): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val h = pm.getThermalHeadroom(10)
                if (h.isNaN() || h <= 0f) -1f else h
            } catch (_: Exception) {
                -1f
            }
        } else {
            -1f
        }
    }

    fun readAppMemoryMb(): Int {
        return try {
            val pssKb = Debug.getPss()
            if (pssKb > 0L) {
                (pssKb / 1024L).toInt()
            } else {
                val rt = Runtime.getRuntime()
                ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).toInt()
            }
        } catch (_: Exception) {
            val rt = Runtime.getRuntime()
            ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).toInt()
        }
    }

    fun describeDetailedMemory(avgPssMb: Int): String {
        return try {
            val rt = Runtime.getRuntime()
            val javaHeapMb = ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).toInt()
            val nativeHeapMb = (Debug.getNativeHeapAllocatedSize() / (1024L * 1024L)).toInt()
            "App Process PSS: ${avgPssMb} MB (Java Heap: ${javaHeapMb} MB | Native Heap: ${nativeHeapMb} MB)"
        } catch (_: Exception) {
            "App Process PSS: ${avgPssMb} MB"
        }
    }

    fun thermalStatusName(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal (None)"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Warmth"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe (Throttling Risk)"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        else -> "Status $status"
    }

    fun audioDeviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset Mic"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth HFP (Call-Mode)"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Mic"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-C Headset Mic"
        AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
        26 -> "Bluetooth LE Audio Headset"
        else -> "Type-$type"
    }

    fun inspectActiveAudioRecordings(context: Context): String {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val configs = am.activeRecordingConfigurations
            if (configs.isEmpty()) return "No active AudioRecord sessions"
            configs.mapIndexed { idx, c ->
                val silenced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.isClientSilenced.toString()
                } else {
                    "n/a"
                }
                val srcName = when (c.clientAudioSource) {
                    MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
                    MediaRecorder.AudioSource.MIC -> "MIC"
                    MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_REC"
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMM"
                    MediaRecorder.AudioSource.REMOTE_SUBMIX -> "INTERNAL_PLAYBACK"
                    else -> "SRC_${c.clientAudioSource}"
                }
                val dev = c.audioDevice
                val devDesc = if (dev != null) {
                    "${audioDeviceTypeName(dev.type)}(${dev.productName})"
                } else {
                    "none"
                }
                val clientRate = c.clientFormat.sampleRate
                val halRate = c.format.sampleRate
                "#$idx src=$srcName silenced=$silenced appRate=${clientRate}Hz(hal=${halRate}Hz) ch=${c.clientFormat.channelCount} dev=$devDesc"
            }.joinToString(" | ")
        } catch (e: Exception) {
            "Audio config query unavailable (${e.javaClass.simpleName})"
        }
    }

    fun finishAndSaveBenchmark(context: Context, isGlipBaseline: Boolean) {
        val mins = if (sessionStartMs > 0L) {
            max(1, ((System.currentTimeMillis() - sessionStartMs) / 60000L).toInt())
        } else {
            1
        }
        val avgRam = if (ramSampleCount > 0) (ramSampleSumMb / ramSampleCount).toInt() else readAppMemoryMb()
        val liveTemp = readBatteryTempC(context)
        val sTemp = if (startBatteryTempC > 0f) startBatteryTempC else liveTemp
        val pTemp = if (peakBatteryTempC > 0f) peakBatteryTempC else liveTemp
        val prefix = if (isGlipBaseline) "glip" else "sol"
        context.applicationContext.getSharedPreferences(BENCH_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("${prefix}_exists", true)
            .putInt("${prefix}_mins", mins)
            .putFloat("${prefix}_start_temp", sTemp)
            .putFloat("${prefix}_peak_temp", pTemp)
            .putInt("${prefix}_ram", avgRam)
            .putInt("${prefix}_thermal", worstThermalStatus)
            .putInt("${prefix}_drops", droppedVideoFrames)
            .putInt("${prefix}_reconnects", reconnectAttempts)
            .apply()
    }

    private fun loadBenchmark(context: Context, prefix: String, label: String): BenchmarkSummary? {
        val p = context.applicationContext.getSharedPreferences(BENCH_PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("${prefix}_exists", false)) return null
        return BenchmarkSummary(
            label = label,
            durationMinutes = p.getInt("${prefix}_mins", 0),
            startTempC = p.getFloat("${prefix}_start_temp", -1f),
            peakTempC = p.getFloat("${prefix}_peak_temp", -1f),
            avgRamMb = p.getInt("${prefix}_ram", 0),
            worstThermalLevel = p.getInt("${prefix}_thermal", 0),
            droppedFrames = p.getInt("${prefix}_drops", 0),
            reconnectCount = p.getInt("${prefix}_reconnects", 0)
        )
    }

    private fun formatQueueSummary(): String {
        return when {
            lastQueueFrames >= 0 && lastQueueCapacity > 0 -> {
                "$lastQueueFrames/$lastQueueCapacity frames" +
                    if (lastQueueBytes >= 0L) " (${lastQueueBytes / 1024} KB)" else ""
            }
            lastQueueFrames >= 0 -> "$lastQueueFrames frames"
            lastQueueBytes >= 0L -> "${lastQueueBytes / 1024} KB"
            else -> "N/A"
        }
    }

    @Synchronized
    fun buildShareableReport(context: Context): String {
        val scan = CodecCapabilityProbe.peekCachedOrPlaceholder(context)
        val avgRam = if (ramSampleCount > 0) (ramSampleSumMb / ramSampleCount).toInt() else readAppMemoryMb()
        val thermalLoad = readThermalHeadroom(context)
        val liveTemp = readBatteryTempC(context)
        val displayStartTemp = if (sessionStartMs > 0L) startBatteryTempC else liveTemp
        val displayPeakTemp = if (sessionStartMs > 0L) peakBatteryTempC else liveTemp
        val savedCrash = context.applicationContext
            .getSharedPreferences(BENCH_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH, null)

        val sb = StringBuilder()
        sb.appendLine("=== SOLSTREAM STUDIO ULTRA — AUTOMATIC SESSION REPORT ===")
        sb.appendLine("Privacy Guarantee: Stream keys, notifications, and voice audio are NEVER included.")
        sb.appendLine(
            "Device: ${scan.deviceModel} (${scan.socModel}) | Android ${scan.androidRelease} (API ${scan.sdkInt}) | " +
                "Display: ${scan.maxDisplayHz}Hz | GL: ${scan.maxGlEsVersion}"
        )
        sb.appendLine("Vault Security: ${KeystoreVault.getKeySecurityDescription()} | Targets: $activeSimulcastDesc")
        sb.appendLine("Active Pipeline: $activeEncoderDesc | Audio DSP: $activeAudioDspDesc")

        val freshStr = if (freshCaptureFps >= 0) "$freshCaptureFps" else "N/A"
        val qpStr = if (lastAverageQp >= 0) "$lastAverageQp" else "N/A"
        val rttStr = if (lastRttMs >= 0L) "${lastRttMs} ms" else "N/A"
        sb.appendLine(
            "Latest Live Metrics: ${String.format(Locale.US, "%.2f", lastBitrateKbps / 1000.0)} Mbps | " +
                "Out FPS: $lastEncodedFps (Fresh Game FPS: $freshStr, Skipped Idle: $repeatedFramesCount, Avg QP: $qpStr) | " +
                "Send Queue: ${formatQueueSummary()} | RTMP Ping: $rttStr | " +
                "A/V Sync Offset: ${avDriftMs} ms | Mic: $micStatusLabel"
        )
        sb.appendLine(
            "Battery Temp & Memory: Start ${formatTempC(displayStartTemp)} -> Peak ${formatTempC(displayPeakTemp)} | " +
                "Worst SoC Thermal: ${thermalStatusName(worstThermalStatus)} | " +
                "Thermal Load (toward Severe): ${if (thermalLoad >= 0f) "${(thermalLoad * 100).roundToInt()}%" else "N/A"} | " +
                describeDetailedMemory(avgRam)
        )
        sb.appendLine("Active Audio Policy: ${inspectActiveAudioRecordings(context)}")
        if (!savedCrash.isNullOrBlank()) {
            sb.appendLine("Last Recorded Crash: $savedCrash")
        }
        sb.appendLine()

        if (clipHighlights.isNotEmpty()) {
            sb.appendLine("--- ★ SAVED STREAM CLIP HIGHLIGHTS (FOR SHORTS / VODS) ---")
            for (clip in clipHighlights) {
                sb.appendLine("• $clip")
            }
            sb.appendLine()
        }

        val glip = loadBenchmark(context, "glip", "Glip Baseline")
        val sol = loadBenchmark(context, "sol", "SolStream Studio")
        if (glip != null || sol != null) {
            sb.appendLine("--- GLIP vs. SOLSTREAM BENCHMARK COMPARISON ---")
            glip?.let {
                sb.appendLine(
                    "• Glip (${it.durationMinutes}m): Start ${formatTempC(it.startTempC)} -> Peak ${formatTempC(it.peakTempC)} | " +
                        "Worst Thermal: ${thermalStatusName(it.worstThermalLevel)} | App PSS: ${it.avgRamMb} MB"
                )
            }
            sol?.let {
                sb.appendLine(
                    "• SolStream (${it.durationMinutes}m): Start ${formatTempC(it.startTempC)} -> Peak ${formatTempC(it.peakTempC)} | " +
                        "Worst Thermal: ${thermalStatusName(it.worstThermalLevel)} | App PSS: ${it.avgRamMb} MB | " +
                        "Sender Drops: ${it.droppedFrames} | Reconnects: ${it.reconnectCount}"
                )
            }
            sb.appendLine()
        }

        sb.appendLine("--- PLAIN-ENGLISH SESSION NOTES (CHRONOLOGICAL) ---")
        if (events.isEmpty()) {
            sb.appendLine("No stream events recorded yet.")
        } else {
            for (e in events) {
                val tech = if (e.technicalDetail.isNotBlank()) " [${e.technicalDetail}]" else ""
                sb.appendLine("${e.time} [${e.category}] ${e.plainEnglish}$tech")
            }
        }
        return sb.toString()
    }
}
