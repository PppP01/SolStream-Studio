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
import kotlin.math.abs
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var appVersionName: String
    private lateinit var settings: StreamSettings
    private lateinit var scanSummary: DeviceScanSummary
    private var micOptions: List<MicInputOption> = emptyList()
    private var showStreamKeyPlain = false
    private var userModifiedSettingsBeforeScan = false
    private var pendingAssetPickSlot: SolSceneAssetStore.AssetSlot = SolSceneAssetStore.AssetSlot.BRB_SCENE
    private var benchmarkTargetAppName: String = "Glip / Other App"

    private val micTestBusy = AtomicBoolean(false)
    private val netTestBusy = AtomicBoolean(false)
    private val hwScanBusy = AtomicBoolean(false)

    // Header & Tier Bar References
    private lateinit var tvHeaderSubtitle: TextView
    private lateinit var tvLiveBanner: TextView
    private lateinit var btnReapproveCapture: Button
    private val tierButtons = mutableMapOf<DeviceTier, Button>()
    private lateinit var tvTierDescription: TextView

    // Card 1: Pre-Flight
    private lateinit var tvPreflightStatus: TextView
    private lateinit var tvRestrictedHint: TextView

    // Card 2: Multi-Platform Vault & Simulcast
    private lateinit var btnPlatformPreset: Button
    private lateinit var etRtmpsUrl: EditText
    private lateinit var tvVaultSecurityLabel: TextView
    private lateinit var etStreamKey: EditText
    private lateinit var btnShowHideKey: Button
    private lateinit var btnToggleSimulcastDrawer: Button
    private lateinit var boxSimulcastDrawer: LinearLayout
    private lateinit var etShortsKey: EditText
    private lateinit var btnUseShortsSlot: Button
    private lateinit var btnSimulcastTwitch: Button
    private lateinit var etTwitchKey: EditText
    private lateinit var btnSimulcastKick: Button
    private lateinit var etKickKey: EditText
    private lateinit var btnDualCompanion: Button
    private lateinit var btnNetTest: Button
    private lateinit var tvNetTestResult: TextView

    // Card 3: Video Engine, Stackable Visuals & Scenes
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
    private lateinit var btnPickShortsPoster: Button
    private lateinit var etBannerText: EditText
    private lateinit var btnBannerToggle: Button
    private lateinit var btnBannerPos: Button
    private lateinit var btnShortsBackdrop: Button
    private lateinit var btnMinimapShield: Button
    private lateinit var btnGlEngine: Button
    private lateinit var btnHdr10: Button
    private lateinit var btnRoiMode: Button
    private lateinit var btnKeyframeAndBFrames: Button
    private lateinit var btnQpAndLatency: Button

    // Card 4: 15-Stage SolLuxe Audio, 250% Game Soundstage & 10s Calibrator
    private lateinit var btnMicDevice: Button
    private lateinit var btnMicSource: Button
    private lateinit var btnAudioBitrate: Button
    private lateinit var btnGenderNeutral: Button
    private lateinit var btnGenderMale: Button
    private lateinit var btnGenderFemale: Button
    private lateinit var btnVoicePreset: Button
    private lateinit var btnNoiseSuppressMode: Button
    private lateinit var btnNoiseSuppressLevel: Button
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
    private lateinit var btnApplyAdvisor: Button

    // Card 5: Launch, Reports, Benchmark & Support
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
        // Guards against overwriting any settings the user manually changed before the scan finished!
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
                    "SolApex™ HyperStream Engine • ${scanSummary.deviceModel} (${scanSummary.socModel}) • Android ${scanSummary.androidRelease}\n${scanSummary.toBadgeText()}"
                refreshAllUi()
            }
        }
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
            info.versionName?.removeSuffix("-debug") ?: "1.1.2"
        } catch (_: Exception) {
            "1.1.2"
        }
    }

    private fun buildDashboardUi(): View {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#070B12"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(28))
        }

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                max(dp(14), bars.left + dp(10)),
                max(dp(14), bars.top + dp(10)),
                max(dp(14), bars.right + dp(10)),
                max(dp(24), bars.bottom + dp(16))
            )
            insets
        }

        // === ULTRA STUDIO HEADER ===
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "SOLSTREAM STUDIO • ULTRA"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val verBadge = TextView(this).apply {
            text = "v$appVersionName • SolApex™"
            setTextColor(Color.parseColor("#FFC400"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedDrawable("#162234", "#FFC400")
        }
        titleRow.addView(title)
        titleRow.addView(verBadge)

        tvHeaderSubtitle = TextView(this).apply {
            text = "SolApex™ HyperStream Engine • ${scanSummary.deviceModel} (${scanSummary.socModel}) • Android ${scanSummary.androidRelease}\n${scanSummary.toBadgeText()}"
            setTextColor(Color.parseColor("#90A4AE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(4), 0, dp(8))
        }

        val topHelperRow = buttonRow(
            actionButton("? Plain-English Guide & Glossary", "#006064") { showPlainEnglishGlossaryDialog() },
            actionButton("Copy Preset", "#1E293B") { copyPresetToClipboard() },
            actionButton("Import Preset", "#1E293B") { importPresetFromClipboard() }
        )

        tvLiveBanner = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(9), dp(12), dp(9))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            layoutParams = lp
            background = cardDrawable("#0D1E26", "#00E676")
        }

        btnReapproveCapture = actionButton("RE-APPROVE SCREEN CAPTURE TO RESUME VIDEO", "#E65100") {
            if (SolStreamService.isBroadcastLive) {
                SolStreamService.instance?.stopLiveBroadcast("Re-approving screen capture")
            }
            requestScreenCaptureAndGoLive()
        }.apply { visibility = View.GONE }

        container.addView(titleRow)
        container.addView(tvHeaderSubtitle)
        container.addView(topHelperRow)
        container.addView(tvLiveBanner)
        container.addView(btnReapproveCapture)

        // === 5-TIER SEGMENTED BAR (Lite | Balanced | High | Flagship | Ultra) ===
        val cardTier = sectionCard("0. Smart Device Tier Presets (100% Manual Freedom)")
        val tierRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
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
                        "${tier.title} Tier applied! Every button below remains 100% customizable.",
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

        // === CARD 1: PRE-FLIGHT PERMISSIONS & EXACT 1-2-3-4 RESTRICTED UNLOCK ===
        val cardPreflight = sectionCard("1. Pre-Flight Readiness & Direct Settings Setup")
        tvPreflightStatus = bodyText("")
        cardPreflight.addView(tvPreflightStatus)

        tvRestrictedHint = TextView(this).apply {
            text = "Android 13–17 Tip (Why ⋮ 3-dots hide at first): Tap '3. Tap Accessibility 1st' -> tap greyed-out SolStream Studio once & hit OK -> then tap '4. Unlock Restricted (⋮)' so the top-right ⋮ menu appears!"
            setTextColor(Color.parseColor("#FFD54F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, dp(6))
        }
        cardPreflight.addView(tvRestrictedHint)

        cardPreflight.addView(
            buttonRow(
                actionButton("1. Grant Mic & Notif", "#1E3A5F") { requestRuntimePermissions() },
                actionButton("2. Unrestricted Batt", "#1E3A5F") { openBatteryUnrestricted() }
            )
        )
        cardPreflight.addView(
            buttonRow(
                actionButton("3. Tap Accessibility 1st", "#00695C") { openAccessibilitySettingsDirect() },
                actionButton("4. Unlock Restricted (⋮)", "#283593") { openAppInfoForRestrictedSettings() }
            )
        )
        cardPreflight.addView(
            actionButton("Samsung One UI, AirPods/Neckband & BR Cooling Guide", "#37474F") {
                showSamsungSetupGuide()
            }
        )
        container.addView(cardPreflight)

        // === CARD 2: SOLRELAY OMEGA ENCRYPTED STREAM VAULT, SHORTS KEY & SIMULCAST ===
        val cardStream = sectionCard("2. SolRelay Omega™ Vault, 9:16 Shorts Key & Simulcast")
        btnPlatformPreset = actionButton("", "#1E3A5F") { cyclePlatformPreset() }
        cardStream.addView(btnPlatformPreset)

        cardStream.addView(labelText("Primary RTMPS Ingest Server URL (works with or without :443):"))
        etRtmpsUrl = styledEditText(KeystoreVault.getRtmpsUrl(this), isPassword = false)
        cardStream.addView(etRtmpsUrl)

        tvVaultSecurityLabel = labelText("Primary Stream Key (AES-256-GCM • ${KeystoreVault.getKeySecurityDescription()}):")
        cardStream.addView(tvVaultSecurityLabel)
        etStreamKey = styledEditText(KeystoreVault.getStreamKey(this), isPassword = true).apply {
            hint = "Paste your YouTube / Twitch / Kick Stream Key"
        }
        cardStream.addView(etStreamKey)

        btnShowHideKey = actionButton("Show Key", "#263238") { toggleKeyVisibility() }
        cardStream.addView(
            buttonRow(
                actionButton("Paste Key", "#263238") { pasteStreamKeyFromClipboard() },
                btnShowHideKey,
                actionButton("Clear Key", "#455A64") { clearStreamKeyInput() },
                actionButton("Save Vault", "#00695C") { saveVaultInputs(showToast = true) }
            )
        )

        cardStream.addView(
            buttonRow(
                actionButton("Get Key on Phone (YT Studio)", "#1565C0") { openYouTubeStudioInBrowser() },
                actionButton("Open YouTube App", "#37474F") { openYouTubeAppQuick() }
            )
        )

        // Collapsible Drawer: 9:16 Shorts Key Slot + Twitch/Kick Simulcast + Dual-Format Companion
        boxSimulcastDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = cardDrawable("#0B111C", "#1E3A5F")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            layoutParams = lp
        }
        btnToggleSimulcastDrawer = actionButton(
            "▼ Expand: 9:16 Shorts Key Slot, Twitch/Kick Simulcast & Dual-Format",
            "#162234"
        ) {
            val show = boxSimulcastDrawer.visibility != View.VISIBLE
            boxSimulcastDrawer.visibility = if (show) View.VISIBLE else View.GONE
            btnToggleSimulcastDrawer.text = if (show) {
                "▲ Hide: 9:16 Shorts Key Slot, Twitch/Kick Simulcast & Dual-Format"
            } else {
                "▼ Expand: 9:16 Shorts Key Slot, Twitch/Kick Simulcast & Dual-Format"
            }
        }

        boxSimulcastDrawer.addView(
            labelText("Slot #2 — YouTube 9:16 Variable Shorts Stream Key (Leave 'Manual Resolution' OFF in YT Studio):")
        )
        etShortsKey = styledEditText(KeystoreVault.getShortsStreamKey(this), isPassword = true).apply {
            hint = "Optional separate Variable Stream Key for 9:16 Shorts Live Feed"
        }
        btnUseShortsSlot = actionButton("", "#1C2836") {
            saveVaultInputs(showToast = false)
            updateSetting(settings.copy(useShortsKeySlot = !settings.useShortsKeySlot), requiresRestartIfLive = true)
        }
        boxSimulcastDrawer.addView(etShortsKey)
        boxSimulcastDrawer.addView(btnUseShortsSlot)

        boxSimulcastDrawer.addView(
            labelText("Single-Encode Multi-Platform Simulcast (Encodes once -> sends to YT + Twitch + Kick):")
        )
        btnSimulcastTwitch = actionButton("", "#4A148C") {
            saveVaultInputs(showToast = false)
            updateSetting(
                settings.copy(simulcastTwitchEnabled = !settings.simulcastTwitchEnabled),
                requiresRestartIfLive = true
            )
        }
        etTwitchKey = styledEditText(KeystoreVault.getTwitchStreamKey(this), isPassword = true).apply {
            hint = "Optional Twitch Stream Key (live_...)"
        }
        btnSimulcastKick = actionButton("", "#1B5E20") {
            saveVaultInputs(showToast = false)
            updateSetting(
                settings.copy(simulcastKickEnabled = !settings.simulcastKickEnabled),
                requiresRestartIfLive = true
            )
        }
        etKickKey = styledEditText(KeystoreVault.getKickStreamKey(this), isPassword = true).apply {
            hint = "Optional Kick Stream Key (sk_...)"
        }
        btnDualCompanion = actionButton("", "#004D40") {
            saveVaultInputs(showToast = false)
            updateSetting(
                settings.copy(dualFormatCompanionEnabled = !settings.dualFormatCompanionEnabled),
                requiresRestartIfLive = true
            )
        }
        val btnSaveSimKeys = actionButton("Save Shorts & Simulcast Keys in Keystore", "#00695C") {
            saveVaultInputs(showToast = true)
        }

        boxSimulcastDrawer.addView(btnSimulcastTwitch)
        boxSimulcastDrawer.addView(etTwitchKey)
        boxSimulcastDrawer.addView(btnSimulcastKick)
        boxSimulcastDrawer.addView(etKickKey)
        boxSimulcastDrawer.addView(btnDualCompanion)
        boxSimulcastDrawer.addView(btnSaveSimKeys)

        cardStream.addView(btnToggleSimulcastDrawer)
        cardStream.addView(boxSimulcastDrawer)

        tvNetTestResult = bodyText(
            "Tip: Use a Variable Resolution Stream Key in YouTube Studio to go live on both 16:9 Landscape & 9:16 Shorts Feed without Google login!"
        )
        btnNetTest = actionButton("Test Internet RTMPS Ping & Upload", "#1565C0") {
            runRtmpsNetworkCheck()
        }
        cardStream.addView(
            buttonRow(
                btnNetTest,
                actionButton("Reset Default URL", "#37474F") {
                    etRtmpsUrl.setText(KeystoreVault.DEFAULT_YOUTUBE_RTMPS)
                    saveVaultInputs(showToast = true)
                }
            )
        )
        cardStream.addView(tvNetTestResult)
        container.addView(cardStream)

        // === CARD 3: SOLCORE ULTRA VIDEO ENGINE & SOLCODEC (360p-4K60, STRETCH, STACKABLE VISUALS) ===
        val cardVideo = sectionCard("3. SolCore Ultra™ Video Engine & SolCodec (360p–4K60 • 0.5–100 Mbps)")
        cardVideo.addView(
            bodyText("Plays at 120–185 Hz on screen while hardware silicon encodes 60/30 FPS. Use 'STRETCH' Framing for full 16:9 zero-bars with 100% HUD visible!")
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

        btnBitrateDown = actionButton("− Mbps", "#1B5E20") { stepBitrate(-1) }
        btnBitrateCycle = actionButton("", "#1B5E20") { stepBitrate(+1) }
        btnBitrateUp = actionButton("+ Mbps", "#1B5E20") { stepBitrate(+1) }

        btnRateControl = actionButton("", "#1C2836") {
            updateSetting(settings.copy(useVbr = !settings.useVbr), requiresRestartIfLive = true)
        }
        btnAbr = actionButton("", "#1C2836") {
            updateSetting(settings.copy(adaptiveBitrate = !settings.adaptiveBitrate))
        }
        btnUpscaler = actionButton("", "#283593") { cycleUpscaler() }
        btnFraming = actionButton("", "#283593") { cycleFraming() }
        btnSmartZoom = actionButton("", "#1C2836") { cycleSmartZoom() }

        cardVideo.addView(buttonRow(btnCodec, btnResolution))
        cardVideo.addView(buttonRow(btnFps, btnOrientationFlip))
        cardVideo.addView(buttonRow(btnBitrateDown, btnBitrateCycle, btnBitrateUp))
        cardVideo.addView(buttonRow(btnRateControl, btnAbr))
        cardVideo.addView(buttonRow(btnUpscaler, btnFraming, btnSmartZoom))

        cardVideo.addView(labelText("Stackable Live Visual Filters (Mix & Match Any Combo Live):"))
        btnFilmic = actionButton("", "#1C2836") {
            updateSetting(settings.copy(cinematicFilmic = !settings.cinematicFilmic))
        }
        btnAmoledVivid = actionButton("", "#1C2836") {
            updateSetting(settings.copy(amoledVivid = !settings.amoledVivid))
        }
        btnShadowBoost = actionButton("", "#1C2836") {
            updateSetting(settings.copy(shadowBoost = !settings.shadowBoost))
        }
        btnColorTemp = actionButton("", "#1C2836") { cycleColorTemp() }
        btnSharpen = actionButton("", "#1C2836") {
            updateSetting(
                newSettings = settings.copy(gpuSharpen = !settings.gpuSharpen),
                userToggledSharp = true
            )
        }

        cardVideo.addView(buttonRow(btnFilmic, btnAmoledVivid))
        cardVideo.addView(buttonRow(btnShadowBoost, btnColorTemp))
        cardVideo.addView(btnSharpen)

        // Collapsible Drawer: Scenes, .PNG Overlay, Text Banner, Minimap Shield & Pro Silicon
        boxSceneProDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = cardDrawable("#0B111C", "#1E3A5F")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            layoutParams = lp
        }
        btnToggleSceneProDrawer = actionButton(
            "▼ Expand: Custom Scenes, .PNG Overlay, Text Banner & SolCodec Silicon",
            "#162234"
        ) {
            val show = boxSceneProDrawer.visibility != View.VISIBLE
            boxSceneProDrawer.visibility = if (show) View.VISIBLE else View.GONE
            btnToggleSceneProDrawer.text = if (show) {
                "▲ Hide: Custom Scenes, .PNG Overlay, Text Banner & SolCodec Silicon"
            } else {
                "▼ Expand: Custom Scenes, .PNG Overlay, Text Banner & SolCodec Silicon"
            }
        }

        boxSceneProDrawer.addView(labelText("Zero-Permission Saved Graphics (Tap to Pick from Gallery):"))
        btnPickStartImg = actionButton("", "#1E3A5F") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.STARTING_SOON
            imageAssetPickerLauncher.launch("image/*")
        }
        btnPickBrbImg = actionButton("", "#1E3A5F") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.BRB_SCENE
            imageAssetPickerLauncher.launch("image/*")
        }
        btnPickOverlayPng = actionButton("", "#00695C") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.OVERLAY_PNG
            imageAssetPickerLauncher.launch("image/*")
        }
        btnPickShortsPoster = actionButton("", "#283593") {
            pendingAssetPickSlot = SolSceneAssetStore.AssetSlot.SHORTS_POSTER
            imageAssetPickerLauncher.launch("image/*")
        }
        val btnToggleOverlayOn = actionButton("Toggle .PNG Overlay ON/OFF", "#263238") {
            updateSetting(settings.copy(overlayImageEnabled = !settings.overlayImageEnabled))
        }
        val btnClearGraphics = actionButton("Clear Saved Graphics", "#455A64") {
            SolSceneAssetStore.AssetSlot.entries.forEach { SolSceneAssetStore.clearCustomAsset(this, it) }
            updateSetting(settings.copy(overlayImageEnabled = false))
            Toast.makeText(this, "Cleared custom scene/overlay images (reverted to built-in slates).", Toast.LENGTH_SHORT).show()
        }

        boxSceneProDrawer.addView(buttonRow(btnPickStartImg, btnPickBrbImg))
        boxSceneProDrawer.addView(buttonRow(btnPickOverlayPng, btnPickShortsPoster))
        boxSceneProDrawer.addView(buttonRow(btnToggleOverlayOn, btnClearGraphics))

        boxSceneProDrawer.addView(labelText("Built-In GPU Live Text Banner (0% CPU Redraw Cost):"))
        etBannerText = styledEditText(settings.liveBannerText, isPassword = false).apply {
            hint = "e.g. SUB GOAL 75 • @Sol_CODM • 120 FPS LIVE"
        }
        btnBannerToggle = actionButton("", "#00695C") {
            val txt = etBannerText.text?.toString()?.trim()?.take(80) ?: ""
            SolSceneAssetStore.bumpRevision()
            updateSetting(settings.copy(liveBannerText = txt, liveBannerEnabled = !settings.liveBannerEnabled))
        }
        btnBannerPos = actionButton("", "#263238") {
            val txt = etBannerText.text?.toString()?.trim()?.take(80) ?: ""
            val nextPos = if (settings.liveBannerPosition == "TOP") "BOTTOM" else "TOP"
            SolSceneAssetStore.bumpRevision()
            updateSetting(settings.copy(liveBannerText = txt, liveBannerPosition = nextPos))
        }
        boxSceneProDrawer.addView(etBannerText)
        boxSceneProDrawer.addView(buttonRow(btnBannerToggle, btnBannerPos))

        btnShortsBackdrop = actionButton("", "#1C2836") { cycleShortsBackdrop() }
        btnMinimapShield = actionButton("", "#1C2836") { cycleMinimapShield() }
        boxSceneProDrawer.addView(btnShortsBackdrop)
        boxSceneProDrawer.addView(btnMinimapShield)

        boxSceneProDrawer.addView(labelText("SolCodec™ Silicon & OpenGL Engine Controls:"))
        btnGlEngine = actionButton("", "#1C2836") { cycleGlEngine() }
        btnHdr10 = actionButton("", "#1C2836") {
            updateSetting(settings.copy(hdr10Enabled = !settings.hdr10Enabled), requiresRestartIfLive = true)
        }
        btnRoiMode = actionButton("", "#1C2836") { cycleRoiMode() }
        btnKeyframeAndBFrames = actionButton("", "#1C2836") { cycleKeyframeAndBFrames() }
        btnQpAndLatency = actionButton("", "#1C2836") { cycleQpAndLatency() }

        boxSceneProDrawer.addView(buttonRow(btnGlEngine, btnHdr10))
        boxSceneProDrawer.addView(btnRoiMode)
        boxSceneProDrawer.addView(buttonRow(btnKeyframeAndBFrames, btnQpAndLatency))

        cardVideo.addView(btnToggleSceneProDrawer)
        cardVideo.addView(boxSceneProDrawer)
        container.addView(cardVideo)

        // === CARD 4: 15-STAGE SOLLUXE AUDIO, 250% GAME SOUNDSTAGE & 10s CALIBRATOR ===
        val cardAudio = sectionCard("4. 15-Stage SolLuxe™ Voice Studio & 250% Game Soundstage")
        cardAudio.addView(
            bodyText("Locks 48 kHz studio mic in AUTO mode (keeps Bluetooth earphones in stereo Game Mode!) + 64-bit Double MEMS EQ + 5.2 kHz Velvet Air + Unified 3-Zone Dynamics.")
        )

        btnMicDevice = actionButton("", "#1C2836") { cycleMicInputDevice() }
        btnMicSource = actionButton("", "#1C2836") { cycleMicSourceMode() }
        btnAudioBitrate = actionButton("", "#1C2836") { cycleAudioBitrate() }

        cardAudio.addView(btnMicDevice)
        cardAudio.addView(buttonRow(btnMicSource, btnAudioBitrate))

        cardAudio.addView(labelText("Voice Profile (Pick 1 — Tunes Chest Warmth, 300Hz Mud Cut, 5.2kHz Air & De-Esser):"))
        btnGenderNeutral = actionButton("Neutral", "#1C2836") {
            updateSetting(settings.copy(voiceGenderProfile = "NEUTRAL"))
        }
        btnGenderMale = actionButton("Male Voice", "#1C2836") {
            updateSetting(settings.copy(voiceGenderProfile = "MALE"))
        }
        btnGenderFemale = actionButton("Female Voice", "#1C2836") {
            updateSetting(settings.copy(voiceGenderProfile = "FEMALE"))
        }
        cardAudio.addView(buttonRow(btnGenderNeutral, btnGenderMale, btnGenderFemale))

        btnVoicePreset = actionButton("", "#283593") { cycleVoicePreset() }
        btnNoiseSuppressMode = actionButton("", "#004D40") { cycleNoiseSuppressMode() }
        btnNoiseSuppressLevel = actionButton("", "#004D40") {
            updateSetting(settings.copy(rnnoiseLevel = (settings.rnnoiseLevel + 1) % 4))
        }
        cardAudio.addView(btnVoicePreset)
        cardAudio.addView(buttonRow(btnNoiseSuppressMode, btnNoiseSuppressLevel))

        btnMicGain = actionButton("", "#00695C") { cycleMicGain() }
        btnGameGain = actionButton("", "#0D47A1") { cycleGameGain() }
        btnDucking = actionButton("", "#1C2836") { cycleDucking() }
        btnMicDelay = actionButton("", "#1C2836") { cycleMicSyncDelay() }
        cardAudio.addView(buttonRow(btnMicGain, btnGameGain))
        cardAudio.addView(buttonRow(btnDucking, btnMicDelay))

        cardAudio.addView(labelText("Stackable Stereo Game Soundstage (Stream-Only • Combine Any 1–4):"))
        btnGameWide = actionButton("", "#1C2836") {
            updateSetting(settings.copy(gameCinemaWideEnabled = !settings.gameCinemaWideEnabled))
        }
        btnGameSubPunch = actionButton("", "#1C2836") {
            updateSetting(settings.copy(gameSubPunchEnabled = !settings.gameSubPunchEnabled))
        }
        btnGameFootsteps = actionButton("", "#1C2836") {
            updateSetting(settings.copy(gameFootstepFocusEnabled = !settings.gameFootstepFocusEnabled))
        }
        btnGameBlastTamer = actionButton("", "#1C2836") {
            updateSetting(settings.copy(gameBlastTamerEnabled = !settings.gameBlastTamerEnabled))
        }
        cardAudio.addView(buttonRow(btnGameWide, btnGameSubPunch))
        cardAudio.addView(buttonRow(btnGameFootsteps, btnGameBlastTamer))

        // Collapsible Drawer: Individual 10-Stage Voice Filter Switches
        boxVoiceStageDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = cardDrawable("#0B111C", "#1E3A5F")
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            layoutParams = lp
        }
        btnToggleVoiceStageDrawer = actionButton(
            "▼ Expand: Individual Voice Filter Switches (Mix & Match All 10 Stages)",
            "#162234"
        ) {
            val show = boxVoiceStageDrawer.visibility != View.VISIBLE
            boxVoiceStageDrawer.visibility = if (show) View.VISIBLE else View.GONE
            btnToggleVoiceStageDrawer.text = if (show) {
                "▲ Hide: Individual Voice Filter Switches"
            } else {
                "▼ Expand: Individual Voice Filter Switches (Mix & Match All 10 Stages)"
            }
        }

        btnStageHpf = actionButton("", "#1C2836") { cycleHpf() }
        btnStageTransient = actionButton("", "#1C2836") {
            updateSetting(settings.copy(transientTamerEnabled = !settings.transientTamerEnabled))
        }
        btnStageGate = actionButton("", "#1C2836") {
            updateSetting(settings.copy(noiseGateEnabled = !settings.noiseGateEnabled))
        }
        btnStageEq = actionButton("", "#1C2836") {
            updateSetting(settings.copy(voiceEqEnabled = !settings.voiceEqEnabled))
        }
        btnStageDeEss = actionButton("", "#1C2836") {
            updateSetting(settings.copy(deEsserEnabled = !settings.deEsserEnabled))
        }
        btnStageVelvet = actionButton("", "#1C2836") {
            updateSetting(settings.copy(velvetWarmthEnabled = !settings.velvetWarmthEnabled))
        }
        btnStageComp = actionButton("", "#1C2836") {
            updateSetting(settings.copy(compressorEnabled = !settings.compressorEnabled))
        }
        btnStageWhisper = actionButton("", "#1C2836") {
            updateSetting(settings.copy(whisperLiftEnabled = !settings.whisperLiftEnabled))
        }
        btnStageSlowAgc = actionButton("", "#1C2836") {
            updateSetting(settings.copy(slowAgcEnabled = !settings.slowAgcEnabled))
        }
        btnStageSpeakerAec = actionButton("", "#1C2836") {
            updateSetting(settings.copy(speakerAecEnabled = !settings.speakerAecEnabled))
        }
        btnExtraSafeguards = actionButton("", "#1C2836") { cycleExtraSafeguards() }

        boxVoiceStageDrawer.addView(buttonRow(btnStageHpf, btnStageTransient))
        boxVoiceStageDrawer.addView(buttonRow(btnStageGate, btnStageEq))
        boxVoiceStageDrawer.addView(buttonRow(btnStageDeEss, btnStageVelvet))
        boxVoiceStageDrawer.addView(buttonRow(btnStageComp, btnStageWhisper))
        boxVoiceStageDrawer.addView(buttonRow(btnStageSlowAgc, btnStageSpeakerAec))
        boxVoiceStageDrawer.addView(btnExtraSafeguards)

        cardAudio.addView(btnToggleVoiceStageDrawer)
        cardAudio.addView(boxVoiceStageDrawer)

        tvMicTestStatus = bodyText(
            "10s Guided CODM Grip Calibrator: 3s silent grip (measures fan/rumble) + 7s callouts -> recommends exact Gain, HPF & RNNoise!"
        )
        btnApplyAdvisor = actionButton("Apply Advisor Recommendation", "#6A1B9A") {
            val adv = SolDualAudioEngine.lastCalibrationAdvice
            if (adv != null) {
                updateSetting(adv.applyTo(settings))
                Toast.makeText(this, "Applied calibrated voice settings!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Run the 10s Grip Test first!", Toast.LENGTH_SHORT).show()
            }
        }
        cardAudio.addView(tvMicTestStatus)
        cardAudio.addView(
            buttonRow(
                actionButton("Record 10s Test", "#00695C") { runTenSecMicCalibration() },
                actionButton("Play Test", "#1565C0") { playMicTestSample() },
                actionButton("Delete", "#37474F") {
                    if (!micTestBusy.get()) {
                        SolDualAudioEngine.deleteMicTestFile(this)
                        tvMicTestStatus.text = "Saved 10s calibration sample deleted."
                    }
                }
            )
        )
        cardAudio.addView(btnApplyAdvisor)
        container.addView(cardAudio)

        // === CARD 5: LAUNCH BROADCAST, ARCHIVED SESSION REPORTS, BENCHMARK & SUPPORT ===
        val cardLaunch = sectionCard("5. Launch Broadcast, Report History & App Benchmark")

        btnGoLive = actionButton("GO LIVE — START STREAM", "#00C853") {
            onGoLiveClicked()
        }.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(52)
        }
        cardLaunch.addView(btnGoLive)

        btnGlipBench = actionButton("Benchmark Other App", "#37474F") {
            toggleCompetitorBenchmark()
        }

        cardLaunch.addView(
            buttonRow(
                actionButton("Session Reports & History", "#1565C0") { showDiagnosticsDialog() },
                btnGlipBench
            )
        )
        cardLaunch.addView(
            buttonRow(
                actionButton("Refresh Mics / HW Scan", "#263238") { refreshHardwareAndMicsAsync() },
                actionButton("Check GitHub Update", "#263238") { checkGitHubReleaseUpdate() }
            )
        )
        cardLaunch.addView(
            actionButton("❤️ Enjoying SolStream? Subscribe to @Sol_CODM to Support!", "#AD1457") {
                openDeveloperYouTubeChannel()
            }
        )
        container.addView(cardLaunch)

        // === FOOTER ===
        val footer = TextView(this).apply {
            text = "ॐ नमः शिवाय\nMade by Sol (@Sol_CODM) • Powered by SolApex™ HyperStream Engine (SolCore Ultra • SolLuxe • SolRelay Omega • SolCodec)"
            setTextColor(Color.parseColor("#78909C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        container.addView(footer)

        rootScroll.addView(container)
        return rootScroll
    }

    private fun applyTierDefaultsToSettings(
        base: StreamSettings,
        tier: DeviceTier,
        scan: DeviceScanSummary
    ): StreamSettings {
        val hevcOk = scan.hevcHardware?.let {
            it.verifiedRealEncode || it.verifiedSurfaceConfigure || it.advertisedByMetadata
        } == true
        val codec = if (hevcOk) "HEVC" else "H264"
        return when (tier) {
            DeviceTier.LITE -> MicDspProcessor.applyPresetToSettings(base, "CLEAN").copy(
                deviceTier = tier.id,
                codec = codec,
                width = 1280,
                height = 720,
                fps = 30,
                targetBitrateKbps = 4000,
                audioBitrateKbps = 128,
                upscalerMode = "BILINEAR",
                noiseSuppressMode = "RNNOISE",
                rnnoiseLevel = 1
            )
            DeviceTier.BALANCED -> MicDspProcessor.applyPresetToSettings(base, "CLEAN").copy(
                deviceTier = tier.id,
                codec = codec,
                width = 1920,
                height = 1080,
                fps = 60,
                targetBitrateKbps = 12000,
                audioBitrateKbps = 160,
                upscalerMode = "BICUBIC",
                noiseSuppressMode = "RNNOISE",
                rnnoiseLevel = 2
            )
            DeviceTier.HIGH -> MicDspProcessor.applyPresetToSettings(base, "STUDIO").copy(
                deviceTier = tier.id,
                codec = codec,
                width = 1920,
                height = 1080,
                fps = 60,
                targetBitrateKbps = 16000,
                audioBitrateKbps = 192,
                upscalerMode = "BICUBIC",
                noiseSuppressMode = "RNNOISE",
                rnnoiseLevel = 2
            )
            DeviceTier.FLAGSHIP -> MicDspProcessor.applyPresetToSettings(base, "SOL_LUXE").copy(
                deviceTier = tier.id,
                codec = codec,
                width = if (scan.hevcHardware?.supports1440p60 == true) 2560 else 1920,
                height = if (scan.hevcHardware?.supports1440p60 == true) 1440 else 1080,
                fps = 60,
                targetBitrateKbps = 20000,
                audioBitrateKbps = 256,
                upscalerMode = "BICUBIC",
                noiseSuppressMode = "RNNOISE",
                rnnoiseLevel = 2
            )
            DeviceTier.ULTRA -> MicDspProcessor.applyPresetToSettings(base, "SOL_LUXE").copy(
                deviceTier = tier.id,
                codec = codec,
                width = if (scan.hevcHardware?.supports1440p60 == true) 2560 else 1920,
                height = if (scan.hevcHardware?.supports1440p60 == true) 1440 else 1080,
                fps = 60,
                targetBitrateKbps = 20000,
                audioBitrateKbps = 320,
                upscalerMode = "LANCZOS",
                noiseSuppressMode = "RNNOISE",
                rnnoiseLevel = 2
            )
        }
    }

    private fun refreshHardwareAndMicsAsync() {
        // Never run hardware MediaCodec probing while a live broadcast is active!
        if (SolStreamService.isBroadcastLive) {
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
                    "SolApex™ HyperStream Engine • ${scanSummary.deviceModel} (${scanSummary.socModel}) • Android ${scanSummary.androidRelease}\n${scanSummary.toBadgeText()}"
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

        tvLiveBanner.text = when {
            SolStreamService.needsProjectionReapproval ->
                "STATUS: Screen capture paused by Android (lock/system). Tap orange button below to re-approve!"
            isLive ->
                "STATUS: ${SolStreamService.currentLiveStateLabel} • All live controls below & 3-Tab Floating Pill sync instantly!"
            SolStreamService.isGlipBenchmarkRunning ->
                "STATUS: Passive Benchmark Recording Thermal & RAM for $benchmarkTargetAppName..."
            else ->
                "STATUS: Ready in SolApex™ Pre-Stream Studio (0% background load when idle)"
        }
        btnReapproveCapture.visibility = if (SolStreamService.needsProjectionReapproval) View.VISIBLE else View.GONE

        // Tier Buttons
        val activeTier = DeviceTier.fromId(settings.deviceTier)
        val recTier = scanSummary.recommendedTier
        tierButtons.forEach { (t, btn) ->
            val selected = (t == activeTier)
            val fill = when {
                selected && t == DeviceTier.ULTRA -> "#6A1B9A"
                selected -> "#00838F"
                else -> "#15202E"
            }
            val stroke = if (selected) "#FFC400" else if (t == recTier) "#00E5FF" else "#37474F"
            btn.background = roundedDrawable(fill, stroke)
        }
        tvTierDescription.text =
            "Active Tier: ${activeTier.title} (${activeTier.badgeSummary})\n" +
                "Scanner Recommended for ${scanSummary.deviceModel}: ${recTier.title} Tier"

        // Pre-Flight
        tvPreflightStatus.text =
            "• Mic Permission: ${okWait(micGranted)}   • Notifications: ${okWait(notifGranted)}\n" +
                "• Battery Unrestricted: ${okWait(battUnrestricted)}   • Accessibility Service: ${okWait(accEnabled)}"
        tvRestrictedHint.visibility = if (accEnabled) View.GONE else View.VISIBLE

        // Vault & Simulcast
        btnPlatformPreset.text = "Platform Preset: ${settings.platformPreset} (Tap to switch YT / Twitch / Kick / Custom)"
        tvVaultSecurityLabel.text = "Primary Stream Key (AES-256-GCM • ${KeystoreVault.getKeySecurityDescription()}):"
        btnShowHideKey.text = if (showStreamKeyPlain) "Hide Key" else "Show Key"
        btnUseShortsSlot.text =
            "Auto-Use Slot #2 Key When 9:16 Shorts Selected: ${onOff(settings.useShortsKeySlot)}"
        btnSimulcastTwitch.text =
            "Simulcast to Twitch (Single-Encode H.264): ${onOff(settings.simulcastTwitchEnabled)}"
        btnSimulcastKick.text =
            "Simulcast to Kick (Single-Encode H.264): ${onOff(settings.simulcastKickEnabled)}"
        val dualSupported = scanSummary.supportsDualFormatCompanion()
        btnDualCompanion.text =
            "Dual-Format 16:9 + 9:16 Simultaneous Companion: ${onOff(settings.dualFormatCompanionEnabled)}" +
                (if (!dualSupported) " (Needs Flagship/Ultra 2-Instance HW)" else "")

        // Video Engine
        val restartSuffix = if (isLive) " [Next]" else ""
        val av1Available = scanSummary.av1Hardware != null &&
            (scanSummary.av1Hardware?.supports1440p60 == true || scanSummary.av1Hardware?.supports1080p60 == true)
        val simLockH264 = settings.simulcastTwitchEnabled || settings.simulcastKickEnabled
        btnCodec.text = if (simLockH264) {
            "Codec: H264 (Locked for Simulcast)$restartSuffix"
        } else {
            "Codec: ${settings.codec}" + (if (!av1Available && settings.codec != "AV1") " (AV1 SW Blocked)" else "") + restartSuffix
        }

        val longDim = max(settings.width, settings.height)
        val resLabel = when {
            longDim >= 3840 -> "4K (2160p)"
            longDim >= 2560 -> "1440p (2K)"
            longDim >= 1920 -> "1080p (FHD)"
            longDim >= 1280 -> "720p (HD)"
            longDim >= 800 -> "480p (Data Saver)"
            else -> "360p (Ultra Saver)"
        }
        btnResolution.text = "Res: $resLabel$restartSuffix"
        btnFps.text = "Stream: ${settings.fps} FPS$restartSuffix" +
            (if (longDim <= 864 && settings.fps == 60) " (YT 480p plays @30)" else "")
        btnOrientationFlip.text = if (settings.isVertical) {
            "Format: 9:16 Vertical Shorts$restartSuffix"
        } else {
            "Format: 16:9 Wide (${settings.landscapeFlip}° USB-C Flip)"
        }

        val mbpsStr = formatBitrateMbps(settings.targetBitrateKbps)
        btnBitrateCycle.text = "Target: $mbpsStr"
        btnRateControl.text = "Rate: ${if (settings.useVbr) "VBR (Test)" else "CBR (Rec)"}$restartSuffix"
        btnAbr.text = "Ping Guard ABR 2.0: ${onOff(settings.adaptiveBitrate)}"
        btnUpscaler.text = "Upscaler: ${settings.upscalerMode}"
        val frameDisp = if (settings.framingMode == "STRETCH") "STRETCH (16:9 Full)" else settings.framingMode
        btnFraming.text = "Framing: $frameDisp"
        btnSmartZoom.text = "Zoom: ${settings.smartZoomPercent}%"

        btnFilmic.text = "Cinematic Filmic: ${onOff(settings.cinematicFilmic)}"
        btnFilmic.background = roundedDrawable(if (settings.cinematicFilmic) "#00695C" else "#1C2836", "#37474F")
        btnAmoledVivid.text = "AMOLED Vivid Pop: ${onOff(settings.amoledVivid)}"
        btnAmoledVivid.background = roundedDrawable(if (settings.amoledVivid) "#00695C" else "#1C2836", "#37474F")
        btnShadowBoost.text = "Shadow Boost (Dark Corners): ${onOff(settings.shadowBoost)}"
        btnShadowBoost.background = roundedDrawable(if (settings.shadowBoost) "#00695C" else "#1C2836", "#37474F")
        btnColorTemp.text = "Color Temp: ${settings.colorTempFilter}"
        btnSharpen.text = if (settings.gpuSharpen) {
            "GPU Crisp Sharpen: ON (Turn OFF to reduce phone heat)"
        } else {
            "GPU Crisp Sharpen: OFF (Recommended — Coolest Temp)"
        }
        btnSharpen.background = roundedDrawable(if (settings.gpuSharpen) "#00695C" else "#1C2836", "#37474F")

        // Scenes & Pro Silicon Drawer
        val hasStart = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.STARTING_SOON)
        val hasBrb = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.BRB_SCENE)
        val hasOv = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.OVERLAY_PNG)
        val hasPost = SolSceneAssetStore.hasCustomAsset(this, SolSceneAssetStore.AssetSlot.SHORTS_POSTER)
        btnPickStartImg.text = "Starting Soon Img (${if (hasStart) "Custom Saved" else "Default Slate"})"
        btnPickBrbImg.text = "BRB Privacy Img (${if (hasBrb) "Custom Saved" else "Default Slate"})"
        btnPickOverlayPng.text = ".PNG Overlay (${if (hasOv) "Saved • ${onOff(settings.overlayImageEnabled)}" else "Pick .PNG"})"
        btnPickShortsPoster.text = "9:16 Poster (${if (hasPost) "Custom Saved" else "Pick Image"})"
        btnBannerToggle.text = "Live Text Banner: ${onOff(settings.liveBannerEnabled)}"
        btnBannerPos.text = "Banner Pos: ${settings.liveBannerPosition}"
        btnShortsBackdrop.text = "9:16 Shorts Canvas: ${settings.shortsBackdropMode} (BLUR / DARK / POSTER)"
        btnMinimapShield.text =
            "Stream-Sniper Minimap Blur Shield: ${onOff(settings.minimapShieldEnabled)} (${settings.minimapShieldCorner})"
        btnGlEngine.text = "OpenGL Engine: ${settings.glEngineMode} (Device Max: ES ${scanSummary.maxGlEsVersion})$restartSuffix"
        val hdrSupported = scanSummary.hevcHardware?.supports10BitHdr == true
        btnHdr10.text = "True 10-Bit HDR10 (HLG): ${onOff(settings.hdr10Enabled)}" +
            (if (!hdrSupported) " (HW 8-Bit Only)" else "") + restartSuffix
        btnRoiMode.text = "Hardware ROI Focus: ${settings.roiMode} (Crosshair/Minimap QP Boost)"
        btnKeyframeAndBFrames.text = "IDR Keyframe: ${settings.keyframeIntervalSec}s | B-Frames: ${settings.maxBFrames}$restartSuffix"
        val qpLabel = if (settings.qpMaxCap == 0) "Auto (Min QP 14)" else "14..${settings.qpMaxCap}"
        btnQpAndLatency.text = "QP Range: $qpLabel | Low-Lat Hint: ${onOff(settings.lowLatencyCodecHint)}$restartSuffix"

        // Audio Studio
        val selectedMicLabel = micOptions.firstOrNull { it.deviceId == settings.micDeviceId }?.label
            ?: micOptions.firstOrNull()?.label ?: "Auto (Phone Studio Mic Locked)"
        btnMicDevice.text = "Mic Input: $selectedMicLabel"
        val srcName = when (settings.micAudioSource) {
            1 -> "MIC (Raw)"
            6 -> "VOICE_REC (Wideband 48kHz)"
            7 -> "VOICE_COMM (Speaker AEC)"
            else -> "AUTO (48kHz Studio Wideband)"
        }
        btnMicSource.text = "Source: $srcName"
        btnAudioBitrate.text = "Master AAC: ${settings.audioBitrateKbps} kbps$restartSuffix"

        val gProf = settings.voiceGenderProfile.uppercase(Locale.US)
        btnGenderNeutral.background = roundedDrawable(if (gProf == "NEUTRAL") "#00838F" else "#1C2836", if (gProf == "NEUTRAL") "#FFC400" else "#37474F")
        btnGenderMale.background = roundedDrawable(if (gProf == "MALE") "#00838F" else "#1C2836", if (gProf == "MALE") "#FFC400" else "#37474F")
        btnGenderFemale.background = roundedDrawable(if (gProf == "FEMALE") "#00838F" else "#1C2836", if (gProf == "FEMALE") "#FFC400" else "#37474F")

        btnVoicePreset.text = "Voice Type: ${MicDspProcessor.presetDisplayName(settings.voicePreset)}"
        val nsModeDesc = when (settings.noiseSuppressMode) {
            "RNNOISE" -> if (SolNativeRnnoise.isLibraryLoaded) "Native C 48kHz RNNoise" else "RNNoise (Fallback)"
            "LIGHT" -> "Light Mathematical Cut"
            "HW_NS" -> "Phone Chip HW NS"
            else -> "OFF"
        }
        val nsLvlDesc = when (settings.rnnoiseLevel) {
            0 -> "OFF (0%)"
            1 -> "Lv1 Gentle (65%)"
            2 -> "Lv2 Natural (88%)"
            else -> "Lv3 Max (100%)"
        }
        btnNoiseSuppressMode.text = "Denoiser (Pick 1): $nsModeDesc"
        btnNoiseSuppressLevel.text = "Strength: $nsLvlDesc"

        btnMicGain.text = "Mic Boost: ${settings.micGainPercent}%"
        btnGameGain.text = "Game Mix: ${settings.gameGainPercent}% (Up to 250%)"
        btnDucking.text = if (settings.vadDuckingPercent == 0) "VAD Ducking: OFF" else "VAD Ducking: -${settings.vadDuckingPercent}%"
        btnMicDelay.text = "Mic Sync: +${settings.micSyncDelayMs} ms"

        btnGameWide.text = "Cinema 3D Wide: ${onOff(settings.gameCinemaWideEnabled)}"
        btnGameWide.background = roundedDrawable(if (settings.gameCinemaWideEnabled) "#0D47A1" else "#1C2836", "#37474F")
        btnGameSubPunch.text = "Theater Sub-Punch: ${onOff(settings.gameSubPunchEnabled)}"
        btnGameSubPunch.background = roundedDrawable(if (settings.gameSubPunchEnabled) "#0D47A1" else "#1C2836", "#37474F")
        btnGameFootsteps.text = "Footstep Focus: ${onOff(settings.gameFootstepFocusEnabled)}"
        btnGameFootsteps.background = roundedDrawable(if (settings.gameFootstepFocusEnabled) "#0D47A1" else "#1C2836", "#37474F")
        btnGameBlastTamer.text = "Blast Tamer: ${onOff(settings.gameBlastTamerEnabled)}"
        btnGameBlastTamer.background = roundedDrawable(if (settings.gameBlastTamerEnabled) "#0D47A1" else "#1C2836", "#37474F")

        // Individual Voice Stage Switches
        btnStageHpf.text = if (settings.hpfCutoffHz == 0) "1. HPF Rumble: OFF" else "1. HPF Rumble: ${settings.hpfCutoffHz} Hz"
        btnStageTransient.text = "2. Spark/Hammer & Grip Blanker: ${onOff(settings.transientTamerEnabled)}"
        btnStageGate.text = "3. 0-ms Instant Breath Gate: ${onOff(settings.noiseGateEnabled)}"
        btnStageEq.text = "4. ${settings.voiceGenderProfile} MEMS 3-Band EQ: ${onOff(settings.voiceEqEnabled)}"
        btnStageDeEss.text = "5. Split-Band De-Esser: ${onOff(settings.deEsserEnabled)}"
        btnStageVelvet.text = "6. 5.2kHz Velvet Air & Exciter: ${onOff(settings.velvetWarmthEnabled)}"
        btnStageComp.text = "7. 12ms-Punch 3:1 Compressor: ${onOff(settings.compressorEnabled)}"
        btnStageWhisper.text = "8. Whisper Callout Lift: ${onOff(settings.whisperLiftEnabled)}"
        btnStageSlowAgc.text = "9. Speech-Gated Slow AGC: ${onOff(settings.slowAgcEnabled)}"
        btnStageSpeakerAec.text = "10. Speaker Echo Canceler: ${onOff(settings.speakerAecEnabled)}"
        btnExtraSafeguards.text =
            "Scene Auto-Mute Mic: ${onOff(settings.brbMuteMicToo)} | Low-Lat Wi-Fi Lock: ${onOff(settings.wifiLowLatencyLock)}"

        if (isLive) {
            btnGoLive.text = "STOP LIVE STREAM"
            btnGoLive.background = roundedDrawable("#D50000", "#FF5252")
        } else {
            btnGoLive.text = "GO LIVE — START STREAM"
            btnGoLive.background = roundedDrawable("#00897B", "#00E676")
        }

        btnGlipBench.text = if (SolStreamService.isGlipBenchmarkRunning) {
            "Stop & Save $benchmarkTargetAppName Benchmark"
        } else {
            "Benchmark Other Stream App"
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

    // === CYCLERS & STEPPERS ===
    private fun cyclePlatformPreset() {
        val order = listOf("YOUTUBE", "TWITCH", "KICK", "CUSTOM")
        val next = order[(order.indexOf(settings.platformPreset) + 1) % order.size]
        val newUrl = when (next) {
            "YOUTUBE" -> KeystoreVault.DEFAULT_YOUTUBE_RTMPS
            "TWITCH" -> KeystoreVault.DEFAULT_TWITCH_RTMPS
            "KICK" -> KeystoreVault.DEFAULT_KICK_RTMPS
            else -> etRtmpsUrl.text?.toString() ?: KeystoreVault.DEFAULT_YOUTUBE_RTMPS
        }
        etRtmpsUrl.setText(newUrl)
        saveVaultInputs(showToast = false)
        val forceCodec = if (next == "TWITCH" || next == "KICK") "H264" else settings.codec
        updateSetting(settings.copy(platformPreset = next, codec = forceCodec), requiresRestartIfLive = true)
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
        val zooms = listOf(104, 108, 112, 116, 120)
        val next = zooms[(zooms.indexOf(settings.smartZoomPercent) + 1) % zooms.size]
        updateSetting(settings.copy(framingMode = "SMART", smartZoomPercent = next))
    }

    private fun cycleColorTemp() {
        val order = listOf("NEUTRAL", "WARM", "COOL")
        val next = order[(order.indexOf(settings.colorTempFilter) + 1) % order.size]
        updateSetting(settings.copy(colorTempFilter = next))
    }

    private fun cycleShortsBackdrop() {
        val order = listOf("BLUR", "DARK", "POSTER")
        val next = order[(order.indexOf(settings.shortsBackdropMode) + 1) % order.size]
        updateSetting(settings.copy(shortsBackdropMode = next))
    }

    private fun cycleMinimapShield() {
        val (en, corner) = when {
            !settings.minimapShieldEnabled -> true to "TOP_RIGHT"
            settings.minimapShieldCorner == "TOP_RIGHT" -> true to "TOP_LEFT"
            settings.minimapShieldCorner == "TOP_LEFT" -> true to "BOTTOM_LEFT"
            settings.minimapShieldCorner == "BOTTOM_LEFT" -> true to "BOTTOM_RIGHT"
            else -> false to "TOP_RIGHT"
        }
        updateSetting(settings.copy(minimapShieldEnabled = en, minimapShieldCorner = corner))
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
            settings.qpMaxCap == 0 && settings.lowLatencyCodecHint -> 38 to true
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
        val next = when (settings.hpfCutoffHz) {
            0 -> 75; 75 -> 80; 80 -> 85; 85 -> 100; 100 -> 120; else -> 0
        }
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
        val next = when (settings.vadDuckingPercent) {
            0 -> 20; 20 -> 35; 35 -> 50; else -> 0
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

    // === PRESET SHARE & PLAIN-ENGLISH GLOSSARY ===
    private fun copyPresetToClipboard() {
        val json = KeystoreVault.exportPresetJson(settings)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SolStream Ultra Preset", json))
        Toast.makeText(
            this,
            "Settings Preset copied! (Stream Keys are NEVER included — safe to share with friends!)",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun importPresetFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val raw = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (raw.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        val imported = KeystoreVault.importPresetJson(raw, settings)
        if (imported != null) {
            updateSetting(imported, requiresRestartIfLive = true)
            Toast.makeText(this, "Preset imported and applied!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Clipboard does not contain a valid SolStream Preset JSON.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPlainEnglishGlossaryDialog() {
        val guide = """
            === SOLSTREAM STUDIO ULTRA (SolApex™ HyperStream Engine) — GUIDE & GLOSSARY ===

            [SOLAPEX™ HYPERSTREAM ENGINE ARCHITECTURE]
            • SolCore Ultra (Video Engine): Direct-TBDR OpenGL ES 3.0/2.0 renderer with precomputed vertex UV offsets, zero-ALU passthrough, and 10-bit HDR10 verification.
            • SolLuxe (Audio Engine): 15-stage 32-bit float / 64-bit double voice & stereo game studio with Native C 48 kHz RNNoise and Unified 3-Zone Dynamics.
            • SolRelay Omega (Broadcast Engine): RTMPS engine with Queue-Aware Ping Guard ABR 2.0, zombie-socket recovery, and single-encode simulcast.
            • SolCodec (Hardware Silicon Layer): Verified hardware MediaCodec selector with QP 14 lossless floor, ROI focus, and automatic live resolution fallback ladder.

            [VIDEO & FRAMING TERMS]
            • Framing (FIT / STRETCH / SMART / FILL):
              - FIT: Shows 100% of your phone screen with thin black bars at top/bottom.
              - STRETCH (Full 16:9 Zero-Bars): Maps all 4 corners of your phone screen directly to all 4 corners of 360p, 480p, 720p, 1080p, 1440p, or 4K! Gives ZERO black bars on laptops/TVs while keeping 100% of your CODM HUD buttons visible!
              - SMART (104%–120%): Zooms partway to reduce top/bottom bars.
              - FILL: Zooms until bars disappear (crops ~9% of left/right edges).
            • HEVC (H.265) vs. H.264 vs. AV1: Keep HEVC for YouTube (~40% sharper 1440p60/1080p60 video than H.264 at the same Mbps). Use H.264 for Twitch/Kick or Simulcast.
            • Upscaler (Bilinear / Bicubic / Lanczos): Bilinear is coolest; Bicubic is crisp & balanced; Lanczos is ultra-sharp for flagship GPUs.
            • 2-Stage Ground-Reality Thermal Armor: Above 44.0°C & 90% SoC load, gently trims repeat-frame GPU work & 15% Mbps while keeping full 60 FPS. If Severe warmth occurs, steps to 30 FPS AND lowers Mbps by 40% (with a QP 14 floor) so CODM gunfire never stutters!

            [SOLLUXE™ VOICE & GAME SOUNDSTAGE TERMS]
            • Mic Input AUTO (Phone Studio Mic Locked): Automatically locks onto your phone's 48 kHz studio microphone using VOICE_RECOGNITION wideband mode—even if you leave Bluetooth 'Calls ON' in Android settings!
            • Voice Profile (Neutral / Male / Female): Tunes chest body (125/150/185 Hz), two-hand grip mud cut (-3.2 to -3.4 dB @ 300/335/375 Hz), callout clarity (+2.8 dB @ 3.15–3.65 kHz), 5.1–5.6 kHz Velvet Air Shelf, and De-Esser.
            • SOL LUXE (Studio + Velvet): Combines Native C 48kHz RNNoise, Consonant-Safe Spark/Hammer Blanker, 0-ms Instant Gate, MEMS 3-Band EQ, Split-Band De-Esser, 5.2 kHz Velvet Air + Adaptive Clarity Exciter, and Unified 3-Zone Gain Controller (Whisper Lift + Slow AGC + 12ms-Punch 3:1 Compressor).
            • Cinema 3D Wide, Sub-Punch, Footsteps & Blast Tamer: Enhances stereo game audio for your stream viewers without altering your own earphones!
        """.trimIndent()

        val tv = TextView(this).apply {
            text = guide
            setTextColor(Color.parseColor("#ECEFF1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#070B12"))
            addView(tv)
        }
        AlertDialog.Builder(this)
            .setTitle("SolApex™ Plain-English Guide & Glossary")
            .setView(scroll)
            .setPositiveButton("Got It", null)
            .show()
    }

    // === VAULT & KEY VISIBILITY ACTIONS (VERIFIES ENCRYPTION + STORAGE SUCCEEDED) ===
    private fun saveVaultInputs(showToast: Boolean): Boolean {
        val rawUrl = etRtmpsUrl.text?.toString() ?: KeystoreVault.DEFAULT_YOUTUBE_RTMPS
        val normalizedUrl = KeystoreVault.normalizeIngestUrl(rawUrl)
        val cleanKey = etStreamKey.text?.toString()?.trim()?.trimStart('/') ?: ""
        etRtmpsUrl.setText(normalizedUrl)

        val okPrimary = KeystoreVault.saveEndpointAndKey(this, normalizedUrl, cleanKey)
        val okShorts = KeystoreVault.saveShortsStreamKey(this, etShortsKey.text?.toString() ?: "")
        val okTwitch = KeystoreVault.saveTwitchUrlAndKey(
            this,
            KeystoreVault.DEFAULT_TWITCH_RTMPS,
            etTwitchKey.text?.toString() ?: ""
        )
        val okKick = KeystoreVault.saveKickUrlAndKey(
            this,
            KeystoreVault.DEFAULT_KICK_RTMPS,
            etKickKey.text?.toString() ?: ""
        )
        val allSavedOk = okPrimary && okShorts && okTwitch && okKick

        val bannerTxt = etBannerText.text?.toString()?.trim()?.take(80) ?: ""
        if (bannerTxt != settings.liveBannerText) {
            settings = settings.copy(liveBannerText = bannerTxt)
            KeystoreVault.saveSettings(this, settings)
            SolSceneAssetStore.bumpRevision()
        }

        if (!allSavedOk) {
            Toast.makeText(
                this,
                "Warning: Android Keystore encryption failed—new Stream Key could not be saved!",
                Toast.LENGTH_LONG
            ).show()
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
        Toast.makeText(this, "Primary Stream Key cleared.", Toast.LENGTH_SHORT).show()
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
            "Opening YouTube Studio — tap Go Live icon -> copy your Default Variable Stream Key once!",
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

        if (!saveVaultInputs(showToast = false)) {
            return
        }
        if (KeystoreVault.buildActivePrimaryEndpoint(this, settings).isNullOrBlank()) {
            Toast.makeText(this, "Please paste or type your Stream Key first!", Toast.LENGTH_LONG).show()
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
                    "SolStream Studio uses Android's Accessibility Overlay to display your 3-Tab Floating Pill in CODM and keep microphone sharing active alongside squad voice chat.\n\n" +
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
        // Gate Go Live if initial 1-second startup hardware scan is still running so probe & live codecs never contend
        if (CodecCapabilityProbe.isHardwareProbeCurrentlyRunning()) {
            Toast.makeText(
                this,
                "Finishing 1-second hardware encoder check—launching stream...",
                Toast.LENGTH_SHORT
            ).show()
            Thread({
                var waitedMs = 0
                while (CodecCapabilityProbe.isHardwareProbeCurrentlyRunning() && waitedMs < 1500) {
                    try { Thread.sleep(60) } catch (_: InterruptedException) { break }
                    waitedMs += 60
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && !SolStreamService.isBroadcastLive) {
                        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(mpm.createScreenCaptureIntent())
                    }
                }
            }, "SolWaitProbeBeforeLive").start()
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun runTenSecMicCalibration() {
        if (SolStreamService.isBroadcastLive) {
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
        tvMicTestStatus.text = "Starting 10-second Guided CODM Grip Calibration..."
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
        if (!micTestBusy.compareAndSet(false, true)) {
            Toast.makeText(this, "Audio test busy...", Toast.LENGTH_SHORT).show()
            return
        }
        tvMicTestStatus.text = "Playing recorded 10s calibration sample..."
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

        val apps = arrayOf("Glip", "PRISM Live Studio", "Streamlabs", "Zero Lag", "Turnip / Other App")
        AlertDialog.Builder(this)
            .setTitle("Benchmark Another Streaming App")
            .setItems(apps) { _, which ->
                benchmarkTargetAppName = apps[which]
                DiagnosticsLogger.log("BENCH_TARGET", "Selected $benchmarkTargetAppName for passive thermal/RAM comparison.")
                startService(Intent(this, SolStreamService::class.java).apply {
                    action = SolStreamService.ACTION_START_GLIP_BENCH
                })
                refreshAllUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                    uploadMbps > 0.0 && uploadMbps < 5.0 ->
                        "Mobile/Low Upload (${String.format(Locale.US, "%.1f", uploadMbps)} Mbps): use 480p/720p HEVC @ 1.5–3 Mbps + ABR ON."
                    uploadMbps in 5.0..14.0 ->
                        "Upload measured ${String.format(Locale.US, "%.1f", uploadMbps)} Mbps: use 1080p60 @ 8–10 Mbps + ABR ON."
                    bloatDeltaMs > 65L ->
                        "Router bufferbloat +${bloatDeltaMs}ms under upload: set Bitrate 20–25% below max upload & keep Queue ABR ON."
                    uploadMbps >= 26.0 && idleAvgMs < 95 && idleJitterMs < 35 ->
                        "Excellent headroom! Safe for crisp 20–24 Mbps 1440p60 (or 35–50 Mbps 4K60)."
                    idleAvgMs < 150 && idleJitterMs < 65 ->
                        "Solid connection! 18–20 Mbps 1440p60 with Queue ABR ON recommended."
                    else ->
                        "High Wi-Fi jitter (${idleJitterMs}ms): use 12–16 Mbps Stable + 5GHz Wi-Fi."
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
            "Current Session Report (Saved History: $archivedCount)"
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
            setBackgroundColor(Color.parseColor("#070B12"))
            addView(tv)
        }
        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(scroll)
            .setPositiveButton("Copy Report") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("SolStream Report", reportText))
                Toast.makeText(this, "Report copied! Safe to paste anywhere.", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton(
                if (showingArchived) "View Current Report" else "Past History ($archivedCount)"
            ) { _, _ ->
                showDiagnosticsDialog(showingArchived = !showingArchived)
            }
            .setNegativeButton(if (showingArchived && archivedCount > 0) "Delete History" else "Close") { _, _ ->
                if (showingArchived && archivedCount > 0) {
                    SolStreamService.clearLocalReportArchive(this)
                    Toast.makeText(this, "Archived session history deleted.", Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun showSamsungSetupGuide() {
        val msg = """
            1. UNLOCK RESTRICTED ACCESSIBILITY (Why ⋮ 3-Dots Hide at First):
            • Tap '3. Tap Accessibility 1st' -> Installed apps -> tap greyed-out SolStream Studio once & press OK.
            • Return here and tap '4. Unlock Restricted (⋮)' -> tap ⋮ at top-right -> 'Allow restricted settings'.
            • Tap '3. Tap Accessibility 1st' again and switch SolStream Studio ON!

            2. AIRPODS / BLUETOOTH NECKBAND / WIRED EARPHONES (AUTO STUDIO MIC LOCK):
            • In 'Auto (Phone Studio Mic Locked)' mode, SolStream automatically locks onto your phone's 48 kHz studio microphone using VOICE_RECOGNITION wideband mode—even if you leave Bluetooth 'Calls ON'!
            • Optional on Samsung: Settings -> Sounds and vibration -> Separate app sound -> lock Call of Duty to Bluetooth for extra routing isolation.
            • Wired 3.5mm & USB-C Headsets work automatically in full stereo!

            3. ZERO-BARS 16:9 FRAMING & KEEPING BATTLE ROYALE COOL:
            • Set Framing to 'STRETCH (16:9 Full)' to fill 100% of 1080p/1440p/4K screens with zero black bars and 100% of your CODM HUD visible!
            • Our 2-Stage Thermal Armor automatically cools repeat frames at 44.0°C (90% SoC load) and scales bitrate proportionally (-40%) if 30 FPS Cooldown activates so CODM gunfire never stutters.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Samsung One UI, Earphones & BR Pro Guide")
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
        if (SolStreamService.isAccessibilityConnected(this)) return true
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains("$packageName/${SolStreamService::class.java.name}") ||
            enabled.contains("$packageName/.SolStreamService")
    }

    // === UI HELPERS ===
    private fun sectionCard(heading: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable("#101826", "#1E2D42")
            setPadding(dp(12), dp(11), dp(12), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(11) }
            layoutParams = lp

            addView(TextView(this@MainActivity).apply {
                text = heading
                setTextColor(Color.parseColor("#00E5FF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(5))
            })
        }
    }

    private fun labelText(txt: String): TextView = TextView(this).apply {
        text = txt
        setTextColor(Color.parseColor("#B0BEC5"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setPadding(0, dp(6), 0, dp(2))
    }

    private fun bodyText(txt: String): TextView = TextView(this).apply {
        text = txt
        setTextColor(Color.parseColor("#CFD8DC"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setPadding(0, dp(3), 0, dp(5))
    }

    private fun styledEditText(initial: String, isPassword: Boolean): EditText = EditText(this).apply {
        setText(initial)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#607D8B"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        isSingleLine = true
        background = cardDrawable("#070B12", "#37474F")
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
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        background = roundedDrawable(bgHex, "#37474F")
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
