/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.max
import kotlin.math.min

data class StreamSettings(
    // === Core Tier & Platform ===
    val deviceTier: String = "FLAGSHIP",         // "LITE", "BALANCED", "HIGH", "FLAGSHIP", "ULTRA"
    val platformPreset: String = "YOUTUBE",       // "YOUTUBE", "TWITCH", "KICK", "CUSTOM"
    val useShortsKeySlot: Boolean = false,        // Auto-uses Slot #2 Variable Key when 9:16 Shorts is active
    val simulcastTwitchEnabled: Boolean = false,  // Multi-destination packet fan-out
    val simulcastKickEnabled: Boolean = false,
    val dualFormatCompanionEnabled: Boolean = false, // Opt-in Flagship/Ultra 16:9 + 9:16 companion stream
    val dualFormatBitrateKbps: Int = 8000,        // 2,000..20,000 kbps (2..20 Mbps)

    // === Video Encoder, Bitrate (0.5–100 Mbps) & Framing ===
    val codec: String = "HEVC",                   // "HEVC", "H264", "AV1"
    val width: Int = 2560,                        // 640(360p), 854(480p), 1280(720p), 1920, 2560, 3840(4K)
    val height: Int = 1440,
    val fps: Int = 60,                            // 30 or 60
    val isVertical: Boolean = false,              // false = 16:9 Landscape, true = 9:16 Shorts
    val landscapeFlip: Int = 0,                   // 0 or 180 (USB-C Bypass cable comfort)
    val targetBitrateKbps: Int = 20000,           // 500 kbps (0.5M) .. 100,000 kbps (100M)
    val useVbr: Boolean = false,                  // false = CBR (Broadcast Standard), true = VBR
    val adaptiveBitrate: Boolean = true,          // Queue-aware Ping Guard ABR 2.0
    val framingMode: String = "FIT",              // "FIT", "STRETCH", "SMART", "FILL"
    val smartZoomPercent: Int = 108,              // 100..250% (10-step landscape & 9:16 center zoom)

    // === OpenGL ES 3.0/2.0 Visual Engine, Stackable Filters & Scenes ===
    val glEngineMode: String = "AUTO",            // "AUTO", "ES30", "ES20"
    val upscalerMode: String = "BICUBIC",         // "BILINEAR", "BICUBIC", "LANCZOS"
    val hdr10Enabled: Boolean = false,            // True 10-Bit HLG10 HDR (when HW Main10 supported)
    val cinematicFilmic: Boolean = false,         // Stackable SDR ACES Filmic S-Curve (with Shadow Toe Guard)
    val amoledVivid: Boolean = false,             // Stackable SDR Mid-Tone Saturation Pop (Skin-Tone Safe)
    val shadowBoost: Boolean = false,             // Stackable Adaptive Dark-Corner Enemy Clarifier
    val colorTempFilter: String = "NEUTRAL",      // "NEUTRAL", "WARM", "COOL"
    val gpuSharpen: Boolean = false,              // Stackable Contrast-Adaptive Crisp Sharpen
    val roiMode: String = "AUTO",                 // "OFF", "AUTO", "CENTER", "HUD"
    val keyframeIntervalSec: Int = 2,             // 1 or 2 seconds (locked to 2s on Twitch/Kick)
    val maxBFrames: Int = 0,                      // Locked to 0 (RootEncoder 2.8.1 RTMP packetizer writes CTS=0)
    val qpMaxCap: Int = 0,                        // 0 = Auto (14..51), 32, 36, 38, 42
    val lowLatencyCodecHint: Boolean = true,
    val shortsBackdropMode: String = "BLUR",      // "DARK", "BLUR", "POSTER"
    val overlayImageEnabled: Boolean = false,     // Zero-permission cached .PNG stream frame/logo
    val liveBannerEnabled: Boolean = false,       // Built-in GPU text banner
    val liveBannerText: String = "",
    val liveBannerPosition: String = "TOP",       // "TOP" or "BOTTOM"
    val minimapShieldEnabled: Boolean = false,    // Stream-sniper / Privacy zone blur
    val minimapShieldCorner: String = "TOP_RIGHT",// "TOP_RIGHT", "TOP_RIGHT_SMALL", "TOP_RIGHT_LARGE", "TOP_LEFT", "BOTTOM_LEFT", "BOTTOM_RIGHT", "CENTER_BOX", "KEYBOARD"

    // === 6-Stage Isolated 32-Bit Float / 64-Bit Double SolLuxe™ 3.0 Pure Voice + Stereo Game Soundstage ===
    val micDeviceId: Int = -1,
    val micAudioSource: Int = 0,                  // 0 = AUTO, 1 = MIC, 6 = VOICE_RECOGNITION, 7 = VOICE_COMM
    val audioBitrateKbps: Int = 256,              // 64, 96, 128, 160, 192, 256, 320 kbps
    val voiceGenderProfile: String = "NEUTRAL",   // Pick-1: "NEUTRAL" (68Hz), "MALE" (55Hz), "FEMALE" (85Hz)
    val voicePreset: String = "SOL_LUXE",         // "RAW", "CLEAN", "NEURAL", "STUDIO", "VELVET", "SOL_LUXE"
    val noiseSuppressMode: String = "RNNOISE",    // Pick-1: "OFF", "LIGHT", "RNNOISE", "HW_NS"
    val rnnoiseLevel: Int = 2,                    // Strength: 0 = Off, 1 = Gentle (65%), 2 = Natural (88%), 3 = Max (100%)
    val hpfCutoffHz: Int = 68,                    // Auto (55M/68N/85F) or Manual: 0, 50, 55, 60, 65, 68, 70, 75, 85, 95, 105, 120 Hz
    val hpfAutoMode: Boolean = true,              // true = tracks profile/hardware default; false = preserves explicit manual HPF across preset switches
    val transientTamerEnabled: Boolean = true,    // Stage 2: Raised-Cosine Micro-Impulse Clamper (<0.6ms zap/pop guard)
    val noiseGateEnabled: Boolean = true,         // Stage 3: Hybrid Neural-VAD Smooth Soft-Floor Expander (8ms attack / 220ms release)
    val voiceEqEnabled: Boolean = true,           // Stage 5: 4-Gear × 3-Gender Condenser EQ (Chest, Mud, Anti-Nasal, Silk Presence, Air Shelf)
    val deEsserEnabled: Boolean = true,           // Stage 6: Post-EQ/Post-Comp Dynamic Sibilance-Ratio De-Esser (6.2/6.7/7.2 kHz)
    val velvetWarmthEnabled: Boolean = true,      // Stage 5: Low-Band Analog Warmth Shaper (clean sub-warmth; zero Neutral/Female 2f mud)
    val compressorEnabled: Boolean = true,        // Stage 6: Gender-Sidechain Soft-Knee Broadcast Leveler (10ms/130ms, 2.5:1)
    val whisperLiftEnabled: Boolean = true,       // Stage 6: Continuous VAD-Synced Quiet-Speech Upward Lift (0.25..0.55 ramp × expander gain)
    val slowAgcEnabled: Boolean = true,           // Stage 6: Gentle Speech-Synced Broadcast Loudness Stabilization
    val speakerAecEnabled: Boolean = false,       // Default OFF (Strictly disabled on earphones via effectiveSpeakerAec; double-talk safe on speakerphone)
    val micGainPercent: Int = 130,                // Stage 6 Output: True Post-DSP Mic Boost (50..250%)
    val gameGainPercent: Int = 100,               // 0..250% (Protected by 4x Cubic Look-Ahead Limiter)
    val gameCinemaWideEnabled: Boolean = false,   // Stackable Game Bus: Loudness-normalized Mid-Side 3D widener
    val gameSubPunchEnabled: Boolean = false,     // Stackable Game Bus: +3.0 dB @ 62 Hz theater sub-punch (Phase-safe voice duck)
    val gameFootstepFocusEnabled: Boolean = false,// Stackable Game Bus: Dual-Band 250Hz Thump + 2450Hz Crunch + Sonar Lift
    val gameBlastTamerEnabled: Boolean = false,   // Stackable Game Bus: Smooth 35ms release explosion spike tamer
    val vadDuckingPercent: Int = 0,               // 0, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70%
    val micSyncDelayMs: Int = 0,                  // 0, 15, 30, 45, 60, 80, 100, 120, 150, 180, 200 ms

    // === Survival, Privacy & v1.4.0 Extensions ===
    val brbMuteMicToo: Boolean = true,
    val wifiLowLatencyLock: Boolean = true,
    val overlayPosition: String = "FULL",         // "FULL", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"
    val autoBlurKeyboard: Boolean = true,         // Automatically blurs bottom half of stream when IME keyboard opens
    val simulcastSplitEncode: Boolean = false,    // Mode 2: Uses secondary HW encoder for 1080p H.264 simulcast on Flagship/Ultra

    // === Phase 1 (v1.5.0+) Pro Broadcast Overlays (Strictly Opt-In, 0 MB VRAM When OFF) ===
    val hudTelemetryEnabled: Boolean = false,     // SolHUD™ Live GPU FPS • °C • Mbps badge (visible in Single-App Capture)
    val hudTelemetryCorner: String = "TOP_LEFT",  // "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"
    val qrOverlayEnabled: Boolean = false,        // Pure-Kotlin offline Corner QR (UPI / YouTube / Discord)
    val qrOverlayText: String = "",               // Max 200 chars (QR Version 1..10 Level M safe)
    val qrOverlayCorner: String = "BOTTOM_RIGHT", // "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"
    val pngTuberEnabled: Boolean = false,         // Zero-Camera 2-PNG voice-reactive avatar (uses existing vadSpeechActive)
    val pngTuberCorner: String = "BOTTOM_LEFT",   // "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"
    val pngTuberSizePercent: Int = 18,            // 12..30% of output frame height

    // === v1.6.0 SolShorts™ Studio Controls (All Freely Toggleable — Zero Locks/Caps) ===
    val shortsVerticalAnchor: String = "CENTER",  // "CENTER" (0%) or "ABOVE_CHAT" (+10% upward shift above YouTube Shorts chat)
    val shortsMinimapLoupe: String = "OFF",       // "OFF", "TOP_RIGHT", "TOP_LEFT" (0-copy GPU radar crop above gameplay)
    val shortsScoreLoupe: String = "OFF",         // "OFF", "TOP_LEFT_FEED" (Score+Killfeed), "TOP_LEFT" (Score/Squad), "TOP_CENTER"
    val shortsHeaderLayout: Boolean = false,      // Docks SolHUD / QR / PNGTuber cleanly above gameplay in 9:16 mode
    val shortsCaptureCooler: Boolean = true       // Toggleable ~50%-lighter 9:16 capture + landscape-first VirtualDisplay startup
)

