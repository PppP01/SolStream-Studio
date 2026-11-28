/*
- ॐ नमः शिवाय (Om Namah Shivaya)
- SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
- Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
- Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PillTelemetrySnapshot(
    val isBenchmarkOnly: Boolean,
    val elapsedSec: Long,
    val liveTempC: Float,
    val peakPssMb: Int,
    val rtmpConnected: Boolean,
    val worstThermalState: Int,
    val encodedBitrateBps: Int,
    val encodedFps: Int,
    val freshCaptureFps: Int,
    val micMuted: Boolean,
    val gameMuted: Boolean,
    val sceneMode: String,
    val clipCount: Int,
    val activeSettings: StreamSettings,
    val shortMicPillLabel: String,
    val keyboardBlurActive: Boolean,
    val manualKeyboardBlurOverride: Boolean,
    val activeBitrateBps: Int,
    val thermalThrottle30Fps: Boolean,
    val manual30FpsSaverEnabled: Boolean,
    val autoThermalNative30BitrateTrimActive: Boolean,
    val thermalPressurePercent: Int,
    val queueFrames: Int,
    val enqueueLatencyMs: Long,
    val rttMs: Long,
    val rttSourceLabel: String,
    val appCpuPercent: Int,
    val appRamMb: Int,
    val totalRamGb: Int,
    val availRamGb: Float,
    val activeAuxCount: Int,
    val loupesPaused: Boolean = false
)

internal interface PillActions {
    fun getSnapshot(): PillTelemetrySnapshot
    fun onLiveStateLabelChanged(label: String)
    fun onImeKeyboardVisibilityChanged(visible: Boolean)
    fun onToggleMicMute()
    fun onToggleGameMute()
    fun onCycleScene()
    fun onMarkClip()
    fun onCycleVoiceGender()
    fun onCycleVoicePreset()
    fun onCycleNsMode()
    fun onCycleRnnoiseLevel()
    fun onCycleHpf()
    fun onToggleVoiceEq()
    fun onToggleDeEsser()
    fun onToggleVelvet()
    fun onToggleSlowAgc()
    fun onStepMicBoost(deltaPct: Int)
    fun onStepGameMix(deltaPct: Int)
    fun onToggleGameCinemaWide()
    fun onToggleGameSubPunch()
    fun onToggleGameFootstep()
    fun onToggleGameBlastTamer()
    fun onCycleDucking()
    fun onCycleMicHardware()
    fun onCycleUpscaler()
    fun onCycleFramingMode()
    fun onCycleSmartZoom()
    fun onToggleCinematicFilmic()
    fun onToggleAmoledVivid()
    fun onToggleShadowBoost()
    fun onCycleColorTemp()
    fun onToggleLandscapeFlip()
    fun onToggleGpuSharpen()
    fun onCycleOverlayPosition()
    fun onToggleLiveBanner()
    fun onTriggerCleanScreenshot()
    fun onToggleHudTelemetry()
    fun onToggleQrOverlay()
    fun onTogglePngTuber()
    fun onToggleMinimapShield()
    fun onToggleManualKeyboardBlur()
    fun onCycleShortsAnchor()
    fun onCycleShortsMinimapLoupe()
    fun onCycleShortsScoreLoupe()
    fun onToggleShortsHeaderLayout()
    fun onToggleLoupesPaused() {}
    fun onStepLiveBitrate(dir: Int)
    fun onToggleAbr()
    fun onToggleThermal30FpsSaver()
    fun onStopConfirmed()
}

/**
- Self-contained 3-tab Accessibility Ghost Floating Pill & IME Keyboard Blur detector.
- Keeps SolStreamService focused purely on broadcast lifecycle, networking, ABR, and thermal control.
 */
