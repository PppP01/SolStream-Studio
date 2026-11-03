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
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SolDualAudioEngine(private val context: Context) {

    val sampleRate = 48000
    val micDsp = MicDspProcessor(sampleRate)

    @Volatile var isRunning = false
        private set

    @Volatile var gameStreamMuted = false
    @Volatile var gameGainLinear = 1.0f
    @Volatile var vadDuckingDepth = 0.0f // 0.0f = Off, 0.20f = -20%, 0.35f = -35%
    @Volatile var micStatusText = "MIC READY"
    @Volatile var gameAudioActive = false

    private var audioThread: Thread? = null
    private var callbackThread: HandlerThread? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null
    private var streamOriginUs: Long = 0L

    fun updateLiveSettings(s: StreamSettings) {
        micDsp.applySettings(s)
        gameGainLinear = (s.gameGainPercent.coerceIn(0, 125)) / 100.0f
        vadDuckingDepth = (s.vadDuckingPercent.coerceIn(0, 60)) / 100.0f
    }

    fun startStreamingAudio(
        settings: StreamSettings,
        mediaProjection: MediaProjection?,
        originTimeUs: Long,
        onStatusChanged: ((String) -> Unit)?,
        onEncodedAac: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ): Boolean {
        if (isRunning) return true
        updateLiveSettings(settings)
        streamOriginUs = originTimeUs

        val aacCodec = createHardwareAacEncoder(settings.audioBitrateKbps * 1000)
        if (aacCodec == null) {
            DiagnosticsLogger.log(
                "AUDIO_ERR",
                "Failed to start AAC hardware audio encoder.",
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
            DiagnosticsLogger.log(
                "GAME_AUDIO",
                "Internal game playback capture unavailable; streaming microphone only.",
                "mediaProjection=${mediaProjection != null}"
            )
        }

        registerMicMonitorCallback(micRecord, onStatusChanged)
        isRunning = true

        audioThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runDualMixerLoop(
                settings = settings,
                micRecord = micRecord,
                gameRecord = if (gameAudioActive) gameRecord else null,
                aacCodec = aacCodec,
                onStatusChanged = onStatusChanged,
                onEncodedAac = onEncodedAac
            )
        }, "SolStudioAudioMixer").apply { start() }

        return true
    }

    fun stopStreamingAudio() {
        isRunning = false
        try {
            audioThread?.join(1200)
        } catch (_: Exception) {
        }
        audioThread = null
        unregisterMicMonitorCallback()
    }

    private fun registerMicMonitorCallback(
        micRecord: AudioRecord,
        onStatusChanged: ((String) -> Unit)?
    ) {
        try {
            val ht = HandlerThread("SolMicCallback").apply { start() }
            callbackThread = ht
            val cb = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                    val mySession = micRecord.audioSessionId
                    val match = configs.firstOrNull { it.clientAudioSessionId == mySession } ?: return
                    val silenced = match.isClientSilenced
                    val devName = match.audioDevice?.productName?.toString() ?: "Built-in"
                    val newStatus = if (silenced) "MIC SILENCED" else "MIC OK ($devName)"
                    if (newStatus != micStatusText) {
                        micStatusText = newStatus
                        DiagnosticsLogger.micStatusLabel = newStatus
                        if (silenced) {
                            DiagnosticsLogger.log(
                                "MIC_WARN",
                                "Android temporarily silenced stream mic capture (check Accessibility overlay visibility).",
                                "session=$mySession dev=$devName"
                            )
                        } else {
                            DiagnosticsLogger.log(
                                "MIC_OK",
                                "Stream microphone active on $devName.",
                                "session=$mySession"
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
        recordingCallback = null
        try {
            callbackThread?.quitSafely()
        } catch (_: Exception) {
        }
        callbackThread = null
    }

    private fun createHardwareAacEncoder(requestedBps: Int): MediaCodec? {
        val candidates = listOf(requestedBps, 192_000, 160_000, 128_000).distinct()
        for (bps in candidates) {
            var codec: MediaCodec? = null
            try {
                val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, bps)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                return codec
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

        // Apply Glip sharing rule when Auto source (0) is chosen:
        // DEFAULT (0) for Built-in Phone Mic; VOICE_RECOGNITION (6) if Bluetooth/Headset selected
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
                .setBufferSizeInBytes(max(minBuf * 2, 480 * 4 * 8))
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
                .setBufferSizeInBytes(max(minBuf * 2, 480 * 8 * 8))
                .build()
        } catch (_: Exception) {
            null
        }
    }

    private fun runDualMixerLoop(
        settings: StreamSettings,
        micRecord: AudioRecord,
        gameRecord: AudioRecord?,
        aacCodec: MediaCodec,
        onStatusChanged: ((String) -> Unit)?,
        onEncodedAac: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val frameSamples = 480 // Exact 10 ms frame @ 48,000 Hz
        val micFloat = FloatArray(frameSamples)
        val delayedMicFloat = FloatArray(frameSamples)
        val gameStereoFloat = FloatArray(frameSamples * 2)
        val outPcmBytes = ByteArray(frameSamples * 4) // 16-bit Stereo Little-Endian

        // Pre-allocated ring buffer for 0..200 ms Mic Lip-Sync Delay (max 9,600 samples)
        val maxDelaySamples = (sampleRate * 200) / 1000
        val delayRing = FloatArray(maxDelaySamples + frameSamples)
        var delayWritePos = 0
        val delaySamples = ((settings.micSyncDelayMs.coerceIn(0, 200) * sampleRate) / 1000)

        // Pre-allocated output packet ring so MediaCodec buffers release immediately without GC
        val packetPool = Array(32) { ByteBuffer.allocateDirect(2048) }
        var poolIndex = 0
        val outBufferInfo = MediaCodec.BufferInfo()

        val micTs = AudioTimestamp()
        val gameTs = AudioTimestamp()
        var lastDriftCheckMs = 0L

        var duckSmoothGain = 1.0f
        var gameMuteSmooth = 1.0f
        var limiterGain = 1.0f

        try {
            micRecord.startRecording()
            gameRecord?.startRecording()
            micStatusText = "MIC OK"
            DiagnosticsLogger.micStatusLabel = "MIC OK"
            onStatusChanged?.invoke("MIC OK")

            while (isRunning) {
                val readMic = micRecord.read(micFloat, 0, frameSamples, AudioRecord.READ_BLOCKING)
                if (readMic <= 0) continue

                val readGame = gameRecord?.read(
                    gameStereoFloat,
                    0,
                    readMic * 2,
                    AudioRecord.READ_NON_BLOCKING
                ) ?: 0

                // 1. Process Microphone ONLY in 32-bit float before mixing
                micDsp.processMonoFrameInPlace(micFloat, readMic)

                // 2. Apply optional pre-allocated lip-sync delay
                val micSourceArray: FloatArray = if (delaySamples <= 0) {
                    micFloat
                } else {
                    val ringLen = delayRing.size
                    for (i in 0 until readMic) {
                        delayRing[delayWritePos] = micFloat[i]
                        val readPos = (delayWritePos - delaySamples + ringLen) % ringLen
                        delayedMicFloat[i] = delayRing[readPos]
                        delayWritePos = (delayWritePos + 1) % ringLen
                    }
                    delayedMicFloat
                }

                // 3. Smooth VAD Auto-Ducking + Game Stream Mute + Master True-Peak Limiter (-1.0 dBFS)
                val targetDuck = if (vadDuckingDepth > 0f && micDsp.vadSpeechActive && !micDsp.streamMicMuted) {
                    1.0f - vadDuckingDepth
                } else {
                    1.0f
                }
                val targetGameMute = if (gameStreamMuted) 0.0f else 1.0f
                val gLinear = gameGainLinear
                val ceiling = 0.891f // -1.0 dBFS ceiling

                var byteIdx = 0
                for (i in 0 until readMic) {
                    val duckSpeed = if (targetDuck < duckSmoothGain) 0.015f else 0.002f
                    duckSmoothGain += duckSpeed * (targetDuck - duckSmoothGain)
                    gameMuteSmooth += 0.015f * (targetGameMute - gameMuteSmooth)

                    val gIdx = i * 2
                    val gL = if (gIdx + 1 < readGame) gameStereoFloat[gIdx] * gLinear * duckSmoothGain * gameMuteSmooth else 0f
                    val gR = if (gIdx + 1 < readGame) gameStereoFloat[gIdx + 1] * gLinear * duckSmoothGain * gameMuteSmooth else 0f
                    val mCenter = micSourceArray[i]

                    var mixL = gL + mCenter
                    var mixR = gR + mCenter

                    // Fast transparent master limiter
                    val peak = max(abs(mixL), abs(mixR))
                    val neededGain = if (peak > ceiling) ceiling / peak else 1.0f
                    val limCoeff = if (neededGain < limiterGain) 0.35f else 0.004f
                    limiterGain += limCoeff * (neededGain - limiterGain)

                    mixL = (mixL * limiterGain).coerceIn(-1.0f, 1.0f)
                    mixR = (mixR * limiterGain).coerceIn(-1.0f, 1.0f)

                    val sL = (mixL * 32767.0f).toInt().coerceIn(-32768, 32767)
                    val sR = (mixR * 32767.0f).toInt().coerceIn(-32768, 32767)

                    outPcmBytes[byteIdx++] = (sL and 0xFF).toByte()
                    outPcmBytes[byteIdx++] = ((sL shr 8) and 0xFF).toByte()
                    outPcmBytes[byteIdx++] = (sR and 0xFF).toByte()
                    outPcmBytes[byteIdx++] = ((sR shr 8) and 0xFF).toByte()
                }

                // 4. Periodic Mic-vs-Game hardware timestamp drift check (every 2s)
                val nowMs = System.currentTimeMillis()
                if (gameRecord != null && nowMs - lastDriftCheckMs > 2000L) {
                    lastDriftCheckMs = nowMs
                    if (micRecord.getTimestamp(micTs, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS &&
                        gameRecord.getTimestamp(gameTs, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS
                    ) {
                        DiagnosticsLogger.avDriftMs = (micTs.nanoTime - gameTs.nanoTime) / 1_000_000L
                    }
                }

                // 5. Feed monotonic-timestamped PCM into Hardware AAC Encoder
                val ptsUs = max(0L, (System.nanoTime() / 1000L) - streamOriginUs)
                val inIdx = aacCodec.dequeueInputBuffer(5000L)
                if (inIdx >= 0) {
                    val inBuf = aacCodec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        inBuf.clear()
                        val bytesToWrite = min(byteIdx, inBuf.remaining())
                        inBuf.put(outPcmBytes, 0, bytesToWrite)
                        aacCodec.queueInputBuffer(inIdx, 0, bytesToWrite, ptsUs, 0)
                    }
                }

                // 6. Drain encoded AAC frames & forward to RTMPS sender
                while (true) {
                    val outIdx = aacCodec.dequeueOutputBuffer(outBufferInfo, 0L)
                    if (outIdx < 0) break
                    val outBuf = aacCodec.getOutputBuffer(outIdx)
                    val isConfig = (outBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (outBuf != null && outBufferInfo.size > 0 && !isConfig) {
                        val copyBuf = packetPool[poolIndex]
                        poolIndex = (poolIndex + 1) % packetPool.size
                        copyBuf.clear()
                        outBuf.position(outBufferInfo.offset)
                        outBuf.limit(outBufferInfo.offset + outBufferInfo.size)
                        val copySize = min(outBufferInfo.size, copyBuf.remaining())
                        val prevLimit = outBuf.limit()
                        outBuf.limit(outBufferInfo.offset + copySize)
                        copyBuf.put(outBuf)
                        outBuf.limit(prevLimit)
                        copyBuf.flip()

                        val infoCopy = MediaCodec.BufferInfo().apply {
                            set(0, copySize, outBufferInfo.presentationTimeUs, outBufferInfo.flags)
                        }
                        onEncodedAac(copyBuf, infoCopy)
                    }
                    aacCodec.releaseOutputBuffer(outIdx, false)
                }
            }
        } catch (e: Exception) {
            DiagnosticsLogger.log(
                "AUDIO_RECOVER",
                "Audio mixer encountered an interruption (${e.javaClass.simpleName}).",
                e.message ?: ""
            )
        } finally {
            try { micRecord.stop() } catch (_: Exception) {}
            try { micRecord.release() } catch (_: Exception) {}
            try { gameRecord?.stop() } catch (_: Exception) {}
            try { gameRecord?.release() } catch (_: Exception) {}
            try { aacCodec.stop() } catch (_: Exception) {}
            try { aacCodec.release() } catch (_: Exception) {}
        }
    }

    // === PRE-STREAM 5-SECOND MIC TEST STUDIO (MICROPHONE-ONLY, NO MEDIAPROJECTION) ===
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

                try {
                    FileOutputStream(outFile).use { fos ->
                        rec.startRecording()
                        val totalFrames = 500 // 500 * 10 ms = 5.0 seconds
                        for (step in 0 until totalFrames) {
                            val r = rec.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                            if (r <= 0) continue
                            dsp.processMonoFrameInPlace(frame, r)
                            if (dsp.lastPeakDb > maxPeakDb) maxPeakDb = dsp.lastPeakDb

                            var idx = 0
                            for (i in 0 until r) {
                                val v = (frame[i].coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32768, 32767)
                                pcmBytes[idx++] = (v and 0xFF).toByte()
                                pcmBytes[idx++] = ((v shr 8) and 0xFF).toByte()
                            }
                            fos.write(pcmBytes, 0, idx)
                            if (step % 50 == 0) {
                                val secLeft = 5 - (step / 100)
                                onProgress("Recording CODM grip sample (${secLeft}s)... Peak: ${dsp.lastPeakDb.toInt()} dB")
                            }
                        }
                    }
                    onComplete(
                        true,
                        "5s test saved! Peak: ${maxPeakDb.toInt()} dB | Noise Floor: ${dsp.lastNoiseFloorDb.toInt()} dB | Clips: ${dsp.clipCount}"
                    )
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
                    track = AudioTrack.Builder()
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
                    track.write(data, 0, data.size)
                    track.play()
                    Thread.sleep(5150L)
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
