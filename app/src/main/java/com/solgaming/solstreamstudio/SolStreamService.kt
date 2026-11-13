/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.Manifest
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
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
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
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.rtmp.rtmp.RtmpClient
import java.lang.reflect.Method
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    private var splitEncodeSimulcastActive = false

    private var videoPipeline: SolVideoPipeline? = null
    private var audioEngine: SolDualAudioEngine? = null
    private var mediaProjection: MediaProjection? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var lastActiveNetworkHandle: Long = 0L

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

    // Real-time outbound send-queue tracking (guarantees real Queue telemetry instead of Q: N/A)
    private val inFlightVideoFrames = AtomicInteger(0)
    private val inFlightVideoBytes = AtomicLong(0L)
    @Volatile private var lastVideoSendLatencyMs: Long = 1L

    // Manual override flags so automatic thermal saver never fights explicit user clicks
    private var userManuallyOverrodeSharp = false
    private var userManuallyOverrode30FpsSaver = false
    private var severeThermalLastSeenElapsedMs = 0L

    // Dual-Layer Keyboard Privacy Blur states (Auto IME detector + Manual 1-tap override)
    @Volatile private var imeKeyboardVisible = false
    @Volatile private var manualKeyboardBlurOverride = false

    private var reconnectCount = 0
    private var maxConfiguredBitrateBps = 20_000_000
    private var maxConfiguredCompanionBps = 8_000_000
    private var minAdaptiveBitrateBps = 6_000_000
    private var activeBitrateBps = 20_000_000
    private var activeThermalCeilingBps = 20_000_000
    private var wasThermalThrottledLastTick = false
    private var calmTicksCount = 0
    private var zeroThroughputStallTicks = 0
    private var lastDroppedTotal = 0L
    private var lastRttProbeElapsedMs = 0L

    @Volatile private var cachedSps: ByteArray? = null
    @Volatile private var cachedPps: ByteArray? = null
    @Volatile private var cachedVps: ByteArray? = null

    private var windowManager: WindowManager? = null
    private var pillRootView: LinearLayout? = null
    private var imeDetectorView: View? = null
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
    private var btnOvOverlayPos: TextView? = null
    private var btnOvLiveBanner: TextView? = null
    private var btnOvMinimapShield: TextView? = null
    private var btnOvKeyboardBlur: TextView? = null

    // Tab 3 (Bitrate / Net) live buttons & telemetry readouts
    private var tvOvBitrateReadout: TextView? = null
    private var btnOvAbrToggle: TextView? = null
    private var btnOvThermal30Saver: TextView? = null
    private var tvOvFpsAndSocReadout: TextView? = null
    private var tvOvQueueAndRttReadout: TextView? = null
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
     * GPT Audit Fix #1: Per-session primary RTMP callback wrapper that holds a direct reference
     * to the exact RtmpClient instance it was created for (clientRef), so a delayed callback from
     * a stopped client can only disconnect its own old socket and never a newer broadcast session.
     */
    private class PrimaryRtmpCallback(
        private val sessionGen: Long,
        private val service: SolStreamService
    ) : ConnectChecker {
        @Volatile var clientRef: RtmpClient? = null

        override fun onConnectionStarted(url: String) {
            service.onPrimaryConnectionStarted(sessionGen)
        }

        override fun onConnectionSuccess() {
            service.onPrimaryConnectionSuccess(sessionGen, clientRef)
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

    /**
     * GPT Audit Fix #2: Bounded 90-frame send queue (DiscardOldestPolicy) with safe
     * RejectedExecutionException handling so a stalled auxiliary target never leaks RAM.
     */
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
        private val sendExecutor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(90),
            ThreadFactory { r -> Thread(r, "SolAuxRtmp-$name").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardOldestPolicy()
        )

        private fun executeSafely(task: () -> Unit) {
            if (sendExecutor.isShutdown) return
            try {
                sendExecutor.execute(task)
            } catch (_: RejectedExecutionException) {
            }
        }

        fun startAsync() {
            stopped = false
            executeSafely {
                if (stopped || !shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get(), stopped)) {
                    return@executeSafely
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
                        val w = if (isPortrait) 1080 else if (splitEncodeSimulcastActive) 1920 else activeSettings.width
                        val h = if (isPortrait) 1920 else if (splitEncodeSimulcastActive) 1080 else activeSettings.height
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
            executeSafely {
                if (!connected || stopped) return@executeSafely
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
            executeSafely {
                if (!connected || stopped) return@executeSafely
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
            executeSafely {
                if (!connected || stopped) return@executeSafely
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
            executeSafely {
                try { client.disconnect() } catch (_: Exception) {}
            }
            try { sendExecutor.shutdown() } catch (_: Exception) {}
        }

        override fun onConnectionStarted(url: String) {}

        override fun onConnectionSuccess() {
            if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get(), stopped)) {
                executeSafely { try { client.disconnect() } catch (_: Exception) {} }
                return
            }
            connected = true
            reconnecting.set(false)
            DiagnosticsLogger.log("AUX_LIVE", "$name target connected and live.")
            if (!name.contains("9:16") && !splitEncodeSimulcastActive) {
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
        notifyDashboardStateChanged()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        notifyDashboardStateChanged()
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
                    CodecCapabilityProbe.isBroadcastStartingOrLive = false
                    DiagnosticsLogger.log("START_ABORT", "Screen capture permission was not granted.")
                }
            }
            ACTION_START_BENCHMARK, ACTION_START_GLIP_BENCH -> startPassiveCompetitorBenchmark()
            ACTION_STOP_STREAM, ACTION_STOP_GLIP_BENCH -> stopLiveBroadcast("Stopped by user")
            ACTION_TOGGLE_MIC_MUTE -> toggleMicMuteLive()
            ACTION_TOGGLE_BRB -> cycleSceneLive()
            ACTION_MARK_CLIP -> saveClipMarkerLive("Notification ★ Clip")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterThermalStatusListenerIfSupported()
        stopLiveBroadcast("Service destroyed")
        if (instance === this) instance = null
        notifyDashboardStateChanged()
        super.onDestroy()
    }

    private fun notifyDashboardStateChanged() {
        mainHandler.post {
            try { onStateChangedListener?.invoke() } catch (_: Exception) {}
        }
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

    /**
     * GPT Audit Fix #5: Verifies RECORD_AUDIO permission before promoting the Benchmark foreground
     * service with FOREGROUND_SERVICE_TYPE_MICROPHONE on Android 14-18+, wrapped in try/catch.
     */
    private fun startPassiveCompetitorBenchmark() {
        if (broadcastActive || benchmarkOnlyActive) return
        val hasMicPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPerm) {
            Toast.makeText(
                this,
                "Grant Microphone permission in Card 1 first to start Benchmark mode.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        activeSettings = KeystoreVault.loadSettings(this)
        val notif = buildStreamNotification("Competitor App Thermal & RAM Benchmark Active")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            DiagnosticsLogger.log("BENCH_ERR", "Benchmark foreground service blocked (${e.javaClass.simpleName}).")
            Toast.makeText(this, "Could not start Benchmark service (${e.javaClass.simpleName}).", Toast.LENGTH_LONG).show()
            return
        }

        benchmarkOnlyActive = true
        needsProjectionReapproval = false
        currentLiveStateLabel = "BENCHMARK ACTIVE (Profiling Thermal & RAM)"
        userManuallyOverrodeSharp = false
        userManuallyOverrode30FpsSaver = false
        severeThermalLastSeenElapsedMs = 0L

        DiagnosticsLogger.startNewSession(
            this,
            "Competitor App Benchmark",
            "Passive Thermal & Memory Profiler (Zero Encoding)"
        )
        acquirePartialWakeLock()
        attachFloatingOverlayPill()
        mainHandler.removeCallbacks(healthTickRunnable)
        mainHandler.post(healthTickRunnable)
        notifyDashboardStateChanged()
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

        // Checklist #9 & #32: Immediately reserve isBroadcastStartingOrLive so background hardware scans
        // cannot start or overlap while we promote foreground service and allocate live encoders!
        CodecCapabilityProbe.isBroadcastStartingOrLive = true

        activeSettings = KeystoreVault.loadSettings(this)
        needsProjectionReapproval = false
        userManuallyOverrodeSharp = false
        userManuallyOverrode30FpsSaver = false
        imeKeyboardVisible = false
        manualKeyboardBlurOverride = false
        severeThermalLastSeenElapsedMs = 0L
        wasThermalThrottledLastTick = false
        lastRttProbeElapsedMs = 0L
        inFlightVideoFrames.set(0)
        inFlightVideoBytes.set(0L)
        lastVideoSendLatencyMs = 1L

        val builtPrimaryUrl = KeystoreVault.buildActivePrimaryEndpoint(this, activeSettings)
        if (builtPrimaryUrl.isNullOrBlank()) {
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            Toast.makeText(this, "Save your YouTube/RTMPS Stream Key in the Vault first.", Toast.LENGTH_LONG).show()
            return
        }

        // Android 17/18 BackgroundAudioHardening (#32): Promote foreground service FIRST before
        // touching MediaProjection, AudioRecord, or MediaCodec!
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
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            DiagnosticsLogger.log("FGS_ERR", "Foreground service promotion failed (${e.javaClass.simpleName}).")
            Toast.makeText(this, "Screen capture token expired. Please tap Go Live again.", Toast.LENGTH_LONG).show()
            return
        }

        // Checklist #9: If an initial startup hardware scan is still running on a worker thread,
        // wait cleanly up to 5000 ms for it to finish before allocating live encoders.
        val scan = CodecCapabilityProbe.awaitCachedSummary(this, maxWaitMs = 5000L)

        primaryStreamUrl = builtPrimaryUrl
        backupStreamUrl = KeystoreVault.deriveYoutubeBackupUrl(primaryStreamUrl)
        parseIngestHostAndPort(primaryStreamUrl)
        usingBackupUrl = false
        reconnectCount = 0
        reconnectScheduled.set(false)
        val mySessionGen = connectionSessionGen.incrementAndGet()
        cachedSps = null
        cachedPps = null
        cachedVps = null

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            DiagnosticsLogger.log("PROJ_ERR", "MediaProjection returned null.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        mediaProjection = projection
        acquirePartialWakeLock()
        if (activeSettings.wifiLowLatencyLock) {
            acquireWifiLowLatencyLock()
        }

        val originNs = System.nanoTime()
        val originUs = originNs / 1000L

        val primaryCb = PrimaryRtmpCallback(mySessionGen, this)
        val client = RtmpClient(primaryCb).apply {
            setLogs(false)
            setCheckServerAlive(true)
            setOnlyVideo(false)
            try { resizeCache(120) } catch (_: Throwable) {}
        }
        primaryCb.clientRef = client
        rtmpClient = client

        auxTargets.clear()
        companionTarget = null
        splitEncodeSimulcastActive = false

        val useShortsSlot = activeSettings.isVertical &&
            activeSettings.useShortsKeySlot &&
            KeystoreVault.getShortsStreamKey(this).isNotBlank()
        val targetNames = mutableListOf(
            if (useShortsSlot) "YouTube 9:16 Shorts" else "Primary (${if (activeSettings.isVertical) "9:16" else "16:9"})"
        )

        val simTargets = KeystoreVault.buildSimulcastTargets(this, activeSettings)
        for ((simName, simUrl) in simTargets) {
            auxTargets.add(AuxRtmpTarget(simName, simUrl, "H264", mySessionGen))
            targetNames.add(simName)
        }

        val compEndpoint = if (activeSettings.dualFormatCompanionEnabled && !activeSettings.isVertical) {
            KeystoreVault.buildCompanionVerticalEndpoint(this)
        } else {
            null
        }
        if (!compEndpoint.isNullOrBlank()) {
            val compCodec = if (scan.hevcHardware != null) "HEVC" else "H264"
            companionTarget = AuxRtmpTarget("YT 9:16 Companion", compEndpoint, compCodec, mySessionGen)
            targetNames.add("YT 9:16 Companion")
        } else if (activeSettings.simulcastSplitEncode &&
            auxTargets.isNotEmpty() &&
            !activeSettings.isVertical &&
            scan.supportsDualFormatCompanion()
        ) {
            // Checklist #18: Mode 2 Split-Encode Simulcast — Primary encoder sends 1440p/4K HEVC to
            // YouTube while Secondary Hardware Encoder outputs 1080p60 H.264 CBR to Twitch/Kick!
            splitEncodeSimulcastActive = true
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
                    splitEncodeSimulcastActive = false
                    DiagnosticsLogger.log("DUAL_STOP", "Secondary companion encoder stopped ($reason); Primary stream continues.")
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

        val needSecondEncoder = (companionTarget != null) || splitEncodeSimulcastActive
        val videoInfo = vPipeline.startPipeline(
            settings = activeSettings,
            mediaProjection = projection,
            originTimeNs = originNs,
            onProjectionStoppedBySystem = {
                mainHandler.post {
                    if (broadcastActive) {
                        needsProjectionReapproval = true
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
                if (!splitEncodeSimulcastActive) {
                    for (aux in auxTargets) {
                        aux.sendVideoHeader(spsArr, ppsArr, vpsArr)
                    }
                }
            },
            onEncodedVideo = { bb, info ->
                if (rtmpConnected) {
                    inFlightVideoFrames.incrementAndGet()
                    inFlightVideoBytes.addAndGet(info.size.toLong())
                    val t0Ns = System.nanoTime()
                    try {
                        client.sendVideo(bb, info)
                    } finally {
                        val elapsedMs = ((System.nanoTime() - t0Ns) / 1_000_000L).coerceAtLeast(0L)
                        lastVideoSendLatencyMs = elapsedMs
                        DiagnosticsLogger.lastSendLatencyMs = elapsedMs
                        inFlightVideoFrames.decrementAndGet()
                        inFlightVideoBytes.addAndGet(-info.size.toLong())
                    }
                }
                if (auxTargets.isNotEmpty() && !splitEncodeSimulcastActive) {
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
            enableDualCompanion = needSecondEncoder,
            onCompanionConfig = { sps, pps, vps ->
                val spsArr = copyToByteArray(sps)
                val ppsArr = pps?.let { copyToByteArray(it) }
                val vpsArr = vps?.let { copyToByteArray(it) }
                if (splitEncodeSimulcastActive) {
                    for (aux in auxTargets) {
                        aux.sendVideoHeader(spsArr, ppsArr, vpsArr)
                    }
                } else {
                    companionTarget?.sendVideoHeader(spsArr, ppsArr, vpsArr)
                }
            },
            onCompanionVideo = { bb, info ->
                if (splitEncodeSimulcastActive) {
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
                } else {
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
            },
            companionIsLandscapeH264 = splitEncodeSimulcastActive
        )

        if (videoInfo == null) {
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            Toast.makeText(
                this,
                "Hardware video encoder rejected settings. Try 1080p60 or H.264.",
                Toast.LENGTH_LONG
            ).show()
            cleanupResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        if (!videoInfo.companionActive) {
            if (companionTarget != null) {
                companionTarget?.stop()
                companionTarget = null
            }
            splitEncodeSimulcastActive = false
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
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            Toast.makeText(this, "Microphone initialization failed. Check Mic permission.", Toast.LENGTH_LONG).show()
            vPipeline.stopPipeline()
            cleanupResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        videoPipeline = vPipeline
        audioEngine = aEngine
        broadcastActive = true
        currentLiveStateLabel = "CONNECTING TO INGEST..."

        maxConfiguredBitrateBps = (activeSettings.targetBitrateKbps.coerceIn(500, 100_000) * 1000)
        maxConfiguredCompanionBps = vPipeline.companionBitrateBps
        activeBitrateBps = vPipeline.currentBitrateBps
        activeThermalCeilingBps = maxConfiguredBitrateBps
        minAdaptiveBitrateBps = computeDynamicAbrFloorBps(maxConfiguredBitrateBps)
        calmTicksCount = 0
        zeroThroughputStallTicks = 0
        lastDroppedTotal = 0L

        val hdrTag = if (videoInfo.isHdr10) " • 10-Bit HDR10" else ""
        val dualTag = when {
            videoInfo.companionActive && splitEncodeSimulcastActive -> " + Split-Encode 1080p H.264"
            videoInfo.companionActive -> " + 9:16 Companion"
            else -> ""
        }
        val pipeSummary = "${videoInfo.codecKey} (${videoInfo.encoderName}) ${videoInfo.outWidth}x${videoInfo.outHeight}@${videoInfo.fps}fps ${if (videoInfo.isVbr) "VBR" else "CBR"}$hdrTag$dualTag • ${videoInfo.glEngineLabel}"

        DiagnosticsLogger.activeAudioDspDesc =
            "${aEngine.micDsp.voiceGenderProfile}/${aEngine.micDsp.voicePreset} • ${aEngine.micDsp.activeDenoiserSummary} • ${activeSettings.audioBitrateKbps}k AAC"
        DiagnosticsLogger.startNewSession(this, "Live Broadcast", pipeSummary)

        registerMultipathNetworkHandoverWatcher(mySessionGen)
        attachFloatingOverlayPill()
        updateStreamNotification("LIVE: ${videoInfo.outWidth}x${videoInfo.outHeight}@${videoInfo.fps} (${videoInfo.codecKey})")
        notifyDashboardStateChanged()

        connectRtmpAsync(primaryStreamUrl, mySessionGen, client)
        for (aux in auxTargets) {
            aux.startAsync()
        }
        companionTarget?.startAsync()

        mainHandler.removeCallbacks(healthTickRunnable)
        mainHandler.postDelayed(healthTickRunnable, 2000L)
    }

    /**
     * Checklist #20: Instant Wi-Fi <-> 5G Mobile Data Multipath Failover Watcher.
     * When both Wi-Fi and 5G Mobile Data are open, detects the exact millisecond Android switches
     * the default internet pipe and triggers a fast 300 ms reconnect over the new network.
     */
    private fun registerMultipathNetworkHandoverWatcher(sessionGen: Long) {
        unregisterMultipathNetworkHandoverWatcher()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            lastActiveNetworkHandle = cm.activeNetwork?.networkHandle ?: 0L
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!broadcastActive || sessionGen != connectionSessionGen.get()) return
                    val handle = network.networkHandle
                    val prev = lastActiveNetworkHandle
                    lastActiveNetworkHandle = handle
                    if (prev != 0L && handle != prev) {
                        val caps = try { cm.getNetworkCapabilities(network) } catch (_: Exception) { null }
                        val pipeName = when {
                            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "5G / Mobile Data"
                            else -> "Alternate Network"
                        }
                        DiagnosticsLogger.log(
                            "NET_HANDOFF",
                            "Active internet route switched to $pipeName; triggering instant 0.3s stream handover..."
                        )
                        scheduleSilentReconnect("Route handoff to $pipeName", sessionGen, immediateHandover = true)
                    }
                }
            }
            networkCallback = cb
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(cb, mainHandler)
            } else {
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(req, cb)
            }
        } catch (_: Exception) {
        }
    }

    private fun unregisterMultipathNetworkHandoverWatcher() {
        val cb = networkCallback ?: return
        networkCallback = null
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
        }
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

    private fun scheduleSilentReconnect(
        reason: String,
        sessionGen: Long,
        immediateHandover: Boolean = false
    ) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) return
        if (!reconnectScheduled.compareAndSet(false, true)) return

        rtmpConnected = false
        reconnectCount++
        DiagnosticsLogger.reconnectAttempts = reconnectCount
        currentLiveStateLabel = "RECONNECTING (#$reconnectCount)..."

        val useBackupNow = (!immediateHandover && reconnectCount >= 2 && !backupStreamUrl.isNullOrBlank() && !usingBackupUrl)
        if (useBackupNow) {
            usingBackupUrl = true
        }
        val targetUrl = if (usingBackupUrl && !backupStreamUrl.isNullOrBlank()) {
            backupStreamUrl!!
        } else {
            primaryStreamUrl
        }

        val delayMs = if (immediateHandover) {
            300L
        } else {
            when (reconnectCount) {
                1 -> 1200L
                2 -> 2500L
                3 -> 4500L
                else -> 7000L
            }
        }

        val targetDesc = if (usingBackupUrl) "YouTube Backup Server (b.rtmps)" else "Primary Server"
        DiagnosticsLogger.log(
            "RECONNECT",
            "Network hiccup ($reason). Auto-reconnecting (#$reconnectCount) via $targetDesc in ${delayMs / 1000.0}s while keeping game capture alive."
        )
        mainHandler.post {
            updatePillTelemetryUI()
            notifyDashboardStateChanged()
        }

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

    fun stopLiveBroadcast(reason: String = "Stopped by user") {
        val wasLive = broadcastActive
        val wasBench = benchmarkOnlyActive
        if (!wasLive && !wasBench) {
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            return
        }

        // Checklist #34: Snapshot the true active mic status BEFORE stopping the audio engine!
        DiagnosticsLogger.lockFinalMicStatusBeforeStop()

        connectionSessionGen.incrementAndGet()
        broadcastActive = false
        benchmarkOnlyActive = false
        CodecCapabilityProbe.isBroadcastStartingOrLive = false
        rtmpConnected = false
        reconnectScheduled.set(false)
        currentLiveStateLabel = "IDLE (Ready)"
        mainHandler.removeCallbacks(healthTickRunnable)
        unregisterMultipathNetworkHandoverWatcher()

        DiagnosticsLogger.log("SESSION_END", "Stream ended ($reason).")
        val snap = DiagnosticsLogger.finishAndSaveBenchmark(this, isGlipBaseline = wasBench)
        val fullReport = DiagnosticsLogger.buildShareableReport(this)
        saveReportToLocalArchive(
            context = this,
            header = "${snap.label} (${snap.durationMinutes}m • Peak ${String.format(Locale.US, "%.1f°C", snap.peakBatteryTempC)})",
            body = fullReport
        )

        cleanupResources()
        detachFloatingOverlayPill()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyDashboardStateChanged()
    }

    private fun cleanupResources() {
        val clientToClose = rtmpClient
        rtmpClient = null

        val auxListToClose = ArrayList(auxTargets)
        auxTargets.clear()

        val compToClose = companionTarget
        companionTarget = null
        splitEncodeSimulcastActive = false

        val audioToClose = audioEngine
        audioEngine = null

        val videoToClose = videoPipeline
        videoPipeline = null

        val projToClose = mediaProjection
        mediaProjection = null

        val wlToRelease = wakeLock
        wakeLock = null

        val wifiToRelease = wifiLock
        wifiLock = null

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
                if (wifiToRelease?.isHeld == true) wifiToRelease.release()
            } catch (_: Exception) {
            }
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
                sock.tcpNoDelay = true
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

    // === 2-SECOND QUEUE-AWARE PING GUARD ABR 2.0 + ZOMBIE-SOCKET + YOUR EXACT THERMAL BITRATE RULES ===
    private fun runHealthAndRecoveryTick() {
        val (tempC, thermal) = DiagnosticsLogger.sampleDeviceHealth(this)
        val headroom = DiagnosticsLogger.lastThermalHeadroom
        val nowElapsed = SystemClock.elapsedRealtime()

        if (broadcastActive) {
            val client = rtmpClient
            val vPipe = videoPipeline
            if (client != null && vPipe != null) {
                val bridgeFrames = RtmpTelemetryBridge.readCacheItemsCount(client)
                val qFrames = if (bridgeFrames >= 0) bridgeFrames else inFlightVideoFrames.get().coerceAtLeast(0)
                val qCap = RtmpTelemetryBridge.readCacheSizeCapacity(client).coerceAtLeast(60)
                val bridgeBytes = RtmpTelemetryBridge.readSentQueueBytes(client)
                val qBytes = if (bridgeBytes >= 0L) bridgeBytes else inFlightVideoBytes.get().coerceAtLeast(0L)
                var rttMs = RtmpTelemetryBridge.readRttMs(client)
                val droppedVideo = RtmpTelemetryBridge.readDroppedVideoFrames(client)
                val sentKbps = RtmpTelemetryBridge.readSentBitrateKbps(client)

                DiagnosticsLogger.lastQueueFrames = qFrames
                DiagnosticsLogger.lastQueueCapacity = qCap
                DiagnosticsLogger.lastQueueBytes = qBytes
                DiagnosticsLogger.lastSendLatencyMs = lastVideoSendLatencyMs
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
                // but outbound sent throughput is 0 kbps with a backed-up queue or high send latency for 3 ticks (6s),
                // force an immediate clean reconnect!
                if (rtmpConnected && vPipe.actualEncodedFps >= 15) {
                    val isBackedUpAndStalled = (sentKbps in 0..5) &&
                        ((qFrames >= 25) || (qBytes > 1_200_000L) || (droppedDelta >= 10L) || (lastVideoSendLatencyMs > 900L))
                    if (isBackedUpAndStalled) {
                        zeroThroughputStallTicks++
                        if (zeroThroughputStallTicks >= 3) {
                            zeroThroughputStallTicks = 0
                            DiagnosticsLogger.log(
                                "ZOMBIE_SOCKET",
                                "Detected silent Wi-Fi/router stall (0 kbps outbound for 6s with queued frames); forcing clean RTMPS reconnect.",
                                "qFrames=$qFrames qKB=${qBytes / 1024} dropsDelta=$droppedDelta sendMs=$lastVideoSendLatencyMs"
                            )
                            scheduleSilentReconnect("Silent socket stall (0 kbps for 6s)", connectionSessionGen.get())
                        }
                    } else {
                        zeroThroughputStallTicks = 0
                    }
                } else {
                    zeroThroughputStallTicks = 0
                }

                // === YOUR EXACT THERMAL & ABR BITRATE RULES (#26) ===
                // 1. NEVER trim bitrate for heat while streaming at 60 FPS!
                // 2. Only step bitrate down (-40%) when Severe warmth (or manual 30FPS Cool) steps a 60 FPS stream down to 30 FPS.
                // 3. Apply linked scaling across both Primary and Companion encoders when Dual-Format is active.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val needModerateRelief = thermal >= PowerManager.THERMAL_STATUS_MODERATE &&
                        (tempC >= 44.0f || headroom >= 0.90f)
                    val cooledFromModerateRelief = thermal < PowerManager.THERMAL_STATUS_MODERATE ||
                        (tempC <= 43.2f && (headroom < 0f || headroom < 0.84f))

                    if (needModerateRelief && !vPipe.thermalModerateCoolerActive) {
                        vPipe.thermalModerateCoolerActive = true
                    } else if (cooledFromModerateRelief && vPipe.thermalModerateCoolerActive) {
                        vPipe.thermalModerateCoolerActive = false
                    }

                    if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
                        severeThermalLastSeenElapsedMs = nowElapsed
                        if (thermal >= PowerManager.THERMAL_STATUS_CRITICAL &&
                            companionTarget != null &&
                            !vPipe.companionPausedByThermal
                        ) {
                            vPipe.companionPausedByThermal = true
                            DiagnosticsLogger.log(
                                "THERMAL",
                                "Phone reached Critical warmth; paused secondary 9:16 Companion encode to protect Primary stream."
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
                                "Phone reached Severe warmth; stepped 60 FPS stream to 30 FPS & ${propMbps} Mbps (-40% encoder heat)."
                            )
                        }
                    } else if (thermal <= PowerManager.THERMAL_STATUS_MODERATE) {
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

                // Exact Thermal Ceiling: 60% ONLY when a 60 FPS stream is throttled to 30 FPS; 100% at all 60 FPS times!
                val is30FpsCooldownActive = vPipe.thermalThrottle30Fps && activeSettings.fps > 30
                val newThermalCeilingBps = if (is30FpsCooldownActive) {
                    max(minAdaptiveBitrateBps, (maxConfiguredBitrateBps * 60) / 100)
                } else {
                    maxConfiguredBitrateBps
                }
                activeThermalCeilingBps = newThermalCeilingBps

                // Linked Dual-Encoder Thermal Governor (#26)
                if (is30FpsCooldownActive && !wasThermalThrottledLastTick) {
                    wasThermalThrottledLastTick = true
                    if (activeBitrateBps > newThermalCeilingBps) {
                        activeBitrateBps = newThermalCeilingBps
                        vPipe.setBitrateOnFly(activeBitrateBps)
                    }
                    val scaledCompBps = max(1_200_000, (maxConfiguredCompanionBps * 60) / 100)
                    vPipe.setCompanionBitrateOnFly(scaledCompBps)
                } else if (!is30FpsCooldownActive && wasThermalThrottledLastTick) {
                    wasThermalThrottledLastTick = false
                    vPipe.setCompanionBitrateOnFly(maxConfiguredCompanionBps)
                    // If Ping Guard ABR is OFF (or network is calm with 0 drops), restore 100% primary bitrate immediately
                    if (!activeSettings.adaptiveBitrate || (droppedDelta == 0L && qFrames <= 5)) {
                        activeBitrateBps = maxConfiguredBitrateBps
                        vPipe.setBitrateOnFly(activeBitrateBps)
                    }
                }

                // Queue-Aware Ping Guard ABR 2.0 (#26: never drops from background RTT probes when qFrames <= 8 & droppedDelta == 0)
                if (activeSettings.adaptiveBitrate && rtmpConnected) {
                    val queueCongested = (qCap > 0 && qFrames > (qCap * 35) / 100) ||
                        (qFrames > 35) ||
                        (qBytes > 1_600_000L) ||
                        (droppedDelta > 2L) ||
                        (rttMs > 280L && (qFrames > 8 || droppedDelta > 0L || lastVideoSendLatencyMs > 180L))

                    val queueCalm = isQueueCalmForAbrRecovery(
                        qFrames = qFrames,
                        qBytes = qBytes,
                        droppedDelta = droppedDelta.toInt()
                    ) && (rttMs <= 0L || rttMs < 150L)

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
                    } else if (queueCalm && activeBitrateBps < activeThermalCeilingBps) {
                        calmTicksCount++
                        if (calmTicksCount >= 3) {
                            calmTicksCount = 0
                            val recovered = min(activeThermalCeilingBps, activeBitrateBps + 1_500_000)
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
        notifyDashboardStateChanged()
    }

    private fun updateEffectiveKeyboardBlurState() {
        val autoBlurWanted = activeSettings.autoBlurKeyboard && imeKeyboardVisible
        val shouldBlurBottomHalf = manualKeyboardBlurOverride || autoBlurWanted
        videoPipeline?.keyboardBlurActive = shouldBlurBottomHalf
    }

    // === 3-TAB FLOATING IN-GAME PILL (Short Closed Pill + Clean Aligned Grid Open Pill + Auto-Blur Keyboard Detector) ===
    @SuppressLint("ClickableViewAccessibility")
    private fun attachFloatingOverlayPill() {
        if (!isAccessibilityEnabled(this) || pillRootView != null) return
        val wm = windowManager ?: return

        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).roundToInt()
        val screenMinPx = min(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val expandedPanelWidthPx = min(dp(336), (screenMinPx * 0.92f).roundToInt().coerceAtLeast(dp(276)))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EC080E1A"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#4D00E5FF"))
            }
            setPadding(dp(10), dp(6), dp(10), dp(6))
            alpha = 0.94f
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
            text = "00:00 | Starting..."
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
            layoutParams = LinearLayout.LayoutParams(expandedPanelWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Top Quick-Action Bar (5 evenly aligned buttons, zero horizontal scroll)
        btnQuickMicMute = makePillMiniBtn("Mic:ON", "#10B981") { toggleMicMuteLive() }
        btnQuickGameMute = makePillMiniBtn("Game:ON", "#38BDF8") { toggleGameAudioMuteLive() }
        btnQuickScene = makePillMiniBtn("Scene:LIVE", "#A78BFA") { cycleSceneLive() }
        btnQuickMarkClip = makePillMiniBtn("★ Clip", "#F59E0B") { saveClipMarkerLive("In-Game ★ Clip") }
        val btnCollapse = makePillMiniBtn("✕", "#94A3B8") { togglePillDrawer(false) }

        drawer.addView(
            makeAlignedGridRow(
                btnQuickMicMute!!,
                btnQuickGameMute!!,
                btnQuickScene!!,
                btnQuickMarkClip!!,
                btnCollapse
            )
        )

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(4))
        }
        tabBtnAudio = makePillTabHeader("🎙️ Audio", true) { switchOverlayTab(0) }
        tabBtnVideo = makePillTabHeader("🎬 Video", false) { switchOverlayTab(1) }
        tabBtnNet = makePillTabHeader("📡 Bitrate/Net", false) { switchOverlayTab(2) }
        tabBar.addView(tabBtnAudio, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(3) })
        tabBar.addView(tabBtnVideo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(3) })
        tabBar.addView(tabBtnNet, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        drawer.addView(tabBar)

        val vScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        val panesHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // === TAB 0: 🎙️ AUDIO STUDIO + SOUNDSTAGE (Full-Width Aligned Grid Rows, Zero Cut-Off!) ===
        val audioPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
        }
        btnOvGender = makePillMiniBtn("Voice:NEUTRAL", "#00E5FF") { cycleVoiceGenderLive() }
        btnOvVoiceType = makePillMiniBtn("Type:SOL_LUXE", "#A78BFA") { cycleVoicePresetLive() }
        audioPane.addView(makeAlignedGridRow(btnOvGender!!, btnOvVoiceType!!))

        btnOvNsMode = makePillMiniBtn("NS:RNNOISE", "#10B981") { cycleNsModeLive() }
        btnOvNsLevel = makePillMiniBtn("Lv:2 (88%)", "#10B981") { cycleRnnoiseLive() }
        btnOvHpf = makePillMiniBtn("HPF:75Hz", "#38BDF8") { cycleHpfLive() }
        audioPane.addView(makeAlignedGridRow(btnOvNsMode!!, btnOvNsLevel!!, btnOvHpf!!))

        btnOvEq = makePillMiniBtn("EQ:ON", "#38BDF8") { toggleVoiceEqLive() }
        btnOvDeEss = makePillMiniBtn("DeEss:ON", "#38BDF8") { toggleDeEsserLive() }
        btnOvVelvet = makePillMiniBtn("Velvet:ON", "#F59E0B") { toggleVelvetLive() }
        btnOvAgc = makePillMiniBtn("AGC:ON", "#10B981") { toggleSlowAgcLive() }
        audioPane.addView(makeAlignedGridRow(btnOvEq!!, btnOvDeEss!!, btnOvVelvet!!, btnOvAgc!!))

        val btnMicDown = makePillMiniBtn("Mic −10%", "#94A3B8") { stepMicBoostLive(-10) }
        btnOvMicGainLabel = makePillMiniBtn("Mic:130%", "#00E5FF") { stepMicBoostLive(10) }
        val btnMicUp = makePillMiniBtn("Mic +10%", "#94A3B8") { stepMicBoostLive(10) }
        audioPane.addView(makeAlignedGridRow(btnMicDown, btnOvMicGainLabel!!, btnMicUp))

        val btnGameDown = makePillMiniBtn("Game −10%", "#94A3B8") { stepGameMixLive(-10) }
        btnOvGameGainLabel = makePillMiniBtn("Game:100%", "#38BDF8") { stepGameMixLive(10) }
        val btnGameUp = makePillMiniBtn("Game +10%", "#94A3B8") { stepGameMixLive(10) }
        audioPane.addView(makeAlignedGridRow(btnGameDown, btnOvGameGainLabel!!, btnGameUp))

        btnOvCinemaWide = makePillMiniBtn("3DWide:OFF", "#38BDF8") { toggleGameCinemaWideLive() }
        btnOvSubPunch = makePillMiniBtn("Sub:OFF", "#F59E0B") { toggleGameSubPunchLive() }
        btnOvFootstep = makePillMiniBtn("Foot:OFF", "#10B981") { toggleGameFootstepLive() }
        audioPane.addView(makeAlignedGridRow(btnOvCinemaWide!!, btnOvSubPunch!!, btnOvFootstep!!))

        btnOvBlastTamer = makePillMiniBtn("Blast:OFF", "#A78BFA") { toggleGameBlastTamerLive() }
        btnOvDucking = makePillMiniBtn("Duck:OFF", "#38BDF8") { cycleDuckingLive() }
        btnOvMicRoute = makePillMiniBtn("Mic:Phone Studio", "#E2E8F0") { cycleMicHardwareLive() }
        audioPane.addView(makeAlignedGridRow(btnOvBlastTamer!!, btnOvDucking!!, btnOvMicRoute!!))

        // === TAB 1: 🎬 VIDEO, FRAMING, OVERLAY LAYOUT & DUAL PRIVACY BLUR ===
        val videoPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        btnOvSceneSelect = makePillMiniBtn("Scene:LIVE", "#10B981") { cycleSceneLive() }
        btnOvFraming = makePillMiniBtn("Frame:FIT", "#00E5FF") { cycleFramingModeLive() }
        btnOvZoom = makePillMiniBtn("Zoom:108%", "#38BDF8") { cycleSmartZoomLive() }
        videoPane.addView(makeAlignedGridRow(btnOvSceneSelect!!, btnOvFraming!!, btnOvZoom!!))

        btnOvUpscaler = makePillMiniBtn("Scale:BICUBIC", "#38BDF8") { cycleUpscalerLive() }
        btnOvSharpen = makePillMiniBtn("Sharp:OFF", "#38BDF8") { toggleGpuSharpenLive() }
        btnOvFlip180 = makePillMiniBtn("Rot:0°", "#38BDF8") { toggleLandscapeFlipLive() }
        videoPane.addView(makeAlignedGridRow(btnOvUpscaler!!, btnOvSharpen!!, btnOvFlip180!!))

        btnOvFilmic = makePillMiniBtn("Filmic:OFF", "#A78BFA") { toggleCinematicFilmicLive() }
        btnOvVivid = makePillMiniBtn("Vivid:OFF", "#F59E0B") { toggleAmoledVividLive() }
        btnOvShadowBoost = makePillMiniBtn("Shadow+:OFF", "#10B981") { toggleShadowBoostLive() }
        btnOvColorTemp = makePillMiniBtn("Temp:NEUT", "#38BDF8") { cycleColorTempLive() }
        videoPane.addView(makeAlignedGridRow(btnOvFilmic!!, btnOvVivid!!, btnOvShadowBoost!!, btnOvColorTemp!!))

        btnOvOverlayPng = makePillMiniBtn(".PNG:OFF", "#10B981") { toggleOverlayPngLive() }
        btnOvOverlayPos = makePillMiniBtn("Logo:FULL", "#38BDF8") { cycleOverlayPositionLive() }
        btnOvLiveBanner = makePillMiniBtn("Banner:OFF", "#F59E0B") { toggleLiveBannerLive() }
        videoPane.addView(makeAlignedGridRow(btnOvOverlayPng!!, btnOvOverlayPos!!, btnOvLiveBanner!!))

        btnOvMinimapShield = makePillMiniBtn("MapBlur:OFF", "#A78BFA") { toggleMinimapShieldLive() }
        btnOvKeyboardBlur = makePillMiniBtn("KeyBlur:AUTO", "#00E5FF") { toggleManualKeyboardBlurLive() }
        videoPane.addView(makeAlignedGridRow(btnOvMinimapShield!!, btnOvKeyboardBlur!!))

        // === TAB 2: 📡 BITRATE / NET / LIVE TELEMETRY / STOP ===
        val netPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val btnBitDown = makePillMiniBtn("− Mbps", "#00E5FF") { stepLiveBitrate(-1) }
        tvOvBitrateReadout = makePillMiniBtn("20.0 Mbps", "#F8FAFC") {}
        val btnBitUp = makePillMiniBtn("+ Mbps", "#00E5FF") { stepLiveBitrate(1) }
        netPane.addView(makeAlignedGridRow(btnBitDown, tvOvBitrateReadout!!, btnBitUp))

        btnOvAbrToggle = makePillMiniBtn("ABR:ON", "#10B981") { toggleAbrLive() }
        btnOvThermal30Saver = makePillMiniBtn("30FPS Cool:AUTO", "#10B981") { toggleThermal30FpsSaverLive() }
        netPane.addView(makeAlignedGridRow(btnOvAbrToggle!!, btnOvThermal30Saver!!))

        tvOvFpsAndSocReadout = makePillMiniBtn("Stream: 60fps (Game: 60) • SoC: 58%", "#CBD5E1") {}
        tvOvQueueAndRttReadout = makePillMiniBtn("Queue: 0f (1ms) • Ping: --", "#38BDF8") {}
        netPane.addView(makeAlignedGridRow(tvOvFpsAndSocReadout!!))
        netPane.addView(makeAlignedGridRow(tvOvQueueAndRttReadout!!))

        tvOvSimulcastStatus = makePillMiniBtn("Targets: Primary", "#94A3B8") {}
        btnOvStopConfirm = makePillMiniBtn("⏹ Stop Stream", "#EF4444") { onStopButtonTappedInOverlay() }
        netPane.addView(makeAlignedGridRow(tvOvSimulcastStatus!!, btnOvStopConfirm!!))

        tabPaneAudio = audioPane
        tabPaneVideo = videoPane
        tabPaneNet = netPane

        panesHolder.addView(audioPane)
        panesHolder.addView(videoPane)
        panesHolder.addView(netPane)
        vScroll.addView(panesHolder)
        drawer.addView(vScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(148)))

        root.addView(drawer)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                val isIme = insets.isVisible(WindowInsets.Type.ime())
                if (isIme != imeKeyboardVisible) {
                    imeKeyboardVisible = isIme
                    updateEffectiveKeyboardBlurState()
                    if (pillExpanded) refreshExpandedControlsState()
                }
                insets
            }
        }

        val savedPos = loadOverlayPositionPrefs(this)
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
                    root.alpha = 0.97f
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
                        saveOverlayPositionPrefs(this, lp.x, lp.y)
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
            attachInvisibleImeDetectorView(wm)
            refreshExpandedControlsState()
            updatePillTelemetryUI()
            scheduleGhostFade()
        } catch (e: Exception) {
            DiagnosticsLogger.log("OVERLAY_ERR", "Could not attach floating pill (${e.javaClass.simpleName}).")
        }
    }

    /**
     * Attaches a 1px-wide non-focusable, non-touchable height-watcher overlay with SOFT_INPUT_ADJUST_RESIZE
     * so Auto-Blur Keyboard (#24) detects the keyboard popping up on 100% of Android 10-18 phones!
     */
    private fun attachInvisibleImeDetectorView(wm: WindowManager) {
        if (imeDetectorView != null) return
        try {
            var maxObservedHeight = 0
            val detector = View(this).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setOnApplyWindowInsetsListener { _, insets ->
                        val visible = insets.isVisible(WindowInsets.Type.ime())
                        if (visible != imeKeyboardVisible) {
                            imeKeyboardVisible = visible
                            updateEffectiveKeyboardBlurState()
                            if (pillExpanded) mainHandler.post { refreshExpandedControlsState() }
                        }
                        insets
                    }
                }
                addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                    val h = bottom - top
                    if (h > maxObservedHeight) maxObservedHeight = h
                    if (maxObservedHeight > 400) {
                        val shrunkByKeyboard = h < (maxObservedHeight * 0.80f)
                        if (shrunkByKeyboard != imeKeyboardVisible) {
                            imeKeyboardVisible = shrunkByKeyboard
                            updateEffectiveKeyboardBlurState()
                            if (pillExpanded) mainHandler.post { refreshExpandedControlsState() }
                        }
                    }
                }
            }
            @Suppress("DEPRECATION")
            val detLp = WindowManager.LayoutParams(
                1,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            wm.addView(detector, detLp)
            imeDetectorView = detector
        } catch (_: Exception) {
        }
    }

    private fun makeAlignedGridRow(vararg buttons: TextView): LinearLayout {
        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).roundToInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
            for ((idx, btn) in buttons.withIndex()) {
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < buttons.size - 1) rightMargin = dp(4)
                }
                btn.layoutParams = lp
                btn.gravity = Gravity.CENTER
                btn.maxLines = 1
                btn.ellipsize = TextUtils.TruncateAt.END
                addView(btn)
            }
        }
    }

    private fun makePillTabHeader(title: String, selected: Boolean, onClick: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).roundToInt()
        return TextView(this).apply {
            text = title
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(5), dp(6), dp(5))
            setTextColor(if (selected) Color.parseColor("#060B14") else Color.parseColor("#CBD5E1"))
            background = GradientDrawable().apply {
                setColor(if (selected) Color.parseColor("#00E5FF") else Color.parseColor("#182235"))
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
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(hexColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.0f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(5), dp(6), dp(5))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#151F30"))
                cornerRadius = dp(8).toFloat()
                setStroke(1, Color.parseColor("#3300E5FF"))
            }
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
            tv.setTextColor(if (sel) Color.parseColor("#060B14") else Color.parseColor("#CBD5E1"))
            (tv.background as? GradientDrawable)?.setColor(
                if (sel) Color.parseColor("#00E5FF") else Color.parseColor("#182235")
            )
        }
    }

    private fun togglePillDrawer(expand: Boolean) {
        pillExpanded = expand
        pillDrawerContainer?.visibility = if (expand) View.VISIBLE else View.GONE
        if (expand) {
            mainHandler.removeCallbacks(ghostAlphaRunnable)
            pillRootView?.alpha = 0.98f
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
        val det = imeDetectorView
        imeDetectorView = null
        if (det != null) {
            try { windowManager?.removeView(det) } catch (_: Exception) {}
        }
        val view = pillRootView ?: return
        pillRootView = null
        try { windowManager?.removeView(view) } catch (_: Exception) {}
    }

    /**
     * Checklist #36: Short Closed Pill (● 05:36 | 17.6M | 60fps | Mic:ON | 38.4°C)
     * that never cuts off on screen, while keeping SoC %, Game FPS, and Queue (ms) inside the Open Pill!
     */
    private fun updatePillTelemetryUI() {
        val statusTv = pillStatusText ?: return
        val dot = pillStatusDot ?: return

        val elapsedSec = if (DiagnosticsLogger.sessionStartMs > 0L) {
            max(0L, (System.currentTimeMillis() - DiagnosticsLogger.sessionStartMs) / 1000L)
        } else 0L
        val mm = elapsedSec / 60L
        val ss = elapsedSec % 60L
        val timeStr = String.format(Locale.US, "%02d:%02d", mm, ss)
        val liveTemp = CodecCapabilityProbe.readLiveHardwareHealth(this).batteryTempC
        val tempStr = String.format(Locale.US, "%.1f°C", liveTemp)

        if (benchmarkOnlyActive) {
            dot.setTextColor(Color.parseColor("#F59E0B"))
            statusTv.text = "BENCH $timeStr | $tempStr | RAM ${DiagnosticsLogger.peakTotalPssMb}MB"
            return
        }

        val vPipe = videoPipeline
        val aEng = audioEngine
        val mbps = (vPipe?.actualEncodedBitrateBps ?: 0) / 1_000_000.0
        val outFps = vPipe?.actualEncodedFps ?: 0
        val micMuted = aEng?.micDsp?.streamMicMuted ?: false
        val micShort = if (micMuted) "Mic:MUTE" else "Mic:ON"
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
        val summaryLine = "$timeStr | $netState | ${outFps}fps | $micShort | $tempStr$sceneTag"
        statusTv.text = summaryLine
        currentLiveStateLabel = "LIVE ($summaryLine)"

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
        btnOvHpf?.text = if (activeSettings.hpfCutoffHz == 0) "HPF:OFF" else "HPF:${activeSettings.hpfCutoffHz}Hz"
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
        btnOvMicRoute?.text = "Mic:${aEng?.shortMicPillLabel ?: "Phone Studio"}"

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
        btnOvOverlayPos?.text = "Logo:${activeSettings.overlayPosition}"
        btnOvLiveBanner?.text = if (activeSettings.liveBannerEnabled) "Banner:ON" else "Banner:OFF"
        btnOvMinimapShield?.text = if (activeSettings.minimapShieldEnabled) "Blur:${activeSettings.minimapShieldCorner.take(9)}" else "Blur:OFF"

        val kbBlurNow = vPipe?.keyboardBlurActive == true
        val kbLabel = when {
            manualKeyboardBlurOverride -> "KeyBlur:ON (Locked)"
            kbBlurNow -> "KeyBlur:ACTIVE"
            activeSettings.autoBlurKeyboard -> "KeyBlur:AUTO"
            else -> "KeyBlur:OFF"
        }
        btnOvKeyboardBlur?.text = kbLabel
        btnOvKeyboardBlur?.setTextColor(Color.parseColor(if (kbBlurNow) "#F59E0B" else "#00E5FF"))

        // Tab 2 (Net / Bitrate / Detailed Telemetry)
        tvOvBitrateReadout?.text = String.format(Locale.US, "%.1f Mbps", activeBitrateBps / 1_000_000.0)
        btnOvAbrToggle?.text = if (activeSettings.adaptiveBitrate) "ABR:ON" else "ABR:OFF"
        val saver30 = vPipe?.thermalThrottle30Fps == true
        btnOvThermal30Saver?.text = if (saver30) "30FPS Cool:ACTIVE" else "30FPS Cool:AUTO"
        btnOvThermal30Saver?.setTextColor(Color.parseColor(if (saver30) "#F59E0B" else "#10B981"))

        val outFps = vPipe?.actualEncodedFps ?: 0
        val gameFps = vPipe?.actualFreshCaptureFps ?: 0
        val socLoad = CodecCapabilityProbe.readLiveHardwareHealth(this).socThermalLoadPercent
        tvOvFpsAndSocReadout?.text = "Stream: ${outFps}fps (Game: $gameFps) • SoC: ${socLoad}%"

        val qFrames = max(0, DiagnosticsLogger.lastQueueFrames)
        val sendMs = max(0L, DiagnosticsLogger.lastSendLatencyMs)
        val rtt = DiagnosticsLogger.lastRttMs
        val pingStr = if (rtt > 0L) "${rtt}ms" else "--"
        tvOvQueueAndRttReadout?.text = "Queue: ${qFrames}f (${sendMs}ms) • Ping: $pingStr"

        val activeAuxCount = auxTargets.count { it.connected } + (if (companionTarget?.connected == true) 1 else 0)
        tvOvSimulcastStatus?.text = if (activeAuxCount > 0) {
            "Targets: Primary + $activeAuxCount"
        } else {
            "Targets: Primary Only"
        }
    }

    // === LIVE IN-GAME & DASHBOARD ACTION HANDLERS ===
    fun applyDashboardSettingsLive(updated: StreamSettings) {
        saveAndPushSettings(updated)
    }

    private fun saveAndPushSettings(updated: StreamSettings) {
        activeSettings = updated
        KeystoreVault.saveSettings(this, updated)
        audioEngine?.updateLiveSettings(updated)
        videoPipeline?.applyLiveVisualSettings(updated)
        updateEffectiveKeyboardBlurState()
        audioEngine?.let {
            DiagnosticsLogger.activeAudioDspDesc =
                "${it.micDsp.voiceGenderProfile}/${it.micDsp.voicePreset} • ${it.micDsp.activeDenoiserSummary} • ${updated.audioBitrateKbps}k AAC"
        }
        refreshExpandedControlsState()
        notifyDashboardStateChanged()
    }

    private fun toggleMicMuteLive() {
        val dsp = audioEngine?.micDsp ?: return
        dsp.streamMicMuted = !dsp.streamMicMuted
        refreshExpandedControlsState()
        updatePillTelemetryUI()
        updateStreamNotification(if (dsp.streamMicMuted) "LIVE (Stream Mic Muted)" else "LIVE Broadcast Active")
        notifyDashboardStateChanged()
    }

    private fun toggleGameAudioMuteLive() {
        val aEng = audioEngine ?: return
        aEng.gameStreamMuted = !aEng.gameStreamMuted
        refreshExpandedControlsState()
        notifyDashboardStateChanged()
    }

    private fun cycleSceneLive() {
        val vPipe = videoPipeline ?: return
        val next = when (vPipe.activeSceneMode) {
            "LIVE" -> "BRB"
            "BRB" -> "STARTING_SOON"
            else -> "LIVE"
        }
        vPipe.setSceneMode(next)
        audioEngine?.streamSceneIsLive = (next == "LIVE")
        if (activeSettings.brbMuteMicToo) {
            audioEngine?.micDsp?.streamMicMuted = (next != "LIVE")
        }
        if (next != "LIVE") {
            vPipe.setBitrateOnFly(min(activeBitrateBps, 1_500_000))
        } else {
            vPipe.setBitrateOnFly(activeBitrateBps)
        }
        refreshExpandedControlsState()
        updatePillTelemetryUI()
        notifyDashboardStateChanged()
    }

    private fun saveClipMarkerLive(label: String) {
        val elapsedSec = if (DiagnosticsLogger.sessionStartMs > 0L) {
            max(0L, (System.currentTimeMillis() - DiagnosticsLogger.sessionStartMs) / 1000L)
        } else 0L
        val stamp = DiagnosticsLogger.markClipHighlight(elapsedSec, label, this)
        Toast.makeText(this, "★ Saved Clip Highlight @ $stamp!", Toast.LENGTH_SHORT).show()
        refreshExpandedControlsState()
        notifyDashboardStateChanged()
    }

    private fun cycleVoiceGenderLive() {
        val order = MicDspProcessor.GENDER_PROFILE_ORDER
        val next = order[(order.indexOf(activeSettings.voiceGenderProfile) + 1) % order.size]
        saveAndPushSettings(MicDspProcessor.applyGenderProfileToSettings(activeSettings, next))
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
        val order = MicDspProcessor.HPF_CUTOFF_STEPS
        val curIdx = order.indexOf(activeSettings.hpfCutoffHz)
        val next = order[(curIdx + 1) % order.size]
        saveAndPushSettings(activeSettings.copy(hpfCutoffHz = next))
    }

    private fun toggleVoiceEqLive() = saveAndPushSettings(activeSettings.copy(voiceEqEnabled = !activeSettings.voiceEqEnabled))
    private fun toggleDeEsserLive() = saveAndPushSettings(activeSettings.copy(deEsserEnabled = !activeSettings.deEsserEnabled))
    private fun toggleVelvetLive() = saveAndPushSettings(activeSettings.copy(velvetWarmthEnabled = !activeSettings.velvetWarmthEnabled))
    private fun toggleSlowAgcLive() = saveAndPushSettings(activeSettings.copy(slowAgcEnabled = !activeSettings.slowAgcEnabled))

    private fun stepMicBoostLive(deltaPct: Int) {
        val next = (activeSettings.micGainPercent + deltaPct).coerceIn(50, 250)
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
        val order = VAD_DUCKING_STEPS
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

    /**
     * Checklist #22: Expands Zoom to 10 steps (100%..250%) and automatically activates SMART
     * framing whenever Zoom is tapped so the stream zooms immediately!
     */
    private fun cycleSmartZoomLive() {
        val order = SMART_ZOOM_STEPS
        val curIdx = order.indexOf(activeSettings.smartZoomPercent)
        val next = order[(curIdx + 1) % order.size]
        val nextFraming = if (next > 100) "SMART" else activeSettings.framingMode
        saveAndPushSettings(activeSettings.copy(smartZoomPercent = next, framingMode = nextFraming))
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

    private fun cycleOverlayPositionLive() {
        val order = OVERLAY_POSITION_ORDER
        val next = order[(order.indexOf(activeSettings.overlayPosition) + 1) % order.size]
        saveAndPushSettings(activeSettings.copy(overlayPosition = next, overlayImageEnabled = true))
    }

    private fun toggleLiveBannerLive() =
        saveAndPushSettings(activeSettings.copy(liveBannerEnabled = !activeSettings.liveBannerEnabled))

    private fun toggleMinimapShieldLive() {
        if (!activeSettings.minimapShieldEnabled) {
            saveAndPushSettings(activeSettings.copy(minimapShieldEnabled = true))
        } else {
            val zones = PRIVACY_BLUR_ZONES
            val idx = zones.indexOf(activeSettings.minimapShieldCorner)
            if (idx in 0 until zones.size - 1) {
                saveAndPushSettings(activeSettings.copy(minimapShieldCorner = zones[idx + 1]))
            } else {
                saveAndPushSettings(activeSettings.copy(minimapShieldEnabled = false, minimapShieldCorner = "TOP_RIGHT"))
            }
        }
    }

    private fun toggleManualKeyboardBlurLive() {
        manualKeyboardBlurOverride = !manualKeyboardBlurOverride
        updateEffectiveKeyboardBlurState()
        refreshExpandedControlsState()
    }

    private fun stepLiveBitrate(dir: Int) {
        val stepsKbps = MASTER_BITRATE_LADDER_KBPS
        val curKbps = activeBitrateBps / 1000
        val closestIdx = stepsKbps.indices.minByOrNull { abs(stepsKbps[it] - curKbps) } ?: 14
        val nextIdx = (closestIdx + dir).coerceIn(0, stepsKbps.size - 1)
        val nextKbps = stepsKbps[nextIdx]

        activeBitrateBps = nextKbps * 1000
        maxConfiguredBitrateBps = activeBitrateBps
        activeThermalCeilingBps = activeBitrateBps
        minAdaptiveBitrateBps = computeDynamicAbrFloorBps(maxConfiguredBitrateBps)
        videoPipeline?.setBitrateOnFly(activeBitrateBps)
        saveAndPushSettings(activeSettings.copy(targetBitrateKbps = nextKbps))
    }

    private fun toggleAbrLive() =
        saveAndPushSettings(activeSettings.copy(adaptiveBitrate = !activeSettings.adaptiveBitrate))

    private fun toggleThermal30FpsSaverLive() {
        val vPipe = videoPipeline ?: return
        userManuallyOverrode30FpsSaver = true
        val newThrottleState = !vPipe.thermalThrottle30Fps
        vPipe.thermalThrottle30Fps = newThrottleState
        wasThermalThrottledLastTick = newThrottleState
        if (newThrottleState && activeSettings.fps > 30) {
            val scaledBps = max(minAdaptiveBitrateBps, (maxConfiguredBitrateBps * 60) / 100)
            activeBitrateBps = scaledBps
            activeThermalCeilingBps = scaledBps
            vPipe.setBitrateOnFly(activeBitrateBps)
            vPipe.setCompanionBitrateOnFly(max(1_200_000, (maxConfiguredCompanionBps * 60) / 100))
        } else {
            activeBitrateBps = maxConfiguredBitrateBps
            activeThermalCeilingBps = maxConfiguredBitrateBps
            vPipe.setBitrateOnFly(activeBitrateBps)
            vPipe.setCompanionBitrateOnFly(maxConfiguredCompanionBps)
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
                btnOvStopConfirm?.text = "⏹ Stop Stream"
            }, 2500L)
        }
    }

    // === PER-SESSION GENERATION-GUARDED PRIMARY RTMP CALLBACK HANDLERS ===
    private fun onPrimaryConnectionStarted(sessionGen: Long) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) return
    }

    private fun onPrimaryConnectionSuccess(sessionGen: Long, sourceClient: RtmpClient?) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) {
            // GPT Audit Fix #1: Disconnect ONLY the stale sourceClient that fired this callback,
            // NEVER the current rtmpClient of a newer broadcast session!
            netExecutor.execute { try { sourceClient?.disconnect() } catch (_: Exception) {} }
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
            sourceClient?.setVideoInfo(
                ByteBuffer.wrap(sps),
                cachedPps?.let { ByteBuffer.wrap(it) },
                cachedVps?.let { ByteBuffer.wrap(it) }
            )
        }
        probeIngestTcpRttIfNeeded()
        mainHandler.post {
            updatePillTelemetryUI()
            notifyDashboardStateChanged()
        }
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
        mainHandler.post {
            updatePillTelemetryUI()
            notifyDashboardStateChanged()
        }
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

    // === NOTIFICATION, WAKELOCK & WIFI LOW-LATENCY LOCK HELPERS ===
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

    private fun acquireWifiLowLatencyLock() {
        try {
            if (wifiLock?.isHeld == true) return
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm.createWifiLock(lockMode, "SolStreamStudio::LowLatencyWifi").apply {
                setReferenceCounted(false)
                acquire()
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
        const val ACTION_START_GLIP_BENCH = "com.solgaming.solstreamstudio.ACTION_START_BENCHMARK"
        const val ACTION_STOP_GLIP_BENCH = "com.solgaming.solstreamstudio.ACTION_STOP_STREAM"
        const val ACTION_TOGGLE_MIC_MUTE = "com.solgaming.solstreamstudio.ACTION_TOGGLE_MIC_MUTE"
        const val ACTION_TOGGLE_BRB = "com.solgaming.solstreamstudio.ACTION_TOGGLE_BRB"
        const val ACTION_MARK_CLIP = "com.solgaming.solstreamstudio.ACTION_MARK_CLIP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val CHANNEL_ID = "solstream_live_channel"
        private const val NOTIF_ID = 1001
        private const val OVERLAY_PREFS = "solstream_overlay_ui_prefs"
        private const val KEY_OV_X = "ov_pos_x"
        private const val KEY_OV_Y = "ov_pos_y"
        private const val KEY_ARCHIVE_JSON = "archived_session_reports_json"

        val MASTER_BITRATE_LADDER_KBPS = intArrayOf(
            500, 800, 1000, 1500, 2000, 3000, 4500, 6000, 8000, 10000, 12000, 14000,
            16000, 18000, 20000, 24000, 30000, 35000, 40000, 50000, 65000, 80000, 100000
        )
        val COMPANION_BITRATE_STEPS_KBPS = intArrayOf(
            2000, 3000, 4000, 6000, 8000, 10000, 12000, 15000, 18000, 20000
        )
        val SMART_ZOOM_STEPS = listOf(100, 108, 115, 125, 135, 150, 175, 200, 225, 250)
        val VAD_DUCKING_STEPS = listOf(0, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70)
        val MIC_SYNC_DELAY_STEPS = listOf(0, 15, 30, 45, 60, 80, 100, 120, 150, 180, 200)
        val OVERLAY_POSITION_ORDER = listOf("FULL", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT")
        val PRIVACY_BLUR_ZONES = listOf(
            "TOP_RIGHT",
            "TOP_RIGHT_SMALL",
            "TOP_RIGHT_LARGE",
            "CENTER_BOX",
            "KEYBOARD",
            "TOP_LEFT",
            "BOTTOM_LEFT",
            "BOTTOM_RIGHT"
        )
        val MIC_GAIN_STEPS = intArrayOf(50, 75, 100, 125, 150, 175, 200, 225, 250)
        val GAME_GAIN_STEPS = intArrayOf(0, 25, 50, 75, 100, 125, 150, 175, 200, 225, 250)

        @Volatile var instance: SolStreamService? = null
        @Volatile var onStateChangedListener: (() -> Unit)? = null
        @Volatile var needsProjectionReapproval: Boolean = false
        @Volatile var currentLiveStateLabel: String = "IDLE (Ready)"

        val isBroadcastLive: Boolean
            get() = instance?.broadcastActive == true

        val isBenchmarkRunning: Boolean
            get() = instance?.benchmarkOnlyActive == true

        val isGlipBenchmarkRunning: Boolean
            get() = instance?.benchmarkOnlyActive == true

        fun pushLiveSettingsIfRunning(settings: StreamSettings) {
            val svc = instance ?: return
            if (svc.broadcastActive) {
                svc.mainHandler.post { svc.saveAndPushSettings(settings) }
            }
        }

        private fun loadOverlayPositionPrefs(context: Context): Pair<Int, Int> {
            val prefs = context.getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_OV_X, 24) to prefs.getInt(KEY_OV_Y, 120)
        }

        private fun saveOverlayPositionPrefs(context: Context, x: Int, y: Int) {
            context.getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_OV_X, x)
                .putInt(KEY_OV_Y, y)
                .apply()
        }

        internal fun saveReportToLocalArchive(context: Context, header: String, body: String) {
            try {
                val prefs = context.getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
                val arr = org.json.JSONArray(prefs.getString(KEY_ARCHIVE_JSON, "[]") ?: "[]")
                val entry = org.json.JSONObject().apply {
                    put("header", header)
                    put("body", body)
                }
                val updated = org.json.JSONArray()
                updated.put(entry)
                for (i in 0 until min(9, arr.length())) {
                    updated.put(arr.getJSONObject(i))
                }
                prefs.edit().putString(KEY_ARCHIVE_JSON, updated.toString()).apply()
            } catch (_: Exception) {
            }
        }

        internal fun loadLocalReportArchive(context: Context): List<Pair<String, String>> {
            return try {
                val prefs = context.getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
                val arr = org.json.JSONArray(prefs.getString(KEY_ARCHIVE_JSON, "[]") ?: "[]")
                val out = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    out.add(obj.optString("header", "Session") to obj.optString("body", ""))
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }

        internal fun clearLocalReportArchive(context: Context) {
            context.getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ARCHIVE_JSON)
                .apply()
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

        fun isAccessibilityConnected(context: Context): Boolean = isAccessibilityEnabled(context)

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
