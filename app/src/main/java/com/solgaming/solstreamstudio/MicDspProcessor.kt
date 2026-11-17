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
 * Automatically adapts proximity chest warmth, analog triode sub-warmth, hand-grip mud scoop,
 * 920 Hz sweet velvet dip, hard-breath plosive guard, Aphex Silk Air exciter, and Bluetooth
 * >5.2 kHz hiss roll-off across all 3 gender profiles (12 combinations total).
 */
enum class HardwareAcousticMode(val title: String) {
    PHONE_STUDIO("48kHz Phone Studio Mic (Hand-Grip Tuned)"),
    WIRED_USB_CLOSE("48kHz Wired / USB-C Close-Talk Mic"),
    BLUETOOTH_SCO("Bluetooth Collar / Earbud Mic (Anti-Hiss)"),
    SPEAKER_AEC("Phone Speakerphone Mode (Echo-Guard)")
}

class MicDspProcessor(private val sampleRate: Int = 48000) {

    // Pick-1 selectors (Locked to optimal out-of-the-box studio defaults)
    @Volatile var voiceGenderProfile: String = "NEUTRAL" // "NEUTRAL" (65Hz), "MALE" (50Hz), "FEMALE" (85Hz)
    @Volatile var voicePreset: String = "SOL_LUXE"       // "RAW", "CLEAN", "NEURAL", "STUDIO", "VELVET", "SOL_LUXE"
    @Volatile var noiseSuppressMode: String = "RNNOISE"  // "OFF", "LIGHT", "RNNOISE", "HW_NS"
    @Volatile var noiseSuppressLevel: Int = 2            // 0 = Off, 1 = Gentle(65%), 2 = Natural(88%), 3 = Max(100%)

    // Stackable independent stage switches (100% mix-and-match freedom; studio defaults ON)
    @Volatile var hpfCutoffHz: Int = 65                  // 0=Off, 50(Male), 55, 60, 65(Neutral), 70, 75, 85(Female), 95, 105, 120 Hz
    @Volatile var transientTamerEnabled: Boolean = true  // Stage 2: Spark/Hammer & Grip Blanker
    @Volatile var gateEnabled: Boolean = true            // Stage 4 & 5: 0-ms Instant Vocal Gate + Hard-Breath Plosive Guard
    @Volatile var eqEnabled: Boolean = true              // Stage 6 & 8: Deep Proximity, Deeper 315-365Hz Mud Scoop, 920Hz Velvet Dip & Presence
    @Volatile var velvetWarmthEnabled: Boolean = true    // Stage 7 & 9: Cubic Analog Sub-Warmth + Aphex Silk Air Exciter & Shelf
    @Volatile var aiToneBalancerEnabled: Boolean = true  // Stage 10: AI Dynamic Vocal Tone Balancer
    @Volatile var compressorEnabled: Boolean = true      // Stage 11: High-Pass Sidechain 12ms-Punch 3:1 Broadcast Leveler
    @Volatile var whisperLiftEnabled: Boolean = true     // Stage 11: Quiet Callout Booster
    @Volatile var slowAgcEnabled: Boolean = true         // Stage 11: Speech-Gated Slow Broadcast Loudness Ride
    @Volatile var deEsserEnabled: Boolean = true         // Stage 12: Post-Compressor Split-Band Sibilance Tamer

    // Stage 13: True Post-DSP Makeup Gain (50%..250%) — placed AFTER Slow AGC & Compressor!
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
    @Volatile var cpuEcoBypassActive: Boolean = false

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
    private val vocalBodyDetector = Biquad64()      // Independent 250 Hz Q=1.15 vowel-cord harmonic detector (VAD, Spark & Breath Guard)
    private val eqLowWarmth = Biquad64()            // Stage 6 Band 1: Tight Cardioid Proximity Chest Contour (95/130/175 Hz)
    private val subWarmthBandFilter = Biquad64()    // Stage 7a: Isolated Vocal Fundamental Extractor (92/128/170 Hz)
    private val subOctaveLowPassFilter = Biquad64() // Stage 7b: 2nd-Order Butterworth Smoothing Filter for Cubic Analog Sub-Warmth
    private val eqMudCut = Biquad64()               // Stage 8a Band 2: Deeper Q=1.65 Room & Hand-Grip Boxiness Scoop (315/340/365 Hz)
    private val eqVelvetDip = Biquad64()            // Stage 8b Band 3: 920/980/1050 Hz "Sweet Velvet" Anti-Nasal Dip
    private val eqPresence = Biquad64()             // Stage 8c Band 4: Silky Intimate Articulation Presence (2.85/3.15/3.40 kHz)
    private val airExciterSourceBand = Biquad64()   // Stage 9a: 2.4-3.6 kHz Vocal Harmonic Extractor for Aphex Silk Air Synthesis
    private val eqVelvetAir = Biquad64()            // Stage 9b: Intimate Silk Air High-Shelf (6.5/6.7/7.0 kHz)
    private val aiPresenceTamer = Biquad64()        // Stage 10: AI Dynamic Shout Tamer (2.95-3.45 kHz detector)
    private val sibilanceBand = Biquad64()          // Stage 12: Post-Compressor Split-Band De-Esser detector (5.8/6.25/6.7 kHz)

