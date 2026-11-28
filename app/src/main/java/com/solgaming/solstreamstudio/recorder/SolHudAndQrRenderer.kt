/*
- ॐ नमः शिवाय (Om Namah Shivaya)
- SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
- Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
- Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio.recorder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
- Pure-Kotlin GPU Overlay & Media Utility for SolStream Studio (v1.6.1):
- 1. SolHUD™ Live Telemetry Badge (480x72 = 138 KB reused scratch bitmap, zero per-frame GC) + 3s EMA bitrate smoother.
- 2. ISO/IEC 18004 QR Code Generator (Versions 1..10, Error Correction Level M, Byte Mode, up to 213 UTF-8 bytes)
-    with zero third-party dependencies and crisp 256x256 white-bordered card output.
- 3. Pure-Kotlin 4x4 Column-Major OpenGL Corner Quad & 9:16 Shorts Safe-Stack Layout Calculator (100% JVM unit-testable).
- 4. Zero-permission Android 10–18 MediaStore Screenshot saver (IS_PENDING = 1 -> 0, JPEG quality 92).
 */
object SolHudAndQrRenderer {

    const val HUD_WIDTH_PX = 480
    const val HUD_HEIGHT_PX = 72
    const val QR_SIZE_PX = 256
    const val MAX_QR_UTF8_BYTES = 213

    // Strictly clears YouTube Shorts Mobile top header (0%..15% = +1.00..+0.700 NDC = 288 px on 1080x1920)
    const val SHORTS_TOP_SAFE_NDC = 0.700f

    // 55% Left Score+Killfeed Loupe & 38% Right Minimap Loupe (32.4 px horizontal gap on 1080px width)
    const val SHORTS_SCORE_SLOT_LEFT_NDC = -0.96f
    const val SHORTS_SCORE_SLOT_RIGHT_NDC = 0.14f
    const val SHORTS_MINIMAP_SLOT_LEFT_NDC = 0.20f
    const val SHORTS_MINIMAP_SLOT_RIGHT_NDC = 0.96f

    data class NdcRect(
        val left: Float,
        val bottom: Float,
        val right: Float,
        val top: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = top - bottom

        fun overlaps(other: NdcRect, eps: Float = 0.0005f): Boolean {
            return left < other.right - eps &&
                right > other.left + eps &&
                bottom < other.top - eps &&
                top > other.bottom + eps
        }
    }

    data class ShortsTopLayout(
        val hasLoupeBand: Boolean,
        val loupeBottomNdc: Float,
        val loupeTopNdc: Float,
        val scoreSlotLeftNdc: Float,
        val scoreSlotRightNdc: Float,
        val minimapSlotLeftNdc: Float,
        val minimapSlotRightNdc: Float,
        val hudRect: NdcRect?,
        val pngTuberRect: NdcRect?,
        val qrRect: NdcRect?
    )

    data class QrMatrix(
        val version: Int,
        val size: Int,
        val modules: Array<BooleanArray>
    )

    private data class QrVersionSpecM(
        val version: Int,
        val totalCodewords: Int,
        val dataCodewords: Int,
        val ecPerBlock: Int,
        val group1Blocks: Int,
        val group1DataCw: Int,
        val group2Blocks: Int,
        val group2DataCw: Int,
        val alignmentCenters: IntArray
    ) {
        val countIndicatorBits: Int get() = if (version <= 9) 8 else 16
        val maxBytePayload: Int get() = (dataCodewords * 8 - 4 - countIndicatorBits) / 8
    }

    // Verified ISO/IEC 18004 Table 9 & Annex E parameters for Versions 1..10, Error Correction Level M
    private val VERSION_SPECS_M = arrayOf(
        QrVersionSpecM(1, 26, 16, 10, 1, 16, 0, 0, intArrayOf()),
        QrVersionSpecM(2, 44, 28, 16, 1, 28, 0, 0, intArrayOf(6, 18)),
        QrVersionSpecM(3, 70, 44, 26, 1, 44, 0, 0, intArrayOf(6, 22)),
        QrVersionSpecM(4, 100, 64, 18, 2, 32, 0, 0, intArrayOf(6, 26)),
        QrVersionSpecM(5, 134, 86, 24, 2, 43, 0, 0, intArrayOf(6, 30)),
        QrVersionSpecM(6, 172, 108, 16, 4, 27, 0, 0, intArrayOf(6, 34)),
        QrVersionSpecM(7, 196, 124, 18, 4, 31, 0, 0, intArrayOf(6, 22, 38)),
        QrVersionSpecM(8, 242, 154, 22, 2, 38, 2, 39, intArrayOf(6, 24, 42)),
        QrVersionSpecM(9, 292, 182, 22, 3, 36, 2, 37, intArrayOf(6, 26, 46)),
        QrVersionSpecM(10, 346, 216, 26, 4, 43, 1, 44, intArrayOf(6, 28, 50))
    )

