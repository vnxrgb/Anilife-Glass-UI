package com.anilili.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anilili.R

private const val LaunchCycleMillis = 1600
private val BrandPurple = Color(0xFF6D3DF2)
private val BrandGold = Color(0xFFFFD23F)

/** Static brand mark for headers and other in-app branding. */
@Composable
fun AnilumosBrandMark(
    modifier: Modifier = Modifier,
    flameAmount: Float = 0.5f,
) {
    Image(
        painter = painterResource(R.drawable.anilumos_mark),
        contentDescription = "Anilumos",
        modifier = modifier,
    )
}

/** Native logo reveal: purple tile, two white strokes, then the golden light. */
@Composable
fun WaterFillLogoIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    playOnce: Boolean = false,
    animateFlame: Boolean = true,
    onFilled: () -> Unit = {},
) {
    val progress = remember { Animatable(if (playOnce && animateFlame) 0f else 1f) }
    val latestOnFilled = rememberUpdatedState(onFilled)
    LaunchedEffect(playOnce, animateFlame) {
        if (!animateFlame) {
            progress.snapTo(1f)
            if (playOnce) latestOnFilled.value()
        } else if (playOnce) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(LaunchCycleMillis, easing = LinearEasing))
            latestOnFilled.value()
        } else {
            progress.snapTo(1f)
        }
    }
    Canvas(modifier.size(size).semantics { contentDescription = "Anilumos" }) {
        val p = progress.value
        fun phase(start: Float, end: Float): Float =
            FastOutSlowInEasing.transform(((p - start) / (end - start)).coerceIn(0f, 1f))
        val tile = phase(0f, .32f)
        val stroke = phase(.15f, .67f)
        val dot = phase(.57f, .86f)
        val canvasSize = this.size
        val s = canvasSize.minDimension / 100f
        withTransform({
            translate((canvasSize.width - 100f * s) / 2f, (canvasSize.height - 100f * s) / 2f)
            scale(s, s, pivot = Offset.Zero)
        }) {
            withTransform({ scale(.84f + .16f * tile, .84f + .16f * tile, pivot = Offset(50f, 50f)) }) {
                drawRoundRect(BrandPurple.copy(alpha = tile), size = Size(100f, 100f), cornerRadius = CornerRadius(22f))
                if (stroke > 0f) {
                    val tip = Offset(50f, 26f)
                    drawLine(Color.White, tip, Offset(50f - 22f * stroke, 26f + 48f * stroke), 12f, cap = StrokeCap.Round)
                    drawLine(Color.White, tip, Offset(50f + 22f * stroke, 26f + 48f * stroke), 12f, cap = StrokeCap.Round)
                }
                if (dot > 0f) {
                    drawCircle(BrandGold.copy(alpha = dot), radius = 6.5f * dot, center = Offset(50f, 64f))
                }
            }
        }
    }
}
