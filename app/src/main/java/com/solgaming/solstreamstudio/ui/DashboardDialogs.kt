/*
- ॐ नमः शिवाय (Om Namah Shivaya)
- SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
- Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
- Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.solgaming.solstreamstudio.DiagnosticsLogger
import com.solgaming.solstreamstudio.KeystoreVault
import com.solgaming.solstreamstudio.SolStreamService
import com.solgaming.solstreamstudio.SolVideoPipeline
import com.solgaming.solstreamstudio.StreamSettings
import com.solgaming.solstreamstudio.recorder.SolHudAndQrRenderer
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
import kotlin.math.min
import kotlin.math.roundToInt

/**
- Modular dashboard dialogs, visual user guide cards, YouTube Shorts 9:16 Safe-Zone Previewer,
- Accessibility prominent disclosure, Session Reports / Saved Clips viewers,
- 5-Metric Network Analyzer, and GitHub Update Checker.
 */
object DashboardDialogs {

    private val netTestBusy = AtomicBoolean(false)
    private val updateCheckBusy = AtomicBoolean(false)

    val isNetworkTestRunning: Boolean
        get() = netTestBusy.get()

    fun copyPlainTextToClipboard(context: Context, label: String, text: String) {
        try {
            val appCtx = context.applicationContext ?: context
            val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
        } catch (_: Exception) {
        }
    }

    fun buildBrandAntiKillOneLiner(): String {
        val mfr = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase(Locale.US)
        return when {
            mfr.contains("samsung") ->
                "Samsung Tip: Set Battery → Unrestricted, add SolStream & your game to Device Care → Memory → Excluded apps, and keep RAM Plus OFF."
            mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") ->
                "HyperOS/MIUI Tip: Set Battery → No restrictions, enable Autostart, and lock SolStream in Recents."
            mfr.contains("oneplus") || mfr.contains("oppo") || mfr.contains("realme") ->
                "ColorOS/OxygenOS Tip: Turn ON Allow Background Activity and lock SolStream in Recents."
            mfr.contains("vivo") || mfr.contains("iqoo") ->
                "Vivo/iQOO Tip: Allow High Background Power Consumption, lock in Recents, and keep Game Frame Interpolation OFF."
            else ->
                "Android Tip: Set Battery → Unrestricted in App Info so Android never cuts long broadcasts."
        }
    }

