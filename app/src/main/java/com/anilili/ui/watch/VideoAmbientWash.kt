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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.FilterQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Colours are sampled from the live picture, not guessed from the episode poster. */
data class VideoAmbientPalette(
    val left: Color,
    val right: Color,
    val center: Color = left,
    val spatialLight: ImageBitmap? = null,
) {
    companion object {
        val Empty = VideoAmbientPalette(Color.Transparent, Color.Transparent, Color.Transparent)
    }
}

/**
 * Native-player-only sampling for the ambient wash *below* a windowed video.
 * PixelCopy reads a tiny 96x54 SurfaceView frame, downsampled by the Morphe renderer; the displayed
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
        val spatialRenderer = MorpheAmbientRenderer()
        while (isActive) {
            if (surface.holder.surface.isValid && surface.width > 0 && surface.height > 0 &&
                surface.isShown && playerView?.player?.isPlaying == true) {
                val bitmap = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888)
                // PixelCopy only reads the existing decoded SurfaceView; the Morphe
                // renderer does its light-field processing on the worker dispatcher.
                val pixels = try {
                    suspendCancellableCoroutine<IntArray?> { continuation ->
                        try {
                            PixelCopy.request(surface, bitmap, { result ->
                                val captured = if (result == PixelCopy.SUCCESS) {
                                    IntArray(bitmap.width * bitmap.height).also {
                                        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                                    }
                                } else null
                                bitmap.recycle()
                                if (continuation.isActive) continuation.resume(captured)
                            }, handler)
                        } catch (_: Exception) {
                            bitmap.recycle()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                val sampledLight = if (pixels != null) withContext(Dispatchers.Default) {
                    spatialRenderer.render(pixels)
                } else null
                if (sampledLight != null) {
                    currentCallback(
                        VideoAmbientPalette(
                            left = Color.Transparent,
                            right = Color.Transparent,
                            center = Color.Transparent,
                            spatialLight = sampledLight,
                        ),
                    )
                }
            }
            // Morphe's light updates multiple times per second. A short cadence plus
            // GPU crossfades avoids the visibly stepped 260ms bitmap replacement.
            // The renderer skips unchanged frames and low-memory devices opt out.
            delay(125L)
        }
    }
}

/** Average the live picture, preserve brightness, and gently lift chroma for a recognizable wash. */
private fun extractAmbientPalette(bitmap: Bitmap): VideoAmbientPalette? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    fun average(startX: Int, endX: Int): Color {
        var red = 0f
        var green = 0f
        var blue = 0f
        var totalWeight = 0f
        // Ignore extreme letterboxing, subtitles and fully black video frames.
        for (y in 2 until (height * 4 / 5).coerceAtLeast(3) step 2) {
            for (x in startX until endX step 2) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel).toFloat() / 255f
                val g = android.graphics.Color.green(pixel).toFloat() / 255f
                val b = android.graphics.Color.blue(pixel).toFloat() / 255f
                val peak = maxOf(r, g, b)
                if (peak < 0.045f) continue
                // Weighted colour regions preserve saturated highlights without
                // a single small bright pixel dominating the entire glow.
                val low = minOf(r, g, b)
                val weight = 0.35f + (peak - low) * 1.25f
                red += r * r * weight
                green += g * g * weight
                blue += b * b * weight
                totalWeight += weight
            }
        }
        if (totalWeight <= 0f) return Color.Transparent
        fun channel(sum: Float): Float =
            kotlin.math.sqrt(sum / totalWeight).coerceIn(0f, 0.82f)
        return Color(channel(red), channel(green), channel(blue))
    }
    val left = average(0, width / 3)
    val center = average(width / 3, width * 2 / 3)
    val right = average(width * 2 / 3, width)
    if (listOf(left, center, right).all { it.alpha == 0f }) return null
    val fallback = listOf(center, left, right).first { it.alpha > 0f }
    return VideoAmbientPalette(
        left = if (left.alpha == 0f) fallback else left,
        center = if (center.alpha == 0f) fallback else center,
        right = if (right.alpha == 0f) fallback else right,
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
    val left by animateColorAsState(palette.left, tween(450), label = "ambientLeft")
    val right by animateColorAsState(palette.right, tween(450), label = "ambientRight")
    val middle by animateColorAsState(palette.center, tween(450), label = "ambientMiddle")

    // Morphe's spatial field is a small bitmap, not a Compose Color. The old code
    // replaced that bitmap instantly each capture (~4 times/s), making an otherwise
    // accurate lighting effect visibly stutter. Interpolate successive fields on the
    // GPU while PixelCopy and Morphe's blur calculations run on their own cadence.
    var displayedLight by remember { mutableStateOf<ImageBitmap?>(null) }
    var previousLight by remember { mutableStateOf<ImageBitmap?>(null) }
    val blend = remember { Animatable(1f) }
    val ambientLayerPaint = remember { Paint() }
    LaunchedEffect(palette.spatialLight) {
        val next = palette.spatialLight
        if (next == null) {
            previousLight = null
            displayedLight = null
            blend.snapTo(1f)
        } else if (displayedLight !== next) {
            previousLight = displayedLight
            displayedLight = next
            blend.snapTo(if (previousLight == null) 1f else 0f)
            if (previousLight != null) {
                // A finished fade must precede the next 125ms video capture.
                // Interrupting a 155ms fade every 125ms was creating visible pulses.
                blend.animateTo(1f, tween(95, easing = LinearEasing))
                previousLight = null
            }
        }
    }
    return this.drawBehind {
        val target = displayedLight ?: palette.spatialLight
        if (target != null) {
            val sizePx = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
            val strength = intensity.coerceIn(0.2f, 1f)
            val fraction = blend.value
            val outgoing = previousLight
            // Blend into a transparent offscreen layer using additive ("plus")
            // compositing. Drawing two semi-transparent lights with SrcOver
            // reduced their combined alpha in the middle of every crossfade:
            // the resulting rapid dim/restore was the reported blinking.
            // Plus produces a true weighted interpolation of premultiplied
            // colors and alpha. Apply strength ONCE to the combined result.
            ambientLayerPaint.alpha = strength
            drawContext.canvas.saveLayer(size.toRect(), ambientLayerPaint)
            try {
                if (outgoing != null) {
                    drawImage(
                        image = outgoing,
                        dstSize = sizePx,
                        alpha = (1f - fraction).coerceIn(0f, 1f),
                        blendMode = BlendMode.Plus,
                        filterQuality = FilterQuality.Medium,
                    )
                }
                drawImage(
                    image = target,
                    dstSize = sizePx,
                    alpha = if (outgoing == null) 1f else fraction.coerceIn(0f, 1f),
                    blendMode = BlendMode.Plus,
                    filterQuality = FilterQuality.Medium,
                )
            } finally {
                drawContext.canvas.restore()
            }
            return@drawBehind
        }
        if (left.alpha <= 0f && right.alpha <= 0f && middle.alpha <= 0f) return@drawBehind

        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawBehind
        val strength = intensity.coerceIn(0.5f, 1f)
        // Layer three broad, feathered colour blooms instead of a solid tinted slab.
        // The video itself is never blurred; only the watch-page background is painted.
        val reach = maxOf(w * 1.20f, h * 0.42f)
        val center = Color(
            red = (left.red + middle.red + right.red) / 3f,
            green = (left.green + middle.green + right.green) / 3f,
            blue = (left.blue + middle.blue + right.blue) / 3f,
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
                    0f to middle.copy(alpha = 0.42f * strength),
                    0.38f to middle.copy(alpha = 0.20f * strength),
                    0.80f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.04f),
                radius = maxOf(w * 0.72f, h * 0.36f),
            ),
        )
    }
}
