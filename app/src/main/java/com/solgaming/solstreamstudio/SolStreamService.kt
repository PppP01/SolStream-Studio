/*
- ॐ नमः शिवाय (Om Namah Shivaya)
- SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
- Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
- Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.Manifest
import android.accessibilityservice.AccessibilityService
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
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.rtmp.rtmp.RtmpClient
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
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
    private val rttProbeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SolIngestRttProbe").apply { isDaemon = true }
    }
    private val checkpointExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SolCheckpointIo").apply { isDaemon = true }
    }

    @Volatile private var rtmpClient: RtmpClient? = null
    private val auxTargets = mutableListOf<SolAuxRtmpTarget>()
    @Volatile private var companionTarget: SolAuxRtmpTarget? = null
    @Volatile private var splitEncodeSimulcastActive = false

    private var activeVideoCodecEnum: VideoCodec = VideoCodec.H265
    private var activeNegotiatedWidth: Int = 1920
    private var activeNegotiatedHeight: Int = 1080
    private var activeNegotiatedFps: Int = 60

    @Volatile private var videoPipeline: SolVideoPipeline? = null
    @Volatile private var audioEngine: SolDualAudioEngine? = null
    @Volatile private var mediaProjection: MediaProjection? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var lastActiveNetworkHandle: Long = 0L
    @Volatile private var activeTeardownLatch: CountDownLatch? = null

    private var activeSettings: StreamSettings = StreamSettings()
    private var primaryStreamUrl: String = ""
    private var backupStreamUrl: String? = null
    private var primaryIngestHost: String = ""
    private var primaryIngestPort: Int = 443
    private var usingBackupUrl = false

    private val broadcastStarting = AtomicBoolean(false)
    @Volatile private var broadcastActive = false
    @Volatile private var benchmarkOnlyActive = false
    @Volatile private var rtmpConnected = false
    @Volatile private var rtmpConnectedSinceElapsedMs: Long = 0L
    @Volatile private var connectAttemptStartElapsedMs: Long = 0L
    private val reconnectScheduled = AtomicBoolean(false)
    private val connectionSessionGen = AtomicLong(0L)

    // Real-time outbound send-queue & live RtmpClient bitrate callback tracking
    private val inFlightVideoFrames = AtomicInteger(0)
    private val inFlightVideoBytes = AtomicLong(0L)
    @Volatile private var lastVideoSendLatencyMs: Long = 0L
    @Volatile private var lastCallbackBitrateKbps: Int = -1
    @Volatile private var lastCallbackBitrateElapsedMs: Long = 0L

    // Cached per-tick hardware snapshot (eliminates redundant main-thread Binder IPCs)
    @Volatile private var cachedLiveTempC: Float = 0f
    @Volatile private var cachedThermalPressurePct: Int = 0
    @Volatile private var cachedTotalRamGb: Int = 8
    @Volatile private var cachedAvailRamGb: Float = 2.0f

    // Process CPU & RAM Telemetry (tracked strictly separately from Android Thermal Pressure)
    private var prevProcessCpuTimeMs: Long = 0L
    private var prevProcessSampleWallMs: Long = 0L
    @Volatile private var lastAppCpuPercent: Int = 4
    @Volatile private var peakAppCpuPercent: Int = 4
    private var appCpuSamplesSum: Long = 0L
    private var appCpuSamplesCount: Long = 0L

    // Separate manual vs. automatic thermal cooldown states
    private var userManuallyOverrodeSharp = false
    private var manual30FpsSaverEnabled = false
    private var autoThermal30FpsActive = false
    private var autoThermalNative30BitrateTrimActive = false
    private var severeThermalEnteredElapsedMs = 0L
    private var stickySevereOverrideUntilElapsedMs = 0L

    // Preserves manual mic mute state across BRB / STARTING_SOON scene transitions
    private var micWasMutedBeforeSceneSwitch = false

    // Dual-Layer Keyboard Privacy Blur states (Auto IME detector + Manual 1-tap override)
    @Volatile private var imeKeyboardVisible = false
    @Volatile private var manualKeyboardBlurOverride = false

    @Volatile private var reconnectCount = 0
    private var maxConfiguredBitrateBps = 20_000_000
    private var maxConfiguredCompanionBps = 8_000_000
    private var minAdaptiveBitrateBps = 6_000_000
    private var activeBitrateBps = 20_000_000
    private var activeThermalCeilingBps = 20_000_000
    private var wasThermalThrottledLastTick = false
    private var calmTicksCount = 0
    private var zeroThroughputStallTicks = 0
    private var lastDroppedTotal = 0L
    private var cumulativeDroppedFramesBeforeReconnect = 0L
    private var lastRttProbeElapsedMs = 0L
    private var lastCheckpointSavedElapsedMs = 0L

    @Volatile private var cachedSps: ByteArray? = null
    @Volatile private var cachedPps: ByteArray? = null
    @Volatile private var cachedVps: ByteArray? = null

    private var windowManager: WindowManager? = null
    private var pillController: SolFloatingPillController? = null
    private var lastOverlayMicSwitchMs = 0L
    @Volatile private var lastClipMarkElapsedMs = 0L

    private var thermalListenerObj: Any? = null

    private val healthTickRunnable = object : Runnable {
        override fun run() {
            if (!broadcastActive && !benchmarkOnlyActive) return
            runHealthAndRecoveryTick()
            mainHandler.postDelayed(this, 2000L)
        }
    }

    /**
- Per-session primary RTMP callback wrapper holding a direct reference to the exact
- RtmpClient instance it was created for (clientRef), so a delayed callback from a stopped
- or replaced client can only disconnect its own old socket and never the active client.
     */
    private class PrimaryRtmpCallback(
        private val sessionGen: Long,
        private val service: SolStreamService
    ) : ConnectChecker {
        @Volatile var clientRef: RtmpClient? = null

        override fun onConnectionStarted(url: String) {
            service.onPrimaryConnectionStarted(sessionGen, clientRef)
        }

        override fun onConnectionSuccess() {
            service.onPrimaryConnectionSuccess(sessionGen, clientRef)
        }

        override fun onNewBitrate(bitrate: Long) {
            service.onPrimaryNewBitrate(sessionGen, clientRef, bitrate)
        }

        override fun onConnectionFailed(reason: String) {
            service.onPrimaryConnectionFailed(sessionGen, clientRef, reason)
        }

        override fun onDisconnect() {
            service.onPrimaryDisconnect(sessionGen, clientRef)
        }

        override fun onAuthError() {
            service.onPrimaryAuthError(sessionGen, clientRef)
        }

        override fun onAuthSuccess() {
            service.onPrimaryAuthSuccess(sessionGen)
        }
    }

    private val pillActions = object : PillActions {
        override fun getSnapshot(): PillTelemetrySnapshot = buildPillSnapshot()
        override fun onLiveStateLabelChanged(label: String) {
            currentLiveStateLabel = label
        }
        override fun onImeKeyboardVisibilityChanged(visible: Boolean) {
            imeKeyboardVisible = visible
            updateEffectiveKeyboardBlurState()
        }
        override fun onToggleMicMute() = toggleMicMuteLive()
        override fun onToggleGameMute() = toggleGameAudioMuteLive()
        override fun onCycleScene() = cycleSceneLive()
        override fun onMarkClip() = saveClipMarkerLive("In-Game ★ Clip")
        override fun onCycleVoiceGender() = cycleVoiceGenderLive()
        override fun onCycleVoicePreset() = cycleVoicePresetLive()
        override fun onCycleNsMode() = cycleNsModeLive()
        override fun onCycleRnnoiseLevel() = cycleRnnoiseLive()
        override fun onCycleHpf() = cycleHpfLive()
        override fun onToggleVoiceEq() = toggleVoiceEqLive()
        override fun onToggleDeEsser() = toggleDeEsserLive()
        override fun onToggleVelvet() = toggleVelvetLive()
        override fun onToggleSlowAgc() = toggleSlowAgcLive()
        override fun onStepMicBoost(deltaPct: Int) = stepMicBoostLive(deltaPct)
        override fun onStepGameMix(deltaPct: Int) = stepGameMixLive(deltaPct)
        override fun onToggleGameCinemaWide() = toggleGameCinemaWideLive()
        override fun onToggleGameSubPunch() = toggleGameSubPunchLive()
        override fun onToggleGameFootstep() = toggleGameFootstepLive()
        override fun onToggleGameBlastTamer() = toggleGameBlastTamerLive()
        override fun onCycleDucking() = cycleDuckingLive()
        override fun onCycleMicHardware() = cycleMicHardwareLive()
        override fun onCycleUpscaler() = cycleUpscalerLive()
        override fun onCycleFramingMode() = cycleFramingModeLive()
        override fun onCycleSmartZoom() = cycleSmartZoomLive()
        override fun onToggleCinematicFilmic() = toggleCinematicFilmicLive()
        override fun onToggleAmoledVivid() = toggleAmoledVividLive()
        override fun onToggleShadowBoost() = toggleShadowBoostLive()
        override fun onCycleColorTemp() = cycleColorTempLive()
        override fun onToggleLandscapeFlip() = toggleLandscapeFlipLive()
        override fun onToggleGpuSharpen() = toggleGpuSharpenLive()
        override fun onCycleOverlayPosition() = cycleOverlayPositionLive()
        override fun onToggleLiveBanner() = toggleLiveBannerLive()
        override fun onTriggerCleanScreenshot() = triggerCleanScreenshotLive()
        override fun onToggleHudTelemetry() = toggleHudTelemetryLive()
        override fun onToggleQrOverlay() = toggleQrOverlayLive()
        override fun onTogglePngTuber() = togglePngTuberLive()
        override fun onToggleMinimapShield() = toggleMinimapShieldLive()
        override fun onToggleManualKeyboardBlur() = toggleManualKeyboardBlurLive()
        override fun onCycleShortsAnchor() = cycleShortsAnchorLive()
        override fun onCycleShortsMinimapLoupe() = cycleShortsMinimapLoupeLive()
        override fun onCycleShortsScoreLoupe() = cycleShortsScoreLoupeLive()
        override fun onToggleShortsHeaderLayout() = toggleShortsHeaderLayoutLive()
        override fun onToggleLoupesPaused() = toggleLoupesPausedLive()
        override fun onStepLiveBitrate(dir: Int) = stepLiveBitrate(dir)
        override fun onToggleAbr() = toggleAbrLive()
        override fun onToggleThermal30FpsSaver() = toggleThermal30FpsSaverLive()
        override fun onStopConfirmed() = stopLiveBroadcast("Stopped from floating menu")
    }

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLogger.installPrivacySafeCrashHandler(applicationContext)
        instance = this
        activeSettings = KeystoreVault.loadSettings(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureNotificationChannel()
        if (!broadcastActive && !benchmarkOnlyActive) {
            try {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
            } catch (_: Exception) {
            }
        }
        registerThermalStatusListenerIfSupported()
        notifyDashboardStateChanged()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        notifyDashboardStateChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Zero UI inspection: strictly used for optional TYPE_ACCESSIBILITY_OVERLAY dock.
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
                    satisfyForegroundContractAndStopIfIdle("Screen capture permission was not granted.")
                }
            }
            ACTION_START_BENCHMARK, ACTION_START_GLIP_BENCH -> startPassiveCompetitorBenchmark()
            ACTION_STOP_STREAM, ACTION_STOP_GLIP_BENCH -> stopLiveBroadcast("Stopped by user")
            ACTION_TOGGLE_MIC_MUTE -> toggleMicMuteLive()
            ACTION_TOGGLE_BRB -> cycleSceneLive()
            ACTION_MARK_CLIP -> saveClipMarkerLive("Notification ★ Clip")
        }
        return START_NOT_STICKY
    }

    /**
- Mandatory Android 15 / 16 / 17 (API 35+) Foreground Service timeout handler:
- Saves the Session Report and stops the service cleanly so Google Play Vitals never flags an ANR.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        DiagnosticsLogger.log(
            "FGS_TIMEOUT",
            "Android OS signaled foreground service time limit (type=$fgsType); stopping stream safely."
        )
        stopLiveBroadcast("Android OS Foreground Service Timeout")
        stopSelf(startId)
    }

    private fun satisfyForegroundContractAndStopIfIdle(logMsg: String) {
        broadcastStarting.set(false)
        CodecCapabilityProbe.isBroadcastStartingOrLive = false
        DiagnosticsLogger.log("START_ABORT", logMsg)
        if (!broadcastActive && !benchmarkOnlyActive) {
            try {
                startForeground(NOTIF_ID, buildStreamNotification("SolStream Studio Ready"))
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
        }
        notifyDashboardStateChanged()
    }

    override fun onDestroy() {
        unregisterThermalStatusListenerIfSupported()
        stopLiveBroadcast("Service destroyed")
        mainHandler.removeCallbacksAndMessages(null)
        try { rttProbeExecutor.shutdownNow() } catch (_: Exception) {}
        try { checkpointExecutor.shutdownNow() } catch (_: Exception) {}
        if (instance === this) instance = null
        notifyDashboardStateChanged()
        super.onDestroy()
    }

    private fun notifyDashboardStateChanged() {
        mainHandler.post {
            try { onStateChangedListener?.invoke() } catch (_: Exception) {}
        }
    }

    private fun refreshCachedHardwareSnapshot(): LiveHardwareHealth {
        val h = CodecCapabilityProbe.readLiveHardwareHealth(this)
        cachedLiveTempC = h.batteryTempC
        cachedThermalPressurePct = h.thermalPressurePercent
        cachedTotalRamGb = h.totalRamGb.coerceAtLeast(2)
        cachedAvailRamGb = h.availRamGb
        return h
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
        if (broadcastActive || benchmarkOnlyActive || broadcastStarting.get()) return
        val hasMicPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPerm) {
            satisfyForegroundContractAndStopIfIdle("Benchmark aborted: Microphone permission missing.")
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
            notifyDashboardStateChanged()
            return
        }

        benchmarkOnlyActive = true
        needsProjectionReapproval = false
        currentLiveStateLabel = "BENCHMARK ACTIVE (Profiling Thermal & RAM)"
        userManuallyOverrodeSharp = false
        manual30FpsSaverEnabled = false
        autoThermal30FpsActive = false
        autoThermalNative30BitrateTrimActive = false
        severeThermalEnteredElapsedMs = 0L
        stickySevereOverrideUntilElapsedMs = 0L
        lastCheckpointSavedElapsedMs = SystemClock.elapsedRealtime()

        DiagnosticsLogger.startNewSession(
            this,
            "Competitor App Benchmark",
            "Passive Thermal & Memory Profiler (Zero Encoding)"
        )
        refreshCachedHardwareSnapshot()
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

    private fun isCurrentNetworkWifiOrEthernet(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
- Builds a fresh, clean RtmpClient instance configured with TLS Hostname Verification,
- active RTMP Ping RTT tracking, setCheckServerAlive(false) (disabling Pedro's ICMP probe),
- Network-Adaptive RTMP write chunk sizing (65535 Wi-Fi / 4096 Cellular & Custom),
- negotiated video resolution/FPS/codec, and cached SPS/PPS/VPS headers.
     */
    private fun buildConfiguredPrimaryRtmpClient(sessionGen: Long, targetUrl: String): RtmpClient {
        val cb = PrimaryRtmpCallback(sessionGen, this)
        val newClient = RtmpClient(cb)
        newClient.setLogs(false)
        newClient.setCheckServerAlive(false)
        newClient.setOnlyVideo(false)
        val chunkSize = resolveAdaptiveRtmpChunkSize(isCurrentNetworkWifiOrEthernet(), targetUrl)
        SolRtmpTelemetryBridge.configureClientSecurityPingsAndChunkSize(newClient, chunkSize)
        try { newClient.resizeCache(120) } catch (_: Throwable) {}
        SolRtmpTelemetryBridge.clearCacheSafely(newClient)
        newClient.setVideoCodec(activeVideoCodecEnum)
        newClient.setVideoResolution(activeNegotiatedWidth, activeNegotiatedHeight)
        newClient.setFps(activeNegotiatedFps)
        newClient.setAudioInfo(SolDualAudioEngine.SAMPLE_RATE, true)
        val sps = cachedSps
        if (sps != null) {
            try {
                newClient.setVideoInfo(
                    ByteBuffer.wrap(sps),
                    cachedPps?.let { ByteBuffer.wrap(it) },
                    cachedVps?.let { ByteBuffer.wrap(it) }
                )
            } catch (_: Exception) {
            }
        }
        cb.clientRef = newClient
        return newClient
    }

    private fun startLiveBroadcast(resultCode: Int, resultData: Intent) {
        if (broadcastActive || !broadcastStarting.compareAndSet(false, true)) return
        if (benchmarkOnlyActive) {
            stopLiveBroadcast("Switching to Live Broadcast")
            broadcastStarting.set(true)
        }

        CodecCapabilityProbe.isBroadcastStartingOrLive = true

        // Promote foreground service FIRST before any background wait or hardware setup
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
            broadcastStarting.set(false)
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            DiagnosticsLogger.log("FGS_ERR", "Foreground service promotion failed (${e.javaClass.simpleName}).")
            Toast.makeText(this, "Screen capture token expired. Please tap Go Live again.", Toast.LENGTH_LONG).show()
            notifyDashboardStateChanged()
            return
        }

        // Wait for any previous session's background teardown OFF the main thread (hard 5s timeout ceiling)
        val pendingLatch = activeTeardownLatch
        if (pendingLatch != null && pendingLatch.count > 0L) {
            Thread({
                val teardownCompleted = try {
                    pendingLatch.await(5000L, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    false
                }
                mainHandler.post {
                    if (!teardownCompleted) {
                        broadcastStarting.set(false)
                        CodecCapabilityProbe.isBroadcastStartingOrLive = false
                        DiagnosticsLogger.log(
                            "START_ABORT",
                            "Aborted new broadcast start: previous session teardown did not complete within 5s."
                        )
                        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
                        Toast.makeText(
                            this@SolStreamService,
                            "Previous stream is still finishing cleanup. Tap Go Live again in a moment.",
                            Toast.LENGTH_LONG
                        ).show()
                        notifyDashboardStateChanged()
                    } else {
                        continueStartLiveBroadcast(resultCode, resultData)
                    }
                }
            }, "SolStartTeardownWait").apply {
                isDaemon = true
                start()
            }
        } else {
            continueStartLiveBroadcast(resultCode, resultData)
        }
    }

    private fun continueStartLiveBroadcast(resultCode: Int, resultData: Intent) {
        if (broadcastActive || !broadcastStarting.get()) {
            broadcastStarting.set(false)
            if (!broadcastActive && !benchmarkOnlyActive) {
                CodecCapabilityProbe.isBroadcastStartingOrLive = false
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
                notifyDashboardStateChanged()
            }
            return
        }

        val (enforcedSettings, wasPlatformClamped) = KeystoreVault.enforcePlatformLimits(KeystoreVault.loadSettings(this))
        activeSettings = enforcedSettings
        if (wasPlatformClamped) {
            KeystoreVault.saveSettings(this, enforcedSettings)
        }

        needsProjectionReapproval = false
        userManuallyOverrodeSharp = false
        manual30FpsSaverEnabled = false
        autoThermal30FpsActive = false
        autoThermalNative30BitrateTrimActive = false
        micWasMutedBeforeSceneSwitch = false
        imeKeyboardVisible = false
        manualKeyboardBlurOverride = false
        severeThermalEnteredElapsedMs = 0L
        stickySevereOverrideUntilElapsedMs = 0L
        wasThermalThrottledLastTick = false
        lastRttProbeElapsedMs = 0L
        lastCheckpointSavedElapsedMs = SystemClock.elapsedRealtime()
        rtmpConnectedSinceElapsedMs = 0L
        connectAttemptStartElapsedMs = SystemClock.elapsedRealtime()
        inFlightVideoFrames.set(0)
        inFlightVideoBytes.set(0L)
        lastVideoSendLatencyMs = 0L
        lastCallbackBitrateKbps = -1
        lastCallbackBitrateElapsedMs = 0L
        prevProcessCpuTimeMs = Process.getElapsedCpuTime()
        prevProcessSampleWallMs = SystemClock.elapsedRealtime()
        lastAppCpuPercent = 4
        peakAppCpuPercent = 4
        appCpuSamplesSum = 0L
        appCpuSamplesCount = 0L
        cumulativeDroppedFramesBeforeReconnect = 0L
        SolRtmpTelemetryBridge.resetSessionByteCounter()

        val builtPrimaryUrl = KeystoreVault.buildActivePrimaryEndpoint(this, activeSettings)
        if (builtPrimaryUrl.isNullOrBlank()) {
            broadcastStarting.set(false)
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifyDashboardStateChanged()
            Toast.makeText(this, "Save your YouTube/RTMPS Stream Key in the Vault first.", Toast.LENGTH_LONG).show()
            return
        }

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
            broadcastStarting.set(false)
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            DiagnosticsLogger.log("PROJ_ERR", "MediaProjection returned null.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifyDashboardStateChanged()
            return
        }
        mediaProjection = projection
        acquirePartialWakeLock()
        if (activeSettings.wifiLowLatencyLock) {
            acquireWifiLowLatencyLock()
        }

        val originNs = System.nanoTime()
        val originUs = originNs / 1000L

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
            auxTargets.add(
                createAuxTarget(
                    name = simName,
                    fullUrl = simUrl,
                    codecKey = "H264",
                    sessionGen = mySessionGen,
                    width = activeSettings.width,
                    height = activeSettings.height,
                    fps = activeSettings.fps,
                    usesSharedPrimaryHeaders = true
                )
            )
            targetNames.add(simName)
        }

        val compEndpoint = if (activeSettings.dualFormatCompanionEnabled && !activeSettings.isVertical) {
            KeystoreVault.buildCompanionVerticalEndpoint(this)
        } else {
            null
        }
        val selectedCompReport = if (!compEndpoint.isNullOrBlank()) {
            scan.selectCompanionEncoder(primaryCodec = activeSettings.codec, requireH264Only = false)
        } else {
            null
        }

        if (!compEndpoint.isNullOrBlank() && selectedCompReport != null) {
            val (compAlignedW, compAlignedH) = selectedCompReport.alignDimensions(1080, 1920)
            val compFps = if (selectedCompReport.supports1080p60 && activeSettings.fps >= 60) 60 else 30
            companionTarget = createAuxTarget(
                name = "YT 9:16 Companion",
                fullUrl = compEndpoint,
                codecKey = selectedCompReport.codecKey,
                sessionGen = mySessionGen,
                width = compAlignedW,
                height = compAlignedH,
                fps = compFps,
                usesSharedPrimaryHeaders = false
            )
            targetNames.add("YT 9:16 Companion")
        } else if (activeSettings.simulcastSplitEncode &&
            auxTargets.isNotEmpty() &&
            !activeSettings.isVertical &&
            scan.selectCompanionEncoder(primaryCodec = activeSettings.codec, requireH264Only = true) != null
        ) {
            splitEncodeSimulcastActive = true
        }

        // Start DiagnosticsLogger session BEFORE pipeline init so [GL_INIT], [VIDEO_FMT], [GAME_AUDIO] & [AUDIO_ENC] stay logged
        DiagnosticsLogger.activeSimulcastDesc = targetNames.joinToString(" + ")
        DiagnosticsLogger.startNewSession(
            context = this,
            modeTitle = "Live Broadcast",
            pipelineDesc = "Initializing ${activeSettings.codec} ${activeSettings.width}x${activeSettings.height}@${activeSettings.fps}fps...",
            saveInitialCheckpoint = false
        )
        DiagnosticsLogger.activeOverlaysDesc = buildActiveOverlaysSummary(activeSettings)

        val sessionAuxList = auxTargets.toList()
        val sessionCompTarget = companionTarget

        val vPipeline = SolVideoPipeline(this).apply {
            onFatalPipelineError = { errMsg ->
                mainHandler.post {
                    if (shouldAcceptConnectionCallback(broadcastActive, mySessionGen, connectionSessionGen.get())) {
                        Toast.makeText(this@SolStreamService, errMsg, Toast.LENGTH_LONG).show()
                        stopLiveBroadcast("Video hardware error")
                    }
                }
            }
            onCompanionPipelineStopped = { reason ->
                mainHandler.post {
                    if (shouldAcceptConnectionCallback(broadcastActive, mySessionGen, connectionSessionGen.get())) {
                        val comp = companionTarget
                        companionTarget = null
                        comp?.stop()
                        splitEncodeSimulcastActive = false
                        DiagnosticsLogger.log("DUAL_STOP", "Secondary companion encoder stopped ($reason); Primary stream continues.")
                        pillController?.refreshExpandedControls()
                    }
                }
            }
        }

        val aEngine = SolDualAudioEngine(this).apply {
            onFatalAudioError = { errMsg ->
                mainHandler.post {
                    if (shouldAcceptConnectionCallback(broadcastActive, mySessionGen, connectionSessionGen.get())) {
                        Toast.makeText(
                            this@SolStreamService,
                            "Audio engine stopped unexpectedly ($errMsg). Ending stream so viewers don't get silent video.",
                            Toast.LENGTH_LONG
                        ).show()
                        stopLiveBroadcast("Fatal audio error: $errMsg")
                    }
                }
            }
        }

        val needSecondEncoder = (sessionCompTarget != null) || splitEncodeSimulcastActive
        val videoInfo = vPipeline.startPipeline(
            settings = activeSettings,
            mediaProjection = projection,
            originTimeNs = originNs,
            onProjectionStoppedBySystem = {
                mainHandler.post {
                    if (shouldAcceptConnectionCallback(broadcastActive, mySessionGen, connectionSessionGen.get())) {
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
                if (mySessionGen != connectionSessionGen.get()) {
                    return@startPipeline
                }
                val spsArr = copyToByteArray(sps)
                val ppsArr = pps?.let { copyToByteArray(it) }
                val vpsArr = vps?.let { copyToByteArray(it) }
                cachedSps = spsArr
                cachedPps = ppsArr
                cachedVps = vpsArr
                try {
                    this@SolStreamService.rtmpClient?.setVideoInfo(
                        ByteBuffer.wrap(spsArr),
                        ppsArr?.let { ByteBuffer.wrap(it) },
                        vpsArr?.let { ByteBuffer.wrap(it) }
                    )
                } catch (_: Exception) {
                }
                if (!splitEncodeSimulcastActive) {
                    for (aux in sessionAuxList) {
                        aux.sendVideoHeader(spsArr, ppsArr, vpsArr)
                    }
                }
            },
            onEncodedVideo = { bb, info ->
                if (!shouldAcceptConnectionCallback(broadcastActive, mySessionGen, connectionSessionGen.get())) {
                    return@startPipeline
                }
                val activeClient = this@SolStreamService.rtmpClient
                if (rtmpConnected && activeClient != null) {
                    inFlightVideoFrames.incrementAndGet()
                    inFlightVideoBytes.addAndGet(info.size.toLong())
                    val t0Ns = System.nanoTime()
                    try {
                        activeClient.sendVideo(bb, info)
                    } catch (_: Exception) {
                        // Guard hardware video drain loop against transient socket send races during disconnect/reconnect
                    } finally {
                        val elapsedMs = ((System.nanoTime() - t0Ns) / 1_000_000L).coerceAtLeast(0L)
                        lastVideoSendLatencyMs = elapsedMs
                        DiagnosticsLogger.lastSendLatencyMs = elapsedMs
                        inFlightVideoFrames.decrementAndGet()
                        inFlightVideoBytes.addAndGet(-info.size.toLong())
                    }
                }
                if (sessionAuxList.isNotEmpty() && !splitEncodeSimulcastActive) {
                    for (aux in sessionAuxList) {
                        aux.sendVideoBuffer(bb, info)
                    }
                }
            },
            enableDualCompanion = needSecondEncoder,
            onCompanionConfig = { sps, pps, vps ->
                if (mySessionGen != connectionSessionGen.get()) {
                    return@startPipeline
                }
                val spsArr = copyToByteArray(sps)
                val ppsArr = pps?.let { copyToByteArray(it) }
                val vpsArr = vps?.let { copyToByteArray(it) }
                if (splitEncodeSimulcastActive) {
                    for (aux in sessionAuxList) {
                        aux.sendVideoHeader(spsArr, ppsArr, vpsArr)
                    }
                } else {
                    sessionCompTarget?.sendVideoHeader(spsArr, ppsArr, vpsArr)
                }
            },
            onCompanionVideo = { bb, info ->
                if (!shouldAcceptConnectionCallback(broadcastActive, mySessionGen, connectionSessionGen.get())) {
                    return@startPipeline
                }
                if (splitEncodeSimulcastActive) {
                    for (aux in sessionAuxList) {
                        aux.sendVideoBuffer(bb, info)
                    }
                } else if (sessionCompTarget != null && sessionCompTarget.connected) {
                    sessionCompTarget.sendVideoBuffer(bb, info)
                }
            },
            companionIsLandscapeH264 = splitEncodeSimulcastActive
        )

        if (videoInfo == null) {
            broadcastStarting.set(false)
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            DiagnosticsLogger.activePipelineDesc = "Start Aborted (Video Encoder Rejected)"
            Toast.makeText(
                this,
                "Hardware video encoder rejected settings. Try 1080p60 or H.264.",
                Toast.LENGTH_LONG
            ).show()
            cleanupResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifyDashboardStateChanged()
            return
        }

        if (!videoInfo.companionActive) {
            if (companionTarget != null) {
                companionTarget?.stop()
                companionTarget = null
            }
            splitEncodeSimulcastActive = false
        }

        activeVideoCodecEnum = when (videoInfo.codecKey) {
            "HEVC" -> VideoCodec.H265
            "AV1" -> VideoCodec.AV1
            else -> VideoCodec.H264
        }
        activeNegotiatedWidth = videoInfo.outWidth
        activeNegotiatedHeight = videoInfo.outHeight
        activeNegotiatedFps = videoInfo.fps

        val (splitAlignedW, splitAlignedH) = (scan.h264Hardware?.alignDimensions(1920, 1080)) ?: (1920 to 1080)
        val splitNegotiatedFps = if (scan.h264Hardware?.supports1080p60 != false && videoInfo.fps >= 60) 60 else 30
        for (aux in sessionAuxList) {
            if (splitEncodeSimulcastActive) {
                aux.updateNegotiatedFormat(splitAlignedW, splitAlignedH, splitNegotiatedFps, "H264", sharedPrimaryHeaders = false)
            } else {
                aux.updateNegotiatedFormat(activeNegotiatedWidth, activeNegotiatedHeight, activeNegotiatedFps, videoInfo.codecKey, sharedPrimaryHeaders = true)
            }
        }
        if (sessionCompTarget != null) {
            val activeCompReport = scan.selectCompanionEncoder(primaryCodec = videoInfo.codecKey, requireH264Only = false)
                ?: selectedCompReport
            val (cW, cH) = activeCompReport?.alignDimensions(1080, 1920) ?: (1080 to 1920)
            val cFps = if (activeCompReport?.supports1080p60 != false && videoInfo.fps >= 60) 60 else 30
            sessionCompTarget.updateNegotiatedFormat(cW, cH, cFps, activeCompReport?.codecKey, sharedPrimaryHeaders = false)
        }

        val initialClient = buildConfiguredPrimaryRtmpClient(mySessionGen, primaryStreamUrl)
        rtmpClient = initialClient

        val audioOk = aEngine.startStreamingAudio(
            settings = activeSettings,
            mediaProjection = projection,
            originTimeUs = originUs,
            onStatusChanged = {
                mainHandler.post { pillController?.refreshExpandedControls() }
            },
            onEncodedAac = { aacBuf, aacInfo ->
                if (!shouldAcceptConnectionCallback(broadcastActive, mySessionGen, connectionSessionGen.get())) {
                    return@startStreamingAudio
                }
                vPipeline.liveVoiceSpeaking = !aEngine.micDsp.streamMicMuted && aEngine.micDsp.vadSpeechActive

                val activeClient = this@SolStreamService.rtmpClient
                if (rtmpConnected && activeClient != null) {
                    try {
                        activeClient.sendAudio(aacBuf, aacInfo)
                    } catch (_: Exception) {
                        // Guard AAC drain loop against transient socket send races during disconnect/reconnect
                    }
                }
                val vPts = vPipeline.lastSentVideoPtsUs
                if (vPts > 0L) {
                    DiagnosticsLogger.avDriftMs = (aacInfo.presentationTimeUs - vPts) / 1000L
                }
                if (sessionAuxList.isNotEmpty() || sessionCompTarget != null) {
                    for (aux in sessionAuxList) {
                        aux.sendAudioBuffer(aacBuf, aacInfo)
                    }
                    sessionCompTarget?.sendAudioBuffer(aacBuf, aacInfo)
                }
            }
        )

        if (!audioOk) {
            broadcastStarting.set(false)
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            DiagnosticsLogger.activePipelineDesc = "Start Aborted (Microphone Init Failed)"
            Toast.makeText(this, "Microphone initialization failed. Check Mic permission.", Toast.LENGTH_LONG).show()
            vPipeline.stopPipeline()
            cleanupResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifyDashboardStateChanged()
            return
        }

        videoPipeline = vPipeline
        audioEngine = aEngine
        broadcastActive = true
        broadcastStarting.set(false)
        currentLiveStateLabel = "CONNECTING TO INGEST..."

        maxConfiguredBitrateBps = min(
            activeSettings.targetBitrateKbps.coerceIn(500, 100_000) * 1000,
            KeystoreVault.maxVideoBitrateKbpsForDestination(activeSettings) * 1000
        )
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
        DiagnosticsLogger.activeOverlaysDesc = buildActiveOverlaysSummary(activeSettings)
        DiagnosticsLogger.updateActivePipelineAndCheckpoint(this, pipeSummary)
        val initHealth = refreshCachedHardwareSnapshot()
        vPipeline.liveHudTempC = initHealth.batteryTempC

        registerFastNetworkHandoverWatcher(mySessionGen)
        attachFloatingOverlayPill()
        updateStreamNotification("LIVE: ${videoInfo.outWidth}x${videoInfo.outHeight}@${videoInfo.fps} (${videoInfo.codecKey})")
        notifyDashboardStateChanged()

        connectRtmpAsync(primaryStreamUrl, mySessionGen, initialClient)
        for (aux in sessionAuxList) {
            aux.startAsync()
        }
        if (videoInfo.companionActive) {
            sessionCompTarget?.startAsync()
        }

        mainHandler.removeCallbacks(healthTickRunnable)
        mainHandler.postDelayed(healthTickRunnable, 2000L)
    }

    private fun createAuxTarget(
        name: String,
        fullUrl: String,
        codecKey: String,
        sessionGen: Long,
        width: Int,
        height: Int,
        fps: Int,
        usesSharedPrimaryHeaders: Boolean
    ): SolAuxRtmpTarget {
        return SolAuxRtmpTarget(
            name = name,
            fullUrl = fullUrl,
            codecKey = codecKey,
            sessionGen = sessionGen,
            videoWidth = width,
            videoHeight = height,
            videoFps = fps,
            usesSharedPrimaryHeaders = usesSharedPrimaryHeaders,
            isWifiOrEthernet = { isCurrentNetworkWifiOrEthernet() },
            isSessionStillValid = { gen, targetStopped ->
                shouldAcceptConnectionCallback(broadcastActive, gen, connectionSessionGen.get(), targetStopped)
            },
            fallbackPrimaryHeaders = {
                val s = cachedSps
                if (s != null) Triple(s, cachedPps, cachedVps) else null
            },
            postOnMain = { block -> mainHandler.post(block) },
            postDelayedOnMain = { delayMs, block -> mainHandler.postDelayed(block, delayMs) },
            onStateChanged = { pillController?.refreshExpandedControls() }
        )
    }

    /**
- Fast Network Handover Watcher (Wi-Fi <-> Cellular):
- Detects when Android switches default internet routes and triggers a fast 300 ms
- RTMP reconnect over the new network (re-evaluating adaptive chunk size).
     */
    private fun registerFastNetworkHandoverWatcher(sessionGen: Long) {
        unregisterFastNetworkHandoverWatcher()
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
                            "Active internet route switched to $pipeName; triggering fast 0.3s network handover..."
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

    private fun unregisterFastNetworkHandoverWatcher() {
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

    private fun computeNative30FpsSafeThermalFloorBps(): Int {
        val maxDim = max(activeNegotiatedWidth, activeNegotiatedHeight)
        return when {
            maxDim >= 2560 -> 12_000_000
            maxDim >= 1920 -> 8_000_000
            else -> maxConfiguredBitrateBps
        }
    }

    private fun copyToByteArray(src: ByteBuffer): ByteArray {
        val dup = src.duplicate()
        val out = ByteArray(dup.remaining())
        dup.get(out)
        return out
    }

    private fun connectRtmpAsync(url: String, sessionGen: Long, targetClient: RtmpClient) {
        connectAttemptStartElapsedMs = SystemClock.elapsedRealtime()
        Thread({
            if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get()) ||
                targetClient !== rtmpClient
            ) {
                return@Thread
            }
            try {
                val endpointLabel = if (usingBackupUrl) "Backup RTMPS (?backup=1)" else "Primary RTMPS"
                val chunkBytes = resolveAdaptiveRtmpChunkSize(isCurrentNetworkWifiOrEthernet(), url)
                DiagnosticsLogger.log(
                    "NETWORK",
                    "Connecting to $endpointLabel server (TLS Host Verified)...",
                    "chunk=${chunkBytes}B"
                )
                targetClient.connect(url)
            } catch (e: Exception) {
                if (targetClient === rtmpClient) {
                    DiagnosticsLogger.log("NET_ERR", "RTMPS connect exception (${e.javaClass.simpleName}).", e.message ?: "")
                    scheduleSilentReconnect("Connect exception", sessionGen)
                }
            }
        }, "SolRtmpConnectAttempt").apply {
            isDaemon = true
            start()
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
        rtmpConnectedSinceElapsedMs = 0L
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
                2 -> 2200L
                3 -> 3800L
                else -> 5500L
            }
        }

        val targetDesc = if (usingBackupUrl) "YouTube Backup Server (b.rtmps)" else "Primary Server"
        DiagnosticsLogger.log(
            "RECONNECT",
            "Network hiccup ($reason). Auto-reconnecting (#$reconnectCount) via $targetDesc in ${delayMs / 1000.0}s while keeping game capture alive."
        )
        mainHandler.post {
            pillController?.updateTelemetry()
            notifyDashboardStateChanged()
        }

        mainHandler.postDelayed({
            reconnectScheduled.set(false)
            if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get())) {
                return@postDelayed
            }
            val oldClient = rtmpClient
            cumulativeDroppedFramesBeforeReconnect += lastDroppedTotal
            lastDroppedTotal = 0L

            parseIngestHostAndPort(targetUrl)
            val freshClient = buildConfiguredPrimaryRtmpClient(sessionGen, targetUrl)
            rtmpClient = freshClient
            lastCallbackBitrateKbps = -1
            lastCallbackBitrateElapsedMs = 0L
            SolRtmpTelemetryBridge.resetSessionByteCounter()

            if (oldClient != null) {
                Thread({
                    SolRtmpTelemetryBridge.clearCacheSafely(oldClient)
                    try { oldClient.disconnect() } catch (_: Exception) {}
                }, "SolOldRtmpClose").apply {
                    isDaemon = true
                    start()
                }
            }

            connectRtmpAsync(targetUrl, sessionGen, freshClient)
        }, delayMs)
    }

    fun stopLiveBroadcast(reason: String = "Stopped by user") {
        val wasLive = broadcastActive
        val wasBench = benchmarkOnlyActive
        broadcastStarting.set(false)
        if (!wasLive && !wasBench) {
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            return
        }

        DiagnosticsLogger.lockFinalMicStatusBeforeStop()

        connectionSessionGen.incrementAndGet()
        broadcastActive = false
        benchmarkOnlyActive = false
        CodecCapabilityProbe.isBroadcastStartingOrLive = false
        rtmpConnected = false
        rtmpConnectedSinceElapsedMs = 0L
        reconnectScheduled.set(false)
        currentLiveStateLabel = "IDLE (Ready)"
        mainHandler.removeCallbacks(healthTickRunnable)
        unregisterFastNetworkHandoverWatcher()

        val liveHealth = refreshCachedHardwareSnapshot()
        val avgAppCpu = if (appCpuSamplesCount > 0L) (appCpuSamplesSum / appCpuSamplesCount).toInt() else lastAppCpuPercent
        val appRamMb = max(DiagnosticsLogger.peakTotalPssMb, DiagnosticsLogger.lastJavaHeapMb + DiagnosticsLogger.lastNativeHeapMb).coerceAtLeast(45)
        val totalUsedGb = (liveHealth.totalRamGb.toFloat() - liveHealth.availRamGb).coerceAtLeast(0.5f)
        val gameOsRamGb = (totalUsedGb - (appRamMb / 1024.0f)).coerceAtLeast(0.3f)

        DiagnosticsLogger.log(
            "RESOURCE_SPLIT",
            "Resource Telemetry: SolStream Process ~${avgAppCpu}% CPU (Peak ${peakAppCpuPercent}%) & ${appRamMb} MB RAM | Android Thermal Pressure: ${liveHealth.thermalPressurePercent}% | Game + OS RAM: ${String.format(Locale.US, "%.1f", gameOsRamGb)} GB (of ${liveHealth.totalRamGb} GB)."
        )
        DiagnosticsLogger.log("SESSION_END", "Stream ended ($reason).")
        val snap = DiagnosticsLogger.finishAndSaveBenchmark(this, isGlipBaseline = wasBench)
        val fullReport = DiagnosticsLogger.buildShareableReport(this)
        val peakTempTag = if (snap.peakBatteryTempC > 0f) {
            String.format(Locale.US, "%.1f°C", snap.peakBatteryTempC)
        } else {
            "N/A"
        }
        saveReportToLocalArchive(
            context = this,
            header = "${snap.label} (${snap.durationMinutes}m • Peak $peakTempTag)",
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

        val latch = CountDownLatch(1)
        activeTeardownLatch = latch

        Thread({
            try {
                if (clientToClose != null) {
                    SolRtmpTelemetryBridge.clearCacheSafely(clientToClose)
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
            } finally {
                latch.countDown()
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
        try {
            rttProbeExecutor.execute {
                if (!broadcastActive || !rtmpConnected) return@execute
                val sock = Socket()
                try {
                    sock.tcpNoDelay = true
                    val t0 = SystemClock.elapsedRealtime()
                    sock.connect(InetSocketAddress(host, port), 2500)
                    val rtt = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
                    if (DiagnosticsLogger.lastRttSourceLabel != "RTMP Ping") {
                        DiagnosticsLogger.lastRttMs = rtt
                        DiagnosticsLogger.lastRttSourceLabel = "TCP Connect"
                    }
                } catch (_: Exception) {
                } finally {
                    try { sock.close() } catch (_: Exception) {}
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    // === 2-SECOND QUEUE-AWARE PING GUARD ABR 2.0 + 60s AUTO-CHECKPOINT + THERMAL GOVERNOR ===
    private fun runHealthAndRecoveryTick() {
        val (tempC, thermal) = DiagnosticsLogger.sampleDeviceHealth(this)
        refreshCachedHardwareSnapshot()
        videoPipeline?.liveHudTempC = tempC
        val headroom = DiagnosticsLogger.lastThermalHeadroom
        val nowElapsed = SystemClock.elapsedRealtime()

        // 60-second single-slot storage auto-checkpoint (survives OS memory/thermal kills without touching 9-slot history)
        if ((broadcastActive || benchmarkOnlyActive) && nowElapsed - lastCheckpointSavedElapsedMs >= 60_000L) {
            lastCheckpointSavedElapsedMs = nowElapsed
            val appCtx = applicationContext
            try {
                checkpointExecutor.execute {
                    if (broadcastActive || benchmarkOnlyActive) {
                        DiagnosticsLogger.saveLiveCheckpoint(appCtx)
                    }
                }
            } catch (_: RejectedExecutionException) {
            }
        }

        // Measure per-process SolStream Studio CPU %
        val nowCpuMs = Process.getElapsedCpuTime()
        val deltaCpuMs = (nowCpuMs - prevProcessCpuTimeMs).coerceAtLeast(0L)
        val deltaWallMs = (nowElapsed - prevProcessSampleWallMs).coerceAtLeast(1L)
        prevProcessCpuTimeMs = nowCpuMs
        prevProcessSampleWallMs = nowElapsed
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val measuredAppCpu = ((deltaCpuMs * 100.0f) / (deltaWallMs * cores).toFloat()).roundToInt().coerceIn(1, 100)
        lastAppCpuPercent = measuredAppCpu
        if (measuredAppCpu > peakAppCpuPercent) peakAppCpuPercent = measuredAppCpu
        appCpuSamplesSum += measuredAppCpu.toLong()
        appCpuSamplesCount++

        if (broadcastActive) {
            if (rtmpConnected && reconnectCount > 0 &&
                rtmpConnectedSinceElapsedMs > 0L &&
                nowElapsed - rtmpConnectedSinceElapsedMs >= 30_000L
            ) {
                reconnectCount = 0
            }

            val client = rtmpClient
            val vPipe = videoPipeline
            if (client != null && vPipe != null) {
                val bridgeFrames = SolRtmpTelemetryBridge.readCacheItemsCount(client)
                val qFrames = if (bridgeFrames >= 0) bridgeFrames else inFlightVideoFrames.get().coerceAtLeast(0)
                val qCap = SolRtmpTelemetryBridge.readCacheSizeCapacity(client).coerceAtLeast(60)
                val bridgeBytes = SolRtmpTelemetryBridge.readSentQueueBytes(client)
                val qBytes = if (bridgeBytes >= 0L) bridgeBytes else inFlightVideoBytes.get().coerceAtLeast(0L)
                var rttMs = SolRtmpTelemetryBridge.readRttMs(client)
                val droppedVideo = SolRtmpTelemetryBridge.readDroppedVideoFrames(client)

                val sentKbps = if (nowElapsed - lastCallbackBitrateElapsedMs <= 4500L && lastCallbackBitrateKbps >= 0) {
                    lastCallbackBitrateKbps
                } else {
                    SolRtmpTelemetryBridge.readSentBitrateKbps(client)
                }

                DiagnosticsLogger.lastQueueFrames = qFrames
                DiagnosticsLogger.lastQueueCapacity = qCap
                DiagnosticsLogger.lastQueueBytes = qBytes
                DiagnosticsLogger.lastSendLatencyMs = lastVideoSendLatencyMs
                if (rttMs > 0L) {
                    DiagnosticsLogger.lastRttMs = rttMs
                    DiagnosticsLogger.lastRttSourceLabel = "RTMP Ping"
                } else {
                    probeIngestTcpRttIfNeeded()
                    rttMs = DiagnosticsLogger.lastRttMs
                }
                DiagnosticsLogger.droppedVideoFrames = (cumulativeDroppedFramesBeforeReconnect + droppedVideo).toInt()

                val droppedDelta = max(0L, droppedVideo - lastDroppedTotal)
                lastDroppedTotal = droppedVideo

                // 8.5s Connect-Timeout Watchdog
                if (!rtmpConnected && !reconnectScheduled.get() &&
                    connectAttemptStartElapsedMs > 0L &&
                    nowElapsed - connectAttemptStartElapsedMs >= 8_500L
                ) {
                    connectAttemptStartElapsedMs = nowElapsed
                    DiagnosticsLogger.log(
                        "CONN_TIMEOUT",
                        "RTMPS handshake timed out after 8.5s; cycling to fresh socket reconnect..."
                    )
                    scheduleSilentReconnect("Handshake timeout (8.5s)", connectionSessionGen.get())
                }

                // 6s Zombie-Socket Stall Watchdog
                if (rtmpConnected && vPipe.actualEncodedFps >= 15) {
                    val zeroSentConfirmed = (sentKbps in 0..5) &&
                        ((qFrames >= 25) || (qBytes > 1_200_000L) || (droppedDelta >= 10L))
                    val severeBlockingSendStall = (lastVideoSendLatencyMs > 1200L) && (qFrames >= 20 || droppedDelta >= 8L)
                    if (zeroSentConfirmed || severeBlockingSendStall) {
                        zeroThroughputStallTicks++
                        if (zeroThroughputStallTicks >= 3) {
                            zeroThroughputStallTicks = 0
                            DiagnosticsLogger.log(
                                "ZOMBIE_SOCKET",
                                "Detected silent Wi-Fi/router stall (6s outbound socket stall with queued frames); forcing clean RTMPS reconnect.",
                                "sentKbps=$sentKbps qFrames=$qFrames qKB=${qBytes / 1024} dropsDelta=$droppedDelta enqMs=$lastVideoSendLatencyMs"
                            )
                            scheduleSilentReconnect("Silent socket stall (6s)", connectionSessionGen.get())
                        }
                    } else {
                        zeroThroughputStallTicks = 0
                    }
                } else {
                    zeroThroughputStallTicks = 0
                }

                // Thermal & ABR Bitrate Governor (Hysteresis + Sticky Severe Guard)
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

                        val physicallyCooledUnderStickySevere =
                            (autoThermal30FpsActive || autoThermalNative30BitrateTrimActive) &&
                                severeThermalEnteredElapsedMs > 0L &&
                                (nowElapsed - severeThermalEnteredElapsedMs >= 90_000L) &&
                                tempC in 1.0f..42.6f &&
                                (headroom < 0f || headroom <= 0.88f) &&
                                thermal == PowerManager.THERMAL_STATUS_SEVERE

                        if (physicallyCooledUnderStickySevere) {
                            stickySevereOverrideUntilElapsedMs = nowElapsed + 60_000L
                            if (autoThermal30FpsActive) {
                                autoThermal30FpsActive = false
                                vPipe.thermalThrottle30Fps = manual30FpsSaverEnabled && activeNegotiatedFps > 30
                                if (!manual30FpsSaverEnabled) {
                                    DiagnosticsLogger.log(
                                        "THERMAL",
                                        "Physical temp cooled to ${String.format(Locale.US, "%.1f°C", tempC)}; restored full 60 FPS & 100% target bitrate."
                                    )
                                }
                            }
                            if (autoThermalNative30BitrateTrimActive) {
                                autoThermalNative30BitrateTrimActive = false
                                DiagnosticsLogger.log(
                                    "THERMAL",
                                    "Physical temp cooled to ${String.format(Locale.US, "%.1f°C", tempC)}; restored 100% target bitrate on 30 FPS stream."
                                )
                            }
                        } else {
                            val inStickyGrace = nowElapsed < stickySevereOverrideUntilElapsedMs &&
                                tempC <= 43.3f &&
                                (headroom < 0f || headroom <= 0.92f) &&
                                thermal == PowerManager.THERMAL_STATUS_SEVERE

                            if (!inStickyGrace) {
                                if (activeNegotiatedFps > 30) {
                                    if (!autoThermal30FpsActive) {
                                        autoThermal30FpsActive = true
                                        severeThermalEnteredElapsedMs = nowElapsed
                                        vPipe.thermalThrottle30Fps = true
                                        val propMbps = String.format(Locale.US, "%.1f", (maxConfiguredBitrateBps * 0.60) / 1_000_000.0)
                                        DiagnosticsLogger.log(
                                            "THERMAL",
                                            "Phone reached Severe warmth; stepped 60 FPS stream to 30 FPS & ${propMbps} Mbps (-40% encoder heat)."
                                        )
                                    }
                                } else {
                                    val safe30FloorBps = computeNative30FpsSafeThermalFloorBps()
                                    if (!autoThermalNative30BitrateTrimActive && maxConfiguredBitrateBps > safe30FloorBps) {
                                        autoThermalNative30BitrateTrimActive = true
                                        severeThermalEnteredElapsedMs = nowElapsed
                                        val trimmedBps = max(safe30FloorBps, (maxConfiguredBitrateBps * 75) / 100)
                                        val propMbps = String.format(Locale.US, "%.1f", trimmedBps / 1_000_000.0)
                                        DiagnosticsLogger.log(
                                            "THERMAL",
                                            "Phone reached Severe warmth on 30 FPS stream; trimmed high bitrate to ${propMbps} Mbps (-25% encoder heat)."
                                        )
                                    }
                                }
                            }
                        }
                    } else if (thermal <= PowerManager.THERMAL_STATUS_MODERATE) {
                        val minCooldownHoldMet = severeThermalEnteredElapsedMs == 0L ||
                            (nowElapsed - severeThermalEnteredElapsedMs >= 90_000L)
                        val physicallyCooled = if (tempC > 1.0f) {
                            tempC <= 42.8f && (headroom < 0f || headroom <= 0.88f)
                        } else {
                            thermal <= PowerManager.THERMAL_STATUS_LIGHT
                        }
                        val cooldownSatisfied = minCooldownHoldMet && physicallyCooled

                        if (autoThermal30FpsActive && cooldownSatisfied) {
                            autoThermal30FpsActive = false
                            vPipe.thermalThrottle30Fps = manual30FpsSaverEnabled && activeNegotiatedFps > 30
                            if (!manual30FpsSaverEnabled) {
                                DiagnosticsLogger.log(
                                    "THERMAL",
                                    "Device cooled safely (${String.format(Locale.US, "%.1f°C", tempC)}); restored full 60 FPS broadcast cadence & 100% target bitrate."
                                )
                            }
                        }
                        if (autoThermalNative30BitrateTrimActive && cooldownSatisfied) {
                            autoThermalNative30BitrateTrimActive = false
                            DiagnosticsLogger.log(
                                "THERMAL",
                                "Device cooled safely (${String.format(Locale.US, "%.1f°C", tempC)}); restored 100% target bitrate on 30 FPS stream."
                            )
                        }
                        if (vPipe.companionPausedByThermal && thermal <= PowerManager.THERMAL_STATUS_LIGHT) {
                            vPipe.companionPausedByThermal = false
                        }
                    }
                }

                val is60To30CooldownActive = (autoThermal30FpsActive || manual30FpsSaverEnabled) && activeNegotiatedFps > 30
                val isNative30TrimActive = (autoThermalNative30BitrateTrimActive || manual30FpsSaverEnabled) && activeNegotiatedFps <= 30

                val newThermalCeilingBps = when {
                    is60To30CooldownActive -> max(minAdaptiveBitrateBps, (maxConfiguredBitrateBps * 60) / 100)
                    isNative30TrimActive -> {
                        val safe30Floor = computeNative30FpsSafeThermalFloorBps()
                        if (maxConfiguredBitrateBps > safe30Floor) {
                            max(safe30Floor, (maxConfiguredBitrateBps * 75) / 100)
                        } else {
                            maxConfiguredBitrateBps
                        }
                    }
                    else -> maxConfiguredBitrateBps
                }
                activeThermalCeilingBps = newThermalCeilingBps

                val anyThermalBitrateThrottle = is60To30CooldownActive || (isNative30TrimActive && newThermalCeilingBps < maxConfiguredBitrateBps)

                if (anyThermalBitrateThrottle && !wasThermalThrottledLastTick) {
                    wasThermalThrottledLastTick = true
                    if (activeBitrateBps > newThermalCeilingBps && vPipe.activeSceneMode == "LIVE") {
                        activeBitrateBps = newThermalCeilingBps
                        vPipe.setBitrateOnFly(activeBitrateBps)
                    }
                    val compRatio = if (is60To30CooldownActive) 60 else 75
                    val scaledCompBps = max(1_200_000, (maxConfiguredCompanionBps * compRatio) / 100)
                    vPipe.setCompanionBitrateOnFly(scaledCompBps)
                } else if (!anyThermalBitrateThrottle && wasThermalThrottledLastTick) {
                    wasThermalThrottledLastTick = false
                    vPipe.setCompanionBitrateOnFly(maxConfiguredCompanionBps)
                    if (!activeSettings.adaptiveBitrate || (droppedDelta == 0L && qFrames <= 5)) {
                        activeBitrateBps = maxConfiguredBitrateBps
                        if (vPipe.activeSceneMode == "LIVE") {
                            vPipe.setBitrateOnFly(activeBitrateBps)
                        }
                    }
                }

                // Queue-Aware Ping Guard ABR 2.0
                if (activeSettings.adaptiveBitrate && rtmpConnected && vPipe.activeSceneMode == "LIVE") {
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
                                SolRtmpTelemetryBridge.clearCacheSafely(client)
                                vPipe.requestSyncFrame()
                            }
                            DiagnosticsLogger.log(
                                "PING_GUARD_ABR",
                                "Trimmed stream bitrate to ${String.format(Locale.US, "%.1f", activeBitrateBps / 1_000_000.0)} Mbps so game ping stays flat.",
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

        pillController?.updateTelemetry()
        notifyDashboardStateChanged()
    }

    private fun updateEffectiveKeyboardBlurState() {
        val autoBlurWanted = activeSettings.autoBlurKeyboard && imeKeyboardVisible
        val shouldBlurBottomHalf = manualKeyboardBlurOverride || autoBlurWanted
        videoPipeline?.keyboardBlurActive = shouldBlurBottomHalf
    }

    private fun attachFloatingOverlayPill() {
        if (pillController != null) return
        val wm = windowManager ?: return
        pillController = SolFloatingPillController(
            context = this,
            windowManager = wm,
            mainHandler = mainHandler,
            actions = pillActions
        ).apply { attach() }
    }

    private fun detachFloatingOverlayPill() {
        val ctrl = pillController ?: return
        pillController = null
        ctrl.detach()
    }

    private fun buildPillSnapshot(): PillTelemetrySnapshot {
        val vPipe = videoPipeline
        val aEng = audioEngine
        val elapsedSec = if (DiagnosticsLogger.sessionStartMs > 0L) {
            max(0L, (System.currentTimeMillis() - DiagnosticsLogger.sessionStartMs) / 1000L)
        } else 0L
        val liveTemp = if (cachedLiveTempC > 0f) cachedLiveTempC else DiagnosticsLogger.startTempC
        val appRamMb = max(
            DiagnosticsLogger.peakTotalPssMb,
            DiagnosticsLogger.lastJavaHeapMb + DiagnosticsLogger.lastNativeHeapMb
        ).coerceAtLeast(45)
        val activeAuxCount = auxTargets.count { it.connected } + (if (companionTarget?.connected == true) 1 else 0)

        return PillTelemetrySnapshot(
            isBenchmarkOnly = benchmarkOnlyActive,
            elapsedSec = elapsedSec,
            liveTempC = liveTemp,
            peakPssMb = DiagnosticsLogger.peakTotalPssMb,
            rtmpConnected = rtmpConnected,
            worstThermalState = DiagnosticsLogger.worstThermalState,
            encodedBitrateBps = vPipe?.actualEncodedBitrateBps ?: 0,
            encodedFps = vPipe?.actualEncodedFps ?: 0,
            freshCaptureFps = vPipe?.actualFreshCaptureFps ?: 0,
            micMuted = aEng?.micDsp?.streamMicMuted ?: false,
            gameMuted = aEng?.gameStreamMuted ?: false,
            sceneMode = vPipe?.activeSceneMode ?: "LIVE",
            clipCount = DiagnosticsLogger.getClipHighlightCount(),
            activeSettings = activeSettings,
            shortMicPillLabel = aEng?.shortMicPillLabel ?: "Phone Studio",
            keyboardBlurActive = vPipe?.keyboardBlurActive == true,
            manualKeyboardBlurOverride = manualKeyboardBlurOverride,
            activeBitrateBps = activeBitrateBps,
            thermalThrottle30Fps = vPipe?.thermalThrottle30Fps == true,
            manual30FpsSaverEnabled = manual30FpsSaverEnabled,
            autoThermalNative30BitrateTrimActive = autoThermalNative30BitrateTrimActive,
            thermalPressurePercent = cachedThermalPressurePct,
            queueFrames = DiagnosticsLogger.lastQueueFrames,
            enqueueLatencyMs = DiagnosticsLogger.lastSendLatencyMs,
            rttMs = DiagnosticsLogger.lastRttMs,
            rttSourceLabel = DiagnosticsLogger.lastRttSourceLabel,
            appCpuPercent = lastAppCpuPercent,
            appRamMb = appRamMb,
            totalRamGb = cachedTotalRamGb,
            availRamGb = cachedAvailRamGb,
            activeAuxCount = activeAuxCount,
            loupesPaused = vPipe?.liveShortsLoupesPaused == true
        )
    }

    // === LIVE IN-GAME & DASHBOARD ACTION HANDLERS ===
    fun applyDashboardSettingsLive(updated: StreamSettings) {
        saveAndPushSettings(updated)
    }

    private fun saveAndPushSettings(updated: StreamSettings) {
        val safe = KeystoreVault.enforcePlatformLimits(updated).first
        activeSettings = safe
        KeystoreVault.saveSettings(this, safe)
        audioEngine?.updateLiveSettings(safe)

        val vPipe = videoPipeline
        vPipe?.applyLiveVisualSettings(safe)
        if (broadcastActive && vPipe != null) {
            val requestedBps = min(
                safe.targetBitrateKbps.coerceIn(500, 100_000) * 1000,
                KeystoreVault.maxVideoBitrateKbpsForDestination(safe) * 1000
            )
            if (requestedBps != maxConfiguredBitrateBps) {
                maxConfiguredBitrateBps = requestedBps
                minAdaptiveBitrateBps = computeDynamicAbrFloorBps(maxConfiguredBitrateBps)
                val targetNow = if (wasThermalThrottledLastTick) {
                    min(requestedBps, activeThermalCeilingBps)
                } else {
                    activeThermalCeilingBps = requestedBps
                    requestedBps
                }
                activeBitrateBps = targetNow
                if (vPipe.activeSceneMode == "LIVE") {
                    vPipe.setBitrateOnFly(targetNow)
                    activeBitrateBps = vPipe.currentBitrateBps
                }
            }
        }

        updateEffectiveKeyboardBlurState()
        DiagnosticsLogger.activeOverlaysDesc = buildActiveOverlaysSummary(safe)
        audioEngine?.let {
            DiagnosticsLogger.activeAudioDspDesc =
                "${it.micDsp.voiceGenderProfile}/${it.micDsp.voicePreset} • ${it.micDsp.activeDenoiserSummary} • ${safe.audioBitrateKbps}k AAC"
        }
        pillController?.refreshExpandedControls()
        notifyDashboardStateChanged()
    }

    private fun toggleMicMuteLive() {
        val dsp = audioEngine?.micDsp ?: return
        val newMuted = !dsp.streamMicMuted
        dsp.streamMicMuted = newMuted
        if (newMuted) {
            videoPipeline?.liveVoiceSpeaking = false
        }
        micWasMutedBeforeSceneSwitch = newMuted
        pillController?.refreshExpandedControls()
        pillController?.updateTelemetry()
        updateStreamNotification(if (newMuted) "LIVE (Stream Mic Muted)" else "LIVE Broadcast Active")
        notifyDashboardStateChanged()
    }

    private fun toggleGameAudioMuteLive() {
        val aEng = audioEngine ?: return
        aEng.gameStreamMuted = !aEng.gameStreamMuted
        pillController?.refreshExpandedControls()
        notifyDashboardStateChanged()
    }

    private fun cycleSceneLive() {
        val vPipe = videoPipeline ?: return
        val prevScene = vPipe.activeSceneMode
        val next = when (prevScene) {
            "LIVE" -> "BRB"
            "BRB" -> "STARTING_SOON"
            else -> "LIVE"
        }
        vPipe.setSceneMode(next)
        audioEngine?.streamSceneIsLive = (next == "LIVE")
        if (activeSettings.brbMuteMicToo) {
            val dsp = audioEngine?.micDsp
            if (dsp != null) {
                if (prevScene == "LIVE" && next != "LIVE") {
                    micWasMutedBeforeSceneSwitch = dsp.streamMicMuted
                    dsp.streamMicMuted = true
                    vPipe.liveVoiceSpeaking = false
                } else if (next == "LIVE") {
                    dsp.streamMicMuted = micWasMutedBeforeSceneSwitch
                }
            }
        }
        if (next != "LIVE") {
            vPipe.setBitrateOnFly(min(activeBitrateBps, 1_500_000))
        } else {
            vPipe.setBitrateOnFly(activeBitrateBps)
        }
        pillController?.refreshExpandedControls()
        pillController?.updateTelemetry()
        notifyDashboardStateChanged()
    }

    private fun saveClipMarkerLive(label: String) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - lastClipMarkElapsedMs < 1500L) return
        lastClipMarkElapsedMs = nowElapsed

        val elapsedSec = if (DiagnosticsLogger.sessionStartMs > 0L) {
            max(0L, (System.currentTimeMillis() - DiagnosticsLogger.sessionStartMs) / 1000L)
        } else 0L
        val stamp = DiagnosticsLogger.markClipHighlight(elapsedSec, label, this)
        Toast.makeText(this, "★ Saved Clip Highlight @ $stamp!", Toast.LENGTH_SHORT).show()
        pillController?.refreshExpandedControls()
        notifyDashboardStateChanged()
    }

    private fun triggerCleanScreenshotLive() {
        val vPipe = videoPipeline
        if (vPipe == null || !broadcastActive) {
            Toast.makeText(this, "Start a live stream to capture a clean thumbnail.", Toast.LENGTH_SHORT).show()
            return
        }
        val accepted = vPipe.requestCleanScreenshot { result ->
            mainHandler.post {
                result.onSuccess { fileName ->
                    Toast.makeText(
                        this,
                        "📸 Saved clean thumbnail: Pictures/SolStream/$fileName",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { err ->
                    Toast.makeText(
                        this,
                        "Screenshot failed: ${err.message ?: "try again"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        if (!accepted) {
            Toast.makeText(this, "Screenshot cooling down (wait 3s)...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleHudTelemetryLive() =
        saveAndPushSettings(activeSettings.copy(hudTelemetryEnabled = !activeSettings.hudTelemetryEnabled))

    private fun toggleQrOverlayLive() {
        if (!activeSettings.qrOverlayEnabled && activeSettings.qrOverlayText.isBlank()) {
            Toast.makeText(
                this,
                "Set a UPI, YouTube, or Discord link in Dashboard Card 3 first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        saveAndPushSettings(activeSettings.copy(qrOverlayEnabled = !activeSettings.qrOverlayEnabled))
    }

    private fun togglePngTuberLive() =
        saveAndPushSettings(activeSettings.copy(pngTuberEnabled = !activeSettings.pngTuberEnabled))

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
        val profileDefault = KeystoreVault.resolveDefaultHpfForProfile(activeSettings.voiceGenderProfile)
        saveAndPushSettings(
            activeSettings.copy(
                hpfCutoffHz = next,
                hpfAutoMode = (next == profileDefault)
            )
        )
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

    private fun cycleOverlayPositionLive() {
        val order = OVERLAY_POSITION_ORDER
        if (!activeSettings.overlayImageEnabled) {
            saveAndPushSettings(activeSettings.copy(overlayImageEnabled = true, overlayPosition = order.first()))
            return
        }
        val idx = order.indexOf(activeSettings.overlayPosition)
        if (idx in 0 until order.size - 1) {
            saveAndPushSettings(activeSettings.copy(overlayImageEnabled = true, overlayPosition = order[idx + 1]))
        } else {
            saveAndPushSettings(activeSettings.copy(overlayImageEnabled = false, overlayPosition = order.first()))
        }
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
        pillController?.refreshExpandedControls()
    }

    private fun cycleShortsAnchorLive() {
        val order = KeystoreVault.SHORTS_ANCHOR_ORDER
        val curIdx = order.indexOf(activeSettings.shortsVerticalAnchor.uppercase(Locale.US))
        val next = order[(curIdx + 1) % order.size]
        saveAndPushSettings(activeSettings.copy(shortsVerticalAnchor = next))
    }

    private fun cycleShortsMinimapLoupeLive() {
        val order = KeystoreVault.SHORTS_MINIMAP_LOUPE_ORDER
        val curIdx = order.indexOf(activeSettings.shortsMinimapLoupe.uppercase(Locale.US))
        val next = order[(curIdx + 1) % order.size]
        if (!next.equals("OFF", ignoreCase = true)) {
            videoPipeline?.setLoupesPaused(false)
        }
        saveAndPushSettings(activeSettings.copy(shortsMinimapLoupe = next))
    }

    private fun cycleShortsScoreLoupeLive() {
        val order = KeystoreVault.SHORTS_SCORE_LOUPE_ORDER
        val curIdx = order.indexOf(activeSettings.shortsScoreLoupe.uppercase(Locale.US))
        val next = order[(curIdx + 1) % order.size]
        if (!next.equals("OFF", ignoreCase = true)) {
            videoPipeline?.setLoupesPaused(false)
        }
        saveAndPushSettings(activeSettings.copy(shortsScoreLoupe = next))
    }

    private fun toggleShortsHeaderLayoutLive() =
        saveAndPushSettings(activeSettings.copy(shortsHeaderLayout = !activeSettings.shortsHeaderLayout))

    private fun toggleLoupesPausedLive() {
        val vPipe = videoPipeline ?: return
        val hasAnyLoupe = !activeSettings.shortsScoreLoupe.equals("OFF", ignoreCase = true) ||
            (!activeSettings.shortsMinimapLoupe.equals("OFF", ignoreCase = true) &&
                !SolVideoPipeline.isMinimapLoupeSuppressedByShield(
                    activeSettings.shortsMinimapLoupe,
                    activeSettings.minimapShieldEnabled,
                    activeSettings.minimapShieldCorner
                ))
        if (!hasAnyLoupe) {
            Toast.makeText(
                this,
                "Turn on FeedL or MapL first to use 1-tap Lobby Loupe Pause.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        vPipe.setLoupesPaused(!vPipe.liveShortsLoupesPaused)
        pillController?.refreshExpandedControls()
    }

    private fun stepLiveBitrate(dir: Int) {
        val destMaxKbps = KeystoreVault.maxVideoBitrateKbpsForDestination(activeSettings)
        val stepsKbps = MASTER_BITRATE_LADDER_KBPS.filter { it <= destMaxKbps }
        if (stepsKbps.isEmpty()) return
        val curKbps = activeBitrateBps / 1000
        val closestIdx = stepsKbps.indices.minByOrNull { abs(stepsKbps[it] - curKbps) } ?: 0
        val nextIdx = (closestIdx + dir).coerceIn(0, stepsKbps.lastIndex)
        val nextKbps = stepsKbps[nextIdx]

        val vPipe = videoPipeline
        if (vPipe != null) {
            if (vPipe.activeSceneMode == "LIVE") {
                vPipe.setBitrateOnFly(nextKbps * 1000)
                activeBitrateBps = vPipe.currentBitrateBps
            } else {
                activeBitrateBps = nextKbps * 1000
            }
        } else {
            activeBitrateBps = nextKbps * 1000
        }
        maxConfiguredBitrateBps = activeBitrateBps
        activeThermalCeilingBps = activeBitrateBps
        minAdaptiveBitrateBps = computeDynamicAbrFloorBps(maxConfiguredBitrateBps)
        saveAndPushSettings(activeSettings.copy(targetBitrateKbps = activeBitrateBps / 1000))
    }

    private fun toggleAbrLive() =
        saveAndPushSettings(activeSettings.copy(adaptiveBitrate = !activeSettings.adaptiveBitrate))

    private fun toggleThermal30FpsSaverLive() {
        val vPipe = videoPipeline ?: return
        val currentlyThrottled = manual30FpsSaverEnabled || autoThermal30FpsActive || autoThermalNative30BitrateTrimActive
        val newThrottleState = !currentlyThrottled

        val liveThermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DiagnosticsLogger.readThermalStatus(this)
        } else {
            0
        }
        if (shouldBlockManual30FpsRelease(
                requestingThrottleOff = !newThrottleState,
                autoThermalActive = autoThermal30FpsActive || autoThermalNative30BitrateTrimActive,
                currentThermalStatus = liveThermal
            )
        ) {
            manual30FpsSaverEnabled = false
            val liveTemp = if (cachedLiveTempC > 1.0f) cachedLiveTempC else DiagnosticsLogger.readBatteryTempC(this)
            val tempTag = if (liveTemp > 1.0f) String.format(Locale.US, " (%.1f°C)", liveTemp) else ""
            Toast.makeText(
                this,
                "Still in Severe warmth$tempTag — holding 30 FPS until cooled.",
                Toast.LENGTH_SHORT
            ).show()
            pillController?.refreshExpandedControls()
            return
        }

        manual30FpsSaverEnabled = newThrottleState
        if (!newThrottleState) {
            autoThermal30FpsActive = false
            autoThermalNative30BitrateTrimActive = false
        }
        vPipe.thermalThrottle30Fps = newThrottleState && activeNegotiatedFps > 30
        wasThermalThrottledLastTick = newThrottleState

        if (newThrottleState) {
            val scaledBps = if (activeNegotiatedFps > 30) {
                max(minAdaptiveBitrateBps, (maxConfiguredBitrateBps * 60) / 100)
            } else {
                val safe30Floor = computeNative30FpsSafeThermalFloorBps()
                if (maxConfiguredBitrateBps > safe30Floor) {
                    max(safe30Floor, (maxConfiguredBitrateBps * 75) / 100)
                } else {
                    maxConfiguredBitrateBps
                }
            }
            activeBitrateBps = scaledBps
            activeThermalCeilingBps = scaledBps
            if (vPipe.activeSceneMode == "LIVE") {
                vPipe.setBitrateOnFly(activeBitrateBps)
            }
            val compRatio = if (activeNegotiatedFps > 30) 60 else 75
            vPipe.setCompanionBitrateOnFly(max(1_200_000, (maxConfiguredCompanionBps * compRatio) / 100))
        } else {
            activeBitrateBps = maxConfiguredBitrateBps
            activeThermalCeilingBps = maxConfiguredBitrateBps
            if (vPipe.activeSceneMode == "LIVE") {
                vPipe.setBitrateOnFly(activeBitrateBps)
            }
            vPipe.setCompanionBitrateOnFly(maxConfiguredCompanionBps)
        }
        pillController?.refreshExpandedControls()
    }

    // === PER-SESSION & PER-CLIENT GUARDED PRIMARY RTMP CALLBACK HANDLERS ===
    private fun onPrimaryConnectionStarted(sessionGen: Long, sourceClient: RtmpClient?) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get()) ||
            sourceClient !== rtmpClient
        ) {
            return
        }
    }

    private fun onPrimaryConnectionSuccess(sessionGen: Long, sourceClient: RtmpClient?) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get()) ||
            sourceClient !== rtmpClient
        ) {
            Thread({ try { sourceClient?.disconnect() } catch (_: Exception) {} }).apply {
                isDaemon = true
                start()
            }
            return
        }
        // Push SPS/PPS/VPS BEFORE flipping rtmpConnected = true so no live packet precedes headers
        val sps = cachedSps
        if (sps != null) {
            try {
                sourceClient?.setVideoInfo(
                    ByteBuffer.wrap(sps),
                    cachedPps?.let { ByteBuffer.wrap(it) },
                    cachedVps?.let { ByteBuffer.wrap(it) }
                )
            } catch (_: Exception) {
            }
        }
        rtmpConnected = true
        rtmpConnectedSinceElapsedMs = SystemClock.elapsedRealtime()
        zeroThroughputStallTicks = 0
        reconnectScheduled.set(false)
        val serverTag = if (usingBackupUrl) "Backup RTMPS (?backup=1)" else "Primary RTMPS"
        DiagnosticsLogger.log("NETWORK", "Connected to $serverTag ingest. Stream is LIVE.")
        videoPipeline?.requestSyncFrame()
        probeIngestTcpRttIfNeeded()
        mainHandler.post {
            pillController?.updateTelemetry()
            notifyDashboardStateChanged()
        }
    }

    private fun onPrimaryNewBitrate(sessionGen: Long, sourceClient: RtmpClient?, bitrate: Long) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get()) ||
            sourceClient !== rtmpClient
        ) {
            return
        }
        lastCallbackBitrateKbps = (bitrate.coerceAtLeast(0L) / 1000L).toInt()
        lastCallbackBitrateElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun onPrimaryConnectionFailed(sessionGen: Long, sourceClient: RtmpClient?, reason: String) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get()) ||
            sourceClient !== rtmpClient
        ) {
            return
        }
        scheduleSilentReconnect(reason, sessionGen)
    }

    private fun onPrimaryDisconnect(sessionGen: Long, sourceClient: RtmpClient?) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get()) ||
            sourceClient !== rtmpClient
        ) {
            return
        }
        rtmpConnected = false
        rtmpConnectedSinceElapsedMs = 0L
        mainHandler.post {
            pillController?.updateTelemetry()
            notifyDashboardStateChanged()
        }
        scheduleSilentReconnect("Disconnected from server", sessionGen)
    }

    private fun onPrimaryAuthError(sessionGen: Long, sourceClient: RtmpClient?) {
        if (!shouldAcceptConnectionCallback(broadcastActive, sessionGen, connectionSessionGen.get()) ||
            sourceClient !== rtmpClient
        ) {
            return
        }
        rtmpConnected = false
        rtmpConnectedSinceElapsedMs = 0L
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

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        // Exactly 3 safe notification actions (Mute Mic, Scene/BRB, ★ Clip) — no accidental Stop in shade
        return builder
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentTitle("SolStream Studio • ${DiagnosticsLogger.ENGINE_VERSION}")
            .setContentText(contentText)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Mute Mic", mutePending).build())
            .addAction(Notification.Action.Builder(null, "Scene/BRB", brbPending).build())
            .addAction(Notification.Action.Builder(null, "★ Clip", clipPending).build())
            .build()
    }

    private fun updateStreamNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildStreamNotification(text))
        } catch (_: Exception) {
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
        val CORNER_POSITION_ORDER = listOf("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT")
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

        internal fun shouldBlockManual30FpsRelease(
            requestingThrottleOff: Boolean,
            autoThermalActive: Boolean,
            currentThermalStatus: Int
        ): Boolean {
            return requestingThrottleOff &&
                autoThermalActive &&
                currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        }

        internal fun formatCompactCornerShortTag(corner: String): String {
            return when (corner.trim().uppercase(Locale.US)) {
                "TOP_LEFT" -> "TL"
                "BOTTOM_LEFT" -> "BL"
                "BOTTOM_RIGHT" -> "BR"
                else -> "TR"
            }
        }

        internal fun formatCompactShortsAnchorTag(anchor: String): String {
            return if (anchor.trim().equals("ABOVE_CHAT", ignoreCase = true)) "Pos:ABOVE" else "Pos:CENTER"
        }

        internal fun formatCompactShortsMinimapLoupeTag(
            loupe: String,
            suppressedByShield: Boolean = false
        ): String {
            val norm = loupe.trim().uppercase(Locale.US)
            if (norm == "OFF") return "MapL:OFF"
            if (suppressedByShield) return "MapL:SHIELD"
            return when (norm) {
                "TOP_LEFT" -> "MapL:TL"
                else -> "MapL:TR"
            }
        }

        internal fun formatCompactShortsScoreLoupeTag(loupe: String): String {
            return when (loupe.trim().uppercase(Locale.US)) {
                "TOP_LEFT_FEED" -> "FeedL:TL+K"
                "TOP_LEFT" -> "FeedL:TL"
                "TOP_CENTER" -> "FeedL:TC"
                else -> "FeedL:OFF"
            }
        }

        internal fun buildActiveOverlaysSummary(s: StreamSettings): String {
            val parts = mutableListOf<String>()
            if (s.hudTelemetryEnabled) parts.add("SolHUD (${formatCompactCornerShortTag(s.hudTelemetryCorner)})")
            if (s.qrOverlayEnabled && s.qrOverlayText.isNotBlank()) parts.add("QR (${formatCompactCornerShortTag(s.qrOverlayCorner)})")
            if (s.pngTuberEnabled) parts.add("PNGTuber (${formatCompactCornerShortTag(s.pngTuberCorner)})")
            if (s.overlayImageEnabled) parts.add(formatCompactOverlayPositionTag(true, s.overlayPosition))
            if (s.liveBannerEnabled && s.liveBannerText.isNotBlank()) parts.add("Banner (${s.liveBannerPosition})")
            if (s.minimapShieldEnabled) parts.add("MapBlur (${s.minimapShieldCorner})")
            if (s.isVertical) {
                val mapSuppressed = SolVideoPipeline.isMinimapLoupeSuppressedByShield(
                    s.shortsMinimapLoupe,
                    s.minimapShieldEnabled,
                    s.minimapShieldCorner
                )
                if (s.shortsMinimapLoupe != "OFF" && !mapSuppressed) {
                    parts.add("MapLoupe (${formatCompactCornerShortTag(s.shortsMinimapLoupe)})")
                }
                if (s.shortsScoreLoupe != "OFF") {
                    val scoreTag = when (s.shortsScoreLoupe.uppercase(Locale.US)) {
                        "TOP_LEFT_FEED" -> "TL+Feed"
                        "TOP_CENTER" -> "TC"
                        else -> "TL"
                    }
                    parts.add("ScoreLoupe ($scoreTag)")
                }
                if (s.shortsHeaderLayout) {
                    parts.add("ShortsHeader")
                }
            }
            return if (parts.isEmpty()) "None" else parts.joinToString(" + ")
        }

        internal fun saveReportToLocalArchive(context: Context, header: String, body: String) =
            SolReportArchiveStore.saveReport(context, header, body)

        internal fun loadLocalReportArchive(context: Context): List<Pair<String, String>> =
            SolReportArchiveStore.loadReports(context)

        internal fun clearLocalReportArchive(context: Context) =
            SolReportArchiveStore.clearReports(context)

        /**
- Network-Adaptive RTMP Write Chunk Size:
- 65,535 bytes (64 KB) on Wi-Fi/Ethernet for major broadcast ingests (YouTube, Twitch, Kick)
- 4,096 bytes (4 KB) on Mobile Data/Cellular AND unknown custom RTMP relays
         */
        internal fun resolveAdaptiveRtmpChunkSize(
            isWifiOrEthernet: Boolean,
            endpointUrl: String
        ): Int {
            if (!isWifiOrEthernet) return 4096
            val lower = endpointUrl.lowercase(Locale.US)
            val isMajorIngest = lower.contains("youtube.com") ||
                lower.contains("twitch.tv") ||
                lower.contains("live-video.net") ||
                lower.contains("kick.com")
            return if (isMajorIngest) 65535 else 4096
        }

        internal fun formatCompactOverlayPositionTag(enabled: Boolean, position: String): String {
            if (!enabled) return "Logo:OFF"
            return when (position.uppercase(Locale.US)) {
                "TOP_LEFT" -> "Logo:TL"
                "TOP_RIGHT" -> "Logo:TR"
                "BOTTOM_LEFT" -> "Logo:BL"
                "BOTTOM_RIGHT" -> "Logo:BR"
                else -> "Logo:FULL"
            }
        }

        internal fun formatOverlayQueueTag(qFrames: Int): String =
            if (qFrames >= 0) "Q:${qFrames}f" else "Q:N/A"

        internal fun convertRttMicrosToMs(rawRttUs: Long): Long {
            if (rawRttUs <= 0L) return -1L
            return max(1L, (rawRttUs + 500L) / 1000L)
        }

        internal fun shouldAcceptConnectionCallback(
            isBroadcastActive: Boolean,
            callbackGen: Long,
            currentGen: Long,
            isTargetStopped: Boolean = false
        ): Boolean {
            return isBroadcastActive && !isTargetStopped && callbackGen == currentGen
        }

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