    /**
- In-App 9:16 YouTube Shorts Safe-Zone Visual Previewer (v1.6.1 SolShorts™ Studio).
- Mirrors SolHudAndQrRenderer.computeShortsTopLayout() and SolVideoPipeline.kt's exact NDC math
- so users can inspect gameplay height, ABOVE_CHAT shift, 55%/38% Tactical Loupes,
- Shorts Pro Header safe-stacking below YT's 15% top bar, and Shorts UI dead zones before going live.
     */
    fun showShortsSafeZonePreviewDialog(
        activity: Activity,
        settings: StreamSettings
    ) {
        val dm = activity.resources.displayMetrics
        val panelLong = max(dm.widthPixels, dm.heightPixels).coerceAtLeast(1280)
        val panelShort = min(dm.widthPixels, dm.heightPixels).coerceAtLeast(720)
        val srcAspect = (panelLong.toFloat() / panelShort.toFloat()).coerceIn(1.75f, 2.35f)
        val dstAspect = 9.0f / 16.0f

        val framing = settings.framingMode.uppercase(Locale.US)
        val zoomPct = settings.smartZoomPercent.coerceIn(100, 250)
        val sideCropPct = when {
            framing == "FILL" -> (((1.0f - (dstAspect / srcAspect)) * 50.0f).coerceIn(0f, 45f))
            framing == "SMART" && zoomPct > 100 -> (((1.0f - (100.0f / zoomPct.toFloat())) * 50.0f).coerceIn(0f, 40f))
            else -> 0.0f
        }
        val (capW, capH) = SolVideoPipeline.scaleCaptureForShortsCooler(
            rawW = panelLong,
            rawH = panelShort,
            framingMode = framing,
            smartZoomPercent = zoomPct,
            coolerEnabled = settings.shortsCaptureCooler
        )
        val rawPx = panelLong.toLong() * panelShort.toLong()
        val capPx = capW.toLong() * capH.toLong()
        val bandwidthSavedPct = if (rawPx > 0L && capPx < rawPx) {
            (((rawPx - capPx) * 100L) / rawPx).toInt()
        } else {
            0
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#050A14"))
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 10))
        }

        val previewView = ShortsPreviewCanvasView(activity, settings, srcAspect)
        container.addView(
            previewView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(activity, 10) }
        )

        val mapSuppressed = SolVideoPipeline.isMinimapLoupeSuppressedByShield(
            settings.shortsMinimapLoupe,
            settings.minimapShieldEnabled,
            settings.minimapShieldCorner
        )
        val coolerLine = if (settings.shortsCaptureCooler && bandwidthSavedPct > 0) {
            "Capture Cooler: ON (${capW}×${capH} • -${bandwidthSavedPct}% capture bandwidth)"
        } else {
            "Capture Cooler: OFF (Full ${panelLong}×${panelShort} panel capture)"
        }
        val loupeLine = "Loupes: Score(55%)=${settings.shortsScoreLoupe} • Minimap(38%)=" +
            if (mapSuppressed) "${settings.shortsMinimapLoupe} (Paused by Blur Shield)" else settings.shortsMinimapLoupe
        val headerLine = "Shorts Pro Header: " +
            if (settings.shortsHeaderLayout) "ON (Auto-stacked below 15% YT Top Bar)" else "OFF (Standard Corners)"

        val infoTv = TextView(activity).apply {
            text = buildString {
                append("Framing: $framing")
                if (framing == "SMART") append(" ($zoomPct% • ~${String.format(Locale.US, "%.0f%%", sideCropPct)} side crop)")
                append(" • Anchor: ${settings.shortsVerticalAnchor}\n")
                append("$loupeLine\n")
                append("$headerLine\n")
                append("$coolerLine\n")
                append("Red zones = YouTube Shorts LIVE header (15%), Live Chat (35%) & Right Like/Share buttons (18%).")
            }
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.8f)
            setLineSpacing(dp(activity, 2).toFloat(), 1.0f)
        }
        container.addView(infoTv)

        val scroll = ScrollView(activity).apply {
            setBackgroundColor(Color.parseColor("#050A14"))
            addView(container)
        }
        AlertDialog.Builder(activity)
            .setTitle("👁️ 9:16 YouTube Shorts Safe-Zone Preview")
            .setView(scroll)
            .setPositiveButton("Got It", null)
            .show()
    }

    private class ShortsPreviewCanvasView(
        context: Context,
        private val settings: StreamSettings,
        private val srcAspect: Float
    ) : View(context) {
        private val dstAspect = 9.0f / 16.0f
        private val density = context.resources.displayMetrics.density

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val frameStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#38BDF8")
            strokeWidth = 2f * density
        }
        private val gameFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#12304D")
        }
        private val gameStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#00E5FF")
            strokeWidth = 2f * density
        }
        private val loupeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0B2A3D")
        }
        private val loupeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#00E5FF")
            strokeWidth = 1.5f * density
        }
        private val deadZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(72, 255, 23, 68)
        }
        private val deadZoneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(145, 255, 82, 82)
            strokeWidth = 1f * density
        }
        private val overlayChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#4C1D95")
        }
        private val overlayChipStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#A78BFA")
            strokeWidth = 1f * density
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 10f * density
        }
        private val smallLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0F2FE")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 8.2f * density
        }
        private val deadLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDA4AF")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 8.5f * density
        }
        private val tmpRect = RectF()
        private val tmpMvp = FloatArray(16)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val screenH = context.resources.displayMetrics.heightPixels
            val screenW = context.resources.displayMetrics.widthPixels
            val maxH = min((400f * density).roundToInt(), (screenH * 0.48f).roundToInt()).coerceAtLeast((240f * density).roundToInt())
            val maxW = (screenW * 0.72f).roundToInt().coerceAtLeast((150f * density).roundToInt())
            var h = maxH
            var w = (h * dstAspect).roundToInt()
            if (w > maxW) {
                w = maxW
                h = (w / dstAspect).roundToInt()
            }
            setMeasuredDimension(w, h)
        }

        private fun ndcX(x: Float, w: Float): Float = (x + 1.0f) * 0.5f * w
        private fun ndcY(y: Float, h: Float): Float = (1.0f - y) * 0.5f * h

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 4f || h <= 4f) return

            // 1. Canvas Backdrop
            bgPaint.color = when (settings.shortsBackdropMode.uppercase(Locale.US)) {
                "DARK" -> Color.parseColor("#070B14")
                "POSTER" -> Color.parseColor("#1A1330")
                else -> Color.parseColor("#0F1A2E")
            }
            tmpRect.set(0f, 0f, w, h)
            canvas.drawRect(tmpRect, bgPaint)

            // 2. Gameplay Window (exact SolVideoPipeline NDC math)
            val framing = settings.framingMode.uppercase(Locale.US)
            val zoomPct = settings.smartZoomPercent.coerceIn(100, 250)
            val activeZoom = if (framing == "SMART") zoomPct / 100.0f else 1.0f
            val sharpScaleY = if (framing == "FILL") {
                1.0f
            } else {
                ((dstAspect / srcAspect) * activeZoom).coerceIn(0.10f, 1.50f)
            }
            val translateY = SolVideoPipeline.computeShortsVerticalOffsetNdc(
                isVerticalDst = true,
                isLandscapeSrc = true,
                framingMode = framing,
                sharpScaleY = sharpScaleY,
                anchorMode = settings.shortsVerticalAnchor
            )
            val topNdc = (translateY + sharpScaleY).coerceIn(-1.0f, 1.0f)
            val botNdc = (translateY - sharpScaleY).coerceIn(-1.0f, 1.0f)

            tmpRect.set(0f, ndcY(topNdc, h), w, ndcY(botNdc, h))
            canvas.drawRect(tmpRect, gameFillPaint)
            canvas.drawRect(tmpRect, gameStrokePaint)

            val gameCenterY = (tmpRect.top + tmpRect.bottom) * 0.5f
            val mainText = if (framing == "SMART") "GAMEPLAY ($framing $zoomPct%)" else "GAMEPLAY ($framing)"
            canvas.drawText(mainText, w * 0.5f, gameCenterY + (3.5f * density), labelPaint)

            // 3. Shared v1.6.1 Shorts Top-Area Safe Stack (Row 1 Pro Overlays + Row 2 55%/38% Loupes)
            val scoreCrop = if (framing != "FILL") {
                SolVideoPipeline.resolveShortsLoupeSourceCrop(settings.shortsScoreLoupe, isMinimap = false)
            } else {
                null
            }
            val mapSuppressed = SolVideoPipeline.isMinimapLoupeSuppressedByShield(
                settings.shortsMinimapLoupe,
                settings.minimapShieldEnabled,
                settings.minimapShieldCorner
            )
            val mapCrop = if (framing != "FILL" && !mapSuppressed) {
                SolVideoPipeline.resolveShortsLoupeSourceCrop(settings.shortsMinimapLoupe, isMinimap = true)
            } else {
                null
            }
            val hasQr = settings.qrOverlayEnabled && settings.qrOverlayText.isNotBlank()

            val topLayout = SolHudAndQrRenderer.computeShortsTopLayout(
                dstW = 1080,
                dstH = 1920,
                gameTopNdc = topNdc,
                hasScoreLoupe = scoreCrop != null,
                hasMinimapLoupe = mapCrop != null,
                loupesPaused = false,
                shortsHeaderLayout = settings.shortsHeaderLayout,
                hudEnabled = settings.hudTelemetryEnabled,
                qrEnabled = hasQr,
                pngTuberEnabled = settings.pngTuberEnabled
            )

            // Draw Row 2 Tactical HUD Loupes
            if (topLayout.hasLoupeBand) {
                if (scoreCrop != null) {
                    val cropAspect = ((scoreCrop.x1 - scoreCrop.x0) * srcAspect) /
                        max(0.05f, scoreCrop.yBot - scoreCrop.yTop)
                    SolVideoPipeline.computeShortsLoupeDestNdc(
                        slotLeftNdc = topLayout.scoreSlotLeftNdc,
                        slotRightNdc = topLayout.scoreSlotRightNdc,
                        slotBottomNdc = topLayout.loupeBottomNdc,
                        slotTopNdc = topLayout.loupeTopNdc,
                        cropAspect = cropAspect,
                        dstAspect = dstAspect,
                        alignRight = false
                    )?.let { dest ->
                        tmpRect.set(ndcX(dest.left, w), ndcY(dest.top, h), ndcX(dest.right, w), ndcY(dest.bottom, h))
                        canvas.drawRect(tmpRect, loupeFillPaint)
                        canvas.drawRect(tmpRect, loupeStrokePaint)
                        val lbl = if (settings.shortsScoreLoupe == "TOP_LEFT_FEED") "SCORE+FEED" else "SCORE"
                        canvas.drawText(lbl, tmpRect.centerX(), tmpRect.centerY() + (3f * density), smallLabelPaint)
                    }
                }
                if (mapCrop != null) {
                    val cropAspect = ((mapCrop.x1 - mapCrop.x0) * srcAspect) /
                        max(0.05f, mapCrop.yBot - mapCrop.yTop)
                    SolVideoPipeline.computeShortsLoupeDestNdc(
                        slotLeftNdc = topLayout.minimapSlotLeftNdc,
                        slotRightNdc = topLayout.minimapSlotRightNdc,
                        slotBottomNdc = topLayout.loupeBottomNdc,
                        slotTopNdc = topLayout.loupeTopNdc,
                        cropAspect = cropAspect,
                        dstAspect = dstAspect,
                        alignRight = true
                    )?.let { dest ->
                        tmpRect.set(ndcX(dest.left, w), ndcY(dest.top, h), ndcX(dest.right, w), ndcY(dest.bottom, h))
                        canvas.drawRect(tmpRect, loupeFillPaint)
                        canvas.drawRect(tmpRect, loupeStrokePaint)
                        canvas.drawText("MINIMAP", tmpRect.centerX(), tmpRect.centerY() + (3f * density), smallLabelPaint)
                    }
                }
            }

            // 4. Active Pro Overlays (Exact Safe-Stack Rects when Shorts Pro Header ON; exact corners when OFF)
            if (settings.shortsHeaderLayout) {
                topLayout.hudRect?.let { drawNdcOverlayBadge(canvas, w, h, it, "HUD") }
                topLayout.pngTuberRect?.let { drawNdcOverlayBadge(canvas, w, h, it, "AVATAR") }
                topLayout.qrRect?.let { drawNdcOverlayBadge(canvas, w, h, it, "QR") }
            } else {
                if (settings.hudTelemetryEnabled) {
                    SolHudAndQrRenderer.computeCornerQuadMvp(
                        tmpMvp,
                        SolHudAndQrRenderer.HUD_WIDTH_PX,
                        SolHudAndQrRenderer.HUD_HEIGHT_PX,
                        1080,
                        1920,
                        settings.hudTelemetryCorner,
                        0.052f,
                        0.46f
                    )
                    drawNdcOverlayBadge(canvas, w, h, mvpToNdcRect(tmpMvp), "HUD")
                }
                if (settings.pngTuberEnabled) {
                    SolHudAndQrRenderer.computeCornerQuadMvp(
                        tmpMvp,
                        512,
                        512,
                        1080,
                        1920,
                        settings.pngTuberCorner,
                        0.135f,
                        0.26f
                    )
                    drawNdcOverlayBadge(canvas, w, h, mvpToNdcRect(tmpMvp), "AVATAR")
                }
                if (hasQr) {
                    SolHudAndQrRenderer.computeCornerQuadMvp(
                        tmpMvp,
                        SolHudAndQrRenderer.QR_SIZE_PX,
                        SolHudAndQrRenderer.QR_SIZE_PX,
                        1080,
                        1920,
                        settings.qrOverlayCorner,
                        0.115f,
                        0.22f
                    )
                    drawNdcOverlayBadge(canvas, w, h, mvpToNdcRect(tmpMvp), "QR")
                }
            }

            // 5. YouTube Shorts UI Dead Zones (Top 15%, Bottom 35%, Right 18% action buttons)
            tmpRect.set(0f, 0f, w, h * 0.15f)
            canvas.drawRect(tmpRect, deadZonePaint)
            canvas.drawRect(tmpRect, deadZoneStrokePaint)
            canvas.drawText("YT TOP BAR (15%)", w * 0.5f, h * 0.085f, deadLabelPaint)

            tmpRect.set(w * 0.82f, h * 0.45f, w, h * 0.65f)
            canvas.drawRect(tmpRect, deadZonePaint)
            canvas.drawRect(tmpRect, deadZoneStrokePaint)
            canvas.drawText("LIKE", tmpRect.centerX(), tmpRect.centerY() + (3f * density), deadLabelPaint)

            tmpRect.set(0f, h * 0.65f, w, h)
            canvas.drawRect(tmpRect, deadZonePaint)
            canvas.drawRect(tmpRect, deadZoneStrokePaint)
            canvas.drawText("YT LIVE CHAT & CHANNEL (35%)", w * 0.5f, h * 0.83f, deadLabelPaint)

            // Outer 9:16 border
            tmpRect.set(1f, 1f, w - 1f, h - 1f)
            canvas.drawRect(tmpRect, frameStrokePaint)
        }

        private fun mvpToNdcRect(mvp: FloatArray): SolHudAndQrRenderer.NdcRect {
            val sx = mvp[0]
            val sy = mvp[5]
            val tx = mvp[12]
            val ty = mvp[13]
            return SolHudAndQrRenderer.NdcRect(
                left = tx - sx,
                bottom = ty - sy,
                right = tx + sx,
                top = ty + sy
            )
        }

        private fun drawNdcOverlayBadge(
            canvas: Canvas,
            w: Float,
            h: Float,
            rect: SolHudAndQrRenderer.NdcRect,
            text: String
        ) {
            tmpRect.set(ndcX(rect.left, w), ndcY(rect.top, h), ndcX(rect.right, w), ndcY(rect.bottom, h))
            canvas.drawRoundRect(tmpRect, 4f * density, 4f * density, overlayChipPaint)
            canvas.drawRoundRect(tmpRect, 4f * density, 4f * density, overlayChipStrokePaint)
            canvas.drawText(text, tmpRect.centerX(), tmpRect.centerY() + (2.8f * density), smallLabelPaint)
        }
    }

    fun showPlainEnglishGlossaryDialog(
        activity: Activity,
        appVersionName: String,
        onOpenBatteryUnrestricted: () -> Unit
    ) {
        val mfr = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase(Locale.US)
        val detectedAntiKillBlock = when {
            mfr.contains("samsung") ->
                "★ YOUR DEVICE: SAMSUNG (${Build.MODEL})\n" +
                    "1. App Info → Battery → Unrestricted.\n" +
                    "2. Device Care → Memory → Excluded apps → add BOTH SolStream Studio and your game.\n" +
                    "3. Device Care → Memory → RAM Plus → OFF (prevents background disk-swap freezes on 8 GB phones).\n" +
                    "4. Game Booster → Plug in your 25W+ PPS charger BEFORE opening your game and enable 'Pause USB PD charging when gaming' for cooler sessions."
            mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") ->
                "★ YOUR DEVICE: XIAOMI / POCO / REDMI (${Build.MODEL})\n" +
                    "1. App Info → Battery Saver → No restrictions.\n" +
                    "2. Enable Autostart and Lock SolStream Studio in Recents.\n" +
                    "3. Sideloaded APK Unlock: Scroll to bottom of App Info → 'Allow restricted settings'."
            mfr.contains("vivo") || mfr.contains("iqoo") ->
                "★ YOUR DEVICE: VIVO / iQOO (${Build.MODEL})\n" +
                    "1. Enable High Background Power Consumption & lock SolStream in Recents.\n" +
                    "2. Ultra Game Mode: Keep 'Game Frame Interpolation' OFF (use Native 90/120 FPS) for smooth 60 FPS capture."
            mfr.contains("oneplus") || mfr.contains("oppo") || mfr.contains("realme") ->
                "★ YOUR DEVICE: ONEPLUS / OPPO / REALME (${Build.MODEL})\n" +
                    "1. App Info → Battery Usage → turn ON 'Allow background activity'.\n" +
                    "2. Lock SolStream Studio in Recents so memory cleaner skips it."
            else ->
                "★ YOUR DEVICE: ${Build.MANUFACTURER.uppercase(Locale.US)} ${Build.MODEL}\n" +
                    "1. App Info → Battery → Unrestricted (keeps 3+ hour streams uninterrupted)."
        }

        val guideContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050A14"))
            setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12))
        }

        guideContainer.addView(
            makeGuideCard(
                context = activity,
                badgeText = "CARD 1 • 3-STEP QUICK START",
                titleText = "Go Live in Under 60 Seconds",
                bodyContent = "1. Paste your YouTube, Twitch, or Kick Stream Key in Card 2 (AES-256 Hardware Keystore encrypted).\n" +
                    "2. Pick your Device Tier (Card 0), One-Tap Studio Profile (Card 2), and Gender Voice Profile (Card 4) — HPF, bitrate, and platform limits auto-sync.\n" +
                    "3. Tap GO LIVE. In YouTube Studio, keep 'Turn on manual settings' OFF so one key works for both 16:9 and 9:16 Shorts. (Internal game audio capture depends on each game permitting Android AudioPlaybackCapture.)",
                accentHex = "#00E5FF"
            )
        )

        guideContainer.addView(
            makeGuideCard(
                context = activity,
                badgeText = "CARD 2 • SOLLUXE™ 3.0 PURE AUDIO",
                titleText = "Voice Presets at a Glance",
                bodyContent = "• SOL_LUXE: Full 6-stage pure condenser chain — deep chest warmth, surgical mud cleanup, silky 4.5–5.1 kHz presence, clean 8.5/9.0 kHz air shelf & smooth VAD leveling.\n" +
                    "• RADIO STUDIO: Shifts presence to 2.9–3.5 kHz (+1.6 dB) with tighter 3.2:1 leveling to cut through intense gunfights.\n" +
                    "• VELVET: Warm natural tone with subtle low-band warmth and gentle 2.0:1 leveling.\n" +
                    "• CLEAN / NEURAL: Noise removal + HPF + smooth leveling with zero EQ coloration.\n" +
                    "• RAW: Dry uncolored mic bypass (retains Mute, Mic Boost & safety peak limiter).\n\n" +
                    "Bluetooth Earphones stay in HD Stereo (A2DP) for footsteps while SolStream captures your phone's 48 kHz studio mic.",
                accentHex = "#8B5CF6"
            )
        )

        val antiKillCard = makeGuideCard(
            context = activity,
            badgeText = "CARD 3 • PREVENT 3-HOUR STREAM CUTS",
            titleText = "Long-Stream Anti-Kill Checklist",
            bodyContent = "$detectedAntiKillBlock\n\n" +
                "• 60-Second Auto-Checkpoint: Even if Android forcefully kills the process, your live telemetry and ★ Saved Clips are safely checkpointed every 60 seconds without overwriting past report history.\n" +
                "• Fast Network Handover: Switching between Wi-Fi and 5G triggers a fast automatic RTMP reconnect over the new route.",
            accentHex = "#10B981"
        )
        antiKillCard.addView(
            makeDialogButton(activity, "Open Battery / App Info Settings", "#0D3652") {
                onOpenBatteryUnrestricted()
            }
        )
        guideContainer.addView(antiKillCard)

        guideContainer.addView(
            makeGuideCard(
                context = activity,
                badgeText = "CARD 4 • SOLSHORTS™ STUDIO, PRO OVERLAYS & PILL",
                titleText = "9:16 Tactical Loupes, Safe-Zone Anchor, SolHUD™ & Thumbnails",
                bodyContent = "• SolShorts™ Studio (Opt-In, Zero Locks/Caps): Keep 135–150% zoomed gameplay big while docking zero-copy Score+Killfeed (55%) & tight-cropped Minimap (38%) Loupes strictly below YouTube's 15% top bar. Use the 1-tap Loupes: ON/OFF button in the Floating Pill to hide both Loupes in lobbies without losing your chosen crops.\n" +
                    "• Floating Pill: Double-tap or hold the compact pill to open 3 tabs (Audio, Video, Bitrate/Net) with separate App CPU % and Android Thermal Pressure % telemetry. Tap 📸 Shot in Video tab to save a clean pill-free 1080p YouTube thumbnail to Pictures/SolStream/.\n" +
                    "• Pro Overlays (Opt-In, 0 MB VRAM When OFF): Turn on SolHUD™ (smoothed live FPS • °C • Mbps visible even in Single-App Capture), offline Corner QR (UPI / YouTube / Discord), or zero-camera 2-PNG PNGTuber Avatar — Shorts Pro Header auto-stacks all three cleanly with zero overlap.\n" +
                    "• Safe Notification Shade: Exactly 3 quick actions (Mute Mic, Scene/BRB, ★ Clip) — Stop is safely locked inside the app & Floating Pill with 2-tap confirm.",
                accentHex = "#38BDF8"
            )
        )

        guideContainer.addView(
            makeGuideCard(
                context = activity,
                badgeText = "CARD 5 • PRIVACY POLICY & OPEN-SOURCE LICENSES",
                titleText = "Data Safety, Accessibility & Credits",
                bodyContent = "• Zero Developer Data Collection: All video/audio processing runs locally on-device and streams solely to your configured RTMP/RTMPS ingest. Zero developer servers, zero analytics, zero ads. Voluntary Cloudflare speed tests and GitHub update checks send standard network request metadata (IP/HTTP headers) directly to those services.\n" +
                    "• Hardware Keystore: Stream keys are encrypted via Android Keystore (AES-256-GCM) and auto-redacted ([REDACTED_KEY]) from logs.\n" +
                    "• AccessibilityService Policy: Used optionally solely to display the assistive floating broadcast control dock over full-screen games (microphone capture uses Android's standard RECORD_AUDIO permission). Strictly sets isAccessibilityTool=\"false\", canRetrieveWindowContent=\"false\", canPerformGestures=\"false\", and subscribes to zero accessibility events — cannot read screen text or passwords.\n\n" +
                    "Open-Source Credits:\n" +
                    "1. RootEncoder (Pedro Sánchez / pedroSG94) — Apache-2.0\n" +
                    "2. Xiph RNNoise v0.1.1 (Xiph.Org & Jean-Marc Valin) — BSD 3-Clause\n" +
                    "3. AndroidX (Core, AppCompat, ExifInterface), Material & Kotlin Coroutines — Apache-2.0",
                accentHex = "#64748B"
            )
        )

        val scroll = ScrollView(activity).apply {
            setBackgroundColor(Color.parseColor("#050A14"))
            addView(guideContainer)
        }
        AlertDialog.Builder(activity)
            .setTitle("📖 SolStream Studio v$appVersionName Guide")
            .setView(scroll)
            .setPositiveButton("Got It", null)
            .show()
    }

    fun showAccessibilityDisclosureDialog(
        activity: Activity,
        allowSkipToStream: Boolean,
        onAcceptOpenSettings: () -> Unit,
        onOpenAppInfoRestricted: () -> Unit,
        onSkipToStream: () -> Unit
    ) {
        val msg = "SolStream Studio optionally uses the Android AccessibilityService API solely to display an assistive floating in-game broadcast control dock (TYPE_ACCESSIBILITY_OVERLAY) over full-screen games for one-tap microphone mute, privacy blur shield, scene switching, overlay toggles, clean thumbnail capture, and clip markers.\n\n" +
            "Microphone & Audio Capture Note:\n" +
            "• Microphone audio is captured separately via Android's standard RECORD_AUDIO permission, and internal game audio uses Android's MediaProjection playback capture when permitted by the game.\n" +
            "• On supported Android versions, keeping this optional overlay active also allows Android's concurrent audio-input rules to operate while broadcasting over games.\n\n" +
            "Privacy & Security Guarantee:\n" +
            "• SolStream Studio declares isAccessibilityTool=\"false\", canRetrieveWindowContent=\"false\", and canPerformGestures=\"false\", and subscribes to zero accessibility events.\n" +
            "• It CANNOT inspect, read, or collect screen text, passwords, messages, or personal data, and CANNOT perform automated taps or gestures.\n" +
            "• Zero audio, video, or personal data is ever sent to developer servers; all streams go directly to your own configured RTMP/RTMPS server.\n\n" +
            "Sideloaded APK Note (Android 13–18): If SolStream Studio is greyed out in Accessibility, tap it once -> tap 'Step 4: App Info (⋮)' -> tap ⋮ at top-right -> 'Allow restricted settings'."

        val builder = AlertDialog.Builder(activity)
            .setTitle("Assistive Floating Broadcast Dock Disclosure")
            .setMessage(msg)
            .setPositiveButton("Accept & Open Settings") { _, _ ->
                onAcceptOpenSettings()
            }
            .setNeutralButton("Step 4: App Info (⋮)") { _, _ ->
                onOpenAppInfoRestricted()
            }

        if (allowSkipToStream) {
            builder.setNegativeButton("No Thanks (Stream Without Overlay)") { _, _ ->
                onSkipToStream()
            }
        } else {
            builder.setNegativeButton("No Thanks (Continue Without Dock)", null)
        }
        builder.show()
    }

    fun showAllBrandSetupGuide(
        activity: Activity,
        onOpenBatteryUnrestricted: () -> Unit,
        onOpenAccessibilityDisclosure: () -> Unit
    ) {
        val mfr = Build.MANUFACTURER.lowercase(Locale.US)
        val detectedBrandTip = when {
            mfr.contains("samsung") ->
                "★ DETECTED PHONE: SAMSUNG ONE UI (${Build.MODEL})\n" +
                    "• Memory Exclusion (Prevents 2–3h Kills): Settings → Device Care → Memory → Excluded apps → add BOTH SolStream Studio and your game.\n" +
                    "• RAM Plus: Keep Device Care → Memory → RAM Plus OFF on 8 GB phones to prevent background disk-swap freezes.\n" +
                    "• Game Booster: Plug in your 25W+ PPS charger BEFORE opening your game and enable 'Pause USB PD charging when gaming' for cooler sessions."
            mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") ->
                "★ DETECTED PHONE: XIAOMI / REDMI / POCO (HyperOS / MIUI)\n" +
                    "• Unlocking Restricted Settings: Open App Info → scroll to the VERY BOTTOM → tap 'Allow restricted settings'.\n" +
                    "• Background Lock: Set Battery Saver to 'No restrictions', enable Autostart, and lock SolStream Studio in Recents."
            mfr.contains("vivo") || mfr.contains("iqoo") ->
                "★ DETECTED PHONE: VIVO / iQOO (FuntouchOS / OriginOS)\n" +
                    "• Q1/Q2 Chip Tip: In Ultra Game Mode, keep 'Game Frame Interpolation' OFF (use Native 90/120 FPS) while streaming.\n" +
                    "• Background Lock: Enable 'High Background Power Consumption' and lock SolStream in Recents."
            mfr.contains("oneplus") || mfr.contains("realme") || mfr.contains("oppo") ->
                "★ DETECTED PHONE: ONEPLUS / REALME / OPPO (OxygenOS / ColorOS / Realme UI)\n" +
                    "• Battery Lock: App Info → Battery Usage → turn ON 'Allow background activity' and lock SolStream in Recents.\n" +
                    "• HyperBoost: Keep Balanced thermal mode for 3+ hour streams."
            else ->
                "★ DETECTED PHONE: ${Build.MANUFACTURER.uppercase(Locale.US)} ${Build.MODEL}\n" +
                    "• Set Battery to 'Unrestricted' in App Info so Android never pauses your background broadcast."
        }

        val msg = "$detectedBrandTip\n\n" +
            "1. LONG-STREAM BATTERY PROTECTION (Zero Extra Permissions):\n" +
            "• Tap 'Battery / App Info' below → Battery → select 'Unrestricted'.\n\n" +
            "2. ENABLE ACCESSIBILITY DOCK (Play Store & Sideloaded APK):\n" +
            "• Play Store install: Tap 'Step 3: Accessibility' and switch SolStream Studio ON in 1 tap.\n" +
            "• Sideloaded GitHub APK (Android 13–18):\n" +
            "  a) Tap 'Step 3: Accessibility' → Installed apps → tap greyed-out SolStream Studio once & press OK.\n" +
            "  b) Tap 'Step 4: App Info (⋮)' → tap ⋮ at top-right → 'Allow restricted settings', then switch ON.\n\n" +
            "3. EARPHONES, WIRED & USB-C MICS (SolLuxe™ 3.0 Pure 4×3 Matrix):\n" +
            "• Bluetooth Earphones stay in 100% HD Stereo (A2DP) for footsteps while SolStream captures your phone's 48 kHz studio mic.\n" +
            "• Plugging in a Wired 3.5mm or USB-C mic auto-switches from Far-Field to Close-Talk tuning and forces hardware Speaker AEC OFF."

        AlertDialog.Builder(activity)
            .setTitle("🛡️ All-Brand Anti-Kill & Setup Guide")
            .setMessage(msg)
            .setPositiveButton("Battery / App Info") { _, _ ->
                onOpenBatteryUnrestricted()
            }
            .setNeutralButton("Step 3: Accessibility") { _, _ ->
                onOpenAccessibilityDisclosure()
            }
            .setNegativeButton("Got It", null)
            .show()
    }

    fun showSavedClipsDialog(
        activity: Activity,
        onClipsCleared: () -> Unit
    ) {
        val summary = DiagnosticsLogger.buildSavedClipsPlainEnglishSummary(activity)
        val clipCount = DiagnosticsLogger.getSavedClipsList(activity).size

        val tv = TextView(activity).apply {
            text = summary
            setTextColor(Color.parseColor("#ECEFF1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(activity).apply {
            setBackgroundColor(Color.parseColor("#050A14"))
            addView(tv)
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle("★ Saved Stream Clips & AI Highlights ($clipCount)")
            .setView(scroll)
            .setPositiveButton("Copy Clip Times") { _, _ ->
                copyPlainTextToClipboard(activity, "SolStream Saved Clips", summary)
                Toast.makeText(activity, "★ Saved Clip Timestamps copied to clipboard!", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Close", null)

        if (clipCount > 0) {
            builder.setNeutralButton("Clear Clips") { _, _ ->
                DiagnosticsLogger.clearSavedClips(activity)
                onClipsCleared()
                Toast.makeText(activity, "Saved clip list cleared.", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    fun showDiagnosticsDialog(
        activity: Activity,
        showingArchived: Boolean = false,
        onHistoryOrClipsChanged: () -> Unit
    ) {
        val archiveList = SolStreamService.loadLocalReportArchive(activity)
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
            DiagnosticsLogger.buildShareableReport(activity)
        }
        val hasCheckpoint = !showingArchived &&
            !SolStreamService.isBroadcastLive &&
            DiagnosticsLogger.hasUnfinishedLiveCheckpoint(activity)
        val checkpointHint = if (hasCheckpoint) " • Recovered 60s" else ""
        val dialogTitle = if (showingArchived) {
            "Archived Past Reports ($archivedCount Saved)"
        } else {
            "Current Session Report (History: $archivedCount$checkpointHint)"
        }
        val clipCount = DiagnosticsLogger.getSavedClipsList(activity).size

        val tv = TextView(activity).apply {
            text = reportText
            setTextColor(Color.parseColor("#ECEFF1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(activity).apply {
            setBackgroundColor(Color.parseColor("#050A14"))
            addView(tv)
        }

        val negLabel = when {
            !showingArchived && archivedCount > 0 -> "Past History ($archivedCount)"
            showingArchived && archivedCount > 0 -> "Delete History"
            hasCheckpoint -> "Clear 60s"
            else -> "Close"
        }

        AlertDialog.Builder(activity)
            .setTitle(dialogTitle)
            .setView(scroll)
            .setPositiveButton("Copy Report") { _, _ ->
                copyPlainTextToClipboard(activity, "SolStream Report", reportText)
                Toast.makeText(activity, "Report copied! Safe to paste anywhere.", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton(
                if (showingArchived) "Current Report" else "★ View Saved Clips ($clipCount)"
            ) { _, _ ->
                if (showingArchived) {
                    showDiagnosticsDialog(activity, showingArchived = false, onHistoryOrClipsChanged = onHistoryOrClipsChanged)
                } else {
                    showSavedClipsDialog(activity, onClipsCleared = onHistoryOrClipsChanged)
                }
            }
            .setNegativeButton(negLabel) { _, _ ->
                when {
                    !showingArchived && archivedCount > 0 -> {
                        showDiagnosticsDialog(activity, showingArchived = true, onHistoryOrClipsChanged = onHistoryOrClipsChanged)
                    }
                    showingArchived && archivedCount > 0 -> {
                        SolStreamService.clearLocalReportArchive(activity)
                        DiagnosticsLogger.clearLiveCheckpoint(activity)
                        onHistoryOrClipsChanged()
                        Toast.makeText(activity, "Archived session history deleted.", Toast.LENGTH_SHORT).show()
                    }
                    hasCheckpoint -> {
                        DiagnosticsLogger.clearLiveCheckpoint(activity)
                        onHistoryOrClipsChanged()
                        Toast.makeText(activity, "Recovered 60s checkpoint cleared.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    // === 5-METRIC PRO NETWORK ANALYZER (DESTINATION-AWARE) ===
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
            max(1L, (System.nanoTime() - t0) / 1_000_000L)
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    fun runRtmpsNetworkCheck(
        activity: Activity,
        rawIngestUrl: String,
        settings: StreamSettings,
        onStateUpdate: (isRunning: Boolean, statusText: String) -> Unit
    ) {
        if (SolStreamService.isBroadcastLive) {
            Toast.makeText(
                activity,
                "Speed test is locked while streaming live so packets never compete with your stream!",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (!netTestBusy.compareAndSet(false, true)) {
            Toast.makeText(activity, "Network analyzer already running...", Toast.LENGTH_SHORT).show()
            return
        }

        val (isTls, host, port) = parseIngestHostPort(rawIngestUrl.ifBlank { KeystoreVault.DEFAULT_YOUTUBE_RTMPS })
        val isTwitchLimited = KeystoreVault.isTwitchLimited(settings)
        val isKickLimited = KeystoreVault.isKickLimited(settings)
        onStateUpdate(true, "Step 1/4: Verifying DNS & ${if (isTls) "TLS" else "TCP"} handshake to $host:$port...")

        Thread({
            try {
                val dnsStartNs = System.nanoTime()
                val resolvedIp = InetAddress.getByName(host)
                val dnsMs = max(1L, (System.nanoTime() - dnsStartNs) / 1_000_000L)
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
                        val hsMs = max(1L, (System.nanoTime() - t0) / 1_000_000L)
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
                val idleAvgMs = idleSamples.average().roundToInt().toLong().coerceAtLeast(1L)
                val idleJitterMs = (idleSamples.maxOrNull() ?: idleAvgMs) - (idleSamples.minOrNull() ?: idleAvgMs)

                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        onStateUpdate(
                            true,
                            "Step 2/4: Ingest TCP Connect RTT ${idleAvgMs}ms (Jitter ${idleJitterMs}ms) — measuring Download Mbps..."
                        )
                    }
                }

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

                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        val dlTag = if (downloadMbps > 0) String.format(Locale.US, "%.1f Mbps", downloadMbps) else "N/A"
                        onStateUpdate(
                            true,
                            "Step 3/4: Download $dlTag — testing Sustained Upload Mbps & Router Bufferbloat..."
                        )
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
                        loadedRttMs = loadedList.average().roundToInt().toLong().coerceAtLeast(1L)
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

                val rawSafeMbps = if (uploadMbps > 0.0) (uploadMbps * 0.60).roundToInt().coerceIn(1, 50) else 16
                val platformCappedMbps = when {
                    isTwitchLimited -> min(rawSafeMbps, 6)
                    isKickLimited -> min(rawSafeMbps, 8)
                    else -> min(rawSafeMbps, 24)
                }

                val advice = when {
                    isTwitchLimited ->
                        "Twitch Limit Active: Safe up to $platformCappedMbps Mbps (1080p60 H.264 CBR • 6 Mbps Twitch Cap)."
                    isKickLimited ->
                        "Kick Limit Active: Safe up to $platformCappedMbps Mbps (1080p60 H.264 CBR • 8 Mbps Kick Cap)."
                    uploadMbps > 0.0 && uploadMbps < 6.0 ->
                        "Low Upload: Use 720p/480p HEVC @ ${platformCappedMbps} Mbps + Ping Guard ABR ON."
                    settings.isVertical && uploadMbps >= 14.0 ->
                        "Shorts 9:16 Sweet Spot: 8–10 Mbps HEVC (1080×1920@60) or up to $platformCappedMbps Mbps with ABR ON."
                    uploadMbps >= 24.0 && bloatDeltaMs in 0..35 ->
                        "Grade A+ Fiber! Safe for 18–24 Mbps 1440p60 HEVC (Single) or 16M + 8M (Dual Companion)."
                    else ->
                        "Recommended Safe Stream Bitrate: Up to $platformCappedMbps Mbps with Ping Guard ABR ON."
                }

                val summary = buildString {
                    val dlStr = if (downloadMbps > 0.0) String.format(Locale.US, "%.1f Mbps", downloadMbps) else "N/A"
                    val ulStr = if (uploadMbps > 0.0) String.format(Locale.US, "%.1f Mbps", uploadMbps) else "N/A"
                    append("Download: $dlStr | Upload: $ulStr | TCP Connect RTT: ${idleAvgMs}ms (Jitter ${idleJitterMs}ms)\n")
                    append("TLS: $tlsInfo (DNS ${dnsMs}ms) | Bufferbloat: $bloatGrade\n")
                    append("-> $advice")
                }

                DiagnosticsLogger.log("NET_CHECK", summary.replace('\n', ' '))
                netTestBusy.set(false)
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        onStateUpdate(false, summary)
                    }
                }
            } catch (t: Throwable) {
                netTestBusy.set(false)
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        onStateUpdate(
                            false,
                            "Ingest check failed (${t.javaClass.simpleName}: ${t.message ?: "check internet"}). Verify URL & 5GHz Wi-Fi."
                        )
                    }
                }
            }
        }, "SolNetProbe").start()
    }

    internal fun isRemoteVersionNewer(remoteRaw: String, localRaw: String): Boolean {
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

    fun checkGitHubReleaseUpdate(activity: Activity, appVersionName: String) {
        if (!updateCheckBusy.compareAndSet(false, true)) {
            Toast.makeText(activity, "Already checking GitHub releases...", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(activity, "Checking SolStream Studio releases...", Toast.LENGTH_SHORT).show()
        Thread({
            try {
                // Queries public releases mirror first (stays working after main code repo goes private),
                // with automatic fallback to the original repo.
                val candidateRepos = listOf(
                    "PppP01/SolStream-Studio-Releases",
                    "PppP01/SolStream-Studio"
                )
                var responseBody: String? = null
                var matchedRepo: String = candidateRepos.first()

                for (repo in candidateRepos) {
                    var conn: HttpsURLConnection? = null
                    try {
                        conn = (URL("https://api.github.com/repos/$repo/releases/latest")
                            .openConnection() as HttpsURLConnection).apply {
                            connectTimeout = 5000
                            readTimeout = 5000
                            setRequestProperty("Accept", "application/vnd.github+json")
                            setRequestProperty("User-Agent", "SolStream-Studio/$appVersionName")
                        }
                        if (conn.responseCode == 200) {
                            responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                            matchedRepo = repo
                            break
                        }
                    } catch (_: Exception) {
                        // Try next candidate repo
                    } finally {
                        try { conn?.disconnect() } catch (_: Exception) {}
                    }
                }

                val body = responseBody
                if (body.isNullOrBlank()) {
                    activity.runOnUiThread {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            Toast.makeText(
                                activity,
                                "No newer GitHub Release found (running v$appVersionName).",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    return@Thread
                }

                val json = JSONObject(body)
                val latestTag = json.optString("tag_name", "").removePrefix("v")
                val fallbackUrl = "https://github.com/$matchedRepo/releases"
                var downloadUrl = json.optString("html_url", fallbackUrl)
                val assets = json.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    var chosenAssetUrl: String? = null
                    for (i in 0 until assets.length()) {
                        val obj = assets.optJSONObject(i) ?: continue
                        val name = obj.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true) && !name.contains("debug", ignoreCase = true)) {
                            chosenAssetUrl = obj.optString("browser_download_url", "")
                            break
                        }
                    }
                    downloadUrl = chosenAssetUrl?.takeIf { it.startsWith("https://", ignoreCase = true) }
                        ?: assets.optJSONObject(0)?.optString("browser_download_url", downloadUrl)
                            ?.takeIf { it.startsWith("https://", ignoreCase = true) }
                        ?: fallbackUrl
                }
                if (!downloadUrl.startsWith("https://", ignoreCase = true)) {
                    downloadUrl = fallbackUrl
                }
                val finalUrl = downloadUrl
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (latestTag.isNotBlank() && isRemoteVersionNewer(latestTag, appVersionName)) {
                        AlertDialog.Builder(activity)
                            .setTitle("Update Available: v$latestTag")
                            .setMessage("You are on v$appVersionName. Download signed update from official SolStream Releases?")
                            .setPositiveButton("Download APK") { _, _ ->
                                try {
                                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                                } catch (_: Exception) {
                                    Toast.makeText(activity, "Could not open browser for download.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    } else {
                        Toast.makeText(activity, "You are on the latest version (v$appVersionName)!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        Toast.makeText(activity, "Update check failed: ${e.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                updateCheckBusy.set(false)
            }
        }, "SolUpdateCheck").start()
    }

    private fun makeGuideCard(
        context: Context,
        badgeText: String,
        titleText: String,
        bodyContent: String,
        accentHex: String
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable(context, "#0B1324", accentHex)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 10) }

            val badge = TextView(context).apply {
                text = badgeText
                setTextColor(Color.parseColor(accentHex))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
            }
            val title = TextView(context).apply {
                text = titleText
                setTextColor(Color.parseColor("#F8FAFC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(context, 2), 0, dp(context, 5))
            }
            val body = TextView(context).apply {
                text = bodyContent
                setTextColor(Color.parseColor("#CBD5E1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.4f)
                setLineSpacing(dp(context, 2).toFloat(), 1.0f)
                setTextIsSelectable(true)
            }
            addView(badge)
            addView(title)
            addView(body)
        }
    }

    private fun makeDialogButton(context: Context, label: String, bgHex: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            background = roundedDrawable(context, bgHex, "#00E5FF")
            setPadding(dp(context, 10), dp(context, 7), dp(context, 10), dp(context, 7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }
    }

    private fun cardDrawable(context: Context, fillHex: String, strokeHex: String): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(context, 14).toFloat()
            setStroke(dp(context, 1), Color.parseColor(strokeHex))
        }

    private fun roundedDrawable(context: Context, fillHex: String, strokeHex: String): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(context, 9).toFloat()
            setStroke(dp(context, 1), Color.parseColor(strokeHex))
        }

    private fun dp(context: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
}
