package com.solgaming.solstreamstudio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

data class HardwareEncoderReport(
    val codecKey: String,          // "HEVC", "H264", "AV1"
    val mimeType: String,
    val encoderName: String,
    val supports1440p60: Boolean,
    val supports1080p60: Boolean,
    val supportsCbr: Boolean,
    val supportsVbr: Boolean,
    val complexityMin: Int,
    val complexityMax: Int,
    val verifiedSurfaceConfigure: Boolean
)

data class DeviceScanSummary(
    val deviceModel: String,
    val socModel: String,
    val androidRelease: String,
    val sdkInt: Int,
    val hevcHardware: HardwareEncoderReport?,
    val h264Hardware: HardwareEncoderReport?,
    val av1Hardware: HardwareEncoderReport?,
    val recommendedCodec: String,
    val recommendedWidth: Int,
    val recommendedHeight: Int
) {
    fun findReport(codecKey: String): HardwareEncoderReport? = when (codecKey) {
        "HEVC" -> hevcHardware
        "AV1" -> av1Hardware
        else -> h264Hardware
    }

    fun toBadgeText(): String {
        val parts = mutableListOf<String>()
        hevcHardware?.let {
            val res = if (it.supports1440p60) "1440p60" else if (it.supports1080p60) "1080p60" else " Basic"
            parts.add("HW HEVC ($res • ${it.encoderName})")
        } ?: parts.add("HEVC: No HW")

        h264Hardware?.let {
            parts.add("HW H.264 (Ready)")
        }

        if (av1Hardware != null && (av1Hardware.supports1440p60 || av1Hardware.supports1080p60)) {
            parts.add("HW AV1 (${av1Hardware.encoderName})")
        } else {
            parts.add("AV1: Locked (SW-only blocked)")
        }
        return parts.joinToString("  |  ")
    }
}

data class MicInputOption(
    val deviceId: Int,
    val label: String,
    val isBluetooth: Boolean,
    val deviceInfo: AudioDeviceInfo?
)

object CodecCapabilityProbe {

    private var cachedScan: DeviceScanSummary? = null

    fun scanDevice(context: Context, forceRefresh: Boolean = false): DeviceScanSummary {
        if (!forceRefresh) cachedScan?.let { return it }

        val hevc = probeHardwareCodec("HEVC", MediaFormat.MIMETYPE_VIDEO_HEVC)
        val h264 = probeHardwareCodec("H264", MediaFormat.MIMETYPE_VIDEO_AVC)
        val av1 = probeHardwareCodec("AV1", "video/av01")

        val recCodec: String
        val recW: Int
        val recH: Int

        when {
            hevc != null && hevc.supports1440p60 && hevc.verifiedSurfaceConfigure -> {
                recCodec = "HEVC"; recW = 2560; recH = 1440
            }
            hevc != null && hevc.supports1080p60 && hevc.verifiedSurfaceConfigure -> {
                recCodec = "HEVC"; recW = 1920; recH = 1080
            }
            h264 != null && h264.supports1440p60 && h264.verifiedSurfaceConfigure -> {
                recCodec = "H264"; recW = 2560; recH = 1440
            }
            else -> {
                recCodec = "H264"; recW = 1920; recH = 1080
            }
        }

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim()
        } else {
            Build.HARDWARE
        }

