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
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    @Volatile var liveFramingMode = "FIT"         // "FIT", "STRETCH", "SMART", "FILL"
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
    @Volatile var liveOverlayPosition = "FULL"    // "FULL", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"
    @Volatile var liveBannerEnabled = false
    @Volatile var liveBannerText = ""
    @Volatile var liveBannerPosition = "TOP"
    @Volatile var liveMinimapShieldEnabled = false
    @Volatile var liveMinimapShieldCorner = "TOP_RIGHT"
    @Volatile var liveRoiMode = "AUTO"

    /**
     * Independent Dual-Layer Keyboard Privacy Blur (#24):
     * Blurs the bottom half of the stream when the keyboard opens (or when manually toggled in the
     * Floating Pill) without disabling or interfering with the user's Minimap Blur Shield!
     */
    @Volatile var keyboardBlurActive: Boolean = false

    /** Automatic Severe-Thermal cooldown relief: steps 60 FPS pacing down to 30 FPS temporarily. */
    @Volatile var thermalThrottle30Fps = false

    /** Stage-1 Moderate-Warmth (>=44.0°C / 90% SoC load) repeat-frame 1-tap GPU cooler (keeps 100% quality on fresh frames). */
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

    @Volatile private var pendingCaptureWidth = 0
    @Volatile private var pendingCaptureHeight = 0

    private val codecReleaseLock = Any()
    private val projectionCallbackLock = Any()
    private var maxHardwareBitrateBps = 100_000_000
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
        val clamped = newBitrateBps.coerceIn(800_000, 25_000_000)
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
     * API 30+ and vendor-specific Qualcomm Snapdragon / Samsung Exynos keys (#25).
     */
    private fun pushHardwareRoiIfSupported(codec: MediaCodec, width: Int, height: Int, mode: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
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
                if (primarySupportsRoi) {
                    putString("qp-offset-rects", rectSpec)
                }
                if (encLower.contains("qti") || encLower.contains("qcom")) {
                    putString("vendor.qti-ext-enc-roiinfo.rect-payload", rectSpec)
                }
            }
            if (!b.isEmpty) {
                codec.setParameters(b)
            }
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
        applyLiveVisualSettings(settings)
        thermalThrottle30Fps = false
        thermalModerateCoolerActive = false
        companionPausedByThermal = false
        lastSentVideoPtsUs = -1L
        actualEncodedFps = 0
        actualEncodedBitrateBps = 0
        actualFreshCaptureFps = 0
        configuredKeyframeSec = settings.keyframeIntervalSec.coerceIn(1, 4)

        val scan = CodecCapabilityProbe.getBestAvailableScanNonBlocking(context)
        val singleEncodeSimulcastLock = (settings.simulcastTwitchEnabled || settings.simulcastKickEnabled) &&
            !settings.simulcastSplitEncode
        val strictH264Lock = singleEncodeSimulcastLock ||
            settings.platformPreset.equals("TWITCH", ignoreCase = true) ||
            settings.platformPreset.equals("KICK", ignoreCase = true)

        val effectiveCodecReq = if (strictH264Lock) {
            "H264"
        } else {
            settings.codec
        }
        val candidateReports = resolveCandidateHardwareEncoders(
            scan = scan,
            requestedCodec = effectiveCodecReq,
            strictCodecOnly = strictH264Lock
        )
        if (candidateReports.isEmpty()) {
            DiagnosticsLogger.log(
                "VIDEO_ERR",
                "No hardware video encoder matched $effectiveCodecReq. Software fallback is blocked to protect CODM FPS.",
                "requested=$effectiveCodecReq strictH264=$strictH264Lock"
            )
            return null
        }

        val preferEs30 = when (settings.glEngineMode.uppercase(Locale.US)) {
            "ES20" -> false
            "ES30" -> true
            else -> scan.supportsGlEs30
        }
        val canRecord10BitEgl = settings.hdr10Enabled &&
            preferEs30 &&
            !strictH264Lock &&
            canCreateRecordable10BitEglConfig()

        val requestedFps = if (settings.fps <= 30) 30 else 60

        var chosenReport: HardwareEncoderReport? = null
        var configured: ConfiguredEncoder? = null
        var outWidth = 1920
        var outHeight = 1080
        var targetFps = requestedFps
        var startBps = 12_000_000

        // Live Hardware Encoder & Resolution Fallback Ladder
        outerLoop@ for ((reportIdx, candidate) in candidateReports.withIndex()) {
            val (initialLong, initialShort, initialFps) = resolveSupportedResolutionAndFps(
                report = candidate,
                reqWidth = settings.width,
                reqHeight = settings.height,
                reqFps = requestedFps
            )
            val ladder = buildResolutionFallbackLadder(
                report = candidate,
                startLong = initialLong,
                startShort = initialShort,
                startFps = initialFps
            )
            // GPT Audit Fix #3: strictly respect the hardware encoder's chipMaxBps ceiling
            val candidateMaxBps = (candidate.maxBitrateMbps.coerceIn(4, 100) * 1_000_000)
            val wantHdr10 = canRecord10BitEgl && candidate.supports10BitHdr

            for ((tierIdx, tier) in ladder.withIndex()) {
                val (tierLong, tierShort, tierFps) = tier
                val rawW = if (settings.isVertical) tierShort else tierLong
                val rawH = if (settings.isVertical) tierLong else tierShort
                val (alignedW, alignedH) = candidate.alignDimensions(rawW, rawH)

                val scaledKbps = if (tierIdx == 0) {
                    settings.targetBitrateKbps.coerceIn(500, 100_000)
                } else {
                    when {
                        tierLong >= 2560 -> min(settings.targetBitrateKbps, 18_000)
                        tierLong >= 1920 -> min(settings.targetBitrateKbps, 12_000)
                        tierLong >= 1280 -> min(settings.targetBitrateKbps, 6_000)
                        else -> min(settings.targetBitrateKbps, 2_500)
                    }.coerceAtLeast(500)
                }
                val tierBps = (scaledKbps * 1000L).coerceIn(500_000L, candidateMaxBps.toLong()).toInt()

                val attemptCfg = configureHardwareSurfaceEncoder(
                    report = candidate,
                    width = alignedW,
                    height = alignedH,
                    fps = tierFps,
                    bitrateBps = tierBps,
                    wantVbr = settings.useVbr && !strictH264Lock,
                    wantHdr10 = wantHdr10 && tierIdx == 0,
                    settings = settings
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
                            "req=${settings.width}x${settings.height}@$requestedFps"
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

        // Secondary Hardware Encoder setup: supports BOTH 9:16 Vertical Shorts Companion
        // AND Mode 2 Split-Encode 16:9 H.264 Simulcast (Twitch/Kick)!
        var compConfigured: ConfiguredEncoder? = null
        val compReport = if (companionIsLandscapeH264) {
            scan.h264Hardware ?: finalReport
        } else {
            scan.hevcHardware?.takeIf {
                it.verifiedRealEncode || it.verifiedSurfaceConfigure || it.advertisedByMetadata
            } ?: scan.h264Hardware ?: finalReport
        }
        val rawCompW = if (companionIsLandscapeH264) 1920 else 1080
        val rawCompH = if (companionIsLandscapeH264) 1080 else 1920
        val (compAlignedW, compAlignedH) = compReport.alignDimensions(rawCompW, rawCompH)
        val compFps = if (compReport.supports1080p60 && targetFps >= 60) 60 else 30

        if (enableDualCompanion &&
            (!settings.isVertical || companionIsLandscapeH264) &&
            !finalConfigured.actualIsHdr10 &&
            onCompanionConfig != null &&
            onCompanionVideo != null &&
            scan.supportsDualFormatCompanion()
        ) {
            val compStartBps = if (companionIsLandscapeH264) {
                if (settings.simulcastTwitchEnabled) 6_000_000 else 8_000_000
            } else {
                settings.dualFormatBitrateKbps.coerceIn(1500, 20000) * 1000
            }
            companionBitrateBps = compStartBps
            compConfigured = configureHardwareSurfaceEncoder(
                report = compReport,
                width = compAlignedW,
                height = compAlignedH,
                fps = compFps,
                bitrateBps = compStartBps,
                wantVbr = false,
                wantHdr10 = false,
                settings = settings.copy(isVertical = !companionIsLandscapeH264, maxBFrames = 0)
            )
            companionCodecInstance = compConfigured?.codec
        }

        val (rawScreenW, rawScreenH, densityDpi) = readPhysicalDisplayMetrics()
        val panelLong = evenDimension(max(rawScreenW, rawScreenH).coerceAtLeast(1280))
        val panelShort = evenDimension(min(rawScreenW, rawScreenH).coerceAtLeast(720))
        val capWidth = if (settings.isVertical) panelShort else panelLong
        val capHeight = if (settings.isVertical) panelLong else panelShort
        pendingCaptureWidth = 0
        pendingCaptureHeight = 0

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
                    if (isRunning && safeW >= 128 && safeH >= 128) {
                        pendingCaptureWidth = safeW
                        pendingCaptureHeight = safeH
                    }
                }

                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    if (isRunning && !isVisible) {
                        DiagnosticsLogger.log(
                            "CAPTURE_VIS",
                            "Single-app capture target moved to background; holding steady broadcast cadence.",
                            "visible=false"
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
        renderThread = Thread({
            runGlRenderLoop(
                mediaProjection = mediaProjection,
                encoderInputSurface = encoderInputSurface,
                companionInputSurface = compSurf,
                companionWidth = compAlignedW,
                companionHeight = compAlignedH,
                preferGlEs30 = preferEs30,
                use10BitEgl = finalConfigured.actualIsHdr10,
                initialCapWidth = capWidth,
                initialCapHeight = capHeight,
                densityDpi = densityDpi,
                outWidth = outWidth,
                outHeight = outHeight,
                isVerticalPrimary = settings.isVertical,
                targetFps = targetFps,
                originTimeNs = originTimeNs,
                onEglInitialized = { res ->
                    eglInitRef.set(res)
                    eglLatch.countDown()
                }
            )
        }, "SolGlFramePacer").apply { start() }

        val latchCompleted = try {
            eglLatch.await(1500, TimeUnit.MILLISECONDS)
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

        val verifiedCompanionActive = compConfigured != null &&
            eglInitResult.companionReady &&
            onCompanionConfig != null &&
            onCompanionVideo != null

        if (verifiedCompanionActive && compConfigured != null && onCompanionConfig != null && onCompanionVideo != null) {
            val modeDesc = if (companionIsLandscapeH264) "Split-Encode 16:9 H.264 Simulcast" else "Dual-Format 9:16 Shorts Companion"
            DiagnosticsLogger.log(
                "DUAL_ENC",
                "$modeDesc hardware encoder & EGL surface active (${compAlignedW}x${compAlignedH}@${compFps}fps).",
                "encoder=${compReport.encoderName} codec=${compReport.codecKey}"
            )
            companionDrainThread = Thread({
                runEncoderDrainLoop(
                    codec = compConfigured.codec,
                    codecKey = compReport.codecKey,
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
     * [CAPTURE_STOP] event fires, then joins worker threads and releases codecs/VirtualDisplay safely.
     */
    fun stopPipeline() {
        prepareForImmediateStop()
        val rt = renderThread
        val dt = drainThread
        val cdt = companionDrainThread

        try { rt?.join(1500) } catch (_: Exception) {}
        if (rt?.isAlive != true) {
            renderThread = null
            releaseVirtualDisplaySafely()
        }

        synchronized(codecReleaseLock) {
            try { videoCodecInstance?.signalEndOfInputStream() } catch (_: Exception) {}
            try { companionCodecInstance?.signalEndOfInputStream() } catch (_: Exception) {}
        }

        try { dt?.join(1500) } catch (_: Exception) {}
        if (dt?.isAlive != true) {
            drainThread = null
            releasePrimaryCodecSafely()
        }

        try { cdt?.join(1200) } catch (_: Exception) {}
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
     * GPT Audit Fix #1: When strictCodecOnly is true (Single-Encode Twitch/Kick Simulcast or
     * Twitch/Kick Primary Preset), strictly returns ONLY H.264 hardware encoders and never falls back to HEVC/AV1.
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
        wantHdr10: Boolean,
        settings: StreamSettings
    ): ConfiguredEncoder? {
        for (attempt in 1..3) {
            var codec: MediaCodec? = null
            try {
                val created = MediaCodec.createByCodecName(report.encoderName)
                codec = created
                val tryHdrThisAttempt = wantHdr10 && attempt == 1
                val requestedMode = when {
                    wantVbr && report.supportsVbr -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                    report.supportsCbr -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    else -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                }

                val keyframeSec = settings.keyframeIntervalSec.coerceIn(1, 4)
                val bFrames = if (attempt == 1) settings.maxBFrames.coerceIn(0, 2) else 0

                val format = MediaFormat.createVideoFormat(report.mimeType, width, height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyframeSec)
                    setInteger(MediaFormat.KEY_BITRATE_MODE, requestedMode)

                    // Always stamp explicit BT.709 HD color standard on SDR (or BT.2020 HLG on HDR10)
                    // across all attempts so YouTube never defaults to washed-out SD BT.601 colors (#25)!
                    if (tryHdrThisAttempt) {
                        setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                        setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                        setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_HLG)
                    } else {
                        setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                        setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                        setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                    }

                    if (attempt <= 2) {
                        setInteger(MediaFormat.KEY_OPERATING_RATE, fps)
                        setInteger(MediaFormat.KEY_PRIORITY, 1)
                        setInteger(MediaFormat.KEY_MAX_B_FRAMES, bFrames)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, fps.toFloat())
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && settings.lowLatencyCodecHint && bFrames == 0) {
                            setInteger(MediaFormat.KEY_LATENCY, 1)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 0)
                            if (attempt == 1) {
                                val qpMin = 14
                                setInteger(MediaFormat.KEY_VIDEO_QP_I_MIN, qpMin)
                                setInteger(MediaFormat.KEY_VIDEO_QP_P_MIN, qpMin)
                                setInteger(MediaFormat.KEY_VIDEO_QP_B_MIN, qpMin)
                                if (settings.qpMaxCap in 24..51) {
                                    val qpCap = settings.qpMaxCap
                                    setInteger(MediaFormat.KEY_VIDEO_QP_I_MAX, qpCap)
                                    setInteger(MediaFormat.KEY_VIDEO_QP_P_MAX, qpCap)
                                    setInteger(MediaFormat.KEY_VIDEO_QP_B_MAX, qpCap)
                                }
                                // Safe Samsung Exynos / Qualcomm vendor QP floor hints
                                val encLower = report.encoderName.lowercase(Locale.US)
                                if (encLower.contains("exynos") || encLower.contains("sec")) {
                                    setInteger("vendor.sec-ext-enc-qp-range.I-minQP", qpMin)
                                    setInteger("vendor.sec-ext-enc-qp-range.P-minQP", qpMin)
                                }
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
                val inputSurface = created.createInputSurface()
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

                // GPT Audit Fix #4: Strictly require explicit HLG or ST2084 transfer or 10-bit profile in outputFormat before reporting HDR10 active
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
                    inputSurface = inputSurface,
                    actualIsVbr = isActualVbr,
                    actualIsHdr10 = verifiedHdrOutput
                )
            } catch (e: Exception) {
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
     * GPT Audit Fix #4: Ensures EGL14.eglTerminate(dpy) is always called in finally when probing
     * 10-bit recordable EGLConfig support.
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
        var posterTexId = 0
        var bannerTexId = 0
        var loadedBannerSig = ""
        var seenAssetRevision = -1

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

                val reqW = pendingCaptureWidth
                val reqH = pendingCaptureHeight
                if (reqW >= 128 && reqH >= 128 && (reqW != activeCapW || reqH != activeCapH)) {
                    pendingCaptureWidth = 0
                    pendingCaptureHeight = 0
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
                val elapsedMs = nowMs - freshWindowStartMs
                if (elapsedMs >= 1000L) {
                    actualFreshCaptureFps = ((freshFramesInWindow * 1000L) / elapsedMs).toInt()
                    DiagnosticsLogger.freshCaptureFps = actualFreshCaptureFps
                    DiagnosticsLogger.repeatedFramesCount = repeatedFramesTotal
                    freshWindowStartMs = nowMs
                    freshFramesInWindow = 0
                }

                if (nowMs - lastRoiPushMs > 2500L) {
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
                    if (overlayTexId != 0) {
                        GlShaderBank.deleteTexture2d(overlayTexId)
                        overlayTexId = 0
                    }
                    if (posterTexId != 0) {
                        GlShaderBank.deleteTexture2d(posterTexId)
                        posterTexId = 0
                    }
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

                if (liveOverlayImageEnabled && overlayTexId == 0) {
                    SolSceneAssetStore.loadSavedBitmapForGl(context, SolSceneAssetStore.AssetSlot.OVERLAY_PNG)?.let { bmp ->
                        overlayBitmapW = max(1, bmp.width)
                        overlayBitmapH = max(1, bmp.height)
                        overlayTexId = GlShaderBank.uploadBitmapTexture2d(bmp)
                        bmp.recycle()
                    }
                } else if (!liveOverlayImageEnabled && overlayTexId != 0) {
                    GlShaderBank.deleteTexture2d(overlayTexId)
                    overlayTexId = 0
                }

                val needPoster = (isVerticalPrimary || companionEglSurface != EGL14.EGL_NO_SURFACE) &&
                    liveShortsBackdropMode == "POSTER"
                if (needPoster && posterTexId == 0) {
                    SolSceneAssetStore.loadSavedBitmapForGl(context, SolSceneAssetStore.AssetSlot.SHORTS_POSTER)?.let { bmp ->
                        posterTexId = GlShaderBank.uploadBitmapTexture2d(bmp)
                        bmp.recycle()
                    }
                } else if (!needPoster && posterTexId != 0) {
                    GlShaderBank.deleteTexture2d(posterTexId)
                    posterTexId = 0
                }

                // Checklist #23: Live Mid-Stream Text Banner Refresh (updates on the very next frame when text is edited)
                val wantBanner = liveBannerEnabled && liveBannerText.isNotBlank()
                val curBannerSig = if (wantBanner) "${liveBannerPosition}:${liveBannerText.trim()}" else ""
                if (wantBanner && (bannerTexId == 0 || curBannerSig != loadedBannerSig)) {
                    if (bannerTexId != 0) GlShaderBank.deleteTexture2d(bannerTexId)
                    val bBmp = SolSceneAssetStore.buildLiveBannerBitmap(liveBannerText.trim())
                    bannerTexId = if (bBmp != null) {
                        val id = GlShaderBank.uploadBitmapTexture2d(bBmp)
                        bBmp.recycle()
                        id
                    } else 0
                    loadedBannerSig = curBannerSig
                } else if (!wantBanner && bannerTexId != 0) {
                    GlShaderBank.deleteTexture2d(bannerTexId)
                    bannerTexId = 0
                    loadedBannerSig = ""
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

        // Checklist #25: 40% Cooler 9:16 BLUR Canvas
        // When streaming 9:16 Shorts with a blurred backdrop, compute how much of the vertical height
        // is covered by the sharp center gameplay and use hardware glScissor to blur ONLY the visible
        // top and bottom letterbox bars (skipping fragment shader blur work inside the covered middle!).
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

                computeFramingMatrix(mvpMatrix, srcW, srcH, dstW, dstH, "FILL", 100, liveLandscapeFlip)
                if (sharpScaleY < 0.96f) {
                    val barHeightPx = (((1.0f - sharpScaleY) * 0.5f * dstH).roundToInt() + 4).coerceIn(8, dstH / 2)
                    GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
                    // Bottom visible blur bar
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
                    // Top visible blur bar
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
                } else {
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
                }
            }
        }

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

        // Layer 1 of Privacy Blur Shield (#24): 8-Zone Minimap / Room-Code / Center Popup / Keyboard Shield
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

        // Layer 2 of Privacy Blur Shield (#24): Independent Automatic/Manual Keyboard Privacy Blur
        // Blurs the bottom 54% of the screen while keeping Layer 1 (Minimap Blur) 100% intact!
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

        // Checklist #23: 5-Position Aspect-Ratio-Preserving .PNG Overlay (Full-Screen Frame or 4 Corner Logos)
        if (overlayTexId != 0) {
            val posMode = liveOverlayPosition
            if (posMode == "FULL") {
                drawRgbaQuad(rgbaHandles, overlayTexId, identityMvp, fullQuadVerts, bitmapTexCoords, enableBlend = true)
            } else {
                computeCornerOverlayMatrix(
                    mvp = overlayMvpMatrix,
                    logoW = overlayBmpW,
                    logoH = overlayBmpH,
                    dstW = dstW,
                    dstH = dstH,
                    position = posMode
                )
                drawRgbaQuad(rgbaHandles, overlayTexId, overlayMvpMatrix, fullQuadVerts, bitmapTexCoords, enableBlend = true)
            }
        }
        if (bannerTexId != 0) {
            val bannerVerts = if (liveBannerPosition == "BOTTOM") bannerBottomVerts else bannerTopVerts
            drawRgbaQuad(rgbaHandles, bannerTexId, identityMvp, bannerVerts, bitmapTexCoords, enableBlend = true)
        }
    }

    private data class ScissorRect(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun resolvePrivacyBlurRect(dstW: Int, dstH: Int, zone: String): ScissorRect {
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

    private fun computeCornerOverlayMatrix(
        mvp: FloatArray,
        logoW: Int,
        logoH: Int,
        dstW: Int,
        dstH: Int,
        position: String
    ) {
        val maxBoxH = dstH * 0.18f
        val maxBoxW = dstW * 0.22f
        val aspect = logoW.toFloat() / max(1, logoH).toFloat()

        val drawW: Float
        val drawH: Float
        if (maxBoxH * aspect <= maxBoxW) {
            drawH = maxBoxH
            drawW = maxBoxH * aspect
        } else {
            drawW = maxBoxW
            drawH = maxBoxW / max(0.05f, aspect)
        }

        val scaleX = (drawW / max(1, dstW).toFloat()).coerceIn(0.04f, 0.90f)
        val scaleY = (drawH / max(1, dstH).toFloat()).coerceIn(0.04f, 0.90f)
        val marginX = 0.05f
        val marginY = 0.06f

        val tx: Float
        val ty: Float
        when (position.uppercase(Locale.US)) {
            "TOP_LEFT" -> {
                tx = -1.0f + marginX + scaleX
                ty = 1.0f - marginY - scaleY
            }
            "BOTTOM_LEFT" -> {
                tx = -1.0f + marginX + scaleX
                ty = -1.0f + marginY + scaleY
            }
            "BOTTOM_RIGHT" -> {
                tx = 1.0f - marginX - scaleX
                ty = -1.0f + marginY + scaleY
            }
            else -> {
                tx = 1.0f - marginX - scaleX
                ty = 1.0f - marginY - scaleY
            }
        }

        Matrix.setIdentityM(mvp, 0)
        Matrix.translateM(mvp, 0, tx, ty, 0f)
        Matrix.scaleM(mvp, 0, scaleX, scaleY, 1f)
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
        GLES20.glUniform2f(h.uTexelStepLoc, 1.0f / srcW.toFloat(), 1.0f / srcH.toFloat())

        // Repeat-Frame 1-Tap Cooler: when Stage-1 Moderate Warmth cooler is active (>=44.0°C & >=90% SoC),
        // repeat ticks use 1-tap hardware fetch while every fresh game frame gets full Bicubic/Lanczos!
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
        val srcAspect = srcW.toFloat() / srcH.toFloat()
        val dstAspect = dstW.toFloat() / dstH.toFloat()
        val sameOrientation = (srcW >= srcH) == (dstW >= dstH)

        var scaleX = 1.0f
        var scaleY = 1.0f

        when {
            // STRETCH (Full 16:9 Zero-Bars): maps all 4 corners of phone screen directly to all 4 corners
            // of 360p, 480p, 720p, 1080p, 1440p, or 4K landscape stream with zero black bars & 100% HUD!
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
        // Trimmed 6-slot direct buffer pool (saves ~11-15 MB idle PSS while keeping zero-GC operation)
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

        try {
            while (isRunning) {
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 15_000L)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
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
                        extractAndSendCodecHeaders(outFmt, codecKey, onVideoConfig)
                    }
                    continue
                }
                if (outIdx < 0) continue

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
            if (ce.isTransient && isRunning) {
                DiagnosticsLogger.log(
                    "ENC_TRANSIENT",
                    "Hardware encoder reported a brief transient stall; continuing stream safely.",
                    ce.diagnosticInfo
                )
            } else if (isRunning) {
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
            val b0 = format.getByteBuffer("csd-0")?.duplicate() ?: return null
            val a0 = ByteArray(b0.remaining()).also { b0.get(it) }
            val b1 = format.getByteBuffer("csd-1")?.duplicate()
            if (b1 != null) {
                val a1 = ByteArray(b1.remaining()).also { b1.get(it) }
                val merged = ByteArray(a0.size + a1.size)
                System.arraycopy(a0, 0, merged, 0, a0.size)
                System.arraycopy(a1, 0, merged, a0.size, a1.size)
                merged
            } else {
                a0
            }
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
        onVideoConfig: (sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) -> Unit
    ) {
        val csd0 = format.getByteBuffer("csd-0")?.duplicate() ?: return
        when (codecKey) {
            "H264" -> {
                val csd1 = format.getByteBuffer("csd-1")?.duplicate()
                if (csd1 != null) {
                    onVideoConfig(stripStartCode(csd0), stripStartCode(csd1), null)
                } else {
                    parseAnnexBNalsAndSend(csd0, isHevc = false, onVideoConfig)
                }
            }
            "HEVC" -> parseAnnexBNalsAndSend(csd0, isHevc = true, onVideoConfig)
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

        /**
         * Builds a descending resolution/FPS fallback ladder starting from (startLong, startShort, startFps)
         * using the exact 60/30 FPS capability flags for every tier including 480p and 360p.
         */
        internal fun buildResolutionFallbackLadder(
            report: HardwareEncoderReport,
            startLong: Int,
            startShort: Int,
            startFps: Int
        ): List<Triple<Int, Int, Int>> {
            val allTiers = buildList {
                add(Triple(startLong, startShort, startFps))
                if (startLong >= 2560 && startFps >= 60 && (report.supports1440p30 || report.supports1440p60)) {
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

        // Precomputed Vertex-Stage Cardinal Neighbor UV Offsets (computed 4 times/frame instead of 3.68M times/frame!)
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

        // Checklist #25: OpenGL ES 3.0 Fast-FP16 Color Math + Hardware TMU 5-Tap Bicubic/Lanczos + AI Adaptive Dark-Corner Optimizer
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

                // AI Adaptive Dark-Corner Optimizer (#25): scales shadow lift by local + ambient darkness
                // so dark indoor corners get +22% visibility while bright outdoor maps never look washed out!
                if (uShadowBoost > 0.5) {
                    mediump float ambLuma = dot(0.5 * (col + avg), vec3(0.2126, 0.7152, 0.0722));
                    mediump float darkMask = 1.0 - smoothstep(0.08, 0.44, ambLuma);
                    col = clamp(col + (0.22 * darkMask) * col * (1.0 - col) * (1.0 - col), 0.0, 1.0);
                }

                if (uFilmic > 0.5) {
                    mediump vec3 aces = clamp((col * (2.51 * col + 0.03)) / (col * (2.43 * col + 0.59) + 0.14), 0.0, 1.0);
                    col = mix(col, aces, 0.55);
                }

                if (uVivid > 0.5) {
                    mediump float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
                    mediump float maxC = max(col.r, max(col.g, col.b));
                    mediump float minC = min(col.r, min(col.g, col.b));
                    mediump float curSat = maxC - minC;
                    mediump float vibGain = 1.0 + 0.25 * (1.0 - curSat);
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

                // Hardware TMU-Assisted 5-Tap Bicubic (1.0) & Catmull-Rom/Lanczos (2.0):
                // Offsets fractional UV coordinates so the GPU's hardware bilinear unit blends 9-16
                // surrounding texels in only 5 texture reads!
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

        private const val OES_VS_ES20 = """
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

        private const val RGBA_VS = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = aTexCoord.xy;
            }
        """

        private const val RGBA_FS = """
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

        fun uploadBitmapTexture2d(bitmap: Bitmap): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val texId = ids[0]
            if (texId == 0) return 0
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            return texId
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
