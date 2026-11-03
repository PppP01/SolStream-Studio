package com.solgaming.solstreamstudio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private const val MAX_EVENTS = 240
    private const val BENCH_PREFS = "solstream_benchmarks"
    private val events = ArrayDeque<DiagnosticEvent>(MAX_EVENTS)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Volatile var sessionStartMs: Long = 0L
    @Volatile var startBatteryTempC: Float = 0f
    @Volatile var peakBatteryTempC: Float = 0f
    @Volatile var worstThermalStatus: Int = 0
    @Volatile var ramSampleSumMb: Long = 0L
    @Volatile var ramSampleCount: Int = 0
    @Volatile var droppedVideoFrames: Int = 0
    @Volatile var reconnectAttempts: Int = 0
    @Volatile var activeEncoderDesc: String = "Idle"
    @Volatile var lastQueueBytes: Long = 0L
    @Volatile var lastEncodedFps: Int = 0
    @Volatile var lastBitrateKbps: Int = 0
    @Volatile var avDriftMs: Long = 0L
    @Volatile var micStatusLabel: String = "IDLE"

    private fun redactSecrets(input: String): String {
        return input
            .replace(Regex("(rtmps?://[^/\\s]+/[^/\\s]+/)([^\\s]+)", RegexOption.IGNORE_CASE), "$1[REDACTED_KEY]")
            .replace(Regex("([a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4,})", RegexOption.IGNORE_CASE), "[REDACTED_KEY]")
    }

    @Synchronized
    fun startNewSession(context: Context, modeLabel: String, encoderDesc: String) {
        events.clear()
        sessionStartMs = System.currentTimeMillis()
        val temp = readBatteryTempC(context)
        startBatteryTempC = temp
        peakBatteryTempC = temp
        worstThermalStatus = readThermalStatus(context)
        ramSampleSumMb = 0L
        ramSampleCount = 0
        droppedVideoFrames = 0
        reconnectAttempts = 0
        activeEncoderDesc = encoderDesc
        lastQueueBytes = 0L
        avDriftMs = 0L
        log(
            "SESSION",
            "$modeLabel started ($encoderDesc).",
            "startTemp=${temp}C thermal=${thermalStatusName(worstThermalStatus)}"
        )
    }

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
        val ramMb = readAppMemoryMb()
        synchronized(this) {
            if (tempC > peakBatteryTempC) peakBatteryTempC = tempC
            if (thermal > worstThermalStatus) worstThermalStatus = thermal
            ramSampleSumMb += ramMb
            ramSampleCount++
        }
        return tempC to thermal
    }

    fun readBatteryTempC(context: Context): Float {
        return try {
            val intent = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            tenths / 10.0f
        } catch (_: Exception) {
            0f
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
                pm.getThermalHeadroom(10)
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
            (pssKb / 1024L).toInt()
        } catch (_: Exception) {
            val rt = Runtime.getRuntime()
            ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).toInt()
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
                "#$idx src=$srcName silenced=$silenced rate=${c.format.sampleRate}Hz ch=${c.format.channelCount} dev=${c.audioDevice?.type ?: "none"}"
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
        val prefix = if (isGlipBaseline) "glip" else "sol"
        context.applicationContext.getSharedPreferences(BENCH_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("${prefix}_exists", true)
            .putInt("${prefix}_mins", mins)
            .putFloat("${prefix}_start_temp", startBatteryTempC)
            .putFloat("${prefix}_peak_temp", peakBatteryTempC)
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
            startTempC = p.getFloat("${prefix}_start_temp", 0f),
            peakTempC = p.getFloat("${prefix}_peak_temp", 0f),
            avgRamMb = p.getInt("${prefix}_ram", 0),
            worstThermalLevel = p.getInt("${prefix}_thermal", 0),
            droppedFrames = p.getInt("${prefix}_drops", 0),
            reconnectCount = p.getInt("${prefix}_reconnects", 0)
        )
    }

    @Synchronized
    fun buildShareableReport(context: Context): String {
        val scan = CodecCapabilityProbe.scanDevice(context)
        val avgRam = if (ramSampleCount > 0) (ramSampleSumMb / ramSampleCount).toInt() else readAppMemoryMb()
        val headroom = readThermalHeadroom(context)
        val sb = StringBuilder()

        sb.appendLine("=== SOLSTREAM STUDIO — AUTOMATIC SESSION REPORT ===")
        sb.appendLine("Privacy Guarantee: Stream keys, notifications, and voice audio are NEVER included.")
        sb.appendLine("Device: ${scan.deviceModel} (${scan.socModel}) | Android ${scan.androidRelease} (API ${scan.sdkInt})")
        sb.appendLine("Active Pipeline: $activeEncoderDesc")
        sb.appendLine(
            "Latest Live Metrics: ${lastBitrateKbps / 1000.0} Mbps | Out FPS: $lastEncodedFps | " +
                "Send Queue: ${lastQueueBytes / 1024} KB | A/V Drift: ${avDriftMs} ms | Mic: $micStatusLabel"
        )
        sb.appendLine(
            "Thermal & Memory: Start ${startBatteryTempC}°C -> Peak ${peakBatteryTempC}°C | " +
                "Worst Thermal: ${thermalStatusName(worstThermalStatus)} | " +
                "Headroom: ${if (headroom >= 0f) "${(headroom * 100).roundToInt()}%" else "N/A"} | Avg RAM: ${avgRam} MB"
        )
        sb.appendLine("Active Audio Policy: ${inspectActiveAudioRecordings(context)}")
        sb.appendLine()

        val glip = loadBenchmark(context, "glip", "Glip Baseline")
        val sol = loadBenchmark(context, "sol", "SolStream Studio")
        if (glip != null || sol != null) {
            sb.appendLine("--- GLIP vs. SOLSTREAM BENCHMARK COMPARISON ---")
            glip?.let {
                sb.appendLine(
                    "• Glip (${it.durationMinutes}m): Start ${it.startTempC}°C -> Peak ${it.peakTempC}°C | " +
                        "Worst Thermal: ${thermalStatusName(it.worstThermalLevel)} | App RAM: ${it.avgRamMb} MB"
                )
            }
            sol?.let {
                sb.appendLine(
                    "• SolStream (${it.durationMinutes}m): Start ${it.startTempC}°C -> Peak ${it.peakTempC}°C | " +
                        "Worst Thermal: ${thermalStatusName(it.worstThermalLevel)} | App RAM: ${it.avgRamMb} MB | " +
                        "Drops: ${it.droppedFrames} | Reconnects: ${it.reconnectCount}"
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