    // === 1. PURE-KOTLIN OPENGL QUAD & 9:16 SHORTS SAFE-STACK CALCULATORS (JVM UNIT-TESTABLE) ===

    fun computeCornerQuadMvp(
        mvp: FloatArray,
        bmpW: Int,
        bmpH: Int,
        dstW: Int,
        dstH: Int,
        corner: String,
        maxHeightFrac: Float,
        maxWidthFrac: Float,
        extraOffsetY: Float = 0f
    ) {
        val safeDstW = max(1, dstW).toFloat()
        val safeDstH = max(1, dstH).toFloat()
        val maxBoxH = safeDstH * maxHeightFrac.coerceIn(0.02f, 0.90f)
        val maxBoxW = safeDstW * maxWidthFrac.coerceIn(0.02f, 0.90f)
        val aspect = max(1, bmpW).toFloat() / max(1, bmpH).toFloat()

        val drawW: Float
        val drawH: Float
        if (maxBoxH * aspect <= maxBoxW) {
            drawH = maxBoxH
            drawW = maxBoxH * aspect
        } else {
            drawW = maxBoxW
            drawH = maxBoxW / max(0.05f, aspect)
        }

        // Full quad vertices span [-1, +1] (width 2.0 NDC), so scaleX = drawW / dstW maps directly to drawW px.
        val scaleX = (drawW / safeDstW).coerceIn(0.02f, 0.92f)
        val scaleY = (drawH / safeDstH).coerceIn(0.02f, 0.92f)
        val marginX = 0.045f
        val marginY = 0.055f

        val tx: Float
        val ty: Float
        when (corner.trim().uppercase(Locale.US)) {
            "TOP_LEFT" -> {
                tx = -1.0f + marginX + scaleX
                ty = 1.0f - marginY - scaleY
            }
            "BOTTOM_LEFT" -> {
                tx = -1.0f + marginX + scaleX
                ty = -1.0f + marginY + scaleY + extraOffsetY
            }
            "BOTTOM_RIGHT" -> {
                tx = 1.0f - marginX - scaleX
                ty = -1.0f + marginY + scaleY + extraOffsetY
            }
            else -> {
                tx = 1.0f - marginX - scaleX
                ty = 1.0f - marginY - scaleY
            }
        }

        // Clamp strictly inside [-0.99, +0.99] NDC so bounce offsets never clip outside the viewport
        val clampedTx = tx.coerceIn(-0.99f + scaleX, 0.99f - scaleX)
        val clampedTy = ty.coerceIn(-0.99f + scaleY, 0.99f - scaleY)

        mvp.fill(0f)
        mvp[0] = scaleX
        mvp[5] = scaleY
        mvp[10] = 1.0f
        mvp[12] = clampedTx
        mvp[13] = clampedTy
        mvp[15] = 1.0f
    }

    fun computeRectQuadMvp(mvp: FloatArray, rect: NdcRect) {
        val sx = ((rect.right - rect.left) * 0.5f).coerceIn(0.005f, 1.0f)
        val sy = ((rect.top - rect.bottom) * 0.5f).coerceIn(0.005f, 1.0f)
        val tx = ((rect.left + rect.right) * 0.5f).coerceIn(-1.0f + sx, 1.0f - sx)
        val ty = ((rect.bottom + rect.top) * 0.5f).coerceIn(-1.0f + sy, 1.0f - sy)
        mvp.fill(0f)
        mvp[0] = sx
        mvp[5] = sy
        mvp[10] = 1.0f
        mvp[12] = tx
        mvp[13] = ty
        mvp[15] = 1.0f
    }

