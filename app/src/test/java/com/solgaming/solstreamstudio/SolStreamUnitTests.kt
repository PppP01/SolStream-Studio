/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 */

package com.solgaming.solstreamstudio

import android.media.MediaCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class SolStreamUnitTests {

    @Test
    fun rtmpsUrlNormalization_upgradesLegacyHostAndInsertsDefaultPort() {
        val legacy = "rtmps://a.rtmp.youtube.com/live2/"
        val normalized = KeystoreVault.normalizeIngestUrl(legacy)
        assertEquals("rtmps://a.rtmps.youtube.com:443/live2", normalized)

        val joined = KeystoreVault.joinUrlAndStreamKey(normalized, "abcd-1234-efgh-5678")
        assertEquals("rtmps://a.rtmps.youtube.com:443/live2/abcd-1234-efgh-5678", joined)
    }

    @Test
    fun youtubeBackupFailover_constructsValidBackupEndpoint() {
        val primary = "rtmps://a.rtmps.youtube.com:443/live2/test-key"
        val backup = KeystoreVault.deriveYoutubeBackupUrl(primary)
        assertNotNull(backup)
        assertEquals("rtmps://b.rtmps.youtube.com:443/live2/test-key?backup=1", backup)

        val nonYoutube = "rtmps://live.twitch.tv:443/app/live_123"
        assertNull(KeystoreVault.deriveYoutubeBackupUrl(nonYoutube))
    }

    @Test
    fun hardwareDimensionAlignment_aligns480pAnd360pToSiliconBoundaries() {
        val report16 = HardwareEncoderReport(
            codecKey = "HEVC",
            mimeType = "video/hevc",
            encoderName = "c2.exynos.hevc.encoder",
            supports1440p60 = true,
            supports1080p60 = true,
            supportsCbr = true,
            supportsVbr = true,
            widthAlignment = 16,
            heightAlignment = 16,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true
        )
        val (w480, h480) = report16.alignDimensions(854, 480)
        assertEquals(0, w480 % 16)
        assertEquals(0, h480 % 16)
        assertEquals(848, w480)
        assertEquals(480, h480)

        val (w360, h360) = report16.alignDimensions(640, 360)
        assertEquals(0, w360 % 16)
        assertEquals(0, h360 % 16)
    }

    @Test
    fun voicePresetsAndGenderProfiles_applyCleanlyAndPreserveMixAndMatchFreedom() {
        val base = StreamSettings(
            voiceGenderProfile = "MALE",
            hpfCutoffHz = 0,
            gameGainPercent = 250,
            targetBitrateKbps = 100000
        )
        val luxe = MicDspProcessor.applyPresetToSettings(base, "SOL_LUXE")
        assertEquals("SOL_LUXE", luxe.voicePreset)
        assertEquals("MALE", luxe.voiceGenderProfile)
        assertEquals(80, luxe.hpfCutoffHz)
        assertTrue(luxe.voiceEqEnabled)
        assertTrue(luxe.deEsserEnabled)
        assertTrue(luxe.velvetWarmthEnabled)
        assertTrue(luxe.slowAgcEnabled)
        assertEquals(250, luxe.gameGainPercent)
        assertEquals(100000, luxe.targetBitrateKbps)
    }

    @Test
    fun micDspProcessor_processes48kHzFramesWithoutNanOrClipping() {
        val dsp = MicDspProcessor(48000)
        val settings = MicDspProcessor.applyPresetToSettings(
            StreamSettings(voiceGenderProfile = "MALE", rnnoiseLevel = 2),
            "SOL_LUXE"
        )
        dsp.applySettings(settings)

        val frame = FloatArray(480) { idx ->
            (0.25f * sin(2.0 * Math.PI * 220.0 * idx / 48000.0)).toFloat()
        }
        dsp.processMonoFrameInPlace(frame, 480)

        for (s in frame) {
            assertTrue("DSP output must be finite", s.isFinite())
            assertTrue("DSP output must respect soft-clip ceiling", abs(s) <= 1.0f)
        }
        dsp.release()
    }

    @Test
    fun calibrationAdvisor_appliesRecommendedGainAndFilters() {
        val advice = MicCalibrationAdvice(
            measuredNoiseFloorDb = -52,
            measuredSpeechRmsDb = -20,
            measuredPeakDb = -6,
            recommendedGainPercent = 150,
            recommendedHpfHz = 80,
            recommendedNoiseMode = "RNNOISE",
            recommendedNoiseLevel = 2,
            recommendedPreset = "SOL_LUXE",
            recommendDeEsser = true,
            recommendSlowAgc = true,
            plainSummary = "Test summary"
        )
        val updated = advice.applyTo(StreamSettings(voiceGenderProfile = "MALE"))
        assertEquals(150, updated.micGainPercent)
        assertEquals(80, updated.hpfCutoffHz)
        assertEquals("RNNOISE", updated.noiseSuppressMode)
        assertEquals(2, updated.rnnoiseLevel)
        assertEquals("SOL_LUXE", updated.voicePreset)
    }

    @Test
    fun abrRecoveryCheck_rejectsMissingMinusOneTelemetryAndAcceptsCalmValidQueue() {
        assertFalse(
            "Unavailable (-1) queue metrics must never trigger ABR step-up",
            SolStreamService.isQueueCalmForAbrRecovery(qFrames = -1, qBytes = -1L, droppedDelta = 0)
        )
        assertFalse(
            "Non-zero dropped frame delta must block ABR step-up",
            SolStreamService.isQueueCalmForAbrRecovery(qFrames = 2, qBytes = 10_000L, droppedDelta = 1)
        )
        assertFalse(
            "High frame queue must block ABR step-up",
            SolStreamService.isQueueCalmForAbrRecovery(qFrames = 14, qBytes = 50_000L, droppedDelta = 0)
        )
        assertTrue(
            "Valid empty queue must allow ABR step-up",
            SolStreamService.isQueueCalmForAbrRecovery(qFrames = 0, qBytes = 0L, droppedDelta = 0)
        )
        assertTrue(
            "Valid low queue must allow ABR step-up",
            SolStreamService.isQueueCalmForAbrRecovery(qFrames = 4, qBytes = 120_000L, droppedDelta = 0)
        )
    }

    @Test
    fun encoderFrameVerification_rejectsFormatChangedAndConfigBuffers() {
        assertFalse(
            "INFO_OUTPUT_FORMAT_CHANGED is not an encoded video frame",
            CodecCapabilityProbe.isRealEncodedFrameOutput(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED, 0, 0)
        )
        assertFalse(
            "Codec config buffer is not an encoded video frame",
            CodecCapabilityProbe.isRealEncodedFrameOutput(0, 64, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
        )
        assertFalse(
            "Zero-byte buffer is not an encoded video frame",
            CodecCapabilityProbe.isRealEncodedFrameOutput(0, 0, 0)
        )
        assertTrue(
            "Non-empty keyframe buffer must be accepted as real encode output",
            CodecCapabilityProbe.isRealEncodedFrameOutput(0, 4096, MediaCodec.BUFFER_FLAG_KEY_FRAME)
        )
    }

    @Test
    fun resolutionAndFpsResolver_stepsDownWhen1080p60UnsupportedOn1080p30Chip() {
        val budgetChip = HardwareEncoderReport(
            codecKey = "H264",
            mimeType = "video/avc",
            encoderName = "c2.mtk.avc.encoder",
            supports4k60 = false,
            supports4k30 = false,
            supports1440p60 = false,
            supports1440p30 = false,
            supports1080p60 = false,
            supports1080p30 = true,
            supports720p60 = true,
            supports720p30 = true,
            supportsCbr = true,
            supportsVbr = true,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true
        )
        val (w, h, fps) = SolVideoPipeline.resolveSupportedResolutionAndFps(
            report = budgetChip,
            reqWidth = 1920,
            reqHeight = 1080,
            reqFps = 60
        )
        assertEquals(1920, w)
        assertEquals(1080, h)
        assertEquals("Must step down to 30 FPS when 1080p60 is unsupported", 30, fps)
    }

    @Test
    fun rnnoiseFallbackAndPartialFrames_attenuateNoiseEvenWhen480SampleNativePathSkipped() {
        val dsp = MicDspProcessor(48000)
        dsp.applySettings(
            StreamSettings(
                voicePreset = "NEURAL",
                noiseSuppressMode = "RNNOISE",
                rnnoiseLevel = 3,
                hpfCutoffHz = 0,
                transientTamerEnabled = false,
                noiseGateEnabled = false,
                compressorEnabled = false
            )
        )
        val partialFrame = FloatArray(240) { 0.001f }
        dsp.processMonoFrameInPlace(partialFrame, 240)
        assertTrue("Partial frame fallback output must be finite", partialFrame.last().isFinite())
        assertTrue("Light spectral fallback must attenuate stationary noise", abs(partialFrame.last()) < 0.001f)
        dsp.release()
    }

    @Test
    fun physicalRamNormalizer_maps7Point17GiBKernelMemTo8GbFlagshipTier() {
        val s24FeKernelBytes = (7.17 * 1024.0 * 1024.0 * 1024.0).toLong()
        assertEquals(8, CodecCapabilityProbe.normalizePhysicalRamGb(s24FeKernelBytes))

        val twelveGbKernelBytes = (11.2 * 1024.0 * 1024.0 * 1024.0).toLong()
        assertEquals(12, CodecCapabilityProbe.normalizePhysicalRamGb(twelveGbKernelBytes))
    }

    @Test
    fun connectionGenerationGuard_rejectsLateSuccessCallbackAfterStop() {
        assertFalse(
            SolStreamService.shouldAcceptConnectionCallback(
                isBroadcastActive = false,
                callbackGen = 1L,
                currentGen = 1L
            )
        )
        assertFalse(
            SolStreamService.shouldAcceptConnectionCallback(
                isBroadcastActive = true,
                callbackGen = 1L,
                currentGen = 2L
            )
        )
        assertFalse(
            SolStreamService.shouldAcceptConnectionCallback(
                isBroadcastActive = true,
                callbackGen = 2L,
                currentGen = 2L,
                isTargetStopped = true
            )
        )
        assertTrue(
            SolStreamService.shouldAcceptConnectionCallback(
                isBroadcastActive = true,
                callbackGen = 2L,
                currentGen = 2L,
                isTargetStopped = false
            )
        )
    }
}
