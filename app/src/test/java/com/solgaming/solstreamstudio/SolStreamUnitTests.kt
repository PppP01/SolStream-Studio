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
import kotlin.math.roundToInt
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
    fun genderMatchedHpfCoordinator_resolves55Male68Neutral85FemaleAndPreservesManualHpf() {
        assertEquals(55, KeystoreVault.resolveDefaultHpfForProfile("MALE"))
        assertEquals(68, KeystoreVault.resolveDefaultHpfForProfile("NEUTRAL"))
        assertEquals(85, KeystoreVault.resolveDefaultHpfForProfile("FEMALE"))
        assertTrue(MicDspProcessor.HPF_CUTOFF_STEPS.containsAll(listOf(0, 50, 55, 60, 65, 68, 70, 75, 85, 95, 105, 120)))

        // Auto HPF tracks gender profile changes cleanly
        val autoBase = StreamSettings(voiceGenderProfile = "NEUTRAL", hpfCutoffHz = 68, hpfAutoMode = true)
        val maleApplied = MicDspProcessor.applyGenderProfileToSettings(autoBase, "MALE")
        assertEquals("MALE", maleApplied.voiceGenderProfile)
        assertEquals(55, maleApplied.hpfCutoffHz)

        val femaleApplied = MicDspProcessor.applyGenderProfileToSettings(maleApplied, "FEMALE")
        assertEquals("FEMALE", femaleApplied.voiceGenderProfile)
        assertEquals(85, femaleApplied.hpfCutoffHz)

        // Manual HPF (hpfAutoMode = false) must NEVER be overwritten when switching presets or profiles
        val manualMale = StreamSettings(voiceGenderProfile = "MALE", hpfCutoffHz = 85, hpfAutoMode = false)
        val afterStudioPreset = MicDspProcessor.applyPresetToSettings(manualMale, "STUDIO")
        assertEquals(85, afterStudioPreset.hpfCutoffHz)
        assertFalse(afterStudioPreset.hpfAutoMode)

        val afterCleanPreset = MicDspProcessor.applyPresetToSettings(afterStudioPreset, "CLEAN")
        assertEquals(85, afterCleanPreset.hpfCutoffHz)
        assertFalse(afterCleanPreset.hpfAutoMode)
    }

    @Test
    fun tierPresetCoordinator_preservesExplicitManualHpfWhenAutoModeIsOff() {
        val manualSettings = StreamSettings(
            platformPreset = "YOUTUBE",
            voiceGenderProfile = "MALE",
            hpfCutoffHz = 95,
            hpfAutoMode = false
        )
        val applied = KeystoreVault.applyPlatformAndVoiceAwareTierPreset(
            current = manualSettings,
            tier = DeviceTier.FLAGSHIP,
            scan = null
        )
        assertEquals(95, applied.hpfCutoffHz)
        assertFalse(applied.hpfAutoMode)
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
            androidRelease = "17",
            sdkInt = 37,
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
            hpfAutoMode = true,
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
        assertEquals(55, ytFlagship.hpfCutoffHz)
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
    fun dualFormatCompanionSupport_selectsOnlyEligibleConcurrentCodec() {
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
        // Because HEVC supports only 1 instance, companion MUST select H264 instead of failing on HEVC
        assertEquals("H264", summary.selectCompanionEncoder(primaryCodec = "HEVC")?.codecKey)
        assertEquals(listOf("H264"), summary.eligibleCompanionEncoders(primaryCodec = "HEVC").map { it.codecKey })

        // When both support >= 2 instances, vertical companion prefers HEVC first with H264 fallback
        val dualBoth = summary.copy(hevcHardware = singleInstanceHevc.copy(maxConcurrentInstances = 2))
        assertEquals(listOf("HEVC", "H264"), dualBoth.eligibleCompanionEncoders("HEVC").map { it.codecKey })
        assertEquals(listOf("H264"), dualBoth.eligibleCompanionEncoders("HEVC", requireH264Only = true).map { it.codecKey })

        // When neither supports >= 2 instances, dual companion must be rejected
        val singleBoth = summary.copy(h264Hardware = dualInstanceH264.copy(maxConcurrentInstances = 1))
        assertFalse(singleBoth.supportsDualFormatCompanion())
        assertNull(singleBoth.selectCompanionEncoder("HEVC"))
    }

    @Test
    fun globalSocAndGpuFriendlyMapper_mapsCrypticChipAndAngleStringsCleanly() {
        assertEquals("Samsung Exynos 2400e", CodecCapabilityProbe.formatFriendlySocName("Samsung s5e9945", "s5e9945"))
        assertEquals("Snapdragon 8 Gen 3", CodecCapabilityProbe.formatFriendlySocName("Qualcomm SM8650", null))
        assertEquals("Snapdragon 6 Gen 1", CodecCapabilityProbe.formatFriendlySocName("Qualcomm SM6450", "qcom"))
        assertEquals("MediaTek Dimensity 8300", CodecCapabilityProbe.formatFriendlySocName("MediaTek MT6897", "mt6897"))

        val angleRaw = "ANGLE ((Samsung Xclipse 940) on Vulkan 1.3.304)"
        assertEquals("Samsung Xclipse 940 (Vulkan)", CodecCapabilityProbe.formatFriendlyGpuName(angleRaw, shortBadge = false))
        assertEquals("Xclipse 940", CodecCapabilityProbe.formatFriendlyGpuName(angleRaw, shortBadge = true))
    }

    @Test
    fun micDspMicroImpulseClamper_shavesMosquitoRacketZapsUnder06ms() {
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

        // Simulate 4 rapid sub-millisecond electric mosquito-racket spark strikes
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
    fun micDspAll12HardwareAndGenderCombinations_preserveCleanSpeechAndPostDspBoost() {
        for (hwMode in HardwareAcousticMode.entries) {
            for (gender in listOf("MALE", "NEUTRAL", "FEMALE")) {
                val dsp100 = MicDspProcessor(48000)
                dsp100.applyHardwareAcousticMode(hwMode)
                dsp100.applySettings(
                    MicDspProcessor.applyPresetToSettings(
                        StreamSettings(voiceGenderProfile = gender, micGainPercent = 100),
                        "SOL_LUXE"
                    )
                )

                val dsp180 = MicDspProcessor(48000)
                dsp180.applyHardwareAcousticMode(hwMode)
                dsp180.applySettings(
                    MicDspProcessor.applyPresetToSettings(
                        StreamSettings(voiceGenderProfile = gender, micGainPercent = 180),
                        "SOL_LUXE"
                    )
                )

                val fundHz = when (gender) {
                    "MALE" -> 115.0
                    "FEMALE" -> 210.0
                    else -> 155.0
                }
                var rms100 = 0f
                var rms180 = 0f
                repeat(8) { frameIdx ->
                    val frameA = FloatArray(480) { i ->
                        val sampleIdx = frameIdx * 480 + i
                        val t = sampleIdx.toDouble() / 48000.0
                        (0.10 * sin(2.0 * PI * fundHz * t) +
                            0.07 * sin(2.0 * PI * 260.0 * t) +
                            0.04 * sin(2.0 * PI * 2800.0 * t)).toFloat()
                    }
                    val frameB = frameA.clone()
                    dsp100.processMonoFrameInPlace(frameA, frameA.size)
                    dsp180.processMonoFrameInPlace(frameB, frameB.size)

                    for (s in frameA) {
                        assertTrue("$hwMode/$gender sample must be finite", s.isFinite())
                        assertTrue("$hwMode/$gender must obey -1.16 dBTP ceiling", abs(s) <= MicDspProcessor.TRUE_PEAK_CEILING_LINEAR + 0.002f)
                    }
                    for (s in frameB) {
                        assertTrue("$hwMode/$gender 180% sample must be finite", s.isFinite())
                        assertTrue("$hwMode/$gender 180% must obey -1.16 dBTP ceiling", abs(s) <= MicDspProcessor.TRUE_PEAK_CEILING_LINEAR + 0.002f)
                    }

                    rms100 = sqrt(frameA.fold(0f) { acc, v -> acc + v * v } / frameA.size)
                    rms180 = sqrt(frameB.fold(0f) { acc, v -> acc + v * v } / frameB.size)
                }
                assertTrue("$hwMode/$gender speech RMS must remain clear (>0.055f), was $rms100", rms100 > 0.055f)
                assertTrue(
                    "$hwMode/$gender: 180% Post-DSP Mic Boost ($rms180) must be louder than 100% ($rms100)",
                    rms180 > rms100 * 1.30f
                )
            }
        }
    }

    @Test
    fun privacyBlurRectangles_resolveValidScissorBoundsAcrossAll8ZonesIn16x9And9x16() {
        val resolutions = listOf(2560 to 1440, 1080 to 1920)
        for ((dstW, dstH) in resolutions) {
            for (zone in SolStreamService.PRIVACY_BLUR_ZONES) {
                val rect = SolVideoPipeline.resolvePrivacyBlurRect(dstW, dstH, zone)
                assertTrue("Zone $zone x (${rect.x}) must be >= 0", rect.x >= 0)
                assertTrue("Zone $zone y (${rect.y}) must be >= 0", rect.y >= 0)
                assertTrue("Zone $zone w (${rect.w}) must be >= 32", rect.w >= 32)
                assertTrue("Zone $zone h (${rect.h}) must be >= 32", rect.h >= 32)
                assertTrue("Zone $zone x+w (${rect.x + rect.w}) must fit inside $dstW", rect.x + rect.w <= dstW)
                assertTrue("Zone $zone y+h (${rect.y + rect.h}) must fit inside $dstH", rect.y + rect.h <= dstH)
            }
            val kbRect = SolVideoPipeline.resolvePrivacyBlurRect(dstW, dstH, "KEYBOARD")
            assertEquals(0, kbRect.x)
            assertEquals(0, kbRect.y)
            assertEquals(dstW, kbRect.w)
            assertTrue(kbRect.h > dstH / 2)
        }
    }

    @Test
    fun micDspLowBandWarmthAndChestContour_enrichesBaritoneWithZeroIdleRumble() {
        val dspLuxe = MicDspProcessor(48000)
        dspLuxe.applyHardwareAcousticMode(HardwareAcousticMode.PHONE_STUDIO)
        dspLuxe.applySettings(
            StreamSettings(
                voiceGenderProfile = "MALE",
                voicePreset = "SOL_LUXE",
                hpfCutoffHz = 55,
                voiceEqEnabled = true,
                velvetWarmthEnabled = true,
                compressorEnabled = false,
                whisperLiftEnabled = false,
                slowAgcEnabled = false,
                micGainPercent = 100
            )
        )

        // 1. Verify zero low-band rumble during silence
        val silentFrame = FloatArray(480) { 0f }
        repeat(5) {
            dspLuxe.processMonoFrameInPlace(silentFrame, silentFrame.size)
        }
        val maxSilentOut = silentFrame.maxOf { abs(it) }
        assertTrue("Low-band analog warmth shaper must output 0.0 during silence, was $maxSilentOut", maxSilentOut < 1e-6f)

        // 2. Compare 98 Hz baritone fundamental + 245 Hz vowel enrichment against dry CLEAN mode
        val dspDry = MicDspProcessor(48000)
        dspDry.applyHardwareAcousticMode(HardwareAcousticMode.PHONE_STUDIO)
        dspDry.applySettings(
            StreamSettings(
                voiceGenderProfile = "MALE",
                voicePreset = "CLEAN",
                hpfCutoffHz = 55,
                voiceEqEnabled = false,
                velvetWarmthEnabled = false,
                compressorEnabled = false,
                whisperLiftEnabled = false,
                slowAgcEnabled = false,
                micGainPercent = 100
            )
        )

        var rmsLuxe = 0f
        var rmsDry = 0f
        repeat(10) { frameIdx ->
            val fLuxe = FloatArray(480) { i ->
                val sampleIdx = frameIdx * 480 + i
                val t = sampleIdx.toDouble() / 48000.0
                (0.12 * sin(2.0 * PI * 98.0 * t) + 0.065 * sin(2.0 * PI * 245.0 * t)).toFloat()
            }
            val fDry = fLuxe.clone()
            dspLuxe.processMonoFrameInPlace(fLuxe, fLuxe.size)
            dspDry.processMonoFrameInPlace(fDry, fDry.size)

            rmsLuxe = sqrt(fLuxe.fold(0f) { acc, v -> acc + v * v } / fLuxe.size)
            rmsDry = sqrt(fDry.fold(0f) { acc, v -> acc + v * v } / fDry.size)
        }
        assertTrue("SolLuxe 3.0 95Hz chest + low-band warmth ($rmsLuxe) must enrich baritone (>1.25x dry $rmsDry)", rmsLuxe > rmsDry * 1.25f)
    }

    @Test
    fun rtmpRttMicrosecondToMillisecondConverter_scalesPedroMicrosAccurately() {
        assertEquals(-1L, SolStreamService.convertRttMicrosToMs(-1L))
        assertEquals(-1L, SolStreamService.convertRttMicrosToMs(0L))
        assertEquals(1L, SolStreamService.convertRttMicrosToMs(350L))
        assertEquals(25L, SolStreamService.convertRttMicrosToMs(25_000L))
        assertEquals(80L, SolStreamService.convertRttMicrosToMs(80_200L))
    }

    @Test
    fun micDspSplitBandPlosiveGuard_ducksSub115HzAirBlastWhileKeepingVocalHarmonics() {
        val dspGuard = MicDspProcessor(48000)
        dspGuard.applyHardwareAcousticMode(HardwareAcousticMode.PHONE_STUDIO)
        dspGuard.applySettings(
            StreamSettings(
                voiceGenderProfile = "MALE",
                voicePreset = "CLEAN",
                hpfCutoffHz = 55,
                transientTamerEnabled = false,
                noiseSuppressMode = "OFF",
                rnnoiseLevel = 0,
                noiseGateEnabled = false,
                voiceEqEnabled = false,
                velvetWarmthEnabled = false,
                deEsserEnabled = false,
                compressorEnabled = false,
                whisperLiftEnabled = false,
                slowAgcEnabled = false,
                micGainPercent = 100
            )
        )

        var rawRms = 0f
        var guardedRms = 0f
        repeat(8) { frameIdx ->
            val windFrame = FloatArray(480) { i ->
                val sampleIdx = frameIdx * 480 + i
                val t = sampleIdx.toDouble() / 48000.0
                (0.18 * sin(2.0 * PI * 75.0 * t)).toFloat()
            }
            rawRms = sqrt(windFrame.fold(0f) { acc, v -> acc + v * v } / windFrame.size)
            dspGuard.processMonoFrameInPlace(windFrame, windFrame.size)
            guardedRms = sqrt(windFrame.fold(0f) { acc, v -> acc + v * v } / windFrame.size)
        }
        assertTrue(
            "Stage 4 Split-Band Plosive Guard must attenuate non-vocal 75Hz air blast ($guardedRms < 0.55x of $rawRms)",
            guardedRms < rawRms * 0.55f
        )
    }

    @Test
    fun micDspHybridVadTracker_detectsSpeechWhenTransientTamerAndRnnoiseAreOff() {
        val dsp = MicDspProcessor(48000)
        dsp.applySettings(
            StreamSettings(
                voiceGenderProfile = "MALE",
                voicePreset = "STUDIO",
                transientTamerEnabled = false,
                noiseSuppressMode = "OFF",
                rnnoiseLevel = 0,
                noiseGateEnabled = true,
                micGainPercent = 100
            )
        )

        repeat(6) { frameIdx ->
            val speech = FloatArray(480) { i ->
                val sampleIdx = frameIdx * 480 + i
                val t = sampleIdx.toDouble() / 48000.0
                (0.11 * sin(2.0 * PI * 115.0 * t) +
                    0.07 * sin(2.0 * PI * 250.0 * t) +
                    0.03 * sin(2.0 * PI * 2900.0 * t)).toFloat()
            }
            dsp.processMonoFrameInPlace(speech, speech.size)
        }
        assertTrue("Hybrid 16kHz-safe VAD must detect speech even when transientTamerEnabled is false", dsp.vadSpeechActive)
        assertTrue("Hybrid VAD probability should be >= 0.45f", dsp.lastVadProbability >= 0.45f)
    }

    @Test
    fun micDspAirShelfDeEsserAndBluetoothLpf_maintainCleanFrequencySeparation() {
        // 1. Clean 8.5 kHz Air Shelf boosts top-end air in PHONE_STUDIO
        val dspFlat = MicDspProcessor(48000).apply {
            applyHardwareAcousticMode(HardwareAcousticMode.PHONE_STUDIO)
            applySettings(
                StreamSettings(
                    voiceGenderProfile = "FEMALE",
                    voicePreset = "SOL_LUXE",
                    hpfCutoffHz = 0,
                    transientTamerEnabled = false,
                    noiseSuppressMode = "OFF",
                    rnnoiseLevel = 0,
                    noiseGateEnabled = false,
                    voiceEqEnabled = false,
                    velvetWarmthEnabled = false,
                    deEsserEnabled = false,
                    compressorEnabled = false,
                    whisperLiftEnabled = false,
                    slowAgcEnabled = false,
                    micGainPercent = 100
                )
            )
        }
        val dspAir = MicDspProcessor(48000).apply {
            applyHardwareAcousticMode(HardwareAcousticMode.PHONE_STUDIO)
            applySettings(
                StreamSettings(
                    voiceGenderProfile = "FEMALE",
                    voicePreset = "SOL_LUXE",
                    hpfCutoffHz = 0,
                    transientTamerEnabled = false,
                    noiseSuppressMode = "OFF",
                    rnnoiseLevel = 0,
                    noiseGateEnabled = false,
                    voiceEqEnabled = true,
                    velvetWarmthEnabled = false,
                    deEsserEnabled = false,
                    compressorEnabled = false,
                    whisperLiftEnabled = false,
                    slowAgcEnabled = false,
                    micGainPercent = 100
                )
            )
        }
        val dspBt = MicDspProcessor(48000).apply {
            applyHardwareAcousticMode(HardwareAcousticMode.BLUETOOTH_SCO)
            applySettings(
                StreamSettings(
                    voiceGenderProfile = "FEMALE",
                    voicePreset = "SOL_LUXE",
                    hpfCutoffHz = 0,
                    transientTamerEnabled = false,
                    noiseSuppressMode = "OFF",
                    rnnoiseLevel = 0,
                    noiseGateEnabled = false,
                    voiceEqEnabled = true,
                    velvetWarmthEnabled = false,
                    deEsserEnabled = false,
                    compressorEnabled = false,
                    whisperLiftEnabled = false,
                    slowAgcEnabled = false,
                    micGainPercent = 100
                )
            )
        }

        var rmsFlat = 0f
        var rmsAir = 0f
        var rmsBt = 0f
        repeat(8) { frameIdx ->
            val tone9500 = FloatArray(480) { i ->
                val t = (frameIdx * 480 + i).toDouble() / 48000.0
                (0.08 * sin(2.0 * PI * 9500.0 * t)).toFloat()
            }
            val fA = tone9500.clone()
            val fB = tone9500.clone()
            val fC = tone9500.clone()
            dspFlat.processMonoFrameInPlace(fA, fA.size)
            dspAir.processMonoFrameInPlace(fB, fB.size)
            dspBt.processMonoFrameInPlace(fC, fC.size)
            rmsFlat = sqrt(fA.fold(0f) { acc, v -> acc + v * v } / fA.size)
            rmsAir = sqrt(fB.fold(0f) { acc, v -> acc + v * v } / fB.size)
            rmsBt = sqrt(fC.fold(0f) { acc, v -> acc + v * v } / fC.size)
        }
        assertTrue("8.5 kHz Air Shelf ($rmsAir) must boost 9.5 kHz air over flat ($rmsFlat)", rmsAir > rmsFlat * 1.12f)
        assertTrue("Bluetooth SCO 5.2 kHz LPF ($rmsBt) must roll off 9.5 kHz hiss vs Phone Studio ($rmsAir)", rmsBt < rmsAir * 0.45f)

        // 2. Dynamic De-Esser tames sharp 6.2 kHz Male sibilance without dulling warm 250 Hz vowels
        val dspNoDeEss = MicDspProcessor(48000).apply {
            applySettings(
                StreamSettings(
                    voiceGenderProfile = "MALE",
                    voicePreset = "CLEAN",
                    hpfCutoffHz = 0,
                    transientTamerEnabled = false,
                    noiseSuppressMode = "OFF",
                    rnnoiseLevel = 0,
                    noiseGateEnabled = false,
                    voiceEqEnabled = false,
                    velvetWarmthEnabled = false,
                    deEsserEnabled = false,
                    compressorEnabled = false,
                    whisperLiftEnabled = false,
                    slowAgcEnabled = false,
                    micGainPercent = 100
                )
            )
        }
        val dspDeEss = MicDspProcessor(48000).apply {
            applySettings(
                StreamSettings(
                    voiceGenderProfile = "MALE",
                    voicePreset = "CLEAN",
                    hpfCutoffHz = 0,
                    transientTamerEnabled = false,
                    noiseSuppressMode = "OFF",
                    rnnoiseLevel = 0,
                    noiseGateEnabled = false,
                    voiceEqEnabled = false,
                    velvetWarmthEnabled = false,
                    deEsserEnabled = true,
                    compressorEnabled = false,
                    whisperLiftEnabled = false,
                    slowAgcEnabled = false,
                    micGainPercent = 100
                )
            )
        }
        var sibDryRms = 0f
        var sibTamedRms = 0f
        repeat(8) { frameIdx ->
            val hiss = FloatArray(480) { i ->
                val t = (frameIdx * 480 + i).toDouble() / 48000.0
                (0.18 * sin(2.0 * PI * 6200.0 * t)).toFloat()
            }
            val a = hiss.clone()
            val b = hiss.clone()
            dspNoDeEss.processMonoFrameInPlace(a, a.size)
            dspDeEss.processMonoFrameInPlace(b, b.size)
            sibDryRms = sqrt(a.fold(0f) { acc, v -> acc + v * v } / a.size)
            sibTamedRms = sqrt(b.fold(0f) { acc, v -> acc + v * v } / b.size)
        }
        assertTrue("De-Esser must tame sharp 6.2 kHz Male sibilance ($sibTamedRms < 0.80x of $sibDryRms)", sibTamedRms < sibDryRms * 0.80f)
    }

    @Test
    fun micDspSmoothExpander_avoidsHardZeroMsSpeechOnsetClicks() {
        val dsp = MicDspProcessor(48000).apply {
            applySettings(
                StreamSettings(
                    voiceGenderProfile = "MALE",
                    voicePreset = "SOL_LUXE",
                    noiseSuppressMode = "OFF",
                    rnnoiseLevel = 0,
                    noiseGateEnabled = true,
                    micGainPercent = 100
                )
            )
        }
        // Let expander settle smoothly to idle -14 dB floor during quiet pause
        repeat(35) {
            val quiet = FloatArray(480) { 0.0004f }
            dsp.processMonoFrameInPlace(quiet, quiet.size)
        }
        // Sudden speech onset: 8 ms smooth attack guarantees no instantaneous full-scale step click
        val onset = FloatArray(480) { i ->
            val t = i.toDouble() / 48000.0
            (0.14 * sin(2.0 * PI * 220.0 * t)).toFloat()
        }
        dsp.processMonoFrameInPlace(onset, onset.size)
        val first10Peak = (0 until 10).maxOf { abs(onset[it]) }
        val last50Peak = (430 until 480).maxOf { abs(onset[it]) }
        assertTrue("8 ms smooth expander attack should ramp gradually (start $first10Peak < end $last50Peak)", first10Peak < last50Peak)
    }

    @Test
    fun micDspRawMode_reportsBypassedDenoiserSummaryAndZeroGainReduction() {
        val dsp = MicDspProcessor(48000)
        dsp.applySettings(
            StreamSettings(
                voicePreset = "RAW",
                noiseSuppressMode = "RNNOISE",
                rnnoiseLevel = 2,
                micGainPercent = 100
            )
        )
        assertEquals("Bypassed (RAW Mode)", dsp.activeDenoiserSummary)

        val frame = FloatArray(480) { i -> (0.20 * sin(2.0 * PI * 300.0 * i / 48000.0)).toFloat() }
        dsp.processMonoFrameInPlace(frame, frame.size)
        assertEquals(0f, dsp.lastGainReductionDb, 0.001f)
    }

    @Test
    fun audioWorkerBarrier_releasesSharedResourcesOnlyOnceAfterBothWorkersExit() {
        // Case 1: Deferred release when both workers outlive stopStreamingAudio() join
        val deferredBarrier = AudioWorkerBarrier()
        deferredBarrier.registerWorker() // Mixer
        deferredBarrier.registerWorker() // AAC Drain
        assertFalse(deferredBarrier.requestRelease())
        assertFalse(deferredBarrier.isReleased())
        // First worker exits while second is still active -> MUST NOT release yet
        assertFalse(deferredBarrier.onWorkerExited())
        assertFalse(deferredBarrier.isReleased())
        // Final worker exits -> releases exactly once
        assertTrue(deferredBarrier.onWorkerExited())
        assertTrue(deferredBarrier.isReleased())
        // Duplicate exit guard -> never double-releases
        assertFalse(deferredBarrier.onWorkerExited())

        // Case 2: Normal shutdown where both workers finish before requestRelease()
        val normalBarrier = AudioWorkerBarrier()
        normalBarrier.registerWorker()
        normalBarrier.registerWorker()
        assertFalse(normalBarrier.onWorkerExited())
        assertFalse(normalBarrier.onWorkerExited())
        assertTrue(normalBarrier.requestRelease())
        assertFalse(normalBarrier.requestRelease())
    }

    @Test
    fun linearMonoResampler48k_converts44100And16000Pcm16ToExact480FloatSamples() {
        for (srcRate in listOf(44100, 16000)) {
            val resampler = LinearMonoResampler48k(srcRate)
            val srcSamples = ShortArray(resampler.sourceSamplesPer10ms) { i ->
                val t = i.toDouble() / srcRate.toDouble()
                (0.5 * sin(2.0 * PI * 440.0 * t) * 32767.0).roundToInt().toShort()
            }
            val dest480 = FloatArray(480)
            resampler.resample10msPcm16To480Float(srcSamples, srcSamples.size, dest480)

            var sumSq = 0f
            for (s in dest480) {
                assertTrue("Resampled $srcRate Hz sample must be finite", s.isFinite())
                assertTrue("Resampled $srcRate Hz sample must stay in [-1, 1]", abs(s) <= 1.0f)
                sumSq += s * s
            }
            val rms = sqrt(sumSq / dest480.size)
            assertTrue("Resampled $srcRate Hz RMS ($rms) should match ~0.353f sine RMS", rms in 0.30f..0.40f)
        }
    }

    @Test
    fun adaptiveRtmpChunkSize_uses65535OnWifiMajorIngestsAnd4096OnCellularOrCustom() {
        assertEquals(65535, SolStreamService.resolveAdaptiveRtmpChunkSize(true, "rtmps://a.rtmps.youtube.com:443/live2/key"))
        assertEquals(65535, SolStreamService.resolveAdaptiveRtmpChunkSize(true, "rtmps://ingest.global-contribute.live-video.net:443/app/key"))
        assertEquals(4096, SolStreamService.resolveAdaptiveRtmpChunkSize(true, "rtmp://my-custom-nginx.example.com:1935/live/key"))
        assertEquals(4096, SolStreamService.resolveAdaptiveRtmpChunkSize(false, "rtmps://a.rtmps.youtube.com:443/live2/key"))
    }

    @Test
    fun unifiedOverlayLogoTag_formatsOffAndAllFivePositionsCleanly() {
        assertEquals("Logo:OFF", SolStreamService.formatCompactOverlayPositionTag(false, "FULL"))
        assertEquals("Logo:OFF", SolStreamService.formatCompactOverlayPositionTag(false, "TOP_LEFT"))
        assertEquals("Logo:FULL", SolStreamService.formatCompactOverlayPositionTag(true, "FULL"))
        assertEquals("Logo:TL", SolStreamService.formatCompactOverlayPositionTag(true, "TOP_LEFT"))
        assertEquals("Logo:TR", SolStreamService.formatCompactOverlayPositionTag(true, "TOP_RIGHT"))
        assertEquals("Logo:BL", SolStreamService.formatCompactOverlayPositionTag(true, "BOTTOM_LEFT"))
        assertEquals("Logo:BR", SolStreamService.formatCompactOverlayPositionTag(true, "BOTTOM_RIGHT"))
    }

    @Test
    fun smartZoomStepsAndPresetCoordinator_enforce108PercentDefaultAndSolLuxe30() {
        assertTrue(SolStreamService.SMART_ZOOM_STEPS.contains(108))
        assertFalse(SolStreamService.SMART_ZOOM_STEPS.contains(104))
        assertEquals(108, StreamSettings().smartZoomPercent)
        assertEquals("SOL LUXE 3.0 (Pure Condenser)", MicDspProcessor.presetDisplayName("SOL_LUXE"))
    }
}