    private val nativeLock = Any()
    @Volatile private var nativeRnnHandle: Long = 0L
    @Volatile private var released: Boolean = false

    private var configuredEffectiveHpfHz = -1
    private var configuredGender = ""
    private var configuredHwMode: HardwareAcousticMode? = null
    private var configuredStudioForwardMode = false

    // Stage 2: Consonant-safe multi-strike mosquito-racket spark ("phat-phat-phat") & hammer blanker state
    private var muteSmoothGain = 1.0f
    private var prevRawSample = 0.0f
    private var slewFastEnv = 0.0f
    private var slewSlowEnv = 0.0015f
    private var transientFastEnv = 0.0f
    private var transientSlowEnv = 0.008f
    private var vocalBodyEnv = 0.002f
    private var sparkBlankerHoldSamples = 0

    // Stage 4 & Stage 5: 0-ms Instant Vocal VAD, 2.2:1 Breath Expander & Zero-Phase-Peak 2-Stage Hard-Breath Plosive Guard
    private var gateGain = 1.0f
    private var gateHoldSamples = 0
    private var breathLp1State = 0.0f
    private var breathLp2State = 0.0f
    private var breathLowEnv = 0.0005f
    private var breathSubtractSmooth = 0.0f

    // Stage 3 fallback: Stationary light spectral state (strictly bypassed when Native C RNNoise runs!)
    private var noiseFloorEnv = 0.0018f
    private var suppressSmoothGain = 1.0f
    private var lpNoiseState = 0.0f

    // Stage 7 & Stage 9: Pure Analog Cubic Sub-Warmth + Pure 2f Upper-Harmonic "Aphex Silk Air" Exciter states
    private var subCubicDrive = 0.14f
    private var tube2fDrive = 0.09f
    private var airExciterDrive = 0.24f
    private var velvetDcState = 0.0f
    private var airExciterHpState = 0.0f
    private var velvetAirMixSmooth = 0.0f
    private var allowUpperAirExciter = true

    // Stage 10, 11, 12 states: AI Tone Balancer, High-Pass Sidechain 3-Zone Dynamics & Post-Comp De-Esser
    private var aiLowEnv = 0.0f
    private var aiHighEnv = 0.0f
    private var aiWarmthBoostSmooth = 0.0f
    private var aiBiteTrimSmooth = 0.0f

    private var compEnv = 0.0f
    private var compGainSmooth = 1.0f
    private var slowAgcGain = 1.0f
    private var deEssEnv = 0.0f
    private var deEssThreshold = 0.088f

    // Low-end CPU budget watchdog state (protects 3-4 GB entry phones under heavy thermal throttling)
    private var slowFrameStrikeCount = 0
    private var ecoBypassRemainingFrames = 0

    init {
        configureBandPass(vocalBodyDetector, 250.0, 1.15)
        updateGenderAndHardwareFiltersIfNeeded(voiceGenderProfile, hardwareAcousticMode, voicePreset == "STUDIO")
        updateHpfIfNeeded(hpfCutoffHz, voiceGenderProfile, hardwareAcousticMode)
        refreshDenoiserSummary()
    }

