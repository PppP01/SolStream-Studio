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
import java.lang.reflect.Method
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
        const val ACTION_TOGGLE_MIC_MUTE = "com.solgaming.solstreamstudio.TOGGLE_MIC_MUTE"
        const val ACTION_TOGGLE_BRB = "com.solgaming.solstreamstudio.TOGGLE_BRB"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIF_CHANNEL_ID = "solstream_live_channel"
        private const val NOTIF_ID = 4102
        private const val WAKELOCK_LEASE_MS = 12 * 60 * 60 * 1000L
        private const val BRB_SAVER_BITRATE_BPS = 1_500_000

        @Volatile var instance: SolStreamService? = null
        @Volatile var isAccessibilityConnected: Boolean = false
        @Volatile var isBroadcastLive: Boolean = false
        @Volatile var isGlipBenchmarkRunning: Boolean = false
        @Volatile var needsProjectionReapproval: Boolean = false
        @Volatile var connectionStateLabel: String = "IDLE"
        var onStateChangedListener: (() -> Unit)? = null
    }

    private data class SenderTelemetry(
        val queueBytes: Long,
        val queueFrames: Int,
        val queueCapacity: Int,
        val droppedFrames: Int,
        val hasCongestion: Boolean
    )

    private class RtmpTelemetryBridge(private val client: RtmpClient) {
        private var senderObj: Any? = null
        private var mClientItemsInCache: Method? = null
        private var mClientCacheSize: Method? = null
        private var mClientDroppedVideo: Method? = null
        private var mSenderQueueBytes: Method? = null
        private var mSenderItemsInCache: Method? = null
        private var mSenderCacheSize: Method? = null
        private var mSenderDroppedVideo: Method? = null

        init {
            try {
                mClientItemsInCache = client.javaClass.methods.firstOrNull {
                    it.name == "getItemsInCache" && it.parameterCount == 0
                }
                mClientCacheSize = client.javaClass.methods.firstOrNull {
                    it.name == "getCacheSize" && it.parameterCount == 0
                }
                mClientDroppedVideo = client.javaClass.methods.firstOrNull {
                    it.name == "getDroppedVideoFrames" && it.parameterCount == 0
                }

                val senderField = client.javaClass.declaredFields.firstOrNull { field ->
                    field.name.contains("Sender", ignoreCase = true) ||
                        field.type.simpleName.contains("Sender", ignoreCase = true)
                }
                if (senderField != null) {
                    senderField.isAccessible = true
                    val target = senderField.get(client)
                    senderObj = target
                    if (target != null) {
                        mSenderQueueBytes = target.javaClass.methods.firstOrNull {
                            it.name == "getQueueBytesOut" && it.parameterCount == 0
                        }
                        mSenderItemsInCache = target.javaClass.methods.firstOrNull {
                            it.name == "getItemsInCache" && it.parameterCount == 0
                        }
                        mSenderCacheSize = target.javaClass.methods.firstOrNull {
                            it.name == "getCacheSize" && it.parameterCount == 0
                        }
                        mSenderDroppedVideo = target.javaClass.methods.firstOrNull {
                            it.name == "getDroppedVideoFrames" && it.parameterCount == 0
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        fun poll(): SenderTelemetry {
            val sObj = senderObj
            val qBytes = invokeLong(mSenderQueueBytes, sObj)
            val qFrames = invokeInt(mClientItemsInCache, client)
                .let { if (it >= 0) it else invokeInt(mSenderItemsInCache, sObj) }
            val qCap = invokeInt(mClientCacheSize, client)
                .let { if (it > 0) it else invokeInt(mSenderCacheSize, sObj) }
            val drops = invokeInt(mClientDroppedVideo, client)
                .let { if (it >= 0) it else invokeInt(mSenderDroppedVideo, sObj) }
                .coerceAtLeast(0)
            val congestedFlag = try {
                client.hasCongestion(35f)
            } catch (_: Exception) {
                false
            }
            return SenderTelemetry(
                queueBytes = qBytes,
                queueFrames = qFrames,
                queueCapacity = qCap,
                droppedFrames = drops,
                hasCongestion = congestedFlag
            )
        }

        private fun invokeLong(method: Method?, target: Any?): Long {
            if (method == null || target == null) return -1L
            return try {
                (method.invoke(target) as? Number)?.toLong() ?: -1L
            } catch (_: Exception) {
                -1L
            }
        }

        private fun invokeInt(method: Method?, target: Any?): Int {
            if (method == null || target == null) return -1
            return try {
                (method.invoke(target) as? Number)?.toInt() ?: -1
            } catch (_: Exception) {
                -1
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var rtmpClient: RtmpClient? = null
    private var telemetryBridge: RtmpTelemetryBridge? = null
    private var videoPipeline: SolVideoPipeline? = null
    private var audioEngine: SolDualAudioEngine? = null
    private var mediaProjection: MediaProjection? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockAcquiredAtMs: Long = 0L
    private var wifiLock: WifiManager.WifiLock? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    private var activeSettings = StreamSettings()
    private var primaryEndpoint: String = ""
    private var backupEndpoint: String? = null
    private var usingBackupEndpoint = false

    @Volatile private var isRtmpConnected = false
    @Volatile private var awaitingKeyframeAfterConnect = true
    @Volatile private var reconnectScheduled = false

    private var streamStartElapsedMs = 0L
    private var reconnectCount = 0
    private val maxReconnects = 10
    private var stopConfirmTapTimeMs = 0L
    private var savedPreBrbBitrateBps = 20_000_000
    private var lastNotifStatusSummary = ""

    // Immutable byte copies of codec sequence headers for safe multi-hour reconnects
    @Volatile private var cachedSpsBytes: ByteArray? = null
    @Volatile private var cachedPpsBytes: ByteArray? = null
    @Volatile private var cachedVpsBytes: ByteArray? = null

    // Queue-aware adaptive bitrate state
    @Volatile private var lastMeasuredOutBps: Long = 0L
    private var prevDroppedFrames = 0
    private var prevQueueFrames = 0
    private var congestedTicks = 0
    private var stableTicks = 0
    private var abrReducedActive = false

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
                // Sleep UI redraws at 0 Hz when collapsed; poll health quietly every 2.5s
                mainHandler.postDelayed(this, 2500L)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsLogger.installPrivacySafeCrashHandler(this)
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
            ACTION_STOP_STREAM -> stopLiveBroadcast("Stopped by user")
            ACTION_TOGGLE_MIC_MUTE -> {
                if (isBroadcastLive) toggleStreamMicMute()
            }
            ACTION_TOGGLE_BRB -> {
                if (isBroadcastLive) toggleBrbShield()
            }
            ACTION_START_GLIP_BENCH -> startPassiveGlipBenchmark()
            ACTION_STOP_GLIP_BENCH -> stopPassiveGlipBenchmark()
        }
        return START_NOT_STICKY
    }

    private fun toggleStreamMicMute() {
        val a = audioEngine ?: return
        a.micDsp.streamMicMuted = !a.micDsp.streamMicMuted
        updateGhostPillAppearance()
        if (isOverlayExpanded) refreshExpandedOverlayText()
        refreshForegroundNotification(force = false)
    }

    private fun toggleBrbShield() {
        val v = videoPipeline ?: return
        val enteringBrb = !v.brbBlackoutActive
        if (enteringBrb) {
            savedPreBrbBitrateBps = v.currentBitrateBps.coerceAtLeast(3_000_000)
            v.brbBlackoutActive = true
            v.setBitrateOnFly(BRB_SAVER_BITRATE_BPS)
            if (activeSettings.brbMuteMicToo) {
                audioEngine?.micDsp?.streamMicMuted = true
            }
            DiagnosticsLogger.log(
                "BRB_ON",
                "BRB Privacy Blackout enabled; throttled video upload to 1.5 Mbps to save data & cool Wi-Fi.",
                "savedTarget=${savedPreBrbBitrateBps / 1000}kbps"
            )
        } else {
            v.brbBlackoutActive = false
            val restoreBps = max(savedPreBrbBitrateBps, activeSettings.targetBitrateKbps * 1000)
            v.setBitrateOnFly(restoreBps)
            v.requestSyncFrame()
            if (activeSettings.brbMuteMicToo) {
                audioEngine?.micDsp?.streamMicMuted = false
            }
            congestedTicks = 0
            stableTicks = 0
            DiagnosticsLogger.log(
                "BRB_OFF",
                "BRB Privacy Blackout ended; restored full gameplay bitrate and sent fresh keyframe.",
                "restored=${restoreBps / 1000}kbps"
            )
        }
        updateGhostPillAppearance()
        if (isOverlayExpanded) refreshExpandedOverlayText()
        refreshForegroundNotification(force = false)
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

    private fun copyBufferBytes(buf: ByteBuffer?): ByteArray? {
        if (buf == null) return null
        val dup = buf.duplicate()
        val out = ByteArray(dup.remaining())
        dup.get(out)
        return out
    }

    private fun sendCachedHeadersToClient(client: RtmpClient) {
        val sps = cachedSpsBytes ?: return
        val pps = cachedPpsBytes
        val vps = cachedVpsBytes
        try {
            client.setVideoInfo(
                ByteBuffer.wrap(sps),
                pps?.let { ByteBuffer.wrap(it) },
                vps?.let { ByteBuffer.wrap(it) }
            )
        } catch (_: Exception) {
        }
    }

    private fun startLiveBroadcast(resultCode: Int, resultData: Intent) {
        if (isBroadcastLive) return
        if (isGlipBenchmarkRunning) stopPassiveGlipBenchmark()

        val endpoint = KeystoreVault.buildFullStreamEndpoint(this)
        if (endpoint.isNullOrBlank()) {
            Toast.makeText(this, "Paste and save your Stream Key first!", Toast.LENGTH_LONG).show()
            return
        }

        activeSettings = KeystoreVault.loadSettings(this)
        primaryEndpoint = endpoint
        backupEndpoint = KeystoreVault.buildBackupStreamEndpoint(this)
        usingBackupEndpoint = false
        needsProjectionReapproval = false
        reconnectScheduled = false
        awaitingKeyframeAfterConnect = true
        reconnectCount = 0
        prevDroppedFrames = 0
        prevQueueFrames = 0
        congestedTicks = 0
        stableTicks = 0
        abrReducedActive = false
        lastMeasuredOutBps = 0L
        savedPreBrbBitrateBps = activeSettings.targetBitrateKbps * 1000
        lastNotifStatusSummary = ""

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
        // Cap sender queue at 120 frames (~0.75s audio+video) instead of 400 to prevent CODM Wi-Fi bufferbloat
        try {
            client.resizeCache(120)
        } catch (_: Exception) {
        }
        rtmpClient = client
        telemetryBridge = RtmpTelemetryBridge(client)

        // 2. Start Hardware Video Pipeline
        val vPipe = SolVideoPipeline(this)
        vPipe.onFatalPipelineError = { errMsg ->
            mainHandler.post { handleFatalVideoError(errMsg) }
        }
        videoPipeline = vPipe
        val activeVideo = vPipe.startPipeline(
            settings = activeSettings,
            mediaProjection = projection,
            originTimeNs = originNs,
            onProjectionStoppedBySystem = {
                mainHandler.post { handleSystemProjectionStop() }
            },
            onVideoConfig = { sps, pps, vps ->
                cachedSpsBytes = copyBufferBytes(sps)
                cachedPpsBytes = copyBufferBytes(pps)
                cachedVpsBytes = copyBufferBytes(vps)
                sendCachedHeadersToClient(client)
            },
            onEncodedVideo = { buf, info ->
                if (isRtmpConnected) {
                    if (awaitingKeyframeAfterConnect) {
                        val isKey = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        if (!isKey) {
                            return@startPipeline
                        }
                        awaitingKeyframeAfterConnect = false
                    }
                    try {
                        client.sendVideo(buf.duplicate(), info)
                    } catch (_: Exception) {
                    }
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
                mainHandler.post {
                    updateGhostPillAppearance()
                    if (isOverlayExpanded) refreshExpandedOverlayText()
                }
            },
            onEncodedAac = { aacBuf, info ->
                if (isRtmpConnected) {
                    try {
                        client.sendAudio(aacBuf.duplicate(), info)
                    } catch (_: Exception) {
                    }
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
        refreshForegroundNotification(force = true)

        // 5. Connect RTMPS on background thread
        val connectTarget = primaryEndpoint
        Thread({
            try {
                client.connect(connectTarget)
            } catch (e: Exception) {
                DiagnosticsLogger.log("NET_ERR", "RTMPS connection exception (${e.javaClass.simpleName}).", e.message ?: "")
            }
        }, "SolRtmpsConnect").start()

        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, 1500L)
        onStateChangedListener?.invoke()
    }

    private fun handleFatalVideoError(errMsg: String) {
        if (!isBroadcastLive || needsProjectionReapproval) return
        needsProjectionReapproval = true
        connectionStateLabel = "VIDEO RESET (RE-APPROVE)"
        videoPipeline?.stopPipeline()
        updateGhostPillAppearance()
        if (isOverlayExpanded) refreshExpandedOverlayText()
        refreshForegroundNotification(force = true)
        Toast.makeText(
            this,
            "Video hardware encoder reset ($errMsg). Open SolStream Studio to re-approve capture.",
            Toast.LENGTH_LONG
        ).show()
        onStateChangedListener?.invoke()
    }

    private fun handleSystemProjectionStop() {
        if (!isBroadcastLive) return
        needsProjectionReapproval = true
        connectionStateLabel = "CAPTURE PAUSED (RE-APPROVE)"
        videoPipeline?.stopPipeline()
        updateGhostPillAppearance()
        if (isOverlayExpanded) refreshExpandedOverlayText()
        refreshForegroundNotification(force = true)
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
        reconnectScheduled = false
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
        telemetryBridge = null
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

        cachedSpsBytes = null
        cachedPpsBytes = null
        cachedVpsBytes = null
        releaseLocksAndThermalMonitor()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }

    // === CONNECTCHECKER CALLBACKS ===
    override fun onConnectionStarted(url: String) {
        connectionStateLabel = if (usingBackupEndpoint) "CONNECTING (BACKUP)" else "CONNECTING"
        val serverTag = if (usingBackupEndpoint) "Backup RTMPS server" else "Primary RTMPS server"
        DiagnosticsLogger.log("NETWORK", "Connecting to $serverTag...")
        mainHandler.post {
            updateGhostPillAppearance()
            refreshForegroundNotification(force = false)
        }
    }

    override fun onConnectionSuccess() {
        awaitingKeyframeAfterConnect = true
        isRtmpConnected = true
        reconnectScheduled = false
        reconnectCount = 0
        connectionStateLabel = if (usingBackupEndpoint) "LIVE (BACKUP)" else "LIVE"
        val client = rtmpClient
        if (client != null) {
            sendCachedHeadersToClient(client)
        }
        videoPipeline?.requestSyncFrame()
        val serverTag = if (usingBackupEndpoint) "YouTube Backup RTMPS" else "YouTube Primary RTMPS"
        DiagnosticsLogger.log("NETWORK", "Connected to $serverTag ingest. Stream is LIVE.")
        mainHandler.post {
            updateGhostPillAppearance()
            if (isOverlayExpanded) refreshExpandedOverlayText()
            refreshForegroundNotification(force = true)
            onStateChangedListener?.invoke()
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        lastMeasuredOutBps = bitrate
    }

    override fun onConnectionFailed(reason: String) {
        isRtmpConnected = false
        awaitingKeyframeAfterConnect = true
        if (!isBroadcastLive || needsProjectionReapproval || reconnectScheduled) return

        if (reconnectCount < maxReconnects) {
            reconnectScheduled = true
            reconnectCount++
            DiagnosticsLogger.reconnectAttempts = reconnectCount

            // Switch cleanly to YouTube Backup server on even retries if a backup endpoint is available
            val bkp = backupEndpoint
            if (!bkp.isNullOrBlank() && reconnectCount >= 2) {
                usingBackupEndpoint = (reconnectCount % 2 == 0)
            } else {
                usingBackupEndpoint = false
            }
            val nextTargetUrl = if (usingBackupEndpoint && !bkp.isNullOrBlank()) bkp else primaryEndpoint
            val targetName = if (usingBackupEndpoint) "Backup Server" else "Primary Server"

            val delayMs = min(15_000L, 2000L * reconnectCount)
            connectionStateLabel = "RETRY $reconnectCount/$maxReconnects"
            DiagnosticsLogger.log(
                "NET_RETRY",
                "Upload interrupted. Reconnecting ($reconnectCount/$maxReconnects) via $targetName in ${delayMs / 1000}s.",
                reason
            )
            mainHandler.post {
                updateGhostPillAppearance()
                if (isOverlayExpanded) refreshExpandedOverlayText()
                refreshForegroundNotification(force = false)
            }
            mainHandler.postDelayed({
                reconnectScheduled = false
                val client = rtmpClient
                if (isBroadcastLive && !needsProjectionReapproval && client != null) {
                    Thread({
                        try {
                            client.disconnect()
                            sendCachedHeadersToClient(client)
                            awaitingKeyframeAfterConnect = true
                            client.connect(nextTargetUrl, true)
                        } catch (_: Exception) {
                        }
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
        awaitingKeyframeAfterConnect = true
    }

    override fun onAuthError() {
        DiagnosticsLogger.log("AUTH_ERR", "Server rejected the Stream Key (Auth Error). Verify your key in Pre-Stream Studio.")
        mainHandler.post {
            Toast.makeText(this, "Stream Key rejected by server!", Toast.LENGTH_LONG).show()
            stopLiveBroadcast("Authentication error")
        }
    }

    override fun onAuthSuccess() {}

    // === SOLSTREAM HEALTH, 12H WAKELOCK RENEWAL & LOBBY-SAFE ABR 2.0 CONTROLLER ===
    private fun runHealthAndRecoveryTick() {
        val (_, thermal) = DiagnosticsLogger.sampleDeviceHealth(this)
        if (!isBroadcastLive) return

        // Renew WakeLock well before 12h lease expiry for uninterrupted marathon broadcasts
        renewWakeLockIfNeeded()

        // If awaiting post-connect keyframe, nudge hardware encoder to emit IDR immediately
        if (isRtmpConnected && awaitingKeyframeAfterConnect) {
            videoPipeline?.requestSyncFrame()
        }

        // 1. Poll real sender queue frames, bytes, capacity & dropped frames
        val tel = telemetryBridge?.poll() ?: SenderTelemetry(-1L, -1, -1, 0, false)
        DiagnosticsLogger.lastQueueBytes = tel.queueBytes
        DiagnosticsLogger.lastQueueFrames = tel.queueFrames
        DiagnosticsLogger.lastQueueCapacity = tel.queueCapacity
        DiagnosticsLogger.droppedVideoFrames = tel.droppedFrames

        val droppedDelta = max(0, tel.droppedFrames - prevDroppedFrames)
        prevDroppedFrames = tel.droppedFrames

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

        // 3. Lobby-Safe & BRB-Safe Queue-Aware Adaptive Bitrate 2.0
        if (vPipe != null && vPipe.brbBlackoutActive) {
            // Do not run ABR while BRB low-bitrate saver (1.5 Mbps) is active
            congestedTicks = 0
            stableTicks = 0
            prevQueueFrames = max(0, tel.queueFrames)
            return
        }

        if (activeSettings.adaptiveBitrate && isRtmpConnected && vPipe != null) {
            val currentTargetBps = vPipe.currentBitrateBps
            val maxConfiguredBps = activeSettings.targetBitrateKbps * 1000
            val outBps = lastMeasuredOutBps

            // Compare outgoing network rate against ACTUAL encoder production (never penalizing calm lobbies!)
            val producedBps = vPipe.actualEncodedBitrateBps.toLong() + (activeSettings.audioBitrateKbps * 1000L)
            val qFrames = tel.queueFrames
            val qBytes = tel.queueBytes
            val queueGrowingFast = qFrames >= 16 && prevQueueFrames >= 0 && (qFrames - prevQueueFrames) >= 6
            prevQueueFrames = max(0, qFrames)

            val senderfallingBehindEncoder = producedBps > 3_000_000L &&
                qFrames >= 12 &&
                outBps in 1L until (producedBps * 0.70).toLong()

            val realCongestion = tel.hasCongestion ||
                droppedDelta > 0 ||
                (qFrames >= 30) ||
                (qBytes > 650_000L) ||
                queueGrowingFast ||
                senderfallingBehindEncoder

            if (realCongestion) {
                congestedTicks++
                stableTicks = 0
                if (congestedTicks >= 2 || droppedDelta > 0) {
                    congestedTicks = 0
                    val reduced = max(3_500_000, (currentTargetBps * 0.82).toInt())
                    if (reduced < currentTargetBps) {
                        vPipe.setBitrateOnFly(reduced)
                        abrReducedActive = true
                        val qDesc = when {
                            qFrames >= 0 && tel.queueCapacity > 0 -> "$qFrames/${tel.queueCapacity} frames"
                            qBytes >= 0L -> "${qBytes / 1024} KB"
                            else -> "drops=$droppedDelta"
                        }
                        DiagnosticsLogger.log(
                            "ABR_DOWN",
                            "Real upload backlog detected ($qDesc). Stepped video target to ${String.format(Locale.US, "%.1f", reduced / 1_000_000.0)} Mbps to protect CODM ping.",
                            "prod=${producedBps / 1000}kbps sent=${outBps / 1000}kbps drops=+$droppedDelta"
                        )
                    }
                }
            } else {
                congestedTicks = 0
                val queueCalm = (qFrames <= 5) && (qBytes < 180_000L) && droppedDelta == 0
                if (currentTargetBps < maxConfiguredBps && queueCalm) {
                    stableTicks++
                    if (stableTicks >= 4) {
                        stableTicks = 0
                        val steppedUp = min(maxConfiguredBps, currentTargetBps + 1_200_000)
                        vPipe.setBitrateOnFly(steppedUp)
                        if (steppedUp >= maxConfiguredBps && abrReducedActive) {
                            abrReducedActive = false
                            DiagnosticsLogger.log(
                                "ABR_UP",
                                "Network clear; restored full ${String.format(Locale.US, "%.1f", steppedUp / 1_000_000.0)} Mbps video bitrate.",
                                "sent=${outBps / 1000}kbps"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun renewWakeLockIfNeeded() {
        val wl = wakeLock ?: return
        val now = SystemClock.elapsedRealtime()
        if (!wl.isHeld || now - wakeLockAcquiredAtMs > (WAKELOCK_LEASE_MS - 30 * 60 * 1000L)) {
            try {
                wl.acquire(WAKELOCK_LEASE_MS)
                wakeLockAcquiredAtMs = now
            } catch (_: Exception) {
            }
        }
    }

    private fun acquireLocksAndThermalMonitor(s: StreamSettings) {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SolStream::BroadcastWakeLock").apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_LEASE_MS)
            }
            wakeLockAcquiredAtMs = SystemClock.elapsedRealtime()

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
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(lockMode, "SolStream::LowLatencyWifi").apply {
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
            alpha = 0.38f
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
                        val dm = resources.displayMetrics
                        val maxX = max(dp(40), dm.widthPixels - dp(90))
                        val maxY = max(dp(40), dm.heightPixels - dp(48))
                        params.x = (startParamX + dx).coerceIn(0, maxX)
                        params.y = (startParamY + dy).coerceIn(0, maxY)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
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
            toggleStreamMicMute()
        }

        btnGameMute = smallActionButton("GAME + SQUAD STREAM: ON", "#1565C0") {
            val a = audioEngine ?: return@smallActionButton
            a.gameStreamMuted = !a.gameStreamMuted
            refreshExpandedOverlayText()
        }

        btnBrbShield = smallActionButton("BRB Privacy Blackout: OFF", "#37474F") {
            toggleBrbShield()
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
            savedPreBrbBitrateBps = next * 1000
            KeystoreVault.saveSettings(this, activeSettings)
            if (videoPipeline?.brbBlackoutActive != true) {
                videoPipeline?.setBitrateOnFly(next * 1000)
            }
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
        val encFps = videoPipeline?.actualEncodedFps ?: 0
        val freshFps = videoPipeline?.actualFreshCaptureFps ?: 0
        val qFrames = DiagnosticsLogger.lastQueueFrames
        val qBytes = DiagnosticsLogger.lastQueueBytes
        val qTag = when {
            qFrames >= 0 -> "Q:${qFrames}f"
            qBytes >= 0L -> "Q:${qBytes / 1024}KB"
            else -> "Q:N/A"
        }

        tvHeaderStats?.text = String.format(
            Locale.US,
            "%02d:%02d:%02d | %.1fM | %d/%d FPS | %s",
            hh, mm, ss, outMbps, freshFps, encFps, qTag
        )

        val micState = audioEngine?.micStatusText ?: "MIC IDLE"
        val micDb = audioEngine?.micDsp?.lastPeakDb?.toInt() ?: -90
        val gameDb = audioEngine?.lastGamePeakDb?.toInt() ?: -90
        val gameState = when {
            audioEngine?.gameAudioActive != true -> "MIC-ONLY"
            audioEngine?.gameStreamMuted == true -> "GAME MUTED"
            else -> "GAME OK ($gameDb dB)"
        }
        tvBadges?.text = "$connectionStateLabel • $micState ($micDb dB) • $gameState"

        val micMuted = audioEngine?.micDsp?.streamMicMuted == true
        btnMicMute?.text = if (micMuted) "Stream Mic: MUTED (Squad Still Hears You)" else "Stream Mic: ON ($micDb dB)"
        btnMicMute?.background = roundedBg(if (micMuted) "#B71C1C" else "#00695C", "#37474F", 8)

        val gMuted = audioEngine?.gameStreamMuted == true
        btnGameMute?.text = if (gMuted) "GAME + SQUAD STREAM: MUTED" else "GAME + SQUAD STREAM: ON ($gameDb dB)"
        btnGameMute?.background = roundedBg(if (gMuted) "#B71C1C" else "#1565C0", "#37474F", 8)

        val brb = videoPipeline?.brbBlackoutActive == true
        btnBrbShield?.text = if (brb) "BRB Privacy Blackout: ACTIVE (1.5M Saver)" else "BRB Privacy Blackout: OFF"
        btnBrbShield?.background = roundedBg(if (brb) "#EF6C00" else "#37474F", "#455A64", 8)

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

    private fun refreshForegroundNotification(force: Boolean) {
        if (!isBroadcastLive) return
        val micMuted = audioEngine?.micDsp?.streamMicMuted == true
        val brb = videoPipeline?.brbBlackoutActive == true
        val summary = when {
            needsProjectionReapproval -> "Capture Paused • Tap to Re-Approve"
            brb -> "$connectionStateLabel • BRB Blackout Active (1.5M)"
            micMuted -> "$connectionStateLabel • Stream Mic MUTED"
            else -> "$connectionStateLabel • Stream Mic ON"
        }
        if (!force && summary == lastNotifStatusSummary) return
        lastNotifStatusSummary = summary
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildForegroundNotification(summary))
        } catch (_: Exception) {
        }
    }

    private fun serviceActionPendingIntent(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(this, SolStreamService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        val micMuted = audioEngine?.micDsp?.streamMicMuted == true
        val brb = videoPipeline?.brbBlackoutActive == true

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentTitle("SolStream Studio")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .addAction(
                0,
                if (micMuted) "Unmute Mic" else "Mute Mic",
                serviceActionPendingIntent(ACTION_TOGGLE_MIC_MUTE, 101)
            )
            .addAction(
                0,
                if (brb) "Resume Video" else "BRB Blackout",
                serviceActionPendingIntent(ACTION_TOGGLE_BRB, 102)
            )
            .addAction(
                0,
                "Stop",
                serviceActionPendingIntent(ACTION_STOP_STREAM, 103)
            )
            .build()
    }
}
