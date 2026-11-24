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
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
        val dir = File((context.applicationContext ?: context).filesDir, SCENES_DIR)
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
        val resolver = (context.applicationContext ?: context).contentResolver
        var tempFile: File? = null
        var finalBitmap: Bitmap? = null
        return try {
            val (rotationDeg, isFlipped) = try {
                resolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    exif.rotationDegrees.toFloat() to exif.isFlipped
                } ?: (0f to false)
            } catch (_: Throwable) {
                0f to false
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

            val transformed = transformAndClampBitmap(decoded, rotationDeg, isFlipped, maxTarget)
            if (transformed !== decoded) {
                decoded.recycle()
            }
            finalBitmap = transformed

            val outFile = slotFile(context, slot)
            val tmp = File(outFile.parentFile, "${slot.fileName}.${System.nanoTime()}.tmp")
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
        } catch (t: Throwable) {
            Result.failure(t)
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

    private fun transformAndClampBitmap(
        src: Bitmap,
        rotationDeg: Float,
        isFlipped: Boolean,
        maxDim: Int
    ): Bitmap {
        val rotW = if (rotationDeg == 90f || rotationDeg == 270f) src.height else src.width
        val rotH = if (rotationDeg == 90f || rotationDeg == 270f) src.width else src.height
        val longest = max(rotW, rotH)
        val scale = if (longest > maxDim) maxDim.toFloat() / longest.toFloat() else 1.0f

        if (rotationDeg == 0f && !isFlipped && scale >= 0.999f) {
            if (src.config == Bitmap.Config.ARGB_8888) {
                return src
            }
            return src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        }

        val matrix = Matrix()
        if (isFlipped) {
            matrix.postScale(-1f, 1f)
        }
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
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadOptionalBundledAvatarBitmap(context: Context, drawableName: String): Bitmap? {
        return try {
            val appCtx = context.applicationContext ?: context
            val resId = appCtx.resources.getIdentifier(drawableName, "drawable", appCtx.packageName)
            if (resId == 0) return null
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPremultiplied = true
            }
            BitmapFactory.decodeResource(appCtx.resources, resId, opts)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Loads the PNGTuber avatar pair for OpenGL:
     * - 2 custom PNGs uploaded -> returns (customIdle, customTalk)
     * - 1 custom PNG uploaded -> returns (customImg, null) so pipeline reuses 1 texture with smooth talk-hop
     * - Optional bundled drawables (`sol_avatar_{male|female|neutral}_{idle|talk}`) if packaged in res/drawable-nodpi/
     * - Otherwise generates the stylized Masked Esports Striker avatar matched to voiceGenderProfile.
     */
    fun loadPngTuberPairForGl(context: Context, genderProfile: String): Pair<Bitmap, Bitmap?> {
        val customIdle = loadSavedBitmapForGl(context, AssetSlot.PNGTUBER_IDLE)
        val customTalk = loadSavedBitmapForGl(context, AssetSlot.PNGTUBER_TALK)
        if (customIdle != null && customTalk != null) return customIdle to customTalk
        if (customIdle != null) return customIdle to null
        if (customTalk != null) return customTalk to null

        val gKey = when (genderProfile.trim().uppercase(Locale.US)) {
            "MALE" -> "male"
            "FEMALE" -> "female"
            else -> "neutral"
        }
        val bundledIdle = loadOptionalBundledAvatarBitmap(context, "sol_avatar_${gKey}_idle")
        val bundledTalk = loadOptionalBundledAvatarBitmap(context, "sol_avatar_${gKey}_talk")
        if (bundledIdle != null) {
            return bundledIdle to bundledTalk
        }
        if (bundledTalk != null) {
            return bundledTalk to null
        }

        return generateCyberAvatarBitmap(isTalking = false, genderProfile = genderProfile) to
            generateCyberAvatarBitmap(isTalking = true, genderProfile = genderProfile)
    }

    /**
     * Generates a transparent 512x512 stylized Masked Esports Striker portrait matching SolStream's
     * Obsidian-Glass theme (hooded silhouette, gaming headset, anime-styled expressive cyber eyes,
     * and neon voice-reactive stealth mask — styled per MALE / FEMALE / NEUTRAL voice profile).
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
            else -> Color.parseColor("#38BDF8")
        }
        val accentEnd = when (normGender) {
            "FEMALE" -> Color.parseColor("#A855F7")
            "MALE" -> Color.parseColor("#2979FF")
            else -> Color.parseColor("#F59E0B")
        }
        val glowHex = when (normGender) {
            "FEMALE" -> if (isTalking) "#99FF4081" else "#38FF4081"
            "MALE" -> if (isTalking) "#9900E5FF" else "#3800E5FF"
            else -> if (isTalking) "#9938BDF8" else "#3838BDF8"
        }
        val hairDark = when (normGender) {
            "FEMALE" -> Color.parseColor("#1D1330")
            "MALE" -> Color.parseColor("#0E172A")
            else -> Color.parseColor("#111827")
        }
        val hairHighlight = when (normGender) {
            "FEMALE" -> Color.parseColor("#4C1D95")
            "MALE" -> Color.parseColor("#1E3A5F")
            else -> Color.parseColor("#1F2937")
        }

        val cx = size * 0.5f
        val cy = size * 0.5f

        // 1. Soft neon radial backdrop aura (expands subtly when speaking)
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx,
                cy,
                if (isTalking) 244f else 224f,
                intArrayOf(Color.parseColor(glowHex), Color.TRANSPARENT),
                floatArrayOf(0.68f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, if (isTalking) 244f else 224f, haloPaint)

        // 2. Dark obsidian circular badge base
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                70f, 50f, 442f, 462f,
                intArrayOf(
                    Color.parseColor("#F2070E1A"),
                    Color.parseColor("#F5111E33"),
                    Color.parseColor("#F2050A14")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, 208f, bgPaint)

        // 3. Esports hoodie shoulders & hood frame (clipped inside circular medallion)
        canvas.save()
        val clipCircle = Path().apply { addCircle(cx, cy, 204f, Path.Direction.CW) }
        canvas.clipPath(clipCircle)

        val hoodiePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx, 280f, cx, 470f,
                Color.parseColor("#18253B"),
                Color.parseColor("#0A101C"),
                Shader.TileMode.CLAMP
            )
        }
        val shouldersPath = Path().apply {
            moveTo(76f, 468f)
            quadTo(118f, 342f, 188f, 326f)
            lineTo(324f, 326f)
            quadTo(394f, 342f, 436f, 468f)
            close()
        }
        canvas.drawPath(shouldersPath, hoodiePaint)

        // Neon hoodie collar trim
        val collarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            shader = LinearGradient(160f, 330f, 352f, 440f, accentStart, accentEnd, Shader.TileMode.CLAMP)
        }
        val collarPath = Path().apply {
            moveTo(182f, 336f)
            lineTo(cx, 412f)
            lineTo(330f, 336f)
        }
        canvas.drawPath(collarPath, collarPaint)

        // Outer hood shadow behind head
        val hoodBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F1A2E")
            style = Paint.Style.FILL
        }
        val hoodBackPath = Path().apply {
            moveTo(114f, 348f)
            quadTo(98f, 122f, cx, 76f)
            quadTo(414f, 122f, 398f, 348f)
            close()
        }
        canvas.drawPath(hoodBackPath, hoodBackPaint)

        // Headset headband arch
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            color = Color.parseColor("#1E293B")
        }
        canvas.drawArc(RectF(112f, 82f, 400f, 358f), 192f, 156f, false, bandPaint)
        val bandAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            shader = LinearGradient(140f, 92f, 372f, 92f, accentStart, accentEnd, Shader.TileMode.CLAMP)
        }
        canvas.drawArc(RectF(118f, 88f, 394f, 352f), 205f, 130f, false, bandAccent)

        // Gaming headset earcups with glowing LED strips
        val cupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E2D45") }
        canvas.drawRoundRect(RectF(96f, 206f, 136f, 306f), 18f, 18f, cupPaint)
        canvas.drawRoundRect(RectF(376f, 206f, 416f, 306f), 18f, 18f, cupPaint)

        val cupLed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 220f, 0f, 292f, accentStart, accentEnd, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(108f, 224f, 118f, 288f), 5f, 5f, cupLed)
        canvas.drawRoundRect(RectF(394f, 224f, 404f, 288f), 5f, 5f, cupLed)

        // Neck
        val skinShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C79A7B") }
        canvas.drawRoundRect(RectF(224f, 308f, 288f, 370f), 20f, 20f, skinShadow)

        // Stylized tapered anime face contour (upper face visible above tactical mask)
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx, 150f, cx, 340f,
                Color.parseColor("#F5D0B5"),
                Color.parseColor("#DFB193"),
                Shader.TileMode.CLAMP
            )
        }
        val facePath = Path().apply {
            moveTo(156f, 198f)
            quadTo(158f, 118f, cx, 114f)
            quadTo(354f, 118f, 356f, 198f)
            quadTo(352f, 286f, 314f, 324f)
            quadTo(cx, 354f, 198f, 324f)
            quadTo(160f, 286f, 156f, 198f)
            close()
        }
        canvas.drawPath(facePath, facePaint)

        // Expressive anime-styled cyber eyes
        val eyeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B1120") }
        val leftEyeOutline = Path().apply {
            moveTo(176f, 224f)
            quadTo(206f, 208f, 236f, 222f)
            quadTo(212f, 240f, 182f, 234f)
            close()
        }
        val rightEyeOutline = Path().apply {
            moveTo(336f, 224f)
            quadTo(306f, 208f, 276f, 222f)
            quadTo(300f, 240f, 330f, 234f)
            close()
        }
        canvas.drawPath(leftEyeOutline, eyeBgPaint)
        canvas.drawPath(rightEyeOutline, eyeBgPaint)

        val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 212f, 0f, 238f, accentStart, accentEnd, Shader.TileMode.CLAMP)
        }
        val irisR = if (isTalking) 11.5f else 9.8f
        canvas.drawCircle(208f, 224f, irisR, irisPaint)
        canvas.drawCircle(304f, 224f, irisR, irisPaint)

        val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(212f, 220f, 3.8f, specularPaint)
        canvas.drawCircle(308f, 220f, 3.8f, specularPaint)

        // Subtle confident eyebrows
        val browPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            color = hairDark
        }
        canvas.drawLine(178f, 204f, 232f, 210f, browPaint)
        canvas.drawLine(334f, 204f, 280f, 210f, browPaint)

        // Sleek esports tactical half-mask over lower face
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                160f, 250f, 352f, 360f,
                Color.parseColor("#111C2F"),
                Color.parseColor("#060B14"),
                Shader.TileMode.CLAMP
            )
        }
        val maskPath = Path().apply {
            moveTo(156f, 252f)
            quadTo(cx, 236f, 356f, 252f)
            quadTo(348f, 312f, 314f, 334f)
            quadTo(cx, 366f, 198f, 334f)
            quadTo(164f, 312f, 156f, 252f)
            close()
        }
        canvas.drawPath(maskPath, maskPaint)
        val maskBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
            shader = LinearGradient(160f, 250f, 352f, 345f, accentStart, accentEnd, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(maskPath, maskBorder)

        // Neon voice-wave emblem on the stealth mask (calm sleek line when idle, active glowing wave when talking)
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(196f, 280f, 316f, 330f, accentStart, accentEnd, Shader.TileMode.CLAMP)
            style = Paint.Style.FILL
        }
        if (isTalking) {
            val heights = floatArrayOf(14f, 30f, 44f, 30f, 14f)
            val barW = 12f
            val gap = 11f
            val totalW = heights.size * barW + (heights.size - 1) * gap
            var bx = cx - totalW * 0.5f
            val waveCy = 298f
            for (bh in heights) {
                canvas.drawRoundRect(
                    RectF(bx, waveCy - bh * 0.5f, bx + barW, waveCy + bh * 0.5f),
                    6f,
                    6f,
                    wavePaint
                )
                bx += barW + gap
            }
        } else {
            val idleWave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8894A3B8")
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(214f, 294f, 298f, 302f), 4f, 4f, idleWave)
        }

        // Stylized layered anime hair bangs framing the forehead
        val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(150f, 95f, 362f, 215f, hairHighlight, hairDark, Shader.TileMode.CLAMP)
        }
        val hairPath = Path().apply {
            moveTo(142f, 224f)
            quadTo(138f, 114f, cx, 98f)
            quadTo(374f, 114f, 370f, 224f)
            lineTo(346f, 178f)
            lineTo(324f, 206f)
            lineTo(298f, 168f)
            lineTo(264f, 208f)
            lineTo(240f, 166f)
            lineTo(202f, 208f)
            lineTo(178f, 176f)
            close()
        }
        canvas.drawPath(hairPath, hairPaint)

        // Optional sleek side hair locks for Female profile
        if (normGender == "FEMALE") {
            val leftLock = Path().apply {
                moveTo(142f, 206f)
                quadTo(130f, 296f, 152f, 362f)
                quadTo(170f, 314f, 164f, 228f)
                close()
            }
            val rightLock = Path().apply {
                moveTo(370f, 206f)
                quadTo(382f, 296f, 360f, 362f)
                quadTo(342f, 314f, 348f, 228f)
                close()
            }
            canvas.drawPath(leftLock, hairPaint)
            canvas.drawPath(rightLock, hairPaint)
        }

        canvas.restore()

        // 4. Crisp outer metallic gradient ring (brightens when talking)
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (isTalking) 9.5f else 6.0f
            shader = LinearGradient(48f, 48f, 464f, 464f, accentStart, accentEnd, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(cx, cy, 208f, rimPaint)

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
                "Warming up lobby • Audio & stream locked in"
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

        val displayStr = clean.take(68)

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
