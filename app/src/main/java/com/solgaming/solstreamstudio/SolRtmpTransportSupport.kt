/*
- ॐ नमः शिवाय (Om Namah Shivaya)
- SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
- Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
- Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.content.Context
import android.media.MediaCodec
import android.os.SystemClock
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.rtmp.rtmp.RtmpClient
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
- Reflection bridge for Pedro RootEncoder 2.8.x RtmpClient security, adaptive chunking,
- ping RTT, and send-queue telemetry.
 */
internal object SolRtmpTelemetryBridge {
    private var resolved = false
    private var mCacheItems: Method? = null
    private var mCacheSize: Method? = null
    private var mQueueBytes: Method? = null
    private var mRtt: Method? = null
    private var mDroppedVideo: Method? = null
    private var mSentBitrate: Method? = null
    private var mBytesSend: Method? = null
    private var mShouldSendPings: Method? = null
    private var mSetTlsHostVerification: Method? = null
    private var mSetWriteChunkSize: Method? = null
    private var mClearCache: Method? = null

    private val byteSampleLock = Any()
    private var prevTotalBytesSent: Long = -1L
    private var prevBytesSampleTimeMs: Long = 0L

    fun resetSessionByteCounter() {
        synchronized(byteSampleLock) {
            prevTotalBytesSent = -1L
            prevBytesSampleTimeMs = 0L
        }
    }

