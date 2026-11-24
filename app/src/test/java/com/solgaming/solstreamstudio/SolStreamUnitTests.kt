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
import android.os.BatteryManager
import android.os.PowerManager
import com.solgaming.solstreamstudio.recorder.SolHudAndQrRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
            ytFlagship.copy(platformPreset = "TWITCH", hdr10Enabled = true),
            DeviceTier.FLAGSHIP,
            scan
        )
        assertEquals("H264", twitchFlagship.codec)
        assertEquals(1920, twitchFlagship.width)
        assertEquals(1080, twitchFlagship.height)
        assertEquals(6000, twitchFlagship.targetBitrateKbps)
        assertEquals(160, twitchFlagship.audioBitrateKbps)
        assertFalse(twitchFlagship.hdr10Enabled)

        val kickFlagship = KeystoreVault.applyPlatformAndVoiceAwareTierPreset(
            ytFlagship.copy(platformPreset = "KICK", hdr10Enabled = true),
            DeviceTier.FLAGSHIP,
            scan
        )
        assertEquals("H264", kickFlagship.codec)
        assertEquals(8000, kickFlagship.targetBitrateKbps)
        assertEquals(160, kickFlagship.audioBitrateKbps)
        assertFalse(kickFlagship.hdr10Enabled)
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

    // === PHASE 1 (v1.5.0 / v1.5.1 / v1.5.2) UNIT TESTS ===

    @Test
    fun diagnosticsReportFreeze_stopsResamplingAfterSessionFinalized() {
        assertEquals("v1.5.2", DiagnosticsLogger.ENGINE_VERSION)
        try {
            DiagnosticsLogger.resetSessionLifecycleForTests()
            // Before any session starts -> allowed to sample idle hardware health
            assertTrue(DiagnosticsLogger.shouldSampleHealthOnReport(isLive = false))

            // Active live session -> allowed to sample
            DiagnosticsLogger.sessionStartMs = 1_700_000_000_000L
            DiagnosticsLogger.isSessionFinalized = false
            assertTrue(DiagnosticsLogger.shouldSampleHealthOnReport(isLive = true))
            assertTrue(DiagnosticsLogger.shouldSampleHealthOnReport(isLive = false))

            // Finalized post-stop report -> MUST stay frozen on idle reopen
            DiagnosticsLogger.isSessionFinalized = true
            assertFalse(DiagnosticsLogger.shouldSampleHealthOnReport(isLive = false))
            // If a live session is active again, sampling is allowed
            assertTrue(DiagnosticsLogger.shouldSampleHealthOnReport(isLive = true))
        } finally {
            DiagnosticsLogger.resetSessionLifecycleForTests()
        }
    }

    @Test
    fun thermalManualReleaseGuard_blocksOverrideWhileAndroidIsStillSevere() {
        assertTrue(
            SolStreamService.shouldBlockManual30FpsRelease(
                requestingThrottleOff = true,
                autoThermalActive = true,
                currentThermalStatus = PowerManager.THERMAL_STATUS_SEVERE
            )
        )
        assertTrue(
            SolStreamService.shouldBlockManual30FpsRelease(
                requestingThrottleOff = true,
                autoThermalActive = true,
                currentThermalStatus = PowerManager.THERMAL_STATUS_CRITICAL
            )
        )
        // Once cooled to MODERATE or lower, release is allowed
        assertFalse(
            SolStreamService.shouldBlockManual30FpsRelease(
                requestingThrottleOff = true,
                autoThermalActive = true,
                currentThermalStatus = PowerManager.THERMAL_STATUS_MODERATE
            )
        )
        // Turning manual 30 FPS saver ON is never blocked
        assertFalse(
            SolStreamService.shouldBlockManual30FpsRelease(
                requestingThrottleOff = false,
                autoThermalActive = true,
                currentThermalStatus = PowerManager.THERMAL_STATUS_SEVERE
            )
        )
        // Releasing a purely manual saver when auto-thermal is NOT active is allowed
        assertFalse(
            SolStreamService.shouldBlockManual30FpsRelease(
                requestingThrottleOff = true,
                autoThermalActive = false,
                currentThermalStatus = PowerManager.THERMAL_STATUS_SEVERE
            )
        )
    }

    @Test
    fun studioProfiles_adaptAll4StarterSlotsToFlagshipAndLiteHardware() {
        val flagshipHevc = HardwareEncoderReport(
            codecKey = "HEVC",
            mimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            encoderName = "c2.exynos.hevc.encoder",
            supports1440p60 = true,
            supports1080p60 = true,
            supportsCbr = true,
            supportsVbr = true,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true,
            verifiedRealEncode = true
        )
        val flagshipScan = DeviceScanSummary(
            deviceModel = "samsung SM-S721B",
            socModel = "Samsung Exynos 2400e",
            androidRelease = "17",
            sdkInt = 37,
            totalRamGb = 8,
            hevcHardware = flagshipHevc,
            h264Hardware = flagshipHevc.copy(codecKey = "H264", mimeType = MediaFormat.MIMETYPE_VIDEO_AVC),
            av1Hardware = null,
            recommendedTier = DeviceTier.FLAGSHIP,
            recommendedCodec = "HEVC",
            recommendedWidth = 2560,
            recommendedHeight = 1440
        )

        val currentFlagship = StreamSettings(deviceTier = "FLAGSHIP")
        val p0 = KeystoreVault.buildDefaultStudioProfile(0, currentFlagship, flagshipScan)
        val p1 = KeystoreVault.buildDefaultStudioProfile(1, currentFlagship, flagshipScan)
        val p2 = KeystoreVault.buildDefaultStudioProfile(2, currentFlagship, flagshipScan)
        val p3 = KeystoreVault.buildDefaultStudioProfile(3, currentFlagship, flagshipScan)

        assertEquals("Wi-Fi Max", p0.name)
        assertEquals(2560, p0.settings.width)
        assertEquals(60, p0.settings.fps)
        assertEquals("HEVC", p0.settings.codec)

        assertEquals("Mobile Data", p1.name)
        assertEquals(1280, p1.settings.width)
        assertEquals(30, p1.settings.fps)
        assertEquals(4500, p1.settings.targetBitrateKbps)
        assertEquals(128, p1.settings.audioBitrateKbps)
        assertTrue(p1.settings.adaptiveBitrate)

        assertEquals("Shorts 9:16", p2.name)
        assertTrue(p2.settings.isVertical)
        assertTrue(p2.settings.useShortsKeySlot)
        assertEquals("BLUR", p2.settings.shortsBackdropMode)
        assertEquals(12000, p2.settings.targetBitrateKbps)

        assertEquals("Custom", p3.name)
        assertFalse(p3.isUserCustomized)

        // Lite budget phone (H.264 1080p30 max) must auto-clamp Wi-Fi Max and Shorts cleanly
        val liteH264 = HardwareEncoderReport(
            codecKey = "H264",
            mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            encoderName = "c2.mtk.avc.encoder",
            supports1440p60 = false,
            supports1080p60 = false,
            supports1080p30 = true,
            supportsCbr = true,
            supportsVbr = true,
            maxBitrateMbps = 12,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true,
            verifiedRealEncode = true
        )
        val liteScan = flagshipScan.copy(
            hevcHardware = null,
            h264Hardware = liteH264,
            recommendedTier = DeviceTier.LITE,
            recommendedCodec = "H264",
            recommendedWidth = 1920,
            recommendedHeight = 1080,
            recommendedFps = 30
        )
        val liteWifi = KeystoreVault.buildDefaultStudioProfile(0, StreamSettings(deviceTier = "LITE"), liteScan)
        val liteShorts = KeystoreVault.buildDefaultStudioProfile(2, StreamSettings(deviceTier = "LITE"), liteScan)
        assertEquals("H264", liteWifi.settings.codec)
        assertEquals(1920, liteWifi.settings.width)
        assertEquals(30, liteWifi.settings.fps)
        assertEquals(1280, liteShorts.settings.width)
        assertEquals(30, liteShorts.settings.fps)
    }

    @Test
    fun overlayCornerSanitizer_acceptsFourValidCornersAndRejectsInvalidInput() {
        assertEquals("TOP_LEFT", KeystoreVault.sanitizeOverlayCorner("top_left", "BOTTOM_RIGHT"))
        assertEquals("TOP_RIGHT", KeystoreVault.sanitizeOverlayCorner(" TOP_RIGHT ", "TOP_LEFT"))
        assertEquals("BOTTOM_LEFT", KeystoreVault.sanitizeOverlayCorner("BOTTOM_LEFT", "TOP_LEFT"))
        assertEquals("BOTTOM_RIGHT", KeystoreVault.sanitizeOverlayCorner("bottom_right", "TOP_LEFT"))
        assertEquals("TOP_LEFT", KeystoreVault.sanitizeOverlayCorner("CENTER", "TOP_LEFT"))
        assertEquals("BOTTOM_RIGHT", KeystoreVault.sanitizeOverlayCorner("", "BOTTOM_RIGHT"))
        assertEquals("BOTTOM_LEFT", KeystoreVault.sanitizeOverlayCorner(null, "BOTTOM_LEFT"))
    }

    @Test
    fun qrEncoder_generatesValidIsoMatricesForYoutubeUpiAndMax213Bytes() {
        assertNull(SolHudAndQrRenderer.encodeQrMatrix(""))
        assertNull(SolHudAndQrRenderer.encodeQrMatrix("   "))
        assertNull(SolHudAndQrRenderer.encodeQrMatrix("A".repeat(214)))
        // 72 Devanagari chars = 216 UTF-8 bytes (> 213 byte Version 10-M capacity) -> rejected cleanly
        assertNull(SolHudAndQrRenderer.encodeQrMatrix("ॐ".repeat(72)))

        val ytQr = SolHudAndQrRenderer.encodeQrMatrix("https://youtube.com/@Sol_CODM")
        assertNotNull(ytQr)
        assertEquals(3, ytQr!!.version)
        assertEquals(29, ytQr.size)
        // Verify top-left 7x7 finder pattern + separator + fixed dark module
        assertTrue(ytQr.modules[0][0])
        assertTrue(ytQr.modules[3][3])
        assertFalse(ytQr.modules[1][1])
        assertFalse(ytQr.modules[0][7])
        assertTrue(ytQr.modules[ytQr.size - 8][8])

        val upiQr = SolHudAndQrRenderer.encodeQrMatrix(
            "upi://pay?pa=solgaming@okaxis&pn=Sol%20Gaming%20CODM&cu=INR&tn=Stream%20Support"
        )
        assertNotNull(upiQr)
        assertEquals(5, upiQr!!.version)
        assertEquals(37, upiQr.size)
        assertTrue(upiQr.modules[upiQr.size - 8][8])

        // Boundary test: exact 213-byte Version 10 Level M payload (exercises 16-bit length + BCH version blocks)
        val maxQr = SolHudAndQrRenderer.encodeQrMatrix("A".repeat(213))
        assertNotNull(maxQr)
        assertEquals(10, maxQr!!.version)
        assertEquals(57, maxQr.size)
        assertTrue(maxQr.modules[maxQr.size - 8][8])
    }

    @Test
    fun cornerQuadMvpMatrix_staysStrictlyInsideViewportAcross16x9And9x16() {
        val mvp = FloatArray(16)
        val targets = listOf(2560 to 1440, 1080 to 1920)
        for ((dstW, dstH) in targets) {
            for (corner in SolStreamService.CORNER_POSITION_ORDER) {
                SolHudAndQrRenderer.computeCornerQuadMvp(
                    mvp = mvp,
                    bmpW = SolHudAndQrRenderer.HUD_WIDTH_PX,
                    bmpH = SolHudAndQrRenderer.HUD_HEIGHT_PX,
                    dstW = dstW,
                    dstH = dstH,
                    corner = corner,
                    maxHeightFrac = 0.062f,
                    maxWidthFrac = 0.30f,
                    extraOffsetY = 0.032f
                )
                val sx = mvp[0]
                val sy = mvp[5]
                val tx = mvp[12]
                val ty = mvp[13]
                assertEquals(1.0f, mvp[10], 1e-6f)
                assertEquals(1.0f, mvp[15], 1e-6f)
                assertTrue("scaleX must be positive", sx > 0f)
                assertTrue("scaleY must be positive", sy > 0f)
                assertTrue("left edge must stay >= -1.0", tx - sx >= -1.0f)
                assertTrue("right edge must stay <= 1.0", tx + sx <= 1.0f)
                assertTrue("bottom edge must stay >= -1.0", ty - sy >= -1.0f)
                assertTrue("top edge must stay <= 1.0", ty + sy <= 1.0f)

                when (corner) {
                    "TOP_LEFT" -> {
                        assertTrue(tx < 0f)
                        assertTrue(ty > 0f)
                    }
                    "TOP_RIGHT" -> {
                        assertTrue(tx > 0f)
                        assertTrue(ty > 0f)
                    }
                    "BOTTOM_LEFT" -> {
                        assertTrue(tx < 0f)
                        assertTrue(ty < 0f)
                    }
                    "BOTTOM_RIGHT" -> {
                        assertTrue(tx > 0f)
                        assertTrue(ty < 0f)
                    }
                }
            }
        }
    }

    @Test
    fun solHudFormatter_rendersNormalThrottledAndMissingTempCleanly() {
        assertEquals(
            "60 FPS  •  39.4°C  •  20.0M",
            SolHudAndQrRenderer.formatHudTelemetryLine(60, 39.4f, 20.0f, isThrottled30 = false)
        )
        assertEquals(
            "30 FPS COOL  •  43.8°C  •  12.0M",
            SolHudAndQrRenderer.formatHudTelemetryLine(30, 43.8f, 12.0f, isThrottled30 = true)
        )
        assertEquals(
            "60 FPS  •  --.-°C  •  15.5M",
            SolHudAndQrRenderer.formatHudTelemetryLine(60, 0f, 15.5f, isThrottled30 = false)
        )
    }

    @Test
    fun screenshotDimensionClamp_capsAt1080pEvenDimensionsWithoutUpscaling() {
        assertEquals(1920 to 1080, SolVideoPipeline.computeScreenshotDimensions(3840, 2160))
        assertEquals(1920 to 1080, SolVideoPipeline.computeScreenshotDimensions(2560, 1440))
        assertEquals(1920 to 1080, SolVideoPipeline.computeScreenshotDimensions(1920, 1080))
        assertEquals(1080 to 1920, SolVideoPipeline.computeScreenshotDimensions(1440, 2560))
        assertEquals(1080 to 1920, SolVideoPipeline.computeScreenshotDimensions(1080, 1920))
        assertEquals(1280 to 720, SolVideoPipeline.computeScreenshotDimensions(1280, 720))

        val (oddW, oddH) = SolVideoPipeline.computeScreenshotDimensions(2559, 1439)
        assertEquals(0, oddW % 2)
        assertEquals(0, oddH % 2)
        assertTrue(oddW * oddH * 4 <= 1920 * 1080 * 4)
    }

    @Test
    fun screenshotVerticalRowFlip_invertsRgbaRowsInPlaceAccurately() {
        // 2x3 RGBA image (rowStride = 8 bytes)
        val pixels = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8,         // Row 0 (bottom in OpenGL)
            11, 12, 13, 14, 15, 16, 17, 18, // Row 1 (middle)
            21, 22, 23, 24, 25, 26, 27, 28  // Row 2 (top in OpenGL)
        )
        SolHudAndQrRenderer.flipRgbaRowsVerticallyInPlace(pixels, width = 2, height = 3)
        assertEquals(21.toByte(), pixels[0])
        assertEquals(28.toByte(), pixels[7])
        assertEquals(11.toByte(), pixels[8])
        assertEquals(18.toByte(), pixels[15])
        assertEquals(1.toByte(), pixels[16])
        assertEquals(8.toByte(), pixels[23])
    }

    @Test
    fun activeOverlaysSummaryAndCornerTags_formatCleanReportStrings() {
        assertEquals("TL", SolStreamService.formatCompactCornerShortTag("TOP_LEFT"))
        assertEquals("TR", SolStreamService.formatCompactCornerShortTag("TOP_RIGHT"))
        assertEquals("BL", SolStreamService.formatCompactCornerShortTag("BOTTOM_LEFT"))
        assertEquals("BR", SolStreamService.formatCompactCornerShortTag("BOTTOM_RIGHT"))

        assertEquals("None", SolStreamService.buildActiveOverlaysSummary(StreamSettings()))

        val allPro = StreamSettings(
            hudTelemetryEnabled = true,
            hudTelemetryCorner = "TOP_LEFT",
            qrOverlayEnabled = true,
            qrOverlayText = "https://youtube.com/@Sol_CODM",
            qrOverlayCorner = "BOTTOM_RIGHT",
            pngTuberEnabled = true,
            pngTuberCorner = "BOTTOM_LEFT"
        )
        val summary = SolStreamService.buildActiveOverlaysSummary(allPro)
        assertTrue(summary.contains("SolHUD (TL)"))
        assertTrue(summary.contains("QR (BR)"))
        assertTrue(summary.contains("PNGTuber (BL)"))

        // QR with blank text must not appear as active in summary
        val blankQr = StreamSettings(qrOverlayEnabled = true, qrOverlayText = "   ")
        assertEquals("None", SolStreamService.buildActiveOverlaysSummary(blankQr))
    }

    @Test
    fun pngTuberAssetSlots_enforce512PxMaxFor2MbTotalVramBudget() {
        assertEquals(512, SolSceneAssetStore.AssetSlot.PNGTUBER_IDLE.maxDimensionPx)
        assertEquals(512, SolSceneAssetStore.AssetSlot.PNGTUBER_TALK.maxDimensionPx)
        val totalVramBytes = 2L * 512L * 512L * 4L
        assertEquals(2_097_152L, totalVramBytes)
    }

    @Test
    fun flagshipTierFallback_stepsTo30FpsWhenHardwareLacks60FpsSupport() {
        val hevc30Only = HardwareEncoderReport(
            codecKey = "HEVC",
            mimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            encoderName = "c2.vendor.hevc30.encoder",
            supports1440p60 = false,
            supports1440p30 = false,
            supports1080p60 = false,
            supports1080p30 = true,
            supportsCbr = true,
            supportsVbr = true,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true,
            verifiedRealEncode = true
        )
        val scan30Only = DeviceScanSummary(
            deviceModel = "30fps Test Device",
            socModel = "Test SoC",
            androidRelease = "15",
            sdkInt = 35,
            totalRamGb = 8,
            hevcHardware = hevc30Only,
            h264Hardware = null,
            av1Hardware = null,
            recommendedTier = DeviceTier.FLAGSHIP,
            recommendedCodec = "HEVC",
            recommendedWidth = 1920,
            recommendedHeight = 1080,
            recommendedFps = 30
        )
        val applied = KeystoreVault.applyPlatformAndVoiceAwareTierPreset(
            current = StreamSettings(platformPreset = "YOUTUBE"),
            tier = DeviceTier.FLAGSHIP,
            scan = scan30Only
        )
        assertEquals(1920, applied.width)
        assertEquals(1080, applied.height)
        assertEquals(30, applied.fps)
    }

    // === v1.5.1 / v1.5.2 PLAY STORE HARDENING UNIT TESTS ===

    @Test
    fun powerSourceClassifier_detectsBypassChargingAndAvoidsFalsePositives() {
        // Unplugged -> Battery
        assertEquals(
            "Battery",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = 0,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                battPct = 75
            )
        )

        // Samsung Pause USB PD reporting NOT_CHARGING while on AC
        assertEquals(
            "Bypass Charging (USB-PD)",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_AC,
                status = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                battPct = 68,
                currentAvgMicroAmps = 0,
                currentNowMicroAmps = 10_000
            )
        )

        // Samsung Pause USB PD reporting CHARGING with small transient buffer current (-120 mA)
        assertEquals(
            "Bypass Charging (USB-PD)",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_AC,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                battPct = 72,
                currentAvgMicroAmps = -120_000,
                currentNowMicroAmps = -140_000
            )
        )

        // Falls back to CURRENT_NOW when CURRENT_AVERAGE is 0 (unsupported OEM)
        assertEquals(
            "Bypass Charging (USB-PD)",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_AC,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                battPct = 60,
                currentAvgMicroAmps = 0,
                currentNowMicroAmps = 95_000
            )
        )

        // Real AC Fast Charging (2.4 A) -> AC Fast Power
        assertEquals(
            "AC Fast Power",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_AC,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                battPct = 60,
                currentAvgMicroAmps = 2_400_000,
                currentNowMicroAmps = 2_350_000
            )
        )

        // Trickle charge near full (98%) or 100% NOT_CHARGING must NOT falsely report Bypass Charging
        assertEquals(
            "AC Fast Power",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_AC,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                battPct = 98,
                currentAvgMicroAmps = 140_000,
                currentNowMicroAmps = 140_000
            )
        )
        assertEquals(
            "AC Fast Power",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_AC,
                status = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                battPct = 100,
                currentAvgMicroAmps = 0,
                currentNowMicroAmps = 0
            )
        )

        // USB port reporting CHARGING, NOT_CHARGING, or DISCHARGING must NEVER report Bypass Charging (USB-PD)
        assertEquals(
            "USB Power",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_USB,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                battPct = 55,
                currentAvgMicroAmps = 180_000,
                currentNowMicroAmps = 180_000
            )
        )
        assertEquals(
            "USB Power",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_USB,
                status = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                battPct = 60,
                currentAvgMicroAmps = 0,
                currentNowMicroAmps = 0
            )
        )
        assertEquals(
            "USB Power",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_USB,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                battPct = 60,
                currentAvgMicroAmps = -150_000,
                currentNowMicroAmps = -150_000
            )
        )

        // Wireless pad reporting NOT_CHARGING or DISCHARGING must NEVER report Bypass Charging (USB-PD)
        assertEquals(
            "Wireless Power",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_WIRELESS,
                status = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                battPct = 65,
                currentAvgMicroAmps = 0,
                currentNowMicroAmps = 0
            )
        )
        assertEquals(
            "Wireless Power",
            CodecCapabilityProbe.resolvePowerSourceLabel(
                plugged = BatteryManager.BATTERY_PLUGGED_WIRELESS,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                battPct = 65,
                currentAvgMicroAmps = -200_000,
                currentNowMicroAmps = -200_000
            )
        )
    }

    @Test
    fun captureResizeGuard_blocksHiddenResizesAcrossBoth16x9And9x16() {
        // Hidden target (!isVisible) is blocked in BOTH 9:16 vertical and 16:9 landscape
        assertFalse(
            SolVideoPipeline.shouldAcceptCaptureResize(
                isVerticalPrimary = true,
                reqW = 1080,
                reqH = 2340,
                isVisible = false,
                currentCapW = 2340,
                currentCapH = 1080
            )
        )
        assertFalse(
            SolVideoPipeline.shouldAcceptCaptureResize(
                isVerticalPrimary = false,
                reqW = 2340,
                reqH = 1080,
                isVisible = false,
                currentCapW = 1920,
                currentCapH = 1080
            )
        )

        // Visible 9:16 vertical stream allows initial portrait -> landscape game transition
        assertTrue(
            SolVideoPipeline.shouldAcceptCaptureResize(
                isVerticalPrimary = true,
                reqW = 2340,
                reqH = 1080,
                isVisible = true,
                currentCapW = 1080,
                currentCapH = 2340
            )
        )

        // Visible 16:9 landscape stream blocks temporary portrait resizes
        assertFalse(
            SolVideoPipeline.shouldAcceptCaptureResize(
                isVerticalPrimary = false,
                reqW = 1080,
                reqH = 2340,
                isVisible = true,
                currentCapW = 2340,
                currentCapH = 1080
            )
        )

        // Redundant identical size and tiny invalid dimensions are blocked
        assertFalse(
            SolVideoPipeline.shouldAcceptCaptureResize(
                isVerticalPrimary = true,
                reqW = 2340,
                reqH = 1080,
                isVisible = true,
                currentCapW = 2340,
                currentCapH = 1080
            )
        )
        assertFalse(
            SolVideoPipeline.shouldAcceptCaptureResize(
                isVerticalPrimary = true,
                reqW = 64,
                reqH = 64,
                isVisible = true,
                currentCapW = 1080,
                currentCapH = 2340
            )
        )
    }

    @Test
    fun benchmarkSectionHeader_omitsCompetitorLabelWhenNoCompetitorSaved() {
        assertEquals(
            "--- PREVIOUS SOLSTREAM SESSION BENCHMARK ---",
            DiagnosticsLogger.resolveBenchmarkSectionHeader(hasCompetitorBenchmark = false)
        )
        assertEquals(
            "--- COMPETITOR vs. SOLSTREAM BENCHMARK COMPARISON ---",
            DiagnosticsLogger.resolveBenchmarkSectionHeader(hasCompetitorBenchmark = true)
        )
    }

    @Test
    fun shortsBackdropButtonLabel_matchesSelectedModeAccurately() {
        val darkLabel = MainActivity.formatShortsBackdropButtonLabel("DARK")
        assertTrue(darkLabel.contains("DARK"))
        assertTrue(darkLabel.contains("0% Extra GPU"))
        assertFalse(darkLabel.contains("Blur", ignoreCase = true))

        val posterLabel = MainActivity.formatShortsBackdropButtonLabel("POSTER")
        assertTrue(posterLabel.contains("POSTER"))
        assertTrue(posterLabel.contains("Static Image"))
        assertFalse(posterLabel.contains("Blur", ignoreCase = true))

        val blurLabel = MainActivity.formatShortsBackdropButtonLabel("BLUR")
        assertTrue(blurLabel.contains("BLUR"))
        assertTrue(blurLabel.contains("40% Cooler Scissor Blur"))
    }

    @Test
    fun platformDestinationEnforcer_clampsTwitchKickSimulcastAndLocksBFramesZero() {
        // Twitch primary must enforce H264, <=1080p, <=6000 kbps, CBR, <=160k AAC, 2s keyframe, HDR10 OFF, B-frames 0
        val badTwitch = StreamSettings(
            platformPreset = "TWITCH",
            codec = "HEVC",
            width = 2560,
            height = 1440,
            targetBitrateKbps = 20000,
            audioBitrateKbps = 320,
            useVbr = true,
            hdr10Enabled = true,
            keyframeIntervalSec = 1,
            maxBFrames = 2
        )
        val (fixedTwitch, changedTwitch) = KeystoreVault.enforcePlatformLimits(badTwitch)
        assertTrue(changedTwitch)
        assertEquals("H264", fixedTwitch.codec)
        assertEquals(1920, fixedTwitch.width)
        assertEquals(1080, fixedTwitch.height)
        assertEquals(6000, fixedTwitch.targetBitrateKbps)
        assertEquals(160, fixedTwitch.audioBitrateKbps)
        assertFalse(fixedTwitch.useVbr)
        assertFalse(fixedTwitch.hdr10Enabled)
        assertEquals(2, fixedTwitch.keyframeIntervalSec)
        assertEquals(0, fixedTwitch.maxBFrames)

        // Kick primary must enforce H264, <=1080p, <=8000 kbps, CBR, <=160k AAC, 2s keyframe, B-frames 0
        val badKick = badTwitch.copy(platformPreset = "KICK", width = 3840, height = 2160, targetBitrateKbps = 30000)
        val (fixedKick, changedKick) = KeystoreVault.enforcePlatformLimits(badKick)
        assertTrue(changedKick)
        assertEquals("H264", fixedKick.codec)
        assertEquals(1920, fixedKick.width)
        assertEquals(1080, fixedKick.height)
        assertEquals(8000, fixedKick.targetBitrateKbps)
        assertEquals(160, fixedKick.audioBitrateKbps)
        assertFalse(fixedKick.useVbr)
        assertEquals(0, fixedKick.maxBFrames)

        // Single-encode YouTube + Twitch simulcast enforces stricter 6M H264 limits
        val badSingleSim = StreamSettings(
            platformPreset = "YOUTUBE",
            simulcastTwitchEnabled = true,
            simulcastSplitEncode = false,
            codec = "HEVC",
            width = 2560,
            height = 1440,
            targetBitrateKbps = 18000,
            audioBitrateKbps = 256
        )
        val (fixedSingleSim, _) = KeystoreVault.enforcePlatformLimits(badSingleSim)
        assertEquals("H264", fixedSingleSim.codec)
        assertEquals(1920, fixedSingleSim.width)
        assertEquals(6000, fixedSingleSim.targetBitrateKbps)
        assertEquals(160, fixedSingleSim.audioBitrateKbps)

        // Split-encode YouTube + Twitch keeps YouTube primary at 1440p HEVC 20M while locking B-frames to 0
        val splitSim = badSingleSim.copy(simulcastSplitEncode = true, maxBFrames = 2)
        val (fixedSplitSim, _) = KeystoreVault.enforcePlatformLimits(splitSim)
        assertEquals("HEVC", fixedSplitSim.codec)
        assertEquals(2560, fixedSplitSim.width)
        assertEquals(18000, fixedSplitSim.targetBitrateKbps)
        assertEquals(0, fixedSplitSim.maxBFrames)
    }

    @Test
    fun crossPhoneClamping_handles720pOnlyAnd720p30OnlyBudgetEncoders() {
        val enc720p30Only = HardwareEncoderReport(
            codecKey = "H264",
            mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            encoderName = "c2.budget.avc.encoder",
            supports1440p60 = false,
            supports1440p30 = false,
            supports1080p60 = false,
            supports1080p30 = false,
            supports720p60 = false,
            supports720p30 = true,
            supportsCbr = true,
            supportsVbr = true,
            maxBitrateMbps = 8,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true,
            verifiedRealEncode = true
        )
        assertEquals("720p30", enc720p30Only.topResolutionTag())
        assertEquals("1080p30", enc720p30Only.copy(supports1080p30 = true).topResolutionTag())

        val scan720 = DeviceScanSummary(
            deviceModel = "Entry Phone",
            socModel = "Entry SoC",
            androidRelease = "13",
            sdkInt = 33,
            totalRamGb = 4,
            hevcHardware = null,
            h264Hardware = enc720p30Only,
            av1Hardware = null,
            recommendedTier = DeviceTier.LITE,
            recommendedCodec = "H264",
            recommendedWidth = 1280,
            recommendedHeight = 720,
            recommendedFps = 30
        )

        val imported1080p30 = StreamSettings(codec = "H264", width = 1920, height = 1080, fps = 30, targetBitrateKbps = 6000)
        val (clamped1, changed1) = KeystoreVault.clampImportedSettingsToHardware(imported1080p30, scan720)
        assertTrue(changed1)
        assertEquals(1280, clamped1.width)
        assertEquals(720, clamped1.height)
        assertEquals(30, clamped1.fps)

        val imported720p60 = StreamSettings(codec = "H264", width = 1280, height = 720, fps = 60, targetBitrateKbps = 5000)
        val (clamped2, changed2) = KeystoreVault.clampImportedSettingsToHardware(imported720p60, scan720)
        assertTrue(changed2)
        assertEquals(1280, clamped2.width)
        assertEquals(720, clamped2.height)
        assertEquals(30, clamped2.fps)
    }

    @Test
    fun videoDrainTransientRetryGuard_retriesBoundedTimesThenEscalates() {
        assertTrue(SolVideoPipeline.shouldRetryTransientCodecException(true, true, 0))
        assertTrue(SolVideoPipeline.shouldRetryTransientCodecException(true, true, SolVideoPipeline.MAX_TRANSIENT_CODEC_RETRIES - 1))
        assertFalse(SolVideoPipeline.shouldRetryTransientCodecException(true, true, SolVideoPipeline.MAX_TRANSIENT_CODEC_RETRIES))
        assertFalse(SolVideoPipeline.shouldRetryTransientCodecException(false, true, 0))
        assertFalse(SolVideoPipeline.shouldRetryTransientCodecException(true, false, 0))
    }

    @Test
    fun fourKFallbackLadder_stepsThrough4k30And1440p60Before1440p30() {
        val ultraEnc = HardwareEncoderReport(
            codecKey = "HEVC",
            mimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
            encoderName = "c2.ultra.hevc.encoder",
            supports4k60 = true,
            supports4k30 = true,
            supports1440p60 = true,
            supports1440p30 = true,
            supports1080p60 = true,
            supports1080p30 = true,
            supportsCbr = true,
            supportsVbr = true,
            complexityMin = 0,
            complexityMax = 0,
            verifiedSurfaceConfigure = true,
            verifiedRealEncode = true
        )
        val ladder = SolVideoPipeline.buildResolutionFallbackLadder(ultraEnc, 3840, 2160, 60)
        assertEquals(Triple(3840, 2160, 60), ladder[0])
        assertEquals(Triple(3840, 2160, 30), ladder[1])
        assertEquals(Triple(2560, 1440, 60), ladder[2])
        assertEquals(Triple(2560, 1440, 30), ladder[3])
    }

    @Test
    fun verifiedSocThermalZoneFilter_acceptsCpuGpuZonesAndRejectsBatterySkin() {
        assertTrue(DiagnosticsLogger.isVerifiedSocThermalZoneType("cpu0-thermal"))
        assertTrue(DiagnosticsLogger.isVerifiedSocThermalZoneType("soc_therm"))
        assertTrue(DiagnosticsLogger.isVerifiedSocThermalZoneType("G3D"))
        assertTrue(DiagnosticsLogger.isVerifiedSocThermalZoneType("BIG"))
        assertFalse(DiagnosticsLogger.isVerifiedSocThermalZoneType("battery"))
        assertFalse(DiagnosticsLogger.isVerifiedSocThermalZoneType("usb_port"))
        assertFalse(DiagnosticsLogger.isVerifiedSocThermalZoneType("skin-therm"))
        assertFalse(DiagnosticsLogger.isVerifiedSocThermalZoneType("quiet-therm"))
        assertFalse(DiagnosticsLogger.isVerifiedSocThermalZoneType(""))
    }

    @Test
    fun idleHardwareHealthBanner_usesThermalPressureLabelAndHandlesMissingTemp() {
        val missingTemp = LiveHardwareHealth(
            batteryTempC = 0f,
            thermalStatus = PowerManager.THERMAL_STATUS_NONE,
            thermalHeadroomRatio = 0.42f,
            socThermalLoadPercent = 42,
            availRamGb = 3.5f,
            totalRamGb = 8,
            isPluggedIn = true,
            powerSourceLabel = "Bypass Charging (USB-PD)"
        )
        val banner = missingTemp.toIdleStatusBanner()
        assertTrue(banner.contains("Idle Temp: N/A"))
        assertTrue(banner.contains("Thermal Pressure: 42%"))
        assertFalse(banner.contains("SoC Load"))
        assertTrue(banner.contains("⚡ Bypass Charging (USB-PD)"))
        assertFalse(banner.contains("⚡ ⚡"))
    }
}
