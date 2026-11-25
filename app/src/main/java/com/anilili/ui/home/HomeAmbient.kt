package com.anilili.ui.home

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Sample just the active artwork at thumbnail size. Keep the previous palette while the next
 * image loads; animate the two colors rather than repeatedly blurring full-screen bitmaps. */
@Composable
internal fun HomeAmbient(artwork: String, listState: LazyListState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var left by remember { mutableStateOf(Color.Transparent) }
    var right by remember { mutableStateOf(Color.Transparent) }
    LaunchedEffect(artwork) {
        try {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(artwork).size(64, 64).allowHardware(false).build()
            ) as? SuccessResult
            val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
            if (bitmap == null) { left = Color.Transparent; right = Color.Transparent; return@LaunchedEffect }
            val colors = withContext(Dispatchers.Default) {
                val bins = Array(2) { FloatArray(12) }
                val hsv = FloatArray(3)
                for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.alpha(pixel) < 128) continue
                    android.graphics.Color.colorToHSV(pixel, hsv)
                    if (hsv[1] < .18f || hsv[2] < .16f) continue
                    val hue = (hsv[0] / 30f).toInt().coerceIn(0, 11)
                    bins[if (x < bitmap.width / 2) 0 else 1][hue] += hsv[1] * hsv[1] * hsv[2]
                }
                bins.map { weights ->
                    val dominant = weights.indices.maxByOrNull { weights[it] } ?: 0
                    if (weights[dominant] < 1f) Color.Transparent
                    else Color(android.graphics.Color.HSVToColor(floatArrayOf(dominant * 30f + 15f, .66f, .87f)))
                }
            }
            left = colors[0]; right = colors[1]
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { left = Color.Transparent; right = Color.Transparent }
    }
    val a by animateColorAsState(left, tween(750), label = "homeAmbientLeft")
    val b by animateColorAsState(right, tween(750), label = "homeAmbientRight")
    val light = MaterialTheme.colorScheme.background.luminance() > .5f
    Box(modifier.drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        // Read scroll in draw, not composition. Once the spotlight leaves the first item,
        // the ambient wash is gone. A large radius makes the glow reach the first rail.
        val fade = if (listState.firstVisibleItemIndex != 0) 0f
            else (1f - listState.firstVisibleItemScrollOffset / 360.dp.toPx()).coerceIn(0f, 1f)
        if (fade <= 0f) return@drawBehind
        val strength = fade * if (light) .26f else .44f
        val y = size.height * .60f - listState.firstVisibleItemScrollOffset * .45f
        drawRect(Brush.radialGradient(
            0f to a.copy(alpha = a.alpha * strength),
            .55f to a.copy(alpha = a.alpha * strength * .55f),
            1f to Color.Transparent,
            center = Offset(size.width * .12f, y),
            radius = maxOf(size.width * .94f, size.height * .55f),
        ))
        drawRect(Brush.radialGradient(
            0f to b.copy(alpha = b.alpha * strength),
            .55f to b.copy(alpha = b.alpha * strength * .55f),
            1f to Color.Transparent,
            center = Offset(size.width * .88f, y + 48.dp.toPx()),
            radius = maxOf(size.width * .92f, size.height * .52f),
        ))
    })
}
