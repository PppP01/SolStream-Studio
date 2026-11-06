package com.solgaming.solstreamstudio

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var appVersionName: String
    private lateinit var settings: StreamSettings
    private lateinit var scanSummary: DeviceScanSummary
    private var micOptions: List<MicInputOption> = emptyList()
    private var showStreamKeyPlain = false

    private val micTestBusy = AtomicBoolean(false)
    private val netTestBusy = AtomicBoolean(false)
    private val hwScanBusy = AtomicBoolean(false)

    // UI References for dynamic refresh
    private lateinit var tvHeaderSubtitle: TextView
    private lateinit var tvLiveBanner: TextView
    private lateinit var btnReapproveCapture: Button
    private lateinit var tvPreflightStatus: TextView
    private lateinit var tvRestrictedHint: TextView
    private lateinit var tvVaultSecurityLabel: TextView
    private lateinit var etRtmpsUrl: EditText
    private lateinit var etStreamKey: EditText
    private lateinit var btnShowHideKey: Button
    private lateinit var btnNetTest: Button
    private lateinit var tvNetTestResult: TextView

    private lateinit var btnCodec: Button
    private lateinit var btnResolution: Button
    private lateinit var btnFps: Button
    private lateinit var btnOrientationFlip: Button
    private lateinit var btnBitrate: Button
    private lateinit var btnRateControl: Button
    private lateinit var btnAbr: Button
    private lateinit var btnFraming: Button
    private lateinit var btnSmartZoom: Button
    private lateinit var btnSharpen: Button

    private lateinit var btnMicDevice: Button
    private lateinit var btnMicSource: Button
    private lateinit var btnAudioBitrate: Button
    private lateinit var btnVoicePreset: Button
    private lateinit var btnHpf: Button
    private lateinit var btnNoiseSuppress: Button
    private lateinit var btnGateComp: Button
    private lateinit var btnEqDeEss: Button
    private lateinit var btnMicGain: Button
    private lateinit var btnGameGain: Button
    private lateinit var btnDucking: Button
    private lateinit var btnMicDelay: Button
    private lateinit var btnExtraToggles: Button
    private lateinit var tvMicTestStatus: TextView

    private lateinit var btnGoLive: Button
    private lateinit var btnGlipBench: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshAllUi()
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val svcIntent = Intent(this, SolStreamService::class.java).apply {
                action = SolStreamService.ACTION_START_STREAM
                putExtra(SolStreamService.EXTRA_RESULT_CODE, res.resultCode)
                putExtra(SolStreamService.EXTRA_RESULT_DATA, res.data)
            }
            ContextCompat.startForegroundService(this, svcIntent)
        } else {
            Toast.makeText(this, "Screen capture cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticsLogger.installPrivacySafeCrashHandler(this)
        appVersionName = readAppVersionName()

        scanSummary = CodecCapabilityProbe.scanDevice(this)
        settings = KeystoreVault.loadSettings(this)

        // First-launch Auto Hardware Scanner recommendation (user retains 100% manual control)
        if (!KeystoreVault.hasCompletedInitialAutoScan(this)) {
            settings = settings.copy(
                codec = scanSummary.recommendedCodec,
                width = scanSummary.recommendedWidth,
                height = scanSummary.recommendedHeight
            )
            KeystoreVault.saveSettings(this, settings)
            KeystoreVault.markInitialAutoScanDone(this)
        }

        micOptions = CodecCapabilityProbe.listAvailableMicInputs(this)
        setContentView(buildDashboardUi())

        SolStreamService.onStateChangedListener = {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    settings = KeystoreVault.loadSettings(this)
                    refreshAllUi()
                }
            }
        }
        refreshAllUi()
    }

    override fun onResume() {
        super.onResume()
        settings = KeystoreVault.loadSettings(this)
        micOptions = CodecCapabilityProbe.listAvailableMicInputs(this)
        etRtmpsUrl.setText(KeystoreVault.getRtmpsUrl(this))
        refreshAllUi()
    }

    override fun onDestroy() {
        if (SolStreamService.onStateChangedListener != null) {
            SolStreamService.onStateChangedListener = null
        }
        super.onDestroy()
    }

    private fun readAppVersionName(): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            info.versionName?.removeSuffix("-debug") ?: "1.0.2"
        } catch (_: Exception) {
            "1.0.2"
        }
    }

    private fun buildDashboardUi(): View {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F17"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
        }

        // Safe Edge-to-Edge WindowInsets for One UI camera cutout & gesture bar
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                max(dp(16), bars.left + dp(12)),
                max(dp(16), bars.top + dp(10)),
                max(dp(16), bars.right + dp(12)),
                max(dp(24), bars.bottom + dp(16))
            )
            insets
        }

        // === HEADER ===
        val title = TextView(this).apply {
            text = "SOLSTREAM STUDIO  v$appVersionName"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
        }
        tvHeaderSubtitle = TextView(this).apply {
            text = "${scanSummary.deviceModel} (${scanSummary.socModel}) • Android ${scanSummary.androidRelease}\n${scanSummary.toBadgeText()}"
            setTextColor(Color.parseColor("#90A4AE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(4), 0, dp(10))
        }

        tvLiveBanner = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = cardDrawable("#102027", "#00E676")
        }

        btnReapproveCapture = actionButton("RE-APPROVE SCREEN CAPTURE TO RESUME VIDEO", "#E65100") {
            if (SolStreamService.isBroadcastLive) {
                val liveSvc = SolStreamService.instance
                if (liveSvc != null) {
                    liveSvc.stopLiveBroadcast("Re-approving screen capture")
                } else {
                    startService(Intent(this, SolStreamService::class.java).apply {
                        action = SolStreamService.ACTION_STOP_STREAM
                    })
                }
            }
            requestScreenCaptureAndGoLive()
        }.apply { visibility = View.GONE }

        container.addView(title)
        container.addView(tvHeaderSubtitle)
        container.addView(tvLiveBanner)
        container.addView(btnReapproveCapture)

        // === CARD 1: PRE-FLIGHT PERMISSIONS & EXACT 1-2-3-4 RESTRICTED UNLOCK FLOW ===
        val cardPreflight = sectionCard("1. Pre-Flight Readiness & Direct Settings Setup")
        tvPreflightStatus = bodyText("")
        cardPreflight.addView(tvPreflightStatus)

        tvRestrictedHint = TextView(this).apply {
            text = "Android 13–17 Tip (Why ⋮ 3-dots hide at first): Tap '3. Tap Accessibility 1st' and tap greyed-out SolStream Studio once -> press OK -> then tap '4. Unlock Restricted (⋮)' so the top-right ⋮ menu appears!"
            setTextColor(Color.parseColor("#FFD54F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.2f)
            setPadding(0, dp(2), 0, dp(6))
        }
        cardPreflight.addView(tvRestrictedHint)

        val rowPerms = buttonRow(
            actionButton("1. Grant Mic & Notif", "#1E3A5F") { requestRuntimePermissions() },
            actionButton("2. Unrestricted Batt", "#1E3A5F") { openBatteryUnrestricted() }
        )
        // Exact order Android 13-17 enforces: trigger Accessibility prompt FIRST, then App Info ⋮ SECOND
        val rowRestrictedAndAcc = buttonRow(
            actionButton("3. Tap Accessibility 1st", "#00695C") { openAccessibilitySettingsDirect() },
            actionButton("4. Unlock Restricted (⋮)", "#283593") { openAppInfoForRestrictedSettings() }
        )
        val btnSetupGuide = actionButton("Samsung One UI & Neckband Audio Setup Guide", "#37474F") {
            showSamsungSetupGuide()
        }
        cardPreflight.addView(rowPerms)
        cardPreflight.addView(rowRestrictedAndAcc)
        cardPreflight.addView(btnSetupGuide)
        container.addView(cardPreflight)

        // === CARD 2: YOUTUBE RTMPS & ENCRYPTED STREAM KEY VAULT ===
        val cardStream = sectionCard("2. YouTube RTMPS & Encrypted Stream Key Vault")
        cardStream.addView(labelText("RTMPS Ingest Server URL (works with or without :443):"))
        etRtmpsUrl = styledEditText(KeystoreVault.getRtmpsUrl(this), isPassword = false)
        cardStream.addView(etRtmpsUrl)

        tvVaultSecurityLabel = labelText("Stream Key (AES-256-GCM • ${KeystoreVault.getKeySecurityDescription()}):")
        cardStream.addView(tvVaultSecurityLabel)
        etStreamKey = styledEditText(KeystoreVault.getStreamKey(this), isPassword = true).apply {
            hint = "Paste or type your YouTube Stream Key"
        }
        cardStream.addView(etStreamKey)

        btnShowHideKey = actionButton("Show Key", "#263238") { toggleKeyVisibility() }
        val rowKeyBtnsTop = buttonRow(
            actionButton("Paste Key", "#263238") { pasteStreamKeyFromClipboard() },
            btnShowHideKey
        )
        val rowKeyBtnsBottom = buttonRow(
            actionButton("Clear Key", "#455A64") { clearStreamKeyInput() },
            actionButton("Save Vault", "#00695C") { saveVaultInputs(showToast = true) }
        )
        cardStream.addView(rowKeyBtnsTop)
        cardStream.addView(rowKeyBtnsBottom)

        tvNetTestResult = bodyText(
            "Tip: Create a reusable Custom 1440p60 Stream Key in YouTube Studio (Low-latency ON, Auto-stop OFF, Auto Dual-Format ON)."
        )
        btnNetTest = actionButton("Test Internet RTMPS Ping & Upload", "#1565C0") {
            runRtmpsNetworkCheck()
        }
        val rowNetBtns = buttonRow(
            btnNetTest,
            actionButton("Reset Default URL", "#37474F") {
                etRtmpsUrl.setText(KeystoreVault.DEFAULT_YOUTUBE_RTMPS)
                saveVaultInputs(showToast = true)
            }
        )
        cardStream.addView(rowNetBtns)
        cardStream.addView(tvNetTestResult)
        container.addView(cardStream)

        // === CARD 3: VIDEO, 120->60 FPS PACING & CODM HUD FRAMING ===
        val cardVideo = sectionCard("3. Video Encoder, Bitrate & CODM HUD Framing")
        cardVideo.addView(
            bodyText("CODM stays at 120 FPS on your screen while the hardware encoder paces YouTube output cleanly at 60 or 30 FPS. Keep GPU Sharpen OFF for the lowest phone heat during marathon streams.")
        )

        btnCodec = actionButton("", "#1C2836") { cycleCodec() }
        btnResolution = actionButton("", "#1C2836") { cycleResolution() }
        btnFps = actionButton("", "#1C2836") {
            updateSetting(
                settings.copy(fps = if (settings.fps == 60) 30 else 60),
                requiresRestartIfLive = true
            )
        }
        btnOrientationFlip = actionButton("", "#1C2836") { cycleOrientationAndFlip() }
        btnBitrate = actionButton("", "#1B5E20") { cycleBitrate() }
        btnRateControl = actionButton("", "#1C2836") {
            updateSetting(
                settings.copy(useVbr = !settings.useVbr),
                requiresRestartIfLive = true
            )
        }
        btnAbr = actionButton("", "#1C2836") {
            updateSetting(settings.copy(adaptiveBitrate = !settings.adaptiveBitrate))
        }
        btnFraming = actionButton("", "#283593") { cycleFraming() }
        btnSmartZoom = actionButton("", "#1C2836") { cycleSmartZoom() }
        btnSharpen = actionButton("", "#1C2836") {
            val nextSharp = !settings.gpuSharpen
            updateSetting(
                newSettings = settings.copy(gpuSharpen = nextSharp),
                userToggledSharp = true
            )
        }

        cardVideo.addView(buttonRow(btnCodec, btnResolution))
        cardVideo.addView(buttonRow(btnFps, btnOrientationFlip))
        cardVideo.addView(buttonRow(btnBitrate, btnRateControl))
        cardVideo.addView(btnAbr)
        cardVideo.addView(buttonRow(btnFraming, btnSmartZoom))
        cardVideo.addView(btnSharpen)
        container.addView(cardVideo)

        // === CARD 4: 32-BIT FLOAT STUDIO AUDIO & MIC TEST STUDIO ===
        val cardAudio = sectionCard("4. 32-Bit Float Studio Audio & 5s Mic Test Studio")
        cardAudio.addView(
            bodyText("Mic filters process ONLY your voice before mixing with stereo CODM gameplay. Nothing alters what you hear in your earphones.")
        )

        btnMicDevice = actionButton("", "#1C2836") { cycleMicInputDevice() }
        btnMicSource = actionButton("", "#1C2836") { cycleMicSourceMode() }
        btnAudioBitrate = actionButton("", "#1C2836") { cycleAudioBitrate() }
        btnVoicePreset = actionButton("", "#283593") { cycleVoicePreset() }
        btnHpf = actionButton("", "#1C2836") { cycleHpf() }
        btnNoiseSuppress = actionButton("", "#1C2836") {
            updateSetting(settings.copy(rnnoiseLevel = (settings.rnnoiseLevel + 1) % 3))
        }
        btnGateComp = actionButton("", "#1C2836") { cycleGateAndComp() }
        btnEqDeEss = actionButton("", "#1C2836") { cycleEqAndDeEss() }
        btnMicGain = actionButton("", "#1C2836") { cycleMicGain() }
        btnGameGain = actionButton("", "#1C2836") { cycleGameGain() }
        btnDucking = actionButton("", "#1C2836") { cycleDucking() }
        btnMicDelay = actionButton("", "#1C2836") { cycleMicSyncDelay() }
        btnExtraToggles = actionButton("", "#1C2836") { cycleExtraSafeguards() }

        cardAudio.addView(btnMicDevice)
        cardAudio.addView(buttonRow(btnMicSource, btnAudioBitrate))
        cardAudio.addView(buttonRow(btnVoicePreset, btnNoiseSuppress))
        cardAudio.addView(buttonRow(btnHpf, btnGateComp))
        cardAudio.addView(btnEqDeEss)
        cardAudio.addView(buttonRow(btnMicGain, btnGameGain))
        cardAudio.addView(buttonRow(btnDucking, btnMicDelay))
        cardAudio.addView(btnExtraToggles)

        tvMicTestStatus = bodyText("Mic Test Studio: Hold phone in your normal 2-hand CODM grip to test voice clarity & noise floor.")
        val rowMicTest = buttonRow(
            actionButton("Record 5s Test", "#00695C") { runFiveSecMicTest() },
            actionButton("Play Test", "#1565C0") { playMicTestSample() },
            actionButton("Delete", "#37474F") {
                if (!micTestBusy.get()) {
                    SolDualAudioEngine.deleteMicTestFile(this)
                    tvMicTestStatus.text = "Saved test sample deleted."
                }
            }
        )
        cardAudio.addView(tvMicTestStatus)
        cardAudio.addView(rowMicTest)
        container.addView(cardAudio)

        // === CARD 5: GO LIVE, SESSION REPORT, GLIP BENCHMARK & UPDATES ===
        val cardLaunch = sectionCard("5. Launch Broadcast, Diagnostics & Updates")

        btnGoLive = actionButton("GO LIVE — START YOUTUBE STREAM", "#00C853") {
            onGoLiveClicked()
        }.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(52)
        }
        cardLaunch.addView(btnGoLive)

        btnGlipBench = actionButton("Start Glip Benchmark", "#37474F") {
            toggleGlipBenchmark()
        }

        val rowDiag = buttonRow(
            actionButton("Session Notes & Report", "#1565C0") { showDiagnosticsDialog() },
            btnGlipBench
        )
        val rowUpdate = buttonRow(
            actionButton("Refresh Mics / HW Scan", "#263238") { refreshHardwareAndMicsAsync() },
            actionButton("Check GitHub Update", "#263238") { checkGitHubReleaseUpdate() }
        )

        cardLaunch.addView(rowDiag)
        cardLaunch.addView(rowUpdate)
        container.addView(cardLaunch)

        // === FOOTER ===
        val footer = TextView(this).apply {
            text = "Made by Sol (@Sol_CODM) • Open-Source Zero-Ad Broadcast Studio"
            setTextColor(Color.parseColor("#78909C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        container.addView(footer)

        rootScroll.addView(container)
        return rootScroll
    }

    private fun refreshHardwareAndMicsAsync() {
        if (!hwScanBusy.compareAndSet(false, true)) return
        Toast.makeText(this, "Scanning hardware encoders & microphones...", Toast.LENGTH_SHORT).show()
        Thread({
            try {
                val freshScan = CodecCapabilityProbe.scanDevice(applicationContext, forceRefresh = true)
                val freshMics = CodecCapabilityProbe.listAvailableMicInputs(applicationContext)
                runOnUiThread {
                    hwScanBusy.set(false)
                    if (!isFinishing && !isDestroyed) {
                        scanSummary = freshScan
                        micOptions = freshMics
                        tvHeaderSubtitle.text =
                            "${scanSummary.deviceModel} (${scanSummary.socModel}) • Android ${scanSummary.androidRelease}\n${scanSummary.toBadgeText()}"
                        refreshAllUi()
                        Toast.makeText(this, "Hardware & mic list refreshed!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                hwScanBusy.set(false)
            }
        }, "SolAsyncHwScan").start()
    }

    private fun refreshAllUi() {
        val micGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val battUnrestricted = pm.isIgnoringBatteryOptimizations(packageName)
        val accEnabled = isAccessibilityServiceEnabled()
        val isLive = SolStreamService.isBroadcastLive

        tvLiveBanner.text = when {
            SolStreamService.needsProjectionReapproval ->
                "STATUS: Screen capture paused by Android (lock/system). Tap orange button below to re-approve!"
            isLive ->
                "STATUS: ${SolStreamService.connectionStateLabel} • Live controls below & in-game Ghost Pill sync immediately!"
            SolStreamService.isGlipBenchmarkRunning ->
                "STATUS: Passive Glip Benchmark Recording Thermal & RAM..."
            else ->
                "STATUS: Ready in Pre-Stream Studio (0% background load when idle)"
        }
        btnReapproveCapture.visibility = if (SolStreamService.needsProjectionReapproval) View.VISIBLE else View.GONE

        tvPreflightStatus.text =
            "• Mic Permission: ${okWait(micGranted)}   • Notifications: ${okWait(notifGranted)}\n" +
                "• Battery Unrestricted: ${okWait(battUnrestricted)}   • Accessibility Service: ${okWait(accEnabled)}"
        tvRestrictedHint.visibility = if (accEnabled) View.GONE else View.VISIBLE

        tvVaultSecurityLabel.text = "Stream Key (AES-256-GCM • ${KeystoreVault.getKeySecurityDescription()}):"
        btnShowHideKey.text = if (showStreamKeyPlain) "Hide Key" else "Show Key"

        val restartSuffix = if (isLive) " [Next]" else ""
        val av1Available = scanSummary.av1Hardware != null &&
            (scanSummary.av1Hardware?.supports1440p60 == true || scanSummary.av1Hardware?.supports1080p60 == true)
        btnCodec.text = "Codec: ${settings.codec}" +
            (if (!av1Available && settings.codec != "AV1") " (AV1 SW Blocked)" else "") +
            restartSuffix
        btnResolution.text = "Res: ${if (max(settings.width, settings.height) >= 2560) "1440p" else "1080p"}$restartSuffix"
        btnFps.text = "Stream: ${settings.fps} FPS$restartSuffix"
        val orient = if (settings.isVertical) {
            "9:16 Shorts$restartSuffix"
        } else {
            "16:9 Wide (${settings.landscapeFlip}°)"
        }
        btnOrientationFlip.text = orient
        btnBitrate.text = "Target: ${settings.targetBitrateKbps / 1000} Mbps"
        btnRateControl.text = "Rate: ${if (settings.useVbr) "VBR (Test)" else "CBR (Rec)"}$restartSuffix"
        btnAbr.text = "Queue-Aware Auto Bitrate (Ping Guard): ${if (settings.adaptiveBitrate) "ON" else "OFF"}"
        btnFraming.text = "Framing: ${settings.framingMode}"
        btnSmartZoom.text = "Smart Zoom: ${settings.smartZoomPercent}%"
        btnSharpen.text = if (settings.gpuSharpen) {
            "GPU Crisp Sharpen: ON (Turn OFF to reduce phone heat)"
        } else {
            "GPU Crisp Sharpen: OFF (Recommended — Coolest Temp)"
        }

        val selectedMicLabel = micOptions.firstOrNull { it.deviceId == settings.micDeviceId }?.label
            ?: micOptions.firstOrNull()?.label ?: "Auto Phone Mic"
        btnMicDevice.text = "Mic Input: $selectedMicLabel"
        val srcName = when (settings.micAudioSource) {
            1 -> "MIC (Raw)"
            6 -> "VOICE_REC (BT/Headset)"
            else -> "AUTO (Glip Smart)"
        }
        btnMicSource.text = "Source: $srcName"
        btnAudioBitrate.text = "AAC: ${settings.audioBitrateKbps} kbps$restartSuffix"
        btnVoicePreset.text = "Voice: ${settings.voicePreset}"
        btnHpf.text = if (settings.hpfCutoffHz == 0) "HPF Rumble: OFF" else "HPF: ${settings.hpfCutoffHz} Hz"
        val nsText = when (settings.rnnoiseLevel) { 1 -> "LOW"; 2 -> "MED"; else -> "OFF" }
        btnNoiseSuppress.text = "Light Noise Cut: $nsText"
        btnGateComp.text = "Gate:${onOff(settings.noiseGateEnabled)} | Comp:${onOff(settings.compressorEnabled)}"
        btnEqDeEss.text = "Podcast Voice EQ: ${onOff(settings.voiceEqEnabled)} | De-Esser: ${onOff(settings.deEsserEnabled)}"
        btnMicGain.text = "Mic Boost: ${settings.micGainPercent}%"
        btnGameGain.text = "Game Mix: ${settings.gameGainPercent}%"
        btnDucking.text = if (settings.vadDuckingPercent == 0) "Ducking: OFF" else "Ducking: -${settings.vadDuckingPercent}%"
        btnMicDelay.text = "Mic Sync: +${settings.micSyncDelayMs} ms"
        btnExtraToggles.text = "BRB Auto-Mute Mic: ${onOff(settings.brbMuteMicToo)} | Low-Lat Wi-Fi Lock: ${onOff(settings.wifiLowLatencyLock)}"

        if (isLive) {
            btnGoLive.text = "STOP LIVE STREAM"
            btnGoLive.background = roundedDrawable("#D50000", "#FF5252")
        } else {
            btnGoLive.text = "GO LIVE — START YOUTUBE STREAM"
            btnGoLive.background = roundedDrawable("#00897B", "#00E676")
        }

        btnGlipBench.text = if (SolStreamService.isGlipBenchmarkRunning) {
            "Stop & Save Glip Benchmark"
        } else {
            "Start Glip Benchmark"
        }
    }

    private fun updateSetting(
        newSettings: StreamSettings,
        requiresRestartIfLive: Boolean = false,
        userToggledSharp: Boolean = false
    ) {
        settings = newSettings
        KeystoreVault.saveSettings(this, settings)
        if (SolStreamService.isBroadcastLive) {
            SolStreamService.instance?.applyDashboardSettingsLive(
                newSettings = settings,
                userToggledSharp = userToggledSharp
            )
            if (requiresRestartIfLive) {
                Toast.makeText(
                    this,
                    "Saved for next stream! (Encoder Codec/Res/FPS/Rate/AAC require stream restart.)",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (userToggledSharp && settings.gpuSharpen) {
                Toast.makeText(
                    this,
                    "GPU Sharp ON live — turn OFF anytime to reduce phone heat.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (userToggledSharp && settings.gpuSharpen) {
            Toast.makeText(
                this,
                "Tip: Turn GPU Sharp OFF if you want cooler phone temps on 3+ hour streams.",
                Toast.LENGTH_SHORT
            ).show()
        }
        refreshAllUi()
    }

    // === CYCLERS ===
    private fun cycleCodec() {
        val av1Ok = scanSummary.av1Hardware != null &&
            (scanSummary.av1Hardware?.supports1440p60 == true || scanSummary.av1Hardware?.supports1080p60 == true)
        val order = if (av1Ok) listOf("HEVC", "H264", "AV1") else listOf("HEVC", "H264")
        val next = order[(order.indexOf(settings.codec) + 1) % order.size]
        updateSetting(settings.copy(codec = next), requiresRestartIfLive = true)
    }

    private fun cycleResolution() {
        val is1440 = max(settings.width, settings.height) >= 2560
        val updated = if (is1440) {
            settings.copy(width = 1920, height = 1080)
        } else {
            settings.copy(width = 2560, height = 1440)
        }
        updateSetting(updated, requiresRestartIfLive = true)
    }

    private fun cycleOrientationAndFlip() {
        if (SolStreamService.isBroadcastLive && !settings.isVertical) {
            // While live in 16:9 landscape, flip cleanly between 0° and 180° for USB-C cable comfort
            val nextFlip = if (settings.landscapeFlip == 0) 180 else 0
            updateSetting(settings.copy(landscapeFlip = nextFlip), requiresRestartIfLive = false)
            return
        }
        when {
            !settings.isVertical && settings.landscapeFlip == 0 ->
                updateSetting(settings.copy(isVertical = false, landscapeFlip = 180))
            !settings.isVertical && settings.landscapeFlip == 180 ->
                updateSetting(settings.copy(isVertical = true, landscapeFlip = 0), requiresRestartIfLive = true)
            else ->
                updateSetting(settings.copy(isVertical = false, landscapeFlip = 0), requiresRestartIfLive = true)
        }
    }

    private fun cycleBitrate() {
        val rates = listOf(12000, 16000, 18000, 20000, 22000, 24000)
        val next = rates[(rates.indexOf(settings.targetBitrateKbps) + 1) % rates.size]
        updateSetting(settings.copy(targetBitrateKbps = next))
    }

    private fun cycleFraming() {
        val next = when (settings.framingMode) {
            "FIT" -> "SMART"
            "SMART" -> "FILL"
            else -> "FIT"
        }
        updateSetting(settings.copy(framingMode = next))
    }

    private fun cycleSmartZoom() {
        val zooms = listOf(104, 108, 112, 116)
        val next = zooms[(zooms.indexOf(settings.smartZoomPercent) + 1) % zooms.size]
        updateSetting(settings.copy(framingMode = "SMART", smartZoomPercent = next))
    }

    private fun cycleMicInputDevice() {
        micOptions = CodecCapabilityProbe.listAvailableMicInputs(this)
        if (micOptions.isEmpty()) return
        val curIdx = micOptions.indexOfFirst { it.deviceId == settings.micDeviceId }
        val nextOpt = micOptions[(curIdx + 1) % micOptions.size]
        updateSetting(settings.copy(micDeviceId = nextOpt.deviceId))
    }

    private fun cycleMicSourceMode() {
        val next = when (settings.micAudioSource) {
            0 -> 1
            1 -> 6
            else -> 0
        }
        updateSetting(settings.copy(micAudioSource = next))
    }

    private fun cycleAudioBitrate() {
        val rates = listOf(128, 160, 192, 256, 320)
        val next = rates[(rates.indexOf(settings.audioBitrateKbps) + 1) % rates.size]
        updateSetting(settings.copy(audioBitrateKbps = next), requiresRestartIfLive = true)
    }

    private fun cycleVoicePreset() {
        val next = when (settings.voicePreset) {
            "RAW" -> "CLEAN"
            "CLEAN" -> "STUDIO"
            else -> "RAW"
        }
        val studioOn = (next == "STUDIO")
        updateSetting(
            settings.copy(
                voicePreset = next,
                voiceEqEnabled = if (studioOn) true else settings.voiceEqEnabled,
                deEsserEnabled = if (studioOn) true else settings.deEsserEnabled
            )
        )
    }

    private fun cycleHpf() {
        val next = when (settings.hpfCutoffHz) {
            0 -> 75; 75 -> 85; 85 -> 100; else -> 0
        }
        updateSetting(settings.copy(hpfCutoffHz = next))
    }

    private fun cycleGateAndComp() {
        val (g, c) = when {
            settings.noiseGateEnabled && settings.compressorEnabled -> true to false
            settings.noiseGateEnabled && !settings.compressorEnabled -> false to true
            !settings.noiseGateEnabled && settings.compressorEnabled -> false to false
            else -> true to true
        }
        updateSetting(settings.copy(noiseGateEnabled = g, compressorEnabled = c))
    }

    private fun cycleEqAndDeEss() {
        val (eq, de) = when {
            !settings.voiceEqEnabled && !settings.deEsserEnabled -> true to false
            settings.voiceEqEnabled && !settings.deEsserEnabled -> true to true
            settings.voiceEqEnabled && settings.deEsserEnabled -> false to true
            else -> false to false
        }
        updateSetting(settings.copy(voiceEqEnabled = eq, deEsserEnabled = de))
    }

    private fun cycleMicGain() {
        val steps = listOf(75, 100, 125, 150, 180, 220)
        val next = steps[(steps.indexOf(settings.micGainPercent) + 1) % steps.size]
        updateSetting(settings.copy(micGainPercent = next))
    }

    private fun cycleGameGain() {
        val steps = listOf(0, 35, 65, 85, 100, 120)
        val next = steps[(steps.indexOf(settings.gameGainPercent) + 1) % steps.size]
        updateSetting(settings.copy(gameGainPercent = next))
    }

    private fun cycleDucking() {
        val next = when (settings.vadDuckingPercent) {
            0 -> 20; 20 -> 35; else -> 0
        }
        updateSetting(settings.copy(vadDuckingPercent = next))
    }

    private fun cycleMicSyncDelay() {
        val delays = listOf(0, 40, 80, 120, 160)
        val next = delays[(delays.indexOf(settings.micSyncDelayMs) + 1) % delays.size]
        updateSetting(settings.copy(micSyncDelayMs = next))
    }

    private fun cycleExtraSafeguards() {
        val (brbMute, wifiLow) = when {
            settings.brbMuteMicToo && !settings.wifiLowLatencyLock -> true to true
            settings.brbMuteMicToo && settings.wifiLowLatencyLock -> false to false
            else -> true to false
        }
        updateSetting(settings.copy(brbMuteMicToo = brbMute, wifiLowLatencyLock = wifiLow))
    }

    // === VAULT & KEY VISIBILITY ACTIONS ===
    private fun saveVaultInputs(showToast: Boolean) {
        val rawUrl = etRtmpsUrl.text?.toString() ?: KeystoreVault.DEFAULT_YOUTUBE_RTMPS
        val normalizedUrl = KeystoreVault.normalizeIngestUrl(rawUrl)
        val cleanKey = etStreamKey.text?.toString()?.trim()?.trimStart('/') ?: ""
        etRtmpsUrl.setText(normalizedUrl)
        KeystoreVault.saveEndpointAndKey(this, normalizedUrl, cleanKey)
        if (showToast) {
            val msg = if (cleanKey.isEmpty()) {
                "Ingest URL saved (Stream Key box is currently empty)."
            } else {
                "RTMPS URL & Stream Key encrypted in Keystore!"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearStreamKeyInput() {
        etStreamKey.setText("")
        KeystoreVault.clearStreamKey(this)
        Toast.makeText(this, "Saved Stream Key cleared. Ready to enter a new key.", Toast.LENGTH_SHORT).show()
    }

    private fun pasteStreamKeyFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (!txt.isNullOrEmpty()) {
            etStreamKey.setText(txt)
            etStreamKey.setSelection(etStreamKey.text?.length ?: 0)
            saveVaultInputs(showToast = true)
        } else {
            Toast.makeText(this, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleKeyVisibility() {
        showStreamKeyPlain = !showStreamKeyPlain
        if (showStreamKeyPlain) {
            etStreamKey.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            etStreamKey.transformationMethod = null
            btnShowHideKey.text = "Hide Key"
        } else {
            etStreamKey.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            etStreamKey.transformationMethod = PasswordTransformationMethod.getInstance()
            btnShowHideKey.text = "Show Key"
        }
        etStreamKey.setSelection(etStreamKey.text?.length ?: 0)
    }

    // === DIRECT PERMISSION & SETTINGS TELEPORTERS ===
    private fun openAccessibilitySettingsDirect() {
        try {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(
                    this,
                    "Installed apps -> SolStream Studio -> if greyed out, tap it once & press OK so ⋮ appears in Step 4!",
                    Toast.LENGTH_LONG
                ).show()
            }
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openAppInfoForRestrictedSettings() {
        try {
            Toast.makeText(
                this,
                "Tap ⋮ (top-right) -> 'Allow restricted settings'! (If no ⋮ shows, tap grey SolStream in Step 3 first.)",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun onGoLiveClicked() {
        if (SolStreamService.isBroadcastLive) {
            val liveSvc = SolStreamService.instance
            if (liveSvc != null) {
                liveSvc.stopLiveBroadcast("Stopped from dashboard")
            } else {
                startService(Intent(this, SolStreamService::class.java).apply {
                    action = SolStreamService.ACTION_STOP_STREAM
                })
            }
            return
        }

        saveVaultInputs(showToast = false)
        if (KeystoreVault.buildFullStreamEndpoint(this).isNullOrBlank()) {
            Toast.makeText(this, "Please paste or type your Stream Key first!", Toast.LENGTH_LONG).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRuntimePermissions()
            Toast.makeText(this, "Grant Microphone permission first, then tap GO LIVE.", Toast.LENGTH_LONG).show()
            return
        }

        // Non-blocking Silent-Stream Launch Guard
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val mediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (mediaVol == 0 || settings.gameGainPercent == 0) {
                Toast.makeText(
                    this,
                    "Heads up: Phone Media Volume or Game Mix is at 0% (game audio will be quiet).",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (_: Exception) {
        }

        if (!isAccessibilityServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Enable Accessibility Service First")
                .setMessage(
                    "SolStream Studio uses Android's Accessibility Overlay to display your floating live pill in CODM and keep microphone sharing active alongside squad voice chat.\n\n" +
                        "Why ⋮ 3-dots hide on Android 13–17:\n" +
                        "1) Tap 'Step A: Accessibility' below -> Installed apps -> tap greyed-out SolStream Studio once & press OK.\n" +
                        "2) Tap 'Step B: Unlock ⋮' -> tap ⋮ at top-right -> Allow restricted settings, then turn Accessibility ON!"
                )
                .setPositiveButton("Step A: Accessibility") { _, _ ->
                    openAccessibilitySettingsDirect()
                }
                .setNeutralButton("Step B: Unlock ⋮") { _, _ ->
                    openAppInfoForRestrictedSettings()
                }
                .setNegativeButton("Stream Without Overlay") { _, _ ->
                    requestScreenCaptureAndGoLive()
                }
                .show()
            return
        }

        requestScreenCaptureAndGoLive()
    }

    private fun requestScreenCaptureAndGoLive() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun runFiveSecMicTest() {
        if (SolStreamService.isBroadcastLive) {
            Toast.makeText(this, "Mic is already live on stream!", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRuntimePermissions()
            return
        }
        if (!micTestBusy.compareAndSet(false, true)) {
            Toast.makeText(this, "Mic test is already running...", Toast.LENGTH_SHORT).show()
            return
        }
        tvMicTestStatus.text = "Starting 5-second CODM grip mic recording..."
        SolDualAudioEngine.recordFiveSecondMicTest(
            context = this,
            settings = settings,
            onProgress = { msg ->
                runOnUiThread { if (!isFinishing && !isDestroyed) tvMicTestStatus.text = msg }
            },
            onComplete = { _, summary ->
                micTestBusy.set(false)
                runOnUiThread { if (!isFinishing && !isDestroyed) tvMicTestStatus.text = summary }
            }
        )
    }

    private fun playMicTestSample() {
        if (!micTestBusy.compareAndSet(false, true)) {
            Toast.makeText(this, "Audio test busy...", Toast.LENGTH_SHORT).show()
            return
        }
        tvMicTestStatus.text = "Playing recorded 5s mic sample..."
        SolDualAudioEngine.playRecordedMicTest(this) { msg ->
            micTestBusy.set(false)
            runOnUiThread { if (!isFinishing && !isDestroyed) tvMicTestStatus.text = msg }
        }
    }

    private fun toggleGlipBenchmark() {
        if (SolStreamService.isBroadcastLive) {
            Toast.makeText(this, "Stop SolStream live broadcast before running Glip baseline.", Toast.LENGTH_SHORT).show()
            return
        }
        val act = if (SolStreamService.isGlipBenchmarkRunning) {
            SolStreamService.ACTION_STOP_GLIP_BENCH
        } else {
            SolStreamService.ACTION_START_GLIP_BENCH
        }
        startService(Intent(this, SolStreamService::class.java).apply { action = act })
    }

    // === REAL TLS + SNI HANDSHAKE & UPLOAD BUFFERBLOAT PRE-FLIGHT ===
    private fun parseIngestHostPort(rawUrl: String): Triple<Boolean, String, Int> {
        val clean = KeystoreVault.normalizeIngestUrl(rawUrl)
        return try {
            val uri = URI(clean)
            val scheme = (uri.scheme ?: "rtmps").lowercase(Locale.US)
            val isTls = scheme == "rtmps"
            val host = uri.host?.takeIf { it.isNotBlank() } ?: "a.rtmps.youtube.com"
            val port = if (uri.port > 0) uri.port else if (isTls) 443 else 1935
            Triple(isTls, host, port)
        } catch (_: Exception) {
            Triple(true, "a.rtmps.youtube.com", 443)
        }
    }

    private fun measureSingleTcpRttMs(addr: InetSocketAddress, timeoutMs: Int = 3000): Long {
        val sock = Socket()
        return try {
            val t0 = System.nanoTime()
            sock.connect(addr, timeoutMs)
            (System.nanoTime() - t0) / 1_000_000L
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    private fun runRtmpsNetworkCheck() {
        if (!netTestBusy.compareAndSet(false, true)) {
            Toast.makeText(this, "Internet check already running...", Toast.LENGTH_SHORT).show()
            return
        }
        saveVaultInputs(showToast = false)
        val (isTls, host, port) = parseIngestHostPort(etRtmpsUrl.text?.toString() ?: KeystoreVault.DEFAULT_YOUTUBE_RTMPS)
        btnNetTest.isEnabled = false
        tvNetTestResult.text = "Step 1/3: Resolving DNS & verifying ${if (isTls) "TLS" else "TCP"} handshake to $host:$port..."

        Thread({
            try {
                val dnsStartNs = System.nanoTime()
                val resolvedIp = InetAddress.getByName(host)
                val dnsMs = (System.nanoTime() - dnsStartNs) / 1_000_000L
                val targetAddr = InetSocketAddress(resolvedIp, port)

                var tlsInfo = "Plain RTMP (No TLS)"
                if (isTls) {
                    val rawSock = Socket()
                    var sslSock: SSLSocket? = null
                    try {
                        val t0 = System.nanoTime()
                        rawSock.connect(targetAddr, 3500)
                        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                        val createdSsl = factory.createSocket(rawSock, host, port, true) as SSLSocket
                        sslSock = createdSsl
                        val params = createdSsl.sslParameters
                        params.endpointIdentificationAlgorithm = "HTTPS"
                        createdSsl.sslParameters = params
                        createdSsl.soTimeout = 4500
                        createdSsl.startHandshake()
                        val hsMs = (System.nanoTime() - t0) / 1_000_000L
                        tlsInfo = "${createdSsl.session.protocol} Verified (${hsMs}ms)"
                    } finally {
                        try { sslSock?.close() } catch (_: Exception) {}
                        try { rawSock.close() } catch (_: Exception) {}
                    }
                }

                val idleSamples = mutableListOf<Long>()
                repeat(4) {
                    idleSamples.add(measureSingleTcpRttMs(targetAddr, 3000))
                }
                val idleAvgMs = idleSamples.average().toLong()
                val idleJitterMs = (idleSamples.maxOrNull() ?: idleAvgMs) - (idleSamples.minOrNull() ?: idleAvgMs)

                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        tvNetTestResult.text = "Step 2/3: $tlsInfo | Idle Ping ${idleAvgMs}ms — testing upload Mbps & bufferbloat..."
                    }
                }

                var uploadMbps = -1.0
                var loadedRttMs = -1L
                val probeDone = AtomicBoolean(false)

                val loadedPingThread = Thread({
                    val loadedList = mutableListOf<Long>()
                    try {
                        Thread.sleep(220)
                        while (!probeDone.get() && loadedList.size < 3) {
                            loadedList.add(measureSingleTcpRttMs(targetAddr, 2500))
                            Thread.sleep(200)
                        }
                    } catch (_: Exception) {
                    }
                    if (loadedList.isNotEmpty()) {
                        loadedRttMs = loadedList.average().toLong()
                    }
                }, "SolBloatPing")

                var upConn: HttpsURLConnection? = null
                try {
                    val totalBytes = 2 * 1024 * 1024
                    val chunk = ByteArray(16 * 1024) { 0x5A.toByte() }
                    upConn = (URL("https://speed.cloudflare.com/__up").openConnection() as HttpsURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        useCaches = false
                        connectTimeout = 4000
                        readTimeout = 8000
                        setRequestProperty("Content-Type", "application/octet-stream")
                        setFixedLengthStreamingMode(totalBytes)
                    }
                    loadedPingThread.start()
                    val upStartNs = System.nanoTime()
                    upConn.outputStream.use { os ->
                        var sent = 0
                        while (sent < totalBytes) {
                            val toWrite = if (totalBytes - sent < chunk.size) totalBytes - sent else chunk.size
                            os.write(chunk, 0, toWrite)
                            sent += toWrite
                        }
                        os.flush()
                    }
                    val code = upConn.responseCode
                    val upSec = max(0.05, (System.nanoTime() - upStartNs) / 1_000_000_000.0)
                    if (code in 200..299) {
                        uploadMbps = (totalBytes * 8.0) / (upSec * 1_000_000.0)
                    }
                } catch (_: Exception) {
                } finally {
                    probeDone.set(true)
                    try { loadedPingThread.join(1200) } catch (_: Exception) {}
                    try { upConn?.disconnect() } catch (_: Exception) {}
                }

                val bloatDeltaMs = if (loadedRttMs > 0L) max(0L, loadedRttMs - idleAvgMs) else -1L
                val advice = when {
                    uploadMbps > 0.0 && uploadMbps < 14.0 ->
                        "Upload measured ${String.format(Locale.US, "%.1f", uploadMbps)} Mbps: use 1080p60 @ 10–12 Mbps + ABR ON."
                    bloatDeltaMs > 65L ->
                        "Router bufferbloat +${bloatDeltaMs}ms under upload: set Bitrate 20–25% below max upload & keep Queue ABR ON."
                    uploadMbps >= 26.0 && idleAvgMs < 95 && idleJitterMs < 35 ->
                        "Excellent headroom! Safe for crisp 20–24 Mbps 1440p60."
                    idleAvgMs < 150 && idleJitterMs < 65 ->
                        "Solid connection! 18–20 Mbps 1440p60 with Queue ABR ON recommended."
                    else ->
                        "High Wi-Fi jitter (${idleJitterMs}ms): use 16 Mbps Stable + 5GHz Wi-Fi."
                }

                val summary = buildString {
                    append("DNS: ${dnsMs}ms | $tlsInfo | Ping: ${idleAvgMs}ms (Jitter ${idleJitterMs}ms)")
                    if (uploadMbps > 0.0) {
                        append(" | Upload: ${String.format(Locale.US, "%.1f", uploadMbps)} Mbps")
                    }
                    if (bloatDeltaMs >= 0L) {
                        append(" | Load Ping: +${bloatDeltaMs}ms")
                    }
                    append("\n-> $advice")
                }

                DiagnosticsLogger.log("NET_CHECK", summary.replace('\n', ' '))
                runOnUiThread {
                    netTestBusy.set(false)
                    if (!isFinishing && !isDestroyed) {
                        btnNetTest.isEnabled = true
                        tvNetTestResult.text = summary
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    netTestBusy.set(false)
                    if (!isFinishing && !isDestroyed) {
                        btnNetTest.isEnabled = true
                        tvNetTestResult.text =
                            "Ingest check failed (${e.javaClass.simpleName}: ${e.message ?: "check internet"}). Verify URL & 5GHz Wi-Fi."
                    }
                }
            }
        }, "SolNetProbe").start()
    }

    private fun showDiagnosticsDialog() {
        val report = DiagnosticsLogger.buildShareableReport(this)
        val tv = TextView(this).apply {
            text = report
            setTextColor(Color.parseColor("#ECEFF1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F17"))
            addView(tv)
        }
        AlertDialog.Builder(this)
            .setTitle("SolStream Automatic Session Report")
            .setView(scroll)
            .setPositiveButton("Copy Report") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("SolStream Report", report))
                Toast.makeText(this, "Report copied! Safe to paste anywhere.", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Share") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                startActivity(Intent.createChooser(send, "Share Session Report"))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun isRemoteVersionNewer(remoteRaw: String, localRaw: String): Boolean {
        val rParts = remoteRaw.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val lParts = localRaw.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val count = max(rParts.size, lParts.size)
        for (i in 0 until count) {
            val rv = rParts.getOrElse(i) { 0 }
            val lv = lParts.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun checkGitHubReleaseUpdate() {
        Toast.makeText(this, "Checking PppP01/SolStream-Studio releases...", Toast.LENGTH_SHORT).show()
        Thread({
            var conn: HttpsURLConnection? = null
            try {
                conn = (URL("https://api.github.com/repos/PppP01/SolStream-Studio/releases/latest")
                    .openConnection() as HttpsURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                if (conn.responseCode != 200) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(
                                this,
                                "No published GitHub Releases yet (running v$appVersionName).",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    return@Thread
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val latestTag = json.optString("tag_name", "").removePrefix("v")
                var downloadUrl = json.optString("html_url", "https://github.com/PppP01/SolStream-Studio/releases")
                val assets = json.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    var chosenAssetUrl: String? = null
                    for (i in 0 until assets.length()) {
                        val obj = assets.getJSONObject(i)
                        val name = obj.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true) && !name.contains("debug", ignoreCase = true)) {
                            chosenAssetUrl = obj.optString("browser_download_url", "")
                            break
                        }
                    }
                    downloadUrl = chosenAssetUrl?.takeIf { it.isNotBlank() }
                        ?: assets.getJSONObject(0).optString("browser_download_url", downloadUrl)
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (latestTag.isNotBlank() && isRemoteVersionNewer(latestTag, appVersionName)) {
                        AlertDialog.Builder(this)
                            .setTitle("Update Available: v$latestTag")
                            .setMessage("You are on v$appVersionName. Download signed update from your GitHub Releases?")
                            .setPositiveButton("Download APK") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    } else {
                        Toast.makeText(this, "You are on the latest version (v$appVersionName)!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Update check failed: ${e.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }, "SolUpdateCheck").start()
    }

    private fun showSamsungSetupGuide() {
        val msg = """
            1. UNLOCK RESTRICTED ACCESSIBILITY (Why ⋮ 3-Dots Hide at First):
            • On Android 13–17, Android hides the App Info ⋮ menu until you try Accessibility once!
            • Tap '3. Tap Accessibility 1st' -> Installed apps -> tap greyed-out SolStream Studio once & press OK.
            • Return here and tap '4. Unlock Restricted (⋮)' -> tap ⋮ at top-right -> 'Allow restricted settings'.
            • Tap '3. Tap Accessibility 1st' again and toggle SolStream Studio ON!
            (Once unlocked, future signed updates keep Accessibility ON forever.)

            2. REALME NECKBAND STEREO + 45ms GAME MODE TIP:
            • If using Phone Mic for 48 kHz studio voice while keeping stereo footsteps in your neckband: open Bluetooth Settings -> realme Buds Wireless 5 ANC (Gear icon) -> toggle Calls OFF and keep Audio ON.
            • In Samsung Settings -> Sounds and vibration -> Separate app sound -> lock Call of Duty to Bluetooth device so match transitions never jump to phone speaker.

            3. SINGLE-APP PRIVACY CAPTURE:
            • When Android's "Start recording or casting" popup appears, choose "A single app" -> Call of Duty so notifications/OTPs never appear on your YouTube stream!
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Samsung One UI & CODM Pro Setup")
            .setMessage(msg)
            .setPositiveButton("Step 1: Accessibility") { _, _ ->
                openAccessibilitySettingsDirect()
            }
            .setNeutralButton("Step 2: App Info (⋮)") { _, _ ->
                openAppInfoForRestrictedSettings()
            }
            .setNegativeButton("Got It", null)
            .show()
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun openBatteryUnrestricted() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Battery is already Unrestricted!", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (SolStreamService.isAccessibilityConnected) return true
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains("$packageName/${SolStreamService::class.java.name}") ||
            enabled.contains("$packageName/.SolStreamService")
    }

    // === UI HELPERS ===
    private fun sectionCard(heading: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable("#131A26", "#223042")
            setPadding(dp(14), dp(12), dp(14), dp(14))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            layoutParams = lp

            addView(TextView(this@MainActivity).apply {
                text = heading
                setTextColor(Color.parseColor("#00E5FF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(6))
            })
        }
    }

    private fun labelText(txt: String): TextView = TextView(this).apply {
        text = txt
        setTextColor(Color.parseColor("#B0BEC5"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setPadding(0, dp(6), 0, dp(2))
    }

    private fun bodyText(txt: String): TextView = TextView(this).apply {
        text = txt
        setTextColor(Color.parseColor("#CFD8DC"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, dp(4), 0, dp(6))
    }

    private fun styledEditText(initial: String, isPassword: Boolean): EditText = EditText(this).apply {
        setText(initial)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#607D8B"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        isSingleLine = true
        background = cardDrawable("#0B0F17", "#37474F")
        setPadding(dp(10), dp(9), dp(10), dp(9))
        if (isPassword) {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            transformationMethod = PasswordTransformationMethod.getInstance()
        } else {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
    }

    private fun actionButton(label: String, bgHex: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        background = roundedDrawable(bgHex, "#37474F")
        setPadding(dp(10), dp(6), dp(10), dp(6))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) }
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    private fun buttonRow(vararg buttons: Button): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            layoutParams = lp
        }
        buttons.forEachIndexed { idx, btn ->
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (idx > 0) marginStart = dp(6)
            }
            btn.layoutParams = lp
            row.addView(btn)
        }
        return row
    }

    private fun cardDrawable(fillHex: String, strokeHex: String): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(fillHex))
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), Color.parseColor(strokeHex))
    }

    private fun roundedDrawable(fillHex: String, strokeHex: String): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(fillHex))
        cornerRadius = dp(8).toFloat()
        setStroke(dp(1), Color.parseColor(strokeHex))
    }

    private fun okWait(ok: Boolean): String = if (ok) "READY" else "NEEDED"
    private fun onOff(v: Boolean): String = if (v) "ON" else "OFF"
    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
