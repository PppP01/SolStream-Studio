package com.solgaming.solstreamstudio

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StreamSettings(
    val codec: String = "HEVC",          // "HEVC", "H264", "AV1"
    val width: Int = 2560,               // 2560 or 1920
    val height: Int = 1440,              // 1440 or 1080
    val fps: Int = 60,                   // 60 or 30
    val targetBitrateKbps: Int = 20000,  // 12000..24000
    val adaptiveBitrate: Boolean = true,
    val useVbr: Boolean = false,         // false = CBR (default), true = VBR test
    val isVertical: Boolean = false,     // false = 16:9, true = 9:16
    val landscapeFlip: Int = 0,          // 0 = Normal, 180 = Flipped for opposite USB-C side
    val framingMode: String = "FIT",     // "FIT" (HUD safe), "FILL", "SMART"
    val smartZoomPercent: Int = 108,     // 100..122%
    val gpuSharpen: Boolean = false,     // Off by default for thermal budget
    val audioBitrateKbps: Int = 160,     // 128, 160, 192, 256
    val micDeviceId: Int = -1,           // -1 = Auto / Built-in Phone Mic
    val micAudioSource: Int = 0,         // 0 = DEFAULT, 1 = MIC, 6 = VOICE_RECOGNITION
    val micSyncDelayMs: Int = 0,         // 0..200 ms lip-sync offset
    val voicePreset: String = "CLEAN",   // "RAW", "CLEAN", "STUDIO"
    val hpfCutoffHz: Int = 85,           // 0 = Off, 75, 85, 100
    val noiseGateEnabled: Boolean = true,
    val rnnoiseLevel: Int = 0,           // 0 = Off (default), 1 = Low, 2 = Medium
    val voiceEqEnabled: Boolean = false,
    val compressorEnabled: Boolean = true,
    val deEsserEnabled: Boolean = false,
    val micGainPercent: Int = 125,       // 50..250%
    val gameGainPercent: Int = 100,      // 0..125%
    val vadDuckingPercent: Int = 0,      // 0 = Off, 20 = -20%, 35 = -35%
    val wifiLowLatencyLock: Boolean = false,
    val brbMuteMicToo: Boolean = true
)

