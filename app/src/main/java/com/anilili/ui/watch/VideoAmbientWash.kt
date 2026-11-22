package com.anilili.ui.watch

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.app.ActivityManager
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Colours are sampled from the live picture, not guessed from the episode poster. */
data class VideoAmbientPalette(val left: Color, val right: Color) {
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
@OptIn(UnstableApi::class)
@Composable
internal fun NativeVideoAmbientSampler(
    playerView: PlayerView?,
    active: Boolean,
    onPalette: (VideoAmbientPalette) -> Unit,
) {
    val currentCallback by rememberUpdatedState(onPalette)
    val context = LocalContext.current
    val lowMemoryDevice = (context.getSystemService(Context.ACTIVITY_SERVICE)
        as? ActivityManager)?.isLowRamDevice == true
    LaunchedEffect(playerView, active, lowMemoryDevice) {
        if (!active || lowMemoryDevice || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@LaunchedEffect
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
            delay(1_200L)
        }
    }
}

/** Average the live picture, preserve brightness, and gently lift chroma for a recognizable wash. */
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
        val r = (red.toFloat() / samples / 255f).coerceIn(0f, 1f)
        val g = (green.toFloat() / samples / 255f).coerceIn(0f, 1f)
        val b = (blue.toFloat() / samples / 255f).coerceIn(0f, 1f)
        // Never substitute a static poster colour: these hues follow actual video frames.
        // A small brightness lift gives dark anime scenes some atmosphere without
        // letting bright frames wash out the watch-page typography.
        val peak = maxOf(r, g, b)
        val lift = if (peak < 0.10f) 0.04f else 0.08f
        // Skip pure black frames so fades/cut-to-black do not flash a dull grey
        // backdrop. The last valid palette continues to transition smoothly.
        if (peak < 0.035f) return Color.Transparent
        // Lift colour separation without injecting a fixed hue. Grey scenes remain
        // neutral; saturated frames cast recognisable blooms instead of muddy brown.
        val mean = (r + g + b) / 3f
        fun vivid(channel: Float) = ((mean + (channel - mean) * 1.22f) * 0.93f + lift)
            .coerceIn(0f, 1f)
        return Color(red = vivid(r), green = vivid(g), blue = vivid(b))
    }
    val left = average(0, width / 2)
    val right = average(width / 2, width)
    if (left.alpha == 0f && right.alpha == 0f) return null
    // A letterboxed or nearly black half should not pull the centre bloom toward
    // black while the other half still contains a valid frame colour.
    return VideoAmbientPalette(
        left = if (left.alpha == 0f) right else left,
        right = if (right.alpha == 0f) left else right,
    )
}

/**
 * YouTube-style watch-page ambience. The artwork stays sharp in the native player above;
 * only the area BELOW it receives a long, softly blended wash from the current video.
 * The first screenful is visibly atmospheric, then falls off toward the episode list.
 *
 * No blur, capture, or translucency is applied to the video, controls, subtitles or DRM
 * surface. This is a small set of GPU gradients on the existing watch-page background.
 */
@Composable
internal fun Modifier.videoAmbientWash(
    palette: VideoAmbientPalette,
    intensity: Float = 0.75f,
): Modifier {
    val left by animateColorAsState(palette.left, tween(1_450), label = "ambientLeft")
    val right by animateColorAsState(palette.right, tween(1_450), label = "ambientRight")
    return this.drawBehind {
        if (left.alpha <= 0f && right.alpha <= 0f) return@drawBehind

        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawBehind
        val strength = intensity.coerceIn(0.5f, 1f)
        // Layer three broad, feathered colour blooms instead of a solid tinted slab.
        // The video itself is never blurred; only the watch-page background is painted.
        val reach = maxOf(w * 1.20f, h * 0.42f)
        val center = Color(
            red = (left.red + right.red) * 0.5f,
            green = (left.green + right.green) * 0.5f,
            blue = (left.blue + right.blue) * 0.5f,
        )
        drawRect(
            brush = Brush.verticalGradient(
                0f to center.copy(alpha = 0.16f * strength),
                0.32f to center.copy(alpha = 0.06f * strength),
                0.75f to Color.Transparent,
                1f to Color.Transparent,
                startY = 0f,
                endY = maxOf(w * 1.8f, h * 0.70f),
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                *arrayOf(
                    0f to left.copy(alpha = 0.64f * strength),
                    0.24f to left.copy(alpha = 0.36f * strength),
                    0.52f to left.copy(alpha = 0.14f * strength),
                    0.85f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                center = Offset(-w * 0.03f, h * 0.04f),
                radius = reach,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                *arrayOf(
                    0f to right.copy(alpha = 0.61f * strength),
                    0.24f to right.copy(alpha = 0.33f * strength),
                    0.52f to right.copy(alpha = 0.13f * strength),
                    0.85f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                center = Offset(w * 1.03f, h * 0.04f),
                radius = reach,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                *arrayOf(
                    0f to center.copy(alpha = 0.18f * strength),
                    0.38f to center.copy(alpha = 0.09f * strength),
                    0.80f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.31f),
                radius = maxOf(w * 0.72f, h * 0.36f),
            ),
        )
    }
}
