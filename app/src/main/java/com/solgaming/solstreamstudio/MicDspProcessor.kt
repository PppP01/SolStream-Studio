/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 4-Hardware Acoustic Adaptation Modes (12 combinations across Male / Neutral / Female):
 * Automatically adapts HPF shield, proximity chest contour, mud cleanup, anti-nasal dip,
 * Neve-style presence sheen, clean air shelf / Bluetooth >5.2 kHz hiss LPF, and
 * double-talk-safe speakerphone echo-guard across all 3 gender profiles.
 */
enum class HardwareAcousticMode(val title: String) {
    PHONE_STUDIO("48kHz Phone Studio Mic (Hand-Grip Tuned)"),
    WIRED_USB_CLOSE("48kHz Wired / USB-C Close-Talk Mic"),
    BLUETOOTH_SCO("Bluetooth Collar / Earbud Mic (Anti-Hiss)"),
    SPEAKER_AEC("Phone Speakerphone Mode (Echo-Guard)")
}

class MicDspProcessor(private val sampleRate: Int = 48000) {

    /**
     * Immutable atomic configuration snapshot.
     * UI / service threads publish a new snapshot via [config]; the real-time audio worker reads
     * a single volatile reference at the top of each 10 ms frame and updates all [Biquad64]
     * coefficients exclusively on the audio thread — zero lock contention, zero mid-frame races.
     */
    private data class DspConfigSnapshot(
        val voiceGenderProfile: String = "NEUTRAL",
        val voicePreset: String = "SOL_LUXE",
        val noiseSuppressMode: String = "RNNOISE",
        val noiseSuppressLevel: Int = 2,
        val hpfCutoffHz: Int = 68,
        val hpfAutoMode: Boolean = true,
        val transientTamerEnabled: Boolean = true,
        val gateEnabled: Boolean = true,
        val eqEnabled: Boolean = true,
        val velvetWarmthEnabled: Boolean = true,
        val compressorEnabled: Boolean = true,
        val whisperLiftEnabled: Boolean = true,
        val slowAgcEnabled: Boolean = true,
        val deEsserEnabled: Boolean = true,
        val micGainLinear: Float = 1.30f,
        val hardwareAcousticMode: HardwareAcousticMode = HardwareAcousticMode.PHONE_STUDIO
    )

    private val configWriteLock = Any()
    @Volatile private var config = DspConfigSnapshot()

    // Public configuration accessors (backed atomically by immutable DspConfigSnapshot)
    var voiceGenderProfile: String
        get() = config.voiceGenderProfile
        set(value) {
            val norm = normalizeGender(value)
            synchronized(configWriteLock) { config = config.copy(voiceGenderProfile = norm) }
        }

    var voicePreset: String
        get() = config.voicePreset
        set(value) {
            val norm = value.uppercase(Locale.US)
            val updated = synchronized(configWriteLock) {
                config.copy(voicePreset = norm).also { config = it }
            }
            refreshDenoiserSummary(updated)
        }

    var noiseSuppressMode: String
        get() = config.noiseSuppressMode
        set(value) {
            val norm = normalizeNoiseMode(value)
            val updated = synchronized(configWriteLock) {
                config.copy(noiseSuppressMode = norm).also { config = it }
            }
            refreshDenoiserSummary(updated)
        }

    var noiseSuppressLevel: Int
        get() = config.noiseSuppressLevel
        set(value) {
            val clamped = value.coerceIn(0, 3)
            val updated = synchronized(configWriteLock) {
                config.copy(noiseSuppressLevel = clamped).also { config = it }
            }
            refreshDenoiserSummary(updated)
        }

    var hpfCutoffHz: Int
        get() = config.hpfCutoffHz
        set(value) {
            val clamped = value.coerceIn(0, 160)
            synchronized(configWriteLock) { config = config.copy(hpfCutoffHz = clamped) }
        }

    var hpfAutoMode: Boolean
        get() = config.hpfAutoMode
        set(value) {
            synchronized(configWriteLock) { config = config.copy(hpfAutoMode = value) }
        }

    var transientTamerEnabled: Boolean
        get() = config.transientTamerEnabled
        set(value) {
            synchronized(configWriteLock) { config = config.copy(transientTamerEnabled = value) }
        }

    var gateEnabled: Boolean
        get() = config.gateEnabled
        set(value) {
            synchronized(configWriteLock) { config = config.copy(gateEnabled = value) }
        }

    var eqEnabled: Boolean
        get() = config.eqEnabled
        set(value) {
            synchronized(configWriteLock) { config = config.copy(eqEnabled = value) }
        }

    var velvetWarmthEnabled: Boolean
        get() = config.velvetWarmthEnabled
        set(value) {
            synchronized(configWriteLock) { config = config.copy(velvetWarmthEnabled = value) }
        }

    // Legacy compatibility property (Stage 10 tone balancer removed in SolLuxe™ 3.0 Pure)
    @Volatile var aiToneBalancerEnabled: Boolean = false

    var compressorEnabled: Boolean
        get() = config.compressorEnabled
        set(value) {
            synchronized(configWriteLock) { config = config.copy(compressorEnabled = value) }
        }

    var whisperLiftEnabled: Boolean
        get() = config.whisperLiftEnabled
        set(value) {
            synchronized(configWriteLock) { config = config.copy(whisperLiftEnabled = value) }
        }

    var slowAgcEnabled: Boolean
        get() = config.slowAgcEnabled
        set(value) {
            synchronized(configWriteLock) { config = config.copy(slowAgcEnabled = value) }
        }

    var deEsserEnabled: Boolean
        get() = config.deEsserEnabled
        set(value) {
            synchronized(configWriteLock) { config = config.copy(deEsserEnabled = value) }
        }

    var micGainLinear: Float
        get() = config.micGainLinear
        set(value) {
            val clamped = value.coerceIn(0.50f, 2.50f)
            synchronized(configWriteLock) { config = config.copy(micGainLinear = clamped) }
        }

    var hardwareAcousticMode: HardwareAcousticMode
        get() = config.hardwareAcousticMode
        set(value) {
            synchronized(configWriteLock) { config = config.copy(hardwareAcousticMode = value) }
        }

    // High-frequency real-time flags (zero-allocation primitives updated on audio/control paths)
    @Volatile var streamMicMuted: Boolean = false
    @Volatile var platformHwNsAttached: Boolean = false
        set(value) {
            field = value
            refreshDenoiserSummary(config)
        }
    @Volatile var speakerBleedLevel: Float = 0.0f

    // Live telemetry for UI, Guided 10s Voice Test, AI Auto-Clutch & Diagnostics
    @Volatile var vadSpeechActive: Boolean = false
    @Volatile var excitedCalloutActive: Boolean = false
    @Volatile var lastVadProbability: Float = 0f
    @Volatile var lastPeakDb: Float = -90f
    @Volatile var lastRmsDb: Float = -90f
    @Volatile var lastNoiseFloorDb: Float = -70f
    @Volatile var lastGainReductionDb: Float = 0f
    @Volatile var clipCount: Int = 0
    @Volatile var activeDenoiserSummary: String = "C RNNoise (Natural 88%)"
    @Volatile var cpuEcoBypassActive: Boolean = false

    // 64-bit IEEE-754 Double internal biquad states (>1,500 dB headroom, zero denormal stalls)
    private class Biquad64 {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
        var a1 = 0.0; var a2 = 0.0
        var z1 = 0.0; var z2 = 0.0

        fun process(x: Float): Float {
            val xd = x.toDouble()
            var y = b0 * xd + z1
            if (!y.isFinite() || abs(y) < 1e-24) y = 0.0
            z1 = b1 * xd - a1 * y + z2
            z2 = b2 * xd - a2 * y
            if (!z1.isFinite() || abs(z1) < 1e-24) z1 = 0.0
            if (!z2.isFinite() || abs(z2) < 1e-24) z2 = 0.0
            return y.toFloat()
        }