    @Synchronized
    private fun ensureResolved(client: RtmpClient) {
        if (resolved) return
        resolved = true
        val cls = client.javaClass
        mCacheItems = findZeroArg(cls, "getItemsInCache", "getItemsInCacheCount", "getCacheItemsCount")
        mCacheSize = findZeroArg(cls, "getCacheSize", "getMaxCacheSize")
        mQueueBytes = findZeroArg(cls, "getQueueBytesOut", "getSentQueueBytes", "getBytesInQueue")
        mRtt = findZeroArg(cls, "getRtt", "getPingRtt")
        mDroppedVideo = findZeroArg(cls, "getDroppedVideoFrames", "getDroppedFrames")
        mSentBitrate = findZeroArg(cls, "getBitrate", "getSentBitrate")
        mBytesSend = findZeroArg(cls, "getBytesSend", "getBytesSent")
        mShouldSendPings = findBooleanArg(cls, "shouldSendPings", "setShouldSendPings")
        mSetTlsHostVerification = findBooleanArg(cls, "setTlsHostVerification", "enableTlsHostVerification")
        mSetWriteChunkSize = findIntArg(cls, "setWriteChunkSize")
        mClearCache = findZeroArg(cls, "clearCache")
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

    private fun findBooleanArg(cls: Class<*>, vararg names: String): Method? {
        for (n in names) {
            try {
                return cls.getMethod(n, Boolean::class.javaPrimitiveType ?: Boolean::class.java).apply {
                    isAccessible = true
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun findIntArg(cls: Class<*>, vararg names: String): Method? {
        for (n in names) {
            try {
                return cls.getMethod(n, Int::class.javaPrimitiveType ?: Int::class.java).apply {
                    isAccessible = true
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
- Enables TLS Hostname Verification, active RTMP Ping RTT measurement, and
- Network-Adaptive RTMP Write Chunk Sizing (65535 Wi-Fi / 4096 Cellular & Custom) before connect().
     */
    fun configureClientSecurityPingsAndChunkSize(client: RtmpClient, writeChunkSize: Int) {
        ensureResolved(client)
        try { mShouldSendPings?.invoke(client, true) } catch (_: Throwable) {}
        try { mSetWriteChunkSize?.invoke(client, writeChunkSize.coerceIn(128, 65535)) } catch (_: Throwable) {}
        try {
            if (mSetTlsHostVerification != null) {
                mSetTlsHostVerification?.invoke(client, true)
            } else {
                val f = client.javaClass.getDeclaredField("tlsHostVerification")
                f.isAccessible = true
                f.setBoolean(client, true)
            }
        } catch (_: Throwable) {
        }
    }

    fun clearCacheSafely(client: RtmpClient?) {
        if (client == null) return
        ensureResolved(client)
        try { mClearCache?.invoke(client) } catch (_: Throwable) {}
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
        val rawMicros = try { (mRtt?.invoke(client) as? Number)?.toLong() ?: -1L } catch (_: Exception) { -1L }
        return SolStreamService.convertRttMicrosToMs(rawMicros)
    }

    fun readDroppedVideoFrames(client: RtmpClient): Long {
        ensureResolved(client)
        return try { (mDroppedVideo?.invoke(client) as? Number)?.toLong() ?: 0L } catch (_: Exception) { 0L }
    }

    fun readSentBitrateKbps(client: RtmpClient): Int {
        ensureResolved(client)
        try {
            val direct = (mSentBitrate?.invoke(client) as? Number)?.toLong() ?: -1L
            if (direct >= 0L) return (direct / 1000L).toInt()
        } catch (_: Exception) {
        }
        return try {
            val totalBytes = (mBytesSend?.invoke(client) as? Number)?.toLong() ?: return -1
            val nowMs = SystemClock.elapsedRealtime()
            synchronized(byteSampleLock) {
                val prevB = prevTotalBytesSent
                val prevT = prevBytesSampleTimeMs
                if (prevB < 0L || totalBytes < prevB) {
                    prevTotalBytesSent = totalBytes
                    prevBytesSampleTimeMs = nowMs
                    return -1
                }
                val deltaMs = nowMs - prevT
                if (deltaMs < 250L) return -1
                val deltaBytes = totalBytes - prevB
                prevTotalBytesSent = totalBytes
                prevBytesSampleTimeMs = nowMs
                ((deltaBytes * 8L) / deltaMs).toInt()
            }
        } catch (_: Exception) {
            -1
        }
    }
}

/**
- Bounded leased-slot pooled Simulcast & Companion RTMP sender:
- - Exclusively leased PacketSlots (24 video + 32 audio) returned on completion or discard
- - Per-client callback identity guard, atomic SPS/PPS/VPS header triple & pre-swap connected = false reset
- - Guarantees SPS/PPS/VPS headers are applied on sendExecutor BEFORE connected flips true
- - Auto-reconnects cleanly on both onConnectionFailed, onDisconnect, and sync connect errors (unless auth rejected)
- - Interrupts stalled handshakes & drains queued slots immediately on stop()
 */
internal class SolAuxRtmpTarget(
    val name: String,
    val fullUrl: String,
    @Volatile var codecKey: String,
    val sessionGen: Long,
    @Volatile var videoWidth: Int,
    @Volatile var videoHeight: Int,
    @Volatile var videoFps: Int,
    @Volatile var usesSharedPrimaryHeaders: Boolean,
    private val isWifiOrEthernet: () -> Boolean,
    private val isSessionStillValid: (Long, Boolean) -> Boolean,
    private val fallbackPrimaryHeaders: () -> Triple<ByteArray, ByteArray?, ByteArray?>?,
    private val postOnMain: (() -> Unit) -> Unit,
    private val postDelayedOnMain: (Long, () -> Unit) -> Unit,
    private val onStateChanged: () -> Unit
) {
    private class AuxRtmpCallback(
        private val target: SolAuxRtmpTarget
    ) : ConnectChecker {
        @Volatile var clientRef: RtmpClient? = null

        override fun onConnectionStarted(url: String) {}
        override fun onConnectionSuccess() = target.onAuxConnectionSuccess(clientRef)
        override fun onNewBitrate(bitrate: Long) {}
        override fun onConnectionFailed(reason: String) = target.onAuxConnectionFailed(clientRef, reason)
        override fun onDisconnect() = target.onAuxDisconnect(clientRef)
        override fun onAuthError() = target.onAuxAuthError(clientRef)
        override fun onAuthSuccess() {}
    }

    private class PacketSlot(initialCapacity: Int) {
        var buffer: ByteBuffer = ByteBuffer.allocateDirect(initialCapacity)
        val info: MediaCodec.BufferInfo = MediaCodec.BufferInfo()

        fun loadFrom(srcBuf: ByteBuffer, srcInfo: MediaCodec.BufferInfo) {
            if (srcInfo.size > buffer.capacity()) {
                buffer = ByteBuffer.allocateDirect(srcInfo.size + 32_768)
            }
            val dup = srcBuf.duplicate()
            dup.position(srcInfo.offset)
            dup.limit(srcInfo.offset + srcInfo.size)
            buffer.clear()
            buffer.put(dup)
            buffer.flip()
            info.set(0, srcInfo.size, srcInfo.presentationTimeUs, srcInfo.flags)
        }
    }

    private interface PoolReleasableRunnable : Runnable {
        fun releaseSlotOnDiscard()
    }

    @Volatile var client: RtmpClient? = null
    @Volatile var connected = false
    @Volatile var stopped = false
    @Volatile private var authRejected = false
    private val reconnecting = AtomicBoolean(false)

    // Single atomic reference so SPS/PPS/VPS can never tear across threads
    @Volatile private var lastHeaders: Triple<ByteArray, ByteArray?, ByteArray?>? = null

    private val videoPoolCap = 24
    private val audioPoolCap = 32
    private val videoFreeSlots = ArrayBlockingQueue<PacketSlot>(videoPoolCap).apply {
        repeat(videoPoolCap) { add(PacketSlot(393_216)) }
    }
    private val audioFreeSlots = ArrayBlockingQueue<PacketSlot>(audioPoolCap).apply {
        repeat(audioPoolCap) { add(PacketSlot(4096)) }
    }

    private val sendExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(48),
        ThreadFactory { r -> Thread(r, "SolAuxRtmp-$name").apply { isDaemon = true } },
        { r, executor ->
            if (!executor.isShutdown) {
                val dropped = executor.queue.poll()
                (dropped as? PoolReleasableRunnable)?.releaseSlotOnDiscard()
                if (!executor.queue.offer(r)) {
                    (r as? PoolReleasableRunnable)?.releaseSlotOnDiscard()
                }
            } else {
                (r as? PoolReleasableRunnable)?.releaseSlotOnDiscard()
            }
        }
    )

    fun updateNegotiatedFormat(
        width: Int,
        height: Int,
        fps: Int,
        codec: String? = null,
        sharedPrimaryHeaders: Boolean? = null
    ) {
        videoWidth = width
        videoHeight = height
        videoFps = fps
        if (!codec.isNullOrBlank()) {
            codecKey = codec
        }
        if (sharedPrimaryHeaders != null) {
            usesSharedPrimaryHeaders = sharedPrimaryHeaders
        }
    }

    private fun executeControlSafely(task: () -> Unit) {
        if (sendExecutor.isShutdown) return
        try {
            sendExecutor.execute(task)
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun scheduleAuxReconnect(reason: String) {
        if (authRejected || stopped || !isSessionStillValid(sessionGen, stopped)) return
        if (reconnecting.compareAndSet(false, true)) {
            DiagnosticsLogger.log("AUX_WARN", "$name target disconnected ($reason); retrying in 4s...")
            postDelayedOnMain(4000L) {
                reconnecting.set(false)
                if (!authRejected && isSessionStillValid(sessionGen, stopped)) {
                    startAsync()
                }
            }
        }
    }

    fun startAsync() {
        stopped = false
        authRejected = false
        executeControlSafely {
            if (stopped || authRejected || !isSessionStillValid(sessionGen, stopped)) {
                return@executeControlSafely
            }
            // Reset connected BEFORE swapping/disconnecting previous client so no packets race
            connected = false
            val prevClient = client

            val cb = AuxRtmpCallback(this)
            val freshClient = RtmpClient(cb)
            cb.clientRef = freshClient
            client = freshClient

            if (prevClient != null) {
                Thread({
                    SolRtmpTelemetryBridge.clearCacheSafely(prevClient)
                    try { prevClient.disconnect() } catch (_: Exception) {}
                }, "SolAuxOldClose-$name").apply {
                    isDaemon = true
                    start()
                }
            }

            try {
                freshClient.setLogs(false)
                freshClient.setCheckServerAlive(false)
                freshClient.setOnlyVideo(false)
                val chunkSize = SolStreamService.resolveAdaptiveRtmpChunkSize(isWifiOrEthernet(), fullUrl)
                SolRtmpTelemetryBridge.configureClientSecurityPingsAndChunkSize(freshClient, chunkSize)
                try { freshClient.resizeCache(90) } catch (_: Throwable) {}
                SolRtmpTelemetryBridge.clearCacheSafely(freshClient)
                val vCodec = when (codecKey) {
                    "HEVC" -> VideoCodec.H265
                    "AV1" -> VideoCodec.AV1
                    else -> VideoCodec.H264
                }
                freshClient.setVideoCodec(vCodec)
                freshClient.setVideoResolution(videoWidth, videoHeight)
                freshClient.setFps(videoFps)
                freshClient.setAudioInfo(SolDualAudioEngine.SAMPLE_RATE, true)
                lastHeaders?.let { (sps, pps, vps) ->
                    freshClient.setVideoInfo(
                        ByteBuffer.wrap(sps),
                        pps?.let { ByteBuffer.wrap(it) },
                        vps?.let { ByteBuffer.wrap(it) }
                    )
                }
                freshClient.connect(fullUrl)
            } catch (e: Exception) {
                if (freshClient === client && !stopped && isSessionStillValid(sessionGen, stopped)) {
                    scheduleAuxReconnect("connect error: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    fun sendVideoHeader(sps: ByteArray, pps: ByteArray?, vps: ByteArray?) {
        val hdr = Triple(sps, pps, vps)
        lastHeaders = hdr
        if (!connected || stopped) return
        executeControlSafely {
            if (!connected || stopped) return@executeControlSafely
            try {
                client?.setVideoInfo(
                    ByteBuffer.wrap(hdr.first),
                    hdr.second?.let { ByteBuffer.wrap(it) },
                    hdr.third?.let { ByteBuffer.wrap(it) }
                )
            } catch (_: Exception) {
            }
        }
    }

    fun sendVideoBuffer(srcBuf: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!connected || stopped || info.size <= 0 || sendExecutor.isShutdown) return
        val slot = videoFreeSlots.poll() ?: return
        try {
            slot.loadFrom(srcBuf, info)
        } catch (_: Exception) {
            videoFreeSlots.offer(slot)
            return
        }
        val released = AtomicBoolean(false)
        val task = object : PoolReleasableRunnable {
            override fun releaseSlotOnDiscard() {
                if (released.compareAndSet(false, true)) {
                    videoFreeSlots.offer(slot)
                }
            }

            override fun run() {
                try {
                    if (connected && !stopped) {
                        client?.sendVideo(slot.buffer.duplicate(), slot.info)
                    }
                } catch (_: Exception) {
                } finally {
                    releaseSlotOnDiscard()
                }
            }
        }
        try {
            sendExecutor.execute(task)
        } catch (_: Exception) {
            task.releaseSlotOnDiscard()
        }
    }

    fun sendAudioBuffer(srcBuf: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!connected || stopped || info.size <= 0 || sendExecutor.isShutdown) return
        val slot = audioFreeSlots.poll() ?: return
        try {
            slot.loadFrom(srcBuf, info)
        } catch (_: Exception) {
            audioFreeSlots.offer(slot)
            return
        }
        val released = AtomicBoolean(false)
        val task = object : PoolReleasableRunnable {
            override fun releaseSlotOnDiscard() {
                if (released.compareAndSet(false, true)) {
                    audioFreeSlots.offer(slot)
                }
            }

            override fun run() {
                try {
                    if (connected && !stopped) {
                        client?.sendAudio(slot.buffer.duplicate(), slot.info)
                    }
                } catch (_: Exception) {
                } finally {
                    releaseSlotOnDiscard()
                }
            }
        }
        try {
            sendExecutor.execute(task)
        } catch (_: Exception) {
            task.releaseSlotOnDiscard()
        }
    }

    fun stop() {
        stopped = true
        connected = false
        val clientToClose = client
        client = null

        val staleTasks = try {
            sendExecutor.shutdownNow()
        } catch (_: Exception) {
            emptyList<Runnable>()
        }
        for (r in staleTasks) {
            (r as? PoolReleasableRunnable)?.releaseSlotOnDiscard()
        }

        if (clientToClose != null) {
            Thread({
                SolRtmpTelemetryBridge.clearCacheSafely(clientToClose)
                try { clientToClose.disconnect() } catch (_: Exception) {}
            }, "SolAuxStopClose-$name").apply {
                isDaemon = true
                start()
            }
        }
    }

    internal fun onAuxConnectionSuccess(sourceClient: RtmpClient?) {
        if (sourceClient == null ||
            sourceClient !== client ||
            !isSessionStillValid(sessionGen, stopped)
        ) {
            Thread({ try { sourceClient?.disconnect() } catch (_: Exception) {} }).apply {
                isDaemon = true
                start()
            }
            return
        }
        reconnecting.set(false)
        DiagnosticsLogger.log(
            "AUX_LIVE",
            "$name target connected and live (${videoWidth}x${videoHeight}@${videoFps}fps $codecKey)."
        )
        // Push SPS/PPS/VPS on sendExecutor BEFORE flipping connected = true so no video frame precedes headers
        executeControlSafely {
            if (stopped || sourceClient !== client || !isSessionStillValid(sessionGen, stopped)) {
                return@executeControlSafely
            }
            val own = lastHeaders
            if (own != null) {
                try {
                    sourceClient.setVideoInfo(
                        ByteBuffer.wrap(own.first),
                        own.second?.let { ByteBuffer.wrap(it) },
                        own.third?.let { ByteBuffer.wrap(it) }
                    )
                } catch (_: Exception) {
                }
            } else if (usesSharedPrimaryHeaders) {
                fallbackPrimaryHeaders()?.let { fb ->
                    lastHeaders = fb
                    try {
                        sourceClient.setVideoInfo(
                            ByteBuffer.wrap(fb.first),
                            fb.second?.let { ByteBuffer.wrap(it) },
                            fb.third?.let { ByteBuffer.wrap(it) }
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            if (!stopped && sourceClient === client && isSessionStillValid(sessionGen, stopped)) {
                connected = true
                postOnMain { onStateChanged() }
            }
        }
    }

    internal fun onAuxConnectionFailed(sourceClient: RtmpClient?, reason: String) {
        if (sourceClient !== client || !isSessionStillValid(sessionGen, stopped)) {
            return
        }
        connected = false
        postOnMain { onStateChanged() }
        scheduleAuxReconnect(reason)
    }

    internal fun onAuxDisconnect(sourceClient: RtmpClient?) {
        if (sourceClient !== client) return
        connected = false
        postOnMain { onStateChanged() }
        if (!authRejected && isSessionStillValid(sessionGen, stopped)) {
            scheduleAuxReconnect("server closed connection")
        }
    }

    internal fun onAuxAuthError(sourceClient: RtmpClient?) {
        if (sourceClient !== client || !isSessionStillValid(sessionGen, stopped)) {
            return
        }
        authRejected = true
        connected = false
        DiagnosticsLogger.log("AUX_AUTH_ERR", "$name stream key rejected by server.")
        postOnMain { onStateChanged() }
    }
}

/**
- Local 9-slot Session Report Archive store (uses applicationContext and self-healing JSON parsing).
 */
internal object SolReportArchiveStore {
    private const val PREFS_NAME = "solstream_overlay_ui_prefs"
    private const val KEY_ARCHIVE_JSON = "archived_session_reports_json"

    fun saveReport(context: Context, header: String, body: String) {
        try {
            val prefs = (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = try {
                val raw = prefs.getString(KEY_ARCHIVE_JSON, null)
                if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw)
            } catch (_: Exception) {
                JSONArray()
            }
            val entry = JSONObject().apply {
                put("header", header)
                put("body", body)
            }
            val updated = JSONArray()
            updated.put(entry)
            for (i in 0 until min(9, arr.length())) {
                arr.optJSONObject(i)?.let { updated.put(it) }
            }
            prefs.edit().putString(KEY_ARCHIVE_JSON, updated.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun loadReports(context: Context): List<Pair<String, String>> {
        return try {
            val prefs = (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_ARCHIVE_JSON, null)
            if (raw.isNullOrBlank()) return emptyList()
            val arr = JSONArray(raw)
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

    fun clearReports(context: Context) {
        try {
            (context.applicationContext ?: context)
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ARCHIVE_JSON)
                .apply()
        } catch (_: Exception) {
        }
    }
}
