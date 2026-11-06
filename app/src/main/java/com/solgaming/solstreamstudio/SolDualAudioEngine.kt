package com.solgaming.solstreamstudio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SolDualAudioEngine(private val context: Context) {

    val sampleRate = 48000
    val micDsp = MicDspProcessor(sampleRate)

    @Volatile var isRunning = false
        private set

    @Volatile var gameStreamMuted = false
    @Volatile var gameGainLinear = 1.0f
    @Volatile var vadDuckingDepth = 0.0f // 0.0f = Off, 0.20f = -20%, 0.35f = -35%
    @Volatile var micStatusText = "MIC READY"
    @Volatile var routedMicLabel = "Auto"
    @Volatile var gameAudioActive = false
    @Volatile var gameAudioReceivingSignal = false
        private set
    @Volatile var lastGamePeakDb = -90.0f
        private set
    @Volatile var lastMixTruePeakDb = -90.0f
        private set
    @Volatile var lastSentAudioPtsUs: Long = -1L
        private set
    @Volatile var aacEncoderSummary = "AAC-LC"
        private set

    /** Notifies SolStreamService if the audio mixer ever stops unexpectedly. */
    @Volatile var onFatalAudioError: ((String) -> Unit)? = null

    @Volatile private var activeSettings = StreamSettings()
    @Volatile private var targetMicDelaySamples = 0
    @Volatile private var pendingMicRecreate = false

    private var audioThread: Thread? = null
    private var callbackThread: HandlerThread? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null
    private var activeMicRecordRef: AudioRecord? = null
    private var streamOriginUs: Long = 0L

    fun updateLiveSettings(s: StreamSettings) {
        val prev = activeSettings
        activeSettings = s
        micDsp.applySettings(s)
        gameGainLinear = (s.gameGainPercent.coerceIn(0, 125)) / 100.0f
        vadDuckingDepth = (s.vadDuckingPercent.coerceIn(0, 60)) / 100.0f
        targetMicDelaySamples = (s.micSyncDelayMs.coerceIn(0, 200) * sampleRate) / 1000

        if (isRunning && (s.micDeviceId != prev.micDeviceId || s.micAudioSource != prev.micAudioSource)) {
            pendingMicRecreate = true
        }
    }

    fun switchMicDeviceLive(newDeviceId: Int) {
        val updated = activeSettings.copy(micDeviceId = newDeviceId)
        updateLiveSettings(updated)
    }

    fun startStreamingAudio(
        settings: StreamSettings,
        mediaProjection: MediaProjection?,
        originTimeUs: Long,
        onStatusChanged: ((String) -> Unit)?,
        onEncodedAac: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ): Boolean {
        if (isRunning && audioThread?.isAlive == true) return true
        activeSettings = settings
        pendingMicRecreate = false
        lastSentAudioPtsUs = -1L
        gameAudioReceivingSignal = false
        updateLiveSettings(settings)
        streamOriginUs = originTimeUs

        val aacCodec = createHardwareAacEncoder(settings.audioBitrateKbps * 1000)
        if (aacCodec == null) {
            DiagnosticsLogger.log(
                "AUDIO_ERR",
                "Failed to start AAC audio encoder.",
                "reqBitrate=${settings.audioBitrateKbps}k"
            )
            return false
        }

        val micRecord = buildMicAudioRecord(settings)
        if (micRecord == null || micRecord.state != AudioRecord.STATE_INITIALIZED) {
            try { aacCodec.stop(); aacCodec.release() } catch (_: Exception) {}
            DiagnosticsLogger.log(
                "MIC_ERR",
                "Microphone could not be opened. Check microphone permission.",
                "source=${settings.micAudioSource}"
            )
            return false
        }

        val gameRecord = if (mediaProjection != null) buildGamePlaybackRecord(mediaProjection) else null
        gameAudioActive = gameRecord != null && gameRecord.state == AudioRecord.STATE_INITIALIZED
        if (!gameAudioActive) {
            try { gameRecord?.release() } catch (_: Exception) {}
            DiagnosticsLogger.log(
                "GAME_AUDIO",
                "Internal game playback capture unavailable initially; will retry automatically in background.",
                "mediaProjection=${mediaProjection != null}"
            )
        }

        activeMicRecordRef = micRecord
        registerMicMonitorCallback(micRecord, onStatusChanged)
        isRunning = true

        audioThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runDualMixerLoop(
                initialMicRecord = micRecord,
                initialGameRecord = if (gameAudioActive) gameRecord else null,
                mediaProjection = mediaProjection,
                initialAacCodec = aacCodec,
                onStatusChanged = onStatusChanged,
                onEncodedAac = onEncodedAac
            )
        }, "SolStudioAudioMixer").apply { start() }

        return true
    }

    fun stopStreamingAudio() {
        isRunning = false
        try {
            audioThread?.join(1500)
        } catch (_: Exception) {
        }
        audioThread = null
        unregisterMicMonitorCallback()
        activeMicRecordRef = null
        gameAudioActive = false
        gameAudioReceivingSignal = false
        lastGamePeakDb = -90.0f
        lastMixTruePeakDb = -90.0f
    }

    private fun describeRoutedMic(dev: AudioDeviceInfo?): String {
        if (dev == null) return "Built-in"
        val prod = dev.productName?.toString()?.trim().orEmpty()
        val typeLabel = DiagnosticsLogger.audioDeviceTypeName(dev.type)
        return when {
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                if (prod.isNotEmpty()) "$prod • BT Call-Mode" else "BT Call-Mode"
            }
            prod.isNotEmpty() && !prod.equals(typeLabel, ignoreCase = true) -> "$prod ($typeLabel)"
            else -> typeLabel
        }
    }

    private fun registerMicMonitorCallback(
        micRecord: AudioRecord,
        onStatusChanged: ((String) -> Unit)?
    ) {
        unregisterMicMonitorCallback()
        try {
            val ht = HandlerThread("SolMicCallback").apply { start() }
            callbackThread = ht
            val cb = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                    val currentRec = activeMicRecordRef ?: micRecord
                    val mySession = currentRec.audioSessionId
                    val match = configs.firstOrNull { it.clientAudioSessionId == mySession } ?: return
                    val silenced = match.isClientSilenced
                    val routedDev = currentRec.routedDevice ?: match.audioDevice
                    val devDesc = describeRoutedMic(routedDev)
                    routedMicLabel = devDesc
                    val newStatus = if (silenced) "MIC SILENCED ($devDesc)" else "MIC OK ($devDesc)"
                    if (newStatus != micStatusText) {
                        micStatusText = newStatus
                        DiagnosticsLogger.micStatusLabel = newStatus
                        if (silenced) {
                            DiagnosticsLogger.log(
                                "MIC_WARN",
                                "Android temporarily silenced stream mic capture (check Accessibility overlay visibility).",
                                "session=$mySession dev=$devDesc"
                            )
                        } else {
                            val rateInfo = "${match.format.sampleRate}Hz/${match.format.channelCount}ch"
                            DiagnosticsLogger.log(
                                "MIC_OK",
                                "Stream microphone active on $devDesc.",
                                "session=$mySession fmt=$rateInfo"
                            )
                        }
                        onStatusChanged?.invoke(newStatus)
                    }
                }
            }
            recordingCallback = cb
            micRecord.registerAudioRecordingCallback(
                { r -> Handler(ht.looper).post(r) },
                cb
            )
        } catch (_: Exception) {
        }
    }

    private fun unregisterMicMonitorCallback() {
        val cb = recordingCallback
        val rec = activeMicRecordRef
        if (cb != null && rec != null) {
            try { rec.unregisterAudioRecordingCallback(cb) } catch (_: Exception) {}
        }
        recordingCallback = null
        try {
            callbackThread?.quitSafely()
        } catch (_: Exception) {
        }
        callbackThread = null
    }

    private fun createHardwareAacEncoder(requestedBps: Int): MediaCodec? {
        val clampedReq = requestedBps.coerceIn(96_000, 320_000)
        val candidates = listOf(clampedReq, 256_000, 192_000, 160_000, 128_000).distinct()

        val preferredCodecName: String? = try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            val aacEncoders = list.filter { info ->
                info.isEncoder && info.supportedTypes.any {
                    it.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true)
                }
            }
            val hwAac = aacEncoders.firstOrNull { it.isHardwareAccelerated && !it.isSoftwareOnly }
            (hwAac ?: aacEncoders.firstOrNull())?.name
        } catch (_: Exception) {
            null
        }

        for (bps in candidates) {
            var codec: MediaCodec? = null
            try {
                val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, bps)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                val createdCodec: MediaCodec = if (preferredCodecName != null && preferredCodecName.isNotBlank()) {
                    MediaCodec.createByCodecName(preferredCodecName)
                } else {
                    MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                }
                codec = createdCodec
                createdCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                createdCodec.start()

                val info = createdCodec.codecInfo
                val accelTag = if (info.isHardwareAccelerated && !info.isSoftwareOnly) "HW" else "Platform"
                aacEncoderSummary = "${info.name} ($accelTag • ${bps / 1000}kbps)"
                DiagnosticsLogger.log(
                    "AUDIO_ENC",
                    "Stereo 48 kHz AAC-LC encoder started: $aacEncoderSummary.",
                    "hw=${info.isHardwareAccelerated} swOnly=${info.isSoftwareOnly}"
                )
                return createdCodec
            } catch (_: Exception) {
                try { codec?.release() } catch (_: Exception) {}
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun buildMicAudioRecord(settings: StreamSettings): AudioRecord? {
        val chosenOption = CodecCapabilityProbe.listAvailableMicInputs(context)
            .firstOrNull { it.deviceId == settings.micDeviceId }

        val resolvedSource = when {
            settings.micAudioSource != 0 -> settings.micAudioSource
            chosenOption?.isBluetooth == true -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            else -> MediaRecorder.AudioSource.DEFAULT
        }

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBuf <= 0) return null

        return try {
            val rec = AudioRecord.Builder()
                .setAudioSource(resolvedSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf * 3, 480 * 4 * 12))
                .build()

            chosenOption?.deviceInfo?.let { info: AudioDeviceInfo ->
                rec.setPreferredDevice(info)
            }
            rec
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun buildGamePlaybackRecord(mediaProjection: MediaProjection): AudioRecord? {
        return try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            if (minBuf <= 0) return null

            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf * 3, 480 * 8 * 16))
                .build()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 64-bit pointer-safe Stereo Circular Buffer with drift-locked fractional linear interpolation
     * and anti-click soft-release/fade-in.
     */
    private class GameStereoSyncRing {
        private val capFrames = 8192 // ~170 ms @ 48 kHz (power of 2)
        private val maskLong = (capFrames - 1).toLong()
        private val dataL = FloatArray(capFrames)
        private val dataR = FloatArray(capFrames)

        private var writeFrame: Long = 0L
        private var readFrame: Long = 0L
        private var readFrac: Double = 0.0

        private val targetFillFrames = 1440.0 // 30 ms cushion over 20 ms OS bursts
        private var smoothedFill = targetFillFrames
        private var primed = false
        private var fadeInGain = 0.0f

        private var crossfadeFromL = 0.0f
        private var crossfadeFromR = 0.0f
        private var crossfadePos = 240
        private val crossfadeLen = 240 // 5 ms clickless crossfade on overflow reset

        var lastOutL = 0.0f
            private set
        var lastOutR = 0.0f
            private set

        fun pushInterleaved(interleaved: FloatArray, stereoFrames: Int) {
            if (stereoFrames <= 0) return
            val currentAvail = writeFrame - readFrame
            if (currentAvail + stereoFrames > capFrames - 32) {
                crossfadeFromL = lastOutL
                crossfadeFromR = lastOutR
                crossfadePos = 0
                readFrame = max(0L, (writeFrame + stereoFrames) - targetFillFrames.toLong())
                readFrac = 0.0
                smoothedFill = targetFillFrames
            }
            for (f in 0 until stereoFrames) {
                val idx = ((writeFrame + f) and maskLong).toInt()
                val l = interleaved[f * 2]
                val r = interleaved[f * 2 + 1]
                dataL[idx] = if (l.isFinite()) l else 0f
                dataR[idx] = if (r.isFinite()) r else 0f
            }
            writeFrame += stereoFrames
        }

        fun pullSynchronizedBlock(outL: FloatArray, outR: FloatArray, outFrames: Int) {
            val availNow = (writeFrame - readFrame).toDouble() - readFrac
            if (!primed) {
                if (availNow >= 1200.0) {
                    primed = true
                    fadeInGain = 0.0f
                    smoothedFill = availNow
                } else {
                    for (i in 0 until outFrames) {
                        lastOutL *= 0.96f
                        lastOutR *= 0.96f
                        if (abs(lastOutL) < 1e-6f) lastOutL = 0f
                        if (abs(lastOutR) < 1e-6f) lastOutR = 0f
                        outL[i] = lastOutL
                        outR[i] = lastOutR
                    }
                    return
                }
            }

            if (availNow > 4800.0) {
                crossfadeFromL = lastOutL
                crossfadeFromR = lastOutR
                crossfadePos = 0
                readFrame = max(0L, writeFrame - targetFillFrames.toLong())
                readFrac = 0.0
                smoothedFill = targetFillFrames
            } else {
                smoothedFill += 0.04 * (availNow - smoothedFill)
            }

            val fillErr = smoothedFill - targetFillFrames
            val step = (1.0 + (fillErr * 0.000003).coerceIn(-0.0015, 0.0015))

            for (i in 0 until outFrames) {
                if (readFrame + 1L < writeFrame) {
                    val i0 = (readFrame and maskLong).toInt()
                    val i1 = ((readFrame + 1L) and maskLong).toInt()
                    val frac = readFrac.toFloat()

                    var sL = dataL[i0] + (dataL[i1] - dataL[i0]) * frac
                    var sR = dataR[i0] + (dataR[i1] - dataR[i0]) * frac

                    if (fadeInGain < 1.0f) {
                        fadeInGain = min(1.0f, fadeInGain + (1.0f / 240.0f))
                        sL *= fadeInGain
                        sR *= fadeInGain
                    }
                    if (crossfadePos < crossfadeLen) {
                        val alpha = crossfadePos.toFloat() / crossfadeLen.toFloat()
                        sL = crossfadeFromL * (1.0f - alpha) + sL * alpha
                        sR = crossfadeFromR * (1.0f - alpha) + sR * alpha
                        crossfadePos++
                    }

                    readFrac += step
                    val whole = readFrac.toLong()
                    if (whole > 0L) {
                        readFrame += whole
                        readFrac -= whole.toDouble()
                    }
                    lastOutL = sL
                    lastOutR = sR
                } else {
                    primed = false
                    lastOutL *= 0.96f
                    lastOutR *= 0.96f
                    if (abs(lastOutL) < 1e-6f) lastOutL = 0f
                    if (abs(lastOutR) < 1e-6f) lastOutR = 0f
                }
                outL[i] = lastOutL
                outR[i] = lastOutR
            }
        }
    }

    /**
     * 4x Catmull-Rom cubic inter-sample peak detector evaluating t = 0.25, 0.50, 0.75, 1.00
     * with zero heap allocations.
     */
    private fun cubicInterSamplePeak(p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val a0 = -0.5f * p0 + 1.5f * p1 - 1.5f * p2 + 0.5f * p3
        val a1 = p0 - 2.5f * p1 + 2.0f * p2 - 0.5f * p3
        val a2 = -0.5f * p0 + 0.5f * p2
        val a3 = p1

        val q25 = abs(((a0 * 0.25f + a1) * 0.25f + a2) * 0.25f + a3)
        val q50 = abs(((a0 * 0.50f + a1) * 0.50f + a2) * 0.50f + a3)
        val q75 = abs(((a0 * 0.75f + a1) * 0.75f + a2) * 0.75f + a3)
        return max(max(abs(p1), abs(p2)), max(q25, max(q50, q75)))
    }

    private fun runDualMixerLoop(
        initialMicRecord: AudioRecord,
        initialGameRecord: AudioRecord?,
        mediaProjection: MediaProjection?,
        initialAacCodec: MediaCodec,
        onStatusChanged: ((String) -> Unit)?,
        onEncodedAac: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        var micRecord: AudioRecord? = initialMicRecord
        var gameRecord: AudioRecord? = initialGameRecord
        var aacCodec: MediaCodec? = initialAacCodec

        val frameSamples = 480 // 10 ms @ 48,000 Hz
        val maxOutSamples = frameSamples + 2
        val micFloat = FloatArray(frameSamples)
        val resampledMic = FloatArray(maxOutSamples)
        val delayedMicFloat = FloatArray(maxOutSamples)

        val gameStaging = FloatArray(1920 * 2)
        val gameBlockL = FloatArray(maxOutSamples)
        val gameBlockR = FloatArray(maxOutSamples)
        val gameRing = GameStereoSyncRing()

        val outPcmBytes = ByteArray((maxOutSamples + 4) * 4)

        val maxDelaySamples = (sampleRate * 220) / 1000
        val delayRing = FloatArray(maxDelaySamples + maxOutSamples + 8)
        var delayWritePos = 0
        var activeDelaySamples = targetMicDelaySamples.coerceIn(0, (sampleRate * 200) / 1000)

        // 64-sample (1.33 ms) Look-Ahead 4x Cubic Oversampled Limiter state
        val lookLen = 64
        val lookMask = lookLen - 1
        val lookBufL = FloatArray(lookLen)
        val lookBufR = FloatArray(lookLen)
        val lookReqGain = FloatArray(lookLen) { 1.0f }
        var lookIdx = 0
        var histL0 = 0f; var histL1 = 0f; var histL2 = 0f
        var histR0 = 0f; var histR1 = 0f; var histR2 = 0f
        var limiterGain = 1.0f

        val packetPool = Array(32) { ByteBuffer.allocateDirect(4096) }
        val infoPool = Array(32) { MediaCodec.BufferInfo() }
        var poolIndex = 0
        val outBufferInfo = MediaCodec.BufferInfo()

        val micTs = AudioTimestamp()
        val gameTs = AudioTimestamp()
        var lastDriftCheckMs = 0L

        var duckSmoothGain = 1.0f
        var gameMuteSmooth = 1.0f
        var micStartupFade = 0.0f
        var wasStreamMicMuted = micDsp.streamMicMuted
        var prevUnmutedMicSample = 0.0f
        var micUnmuteRamp = 1.0f

        var audioAnchorInitialized = false
        var audioStartOffsetUs = 0L
        var totalSamplesQueued = 0L
        var lastAacSentPtsUs = -1L
        var slewCooldownBlocks = 0

        var consecutiveMicErrors = 0
        var lastMicRecreateAttemptMs = 0L
        var lastGameRecreateAttemptMs = System.currentTimeMillis()
        var lastAacRecreateAttemptMs = 0L
        var silentGameBlocks = 0
        var fatalExitReason: String? = null
        var prngState = 0x5A17C9E3

        try {
            micRecord?.startRecording()
            gameRecord?.startRecording()
            val startDevDesc = describeRoutedMic(micRecord?.routedDevice)
            routedMicLabel = startDevDesc
            micStatusText = "MIC OK ($startDevDesc)"
            DiagnosticsLogger.micStatusLabel = micStatusText
            onStatusChanged?.invoke(micStatusText)

            while (isRunning) {
                if (pendingMicRecreate) {
                    pendingMicRecreate = false
                    val rebuilt = recreateMicRecordSafely(micRecord, onStatusChanged)
                    if (rebuilt != null) {
                        micRecord = rebuilt
                        micStartupFade = 0.0f
                        consecutiveMicErrors = 0
                    }
                }

                val curMic: AudioRecord? = micRecord
                val readMic = curMic?.read(micFloat, 0, frameSamples, AudioRecord.READ_BLOCKING)
                    ?: AudioRecord.ERROR_INVALID_OPERATION

                val validMicSamples: Int
                if (readMic > 0) {
                    consecutiveMicErrors = 0
                    validMicSamples = readMic
                    if (!audioAnchorInitialized) {
                        audioStartOffsetUs = max(0L, (System.nanoTime() / 1000L) - streamOriginUs)
                        audioAnchorInitialized = true
                    }
                } else {
                    consecutiveMicErrors++
                    try { Thread.sleep(10) } catch (_: Exception) {}
                    if (!audioAnchorInitialized) {
                        audioStartOffsetUs = max(0L, (System.nanoTime() / 1000L) - streamOriginUs)
                        audioAnchorInitialized = true
                    }
                    micFloat.fill(0f)
                    validMicSamples = frameSamples

                    val nowErrMs = System.currentTimeMillis()
                    if ((readMic == AudioRecord.ERROR_DEAD_OBJECT || consecutiveMicErrors >= 10) &&
                        nowErrMs - lastMicRecreateAttemptMs > 1500L
                    ) {
                        lastMicRecreateAttemptMs = nowErrMs
                        val rebuilt = recreateMicRecordSafely(micRecord, onStatusChanged)
                        if (rebuilt != null) {
                            micRecord = rebuilt
                            micStartupFade = 0.0f
                            consecutiveMicErrors = 0
                        }
                    }
                }

                // Drain game audio OR keep retrying gameRecord creation every 3s if currently null
                val snapshotGame = gameRecord
                if (snapshotGame != null) {
                    var safetyReads = 0
                    while (safetyReads < 4) {
                        val activeGame: AudioRecord = gameRecord ?: break
                        val rg = activeGame.read(gameStaging, 0, gameStaging.size, AudioRecord.READ_NON_BLOCKING)
                        if (rg > 0) {
                            gameRing.pushInterleaved(gameStaging, rg / 2)
                            safetyReads++
                            if (rg < gameStaging.size) break
                        } else {
                            if (rg == AudioRecord.ERROR_DEAD_OBJECT && mediaProjection != null) {
                                val nowGMs = System.currentTimeMillis()
                                if (nowGMs - lastGameRecreateAttemptMs > 2500L) {
                                    lastGameRecreateAttemptMs = nowGMs
                                    gameRecord = recreateGameRecordSafely(activeGame, mediaProjection)
                                }
                            }
                            break
                        }
                    }
                } else if (mediaProjection != null) {
                    val nowGMs = System.currentTimeMillis()
                    if (nowGMs - lastGameRecreateAttemptMs > 3000L) {
                        lastGameRecreateAttemptMs = nowGMs
                        gameRecord = recreateGameRecordSafely(null, mediaProjection)
                    }
                }

                // 1. Process Microphone ONLY in 32-bit float before mixing
                val currentlyMuted = micDsp.streamMicMuted
                micDsp.processMonoFrameInPlace(micFloat, validMicSamples)

                if (!wasStreamMicMuted && currentlyMuted) {
                    var tail = prevUnmutedMicSample
                    val fadeLen = min(validMicSamples, 240)
                    for (i in 0 until fadeLen) {
                        tail *= 0.96f
                        micFloat[i] = tail
                    }
                } else if (wasStreamMicMuted && !currentlyMuted) {
                    micUnmuteRamp = 0.0f
                }
                wasStreamMicMuted = currentlyMuted

                for (i in 0 until validMicSamples) {
                    var s = micFloat[i]
                    if (!s.isFinite()) s = 0f
                    if (micStartupFade < 1.0f) {
                        micStartupFade = min(1.0f, micStartupFade + (1.0f / 1440.0f))
                        s *= micStartupFade
                    }
                    if (micUnmuteRamp < 1.0f) {
                        micUnmuteRamp = min(1.0f, micUnmuteRamp + (1.0f / 240.0f))
                        s *= micUnmuteRamp
                    }
                    micFloat[i] = s
                }
                if (!currentlyMuted && validMicSamples > 0) {
                    prevUnmutedMicSample = micFloat[validMicSamples - 1]
                } else {
                    prevUnmutedMicSample = 0f
                }

                val wallElapsedUs = max(0L, (System.nanoTime() / 1000L) - streamOriginUs)
                val samplePtsUs = audioStartOffsetUs + (totalSamplesQueued * 1_000_000L) / sampleRate
                val wallDriftUs = wallElapsedUs - samplePtsUs
                val outSamples = if (slewCooldownBlocks > 0) {
                    slewCooldownBlocks--
                    validMicSamples
                } else if (validMicSamples == frameSamples && wallDriftUs > 30_000L) {
                    slewCooldownBlocks = 4
                    frameSamples + 1
                } else if (validMicSamples == frameSamples && wallDriftUs < -30_000L) {
                    slewCooldownBlocks = 4
                    frameSamples - 1
                } else {
                    validMicSamples
                }

                val micReadyBlock: FloatArray = if (outSamples == validMicSamples) {
                    micFloat
                } else {
                    val scale = (validMicSamples - 1).toFloat() / max(1, outSamples - 1).toFloat()
                    for (i in 0 until outSamples) {
                        val pos = i * scale
                        val idx0 = pos.toInt().coerceIn(0, validMicSamples - 1)
                        val idx1 = min(validMicSamples - 1, idx0 + 1)
                        val frac = pos - idx0
                        resampledMic[i] = micFloat[idx0] + (micFloat[idx1] - micFloat[idx0]) * frac
                    }
                    resampledMic
                }

                // 2. Apply optional pre-allocated lip-sync delay smoothly
                val desiredDelay = targetMicDelaySamples.coerceIn(0, maxDelaySamples)
                if (activeDelaySamples < desiredDelay) activeDelaySamples++
                else if (activeDelaySamples > desiredDelay) activeDelaySamples--

                val ringLen = delayRing.size
                val micSourceArray: FloatArray = if (activeDelaySamples <= 0 && desiredDelay <= 0) {
                    micReadyBlock
                } else {
                    for (i in 0 until outSamples) {
                        delayRing[delayWritePos] = micReadyBlock[i]
                        val readPos = (delayWritePos - activeDelaySamples + ringLen) % ringLen
                        delayedMicFloat[i] = delayRing[readPos]
                        delayWritePos = (delayWritePos + 1) % ringLen
                    }
                    delayedMicFloat
                }

                var rawGamePeakInBlock = 0f
                if (gameRecord != null && gameAudioActive) {
                    gameRing.pullSynchronizedBlock(gameBlockL, gameBlockR, outSamples)
                    for (i in 0 until outSamples) {
                        val p = max(abs(gameBlockL[i]), abs(gameBlockR[i]))
                        if (p > rawGamePeakInBlock) rawGamePeakInBlock = p
                    }
                    if (rawGamePeakInBlock > 0.00006f) { // > -84 dBFS real game audio signal
                        silentGameBlocks = 0
                        gameAudioReceivingSignal = true
                    } else {
                        silentGameBlocks++
                        if (silentGameBlocks > 300) { // > 3.0s continuous silence
                            gameAudioReceivingSignal = false
                        }
                    }
                } else {
                    gameBlockL.fill(0f, 0, outSamples)
                    gameBlockR.fill(0f, 0, outSamples)
                    gameAudioReceivingSignal = false
                }

                // 3. Smooth VAD Auto-Ducking + Game Mute + 64-Sample Look-Ahead 4x Cubic Oversampled Limiter
                val targetDuck = if (vadDuckingDepth > 0f && micDsp.vadSpeechActive && !currentlyMuted) {
                    1.0f - vadDuckingDepth
                } else {
                    1.0f
                }
                val targetGameMute = if (gameStreamMuted) 0.0f else 1.0f
                val gLinear = gameGainLinear
                val ceiling = 0.875f // -1.16 dBTP broadcast-safe ceiling

                var blockGamePeak = 1e-5f
                var blockMixTruePeak = 1e-5f
                var byteIdx = 0

                for (i in 0 until outSamples) {
                    val duckSpeed = if (targetDuck < duckSmoothGain) 0.015f else 0.002f
                    duckSmoothGain += duckSpeed * (targetDuck - duckSmoothGain)
                    gameMuteSmooth += 0.008f * (targetGameMute - gameMuteSmooth)

                    val gL = gameBlockL[i] * gLinear * duckSmoothGain * gameMuteSmooth
                    val gR = gameBlockR[i] * gLinear * duckSmoothGain * gameMuteSmooth
                    val gAbs = max(abs(gL), abs(gR))
                    if (gAbs > blockGamePeak) blockGamePeak = gAbs

                    val mCenter = micSourceArray[i]
                    val inMixL = gL + mCenter
                    val inMixR = gR + mCenter

                    val tpL = cubicInterSamplePeak(histL0, histL1, histL2, inMixL)
                    val tpR = cubicInterSamplePeak(histR0, histR1, histR2, inMixR)
                    histL0 = histL1; histL1 = histL2; histL2 = inMixL
                    histR0 = histR1; histR1 = histR2; histR2 = inMixR

                    val estTruePeak = max(tpL, tpR)
                    if (estTruePeak > blockMixTruePeak) blockMixTruePeak = estTruePeak
                    val reqGain = if (estTruePeak > ceiling) ceiling / estTruePeak else 1.0f

                    // Push histL1/histR1 (aligned with cubic interval [p1..p2]) into 64-sample look-ahead line
                    lookBufL[lookIdx] = histL1
                    lookBufR[lookIdx] = histR1
                    lookReqGain[lookIdx] = reqGain
                    lookIdx = (lookIdx + 1) and lookMask

                    val delayedL = lookBufL[lookIdx]
                    val delayedR = lookBufR[lookIdx]

                    var minWindowGain = 1.0f
                    for (k in 0 until lookLen) {
                        val g = lookReqGain[k]
                        if (g < minWindowGain) minWindowGain = g
                    }

                    if (minWindowGain < limiterGain) {
                        limiterGain += 0.075f * (minWindowGain - limiterGain)
                        val delPeak = max(abs(delayedL), abs(delayedR))
                        if (delPeak * limiterGain > ceiling) {
                            limiterGain = ceiling / delPeak
                        }
                    } else {
                        limiterGain += 0.0005f * (minWindowGain - limiterGain)
                    }

                    val outL = (delayedL * limiterGain).coerceIn(-0.99f, 0.99f)
                    val outR = (delayedR * limiterGain).coerceIn(-0.99f, 0.99f)

                    prngState = prngState xor (prngState shl 13)
                    prngState = prngState xor (prngState ushr 17)
                    prngState = prngState xor (prngState shl 5)
                    val r1 = ((prngState and 0xFFFF) / 65536.0f) - 0.5f
                    prngState = prngState xor (prngState shl 13)
                    prngState = prngState xor (prngState ushr 17)
                    prngState = prngState xor (prngState shl 5)
                    val r2 = ((prngState and 0xFFFF) / 65536.0f) - 0.5f
                    val dither = (r1 + r2) * 0.5f

                    val sL = (outL * 32767.0f + dither).roundToInt().coerceIn(-32768, 32767)
                    val sR = (outR * 32767.0f + dither).roundToInt().coerceIn(-32768, 32767)

                    outPcmBytes[byteIdx++] = (sL and 0xFF).toByte()
                    outPcmBytes[byteIdx++] = ((sL shr 8) and 0xFF).toByte()
                    outPcmBytes[byteIdx++] = (sR and 0xFF).toByte()
                    outPcmBytes[byteIdx++] = ((sR shr 8) and 0xFF).toByte()
                }

                lastGamePeakDb = 20.0f * log10(max(1e-5f, blockGamePeak))
                lastMixTruePeakDb = 20.0f * log10(max(1e-5f, blockMixTruePeak * limiterGain))

                // 4. Periodic routed-device & hardware timestamp check (every 2s)
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastDriftCheckMs > 2000L) {
                    lastDriftCheckMs = nowMs
                    val liveDev = describeRoutedMic(curMic?.routedDevice)
                    if (liveDev != routedMicLabel) {
                        routedMicLabel = liveDev
                        if (!micStatusText.startsWith("MIC SILENCED")) {
                            micStatusText = "MIC OK ($liveDev)"
                            DiagnosticsLogger.micStatusLabel = micStatusText
                            onStatusChanged?.invoke(micStatusText)
                        }
                    }
                    val snapMic = curMic
                    val snapGame = gameRecord
                    if (snapMic != null && snapGame != null) {
                        snapMic.getTimestamp(micTs, AudioTimestamp.TIMEBASE_MONOTONIC)
                        snapGame.getTimestamp(gameTs, AudioTimestamp.TIMEBASE_MONOTONIC)
                    }
                }

                // 5 & 6. Feed sample-accurate PCM into AAC Encoder & drain packets (with AAC self-recovery)
                var activeAac: MediaCodec? = aacCodec
                if (activeAac == null && nowMs - lastAacRecreateAttemptMs > 2000L) {
                    lastAacRecreateAttemptMs = nowMs
                    activeAac = createHardwareAacEncoder(activeSettings.audioBitrateKbps * 1000)
                    aacCodec = activeAac
                }

                if (activeAac != null) {
                    try {
                        val ptsUs = audioStartOffsetUs + (totalSamplesQueued * 1_000_000L) / sampleRate
                        val inIdx = activeAac.dequeueInputBuffer(5000L)
                        if (inIdx >= 0) {
                            val inBuf = activeAac.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                inBuf.clear()
                                val bytesToWrite = min(byteIdx, inBuf.remaining())
                                inBuf.put(outPcmBytes, 0, bytesToWrite)
                                activeAac.queueInputBuffer(inIdx, 0, bytesToWrite, ptsUs, 0)
                                totalSamplesQueued += (bytesToWrite / 4).toLong()
                            }
                        } else {
                            totalSamplesQueued += outSamples.toLong()
                        }

                        while (true) {
                            val outIdx = activeAac.dequeueOutputBuffer(outBufferInfo, 0L)
                            if (outIdx < 0) break
                            val outBuf = activeAac.getOutputBuffer(outIdx)
                            val isConfig = (outBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (outBuf != null && outBufferInfo.size > 0 && !isConfig) {
                                val slot = poolIndex
                                poolIndex = (poolIndex + 1) % packetPool.size

                                val copyBuf = packetPool[slot]
                                copyBuf.clear()
                                val copySize = min(outBufferInfo.size, copyBuf.capacity())
                                outBuf.position(outBufferInfo.offset)
                                outBuf.limit(outBufferInfo.offset + copySize)
                                copyBuf.put(outBuf)
                                copyBuf.flip()

                                var outPtsUs = outBufferInfo.presentationTimeUs
                                if (outPtsUs <= lastAacSentPtsUs) {
                                    outPtsUs = lastAacSentPtsUs + 1_000L
                                }
                                lastAacSentPtsUs = outPtsUs
                                lastSentAudioPtsUs = outPtsUs

                                val infoCopy = infoPool[slot].apply {
                                    set(0, copySize, outPtsUs, outBufferInfo.flags)
                                }
                                onEncodedAac(copyBuf, infoCopy)
                            }
                            activeAac.releaseOutputBuffer(outIdx, false)
                        }
                    } catch (codecEx: Exception) {
                        DiagnosticsLogger.log(
                            "AUDIO_ENC_RECOVER",
                            "AAC audio encoder hiccupped (${codecEx.javaClass.simpleName}); rebuilding encoder automatically.",
                            codecEx.message ?: ""
                        )
                        try { activeAac.stop() } catch (_: Exception) {}
                        try { activeAac.release() } catch (_: Exception) {}
                        aacCodec = null
                        totalSamplesQueued += outSamples.toLong()
                    }
                } else {
                    totalSamplesQueued += outSamples.toLong()
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                fatalExitReason = "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
                DiagnosticsLogger.log(
                    "AUDIO_ERR",
                    "Audio mixer stopped unexpectedly ($fatalExitReason).",
                    e.javaClass.name
                )
            }
        } finally {
            val exitedWhileActive = isRunning
            isRunning = false
            try { micRecord?.stop() } catch (_: Exception) {}
            try { micRecord?.release() } catch (_: Exception) {}
            try { gameRecord?.stop() } catch (_: Exception) {}
            try { gameRecord?.release() } catch (_: Exception) {}
            try { aacCodec?.stop() } catch (_: Exception) {}
            try { aacCodec?.release() } catch (_: Exception) {}
            if (exitedWhileActive) {
                val reason = fatalExitReason ?: "Unexpected audio thread exit"
                onFatalAudioError?.invoke(reason)
            }
        }
    }

    private fun recreateMicRecordSafely(
        oldMic: AudioRecord?,
        onStatusChanged: ((String) -> Unit)?
    ): AudioRecord? {
        unregisterMicMonitorCallback()
        try { oldMic?.stop() } catch (_: Exception) {}
        try { oldMic?.release() } catch (_: Exception) {}

        val fresh = buildMicAudioRecord(activeSettings)
        if (fresh != null && fresh.state == AudioRecord.STATE_INITIALIZED) {
            return try {
                fresh.startRecording()
                activeMicRecordRef = fresh
                registerMicMonitorCallback(fresh, onStatusChanged)
                val devDesc = describeRoutedMic(fresh.routedDevice)
                routedMicLabel = devDesc
                micStatusText = "MIC OK ($devDesc)"
                DiagnosticsLogger.micStatusLabel = micStatusText
                DiagnosticsLogger.log(
                    "MIC_RECOVER",
                    "Microphone input seamlessly switched/recovered on $devDesc.",
                    "deviceId=${activeSettings.micDeviceId}"
                )
                onStatusChanged?.invoke(micStatusText)
                fresh
            } catch (_: Exception) {
                try { fresh.release() } catch (_: Exception) {}
                null
            }
        }
        try { fresh?.release() } catch (_: Exception) {}
        return null
    }

    private fun recreateGameRecordSafely(
        oldGame: AudioRecord?,
        mediaProjection: MediaProjection
    ): AudioRecord? {
        try { oldGame?.stop() } catch (_: Exception) {}
        try { oldGame?.release() } catch (_: Exception) {}

        val fresh = buildGamePlaybackRecord(mediaProjection)
        if (fresh != null && fresh.state == AudioRecord.STATE_INITIALIZED) {
            return try {
                fresh.startRecording()
                gameAudioActive = true
                DiagnosticsLogger.log(
                    "GAME_AUDIO",
                    "Internal game audio capture active/recovered.",
                    "status=OK"
                )
                fresh
            } catch (_: Exception) {
                try { fresh.release() } catch (_: Exception) {}
                gameAudioActive = false
                null
            }
        }
        try { fresh?.release() } catch (_: Exception) {}
        gameAudioActive = false
        return null
    }

    companion object {
        private const val TEST_PCM_FILE = "solstream_mic_test.pcm"

        fun getTestFile(context: Context): File = File(context.cacheDir, TEST_PCM_FILE)

        fun deleteMicTestFile(context: Context): Boolean {
            val f = getTestFile(context)
            return if (f.exists()) f.delete() else true
        }

        @SuppressLint("MissingPermission")
        fun recordFiveSecondMicTest(
            context: Context,
            settings: StreamSettings,
            onProgress: (String) -> Unit,
            onComplete: (Boolean, String) -> Unit
        ) {
            Thread({
                val sampleRate = 48000
                val dsp = MicDspProcessor(sampleRate).apply { applySettings(settings) }
                val engine = SolDualAudioEngine(context)
                val rec = engine.buildMicAudioRecord(settings)
                if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                    onComplete(false, "Could not open selected mic. Check Mic permission or Bluetooth state.")
                    return@Thread
                }

                val outFile = getTestFile(context)
                val frame = FloatArray(480)
                val pcmBytes = ByteArray(480 * 2)
                var maxPeakDb = -90f
                var startupFade = 0f

                try {
                    FileOutputStream(outFile).use { fos ->
                        rec.startRecording()
                        val devName = engine.describeRoutedMic(rec.routedDevice)
                        val totalFrames = 500
                        for (step in 0 until totalFrames) {
                            val r = rec.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                            if (r <= 0) continue
                            dsp.processMonoFrameInPlace(frame, r)
                            if (dsp.lastPeakDb > maxPeakDb) maxPeakDb = dsp.lastPeakDb

                            var idx = 0
                            for (i in 0 until r) {
                                var sample = if (frame[i].isFinite()) frame[i] else 0f
                                if (startupFade < 1f) {
                                    startupFade = min(1f, startupFade + (1f / 960f))
                                    sample *= startupFade
                                }
                                val v = (sample.coerceIn(-0.98f, 0.98f) * 32767f).roundToInt().coerceIn(-32768, 32767)
                                pcmBytes[idx++] = (v and 0xFF).toByte()
                                pcmBytes[idx++] = ((v shr 8) and 0xFF).toByte()
                            }
                            fos.write(pcmBytes, 0, idx)
                            if (step % 50 == 0) {
                                val secLeft = 5 - (step / 100)
                                onProgress("Recording on $devName (${secLeft}s)... Peak: ${dsp.lastPeakDb.toInt()} dB")
                            }
                        }
                        onComplete(
                            true,
                            "5s test saved ($devName)! Peak: ${maxPeakDb.toInt()} dB | Noise Floor: ${dsp.lastNoiseFloorDb.toInt()} dB | Clips: ${dsp.clipCount}"
                        )
                    }
                } catch (e: Exception) {
                    onComplete(false, "Mic test failed: ${e.javaClass.simpleName}")
                } finally {
                    try { rec.stop(); rec.release() } catch (_: Exception) {}
                }
            }, "SolMicTester").start()
        }

        fun playRecordedMicTest(context: Context, onFinished: (String) -> Unit) {
            val file = getTestFile(context)
            if (!file.exists() || file.length() == 0L) {
                onFinished("No mic test recorded yet. Tap 'Record 5s Test' first.")
                return
            }
            Thread({
                var track: AudioTrack? = null
                try {
                    val data = FileInputStream(file).use { it.readBytes() }
                    val durationMs = ((data.size / 2L) * 1000L) / 48000L
                    val builtTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(48000)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(max(data.size, 9600))
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                    track = builtTrack
                    builtTrack.write(data, 0, data.size)
                    builtTrack.play()
                    Thread.sleep(durationMs + 150L)
                    onFinished("Playback finished.")
                } catch (e: Exception) {
                    onFinished("Playback error: ${e.javaClass.simpleName}")
                } finally {
                    try { track?.stop(); track?.release() } catch (_: Exception) {}
                }
            }, "SolMicTestPlay").start()
        }
    }
}
