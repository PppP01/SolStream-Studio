/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class DeviceTier(
    val id: String,
    val title: String,
    val badgeSummary: String,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val defaultFps: Int,
    val defaultBitrateKbps: Int,
    val defaultAudioBitrateKbps: Int
) {
    LITE(
        id = "LITE",
        title = "Lite",
        badgeSummary = "Budget / Mobile-Data Safe • 1080p30 + Full 10-Stage Voice Filters",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultFps = 30,
        defaultBitrateKbps = 8000,
        defaultAudioBitrateKbps = 128
    ),
    BALANCED(
        id = "BALANCED",
        title = "Balanced",
        badgeSummary = "Mid-Range Smooth • 1080p60 + Full 10-Stage Voice Studio",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultFps = 60,
        defaultBitrateKbps = 12000,
        defaultAudioBitrateKbps = 160
    ),
    HIGH(
        id = "HIGH",
        title = "High",
        badgeSummary = "Upper Mid-Range • 1080p60/1440p60 + Bicubic + RNNoise Ready",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultFps = 60,
        defaultBitrateKbps = 16000,
        defaultAudioBitrateKbps = 192
    ),
    FLAGSHIP(
        id = "FLAGSHIP",
        title = "Flagship",
        badgeSummary = "Flagship Esports • 1440p60 HEVC + SOL LUXE Voice + 256k Master AAC",
        defaultWidth = 2560,
        defaultHeight = 1440,
        defaultFps = 60,
        defaultBitrateKbps = 20000,
        defaultAudioBitrateKbps = 256
    ),
    ULTRA(
        id = "ULTRA",
        title = "Ultra",
        badgeSummary = "Ultra Flagship • 4K60 / HW AV1 / 10-Bit HDR Unlocked + 320k Master AAC",
        defaultWidth = 2560,
        defaultHeight = 1440,
        defaultFps = 60,
        defaultBitrateKbps = 24000,
        defaultAudioBitrateKbps = 320
    );

    companion object {
        fun fromId(raw: String?): DeviceTier =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: FLAGSHIP
    }
}

