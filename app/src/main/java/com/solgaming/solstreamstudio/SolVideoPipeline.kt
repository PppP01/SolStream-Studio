package com.solgaming.solstreamstudio

import android.content.Context
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
import android.opengl.Matrix
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class SolVideoPipeline(private val context: Context) {

    data class ActiveVideoInfo(
        val codecKey: String,
        val encoderName: String,
        val outWidth: Int,
        val outHeight: Int,
        val fps: Int,
        val isVbr: Boolean
    )

    private data class ConfiguredEncoder(
        val codec: MediaCodec,
        val inputSurface: Surface,
        val actualIsVbr: Boolean
    )

    @Volatile var isRunning = false
        private set

    @Volatile var brbBlackoutActive = false
    @Volatile var liveFramingMode = "FIT"        // "FIT", "FILL", "SMART"
    @Volatile var liveSmartZoomPercent = 108
    @Volatile var liveLandscapeFlip = 0          // 0 or 180
    @Volatile var liveGpuSharpen = false
    @Volatile var currentBitrateBps = 20_000_000
        private set

    @Volatile var actualEncodedFps = 0
        private set
    @Volatile var actualEncodedBitrateBps = 0
        private set
    @Volatile var actualFreshCaptureFps = 0
        private set

    /** Optional hook for SolStreamService to react if hardware encoder/GL dies mid-stream. */
    @Volatile var onFatalPipelineError: ((String) -> Unit)? = null

    @Volatile private var pendingCaptureWidth = 0
    @Volatile private var pendingCaptureHeight = 0

    private var videoCodecInstance: MediaCodec? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var renderThread: Thread? = null
    private var drainThread: Thread? = null
    private var callbackHandlerThread: HandlerThread? = null
    private var projectionRef: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    fun applyLiveVisualSettings(s: StreamSettings) {
        liveFramingMode = s.framingMode
        liveSmartZoomPercent = s.smartZoomPercent.coerceIn(100, 125)
        liveLandscapeFlip = if (s.landscapeFlip == 180) 180 else 0
        liveGpuSharpen = s.gpuSharpen
    }

    fun setBitrateOnFly(newBitrateBps: Int) {
        val clamped = newBitrateBps.coerceIn(1_000_000, 55_000_000)
        currentBitrateBps = clamped
        try {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, clamped)
            }
            videoCodecInstance?.setParameters(params)
        } catch (_: Exception) {
        }
    }

    fun requestSyncFrame() {
        try {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            videoCodecInstance?.setParameters(params)
        } catch (_: Exception) {
        }
    }

    fun startPipeline(
        settings: StreamSettings,
        mediaProjection: MediaProjection,
        originTimeNs: Long,
        onProjectionStoppedBySystem: () -> Unit,
        onVideoConfig: (sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) -> Unit,
        onEncodedVideo: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ): ActiveVideoInfo? {
        if (isRunning) return null
        applyLiveVisualSettings(settings)

        val scan = CodecCapabilityProbe.scanDevice(context)
        val chosenReport = resolveSafeHardwareEncoder(scan, settings)
        if (chosenReport == null) {
            DiagnosticsLogger.log(
                "VIDEO_ERR",
                "No verified hardware video encoder matched ${settings.codec}. Software fallback is blocked to protect CODM FPS.",
                "requested=${settings.codec}"
            )
            return null
        }

        val baseLong = if (chosenReport.supports1440p60 && max(settings.width, settings.height) >= 2560) 2560 else 1920
        val baseShort = if (baseLong == 2560) 1440 else 1080
        val outWidth = if (settings.isVertical) baseShort else baseLong
        val outHeight = if (settings.isVertical) baseLong else baseShort
        val targetFps = if (settings.fps <= 30) 30 else 60
        val startBps = settings.targetBitrateKbps.coerceIn(4000, 50000) * 1000
        currentBitrateBps = startBps

        val configured = configureHardwareSurfaceEncoder(
            report = chosenReport,
            width = outWidth,
            height = outHeight,
            fps = targetFps,
            bitrateBps = startBps,
            wantVbr = settings.useVbr
        ) ?: return null

        val codec = configured.codec
        val encoderInputSurface = configured.inputSurface
        val actualVbr = configured.actualIsVbr
        videoCodecInstance = codec

        val (rawScreenW, rawScreenH, densityDpi) = readPhysicalDisplayMetrics()
        val panelLong = evenDimension(max(rawScreenW, rawScreenH).coerceAtLeast(1920))
        val panelShort = evenDimension(min(rawScreenW, rawScreenH).coerceAtLeast(1080))
        val capWidth = if (settings.isVertical) panelShort else panelLong
        val capHeight = if (settings.isVertical) panelLong else panelShort
        pendingCaptureWidth = 0
        pendingCaptureHeight = 0

        // Android 14+ rule: MUST register MediaProjection.Callback BEFORE createVirtualDisplay()
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

        isRunning = true

        drainThread = Thread({
            runEncoderDrainLoop(
                codec = codec,
                codecKey = chosenReport.codecKey,
                originTimeUs = originTimeNs / 1000L,
                onVideoConfig = onVideoConfig,
                onEncodedVideo = onEncodedVideo
            )
        }, "SolVideoDrain").apply { start() }

        renderThread = Thread({
            runGlRenderLoop(
                mediaProjection = mediaProjection,
                encoderInputSurface = encoderInputSurface,
                initialCapWidth = capWidth,
                initialCapHeight = capHeight,
                densityDpi = densityDpi,
                outWidth = outWidth,
                outHeight = outHeight,
                targetFps = targetFps,
                originTimeNs = originTimeNs
            )
        }, "SolGlFramePacer").apply { start() }

        return ActiveVideoInfo(
            codecKey = chosenReport.codecKey,
            encoderName = chosenReport.encoderName,
            outWidth = outWidth,
            outHeight = outHeight,
            fps = targetFps,
            isVbr = actualVbr
        )
    }

    fun stopPipeline() {
        isRunning = false
        try { renderThread?.join(1500) } catch (_: Exception) {}
        renderThread = null

        try { videoCodecInstance?.signalEndOfInputStream() } catch (_: Exception) {}
        try { drainThread?.join(1500) } catch (_: Exception) {}
        drainThread = null

        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null

        projectionCallback?.let { cb ->
            try { projectionRef?.unregisterCallback(cb) } catch (_: Exception) {}
        }
        projectionCallback = null
        projectionRef = null

        try { callbackHandlerThread?.quitSafely() } catch (_: Exception) {}
        callbackHandlerThread = null

        try { videoCodecInstance?.stop() } catch (_: Exception) {}
        try { videoCodecInstance?.release() } catch (_: Exception) {}
        videoCodecInstance = null

        actualEncodedFps = 0
        actualEncodedBitrateBps = 0
        actualFreshCaptureFps = 0
        DiagnosticsLogger.freshCaptureFps = -1
        DiagnosticsLogger.lastAverageQp = -1
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

    private fun resolveSafeHardwareEncoder(
        scan: DeviceScanSummary,
        settings: StreamSettings
    ): HardwareEncoderReport? {
        val primary = scan.findReport(settings.codec)
        if (primary != null && primary.verifiedSurfaceConfigure) return primary
        if (scan.hevcHardware?.verifiedSurfaceConfigure == true) return scan.hevcHardware
        return scan.h264Hardware?.takeIf { it.verifiedSurfaceConfigure }
    }

    private fun selectSupportedHighProfile(
        codec: MediaCodec,
        mimeType: String,
        codecKey: String,
        width: Int,
        height: Int
    ): Pair<Int, Int>? {
        return try {
            val caps = codec.codecInfo.getCapabilitiesForType(mimeType)
            val levels = caps.profileLevels ?: return null
            if (codecKey == "H264") {
                val targetProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
                val minLevel = if (max(width, height) >= 2560) {
                    MediaCodecInfo.CodecProfileLevel.AVCLevel51
                } else {
                    MediaCodecInfo.CodecProfileLevel.AVCLevel42
                }
                val match = levels
                    .filter { it.profile == targetProfile && it.level >= minLevel }
                    .minByOrNull { it.level }
                    ?: levels.filter { it.profile == targetProfile }.maxByOrNull { it.level }
                match?.let { it.profile to it.level }
            } else if (codecKey == "HEVC") {
                val targetProfile = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
                val minLevel = if (max(width, height) >= 2560) {
                    MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5
                } else {
                    MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41
                }
                val match = levels
                    .filter { it.profile == targetProfile && it.level >= minLevel }
                    .minByOrNull { it.level }
                    ?: levels.filter { it.profile == targetProfile }.maxByOrNull { it.level }
                match?.let { it.profile to it.level }
            } else {
                null
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
        wantVbr: Boolean
    ): ConfiguredEncoder? {
        for (attempt in 1..3) {
            var codec: MediaCodec? = null
            try {
                codec = MediaCodec.createByCodecName(report.encoderName)
                val requestedMode = when {
                    wantVbr && report.supportsVbr -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                    report.supportsCbr -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    else -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                }

                val format = MediaFormat.createVideoFormat(report.mimeType, width, height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                    setInteger(MediaFormat.KEY_BITRATE_MODE, requestedMode)

                    if (attempt <= 2) {
                        setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                        setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                        setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                        setInteger(MediaFormat.KEY_OPERATING_RATE, fps)
                        setInteger(MediaFormat.KEY_PRIORITY, 0)
                        setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, fps.toFloat())
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            setInteger(
                                MediaFormat.KEY_VIDEO_ENCODING_STATISTICS_LEVEL,
                                MediaFormat.VIDEO_ENCODING_STATISTICS_LEVEL_1
                            )
                        }
                        if (report.complexityMax > report.complexityMin) {
                            val midHigh = (report.complexityMin + report.complexityMax + 1) / 2
                            setInteger(MediaFormat.KEY_COMPLEXITY, midHigh)
                        }
                    }

                    if (attempt == 1) {
                        selectSupportedHighProfile(
                            codec = codec,
                            mimeType = report.mimeType,
                            codecKey = report.codecKey,
                            width = width,
                            height = height
                        )?.let { (prof, lvl) ->
                            setInteger(MediaFormat.KEY_PROFILE, prof)
                            setInteger(MediaFormat.KEY_LEVEL, lvl)
                        }
                    }
                }

                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = codec.createInputSurface()
                codec.start()

                val actualMode = try {
                    if (codec.outputFormat.containsKey(MediaFormat.KEY_BITRATE_MODE)) {
                        codec.outputFormat.getInteger(MediaFormat.KEY_BITRATE_MODE)
                    } else {
                        requestedMode
                    }
                } catch (_: Exception) {
                    requestedMode
                }
                val isActualVbr = actualMode == MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR

                if (attempt > 1) {
                    DiagnosticsLogger.log(
                        "VIDEO_CFG",
                        "Hardware encoder ${report.encoderName} configured cleanly on attempt #$attempt.",
                        "${width}x${height}@${fps} mode=${if (isActualVbr) "VBR" else "CBR"}"
                    )
                }
                return ConfiguredEncoder(
                    codec = codec,
                    inputSurface = inputSurface,
                    actualIsVbr = isActualVbr
                )
            } catch (e: Exception) {
                try { codec?.release() } catch (_: Exception) {}
                if (attempt == 3) {
                    DiagnosticsLogger.log(
                        "VIDEO_ERR",
                        "Hardware encoder ${report.encoderName} rejected configuration.",
                        "${e.javaClass.simpleName}: ${e.message}"
                    )
                }
            }
        }
        return null
    }

    private fun runGlRenderLoop(
        mediaProjection: MediaProjection,
        encoderInputSurface: Surface,
        initialCapWidth: Int,
        initialCapHeight: Int,
        densityDpi: Int,
        outWidth: Int,
        outHeight: Int,
        targetFps: Int,
        originTimeNs: Long
    ) {
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        var captureSurface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null
        var oesTexId = 0
        var program = 0

        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            val eglConfig = configs[0] ?: throw IllegalStateException("No recordable EGLConfig")

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

            val surfAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, encoderInputSurface, surfAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            program = GlShaderHelper.buildOesProgram()
            val aPosLoc = GLES20.glGetAttribLocation(program, "aPosition")
            val aTexLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
            val uMvpLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            val uTexMatLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
            val uSharpenLoc = GLES20.glGetUniformLocation(program, "uSharpen")
            val uTexelStepLoc = GLES20.glGetUniformLocation(program, "uTexelStep")

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

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "SolStreamCapture",
                activeCapW,
                activeCapH,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                capSurf,
                null,
                null
            )

            val quadVertices = GlShaderHelper.createFloatBuffer(
                floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            )
            val quadTexCoords = GlShaderHelper.createFloatBuffer(
                floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
            )
            val stMatrix = FloatArray(16)
            val mvpMatrix = FloatArray(16)
            Matrix.setIdentityM(stMatrix, 0)

            val frameIntervalNs = 1_000_000_000L / targetFps.toLong()
            var nextRenderNs = System.nanoTime()
            var lastSurfaceTsNs = -1L
            var freshWindowStartMs = System.currentTimeMillis()
            var freshFramesInWindow = 0
            var repeatedFramesTotal = 0L
            var wasBrbActive = false

            while (isRunning) {
                val nowNs = System.nanoTime()
                val waitNs = nextRenderNs - nowNs
                if (waitNs > 1_000_000L) {
                    synchronized(frameSync) {
                        frameSync.wait(waitNs / 1_000_000L, (waitNs % 1_000_000L).toInt())
                    }
                    continue
                }

                // Advance target timestamp strictly at 60/30 FPS without burst catch-up
                nextRenderNs += frameIntervalNs
                if (nowNs - nextRenderNs > frameIntervalNs * 2) {
                    nextRenderNs = nowNs + frameIntervalNs
                }

                // Apply dynamic Android 14+ single-app capture resize without restarting encoder
                val reqW = pendingCaptureWidth
                val reqH = pendingCaptureHeight
                if (reqW >= 128 && reqH >= 128 && (reqW != activeCapW || reqH != activeCapH)) {
                    pendingCaptureWidth = 0
                    pendingCaptureHeight = 0
                    activeCapW = reqW
                    activeCapH = reqH
                    try {
                        st.setDefaultBufferSize(activeCapW, activeCapH)
                        virtualDisplay?.resize(activeCapW, activeCapH, densityDpi)
                        DiagnosticsLogger.log(
                            "CAPTURE_RESIZE",
                            "Adapted incoming game capture size seamlessly without interrupting YouTube encoder.",
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
                if (pullNewTexture) {
                    st.updateTexImage()
                    st.getTransformMatrix(stMatrix)
                    val curTs = st.timestamp
                    if (curTs != lastSurfaceTsNs) {
                        lastSurfaceTsNs = curTs
                        freshFramesInWindow++
                    } else {
                        repeatedFramesTotal++
                    }
                } else if (!brbBlackoutActive && hasFirstFrame) {
                    repeatedFramesTotal++
                }

                val isBrbNow = brbBlackoutActive
                if (wasBrbActive && !isBrbNow) {
                    requestSyncFrame()
                }
                wasBrbActive = isBrbNow

                GLES20.glViewport(0, 0, outWidth, outHeight)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                if (!isBrbNow && hasFirstFrame) {
                    computeFramingMatrix(
                        mvp = mvpMatrix,
                        srcW = activeCapW,
                        srcH = activeCapH,
                        dstW = outWidth,
                        dstH = outHeight,
                        mode = liveFramingMode,
                        smartZoomPct = liveSmartZoomPercent,
                        flipDeg = liveLandscapeFlip
                    )

                    GLES20.glUseProgram(program)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)

                    GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
                    GLES20.glUniformMatrix4fv(uTexMatLoc, 1, false, stMatrix, 0)
                    GLES20.glUniform1f(uSharpenLoc, if (liveGpuSharpen) 0.28f else 0.0f)
                    GLES20.glUniform2f(uTexelStepLoc, 1.0f / activeCapW.toFloat(), 1.0f / activeCapH.toFloat())

                    quadVertices.position(0)
                    GLES20.glEnableVertexAttribArray(aPosLoc)
                    GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 0, quadVertices)

                    quadTexCoords.position(0)
                    GLES20.glEnableVertexAttribArray(aTexLoc)
                    GLES20.glVertexAttribPointer(aTexLoc, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)

                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                    GLES20.glDisableVertexAttribArray(aPosLoc)
                    GLES20.glDisableVertexAttribArray(aTexLoc)
                }

                val ptsNs = max(1_000L, System.nanoTime() - originTimeNs)
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                val nowMs = System.currentTimeMillis()
                val elapsedMs = nowMs - freshWindowStartMs
                if (elapsedMs >= 1000L) {
                    actualFreshCaptureFps = ((freshFramesInWindow * 1000L) / elapsedMs).toInt()
                    DiagnosticsLogger.freshCaptureFps = actualFreshCaptureFps
                    DiagnosticsLogger.repeatedFramesCount = repeatedFramesTotal
                    freshWindowStartMs = nowMs
                    freshFramesInWindow = 0
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                val msg = "Video OpenGL render loop interrupted (${e.javaClass.simpleName}: ${e.message ?: "unknown"})."
                DiagnosticsLogger.log("GL_ERR", msg, e.javaClass.name)
                onFatalPipelineError?.invoke(msg)
            }
        } finally {
            try { virtualDisplay?.release() } catch (_: Exception) {}
            virtualDisplay = null
            try { captureSurface?.release() } catch (_: Exception) {}
            try { surfaceTexture?.release() } catch (_: Exception) {}
            if (oesTexId != 0) {
                try { GLES20.glDeleteTextures(1, intArrayOf(oesTexId), 0) } catch (_: Exception) {}
            }
            if (program != 0) {
                try { GLES20.glDeleteProgram(program) } catch (_: Exception) {}
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            try { encoderInputSurface.release() } catch (_: Exception) {}
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

        var scaleX = 1.0f
        var scaleY = 1.0f

        if (mode == "FILL") {
            if (srcAspect > dstAspect) {
                scaleX = srcAspect / dstAspect
            } else {
                scaleY = dstAspect / srcAspect
            }
        } else {
            // "FIT" or "SMART"
            if (srcAspect > dstAspect) {
                scaleY = dstAspect / srcAspect
            } else {
                scaleX = srcAspect / dstAspect
            }
            if (mode == "SMART") {
                val zoom = (smartZoomPct.coerceIn(100, 125)) / 100.0f
                scaleX *= zoom
                scaleY *= zoom
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
        onVideoConfig: (sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) -> Unit,
        onEncodedVideo: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        val packetPool = Array(16) { ByteBuffer.allocateDirect(1_048_576) }
        val infoPool = Array(16) { MediaCodec.BufferInfo() }
        var poolIdx = 0

        var ptsBaseLocked = false
        var ptsBaseUs = 0L
        var lastSentPtsUs = 0L

        var statsWindowStartMs = System.currentTimeMillis()
        var framesInWindow = 0
        var bytesInWindow = 0L
        var qpSumInWindow = 0L
        var qpCountInWindow = 0

        while (isRunning) {
            try {
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 15_000L)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFmt = codec.outputFormat
                    logActualEncoderOutputFormat(outFmt, codecKey)
                    extractAndSendCodecHeaders(outFmt, codecKey, onVideoConfig)
                    continue
                }
                if (outIdx < 0) continue

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                        extractHeadersFromConfigBuffer(outBuf, bufferInfo, codecKey, onVideoConfig)
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
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        targetBuf.put(outBuf)
                        targetBuf.flip()

                        // Lock timestamp base ONCE on Frame 1 so 5-6+ hour streams never jump mid-broadcast
                        if (!ptsBaseLocked) {
                            ptsBaseUs = if (originTimeUs > 2_000_000L &&
                                bufferInfo.presentationTimeUs >= originTimeUs - 500_000L
                            ) {
                                originTimeUs
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

                        val outInfo = infoPool[slot].apply {
                            set(0, bufferInfo.size, adjPtsUs, bufferInfo.flags)
                        }
                        onEncodedVideo(targetBuf, outInfo)

                        framesInWindow++
                        bytesInWindow += bufferInfo.size
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)

                val nowMs = System.currentTimeMillis()
                val elapsedMs = nowMs - statsWindowStartMs
                if (elapsedMs >= 1000L) {
                    actualEncodedFps = ((framesInWindow * 1000L) / elapsedMs).toInt()
                    actualEncodedBitrateBps = ((bytesInWindow * 8000L) / elapsedMs).toInt()
                    DiagnosticsLogger.lastEncodedFps = actualEncodedFps
                    DiagnosticsLogger.lastBitrateKbps = actualEncodedBitrateBps / 1000
                    DiagnosticsLogger.lastAverageQp = if (qpCountInWindow > 0) {
                        (qpSumInWindow / qpCountInWindow).toInt()
                    } else {
                        -1
                    }
                    statsWindowStartMs = nowMs
                    framesInWindow = 0
                    bytesInWindow = 0L
                    qpSumInWindow = 0L
                    qpCountInWindow = 0
                }
            } catch (ce: MediaCodec.CodecException) {
                if (ce.isTransient) {
                    DiagnosticsLogger.log(
                        "ENC_TRANSIENT",
                        "Hardware encoder reported a brief transient stall; continuing marathon stream safely.",
                        ce.diagnosticInfo
                    )
                    try { Thread.sleep(8) } catch (_: Exception) {}
                } else {
                    if (isRunning) {
                        val msg = "Hardware video encoder fatal error (${ce.diagnosticInfo})."
                        DiagnosticsLogger.log("ENC_ERR", msg, ce.message ?: "")
                        onFatalPipelineError?.invoke(msg)
                    }
                    break
                }
            } catch (e: Exception) {
                if (isRunning) {
                    val msg = "Hardware video encoder stopped unexpectedly (${e.javaClass.simpleName})."
                    DiagnosticsLogger.log("ENC_ERR", msg, e.message ?: "")
                    onFatalPipelineError?.invoke(msg)
                }
                break
            }
        }
    }

    private fun logActualEncoderOutputFormat(format: MediaFormat, codecKey: String) {
        try {
            val std = if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                format.getInteger(MediaFormat.KEY_COLOR_STANDARD)
            } else -1
            val rng = if (format.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
                format.getInteger(MediaFormat.KEY_COLOR_RANGE)
            } else -1
            val prof = if (format.containsKey(MediaFormat.KEY_PROFILE)) {
                format.getInteger(MediaFormat.KEY_PROFILE)
            } else -1
            DiagnosticsLogger.log(
                "VIDEO_FMT",
                "$codecKey hardware encoder locked output format.",
                "profile=$prof colorStd=$std colorRange=$rng"
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
        dup.limit(info.offset + info.size)
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

    private object GlShaderHelper {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            uniform float uSharpen;
            uniform vec2 uTexelStep;
            void main() {
                vec4 center = texture2D(sTexture, vTexCoord);
                if (uSharpen <= 0.01) {
                    gl_FragColor = center;
                } else {
                    vec3 n = texture2D(sTexture, vTexCoord + vec2(0.0, uTexelStep.y)).rgb;
                    vec3 s = texture2D(sTexture, vTexCoord - vec2(0.0, uTexelStep.y)).rgb;
                    vec3 e = texture2D(sTexture, vTexCoord + vec2(uTexelStep.x, 0.0)).rgb;
                    vec3 w = texture2D(sTexture, vTexCoord - vec2(uTexelStep.x, 0.0)).rgb;
                    vec3 avg = 0.25 * (n + s + e + w);
                    vec3 sharp = clamp(center.rgb + (center.rgb - avg) * uSharpen, 0.0, 1.0);
                    gl_FragColor = vec4(sharp, center.a);
                }
            }
        """

        fun buildOesProgram(): Int {
            val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return prog
        }

        private fun compileShader(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            return shader
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