data class StudioProfile(
    val slotIndex: Int,
    val name: String,
    val settings: StreamSettings,
    val isUserCustomized: Boolean = false
)

object KeystoreVault {

    const val AUSPICIOUS_DEDICATION = "ॐ नमः शिवाय"
    const val DEFAULT_YOUTUBE_RTMPS = "rtmps://a.rtmps.youtube.com:443/live2"
    const val DEFAULT_TWITCH_RTMPS = "rtmps://ingest.global-contribute.live-video.net:443/app"
    const val DEFAULT_KICK_RTMPS = "rtmps://fa723fc1b171.global-contribute.live-video.net:443/app"
    const val STUDIO_PROFILE_COUNT = 4
    const val MAX_QR_OVERLAY_CHARS = 200

    val SHORTS_ANCHOR_ORDER = listOf("CENTER", "ABOVE_CHAT")
    val SHORTS_MINIMAP_LOUPE_ORDER = listOf("OFF", "TOP_RIGHT", "TOP_LEFT")
    val SHORTS_SCORE_LOUPE_ORDER = listOf("OFF", "TOP_LEFT_FEED", "TOP_LEFT", "TOP_CENTER")

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "solstream_vault_aes256_v1"
    private const val PREFS_NAME = "solstream_studio_vault"

    private const val KEY_INGEST_URL = "ingest_rtmps_url"
    private const val KEY_ENC_STREAM_KEY = "enc_youtube_stream_key"
    private const val KEY_ENC_SHORTS_KEY = "enc_youtube_shorts_key"
    private const val KEY_TWITCH_URL = "simulcast_twitch_url"
    private const val KEY_ENC_TWITCH_KEY = "enc_twitch_stream_key"
    private const val KEY_KICK_URL = "simulcast_kick_url"
    private const val KEY_ENC_KICK_KEY = "enc_kick_stream_key"
    private const val KEY_SETTINGS_JSON = "stream_settings_json"
    private const val KEY_AUTO_SCAN_DONE = "initial_auto_hw_scan_done"
    private const val KEY_PROFILE_SLOT_PREFIX = "studio_profile_slot_v1_"

    private val vaultLock = Any()
    private val invisibleWhitespaceRegex = Regex("[\\s\\u200B\\u200C\\u200D\\uFEFF]+")
    private val validOverlayCorners = setOf("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT")

    @Volatile private var cachedSecurityLabel: String? = null