data class HardwareEncoderReport(
    val codecKey: String,                // "HEVC", "H264", "AV1"
    val mimeType: String,
    val encoderName: String,
    val supports4k60: Boolean = false,
    val supports4k30: Boolean = false,
    val supports1440p60: Boolean,
    val supports1440p30: Boolean = false,
    val supports1080p60: Boolean,
    val supports1080p30: Boolean = true,
    val supports720p60: Boolean = true,
    val supports720p30: Boolean = true,
    val supports480p: Boolean = true,
    val supports360p: Boolean = true,
    val supportsCbr: Boolean,
    val supportsVbr: Boolean,
    val supports10BitHdr: Boolean = false,
    val supportsRoi: Boolean = false,
    val widthAlignment: Int = 2,
    val heightAlignment: Int = 2,
    val maxBitrateMbps: Int = 50,
    val maxConcurrentInstances: Int = 1,
    val complexityMin: Int,
    val complexityMax: Int,
    val verifiedSurfaceConfigure: Boolean,
    val verifiedRealEncode: Boolean = false
) {
    fun alignDimensions(rawWidth: Int, rawHeight: Int): Pair<Int, Int> {
        val wAlign = max(2, if (widthAlignment % 2 == 0) widthAlignment else widthAlignment * 2)
        val hAlign = max(2, if (heightAlignment % 2 == 0) heightAlignment else heightAlignment * 2)
        val alignedW = max(wAlign, ((rawWidth + (wAlign / 2)) / wAlign) * wAlign)
        val alignedH = max(hAlign, ((rawHeight + (hAlign / 2)) / hAlign) * hAlign)
        return alignedW to alignedH
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("codecKey", codecKey)
        put("mimeType", mimeType)
        put("encoderName", encoderName)
        put("supports4k60", supports4k60)
        put("supports4k30", supports4k30)
        put("supports1440p60", supports1440p60)
        put("supports1440p30", supports1440p30)
        put("supports1080p60", supports1080p60)
        put("supports1080p30", supports1080p30)
        put("supports720p60", supports720p60)
        put("supports720p30", supports720p30)
        put("supports480p", supports480p)
        put("supports360p", supports360p)
        put("supportsCbr", supportsCbr)
        put("supportsVbr", supportsVbr)
        put("supports10BitHdr", supports10BitHdr)
        put("supportsRoi", supportsRoi)
        put("widthAlignment", widthAlignment)
        put("heightAlignment", heightAlignment)
        put("maxBitrateMbps", maxBitrateMbps)
        put("maxConcurrentInstances", maxConcurrentInstances)
        put("complexityMin", complexityMin)
        put("complexityMax", complexityMax)
        put("verifiedSurfaceConfigure", verifiedSurfaceConfigure)
        put("verifiedRealEncode", verifiedRealEncode)
    }

    companion object {
        fun fromJson(obj: JSONObject?): HardwareEncoderReport? {
            if (obj == null) return null
            val name = obj.optString("encoderName", "")
            if (name.isBlank()) return null
            return HardwareEncoderReport(
                codecKey = obj.optString("codecKey", "H264"),
                mimeType = obj.optString("mimeType", MediaFormat.MIMETYPE_VIDEO_AVC),
                encoderName = name,
                supports4k60 = obj.optBoolean("supports4k60", false),
                supports4k30 = obj.optBoolean("supports4k30", false),
                supports1440p60 = obj.optBoolean("supports1440p60", false),
                supports1440p30 = obj.optBoolean("supports1440p30", obj.optBoolean("supports1440p60", false)),
                supports1080p60 = obj.optBoolean("supports1080p60", true),
                supports1080p30 = obj.optBoolean("supports1080p30", true),
                supports720p60 = obj.optBoolean("supports720p60", true),
                supports720p30 = obj.optBoolean("supports720p30", true),
                supports480p = obj.optBoolean("supports480p", true),
                supports360p = obj.optBoolean("supports360p", true),
                supportsCbr = obj.optBoolean("supportsCbr", true),
                supportsVbr = obj.optBoolean("supportsVbr", true),
                supports10BitHdr = obj.optBoolean("supports10BitHdr", false),
                supportsRoi = obj.optBoolean("supportsRoi", false),
                widthAlignment = obj.optInt("widthAlignment", 2),
                heightAlignment = obj.optInt("heightAlignment", 2),
                maxBitrateMbps = obj.optInt("maxBitrateMbps", 50),
                maxConcurrentInstances = obj.optInt("maxConcurrentInstances", 1),
                complexityMin = obj.optInt("complexityMin", 0),
                complexityMax = obj.optInt("complexityMax", 0),
                verifiedSurfaceConfigure = obj.optBoolean("verifiedSurfaceConfigure", true),
                verifiedRealEncode = obj.optBoolean("verifiedRealEncode", false)
            )
        }
    }
}

