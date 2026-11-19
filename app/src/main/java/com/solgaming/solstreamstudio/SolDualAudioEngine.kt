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
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjection
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PerformanceHintManager
import android.os.Process
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
        val withPreset = MicDspProcessor.applyPresetToSettings(base, recommendedPreset)
        val defaultProfileHpf = KeystoreVault.resolveDefaultHpfForProfile(withPreset.voiceGenderProfile)
        return withPreset.copy(
            micGainPercent = recommendedGainPercent,
            hpfCutoffHz = recommendedHpfHz,
            hpfAutoMode = (recommendedHpfHz == defaultProfileHpf),
            noiseSuppressMode = recommendedNoiseMode,
            rnnoiseLevel = recommendedNoiseLevel,
            deEsserEnabled = recommendDeEsser,
            slowAgcEnabled = recommendSlowAgc
        )
    }
}

/**
 * Per-session exact-launched worker completion barrier.
 * Ensures shared AudioRecord / MicDspProcessor / AAC MediaCodec resources are released
 * strictly ONCE and ONLY after ALL launched audio workers (SolFloatAudioMixer + SolAacDrain)
 * have fully exited their finally blocks.
 */
internal class AudioWorkerBarrier {
    private var activeWorkers = 0
    private var releaseRequested = false
    private var released = false

    @Synchronized
    fun registerWorker() {
        activeWorkers++
    }

    /**
     * Called inside each launched worker's finally block.
     * Returns true ONLY for the final worker to exit after [requestRelease] has been invoked.
     */
    @Synchronized
    fun onWorkerExited(): Boolean {
        if (activeWorkers > 0) activeWorkers--
        if (activeWorkers == 0 && releaseRequested && !released) {
            released = true
            return true
        }
        return false
    }

    /**
     * Called by [SolDualAudioEngine.stopStreamingAudio] after joining workers.
     * Returns true immediately if all workers have already exited; otherwise defers
     * release cleanly to the last remaining worker's [onWorkerExited].
     */
    @Synchronized
    fun requestRelease(): Boolean {
        releaseRequested = true
        if (activeWorkers == 0 && !released) {
            released = true
            return true
        }
        return false
    }

    @Synchronized
    fun activeWorkerCount(): Int = activeWorkers

    @Synchronized
    fun isReleased(): Boolean = released
}

/**
 * Zero-allocation, phase-continuous linear resampler for strict OEM microphone HALs
 * that reject 48 kHz capture and only expose 44.1 kHz or 16 kHz PCM_16BIT.
 * Guarantees exact 480-sample / 48 kHz mono float output for MicDspProcessor & Xiph RNNoise.
 */
internal class LinearMonoResampler48k(val sourceRateHz: Int) {
    val sourceSamplesPer10ms: Int = max(1, (sourceRateHz * 10) / 1000)
    private var prevTailSample: Float = 0.0f
    private var initialized: Boolean = false

    fun reset() {
        prevTailSample = 0.0f
        initialized = false
    }

    fun resample10msPcm16To480Float(srcPcm16: ShortArray, validSamples: Int, dest480: FloatArray) {
        if (validSamples <= 0) {
            dest480.fill(0f)
            return
        }
        val n = sourceSamplesPer10ms.coerceAtMost(srcPcm16.size)
        if (validSamples < n) {
            srcPcm16.fill(0, validSamples, n)
        }
        val invScale = 1.0f / 32768.0f
        if (!initialized) {
            prevTailSample = srcPcm16[0] * invScale
            initialized = true
        }
        val step = n.toDouble() / SolDualAudioEngine.MONO_SAMPLES_PER_10MS.toDouble()
        for (outIdx in 0 until SolDualAudioEngine.MONO_SAMPLES_PER_10MS) {
            val pos = outIdx * step
            val idx = pos.toInt().coerceIn(0, n - 1)
            val frac = (pos - idx).toFloat()
            val s0 = if (idx == 0) prevTailSample else (srcPcm16[idx - 1] * invScale)
            val s1 = srcPcm16[idx] * invScale
            dest480[outIdx] = s0 + frac * (s1 - s0)
        }
        prevTailSample = srcPcm16[n - 1] * invScale
    }
}

class SolDualAudioEngine(private val context: Context) {

    @Volatile var isRunning = false
        private set

    // Live-adjustable mixer & game soundstage states (Up to 250% Game & Mic Boost)
    @Volatile var gameGainLinear = 1.0f
    @Volatile var gameStreamMuted = false
    @Volatile var vadDuckingAmount = 0.0f
    @Volatile var micSyncDelayMs = 0
    @Volatile var streamSceneIsLive = true

    // Stackable Stereo Game Soundstage states (Stream-Only — never alters user earphones)
    @Volatile var gameCinemaWideEnabled = false
    @Volatile var gameSubPunchEnabled = false
    @Volatile var gameFootstepFocusEnabled = false
    @Volatile var gameBlastTamerEnabled = false

    @Volatile var gameAudioActive = false
        private set
    @Volatile var gameAudioReceivingSignal = false
        private set
    @Volatile var lastGamePeakDb: Float = -90f
        private set

    @Volatile var micStatusText = "MIC IDLE"
        private set
    @Volatile var routedMicLabel = "Auto (Phone Studio)"
        private set
    @Volatile var shortMicPillLabel = "Phone Studio"
        private set
    @Volatile var activeAcousticModeLabel = HardwareAcousticMode.PHONE_STUDIO.title
        private set

    @Volatile var onFatalAudioError: ((String) -> Unit)? = null

    val micDsp = MicDspProcessor(SAMPLE_RATE)

    private var activeMicDeviceId: Int = -1
    private var activeMicAudioSource: Int = 0
    private var activeHwNsEnabled: Boolean = false
    private var activeSpeakerAecEnabled: Boolean = false
    @Volatile private var effectiveSpeakerAecActive: Boolean = false
    @Volatile private var pendingMicRouteRefresh = false

    private val cleanupLock = Any()
    @Volatile private var activeWorkerBarrier: AudioWorkerBarrier? = null

    private var micSession: OpenedMicSession? = null
    private var gameRecord: AudioRecord? = null
    private var gameRecordIsPcm16: Boolean = false
    private var aacCodec: MediaCodec? = null
    private var mixerThread: Thread? = null
    private var drainThread: Thread? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    // State deduplication for [MIC_OK] logging (zero 60-second reopen loops on 16 kHz HAL match locks)
    private var lastLoggedMicSessionId: Int = -1
    private var lastLoggedMicRoute: String = ""
    private var lastLoggedMicSilenced: Boolean? = null
    private var didClearPreferredDeviceForHalRate: Boolean = false

    private var tpdfSeed: Int = 0x6D2B79F5

    // 64-bit Stereo Biquad for Game Sub-Punch (62 Hz) & Dual-Band Footstep Focus (250 Hz + 2,450 Hz)
    private class StereoBiquad64 {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
        var a1 = 0.0; var a2 = 0.0
        var z1L = 0.0; var z2L = 0.0
        var z1R = 0.0; var z2R = 0.0

        fun configurePeaking(freqHz: Double, q: Double, gainDb: Double) {
            val amp = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / SAMPLE_RATE.toDouble()
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha / amp
            if (a0 <= 1e-9) return
            b0 = (1.0 + alpha * amp) / a0
            b1 = (-2.0 * cosW) / a0
            b2 = (1.0 - alpha * amp) / a0
            a1 = (-2.0 * cosW) / a0
            a2 = (1.0 - alpha / amp) / a0
        }

        fun configureBandPass(freqHz: Double, q: Double) {
            val w0 = 2.0 * PI * freqHz / SAMPLE_RATE.toDouble()
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            if (a0 <= 1e-9) return
            b0 = alpha / a0
            b1 = 0.0
            b2 = -alpha / a0
            a1 = (-2.0 * cosW) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun processL(x: Float): Float {
            val xd = x.toDouble()
            var y = b0 * xd + z1L
            if (!y.isFinite() || abs(y) < 1e-24) y = 0.0
            z1L = b1 * xd - a1 * y + z2L
            z2L = b2 * xd - a2 * y
            if (!z1L.isFinite() || abs(z1L) < 1e-24) z1L = 0.0
            if (!z2L.isFinite() || abs(z2L) < 1e-24) z2L = 0.0
            return y.toFloat()
        }

        fun processR(x: Float): Float {
            val xd = x.toDouble()
            var y = b0 * xd + z1R
            if (!y.isFinite() || abs(y) < 1e-24) y = 0.0
            z1R = b1 * xd - a1 * y + z2R
            z2R = b2 * xd - a2 * y
            if (!z1R.isFinite() || abs(z1R) < 1e-24) z1R = 0.0
            if (!z2R.isFinite() || abs(z2R) < 1e-24) z2R = 0.0
            return y.toFloat()
        }

        fun reset() {
            z1L = 0.0; z2L = 0.0
            z1R = 0.0; z2R = 0.0
        }
    }

    // Phase-safe 62 Hz Sub-Bass Band-Pass (+0.42f [+3.0 dB] when silent, ducks to 0.0f when speaking)
    private val gameSubBandExtractor = StereoBiquad64().apply { configureBandPass(62.0, 0.75) }
    // Dual-Band Footstep Focus: 250 Hz indoor boot thump (+1.6 dB) + 2,450 Hz tactical crunch + Sonar Lift
    private val gameFootstepLowThumpFilter = StereoBiquad64().apply { configurePeaking(250.0, 1.35, 1.6) }
    private val gameFootstepBandExtractor = StereoBiquad64().apply { configureBandPass(2450.0, 1.05) }

    private var sideLowPassState = 0.0f
    private var rawGameBlastEnv = 0.0f
    private var gameBlastGain = 1.0f
    private var subPunchVoiceGainSmooth = 0.0f
    private var sonarFootstepExtraLiftSmooth = 0.0f

    // 64-sample Look-Ahead 4x Cubic Inter-Sample True-Peak Limiter state (-1.16 dBTP ceiling)
    private val lookAheadFrames = 64
    private val lookAheadBufL = FloatArray(lookAheadFrames)
    private val lookAheadBufR = FloatArray(lookAheadFrames)
    private var lookAheadIdx = 0
    private var prevSampleL0 = 0f; private var prevSampleL1 = 0f; private var prevSampleL2 = 0f
    private var prevSampleR0 = 0f; private var prevSampleR1 = 0f; private var prevSampleR2 = 0f
    private var limiterEnvGain = 1.0f