    /**
     * Single source of truth for 9:16 YouTube Shorts top-area stacking (v1.6.1):
     * - Strictly anchors everything at or below SHORTS_TOP_SAFE_NDC (+0.700 = 15% from top) so
     *   YouTube's mobile @channel bar, viewer counter, and crown never cover SolHUD, Avatar, QR, or Loupes.
     * - Separates Row 1 (SolHUD Left | PNGTuber Center-Right | QR Right) cleanly above Row 2 (Loupes)
     *   with mathematically guaranteed zero overlap across CENTER (0%), ABOVE_CHAT (+10%), and Lobby Pause.
     */
    fun computeShortsTopLayout(
        dstW: Int,
        dstH: Int,
        gameTopNdc: Float,
        hasScoreLoupe: Boolean,
        hasMinimapLoupe: Boolean,
        loupesPaused: Boolean,
        shortsHeaderLayout: Boolean,
        hudEnabled: Boolean,
        qrEnabled: Boolean,
        pngTuberEnabled: Boolean,
        pngTuberAspect: Float = 1.0f,
        pngTuberBounceNdc: Float = 0f
    ): ShortsTopLayout {
        val safeDstW = max(1, dstW).toFloat()
        val safeDstH = max(1, dstH).toFloat()
        val invDstAspect = safeDstH / safeDstW
        val safePngAspect = if (pngTuberAspect.isFinite() && pngTuberAspect > 0.1f) {
            pngTuberAspect.coerceIn(0.25f, 4.0f)
        } else {
            1.0f
        }

        val wantsLoupes = (hasScoreLoupe || hasMinimapLoupe) && !loupesPaused
        val hasHeaderOverlays = shortsHeaderLayout && (hudEnabled || qrEnabled || pngTuberEnabled)
        val topCeilingNdc = SHORTS_TOP_SAFE_NDC
        val bottomFloorNdc = gameTopNdc.coerceIn(-0.60f, 0.92f) + 0.015f
        val totalBandNdc = topCeilingNdc - bottomFloorNdc
        val rowGapNdc = 0.012f
        val minLoupeNdc = 0.095f

        var hasLoupeBand = false
        var loupeBottomNdc = 0f
        var loupeTopNdc = 0f
        var headerTopNdc = topCeilingNdc
        var headerBandNdc = 0f

        if (hasHeaderOverlays) {
            val desiredHeaderNdc = when {
                !wantsLoupes -> if (qrEnabled || pngTuberEnabled) 0.188f else 0.084f
                qrEnabled -> 0.150f
                pngTuberEnabled -> 0.112f
                else -> 0.078f
            }

            if (wantsLoupes && totalBandNdc >= (0.060f + rowGapNdc + minLoupeNdc)) {
                val maxHeaderForLoupes = (totalBandNdc - rowGapNdc - minLoupeNdc).coerceAtLeast(0.060f)
                headerBandNdc = min(desiredHeaderNdc, maxHeaderForLoupes)
                loupeBottomNdc = bottomFloorNdc
                loupeTopNdc = headerTopNdc - headerBandNdc - rowGapNdc
                hasLoupeBand = (loupeTopNdc - loupeBottomNdc) >= 0.080f
            } else {
                // Either Loupes are OFF/Paused (Mode C) or FILL mode leaves no Loupe band
                headerBandNdc = if (totalBandNdc >= 0.075f) {
                    min(desiredHeaderNdc, totalBandNdc)
                } else {
                    desiredHeaderNdc
                }
                hasLoupeBand = false
            }
        } else if (wantsLoupes && totalBandNdc >= 0.080f) {
            // Mode A: Loupes ON, Shorts Pro Header OFF
            loupeBottomNdc = bottomFloorNdc
            loupeTopNdc = min(topCeilingNdc, loupeBottomNdc + 0.285f)
            hasLoupeBand = (loupeTopNdc - loupeBottomNdc) >= 0.080f
        }

        var hudRect: NdcRect? = null
        var pngTuberRect: NdcRect? = null
        var qrRect: NdcRect? = null

        if (hasHeaderOverlays && headerBandNdc > 0.02f) {
            val headerBottomNdc = headerTopNdc - headerBandNdc
            val leftEdge = -0.96f
            var rightCursor = 0.96f
            val gapX = 0.04f

            // 1. Corner QR (Rightmost inside Row 1)
            if (qrEnabled) {
                var qrH = headerBandNdc
                var qrW = qrH * invDstAspect
                val maxQrW = 0.44f
                if (qrW > maxQrW) {
                    qrW = maxQrW
                    qrH = qrW / invDstAspect
                }
                val qrRight = rightCursor
                val qrLeft = qrRight - qrW
                val cy = (headerTopNdc + headerBottomNdc) * 0.5f
                qrRect = NdcRect(
                    left = qrLeft,
                    bottom = cy - qrH * 0.5f,
                    right = qrRight,
                    top = cy + qrH * 0.5f
                )
                rightCursor = qrLeft - gapX
            }

            // 2. PNGTuber Avatar (Directly left of QR, or rightmost when QR is OFF)
            if (pngTuberEnabled) {
                val bounceHeadroom = min(0.014f, headerBandNdc * 0.12f)
                val maxAvH = (headerBandNdc - bounceHeadroom).coerceAtLeast(0.036f)
                var avH = if (qrEnabled) min(maxAvH, 0.138f) else min(maxAvH, 0.160f)
                var avW = avH * safePngAspect * invDstAspect
                val maxAvW = 0.36f
                if (avW > maxAvW) {
                    avW = maxAvW
                    avH = avW / (safePngAspect * invDstAspect)
                }
                val avRight = rightCursor
                val avLeft = avRight - avW
                val baseBottom = headerBottomNdc + (headerBandNdc - bounceHeadroom - avH) * 0.5f
                val clampedBounce = if (pngTuberBounceNdc.isFinite()) {
                    pngTuberBounceNdc.coerceIn(0f, bounceHeadroom)
                } else {
                    0f
                }
                val avBottom = (baseBottom + clampedBounce).coerceIn(headerBottomNdc, headerTopNdc - avH)
                pngTuberRect = NdcRect(
                    left = avLeft,
                    bottom = avBottom,
                    right = avRight,
                    top = avBottom + avH
                )
                rightCursor = avLeft - gapX
            }

            // 3. SolHUD™ Live Telemetry Badge (Leftmost inside Row 1)
            if (hudEnabled) {
                val availW = (rightCursor - leftEdge).coerceAtLeast(0.24f)
                val targetMaxW = min(0.94f, availW)
                val hudAspect = HUD_WIDTH_PX.toFloat() / HUD_HEIGHT_PX.toFloat()
                val hFromW = targetMaxW / (hudAspect * invDstAspect)
                val hudH = min(headerBandNdc, hFromW)
                val hudW = min(targetMaxW, hudH * hudAspect * invDstAspect)
                val cy = (headerTopNdc + headerBottomNdc) * 0.5f
                hudRect = NdcRect(
                    left = leftEdge,
                    bottom = cy - hudH * 0.5f,
                    right = leftEdge + hudW,
                    top = cy + hudH * 0.5f
                )
            }
        }

        return ShortsTopLayout(
            hasLoupeBand = hasLoupeBand,
            loupeBottomNdc = loupeBottomNdc,
            loupeTopNdc = loupeTopNdc,
            scoreSlotLeftNdc = SHORTS_SCORE_SLOT_LEFT_NDC,
            scoreSlotRightNdc = SHORTS_SCORE_SLOT_RIGHT_NDC,
            minimapSlotLeftNdc = SHORTS_MINIMAP_SLOT_LEFT_NDC,
            minimapSlotRightNdc = SHORTS_MINIMAP_SLOT_RIGHT_NDC,
            hudRect = hudRect,
            pngTuberRect = pngTuberRect,
            qrRect = qrRect
        )
    }

