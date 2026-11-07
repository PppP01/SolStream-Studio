/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 */

package com.solgaming.solstreamstudio

import org.junit.Assert.assertEquals
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
        val base = StreamSettings(voiceGenderProfile = "MALE", gameGainPercent = 250, targetBitrateKbps = 100000)
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
}
