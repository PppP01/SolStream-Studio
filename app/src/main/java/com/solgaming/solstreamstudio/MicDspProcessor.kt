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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 4-Hardware Acoustic Adaptation Modes (#28):
 * Automatically adapts proximity chest warmth, hand-grip mud cut, breath-plosive HPF,
 * and Bluetooth >5.2 kHz hiss roll-off across all 3 gender profiles (12 combinations total).
 */
enum class HardwareAcousticMode(val title: String) {
    PHONE_STUDIO("48kHz Phone Studio Mic (Hand-Grip Tuned)"),
    WIRED_USB_CLOSE("48kHz Wired / USB-C Close-Talk Mic"),
    BLUETOOTH_SCO("Bluetooth Collar / Earbud Mic (Anti-Hiss)"),
    SPEAKER_AEC("Phone Speakerphone Mode (Echo-Guard)")
}

class MicDspProcessor(private val sampleRate: Int = 48000) {

    // Pick-1 selectors (Locked to optimal out-of-the-box studio defaults)
    @Volatile var voiceGenderProfile: String = "NEUTRAL" // "NEUTRAL", "MALE", "FEMALE"
    @Volatile var voicePreset: String = "SOL_LUXE"       // "RAW", "CLEAN", "NEURAL", "STUDIO", "VELVET", "SOL_LUXE"
    @Volatile var noiseSuppressMode: String = "RNNOISE"  // "OFF", "LIGHT", "RNNOISE", "HW_NS"
    @Volatile var noiseSuppressLevel: Int = 2            // 0 = Off, 1 = Gentle(65%), 2 = Natural(88%), 3 = Max(100%)

    // Stackable independent stage switches (100% mix-and-match freedom; studio defaults ON)
    @Volatile var hpfCutoffHz: Int = 75                  // 0=Off, 50, 60(Male), 70, 75(Neutral), 80, 85, 95(Female), 105, 120 Hz
    @Volatile var transientTamerEnabled: Boolean = true  // Stage 2: Spark/Hammer & Grip Blanker
    @Volatile var gateEnabled: Boolean = true            // Stage 4: 0-ms Instant Vocal Gate & Breath Expander
    @Volatile var eqEnabled: Boolean = true              // Stage 5: Cardioid Proximity & Narrow Q=2.2 3-Band EQ
    @Volatile var velvetWarmthEnabled: Boolean = true    // Stage 6: 2f Sub-Harmonic Tube Warmth + Silk Air Shelf
    @Volatile var aiToneBalancerEnabled: Boolean = true  // Stage 7: AI Dynamic Vocal Tone Balancer
    @Volatile var compressorEnabled: Boolean = true      // Stage 8: 12ms-Punch 3:1 Broadcast Leveler
    @Volatile var whisperLiftEnabled: Boolean = true     // Stage 8: Quiet Callout Booster
    @Volatile var slowAgcEnabled: Boolean = true         // Stage 8: Speech-Gated Slow Broadcast Loudness Ride
    @Volatile var deEsserEnabled: Boolean = true         // Stage 9: Post-Compressor Split-Band Sibilance Tamer

    // Stage 10: True Post-DSP Makeup Gain (50%..250%) — placed AFTER Slow AGC & Compressor!
    @Volatile var micGainLinear: Float = 1.30f
    @Volatile var streamMicMuted: Boolean = false
    @Volatile var platformHwNsAttached: Boolean = false
    @Volatile var hardwareAcousticMode: HardwareAcousticMode = HardwareAcousticMode.PHONE_STUDIO

    // Real-time game speaker bleed reference (0.0..1.0) fed when Stage 0 Speaker Echo Canceler is ON
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

    // 64-bit Double internal biquad states for zero low-frequency rounding noise over 6+ hour streams
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

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }
    }

    private val hpfFilter = Biquad64()
    private val vocalBodyDetector = Biquad64()   // 220 Hz vocal-cord fundamental detector for Spark/Hammer rejection
    private val eqLowWarmth = Biquad64()         // Stage 5 Band 1: Studio Cardioid Proximity Chest Warmth
    private val eqMudCut = Biquad64()            // Stage 5 Band 2: Surgical Narrow Q=2.1..2.2 Boxiness Cut
    private val eqPresence = Biquad64()          // Stage 5 Band 3: Smooth Callout Presence
    private val subWarmthBandFilter = Biquad64() // Stage 6 Part A: Isolated Vocal Fundamental for 2f Tube Harmonics
    private val eqVelvetAir = Biquad64()         // Stage 6 Part B: Gentle Silk Air High-Shelf
    private val aiPresenceTamer = Biquad64()     // Stage 7: AI Dynamic Shout Tamer (3.1-3.5 kHz detector)
    private val sibilanceBand = Biquad64()       // Stage 9: Post-Compressor Split-Band De-Esser detector

    private val nativeLock = Any()
    @Volatile private var nativeRnnHandle: Long = 0L
    @Volatile private var released: Boolean = false

    private var configuredEffectiveHpfHz = -1
    private var configuredGender = ""
    private var configuredHwMode: HardwareAcousticMode? = null

    // Stage 2: Consonant-safe multi-strike mosquito-racket spark ("phat-phat-phat") & hammer blanker state
    private var muteSmoothGain = 1.0f
    private var prevRawSample = 0.0f
    private var slewFastEnv = 0.0f
    private var slewSlowEnv = 0.0015f
    private var transientFastEnv = 0.0f
    private var transientSlowEnv = 0.008f
    private var vocalBodyEnv = 0.002f
    private var sparkBlankerHoldSamples = 0

    // Stage 4: 0-ms Instant Vocal VAD & Soft 2.2:1 Breath Expander state
    private var gateGain = 1.0f
    private var gateHoldSamples = 0

    // Stage 3 fallback: Stationary light spectral state (strictly bypassed when Native C RNNoise runs!)
    private var noiseFloorEnv = 0.0018f
    private var suppressSmoothGain = 1.0f
    private var lpNoiseState = 0.0f

    // Stage 6, 7, 8, 9 states: Sub-Harmonic Tube Warmth, AI Tone Balancer, 3-Zone Dynamics & Post-Compressor De-Esser
    private var velvetDcState = 0.0f
    private var exciterLpState = 0.0f
    private var velvetAirMixSmooth = 0.0f
    private var subHarmonicDrive = 0.11f
    private var allowUpperAirExciter = true

    private var aiLowEnv = 0.0f
    private var aiHighEnv = 0.0f
    private var aiWarmthBoostSmooth = 0.0f
    private var aiBiteTrimSmooth = 0.0f

    private var compEnv = 0.0f
    private var compGainSmooth = 1.0f
    private var slowAgcGain = 1.0f
    private var deEssEnv = 0.0f
    private var deEssThreshold = 0.095f

    init {
        configureBandPass(vocalBodyDetector, 220.0, 0.90)
        updateGenderAndHardwareFiltersIfNeeded(voiceGenderProfile, hardwareAcousticMode)
        updateHpfIfNeeded(hpfCutoffHz, hardwareAcousticMode)
        refreshDenoiserSummary()
    }

    /**
     * Updates the active hardware acoustic mode and immediately re-tunes the 12-mode filter matrix.
     * Named applyHardwareAcousticMode to avoid clashing with Kotlin's auto-generated setter for var hardwareAcousticMode.
     */
    fun applyHardwareAcousticMode(mode: HardwareAcousticMode) {
        if (hardwareAcousticMode != mode) {
            hardwareAcousticMode = mode
            updateGenderAndHardwareFiltersIfNeeded(voiceGenderProfile, mode)
            updateHpfIfNeeded(hpfCutoffHz, mode)
        }
    }

    fun applySettings(s: StreamSettings) {
        val normGender = when (s.voiceGenderProfile.uppercase(Locale.US)) {
            "MALE" -> "MALE"
            "FEMALE" -> "FEMALE"
            else -> "NEUTRAL"
        }
        val normMode = when (s.noiseSuppressMode.uppercase(Locale.US)) {
            "OFF" -> "OFF"
            "LIGHT" -> "LIGHT"
            "HW_NS" -> "HW_NS"
            else -> "RNNOISE"
        }
        val presetUpper = s.voicePreset.uppercase(Locale.US)

        voiceGenderProfile = normGender
        voicePreset = presetUpper
        noiseSuppressMode = normMode
        noiseSuppressLevel = s.rnnoiseLevel.coerceIn(0, 3)

        hpfCutoffHz = s.hpfCutoffHz.coerceIn(0, 160)
        transientTamerEnabled = s.transientTamerEnabled
        gateEnabled = s.noiseGateEnabled
        eqEnabled = s.voiceEqEnabled
        deEsserEnabled = s.deEsserEnabled
        velvetWarmthEnabled = s.velvetWarmthEnabled
        compressorEnabled = s.compressorEnabled
        whisperLiftEnabled = s.whisperLiftEnabled
        slowAgcEnabled = s.slowAgcEnabled
        aiToneBalancerEnabled = presetUpper in setOf("SOL_LUXE", "VELVET", "STUDIO")
        micGainLinear = (s.micGainPercent.coerceIn(50, 250)) / 100.0f

        val hwMode = hardwareAcousticMode
        updateHpfIfNeeded(hpfCutoffHz, hwMode)
        updateGenderAndHardwareFiltersIfNeeded(normGender, hwMode)
        refreshDenoiserSummary()
    }

    private fun refreshDenoiserSummary() {
        val lvl = noiseSuppressLevel
        val mode = noiseSuppressMode
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

    private fun updateHpfIfNeeded(baseCutoffHz: Int, hwMode: HardwareAcousticMode) {
        val effectiveHz = if (baseCutoffHz <= 0) {
            0
        } else {
            // Hardware-specific plosive & collar-rub protection offset (#28):
            // - Wired/USB-C close-talk mics sit 2-3 inches from mouth (+5 Hz breath-pop shield)
            // - Bluetooth neckband/collar mics experience shirt/collar friction (+10 Hz rumble shield)
            val offset = when (hwMode) {
                HardwareAcousticMode.WIRED_USB_CLOSE -> 5
                HardwareAcousticMode.BLUETOOTH_SCO -> 10
                else -> 0
            }
            (baseCutoffHz + offset).coerceIn(40, 150)
        }
        if (effectiveHz == configuredEffectiveHpfHz) return
        configuredEffectiveHpfHz = effectiveHz
        if (effectiveHz <= 0) {
            hpfFilter.reset()
            return
        }
        val w0 = 2.0 * PI * effectiveHz.toDouble() / sampleRate.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / sqrt(2.0)
        val a0 = 1.0 + alpha
        if (a0 <= 1e-9) return
        hpfFilter.b0 = ((1.0 + cosW) * 0.5) / a0
        hpfFilter.b1 = -(1.0 + cosW) / a0
        hpfFilter.b2 = ((1.0 + cosW) * 0.5) / a0
        hpfFilter.a1 = (-2.0 * cosW) / a0
        hpfFilter.a2 = (1.0 - alpha) / a0
    }

    private fun configurePeakingEq(target: Biquad64, freqHz: Double, q: Double, gainDb: Double) {
        if (abs(gainDb) < 0.05) {
            target.b0 = 1.0; target.b1 = 0.0; target.b2 = 0.0
            target.a1 = 0.0; target.a2 = 0.0
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
            target.b0 = 1.0; target.b1 = 0.0; target.b2 = 0.0
            target.a1 = 0.0; target.a2 = 0.0
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
     * SolLuxe™ 2.0 4-Hardware x 3-Gender (12-Combination) Studio Acoustic Matrix (#27 & #28):
     * - Mode 1 (PHONE_STUDIO / SPEAKER_AEC): Recreates nearfield studio cardioid proximity (+3.8/+2.8/+2.5 dB)
     *   for a phone held 12-15 inches away, with a surgical narrow Q (2.10-2.20) boxiness cut at 335/355/380 Hz
     *   that never drags down 100-240 Hz chest warmth, plus gentle +1.8/+2.0/+2.2 dB Silk Air.
     * - Mode 2 (WIRED_USB_CLOSE): Scales proximity boost to +1.6/+1.2/+1.0 dB and mud cut to -1.0 dB
     *   because headset boom / lapel mics already sit 2-4 inches from the mouth without cupped hands.
     * - Mode 3 (BLUETOOTH_SCO): Shifts vocal presence to 2.4-2.7 kHz, zeroes >5.2 kHz shelf (preventing
     *   16 kHz Bluetooth codec hiss boost), and lowers De-Esser to 4.8-5.2 kHz.
     */
    private fun updateGenderAndHardwareFiltersIfNeeded(
        gender: String,
        hwMode: HardwareAcousticMode
    ) {
        if (gender == configuredGender && hwMode == configuredHwMode) return
        configuredGender = gender
        configuredHwMode = hwMode

        when (hwMode) {
            HardwareAcousticMode.WIRED_USB_CLOSE -> {
                allowUpperAirExciter = true
                subHarmonicDrive = 0.065f
                deEssThreshold = 0.092f
                when (gender) {
                    "MALE" -> {
                        configurePeakingEq(eqLowWarmth, 110.0, 0.90, 1.6)
                        configurePeakingEq(eqMudCut, 335.0, 2.20, -1.0)
                        configurePeakingEq(eqPresence, 3100.0, 1.05, 1.8)
                        configureBandPass(subWarmthBandFilter, 110.0, 1.10)
                        configureHighShelf(eqVelvetAir, 5200.0, 1.6)
                        configureBandPass(aiPresenceTamer, 3100.0, 1.20)
                        configureBandPass(sibilanceBand, 6000.0, 1.75)
                    }
                    "FEMALE" -> {
                        configurePeakingEq(eqLowWarmth, 190.0, 0.95, 1.0)
                        configurePeakingEq(eqMudCut, 380.0, 2.10, -1.0)
                        configurePeakingEq(eqPresence, 3500.0, 1.05, 1.8)
                        configureBandPass(subWarmthBandFilter, 195.0, 1.10)
                        configureHighShelf(eqVelvetAir, 5600.0, 2.0)
                        configureBandPass(aiPresenceTamer, 3500.0, 1.20)
                        configureBandPass(sibilanceBand, 6800.0, 1.75)
                    }
                    else -> {
                        configurePeakingEq(eqLowWarmth, 145.0, 0.90, 1.2)
                        configurePeakingEq(eqMudCut, 355.0, 2.15, -1.0)
                        configurePeakingEq(eqPresence, 3300.0, 1.05, 1.8)
                        configureBandPass(subWarmthBandFilter, 145.0, 1.10)
                        configureHighShelf(eqVelvetAir, 5400.0, 1.8)
                        configureBandPass(aiPresenceTamer, 3300.0, 1.20)
                        configureBandPass(sibilanceBand, 6400.0, 1.75)
                    }
                }
            }

            HardwareAcousticMode.BLUETOOTH_SCO -> {
                allowUpperAirExciter = false
                subHarmonicDrive = 0.120f
                deEssThreshold = 0.088f
                when (gender) {
                    "MALE" -> {
                        configurePeakingEq(eqLowWarmth, 115.0, 0.90, 2.4)
                        configurePeakingEq(eqMudCut, 320.0, 2.10, -2.6)
                        configurePeakingEq(eqPresence, 2400.0, 1.00, 2.6)
                        configureBandPass(subWarmthBandFilter, 112.0, 1.10)
                        configureHighShelf(eqVelvetAir, 5200.0, 0.0)
                        configureBandPass(aiPresenceTamer, 2500.0, 1.20)
                        configureBandPass(sibilanceBand, 4800.0, 1.65)
                    }
                    "FEMALE" -> {
                        configurePeakingEq(eqLowWarmth, 195.0, 0.95, 1.8)
                        configurePeakingEq(eqMudCut, 365.0, 2.10, -2.4)
                        configurePeakingEq(eqPresence, 2700.0, 1.00, 2.6)
                        configureBandPass(subWarmthBandFilter, 195.0, 1.10)
                        configureHighShelf(eqVelvetAir, 5400.0, 0.0)
                        configureBandPass(aiPresenceTamer, 2750.0, 1.20)
                        configureBandPass(sibilanceBand, 5200.0, 1.65)
                    }
                    else -> {
                        configurePeakingEq(eqLowWarmth, 150.0, 0.90, 2.0)
                        configurePeakingEq(eqMudCut, 340.0, 2.10, -2.5)
                        configurePeakingEq(eqPresence, 2550.0, 1.00, 2.6)
                        configureBandPass(subWarmthBandFilter, 148.0, 1.10)
                        configureHighShelf(eqVelvetAir, 5300.0, 0.0)
                        configureBandPass(aiPresenceTamer, 2600.0, 1.20)
                        configureBandPass(sibilanceBand, 5000.0, 1.65)
                    }
                }
            }

            HardwareAcousticMode.PHONE_STUDIO,
            HardwareAcousticMode.SPEAKER_AEC -> {
                allowUpperAirExciter = true
                subHarmonicDrive = if (gender == "MALE") 0.115f else 0.095f
                deEssThreshold = 0.095f
                when (gender) {
                    "MALE" -> {
                        configurePeakingEq(eqLowWarmth, 108.0, 0.85, 3.8)
                        configurePeakingEq(eqMudCut, 335.0, 2.20, -2.2)
                        configurePeakingEq(eqPresence, 3100.0, 1.05, 2.0)
                        // Centered at 110 Hz (Q=1.10) so 2f harmonics enrich 170-235 Hz baritone chest without spilling into 335 Hz!
                        configureBandPass(subWarmthBandFilter, 110.0, 1.10)
                        configureHighShelf(eqVelvetAir, 5100.0, 1.8)
                        configureBandPass(aiPresenceTamer, 3100.0, 1.20)
                        configureBandPass(sibilanceBand, 6000.0, 1.75)
                    }
                    "FEMALE" -> {
                        configurePeakingEq(eqLowWarmth, 190.0, 0.95, 2.5)
                        configurePeakingEq(eqMudCut, 380.0, 2.10, -2.2)
                        configurePeakingEq(eqPresence, 3500.0, 1.05, 2.0)
                        configureBandPass(subWarmthBandFilter, 195.0, 1.10)
                        configureHighShelf(eqVelvetAir, 5600.0, 2.2)
                        configureBandPass(aiPresenceTamer, 3500.0, 1.20)
                        configureBandPass(sibilanceBand, 6800.0, 1.75)
                    }
                    else -> {
                        configurePeakingEq(eqLowWarmth, 145.0, 0.90, 2.8)
                        configurePeakingEq(eqMudCut, 355.0, 2.15, -2.2)
                        configurePeakingEq(eqPresence, 3300.0, 1.05, 2.0)
                        configureBandPass(subWarmthBandFilter, 145.0, 1.10)
                        configureHighShelf(eqVelvetAir, 5350.0, 2.0)
                        configureBandPass(aiPresenceTamer, 3300.0, 1.20)
                        configureBandPass(sibilanceBand, 6400.0, 1.75)
                    }
                }
            }
        }
    }

    fun processMonoFrameInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return

        val targetMute = if (streamMicMuted) 0.0f else 1.0f
        val preset = voicePreset
        val rawMode = (preset == "RAW")
        val hpfHz = hpfCutoffHz
        val hwMode = hardwareAcousticMode
        val useTransient = transientTamerEnabled && !rawMode
        val postDspGain = micGainLinear

        updateHpfIfNeeded(hpfHz, hwMode)
        updateGenderAndHardwareFiltersIfNeeded(voiceGenderProfile, hwMode)

        // === PASS 1 (Unity 1.0x Gain): Smooth Mute + Stage 1 (64-Bit HPF) + Stage 2 (Spark/Hammer & Grip Blanker) ===
        var impulseSamplesInFrame = 0
        val closeMicMode = (hwMode == HardwareAcousticMode.WIRED_USB_CLOSE)

        for (i in 0 until count) {
            muteSmoothGain += 0.015f * (targetMute - muteSmoothGain)
            val rawIn = buffer[i]
            // Unity 1.0x input (postDspGain is applied at Stage 10 so RNNoise & Slow AGC never fight Mic Boost!)
            var s = (if (rawIn.isFinite()) rawIn else 0f) * muteSmoothGain

            if (!rawMode && hpfHz > 0) {
                s = hpfFilter.process(s)
            }

            if (useTransient) {
                val absS = abs(s)
                val slew = abs(s - prevRawSample)
                prevRawSample = s

                // Track 220 Hz vocal-cord fundamental vs extreme electric spark/hammer slew.
                val vocalFund = abs(vocalBodyDetector.process(s))
                vocalBodyEnv += (if (vocalFund > vocalBodyEnv) 0.10f else 0.004f) * (vocalFund - vocalBodyEnv)
                slewFastEnv += (if (slew > slewFastEnv) 0.45f else 0.03f) * (slew - slewFastEnv)
                transientFastEnv += (if (absS > transientFastEnv) 0.35f else 0.015f) * (absS - transientFastEnv)

                val slewMult = if (closeMicMode) 6.0f else 6.5f
                val slewCeiling = max(0.080f, slewSlowEnv * slewMult)
                val ampCeiling = max(0.220f, transientSlowEnv * 7.2f)
                val lacksVocalBody = vocalBodyEnv < (transientFastEnv * 0.22f)
                val isSparkOrHammer = ((slewFastEnv > slewCeiling && absS > 0.14f) ||
                    (transientFastEnv > ampCeiling && slew > 0.055f)) && lacksVocalBody

                if (isSparkOrHammer) {
                    // Hold blanker for 4.5 ms (216 samples) per strike & freeze slow baselines
                    sparkBlankerHoldSamples = 216
                    impulseSamplesInFrame++
                } else {
                    if (sparkBlankerHoldSamples > 0) {
                        sparkBlankerHoldSamples--
                        impulseSamplesInFrame++
                    } else {
                        transientSlowEnv += 0.0025f * (absS - transientSlowEnv)
                        slewSlowEnv += 0.0025f * (slew - slewSlowEnv)
                    }
                }

                if (sparkBlankerHoldSamples > 0) {
                    val safeCeiling = if (vadSpeechActive) {
                        max(vocalBodyEnv * 2.2f, transientSlowEnv * 2.4f)
                    } else {
                        max(0.004f, transientSlowEnv * 1.15f)
                    }
                    if (absS > safeCeiling) {
                        val atten = (safeCeiling / max(absS, 1e-5f)).coerceIn(0.08f, 1.0f)
                        s *= atten
                    }
                }
            } else {
                prevRawSample = s
            }

            if (abs(s) < 1e-20f) s = 0f
            buffer[i] = s
        }

        if (rawMode) {
            var rawPeak = 0f
            var rawSumSq = 0f
            for (i in 0 until count) {
                var outS = buffer[i] * postDspGain
                if (abs(outS) > 0.93f) {
                    val sign = if (outS >= 0f) 1f else -1f
                    val excess = abs(outS) - 0.93f
                    outS = sign * (0.93f + (0.065f * excess) / (0.065f + excess))
                }
                buffer[i] = outS
                val a = abs(outS)
                if (a > rawPeak) rawPeak = a
                rawSumSq += a * a
                if (a > 0.99f) clipCount++
            }
            val rawRms = sqrt(rawSumSq / count)
            lastPeakDb = linearToDb(rawPeak)
            lastRmsDb = linearToDb(rawRms)
            vadSpeechActive = (muteSmoothGain > 0.05f) && (rawRms > 0.010f)
            excitedCalloutActive = vadSpeechActive && (rawRms > 0.18f)
            return
        }

        // === PASS 2: Stage 3 — Phase-Aligned Native C 48 kHz RNNoise (Running at Pure 1.0x Unity Gain) ===
        val nsMode = noiseSuppressMode
        val nsLevel = noiseSuppressLevel
        var neuralVadProb = -1.0f
        var ranNativeRnnoise = false

        if (nsMode == "RNNOISE" && nsLevel > 0 && count == 480 && sampleRate == 48000) {
            val wetStrength = when (nsLevel) {
                1 -> 0.65f
                2 -> 0.88f
                else -> 0.98f
            }
            neuralVadProb = runNativeRnnoiseLocked(buffer, wetStrength)
            ranNativeRnnoise = (neuralVadProb >= 0.0f)
        }

        // Measure post-HPF / post-RNNoise unity frame energy & zero-crossing rate for VAD & noise-floor tracking
        var preDspSumSq = 0.0f
        var zeroCrossings = 0
        var prevSign = if (buffer[0] >= 0f) 1 else -1

        for (i in 0 until count) {
            val s = buffer[i]
            preDspSumSq += s * s
            val sign = if (s >= 0f) 1 else -1
            if (sign != prevSign) {
                zeroCrossings++
                prevSign = sign
            }
        }

        val preDspRms = sqrt(preDspSumSq / count)

        // Do not let short spark/hammer frames pollute the stationary room noise-floor tracker
        val frameHadImpulse = impulseSamplesInFrame > (count / 8)
        val likelyQuiet = if (neuralVadProb >= 0f) {
            neuralVadProb < 0.25f && preDspRms < noiseFloorEnv * 1.8f && !frameHadImpulse
        } else {
            preDspRms < noiseFloorEnv * 1.6f && !frameHadImpulse
        }
        if (likelyQuiet) {
            noiseFloorEnv = 0.96f * noiseFloorEnv + 0.04f * max(preDspRms, 0.00010f)
        } else if (!frameHadImpulse) {
            noiseFloorEnv = min(0.035f, noiseFloorEnv * 1.0003f)
        }
        lastNoiseFloorDb = linearToDb(noiseFloorEnv)

        // Stage 0 + Stage 4 Speaker-Bleed Gate Guard (#27):
        // When Speaker Echo Canceler is ON (SPEAKER_AEC) and loud game gunfire plays over the phone's
        // speakers, require higher vocal harmonic confidence so speaker bleed never opens the mic gate!
        val speakerBleed = if (hwMode == HardwareAcousticMode.SPEAKER_AEC) {
            speakerBleedLevel.coerceIn(0f, 1f)
        } else {
            0f
        }
        val vadProbFloor = 0.48f + (speakerBleed * 0.16f)
        val rmsSpeechFloor = 0.0030f + (speakerBleed * 0.0035f)

        val zcrRatio = zeroCrossings.toFloat() / count.toFloat()
        val snrRatio = preDspRms / max(noiseFloorEnv, 0.00015f)
        val hasVocalHarmonics = vocalBodyEnv > (preDspRms * 0.16f) && zcrRatio < 0.45f
        val energySpeech = (preDspRms > (0.0055f + speakerBleed * 0.004f)) &&
            (snrRatio > (2.2f + speakerBleed * 0.8f)) &&
            hasVocalHarmonics

        // 0-ms Instant Vocal Open: opens on the VERY FIRST frame of real speech so initial
        // consonants/syllables are NEVER clipped, while !frameHadImpulse blocks mosquito zaps!
        val confirmedSpeech = (muteSmoothGain > 0.05f) && !frameHadImpulse && (
            (neuralVadProb >= vadProbFloor && preDspRms > rmsSpeechFloor) ||
                (neuralVadProb < 0f && energySpeech) ||
                (snrRatio > (3.2f + speakerBleed) && preDspRms > 0.0080f && hasVocalHarmonics)
            )
        lastVadProbability = if (neuralVadProb >= 0f) neuralVadProb else if (confirmedSpeech) 0.85f else 0.05f

        val holdDurationSamples = (sampleRate * 0.18f).toInt()
        if (confirmedSpeech) {
            gateHoldSamples = holdDurationSamples
        } else if (gateHoldSamples > 0) {
            gateHoldSamples = max(0, gateHoldSamples - count)
        }
        val activeSpeech = confirmedSpeech || gateHoldSamples > 0
        vadSpeechActive = activeSpeech
        excitedCalloutActive = confirmedSpeech && (preDspRms >= 0.085f)

        // Pure Single-Pass Rule: NEVER run Light Spectral low-pass filter when Native C RNNoise already ran!
        val runLightSpectral = (nsLevel > 0 && !ranNativeRnnoise &&
            (nsMode == "LIGHT" || nsMode == "RNNOISE" || (nsMode == "HW_NS" && !platformHwNsAttached)))

        val targetSuppressGain = if (runLightSpectral) {
            val strength = when (nsLevel) {
                1 -> 0.50f
                2 -> 0.76f
                else -> 0.86f
            }
            val cleanRatio = ((snrRatio - 1.2f) / 3.5f).coerceIn(0f, 1f)
            (1.0f - strength * (1.0f - cleanRatio)).coerceIn(0.14f, 1.0f)
        } else {
            1.0f
        }

        // Stage 8 Zone 2: Speech-Gated Slow Broadcast AGC (operates on pre-boost unity signal so it NEVER fights Mic Boost!)
        val useSlowAgc = slowAgcEnabled
        if (useSlowAgc) {
            if (confirmedSpeech && preDspRms in 0.012f..0.200f) {
                val targetSpeechRms = 0.115f
                val desiredAgc = (targetSpeechRms / preDspRms).coerceIn(0.84f, 1.38f)
                val agcRate = if (desiredAgc < slowAgcGain) 0.045f else 0.018f
                slowAgcGain += agcRate * (desiredAgc - slowAgcGain)
            }
        } else {
            slowAgcGain += 0.05f * (1.0f - slowAgcGain)
        }
        val currentAgcGain = slowAgcGain

        val useGate = gateEnabled
        val useEq = eqEnabled
        val useVelvet = velvetWarmthEnabled
        val useAiTone = aiToneBalancerEnabled && (preset in setOf("SOL_LUXE", "VELVET", "STUDIO"))
        val useWhisper = whisperLiftEnabled
        val useComp = compressorEnabled
        val useDeEss = deEsserEnabled

        val idleGateFloor = if (speakerBleed > 0.25f) 0.12f else 0.28f
        val targetGate = if (!useGate || activeSpeech) 1.0f else if (frameHadImpulse) 0.10f else idleGateFloor
        val targetAirMix = if (useVelvet && activeSpeech) 1.0f else 0.0f

        // Stage 7: AI Dynamic Vocal Tone Balancer target calculation
        // - Soft/calm speech (preDspRms < 0.065): gently reinforces chest warmth (+15%..+22%)
        // - Loud shouted gunfight callout (preDspRms > 0.105): dynamically trims 3.1-3.5 kHz bite
        val targetAiWarmthBoost = if (useAiTone && activeSpeech && preDspRms < 0.065f) {
            ((0.065f - preDspRms) / 0.065f).coerceIn(0.0f, 0.22f)
        } else {
            0.0f
        }
        val targetAiBiteTrim = if (useAiTone && activeSpeech && preDspRms > 0.105f) {
            ((preDspRms - 0.105f) / 0.180f).coerceIn(0.0f, 0.32f)
        } else {
            0.0f
        }

        var targetCompBlockGain = 1.0f
        var minCompGainInFrame = 1.0f
        var finalPeak = 0.0f
        var finalSumSq = 0.0f
        val warmDrive = subHarmonicDrive
        val enableUpperExciter = allowUpperAirExciter
        val currentSibThreshold = deEssThreshold

        // === PASS 3: Stage 4 (Gate) -> Stage 5a (Proximity Chest Warmth) -> Stage 6a (2f Sub-Harmonic Tube Warmth)
        //          -> Stage 5b/c (Surgical Narrow Q=2.2 Mud Cut + Presence) -> Stage 6b (Silk Air Shelf)
        //          -> Stage 7 (AI Tone Balancer) -> Stage 8 (3-Zone Dynamics)
        //          -> Stage 9 (Post-Compressor Split-Band De-Esser) -> Stage 10 (True Post-DSP Mic Boost & Safety Clip) ===
        for (i in 0 until count) {
            var s = buffer[i]

            if (runLightSpectral) {
                suppressSmoothGain += 0.010f * (targetSuppressGain - suppressSmoothGain)
                lpNoiseState += 0.22f * (s - lpNoiseState)
                val highBand = s - lpNoiseState
                s = (lpNoiseState * suppressSmoothGain) + (highBand * suppressSmoothGain * suppressSmoothGain)
            }

            // Stage 4: 0-ms Instant Vocal Gate & Soft 2.2:1 Breath Expander
            if (useGate) {
                val gCoeff = if (targetGate > gateGain) 0.080f else if (frameHadImpulse) 0.015f else 0.0012f
                gateGain += gCoeff * (targetGate - gateGain)
                s *= gateGain
            }

            // Stage 5 Band 1: Studio Cardioid Proximity Chest Warmth (108 / 145 / 190 Hz)
            if (useEq) {
                s = eqLowWarmth.process(s)
            }

            // Extract isolated vocal fundamental (110 / 145 / 195 Hz) BEFORE mud cut so 2f harmonics
            // are generated cleanly and any upper harmonic spill near 335 Hz is cleaned by eqMudCut!
            val subChestBand = if (useVelvet || useAiTone) subWarmthBandFilter.process(s) else 0.0f

            if (useVelvet) {
                val subClamped = subChestBand.coerceIn(-0.85f, 0.85f)
                val sq = subClamped * abs(subClamped) + (0.45f * subClamped * subClamped)
                velvetDcState += 0.003f * (sq - velvetDcState)
                val baritoneTubeHarmonics = warmDrive * (sq - velvetDcState)
                s += baritoneTubeHarmonics
            }

            // Stage 5 Band 2 & Band 3: Surgical Narrow Q=2.2 Boxiness Cut (335/355/380 Hz) + Callout Presence
            if (useEq) {
                s = eqMudCut.process(s)
                s = eqPresence.process(s)
            }

            // Stage 6 Part B: Gentle Silk Air High-Shelf (5.1 / 5.35 / 5.6 kHz)
            if (useVelvet) {
                val clamped = s.coerceIn(-1.2f, 1.2f)
                val airExciter = if (enableUpperExciter) {
                    exciterLpState += 0.38f * (clamped - exciterLpState)
                    val highPresence = clamped - exciterLpState
                    highPresence * (0.07f + 0.09f * abs(highPresence))
                } else {
                    0.0f
                }
                val warmed = clamped + (airExciter * velvetAirMixSmooth)
                velvetAirMixSmooth += 0.015f * (targetAirMix - velvetAirMixSmooth)
                val aired = eqVelvetAir.process(warmed)
                s = warmed + velvetAirMixSmooth * (aired - warmed)
            }

            // Stage 7: AI Dynamic Vocal Tone Balancer (Warmth reinforcement on whispers; bite taming on shouts)
            if (useAiTone) {
                aiWarmthBoostSmooth += 0.008f * (targetAiWarmthBoost - aiWarmthBoostSmooth)
                aiBiteTrimSmooth += 0.012f * (targetAiBiteTrim - aiBiteTrimSmooth)
                val presenceBand = aiPresenceTamer.process(s)
                aiLowEnv += 0.02f * (abs(subChestBand) - aiLowEnv)
                aiHighEnv += 0.02f * (abs(presenceBand) - aiHighEnv)
                s += (subChestBand * aiWarmthBoostSmooth) - (presenceBand * aiBiteTrimSmooth)
            }

            // Stage 8: Unified Non-Overlapping 3-Zone Dynamics (Whisper Lift -> Slow AGC -> 12ms-Punch 3:1 Compressor)
            val absIn = abs(s)
            compEnv += (if (absIn > compEnv) 0.0018f else 0.00045f) * (absIn - compEnv)

            if ((i and 15) == 0) {
                val threshold = 0.240f
                targetCompBlockGain = when {
                    useComp && compEnv > threshold -> {
                        val atten = (threshold / compEnv).pow(0.6667f)
                        if (atten < minCompGainInFrame) minCompGainInFrame = atten
                        atten * 1.12f
                    }
                    useWhisper && activeSpeech && compEnv in 0.009f..0.072f -> {
                        val lift = (0.072f / max(compEnv, 0.020f)).pow(0.30f).coerceIn(1.0f, 1.48f)
                        lift
                    }
                    else -> {
                        val baseMakeup = if (useComp) 1.06f else 1.0f
                        baseMakeup * currentAgcGain
                    }
                }
            }

            compGainSmooth += 0.06f * (targetCompBlockGain - compGainSmooth)
            s *= compGainSmooth

            // Stage 9: Post-Compressor Split-Band De-Esser (catches any sibilance boosted by Velvet Air or Compression!)
            if (useDeEss) {
                val sibSignal = sibilanceBand.process(s)
                val absSib = abs(sibSignal)
                deEssEnv += (if (absSib > deEssEnv) 0.14f else 0.008f) * (absSib - deEssEnv)
                if (deEssEnv > currentSibThreshold) {
                    val keepRatio = (currentSibThreshold / deEssEnv).coerceIn(0.38f, 1.0f)
                    s -= sibSignal * (1.0f - keepRatio)
                }
            }

            // Stage 10: True Post-DSP Mic Boost (50%..250%) + Soft Output Safety Ceiling
            s *= postDspGain

            if (abs(s) > 0.92f) {
                val sign = if (s >= 0f) 1f else -1f
                val excess = abs(s) - 0.92f
                s = sign * (0.92f + (0.075f * excess) / (0.075f + excess))
                if (abs(s) > 0.99f) clipCount++
            }

            val absOut = abs(s)
            if (absOut > finalPeak) finalPeak = absOut
            finalSumSq += s * s
            buffer[i] = s
        }

        val finalRms = sqrt(finalSumSq / count)
        lastPeakDb = linearToDb(finalPeak)
        lastRmsDb = linearToDb(finalRms)
        lastGainReductionDb = if (minCompGainInFrame < 0.999f) {
            -linearToDb(minCompGainInFrame)
        } else {
            0f
        }
    }

    companion object {
        val VOICE_PRESET_ORDER = listOf("RAW", "CLEAN", "NEURAL", "STUDIO", "VELVET", "SOL_LUXE")
        val GENDER_PROFILE_ORDER = listOf("NEUTRAL", "MALE", "FEMALE")
        val NOISE_MODE_ORDER = listOf("OFF", "LIGHT", "RNNOISE", "HW_NS")
        val HPF_CUTOFF_STEPS = listOf(0, 50, 60, 70, 75, 80, 85, 95, 105, 120)

        fun presetDisplayName(preset: String): String = when (preset.uppercase(Locale.US)) {
            "RAW" -> "RAW (Dry Mic)"
            "CLEAN" -> "CLEAN (Natural)"
            "NEURAL" -> "NEURAL (C RNNoise)"
            "STUDIO" -> "RADIO STUDIO"
            "VELVET" -> "VELVET (Warm Air)"
            "SOL_LUXE" -> "SOL LUXE (Studio+Velvet)"
            else -> preset
        }

        /**
         * Switches the user's Voice Gender Profile (MALE / NEUTRAL / FEMALE) and automatically
         * sets the gender-matched High-Pass Filter cutoff (60 Hz Male / 75 Hz Neutral / 95 Hz Female).
         */
        fun applyGenderProfileToSettings(current: StreamSettings, newGender: String): StreamSettings {
            val normGender = when (newGender.uppercase(Locale.US)) {
                "MALE" -> "MALE"
                "FEMALE" -> "FEMALE"
                else -> "NEUTRAL"
            }
            val matchedHpf = KeystoreVault.resolveDefaultHpfForProfile(normGender)
            return current.copy(
                voiceGenderProfile = normGender,
                hpfCutoffHz = if (current.hpfCutoffHz == 0) 0 else matchedHpf
            )
        }

        fun applyPresetToSettings(current: StreamSettings, newPreset: String): StreamSettings {
            val p = newPreset.uppercase(Locale.US)
            val matchedHpf = KeystoreVault.resolveDefaultHpfForProfile(current.voiceGenderProfile)
            val activeHpf = if (current.hpfCutoffHz <= 0 || current.hpfCutoffHz in setOf(60, 75, 80, 85, 95, 100)) {
                matchedHpf
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