    /**
     * 3-second EMA bitrate smoother for SolHUD™ so a single static dark/lobby frame
     * never causes an abrupt 0.1M readout flash.
     */
    fun smoothHudBitrateMbps(
        previousMbps: Float,
        currentMbps: Float,
        alpha: Float = 0.35f
    ): Float {
        val safePrev = if (previousMbps.isFinite() && previousMbps > 0f) previousMbps else 0f
        if (!currentMbps.isFinite() || currentMbps < 0f) return safePrev
        if (safePrev <= 0.05f) return currentMbps
        val a = alpha.coerceIn(0.05f, 1.0f)
        return (a * currentMbps) + ((1.0f - a) * safePrev)
    }

    // === 2. SOLHUD™ LIVE GPU TELEMETRY BADGE (ZERO-ALLOCATION REUSED SCRATCH BITMAP) ===

    fun createHudScratchBitmap(): Bitmap {
        return Bitmap.createBitmap(HUD_WIDTH_PX, HUD_HEIGHT_PX, Bitmap.Config.ARGB_8888)
    }

    fun formatHudTelemetryLine(
        fps: Int,
        tempC: Float,
        bitrateMbps: Float,
        isThrottled30: Boolean
    ): String {
        val fpsTag = if (isThrottled30) "${max(0, fps)} FPS COOL" else "${max(0, fps)} FPS"
        val tempTag = if (tempC > 1.0f) String.format(Locale.US, "%.1f°C", tempC) else "--.-°C"
        val mbpsTag = String.format(Locale.US, "%.1fM", max(0f, bitrateMbps))
        return "$fpsTag  •  $tempTag  •  $mbpsTag"
    }

