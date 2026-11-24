/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PerformanceHintManager
import android.os.Process
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.solgaming.solstreamstudio.recorder.SolHudAndQrRenderer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SolVideoPipeline(private val context: Context) {

    data class ActiveVideoInfo(
        val codecKey: String,
        val encoderName: String,
        val outWidth: Int,
        val outHeight: Int,
        val fps: Int,
        val isVbr: Boolean,
        val isHdr10: Boolean = false,
        val glEngineLabel: String = "ES 3.0",
        val companionActive: Boolean = false
    )

    internal data class ScissorRect(val x: Int, val y: Int, val w: Int, val h: Int)

    private data class ConfiguredEncoder(
        val codec: MediaCodec,
        val inputSurface: Surface,
        val actualIsVbr: Boolean,
        val actualIsHdr10: Boolean
    )

    private data class EglInitResult(
        val primaryReady: Boolean,
        val companionReady: Boolean,
        val actualGlMajor: Int,
        val actualIs10Bit: Boolean,
        val engineSummary: String
    )

    @Volatile var isRunning = false
        private set

    @Volatile var activeSceneMode: String = "LIVE"

    var brbBlackoutActive: Boolean
        get() = activeSceneMode != "LIVE"
        set(value) {
            activeSceneMode = if (value) {
                if (activeSceneMode == "STARTING_SOON") "STARTING_SOON" else "BRB"
            } else {
                "LIVE"
            }
        }

    // Live-adjustable visual & filter states (0-ms hot-swap inside OpenGL render loop)
    @Volatile var liveFramingMode = "FIT"          // "FIT", "STRETCH", "SMART", "FILL"
    @Volatile var liveSmartZoomPercent = 108      // 100..250% (Landscape & 9:16 Shorts Center Zoom)
    @Volatile var liveLandscapeFlip = 0
    @Volatile var liveUpscalerMode = "BICUBIC"    // "BILINEAR", "BICUBIC", "LANCZOS"
    @Volatile var liveCinematicFilmic = false
    @Volatile var liveAmoledVivid = false
    @Volatile var liveShadowBoost = false
    @Volatile var liveColorTempFilter = "NEUTRAL"
    @Volatile var liveGpuSharpen = false
    @Volatile var liveShortsBackdropMode = "BLUR"
    @Volatile var liveOverlayImageEnabled = false
    @Volatile var liveOverlayPosition = "FULL"     // "FULL", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"
    @Volatile var liveBannerEnabled = false
    @Volatile var liveBannerText = ""
    @Volatile var liveBannerPosition = "TOP"
    @Volatile var liveMinimapShieldEnabled = false
    @Volatile var liveMinimapShieldCorner = "TOP_RIGHT"
    @Volatile var liveRoiMode = "AUTO"

    // Phase 1 (v1.5.0+) Pro Broadcast Overlays (strictly opt-in, 0 MB VRAM when OFF)
    @Volatile var liveHudTelemetryEnabled = false
    @Volatile var liveHudTelemetryCorner = "TOP_LEFT"
    @Volatile var liveHudTempC = 0f
    @Volatile var liveQrOverlayEnabled = false
    @Volatile var liveQrOverlayText = ""
    @Volatile var liveQrOverlayCorner = "BOTTOM_RIGHT"
    @Volatile var livePngTuberEnabled = false
    @Volatile var livePngTuberCorner = "BOTTOM_LEFT"
    @Volatile var livePngTuberSizePercent = 18
    @Volatile var liveVoiceGenderProfile = "NEUTRAL"
    @Volatile var liveVoiceSpeaking = false

    /**
     * Independent Dual-Layer Keyboard Privacy Blur:
     * Blurs the bottom half of the stream when the keyboard opens (or when manually toggled in the
     * Floating Pill) without disabling or interfering with the user's Minimap Blur Shield.
     */
    @Volatile var keyboardBlurActive: Boolean = false

    /** Automatic Severe-Thermal cooldown relief: steps 60 FPS pacing down to 30 FPS temporarily. */
    @Volatile var thermalThrottle30Fps = false

    /** Stage-1 Moderate-Warmth (>=44.0°C / 90% Thermal Pressure) repeat-frame 1-tap GPU cooler (keeps 100% quality on fresh frames). */
    @Volatile var thermalModerateCoolerActive = false

    /** Pauses secondary companion rendering first if Critical/Emergency warmth occurs. */
    @Volatile var companionPausedByThermal = false

    @Volatile var currentBitrateBps = 20_000_000
        private set
    @Volatile var companionBitrateBps = 8_000_000
        private set

    @Volatile var actualEncodedFps = 0
        private set
    @Volatile var actualEncodedBitrateBps = 0
        private set
    @Volatile var actualFreshCaptureFps = 0
        private set
    @Volatile var lastSentVideoPtsUs: Long = -1L
        private set
    @Volatile var activeGlEngineSummary: String = "OpenGL ES 3.0 Fast-FP16"
        private set

    @Volatile var onFatalPipelineError: ((String) -> Unit)? = null
    @Volatile var onCompanionPipelineStopped: ((String) -> Unit)? = null

    private val pendingCaptureResize = AtomicReference<Pair<Int, Int>?>(null)
    @Volatile private var capturedContentVisible = true

    // Async 1-Tap Clean Screenshot guard (3-second cooldown, max 1 in-flight offscreen FBO capture)
    private val screenshotInFlight = AtomicBoolean(false)
    private val pendingScreenshotCallback = AtomicReference<((Result<String>) -> Unit)?>(null)
    @Volatile private var lastScreenshotAcceptedMs = 0L

    private val codecReleaseLock = Any()
    private val projectionCallbackLock = Any()
    private var maxHardwareBitrateBps = 100_000_000
    private var maxCompanionBitrateBps = 20_000_000
    private var primarySupportsRoi = false
    private var primaryEncoderName = ""
    private var primaryOutW = 1920
    private var primaryOutH = 1080
    private var configuredKeyframeSec = 2

    @Volatile private var videoCodecInstance: MediaCodec? = null
    @Volatile private var companionCodecInstance: MediaCodec? = null
    @Volatile private var virtualDisplay: VirtualDisplay? = null
    @Volatile private var renderThread: Thread? = null
    @Volatile private var drainThread: Thread? = null
    @Volatile private var companionDrainThread: Thread? = null
    private var callbackHandlerThread: HandlerThread? = null
    private var projectionRef: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    fun setSceneMode(mode: String) {
        val norm = when (mode.uppercase(Locale.US)) {
            "STARTING_SOON" -> "STARTING_SOON"
            "BRB" -> "BRB"
            else -> "LIVE"
        }
        activeSceneMode = norm
    }

    fun applyLiveVisualSettings(s: StreamSettings) {
        liveFramingMode = s.framingMode.uppercase(Locale.US)
        liveSmartZoomPercent = s.smartZoomPercent.coerceIn(100, 250)
        liveLandscapeFlip = if (s.landscapeFlip == 180) 180 else 0
        liveUpscalerMode = s.upscalerMode.uppercase(Locale.US)
        liveCinematicFilmic = s.cinematicFilmic
        liveAmoledVivid = s.amoledVivid
        liveShadowBoost = s.shadowBoost
        liveColorTempFilter = s.colorTempFilter.uppercase(Locale.US)
        liveGpuSharpen = s.gpuSharpen
        liveShortsBackdropMode = s.shortsBackdropMode.uppercase(Locale.US)
        liveOverlayImageEnabled = s.overlayImageEnabled
        liveOverlayPosition = s.overlayPosition.uppercase(Locale.US)
        liveBannerEnabled = s.liveBannerEnabled
        liveBannerText = s.liveBannerText
        liveBannerPosition = s.liveBannerPosition.uppercase(Locale.US)
        liveMinimapShieldEnabled = s.minimapShieldEnabled
        liveMinimapShieldCorner = s.minimapShieldCorner.uppercase(Locale.US)
        liveRoiMode = s.roiMode.uppercase(Locale.US)

        // Phase 1 (v1.5.0+) Pro Broadcast Overlays
        liveHudTelemetryEnabled = s.hudTelemetryEnabled
        liveHudTelemetryCorner = KeystoreVault.sanitizeOverlayCorner(s.hudTelemetryCorner, "TOP_LEFT")
        liveQrOverlayEnabled = s.qrOverlayEnabled
        liveQrOverlayText = s.qrOverlayText.trim().take(KeystoreVault.MAX_QR_OVERLAY_CHARS)
        liveQrOverlayCorner = KeystoreVault.sanitizeOverlayCorner(s.qrOverlayCorner, "BOTTOM_RIGHT")
        livePngTuberEnabled = s.pngTuberEnabled
        livePngTuberCorner = KeystoreVault.sanitizeOverlayCorner(s.pngTuberCorner, "BOTTOM_LEFT")
        livePngTuberSizePercent = s.pngTuberSizePercent.coerceIn(12, 30)
        liveVoiceGenderProfile = s.voiceGenderProfile.uppercase(Locale.US)
    }

    /**
     * Triggers an async 1-Tap Clean Screenshot (pill-free YouTube thumbnail <=1080p) using a
     * temporary offscreen RGBA8 OpenGL FBO so MediaCodec's write-only encoder surface never returns black.
     * Enforces a 3-second cooldown and max 1 in-flight capture to keep RAM flat.
     */
    fun requestCleanScreenshot(onComplete: (Result<String>) -> Unit): Boolean {
        if (!isRunning) {
            onComplete(Result.failure(IllegalStateException("Stream is not active.")))
            return false
        }
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastScreenshotAcceptedMs < SCREENSHOT_COOLDOWN_MS) {
            return false
        }
        if (!screenshotInFlight.compareAndSet(false, true)) {
            return false
        }
        lastScreenshotAcceptedMs = nowMs
        pendingScreenshotCallback.set(onComplete)
        return true
    }

    fun setBitrateOnFly(newBitrateBps: Int) {
        val clamped = newBitrateBps.coerceIn(400_000, maxHardwareBitrateBps)
        currentBitrateBps = clamped
        try {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, clamped)
            }
            videoCodecInstance?.setParameters(params)
        } catch (_: Exception) {
        }
    }

    fun setCompanionBitrateOnFly(newBitrateBps: Int) {
        val clamped = newBitrateBps.coerceIn(800_000, maxCompanionBitrateBps)
        companionBitrateBps = clamped
        try {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, clamped)
            }
            companionCodecInstance?.setParameters(params)
        } catch (_: Exception) {
        }
    }

    fun requestSyncFrame() {
        val params = Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        }
        try { videoCodecInstance?.setParameters(params) } catch (_: Exception) {}
        try { companionCodecInstance?.setParameters(params) } catch (_: Exception) {}
    }

    /**
     * Immediately marks the video pipeline stopped and unregisters the MediaProjection callback
     * before async network/audio teardown begins, preventing false [CAPTURE_STOP] logs on user stop.
     */
    fun prepareForImmediateStop() {
        isRunning = false
        pendingCaptureResize.set(null)
        pendingScreenshotCallback.getAndSet(null)?.let { cb ->
            screenshotInFlight.set(false)
            try { cb(Result.failure(IllegalStateException("Stream stopped before screenshot completed."))) } catch (_: Exception) {}
        }
        unregisterProjectionCallbackImmediately()
    }

    private fun unregisterProjectionCallbackImmediately() {
        synchronized(projectionCallbackLock) {
            val cb = projectionCallback
            val proj = projectionRef
            projectionCallback = null
            projectionRef = null
            if (cb != null && proj != null) {
                try { proj.unregisterCallback(cb) } catch (_: Exception) {}
            }
            try { callbackHandlerThread?.quitSafely() } catch (_: Exception) {}
            callbackHandlerThread = null
        }
    }

    /**
     * Pushes Region-of-Interest (ROI) crosshair & minimap QP focus hints across Android Standard
     * API 30+ and vendor-specific Qualcomm Snapdragon / Samsung Exynos keys.
     */
    private fun pushHardwareRoiIfSupported(codec: MediaCodec, width: Int, height: Int, mode: String) {
        if (!primarySupportsRoi || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val m = mode.uppercase(Locale.US)
        if (m == "OFF") return

        try {
            val cTop = (height * 0.28f).roundToInt()
            val cBot = (height * 0.72f).roundToInt()
            val cLeft = (width * 0.30f).roundToInt()
            val cRight = (width * 0.70f).roundToInt()

            val rectSpec = when (m) {
                "CENTER" -> "$cTop,$cLeft-$cBot,$cRight=-3;"
                "HUD" -> {
                    val mBot = (height * 0.34f).roundToInt()
                    val mLeft = (width * 0.72f).roundToInt()
                    val bTop = (height * 0.74f).roundToInt()
                    "0,$mLeft-$mBot,$width=-2;$bTop,0-$height,$width=-2;"
                }
                else -> {
                    val mBot = (height * 0.32f).roundToInt()
                    val mLeft = (width * 0.74f).roundToInt()
                    "$cTop,$cLeft-$cBot,$cRight=-3;0,$mLeft-$mBot,$width=-2;"
                }
            }
            val encLower = primaryEncoderName.lowercase(Locale.US)
            val b = Bundle().apply {
                putString("qp-offset-rects", rectSpec)
                if (encLower.contains("qti") || encLower.contains("qcom")) {
                    putString("vendor.qti-ext-enc-roiinfo.rect-payload", rectSpec)
                }
            }
            codec.setParameters(b)
        } catch (_: Exception) {
        }
    }

    fun startPipeline(
        settings: StreamSettings,
        mediaProjection: MediaProjection,
        originTimeNs: Long,
        onProjectionStoppedBySystem: () -> Unit,
        onVideoConfig: (sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) -> Unit,
        onEncodedVideo: (ByteBuffer, MediaCodec.BufferInfo) -> Unit,
        enableDualCompanion: Boolean = false,
        onCompanionConfig: ((sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) -> Unit)? = null,
        onCompanionVideo: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null,
        companionIsLandscapeH264: Boolean = false
    ): ActiveVideoInfo? {
        if (isRunning) return null
        val (safeSettings, destWasClamped) = KeystoreVault.enforcePlatformLimits(settings)
        if (destWasClamped) {
            DiagnosticsLogger.log(
                "PLATFORM_GUARD",
                "Adjusted encoder settings to comply with ${safeSettings.platformPreset} streaming limits (${safeSettings.codec} ${safeSettings.width}x${safeSettings.height}@${safeSettings.fps}fps <= ${safeSettings.targetBitrateKbps / 1000} Mbps CBR)."
            )
        }

        applyLiveVisualSettings(safeSettings)
        thermalThrottle30Fps = false
        thermalModerateCoolerActive = false
        companionPausedByThermal = false
        capturedContentVisible = true
        liveVoiceSpeaking = false
        screenshotInFlight.set(false)
        pendingScreenshotCallback.set(null)
        pendingCaptureResize.set(null)
        lastSentVideoPtsUs = -1L
        actualEncodedFps = 0
        actualEncodedBitrateBps = 0
        actualFreshCaptureFps = 0
        configuredKeyframeSec = safeSettings.keyframeIntervalSec.coerceIn(1, 4)

        val scan = CodecCapabilityProbe.getBestAvailableScanNonBlocking(context)
        val strictH264Lock = KeystoreVault.isStrictH264Destination(safeSettings)
        val strictCbrLock = KeystoreVault.isStrictCbrDestination(safeSettings)
        val destinationMaxBitrateBps = KeystoreVault.maxVideoBitrateKbpsForDestination(safeSettings) * 1000

        val effectiveCodecReq = if (strictH264Lock) "H264" else safeSettings.codec
        val candidateReports = resolveCandidateHardwareEncoders(
            scan = scan,
            requestedCodec = effectiveCodecReq,
            strictCodecOnly = strictH264Lock
        )
        if (candidateReports.isEmpty()) {
            DiagnosticsLogger.log(
                "VIDEO_ERR",
                "No hardware video encoder matched $effectiveCodecReq. Software fallback is blocked to protect game FPS.",
                "requested=$effectiveCodecReq strictH264=$strictH264Lock"
            )
            return null
        }

        val preferEs30 = when (safeSettings.glEngineMode.uppercase(Locale.US)) {
            "ES20" -> false
            "ES30" -> true
            else -> scan.supportsGlEs30
        }
        val canRecord10BitEgl = safeSettings.hdr10Enabled &&
            preferEs30 &&
            !strictH264Lock &&
            canCreateRecordable10BitEglConfig()

        val requestedFps = if (safeSettings.fps <= 30) 30 else 60

        var chosenReport: HardwareEncoderReport? = null
        var configured: ConfiguredEncoder? = null
        var outWidth = 1920
        var outHeight = 1080
        var targetFps = requestedFps
        var startBps = 12_000_000

        // Live Hardware Encoder & Resolution Fallback Ladder
        outerLoop@ for ((reportIdx, candidate) in candidateReports.withIndex()) {
            if (strictCbrLock && !candidate.supportsCbr) {
                DiagnosticsLogger.log(
                    "VIDEO_SKIP",
                    "Skipped ${candidate.encoderName} because destination requires strict CBR and encoder does not advertise CBR."
                )
                continue
            }

            val (initialLong, initialShort, initialFps) = resolveSupportedResolutionAndFps(
                report = candidate,
                reqWidth = safeSettings.width,
                reqHeight = safeSettings.height,
                reqFps = requestedFps
            )
            val ladder = buildResolutionFallbackLadder(
                report = candidate,
                startLong = initialLong,
                startShort = initialShort,
                startFps = initialFps
            )
            val candidateMaxBps = min(
                candidate.maxBitrateMbps.coerceIn(4, 100) * 1_000_000,
                destinationMaxBitrateBps
            )
            val wantHdr10 = canRecord10BitEgl && candidate.supports10BitHdr

            for ((tierIdx, tier) in ladder.withIndex()) {
                val (tierLong, tierShort, tierFps) = tier
                val rawW = if (safeSettings.isVertical) tierShort else tierLong
                val rawH = if (safeSettings.isVertical) tierLong else tierShort
                val (alignedW, alignedH) = candidate.alignDimensions(rawW, rawH)

                val scaledKbps = if (tierIdx == 0) {
                    safeSettings.targetBitrateKbps.coerceIn(500, 100_000)
                } else {
                    when {
                        tierLong >= 2560 -> min(safeSettings.targetBitrateKbps, 18_000)
                        tierLong >= 1920 -> min(safeSettings.targetBitrateKbps, 12_000)
                        tierLong >= 1280 -> min(safeSettings.targetBitrateKbps, 6_000)
                        else -> min(safeSettings.targetBitrateKbps, 2_500)
                    }.coerceAtLeast(500)
                }
                val tierBps = (scaledKbps * 1000L).coerceIn(500_000L, candidateMaxBps.toLong()).toInt()

                val attemptCfg = configureHardwareSurfaceEncoder(
                    report = candidate,
                    width = alignedW,
                    height = alignedH,
                    fps = tierFps,
                    bitrateBps = tierBps,
                    wantVbr = safeSettings.useVbr && !strictCbrLock,
                    requireStrictCbr = strictCbrLock,
                    wantHdr10 = wantHdr10 && tierIdx == 0,
                    settings = safeSettings
                )
                if (attemptCfg != null) {
                    chosenReport = candidate
                    configured = attemptCfg
                    outWidth = alignedW
                    outHeight = alignedH
                    targetFps = tierFps
                    maxHardwareBitrateBps = candidateMaxBps
                    startBps = tierBps
                    if (reportIdx > 0 || tierIdx > 0) {
                        DiagnosticsLogger.log(
                            "VIDEO_FALLBACK",
                            "Hardware encoder automatically stepped to ${candidate.codecKey} (${candidate.encoderName}) ${alignedW}x${alignedH}@${tierFps}fps.",
                            "req=${safeSettings.width}x${safeSettings.height}@$requestedFps"
                        )
                    }
                    break@outerLoop
                }
            }
        }

        val finalReport = chosenReport ?: return null
        val finalConfigured = configured ?: return null

        primaryOutW = outWidth
        primaryOutH = outHeight
        primarySupportsRoi = finalReport.supportsRoi
        primaryEncoderName = finalReport.encoderName
        currentBitrateBps = startBps

        val codec = finalConfigured.codec
        val encoderInputSurface = finalConfigured.inputSurface
        videoCodecInstance = codec

        // Secondary Hardware Encoder setup:
        // Selects ONLY from codecs that passed the simultaneous-instance requirement in CodecCapabilityProbe.
        var compConfigured: ConfiguredEncoder? = null
        var selectedCompReport: HardwareEncoderReport? = null
        var compAlignedW = if (companionIsLandscapeH264) 1920 else 1080
        var compAlignedH = if (companionIsLandscapeH264) 1080 else 1920
        var compFps = min(targetFps, 60)

        if (enableDualCompanion &&
            (!safeSettings.isVertical || companionIsLandscapeH264) &&
            !finalConfigured.actualIsHdr10 &&
            onCompanionConfig != null &&
            onCompanionVideo != null
        ) {
            val eligibleCompReports = scan.eligibleCompanionEncoders(
                primaryCodec = finalReport.codecKey,
                requireH264Only = companionIsLandscapeH264
            )
            if (eligibleCompReports.isEmpty()) {
                DiagnosticsLogger.log(
                    "DUAL_SKIP",
                    "Companion encoder skipped: hardware reported no codec supporting simultaneous 1080p instances.",
                    "primary=${finalReport.codecKey} requireH264=$companionIsLandscapeH264"
                )
            } else {
                val rawCompW = if (companionIsLandscapeH264) 1920 else 1080
                val rawCompH = if (companionIsLandscapeH264) 1080 else 1920
                val compCapBps = if (companionIsLandscapeH264) {
                    if (safeSettings.simulcastTwitchEnabled) 6_000_000 else 8_000_000
                } else {
                    20_000_000
                }
                val compStartBps = if (companionIsLandscapeH264) {
                    compCapBps
                } else {
                    (safeSettings.dualFormatBitrateKbps.coerceIn(1500, 20000) * 1000).coerceAtMost(compCapBps)
                }
                val compRequireStrictCbr = companionIsLandscapeH264
                for (candidateComp in eligibleCompReports) {
                    if (compRequireStrictCbr && !candidateComp.supportsCbr) continue
                    val (w, h) = candidateComp.alignDimensions(rawCompW, rawCompH)
                    val fps = if (candidateComp.supports1080p60 && targetFps >= 60) 60 else 30
                    val attempt = configureHardwareSurfaceEncoder(
                        report = candidateComp,
                        width = w,
                        height = h,
                        fps = fps,
                        bitrateBps = compStartBps,
                        wantVbr = false,
                        requireStrictCbr = compRequireStrictCbr,
                        wantHdr10 = false,
                        settings = safeSettings.copy(
                            isVertical = !companionIsLandscapeH264,
                            keyframeIntervalSec = 2,
                            maxBFrames = 0
                        )
                    )
                    if (attempt != null) {
                        compConfigured = attempt
                        selectedCompReport = candidateComp
                        compAlignedW = w
                        compAlignedH = h
                        compFps = fps
                        maxCompanionBitrateBps = compCapBps
                        companionBitrateBps = compStartBps
                        companionCodecInstance = attempt.codec
                        break
                    }
                }
                if (compConfigured == null) {
                    DiagnosticsLogger.log(
                        "DUAL_SKIP",
                        "Companion encoder could not configure any eligible concurrent codec; continuing primary stream only.",
                        "tried=${eligibleCompReports.joinToString(",") { it.codecKey }}"
                    )
                }
            }
        }

        val (rawScreenW, rawScreenH, densityDpi) = readPhysicalDisplayMetrics()
        val panelLong = evenDimension(max(rawScreenW, rawScreenH).coerceAtLeast(1280))
        val panelShort = evenDimension(min(rawScreenW, rawScreenH).coerceAtLeast(720))
        val capWidth = if (safeSettings.isVertical && !isDisplayCurrentlyLandscape()) panelShort else panelLong
        val capHeight = if (capWidth == panelShort) panelLong else panelShort

        // Android 14+ strict invariant: register MediaProjection.Callback BEFORE createVirtualDisplay()
        synchronized(projectionCallbackLock) {
            val cbThread = HandlerThread("SolProjectionCb").apply { start() }
            callbackHandlerThread = cbThread
            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (isRunning) {
                        DiagnosticsLogger.log(
                            "CAPTURE_STOP",
                            "Android stopped screen capture (phone locked or permission ended). Re-approval needed to resume video.",
                            "MediaProjection.Callback.onStop"
                        )
                        onProjectionStoppedBySystem()
                    }
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    val safeW = evenDimension(width)
                    val safeH = evenDimension(height)
                    if (!isRunning) return

                    // Unified Graphics/Gralloc PSS Resize Guard (16:9 Landscape & 9:16 Vertical):
                    // Ignores resize callbacks while captured game is hidden/unfocused (!capturedContentVisible)
                    // AND ignores temporary portrait resizes during 16:9 landscape streams.
                    if (!shouldAcceptCaptureResize(
                            isVerticalPrimary = safeSettings.isVertical,
                            reqW = safeW,
                            reqH = safeH,
                            isVisible = capturedContentVisible,
                            currentCapW = 0,
                            currentCapH = 0
                        )
                    ) {
                        return
                    }
                    pendingCaptureResize.set(safeW to safeH)
                }

                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    if (isVisible == capturedContentVisible) return
                    capturedContentVisible = isVisible
                    if (!isVisible) {
                        pendingCaptureResize.set(null)
                    }
                    if (!isRunning) return
                    if (!isVisible) {
                        DiagnosticsLogger.log(
                            "CAPTURE_VIS",
                            "Single-app capture target moved to background; holding steady broadcast cadence.",
                            "visible=false"
                        )
                    } else {
                        DiagnosticsLogger.log(
                            "CAPTURE_VIS",
                            "Single-app capture target returned to foreground.",
                            "visible=true"
                        )
                    }
                }
            }
            projectionRef = mediaProjection
            projectionCallback = cb
            mediaProjection.registerCallback(cb, Handler(cbThread.looper))
        }

        isRunning = true

        drainThread = Thread({
            runEncoderDrainLoop(
                codec = codec,
                codecKey = finalReport.codecKey,
                originTimeUs = originTimeNs / 1000L,
                isPrimaryStream = true,
                onVideoConfig = onVideoConfig,
                onEncodedVideo = onEncodedVideo
            )
        }, "SolVideoDrain").apply { start() }

        val eglLatch = CountDownLatch(1)
        val eglInitRef = AtomicReference(
            EglInitResult(
                primaryReady = false,
                companionReady = false,
                actualGlMajor = if (preferEs30) 3 else 2,
                actualIs10Bit = false,
                engineSummary = "Initializing"
            )
        )

        val compSurf = compConfigured?.inputSurface
        val preferGlEs30 = preferEs30
        renderThread = Thread({
            runGlRenderLoop(
                mediaProjection = mediaProjection,
                encoderInputSurface = encoderInputSurface,
                companionInputSurface = compSurf,
                companionWidth = compAlignedW,
                companionHeight = compAlignedH,
                preferGlEs30 = preferGlEs30,
                use10BitEgl = finalConfigured.actualIsHdr10,
                initialCapWidth = capWidth,
                initialCapHeight = capHeight,
                panelLong = panelLong,
                panelShort = panelShort,
                densityDpi = densityDpi,
                outWidth = outWidth,
                outHeight = outHeight,
                isVerticalPrimary = safeSettings.isVertical,
                targetFps = targetFps,
                originTimeNs = originTimeNs,
                onEglInitialized = { res ->
                    eglInitRef.set(res)
                    eglLatch.countDown()
                }
            )
        }, "SolGlFramePacer").apply { start() }

        val latchCompleted = try {
            eglLatch.await(3000, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            false
        }
        val eglInitResult = eglInitRef.get()

        if (!latchCompleted || !eglInitResult.primaryReady) {
            DiagnosticsLogger.log(
                "GL_ERR",
                "Video capture/EGL initialization did not complete cleanly (latch=$latchCompleted ready=${eglInitResult.primaryReady})."
            )
            stopPipeline()
            return null
        }

        val activeCompReport = selectedCompReport
        val verifiedCompanionActive = compConfigured != null &&
            activeCompReport != null &&
            eglInitResult.companionReady &&
            onCompanionConfig != null &&
            onCompanionVideo != null

        if (verifiedCompanionActive && compConfigured != null && activeCompReport != null && onCompanionConfig != null && onCompanionVideo != null) {
            val modeDesc = if (companionIsLandscapeH264) "Split-Encode 16:9 H.264 Simulcast" else "Dual-Format 9:16 Shorts Companion"
            DiagnosticsLogger.log(
                "DUAL_ENC",
                "$modeDesc hardware encoder & EGL surface active (${compAlignedW}x${compAlignedH}@${compFps}fps).",
                "encoder=${activeCompReport.encoderName} codec=${activeCompReport.codecKey}"
            )
            companionDrainThread = Thread({
                runEncoderDrainLoop(
                    codec = compConfigured.codec,
                    codecKey = activeCompReport.codecKey,
                    originTimeUs = originTimeNs / 1000L,
                    isPrimaryStream = false,
                    onVideoConfig = onCompanionConfig,
                    onEncodedVideo = onCompanionVideo
                )
            }, "SolCompanionDrain").apply { start() }
        } else if (compConfigured != null) {
            releaseCompanionCodecSafely()
        }

        val finalHdrActive = finalConfigured.actualIsHdr10 && eglInitResult.actualIs10Bit
        return ActiveVideoInfo(
            codecKey = finalReport.codecKey,
            encoderName = finalReport.encoderName,
            outWidth = outWidth,
            outHeight = outHeight,
            fps = targetFps,
            isVbr = finalConfigured.actualIsVbr,
            isHdr10 = finalHdrActive,
            glEngineLabel = eglInitResult.engineSummary,
            companionActive = verifiedCompanionActive
        )
    }

    private fun releasePrimaryCodecSafely() {
        synchronized(codecReleaseLock) {
            val c = videoCodecInstance ?: return
            videoCodecInstance = null
            try { c.stop() } catch (_: Exception) {}
            try { c.release() } catch (_: Exception) {}
        }
    }

    private fun releaseCompanionCodecSafely() {
        synchronized(codecReleaseLock) {
            val c = companionCodecInstance ?: return
            companionCodecInstance = null
            try { c.stop() } catch (_: Exception) {}
            try { c.release() } catch (_: Exception) {}
        }
    }

    private fun releaseVirtualDisplaySafely() {
        synchronized(codecReleaseLock) {
            val vd = virtualDisplay ?: return
            virtualDisplay = null
            try { vd.release() } catch (_: Exception) {}
        }
    }

    /**
     * Thread-safe pipeline shutdown: unregisters MediaProjection callback first so no late
     * [CAPTURE_STOP] event fires, then joins/interrupts worker threads and releases codecs/VirtualDisplay safely.
     */
    fun stopPipeline() {
        prepareForImmediateStop()
        val rt = renderThread
        val dt = drainThread
        val cdt = companionDrainThread

        try { rt?.join(1500) } catch (_: Exception) {}
        if (rt?.isAlive == true) {
            try { rt.interrupt() } catch (_: Exception) {}
            try { rt.join(600) } catch (_: Exception) {}
        }
        if (rt?.isAlive != true) {
            renderThread = null
            releaseVirtualDisplaySafely()
        }

        synchronized(codecReleaseLock) {
            try { videoCodecInstance?.signalEndOfInputStream() } catch (_: Exception) {}
            try { companionCodecInstance?.signalEndOfInputStream() } catch (_: Exception) {}
        }

        try { dt?.join(1500) } catch (_: Exception) {}
        if (dt?.isAlive == true) {
            try { dt.interrupt() } catch (_: Exception) {}
            try { dt.join(600) } catch (_: Exception) {}
        }
        if (dt?.isAlive != true) {
            drainThread = null
            releasePrimaryCodecSafely()
        }

        try { cdt?.join(1200) } catch (_: Exception) {}
        if (cdt?.isAlive == true) {
            try { cdt.interrupt() } catch (_: Exception) {}
            try { cdt.join(500) } catch (_: Exception) {}
        }
        if (cdt?.isAlive != true) {
            companionDrainThread = null
            releaseCompanionCodecSafely()
        }

        actualEncodedFps = 0
        actualEncodedBitrateBps = 0
        actualFreshCaptureFps = 0
    }

    private fun evenDimension(value: Int): Int {
        val v = max(2, value)
        return if (v % 2 == 0) v else v - 1
    }

    private fun isDisplayCurrentlyLandscape(): Boolean {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
            val rot = display?.rotation ?: Surface.ROTATION_0
            rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270
        } catch (_: Exception) {
            false
        }
    }

    private fun readPhysicalDisplayMetrics(): Triple<Int, Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val densityDpi = context.resources.displayMetrics.densityDpi.coerceAtLeast(160)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), densityDpi)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi.coerceAtLeast(160))
        }
    }

    /**
     * When strictCodecOnly is true (Single-Encode Twitch/Kick Simulcast or Twitch/Kick Primary Preset),
     * strictly returns ONLY H.264 hardware encoders and never falls back to HEVC/AV1.
     */
    private fun resolveCandidateHardwareEncoders(
        scan: DeviceScanSummary,
        requestedCodec: String,
        strictCodecOnly: Boolean = false
    ): List<HardwareEncoderReport> {
        val rawCandidates = buildList {
            scan.findReport(requestedCodec)?.let { add(it) }
            if (!strictCodecOnly) {
                if (requestedCodec != "H264") {
                    scan.hevcHardware?.let { add(it) }
                }
                scan.h264Hardware?.let { add(it) }
                scan.hevcHardware?.let { add(it) }
            }
        }.distinctBy { it.encoderName }

        val realVerified = rawCandidates.filter { it.verifiedRealEncode }
        val surfaceVerified = rawCandidates.filter { !it.verifiedRealEncode && it.verifiedSurfaceConfigure }
        val metadataAdvertised = rawCandidates.filter {
            !it.verifiedRealEncode && !it.verifiedSurfaceConfigure && it.advertisedByMetadata
        }
        return realVerified + surfaceVerified + metadataAdvertised
    }

    private fun selectSupportedProfileLevel(
        codec: MediaCodec,
        mimeType: String,
        codecKey: String,
        width: Int,
        height: Int,
        wantHdr10: Boolean
    ): Pair<Int, Int>? {
        return try {
            val caps = codec.codecInfo.getCapabilitiesForType(mimeType)
            val levels = caps.profileLevels ?: return null
            val maxDim = max(width, height)

            when (codecKey) {
                "H264" -> {
                    val targetProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
                    val minLevel = when {
                        maxDim >= 3840 -> MediaCodecInfo.CodecProfileLevel.AVCLevel52
                        maxDim >= 2560 -> MediaCodecInfo.CodecProfileLevel.AVCLevel51
                        maxDim >= 1920 -> MediaCodecInfo.CodecProfileLevel.AVCLevel42
                        else -> MediaCodecInfo.CodecProfileLevel.AVCLevel31
                    }
                    val match = levels
                        .filter { it.profile == targetProfile && it.level >= minLevel }
                        .minByOrNull { it.level }
                        ?: levels.filter { it.profile == targetProfile }.maxByOrNull { it.level }
                    match?.let { it.profile to it.level }
                }
                "HEVC" -> {
                    val targetProfile = if (wantHdr10) {
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                    } else {
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
                    }
                    val minLevel = when {
                        maxDim >= 3840 -> MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51
                        maxDim >= 2560 -> MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5
                        maxDim >= 1920 -> MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41
                        else -> MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31
                    }
                    val match = levels
                        .filter { it.profile == targetProfile && it.level >= minLevel }
                        .minByOrNull { it.level }
                        ?: levels.filter { it.profile == targetProfile }.maxByOrNull { it.level }
                    match?.let { it.profile to it.level }
                }
                "AV1" -> {
                    val targetProfile = if (wantHdr10) {
                        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10
                    } else {
                        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8
                    }
                    val match = levels.filter { it.profile == targetProfile }.maxByOrNull { it.level }
                    match?.let { it.profile to it.level }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun configureHardwareSurfaceEncoder(
        report: HardwareEncoderReport,
        width: Int,
        height: Int,
        fps: Int,
        bitrateBps: Int,
        wantVbr: Boolean,
        requireStrictCbr: Boolean,
        wantHdr10: Boolean,
        settings: StreamSettings
    ): ConfiguredEncoder? {
        if (requireStrictCbr && !report.supportsCbr) {
            return null
        }

        for (attempt in 1..3) {
            var codec: MediaCodec? = null
            var inputSurface: Surface? = null
            try {
                val created = MediaCodec.createByCodecName(report.encoderName)
                codec = created
                val tryHdrThisAttempt = wantHdr10 && attempt == 1
                val requestedMode = when {
                    requireStrictCbr -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    wantVbr && report.supportsVbr -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                    report.supportsCbr -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    else -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                }

                val keyframeSec = settings.keyframeIntervalSec.coerceIn(1, 4)
                // Locked to 0 B-frames: RootEncoder 2.8.1 RTMP packetizer writes CTS=0
                val bFrames = 0

                val format = MediaFormat.createVideoFormat(report.mimeType, width, height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyframeSec)
                    setInteger(MediaFormat.KEY_BITRATE_MODE, requestedMode)

                    if (attempt <= 2) {
                        if (tryHdrThisAttempt) {
                            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                            setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_HLG)
                        } else {
                            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                            setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                        }
                    }

                    if (attempt <= 2) {
                        setInteger(MediaFormat.KEY_OPERATING_RATE, fps)
                        setInteger(MediaFormat.KEY_PRIORITY, 1)
                        setInteger(MediaFormat.KEY_MAX_B_FRAMES, bFrames)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, fps.toFloat())
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && settings.lowLatencyCodecHint) {
                            setInteger(MediaFormat.KEY_LATENCY, 1)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 0)
                            val qpMin = 14
                            setInteger(MediaFormat.KEY_VIDEO_QP_I_MIN, qpMin)
                            setInteger(MediaFormat.KEY_VIDEO_QP_P_MIN, qpMin)
                            setInteger(MediaFormat.KEY_VIDEO_QP_B_MIN, qpMin)
                            if (settings.qpMaxCap in 24..51 && attempt == 1) {
                                val qpCap = settings.qpMaxCap
                                setInteger(MediaFormat.KEY_VIDEO_QP_I_MAX, qpCap)
                                setInteger(MediaFormat.KEY_VIDEO_QP_P_MAX, qpCap)
                                setInteger(MediaFormat.KEY_VIDEO_QP_B_MAX, qpCap)
                            }
                            val encLower = report.encoderName.lowercase(Locale.US)
                            if (encLower.contains("exynos") || encLower.contains("sec")) {
                                setInteger("vendor.sec-ext-enc-qp-range.I-minQP", qpMin)
                                setInteger("vendor.sec-ext-enc-qp-range.P-minQP", qpMin)
                            }
                        } else {
                            setInteger("allow-frame-drop", 0)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            setInteger(
                                MediaFormat.KEY_VIDEO_ENCODING_STATISTICS_LEVEL,
                                MediaFormat.VIDEO_ENCODING_STATISTICS_LEVEL_1
                            )
                        }
                        if (report.supportsRoi && settings.roiMode.uppercase(Locale.US) != "OFF" && attempt == 1) {
                            try {
                                setFeatureEnabled("region-of-interest", true)
                            } catch (_: Exception) {
                            }
                        }
                        if (report.complexityMax > report.complexityMin) {
                            val midHigh = (report.complexityMin + report.complexityMax + 1) / 2
                            setInteger(MediaFormat.KEY_COMPLEXITY, midHigh)
                        }
                    }

                    if (attempt == 1) {
                        selectSupportedProfileLevel(
                            codec = created,
                            mimeType = report.mimeType,
                            codecKey = report.codecKey,
                            width = width,
                            height = height,
                            wantHdr10 = tryHdrThisAttempt
                        )?.let { (prof, lvl) ->
                            setInteger(MediaFormat.KEY_PROFILE, prof)
                            setInteger(MediaFormat.KEY_LEVEL, lvl)
                        }
                    }
                }

                created.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val surf = created.createInputSurface()
                inputSurface = surf
                created.start()

                val outFmt = try { created.outputFormat } catch (_: Exception) { null }
                val actualMode = try {
                    if (outFmt != null && outFmt.containsKey(MediaFormat.KEY_BITRATE_MODE)) {
                        outFmt.getInteger(MediaFormat.KEY_BITRATE_MODE)
                    } else {
                        requestedMode
                    }
                } catch (_: Exception) {
                    requestedMode
                }
                val isActualVbr = actualMode == MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                if (requireStrictCbr && isActualVbr) {
                    try { surf.release() } catch (_: Exception) {}
                    try { created.stop() } catch (_: Exception) {}
                    try { created.release() } catch (_: Exception) {}
                    DiagnosticsLogger.log(
                        "VIDEO_ERR",
                        "Hardware encoder ${report.encoderName} fell back to VBR on a strict CBR destination; rejecting attempt #$attempt."
                    )
                    continue
                }

                val verifiedHdrOutput = if (tryHdrThisAttempt && outFmt != null) {
                    try {
                        val hasHdrTransfer = outFmt.containsKey(MediaFormat.KEY_COLOR_TRANSFER) && (
                            outFmt.getInteger(MediaFormat.KEY_COLOR_TRANSFER) == MediaFormat.COLOR_TRANSFER_HLG ||
                                outFmt.getInteger(MediaFormat.KEY_COLOR_TRANSFER) == MediaFormat.COLOR_TRANSFER_ST2084
                            )
                        val has10BitProfile = outFmt.containsKey(MediaFormat.KEY_PROFILE) && (
                            outFmt.getInteger(MediaFormat.KEY_PROFILE) == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                                outFmt.getInteger(MediaFormat.KEY_PROFILE) == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                                outFmt.getInteger(MediaFormat.KEY_PROFILE) == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10
                            )
                        hasHdrTransfer || has10BitProfile
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }

                if (attempt > 1) {
                    DiagnosticsLogger.log(
                        "VIDEO_CFG",
                        "Hardware encoder ${report.encoderName} configured cleanly on attempt #$attempt.",
                        "${width}x${height}@${fps} mode=${if (isActualVbr) "VBR" else "CBR"}"
                    )
                }
                return ConfiguredEncoder(
                    codec = created,
                    inputSurface = surf,
                    actualIsVbr = isActualVbr,
                    actualIsHdr10 = verifiedHdrOutput
                )
            } catch (e: Exception) {
                try { inputSurface?.release() } catch (_: Exception) {}
                try { codec?.release() } catch (_: Exception) {}
                if (attempt == 3) {
                    DiagnosticsLogger.log(
                        "VIDEO_ERR",
                        "Hardware encoder ${report.encoderName} rejected ${width}x${height}@${fps} configuration.",
                        "${e.javaClass.simpleName}: ${e.message}"
                    )
                }
            }
        }
        return null
    }

    /**
     * Ensures EGL14.eglTerminate(dpy) is always called in finally when probing 10-bit recordable EGLConfig support.
     */
    private fun canCreateRecordable10BitEglConfig(): Boolean {
        var dpy: EGLDisplay = EGL14.EGL_NO_DISPLAY
        val prevDpy = EGL14.eglGetCurrentDisplay()
        return try {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (dpy == EGL14.EGL_NO_DISPLAY) return false
            val ver = IntArray(2)
            if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) return false
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 10,
                EGL14.EGL_GREEN_SIZE, 10,
                EGL14.EGL_BLUE_SIZE, 10,
                EGL14.EGL_ALPHA_SIZE, 2,
                EGL14.EGL_RENDERABLE_TYPE, 0x0040,
                0x3142, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            val ok = EGL14.eglChooseConfig(dpy, attribs, 0, configs, 0, 1, num, 0) &&
                num[0] > 0 &&
                configs[0] != null
            if (!ok) return false
            val redBits = IntArray(1)
            EGL14.eglGetConfigAttrib(dpy, configs[0], EGL14.EGL_RED_SIZE, redBits, 0)
            redBits[0] >= 10
        } catch (_: Exception) {
            false
        } finally {
            if (dpy != EGL14.EGL_NO_DISPLAY && prevDpy == EGL14.EGL_NO_DISPLAY) {
                try { EGL14.eglTerminate(dpy) } catch (_: Exception) {}
            }
        }
    }

    private fun chooseEncoderEglConfig(
        eglDisplay: EGLDisplay,
        wantEs30: Boolean,
        want10Bit: Boolean
    ): Triple<EGLConfig, Int, Boolean>? {
        val es3Bit = 0x0040
        val candidates = buildList {
            if (wantEs30 && want10Bit) add(Triple(es3Bit, 3, true))
            if (wantEs30) add(Triple(es3Bit, 3, false))
            add(Triple(EGL14.EGL_OPENGL_ES2_BIT, 2, false))
        }

        for ((renderBit, clientVer, tenBit) in candidates) {
            val rBits = if (tenBit) 10 else 8
            val aBits = if (tenBit) 2 else 8
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, rBits,
                EGL14.EGL_GREEN_SIZE, rBits,
                EGL14.EGL_BLUE_SIZE, rBits,
                EGL14.EGL_ALPHA_SIZE, aBits,
                EGL14.EGL_RENDERABLE_TYPE, renderBit,
                0x3142, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numCfg = IntArray(1)
            if (EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numCfg, 0) &&
                numCfg[0] > 0 &&
                configs[0] != null
            ) {
                val cfg = configs[0]!!
                val actualRed = IntArray(1)
                EGL14.eglGetConfigAttrib(eglDisplay, cfg, EGL14.EGL_RED_SIZE, actualRed, 0)
                val verified10Bit = tenBit && actualRed[0] >= 10
                return Triple(cfg, clientVer, verified10Bit)
            }
        }
        return null
    }

    private fun runGlRenderLoop(
        mediaProjection: MediaProjection,
        encoderInputSurface: Surface,
        companionInputSurface: Surface?,
        companionWidth: Int,
        companionHeight: Int,
        preferGlEs30: Boolean,
        use10BitEgl: Boolean,
        initialCapWidth: Int,
        initialCapHeight: Int,
        panelLong: Int,
        panelShort: Int,
        densityDpi: Int,
        outWidth: Int,
        outHeight: Int,
        isVerticalPrimary: Boolean,
        targetFps: Int,
        originTimeNs: Long,
        onEglInitialized: (EglInitResult) -> Unit
    ) {
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        var companionEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        var captureSurface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null
        var oesTexId = 0
        var oesProg = 0
        var rgbaProg = 0
        var initNotified = false
        var adpfSession: PerformanceHintManager.Session? = null

        var sceneTexId = 0
        var loadedSceneSlotKey = ""
        var overlayTexId = 0
        var overlayBitmapW = 1920
        var overlayBitmapH = 1080
        var overlayLoadAttemptedRev = -1
        var posterTexId = 0
        var posterLoadAttemptedRev = -1
        var bannerTexId = 0
        var loadedBannerSig = ""
        var seenAssetRevision = -1

        // Phase 1 (v1.5.0+) opt-in GPU overlays (0 MB VRAM when OFF)
        var hudTexId = 0
        var hudScratchBitmap: Bitmap? = null
        var lastHudUpdateMs = 0L

        var qrTexId = 0
        var loadedQrSig = ""

        var pngTuberIdleTexId = 0
        var pngTuberTalkTexId = 0
        var pngTuberBmpW = 512
        var pngTuberBmpH = 512
        var loadedPngTuberGender = ""
        var lastVoiceActiveMs = 0L
        var pngTuberBounceFrac = 0f

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val phm = context.getSystemService(PerformanceHintManager::class.java)
                    adpfSession = phm?.createHintSession(
                        intArrayOf(Process.myTid()),
                        1_000_000_000L / max(30, targetFps).toLong()
                    )
                } catch (_: Throwable) {
                }
            }

            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw IllegalStateException("EGL_DEFAULT_DISPLAY unavailable")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw IllegalStateException("eglInitialize failed")
            }

            val chosenCfgTriple = chooseEncoderEglConfig(eglDisplay, preferGlEs30, use10BitEgl)
                ?: throw IllegalStateException("No recordable EGLConfig available")
            var eglConfig = chosenCfgTriple.first
            var glClientVer = chosenCfgTriple.second
            var actual10BitEgl = chosenCfgTriple.third

            var ctx = EGL14.eglCreateContext(
                eglDisplay,
                eglConfig,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, glClientVer, EGL14.EGL_NONE),
                0
            )
            if (ctx == EGL14.EGL_NO_CONTEXT && glClientVer == 3) {
                val fallback20 = chooseEncoderEglConfig(eglDisplay, wantEs30 = false, want10Bit = false)
                    ?: throw IllegalStateException("OpenGL ES 2.0 fallback failed")
                eglConfig = fallback20.first
                glClientVer = 2
                actual10BitEgl = false
                ctx = EGL14.eglCreateContext(
                    eglDisplay,
                    eglConfig,
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                    0
                )
            }
            if (ctx == EGL14.EGL_NO_CONTEXT) {
                throw IllegalStateException("eglCreateContext failed")
            }
            eglContext = ctx

            val surfAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, encoderInputSurface, surfAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw IllegalStateException("Primary eglCreateWindowSurface failed")
            }

            if (companionInputSurface != null) {
                try {
                    val compCfg = if (actual10BitEgl) {
                        chooseEncoderEglConfig(eglDisplay, glClientVer >= 3, want10Bit = false)?.first ?: eglConfig
                    } else {
                        eglConfig
                    }
                    companionEglSurface = EGL14.eglCreateWindowSurface(
                        eglDisplay,
                        compCfg,
                        companionInputSurface,
                        surfAttribs,
                        0
                    )
                } catch (_: Exception) {
                    companionEglSurface = EGL14.EGL_NO_SURFACE
                }
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw IllegalStateException("Initial eglMakeCurrent failed")
            }

            val oesHandles = GlShaderBank.buildOesPipelineProgram(allowEs30Shader = (glClientVer >= 3))
            oesProg = oesHandles.programId
            val rgbaHandles = GlShaderBank.buildRgba2dProgram()
            rgbaProg = rgbaHandles.programId

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            oesTexId = texIds[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            var activeCapW = initialCapWidth
            var activeCapH = initialCapHeight
            var hasFirstFrame = false
            val frameSync = Object()
            var frameAvailable = false

            val st = SurfaceTexture(oesTexId).apply {
                setDefaultBufferSize(activeCapW, activeCapH)
                setOnFrameAvailableListener {
                    synchronized(frameSync) {
                        frameAvailable = true
                        hasFirstFrame = true
                        frameSync.notifyAll()
                    }
                }
            }
            surfaceTexture = st
            val capSurf = Surface(st)
            captureSurface = capSurf

            var lastAppliedSurfaceFps = targetFps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    capSurf.setFrameRate(
                        targetFps.toFloat(),
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
                    )
                } catch (_: Throwable) {
                }
            }

            val createdVd = mediaProjection.createVirtualDisplay(
                "SolStreamCapture",
                activeCapW,
                activeCapH,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                capSurf,
                null,
                null
            ) ?: throw IllegalStateException("createVirtualDisplay returned null")
            synchronized(codecReleaseLock) {
                virtualDisplay = createdVd
            }

            val fp16Tag = if (glClientVer >= 3) " Fast-FP16" else ""
            val summary = "OpenGL ES $glClientVer.0$fp16Tag" + (if (actual10BitEgl) " (10-Bit HDR)" else "")
            activeGlEngineSummary = summary
            initNotified = true
            onEglInitialized(
                EglInitResult(
                    primaryReady = true,
                    companionReady = (companionEglSurface != EGL14.EGL_NO_SURFACE),
                    actualGlMajor = glClientVer,
                    actualIs10Bit = actual10BitEgl,
                    engineSummary = summary
                )
            )
            DiagnosticsLogger.log("GL_INIT", "Initialized $activeGlEngineSummary SolCore Ultra render pipeline.")

            val fullQuadVerts = GlShaderBank.createFloatBuffer(
                floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            )
            val oesTexCoords = GlShaderBank.createFloatBuffer(
                floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
            )
            val bitmapTexCoords = GlShaderBank.createFloatBuffer(
                floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
            )
            val bannerTopVerts = GlShaderBank.createFloatBuffer(
                floatArrayOf(-0.92f, 0.78f, 0.92f, 0.78f, -0.92f, 0.95f, 0.92f, 0.95f)
            )
            val bannerBottomVerts = GlShaderBank.createFloatBuffer(
                floatArrayOf(-0.92f, -0.95f, 0.92f, -0.95f, -0.92f, -0.78f, 0.92f, -0.78f)
            )

            val stMatrix = FloatArray(16)
            val mvpMatrix = FloatArray(16)
            val overlayMvpMatrix = FloatArray(16)
            val identityMvp = FloatArray(16)
            Matrix.setIdentityM(stMatrix, 0)
            Matrix.setIdentityM(identityMvp, 0)

            var nextRenderNs = System.nanoTime()
            var lastSurfaceTsNs = -1L
            var freshWindowStartMs = System.currentTimeMillis()
            var lastRoiPushMs = 0L
            var lastWallKeyframePushMs = System.currentTimeMillis()
            var freshFramesInWindow = 0
            var repeatedFramesTotal = 0L
            var prevSceneMode = "LIVE"
            var consecutivePrimaryEglErrors = 0

            while (isRunning) {
                val sceneNow = activeSceneMode
                val effectiveFps = when {
                    sceneNow != "LIVE" -> min(targetFps, 30)
                    thermalThrottle30Fps -> min(targetFps, 30)
                    else -> targetFps
                }
                if (effectiveFps != lastAppliedSurfaceFps && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    lastAppliedSurfaceFps = effectiveFps
                    try {
                        capSurf.setFrameRate(
                            effectiveFps.toFloat(),
                            Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
                        )
                    } catch (_: Throwable) {
                    }
                }
                val frameIntervalNs = 1_000_000_000L / effectiveFps.toLong()

                val nowNs = System.nanoTime()
                val waitNs = nextRenderNs - nowNs
                if (waitNs > 1_000_000L) {
                    synchronized(frameSync) {
                        frameSync.wait(waitNs / 1_000_000L, (waitNs % 1_000_000L).toInt())
                    }
                    continue
                }

                val workStartNs = System.nanoTime()
                nextRenderNs += frameIntervalNs
                if (nowNs - nextRenderNs > frameIntervalNs * 2) {
                    nextRenderNs = nowNs + frameIntervalNs
                }

                val pendingSize = pendingCaptureResize.getAndSet(null)
                if (pendingSize != null) {
                    val (reqW, reqH) = pendingSize
                    if (shouldAcceptCaptureResize(
                            isVerticalPrimary = isVerticalPrimary,
                            reqW = reqW,
                            reqH = reqH,
                            isVisible = capturedContentVisible,
                            currentCapW = activeCapW,
                            currentCapH = activeCapH
                        )
                    ) {
                        activeCapW = reqW
                        activeCapH = reqH
                        try {
                            st.setDefaultBufferSize(activeCapW, activeCapH)
                            synchronized(codecReleaseLock) {
                                virtualDisplay?.resize(activeCapW, activeCapH, densityDpi)
                            }
                            DiagnosticsLogger.log(
                                "CAPTURE_RESIZE",
                                "Adapted incoming game capture size seamlessly without interrupting stream.",
                                "capture=${activeCapW}x${activeCapH} -> stream=${outWidth}x${outHeight}"
                            )
                        } catch (_: Exception) {
                        }
                    }
                }

                val pullNewTexture = synchronized(frameSync) {
                    if (frameAvailable) {
                        frameAvailable = false
                        true
                    } else {
                        false
                    }
                }
                var isFreshGameFrame = false
                if (pullNewTexture) {
                    st.updateTexImage()
                    st.getTransformMatrix(stMatrix)
                    val curTs = st.timestamp
                    if (curTs != lastSurfaceTsNs) {
                        lastSurfaceTsNs = curTs
                        freshFramesInWindow++
                        isFreshGameFrame = true
                    } else {
                        repeatedFramesTotal++
                    }
                } else if (sceneNow == "LIVE" && hasFirstFrame) {
                    repeatedFramesTotal++
                }

                val nowMs = System.currentTimeMillis()
                val monoMs = SystemClock.elapsedRealtime()
                val elapsedMs = nowMs - freshWindowStartMs
                if (elapsedMs >= 1000L) {
                    actualFreshCaptureFps = ((freshFramesInWindow * 1000L) / elapsedMs).toInt()
                    DiagnosticsLogger.freshCaptureFps = actualFreshCaptureFps
                    DiagnosticsLogger.repeatedFramesCount = repeatedFramesTotal
                    freshWindowStartMs = nowMs
                    freshFramesInWindow = 0

                    if (isVerticalPrimary &&
                        capturedContentVisible &&
                        activeCapW < activeCapH &&
                        isDisplayCurrentlyLandscape()
                    ) {
                        pendingCaptureResize.compareAndSet(null, panelLong to panelShort)
                    }
                }

                if (primarySupportsRoi && nowMs - lastRoiPushMs > 2500L) {
                    lastRoiPushMs = nowMs
                    videoCodecInstance?.let { pushHardwareRoiIfSupported(it, primaryOutW, primaryOutH, liveRoiMode) }
                }

                val sceneChanged = (sceneNow != prevSceneMode)
                if (sceneChanged) {
                    prevSceneMode = sceneNow
                    lastWallKeyframePushMs = nowMs
                    requestSyncFrame()
                } else if (effectiveFps < targetFps && nowMs - lastWallKeyframePushMs >= configuredKeyframeSec * 1000L) {
                    lastWallKeyframePushMs = nowMs
                    requestSyncFrame()
                }

                val curRev = SolSceneAssetStore.currentRevision()
                val assetDirty = (curRev != seenAssetRevision)
                if (assetDirty) {
                    seenAssetRevision = curRev
                    loadedSceneSlotKey = ""
                    overlayLoadAttemptedRev = -1
                    posterLoadAttemptedRev = -1
                    if (overlayTexId != 0) {
                        GlShaderBank.deleteTexture2d(overlayTexId)
                        overlayTexId = 0
                    }
                    if (posterTexId != 0) {
                        GlShaderBank.deleteTexture2d(posterTexId)
                        posterTexId = 0
                    }
                    if (pngTuberIdleTexId != 0) {
                        GlShaderBank.deleteTexture2d(pngTuberIdleTexId)
                        pngTuberIdleTexId = 0
                    }
                    if (pngTuberTalkTexId != 0) {
                        GlShaderBank.deleteTexture2d(pngTuberTalkTexId)
                        pngTuberTalkTexId = 0
                    }
                    loadedPngTuberGender = ""
                }

                if (sceneNow != "LIVE") {
                    val wantKey = "$sceneNow:$isVerticalPrimary:${liveBannerText.trim()}"
                    if (sceneTexId == 0 || loadedSceneSlotKey != wantKey) {
                        if (sceneTexId != 0) GlShaderBank.deleteTexture2d(sceneTexId)
                        val slot = if (sceneNow == "STARTING_SOON") {
                            SolSceneAssetStore.AssetSlot.STARTING_SOON
                        } else {
                            SolSceneAssetStore.AssetSlot.BRB_SCENE
                        }
                        val bmp = SolSceneAssetStore.loadOrGenerateSceneBitmap(
                            context = context,
                            slot = slot,
                            isVertical = isVerticalPrimary,
                            customSubtitle = liveBannerText
                        )
                        sceneTexId = GlShaderBank.uploadBitmapTexture2d(bmp)
                        bmp.recycle()
                        loadedSceneSlotKey = wantKey
                    }
                }

                if (liveOverlayImageEnabled) {
                    if (overlayTexId == 0 && overlayLoadAttemptedRev != curRev) {
                        overlayLoadAttemptedRev = curRev
                        SolSceneAssetStore.loadSavedBitmapForGl(context, SolSceneAssetStore.AssetSlot.OVERLAY_PNG)?.let { bmp ->
                            overlayBitmapW = max(1, bmp.width)
                            overlayBitmapH = max(1, bmp.height)
                            overlayTexId = GlShaderBank.uploadBitmapTexture2d(bmp)
                            bmp.recycle()
                        }
                    }
                } else {
                    if (overlayTexId != 0) {
                        GlShaderBank.deleteTexture2d(overlayTexId)
                        overlayTexId = 0
                    }
                    overlayLoadAttemptedRev = -1
                }

                val needPoster = (isVerticalPrimary || companionEglSurface != EGL14.EGL_NO_SURFACE) &&
                    liveShortsBackdropMode == "POSTER"
                if (needPoster) {
                    if (posterTexId == 0 && posterLoadAttemptedRev != curRev) {
                        posterLoadAttemptedRev = curRev
                        SolSceneAssetStore.loadSavedBitmapForGl(context, SolSceneAssetStore.AssetSlot.SHORTS_POSTER)?.let { bmp ->
                            posterTexId = GlShaderBank.uploadBitmapTexture2d(bmp)
                            bmp.recycle()
                        }
                    }
                } else {
                    if (posterTexId != 0) {
                        GlShaderBank.deleteTexture2d(posterTexId)
                        posterTexId = 0
                    }
                    posterLoadAttemptedRev = -1
                }

                val wantBanner = liveBannerEnabled && liveBannerText.isNotBlank()
                val curBannerSig = if (wantBanner) "${liveBannerPosition}:${liveBannerText.trim()}" else ""
                if (wantBanner) {
                    if (curBannerSig != loadedBannerSig) {
                        if (bannerTexId != 0) GlShaderBank.deleteTexture2d(bannerTexId)
                        val bBmp = SolSceneAssetStore.buildLiveBannerBitmap(liveBannerText.trim())
                        bannerTexId = if (bBmp != null) {
                            val id = GlShaderBank.uploadBitmapTexture2d(bBmp)
                            bBmp.recycle()
                            id
                        } else 0
                        loadedBannerSig = curBannerSig
                    }
                } else if (bannerTexId != 0 || loadedBannerSig.isNotEmpty()) {
                    GlShaderBank.deleteTexture2d(bannerTexId)
                    bannerTexId = 0
                    loadedBannerSig = ""
                }

                // === Phase 1 (v1.5.0+) Zero-Allocation SolHUD™ Live GPU Telemetry Texture ===
                if (liveHudTelemetryEnabled) {
                    if (hudTexId == 0 || monoMs - lastHudUpdateMs >= HUD_REFRESH_INTERVAL_MS) {
                        lastHudUpdateMs = monoMs
                        val scratch = hudScratchBitmap ?: SolHudAndQrRenderer.createHudScratchBitmap().also {
                            hudScratchBitmap = it
                        }
                        val shownFps = if (actualEncodedFps > 0) actualEncodedFps else effectiveFps
                        val shownMbps = if (actualEncodedBitrateBps > 0) {
                            actualEncodedBitrateBps / 1_000_000f
                        } else {
                            currentBitrateBps / 1_000_000f
                        }
                        SolHudAndQrRenderer.renderHudIntoBitmap(
                            target = scratch,
                            fps = shownFps,
                            tempC = liveHudTempC,
                            bitrateMbps = shownMbps,
                            isThrottled30 = thermalThrottle30Fps
                        )
                        if (hudTexId == 0) {
                            hudTexId = GlShaderBank.uploadBitmapTexture2d(scratch)
                        } else {
                            GlShaderBank.updateBitmapTexture2d(hudTexId, scratch)
                        }
                    }
                } else {
                    if (hudTexId != 0) {
                        GlShaderBank.deleteTexture2d(hudTexId)
                        hudTexId = 0
                    }
                    hudScratchBitmap?.let { bmp ->
                        if (!bmp.isRecycled) bmp.recycle()
                        hudScratchBitmap = null
                    }
                    lastHudUpdateMs = 0L
                }

                // === Phase 1 (v1.5.0+) Corner QR Code Overlay (GL_NEAREST for razor-sharp camera scanning) ===
                val wantQr = liveQrOverlayEnabled && liveQrOverlayText.isNotBlank()
                val curQrSig = if (wantQr) liveQrOverlayText.trim() else ""
                if (wantQr) {
                    if (curQrSig != loadedQrSig) {
                        if (qrTexId != 0) GlShaderBank.deleteTexture2d(qrTexId)
                        val qrBmp = SolHudAndQrRenderer.generateQrOverlayBitmap(curQrSig)
                        qrTexId = if (qrBmp != null) {
                            val id = GlShaderBank.uploadBitmapTexture2d(qrBmp, nearestFilter = true)
                            qrBmp.recycle()
                            id
                        } else 0
                        loadedQrSig = curQrSig
                    }
                } else if (qrTexId != 0 || loadedQrSig.isNotEmpty()) {
                    GlShaderBank.deleteTexture2d(qrTexId)
                    qrTexId = 0
                    loadedQrSig = ""
                }

                // === Phase 1 (v1.5.0+) Zero-Camera 2-PNG PNGTuber Avatar (140ms hangover + smooth talk-hop) ===
                if (livePngTuberEnabled) {
                    val genderNow = liveVoiceGenderProfile
                    if (pngTuberIdleTexId == 0 || loadedPngTuberGender != genderNow) {
                        if (pngTuberIdleTexId != 0) {
                            GlShaderBank.deleteTexture2d(pngTuberIdleTexId)
                            pngTuberIdleTexId = 0
                        }
                        if (pngTuberTalkTexId != 0) {
                            GlShaderBank.deleteTexture2d(pngTuberTalkTexId)
                            pngTuberTalkTexId = 0
                        }
                        val (idleBmp, talkBmp) = SolSceneAssetStore.loadPngTuberPairForGl(context, genderNow)
                        pngTuberBmpW = max(1, idleBmp.width)
                        pngTuberBmpH = max(1, idleBmp.height)
                        pngTuberIdleTexId = GlShaderBank.uploadBitmapTexture2d(idleBmp)
                        idleBmp.recycle()
                        if (talkBmp != null) {
                            pngTuberTalkTexId = GlShaderBank.uploadBitmapTexture2d(talkBmp)
                            talkBmp.recycle()
                        }
                        loadedPngTuberGender = genderNow
                    }
                    if (liveVoiceSpeaking) {
                        lastVoiceActiveMs = monoMs
                    }
                    val talkingNow = liveVoiceSpeaking || (monoMs - lastVoiceActiveMs < PNGTUBER_TALK_HOLD_MS)
                    val targetBounce = if (talkingNow) 1.0f else 0.0f
                    pngTuberBounceFrac += (targetBounce - pngTuberBounceFrac) * 0.35f
                } else {
                    if (pngTuberIdleTexId != 0) {
                        GlShaderBank.deleteTexture2d(pngTuberIdleTexId)
                        pngTuberIdleTexId = 0
                    }
                    if (pngTuberTalkTexId != 0) {
                        GlShaderBank.deleteTexture2d(pngTuberTalkTexId)
                        pngTuberTalkTexId = 0
                    }
                    loadedPngTuberGender = ""
                    pngTuberBounceFrac = 0f
                }

                val activePngTuberTexId = if (!livePngTuberEnabled || pngTuberIdleTexId == 0) {
                    0
                } else if ((liveVoiceSpeaking || (monoMs - lastVoiceActiveMs < PNGTUBER_TALK_HOLD_MS)) && pngTuberTalkTexId != 0) {
                    pngTuberTalkTexId
                } else {
                    pngTuberIdleTexId
                }

                val ptsNs = max(1_000L, System.nanoTime() - originTimeNs)

                val primaryCurrentOk = EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                if (!primaryCurrentOk) {
                    consecutivePrimaryEglErrors++
                    if (consecutivePrimaryEglErrors >= 3) {
                        throw IllegalStateException("Primary eglMakeCurrent failed (err=0x${Integer.toHexString(EGL14.eglGetError())})")
                    }
                    continue
                }

                renderCompositePass(
                    oesHandles = oesHandles,
                    rgbaHandles = rgbaHandles,
                    oesTexId = oesTexId,
                    sceneNow = sceneNow,
                    sceneTexId = sceneTexId,
                    posterTexId = posterTexId,
                    overlayTexId = overlayTexId,
                    overlayBmpW = overlayBitmapW,
                    overlayBmpH = overlayBitmapH,
                    hudTexId = hudTexId,
                    qrTexId = qrTexId,
                    pngTuberTexId = activePngTuberTexId,
                    pngTuberBmpW = pngTuberBmpW,
                    pngTuberBmpH = pngTuberBmpH,
                    pngTuberBounceFrac = pngTuberBounceFrac,
                    bannerTexId = bannerTexId,
                    hasFirstFrame = hasFirstFrame,
                    isFreshGameFrame = isFreshGameFrame,
                    srcW = activeCapW,
                    srcH = activeCapH,
                    dstW = outWidth,
                    dstH = outHeight,
                    isHdr10Surface = actual10BitEgl,
                    fullQuadVerts = fullQuadVerts,
                    bannerTopVerts = bannerTopVerts,
                    bannerBottomVerts = bannerBottomVerts,
                    oesTexCoords = oesTexCoords,
                    bitmapTexCoords = bitmapTexCoords,
                    stMatrix = stMatrix,
                    mvpMatrix = mvpMatrix,
                    overlayMvpMatrix = overlayMvpMatrix,
                    identityMvp = identityMvp
                )
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
                val primarySwapOk = EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                if (!primarySwapOk) {
                    consecutivePrimaryEglErrors++
                    if (consecutivePrimaryEglErrors >= 3) {
                        throw IllegalStateException("Primary eglSwapBuffers failed (err=0x${Integer.toHexString(EGL14.eglGetError())})")
                    }
                } else {
                    consecutivePrimaryEglErrors = 0
                }

                // Async 1-Tap Clean Screenshot (runs AFTER eglSwapBuffers via temporary offscreen RGBA8 FBO)
                val shotCallback = pendingScreenshotCallback.getAndSet(null)
                if (shotCallback != null) {
                    captureCleanFboScreenshot(
                        oesHandles = oesHandles,
                        rgbaHandles = rgbaHandles,
                        oesTexId = oesTexId,
                        sceneNow = sceneNow,
                        sceneTexId = sceneTexId,
                        posterTexId = posterTexId,
                        overlayTexId = overlayTexId,
                        overlayBmpW = overlayBitmapW,
                        overlayBmpH = overlayBitmapH,
                        hudTexId = hudTexId,
                        qrTexId = qrTexId,
                        pngTuberTexId = activePngTuberTexId,
                        pngTuberBmpW = pngTuberBmpW,
                        pngTuberBmpH = pngTuberBmpH,
                        pngTuberBounceFrac = pngTuberBounceFrac,
                        bannerTexId = bannerTexId,
                        hasFirstFrame = hasFirstFrame,
                        srcW = activeCapW,
                        srcH = activeCapH,
                        primaryW = outWidth,
                        primaryH = outHeight,
                        fullQuadVerts = fullQuadVerts,
                        bannerTopVerts = bannerTopVerts,
                        bannerBottomVerts = bannerBottomVerts,
                        oesTexCoords = oesTexCoords,
                        bitmapTexCoords = bitmapTexCoords,
                        stMatrix = stMatrix,
                        mvpMatrix = mvpMatrix,
                        overlayMvpMatrix = overlayMvpMatrix,
                        identityMvp = identityMvp,
                        onComplete = shotCallback
                    )
                }

                if (companionEglSurface != EGL14.EGL_NO_SURFACE && !companionPausedByThermal) {
                    val compCurrentOk = EGL14.eglMakeCurrent(eglDisplay, companionEglSurface, companionEglSurface, eglContext)
                    if (!compCurrentOk) {
                        DiagnosticsLogger.log("DUAL_ERR", "Companion EGL makeCurrent failed; stopping secondary companion target.")
                        try { EGL14.eglDestroySurface(eglDisplay, companionEglSurface) } catch (_: Exception) {}
                        companionEglSurface = EGL14.EGL_NO_SURFACE
                        onCompanionPipelineStopped?.invoke("Companion EGL makeCurrent failed")
                    } else {
                        renderCompositePass(
                            oesHandles = oesHandles,
                            rgbaHandles = rgbaHandles,
                            oesTexId = oesTexId,
                            sceneNow = sceneNow,
                            sceneTexId = sceneTexId,
                            posterTexId = posterTexId,
                            overlayTexId = overlayTexId,
                            overlayBmpW = overlayBitmapW,
                            overlayBmpH = overlayBitmapH,
                            hudTexId = hudTexId,
                            qrTexId = qrTexId,
                            pngTuberTexId = activePngTuberTexId,
                            pngTuberBmpW = pngTuberBmpW,
                            pngTuberBmpH = pngTuberBmpH,
                            pngTuberBounceFrac = pngTuberBounceFrac,
                            bannerTexId = bannerTexId,
                            hasFirstFrame = hasFirstFrame,
                            isFreshGameFrame = isFreshGameFrame,
                            srcW = activeCapW,
                            srcH = activeCapH,
                            dstW = companionWidth,
                            dstH = companionHeight,
                            isHdr10Surface = false,
                            fullQuadVerts = fullQuadVerts,
                            bannerTopVerts = bannerTopVerts,
                            bannerBottomVerts = bannerBottomVerts,
                            oesTexCoords = oesTexCoords,
                            bitmapTexCoords = bitmapTexCoords,
                            stMatrix = stMatrix,
                            mvpMatrix = mvpMatrix,
                            overlayMvpMatrix = overlayMvpMatrix,
                            identityMvp = identityMvp
                        )
                        EGLExt.eglPresentationTimeANDROID(eglDisplay, companionEglSurface, ptsNs)
                        val compSwapOk = EGL14.eglSwapBuffers(eglDisplay, companionEglSurface)
                        if (!compSwapOk) {
                            DiagnosticsLogger.log("DUAL_ERR", "Companion EGL swapBuffers failed; stopping secondary companion target.")
                            try { EGL14.eglDestroySurface(eglDisplay, companionEglSurface) } catch (_: Exception) {}
                            companionEglSurface = EGL14.EGL_NO_SURFACE
                            onCompanionPipelineStopped?.invoke("Companion EGL swapBuffers failed")
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && adpfSession != null) {
                    try {
                        adpfSession.reportActualWorkDuration(System.nanoTime() - workStartNs)
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (e: Exception) {
            if (!initNotified) {
                initNotified = true
                onEglInitialized(
                    EglInitResult(
                        primaryReady = false,
                        companionReady = false,
                        actualGlMajor = 2,
                        actualIs10Bit = false,
                        engineSummary = "EGL Error"
                    )
                )
            }
            if (isRunning) {
                val msg = "Video OpenGL render loop interrupted (${e.javaClass.simpleName}: ${e.message ?: "unknown"})."
                DiagnosticsLogger.log("GL_ERR", msg, e.javaClass.name)
                onFatalPipelineError?.invoke(msg)
            }
        } finally {
            renderThread = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { adpfSession?.close() } catch (_: Throwable) {}
            }
            releaseVirtualDisplaySafely()
            try { captureSurface?.release() } catch (_: Exception) {}
            try { surfaceTexture?.release() } catch (_: Exception) {}

            GlShaderBank.deleteTexture2d(sceneTexId)
            GlShaderBank.deleteTexture2d(overlayTexId)
            GlShaderBank.deleteTexture2d(posterTexId)
            GlShaderBank.deleteTexture2d(bannerTexId)
            GlShaderBank.deleteTexture2d(hudTexId)
            GlShaderBank.deleteTexture2d(qrTexId)
            GlShaderBank.deleteTexture2d(pngTuberIdleTexId)
            GlShaderBank.deleteTexture2d(pngTuberTalkTexId)
            hudScratchBitmap?.let { bmp ->
                try { if (!bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
            }
            if (oesTexId != 0) {
                try { GLES20.glDeleteTextures(1, intArrayOf(oesTexId), 0) } catch (_: Exception) {}
            }
            if (oesProg != 0) {
                try { GLES20.glDeleteProgram(oesProg) } catch (_: Exception) {}
            }
            if (rgbaProg != 0) {
                try { GLES20.glDeleteProgram(rgbaProg) } catch (_: Exception) {}
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (companionEglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, companionEglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            try { encoderInputSurface.release() } catch (_: Exception) {}
            try { companionInputSurface?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Renders a clean, pill-free RGBA8 frame into a temporary offscreen FBO (clamped <=1080p,
     * max 7.91 MiB), reads back pixels, destroys the FBO immediately (0 MB retained VRAM),
     * and dispatches vertical-flip + JPEG save to a background thread.
     */
    private fun captureCleanFboScreenshot(
        oesHandles: GlShaderBank.OesHandles,
        rgbaHandles: GlShaderBank.Rgba2dHandles,
        oesTexId: Int,
        sceneNow: String,
        sceneTexId: Int,
        posterTexId: Int,
        overlayTexId: Int,
        overlayBmpW: Int,
        overlayBmpH: Int,
        hudTexId: Int,
        qrTexId: Int,
        pngTuberTexId: Int,
        pngTuberBmpW: Int,
        pngTuberBmpH: Int,
        pngTuberBounceFrac: Float,
        bannerTexId: Int,
        hasFirstFrame: Boolean,
        srcW: Int,
        srcH: Int,
        primaryW: Int,
        primaryH: Int,
        fullQuadVerts: FloatBuffer,
        bannerTopVerts: FloatBuffer,
        bannerBottomVerts: FloatBuffer,
        oesTexCoords: FloatBuffer,
        bitmapTexCoords: FloatBuffer,
        stMatrix: FloatArray,
        mvpMatrix: FloatArray,
        overlayMvpMatrix: FloatArray,
        identityMvp: FloatArray,
        onComplete: (Result<String>) -> Unit
    ) {
        val (shotW, shotH) = computeScreenshotDimensions(primaryW, primaryH)
        val fboIds = IntArray(1)
        val texIds = IntArray(1)
        var rgbaBytes: ByteArray? = null
        var captureError: Throwable? = null

        try {
            GLES20.glGenFramebuffers(1, fboIds, 0)
            GLES20.glGenTextures(1, texIds, 0)
            val fboId = fboIds[0]
            val texId = texIds[0]
            if (fboId == 0 || texId == 0) {
                throw IllegalStateException("Could not allocate screenshot FBO.")
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                shotW,
                shotH,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null
            )

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                texId,
                0
            )
            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw IllegalStateException("Screenshot FBO incomplete (status=0x${Integer.toHexString(status)})")
            }

            renderCompositePass(
                oesHandles = oesHandles,
                rgbaHandles = rgbaHandles,
                oesTexId = oesTexId,
                sceneNow = sceneNow,
                sceneTexId = sceneTexId,
                posterTexId = posterTexId,
                overlayTexId = overlayTexId,
                overlayBmpW = overlayBmpW,
                overlayBmpH = overlayBmpH,
                hudTexId = hudTexId,
                qrTexId = qrTexId,
                pngTuberTexId = pngTuberTexId,
                pngTuberBmpW = pngTuberBmpW,
                pngTuberBmpH = pngTuberBmpH,
                pngTuberBounceFrac = pngTuberBounceFrac,
                bannerTexId = bannerTexId,
                hasFirstFrame = hasFirstFrame,
                isFreshGameFrame = true,
                srcW = srcW,
                srcH = srcH,
                dstW = shotW,
                dstH = shotH,
                isHdr10Surface = false,
                fullQuadVerts = fullQuadVerts,
                bannerTopVerts = bannerTopVerts,
                bannerBottomVerts = bannerBottomVerts,
                oesTexCoords = oesTexCoords,
                bitmapTexCoords = bitmapTexCoords,
                stMatrix = stMatrix,
                mvpMatrix = mvpMatrix,
                overlayMvpMatrix = overlayMvpMatrix,
                identityMvp = identityMvp
            )

            val readBuf = ByteBuffer.allocateDirect(shotW * shotH * 4).order(ByteOrder.nativeOrder())
            GLES20.glPixelStorei(GLES20.GL_PACK_ALIGNMENT, 4)
            GLES20.glReadPixels(0, 0, shotW, shotH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, readBuf)
            readBuf.rewind()
            val bytes = ByteArray(shotW * shotH * 4)
            readBuf.get(bytes)
            rgbaBytes = bytes
        } catch (t: Throwable) {
            captureError = t
        } finally {
            try { GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) } catch (_: Exception) {}
            try { GLES20.glViewport(0, 0, primaryW, primaryH) } catch (_: Exception) {}
            if (texIds[0] != 0) {
                try { GLES20.glDeleteTextures(1, texIds, 0) } catch (_: Exception) {}
            }
            if (fboIds[0] != 0) {
                try { GLES20.glDeleteFramebuffers(1, fboIds, 0) } catch (_: Exception) {}
            }
        }

        val capturedBytes = rgbaBytes
        if (capturedBytes == null || captureError != null) {
            screenshotInFlight.set(false)
            val err = captureError ?: IllegalStateException("Screenshot capture failed.")
            DiagnosticsLogger.log("SHOT_ERR", "Clean screenshot failed: ${err.message}")
            try { onComplete(Result.failure(err)) } catch (_: Exception) {}
            return
        }

        val appCtx = context.applicationContext
        Thread({
            val res = try {
                SolHudAndQrRenderer.saveRgbaScreenshotToMediaStore(appCtx, capturedBytes, shotW, shotH)
            } catch (t: Throwable) {
                Result.failure(t)
            } finally {
                screenshotInFlight.set(false)
            }
            res.onSuccess { fileName ->
                DiagnosticsLogger.log("SCREENSHOT", "Saved clean YouTube thumbnail to Pictures/SolStream/$fileName (${shotW}x${shotH}).")
            }.onFailure { err ->
                DiagnosticsLogger.log("SHOT_ERR", "Could not save clean screenshot: ${err.message}")
            }
            try { onComplete(res) } catch (_: Exception) {}
        }, "SolScreenshotSave").start()
    }

    private fun renderCompositePass(
        oesHandles: GlShaderBank.OesHandles,
        rgbaHandles: GlShaderBank.Rgba2dHandles,
        oesTexId: Int,
        sceneNow: String,
        sceneTexId: Int,
        posterTexId: Int,
        overlayTexId: Int,
        overlayBmpW: Int,
        overlayBmpH: Int,
        hudTexId: Int,
        qrTexId: Int,
        pngTuberTexId: Int,
        pngTuberBmpW: Int,
        pngTuberBmpH: Int,
        pngTuberBounceFrac: Float,
        bannerTexId: Int,
        hasFirstFrame: Boolean,
        isFreshGameFrame: Boolean,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        isHdr10Surface: Boolean,
        fullQuadVerts: FloatBuffer,
        bannerTopVerts: FloatBuffer,
        bannerBottomVerts: FloatBuffer,
        oesTexCoords: FloatBuffer,
        bitmapTexCoords: FloatBuffer,
        stMatrix: FloatArray,
        mvpMatrix: FloatArray,
        overlayMvpMatrix: FloatArray,
        identityMvp: FloatArray
    ) {
        GLES20.glViewport(0, 0, dstW, dstH)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0.02f, 0.03f, 0.05f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (sceneNow != "LIVE") {
            if (sceneTexId != 0) {
                drawRgbaQuad(rgbaHandles, sceneTexId, identityMvp, fullQuadVerts, bitmapTexCoords, enableBlend = false)
            }
            return
        }

        if (!hasFirstFrame) return

        val dstIsVertical = dstH > dstW
        val srcIsLandscape = srcW > srcH
        val framing = liveFramingMode
        val zoomPct = liveSmartZoomPercent

        if (dstIsVertical && srcIsLandscape && framing != "FILL") {
            val needBlurBackdrop = liveShortsBackdropMode == "BLUR" ||
                (liveShortsBackdropMode == "POSTER" && posterTexId == 0)

            if (liveShortsBackdropMode == "POSTER" && posterTexId != 0) {
                drawRgbaQuad(rgbaHandles, posterTexId, identityMvp, fullQuadVerts, bitmapTexCoords, enableBlend = false)
            } else if (needBlurBackdrop) {
                val srcAspect = srcW.toFloat() / max(1, srcH).toFloat()
                val dstAspect = dstW.toFloat() / max(1, dstH).toFloat()
                val activeZoom = if (framing == "SMART") (zoomPct.coerceIn(100, 250) / 100.0f) else 1.0f
                val sharpScaleY = ((dstAspect / srcAspect) * activeZoom).coerceIn(0.10f, 1.50f)

                // Skip blur completely when zoomed gameplay already fills 100% of vertical height
                if (sharpScaleY < 0.998f) {
                    computeFramingMatrix(mvpMatrix, srcW, srcH, dstW, dstH, "FILL", 100, liveLandscapeFlip)
                    val barHeightPx = (((1.0f - sharpScaleY) * 0.5f * dstH).roundToInt() + 4).coerceIn(4, dstH / 2)
                    GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
                    GLES20.glScissor(0, 0, dstW, barHeightPx)
                    drawOesGameplayQuad(
                        h = oesHandles,
                        oesTexId = oesTexId,
                        mvp = mvpMatrix,
                        texMatrix = stMatrix,
                        verts = fullQuadVerts,
                        texCoords = oesTexCoords,
                        srcW = srcW,
                        srcH = srcH,
                        isBlurPass = true,
                        isFreshFrame = isFreshGameFrame,
                        isHdr10 = isHdr10Surface
                    )
                    GLES20.glScissor(0, dstH - barHeightPx, dstW, barHeightPx)
                    drawOesGameplayQuad(
                        h = oesHandles,
                        oesTexId = oesTexId,
                        mvp = mvpMatrix,
                        texMatrix = stMatrix,
                        verts = fullQuadVerts,
                        texCoords = oesTexCoords,
                        srcW = srcW,
                        srcH = srcH,
                        isBlurPass = true,
                        isFreshFrame = isFreshGameFrame,
                        isHdr10 = isHdr10Surface
                    )
                    GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
                }
            }
        }

        // 1. Game Frame + Color Filters
        computeFramingMatrix(
            mvp = mvpMatrix,
            srcW = srcW,
            srcH = srcH,
            dstW = dstW,
            dstH = dstH,
            mode = framing,
            smartZoomPct = zoomPct,
            flipDeg = liveLandscapeFlip
        )
        drawOesGameplayQuad(
            h = oesHandles,
            oesTexId = oesTexId,
            mvp = mvpMatrix,
            texMatrix = stMatrix,
            verts = fullQuadVerts,
            texCoords = oesTexCoords,
            srcW = srcW,
            srcH = srcH,
            isBlurPass = false,
            isFreshFrame = isFreshGameFrame,
            isHdr10 = isHdr10Surface
        )

        // 2. Privacy Blur (Minimap Shield + Independent Keyboard Blur)
        if (liveMinimapShieldEnabled) {
            val (scX, scY, boxW, boxH) = resolvePrivacyBlurRect(dstW, dstH, liveMinimapShieldCorner)
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(scX, scY, boxW, boxH)
            drawOesGameplayQuad(
                h = oesHandles,
                oesTexId = oesTexId,
                mvp = mvpMatrix,
                texMatrix = stMatrix,
                verts = fullQuadVerts,
                texCoords = oesTexCoords,
                srcW = srcW,
                srcH = srcH,
                isBlurPass = true,
                isFreshFrame = isFreshGameFrame,
                isHdr10 = isHdr10Surface
            )
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        }

        if (keyboardBlurActive && !(liveMinimapShieldEnabled && liveMinimapShieldCorner == "KEYBOARD")) {
            val kbH = (dstH * 0.54f).roundToInt().coerceIn(64, dstH)
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(0, 0, dstW, kbH)
            drawOesGameplayQuad(
                h = oesHandles,
                oesTexId = oesTexId,
                mvp = mvpMatrix,
                texMatrix = stMatrix,
                verts = fullQuadVerts,
                texCoords = oesTexCoords,
                srcW = srcW,
                srcH = srcH,
                isBlurPass = true,
                isFreshFrame = isFreshGameFrame,
                isHdr10 = isHdr10Surface
            )
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        }

        // 3. .PNG Stream Frame / Logo Overlay
        if (overlayTexId != 0) {
            val posMode = liveOverlayPosition
            if (posMode == "FULL") {
                drawRgbaQuad(rgbaHandles, overlayTexId, identityMvp, fullQuadVerts, bitmapTexCoords, enableBlend = true)
            } else {
                SolHudAndQrRenderer.computeCornerQuadMvp(
                    mvp = overlayMvpMatrix,
                    bmpW = overlayBmpW,
                    bmpH = overlayBmpH,
                    dstW = dstW,
                    dstH = dstH,
                    corner = posMode,
                    maxHeightFrac = 0.18f,
                    maxWidthFrac = 0.22f
                )
                drawRgbaQuad(rgbaHandles, overlayTexId, overlayMvpMatrix, fullQuadVerts, bitmapTexCoords, enableBlend = true)
            }
        }

        // 4. Phase 1 (v1.5.0+) Pro Broadcast Overlays (drawn ABOVE Privacy Blur so HUD/QR/Avatar stay crisp)
        if (hudTexId != 0) {
            val maxH = if (dstIsVertical) 0.042f else 0.062f
            val maxW = if (dstIsVertical) 0.52f else 0.30f
            SolHudAndQrRenderer.computeCornerQuadMvp(
                mvp = overlayMvpMatrix,
                bmpW = SolHudAndQrRenderer.HUD_WIDTH_PX,
                bmpH = SolHudAndQrRenderer.HUD_HEIGHT_PX,
                dstW = dstW,
                dstH = dstH,
                corner = liveHudTelemetryCorner,
                maxHeightFrac = maxH,
                maxWidthFrac = maxW
            )
            drawRgbaQuad(rgbaHandles, hudTexId, overlayMvpMatrix, fullQuadVerts, bitmapTexCoords, enableBlend = true)
        }

        if (qrTexId != 0) {
            val qrMaxH = if (dstIsVertical) 0.13f else 0.18f
            val qrMaxW = if (dstIsVertical) 0.24f else 0.14f
            SolHudAndQrRenderer.computeCornerQuadMvp(
                mvp = overlayMvpMatrix,
                bmpW = SolHudAndQrRenderer.QR_SIZE_PX,
                bmpH = SolHudAndQrRenderer.QR_SIZE_PX,
                dstW = dstW,
                dstH = dstH,
                corner = liveQrOverlayCorner,
                maxHeightFrac = qrMaxH,
                maxWidthFrac = qrMaxW
            )
            drawRgbaQuad(rgbaHandles, qrTexId, overlayMvpMatrix, fullQuadVerts, bitmapTexCoords, enableBlend = true)
        }

        if (pngTuberTexId != 0) {
            val sizeFrac = (livePngTuberSizePercent.coerceIn(12, 30) / 100.0f) * (if (dstIsVertical) 0.78f else 1.0f)
            val hopNdcY = pngTuberBounceFrac * 0.032f
            SolHudAndQrRenderer.computeCornerQuadMvp(
                mvp = overlayMvpMatrix,
                bmpW = pngTuberBmpW,
                bmpH = pngTuberBmpH,
                dstW = dstW,
                dstH = dstH,
                corner = livePngTuberCorner,
                maxHeightFrac = sizeFrac,
                maxWidthFrac = if (dstIsVertical) 0.34f else 0.24f,
                extraOffsetY = hopNdcY
            )
            drawRgbaQuad(rgbaHandles, pngTuberTexId, overlayMvpMatrix, fullQuadVerts, bitmapTexCoords, enableBlend = true)
        }

        // 5. Live Text Banner (topmost layer)
        if (bannerTexId != 0) {
            val bannerVerts = if (liveBannerPosition == "BOTTOM") bannerBottomVerts else bannerTopVerts
            drawRgbaQuad(rgbaHandles, bannerTexId, identityMvp, bannerVerts, bitmapTexCoords, enableBlend = true)
        }
    }

    private fun drawOesGameplayQuad(
        h: GlShaderBank.OesHandles,
        oesTexId: Int,
        mvp: FloatArray,
        texMatrix: FloatArray,
        verts: FloatBuffer,
        texCoords: FloatBuffer,
        srcW: Int,
        srcH: Int,
        isBlurPass: Boolean,
        isFreshFrame: Boolean,
        isHdr10: Boolean
    ) {
        GLES20.glUseProgram(h.programId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glUniform1i(h.uTexSamplerLoc, 0)

        GLES20.glUniformMatrix4fv(h.uMvpLoc, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(h.uTexMatLoc, 1, false, texMatrix, 0)
        GLES20.glUniform2f(h.uTexelStepLoc, 1.0f / max(1, srcW).toFloat(), 1.0f / max(1, srcH).toFloat())

        val rawUpscalerVal = when (liveUpscalerMode) {
            "BILINEAR" -> 0.0f
            "LANCZOS" -> 2.0f
            else -> 1.0f
        }
        val effectiveUpscalerVal = if (isBlurPass || (thermalModerateCoolerActive && !isFreshFrame)) {
            0.0f
        } else {
            rawUpscalerVal
        }

        val tempShift = when (liveColorTempFilter) {
            "WARM" -> 1.0f
            "COOL" -> -1.0f
            else -> 0.0f
        }

        val shadowVal = if (!isBlurPass && !isHdr10 && liveShadowBoost) 1.0f else 0.0f
        val filmicVal = if (!isBlurPass && !isHdr10 && liveCinematicFilmic) 1.0f else 0.0f
        val vividVal = if (!isBlurPass && !isHdr10 && liveAmoledVivid) 1.0f else 0.0f
        val tempVal = if (!isBlurPass && !isHdr10) tempShift else 0.0f
        val sharpVal = if (!isBlurPass && liveGpuSharpen) 0.34f else 0.0f

        val fastPassthrough = !isBlurPass &&
            effectiveUpscalerVal < 0.5f &&
            shadowVal < 0.5f &&
            filmicVal < 0.5f &&
            vividVal < 0.5f &&
            abs(tempVal) < 0.1f &&
            sharpVal < 0.01f

        GLES20.glUniform1f(h.uFastPassthroughLoc, if (fastPassthrough) 1.0f else 0.0f)
        GLES20.glUniform1f(h.uUpscalerLoc, effectiveUpscalerVal)
        GLES20.glUniform1f(h.uBlurModeLoc, if (isBlurPass) 1.0f else 0.0f)
        GLES20.glUniform1f(h.uShadowBoostLoc, shadowVal)
        GLES20.glUniform1f(h.uFilmicLoc, filmicVal)
        GLES20.glUniform1f(h.uVividLoc, vividVal)
        GLES20.glUniform1f(h.uTempShiftLoc, tempVal)
        GLES20.glUniform1f(h.uSharpenLoc, sharpVal)

        verts.position(0)
        GLES20.glEnableVertexAttribArray(h.aPosLoc)
        GLES20.glVertexAttribPointer(h.aPosLoc, 2, GLES20.GL_FLOAT, false, 0, verts)

        texCoords.position(0)
        GLES20.glEnableVertexAttribArray(h.aTexLoc)
        GLES20.glVertexAttribPointer(h.aTexLoc, 2, GLES20.GL_FLOAT, false, 0, texCoords)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(h.aPosLoc)
        GLES20.glDisableVertexAttribArray(h.aTexLoc)
    }

    private fun drawRgbaQuad(
        h: GlShaderBank.Rgba2dHandles,
        texId: Int,
        mvp: FloatArray,
        verts: FloatBuffer,
        texCoords: FloatBuffer,
        enableBlend: Boolean
    ) {
        if (enableBlend) {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        GLES20.glUseProgram(h.programId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(h.uSamplerLoc, 0)
        GLES20.glUniformMatrix4fv(h.uMvpLoc, 1, false, mvp, 0)

        verts.position(0)
        GLES20.glEnableVertexAttribArray(h.aPosLoc)
        GLES20.glVertexAttribPointer(h.aPosLoc, 2, GLES20.GL_FLOAT, false, 0, verts)

        texCoords.position(0)
        GLES20.glEnableVertexAttribArray(h.aTexLoc)
        GLES20.glVertexAttribPointer(h.aTexLoc, 2, GLES20.GL_FLOAT, false, 0, texCoords)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(h.aPosLoc)
        GLES20.glDisableVertexAttribArray(h.aTexLoc)
        if (enableBlend) {
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    private fun computeFramingMatrix(
        mvp: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        mode: String,
        smartZoomPct: Int,
        flipDeg: Int
    ) {
        val srcAspect = max(1, srcW).toFloat() / max(1, srcH).toFloat()
        val dstAspect = max(1, dstW).toFloat() / max(1, dstH).toFloat()
        val sameOrientation = (srcW >= srcH) == (dstW >= dstH)

        var scaleX = 1.0f
        var scaleY = 1.0f

        when {
            mode == "STRETCH" && sameOrientation -> {
                scaleX = 1.0f
                scaleY = 1.0f
            }
            mode == "FILL" -> {
                if (srcAspect > dstAspect) {
                    scaleX = srcAspect / dstAspect
                } else {
                    scaleY = dstAspect / srcAspect
                }
            }
            else -> {
                if (srcAspect > dstAspect) {
                    scaleY = dstAspect / srcAspect
                } else {
                    scaleX = srcAspect / dstAspect
                }
                if (mode == "SMART") {
                    val zoom = (smartZoomPct.coerceIn(100, 250)) / 100.0f
                    scaleX *= zoom
                    scaleY *= zoom
                }
            }
        }

        Matrix.setIdentityM(mvp, 0)
        if (flipDeg == 180) {
            Matrix.rotateM(mvp, 0, 180f, 0f, 0f, 1f)
        }
        Matrix.scaleM(mvp, 0, scaleX, scaleY, 1.0f)
    }

    private fun runEncoderDrainLoop(
        codec: MediaCodec,
        codecKey: String,
        originTimeUs: Long,
        isPrimaryStream: Boolean,
        onVideoConfig: (sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) -> Unit,
        onEncodedVideo: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        val packetPool = Array(6) { ByteBuffer.allocateDirect(786_432) }
        val infoPool = Array(6) { MediaCodec.BufferInfo() }
        var poolIdx = 0

        var ptsBaseLocked = false
        var ptsBaseUs = 0L
        var lastSentPtsUs = 0L

        var lastLoggedFormatSig = ""
        var lastSentConfigSig: ByteArray? = null

        var statsWindowStartMs = System.currentTimeMillis()
        var framesInWindow = 0
        var bytesInWindow = 0L
        var qpSumInWindow = 0L
        var qpCountInWindow = 0
        var consecutiveTransientErrors = 0

        try {
            while (isRunning) {
                val outIdx = try {
                    codec.dequeueOutputBuffer(bufferInfo, 15_000L)
                } catch (ce: MediaCodec.CodecException) {
                    if (shouldRetryTransientCodecException(ce.isTransient, isRunning, consecutiveTransientErrors)) {
                        consecutiveTransientErrors++
                        DiagnosticsLogger.log(
                            "ENC_TRANSIENT",
                            "Hardware video encoder reported transient stall #$consecutiveTransientErrors/$MAX_TRANSIENT_CODEC_RETRIES; retrying safely.",
                            ce.diagnosticInfo
                        )
                        try { Thread.sleep(25L) } catch (_: InterruptedException) { break }
                        continue
                    }
                    if (isRunning) {
                        if (isPrimaryStream) {
                            val msg = "Hardware video encoder fatal error (${ce.diagnosticInfo})."
                            DiagnosticsLogger.log("ENC_ERR", msg, ce.message ?: "")
                            onFatalPipelineError?.invoke(msg)
                        } else {
                            onCompanionPipelineStopped?.invoke("Companion encoder fatal error")
                        }
                    }
                    break
                }

                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    consecutiveTransientErrors = 0
                    val outFmt = codec.outputFormat
                    if (isPrimaryStream &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        outFmt.containsKey(MediaFormat.KEY_VIDEO_QP_AVERAGE)
                    ) {
                        val qp = outFmt.getInteger(MediaFormat.KEY_VIDEO_QP_AVERAGE)
                        if (qp in 0..63) {
                            qpSumInWindow += qp
                            qpCountInWindow++
                        }
                    }

                    val fmtSig = buildFormatSignature(outFmt)
                    if (isPrimaryStream && fmtSig != lastLoggedFormatSig) {
                        lastLoggedFormatSig = fmtSig
                        logActualEncoderOutputFormat(outFmt, codecKey)
                    }

                    val combinedCsd = combineCsdBuffers(outFmt)
                    if (combinedCsd != null && (lastSentConfigSig == null || !combinedCsd.contentEquals(lastSentConfigSig))) {
                        lastSentConfigSig = combinedCsd
                        extractAndSendCodecHeaders(outFmt, codecKey, combinedCsd, onVideoConfig)
                    }
                    continue
                }
                if (outIdx < 0) continue

                consecutiveTransientErrors = 0

                if (isPrimaryStream && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        val frameFmt = codec.getOutputFormat(outIdx)
                        if (frameFmt.containsKey(MediaFormat.KEY_VIDEO_QP_AVERAGE)) {
                            val qp = frameFmt.getInteger(MediaFormat.KEY_VIDEO_QP_AVERAGE)
                            if (qp in 0..63) {
                                qpSumInWindow += qp
                                qpCountInWindow++
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                val outBuf = codec.getOutputBuffer(outIdx)
                val isConfigFlag = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0

                if (outBuf != null && bufferInfo.size > 0) {
                    if (isConfigFlag) {
                        val dup = outBuf.duplicate()
                        dup.position(bufferInfo.offset)
                        dup.limit(infoEnd(bufferInfo))
                        val cfgBytes = ByteArray(dup.remaining())
                        dup.get(cfgBytes)
                        if (lastSentConfigSig == null || !cfgBytes.contentEquals(lastSentConfigSig)) {
                            lastSentConfigSig = cfgBytes
                            extractHeadersFromConfigBuffer(outBuf, bufferInfo, codecKey, onVideoConfig)
                        }
                    } else {
                        val slot = poolIdx
                        poolIdx = (poolIdx + 1) % packetPool.size

                        var targetBuf = packetPool[slot]
                        if (bufferInfo.size > targetBuf.capacity()) {
                            targetBuf = ByteBuffer.allocateDirect(bufferInfo.size + 65536)
                            packetPool[slot] = targetBuf
                        }

                        targetBuf.clear()
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(infoEnd(bufferInfo))
                        targetBuf.put(outBuf)
                        targetBuf.flip()

                        if (!ptsBaseLocked) {
                            ptsBaseUs = if (originTimeUs > 2_000_000L &&
                                bufferInfo.presentationTimeUs >= originTimeUs - 500_000L
                            ) {
                                min(originTimeUs, bufferInfo.presentationTimeUs)
                            } else {
                                0L
                            }
                            ptsBaseLocked = true
                        }
                        var adjPtsUs = max(0L, bufferInfo.presentationTimeUs - ptsBaseUs)
                        if (adjPtsUs < lastSentPtsUs) {
                            adjPtsUs = lastSentPtsUs + 1_000L
                        }
                        lastSentPtsUs = adjPtsUs
                        if (isPrimaryStream) {
                            lastSentVideoPtsUs = adjPtsUs
                        }

                        val outInfo = infoPool[slot].apply {
                            set(0, bufferInfo.size, adjPtsUs, bufferInfo.flags)
                        }
                        onEncodedVideo(targetBuf, outInfo)

                        framesInWindow++
                        bytesInWindow += bufferInfo.size
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)

                if (isPrimaryStream) {
                    val nowMs = System.currentTimeMillis()
                    val elapsedMs = nowMs - statsWindowStartMs
                    if (elapsedMs >= 1000L) {
                        actualEncodedFps = ((framesInWindow * 1000L) / elapsedMs).toInt()
                        actualEncodedBitrateBps = ((bytesInWindow * 8000L) / elapsedMs).toInt()
                        DiagnosticsLogger.lastEncodedFps = actualEncodedFps
                        DiagnosticsLogger.recordBitrateSample(actualEncodedBitrateBps / 1000)
                        if (qpCountInWindow > 0) {
                            DiagnosticsLogger.lastAverageQp = (qpSumInWindow / qpCountInWindow).toInt()
                        }
                        statsWindowStartMs = nowMs
                        framesInWindow = 0
                        bytesInWindow = 0L
                        qpSumInWindow = 0L
                        qpCountInWindow = 0
                    }
                }
            }
        } catch (ce: MediaCodec.CodecException) {
            if (isRunning) {
                if (isPrimaryStream) {
                    val msg = "Hardware video encoder fatal error (${ce.diagnosticInfo})."
                    DiagnosticsLogger.log("ENC_ERR", msg, ce.message ?: "")
                    onFatalPipelineError?.invoke(msg)
                } else {
                    onCompanionPipelineStopped?.invoke("Companion encoder fatal error")
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                if (isPrimaryStream) {
                    val msg = "Hardware video encoder stopped unexpectedly (${e.javaClass.simpleName})."
                    DiagnosticsLogger.log("ENC_ERR", msg, e.message ?: "")
                    onFatalPipelineError?.invoke(msg)
                } else {
                    onCompanionPipelineStopped?.invoke("Companion encoder stopped")
                }
            }
        } finally {
            if (isPrimaryStream) {
                drainThread = null
                releasePrimaryCodecSafely()
            } else {
                companionDrainThread = null
                releaseCompanionCodecSafely()
            }
        }
    }

    private fun infoEnd(info: MediaCodec.BufferInfo): Int = info.offset + info.size

    private fun buildFormatSignature(format: MediaFormat): String {
        return try {
            val w = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else -1
            val h = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else -1
            val std = if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) format.getInteger(MediaFormat.KEY_COLOR_STANDARD) else -1
            val rng = if (format.containsKey(MediaFormat.KEY_COLOR_RANGE)) format.getInteger(MediaFormat.KEY_COLOR_RANGE) else -1
            val trf = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else -1
            val prof = if (format.containsKey(MediaFormat.KEY_PROFILE)) format.getInteger(MediaFormat.KEY_PROFILE) else -1
            "$w:$h:$prof:$std:$rng:$trf"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun combineCsdBuffers(format: MediaFormat): ByteArray? {
        return try {
            val out = ByteArrayOutputStream()
            val keys = arrayOf("csd-0", "csd-1", "csd-2")
            for (k in keys) {
                val bb = format.getByteBuffer(k)?.duplicate() ?: continue
                if (bb.remaining() <= 0) continue
                val arr = ByteArray(bb.remaining()).also { bb.get(it) }
                val hasStartCode = (arr.size >= 4 && arr[0].toInt() == 0 && arr[1].toInt() == 0 && arr[2].toInt() == 0 && arr[3].toInt() == 1) ||
                    (arr.size >= 3 && arr[0].toInt() == 0 && arr[1].toInt() == 0 && arr[2].toInt() == 1)
                if (!hasStartCode) {
                    out.write(byteArrayOf(0, 0, 0, 1))
                }
                out.write(arr)
            }
            val merged = out.toByteArray()
            if (merged.isNotEmpty()) merged else null
        } catch (_: Exception) {
            null
        }
    }

    private fun logActualEncoderOutputFormat(format: MediaFormat, codecKey: String) {
        try {
            val std = if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) format.getInteger(MediaFormat.KEY_COLOR_STANDARD) else -1
            val rng = if (format.containsKey(MediaFormat.KEY_COLOR_RANGE)) format.getInteger(MediaFormat.KEY_COLOR_RANGE) else -1
            val trf = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else -1
            val prof = if (format.containsKey(MediaFormat.KEY_PROFILE)) format.getInteger(MediaFormat.KEY_PROFILE) else -1
            DiagnosticsLogger.log(
                "VIDEO_FMT",
                "$codecKey hardware encoder locked output format.",
                "profile=$prof colorStd=$std colorRange=$rng transfer=$trf"
            )
        } catch (_: Exception) {
        }
    }

    private fun extractAndSendCodecHeaders(
        format: MediaFormat,
        codecKey: String,
        combinedCsd: ByteArray,
        onVideoConfig: (sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) -> Unit
    ) {
        val csd0 = format.getByteBuffer("csd-0")?.duplicate() ?: return
        when (codecKey) {
            "H264" -> {
                val csd1 = format.getByteBuffer("csd-1")?.duplicate()
                if (csd1 != null) {
                    onVideoConfig(stripStartCode(csd0), stripStartCode(csd1), null)
                } else {
                    parseAnnexBNalsAndSend(ByteBuffer.wrap(combinedCsd), isHevc = false, onVideoConfig)
                }
            }
            "HEVC" -> parseAnnexBNalsAndSend(ByteBuffer.wrap(combinedCsd), isHevc = true, onVideoConfig)
            "AV1" -> onVideoConfig(copyBuffer(csd0), null, null)
        }
    }

    private fun extractHeadersFromConfigBuffer(
        buf: ByteBuffer,
        info: MediaCodec.BufferInfo,
        codecKey: String,
        onVideoConfig: (sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) -> Unit
    ) {
        val dup = buf.duplicate()
        dup.position(info.offset)
        dup.limit(infoEnd(info))
        when (codecKey) {
            "H264" -> parseAnnexBNalsAndSend(dup, isHevc = false, onVideoConfig)
            "HEVC" -> parseAnnexBNalsAndSend(dup, isHevc = true, onVideoConfig)
            "AV1" -> onVideoConfig(copyBuffer(dup), null, null)
        }
    }

    private fun stripStartCode(src: ByteBuffer): ByteBuffer {
        val arr = ByteArray(src.remaining())
        src.get(arr)
        val offset = when {
            arr.size >= 4 && arr[0].toInt() == 0 && arr[1].toInt() == 0 && arr[2].toInt() == 0 && arr[3].toInt() == 1 -> 4
            arr.size >= 3 && arr[0].toInt() == 0 && arr[1].toInt() == 0 && arr[2].toInt() == 1 -> 3
            else -> 0
        }
        return ByteBuffer.wrap(arr.copyOfRange(offset, arr.size))
    }

    private fun parseAnnexBNalsAndSend(
        source: ByteBuffer,
        isHevc: Boolean,
        onVideoConfig: (sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) -> Unit
    ) {
        val bytes = ByteArray(source.remaining())
        source.get(bytes)
        val nals = splitAnnexBNalsWithStartCode(bytes)

        var vps: ByteBuffer? = null
        var sps: ByteBuffer? = null
        var pps: ByteBuffer? = null

        for (nal in nals) {
            val scLen = if (nal.size >= 4 && nal[0].toInt() == 0 && nal[1].toInt() == 0 && nal[2].toInt() == 0 && nal[3].toInt() == 1) 4 else 3
            if (nal.size <= scLen) continue
            val rawNal = nal.copyOfRange(scLen, nal.size)
            val headerByte = rawNal[0].toInt() and 0xFF
            if (isHevc) {
                when ((headerByte shr 1) and 0x3F) {
                    32 -> vps = ByteBuffer.wrap(rawNal)
                    33 -> sps = ByteBuffer.wrap(rawNal)
                    34 -> pps = ByteBuffer.wrap(rawNal)
                }
            } else {
                when (headerByte and 0x1F) {
                    7 -> sps = ByteBuffer.wrap(rawNal)
                    8 -> pps = ByteBuffer.wrap(rawNal)
                }
            }
        }
        val finalSps = sps ?: return
        onVideoConfig(finalSps, pps, vps)
    }

    private fun splitAnnexBNalsWithStartCode(data: ByteArray): List<ByteArray> {
        val starts = mutableListOf<Int>()
        var i = 0
        while (i < data.size - 3) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) {
                if (data[i + 2].toInt() == 1) {
                    starts.add(i)
                    i += 3
                    continue
                } else if (i + 3 < data.size && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) {
                    starts.add(i)
                    i += 4
                    continue
                }
            }
            i++
        }
        if (starts.isEmpty()) return listOf(data)
        val out = mutableListOf<ByteArray>()
        for (idx in starts.indices) {
            val from = starts[idx]
            val to = if (idx + 1 < starts.size) starts[idx + 1] else data.size
            out.add(data.copyOfRange(from, to))
        }
        return out
    }

    private fun copyBuffer(src: ByteBuffer): ByteBuffer {
        val arr = ByteArray(src.remaining())
        src.get(arr)
        return ByteBuffer.wrap(arr)
    }

    companion object {
        private const val SCREENSHOT_COOLDOWN_MS = 3_000L
        private const val HUD_REFRESH_INTERVAL_MS = 2_000L
        private const val PNGTUBER_TALK_HOLD_MS = 140L
        internal const val MAX_TRANSIENT_CODEC_RETRIES = 5

        /**
         * Bounded retry gate for transient MediaCodec.CodecException errors inside the drain loop:
         * retries up to MAX_TRANSIENT_CODEC_RETRIES consecutive times while running, then escalates
         * cleanly to fatal error handling instead of silently stopping the encoder in LIVE state.
         */
        internal fun shouldRetryTransientCodecException(
            isTransient: Boolean,
            isRunning: Boolean,
            consecutiveTransientErrors: Int
        ): Boolean {
            return isTransient && isRunning && consecutiveTransientErrors < MAX_TRANSIENT_CODEC_RETRIES
        }

        /**
         * Unified Graphics/Gralloc PSS Resize Guard (16:9 Landscape & 9:16 Vertical):
         * Blocks VirtualDisplay reallocations when the captured target is hidden/unfocused,
         * blocks redundant identical-size resizes, and blocks temporary portrait resizes during 16:9 streams.
         */
        internal fun shouldAcceptCaptureResize(
            isVerticalPrimary: Boolean,
            reqW: Int,
            reqH: Int,
            isVisible: Boolean,
            currentCapW: Int,
            currentCapH: Int
        ): Boolean {
            if (reqW < 128 || reqH < 128) return false
            if (!isVisible) return false
            if (reqW == currentCapW && reqH == currentCapH) return false
            if (!isVerticalPrimary && reqW < reqH) return false
            return true
        }

        /**
         * Clamps clean screenshot dimensions to <=1920 on the longest side (max 1920x1080 = 7.91 MiB RGBA8)
         * while preserving exact aspect ratio for both 16:9 landscape and 9:16 Shorts streams.
         */
        internal fun computeScreenshotDimensions(primaryW: Int, primaryH: Int): Pair<Int, Int> {
            val safeW = max(2, primaryW)
            val safeH = max(2, primaryH)
            val longSide = max(safeW, safeH)
            val scale = if (longSide > 1920) 1920f / longSide.toFloat() else 1.0f
            val w = max(2, (safeW * scale).roundToInt()).let { if (it % 2 == 0) it else it - 1 }
            val h = max(2, (safeH * scale).roundToInt()).let { if (it % 2 == 0) it else it - 1 }
            return w to h
        }

        /**
         * Resolves the exact OpenGL glScissor rectangle for all 8 Privacy Blur Shield zones
         * across any landscape (16:9) or vertical (9:16) output resolution.
         */
        internal fun resolvePrivacyBlurRect(dstW: Int, dstH: Int, zone: String): ScissorRect {
            return when (zone.uppercase(Locale.US)) {
                "KEYBOARD" -> {
                    val h = (dstH * 0.54f).roundToInt().coerceIn(64, dstH)
                    ScissorRect(0, 0, dstW, h)
                }
                "CENTER_BOX" -> {
                    val w = (dstW * 0.44f).roundToInt().coerceAtLeast(64)
                    val h = (dstH * 0.36f).roundToInt().coerceAtLeast(64)
                    ScissorRect((dstW - w) / 2, (dstH - h) / 2, w, h)
                }
                "TOP_RIGHT_SMALL" -> {
                    val w = (dstW * 0.14f).roundToInt().coerceAtLeast(32)
                    val h = (dstH * 0.12f).roundToInt().coerceAtLeast(32)
                    ScissorRect(dstW - w, dstH - h, w, h)
                }
                "TOP_RIGHT_LARGE" -> {
                    val w = (dstW * 0.28f).roundToInt().coerceAtLeast(48)
                    val h = (dstH * 0.36f).roundToInt().coerceAtLeast(48)
                    ScissorRect(dstW - w, dstH - h, w, h)
                }
                "TOP_LEFT" -> {
                    val w = (dstW * 0.21f).roundToInt().coerceAtLeast(32)
                    val h = (dstH * 0.28f).roundToInt().coerceAtLeast(32)
                    ScissorRect(0, dstH - h, w, h)
                }
                "BOTTOM_LEFT" -> {
                    val w = (dstW * 0.21f).roundToInt().coerceAtLeast(32)
                    val h = (dstH * 0.28f).roundToInt().coerceAtLeast(32)
                    ScissorRect(0, 0, w, h)
                }
                "BOTTOM_RIGHT" -> {
                    val w = (dstW * 0.21f).roundToInt().coerceAtLeast(32)
                    val h = (dstH * 0.28f).roundToInt().coerceAtLeast(32)
                    ScissorRect(dstW - w, 0, w, h)
                }
                else -> {
                    val w = (dstW * 0.21f).roundToInt().coerceAtLeast(32)
                    val h = (dstH * 0.28f).roundToInt().coerceAtLeast(32)
                    ScissorRect(dstW - w, dstH - h, w, h)
                }
            }
        }

        internal fun resolveSupportedResolutionAndFps(
            report: HardwareEncoderReport,
            reqWidth: Int,
            reqHeight: Int,
            reqFps: Int
        ): Triple<Int, Int, Int> {
            val reqLong = max(reqWidth, reqHeight)
            val want60 = reqFps >= 60

            if (reqLong >= 3840) {
                if (want60 && report.supports4k60) return Triple(3840, 2160, 60)
                if (report.supports4k30 || report.supports4k60) return Triple(3840, 2160, 30)
            }
            if (reqLong >= 2560) {
                if (want60 && report.supports1440p60) return Triple(2560, 1440, 60)
                if (report.supports1440p30 || report.supports1440p60) return Triple(2560, 1440, 30)
            }
            if (reqLong >= 1920) {
                if (want60 && report.supports1080p60) return Triple(1920, 1080, 60)
                if (!want60 && (report.supports1080p30 || report.supports1080p60)) return Triple(1920, 1080, 30)
                if (report.supports1080p30) return Triple(1920, 1080, 30)
                if (want60 && report.supports720p60) return Triple(1280, 720, 60)
            }
            if (reqLong >= 1280) {
                if (want60 && report.supports720p60) return Triple(1280, 720, 60)
                if (report.supports720p30 || report.supports720p60) return Triple(1280, 720, 30)
            }
            if (reqLong >= 800) {
                if (report.supports480p60 || report.supports480p30 || report.supports480p) {
                    val fps480 = if (want60 && report.supports480p60) 60 else 30
                    return Triple(854, 480, fps480)
                }
                return Triple(1280, 720, if (want60 && report.supports720p60) 60 else 30)
            }
            if (report.supports360p60 || report.supports360p30 || report.supports360p) {
                val fps360 = if (want60 && report.supports360p60) 60 else 30
                return Triple(640, 360, fps360)
            }
            if (report.supports480p) return Triple(854, 480, 30)
            return Triple(1280, 720, 30)
        }

        internal fun buildResolutionFallbackLadder(
            report: HardwareEncoderReport,
            startLong: Int,
            startShort: Int,
            startFps: Int
        ): List<Triple<Int, Int, Int>> {
            val allTiers = buildList {
                add(Triple(startLong, startShort, startFps))
                if (startLong >= 3840 && startFps >= 60 && (report.supports4k30 || report.supports4k60)) {
                    add(Triple(3840, 2160, 30))
                }
                if (startLong >= 3840 && report.supports1440p60 && startFps >= 60) {
                    add(Triple(2560, 1440, 60))
                }
                if (startLong >= 2560 && (report.supports1440p30 || report.supports1440p60)) {
                    add(Triple(2560, 1440, 30))
                }
                if (report.supports1080p60 && startFps >= 60) add(Triple(1920, 1080, 60))
                if (report.supports1080p30 || report.supports1080p60) add(Triple(1920, 1080, 30))
                if (report.supports720p60 && startFps >= 60) add(Triple(1280, 720, 60))
                if (report.supports720p30 || report.supports720p60) add(Triple(1280, 720, 30))
                if (report.supports480p60 && startFps >= 60) add(Triple(854, 480, 60))
                if (report.supports480p30 || report.supports480p) add(Triple(854, 480, 30))
                if (report.supports360p60 && startFps >= 60) add(Triple(640, 360, 60))
                if (report.supports360p30 || report.supports360p) add(Triple(640, 360, 30))
            }
            return allTiers.distinct()
        }
    }

    private object GlShaderBank {

        data class OesHandles(
            val programId: Int,
            val aPosLoc: Int,
            val aTexLoc: Int,
            val uMvpLoc: Int,
            val uTexMatLoc: Int,
            val uTexSamplerLoc: Int,
            val uTexelStepLoc: Int,
            val uFastPassthroughLoc: Int,
            val uUpscalerLoc: Int,
            val uBlurModeLoc: Int,
            val uShadowBoostLoc: Int,
            val uFilmicLoc: Int,
            val uVividLoc: Int,
            val uTempShiftLoc: Int,
            val uSharpenLoc: Int
        )

        data class Rgba2dHandles(
            val programId: Int,
            val aPosLoc: Int,
            val aTexLoc: Int,
            val uMvpLoc: Int,
            val uSamplerLoc: Int
        )

        private const val OES_VS_ES30 = """#version 300 es
            precision highp float;
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            uniform vec2 uTexelStep;
            in vec4 aPosition;
            in vec4 aTexCoord;
            out vec2 vTexCoord;
            out vec2 vUvN;
            out vec2 vUvS;
            out vec2 vUvE;
            out vec2 vUvW;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vec2 uv = (uTexMatrix * aTexCoord).xy;
                vTexCoord = uv;
                vUvN = uv + vec2(0.0, uTexelStep.y);
                vUvS = uv - vec2(0.0, uTexelStep.y);
                vUvE = uv + vec2(uTexelStep.x, 0.0);
                vUvW = uv - vec2(uTexelStep.x, 0.0);
            }
        """

        private const val OES_FS_BODY_CORE = """
            uniform highp vec2 uTexelStep;
            uniform mediump float uFastPassthrough;
            uniform mediump float uUpscaler;
            uniform mediump float uBlurMode;
            uniform mediump float uShadowBoost;
            uniform mediump float uFilmic;
            uniform mediump float uVivid;
            uniform mediump float uTempShift;
            uniform mediump float uSharpen;

            mediump vec3 applyOrderedVisualFilters(mediump vec3 baseCol) {
                mediump vec3 col = baseCol;

                if (uSharpen > 0.01 || uShadowBoost > 0.5) {
                    mediump vec3 n = TEX_FETCH(sTexture, vUvN).rgb;
                    mediump vec3 s = TEX_FETCH(sTexture, vUvS).rgb;
                    mediump vec3 e = TEX_FETCH(sTexture, vUvE).rgb;
                    mediump vec3 w = TEX_FETCH(sTexture, vUvW).rgb;
                    mediump vec3 avg = 0.25 * (n + s + e + w);

                    if (uSharpen > 0.01) {
                        mediump vec3 minN = min(min(n, s), min(e, min(w, col)));
                        mediump vec3 maxN = max(max(n, s), max(e, max(w, col)));
                        mediump vec3 contrastHeadroom = clamp(min(minN, 1.0 - maxN) / max(maxN, vec3(0.001)), 0.0, 1.0);
                        col = clamp(col + (col - avg) * (uSharpen * (0.45 + 0.55 * contrastHeadroom)), 0.0, 1.0);
                    }

                    if (uShadowBoost > 0.5) {
                        mediump float ambLuma = dot(0.5 * (col + avg), vec3(0.2126, 0.7152, 0.0722));
                        mediump float darkMask = 1.0 - smoothstep(0.08, 0.44, ambLuma);
                        col = clamp(col + (0.22 * darkMask) * col * (1.0 - col) * (1.0 - col), 0.0, 1.0);
                    }
                }

                if (uFilmic > 0.5) {
                    mediump vec3 aces = clamp((col * (2.51 * col + 0.03)) / (col * (2.43 * col + 0.59) + 0.14), 0.0, 1.0);
                    mediump vec3 safeAces = max(aces, col * 0.90);
                    col = mix(col, safeAces, 0.55);
                }

                if (uVivid > 0.5) {
                    mediump float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
                    mediump float maxC = max(col.r, max(col.g, col.b));
                    mediump float minC = min(col.r, min(col.g, col.b));
                    mediump float curSat = maxC - minC;
                    mediump float skinProtect = 1.0 - 0.28 * clamp(col.r - col.g, 0.0, 1.0);
                    mediump float vibGain = 1.0 + (0.25 * (1.0 - curSat) * skinProtect);
                    col = clamp(vec3(luma) + (col - vec3(luma)) * vibGain, 0.0, 1.0);
                }

                if (abs(uTempShift) > 0.1) {
                    mediump vec3 tint = vec3(1.0 + 0.042 * uTempShift, 1.0 + 0.008 * uTempShift, 1.0 - 0.048 * uTempShift);
                    col = clamp(col * tint, 0.0, 1.0);
                }

                return col;
            }

            mediump vec3 sampleGameSpatial(highp vec2 uv) {
                if (uBlurMode > 0.5) {
                    highp vec2 r1 = uTexelStep * 4.5;
                    highp vec2 r2 = uTexelStep * 9.5;
                    mediump vec3 acc = TEX_FETCH(sTexture, uv).rgb * 0.20;
                    acc += TEX_FETCH(sTexture, uv + vec2(r1.x, 0.0)).rgb * 0.12;
                    acc += TEX_FETCH(sTexture, uv - vec2(r1.x, 0.0)).rgb * 0.12;
                    acc += TEX_FETCH(sTexture, uv + vec2(0.0, r1.y)).rgb * 0.12;
                    acc += TEX_FETCH(sTexture, uv - vec2(0.0, r1.y)).rgb * 0.12;
                    acc += TEX_FETCH(sTexture, uv + vec2(r2.x, r2.y)).rgb * 0.08;
                    acc += TEX_FETCH(sTexture, uv + vec2(-r2.x, r2.y)).rgb * 0.08;
                    acc += TEX_FETCH(sTexture, uv + vec2(r2.x, -r2.y)).rgb * 0.08;
                    acc += TEX_FETCH(sTexture, uv + vec2(-r2.x, -r2.y)).rgb * 0.08;
                    return acc * 0.54;
                }

                if (uUpscaler < 0.5) {
                    return TEX_FETCH(sTexture, uv).rgb;
                }

                highp vec2 texSize = 1.0 / max(uTexelStep, vec2(0.00001));
                highp vec2 coord = uv * texSize - 0.5;
                highp vec2 f = fract(coord);
                highp vec2 sf = f * f * (3.0 - 2.0 * f);
                highp vec2 smoothUv = (floor(coord) + sf + 0.5) * uTexelStep;

                if (uUpscaler < 1.5) {
                    mediump vec3 c = TEX_FETCH(sTexture, smoothUv).rgb;
                    mediump vec3 n = TEX_FETCH(sTexture, smoothUv + vec2(0.0, uTexelStep.y * 0.65)).rgb;
                    mediump vec3 s = TEX_FETCH(sTexture, smoothUv - vec2(0.0, uTexelStep.y * 0.65)).rgb;
                    mediump vec3 e = TEX_FETCH(sTexture, smoothUv + vec2(uTexelStep.x * 0.65, 0.0)).rgb;
                    mediump vec3 w = TEX_FETCH(sTexture, smoothUv - vec2(uTexelStep.x * 0.65, 0.0)).rgb;
                    mediump vec3 minB = min(c, min(min(n, s), min(e, w)));
                    mediump vec3 maxB = max(c, max(max(n, s), max(e, w)));
                    return clamp(c * 1.16 - 0.04 * (n + s + e + w), minB, maxB);
                }

                highp vec2 tmuDiag = uTexelStep * 1.2071;
                mediump vec3 c0 = TEX_FETCH(sTexture, smoothUv).rgb;
                mediump vec3 d1 = TEX_FETCH(sTexture, smoothUv + tmuDiag).rgb;
                mediump vec3 d2 = TEX_FETCH(sTexture, smoothUv - tmuDiag).rgb;
                mediump vec3 d3 = TEX_FETCH(sTexture, smoothUv + vec2(tmuDiag.x, -tmuDiag.y)).rgb;
                mediump vec3 d4 = TEX_FETCH(sTexture, smoothUv + vec2(-tmuDiag.x, tmuDiag.y)).rgb;
                mediump vec3 lo = min(c0, min(min(d1, d2), min(d3, d4)));
                mediump vec3 hi = max(c0, max(max(d1, d2), max(d3, d4)));
                mediump vec3 recon = c0 * 1.32 - 0.08 * (d1 + d2 + d3 + d4);
                return clamp(recon, lo, hi);
            }
        """

        private val OES_FS_ES30 = """#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            in highp vec2 vTexCoord;
            in highp vec2 vUvN;
            in highp vec2 vUvS;
            in highp vec2 vUvE;
            in highp vec2 vUvW;
            uniform samplerExternalOES sTexture;
            out mediump vec4 fragColor;
            ${OES_FS_BODY_CORE.replace("TEX_FETCH", "texture")}
            void main() {
                if (uFastPassthrough > 0.5) {
                    fragColor = vec4(texture(sTexture, vTexCoord).rgb, 1.0);
                    return;
                }
                mediump vec3 col = sampleGameSpatial(vTexCoord);
                if (uBlurMode < 0.5) {
                    col = applyOrderedVisualFilters(col);
                }
                fragColor = vec4(col, 1.0);
            }
        """

        private val OES_VS_ES20 = """
            precision highp float;
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            uniform vec2 uTexelStep;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            varying vec2 vUvN;
            varying vec2 vUvS;
            varying vec2 vUvE;
            varying vec2 vUvW;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vec2 uv = (uTexMatrix * aTexCoord).xy;
                vTexCoord = uv;
                vUvN = uv + vec2(0.0, uTexelStep.y);
                vUvS = uv - vec2(0.0, uTexelStep.y);
                vUvE = uv + vec2(uTexelStep.x, 0.0);
                vUvW = uv - vec2(uTexelStep.x, 0.0);
            }
        """

        private val OES_FS_ES20 = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying highp vec2 vTexCoord;
            varying highp vec2 vUvN;
            varying highp vec2 vUvS;
            varying highp vec2 vUvE;
            varying highp vec2 vUvW;
            uniform samplerExternalOES sTexture;
            ${OES_FS_BODY_CORE.replace("TEX_FETCH", "texture2D")}
            void main() {
                if (uFastPassthrough > 0.5) {
                    gl_FragColor = vec4(texture2D(sTexture, vTexCoord).rgb, 1.0);
                    return;
                }
                mediump vec3 col = sampleGameSpatial(vTexCoord);
                if (uBlurMode < 0.5) {
                    col = applyOrderedVisualFilters(col);
                }
                gl_FragColor = vec4(col, 1.0);
            }
        """

        private val RGBA_VS = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = aTexCoord.xy;
            }
        """

        private val RGBA_FS = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sRgbaTex;
            void main() {
                gl_FragColor = texture2D(sRgbaTex, vTexCoord);
            }
        """

        fun buildOesPipelineProgram(allowEs30Shader: Boolean): OesHandles {
            var prog = 0
            if (allowEs30Shader) {
                prog = tryLinkProgram(OES_VS_ES30, OES_FS_ES30)
            }
            if (prog == 0) {
                prog = tryLinkProgram(OES_VS_ES20, OES_FS_ES20)
            }
            if (prog == 0) {
                throw IllegalStateException("Failed to compile OpenGL OES video shader")
            }
            return OesHandles(
                programId = prog,
                aPosLoc = GLES20.glGetAttribLocation(prog, "aPosition"),
                aTexLoc = GLES20.glGetAttribLocation(prog, "aTexCoord"),
                uMvpLoc = GLES20.glGetUniformLocation(prog, "uMVPMatrix"),
                uTexMatLoc = GLES20.glGetUniformLocation(prog, "uTexMatrix"),
                uTexSamplerLoc = GLES20.glGetUniformLocation(prog, "sTexture"),
                uTexelStepLoc = GLES20.glGetUniformLocation(prog, "uTexelStep"),
                uFastPassthroughLoc = GLES20.glGetUniformLocation(prog, "uFastPassthrough"),
                uUpscalerLoc = GLES20.glGetUniformLocation(prog, "uUpscaler"),
                uBlurModeLoc = GLES20.glGetUniformLocation(prog, "uBlurMode"),
                uShadowBoostLoc = GLES20.glGetUniformLocation(prog, "uShadowBoost"),
                uFilmicLoc = GLES20.glGetUniformLocation(prog, "uFilmic"),
                uVividLoc = GLES20.glGetUniformLocation(prog, "uVivid"),
                uTempShiftLoc = GLES20.glGetUniformLocation(prog, "uTempShift"),
                uSharpenLoc = GLES20.glGetUniformLocation(prog, "uSharpen")
            )
        }

        fun buildRgba2dProgram(): Rgba2dHandles {
            val prog = tryLinkProgram(RGBA_VS, RGBA_FS)
            if (prog == 0) {
                throw IllegalStateException("Failed to compile OpenGL RGBA overlay shader")
            }
            return Rgba2dHandles(
                programId = prog,
                aPosLoc = GLES20.glGetAttribLocation(prog, "aPosition"),
                aTexLoc = GLES20.glGetAttribLocation(prog, "aTexCoord"),
                uMvpLoc = GLES20.glGetUniformLocation(prog, "uMVPMatrix"),
                uSamplerLoc = GLES20.glGetUniformLocation(prog, "sRgbaTex")
            )
        }

        private fun tryLinkProgram(vsSrc: String, fsSrc: String): Int {
            val vs = compileChecked(GLES20.GL_VERTEX_SHADER, vsSrc)
            if (vs == 0) return 0
            val fs = compileChecked(GLES20.GL_FRAGMENT_SHADER, fsSrc)
            if (fs == 0) {
                GLES20.glDeleteShader(vs)
                return 0
            }
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(prog)
                return 0
            }
            return prog
        }

        private fun compileChecked(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            if (shader == 0) return 0
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        }

        fun uploadBitmapTexture2d(bitmap: Bitmap, nearestFilter: Boolean = false): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val texId = ids[0]
            if (texId == 0) return 0
            val filter = if (nearestFilter) GLES20.GL_NEAREST else GLES20.GL_LINEAR
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            return texId
        }

        fun updateBitmapTexture2d(texId: Int, bitmap: Bitmap) {
            if (texId == 0) return
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        }

        fun deleteTexture2d(texId: Int) {
            if (texId != 0) {
                try { GLES20.glDeleteTextures(1, intArrayOf(texId), 0) } catch (_: Exception) {}
            }
        }

        fun createFloatBuffer(coords: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(coords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(coords)
                    position(0)
                }
    }
}
