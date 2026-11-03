package com.solgaming.solstreamstudio

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

    @Volatile var voicePreset: String = "CLEAN" // "RAW", "CLEAN", "STUDIO"
    @Volatile var hpfCutoffHz: Int = 85         // 0 = Off, 75, 85, 100
    @Volatile var noiseSuppressLevel: Int = 0   // 0 = Off, 1 = Low, 2 = Medium
    @Volatile var gateEnabled: Boolean = true
    @Volatile var eqEnabled: Boolean = false
    @Volatile var deEsserEnabled: Boolean = false
    @Volatile var compressorEnabled: Boolean = true
    @Volatile var micGainLinear: Float = 1.25f
    @Volatile var streamMicMuted: Boolean = false

    // Live telemetry for UI & Diagnostics
    @Volatile var vadSpeechActive: Boolean = false
    @Volatile var lastPeakDb: Float = -90f
    @Volatile var lastNoiseFloorDb: Float = -70f
    @Volatile var clipCount: Int = 0

    // 64-bit Double internal biquad states for zero bass rounding noise
    private class Biquad64 {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
        var a1 = 0.0; var a2 = 0.0
        var z1 = 0.0; var z2 = 0.0

        fun process(x: Float): Float {
            val xd = x.toDouble()
            val y = b0 * xd + z1
            z1 = b1 * xd - a1 * y + z2
            z2 = b2 * xd - a2 * y
            return y.toFloat()
        }

        fun reset() {
            z1 = 0.0; z2 = 0.0
        }
    }

    private val hpfFilter = Biquad64()
    private val eqLowShelf = Biquad64()
    private val eqMudCut = Biquad64()
    private val eqPresence = Biquad64()
    private val sibilanceBand = Biquad64()

    private var configuredHpfHz = -1
    private var muteSmoothGain = 1.0f
    private var gateGain = 1.0f
    private var gateHoldSamples = 0
    private var compEnv = 0.0f
    private var deEssEnv = 0.0f

    // Stationary fan/hum noise floor estimator
    private var noiseFloorEnv = 0.002f
    private var suppressSmoothGain = 1.0f
    private var lpNoiseState = 0.0f

    init {
        configureFixedFilters()
        updateHpfIfNeeded(hpfCutoffHz)
    }

    fun applySettings(s: StreamSettings) {
        voicePreset = s.voicePreset
        hpfCutoffHz = s.hpfCutoffHz
        noiseSuppressLevel = s.rnnoiseLevel
        gateEnabled = s.noiseGateEnabled
        eqEnabled = s.voiceEqEnabled || s.voicePreset == "STUDIO"
        deEsserEnabled = s.deEsserEnabled || s.voicePreset == "STUDIO"
        compressorEnabled = s.compressorEnabled
        micGainLinear = (s.micGainPercent.coerceIn(50, 250)) / 100.0f
        updateHpfIfNeeded(s.hpfCutoffHz)
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

    private fun configureFixedFilters() {
        // Gentle vocal clarity curve (125 Hz chest warmth, 350 Hz mud cut, 3.8 kHz presence)
        configurePeakingEq(eqLowShelf, 125.0, 0.8, 1.2)
        configurePeakingEq(eqMudCut, 350.0, 1.2, -2.2)
        configurePeakingEq(eqPresence, 3800.0, 1.0, 2.2)
        // 6.5 kHz sibilance detector for De-Esser
        configureBandPass(sibilanceBand, 6500.0, 1.8)
    }

    fun processMonoFrameInPlace(buffer: FloatArray, count: Int) {
        val targetMute = if (streamMicMuted) 0.0f else 1.0f
        val rawMode = (voicePreset == "RAW")
        val hpfHz = hpfCutoffHz
        updateHpfIfNeeded(hpfHz)

        var frameSumSq = 0.0f
        var framePeak = 0.0f
        var zeroCrossings = 0
        var prevSign = if (count > 0 && buffer[0] >= 0f) 1 else -1

        // Pass 1: Smooth Mute + Pre-Gain + High-Pass Rumble/Grip Cut + Frame Energy
        val baseGain = micGainLinear
        for (i in 0 until count) {
            muteSmoothGain += 0.015f * (targetMute - muteSmoothGain)
            var s = buffer[i] * muteSmoothGain * baseGain

            if (!rawMode && hpfHz > 0) {
                s = hpfFilter.process(s)
            }

            buffer[i] = s
            val absS = abs(s)
            if (absS > framePeak) framePeak = absS
            frameSumSq += s * s

            val sign = if (s >= 0f) 1 else -1
            if (sign != prevSign) {
                zeroCrossings++
                prevSign = sign
            }
        }

        val rms = if (count > 0) sqrt(frameSumSq / count) else 0f
        lastPeakDb = linearToDb(framePeak)

        // Update continuous stationary fan/room noise floor during quiet moments
        if (rms < noiseFloorEnv * 1.6f) {
            noiseFloorEnv = 0.96f * noiseFloorEnv + 0.04f * max(rms, 0.00015f)
        } else {
            noiseFloorEnv = min(0.04f, noiseFloorEnv * 1.0004f)
        }
        lastNoiseFloorDb = linearToDb(noiseFloorEnv)

        // Voice Activity Detection (energy above dynamic noise floor + natural speech ZCR)
        val zcrRatio = if (count > 0) zeroCrossings.toFloat() / count.toFloat() else 0f
        val snrRatio = rms / max(noiseFloorEnv, 0.0002f)
        val speechDetected = (muteSmoothGain > 0.05f) && (rms > 0.008f) && (snrRatio > 2.6f) && (zcrRatio < 0.42f)

        val holdDurationSamples = (sampleRate * 0.15f).toInt() // 150 ms hold
        if (speechDetected) {
            gateHoldSamples = holdDurationSamples
        } else if (gateHoldSamples > 0) {
            gateHoldSamples = max(0, gateHoldSamples - count)
        }
        vadSpeechActive = speechDetected || gateHoldSamples > 0

        if (rawMode) {
            for (i in 0 until count) {
                if (abs(buffer[i]) > 0.99f) clipCount++
            }
            return
        }

        val nsLevel = noiseSuppressLevel
        val useGate = gateEnabled
        val useEq = eqEnabled
        val useDeEss = deEsserEnabled
        val useComp = compressorEnabled

        // Stationary fan/hum spectral Wiener attenuation target
        val targetSuppressGain = if (nsLevel > 0) {
            val strength = if (nsLevel == 1) 0.55f else 0.80f
            val cleanRatio = ((snrRatio - 1.2f) / 3.5f).coerceIn(0f, 1f)
            (1.0f - strength * (1.0f - cleanRatio)).coerceIn(0.16f, 1.0f)
        } else {
            1.0f
        }

        val targetGate = if (!useGate || vadSpeechActive) 1.0f else 0.22f // Gentle -13 dB breath/idle expander

        // Pass 2: Sample-accurate Suppressor + Gentle Expander + Optional EQ + De-Esser + Compressor
        for (i in 0 until count) {
            var s = buffer[i]

            if (nsLevel > 0) {
                suppressSmoothGain += 0.008f * (targetSuppressGain - suppressSmoothGain)
                // Attenuate high-frequency fan hiss slightly stronger when non-voiced
                lpNoiseState += 0.22f * (s - lpNoiseState)
                val highBand = s - lpNoiseState
                s = (lpNoiseState * suppressSmoothGain) + (highBand * suppressSmoothGain * suppressSmoothGain)
            }

            if (useGate) {
                val coeff = if (targetGate > gateGain) 0.025f else 0.0012f
                gateGain += coeff * (targetGate - gateGain)
                s *= gateGain
            }

            if (useEq) {
                s = eqLowShelf.process(s)
                s = eqMudCut.process(s)
                s = eqPresence.process(s)
            }

            if (useDeEss) {
                val sib = abs(sibilanceBand.process(s))
                deEssEnv += (if (sib > deEssEnv) 0.08f else 0.005f) * (sib - deEssEnv)
                if (deEssEnv > 0.07f) {
                    val sibReduce = (0.07f / deEssEnv).coerceIn(0.45f, 1.0f)
                    s *= sibReduce
                }
            }

            if (useComp) {
                val absIn = abs(s)
                compEnv += (if (absIn > compEnv) 0.02f else 0.0008f) * (absIn - compEnv)
                val threshold = 0.25f // ~ -12 dBFS
                if (compEnv > threshold) {
                    // 3:1 compression ratio above threshold with gentle 1.12x makeup
                    val overDb = linearToDb(compEnv / threshold)
                    val gainReductionDb = overDb * (1.0f - (1.0f / 3.0f))
                    s *= dbToLinear(-gainReductionDb) * 1.12f
                } else {
                    s *= 1.12f
                }
            }

            if (abs(s) > 0.99f) clipCount++
            buffer[i] = s
        }
    }

    companion object {
        fun linearToDb(v: Float): Float {
            if (v <= 0.00003f) return -90f
            return (20.0 * ln(v.toDouble()) / ln(10.0)).toFloat()
        }

        fun dbToLinear(db: Float): Float = 10.0.pow(db.toDouble() / 20.0).toFloat()
    }
}