internal class SolFloatingPillController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val mainHandler: Handler,
    private val actions: PillActions
) {
    private var pillRootView: LinearLayout? = null
    private var imeDetectorView: View? = null
    private var pillHeaderRow: LinearLayout? = null
    private var pillStatusDot: TextView? = null
    private var pillStatusText: TextView? = null
    private var pillDrawerContainer: LinearLayout? = null
    private var pillLayoutParams: WindowManager.LayoutParams? = null
    private var expandedPanelWidthPx: Int = 0
    private var pillExpanded = false
    private var activePillTab = 0 // 0 = Audio, 1 = Video, 2 = Bitrate/Net
    private var lastStopConfirmTapMs = 0L
    @Volatile private var lastObservedImeVisible = false

    private var tabBtnAudio: TextView? = null
    private var tabBtnVideo: TextView? = null
    private var tabBtnNet: TextView? = null
    private var tabPaneAudio: LinearLayout? = null
    private var tabPaneVideo: LinearLayout? = null
    private var tabPaneNet: LinearLayout? = null

    // Top Quick-Action Bar buttons
    private var btnQuickMicMute: TextView? = null
    private var btnQuickGameMute: TextView? = null
    private var btnQuickScene: TextView? = null
    private var btnQuickMarkClip: TextView? = null

    // Tab 0 (Audio) live buttons
    private var btnOvGender: TextView? = null
    private var btnOvVoiceType: TextView? = null
    private var btnOvNsMode: TextView? = null
    private var btnOvNsLevel: TextView? = null
    private var btnOvHpf: TextView? = null
    private var btnOvEq: TextView? = null
    private var btnOvDeEss: TextView? = null
    private var btnOvVelvet: TextView? = null
    private var btnOvAgc: TextView? = null
    private var btnOvMicGainLabel: TextView? = null
    private var btnOvGameGainLabel: TextView? = null
    private var btnOvCinemaWide: TextView? = null
    private var btnOvSubPunch: TextView? = null
    private var btnOvFootstep: TextView? = null
    private var btnOvBlastTamer: TextView? = null
    private var btnOvDucking: TextView? = null
    private var btnOvMicRoute: TextView? = null

    // Tab 1 (Video, SolShorts™ Studio & Pro Overlays) live buttons
    private var btnOvSceneSelect: TextView? = null
    private var btnOvUpscaler: TextView? = null
    private var btnOvFraming: TextView? = null
    private var btnOvZoom: TextView? = null
    private var btnOvFilmic: TextView? = null
    private var btnOvVivid: TextView? = null
    private var btnOvShadowBoost: TextView? = null
    private var btnOvColorTemp: TextView? = null
    private var btnOvFlip180: TextView? = null
    private var btnOvSharpen: TextView? = null
    private var shortsStudioSection: LinearLayout? = null
    private var btnOvShortsAnchor: TextView? = null
    private var btnOvShortsHeader: TextView? = null
    private var btnOvShortsLoupesPause: TextView? = null
    private var btnOvShortsScoreLoupe: TextView? = null
    private var btnOvShortsMinimapLoupe: TextView? = null
    private var btnOvOverlayPos: TextView? = null
    private var btnOvLiveBanner: TextView? = null
    private var btnOvCleanShot: TextView? = null
    private var btnOvHudTelemetry: TextView? = null
    private var btnOvQrOverlay: TextView? = null
    private var btnOvPngTuber: TextView? = null
    private var btnOvMinimapShield: TextView? = null
    private var btnOvKeyboardBlur: TextView? = null

    // Tab 2 (Bitrate / Net / Honest App CPU & Thermal Pressure Split)
    private var tvOvBitrateReadout: TextView? = null
    private var btnOvAbrToggle: TextView? = null
    private var btnOvThermal30Saver: TextView? = null
    private var tvOvFpsAndThermalReadout: TextView? = null
    private var tvOvQueueAndRttReadout: TextView? = null
    private var tvOvHeatSplitReadout: TextView? = null
    private var tvOvRamSplitReadout: TextView? = null
    private var tvOvSimulcastStatus: TextView? = null
    private var btnOvStopConfirm: TextView? = null

    private val ghostAlphaRunnable = Runnable {
        if (!pillExpanded) {
            pillRootView?.animate()?.alpha(0.38f)?.setDuration(300)?.start()
        }
    }

    private val stopConfirmResetRunnable = Runnable {
        btnOvStopConfirm?.text = "⏹ Stop Stream"
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).roundToInt()

    private fun clampOverlayCoords(rawX: Int, rawY: Int, useExpandedWidth: Boolean = false): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        val widthReserve = if (useExpandedWidth && expandedPanelWidthPx > 0) {
            expandedPanelWidthPx + dp(20)
        } else {
            dp(72)
        }
        val maxX = max(0, dm.widthPixels - widthReserve)
        val maxY = max(0, dm.heightPixels - dp(48))
        return rawX.coerceIn(0, maxX) to rawY.coerceIn(0, maxY)
    }

    private fun ensurePillWithinCurrentScreenBounds(useExpandedWidth: Boolean = pillExpanded) {
        val root = pillRootView ?: return
        val lp = pillLayoutParams ?: return
        val (clampedX, clampedY) = clampOverlayCoords(lp.x, lp.y, useExpandedWidth = useExpandedWidth)
        if (clampedX != lp.x || clampedY != lp.y) {
            lp.x = clampedX
            lp.y = clampedY
            try {
                windowManager.updateViewLayout(root, lp)
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        if (!SolStreamService.isAccessibilityEnabled(context) || pillRootView != null) return

        val screenMinPx = min(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        expandedPanelWidthPx = min(dp(344), (screenMinPx * 0.93f).roundToInt().coerceAtLeast(dp(280)))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F4050A14"), Color.parseColor("#F40B1324"))
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#7300E5FF"))
            }
            setPadding(dp(10), dp(6), dp(10), dp(6))
            alpha = 0.95f
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = TextView(context).apply {
            text = "●"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, dp(5), 0)
        }
        val statusTv = TextView(context).apply {
            text = "00:00 | Starting..."
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        header.addView(dot)
        header.addView(statusTv)
        root.addView(header)

        val drawer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(6), 0, dp(2))
            layoutParams = LinearLayout.LayoutParams(expandedPanelWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Top Quick-Action Bar: 4 weighted buttons + compact fixed-width ✕ button
        btnQuickMicMute = makePillMiniBtn("Mic:ON", "#00E5FF", padHdp = 4, textSp = 9.5f) { actions.onToggleMicMute() }
        btnQuickGameMute = makePillMiniBtn("Game:ON", "#00E5FF", padHdp = 4, textSp = 9.5f) { actions.onToggleGameMute() }
        btnQuickScene = makePillMiniBtn("LIVE", "#00E5FF", padHdp = 4, textSp = 9.5f) { actions.onCycleScene() }
        btnQuickMarkClip = makePillMiniBtn("★ Clip", "#38BDF8", padHdp = 4, textSp = 9.5f) { actions.onMarkClip() }
        val btnCollapse = makePillMiniBtn("✕", "#94A3B8", padHdp = 4, textSp = 10.0f) { toggleDrawer(false) }

        val quickRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
            val actionButtons = listOf(btnQuickMicMute!!, btnQuickGameMute!!, btnQuickScene!!, btnQuickMarkClip!!)
            for (btn in actionButtons) {
                btn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(4)
                }
                addView(btn)
            }
            btnCollapse.layoutParams = LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(btnCollapse)
        }
        drawer.addView(quickRow)

        val tabBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(4))
        }
        tabBtnAudio = makePillTabHeader("🎙️ Audio", true) { switchOverlayTab(0) }
        tabBtnVideo = makePillTabHeader("🎬 Video", false) { switchOverlayTab(1) }
        tabBtnNet = makePillTabHeader("📡 Bitrate/Net", false) { switchOverlayTab(2) }
        tabBar.addView(tabBtnAudio, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(3) })
        tabBar.addView(tabBtnVideo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(3) })
        tabBar.addView(tabBtnNet, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        drawer.addView(tabBar)

        val vScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
        }
        val panesHolder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // === TAB 0: 🎙️ AUDIO STUDIO + SOUNDSTAGE ===
        val audioPane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
        }
        audioPane.addView(makePillSectionMicroHeader("SOLLUXE™ 3.0 PURE VOICE & DENOISER"))
        btnOvGender = makePillMiniBtn("Voice:NEUTRAL", "#00E5FF") { actions.onCycleVoiceGender() }
        btnOvVoiceType = makePillMiniBtn("Type:SOL_LUXE", "#00E5FF") { actions.onCycleVoicePreset() }
        audioPane.addView(makeAlignedGridRow(btnOvGender!!, btnOvVoiceType!!))

        btnOvNsMode = makePillMiniBtn("NS:RNNOISE", "#00E5FF") { actions.onCycleNsMode() }
        btnOvNsLevel = makePillMiniBtn("Lv:2 (88%)", "#00E5FF") { actions.onCycleRnnoiseLevel() }
        btnOvHpf = makePillMiniBtn("HPF:68Hz", "#38BDF8") { actions.onCycleHpf() }
        audioPane.addView(makeAlignedGridRow(btnOvNsMode!!, btnOvNsLevel!!, btnOvHpf!!))

        audioPane.addView(makePillSectionMicroHeader("STUDIO POLISH & VOLUME MIX"))
        btnOvEq = makePillMiniBtn("EQ:ON", "#00E5FF") { actions.onToggleVoiceEq() }
        btnOvDeEss = makePillMiniBtn("DeEss:ON", "#00E5FF") { actions.onToggleDeEsser() }
        btnOvVelvet = makePillMiniBtn("Warm:ON", "#00E5FF") { actions.onToggleVelvet() }
        btnOvAgc = makePillMiniBtn("AGC:ON", "#00E5FF") { actions.onToggleSlowAgc() }
        audioPane.addView(makeAlignedGridRow(btnOvEq!!, btnOvDeEss!!, btnOvVelvet!!, btnOvAgc!!))

        val btnMicDown = makePillMiniBtn("Mic −10%", "#38BDF8") { actions.onStepMicBoost(-10) }
        btnOvMicGainLabel = makePillMiniBtn("🎙️ Mic:130%", "#00E5FF") { actions.onStepMicBoost(10) }
        val btnMicUp = makePillMiniBtn("Mic +10%", "#38BDF8") { actions.onStepMicBoost(10) }
        audioPane.addView(makeAlignedGridRow(btnMicDown, btnOvMicGainLabel!!, btnMicUp))

        val btnGameDown = makePillMiniBtn("Game −10%", "#38BDF8") { actions.onStepGameMix(-10) }
        btnOvGameGainLabel = makePillMiniBtn("🎮 Game:100%", "#00E5FF") { actions.onStepGameMix(10) }
        val btnGameUp = makePillMiniBtn("Game +10%", "#38BDF8") { actions.onStepGameMix(10) }
        audioPane.addView(makeAlignedGridRow(btnGameDown, btnOvGameGainLabel!!, btnGameUp))

        audioPane.addView(makePillSectionMicroHeader("3D GAME SOUNDSTAGE & MIC ROUTE"))
        btnOvCinemaWide = makePillMiniBtn("3DWide:OFF", "#94A3B8") { actions.onToggleGameCinemaWide() }
        btnOvSubPunch = makePillMiniBtn("Sub:OFF", "#94A3B8") { actions.onToggleGameSubPunch() }
        btnOvFootstep = makePillMiniBtn("Foot:OFF", "#94A3B8") { actions.onToggleGameFootstep() }
        audioPane.addView(makeAlignedGridRow(btnOvCinemaWide!!, btnOvSubPunch!!, btnOvFootstep!!))

        btnOvBlastTamer = makePillMiniBtn("Blast:OFF", "#94A3B8") { actions.onToggleGameBlastTamer() }
        btnOvDucking = makePillMiniBtn("Duck:OFF", "#94A3B8") { actions.onCycleDucking() }
        btnOvMicRoute = makePillMiniBtn("Mic:Phone Studio", "#38BDF8") { actions.onCycleMicHardware() }
        audioPane.addView(makeAlignedGridRow(btnOvBlastTamer!!, btnOvDucking!!, btnOvMicRoute!!))

        // === TAB 1: 🎬 VIDEO, SOLSHORTS™ STUDIO, PRO OVERLAYS, SCREENSHOT & DUAL PRIVACY BLUR ===
        val videoPane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        videoPane.addView(makePillSectionMicroHeader("SOLCORE™ FRAMING & UPSCALE"))
        btnOvSceneSelect = makePillMiniBtn("Scene:LIVE", "#00E5FF") { actions.onCycleScene() }
        btnOvFraming = makePillMiniBtn("Frame:FIT", "#38BDF8") { actions.onCycleFramingMode() }
        btnOvZoom = makePillMiniBtn("Zoom:108%", "#38BDF8") { actions.onCycleSmartZoom() }
        videoPane.addView(makeAlignedGridRow(btnOvSceneSelect!!, btnOvFraming!!, btnOvZoom!!))

        btnOvUpscaler = makePillMiniBtn("Scale:BICUBIC", "#38BDF8") { actions.onCycleUpscaler() }
        btnOvSharpen = makePillMiniBtn("Sharp:OFF", "#94A3B8") { actions.onToggleGpuSharpen() }
        btnOvFlip180 = makePillMiniBtn("Rot:0°", "#38BDF8") { actions.onToggleLandscapeFlip() }
        videoPane.addView(makeAlignedGridRow(btnOvUpscaler!!, btnOvSharpen!!, btnOvFlip180!!))

        // v1.6.1 SolShorts™ Studio live controls (auto-shown for 9:16 Shorts & Dual-Format Companion)
        val shortsSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        shortsSection.addView(makePillSectionMicroHeader("SOLSHORTS™ 9:16 STUDIO (OPT-IN)"))
        btnOvShortsAnchor = makePillMiniBtn("Pos:CENTER", "#38BDF8") { actions.onCycleShortsAnchor() }
        btnOvShortsHeader = makePillMiniBtn("Header:OFF", "#94A3B8") { actions.onToggleShortsHeaderLayout() }
        shortsSection.addView(makeAlignedGridRow(btnOvShortsAnchor!!, btnOvShortsHeader!!))

        btnOvShortsLoupesPause = makePillMiniBtn("Loupes:OFF", "#94A3B8") { actions.onToggleLoupesPaused() }
        btnOvShortsScoreLoupe = makePillMiniBtn("FeedL:OFF", "#94A3B8") { actions.onCycleShortsScoreLoupe() }
        btnOvShortsMinimapLoupe = makePillMiniBtn("MapL:OFF", "#94A3B8") { actions.onCycleShortsMinimapLoupe() }
        shortsSection.addView(makeAlignedGridRow(btnOvShortsLoupesPause!!, btnOvShortsScoreLoupe!!, btnOvShortsMinimapLoupe!!))
        shortsStudioSection = shortsSection
        videoPane.addView(shortsSection)

        videoPane.addView(makePillSectionMicroHeader("STACKABLE GPU COLOR GRADING"))
        btnOvFilmic = makePillMiniBtn("Filmic:OFF", "#94A3B8") { actions.onToggleCinematicFilmic() }
        btnOvVivid = makePillMiniBtn("Vivid:OFF", "#94A3B8") { actions.onToggleAmoledVivid() }
        btnOvShadowBoost = makePillMiniBtn("Shadow+:OFF", "#94A3B8") { actions.onToggleShadowBoost() }
        btnOvColorTemp = makePillMiniBtn("Temp:NEUT", "#38BDF8") { actions.onCycleColorTemp() }
        videoPane.addView(makeAlignedGridRow(btnOvFilmic!!, btnOvVivid!!, btnOvShadowBoost!!, btnOvColorTemp!!))

        videoPane.addView(makePillSectionMicroHeader("PRO OVERLAYS, HUD & THUMBNAIL"))
        btnOvOverlayPos = makePillMiniBtn("Logo:OFF", "#94A3B8") { actions.onCycleOverlayPosition() }
        btnOvLiveBanner = makePillMiniBtn("Banner:OFF", "#94A3B8") { actions.onToggleLiveBanner() }
        btnOvCleanShot = makePillMiniBtn("📸 Shot", "#38BDF8") { actions.onTriggerCleanScreenshot() }
        videoPane.addView(makeAlignedGridRow(btnOvOverlayPos!!, btnOvLiveBanner!!, btnOvCleanShot!!))

        btnOvHudTelemetry = makePillMiniBtn("HUD:OFF", "#94A3B8") { actions.onToggleHudTelemetry() }
        btnOvQrOverlay = makePillMiniBtn("QR:OFF", "#94A3B8") { actions.onToggleQrOverlay() }
        btnOvPngTuber = makePillMiniBtn("Avatar:OFF", "#94A3B8") { actions.onTogglePngTuber() }
        videoPane.addView(makeAlignedGridRow(btnOvHudTelemetry!!, btnOvQrOverlay!!, btnOvPngTuber!!))

        videoPane.addView(makePillSectionMicroHeader("DUAL PRIVACY SHIELD"))
        btnOvMinimapShield = makePillMiniBtn("MapBlur:OFF", "#94A3B8") { actions.onToggleMinimapShield() }
        btnOvKeyboardBlur = makePillMiniBtn("KeyBlur:AUTO", "#00E5FF") { actions.onToggleManualKeyboardBlur() }
        videoPane.addView(makeAlignedGridRow(btnOvMinimapShield!!, btnOvKeyboardBlur!!))

        // === TAB 2: 📡 BITRATE / NET / HONEST CPU & THERMAL PRESSURE SPLIT / STOP ===
        val netPane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        netPane.addView(makePillSectionMicroHeader("SOLRELAY™ BITRATE & THERMAL COOLER"))
        val btnBitDown = makePillMiniBtn("− Mbps", "#38BDF8") { actions.onStepLiveBitrate(-1) }
        tvOvBitrateReadout = makePillMiniBtn("📡 20.0 Mbps", "#00E5FF") {}
        val btnBitUp = makePillMiniBtn("+ Mbps", "#38BDF8") { actions.onStepLiveBitrate(1) }
        netPane.addView(makeAlignedGridRow(btnBitDown, tvOvBitrateReadout!!, btnBitUp))

        btnOvAbrToggle = makePillMiniBtn("ABR:ON", "#00E5FF") { actions.onToggleAbr() }
        btnOvThermal30Saver = makePillMiniBtn("30FPS Cool:AUTO", "#00E5FF") { actions.onToggleThermal30FpsSaver() }
        netPane.addView(makeAlignedGridRow(btnOvAbrToggle!!, btnOvThermal30Saver!!))

        netPane.addView(makePillSectionMicroHeader("LIVE STREAM & HARDWARE TELEMETRY"))
        tvOvFpsAndThermalReadout = makePillMiniBtn("🎥 Stream: 60fps (Game: 60) • Thermal: 50%", "#E2E8F0") {}
        tvOvQueueAndRttReadout = makePillMiniBtn("📡 Queue(A+V): 0f (0ms enq) • RTT: --", "#38BDF8") {}
        tvOvHeatSplitReadout = makePillMiniBtn("🔥 App CPU: ~4% | Thermal Pressure: 50%", "#00E5FF") {}
        tvOvRamSplitReadout = makePillMiniBtn("💾 RAM: App 180MB | Game+OS 5.5GB (of 8GB)", "#38BDF8") {}
        netPane.addView(makeAlignedGridRow(tvOvFpsAndThermalReadout!!))
        netPane.addView(makeAlignedGridRow(tvOvQueueAndRttReadout!!))
        netPane.addView(makeAlignedGridRow(tvOvHeatSplitReadout!!))
        netPane.addView(makeAlignedGridRow(tvOvRamSplitReadout!!))

        tvOvSimulcastStatus = makePillMiniBtn("Targets: Primary", "#94A3B8") {}
        btnOvStopConfirm = makePillMiniBtn("⏹ Stop Stream", "#FF1744") { onStopButtonTapped() }
        netPane.addView(makeAlignedGridRow(tvOvSimulcastStatus!!, btnOvStopConfirm!!))

        tabPaneAudio = audioPane
        tabPaneVideo = videoPane
        tabPaneNet = netPane

        panesHolder.addView(audioPane)
        panesHolder.addView(videoPane)
        panesHolder.addView(netPane)
        vScroll.addView(panesHolder)
        drawer.addView(vScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(168)))

        root.addView(drawer)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                notifyImeVisibilityIfChanged(insets.isVisible(WindowInsets.Type.ime()))
                insets
            }
        }

        val savedPos = loadOverlayPositionPrefs()
        val (clampedX, clampedY) = clampOverlayCoords(savedPos.first, savedPos.second, useExpandedWidth = false)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = clampedX
            y = clampedY
        }

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startWinX = 0
        var startWinY = 0
        var dragging = false
        var lastTapUpMs = 0L

        header.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mainHandler.removeCallbacks(ghostAlphaRunnable)
                    root.alpha = 0.97f
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startWinX = lp.x
                    startWinY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downRawX).roundToInt()
                    val dy = (ev.rawY - downRawY).roundToInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        dragging = true
                        val (nx, ny) = clampOverlayCoords(startWinX + dx, startWinY + dy, useExpandedWidth = pillExpanded)
                        lp.x = nx
                        lp.y = ny
                        try { windowManager.updateViewLayout(root, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        saveOverlayPositionPrefs(lp.x, lp.y)
                        scheduleGhostFade()
                    } else {
                        val now = SystemClock.uptimeMillis()
                        val holdMs = now - ev.downTime
                        if (now - lastTapUpMs < 360L || holdMs >= 420L) {
                            toggleDrawer(!pillExpanded)
                            lastTapUpMs = 0L
                        } else {
                            lastTapUpMs = now
                            scheduleGhostFade()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(root, lp)
            pillRootView = root
            pillHeaderRow = header
            pillStatusDot = dot
            pillStatusText = statusTv
            pillDrawerContainer = drawer
            pillLayoutParams = lp
            lastObservedImeVisible = false
            attachInvisibleImeDetectorView()
            refreshExpandedControls()
            updateTelemetry()
            scheduleGhostFade()
        } catch (e: Exception) {
            DiagnosticsLogger.log("OVERLAY_ERR", "Could not attach floating pill (${e.javaClass.simpleName}).")
        }
    }

    private fun notifyImeVisibilityIfChanged(visible: Boolean) {
        if (visible == lastObservedImeVisible) return
        lastObservedImeVisible = visible
        actions.onImeKeyboardVisibilityChanged(visible)
        if (pillExpanded) {
            mainHandler.post { refreshExpandedControls() }
        }
    }

    /**
- Orientation-safe IME keyboard detector:
- - Android 11+ (API 30+): strictly uses WindowInsets.Type.ime() (never false-triggers on screen rotation).
- - Android 10 (API 29): tracks max viewport height separately per portrait/landscape orientation.
     */
    private fun attachInvisibleImeDetectorView() {
        if (imeDetectorView != null) return
        try {
            var maxPortraitHeight = 0
            var maxLandscapeHeight = 0
            val detector = View(context).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setOnApplyWindowInsetsListener { _, insets ->
                        notifyImeVisibilityIfChanged(insets.isVisible(WindowInsets.Type.ime()))
                        insets
                    }
                } else {
                    addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                        val h = bottom - top
                        if (h <= 0) return@addOnLayoutChangeListener
                        val isLand = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        val baseline = if (isLand) {
                            if (h > maxLandscapeHeight) maxLandscapeHeight = h
                            maxLandscapeHeight
                        } else {
                            if (h > maxPortraitHeight) maxPortraitHeight = h
                            maxPortraitHeight
                        }
                        if (baseline > 400) {
                            val shrunkByKeyboard = h < (baseline * 0.80f)
                            notifyImeVisibilityIfChanged(shrunkByKeyboard)
                        }
                    }
                }
            }
            @Suppress("DEPRECATION")
            val detLp = WindowManager.LayoutParams(
                1,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            windowManager.addView(detector, detLp)
            imeDetectorView = detector
        } catch (_: Exception) {
        }
    }

    fun detach() {
        mainHandler.removeCallbacks(ghostAlphaRunnable)
        mainHandler.removeCallbacks(stopConfirmResetRunnable)
        pillExpanded = false
        lastStopConfirmTapMs = 0L
        lastObservedImeVisible = false

        val det = imeDetectorView
        imeDetectorView = null
        if (det != null) {
            try { windowManager.removeView(det) } catch (_: Exception) {}
        }

        val view = pillRootView
        pillRootView = null
        if (view != null) {
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }

        // Release all detached view references cleanly so zero UI memory lingers between sessions
        pillHeaderRow = null; pillStatusDot = null; pillStatusText = null
        pillDrawerContainer = null; pillLayoutParams = null
        tabBtnAudio = null; tabBtnVideo = null; tabBtnNet = null
        tabPaneAudio = null; tabPaneVideo = null; tabPaneNet = null
        btnQuickMicMute = null; btnQuickGameMute = null; btnQuickScene = null; btnQuickMarkClip = null
        btnOvGender = null; btnOvVoiceType = null; btnOvNsMode = null; btnOvNsLevel = null
        btnOvHpf = null; btnOvEq = null; btnOvDeEss = null; btnOvVelvet = null; btnOvAgc = null
        btnOvMicGainLabel = null; btnOvGameGainLabel = null; btnOvCinemaWide = null
        btnOvSubPunch = null; btnOvFootstep = null; btnOvBlastTamer = null
        btnOvDucking = null; btnOvMicRoute = null
        btnOvSceneSelect = null; btnOvUpscaler = null; btnOvFraming = null; btnOvZoom = null
        btnOvFilmic = null; btnOvVivid = null; btnOvShadowBoost = null; btnOvColorTemp = null
        btnOvFlip180 = null; btnOvSharpen = null
        shortsStudioSection = null; btnOvShortsAnchor = null; btnOvShortsHeader = null
        btnOvShortsLoupesPause = null; btnOvShortsScoreLoupe = null; btnOvShortsMinimapLoupe = null
        btnOvOverlayPos = null; btnOvLiveBanner = null
        btnOvCleanShot = null; btnOvHudTelemetry = null; btnOvQrOverlay = null; btnOvPngTuber = null
        btnOvMinimapShield = null; btnOvKeyboardBlur = null
        tvOvBitrateReadout = null; btnOvAbrToggle = null; btnOvThermal30Saver = null
        tvOvFpsAndThermalReadout = null; tvOvQueueAndRttReadout = null
        tvOvHeatSplitReadout = null; tvOvRamSplitReadout = null
        tvOvSimulcastStatus = null; btnOvStopConfirm = null
    }

    fun updateTelemetry() {
        val statusTv = pillStatusText ?: return
        val dot = pillStatusDot ?: return
        ensurePillWithinCurrentScreenBounds(useExpandedWidth = pillExpanded)
        val snap = actions.getSnapshot()

        val mm = snap.elapsedSec / 60L
        val ss = snap.elapsedSec % 60L
        val timeStr = String.format(Locale.US, "%02d:%02d", mm, ss)
        val tempStr = if (snap.liveTempC > 0f) String.format(Locale.US, "%.1f°C", snap.liveTempC) else "N/A"

        if (snap.isBenchmarkOnly) {
            dot.setTextColor(Color.parseColor("#F59E0B"))
            statusTv.text = "BENCH $timeStr | $tempStr | RAM ${snap.peakPssMb}MB"
            return
        }

        val mbps = snap.encodedBitrateBps / 1_000_000.0
        val micShort = if (snap.micMuted) "Mic:MUTE" else "Mic:ON"
        val sceneTag = if (snap.sceneMode != "LIVE") " [${snap.sceneMode}]" else ""

        val dotColor = when {
            !snap.rtmpConnected -> "#F59E0B"
            snap.worstThermalState >= PowerManager.THERMAL_STATUS_SEVERE -> "#FF1744"
            else -> "#00E5FF"
        }
        dot.setTextColor(Color.parseColor(dotColor))

        val netState = if (snap.rtmpConnected) {
            String.format(Locale.US, "%.1fM", mbps)
        } else {
            "RECONN"
        }
        val summaryLine = "$timeStr | $netState | ${snap.encodedFps}fps | $micShort | $tempStr$sceneTag"
        statusTv.text = summaryLine
        actions.onLiveStateLabelChanged("LIVE ($summaryLine)")

        if (pillExpanded) {
            refreshExpandedControls(snap)
        }
    }

    fun refreshExpandedControls(providedSnap: PillTelemetrySnapshot? = null) {
        if (pillRootView == null) return
        val snap = providedSnap ?: actions.getSnapshot()
        val s = snap.activeSettings

        stylePillControlBtn(
            btnQuickMicMute,
            label = if (snap.micMuted) "Mic:OFF" else "Mic:ON",
            isOn = !snap.micMuted,
            isDanger = snap.micMuted
        )

        stylePillControlBtn(
            btnQuickGameMute,
            label = if (snap.gameMuted) "Game:OFF" else "Game:ON",
            isOn = !snap.gameMuted,
            isDanger = snap.gameMuted
        )

        val sceneShort = when (snap.sceneMode) {
            "STARTING_SOON" -> "START"
            "BRB" -> "BRB"
            else -> "LIVE"
        }
        stylePillControlBtn(btnQuickScene, sceneShort, isOn = (snap.sceneMode == "LIVE"), isSelector = (snap.sceneMode != "LIVE"))
        stylePillControlBtn(btnOvSceneSelect, "Scene:${snap.sceneMode}", isOn = (snap.sceneMode == "LIVE"), isSelector = (snap.sceneMode != "LIVE"))

        stylePillControlBtn(
            btnQuickMarkClip,
            label = if (snap.clipCount > 0) "★(${snap.clipCount})" else "★ Clip",
            isOn = snap.clipCount > 0,
            isSelector = true
        )

        // Tab 0 (Audio)
        stylePillControlBtn(btnOvGender, "Voice:${s.voiceGenderProfile}", isOn = false, isSelector = true)
        stylePillControlBtn(btnOvVoiceType, "Type:${s.voicePreset}", isOn = (s.voicePreset != "RAW"), isSelector = true)

        val nsActive = s.noiseSuppressMode != "OFF" && s.rnnoiseLevel > 0
        stylePillControlBtn(btnOvNsMode, "NS:${s.noiseSuppressMode}", isOn = nsActive)
        val lvlPct = when (s.rnnoiseLevel) {
            1 -> "Lv:1 (65%)"
            2 -> "Lv:2 (88%)"
            3 -> "Lv:3 (100%)"
            else -> "Lv:OFF"
        }
        stylePillControlBtn(btnOvNsLevel, lvlPct, isOn = s.rnnoiseLevel > 0)
        val hpfTag = when {
            s.hpfCutoffHz == 0 -> "HPF:OFF"
            s.hpfAutoMode -> "HPF:${s.hpfCutoffHz}Hz"
            else -> "HPF:${s.hpfCutoffHz}M"
        }
        stylePillControlBtn(btnOvHpf, label = hpfTag, isOn = s.hpfCutoffHz > 0)

        stylePillControlBtn(btnOvEq, if (s.voiceEqEnabled) "EQ:ON" else "EQ:OFF", isOn = s.voiceEqEnabled)
        stylePillControlBtn(btnOvDeEss, if (s.deEsserEnabled) "DeEss:ON" else "DeEss:OFF", isOn = s.deEsserEnabled)
        stylePillControlBtn(btnOvVelvet, if (s.velvetWarmthEnabled) "Warm:ON" else "Warm:OFF", isOn = s.velvetWarmthEnabled)
        stylePillControlBtn(btnOvAgc, if (s.slowAgcEnabled) "AGC:ON" else "AGC:OFF", isOn = s.slowAgcEnabled)

        stylePillControlBtn(btnOvMicGainLabel, "🎙️ Mic:${s.micGainPercent}%", isOn = true)
        stylePillControlBtn(btnOvGameGainLabel, "🎮 Game:${s.gameGainPercent}%", isOn = s.gameGainPercent > 0)

        stylePillControlBtn(btnOvCinemaWide, if (s.gameCinemaWideEnabled) "3DWide:ON" else "3DWide:OFF", isOn = s.gameCinemaWideEnabled)
        stylePillControlBtn(btnOvSubPunch, if (s.gameSubPunchEnabled) "Sub:ON" else "Sub:OFF", isOn = s.gameSubPunchEnabled)
        stylePillControlBtn(btnOvFootstep, if (s.gameFootstepFocusEnabled) "Foot:ON" else "Foot:OFF", isOn = s.gameFootstepFocusEnabled)
        stylePillControlBtn(btnOvBlastTamer, if (s.gameBlastTamerEnabled) "Blast:ON" else "Blast:OFF", isOn = s.gameBlastTamerEnabled)
        stylePillControlBtn(
            btnOvDucking,
            label = if (s.vadDuckingPercent == 0) "Duck:OFF" else "Duck:-${s.vadDuckingPercent}%",
            isOn = s.vadDuckingPercent > 0
        )
        stylePillControlBtn(btnOvMicRoute, "Mic:${snap.shortMicPillLabel}", isOn = false, isSelector = true)

        // Tab 1 (Video, SolShorts™ Studio & Phase 1 Pro Overlays)
        stylePillControlBtn(btnOvUpscaler, "Scale:${s.upscalerMode}", isOn = s.upscalerMode != "BILINEAR", isSelector = true)
        stylePillControlBtn(btnOvFraming, "Frame:${s.framingMode}", isOn = s.framingMode != "FIT", isSelector = true)
        stylePillControlBtn(btnOvZoom, "Zoom:${s.smartZoomPercent}%", isOn = s.framingMode == "SMART" && s.smartZoomPercent > 100, isSelector = true)
        stylePillControlBtn(btnOvFilmic, if (s.cinematicFilmic) "Filmic:ON" else "Filmic:OFF", isOn = s.cinematicFilmic)
        stylePillControlBtn(btnOvVivid, if (s.amoledVivid) "Vivid:ON" else "Vivid:OFF", isOn = s.amoledVivid)
        stylePillControlBtn(btnOvShadowBoost, if (s.shadowBoost) "Shadow+:ON" else "Shadow+:OFF", isOn = s.shadowBoost)
        stylePillControlBtn(btnOvColorTemp, "Temp:${s.colorTempFilter.take(4)}", isOn = s.colorTempFilter != "NEUTRAL", isSelector = true)
        stylePillControlBtn(btnOvFlip180, "Rot:${s.landscapeFlip}°", isOn = s.landscapeFlip == 180, isSelector = true)
        stylePillControlBtn(btnOvSharpen, if (s.gpuSharpen) "Sharp:ON" else "Sharp:OFF", isOn = s.gpuSharpen)

        val showShortsControls = s.isVertical || s.dualFormatCompanionEnabled
        shortsStudioSection?.visibility = if (showShortsControls) View.VISIBLE else View.GONE
        if (showShortsControls) {
            val anchorAbove = s.shortsVerticalAnchor.equals("ABOVE_CHAT", ignoreCase = true)
            stylePillControlBtn(
                btnOvShortsAnchor,
                label = SolStreamService.formatCompactShortsAnchorTag(s.shortsVerticalAnchor),
                isOn = anchorAbove,
                isSelector = !anchorAbove
            )
            stylePillControlBtn(
                btnOvShortsHeader,
                label = if (s.shortsHeaderLayout) "Header:ON" else "Header:OFF",
                isOn = s.shortsHeaderLayout
            )

            val mapSuppressed = SolVideoPipeline.isMinimapLoupeSuppressedByShield(
                s.shortsMinimapLoupe,
                s.minimapShieldEnabled,
                s.minimapShieldCorner
            )
            val scoreOn = !s.shortsScoreLoupe.equals("OFF", ignoreCase = true)
            val mapActive = !s.shortsMinimapLoupe.equals("OFF", ignoreCase = true) && !mapSuppressed
            val hasAnyLoupeConfigured = scoreOn || mapActive

            val loupesPauseLabel = when {
                !hasAnyLoupeConfigured -> "Loupes:OFF"
                snap.loupesPaused -> "Loupes:HIDE"
                else -> "Loupes:ON"
            }
            stylePillControlBtn(
                btnOvShortsLoupesPause,
                label = loupesPauseLabel,
                isOn = hasAnyLoupeConfigured && !snap.loupesPaused,
                isSelector = hasAnyLoupeConfigured && snap.loupesPaused
            )
            stylePillControlBtn(
                btnOvShortsScoreLoupe,
                label = SolStreamService.formatCompactShortsScoreLoupeTag(s.shortsScoreLoupe),
                isOn = scoreOn && !snap.loupesPaused,
                isSelector = scoreOn && snap.loupesPaused
            )
            stylePillControlBtn(
                btnOvShortsMinimapLoupe,
                label = SolStreamService.formatCompactShortsMinimapLoupeTag(s.shortsMinimapLoupe, mapSuppressed),
                isOn = mapActive && !snap.loupesPaused,
                isSelector = mapSuppressed || (mapActive && snap.loupesPaused)
            )
        }

        val logoLabel = SolStreamService.formatCompactOverlayPositionTag(s.overlayImageEnabled, s.overlayPosition)
        stylePillControlBtn(btnOvOverlayPos, label = logoLabel, isOn = s.overlayImageEnabled)
        stylePillControlBtn(btnOvLiveBanner, if (s.liveBannerEnabled) "Banner:ON" else "Banner:OFF", isOn = s.liveBannerEnabled)
        stylePillControlBtn(btnOvCleanShot, "📸 Shot", isOn = false, isSelector = true)

        val hudCornerShort = SolStreamService.formatCompactCornerShortTag(s.hudTelemetryCorner)
        stylePillControlBtn(
            btnOvHudTelemetry,
            label = if (s.hudTelemetryEnabled) "HUD:$hudCornerShort" else "HUD:OFF",
            isOn = s.hudTelemetryEnabled
        )
        val qrActive = s.qrOverlayEnabled && s.qrOverlayText.isNotBlank()
        val qrCornerShort = SolStreamService.formatCompactCornerShortTag(s.qrOverlayCorner)
        stylePillControlBtn(
            btnOvQrOverlay,
            label = if (qrActive) "QR:$qrCornerShort" else "QR:OFF",
            isOn = qrActive
        )
        val avatarCornerShort = SolStreamService.formatCompactCornerShortTag(s.pngTuberCorner)
        stylePillControlBtn(
            btnOvPngTuber,
            label = if (s.pngTuberEnabled) "Avatar:$avatarCornerShort" else "Avatar:OFF",
            isOn = s.pngTuberEnabled
        )

        stylePillControlBtn(
            btnOvMinimapShield,
            label = if (s.minimapShieldEnabled) "MapBlur:${s.minimapShieldCorner.take(8)}" else "MapBlur:OFF",
            isOn = s.minimapShieldEnabled
        )

        val kbLabel = when {
            snap.manualKeyboardBlurOverride -> "KeyBlur:ON (Locked)"
            snap.keyboardBlurActive -> "KeyBlur:ACTIVE"
            s.autoBlurKeyboard -> "KeyBlur:AUTO"
            else -> "KeyBlur:OFF"
        }
        stylePillControlBtn(btnOvKeyboardBlur, kbLabel, isOn = snap.keyboardBlurActive || s.autoBlurKeyboard)

        // Tab 2 (Net / Bitrate / Honest Separated CPU & Thermal Pressure Readouts)
        stylePillControlBtn(
            tvOvBitrateReadout,
            String.format(Locale.US, "📡 %.1f Mbps", snap.activeBitrateBps / 1_000_000.0),
            isOn = true
        )
        stylePillControlBtn(btnOvAbrToggle, if (s.adaptiveBitrate) "ABR:ON" else "ABR:OFF", isOn = s.adaptiveBitrate)

        val saver30 = snap.thermalThrottle30Fps || snap.manual30FpsSaverEnabled || snap.autoThermalNative30BitrateTrimActive
        val saverLabel = when {
            snap.manual30FpsSaverEnabled -> "30FPS Cool:ON"
            saver30 -> "30FPS Cool:ACTIVE"
            else -> "30FPS Cool:AUTO"
        }
        stylePillControlBtn(btnOvThermal30Saver, saverLabel, isOn = saver30, isSelector = !saver30)

        tvOvFpsAndThermalReadout?.text =
            "🎥 Stream: ${snap.encodedFps}fps (Game: ${snap.freshCaptureFps}) • Thermal: ${snap.thermalPressurePercent}%"

        val qFrames = max(0, snap.queueFrames)
        val enqMs = max(0L, snap.enqueueLatencyMs)
        val rttLabel = if (snap.rttSourceLabel.isNotBlank()) snap.rttSourceLabel else "RTT"
        val pingStr = if (snap.rttMs > 0L) "${snap.rttMs}ms" else "--"
        tvOvQueueAndRttReadout?.text = "📡 Queue(A+V): ${qFrames}f (${enqMs}ms enq) • $rttLabel: $pingStr"

        val appCpu = snap.appCpuPercent.coerceIn(1, 100)
        val tempStr = if (snap.liveTempC > 0f) String.format(Locale.US, "%.1f°C", snap.liveTempC) else "N/A"
        tvOvHeatSplitReadout?.text = "🔥 App CPU: ~${appCpu}% | Thermal Pressure: ${snap.thermalPressurePercent}% ($tempStr)"

        val totalUsedGb = (snap.totalRamGb.toFloat() - snap.availRamGb).coerceAtLeast(0.5f)
        val gameOsRamGb = (totalUsedGb - (snap.appRamMb / 1024.0f)).coerceAtLeast(0.3f)
        tvOvRamSplitReadout?.text = String.format(
            Locale.US,
            "💾 RAM: App %dMB | Game+OS %.1fGB (of %dGB)",
            snap.appRamMb,
            gameOsRamGb,
            snap.totalRamGb
        )

        stylePillControlBtn(
            tvOvSimulcastStatus,
            label = if (snap.activeAuxCount > 0) "Targets: Primary + ${snap.activeAuxCount}" else "Targets: Primary Only",
            isOn = snap.activeAuxCount > 0
        )
        stylePillControlBtn(btnOvStopConfirm, btnOvStopConfirm?.text?.toString() ?: "⏹ Stop Stream", isOn = false, isDanger = true)
    }

    private fun toggleDrawer(expand: Boolean) {
        pillExpanded = expand
        pillDrawerContainer?.visibility = if (expand) View.VISIBLE else View.GONE
        if (expand) {
            mainHandler.removeCallbacks(ghostAlphaRunnable)
            pillRootView?.alpha = 0.98f
            ensurePillWithinCurrentScreenBounds(useExpandedWidth = true)
            refreshExpandedControls()
        } else {
            ensurePillWithinCurrentScreenBounds(useExpandedWidth = false)
            scheduleGhostFade()
        }
    }

    private fun scheduleGhostFade() {
        mainHandler.removeCallbacks(ghostAlphaRunnable)
        if (!pillExpanded) {
            mainHandler.postDelayed(ghostAlphaRunnable, 3200L)
        }
    }

    private fun switchOverlayTab(tabIdx: Int) {
        activePillTab = tabIdx.coerceIn(0, 2)
        tabPaneAudio?.visibility = if (activePillTab == 0) View.VISIBLE else View.GONE
        tabPaneVideo?.visibility = if (activePillTab == 1) View.VISIBLE else View.GONE
        tabPaneNet?.visibility = if (activePillTab == 2) View.VISIBLE else View.GONE

        val tabs = listOf(tabBtnAudio, tabBtnVideo, tabBtnNet)
        for (i in tabs.indices) {
            val tv = tabs[i] ?: continue
            val sel = (i == activePillTab)
            tv.setTextColor(if (sel) Color.parseColor("#040812") else Color.parseColor("#CBD5E1"))
            (tv.background as? GradientDrawable)?.apply {
                setColor(if (sel) Color.parseColor("#00E5FF") else Color.parseColor("#111B2E"))
                setStroke(1, Color.parseColor(if (sel) "#00E5FF" else "#293B56"))
            }
        }
    }

    private fun onStopButtonTapped() {
        val now = SystemClock.uptimeMillis()
        if (now - lastStopConfirmTapMs < 2500L) {
            mainHandler.removeCallbacks(stopConfirmResetRunnable)
            actions.onStopConfirmed()
        } else {
            lastStopConfirmTapMs = now
            btnOvStopConfirm?.text = "Tap Again to Stop!"
            mainHandler.removeCallbacks(stopConfirmResetRunnable)
            mainHandler.postDelayed(stopConfirmResetRunnable, 2500L)
        }
    }

    private fun makePillSectionMicroHeader(title: String): TextView {
        return TextView(context).apply {
            text = title
            setTextColor(Color.parseColor("#7DD3FC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.8f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding(dp(2), dp(4), dp(2), dp(1))
        }
    }

    private fun makeAlignedGridRow(vararg buttons: TextView): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
            for ((idx, btn) in buttons.withIndex()) {
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < buttons.size - 1) rightMargin = dp(4)
                }
                btn.layoutParams = lp
                btn.gravity = Gravity.CENTER
                btn.maxLines = 1
                btn.ellipsize = TextUtils.TruncateAt.END
                addView(btn)
            }
        }
    }

    private fun makePillTabHeader(title: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = title
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(5), dp(6), dp(5))
            setTextColor(if (selected) Color.parseColor("#040812") else Color.parseColor("#CBD5E1"))
            background = GradientDrawable().apply {
                setColor(if (selected) Color.parseColor("#00E5FF") else Color.parseColor("#111B2E"))
                cornerRadius = dp(8).toFloat()
                setStroke(1, Color.parseColor(if (selected) "#00E5FF" else "#293B56"))
            }
            setOnClickListener {
                onClick()
                scheduleGhostFade()
            }
        }
    }

    private fun makePillMiniBtn(
        label: String,
        hexColor: String,
        padHdp: Int = 6,
        textSp: Float = 10.0f,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(hexColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(padHdp), dp(5), dp(padHdp), dp(5))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#101929"))
                cornerRadius = dp(8).toFloat()
                setStroke(1, Color.parseColor("#2E4361"))
            }
            setOnClickListener {
                onClick()
                scheduleGhostFade()
            }
        }
    }

    private fun stylePillControlBtn(
        btn: TextView?,
        label: String,
        isOn: Boolean,
        isSelector: Boolean = false,
        isDanger: Boolean = false
    ) {
        if (btn == null) return
        btn.text = label
        val bg = btn.background as? GradientDrawable ?: GradientDrawable().also { btn.background = it }
        when {
            isDanger -> {
                btn.setTextColor(Color.parseColor("#FFE4E6"))
                bg.setColor(Color.parseColor("#3B0D18"))
                bg.setStroke(2, Color.parseColor("#FF1744"))
            }
            isOn -> {
                btn.setTextColor(Color.parseColor("#F8FAFC"))
                bg.setColor(Color.parseColor("#0D3652"))
                bg.setStroke(2, Color.parseColor("#00E5FF"))
            }
            isSelector -> {
                btn.setTextColor(Color.parseColor("#E0F2FE"))
                bg.setColor(Color.parseColor("#13284A"))
                bg.setStroke(1, Color.parseColor("#38BDF8"))
            }
            else -> {
                btn.setTextColor(Color.parseColor("#94A3B8"))
                bg.setColor(Color.parseColor("#101929"))
                bg.setStroke(1, Color.parseColor("#24344D"))
            }
        }
    }

    private fun loadOverlayPositionPrefs(): Pair<Int, Int> {
        val prefs = (context.applicationContext ?: context).getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_OV_X, 24) to prefs.getInt(KEY_OV_Y, 120)
    }

    private fun saveOverlayPositionPrefs(x: Int, y: Int) {
        (context.applicationContext ?: context).getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OV_X, x)
            .putInt(KEY_OV_Y, y)
            .apply()
    }

    companion object {
        private const val OVERLAY_PREFS = "solstream_overlay_ui_prefs"
        private const val KEY_OV_X = "ov_pos_x"
        private const val KEY_OV_Y = "ov_pos_y"
    }
}