data class DeviceScanSummary(
    val deviceModel: String,
    val socModel: String,
    val androidRelease: String,
    val sdkInt: Int,
    val totalRamGb: Int = 8,
    val maxDisplayHz: Int = 120,
    val activeDisplayHz: Int = 60,
    val maxGlEsVersion: String = "3.0",
    val supportsGlEs30: Boolean = true,
    val supportsGlEs32: Boolean = false,
    val hasHardwareAac: Boolean = false,
    val hasPlatformHwNs: Boolean = false,
    val hasPlatformHwAec: Boolean = false,
    val hevcHardware: HardwareEncoderReport?,
    val h264Hardware: HardwareEncoderReport?,
    val av1Hardware: HardwareEncoderReport?,
    val recommendedTier: DeviceTier = DeviceTier.FLAGSHIP,
    val recommendedCodec: String,
    val recommendedWidth: Int,
    val recommendedHeight: Int,
    val recommendedFps: Int = 60,
    val recommendedBitrateKbps: Int = 20000
) {
    fun findReport(codecKey: String): HardwareEncoderReport? = when (codecKey) {
        "HEVC" -> hevcHardware
        "AV1" -> av1Hardware
        else -> h264Hardware
    }

    fun supportsDualFormatCompanion(): Boolean {
        val primary = hevcHardware ?: h264Hardware ?: return false
        return totalRamGb >= 8 && primary.maxConcurrentInstances >= 2 && primary.supports1080p60
    }

    fun toBadgeText(): String {
        val parts = mutableListOf<String>()
        parts.add("Tier: ${recommendedTier.title.uppercase(Locale.US)} (${totalRamGb}GB • ${maxDisplayHz}Hz • GL ES $maxGlEsVersion)")

        hevcHardware?.let {
            val topRes = when {
                it.supports4k60 -> "4K60"
                it.supports1440p60 -> "1440p60"
                it.supports1080p60 -> "1080p60"
                else -> "1080p30"
            }
            val extras = buildList {
                if (it.supports10BitHdr) add("HDR10")
                if (it.supportsRoi) add("ROI")
            }.joinToString("+")
            val extraTag = if (extras.isNotEmpty()) " • $extras" else ""
            parts.add("HW HEVC ($topRes$extraTag)")
        } ?: parts.add("HEVC: No HW")

        h264Hardware?.let {
            val topH264 = if (it.supports4k60) "4K60" else if (it.supports1440p60) "1440p60" else "1080p60"
            parts.add("HW H.264 ($topH264)")
        }

        if (av1Hardware != null && (av1Hardware.supports1440p60 || av1Hardware.supports1080p60)) {
            parts.add("HW AV1 (${av1Hardware.encoderName})")
        } else {
            parts.add("AV1: SW Blocked")
        }
        return parts.joinToString("  |  ")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("deviceModel", deviceModel)
        put("socModel", socModel)
        put("androidRelease", androidRelease)
        put("sdkInt", sdkInt)
        put("totalRamGb", totalRamGb)
        put("maxDisplayHz", maxDisplayHz)
        put("activeDisplayHz", activeDisplayHz)
        put("maxGlEsVersion", maxGlEsVersion)
        put("supportsGlEs30", supportsGlEs30)
        put("supportsGlEs32", supportsGlEs32)
        put("hasHardwareAac", hasHardwareAac)
        put("hasPlatformHwNs", hasPlatformHwNs)
        put("hasPlatformHwAec", hasPlatformHwAec)
        put("hevcHardware", hevcHardware?.toJson())
        put("h264Hardware", h264Hardware?.toJson())
        put("av1Hardware", av1Hardware?.toJson())
        put("recommendedTier", recommendedTier.id)
        put("recommendedCodec", recommendedCodec)
        put("recommendedWidth", recommendedWidth)
        put("recommendedHeight", recommendedHeight)
        put("recommendedFps", recommendedFps)
        put("recommendedBitrateKbps", recommendedBitrateKbps)
    }

    companion object {
        fun fromJson(obj: JSONObject): DeviceScanSummary {
            return DeviceScanSummary(
                deviceModel = obj.optString("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}"),
                socModel = obj.optString("socModel", Build.HARDWARE),
                androidRelease = obj.optString("androidRelease", "${Build.VERSION.SDK_INT}"),
                sdkInt = obj.optInt("sdkInt", Build.VERSION.SDK_INT),
                totalRamGb = obj.optInt("totalRamGb", 8),
                maxDisplayHz = obj.optInt("maxDisplayHz", 120),
                activeDisplayHz = obj.optInt("activeDisplayHz", 60),
                maxGlEsVersion = obj.optString("maxGlEsVersion", "3.0"),
                supportsGlEs30 = obj.optBoolean("supportsGlEs30", true),
                supportsGlEs32 = obj.optBoolean("supportsGlEs32", false),
                hasHardwareAac = obj.optBoolean("hasHardwareAac", false),
                hasPlatformHwNs = obj.optBoolean("hasPlatformHwNs", false),
                hasPlatformHwAec = obj.optBoolean("hasPlatformHwAec", false),
                hevcHardware = HardwareEncoderReport.fromJson(obj.optJSONObject("hevcHardware")),
                h264Hardware = HardwareEncoderReport.fromJson(obj.optJSONObject("h264Hardware")),
                av1Hardware = HardwareEncoderReport.fromJson(obj.optJSONObject("av1Hardware")),
                recommendedTier = DeviceTier.fromId(obj.optString("recommendedTier", "FLAGSHIP")),
                recommendedCodec = obj.optString("recommendedCodec", "HEVC"),
                recommendedWidth = obj.optInt("recommendedWidth", 2560),
                recommendedHeight = obj.optInt("recommendedHeight", 1440),
                recommendedFps = obj.optInt("recommendedFps", 60),
                recommendedBitrateKbps = obj.optInt("recommendedBitrateKbps", 20000)
            )
        }
    }
}

data class MicInputOption(
    val deviceId: Int,
    val label: String,
    val isBluetooth: Boolean,
    val deviceInfo: AudioDeviceInfo?
)

object CodecCapabilityProbe {

    private const val CACHE_PREFS = "solstream_hw_probe_cache_v3"
    private const val KEY_FINGERPRINT = "cached_fingerprint"
    private const val KEY_PAYLOAD = "cached_scan_json"
    private const val CACHE_SCHEMA_TAG = "ultra_v111_r1"

