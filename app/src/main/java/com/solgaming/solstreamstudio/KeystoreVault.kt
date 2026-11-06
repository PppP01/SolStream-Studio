/*
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

data class StreamSettings(
    // === Core Tier & Platform ===
    val deviceTier: String = "FLAGSHIP",          // "LITE", "BALANCED", "HIGH", "FLAGSHIP", "ULTRA"
    val platformPreset: String = "YOUTUBE",       // "YOUTUBE", "TWITCH", "KICK", "CUSTOM"
    val useShortsKeySlot: Boolean = false,        // Auto-uses Slot #2 Variable Key when 9:16 Shorts is active
    val simulcastTwitchEnabled: Boolean = false,  // Single-encode multi-destination packet fan-out
    val simulcastKickEnabled: Boolean = false,
    val dualFormatCompanionEnabled: Boolean = false, // Opt-in Flagship/Ultra 16:9 + 9:16 companion stream
    val dualFormatBitrateKbps: Int = 8000,

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
    val framingMode: String = "FIT",              // "FIT", "SMART", "FILL"
    val smartZoomPercent: Int = 108,              // 102..125

    // === OpenGL ES 3.0/2.0 Visual Engine, Stackable Filters & Scenes ===
    val glEngineMode: String = "AUTO",            // "AUTO", "ES30", "ES20"
    val upscalerMode: String = "BICUBIC",         // "BILINEAR", "BICUBIC", "LANCZOS"
    val hdr10Enabled: Boolean = false,            // True 10-Bit HLG10 HDR (when HW Main10 supported)
    val cinematicFilmic: Boolean = false,         // Stackable SDR ACES Filmic S-Curve
    val amoledVivid: Boolean = false,             // Stackable SDR Mid-Tone Saturation Pop
    val shadowBoost: Boolean = false,             // Stackable Dark-Corner Enemy Clarifier
    val colorTempFilter: String = "NEUTRAL",      // "NEUTRAL", "WARM", "COOL"
    val gpuSharpen: Boolean = false,              // Stackable Contrast-Adaptive Crisp Sharpen
    val roiMode: String = "AUTO",                 // "OFF", "AUTO", "CENTER", "HUD"
    val keyframeIntervalSec: Int = 2,             // 1 or 2 seconds
    val maxBFrames: Int = 0,                      // 0 (lowest latency), 1, or 2
    val qpMaxCap: Int = 0,                        // 0 = Auto, 36, 40, 44
    val lowLatencyCodecHint: Boolean = true,
    val shortsBackdropMode: String = "BLUR",      // "DARK", "BLUR", "POSTER"
    val overlayImageEnabled: Boolean = false,     // Zero-permission cached .PNG stream frame/logo
    val liveBannerEnabled: Boolean = false,       // Built-in GPU text banner
    val liveBannerText: String = "",
    val liveBannerPosition: String = "TOP",       // "TOP" or "BOTTOM"
    val minimapShieldEnabled: Boolean = false,    // Stream-sniper corner blur/cover
    val minimapShieldCorner: String = "TOP_RIGHT",// "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"

    // === 15-Stage Isolated 32-Bit Float Voice + Stereo Game Soundstage ===
    val micDeviceId: Int = -1,
    val micAudioSource: Int = 0,                  // 0 = AUTO, 1 = MIC, 6 = VOICE_RECOGNITION, 7 = VOICE_COMM
    val audioBitrateKbps: Int = 192,              // 64, 96, 128, 160, 192, 256, 320 kbps
    val voiceGenderProfile: String = "NEUTRAL",   // Pick-1: "NEUTRAL", "MALE", "FEMALE"
    val voicePreset: String = "CLEAN",            // "RAW", "CLEAN", "NEURAL", "STUDIO", "VELVET", "SOL_LUXE"
    val noiseSuppressMode: String = "RNNOISE",    // Pick-1: "OFF", "LIGHT", "RNNOISE", "HW_NS"
    val rnnoiseLevel: Int = 0,                    // Strength: 0 = Off, 1 = Gentle (65%), 2 = Natural (88%), 3 = Max (100%)
    val hpfCutoffHz: Int = 85,                    // 0 = Off, 75, 85, 100, 120 Hz
    val transientTamerEnabled: Boolean = true,    // Tames finger/grip thumps & harsh mic plosives
    val noiseGateEnabled: Boolean = true,         // Gentle breath/idle expander
    val voiceEqEnabled: Boolean = false,          // Gender-tuned 3-band dynamic vocal EQ
    val deEsserEnabled: Boolean = false,          // Gender-tuned split-band sibilance tamer
    val velvetWarmthEnabled: Boolean = false,     //Even-order tube warmth + 11.5 kHz silky air
    val compressorEnabled: Boolean = true,        // Broadcast 3:1 vocal leveling compressor
    val whisperLiftEnabled: Boolean = false,      // Upward low-level callout booster
    val slowAgcEnabled: Boolean = false,          // Speech-gated slow broadcast loudness ride
    val speakerAecEnabled: Boolean = false,       // Optional hardware AcousticEchoCanceler for speaker players
    val micGainPercent: Int = 125,                // 50..250%
    val gameGainPercent: Int = 100,               // 0..135%
    val gameCinemaWideEnabled: Boolean = false,   // Stackable Game Bus: Mid-Side 3D spatial widener
    val gameSubPunchEnabled: Boolean = false,     // Stackable Game Bus: +1.5 dB @ 65 Hz theater sub-punch
    val gameFootstepFocusEnabled: Boolean = false,// Stackable Game Bus: +1.8 dB @ 2.8 kHz footstep lift
    val gameBlastTamerEnabled: Boolean = false,   // Stackable Game Bus: tames harsh explosion spikes
    val vadDuckingPercent: Int = 0,               // 0, 20, 35, 50%
    val micSyncDelayMs: Int = 0,                  // 0..200 ms

    // === Survival & Safeguards ===
    val brbMuteMicToo: Boolean = true,
    val wifiLowLatencyLock: Boolean = true
)

object KeystoreVault {

    const val DEFAULT_YOUTUBE_RTMPS = "rtmps://a.rtmps.youtube.com:443/live2"
    const val DEFAULT_TWITCH_RTMPS = "rtmps://live.twitch.tv:443/app"
    const val DEFAULT_KICK_RTMPS = "rtmps://fa723fc1b171.global-contribute.live-video.net:443/app"

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

    @Volatile private var cachedSecurityLabel: String? = null

    fun normalizeIngestUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return DEFAULT_YOUTUBE_RTMPS

        // Auto-upgrade legacy a.rtmp.youtube.com over rtmps:// to official a.rtmps.youtube.com
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
        val cleanKey = streamKey.trim().trimStart('/')
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

    // === PRIMARY & SHORTS STREAM KEY VAULT ===
    fun saveEndpointAndKey(context: Context, rtmpsUrl: String, plainStreamKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normUrl = normalizeIngestUrl(rtmpsUrl)
        val cleanKey = plainStreamKey.trim().trimStart('/')
        val editor = prefs.edit().putString(KEY_INGEST_URL, normUrl)
        if (cleanKey.isEmpty()) {
            editor.remove(KEY_ENC_STREAM_KEY)
        } else {
            encryptSecret(context, cleanKey)?.let { enc ->
                editor.putString(KEY_ENC_STREAM_KEY, enc)
            }
        }
        editor.apply()
    }

    fun getRtmpsUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_INGEST_URL, DEFAULT_YOUTUBE_RTMPS) ?: DEFAULT_YOUTUBE_RTMPS
        return normalizeIngestUrl(raw)
    }

    fun getStreamKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blob = prefs.getString(KEY_ENC_STREAM_KEY, null) ?: return ""
        return decryptSecret(blob)
    }

    fun clearStreamKey(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ENC_STREAM_KEY)
            .apply()
    }

    fun saveShortsStreamKey(context: Context, shortsKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val clean = shortsKey.trim().trimStart('/')
        val edit = prefs.edit()
        if (clean.isEmpty()) {
            edit.remove(KEY_ENC_SHORTS_KEY)
        } else {
            encryptSecret(context, clean)?.let { edit.putString(KEY_ENC_SHORTS_KEY, it) }
        }
        edit.apply()
    }

    fun getShortsStreamKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blob = prefs.getString(KEY_ENC_SHORTS_KEY, null) ?: return ""
        return decryptSecret(blob)
    }

    // === MULTI-PLATFORM SIMULCAST VAULT (TWITCH & KICK) ===
    fun saveTwitchUrlAndKey(context: Context, url: String, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit().putString(KEY_TWITCH_URL, normalizeIngestUrl(url.ifBlank { DEFAULT_TWITCH_RTMPS }))
        val clean = key.trim().trimStart('/')
        if (clean.isEmpty()) edit.remove(KEY_ENC_TWITCH_KEY)
        else encryptSecret(context, clean)?.let { edit.putString(KEY_ENC_TWITCH_KEY, it) }
        edit.apply()
    }

    fun getTwitchUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TWITCH_URL, DEFAULT_TWITCH_RTMPS) ?: DEFAULT_TWITCH_RTMPS
    }

    fun getTwitchStreamKey(context: Context): String {
        val blob = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENC_TWITCH_KEY, null) ?: return ""
        return decryptSecret(blob)
    }

    fun saveKickUrlAndKey(context: Context, url: String, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit().putString(KEY_KICK_URL, normalizeIngestUrl(url.ifBlank { DEFAULT_KICK_RTMPS }))
        val clean = key.trim().trimStart('/')
        if (clean.isEmpty()) edit.remove(KEY_ENC_KICK_KEY)
        else encryptSecret(context, clean)?.let { edit.putString(KEY_ENC_KICK_KEY, it) }
        edit.apply()
    }

    fun getKickUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_KICK_URL, DEFAULT_KICK_RTMPS) ?: DEFAULT_KICK_RTMPS
    }

    fun getKickStreamKey(context: Context): String {
        val blob = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENC_KICK_KEY, null) ?: return ""
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

    fun buildCompanionVerticalEndpoint(context: Context): String? {
        val shortsKey = getShortsStreamKey(context)
        if (shortsKey.isBlank()) return null
        return joinUrlAndStreamKey(getRtmpsUrl(context), shortsKey)
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

    // === HARDWARE KEYSTORE AES-256-GCM ===
    fun getKeySecurityDescription(): String {
        cachedSecurityLabel?.let { return it }
        val desc = try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = (ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey) ?: return "Android Keystore Ready"
            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "Hardware-Backed (StrongBox)"
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "Hardware-Backed (TEE)"
                    else -> "OS Keystore Protected"
                }
            } else {
                @Suppress("DEPRECATION")
                if (info.isInsideSecureHardware) "Hardware-Backed (TEE)" else "OS Keystore Protected"
            }
        } catch (_: Exception) {
            "Android Keystore Protected"
        }
        cachedSecurityLabel = desc
        return desc
    }

    private fun getOrCreateAesKey(context: Context): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val hasStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

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
                // Fall back cleanly to TEE hardware keystore
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

    private fun encryptSecret(context: Context, plain: String): String? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey(context))
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val ct = Base64.encodeToString(
                cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8)),
                Base64.NO_WRAP
            )
            "$iv:$ct"
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptSecret(encodedBlob: String): String {
        return try {
            val parts = encodedBlob.split(':')
            if (parts.size != 2) return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = (ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey) ?: return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    // === SETTINGS PERSISTENCE & SECRET-FREE PRESET SHARE/IMPORT ===
    fun saveSettings(context: Context, s: StreamSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SETTINGS_JSON, exportPresetJson(s)).apply()
    }

    fun loadSettings(context: Context): StreamSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SETTINGS_JSON, null) ?: return StreamSettings()
        return importPresetJson(raw, StreamSettings()) ?: StreamSettings()
    }

    fun exportPresetJson(s: StreamSettings): String {
        return JSONObject().apply {
            put("solPresetSchema", 2)
            put("deviceTier", s.deviceTier)
            put("platformPreset", s.platformPreset)
            put("useShortsKeySlot", s.useShortsKeySlot)
            put("simulcastTwitchEnabled", s.simulcastTwitchEnabled)
            put("simulcastKickEnabled", s.simulcastKickEnabled)
            put("dualFormatCompanionEnabled", s.dualFormatCompanionEnabled)
            put("dualFormatBitrateKbps", s.dualFormatBitrateKbps)

            put("codec", s.codec)
            put("width", s.width)
            put("height", s.height)
            put("fps", s.fps)
            put("isVertical", s.isVertical)
            put("landscapeFlip", s.landscapeFlip)
            put("targetBitrateKbps", s.targetBitrateKbps)
            put("useVbr", s.useVbr)
            put("adaptiveBitrate", s.adaptiveBitrate)
            put("framingMode", s.framingMode)
            put("smartZoomPercent", s.smartZoomPercent)

            put("glEngineMode", s.glEngineMode)
            put("upscalerMode", s.upscalerMode)
            put("hdr10Enabled", s.hdr10Enabled)
            put("cinematicFilmic", s.cinematicFilmic)
            put("amoledVivid", s.amoledVivid)
            put("shadowBoost", s.shadowBoost)
            put("colorTempFilter", s.colorTempFilter)
            put("gpuSharpen", s.gpuSharpen)
            put("roiMode", s.roiMode)
            put("keyframeIntervalSec", s.keyframeIntervalSec)
            put("maxBFrames", s.maxBFrames)
            put("qpMaxCap", s.qpMaxCap)
            put("lowLatencyCodecHint", s.lowLatencyCodecHint)
            put("shortsBackdropMode", s.shortsBackdropMode)
            put("overlayImageEnabled", s.overlayImageEnabled)
            put("liveBannerEnabled", s.liveBannerEnabled)
            put("liveBannerText", s.liveBannerText)
            put("liveBannerPosition", s.liveBannerPosition)
            put("minimapShieldEnabled", s.minimapShieldEnabled)
            put("minimapShieldCorner", s.minimapShieldCorner)

            put("micDeviceId", s.micDeviceId)
            put("micAudioSource", s.micAudioSource)
            put("audioBitrateKbps", s.audioBitrateKbps)
            put("voiceGenderProfile", s.voiceGenderProfile)
            put("voicePreset", s.voicePreset)
            put("noiseSuppressMode", s.noiseSuppressMode)
            put("rnnoiseLevel", s.rnnoiseLevel)
            put("hpfCutoffHz", s.hpfCutoffHz)
            put("transientTamerEnabled", s.transientTamerEnabled)
            put("noiseGateEnabled", s.noiseGateEnabled)
            put("voiceEqEnabled", s.voiceEqEnabled)
            put("deEsserEnabled", s.deEsserEnabled)
            put("velvetWarmthEnabled", s.velvetWarmthEnabled)
            put("compressorEnabled", s.compressorEnabled)
            put("whisperLiftEnabled", s.whisperLiftEnabled)
            put("slowAgcEnabled", s.slowAgcEnabled)
            put("speakerAecEnabled", s.speakerAecEnabled)
            put("micGainPercent", s.micGainPercent)
            put("gameGainPercent", s.gameGainPercent)
            put("gameCinemaWideEnabled", s.gameCinemaWideEnabled)
            put("gameSubPunchEnabled", s.gameSubPunchEnabled)
            put("gameFootstepFocusEnabled", s.gameFootstepFocusEnabled)
            put("gameBlastTamerEnabled", s.gameBlastTamerEnabled)
            put("vadDuckingPercent", s.vadDuckingPercent)
            put("micSyncDelayMs", s.micSyncDelayMs)

            put("brbMuteMicToo", s.brbMuteMicToo)
            put("wifiLowLatencyLock", s.wifiLowLatencyLock)
        }.toString()
    }

    fun importPresetJson(rawJson: String, fallback: StreamSettings = StreamSettings()): StreamSettings? {
        return try {
            val j = JSONObject(rawJson.trim())
            StreamSettings(
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
                smartZoomPercent = j.optInt("smartZoomPercent", fallback.smartZoomPercent).coerceIn(100, 125),

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
                maxBFrames = j.optInt("maxBFrames", fallback.maxBFrames).coerceIn(0, 2),
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
                voiceGenderProfile = j.optString("voiceGenderProfile", fallback.voiceGenderProfile),
                voicePreset = j.optString("voicePreset", fallback.voicePreset),
                noiseSuppressMode = j.optString("noiseSuppressMode", fallback.noiseSuppressMode),
                rnnoiseLevel = j.optInt("rnnoiseLevel", fallback.rnnoiseLevel).coerceIn(0, 3),
                hpfCutoffHz = j.optInt("hpfCutoffHz", fallback.hpfCutoffHz).coerceIn(0, 160),
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
                gameGainPercent = j.optInt("gameGainPercent", fallback.gameGainPercent).coerceIn(0, 135),
                gameCinemaWideEnabled = j.optBoolean("gameCinemaWideEnabled", fallback.gameCinemaWideEnabled),
                gameSubPunchEnabled = j.optBoolean("gameSubPunchEnabled", fallback.gameSubPunchEnabled),
                gameFootstepFocusEnabled = j.optBoolean("gameFootstepFocusEnabled", fallback.gameFootstepFocusEnabled),
                gameBlastTamerEnabled = j.optBoolean("gameBlastTamerEnabled", fallback.gameBlastTamerEnabled),
                vadDuckingPercent = j.optInt("vadDuckingPercent", fallback.vadDuckingPercent).coerceIn(0, 60),
                micSyncDelayMs = j.optInt("micSyncDelayMs", fallback.micSyncDelayMs).coerceIn(0, 200),

                brbMuteMicToo = j.optBoolean("brbMuteMicToo", fallback.brbMuteMicToo),
                wifiLowLatencyLock = j.optBoolean("wifiLowLatencyLock", fallback.wifiLowLatencyLock)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun hasCompletedInitialAutoScan(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SCAN_DONE, false)
    }

    fun markInitialAutoScanDone(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SCAN_DONE, true)
            .apply()
    }
}