    private fun prefs(context: Context): SharedPreferences {
        val safeCtx = context.applicationContext ?: context
        return safeCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun sanitizeOverlayCorner(rawCorner: String?, fallback: String): String {
        val norm = rawCorner?.trim()?.uppercase(Locale.US) ?: ""
        return if (norm in validOverlayCorners) norm else fallback
    }

    fun sanitizeShortsVerticalAnchor(raw: String?): String {
        return if (raw?.trim()?.equals("ABOVE_CHAT", ignoreCase = true) == true) "ABOVE_CHAT" else "CENTER"
    }

    fun sanitizeShortsMinimapLoupe(raw: String?): String {
        val norm = raw?.trim()?.uppercase(Locale.US) ?: "OFF"
        return if (norm in SHORTS_MINIMAP_LOUPE_ORDER) norm else "OFF"
    }

    fun sanitizeShortsScoreLoupe(raw: String?): String {
        val norm = raw?.trim()?.uppercase(Locale.US) ?: "OFF"
        return if (norm in SHORTS_SCORE_LOUPE_ORDER) norm else "OFF"
    }

    private fun resolveDeviceTier(tierId: String): DeviceTier = when (tierId.uppercase(Locale.US)) {
        "LITE" -> DeviceTier.LITE
        "BALANCED" -> DeviceTier.BALANCED
        "HIGH" -> DeviceTier.HIGH
        "ULTRA" -> DeviceTier.ULTRA
        else -> DeviceTier.FLAGSHIP
    }

    // === CENTRAL PLATFORM-COMPATIBILITY VALIDATOR (TWITCH / KICK / SINGLE-ENCODE SIMULCAST / B-FRAMES) ===

    fun isSingleEncodeSimulcast(settings: StreamSettings): Boolean {
        return (settings.simulcastTwitchEnabled || settings.simulcastKickEnabled) && !settings.simulcastSplitEncode
    }

    fun isTwitchLimited(settings: StreamSettings): Boolean {
        return settings.platformPreset.trim().equals("TWITCH", ignoreCase = true) ||
            (isSingleEncodeSimulcast(settings) && settings.simulcastTwitchEnabled)
    }

    fun isKickLimited(settings: StreamSettings): Boolean {
        return settings.platformPreset.trim().equals("KICK", ignoreCase = true) ||
            (isSingleEncodeSimulcast(settings) && settings.simulcastKickEnabled)
    }

    fun isStrictH264Destination(settings: StreamSettings): Boolean {
        return isTwitchLimited(settings) || isKickLimited(settings)
    }

    fun isStrictCbrDestination(settings: StreamSettings): Boolean {
        return isTwitchLimited(settings) || isKickLimited(settings)
    }

    fun maxLongSideForDestination(settings: StreamSettings): Int {
        return if (isStrictH264Destination(settings)) 1920 else 3840
    }

    fun maxVideoBitrateKbpsForDestination(settings: StreamSettings): Int {
        return when {
            isTwitchLimited(settings) -> 6000
            isKickLimited(settings) -> 8000
            else -> 100_000
        }
    }

    fun maxAudioBitrateKbpsForDestination(settings: StreamSettings): Int {
        return if (isStrictH264Destination(settings)) 160 else 320
    }

    /**
     * Central destination-compatibility enforcer used on preset selection, UI edits, JSON import,
     * and immediately before encoder start:
     * - Twitch (primary or single-encode simulcast): H.264, CBR, <=1080p60, <=6,000 kbps, <=160 kbps AAC, 2s keyframe, HDR10 OFF.
     * - Kick (primary or single-encode simulcast): H.264, CBR, <=1080p60, <=8,000 kbps, <=160 kbps AAC, 2s keyframe, HDR10 OFF.
     * - YouTube & Custom RTMP: Fully unlocked (360p..4K60, 0.5..100 Mbps, HEVC/H264/AV1, all SolShorts™ toggles free).
     * - All destinations: maxBFrames locked to 0 (RootEncoder 2.8.1 packetizer writes CTS=0).
     */
    fun enforcePlatformLimits(settings: StreamSettings): Pair<StreamSettings, Boolean> {
        val normPlatform = when (settings.platformPreset.trim().uppercase(Locale.US)) {
            "YOUTUBE", "TWITCH", "KICK", "CUSTOM" -> settings.platformPreset.trim().uppercase(Locale.US)
            else -> "YOUTUBE"
        }
        val normalized = settings.copy(
            platformPreset = normPlatform,
            shortsVerticalAnchor = sanitizeShortsVerticalAnchor(settings.shortsVerticalAnchor),
            shortsMinimapLoupe = sanitizeShortsMinimapLoupe(settings.shortsMinimapLoupe),
            shortsScoreLoupe = sanitizeShortsScoreLoupe(settings.shortsScoreLoupe)
        )

        val strictDest = isStrictH264Destination(normalized)
        val normCodec = when (normalized.codec.trim().uppercase(Locale.US)) {
            "HEVC", "H264", "AV1" -> normalized.codec.trim().uppercase(Locale.US)
            else -> "HEVC"
        }
        val finalCodec = if (strictDest) "H264" else normCodec

        var longDim = max(normalized.width, normalized.height).coerceIn(360, 3840)
        var shortDim = min(normalized.width, normalized.height).coerceIn(360, 2160)
        val maxLong = maxLongSideForDestination(normalized)
        if (longDim > maxLong) {
            longDim = 1920
            shortDim = 1080
        }

        val finalFps = if (normalized.fps <= 30) 30 else 60
        val finalBitrate = normalized.targetBitrateKbps.coerceIn(500, maxVideoBitrateKbpsForDestination(normalized))
        val finalAudioBitrate = normalized.audioBitrateKbps.coerceIn(64, maxAudioBitrateKbpsForDestination(normalized))
        val finalUseVbr = if (strictDest) false else normalized.useVbr
        val finalHdr10 = if (strictDest) false else normalized.hdr10Enabled
        val finalKeyframe = if (strictDest) 2 else normalized.keyframeIntervalSec.coerceIn(1, 4)
        val finalBFrames = 0

        val enforced = normalized.copy(
            codec = finalCodec,
            width = longDim,
            height = shortDim,
            fps = finalFps,
            targetBitrateKbps = finalBitrate,
            audioBitrateKbps = finalAudioBitrate,
            useVbr = finalUseVbr,
            hdr10Enabled = finalHdr10,
            keyframeIntervalSec = finalKeyframe,
            maxBFrames = finalBFrames
        )
        return enforced to (enforced != settings)
    }

    /**
     * Single shared coordinator for gender-matched High-Pass Filter cutoffs (SolLuxe™ 3.0 Pure):
     * - MALE:    55 Hz (clears table taps & AC hum while preserving full 95 Hz baritone chest resonance)
     * - NEUTRAL: 68 Hz
     * - FEMALE:  85 Hz
     */
    fun resolveDefaultHpfForProfile(genderProfile: String): Int {
        return when (genderProfile.uppercase(Locale.US)) {
            "MALE" -> 55
            "FEMALE" -> 85
            else -> 68
        }
    }

    /**
     * Strips invisible spaces, newlines, tabs, and zero-width characters copied from mobile browsers.
     */
    fun sanitizeStreamKeyInput(rawKey: String): String {
        return rawKey.replace(invisibleWhitespaceRegex, "").trim().trimStart('/')
    }

    /**
     * Detects if a user accidentally copied an RTMP/RTMPS Server URL when tapping "Paste Key".
     */
    fun isLikelyRtmpUrl(rawText: String): Boolean {
        val clean = rawText.trim().lowercase(Locale.US)
        return clean.startsWith("rtmp://") || clean.startsWith("rtmps://")
    }

    /**
     * Builds the Card 2 Key Vault Status Badge without exposing the full secret key.
     */
    fun getMaskedKeyStatusBadge(rawKey: String): String {
        val clean = sanitizeStreamKeyInput(rawKey)
        if (clean.isEmpty()) return "⚠️ Empty"
        val tail = if (clean.length >= 4) clean.takeLast(4) else clean
        return "🔒 Key Saved (••••$tail)"
    }

    fun normalizeIngestUrl(rawUrl: String): String {
        val trimmed = rawUrl.replace(invisibleWhitespaceRegex, "").trim().trimEnd('/')
        if (trimmed.isEmpty()) return DEFAULT_YOUTUBE_RTMPS

        val fixedHost = trimmed
            .replace("rtmps://a.rtmp.youtube.com", "rtmps://a.rtmps.youtube.com", ignoreCase = true)
            .replace("rtmps://b.rtmp.youtube.com", "rtmps://b.rtmps.youtube.com", ignoreCase = true)

        return try {
            val uri = URI(fixedHost)
            val scheme = (uri.scheme ?: "rtmps").lowercase(Locale.US)
            val host = uri.host ?: return fixedHost
            val port = if (uri.port > 0) uri.port else if (scheme == "rtmps") 443 else 1935
            val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/live2"
            val query = uri.rawQuery?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
            "$scheme://$host:$port$path$query"
        } catch (_: Exception) {
            fixedHost
        }
    }

    fun joinUrlAndStreamKey(baseUrl: String, streamKey: String): String? {
        val cleanKey = sanitizeStreamKeyInput(streamKey)
        if (cleanKey.isEmpty()) return null
        val normBase = normalizeIngestUrl(baseUrl)
        val qIdx = normBase.indexOf('?')
        return if (qIdx >= 0) {
            val pathPart = normBase.substring(0, qIdx).trimEnd('/')
            val queryPart = normBase.substring(qIdx)
            "$pathPart/$cleanKey$queryPart"
        } else {
            "${normBase.trimEnd('/')}/$cleanKey"
        }
    }

    fun deriveYoutubeBackupUrl(primaryFullEndpoint: String): String? {
        val lower = primaryFullEndpoint.lowercase(Locale.US)
        if (!lower.contains("a.rtmps.youtube.com") && !lower.contains("a.rtmp.youtube.com")) {
            return null
        }
        val swapped = primaryFullEndpoint
            .replace("a.rtmps.youtube.com", "b.rtmps.youtube.com", ignoreCase = true)
            .replace("a.rtmp.youtube.com", "b.rtmp.youtube.com", ignoreCase = true)
        return if (swapped.contains("backup=1")) {
            swapped
        } else if (swapped.contains("?")) {
            "$swapped&backup=1"
        } else {
            "$swapped?backup=1"
        }
    }

    // === PRIMARY & SHORTS STREAM KEY VAULT (RETURNS TRUE ONLY WHEN ENCRYPTION + STORAGE SUCCEED) ===
    fun saveEndpointAndKey(context: Context, rtmpsUrl: String, plainStreamKey: String): Boolean {
        val sp = prefs(context)
        val normUrl = normalizeIngestUrl(rtmpsUrl)
        val cleanKey = sanitizeStreamKeyInput(plainStreamKey)
        val editor = sp.edit().putString(KEY_INGEST_URL, normUrl)
        if (cleanKey.isEmpty()) {
            editor.remove(KEY_ENC_STREAM_KEY)
            return editor.commit()
        }
        val enc = encryptSecret(context, cleanKey) ?: return false
        editor.putString(KEY_ENC_STREAM_KEY, enc)
        return editor.commit()
    }

    fun getRtmpsUrl(context: Context): String {
        val raw = prefs(context).getString(KEY_INGEST_URL, DEFAULT_YOUTUBE_RTMPS) ?: DEFAULT_YOUTUBE_RTMPS
        return normalizeIngestUrl(raw)
    }

    fun getStreamKey(context: Context): String {
        val blob = prefs(context).getString(KEY_ENC_STREAM_KEY, null) ?: return ""
        return decryptSecret(blob)
    }

    fun clearStreamKey(context: Context) {
        prefs(context).edit().remove(KEY_ENC_STREAM_KEY).apply()
    }

    fun saveShortsStreamKey(context: Context, shortsKey: String): Boolean {
        val sp = prefs(context)
        val clean = sanitizeStreamKeyInput(shortsKey)
        val edit = sp.edit()
        if (clean.isEmpty()) {
            edit.remove(KEY_ENC_SHORTS_KEY)
            return edit.commit()
        }
        val enc = encryptSecret(context, clean) ?: return false
        edit.putString(KEY_ENC_SHORTS_KEY, enc)
        return edit.commit()
    }

    fun getShortsStreamKey(context: Context): String {
        val blob = prefs(context).getString(KEY_ENC_SHORTS_KEY, null) ?: return ""
        return decryptSecret(blob)
    }

    // === MULTI-PLATFORM SIMULCAST VAULT (TWITCH, KICK & CUSTOM RTMP) ===
    fun saveTwitchUrlAndKey(context: Context, url: String, key: String): Boolean {
        val sp = prefs(context)
        val edit = sp.edit().putString(KEY_TWITCH_URL, normalizeIngestUrl(url.ifBlank { DEFAULT_TWITCH_RTMPS }))
        val clean = sanitizeStreamKeyInput(key)
        if (clean.isEmpty()) {
            edit.remove(KEY_ENC_TWITCH_KEY)
            return edit.commit()
        }
        val enc = encryptSecret(context, clean) ?: return false
        edit.putString(KEY_ENC_TWITCH_KEY, enc)
        return edit.commit()
    }

    fun getTwitchUrl(context: Context): String {
        val raw = prefs(context).getString(KEY_TWITCH_URL, DEFAULT_TWITCH_RTMPS) ?: DEFAULT_TWITCH_RTMPS
        return normalizeIngestUrl(raw)
    }

    fun getTwitchStreamKey(context: Context): String {
        val blob = prefs(context).getString(KEY_ENC_TWITCH_KEY, null) ?: return ""
        return decryptSecret(blob)
    }

    fun saveKickUrlAndKey(context: Context, url: String, key: String): Boolean {
        val sp = prefs(context)
        val edit = sp.edit().putString(KEY_KICK_URL, normalizeIngestUrl(url.ifBlank { DEFAULT_KICK_RTMPS }))
        val clean = sanitizeStreamKeyInput(key)
        if (clean.isEmpty()) {
            edit.remove(KEY_ENC_KICK_KEY)
            return edit.commit()
        }
        val enc = encryptSecret(context, clean) ?: return false
        edit.putString(KEY_ENC_KICK_KEY, enc)
        return edit.commit()
    }

    fun getKickUrl(context: Context): String {
        val raw = prefs(context).getString(KEY_KICK_URL, DEFAULT_KICK_RTMPS) ?: DEFAULT_KICK_RTMPS
        return normalizeIngestUrl(raw)
    }

    fun getKickStreamKey(context: Context): String {
        val blob = prefs(context).getString(KEY_ENC_KICK_KEY, null) ?: return ""
        return decryptSecret(blob)
    }

    // === ENDPOINT BUILDERS ===
    fun buildFullStreamEndpoint(context: Context): String? {
        val settings = loadSettings(context)
        return buildActivePrimaryEndpoint(context, settings)
    }

    fun buildBackupStreamEndpoint(context: Context): String? {
        val primary = buildFullStreamEndpoint(context) ?: return null
        return deriveYoutubeBackupUrl(primary)
    }

    fun buildActivePrimaryEndpoint(context: Context, settings: StreamSettings): String? {
        val baseUrl = getRtmpsUrl(context)
        val shortsKey = getShortsStreamKey(context)
        val activeKey = if (settings.isVertical && settings.useShortsKeySlot && shortsKey.isNotBlank()) {
            shortsKey
        } else {
            getStreamKey(context)
        }
        return joinUrlAndStreamKey(baseUrl, activeKey)
    }

    /**
     * Builds the secondary 9:16 companion endpoint ONLY when Slot #2 has a distinct non-blank
     * stream key (prevents primary + companion encoders from colliding on the same YouTube key).
     */
    fun buildCompanionVerticalEndpoint(context: Context): String? {
        val shortsKey = sanitizeStreamKeyInput(getShortsStreamKey(context))
        val primaryKey = sanitizeStreamKeyInput(getStreamKey(context))
        if (shortsKey.isBlank() || shortsKey == primaryKey) return null
        return joinUrlAndStreamKey(getRtmpsUrl(context), shortsKey)
    }

    fun hasDistinctCompanionShortsKey(context: Context): Boolean {
        val shortsKey = sanitizeStreamKeyInput(getShortsStreamKey(context))
        val primaryKey = sanitizeStreamKeyInput(getStreamKey(context))
        return shortsKey.isNotBlank() && shortsKey != primaryKey
    }

    fun buildSimulcastTargets(context: Context, settings: StreamSettings): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        if (settings.simulcastTwitchEnabled) {
            joinUrlAndStreamKey(getTwitchUrl(context), getTwitchStreamKey(context))?.let {
                out.add("Twitch" to it)
            }
        }
        if (settings.simulcastKickEnabled) {
            joinUrlAndStreamKey(getKickUrl(context), getKickStreamKey(context))?.let {
                out.add("Kick" to it)
            }
        }
        return out
    }

