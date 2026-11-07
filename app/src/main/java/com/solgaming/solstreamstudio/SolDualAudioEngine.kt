/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

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
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PerformanceHintManager
import android.os.Process
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class MicCalibrationAdvice(
    val measuredNoiseFloorDb: Int,
    val measuredSpeechRmsDb: Int,
    val measuredPeakDb: Int,
    val recommendedGainPercent: Int,
    val recommendedHpfHz: Int,
    val recommendedNoiseMode: String,
    val recommendedNoiseLevel: Int,
    val recommendedPreset: String,
    val recommendDeEsser: Boolean,
    val recommendSlowAgc: Boolean,
    val plainSummary: String
) {
    fun applyTo(base: StreamSettings): StreamSettings {
        val presetApplied = MicDspProcessor.applyPresetToSettings(base, recommendedPreset)
        return presetApplied.copy(
            micGainPercent = recommendedGainPercent,
            hpfCutoffHz = recommendedHpfHz,
            noiseSuppressMode = recommendedNoiseMode,
            rnnoiseLevel = recommendedNoiseLevel,
            deEsserEnabled = recommendDeEsser || presetApplied.deEsserEnabled,
            slowAgcEnabled = recommendSlowAgc || presetApplied.slowAgcEnabled
        )
    }
}

class SolDualAudioEngine(private val context: Context) {

    val sampleRate = 48000
    val micDsp = MicDspProcessor(sampleRate)

    @Volatile var isRunning = false
        private set

    @Volatile var gameStreamMuted = false
    @Volatile var gameGainLinear = 1.0f
    @Volatile var vadDuckingDepth = 0.0f

    // Stackable Stereo Game Soundstage switches (Bus B: Stages 11–12, stream-only)
    @Volatile var gameCinemaWideEnabled = false
    @Volatile var gameSubPunchEnabled = false
    @Volatile var gameFootstepFocusEnabled = false
    @Volatile var gameBlastTamerEnabled = false

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

    @Volatile var onFatalAudioError: ((String) -> Unit)? = null

    @Volatile private var activeSettings = StreamSettings()
    @Volatile private var targetMicDelaySamples = 0
    @Volatile private var pendingMicRecreate = false
    @Volatile private var pendingFxSync = false

    private var audioThread: Thread? = null
    private var callbackThread: HandlerThread? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null
    private var activeMicRecordRef: AudioRecord? = null
    private var hwNoiseSuppressor: NoiseSuppressor? = null
    private var hwEchoCanceler: AcousticEchoCanceler? = null
    private var streamOriginUs: Long = 0L

    private class StereoBiquad64 {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
        var a1 = 0.0; var a2 = 0.0
        private var z1L = 0.0; private var z2L = 0.0
        private var z1R = 0.0; private var z2R = 0.0

        fun configurePeaking(sampleRate: Int, freqHz: Double, q: Double, gainDb: Double) {
            val amp = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sampleRate.toDouble()
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha / amp
            b0 = (1.0 + alpha * amp) / a0
            b1 = (-2.0 * cosW) / a0
            b2 = (1.0 - alpha * amp) / a0
            a1 = (-2.0 * cosW) / a0
            a2 = (1.0 - alpha / amp) / a0
        }

        fun configureHighPass(sampleRate: Int, cutoffHz: Double) {
            val w0 = 2.0 * PI * cutoffHz / sampleRate.toDouble()
            val cosW = cos(w0)
            val alpha = sin(w0) / sqrt(2.0)
            val a0 = 1.0 + alpha
            b0 = ((1.0 + cosW) * 0.5) / a0
            b1 = -(1.0 + cosW) / a0
            b2 = ((1.0 + cosW) * 0.5) / a0
            a1 = (-2.0 * cosW) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun processLeft(x: Float): Float {
            val xd = x.toDouble()
            var y = b0 * xd + z1L
            if (!y.isFinite() || abs(y) < 1e-24) y = 0.0
            z1L = b1 * xd - a1 * y + z2L
            z2L = b2 * xd - a2 * y
            if (abs(z1L) < 1e-24) z1L = 0.0
            if (abs(z2L) < 1e-24) z2L = 0.0
            return y.toFloat()
        }

        fun processRight(x: Float): Float {
            val xd = x.toDouble()
            var y = b0 * xd + z1R
            if (!y.isFinite() || abs(y) < 1e-24) y = 0.0
            z1R = b1 * xd - a1 * y + z2R
            z2R = b2 * xd - a2 * y
            if (abs(z1R) < 1e-24) z1R = 0.0
            if (abs(z2R) < 1e-24) z2R = 0.0
            return y.toFloat()
        }
    }