object KeystoreVault {
    private const val PREFS_NAME = "solstream_secure_prefs"
    private const val KEY_ALIAS = "SolStreamMasterAesKey_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private const val KEY_ENC_URL = "enc_rtmps_url"
    private const val KEY_ENC_STREAM = "enc_stream_key"
    const val DEFAULT_YOUTUBE_RTMPS = "rtmps://a.rtmp.youtube.com:443/live2"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$iv:$encrypted"
    }

    private fun decrypt(payload: String): String {
        if (payload.isBlank() || !payload.contains(":")) return ""
        return try {
            val parts = payload.split(":", limit = 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun saveEndpointAndKey(context: Context, rtmpsUrl: String, streamKey: String) {
        val cleanUrl = rtmpsUrl.trim().trimEnd('/').ifEmpty { DEFAULT_YOUTUBE_RTMPS }
        val cleanKey = streamKey.trim().trimStart('/')
        prefs(context).edit()
            .putString(KEY_ENC_URL, encrypt(cleanUrl))
            .putString(KEY_ENC_STREAM, encrypt(cleanKey))
            .apply()
    }

    fun getRtmpsUrl(context: Context): String {
        val raw = prefs(context).getString(KEY_ENC_URL, null) ?: return DEFAULT_YOUTUBE_RTMPS
        return decrypt(raw).ifBlank { DEFAULT_YOUTUBE_RTMPS }
    }

    fun getStreamKey(context: Context): String {
        val raw = prefs(context).getString(KEY_ENC_STREAM, null) ?: return ""
        return decrypt(raw)
    }

    fun buildFullStreamEndpoint(context: Context): String? {
        val key = getStreamKey(context).trim()
        if (key.isEmpty()) return null
        if (key.startsWith("rtmps://", ignoreCase = true) || key.startsWith("rtmp://", ignoreCase = true)) {
            return key
        }
        val base = getRtmpsUrl(context).trimEnd('/')
        return "$base/$key"
    }

    fun saveSettings(context: Context, s: StreamSettings) {
        prefs(context).edit()
            .putString("codec", s.codec)
            .putInt("width", s.width)
            .putInt("height", s.height)
            .putInt("fps", s.fps)
            .putInt("targetBitrateKbps", s.targetBitrateKbps)
            .putBoolean("adaptiveBitrate", s.adaptiveBitrate)
            .putBoolean("useVbr", s.useVbr)
            .putBoolean("isVertical", s.isVertical)
            .putInt("landscapeFlip", s.landscapeFlip)
            .putString("framingMode", s.framingMode)
            .putInt("smartZoomPercent", s.smartZoomPercent)
            .putBoolean("gpuSharpen", s.gpuSharpen)
            .putInt("audioBitrateKbps", s.audioBitrateKbps)
            .putInt("micDeviceId", s.micDeviceId)
            .putInt("micAudioSource", s.micAudioSource)
            .putInt("micSyncDelayMs", s.micSyncDelayMs)
            .putString("voicePreset", s.voicePreset)
            .putInt("hpfCutoffHz", s.hpfCutoffHz)
            .putBoolean("noiseGateEnabled", s.noiseGateEnabled)
            .putInt("rnnoiseLevel", s.rnnoiseLevel)
            .putBoolean("voiceEqEnabled", s.voiceEqEnabled)
            .putBoolean("compressorEnabled", s.compressorEnabled)
            .putBoolean("deEsserEnabled", s.deEsserEnabled)
            .putInt("micGainPercent", s.micGainPercent)
            .putInt("gameGainPercent", s.gameGainPercent)
            .putInt("vadDuckingPercent", s.vadDuckingPercent)
            .putBoolean("wifiLowLatencyLock", s.wifiLowLatencyLock)
            .putBoolean("brbMuteMicToo", s.brbMuteMicToo)
            .apply()
    }

    fun loadSettings(context: Context): StreamSettings {
        val p = prefs(context)
        return StreamSettings(
            codec = p.getString("codec", "HEVC") ?: "HEVC",
            width = p.getInt("width", 2560),
            height = p.getInt("height", 1440),
            fps = p.getInt("fps", 60),
            targetBitrateKbps = p.getInt("targetBitrateKbps", 20000),
            adaptiveBitrate = p.getBoolean("adaptiveBitrate", true),
            useVbr = p.getBoolean("useVbr", false),
            isVertical = p.getBoolean("isVertical", false),
            landscapeFlip = p.getInt("landscapeFlip", 0),
            framingMode = p.getString("framingMode", "FIT") ?: "FIT",
            smartZoomPercent = p.getInt("smartZoomPercent", 108),
            gpuSharpen = p.getBoolean("gpuSharpen", false),
            audioBitrateKbps = p.getInt("audioBitrateKbps", 160),
            micDeviceId = p.getInt("micDeviceId", -1),
            micAudioSource = p.getInt("micAudioSource", 0),
            micSyncDelayMs = p.getInt("micSyncDelayMs", 0),
            voicePreset = p.getString("voicePreset", "CLEAN") ?: "CLEAN",
            hpfCutoffHz = p.getInt("hpfCutoffHz", 85),
            noiseGateEnabled = p.getBoolean("noiseGateEnabled", true),
            rnnoiseLevel = p.getInt("rnnoiseLevel", 0),
            voiceEqEnabled = p.getBoolean("voiceEqEnabled", false),
            compressorEnabled = p.getBoolean("compressorEnabled", true),
            deEsserEnabled = p.getBoolean("deEsserEnabled", false),
            micGainPercent = p.getInt("micGainPercent", 125),
            gameGainPercent = p.getInt("gameGainPercent", 100),
            vadDuckingPercent = p.getInt("vadDuckingPercent", 0),
            wifiLowLatencyLock = p.getBoolean("wifiLowLatencyLock", false),
            brbMuteMicToo = p.getBoolean("brbMuteMicToo", true)
        )
    }

    fun hasCompletedInitialAutoScan(context: Context): Boolean =
        prefs(context).getBoolean("initial_hw_scan_done", false)

    fun markInitialAutoScanDone(context: Context) {
        prefs(context).edit().putBoolean("initial_hw_scan_done", true).apply()
    }
}