    // === PLATFORM-AWARE & VOICE-AWARE TIER PRESET COORDINATOR (CARD 0 <-> CARD 2 <-> CARD 3 <-> CARD 4) ===
    fun applyPlatformAndVoiceAwareTierPreset(
        current: StreamSettings,
        tier: DeviceTier,
        scan: DeviceScanSummary?
    ): StreamSettings {
        val matchedHpf = resolveDefaultHpfForProfile(current.voiceGenderProfile)
        val resolvedHpf = if (current.hpfAutoMode) matchedHpf else current.hpfCutoffHz
        val twitchRestricted = isTwitchLimited(current)
        val kickRestricted = isKickLimited(current)

        val hevcReport = scan?.hevcHardware
        val h264Report = scan?.h264Hardware
        val hevcOk = hevcReport != null && (hevcReport.verifiedRealEncode || hevcReport.verifiedSurfaceConfigure || hevcReport.advertisedByMetadata)

        val resolvedCodec: String
        val resolvedW: Int
        val resolvedH: Int
        val resolvedFps: Int
        val resolvedBitrateKbps: Int
        val resolvedAudioKbps: Int

        val h264Supports1080p = h264Report == null || h264Report.supports1080p60 || h264Report.supports1080p30
        val h264Supports1080p60 = h264Report == null || h264Report.supports1080p60

        if (twitchRestricted) {
            resolvedCodec = "H264"
            resolvedW = if (tier == DeviceTier.LITE && !h264Supports1080p) 1280 else 1920
            resolvedH = if (resolvedW == 1280) 720 else 1080
            resolvedFps = if (tier == DeviceTier.LITE || !h264Supports1080p60) 30 else 60
            resolvedBitrateKbps = if (tier == DeviceTier.LITE) 4500 else 6000
            resolvedAudioKbps = if (tier == DeviceTier.LITE) 128 else 160
        } else if (kickRestricted) {
            resolvedCodec = "H264"
            resolvedW = if (tier == DeviceTier.LITE && !h264Supports1080p) 1280 else 1920
            resolvedH = if (resolvedW == 1280) 720 else 1080
            resolvedFps = if (tier == DeviceTier.LITE || !h264Supports1080p60) 30 else 60
            resolvedBitrateKbps = if (tier == DeviceTier.LITE) 5000 else 8000
            resolvedAudioKbps = 160
        } else {
            resolvedCodec = if (hevcOk) "HEVC" else "H264"
            val activeRep = if (resolvedCodec == "HEVC") hevcReport else h264Report
            val can1440p60 = activeRep?.supports1440p60 ?: true
            val can1080p60 = activeRep?.supports1080p60 ?: true
            val can1080pAny = activeRep == null || activeRep.supports1080p60 || activeRep.supports1080p30
            when (tier) {
                DeviceTier.LITE -> {
                    resolvedW = if (can1080pAny) 1920 else 1280
                    resolvedH = if (resolvedW == 1920) 1080 else 720
                    resolvedFps = 30
                    resolvedBitrateKbps = if (resolvedW == 1920) 8000 else 4500
                    resolvedAudioKbps = 128
                }
                DeviceTier.BALANCED -> {
                    resolvedW = if (can1080pAny) 1920 else 1280
                    resolvedH = if (resolvedW == 1920) 1080 else 720
                    resolvedFps = if (can1080p60) 60 else 30
                    resolvedBitrateKbps = 12000
                    resolvedAudioKbps = 160
                }
                DeviceTier.HIGH -> {
                    resolvedW = 1920
                    resolvedH = 1080
                    resolvedFps = if (can1080p60) 60 else 30
                    resolvedBitrateKbps = 16000
                    resolvedAudioKbps = 192
                }
                DeviceTier.FLAGSHIP -> {
                    resolvedW = if (can1440p60) 2560 else 1920
                    resolvedH = if (can1440p60) 1440 else 1080
                    resolvedFps = if (can1440p60 || can1080p60) 60 else 30
                    resolvedBitrateKbps = 20000
                    resolvedAudioKbps = 256
                }
                DeviceTier.ULTRA -> {
                    resolvedW = if (can1440p60) 2560 else 1920
                    resolvedH = if (can1440p60) 1440 else 1080
                    resolvedFps = if (can1440p60 || can1080p60) 60 else 30
                    resolvedBitrateKbps = 20000
                    resolvedAudioKbps = 320
                }
            }
        }

        val built = current.copy(
            deviceTier = tier.id,
            codec = resolvedCodec,
            width = resolvedW,
            height = resolvedH,
            fps = resolvedFps,
            targetBitrateKbps = resolvedBitrateKbps,
            useVbr = false,
            adaptiveBitrate = true,
            upscalerMode = if (tier == DeviceTier.LITE) "BILINEAR" else "BICUBIC",
            roiMode = "AUTO",
            keyframeIntervalSec = 2,
            maxBFrames = 0,
            qpMaxCap = 0,
            lowLatencyCodecHint = true,
            audioBitrateKbps = resolvedAudioKbps,
            voicePreset = "SOL_LUXE",
            noiseSuppressMode = "RNNOISE",
            rnnoiseLevel = 2,
            hpfCutoffHz = resolvedHpf,
            hpfAutoMode = current.hpfAutoMode,
            transientTamerEnabled = true,
            noiseGateEnabled = true,
            voiceEqEnabled = true,
            deEsserEnabled = true,
            velvetWarmthEnabled = true,
            compressorEnabled = true,
            whisperLiftEnabled = true,
            slowAgcEnabled = true,
            speakerAecEnabled = false
        )
        return enforcePlatformLimits(built).first
    }