    fun updateLiveSettings(s: StreamSettings) {
        val routedOverride = micSession?.record?.routedDevice
        val detectedMode = detectHardwareAcousticMode(context, s.micDeviceId, s.speakerAecEnabled, routedOverride)
        val newEffectiveAec = computeEffectiveSpeakerAec(context, s.speakerAecEnabled, s.micDeviceId, routedOverride)

        micDsp.applyHardwareAcousticMode(detectedMode)
        activeAcousticModeLabel = detectedMode.title
        micDsp.applySettings(s)

        gameGainLinear = (s.gameGainPercent.coerceIn(0, 250)) / 100.0f
        vadDuckingAmount = (s.vadDuckingPercent.coerceIn(0, 80)) / 100.0f
        micSyncDelayMs = s.micSyncDelayMs.coerceIn(0, 200)

        gameCinemaWideEnabled = s.gameCinemaWideEnabled
        gameSubPunchEnabled = s.gameSubPunchEnabled
        gameFootstepFocusEnabled = s.gameFootstepFocusEnabled
        gameBlastTamerEnabled = s.gameBlastTamerEnabled

        val wantHwNs = s.noiseSuppressMode.equals("HW_NS", ignoreCase = true) && s.rnnoiseLevel > 0
        if (s.micDeviceId != activeMicDeviceId ||
            s.micAudioSource != activeMicAudioSource ||
            wantHwNs != activeHwNsEnabled ||
            s.speakerAecEnabled != activeSpeakerAecEnabled ||
            newEffectiveAec != effectiveSpeakerAecActive
        ) {
            activeMicDeviceId = s.micDeviceId
            activeMicAudioSource = s.micAudioSource
            activeHwNsEnabled = wantHwNs
            activeSpeakerAecEnabled = s.speakerAecEnabled
            effectiveSpeakerAecActive = newEffectiveAec
            pendingMicRouteRefresh = true
        }
    }

    fun startStreamingAudio(
        settings: StreamSettings,
        mediaProjection: MediaProjection?,
        originTimeUs: Long,
        onStatusChanged: (() -> Unit)? = null,
        onEncodedAac: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ): Boolean {
        if (isRunning) return false

        lastLoggedMicSessionId = -1
        lastLoggedMicRoute = ""
        lastLoggedMicSilenced = null
        didClearPreferredDeviceForHalRate = false

        activeMicDeviceId = settings.micDeviceId
        activeMicAudioSource = settings.micAudioSource
        activeHwNsEnabled = settings.noiseSuppressMode.equals("HW_NS", ignoreCase = true) && settings.rnnoiseLevel > 0
        activeSpeakerAecEnabled = settings.speakerAecEnabled
        effectiveSpeakerAecActive = computeEffectiveSpeakerAec(context, activeSpeakerAecEnabled, activeMicDeviceId)
        updateLiveSettings(settings)
        pendingMicRouteRefresh = false
        gameAudioReceivingSignal = false
        lastGamePeakDb = -90f

        val openedMic = buildMicAudioRecord(
            context = context,
            requestedSource = activeMicAudioSource,
            preferredDeviceId = activeMicDeviceId,
            enableHwNs = activeHwNsEnabled,
            effectiveSpeakerAec = effectiveSpeakerAecActive
        )
        if (openedMic == null) {
            micStatusText = "MIC ERROR"
            DiagnosticsLogger.micStatusLabel = micStatusText
            DiagnosticsLogger.log("AUDIO_ERR", "Could not initialize microphone AudioRecord (tried 48k float, 48k/44.1k/16k PCM16).")
            return false
        }
        micSession = openedMic
        effectiveSpeakerAecActive = computeEffectiveSpeakerAec(
            context,
            activeSpeakerAecEnabled,
            activeMicDeviceId,
            openedMic.record.routedDevice
        )
        micDsp.platformHwNsAttached = (openedMic.hwNoiseSuppressor != null)
        val initialHwMode = detectHardwareAcousticMode(
            context,
            activeMicDeviceId,
            activeSpeakerAecEnabled,
            openedMic.record.routedDevice
        )
        micDsp.applyHardwareAcousticMode(initialHwMode)
        activeAcousticModeLabel = initialHwMode.title
        micDsp.applySettings(settings)

        if (mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val builtGame = buildGameAudioRecord(mediaProjection)
            gameRecord = builtGame?.first
            gameRecordIsPcm16 = builtGame?.second ?: false
            gameAudioActive = (gameRecord != null)
        } else {
            gameRecord = null
            gameRecordIsPcm16 = false
            gameAudioActive = false
        }

        val targetAacBps = (settings.audioBitrateKbps.coerceIn(64, 320)) * 1000
        val configuredAac = createAndConfigureAacEncoder(targetAacBps)
        if (configuredAac == null) {
            releaseMicSafely()
            releaseGameSafely()
            return false
        }
        aacCodec = configuredAac

        val sessionBarrier = AudioWorkerBarrier()
        activeWorkerBarrier = sessionBarrier
        isRunning = true
        registerDynamicAudioRouteWatcher()

        sessionBarrier.registerWorker()
        drainThread = Thread({
            runAacDrainLoop(configuredAac, sessionBarrier, onEncodedAac)
        }, "SolAacDrain").apply { start() }

        sessionBarrier.registerWorker()
        mixerThread = Thread({
            runRealtimeFloatMixLoop(
                codec = configuredAac,
                mediaProjection = mediaProjection,
                originTimeUs = originTimeUs,
                sessionBarrier = sessionBarrier,
                onStatusChanged = onStatusChanged
            )
        }, "SolFloatAudioMixer").apply { start() }

        return true
    }

