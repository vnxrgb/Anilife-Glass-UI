package com.anilili.ui.watch

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

/**
 * Samples a tiny copy of the actual video frame for the surrounding *phone*
 * watch-screen ambience. Never overlays, replaces, or reconfigures the decoder.
 *
 * Failure is expected on secure video surfaces: keep the caller's dark fallback.
 * Sampling is opt-in from a playback-aware LaunchedEffect, not a frame listener.
 */
@OptIn(UnstableApi::class)
@RequiresApi(Build.VERSION_CODES.O)
internal suspend fun sampleVideoAmbientColor(view: PlayerView): Color? {
    val surface = view.videoSurfaceView as? SurfaceView ?: return null
    if (!surface.holder.surface.isValid || !view.isShown) return null
    val bitmap = Bitmap.createBitmap(40, 24, Bitmap.Config.ARGB_8888)
    return try {
        val code = suspendCancellableCoroutine<Int> { continuation ->
            try {
                PixelCopy.request(
                    surface,
                    bitmap,
                    { result ->
                        if (continuation.isActive) continuation.resume(result)
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: IllegalArgumentException) {
                if (continuation.isActive) continuation.resume(PixelCopy.ERROR_SOURCE_INVALID)
            }
        }
        if (code != PixelCopy.SUCCESS) return null
        withContext(Dispatchers.Default) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            extractVideoAmbientTint(pixels, bitmap.width, bitmap.height)
        }
    } catch (_: Exception) {
        null
    } finally {
        bitmap.recycle()
    }
}

/**
 * Build a subtle but colourful glow from actual scene pixels. The bottom fifth is ignored
 * because subtitle text and playback overlays are not part of the scene's lighting.
 * Chroma-weighted accumulation favours the scene's colours over grey skies and letterboxing,
 * without letting a single tiny bright pixel determine the entire wash.
 */
internal fun extractVideoAmbientTint(pixels: IntArray, width: Int, height: Int): Color? {
    if (width <= 0 || height <= 0 || pixels.size < width.toLong() * height) return null
    var weightedRed = 0L
    var weightedGreen = 0L
    var weightedBlue = 0L
    var weightSum = 0L
    var chromaSum = 0L
    var valid = 0
    val sampledHeight = (height * 4 / 5).coerceAtLeast(1)
    for (y in 0 until sampledHeight) {
        for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            val red = (pixel ushr 16) and 0xFF
            val green = (pixel ushr 8) and 0xFF
            val blue = pixel and 0xFF
            val brightness = (red + green + blue) / 3
            if (brightness < 20 || brightness > 242) continue
            val chroma = max(red, max(green, blue)) - min(red, min(green, blue))
            val weight = 16 + chroma.coerceAtMost(160)
            weightedRed += red.toLong() * weight
            weightedGreen += green.toLong() * weight
            weightedBlue += blue.toLong() * weight
            weightSum += weight
            chromaSum += chroma
            valid++
        }
    }
    // Blank, grey and protected frames should not abruptly replace the last vibrant scene.
    if (valid < max(12, width * sampledHeight / 20) || weightSum == 0L ||
        chromaSum < valid.toLong() * 12
    ) return null

    fun shade(sum: Long): Float = ((sum.toFloat() / weightSum) * 0.34f + 11f)
        .roundToInt().coerceIn(11, 100) / 255f
    return Color(shade(weightedRed), shade(weightedGreen), shade(weightedBlue))
}