    /**
     * Smart Cross-Phone & Destination Preset Clamping: validates an imported JSON preset against both
     * the receiving phone's hardware scan and the active destination's streaming limits (Twitch / Kick / Simulcast).
     */
    fun clampImportedSettingsToHardware(
        imported: StreamSettings,
        scan: DeviceScanSummary
    ): Pair<StreamSettings, Boolean> {
        val (destClamped, destChanged) = enforcePlatformLimits(imported)
        var changed = destChanged
        var codec = destClamped.codec.uppercase(Locale.US)
        if (codec == "AV1" && scan.av1Hardware == null) {
            codec = if (scan.hevcHardware != null) "HEVC" else "H264"
            changed = true
        }
        if (codec == "HEVC" && scan.hevcHardware == null) {
            codec = "H264"
            changed = true
        }

        val report = scan.findReport(codec) ?: scan.hevcHardware ?: scan.h264Hardware
        var longDim = max(destClamped.width, destClamped.height)
        var shortDim = min(destClamped.width, destClamped.height)
        var fps = destClamped.fps

        if (report != null) {
            if (longDim >= 3840 && !(if (fps >= 60) report.supports4k60 else (report.supports4k30 || report.supports4k60))) {
                longDim = 2560
                shortDim = 1440
                changed = true
            }
            if (longDim >= 2560 && !(if (fps >= 60) report.supports1440p60 else (report.supports1440p30 || report.supports1440p60))) {
                longDim = 1920
                shortDim = 1080
                changed = true
            }
            if (longDim >= 1920) {
                val ok1080AtFps = if (fps >= 60) report.supports1080p60 else (report.supports1080p30 || report.supports1080p60)
                if (!ok1080AtFps) {
                    if (fps >= 60 && report.supports1080p30) {
                        fps = 30
                    } else {
                        longDim = 1280
                        shortDim = 720
                    }
                    changed = true
                }
            }
            if (longDim == 1280 && fps >= 60 && !report.supports720p60 && report.supports720p30) {
                fps = 30
                changed = true
            }
        }

        val maxChipKbps = ((report?.maxBitrateMbps ?: 50) * 1000).coerceIn(4000, 100_000)
        val effectiveMaxKbps = min(maxChipKbps, maxVideoBitrateKbpsForDestination(destClamped))
        val clampedBps = destClamped.targetBitrateKbps.coerceIn(500, effectiveMaxKbps)
        if (clampedBps != destClamped.targetBitrateKbps) changed = true

        val hdrOk = destClamped.hdr10Enabled && (report?.supports10BitHdr == true)
        if (hdrOk != destClamped.hdr10Enabled) changed = true

        val dualOk = destClamped.dualFormatCompanionEnabled && scan.supportsDualFormatCompanion(primaryCodec = codec)
        if (dualOk != destClamped.dualFormatCompanionEnabled) changed = true

        val finalCandidate = destClamped.copy(
            codec = codec,
            width = longDim,
            height = shortDim,
            fps = fps,
            targetBitrateKbps = clampedBps,
            hdr10Enabled = hdrOk,
            dualFormatCompanionEnabled = dualOk
        )
        val (enforcedFinal, finalChanged) = enforcePlatformLimits(finalCandidate)
        return enforcedFinal to (changed || finalChanged)
    }

    // === 4 ONE-TAP HARDWARE-ADAPTED STUDIO PROFILES (SECRET-FREE: NEVER STORES STREAM KEYS) ===

    fun buildDefaultStudioProfile(
        slotIndex: Int,
        current: StreamSettings,
        scan: DeviceScanSummary?
    ): StudioProfile {
        val safeSlot = slotIndex.coerceIn(0, STUDIO_PROFILE_COUNT - 1)
        val tier = resolveDeviceTier(current.deviceTier)
        val baseLandscape = applyPlatformAndVoiceAwareTierPreset(
            current = current.copy(isVertical = false, useShortsKeySlot = false),
            tier = tier,
            scan = scan
        )

        val (defaultName, rawSettings) = when (safeSlot) {
            0 -> "Wi-Fi Max" to baseLandscape
            1 -> "Mobile Data" to baseLandscape.copy(
                width = 1280,
                height = 720,
                fps = 30,
                targetBitrateKbps = 4500,
                useVbr = false,
                adaptiveBitrate = true,
                audioBitrateKbps = 128,
                dualFormatCompanionEnabled = false
            )
            2 -> {
                val shortsW = if (tier == DeviceTier.LITE) 1280 else 1920
                val shortsH = if (tier == DeviceTier.LITE) 720 else 1080
                val shortsFps = if (tier == DeviceTier.LITE) 30 else 60
                val shortsKbps = when (tier) {
                    DeviceTier.LITE -> 5000
                    DeviceTier.BALANCED, DeviceTier.HIGH -> 8000
                    DeviceTier.FLAGSHIP, DeviceTier.ULTRA -> 9000
                }
                val shortsAudioKbps = if (tier == DeviceTier.LITE) 128 else 160
                "Shorts 9:16" to baseLandscape.copy(
                    width = shortsW,
                    height = shortsH,
                    fps = shortsFps,
                    isVertical = true,
                    useShortsKeySlot = true,
                    shortsBackdropMode = "BLUR",
                    shortsVerticalAnchor = "ABOVE_CHAT",
                    shortsCaptureCooler = true,
                    targetBitrateKbps = shortsKbps,
                    audioBitrateKbps = shortsAudioKbps,
                    dualFormatCompanionEnabled = false
                )
            }
            else -> "Custom" to current
        }

        val finalSettings = if (scan != null) {
            clampImportedSettingsToHardware(rawSettings, scan).first
        } else {
            enforcePlatformLimits(rawSettings).first
        }
        return StudioProfile(
            slotIndex = safeSlot,
            name = defaultName,
            settings = finalSettings,
            isUserCustomized = false
        )
    }

