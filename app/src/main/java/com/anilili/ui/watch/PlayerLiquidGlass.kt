package com.anilili.ui.watch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
        PlayerLiquidGlassTone.SOFT -> 0.32f
        PlayerLiquidGlassTone.DOCK -> 0.49f
        PlayerLiquidGlassTone.PRIMARY -> 0.38f
    }
    val glint = when (tone) {
        PlayerLiquidGlassTone.SOFT -> 0.055f
        PlayerLiquidGlassTone.DOCK -> 0.075f
        PlayerLiquidGlassTone.PRIMARY -> 0.10f
    }
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                // Dark neutral glass, not a large silver/grey fill that veils
                // bright anime frames or fights the Morphe lighting.
                0f to Color.White.copy(alpha = glint),
                0.18f to Color(0xFF101013).copy(alpha = dark * 0.60f),
                0.74f to Color(0xFF09090D).copy(alpha = dark),
                1f to Color(0xFF050507).copy(alpha = (dark + 0.08f).coerceAtMost(0.75f)),
            ),
            shape,
        )
        .drawWithCache {
            // Cache the reflection shader until size changes; avoid recreating
            // a gradient on each Media3 control redraw or time update.
            val radius = maxOf(size.width * 0.95f, size.height * 1.55f, 1f)
            val reflection = Brush.radialGradient(
                0f to Color.White.copy(alpha = glint * 0.52f),
                0.42f to Color.White.copy(alpha = glint * 0.10f),
                1f to Color.Transparent,
                center = Offset(size.width * 0.12f, 0f),
                radius = radius,
            )
            onDrawBehind { drawRect(brush = reflection) }
        }
        .border(
            BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.28f),
                    0.35f to Color.White.copy(alpha = 0.13f),
                    0.72f to Color.White.copy(alpha = 0.07f),
                    1f to Color.White.copy(alpha = 0.15f),
                ),
            ),
            shape,
        )
}
