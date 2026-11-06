/*
 * SolStream Studio — Ultra-Lightweight Mobile Gaming Broadcast Engine
 * Copyright (c) 2026 Sol (@Sol_CODM / github.com/PppP01). All rights reserved.
 * SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
 * Commercial redistribution, rebranding, or app-store monetization prohibited without written license.
 */

package com.solgaming.solstreamstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * Zero-permission graphic asset store for Starting Soon, BRB, transparent .PNG overlays,
 * 9:16 Shorts posters, and GPU-cached Live Text Banners.
 *
 * Uses Android's system Photo Picker / Document URI streams directly—requiring ZERO
 * READ_EXTERNAL_STORAGE or READ_MEDIA_IMAGES permissions.
 */
object SolSceneAssetStore {

    enum class AssetSlot(val fileName: String, val maxDimensionPx: Int) {
        STARTING_SOON("scene_starting_soon.png", 1920),
        BRB_SCENE("scene_brb.png", 1920),
        OVERLAY_PNG("stream_overlay_rgba.png", 2560),
        SHORTS_POSTER("shorts_backdrop_poster.png", 1920)
    }

    private const val SCENES_DIR = "scenes"
    private val revisionCounter = AtomicInteger(1)

    fun currentRevision(): Int = revisionCounter.get()

    fun bumpRevision() {
        revisionCounter.incrementAndGet()
    }

    private fun slotFile(context: Context, slot: AssetSlot): File {
        val dir = File(context.applicationContext.filesDir, SCENES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, slot.fileName)
    }

    fun hasCustomAsset(context: Context, slot: AssetSlot): Boolean {
        val f = slotFile(context, slot)
        return f.exists() && f.length() > 64L
    }

    fun clearCustomAsset(context: Context, slot: AssetSlot): Boolean {
        val f = slotFile(context, slot)
        val ok = if (f.exists()) f.delete() else true
        bumpRevision()
        return ok
    }

