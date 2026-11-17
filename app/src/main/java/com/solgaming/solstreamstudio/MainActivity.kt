/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
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
import android.os.PersistableBundle
import android.os.PowerManager
import android.os.SystemClock
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
    private var suppressSeekBarCallback = false
    private var pendingAssetPickSlot: SolSceneAssetStore.AssetSlot = SolSceneAssetStore.AssetSlot.BRB_SCENE
    private var benchmarkTargetAppName: String = "Glip / Other App"

    private val uiHandler = Handler(Looper.getMainLooper())
    private val micTestBusy = AtomicBoolean(false)
    private val netTestBusy = AtomicBoolean(false)
    private val hwScanBusy = AtomicBoolean(false)

    // Responsive Layout Containers (for Android 16/17 Foldables, Tablets & Rotation)
    private lateinit var fixedHeaderBox: LinearLayout
    private lateinit var mainContentContainer: LinearLayout
    private var lastInsetLeft = 0
    private var lastInsetTop = 0
    private var lastInsetRight = 0
    private var lastInsetBottom = 0

    // Fixed Header, Live Idle Telemetry Banner & 3-Tile Hero Summary Bar
    private lateinit var tvHeaderSubtitle: TextView
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

    // Card 2: SolRelay Omega™ Vault, 4-Pill Platform Bar, Simulcast & 5-Metric Speed Test
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

    // Card 3: SolCore Ultra™ Video Engine, Segmented Pills, Visuals & Scenes
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
    private lateinit var btnPickStartImg: Button
    private lateinit var btnPickBrbImg: Button
    private lateinit var btnPickOverlayPng: Button
    private lateinit var btnOverlayPosition: Button
    private lateinit var btnPickShortsPoster: Button
    private lateinit var btnToggleOverlayOn: Button
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

    // Card 4: 13-Stage SolLuxe™ 2.0 Audio, SeekBar Sliders, 250% Game Soundstage & 10s Calibrator
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
    private lateinit var btnSavedClipsQuick: Button
    private lateinit var btnGlipBench: Button

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
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (_: Exception) {
                }
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
            ContextCompat.startForegroundService(this, svcIntent)
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
        Toast.makeText(this, "Saving ${targetSlot.name} graphic...", Toast.LENGTH_SHORT).show()
        Thread({
            val res = SolSceneAssetStore.importAssetFromUri(applicationContext, uri, targetSlot)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    res.onSuccess { info ->
                        if (targetSlot == SolSceneAssetStore.AssetSlot.OVERLAY_PNG && !settings.overlayImageEnabled) {
                            updateSetting(settings.copy(overlayImageEnabled = true))
                        } else if (targetSlot == SolSceneAssetStore.AssetSlot.SHORTS_POSTER) {
                            updateSetting(settings.copy(shortsBackdropMode = "POSTER"))
                        } else {
                            refreshAllUi()
                        }
                        Toast.makeText(this, "${targetSlot.name}: $info!", Toast.LENGTH_SHORT).show()
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

        // Instant 0-ms cached/placeholder summary so MainActivity.onCreate() NEVER blocks UI thread!
        scanSummary = CodecCapabilityProbe.peekCachedOrPlaceholder(this)
        settings = KeystoreVault.loadSettings(this)
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

        // Complete full multi-encoder hardware verification asynchronously in background.
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
                    "SolApex™ HyperStream • ${scanSummary.friendlyHardwareLine()}\n${scanSummary.toBadgeText()}"
                refreshAllUi()
            }
        }
    }

    /**
     * Android 16/17 (API 36/37) Large-Screen, Foldable & Rotation Handler:
     * Updates responsive horizontal centering padding whenever the phone rotates or a foldable unfolds
     * without destroying or resetting the active dashboard state!
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyResponsiveContainerPadding()
    }

    override fun onResume() {
        super.onResume()
        settings = KeystoreVault.loadSettings(this)
        micOptions = CodecCapabilityProbe.listAvailableMicInputs(this)
        refreshAllUi()
        uiHandler.removeCallbacks(idleTelemetryRefreshRunnable)
        uiHandler.post(idleTelemetryRefreshRunnable)
    }

    /**
     * Auto-saves any typed Ingest URL, Stream Keys, or Live Banner Text when the user
     * switches to Chrome/YouTube Studio or back to CODM so unsaved edits are never wiped in onResume()!
     */
    override fun onPause() {
        uiHandler.removeCallbacks(idleTelemetryRefreshRunnable)
        try {
            saveVaultInputs(showToast = false)
        } catch (_: Exception) {
        }
        super.onPause()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(idleTelemetryRefreshRunnable)
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
            info.versionName?.removeSuffix("-debug") ?: "1.3.0"
        } catch (_: Exception) {
            "1.3.0"
        }
    }

    private fun applyResponsiveContainerPadding() {
        if (!::fixedHeaderBox.isInitialized || !::mainContentContainer.isInitialized) return
        val screenW = resources.displayMetrics.widthPixels
        val maxContentW = dp(640)
        val extraSidePad = if (screenW > maxContentW) ((screenW - maxContentW) / 2) else 0
        val leftPad = max(dp(14), lastInsetLeft + dp(10)) + extraSidePad
        val rightPad = max(dp(14), lastInsetRight + dp(10)) + extraSidePad

        fixedHeaderBox.setPadding(
            leftPad,
            max(dp(10), lastInsetTop + dp(6)),
            rightPad,
            dp(9)
        )
        mainContentContainer.setPadding(
            leftPad,
            dp(8),
            rightPad,
            max(dp(24), lastInsetBottom + dp(16))
        )
    }

    private fun buildDashboardUi(): View {
        // Outer vertical root pins the Top Header Bar above the ScrollView so scrolling
        // cards never collide with the phone's top status bar clock or battery icon!
        val outerRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050914"))
        }

        fixedHeaderBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#060C1A"), Color.parseColor("#0B162C"))
            )
            setPadding(dp(14), dp(12), dp(14), dp(9))
        }

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#050914"))
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(28))
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

        // === FIXED SOLAPEX™ BRANDED STUDIO HEADER WITH SIGNAL CREST ===
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Mini SolStream 'S' Energy Signal Core Crest Badge
        val crestBadge = TextView(this).apply {
            text = "S•"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#0D254C"), Color.parseColor("#2A125E"))
            ).apply {
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                marginEnd = dp(8)
            }
        }

        val title = TextView(this).apply {
            text = "SOLSTREAM STUDIO • ULTRA"
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val verBadge = TextView(this).apply {
            text = "v$appVersionName • SolApex™"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedDrawable("#0F2744", "#00E5FF")
        }
        titleRow.addView(crestBadge)
        titleRow.addView(title)
        titleRow.addView(verBadge)

        tvHeaderSubtitle = TextView(this).apply {
            text = "SolApex™ HyperStream • ${scanSummary.friendlyHardwareLine()}\n${scanSummary.toBadgeText()}"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding(0, dp(4), 0, dp(4))
        }

        val topHelperRow = buttonRow(
            actionButton("📖 Guide, Privacy & Licenses", "#0D3652") { showPlainEnglishGlossaryDialog() },
            actionButton("Copy Preset", "#152338") { copyPresetToClipboard() },
            actionButton("Import Preset", "#152338") { importPresetFromClipboard() }
        )

        fixedHeaderBox.addView(titleRow)
        fixedHeaderBox.addView(tvHeaderSubtitle)
        fixedHeaderBox.addView(topHelperRow)

        val headerDivider = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#2979FF"),
                    Color.parseColor("#7C3AED")
                )
            )
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2))
        }

        // === LIVE IDLE / ACTIVE TELEMETRY BANNER ===
        tvLiveBanner = TextView(this).apply {
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(9), dp(12), dp(9))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            layoutParams = lp
            background = cardDrawable("#0B1D2C", "#00E5FF")
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
            ).apply { topMargin = dp(9) }
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

        heroRow.addView(tileResBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        heroRow.addView(tileFpsBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
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
                val lp = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (idx > 0) marginStart = dp(4)
                }
                layoutParams = lp
                setOnClickListener {
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

        // === CARD 1: PRE-FLIGHT READINESS, AUTO-COLLAPSE, 5TH AUDIO/WIFI LINE & ALL-BRAND GUIDE ===
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
            setPadding(0, dp(2), 0, dp(4))
        }
        cardPreflight.addView(tvPreflightStatus)
        cardPreflight.addView(tvPreflightAudioWifiLine)

        btnPreflightCollapsedToggle = actionButton(
            "✅ All 4 Pre-Flight Checks READY (Tap to Show Setup & All-Brand Guide)",
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
            text = "Android 13–18 Tip (For Sideloaded APKs): Tap '3. Tap Accessibility 1st' -> Accept disclosure -> tap greyed-out SolStream Studio once & hit OK -> then tap '4. Unlock Restricted (⋮)' -> tap ⋮ at top-right (or scroll to bottom of App Info on Xiaomi/POCO/Vivo) -> tap 'Allow restricted settings'! (Play Store installs unlock automatically.)"
            setTextColor(Color.parseColor("#FFD54F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, dp(6))
        }
        boxPreflightButtonsContainer.addView(tvRestrictedHint)

        btnGrantMicNotif = actionButton("1. Grant Mic & Notif", "#101929") { requestRuntimePermissions() }
        btnUnrestrictedBatt = actionButton("2. Unrestricted Batt", "#101929") { openBatteryUnrestricted() }
        btnTapAccessibilityFirst = actionButton("3. Tap Accessibility 1st", "#101929") {
            showPlayStoreCompliantAccessibilityDisclosure(allowSkipToStream = false)
        }
        btnUnlockRestricted = actionButton("4. Unlock Restricted (⋮)", "#101929") { openAppInfoForRestrictedSettings() }

        boxPreflightButtonsContainer.addView(buttonRow(btnGrantMicNotif, btnUnrestrictedBatt))
        boxPreflightButtonsContainer.addView(buttonRow(btnTapAccessibilityFirst, btnUnlockRestricted))
        boxPreflightButtonsContainer.addView(
            actionButton("All-Brand Phone Setup, Earphones & Cool-Stream Guide", "#152844") {
                showAllBrandSetupGuide()
            }
        )
        cardPreflight.addView(boxPreflightButtonsContainer)
        container.addView(cardPreflight)

        // === CARD 2: SOLRELAY OMEGA VAULT, 4-PILL PLATFORM BAR, SIMULCAST & 5-METRIC SPEED TEST ===
        val cardStream = sectionCard(
            heading = "2. Stream Key Vault & Simulcast",
            engineBadge = "SOLRELAY OMEGA™",
            badgeHex = "#00E5FF"
        )

        cardStream.addView(labelText("Select Broadcast Platform (Auto-configures Codec, Max Mbps & AAC):"))
        val platRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3); bottomMargin = dp(4) }
        }
        val platforms = listOf("YOUTUBE" to "YouTube", "TWITCH" to "Twitch", "KICK" to "Kick", "CUSTOM" to "Custom")
        platforms.forEachIndexed { idx, (platKey, platTitle) ->
            val b = Button(this).apply {
                text = platTitle
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(2), dp(6), dp(2), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (idx > 0) marginStart = dp(4)
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
            setPadding(0, dp(6), 0, dp(2))
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
            setPadding(dp(7), dp(2), dp(7), dp(2))
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

        // Collapsible Drawer: 9:16 Shorts Key Slot + Custom Simulcast + Dual-Format Companion
        boxSimulcastDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = cardDrawable("#08101E", "#1E3A5F")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            layoutParams = lp
        }
        btnToggleSimulcastDrawer = actionButton(
            "▼ Expand: 9:16 Shorts Key Slot, Simulcast (Twitch/Kick/Custom) & Dual-Format",
            "#122036"
        ) {
            val show = boxSimulcastDrawer.visibility != View.VISIBLE
            boxSimulcastDrawer.visibility = if (show) View.VISIBLE else View.GONE
            btnToggleSimulcastDrawer.text = if (show) {
                "▲ Hide: 9:16 Shorts Key Slot, Simulcast & Dual-Format"
            } else {
                "▼ Expand: 9:16 Shorts Key Slot, Simulcast (Twitch/Kick/Custom) & Dual-Format"
            }
        }

        boxSimulcastDrawer.addView(
            labelText("Slot #2 — YouTube 9:16 Shorts Key (Keep 'Turn on manual settings' OFF in YT Studio):")
        )
        etShortsKey = styledEditText(KeystoreVault.getShortsStreamKey(this), isPassword = true).apply {
            hint = "Optional Slot #2 Variable Key for 9:16 Shorts"
        }
        btnUseShortsSlot = actionButton("", "#101929") {
            saveVaultInputs(showToast = false)
            updateSetting(settings.copy(useShortsKeySlot = !settings.useShortsKeySlot), requiresRestartIfLive = true)
        }
        boxSimulcastDrawer.addView(etShortsKey)
        boxSimulcastDrawer.addView(btnUseShortsSlot)

        boxSimulcastDrawer.addView(
            labelText("Multi-Platform Simulcast (Twitch, Kick, Rooter, Loco, TikTok Live or Custom RTMP):")
        )
        btnSimulcastMode = actionButton("", "#13284A") {
            updateSetting(settings.copy(simulcastSplitEncode = !settings.simulcastSplitEncode), requiresRestartIfLive = true)
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
            "Tip: Keep 'Turn on manual settings' OFF in YouTube Studio so one Default Stream Key works automatically for both 16:9 Landscape & 9:16 Vertical Shorts!"
        )
        btnNetTest = actionButton("Test Internet: Download, Upload, TCP RTT & Bitrate Advisor", "#153969") {
            runRtmpsNetworkCheck()
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

        // === CARD 3: SOLCORE ULTRA VIDEO ENGINE, DIRECT-TAP PILLS, VISUALS & SCENES ===
        val cardVideo = sectionCard(
            heading = "3. Video Engine (360p–4K60 • 0.5–100M)",
            engineBadge = "SOLCORE ULTRA™",
            badgeHex = "#2979FF"
        )
        cardVideo.addView(
            bodyText("Plays at 120–185 Hz on your screen while hardware silicon encodes 60/30 FPS. Tap any Resolution, Codec, FPS, or Framing pill directly below!")
        )

        // Direct-Tap 6-Pill Resolution Row
        cardVideo.addView(labelText("Direct-Tap Stream Resolution:"))
        val resRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
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
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    if (idx > 0) marginStart = dp(3)
                }
                setOnClickListener {
                    updateSetting(settings.copy(width = longW, height = shortH), requiresRestartIfLive = true)
                }
            }
            resPillButtons[longW] = b
            resRow.addView(b)
        }
        cardVideo.addView(resRow)

        // Direct-Tap Codec & FPS Row
        cardVideo.addView(labelText("Direct-Tap Hardware Codec & Frame Rate:"))
        val codecFpsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        }
        listOf("HEVC" to "HEVC", "H264" to "H.264", "AV1" to "AV1").forEachIndexed { idx, (cKey, cTitle) ->
            val b = Button(this).apply {
                text = cTitle
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setPadding(dp(2), dp(5), dp(2), dp(5))
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    if (idx > 0) marginStart = dp(3)
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
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    marginStart = dp(4)
                }
                setOnClickListener {
                    updateSetting(settings.copy(fps = fpsVal), requiresRestartIfLive = true)
                }
            }
            fpsPillButtons[fpsVal] = b
            codecFpsRow.addView(b)
        }
        cardVideo.addView(codecFpsRow)

        // Direct-Tap Framing Row
        cardVideo.addView(labelText("Direct-Tap Screen Framing (STRETCH = Full 16:9 Zero-Bars + 100% HUD):"))
        val framingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2); bottomMargin = dp(3) }
        }
        listOf("FIT" to "FIT", "STRETCH" to "STRETCH (16:9)", "SMART" to "SMART", "FILL" to "FILL").forEachIndexed { idx, (fKey, fTitle) ->
            val b = Button(this).apply {
                text = fTitle
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setPadding(dp(2), dp(5), dp(2), dp(5))
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    if (idx > 0) marginStart = dp(3)
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

        btnRateControl = actionButton("", "#13284A") {
            updateSetting(settings.copy(useVbr = !settings.useVbr), requiresRestartIfLive = true)
        }
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

        cardVideo.addView(labelText("Stackable Live Visual Filters (Mix & Match Any Combo Live):"))
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

        // Collapsible Drawer: Scenes, .PNG Overlay Layout, Text Banner, Dual Privacy Blur & Pro Silicon
        boxSceneProDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = cardDrawable("#08101E", "#1E3A5F")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            layoutParams = lp
        }
        btnToggleSceneProDrawer = actionButton(
            "▼ Expand: Custom Scenes, .PNG Corner Logo, Text Banner, Privacy Blur & SolCodec™",
            "#122036"
        ) {
            val show = boxSceneProDrawer.visibility != View.VISIBLE
            boxSceneProDrawer.visibility = if (show) View.VISIBLE else View.GONE
            btnToggleSceneProDrawer.text = if (show) {
                "▲ Hide: Custom Scenes, .PNG Corner Logo, Text Banner, Privacy Blur & SolCodec™"
            } else {
                "▼ Expand: Custom Scenes, .PNG Corner Logo, Text Banner, Privacy Blur & SolCodec™"
            }
        }

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
        btnOverlayPosition = actionButton("", "#13284A") { cycleOverlayPosition() }
        btnPickShortsPoster = actionButton("", "#13284A") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.SHORTS_POSTER
            imageAssetPickerLauncher.launch("image/*")
        }
        btnToggleOverlayOn = actionButton("Toggle .PNG Overlay ON/OFF", "#101929") {
            updateSetting(settings.copy(overlayImageEnabled = !settings.overlayImageEnabled))
        }
        val btnClearGraphics = actionButton("Clear Saved Graphics", "#1E293B") {
            SolSceneAssetStore.AssetSlot.entries.forEach { SolSceneAssetStore.clearCustomAsset(this, it) }
            updateSetting(settings.copy(overlayImageEnabled = false))
            Toast.makeText(this, "Cleared custom scene/overlay images (reverted to built-in slates).", Toast.LENGTH_SHORT).show()
        }

        boxSceneProDrawer.addView(buttonRow(btnPickStartImg, btnPickBrbImg))
        boxSceneProDrawer.addView(buttonRow(btnPickOverlayPng, btnPickShortsPoster))
        boxSceneProDrawer.addView(btnOverlayPosition)
        boxSceneProDrawer.addView(buttonRow(btnToggleOverlayOn, btnClearGraphics))

        boxSceneProDrawer.addView(labelText("Built-In GPU Live Text Banner (Updates Live on Next Frame!):"))
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
        btnHdr10 = actionButton("", "#101929") {
            updateSetting(settings.copy(hdr10Enabled = !settings.hdr10Enabled), requiresRestartIfLive = true)
        }
        btnRoiMode = actionButton("", "#101929") { cycleRoiMode() }
        btnKeyframeAndBFrames = actionButton("", "#13284A") { cycleKeyframeAndBFrames() }
        btnQpAndLatency = actionButton("", "#13284A") { cycleQpAndLatency() }

        boxSceneProDrawer.addView(buttonRow(btnGlEngine, btnHdr10))
        boxSceneProDrawer.addView(btnRoiMode)
        boxSceneProDrawer.addView(buttonRow(btnKeyframeAndBFrames, btnQpAndLatency))

        cardVideo.addView(btnToggleSceneProDrawer)
        cardVideo.addView(boxSceneProDrawer)
        container.addView(cardVideo)

        // === CARD 4: SOLLUXE™ 2.0 13-STAGE VOICE STUDIO, 12-MODE MATRIX & 250% GAME SOUNDSTAGE ===
        val cardAudio = sectionCard(
            heading = "4. 13-Stage Voice Studio & 3D Game Audio",
            engineBadge = "SOLLUXE™ 2.0",
            badgeHex = "#8B5CF6"
        )
        cardAudio.addView(
            bodyText("Auto-adapts across Phone Studio Mic, Wired/USB-C Close Mic & Bluetooth! Includes 50/65/85Hz Gender HPF, Hard-Breath Plosive Guard, Cubic Analog Sub-Warmth, -3.5dB Boxy Mud Scoop, 920Hz Velvet Dip & Aphex Silk Air Exciter.")
        )

        btnMicDevice = actionButton("", "#13284A") { cycleMicInputDevice() }
        btnMicSource = actionButton("", "#13284A") { cycleMicSourceMode() }
        btnAudioBitrate = actionButton("", "#13284A") { cycleAudioBitrate() }

        cardAudio.addView(btnMicDevice)
        cardAudio.addView(buttonRow(btnMicSource, btnAudioBitrate))

        cardAudio.addView(labelText("Voice Profile (Pick 1 — Auto-Sets 50/65/85Hz HPF, Cubic Sub-Warmth, 315–365Hz Mud Scoop, 920Hz Velvet Dip & Aphex Silk Air):"))
        btnGenderNeutral = actionButton("Neutral (65Hz)", "#101929") {
            updateSetting(MicDspProcessor.applyGenderProfileToSettings(settings, "NEUTRAL"))
        }
        btnGenderMale = actionButton("Male Voice (50Hz)", "#101929") {
            updateSetting(MicDspProcessor.applyGenderProfileToSettings(settings, "MALE"))
        }
        btnGenderFemale = actionButton("Female Voice (85Hz)", "#101929") {
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

        // Glowing Cyan SeekBar Volume Sliders + -10% / +10% Fine Step Buttons
        cardAudio.addView(labelText("Post-DSP Mic Boost (50%–250% • Placed at Stage 13 After Slow AGC):"))
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

        cardAudio.addView(labelText("Internal Stereo Game Mix (0%–250% • Protected by 4x True-Peak Limiter):"))
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

        cardAudio.addView(labelText("Stackable Stereo Game Soundstage (Stream-Only • Phase-Safe 62Hz Voice Ducking):"))
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

        // Collapsible Drawer: Individual 13-Stage Voice Filter Switches
        boxVoiceStageDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = cardDrawable("#08101E", "#1E3A5F")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            layoutParams = lp
        }
        btnToggleVoiceStageDrawer = actionButton(
            "▼ Expand: Individual Voice Filter Switches (Mix & Match All 13 Stages)",
            "#122036"
        ) {
            val show = boxVoiceStageDrawer.visibility != View.VISIBLE
            boxVoiceStageDrawer.visibility = if (show) View.VISIBLE else View.GONE
            btnToggleVoiceStageDrawer.text = if (show) {
                "▲ Hide: Individual Voice Filter Switches"
            } else {
                "▼ Expand: Individual Voice Filter Switches (Mix & Match All 13 Stages)"
            }
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
            "10s Studio Voice Test (Loads 100% of your active Profile, Hard-Breath Plosive Guard, Cubic Sub-Warmth, Aphex Silk Air & Mic Boost % so you hear your exact live stream voice!):"
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

        btnGoLive = actionButton("GO LIVE — START STREAM", "#00897B") {
            onGoLiveClicked()
        }.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(52)
        }
        cardLaunch.addView(btnGoLive)

        btnSavedClipsQuick = actionButton("★ Saved Clips & AI Highlights", "#4C1D95") {
            showSavedClipsDialog()
        }
        btnGlipBench = actionButton("Benchmark Other App", "#152338") {
            toggleCompetitorBenchmark()
        }

        cardLaunch.addView(
            buttonRow(
                actionButton("Session Reports & History", "#153969") { showDiagnosticsDialog() },
                btnSavedClipsQuick
            )
        )
        cardLaunch.addView(
            buttonRow(
                btnGlipBench,
                actionButton("Refresh Mics / HW Scan", "#152338") { refreshHardwareAndMicsAsync() },
                actionButton("Check GitHub Update", "#152338") { checkGitHubReleaseUpdate() }
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
            text = "ॐ नमः शिवाय\nMade by Sol (@Sol_CODM) • Powered by SolApex™ HyperStream Engine v$appVersionName (SolCore Ultra • SolLuxe 2.0 • SolRelay Omega • SolCodec)"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        container.addView(footer)

        rootScroll.addView(container)
        outerRoot.addView(fixedHeaderBox)
        outerRoot.addView(headerDivider)
        outerRoot.addView(rootScroll)
        applyResponsiveContainerPadding()
        return outerRoot
    }

    private fun makeHeroTile(
        caption: String,
        initialValue: String,
        onClick: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0D1C33"), Color.parseColor("#091324"))
            ).apply {
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#00E5FF"))
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
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
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
            ).apply { topMargin = dp(4); bottomMargin = dp(2) }
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
                marginStart = dp(6)
                marginEnd = dp(6)
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

    /**
     * Unified SolApex™ Button State Styler:
     * - ON / Active: Deep Royal-Cyan Fill (#0D3652) + Electric Cyan Border (#00E5FF) + Pure White Text
     * - OFF / Inactive: Midnight Slate Fill (#101929) + Subtle Slate Border (#24344D) + Cool Silver Text (#94A3B8)
     */
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
            btn.background = roundedDrawable("#101929", "#24344D")
        }
    }

    /**
     * Platform-Aware & Voice-Aware Tier Presets (#8):
     * Applies optimal defaults across all 5 tiers, respects active Platform in Card 2 (YouTube vs Twitch 6M vs Kick 8M),
     * sets the gender-matched HPF (50/65/85 Hz), and resets advanced silicon controls cleanly.
     */
    private fun applyTierDefaultsToSettings(
        base: StreamSettings,
        tier: DeviceTier,
        scan: DeviceScanSummary
    ): StreamSettings {
        return KeystoreVault.applyPlatformAndVoiceAwareTierPreset(base, tier, scan)
    }

    private fun refreshHardwareAndMicsAsync() {
        // Never run hardware MediaCodec probing while a live broadcast is starting or active!
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
                    "SolApex™ HyperStream • ${scanSummary.friendlyHardwareLine()}\n${scanSummary.toBadgeText()}"
                refreshAllUi()
                Toast.makeText(this, "Hardware & mic list refreshed!", Toast.LENGTH_SHORT).show()
            }
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

        val idleHealth = CodecCapabilityProbe.readLiveHardwareHealth(this)
        tvLiveBanner.text = when {
            SolStreamService.needsProjectionReapproval ->
                "STATUS: Screen capture paused by Android (lock/system). Tap orange button below to re-approve!"
            isLive ->
                "STATUS: ${SolStreamService.currentLiveStateLabel} • All live controls & Floating Pill sync in 0 ms!"
            SolStreamService.isGlipBenchmarkRunning ->
                "STATUS: Passive Benchmark Recording Thermal & RAM for $benchmarkTargetAppName..."
            else ->
                "STATUS: ${idleHealth.toIdleStatusBanner()}"
        }
        btnReapproveCapture.visibility = if (SolStreamService.needsProjectionReapproval) View.VISIBLE else View.GONE

        // 3-Tile Hero Summary Bar
        val longDim = max(settings.width, settings.height)
        val heroResShort = when {
            longDim >= 3840 -> "4K (2160p)"
            longDim >= 2560 -> "1440p (2K)"
            longDim >= 1920 -> "1080p (FHD)"
            longDim >= 1280 -> "720p (HD)"
            longDim >= 800 -> "480p"
            else -> "360p"
        }
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
                else -> "#101929"
            }
            val stroke = if (selected) "#00E5FF" else if (t == recTier) "#38BDF8" else "#24344D"
            btn.setTextColor(Color.parseColor(if (selected) "#F8FAFC" else "#94A3B8"))
            btn.background = roundedDrawable(fill, stroke)
        }
        tvTierDescription.text =
            "Active Tier: ${activeTier.title} (${activeTier.badgeSummary})\n" +
                "Scanner Recommended for ${scanSummary.deviceModel}: ${recTier.title} Tier"

        // Card 1: Pre-Flight Readiness & Smart Auto-Collapse (#10, #13)
        val allFourReady = micGranted && notifGranted && battUnrestricted && accEnabled
        tvPreflightStatus.text =
            "• Mic Permission: ${okWait(micGranted)}   • Notifications: ${okWait(notifGranted)}\n" +
                "• Battery Unrestricted: ${okWait(battUnrestricted)}   • Accessibility Service: ${okWait(accEnabled)}"
        tvPreflightAudioWifiLine.text = SolDualAudioEngine.inspectPreFlightAudioAndWifiCoexistence(this)

        styleToggleBtn(
            btnGrantMicNotif,
            isOn = micGranted && notifGranted,
            activeFillHex = "#0D3652",
            activeStrokeHex = "#00E5FF"
        )
        styleToggleBtn(
            btnUnrestrictedBatt,
            isOn = battUnrestricted,
            activeFillHex = "#0D3652",
            activeStrokeHex = "#00E5FF"
        )
        styleToggleBtn(
            btnTapAccessibilityFirst,
            isOn = accEnabled,
            activeFillHex = "#0D3652",
            activeStrokeHex = "#00E5FF"
        )
        styleToggleBtn(
            btnUnlockRestricted,
            isOn = accEnabled,
            activeFillHex = "#13284A",
            activeStrokeHex = "#38BDF8"
        )

        if (allFourReady) {
            btnPreflightCollapsedToggle.visibility = View.VISIBLE
            btnPreflightCollapsedToggle.text = if (preflightManuallyExpanded) {
                "▲ All 4 Pre-Flight Checks READY (Tap to Collapse Setup Buttons)"
            } else {
                "✅ All 4 Pre-Flight Checks READY (Tap to Show Setup & All-Brand Guide)"
            }
            boxPreflightButtonsContainer.visibility = if (preflightManuallyExpanded) View.VISIBLE else View.GONE
        } else {
            btnPreflightCollapsedToggle.visibility = View.GONE
            boxPreflightButtonsContainer.visibility = View.VISIBLE
        }
        tvRestrictedHint.visibility = if (accEnabled) View.GONE else View.VISIBLE

        // Card 2: 4-Pill Platform Selector, Key Vault Badge, Simulcast & Speed Test
        val activePlat = settings.platformPreset.uppercase(Locale.US)
        platformPillButtons.forEach { (pKey, btn) ->
            val sel = (pKey == activePlat)
            styleToggleBtn(btn, isOn = sel)
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
            "Auto-Use Slot #2 Key When 9:16 Shorts Selected: ${onOff(settings.useShortsKeySlot)}"
        styleToggleBtn(btnUseShortsSlot, isOn = settings.useShortsKeySlot)

        val dualSupported = scanSummary.supportsDualFormatCompanion()
        val splitModeActive = settings.simulcastSplitEncode && dualSupported
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

        btnDualCompanion.text =
            "Dual-Format 16:9 + 9:16 Simultaneous Companion: ${onOff(settings.dualFormatCompanionEnabled)}" +
                (if (!dualSupported) " (Needs Flagship/Ultra 2-Instance HW)" else "")
        styleToggleBtn(btnDualCompanion, isOn = settings.dualFormatCompanionEnabled)

        btnCompanionBitrate.text =
            "9:16 Companion Bitrate: ${formatBitrateMbps(settings.dualFormatBitrateKbps)} (Tap to step 2–20 Mbps)"
        btnNetTest.isEnabled = !isLive && !netTestBusy.get()
        btnNetTest.text = if (isLive) {
            "Speed Test Locked While Live (See Floating Pill Net Tab)"
        } else {
            "Test Internet: Download, Upload, TCP RTT & Bitrate Advisor"
        }

        // Card 3: Direct-Tap Pills & Video Engine
        val restartSuffix = if (isLive) " [Next]" else ""
        val av1Available = scanSummary.av1Hardware != null &&
            (scanSummary.av1Hardware?.supports1440p60 == true || scanSummary.av1Hardware?.supports1080p60 == true)
        val singleEncodeSimLock = (settings.simulcastTwitchEnabled || settings.simulcastKickEnabled) && !settings.simulcastSplitEncode

        resPillButtons.forEach { (wKey, btn) ->
            styleToggleBtn(btn, isOn = (longDim == wKey))
        }
        val effectiveActiveCodec = if (singleEncodeSimLock) "H264" else settings.codec.uppercase(Locale.US)
        codecPillButtons.forEach { (cKey, btn) ->
            styleToggleBtn(btn, isOn = (cKey == effectiveActiveCodec))
        }
        fpsPillButtons.forEach { (fVal, btn) ->
            styleToggleBtn(btn, isOn = (settings.fps == fVal))
        }
        framingPillButtons.forEach { (fKey, btn) ->
            styleToggleBtn(btn, isOn = settings.framingMode.equals(fKey, ignoreCase = true))
        }

        btnCodec.text = if (singleEncodeSimLock) {
            "Codec: H264 (Locked for Single-Encode Simulcast)$restartSuffix"
        } else {
            "Codec: ${settings.codec}" + (if (!av1Available && settings.codec != "AV1") " (AV1: No HW Enc)" else "") + restartSuffix
        }
        btnResolution.text = "Res: $heroResShort$restartSuffix"
        btnFps.text = "Stream: ${settings.fps} FPS$restartSuffix" +
            (if (longDim <= 864 && settings.fps == 60) " (YT 480p plays @30)" else "")
        btnOrientationFlip.text = if (settings.isVertical) {
            "Format: 9:16 Vertical Shorts$restartSuffix"
        } else {
            "Format: 16:9 Wide (${settings.landscapeFlip}° USB-C Flip)"
        }

        btnBitrateCycle.text = "Target: $mbpsStr"
        btnRateControl.text = "Rate: ${if (settings.useVbr) "VBR (Test)" else "CBR (Rec)"}$restartSuffix"
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

        btnShadowBoost.text = "AI Dark-Corner Boost: ${onOff(settings.shadowBoost)}"
        styleToggleBtn(btnShadowBoost, isOn = settings.shadowBoost)

        btnColorTemp.text = "Color Temp: ${settings.colorTempFilter}"
        styleToggleBtn(btnColorTemp, isOn = settings.colorTempFilter != "NEUTRAL", activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        btnSharpen.text = if (settings.gpuSharpen) {
            "GPU Crisp Sharpen: ON (Turn OFF to reduce phone heat)"
        } else {
            "GPU Crisp Sharpen: OFF (Recommended — Coolest Temp)"
        }
        styleToggleBtn(btnSharpen, isOn = settings.gpuSharpen)

        // Scenes, Overlay Position, Dual Privacy Blur & Pro Silicon Drawer
        val hasStart = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.STARTING_SOON)
        val hasBrb = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.BRB_SCENE)
        val hasOv = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.OVERLAY_PNG)
        val hasPost = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.SHORTS_POSTER)
        btnPickStartImg.text = "Starting Soon Img (${if (hasStart) "Custom Saved" else "Default Slate"})"
        styleToggleBtn(btnPickStartImg, isOn = hasStart, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        btnPickBrbImg.text = "BRB Privacy Img (${if (hasBrb) "Custom Saved" else "Default Slate"})"
        styleToggleBtn(btnPickBrbImg, isOn = hasBrb, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        btnPickOverlayPng.text = ".PNG Overlay (${if (hasOv) "Saved • ${onOff(settings.overlayImageEnabled)}" else "Pick .PNG"})"
        styleToggleBtn(btnPickOverlayPng, isOn = settings.overlayImageEnabled)
        styleToggleBtn(btnToggleOverlayOn, isOn = settings.overlayImageEnabled)

        val posFriendly = when (settings.overlayPosition) {
            "TOP_LEFT" -> "Corner Logo: Top-Left"
            "TOP_RIGHT" -> "Corner Logo: Top-Right"
            "BOTTOM_LEFT" -> "Corner Logo: Bottom-Left"
            "BOTTOM_RIGHT" -> "Corner Logo: Bottom-Right"
            else -> "Full-Screen 16:9 Frame"
        }
        btnOverlayPosition.text = ".PNG Overlay Layout: $posFriendly"
        btnPickShortsPoster.text = "9:16 Poster (${if (hasPost) "Custom Saved" else "Pick Image"})"
        styleToggleBtn(btnPickShortsPoster, isOn = hasPost, activeFillHex = "#13284A", activeStrokeHex = "#38BDF8")

        if (!etBannerText.hasFocus() && etBannerText.text?.toString() != settings.liveBannerText) {
            etBannerText.setText(settings.liveBannerText)
        }
        btnBannerToggle.text = "Live Text Banner: ${onOff(settings.liveBannerEnabled)}"
        styleToggleBtn(btnBannerToggle, isOn = settings.liveBannerEnabled)

        btnBannerPos.text = "Banner Pos: ${settings.liveBannerPosition}"
        btnShortsBackdrop.text = "9:16 Shorts Canvas: ${settings.shortsBackdropMode} (40% Cooler GPU Blur)"
        btnMinimapShield.text =
            "Privacy / Minimap Blur Shield: ${onOff(settings.minimapShieldEnabled)} (${settings.minimapShieldCorner})"
        styleToggleBtn(btnMinimapShield, isOn = settings.minimapShieldEnabled)

        btnAutoBlurKeyboard.text =
            "Auto-Blur Keyboard (Bottom Half Privacy When Typing): ${onOff(settings.autoBlurKeyboard)}"
        styleToggleBtn(btnAutoBlurKeyboard, isOn = settings.autoBlurKeyboard)

        val glDisp = if (settings.glEngineMode == "ES20") "ES 2.0" else "ES 3.0 Fast-FP16"
        btnGlEngine.text = "OpenGL Engine: ${settings.glEngineMode} ($glDisp • Device Max: ES ${scanSummary.maxGlEsVersion})$restartSuffix"
        val hdrSupported = scanSummary.hevcHardware?.supports10BitHdr == true
        btnHdr10.text = "True 10-Bit HDR10 (HLG): ${onOff(settings.hdr10Enabled)}" +
            (if (!hdrSupported) " (HW 8-Bit Only)" else "") + restartSuffix
        styleToggleBtn(btnHdr10, isOn = settings.hdr10Enabled)

        btnRoiMode.text = "Hardware ROI Focus: ${settings.roiMode} (Crosshair/Minimap QP Boost)"
        styleToggleBtn(btnRoiMode, isOn = settings.roiMode != "OFF")

        btnKeyframeAndBFrames.text = "IDR Keyframe: ${settings.keyframeIntervalSec}s | B-Frames: ${settings.maxBFrames}$restartSuffix"
        val qpLabel = when (settings.qpMaxCap) {
            0 -> "Auto (14..51)"
            32 -> "14..32 (Ultra Crisp Foliage)"
            36 -> "14..36 (High)"
            38 -> "14..38 (Balanced)"
            else -> "14..${settings.qpMaxCap}"
        }
        btnQpAndLatency.text = "QP Range: $qpLabel | Low-Lat Hint: ${onOff(settings.lowLatencyCodecHint)}$restartSuffix"

        // Card 4: SolLuxe™ 2.0 13-Stage Audio Studio & SeekBar Sync
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
        btnAudioBitrate.text = "Master AAC: ${settings.audioBitrateKbps} kbps$restartSuffix"

        val gProf = settings.voiceGenderProfile.uppercase(Locale.US)
        styleToggleBtn(btnGenderNeutral, isOn = (gProf == "NEUTRAL"))
        styleToggleBtn(btnGenderMale, isOn = (gProf == "MALE"))
        styleToggleBtn(btnGenderFemale, isOn = (gProf == "FEMALE"))

        btnVoicePreset.text = "Voice Type: ${MicDspProcessor.presetDisplayName(settings.voicePreset)}"
        styleToggleBtn(btnVoicePreset, isOn = settings.voicePreset != "RAW", activeFillHex = "#2E1065", activeStrokeHex = "#8B5CF6")

        // Verify both isLibraryLoaded and isNeuralModelReady on the Denoiser badge
        val nsModeDesc = when (settings.noiseSuppressMode) {
            "RNNOISE" -> when {
                SolNativeRnnoise.isLibraryLoaded && SolNativeRnnoise.isNeuralModelReady -> "Native C 48kHz RNNoise"
                SolNativeRnnoise.isLibraryLoaded -> "Native C 48kHz Bark-MMSE"
                else -> "RNNoise (Spectral Fallback)"
            }
            "LIGHT" -> "Light Mathematical Cut"
            "HW_NS" -> "Phone Chip HW NS"
            else -> "OFF"
        }
        val nsLvlDesc = when (settings.rnnoiseLevel) {
            0 -> "OFF (0%)"
            1 -> "Lv1 Gentle (65%)"
            2 -> "Lv2 Natural (88%)"
            else -> "Lv3 Max (98%)"
        }
        val denoiserActive = settings.noiseSuppressMode != "OFF" && settings.rnnoiseLevel > 0
        btnNoiseSuppressMode.text = "Denoiser (Pick 1): $nsModeDesc"
        styleToggleBtn(btnNoiseSuppressMode, isOn = denoiserActive)
        btnNoiseSuppressLevel.text = "Strength: $nsLvlDesc"
        styleToggleBtn(btnNoiseSuppressLevel, isOn = denoiserActive)

        btnMicGain.text = "🎙️ Mic Boost: ${settings.micGainPercent}% (Post-DSP Stage 13)"
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

        // Individual 13-Stage Voice Filter Switches
        btnStageHpf.text = if (settings.hpfCutoffHz == 0) "1. HPF Rumble: OFF" else "1. HPF Rumble: ${settings.hpfCutoffHz} Hz"
        styleToggleBtn(btnStageHpf, isOn = settings.hpfCutoffHz > 0)

        btnStageTransient.text = "2. Spark/Hammer & Grip Blanker: ${onOff(settings.transientTamerEnabled)}"
        styleToggleBtn(btnStageTransient, isOn = settings.transientTamerEnabled)

        btnStageGate.text = "3. 0-ms Gate + Hard-Breath Guard: ${onOff(settings.noiseGateEnabled)}"
        styleToggleBtn(btnStageGate, isOn = settings.noiseGateEnabled)

        btnStageEq.text = "4. ${settings.voiceGenderProfile} Proximity, Mud Scoop & 920Hz Velvet: ${onOff(settings.voiceEqEnabled)}"
        styleToggleBtn(btnStageEq, isOn = settings.voiceEqEnabled)

        btnStageVelvet.text = "5. Cubic Sub-Warmth & Aphex Silk Air: ${onOff(settings.velvetWarmthEnabled)}"
        styleToggleBtn(btnStageVelvet, isOn = settings.velvetWarmthEnabled)

        btnStageDeEss.text = "6. Post-Comp Split-Band De-Esser: ${onOff(settings.deEsserEnabled)}"
        styleToggleBtn(btnStageDeEss, isOn = settings.deEsserEnabled)

        btnStageComp.text = "7. High-Pass Sidechain 3:1 Leveler: ${onOff(settings.compressorEnabled)}"
        styleToggleBtn(btnStageComp, isOn = settings.compressorEnabled)

        btnStageWhisper.text = "8. Whisper Callout Lift: ${onOff(settings.whisperLiftEnabled)}"
        styleToggleBtn(btnStageWhisper, isOn = settings.whisperLiftEnabled)

        btnStageSlowAgc.text = "9. Speech-Gated Slow AGC: ${onOff(settings.slowAgcEnabled)}"
        styleToggleBtn(btnStageSlowAgc, isOn = settings.slowAgcEnabled)

        btnStageSpeakerAec.text = "10. Speaker Echo Canceler: ${onOff(settings.speakerAecEnabled)}"
        styleToggleBtn(btnStageSpeakerAec, isOn = settings.speakerAecEnabled)

        btnExtraSafeguards.text =
            "Scene Auto-Mute Mic: ${onOff(settings.brbMuteMicToo)} | Low-Lat Wi-Fi Lock: ${onOff(settings.wifiLowLatencyLock)}"
        styleToggleBtn(btnExtraSafeguards, isOn = settings.brbMuteMicToo || settings.wifiLowLatencyLock)

        btnRecordMicTest.isEnabled = !isLive
        btnPlayMicTest.isEnabled = !isLive
        btnRecordMicTest.text = if (isLive) "10s Test Locked (Live)" else "Record 10s Test"

        // Card 5: Go Live / Stop Live Stream + Saved Clips Count
        val clipsCount = DiagnosticsLogger.getClipHighlightCount()
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
        settings = newSettings
        KeystoreVault.saveSettings(this, settings)
        if (SolStreamService.isBroadcastLive) {
            SolStreamService.instance?.applyDashboardSettingsLive(settings)
            if (requiresRestartIfLive) {
                Toast.makeText(
                    this,
                    "Saved for next stream! (Encoder Codec/Res/FPS/HDR/AAC apply on next Go Live.)",
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

    // === PLATFORM, SIMULCAST & VIDEO PILL HANDLERS ===
    /**
     * Selecting Twitch auto-configures 1080p60 • H.264 • 6 Mbps • 160k AAC;
     * selecting Kick auto-configures 1080p60 • H.264 • 8 Mbps • 160k AAC;
     * switching back to YouTube restores the active Device Tier's YouTube resolution/bitrate/codec if it was clamped!
     */
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
            "TWITCH" -> settings.copy(
                platformPreset = "TWITCH",
                codec = "H264",
                width = min(settings.width, 1920),
                height = min(settings.height, 1080),
                targetBitrateKbps = min(settings.targetBitrateKbps, 6000),
                audioBitrateKbps = min(settings.audioBitrateKbps, 160),
                useVbr = false,
                hdr10Enabled = false
            )
            "KICK" -> settings.copy(
                platformPreset = "KICK",
                codec = "H264",
                width = min(settings.width, 1920),
                height = min(settings.height, 1080),
                targetBitrateKbps = min(settings.targetBitrateKbps, 8000),
                audioBitrateKbps = min(settings.audioBitrateKbps, 160),
                useVbr = false,
                hdr10Enabled = false
            )
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
                        codec = if (hevcOk && !settings.simulcastTwitchEnabled && !settings.simulcastKickEnabled) "HEVC" else settings.codec
                    )
                }
            }
            else -> settings.copy(platformPreset = "CUSTOM")
        }
        updateSetting(updated, requiresRestartIfLive = true)
    }

    private fun toggleSimulcastTarget(isTwitch: Boolean) {
        val newTwitch = if (isTwitch) !settings.simulcastTwitchEnabled else settings.simulcastTwitchEnabled
        val newKick = if (!isTwitch) !settings.simulcastKickEnabled else settings.simulcastKickEnabled
        val anySim = newTwitch || newKick
        val singleEncode = anySim && !settings.simulcastSplitEncode

        val updated = if (singleEncode) {
            val capKbps = if (newTwitch) min(settings.targetBitrateKbps, 6000) else min(settings.targetBitrateKbps, 8000)
            settings.copy(
                simulcastTwitchEnabled = newTwitch,
                simulcastKickEnabled = newKick,
                codec = "H264",
                width = min(settings.width, 1920),
                height = min(settings.height, 1080),
                targetBitrateKbps = capKbps,
                audioBitrateKbps = min(settings.audioBitrateKbps, 160),
                useVbr = false,
                hdr10Enabled = false
            )
        } else {
            settings.copy(
                simulcastTwitchEnabled = newTwitch,
                simulcastKickEnabled = newKick
            )
        }
        updateSetting(updated, requiresRestartIfLive = true)
    }

    private fun toggleDualFormatCompanion() {
        val nextState = !settings.dualFormatCompanionEnabled
        if (nextState) {
            if (!scanSummary.supportsDualFormatCompanion()) {
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
        val av1Ok = scanSummary.av1Hardware != null &&
            (scanSummary.av1Hardware?.supports1440p60 == true || scanSummary.av1Hardware?.supports1080p60 == true)
        if (codecKey == "AV1" && !av1Ok) {
            Toast.makeText(
                this,
                "This processor has no Hardware AV1 Encoder (CPU-SW AV1 is blocked to protect 120 FPS gaming). Using Hardware HEVC!",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        updateSetting(settings.copy(codec = codecKey), requiresRestartIfLive = true)
    }

    private fun cycleCodec() {
        val av1Ok = scanSummary.av1Hardware != null &&
            (scanSummary.av1Hardware?.supports1440p60 == true || scanSummary.av1Hardware?.supports1080p60 == true)
        val order = if (av1Ok) listOf("HEVC", "H264", "AV1") else listOf("HEVC", "H264")
        val next = order[(order.indexOf(settings.codec) + 1) % order.size]
        updateSetting(settings.copy(codec = next), requiresRestartIfLive = true)
    }

    private fun cycleResolution() {
        val tiers = listOf(
            640 to 360,
            854 to 480,
            1280 to 720,
            1920 to 1080,
            2560 to 1440,
            3840 to 2160
        )
        val curLong = max(settings.width, settings.height)
        val curIdx = tiers.indexOfFirst { it.first == curLong }.let { if (it < 0) 4 else it }
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
                updateSetting(settings.copy(isVertical = true, landscapeFlip = 0), requiresRestartIfLive = true)
            else ->
                updateSetting(settings.copy(isVertical = false, landscapeFlip = 0), requiresRestartIfLive = true)
        }
    }

    private fun stepBitrate(delta: Int) {
        val ladder = SolStreamService.MASTER_BITRATE_LADDER_KBPS
        val cur = settings.targetBitrateKbps
        val nearestIdx = ladder.indices.minByOrNull { abs(ladder[it] - cur) } ?: 13
        val nextIdx = (nearestIdx + delta).coerceIn(0, ladder.lastIndex)
        updateSetting(settings.copy(targetBitrateKbps = ladder[nextIdx]))
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

    private fun cycleOverlayPosition() {
        val order = SolStreamService.OVERLAY_POSITION_ORDER
        val next = order[(order.indexOf(settings.overlayPosition) + 1) % order.size]
        updateSetting(settings.copy(overlayPosition = next))
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

    private fun cycleKeyframeAndBFrames() {
        val (kf, bf) = when {
            settings.keyframeIntervalSec == 2 && settings.maxBFrames == 0 -> 1 to 0
            settings.keyframeIntervalSec == 1 && settings.maxBFrames == 0 -> 2 to 1
            settings.keyframeIntervalSec == 2 && settings.maxBFrames == 1 -> 2 to 2
            else -> 2 to 0
        }
        updateSetting(settings.copy(keyframeIntervalSec = kf, maxBFrames = bf), requiresRestartIfLive = true)
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
        val rates = listOf(64, 96, 128, 160, 192, 256, 320)
        val next = rates[(rates.indexOf(settings.audioBitrateKbps) + 1) % rates.size]
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
        val next = order[(order.indexOf(settings.hpfCutoffHz) + 1) % order.size]
        updateSetting(settings.copy(hpfCutoffHz = next))
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

    // === CLIPBOARD HELPER (Ensures Samsung Keyboard Clipboard History Always Shows Copied Items) ===
    private fun copyPlainTextToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
                }
            } catch (_: Exception) {
            }
        }
        cm.setPrimaryClip(clip)
    }

    // === PRESET SHARE, SMART HARDWARE CLAMPING & COMPLETE GUIDE / PRIVACY POLICY / LICENSES ===
    private fun copyPresetToClipboard() {
        val json = KeystoreVault.exportPresetJson(settings)
        copyPlainTextToClipboard("SolStream Ultra Preset", json)
        Toast.makeText(
            this,
            "Settings Preset copied! (Stream Keys are NEVER included — safe to share with friends!)",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Smart Cross-Phone Preset Clamping (#4):
     * Validates imported JSON presets against the receiving phone's hardware scan and adapts
     * unsupported resolutions/codecs cleanly.
     */
    private fun importPresetFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val raw = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (raw.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        val imported = KeystoreVault.importPresetJson(raw, settings)
        if (imported != null) {
            val (clamped, wasAdapted) = KeystoreVault.clampImportedSettingsToHardware(imported, scanSummary)
            updateSetting(clamped, requiresRestartIfLive = true)
            val msg = if (wasAdapted) {
                "Preset imported & auto-adapted to your phone's verified hardware capabilities!"
            } else {
                "Preset imported and applied!"
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Clipboard does not contain a valid SolStream Preset JSON.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Comprehensive, Easy-to-Read In-App Studio Guide + Google Play Privacy Policy + Open-Source Licenses.
     */
    private fun showPlainEnglishGlossaryDialog() {
        val guide = """
            === SOLSTREAM STUDIO ULTRA v$appVersionName (SolApex™ HyperStream) — COMPLETE USER GUIDE, PRIVACY POLICY & LICENSES ===

            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            PART 1 — QUICK-START STUDIO GUIDE (PLAIN ENGLISH)
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            1. SMART DEVICE TIERS & HERO BAR (Top & Card 0)
            • 3-Tile Hero Bar: Tap Resolution, Frame Rate, or Bitrate at the very top anytime for 1-tap switching.
            • 5 Smart Tiers (Lite / Balanced / High / Flagship / Ultra): Auto-configures the best resolution, bitrate, and 13-Stage SolLuxe™ 2.0 voice studio for your phone while respecting your selected Platform (YouTube vs Twitch 6M vs Kick 8M).

            2. PLATFORMS, 9:16 SHORTS & MULTI-PLATFORM SIMULCAST (Card 2)
            • YouTube: Supports 360p to 4K60 in HEVC, H.264, or AV1 up to 50+ Mbps.
            • Twitch (6 Mbps H.264 Cap) & Kick (8 Mbps H.264 Cap): 1 tap auto-configures 1080p60 H.264 + 160k AAC so Twitch/Kick never reject your stream.
            • YouTube 9:16 Shorts Tip: In YouTube Studio, keep 'Turn on manual settings' OFF so your single Default Stream Key works automatically for both 16:9 Landscape and 9:16 Vertical Shorts!
            • Simulcast Modes: Choose Single-Encode Cool Mode (0% extra encoder heat) or Split-Encode Dual HW Quality (sends 1440p HEVC to YouTube + 1080p H.264 to Twitch/Kick simultaneously on Flagship/Ultra chips).

            3. SOLCORE ULTRA™ VIDEO, STRETCH FRAMING & DUAL PRIVACY BLUR (Card 3)
            • STRETCH (16:9 Full): Maps all 4 corners of your phone screen to all 4 corners of the stream—zero black bars and 100% of your game HUD visible!
            • SMART + Zoom (100%–250%): Zooms smoothly into your center crosshair in both 16:9 Landscape and 9:16 Shorts.
            • Dual-Layer Privacy Blur Shield:
              - Auto-Blur Keyboard: Automatically blurs the bottom 54% of the stream the instant your keyboard opens (protecting passwords, chats & keyboard photos) while keeping your Minimap Blur active!
              - 8 Screen Zones: Blur Top-Right (Small Room Code / Med Minimap / Large HUD), Center Box, Keyboard, or any corner.

            4. SOLLUXE™ 2.0 13-STAGE VOICE STUDIO & 3D GAME SOUNDSTAGE (Card 4)
            • Automatic 12-Mode Hardware Matrix (4 Gear Modes × 3 Gender Profiles):
              - Wearing Bluetooth Earphones (Auto Mode): Your earphones stay 100% in crisp HD Stereo (A2DP) for footsteps while your voice is captured from the phone's 48 kHz studio mic.
              - Plugging in a Wired 3.5mm or USB-C Mic: Automatically switches from Far-Field Hand-Grip tuning to Close-Talk Broadcast tuning!
            • Male (50Hz HPF) / Neutral (65Hz HPF) / Female (85Hz HPF):
              - Stage 1–4: 64-Bit HPF + Spark/Grip Blanker + Native C 48kHz Xiph RNNoise + 0-ms Breath Gate.
              - Stage 5 (Dynamic Hard-Breath Plosive Guard): Automatically ducks <115 Hz wind blasts when you exhale or blow air out hard, restoring 100% of your baritone chest warmth in 0 ms when you speak.
              - Stage 6–9 (Clean, Intimate & Sexy Vocal Polish): Tight Cardioid Proximity Chest Contour (95/130/175 Hz) + Pure Analog Cubic Sub-Warmth + Deeper -3.5 dB Boxy Mud Scoop (315–365 Hz) + 920 Hz Sweet Velvet Anti-Nasal Dip + Upper-Harmonic "Aphex Silk Air" Exciter (restores 5–10 kHz studio sparkle even during 16 kHz in-game match locks!).
              - Stage 10–13: AI Tone Balancer + High-Pass Sidechain 3:1 Leveler & Slow AGC + Post-Comp Split-Band De-Esser + Post-DSP Mic Boost (50%–250%).
            • 3D Game Soundstage: Cinema 3D Wide, Theater Sub-Punch (62 Hz grenade thump that ducks to 0 dB when you speak), Footstep Focus (250 Hz + 2,450 Hz + Sonar Lift), and Blast Tamer.

            5. FLOATING IN-GAME PILL & LIVE RESOURCE SPLIT (Card 5)
            • Double-tap or hold the compact in-game pill to expand 3 tabs (🎙️ Audio, 🎬 Video, 📡 Bitrate/Net).
            • Tab 3 (📡 Bitrate/Net) shows live App vs. Game Heat/CPU Split and App vs. Game RAM Split so you can see exactly how little power SolStream uses compared to your game!

            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            PART 2 — OFFICIAL PRIVACY POLICY & GOOGLE PLAY DATA SAFETY
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            • Zero Data Collection & Zero Server Storage: SolStream Studio processes all screen video and microphone audio locally in real time on your device and transmits the encoded stream solely to the RTMP/RTMPS ingest URL you configure. SolStream Studio has no developer servers, no user accounts, no advertising SDKs, and no analytics trackers.
            • Hardware Keystore Encryption: Your RTMP/RTMPS stream keys are encrypted locally on your device using the Android Hardware Keystore (AES-256-GCM / StrongBox / TEE) and are automatically redacted ([REDACTED_KEY]) from diagnostic logs.
            • AccessibilityService Disclosure & Strict Privacy Limits: SolStream Studio optionally uses the Android AccessibilityService API solely to:
              1) Display the assistive in-game floating broadcast dock (TYPE_ACCESSIBILITY_OVERLAY) for one-tap mic mute, privacy blur, scenes, and clip markers over full-screen games, and
              2) Maintain continuous microphone audio routing alongside internal game audio when multiplayer games activate squad voice chat.
              SolStream Studio sets android:canRetrieveWindowContent="false", android:canPerformGestures="false", and android:isAccessibilityTool="false". It CANNOT read screen text, passwords, messages, or personal data. You may decline or disable Accessibility at any time and stream using standard foreground microphone capture.

            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            PART 3 — OPEN-SOURCE CREDITS & LICENSES
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            1. RootEncoder (com.github.pedroSG94.RootEncoder)
               • Copyright (c) 2024 Pedro Sánchez (pedroSG94)
               • Licensed under the Apache License, Version 2.0.
            2. Xiph RNNoise (xiph/rnnoise v0.1.1 — 48 kHz Recurrent Neural Denoiser)
               • Copyright (c) 2017–2024 Xiph.Org Foundation and Jean-Marc Valin
               • Licensed under the BSD 3-Clause License.
            3. AndroidX Core, AppCompat, Material Components & Kotlin Coroutines
               • Copyright (c) Google LLC & JetBrains s.r.o.
               • Licensed under the Apache License, Version 2.0.
        """.trimIndent()

        val tv = TextView(this).apply {
            text = guide
            setTextColor(Color.parseColor("#ECEFF1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#050914"))
            addView(tv)
        }
        AlertDialog.Builder(this)
            .setTitle("📖 SolStream v$appVersionName Guide, Privacy & Licenses")
            .setView(scroll)
            .setPositiveButton("Got It", null)
            .show()
    }

    // === VAULT & KEY VISIBILITY ACTIONS (WITH SMART CLIPBOARD PASTE GUARD) ===
    private fun saveVaultInputs(showToast: Boolean): Boolean {
        val rawUrl = etRtmpsUrl.text?.toString() ?: KeystoreVault.DEFAULT_YOUTUBE_RTMPS
        val normalizedUrl = KeystoreVault.normalizeIngestUrl(rawUrl)
        val cleanKey = KeystoreVault.sanitizeStreamKeyInput(etStreamKey.text?.toString() ?: "")
        if (etRtmpsUrl.text?.toString() != normalizedUrl) {
            etRtmpsUrl.setText(normalizedUrl)
        }

        val okPrimary = KeystoreVault.saveEndpointAndKey(this, normalizedUrl, cleanKey)
        val okShorts = KeystoreVault.saveShortsStreamKey(this, etShortsKey.text?.toString() ?: "")
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

        val bannerTxt = etBannerText.text?.toString()?.trim()?.take(80) ?: ""
        if (bannerTxt != settings.liveBannerText) {
            settings = settings.copy(liveBannerText = bannerTxt)
            KeystoreVault.saveSettings(this, settings)
            SolSceneAssetStore.bumpRevision()
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
            val msg = if (cleanKey.isEmpty()) {
                "Ingest URL & auxiliary slots saved (Primary Key box is empty)."
            } else {
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

    /**
     * Smart Clipboard Paste Guard (#17):
     * Strips invisible trailing spaces/newlines, and if the user accidentally copied an rtmp:// or
     * rtmps:// Server URL and tapped "Paste Key", routes it into the Ingest Server URL box automatically!
     */
    private fun pasteStreamKeyFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val rawClip = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
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
    /**
     * Mandatory Google Play Prominent Disclosure Dialog before launching Settings.ACTION_ACCESSIBILITY_SETTINGS.
     * Ensures human reviewers see the clear Accept / Decline disclosure whether they tap Card 1 or GO LIVE.
     */
    private fun showPlayStoreCompliantAccessibilityDisclosure(allowSkipToStream: Boolean) {
        if (isAccessibilityServiceEnabled()) {
            openAccessibilitySettingsDirect()
            return
        }
        val msg = "SolStream Studio optionally uses the Android AccessibilityService API to:\n\n" +
            "1. Display an assistive floating in-game broadcast control dock (TYPE_ACCESSIBILITY_OVERLAY) for one-tap microphone mute, privacy blur shield, scene switching, and clip markers over full-screen games.\n" +
            "2. Maintain continuous microphone audio routing alongside internal game audio when multiplayer games activate in-game squad voice chat.\n\n" +
            "Privacy & Security Guarantee:\n" +
            "• SolStream Studio CANNOT read your screen text, passwords, messages, or personal data (canRetrieveWindowContent is strictly false and canPerformGestures is false).\n" +
            "• Zero audio, video, or personal data is ever collected, stored, or shared with developer servers; all streams are sent directly to your own configured RTMP/RTMPS server.\n\n" +
            "Sideloaded APK Note (Android 13–18): If SolStream Studio is greyed out in Accessibility, tap it once -> tap 'Step 4: App Info (⋮)' -> tap ⋮ at top-right -> 'Allow restricted settings'."

        val builder = AlertDialog.Builder(this)
            .setTitle("Assistive Broadcast Dock & Voice Routing Disclosure")
            .setMessage(msg)
            .setPositiveButton("Accept & Open Settings") { _, _ ->
                openAccessibilitySettingsDirect()
            }
            .setNeutralButton("Step 4: App Info (⋮)") { _, _ ->
                openAppInfoForRestrictedSettings()
            }

        if (allowSkipToStream) {
            builder.setNegativeButton("No Thanks (Stream Without Overlay)") { _, _ ->
                requestScreenCaptureAndGoLive()
            }
        } else {
            builder.setNegativeButton("No Thanks (Use Standard Mic)", null)
        }
        builder.show()
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
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openAppInfoForRestrictedSettings() {
        try {
            Toast.makeText(
                this,
                "Tap ⋮ at top-right (or scroll to the bottom of App Info on Xiaomi/POCO/Vivo) -> tap 'Allow restricted settings'!",
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
            val now = SystemClock.uptimeMillis()
            if (now - lastDashboardStopTapMs < 2500L) {
                lastDashboardStopTapMs = 0L
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
                uiHandler.postDelayed({
                    if (!isFinishing && !isDestroyed && SolStreamService.isBroadcastLive) {
                        btnGoLive.text = "⏹ STOP LIVE STREAM"
                    }
                }, 2500L)
            }
            return
        }

        if (!saveVaultInputs(showToast = false)) {
            return
        }
        if (KeystoreVault.buildActivePrimaryEndpoint(this, settings).isNullOrBlank()) {
            Toast.makeText(this, "Please paste or type your Stream Key first!", Toast.LENGTH_LONG).show()
            return
        }
        if (settings.dualFormatCompanionEnabled && !KeystoreVault.hasDistinctCompanionShortsKey(this)) {
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

    /**
     * Reserves isBroadcastStartingOrLive = true immediately and waits up to 5.0 seconds
     * (5000 ms) if an initial startup hardware scan is still running on a slow phone (0.05s if already done).
     */
    private fun requestScreenCaptureAndGoLive() {
        CodecCapabilityProbe.isBroadcastStartingOrLive = true
        if (CodecCapabilityProbe.isHardwareProbeCurrentlyRunning()) {
            Toast.makeText(
                this,
                "Finishing startup hardware encoder check—launching stream...",
                Toast.LENGTH_SHORT
            ).show()
            Thread({
                var waitedMs = 0
                while (CodecCapabilityProbe.isHardwareProbeCurrentlyRunning() && waitedMs < 5000) {
                    try { Thread.sleep(50) } catch (_: InterruptedException) { break }
                    waitedMs += 50
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && !SolStreamService.isBroadcastLive) {
                        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(mpm.createScreenCaptureIntent())
                    } else {
                        CodecCapabilityProbe.isBroadcastStartingOrLive = false
                    }
                }
            }, "SolWaitProbeBeforeLive").start()
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
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
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, SolStreamService::class.java).apply {
                        action = SolStreamService.ACTION_START_GLIP_BENCH
                    }
                )
                refreshAllUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // === 5-METRIC PRO NETWORK ANALYZER (DOWNLOAD + UPLOAD + TCP HANDSHAKE RTT + JITTER + BUFFERBLOAT) ===
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
            sock.tcpNoDelay = true
            val t0 = System.nanoTime()
            sock.connect(addr, timeoutMs)
            (System.nanoTime() - t0) / 1_000_000L
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    private fun runRtmpsNetworkCheck() {
        if (SolStreamService.isBroadcastLive) {
            Toast.makeText(this, "Speed test is locked while streaming live so packets never compete with your stream!", Toast.LENGTH_LONG).show()
            return
        }
        if (!netTestBusy.compareAndSet(false, true)) {
            Toast.makeText(this, "Network analyzer already running...", Toast.LENGTH_SHORT).show()
            return
        }
        saveVaultInputs(showToast = false)
        val (isTls, host, port) = parseIngestHostPort(etRtmpsUrl.text?.toString() ?: KeystoreVault.DEFAULT_YOUTUBE_RTMPS)
        val activePlat = settings.platformPreset.uppercase(Locale.US)
        btnNetTest.isEnabled = false
        tvNetTestResult.text = "Step 1/4: Verifying DNS & ${if (isTls) "TLS" else "TCP"} handshake to $host:$port..."

        Thread({
            try {
                val dnsStartNs = System.nanoTime()
                val resolvedIp = InetAddress.getByName(host)
                val dnsMs = (System.nanoTime() - dnsStartNs) / 1_000_000L
                val targetAddr = InetSocketAddress(resolvedIp, port)

                var tlsInfo = "Plain RTMP"
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
                        tlsInfo = "${createdSsl.session.protocol} (${hsMs}ms)"
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
                        tvNetTestResult.text = "Step 2/4: Ingest TCP Handshake RTT ${idleAvgMs}ms (Jitter ${idleJitterMs}ms) — measuring Download Mbps..."
                    }
                }

                // Step 2: Measure Cloudflare Download Speed (2.5 MB burst)
                var downloadMbps = -1.0
                var downConn: HttpsURLConnection? = null
                try {
                    val downBytesTarget = 2_500_000
                    downConn = (URL("https://speed.cloudflare.com/__down?bytes=$downBytesTarget").openConnection() as HttpsURLConnection).apply {
                        requestMethod = "GET"
                        useCaches = false
                        connectTimeout = 4000
                        readTimeout = 7000
                    }
                    val downStartNs = System.nanoTime()
                    var totalRead = 0L
                    val buf = ByteArray(32 * 1024)
                    downConn.inputStream.use { ins ->
                        while (true) {
                            val r = ins.read(buf)
                            if (r <= 0) break
                            totalRead += r
                        }
                    }
                    val downSec = max(0.05, (System.nanoTime() - downStartNs) / 1_000_000_000.0)
                    if (totalRead > 100_000L) {
                        downloadMbps = (totalRead * 8.0) / (downSec * 1_000_000.0)
                    }
                } catch (_: Exception) {
                } finally {
                    try { downConn?.disconnect() } catch (_: Exception) {}
                }

                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        val dlTag = if (downloadMbps > 0) String.format(Locale.US, "%.1f Mbps", downloadMbps) else "N/A"
                        tvNetTestResult.text = "Step 3/4: Download $dlTag — testing Sustained Upload Mbps & Router Bufferbloat..."
                    }
                }

                // Step 3: Measure Sustained Upload Speed + Loaded TCP Handshake Bufferbloat
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
                val bloatGrade = when {
                    bloatDeltaMs < 0L -> "N/A"
                    bloatDeltaMs <= 15L -> "+${bloatDeltaMs}ms (Grade A+ Zero Bufferbloat)"
                    bloatDeltaMs <= 45L -> "+${bloatDeltaMs}ms (Grade B Mild)"
                    else -> "+${bloatDeltaMs}ms (⚠️ Router Bufferbloat — Keep ABR ON)"
                }

                // Step 4: Platform-Aware Safe Bitrate Recommendation (60% safe upload rule + Twitch/Kick caps)
                val rawSafeMbps = if (uploadMbps > 0.0) (uploadMbps * 0.60).roundToInt().coerceIn(1, 50) else 16
                val platformCappedMbps = when {
                    activePlat == "TWITCH" || (settings.simulcastTwitchEnabled && !settings.simulcastSplitEncode) -> min(rawSafeMbps, 6)
                    activePlat == "KICK" || (settings.simulcastKickEnabled && !settings.simulcastSplitEncode) -> min(rawSafeMbps, 8)
                    else -> min(rawSafeMbps, 24)
                }

                val advice = when {
                    activePlat == "TWITCH" ->
                        "Twitch Active: Safe up to $platformCappedMbps Mbps (1080p60 H.264 • 6 Mbps Twitch Cap)."
                    activePlat == "KICK" ->
                        "Kick Active: Safe up to $platformCappedMbps Mbps (1080p60 H.264 • 8 Mbps Kick Cap)."
                    uploadMbps > 0.0 && uploadMbps < 6.0 ->
                        "Low Upload: Use 720p/480p HEVC @ ${platformCappedMbps} Mbps + Ping Guard ABR ON."
                    uploadMbps >= 24.0 && bloatDeltaMs in 0..35 ->
                        "Grade A+ Fiber! Safe for 18–22 Mbps 1440p60 HEVC (Single) or 16M + 8M (Dual Companion)."
                    else ->
                        "Recommended Safe Stream Bitrate: Up to $platformCappedMbps Mbps with Ping Guard ABR ON."
                }

                val summary = buildString {
                    val dlStr = if (downloadMbps > 0.0) String.format(Locale.US, "%.1f Mbps", downloadMbps) else "N/A"
                    val ulStr = if (uploadMbps > 0.0) String.format(Locale.US, "%.1f Mbps", uploadMbps) else "N/A"
                    append("Download: $dlStr | Upload: $ulStr | TCP Handshake RTT: ${idleAvgMs}ms (Jitter ${idleJitterMs}ms)\n")
                    append("TLS: $tlsInfo (DNS ${dnsMs}ms) | Bufferbloat: $bloatGrade\n")
                    append("-> $advice")
                }

                DiagnosticsLogger.log("NET_CHECK", summary.replace('\n', ' '))
                runOnUiThread {
                    netTestBusy.set(false)
                    if (!isFinishing && !isDestroyed) {
                        btnNetTest.isEnabled = !SolStreamService.isBroadcastLive
                        tvNetTestResult.text = summary
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    netTestBusy.set(false)
                    if (!isFinishing && !isDestroyed) {
                        btnNetTest.isEnabled = !SolStreamService.isBroadcastLive
                        tvNetTestResult.text =
                            "Ingest check failed (${e.javaClass.simpleName}: ${e.message ?: "check internet"}). Verify URL & 5GHz Wi-Fi."
                    }
                }
            }
        }, "SolNetProbe").start()
    }

    // === DEDICATED PLAIN-ENGLISH SAVED CLIPS & AI HIGHLIGHTS VIEWER ===
    private fun showSavedClipsDialog() {
        val summary = DiagnosticsLogger.buildSavedClipsPlainEnglishSummary(this)
        val clipCount = DiagnosticsLogger.getClipHighlightCount()

        val tv = TextView(this).apply {
            text = summary
            setTextColor(Color.parseColor("#ECEFF1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#050914"))
            addView(tv)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("★ Saved Stream Clips & AI Highlights ($clipCount)")
            .setView(scroll)
            .setPositiveButton("Copy Clip Times") { _, _ ->
                copyPlainTextToClipboard("SolStream Saved Clips", summary)
                Toast.makeText(this, "★ Saved Clip Timestamps copied to clipboard!", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Close", null)

        if (clipCount > 0) {
            builder.setNeutralButton("Clear Clips") { _, _ ->
                DiagnosticsLogger.clearSavedClips(this)
                refreshAllUi()
                Toast.makeText(this, "Saved clip list cleared.", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    // === MULTI-DAY SESSION REPORT VIEWER & ARCHIVE MANAGER ===
    private fun showDiagnosticsDialog(showingArchived: Boolean = false) {
        val archiveList = SolStreamService.loadLocalReportArchive(this)
        val archivedCount = archiveList.size
        val reportText = if (showingArchived) {
            if (archiveList.isEmpty()) {
                "No past session reports archived yet. Complete a live stream or benchmark session first."
            } else {
                archiveList.mapIndexed { idx, (hdr, body) ->
                    "=== SAVED SESSION #${idx + 1}: $hdr ===\n$body"
                }.joinToString("\n\n--------------------------------------------\n\n")
            }
        } else {
            DiagnosticsLogger.buildShareableReport(this)
        }
        val dialogTitle = if (showingArchived) {
            "Archived Past Reports ($archivedCount Saved)"
        } else {
            "Current Session Report (History: $archivedCount)"
        }

        val tv = TextView(this).apply {
            text = reportText
            setTextColor(Color.parseColor("#ECEFF1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#050914"))
            addView(tv)
        }
        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(scroll)
            .setPositiveButton("Copy Report") { _, _ ->
                copyPlainTextToClipboard("SolStream Report", reportText)
                Toast.makeText(this, "Report copied! Safe to paste anywhere.", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton(
                if (showingArchived) "Current Report" else "★ View Saved Clips (${DiagnosticsLogger.getClipHighlightCount()})"
            ) { _, _ ->
                if (showingArchived) {
                    showDiagnosticsDialog(showingArchived = false)
                } else {
                    showSavedClipsDialog()
                }
            }
            .setNegativeButton(
                if (!showingArchived && archivedCount > 0) "Past History ($archivedCount)"
                else if (showingArchived && archivedCount > 0) "Delete History"
                else "Close"
            ) { _, _ ->
                if (!showingArchived && archivedCount > 0) {
                    showDiagnosticsDialog(showingArchived = true)
                } else if (showingArchived && archivedCount > 0) {
                    SolStreamService.clearLocalReportArchive(this)
                    Toast.makeText(this, "Archived session history deleted.", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /**
     * Numerical Semantic Version Parser ([1, 3, 0] vs [1, 2, 1])
     * so future releases never fail alphabetical string comparison.
     */
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
                                "No newer GitHub Release found (running v$appVersionName).",
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

    /**
     * All-Brand Phone Setup, Earphones & Cool-Stream Guide (#14):
     * Auto-detects Build.MANUFACTURER so the user's own phone brand appears right at the top,
     * including iQOO/Vivo Q1/Q2 Frame Interpolation tips and OEM background power settings!
     */
    private fun showAllBrandSetupGuide() {
        val mfr = Build.MANUFACTURER.lowercase(Locale.US)
        val detectedBrandTip = when {
            mfr.contains("samsung") ->
                "★ DETECTED PHONE: SAMSUNG ONE UI (${Build.MODEL})\n" +
                    "• Game Booster: Open Gaming Hub -> Game Booster -> set Performance to 'Standard/Balanced' (or enable 'Pause USB PD charging when gaming' with a 25W+ PPS charger for 4°C cooler sessions).\n" +
                    "• Optional Audio Isolation: Settings -> Sounds and vibration -> Separate app sound -> route your game to Bluetooth."
            mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") ->
                "★ DETECTED PHONE: XIAOMI / REDMI / POCO (HyperOS / MIUI)\n" +
                    "• Unlocking Restricted Settings: Open App Info -> scroll to the VERY BOTTOM of the screen -> tap 'Allow restricted settings'.\n" +
                    "• Background Lock: Set Battery Saver to 'No restrictions', enable Autostart, and lock SolStream Studio in Recents."
            mfr.contains("vivo") || mfr.contains("iqoo") ->
                "★ DETECTED PHONE: VIVO / iQOO (FuntouchOS / OriginOS)\n" +
                    "• Q1/Q2 Supercomputing Chip Tip: In Ultra Game Mode, keep 'Game Frame Interpolation' OFF (use Native 90/120 FPS) while streaming so SurfaceFlinger never drops capture cadence to 45 FPS.\n" +
                    "• Background Lock: Enable 'High Background Power Consumption' for SolStream Studio."
            mfr.contains("oneplus") || mfr.contains("realme") || mfr.contains("oppo") ->
                "★ DETECTED PHONE: ONEPLUS / REALME / OPPO (OxygenOS / ColorOS / Realme UI)\n" +
                    "• Battery Lock: App Info -> Battery Usage -> turn ON 'Allow background activity'.\n" +
                    "• HyperBoost Game Engine: Keep Championship/Pro Gamer mode in Balanced thermal mode for 3+ hour streams."
            else ->
                "★ DETECTED PHONE: ${Build.MANUFACTURER.uppercase(Locale.US)} ${Build.MODEL}\n" +
                    "• Keep Battery set to 'Unrestricted' in Step 2 so Android never pauses your background broadcast."
        }

        val msg = """
            $detectedBrandTip

            1. ENABLE ACCESSIBILITY DOCK (Play Store & Sideloaded APK):
            • If installed from Google Play Store: Tap 'Step 3: Accessibility' and switch SolStream Studio ON in 1 tap!
            • If sideloaded via GitHub APK on Android 13–18:
              a) Tap 'Step 3: Accessibility' -> Installed apps -> tap greyed-out SolStream Studio once & press OK.
              b) Tap 'Step 4: App Info (⋮)' -> tap ⋮ at top-right (or scroll to bottom of App Info on Xiaomi/POCO/Vivo) -> 'Allow restricted settings', then switch SolStream Studio ON!

            2. BLUETOOTH NECKBAND / AIRPODS / WIRED & USB-C MICS (12-MODE MATRIX):
            • In 'Auto (Phone Studio Mic Locked)' mode, SolStream keeps your Bluetooth earphones in 100% Stereo Game Mode (A2DP) while capturing your voice from the phone's 48 kHz studio microphone.
            • Plugging in a Wired 3.5mm Headset or USB-C Mic automatically switches SolLuxe™ 2.0 from Far-Field Hand-Grip tuning to Close-Talk Broadcast tuning!

            3. STRETCH (16:9 FULL) & PRIVACY KEYBOARD BLUR:
            • Select 'STRETCH (16:9)' in Card 3 to fill 100% of 1080p/1440p/4K screens with zero black bars and 100% HUD visibility.
            • Keep 'Auto-Blur Keyboard: ON' so whenever you open your keyboard to type a password or message, viewers only see a smooth frosted blur!
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("All-Brand Phone, Earphones & Cool-Stream Guide")
            .setMessage(msg)
            .setPositiveButton("Step 3: Accessibility") { _, _ ->
                showPlayStoreCompliantAccessibilityDisclosure(allowSkipToStream = false)
            }
            .setNeutralButton("Step 4: App Info (⋮)") { _, _ ->
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
                return
            }
            // If standard Android battery exemption is already granted, offer OEM background settings on Vivo/iQOO/Xiaomi/OnePlus or confirm ready
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
                    Toast.makeText(this, "Battery is Unrestricted! Opened OEM Background Autostart guard.", Toast.LENGTH_LONG).show()
                    return
                }
            }
            Toast.makeText(this, "Battery is already Unrestricted!", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (SolStreamService.isAccessibilityConnected(this)) return true
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains("$packageName/${SolStreamService::class.java.name}") ||
            enabled.contains("$packageName/.SolStreamService")
    }

    // === SOLAPEX™ UI CARD & BUTTON HELPERS ===
    private fun sectionCard(
        heading: String,
        engineBadge: String = "",
        badgeHex: String = "#00E5FF"
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0D1628"), Color.parseColor("#09101E"))
            ).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#1E3454"))
            }
            setPadding(dp(12), dp(11), dp(12), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(11) }
            layoutParams = lp

            val hdrRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(6))
            }
            val titleTv = TextView(this@MainActivity).apply {
                text = heading
                setTextColor(Color.parseColor("#00E5FF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.2f)
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
                    letterSpacing = 0.05f
                    setPadding(dp(7), dp(2), dp(7), dp(2))
                    background = roundedDrawable("#0A192F", badgeHex)
                }
                hdrRow.addView(badgeTv)
            }
            addView(hdrRow)
        }
    }

    private fun labelText(txt: String): TextView = TextView(this).apply {
        text = txt
        setTextColor(Color.parseColor("#94A3B8"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(6), 0, dp(2))
    }

    private fun bodyText(txt: String): TextView = TextView(this).apply {
        text = txt
        setTextColor(Color.parseColor("#CBD5E1"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setPadding(0, dp(3), 0, dp(5))
    }

    private fun styledEditText(initial: String, isPassword: Boolean): EditText = EditText(this).apply {
        setText(initial)
        setTextColor(Color.parseColor("#F8FAFC"))
        setHintTextColor(Color.parseColor("#64748B"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        isSingleLine = true
        background = cardDrawable("#060B16", "#294066")
        setPadding(dp(10), dp(8), dp(10), dp(8))
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
            else -> "#293B56"
        }
        background = roundedDrawable(bgHex, strokeHex)
        setPadding(dp(8), dp(5), dp(8), dp(5))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(5) }
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
            ).apply { topMargin = dp(5) }
            layoutParams = lp
        }
        buttons.forEachIndexed { idx, btn ->
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (idx > 0) marginStart = dp(5)
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
