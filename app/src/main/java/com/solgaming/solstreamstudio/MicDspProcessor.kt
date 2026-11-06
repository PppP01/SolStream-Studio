/*
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

class MicDspProcessor(private val sampleRate: Int = 48000) {

    // Pick-1 selectors
    @Volatile var voiceGenderProfile: String = "NEUTRAL" // "NEUTRAL", "MALE", "FEMALE"
    @Volatile var voicePreset: String = "CLEAN"          // "RAW", "CLEAN", "NEURAL", "STUDIO", "VELVET", "SOL_LUXE"
    @Volatile var noiseSuppressMode: String = "RNNOISE"  // "OFF", "LIGHT", "RNNOISE", "HW_NS"
    @Volatile var noiseSuppressLevel: Int = 0            // 0 = Off, 1 = Gentle(65%), 2 = Natural(88%), 3 = Max(100%)

    // Stackable independent stage switches (100% mix-and-match freedom)
    @Volatile var hpfCutoffHz: Int = 85                  // 0 = Off, 75, 85, 100, 120 Hz
    @Volatile var transientTamerEnabled: Boolean = true
    @Volatile var gateEnabled: Boolean = true
    @Volatile var eqEnabled: Boolean = false
    @Volatile var deEsserEnabled: Boolean = false
    @Volatile var velvetWarmthEnabled: Boolean = false
    @Volatile var compressorEnabled: Boolean = true
    @Volatile var whisperLiftEnabled: Boolean = false
    @Volatile var slowAgcEnabled: Boolean = false

    @Volatile var micGainLinear: Float = 1.25f
    @Volatile var streamMicMuted: Boolean = false
    @Volatile var platformHwNsAttached: Boolean = false

    // Live telemetry for UI, Guided Grip Calibrator & Diagnostics
    @Volatile var vadSpeechActive: Boolean = false
    @Volatile var lastVadProbability: Float = 0f
    @Volatile var lastPeakDb: Float = -90f
    @Volatile var lastRmsDb: Float = -90f
    @Volatile var lastNoiseFloorDb: Float = -70f
    @Volatile var lastGainReductionDb: Float = 0f
    @Volatile var clipCount: Int = 0
    @Volatile var activeDenoiserSummary: String = "OFF"

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
            if (abs(z1) < 1e-24) z1 = 0.0
            if (abs(z2) < 1e-24) z2 = 0.0
            return y.toFloat()
        }

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }
    }

    private val hpfFilter = Biquad64()
    private val eqLowWarmth = Biquad64()
    private val eqMudCut = Biquad64()
    private val eqPresence = Biquad64()
    private val sibilanceBand = Biquad64()
    private val eqVelvetAir = Biquad64()

    private val nativeLock = Any()
    @Volatile private var nativeRnnHandle: Long = 0L

    private var configuredHpfHz = -1
    private var configuredGender = ""

    private var muteSmoothGain = 1.0f
    private var transientFastEnv = 0.0f
    private var transientSlowEnv = 0.01f
    private var gateGain = 1.0f
    private var gateHoldSamples = 0

    // Stationary light spectral fallback state
    private var noiseFloorEnv = 0.0018f
    private var suppressSmoothGain = 1.0f
    private var lpNoiseState = 0.0f

    // Split-band De-Esser, Velvet Warmth, Whisper Lift, Compressor & Speech-Gated Slow AGC states
    private var deEssEnv = 0.0f
    private var velvetDcState = 0.0f
    private var velvetAirMixSmooth = 0.0f
    private var compEnv = 0.0f
    private var compGainSmooth = 1.0f
    private var slowAgcGain = 1.0f

    init {
        updateGenderTunedFiltersIfNeeded(voiceGenderProfile)
        updateHpfIfNeeded(hpfCutoffHz)
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

        voiceGenderProfile = normGender
        voicePreset = s.voicePreset.uppercase(Locale.US)
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
        micGainLinear = (s.micGainPercent.coerceIn(50, 250)) / 100.0f

        updateHpfIfNeeded(hpfCutoffHz)
        updateGenderTunedFiltersIfNeeded(normGender)
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

    private fun ensureNativeDenoiserHandle(): Long {
        val existing = nativeRnnHandle
        if (existing != 0L) return existing
        if (!SolNativeRnnoise.isLibraryLoaded) return 0L
        synchronized(nativeLock) {
            if (nativeRnnHandle == 0L) {
                nativeRnnHandle = SolNativeRnnoise.createInstance()
            }
            return nativeRnnHandle
        }
    }

    fun release() {
        synchronized(nativeLock) {
            val h = nativeRnnHandle
            nativeRnnHandle = 0L
            if (h != 0L) {
                SolNativeRnnoise.destroyInstance(h)
            }
        }
    }

    private fun updateHpfIfNeeded(cutoffHz: Int) {
        if (cutoffHz == configuredHpfHz) return
        configuredHpfHz = cutoffHz
        if (cutoffHz <= 0) {
            hpfFilter.reset()
            return
        }
        val w0 = 2.0 * PI * cutoffHz.toDouble() / sampleRate.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / sqrt(2.0) // Butterworth Q = 0.7071
        val a0 = 1.0 + alpha
        hpfFilter.b0 = ((1.0 + cosW) * 0.5) / a0
        hpfFilter.b1 = -(1.0 + cosW) / a0
        hpfFilter.b2 = ((1.0 + cosW) * 0.5) / a0
        hpfFilter.a1 = (-2.0 * cosW) / a0
        hpfFilter.a2 = (1.0 - alpha) / a0
    }

    private fun configurePeakingEq(target: Biquad64, freqHz: Double, q: Double, gainDb: Double) {
        val amp = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRate.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha / amp
        target.b0 = (1.0 + alpha * amp) / a0
        target.b1 = (-2.0 * cosW) / a0
        target.b2 = (1.0 - alpha * amp) / a0
        target.a1 = (-2.0 * cosW) / a0
        target.a2 = (1.0 - alpha / amp) / a0
    }

    private fun configureHighShelf(target: Biquad64, freqHz: Double, gainDb: Double) {
        val amp = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRate.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(amp) * alpha
        val a0 = (amp + 1.0) - (amp - 1.0) * cosW + twoSqrtAAlpha
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
        target.b0 = alpha / a0
        target.b1 = 0.0
        target.b2 = -alpha / a0
        target.a1 = (-2.0 * cosW) / a0
        target.a2 = (1.0 - alpha) / a0
    }

    private fun updateGenderTunedFiltersIfNeeded(gender: String) {
        if (gender == configuredGender) return
        configuredGender = gender
        when (gender) {
            "MALE" -> {
                // Deep chest foundation (115 Hz), box/mud cleanup (310 Hz), clear callout presence (3.5 kHz)
                configurePeakingEq(eqLowWarmth, 115.0, 0.78, 1.8)
                configurePeakingEq(eqMudCut, 310.0, 1.25, -2.6)
                configurePeakingEq(eqPresence, 3500.0, 0.95, 2.4)
                configureBandPass(sibilanceBand, 6200.0, 1.60)
                configureHighShelf(eqVelvetAir, 11000.0, 1.8)
            }
            "FEMALE" -> {
                // Warmth matched to higher fundamental (165 Hz), honk cut (400 Hz), smooth presence (4.2 kHz)
                configurePeakingEq(eqLowWarmth, 165.0, 0.80, 1.6)
                configurePeakingEq(eqMudCut, 400.0, 1.30, -2.4)
                configurePeakingEq(eqPresence, 4200.0, 1.00, 2.2)
                configureBandPass(sibilanceBand, 7200.0, 1.60)
                configureHighShelf(eqVelvetAir, 12000.0, 2.0)
            }
            else -> {
                // Balanced broadcast curve for all voices
                configurePeakingEq(eqLowWarmth, 135.0, 0.80, 1.5)
                configurePeakingEq(eqMudCut, 345.0, 1.25, -2.4)
                configurePeakingEq(eqPresence, 3800.0, 0.98, 2.3)
                configureBandPass(sibilanceBand, 6600.0, 1.60)
                configureHighShelf(eqVelvetAir, 11500.0, 1.8)
            }
        }
    }

    fun processMonoFrameInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return

        val targetMute = if (streamMicMuted) 0.0f else 1.0f
        val rawMode = (voicePreset == "RAW")
        val hpfHz = hpfCutoffHz
        val useTransient = transientTamerEnabled && !rawMode
        updateHpfIfNeeded(hpfHz)
        updateGenderTunedFiltersIfNeeded(voiceGenderProfile)

        // === PASS 1: Smooth Anti-Click Mute + Pre-Gain + 64-Bit HPF + Grip/Plosive Transient Tamer ===
        val baseGain = micGainLinear
        for (i in 0 until count) {
            muteSmoothGain += 0.015f * (targetMute - muteSmoothGain)
            val rawIn = buffer[i]
            var s = (if (rawIn.isFinite()) rawIn else 0f) * muteSmoothGain * baseGain

            if (!rawMode && hpfHz > 0) {
                s = hpfFilter.process(s)
            }

            if (useTransient) {
                val absS = abs(s)
                transientFastEnv += (if (absS > transientFastEnv) 0.28f else 0.012f) * (absS - transientFastEnv)
                transientSlowEnv += 0.003f * (absS - transientSlowEnv)
                val impulseCeiling = max(0.18f, transientSlowEnv * 4.2f)
                if (transientFastEnv > impulseCeiling) {
                    s *= (impulseCeiling / transientFastEnv).coerceIn(0.35f, 1.0f)
                }
            }

            if (abs(s) < 1e-20f) s = 0f
            buffer[i] = s
        }

        if (rawMode) {
            var rawPeak = 0f
            var rawSumSq = 0f
            for (i in 0 until count) {
                val a = abs(buffer[i])
                if (a > rawPeak) rawPeak = a
                rawSumSq += a * a
                if (a > 0.99f) clipCount++
            }
            val rawRms = sqrt(rawSumSq / count)
            lastPeakDb = linearToDb(rawPeak)
            lastRmsDb = linearToDb(rawRms)
            vadSpeechActive = (muteSmoothGain > 0.05f) && (rawRms > 0.010f)
            return
        }

        // === PASS 2: Single-Active Noise Suppressor (RNNoise / Light Cut / Chip HW NS) ===
        val nsMode = noiseSuppressMode
        val nsLevel = noiseSuppressLevel
        var neuralVadProb = -1.0f
        var ranNativeRnnoise = false

        if (nsMode == "RNNOISE" && nsLevel > 0 && count == 480 && sampleRate == 48000 && SolNativeRnnoise.isLibraryLoaded) {
            val handle = ensureNativeDenoiserHandle()
            if (handle != 0L) {
                val wetStrength = when (nsLevel) {
                    1 -> 0.65f
                    2 -> 0.88f
                    else -> 1.00f
                }
                neuralVadProb = SolNativeRnnoise.processMono480InPlace(handle, buffer, wetStrength)
                ranNativeRnnoise = (neuralVadProb >= 0.0f)
            }
        }

        // Measure post-HPF / post-RNNoise frame energy & zero-crossing rate for VAD & noise-floor tracking
        var frameSumSq = 0.0f
        var framePeak = 0.0f
        var zeroCrossings = 0
        var prevSign = if (buffer[0] >= 0f) 1 else -1

        for (i in 0 until count) {
            val s = buffer[i]
            val absS = abs(s)
            if (absS > framePeak) framePeak = absS
            frameSumSq += s * s
            val sign = if (s >= 0f) 1 else -1
            if (sign != prevSign) {
                zeroCrossings++
                prevSign = sign
            }
        }

        val rms = sqrt(frameSumSq / count)
        lastPeakDb = linearToDb(framePeak)
        lastRmsDb = linearToDb(rms)

        val likelyQuiet = if (neuralVadProb >= 0f) {
            neuralVadProb < 0.25f && rms < noiseFloorEnv * 1.8f
        } else {
            rms < noiseFloorEnv * 1.6f
        }
        if (likelyQuiet) {
            noiseFloorEnv = 0.96f * noiseFloorEnv + 0.04f * max(rms, 0.00010f)
        } else {
            noiseFloorEnv = min(0.035f, noiseFloorEnv * 1.0003f)
        }
        lastNoiseFloorDb = linearToDb(noiseFloorEnv)

        val zcrRatio = zeroCrossings.toFloat() / count.toFloat()
        val snrRatio = rms / max(noiseFloorEnv, 0.00015f)
        val energySpeech = (rms > 0.0065f) && (snrRatio > 2.4f) && (zcrRatio < 0.44f)
        val speechDetected = (muteSmoothGain > 0.05f) && (
            (neuralVadProb >= 0.48f && rms > 0.0035f) ||
                (neuralVadProb < 0f && energySpeech) ||
                (snrRatio > 3.6f && rms > 0.009f)
            )
        lastVadProbability = if (neuralVadProb >= 0f) neuralVadProb else if (speechDetected) 0.85f else 0.05f

        val holdDurationSamples = (sampleRate * 0.16f).toInt() // 160 ms natural syllable tail hold
        if (speechDetected) {
            gateHoldSamples = holdDurationSamples
        } else if (gateHoldSamples > 0) {
            gateHoldSamples = max(0, gateHoldSamples - count)
        }
        val activeSpeech = speechDetected || gateHoldSamples > 0
        vadSpeechActive = activeSpeech

        // Run light spectral cut ONLY if selected (or as fallback if RNNoise/HW_NS wasn't active)
        val runLightSpectral = nsLevel > 0 && !ranNativeRnnoise &&
            (nsMode == "LIGHT" || (nsMode == "RNNOISE" && !SolNativeRnnoise.isLibraryLoaded) || (nsMode == "HW_NS" && !platformHwNsAttached))

        val targetSuppressGain = if (runLightSpectral) {
            val strength = when (nsLevel) {
                1 -> 0.50f
                2 -> 0.76f
                else -> 0.86f
            }
            val cleanRatio = ((snrRatio - 1.2f) / 3.5f).coerceIn(0f, 1f)
            (1.0f - strength * (1.0f - cleanRatio)).coerceIn(0.16f, 1.0f)
        } else {
            1.0f
        }

        // Speech-Gated Slow Broadcast AGC (strictly frozen during silence so idle hiss is NEVER boosted!)
        val useSlowAgc = slowAgcEnabled
        if (useSlowAgc) {
            if (speechDetected && rms > 0.011f) {
                val targetSpeechRms = 0.145f // ~ -16.8 dBFS consistent speaking loudness
                val desiredAgc = (targetSpeechRms / rms).coerceIn(0.63f, 1.95f) // -4 dB .. +5.8 dB
                val agcRate = if (desiredAgc < slowAgcGain) 0.06f else 0.02f
                slowAgcGain += agcRate * (desiredAgc - slowAgcGain)
            }
        } else {
            slowAgcGain += 0.05f * (1.0f - slowAgcGain)
        }
        val currentAgcGain = slowAgcGain

        val useGate = gateEnabled
        val useEq = eqEnabled
        val useDeEss = deEsserEnabled
        val useVelvet = velvetWarmthEnabled
        val useWhisper = whisperLiftEnabled
        val useComp = compressorEnabled

        val targetGate = if (!useGate || activeSpeech) 1.0f else 0.24f // Smooth -12.4 dB idle breath floor
        val targetAirMix = if (useVelvet && activeSpeech) 1.0f else 0.0f
        var targetCompBlockGain = 1.0f
        var minCompGainInFrame = 1.0f

        // === PASS 3: Expander -> Gender EQ -> Split-Band De-Esser -> Velvet Warmth/Air -> Whisper + 3:1 Comp + AGC ===
        for (i in 0 until count) {
            var s = buffer[i]

            if (runLightSpectral) {
                suppressSmoothGain += 0.008f * (targetSuppressGain - suppressSmoothGain)
                lpNoiseState += 0.22f * (s - lpNoiseState)
                val highBand = s - lpNoiseState
                s = (lpNoiseState * suppressSmoothGain) + (highBand * suppressSmoothGain * suppressSmoothGain)
            }

            if (useGate) {
                val gCoeff = if (targetGate > gateGain) 0.028f else 0.0012f
                gateGain += gCoeff * (targetGate - gateGain)
                s *= gateGain
            }

            if (useEq) {
                s = eqLowWarmth.process(s)
                s = eqMudCut.process(s)
                s = eqPresence.process(s)
            }

            // True Split-Band De-Esser: subtracts ONLY excess sibilant energy without ducking chest/mid voice
            if (useDeEss) {
                val sibSignal = sibilanceBand.process(s)
                val absSib = abs(sibSignal)
                deEssEnv += (if (absSib > deEssEnv) 0.10f else 0.006f) * (absSib - deEssEnv)
                val sibThreshold = 0.055f
                if (deEssEnv > sibThreshold) {
                    val keepRatio = (sibThreshold / deEssEnv).coerceIn(0.32f, 1.0f)
                    s -= sibSignal * (1.0f - keepRatio)
                }
            }

            // Velvet Preamp Warmth (DC-blocked even-order harmonic richness + VAD-gated silky top air)
            if (useVelvet) {
                val clamped = s.coerceIn(-1.2f, 1.2f)
                val sq = clamped * clamped
                velvetDcState += 0.002f * (sq - velvetDcState)
                val evenHarmonic = 0.055f * (sq - velvetDcState)
                val softCubic = clamped - 0.11f * clamped * sq
                val warmed = softCubic + evenHarmonic

                velvetAirMixSmooth += 0.010f * (targetAirMix - velvetAirMixSmooth)
                val aired = eqVelvetAir.process(warmed)
                s = warmed + velvetAirMixSmooth * (aired - warmed)
            }

            // Upward Whisper Lift + Broadcast 3:1 Compressor (computed every 16 samples & slewed sample-accurately)
            val absIn = abs(s)
            compEnv += (if (absIn > compEnv) 0.018f else 0.0007f) * (absIn - compEnv)

            if ((i and 15) == 0) {
                var desiredDynGain = 1.0f
                if (useWhisper && activeSpeech && compEnv in 0.012f..0.110f) {
                    // Gently lift quiet callouts up to +4.2 dB without boosting room floor
                    val lift = (0.110f / max(compEnv, 0.025f)).pow(0.32f).coerceIn(1.0f, 1.62f)
                    desiredDynGain *= lift
                }
                if (useComp) {
                    val threshold = 0.224f // ~ -13.0 dBFS
                    if (compEnv > threshold) {
                        // 3:1 compression ratio: (threshold / env)^(2/3) * 1.14 makeup
                        val atten = (threshold / compEnv).pow(0.6667f)
                        if (atten < minCompGainInFrame) minCompGainInFrame = atten
                        desiredDynGain *= atten * 1.14f
                    } else {
                        desiredDynGain *= 1.14f
                    }
                }
                targetCompBlockGain = desiredDynGain
            }

            compGainSmooth += 0.08f * (targetCompBlockGain - compGainSmooth)
            s *= compGainSmooth * currentAgcGain

            // Transparent rational soft-clip safety ceiling before 4x cubic look-ahead master limiter
            if (abs(s) > 0.92f) {
                val sign = if (s >= 0f) 1f else -1f
                val excess = abs(s) - 0.92f
                s = sign * (0.92f + (0.075f * excess) / (0.075f + excess))
                if (abs(s) > 0.99f) clipCount++
            }

            buffer[i] = s
        }

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
         * Applies a 1-tap voice preset recipe onto [StreamSettings] while leaving every individual
         * filter switch editable afterward for 100% mix-and-match customization.
         */
        fun applyPresetToSettings(current: StreamSettings, newPreset: String): StreamSettings {
            val p = newPreset.uppercase(Locale.US)
            val defaultHpf = when (current.voiceGenderProfile.uppercase(Locale.US)) {
                "MALE" -> 80
                "FEMALE" -> 100
                else -> 85
            }
            val activeHpf = if (current.hpfCutoffHz == 0) defaultHpf else current.hpfCutoffHz

            return when (p) {
                "RAW" -> current.copy(
                    voicePreset = "RAW"
                )
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
