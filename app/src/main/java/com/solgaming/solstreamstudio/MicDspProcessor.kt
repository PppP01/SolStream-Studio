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
    private val vocalBodyDetector = Biquad64() // 220 Hz vocal-cord fundamental detector for Spark/Hammer rejection
    private val eqLowWarmth = Biquad64()
    private val eqMudCut = Biquad64()
    private val eqPresence = Biquad64()
    private val sibilanceBand = Biquad64()
    private val eqVelvetAir = Biquad64()

    private val nativeLock = Any()
    @Volatile private var nativeRnnHandle: Long = 0L
    @Volatile private var released: Boolean = false

    private var configuredHpfHz = -1
    private var configuredGender = ""

    // Multi-strike mosquito-racket spark ("phat-phat-phat") & construction hammer/drill blanker state
    private var muteSmoothGain = 1.0f
    private var prevRawSample = 0.0f
    private var slewFastEnv = 0.0f
    private var slewSlowEnv = 0.0015f
    private var transientFastEnv = 0.0f
    private var transientSlowEnv = 0.008f
    private var vocalBodyEnv = 0.002f
    private var sparkBlankerHoldSamples = 0

    // VAD & Breath Expander state (with 30 ms consecutive vocal confirmation so short sparks never open gate)
    private var gateGain = 1.0f
    private var gateHoldSamples = 0
    private var consecutiveSpeechFrames = 0

    // Stationary light spectral fallback & construction high-band tamer state
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
        configureBandPass(vocalBodyDetector, 220.0, 0.90)
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

    private fun updateHpfIfNeeded(cutoffHz: Int) {
        if (cutoffHz == configuredHpfHz) return
        configuredHpfHz = cutoffHz
        if (cutoffHz <= 0) {
            hpfFilter.reset()
            return
        }
        val w0 = 2.0 * PI * cutoffHz.toDouble() / sampleRate.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / sqrt(2.0)
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
                configurePeakingEq(eqLowWarmth, 115.0, 0.78, 1.8)
                configurePeakingEq(eqMudCut, 310.0, 1.25, -2.6)
                configurePeakingEq(eqPresence, 3500.0, 0.95, 2.4)
                configureBandPass(sibilanceBand, 6200.0, 1.60)
                configureHighShelf(eqVelvetAir, 11000.0, 1.8)
            }
            "FEMALE" -> {
                configurePeakingEq(eqLowWarmth, 165.0, 0.80, 1.6)
                configurePeakingEq(eqMudCut, 400.0, 1.30, -2.4)
                configurePeakingEq(eqPresence, 4200.0, 1.00, 2.2)
                configureBandPass(sibilanceBand, 7200.0, 1.60)
                configureHighShelf(eqVelvetAir, 12000.0, 2.0)
            }
            else -> {
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

        // === PASS 1: Smooth Mute + Pre-Gain + 64-Bit HPF + Multi-Strike Spark/Hammer Blanker ===
        val baseGain = micGainLinear
        var impulseSamplesInFrame = 0

        for (i in 0 until count) {
            muteSmoothGain += 0.015f * (targetMute - muteSmoothGain)
            val rawIn = buffer[i]
            var s = (if (rawIn.isFinite()) rawIn else 0f) * muteSmoothGain * baseGain

            if (!rawMode && hpfHz > 0) {
                s = hpfFilter.process(s)
            }

            if (useTransient) {
                val absS = abs(s)
                val slew = abs(s - prevRawSample)
                prevRawSample = s

                // Track 220 Hz vocal-cord fundamental vs sub-millisecond spark/hammer slew
                val vocalFund = abs(vocalBodyDetector.process(s))
                vocalBodyEnv += (if (vocalFund > vocalBodyEnv) 0.08f else 0.004f) * (vocalFund - vocalBodyEnv)
                slewFastEnv += (if (slew > slewFastEnv) 0.45f else 0.03f) * (slew - slewFastEnv)
                transientFastEnv += (if (absS > transientFastEnv) 0.35f else 0.015f) * (absS - transientFastEnv)

                // Detect electric spark ("phat-phat-phat") or hammer strike:
                // Sharp vertical jump where high-frequency slew dominates over 220 Hz vocal-cord body
                val slewCeiling = max(0.012f, slewSlowEnv * 3.4f)
                val ampCeiling = max(0.035f, transientSlowEnv * 3.8f)
                val lacksVocalBody = vocalBodyEnv < (transientFastEnv * 0.38f)
                val isSparkOrHammer = (slewFastEnv > slewCeiling && lacksVocalBody) ||
                    (transientFastEnv > ampCeiling && lacksVocalBody)

                if (isSparkOrHammer) {
                    // Hold blanker for 6 ms (288 samples) per strike & FREEZE slow baselines
                    // so rapid bursts of 3-6 mosquito zaps in 2-4s never adapt the baseline upward!
                    sparkBlankerHoldSamples = 288
                    impulseSamplesInFrame++
                } else {
                    if (sparkBlankerHoldSamples > 0) {
                        sparkBlankerHoldSamples--
                        impulseSamplesInFrame++
                    } else {
                        // Only update slow baseline during non-impulse samples!
                        transientSlowEnv += 0.0025f * (absS - transientSlowEnv)
                        slewSlowEnv += 0.0025f * (slew - slewSlowEnv)
                    }
                }

                if (sparkBlankerHoldSamples > 0) {
                    // If user is speaking, shave spark down to vocal body; if silent, clamp by up to 92% (-22 dB)
                    val safeCeiling = if (vadSpeechActive) {
                        max(vocalBodyEnv * 1.6f, transientSlowEnv * 1.8f)
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

        if (nsMode == "RNNOISE" && nsLevel > 0 && count == 480 && sampleRate == 48000) {
            val wetStrength = when (nsLevel) {
                1 -> 0.65f
                2 -> 0.88f
                else -> 1.00f
            }
            neuralVadProb = runNativeRnnoiseLocked(buffer, wetStrength)
            ranNativeRnnoise = (neuralVadProb >= 0.0f)
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

        // Do not let short spark/hammer frames pollute the stationary room noise-floor tracker
        val frameHadImpulse = impulseSamplesInFrame > (count / 6)
        val likelyQuiet = if (neuralVadProb >= 0f) {
            neuralVadProb < 0.25f && rms < noiseFloorEnv * 1.8f && !frameHadImpulse
        } else {
            rms < noiseFloorEnv * 1.6f && !frameHadImpulse
        }
        if (likelyQuiet) {
            noiseFloorEnv = 0.96f * noiseFloorEnv + 0.04f * max(rms, 0.00010f)
        } else if (!frameHadImpulse) {
            noiseFloorEnv = min(0.035f, noiseFloorEnv * 1.0003f)
        }
        lastNoiseFloorDb = linearToDb(noiseFloorEnv)

        val zcrRatio = zeroCrossings.toFloat() / count.toFloat()
        val snrRatio = rms / max(noiseFloorEnv, 0.00015f)
        // True human speech has strong 220 Hz vocal-cord body and moderate zero-crossing rate (<0.42),
        // whereas mosquito zaps & construction drills have high ZCR and weak vocal body
        val hasVocalHarmonics = vocalBodyEnv > (rms * 0.22f) && zcrRatio < 0.42f
        val energySpeech = (rms > 0.0065f) && (snrRatio > 2.4f) && hasVocalHarmonics
        val rawFrameSpeech = (muteSmoothGain > 0.05f) && !frameHadImpulse && (
            (neuralVadProb >= 0.52f && rms > 0.0035f && zcrRatio < 0.46f) ||
                (neuralVadProb < 0f && energySpeech) ||
                (snrRatio > 3.8f && rms > 0.0095f && hasVocalHarmonics)
            )

        if (rawFrameSpeech) {
            consecutiveSpeechFrames = min(50, consecutiveSpeechFrames + 1)
        } else {
            consecutiveSpeechFrames = max(0, consecutiveSpeechFrames - 1)
        }

        // Require at least 2 consecutive frames (20 ms) of vocal-cord speech (or high neural VAD)
        // before triggering the 160 ms gate hold, so a 5-15 ms mosquito zap NEVER opens the gate!
        val confirmedSpeech = rawFrameSpeech && (consecutiveSpeechFrames >= 2 || neuralVadProb >= 0.68f)
        lastVadProbability = if (neuralVadProb >= 0f) neuralVadProb else if (confirmedSpeech) 0.85f else 0.05f

        val holdDurationSamples = (sampleRate * 0.16f).toInt()
        if (confirmedSpeech) {
            gateHoldSamples = holdDurationSamples
        } else if (gateHoldSamples > 0) {
            gateHoldSamples = max(0, gateHoldSamples - count)
        }
        val activeSpeech = confirmedSpeech || gateHoldSamples > 0
        vadSpeechActive = activeSpeech

        // Fallback to Light Spectral Cut whenever RNNOISE did not run on the current frame,
        // OR apply extra high-band construction/grinder taming when non-speech high-ZCR noise is present
        val highFreqDrillNoise = !activeSpeech && useTransient && zcrRatio > 0.34f && snrRatio > 1.6f
        val runLightSpectral = (nsLevel > 0 && !ranNativeRnnoise &&
            (nsMode == "LIGHT" || nsMode == "RNNOISE" || (nsMode == "HW_NS" && !platformHwNsAttached))) ||
            highFreqDrillNoise

        val targetSuppressGain = if (runLightSpectral) {
            val strength = when {
                nsLevel == 1 -> 0.50f
                nsLevel == 2 -> 0.76f
                nsLevel >= 3 -> 0.86f
                else -> 0.65f
            }
            val cleanRatio = if (highFreqDrillNoise && !activeSpeech) {
                0.0f
            } else {
                ((snrRatio - 1.2f) / 3.5f).coerceIn(0f, 1f)
            }
            (1.0f - strength * (1.0f - cleanRatio)).coerceIn(0.14f, 1.0f)
        } else {
            1.0f
        }

        // Speech-Gated Slow Broadcast AGC (strictly frozen during silence/sparks so noise is NEVER boosted!)
        val useSlowAgc = slowAgcEnabled
        if (useSlowAgc) {
            if (confirmedSpeech && rms > 0.011f) {
                val targetSpeechRms = 0.145f
                val desiredAgc = (targetSpeechRms / rms).coerceIn(0.63f, 1.95f)
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

        val targetGate = if (!useGate || activeSpeech) 1.0f else if (frameHadImpulse) 0.10f else 0.22f
        val targetAirMix = if (useVelvet && activeSpeech) 1.0f else 0.0f
        var targetCompBlockGain = 1.0f
        var minCompGainInFrame = 1.0f

        // === PASS 3: Expander -> Gender EQ -> Split-Band De-Esser -> Velvet Warmth/Air -> Whisper + 3:1 Comp + AGC ===
        for (i in 0 until count) {
            var s = buffer[i]

            if (runLightSpectral) {
                suppressSmoothGain += 0.010f * (targetSuppressGain - suppressSmoothGain)
                lpNoiseState += 0.22f * (s - lpNoiseState)
                val highBand = s - lpNoiseState
                // High-frequency band (mosquito spark crackle / drill whine) gets squared attenuation
                s = (lpNoiseState * suppressSmoothGain) + (highBand * suppressSmoothGain * suppressSmoothGain)
            }

            if (useGate) {
                val gCoeff = if (targetGate > gateGain) 0.028f else if (frameHadImpulse) 0.015f else 0.0014f
                gateGain += gCoeff * (targetGate - gateGain)
                s *= gateGain
            }

            if (useEq) {
                s = eqLowWarmth.process(s)
                s = eqMudCut.process(s)
                s = eqPresence.process(s)
            }

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

            val absIn = abs(s)
            compEnv += (if (absIn > compEnv) 0.018f else 0.0007f) * (absIn - compEnv)

            if ((i and 15) == 0) {
                var desiredDynGain = 1.0f
                if (useWhisper && activeSpeech && compEnv in 0.012f..0.110f) {
                    val lift = (0.110f / max(compEnv, 0.025f)).pow(0.32f).coerceIn(1.0f, 1.62f)
                    desiredDynGain *= lift
                }
                if (useComp) {
                    val threshold = 0.224f
                    if (compEnv > threshold) {
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

        fun applyPresetToSettings(current: StreamSettings, newPreset: String): StreamSettings {
            val p = newPreset.uppercase(Locale.US)
            val defaultHpf = when (current.voiceGenderProfile.uppercase(Locale.US)) {
                "MALE" -> 80
                "FEMALE" -> 100
                else -> 85
            }
            val activeHpf = if (current.hpfCutoffHz == 0) defaultHpf else current.hpfCutoffHz

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
