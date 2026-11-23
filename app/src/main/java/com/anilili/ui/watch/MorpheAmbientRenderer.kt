package com.anilili.ui.watch

/*
 * AniLife Media3 adapter for the ambient-light math from Morphe.
 * The renderer implementation in AmbientLightMath.java is copyright 2026 Morphe,
 * GPL-3.0 with Section 7 terms; see licenses/morphe/GPL-3.0.txt and NOTICE.
 * This new adapter maps AniLife's PixelCopy frames to Morphe's spatial light grid.
 * Morphe and AniLife are independent projects; no Morphe branding is used.
 */

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Computes the original spatial light-field (sharp near the picture, blurred farther away).
 * Lives on a background dispatcher; the Media3 SurfaceView itself is never modified.
 * This adapter owns its smoothing buffers and must only be called from one sampling coroutine.
 */
internal class MorpheAmbientRenderer {
    private val width = 48
    private val height = 27
    private val gridWidth = 64
    private val gridHeight = 88
    private val nearExact = FloatArray(width * height * 3)
    private val near = IntArray(width * height)
    private val far = IntArray(width * 2)
    private val nearLinear = FloatArray(width * height * 3)
    private val farLinear = FloatArray(width * 2 * 3)
    private val grid = FloatArray(gridWidth * gridHeight * 3)
    private val soft = FloatArray(grid.size)
    private val scratch = FloatArray(grid.size)
    private val encoded = FloatArray(grid.size)
    private val presented = FloatArray(grid.size)
    private val imagePixels = IntArray(gridWidth * gridHeight)
    // The vertical attenuation depends only on row, not on video content. Cache
    // pow() and rounding once rather than 88 times for every captured frame.
    private val rowAlpha = IntArray(gridHeight) { y ->
        ((1f - y.toFloat() / gridHeight).coerceIn(0f, 1f).pow(1.40f) * 168f)
            .roundToInt().coerceIn(0, 255)
    }
    private val pageLinear = FloatArray(3)
    private val virtualWidth = 640
    private val virtualHeight = 880
    private val videoHeight = 360
    private val picture = intArrayOf(0, -videoHeight, virtualWidth, 0)
    private val frameBounds = intArrayOf(0, 0, 96, 54)
    private val fullPicture = intArrayOf(0, 0, width, height)
    private val pageSpace = floatArrayOf(0f, -videoHeight.toFloat() * gridHeight / virtualHeight,
        gridWidth.toFloat(), 0f)
    private var hasFrame = false
    private var lastFrame: IntArray? = null

    fun render(frame: IntArray): ImageBitmap? {
        if (frame.size != 96 * 54) return null
        // Reject dark/protected frames. Preserve the previous light instead of flashing gray.
        val previous = lastFrame
        var sum = 0L
        var changed = 0L
        for (i in frame.indices step 8) {
            val p = frame[i]
            sum += ((p shr 16) and 255) + ((p shr 8) and 255) + (p and 255)
            if (previous != null) {
                val q = previous[i]
                changed += kotlin.math.abs(((p shr 16) and 255) - ((q shr 16) and 255))
                changed += kotlin.math.abs(((p shr 8) and 255) - ((q shr 8) and 255))
                changed += kotlin.math.abs((p and 255) - (q and 255))
            }
        }
        if (sum < frame.size / 8L * 12L) return null
        if (previous != null && changed < frame.size / 8L * 3L) return null

        AmbientLightMath.downsample(frame, 96, frameBounds, near, width, height)
        val bounds = AmbientLightMath.pictureBounds(near, width, height) ?: return null
        if ((bounds[2] - bounds[0]) < width / 2 || (bounds[3] - bounds[1]) < height / 2) return null

        // Morphe's scene-cut behavior: snap to a different scene but ease normal motion.
        val sceneCut = previous != null && changed > frame.size / 8L * 70L
        // Keep a share of the prior frame to soften motion between captures.
        // Retain a little even at scene cuts so bright flashes don't pop abruptly.
        AmbientLightMath.blend(nearExact, near, if (!hasFrame) 0f else if (sceneCut) 0.12f else 0.58f, near)
        hasFrame = true
        lastFrame = frame

        AmbientLightMath.downsample(near, width, fullPicture, far, width, 2)
        AmbientLightMath.toLinear(near, nearLinear)
        AmbientLightMath.toLinear(far, farLinear)
        AmbientLightMath.render(
            nearLinear, width, height, farLinear, width, 2,
            picture, virtualWidth, virtualHeight, 0.30f,
            grid, gridWidth, gridHeight,
        )
        // The page is near-black; preserve small linear-light values like Morphe.
        pageLinear[0] = 0.002f
        pageLinear[1] = 0.002f
        pageLinear[2] = 0.002f
        AmbientLightMath.soften(
            grid, soft, scratch, gridWidth, gridHeight,
            pageSpace, pageLinear, 0.30f,
        )
        AmbientLightMath.encode(grid, encoded, presented, gridWidth, gridHeight, pageSpace)
        encoded.copyInto(presented)
        for (y in 0 until gridHeight) {
            val alpha = rowAlpha[y]
            for (x in 0 until gridWidth) {
                val n = (y * gridWidth + x) * 3
                val r = (encoded[n].coerceIn(0f, 1f) * 255).roundToInt()
                val g = (encoded[n + 1].coerceIn(0f, 1f) * 255).roundToInt()
                val b = (encoded[n + 2].coerceIn(0f, 1f) * 255).roundToInt()
                imagePixels[y * gridWidth + x] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(imagePixels, gridWidth, gridHeight, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
}
