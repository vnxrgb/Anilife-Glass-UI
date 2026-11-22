package com.anilili.ui.watch

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max

/** Colours are sampled from the live picture, not guessed from the episode poster. */
internal data class VideoAmbientPalette(val left: Color, val right: Color) {
    companion object {
        val Empty = VideoAmbientPalette(Color.Transparent, Color.Transparent)
    }
}

/**
 * Native-player-only sampling for the ambient wash *below* a windowed video.
 * PixelCopy reads a tiny 48x27 SurfaceView frame once per 1.3s, so the displayed
 * video, subtitles and controls are never blurred, reparented or screenshotted.
 * DRM/secure surfaces, unsupported decoders and older Android versions fail closed.
 */
@Composable
internal fun NativeVideoAmbientSampler(
    playerView: PlayerView?,
    active: Boolean,
    onPalette: (VideoAmbientPalette) -> Unit,
) {
    val currentCallback by rememberUpdatedState(onPalette)
    LaunchedEffect(playerView, active) {
        if (!active || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@LaunchedEffect
        val surface = playerView?.videoSurfaceView as? SurfaceView ?: return@LaunchedEffect
        val handler = Handler(Looper.getMainLooper())
        while (isActive) {
            if (surface.holder.surface.isValid && surface.width > 0 && surface.height > 0) {
                val bitmap = Bitmap.createBitmap(48, 27, Bitmap.Config.ARGB_8888)
                val sampled = try {
                    suspendCancellableCoroutine<VideoAmbientPalette?> { continuation ->
                        try {
                            PixelCopy.request(surface, bitmap, { result ->
                                val palette = if (result == PixelCopy.SUCCESS) {
                                    extractAmbientPalette(bitmap)
                                } else {
                                    null
                                }
                                bitmap.recycle()
                                if (continuation.isActive) continuation.resume(palette)
                            }, handler)
                        } catch (_: Exception) {
                            bitmap.recycle()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                if (sampled != null) currentCallback(sampled)
            }
            delay(1_300L)
        }
    }
}

/** Dark and low-chroma scenes stay subtle; vivid frames lend only their hue. */
private fun extractAmbientPalette(bitmap: Bitmap): VideoAmbientPalette? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    fun average(startX: Int, endX: Int): Color {
        var red = 0L
        var green = 0L
        var blue = 0L
        var samples = 0L
        for (y in (height / 3) until height step 2) {
            for (x in startX until endX step 2) {
                val pixel = bitmap.getPixel(x, y)
                red += android.graphics.Color.red(pixel)
                green += android.graphics.Color.green(pixel)
                blue += android.graphics.Color.blue(pixel)
                samples++
            }
        }
        if (samples == 0L) return Color.Transparent
        return Color(
            red = (red / samples).toInt().coerceIn(0, 255),
            green = (green / samples).toInt().coerceIn(0, 255),
            blue = (blue / samples).toInt().coerceIn(0, 255),
        )
    }
    return VideoAmbientPalette(average(0, width / 2), average(width / 2, width))
}

/** A pair of wide falloff glows matches the picture edges on both dark and AMOLED themes. */
@Composable
internal fun Modifier.videoAmbientWash(palette: VideoAmbientPalette): Modifier {
    val left by animateColorAsState(palette.left, tween(1_050), label = "ambientLeft")
    val right by animateColorAsState(palette.right, tween(1_050), label = "ambientRight")
    return this.drawBehind {
        if (left.alpha <= 0f && right.alpha <= 0f) return@drawBehind
        val spread = max(size.width, size.height) * 0.92f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(left.copy(alpha = 0.38f), Color.Transparent),
                center = Offset(0f, 0f),
                radius = spread,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(right.copy(alpha = 0.34f), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = spread,
            ),
        )
    }
}
