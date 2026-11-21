/*
 * ॐ नमः शिवाय (Om Namah Shivaya)
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
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

object SolSceneAssetStore {

    enum class AssetSlot(val fileName: String, val maxDimensionPx: Int) {
        STARTING_SOON("scene_starting_soon.png", 1920),
        BRB_SCENE("scene_brb.png", 1920),
        OVERLAY_PNG("stream_overlay_rgba.png", 2560),
        SHORTS_POSTER("shorts_backdrop_poster.png", 1920),
        PNGTUBER_IDLE("pngtuber_idle.png", 512),
        PNGTUBER_TALK("pngtuber_talk.png", 512)
    }

    private const val SCENES_DIR = "scenes"
    private val fileIoLock = Any()
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

    fun hasCustomAsset(context: Context, slot: AssetSlot): Boolean = synchronized(fileIoLock) {
        val f = slotFile(context, slot)
        f.exists() && f.length() > 64L
    }

    fun clearCustomAsset(context: Context, slot: AssetSlot): Boolean = synchronized(fileIoLock) {
        val f = slotFile(context, slot)
        val ok = if (f.exists()) f.delete() else true
        bumpRevision()
        ok
    }

    /**
     * Atomically replaces [outFile] with [tempFile]. If direct rename fails, copies into a separate
     * staging file first and unconditionally restores [backupFile] if any step fails or throws,
     * so a partially written destination file can never block rollback.
     */
    internal fun replaceFileSafely(tempFile: File, outFile: File): Boolean = synchronized(fileIoLock) {
        if (!tempFile.exists() || tempFile.length() <= 64L) return false
        val expectedLength = tempFile.length()
        val backupFile = File(outFile.parentFile, "${outFile.name}.bak")
        val copyStageFile = File(outFile.parentFile, "${outFile.name}.stage")

        try {
            if (backupFile.exists()) backupFile.delete()
            if (copyStageFile.exists()) copyStageFile.delete()

            if (outFile.exists()) {
                val backedUp = outFile.renameTo(backupFile) || run {
                    FileInputStream(outFile).use { fis ->
                        FileOutputStream(backupFile).use { fos ->
                            fis.copyTo(fos)
                            fos.flush()
                        }
                    }
                    backupFile.exists() && backupFile.length() == outFile.length() && outFile.delete()
                }
                if (!backedUp) {
                    return false
                }
            }

            val movedDirectly = tempFile.renameTo(outFile) &&
                outFile.exists() &&
                outFile.length() == expectedLength

            val committed = if (movedDirectly) {
                true
            } else {
                FileInputStream(tempFile).use { fis ->
                    FileOutputStream(copyStageFile).use { fos ->
                        fis.copyTo(fos)
                        fos.flush()
                    }
                }
                val stageValid = copyStageFile.exists() && copyStageFile.length() == expectedLength
                if (outFile.exists()) outFile.delete()
                stageValid && copyStageFile.renameTo(outFile) &&
                    outFile.exists() &&
                    outFile.length() == expectedLength
            }

            if (committed) {
                if (backupFile.exists()) backupFile.delete()
                return true
            }

            restoreBackupUnconditionally(backupFile, outFile)
            return false
        } catch (_: Exception) {
            restoreBackupUnconditionally(backupFile, outFile)
            return false
        } finally {
            if (tempFile.exists()) {
                try { tempFile.delete() } catch (_: Exception) {}
            }
            if (copyStageFile.exists()) {
                try { copyStageFile.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun restoreBackupUnconditionally(backupFile: File, outFile: File) {
        if (!backupFile.exists()) return
        try {
            if (outFile.exists()) outFile.delete()
            if (!backupFile.renameTo(outFile)) {
                FileInputStream(backupFile).use { fis ->
                    FileOutputStream(outFile).use { fos ->
                        fis.copyTo(fos)
                        fos.flush()
                    }
                }
                backupFile.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun importAssetFromUri(context: Context, uri: Uri, slot: AssetSlot): Result<String> {
        val resolver = context.applicationContext.contentResolver
        var tempFile: File? = null
        var finalBitmap: Bitmap? = null
        return try {
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

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { probeStream ->
                BitmapFactory.decodeStream(probeStream, null, boundsOpts)
            }
            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            if (srcW <= 0 || srcH <= 0) {
                return Result.failure(IllegalArgumentException("Selected file is not a valid image."))
            }

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

            val transformed = transformAndClampBitmap(decoded, rotationDeg, maxTarget)
            if (transformed !== decoded) {
                decoded.recycle()
            }
            finalBitmap = transformed

            val outFile = slotFile(context, slot)
            val tmp = File(outFile.parentFile, "${slot.fileName}.tmp")
            tempFile = tmp

            val finalW = transformed.width
            val finalH = transformed.height
            val compressedOk = FileOutputStream(tmp).use { fos ->
                val ok = transformed.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
                ok
            }
            transformed.recycle()
            finalBitmap = null

            if (!compressedOk || !replaceFileSafely(tmp, outFile)) {
                return Result.failure(IllegalStateException("Failed to write image file to internal storage."))
            }

            bumpRevision()
            Result.success("${finalW}x${finalH} saved")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                if (finalBitmap != null && !finalBitmap.isRecycled) {
                    finalBitmap.recycle()
                }
            } catch (_: Exception) {}
            try {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun transformAndClampBitmap(src: Bitmap, rotationDeg: Float, maxDim: Int): Bitmap {
        val rotW = if (rotationDeg == 90f || rotationDeg == 270f) src.height else src.width
        val rotH = if (rotationDeg == 90f || rotationDeg == 270f) src.width else src.height
        val longest = max(rotW, rotH)
        val scale = if (longest > maxDim) maxDim.toFloat() / longest.toFloat() else 1.0f

        if (rotationDeg == 0f && scale >= 0.999f) {
            if (src.config == Bitmap.Config.ARGB_8888) {
                return src
            }
            return src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        }

        val matrix = Matrix()
        if (rotationDeg != 0f) {
            matrix.postRotate(rotationDeg)
        }
        if (scale < 0.999f) {
            matrix.postScale(scale, scale)
        }
        val transformed = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (transformed.config != Bitmap.Config.ARGB_8888) {
            val copy = transformed.copy(Bitmap.Config.ARGB_8888, false)
            if (copy != null) {
                if (transformed !== src) transformed.recycle()
                return copy
            }
        }
        return transformed
    }

    fun loadSavedBitmapForGl(context: Context, slot: AssetSlot): Bitmap? = synchronized(fileIoLock) {
        val file = slotFile(context, slot)
        if (!file.exists() || file.length() <= 64L) return@synchronized null
        try {
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
     * Loads the PNGTuber avatar pair for OpenGL:
     * - 2 custom PNGs uploaded -> returns (customIdle, customTalk)
     * - 1 custom PNG uploaded -> returns (customImg, null) so pipeline reuses 1 texture with smooth talk-hop
     * - 0 custom PNGs uploaded -> procedurally generates the Obsidian-Glass Cyber Visor Esports Avatar
     *   matched to the active Mic Studio voiceGenderProfile ("MALE" / "FEMALE" / "NEUTRAL").
     */
    fun loadPngTuberPairForGl(context: Context, genderProfile: String): Pair<Bitmap, Bitmap?> {
        val customIdle = loadSavedBitmapForGl(context, AssetSlot.PNGTUBER_IDLE)
        val customTalk = loadSavedBitmapForGl(context, AssetSlot.PNGTUBER_TALK)
        if (customIdle != null && customTalk != null) return customIdle to customTalk
        if (customIdle != null) return customIdle to null
        if (customTalk != null) return customTalk to null
        return generateCyberAvatarBitmap(isTalking = false, genderProfile = genderProfile) to
            generateCyberAvatarBitmap(isTalking = true, genderProfile = genderProfile)
    }

    /**
     * Generates a transparent 512x512 Obsidian-Glass Cyber Visor Esports Avatar matching SolStream's theme
     * (zero APK bloat, zero CAMERA permission, auto-styled to MALE / FEMALE / NEUTRAL voice profile).
     */
    fun generateCyberAvatarBitmap(isTalking: Boolean, genderProfile: String): Bitmap {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val normGender = genderProfile.trim().uppercase(Locale.US)
        val accentStart = when (normGender) {
            "FEMALE" -> Color.parseColor("#FF4081")
            "MALE" -> Color.parseColor("#00E5FF")
            else -> Color.parseColor("#18FFFF")
        }
        val accentEnd = when (normGender) {
            "FEMALE" -> Color.parseColor("#7C4DFF")
            "MALE" -> Color.parseColor("#2979FF")
            else -> Color.parseColor("#00B0FF")
        }
        val glowColor = when (normGender) {
            "FEMALE" -> Color.parseColor(if (isTalking) "#88FF4081" else "#38FF4081")
            "MALE" -> Color.parseColor(if (isTalking) "#8800E5FF" else "#3800E5FF")
            else -> Color.parseColor(if (isTalking) "#8818FFFF" else "#3818FFFF")
        }

        val cx = size * 0.5f
        val cy = size * 0.5f

        // Outer neon speech aura ring
        val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (isTalking) 18f else 10f
            color = glowColor
        }
        canvas.drawCircle(cx, cy, 224f, auraPaint)

        // Obsidian medallion fill
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                64f, 48f, 448f, 464f,
                intArrayOf(
                    Color.parseColor("#F008101C"),
                    Color.parseColor("#F5111E32"),
                    Color.parseColor("#F0060B14")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, 212f, bodyPaint)

        // Crisp metallic gradient rim
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (isTalking) 8.5f else 5.5f
            shader = LinearGradient(48f, 48f, 464f, 464f, accentStart, accentEnd, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(cx, cy, 212f, rimPaint)

        // Tactical headset ear-cups
        val cupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#18283F")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(66f, 188f, 114f, 314f), 20f, 20f, cupPaint)
        canvas.drawRoundRect(RectF(398f, 188f, 446f, 314f), 20f, 20f, cupPaint)

        val cupAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentStart
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(78f, 214f, 92f, 288f), 6f, 6f, cupAccent)
        canvas.drawRoundRect(RectF(420f, 214f, 434f, 288f), 6f, 6f, cupAccent)

        // Cyber helmet silhouette
        val helmetPath = Path().apply {
            moveTo(126f, 218f)
            quadTo(128f, 102f, cx, 96f)
            quadTo(384f, 102f, 386f, 218f)
            lineTo(374f, 326f)
            quadTo(366f, 396f, cx, 412f)
            quadTo(146f, 396f, 138f, 326f)
            close()
        }
        val helmetFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#101A2B")
            style = Paint.Style.FILL
        }
        canvas.drawPath(helmetPath, helmetFill)
        val helmetEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#355070")
        }
        canvas.drawPath(helmetPath, helmetEdge)

        // Glossy cyber visor panel
        val visorRect = RectF(150f, 188f, 362f, 266f)
        val visorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#04080F")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(visorRect, 32f, 32f, visorFill)
        val visorStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            shader = LinearGradient(visorRect.left, visorRect.top, visorRect.right, visorRect.bottom, accentStart, accentEnd, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(visorRect, 32f, 32f, visorStroke)

        // Glowing cyber eyes inside visor (brighter & slightly angled when speaking)
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(170f, 210f, 340f, 245f, accentStart, Color.WHITE, Shader.TileMode.CLAMP)
            style = Paint.Style.FILL
        }
        val eyeHalfH = if (isTalking) 14f else 9.5f
        val leftEye = RectF(186f, 227f - eyeHalfH, 238f, 227f + eyeHalfH)
        val rightEye = RectF(274f, 227f - eyeHalfH, 326f, 227f + eyeHalfH)
        canvas.drawRoundRect(leftEye, 10f, 10f, eyePaint)
        canvas.drawRoundRect(rightEye, 10f, 10f, eyePaint)

        // Voice equalizer mouth (calm horizontal line when idle, 5 glowing neon bars when talking)
        val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(184f, 310f, 328f, 366f, accentStart, accentEnd, Shader.TileMode.CLAMP)
            style = Paint.Style.FILL
        }
        if (isTalking) {
            val barHeights = floatArrayOf(22f, 44f, 58f, 44f, 22f)
            val barW = 16f
            val gap = 14f
            val totalW = barHeights.size * barW + (barHeights.size - 1) * gap
            var bx = cx - totalW * 0.5f
            val mouthCy = 336f
            for (bh in barHeights) {
                canvas.drawRoundRect(
                    RectF(bx, mouthCy - bh * 0.5f, bx + barW, mouthCy + bh * 0.5f),
                    8f,
                    8f,
                    mouthPaint
                )
                bx += barW + gap
            }
        } else {
            val idleMouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6690A4AE")
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(206f, 331f, 306f, 341f), 5f, 5f, idleMouthPaint)
        }

        return bmp
    }

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
                Color.parseColor("#2979FF"),
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

    fun buildLiveBannerBitmap(rawText: String): Bitmap? {
        val clean = rawText.trim()
        if (clean.isEmpty()) return null

        val texW = 1280
        val texH = 92
        val bmp = Bitmap.createBitmap(texW, texH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val clippedText = clean.take(68)
        val displayStr = clippedText.uppercase(Locale.US).let { if (clean == it) it else clippedText }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 31f
        }

        // Auto-fit font size down to 17f so long 68-character banners never overflow the pill border
        val maxAllowedTextW = texW - 116f
        var measuredTextW = textPaint.measureText(displayStr)
        while (measuredTextW > maxAllowedTextW && textPaint.textSize > 17f) {
            textPaint.textSize -= 1.5f
            measuredTextW = textPaint.measureText(displayStr)
        }
        measuredTextW = min(maxAllowedTextW, measuredTextW)

        val pillW = (measuredTextW + 92f).coerceIn(280f, texW - 24f)
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
                Color.parseColor("#2979FF"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(pillRect, 36f, 36f, strokePaint)

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
            displayStr,
            pillRect.left + 56f,
            baselineY,
            textPaint
        )

        return bmp
    }
}