    fun exportStudioProfileJson(profile: StudioProfile): String {
        val cleanName = profile.name.trim().ifBlank { "Profile ${profile.slotIndex + 1}" }.take(20)
        return JSONObject().apply {
            put("slotIndex", profile.slotIndex.coerceIn(0, STUDIO_PROFILE_COUNT - 1))
            put("name", cleanName)
            put("settings", JSONObject(exportPresetJson(profile.settings)))
        }.toString()
    }

    fun importStudioProfileJson(
        slotIndex: Int,
        rawJson: String,
        fallback: StreamSettings = StreamSettings()
    ): StudioProfile? {
        return try {
            val j = JSONObject(rawJson.trim())
            val settingsObj = j.optJSONObject("settings") ?: return null
            val parsed = importPresetJson(settingsObj.toString(), fallback) ?: return null
            val safeSlot = slotIndex.coerceIn(0, STUDIO_PROFILE_COUNT - 1)
            val cleanName = j.optString("name", "Profile ${safeSlot + 1}")
                .trim()
                .ifBlank { "Profile ${safeSlot + 1}" }
                .take(20)
            StudioProfile(
                slotIndex = safeSlot,
                name = cleanName,
                settings = parsed,
                isUserCustomized = true
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveStudioProfile(
        context: Context,
        slotIndex: Int,
        name: String,
        settings: StreamSettings
    ): Boolean {
        val safeSlot = slotIndex.coerceIn(0, STUDIO_PROFILE_COUNT - 1)
        val profile = StudioProfile(
            slotIndex = safeSlot,
            name = name.trim().ifBlank { "Profile ${safeSlot + 1}" }.take(20),
            settings = enforcePlatformLimits(settings).first,
            isUserCustomized = true
        )
        return try {
            prefs(context)
                .edit()
                .putString("$KEY_PROFILE_SLOT_PREFIX$safeSlot", exportStudioProfileJson(profile))
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    fun loadStudioProfile(
        context: Context,
        slotIndex: Int,
        current: StreamSettings,
        scan: DeviceScanSummary?
    ): StudioProfile {
        val safeSlot = slotIndex.coerceIn(0, STUDIO_PROFILE_COUNT - 1)
        val raw = prefs(context).getString("$KEY_PROFILE_SLOT_PREFIX$safeSlot", null)
        if (!raw.isNullOrBlank()) {
            val imported = importStudioProfileJson(safeSlot, raw, current)
            if (imported != null) {
                val clamped = if (scan != null) {
                    clampImportedSettingsToHardware(imported.settings, scan).first
                } else {
                    enforcePlatformLimits(imported.settings).first
                }
                return imported.copy(settings = clamped)
            }
        }
        return buildDefaultStudioProfile(safeSlot, current, scan)
    }

    fun loadAllStudioProfiles(
        context: Context,
        current: StreamSettings,
        scan: DeviceScanSummary?
    ): List<StudioProfile> {
        return List(STUDIO_PROFILE_COUNT) { idx ->
            loadStudioProfile(context, idx, current, scan)
        }
    }

    fun resetStudioProfile(context: Context, slotIndex: Int) {
        val safeSlot = slotIndex.coerceIn(0, STUDIO_PROFILE_COUNT - 1)
        prefs(context).edit().remove("$KEY_PROFILE_SLOT_PREFIX$safeSlot").apply()
    }

    // === HARDWARE KEYSTORE AES-256-GCM ===
    fun getKeySecurityDescription(): String {
        cachedSecurityLabel?.let { return it }
        return synchronized(vaultLock) {
            cachedSecurityLabel?.let { return@synchronized it }
            try {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val key = (ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey) ?: return@synchronized "Android Keystore Ready"
                val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
                val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
                val verified = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    when (info.securityLevel) {
                        KeyProperties.SECURITY_LEVEL_STRONGBOX -> "Hardware-Backed (StrongBox)"
                        KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "Hardware-Backed (TEE)"
                        else -> "OS Keystore Protected"
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (info.isInsideSecureHardware) "Hardware-Backed (TEE)" else "OS Keystore Protected"
                }
                cachedSecurityLabel = verified
                verified
            } catch (_: Exception) {
                "Android Keystore Protected"
            }
        }
    }

    private fun getOrCreateAesKey(context: Context): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val appCtx = context.applicationContext ?: context
        val hasStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            appCtx.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (hasStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setIsStrongBoxBacked(true)
                    .build()
                kg.init(spec)
                cachedSecurityLabel = null
                return kg.generateKey()
            } catch (_: Exception) {
            }
        }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        kg.init(spec)
        cachedSecurityLabel = null
        return kg.generateKey()
    }

    private fun isPermanentKeyInvalidation(e: Throwable): Boolean {
        var curr: Throwable? = e
        while (curr != null) {
            if (curr is KeyPermanentlyInvalidatedException || curr is UnrecoverableKeyException) {
                return true
            }
            curr = curr.cause
        }
        return false
    }

    private fun encryptSecret(context: Context, plain: String): String? = synchronized(vaultLock) {
        for (attempt in 0..1) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey(context))
                val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
                val ct = Base64.encodeToString(
                    cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8)),
                    Base64.NO_WRAP
                )
                return@synchronized "$iv:$ct"
            } catch (e: Exception) {
                // Non-Destructive Keystore Recovery: only delete/regenerate master key on permanent invalidation
                if (attempt == 0 && isPermanentKeyInvalidation(e)) {
                    try {
                        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                        ks.deleteEntry(MASTER_KEY_ALIAS)
                        cachedSecurityLabel = null
                    } catch (_: Exception) {
                    }
                } else {
                    return@synchronized null
                }
            }
        }
        null
    }

    private fun decryptSecret(encodedBlob: String): String = synchronized(vaultLock) {
        try {
            val parts = encodedBlob.split(':')
            if (parts.size != 2) return@synchronized ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = (ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey) ?: return@synchronized ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), StandardCharsets.UTF_8)
        } catch (e: Throwable) {
            if (isPermanentKeyInvalidation(e)) {
                try {
                    val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    ks.deleteEntry(MASTER_KEY_ALIAS)
                    cachedSecurityLabel = null
                } catch (_: Exception) {
                }
            }
            ""
        }
    }

    // === SETTINGS PERSISTENCE & SECRET-FREE PRESET SHARE/IMPORT ===
    fun saveSettings(context: Context, s: StreamSettings) {
        val safe = enforcePlatformLimits(s).first
        prefs(context).edit().putString(KEY_SETTINGS_JSON, exportPresetJson(safe)).apply()
    }

    fun loadSettings(context: Context): StreamSettings {
        val raw = prefs(context).getString(KEY_SETTINGS_JSON, null) ?: return StreamSettings()
        return importPresetJson(raw, StreamSettings()) ?: StreamSettings()
    }

    fun exportPresetJson(s: StreamSettings): String {
        val safe = enforcePlatformLimits(s).first
        return JSONObject().apply {
            put("solPresetSchema", 6)
            put("deviceTier", safe.deviceTier)
            put("platformPreset", safe.platformPreset)
            put("useShortsKeySlot", safe.useShortsKeySlot)
            put("simulcastTwitchEnabled", safe.simulcastTwitchEnabled)
            put("simulcastKickEnabled", safe.simulcastKickEnabled)
            put("dualFormatCompanionEnabled", safe.dualFormatCompanionEnabled)
            put("dualFormatBitrateKbps", safe.dualFormatBitrateKbps)

            put("codec", safe.codec)
            put("width", safe.width)
            put("height", safe.height)
            put("fps", safe.fps)
            put("isVertical", safe.isVertical)
            put("landscapeFlip", safe.landscapeFlip)
            put("targetBitrateKbps", safe.targetBitrateKbps)
            put("useVbr", safe.useVbr)
            put("adaptiveBitrate", safe.adaptiveBitrate)
            put("framingMode", safe.framingMode)
            // Snap legacy orphan zoom values (like 104%) to the nearest valid step (108%)
            val clampedZoom = when (safe.smartZoomPercent) {
                in 100..106 -> 100
                in 107..111 -> 108
                in 112..120 -> 115
                in 121..130 -> 125
                in 131..142 -> 135
                in 143..162 -> 150
                in 163..187 -> 175
                in 188..212 -> 200
                in 213..237 -> 225
                else -> if (safe.smartZoomPercent > 237) 250 else safe.smartZoomPercent
            }.coerceIn(100, 250)
            put("smartZoomPercent", clampedZoom)

            put("glEngineMode", safe.glEngineMode)
            put("upscalerMode", safe.upscalerMode)
            put("hdr10Enabled", safe.hdr10Enabled)
            put("cinematicFilmic", safe.cinematicFilmic)
            put("amoledVivid", safe.amoledVivid)
            put("shadowBoost", safe.shadowBoost)
            put("colorTempFilter", safe.colorTempFilter)
            put("gpuSharpen", safe.gpuSharpen)
            put("roiMode", safe.roiMode)
            put("keyframeIntervalSec", safe.keyframeIntervalSec)
            put("maxBFrames", 0)
            put("qpMaxCap", safe.qpMaxCap)
            put("lowLatencyCodecHint", safe.lowLatencyCodecHint)
            put("shortsBackdropMode", safe.shortsBackdropMode)
            put("overlayImageEnabled", safe.overlayImageEnabled)
            put("liveBannerEnabled", safe.liveBannerEnabled)
            put("liveBannerText", safe.liveBannerText)
            put("liveBannerPosition", safe.liveBannerPosition)
            put("minimapShieldEnabled", safe.minimapShieldEnabled)
            put("minimapShieldCorner", safe.minimapShieldCorner)

            put("micDeviceId", safe.micDeviceId)
            put("micAudioSource", safe.micAudioSource)
            put("audioBitrateKbps", safe.audioBitrateKbps)
            put("voiceGenderProfile", safe.voiceGenderProfile)
            put("voicePreset", safe.voicePreset)
            put("noiseSuppressMode", safe.noiseSuppressMode)
            put("rnnoiseLevel", safe.rnnoiseLevel)
            put("hpfCutoffHz", safe.hpfCutoffHz)
            put("hpfAutoMode", safe.hpfAutoMode)
            put("transientTamerEnabled", safe.transientTamerEnabled)
            put("noiseGateEnabled", safe.noiseGateEnabled)
            put("voiceEqEnabled", safe.voiceEqEnabled)
            put("deEsserEnabled", safe.deEsserEnabled)
            put("velvetWarmthEnabled", safe.velvetWarmthEnabled)
            put("compressorEnabled", safe.compressorEnabled)
            put("whisperLiftEnabled", safe.whisperLiftEnabled)
            put("slowAgcEnabled", safe.slowAgcEnabled)
            put("speakerAecEnabled", safe.speakerAecEnabled)
            put("micGainPercent", safe.micGainPercent)
            put("gameGainPercent", safe.gameGainPercent)
            put("gameCinemaWideEnabled", safe.gameCinemaWideEnabled)
            put("gameSubPunchEnabled", safe.gameSubPunchEnabled)
            put("gameFootstepFocusEnabled", safe.gameFootstepFocusEnabled)
            put("gameBlastTamerEnabled", safe.gameBlastTamerEnabled)
            put("vadDuckingPercent", safe.vadDuckingPercent)
            put("micSyncDelayMs", safe.micSyncDelayMs)

            put("brbMuteMicToo", safe.brbMuteMicToo)
            put("wifiLowLatencyLock", safe.wifiLowLatencyLock)
            put("overlayPosition", safe.overlayPosition)
            put("autoBlurKeyboard", safe.autoBlurKeyboard)
            put("simulcastSplitEncode", safe.simulcastSplitEncode)

            // Phase 1 (v1.5.0+) Pro Broadcast Overlays
            put("hudTelemetryEnabled", safe.hudTelemetryEnabled)
            put("hudTelemetryCorner", sanitizeOverlayCorner(safe.hudTelemetryCorner, "TOP_LEFT"))
            put("qrOverlayEnabled", safe.qrOverlayEnabled)
            put("qrOverlayText", safe.qrOverlayText.trim().take(MAX_QR_OVERLAY_CHARS))
            put("qrOverlayCorner", sanitizeOverlayCorner(safe.qrOverlayCorner, "BOTTOM_RIGHT"))
            put("pngTuberEnabled", safe.pngTuberEnabled)
            put("pngTuberCorner", sanitizeOverlayCorner(safe.pngTuberCorner, "BOTTOM_LEFT"))
            put("pngTuberSizePercent", safe.pngTuberSizePercent.coerceIn(12, 30))

            // v1.6.0 SolShorts™ Studio Controls
            put("shortsVerticalAnchor", sanitizeShortsVerticalAnchor(safe.shortsVerticalAnchor))
            put("shortsMinimapLoupe", sanitizeShortsMinimapLoupe(safe.shortsMinimapLoupe))
            put("shortsScoreLoupe", sanitizeShortsScoreLoupe(safe.shortsScoreLoupe))
            put("shortsHeaderLayout", safe.shortsHeaderLayout)
            put("shortsCaptureCooler", safe.shortsCaptureCooler)
        }.toString()
    }

    fun importPresetJson(rawJson: String, fallback: StreamSettings = StreamSettings()): StreamSettings? {
        return try {
            val j = JSONObject(rawJson.trim())
            val gender = j.optString("voiceGenderProfile", fallback.voiceGenderProfile)
            val legacyDefaultHpf = when (gender.uppercase(Locale.US)) {
                "MALE" -> 50
                "FEMALE" -> 85
                else -> 65
            }
            val defaultHpf = resolveDefaultHpfForProfile(gender)
            val rawHpf = j.optInt("hpfCutoffHz", defaultHpf).coerceIn(0, 160)
            val inferredAutoHpf = if (j.has("hpfAutoMode")) {
                j.optBoolean("hpfAutoMode", fallback.hpfAutoMode)
            } else if (!j.has("hpfCutoffHz")) {
                true
            } else {
                rawHpf == legacyDefaultHpf || rawHpf == defaultHpf
            }
            val resolvedHpf = if (inferredAutoHpf) defaultHpf else rawHpf

            val rawZoom = j.optInt("smartZoomPercent", fallback.smartZoomPercent)
            val clampedZoom = when (rawZoom) {
                in 100..106 -> 100
                in 107..111 -> 108
                in 112..120 -> 115
                in 121..130 -> 125
                in 131..142 -> 135
                in 143..162 -> 150
                in 163..187 -> 175
                in 188..212 -> 200
                in 213..237 -> 225
                else -> if (rawZoom > 237) 250 else rawZoom
            }.coerceIn(100, 250)

            val parsed = StreamSettings(
                deviceTier = j.optString("deviceTier", fallback.deviceTier),
                platformPreset = j.optString("platformPreset", fallback.platformPreset),
                useShortsKeySlot = j.optBoolean("useShortsKeySlot", fallback.useShortsKeySlot),
                simulcastTwitchEnabled = j.optBoolean("simulcastTwitchEnabled", fallback.simulcastTwitchEnabled),
                simulcastKickEnabled = j.optBoolean("simulcastKickEnabled", fallback.simulcastKickEnabled),
                dualFormatCompanionEnabled = j.optBoolean("dualFormatCompanionEnabled", fallback.dualFormatCompanionEnabled),
                dualFormatBitrateKbps = j.optInt("dualFormatBitrateKbps", fallback.dualFormatBitrateKbps).coerceIn(1500, 25000),

                codec = j.optString("codec", fallback.codec),
                width = j.optInt("width", fallback.width).coerceIn(360, 3840),
                height = j.optInt("height", fallback.height).coerceIn(360, 3840),
                fps = if (j.optInt("fps", fallback.fps) <= 30) 30 else 60,
                isVertical = j.optBoolean("isVertical", fallback.isVertical),
                landscapeFlip = if (j.optInt("landscapeFlip", fallback.landscapeFlip) == 180) 180 else 0,
                targetBitrateKbps = j.optInt("targetBitrateKbps", fallback.targetBitrateKbps).coerceIn(500, 100_000),
                useVbr = j.optBoolean("useVbr", fallback.useVbr),
                adaptiveBitrate = j.optBoolean("adaptiveBitrate", fallback.adaptiveBitrate),
                framingMode = j.optString("framingMode", fallback.framingMode),
                smartZoomPercent = clampedZoom,

                glEngineMode = j.optString("glEngineMode", fallback.glEngineMode),
                upscalerMode = j.optString("upscalerMode", fallback.upscalerMode),
                hdr10Enabled = j.optBoolean("hdr10Enabled", fallback.hdr10Enabled),
                cinematicFilmic = j.optBoolean("cinematicFilmic", fallback.cinematicFilmic),
                amoledVivid = j.optBoolean("amoledVivid", fallback.amoledVivid),
                shadowBoost = j.optBoolean("shadowBoost", fallback.shadowBoost),
                colorTempFilter = j.optString("colorTempFilter", fallback.colorTempFilter),
                gpuSharpen = j.optBoolean("gpuSharpen", fallback.gpuSharpen),
                roiMode = j.optString("roiMode", fallback.roiMode),
                keyframeIntervalSec = j.optInt("keyframeIntervalSec", fallback.keyframeIntervalSec).coerceIn(1, 4),
                maxBFrames = 0,
                qpMaxCap = j.optInt("qpMaxCap", fallback.qpMaxCap).coerceIn(0, 51),
                lowLatencyCodecHint = j.optBoolean("lowLatencyCodecHint", fallback.lowLatencyCodecHint),
                shortsBackdropMode = j.optString("shortsBackdropMode", fallback.shortsBackdropMode),
                overlayImageEnabled = j.optBoolean("overlayImageEnabled", fallback.overlayImageEnabled),
                liveBannerEnabled = j.optBoolean("liveBannerEnabled", fallback.liveBannerEnabled),
                liveBannerText = j.optString("liveBannerText", fallback.liveBannerText).take(90),
                liveBannerPosition = j.optString("liveBannerPosition", fallback.liveBannerPosition),
                minimapShieldEnabled = j.optBoolean("minimapShieldEnabled", fallback.minimapShieldEnabled),
                minimapShieldCorner = j.optString("minimapShieldCorner", fallback.minimapShieldCorner),

                micDeviceId = j.optInt("micDeviceId", fallback.micDeviceId),
                micAudioSource = j.optInt("micAudioSource", fallback.micAudioSource),
                audioBitrateKbps = j.optInt("audioBitrateKbps", fallback.audioBitrateKbps).coerceIn(64, 320),
                voiceGenderProfile = gender,
                voicePreset = j.optString("voicePreset", fallback.voicePreset),
                noiseSuppressMode = j.optString("noiseSuppressMode", fallback.noiseSuppressMode),
                rnnoiseLevel = j.optInt("rnnoiseLevel", fallback.rnnoiseLevel).coerceIn(0, 3),
                hpfCutoffHz = resolvedHpf,
                hpfAutoMode = inferredAutoHpf,
                transientTamerEnabled = j.optBoolean("transientTamerEnabled", fallback.transientTamerEnabled),
                noiseGateEnabled = j.optBoolean("noiseGateEnabled", fallback.noiseGateEnabled),
                voiceEqEnabled = j.optBoolean("voiceEqEnabled", fallback.voiceEqEnabled),
                deEsserEnabled = j.optBoolean("deEsserEnabled", fallback.deEsserEnabled),
                velvetWarmthEnabled = j.optBoolean("velvetWarmthEnabled", fallback.velvetWarmthEnabled),
                compressorEnabled = j.optBoolean("compressorEnabled", fallback.compressorEnabled),
                whisperLiftEnabled = j.optBoolean("whisperLiftEnabled", fallback.whisperLiftEnabled),
                slowAgcEnabled = j.optBoolean("slowAgcEnabled", fallback.slowAgcEnabled),
                speakerAecEnabled = j.optBoolean("speakerAecEnabled", fallback.speakerAecEnabled),
                micGainPercent = j.optInt("micGainPercent", fallback.micGainPercent).coerceIn(50, 250),
                gameGainPercent = j.optInt("gameGainPercent", fallback.gameGainPercent).coerceIn(0, 250),
                gameCinemaWideEnabled = j.optBoolean("gameCinemaWideEnabled", fallback.gameCinemaWideEnabled),
                gameSubPunchEnabled = j.optBoolean("gameSubPunchEnabled", fallback.gameSubPunchEnabled),
                gameFootstepFocusEnabled = j.optBoolean("gameFootstepFocusEnabled", fallback.gameFootstepFocusEnabled),
                gameBlastTamerEnabled = j.optBoolean("gameBlastTamerEnabled", fallback.gameBlastTamerEnabled),
                vadDuckingPercent = j.optInt("vadDuckingPercent", fallback.vadDuckingPercent).coerceIn(0, 70),
                micSyncDelayMs = j.optInt("micSyncDelayMs", fallback.micSyncDelayMs).coerceIn(0, 200),

                brbMuteMicToo = j.optBoolean("brbMuteMicToo", fallback.brbMuteMicToo),
                wifiLowLatencyLock = j.optBoolean("wifiLowLatencyLock", fallback.wifiLowLatencyLock),
                overlayPosition = j.optString("overlayPosition", fallback.overlayPosition),
                autoBlurKeyboard = j.optBoolean("autoBlurKeyboard", fallback.autoBlurKeyboard),
                simulcastSplitEncode = j.optBoolean("simulcastSplitEncode", fallback.simulcastSplitEncode),

                hudTelemetryEnabled = j.optBoolean("hudTelemetryEnabled", fallback.hudTelemetryEnabled),
                hudTelemetryCorner = sanitizeOverlayCorner(
                    j.optString("hudTelemetryCorner", fallback.hudTelemetryCorner),
                    fallback.hudTelemetryCorner
                ),
                qrOverlayEnabled = j.optBoolean("qrOverlayEnabled", fallback.qrOverlayEnabled),
                qrOverlayText = j.optString("qrOverlayText", fallback.qrOverlayText).trim().take(MAX_QR_OVERLAY_CHARS),
                qrOverlayCorner = sanitizeOverlayCorner(
                    j.optString("qrOverlayCorner", fallback.qrOverlayCorner),
                    fallback.qrOverlayCorner
                ),
                pngTuberEnabled = j.optBoolean("pngTuberEnabled", fallback.pngTuberEnabled),
                pngTuberCorner = sanitizeOverlayCorner(
                    j.optString("pngTuberCorner", fallback.pngTuberCorner),
                    fallback.pngTuberCorner
                ),
                pngTuberSizePercent = j.optInt("pngTuberSizePercent", fallback.pngTuberSizePercent).coerceIn(12, 30),

                shortsVerticalAnchor = sanitizeShortsVerticalAnchor(
                    j.optString("shortsVerticalAnchor", fallback.shortsVerticalAnchor)
                ),
                shortsMinimapLoupe = sanitizeShortsMinimapLoupe(
                    j.optString("shortsMinimapLoupe", fallback.shortsMinimapLoupe)
                ),
                shortsScoreLoupe = sanitizeShortsScoreLoupe(
                    j.optString("shortsScoreLoupe", fallback.shortsScoreLoupe)
                ),
                shortsHeaderLayout = j.optBoolean("shortsHeaderLayout", fallback.shortsHeaderLayout),
                shortsCaptureCooler = j.optBoolean("shortsCaptureCooler", fallback.shortsCaptureCooler)
            )
            enforcePlatformLimits(parsed).first
        } catch (_: Exception) {
            null
        }
    }

    fun hasCompletedInitialAutoScan(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_SCAN_DONE, false)
    }

    fun markInitialAutoScanDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_AUTO_SCAN_DONE, true).apply()
    }
}
