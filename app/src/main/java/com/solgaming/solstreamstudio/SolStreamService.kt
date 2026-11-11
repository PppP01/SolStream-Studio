/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.rtmp.rtmp.RtmpClient
import java.lang.reflect.Method
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SolStreamService : AccessibilityService(), ConnectChecker {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val netExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SolRtmpNetWorker").apply { isDaemon = true }
    }
    private val rttProbeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SolIngestRttProbe").apply { isDaemon = true }
    }

    private var rtmpClient: RtmpClient? = null
    private val auxTargets = mutableListOf<AuxRtmpTarget>()
    private var companionTarget: AuxRtmpTarget? = null

    private var videoPipeline: SolVideoPipeline? = null
    private var audioEngine: SolDualAudioEngine? = null
    private var mediaProjection: MediaProjection? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var activeSettings: StreamSettings
    private var primaryStreamUrl: String = ""
    private var backupStreamUrl: String? = null
    private var primaryIngestHost: String = ""
    private var primaryIngestPort: Int = 443
    private var usingBackupUrl = false

    @Volatile private var broadcastActive = false
    @Volatile private var benchmarkOnlyActive = false
    @Volatile private var rtmpConnected = false
    private val reconnectScheduled = AtomicBoolean(false)
    private val connectionSessionGen = AtomicLong(0L)

    // Manual override flags so automatic thermal saver never fights explicit user clicks
    private var userManuallyOverrodeSharp = false
    private var userManuallyOverrode30FpsSaver = false
    private var severeThermalLastSeenElapsedMs = 0L

    private var reconnectCount = 0
    private var maxConfiguredBitrateBps = 20_000_000
    private var minAdaptiveBitrateBps = 6_000_000
    private var activeBitrateBps = 20_000_000
    private var lastThermalReducedBps = -1
    private var calmTicksCount = 0
    private var zeroThroughputStallTicks = 0
    private var lastDroppedTotal = 0L
    private var lastRttProbeElapsedMs = 0L

    @Volatile private var cachedSps: ByteArray? = null
    @Volatile private var cachedPps: ByteArray? = null
    @Volatile private var cachedVps: ByteArray? = null

    private var windowManager: WindowManager? = null
    private var pillRootView: LinearLayout? = null
    private var pillHeaderRow: LinearLayout? = null
    private var pillStatusDot: TextView? = null
    private var pillStatusText: TextView? = null
    private var pillDrawerContainer: LinearLayout? = null
    private var pillExpanded = false
    private var activePillTab = 0 // 0 = Audio, 1 = Video, 2 = Bitrate/Net
    private var pillLayoutParams: WindowManager.LayoutParams? = null
    private var lastStopConfirmTapMs = 0L
    private var lastOverlayMicSwitchMs = 0L

    private var tabBtnAudio: TextView? = null
    private var tabBtnVideo: TextView? = null
    private var tabBtnNet: TextView? = null
    private var tabPaneAudio: LinearLayout? = null
    private var tabPaneVideo: LinearLayout? = null
    private var tabPaneNet: LinearLayout? = null

    // Top Quick-Action Bar buttons
    private var btnQuickMicMute: TextView? = null
    private var btnQuickGameMute: TextView? = null
    private var btnQuickScene: TextView? = null
    private var btnQuickMarkClip: TextView? = null

    // Tab 1 (Audio) live buttons
    private var btnOvGender: TextView? = null
    private var btnOvVoiceType: TextView? = null
    private var btnOvNsMode: TextView? = null
    private var btnOvNsLevel: TextView? = null
    private var btnOvHpf: TextView? = null
    private var btnOvEq: TextView? = null
    private var btnOvDeEss: TextView? = null
    private var btnOvVelvet: TextView? = null
    private var btnOvAgc: TextView? = null
    private var btnOvMicGainLabel: TextView? = null
    private var btnOvGameGainLabel: TextView? = null
    private var btnOvCinemaWide: TextView? = null
    private var btnOvSubPunch: TextView? = null
    private var btnOvFootstep: TextView? = null
    private var btnOvBlastTamer: TextView? = null
    private var btnOvDucking: TextView? = null
    private var btnOvMicRoute: TextView? = null

    // Tab 2 (Video) live buttons
    private var btnOvSceneSelect: TextView? = null
    private var btnOvUpscaler: TextView? = null
    private var btnOvFraming: TextView? = null
    private var btnOvZoom: TextView? = null
    private var btnOvFilmic: TextView? = null
    private var btnOvVivid: TextView? = null
    private var btnOvShadowBoost: TextView? = null
    private var btnOvColorTemp: TextView? = null
    private var btnOvFlip180: TextView? = null
    private var btnOvSharpen: TextView? = null
    private var btnOvOverlayPng: TextView? = null
    private var btnOvLiveBanner: TextView? = null
    private var btnOvMinimapShield: TextView? = null

    // Tab 3 (Bitrate / Net) live buttons
    private var tvOvBitrateReadout: TextView? = null
    private var btnOvAbrToggle: TextView? = null
    private var btnOvThermal30Saver: TextView? = null
    private var tvOvSimulcastStatus: TextView? = null
    private var btnOvStopConfirm: TextView? = null

    private var thermalListenerObj: Any? = null

    private val healthTickRunnable = object : Runnable {
        override fun run() {
            if (!broadcastActive && !benchmarkOnlyActive) return
            runHealthAndRecoveryTick()
            mainHandler.postDelayed(this, 2000L)
        }
    }

    private val ghostAlphaRunnable = Runnable {
        if (!pillExpanded) {
            pillRootView?.animate()?.alpha(0.38f)?.setDuration(300)?.start()
        }
    }

    /**
     * Per-session primary RTMP callback wrapper: binds every RtmpClient instance to the exact
     * connectionSessionGen that created it, so delayed callbacks from a stopped or replaced
     * RtmpClient can never mutate a newer broadcast session.
     */
    private class PrimaryRtmpCallback(
        private val sessionGen: Long,
        private val service: SolStreamService
    ) : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            service.onPrimaryConnectionStarted(sessionGen)
        }

        override fun onConnectionSuccess() {
            service.onPrimaryConnectionSuccess(sessionGen, this)
        }

        override fun onNewBitrate(bitrate: Long) {
            service.onPrimaryNewBitrate(sessionGen, bitrate)
        }

        override fun onConnectionFailed(reason: String) {
            service.onPrimaryConnectionFailed(sessionGen, reason)
        }

        override fun onDisconnect() {
            service.onPrimaryDisconnect(sessionGen)
        }

        override fun onAuthError() {
            service.onPrimaryAuthError(sessionGen)
        }

        override fun onAuthSuccess() {
            service.onPrimaryAuthSuccess(sessionGen)
        }
    }

    private inner class AuxRtmpTarget(
        val name: String,
        val fullUrl: String,
        val codecKey: String,
        val sessionGen: Long
    ) : ConnectChecker {
        val client = RtmpClient(this)
        @Volatile var connected = false
        @Volatile var stopped = false
        private val reconnecting = AtomicBoolean(false)
        private val sendExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "SolAuxRtmp-$name").apply { isDaemon = true }
        }

        fun startAsync() {
            stopped = false
            sendExecutor.execute {
                if (stopped || !shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get(), stopped)) {
                    return@execute
                }
                try {
                    client.setLogs(false)
                    client.setCheckServerAlive(true)
                    client.setOnlyVideo(false)
                    try { client.resizeCache(90) } catch (_: Throwable) {}
                    val vCodec = when (codecKey) {
                        "HEVC" -> VideoCodec.H265
                        "AV1" -> VideoCodec.AV1
                        else -> VideoCodec.H264
                    }
                    client.setVideoCodec(vCodec)
                    val vPipe = videoPipeline
                    if (vPipe != null) {
                        val isPortrait = name.contains("9:16")
                        val w = if (isPortrait) 1080 else activeSettings.width
                        val h = if (isPortrait) 1920 else activeSettings.height
                        client.setVideoResolution(w, h)
                        client.setFps(activeSettings.fps)
                    }
                    client.setAudioInfo(SolDualAudioEngine.SAMPLE_RATE, true)
                    client.connect(fullUrl)
                } catch (e: Exception) {
                    DiagnosticsLogger.log("AUX_WARN", "$name connect error (${e.javaClass.simpleName}).")
                }
            }
        }

        fun sendVideoHeader(sps: ByteArray, pps: ByteArray?, vps: ByteArray?) {
            if (!connected || stopped) return
            sendExecutor.execute {
                if (!connected || stopped) return@execute
                try {
                    client.setVideoInfo(
                        ByteBuffer.wrap(sps),
                        pps?.let { ByteBuffer.wrap(it) },
                        vps?.let { ByteBuffer.wrap(it) }
                    )
                } catch (_: Exception) {
                }
            }
        }

        fun sendVideoFrame(frameBytes: ByteArray, ptsUs: Long, flags: Int) {
            if (!connected || stopped) return
            sendExecutor.execute {
                if (!connected || stopped) return@execute
                try {
                    val info = MediaCodec.BufferInfo().apply {
                        set(0, frameBytes.size, ptsUs, flags)
                    }
                    client.sendVideo(ByteBuffer.wrap(frameBytes), info)
                } catch (_: Exception) {
                }
            }
        }

        fun sendAudioFrame(aacBytes: ByteArray, ptsUs: Long, flags: Int) {
            if (!connected || stopped) return
            sendExecutor.execute {
                if (!connected || stopped) return@execute
                try {
                    val info = MediaCodec.BufferInfo().apply {
                        set(0, aacBytes.size, ptsUs, flags)
                    }
                    client.sendAudio(ByteBuffer.wrap(aacBytes), info)
                } catch (_: Exception) {
                }
            }
        }

        fun stop() {
            stopped = true
            connected = false
            sendExecutor.execute {
                try { client.disconnect() } catch (_: Exception) {}
            }
            try { sendExecutor.shutdown() } catch (_: Exception) {}
        }

        override fun onConnectionStarted(url: String) {}

        override fun onConnectionSuccess() {
            if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get(), stopped)) {
                sendExecutor.execute { try { client.disconnect() } catch (_: Exception) {} }
                return
            }
            connected = true
            reconnecting.set(false)
            DiagnosticsLogger.log("AUX_LIVE", "$name target connected and live.")
            if (!name.contains("9:16")) {
                val s = cachedSps
                if (s != null) sendVideoHeader(s, cachedPps, cachedVps)
            }
            mainHandler.post { refreshExpandedControlsState() }
        }

        override fun onNewBitrate(bitrate: Long) {}

        override fun onConnectionFailed(reason: String) {
            if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get(), stopped)) {
                return
            }
            connected = false
            DiagnosticsLogger.log("AUX_WARN", "$name target disconnected ($reason); retrying in 4s...")
            mainHandler.post { refreshExpandedControlsState() }
            if (reconnecting.compareAndSet(false, true)) {
                mainHandler.postDelayed({
                    reconnecting.set(false)
                    if (shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get(), stopped)) {
                        startAsync()
                    }
                }, 4000L)
            }
        }

        override fun onDisconnect() {
            connected = false
        }

        override fun onAuthError() {
            if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get(), stopped)) return
            connected = false
            DiagnosticsLogger.log("AUX_AUTH_ERR", "$name stream key rejected by server.")
            mainHandler.post { refreshExpandedControlsState() }
        }

        override fun onAuthSuccess() {}
    }

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLogger.installPrivacySafeCrashHandler(applicationContext)
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureNotificationChannel()
        registerThermalStatusListenerIfSupported()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Zero UI inspection: strictly used for TYPE_ACCESSIBILITY_OVERLAY + concurrent mic policy.
    }

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_STREAM -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startLiveBroadcast(resultCode, resultData)
                } else {
                    DiagnosticsLogger.log("START_ABORT", "Screen capture permission was not granted.")
                }
            }
            ACTION_START_BENCHMARK -> startPassiveCompetitorBenchmark()
            ACTION_STOP_STREAM -> stopLiveBroadcast("Stopped by user")
            ACTION_TOGGLE_MIC_MUTE -> toggleMicMuteLive()
            ACTION_TOGGLE_BRB -> cycleSceneLive()
            ACTION_MARK_CLIP -> saveClipMarkerLive("Notification Clip")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterThermalStatusListenerIfSupported()
        stopLiveBroadcast("Service destroyed")
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun registerThermalStatusListenerIfSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                if (!broadcastActive && !benchmarkOnlyActive) return@OnThermalStatusChangedListener
                if (status > DiagnosticsLogger.worstThermalState) {
                    DiagnosticsLogger.worstThermalState = status
                }
                if (status >= PowerManager.THERMAL_STATUS_LIGHT) {
                    DiagnosticsLogger.log(
                        "THERMAL_EVENT",
                        "Android thermal status changed to ${DiagnosticsLogger.thermalStatusName(status)}."
                    )
                }
            }
            pm.addThermalStatusListener(mainExecutor, listener)
            thermalListenerObj = listener
        } catch (_: Exception) {
        }
    }

    private fun unregisterThermalStatusListenerIfSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val l = thermalListenerObj as? PowerManager.OnThermalStatusChangedListener ?: return
            pm.removeThermalStatusListener(l)
            thermalListenerObj = null
        } catch (_: Exception) {
        }
    }

    private fun startPassiveCompetitorBenchmark() {
        if (broadcastActive || benchmarkOnlyActive) return
        activeSettings = KeystoreVault.loadSettings(this)
        benchmarkOnlyActive = true
        userManuallyOverrodeSharp = false
        userManuallyOverrode30FpsSaver = false
        severeThermalLastSeenElapsedMs = 0L

        val notif = buildStreamNotification("Competitor App Thermal & RAM Benchmark Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        DiagnosticsLogger.startNewSession(
            this,
            "Competitor App Benchmark",
            "Passive Thermal & Memory Profiler (Zero Encoding)"
        )
        acquirePartialWakeLock()
        attachFloatingOverlayPill()
        mainHandler.removeCallbacks(healthTickRunnable)
        mainHandler.post(healthTickRunnable)
        Toast.makeText(
            this,
            "Benchmark active! Open your other stream app & play for 15m, then tap Stop.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun parseIngestHostAndPort(url: String) {
        try {
            val uri = Uri.parse(url)
            primaryIngestHost = uri.host ?: ""
            val p = uri.port
            primaryIngestPort = if (p > 0) p else if (url.startsWith("rtmps://", ignoreCase = true)) 443 else 1935
        } catch (_: Exception) {
            primaryIngestHost = ""
            primaryIngestPort = 443
        }
    }

    private fun startLiveBroadcast(resultCode: Int, resultData: Intent) {
        if (broadcastActive) return
        if (benchmarkOnlyActive) stopLiveBroadcast("Switching to Live Broadcast")

        activeSettings = KeystoreVault.loadSettings(this)
        userManuallyOverrodeSharp = false
        userManuallyOverrode30FpsSaver = false
        severeThermalLastSeenElapsedMs = 0L
        lastThermalReducedBps = -1
        lastRttProbeElapsedMs = 0L

        val shortsKey = KeystoreVault.loadSecondaryKey(this, KeystoreVault.Slot.YOUTUBE_SHORTS_9_16)
        val useShortsKeyAsPrimary = activeSettings.isVertical &&
            activeSettings.platformPreset == "YOUTUBE" &&
            !shortsKey.isNullOrBlank()

        val rawKey = if (useShortsKeyAsPrimary) {
            shortsKey!!
        } else {
            KeystoreVault.loadStreamKey(this)
        }
        val cleanUrl = KeystoreVault.normalizeIngestUrl(activeSettings.rtmpUrl)
        if (rawKey.isNullOrBlank() || cleanUrl.isBlank()) {
            Toast.makeText(this, "Save your YouTube/RTMPS Stream Key in the Vault first.", Toast.LENGTH_LONG).show()
            return
        }

        primaryStreamUrl = KeystoreVault.joinUrlAndStreamKey(cleanUrl, rawKey)
        backupStreamUrl = KeystoreVault.deriveYoutubeBackupUrl(primaryStreamUrl)
        parseIngestHostAndPort(primaryStreamUrl)
        usingBackupUrl = false
        reconnectCount = 0
        reconnectScheduled.set(false)
        val mySessionGen = connectionSessionGen.incrementAndGet()
        cachedSps = null
        cachedPps = null
        cachedVps = null

        val notif = buildStreamNotification("Starting SolApex™ HyperStream broadcast...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var fgMask = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fgMask = fgMask or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIF_ID, notif, fgMask)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            DiagnosticsLogger.log("FGS_ERR", "Foreground service promotion failed (${e.javaClass.simpleName}).")
            Toast.makeText(this, "Screen capture token expired. Please tap Go Live again.", Toast.LENGTH_LONG).show()
            return
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            DiagnosticsLogger.log("PROJ_ERR", "MediaProjection returned null.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        mediaProjection = projection
        acquirePartialWakeLock()

        val originNs = System.nanoTime()
        val originUs = originNs / 1000L

        val primaryCb = PrimaryRtmpCallback(mySessionGen, this)
        val client = RtmpClient(primaryCb).apply {
            setLogs(false)
            setCheckServerAlive(true)
            setOnlyVideo(false)
            try { resizeCache(120) } catch (_: Throwable) {}
        }
        rtmpClient = client

        auxTargets.clear()
        companionTarget = null
        val targetNames = mutableListOf(
            if (useShortsKeyAsPrimary) "YouTube 9:16 Shorts" else "Primary (${if (activeSettings.isVertical) "9:16" else "16:9"})"
        )

        if (activeSettings.simulcastTwitchEnabled) {
            val tk = KeystoreVault.loadSecondaryKey(this, KeystoreVault.Slot.TWITCH)
            if (!tk.isNullOrBlank()) {
                val tUrl = KeystoreVault.joinUrlAndStreamKey(KeystoreVault.URL_TWITCH_RTMPS, tk)
                auxTargets.add(AuxRtmpTarget("Twitch", tUrl, "H264", mySessionGen))
                targetNames.add("Twitch")
            }
        }
        if (activeSettings.simulcastKickEnabled) {
            val kk = KeystoreVault.loadSecondaryKey(this, KeystoreVault.Slot.KICK)
            if (!kk.isNullOrBlank()) {
                val kUrl = KeystoreVault.joinUrlAndStreamKey(KeystoreVault.URL_KICK_RTMPS, kk)
                auxTargets.add(AuxRtmpTarget("Kick", kUrl, "H264", mySessionGen))
                targetNames.add("Kick")
            }
        }

        val wantDualCompanion = activeSettings.dualFormat916Enabled &&
            !activeSettings.isVertical &&
            !shortsKey.isNullOrBlank()
        if (wantDualCompanion) {
            val compUrl = KeystoreVault.joinUrlAndStreamKey(KeystoreVault.URL_YOUTUBE_RTMPS_PRIMARY, shortsKey!!)
            val scan = CodecCapabilityProbe.getBestAvailableScanNonBlocking(this)
            val compCodec = if (scan.hevcHardware != null) "HEVC" else "H264"
            companionTarget = AuxRtmpTarget("YT 9:16 Companion", compUrl, compCodec, mySessionGen)
            targetNames.add("YT 9:16 Companion")
        }
        DiagnosticsLogger.activeSimulcastDesc = targetNames.joinToString(" + ")

        val vPipeline = SolVideoPipeline(this).apply {
            onFatalPipelineError = { errMsg ->
                mainHandler.post {
                    if (broadcastActive) {
                        Toast.makeText(this@SolStreamService, errMsg, Toast.LENGTH_LONG).show()
                        stopLiveBroadcast("Video hardware error")
                    }
                }
            }
            onCompanionPipelineStopped = { reason ->
                mainHandler.post {
                    val comp = companionTarget
                    companionTarget = null
                    comp?.stop()
                    DiagnosticsLogger.log("DUAL_STOP", "9:16 Companion stream stopped ($reason); Primary 16:9 stream continues.")
                    refreshExpandedControlsState()
                }
            }
        }

        val aEngine = SolDualAudioEngine(this).apply {
            onFatalAudioError = { errMsg ->
                mainHandler.post {
                    if (broadcastActive) {
                        Toast.makeText(this@SolStreamService, errMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val videoInfo = vPipeline.startPipeline(
            settings = activeSettings,
            mediaProjection = projection,
            originTimeNs = originNs,
            onProjectionStoppedBySystem = {
                mainHandler.post {
                    if (broadcastActive) {
                        Toast.makeText(
                            this,
                            "Screen capture ended by Android (screen locked or permission revoked).",
                            Toast.LENGTH_LONG
                        ).show()
                        stopLiveBroadcast("Screen capture stopped by system")
                    }
                }
            },
            onVideoConfig = { sps, pps, vps ->
                val spsArr = copyToByteArray(sps)
                val ppsArr = pps?.let { copyToByteArray(it) }
                val vpsArr = vps?.let { copyToByteArray(it) }
                cachedSps = spsArr
                cachedPps = ppsArr
                cachedVps = vpsArr
                client.setVideoInfo(
                    ByteBuffer.wrap(spsArr),
                    ppsArr?.let { ByteBuffer.wrap(it) },
                    vpsArr?.let { ByteBuffer.wrap(it) }
                )
                for (aux in auxTargets) {
                    aux.sendVideoHeader(spsArr, ppsArr, vpsArr)
                }
            },
            onEncodedVideo = { bb, info ->
                if (rtmpConnected) {
                    client.sendVideo(bb, info)
                }
                if (auxTargets.isNotEmpty()) {
                    val dup = bb.duplicate()
                    val bytes = ByteArray(info.size)
                    dup.position(info.offset)
                    dup.limit(info.offset + info.size)
                    dup.get(bytes)
                    for (aux in auxTargets) {
                        aux.sendVideoFrame(bytes, info.presentationTimeUs, info.flags)
                    }
                }
            },
            enableDualCompanion = (companionTarget != null),
            onCompanionConfig = { sps, pps, vps ->
                val spsArr = copyToByteArray(sps)
                val ppsArr = pps?.let { copyToByteArray(it) }
                val vpsArr = vps?.let { copyToByteArray(it) }
                companionTarget?.sendVideoHeader(spsArr, ppsArr, vpsArr)
            },
            onCompanionVideo = { bb, info ->
                val comp = companionTarget ?: return@startPipeline
                if (comp.connected) {
                    val dup = bb.duplicate()
                    val bytes = ByteArray(info.size)
                    dup.position(info.offset)
                    dup.limit(info.offset + info.size)
                    dup.get(bytes)
                    comp.sendVideoFrame(bytes, info.presentationTimeUs, info.flags)
                }
            }
        )

        if (videoInfo == null) {
            Toast.makeText(
                this,
                "Hardware video encoder rejected settings. Try 1080p60 or H.264.",
                Toast.LENGTH_LONG
            ).show()
            cleanupResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        if (!videoInfo.companionActive && companionTarget != null) {
            companionTarget?.stop()
            companionTarget = null
        }

        val mappedVideoCodec = when (videoInfo.codecKey) {
            "HEVC" -> VideoCodec.H265
            "AV1" -> VideoCodec.AV1
            else -> VideoCodec.H264
        }
        client.setVideoCodec(mappedVideoCodec)
        client.setVideoResolution(videoInfo.outWidth, videoInfo.outHeight)
        client.setFps(videoInfo.fps)
        client.setAudioInfo(SolDualAudioEngine.SAMPLE_RATE, true)

        val audioOk = aEngine.startStreamingAudio(
            settings = activeSettings,
            mediaProjection = projection,
            originTimeUs = originUs,
            onStatusChanged = {
                mainHandler.post { refreshExpandedControlsState() }
            },
            onEncodedAac = { aacBuf, aacInfo ->
                if (rtmpConnected) {
                    client.sendAudio(aacBuf, aacInfo)
                }
                val vPts = vPipeline.lastSentVideoPtsUs
                if (vPts > 0L) {
                    DiagnosticsLogger.avDriftMs = (aacInfo.presentationTimeUs - vPts) / 1000L
                }
                if (auxTargets.isNotEmpty() || companionTarget != null) {
                    val dup = aacBuf.duplicate()
                    val bytes = ByteArray(aacInfo.size)
                    dup.position(aacInfo.offset)
                    dup.limit(aacInfo.offset + aacInfo.size)
                    dup.get(bytes)
                    for (aux in auxTargets) {
                        aux.sendAudioFrame(bytes, aacInfo.presentationTimeUs, aacInfo.flags)
                    }
                    companionTarget?.sendAudioFrame(bytes, aacInfo.presentationTimeUs, aacInfo.flags)
                }
            }
        )

        if (!audioOk) {
            Toast.makeText(this, "Microphone initialization failed. Check Mic permission.", Toast.LENGTH_LONG).show()
            vPipeline.stopPipeline()
            cleanupResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        videoPipeline = vPipeline
        audioEngine = aEngine
        broadcastActive = true

        maxConfiguredBitrateBps = (activeSettings.targetBitrateKbps.coerceIn(500, 100_000) * 1000)
        activeBitrateBps = vPipeline.currentBitrateBps
        minAdaptiveBitrateBps = computeDynamicAbrFloorBps(maxConfiguredBitrateBps)
        calmTicksCount = 0
        zeroThroughputStallTicks = 0
        lastDroppedTotal = 0L

        val hdrTag = if (videoInfo.isHdr10) " • 10-Bit HDR10" else ""
        val dualTag = if (videoInfo.companionActive) " + 9:16 Companion" else ""
        val pipeSummary = "${videoInfo.codecKey} (${videoInfo.encoderName}) ${videoInfo.outWidth}x${videoInfo.outHeight}@${videoInfo.fps}fps ${if (videoInfo.isVbr) "VBR" else "CBR"}$hdrTag$dualTag • ${videoInfo.glEngineLabel}"

        DiagnosticsLogger.activeAudioDspDesc =
            "${aEngine.micDsp.voiceGenderProfile}/${aEngine.micDsp.voicePreset} • ${aEngine.micDsp.activeDenoiserSummary} • ${activeSettings.audioBitrateKbps}k AAC"
        DiagnosticsLogger.startNewSession(this, "Live Broadcast", pipeSummary)

        attachFloatingOverlayPill()
        updateStreamNotification("LIVE: ${videoInfo.outWidth}x${videoInfo.outHeight}@${videoInfo.fps} (${videoInfo.codecKey})")

        connectRtmpAsync(primaryStreamUrl, mySessionGen, client)
        for (aux in auxTargets) {
            aux.startAsync()
        }
        companionTarget?.startAsync()

        mainHandler.removeCallbacks(healthTickRunnable)
        mainHandler.postDelayed(healthTickRunnable, 2000L)
    }

    private fun computeDynamicAbrFloorBps(configuredMaxBps: Int): Int {
        return max(400_000, min(6_000_000, (configuredMaxBps * 35) / 100))
    }

    private fun copyToByteArray(src: ByteBuffer): ByteArray {
        val dup = src.duplicate()
        val out = ByteArray(dup.remaining())
        dup.get(out)
        return out
    }

    private fun connectRtmpAsync(url: String, sessionGen: Long, targetClient: RtmpClient) {
        netExecutor.execute {
            if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) return@execute
            try {
                val endpointLabel = if (usingBackupUrl) "Backup RTMPS (?backup=1)" else "Primary RTMPS"
                DiagnosticsLogger.log("NETWORK", "Connecting to $endpointLabel server (TLS Host Verified)...")
                targetClient.connect(url)
            } catch (e: Exception) {
                DiagnosticsLogger.log("NET_ERR", "RTMPS connect exception (${e.javaClass.simpleName}).", e.message ?: "")
                scheduleSilentReconnect("Connect exception", sessionGen)
            }
        }
    }

    private fun scheduleSilentReconnect(reason: String, sessionGen: Long) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) return
        if (!reconnectScheduled.compareAndSet(false, true)) return

        rtmpConnected = false
        reconnectCount++
        DiagnosticsLogger.reconnectAttempts = reconnectCount

        val useBackupNow = (reconnectCount >= 2 && !backupStreamUrl.isNullOrBlank() && !usingBackupUrl)
        if (useBackupNow) {
            usingBackupUrl = true
        }
        val targetUrl = if (usingBackupUrl && !backupStreamUrl.isNullOrBlank()) {
            backupStreamUrl!!
        } else {
            primaryStreamUrl
        }

        val delayMs = when (reconnectCount) {
            1 -> 1500L
            2 -> 3000L
            3 -> 5000L
            else -> 8000L
        }

        val targetDesc = if (usingBackupUrl) "YouTube Backup Server (b.rtmps)" else "Primary Server"
        DiagnosticsLogger.log(
            "RECONNECT",
            "Network hiccup ($reason). Auto-reconnecting (#$reconnectCount) via $targetDesc in ${delayMs / 1000.0}s while keeping game capture alive."
        )
        mainHandler.post { updatePillTelemetryUI() }

        mainHandler.postDelayed({
            reconnectScheduled.set(false)
            if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) {
                return@postDelayed
            }
            val currentClient = rtmpClient ?: return@postDelayed
            netExecutor.execute {
                if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) {
                    return@execute
                }
                try { currentClient.disconnect() } catch (_: Exception) {}
                val sps = cachedSps
                if (sps != null) {
                    currentClient.setVideoInfo(
                        ByteBuffer.wrap(sps),
                        cachedPps?.let { ByteBuffer.wrap(it) },
                        cachedVps?.let { ByteBuffer.wrap(it) }
                    )
                }
                connectRtmpAsync(targetUrl, sessionGen, currentClient)
            }
        }, delayMs)
    }

    private fun stopLiveBroadcast(reason: String) {
        val wasLive = broadcastActive
        val wasBench = benchmarkOnlyActive
        if (!wasLive && !wasBench) return

        connectionSessionGen.incrementAndGet()
        broadcastActive = false
        benchmarkOnlyActive = false
        rtmpConnected = false
        reconnectScheduled.set(false)
        mainHandler.removeCallbacks(healthTickRunnable)

        DiagnosticsLogger.log("SESSION_END", "Stream ended ($reason).")
        val snap = DiagnosticsLogger.finishAndSaveBenchmark(this, isGlipBaseline = wasBench)
        val fullReport = DiagnosticsLogger.buildShareableReport(this)
        KeystoreVault.appendSessionReportToHistory(
            this,
            title = "${snap.label} (${snap.durationMinutes}m • Peak ${String.format(Locale.US, "%.1f°C", snap.peakBatteryTempC)})",
            reportBody = fullReport
        )

        cleanupResources()
        detachFloatingOverlayPill()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Non-blocking resource teardown: immediately marks videoPipeline stopped & unregisters
     * MediaProjection.Callback synchronously (preventing false [CAPTURE_STOP] logs), then
     * performs socket/thread joins on a daemon thread so the main UI thread never blocks.
     */
    private fun cleanupResources() {
        val clientToClose = rtmpClient
        rtmpClient = null

        val auxListToClose = ArrayList(auxTargets)
        auxTargets.clear()

        val compToClose = companionTarget
        companionTarget = null

        val audioToClose = audioEngine
        audioEngine = null

        val videoToClose = videoPipeline
        videoPipeline = null

        val projToClose = mediaProjection
        mediaProjection = null

        val wlToRelease = wakeLock
        wakeLock = null

        // Unregister MediaProjection.Callback immediately before async disconnect/audio teardown!
        videoToClose?.prepareForImmediateStop()

        for (aux in auxListToClose) {
            try { aux.stop() } catch (_: Exception) {}
        }
        try { compToClose?.stop() } catch (_: Exception) {}

        Thread({
            if (clientToClose != null) {
                try { clientToClose.disconnect() } catch (_: Exception) {}
            }
            try { audioToClose?.stopStreamingAudio() } catch (_: Exception) {}
            try { videoToClose?.stopPipeline() } catch (_: Exception) {}
            try { projToClose?.stop() } catch (_: Exception) {}
            try {
                if (wlToRelease?.isHeld == true) wlToRelease.release()
            } catch (_: Exception) {
            }
        }, "SolAsyncStopCleanup").apply {
            isDaemon = true
            start()
        }
    }

    private fun probeIngestTcpRttIfNeeded() {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (primaryIngestHost.isBlank() || nowElapsed - lastRttProbeElapsedMs < 16_000L) return
        lastRttProbeElapsedMs = nowElapsed
        val host = primaryIngestHost
        val port = primaryIngestPort
        rttProbeExecutor.execute {
            if (!broadcastActive || !rtmpConnected) return@execute
            val sock = Socket()
            try {
                val t0 = SystemClock.elapsedRealtime()
                sock.connect(InetSocketAddress(host, port), 2500)
                val rtt = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
                DiagnosticsLogger.lastRttMs = rtt
            } catch (_: Exception) {
            } finally {
                try { sock.close() } catch (_: Exception) {}
            }
        }
    }

    // === 2-SECOND QUEUE-AWARE PING GUARD ABR 2.0 + ZOMBIE-SOCKET + GROUND-REALITY 2-STAGE THERMAL GUARD ===
    private fun runHealthAndRecoveryTick() {
        val (tempC, thermal) = DiagnosticsLogger.sampleDeviceHealth(this)
        val headroom = DiagnosticsLogger.lastThermalHeadroom
        val nowElapsed = SystemClock.elapsedRealtime()

        if (broadcastActive) {
            val client = rtmpClient
            val vPipe = videoPipeline
            if (client != null && vPipe != null) {
                val qFrames = RtmpTelemetryBridge.readCacheItemsCount(client)
                val qCap = RtmpTelemetryBridge.readCacheSizeCapacity(client)
                val qBytes = RtmpTelemetryBridge.readSentQueueBytes(client)
                var rttMs = RtmpTelemetryBridge.readRttMs(client)
                val droppedVideo = RtmpTelemetryBridge.readDroppedVideoFrames(client)
                val sentKbps = RtmpTelemetryBridge.readSentBitrateKbps(client)

                DiagnosticsLogger.lastQueueFrames = qFrames
                DiagnosticsLogger.lastQueueCapacity = qCap
                DiagnosticsLogger.lastQueueBytes = qBytes
                if (rttMs > 0L) {
                    DiagnosticsLogger.lastRttMs = rttMs
                } else {
                    probeIngestTcpRttIfNeeded()
                    rttMs = DiagnosticsLogger.lastRttMs
                }
                DiagnosticsLogger.droppedVideoFrames = droppedVideo.toInt()

                val droppedDelta = max(0L, droppedVideo - lastDroppedTotal)
                lastDroppedTotal = droppedVideo

                // Zombie-Socket Watchdog: if socket claims connected, VideoPipeline is producing frames,
                // but outbound sent throughput is 0 kbps with a backed-up queue for 3 consecutive ticks (6s),
                // force an immediate clean reconnect!
                if (rtmpConnected && vPipe.actualEncodedFps >= 15) {
                    val isBackedUpAndStalled = (sentKbps in 0..5) &&
                        ((qFrames >= 30) || (qBytes > 1_200_000L) || (droppedDelta >= 12L))
                    if (isBackedUpAndStalled) {
                        zeroThroughputStallTicks++
                        if (zeroThroughputStallTicks >= 3) {
                            zeroThroughputStallTicks = 0
                            DiagnosticsLogger.log(
                                "ZOMBIE_SOCKET",
                                "Detected silent Wi-Fi/router stall (0 kbps outbound for 6s with queued frames); forcing clean RTMPS reconnect.",
                                "qFrames=$qFrames qKB=${qBytes / 1024} dropsDelta=$droppedDelta"
                            )
                            scheduleSilentReconnect("Silent socket stall (0 kbps for 6s)", connectionSessionGen.get())
                        }
                    } else {
                        zeroThroughputStallTicks = 0
                    }
                } else {
                    zeroThroughputStallTicks = 0
                }

                // === GROUND-REALITY 2-STAGE THERMAL ARMOR (44.0°C / 90% SoC Load + Proportional 30-FPS Bitrate) ===
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Stage 1: Moderate Warmth High-Load Relief (>= 44.0°C OR >= 90% SoC Headroom)
                    val needModerateRelief = thermal >= PowerManager.THERMAL_STATUS_MODERATE &&
                        (tempC >= 44.0f || headroom >= 0.90f)
                    val cooledFromModerateRelief = thermal < PowerManager.THERMAL_STATUS_MODERATE ||
                        (tempC <= 43.2f && (headroom < 0f || headroom < 0.84f))

                    if (needModerateRelief && !vPipe.thermalModerateCoolerActive) {
                        vPipe.thermalModerateCoolerActive = true
                    } else if (cooledFromModerateRelief && vPipe.thermalModerateCoolerActive) {
                        vPipe.thermalModerateCoolerActive = false
                    }

                    // Stage 2: Severe Thermal Cooldown (Steps to 30 FPS + Proportional -40% Bitrate so QP never hits 5-10!)
                    if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
                        severeThermalLastSeenElapsedMs = nowElapsed
                        if (companionTarget != null && !vPipe.companionPausedByThermal) {
                            vPipe.companionPausedByThermal = true
                            DiagnosticsLogger.log(
                                "THERMAL",
                                "Phone reached Severe warmth; paused secondary 9:16 Companion encode first to protect Primary 16:9 stream."
                            )
                        }
                        if (!userManuallyOverrodeSharp && vPipe.liveGpuSharpen) {
                            vPipe.liveGpuSharpen = false
                            activeSettings = activeSettings.copy(gpuSharpen = false)
                        }
                        if (!userManuallyOverrode30FpsSaver && !vPipe.thermalThrottle30Fps && activeSettings.fps > 30) {
                            vPipe.thermalThrottle30Fps = true
                            val propMbps = String.format(Locale.US, "%.1f", (maxConfiguredBitrateBps * 0.60) / 1_000_000.0)
                            DiagnosticsLogger.log(
                                "THERMAL",
                                "Phone reached Severe warmth; stepped stream to 30 FPS & ${propMbps} Mbps (-40% encoder heat) so CODM gunfire never stutters."
                            )
                        }
                    } else if (thermal <= PowerManager.THERMAL_STATUS_MODERATE) {
                        // Reset manual 30 FPS test toggle automatically once phone cools down to Light Warmth or <= 43.4°C
                        if (userManuallyOverrode30FpsSaver &&
                            (thermal <= PowerManager.THERMAL_STATUS_LIGHT || tempC <= 43.4f)
                        ) {
                            userManuallyOverrode30FpsSaver = false
                        }
                        val cooldownSatisfied = (nowElapsed - severeThermalLastSeenElapsedMs >= 45_000L) ||
                            (tempC in 1.0f..43.8f && (headroom < 0f || headroom <= 0.85f))
                        if (!userManuallyOverrode30FpsSaver && vPipe.thermalThrottle30Fps && cooldownSatisfied) {
                            vPipe.thermalThrottle30Fps = false
                            DiagnosticsLogger.log(
                                "THERMAL",
                                "SoC cooled safely; restored full 60 FPS broadcast cadence & 100% target bitrate."
                            )
                        }
                        if (vPipe.companionPausedByThermal && thermal <= PowerManager.THERMAL_STATUS_LIGHT) {
                            vPipe.companionPausedByThermal = false
                        }
                    }
                }

                // Compute Thermal Ceiling for Encoder Bitrate:
                // - When 30 FPS Cooldown is active (auto or manual on a 60 FPS stream): 60% of maxConfiguredBitrateBps
                //   (gives identical bits-per-frame as 60 FPS without forcing QP 5-10 CABAC heat!)
                // - When Stage 1 Moderate Relief (>=44.0°C / >=90% SoC) is active at 60 FPS: 85% of maxConfiguredBitrateBps
                // - Otherwise: 100% of maxConfiguredBitrateBps
                val thermalCeilingBps = when {
                    vPipe.thermalThrottle30Fps && activeSettings.fps > 30 ->
                        max(minAdaptiveBitrateBps, (maxConfiguredBitrateBps * 60) / 100)
                    vPipe.thermalModerateCoolerActive ->
                        max(minAdaptiveBitrateBps, (maxConfiguredBitrateBps * 85) / 100)
                    else -> maxConfiguredBitrateBps
                }

                if (thermalCeilingBps < activeBitrateBps) {
                    activeBitrateBps = thermalCeilingBps
                    lastThermalReducedBps = thermalCeilingBps
                    vPipe.setBitrateOnFly(activeBitrateBps)
                } else if (lastThermalReducedBps > 0 && thermalCeilingBps > activeBitrateBps && droppedDelta == 0L) {
                    activeBitrateBps = min(thermalCeilingBps, maxConfiguredBitrateBps)
                    if (activeBitrateBps >= maxConfiguredBitrateBps) {
                        lastThermalReducedBps = -1
                    }
                    vPipe.setBitrateOnFly(activeBitrateBps)
                }

                // Queue-Aware Ping Guard ABR 2.0
                if (activeSettings.adaptiveBitrateEnabled && rtmpConnected) {
                    val queueCongested = (qCap > 0 && qFrames > (qCap * 35) / 100) ||
                        (qFrames > 35) ||
                        (qBytes > 1_600_000L) ||
                        (rttMs > 260L) ||
                        (droppedDelta > 2L)

                    val queueCalm = isQueueCalmForAbrRecovery(
                        qFrames = qFrames,
                        qBytes = qBytes,
                        droppedDelta = droppedDelta.toInt()
                    ) && (rttMs <= 0L || rttMs < 130L)

                    if (queueCongested) {
                        calmTicksCount = 0
                        val reduced = max(minAdaptiveBitrateBps, (activeBitrateBps * 82) / 100)
                        if (reduced < activeBitrateBps) {
                            activeBitrateBps = reduced
                            vPipe.setBitrateOnFly(activeBitrateBps)
                            if (qFrames > 55 || droppedDelta > 8L) {
                                try { client.clearCache() } catch (_: Throwable) {}
                                vPipe.requestSyncFrame()
                            }
                            DiagnosticsLogger.log(
                                "PING_GUARD_ABR",
                                "Trimmed stream bitrate to ${String.format(Locale.US, "%.1f", activeBitrateBps / 1_000_000.0)} Mbps so CODM ping stays flat.",
                                "qFrames=$qFrames/$qCap qKB=${qBytes / 1024} rtt=${rttMs}ms dropsDelta=$droppedDelta"
                            )
                        }
                    } else if (queueCalm && activeBitrateBps < thermalCeilingBps) {
                        calmTicksCount++
                        if (calmTicksCount >= 3) {
                            calmTicksCount = 0
                            val recovered = min(thermalCeilingBps, activeBitrateBps + 1_500_000)
                            if (recovered > activeBitrateBps) {
                                activeBitrateBps = recovered
                                vPipe.setBitrateOnFly(activeBitrateBps)
                            }
                        }
                    }
                }
            }
        }

        updatePillTelemetryUI()
    }

    // === 3-TAB FLOATING IN-GAME PILL (0 Hz Idle Redraws + Multi-Touch Split Flags) ===
    @SuppressLint("ClickableViewAccessibility")
    private fun attachFloatingOverlayPill() {
        if (!isAccessibilityEnabled(this) || pillRootView != null) return
        val wm = windowManager ?: return

        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).roundToInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60B101B"))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#3338BDF8"))
            }
            setPadding(dp(10), dp(6), dp(10), dp(6))
            alpha = 0.92f
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = TextView(this).apply {
            text = "●"
            setTextColor(Color.parseColor("#10B981"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, dp(5), 0)
        }
        val statusTv = TextView(this).apply {
            text = "SOL • Starting..."
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        header.addView(dot)
        header.addView(statusTv)
        root.addView(header)

        val drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(6), 0, dp(2))
        }

        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        btnQuickMicMute = makePillMiniBtn("Mic:ON", "#10B981") { toggleMicMuteLive() }
        btnQuickGameMute = makePillMiniBtn("Game:ON", "#38BDF8") { toggleGameAudioMuteLive() }
        btnQuickScene = makePillMiniBtn("Scene:LIVE", "#A78BFA") { cycleSceneLive() }
        btnQuickMarkClip = makePillMiniBtn("★ Clip", "#F59E0B") { saveClipMarkerLive("In-Game Clutch Marker") }
        val btnCollapse = makePillMiniBtn("✕", "#94A3B8") { togglePillDrawer(false) }

        quickRow.addView(btnQuickMicMute)
        quickRow.addView(btnQuickGameMute)
        quickRow.addView(btnQuickScene)
        quickRow.addView(btnQuickMarkClip)
        quickRow.addView(btnCollapse)
        drawer.addView(quickRow)

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(4))
        }
        tabBtnAudio = makePillTabHeader("🎙️ Audio", true) { switchOverlayTab(0) }
        tabBtnVideo = makePillTabHeader("🎬 Video", false) { switchOverlayTab(1) }
        tabBtnNet = makePillTabHeader("📡 Bitrate/Net", false) { switchOverlayTab(2) }
        tabBar.addView(tabBtnAudio, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabBar.addView(tabBtnVideo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabBar.addView(tabBtnNet, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        drawer.addView(tabBar)

        val vScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        val panesHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // === TAB 0: 🎙️ AUDIO STUDIO + SOUNDSTAGE ===
        val audioPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
        }
        val aRow1 = makeHScrollRow()
        btnOvGender = makePillMiniBtn("Profile", "#38BDF8") { cycleVoiceGenderLive() }
        btnOvVoiceType = makePillMiniBtn("Preset", "#A78BFA") { cycleVoicePresetLive() }
        btnOvNsMode = makePillMiniBtn("NS Mode", "#10B981") { cycleNsModeLive() }
        btnOvNsLevel = makePillMiniBtn("NS Lv", "#10B981") { cycleRnnoiseLive() }
        aRow1.second.addView(btnOvGender)
        aRow1.second.addView(btnOvVoiceType)
        aRow1.second.addView(btnOvNsMode)
        aRow1.second.addView(btnOvNsLevel)
        audioPane.addView(aRow1.first)

        val aRow2 = makeHScrollRow()
        btnOvHpf = makePillMiniBtn("HPF", "#38BDF8") { cycleHpfLive() }
        btnOvEq = makePillMiniBtn("EQ", "#38BDF8") { toggleVoiceEqLive() }
        btnOvDeEss = makePillMiniBtn("DeEss", "#38BDF8") { toggleDeEsserLive() }
        btnOvVelvet = makePillMiniBtn("Velvet", "#F59E0B") { toggleVelvetLive() }
        btnOvAgc = makePillMiniBtn("SlowAGC", "#10B981") { toggleSlowAgcLive() }
        aRow2.second.addView(btnOvHpf)
        aRow2.second.addView(btnOvEq)
        aRow2.second.addView(btnOvDeEss)
        aRow2.second.addView(btnOvVelvet)
        aRow2.second.addView(btnOvAgc)
        audioPane.addView(aRow2.first)

        val aRow3 = makeHScrollRow()
        val btnMicDown = makePillMiniBtn("Mic −", "#94A3B8") { stepMicBoostLive(-25) }
        btnOvMicGainLabel = makePillMiniBtn("Mic:125%", "#E2E8F0") { stepMicBoostLive(25) }
        val btnMicUp = makePillMiniBtn("Mic +", "#94A3B8") { stepMicBoostLive(25) }
        val btnGameDown = makePillMiniBtn("Game −", "#94A3B8") { stepGameMixLive(-25) }
        btnOvGameGainLabel = makePillMiniBtn("Game:100%", "#E2E8F0") { stepGameMixLive(25) }
        val btnGameUp = makePillMiniBtn("Game +", "#94A3B8") { stepGameMixLive(25) }
        aRow3.second.addView(btnMicDown)
        aRow3.second.addView(btnOvMicGainLabel)
        aRow3.second.addView(btnMicUp)
        aRow3.second.addView(btnGameDown)
        aRow3.second.addView(btnOvGameGainLabel)
        aRow3.second.addView(btnGameUp)
        audioPane.addView(aRow3.first)

        val aRow4 = makeHScrollRow()
        btnOvCinemaWide = makePillMiniBtn("3D Wide", "#38BDF8") { toggleGameCinemaWideLive() }
        btnOvSubPunch = makePillMiniBtn("SubBass", "#F59E0B") { toggleGameSubPunchLive() }
        btnOvFootstep = makePillMiniBtn("Footsteps", "#10B981") { toggleGameFootstepLive() }
        btnOvBlastTamer = makePillMiniBtn("BlastTame", "#A78BFA") { toggleGameBlastTamerLive() }
        btnOvDucking = makePillMiniBtn("Duck:OFF", "#38BDF8") { cycleDuckingLive() }
        btnOvMicRoute = makePillMiniBtn("Mic:Auto", "#E2E8F0") { cycleMicHardwareLive() }
        aRow4.second.addView(btnOvCinemaWide)
        aRow4.second.addView(btnOvSubPunch)
        aRow4.second.addView(btnOvFootstep)
        aRow4.second.addView(btnOvBlastTamer)
        aRow4.second.addView(btnOvDucking)
        aRow4.second.addView(btnOvMicRoute)
        audioPane.addView(aRow4.first)

        // === TAB 1: 🎬 VIDEO & SCENES (Includes STRETCH Zero-Bars!) ===
        val videoPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val vRow1 = makeHScrollRow()
        btnOvSceneSelect = makePillMiniBtn("Scene:LIVE", "#10B981") { cycleSceneLive() }
        btnOvUpscaler = makePillMiniBtn("Scale:BICUBIC", "#38BDF8") { cycleUpscalerLive() }
        btnOvFraming = makePillMiniBtn("Frame:FIT", "#38BDF8") { cycleFramingModeLive() }
        btnOvZoom = makePillMiniBtn("Zoom:108%", "#94A3B8") { cycleSmartZoomLive() }
        vRow1.second.addView(btnOvSceneSelect)
        vRow1.second.addView(btnOvUpscaler)
        vRow1.second.addView(btnOvFraming)
        vRow1.second.addView(btnOvZoom)
        videoPane.addView(vRow1.first)

        val vRow2 = makeHScrollRow()
        btnOvFilmic = makePillMiniBtn("Filmic", "#A78BFA") { toggleCinematicFilmicLive() }
        btnOvVivid = makePillMiniBtn("Vivid", "#F59E0B") { toggleAmoledVividLive() }
        btnOvShadowBoost = makePillMiniBtn("Shadow+", "#10B981") { toggleShadowBoostLive() }
        btnOvColorTemp = makePillMiniBtn("Temp:NEUT", "#38BDF8") { cycleColorTempLive() }
        vRow2.second.addView(btnOvFilmic)
        vRow2.second.addView(btnOvVivid)
        vRow2.second.addView(btnOvShadowBoost)
        vRow2.second.addView(btnOvColorTemp)
        videoPane.addView(vRow2.first)

        val vRow3 = makeHScrollRow()
        btnOvFlip180 = makePillMiniBtn("Rot:0°", "#38BDF8") { toggleLandscapeFlipLive() }
        btnOvSharpen = makePillMiniBtn("Sharp:OFF", "#38BDF8") { toggleGpuSharpenLive() }
        btnOvOverlayPng = makePillMiniBtn(".PNG:OFF", "#10B981") { toggleOverlayPngLive() }
        btnOvLiveBanner = makePillMiniBtn("Banner:OFF", "#F59E0B") { toggleLiveBannerLive() }
        btnOvMinimapShield = makePillMiniBtn("MapBlur:OFF", "#A78BFA") { toggleMinimapShieldLive() }
        vRow3.second.addView(btnOvFlip180)
        vRow3.second.addView(btnOvSharpen)
        vRow3.second.addView(btnOvOverlayPng)
        vRow3.second.addView(btnOvLiveBanner)
        vRow3.second.addView(btnOvMinimapShield)
        videoPane.addView(vRow3.first)

        // === TAB 2: 📡 BITRATE / NET / THERMAL SAVER ===
        val netPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val nRow1 = makeHScrollRow()
        val btnBitDown = makePillMiniBtn("− Mbps", "#38BDF8") { stepLiveBitrate(-1) }
        tvOvBitrateReadout = makePillMiniBtn("20.0 Mbps", "#F8FAFC") {}
        val btnBitUp = makePillMiniBtn("+ Mbps", "#38BDF8") { stepLiveBitrate(1) }
        btnOvAbrToggle = makePillMiniBtn("ABR:ON", "#10B981") { toggleAbrLive() }
        nRow1.second.addView(btnBitDown)
        nRow1.second.addView(tvOvBitrateReadout)
        nRow1.second.addView(btnBitUp)
        nRow1.second.addView(btnOvAbrToggle)
        netPane.addView(nRow1.first)

        val nRow2 = makeHScrollRow()
        btnOvThermal30Saver = makePillMiniBtn("30FPS Cool:AUTO", "#10B981") { toggleThermal30FpsSaverLive() }
        tvOvSimulcastStatus = makePillMiniBtn("Targets: Primary", "#94A3B8") {}
        btnOvStopConfirm = makePillMiniBtn("■ Stop Stream", "#EF4444") { onStopButtonTappedInOverlay() }
        nRow2.second.addView(btnOvThermal30Saver)
        nRow2.second.addView(tvOvSimulcastStatus)
        nRow2.second.addView(btnOvStopConfirm)
        netPane.addView(nRow2.first)

        tabPaneAudio = audioPane
        tabPaneVideo = videoPane
        tabPaneNet = netPane

        panesHolder.addView(audioPane)
        panesHolder.addView(videoPane)
        panesHolder.addView(netPane)
        vScroll.addView(panesHolder)
        drawer.addView(vScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(116)))

        root.addView(drawer)

        val savedPos = KeystoreVault.loadOverlayPosition(this)
        // FLAG_NOT_TOUCH_MODAL + FLAG_SPLIT_TOUCH guarantees 0-ms multi-touch independence for CODM!
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedPos.first
            y = savedPos.second
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startWinX = 0
        var startWinY = 0
        var dragging = false
        var lastTapUpMs = 0L

        header.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mainHandler.removeCallbacks(ghostAlphaRunnable)
                    root.alpha = 0.96f
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startWinX = lp.x
                    startWinY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downRawX).roundToInt()
                    val dy = (ev.rawY - downRawY).roundToInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        dragging = true
                        lp.x = startWinX + dx
                        lp.y = startWinY + dy
                        try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        KeystoreVault.saveOverlayPosition(this, lp.x, lp.y)
                        scheduleGhostFade()
                    } else {
                        val now = SystemClock.uptimeMillis()
                        val holdMs = now - ev.downTime
                        if (now - lastTapUpMs < 360L || holdMs >= 420L) {
                            togglePillDrawer(!pillExpanded)
                            lastTapUpMs = 0L
                        } else {
                            lastTapUpMs = now
                            scheduleGhostFade()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(root, lp)
            pillRootView = root
            pillHeaderRow = header
            pillStatusDot = dot
            pillStatusText = statusTv
            pillDrawerContainer = drawer
            pillLayoutParams = lp
            refreshExpandedControlsState()
            updatePillTelemetryUI()
            scheduleGhostFade()
        } catch (e: Exception) {
            DiagnosticsLogger.log("OVERLAY_ERR", "Could not attach floating pill (${e.javaClass.simpleName}).")
        }
    }

    private fun makeHScrollRow(): Pair<HorizontalScrollView, LinearLayout> {
        val d = resources.displayMetrics.density
        val hsv = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, (2 * d).roundToInt(), 0, (2 * d).roundToInt())
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        hsv.addView(row)
        return hsv to row
    }

    private fun makePillTabHeader(title: String, selected: Boolean, onClick: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).roundToInt()
        return TextView(this).apply {
            text = title
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setTextColor(if (selected) Color.parseColor("#08101D") else Color.parseColor("#CBD5E1"))
            background = GradientDrawable().apply {
                setColor(if (selected) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B"))
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener {
                onClick()
                scheduleGhostFade()
            }
        }
    }

    private fun makePillMiniBtn(label: String, hexColor: String, onClick: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).roundToInt()
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(hexColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#192234"))
                cornerRadius = dp(8).toFloat()
                setStroke(1, Color.parseColor("#33FFFFFF"))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dp(4), 0)
            }
            layoutParams = lp
            setOnClickListener {
                onClick()
                scheduleGhostFade()
            }
        }
    }

    private fun switchOverlayTab(tabIdx: Int) {
        activePillTab = tabIdx.coerceIn(0, 2)
        tabPaneAudio?.visibility = if (activePillTab == 0) View.VISIBLE else View.GONE
        tabPaneVideo?.visibility = if (activePillTab == 1) View.VISIBLE else View.GONE
        tabPaneNet?.visibility = if (activePillTab == 2) View.VISIBLE else View.GONE

        val tabs = listOf(tabBtnAudio, tabBtnVideo, tabBtnNet)
        for (i in tabs.indices) {
            val tv = tabs[i] ?: continue
            val sel = (i == activePillTab)
            tv.setTextColor(if (sel) Color.parseColor("#08101D") else Color.parseColor("#CBD5E1"))
            (tv.background as? GradientDrawable)?.setColor(
                if (sel) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B")
            )
        }
    }

    private fun togglePillDrawer(expand: Boolean) {
        pillExpanded = expand
        pillDrawerContainer?.visibility = if (expand) View.VISIBLE else View.GONE
        if (expand) {
            mainHandler.removeCallbacks(ghostAlphaRunnable)
            pillRootView?.alpha = 0.97f
            refreshExpandedControlsState()
        } else {
            scheduleGhostFade()
        }
    }

    private fun scheduleGhostFade() {
        mainHandler.removeCallbacks(ghostAlphaRunnable)
        if (!pillExpanded) {
            mainHandler.postDelayed(ghostAlphaRunnable, 3200L)
        }
    }

    private fun detachFloatingOverlayPill() {
        mainHandler.removeCallbacks(ghostAlphaRunnable)
        val view = pillRootView ?: return
        pillRootView = null
        try { windowManager?.removeView(view) } catch (_: Exception) {}
    }

    private fun updatePillTelemetryUI() {
        val statusTv = pillStatusText ?: return
        val dot = pillStatusDot ?: return

        val elapsedSec = if (DiagnosticsLogger.sessionStartMs > 0L) {
            max(0L, (System.currentTimeMillis() - DiagnosticsLogger.sessionStartMs) / 1000L)
        } else 0L
        val mm = elapsedSec / 60L
        val ss = elapsedSec % 60L
        val timeStr = String.format(Locale.US, "%02d:%02d", mm, ss)
        val tempStr = String.format(Locale.US, "%.1f°C", DiagnosticsLogger.peakTempC.coerceAtLeast(DiagnosticsLogger.startTempC))
        val socPct = if (DiagnosticsLogger.lastThermalHeadroom > 0f) {
            " (SoC:${(DiagnosticsLogger.lastThermalHeadroom * 100).roundToInt()}%)"
        } else ""

        if (benchmarkOnlyActive) {
            dot.setTextColor(Color.parseColor("#F59E0B"))
            statusTv.text = "BENCH $timeStr | $tempStr$socPct | PSS ${DiagnosticsLogger.peakTotalPssMb}MB"
            return
        }

        val vPipe = videoPipeline
        val mbps = (vPipe?.actualEncodedBitrateBps ?: 0) / 1_000_000.0
        val outFps = vPipe?.actualEncodedFps ?: 0
        val freshFps = vPipe?.actualFreshCaptureFps ?: 0
        val qFrames = DiagnosticsLogger.lastQueueFrames
        val qStr = formatOverlayQueueTag(qFrames)
        val sceneTag = if (vPipe != null && vPipe.activeSceneMode != "LIVE") " [${vPipe.activeSceneMode}]" else ""

        val dotColor = when {
            !rtmpConnected -> "#F59E0B"
            DiagnosticsLogger.worstThermalState >= PowerManager.THERMAL_STATUS_SEVERE -> "#EF4444"
            else -> "#10B981"
        }
        dot.setTextColor(Color.parseColor(dotColor))

        val netState = if (rtmpConnected) {
            String.format(Locale.US, "%.1fM", mbps)
        } else {
            "RECONN"
        }
        statusTv.text = "$timeStr | $netState | ${freshFps}/${outFps}fps | $qStr | $tempStr$socPct$sceneTag"

        if (pillExpanded) {
            refreshExpandedControlsState()
        }
    }

    private fun refreshExpandedControlsState() {
        val aEng = audioEngine
        val vPipe = videoPipeline

        val micMuted = aEng?.micDsp?.streamMicMuted ?: false
        btnQuickMicMute?.text = if (micMuted) "Mic:MUTE" else "Mic:ON"
        btnQuickMicMute?.setTextColor(Color.parseColor(if (micMuted) "#EF4444" else "#10B981"))

        val gameMuted = aEng?.gameStreamMuted ?: false
        btnQuickGameMute?.text = if (gameMuted) "Game:MUTE" else "Game:ON"
        btnQuickGameMute?.setTextColor(Color.parseColor(if (gameMuted) "#EF4444" else "#38BDF8"))

        val scene = vPipe?.activeSceneMode ?: "LIVE"
        val sceneShort = when (scene) {
            "STARTING_SOON" -> "Scene:START"
            "BRB" -> "Scene:BRB"
            else -> "Scene:LIVE"
        }
        btnQuickScene?.text = sceneShort
        btnQuickScene?.setTextColor(Color.parseColor(if (scene == "LIVE") "#10B981" else "#F59E0B"))
        btnOvSceneSelect?.text = sceneShort
        btnOvSceneSelect?.setTextColor(Color.parseColor(if (scene == "LIVE") "#10B981" else "#F59E0B"))

        val clips = DiagnosticsLogger.getClipHighlightCount()
        btnQuickMarkClip?.text = if (clips > 0) "★ Clip($clips)" else "★ Clip"

        // Tab 0 (Audio)
        btnOvGender?.text = "Voice:${activeSettings.voiceGenderProfile}"
        btnOvVoiceType?.text = "Type:${activeSettings.voicePreset}"
        btnOvNsMode?.text = "NS:${activeSettings.noiseSuppressMode}"
        btnOvNsLevel?.text = "Lv:${activeSettings.rnnoiseLevel}"
        btnOvHpf?.text = if (activeSettings.hpfCutoffHz == 0) "HPF:OFF" else "HPF:${activeSettings.hpfCutoffHz}"
        btnOvEq?.text = if (activeSettings.voiceEqEnabled) "EQ:ON" else "EQ:OFF"
        btnOvDeEss?.text = if (activeSettings.deEsserEnabled) "DeEss:ON" else "DeEss:OFF"
        btnOvVelvet?.text = if (activeSettings.velvetWarmthEnabled) "Velvet:ON" else "Velvet:OFF"
        btnOvAgc?.text = if (activeSettings.slowAgcEnabled) "AGC:ON" else "AGC:OFF"
        btnOvMicGainLabel?.text = "Mic:${activeSettings.micGainPercent}%"
        btnOvGameGainLabel?.text = "Game:${activeSettings.gameGainPercent}%"
        btnOvCinemaWide?.text = if (activeSettings.gameCinemaWideEnabled) "3DWide:ON" else "3DWide:OFF"
        btnOvSubPunch?.text = if (activeSettings.gameSubPunchEnabled) "Sub:ON" else "Sub:OFF"
        btnOvFootstep?.text = if (activeSettings.gameFootstepFocusEnabled) "Foot:ON" else "Foot:OFF"
        btnOvBlastTamer?.text = if (activeSettings.gameBlastTamerEnabled) "Blast:ON" else "Blast:OFF"
        btnOvDucking?.text = if (activeSettings.vadDuckingPercent == 0) "Duck:OFF" else "Duck:-${activeSettings.vadDuckingPercent}%"
        btnOvMicRoute?.text = "In:${aEng?.routedMicLabel?.take(12) ?: "Auto"}"

        // Tab 1 (Video)
        btnOvUpscaler?.text = "Scale:${activeSettings.upscalerMode}"
        btnOvFraming?.text = "Frame:${activeSettings.framingMode}"
        btnOvZoom?.text = "Zoom:${activeSettings.smartZoomPercent}%"
        btnOvFilmic?.text = if (activeSettings.cinematicFilmic) "Filmic:ON" else "Filmic:OFF"
        btnOvVivid?.text = if (activeSettings.amoledVivid) "Vivid:ON" else "Vivid:OFF"
        btnOvShadowBoost?.text = if (activeSettings.shadowBoost) "Shadow+:ON" else "Shadow+:OFF"
        btnOvColorTemp?.text = "Temp:${activeSettings.colorTempFilter.take(4)}"
        btnOvFlip180?.text = "Rot:${activeSettings.landscapeFlip}°"
        btnOvSharpen?.text = if (activeSettings.gpuSharpen) "Sharp:ON" else "Sharp:OFF"
        btnOvOverlayPng?.text = if (activeSettings.overlayImageEnabled) ".PNG:ON" else ".PNG:OFF"
        btnOvLiveBanner?.text = if (activeSettings.liveBannerEnabled) "Banner:ON" else "Banner:OFF"
        btnOvMinimapShield?.text = if (activeSettings.minimapShieldEnabled) "MapBlur:${activeSettings.minimapShieldCorner.take(5)}" else "MapBlur:OFF"

        // Tab 2 (Net / Bitrate)
        tvOvBitrateReadout?.text = String.format(Locale.US, "%.1f Mbps", activeBitrateBps / 1_000_000.0)
        btnOvAbrToggle?.text = if (activeSettings.adaptiveBitrateEnabled) "ABR:ON" else "ABR:OFF"
        val saver30 = vPipe?.thermalThrottle30Fps == true
        btnOvThermal30Saver?.text = if (saver30) "30FPS Cool:ACTIVE" else "30FPS Cool:AUTO"
        btnOvThermal30Saver?.setTextColor(Color.parseColor(if (saver30) "#F59E0B" else "#10B981"))

        val activeAuxCount = auxTargets.count { it.connected } + (if (companionTarget?.connected == true) 1 else 0)
        tvOvSimulcastStatus?.text = if (activeAuxCount > 0) {
            "Targets: Primary + $activeAuxCount Aux"
        } else {
            "Targets: Primary Only"
        }
    }

    // === LIVE IN-GAME ACTION HANDLERS ===
    private fun saveAndPushSettings(updated: StreamSettings) {
        activeSettings = updated
        KeystoreVault.saveSettings(this, updated)
        audioEngine?.updateLiveSettings(updated)
        videoPipeline?.applyLiveVisualSettings(updated)
        audioEngine?.let {
            DiagnosticsLogger.activeAudioDspDesc =
                "${it.micDsp.voiceGenderProfile}/${it.micDsp.voicePreset} • ${it.micDsp.activeDenoiserSummary} • ${updated.audioBitrateKbps}k AAC"
        }
        refreshExpandedControlsState()
    }

    private fun toggleMicMuteLive() {
        val dsp = audioEngine?.micDsp ?: return
        dsp.streamMicMuted = !dsp.streamMicMuted
        refreshExpandedControlsState()
        updateStreamNotification(if (dsp.streamMicMuted) "LIVE (Stream Mic Muted)" else "LIVE Broadcast Active")
    }

    private fun toggleGameAudioMuteLive() {
        val aEng = audioEngine ?: return
        aEng.gameStreamMuted = !aEng.gameStreamMuted
        refreshExpandedControlsState()
    }

    private fun cycleSceneLive() {
        val vPipe = videoPipeline ?: return
        val next = when (vPipe.activeSceneMode) {
            "LIVE" -> "BRB"
            "BRB" -> "STARTING_SOON"
            else -> "LIVE"
        }
        vPipe.setSceneMode(next)
        if (next != "LIVE") {
            vPipe.setBitrateOnFly(min(activeBitrateBps, 1_500_000))
        } else {
            vPipe.setBitrateOnFly(activeBitrateBps)
        }
        refreshExpandedControlsState()
        updatePillTelemetryUI()
    }

    private fun saveClipMarkerLive(label: String) {
        val elapsedSec = if (DiagnosticsLogger.sessionStartMs > 0L) {
            max(0L, (System.currentTimeMillis() - DiagnosticsLogger.sessionStartMs) / 1000L)
        } else 0L
        val stamp = DiagnosticsLogger.markClipHighlight(elapsedSec, label)
        Toast.makeText(this, "★ Saved Clip Highlight @ $stamp in Session Report!", Toast.LENGTH_SHORT).show()
        refreshExpandedControlsState()
    }

    private fun cycleVoiceGenderLive() {
        val order = MicDspProcessor.GENDER_PROFILE_ORDER
        val next = order[(order.indexOf(activeSettings.voiceGenderProfile) + 1) % order.size]
        val defaultHpf = when (next) {
            "MALE" -> 80
            "FEMALE" -> 100
            else -> 85
        }
        saveAndPushSettings(activeSettings.copy(voiceGenderProfile = next, hpfCutoffHz = defaultHpf))
    }

    private fun cycleVoicePresetLive() {
        val order = MicDspProcessor.VOICE_PRESET_ORDER
        val next = order[(order.indexOf(activeSettings.voicePreset) + 1) % order.size]
        saveAndPushSettings(MicDspProcessor.applyPresetToSettings(activeSettings, next))
    }

    private fun cycleNsModeLive() {
        val order = MicDspProcessor.NOISE_MODE_ORDER
        val next = order[(order.indexOf(activeSettings.noiseSuppressMode) + 1) % order.size]
        val lvl = if (next != "OFF" && activeSettings.rnnoiseLevel == 0) 2 else activeSettings.rnnoiseLevel
        saveAndPushSettings(activeSettings.copy(noiseSuppressMode = next, rnnoiseLevel = lvl))
    }

    private fun cycleRnnoiseLive() {
        val nextLvl = (activeSettings.rnnoiseLevel + 1) % 4
        val nextMode = if (nextLvl == 0) "OFF" else if (activeSettings.noiseSuppressMode == "OFF") "RNNOISE" else activeSettings.noiseSuppressMode
        saveAndPushSettings(activeSettings.copy(rnnoiseLevel = nextLvl, noiseSuppressMode = nextMode))
    }

    private fun cycleHpfLive() {
        val order = listOf(0, 75, 80, 85, 100, 120)
        val next = order[(order.indexOf(activeSettings.hpfCutoffHz) + 1) % order.size]
        saveAndPushSettings(activeSettings.copy(hpfCutoffHz = next))
    }

    private fun toggleVoiceEqLive() = saveAndPushSettings(activeSettings.copy(voiceEqEnabled = !activeSettings.voiceEqEnabled))
    private fun toggleDeEsserLive() = saveAndPushSettings(activeSettings.copy(deEsserEnabled = !activeSettings.deEsserEnabled))
    private fun toggleVelvetLive() = saveAndPushSettings(activeSettings.copy(velvetWarmthEnabled = !activeSettings.velvetWarmthEnabled))
    private fun toggleSlowAgcLive() = saveAndPushSettings(activeSettings.copy(slowAgcEnabled = !activeSettings.slowAgcEnabled))

    private fun stepMicBoostLive(deltaPct: Int) {
        val next = (activeSettings.micGainPercent + deltaPct).coerceIn(75, 250)
        saveAndPushSettings(activeSettings.copy(micGainPercent = next))
    }

    private fun stepGameMixLive(deltaPct: Int) {
        val next = (activeSettings.gameGainPercent + deltaPct).coerceIn(0, 250)
        saveAndPushSettings(activeSettings.copy(gameGainPercent = next))
    }

    private fun toggleGameCinemaWideLive() =
        saveAndPushSettings(activeSettings.copy(gameCinemaWideEnabled = !activeSettings.gameCinemaWideEnabled))

    private fun toggleGameSubPunchLive() =
        saveAndPushSettings(activeSettings.copy(gameSubPunchEnabled = !activeSettings.gameSubPunchEnabled))

    private fun toggleGameFootstepLive() =
        saveAndPushSettings(activeSettings.copy(gameFootstepFocusEnabled = !activeSettings.gameFootstepFocusEnabled))

    private fun toggleGameBlastTamerLive() =
        saveAndPushSettings(activeSettings.copy(gameBlastTamerEnabled = !activeSettings.gameBlastTamerEnabled))

    private fun cycleDuckingLive() {
        val order = listOf(0, 20, 35, 50)
        val next = order[(order.indexOf(activeSettings.vadDuckingPercent) + 1) % order.size]
        saveAndPushSettings(activeSettings.copy(vadDuckingPercent = next))
    }

    private fun cycleMicHardwareLive() {
        val now = SystemClock.uptimeMillis()
        if (now - lastOverlayMicSwitchMs < 900L) return
        lastOverlayMicSwitchMs = now

        val inputs = CodecCapabilityProbe.listAvailableMicInputs(this)
        if (inputs.isEmpty()) return
        val curIdx = inputs.indexOfFirst { it.deviceId == activeSettings.micDeviceId }
        val nextOpt = inputs[(curIdx + 1) % inputs.size]
        saveAndPushSettings(activeSettings.copy(micDeviceId = nextOpt.deviceId))
    }

    private fun cycleUpscalerLive() {
        val order = listOf("BILINEAR", "BICUBIC", "LANCZOS")
        val next = order[(order.indexOf(activeSettings.upscalerMode) + 1) % order.size]
        saveAndPushSettings(activeSettings.copy(upscalerMode = next))
    }

    private fun cycleFramingModeLive() {
        val order = listOf("FIT", "STRETCH", "SMART", "FILL")
        val next = order[(order.indexOf(activeSettings.framingMode) + 1) % order.size]
        saveAndPushSettings(activeSettings.copy(framingMode = next))
    }

    private fun cycleSmartZoomLive() {
        val order = listOf(104, 108, 112, 116, 120)
        val next = order[(order.indexOf(activeSettings.smartZoomPercent) + 1) % order.size]
        saveAndPushSettings(activeSettings.copy(smartZoomPercent = next))
    }

    private fun toggleCinematicFilmicLive() =
        saveAndPushSettings(activeSettings.copy(cinematicFilmic = !activeSettings.cinematicFilmic))

    private fun toggleAmoledVividLive() =
        saveAndPushSettings(activeSettings.copy(amoledVivid = !activeSettings.amoledVivid))

    private fun toggleShadowBoostLive() =
        saveAndPushSettings(activeSettings.copy(shadowBoost = !activeSettings.shadowBoost))

    private fun cycleColorTempLive() {
        val order = listOf("NEUTRAL", "WARM", "COOL")
        val next = order[(order.indexOf(activeSettings.colorTempFilter) + 1) % order.size]
        saveAndPushSettings(activeSettings.copy(colorTempFilter = next))
    }

    private fun toggleLandscapeFlipLive() {
        val next = if (activeSettings.landscapeFlip == 180) 0 else 180
        saveAndPushSettings(activeSettings.copy(landscapeFlip = next))
    }

    private fun toggleGpuSharpenLive() {
        userManuallyOverrodeSharp = true
        saveAndPushSettings(activeSettings.copy(gpuSharpen = !activeSettings.gpuSharpen))
    }

    private fun toggleOverlayPngLive() =
        saveAndPushSettings(activeSettings.copy(overlayImageEnabled = !activeSettings.overlayImageEnabled))

    private fun toggleLiveBannerLive() =
        saveAndPushSettings(activeSettings.copy(liveBannerEnabled = !activeSettings.liveBannerEnabled))

    private fun toggleMinimapShieldLive() {
        if (!activeSettings.minimapShieldEnabled) {
            saveAndPushSettings(activeSettings.copy(minimapShieldEnabled = true))
        } else {
            val corners = listOf("TOP_RIGHT", "TOP_LEFT", "BOTTOM_LEFT", "BOTTOM_RIGHT")
            val idx = corners.indexOf(activeSettings.minimapShieldCorner)
            if (idx in 0 until corners.size - 1) {
                saveAndPushSettings(activeSettings.copy(minimapShieldCorner = corners[idx + 1]))
            } else {
                saveAndPushSettings(activeSettings.copy(minimapShieldEnabled = false, minimapShieldCorner = "TOP_RIGHT"))
            }
        }
    }

    private fun stepLiveBitrate(dir: Int) {
        val stepsKbps = listOf(
            500, 800, 1000, 1500, 2000, 3000, 4500, 6000, 8000, 10000, 12000, 14000,
            16000, 18000, 20000, 24000, 30000, 35000, 40000, 50000, 65000, 80000, 100000
        )
        val curKbps = activeBitrateBps / 1000
        val closestIdx = stepsKbps.indices.minByOrNull { abs(stepsKbps[it] - curKbps) } ?: 14
        val nextIdx = (closestIdx + dir).coerceIn(0, stepsKbps.size - 1)
        val nextKbps = stepsKbps[nextIdx]

        activeBitrateBps = nextKbps * 1000
        maxConfiguredBitrateBps = activeBitrateBps
        minAdaptiveBitrateBps = computeDynamicAbrFloorBps(maxConfiguredBitrateBps)
        videoPipeline?.setBitrateOnFly(activeBitrateBps)
        saveAndPushSettings(activeSettings.copy(targetBitrateKbps = nextKbps))
    }

    private fun toggleAbrLive() =
        saveAndPushSettings(activeSettings.copy(adaptiveBitrateEnabled = !activeSettings.adaptiveBitrateEnabled))

    private fun toggleThermal30FpsSaverLive() {
        val vPipe = videoPipeline ?: return
        userManuallyOverrode30FpsSaver = true
        val newThrottleState = !vPipe.thermalThrottle30Fps
        vPipe.thermalThrottle30Fps = newThrottleState
        if (newThrottleState && activeSettings.fps > 30) {
            val scaledBps = max(minAdaptiveBitrateBps, (maxConfiguredBitrateBps * 60) / 100)
            activeBitrateBps = scaledBps
            lastThermalReducedBps = scaledBps
            vPipe.setBitrateOnFly(activeBitrateBps)
        } else {
            activeBitrateBps = maxConfiguredBitrateBps
            lastThermalReducedBps = -1
            vPipe.setBitrateOnFly(activeBitrateBps)
        }
        refreshExpandedControlsState()
    }

    private fun onStopButtonTappedInOverlay() {
        val now = SystemClock.uptimeMillis()
        if (now - lastStopConfirmTapMs < 2500L) {
            stopLiveBroadcast("Stopped from floating menu")
        } else {
            lastStopConfirmTapMs = now
            btnOvStopConfirm?.text = "Tap Again to Stop!"
            mainHandler.postDelayed({
                btnOvStopConfirm?.text = "■ Stop Stream"
            }, 2500L)
        }
    }

    // === PER-SESSION GENERATION-GUARDED PRIMARY RTMP CALLBACK HANDLERS ===
    private fun onPrimaryConnectionStarted(sessionGen: Long) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) return
    }

    private fun onPrimaryConnectionSuccess(sessionGen: Long, callbackOwner: PrimaryRtmpCallback) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) {
            val staleClient = rtmpClient
            netExecutor.execute { try { staleClient?.disconnect() } catch (_: Exception) {} }
            return
        }
        rtmpConnected = true
        reconnectCount = 0
        zeroThroughputStallTicks = 0
        reconnectScheduled.set(false)
        val serverTag = if (usingBackupUrl) "Backup RTMPS (?backup=1)" else "Primary RTMPS"
        DiagnosticsLogger.log("NETWORK", "Connected to $serverTag ingest. Stream is LIVE.")
        val sps = cachedSps
        if (sps != null) {
            rtmpClient?.setVideoInfo(
                ByteBuffer.wrap(sps),
                cachedPps?.let { ByteBuffer.wrap(it) },
                cachedVps?.let { ByteBuffer.wrap(it) }
            )
        }
        probeIngestTcpRttIfNeeded()
        mainHandler.post { updatePillTelemetryUI() }
    }

    private fun onPrimaryNewBitrate(sessionGen: Long, bitrate: Long) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) return
    }

    private fun onPrimaryConnectionFailed(sessionGen: Long, reason: String) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) return
        scheduleSilentReconnect(reason, sessionGen)
    }

    private fun onPrimaryDisconnect(sessionGen: Long) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) return
        rtmpConnected = false
        mainHandler.post { updatePillTelemetryUI() }
    }

    private fun onPrimaryAuthError(sessionGen: Long) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) return
        rtmpConnected = false
        DiagnosticsLogger.log("AUTH_ERR", "RTMPS server rejected the Stream Key (Auth Error).")
        mainHandler.post {
            Toast.makeText(this, "Stream Key rejected by server. Check Key Vault.", Toast.LENGTH_LONG).show()
            stopLiveBroadcast("RTMPS Auth Error")
        }
    }

    private fun onPrimaryAuthSuccess(sessionGen: Long) {}

    // Unused legacy interface methods (kept so SolStreamService still implements ConnectChecker cleanly)
    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {}
    override fun onNewBitrate(bitrate: Long) {}
    override fun onConnectionFailed(reason: String) {}
    override fun onDisconnect() {}
    override fun onAuthError() {}
    override fun onAuthSuccess() {}

    // === NOTIFICATION & WAKELOCK HELPERS ===
    private fun acquirePartialWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SolStreamStudio::BroadcastLock").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "SolStream Live Broadcast",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active gaming live stream & quick controls"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildStreamNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 10, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, SolStreamService::class.java).apply { action = ACTION_TOGGLE_MIC_MUTE }
        val mutePending = PendingIntent.getService(
            this, 11, muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val brbIntent = Intent(this, SolStreamService::class.java).apply { action = ACTION_TOGGLE_BRB }
        val brbPending = PendingIntent.getService(
            this, 12, brbIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clipIntent = Intent(this, SolStreamService::class.java).apply { action = ACTION_MARK_CLIP }
        val clipPending = PendingIntent.getService(
            this, 13, clipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SolStreamService::class.java).apply { action = ACTION_STOP_STREAM }
        val stopPending = PendingIntent.getService(
            this, 14, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentTitle("SolStream Studio • ULTRA")
            .setContentText(contentText)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Mute Mic", mutePending).build())
            .addAction(Notification.Action.Builder(null, "Scene/BRB", brbPending).build())
            .addAction(Notification.Action.Builder(null, "★ Clip", clipPending).build())
            .addAction(Notification.Action.Builder(null, "Stop", stopPending).build())
            .build()
    }

    private fun updateStreamNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildStreamNotification(text))
        } catch (_: Exception) {
        }
    }

    private object RtmpTelemetryBridge {
        private var resolved = false
        private var mCacheItems: Method? = null
        private var mCacheSize: Method? = null
        private var mQueueBytes: Method? = null
        private var mRtt: Method? = null
        private var mDroppedVideo: Method? = null
        private var mSentBitrate: Method? = null

        @Synchronized
        private fun ensureResolved(client: RtmpClient) {
            if (resolved) return
            resolved = true
            val cls = client.javaClass
            mCacheItems = findZeroArg(cls, "getItemsInCacheCount", "getCacheItemsCount")
            mCacheSize = findZeroArg(cls, "getCacheSize", "getMaxCacheSize")
            mQueueBytes = findZeroArg(cls, "getSentQueueBytes", "getBytesInQueue")
            mRtt = findZeroArg(cls, "getRtt", "getPingRtt")
            mDroppedVideo = findZeroArg(cls, "getDroppedVideoFrames", "getDroppedFrames")
            mSentBitrate = findZeroArg(cls, "getBitrate", "getSentBitrate")
        }

        private fun findZeroArg(cls: Class<*>, vararg names: String): Method? {
            for (n in names) {
                try {
                    return cls.getMethod(n).apply { isAccessible = true }
                } catch (_: Exception) {
                }
            }
            return null
        }

        fun readCacheItemsCount(client: RtmpClient): Int {
            ensureResolved(client)
            return try { (mCacheItems?.invoke(client) as? Number)?.toInt() ?: -1 } catch (_: Exception) { -1 }
        }

        fun readCacheSizeCapacity(client: RtmpClient): Int {
            ensureResolved(client)
            return try { (mCacheSize?.invoke(client) as? Number)?.toInt() ?: 120 } catch (_: Exception) { 120 }
        }

        fun readSentQueueBytes(client: RtmpClient): Long {
            ensureResolved(client)
            return try { (mQueueBytes?.invoke(client) as? Number)?.toLong() ?: -1L } catch (_: Exception) { -1L }
        }

        fun readRttMs(client: RtmpClient): Long {
            ensureResolved(client)
            return try { (mRtt?.invoke(client) as? Number)?.toLong() ?: -1L } catch (_: Exception) { -1L }
        }

        fun readDroppedVideoFrames(client: RtmpClient): Long {
            ensureResolved(client)
            return try { (mDroppedVideo?.invoke(client) as? Number)?.toLong() ?: 0L } catch (_: Exception) { 0L }
        }

        fun readSentBitrateKbps(client: RtmpClient): Int {
            ensureResolved(client)
            return try { ((mSentBitrate?.invoke(client) as? Number)?.toLong() ?: -1L).toInt() / 1000 } catch (_: Exception) { -1 }
        }
    }

    companion object {
        const val ACTION_START_STREAM = "com.solgaming.solstreamstudio.ACTION_START_STREAM"
        const val ACTION_STOP_STREAM = "com.solgaming.solstreamstudio.ACTION_STOP_STREAM"
        const val ACTION_START_BENCHMARK = "com.solgaming.solstreamstudio.ACTION_START_BENCHMARK"
        const val ACTION_TOGGLE_MIC_MUTE = "com.solgaming.solstreamstudio.ACTION_TOGGLE_MIC_MUTE"
        const val ACTION_TOGGLE_BRB = "com.solgaming.solstreamstudio.ACTION_TOGGLE_BRB"
        const val ACTION_MARK_CLIP = "com.solgaming.solstreamstudio.ACTION_MARK_CLIP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val CHANNEL_ID = "solstream_live_channel"
        private const val NOTIF_ID = 1001

        @Volatile private var instance: SolStreamService? = null

        val isBroadcastLive: Boolean
            get() = instance?.broadcastActive == true

        val isBenchmarkRunning: Boolean
            get() = instance?.benchmarkOnlyActive == true

        fun pushLiveSettingsIfRunning(settings: StreamSettings) {
            val svc = instance ?: return
            if (svc.broadcastActive) {
                svc.mainHandler.post { svc.saveAndPushSettings(settings) }
            }
        }

        internal fun formatOverlayQueueTag(qFrames: Int): String =
            if (qFrames >= 0) "Q:${qFrames}f" else "Q:N/A"

        internal fun shouldAcceptConnectionCallback(
            isBroadcastActive: Boolean,
            callbackGen: Long,
            currentGen: Long,
            isTargetStopped: Boolean = false
        ): Boolean {
            return isBroadcastActive && !isTargetStopped && callbackGen == currentGen
        }

        /**
         * Pure helper for Queue-Aware Ping Guard ABR 2.0 step-up recovery:
         * Strictly requires at least one valid (non-negative) queue telemetry metric and zero drops
         * before allowing bitrate step-up, so unavailable (-1) telemetry never triggers false recovery.
         */
        internal fun isQueueCalmForAbrRecovery(
            qFrames: Int,
            qBytes: Long,
            droppedDelta: Int
        ): Boolean {
            if (droppedDelta != 0) return false
            val hasValidMetric = (qFrames >= 0) || (qBytes >= 0L)
            if (!hasValidMetric) return false
            val framesOk = (qFrames < 0) || (qFrames <= 5)
            val bytesOk = (qBytes < 0L) || (qBytes in 0L..180_000L)
            return framesOk && bytesOk
        }

        fun isAccessibilityEnabled(context: Context): Boolean {
            return try {
                val expected = ComponentName(context, SolStreamService::class.java)
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                enabledServices.split(':').any {
                    ComponentName.unflattenFromString(it.trim()) == expected
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
