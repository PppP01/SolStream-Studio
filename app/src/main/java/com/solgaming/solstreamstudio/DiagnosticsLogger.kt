/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
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
import android.media.AudioRecordingConfiguration
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class SessionEvent(
    val timestamp: String,
    val tag: String,
    val plainMessage: String,
    val technicalDetail: String = ""
)

data class BenchmarkSnapshot(
    val label: String,
    val durationMinutes: Int,
    val startBatteryTempC: Float,
    val peakBatteryTempC: Float,
    val worstThermalStatus: Int,
    val peakPssMb: Int,
    val droppedFrames: Int,
    val reconnects: Int
) {
    fun toSummaryLine(): String {
        val tempSpan = if (startBatteryTempC > 0f && peakBatteryTempC > 0f) {
            String.format(Locale.US, "%.1f°C -> Peak %.1f°C", startBatteryTempC, peakBatteryTempC)
        } else {
            "N/A"
        }
        val therm = DiagnosticsLogger.thermalStatusName(worstThermalStatus)
        return "$label (${durationMinutes}m): Start $tempSpan | Worst Thermal: $therm | App PSS: ${peakPssMb} MB | Sender Drops: $droppedFrames | Reconnects: $reconnects"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("label", label)
        put("durationMinutes", durationMinutes)
        put("startBatteryTempC", startBatteryTempC.toDouble())
        put("peakBatteryTempC", peakBatteryTempC.toDouble())
        put("worstThermalStatus", worstThermalStatus)
        put("peakPssMb", peakPssMb)
        put("droppedFrames", droppedFrames)
        put("reconnects", reconnects)
    }

    companion object {
        fun fromJson(raw: String?): BenchmarkSnapshot? {
            if (raw.isNullOrBlank()) return null
            return try {
                val j = JSONObject(raw)
                BenchmarkSnapshot(
                    label = j.optString("label", "Session"),
                    durationMinutes = j.optInt("durationMinutes", 0),
                    startBatteryTempC = j.optDouble("startBatteryTempC", 0.0).toFloat(),
                    peakBatteryTempC = j.optDouble("peakBatteryTempC", 0.0).toFloat(),
                    worstThermalStatus = j.optInt("worstThermalStatus", 0),
                    peakPssMb = j.optInt("peakPssMb", 0),
                    droppedFrames = j.optInt("droppedFrames", 0),
                    reconnects = j.optInt("reconnects", 0)
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

object DiagnosticsLogger {

    const val ENGINE_VERSION = "v1.6.0"

    private const val MAX_EVENTS = 160
    private const val MAX_CLIPS = 60
    private const val PREFS_BENCH = "solstream_benchmarks"
    private const val KEY_LAST_SOL_BENCH = "last_solstream_bench_json"
    private const val KEY_LAST_GLIP_BENCH = "last_glip_bench_json"
    private const val KEY_LAST_CRASH = "last_uncaught_exception"
    private const val KEY_SAVED_CLIPS_JSON = "saved_stream_clips_json"

    // Dedicated single-slot 60-second storage checkpoint (never pollutes the 9-slot session history)
    private const val KEY_LIVE_CHECKPOINT_ACTIVE = "live_checkpoint_active"
    private const val KEY_LIVE_CHECKPOINT_REPORT = "live_checkpoint_report_text"
    private const val KEY_LIVE_CHECKPOINT_LABEL = "live_checkpoint_label"
    private const val KEY_LIVE_CHECKPOINT_SAVED_MS = "live_checkpoint_saved_ms"

    private val events = ArrayDeque<SessionEvent>(MAX_EVENTS)
    private val clipHighlights = ArrayList<String>(MAX_CLIPS)
    private val timeFmtLock = Any()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val secretUrlRegex = Regex("(rtmps?://[^/\\s]+/[^/\\s]+/)([^\\s?\"']+)", RegexOption.IGNORE_CASE)
    private val queryKeyRegex = Regex("(stream_?key=|key=|token=|auth=)([^&\\s\"']+)", RegexOption.IGNORE_CASE)

    @Volatile private var crashHandlerInstalled = false
    @Volatile private var lastPssSampleRealtimeMs: Long = 0L
    @Volatile private var lastAiClutchRealtimeMs: Long = 0L
    @Volatile private var finalMicStatusSnapshot: String = ""

    @Volatile var sessionStartMs: Long = 0L
    @Volatile var sessionEndMs: Long = 0L
    @Volatile var isSessionFinalized: Boolean = false
    @Volatile var activePipelineDesc: String = "Idle (Not Streaming)"
    @Volatile var activeAudioDspDesc: String = "SolLuxe™ 3.0 Pure 32-Bit Float + Native C RNNoise"
    @Volatile var activeSimulcastDesc: String = "Primary Only"
    @Volatile var activeOverlaysDesc: String = "None"
    @Volatile var startTempC: Float = 0f
    @Volatile var peakTempC: Float = 0f
    @Volatile var peakSocTempC: Float = -1f
    @Volatile var worstThermalState: Int = 0
    @Volatile var lastThermalHeadroom: Float = -1f

    @Volatile var lastBatteryPercent: Int = 100
    @Volatile var startPowerSourceLabel: String = "Battery"
    @Volatile var lastPowerSourceLabel: String = "Battery"
    @Volatile var peakTotalPssMb: Int = 0
    @Volatile var lastJavaHeapMb: Int = 0
    @Volatile var lastNativeHeapMb: Int = 0

    @Volatile var droppedVideoFrames: Int = 0
    @Volatile var droppedAudioFrames: Int = 0
    @Volatile var reconnectAttempts: Int = 0
    @Volatile var lastBitrateKbps: Int = 0
    @Volatile private var bitrateSamplesSumKbps: Long = 0L
    @Volatile private var bitrateSamplesCount: Long = 0L
    @Volatile var lastEncodedFps: Int = 0
    @Volatile var freshCaptureFps: Int = 0
    @Volatile var repeatedFramesCount: Long = 0L
    @Volatile var lastAverageQp: Int = -1
    @Volatile var lastQueueBytes: Long = 0L
    @Volatile var lastQueueFrames: Int = 0
    @Volatile var lastQueueCapacity: Int = 60
    @Volatile var lastSendLatencyMs: Long = 0L
    @Volatile var lastRttMs: Long = -1L
    @Volatile var lastRttSourceLabel: String = ""
    @Volatile var avDriftMs: Long = 0L
    @Volatile var micStatusLabel: String = "Ready"
    @Volatile var lastActiveAudioPolicySnapshot: String = ""

    private fun formatWallClockNow(): String {
        synchronized(timeFmtLock) {
            return timeFmt.format(Date())
        }
    }

    private fun formatTempOrNa(tempC: Float): String {
        return if (tempC > 0f) String.format(Locale.US, "%.1f°C", tempC) else "N/A"
    }

    /**
     * Prevents idle post-stop report views from overwriting locked end-of-stream power,
     * thermal headroom, battery %, and heap metrics.
     */
    internal fun shouldSampleHealthOnReport(isLive: Boolean): Boolean {
        return isLive || sessionStartMs == 0L || !isSessionFinalized
    }

    /**
     * Shows "COMPETITOR vs. SOLSTREAM" only when a competitor benchmark actually exists;
     * otherwise labels the saved SolStream entry clearly as a previous SolStream session.
     */
    internal fun resolveBenchmarkSectionHeader(hasCompetitorBenchmark: Boolean): String {
        return if (hasCompetitorBenchmark) {
            "--- COMPETITOR vs. SOLSTREAM BENCHMARK COMPARISON ---"
        } else {
            "--- PREVIOUS SOLSTREAM SESSION BENCHMARK ---"
        }
    }

    /**
     * Verifies a sysfs /sys/class/thermal/thermal_zoneN/type string represents a real CPU/SoC/GPU
     * silicon sensor and explicitly rejects battery, charger, USB, skin, and RF sensors.
     */
    internal fun isVerifiedSocThermalZoneType(rawType: String): Boolean {
        val t = rawType.trim().lowercase(Locale.US)
        if (t.isEmpty()) return false
        val excluded = listOf("batt", "battery", "usb", "chg", "charger", "skin", "quiet", "pa_therm", "xo_therm", "pmic", "flash", "camera")
        if (excluded.any { t.contains(it) }) return false
        val socMarkers = listOf("cpu", "soc", "gpu", "g3d", "big", "little", "mid", "prime", "cluster", "tsens", "ap_", "exynos", "mtktscpu")
        return socMarkers.any { t.contains(it) }
    }

    /**
     * Parses sysfs thermal_zone temp readings reported in millidegrees (42500 -> 42.5°C),
     * decidegrees (425 -> 42.5°C), or whole degrees (42 -> 42.0°C). Returns -1f if out of range.
     */
    internal fun parseThermalZoneTempC(rawValue: Long): Float {
        val degC = when {
            rawValue > 1000L -> rawValue / 1000.0f
            rawValue in 200L..1150L -> rawValue / 10.0f
            else -> rawValue.toFloat()
        }
        return if (degC in 20.0f..115.0f) degC else -1f
    }

    @Synchronized
    internal fun resetSessionLifecycleForTests() {
        sessionStartMs = 0L
        sessionEndMs = 0L
        isSessionFinalized = false
        activeOverlaysDesc = "None"
        droppedVideoFrames = 0
        droppedAudioFrames = 0
        lastBitrateKbps = 0
        bitrateSamplesSumKbps = 0L
        bitrateSamplesCount = 0L
        lastRttMs = -1L
        lastRttSourceLabel = ""
        events.clear()
        clipHighlights.clear()
    }

    fun installPrivacySafeCrashHandler(context: Context) {
        if (crashHandlerInstalled) return
        synchronized(this) {
            if (crashHandlerInstalled) return
            val appCtx = context.applicationContext ?: context
            loadPersistedClipsIfEmpty(appCtx)
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val safeMsg = redactSecrets(throwable.message ?: "no message")
                    val topFrame = throwable.stackTrace.firstOrNull()?.let {
                        "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                    } ?: "unknown"
                    val summary = "${formatWallClockNow()} [${thread.name}] ${throwable.javaClass.simpleName}: $safeMsg (@ $topFrame)"
                    appCtx.getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_LAST_CRASH, summary.take(280))
                        .commit()
                } catch (_: Throwable) {
                }
                previousHandler?.uncaughtException(thread, throwable)
            }
            crashHandlerInstalled = true
        }
    }

    private fun redactSecrets(input: String): String {
        if (input.isEmpty()) return input
        return input
            .replace(secretUrlRegex, "$1[REDACTED_KEY]")
            .replace(queryKeyRegex, "$1[REDACTED]")
    }

    @Synchronized
    fun recordBitrateSample(kbps: Int) {
        lastBitrateKbps = kbps
        if (kbps > 0) {
            bitrateSamplesSumKbps += kbps.toLong()
            bitrateSamplesCount++
        }
    }

    @Synchronized
    fun getSessionAverageBitrateKbps(): Int {
        return if (bitrateSamplesCount > 0L) {
            (bitrateSamplesSumKbps / bitrateSamplesCount).toInt()
        } else {
            lastBitrateKbps
        }
    }

    /**
     * Snapshots the live active microphone status right BEFORE cleanupResources() stops the audio
     * engine so the final Session Report header always shows the true active mic status instead of "MIC STOPPED".
     */
    @Synchronized
    fun lockFinalMicStatusBeforeStop() {
        val current = micStatusLabel.trim()
        if (current.isNotBlank() && !current.contains("STOPPED", ignoreCase = true)) {
            finalMicStatusSnapshot = current
        }
    }

    @Synchronized
    fun markClipHighlight(
        elapsedStreamSec: Long,
        label: String = "Manual ★ Highlight",
        context: Context? = null
    ): String {
        val safeSec = max(0L, elapsedStreamSec)
        val hh = safeSec / 3600L
        val mm = (safeSec % 3600L) / 60L
        val ss = safeSec % 60L
        val streamStamp = String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss)
        val wallStamp = formatWallClockNow()
        val index = clipHighlights.size + 1
        val entry = "Clip #$index — $streamStamp into stream (Clock $wallStamp) • $label"
        if (clipHighlights.size < MAX_CLIPS) {
            clipHighlights.add(entry)
        }
        context?.let { persistSavedClips(it) }
        log("CLIP_MARK", "Saved stream highlight marker #$index at $streamStamp.", label)
        return streamStamp
    }

    @Synchronized
    fun markAiClutchHighlight(
        elapsedStreamSec: Long,
        detail: String = "🤖 AI Clutch Moment (Gunfight + Voice Callout)",
        context: Context? = null
    ): String? {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastAiClutchRealtimeMs < 45_000L) return null
        lastAiClutchRealtimeMs = nowMs
        return markClipHighlight(elapsedStreamSec, detail, context)
    }

    @Synchronized
    fun getClipHighlightCount(): Int = clipHighlights.size

    @Synchronized
    fun getSavedClipsList(context: Context? = null): List<String> {
        if (clipHighlights.isEmpty() && context != null) {
            loadPersistedClipsIfEmpty(context)
        }
        return clipHighlights.toList()
    }

    @Synchronized
    fun clearSavedClips(context: Context) {
        clipHighlights.clear()
        try {
            (context.applicationContext ?: context).getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SAVED_CLIPS_JSON)
                .apply()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun buildSavedClipsPlainEnglishSummary(context: Context): String {
        val list = getSavedClipsList(context)
        if (list.isEmpty()) {
            return "No saved clip timestamps yet.\n\n" +
                "• While streaming, tap '★ Clip' inside the Floating Pill anytime you hit a great play.\n" +
                "• Plus, SolStream's 🤖 AI Auto-Clutch Detector automatically marks intense gunfight + voice callout moments for you!"
        }
        val sb = StringBuilder()
        sb.appendLine("=== ★ SAVED STREAM CLIP TIMESTAMPS (${list.size} Total) ===")
        sb.appendLine("Open your YouTube Live VOD or video editor and jump straight to these exact stream times:")
        sb.appendLine()
        for (item in list) {
            sb.appendLine("• $item")
        }
        return sb.toString().trimEnd()
    }

    @Synchronized
    private fun persistSavedClips(context: Context) {
        try {
            val arr = JSONArray()
            for (c in clipHighlights) arr.put(c)
            (context.applicationContext ?: context).getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SAVED_CLIPS_JSON, arr.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun loadPersistedClipsIfEmpty(context: Context) {
        if (clipHighlights.isNotEmpty()) return
        try {
            val raw = (context.applicationContext ?: context)
                .getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
                .getString(KEY_SAVED_CLIPS_JSON, null) ?: return
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "")
                if (item.isNotBlank() && clipHighlights.size < MAX_CLIPS) {
                    clipHighlights.add(item)
                }
            }
        } catch (_: Exception) {
        }
    }

    // === 60-SECOND SINGLE-SLOT STORAGE AUTO-CHECKPOINT (SURVIVES SUDDEN OS MEMORY/THERMAL KILLS) ===

    @Synchronized
    fun saveLiveCheckpoint(context: Context, skipHealthResample: Boolean = false) {
        if (sessionStartMs <= 0L || isSessionFinalized) return
        try {
            persistSavedClips(context)
            val durMin = max(1, ((System.currentTimeMillis() - sessionStartMs) / 60_000L).toInt())
            val label = "SolStream (${durMin}m • Peak ${formatTempOrNa(peakTempC)} • LIVE / INCOMPLETE)"
            val reportText = buildShareableReportInternal(
                context = context,
                isLiveCheckpoint = true,
                skipHealthResample = skipHealthResample
            )
            (context.applicationContext ?: context).getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LIVE_CHECKPOINT_ACTIVE, true)
                .putString(KEY_LIVE_CHECKPOINT_LABEL, label)
                .putString(KEY_LIVE_CHECKPOINT_REPORT, reportText)
                .putLong(KEY_LIVE_CHECKPOINT_SAVED_MS, System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun clearLiveCheckpoint(context: Context) {
        try {
            (context.applicationContext ?: context).getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LIVE_CHECKPOINT_ACTIVE, false)
                .remove(KEY_LIVE_CHECKPOINT_LABEL)
                .remove(KEY_LIVE_CHECKPOINT_REPORT)
                .remove(KEY_LIVE_CHECKPOINT_SAVED_MS)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun hasUnfinishedLiveCheckpoint(context: Context): Boolean {
        return try {
            val prefs = (context.applicationContext ?: context).getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_LIVE_CHECKPOINT_ACTIVE, false) &&
                !prefs.getString(KEY_LIVE_CHECKPOINT_REPORT, null).isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun getRecoveredLiveCheckpointSummaryLabel(context: Context): String? {
        return try {
            val prefs = (context.applicationContext ?: context).getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_LIVE_CHECKPOINT_ACTIVE, false)) return null
            prefs.getString(KEY_LIVE_CHECKPOINT_LABEL, null)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun getRecoveredLiveCheckpointReport(context: Context): String? {
        return try {
            val prefs = (context.applicationContext ?: context).getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_LIVE_CHECKPOINT_ACTIVE, false)) return null
            val raw = prefs.getString(KEY_LIVE_CHECKPOINT_REPORT, null)?.takeIf { it.isNotBlank() } ?: return null
            "=== RECOVERED 60s LIVE CHECKPOINT (Session Ended Unexpectedly by OS) ===\n$raw"
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun startNewSession(context: Context, modeTitle: String, pipelineDesc: String) {
        events.clear()
        clipHighlights.clear()
        persistSavedClips(context)
        finalMicStatusSnapshot = ""
        lastAiClutchRealtimeMs = 0L
        lastPssSampleRealtimeMs = 0L
        sessionStartMs = System.currentTimeMillis()
        sessionEndMs = 0L
        isSessionFinalized = false
        activePipelineDesc = pipelineDesc
        activeOverlaysDesc = "None"

        val liveHealth = CodecCapabilityProbe.readLiveHardwareHealth(context)
        val temp = liveHealth.batteryTempC
        startTempC = temp
        peakTempC = temp
        peakSocTempC = readOptionalSocTempC()
        worstThermalState = liveHealth.thermalStatus
        lastThermalHeadroom = if (liveHealth.thermalHeadroomRatio.isNaN()) -1f else liveHealth.thermalHeadroomRatio
        lastBatteryPercent = readBatteryPercentage(context)
        startPowerSourceLabel = liveHealth.powerSourceLabel
        lastPowerSourceLabel = liveHealth.powerSourceLabel

        peakTotalPssMb = 0
        sampleMemoryFootprint(forcePssWalk = true)
        droppedVideoFrames = 0
        droppedAudioFrames = 0
        reconnectAttempts = 0
        lastBitrateKbps = 0
        bitrateSamplesSumKbps = 0L
        bitrateSamplesCount = 0L
        lastEncodedFps = 0
        freshCaptureFps = 0
        repeatedFramesCount = 0L
        lastAverageQp = -1
        lastQueueBytes = 0L
        lastQueueFrames = 0
        lastQueueCapacity = 60
        lastSendLatencyMs = 0L
        lastRttMs = -1L
        lastRttSourceLabel = ""
        avDriftMs = 0L
        lastActiveAudioPolicySnapshot = ""
        captureLiveAudioPolicySnapshot(context)
        log(
            "SESSION",
            "$modeTitle started ($pipelineDesc).",
            "startTemp=${formatTempOrNa(temp)} batt=${lastBatteryPercent}% power=$startPowerSourceLabel thermal=${thermalStatusName(worstThermalState)}"
        )
        saveLiveCheckpoint(context, skipHealthResample = true)
    }

    @Synchronized
    fun log(tag: String, plainMessage: String, technicalDetail: String = "") {
        if (events.size >= MAX_EVENTS) {
            events.removeFirst()
        }
        events.addLast(
            SessionEvent(
                timestamp = formatWallClockNow(),
                tag = tag,
                plainMessage = redactSecrets(plainMessage),
                technicalDetail = redactSecrets(technicalDetail)
            )
        )
    }

    fun captureLiveAudioPolicySnapshot(context: Context) {
        try {
            val am = (context.applicationContext ?: context).getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val configs: List<AudioRecordingConfiguration> = am.activeRecordingConfigurations
            if (configs.isNotEmpty()) {
                lastActiveAudioPolicySnapshot = configs.joinToString(" | ") { cfg ->
                    val dev = cfg.audioDevice
                    val devLabel = if (dev != null) {
                        "${dev.productName} (${audioDeviceTypeName(dev.type)})"
                    } else {
                        "Default Routing"
                    }
                    val silenced = if (cfg.isClientSilenced) "SILENCED" else "ACTIVE"
                    val fmt = cfg.format
                    "Session#${cfg.clientAudioSessionId}: $silenced via $devLabel (${fmt.sampleRate}Hz/${fmt.channelCount}ch)"
                }
            }
        } catch (_: Exception) {
        }
    }

    fun sampleDeviceHealth(context: Context): Pair<Float, Int> {
        val health = CodecCapabilityProbe.readLiveHardwareHealth(context)
        val t = health.batteryTempC
        val s = health.thermalStatus
        if (t > peakTempC) peakTempC = t
        if (startTempC <= 0f && t > 0f) startTempC = t
        val socT = readOptionalSocTempC()
        if (socT > peakSocTempC) peakSocTempC = socT
        if (s > worstThermalState) worstThermalState = s
        if (!health.thermalHeadroomRatio.isNaN() && health.thermalHeadroomRatio > 0f) {
            lastThermalHeadroom = health.thermalHeadroomRatio
        }
        lastPowerSourceLabel = health.powerSourceLabel
        val battPct = readBatteryPercentage(context)
        if (battPct in 1..100) lastBatteryPercent = battPct
        if (SolStreamService.isBroadcastLive) {
            captureLiveAudioPolicySnapshot(context)
            val currentMic = micStatusLabel.trim()
            if (currentMic.isNotBlank() && !currentMic.contains("STOPPED", ignoreCase = true)) {
                finalMicStatusSnapshot = currentMic
            }
        }
        sampleMemoryFootprint(forcePssWalk = false)
        return t to s
    }

    private fun sampleMemoryFootprint(forcePssWalk: Boolean = false) {
        try {
            val rt = Runtime.getRuntime()
            lastJavaHeapMb = ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).toInt()
            lastNativeHeapMb = (Debug.getNativeHeapAllocatedSize() / (1024L * 1024L)).toInt()
            val nowMs = SystemClock.elapsedRealtime()
            val totalMb = if (forcePssWalk || nowMs - lastPssSampleRealtimeMs >= 15_000L || peakTotalPssMb == 0) {
                lastPssSampleRealtimeMs = nowMs
                val pssKb = Debug.getPss()
                if (pssKb > 0L) (pssKb / 1024L).toInt() else (lastJavaHeapMb + lastNativeHeapMb)
            } else {
                max(peakTotalPssMb, lastJavaHeapMb + lastNativeHeapMb)
            }
            if (totalMb > peakTotalPssMb) peakTotalPssMb = totalMb
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun finishAndSaveBenchmark(context: Context, isGlipBaseline: Boolean): BenchmarkSnapshot {
        // Capture exact end-of-stream health BEFORE freezing session telemetry
        sampleDeviceHealth(context)
        val nowMs = System.currentTimeMillis()
        sessionEndMs = nowMs
        isSessionFinalized = true
        persistSavedClips(context)

        val durMin = if (sessionStartMs > 0L) {
            max(1, ((nowMs - sessionStartMs) / 60_000L).toInt())
        } else {
            1
        }
        val snap = BenchmarkSnapshot(
            label = if (isGlipBaseline) "Competitor Baseline" else "SolStream",
            durationMinutes = durMin,
            startBatteryTempC = startTempC,
            peakBatteryTempC = peakTempC,
            worstThermalStatus = worstThermalState,
            peakPssMb = peakTotalPssMb,
            droppedFrames = droppedVideoFrames,
            reconnects = reconnectAttempts
        )
        val prefs = (context.applicationContext ?: context).getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(if (isGlipBaseline) KEY_LAST_GLIP_BENCH else KEY_LAST_SOL_BENCH, snap.toJson().toString())
            .apply()
        clearLiveCheckpoint(context)
        return snap
    }

    fun readBatteryTempC(context: Context): Float {
        return try {
            val intent = (context.applicationContext ?: context).registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (tenths in 50..850) tenths / 10.0f else 0f
        } catch (_: Exception) {
            0f
        }
    }

    /**
     * Reads peak temperature across verified CPU/SoC/GPU thermal zones only (checking each zone's `type`
     * file first). Supports millidegrees, decidegrees, and whole degrees. Returns -1f if SELinux blocks /sys/class/thermal.
     */
    private fun readOptionalSocTempC(): Float {
        return try {
            var maxSocTemp = -1f
            for (idx in 0..25) {
                val typeFile = File("/sys/class/thermal/thermal_zone$idx/type")
                if (!typeFile.canRead()) continue
                val zoneType = typeFile.readText().trim()
                if (!isVerifiedSocThermalZoneType(zoneType)) continue

                val tempFile = File("/sys/class/thermal/thermal_zone$idx/temp")
                if (!tempFile.canRead()) continue
                val raw = tempFile.readText().trim().toLongOrNull() ?: continue
                val degC = parseThermalZoneTempC(raw)
                if (degC > maxSocTemp) {
                    maxSocTemp = degC
                }
            }
            maxSocTemp
        } catch (_: Throwable) {
            -1f
        }
    }

    fun readBatteryPercentage(context: Context): Int {
        return try {
            val intent = (context.applicationContext ?: context).registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level >= 0 && scale > 0) ((level * 100) / scale).coerceIn(0, 100) else 100
        } catch (_: Exception) {
            100
        }
    }

    fun isBatteryChargingOrPlugged(context: Context): Boolean {
        return try {
            val intent = (context.applicationContext ?: context).registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            plugged != 0
        } catch (_: Exception) {
            false
        }
    }

    fun readThermalStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        return try {
            val pm = (context.applicationContext ?: context).getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.currentThermalStatus
        } catch (_: Exception) {
            0
        }
    }

    fun readThermalHeadroom(context: Context): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return -1f
        val hr = CodecCapabilityProbe.readLiveHardwareHealth(context).thermalHeadroomRatio
        return if (hr.isNaN() || hr <= 0f) -1f else hr
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
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
        else -> "AudioDev($type)"
    }

    private fun buildActiveAudioPolicySummary(context: Context): String {
        if (SolStreamService.isBroadcastLive) {
            captureLiveAudioPolicySnapshot(context)
        }
        if (lastActiveAudioPolicySnapshot.isNotBlank()) {
            return if (SolStreamService.isBroadcastLive) {
                lastActiveAudioPolicySnapshot
            } else {
                "$lastActiveAudioPolicySnapshot (Captured Live)"
            }
        }
        return "No active AudioRecord sessions"
    }

    @Synchronized
    fun buildShareableReport(context: Context): String {
        if (!SolStreamService.isBroadcastLive && sessionStartMs == 0L) {
            getRecoveredLiveCheckpointReport(context)?.let { return it }
        }
        return buildShareableReportInternal(context, isLiveCheckpoint = false, skipHealthResample = false)
    }

    private fun buildShareableReportInternal(
        context: Context,
        isLiveCheckpoint: Boolean,
        skipHealthResample: Boolean
    ): String {
        if (!skipHealthResample && shouldSampleHealthOnReport(SolStreamService.isBroadcastLive)) {
            sampleDeviceHealth(context)
        }
        loadPersistedClipsIfEmpty(context)
        val scan = CodecCapabilityProbe.peekCachedOrPlaceholder(context)
        val prefs = (context.applicationContext ?: context).getSharedPreferences(PREFS_BENCH, Context.MODE_PRIVATE)
        val lastSol = BenchmarkSnapshot.fromJson(prefs.getString(KEY_LAST_SOL_BENCH, null))
        val lastGlip = BenchmarkSnapshot.fromJson(prefs.getString(KEY_LAST_GLIP_BENCH, null))
        val lastCrash = prefs.getString(KEY_LAST_CRASH, null)

        val effectiveMicLabel = if (!SolStreamService.isBroadcastLive &&
            micStatusLabel.contains("STOPPED", ignoreCase = true) &&
            finalMicStatusSnapshot.isNotBlank()
        ) {
            "$finalMicStatusSnapshot (Completed Cleanly)"
        } else {
            micStatusLabel
        }

        val durationSuffix = if (sessionStartMs > 0L) {
            val endMs = if (isSessionFinalized && sessionEndMs >= sessionStartMs) {
                sessionEndMs
            } else {
                System.currentTimeMillis()
            }
            val totalSec = max(0L, (endMs - sessionStartMs) / 1000L)
            val hh = totalSec / 3600L
            val mm = (totalSec % 3600L) / 60L
            val ss = totalSec % 60L
            String.format(Locale.US, " | Duration: %02d:%02d:%02d", hh, mm, ss)
        } else {
            ""
        }

        val sb = StringBuilder()
        sb.appendLine("=== SOLSTREAM STUDIO ULTRA $ENGINE_VERSION (SolApex™ HyperStream Engine) — AUTOMATIC SESSION REPORT ===")
        if (isLiveCheckpoint) {
            sb.appendLine("Checkpoint Status: LIVE / INCOMPLETE (Auto-saved at ${formatWallClockNow()} — survives sudden OS memory/thermal kills)")
        }
        sb.appendLine("Engine Architecture: SolApex™ HyperStream (Video: SolCore Ultra | Audio: SolLuxe™ 3.0 Pure | Broadcast: SolRelay Omega | Silicon: SolCodec)")
        sb.appendLine("Privacy Guarantee: Stream keys, notifications, and voice audio are NEVER included.")
        sb.appendLine(
            "Device: ${scan.friendlyHardwareLine()} | RAM: ${scan.totalRamGb}GB | Display: ${scan.maxDisplayHz}Hz | GL: ${scan.maxGlEsVersion}"
        )
        sb.appendLine(
            "Vault Security: ${KeystoreVault.getKeySecurityDescription()} | Targets: $activeSimulcastDesc | Power: $startPowerSourceLabel -> $lastPowerSourceLabel$durationSuffix"
        )
        sb.appendLine("Active Pipeline: $activePipelineDesc | Audio DSP: $activeAudioDspDesc | Overlays: $activeOverlaysDesc")

        val latestMbps = String.format(Locale.US, "%.2f Mbps", lastBitrateKbps / 1000.0)
        val avgMbps = String.format(Locale.US, "%.2f Mbps", getSessionAverageBitrateKbps() / 1000.0)
        val safeQFrames = max(0, lastQueueFrames)
        val safeQCap = max(30, lastQueueCapacity)
        val safeEnqueueMs = max(0L, lastSendLatencyMs)
        val queueStr = if (lastQueueBytes > 0L) {
            "$safeQFrames/$safeQCap frames (${lastQueueBytes / 1024} KB, ${safeEnqueueMs}ms enqueue)"
        } else {
            "$safeQFrames/$safeQCap frames (${safeEnqueueMs}ms enqueue)"
        }
        val rttLabel = if (lastRttSourceLabel.isNotBlank()) "Ingest RTT ($lastRttSourceLabel)" else "Ingest RTT"
        val rttStr = if (lastRttMs > 0L) "${lastRttMs} ms" else "N/A"
        val qpStr = if (lastAverageQp >= 0) "$lastAverageQp" else "N/A"
        val audioDropStr = if (droppedAudioFrames > 0) " | Audio Drops: $droppedAudioFrames" else ""

        sb.appendLine(
            "Latest Live Metrics: $latestMbps (Session Avg: $avgMbps) | Out FPS: $lastEncodedFps (Fresh Game FPS: $freshCaptureFps, Repeat Paced: $repeatedFramesCount, Avg QP: $qpStr) | Media Queue (A+V): $queueStr | $rttLabel: $rttStr | A/V Sync Offset: ${avDriftMs} ms$audioDropStr | Mic: $effectiveMicLabel"
        )

        val headroomStr = if (lastThermalHeadroom > 0f) {
            " | Android OS Thermal Headroom (PowerManager): ${(lastThermalHeadroom * 100).roundToInt()}% of Severe"
        } else {
            ""
        }
        val socTempStr = if (peakSocTempC > 0f) {
            String.format(Locale.US, " | Peak Verified SoC Sensor: %.1f°C", peakSocTempC)
        } else {
            ""
        }
        sb.appendLine(
            "Battery Temp & Memory: Start ${formatTempOrNa(startTempC)} -> Peak ${formatTempOrNa(peakTempC)}$socTempStr | Worst Thermal State: ${thermalStatusName(worstThermalState)}$headroomStr | App Process PSS: ${peakTotalPssMb} MB (Java Heap: ${lastJavaHeapMb} MB | Native Heap: ${lastNativeHeapMb} MB)"
        )
        sb.appendLine("Active Audio Policy: ${buildActiveAudioPolicySummary(context)}")
        if (!lastCrash.isNullOrBlank()) {
            sb.appendLine("Last Recorded Exception: $lastCrash")
        }

        if (clipHighlights.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("--- ★ SAVED STREAM CLIP HIGHLIGHTS & AI CLUTCH MOMENTS (FOR SHORTS / VODS) ---")
            for (clip in clipHighlights) {
                sb.appendLine("• $clip")
            }
        }

        if (lastSol != null || lastGlip != null) {
            sb.appendLine()
            sb.appendLine(resolveBenchmarkSectionHeader(hasCompetitorBenchmark = lastGlip != null))
            lastSol?.let { sb.appendLine("• ${it.toSummaryLine()}") }
            lastGlip?.let { sb.appendLine("• ${it.toSummaryLine()}") }
        }

        if (events.isEmpty()) {
            sb.appendLine()
            sb.appendLine("--- PLAIN-ENGLISH SESSION NOTES (CHRONOLOGICAL) ---")
            sb.appendLine("• No active stream events recorded yet.")
        } else {
            sb.appendLine()
            sb.appendLine("--- PLAIN-ENGLISH SESSION NOTES (CHRONOLOGICAL) ---")
            for (ev in events) {
                val tech = if (ev.technicalDetail.isNotBlank()) " [${ev.technicalDetail}]" else ""
                sb.appendLine("${ev.timestamp} [${ev.tag}] ${ev.plainMessage}$tech")
            }
        }
        return sb.toString()
    }
}