    fun renderHudIntoBitmap(
        target: Bitmap,
        fps: Int,
        tempC: Float,
        bitrateMbps: Float,
        isThrottled30: Boolean
    ) {
        val canvas = Canvas(target)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val w = target.width.toFloat()
        val h = target.height.toFloat()
        val pillRect = RectF(4f, 6f, w - 4f, h - 6f)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC070D17")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(pillRect, 28f, 28f, fillPaint)

        val accentColor = when {
            isThrottled30 || tempC >= 43.0f -> Color.parseColor("#FF9100")
            tempC >= 40.5f -> Color.parseColor("#FFD600")
            else -> Color.parseColor("#00E5FF")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            shader = LinearGradient(
                pillRect.left, pillRect.top, pillRect.right, pillRect.bottom,
                accentColor,
                Color.parseColor("#2979FF"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(pillRect, 28f, 28f, borderPaint)

        // Live pulse indicator dot
        val dotCx = pillRect.left + 26f
        val dotCy = h * 0.5f
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentColor and 0x00FFFFFF) or 0x44000000
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
        }
        canvas.drawCircle(dotCx, dotCy, 11f, haloPaint)
        canvas.drawCircle(dotCx, dotCy, 5.5f, dotPaint)

        val line = formatHudTelemetryLine(fps, tempC, bitrateMbps, isThrottled30)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 23f
        }
        val maxTextW = w - 68f
        while (textPaint.measureText(line) > maxTextW && textPaint.textSize > 14f) {
            textPaint.textSize -= 1.0f
        }
        val fm = textPaint.fontMetrics
        val baselineY = (h * 0.5f) - (fm.ascent + fm.descent) * 0.5f
        canvas.drawText(line, pillRect.left + 48f, baselineY, textPaint)
    }

    // === 3. PURE-KOTLIN ISO/IEC 18004 QR CODE GENERATOR (VERSIONS 1..10, LEVEL M, BYTE MODE) ===

    fun encodeQrMatrix(rawText: String): QrMatrix? {
        val clean = rawText.trim()
        if (clean.isEmpty()) return null
        val payload = clean.toByteArray(StandardCharsets.UTF_8)
        if (payload.isEmpty() || payload.size > MAX_QR_UTF8_BYTES) return null

        val spec = VERSION_SPECS_M.firstOrNull { payload.size <= it.maxBytePayload } ?: return null
        val dataCodewords = buildDataCodewords(payload, spec)
        val finalStream = interleaveBlocksWithEcc(dataCodewords, spec)

        val size = spec.version * 4 + 17
        val baseModules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        placeFunctionPatterns(spec, size, baseModules, isFunction)
        placeDataBits(size, finalStream, baseModules, isFunction)

        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestModules: Array<BooleanArray>? = null

        for (mask in 0..7) {
            val candidate = Array(size) { r -> baseModules[r].copyOf() }
            applyMask(size, candidate, isFunction, mask)
            drawFormatBits(size, candidate, mask)
            val penalty = computeMaskPenalty(size, candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestModules = candidate
            }
        }

        val chosen = bestModules ?: return null
        drawFormatBits(size, chosen, bestMask)
        return QrMatrix(version = spec.version, size = size, modules = chosen)
    }

    fun generateQrOverlayBitmap(rawText: String, targetSizePx: Int = QR_SIZE_PX): Bitmap? {
        val qr = encodeQrMatrix(rawText) ?: return null
        val quietModules = 4
        val totalModules = qr.size + quietModules * 2
        val bmpSize = max(128, targetSizePx)
        val bmp = Bitmap.createBitmap(bmpSize, bmpSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Crisp opaque white card with subtle rounded corners (high contrast for reliable camera scan)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(0f, 0f, bmpSize.toFloat(), bmpSize.toFloat()), 16f, 16f, cardPaint)

        // Integer module pixel sizing ensures razor-sharp edges under GL_NEAREST (zero fractional jitter)
        val cellPx = max(1, bmpSize / totalModules)
        val drawnSpan = cellPx * totalModules
        val offsetPx = (bmpSize - drawnSpan) / 2

        val darkPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        for (y in 0 until qr.size) {
            val row = qr.modules[y]
            val py = offsetPx + (y + quietModules) * cellPx
            for (x in 0 until qr.size) {
                if (row[x]) {
                    val px = offsetPx + (x + quietModules) * cellPx
                    canvas.drawRect(
                        px.toFloat(),
                        py.toFloat(),
                        (px + cellPx).toFloat(),
                        (py + cellPx).toFloat(),
                        darkPaint
                    )
                }
            }
        }
        return bmp
    }