    fun updateLiveSettings(s: StreamSettings) {
        val prev = activeSettings
        activeSettings = s
        micDsp.applySettings(s)
        gameGainLinear = (s.gameGainPercent.coerceIn(0, 250)) / 100.0f
        vadDuckingDepth = (s.vadDuckingPercent.coerceIn(0, 60)) / 100.0f
        gameCinemaWideEnabled = s.gameCinemaWideEnabled
        gameSubPunchEnabled = s.gameSubPunchEnabled
        gameFootstepFocusEnabled = s.gameFootstepFocusEnabled
        gameBlastTamerEnabled = s.gameBlastTamerEnabled
        targetMicDelaySamples = (s.micSyncDelayMs.coerceIn(0, 200) * sampleRate) / 1000

        if (isRunning) {
            if (s.micDeviceId != prev.micDeviceId || s.micAudioSource != prev.micAudioSource) {
                pendingMicRecreate = true
            } else if (s.noiseSuppressMode != prev.noiseSuppressMode ||
                s.rnnoiseLevel != prev.rnnoiseLevel ||
                s.speakerAecEnabled != prev.speakerAecEnabled
            ) {
                pendingFxSync = true
            }
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
        pendingFxSync = false
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

        syncPlatformAudioEffects(micRecord.audioSessionId, settings)

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

    /**
     * Signals the mixer thread to stop and waits up to 2.0s. If the thread is still finishing an
     * OS read, its own finally block exclusively releases native DSP & platform effects so no
     * handle race can ever occur.
     */
    fun stopStreamingAudio() {
        isRunning = false
        val t = audioThread
        if (t != null) {
            try {
                t.join(2000)
            } catch (_: Exception) {
            }
        }
        val threadStillAlive = (t?.isAlive == true)
        if (!threadStillAlive) {
            audioThread = null
            releasePlatformAudioEffects()
            micDsp.release()
        }
        unregisterMicMonitorCallback()
        activeMicRecordRef = null
        gameAudioActive = false
        gameAudioReceivingSignal = false
        lastGamePeakDb = -90.0f
        lastMixTruePeakDb = -90.0f
    }

    private fun syncPlatformAudioEffects(audioSessionId: Int, s: StreamSettings) {
        releasePlatformAudioEffects()
        if (audioSessionId <= 0) {
            micDsp.platformHwNsAttached = false
            micDsp.applySettings(s)
            return
        }

        val wantHwNs = s.noiseSuppressMode.equals("HW_NS", ignoreCase = true) && s.rnnoiseLevel > 0
        if (wantHwNs) {
            try {
                if (NoiseSuppressor.isAvailable()) {
                    val ns = NoiseSuppressor.create(audioSessionId)
                    if (ns != null) {
                        ns.enabled = true
                        hwNoiseSuppressor = ns
                    }
                }
            } catch (_: Throwable) {
            }
        }
        micDsp.platformHwNsAttached = (hwNoiseSuppressor?.enabled == true)
        micDsp.applySettings(s)

        if (s.speakerAecEnabled) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    val aec = AcousticEchoCanceler.create(audioSessionId)
                    if (aec != null) {
                        aec.enabled = true
                        hwEchoCanceler = aec
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun releasePlatformAudioEffects() {
        val ns = hwNoiseSuppressor
        hwNoiseSuppressor = null
        if (ns != null) {
            try { ns.enabled = false } catch (_: Throwable) {}
            try { ns.release() } catch (_: Throwable) {}
        }

        val aec = hwEchoCanceler
        hwEchoCanceler = null
        if (aec != null) {
            try { aec.enabled = false } catch (_: Throwable) {}
            try { aec.release() } catch (_: Throwable) {}
        }
        micDsp.platformHwNsAttached = false
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
        val clampedReq = requestedBps.coerceIn(64_000, 320_000)
        val candidates = listOf(clampedReq, 256_000, 192_000, 160_000, 128_000, 96_000, 64_000).distinct()

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
            settings.speakerAecEnabled -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
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

    private class GameStereoSyncRing {
        private val capFrames = 8192
        private val maskLong = (capFrames - 1).toLong()
        private val dataL = FloatArray(capFrames)
        private val dataR = FloatArray(capFrames)

        private var writeFrame: Long = 0L
        private var readFrame: Long = 0L
        private var readFrac: Double = 0.0

        private val targetFillFrames = 1440.0
        private var smoothedFill = targetFillFrames
        private var primed = false
        private var fadeInGain = 0.0f

        private var crossfadeFromL = 0.0f
        private var crossfadeFromR = 0.0f
        private var crossfadePos = 240
        private val crossfadeLen = 240

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

        val adpfSession: PerformanceHintManager.Session? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val phm = context.getSystemService(PerformanceHintManager::class.java)
                phm?.createHintSession(intArrayOf(Process.myTid()), 10_000_000L)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        val frameSamples = 480
        val maxOutSamples = frameSamples + 2
        val micFloat = FloatArray(frameSamples)
        val resampledMic = FloatArray(maxOutSamples)
        val delayedMicFloat = FloatArray(maxOutSamples)

        val gameStaging = FloatArray(1920 * 2)
        val gameBlockL = FloatArray(maxOutSamples)
        val gameBlockR = FloatArray(maxOutSamples)
        val gameRing = GameStereoSyncRing()

        val gameInfraHpf = StereoBiquad64().apply { configureHighPass(sampleRate, 28.0) }
        val gameSubPunchEq = StereoBiquad64().apply { configurePeaking(sampleRate, 65.0, 0.75, 1.8) }
        val gameFootMudTrim = StereoBiquad64().apply { configurePeaking(sampleRate, 260.0, 1.10, -1.4) }
        val gameFootPresence = StereoBiquad64().apply { configurePeaking(sampleRate, 2850.0, 1.05, 2.0) }
        var sideBassLpState = 0.0f
        var gameBlastEnv = 0.0f

        val outPcmBytes = ByteArray((maxOutSamples + 4) * 4)

        val maxDelaySamples = (sampleRate * 220) / 1000
        val delayRing = FloatArray(maxDelaySamples + maxOutSamples + 8)
        var delayWritePos = 0
        var activeDelaySamples = targetMicDelaySamples.coerceIn(0, (sampleRate * 200) / 1000)

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
                    pendingFxSync = false
                    val rebuilt = recreateMicRecordSafely(micRecord, onStatusChanged)
                    if (rebuilt != null) {
                        micRecord = rebuilt
                        micStartupFade = 0.0f
                        consecutiveMicErrors = 0
                    }
                } else if (pendingFxSync) {
                    pendingFxSync = false
                    val session = micRecord?.audioSessionId ?: 0
                    syncPlatformAudioEffects(session, activeSettings)
                }

                val curMic: AudioRecord? = micRecord
                val readMic = curMic?.read(micFloat, 0, frameSamples, AudioRecord.READ_BLOCKING)
                    ?: AudioRecord.ERROR_INVALID_OPERATION

                val dspWorkStartNs = System.nanoTime()

                val validMicSamples: Int
                if (readMic > 0) {
                    consecutiveMicErrors = 0
                    validMicSamples = readMic
                    if (!audioAnchorInitialized) {
                        audioStartOffsetUs = max(0L, (dspWorkStartNs / 1000L) - streamOriginUs)
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
                prevUnmutedMicSample = if (!currentlyMuted && validMicSamples > 0) {
                    micFloat[validMicSamples - 1]
                } else {
                    0f
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
                    if (rawGamePeakInBlock > 0.00006f) {
                        silentGameBlocks = 0
                        gameAudioReceivingSignal = true
                    } else {
                        silentGameBlocks++
                        if (silentGameBlocks > 300) {
                            gameAudioReceivingSignal = false
                        }
                    }
                } else {
                    gameBlockL.fill(0f, 0, outSamples)
                    gameBlockR.fill(0f, 0, outSamples)
                    gameAudioReceivingSignal = false
                }

                val targetDuck = if (vadDuckingDepth > 0f && micDsp.vadSpeechActive && !currentlyMuted) {
                    1.0f - vadDuckingDepth
                } else {
                    1.0f
                }
                val targetGameMute = if (gameStreamMuted) 0.0f else 1.0f
                val gLinear = gameGainLinear
                val useSubPunch = gameSubPunchEnabled
                val useFootstep = gameFootstepFocusEnabled
                val useCinemaWide = gameCinemaWideEnabled
                val useBlastTamer = gameBlastTamerEnabled
                val ceiling = 0.875f

                var blockGamePeak = 1e-5f
                var blockMixTruePeak = 1e-5f
                var byteIdx = 0

                for (i in 0 until outSamples) {
                    val duckSpeed = if (targetDuck < duckSmoothGain) 0.015f else 0.002f
                    duckSmoothGain += duckSpeed * (targetDuck - duckSmoothGain)
                    gameMuteSmooth += 0.008f * (targetGameMute - gameMuteSmooth)

                    var gL = gameBlockL[i] * gLinear
                    var gR = gameBlockR[i] * gLinear

                    if (useSubPunch) {
                        gL = gameSubPunchEq.processLeft(gameInfraHpf.processLeft(gL))
                        gR = gameSubPunchEq.processRight(gameInfraHpf.processRight(gR))
                    }

                    if (useFootstep) {
                        gL = gameFootPresence.processLeft(gameFootMudTrim.processLeft(gL))
                        gR = gameFootPresence.processRight(gameFootMudTrim.processRight(gR))
                    }

                    if (useCinemaWide) {
                        val mid = 0.5f * (gL + gR)
                        val side = 0.5f * (gL - gR)
                        sideBassLpState += 0.026f * (side - sideBassLpState)
                        val sideHigh = side - sideBassLpState
                        val wideSide = side + 0.26f * sideHigh
                        gL = mid + wideSide
                        gR = mid - wideSide
                    }

                    if (useBlastTamer) {
                        val gPeak = max(abs(gL), abs(gR))
                        gameBlastEnv += (if (gPeak > gameBlastEnv) 0.05f else 0.00025f) * (gPeak - gameBlastEnv)
                        val blastThresh = 0.335f
                        if (gameBlastEnv > blastThresh) {
                            val blastGain = (blastThresh / gameBlastEnv).pow(0.60f).coerceIn(0.42f, 1.0f)
                            gL *= blastGain
                            gR *= blastGain
                        }
                    }

                    gL *= duckSmoothGain * gameMuteSmooth
                    gR *= duckSmoothGain * gameMuteSmooth

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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && adpfSession != null) {
                    val workNs = (System.nanoTime() - dspWorkStartNs).coerceIn(50_000L, 9_500_000L)
                    try { adpfSession.reportActualWorkDuration(workNs) } catch (_: Throwable) {}
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
            audioThread = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && adpfSession != null) {
                try { adpfSession.close() } catch (_: Throwable) {}
            }
            releasePlatformAudioEffects()
            micDsp.release()
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
        releasePlatformAudioEffects()
        unregisterMicMonitorCallback()
        try { oldMic?.stop() } catch (_: Exception) {}
        try { oldMic?.release() } catch (_: Exception) {}

        val fresh = buildMicAudioRecord(activeSettings)
        if (fresh != null && fresh.state == AudioRecord.STATE_INITIALIZED) {
            return try {
                syncPlatformAudioEffects(fresh.audioSessionId, activeSettings)
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
        private val playbackStopRequested = AtomicBoolean(false)

        @Volatile var lastCalibrationAdvice: MicCalibrationAdvice? = null
            private set

        fun getTestFile(context: Context): File = File(context.cacheDir, TEST_PCM_FILE)

        fun stopMicTestPlayback() {
            playbackStopRequested.set(true)
        }

        fun deleteMicTestFile(context: Context): Boolean {
            stopMicTestPlayback()
            val f = getTestFile(context)
            return if (f.exists()) f.delete() else true
        }

        fun recordFiveSecondMicTest(
            context: Context,
            settings: StreamSettings,
            onProgress: (String) -> Unit,
            onComplete: (Boolean, String) -> Unit
        ) {
            recordGuidedTenSecondMicTest(context, settings, onProgress) { ok, summary, _ ->
                onComplete(ok, summary)
            }
        }

        @SuppressLint("MissingPermission")
        fun recordGuidedTenSecondMicTest(
            context: Context,
            settings: StreamSettings,
            onProgress: (String) -> Unit,
            onComplete: (Boolean, String, MicCalibrationAdvice?) -> Unit
        ) {
            stopMicTestPlayback()
            Thread({
                val sampleRate = 48000
                val dsp = MicDspProcessor(sampleRate).apply { applySettings(settings) }
                val engine = SolDualAudioEngine(context)
                val rec = engine.buildMicAudioRecord(settings)
                if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                    dsp.release()
                    onComplete(false, "Could not open selected mic. Check Mic permission or Bluetooth state.", null)
                    return@Thread
                }

                engine.syncPlatformAudioEffects(rec.audioSessionId, settings)

                val outFile = getTestFile(context)
                val frame = FloatArray(480)
                val pcmBytes = ByteArray(480 * 2)

                var quietEnergySum = 0.0
                var quietLowEnergySum = 0.0
                var quietFrames = 0
                var lowLpState = 0.0f

                var speechEnergySum = 0.0
                var speechHighSibSum = 0.0
                var speechActiveFrames = 0
                var sibHpPrev = 0.0f
                var rawSpeechPeak = 1e-5f
                var maxProcessedPeakDb = -90f
                var startupFade = 0f

                try {
                    FileOutputStream(outFile).use { fos ->
                        rec.startRecording()
                        val devName = engine.describeRoutedMic(rec.routedDevice)
                        val totalFrames = 1000

                        for (step in 0 until totalFrames) {
                            val r = rec.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                            if (r <= 0) continue

                            var rawSq = 0.0
                            var lowSq = 0.0
                            var highDiffSq = 0.0
                            for (i in 0 until r) {
                                val raw = if (frame[i].isFinite()) frame[i] else 0f
                                rawSq += (raw * raw).toDouble()
                                lowLpState += 0.015f * (raw - lowLpState)
                                lowSq += (lowLpState * lowLpState).toDouble()
                                val diff = raw - sibHpPrev
                                sibHpPrev = raw
                                highDiffSq += (diff * diff).toDouble()
                                if (step >= 300 && abs(raw) > rawSpeechPeak) {
                                    rawSpeechPeak = abs(raw)
                                }
                            }
                            val blockRawRms = sqrt(rawSq / r.toDouble()).toFloat()

                            if (step < 300) {
                                quietEnergySum += rawSq / r.toDouble()
                                quietLowEnergySum += lowSq / r.toDouble()
                                quietFrames++
                            } else {
                                val quietRef = if (quietFrames > 0) sqrt(quietEnergySum / quietFrames).toFloat() else 0.002f
                                if (blockRawRms > max(0.005f, quietRef * 2.2f)) {
                                    speechEnergySum += rawSq / r.toDouble()
                                    speechHighSibSum += highDiffSq / r.toDouble()
                                    speechActiveFrames++
                                }
                            }

                            dsp.processMonoFrameInPlace(frame, r)
                            if (dsp.lastPeakDb > maxProcessedPeakDb) maxProcessedPeakDb = dsp.lastPeakDb

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
                                if (step < 300) {
                                    val quietLeft = 3 - (step / 100)
                                    onProgress(
                                        "Phase 1/2 — Hold 2-hand CODM grip SILENTLY (${quietLeft}s)... Fan Floor: ${dsp.lastNoiseFloorDb.toInt()} dB"
                                    )
                                } else {
                                    val speakLeft = 10 - (step / 100)
                                    onProgress(
                                        "Phase 2/2 — Speak normal & loud CODM callouts (${speakLeft}s)... Peak: ${dsp.lastPeakDb.toInt()} dB"
                                    )
                                }
                            }
                        }

                        val rawQuietRms = if (quietFrames > 0) sqrt(quietEnergySum / quietFrames).toFloat() else 0.0015f
                        val rawQuietDb = MicDspProcessor.linearToDb(rawQuietRms).roundToInt()
                        val lowRumbleRatio = if (quietEnergySum > 1e-10) (quietLowEnergySum / quietEnergySum) else 0.25

                        val rawSpeechRms = if (speechActiveFrames >= 12) {
                            sqrt(speechEnergySum / speechActiveFrames).toFloat()
                        } else {
                            max(rawQuietRms * 3.0f, 0.035f)
                        }
                        val rawSpeechDb = MicDspProcessor.linearToDb(rawSpeechRms).roundToInt()
                        val peakDbInt = maxProcessedPeakDb.roundToInt()

                        val idealGainLin = (0.150f / max(rawSpeechRms, 0.015f))
                            .coerceAtMost(0.88f / max(rawSpeechPeak, 0.12f))
                            .coerceIn(0.75f, 2.20f)
                        val gainSteps = listOf(75, 100, 125, 150, 180, 220)
                        val rawPct = (idealGainLin * 100f).roundToInt()
                        val recGainPct = gainSteps.minByOrNull { abs(it - rawPct) } ?: 125

                        val recHpf = when {
                            lowRumbleRatio > 0.55 || rawQuietDb > -48 -> 100
                            settings.voiceGenderProfile.equals("MALE", ignoreCase = true) -> 80
                            settings.voiceGenderProfile.equals("FEMALE", ignoreCase = true) -> 100
                            else -> 85
                        }

                        val (recNsMode, recNsLvl) = when {
                            rawQuietDb >= -46 -> "RNNOISE" to 3
                            rawQuietDb >= -55 -> "RNNOISE" to 2
                            rawQuietDb >= -63 -> "RNNOISE" to 1
                            else -> "LIGHT" to 1
                        }

                        val sibRatio = if (speechEnergySum > 1e-9) (speechHighSibSum / speechEnergySum) else 0.08
                        val needDeEss = sibRatio > 0.18 || recGainPct >= 150
                        val needAgc = speechActiveFrames >= 12 && (MicDspProcessor.linearToDb(rawSpeechPeak) - rawSpeechDb) > 13f
                        val recPreset = if (rawQuietDb >= -55) "SOL_LUXE" else "STUDIO"

                        val nsLabel = if (recNsMode == "RNNOISE") "C RNNoise Lv$recNsLvl" else "Light Cut Lv1"
                        val spokeNote = if (speechActiveFrames < 8) " (Low speech detected—speak louder in Phase 2 to refine Gain)" else ""
                        val summaryText =
                            "10s Grip Test Saved ($devName)! Peak: ${peakDbInt}dB | Fan Floor: ${rawQuietDb}dB | Clips: ${dsp.clipCount}$spokeNote\n" +
                                "-> Advisor: Preset $recPreset • Gain ${recGainPct}% • HPF ${recHpf}Hz • $nsLabel" +
                                (if (needDeEss) " • De-Esser ON" else "")

                        val advice = MicCalibrationAdvice(
                            measuredNoiseFloorDb = rawQuietDb,
                            measuredSpeechRmsDb = rawSpeechDb,
                            measuredPeakDb = peakDbInt,
                            recommendedGainPercent = recGainPct,
                            recommendedHpfHz = recHpf,
                            recommendedNoiseMode = recNsMode,
                            recommendedNoiseLevel = recNsLvl,
                            recommendedPreset = recPreset,
                            recommendDeEsser = needDeEss,
                            recommendSlowAgc = needAgc,
                            plainSummary = summaryText
                        )
                        lastCalibrationAdvice = advice
                        onComplete(true, summaryText, advice)
                    }
                } catch (e: Exception) {
                    onComplete(false, "Mic calibration failed: ${e.javaClass.simpleName}", null)
                } finally {
                    engine.releasePlatformAudioEffects()
                    dsp.release()
                    try { rec.stop(); rec.release() } catch (_: Exception) {}
                }
            }, "SolMicCalibrator10s").start()
        }

        fun playRecordedMicTest(context: Context, onFinished: (String) -> Unit) {
            val file = getTestFile(context)
            if (!file.exists() || file.length() == 0L) {
                onFinished("No mic test recorded yet. Tap 'Record 10s Test' first.")
                return
            }
            playbackStopRequested.set(false)
            Thread({
                var track: AudioTrack? = null
                try {
                    val minOutBuf = AudioTrack.getMinBufferSize(
                        48000,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(9600)

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
                        .setBufferSizeInBytes(minOutBuf * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    track = builtTrack
                    builtTrack.play()

                    val chunk = ByteArray(4800)
                    FileInputStream(file).use { fis ->
                        while (!playbackStopRequested.get()) {
                            val readBytes = fis.read(chunk)
                            if (readBytes <= 0) break
                            builtTrack.write(chunk, 0, readBytes, AudioTrack.WRITE_BLOCKING)
                        }
                    }
                    if (!playbackStopRequested.get()) {
                        Thread.sleep(120L)
                        onFinished("10s calibration playback finished.")
                    } else {
                        onFinished("Playback stopped.")
                    }
                } catch (e: Exception) {
                    onFinished("Playback error: ${e.javaClass.simpleName}")
                } finally {
                    try { track?.stop(); track?.release() } catch (_: Exception) {}
                }
            }, "SolMicTestPlay").start()
        }
    }
}
