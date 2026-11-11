/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.media.MediaCodec
import android.media.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class SolStreamUnitTests {

    @Test
    fun ingestUrlNormalizer_addsDefaultTlsPort443AndFixesYoutubeTypo() {
        val raw = "rtmps://a.rtmp.youtube.com/live2/"
        val normalized = KeystoreVault.normalizeIngestUrl(raw)
        assertEquals("rtmps://a.rtmps.youtube.com:443/live2", normalized)
    }

    @Test
    fun ingestUrlNormalizer_preservesQueryParametersCleanly() {
        val raw = "rtmps://b.rtmps.youtube.com/live2?backup=1"
        val normalized = KeystoreVault.normalizeIngestUrl(raw)
        assertEquals("rtmps://b.rtmps.youtube.com:443/live2?backup=1", normalized)
    }

    @Test
    fun joinUrlAndStreamKey_insertsKeyBeforeQueryString() {
        val base = "rtmps://b.rtmps.youtube.com:443/live2?backup=1"
        val joined = KeystoreVault.joinUrlAndStreamKey(base, "abcd-1234-efgh")
        assertEquals("rtmps://b.rtmps.youtube.com:443/live2/abcd-1234-efgh?backup=1", joined)
    }

    @Test
    fun deriveYoutubeBackupUrl_convertsPrimaryToBServerWithBackupFlag() {
        val primary = "rtmps://a.rtmps.youtube.com:443/live2/sol-key-999"
        val backup = KeystoreVault.deriveYoutubeBackupUrl(primary)
        assertEquals("rtmps://b.rtmps.youtube.com:443/live2/sol-key-999?backup=1", backup)

        val twitch = "rtmps://live.twitch.tv:443/app/live_123"
        assertNull(KeystoreVault.deriveYoutubeBackupUrl(twitch))
    }

    @Test
    fun physicalRamNormalizer_mapsKernelReportedBytesToMarketingTier() {
        val bytes8GbPhone = 7_520_000_000L
        val bytes12GbPhone = 11_300_000_000L
        val bytes16GbPhone = 15_100_000_000L
        assertEquals(8, CodecCapabilityProbe.normalizePhysicalRamGb(bytes8GbPhone))
        assertEquals(12, CodecCapabilityProbe.normalizePhysicalRamGb(bytes12GbPhone))
        assertEquals(16, CodecCapabilityProbe.normalizePhysicalRamGb(bytes16GbPhone))
    }

    @Test
    fun macroblockDimensionAlignment_rounds480pTo16PixelHardwareBoundary() {
        val report = HardwareEncoderReport(
            codecKey = "HEVC",
            mimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            encoderName = "c2.exynos.hevc.encoder",
            supports1440p60 = true,
            supports1080p60 = true,
            supportsCbr = true,
            supportsVbr = true,
            widthAlignment = 16,
            heightAlignment = 16,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true,
            verifiedRealEncode = true,
            advertisedByMetadata = true
        )
        val (alignedW, alignedH) = report.alignDimensions(854, 480)
        assertEquals(0, alignedW % 16)
        assertEquals(0, alignedH % 16)
        assertEquals(848, alignedW)
        assertEquals(480, alignedH)
    }

    @Test
    fun realEncodedFrameCheck_rejectsCsdConfigBuffersAndAcceptsRealFrames() {
        val csdFlag = MediaCodec.BUFFER_FLAG_CODEC_CONFIG
        val keyFrameFlag = MediaCodec.BUFFER_FLAG_KEY_FRAME
        assertFalse(CodecCapabilityProbe.isRealEncodedFrameOutput(0, 64, csdFlag))
        assertFalse(CodecCapabilityProbe.isRealEncodedFrameOutput(-1, 1024, 0))
        assertFalse(CodecCapabilityProbe.isRealEncodedFrameOutput(0, 0, 0))
        assertTrue(CodecCapabilityProbe.isRealEncodedFrameOutput(0, 4096, keyFrameFlag))
        assertTrue(CodecCapabilityProbe.isRealEncodedFrameOutput(1, 1200, 0))
    }

    @Test
    fun probeTargetSelector_supportsAllTiersDownTo720p30And480pAnd360p() {
        val (w1, h1, fps1) = CodecCapabilityProbe.selectValidProbeTarget(
            ok1440p60 = false,
            ok1440p30 = true,
            ok1080p60 = true,
            ok1080p30 = true,
            ok720p60 = true
        )
        assertEquals(1920, w1)
        assertEquals(1080, h1)
        assertEquals(60, fps1)

        val (w2, h2, fps2) = CodecCapabilityProbe.selectValidProbeTarget(
            ok1440p60 = false,
            ok1440p30 = false,
            ok1080p60 = false,
            ok1080p30 = false,
            ok720p60 = false,
            ok720p30 = true,
            ok480p = true,
            ok360p = true
        )
        assertEquals(1280, w2)
        assertEquals(720, h2)
        assertEquals(30, fps2)

        val (w3, h3, fps3) = CodecCapabilityProbe.selectValidProbeTarget(
            ok1440p60 = false,
            ok1440p30 = false,
            ok1080p60 = false,
            ok1080p30 = false,
            ok720p60 = false,
            ok720p30 = false,
            ok480p = true,
            ok360p = true,
            aligned480W = 848,
            aligned480H = 480
        )
        assertEquals(848, w3)
        assertEquals(480, h3)
        assertEquals(30, fps3)
    }

    @Test
    fun encoderCandidateScoring_prioritizesRealEncodeOverSurfaceConfigureAndMetadataOnly() {
        val metadataOnly = HardwareEncoderReport(
            codecKey = "HEVC",
            mimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            encoderName = "c2.exynos.hevc.encoder",
            supports1440p60 = true,
            supports1080p60 = true,
            supportsCbr = true,
            supportsVbr = true,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = false,
            verifiedRealEncode = false,
            advertisedByMetadata = true
        )
        val surfaceVerified = metadataOnly.copy(verifiedSurfaceConfigure = true)
        val realEncodeVerified = surfaceVerified.copy(verifiedRealEncode = true)

        val sMeta = CodecCapabilityProbe.scoreEncoderCandidate(metadataOnly)
        val sSurf = CodecCapabilityProbe.scoreEncoderCandidate(surfaceVerified)
        val sReal = CodecCapabilityProbe.scoreEncoderCandidate(realEncodeVerified)

        assertTrue(sSurf > sMeta)
        assertTrue(sReal > sSurf)
    }

    @Test
    fun resolutionFallbackLadder_stepsDownCleanlyFrom1440p60To360p() {
        val report = HardwareEncoderReport(
            codecKey = "HEVC",
            mimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            encoderName = "c2.exynos.hevc.encoder",
            supports1440p60 = true,
            supports1440p30 = true,
            supports1080p60 = true,
            supports1080p30 = true,
            supports720p60 = true,
            supports720p30 = true,
            supports480p = true,
            supports360p = true,
            supportsCbr = true,
            supportsVbr = true,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true,
            verifiedRealEncode = true
        )
        val ladder = SolVideoPipeline.buildResolutionFallbackLadder(report, 2560, 1440, 60)
        assertEquals(Triple(2560, 1440, 60), ladder.first())
        assertTrue(ladder.contains(Triple(1920, 1080, 60)))
        assertTrue(ladder.contains(Triple(1280, 720, 30)))
        assertEquals(Triple(640, 360, 30), ladder.last())
    }

    @Test
    fun overlayQueueTagFormatter_displaysNAWhenTelemetryUnavailable() {
        assertEquals("Q:N/A", SolStreamService.formatOverlayQueueTag(-1))
        assertEquals("Q:0f", SolStreamService.formatOverlayQueueTag(0))
        assertEquals("Q:14f", SolStreamService.formatOverlayQueueTag(14))
    }

    @Test
    fun sessionGenerationGuard_blocksStaleOrStoppedRtmpCallbacks() {
        assertTrue(SolStreamService.shouldAcceptConnectionCallback(true, 5L, 5L, false))
        assertFalse(SolStreamService.shouldAcceptConnectionCallback(false, 5L, 5L, false))
        assertFalse(SolStreamService.shouldAcceptConnectionCallback(true, 4L, 5L, false))
        assertFalse(SolStreamService.shouldAcceptConnectionCallback(true, 5L, 5L, true))
    }

    @Test
    fun abrRecoveryGate_requiresAtLeastOneValidQueueMetricBeforeStepUp() {
        assertFalse(SolStreamService.isQueueCalmForAbrRecovery(-1, -1L, 0))
        assertFalse(SolStreamService.isQueueCalmForAbrRecovery(2, 10_000L, 1))
        assertFalse(SolStreamService.isQueueCalmForAbrRecovery(12, 50_000L, 0))
        assertTrue(SolStreamService.isQueueCalmForAbrRecovery(3, 80_000L, 0))
        assertTrue(SolStreamService.isQueueCalmForAbrRecovery(2, -1L, 0))
    }

    @Test
    fun diagnosticsBitrateTracker_computesAccurateSessionAverage() {
        DiagnosticsLogger.recordBitrateSample(20_000)
        DiagnosticsLogger.recordBitrateSample(16_000)
        DiagnosticsLogger.recordBitrateSample(18_000)
        assertEquals(18_000, DiagnosticsLogger.lastBitrateKbps)
        assertTrue(DiagnosticsLogger.getSessionAverageBitrateKbps() in 16_000..20_000)
    }

    @Test
    fun micDspSparkBlanker_clampsMultiStrikeMosquitoRacketImpulsesWhilePreservingSpeech() {
        val dsp = MicDspProcessor(48000)
        val luxe = MicDspProcessor.applyPresetToSettings(
            StreamSettings(voiceGenderProfile = "MALE"),
            "SOL_LUXE"
        )
        dsp.applySettings(luxe)

        val quietFrame = FloatArray(480) { 0.001f * sin(2.0 * PI * 200.0 * it / 48000.0).toFloat() }
        repeat(15) {
            val copy = quietFrame.clone()
            dsp.processMonoFrameInPlace(copy, copy.size)
        }

        // Simulate 4 rapid electric mosquito-racket spark strikes ("phat-phat-phat") without 220 Hz vocal body
        for (strike in 1..4) {
            val sparkFrame = FloatArray(480) { 0.001f }
            sparkFrame[40] = 0.95f
            sparkFrame[41] = -0.92f
            sparkFrame[42] = 0.88f
            sparkFrame[43] = -0.84f
            dsp.processMonoFrameInPlace(sparkFrame, sparkFrame.size)
            val clampedPeak = sparkFrame.maxOf { abs(it) }
            assertTrue("Spark strike #$strike should be heavily attenuated (<0.38f), was $clampedPeak", clampedPeak < 0.38f)
        }
    }

    @Test
    fun micDspAllThreeGenderProfiles_preserveCleanSpeechInSolLuxeMode() {
        for (gender in listOf("MALE", "FEMALE", "NEUTRAL")) {
            val dsp = MicDspProcessor(48000)
            val luxe = MicDspProcessor.applyPresetToSettings(
                StreamSettings(voiceGenderProfile = gender, micGainPercent = 125),
                "SOL_LUXE"
            )
            dsp.applySettings(luxe)

            val fundHz = when (gender) {
                "MALE" -> 125.0
                "FEMALE" -> 210.0
                else -> 165.0
            }
            var lastRms = 0f
            repeat(8) {
                val speech = FloatArray(480) { idx ->
                    val t = idx.toDouble() / 48000.0
                    (0.12 * sin(2.0 * PI * fundHz * t) +
                        0.06 * sin(2.0 * PI * (fundHz * 2.0) * t) +
                        0.04 * sin(2.0 * PI * 3200.0 * t)).toFloat()
                }
                dsp.processMonoFrameInPlace(speech, speech.size)
                var sumSq = 0f
                for (s in speech) {
                    assertTrue(s.isFinite())
                    assertTrue(abs(s) <= 1.0f)
                    sumSq += s * s
                }
                lastRms = sqrt(sumSq / speech.size)
            }
            assertTrue("$gender speech RMS should remain strong and clear (>0.06f), was $lastRms", lastRms > 0.06f)
        }
    }
}
