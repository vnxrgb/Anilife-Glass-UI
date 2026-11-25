package com.anilili.ui.detail

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** One small artwork sample per title, never a video capture or a per-frame bitmap. */
@Composable
internal fun DetailAmbient(artwork: String?, fallback: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var target by remember(artwork, fallback) { mutableStateOf(fallback) }
    LaunchedEffect(artwork, fallback) {
        if (artwork.isNullOrBlank()) return@LaunchedEffect
        try {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(artwork).size(64, 64).allowHardware(false).build()
            ) as? SuccessResult ?: return@LaunchedEffect
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
            val sampled = withContext(Dispatchers.Default) {
                val bins = FloatArray(12)
                val hsv = FloatArray(3)
                for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.alpha(pixel) < 128) continue
                    android.graphics.Color.colorToHSV(pixel, hsv)
                    if (hsv[1] < .24f || hsv[2] < .20f) continue
                    val bin = (hsv[0] / 30f).toInt().coerceIn(0, 11)
                    bins[bin] += hsv[1] * hsv[1] * hsv[2]
                }
                val dominant = bins.indices.maxByOrNull { bins[it] } ?: 0
                if (bins[dominant] < 2f) null
                else Color(android.graphics.Color.HSVToColor(floatArrayOf(dominant * 30f + 15f, .68f, .86f)))
            }
            if (sampled != null) target = sampled
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Offline or missing artwork: the catalogue cover colour remains available.
        }
    }
    val tint by animateColorAsState(target, tween(650), label = "detailArtworkAmbient")
    val strength = if (MaterialTheme.colorScheme.background.luminance() > .5f) .55f else 1f
    Box(modifier.drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        val reach = maxOf(size.width * 1.25f, size.height * .92f)
        drawRect(Brush.radialGradient(
            0f to tint.copy(alpha = .30f * strength),
            .40f to tint.copy(alpha = .19f * strength),
            .76f to tint.copy(alpha = .085f * strength),
            1f to Color.Transparent,
            center = Offset(size.width * .52f, size.height * .12f),
            radius = reach,
        ))
        drawRect(Brush.radialGradient(
            0f to tint.copy(alpha = .12f * strength),
            .55f to tint.copy(alpha = .055f * strength),
            1f to Color.Transparent,
            center = Offset(size.width * .88f, size.height * .82f),
            radius = maxOf(size.width, size.height * .55f),
        ))
    })
}
