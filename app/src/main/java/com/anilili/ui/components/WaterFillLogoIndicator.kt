package com.anilili.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val CandleCycleMillis = 1_600

/**
 * Anilumos A-and-candle identity. The letter follows the chosen Material theme;
 * the small flame stays candle-gold. Drawn natively: no video decoder, network or
 * bitmap allocations during a playback or startup frame.
 */
@Composable
fun AnilumosBrandMark(
    modifier: Modifier = Modifier,
    flameAmount: Float = 0.5f,
) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { contentDescription = "Anilumos" }) {
        val s = size.minDimension / 108f
        withTransform({
            translate(left = (size.width - 108f * s) / 2f, top = (size.height - 108f * s) / 2f)
            scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
        }) {
            val letter = Path().apply {
                moveTo(28f, 88f); lineTo(52f, 34f); lineTo(80f, 88f)
                moveTo(39f, 71f); quadraticTo(54f, 66f, 68f, 71f)
            }
            drawPath(letter, accent.copy(alpha = 0.10f), style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(letter, accent.copy(alpha = 0.25f), style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(letter, accent, style = Stroke(width = 7.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val flame = Path().apply {
                moveTo(53f, 8f)
                cubicTo(58f, 17f, 66f, 22f, 60f, 30f)
                cubicTo(57f, 35f, 49f, 35f, 46f, 30f)
                cubicTo(42f, 23f, 50f, 18f, 53f, 8f)
                close()
            }
            val flicker = flameAmount.coerceIn(0f, 1f)
            withTransform({ scale(0.96f + flicker * 0.08f, 0.96f + flicker * 0.08f, pivot = Offset(53f, 24f)) }) {
                drawPath(flame, Color(0xFFFFBD62).copy(alpha = 0.13f), style = Stroke(width = 9f))
                drawPath(flame, Color(0xFFFFC56B))
                drawOval(Color(0xFFFFF4D2), topLeft = Offset(50f, 22f), size = androidx.compose.ui.geometry.Size(5f, 8f))
            }
        }
    }
}

/** Same reusable candle for the black splash and regular loading states. */
@Composable
fun WaterFillLogoIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    playOnce: Boolean = false,
    animateFlame: Boolean = true,
    onFilled: () -> Unit = {},
) {
    val pulse = remember { Animatable(0f) }
    val latestOnFilled = rememberUpdatedState(onFilled)
    LaunchedEffect(playOnce, animateFlame) {
        if (!animateFlame) {
            if (playOnce) latestOnFilled.value()
            return@LaunchedEffect
        }
        if (playOnce) {
            pulse.animateTo(1f, tween(CandleCycleMillis, easing = LinearEasing))
            latestOnFilled.value()
        } else {
            while (true) {
                pulse.animateTo(1f, tween(CandleCycleMillis, easing = LinearEasing))
                pulse.animateTo(0f, tween(CandleCycleMillis, easing = LinearEasing))
            }
        }
    }
    AnilumosBrandMark(
        modifier = modifier.size(size),
        flameAmount = if (animateFlame) pulse.value else 0.5f,
    )
}
