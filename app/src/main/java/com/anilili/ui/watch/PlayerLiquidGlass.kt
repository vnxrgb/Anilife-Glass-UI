package com.anilili.ui.watch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Neutral native glass for controls that sit over a Media3 SurfaceView.
 *
 * These are lightweight, clipped gradients and a refractive-looking highlight,
 * not a backdrop-filter or a framebuffer capture. Video, subtitles, and Morphe's
 * PixelCopy ambient sampling remain entirely outside the effect.
 *
 * The floating player is rendered many times a second; do not allocate a blur
 * RenderEffect here or distort/copy the whole playing video on each frame.
 */
internal enum class PlayerLiquidGlassTone { SOFT, DOCK, PRIMARY }

internal fun Modifier.playerLiquidGlass(
    shape: Shape,
    tone: PlayerLiquidGlassTone = PlayerLiquidGlassTone.SOFT,
): Modifier {
    val dark = when (tone) {
        PlayerLiquidGlassTone.SOFT -> 0.43f
        PlayerLiquidGlassTone.DOCK -> 0.64f
        PlayerLiquidGlassTone.PRIMARY -> 0.56f
    }
    val glint = when (tone) {
        PlayerLiquidGlassTone.SOFT -> 0.16f
        PlayerLiquidGlassTone.DOCK -> 0.20f
        PlayerLiquidGlassTone.PRIMARY -> 0.29f
    }
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to Color(0xFFB9C5D2).copy(alpha = glint),
                0.25f to Color(0xFF3E4754).copy(alpha = dark * 0.72f),
                0.72f to Color(0xFF131820).copy(alpha = dark),
                1f to Color(0xFF090D13).copy(alpha = (dark + 0.12f).coerceAtMost(0.90f)),
            ),
            shape,
        )
        .drawBehind {
            // The upper-left reflection creates the curved-liquid impression.
            // Its radius is size-dependent but static until the button is resized.
            val radius = maxOf(size.width * 0.95f, size.height * 1.55f, 1f)
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = glint * 0.52f),
                    0.42f to Color.White.copy(alpha = glint * 0.10f),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.12f, 0f),
                    radius = radius,
                ),
            )
        }
        .border(
            BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.48f),
                    0.35f to Color.White.copy(alpha = 0.19f),
                    0.72f to Color.White.copy(alpha = 0.10f),
                    1f to Color.White.copy(alpha = 0.24f),
                ),
            ),
            shape,
        )
}