        fun setPassthrough() {
            b0 = 1.0; b1 = 0.0; b2 = 0.0
            a1 = 0.0; a2 = 0.0
        }

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }
    }

    // Audio-thread-exclusive 64-bit filter bank (NEVER mutated from UI thread after init)
    private val hpfFilter = Biquad64()              // Stage 1: 2nd-Order Butterworth HPF (55/68/85 Hz + HW shield)
    private val speechBandFilter = Biquad64()       // Stage 3: 200-2500 Hz 16kHz-safe Hybrid VAD energy monitor
    private val vocalCoreFilter = Biquad64()        // Stage 4 & 6: 200-1200 Hz vocal-core detector (Plosive Guard & De-Esser ratio)
    private val eqLowWarmth = Biquad64()            // Stage 5 Band 1: Chest Warmth (Q=0.85 M/N, Q=1.0 F)
    private val eqMudCut = Biquad64()               // Stage 5 Band 2: Surgical Mud Cleanup (Q=2.0)
    private val eqAntiNasal = Biquad64()            // Stage 5 Band 3: Anti-Nasal Dip (Q=1.8)
    private val eqPresence = Biquad64()             // Stage 5 Band 4: Neve Silk Presence / Radio Studio Presence (Q=1.15)
    private val eqAirOrBtLpf = Biquad64()           // Stage 5 Band 5: Clean Air Shelf (8.5/9.0 kHz) or BT SCO 5.2 kHz LPF
    private val lowWarmthExtractor = Biquad64()     // Stage 5b: Isolated Low-Band Extractor for Analog Warmth Shaper
    private val lowWarmthSmoother = Biquad64()      // Stage 5b: Butterworth Smoother for Low-Band Cubic Warmth
    private val levelerSidechainHpf = Biquad64()    // Stage 6a: Gender-Matched Leveler Sidechain HPF (110/140/180 Hz)
    private val sibilanceBand = Biquad64()          // Stage 6b: Post-EQ/Post-Comp Dynamic De-Esser Band (6.2/6.7/7.2 kHz, Q=2.0)

    // Exact sample-rate-calibrated time constants (at 48 kHz: 8ms=0.00260, 220ms=0.000095, 10ms=0.00208, 130ms=0.000160)
    private val rms20msAlpha = timeConstAlpha(0.020)
    private val maxImpulseSamples = max(1, (sampleRate * 0.0006).toInt())   // <0.6 ms (29 samples @ 48 kHz)
    private val impulseRecoverySamples = max(8, (sampleRate * 0.0020).toInt()) // 2.0 ms (96 samples @ 48 kHz)
    private val expAttackAlpha = timeConstAlpha(0.008)      // 8.0 ms smooth expander attack
    private val expReleaseAlpha = timeConstAlpha(0.220)     // 220 ms smooth expander release
    private val expHoldSamples = (sampleRate * 0.140).toInt() // 140 ms hold (6,720 samples @ 48 kHz)
    private val plosiveLp115Alpha = (1.0 - exp(-2.0 * PI * 115.0 / sampleRate.toDouble())).toFloat()
    private val levelerAttackAlpha = timeConstAlpha(0.010)  // 10.0 ms soft-knee leveler attack
    private val levelerReleaseAlpha = timeConstAlpha(0.130) // 130 ms soft-knee leveler release
    private val levelerGainSmoothAlpha = timeConstAlpha(0.004)
    private val deEssAttackAlpha = timeConstAlpha(0.005)    // 5 ms de-esser attack
    private val deEssReleaseAlpha = timeConstAlpha(0.055)   // 55 ms de-esser release (40-80 ms reaction)
    private val limiterReleaseAlpha = timeConstAlpha(0.045) // 45 ms true-peak limiter release

    private val nativeLock = Any()
    @Volatile private var nativeRnnHandle: Long = 0L
    @Volatile private var released: Boolean = false

    // Audio-thread-private applied filter signatures (never read/written by UI thread)
    private var appliedEffectiveHpfHz = -1
    private var appliedGender = ""
    private var appliedHwMode: HardwareAcousticMode? = null
    private var appliedRadioStudioMode = false

    // Stage 1 / Mute & Stage 2 (Raised-Cosine Micro-Impulse Clamper) state
    private var muteSmoothGain = 1.0f
    private var prevClampSample = 0.0f
    private var localEnergy20ms = 0.00001f
    private var impulseStreakSamples = 0
    private var impulseClampGain = 1.0f
    private var impulseRecoveryStartGain = 1.0f
    private var impulseRecoveryPos = 0

    // Stage 3 (RNNoise + Hybrid Neural-VAD Smooth Soft-Floor Expander) state
    private var noiseFloorEnv = 0.0016f
    private var speechBandNoiseFloor = 0.0012f
    private var expanderGain = 1.0f
    private var expanderHoldRemaining = 0
    private var suppressSmoothGain = 1.0f
    private var lpNoiseState = 0.0f

    // Stage 4 (Split-Band Dynamic Plosive Guard <115 Hz vs 200-1200 Hz) state
    private var plosiveLpState = 0.0f
    private var windLowEnv = 0.0005f
    private var vocalCoreEnv = 0.0015f
    private var plosiveDuckSmooth = 0.0f

    // Stage 5b (Low-Band Analog Warmth Shaper) state
    private var subCubicDrive = 0.08f
    private var tube2fDrive = 0.0f
    private var tubeDcState = 0.0f
    private var isBluetoothLpfActive = false

    // Stage 6 (VAD-Synced Soft-Knee Leveler + Dynamic Sibilance-Ratio De-Esser + 4x Catmull-Rom Limiter) state
    private var levelerEnv = 0.0f
    private var targetLevelerBlockGain = 1.0f
    private var levelerGainSmooth = 1.0f
    private var slowAgcGain = 1.0f
    private var sibilanceEnv = 0.0f
    private var deEssAttenSmooth = 0.0f

    // 4-sample lookahead shift register for 4x Catmull-Rom True-Peak Limiter (-1.16 dBTP = 0.875)
    private var limX0 = 0.0f
    private var limX1 = 0.0f
    private var limX2 = 0.0f
    private var limGain = 1.0f

    // Low-end CPU budget watchdog (protects entry devices under heavy thermal throttling)
    private var slowFrameStrikeCount = 0
    private var ecoBypassRemainingFrames = 0

    init {
        // 200-2500 Hz speech band: f0 = sqrt(200 * 2500) = 707.1 Hz, Q = 707.1 / 2300 = 0.3074
        configureBandPass(speechBandFilter, 707.1, 0.3074)
        // 200-1200 Hz vocal core: f0 = sqrt(200 * 1200) = 489.9 Hz, Q = 489.9 / 1000 = 0.4899
        configureBandPass(vocalCoreFilter, 489.9, 0.4899)
        val initial = config
        syncAudioThreadFilters(initial)
        refreshDenoiserSummary(initial)
    }

    private fun timeConstAlpha(seconds: Double): Float {
        if (seconds <= 0.0) return 1.0f
        return (1.0 - exp(-1.0 / (sampleRate.toDouble() * seconds))).toFloat().coerceIn(1e-6f, 1.0f)
    }

    /**
     * Thread-safe hardware acoustic mode update.
     * Publishes atomically; audio thread adopts the new 4x3 filter bank cleanly at the next 10 ms frame boundary.
     */
    fun applyHardwareAcousticMode(mode: HardwareAcousticMode) {
        synchronized(configWriteLock) {
            if (config.hardwareAcousticMode != mode) {
                config = config.copy(hardwareAcousticMode = mode)
            }
        }
    }

    /**
     * Thread-safe live settings update from UI or Floating Pill.
     * Publishes one immutable snapshot atomically; never mutates [Biquad64] fields on the UI thread.
     */
    fun applySettings(s: StreamSettings) {
        val normGender = normalizeGender(s.voiceGenderProfile)
        val normMode = normalizeNoiseMode(s.noiseSuppressMode)
        val presetUpper = s.voicePreset.uppercase(Locale.US)

        val updated = synchronized(configWriteLock) {
            val hw = config.hardwareAcousticMode
            DspConfigSnapshot(
                voiceGenderProfile = normGender,
                voicePreset = presetUpper,
                noiseSuppressMode = normMode,
                noiseSuppressLevel = s.rnnoiseLevel.coerceIn(0, 3),
                hpfCutoffHz = s.hpfCutoffHz.coerceIn(0, 160),
                hpfAutoMode = s.hpfAutoMode,
                transientTamerEnabled = s.transientTamerEnabled,
                gateEnabled = s.noiseGateEnabled,
                eqEnabled = s.voiceEqEnabled,
                velvetWarmthEnabled = s.velvetWarmthEnabled,
                compressorEnabled = s.compressorEnabled,
                whisperLiftEnabled = s.whisperLiftEnabled,
                slowAgcEnabled = s.slowAgcEnabled,
                deEsserEnabled = s.deEsserEnabled,
                micGainLinear = (s.micGainPercent.coerceIn(50, 250)) / 100.0f,
                hardwareAcousticMode = hw
            ).also { config = it }
        }
        refreshDenoiserSummary(updated)
    }

    private fun refreshDenoiserSummary(cfg: DspConfigSnapshot) {
        if (cfg.voicePreset == "RAW") {
            activeDenoiserSummary = "Bypassed (RAW Mode)"
            return
        }
        val lvl = cfg.noiseSuppressLevel
        val mode = cfg.noiseSuppressMode
        activeDenoiserSummary = when {
            mode == "OFF" || lvl <= 0 -> "OFF"
            mode == "HW_NS" && platformHwNsAttached -> "Chip HW NS"
            mode == "RNNOISE" && SolNativeRnnoise.isLibraryLoaded -> {
                val tag = when (lvl) {
                    1 -> "Gentle 65%"
                    2 -> "Natural 88%"
                    else -> "Max 100%"
                }
                if (SolNativeRnnoise.isNeuralModelReady) "C RNNoise ($tag)" else "C Bark-MMSE ($tag)"
            }
            else -> "Light Cut (Lv$lvl)"
        }
    }

    private fun runNativeRnnoiseLocked(buffer: FloatArray, wetStrength: Float): Float {
        if (!SolNativeRnnoise.isLibraryLoaded) return -1.0f
        synchronized(nativeLock) {
            if (released) return -1.0f
            if (nativeRnnHandle == 0L) {
                nativeRnnHandle = SolNativeRnnoise.createInstance()
            }
            val handle = nativeRnnHandle
            if (handle == 0L) return -1.0f
            return SolNativeRnnoise.processMono480InPlace(handle, buffer, wetStrength)
        }
    }

    fun release() {
        synchronized(nativeLock) {
            released = true
            val h = nativeRnnHandle
            nativeRnnHandle = 0L
            if (h != 0L) {
                SolNativeRnnoise.destroyInstance(h)
            }
        }
    }

    /**
     * Called strictly on the audio thread at the start of [processMonoFrameInPlace] (and once in init).
     */
    private fun syncAudioThreadFilters(cfg: DspConfigSnapshot) {
        updateHpfOnAudioThread(cfg.hpfCutoffHz, cfg.hpfAutoMode, cfg.hardwareAcousticMode)
        updateAcousticMatrixOnAudioThread(
            gender = cfg.voiceGenderProfile,
            hwMode = cfg.hardwareAcousticMode,
            isRadioStudioPreset = (cfg.voicePreset == "STUDIO")
        )
    }

    private fun updateHpfOnAudioThread(
        baseCutoffHz: Int,
        autoMode: Boolean,
        hwMode: HardwareAcousticMode
    ) {
        val effectiveHz = if (baseCutoffHz <= 0) {
            0
        } else if (!autoMode) {
            // Preserve exact explicit manual HPF cutoff across hardware & preset switches
            baseCutoffHz.coerceIn(30, 160)
        } else {
            val offset = when (hwMode) {
                HardwareAcousticMode.PHONE_STUDIO -> 0
                HardwareAcousticMode.WIRED_USB_CLOSE -> 5
                HardwareAcousticMode.BLUETOOTH_SCO -> 10
                HardwareAcousticMode.SPEAKER_AEC -> 15
            }
            (baseCutoffHz + offset).coerceIn(40, 150)
        }
        if (effectiveHz == appliedEffectiveHpfHz) return
        appliedEffectiveHpfHz = effectiveHz
        if (effectiveHz <= 0) {
            hpfFilter.setPassthrough()
            hpfFilter.reset()
            return
        }
        configureButterworthHpf(hpfFilter, effectiveHz.toDouble())
    }

    private fun configureButterworthHpf(target: Biquad64, freqHz: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / sqrt(2.0)
        val a0 = 1.0 + alpha
        if (a0 <= 1e-9) return
        target.b0 = ((1.0 + cosW) * 0.5) / a0
        target.b1 = -(1.0 + cosW) / a0
        target.b2 = ((1.0 + cosW) * 0.5) / a0
        target.a1 = (-2.0 * cosW) / a0
        target.a2 = (1.0 - alpha) / a0
    }

    private fun configureLowPass(target: Biquad64, freqHz: Double, q: Double = 0.70710678) {
        val w0 = 2.0 * PI * freqHz / sampleRate.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        if (a0 <= 1e-9) return
        target.b0 = ((1.0 - cosW) * 0.5) / a0
        target.b1 = (1.0 - cosW) / a0
        target.b2 = ((1.0 - cosW) * 0.5) / a0
        target.a1 = (-2.0 * cosW) / a0
        target.a2 = (1.0 - alpha) / a0
    }

    private fun configurePeakingEq(target: Biquad64, freqHz: Double, q: Double, gainDb: Double) {
        if (abs(gainDb) < 0.05) {
            target.setPassthrough()
            return
        }
        val amp = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRate.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha / amp
        if (a0 <= 1e-9) return
        target.b0 = (1.0 + alpha * amp) / a0
        target.b1 = (-2.0 * cosW) / a0
        target.b2 = (1.0 - alpha * amp) / a0
        target.a1 = (-2.0 * cosW) / a0
        target.a2 = (1.0 - alpha / amp) / a0
    }

    private fun configureHighShelf(target: Biquad64, freqHz: Double, gainDb: Double) {
        if (abs(gainDb) < 0.05) {
            target.setPassthrough()
            return
        }
        val amp = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRate.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(amp) * alpha
        val a0 = (amp + 1.0) - (amp - 1.0) * cosW + twoSqrtAAlpha
        if (a0 <= 1e-9) return
        target.b0 = (amp * ((amp + 1.0) + (amp - 1.0) * cosW + twoSqrtAAlpha)) / a0
        target.b1 = (-2.0 * amp * ((amp - 1.0) + (amp + 1.0) * cosW)) / a0
        target.b2 = (amp * ((amp + 1.0) + (amp - 1.0) * cosW - twoSqrtAAlpha)) / a0
        target.a1 = (2.0 * ((amp - 1.0) - (amp + 1.0) * cosW)) / a0
        target.a2 = ((amp + 1.0) - (amp - 1.0) * cosW - twoSqrtAAlpha) / a0
    }

    private fun configureBandPass(target: Biquad64, freqHz: Double, q: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        if (a0 <= 1e-9) return
        target.b0 = alpha / a0
        target.b1 = 0.0
        target.b2 = -alpha / a0
        target.a1 = (-2.0 * cosW) / a0
        target.a2 = (1.0 - alpha) / a0
    }

    /**
     * SolLuxe™ 3.0 Pure 4-Hardware × 3-Gender Condenser Matrix (Zero Band Overlap):
     * - Band 1 Chest Warmth (Q=0.85 M/N, Q=1.0 F)
     * - Band 2 Surgical Mud Cleanup (Q=2.0)
     * - Band 3 Anti-Nasal Dip (Q=1.8)
     * - Band 4 Neve Silk Presence (Q=1.15) or Radio Studio cut-through (2.9/3.2/3.5 kHz @ +1.6 dB)
     * - Band 5 Clean Air Shelf (8.5/9.0 kHz) or 5.2 kHz Bluetooth SCO Hiss LPF
     * - Stage 6 Gender-Matched Leveler Sidechain HPF (110/140/180 Hz) & Separated De-Esser (6.2/6.7/7.2 kHz)
     */
    private fun updateAcousticMatrixOnAudioThread(
        gender: String,
        hwMode: HardwareAcousticMode,
        isRadioStudioPreset: Boolean
    ) {
        if (gender == appliedGender &&
            hwMode == appliedHwMode &&
            isRadioStudioPreset == appliedRadioStudioMode
        ) {
            return
        }
        appliedGender = gender
        appliedHwMode = hwMode
        appliedRadioStudioMode = isRadioStudioPreset

        // Gender-matched Leveler HPF sidechain & Low-Band Analog Warmth Shaper parameters
        when (gender) {
            "MALE" -> {
                subCubicDrive = 0.09f
                tube2fDrive = 0.06f
                configureLowPass(lowWarmthExtractor, 150.0, 0.70710678)
                configureLowPass(lowWarmthSmoother, 150.0, 0.70710678)
                configureButterworthHpf(levelerSidechainHpf, 110.0)
                configureBandPass(
                    sibilanceBand,
                    if (hwMode == HardwareAcousticMode.BLUETOOTH_SCO) 4600.0 else 6200.0,
                    2.0
                )
            }
            "FEMALE" -> {
                subCubicDrive = 0.06f
                tube2fDrive = 0.0f // Zero 340 Hz mud on Female
                configureLowPass(lowWarmthExtractor, 205.0, 0.70710678)
                configureLowPass(lowWarmthSmoother, 205.0, 0.70710678)
                configureButterworthHpf(levelerSidechainHpf, 180.0)
                configureBandPass(
                    sibilanceBand,
                    if (hwMode == HardwareAcousticMode.BLUETOOTH_SCO) 5000.0 else 7200.0,
                    2.0
                )
            }
            else -> {
                subCubicDrive = 0.08f
                tube2fDrive = 0.0f // Zero 256 Hz mud on Neutral
                configureLowPass(lowWarmthExtractor, 170.0, 0.70710678)
                configureLowPass(lowWarmthSmoother, 170.0, 0.70710678)
                configureButterworthHpf(levelerSidechainHpf, 140.0)
                configureBandPass(
                    sibilanceBand,
                    if (hwMode == HardwareAcousticMode.BLUETOOTH_SCO) 4800.0 else 6700.0,
                    2.0
                )
            }
        }

        isBluetoothLpfActive = (hwMode == HardwareAcousticMode.BLUETOOTH_SCO)

        when (hwMode) {
            HardwareAcousticMode.PHONE_STUDIO -> {
                configureHighShelf(eqAirOrBtLpf, 8500.0, 1.8)
                configurePeakingEq(eqAntiNasal, 920.0, 1.8, -1.0)
                when (gender) {
                    "MALE" -> {
                        configurePeakingEq(eqLowWarmth, 95.0, 0.85, 3.4)
                        configurePeakingEq(eqMudCut, 315.0, 2.0, -1.8)
                        configurePeakingEq(
                            eqPresence,
                            if (isRadioStudioPreset) 2900.0 else 4500.0,
                            1.15,
                            if (isRadioStudioPreset) 1.6 else 1.2
                        )
                    }
                    "FEMALE" -> {
                        configurePeakingEq(eqLowWarmth, 172.0, 1.0, 2.2)
                        configurePeakingEq(eqMudCut, 365.0, 2.0, -1.6)
                        configurePeakingEq(
                            eqPresence,
                            if (isRadioStudioPreset) 3500.0 else 5100.0,
                            1.15,
                            if (isRadioStudioPreset) 1.6 else 1.4
                        )
                    }
                    else -> {
                        configurePeakingEq(eqLowWarmth, 130.0, 0.85, 2.6)
                        configurePeakingEq(eqMudCut, 340.0, 2.0, -1.8)
                        configurePeakingEq(
                            eqPresence,
                            if (isRadioStudioPreset) 3200.0 else 4800.0,
                            1.15,
                            if (isRadioStudioPreset) 1.6 else 1.3
                        )
                    }
                }
            }

            HardwareAcousticMode.WIRED_USB_CLOSE -> {
                configureHighShelf(eqAirOrBtLpf, 9000.0, 1.5)
                configurePeakingEq(eqAntiNasal, 920.0, 1.8, -0.8)
                when (gender) {
                    "MALE" -> {
                        configurePeakingEq(eqLowWarmth, 95.0, 0.85, 1.6)
                        configurePeakingEq(eqMudCut, 280.0, 2.0, -2.2)
                        configurePeakingEq(
                            eqPresence,
                            if (isRadioStudioPreset) 2900.0 else 4600.0,
                            1.15,
                            if (isRadioStudioPreset) 1.6 else 1.0
                        )
                    }
                    "FEMALE" -> {
                        configurePeakingEq(eqLowWarmth, 172.0, 1.0, 1.0)
                        configurePeakingEq(eqMudCut, 355.0, 2.0, -1.8)
                        configurePeakingEq(
                            eqPresence,
                            if (isRadioStudioPreset) 3500.0 else 5200.0,
                            1.15,
                            if (isRadioStudioPreset) 1.6 else 1.2
                        )
                    }
                    else -> {
                        configurePeakingEq(eqLowWarmth, 130.0, 0.85, 1.2)
                        configurePeakingEq(eqMudCut, 310.0, 2.0, -2.0)
                        configurePeakingEq(
                            eqPresence,
                            if (isRadioStudioPreset) 3200.0 else 4900.0,
                            1.15,
                            if (isRadioStudioPreset) 1.6 else 1.1
                        )
                    }
                }
            }

            HardwareAcousticMode.BLUETOOTH_SCO -> {
                // Real 5.2 kHz 2nd-order Butterworth LPF blocks Bluetooth HFP hiss cleanly
                configureLowPass(eqAirOrBtLpf, 5200.0, 0.70710678)
                configurePeakingEq(eqMudCut, 330.0, 2.0, -1.5)
                configurePeakingEq(eqAntiNasal, 900.0, 1.8, -0.8)
                when (gender) {
                    "MALE" -> {
                        configurePeakingEq(eqLowWarmth, 110.0, 0.85, 2.2)
                        configurePeakingEq(eqPresence, 2500.0, 1.15, 1.8)
                    }
                    "FEMALE" -> {
                        configurePeakingEq(eqLowWarmth, 175.0, 1.0, 1.5)
                        configurePeakingEq(eqPresence, 2900.0, 1.15, 1.8)
                    }
                    else -> {
                        configurePeakingEq(eqLowWarmth, 140.0, 0.85, 1.8)
                        configurePeakingEq(eqPresence, 2700.0, 1.15, 1.8)
                    }
                }
            }

            HardwareAcousticMode.SPEAKER_AEC -> {
                configureHighShelf(eqAirOrBtLpf, 8500.0, 0.8)
                configurePeakingEq(eqMudCut, 340.0, 2.0, -2.2)
                configurePeakingEq(eqAntiNasal, 950.0, 1.8, -1.2)
                when (gender) {
                    "MALE" -> {
                        configurePeakingEq(eqLowWarmth, 105.0, 0.85, 2.0)
                        configurePeakingEq(
                            eqPresence,
                            if (isRadioStudioPreset) 2900.0 else 4200.0,
                            1.15,
                            if (isRadioStudioPreset) 1.6 else 1.5
                        )
                    }
                    "FEMALE" -> {
                        configurePeakingEq(eqLowWarmth, 175.0, 1.0, 1.4)
                        configurePeakingEq(
                            eqPresence,
                            if (isRadioStudioPreset) 3500.0 else 4800.0,
                            1.15,
                            if (isRadioStudioPreset) 1.6 else 1.5
                        )
                    }
                    else -> {
                        configurePeakingEq(eqLowWarmth, 135.0, 0.85, 1.6)
                        configurePeakingEq(
                            eqPresence,
                            if (isRadioStudioPreset) 3200.0 else 4500.0,
                            1.15,
                            if (isRadioStudioPreset) 1.6 else 1.5
                        )
                    }
                }
            }
        }
    }

    /**
     * Zero-allocation 4× Catmull-Rom Cubic Inter-Sample True-Peak Limiter (-1.16 dBTP = 0.875).
     * Evaluates inter-sample peaks at t = 0.25, 0.50, 0.75 across a 4-sample window (1-sample latency)
     * so AAC encoding never clips even on hot post-DSP mic boost settings.
     */
    private fun processTruePeakLimiterSample(inSample: Float): Float {
        val x0 = limX0
        val x1 = limX1
        val x2 = limX2
        val x3 = inSample

        limX0 = x1
        limX1 = x2
        limX2 = x3

        // Exact Catmull-Rom 4x oversampled inter-sample peak detection
        val p25 = -0.0703125f * x0 + 0.8671875f * x1 + 0.2265625f * x2 - 0.0234375f * x3
        val p50 = -0.0625f * x0 + 0.5625f * x1 + 0.5625f * x2 - 0.0625f * x3
        val p75 = -0.0234375f * x0 + 0.2265625f * x1 + 0.8671875f * x2 - 0.0703125f * x3

        var truePeak = max(abs(x1), abs(x2))
        truePeak = max(truePeak, abs(p25))
        truePeak = max(truePeak, abs(p50))
        truePeak = max(truePeak, abs(p75))

        val ceiling = TRUE_PEAK_CEILING_LINEAR // 0.875f (-1.16 dBTP)
        val desiredGain = if (truePeak > ceiling) (ceiling / truePeak) else 1.0f
        if (desiredGain < limGain) {
            limGain = desiredGain // Instant peak catch
        } else {
            limGain += limiterReleaseAlpha * (desiredGain - limGain)
        }

        var out = x1 * limGain
        if (abs(out) > ceiling) {
            if (abs(out) > 0.99f) clipCount++
            out = out.coerceIn(-ceiling, ceiling)
        }
        return out
    }

    fun processMonoFrameInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        val frameStartNs = System.nanoTime()

        // Atomic frame-boundary snapshot read (zero locks, zero heap allocations)
        val cfg = config
        syncAudioThreadFilters(cfg)

        val targetMute = if (streamMicMuted) 0.0f else 1.0f
        val preset = cfg.voicePreset
        val rawMode = (preset == "RAW")
        val hpfHz = cfg.hpfCutoffHz
        val hwMode = cfg.hardwareAcousticMode
        val postDspGain = cfg.micGainLinear

        // === RAW BYPASS MODE: Mute + Post-DSP Volume + 4x True-Peak Safety Limiter ===
        if (rawMode) {
            var rawPeak = 0f
            var rawSumSq = 0f
            for (i in 0 until count) {
                muteSmoothGain += 0.015f * (targetMute - muteSmoothGain)
                val rawIn = buffer[i]
                val cleanIn = (if (rawIn.isFinite()) rawIn else 0f) * muteSmoothGain
                val outS = processTruePeakLimiterSample(cleanIn * postDspGain)
                buffer[i] = outS
                val a = abs(outS)
                if (a > rawPeak) rawPeak = a
                rawSumSq += outS * outS
            }
            val rawRms = sqrt(rawSumSq / count)
            lastPeakDb = linearToDb(rawPeak)
            lastRmsDb = linearToDb(rawRms)
            lastGainReductionDb = 0f
            cpuEcoBypassActive = false
            vadSpeechActive = (muteSmoothGain > 0.05f) && (rawRms > 0.010f)
            lastVadProbability = if (vadSpeechActive) 0.85f else 0.05f
            excitedCalloutActive = vadSpeechActive && (rawRms > 0.18f)
            return
        }

        // === PASS 1 (Unity 1.0x Gain): Smooth Mute + Stage 1 (64-Bit HPF) + Stage 2 (Raised-Cosine Micro-Impulse Clamper) ===
        val useClamper = cfg.transientTamerEnabled
        var clampedImpulseSamples = 0

        for (i in 0 until count) {
            muteSmoothGain += 0.015f * (targetMute - muteSmoothGain)
            val rawIn = buffer[i]
            var s = (if (rawIn.isFinite()) rawIn else 0f) * muteSmoothGain

            // Stage 1: 64-Bit Butterworth HPF
            if (hpfHz > 0) {
                s = hpfFilter.process(s)
            }

            // Stage 2: Raised-Cosine Micro-Impulse Clamper (sub-millisecond <0.6ms spikes >3.8x 20ms RMS)
            if (useClamper) {
                val absS = abs(s)
                val slew = abs(s - prevClampSample)
                prevClampSample = s

                val localRms = sqrt(max(localEnergy20ms, 1e-8f))
                val spikeThresh = max(0.060f, localRms * 3.8f)
                val slewThresh = max(0.040f, localRms * 2.2f)
                val isSharpSpike = (absS > spikeThresh) && (slew > slewThresh)

                if (isSharpSpike && impulseStreakSamples < maxImpulseSamples) {
                    // Sub-millisecond mosquito-racket zap or static pop (<0.6 ms): clamp cleanly & freeze RMS baseline
                    impulseStreakSamples++
                    clampedImpulseSamples++
                    val clampCeiling = max(0.018f, localRms * 2.2f)
                    val targetClamp = (clampCeiling / max(absS, 1e-5f)).coerceIn(0.08f, 1.0f)
                    impulseClampGain = min(impulseClampGain, targetClamp)
                    impulseRecoveryStartGain = impulseClampGain
                    impulseRecoveryPos = 0
                } else {
                    if (isSharpSpike) {
                        // Event exceeded 0.6 ms -> genuine speech consonant/shout, stop clamping and recover smoothly
                        impulseStreakSamples = maxImpulseSamples + 1
                    } else {
                        impulseStreakSamples = 0
                    }
                    // Smooth 2.0 ms (96-sample) raised-cosine recovery back to unity 1.0f
                    if (impulseClampGain < 0.999f) {
                        impulseRecoveryPos++
                        val t = (impulseRecoveryPos.toFloat() / impulseRecoverySamples.toFloat()).coerceIn(0f, 1f)
                        val cosEase = 0.5f * (1.0f - cos(PI * t.toDouble()).toFloat())
                        impulseClampGain = impulseRecoveryStartGain + (1.0f - impulseRecoveryStartGain) * cosEase
                        if (t >= 1.0f) impulseClampGain = 1.0f
                    }
                    localEnergy20ms += rms20msAlpha * ((s * s) - localEnergy20ms)
                }
                s *= impulseClampGain
            } else {
                prevClampSample = s
                localEnergy20ms += rms20msAlpha * ((s * s) - localEnergy20ms)
            }

            if (abs(s) < 1e-20f) s = 0f
            buffer[i] = s
        }

        // === PASS 2: Stage 3a — Phase-Aligned Native C 48 kHz RNNoise (Unity 1.0x Gain) ===
        val nsMode = cfg.noiseSuppressMode
        val nsLevel = cfg.noiseSuppressLevel
        var neuralVadProb = -1.0f
        var ranNativeRnnoise = false

        if (nsMode == "RNNOISE" && nsLevel > 0 && count == 480 && sampleRate == 48000) {
            val wetStrength = when (nsLevel) {
                1 -> 0.65f
                2 -> 0.88f
                else -> 1.00f // True 100% wet strength (matches UI label 1:1)
            }
            neuralVadProb = runNativeRnnoiseLocked(buffer, wetStrength)
            ranNativeRnnoise = (neuralVadProb >= 0.0f)
        }

        // Measure post-RNNoise frame energy & 200-2500 Hz speech-band RMS for 16kHz-safe Hybrid VAD
        var preDspSumSq = 0.0f
        var speechBandSumSq = 0.0f
        for (i in 0 until count) {
            val s = buffer[i]
            preDspSumSq += s * s
            val sb = speechBandFilter.process(s)
            speechBandSumSq += sb * sb
        }
        val preDspRms = sqrt(preDspSumSq / count)
        val speechBandRms = sqrt(speechBandSumSq / count)

        val frameHadImpulse = clampedImpulseSamples > (count / 12)
        val likelyQuiet = if (neuralVadProb >= 0f) {
            neuralVadProb < 0.25f && preDspRms < noiseFloorEnv * 1.8f && !frameHadImpulse
        } else {
            preDspRms < noiseFloorEnv * 1.6f && !frameHadImpulse
        }
        if (likelyQuiet) {
            noiseFloorEnv = 0.96f * noiseFloorEnv + 0.04f * max(preDspRms, 0.00010f)
            speechBandNoiseFloor = 0.96f * speechBandNoiseFloor + 0.04f * max(speechBandRms, 0.00008f)
        } else if (!frameHadImpulse) {
            noiseFloorEnv = min(0.035f, noiseFloorEnv * 1.0003f)
            speechBandNoiseFloor = min(0.025f, speechBandNoiseFloor * 1.0003f)
        }
        lastNoiseFloorDb = linearToDb(noiseFloorEnv)

        // Stage 3b: Hybrid 16 kHz-Safe Neural-VAD Trigger + Double-Talk-Safe Speakerphone Reference
        val speakerBleed = if (hwMode == HardwareAcousticMode.SPEAKER_AEC) {
            speakerBleedLevel.coerceIn(0f, 1f)
        } else {
            0f
        }
        val snrRatio = preDspRms / max(noiseFloorEnv, 0.00015f)
        val speechBandSnr = speechBandRms / max(speechBandNoiseFloor, 0.00012f)
        val speechBandMinRms = 0.0040f + (speakerBleed * 0.0080f)

        // Opens when vadProb >= 0.35 OR 200-2500 Hz speech band exceeds adaptive noise floor by +6 dB (2.0x)
        val neuralTrigger = (neuralVadProb >= 0.35f) && (preDspRms > (0.0022f + speakerBleed * 0.0030f))
        val speechBandTrigger = (speechBandRms > speechBandMinRms) && (speechBandSnr >= (2.0f + speakerBleed * 1.0f))

        val confirmedSpeech = (muteSmoothGain > 0.05f) && (neuralTrigger || speechBandTrigger)
        val effectiveVadProb = when {
            neuralVadProb >= 0f -> max(neuralVadProb, if (speechBandTrigger) 0.50f else 0.0f)
            confirmedSpeech -> ((speechBandSnr - 1.5f) / 3.0f).coerceIn(0.45f, 0.95f)
            else -> 0.05f
        }
        lastVadProbability = effectiveVadProb

        if (confirmedSpeech) {
            expanderHoldRemaining = expHoldSamples
        } else if (expanderHoldRemaining > 0) {
            expanderHoldRemaining = max(0, expanderHoldRemaining - count)
        }
        val activeSpeech = confirmedSpeech || expanderHoldRemaining > 0
        vadSpeechActive = activeSpeech
        excitedCalloutActive = confirmedSpeech && (preDspRms >= 0.085f)

        // Pure Single-Pass Rule: NEVER stack Kotlin light spectral cut on top of Native C RNNoise
        val runLightSpectral = (nsLevel > 0 && !ranNativeRnnoise &&
            (nsMode == "LIGHT" || nsMode == "RNNOISE" || (nsMode == "HW_NS" && !platformHwNsAttached)))

        val targetSuppressGain = if (runLightSpectral) {
            val strength = when (nsLevel) {
                1 -> 0.50f
                2 -> 0.76f
                else -> 0.88f
            }
            val cleanRatio = ((snrRatio - 1.2f) / 3.5f).coerceIn(0f, 1f)
            (1.0f - strength * (1.0f - cleanRatio)).coerceIn(0.14f, 1.0f)
        } else {
            1.0f
        }

        // Smooth continuous VAD ramp (0.25 -> 0.55) for upward leveling & gentle slow AGC
        val vadRamp = ((effectiveVadProb - 0.25f) / 0.30f).coerceIn(0.0f, 1.0f)

        // Gentle Speech-Synced Slow Broadcast Leveling (runs pre-boost so it never fights Mic Boost)
        if (cfg.slowAgcEnabled) {
            if (vadRamp > 0.25f && preDspRms in 0.014f..0.200f) {
                val targetSpeechRms = 0.110f
                val desiredAgc = (targetSpeechRms / preDspRms).coerceIn(0.88f, 1.25f)
                val agcRate = (if (desiredAgc < slowAgcGain) 0.035f else 0.014f) * vadRamp
                slowAgcGain += agcRate * (desiredAgc - slowAgcGain)
            }
        } else {
            slowAgcGain += 0.05f * (1.0f - slowAgcGain)
        }
        val currentAgcGain = slowAgcGain

        // Low-End CPU Budget Auto-Bypass check
        val ecoBypassNow = ecoBypassRemainingFrames > 0
        if (ecoBypassRemainingFrames > 0) {
            ecoBypassRemainingFrames--
        }
        cpuEcoBypassActive = ecoBypassNow

        val useExpander = cfg.gateEnabled
        val useEq = cfg.eqEnabled && !ecoBypassNow
        val useWarmth = cfg.velvetWarmthEnabled && !ecoBypassNow
        val useComp = cfg.compressorEnabled
        val useWhisperLift = cfg.whisperLiftEnabled
        val useDeEss = cfg.deEsserEnabled && !ecoBypassNow
        val btLpfActive = isBluetoothLpfActive && !ecoBypassNow

        // Expander smooth floor: -14 dB (0.20x) normal, -18 dB (0.126x) speakerphone (+ double-talk-safe bleed ducking)
        val baseIdleFloor = if (hwMode == HardwareAcousticMode.SPEAKER_AEC) 0.126f else 0.20f
        val speakerRefFloor = if (hwMode == HardwareAcousticMode.SPEAKER_AEC && speakerBleed > 0.10f) {
            (baseIdleFloor * (1.0f - 0.45f * speakerBleed)).coerceIn(0.07f, baseIdleFloor)
        } else {
            baseIdleFloor
        }
        val targetExpander = if (!useExpander || activeSpeech) 1.0f else speakerRefFloor

        val compRatio = when (preset) {
            "STUDIO" -> 3.2f
            "VELVET" -> 2.0f
            else -> 2.5f
        }
        val compSlope = 1.0f - (1.0f / compRatio)
        val curSubCubicDrive = subCubicDrive
        val curTube2fDrive = tube2fDrive

        var minCompGrDbInFrame = 0.0f
        var finalPeak = 0.0f
        var finalSumSq = 0.0f

        // === PASS 3: Clean 6-Stage Non-Fighting SolLuxe™ 3.0 Pure Processing Loop ===
        for (i in 0 until count) {
            var s = buffer[i]

            if (runLightSpectral) {
                suppressSmoothGain += 0.010f * (targetSuppressGain - suppressSmoothGain)
                lpNoiseState += 0.22f * (s - lpNoiseState)
                val highBand = s - lpNoiseState
                s = (lpNoiseState * suppressSmoothGain) + (highBand * suppressSmoothGain * suppressSmoothGain)
            }

            // Stage 3b: Smooth 8 ms Attack / 220 ms Release Hybrid Neural-VAD Soft-Floor Expander
            if (useExpander) {
                val eCoeff = if (targetExpander > expanderGain) expAttackAlpha else expReleaseAlpha
                expanderGain += eCoeff * (targetExpander - expanderGain)
                s *= expanderGain
            } else {
                expanderGain += expAttackAlpha * (1.0f - expanderGain)
            }

            // Stage 4: Split-Band Dynamic Plosive Guard (<115 Hz wind vs 200-1200 Hz vocal core)
            // Ducks ONLY the <115 Hz band up to -9 dB (0.645 subtraction) before Stage 5 Chest Warmth
            plosiveLpState += plosiveLp115Alpha * (s - plosiveLpState)
            val absWind = abs(plosiveLpState)
            windLowEnv += (if (absWind > windLowEnv) 0.08f else 0.005f) * (absWind - windLowEnv)

            val vocalCoreSample = vocalCoreFilter.process(s)
            val absVocalCore = abs(vocalCoreSample)
            vocalCoreEnv += (if (absVocalCore > vocalCoreEnv) 0.06f else 0.004f) * (absVocalCore - vocalCoreEnv)

            val targetPlosiveDuck = if (windLowEnv > 0.0045f && windLowEnv > (vocalCoreEnv * 2.5f + 0.0015f)) {
                val excess = ((windLowEnv - vocalCoreEnv * 2.5f) / max(windLowEnv, 0.001f)).coerceIn(0.0f, 1.0f)
                (0.645f * excess).coerceIn(0.0f, 0.645f) // Up to -9 dB on <115 Hz band only
            } else {
                0.0f
            }
            plosiveDuckSmooth += (if (targetPlosiveDuck > plosiveDuckSmooth) 0.08f else 0.015f) *
                (targetPlosiveDuck - plosiveDuckSmooth)
            if (plosiveDuckSmooth > 0.001f) {
                s -= plosiveLpState * plosiveDuckSmooth
            }

            // Stage 5: 4-Gear × 3-Gender Condenser EQ + Low-Band Analog Warmth Shaper
            if (useEq) {
                s = eqLowWarmth.process(s)
                s = eqMudCut.process(s)
                s = eqAntiNasal.process(s)
                s = eqPresence.process(s)
                s = eqAirOrBtLpf.process(s)
            } else if (btLpfActive) {
                // Always block >5.2 kHz Bluetooth SCO hiss even when EQ is toggled off
                s = eqAirOrBtLpf.process(s)
            }

            if (useWarmth) {
                val lowBand = lowWarmthExtractor.process(s)
                val u = (lowBand * 1.25f).coerceIn(-0.92f, 0.92f)
                val cubicWarmth = lowWarmthSmoother.process(u - ((u * u * u) * 0.3333333f))
                val tubeAddition = if (curTube2fDrive > 0.001f) {
                    val absU = abs(u)
                    val evenRaw = (u * absU + 0.30f * u * u) / (1.0f + absU)
                    tubeDcState += 0.0025f * (evenRaw - tubeDcState)
                    (evenRaw - tubeDcState) * curTube2fDrive
                } else {
                    0.0f
                }
                val warmthOpen = ((expanderGain - baseIdleFloor) / max(0.05f, 1.0f - baseIdleFloor)).coerceIn(0f, 1f)
                s += ((curSubCubicDrive * cubicWarmth) + tubeAddition) * warmthOpen
            }

            // Stage 6a: Gender-Sidechain Single-Curve Soft-Knee Leveler + Continuous VAD-Synced Upward Lift
            val scSample = abs(levelerSidechainHpf.process(s))
            val lCoeff = if (scSample > levelerEnv) levelerAttackAlpha else levelerReleaseAlpha
            levelerEnv += lCoeff * (scSample - levelerEnv)

            if ((i and 7) == 0) {
                val envDb = linearToDb(levelerEnv)

                // Continuous 6 dB soft-knee downward compression around -16 dBFS (zero abrupt knees)
                var compGrDb = 0.0f
                if (useComp) {
                    val thresholdDb = -16.0f
                    val halfKneeDb = 3.0f
                    compGrDb = when {
                        envDb <= (thresholdDb - halfKneeDb) -> 0.0f
                        envDb < (thresholdDb + halfKneeDb) -> {
                            val d = envDb - (thresholdDb - halfKneeDb)
                            -compSlope * (d * d) / (4.0f * halfKneeDb)
                        }
                        else -> -compSlope * (envDb - thresholdDb)
                    }
                    if (compGrDb < minCompGrDbInFrame) minCompGrDbInFrame = compGrDb
                }

                // Continuous upward quiet-speech lift (-38..-22 dBFS, max +3.5 dB) synced to vadRamp × expanderGain
                var upwardLiftDb = 0.0f
                if (useWhisperLift && vadRamp > 0.001f && envDb > -46.0f && envDb < -22.0f) {
                    val rawLiftDb = when {
                        envDb >= -30.0f -> {
                            // Smooth cosine taper from +3.5 dB at -30 dBFS down to 0.0 dB at -22 dBFS
                            val t = ((-22.0f - envDb) / 8.0f).coerceIn(0f, 1f)
                            3.5f * 0.5f * (1.0f - cos(PI * t.toDouble()).toFloat())
                        }
                        envDb >= -38.0f -> 3.5f
                        else -> {
                            // Smooth cosine roll-off from +3.5 dB at -38 dBFS down to 0.0 dB at -46 dBFS
                            val t = ((envDb + 46.0f) / 8.0f).coerceIn(0f, 1f)
                            3.5f * 0.5f * (1.0f - cos(PI * t.toDouble()).toFloat())
                        }
                    }
                    val expanderOpen = ((expanderGain - baseIdleFloor) / max(0.05f, 1.0f - baseIdleFloor)).coerceIn(0f, 1f)
                    upwardLiftDb = rawLiftDb * vadRamp * expanderOpen
                }

                val combinedGainDb = compGrDb + upwardLiftDb
                targetLevelerBlockGain = dbToLinear(combinedGainDb) * currentAgcGain
            }

            levelerGainSmooth += levelerGainSmoothAlpha * (targetLevelerBlockGain - levelerGainSmooth)
            s *= levelerGainSmooth

            // Stage 6b: Post-EQ & Post-Comp Dynamic Sibilance-Ratio De-Esser (6.2 / 6.7 / 7.2 kHz, max -4.5 dB)
            if (useDeEss) {
                val sibSample = sibilanceBand.process(s)
                val absSib = abs(sibSample)
                val dCoeff = if (absSib > sibilanceEnv) deEssAttackAlpha else deEssReleaseAlpha
                sibilanceEnv += dCoeff * (absSib - sibilanceEnv)

                // React only when sibilant energy exceeds both absolute floor and vocal-core ratio (0 dB on warm vowels)
                val sibFloor = max(0.024f, vocalCoreEnv * 0.45f)
                val targetDeEssSub = if (sibilanceEnv > sibFloor) {
                    val excessRatio = ((sibilanceEnv - sibFloor) / max(sibilanceEnv, 0.001f)).coerceIn(0.0f, 1.0f)
                    (0.4044f * excessRatio).coerceIn(0.0f, 0.4044f) // Max -4.5 dB (1 - 10^(-4.5/20) = 0.4044)
                } else {
                    0.0f
                }
                deEssAttenSmooth += (if (targetDeEssSub > deEssAttenSmooth) deEssAttackAlpha else deEssReleaseAlpha) *
                    (targetDeEssSub - deEssAttenSmooth)
                if (deEssAttenSmooth > 0.001f) {
                    s -= sibSample * deEssAttenSmooth
                }
            }

            // Stage 6c: True Post-DSP Mic Boost (50..250%) -> 4× Catmull-Rom True-Peak Limiter (-1.16 dBTP)
            s *= postDspGain
            val outS = processTruePeakLimiterSample(s)

            val absOut = abs(outS)
            if (absOut > finalPeak) finalPeak = absOut
            finalSumSq += outS * outS
            buffer[i] = outS
        }

        val finalRms = sqrt(finalSumSq / count)
        lastPeakDb = linearToDb(finalPeak)
        lastRmsDb = linearToDb(finalRms)
        lastGainReductionDb = if (minCompGrDbInFrame < -0.01f) -minCompGrDbInFrame else 0f

        // Low-End CPU Budget Watchdog (>4.5 ms per 10 ms frame for 6 frames triggers 3s eco EQ bypass)
        val elapsedNs = System.nanoTime() - frameStartNs
        if (elapsedNs > 4_500_000L) {
            slowFrameStrikeCount++
            if (slowFrameStrikeCount >= 6) {
                ecoBypassRemainingFrames = 300
                slowFrameStrikeCount = 0
            }
        } else if (slowFrameStrikeCount > 0) {
            slowFrameStrikeCount--
        }
    }

    companion object {
        const val TRUE_PEAK_CEILING_LINEAR = 0.875f // -1.16 dBTP

        val VOICE_PRESET_ORDER = listOf("RAW", "CLEAN", "NEURAL", "STUDIO", "VELVET", "SOL_LUXE")
        val GENDER_PROFILE_ORDER = listOf("NEUTRAL", "MALE", "FEMALE")
        val NOISE_MODE_ORDER = listOf("OFF", "LIGHT", "RNNOISE", "HW_NS")
        val HPF_CUTOFF_STEPS = listOf(0, 50, 55, 60, 65, 68, 70, 75, 85, 95, 105, 120)

        private fun normalizeGender(raw: String): String = when (raw.uppercase(Locale.US)) {
            "MALE" -> "MALE"
            "FEMALE" -> "FEMALE"
            else -> "NEUTRAL"
        }

        private fun normalizeNoiseMode(raw: String): String = when (raw.uppercase(Locale.US)) {
            "OFF" -> "OFF"
            "LIGHT" -> "LIGHT"
            "HW_NS" -> "HW_NS"
            else -> "RNNOISE"
        }

        fun presetDisplayName(preset: String): String = when (preset.uppercase(Locale.US)) {
            "RAW" -> "RAW (Dry Mic Bypass)"
            "CLEAN" -> "CLEAN (Transparent Natural)"
            "NEURAL" -> "NEURAL (C RNNoise Pure)"
            "STUDIO" -> "RADIO STUDIO (Punchy Cut-Through)"
            "VELVET" -> "VELVET (Warm Natural)"
            "SOL_LUXE" -> "SOL LUXE 3.0 (Pure Condenser)"
            else -> preset
        }

        /**
         * Switches the user's Voice Gender Profile (MALE / NEUTRAL / FEMALE).
         * Updates HPF automatically ONLY when [StreamSettings.hpfAutoMode] is true (55M / 68N / 85F);
         * preserves explicit manual HPF selections untouched.
         */
        fun applyGenderProfileToSettings(current: StreamSettings, newGender: String): StreamSettings {
            val normGender = normalizeGender(newGender)
            val matchedHpf = KeystoreVault.resolveDefaultHpfForProfile(normGender)
            val resolvedHpf = if (current.hpfAutoMode) {
                if (current.hpfCutoffHz == 0) 0 else matchedHpf
            } else {
                current.hpfCutoffHz
            }
            return current.copy(
                voiceGenderProfile = normGender,
                hpfCutoffHz = resolvedHpf
            )
        }

        /**
         * Switches the Voice Preset cleanly while preserving explicit manual HPF cutoffs
         * whenever [StreamSettings.hpfAutoMode] is false.
         */
        fun applyPresetToSettings(current: StreamSettings, newPreset: String): StreamSettings {
            val p = newPreset.uppercase(Locale.US)
            val matchedHpf = KeystoreVault.resolveDefaultHpfForProfile(current.voiceGenderProfile)
            val activeHpf = if (current.hpfAutoMode) {
                if (current.hpfCutoffHz == 0 && current.voicePreset != "RAW") 0 else matchedHpf
            } else {
                current.hpfCutoffHz
            }

            return when (p) {
                "RAW" -> current.copy(voicePreset = "RAW")
                "CLEAN" -> current.copy(
                    voicePreset = "CLEAN",
                    hpfCutoffHz = activeHpf,
                    transientTamerEnabled = true,
                    noiseGateEnabled = true,
                    voiceEqEnabled = false,
                    deEsserEnabled = false,
                    velvetWarmthEnabled = false,
                    compressorEnabled = true,
                    whisperLiftEnabled = false,
                    slowAgcEnabled = false
                )
                "NEURAL" -> current.copy(
                    voicePreset = "NEURAL",
                    hpfCutoffHz = activeHpf,
                    transientTamerEnabled = true,
                    noiseSuppressMode = "RNNOISE",
                    rnnoiseLevel = if (current.rnnoiseLevel == 0) 2 else current.rnnoiseLevel,
                    noiseGateEnabled = true,
                    voiceEqEnabled = false,
                    deEsserEnabled = false,
                    velvetWarmthEnabled = false,
                    compressorEnabled = true,
                    whisperLiftEnabled = false,
                    slowAgcEnabled = false
                )
                "STUDIO" -> current.copy(
                    voicePreset = "STUDIO",
                    hpfCutoffHz = activeHpf,
                    transientTamerEnabled = true,
                    noiseGateEnabled = true,
                    voiceEqEnabled = true,
                    deEsserEnabled = true,
                    velvetWarmthEnabled = false,
                    compressorEnabled = true,
                    whisperLiftEnabled = true,
                    slowAgcEnabled = false
                )
                "VELVET" -> current.copy(
                    voicePreset = "VELVET",
                    hpfCutoffHz = activeHpf,
                    transientTamerEnabled = true,
                    noiseGateEnabled = true,
                    voiceEqEnabled = false,
                    deEsserEnabled = true,
                    velvetWarmthEnabled = true,
                    compressorEnabled = true,
                    whisperLiftEnabled = false,
                    slowAgcEnabled = false
                )
                else -> current.copy(
                    voicePreset = "SOL_LUXE",
                    hpfCutoffHz = activeHpf,
                    transientTamerEnabled = true,
                    noiseSuppressMode = if (current.noiseSuppressMode == "OFF") "RNNOISE" else current.noiseSuppressMode,
                    rnnoiseLevel = if (current.rnnoiseLevel == 0) 2 else current.rnnoiseLevel,
                    noiseGateEnabled = true,
                    voiceEqEnabled = true,
                    deEsserEnabled = true,
                    velvetWarmthEnabled = true,
                    compressorEnabled = true,
                    whisperLiftEnabled = true,
                    slowAgcEnabled = true
                )
            }
        }

        fun linearToDb(v: Float): Float {
            if (v <= 0.00003f) return -90f
            return (20.0 * ln(v.toDouble()) / ln(10.0)).toFloat()
        }

        fun dbToLinear(db: Float): Float = 10.0.pow(db.toDouble() / 20.0).toFloat()
    }
}