    private val probeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SolHwProbeWorker").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var cachedScan: DeviceScanSummary? = null

    fun peekCachedOrPlaceholder(context: Context): DeviceScanSummary {
        cachedScan?.let { return it }
        loadPersistedCache(context)?.let {
            cachedScan = it
            return it
        }
        val (ramGb, maxHz, curHz) = readQuickSystemTraits(context)
        val (glVer, gl30, gl32) = readGlEsTraits(context)
        val soc = readSocName()
        return DeviceScanSummary(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            socModel = soc,
            androidRelease = Build.VERSION.RELEASE ?: "${Build.VERSION.SDK_INT}",
            sdkInt = Build.VERSION.SDK_INT,
            totalRamGb = ramGb,
            maxDisplayHz = maxHz,
            activeDisplayHz = curHz,
            maxGlEsVersion = glVer,
            supportsGlEs30 = gl30,
            supportsGlEs32 = gl32,
            hevcHardware = null,
            h264Hardware = null,
            av1Hardware = null,
            recommendedTier = if (ramGb >= 8) DeviceTier.FLAGSHIP else DeviceTier.BALANCED,
            recommendedCodec = "HEVC",
            recommendedWidth = if (ramGb >= 8) 2560 else 1920,
            recommendedHeight = if (ramGb >= 8) 1440 else 1080,
            recommendedFps = 60,
            recommendedBitrateKbps = if (ramGb >= 8) 20000 else 12000
        )
    }

    fun scanDeviceAsync(
        context: Context,
        forceRefresh: Boolean = false,
        onComplete: (DeviceScanSummary) -> Unit
    ) {
        val appCtx = context.applicationContext
        if (!forceRefresh) {
            cachedScan?.let {
                onComplete(it)
                return
            }
            loadPersistedCache(appCtx)?.let { persisted ->
                cachedScan = persisted
                onComplete(persisted)
                return
            }
        }
        probeExecutor.execute {
            val result = scanDevice(appCtx, forceRefresh = true)
            mainHandler.post { onComplete(result) }
        }
    }

    @Synchronized
    fun scanDevice(context: Context, forceRefresh: Boolean = false): DeviceScanSummary {
        val appCtx = context.applicationContext
        if (!forceRefresh) {
            cachedScan?.let { return it }
            loadPersistedCache(appCtx)?.let { persisted ->
                cachedScan = persisted
                return persisted
            }
        }

        val (ramGb, maxHz, activeHz) = readQuickSystemTraits(appCtx)
        val (glVer, gl30, gl32) = readGlEsTraits(appCtx)
        val hwAac = checkHardwareAacPresent()
        val hwNs = try { NoiseSuppressor.isAvailable() } catch (_: Throwable) { false }
        val hwAec = try { AcousticEchoCanceler.isAvailable() } catch (_: Throwable) { false }

        val hevc = probeBestHardwareCodec("HEVC", MediaFormat.MIMETYPE_VIDEO_HEVC)
        val h264 = probeBestHardwareCodec("H264", MediaFormat.MIMETYPE_VIDEO_AVC)
        val av1 = probeBestHardwareCodec("AV1", "video/av01")

        val modelStr = "${Build.MANUFACTURER} ${Build.MODEL}"
        val av1Usable = av1 != null && av1.verifiedSurfaceConfigure && (av1.supports1440p60 || av1.supports1080p60)
        val hevc4k60 = hevc?.supports4k60 == true && hevc.verifiedSurfaceConfigure
        val hevc1440p60 = hevc?.supports1440p60 == true && hevc.verifiedSurfaceConfigure
        val hevc1080p60 = hevc?.supports1080p60 == true && hevc.verifiedSurfaceConfigure
        val h2641080p60 = h264?.supports1080p60 == true && h264.verifiedSurfaceConfigure

        val recTier: DeviceTier = when {
            (hevc4k60 || av1Usable) &&
                (ramGb >= 12 || av1Usable || modelStr.contains("Ultra", ignoreCase = true)) -> {
                DeviceTier.ULTRA
            }
            hevc1440p60 && ramGb >= 8 && maxHz >= 120 -> {
                DeviceTier.FLAGSHIP
            }
            (hevc1440p60 || hevc1080p60) && ramGb >= 6 && maxHz >= 90 -> {
                DeviceTier.HIGH
            }
            (hevc1080p60 || h2641080p60) && ramGb >= 5 -> {
                DeviceTier.BALANCED
            }
            else -> {
                DeviceTier.LITE
            }
        }

        val recCodec = when {
            hevc != null && hevc.verifiedSurfaceConfigure -> "HEVC"
            else -> "H264"
        }

        val recW: Int
        val recH: Int
        if ((recTier == DeviceTier.FLAGSHIP || recTier == DeviceTier.ULTRA) && hevc1440p60) {
            recW = 2560
            recH = 1440
        } else {
            recW = 1920
            recH = 1080
        }

        val summary = DeviceScanSummary(
            deviceModel = modelStr,
            socModel = readSocName(),
            androidRelease = Build.VERSION.RELEASE ?: "${Build.VERSION.SDK_INT}",
            sdkInt = Build.VERSION.SDK_INT,
            totalRamGb = ramGb,
            maxDisplayHz = maxHz,
            activeDisplayHz = activeHz,
            maxGlEsVersion = glVer,
            supportsGlEs30 = gl30,
            supportsGlEs32 = gl32,
            hasHardwareAac = hwAac,
            hasPlatformHwNs = hwNs,
            hasPlatformHwAec = hwAec,
            hevcHardware = hevc,
            h264Hardware = h264,
            av1Hardware = av1,
            recommendedTier = recTier,
            recommendedCodec = recCodec,
            recommendedWidth = recW,
            recommendedHeight = recH,
            recommendedFps = recTier.defaultFps,
            recommendedBitrateKbps = recTier.defaultBitrateKbps
        )

        cachedScan = summary
        savePersistedCache(appCtx, summary)
        return summary
    }

