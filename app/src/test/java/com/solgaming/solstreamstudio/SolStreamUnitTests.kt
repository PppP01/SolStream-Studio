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
    fun genderMatchedHpfCoordinator_resolvesExact60HzMale75HzNeutral95HzFemale() {
        assertEquals(60, KeystoreVault.resolveDefaultHpfForProfile("MALE"))
        assertEquals(75, KeystoreVault.resolveDefaultHpfForProfile("NEUTRAL"))
        assertEquals(95, KeystoreVault.resolveDefaultHpfForProfile("FEMALE"))

        val base = StreamSettings(voiceGenderProfile = "NEUTRAL", hpfCutoffHz = 75)
        val maleApplied = MicDspProcessor.applyGenderProfileToSettings(base, "MALE")
        assertEquals("MALE", maleApplied.voiceGenderProfile)
        assertEquals(60, maleApplied.hpfCutoffHz)

        val femaleApplied = MicDspProcessor.applyGenderProfileToSettings(maleApplied, "FEMALE")
        assertEquals("FEMALE", femaleApplied.voiceGenderProfile)
        assertEquals(95, femaleApplied.hpfCutoffHz)
    }

    @Test
    fun smartClipboardGuard_stripsInvisibleWhitespaceAndDetectsRtmpUrls() {
        val dirtyKey = "  abcd-1234-xy98\n\t\u200B\uFEFF  "
        assertEquals("abcd-1234-xy98", KeystoreVault.sanitizeStreamKeyInput(dirtyKey))
        assertEquals("🔒 Key Saved (••••xy98)", KeystoreVault.getMaskedKeyStatusBadge(dirtyKey))
        assertEquals("⚠️ Empty", KeystoreVault.getMaskedKeyStatusBadge("   \n "))

        assertTrue(KeystoreVault.isLikelyRtmpUrl("  rtmps://a.rtmps.youtube.com:443/live2 "))
        assertTrue(KeystoreVault.isLikelyRtmpUrl("rtmp://live.twitch.tv/app"))
        assertFalse(KeystoreVault.isLikelyRtmpUrl("abcd-1234-efgh-5678"))
    }

    @Test
    fun platformAwareTierPresets_respectTwitch6MbpsAndKick8MbpsAndYoutubeHevc() {
        val hevcReport = HardwareEncoderReport(
            codecKey = "HEVC",
            mimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            encoderName = "c2.exynos.hevc.encoder",
            supports1440p60 = true,
            supports1080p60 = true,
            supportsCbr = true,
            supportsVbr = true,
            maxConcurrentInstances = 2,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true,
            verifiedRealEncode = true
        )
        val h264Report = hevcReport.copy(
            codecKey = "H264",
            mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            encoderName = "c2.exynos.avc.encoder"
        )
        val scan = DeviceScanSummary(
            deviceModel = "samsung SM-S721B",
            socModel = "Samsung Exynos 2400e",
            androidRelease = "16",
            sdkInt = 36,
            totalRamGb = 8,
            maxDisplayHz = 120,
            activeDisplayHz = 120,
            hevcHardware = hevcReport,
            h264Hardware = h264Report,
            av1Hardware = null,
            recommendedTier = DeviceTier.FLAGSHIP,
            recommendedCodec = "HEVC",
            recommendedWidth = 2560,
            recommendedHeight = 1440
        )

        val messyUserSettings = StreamSettings(
            platformPreset = "YOUTUBE",
            voiceGenderProfile = "MALE",
            maxBFrames = 2,
            qpMaxCap = 42,
            useVbr = true
        )
        val ytFlagship = KeystoreVault.applyPlatformAndVoiceAwareTierPreset(
            messyUserSettings,
            DeviceTier.FLAGSHIP,
            scan
        )
        assertEquals("HEVC", ytFlagship.codec)
        assertEquals(2560, ytFlagship.width)
        assertEquals(1440, ytFlagship.height)
        assertEquals(20000, ytFlagship.targetBitrateKbps)
        assertEquals(60, ytFlagship.hpfCutoffHz)
        assertEquals(0, ytFlagship.maxBFrames)
        assertEquals(0, ytFlagship.qpMaxCap)
        assertFalse(ytFlagship.useVbr)

        val twitchFlagship = KeystoreVault.applyPlatformAndVoiceAwareTierPreset(
            ytFlagship.copy(platformPreset = "TWITCH"),
            DeviceTier.FLAGSHIP,
            scan
        )
        assertEquals("H264", twitchFlagship.codec)
        assertEquals(1920, twitchFlagship.width)
        assertEquals(1080, twitchFlagship.height)
        assertEquals(6000, twitchFlagship.targetBitrateKbps)
        assertEquals(160, twitchFlagship.audioBitrateKbps)

        val kickFlagship = KeystoreVault.applyPlatformAndVoiceAwareTierPreset(
            ytFlagship.copy(platformPreset = "KICK"),
            DeviceTier.FLAGSHIP,
            scan
        )
        assertEquals("H264", kickFlagship.codec)
        assertEquals(8000, kickFlagship.targetBitrateKbps)
        assertEquals(160, kickFlagship.audioBitrateKbps)
    }

    @Test
    fun crossPhonePresetClamping_adaptsFlagship1440pHevcPresetToBudget1080p30H264Phone() {
        val budgetH264 = HardwareEncoderReport(
            codecKey = "H264",
            mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            encoderName = "c2.mtk.avc.encoder",
            supports1440p60 = false,
            supports1440p30 = false,
            supports1080p60 = false,
            supports1080p30 = true,
            supportsCbr = true,
            supportsVbr = true,
            maxBitrateMbps = 12,
            maxConcurrentInstances = 1,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true,
            verifiedRealEncode = true
        )
        val budgetScan = DeviceScanSummary(
            deviceModel = "Budget Phone",
            socModel = "Helio G85",
            androidRelease = "14",
            sdkInt = 34,
            totalRamGb = 4,
            maxDisplayHz = 60,
            activeDisplayHz = 60,
            hevcHardware = null,
            h264Hardware = budgetH264,
            av1Hardware = null,
            recommendedTier = DeviceTier.LITE,
            recommendedCodec = "H264",
            recommendedWidth = 1920,
            recommendedHeight = 1080,
            recommendedFps = 30
        )

        val flagshipPreset = StreamSettings(
            codec = "HEVC",
            width = 2560,
            height = 1440,
            fps = 60,
            targetBitrateKbps = 20000,
            hdr10Enabled = true,
            dualFormatCompanionEnabled = true
        )
        val (clamped, wasChanged) = KeystoreVault.clampImportedSettingsToHardware(flagshipPreset, budgetScan)
        assertTrue(wasChanged)
        assertEquals("H264", clamped.codec)
        assertEquals(1920, clamped.width)
        assertEquals(1080, clamped.height)
        assertEquals(30, clamped.fps)
        assertEquals(12000, clamped.targetBitrateKbps)
        assertFalse(clamped.hdr10Enabled)
        assertFalse(clamped.dualFormatCompanionEnabled)
    }

    @Test
    fun dualFormatCompanionSupport_checksAllHardwareEncodersIncludingH264AndAv1() {
        val singleInstanceHevc = HardwareEncoderReport(
            codecKey = "HEVC",
            mimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            encoderName = "c2.vendor.hevc.encoder",
            supports1440p60 = true,
            supports1080p60 = true,
            supportsCbr = true,
            supportsVbr = true,
            maxConcurrentInstances = 1,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true
        )
        val dualInstanceH264 = singleInstanceHevc.copy(
            codecKey = "H264",
            mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            encoderName = "c2.vendor.avc.encoder",
            maxConcurrentInstances = 2
        )
        val summary = DeviceScanSummary(
            deviceModel = "Global Flagship",
            socModel = "Snapdragon 8 Gen 3",
            androidRelease = "16",
            sdkInt = 36,
            totalRamGb = 8,
            hevcHardware = singleInstanceHevc,
            h264Hardware = dualInstanceH264,
            av1Hardware = null,
            recommendedCodec = "HEVC",
            recommendedWidth = 2560,
            recommendedHeight = 1440
        )
        assertTrue(summary.supportsDualFormatCompanion())
    }

    @Test
    fun globalSocFriendlyMapper_mapsCrypticChipCodesToCleanProcessorNames() {
        assertEquals("Samsung Exynos 2400e", CodecCapabilityProbe.formatFriendlySocName("Samsung s5e9945", "s5e9945"))
        assertEquals("Snapdragon 8 Gen 3", CodecCapabilityProbe.formatFriendlySocName("Qualcomm SM8650", "qcom"))
        assertEquals("MediaTek Dimensity 8300", CodecCapabilityProbe.formatFriendlySocName("MediaTek MT6897", "mt6897"))
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

    @Test
    fun micDsp12ModeHardwareMatrix_adaptsPhoneStudioWiredUsbAndBluetoothAndPostAgcBoost() {
        for (hwMode in HardwareAcousticMode.entries) {
            val dsp100 = MicDspProcessor(48000)
            dsp100.applyHardwareAcousticMode(hwMode)
            dsp100.applySettings(
                MicDspProcessor.applyPresetToSettings(
                    StreamSettings(voiceGenderProfile = "MALE", micGainPercent = 100),
                    "SOL_LUXE"
                )
            )

            val dsp180 = MicDspProcessor(48000)
            dsp100.applyHardwareAcousticMode(hwMode)
            dsp180.applyHardwareAcousticMode(hwMode)
            dsp180.applySettings(
                MicDspProcessor.applyPresetToSettings(
                    StreamSettings(voiceGenderProfile = "MALE", micGainPercent = 180),
                    "SOL_LUXE"
                )
            )

            var rms100 = 0f
            var rms180 = 0f
            repeat(6) {
                val frameA = FloatArray(480) { idx ->
                    val t = idx.toDouble() / 48000.0
                    (0.10 * sin(2.0 * PI * 115.0 * t) + 0.04 * sin(2.0 * PI * 2800.0 * t)).toFloat()
                }
                val frameB = frameA.clone()
                dsp100.processMonoFrameInPlace(frameA, frameA.size)
                dsp180.processMonoFrameInPlace(frameB, frameB.size)

                rms100 = sqrt(frameA.fold(0f) { acc, v -> acc + v * v } / frameA.size)
                rms180 = sqrt(frameB.fold(0f) { acc, v -> acc + v * v } / frameB.size)
            }
            assertTrue("Mode ${hwMode.name}: 180% Post-DSP Mic Boost ($rms180) must be louder than 100% ($rms100)", rms180 > rms100 * 1.35f)
        }
    }
}