    private fun buildDataCodewords(payload: ByteArray, spec: QrVersionSpecM): ByteArray {
        val capacityBits = spec.dataCodewords * 8
        val bits = BooleanArray(capacityBits)
        var bitPos = 0

        fun appendBits(value: Int, count: Int) {
            for (i in (count - 1) downTo 0) {
                if (bitPos < capacityBits) {
                    bits[bitPos++] = ((value ushr i) and 1) != 0
                }
            }
        }

        // Byte mode indicator (0100) + character count indicator (8 or 16 bits)
        appendBits(0b0100, 4)
        appendBits(payload.size, spec.countIndicatorBits)
        for (b in payload) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        // Terminator (up to 4 zero bits) + zero-pad to byte boundary
        val terminator = min(4, capacityBits - bitPos)
        appendBits(0, terminator)
        val remInByte = (8 - (bitPos % 8)) % 8
        appendBits(0, remInByte)

        val out = ByteArray(spec.dataCodewords)
        val writtenBytes = bitPos / 8
        for (i in 0 until writtenBytes) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or (if (bits[i * 8 + b]) 1 else 0)
            }
            out[i] = v.toByte()
        }

        // Standard ISO/IEC 18004 alternating pad bytes (0xEC, 0x11)
        var padToggle = false
        for (i in writtenBytes until spec.dataCodewords) {
            out[i] = if (padToggle) 0x11.toByte() else 0xEC.toByte()
            padToggle = !padToggle
        }
        return out
    }

    private fun interleaveBlocksWithEcc(data: ByteArray, spec: QrVersionSpecM): ByteArray {
        val totalBlocks = spec.group1Blocks + spec.group2Blocks
        val dataBlocks = ArrayList<ByteArray>(totalBlocks)
        val eccBlocks = ArrayList<ByteArray>(totalBlocks)
        val genPoly = rsComputeGeneratorPoly(spec.ecPerBlock)

        var offset = 0
        for (i in 0 until spec.group1Blocks) {
            val blk = data.copyOfRange(offset, offset + spec.group1DataCw)
            offset += spec.group1DataCw
            dataBlocks.add(blk)
            eccBlocks.add(rsComputeRemainder(blk, genPoly))
        }
        for (i in 0 until spec.group2Blocks) {
            val blk = data.copyOfRange(offset, offset + spec.group2DataCw)
            offset += spec.group2DataCw
            dataBlocks.add(blk)
            eccBlocks.add(rsComputeRemainder(blk, genPoly))
        }

        val out = ByteArray(spec.totalCodewords)
        var outIdx = 0
        val maxDataCw = max(spec.group1DataCw, spec.group2DataCw)
        for (col in 0 until maxDataCw) {
            for (blk in dataBlocks) {
                if (col < blk.size) out[outIdx++] = blk[col]
            }
        }
        for (col in 0 until spec.ecPerBlock) {
            for (ecc in eccBlocks) {
                out[outIdx++] = ecc[col]
            }
        }
        return out
    }

    // GF(256) multiplication modulo QR primitive polynomial 0x11D (x^8 + x^4 + x^3 + x^2 + 1)
    private fun gfMultiply(x: Int, y: Int): Int {
        var a = x and 0xFF
        var b = y and 0xFF
        var res = 0
        for (i in 0 until 8) {
            if ((b and 1) != 0) res = res xor a
            val hiBit = a and 0x80
            a = (a shl 1) and 0xFF
            if (hiBit != 0) a = a xor 0x1D
            b = b ushr 1
        }
        return res
    }

    private fun rsComputeGeneratorPoly(degree: Int): ByteArray {
        val poly = ByteArray(degree)
        poly[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                val prod = gfMultiply(poly[j].toInt() and 0xFF, root)
                val next = if (j + 1 < degree) (poly[j + 1].toInt() and 0xFF) else 0
                poly[j] = (prod xor next).toByte()
            }
            root = gfMultiply(root, 0x02)
        }
        return poly
    }

    private fun rsComputeRemainder(data: ByteArray, genPoly: ByteArray): ByteArray {
        val deg = genPoly.size
        val rem = ByteArray(deg)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor (rem[0].toInt() and 0xFF)
            System.arraycopy(rem, 1, rem, 0, deg - 1)
            rem[deg - 1] = 0
            for (i in 0 until deg) {
                rem[i] = ((rem[i].toInt() and 0xFF) xor gfMultiply(genPoly[i].toInt() and 0xFF, factor)).toByte()
            }
        }
        return rem
    }

    private fun placeFunctionPatterns(
        spec: QrVersionSpecM,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        fun setFunc(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y][x] = dark
                isFunction[y][x] = true
            }
        }

        // Timing patterns (row 6 and col 6)
        for (i in 0 until size) {
            setFunc(6, i, (i % 2) == 0)
            setFunc(i, 6, (i % 2) == 0)
        }

        // 3 Finder patterns + 1-module white separators
        val finderCenters = arrayOf(3 to 3, (size - 4) to 3, 3 to (size - 4))
        for ((cx, cy) in finderCenters) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = max(abs(dx), abs(dy))
                    val dark = (dist != 2 && dist != 4)
                    setFunc(cx + dx, cy + dy, dark)
                }
            }
        }

        // Alignment patterns (skipping the 3 finder corners)
        val centers = spec.alignmentCenters
        for (cy in centers) {
            for (cx in centers) {
                val overlapsFinder =
                    (cx == centers.first() && cy == centers.first()) ||
                        (cx == centers.first() && cy == centers.last()) ||
                        (cx == centers.last() && cy == centers.first())
                if (overlapsFinder) continue
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val dist = max(abs(dx), abs(dy))
                        setFunc(cx + dx, cy + dy, dist != 1)
                    }
                }
            }
        }

        // Reserve format information cells + fixed dark module
        for (i in 0..8) {
            if (i != 6) {
                setFunc(8, i, false)
                setFunc(i, 8, false)
            }
        }
        for (i in 0..7) {
            setFunc(size - 1 - i, 8, false)
        }
        for (i in 0..6) {
            setFunc(8, size - 1 - i, false)
        }
        setFunc(8, size - 8, true)

        // Version information blocks (Versions 7..10 only, Golay BCH(18,6))
        if (spec.version >= 7) {
            var rem = spec.version shl 12
            for (i in 17 downTo 12) {
                if (((rem ushr i) and 1) != 0) {
                    rem = rem xor (0x1F25 shl (i - 12))
                }
            }
            val verBits = (spec.version shl 12) or rem
            for (i in 0 until 18) {
                val dark = ((verBits ushr i) and 1) != 0
                val a = size - 11 + (i % 3)
                val b = i / 3
                setFunc(a, b, dark)
                setFunc(b, a, dark)
            }
        }
    }

    private fun placeDataBits(
        size: Int,
        codewords: ByteArray,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        val totalBits = codewords.size * 8
        var bitIdx = 0
        var right = size - 1
        var upward = true

        while (right >= 1) {
            if (right == 6) right = 5
            for (step in 0 until size) {
                val y = if (upward) (size - 1 - step) else step
                for (colOff in 0..1) {
                    val x = right - colOff
                    if (!isFunction[y][x]) {
                        val dark = if (bitIdx < totalBits) {
                            (((codewords[bitIdx ushr 3].toInt() and 0xFF) ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else {
                            false
                        }
                        modules[y][x] = dark
                        bitIdx++
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        mask: Int
    ) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isFunction[y][x]) continue
                val invert = when (mask) {
                    0 -> ((y + x) % 2) == 0
                    1 -> (y % 2) == 0
                    2 -> (x % 3) == 0
                    3 -> ((y + x) % 3) == 0
                    4 -> ((y / 2 + x / 3) % 2) == 0
                    5 -> ((y * x) % 2 + (y * x) % 3) == 0
                    6 -> (((y * x) % 2 + (y * x) % 3) % 2) == 0
                    else -> (((y + x) % 2 + (y * x) % 3) % 2) == 0
                }
                if (invert) {
                    modules[y][x] = !modules[y][x]
                }
            }
        }
    }

    private fun drawFormatBits(size: Int, modules: Array<BooleanArray>, mask: Int) {
        // Error Correction Level M = 00 -> 5-bit data is simply mask (0..7)
        val data5 = mask and 0x07
        var rem = data5 shl 10
        for (i in 14 downTo 10) {
            if (((rem ushr i) and 1) != 0) {
                rem = rem xor (0x537 shl (i - 10))
            }
        }
        val bits = ((data5 shl 10) or rem) xor 0x5412

        // Copy 1 around top-left finder
        for (i in 0..5) modules[i][8] = ((bits ushr i) and 1) != 0
        modules[7][8] = ((bits ushr 6) and 1) != 0
        modules[8][8] = ((bits ushr 7) and 1) != 0
        modules[8][7] = ((bits ushr 8) and 1) != 0
        for (i in 9..14) modules[8][14 - i] = ((bits ushr i) and 1) != 0

        // Copy 2 along top-right and bottom-left finders
        for (i in 0..7) modules[8][size - 1 - i] = ((bits ushr i) and 1) != 0
        for (i in 8..14) modules[size - 15 + i][8] = ((bits ushr i) and 1) != 0
        modules[size - 8][8] = true
    }

    private fun computeMaskPenalty(size: Int, modules: Array<BooleanArray>): Int {
        var penalty = 0

        // Rule 1: Runs of >=5 same-color modules in rows and columns
        for (y in 0 until size) {
            var runColor = modules[y][0]
            var runLen = 1
            for (x in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
        }
        for (x in 0 until size) {
            var runColor = modules[0][x]
            var runLen = 1
            for (y in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
        }

        // Rule 2: 2x2 blocks of identical color
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    penalty += 3
                }
            }
        }

        // Rule 3: Finder-like 1:1:3:1:1 patterns (1011101 with 4 light modules on either side)
        for (y in 0 until size) {
            for (x in 0..size - 11) {
                val r = modules[y]
                if (matchesFinderPatternRow(r, x)) penalty += 40
            }
        }
        for (x in 0 until size) {
            for (y in 0..size - 11) {
                if (matchesFinderPatternCol(modules, x, y)) penalty += 40
            }
        }

        // Rule 4: Dark/light ratio deviation from 50%
        var darkCount = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (modules[y][x]) darkCount++
            }
        }
        val total = size * size
        val k = (abs(darkCount * 20 - total * 10) + total - 1) / total - 1
        penalty += max(0, k) * 10

        return penalty
    }

    private fun matchesFinderPatternRow(r: BooleanArray, x: Int): Boolean {
        val pat1 = r[x] && !r[x + 1] && r[x + 2] && r[x + 3] && r[x + 4] && !r[x + 5] && r[x + 6] &&
            !r[x + 7] && !r[x + 8] && !r[x + 9] && !r[x + 10]
        val pat2 = !r[x] && !r[x + 1] && !r[x + 2] && !r[x + 3] &&
            r[x + 4] && !r[x + 5] && r[x + 6] && r[x + 7] && r[x + 8] && !r[x + 9] && r[x + 10]
        return pat1 || pat2
    }

    private fun matchesFinderPatternCol(m: Array<BooleanArray>, x: Int, y: Int): Boolean {
        val pat1 = m[y][x] && !m[y + 1][x] && m[y + 2][x] && m[y + 3][x] && m[y + 4][x] && !m[y + 5][x] && m[y + 6][x] &&
            !m[y + 7][x] && !m[y + 8][x] && !m[y + 9][x] && !m[y + 10][x]
        val pat2 = !m[y][x] && !m[y + 1][x] && !m[y + 2][x] && !m[y + 3][x] &&
            m[y + 4][x] && !m[y + 5][x] && m[y + 6][x] && m[y + 7][x] && m[y + 8][x] && !m[y + 9][x] && m[y + 10][x]
        return pat1 || pat2
    }

    // === 4. ZERO-PERMISSION CLEAN SCREENSHOT SAVER (ANDROID 10–18 MEDIASTORE IS_PENDING) ===

    fun flipRgbaRowsVerticallyInPlace(rgbaBytes: ByteArray, width: Int, height: Int) {
        val rowStride = width * 4
        if (width <= 0 || height <= 1 || rgbaBytes.size < rowStride * height) return
        val tmpRow = ByteArray(rowStride)
        val halfH = height / 2
        for (y in 0 until halfH) {
            val topOff = y * rowStride
            val botOff = (height - 1 - y) * rowStride
            System.arraycopy(rgbaBytes, topOff, tmpRow, 0, rowStride)
            System.arraycopy(rgbaBytes, botOff, rgbaBytes, topOff, rowStride)
            System.arraycopy(tmpRow, 0, rgbaBytes, botOff, rowStride)
        }
    }

    fun saveRgbaScreenshotToMediaStore(
        context: Context,
        rgbaBytes: ByteArray,
        width: Int,
        height: Int
    ): Result<String> {
        if (width <= 0 || height <= 0 || rgbaBytes.size < width * height * 4) {
            return Result.failure(IllegalArgumentException("Invalid screenshot pixel buffer."))
        }
        var bmp: Bitmap? = null
        val resolver = context.applicationContext.contentResolver
        var insertedUri: android.net.Uri? = null
        return try {
            flipRgbaRowsVerticallyInPlace(rgbaBytes, width, height)
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(rgbaBytes, 0, width * height * 4))

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "SolStream_${stamp}_${width}x${height}.jpg"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/SolStream"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return Result.failure(IllegalStateException("MediaStore refused screenshot entry."))
            insertedUri = uri

            val wroteOk = resolver.openOutputStream(uri)?.use { out ->
                val ok = bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
                ok
            } ?: false

            if (!wroteOk) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                return Result.failure(IllegalStateException("JPEG compression failed."))
            }

            val finishValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, finishValues, null, null)
            Result.success(fileName)
        } catch (e: Exception) {
            insertedUri?.let { uri ->
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            }
            Result.failure(e)
        } finally {
            try {
                if (bmp != null && !bmp.isRecycled) bmp.recycle()
            } catch (_: Exception) {}
        }
    }
}
