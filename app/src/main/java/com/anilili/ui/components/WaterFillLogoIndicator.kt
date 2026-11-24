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
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.anilili.R.drawable.anilumos_mark),
        contentDescription = "Anilumos",
        modifier = modifier,
    )
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