    /**
     * Watches for hot-plugged Wired/USB-C/Bluetooth headsets AND output headphones so both
     * HardwareAcousticMode AND effectiveSpeakerAec refresh immediately without leaving hardware
     * AEC active on headphones.
     */
    private fun registerDynamicAudioRouteWatcher() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cb = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    if (!isRunning || addedDevices.isNullOrEmpty()) return
                    val hasExternalMic = addedDevices.any {
                        it.isSource && (it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_DEVICE)
                    }
                    val routedDev = micSession?.record?.routedDevice
                    val newMode = detectHardwareAcousticMode(context, activeMicDeviceId, activeSpeakerAecEnabled, routedDev)
                    val newEffectiveAec = computeEffectiveSpeakerAec(context, activeSpeakerAecEnabled, activeMicDeviceId, routedDev)
                    micDsp.applyHardwareAcousticMode(newMode)
                    activeAcousticModeLabel = newMode.title
                    if ((hasExternalMic && activeMicDeviceId == -1) || newEffectiveAec != effectiveSpeakerAecActive) {
                        effectiveSpeakerAecActive = newEffectiveAec
                        pendingMicRouteRefresh = true
                    }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    if (!isRunning || removedDevices.isNullOrEmpty()) return
                    val routedDev = micSession?.record?.routedDevice
                    val newMode = detectHardwareAcousticMode(context, activeMicDeviceId, activeSpeakerAecEnabled, routedDev)
                    val newEffectiveAec = computeEffectiveSpeakerAec(context, activeSpeakerAecEnabled, activeMicDeviceId, routedDev)
                    micDsp.applyHardwareAcousticMode(newMode)
                    activeAcousticModeLabel = newMode.title
                    val removedSource = removedDevices.any { it.isSource }
                    if (removedSource || newEffectiveAec != effectiveSpeakerAecActive) {
                        effectiveSpeakerAecActive = newEffectiveAec
                        pendingMicRouteRefresh = true
                    }
                }
            }
            audioDeviceCallback = cb
            am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
        }
    }

    private fun unregisterDynamicAudioRouteWatcher() {
        val cb = audioDeviceCallback ?: return
        audioDeviceCallback = null
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.unregisterAudioDeviceCallback(cb)
        } catch (_: Exception) {
        }
    }

    /**
     * Universal 2-Pass AAC-LC Encoder Configurator:
     * Attempt 1 configures with explicit KEY_MAX_INPUT_SIZE = 32768 and requested bitrate;
     * Attempt 2 falls back to a bare-metal AAC-LC format (without KEY_MAX_INPUT_SIZE and clamped
     * to <= 192 kbps) so older Android 10/11 MediaTek/Unisoc audio codecs never fail initialization.
     */
    private fun createAndConfigureAacEncoder(bitrateBps: Int): MediaCodec? {
        val hwAacName = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
                info.isEncoder &&
                    info.isHardwareAccelerated &&
                    !info.isSoftwareOnly &&
                    info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) }
            }?.name
        } catch (_: Exception) {
            null
        }

        val candidates = buildList {
            if (!hwAacName.isNullOrBlank()) add(hwAacName)
            add("")
        }

        for (name in candidates) {
            for (attempt in 1..2) {
                var codec: MediaCodec? = null
                try {
                    codec = if (name.isNotBlank()) {
                        MediaCodec.createByCodecName(name)
                    } else {
                        MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    }
                    val effectiveBps = if (attempt == 1) bitrateBps else min(bitrateBps, 192_000)
                    val aacFormat = MediaFormat.createAudioFormat(
                        MediaFormat.MIMETYPE_AUDIO_AAC,
                        SAMPLE_RATE,
                        2
                    ).apply {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        setInteger(MediaFormat.KEY_BIT_RATE, effectiveBps)
                        if (attempt == 1) {
                            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
                        }
                    }
                    codec.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    codec.start()
                    val tag = if (name.isNotBlank()) "Hardware ($name)" else "System Default"
                    DiagnosticsLogger.log("AUDIO_ENC", "Configured 48 kHz Stereo AAC-LC encoder at ${effectiveBps / 1000} kbps ($tag).")
                    return codec
                } catch (_: Exception) {
                    try { codec?.release() } catch (_: Exception) {}
                }
            }
        }
        DiagnosticsLogger.log("AUDIO_ERR", "AAC audio encoder failed to initialize.")
        return null
    }

    fun stopStreamingAudio() {
        // Snapshot the true active mic status BEFORE tearing down audio so Session Reports never say "MIC STOPPED"
        DiagnosticsLogger.lockFinalMicStatusBeforeStop()
        isRunning = false
        pendingMicRouteRefresh = false
        unregisterDynamicAudioRouteWatcher()

        // Stop micRecord & gameRecord FIRST to immediately unblock any blocking AudioRecord.read() call
        try { micSession?.record?.stop() } catch (_: Exception) {}
        try { gameRecord?.stop() } catch (_: Exception) {}

        val mixerRef = mixerThread
        val drainRef = drainThread
        try { mixerRef?.join(1500) } catch (_: Exception) {}
        try { drainRef?.join(1500) } catch (_: Exception) {}

        if (mixerRef?.isAlive == true || drainRef?.isAlive == true) {
            try { mixerRef?.interrupt() } catch (_: Exception) {}
            try { drainRef?.interrupt() } catch (_: Exception) {}
            try { mixerRef?.join(600) } catch (_: Exception) {}
            try { drainRef?.join(600) } catch (_: Exception) {}
        }

        mixerThread = null
        drainThread = null

        val barrier = activeWorkerBarrier
        if (barrier == null || barrier.requestRelease()) {
            performFinalResourceRelease(barrier)
        } else {
            DiagnosticsLogger.log(
                "AUDIO_STOP_WAIT",
                "Audio worker (${barrier.activeWorkerCount()} active) still winding down after 2.1s; deferring final release until all workers exit."
            )
        }
        micStatusText = "MIC STOPPED"
        DiagnosticsLogger.micStatusLabel = micStatusText
    }

    private fun performFinalResourceRelease(barrier: AudioWorkerBarrier?) {
        synchronized(cleanupLock) {
            if (barrier != null && activeWorkerBarrier !== barrier) return
            releaseMicSafely()
            releaseGameSafely()
            micDsp.release()
            val codecRef = aacCodec
            aacCodec = null
            if (codecRef != null) {
                try { codecRef.stop() } catch (_: Exception) {}
                try { codecRef.release() } catch (_: Exception) {}
            }
            if (activeWorkerBarrier === barrier) {
                activeWorkerBarrier = null
            }
        }
    }

    private fun releaseMicSafely() {
        val sess = micSession
        micSession = null
        micDsp.platformHwNsAttached = false
        sess?.release()
    }

    private fun releaseGameSafely() {
        try {
            gameRecord?.stop()
            gameRecord?.release()
        } catch (_: Exception) {
        }
        gameRecord = null
        gameRecordIsPcm16 = false
        gameAudioActive = false
        gameAudioReceivingSignal = false
    }

    @SuppressLint("MissingPermission")
    private fun buildGameAudioRecord(mediaProjection: MediaProjection): Pair<AudioRecord, Boolean>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val encodings = listOf(
            AudioFormat.ENCODING_PCM_FLOAT to false,
            AudioFormat.ENCODING_PCM_16BIT to true
        )
        for ((encoding, isPcm16) in encodings) {
            var rec: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    encoding
                )
                if (minBuf <= 0) continue

                val bytesPerSample = if (isPcm16) 2 else 4
                val safeMinBuf = minBuf.coerceAtLeast(SAMPLE_RATE * 2 * bytesPerSample / 10)

                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                val format = AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()

                rec = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(safeMinBuf * 3)
                    .build()

                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    rec.startRecording()
                    val fmtTag = if (isPcm16) "48 kHz 16-bit PCM fallback" else "48 kHz 32-bit float"
                    DiagnosticsLogger.log("GAME_AUDIO", "Internal stereo game audio capture active ($fmtTag).")
                    return rec to isPcm16
                } else {
                    rec.release()
                }
            } catch (_: Exception) {
                try { rec?.release() } catch (_: Exception) {}
            }
        }
        DiagnosticsLogger.log("GAME_AUDIO_WARN", "Game audio capture unavailable; continuing with Mic.")
        return null
    }

    // === DRIFT-LOCKED STEREO GAME CIRCULAR BUFFER + 6-STAGE SOLLUXE™ 3.0 PURE FLOAT MIXER ===
    private fun runRealtimeFloatMixLoop(
        codec: MediaCodec,
        mediaProjection: MediaProjection?,
        originTimeUs: Long,
        sessionBarrier: AudioWorkerBarrier,
        onStatusChanged: (() -> Unit)?
    ) {
        // THREAD_PRIORITY_AUDIO (-16) gives high priority over background apps while NEVER
        // preempting Android's InputDispatcher or CODM's -19 gunfire mixer.
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        var adpfSession: PerformanceHintManager.Session? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val phm = context.getSystemService(PerformanceHintManager::class.java)
                adpfSession = phm?.createHintSession(
                    intArrayOf(Process.myTid()),
                    FRAME_DURATION_US * 1000L
                )
            } catch (_: Throwable) {
            }
        }

        val audioManager = try {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        } catch (_: Exception) {
            null
        }

        val micMonoBuf = FloatArray(MONO_SAMPLES_PER_10MS)
        val gamePullBuf = FloatArray(STEREO_SAMPLES_PER_10MS * 2)
        val gamePullShortBuf = ShortArray(STEREO_SAMPLES_PER_10MS * 2)
        val gameFrameBuf = FloatArray(STEREO_SAMPLES_PER_10MS)
        val mixedOutBytes = ByteArray(STEREO_SAMPLES_PER_10MS * 2)
        val mixedOutByteBuf = ByteBuffer.wrap(mixedOutBytes).order(ByteOrder.LITTLE_ENDIAN)

        // 160 ms stereo circular ring buffer (16 frames * 960 stereo floats) for drift-free game audio
        val ringCap = STEREO_SAMPLES_PER_10MS * 16
        val gameRing = FloatArray(ringCap)
        var ringReadPos = 0
        var ringWritePos = 0
        var ringAvailable = 0

        val maxDelayFrames = 22
        val delayRing = Array(maxDelayFrames) { FloatArray(MONO_SAMPLES_PER_10MS) }
        var delayWriteIdx = 0
        var lastAppliedDelayFrames = 0

        // Reset all game soundstage & limiter filter states cleanly on start
        gameSubBandExtractor.reset()
        gameFootstepLowThumpFilter.reset()
        gameFootstepBandExtractor.reset()
        sideLowPassState = 0.0f
        rawGameBlastEnv = 0.0f
        gameBlastGain = 1.0f
        subPunchVoiceGainSmooth = 0.0f
        sonarFootstepExtraLiftSmooth = 0.0f
        lookAheadBufL.fill(0f)
        lookAheadBufR.fill(0f)
        lookAheadIdx = 0
        prevSampleL0 = 0f; prevSampleL1 = 0f; prevSampleL2 = 0f
        prevSampleR0 = 0f; prevSampleR1 = 0f; prevSampleR2 = 0f
        limiterEnvGain = 1.0f

        var duckGainSmooth = 1.0f
        var gameMuteSmooth = 1.0f
        var consecutiveZeroOrSilencedReads = 0
        var totalFramesMixed = 0L
        var gameRebuildAttempts = 0
        var lastGameSignalMs = System.currentTimeMillis()
        var lastDriftCorrectionFrame = 0L
        var wasInPhoneCallInterruption = false
        var consecutiveClutchFrames = 0

        verifyActiveMicConfiguration(micSession, forceLog = true)
        onStatusChanged?.invoke()

        try {
            while (isRunning) {
                val workStartNs = System.nanoTime()

                // Incoming Phone Call / SIM Ring Privacy Shield
                val callMode = try { audioManager?.mode ?: AudioManager.MODE_NORMAL } catch (_: Exception) { AudioManager.MODE_NORMAL }
                val inPhoneCall = (callMode == AudioManager.MODE_IN_CALL || callMode == AudioManager.MODE_RINGTONE)
                if (inPhoneCall && !wasInPhoneCallInterruption) {
                    wasInPhoneCallInterruption = true
                    micStatusText = "CALL PRIVACY MUTE"
                    DiagnosticsLogger.micStatusLabel = micStatusText
                    DiagnosticsLogger.log("CALL_SHIELD", "Incoming/active phone call detected — stream mic auto-muted for privacy.")
                    onStatusChanged?.invoke()
                } else if (!inPhoneCall && wasInPhoneCallInterruption) {
                    wasInPhoneCallInterruption = false
                    pendingMicRouteRefresh = true
                    DiagnosticsLogger.log("CALL_SHIELD", "Phone call ended — restoring 48 kHz stream studio microphone.")
                }

                if (pendingMicRouteRefresh && !inPhoneCall && isRunning) {
                    pendingMicRouteRefresh = false
                    didClearPreferredDeviceForHalRate = false
                    releaseMicSafely()
                    if (!isRunning) break
                    val desiredEffectiveAec = computeEffectiveSpeakerAec(context, activeSpeakerAecEnabled, activeMicDeviceId)
                    effectiveSpeakerAecActive = desiredEffectiveAec
                    val reopened = buildMicAudioRecord(
                        context = context,
                        requestedSource = activeMicAudioSource,
                        preferredDeviceId = activeMicDeviceId,
                        enableHwNs = activeHwNsEnabled,
                        effectiveSpeakerAec = desiredEffectiveAec
                    )
                    if (!isRunning) {
                        reopened?.release()
                        break
                    }
                    if (reopened != null) {
                        micSession = reopened
                        effectiveSpeakerAecActive = computeEffectiveSpeakerAec(
                            context,
                            activeSpeakerAecEnabled,
                            activeMicDeviceId,
                            reopened.record.routedDevice
                        )
                        micDsp.platformHwNsAttached = (reopened.hwNoiseSuppressor != null)
                        val refreshedMode = detectHardwareAcousticMode(
                            context,
                            activeMicDeviceId,
                            activeSpeakerAecEnabled,
                            reopened.record.routedDevice
                        )
                        micDsp.applyHardwareAcousticMode(refreshedMode)
                        activeAcousticModeLabel = refreshedMode.title
                        verifyActiveMicConfiguration(reopened, forceLog = true)
                    } else {
                        micStatusText = "MIC RETRYING"
                        DiagnosticsLogger.micStatusLabel = micStatusText
                    }
                    onStatusChanged?.invoke()
                }

                // 1. Read 10 ms mono 48 kHz 32-bit float frame from microphone (supports PCM_FLOAT, PCM_16BIT & resampled rates)
                val activeMic = micSession
                val micRead = if (inPhoneCall) {
                    try { Thread.sleep(10) } catch (_: InterruptedException) {}
                    micMonoBuf.fill(0f)
                    MONO_SAMPLES_PER_10MS
                } else {
                    activeMic?.readMono48kFrame(micMonoBuf) ?: -1
                }
                if (!isRunning) break

                var micPeakRaw = 0f
                if (micRead > 0 && !inPhoneCall) {
                    for (i in 0 until MONO_SAMPLES_PER_10MS) {
                        val a = abs(micMonoBuf[i])
                        if (a > micPeakRaw) micPeakRaw = a
                    }
                } else {
                    micMonoBuf.fill(0f)
                }

                if (!inPhoneCall && isRunning) {
                    if (micRead <= 0 || micPeakRaw < 1e-7f) {
                        consecutiveZeroOrSilencedReads++
                        // Always sleep 10 ms on non-blocking error reads so the mixer never spins CPU or jumps PTS
                        if (micRead <= 0) {
                            try { Thread.sleep(10) } catch (_: InterruptedException) { break }
                        }
                        if (consecutiveZeroOrSilencedReads >= 120) {
                            attemptAutomaticMicRecovery(onStatusChanged)
                            consecutiveZeroOrSilencedReads = 0
                        }
                    } else {
                        consecutiveZeroOrSilencedReads = 0
                        if (micStatusText != "MIC OK") {
                            micStatusText = "MIC OK"
                            DiagnosticsLogger.micStatusLabel = "MIC OK ($routedMicLabel)"
                            onStatusChanged?.invoke()
                        }
                    }
                }

                // Feed speaker-bleed reference ONLY when SPEAKER_AEC is genuinely active (never on earphones!)
                micDsp.speakerBleedLevel = if (effectiveSpeakerAecActive && !gameStreamMuted) {
                    (rawGameBlastEnv * 1.4f).coerceIn(0f, 1f)
                } else {
                    0f
                }

                // 2. Run 6-Stage 32-bit Float / 64-bit Double SolLuxe™ 3.0 Pure Voice DSP + C 48kHz RNNoise on Mic ONLY
                micDsp.processMonoFrameInPlace(micMonoBuf, MONO_SAMPLES_PER_10MS)

                // 3. Apply Bluetooth / Facecam Mic Sync Delay Ring Buffer (0 - 200 ms) with clean zeroing on change
                val delayFrames = (micSyncDelayMs / 10).coerceIn(0, maxDelayFrames - 1)
                if (delayFrames != lastAppliedDelayFrames) {
                    for (slot in delayRing) slot.fill(0f)
                    delayWriteIdx = 0
                    lastAppliedDelayFrames = delayFrames
                }
                val delayedMono: FloatArray
                if (delayFrames == 0) {
                    delayedMono = micMonoBuf
                } else {
                    System.arraycopy(micMonoBuf, 0, delayRing[delayWriteIdx], 0, MONO_SAMPLES_PER_10MS)
                    val readIdx = (delayWriteIdx - delayFrames + maxDelayFrames) % maxDelayFrames
                    delayedMono = delayRing[readIdx]
                    delayWriteIdx = (delayWriteIdx + 1) % maxDelayFrames
                }

                // 4. Drain available non-blocking stereo game samples into Drift-Locked Circular Ring Buffer
                val activeGame = gameRecord
                if (activeGame != null) {
                    var pullLoops = 0
                    var gameDeadError = false
                    while (pullLoops < 3) {
                        val pulled = if (!gameRecordIsPcm16) {
                            activeGame.read(
                                gamePullBuf,
                                0,
                                gamePullBuf.size,
                                AudioRecord.READ_NON_BLOCKING
                            )
                        } else {
                            val n = activeGame.read(
                                gamePullShortBuf,
                                0,
                                gamePullShortBuf.size,
                                AudioRecord.READ_NON_BLOCKING
                            )
                            if (n > 0) {
                                val invScale = 1.0f / 32768.0f
                                for (i in 0 until n) {
                                    gamePullBuf[i] = gamePullShortBuf[i] * invScale
                                }
                            }
                            n
                        }
                        if (pulled == AudioRecord.ERROR_DEAD_OBJECT || pulled == AudioRecord.ERROR_INVALID_OPERATION) {
                            gameDeadError = true
                            break
                        }
                        if (pulled <= 0) break
                        val evenPulled = pulled and 1.inv()
                        for (i in 0 until evenPulled) {
                            val v = gamePullBuf[i]
                            gameRing[ringWritePos] = if (v.isFinite()) v else 0f
                            ringWritePos = (ringWritePos + 1) % ringCap
                        }
                        ringAvailable += evenPulled
                        if (ringAvailable > ringCap) {
                            val overflow = ringAvailable - (STEREO_SAMPLES_PER_10MS * 4)
                            ringReadPos = (ringReadPos + overflow) % ringCap
                            ringAvailable = STEREO_SAMPLES_PER_10MS * 4
                        }
                        pullLoops++
                    }

                    if (gameDeadError) {
                        DiagnosticsLogger.log("GAME_AUDIO_REBUILD", "Game AudioRecord reported dead/invalid state; rebuilding capture...")
                        releaseGameSafely()
                        gameFrameBuf.fill(0f)
                    } else {
                        // Seamless fractional 1-stereo-pair slip if ring buffer drifts >90ms
                        if (ringAvailable >= STEREO_SAMPLES_PER_10MS * 9 &&
                            totalFramesMixed - lastDriftCorrectionFrame > 25L
                        ) {
                            val nextPos = (ringReadPos + 2) % ringCap
                            gameRing[nextPos] = 0.5f * (gameRing[ringReadPos] + gameRing[nextPos])
                            gameRing[(nextPos + 1) % ringCap] =
                                0.5f * (gameRing[(ringReadPos + 1) % ringCap] + gameRing[(nextPos + 1) % ringCap])
                            ringReadPos = nextPos
                            ringAvailable -= 2
                            lastDriftCorrectionFrame = totalFramesMixed
                        }

                        if (ringAvailable >= STEREO_SAMPLES_PER_10MS) {
                            var peakG = 0f
                            for (i in 0 until STEREO_SAMPLES_PER_10MS) {
                                val s = gameRing[ringReadPos]
                                ringReadPos = (ringReadPos + 1) % ringCap
                                gameFrameBuf[i] = s
                                val a = abs(s)
                                if (a > peakG) peakG = a
                            }
                            ringAvailable -= STEREO_SAMPLES_PER_10MS
                            lastGamePeakDb = MicDspProcessor.linearToDb(peakG)
                            if (peakG > 0.0002f) {
                                gameRebuildAttempts = 0
                                lastGameSignalMs = System.currentTimeMillis()
                                if (!gameAudioReceivingSignal) {
                                    gameAudioReceivingSignal = true
                                    onStatusChanged?.invoke()
                                }
                            } else if (System.currentTimeMillis() - lastGameSignalMs > 5000L && gameAudioReceivingSignal) {
                                gameAudioReceivingSignal = false
                                onStatusChanged?.invoke()
                            }
                        } else {
                            gameFrameBuf.fill(0f)
                        }
                    }
                } else {
                    gameFrameBuf.fill(0f)
                    if (mediaProjection != null &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        totalFramesMixed > 0 &&
                        (totalFramesMixed % 300L) == 0L &&
                        gameRebuildAttempts < 12 &&
                        isRunning
                    ) {
                        gameRebuildAttempts++
                        val rebuilt = buildGameAudioRecord(mediaProjection)
                        if (rebuilt != null) {
                            gameRecord = rebuilt.first
                            gameRecordIsPcm16 = rebuilt.second
                            gameAudioActive = true
                            onStatusChanged?.invoke()
                        }
                    }
                }

                // 5. VAD Auto-Ducking, Phase-Safe Theater Sub-Punch, Dual-Band Footstep Focus & 250% Game Gain
                val speechNow = micDsp.vadSpeechActive
                val targetDuck = if (speechNow && vadDuckingAmount > 0.01f) {
                    (1.0f - vadDuckingAmount).coerceIn(0.20f, 1.0f)
                } else {
                    1.0f
                }
                val duckCoeff = if (targetDuck < duckGainSmooth) 0.14f else 0.025f
                duckGainSmooth += duckCoeff * (targetDuck - duckGainSmooth)

                val targetGameMute = if (gameStreamMuted) 0.0f else 1.0f
                gameMuteSmooth += 0.12f * (targetGameMute - gameMuteSmooth)

                val effectiveGameGain = gameGainLinear * duckGainSmooth * gameMuteSmooth
                val wideOn = gameCinemaWideEnabled
                val subOn = gameSubPunchEnabled
                val footOn = gameFootstepFocusEnabled
                val blastOn = gameBlastTamerEnabled

                val targetSubVoiceGain = if (!subOn || speechNow) 0.0f else 0.42f
                val targetSonarLift = if (footOn && rawGameBlastEnv < 0.12f) {
                    ((0.12f - rawGameBlastEnv) / 0.12f).coerceIn(0.0f, 1.0f) * 0.25f
                } else {
                    0.0f
                }
                if (!footOn) sonarFootstepExtraLiftSmooth = 0.0f

                mixedOutByteBuf.clear()

                // 6. Mix Stereo Game + Processed Mic -> 64-Sample Look-Ahead 4x Cubic True-Peak Limiter (-1.16 dBTP)
                for (sIdx in 0 until MONO_SAMPLES_PER_10MS) {
                    val m = delayedMono[sIdx]
                    val rawGL = gameFrameBuf[sIdx * 2]
                    val rawGR = gameFrameBuf[sIdx * 2 + 1]

                    val rawPeakG = max(abs(rawGL), abs(rawGR))
                    rawGameBlastEnv += (if (rawPeakG > rawGameBlastEnv) 0.08f else 0.0006f) * (rawPeakG - rawGameBlastEnv)

                    var gL = rawGL * effectiveGameGain
                    var gR = rawGR * effectiveGameGain

                    val subRate = if (targetSubVoiceGain < subPunchVoiceGainSmooth) 0.015f else 0.0025f
                    subPunchVoiceGainSmooth += subRate * (targetSubVoiceGain - subPunchVoiceGainSmooth)
                    if (subOn || subPunchVoiceGainSmooth > 0.001f) {
                        val subBandL = gameSubBandExtractor.processL(gL)
                        val subBandR = gameSubBandExtractor.processR(gR)
                        gL += subBandL * subPunchVoiceGainSmooth
                        gR += subBandR * subPunchVoiceGainSmooth
                    }

                    if (footOn) {
                        gL = gameFootstepLowThumpFilter.processL(gL)
                        gR = gameFootstepLowThumpFilter.processR(gR)

                        val sonarRate = if (targetSonarLift < sonarFootstepExtraLiftSmooth) 0.025f else 0.003f
                        sonarFootstepExtraLiftSmooth += sonarRate * (targetSonarLift - sonarFootstepExtraLiftSmooth)
                        val totalCrunchLift = 0.29f + sonarFootstepExtraLiftSmooth
                        val footBandL = gameFootstepBandExtractor.processL(gL)
                        val footBandR = gameFootstepBandExtractor.processR(gR)
                        gL += footBandL * totalCrunchLift
                        gR += footBandR * totalCrunchLift
                    }

                    if (wideOn) {
                        val mid = 0.5f * (gL + gR) * 0.94f
                        val side = 0.5f * (gL - gR)
                        sideLowPassState += 0.026f * (side - sideLowPassState)
                        val sideHigh = side - sideLowPassState
                        val widenedSide = sideLowPassState + (sideHigh * 1.28f)
                        gL = mid + widenedSide
                        gR = mid - widenedSide
                    }

                    if (blastOn) {
                        val effectiveBlastLevel = max(rawGameBlastEnv, max(abs(gL), abs(gR)) * 0.85f)
                        val blastThresh = 0.42f
                        val targetBlast = if (effectiveBlastLevel > blastThresh) {
                            (blastThresh / effectiveBlastLevel).pow(0.60f).coerceIn(0.38f, 1.0f)
                        } else {
                            1.0f
                        }
                        val blastRate = if (targetBlast < gameBlastGain) 0.08f else 0.0006f
                        gameBlastGain += blastRate * (targetBlast - gameBlastGain)
                        gL *= gameBlastGain
                        gR *= gameBlastGain
                    }

                    val inL = gL + m
                    val inR = gR + m

                    val truePeakL = estimate4xTruePeak(prevSampleL0, prevSampleL1, prevSampleL2, inL)
                    val truePeakR = estimate4xTruePeak(prevSampleR0, prevSampleR1, prevSampleR2, inR)
                    prevSampleL0 = prevSampleL1; prevSampleL1 = prevSampleL2; prevSampleL2 = inL
                    prevSampleR0 = prevSampleR1; prevSampleR1 = prevSampleR2; prevSampleR2 = inR

                    val incomingPeak = max(truePeakL, truePeakR)
                    val ceiling = 0.875f // -1.16 dBTP broadcast True-Peak ceiling
                    val reqGain = if (incomingPeak > ceiling) {
                        (ceiling / incomingPeak).coerceIn(0.15f, 1.0f)
                    } else {
                        1.0f
                    }

                    if (reqGain < limiterEnvGain) {
                        limiterEnvGain += 0.25f * (reqGain - limiterEnvGain)
                    } else {
                        limiterEnvGain += 0.0018f * (1.0f - limiterEnvGain)
                    }

                    val outL = lookAheadBufL[lookAheadIdx] * limiterEnvGain
                    val outR = lookAheadBufR[lookAheadIdx] * limiterEnvGain
                    lookAheadBufL[lookAheadIdx] = inL
                    lookAheadBufR[lookAheadIdx] = inR
                    lookAheadIdx = (lookAheadIdx + 1) % lookAheadFrames

                    mixedOutByteBuf.putShort(floatToPcm16WithTpdfDither(outL))
                    mixedOutByteBuf.putShort(floatToPcm16WithTpdfDither(outR))
                }

                // AI Auto-Clutch Highlight Detector: gated strictly to LIVE scene & unmuted game audio
                if (streamSceneIsLive && !gameStreamMuted && !micDsp.streamMicMuted &&
                    rawGameBlastEnv > 0.26f && micDsp.excitedCalloutActive
                ) {
                    consecutiveClutchFrames++
                    if (consecutiveClutchFrames == 35) {
                        val elapsedSec = max(1L, totalFramesMixed / 100L)
                        DiagnosticsLogger.markAiClutchHighlight(
                            elapsedStreamSec = elapsedSec,
                            detail = "🤖 AI Auto-Clutch (Intense Gunfight + Callout)",
                            context = context
                        )
                    }
                } else {
                    consecutiveClutchFrames = max(0, consecutiveClutchFrames - 1)
                }

                // 7. Tight ±12 ms Drift-Locked PTS Synchronization
                val nowWallUs = max(0L, (System.nanoTime() / 1000L) - originTimeUs)
                val samplePtsUs = totalFramesMixed * FRAME_DURATION_US
                val wallDriftUs = nowWallUs - samplePtsUs
                if (wallDriftUs > 12_000L && ringAvailable >= 2) {
                    ringReadPos = (ringReadPos + 2) % ringCap
                    ringAvailable -= 2
                }
                val ptsUs = if (abs(wallDriftUs) > 85_000L) {
                    totalFramesMixed = nowWallUs / FRAME_DURATION_US
                    nowWallUs
                } else {
                    samplePtsUs
                }
                totalFramesMixed++

                val inIdx = codec.dequeueInputBuffer(8_000L)
                if (inIdx >= 0) {
                    val codecBuf = codec.getInputBuffer(inIdx)
                    if (codecBuf != null) {
                        codecBuf.clear()
                        codecBuf.put(mixedOutBytes, 0, mixedOutBytes.size)
                        codec.queueInputBuffer(inIdx, 0, mixedOutBytes.size, ptsUs, 0)
                    }
                }

                if ((totalFramesMixed % 300L) == 0L && !inPhoneCall && isRunning) {
                    verifyActiveMicConfiguration(micSession, forceLog = false)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && adpfSession != null) {
                    try {
                        adpfSession.reportActualWorkDuration(System.nanoTime() - workStartNs)
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (ce: MediaCodec.CodecException) {
            if (!ce.isTransient && isRunning) {
                val msg = "Audio AAC encoder error (${ce.diagnosticInfo})"
                DiagnosticsLogger.log("AUDIO_ERR", msg)
                onFatalAudioError?.invoke(msg)
            }
        } catch (e: Exception) {
            if (isRunning) {
                val msg = "Audio mixer interrupted (${e.javaClass.simpleName})"
                DiagnosticsLogger.log("AUDIO_ERR", msg, e.message ?: "")
                onFatalAudioError?.invoke(msg)
            }
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { adpfSession?.close() } catch (_: Throwable) {}
            }
            if (sessionBarrier.onWorkerExited()) {
                performFinalResourceRelease(sessionBarrier)
            }
        }
    }

    private fun estimate4xTruePeak(xm1: Float, x0: Float, x1: Float, x2: Float): Float {
        var maxP = max(abs(x0), abs(x1))
        if (maxP < 0.55f) return maxP
        val c0 = x0
        val c1 = 0.5f * (x1 - xm1)
        val c2 = xm1 - 2.5f * x0 + 2.0f * x1 - 0.5f * x2
        val c3 = 0.5f * (x2 - xm1) + 1.5f * (x0 - x1)
        val p25 = abs(((c3 * 0.25f + c2) * 0.25f + c1) * 0.25f + c0)
        val p50 = abs(((c3 * 0.50f + c2) * 0.50f + c1) * 0.50f + c0)
        val p75 = abs(((c3 * 0.75f + c2) * 0.75f + c1) * 0.75f + c0)
        if (p25 > maxP) maxP = p25
        if (p50 > maxP) maxP = p50
        if (p75 > maxP) maxP = p75
        return maxP
    }

    private fun attemptAutomaticMicRecovery(onStatusChanged: (() -> Unit)?) {
        if (!isRunning) return
        try {
            val cur = micSession?.record
            if (cur != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val cfg = findOurRecordingConfiguration(cur)
                if (cfg != null && cfg.isClientSilenced) {
                    micStatusText = "MIC SILENCED BY ANDROID (CHECK ACCESSIBILITY)"
                    DiagnosticsLogger.micStatusLabel = micStatusText
                    DiagnosticsLogger.log(
                        "MIC_SILENCED",
                        "Android silenced the microphone session while another app used voice chat. Re-routing..."
                    )
                }
            }

            releaseMicSafely()
            if (!isRunning) return

            val fallbackSrc = if (activeMicAudioSource == 0) {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            } else {
                activeMicAudioSource
            }
            val desiredEffectiveAec = computeEffectiveSpeakerAec(context, activeSpeakerAecEnabled, activeMicDeviceId)
            effectiveSpeakerAecActive = desiredEffectiveAec
            val reopened = buildMicAudioRecord(
                context = context,
                requestedSource = fallbackSrc,
                preferredDeviceId = activeMicDeviceId,
                enableHwNs = activeHwNsEnabled,
                effectiveSpeakerAec = desiredEffectiveAec
            )
            if (!isRunning) {
                reopened?.release()
                return
            }
            if (reopened != null) {
                micSession = reopened
                effectiveSpeakerAecActive = computeEffectiveSpeakerAec(
                    context,
                    activeSpeakerAecEnabled,
                    activeMicDeviceId,
                    reopened.record.routedDevice
                )
                micDsp.platformHwNsAttached = (reopened.hwNoiseSuppressor != null)
                val recoveredMode = detectHardwareAcousticMode(
                    context,
                    activeMicDeviceId,
                    activeSpeakerAecEnabled,
                    reopened.record.routedDevice
                )
                micDsp.applyHardwareAcousticMode(recoveredMode)
                activeAcousticModeLabel = recoveredMode.title
                verifyActiveMicConfiguration(reopened, forceLog = true)
                DiagnosticsLogger.log(
                    "MIC_RECOVER",
                    "Automatically recovered stream microphone on $routedMicLabel."
                )
            } else {
                micStatusText = "MIC SILENCED"
                DiagnosticsLogger.micStatusLabel = micStatusText
            }
            onStatusChanged?.invoke()
        } catch (_: Exception) {
        }
    }

    /**
     * Strictly matches ONLY our own AudioRecord.audioSessionId so CODM's in-game voice chat
     * session never triggers false [MIC_OK] or silence events.
     */
    private fun findOurRecordingConfiguration(rec: AudioRecord): AudioRecordingConfiguration? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return try {
            val direct = rec.activeRecordingConfiguration
            if (direct != null && direct.clientAudioSessionId == rec.audioSessionId) {
                return direct
            }
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.activeRecordingConfigurations.firstOrNull {
                it.clientAudioSessionId == rec.audioSessionId
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun verifyActiveMicConfiguration(session: OpenedMicSession?, forceLog: Boolean = false) {
        val rec = session?.record ?: return
        if (!isRunning) return
        try {
            val routedDev = rec.routedDevice
            if (routedDev != null) {
                // If user is in Auto (-1) mode and Android routed our mic to a Bluetooth SCO/BLE call mic,
                // immediately lock onto the Phone's Built-in Studio Mic.
                val isUnwantedBtMic = activeMicDeviceId == -1 && (
                    routedDev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        routedDev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    )
                if (isUnwantedBtMic) {
                    resolveTargetMicDevice(context, -1)?.let { studioDev ->
                        try { rec.setPreferredDevice(studioDev) } catch (_: Exception) {}
                    }
                }

                routedMicLabel = "${routedDev.productName} (${DiagnosticsLogger.audioDeviceTypeName(routedDev.type)})"
                shortMicPillLabel = when (routedDev.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone Studio"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Mic"
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-C Mic"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> "BT Mic"
                    else -> "Studio Mic"
                }
                val newHwMode = detectHardwareAcousticMode(context, activeMicDeviceId, activeSpeakerAecEnabled, routedDev)
                val newEffectiveAec = computeEffectiveSpeakerAec(context, activeSpeakerAecEnabled, activeMicDeviceId, routedDev)
                micDsp.applyHardwareAcousticMode(newHwMode)
                activeAcousticModeLabel = newHwMode.title
                if (newEffectiveAec != effectiveSpeakerAecActive) {
                    effectiveSpeakerAecActive = newEffectiveAec
                    pendingMicRouteRefresh = true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val cfg: AudioRecordingConfiguration? = findOurRecordingConfiguration(rec)
                if (cfg != null) {
                    val isSilenced = cfg.isClientSilenced
                    val sessId = rec.audioSessionId
                    val actualFmt = cfg.format
                    val clientFmt = cfg.clientFormat
                    val halRate = actualFmt.sampleRate
                    val clientRate = clientFmt.sampleRate

                    // Keep AudioRecord continuously open when Samsung's HAL shares the mic at 16 kHz mid-match
                    // (zero reopen clicks; SolLuxe™ 3.0 Pure Hybrid VAD handles 16 kHz HAL locks cleanly).
                    val isBuiltInOrWired = routedDev == null || (
                        routedDev.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
                            routedDev.type != AudioDeviceInfo.TYPE_BLE_HEADSET
                        )
                    if (!isSilenced && isBuiltInOrWired && halRate in 8000..32000 && !didClearPreferredDeviceForHalRate) {
                        didClearPreferredDeviceForHalRate = true
                        if (activeMicDeviceId == -1 && routedDev?.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                            try { rec.setPreferredDevice(null) } catch (_: Exception) {}
                        }
                    } else if (halRate >= 44100) {
                        didClearPreferredDeviceForHalRate = false
                    }

                    val stateChanged = forceLog ||
                        sessId != lastLoggedMicSessionId ||
                        routedMicLabel != lastLoggedMicRoute ||
                        isSilenced != lastLoggedMicSilenced

                    lastLoggedMicSessionId = sessId
                    lastLoggedMicRoute = routedMicLabel
                    lastLoggedMicSilenced = isSilenced

                    if (isSilenced) {
                        micStatusText = "MIC SILENCED BY OS"
                        DiagnosticsLogger.micStatusLabel = micStatusText
                    } else {
                        micStatusText = "MIC OK"
                        DiagnosticsLogger.micStatusLabel = "MIC OK ($routedMicLabel)"
                        if (stateChanged) {
                            val aecTag = if (session.actualHwAecEnabled) "hwAec=ON" else "hwAec=OFF"
                            DiagnosticsLogger.log(
                                "MIC_OK",
                                "Stream microphone active on $routedMicLabel ($activeAcousticModeLabel).",
                                "session=$sessId capture=${session.captureLabel} client=${clientRate}Hz hal=${halRate}Hz/${actualFmt.channelCount}ch $aecTag"
                            )
                        }
                    }
                    return
                }
            }
            micStatusText = "MIC OK"
            DiagnosticsLogger.micStatusLabel = "MIC OK ($routedMicLabel)"
        } catch (_: Exception) {
        }
    }

    private fun floatToPcm16WithTpdfDither(sample: Float): Short {
        var s = sample
        if (!s.isFinite()) return 0
        if (s > 0.9999f) s = 0.9999f
        if (s < -0.9999f) s = -0.9999f

        var x = tpdfSeed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        val r1 = (x and 0x7FFF).toFloat() / 32768.0f
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        val r2 = (x and 0x7FFF).toFloat() / 32768.0f
        tpdfSeed = x

        val dither = (r1 - r2) * 0.5f
        val scaled = (s * 32767.0f) + dither
        return scaled.roundToInt().coerceIn(-32768, 32767).toShort()
    }

    private fun runAacDrainLoop(
        codec: MediaCodec,
        sessionBarrier: AudioWorkerBarrier,
        onEncodedAac: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        val aacPool = Array(6) { ByteBuffer.allocateDirect(16384) }
        val infoPool = Array(6) { MediaCodec.BufferInfo() }
        var poolIdx = 0
        var lastSentAudioPtsUs = 0L

        try {
            while (isRunning) {
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 15_000L)
                if (outIdx < 0) continue

                val outBuf = codec.getOutputBuffer(outIdx)
                val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0

                if (outBuf != null && bufferInfo.size > 0 && !isConfig) {
                    val slot = poolIdx
                    poolIdx = (poolIdx + 1) % aacPool.size

                    var copy = aacPool[slot]
                    if (bufferInfo.size > copy.capacity()) {
                        copy = ByteBuffer.allocateDirect(bufferInfo.size + 4096)
                        aacPool[slot] = copy
                    }

                    copy.clear()
                    outBuf.position(bufferInfo.offset)
                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                    copy.put(outBuf)
                    copy.flip()

                    var adjPtsUs = max(0L, bufferInfo.presentationTimeUs)
                    if (adjPtsUs < lastSentAudioPtsUs) {
                        adjPtsUs = lastSentAudioPtsUs + 1_000L
                    }
                    lastSentAudioPtsUs = adjPtsUs

                    val outInfo = infoPool[slot].apply {
                        set(0, bufferInfo.size, adjPtsUs, bufferInfo.flags)
                    }
                    onEncodedAac(copy, outInfo)
                }
                codec.releaseOutputBuffer(outIdx, false)
            }
        } catch (ce: MediaCodec.CodecException) {
            if (!ce.isTransient && isRunning) {
                val msg = "AAC drain encoder error (${ce.diagnosticInfo})"
                DiagnosticsLogger.log("AUDIO_ERR", msg)
                onFatalAudioError?.invoke(msg)
            }
        } catch (e: Exception) {
            if (isRunning) {
                DiagnosticsLogger.log("AUDIO_DRAIN_WARN", "AAC drain loop exited (${e.javaClass.simpleName}).")
            }
        } finally {
            if (sessionBarrier.onWorkerExited()) {
                performFinalResourceRelease(sessionBarrier)
            }
        }
    }

    /**
     * Wraps an active microphone AudioRecord across 48 kHz PCM_FLOAT, 48 kHz PCM_16BIT,
     * and alternate-sample-rate PCM_16BIT fallbacks, always delivering exact 480-sample / 48 kHz
     * mono float frames with zero per-frame allocations.
     */
    private class OpenedMicSession(
        val record: AudioRecord,
        val sampleRateHz: Int,
        val encoding: Int,
        val captureLabel: String,
        val hwNoiseSuppressor: NoiseSuppressor?,
        val hwEchoCanceler: AcousticEchoCanceler?,
        val actualHwAecEnabled: Boolean
    ) {
        private val resampler = if (sampleRateHz != SAMPLE_RATE) LinearMonoResampler48k(sampleRateHz) else null
        private val pcm16Buf = if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
            ShortArray(resampler?.sourceSamplesPer10ms ?: MONO_SAMPLES_PER_10MS)
        } else {
            null
        }

        fun readMono48kFrame(dest480: FloatArray): Int {
            if (encoding == AudioFormat.ENCODING_PCM_FLOAT && sampleRateHz == SAMPLE_RATE) {
                val read = record.read(dest480, 0, MONO_SAMPLES_PER_10MS, AudioRecord.READ_BLOCKING)
                if (read > 0 && read < MONO_SAMPLES_PER_10MS) {
                    dest480.fill(0f, read, MONO_SAMPLES_PER_10MS)
                }
                return read
            }

            val sBuf = pcm16Buf ?: return -1
            val read = record.read(sBuf, 0, sBuf.size, AudioRecord.READ_BLOCKING)
            if (read <= 0) {
                dest480.fill(0f)
                return read
            }
            val rs = resampler
            if (rs != null) {
                rs.resample10msPcm16To480Float(sBuf, read, dest480)
            } else {
                val invScale = 1.0f / 32768.0f
                val count = read.coerceAtMost(MONO_SAMPLES_PER_10MS)
                for (i in 0 until count) {
                    dest480[i] = sBuf[i] * invScale
                }
                if (count < MONO_SAMPLES_PER_10MS) {
                    dest480.fill(0f, count, MONO_SAMPLES_PER_10MS)
                }
            }
            return MONO_SAMPLES_PER_10MS
        }

        fun release() {
            try { hwNoiseSuppressor?.release() } catch (_: Exception) {}
            try { hwEchoCanceler?.release() } catch (_: Exception) {}
            try {
                record.stop()
                record.release()
            } catch (_: Exception) {
            }
        }
    }

    private data class MicCaptureProfile(
        val sampleRateHz: Int,
        val encoding: Int,
        val label: String
    )

    companion object {
        const val SAMPLE_RATE = 48000
        const val MONO_SAMPLES_PER_10MS = 480
        const val STEREO_SAMPLES_PER_10MS = 960
        private const val FRAME_DURATION_US = 10_000L
        private const val TEST_PCM_FILENAME = "sol_mic_test_48k.pcm"

        private val MIC_CAPTURE_PROFILES = listOf(
            MicCaptureProfile(48000, AudioFormat.ENCODING_PCM_FLOAT, "48kHz PCM_FLOAT"),
            MicCaptureProfile(48000, AudioFormat.ENCODING_PCM_16BIT, "48kHz PCM_16BIT"),
            MicCaptureProfile(44100, AudioFormat.ENCODING_PCM_16BIT, "44.1kHz PCM_16BIT->48kHz"),
            MicCaptureProfile(16000, AudioFormat.ENCODING_PCM_16BIT, "16kHz PCM_16BIT->48kHz")
        )

        private val isCalibrationRecording = AtomicBoolean(false)
        private val isCalibrationPlaying = AtomicBoolean(false)

        @Volatile var lastCalibrationAdvice: MicCalibrationAdvice? = null

        /**
         * Determines the active HardwareAcousticMode from the connected/routed microphone
         * and speakerphone setting so SolLuxe™ 3.0 Pure tunes its 4x3 matrix automatically.
         */
        fun detectHardwareAcousticMode(
            context: Context,
            preferredDeviceId: Int,
            speakerAecEnabled: Boolean,
            routedDeviceOverride: AudioDeviceInfo? = null
        ): HardwareAcousticMode {
            val dev = routedDeviceOverride ?: resolveTargetMicDevice(context, preferredDeviceId)
            if (dev != null) {
                when (dev.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE -> return HardwareAcousticMode.WIRED_USB_CLOSE

                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> return HardwareAcousticMode.BLUETOOTH_SCO
                }
            }
            if (speakerAecEnabled && !hasHeadphonesConnected(context)) {
                return HardwareAcousticMode.SPEAKER_AEC
            }
            return HardwareAcousticMode.PHONE_STUDIO
        }

        /**
         * Computes whether Speakerphone AEC should actually be enabled on the microphone route.
         * Strictly returns FALSE whenever wired/USB/Bluetooth headphones or headsets are connected.
         */
        fun computeEffectiveSpeakerAec(
            context: Context,
            speakerAecEnabled: Boolean,
            preferredDeviceId: Int = -1,
            routedDeviceOverride: AudioDeviceInfo? = null
        ): Boolean {
            if (!speakerAecEnabled) return false
            if (hasHeadphonesConnected(context)) return false
            val mode = detectHardwareAcousticMode(context, preferredDeviceId, speakerAecEnabled, routedDeviceOverride)
            return mode == HardwareAcousticMode.SPEAKER_AEC
        }

        fun hasHeadphonesConnected(context: Context): Boolean {
            return try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { out ->
                    out.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        out.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        out.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        out.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        out.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        out.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        out.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        out.type == AudioDeviceInfo.TYPE_USB_DEVICE
                }
            } catch (_: Exception) {
                false
            }
        }

        @SuppressLint("MissingPermission")
        fun inspectPreFlightAudioAndWifiCoexistence(context: Context): String {
            return try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val hasBtOut = outputs.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                val hasWiredOrUsbOut = outputs.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                }

                val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                val volPct = ((curVol * 100.0f) / maxVol.toFloat()).roundToInt().coerceIn(0, 100)

                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val freqMhz = try { wm?.connectionInfo?.frequency ?: 0 } catch (_: Exception) { 0 }
                val wifiBandTag = when {
                    freqMhz in 4900..5900 -> "5GHz Wi-Fi"
                    freqMhz in 5925..7125 -> "6GHz Wi-Fi"
                    freqMhz in 2400..2500 -> "2.4GHz Wi-Fi"
                    else -> ""
                }

                val routeSummary = when {
                    hasBtOut -> "BT Stereo + 48kHz Phone Studio Mic"
                    hasWiredOrUsbOut -> "Wired/USB-C Close-Talk Audio"
                    else -> "Phone Speaker + 48kHz Studio Mic"
                }

                val volWarning = if (volPct == 0) {
                    " • ⚠️ Media Vol: 0% (Turn up phone volume so game audio is heard!)"
                } else {
                    " • Media Vol: $volPct%"
                }

                val radioTag = when {
                    hasBtOut && wifiBandTag.startsWith("5GHz") -> " • ✅ 5GHz Wi-Fi + BT (0% Radio Clash)"
                    hasBtOut && wifiBandTag.startsWith("6GHz") -> " • ✅ 6GHz Wi-Fi + BT (0% Radio Clash)"
                    hasBtOut && wifiBandTag.startsWith("2.4GHz") -> " • ⚠️ 2.4GHz Wi-Fi + BT (Use 5GHz if ping spikes)"
                    wifiBandTag.isNotEmpty() -> " • $wifiBandTag"
                    else -> ""
                }

                "🎧 Audio: $routeSummary$volWarning$radioTag"
            } catch (_: Exception) {
                "🎧 Audio Route Ready • 48 kHz SolLuxe™ 3.0 Pure Studio Active"
            }
        }

        private fun resolveTargetMicDevice(context: Context, preferredDeviceId: Int): AudioDeviceInfo? {
            return try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.isSource }
                if (preferredDeviceId != -1) {
                    return inputs.firstOrNull { it.id == preferredDeviceId }
                }

                val wiredOrUsb = inputs.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                }
                if (wiredOrUsb != null) return wiredOrUsb

                val builtInMics = inputs.filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                builtInMics.firstOrNull { dev ->
                    val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dev.address else ""
                    addr.contains("bottom", ignoreCase = true) || addr.contains("primary", ignoreCase = true)
                } ?: builtInMics.firstOrNull()
            } catch (_: Exception) {
                null
            }
        }

        @SuppressLint("MissingPermission")
        private fun buildMicAudioRecord(
            context: Context,
            requestedSource: Int,
            preferredDeviceId: Int,
            enableHwNs: Boolean,
            effectiveSpeakerAec: Boolean = false
        ): OpenedMicSession? {
            val targetDevice = resolveTargetMicDevice(context, preferredDeviceId)
            val isExplicitBluetoothMic = targetDevice != null && (
                targetDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    targetDevice.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                )
            val isExternalWiredOrUsbMic = targetDevice != null && (
                targetDevice.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    targetDevice.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    targetDevice.type == AudioDeviceInfo.TYPE_USB_DEVICE
                )
            val shouldPinDeviceUpfront = (preferredDeviceId != -1) || isExternalWiredOrUsbMic

            val sourcePriority = when {
                effectiveSpeakerAec -> listOf(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.DEFAULT
                )
                requestedSource == 0 && !isExplicitBluetoothMic -> listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.DEFAULT,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                )
                requestedSource == 0 && isExplicitBluetoothMic -> listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.DEFAULT,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                )
                else -> listOf(
                    requestedSource,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.DEFAULT
                ).distinct()
            }

            // Try 48kHz PCM_FLOAT first (zero overhead on modern flagships), then PCM_16BIT & alternate rates
            for (profile in MIC_CAPTURE_PROFILES) {
                val minBuf = AudioRecord.getMinBufferSize(
                    profile.sampleRateHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    profile.encoding
                )
                if (minBuf <= 0) continue

                val bytesPerSample = if (profile.encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                val safeMinBuf = minBuf.coerceAtLeast(profile.sampleRateHz * bytesPerSample / 10)

                for (src in sourcePriority) {
                    var rec: AudioRecord? = null
                    try {
                        val format = AudioFormat.Builder()
                            .setEncoding(profile.encoding)
                            .setSampleRate(profile.sampleRateHz)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()

                        val builder = AudioRecord.Builder()
                            .setAudioSource(src)
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(safeMinBuf * 3)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            builder.setPrivacySensitive(false)
                        }

                        val created = builder.build()
                        rec = created
                        if (created.state == AudioRecord.STATE_INITIALIZED) {
                            if (targetDevice != null && shouldPinDeviceUpfront) {
                                try {
                                    created.setPreferredDevice(targetDevice)
                                } catch (_: Exception) {
                                }
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                targetDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                            ) {
                                try {
                                    created.setPreferredMicrophoneDirection(AudioRecord.MIC_DIRECTION_TOWARDS_USER)
                                } catch (_: Exception) {
                                }
                            }

                            // Always force OEM hardware AGC OFF so it never pumps background noise
                            try {
                                if (AutomaticGainControl.isAvailable()) {
                                    AutomaticGainControl.create(created.audioSessionId)?.let { agc ->
                                        agc.enabled = false
                                        agc.release()
                                    }
                                }
                            } catch (_: Throwable) {
                            }

                            // Explicitly enable or force-disable hardware NoiseSuppressor
                            var attachedNs: NoiseSuppressor? = null
                            try {
                                if (NoiseSuppressor.isAvailable()) {
                                    val ns = NoiseSuppressor.create(created.audioSessionId)
                                    if (ns != null) {
                                        if (enableHwNs) {
                                            ns.enabled = true
                                            if (ns.enabled) attachedNs = ns else ns.release()
                                        } else {
                                            ns.enabled = false
                                            ns.release()
                                        }
                                    }
                                }
                            } catch (_: Throwable) {
                            }

                            // Headphone-Aware AEC Enforcement + AcousticEchoCanceler.getEnabled() verification
                            var attachedAec: AcousticEchoCanceler? = null
                            var actualHwAecEnabled = false
                            try {
                                if (AcousticEchoCanceler.isAvailable()) {
                                    val aec = AcousticEchoCanceler.create(created.audioSessionId)
                                    if (aec != null) {
                                        if (effectiveSpeakerAec) {
                                            aec.enabled = true
                                            actualHwAecEnabled = aec.enabled
                                            if (actualHwAecEnabled) attachedAec = aec else aec.release()
                                        } else {
                                            aec.enabled = false
                                            actualHwAecEnabled = aec.enabled
                                            aec.release()
                                        }
                                    }
                                }
                            } catch (_: Throwable) {
                            }

                            created.startRecording()
                            if (targetDevice != null) {
                                val routed = created.routedDevice
                                val routedToBtCallMic = routed != null && (
                                    routed.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                        routed.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                                    )
                                if (shouldPinDeviceUpfront || (!isExplicitBluetoothMic && routedToBtCallMic)) {
                                    try {
                                        created.setPreferredDevice(targetDevice)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                            return OpenedMicSession(
                                record = created,
                                sampleRateHz = profile.sampleRateHz,
                                encoding = profile.encoding,
                                captureLabel = profile.label,
                                hwNoiseSuppressor = attachedNs,
                                hwEchoCanceler = attachedAec,
                                actualHwAecEnabled = actualHwAecEnabled
                            )
                        } else {
                            created.release()
                        }
                    } catch (_: Exception) {
                        try { rec?.release() } catch (_: Exception) {}
                    }
                }
            }
            return null
        }

        // === 10-SECOND GUIDED CODM GRIP CALIBRATOR & AUTO-ADVISOR (SYNCED WITH SOLLUXE™ 3.0 PURE) ===
        fun recordGuidedTenSecondMicTest(
            context: Context,
            settings: StreamSettings,
            onProgress: (String) -> Unit,
            onComplete: (Boolean, String, MicCalibrationAdvice?) -> Unit
        ) {
            if (SolStreamService.isBroadcastLive || CodecCapabilityProbe.isBroadcastStartingOrLive) {
                onComplete(false, "Cannot run 10s Mic Test while a live broadcast is active.", null)
                return
            }
            if (!isCalibrationRecording.compareAndSet(false, true)) {
                onComplete(false, "10-second voice test is already recording.", null)
                return
            }

            Thread({
                val wantHwNs = settings.noiseSuppressMode.equals("HW_NS", ignoreCase = true) && settings.rnnoiseLevel > 0
                val effectiveAec = computeEffectiveSpeakerAec(context, settings.speakerAecEnabled, settings.micDeviceId)
                val opened = buildMicAudioRecord(
                    context = context,
                    requestedSource = settings.micAudioSource,
                    preferredDeviceId = settings.micDeviceId,
                    enableHwNs = wantHwNs,
                    effectiveSpeakerAec = effectiveAec
                )
                if (opened == null) {
                    isCalibrationRecording.set(false)
                    onComplete(false, "Microphone busy or permission denied.", null)
                    return@Thread
                }

                val hwMode = detectHardwareAcousticMode(
                    context = context,
                    preferredDeviceId = settings.micDeviceId,
                    speakerAecEnabled = settings.speakerAecEnabled,
                    routedDeviceOverride = opened.record.routedDevice
                )
                val dsp = MicDspProcessor(SAMPLE_RATE).apply {
                    platformHwNsAttached = (opened.hwNoiseSuppressor != null)
                    applyHardwareAcousticMode(hwMode)
                    applySettings(settings)
                }

                val outFile = File(context.cacheDir, TEST_PCM_FILENAME)
                val floatBuf = FloatArray(MONO_SAMPLES_PER_10MS)
                val pcmOut = ByteArray(MONO_SAMPLES_PER_10MS * 2)
                val bb = ByteBuffer.wrap(pcmOut).order(ByteOrder.LITTLE_ENDIAN)

                var quietEnergySum = 0.0
                var quietCount = 0
                var speechEnergySum = 0.0
                var speechCount = 0
                var maxSpeechPeakDb = -90.0f
                var sibilanceTriggerFrames = 0

                try {
                    FileOutputStream(outFile).use { fos ->
                        val totalFrames = 1000 // 10.0 seconds (1000 * 10ms)
                        for (frame in 0 until totalFrames) {
                            if (SolStreamService.isBroadcastLive || CodecCapabilityProbe.isBroadcastStartingOrLive) break
                            if (frame % 100 == 0) {
                                val secLeft = 10 - (frame / 100)
                                val phaseInstruction = if (frame < 300) {
                                    "Phase 1/2 (${secLeft}s) [${hwMode.title}]: Hold phone in your 2-hand grip & STAY SILENT..."
                                } else {
                                    "Phase 2/2 (${secLeft}s) [${settings.voiceGenderProfile}]: Speak normal & loud callouts ('Enemy B', 'Rush mid')!"
                                }
                                onProgress(phaseInstruction)
                            }

                            val r = opened.readMono48kFrame(floatBuf)
                            if (r > 0) {
                                dsp.processMonoFrameInPlace(floatBuf, MONO_SAMPLES_PER_10MS)

                                val frameRmsLin = MicDspProcessor.dbToLinear(dsp.lastRmsDb).toDouble()
                                val framePeakDb = dsp.lastPeakDb
                                if (frame in 30 until 290) {
                                    quietEnergySum += frameRmsLin * frameRmsLin
                                    quietCount++
                                } else if (frame >= 310 && dsp.vadSpeechActive) {
                                    speechEnergySum += frameRmsLin * frameRmsLin
                                    speechCount++
                                    if (framePeakDb > maxSpeechPeakDb) maxSpeechPeakDb = framePeakDb
                                    if (framePeakDb > -5.5f) sibilanceTriggerFrames++
                                }

                                bb.clear()
                                for (i in 0 until MONO_SAMPLES_PER_10MS) {
                                    val s = (floatBuf[i].coerceIn(-0.999f, 0.999f) * 32767.0f).roundToInt().toShort()
                                    bb.putShort(s)
                                }
                                fos.write(pcmOut)
                            }
                        }
                    }

                    val avgQuietDb = if (quietCount > 0) {
                        MicDspProcessor.linearToDb(sqrt(quietEnergySum / quietCount).toFloat()).roundToInt()
                    } else {
                        -62
                    }
                    val avgSpeechDb = if (speechCount > 0) {
                        MicDspProcessor.linearToDb(sqrt(speechEnergySum / speechCount).toFloat()).roundToInt()
                    } else {
                        -20
                    }
                    val peakDb = maxSpeechPeakDb.roundToInt()

                    val recGain = when {
                        speechCount < 15 -> settings.micGainPercent
                        avgSpeechDb < -25 -> min(220, settings.micGainPercent + 40)
                        avgSpeechDb < -20 -> min(190, settings.micGainPercent + 20)
                        avgSpeechDb > -10 || peakDb >= -1 -> max(80, settings.micGainPercent - 20)
                        else -> settings.micGainPercent
                    }
                    val baseGenderHpf = KeystoreVault.resolveDefaultHpfForProfile(settings.voiceGenderProfile)
                    val recHpf = if (avgQuietDb > -44) {
                        (baseGenderHpf + 15).coerceAtMost(105)
                    } else {
                        baseGenderHpf
                    }
                    val recNsLvl = when {
                        avgQuietDb > -42 -> 3
                        avgQuietDb > -54 -> 2
                        else -> 2
                    }
                    val recPreset = if (settings.voicePreset == "RAW") "SOL_LUXE" else settings.voicePreset
                    val recDeEss = sibilanceTriggerFrames >= 4 || settings.deEsserEnabled

                    val summary = buildString {
                        append("10s Studio Test Done! (${hwMode.title} • ${settings.voiceGenderProfile} • ${opened.captureLabel})\n")
                        append("Room Floor: ${avgQuietDb}dB | Voice Avg: ${avgSpeechDb}dB (Peak ${peakDb}dB)\n")
                        append("-> Advisor Recommends: Mic Boost ${recGain}% • HPF ${recHpf}Hz • C RNNoise Lv$recNsLvl • ${MicDspProcessor.presetDisplayName(recPreset)}")
                    }

                    val advice = MicCalibrationAdvice(
                        measuredNoiseFloorDb = avgQuietDb,
                        measuredSpeechRmsDb = avgSpeechDb,
                        measuredPeakDb = peakDb,
                        recommendedGainPercent = recGain,
                        recommendedHpfHz = recHpf,
                        recommendedNoiseMode = "RNNOISE",
                        recommendedNoiseLevel = recNsLvl,
                        recommendedPreset = recPreset,
                        recommendDeEsser = recDeEss,
                        recommendSlowAgc = true,
                        plainSummary = summary
                    )
                    lastCalibrationAdvice = advice
                    onComplete(true, summary, advice)
                } catch (e: Exception) {
                    onComplete(false, "Test failed: ${e.javaClass.simpleName}", null)
                } finally {
                    isCalibrationRecording.set(false)
                    dsp.release()
                    opened.release()
                }
            }, "SolMicGripCalibrator").start()
        }

        fun playRecordedMicTest(context: Context, onComplete: (String) -> Unit) {
            if (isCalibrationRecording.get()) {
                onComplete("Wait for the 10-second recording to finish first.")
                return
            }
            if (!isCalibrationPlaying.compareAndSet(false, true)) {
                onComplete("Test sample is already playing.")
                return
            }

            Thread({
                val inFile = File(context.cacheDir, TEST_PCM_FILENAME)
                if (!inFile.exists() || inFile.length() == 0L) {
                    isCalibrationPlaying.set(false)
                    onComplete("Record a 10s Voice Test sample first.")
                    return@Thread
                }

                var track: AudioTrack? = null
                try {
                    val minBuf = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(4096)

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
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(minBuf * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    track.play()
                    val buf = ByteArray(4096)
                    FileInputStream(inFile).use { fis ->
                        while (true) {
                            if (SolStreamService.isBroadcastLive || CodecCapabilityProbe.isBroadcastStartingOrLive) break
                            val r = fis.read(buf)
                            if (r <= 0) break
                            track.write(buf, 0, r)
                        }
                    }
                    onComplete("Playback complete! Tap 'Apply Advisor' if you want the recommended gain.")
                } catch (e: Exception) {
                    onComplete("Playback error: ${e.javaClass.simpleName}")
                } finally {
                    isCalibrationPlaying.set(false)
                    try { track?.stop(); track?.release() } catch (_: Exception) {}
                }
            }, "SolMicTestPlay").start()
        }

        fun deleteMicTestFile(context: Context) {
            try {
                val f = File(context.cacheDir, TEST_PCM_FILENAME)
                if (f.exists()) f.delete()
            } catch (_: Exception) {
            }
        }
    }
}
