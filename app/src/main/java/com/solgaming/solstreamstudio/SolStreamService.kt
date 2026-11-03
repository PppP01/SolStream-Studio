package com.solgaming.solstreamstudio

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.rtmp.rtmp.RtmpClient
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SolStreamService : AccessibilityService(), ConnectChecker {

    companion object {
        const val ACTION_START_STREAM = "com.solgaming.solstreamstudio.START_STREAM"
        const val ACTION_STOP_STREAM = "com.solgaming.solstreamstudio.STOP_STREAM"
        const val ACTION_START_GLIP_BENCH = "com.solgaming.solstreamstudio.START_GLIP_BENCH"
        const val ACTION_STOP_GLIP_BENCH = "com.solgaming.solstreamstudio.STOP_GLIP_BENCH"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIF_CHANNEL_ID = "solstream_live_channel"
        private const val NOTIF_ID = 4102

        @Volatile var instance: SolStreamService? = null
        @Volatile var isAccessibilityConnected: Boolean = false
        @Volatile var isBroadcastLive: Boolean = false
        @Volatile var isGlipBenchmarkRunning: Boolean = false
        @Volatile var needsProjectionReapproval: Boolean = false
        @Volatile var connectionStateLabel: String = "IDLE"
        var onStateChangedListener: (() -> Unit)? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var rtmpClient: RtmpClient? = null
    private var videoPipeline: SolVideoPipeline? = null
    private var audioEngine: SolDualAudioEngine? = null
    private var mediaProjection: MediaProjection? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    private var activeSettings = StreamSettings()
    private var activeEndpoint: String = ""
    @Volatile private var isRtmpConnected = false
    private var streamStartElapsedMs = 0L
    private var reconnectCount = 0
    private val maxReconnects = 8
    private var stopConfirmTapTimeMs = 0L

    // Cached sequence headers for instant reconnect recovery
    private var cachedSps: ByteBuffer? = null
    private var cachedPps: ByteBuffer? = null
    private var cachedVps: ByteBuffer? = null

    // Queue-aware adaptive bitrate state
    @Volatile private var lastMeasuredOutBps: Long = 0L
    private var congestedTicks = 0
    private var stableTicks = 0

    // Floating Accessibility Overlay Views
    private var overlayRoot: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var ghostPillView: TextView? = null
    private var expandedPanel: ScrollView? = null
    private var isOverlayExpanded = false

    private var tvHeaderStats: TextView? = null
    private var tvBadges: TextView? = null
    private var btnMicMute: Button? = null
    private var btnGameMute: Button? = null
    private var btnBrbShield: Button? = null
    private var btnVoicePreset: Button? = null
    private var btnNoiseSuppress: Button? = null
    private var btnHpf: Button? = null
    private var btnDspExtras: Button? = null
    private var btnMicGain: Button? = null
    private var btnGameGain: Button? = null
    private var btnDucking: Button? = null
    private var btnFraming: Button? = null
    private var btnFlipAndSharp: Button? = null
    private var btnBitrate: Button? = null
    private var btnStopStream: Button? = null

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!isBroadcastLive && !isGlipBenchmarkRunning) return
            runHealthAndRecoveryTick()
            if (isOverlayExpanded && isBroadcastLive) {
                refreshExpandedOverlayText()
                mainHandler.postDelayed(this, 1000L)
            } else {
                // Sleep UI redraws at 0 Hz when collapsed; poll health quietly every 3s
                mainHandler.postDelayed(this, 3000L)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isAccessibilityConnected = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        onStateChangedListener?.invoke()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally minimal: no window content inspection
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stopLiveBroadcast("Accessibility service unbound")
        isAccessibilityConnected = false
        instance = null
        onStateChangedListener?.invoke()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopLiveBroadcast("Service destroyed")
        isAccessibilityConnected = false
        instance = null
        super.onDestroy()
    }

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
                    DiagnosticsLogger.log("PERM_ERR", "Screen capture permission was not granted.")
                }
            }
            ACTION_STOP_STREAM -> stopLiveBroadcast("Stopped from dashboard")
            ACTION_START_GLIP_BENCH -> startPassiveGlipBenchmark()
            ACTION_STOP_GLIP_BENCH -> stopPassiveGlipBenchmark()
        }
        return START_NOT_STICKY
    }

    private fun startPassiveGlipBenchmark() {
        if (isBroadcastLive) return
        isGlipBenchmarkRunning = true
        DiagnosticsLogger.startNewSession(this, "Glip Baseline Benchmark", "Passive Thermal/RAM Monitor")
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.post(watchdogRunnable)
        Toast.makeText(this, "Glip Benchmark started! Play & stream in Glip, then return to stop.", Toast.LENGTH_LONG).show()
        onStateChangedListener?.invoke()
    }

    private fun stopPassiveGlipBenchmark() {
        if (!isGlipBenchmarkRunning) return
        isGlipBenchmarkRunning = false
        mainHandler.removeCallbacks(watchdogRunnable)
        DiagnosticsLogger.finishAndSaveBenchmark(this, isGlipBaseline = true)
        DiagnosticsLogger.log("BENCHMARK", "Glip baseline benchmark saved for comparison.")
        Toast.makeText(this, "Glip Benchmark saved to Session Report!", Toast.LENGTH_SHORT).show()
        onStateChangedListener?.invoke()
    }

    private fun startLiveBroadcast(resultCode: Int, resultData: Intent) {
        if (isBroadcastLive) return
        if (isGlipBenchmarkRunning) stopPassiveGlipBenchmark()

        val endpoint = KeystoreVault.buildFullStreamEndpoint(this)
        if (endpoint.isNullOrBlank()) {
            Toast.makeText(this, "Paste your YouTube Stream Key first!", Toast.LENGTH_LONG).show()
            return
        }

        activeSettings = KeystoreVault.loadSettings(this)
        activeEndpoint = endpoint
        needsProjectionReapproval = false
        reconnectCount = 0
        congestedTicks = 0
        stableTicks = 0

        // 1. Promote to ForegroundService strictly AFTER user granted MediaProjection consent
        val notif = buildForegroundNotification("Starting SolStream Studio broadcast...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            mpm.getMediaProjection(resultCode, resultData)
        } catch (e: Exception) {
            DiagnosticsLogger.log("PROJ_ERR", "Failed to obtain MediaProjection token.", e.message ?: "")
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        if (projection == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        mediaProjection = projection

        acquireLocksAndThermalMonitor(activeSettings)

        val originNs = System.nanoTime()
        val client = RtmpClient(this)
        rtmpClient = client

        // 2. Start Hardware Video Pipeline
        val vPipe = SolVideoPipeline(this)
        videoPipeline = vPipe
        val activeVideo = vPipe.startPipeline(
            settings = activeSettings,
            mediaProjection = projection,
            originTimeNs = originNs,
            onProjectionStoppedBySystem = {
                mainHandler.post { handleSystemProjectionStop() }
            },
            onVideoConfig = { sps, pps, vps ->
                cachedSps = sps.duplicate()
                cachedPps = pps?.duplicate()
                cachedVps = vps?.duplicate()
                try {
                    client.setVideoInfo(sps.duplicate(), pps?.duplicate(), vps?.duplicate())
                } catch (_: Exception) {}
            },
            onEncodedVideo = { buf, info ->
                if (isRtmpConnected) {
                    try { client.sendVideo(buf.duplicate(), info) } catch (_: Exception) {}
                }
            }
        )

        if (activeVideo == null) {
            Toast.makeText(this, "Hardware video encoder failed to start. Check Session Report.", Toast.LENGTH_LONG).show()
            cleanupResources()
            return
        }

        // 3. Configure RtmpClient for Selected Hardware Codec & Stereo 48 kHz AAC
        val rtmpVideoCodec = when (activeVideo.codecKey) {
            "HEVC" -> VideoCodec.H265
            "AV1" -> VideoCodec.AV1
            else -> VideoCodec.H264
        }
        client.setVideoCodec(rtmpVideoCodec)
        client.setAudioCodec(AudioCodec.AAC)
        client.setAudioInfo(48000, true)
        client.setVideoResolution(activeVideo.outWidth, activeVideo.outHeight)
        client.setFps(activeVideo.fps)

        // 4. Start Split 32-Bit Float Mic + Game Audio Engine
        val aEngine = SolDualAudioEngine(this)
        audioEngine = aEngine
        val audioOk = aEngine.startStreamingAudio(
            settings = activeSettings,
            mediaProjection = projection,
            originTimeUs = originNs / 1000L,
            onStatusChanged = {
                if (isOverlayExpanded) mainHandler.post { refreshExpandedOverlayText() }
            },
            onEncodedAac = { aacBuf, info ->
                if (isRtmpConnected) {
                    try { client.sendAudio(aacBuf.duplicate(), info) } catch (_: Exception) {}
                }
            }
        )

        if (!audioOk) {
            Toast.makeText(this, "Audio engine failed to open microphone.", Toast.LENGTH_LONG).show()
            cleanupResources()
            return
        }

        val modeTag = if (activeVideo.isVbr) "VBR" else "CBR"
        val desc = "${activeVideo.codecKey} (${activeVideo.encoderName}) ${activeVideo.outWidth}x${activeVideo.outHeight}@${activeVideo.fps}fps $modeTag"
        DiagnosticsLogger.startNewSession(this, "Live Broadcast", desc)

        isBroadcastLive = true
        streamStartElapsedMs = SystemClock.elapsedRealtime()
        connectionStateLabel = "CONNECTING"
        showFloatingOverlay()

        // 5. Connect RTMPS on background thread
        Thread({
            try {
                client.connect(activeEndpoint)
            } catch (e: Exception) {
                DiagnosticsLogger.log("NET_ERR", "RTMPS connection exception (${e.javaClass.simpleName}).", e.message ?: "")
            }
        }, "SolRtmpsConnect").start()

        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, 1500L)
        onStateChangedListener?.invoke()
    }

    private fun handleSystemProjectionStop() {
        if (!isBroadcastLive) return
        needsProjectionReapproval = true
        connectionStateLabel = "CAPTURE PAUSED (RE-APPROVE)"
        videoPipeline?.stopPipeline()
        updateGhostPillAppearance()
        if (isOverlayExpanded) refreshExpandedOverlayText()
        Toast.makeText(
            this,
            "Screen capture stopped by Android (lock screen/system). Open SolStream Studio to re-approve.",
            Toast.LENGTH_LONG
        ).show()
        onStateChangedListener?.invoke()
    }

    fun stopLiveBroadcast(reason: String = "User stopped stream") {
        if (!isBroadcastLive && rtmpClient == null) return
        isBroadcastLive = false
        isRtmpConnected = false
        connectionStateLabel = "STOPPED"
        mainHandler.removeCallbacksAndMessages(null)

        DiagnosticsLogger.log("SESSION_END", "Stream ended ($reason).")
        DiagnosticsLogger.finishAndSaveBenchmark(this, isGlipBaseline = false)

        cleanupResources()
        removeFloatingOverlay()
        onStateChangedListener?.invoke()
    }

    private fun cleanupResources() {
        val clientToClose = rtmpClient
        rtmpClient = null
        if (clientToClose != null) {
            Thread({
                try { clientToClose.disconnect() } catch (_: Exception) {}
            }, "SolRtmpsDisconnect").start()
        }

        try { audioEngine?.stopStreamingAudio() } catch (_: Exception) {}
        audioEngine = null

        try { videoPipeline?.stopPipeline() } catch (_: Exception) {}
        videoPipeline = null

        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null

        cachedSps = null; cachedPps = null; cachedVps = null
        releaseLocksAndThermalMonitor()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }

    // === CONNECTCHECKER CALLBACKS ===
    override fun onConnectionStarted(url: String) {
        connectionStateLabel = "CONNECTING"
        DiagnosticsLogger.log("NETWORK", "Connecting to RTMPS ingest server...")
        mainHandler.post { updateGhostPillAppearance() }
    }

    override fun onConnectionSuccess() {
        isRtmpConnected = true
        reconnectCount = 0
        connectionStateLabel = "LIVE"
        cachedSps?.let { sps ->
            try { rtmpClient?.setVideoInfo(sps.duplicate(), cachedPps?.duplicate(), cachedVps?.duplicate()) } catch (_: Exception) {}
        }
        videoPipeline?.requestSyncFrame()
        DiagnosticsLogger.log("NETWORK", "Connected to YouTube RTMPS ingest. Stream is LIVE.")
        mainHandler.post {
            updateGhostPillAppearance()
            if (isOverlayExpanded) refreshExpandedOverlayText()
            onStateChangedListener?.invoke()
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        lastMeasuredOutBps = bitrate
    }

    override fun onConnectionFailed(reason: String) {
        isRtmpConnected = false
        if (!isBroadcastLive || needsProjectionReapproval) return

        if (reconnectCount < maxReconnects) {
            reconnectCount++
            DiagnosticsLogger.reconnectAttempts = reconnectCount
            val delayMs = min(15_000L, 2000L * reconnectCount)
            connectionStateLabel = "RETRY $reconnectCount/$maxReconnects"
            DiagnosticsLogger.log(
                "NET_RETRY",
                "Upload interrupted. Attempting safe reconnect $reconnectCount/$maxReconnects in ${delayMs / 1000}s.",
                reason
            )
            mainHandler.post {
                updateGhostPillAppearance()
                if (isOverlayExpanded) refreshExpandedOverlayText()
            }
            mainHandler.postDelayed({
                if (isBroadcastLive && !needsProjectionReapproval) {
                    Thread({
                        try {
                            rtmpClient?.disconnect()
                            cachedSps?.let { sps ->
                                rtmpClient?.setVideoInfo(sps.duplicate(), cachedPps?.duplicate(), cachedVps?.duplicate())
                            }
                            rtmpClient?.connect(activeEndpoint)
                        } catch (_: Exception) {}
                    }, "SolRtmpsRetry").start()
                }
            }, delayMs)
        } else {
            DiagnosticsLogger.log("NET_FAIL", "Stream disconnected after $maxReconnects retries.", reason)
            mainHandler.post { stopLiveBroadcast("Network connection lost") }
        }
    }

    override fun onDisconnect() {
        isRtmpConnected = false
    }

    override fun onAuthError() {
        DiagnosticsLogger.log("AUTH_ERR", "YouTube rejected the Stream Key (Auth Error). Verify your key in Pre-Stream Studio.")
        mainHandler.post {
            Toast.makeText(this, "Stream Key rejected by server!", Toast.LENGTH_LONG).show()
            stopLiveBroadcast("Authentication error")
        }
    }

    override fun onAuthSuccess() {}

    // === SOLSTREAM HEALTH & RECOVERY + QUEUE-AWARE BITRATE CONTROLLER ===
    private fun runHealthAndRecoveryTick() {
        val (_, thermal) = DiagnosticsLogger.sampleDeviceHealth(this)
        if (!isBroadcastLive) return

        // 1. Read RootEncoder 2.8.1 sender queue & dropped frames safely
        val (qBytes, dropped) = queryRtmpSenderTelemetry()
        DiagnosticsLogger.lastQueueBytes = qBytes
        DiagnosticsLogger.droppedVideoFrames = dropped

        // 2. Thermal Auto-Shedding
        val vPipe = videoPipeline
        val aEng = audioEngine
        if (thermal >= PowerManager.THERMAL_STATUS_MODERATE && vPipe?.liveGpuSharpen == true) {
            vPipe.liveGpuSharpen = false
            activeSettings = activeSettings.copy(gpuSharpen = false)
            DiagnosticsLogger.log("THERMAL", "Phone reached Moderate warmth; disabled optional GPU sharpening to protect CODM 120 FPS.")
        }
        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE && (aEng?.micDsp?.noiseSuppressLevel ?: 0) > 0) {
            aEng?.micDsp?.noiseSuppressLevel = 0
            activeSettings = activeSettings.copy(rnnoiseLevel = 0)
            DiagnosticsLogger.log("THERMAL", "Phone reached Severe warmth; stepped down noise suppression to protect gameplay.")
        }

        // 3. Queue-Aware Adaptive Bitrate with Hysteresis
        if (activeSettings.adaptiveBitrate && isRtmpConnected && vPipe != null) {
            val currentTargetBps = vPipe.currentBitrateBps
            val maxConfiguredBps = activeSettings.targetBitrateKbps * 1000
            val outBps = lastMeasuredOutBps

            val queueCongested = qBytes > 650_000L ||
                (outBps in 1L until (currentTargetBps * 0.68).toLong())

            if (queueCongested) {
                congestedTicks++
                stableTicks = 0
                if (congestedTicks >= 2) {
                    congestedTicks = 0
                    val reduced = max(3_000_000, (currentTargetBps * 0.82).toInt())
                    if (reduced < currentTargetBps) {
                        vPipe.setBitrateOnFly(reduced)
                        DiagnosticsLogger.log(
                            "ABR_DOWN",
                            "Upload queue grew (${qBytes / 1024} KB). Reduced video bitrate to ${reduced / 1_000_000.0} Mbps to protect CODM ping.",
                            "out=${outBps / 1000}kbps"
                        )
                    }
                }
            } else {
                congestedTicks = 0
                if (currentTargetBps < maxConfiguredBps && qBytes < 180_000L) {
                    stableTicks++
                    if (stableTicks >= 4) {
                        stableTicks = 0
                        val steppedUp = min(maxConfiguredBps, currentTargetBps + 1_000_000)
                        vPipe.setBitrateOnFly(steppedUp)
                    }
                }
            }
        }
    }

    private fun queryRtmpSenderTelemetry(): Pair<Long, Int> {
        val client = rtmpClient ?: return 0L to 0
        var qBytes = 0L
        var drops = 0
        try {
            val qMethod = client.javaClass.methods.firstOrNull { it.name == "getQueueBytesOut" && it.parameterCount == 0 }
            if (qMethod != null) qBytes = (qMethod.invoke(client) as? Number)?.toLong() ?: 0L
            val dMethod = client.javaClass.methods.firstOrNull { it.name == "getDroppedVideoFrames" && it.parameterCount == 0 }
            if (dMethod != null) drops = (dMethod.invoke(client) as? Number)?.toInt() ?: 0
        } catch (_: Exception) {
        }
        return qBytes to drops
    }

    private fun acquireLocksAndThermalMonitor(s: StreamSettings) {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SolStream::BroadcastWakeLock").apply {
                acquire(6 * 60 * 60 * 1000L)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                        DiagnosticsLogger.log(
                            "THERMAL_EVENT",
                            "Android thermal status changed to ${DiagnosticsLogger.thermalStatusName(status)}."
                        )
                    }
                }
                thermalListener = listener
                pm.addThermalStatusListener(mainExecutor, listener)
            }
            if (s.wifiLowLatencyLock) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "SolStream::LowLatencyWifi").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseLocksAndThermalMonitor() {
        try {
            thermalListener?.let {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    pm.removeThermalStatusListener(it)
                }
            }
        } catch (_: Exception) {}
        thermalListener = null

        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}
        wifiLock = null

        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    // === FLOATING ACCESSIBILITY OVERLAY (SLEEPING GHOST DOT + EXPANDED LIVE STUDIO) ===
    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingOverlay() {
        if (overlayRoot != null) return
        val wm = windowManager ?: return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val pill = TextView(this).apply {
            text = "LIVE • MIC ON"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedBg("#CC0D1B2A", "#00E5FF", 20)
            alpha = 0.38f // Quiet 38% opacity Ghost Mode when collapsed
        }
        ghostPillView = pill

        val panelScroll = buildExpandedStudioPanel()
        panelScroll.visibility = View.GONE
        expandedPanel = panelScroll

        root.addView(pill)
        root.addView(panelScroll)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(90)
        }
        overlayParams = params

        // Drag + Anti-Misclick Double-Tap / Long-Press to Expand
        var downX = 0f; var downY = 0f
        var startParamX = 0; var startParamY = 0
        var moved = false
        var lastTapTime = 0L

        pill.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startParamX = params.x; startParamY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                        params.x = startParamX + dx
                        params.y = startParamY + dy
                        try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val pressDur = ev.eventTime - ev.downTime
                    if (!moved) {
                        val now = SystemClock.uptimeMillis()
                        if (isOverlayExpanded) {
                            setOverlayExpanded(false)
                        } else if (now - lastTapTime < 380L || pressDur >= 420L) {
                            setOverlayExpanded(true)
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(root, params)
            overlayRoot = root
            isOverlayExpanded = false
            updateGhostPillAppearance()
        } catch (e: Exception) {
            DiagnosticsLogger.log("OVERLAY_ERR", "Could not attach floating pill (${e.javaClass.simpleName}).")
        }
    }

    private fun buildExpandedStudioPanel(): ScrollView {
        val scroll = ScrollView(this).apply {
            background = roundedBg("#F20B0F17", "#263238", 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        tvHeaderStats = TextView(this).apply {
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
        }
        tvBadges = TextView(this).apply {
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, dp(6))
        }
        val btnCollapse = smallActionButton("Collapse to Ghost Dot (2-Tap to Reopen)", "#1C2836") {
            setOverlayExpanded(false)
        }

        btnMicMute = smallActionButton("Stream Mic: ON", "#00695C") {
            val a = audioEngine ?: return@smallActionButton
            a.micDsp.streamMicMuted = !a.micDsp.streamMicMuted
            updateGhostPillAppearance()
            refreshExpandedOverlayText()
        }

        btnGameMute = smallActionButton("GAME + SQUAD STREAM: ON", "#1565C0") {
            val a = audioEngine ?: return@smallActionButton
            a.gameStreamMuted = !a.gameStreamMuted
            refreshExpandedOverlayText()
        }

        btnBrbShield = smallActionButton("BRB Privacy Blackout: OFF", "#37474F") {
            val v = videoPipeline ?: return@smallActionButton
            val newBrb = !v.brbBlackoutActive
            v.brbBlackoutActive = newBrb
            if (activeSettings.brbMuteMicToo) {
                audioEngine?.micDsp?.streamMicMuted = newBrb
            }
            updateGhostPillAppearance()
            refreshExpandedOverlayText()
        }

        btnVoicePreset = smallActionButton("Voice Preset: CLEAN", "#283593") {
            val next = when (activeSettings.voicePreset) {
                "RAW" -> "CLEAN"
                "CLEAN" -> "STUDIO"
                else -> "RAW"
            }
            activeSettings = activeSettings.copy(voicePreset = next)
            KeystoreVault.saveSettings(this, activeSettings)
            audioEngine?.updateLiveSettings(activeSettings)
            refreshExpandedOverlayText()
        }

        btnNoiseSuppress = smallActionButton("Fan Noise Suppress: OFF", "#263238") {
            val next = (activeSettings.rnnoiseLevel + 1) % 3
            activeSettings = activeSettings.copy(rnnoiseLevel = next)
            KeystoreVault.saveSettings(this, activeSettings)
            audioEngine?.updateLiveSettings(activeSettings)
            refreshExpandedOverlayText()
        }

        btnHpf = smallActionButton("Wind/Grip HPF: 85 Hz", "#263238") {
            val next = when (activeSettings.hpfCutoffHz) {
                0 -> 75; 75 -> 85; 85 -> 100; else -> 0
            }
            activeSettings = activeSettings.copy(hpfCutoffHz = next)
            KeystoreVault.saveSettings(this, activeSettings)
            audioEngine?.updateLiveSettings(activeSettings)
            refreshExpandedOverlayText()
        }

        btnDspExtras = smallActionButton("Gate / EQ / De-Ess", "#263238") {
            // Cycle clean combos: Gate+Comp -> Gate+Comp+EQ -> Full + De-Esser -> All Off
            val stateIdx = when {
                !activeSettings.noiseGateEnabled -> 0
                !activeSettings.voiceEqEnabled -> 1
                !activeSettings.deEsserEnabled -> 2
                else -> 3
            }
            activeSettings = when (stateIdx) {
                0 -> activeSettings.copy(noiseGateEnabled = true, compressorEnabled = true, voiceEqEnabled = false, deEsserEnabled = false)
                1 -> activeSettings.copy(noiseGateEnabled = true, compressorEnabled = true, voiceEqEnabled = true, deEsserEnabled = false)
                2 -> activeSettings.copy(noiseGateEnabled = true, compressorEnabled = true, voiceEqEnabled = true, deEsserEnabled = true)
                else -> activeSettings.copy(noiseGateEnabled = false, compressorEnabled = false, voiceEqEnabled = false, deEsserEnabled = false)
            }
            KeystoreVault.saveSettings(this, activeSettings)
            audioEngine?.updateLiveSettings(activeSettings)
            refreshExpandedOverlayText()
        }

        btnMicGain = smallActionButton("Mic Gain: 125%", "#263238") {
            val steps = listOf(75, 100, 125, 150, 180, 220)
            val idx = steps.indexOf(activeSettings.micGainPercent)
            val next = steps[(idx + 1) % steps.size]
            activeSettings = activeSettings.copy(micGainPercent = next)
            KeystoreVault.saveSettings(this, activeSettings)
            audioEngine?.updateLiveSettings(activeSettings)
            refreshExpandedOverlayText()
        }

        btnGameGain = smallActionButton("Game Mix Level: 100%", "#263238") {
            val steps = listOf(30, 55, 80, 100, 120)
            val idx = steps.indexOf(activeSettings.gameGainPercent)
            val next = steps[(idx + 1) % steps.size]
            activeSettings = activeSettings.copy(gameGainPercent = next)
            KeystoreVault.saveSettings(this, activeSettings)
            audioEngine?.updateLiveSettings(activeSettings)
            refreshExpandedOverlayText()
        }

        btnDucking = smallActionButton("VAD Auto-Ducking: OFF", "#263238") {
            val next = when (activeSettings.vadDuckingPercent) {
                0 -> 20; 20 -> 35; else -> 0
            }
            activeSettings = activeSettings.copy(vadDuckingPercent = next)
            KeystoreVault.saveSettings(this, activeSettings)
            audioEngine?.updateLiveSettings(activeSettings)
            refreshExpandedOverlayText()
        }

        btnFraming = smallActionButton("Framing: FIT (HUD Safe)", "#263238") {
            val next = when (activeSettings.framingMode) {
                "FIT" -> "SMART"
                "SMART" -> "FILL"
                else -> "FIT"
            }
            activeSettings = activeSettings.copy(framingMode = next)
            KeystoreVault.saveSettings(this, activeSettings)
            videoPipeline?.applyLiveVisualSettings(activeSettings)
            refreshExpandedOverlayText()
        }

        btnFlipAndSharp = smallActionButton("Flip: 0° | Sharp: OFF", "#263238") {
            val (newFlip, newSharp) = when {
                activeSettings.landscapeFlip == 0 && !activeSettings.gpuSharpen -> 0 to true
                activeSettings.landscapeFlip == 0 && activeSettings.gpuSharpen -> 180 to false
                activeSettings.landscapeFlip == 180 && !activeSettings.gpuSharpen -> 180 to true
                else -> 0 to false
            }
            activeSettings = activeSettings.copy(landscapeFlip = newFlip, gpuSharpen = newSharp)
            KeystoreVault.saveSettings(this, activeSettings)
            videoPipeline?.applyLiveVisualSettings(activeSettings)
            refreshExpandedOverlayText()
        }

        btnBitrate = smallActionButton("Target Bitrate: 20 Mbps", "#1B5E20") {
            val steps = listOf(12000, 16000, 18000, 20000, 22000, 24000)
            val idx = steps.indexOf(activeSettings.targetBitrateKbps)
            val next = steps[(idx + 1) % steps.size]
            activeSettings = activeSettings.copy(targetBitrateKbps = next)
            KeystoreVault.saveSettings(this, activeSettings)
            videoPipeline?.setBitrateOnFly(next * 1000)
            refreshExpandedOverlayText()
        }

        btnStopStream = smallActionButton("Stop Stream (2-Tap Confirm)", "#B71C1C") {
            val now = SystemClock.elapsedRealtime()
            if (now - stopConfirmTapTimeMs < 3000L) {
                stopLiveBroadcast("Stopped from floating menu")
            } else {
                stopConfirmTapTimeMs = now
                btnStopStream?.text = "TAP AGAIN WITHIN 3s TO END LIVE!"
            }
        }

        listOf(
            tvHeaderStats, tvBadges, btnCollapse,
            btnMicMute, btnGameMute, btnBrbShield,
            btnVoicePreset, btnNoiseSuppress, btnHpf, btnDspExtras,
            btnMicGain, btnGameGain, btnDucking,
            btnFraming, btnFlipAndSharp, btnBitrate, btnStopStream
        ).forEach { v -> if (v != null) box.addView(v) }

        scroll.addView(box)
        return scroll
    }

    private fun setOverlayExpanded(expand: Boolean) {
        isOverlayExpanded = expand
        expandedPanel?.visibility = if (expand) View.VISIBLE else View.GONE
        ghostPillView?.alpha = if (expand) 0.95f else 0.38f
        if (expand) {
            refreshExpandedOverlayText()
            mainHandler.removeCallbacks(watchdogRunnable)
            mainHandler.post(watchdogRunnable)
        } else {
            updateGhostPillAppearance()
        }
    }

    private fun updateGhostPillAppearance() {
        val pill = ghostPillView ?: return
        val muted = audioEngine?.micDsp?.streamMicMuted == true
        val brb = videoPipeline?.brbBlackoutActive == true
        val label = when {
            needsProjectionReapproval -> "RE-APPROVE CAPTURE"
            brb -> "BRB BLACKOUT"
            muted -> "LIVE • MUTED"
            else -> "$connectionStateLabel • MIC ON"
        }
        val stroke = when {
            needsProjectionReapproval -> "#FF9100"
            brb -> "#FFD600"
            muted -> "#FF1744"
            else -> "#00E676"
        }
        pill.text = label
        pill.background = roundedBg("#CC0D1B2A", stroke, 20)
    }

    private fun refreshExpandedOverlayText() {
        val elapsedSec = max(0L, (SystemClock.elapsedRealtime() - streamStartElapsedMs) / 1000L)
        val hh = elapsedSec / 3600L
        val mm = (elapsedSec % 3600L) / 60L
        val ss = elapsedSec % 60L
        val outMbps = lastMeasuredOutBps / 1_000_000.0
        val fps = videoPipeline?.actualEncodedFps ?: 0
        val qKb = DiagnosticsLogger.lastQueueBytes / 1024L

        tvHeaderStats?.text = String.format(
            Locale.US,
            "%02d:%02d:%02d | %.1f Mbps | %d FPS | Q:%dKB",
            hh, mm, ss, outMbps, fps, qKb
        )

        val micState = audioEngine?.micStatusText ?: "MIC IDLE"
        val gameState = if (audioEngine?.gameAudioActive == true) "GAME OK" else "MIC-ONLY"
        val peakDb = audioEngine?.micDsp?.lastPeakDb?.toInt() ?: -90
        tvBadges?.text = "$connectionStateLabel • $micState ($peakDb dB) • $gameState"

        val micMuted = audioEngine?.micDsp?.streamMicMuted == true
        btnMicMute?.text = if (micMuted) "Stream Mic: MUTED (Squad Still Hears You)" else "Stream Mic: ON"
        btnMicMute?.background = roundedBg(if (micMuted) "#B71C1C" else "#00695C", "#37474F", 8)

        val gMuted = audioEngine?.gameStreamMuted == true
        btnGameMute?.text = if (gMuted) "GAME + SQUAD STREAM: MUTED" else "GAME + SQUAD STREAM: ON"

        val brb = videoPipeline?.brbBlackoutActive == true
        btnBrbShield?.text = if (brb) "BRB Privacy Blackout: ACTIVE" else "BRB Privacy Blackout: OFF"

        btnVoicePreset?.text = "Voice Preset: ${activeSettings.voicePreset}"
        val nsLabel = when (activeSettings.rnnoiseLevel) { 1 -> "LOW"; 2 -> "MED"; else -> "OFF" }
        btnNoiseSuppress?.text = "Fan Noise Suppress: $nsLabel"
        btnHpf?.text = if (activeSettings.hpfCutoffHz == 0) "Wind/Grip HPF: OFF" else "Wind/Grip HPF: ${activeSettings.hpfCutoffHz} Hz"
        btnDspExtras?.text = "Gate:${onOff(activeSettings.noiseGateEnabled)} | EQ:${onOff(activeSettings.voiceEqEnabled)} | Comp:${onOff(activeSettings.compressorEnabled)} | DeEss:${onOff(activeSettings.deEsserEnabled)}"
        btnMicGain?.text = "Mic Gain: ${activeSettings.micGainPercent}%"
        btnGameGain?.text = "Game Mix Level: ${activeSettings.gameGainPercent}%"
        btnDucking?.text = if (activeSettings.vadDuckingPercent == 0) "VAD Auto-Ducking: OFF" else "VAD Auto-Ducking: -${activeSettings.vadDuckingPercent}%"
        btnFraming?.text = "Framing: ${activeSettings.framingMode}"
        btnFlipAndSharp?.text = "Flip: ${activeSettings.landscapeFlip}° | Sharp: ${onOff(activeSettings.gpuSharpen)}"
        val curMbps = (videoPipeline?.currentBitrateBps ?: (activeSettings.targetBitrateKbps * 1000)) / 1_000_000.0
        btnBitrate?.text = String.format(Locale.US, "Bitrate: %.1fM / Max %dM", curMbps, activeSettings.targetBitrateKbps / 1000)
        if (SystemClock.elapsedRealtime() - stopConfirmTapTimeMs >= 3000L) {
            btnStopStream?.text = "Stop Stream (2-Tap Confirm)"
        }
    }

    private fun removeFloatingOverlay() {
        val root = overlayRoot ?: return
        overlayRoot = null
        try { windowManager?.removeView(root) } catch (_: Exception) {}
    }

    private fun smallActionButton(label: String, bgHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            background = roundedBg(bgHex, "#37474F", 8)
            val lp = LinearLayout.LayoutParams(
                dp(250),
                 dp(36)
            ).apply { topMargin = dp(5) }
            layoutParams = lp
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { onClick() }
        }
    }

    private fun roundedBg(fillHex: String, strokeHex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.parseColor(strokeHex))
        }
    }

    private fun onOff(v: Boolean): String = if (v) "ON" else "OFF"

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "SolStream Live Broadcast",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps hardware encoder and microphone active while streaming."
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentTitle("SolStream Studio")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