    private fun readSocName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim().ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }
    }

    private fun readQuickSystemTraits(context: Context): Triple<Int, Int, Int> {
        var ramGb = 8
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0L) {
                ramGb = max(2, ((memInfo.totalMem + 536_870_912L) / (1024L * 1024L * 1024L)).toInt())
            }
        } catch (_: Exception) {
        }

        var maxHz = 60
        var activeHz = 60
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                activeHz = display.refreshRate.roundToInt().coerceAtLeast(30)
                val modes = display.supportedModes
                maxHz = modes?.maxOfOrNull { it.refreshRate.roundToInt() }?.coerceAtLeast(activeHz) ?: activeHz
            }
        } catch (_: Exception) {
        }
        return Triple(ramGb, maxHz, activeHz)
    }

    private fun readGlEsTraits(context: Context): Triple<String, Boolean, Boolean> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val raw = am.deviceConfigurationInfo.reqGlEsVersion
            val major = (raw ushr 16) and 0xFFFF
            val minor = raw and 0xFFFF
            if (major >= 2) {
                val verStr = "$major.$minor"
                val has30 = major > 3 || (major == 3 && minor >= 0)
                val has32 = major > 3 || (major == 3 && minor >= 2)
                Triple(verStr, has30, has32)
            } else {
                Triple("3.0", true, false)
            }
        } catch (_: Exception) {
            Triple("3.0", true, false)
        }
    }

    private fun checkHardwareAacPresent(): Boolean {
        return try {
            val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            infos.any { info ->
                info.isEncoder &&
                    info.isHardwareAccelerated &&
                    !info.isSoftwareOnly &&
                    info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun loadPersistedCache(context: Context): DeviceScanSummary? {
        return try {
            val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            val savedFp = prefs.getString(KEY_FINGERPRINT, null) ?: return null
            val expectedFp = "${Build.FINGERPRINT}|$CACHE_SCHEMA_TAG"
            if (savedFp != expectedFp) return null
            val rawJson = prefs.getString(KEY_PAYLOAD, null) ?: return null
            DeviceScanSummary.fromJson(JSONObject(rawJson))
        } catch (_: Exception) {
            null
        }
    }

    private fun savePersistedCache(context: Context, summary: DeviceScanSummary) {
        try {
            val expectedFp = "${Build.FINGERPRINT}|$CACHE_SCHEMA_TAG"
            context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FINGERPRINT, expectedFp)
                .putString(KEY_PAYLOAD, summary.toJson().toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun isTrueHardwareEncoder(info: MediaCodecInfo): Boolean {
        if (!info.isEncoder) return false
        val nameLower = info.name.lowercase(Locale.US)
        if (nameLower.startsWith("omx.google.") ||
            nameLower.startsWith("c2.android.") ||
            nameLower.contains(".sw.") ||
            nameLower.endsWith(".sw") ||
            nameLower.contains("secure")
        ) {
            return false
        }
        return info.isHardwareAccelerated && !info.isSoftwareOnly
    }

    private fun probeBestHardwareCodec(codecKey: String, mime: String): HardwareEncoderReport? {
        val list = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        } catch (_: Exception) {
            return null
        }

        var bestReport: HardwareEncoderReport? = null
        var bestScore = -1

        for (info in list) {
            if (!isTrueHardwareEncoder(info)) continue
            if (info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) continue

            val candidate = evaluateSingleEncoderCandidate(codecKey, mime, info) ?: continue
            val score = scoreEncoderCandidate(candidate)
            if (score > bestScore) {
                bestScore = score
                bestReport = candidate
            }
        }
        return bestReport
    }

    internal fun scoreEncoderCandidate(r: HardwareEncoderReport): Int {
        var score = 0
        if (r.verifiedRealEncode) score += 1000
        if (r.verifiedSurfaceConfigure) score += 500
        if (r.supports4k60) score += 120
        if (r.supports1440p60) score += 80
        if (r.supports1080p60) score += 40
        if (r.supportsCbr) score += 25
        if (r.supports10BitHdr) score += 15
        if (r.supportsRoi) score += 10
        if (r.encoderName.startsWith("c2.", ignoreCase = true)) score += 20
        if (r.encoderName.contains("low_power", ignoreCase = true)) score -= 5
        return score
    }

    private fun evaluateSingleEncoderCandidate(
        codecKey: String,
        mime: String,
        info: MediaCodecInfo
    ): HardwareEncoderReport? {
        return try {
            val caps = info.getCapabilitiesForType(mime)
            val vCaps = caps.videoCapabilities ?: return null
            val eCaps = caps.encoderCapabilities

            val hasSurfaceColor = caps.colorFormats?.any {
                it == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            } ?: true
            if (!hasSurfaceColor) return null

            var ok4k60 = vCaps.areSizeAndRateSupported(3840, 2160, 60.0)
            var ok4k30 = vCaps.areSizeAndRateSupported(3840, 2160, 30.0)
            var ok1440p60 = vCaps.areSizeAndRateSupported(2560, 1440, 60.0)
            var ok1440p30 = vCaps.areSizeAndRateSupported(2560, 1440, 30.0)
            var ok1080p60 = vCaps.areSizeAndRateSupported(1920, 1080, 60.0)
            val ok1080p30 = vCaps.areSizeAndRateSupported(1920, 1080, 30.0)
            val ok720p60 = vCaps.areSizeAndRateSupported(1280, 720, 60.0)
            val ok720p30 = vCaps.areSizeAndRateSupported(1280, 720, 30.0)

            val perfPoints = vCaps.supportedPerformancePoints
            if (!perfPoints.isNullOrEmpty()) {
                val pp4k60 = MediaCodecInfo.VideoCapabilities.PerformancePoint(3840, 2160, 60)
                val pp4k30 = MediaCodecInfo.VideoCapabilities.PerformancePoint(3840, 2160, 30)
                val pp1440p60 = MediaCodecInfo.VideoCapabilities.PerformancePoint(2560, 1440, 60)
                val pp1440p30 = MediaCodecInfo.VideoCapabilities.PerformancePoint(2560, 1440, 30)
                val pp1080p60 = MediaCodecInfo.VideoCapabilities.PerformancePoint(1920, 1080, 60)
                ok4k60 = ok4k60 && perfPoints.any { it.covers(pp4k60) }
                ok4k30 = ok4k30 && perfPoints.any { it.covers(pp4k30) }
                ok1440p60 = ok1440p60 && perfPoints.any { it.covers(pp1440p60) }
                ok1440p30 = ok1440p30 && perfPoints.any { it.covers(pp1440p30) }
                ok1080p60 = ok1080p60 && perfPoints.any { it.covers(pp1080p60) }
            }

            val wAlign = max(2, vCaps.widthAlignment)
            val hAlign = max(2, vCaps.heightAlignment)
            val aligned480W = max(wAlign, ((854 + (wAlign / 2)) / wAlign) * wAlign)
            val aligned480H = max(hAlign, ((480 + (hAlign / 2)) / hAlign) * hAlign)
            val aligned360W = max(wAlign, ((640 + (wAlign / 2)) / wAlign) * wAlign)
            val aligned360H = max(hAlign, ((360 + (hAlign / 2)) / hAlign) * hAlign)

            val ok480p = vCaps.isSizeSupported(aligned480W, aligned480H)
            val ok360p = vCaps.isSizeSupported(aligned360W, aligned360H)

            val profileLevels = caps.profileLevels
            val hdr10Supported = profileLevels?.any { pl ->
                when (codecKey) {
                    "HEVC" -> pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                        pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                        pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
                    "AV1" -> pl.profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 ||
                        pl.profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 ||
                        pl.profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus
                    else -> false
                }
            } ?: false

            val roiSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    caps.isFeatureSupported("region-of-interest")
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }

            val maxMbps = ((vCaps.bitrateRange?.upper ?: 50_000_000) / 1_000_000).coerceIn(8, 120)
            val maxInstances = try {
                caps.maxSupportedInstances.coerceAtLeast(1)
            } catch (_: Exception) {
                1
            }

            val testW = when {
                ok1440p60 || ok1440p30 -> 2560
                ok1080p60 || ok1080p30 -> 1920
                else -> 1280
            }
            val testH = when (testW) {
                2560 -> 1440
                1920 -> 1080
                else -> 720
            }
            val testFps = if (ok1440p60 || ok1080p60 || ok720p60) 60 else 30
            val (surfaceOk, realEncodeOk) = verifyHardwareSurfaceEncoder(
                encoderName = info.name,
                mime = mime,
                width = testW,
                height = testH,
                fps = testFps
            )

            // Verify 4K surface configure independently before advertising 4K60/4K30 support
            val verified4k60 = ok4k60 && surfaceOk && verifySurfaceConfigureOnly(info.name, mime, 3840, 2160, 60)
            val verified4k30 = (ok4k30 || ok4k60) && surfaceOk &&
                (verified4k60 || verifySurfaceConfigureOnly(info.name, mime, 3840, 2160, 30))

            HardwareEncoderReport(
                codecKey = codecKey,
                mimeType = mime,
                encoderName = info.name,
                supports4k60 = verified4k60,
                supports4k30 = verified4k30,
                supports1440p60 = ok1440p60 && surfaceOk,
                supports1440p30 = (ok1440p30 || ok1440p60) && surfaceOk,
                supports1080p60 = ok1080p60 && surfaceOk,
                supports1080p30 = (ok1080p30 || ok1080p60) && surfaceOk,
                supports720p60 = ok720p60 && surfaceOk,
                supports720p30 = (ok720p30 || ok720p60) && surfaceOk,
                supports480p = ok480p && surfaceOk,
                supports360p = ok360p && surfaceOk,
                supportsCbr = eCaps?.isBitrateModeSupported(
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                ) ?: false,
                supportsVbr = eCaps?.isBitrateModeSupported(
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                ) ?: true,
                supports10BitHdr = hdr10Supported && surfaceOk,
                supportsRoi = roiSupported,
                widthAlignment = wAlign,
                heightAlignment = hAlign,
                maxBitrateMbps = maxMbps,
                maxConcurrentInstances = maxInstances,
                complexityMin = eCaps?.complexityRange?.lower ?: 0,
                complexityMax = eCaps?.complexityRange?.upper ?: 0,
                verifiedSurfaceConfigure = surfaceOk,
                verifiedRealEncode = realEncodeOk
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun verifySurfaceConfigureOnly(
        encoderName: String,
        mime: String,
        width: Int,
        height: Int,
        fps: Int
    ): Boolean {
        var codec: MediaCodec? = null
        var surf: Surface? = null
        return try {
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 24_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            val created = MediaCodec.createByCodecName(encoderName)
            codec = created
            created.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surf = created.createInputSurface()
            true
        } catch (_: Exception) {
            false
        } finally {
            try { surf?.release() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Counts an encode as verified ONLY when an actual encoded frame buffer (info.size > 0,
     * excluding BUFFER_FLAG_CODEC_CONFIG and INFO_OUTPUT_FORMAT_CHANGED) is dequeued.
     */
    internal fun isRealEncodedFrameOutput(outIdx: Int, size: Int, flags: Int): Boolean {
        if (outIdx < 0 || size <= 0) return false
        val isCodecConfig = (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
        return !isCodecConfig
    }

    private fun verifyHardwareSurfaceEncoder(
        encoderName: String,
        mime: String,
        width: Int,
        height: Int,
        fps: Int
    ): Pair<Boolean, Boolean> {
        var codec: MediaCodec? = null
        var inputSurface: Surface? = null
        var surfaceConfigured = false
        var producedFrameBytes = false

        try {
            val safeBps = if (width >= 2560) 16_000_000 else 10_000_000
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, safeBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            val created = MediaCodec.createByCodecName(encoderName)
            codec = created
            created.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surf = created.createInputSurface()
            inputSurface = surf
            created.start()
            surfaceConfigured = true

            val pushedGl = pushTwoSyntheticGlFrames(surf, width, height, fps)
            if (pushedGl) {
                try {
                    created.signalEndOfInputStream()
                } catch (_: Exception) {
                }
                val info = MediaCodec.BufferInfo()
                for (attempt in 0 until 8) {
                    val outIdx = created.dequeueOutputBuffer(info, 12_000L)
                    if (outIdx >= 0) {
                        if (isRealEncodedFrameOutput(outIdx, info.size, info.flags)) {
                            producedFrameBytes = true
                        }
                        created.releaseOutputBuffer(outIdx, false)
                        if (producedFrameBytes) break
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try { inputSurface?.release() } catch (_: Exception) {}
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
        }
        return surfaceConfigured to producedFrameBytes
    }

    private fun pushTwoSyntheticGlFrames(
        targetSurface: Surface,
        width: Int,
        height: Int,
        fps: Int
    ): Boolean {
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        val prevDisplay = EGL14.eglGetCurrentDisplay()
        val prevDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val prevRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        val prevCtx = EGL14.eglGetCurrentContext()

        return try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false
            val ver = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)) return false

            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numCfg = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numCfg, 0) || numCfg[0] <= 0) {
                return false
            }
            val cfg = configs[0] ?: return false

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, cfg, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) return false

            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                cfg,
                targetSurface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )
            if (eglSurface == EGL14.EGL_NO_SURFACE) return false
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return false

            val stepNs = 1_000_000_000L / min(60, max(30, fps)).toLong()
            GLES20.glViewport(0, 0, width, height)
            for (f in 0..1) {
                GLES20.glClearColor(0.05f * f, 0.10f, 0.15f, 1.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, (f + 1) * stepNs)
                if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) return false
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                if (prevCtx != EGL14.EGL_NO_CONTEXT && prevDisplay != EGL14.EGL_NO_DISPLAY) {
                    try { EGL14.eglMakeCurrent(prevDisplay, prevDraw, prevRead, prevCtx) } catch (_: Exception) {}
                } else {
                    try {
                        EGL14.eglMakeCurrent(
                            eglDisplay,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_CONTEXT
                        )
                    } catch (_: Exception) {}
                }
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    try { EGL14.eglDestroySurface(eglDisplay, eglSurface) } catch (_: Exception) {}
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    try { EGL14.eglDestroyContext(eglDisplay, eglContext) } catch (_: Exception) {}
                }
                if (prevDisplay == EGL14.EGL_NO_DISPLAY) {
                    try { EGL14.eglTerminate(eglDisplay) } catch (_: Exception) {}
                }
            }
        }
    }

    fun listAvailableMicInputs(context: Context): List<MicInputOption> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val result = mutableListOf(
            MicInputOption(
                deviceId = -1,
                label = "Auto (Phone Mic when BT Calls OFF)",
                isBluetooth = false,
                deviceInfo = null
            )
        )

        var builtInIndex = 1
        for (dev in inputs) {
            if (!dev.isSource) continue
            val (label, isBt) = when (dev.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> {
                    val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dev.address else ""
                    val posHint = when {
                        addr.contains("bottom", true) || addr.contains("primary", true) -> "Bottom/Primary"
                        addr.contains("back", true) || addr.contains("top", true) -> "Top/Secondary"
                        else -> "Mic #$builtInIndex"
                    }
                    builtInIndex++
                    "Phone Built-in ($posHint)" to false
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                    "Bluetooth Hands-Free (${dev.productName})" to true
                }
                AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                    "Bluetooth LE Headset (${dev.productName})" to true
                }
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                    "Wired Headset Mic (${dev.productName})" to false
                }
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> {
                    "USB-C Mic (${dev.productName})" to false
                }
                else -> continue
            }
            result.add(MicInputOption(dev.id, label, isBt, dev))
        }
        return result
    }
}