    /**
     * Imports a user-selected image URI safely off the UI thread, honoring EXIF orientation,
     * preserving 8-bit PNG alpha transparency, and clamping dimensions to prevent RAM bloat.
     */
    fun importAssetFromUri(context: Context, uri: Uri, slot: AssetSlot): Result<String> {
        val resolver = context.applicationContext.contentResolver
        return try {
            // 1. Read EXIF rotation if present (prevents sideways phone gallery photos)
            val rotationDeg = try {
                resolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) {
                0f
            }

            // 2. Probe dimensions without allocating pixel memory
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { probeStream ->
                BitmapFactory.decodeStream(probeStream, null, boundsOpts)
            }
            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            if (srcW <= 0 || srcH <= 0) {
                return Result.failure(IllegalArgumentException("Selected file is not a valid image."))
            }

            // 3. Compute power-of-2 sub-sampling so even 50 MP gallery photos decode in <12 MB RAM
            val maxTarget = slot.maxDimensionPx
            var sampleSize = 1
            while ((srcW / sampleSize) > maxTarget * 2 || (srcH / sampleSize) > maxTarget * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPremultiplied = true
            }
            val decoded = resolver.openInputStream(uri)?.use { imgStream ->
                BitmapFactory.decodeStream(imgStream, null, decodeOpts)
            } ?: return Result.failure(IllegalStateException("Could not decode selected image."))

            // 4. Apply EXIF rotation and exact max-dimension clamp
            val finalBitmap = transformAndClampBitmap(decoded, rotationDeg, maxTarget)
            if (finalBitmap !== decoded) {
                decoded.recycle()
            }

            val outFile = slotFile(context, slot)
            val tempFile = File(outFile.parentFile, "${slot.fileName}.tmp")
            FileOutputStream(tempFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            val finalW = finalBitmap.width
            val finalH = finalBitmap.height
            finalBitmap.recycle()

            if (outFile.exists()) outFile.delete()
            tempFile.renameTo(outFile)
            bumpRevision()

            Result.success("${finalW}x${finalH} saved")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun transformAndClampBitmap(src: Bitmap, rotationDeg: Float, maxDim: Int): Bitmap {
        val rotW = if (rotationDeg == 90f || rotationDeg == 270f) src.height else src.width
        val rotH = if (rotationDeg == 90f || rotationDeg == 270f) src.width else src.height
        val longest = max(rotW, rotH)
        val scale = if (longest > maxDim) maxDim.toFloat() / longest.toFloat() else 1.0f

        if (rotationDeg == 0f && scale >= 0.999f && src.config == Bitmap.Config.ARGB_8888) {
            return src
        }

        val matrix = Matrix()
        if (rotationDeg != 0f) {
            matrix.postRotate(rotationDeg)
        }
        if (scale < 0.999f) {
            matrix.postScale(scale, scale)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Loads a saved ARGB_8888 bitmap for OpenGL texture upload (guaranteed non-HARDWARE config).
     */
    fun loadSavedBitmapForGl(context: Context, slot: AssetSlot): Bitmap? {
        val file = slotFile(context, slot)
        if (!file.exists() || file.length() <= 64L) return null
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPremultiplied = true
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads the user's saved scene image if present; otherwise generates a clean, lightweight
     * esports broadcast slate ("STARTING SOON" or "BE RIGHT BACK") matched to stream orientation.
     */
    fun loadOrGenerateSceneBitmap(
        context: Context,
        slot: AssetSlot,
        isVertical: Boolean,
        customSubtitle: String
    ): Bitmap {
        loadSavedBitmapForGl(context, slot)?.let { return it }

        val w = if (isVertical) 720 else 1280
        val h = if (isVertical) 1280 else 720
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(
                    Color.parseColor("#060A12"),
                    Color.parseColor("#0D1829"),
                    Color.parseColor("#070B14")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val cardMarginX = w * 0.09f
        val cardHeight = if (isVertical) h * 0.28f else h * 0.44f
        val cardTop = (h - cardHeight) * 0.5f
        val cardRect = RectF(cardMarginX, cardTop, w - cardMarginX, cardTop + cardHeight)

        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC0B1320")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, 28f, 28f, cardFill)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                Color.parseColor("#00E5FF"),
                Color.parseColor("#FFC400"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 28f, 28f, borderPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isVertical) 22f else 24f
            letterSpacing = 0.14f
        }
        canvas.drawText(
            "SOLSTREAM STUDIO • LIVE BROADCAST",
            w * 0.5f,
            cardRect.top + cardHeight * 0.28f,
            badgePaint
        )

        val headline = if (slot == AssetSlot.STARTING_SOON) "STARTING SOON" else "BE RIGHT BACK"
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isVertical) 50f else 62f
        }
        canvas.drawText(headline, w * 0.5f, cardRect.top + cardHeight * 0.58f, titlePaint)

        val subLine = customSubtitle.trim().ifEmpty {
            if (slot == AssetSlot.STARTING_SOON) {
                "Warming up 120 FPS lobby • Audio & stream locked in"
            } else {
                "Privacy shield active • Returning to match shortly"
            }
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0BEC5")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = if (isVertical) 21f else 24f
        }
        canvas.drawText(
            subLine.take(56),
            w * 0.5f,
            cardRect.top + cardHeight * 0.80f,
            subPaint
        )

        return bmp
    }

    /**
     * Renders a compact, premultiplied-alpha 1280x92 GPU banner strip (<0.48 MB RAM)
     * uploaded ONCE whenever text changes—zero per-frame CPU drawing overhead.
     */
    fun buildLiveBannerBitmap(rawText: String): Bitmap? {
        val clean = rawText.trim()
        if (clean.isEmpty()) return null

        val texW = 1280
        val texH = 92
        val bmp = Bitmap.createBitmap(texW, texH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 31f
        }
        val clippedText = clean.take(68)
        var measuredTextW = textPaint.measureText(clippedText)
        if (measuredTextW > texW - 150f) {
            textPaint.textSize = 24f
            measuredTextW = min(texW - 130f, textPaint.measureText(clippedText))
        }

        val pillW = (measuredTextW + 108f).coerceIn(280f, texW - 24f)
        val left = (texW - pillW) * 0.5f
        val pillRect = RectF(left, 10f, left + pillW, texH - 10f)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9080E18")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(pillRect, 36f, 36f, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
            shader = LinearGradient(
                pillRect.left, pillRect.top, pillRect.right, pillRect.bottom,
                Color.parseColor("#00E5FF"),
                Color.parseColor("#FFC400"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(pillRect, 36f, 36f, strokePaint)

        // Glowing crimson live dot on the left side of the pill
        val dotCx = pillRect.left + 32f
        val dotCy = texH * 0.5f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55FF1744")
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF1744")
        }
        canvas.drawCircle(dotCx, dotCy, 12f, glowPaint)
        canvas.drawCircle(dotCx, dotCy, 6.5f, dotPaint)

        val fm = textPaint.fontMetrics
        val baselineY = (texH * 0.5f) - (fm.ascent + fm.descent) * 0.5f
        canvas.drawText(
            clippedText.uppercase(Locale.US).let { if (clean == it) it else clippedText },
            pillRect.left + 56f,
            baselineY,
            textPaint
        )

        return bmp
    }
}
