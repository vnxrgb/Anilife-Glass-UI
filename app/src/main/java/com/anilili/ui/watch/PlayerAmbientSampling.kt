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
            var r = 0L
            var g = 0L
            var b = 0L
            var count = 0
            // Exclude near-black letterboxing and bright subtitle glyphs.
            for (pixel in pixels) {
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                val lightness = (red + green + blue) / 3
                if (lightness < 20 || lightness > 242) continue
                r += red
                g += green
                b += blue
                count++
            }
            if (count < 48) null else {
                // Keep text legible even when the video frame is almost white.
                fun shade(v: Long) = ((v.toFloat() / count) * 0.30f + 12f)
                    .roundToInt().coerceIn(12, 87) / 255f
                Color(shade(r), shade(g), shade(b))
            }
        }
    } catch (_: Exception) {
        null
    } finally {
        bitmap.recycle()
    }
}
