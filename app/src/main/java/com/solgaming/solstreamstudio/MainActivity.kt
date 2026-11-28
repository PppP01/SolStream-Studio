/*
- ॐ नमः शिवाय (Om Namah Shivaya)
- SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
- Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
- Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.solgaming.solstreamstudio.recorder.SolHudAndQrRenderer
import com.solgaming.solstreamstudio.ui.DashboardDialogs
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var appVersionName: String
    private lateinit var settings: StreamSettings
    private lateinit var scanSummary: DeviceScanSummary
    private var micOptions: List<MicInputOption> = emptyList()
    private var showStreamKeyPlain = false
    private var userModifiedSettingsBeforeScan = false
    private var preflightManuallyExpanded = false
    private var askedRuntimePermOnce = false
    private var lastDashboardStopTapMs = 0L
    private var activeStudioProfileSlot = -1
    private var suppressSeekBarCallback = false
    private var pendingAssetPickSlot: SolSceneAssetStore.AssetSlot = SolSceneAssetStore.AssetSlot.BRB_SCENE
    private var benchmarkTargetAppName: String = "Glip / Other App"

    private val uiHandler = Handler(Looper.getMainLooper())
    private val micTestBusy = AtomicBoolean(false)
    private val hwScanBusy = AtomicBoolean(false)
    private val goLiveWaitBusy = AtomicBoolean(false)

    private val serviceStateListener: () -> Unit = {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                settings = KeystoreVault.enforcePlatformLimits(KeystoreVault.loadSettings(this)).first
                refreshAllUi()
            }
        }
    }

    // Responsive Layout Containers (Android 10–18 Phones, Foldables & Tablets)
    private lateinit var fixedHeaderBox: LinearLayout
    private lateinit var mainContentContainer: LinearLayout
    private var lastInsetLeft = 0
    private var lastInsetTop = 0
    private var lastInsetRight = 0
    private var lastInsetBottom = 0

    // Fixed Header, Glanceable Live Chips, Protect Long Streams Banner & 3-Tile Hero Bar
    private lateinit var tvHeaderSubtitle: TextView
    private lateinit var tvChipAudio: TextView
    private lateinit var tvChipVideo: TextView
    private lateinit var tvChipProtection: TextView
    private lateinit var boxProtectBanner: LinearLayout
    private lateinit var tvProtectTitle: TextView
    private lateinit var tvProtectTip: TextView
    private lateinit var btnProtectAction: Button
    private lateinit var tvLiveBanner: TextView
    private lateinit var tvHeroResValue: TextView
    private lateinit var tvHeroFpsValue: TextView
    private lateinit var tvHeroBitrateValue: TextView
    private lateinit var btnReapproveCapture: Button
    private val tierButtons = mutableMapOf<DeviceTier, Button>()
    private lateinit var tvTierDescription: TextView

    // Card 1: Pre-Flight Readiness & Auto-Collapse
    private lateinit var tvPreflightStatus: TextView
    private lateinit var tvPreflightAudioWifiLine: TextView
    private lateinit var btnPreflightCollapsedToggle: Button
    private lateinit var boxPreflightButtonsContainer: LinearLayout
    private lateinit var tvRestrictedHint: TextView
    private lateinit var btnGrantMicNotif: Button
    private lateinit var btnUnrestrictedBatt: Button
    private lateinit var btnTapAccessibilityFirst: Button
    private lateinit var btnUnlockRestricted: Button

    // Card 2: 4 Studio Profiles, SolRelay Omega™ Vault, 4-Pill Platform Bar, Simulcast & 5-Metric Speed Test
    private val studioProfileButtons = mutableListOf<Button>()
    private val platformPillButtons = mutableMapOf<String, Button>()
    private lateinit var etRtmpsUrl: EditText
    private lateinit var tvVaultSecurityLabel: TextView
    private lateinit var tvKeyStatusBadge: TextView
    private lateinit var etStreamKey: EditText
    private lateinit var btnShowHideKey: Button
    private lateinit var btnToggleSimulcastDrawer: Button
    private lateinit var boxSimulcastDrawer: LinearLayout
    private lateinit var etShortsKey: EditText
    private lateinit var btnUseShortsSlot: Button
    private lateinit var btnSimulcastMode: Button
    private lateinit var btnSimulcastTwitch: Button
    private lateinit var etTwitchUrl: EditText
    private lateinit var etTwitchKey: EditText
    private lateinit var btnSimulcastKick: Button
    private lateinit var etKickUrl: EditText
    private lateinit var etKickKey: EditText
    private lateinit var btnDualCompanion: Button
    private lateinit var btnCompanionBitrate: Button
    private lateinit var btnNetTest: Button
    private lateinit var tvNetTestResult: TextView

    // Card 3: SolCore Ultra™ Video Engine, Segmented Pills, Visuals, SolShorts™ Studio & Pro Overlays
    private val resPillButtons = mutableMapOf<Int, Button>()
    private val codecPillButtons = mutableMapOf<String, Button>()
    private val fpsPillButtons = mutableMapOf<Int, Button>()
    private val framingPillButtons = mutableMapOf<String, Button>()
    private lateinit var btnCodec: Button
    private lateinit var btnResolution: Button
    private lateinit var btnFps: Button
    private lateinit var btnOrientationFlip: Button
    private lateinit var btnBitrateDown: Button
    private lateinit var btnBitrateCycle: Button
    private lateinit var btnBitrateUp: Button
    private lateinit var btnRateControl: Button
    private lateinit var btnAbr: Button
    private lateinit var btnUpscaler: Button
    private lateinit var btnFraming: Button
    private lateinit var btnSmartZoom: Button
    private lateinit var btnFilmic: Button
    private lateinit var btnAmoledVivid: Button
    private lateinit var btnShadowBoost: Button
    private lateinit var btnColorTemp: Button
    private lateinit var btnSharpen: Button
    private lateinit var btnToggleSceneProDrawer: Button
    private lateinit var boxSceneProDrawer: LinearLayout
    private lateinit var btnShortsAnchor: Button
    private lateinit var btnShortsHeaderLayout: Button
    private lateinit var btnShortsScoreLoupe: Button
    private lateinit var btnShortsMinimapLoupe: Button
    private lateinit var btnShortsCaptureCooler: Button
    private lateinit var btnPreviewShortsSafeZones: Button
    private lateinit var btnPickStartImg: Button
    private lateinit var btnPickBrbImg: Button
    private lateinit var btnPickOverlayPng: Button
    private lateinit var btnOverlayPosition: Button
    private lateinit var btnPickShortsPoster: Button
    private lateinit var btnHudTelemetryToggle: Button
    private lateinit var btnHudTelemetryCorner: Button
    private lateinit var etQrOverlayText: EditText
    private lateinit var btnQrOverlayToggle: Button
    private lateinit var btnQrOverlayCorner: Button
    private lateinit var btnPickPngTuberIdle: Button
    private lateinit var btnPickPngTuberTalk: Button
    private lateinit var btnPngTuberToggle: Button
    private lateinit var btnPngTuberCorner: Button
    private lateinit var btnPngTuberSize: Button
    private lateinit var etBannerText: EditText
    private lateinit var btnBannerToggle: Button
    private lateinit var btnBannerPos: Button
    private lateinit var btnShortsBackdrop: Button
    private lateinit var btnMinimapShield: Button
    private lateinit var btnAutoBlurKeyboard: Button
    private lateinit var btnGlEngine: Button
    private lateinit var btnHdr10: Button
    private lateinit var btnRoiMode: Button
    private lateinit var btnKeyframeAndBFrames: Button
    private lateinit var btnQpAndLatency: Button

    // Card 4: 6-Stage SolLuxe™ 3.0 Pure Voice Studio, SeekBar Sliders, 250% Game Soundstage & 10s Calibrator
    private lateinit var btnMicDevice: Button
    private lateinit var btnMicSource: Button
    private lateinit var btnAudioBitrate: Button
    private lateinit var btnGenderNeutral: Button
    private lateinit var btnGenderMale: Button
    private lateinit var btnGenderFemale: Button
    private lateinit var btnVoicePreset: Button
    private lateinit var btnNoiseSuppressMode: Button
    private lateinit var btnNoiseSuppressLevel: Button
    private lateinit var seekMicGain: SeekBar
    private lateinit var seekGameGain: SeekBar
    private lateinit var btnMicGain: Button
    private lateinit var btnGameGain: Button
    private lateinit var btnDucking: Button
    private lateinit var btnMicDelay: Button
    private lateinit var btnGameWide: Button
    private lateinit var btnGameSubPunch: Button
    private lateinit var btnGameFootsteps: Button
    private lateinit var btnGameBlastTamer: Button
    private lateinit var btnToggleVoiceStageDrawer: Button
    private lateinit var boxVoiceStageDrawer: LinearLayout
    private lateinit var btnStageHpf: Button
    private lateinit var btnStageTransient: Button
    private lateinit var btnStageGate: Button
    private lateinit var btnStageEq: Button
    private lateinit var btnStageDeEss: Button
    private lateinit var btnStageVelvet: Button
    private lateinit var btnStageComp: Button
    private lateinit var btnStageWhisper: Button
    private lateinit var btnStageSlowAgc: Button
    private lateinit var btnStageSpeakerAec: Button
    private lateinit var btnExtraSafeguards: Button
    private lateinit var tvMicTestStatus: TextView
    private lateinit var btnRecordMicTest: Button
    private lateinit var btnPlayMicTest: Button
    private lateinit var btnApplyAdvisor: Button

    // Card 5: Launch, Reports, Saved Clips, Benchmark & Support
    private lateinit var btnGoLive: Button
    private lateinit var btnReportsQuick: Button
    private lateinit var btnSavedClipsQuick: Button
    private lateinit var btnGlipBench: Button

    private val dashboardStopResetRunnable = Runnable {
        if (!isFinishing && !isDestroyed && SolStreamService.isBroadcastLive && ::btnGoLive.isInitialized) {
            btnGoLive.text = "⏹ STOP LIVE STREAM"
        }
    }

    private val idleTelemetryRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                if (!SolStreamService.isBroadcastLive && !SolStreamService.isGlipBenchmarkRunning) {
                    val idleHealth = CodecCapabilityProbe.readLiveHardwareHealth(this@MainActivity)
                    tvLiveBanner.text = "STATUS: ${idleHealth.toIdleStatusBanner()}"
                }
                tvPreflightAudioWifiLine.text = SolDualAudioEngine.inspectPreFlightAudioAndWifiCoexistence(this@MainActivity)
                uiHandler.postDelayed(this, 4000L)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted && askedRuntimePermOnce) {
            val micDeniedPermanently = !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.RECORD_AUDIO
            ) && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED

            if (micDeniedPermanently) {
                Toast.makeText(
                    this,
                    "Android blocked the permission popup — turn ON Microphone & Notifications in App Permissions here!",
                    Toast.LENGTH_LONG
                ).show()
                openAppInfoSettings()
            }
        }
        askedRuntimePermOnce = true
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
            try {
                ContextCompat.startForegroundService(this, svcIntent)
            } catch (e: Exception) {
                CodecCapabilityProbe.isBroadcastStartingOrLive = false
                Toast.makeText(
                    this,
                    "Could not start broadcast service (${e.javaClass.simpleName}). Tap GO LIVE again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            Toast.makeText(this, "Screen capture cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    private val imageAssetPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val targetSlot = pendingAssetPickSlot
        val friendlyName = friendlyAssetSlotName(targetSlot)
        Toast.makeText(this, "Saving $friendlyName...", Toast.LENGTH_SHORT).show()
        Thread({
            val res = SolSceneAssetStore.importAssetFromUri(applicationContext, uri, targetSlot)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    res.onSuccess { info ->
                        when (targetSlot) {
                            SolSceneAssetStore.AssetSlot.OVERLAY_PNG -> {
                                if (!settings.overlayImageEnabled) {
                                    updateSetting(settings.copy(overlayImageEnabled = true))
                                } else {
                                    refreshAllUi()
                                }
                            }
                            SolSceneAssetStore.AssetSlot.SHORTS_POSTER -> {
                                updateSetting(settings.copy(shortsBackdropMode = "POSTER"))
                            }
                            SolSceneAssetStore.AssetSlot.PNGTUBER_IDLE,
                            SolSceneAssetStore.AssetSlot.PNGTUBER_TALK -> {
                                if (!settings.pngTuberEnabled) {
                                    updateSetting(settings.copy(pngTuberEnabled = true))
                                } else {
                                    refreshAllUi()
                                }
                            }
                            else -> refreshAllUi()
                        }
                        Toast.makeText(this, "$friendlyName: $info!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(this, "Image load failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }, "SolAssetImport").start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticsLogger.installPrivacySafeCrashHandler(this)
        appVersionName = readAppVersionName()

        scanSummary = CodecCapabilityProbe.peekCachedOrPlaceholder(this)
        settings = KeystoreVault.enforcePlatformLimits(KeystoreVault.loadSettings(this)).first
        micOptions = CodecCapabilityProbe.listAvailableMicInputs(this)

        setContentView(buildDashboardUi())

        SolStreamService.onStateChangedListener = serviceStateListener
        refreshAllUi()

        CodecCapabilityProbe.scanDeviceAsync(this, forceRefresh = false) { verifiedScan ->
            if (!isFinishing && !isDestroyed) {
                scanSummary = verifiedScan
                if (!KeystoreVault.hasCompletedInitialAutoScan(this)) {
                    if (!userModifiedSettingsBeforeScan && !SolStreamService.isBroadcastLive) {
                        settings = applyTierDefaultsToSettings(settings, verifiedScan.recommendedTier, verifiedScan)
                        KeystoreVault.saveSettings(this, settings)
                    }
                    KeystoreVault.markInitialAutoScanDone(this)
                }
                tvHeaderSubtitle.text =
                    "${scanSummary.friendlyHardwareLine()}\n${scanSummary.toBadgeText()}"
                refreshAllUi()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyResponsiveContainerPadding()
    }

    override fun onResume() {
        super.onResume()
        settings = KeystoreVault.enforcePlatformLimits(KeystoreVault.loadSettings(this)).first
        micOptions = CodecCapabilityProbe.listAvailableMicInputs(this)
        refreshAllUi()
        uiHandler.removeCallbacks(idleTelemetryRefreshRunnable)
        uiHandler.post(idleTelemetryRefreshRunnable)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(idleTelemetryRefreshRunnable)
        try {
            saveVaultInputs(showToast = false)
        } catch (_: Exception) {
        }
        super.onPause()
    }

    override fun onStop() {
        // Android 17 Background Audio Hardening: stop offline 10s Mic Test AudioTrack/AudioRecord in background
        SolDualAudioEngine.stopOfflineMicTestIfActive()
        super.onStop()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(idleTelemetryRefreshRunnable)
        uiHandler.removeCallbacks(dashboardStopResetRunnable)
        goLiveWaitBusy.set(false)
        if (SolStreamService.onStateChangedListener === serviceStateListener) {
            SolStreamService.onStateChangedListener = null
        }
        super.onDestroy()
    }

    private fun friendlyAssetSlotName(slot: SolSceneAssetStore.AssetSlot): String = when (slot) {
        SolSceneAssetStore.AssetSlot.STARTING_SOON -> "Starting Soon Graphic"
        SolSceneAssetStore.AssetSlot.BRB_SCENE -> "BRB Privacy Graphic"
        SolSceneAssetStore.AssetSlot.OVERLAY_PNG -> ".PNG Stream Logo"
        SolSceneAssetStore.AssetSlot.SHORTS_POSTER -> "9:16 Shorts Poster"
        SolSceneAssetStore.AssetSlot.PNGTUBER_IDLE -> "PNGTuber Idle Avatar"
        SolSceneAssetStore.AssetSlot.PNGTUBER_TALK -> "PNGTuber Talking Avatar"
    }

    private fun readAppVersionName(): String {
        val fallback = DiagnosticsLogger.ENGINE_VERSION.removePrefix("v")
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            info.versionName?.removeSuffix("-debug") ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun applyResponsiveContainerPadding() {
        if (!::fixedHeaderBox.isInitialized || !::mainContentContainer.isInitialized) return
        val screenW = resources.displayMetrics.widthPixels
        val maxContentW = dp(660)
        val extraSidePad = if (screenW > maxContentW) ((screenW - maxContentW) / 2) else 0
        val leftPad = max(dp(16), lastInsetLeft + dp(12)) + extraSidePad
        val rightPad = max(dp(16), lastInsetRight + dp(12)) + extraSidePad

        fixedHeaderBox.setPadding(
            leftPad,
            max(dp(12), lastInsetTop + dp(8)),
            rightPad,
            dp(10)
        )
        mainContentContainer.setPadding(
            leftPad,
            dp(10),
            rightPad,
            max(dp(28), lastInsetBottom + dp(20))
        )
    }

    private fun buildDashboardUi(): View {
        val outerRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050A14"))
        }

        fixedHeaderBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#050A14"), Color.parseColor("#0A1324"))
            )
            setPadding(dp(16), dp(14), dp(16), dp(10))
        }

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#050A14"))
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(28))
        }
        mainContentContainer = container

        ViewCompat.setOnApplyWindowInsetsListener(outerRoot) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            lastInsetLeft = bars.left
            lastInsetTop = bars.top
            lastInsetRight = bars.right
            lastInsetBottom = bars.bottom
            applyResponsiveContainerPadding()
            insets
        }

        // === FIXED OBSIDIAN-GLASS STUDIO HEADER WITH SOLAR ECLIPSE 'S' ICON ===
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logoIconView = ImageView(this).apply {
            setImageResource(R.drawable.ic_solstream_logo)
            contentDescription = "SolStream Studio Logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(10)
            }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(this).apply {
            text = "SolStream Studio"
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.0f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.01f
        }
        val subtitleTag = TextView(this).apply {
            text = "SolApex™ HyperStream • v$appVersionName"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        titleCol.addView(title)
        titleCol.addView(subtitleTag)

        val btnGuideTop = TextView(this).apply {
            text = "📖 Guide"
            setTextColor(Color.parseColor("#E0F2FE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(11), dp(6), dp(11), dp(6))
            background = roundedDrawable("#0E2A47", "#00E5FF")
            setOnClickListener { openGlossaryGuide() }
        }

        titleRow.addView(logoIconView)
        titleRow.addView(titleCol)
        titleRow.addView(btnGuideTop)

        // 3 Glanceable Studio Chips (Audio Engine • Video Target • Long-Stream Protection)
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(2))
        }
        tvChipAudio = makeStatusChip("SolLuxe 3.0", "#0C2138", "#00E5FF", "#E0F2FE")
        tvChipVideo = makeStatusChip("1440p60 • HEVC", "#0C2138", "#38BDF8", "#E0F2FE")
        tvChipProtection = makeStatusChip("Protected ✓", "#0A2B22", "#10B981", "#A7F3D0").apply {
            setOnClickListener { openBatteryUnrestricted() }
        }
        chipRow.addView(tvChipAudio, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        chipRow.addView(tvChipVideo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        chipRow.addView(tvChipProtection)

        tvHeaderSubtitle = TextView(this).apply {
            text = "${scanSummary.friendlyHardwareLine()}\n${scanSummary.toBadgeText()}"
            setTextColor(Color.parseColor("#8FA3BD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.2f)
            setPadding(0, dp(5), 0, dp(4))
        }

        val topHelperRow = buttonRow(
            actionButton("📖 User Guide & Privacy", "#0D3652") { openGlossaryGuide() },
            actionButton("Copy Preset", "#111C2F") { copyPresetToClipboard() },
            actionButton("Import Preset", "#111C2F") { importPresetFromClipboard() }
        )

        fixedHeaderBox.addView(titleRow)
        fixedHeaderBox.addView(chipRow)
        fixedHeaderBox.addView(tvHeaderSubtitle)
        fixedHeaderBox.addView(topHelperRow)

        val headerDivider = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#2979FF"),
                    Color.parseColor("#1D4ED8")
                )
            )
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1.5f.roundToInt()))
        }

        // === LIVE "PROTECT LONG STREAMS" READINESS BANNER (ZERO EXTRA PERMISSIONS) ===
        boxProtectBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            layoutParams = lp
            background = cardDrawable("#1F170A", "#F59E0B")
        }
        val protectTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tvProtectTitle = TextView(this).apply {
            text = "🛡️ Long-Stream Protection: Setup Needed"
            setTextColor(Color.parseColor("#FDE68A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.2f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnProtectAction = Button(this).apply {
            text = "Protect"
            isAllCaps = false
            setTextColor(Color.parseColor("#050A14"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.2f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedDrawable("#F59E0B", "#FDE68A")
            setPadding(dp(12), dp(4), dp(12), dp(4))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
            setOnClickListener {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isIgnoringBatteryOptimizations(packageName)) {
                    openAllBrandSetupGuide()
                } else {
                    openBatteryUnrestricted()
                }
            }
        }
        protectTopRow.addView(tvProtectTitle)
        protectTopRow.addView(btnProtectAction)

        tvProtectTip = TextView(this).apply {
            text = DashboardDialogs.buildBrandAntiKillOneLiner()
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.8f)
            setPadding(0, dp(5), 0, 0)
        }
        boxProtectBanner.addView(protectTopRow)
        boxProtectBanner.addView(tvProtectTip)
        boxProtectBanner.setOnClickListener { openAllBrandSetupGuide() }
        container.addView(boxProtectBanner)

        // === LIVE IDLE / ACTIVE TELEMETRY BANNER ===
        tvLiveBanner = TextView(this).apply {
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            layoutParams = lp
            background = cardDrawable("#0B1829", "#00E5FF")
        }

        btnReapproveCapture = actionButton("RE-APPROVE SCREEN CAPTURE TO RESUME VIDEO", "#E65100") {
            if (SolStreamService.isBroadcastLive) {
                SolStreamService.instance?.stopLiveBroadcast("Re-approving screen capture")
            }
            requestScreenCaptureAndGoLive()
        }.apply { visibility = View.GONE }

        container.addView(tvLiveBanner)
        container.addView(btnReapproveCapture)

        // === 3-TILE HERO SUMMARY BAR ([ 1440p (2K) ] [ 60 FPS ] [ 20 Mbps ]) ===
        val heroRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            layoutParams = lp
        }
        val (tileResBox, tileResVal) = makeHeroTile("RESOLUTION", "1440p (2K)") {
            cycleResolution()
        }
        val (tileFpsBox, tileFpsVal) = makeHeroTile("FRAME RATE", "60 FPS") {
            updateSetting(settings.copy(fps = if (settings.fps == 60) 30 else 60), requiresRestartIfLive = true)
        }
        val (tileBitBox, tileBitVal) = makeHeroTile("BITRATE", "20 Mbps") {
            stepBitrate(+1)
        }
        tvHeroResValue = tileResVal
        tvHeroFpsValue = tileFpsVal
        tvHeroBitrateValue = tileBitVal

        heroRow.addView(tileResBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        heroRow.addView(tileFpsBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        heroRow.addView(tileBitBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(heroRow)

        // === CARD 0: 5-TIER SEGMENTED BAR (Lite | Balanced | High | Flagship | Ultra) ===
        val cardTier = sectionCard(
            heading = "0. Smart Device Tier Presets",
            engineBadge = "SOLAPEX™ TIER",
            badgeHex = "#00E5FF"
        )
        val tierRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        DeviceTier.entries.forEachIndexed { idx, tier ->
            val b = Button(this).apply {
                text = tier.title
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(2), dp(6), dp(2), dp(6))
                val lp = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (idx > 0) marginStart = dp(5)
                }
                layoutParams = lp
                setOnClickListener {
                    activeStudioProfileSlot = -1
                    val updated = applyTierDefaultsToSettings(settings, tier, scanSummary)
                    updateSetting(updated, requiresRestartIfLive = true)
                    Toast.makeText(
                        this@MainActivity,
                        "${tier.title} Tier applied! (Platform & Voice HPF synced automatically.)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            tierButtons[tier] = b
            tierRow.addView(b)
        }
        tvTierDescription = bodyText("")
        cardTier.addView(tierRow)
        cardTier.addView(tvTierDescription)
        container.addView(cardTier)

        // === CARD 1: PRE-FLIGHT READINESS, AUTO-COLLAPSE & ALL-BRAND ANTI-KILL GUIDE ===
        val cardPreflight = sectionCard(
            heading = "1. Pre-Flight Readiness & Setup",
            engineBadge = "PRE-FLIGHT",
            badgeHex = "#38BDF8"
        )
        tvPreflightStatus = bodyText("")
        tvPreflightAudioWifiLine = TextView(this).apply {
            text = SolDualAudioEngine.inspectPreFlightAudioAndWifiCoexistence(this@MainActivity)
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, dp(6))
        }
        cardPreflight.addView(tvPreflightStatus)
        cardPreflight.addView(tvPreflightAudioWifiLine)

        btnPreflightCollapsedToggle = actionButton(
            "✅ All 4 Pre-Flight Checks READY (Tap to Expand Setup & Anti-Kill Guide)",
            "#0D3652"
        ) {
            preflightManuallyExpanded = !preflightManuallyExpanded
            refreshAllUi()
        }
        cardPreflight.addView(btnPreflightCollapsedToggle)

        boxPreflightButtonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        tvRestrictedHint = TextView(this).apply {
            text = "Android 13–18 Tip (Sideloaded APK): Tap '3. Tap Accessibility 1st' → Accept disclosure → tap greyed-out SolStream Studio once & OK → then tap '4. Unlock Restricted (⋮)' → tap ⋮ at top-right → 'Allow restricted settings'."
            setTextColor(Color.parseColor("#FDE68A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(3), 0, dp(6))
        }
        boxPreflightButtonsContainer.addView(tvRestrictedHint)

        btnGrantMicNotif = actionButton("1. Grant Mic & Notif", "#101929") { requestRuntimePermissions() }
        btnUnrestrictedBatt = actionButton("2. Battery Unrestricted", "#101929") { openBatteryUnrestricted() }
        btnTapAccessibilityFirst = actionButton("3. Tap Accessibility 1st", "#101929") {
            showPlayStoreCompliantAccessibilityDisclosure(allowSkipToStream = false)
        }
        btnUnlockRestricted = actionButton("4. Unlock Restricted (⋮)", "#101929") { openAppInfoForRestrictedSettings() }

        boxPreflightButtonsContainer.addView(buttonRow(btnGrantMicNotif, btnUnrestrictedBatt))
        boxPreflightButtonsContainer.addView(buttonRow(btnTapAccessibilityFirst, btnUnlockRestricted))
        boxPreflightButtonsContainer.addView(
            actionButton("🛡️ All-Brand Anti-Kill, Excluded Apps & Cool-Stream Guide", "#152844") {
                openAllBrandSetupGuide()
            }
        )
        cardPreflight.addView(boxPreflightButtonsContainer)
        container.addView(cardPreflight)

        // === CARD 2: 4 STUDIO PROFILES, SOLRELAY OMEGA VAULT, 4-PILL PLATFORM BAR & SPEED TEST ===
        val cardStream = sectionCard(
            heading = "2. Studio Profiles, Key Vault & Simulcast",
            engineBadge = "SOLRELAY OMEGA™",
            badgeHex = "#00E5FF"
        )

        cardStream.addView(labelText("One-Tap Studio Profiles (Tap to Load • Long-Press to Save/Rename Current Setup):"))
        studioProfileButtons.clear()
        val profileRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(6) }
        }
        for (slotIdx in 0 until KeystoreVault.STUDIO_PROFILE_COUNT) {
            val b = Button(this).apply {
                text = "Profile ${slotIdx + 1}"
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.4f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(3), dp(5), dp(3), dp(5))
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (slotIdx > 0) marginStart = dp(4)
                }
                setOnClickListener { loadStudioProfileSlot(slotIdx) }
                setOnLongClickListener {
                    showSaveStudioProfileDialog(slotIdx)
                    true
                }
            }
            studioProfileButtons.add(b)
            profileRow.addView(b)
        }
        cardStream.addView(profileRow)

        cardStream.addView(labelText("Broadcast Platform (Enforces Official Codec, Res, CBR, Bitrate & AAC Limits):"))
        val platRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(6) }
        }
        val platforms = listOf("YOUTUBE" to "YouTube", "TWITCH" to "Twitch", "KICK" to "Kick", "CUSTOM" to "Custom")
        platforms.forEachIndexed { idx, (platKey, platTitle) ->
            val b = Button(this).apply {
                text = platTitle
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.2f)
                setPadding(dp(2), dp(6), dp(2), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (idx > 0) marginStart = dp(5)
                }
                setOnClickListener { selectPlatformPreset(platKey) }
            }
            platformPillButtons[platKey] = b
            platRow.addView(b)
        }
        cardStream.addView(platRow)

        cardStream.addView(labelText("Primary RTMPS Ingest Server URL (works with or without :443):"))
        etRtmpsUrl = styledEditText(KeystoreVault.getRtmpsUrl(this), isPassword = false)
        cardStream.addView(etRtmpsUrl)

        val keyHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(4))
        }
        tvVaultSecurityLabel = TextView(this).apply {
            text = "Primary Stream Key (${KeystoreVault.getKeySecurityDescription()}):"
            setTextColor(Color.parseColor("#B0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvKeyStatusBadge = TextView(this).apply {
            text = KeystoreVault.getMaskedKeyStatusBadge(KeystoreVault.getStreamKey(this@MainActivity))
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedDrawable("#0D3652", "#00E5FF")
        }
        keyHeaderRow.addView(tvVaultSecurityLabel)
        keyHeaderRow.addView(tvKeyStatusBadge)
        cardStream.addView(keyHeaderRow)

        etStreamKey = styledEditText(KeystoreVault.getStreamKey(this), isPassword = true).apply {
            hint = "Paste your YouTube / Twitch / Kick Stream Key"
        }
        cardStream.addView(etStreamKey)

        btnShowHideKey = actionButton("Show Key", "#152338") { toggleKeyVisibility() }
        cardStream.addView(
            buttonRow(
                actionButton("Paste Key", "#152338") { pasteStreamKeyFromClipboard() },
                btnShowHideKey,
                actionButton("Clear Key", "#1E293B") { clearStreamKeyInput() },
                actionButton("Save Vault", "#0D3652") { saveVaultInputs(showToast = true) }
            )
        )

        cardStream.addView(
            buttonRow(
                actionButton("Get Key on Phone (YT Studio)", "#153969") { openYouTubeStudioInBrowser() },
                actionButton("Open YouTube App", "#152338") { openYouTubeAppQuick() }
            )
        )

        // Collapsible Accordion Drawer: 9:16 Shorts Key Slot + Simulcast + Dual-Format
        boxSimulcastDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = cardDrawable("#070D19", "#1E3A5F")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            layoutParams = lp
        }
        btnToggleSimulcastDrawer = actionButton(
            "▸ Simulcast, 9:16 Shorts Key & Dual-Format (Tap to Expand)",
            "#111D30"
        ) {
            val show = boxSimulcastDrawer.visibility != View.VISIBLE
            boxSimulcastDrawer.visibility = if (show) View.VISIBLE else View.GONE
            refreshAccordionHeaders()
        }

        boxSimulcastDrawer.addView(
            labelText("Slot #2 — 9:16 Vertical Shorts Key (Paste your Shorts key here; auto-enables switch below — keep 'Manual settings' OFF in YT Studio):")
        )
        etShortsKey = styledEditText(KeystoreVault.getShortsStreamKey(this), isPassword = true).apply {
            hint = "Paste 9:16 Vertical / Shorts Stream Key here"
        }
        btnUseShortsSlot = actionButton("", "#101929") {
            val nextUseSlot = !settings.useShortsKeySlot
            saveVaultInputs(showToast = false, autoEnableShortsIfNewlyAdded = false)
            updateSetting(settings.copy(useShortsKeySlot = nextUseSlot), requiresRestartIfLive = true)
        }
        boxSimulcastDrawer.addView(etShortsKey)
        boxSimulcastDrawer.addView(btnUseShortsSlot)

        boxSimulcastDrawer.addView(
            labelText("Multi-Platform Simulcast (Twitch, Kick, Rooter, Loco, TikTok Live or Custom RTMP):")
        )
        btnSimulcastMode = actionButton("", "#13284A") {
            toggleSimulcastSplitMode()
        }
        boxSimulcastDrawer.addView(btnSimulcastMode)

        btnSimulcastTwitch = actionButton("", "#101929") {
            saveVaultInputs(showToast = false)
            toggleSimulcastTarget(isTwitch = true)
        }
        etTwitchUrl = styledEditText(KeystoreVault.getTwitchUrl(this), isPassword = false).apply {
            hint = "Simulcast #1 Server URL (Twitch or Custom rtmp/rtmps://...)"
        }
        etTwitchKey = styledEditText(KeystoreVault.getTwitchStreamKey(this), isPassword = true).apply {
            hint = "Simulcast #1 Stream Key (live_...)"
        }

        btnSimulcastKick = actionButton("", "#101929") {
            saveVaultInputs(showToast = false)
            toggleSimulcastTarget(isTwitch = false)
        }
        etKickUrl = styledEditText(KeystoreVault.getKickUrl(this), isPassword = false).apply {
            hint = "Simulcast #2 Server URL (Kick / Rooter / Loco / Custom rtmp://...)"
        }
        etKickKey = styledEditText(KeystoreVault.getKickStreamKey(this), isPassword = true).apply {
            hint = "Simulcast #2 Stream Key (sk_...)"
        }

        btnDualCompanion = actionButton("", "#101929") {
            saveVaultInputs(showToast = false)
            toggleDualFormatCompanion()
        }
        btnCompanionBitrate = actionButton("", "#13284A") {
            cycleCompanionBitrate()
        }
        val btnSaveSimKeys = actionButton("Save Shorts & Simulcast URLs/Keys in Keystore", "#0D3652") {
            saveVaultInputs(showToast = true)
        }

        boxSimulcastDrawer.addView(btnSimulcastTwitch)
        boxSimulcastDrawer.addView(etTwitchUrl)
        boxSimulcastDrawer.addView(etTwitchKey)
        boxSimulcastDrawer.addView(btnSimulcastKick)
        boxSimulcastDrawer.addView(etKickUrl)
        boxSimulcastDrawer.addView(etKickKey)
        boxSimulcastDrawer.addView(btnDualCompanion)
        boxSimulcastDrawer.addView(btnCompanionBitrate)
        boxSimulcastDrawer.addView(btnSaveSimKeys)

        cardStream.addView(btnToggleSimulcastDrawer)
        cardStream.addView(boxSimulcastDrawer)

        tvNetTestResult = bodyText(
            "Tip: Keep 'Turn on manual settings' OFF in YouTube Studio so one Default Stream Key works automatically for both 16:9 Landscape & 9:16 Shorts. Adaptive RTMP chunking uses 64 KB on Wi-Fi and 4 KB on Mobile Data / Custom relays."
        )
        btnNetTest = actionButton("Test Internet: Download, Upload, RTT & Bitrate Advisor", "#153969") {
            saveVaultInputs(showToast = false)
            DashboardDialogs.runRtmpsNetworkCheck(
                activity = this,
                rawIngestUrl = etRtmpsUrl.text?.toString() ?: KeystoreVault.DEFAULT_YOUTUBE_RTMPS,
                settings = settings
            ) { running, statusText ->
                btnNetTest.isEnabled = !running && !SolStreamService.isBroadcastLive
                tvNetTestResult.text = statusText
            }
        }
        cardStream.addView(
            buttonRow(
                btnNetTest,
                actionButton("Reset Default URL", "#152338") {
                    etRtmpsUrl.setText(KeystoreVault.DEFAULT_YOUTUBE_RTMPS)
                    saveVaultInputs(showToast = true)
                }
            )
        )
        cardStream.addView(tvNetTestResult)
        container.addView(cardStream)

        // === CARD 3: SOLCORE ULTRA VIDEO ENGINE, DIRECT-TAP PILLS, VISUALS, SOLSHORTS™ & PRO OVERLAYS ===
        val cardVideo = sectionCard(
            heading = "3. Video Engine (360p–4K60 • 0.5–100M)",
            engineBadge = "SOLCORE ULTRA™",
            badgeHex = "#2979FF"
        )
        cardVideo.addView(
            bodyText("Plays at 120–185 Hz on your display while hardware silicon encodes steady 60/30 FPS. Tap any Resolution, Codec, FPS, or Framing pill below.")
        )

        cardVideo.addView(labelText("Direct-Tap Stream Resolution:"))
        val resRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        val resOptions = listOf(
            640 to "360p",
            854 to "480p",
            1280 to "720p",
            1920 to "1080p",
            2560 to "1440p",
            3840 to "4K"
        )
        resOptions.forEachIndexed { idx, (longW, label) ->
            val shortH = when (longW) {
                640 -> 360; 854 -> 480; 1280 -> 720; 1920 -> 1080; 2560 -> 1440; else -> 2160
            }
            val b = Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setPadding(dp(2), dp(5), dp(2), dp(5))
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (idx > 0) marginStart = dp(4)
                }
                setOnClickListener { selectResolutionDirect(longW, shortH) }
            }
            resPillButtons[longW] = b
            resRow.addView(b)
        }
        cardVideo.addView(resRow)

        cardVideo.addView(labelText("Direct-Tap Hardware Codec & Frame Rate:"))
        val codecFpsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        listOf("HEVC" to "HEVC", "H264" to "H.264", "AV1" to "AV1").forEachIndexed { idx, (cKey, cTitle) ->
            val b = Button(this).apply {
                text = cTitle
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setPadding(dp(2), dp(5), dp(2), dp(5))
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (idx > 0) marginStart = dp(4)
                }
                setOnClickListener { selectCodecDirect(cKey) }
            }
            codecPillButtons[cKey] = b
            codecFpsRow.addView(b)
        }
        listOf(30 to "30 FPS", 60 to "60 FPS").forEach { (fpsVal, fpsTitle) ->
            val b = Button(this).apply {
                text = fpsTitle
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setPadding(dp(2), dp(5), dp(2), dp(5))
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    marginStart = dp(5)
                }
                setOnClickListener {
                    updateSetting(settings.copy(fps = fpsVal), requiresRestartIfLive = true)
                }
            }
            fpsPillButtons[fpsVal] = b
            codecFpsRow.addView(b)
        }
        cardVideo.addView(codecFpsRow)

        cardVideo.addView(labelText("Direct-Tap Screen Framing (STRETCH = Full 16:9 Zero-Bars + 100% HUD):"))
        val framingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3); bottomMargin = dp(4) }
        }
        listOf("FIT" to "FIT", "STRETCH" to "STRETCH (16:9)", "SMART" to "SMART", "FILL" to "FILL").forEachIndexed { idx, (fKey, fTitle) ->
            val b = Button(this).apply {
                text = fTitle
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setPadding(dp(2), dp(5), dp(2), dp(5))
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (idx > 0) marginStart = dp(4)
                }
                setOnClickListener {
                    updateSetting(settings.copy(framingMode = fKey))
                }
            }
            framingPillButtons[fKey] = b
            framingRow.addView(b)
        }
        cardVideo.addView(framingRow)

        btnCodec = actionButton("", "#13284A") { cycleCodec() }
        btnResolution = actionButton("", "#13284A") { cycleResolution() }
        btnFps = actionButton("", "#13284A") {
            updateSetting(
                settings.copy(fps = if (settings.fps == 60) 30 else 60),
                requiresRestartIfLive = true
            )
        }
        btnOrientationFlip = actionButton("", "#13284A") { cycleOrientationAndFlip() }

        btnBitrateDown = actionButton("− Mbps", "#153969") { stepBitrate(-1) }
        btnBitrateCycle = actionButton("", "#0D3652") { stepBitrate(+1) }
        btnBitrateUp = actionButton("+ Mbps", "#153969") { stepBitrate(+1) }

        btnRateControl = actionButton("", "#13284A") { toggleRateControl() }
        btnAbr = actionButton("", "#101929") {
            updateSetting(settings.copy(adaptiveBitrate = !settings.adaptiveBitrate))
        }
        btnUpscaler = actionButton("", "#13284A") { cycleUpscaler() }
        btnFraming = actionButton("", "#13284A") { cycleFraming() }
        btnSmartZoom = actionButton("", "#13284A") { cycleSmartZoom() }

        cardVideo.addView(buttonRow(btnCodec, btnResolution))
        cardVideo.addView(buttonRow(btnFps, btnOrientationFlip))
        cardVideo.addView(buttonRow(btnBitrateDown, btnBitrateCycle, btnBitrateUp))
        cardVideo.addView(buttonRow(btnRateControl, btnAbr))
        cardVideo.addView(buttonRow(btnUpscaler, btnFraming, btnSmartZoom))

        cardVideo.addView(labelText("Stackable Live Visual Filters (Mix & Match Live):"))
        btnFilmic = actionButton("", "#101929") {
            updateSetting(settings.copy(cinematicFilmic = !settings.cinematicFilmic))
        }
        btnAmoledVivid = actionButton("", "#101929") {
            updateSetting(settings.copy(amoledVivid = !settings.amoledVivid))
        }
        btnShadowBoost = actionButton("", "#101929") {
            updateSetting(settings.copy(shadowBoost = !settings.shadowBoost))
        }
        btnColorTemp = actionButton("", "#13284A") { cycleColorTemp() }
        btnSharpen = actionButton("", "#101929") {
            updateSetting(
                newSettings = settings.copy(gpuSharpen = !settings.gpuSharpen),
                userToggledSharp = true
            )
        }

        cardVideo.addView(buttonRow(btnFilmic, btnAmoledVivid))
        cardVideo.addView(buttonRow(btnShadowBoost, btnColorTemp))
        cardVideo.addView(btnSharpen)

        // Collapsible Accordion Drawer: SolShorts™ Studio, Scenes, Pro Overlays (SolHUD, QR, PNGTuber), Privacy & SolCodec
        boxSceneProDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = cardDrawable("#070D19", "#1E3A5F")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            layoutParams = lp
        }
        btnToggleSceneProDrawer = actionButton(
            "▸ SolShorts™, Scenes, SolHUD™, QR, Avatar & SolCodec™",
            "#111D30"
        ) {
            val show = boxSceneProDrawer.visibility != View.VISIBLE
            boxSceneProDrawer.visibility = if (show) View.VISIBLE else View.GONE
            refreshAccordionHeaders()
        }

        boxSceneProDrawer.addView(labelText("SolShorts™ 9:16 Studio Controls (All Opt-In • 0% Extra GPU When OFF):"))
        btnShortsAnchor = actionButton("", "#13284A") { cycleShortsVerticalAnchor() }
        btnShortsHeaderLayout = actionButton("", "#101929") {
            updateSetting(settings.copy(shortsHeaderLayout = !settings.shortsHeaderLayout))
        }
        btnShortsScoreLoupe = actionButton("", "#101929") { cycleShortsScoreLoupe() }
        btnShortsMinimapLoupe = actionButton("", "#101929") { cycleShortsMinimapLoupe() }
        btnShortsCaptureCooler = actionButton("", "#0D3652") {
            updateSetting(settings.copy(shortsCaptureCooler = !settings.shortsCaptureCooler), requiresRestartIfLive = true)
        }
        btnPreviewShortsSafeZones = actionButton("👁️ Preview 9:16 YouTube Shorts Safe Zones", "#153969") {
            saveVaultInputs(showToast = false)
            DashboardDialogs.showShortsSafeZonePreviewDialog(this, settings)
        }
        boxSceneProDrawer.addView(buttonRow(btnShortsAnchor, btnShortsHeaderLayout))
        boxSceneProDrawer.addView(buttonRow(btnShortsScoreLoupe, btnShortsMinimapLoupe))
        boxSceneProDrawer.addView(btnShortsCaptureCooler)
        boxSceneProDrawer.addView(btnPreviewShortsSafeZones)

        boxSceneProDrawer.addView(labelText("SolHUD™ Live GPU Telemetry Badge (Visible Even in Single-App Capture):"))
        btnHudTelemetryToggle = actionButton("", "#101929") {
            updateSetting(settings.copy(hudTelemetryEnabled = !settings.hudTelemetryEnabled))
        }
        btnHudTelemetryCorner = actionButton("", "#13284A") { cycleHudTelemetryCorner() }
        boxSceneProDrawer.addView(buttonRow(btnHudTelemetryToggle, btnHudTelemetryCorner))

        boxSceneProDrawer.addView(labelText("Corner QR Code Overlay (Offline UPI / YouTube / Discord — Max 200 Chars):"))
        etQrOverlayText = styledEditText(settings.qrOverlayText, isPassword = false).apply {
            hint = "upi://pay?pa=name@upi&pn=Sol  or  https://youtube.com/@Sol_CODM"
        }
        btnQrOverlayToggle = actionButton("", "#101929") { toggleQrOverlayFromDashboard() }
        btnQrOverlayCorner = actionButton("", "#13284A") { cycleQrOverlayCorner() }
        boxSceneProDrawer.addView(etQrOverlayText)
        boxSceneProDrawer.addView(buttonRow(btnQrOverlayToggle, btnQrOverlayCorner))

        boxSceneProDrawer.addView(labelText("Zero-Camera 2-PNG PNGTuber Avatar (Syncs to Mic Voice • Built-In Striker Default):"))
        btnPickPngTuberIdle = actionButton("", "#13284A") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.PNGTUBER_IDLE
            imageAssetPickerLauncher.launch("image/*")
        }
        btnPickPngTuberTalk = actionButton("", "#13284A") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.PNGTUBER_TALK
            imageAssetPickerLauncher.launch("image/*")
        }
        btnPngTuberToggle = actionButton("", "#101929") {
            updateSetting(settings.copy(pngTuberEnabled = !settings.pngTuberEnabled))
        }
        btnPngTuberCorner = actionButton("", "#13284A") { cyclePngTuberCorner() }
        btnPngTuberSize = actionButton("", "#13284A") { cyclePngTuberSize() }
        boxSceneProDrawer.addView(buttonRow(btnPickPngTuberIdle, btnPickPngTuberTalk))
        boxSceneProDrawer.addView(buttonRow(btnPngTuberToggle, btnPngTuberCorner, btnPngTuberSize))

        boxSceneProDrawer.addView(labelText("Zero-Permission Saved Graphics (Tap to Pick from Gallery):"))
        btnPickStartImg = actionButton("", "#13284A") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.STARTING_SOON
            imageAssetPickerLauncher.launch("image/*")
        }
        btnPickBrbImg = actionButton("", "#13284A") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.BRB_SCENE
            imageAssetPickerLauncher.launch("image/*")
        }
        btnPickOverlayPng = actionButton("", "#101929") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.OVERLAY_PNG
            imageAssetPickerLauncher.launch("image/*")
        }
        btnOverlayPosition = actionButton("", "#101929") { cycleUnifiedOverlayLogo() }
        btnPickShortsPoster = actionButton("", "#13284A") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.SHORTS_POSTER
            imageAssetPickerLauncher.launch("image/*")
        }
        val btnClearGraphics = actionButton("Clear Saved Graphics", "#1E293B") {
            SolSceneAssetStore.AssetSlot.entries.forEach { SolSceneAssetStore.clearCustomAsset(this, it) }
            updateSetting(settings.copy(overlayImageEnabled = false))
            Toast.makeText(
                this,
                "Cleared custom scenes, logo & avatar PNGs (reverted to built-in slates/default avatar).",
                Toast.LENGTH_SHORT
            ).show()
        }

        boxSceneProDrawer.addView(buttonRow(btnPickStartImg, btnPickBrbImg))
        boxSceneProDrawer.addView(buttonRow(btnPickOverlayPng, btnPickShortsPoster))
        boxSceneProDrawer.addView(buttonRow(btnOverlayPosition, btnClearGraphics))

        boxSceneProDrawer.addView(labelText("Built-In GPU Live Text Banner (Updates Live on Next Frame):"))
        etBannerText = styledEditText(settings.liveBannerText, isPassword = false).apply {
            hint = "e.g. SUB GOAL 75 • @Sol_CODM • 120 FPS LIVE"
        }
        btnBannerToggle = actionButton("", "#101929") {
            val txt = etBannerText.text?.toString()?.trim()?.take(80) ?: ""
            SolSceneAssetStore.bumpRevision()
            updateSetting(settings.copy(liveBannerText = txt, liveBannerEnabled = !settings.liveBannerEnabled))
        }
        btnBannerPos = actionButton("", "#13284A") {
            val txt = etBannerText.text?.toString()?.trim()?.take(80) ?: ""
            val nextPos = if (settings.liveBannerPosition == "TOP") "BOTTOM" else "TOP"
            SolSceneAssetStore.bumpRevision()
            updateSetting(settings.copy(liveBannerText = txt, liveBannerPosition = nextPos))
        }
        boxSceneProDrawer.addView(etBannerText)
        boxSceneProDrawer.addView(buttonRow(btnBannerToggle, btnBannerPos))

        btnShortsBackdrop = actionButton("", "#13284A") { cycleShortsBackdrop() }
        btnMinimapShield = actionButton("", "#101929") { cycleMinimapShield() }
        btnAutoBlurKeyboard = actionButton("", "#101929") {
            updateSetting(settings.copy(autoBlurKeyboard = !settings.autoBlurKeyboard))
        }
        boxSceneProDrawer.addView(btnShortsBackdrop)
        boxSceneProDrawer.addView(btnMinimapShield)
        boxSceneProDrawer.addView(btnAutoBlurKeyboard)

        boxSceneProDrawer.addView(labelText("SolCodec™ Silicon & OpenGL ES 3.0 Fast-FP16 Controls:"))
        btnGlEngine = actionButton("", "#13284A") { cycleGlEngine() }
        btnHdr10 = actionButton("", "#101929") { toggleHdr10() }
        btnRoiMode = actionButton("", "#101929") { cycleRoiMode() }
        btnKeyframeAndBFrames = actionButton("", "#13284A") { cycleKeyframeInterval() }
        btnQpAndLatency = actionButton("", "#13284A") { cycleQpAndLatency() }

        boxSceneProDrawer.addView(buttonRow(btnGlEngine, btnHdr10))
        boxSceneProDrawer.addView(btnRoiMode)
        boxSceneProDrawer.addView(buttonRow(btnKeyframeAndBFrames, btnQpAndLatency))

        cardVideo.addView(btnToggleSceneProDrawer)
        cardVideo.addView(boxSceneProDrawer)
        container.addView(cardVideo)

        // === CARD 4: SOLLUXE™ 3.0 PURE 6-STAGE VOICE STUDIO & 3D GAME SOUNDSTAGE ===
        val cardAudio = sectionCard(
            heading = "4. SolLuxe™ 3.0 Voice Studio & Game Audio",
            engineBadge = "SOLLUXE™ 3.0",
            badgeHex = "#8B5CF6"
        )
        cardAudio.addView(
            bodyText("6-Stage 64-Bit Pure Condenser Chain: 55/68/85Hz Gender HPF, <0.6ms Raised-Cosine Impulse Clamper, Native C 48kHz RNNoise + Hybrid VAD Expander, Split-Band Plosive Guard, 4×3 Condenser EQ & 4× Peak Limiter.")
        )

        btnMicDevice = actionButton("", "#13284A") { cycleMicInputDevice() }
        btnMicSource = actionButton("", "#13284A") { cycleMicSourceMode() }
        btnAudioBitrate = actionButton("", "#13284A") { cycleAudioBitrate() }

        cardAudio.addView(btnMicDevice)
        cardAudio.addView(buttonRow(btnMicSource, btnAudioBitrate))

        cardAudio.addView(labelText("Voice Profile (Pick 1 — Auto-Matches 55/68/85Hz HPF, Condenser EQ, Sidechain & De-Esser):"))
        btnGenderNeutral = actionButton("Neutral (68Hz)", "#101929") {
            updateSetting(MicDspProcessor.applyGenderProfileToSettings(settings, "NEUTRAL"))
        }
        btnGenderMale = actionButton("Male (55Hz)", "#101929") {
            updateSetting(MicDspProcessor.applyGenderProfileToSettings(settings, "MALE"))
        }
        btnGenderFemale = actionButton("Female (85Hz)", "#101929") {
            updateSetting(MicDspProcessor.applyGenderProfileToSettings(settings, "FEMALE"))
        }
        cardAudio.addView(buttonRow(btnGenderNeutral, btnGenderMale, btnGenderFemale))

        btnVoicePreset = actionButton("", "#13284A") { cycleVoicePreset() }
        btnNoiseSuppressMode = actionButton("", "#101929") { cycleNoiseSuppressMode() }
        btnNoiseSuppressLevel = actionButton("", "#101929") {
            val nextLvl = (settings.rnnoiseLevel + 1) % 4
            val nextMode = if (nextLvl == 0) "OFF" else if (settings.noiseSuppressMode == "OFF") "RNNOISE" else settings.noiseSuppressMode
            updateSetting(settings.copy(rnnoiseLevel = nextLvl, noiseSuppressMode = nextMode))
        }
        cardAudio.addView(btnVoicePreset)
        cardAudio.addView(buttonRow(btnNoiseSuppressMode, btnNoiseSuppressLevel))

        cardAudio.addView(labelText("Post-DSP Mic Boost (50%–250% • Protected by 4× Peak Limiter):"))
        btnMicGain = actionButton("", "#0D3652") { cycleMicGain() }
        val micSliderRow = buildVolumeSeekBarRow(
            minPct = 50,
            maxPct = 250,
            initialPct = settings.micGainPercent,
            onStepClick = { delta ->
                val next = (settings.micGainPercent + delta).coerceIn(50, 250)
                updateSetting(settings.copy(micGainPercent = next))
            },
            onSeekChanged = { newPct ->
                if (!suppressSeekBarCallback) {
                    updateSetting(settings.copy(micGainPercent = newPct))
                }
            }
        )
        seekMicGain = micSliderRow.second
        cardAudio.addView(btnMicGain)
        cardAudio.addView(micSliderRow.first)

        cardAudio.addView(labelText("Internal Stereo Game Mix (0%–250% • Protected by 4× Peak Limiter):"))
        btnGameGain = actionButton("", "#153969") { cycleGameGain() }
        val gameSliderRow = buildVolumeSeekBarRow(
            minPct = 0,
            maxPct = 250,
            initialPct = settings.gameGainPercent,
            onStepClick = { delta ->
                val next = (settings.gameGainPercent + delta).coerceIn(0, 250)
                updateSetting(settings.copy(gameGainPercent = next))
            },
            onSeekChanged = { newPct ->
                if (!suppressSeekBarCallback) {
                    updateSetting(settings.copy(gameGainPercent = newPct))
                }
            }
        )
        seekGameGain = gameSliderRow.second
        cardAudio.addView(btnGameGain)
        cardAudio.addView(gameSliderRow.first)

        btnDucking = actionButton("", "#101929") { cycleDucking() }
        btnMicDelay = actionButton("", "#13284A") { cycleMicSyncDelay() }
        cardAudio.addView(buttonRow(btnDucking, btnMicDelay))

        cardAudio.addView(labelText("Stackable Stereo Game Soundstage (Stream-Only • Phase-Safe 62Hz Voice Duck):"))
        btnGameWide = actionButton("", "#101929") {
            updateSetting(settings.copy(gameCinemaWideEnabled = !settings.gameCinemaWideEnabled))
        }
        btnGameSubPunch = actionButton("", "#101929") {
            updateSetting(settings.copy(gameSubPunchEnabled = !settings.gameSubPunchEnabled))
        }
        btnGameFootsteps = actionButton("", "#101929") {
            updateSetting(settings.copy(gameFootstepFocusEnabled = !settings.gameFootstepFocusEnabled))
        }
        btnGameBlastTamer = actionButton("", "#101929") {
            updateSetting(settings.copy(gameBlastTamerEnabled = !settings.gameBlastTamerEnabled))
        }
        cardAudio.addView(buttonRow(btnGameWide, btnGameSubPunch))
        cardAudio.addView(buttonRow(btnGameFootsteps, btnGameBlastTamer))

        // Collapsible Accordion Drawer: Individual 6-Stage Voice Controls
        boxVoiceStageDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = cardDrawable("#070D19", "#1E3A5F")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            layoutParams = lp
        }
        btnToggleVoiceStageDrawer = actionButton(
            "▸ Individual Voice Stage Switches (Tap to Customize)",
            "#111D30"
        ) {
            val show = boxVoiceStageDrawer.visibility != View.VISIBLE
            boxVoiceStageDrawer.visibility = if (show) View.VISIBLE else View.GONE
            refreshAccordionHeaders()
        }

        btnStageHpf = actionButton("", "#101929") { cycleHpf() }
        btnStageTransient = actionButton("", "#101929") {
            updateSetting(settings.copy(transientTamerEnabled = !settings.transientTamerEnabled))
        }
        btnStageGate = actionButton("", "#101929") {
            updateSetting(settings.copy(noiseGateEnabled = !settings.noiseGateEnabled))
        }
        btnStageEq = actionButton("", "#101929") {
            updateSetting(settings.copy(voiceEqEnabled = !settings.voiceEqEnabled))
        }
        btnStageVelvet = actionButton("", "#101929") {
            updateSetting(settings.copy(velvetWarmthEnabled = !settings.velvetWarmthEnabled))
        }
        btnStageComp = actionButton("", "#101929") {
            updateSetting(settings.copy(compressorEnabled = !settings.compressorEnabled))
        }
        btnStageWhisper = actionButton("", "#101929") {
            updateSetting(settings.copy(whisperLiftEnabled = !settings.whisperLiftEnabled))
        }
        btnStageSlowAgc = actionButton("", "#101929") {
            updateSetting(settings.copy(slowAgcEnabled = !settings.slowAgcEnabled))
        }
        btnStageDeEss = actionButton("", "#101929") {
            updateSetting(settings.copy(deEsserEnabled = !settings.deEsserEnabled))
        }
        btnStageSpeakerAec = actionButton("", "#101929") {
            updateSetting(settings.copy(speakerAecEnabled = !settings.speakerAecEnabled))
        }
        btnExtraSafeguards = actionButton("", "#101929") { cycleExtraSafeguards() }

        boxVoiceStageDrawer.addView(buttonRow(btnStageHpf, btnStageTransient))
        boxVoiceStageDrawer.addView(buttonRow(btnStageGate, btnStageEq))
        boxVoiceStageDrawer.addView(buttonRow(btnStageVelvet, btnStageDeEss))
        boxVoiceStageDrawer.addView(buttonRow(btnStageComp, btnStageWhisper))
        boxVoiceStageDrawer.addView(buttonRow(btnStageSlowAgc, btnStageSpeakerAec))
        boxVoiceStageDrawer.addView(btnExtraSafeguards)

        cardAudio.addView(btnToggleVoiceStageDrawer)
        cardAudio.addView(boxVoiceStageDrawer)

        tvMicTestStatus = bodyText(
            "10s Studio Voice Test (Runs your exact live SolLuxe™ 3.0 Pure chain so you hear your real broadcast voice before going live):"
        )
        btnRecordMicTest = actionButton("Record 10s Test", "#0D3652") { runTenSecMicCalibration() }
        btnPlayMicTest = actionButton("Play Test", "#153969") { playMicTestSample() }
        btnApplyAdvisor = actionButton("Apply Advisor Recommendation", "#4C1D95") {
            val adv = SolDualAudioEngine.lastCalibrationAdvice
            if (adv != null) {
                updateSetting(adv.applyTo(settings))
                Toast.makeText(this, "Applied calibrated voice settings!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Run the 10s Voice Test first!", Toast.LENGTH_SHORT).show()
            }
        }
        cardAudio.addView(tvMicTestStatus)
        cardAudio.addView(
            buttonRow(
                btnRecordMicTest,
                btnPlayMicTest,
                actionButton("Delete", "#152338") {
                    if (!micTestBusy.get()) {
                        SolDualAudioEngine.deleteMicTestFile(this)
                        tvMicTestStatus.text = "Saved 10s voice test sample deleted."
                    }
                }
            )
        )
        cardAudio.addView(btnApplyAdvisor)
        container.addView(cardAudio)

        // === CARD 5: LAUNCH BROADCAST, SAVED CLIPS, SESSION REPORTS, BENCHMARK & SUPPORT ===
        val cardLaunch = sectionCard(
            heading = "5. Launch Broadcast, Clips & Reports",
            engineBadge = "LAUNCH DECK",
            badgeHex = "#00E5FF"
        )

        btnGoLive = actionButton("GO LIVE — START STREAM", "#00695C") {
            onGoLiveClicked()
        }.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(54)
        }
        cardLaunch.addView(btnGoLive)

        btnReportsQuick = actionButton("Session Reports & History", "#153969") {
            DashboardDialogs.showDiagnosticsDialog(this, showingArchived = false) { refreshAllUi() }
        }
        btnSavedClipsQuick = actionButton("★ Saved Clips & AI Highlights", "#4C1D95") {
            DashboardDialogs.showSavedClipsDialog(this) { refreshAllUi() }
        }
        btnGlipBench = actionButton("Benchmark Other App", "#152338") {
            toggleCompetitorBenchmark()
        }

        cardLaunch.addView(buttonRow(btnReportsQuick, btnSavedClipsQuick))
        cardLaunch.addView(
            buttonRow(
                btnGlipBench,
                actionButton("Refresh Mics / HW Scan", "#152338") { refreshHardwareAndMicsAsync() },
                actionButton("Check GitHub Update", "#152338") {
                    DashboardDialogs.checkGitHubReleaseUpdate(this, appVersionName)
                }
            )
        )
        cardLaunch.addView(
            actionButton("❤️ Enjoying SolStream? Subscribe to @Sol_CODM to Support!", "#881337") {
                openDeveloperYouTubeChannel()
            }
        )
        container.addView(cardLaunch)

        // === FOOTER ===
        val footer = TextView(this).apply {
            text = "ॐ नमः शिवाय\nMade by Sol (@Sol_CODM) • SolApex™ HyperStream v$appVersionName (SolCore Ultra • SolLuxe™ 3.0 Pure • SolRelay Omega • SolCodec)"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(10))
        }
        container.addView(footer)

        rootScroll.addView(container)
        outerRoot.addView(fixedHeaderBox)
        outerRoot.addView(headerDivider)
        outerRoot.addView(rootScroll)
        applyResponsiveContainerPadding()
        return outerRoot
    }

    private fun openGlossaryGuide() {
        DashboardDialogs.showPlainEnglishGlossaryDialog(
            activity = this,
            appVersionName = appVersionName,
            onOpenBatteryUnrestricted = { openBatteryUnrestricted() }
        )
    }

    private fun openAllBrandSetupGuide() {
        DashboardDialogs.showAllBrandSetupGuide(
            activity = this,
            onOpenBatteryUnrestricted = { openBatteryUnrestricted() },
            onOpenAccessibilityDisclosure = { showPlayStoreCompliantAccessibilityDisclosure(allowSkipToStream = false) }
        )
    }

    private fun activeStrictDestinationName(): String = when {
        KeystoreVault.isTwitchLimited(settings) -> "Twitch"
        KeystoreVault.isKickLimited(settings) -> "Kick"
        else -> settings.platformPreset
    }

    private fun makeStatusChip(
        initialText: String,
        fillHex: String,
        strokeHex: String,
        textHex: String
    ): TextView {
        return TextView(this).apply {
            text = initialText
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.2f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(9), dp(3), dp(9), dp(3))
            background = roundedDrawable(fillHex, strokeHex)
        }
    }

    private fun makeHeroTile(
        caption: String,
        initialValue: String,
        onClick: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(11), dp(10), dp(11))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0D1B30"), Color.parseColor("#081120"))
            ).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#1E4E79"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val valTv = TextView(this).apply {
            text = initialValue
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
        }
        val capTv = TextView(this).apply {
            text = caption
            setTextColor(Color.parseColor("#8FA3BD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.2f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(2), 0, 0)
        }
        box.addView(valTv)
        box.addView(capTv)
        return box to valTv
    }

    private fun buildVolumeSeekBarRow(
        minPct: Int,
        maxPct: Int,
        initialPct: Int,
        onStepClick: (Int) -> Unit,
        onSeekChanged: (Int) -> Unit
    ): Pair<LinearLayout, SeekBar> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5); bottomMargin = dp(3) }
        }
        val btnMinus = Button(this).apply {
            text = "−10%"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = roundedDrawable("#13284A", "#00E5FF")
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(36))
            setOnClickListener { onStepClick(-10) }
        }
        val seek = SeekBar(this).apply {
            max = (maxPct - minPct) / 5
            progress = ((initialPct.coerceIn(minPct, maxPct) - minPct) / 5)
            progressTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progressVal: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val pct = (minPct + progressVal * 5).coerceIn(minPct, maxPct)
                        onSeekChanged(pct)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        val btnPlus = Button(this).apply {
            text = "+10%"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = roundedDrawable("#13284A", "#00E5FF")
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(36))
            setOnClickListener { onStepClick(+10) }
        }
        row.addView(btnMinus)
        row.addView(seek)
        row.addView(btnPlus)
        return row to seek
    }

    private fun styleToggleBtn(
        btn: Button,
        isOn: Boolean,
        activeFillHex: String = "#0D3652",
        activeStrokeHex: String = "#00E5FF"
    ) {
        if (isOn) {
            btn.setTextColor(Color.parseColor("#F8FAFC"))
            btn.background = roundedDrawable(activeFillHex, activeStrokeHex)
        } else {
            btn.setTextColor(Color.parseColor("#94A3B8"))
            btn.background = roundedDrawable("#0F1727", "#223149")
        }
    }

    private fun applyTierDefaultsToSettings(
        base: StreamSettings,
        tier: DeviceTier,
        scan: DeviceScanSummary
    ): StreamSettings {
        return KeystoreVault.applyPlatformAndVoiceAwareTierPreset(base, tier, scan)
    }

    private fun refreshHardwareAndMicsAsync() {
        if (SolStreamService.isBroadcastLive || CodecCapabilityProbe.isBroadcastStartingOrLive) {
            micOptions = CodecCapabilityProbe.listAvailableMicInputs(this)
            refreshAllUi()
            Toast.makeText(
                this,
                "Microphone list refreshed live! (Hardware encoder scan skipped while stream is active.)",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!hwScanBusy.compareAndSet(false, true)) return
        Toast.makeText(this, "Scanning hardware encoders & microphones...", Toast.LENGTH_SHORT).show()
        CodecCapabilityProbe.scanDeviceAsync(this, forceRefresh = true) { freshScan ->
            hwScanBusy.set(false)
            if (!isFinishing && !isDestroyed) {
                scanSummary = freshScan
                micOptions = CodecCapabilityProbe.listAvailableMicInputs(this)
                tvHeaderSubtitle.text =
                    "${scanSummary.friendlyHardwareLine()}\n${scanSummary.toBadgeText()}"
                refreshAllUi()
                Toast.makeText(this, "Hardware & mic list refreshed!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshAccordionHeaders() {
        val simOpen = boxSimulcastDrawer.visibility == View.VISIBLE
        val activeSimCount = (if (settings.simulcastTwitchEnabled) 1 else 0) +
            (if (settings.simulcastKickEnabled) 1 else 0) +
            (if (!settings.isVertical && settings.dualFormatCompanionEnabled) 1 else 0)
        val simSummary = if (activeSimCount > 0) "$activeSimCount Active" else "Primary Only"
        btnToggleSimulcastDrawer.text = if (simOpen) {
            "▾ Simulcast, 9:16 Shorts Key & Dual-Format ($simSummary)"
        } else {
            "▸ Simulcast, 9:16 Shorts Key & Dual-Format ($simSummary)"
        }

        val sceneOpen = boxSceneProDrawer.visibility == View.VISIBLE
        val proTags = mutableListOf<String>()
        if (settings.shortsScoreLoupe != "OFF") proTags.add("FeedL")
        if (settings.shortsMinimapLoupe != "OFF") proTags.add("MapL")
        if (settings.shortsVerticalAnchor == "ABOVE_CHAT") proTags.add("Above")
        if (settings.hudTelemetryEnabled) proTags.add("HUD")
        if (settings.qrOverlayEnabled && settings.qrOverlayText.isNotBlank()) proTags.add("QR")
        if (settings.pngTuberEnabled) proTags.add("Avatar")
        if (settings.overlayImageEnabled) {
            proTags.add(SolStreamService.formatCompactOverlayPositionTag(true, settings.overlayPosition))
        }
        if (settings.minimapShieldEnabled) proTags.add("Blur")
        val sceneSummary = if (proTags.isEmpty()) "All OFF" else proTags.joinToString(" • ")
        btnToggleSceneProDrawer.text = if (sceneOpen) {
            "▾ SolShorts™, Scenes, SolHUD™, QR & SolCodec™ ($sceneSummary)"
        } else {
            "▸ SolShorts™, Scenes, SolHUD™, QR & SolCodec™ ($sceneSummary)"
        }

        val voiceOpen = boxVoiceStageDrawer.visibility == View.VISIBLE
        val hpfTag = if (settings.hpfCutoffHz == 0) "HPF:OFF" else "${settings.hpfCutoffHz}Hz"
        btnToggleVoiceStageDrawer.text = if (voiceOpen) {
            "▾ Individual Voice Stages ($hpfTag • ${settings.voiceGenderProfile})"
        } else {
            "▸ Individual Voice Stages ($hpfTag • ${settings.voiceGenderProfile})"
        }
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

        val longDim = max(settings.width, settings.height)
        val shortDim = min(settings.width, settings.height)
        val heroResShort = when {
            longDim >= 3840 -> "4K (2160p)"
            longDim >= 2560 -> "1440p (2K)"
            longDim >= 1920 -> "1080p (FHD)"
            longDim >= 1280 -> "720p (HD)"
            longDim >= 800 -> "480p"
            else -> "360p"
        }
        val compactRes = when {
            longDim >= 3840 -> "4K"
            longDim >= 2560 -> "1440p"
            longDim >= 1920 -> "1080p"
            longDim >= 1280 -> "720p"
            longDim >= 800 -> "480p"
            else -> "360p"
        }
        val strictDest = KeystoreVault.isStrictH264Destination(settings)
        val destMaxLong = KeystoreVault.maxLongSideForDestination(settings)
        val destMaxMbps = KeystoreVault.maxVideoBitrateKbpsForDestination(settings) / 1000
        val effectiveActiveCodec = if (strictDest) "H264" else settings.codec.uppercase(Locale.US)

        // Glanceable Header Chips & Live Protect Long Streams Banner
        tvChipAudio.text = "SolLuxe 3.0 • ${settings.voicePreset}"
        tvChipVideo.text = "${compactRes}${settings.fps} • $effectiveActiveCodec"
        if (battUnrestricted) {
            tvChipProtection.text = "Protected ✓"
            tvChipProtection.setTextColor(Color.parseColor("#A7F3D0"))
            tvChipProtection.background = roundedDrawable("#0A2B22", "#10B981")

            boxProtectBanner.background = cardDrawable("#08201A", "#10B981")
            tvProtectTitle.text = "🛡️ Long-Stream Protection: Unrestricted ✓"
            tvProtectTitle.setTextColor(Color.parseColor("#A7F3D0"))
            tvProtectTip.visibility = View.VISIBLE
            tvProtectTip.text = DashboardDialogs.buildBrandAntiKillOneLiner()
            btnProtectAction.text = "Tips"
            btnProtectAction.setTextColor(Color.parseColor("#A7F3D0"))
            btnProtectAction.background = roundedDrawable("#0D3329", "#10B981")
        } else {
            tvChipProtection.text = "Setup Needed"
            tvChipProtection.setTextColor(Color.parseColor("#FDE68A"))
            tvChipProtection.background = roundedDrawable("#2B1D0A", "#F59E0B")

            boxProtectBanner.background = cardDrawable("#1F170A", "#F59E0B")
            tvProtectTitle.text = "🛡️ Long-Stream Protection: Setup Needed"
            tvProtectTitle.setTextColor(Color.parseColor("#FDE68A"))
            tvProtectTip.visibility = View.VISIBLE
            tvProtectTip.text = DashboardDialogs.buildBrandAntiKillOneLiner()
            btnProtectAction.text = "Protect"
            btnProtectAction.setTextColor(Color.parseColor("#050A14"))
            btnProtectAction.background = roundedDrawable("#F59E0B", "#FDE68A")
        }

        val idleHealth = CodecCapabilityProbe.readLiveHardwareHealth(this)
        tvLiveBanner.text = when {
            SolStreamService.needsProjectionReapproval ->
                "STATUS: Screen capture paused by Android (lock/system). Tap orange button below to re-approve!"
            isLive ->
                "STATUS: ${SolStreamService.currentLiveStateLabel} • Live controls & Floating Pill sync in 0 ms"
            SolStreamService.isGlipBenchmarkRunning ->
                "STATUS: Passive Benchmark Recording Thermal & RAM for $benchmarkTargetAppName..."
            else ->
                "STATUS: ${idleHealth.toIdleStatusBanner()}"
        }
        btnReapproveCapture.visibility = if (SolStreamService.needsProjectionReapproval) View.VISIBLE else View.GONE

        // 3-Tile Hero Summary Bar
        val mbpsStr = formatBitrateMbps(settings.targetBitrateKbps)
        tvHeroResValue.text = "🎥 $heroResShort"
        tvHeroFpsValue.text = "⚡ ${settings.fps} FPS"
        tvHeroBitrateValue.text = "📡 $mbpsStr"

        // Card 0: Tier Buttons
        val activeTier = DeviceTier.fromId(settings.deviceTier)
        val recTier = scanSummary.recommendedTier
        tierButtons.forEach { (t, btn) ->
            val selected = (t == activeTier)
            val fill = when {
                selected && t == DeviceTier.ULTRA -> "#4C1D95"
                selected -> "#0D3652"
                else -> "#0F1727"
            }
            val stroke = if (selected) "#00E5FF" else if (t == recTier) "#38BDF8" else "#223149"
            btn.setTextColor(Color.parseColor(if (selected) "#F8FAFC" else "#94A3B8"))
            btn.background = roundedDrawable(fill, stroke)
        }
        tvTierDescription.text =
            "Active Tier: ${activeTier.title} (${activeTier.badgeSummary})\n" +
                "Recommended for ${scanSummary.deviceModel}: ${recTier.title} Tier"

        // Card 1: Pre-Flight Readiness & Smart Auto-Collapse
        val allFourReady = micGranted && notifGranted && battUnrestricted && accEnabled
        tvPreflightStatus.text =
            "• Mic Permission: ${okWait(micGranted)}   • Notifications: ${okWait(notifGranted)}\n" +
                "• Battery Unrestricted: ${okWait(battUnrestricted)}   • Accessibility Dock: ${okWait(accEnabled)}"
        tvPreflightAudioWifiLine.text = SolDualAudioEngine.inspectPreFlightAudioAndWifiCoexistence(this)

        styleToggleBtn(btnGrantMicNotif, isOn = micGranted && notifGranted)
        styleToggleBtn(btnUnrestrictedBatt, isOn = battUnrestricted)
        styleToggleBtn(btnTapAccessibilityFirst, isOn = accEnabled)
        styleToggleBtn(btnUnlockRestricted, isOn = accEnabled, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        if (allFourReady) {
            btnPreflightCollapsedToggle.visibility = View.VISIBLE
            btnPreflightCollapsedToggle.text = if (preflightManuallyExpanded) {
                "▾ All 4 Pre-Flight Checks READY (Tap to Collapse)"
            } else {
                "✅ All 4 Pre-Flight Checks READY (Tap to Expand Setup & Anti-Kill Guide)"
            }
            boxPreflightButtonsContainer.visibility = if (preflightManuallyExpanded) View.VISIBLE else View.GONE
        } else {
            btnPreflightCollapsedToggle.visibility = View.GONE
            boxPreflightButtonsContainer.visibility = View.VISIBLE
        }
        tvRestrictedHint.visibility = if (accEnabled) View.GONE else View.VISIBLE

        // Card 2: 4 Studio Profiles, 4-Pill Platform Selector, Key Vault Badge, Simulcast & Speed Test
        val loadedProfiles = KeystoreVault.loadAllStudioProfiles(this, settings, scanSummary)
        for (idx in studioProfileButtons.indices) {
            val btn = studioProfileButtons[idx]
            val prof = loadedProfiles.getOrNull(idx) ?: continue
            btn.text = if (prof.isUserCustomized) "★ ${prof.name}" else prof.name
            styleToggleBtn(
                btn,
                isOn = (idx == activeStudioProfileSlot),
                activeFillHex = "#0D3652",
                activeStrokeHex = "#00E5FF"
            )
        }

        val activePlat = settings.platformPreset.uppercase(Locale.US)
        platformPillButtons.forEach { (pKey, btn) ->
            styleToggleBtn(btn, isOn = (pKey == activePlat))
        }
        tvVaultSecurityLabel.text = "Primary Stream Key (AES-256-GCM • ${KeystoreVault.getKeySecurityDescription()}):"
        val currentTypedOrSavedKey = etStreamKey.text?.toString()?.takeIf { it.isNotBlank() } ?: KeystoreVault.getStreamKey(this)
        val hasPrimaryKey = KeystoreVault.sanitizeStreamKeyInput(currentTypedOrSavedKey).isNotEmpty()
        tvKeyStatusBadge.text = KeystoreVault.getMaskedKeyStatusBadge(currentTypedOrSavedKey)
        tvKeyStatusBadge.setTextColor(Color.parseColor(if (hasPrimaryKey) "#00E5FF" else "#FFC400"))
        tvKeyStatusBadge.background = roundedDrawable(
            if (hasPrimaryKey) "#0D3652" else "#3E2723",
            if (hasPrimaryKey) "#00E5FF" else "#FFC400"
        )

        btnShowHideKey.text = if (showStreamKeyPlain) "Hide Key" else "Show Key"
        btnUseShortsSlot.text =
            "Use Slot #2 Key for 9:16 Vertical Shorts: ${onOff(settings.useShortsKeySlot)}"
        styleToggleBtn(btnUseShortsSlot, isOn = settings.useShortsKeySlot)

        val dualSupported = scanSummary.supportsDualFormatCompanion(primaryCodec = settings.codec)
        val splitSupported = scanSummary.selectCompanionEncoder(primaryCodec = settings.codec, requireH264Only = true) != null
        val splitModeActive = settings.simulcastSplitEncode && splitSupported
        btnSimulcastMode.text = if (splitModeActive) {
            "Simulcast Mode: Dual-Encode Split Quality (YT 1440p HEVC + Aux 1080p H.264)"
        } else {
            "Simulcast Mode: Single-Encode Cool Mode (0% Extra Heat • Shared H.264)"
        }
        styleToggleBtn(btnSimulcastMode, isOn = splitModeActive, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        btnSimulcastTwitch.text =
            "Simulcast Target #1 (Twitch / Custom): ${onOff(settings.simulcastTwitchEnabled)}"
        styleToggleBtn(btnSimulcastTwitch, isOn = settings.simulcastTwitchEnabled)

        btnSimulcastKick.text =
            "Simulcast Target #2 (Kick / Rooter / Custom): ${onOff(settings.simulcastKickEnabled)}"
        styleToggleBtn(btnSimulcastKick, isOn = settings.simulcastKickEnabled)

        val compCodecHint = scanSummary.selectCompanionEncoder(settings.codec, requireH264Only = false)?.codecKey
        val companionEffectiveOn = !settings.isVertical && settings.dualFormatCompanionEnabled
        btnDualCompanion.text =
            "Dual-Format 16:9 + 9:16 Companion: ${onOff(companionEffectiveOn)}" +
                when {
                    settings.isVertical -> " (16:9 Primary Only)"
                    !dualSupported -> " (Needs 2-Instance HW)"
                    compCodecHint != null -> " ($compCodecHint)"
                    else -> ""
                }
        styleToggleBtn(btnDualCompanion, isOn = companionEffectiveOn)

        btnCompanionBitrate.text =
            "9:16 Companion Bitrate: ${formatBitrateMbps(settings.dualFormatBitrateKbps)} (Tap to step 2–20 Mbps)"
        btnNetTest.isEnabled = !isLive && !DashboardDialogs.isNetworkTestRunning
        btnNetTest.text = if (isLive) {
            "Speed Test Locked While Live (See Floating Pill Net Tab)"
        } else {
            "Test Internet: Download, Upload, RTT & Bitrate Advisor"
        }

        // Card 3: Direct-Tap Pills & Video Engine (with clear destination lock dimming)
        val restartSuffix = if (isLive) " [Next]" else ""
        val av1Available = scanSummary.av1Hardware != null &&
            (scanSummary.av1Hardware?.supports1440p60 == true || scanSummary.av1Hardware?.supports1080p60 == true)

        resPillButtons.forEach { (wKey, btn) ->
            styleToggleBtn(btn, isOn = (longDim == wKey))
            btn.alpha = if (wKey > destMaxLong) 0.42f else 1.0f
        }
        codecPillButtons.forEach { (cKey, btn) ->
            styleToggleBtn(btn, isOn = (cKey == effectiveActiveCodec))
            val blocked = (strictDest && cKey != "H264") || (cKey == "AV1" && !av1Available)
            btn.alpha = if (blocked) 0.42f else 1.0f
        }
        fpsPillButtons.forEach { (fVal, btn) ->
            styleToggleBtn(btn, isOn = (settings.fps == fVal))
        }
        framingPillButtons.forEach { (fKey, btn) ->
            styleToggleBtn(btn, isOn = settings.framingMode.equals(fKey, ignoreCase = true))
        }

        btnCodec.text = if (strictDest) {
            "Codec: H264 (Locked for ${activeStrictDestinationName()})$restartSuffix"
        } else {
            "Codec: ${settings.codec}" + (if (!av1Available && settings.codec != "AV1") " (AV1: No HW)" else "") + restartSuffix
        }
        btnResolution.text = if (strictDest) {
            "Res: $heroResShort (Max 1080p)$restartSuffix"
        } else {
            "Res: $heroResShort$restartSuffix"
        }
        btnFps.text = "Stream: ${settings.fps} FPS$restartSuffix" +
            (if (longDim <= 864 && settings.fps == 60) " (YT 480p plays @30)" else "")
        btnOrientationFlip.text = if (settings.isVertical) {
            "Format: 9:16 Vertical Shorts$restartSuffix"
        } else {
            "Format: 16:9 Wide (${settings.landscapeFlip}° USB-C Flip)"
        }

        btnBitrateCycle.text = if (strictDest) {
            "Target: $mbpsStr (Cap ${destMaxMbps}M)"
        } else {
            "Target: $mbpsStr"
        }
        btnRateControl.text = if (strictDest) {
            "Rate: CBR (Locked for ${activeStrictDestinationName()})$restartSuffix"
        } else {
            "Rate: ${if (settings.useVbr) "VBR (Test)" else "CBR (Rec)"}$restartSuffix"
        }
        btnAbr.text = "Ping Guard ABR 2.0: ${onOff(settings.adaptiveBitrate)}"
        styleToggleBtn(btnAbr, isOn = settings.adaptiveBitrate)

        btnUpscaler.text = "Upscaler: ${settings.upscalerMode}"
        val frameDisp = if (settings.framingMode == "STRETCH") "STRETCH (16:9 Full)" else settings.framingMode
        btnFraming.text = "Framing: $frameDisp"
        btnSmartZoom.text = "Zoom: ${settings.smartZoomPercent}% (100–250%)"

        btnFilmic.text = "Cinematic Filmic: ${onOff(settings.cinematicFilmic)}"
        styleToggleBtn(btnFilmic, isOn = settings.cinematicFilmic)

        btnAmoledVivid.text = "AMOLED Vivid Pop: ${onOff(settings.amoledVivid)}"
        styleToggleBtn(btnAmoledVivid, isOn = settings.amoledVivid)

        btnShadowBoost.text = "Adaptive Dark-Corner Boost: ${onOff(settings.shadowBoost)}"
        styleToggleBtn(btnShadowBoost, isOn = settings.shadowBoost)

        btnColorTemp.text = "Color Temp: ${settings.colorTempFilter}"
        styleToggleBtn(btnColorTemp, isOn = settings.colorTempFilter != "NEUTRAL", activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        btnSharpen.text = if (settings.gpuSharpen) {
            "GPU Crisp Sharpen: ON (Turn OFF to reduce phone heat)"
        } else {
            "GPU Crisp Sharpen: OFF (Recommended — Coolest Temp)"
        }
        styleToggleBtn(btnSharpen, isOn = settings.gpuSharpen)

        // v1.6.1 SolShorts™ Studio Controls inside Card 3 Drawer
        val anchorAbove = settings.shortsVerticalAnchor.equals("ABOVE_CHAT", ignoreCase = true)
        btnShortsAnchor.text = if (anchorAbove) {
            "9:16 Pos: ABOVE CHAT (+10% Up)"
        } else {
            "9:16 Pos: CENTER (0%)"
        }
        styleToggleBtn(btnShortsAnchor, isOn = anchorAbove, activeFillHex = "#0D3652", activeStrokeHex = "#00E5FF")

        btnShortsHeaderLayout.text = "Shorts Pro Header: ${onOff(settings.shortsHeaderLayout)}"
        styleToggleBtn(btnShortsHeaderLayout, isOn = settings.shortsHeaderLayout)

        btnShortsScoreLoupe.text = formatShortsScoreLoupeButtonLabel(settings.shortsScoreLoupe)
        styleToggleBtn(btnShortsScoreLoupe, isOn = !settings.shortsScoreLoupe.equals("OFF", ignoreCase = true))

        val mapSuppressed = SolVideoPipeline.isMinimapLoupeSuppressedByShield(
            settings.shortsMinimapLoupe,
            settings.minimapShieldEnabled,
            settings.minimapShieldCorner
        )
        btnShortsMinimapLoupe.text = formatShortsMinimapLoupeButtonLabel(settings.shortsMinimapLoupe, mapSuppressed)
        styleToggleBtn(
            btnShortsMinimapLoupe,
            isOn = !settings.shortsMinimapLoupe.equals("OFF", ignoreCase = true) && !mapSuppressed
        )

        btnShortsCaptureCooler.text = when {
            !settings.shortsCaptureCooler ->
                "Shorts Capture Cooler: OFF (Full Raw Panel Capture)$restartSuffix"
            shortDim > 1080 ->
                "Shorts Capture Cooler: ON (Auto-Bypassed >1080p)$restartSuffix"
            else ->
                "Shorts Capture Cooler (-50% Capture BW): ON$restartSuffix"
        }
        styleToggleBtn(btnShortsCaptureCooler, isOn = settings.shortsCaptureCooler && shortDim <= 1080)

        // Phase 1 (v1.5.0+) Pro Broadcast Overlays inside Card 3 Drawer
        btnHudTelemetryToggle.text = "SolHUD™ Telemetry: ${onOff(settings.hudTelemetryEnabled)}"
        styleToggleBtn(btnHudTelemetryToggle, isOn = settings.hudTelemetryEnabled)
        btnHudTelemetryCorner.text = "HUD Corner: ${SolStreamService.formatCompactCornerShortTag(settings.hudTelemetryCorner)}"

        if (!etQrOverlayText.hasFocus() && etQrOverlayText.text?.toString() != settings.qrOverlayText) {
            etQrOverlayText.setText(settings.qrOverlayText)
        }
        val qrActive = settings.qrOverlayEnabled && settings.qrOverlayText.isNotBlank()
        btnQrOverlayToggle.text = "Corner QR Overlay: ${onOff(qrActive)}"
        styleToggleBtn(btnQrOverlayToggle, isOn = qrActive)
        btnQrOverlayCorner.text = "QR Corner: ${SolStreamService.formatCompactCornerShortTag(settings.qrOverlayCorner)}"

        val hasPngIdle = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.PNGTUBER_IDLE)
        val hasPngTalk = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.PNGTUBER_TALK)
        btnPickPngTuberIdle.text = "Idle PNG (${if (hasPngIdle) "Saved" else "Striker Default"})"
        styleToggleBtn(btnPickPngTuberIdle, isOn = hasPngIdle, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")
        btnPickPngTuberTalk.text = "Talk PNG (${if (hasPngTalk) "Saved" else "Striker Default"})"
        styleToggleBtn(btnPickPngTuberTalk, isOn = hasPngTalk, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")
        btnPngTuberToggle.text = "Avatar: ${onOff(settings.pngTuberEnabled)}"
        styleToggleBtn(btnPngTuberToggle, isOn = settings.pngTuberEnabled)
        btnPngTuberCorner.text = "Pos: ${SolStreamService.formatCompactCornerShortTag(settings.pngTuberCorner)}"
        btnPngTuberSize.text = "Size: ${settings.pngTuberSizePercent}%"

        // Scenes, Unified Logo:OFF Overlay, Dual Privacy Blur & Pro Silicon Drawer
        val hasStart = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.STARTING_SOON)
        val hasBrb = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.BRB_SCENE)
        val hasOv = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.OVERLAY_PNG)
        val hasPost = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.SHORTS_POSTER)
        btnPickStartImg.text = "Starting Soon Img (${if (hasStart) "Custom Saved" else "Default Slate"})"
        styleToggleBtn(btnPickStartImg, isOn = hasStart, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        btnPickBrbImg.text = "BRB Privacy Img (${if (hasBrb) "Custom Saved" else "Default Slate"})"
        styleToggleBtn(btnPickBrbImg, isOn = hasBrb, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        btnPickOverlayPng.text = "Pick .PNG Logo (${if (hasOv) "Saved" else "None"})"
        styleToggleBtn(btnPickOverlayPng, isOn = hasOv, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        val posFriendly = if (!settings.overlayImageEnabled) {
            "Logo Overlay: OFF (Tap to Enable/Cycle)"
        } else {
            when (settings.overlayPosition) {
                "TOP_LEFT" -> "Logo: Top-Left (TL)"
                "TOP_RIGHT" -> "Logo: Top-Right (TR)"
                "BOTTOM_LEFT" -> "Logo: Bottom-Left (BL)"
                "BOTTOM_RIGHT" -> "Logo: Bottom-Right (BR)"
                else -> "Logo: Full-Screen Frame"
            }
        }
        btnOverlayPosition.text = posFriendly
        styleToggleBtn(btnOverlayPosition, isOn = settings.overlayImageEnabled)

        btnPickShortsPoster.text = "9:16 Poster (${if (hasPost) "Custom Saved" else "Pick Image"})"
        styleToggleBtn(btnPickShortsPoster, isOn = hasPost, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        if (!etBannerText.hasFocus() && etBannerText.text?.toString() != settings.liveBannerText) {
            etBannerText.setText(settings.liveBannerText)
        }
        btnBannerToggle.text = "Live Text Banner: ${onOff(settings.liveBannerEnabled)}"
        styleToggleBtn(btnBannerToggle, isOn = settings.liveBannerEnabled)

        btnBannerPos.text = "Banner Pos: ${settings.liveBannerPosition}"
        btnShortsBackdrop.text = formatShortsBackdropButtonLabel(settings.shortsBackdropMode)
        btnMinimapShield.text =
            "Privacy / Minimap Blur Shield: ${onOff(settings.minimapShieldEnabled)} (${settings.minimapShieldCorner})"
        styleToggleBtn(btnMinimapShield, isOn = settings.minimapShieldEnabled)

        btnAutoBlurKeyboard.text =
            "Auto-Blur Keyboard (Bottom Half Privacy When Typing): ${onOff(settings.autoBlurKeyboard)}"
        styleToggleBtn(btnAutoBlurKeyboard, isOn = settings.autoBlurKeyboard)

        val glDisp = if (settings.glEngineMode == "ES20") "ES 2.0" else "ES 3.0 Fast-FP16"
        btnGlEngine.text = "OpenGL Engine: ${settings.glEngineMode} ($glDisp • Max: ES ${scanSummary.maxGlEsVersion})$restartSuffix"
        val hdrSupported = scanSummary.hevcHardware?.supports10BitHdr == true
        btnHdr10.text = when {
            strictDest -> "10-Bit HDR10: OFF (SDR Only on ${activeStrictDestinationName()})"
            !hdrSupported -> "True 10-Bit HDR10 (HLG): ${onOff(settings.hdr10Enabled)} (HW 8-Bit Only)$restartSuffix"
            else -> "True 10-Bit HDR10 (HLG): ${onOff(settings.hdr10Enabled)}$restartSuffix"
        }
        styleToggleBtn(btnHdr10, isOn = settings.hdr10Enabled && !strictDest)

        btnRoiMode.text = "Hardware ROI Focus: ${settings.roiMode} (Crosshair/Minimap QP Boost)"
        styleToggleBtn(btnRoiMode, isOn = settings.roiMode != "OFF")

        val kfTag = if (strictDest) "${settings.keyframeIntervalSec}s (Locked)" else "${settings.keyframeIntervalSec}s"
        btnKeyframeAndBFrames.text = "IDR Keyframe: $kfTag | B-Frames: 0 (RTMP Safe)$restartSuffix"
        val qpLabel = when (settings.qpMaxCap) {
            0 -> "Auto (14..51)"
            32 -> "14..32 (Ultra Crisp)"
            36 -> "14..36 (High)"
            38 -> "14..38 (Balanced)"
            else -> "14..${settings.qpMaxCap}"
        }
        btnQpAndLatency.text = "QP Range: $qpLabel | Low-Lat: ${onOff(settings.lowLatencyCodecHint)}$restartSuffix"

        // Card 4: SolLuxe™ 3.0 Pure 6-Stage Audio Studio & SeekBar Sync
        val selectedMicLabel = micOptions.firstOrNull { it.deviceId == settings.micDeviceId }?.label
            ?: micOptions.firstOrNull()?.label ?: "Auto (Phone Studio Mic Locked)"
        val activeHwAcoustic = SolDualAudioEngine.detectHardwareAcousticMode(this, settings.micDeviceId, settings.speakerAecEnabled)
        btnMicDevice.text = "Mic Input: $selectedMicLabel • [${activeHwAcoustic.title}]"
        val srcName = when (settings.micAudioSource) {
            1 -> "MIC (Raw)"
            6 -> "VOICE_REC (Wideband 48kHz)"
            7 -> "VOICE_COMM (Speaker AEC)"
            else -> "AUTO (48kHz Studio Wideband)"
        }
        btnMicSource.text = "Source: $srcName"
        val aacCapSuffix = if (strictDest) " (Max 160k)" else ""
        btnAudioBitrate.text = "Master AAC: ${settings.audioBitrateKbps} kbps$aacCapSuffix$restartSuffix"

        val gProf = settings.voiceGenderProfile.uppercase(Locale.US)
        styleToggleBtn(btnGenderNeutral, isOn = (gProf == "NEUTRAL"))
        styleToggleBtn(btnGenderMale, isOn = (gProf == "MALE"))
        styleToggleBtn(btnGenderFemale, isOn = (gProf == "FEMALE"))

        btnVoicePreset.text = "Voice Preset: ${MicDspProcessor.presetDisplayName(settings.voicePreset)}"
        styleToggleBtn(btnVoicePreset, isOn = settings.voicePreset != "RAW", activeFillHex = "#2E1065", activeStrokeHex = "#8B5CF6")

        val nsModeDesc = when {
            settings.voicePreset == "RAW" -> "Bypassed (RAW Mode)"
            settings.noiseSuppressMode == "RNNOISE" -> when {
                SolNativeRnnoise.isLibraryLoaded && SolNativeRnnoise.isNeuralModelReady -> "Native C 48kHz RNNoise"
                SolNativeRnnoise.isLibraryLoaded -> "Native C 48kHz Bark-MMSE"
                else -> "RNNoise (Spectral Fallback)"
            }
            settings.noiseSuppressMode == "LIGHT" -> "Light Mathematical Cut"
            settings.noiseSuppressMode == "HW_NS" -> "Phone Chip HW NS"
            else -> "OFF"
        }
        val nsLvlDesc = when (settings.rnnoiseLevel) {
            0 -> "OFF (0%)"
            1 -> "Lv1 Gentle (65%)"
            2 -> "Lv2 Natural (88%)"
            else -> "Lv3 Max (100%)"
        }
        val denoiserActive = settings.voicePreset != "RAW" && settings.noiseSuppressMode != "OFF" && settings.rnnoiseLevel > 0
        btnNoiseSuppressMode.text = "Denoiser (Pick 1): $nsModeDesc"
        styleToggleBtn(btnNoiseSuppressMode, isOn = denoiserActive)
        btnNoiseSuppressLevel.text = "Strength: $nsLvlDesc"
        styleToggleBtn(btnNoiseSuppressLevel, isOn = denoiserActive)

        btnMicGain.text = "🎙️ Mic Boost: ${settings.micGainPercent}% (Post-DSP + 4× Limiter)"
        btnGameGain.text = "🎮 Game Mix: ${settings.gameGainPercent}% (Up to 250%)"
        suppressSeekBarCallback = true
        seekMicGain.progress = ((settings.micGainPercent.coerceIn(50, 250) - 50) / 5)
        seekGameGain.progress = (settings.gameGainPercent.coerceIn(0, 250) / 5)
        suppressSeekBarCallback = false

        btnDucking.text = if (settings.vadDuckingPercent == 0) "VAD Ducking: OFF" else "VAD Ducking: -${settings.vadDuckingPercent}%"
        styleToggleBtn(btnDucking, isOn = settings.vadDuckingPercent > 0)
        btnMicDelay.text = "Mic Sync: +${settings.micSyncDelayMs} ms"

        btnGameWide.text = "Cinema 3D Wide: ${onOff(settings.gameCinemaWideEnabled)}"
        styleToggleBtn(btnGameWide, isOn = settings.gameCinemaWideEnabled)
        btnGameSubPunch.text = "Theater Sub-Punch (62Hz Voice Duck): ${onOff(settings.gameSubPunchEnabled)}"
        styleToggleBtn(btnGameSubPunch, isOn = settings.gameSubPunchEnabled)
        btnGameFootsteps.text = "Footstep Focus (250Hz+2.45kHz+Sonar): ${onOff(settings.gameFootstepFocusEnabled)}"
        styleToggleBtn(btnGameFootsteps, isOn = settings.gameFootstepFocusEnabled)
        btnGameBlastTamer.text = "Blast Tamer (35ms Smooth): ${onOff(settings.gameBlastTamerEnabled)}"
        styleToggleBtn(btnGameBlastTamer, isOn = settings.gameBlastTamerEnabled)

        // Individual 6-Stage SolLuxe™ 3.0 Pure Switches
        btnStageHpf.text = when {
            settings.hpfCutoffHz == 0 -> "1. Butterworth HPF: OFF"
            settings.hpfAutoMode -> "1. HPF: ${settings.hpfCutoffHz} Hz (Auto)"
            else -> "1. HPF: ${settings.hpfCutoffHz} Hz (Manual)"
        }
        styleToggleBtn(btnStageHpf, isOn = settings.hpfCutoffHz > 0)

        btnStageTransient.text = "2. <0.6ms Impulse Clamper: ${onOff(settings.transientTamerEnabled)}"
        styleToggleBtn(btnStageTransient, isOn = settings.transientTamerEnabled)

        btnStageGate.text = "3. Hybrid VAD Expander (-14dB): ${onOff(settings.noiseGateEnabled)}"
        styleToggleBtn(btnStageGate, isOn = settings.noiseGateEnabled)

        btnStageEq.text = "5a. ${settings.voiceGenderProfile} Condenser EQ: ${onOff(settings.voiceEqEnabled)}"
        styleToggleBtn(btnStageEq, isOn = settings.voiceEqEnabled)

        btnStageVelvet.text = "5b. Low-Band Analog Warmth: ${onOff(settings.velvetWarmthEnabled)}"
        styleToggleBtn(btnStageVelvet, isOn = settings.velvetWarmthEnabled)

        btnStageDeEss.text = "6b. Dynamic Sibilance De-Esser: ${onOff(settings.deEsserEnabled)}"
        styleToggleBtn(btnStageDeEss, isOn = settings.deEsserEnabled)

        btnStageComp.text = "6a. Soft-Knee 2.5:1 Leveler: ${onOff(settings.compressorEnabled)}"
        styleToggleBtn(btnStageComp, isOn = settings.compressorEnabled)

        btnStageWhisper.text = "6a. VAD Quiet-Speech Lift: ${onOff(settings.whisperLiftEnabled)}"
        styleToggleBtn(btnStageWhisper, isOn = settings.whisperLiftEnabled)

        btnStageSlowAgc.text = "6a. Speech-Synced Slow AGC: ${onOff(settings.slowAgcEnabled)}"
        styleToggleBtn(btnStageSlowAgc, isOn = settings.slowAgcEnabled)

        val effectiveAecNow = SolDualAudioEngine.computeEffectiveSpeakerAec(this, settings.speakerAecEnabled, settings.micDeviceId)
        btnStageSpeakerAec.text = when {
            !settings.speakerAecEnabled -> "Speaker Echo-Guard: OFF"
            effectiveAecNow -> "Speaker Echo-Guard: ON (Active)"
            else -> "Speaker Echo-Guard: ON (Paused — Earphones)"
        }
        styleToggleBtn(btnStageSpeakerAec, isOn = effectiveAecNow)

        btnExtraSafeguards.text =
            "Scene Auto-Mute Mic: ${onOff(settings.brbMuteMicToo)} | Low-Lat Wi-Fi Lock: ${onOff(settings.wifiLowLatencyLock)}"
        styleToggleBtn(btnExtraSafeguards, isOn = settings.brbMuteMicToo || settings.wifiLowLatencyLock)

        btnRecordMicTest.isEnabled = !isLive
        btnPlayMicTest.isEnabled = !isLive
        btnRecordMicTest.text = if (isLive) "10s Test Locked (Live)" else "Record 10s Test"

        // Card 5: Go Live / Reports (with 60s Checkpoint Indicator) / Saved Clips
        val hasRecoveredCheckpoint = !isLive && DiagnosticsLogger.hasUnfinishedLiveCheckpoint(this)
        btnReportsQuick.text = if (hasRecoveredCheckpoint) {
            "Session Reports (⚡ Recovered 60s)"
        } else {
            "Session Reports & History"
        }

        val clipsCount = DiagnosticsLogger.getSavedClipsList(this).size
        btnSavedClipsQuick.text = if (clipsCount > 0) {
            "★ Saved Clips & AI Highlights ($clipsCount)"
        } else {
            "★ Saved Clips & AI Highlights"
        }

        if (isLive) {
            btnGoLive.text = "⏹ STOP LIVE STREAM"
            btnGoLive.background = roundedDrawable("#B71C1C", "#FF1744")
        } else {
            btnGoLive.text = "GO LIVE — START STREAM"
            btnGoLive.background = roundedDrawable("#00695C", "#00E5FF")
        }

        btnGlipBench.text = if (SolStreamService.isGlipBenchmarkRunning) {
            "Stop & Save $benchmarkTargetAppName Benchmark"
        } else {
            "Benchmark Other App"
        }

        refreshAccordionHeaders()
    }

    private fun formatBitrateMbps(kbps: Int): String {
        val mbps = kbps / 1000.0
        return if (mbps < 10.0 && (kbps % 1000) != 0) {
            String.format(Locale.US, "%.1f Mbps", mbps)
        } else {
            String.format(Locale.US, "%.0f Mbps", mbps)
        }
    }

    private fun updateSetting(
        newSettings: StreamSettings,
        requiresRestartIfLive: Boolean = false,
        userToggledSharp: Boolean = false
    ) {
        userModifiedSettingsBeforeScan = true
        val (enforced, _) = KeystoreVault.enforcePlatformLimits(newSettings)
        settings = enforced
        KeystoreVault.saveSettings(this, settings)
        if (SolStreamService.isBroadcastLive) {
            SolStreamService.instance?.applyDashboardSettingsLive(settings)
            if (requiresRestartIfLive) {
                Toast.makeText(
                    this,
                    "Saved for next stream! (Encoder Codec/Res/FPS/HDR/AAC/Capture Cooler apply on next Go Live.)",
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
                "Tip: Keep GPU Sharp OFF for the coolest phone temps on 3+ hour streams.",
                Toast.LENGTH_SHORT
            ).show()
        }
        refreshAllUi()
    }

    // === v1.6.1 SOLSHORTS™ STUDIO HANDLERS ===
    private fun cycleShortsVerticalAnchor() {
        val order = KeystoreVault.SHORTS_ANCHOR_ORDER
        val curIdx = order.indexOf(settings.shortsVerticalAnchor.uppercase(Locale.US)).let { if (it < 0) 0 else it }
        val next = order[(curIdx + 1) % order.size]
        updateSetting(settings.copy(shortsVerticalAnchor = next))
    }

    private fun cycleShortsMinimapLoupe() {
        val order = KeystoreVault.SHORTS_MINIMAP_LOUPE_ORDER
        val curIdx = order.indexOf(settings.shortsMinimapLoupe.uppercase(Locale.US)).let { if (it < 0) 0 else it }
        val next = order[(curIdx + 1) % order.size]
        updateSetting(settings.copy(shortsMinimapLoupe = next))
    }

    private fun cycleShortsScoreLoupe() {
        val order = KeystoreVault.SHORTS_SCORE_LOUPE_ORDER
        val curIdx = order.indexOf(settings.shortsScoreLoupe.uppercase(Locale.US)).let { if (it < 0) 0 else it }
        val next = order[(curIdx + 1) % order.size]
        updateSetting(settings.copy(shortsScoreLoupe = next))
    }

    // === PHASE 1 (v1.5.0+) STUDIO PROFILES & PRO OVERLAY HANDLERS ===
    private fun loadStudioProfileSlot(slotIndex: Int) {
        val loaded = KeystoreVault.loadStudioProfile(this, slotIndex, settings, scanSummary)
        val next = KeystoreVault.enforcePlatformLimits(loaded.settings).first
        val needsRestart = next.codec != settings.codec ||
            next.width != settings.width ||
            next.height != settings.height ||
            next.fps != settings.fps ||
            next.isVertical != settings.isVertical ||
            next.hdr10Enabled != settings.hdr10Enabled ||
            next.audioBitrateKbps != settings.audioBitrateKbps ||
            next.shortsCaptureCooler != settings.shortsCaptureCooler
        activeStudioProfileSlot = slotIndex
        updateSetting(next, requiresRestartIfLive = needsRestart)
        val resTag = "${min(next.width, next.height)}p${next.fps}"
        val orientTag = if (next.isVertical) "9:16" else "16:9"
        Toast.makeText(
            this,
            "Loaded Profile: ${loaded.name} ($resTag $orientTag • ${next.codec} • ${formatBitrateMbps(next.targetBitrateKbps)})",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showSaveStudioProfileDialog(slotIndex: Int) {
        val existing = KeystoreVault.loadStudioProfile(this, slotIndex, settings, scanSummary)
        val nameInput = styledEditText(existing.name, isPassword = false).apply {
            hint = "Profile Name (max 20 chars)"
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(4))
            addView(bodyText("Save your current video, audio & overlay settings into Slot #${slotIndex + 1}. (Stream Keys are never saved inside profiles.)"))
            addView(nameInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Studio Profile Slot #${slotIndex + 1}")
            .setView(wrap)
            .setPositiveButton("Save Current Setup") { _, _ ->
                saveVaultInputs(showToast = false)
                val chosenName = nameInput.text?.toString()?.trim()?.take(20).orEmpty().ifBlank { existing.name }
                val ok = KeystoreVault.saveStudioProfile(this, slotIndex, chosenName, settings)
                if (ok) {
                    activeStudioProfileSlot = slotIndex
                    refreshAllUi()
                    Toast.makeText(this, "Saved current setup to \"$chosenName\"!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Could not save profile slot.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Reset Default") { _, _ ->
                KeystoreVault.resetStudioProfile(this, slotIndex)
                if (activeStudioProfileSlot == slotIndex) activeStudioProfileSlot = -1
                refreshAllUi()
                Toast.makeText(this, "Reset Slot #${slotIndex + 1} to hardware default.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun nextCornerInOrder(currentCorner: String): String {
        val order = SolStreamService.CORNER_POSITION_ORDER
        val idx = order.indexOf(currentCorner.uppercase(Locale.US))
        return order[(idx + 1) % order.size]
    }

    private fun cycleHudTelemetryCorner() {
        updateSetting(settings.copy(hudTelemetryCorner = nextCornerInOrder(settings.hudTelemetryCorner)))
    }

    private fun toggleQrOverlayFromDashboard() {
        val rawText = etQrOverlayText.text?.toString()?.trim()?.take(KeystoreVault.MAX_QR_OVERLAY_CHARS) ?: ""
        val nextEnabled = !(settings.qrOverlayEnabled && settings.qrOverlayText.isNotBlank())
        if (nextEnabled) {
            if (rawText.isEmpty()) {
                Toast.makeText(
                    this,
                    "Enter a UPI, YouTube, or Discord link in the QR box first!",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            val utf8Bytes = rawText.toByteArray(StandardCharsets.UTF_8).size
            if (utf8Bytes > SolHudAndQrRenderer.MAX_QR_UTF8_BYTES) {
                Toast.makeText(
                    this,
                    "QR link is $utf8Bytes UTF-8 bytes (max ${SolHudAndQrRenderer.MAX_QR_UTF8_BYTES} bytes). Shorten the link so cameras can scan it!",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        updateSetting(settings.copy(qrOverlayText = rawText, qrOverlayEnabled = nextEnabled))
    }

    private fun cycleQrOverlayCorner() {
        val rawText = etQrOverlayText.text?.toString()?.trim()?.take(KeystoreVault.MAX_QR_OVERLAY_CHARS) ?: settings.qrOverlayText
        updateSetting(
            settings.copy(
                qrOverlayText = rawText,
                qrOverlayCorner = nextCornerInOrder(settings.qrOverlayCorner)
            )
        )
    }

    private fun cyclePngTuberCorner() {
        updateSetting(settings.copy(pngTuberCorner = nextCornerInOrder(settings.pngTuberCorner)))
    }

    private fun cyclePngTuberSize() {
        val steps = listOf(12, 15, 18, 22, 26, 30)
        val idx = steps.indexOf(settings.pngTuberSizePercent)
        val nextSize = steps[(idx + 1) % steps.size]
        updateSetting(settings.copy(pngTuberSizePercent = nextSize))
    }

    // === PLATFORM, SIMULCAST & VIDEO PILL HANDLERS (STRICT DESTINATION ENFORCEMENT) ===
    private fun selectPlatformPreset(platformKey: String) {
        val p = platformKey.uppercase(Locale.US)
        val prevPlat = settings.platformPreset.uppercase(Locale.US)
        val newUrl = when (p) {
            "YOUTUBE" -> KeystoreVault.DEFAULT_YOUTUBE_RTMPS
            "TWITCH" -> KeystoreVault.DEFAULT_TWITCH_RTMPS
            "KICK" -> KeystoreVault.DEFAULT_KICK_RTMPS
            else -> etRtmpsUrl.text?.toString() ?: KeystoreVault.DEFAULT_YOUTUBE_RTMPS
        }
        etRtmpsUrl.setText(newUrl)
        saveVaultInputs(showToast = false)

        val updated = when (p) {
            "TWITCH" -> KeystoreVault.enforcePlatformLimits(settings.copy(platformPreset = "TWITCH")).first
            "KICK" -> KeystoreVault.enforcePlatformLimits(settings.copy(platformPreset = "KICK")).first
            "YOUTUBE" -> {
                if (prevPlat in setOf("TWITCH", "KICK") && settings.targetBitrateKbps <= 8000) {
                    KeystoreVault.applyPlatformAndVoiceAwareTierPreset(
                        settings.copy(platformPreset = "YOUTUBE"),
                        DeviceTier.fromId(settings.deviceTier),
                        scanSummary
                    )
                } else {
                    val hevcOk = scanSummary.hevcHardware != null
                    settings.copy(
                        platformPreset = "YOUTUBE",
                        codec = if (hevcOk && !KeystoreVault.isSingleEncodeSimulcast(settings)) "HEVC" else settings.codec
                    )
                }
            }
            else -> settings.copy(platformPreset = "CUSTOM")
        }
        updateSetting(updated, requiresRestartIfLive = true)
    }

    private fun toggleSimulcastSplitMode() {
        val nextSplit = !settings.simulcastSplitEncode
        val splitSupported = scanSummary.selectCompanionEncoder(primaryCodec = settings.codec, requireH264Only = true) != null
        if (nextSplit && !splitSupported) {
            Toast.makeText(
                this,
                "Dual-Encode Split Quality requires 8GB+ RAM and 2+ concurrent H.264 hardware encoder instances. Staying on Single-Encode Cool Mode.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        updateSetting(settings.copy(simulcastSplitEncode = nextSplit), requiresRestartIfLive = true)
    }

    private fun toggleSimulcastTarget(isTwitch: Boolean) {
        val newTwitch = if (isTwitch) !settings.simulcastTwitchEnabled else settings.simulcastTwitchEnabled
        val newKick = if (!isTwitch) !settings.simulcastKickEnabled else settings.simulcastKickEnabled
        val updated = KeystoreVault.enforcePlatformLimits(
            settings.copy(
                simulcastTwitchEnabled = newTwitch,
                simulcastKickEnabled = newKick
            )
        ).first
        updateSetting(updated, requiresRestartIfLive = true)
    }

    private fun toggleDualFormatCompanion() {
        if (settings.isVertical) {
            Toast.makeText(
                this,
                "Dual-Format Companion requires 16:9 Landscape as your Primary format (switch Format to 16:9 first).",
                Toast.LENGTH_LONG
            ).show()
            if (settings.dualFormatCompanionEnabled) {
                updateSetting(settings.copy(dualFormatCompanionEnabled = false), requiresRestartIfLive = true)
            }
            return
        }
        val nextState = !settings.dualFormatCompanionEnabled
        if (nextState) {
            if (!scanSummary.supportsDualFormatCompanion(primaryCodec = settings.codec)) {
                Toast.makeText(
                    this,
                    "Dual-Format Simultaneous Companion requires a Flagship/Ultra chip with 2+ concurrent hardware encoder instances.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            if (!KeystoreVault.hasDistinctCompanionShortsKey(this)) {
                Toast.makeText(
                    this,
                    "Paste a separate Stream Key into Slot #2 (YouTube 9:16 Shorts Key) first — YouTube rejects two encoders using the same key!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        updateSetting(settings.copy(dualFormatCompanionEnabled = nextState), requiresRestartIfLive = true)
    }

    private fun cycleCompanionBitrate() {
        val steps = SolStreamService.COMPANION_BITRATE_STEPS_KBPS
        val cur = settings.dualFormatBitrateKbps
        val idx = steps.indexOfFirst { it == cur }
        val nextKbps = steps[(idx + 1) % steps.size]
        updateSetting(settings.copy(dualFormatBitrateKbps = nextKbps))
    }

    private fun selectCodecDirect(codecKey: String) {
        if (KeystoreVault.isStrictH264Destination(settings) && codecKey != "H264") {
            Toast.makeText(
                this,
                "${activeStrictDestinationName()} standard RTMP requires H.264 — codec is locked to H.264.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val av1Ok = scanSummary.av1Hardware != null &&
            (scanSummary.av1Hardware?.supports1440p60 == true || scanSummary.av1Hardware?.supports1080p60 == true)
        if (codecKey == "AV1" && !av1Ok) {
            Toast.makeText(
                this,
                "This processor has no Hardware AV1 Encoder (CPU-SW AV1 is blocked to protect game FPS). Using Hardware HEVC!",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        updateSetting(settings.copy(codec = codecKey), requiresRestartIfLive = true)
    }

    private fun cycleCodec() {
        if (KeystoreVault.isStrictH264Destination(settings)) {
            Toast.makeText(
                this,
                "Codec is locked to H.264 for ${activeStrictDestinationName()} compatibility.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val av1Ok = scanSummary.av1Hardware != null &&
            (scanSummary.av1Hardware?.supports1440p60 == true || scanSummary.av1Hardware?.supports1080p60 == true)
        val order = if (av1Ok) listOf("HEVC", "H264", "AV1") else listOf("HEVC", "H264")
        val next = order[(order.indexOf(settings.codec) + 1) % order.size]
        updateSetting(settings.copy(codec = next), requiresRestartIfLive = true)
    }

    private fun selectResolutionDirect(longW: Int, shortH: Int) {
        val maxLong = KeystoreVault.maxLongSideForDestination(settings)
        if (longW > maxLong) {
            Toast.makeText(
                this,
                "${activeStrictDestinationName()} standard RTMP caps resolution at 1080p.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        updateSetting(settings.copy(width = longW, height = shortH), requiresRestartIfLive = true)
    }

    private fun cycleResolution() {
        val maxLong = KeystoreVault.maxLongSideForDestination(settings)
        val tiers = listOf(
            640 to 360,
            854 to 480,
            1280 to 720,
            1920 to 1080,
            2560 to 1440,
            3840 to 2160
        ).filter { it.first <= maxLong }
        val curLong = max(settings.width, settings.height)
        val curIdx = tiers.indexOfFirst { it.first == curLong }.let { if (it < 0) tiers.lastIndex else it }
        val (nextW, nextH) = tiers[(curIdx + 1) % tiers.size]
        updateSetting(settings.copy(width = nextW, height = nextH), requiresRestartIfLive = true)
    }

    private fun cycleOrientationAndFlip() {
        if (SolStreamService.isBroadcastLive && !settings.isVertical) {
            val nextFlip = if (settings.landscapeFlip == 0) 180 else 0
            updateSetting(settings.copy(landscapeFlip = nextFlip), requiresRestartIfLive = false)
            return
        }
        when {
            !settings.isVertical && settings.landscapeFlip == 0 ->
                updateSetting(settings.copy(isVertical = false, landscapeFlip = 180))
            !settings.isVertical && settings.landscapeFlip == 180 ->
                updateSetting(
                    settings.copy(isVertical = true, landscapeFlip = 0, dualFormatCompanionEnabled = false),
                    requiresRestartIfLive = true
                )
            else ->
                updateSetting(settings.copy(isVertical = false, landscapeFlip = 0), requiresRestartIfLive = true)
        }
    }

    private fun stepBitrate(delta: Int) {
        val destMaxKbps = KeystoreVault.maxVideoBitrateKbpsForDestination(settings)
        val ladder = SolStreamService.MASTER_BITRATE_LADDER_KBPS.filter { it <= destMaxKbps }
        if (ladder.isEmpty()) return
        val cur = settings.targetBitrateKbps
        val nearestIdx = ladder.indices.minByOrNull { abs(ladder[it] - cur) } ?: 0
        if (delta > 0 && nearestIdx == ladder.lastIndex && KeystoreVault.isStrictH264Destination(settings)) {
            Toast.makeText(
                this,
                "${activeStrictDestinationName()} standard RTMP caps video bitrate at ${destMaxKbps / 1000} Mbps.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val nextIdx = (nearestIdx + delta).coerceIn(0, ladder.lastIndex)
        updateSetting(settings.copy(targetBitrateKbps = ladder[nextIdx]))
    }

    private fun toggleRateControl() {
        if (KeystoreVault.isStrictCbrDestination(settings)) {
            Toast.makeText(
                this,
                "${activeStrictDestinationName()} requires CBR rate control (VBR disabled).",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        updateSetting(settings.copy(useVbr = !settings.useVbr), requiresRestartIfLive = true)
    }

    private fun toggleHdr10() {
        if (KeystoreVault.isStrictH264Destination(settings)) {
            Toast.makeText(
                this,
                "10-Bit HDR10 requires YouTube HEVC/AV1 — unavailable on ${activeStrictDestinationName()}.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        updateSetting(settings.copy(hdr10Enabled = !settings.hdr10Enabled), requiresRestartIfLive = true)
    }

    private fun cycleUpscaler() {
        val order = listOf("BILINEAR", "BICUBIC", "LANCZOS")
        val next = order[(order.indexOf(settings.upscalerMode) + 1) % order.size]
        updateSetting(settings.copy(upscalerMode = next))
    }

    private fun cycleFraming() {
        val order = listOf("FIT", "STRETCH", "SMART", "FILL")
        val next = order[(order.indexOf(settings.framingMode) + 1) % order.size]
        updateSetting(settings.copy(framingMode = next))
    }

    private fun cycleSmartZoom() {
        val zooms = SolStreamService.SMART_ZOOM_STEPS
        val next = zooms[(zooms.indexOf(settings.smartZoomPercent) + 1) % zooms.size]
        val nextFraming = if (next > 100) "SMART" else settings.framingMode
        updateSetting(settings.copy(framingMode = nextFraming, smartZoomPercent = next))
    }

    private fun cycleColorTemp() {
        val order = listOf("NEUTRAL", "WARM", "COOL")
        val next = order[(order.indexOf(settings.colorTempFilter) + 1) % order.size]
        updateSetting(settings.copy(colorTempFilter = next))
    }

    private fun cycleUnifiedOverlayLogo() {
        val order = SolStreamService.OVERLAY_POSITION_ORDER
        if (!settings.overlayImageEnabled) {
            updateSetting(settings.copy(overlayImageEnabled = true, overlayPosition = order.first()))
            return
        }
        val idx = order.indexOf(settings.overlayPosition)
        if (idx in 0 until order.size - 1) {
            updateSetting(settings.copy(overlayImageEnabled = true, overlayPosition = order[idx + 1]))
        } else {
            updateSetting(settings.copy(overlayImageEnabled = false, overlayPosition = order.first()))
        }
    }

    private fun cycleShortsBackdrop() {
        val order = listOf("BLUR", "DARK", "POSTER")
        val next = order[(order.indexOf(settings.shortsBackdropMode) + 1) % order.size]
        updateSetting(settings.copy(shortsBackdropMode = next))
    }

    private fun cycleMinimapShield() {
        if (!settings.minimapShieldEnabled) {
            updateSetting(settings.copy(minimapShieldEnabled = true, minimapShieldCorner = "TOP_RIGHT"))
            return
        }
        val zones = SolStreamService.PRIVACY_BLUR_ZONES
        val idx = zones.indexOf(settings.minimapShieldCorner)
        if (idx in 0 until zones.size - 1) {
            updateSetting(settings.copy(minimapShieldEnabled = true, minimapShieldCorner = zones[idx + 1]))
        } else {
            updateSetting(settings.copy(minimapShieldEnabled = false, minimapShieldCorner = "TOP_RIGHT"))
        }
    }

    private fun cycleGlEngine() {
        val order = listOf("AUTO", "ES30", "ES20")
        val next = order[(order.indexOf(settings.glEngineMode) + 1) % order.size]
        updateSetting(settings.copy(glEngineMode = next), requiresRestartIfLive = true)
    }

    private fun cycleRoiMode() {
        val order = listOf("AUTO", "CENTER", "HUD", "OFF")
        val next = order[(order.indexOf(settings.roiMode) + 1) % order.size]
        updateSetting(settings.copy(roiMode = next))
    }

    private fun cycleKeyframeInterval() {
        if (KeystoreVault.isStrictH264Destination(settings)) {
            Toast.makeText(
                this,
                "${activeStrictDestinationName()} requires a 2-second IDR keyframe interval (B-Frames locked to 0).",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val nextKf = if (settings.keyframeIntervalSec == 2) 1 else 2
        updateSetting(settings.copy(keyframeIntervalSec = nextKf, maxBFrames = 0), requiresRestartIfLive = true)
    }

    private fun cycleQpAndLatency() {
        val (qp, lowLat) = when {
            settings.qpMaxCap == 0 && settings.lowLatencyCodecHint -> 32 to true
            settings.qpMaxCap == 32 -> 36 to true
            settings.qpMaxCap == 36 -> 38 to true
            settings.qpMaxCap == 38 -> 42 to true
            settings.qpMaxCap == 42 -> 0 to false
            else -> 0 to true
        }
        updateSetting(settings.copy(qpMaxCap = qp, lowLatencyCodecHint = lowLat), requiresRestartIfLive = true)
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
            6 -> 7
            else -> 0
        }
        updateSetting(settings.copy(micAudioSource = next))
    }

    private fun cycleAudioBitrate() {
        val maxAac = KeystoreVault.maxAudioBitrateKbpsForDestination(settings)
        val rates = listOf(64, 96, 128, 160, 192, 256, 320).filter { it <= maxAac }
        val curIdx = rates.indexOf(settings.audioBitrateKbps).let { if (it < 0) rates.lastIndex else it }
        val next = rates[(curIdx + 1) % rates.size]
        updateSetting(settings.copy(audioBitrateKbps = next), requiresRestartIfLive = true)
    }

    private fun cycleVoicePreset() {
        val order = MicDspProcessor.VOICE_PRESET_ORDER
        val next = order[(order.indexOf(settings.voicePreset) + 1) % order.size]
        updateSetting(MicDspProcessor.applyPresetToSettings(settings, next))
    }

    private fun cycleNoiseSuppressMode() {
        val order = MicDspProcessor.NOISE_MODE_ORDER
        val next = order[(order.indexOf(settings.noiseSuppressMode) + 1) % order.size]
        val nextLvl = if (next != "OFF" && settings.rnnoiseLevel == 0) 2 else settings.rnnoiseLevel
        updateSetting(settings.copy(noiseSuppressMode = next, rnnoiseLevel = nextLvl))
    }

    private fun cycleHpf() {
        val order = MicDspProcessor.HPF_CUTOFF_STEPS
        val curIdx = order.indexOf(settings.hpfCutoffHz)
        val next = order[(curIdx + 1) % order.size]
        val profileDefault = KeystoreVault.resolveDefaultHpfForProfile(settings.voiceGenderProfile)
        updateSetting(
            settings.copy(
                hpfCutoffHz = next,
                hpfAutoMode = (next == profileDefault)
            )
        )
    }

    private fun cycleMicGain() {
        val steps = SolStreamService.MIC_GAIN_STEPS
        val next = steps[(steps.indexOf(settings.micGainPercent) + 1) % steps.size]
        updateSetting(settings.copy(micGainPercent = next))
    }

    private fun cycleGameGain() {
        val steps = SolStreamService.GAME_GAIN_STEPS
        val next = steps[(steps.indexOf(settings.gameGainPercent) + 1) % steps.size]
        updateSetting(settings.copy(gameGainPercent = next))
    }

    private fun cycleDucking() {
        val order = SolStreamService.VAD_DUCKING_STEPS
        val next = order[(order.indexOf(settings.vadDuckingPercent) + 1) % order.size]
        updateSetting(settings.copy(vadDuckingPercent = next))
    }

    private fun cycleMicSyncDelay() {
        val delays = SolStreamService.MIC_SYNC_DELAY_STEPS
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

    private fun readClipboardTextSafely(): String? {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
        } catch (_: Exception) {
            null
        }
    }

    // === PRESET SHARE & SMART HARDWARE CLAMPING ===
    private fun copyPresetToClipboard() {
        val json = KeystoreVault.exportPresetJson(settings)
        DashboardDialogs.copyPlainTextToClipboard(this, "SolStream Ultra Preset", json)
        Toast.makeText(
            this,
            "Settings Preset copied! (Stream Keys are NEVER included — safe to share with friends!)",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun importPresetFromClipboard() {
        val raw = readClipboardTextSafely()
        if (raw.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        val imported = KeystoreVault.importPresetJson(raw, settings)
        if (imported != null) {
            val (clamped, wasAdapted) = KeystoreVault.clampImportedSettingsToHardware(imported, scanSummary)
            activeStudioProfileSlot = -1
            updateSetting(clamped, requiresRestartIfLive = true)
            val msg = if (wasAdapted) {
                "Preset imported & auto-adapted to your phone's verified hardware and destination limits!"
            } else {
                "Preset imported and applied!"
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Clipboard does not contain a valid SolStream Preset JSON.", Toast.LENGTH_LONG).show()
        }
    }

    // === VAULT & KEY VISIBILITY ACTIONS ===
    private fun saveVaultInputs(
        showToast: Boolean,
        autoEnableShortsIfNewlyAdded: Boolean = true
    ): Boolean {
        if (!::etRtmpsUrl.isInitialized ||
            !::etStreamKey.isInitialized ||
            !::etShortsKey.isInitialized ||
            !::etTwitchUrl.isInitialized ||
            !::etTwitchKey.isInitialized ||
            !::etKickUrl.isInitialized ||
            !::etKickKey.isInitialized
        ) {
            return false
        }
        val rawUrl = etRtmpsUrl.text?.toString() ?: KeystoreVault.DEFAULT_YOUTUBE_RTMPS
        val normalizedUrl = KeystoreVault.normalizeIngestUrl(rawUrl)
        val cleanKey = KeystoreVault.sanitizeStreamKeyInput(etStreamKey.text?.toString() ?: "")
        if (etRtmpsUrl.text?.toString() != normalizedUrl) {
            etRtmpsUrl.setText(normalizedUrl)
        }

        val prevShortsKey = KeystoreVault.getShortsStreamKey(this)
        val cleanShortsKey = KeystoreVault.sanitizeStreamKeyInput(etShortsKey.text?.toString() ?: "")

        val okPrimary = KeystoreVault.saveEndpointAndKey(this, normalizedUrl, cleanKey)
        val okShorts = KeystoreVault.saveShortsStreamKey(this, cleanShortsKey)
        val okTwitch = KeystoreVault.saveTwitchUrlAndKey(
            this,
            etTwitchUrl.text?.toString() ?: KeystoreVault.DEFAULT_TWITCH_RTMPS,
            etTwitchKey.text?.toString() ?: ""
        )
        val okKick = KeystoreVault.saveKickUrlAndKey(
            this,
            etKickUrl.text?.toString() ?: KeystoreVault.DEFAULT_KICK_RTMPS,
            etKickKey.text?.toString() ?: ""
        )
        val allSavedOk = okPrimary && okShorts && okTwitch && okKick

        var settingsChanged = false
        var autoEnabledShortsSlot = false
        var updatedSettings = settings

        if (autoEnableShortsIfNewlyAdded &&
            okShorts &&
            prevShortsKey.isEmpty() &&
            cleanShortsKey.isNotEmpty() &&
            !updatedSettings.useShortsKeySlot
        ) {
            updatedSettings = updatedSettings.copy(useShortsKeySlot = true)
            settingsChanged = true
            autoEnabledShortsSlot = true
        }

        if (::etBannerText.isInitialized) {
            val bannerTxt = etBannerText.text?.toString()?.trim()?.take(80) ?: ""
            if (bannerTxt != updatedSettings.liveBannerText) {
                updatedSettings = updatedSettings.copy(liveBannerText = bannerTxt)
                settingsChanged = true
                SolSceneAssetStore.bumpRevision()
            }
        }
        if (::etQrOverlayText.isInitialized) {
            val qrTxt = etQrOverlayText.text?.toString()?.trim()?.take(KeystoreVault.MAX_QR_OVERLAY_CHARS) ?: ""
            if (qrTxt != updatedSettings.qrOverlayText) {
                val keepQrOn = updatedSettings.qrOverlayEnabled &&
                    qrTxt.isNotEmpty() &&
                    qrTxt.toByteArray(StandardCharsets.UTF_8).size <= SolHudAndQrRenderer.MAX_QR_UTF8_BYTES
                updatedSettings = updatedSettings.copy(
                    qrOverlayText = qrTxt,
                    qrOverlayEnabled = keepQrOn
                )
                settingsChanged = true
            }
        }
        if (settingsChanged) {
            settings = KeystoreVault.enforcePlatformLimits(updatedSettings).first
            KeystoreVault.saveSettings(this, settings)
            if (SolStreamService.isBroadcastLive) {
                SolStreamService.instance?.applyDashboardSettingsLive(settings)
            }
        }

        refreshAllUi()

        if (!allSavedOk) {
            if (showToast) {
                Toast.makeText(
                    this,
                    "Warning: Android Keystore encryption failed—new Stream Key could not be saved!",
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }

        if (showToast) {
            val msg = when {
                autoEnabledShortsSlot ->
                    "Saved! Slot #2 Shorts Key auto-enabled for 9:16 Vertical streams."
                cleanKey.isEmpty() ->
                    "Ingest URL & auxiliary slots saved (Primary Key box is empty)."
                else ->
                    "All Stream Keys encrypted in Android Hardware Keystore!"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun clearStreamKeyInput() {
        etStreamKey.setText("")
        KeystoreVault.clearStreamKey(this)
        refreshAllUi()
        Toast.makeText(this, "Primary Stream Key cleared.", Toast.LENGTH_SHORT).show()
    }

    private fun pasteStreamKeyFromClipboard() {
        val rawClip = readClipboardTextSafely()
        if (rawClip.isNullOrEmpty()) {
            Toast.makeText(this, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        if (KeystoreVault.isLikelyRtmpUrl(rawClip)) {
            val normUrl = KeystoreVault.normalizeIngestUrl(rawClip)
            etRtmpsUrl.setText(normUrl)
            etRtmpsUrl.setSelection(etRtmpsUrl.text?.length ?: 0)
            saveVaultInputs(showToast = false)
            Toast.makeText(
                this,
                "Detected a Server URL — pasted into the Ingest URL box! Now copy your Stream Key.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val cleanKey = KeystoreVault.sanitizeStreamKeyInput(rawClip)
        etStreamKey.setText(cleanKey)
        etStreamKey.setSelection(etStreamKey.text?.length ?: 0)
        saveVaultInputs(showToast = true)
    }

    private fun toggleKeyVisibility() {
        showStreamKeyPlain = !showStreamKeyPlain
        val method = if (showStreamKeyPlain) null else PasswordTransformationMethod.getInstance()
        val inType = if (showStreamKeyPlain) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        listOf(etStreamKey, etShortsKey, etTwitchKey, etKickKey).forEach { et ->
            et.inputType = inType
            et.transformationMethod = method
            et.setSelection(et.text?.length ?: 0)
        }
        btnShowHideKey.text = if (showStreamKeyPlain) "Hide Key" else "Show Key"
    }

    private fun openYouTubeStudioInBrowser() {
        Toast.makeText(
            this,
            "Opening YouTube Studio — tap Go Live icon -> copy your Default Variable Stream Key (keep Manual resolution OFF)!",
            Toast.LENGTH_LONG
        ).show()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com")))
        } catch (_: Exception) {
        }
    }

    private fun openYouTubeAppQuick() {
        try {
            val pkgIntent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (pkgIntent != null) {
                startActivity(pkgIntent)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")))
            }
        } catch (_: Exception) {
        }
    }

    private fun openDeveloperYouTubeChannel() {
        Toast.makeText(
            this,
            "Opening @Sol_CODM on YouTube — thank you for subscribing & supporting!",
            Toast.LENGTH_LONG
        ).show()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@Sol_CODM?sub_confirmation=1")))
        } catch (_: Exception) {
        }
    }

    // === GOOGLE PLAY PROMINENT DISCLOSURE & DIRECT PERMISSION TELEPORTERS ===
    private fun showPlayStoreCompliantAccessibilityDisclosure(allowSkipToStream: Boolean) {
        if (isAccessibilityServiceEnabled()) {
            openAccessibilitySettingsDirect()
            return
        }
        DashboardDialogs.showAccessibilityDisclosureDialog(
            activity = this,
            allowSkipToStream = allowSkipToStream,
            onAcceptOpenSettings = { openAccessibilitySettingsDirect() },
            onOpenAppInfoRestricted = { openAppInfoForRestrictedSettings() },
            onSkipToStream = { requestScreenCaptureAndGoLive() }
        )
    }

    private fun openAccessibilitySettingsDirect() {
        try {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(
                    this,
                    "Installed apps -> SolStream Studio -> if greyed out, tap it once & press OK so Unlock Restricted appears in Step 4!",
                    Toast.LENGTH_LONG
                ).show()
            }
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun openAppInfoSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun openAppInfoForRestrictedSettings() {
        Toast.makeText(
            this,
            "Tap ⋮ at top-right (or scroll to bottom of App Info on Xiaomi/POCO/Vivo) -> tap 'Allow restricted settings'!",
            Toast.LENGTH_LONG
        ).show()
        openAppInfoSettings()
    }

    private fun onGoLiveClicked() {
        if (SolStreamService.isBroadcastLive) {
            val now = SystemClock.uptimeMillis()
            if (now - lastDashboardStopTapMs < 2500L) {
                lastDashboardStopTapMs = 0L
                uiHandler.removeCallbacks(dashboardStopResetRunnable)
                val liveSvc = SolStreamService.instance
                if (liveSvc != null) {
                    liveSvc.stopLiveBroadcast("Stopped from dashboard")
                } else {
                    startService(Intent(this, SolStreamService::class.java).apply {
                        action = SolStreamService.ACTION_STOP_STREAM
                    })
                }
            } else {
                lastDashboardStopTapMs = now
                btnGoLive.text = "⚠️ TAP AGAIN WITHIN 2.5s TO STOP LIVE STREAM"
                Toast.makeText(this, "Tap STOP again to end your live broadcast.", Toast.LENGTH_SHORT).show()
                uiHandler.removeCallbacks(dashboardStopResetRunnable)
                uiHandler.postDelayed(dashboardStopResetRunnable, 2500L)
            }
            return
        }

        if (!saveVaultInputs(showToast = false)) {
            return
        }
        val (enforced, wasClamped) = KeystoreVault.enforcePlatformLimits(settings)
        if (wasClamped) {
            settings = enforced
            KeystoreVault.saveSettings(this, enforced)
            refreshAllUi()
        }

        if (KeystoreVault.buildActivePrimaryEndpoint(this, settings).isNullOrBlank()) {
            Toast.makeText(this, "Please paste or type your Stream Key first!", Toast.LENGTH_LONG).show()
            return
        }
        if (!settings.isVertical &&
            settings.dualFormatCompanionEnabled &&
            !KeystoreVault.hasDistinctCompanionShortsKey(this)
        ) {
            Toast.makeText(
                this,
                "Dual-Format 9:16 Companion is ON, but Slot #2 Shorts Key is empty or identical to Primary Key! Paste a 2nd Stream Key in Slot #2 or turn Dual-Format OFF.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRuntimePermissions()
            Toast.makeText(this, "Grant Microphone permission first, then tap GO LIVE.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val mediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (mediaVol == 0 || settings.gameGainPercent == 0) {
                Toast.makeText(
                    this,
                    "Heads up: Phone Media Volume or Game Mix is at 0% (game audio will be silent on stream!).",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (_: Exception) {
        }

        if (!isAccessibilityServiceEnabled()) {
            showPlayStoreCompliantAccessibilityDisclosure(allowSkipToStream = true)
            return
        }

        requestScreenCaptureAndGoLive()
    }

    private fun launchScreenCapturePromptSafely() {
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        } catch (e: Exception) {
            CodecCapabilityProbe.isBroadcastStartingOrLive = false
            Toast.makeText(
                this,
                "Could not open Android screen capture prompt (${e.javaClass.simpleName}).",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestScreenCaptureAndGoLive() {
        CodecCapabilityProbe.isBroadcastStartingOrLive = true
        if (CodecCapabilityProbe.isHardwareProbeCurrentlyRunning()) {
            if (!goLiveWaitBusy.compareAndSet(false, true)) return
            Toast.makeText(
                this,
                "Finishing startup hardware encoder check—launching stream...",
                Toast.LENGTH_SHORT
            ).show()
            Thread({
                try {
                    var waitedMs = 0
                    while (CodecCapabilityProbe.isHardwareProbeCurrentlyRunning() && waitedMs < 5000) {
                        try { Thread.sleep(50) } catch (_: InterruptedException) { break }
                        waitedMs += 50
                    }
                } finally {
                    runOnUiThread {
                        goLiveWaitBusy.set(false)
                        if (!isFinishing && !isDestroyed && !SolStreamService.isBroadcastLive) {
                            launchScreenCapturePromptSafely()
                        } else {
                            CodecCapabilityProbe.isBroadcastStartingOrLive = false
                        }
                    }
                }
            }, "SolWaitProbeBeforeLive").start()
            return
        }
        if (goLiveWaitBusy.get()) return
        launchScreenCapturePromptSafely()
    }

    private fun runTenSecMicCalibration() {
        if (SolStreamService.isBroadcastLive || CodecCapabilityProbe.isBroadcastStartingOrLive) {
            Toast.makeText(this, "Mic is already live on stream!", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRuntimePermissions()
            return
        }
        if (!micTestBusy.compareAndSet(false, true)) {
            Toast.makeText(this, "10s calibration is already running...", Toast.LENGTH_SHORT).show()
            return
        }
        tvMicTestStatus.text = "Starting 10-second Studio Voice Calibration..."
        SolDualAudioEngine.recordGuidedTenSecondMicTest(
            context = this,
            settings = settings,
            onProgress = { msg ->
                runOnUiThread { if (!isFinishing && !isDestroyed) tvMicTestStatus.text = msg }
            },
            onComplete = { _, summary, _ ->
                micTestBusy.set(false)
                runOnUiThread { if (!isFinishing && !isDestroyed) tvMicTestStatus.text = summary }
            }
        )
    }

    private fun playMicTestSample() {
        if (SolStreamService.isBroadcastLive) {
            Toast.makeText(this, "Stop live stream before playing test sample.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!micTestBusy.compareAndSet(false, true)) {
            Toast.makeText(this, "Audio test busy...", Toast.LENGTH_SHORT).show()
            return
        }
        tvMicTestStatus.text = "Playing recorded 10s studio sample..."
        SolDualAudioEngine.playRecordedMicTest(this) { msg ->
            micTestBusy.set(false)
            runOnUiThread { if (!isFinishing && !isDestroyed) tvMicTestStatus.text = msg }
        }
    }

    private fun toggleCompetitorBenchmark() {
        if (SolStreamService.isBroadcastLive) {
            Toast.makeText(this, "Stop SolStream live broadcast before benchmarking another app.", Toast.LENGTH_SHORT).show()
            return
        }
        if (SolStreamService.isGlipBenchmarkRunning) {
            startService(Intent(this, SolStreamService::class.java).apply {
                action = SolStreamService.ACTION_STOP_GLIP_BENCH
            })
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRuntimePermissions()
            Toast.makeText(this, "Grant Microphone permission first to start the Benchmark profiler.", Toast.LENGTH_LONG).show()
            return
        }

        val apps = arrayOf("Glip", "PRISM Live Studio", "Streamlabs", "Zero Lag", "Turnip / Other App")
        AlertDialog.Builder(this)
            .setTitle("Benchmark Another Streaming App")
            .setItems(apps) { _, which ->
                benchmarkTargetAppName = apps[which]
                DiagnosticsLogger.log("BENCH_TARGET", "Selected $benchmarkTargetAppName for passive thermal/RAM comparison.")
                try {
                    ContextCompat.startForegroundService(
                        this,
                        Intent(this, SolStreamService::class.java).apply {
                            action = SolStreamService.ACTION_START_GLIP_BENCH
                        }
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Could not start Benchmark service (${e.javaClass.simpleName}).",
                        Toast.LENGTH_LONG
                    ).show()
                }
                refreshAllUi()
            }
            .setNegativeButton("Cancel", null)
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
                Toast.makeText(
                    this,
                    "Tap Battery → select 'Unrestricted' so Android never kills long streams!",
                    Toast.LENGTH_LONG
                ).show()
                val openedAppInfo = runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                }.isSuccess
                if (!openedAppInfo) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
                return
            }

            val brand = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase(Locale.US)
            val oemIntents = mutableListOf<Intent>()
            when {
                brand.contains("vivo") || brand.contains("iqoo") -> {
                    oemIntents.add(Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")))
                    oemIntents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
                }
                brand.contains("xiaomi") || brand.contains("poco") || brand.contains("redmi") -> {
                    oemIntents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                }
                brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") -> {
                    oemIntents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                }
            }
            for (intent in oemIntents) {
                if (runCatching { startActivity(intent) }.isSuccess) {
                    Toast.makeText(this, "Battery is Unrestricted ✓ Opened OEM Background Autostart guard.", Toast.LENGTH_LONG).show()
                    return
                }
            }
            if (brand.contains("samsung")) {
                Toast.makeText(
                    this,
                    "Battery is Unrestricted ✓ Also add SolStream & your game in Device Care → Memory → Excluded apps!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "Battery is already Unrestricted ✓", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                openAppInfoSettings()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (SolStreamService.isAccessibilityConnected(this)) return true
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains("$packageName/${SolStreamService::class.java.name}") ||
            enabled.contains("$packageName/.SolStreamService")
    }

    // === OBSIDIAN-GLASS UI CARD & BUTTON HELPERS ===
    private fun sectionCard(
        heading: String,
        engineBadge: String = "",
        badgeHex: String = "#00E5FF"
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0D1526"), Color.parseColor("#0A111F"))
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#1B2B44"))
            }
            setPadding(dp(16), dp(14), dp(16), dp(16))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
            layoutParams = lp

            val hdrRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(8))
            }
            val titleTv = TextView(this@MainActivity).apply {
                text = heading
                setTextColor(Color.parseColor("#F1F5F9"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            hdrRow.addView(titleTv)

            if (engineBadge.isNotBlank()) {
                val badgeTv = TextView(this@MainActivity).apply {
                    text = engineBadge
                    setTextColor(Color.parseColor(badgeHex))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.0f)
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = 0.06f
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                    background = roundedDrawable("#081629", badgeHex)
                }
                hdrRow.addView(badgeTv)
            }
            addView(hdrRow)
        }
    }

    private fun labelText(txt: String): TextView = TextView(this).apply {
        text = txt
        setTextColor(Color.parseColor("#94A3B8"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.2f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(8), 0, dp(3))
    }

    private fun bodyText(txt: String): TextView = TextView(this).apply {
        text = txt
        setTextColor(Color.parseColor("#CBD5E1"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setLineSpacing(dp(1).toFloat(), 1.0f)
        setPadding(0, dp(3), 0, dp(6))
    }

    private fun styledEditText(initial: String, isPassword: Boolean): EditText = EditText(this).apply {
        setText(initial)
        setTextColor(Color.parseColor("#F8FAFC"))
        setHintTextColor(Color.parseColor("#64748B"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        isSingleLine = true
        background = cardDrawable("#060B16", "#23395B")
        setPadding(dp(12), dp(10), dp(12), dp(10))
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
        setTextColor(Color.parseColor("#F8FAFC"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        val strokeHex = when (bgHex.uppercase(Locale.US)) {
            "#0D3652", "#00695C", "#00897B" -> "#00E5FF"
            "#153969", "#13284A" -> "#38BDF8"
            "#4C1D95", "#2E1065" -> "#8B5CF6"
            "#B71C1C", "#D50000", "#881337" -> "#FF1744"
            else -> "#23344E"
        }
        background = roundedDrawable(bgHex, strokeHex)
        setPadding(dp(10), dp(7), dp(10), dp(7))
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
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), Color.parseColor(strokeHex))
    }

    private fun roundedDrawable(fillHex: String, strokeHex: String): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(fillHex))
        cornerRadius = dp(9).toFloat()
        setStroke(dp(1), Color.parseColor(strokeHex))
    }

    private fun okWait(ok: Boolean): String = if (ok) "READY" else "NEEDED"
    private fun onOff(v: Boolean): String = if (v) "ON" else "OFF"
    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        internal fun formatShortsBackdropButtonLabel(mode: String): String {
            return when (mode.trim().uppercase(Locale.US)) {
                "DARK" -> "9:16 Shorts Canvas: DARK (0% Extra GPU — Coolest)"
                "POSTER" -> "9:16 Shorts Canvas: POSTER (Static Image — Low GPU)"
                else -> "9:16 Shorts Canvas: BLUR (40% Cooler Scissor Blur)"
            }
        }

        internal fun formatShortsScoreLoupeButtonLabel(mode: String): String {
            return when (mode.trim().uppercase(Locale.US)) {
                "TOP_LEFT_FEED" -> "Score+Feed Loupe: Top-Left + Killfeed"
                "TOP_LEFT" -> "Score+Feed Loupe: Top-Left Score/Squad"
                "TOP_CENTER" -> "Score+Feed Loupe: Top-Center Timer"
                else -> "Score+Feed Loupe: OFF"
            }
        }

        internal fun formatShortsMinimapLoupeButtonLabel(
            mode: String,
            suppressedByShield: Boolean = false
        ): String {
            val base = when (mode.trim().uppercase(Locale.US)) {
                "TOP_RIGHT" -> "Minimap Loupe: Top-Right Radar"
                "TOP_LEFT" -> "Minimap Loupe: Top-Left Radar"
                else -> return "Minimap Loupe: OFF"
            }
            return if (suppressedByShield) "$base (Paused by Blur)" else base
        }
    }
}
