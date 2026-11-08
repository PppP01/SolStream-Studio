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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import java.io.File
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
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
        const val ACTION_MARK_CLIP = "com.solgaming.solstreamstudio.MARK_CLIP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIF_CHANNEL_ID = "solstream_live_channel"
        private const val NOTIF_ID = 4102
        private const val WAKELOCK_LEASE_MS = 12 * 60 * 60 * 1000L
        private const val SCENE_SAVER_BITRATE_BPS = 1_500_000

        private const val REPORT_HISTORY_FILE = "solstream_report_history.txt"
        private const val REPORT_DELIMITER = "\n==================== SAVED SESSION ARCHIVE ====================\n"
        private const val MAX_ARCHIVED_REPORTS = 10

        val MASTER_BITRATE_LADDER_KBPS = listOf(
            500, 800, 1000, 1500, 2000, 3000, 4000, 6000, 8000,
            10000, 12000, 16000, 18000, 20000, 22000, 24000,
            30000, 35000, 45000, 50000, 60000, 65000, 80000, 100000
        )

        val GAME_GAIN_STEPS = listOf(0, 50, 80, 100, 125, 150, 180, 220, 250)
        val MIC_GAIN_STEPS = listOf(75, 100, 125, 150, 180, 220, 250)

        @Volatile var instance: SolStreamService? = null
        @Volatile var isAccessibilityConnected: Boolean = false
        @Volatile var isBroadcastLive: Boolean = false
        @Volatile var isGlipBenchmarkRunning: Boolean = false
        @Volatile var needsProjectionReapproval: Boolean = false
        @Volatile var connectionStateLabel: String = "IDLE"
        var onStateChangedListener: (() -> Unit)? = null

        /**
         * Returns true ONLY when valid non-negative queue readings exist and are within calm thresholds.
         * Never mistakes -1 (unavailable telemetry) for a calm empty queue!
         */
        internal fun isQueueCalmForAbrRecovery(
            qFrames: Int,
            qBytes: Long,
            droppedDelta: Int
        ): Boolean {
            if (droppedDelta != 0) return false
            val hasValidFrames = qFrames in 0..5
            val hasValidBytes = qBytes in 0L until 180_000L
            val framesOk = hasValidFrames || (qFrames == -1 && hasValidBytes)
            val bytesOk = hasValidBytes || (qBytes == -1L && hasValidFrames)
            return (hasValidFrames || hasValidBytes) && framesOk && bytesOk
        }

        /**
         * Verifies that a connection callback belongs to the currently active broadcast session
         * and that the target has not been cancelled/stopped.
         */
        internal fun shouldAcceptConnectionCallback(
            isBroadcastActive: Boolean,
            callbackGen: Long,
            currentGen: Long,
            isTargetStopped: Boolean = false
        ): Boolean {
            return isBroadcastActive && !isTargetStopped && callbackGen > 0L && callbackGen == currentGen
        }

        @Synchronized
        fun archiveCompletedSessionReport(context: Context, reportText: String) {
            try {
                val file = File(context.applicationContext.filesDir, REPORT_HISTORY_FILE)
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entry = ">>> ARCHIVED STREAM: $stamp <<<\n${reportText.trim()}\n"
                val existing = if (file.exists()) {
                    file.readText()
                        .split(REPORT_DELIMITER)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
                val combined = (listOf(entry) + existing).take(MAX_ARCHIVED_REPORTS)
                file.writeText(combined.joinToString(REPORT_DELIMITER))
            } catch (_: Exception) {
            }
        }

        @Synchronized
        fun loadArchivedReports(context: Context): String {
            return try {
                val file = File(context.applicationContext.filesDir, REPORT_HISTORY_FILE)
                if (!file.exists() || file.length() == 0L) {
                    "No archived session reports yet. Completed live streams are saved here automatically until you delete them."
                } else {
                    file.readText()
                }
            } catch (_: Exception) {
                "Could not read archived reports."
            }
        }

        @Synchronized
        fun getArchivedReportCount(context: Context): Int {
            return try {
                val file = File(context.applicationContext.filesDir, REPORT_HISTORY_FILE)
                if (!file.exists() || file.length() == 0L) 0
                else file.readText().split(REPORT_DELIMITER).count { it.isNotBlank() }
            } catch (_: Exception) {
                0
            }
        }

        @Synchronized
        fun clearArchivedReports(context: Context): Boolean {
            return try {
                val file = File(context.applicationContext.filesDir, REPORT_HISTORY_FILE)
                if (file.exists()) file.delete() else true
            } catch (_: Exception) {
                false
            }
        }
    }

    private data class SenderTelemetry(
        val queueBytes: Long,
        val queueFrames: Int,
        val queueCapacity: Int,
        val droppedFrames: Int,
        val rttMs: Long,
        val hasCongestion: Boolean
    )

    private class RtmpTelemetryBridge(private val client: RtmpClient) {
        private var senderObj: Any? = null
        private var commandsObj: Any? = null
        private var mClientItemsInCache: Method? = null
        private var mClientCacheSize: Method? = null
        private var mClientDroppedVideo: Method? = null
        private var mClientRtt: Method? = null
        private var mSenderQueueBytes: Method? = null
        private var mSenderItemsInCache: Method? = null
        private var mSenderCacheSize: Method? = null
        private var mSenderDroppedVideo: Method? = null
        private var mCommandsRtt: Method? = null

        init {
            try {
                client.javaClass.methods.firstOrNull {
                    it.name == "shouldSendPings" && it.parameterCount == 1
                }?.invoke(client, true)
            } catch (_: Exception) {
            }

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
                mClientRtt = client.javaClass.methods.firstOrNull {
                    (it.name.equals("getRtt", ignoreCase = true) || it.name.equals("getPing", ignoreCase = true)) &&
                        it.parameterCount == 0
                }

                for (field in client.javaClass.declaredFields) {
                    if (field.name.contains("Sender", ignoreCase = true) ||
                        field.type.simpleName.contains("Sender", ignoreCase = true)
                    ) {
                        field.isAccessible = true
                        val target = field.get(client)
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
                    } else if (field.name.contains("commandsManager", ignoreCase = true) ||
                        field.type.simpleName.contains("CommandsManager", ignoreCase = true)
                    ) {
                        field.isAccessible = true
                        val cmdTarget = field.get(client)
                        commandsObj = cmdTarget
                        if (cmdTarget != null) {
                            mCommandsRtt = cmdTarget.javaClass.methods.firstOrNull {
                                (it.name.equals("getRtt", ignoreCase = true) || it.name.equals("getPing", ignoreCase = true)) &&
                                    it.parameterCount == 0
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        fun poll(): SenderTelemetry {
            val sObj = senderObj
            val cObj = commandsObj
            val qBytes = invokeLong(mSenderQueueBytes, sObj)
            val qFrames = invokeInt(mClientItemsInCache, client)
                .let { if (it >= 0) it else invokeInt(mSenderItemsInCache, sObj) }
            val qCap = invokeInt(mClientCacheSize, client)
                .let { if (it > 0) it else invokeInt(mSenderCacheSize, sObj) }
            val rawDrops = invokeInt(mClientDroppedVideo, client)
                .let { if (it >= 0) it else invokeInt(mSenderDroppedVideo, sObj) }
            val rawRtt = invokeLong(mClientRtt, client)
                .let { if (it > 0L) it else invokeLong(mCommandsRtt, cObj) }
            val rttMs = if (rawRtt > 0L) rawRtt else -1L
            val congestedFlag = try {
                client.hasCongestion(35f)
            } catch (_: Exception) {
                false
            }
            return SenderTelemetry(
                queueBytes = qBytes,
                queueFrames = qFrames,
                queueCapacity = qCap,
                droppedFrames = rawDrops,
                rttMs = rttMs,
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

    /**
     * Independent secondary RTMPS transport node for single-encode Simulcast (Twitch / Kick)
     * or Dual-Format 9:16 Shorts Companion streaming, guarded by session generation & stop state.
     */
    private inner class AuxiliaryRtmpTarget(
        val name: String,
        val endpointUrl: String,
        val isCompanionVertical: Boolean,
        val generationId: Long,
        videoCodec: VideoCodec,
        width: Int,
        height: Int,
        fps: Int
    ) : ConnectChecker {
        val client = RtmpClient(this)
        @Volatile var isConnected = false
        @Volatile var isStopped = false
        @Volatile var awaitingKeyframe = true
        @Volatile var statusTag = "$name:WAIT"
        private var retries = 0
        private val maxAuxRetries = 6
        private var lastFailElapsed = -10_000L

        init {
            client.tlsHostVerification = true
            client.setReTries(maxAuxRetries)
            try { client.resizeCache(90) } catch (_: Exception) {}
            client.setVideoCodec(videoCodec)
            client.setAudioCodec(AudioCodec.AAC)
            client.setAudioInfo(48000, true)
            client.setVideoResolution(width, height)
            client.setFps(fps)
        }

        private fun isActiveTarget(): Boolean {
            return shouldAcceptConnectionCallback(
                isBroadcastActive = isBroadcastLive,
                callbackGen = generationId,
                currentGen = sessionGeneration.get(),
                isTargetStopped = isStopped
            )
        }

        fun startAsync() {
            if (!isActiveTarget()) return
            Thread({
                if (!isActiveTarget()) return@Thread
                try {
                    client.connect(endpointUrl)
                    if (!isActiveTarget()) {
                        try { client.disconnect() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    if (isActiveTarget()) {
                        DiagnosticsLogger.log("SIMULCAST", "$name connect error (${e.javaClass.simpleName}).")
                    }
                }
            }, "SolAux_$name").start()
        }

        fun pushHeaders(sps: ByteArray?, pps: ByteArray?, vps: ByteArray?) {
            if (!isActiveTarget()) return
            val s = sps ?: return
            try {
                client.setVideoInfo(
                    ByteBuffer.wrap(s),
                    pps?.let { ByteBuffer.wrap(it) },
                    vps?.let { ByteBuffer.wrap(it) }
                )
            } catch (_: Exception) {
            }
        }

        fun sendVideoPacket(buf: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (!isConnected || !isActiveTarget()) return
            if (awaitingKeyframe) {
                val isKey = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                if (!isKey) return
                awaitingKeyframe = false
            }
            try {
                val infoCopy = MediaCodec.BufferInfo().apply {
                    set(info.offset, info.size, info.presentationTimeUs, info.flags)
                }
                client.sendVideo(buf.duplicate(), infoCopy)
            } catch (_: Exception) {
            }
        }

        fun sendAudioPacket(buf: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (!isConnected || !isActiveTarget()) return
            try {
                val infoCopy = MediaCodec.BufferInfo().apply {
                    set(info.offset, info.size, info.presentationTimeUs, info.flags)
                }
                client.sendAudio(buf.duplicate(), infoCopy)
            } catch (_: Exception) {
            }
        }

        fun stopAsync() {
            isStopped = true
            isConnected = false
            statusTag = "$name:OFF"
            Thread({
                try { client.disconnect() } catch (_: Exception) {}
            }, "SolAuxStop_$name").start()
        }

        override fun onConnectionStarted(url: String) {
            if (!isActiveTarget()) return
            statusTag = "$name:CONN"
        }

        override fun onConnectionSuccess() {
            if (!isActiveTarget()) {
                isConnected = false
                statusTag = "$name:OFF"
                Thread({
                    try { client.disconnect() } catch (_: Exception) {}
                }, "SolAuxLateDisc_$name").start()
                return
            }
            awaitingKeyframe = true
            isConnected = true
            retries = 0
            statusTag = "$name:LIVE"
            if (isCompanionVertical) {
                pushHeaders(compCachedSpsBytes, compCachedPpsBytes, compCachedVpsBytes)
            } else {
                pushHeaders(cachedSpsBytes, cachedPpsBytes, cachedVpsBytes)
            }
            videoPipeline?.requestSyncFrame()
            DiagnosticsLogger.log("SIMULCAST", "$name target connected and LIVE.")
        }

        override fun onNewBitrate(bitrate: Long) {}

        override fun onConnectionFailed(reason: String) {
            isConnected = false
            awaitingKeyframe = true
            if (!isActiveTarget()) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastFailElapsed < 1800L) return
            lastFailElapsed = now
            if (retries < maxAuxRetries) {
                retries++
                statusTag = "$name:RETRY$retries"
                val delay = min(12_000L, 2500L * retries)
                Thread({
                    if (!isActiveTarget()) return@Thread
                    try { client.reConnect(delay, endpointUrl) } catch (_: Exception) {}
                }, "SolAuxReconn_$name").start()
            } else {
                statusTag = "$name:DROP"
                DiagnosticsLogger.log("SIMULCAST", "$name target stopped after $maxAuxRetries retries.", reason)
            }
        }

        override fun onDisconnect() {
            isConnected = false
            awaitingKeyframe = true
        }

        override fun onAuthError() {
            isConnected = false
            if (!isActiveTarget()) return
            statusTag = "$name:AUTH_ERR"
            DiagnosticsLogger.log("SIMULCAST", "$name rejected Stream Key (Auth Error).")
        }

        override fun onAuthSuccess() {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionGeneration = AtomicLong(0L)
    @Volatile private var activeClientGeneration = 0L

    private var windowManager: WindowManager? = null
    private var rtmpClient: RtmpClient? = null
    private var telemetryBridge: RtmpTelemetryBridge? = null
    private val simulcastNodes = CopyOnWriteArrayList<AuxiliaryRtmpTarget>()
    private var companionNode: AuxiliaryRtmpTarget? = null

    private var videoPipeline: SolVideoPipeline? = null
    private var audioEngine: SolDualAudioEngine? = null
    private var mediaProjection: MediaProjection? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockAcquiredAtMs: Long = 0L
    private var wifiLock: WifiManager.WifiLock? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastValidatedNetworkHandle: Long = -1L

    private var activeSettings = StreamSettings()
    private var primaryEndpoint: String = ""
    private var backupEndpoint: String? = null
    private var usingBackupEndpoint = false
    private var sessionOriginNs: Long = 0L

    @Volatile private var isRtmpConnected = false
    @Volatile private var awaitingKeyframeAfterConnect = true

    @Volatile private var lastVideoClockOffsetUs: Long = 0L
    @Volatile private var lastAudioClockOffsetUs: Long = 0L
    @Volatile private var hasVideoOffset: Boolean = false
    @Volatile private var hasAudioOffset: Boolean = false

    private var streamStartElapsedMs = 0L
    private var reconnectCount = 0
    private val maxReconnects = 10
    private var lastFailHandledElapsedMs = -10_000L
    private var currentRetryDelayMs = 2000L
    private var stalledZeroSendTicks = 0

    private var audioRestartAttempts = 0
    private var audioRestartInProgress = false

    private var stopConfirmTapTimeMs = 0L
    private var savedPreBrbBitrateBps = 20_000_000
    private var brbAutoMutedMic = false
    private var userManuallyOverrodeSharp = false
    private var userManuallyOverrode30FpsSaver = false
    private var lowBatt15Warned = false
    private var lowBatt5Warned = false
    private var lastNotifStatusSummary = ""

    @Volatile private var cachedSpsBytes: ByteArray? = null
    @Volatile private var cachedPpsBytes: ByteArray? = null
    @Volatile private var cachedVpsBytes: ByteArray? = null
    @Volatile private var compCachedSpsBytes: ByteArray? = null
    @Volatile private var compCachedPpsBytes: ByteArray? = null
    @Volatile private var compCachedVpsBytes: ByteArray? = null

    @Volatile private var lastMeasuredOutBps: Long = 0L
    private var prevDroppedFrames = 0
    private var prevQueueFrames = 0
    private var congestedTicks = 0
    private var stableTicks = 0
    private var abrReducedActive = false

    // === FLOATING ACCESSIBILITY OVERLAY (SLEEPING GHOST DOT + 3-TAB COMPACT STUDIO) ===
    private var overlayRoot: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var ghostPillView: TextView? = null
    private var expandedPanel: ScrollView? = null
    private var isOverlayExpanded = false
    private var activeOverlayTab = 0

    private var tvHeaderStats: TextView? = null
    private var tvBadges: TextView? = null

    private var btnQuickMic: Button? = null
    private var btnQuickGame: Button? = null
    private var btnQuickScene: Button? = null
    private var btnQuickMarkClip: Button? = null

    private var btnTabAudio: Button? = null
    private var btnTabVideo: Button? = null
    private var btnTabNet: Button? = null
    private var tabBoxAudio: LinearLayout? = null
    private var tabBoxVideo: LinearLayout? = null
    private var tabBoxNet: LinearLayout? = null

    private var btnVoiceGender: Button? = null
    private var btnVoicePreset: Button? = null
    private var btnNoiseSuppress: Button? = null
    private var btnHpfAndGate: Button? = null
    private var btnVoiceStack: Button? = null
    private var btnMicGain: Button? = null
    private var btnGameGain: Button? = null
    private var btnGameSoundstageA: Button? = null
    private var btnGameSoundstageB: Button? = null
    private var btnDucking: Button? = null
    private var btnLiveMicInput: Button? = null

    private var btnSceneCycle: Button? = null
    private var btnUpscalerAndFraming: Button? = null
    private var btnFilmicAndVivid: Button? = null
    private var btnShadowAndTemp: Button? = null
    private var btnFlipAndSharp: Button? = null
    private var btnOverlaysAndShield: Button? = null

    private var btnBitrateDown: Button? = null
    private var btnBitrateUp: Button? = null
    private var tvBitrateCurrent: TextView? = null
    private var btnAbrToggle: Button? = null
    private var btnThermalCooldown: Button? = null
    private var tvSimulcastStatus: TextView? = null
    private var btnStopStream: Button? = null

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!isBroadcastLive && !isGlipBenchmarkRunning) return
            runHealthAndRecoveryTick()
            if (isOverlayExpanded && isBroadcastLive) {
                refreshExpandedOverlayText()
                mainHandler.postDelayed(this, 1000L)
            } else {
                mainHandler.postDelayed(this, 2000L)
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

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
            ACTION_MARK_CLIP -> {
                if (isBroadcastLive) recordLiveClipMarker("Notification Marker")
            }
            ACTION_START_GLIP_BENCH -> startPassiveGlipBenchmark()
            ACTION_STOP_GLIP_BENCH -> stopPassiveGlipBenchmark()
        }
        return START_NOT_STICKY
    }

    private fun applyGameGain250(s: StreamSettings) {
        audioEngine?.gameGainLinear = (s.gameGainPercent.coerceIn(0, 250)) / 100.0f
    }

    fun applyDashboardSettingsLive(newSettings: StreamSettings, userToggledSharp: Boolean = false) {
        if (!isBroadcastLive) return
        val prev = activeSettings
        if (userToggledSharp) {
            userManuallyOverrodeSharp = newSettings.gpuSharpen
        }

        activeSettings = newSettings.copy(
            codec = prev.codec,
            width = prev.width,
            height = prev.height,
            fps = prev.fps,
            useVbr = prev.useVbr,
            isVertical = prev.isVertical,
            hdr10Enabled = prev.hdr10Enabled,
            audioBitrateKbps = prev.audioBitrateKbps
        )

        videoPipeline?.applyLiveVisualSettings(activeSettings)
        if (activeSettings.targetBitrateKbps != prev.targetBitrateKbps) {
            val newBps = activeSettings.targetBitrateKbps * 1000
            savedPreBrbBitrateBps = newBps
            if (videoPipeline?.brbBlackoutActive != true) {
                videoPipeline?.setBitrateOnFly(newBps)
                congestedTicks = 0
                stableTicks = 0
            }
        }

        audioEngine?.updateLiveSettings(activeSettings)
        applyGameGain250(activeSettings)
        syncWifiLowLatencyLock(activeSettings.wifiLowLatencyLock)

        updateGhostPillAppearance()
        if (isOverlayExpanded) refreshExpandedOverlayText()
        refreshForegroundNotification(force = false)
    }

    private fun recordLiveClipMarker(sourceTag: String) {
        if (!isBroadcastLive) return
        val elapsedSec = max(0L, (SystemClock.elapsedRealtime() - streamStartElapsedMs) / 1000L)
        val stamp = DiagnosticsLogger.markClipHighlight(elapsedSec, sourceTag)
        Toast.makeText(this, "★ Saved Highlight $stamp to Session Report!", Toast.LENGTH_SHORT).show()
        if (isOverlayExpanded) refreshExpandedOverlayText()
    }

    private fun toggleStreamMicMute() {
        val a = audioEngine ?: return
        a.micDsp.streamMicMuted = !a.micDsp.streamMicMuted
        if (videoPipeline?.brbBlackoutActive == true) {
            brbAutoMutedMic = false
        }
        updateGhostPillAppearance()
        if (isOverlayExpanded) refreshExpandedOverlayText()
        refreshForegroundNotification(force = false)
    }

    private fun setBroadcastSceneMode(newScene: String) {
        val v = videoPipeline ?: return
        val prevScene = v.activeSceneMode
        val normScene = when (newScene.uppercase(Locale.US)) {
            "STARTING_SOON" -> "STARTING_SOON"
            "BRB" -> "BRB"
            else -> "LIVE"
        }
        if (normScene == prevScene) return

        if (prevScene == "LIVE" && normScene != "LIVE") {
            savedPreBrbBitrateBps = v.currentBitrateBps.coerceAtLeast(1_500_000)
            v.setSceneMode(normScene)
            v.setBitrateOnFly(min(savedPreBrbBitrateBps, SCENE_SAVER_BITRATE_BPS))

            val wasAlreadyMuted = audioEngine?.micDsp?.streamMicMuted == true
            if (activeSettings.brbMuteMicToo && !wasAlreadyMuted) {
                audioEngine?.micDsp?.streamMicMuted = true
                brbAutoMutedMic = true
            } else {
                brbAutoMutedMic = false
            }
            DiagnosticsLogger.log(
                "SCENE_ON",
                "$normScene slate enabled; stepped stream down to 30 FPS / 1.5 Mbps to cool phone & Wi-Fi.",
                "savedTarget=${savedPreBrbBitrateBps / 1000}kbps micAutoMuted=$brbAutoMutedMic"
            )
        } else if (normScene != "LIVE") {
            v.setSceneMode(normScene)
            DiagnosticsLogger.log("SCENE_SWAP", "Switched broadcast slate to $normScene.")
        } else {
            v.setSceneMode("LIVE")
            val restoreBps = max(savedPreBrbBitrateBps, activeSettings.targetBitrateKbps * 1000)
            v.setBitrateOnFly(restoreBps)
            v.requestSyncFrame()

            if (brbAutoMutedMic) {
                audioEngine?.micDsp?.streamMicMuted = false
                brbAutoMutedMic = false
            }
            congestedTicks = 0
            stableTicks = 0
            DiagnosticsLogger.log(
                "SCENE_LIVE",
                "Returned to LIVE gameplay; restored ${restoreBps / 1000} kbps bitrate and preserved mic privacy state.",
                "micMuted=${audioEngine?.micDsp?.streamMicMuted == true}"
            )
        }

        updateGhostPillAppearance()
        if (isOverlayExpanded) refreshExpandedOverlayText()
        refreshForegroundNotification(force = false)
    }

    private fun toggleBrbShield() {
        val v = videoPipeline ?: return
        val next = if (v.activeSceneMode == "LIVE") "BRB" else "LIVE"
        setBroadcastSceneMode(next)
    }

    private fun startPassiveGlipBenchmark() {
        if (isBroadcastLive) return
        isGlipBenchmarkRunning = true
        DiagnosticsLogger.startNewSession(this, "Competitor App Baseline Benchmark", "Passive Thermal/RAM Monitor")
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.post(watchdogRunnable)
        Toast.makeText(this, "App Benchmark started! Play & stream in the other app, then return to stop.", Toast.LENGTH_LONG).show()
        onStateChangedListener?.invoke()
    }

    private fun stopPassiveGlipBenchmark() {
        if (!isGlipBenchmarkRunning) return
        isGlipBenchmarkRunning = false
        mainHandler.removeCallbacks(watchdogRunnable)
        DiagnosticsLogger.finishAndSaveBenchmark(this, isGlipBaseline = true)
        DiagnosticsLogger.log("BENCHMARK", "Competitor baseline benchmark saved for comparison.")
        Toast.makeText(this, "App Benchmark saved to Session Report!", Toast.LENGTH_SHORT).show()
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

    private fun attachAndStartAudioEngine(
        projection: MediaProjection,
        client: RtmpClient,
        originUs: Long
    ): Boolean {
        val aEngine = SolDualAudioEngine(this)
        aEngine.onFatalAudioError = { reason ->
            mainHandler.post { handleUnexpectedAudioExit(reason) }
        }
        audioEngine = aEngine
        val started = aEngine.startStreamingAudio(
            settings = activeSettings,
            mediaProjection = projection,
            originTimeUs = originUs,
            onStatusChanged = {
                mainHandler.post {
                    updateGhostPillAppearance()
                    if (isOverlayExpanded) refreshExpandedOverlayText()
                }
            },
            onEncodedAac = { aacBuf, info ->
                val nowMonoUs = SystemClock.elapsedRealtimeNanos() / 1000L
                lastAudioClockOffsetUs = info.presentationTimeUs - nowMonoUs
                hasAudioOffset = true
                if (isRtmpConnected) {
                    try {
                        client.sendAudio(aacBuf.duplicate(), info)
                    } catch (_: Exception) {
                    }
                }
                for (aux in simulcastNodes) {
                    aux.sendAudioPacket(aacBuf, info)
                }
                companionNode?.sendAudioPacket(aacBuf, info)
            }
        )
        if (started) {
            applyGameGain250(activeSettings)
        }
        return started
    }

    private fun handleUnexpectedAudioExit(reason: String) {
        if (!isBroadcastLive || audioRestartInProgress) return
        val proj = mediaProjection ?: return
        val client = rtmpClient ?: return
        if (audioRestartAttempts >= 4) {
            DiagnosticsLogger.log(
                "AUDIO_FATAL",
                "Audio mixer could not be recovered after $audioRestartAttempts attempts ($reason)."
            )
            Toast.makeText(this, "Warning: Stream audio stopped ($reason).", Toast.LENGTH_LONG).show()
            return
        }
        audioRestartInProgress = true
        audioRestartAttempts++
        DiagnosticsLogger.log(
            "AUDIO_RESTART",
            "Restarting audio studio automatically (attempt $audioRestartAttempts/4) after interruption.",
            reason
        )
        mainHandler.postDelayed({
            audioRestartInProgress = false
            if (!isBroadcastLive) return@postDelayed
            try { audioEngine?.stopStreamingAudio() } catch (_: Exception) {}
            val ok = attachAndStartAudioEngine(proj, client, sessionOriginNs / 1000L)
            if (ok) {
                DiagnosticsLogger.log("AUDIO_RESTART", "Audio studio recovered cleanly.")
            }
            updateGhostPillAppearance()
            if (isOverlayExpanded) refreshExpandedOverlayText()
        }, 800L)
    }

    private fun startLiveBroadcast(resultCode: Int, resultData: Intent) {
        if (isBroadcastLive) return
        if (isGlipBenchmarkRunning) stopPassiveGlipBenchmark()

        activeSettings = KeystoreVault.loadSettings(this)
        val endpoint = KeystoreVault.buildActivePrimaryEndpoint(this, activeSettings)
        if (endpoint.isNullOrBlank()) {
            Toast.makeText(this, "Paste and save your Stream Key first!", Toast.LENGTH_LONG).show()
            return
        }

        val currentGen = sessionGeneration.incrementAndGet()
        activeClientGeneration = currentGen

        primaryEndpoint = endpoint
        backupEndpoint = KeystoreVault.deriveYoutubeBackupUrl(endpoint)
        usingBackupEndpoint = false
        needsProjectionReapproval = false
        awaitingKeyframeAfterConnect = true
        reconnectCount = 0
        lastFailHandledElapsedMs = -10_000L
        currentRetryDelayMs = 2000L
        stalledZeroSendTicks = 0
        audioRestartAttempts = 0
        audioRestartInProgress = false
        brbAutoMutedMic = false
        userManuallyOverrodeSharp = activeSettings.gpuSharpen
        userManuallyOverrode30FpsSaver = false
        lowBatt15Warned = false
        lowBatt5Warned = false
        hasVideoOffset = false
        hasAudioOffset = false
        prevDroppedFrames = 0
        prevQueueFrames = 0
        congestedTicks = 0
        stableTicks = 0
        abrReducedActive = false
        lastMeasuredOutBps = 0L
        savedPreBrbBitrateBps = activeSettings.targetBitrateKbps * 1000
        lastNotifStatusSummary = ""
        simulcastNodes.clear()
        companionNode = null

        val notif = buildForegroundNotification("Starting SolStream Studio Ultra broadcast...")
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
        sessionOriginNs = originNs

        val client = RtmpClient(this)
        client.tlsHostVerification = true
        client.setReTries(maxReconnects)
        try {
            client.resizeCache(120)
        } catch (_: Exception) {
        }
        rtmpClient = client
        telemetryBridge = RtmpTelemetryBridge(client)

        val companionUrl = if (activeSettings.dualFormatCompanionEnabled && !activeSettings.isVertical) {
            KeystoreVault.buildCompanionVerticalEndpoint(this)
        } else {
            null
        }
        val wantCompanion = !companionUrl.isNullOrBlank()

        val vPipe = SolVideoPipeline(this)
        vPipe.onFatalPipelineError = { errMsg ->
            mainHandler.post { handleFatalVideoError(errMsg) }
        }
        vPipe.onCompanionPipelineStopped = { _ ->
            mainHandler.post {
                companionNode?.stopAsync()
                companionNode = null
                if (isOverlayExpanded) refreshExpandedOverlayText()
            }
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
                for (aux in simulcastNodes) {
                    aux.pushHeaders(cachedSpsBytes, cachedPpsBytes, cachedVpsBytes)
                }
            },
            onEncodedVideo = { buf, info ->
                val nowMonoUs = SystemClock.elapsedRealtimeNanos() / 1000L
                lastVideoClockOffsetUs = info.presentationTimeUs - nowMonoUs
                hasVideoOffset = true
                if (isRtmpConnected) {
                    if (awaitingKeyframeAfterConnect) {
                        val isKey = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        if (isKey) {
                            awaitingKeyframeAfterConnect = false
                            try { client.sendVideo(buf.duplicate(), info) } catch (_: Exception) {}
                        }
                    } else {
                        try { client.sendVideo(buf.duplicate(), info) } catch (_: Exception) {}
                    }
                }
                for (aux in simulcastNodes) {
                    aux.sendVideoPacket(buf, info)
                }
            },
            enableDualCompanion = wantCompanion,
            onCompanionConfig = { sps, pps, vps ->
                compCachedSpsBytes = copyBufferBytes(sps)
                compCachedPpsBytes = copyBufferBytes(pps)
                compCachedVpsBytes = copyBufferBytes(vps)
                companionNode?.pushHeaders(compCachedSpsBytes, compCachedPpsBytes, compCachedVpsBytes)
            },
            onCompanionVideo = { buf, info ->
                companionNode?.sendVideoPacket(buf, info)
            }
        )

        if (activeVideo == null) {
            Toast.makeText(this, "Hardware video encoder failed to start. Check Session Report.", Toast.LENGTH_LONG).show()
            cleanupResources()
            return
        }

        // Sync actual resolved dimensions/FPS back to activeSettings
        activeSettings = activeSettings.copy(
            width = activeVideo.outWidth,
            height = activeVideo.outHeight,
            fps = activeVideo.fps,
            useVbr = activeVideo.isVbr,
            hdr10Enabled = activeVideo.isHdr10
        )

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

        val audioOk = attachAndStartAudioEngine(projection, client, originNs / 1000L)
        if (!audioOk) {
            Toast.makeText(this, "Audio engine failed to open microphone.", Toast.LENGTH_LONG).show()
            cleanupResources()
            return
        }

        val simTargets = KeystoreVault.buildSimulcastTargets(this, activeSettings)
        for ((tName, tUrl) in simTargets) {
            val node = AuxiliaryRtmpTarget(
                name = tName,
                endpointUrl = tUrl,
                isCompanionVertical = false,
                generationId = currentGen,
                videoCodec = rtmpVideoCodec,
                width = activeVideo.outWidth,
                height = activeVideo.outHeight,
                fps = activeVideo.fps
            )
            simulcastNodes.add(node)
        }
        if (activeVideo.companionActive && !companionUrl.isNullOrBlank()) {
            val scan = CodecCapabilityProbe.peekCachedOrPlaceholder(this)
            val compCodec = if (scan.hevcHardware?.verifiedSurfaceConfigure == true) VideoCodec.H265 else rtmpVideoCodec
            companionNode = AuxiliaryRtmpTarget(
                name = "YT-Shorts-9:16",
                endpointUrl = companionUrl,
                isCompanionVertical = true,
                generationId = currentGen,
                videoCodec = compCodec,
                width = 1080,
                height = 1920,
                fps = activeVideo.fps
            )
        }

        val modeTag = if (activeVideo.isVbr) "VBR" else "CBR"
        val hdrTag = if (activeVideo.isHdr10) " • 10-Bit HDR10" else ""
        val desc = "${activeVideo.codecKey} (${activeVideo.encoderName}) ${activeVideo.outWidth}x${activeVideo.outHeight}@${activeVideo.fps}fps $modeTag$hdrTag • ${activeVideo.glEngineLabel}"
        DiagnosticsLogger.activeAudioDspDesc =
            "${activeSettings.voiceGenderProfile}/${activeSettings.voicePreset} • ${audioEngine?.micDsp?.activeDenoiserSummary ?: "RNNoise"} • ${activeSettings.audioBitrateKbps}k AAC"
        val targetNames = buildList {
            add(if (activeSettings.isVertical) "Primary (9:16)" else "Primary (16:9)")
            simulcastNodes.forEach { add(it.name) }
            if (companionNode != null) add("Companion (9:16)")
        }.joinToString(" + ")
        DiagnosticsLogger.activeSimulcastDesc = targetNames
        DiagnosticsLogger.startNewSession(this, "Live Broadcast", desc)

        isBroadcastLive = true
        streamStartElapsedMs = SystemClock.elapsedRealtime()
        connectionStateLabel = "CONNECTING"
        showFloatingOverlay()
        refreshForegroundNotification(force = true)

        val connectTarget = primaryEndpoint
        Thread({
            if (!shouldAcceptConnectionCallback(isBroadcastLive, currentGen, sessionGeneration.get())) {
                return@Thread
            }
            try {
                client.connect(connectTarget)
                if (!shouldAcceptConnectionCallback(isBroadcastLive, currentGen, sessionGeneration.get())) {
                    try { client.disconnect() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                if (shouldAcceptConnectionCallback(isBroadcastLive, currentGen, sessionGeneration.get())) {
                    DiagnosticsLogger.log("NET_ERR", "RTMPS connection exception (${e.javaClass.simpleName}).", e.message ?: "")
                }
            }
        }, "SolRtmpsConnect").start()

        for (aux in simulcastNodes) {
            aux.startAsync()
        }
        companionNode?.startAsync()

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
        val wasLive = isBroadcastLive
        sessionGeneration.incrementAndGet()
        activeClientGeneration = 0L
        isBroadcastLive = false
        isRtmpConnected = false
        audioRestartInProgress = false
        connectionStateLabel = "STOPPED"
        mainHandler.removeCallbacksAndMessages(null)

        DiagnosticsLogger.log("SESSION_END", "Stream ended ($reason).")
        DiagnosticsLogger.finishAndSaveBenchmark(this, isGlipBaseline = false)
        if (wasLive) {
            val fullReport = DiagnosticsLogger.buildShareableReport(this)
            archiveCompletedSessionReport(this, fullReport)
        }

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

        for (aux in simulcastNodes) {
            aux.stopAsync()
        }
        simulcastNodes.clear()
        companionNode?.stopAsync()
        companionNode = null

        audioEngine?.onFatalAudioError = null
        try { audioEngine?.stopStreamingAudio() } catch (_: Exception) {}
        audioEngine = null

        videoPipeline?.onFatalPipelineError = null
        videoPipeline?.onCompanionPipelineStopped = null
        try { videoPipeline?.stopPipeline() } catch (_: Exception) {}
        videoPipeline = null

        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null

        cachedSpsBytes = null
        cachedPpsBytes = null
        cachedVpsBytes = null
        compCachedSpsBytes = null
        compCachedPpsBytes = null
        compCachedVpsBytes = null
        releaseLocksAndThermalMonitor()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }

    private fun isCurrentSessionActive(): Boolean {
        return shouldAcceptConnectionCallback(
            isBroadcastActive = isBroadcastLive,
            callbackGen = activeClientGeneration,
            currentGen = sessionGeneration.get()
        )
    }

    // === PRIMARY CONNECTCHECKER CALLBACKS ===
    override fun onConnectionStarted(url: String) {
        if (!isCurrentSessionActive()) return
        connectionStateLabel = if (usingBackupEndpoint) "CONNECTING (BACKUP)" else "CONNECTING"
        val serverTag = if (usingBackupEndpoint) "Backup RTMPS server" else "Primary RTMPS server"
        DiagnosticsLogger.log("NETWORK", "Connecting to $serverTag (TLS Host Verified)...")
        mainHandler.post {
            if (!isCurrentSessionActive()) return@post
            updateGhostPillAppearance()
            refreshForegroundNotification(force = false)
        }
    }

    override fun onConnectionSuccess() {
        val client = rtmpClient
        if (!isCurrentSessionActive() || client == null) {
            isRtmpConnected = false
            if (client != null) {
                Thread({
                    try { client.disconnect() } catch (_: Exception) {}
                }, "SolLateStopDisconnect").start()
            }
            return
        }

        awaitingKeyframeAfterConnect = true
        isRtmpConnected = true
        reconnectCount = 0
        stalledZeroSendTicks = 0
        audioRestartAttempts = 0
        lastFailHandledElapsedMs = -10_000L
        connectionStateLabel = if (usingBackupEndpoint) "LIVE (BACKUP)" else "LIVE"

        client.setReTries(maxReconnects)
        sendCachedHeadersToClient(client)
        videoPipeline?.requestSyncFrame()
        val serverTag = if (usingBackupEndpoint) "Backup RTMPS" else "Primary RTMPS"
        DiagnosticsLogger.log("NETWORK", "Connected to $serverTag ingest. Stream is LIVE.")
        mainHandler.post {
            if (!isCurrentSessionActive()) return@post
            updateGhostPillAppearance()
            if (isOverlayExpanded) refreshExpandedOverlayText()
            refreshForegroundNotification(force = true)
            onStateChangedListener?.invoke()
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        if (!isCurrentSessionActive()) return
        lastMeasuredOutBps = bitrate
    }

    override fun onConnectionFailed(reason: String) {
        isRtmpConnected = false
        awaitingKeyframeAfterConnect = true
        if (!isCurrentSessionActive() || needsProjectionReapproval) return
        scheduleSequentialReconnect(reason)
    }

    private fun scheduleSequentialReconnect(reason: String) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - lastFailHandledElapsedMs < max(800L, currentRetryDelayMs - 250L)) {
            return
        }

        val client = rtmpClient ?: return
        val expectedGen = activeClientGeneration
        if (reconnectCount < maxReconnects) {
            reconnectCount++
            DiagnosticsLogger.reconnectAttempts = reconnectCount

            val bkp = backupEndpoint
            usingBackupEndpoint = !bkp.isNullOrBlank() && reconnectCount >= 2 && (reconnectCount % 2 == 0)
            val nextTargetUrl = if (usingBackupEndpoint && !bkp.isNullOrBlank()) bkp else primaryEndpoint
            val targetName = if (usingBackupEndpoint) "Backup Server" else "Primary Server"

            val delayMs = min(12_000L, 2000L * reconnectCount)
            currentRetryDelayMs = delayMs
            lastFailHandledElapsedMs = nowElapsed
            isRtmpConnected = false
            awaitingKeyframeAfterConnect = true
            connectionStateLabel = "RETRY $reconnectCount/$maxReconnects"

            DiagnosticsLogger.log(
                "NET_RETRY",
                "Upload interrupted. Sequential reConnect ($reconnectCount/$maxReconnects) via $targetName in ${delayMs / 1000}s.",
                reason
            )

            // Run reConnect on a dedicated background thread so RtmpSender's internal thread never deadlocks!
            Thread({
                if (!shouldAcceptConnectionCallback(isBroadcastLive, expectedGen, sessionGeneration.get())) {
                    return@Thread
                }
                sendCachedHeadersToClient(client)
                try {
                    client.reConnect(delayMs, nextTargetUrl)
                } catch (_: Exception) {
                }
            }, "SolRtmpsReconn_$reconnectCount").start()

            mainHandler.post {
                if (!isCurrentSessionActive()) return@post
                updateGhostPillAppearance()
                if (isOverlayExpanded) refreshExpandedOverlayText()
                refreshForegroundNotification(force = false)
            }
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
        if (!isCurrentSessionActive()) return
        DiagnosticsLogger.log("AUTH_ERR", "Server rejected the Stream Key (Auth Error). Verify your key in Pre-Stream Studio.")
        mainHandler.post {
            Toast.makeText(this, "Stream Key rejected by server!", Toast.LENGTH_LONG).show()
            stopLiveBroadcast("Authentication error")
        }
    }

    override fun onAuthSuccess() {}

    // === SOLSTREAM HEALTH, ZOMBIE-SOCKET WATCHDOG, THERMAL 30FPS RELIEF & ABR 2.0 ===
    private fun runHealthAndRecoveryTick() {
        val (_, thermal) = DiagnosticsLogger.sampleDeviceHealth(this)
        if (!isBroadcastLive || !isCurrentSessionActive()) return

        renewWakeLockIfNeeded()

        if (isRtmpConnected && awaitingKeyframeAfterConnect) {
            videoPipeline?.requestSyncFrame()
        }

        // Low-Battery Unplugged Guard (15% & 5% alerts)
        val battPct = DiagnosticsLogger.lastBatteryPercent
        val plugged = DiagnosticsLogger.isBatteryChargingOrPlugged(this)
        if (!plugged && battPct in 1..15) {
            if (battPct <= 5 && !lowBatt5Warned) {
                lowBatt5Warned = true
                DiagnosticsLogger.log("BATT_CRIT", "Phone battery critical ($battPct% unplugged)! Plug in charger immediately.")
                Toast.makeText(this, "CRITICAL BATTERY ($battPct%)! Plug in charger so stream doesn't die!", Toast.LENGTH_LONG).show()
            } else if (battPct <= 15 && !lowBatt15Warned) {
                lowBatt15Warned = true
                DiagnosticsLogger.log("BATT_LOW", "Phone battery low ($battPct% unplugged).")
                Toast.makeText(this, "Battery at $battPct% (unplugged).", Toast.LENGTH_SHORT).show()
            }
        } else if (plugged) {
            lowBatt15Warned = false
            lowBatt5Warned = false
        }

        val aEng = audioEngine
        if (aEng != null && !aEng.isRunning && !audioRestartInProgress) {
            handleUnexpectedAudioExit("Audio watchdog detected stopped mixer")
        }

        if (hasVideoOffset && hasAudioOffset) {
            DiagnosticsLogger.avDriftMs = (lastAudioClockOffsetUs - lastVideoClockOffsetUs) / 1000L
        }

        // 1. Poll sender queue frames, bytes, capacity, RTT & dropped frames (preserving -1 if unavailable)
        val tel = telemetryBridge?.poll() ?: SenderTelemetry(-1L, -1, -1, -1, -1L, false)
        DiagnosticsLogger.lastQueueBytes = tel.queueBytes
        DiagnosticsLogger.lastQueueFrames = tel.queueFrames
        DiagnosticsLogger.lastQueueCapacity = tel.queueCapacity
        DiagnosticsLogger.lastRttMs = tel.rttMs

        val droppedDelta = if (tel.droppedFrames >= 0) {
            val delta = max(0, tel.droppedFrames - prevDroppedFrames)
            prevDroppedFrames = tel.droppedFrames
            DiagnosticsLogger.droppedVideoFrames = tel.droppedFrames
            delta
        } else {
            0
        }

        // 2. 6-Second Zombie-Socket Watchdog (Fixes silent sent=0kbps router blackhole!)
        val qFrames = tel.queueFrames
        val qBytes = tel.queueBytes
        val outBps = lastMeasuredOutBps
        val queueSeverelyBackedUp = (qFrames >= 75) || (qBytes >= 2_500_000L) || (droppedDelta >= 15)
        if (isRtmpConnected && queueSeverelyBackedUp && outBps == 0L) {
            stalledZeroSendTicks++
            if (stalledZeroSendTicks >= 3) {
                stalledZeroSendTicks = 0
                DiagnosticsLogger.log(
                    "NET_STALL",
                    "Detected silent Wi-Fi/router stall (0 kbps sent with full queue); forcing clean reconnect...",
                    "qFrames=$qFrames qBytes=${if (qBytes >= 0) "${qBytes / 1024}KB" else "N/A"} dropsDelta=$droppedDelta"
                )
                scheduleSequentialReconnect("Silent socket stall (0 kbps sent)")
                return
            }
        } else {
            stalledZeroSendTicks = 0
        }

        // 3. Smart Severe-Thermal Relief
        val vPipe = videoPipeline
        if (vPipe != null) {
            if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
                if (companionNode != null && !vPipe.companionPausedByThermal) {
                    vPipe.companionPausedByThermal = true
                    DiagnosticsLogger.log("THERMAL", "Paused secondary 9:16 companion stream first to protect main broadcast & CODM FPS.")
                }
                if (!userManuallyOverrode30FpsSaver && !vPipe.thermalThrottle30Fps && activeSettings.fps > 30) {
                    vPipe.thermalThrottle30Fps = true
                    DiagnosticsLogger.log(
                        "THERMAL",
                        "Phone reached Severe warmth; stepped stream pacer to 30 FPS temporarily to cool GPU/SoC so CODM BR doesn't stutter."
                    )
                }
                if ((thermal >= PowerManager.THERMAL_STATUS_CRITICAL || !userManuallyOverrodeSharp) && vPipe.liveGpuSharpen) {
                    vPipe.liveGpuSharpen = false
                    userManuallyOverrodeSharp = false
                    activeSettings = activeSettings.copy(gpuSharpen = false)
                }
            } else if (thermal <= PowerManager.THERMAL_STATUS_MODERATE) {
                if (vPipe.companionPausedByThermal) {
                    vPipe.companionPausedByThermal = false
                    DiagnosticsLogger.log("THERMAL", "SoC cooled to Moderate; resumed secondary 9:16 companion stream.")
                }
                if (!userManuallyOverrode30FpsSaver && vPipe.thermalThrottle30Fps) {
                    vPipe.thermalThrottle30Fps = false
                    DiagnosticsLogger.log("THERMAL", "SoC cooled to Moderate; restored full 60 FPS broadcast cadence.")
                }
            }
        }

        // 4. Dynamic-Floor Queue-Aware ABR 2.0 (Never mistakes -1 for calm!)
        if (vPipe != null && vPipe.brbBlackoutActive) {
            congestedTicks = 0
            stableTicks = 0
            if (qFrames >= 0) prevQueueFrames = qFrames
            return
        }

        if (activeSettings.adaptiveBitrate && isRtmpConnected && vPipe != null) {
            val currentTargetBps = vPipe.currentBitrateBps
            val maxConfiguredBps = activeSettings.targetBitrateKbps * 1000

            val longEdge = max(activeSettings.width, activeSettings.height)
            val minFloorBps = when {
                longEdge <= 640 -> min(maxConfiguredBps, 400_000)
                longEdge <= 864 -> min(maxConfiguredBps, 650_000)
                longEdge <= 1280 -> min(maxConfiguredBps, 1_200_000)
                else -> min(maxConfiguredBps, max(2_000_000, (maxConfiguredBps * 0.30).toInt()))
            }

            val producedBps = vPipe.actualEncodedBitrateBps.toLong() + (activeSettings.audioBitrateKbps * 1000L)
            val queueGrowingFast = qFrames >= 16 && prevQueueFrames >= 0 && (qFrames - prevQueueFrames) >= 6
            if (qFrames >= 0) prevQueueFrames = qFrames

            val senderFallingBehindEncoder = producedBps > 1_200_000L &&
                qFrames >= 12 &&
                outBps in 1L until (producedBps * 0.70).toLong()

            val realCongestion = tel.hasCongestion ||
                droppedDelta > 0 ||
                (qFrames >= 30) ||
                (qBytes > 650_000L) ||
                queueGrowingFast ||
                senderFallingBehindEncoder

            if (realCongestion) {
                congestedTicks++
                stableTicks = 0
                if (congestedTicks >= 2 || droppedDelta > 0) {
                    congestedTicks = 0
                    val reduced = max(minFloorBps, (currentTargetBps * 0.80).toInt())
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
                            "Real upload backlog ($qDesc). Stepped video target to ${formatMbpsLabel(reduced / 1000)} to protect CODM ping.",
                            "prod=${producedBps / 1000}kbps sent=${outBps / 1000}kbps"
                        )
                    }
                }
            } else {
                congestedTicks = 0
                val queueCalm = !tel.hasCongestion && isQueueCalmForAbrRecovery(qFrames, qBytes, droppedDelta)
                if (currentTargetBps < maxConfiguredBps && queueCalm) {
                    stableTicks++
                    if (stableTicks >= 4) {
                        stableTicks = 0
                        val stepUpDelta = max(300_000, (maxConfiguredBps * 0.08).toInt())
                        val steppedUp = min(maxConfiguredBps, currentTargetBps + stepUpDelta)
                        vPipe.setBitrateOnFly(steppedUp)
                        if (steppedUp >= maxConfiguredBps && abrReducedActive) {
                            abrReducedActive = false
                            DiagnosticsLogger.log(
                                "ABR_UP",
                                "Network clear; restored full ${formatMbpsLabel(steppedUp / 1000)} video bitrate.",
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

    private fun syncWifiLowLatencyLock(enable: Boolean) {
        try {
            if (enable) {
                if (wifiLock?.isHeld == true) return
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
            } else {
                if (wifiLock?.isHeld == true) wifiLock?.release()
                wifiLock = null
            }
        } catch (_: Exception) {
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
            syncWifiLowLatencyLock(s.wifiLowLatencyLock)
            registerValidatedNetworkWatchdog()
        } catch (_: Exception) {
        }
    }

    private fun registerValidatedNetworkWatchdog() {
        unregisterValidatedNetworkWatchdog()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (validated) {
                        val handle = network.networkHandle
                        if (lastValidatedNetworkHandle != -1L && lastValidatedNetworkHandle != handle && isBroadcastLive) {
                            val transport = when {
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G/Mobile Data"
                                else -> "Active Network"
                            }
                            DiagnosticsLogger.log("NET_HANDOVER", "Validated network switched to $transport.")
                        }
                        lastValidatedNetworkHandle = handle
                    }
                }

                override fun onLost(network: Network) {
                    if (isBroadcastLive && network.networkHandle == lastValidatedNetworkHandle) {
                        DiagnosticsLogger.log("NET_LOST", "Primary network link dropped; standing by for fast reconnect.")
                    }
                }
            }
            netCallback = cb
            cm.registerDefaultNetworkCallback(cb)
        } catch (_: Exception) {
        }
    }

    private fun unregisterValidatedNetworkWatchdog() {
        val cb = netCallback ?: return
        netCallback = null
        lastValidatedNetworkHandle = -1L
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
        }
    }

    private fun releaseLocksAndThermalMonitor() {
        unregisterValidatedNetworkWatchdog()
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

    // === FLOATING ACCESSIBILITY OVERLAY (SLEEPING GHOST PILL + COMPACT 3-TAB ULTRA STUDIO) ===
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
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = roundedBg("#CC080E18", "#00E5FF", 20)
            alpha = 0.38f
        }
        ghostPillView = pill

        val panelScroll = buildExpandedThreeTabPanel()
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
            x = dp(22)
            y = dp(86)
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

    private fun buildExpandedThreeTabPanel(): ScrollView {
        val scroll = ScrollView(this).apply {
            background = roundedBg("#F2070C15", "#00E5FF", 14)
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(278), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        tvHeaderStats = TextView(this).apply {
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.8f)
            typeface = Typeface.MONOSPACE
        }
        tvBadges = TextView(this).apply {
            setTextColor(Color.parseColor("#CFD8DC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding(0, dp(2), 0, dp(5))
        }
        box.addView(tvHeaderStats)
        box.addView(tvBadges)

        btnQuickMic = compactRowButton("Mic ON", "#00695C") { toggleStreamMicMute() }
        btnQuickGame = compactRowButton("Game ON", "#1565C0") {
            val a = audioEngine ?: return@compactRowButton
            a.gameStreamMuted = !a.gameStreamMuted
            refreshExpandedOverlayText()
        }
        btnQuickScene = compactRowButton("Scene:LIVE", "#37474F") {
            val cur = videoPipeline?.activeSceneMode ?: "LIVE"
            val next = when (cur) {
                "LIVE" -> "BRB"
                "BRB" -> "STARTING_SOON"
                else -> "LIVE"
            }
            setBroadcastSceneMode(next)
        }
        btnQuickMarkClip = compactRowButton("★ Clip", "#6A1B9A") {
            recordLiveClipMarker("In-Game Clutch Marker")
        }
        val btnQuickCollapse = compactRowButton("✕", "#263238") {
            setOverlayExpanded(false)
        }
        box.addView(
            horizontalRow(
                btnQuickMic!! to 1.15f,
                btnQuickGame!! to 1.15f,
                btnQuickScene!! to 1.25f,
                btnQuickMarkClip!! to 0.95f,
                btnQuickCollapse to 0.55f
            )
        )

        btnTabAudio = compactRowButton("🎙️ Audio", "#00838F") { switchOverlayTab(0) }
        btnTabVideo = compactRowButton("🎬 Video", "#1C2836") { switchOverlayTab(1) }
        btnTabNet = compactRowButton("📡 Bitrate/Net", "#1C2836") { switchOverlayTab(2) }
        box.addView(
            horizontalRow(
                btnTabAudio!! to 1f,
                btnTabVideo!! to 1f,
                btnTabNet!! to 1.15f
            )
        )

        // === TAB 0: 🎙️ AUDIO ===
        val audioTab = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabBoxAudio = audioTab

        btnVoiceGender = fullWidthPillButton("Voice Profile: NEUTRAL", "#1E3A5F") {
            val order = MicDspProcessor.GENDER_PROFILE_ORDER
            val next = order[(order.indexOf(activeSettings.voiceGenderProfile) + 1) % order.size]
            saveAndSyncLiveAudio(activeSettings.copy(voiceGenderProfile = next))
        }
        btnVoicePreset = fullWidthPillButton("Voice Type: SOL LUXE", "#283593") {
            val order = MicDspProcessor.VOICE_PRESET_ORDER
            val next = order[(order.indexOf(activeSettings.voicePreset) + 1) % order.size]
            saveAndSyncLiveAudio(MicDspProcessor.applyPresetToSettings(activeSettings, next))
        }
        btnNoiseSuppress = fullWidthPillButton("Denoiser: C RNNoise (Natural)", "#1C2836") {
            val (nextMode, nextLvl) = when {
                activeSettings.noiseSuppressMode == "RNNOISE" && activeSettings.rnnoiseLevel == 1 -> "RNNOISE" to 2
                activeSettings.noiseSuppressMode == "RNNOISE" && activeSettings.rnnoiseLevel == 2 -> "RNNOISE" to 3
                activeSettings.noiseSuppressMode == "RNNOISE" && activeSettings.rnnoiseLevel >= 3 -> "LIGHT" to 2
                activeSettings.noiseSuppressMode == "LIGHT" -> "HW_NS" to 1
                activeSettings.noiseSuppressMode == "HW_NS" -> "OFF" to 0
                else -> "RNNOISE" to 1
            }
            saveAndSyncLiveAudio(activeSettings.copy(noiseSuppressMode = nextMode, rnnoiseLevel = nextLvl))
        }
        btnHpfAndGate = fullWidthPillButton("HPF: 85Hz | Gate/Comp: ON", "#1C2836") {
            val nextHpf = when (activeSettings.hpfCutoffHz) {
                0 -> 75; 75 -> 85; 85 -> 100; 100 -> 120; else -> 0
            }
            saveAndSyncLiveAudio(activeSettings.copy(hpfCutoffHz = nextHpf))
        }
        btnVoiceStack = fullWidthPillButton("Voice Stack: EQ+DeEss+Velvet+AGC", "#1C2836") {
            val nextState = when {
                !activeSettings.voiceEqEnabled -> activeSettings.copy(voiceEqEnabled = true, deEsserEnabled = true)
                !activeSettings.velvetWarmthEnabled -> activeSettings.copy(velvetWarmthEnabled = true, whisperLiftEnabled = true)
                !activeSettings.slowAgcEnabled -> activeSettings.copy(slowAgcEnabled = true)
                else -> activeSettings.copy(
                    voiceEqEnabled = false,
                    deEsserEnabled = false,
                    velvetWarmthEnabled = false,
                    whisperLiftEnabled = false,
                    slowAgcEnabled = false
                )
            }
            saveAndSyncLiveAudio(nextState)
        }

        btnMicGain = compactRowButton("Mic: 125%", "#004D40") {
            val next = MIC_GAIN_STEPS[(MIC_GAIN_STEPS.indexOf(activeSettings.micGainPercent) + 1) % MIC_GAIN_STEPS.size]
            saveAndSyncLiveAudio(activeSettings.copy(micGainPercent = next))
        }
        btnGameGain = compactRowButton("Game: 100%", "#0D47A1") {
            val next = GAME_GAIN_STEPS[(GAME_GAIN_STEPS.indexOf(activeSettings.gameGainPercent) + 1) % GAME_GAIN_STEPS.size]
            saveAndSyncLiveAudio(activeSettings.copy(gameGainPercent = next))
        }

        btnGameSoundstageA = fullWidthPillButton("Game 3D Wide: OFF | Sub-Punch: OFF", "#1C2836") {
            val (w, sub) = when {
                !activeSettings.gameCinemaWideEnabled && !activeSettings.gameSubPunchEnabled -> true to false
                activeSettings.gameCinemaWideEnabled && !activeSettings.gameSubPunchEnabled -> true to true
                activeSettings.gameCinemaWideEnabled && activeSettings.gameSubPunchEnabled -> false to true
                else -> false to false
            }
            saveAndSyncLiveAudio(activeSettings.copy(gameCinemaWideEnabled = w, gameSubPunchEnabled = sub))
        }
        btnGameSoundstageB = fullWidthPillButton("Footstep Focus: OFF | Blast Tamer: OFF", "#1C2836") {
            val (foot, blast) = when {
                !activeSettings.gameFootstepFocusEnabled && !activeSettings.gameBlastTamerEnabled -> true to false
                activeSettings.gameFootstepFocusEnabled && !activeSettings.gameBlastTamerEnabled -> true to true
                activeSettings.gameFootstepFocusEnabled && activeSettings.gameBlastTamerEnabled -> false to true
                else -> false to false
            }
            saveAndSyncLiveAudio(activeSettings.copy(gameFootstepFocusEnabled = foot, gameBlastTamerEnabled = blast))
        }
        btnDucking = compactRowButton("Duck: OFF", "#1C2836") {
            val next = when (activeSettings.vadDuckingPercent) {
                0 -> 20; 20 -> 35; 35 -> 50; else -> 0
            }
            saveAndSyncLiveAudio(activeSettings.copy(vadDuckingPercent = next))
        }
        btnLiveMicInput = compactRowButton("Mic HW", "#1C2836") {
            val opts = CodecCapabilityProbe.listAvailableMicInputs(this)
            if (opts.isNotEmpty()) {
                val curIdx = opts.indexOfFirst { it.deviceId == activeSettings.micDeviceId }
                val nextOpt = opts[(curIdx + 1) % opts.size]
                saveAndSyncLiveAudio(activeSettings.copy(micDeviceId = nextOpt.deviceId))
            }
        }

        audioTab.addView(btnVoiceGender)
        audioTab.addView(btnVoicePreset)
        audioTab.addView(btnNoiseSuppress)
        audioTab.addView(btnHpfAndGate)
        audioTab.addView(btnVoiceStack)
        audioTab.addView(horizontalRow(btnMicGain!! to 1f, btnGameGain!! to 1f))
        audioTab.addView(btnGameSoundstageA)
        audioTab.addView(btnGameSoundstageB)
        audioTab.addView(horizontalRow(btnDucking!! to 1f, btnLiveMicInput!! to 1.2f))

        // === TAB 1: 🎬 VIDEO & SCENES ===
        val videoTab = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        tabBoxVideo = videoTab

        btnSceneCycle = fullWidthPillButton("Active Scene: LIVE GAMEPLAY", "#00695C") {
            val cur = videoPipeline?.activeSceneMode ?: "LIVE"
            val next = when (cur) {
                "LIVE" -> "STARTING_SOON"
                "STARTING_SOON" -> "BRB"
                else -> "LIVE"
            }
            setBroadcastSceneMode(next)
        }
        btnUpscalerAndFraming = fullWidthPillButton("Scale: BICUBIC | Framing: FIT", "#283593") {
            val (nextUp, nextFr) = when {
                activeSettings.upscalerMode == "BILINEAR" -> "BICUBIC" to activeSettings.framingMode
                activeSettings.upscalerMode == "BICUBIC" -> "LANCZOS" to activeSettings.framingMode
                else -> {
                    val nf = when (activeSettings.framingMode) {
                        "FIT" -> "SMART"
                        "SMART" -> "FILL"
                        else -> "FIT"
                    }
                    "BILINEAR" to nf
                }
            }
            saveAndSyncLiveVideo(activeSettings.copy(upscalerMode = nextUp, framingMode = nextFr))
        }
        btnFilmicAndVivid = fullWidthPillButton("Filmic Contrast: OFF | AMOLED Vivid: OFF", "#1C2836") {
            val (f, v) = when {
                !activeSettings.cinematicFilmic && !activeSettings.amoledVivid -> true to false
                activeSettings.cinematicFilmic && !activeSettings.amoledVivid -> true to true
                activeSettings.cinematicFilmic && activeSettings.amoledVivid -> false to true
                else -> false to false
            }
            saveAndSyncLiveVideo(activeSettings.copy(cinematicFilmic = f, amoledVivid = v))
        }
        btnShadowAndTemp = fullWidthPillButton("Shadow Boost: OFF | Temp: NEUTRAL", "#1C2836") {
            val (sh, tmp) = when {
                !activeSettings.shadowBoost && activeSettings.colorTempFilter == "NEUTRAL" -> true to "NEUTRAL"
                activeSettings.shadowBoost && activeSettings.colorTempFilter == "NEUTRAL" -> true to "WARM"
                activeSettings.shadowBoost && activeSettings.colorTempFilter == "WARM" -> true to "COOL"
                else -> false to "NEUTRAL"
            }
            saveAndSyncLiveVideo(activeSettings.copy(shadowBoost = sh, colorTempFilter = tmp))
        }
        btnFlipAndSharp = fullWidthPillButton("Flip: 0° | Sharp: OFF (Coolest)", "#1C2836") {
            val (newFlip, newSharp) = when {
                activeSettings.landscapeFlip == 0 && !activeSettings.gpuSharpen -> 0 to true
                activeSettings.landscapeFlip == 0 && activeSettings.gpuSharpen -> 180 to false
                activeSettings.landscapeFlip == 180 && !activeSettings.gpuSharpen -> 180 to true
                else -> 0 to false
            }
            userManuallyOverrodeSharp = newSharp
            saveAndSyncLiveVideo(activeSettings.copy(landscapeFlip = newFlip, gpuSharpen = newSharp))
            if (newSharp) {
                Toast.makeText(this, "Sharp ON — turn OFF anytime to reduce phone heat.", Toast.LENGTH_SHORT).show()
            }
        }
        btnOverlaysAndShield = fullWidthPillButton("Overlay: OFF | Banner: OFF | Shield: OFF", "#1C2836") {
            val (ov, bn, sh) = when {
                !activeSettings.overlayImageEnabled && !activeSettings.liveBannerEnabled && !activeSettings.minimapShieldEnabled ->
                    Triple(true, true, false)
                activeSettings.overlayImageEnabled && activeSettings.liveBannerEnabled && !activeSettings.minimapShieldEnabled ->
                    Triple(true, true, true)
                activeSettings.minimapShieldEnabled && activeSettings.overlayImageEnabled ->
                    Triple(false, false, true)
                else ->
                    Triple(false, false, false)
            }
            saveAndSyncLiveVideo(
                activeSettings.copy(
                    overlayImageEnabled = ov,
                    liveBannerEnabled = bn,
                    minimapShieldEnabled = sh
                )
            )
        }

        videoTab.addView(btnSceneCycle)
        videoTab.addView(btnUpscalerAndFraming)
        videoTab.addView(btnFilmicAndVivid)
        videoTab.addView(btnShadowAndTemp)
        videoTab.addView(btnFlipAndSharp)
        videoTab.addView(btnOverlaysAndShield)

        // === TAB 2: 📡 BITRATE & NET ===
        val netTab = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        tabBoxNet = netTab

        tvBitrateCurrent = TextView(this).apply {
            setTextColor(Color.parseColor("#00E676"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
        }
        btnBitrateDown = compactRowButton("− Lower Mbps", "#1B5E20") { stepTargetBitrate(deltaSteps = -1) }
        btnBitrateUp = compactRowButton("+ Raise Mbps", "#1B5E20") { stepTargetBitrate(deltaSteps = +1) }
        btnAbrToggle = fullWidthPillButton("Ping Guard ABR 2.0: ON", "#1C2836") {
            activeSettings = activeSettings.copy(adaptiveBitrate = !activeSettings.adaptiveBitrate)
            KeystoreVault.saveSettings(this, activeSettings)
            refreshExpandedOverlayText()
        }
        btnThermalCooldown = fullWidthPillButton("30 FPS Thermal Cooldown Saver: AUTO", "#263238") {
            val v = videoPipeline ?: return@fullWidthPillButton
            userManuallyOverrode30FpsSaver = true
            v.thermalThrottle30Fps = !v.thermalThrottle30Fps
            val stateMsg = if (v.thermalThrottle30Fps) {
                "30 FPS Cooldown Saver ON (50% less GPU heat)"
            } else {
                "Full ${activeSettings.fps} FPS restored"
            }
            Toast.makeText(this, stateMsg, Toast.LENGTH_SHORT).show()
            refreshExpandedOverlayText()
        }
        tvSimulcastStatus = TextView(this).apply {
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        netTab.addView(tvBitrateCurrent)
        netTab.addView(horizontalRow(btnBitrateDown!! to 1f, btnBitrateUp!! to 1f))
        netTab.addView(btnAbrToggle)
        netTab.addView(btnThermalCooldown)
        netTab.addView(tvSimulcastStatus)

        box.addView(audioTab)
        box.addView(videoTab)
        box.addView(netTab)

        btnStopStream = fullWidthPillButton("Stop Stream (2-Tap Confirm)", "#B71C1C") {
            val now = SystemClock.elapsedRealtime()
            if (now - stopConfirmTapTimeMs < 3000L) {
                stopLiveBroadcast("Stopped from floating menu")
            } else {
                stopConfirmTapTimeMs = now
                btnStopStream?.text = "TAP AGAIN WITHIN 3s TO END LIVE!"
            }
        }
        box.addView(btnStopStream)

        scroll.addView(box)
        return scroll
    }

    private fun switchOverlayTab(tabIndex: Int) {
        activeOverlayTab = tabIndex.coerceIn(0, 2)
        tabBoxAudio?.visibility = if (activeOverlayTab == 0) View.VISIBLE else View.GONE
        tabBoxVideo?.visibility = if (activeOverlayTab == 1) View.VISIBLE else View.GONE
        tabBoxNet?.visibility = if (activeOverlayTab == 2) View.VISIBLE else View.GONE

        btnTabAudio?.background = roundedBg(if (activeOverlayTab == 0) "#00838F" else "#15202E", "#00E5FF", 7)
        btnTabVideo?.background = roundedBg(if (activeOverlayTab == 1) "#00838F" else "#15202E", "#00E5FF", 7)
        btnTabNet?.background = roundedBg(if (activeOverlayTab == 2) "#00838F" else "#15202E", "#00E5FF", 7)
        refreshExpandedOverlayText()
    }

    private fun saveAndSyncLiveAudio(updated: StreamSettings) {
        activeSettings = updated
        KeystoreVault.saveSettings(this, activeSettings)
        audioEngine?.updateLiveSettings(activeSettings)
        applyGameGain250(activeSettings)
        DiagnosticsLogger.activeAudioDspDesc =
            "${activeSettings.voiceGenderProfile}/${activeSettings.voicePreset} • ${audioEngine?.micDsp?.activeDenoiserSummary ?: "RNNoise"} • ${activeSettings.audioBitrateKbps}k AAC"
        refreshExpandedOverlayText()
        onStateChangedListener?.invoke()
    }

    private fun saveAndSyncLiveVideo(updated: StreamSettings) {
        activeSettings = updated
        KeystoreVault.saveSettings(this, activeSettings)
        videoPipeline?.applyLiveVisualSettings(activeSettings)
        refreshExpandedOverlayText()
        onStateChangedListener?.invoke()
    }

    private fun stepTargetBitrate(deltaSteps: Int) {
        val cur = activeSettings.targetBitrateKbps
        val nearestIdx = MASTER_BITRATE_LADDER_KBPS.indices.minByOrNull {
            abs(MASTER_BITRATE_LADDER_KBPS[it] - cur)
        } ?: 13
        val nextIdx = (nearestIdx + deltaSteps).coerceIn(0, MASTER_BITRATE_LADDER_KBPS.lastIndex)
        val nextKbps = MASTER_BITRATE_LADDER_KBPS[nextIdx]
        activeSettings = activeSettings.copy(targetBitrateKbps = nextKbps)
        savedPreBrbBitrateBps = nextKbps * 1000
        KeystoreVault.saveSettings(this, activeSettings)
        if (videoPipeline?.brbBlackoutActive != true) {
            videoPipeline?.setBitrateOnFly(nextKbps * 1000)
        }
        refreshExpandedOverlayText()
        onStateChangedListener?.invoke()
    }

    private fun setOverlayExpanded(expand: Boolean) {
        isOverlayExpanded = expand
        expandedPanel?.visibility = if (expand) View.VISIBLE else View.GONE
        ghostPillView?.alpha = if (expand) 0.96f else 0.38f
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
        val scene = videoPipeline?.activeSceneMode ?: "LIVE"
        val battPct = DiagnosticsLogger.lastBatteryPercent
        val unpluggedLowBatt = !DiagnosticsLogger.isBatteryChargingOrPlugged(this) && battPct in 1..15

        val label = when {
            needsProjectionReapproval -> "RE-APPROVE CAPTURE"
            scene == "STARTING_SOON" -> "STARTING SOON"
            scene == "BRB" -> "BRB SHIELD"
            unpluggedLowBatt -> "LOW BATT ${battPct}% • ${if (muted) "MUTED" else "MIC ON"}"
            muted -> "LIVE • MUTED"
            else -> "$connectionStateLabel • MIC ON"
        }
        val stroke = when {
            needsProjectionReapproval -> "#FF9100"
            scene != "LIVE" -> "#FFC400"
            unpluggedLowBatt -> "#FF6D00"
            muted -> "#FF1744"
            else -> "#00E676"
        }
        pill.text = label
        pill.background = roundedBg("#CC080E18", stroke, 20)
    }

    private fun formatMbpsLabel(kbps: Int): String {
        val mbps = kbps / 1000.0
        return if (mbps < 10.0 && (kbps % 1000) != 0) {
            String.format(Locale.US, "%.1f Mbps", mbps)
        } else {
            String.format(Locale.US, "%.0f Mbps", mbps)
        }
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
        val qTag = if (qFrames >= 0) "Q:${qFrames}f" else "Q:0f"
        val tempC = DiagnosticsLogger.readBatteryTempC(this)
        val tempTag = if (tempC > 0f) String.format(Locale.US, "%.1f°C", tempC) else ""

        tvHeaderStats?.text = String.format(
            Locale.US,
            "%02d:%02d:%02d | %.1fM | %d/%dFPS | %s | %s",
            hh, mm, ss, outMbps, freshFps, encFps, qTag, tempTag
        )

        val micState = audioEngine?.micStatusText ?: "MIC IDLE"
        val micDb = audioEngine?.micDsp?.lastPeakDb?.toInt() ?: -90
        val gameDb = audioEngine?.lastGamePeakDb?.toInt() ?: -90
        val gameState = when {
            audioEngine?.gameAudioActive != true -> "MIC-ONLY(Retry)"
            audioEngine?.gameStreamMuted == true -> "GAME MUTED"
            audioEngine?.gameAudioReceivingSignal == true -> "GAME ${gameDb}dB"
            else -> "GAME QUIET"
        }
        tvBadges?.text = "$connectionStateLabel • $micState (${micDb}dB) • $gameState"

        val micMuted = audioEngine?.micDsp?.streamMicMuted == true
        btnQuickMic?.text = if (micMuted) "Mic:MUTE" else "Mic:ON"
        btnQuickMic?.background = roundedBg(if (micMuted) "#B71C1C" else "#00695C", "#37474F", 7)

        val gMuted = audioEngine?.gameStreamMuted == true
        btnQuickGame?.text = if (gMuted) "Game:MUTE" else "Game:ON"
        btnQuickGame?.background = roundedBg(if (gMuted) "#B71C1C" else "#1565C0", "#37474F", 7)

        val sceneNow = videoPipeline?.activeSceneMode ?: "LIVE"
        btnQuickScene?.text = when (sceneNow) {
            "STARTING_SOON" -> "Scene:START"
            "BRB" -> "Scene:BRB"
            else -> "Scene:LIVE"
        }
        btnQuickScene?.background = roundedBg(if (sceneNow != "LIVE") "#E65100" else "#37474F", "#455A64", 7)

        val clips = DiagnosticsLogger.getClipHighlightCount()
        btnQuickMarkClip?.text = if (clips > 0) "★ Clip($clips)" else "★ Clip"

        btnVoiceGender?.text = "Voice Profile (Pick 1): ${activeSettings.voiceGenderProfile}"
        btnVoicePreset?.text = "Voice Type: ${MicDspProcessor.presetDisplayName(activeSettings.voicePreset)}"
        btnNoiseSuppress?.text = "Denoiser: ${audioEngine?.micDsp?.activeDenoiserSummary ?: activeSettings.noiseSuppressMode}"
        val hpfLbl = if (activeSettings.hpfCutoffHz == 0) "OFF" else "${activeSettings.hpfCutoffHz}Hz"
        btnHpfAndGate?.text = "HPF: $hpfLbl | Gate:${onOff(activeSettings.noiseGateEnabled)} | Comp:${onOff(activeSettings.compressorEnabled)}"
        btnVoiceStack?.text =
            "EQ:${onOff(activeSettings.voiceEqEnabled)} | DeEss:${onOff(activeSettings.deEsserEnabled)} | Velvet:${onOff(activeSettings.velvetWarmthEnabled)} | AGC:${onOff(activeSettings.slowAgcEnabled)}"
        btnMicGain?.text = "Mic Boost: ${activeSettings.micGainPercent}%"
        btnGameGain?.text = "Game Mix: ${activeSettings.gameGainPercent}%"
        btnGameSoundstageA?.text =
            "Cinema 3D Wide: ${onOff(activeSettings.gameCinemaWideEnabled)} | Sub-Punch: ${onOff(activeSettings.gameSubPunchEnabled)}"
        btnGameSoundstageB?.text =
            "Footsteps: ${onOff(activeSettings.gameFootstepFocusEnabled)} | Blast Tamer: ${onOff(activeSettings.gameBlastTamerEnabled)}"
        btnDucking?.text = if (activeSettings.vadDuckingPercent == 0) "Duck: OFF" else "Duck: -${activeSettings.vadDuckingPercent}%"
        btnLiveMicInput?.text = "Mic: ${(audioEngine?.routedMicLabel ?: "Auto").take(14)}"

        btnSceneCycle?.text = when (sceneNow) {
            "STARTING_SOON" -> "Active Scene: STARTING SOON (1.5M Saver)"
            "BRB" -> "Active Scene: BRB PRIVACY (1.5M Saver)"
            else -> "Active Scene: LIVE GAMEPLAY"
        }
        btnSceneCycle?.background = roundedBg(if (sceneNow != "LIVE") "#E65100" else "#00695C", "#37474F", 7)
        btnUpscalerAndFraming?.text = "Scale: ${activeSettings.upscalerMode} | Framing: ${activeSettings.framingMode}"
        btnFilmicAndVivid?.text =
            "Cinematic Filmic: ${onOff(activeSettings.cinematicFilmic)} | AMOLED Vivid: ${onOff(activeSettings.amoledVivid)}"
        btnShadowAndTemp?.text =
            "Shadow Boost: ${onOff(activeSettings.shadowBoost)} | Temp: ${activeSettings.colorTempFilter}"
        val sharpNote = if (activeSettings.gpuSharpen) "ON (OFF Cools)" else "OFF (Coolest)"
        btnFlipAndSharp?.text = "Flip: ${activeSettings.landscapeFlip}° | Sharp: $sharpNote"
        btnOverlaysAndShield?.text =
            "PNG:${onOff(activeSettings.overlayImageEnabled)} | Banner:${onOff(activeSettings.liveBannerEnabled)} | Shield:${onOff(activeSettings.minimapShieldEnabled)}"

        val curBps = videoPipeline?.currentBitrateBps ?: (activeSettings.targetBitrateKbps * 1000)
        tvBitrateCurrent?.text =
            "Live Target: ${formatMbpsLabel(curBps / 1000)}  (Configured Max: ${formatMbpsLabel(activeSettings.targetBitrateKbps)})"
        btnAbrToggle?.text = "Ping Guard ABR 2.0: ${onOff(activeSettings.adaptiveBitrate)}"
        val is30Cooldown = videoPipeline?.thermalThrottle30Fps == true
        btnThermalCooldown?.text = if (is30Cooldown) {
            "30 FPS Cooldown Saver: ACTIVE (Tap for 60 FPS)"
        } else {
            "30 FPS Cooldown Saver: OFF (Tap to Cool Phone)"
        }
        btnThermalCooldown?.background = roundedBg(if (is30Cooldown) "#E65100" else "#263238", "#37474F", 7)

        val auxList = buildList {
            add("Primary:$connectionStateLabel")
            simulcastNodes.forEach { add(it.statusTag) }
            companionNode?.let { add(it.statusTag) }
        }.joinToString(" • ")
        val rtt = DiagnosticsLogger.lastRttMs
        val rttTxt = if (rtt > 0L) "${rtt}ms" else "OK"
        tvSimulcastStatus?.text = "Targets: $auxList | Ping: $rttTxt | Drift: ${DiagnosticsLogger.avDriftMs}ms"

        if (SystemClock.elapsedRealtime() - stopConfirmTapTimeMs >= 3000L) {
            btnStopStream?.text = "Stop Stream (2-Tap Confirm)"
        }
    }

    private fun removeFloatingOverlay() {
        val root = overlayRoot ?: return
        overlayRoot = null
        try { windowManager?.removeView(root) } catch (_: Exception) {}
    }

    private fun fullWidthPillButton(label: String, bgHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = roundedBg(bgHex, "#37474F", 7)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            ).apply { topMargin = dp(4) }
            layoutParams = lp
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener { onClick() }
        }
    }

    private fun compactRowButton(label: String, bgHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            background = roundedBg(bgHex, "#37474F", 7)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setOnClickListener { onClick() }
        }
    }

    private fun horizontalRow(vararg weightedButtons: Pair<Button, Float>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            ).apply { topMargin = dp(4) }
            layoutParams = rowLp
            weightedButtons.forEachIndexed { idx, (btn, weight) ->
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
                    if (idx > 0) marginStart = dp(4)
                }
                btn.layoutParams = lp
                addView(btn)
            }
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
        val scene = videoPipeline?.activeSceneMode ?: "LIVE"
        val summary = when {
            needsProjectionReapproval -> "Capture Paused • Tap to Re-Approve"
            scene != "LIVE" -> "$connectionStateLabel • $scene Active (1.5M Saver)"
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
            .setContentTitle("SolStream Studio Ultra")
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
                if (brb) "Resume Live" else "BRB Shield",
                serviceActionPendingIntent(ACTION_TOGGLE_BRB, 102)
            )
            .addAction(
                0,
                "★ Mark Clip",
                serviceActionPendingIntent(ACTION_MARK_CLIP, 104)
            )
            .addAction(
                0,
                "Stop",
                serviceActionPendingIntent(ACTION_STOP_STREAM, 103)
            )
            .build()
    }
}