    /**
     * Updates the active hardware acoustic mode and immediately re-tunes the 12-mode filter matrix.
     * Named applyHardwareAcousticMode to avoid clashing with Kotlin's auto-generated setter for var hardwareAcousticMode.
     */
    fun applyHardwareAcousticMode(mode: HardwareAcousticMode) {
        if (hardwareAcousticMode != mode) {
            hardwareAcousticMode = mode
            updateGenderAndHardwareFiltersIfNeeded(voiceGenderProfile, mode, voicePreset == "STUDIO")
            updateHpfIfNeeded(hpfCutoffHz, voiceGenderProfile, mode)
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
        updateHpfIfNeeded(hpfCutoffHz, normGender, hwMode)
        updateGenderAndHardwareFiltersIfNeeded(normGender, hwMode, presetUpper == "STUDIO")
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

    private fun updateHpfIfNeeded(
        baseCutoffHz: Int,
        gender: String,
        hwMode: HardwareAcousticMode
    ) {
        val effectiveHz = if (baseCutoffHz <= 0) {
            0
        } else {
            // Exact 12-Mode Table HPF Cutoffs:
            // - PHONE_STUDIO: 50 Hz (Male), 65 Hz (Neutral), 85 Hz (Female)
            // - WIRED_USB_CLOSE (+5 Hz pop shield): 55 Hz (Male), 70 Hz (Neutral), 90 Hz (Female)
            // - BLUETOOTH_SCO (collar-rub shield): 65 Hz (Male), 75 Hz (Neutral), 95 Hz (Female)
            // - SPEAKER_AEC (speaker bass-bleed shield): 70 Hz (Male), 80 Hz (Neutral), 95 Hz (Female)
            val offset = when (hwMode) {
                HardwareAcousticMode.PHONE_STUDIO -> 0
                HardwareAcousticMode.WIRED_USB_CLOSE -> 5
                HardwareAcousticMode.BLUETOOTH_SCO -> if (gender == "MALE") 15 else 10
                HardwareAcousticMode.SPEAKER_AEC -> when (gender) {
                    "MALE" -> 20
                    "NEUTRAL" -> 15
                    else -> 10
                }
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

    private fun configureLowPass(target: Biquad64, freqHz: Double, q: Double = 0.7071) {
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
     * SolLuxe™ 2.0 12-Mode (4-Hardware x 3-Gender) Acoustic Matrix:
     * Tuned for a clean, intimate, luxury podcast/studio vocal sound across Male, Neutral, and Female voices:
     * - Tight Cardioid Proximity Chest Contour (95 / 130 / 175 Hz) without 200 Hz bloat
     * - Deeper Q=1.65 Room & Hand-Grip Boxiness Scoop (-3.5 / -3.2 / -3.0 dB @ 315 / 340 / 365 Hz)
     * - 920 / 980 / 1050 Hz "Sweet Velvet" Anti-Nasal Dip
     * - Pure 2f Upper-Harmonic "Aphex Silk Air" Exciter (2.6-3.1 kHz harmonic source -> 5.2-10.0 kHz shimmer)
     */
    private fun updateGenderAndHardwareFiltersIfNeeded(
        gender: String,
        hwMode: HardwareAcousticMode,
        isRadioStudioPreset: Boolean
    ) {
        if (gender == configuredGender &&
            hwMode == configuredHwMode &&
            isRadioStudioPreset == configuredStudioForwardMode
        ) {
            return
        }
        configuredGender = gender
        configuredHwMode = hwMode
        configuredStudioForwardMode = isRadioStudioPreset

        val studioPresenceAdd = if (isRadioStudioPreset) 0.5 else 0.0

        when (hwMode) {
            HardwareAcousticMode.PHONE_STUDIO -> {
                allowUpperAirExciter = true
                deEssThreshold = 0.086f
                when (gender) {
                    "MALE" -> {
                        subCubicDrive = 0.14f
                        tube2fDrive = 0.09f
                        airExciterDrive = 0.24f
                        configurePeakingEq(eqLowWarmth, 95.0, 0.82, 4.4)
                        configureBandPass(subWarmthBandFilter, 92.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 115.0, 0.7071)
                        configurePeakingEq(eqMudCut, 315.0, 1.65, -3.5)
                        configurePeakingEq(eqVelvetDip, 920.0, 1.40, -2.2)
                        configurePeakingEq(eqPresence, 2850.0, 1.05, 1.4 + studioPresenceAdd)
                        configureBandPass(airExciterSourceBand, 2650.0, 0.90)
                        configureHighShelf(eqVelvetAir, 6500.0, 2.5)
                        configureBandPass(aiPresenceTamer, 2950.0, 1.20)
                        configureBandPass(sibilanceBand, 5800.0, 1.65)
                    }
                    "FEMALE" -> {
                        subCubicDrive = 0.10f
                        tube2fDrive = 0.08f
                        airExciterDrive = 0.26f
                        configurePeakingEq(eqLowWarmth, 175.0, 0.90, 2.8)
                        configureBandPass(subWarmthBandFilter, 170.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 185.0, 0.7071)
                        configurePeakingEq(eqMudCut, 365.0, 1.65, -3.0)
                        configurePeakingEq(eqVelvetDip, 1050.0, 1.40, -1.8)
                        configurePeakingEq(eqPresence, 3400.0, 1.05, 1.6 + studioPresenceAdd)
                        configureBandPass(airExciterSourceBand, 3100.0, 0.90)
                        configureHighShelf(eqVelvetAir, 7000.0, 2.6)
                        configureBandPass(aiPresenceTamer, 3450.0, 1.20)
                        configureBandPass(sibilanceBand, 6700.0, 1.65)
                    }
                    else -> {
                        subCubicDrive = 0.12f
                        tube2fDrive = 0.085f
                        airExciterDrive = 0.24f
                        configurePeakingEq(eqLowWarmth, 130.0, 0.85, 3.5)
                        configureBandPass(subWarmthBandFilter, 128.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 145.0, 0.7071)
                        configurePeakingEq(eqMudCut, 340.0, 1.65, -3.2)
                        configurePeakingEq(eqVelvetDip, 980.0, 1.40, -2.0)
                        configurePeakingEq(eqPresence, 3150.0, 1.05, 1.5 + studioPresenceAdd)
                        configureBandPass(airExciterSourceBand, 2850.0, 0.90)
                        configureHighShelf(eqVelvetAir, 6700.0, 2.5)
                        configureBandPass(aiPresenceTamer, 3200.0, 1.20)
                        configureBandPass(sibilanceBand, 6250.0, 1.65)
                    }
                }
            }

            HardwareAcousticMode.WIRED_USB_CLOSE -> {
                allowUpperAirExciter = true
                deEssThreshold = 0.086f
                when (gender) {
                    "MALE" -> {
                        subCubicDrive = 0.09f
                        tube2fDrive = 0.06f
                        airExciterDrive = 0.18f
                        configurePeakingEq(eqLowWarmth, 98.0, 0.88, 2.0)
                        configureBandPass(subWarmthBandFilter, 95.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 115.0, 0.7071)
                        configurePeakingEq(eqMudCut, 315.0, 1.75, -2.2)
                        configurePeakingEq(eqVelvetDip, 920.0, 1.45, -1.6)
                        configurePeakingEq(eqPresence, 2900.0, 1.05, 1.3 + studioPresenceAdd)
                        configureBandPass(airExciterSourceBand, 2700.0, 0.90)
                        configureHighShelf(eqVelvetAir, 6700.0, 2.0)
                        configureBandPass(aiPresenceTamer, 2950.0, 1.20)
                        configureBandPass(sibilanceBand, 5800.0, 1.65)
                    }
                    "FEMALE" -> {
                        subCubicDrive = 0.07f
                        tube2fDrive = 0.055f
                        airExciterDrive = 0.20f
                        configurePeakingEq(eqLowWarmth, 178.0, 0.92, 1.2)
                        configureBandPass(subWarmthBandFilter, 172.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 185.0, 0.7071)
                        configurePeakingEq(eqMudCut, 365.0, 1.75, -1.8)
                        configurePeakingEq(eqVelvetDip, 1050.0, 1.45, -1.2)
                        configurePeakingEq(eqPresence, 3450.0, 1.05, 1.4 + studioPresenceAdd)
                        configureBandPass(airExciterSourceBand, 3150.0, 0.90)
                        configureHighShelf(eqVelvetAir, 7200.0, 2.2)
                        configureBandPass(aiPresenceTamer, 3500.0, 1.20)
                        configureBandPass(sibilanceBand, 6700.0, 1.65)
                    }
                    else -> {
                        subCubicDrive = 0.08f
                        tube2fDrive = 0.06f
                        airExciterDrive = 0.19f
                        configurePeakingEq(eqLowWarmth, 134.0, 0.90, 1.5)
                        configureBandPass(subWarmthBandFilter, 130.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 145.0, 0.7071)
                        configurePeakingEq(eqMudCut, 340.0, 1.75, -2.0)
                        configurePeakingEq(eqVelvetDip, 980.0, 1.45, -1.4)
                        configurePeakingEq(eqPresence, 3150.0, 1.05, 1.4 + studioPresenceAdd)
                        configureBandPass(airExciterSourceBand, 2900.0, 0.90)
                        configureHighShelf(eqVelvetAir, 6900.0, 2.1)
                        configureBandPass(aiPresenceTamer, 3200.0, 1.20)
                        configureBandPass(sibilanceBand, 6250.0, 1.65)
                    }
                }
            }

            HardwareAcousticMode.BLUETOOTH_SCO -> {
                allowUpperAirExciter = false
                deEssThreshold = 0.084f
                airExciterDrive = 0.0f
                when (gender) {
                    "MALE" -> {
                        subCubicDrive = 0.15f
                        tube2fDrive = 0.10f
                        configurePeakingEq(eqLowWarmth, 102.0, 0.84, 3.8)
                        configureBandPass(subWarmthBandFilter, 100.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 118.0, 0.7071)
                        configurePeakingEq(eqMudCut, 320.0, 1.70, -3.4)
                        configurePeakingEq(eqVelvetDip, 900.0, 1.35, -2.6)
                        configurePeakingEq(eqPresence, 2450.0, 1.00, 2.4)
                        configureBandPass(airExciterSourceBand, 2300.0, 0.90)
                        configureHighShelf(eqVelvetAir, 5200.0, 0.0)
                        configureBandPass(aiPresenceTamer, 2500.0, 1.20)
                        configureBandPass(sibilanceBand, 4800.0, 1.65)
                    }
                    "FEMALE" -> {
                        subCubicDrive = 0.10f
                        tube2fDrive = 0.08f
                        configurePeakingEq(eqLowWarmth, 180.0, 0.90, 2.4)
                        configureBandPass(subWarmthBandFilter, 176.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 185.0, 0.7071)
                        configurePeakingEq(eqMudCut, 365.0, 1.70, -3.0)
                        configurePeakingEq(eqVelvetDip, 1040.0, 1.35, -2.0)
                        configurePeakingEq(eqPresence, 2750.0, 1.00, 2.4)
                        configureBandPass(airExciterSourceBand, 2500.0, 0.90)
                        configureHighShelf(eqVelvetAir, 5400.0, 0.0)
                        configureBandPass(aiPresenceTamer, 2750.0, 1.20)
                        configureBandPass(sibilanceBand, 5200.0, 1.65)
                    }
                    else -> {
                        subCubicDrive = 0.12f
                        tube2fDrive = 0.09f
                        configurePeakingEq(eqLowWarmth, 138.0, 0.86, 3.0)
                        configureBandPass(subWarmthBandFilter, 134.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 148.0, 0.7071)
                        configurePeakingEq(eqMudCut, 345.0, 1.70, -3.2)
                        configurePeakingEq(eqVelvetDip, 960.0, 1.35, -2.2)
                        configurePeakingEq(eqPresence, 2600.0, 1.00, 2.4)
                        configureBandPass(airExciterSourceBand, 2400.0, 0.90)
                        configureHighShelf(eqVelvetAir, 5300.0, 0.0)
                        configureBandPass(aiPresenceTamer, 2600.0, 1.20)
                        configureBandPass(sibilanceBand, 5000.0, 1.65)
                    }
                }
            }

            HardwareAcousticMode.SPEAKER_AEC -> {
                allowUpperAirExciter = true
                deEssThreshold = 0.088f
                subCubicDrive = 0.09f
                tube2fDrive = 0.06f
                airExciterDrive = 0.16f
                when (gender) {
                    "MALE" -> {
                        configurePeakingEq(eqLowWarmth, 105.0, 0.86, 2.8)
                        configureBandPass(subWarmthBandFilter, 102.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 118.0, 0.7071)
                        configurePeakingEq(eqMudCut, 320.0, 1.70, -3.2)
                        configurePeakingEq(eqVelvetDip, 920.0, 1.40, -2.2)
                        configurePeakingEq(eqPresence, 2850.0, 1.05, 1.3)
                        configureBandPass(airExciterSourceBand, 2650.0, 0.90)
                        configureHighShelf(eqVelvetAir, 6600.0, 1.6)
                        configureBandPass(aiPresenceTamer, 2950.0, 1.20)
                        configureBandPass(sibilanceBand, 5800.0, 1.65)
                    }
                    "FEMALE" -> {
                        configurePeakingEq(eqLowWarmth, 180.0, 0.90, 1.8)
                        configureBandPass(subWarmthBandFilter, 176.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 185.0, 0.7071)
                        configurePeakingEq(eqMudCut, 365.0, 1.70, -2.8)
                        configurePeakingEq(eqVelvetDip, 1050.0, 1.40, -2.0)
                        configurePeakingEq(eqPresence, 3400.0, 1.05, 1.3)
                        configureBandPass(airExciterSourceBand, 3100.0, 0.90)
                        configureHighShelf(eqVelvetAir, 6800.0, 1.6)
                        configureBandPass(aiPresenceTamer, 3450.0, 1.20)
                        configureBandPass(sibilanceBand, 6700.0, 1.65)
                    }
                    else -> {
                        configurePeakingEq(eqLowWarmth, 140.0, 0.88, 2.2)
                        configureBandPass(subWarmthBandFilter, 136.0, 1.35)
                        configureLowPass(subOctaveLowPassFilter, 148.0, 0.7071)
                        configurePeakingEq(eqMudCut, 340.0, 1.70, -3.0)
                        configurePeakingEq(eqVelvetDip, 980.0, 1.40, -2.0)
                        configurePeakingEq(eqPresence, 3150.0, 1.05, 1.3)
                        configureBandPass(airExciterSourceBand, 2850.0, 0.90)
                        configureHighShelf(eqVelvetAir, 6700.0, 1.6)
                        configureBandPass(aiPresenceTamer, 3200.0, 1.20)
                        configureBandPass(sibilanceBand, 6250.0, 1.65)
                    }
                }
            }
        }
    }

    fun processMonoFrameInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        val frameStartNs = System.nanoTime()

        val targetMute = if (streamMicMuted) 0.0f else 1.0f
        val preset = voicePreset
        val rawMode = (preset == "RAW")
        val hpfHz = hpfCutoffHz
        val gender = voiceGenderProfile
        val hwMode = hardwareAcousticMode
        val useTransient = transientTamerEnabled && !rawMode
        val useVelvet = velvetWarmthEnabled
        val postDspGain = micGainLinear

        updateHpfIfNeeded(hpfHz, gender, hwMode)
        updateGenderAndHardwareFiltersIfNeeded(gender, hwMode, preset == "STUDIO")

        // === PASS 1 (Unity 1.0x Gain): Smooth Mute + Stage 1 (64-Bit HPF) + Stage 2 (Independent Vocal Body + Spark Blanker) ===
        var impulseSamplesInFrame = 0
        val closeMicMode = (hwMode == HardwareAcousticMode.WIRED_USB_CLOSE)

        for (i in 0 until count) {
            muteSmoothGain += 0.015f * (targetMute - muteSmoothGain)
            val rawIn = buffer[i]
            // Unity 1.0x input (postDspGain is applied at Stage 13 so RNNoise & Slow AGC never fight Mic Boost!)
            var s = (if (rawIn.isFinite()) rawIn else 0f) * muteSmoothGain

            if (!rawMode && hpfHz > 0) {
                s = hpfFilter.process(s)
            }

            // Stage 2a: Unconditional 250 Hz Q=1.15 vocal-cord harmonic tracker (runs even when transientTamerEnabled == false!)
            val vocalFund = abs(vocalBodyDetector.process(s))
            vocalBodyEnv += (if (vocalFund > vocalBodyEnv) 0.10f else 0.006f) * (vocalFund - vocalBodyEnv)

            // Stage 2b: Spark / Hammer & Hand-Grip Impulse Blanker
            if (useTransient) {
                val absS = abs(s)
                val slew = abs(s - prevRawSample)
                prevRawSample = s

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

        // Stage 0 + Stage 4 Speaker-Bleed Gate Guard
        val speakerBleed = if (hwMode == HardwareAcousticMode.SPEAKER_AEC) {
            speakerBleedLevel.coerceIn(0f, 1f)
        } else {
            0f
        }
        val vadProbFloor = 0.48f + (speakerBleed * 0.16f)
        val rmsSpeechFloor = 0.0030f + (speakerBleed * 0.0035f)

        val zcrRatio = zeroCrossings.toFloat() / count.toFloat()
        val snrRatio = preDspRms / max(noiseFloorEnv, 0.00015f)
        val hasVocalHarmonics = vocalBodyEnv > (preDspRms * 0.14f) && zcrRatio < 0.45f
        val energySpeech = (preDspRms > (0.0055f + speakerBleed * 0.004f)) &&
            (snrRatio > (2.1f + speakerBleed * 0.8f)) &&
            hasVocalHarmonics

        val confirmedSpeech = (muteSmoothGain > 0.05f) && !frameHadImpulse && (
            (neuralVadProb >= vadProbFloor && preDspRms > rmsSpeechFloor) ||
                (neuralVadProb < 0f && energySpeech) ||
                (snrRatio > (3.0f + speakerBleed) && preDspRms > 0.0075f && hasVocalHarmonics)
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

        // Stage 11 Zone 2: Speech-Gated Slow Broadcast AGC (operates on pre-boost unity signal so it NEVER fights Mic Boost!)
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

        // Low-End CPU Budget Auto-Bypass check
        val ecoBypassNow = ecoBypassRemainingFrames > 0
        if (ecoBypassRemainingFrames > 0) {
            ecoBypassRemainingFrames--
        }
        cpuEcoBypassActive = ecoBypassNow

        val useGate = gateEnabled
        val useEq = eqEnabled && !ecoBypassNow
        val useVelvetChain = useVelvet && !ecoBypassNow
        val useAiTone = aiToneBalancerEnabled && !ecoBypassNow && (preset in setOf("SOL_LUXE", "VELVET", "STUDIO"))
        val useWhisper = whisperLiftEnabled
        val useComp = compressorEnabled
        val useDeEss = deEsserEnabled && !ecoBypassNow

        val idleGateFloor = if (speakerBleed > 0.25f) 0.12f else 0.28f
        val targetGate = if (!useGate || activeSpeech) 1.0f else if (frameHadImpulse) 0.10f else idleGateFloor
        val targetAirMix = if (useVelvetChain && activeSpeech) 1.0f else 0.0f

        // Stage 10: AI Dynamic Vocal Tone Balancer (gentle whisper warmth + shout harshness trim, no bass double-stacking)
        val targetAiWarmthBoost = if (useAiTone && activeSpeech && preDspRms < 0.050f) {
            ((0.050f - preDspRms) / 0.050f).coerceIn(0.0f, 0.09f)
        } else {
            0.0f
        }
        val targetAiBiteTrim = if (useAiTone && activeSpeech && preDspRms > 0.105f) {
            ((preDspRms - 0.105f) / 0.175f).coerceIn(0.0f, 0.30f)
        } else {
            0.0f
        }

        var targetCompBlockGain = 1.0f
        var minCompGainInFrame = 1.0f
        var finalPeak = 0.0f
        var finalSumSq = 0.0f
        val curSubCubicDrive = subCubicDrive
        val curTube2fDrive = tube2fDrive
        val curAirExciterDrive = airExciterDrive
        val enableUpperExciter = allowUpperAirExciter
        val currentSibThreshold = deEssThreshold

        // === PASS 3: Full 13-Stage Non-Clashing Vocal Processing Loop ===
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

            // Stage 5: Zero-Phase-Peak 2-Stage <135 Hz Hard-Breath & Wind-Plosive Guard (placed BEFORE Stage 6 Proximity Boost!)
            // Uses a 1st-order <135 Hz low-pass split (which has strictly positive real phase across all frequencies,
            // never creating biquad phase-cancellation bumps) to compare sub-135 Hz wind turbulence (breathLowEnv)
            // against 250 Hz vocal-cord vibration (vocalBodyEnv).
            breathLp1State += 0.0174f * (s - breathLp1State)
            val absWind = abs(breathLp1State)
            breathLowEnv += (if (absWind > breathLowEnv) 0.12f else 0.008f) * (absWind - breathLowEnv)
            val isBreathPlosive = (breathLowEnv > 0.0050f) && (breathLowEnv > (vocalBodyEnv * 2.15f + 0.0020f))
            val targetBreathSub = if (isBreathPlosive) {
                val excessRatio = ((breathLowEnv - vocalBodyEnv * 2.15f) / max(breathLowEnv, 0.001f)).coerceIn(0.0f, 1.0f)
                (0.68f + 0.20f * excessRatio).coerceIn(0.0f, 0.86f)
            } else {
                0.0f
            }
            breathSubtractSmooth += (if (targetBreathSub > breathSubtractSmooth) 0.18f else 0.025f) * (targetBreathSub - breathSubtractSmooth)
            val stage1DeWind = s - (breathLp1State * breathSubtractSmooth)
            breathLp2State += 0.0174f * (stage1DeWind - breathLp2State)
            if (breathSubtractSmooth > 0.001f) {
                val stage2DeWind = stage1DeWind - (breathLp2State * breathSubtractSmooth)
                s = stage2DeWind * (1.0f - 0.45f * breathSubtractSmooth)
            }

            // Stage 6 Band 1: Tight Cardioid Proximity Chest Contour (95 / 130 / 175 Hz)
            if (useEq) {
                s = eqLowWarmth.process(s)
            }

            // Stage 7: Pure Analog Cubic Soft-Sine + Asymmetric Triode 2f Sub-Warmth Synthesizer
            // (Zero square-wave flip-flop edges -> zero 180-350 Hz odd-harmonic buzz!)
            val subChestBand = if (useVelvetChain || useAiTone || useComp) subWarmthBandFilter.process(s) else 0.0f

            if (useVelvetChain) {
                val u = (subChestBand * 1.35f).coerceIn(-0.95f, 0.95f)
                val absU = abs(u)

                // Continuous Cubic Soft-Sine Shaper: y = u - (u^3)/3
                val cubicSubWarmth = u - ((u * u * u) * 0.3333333f)
                val smoothSubWarmth = subOctaveLowPassFilter.process(cubicSubWarmth)

                // Asymmetric Triode 2f Even-Order Harmonic Exciter (DC-blocked)
                val triodeRaw = (u * absU + 0.35f * u * u) / (1.0f + absU)
                velvetDcState += 0.003f * (triodeRaw - velvetDcState)
                val clean2fTube = triodeRaw - velvetDcState

                velvetAirMixSmooth += 0.018f * (targetAirMix - velvetAirMixSmooth)
                val subAndTubeAddition = ((curSubCubicDrive * smoothSubWarmth) + (curTube2fDrive * clean2fTube)) * velvetAirMixSmooth
                s += subAndTubeAddition
            }

            // Stage 8a Band 2 (Deeper Q=1.65 Room & Hand-Grip Boxiness Scoop @ 315/340/365 Hz)
            // + Stage 8b Band 3 (920/980/1050 Hz "Sweet Velvet" Anti-Nasal Dip)
            // + Stage 8c Band 4 (Silky Intimate Articulation Presence @ 2.85/3.15/3.40 kHz)
            if (useEq) {
                s = eqMudCut.process(s)
                s = eqVelvetDip.process(s)
                s = eqPresence.process(s)
            }

            // Stage 9: Pure 2f Upper-Harmonic "Aphex Silk Air" Exciter + Intimate Silk Air High-Shelf (6.5 / 6.7 / 7.0 kHz)
            // Uses envelope-normalized square-law frequency doubling (sin^2(wt) -> cos(2wt)) so 100% of the
            // synthesized energy is at 2f (5.2-10.0 kHz) with zero 1f phase cancellation at 3.1 kHz!
            if (useVelvetChain) {
                val clamped = s.coerceIn(-1.2f, 1.2f)
                val airHarmonicShimmer = if (enableUpperExciter && curAirExciterDrive > 0.001f) {
                    val vMid = airExciterSourceBand.process(clamped)
                    val absMid = abs(vMid)
                    // Pure 2f frequency doubler (zero 1f fundamental leakage -> zero phase cancellation!)
                    val doubled2fRaw = (vMid * vMid) / (0.022f + absMid)
                    // 1st-order high-pass @ ~5.0 kHz strips DC & envelope, isolating pure 5.2-10.0 kHz shimmer
                    airExciterHpState += 0.42f * (doubled2fRaw - airExciterHpState)
                    val pureHighShimmer = doubled2fRaw - airExciterHpState
                    pureHighShimmer * curAirExciterDrive
                } else {
                    0.0f
                }
                val shimmered = clamped + (airHarmonicShimmer * velvetAirMixSmooth)
                val aired = eqVelvetAir.process(shimmered)
                s = shimmered + velvetAirMixSmooth * (aired - shimmered)
            }

            // Stage 10: AI Dynamic Vocal Tone Balancer (Warmth balance on quiet speech; bite taming on loud shouts)
            if (useAiTone) {
                aiWarmthBoostSmooth += 0.008f * (targetAiWarmthBoost - aiWarmthBoostSmooth)
                aiBiteTrimSmooth += 0.012f * (targetAiBiteTrim - aiBiteTrimSmooth)
                val presenceBand = aiPresenceTamer.process(s)
                aiLowEnv += 0.02f * (abs(subChestBand) - aiLowEnv)
                aiHighEnv += 0.02f * (abs(presenceBand) - aiHighEnv)
                s += (subChestBand * aiWarmthBoostSmooth) - (presenceBand * aiBiteTrimSmooth)
            }

            // Stage 11: High-Pass Sidechain 3-Zone Dynamics (Whisper Lift -> Slow AGC -> 12ms-Punch 3:1 Compressor)
            // Weights detector by subtracting 55% of subChestBand so deep 55-105 Hz bass never pumps the compressor!
            val sidechainSample = abs(s - (0.55f * subChestBand))
            compEnv += (if (sidechainSample > compEnv) 0.0018f else 0.00045f) * (sidechainSample - compEnv)

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

            // Stage 12: Post-Compressor Split-Band De-Esser (catches any sibilance boosted by Silk Air or Compression!)
            if (useDeEss) {
                val sibSignal = sibilanceBand.process(s)
                val absSib = abs(sibSignal)
                deEssEnv += (if (absSib > deEssEnv) 0.15f else 0.008f) * (absSib - deEssEnv)
                if (deEssEnv > currentSibThreshold) {
                    val keepRatio = (currentSibThreshold / deEssEnv).coerceIn(0.35f, 1.0f)
                    s -= sibSignal * (1.0f - keepRatio)
                }
            }

            // Stage 13: True Post-DSP Mic Boost (50%..250%) + Soft Output Safety Ceiling
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
        val VOICE_PRESET_ORDER = listOf("RAW", "CLEAN", "NEURAL", "STUDIO", "VELVET", "SOL_LUXE")
        val GENDER_PROFILE_ORDER = listOf("NEUTRAL", "MALE", "FEMALE")
        val NOISE_MODE_ORDER = listOf("OFF", "LIGHT", "RNNOISE", "HW_NS")
        val HPF_CUTOFF_STEPS = listOf(0, 50, 55, 60, 65, 70, 75, 85, 95, 105, 120)

        fun presetDisplayName(preset: String): String = when (preset.uppercase(Locale.US)) {
            "RAW" -> "RAW (Dry Mic)"
            "CLEAN" -> "CLEAN (Natural)"
            "NEURAL" -> "NEURAL (C RNNoise)"
            "STUDIO" -> "RADIO STUDIO"
            "VELVET" -> "VELVET (Silk Air+Warmth)"
            "SOL_LUXE" -> "SOL LUXE (13-Stage Silk Studio)"
            else -> preset
        }

        /**
         * Switches the user's Voice Gender Profile (MALE / NEUTRAL / FEMALE) and automatically
         * sets the gender-matched High-Pass Filter cutoff (50 Hz Male / 65 Hz Neutral / 85 Hz Female).
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
            val activeHpf = if (current.hpfCutoffHz == 0 && current.voicePreset != "RAW") {
                0
            } else if (current.hpfCutoffHz <= 0 || current.hpfCutoffHz in setOf(50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100)) {
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