        val summary = DeviceScanSummary(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            socModel = soc,
            androidRelease = Build.VERSION.RELEASE ?: "${Build.VERSION.SDK_INT}",
            sdkInt = Build.VERSION.SDK_INT,
            hevcHardware = hevc,
            h264Hardware = h264,
            av1Hardware = av1,
            recommendedCodec = recCodec,
            recommendedWidth = recW,
            recommendedHeight = recH
        )
        cachedScan = summary
        return summary
    }

    private fun isTrueHardwareEncoder(info: MediaCodecInfo): Boolean {
        if (!info.isEncoder) return false
        val nameLower = info.name.lowercase()
        if (nameLower.startsWith("omx.google.") ||
            nameLower.startsWith("c2.android.") ||
            nameLower.contains(".sw.") ||
            nameLower.endsWith(".sw")
        ) {
            return false
        }
        return info.isHardwareAccelerated && !info.isSoftwareOnly
    }

    private fun probeHardwareCodec(codecKey: String, mime: String): HardwareEncoderReport? {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        for (info in list) {
            if (!isTrueHardwareEncoder(info)) continue
            val types = info.supportedTypes
            if (types.none { it.equals(mime, ignoreCase = true) }) continue

            return try {
                val caps = info.getCapabilitiesForType(mime)
                val vCaps = caps.videoCapabilities ?: continue
                val eCaps = caps.encoderCapabilities

                var ok1440p60 = vCaps.areSizeAndRateSupported(2560, 1440, 60.0)
                var ok1080p60 = vCaps.areSizeAndRateSupported(1920, 1080, 60.0)

                val perfPoints = vCaps.supportedPerformancePoints
                if (!perfPoints.isNullOrEmpty()) {
                    val pp1440 = MediaCodecInfo.VideoCapabilities.PerformancePoint(2560, 1440, 60)
                    val pp1080 = MediaCodecInfo.VideoCapabilities.PerformancePoint(1920, 1080, 60)
                    ok1440p60 = ok1440p60 && perfPoints.any { it.covers(pp1440) }
                    ok1080p60 = ok1080p60 && perfPoints.any { it.covers(pp1080) }
                }

                val testW = if (ok1440p60) 2560 else 1920
                val testH = if (ok1440p60) 1440 else 1080
                val verified = dryRunHardwareSurfaceEncoder(info.name, mime, testW, testH)

                HardwareEncoderReport(
                    codecKey = codecKey,
                    mimeType = mime,
                    encoderName = info.name,
                    supports1440p60 = ok1440p60 && verified,
                    supports1080p60 = ok1080p60 && verified,
                    supportsCbr = eCaps?.isBitrateModeSupported(
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    ) ?: false,
                    supportsVbr = eCaps?.isBitrateModeSupported(
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                    ) ?: true,
                    complexityMin = eCaps?.complexityRange?.lower ?: 0,
                    complexityMax = eCaps?.complexityRange?.upper ?: 0,
                    verifiedSurfaceConfigure = verified
                )
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun dryRunHardwareSurfaceEncoder(
        encoderName: String,
        mime: String,
        width: Int,
        height: Int
    ): Boolean {
        var codec: MediaCodec? = null
        return try {
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 18_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 60)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            codec = MediaCodec.createByCodecName(encoderName)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            surface.release()
            true
        } catch (_: Exception) {
            false
        } finally {
            try {
                codec?.release()
            } catch (_: Exception) {
            }
        }
    }

    fun listAvailableMicInputs(context: Context): List<MicInputOption> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val result = mutableListOf(
            MicInputOption(
                deviceId = -1,
                label = "Auto (Android Default / Phone Mic when BT Calls OFF)",
                isBluetooth = false,
                deviceInfo = null
            )
        )

        var builtInIndex = 1
        for (dev in inputs) {
            if (!dev.isSource) continue
            val (label, isBt) = when (dev.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> {
                    val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dev.address else ""
                    val posHint = when {
                        addr.contains("bottom", true) || addr.contains("primary", true) -> "Bottom/Primary"
                        addr.contains("back", true) || addr.contains("top", true) -> "Top/Secondary"
                        else -> "Mic #$builtInIndex"
                    }
                    builtInIndex++
                    "Phone Built-in ($posHint)" to false
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                    "Bluetooth Hands-Free (${dev.productName})" to true
                }
                AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                    "Bluetooth LE Headset (${dev.productName})" to true
                }
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                    "Wired Headset Mic (${dev.productName})" to false
                }
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> {
                    "USB-C Mic (${dev.productName})" to false
                }
                else -> continue
            }
            result.add(MicInputOption(dev.id, label, isBt, dev))
        }
        return result
    }
}
