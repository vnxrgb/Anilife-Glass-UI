package com.anilili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.anilili.ui.theme.appCanvas
import com.anilili.ui.theme.hasAppBackdrop
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Consistent lightweight glass finish for cards, chips and action panels.
 *
 * The existing Haze-backed chrome continues to blur live content where a backdrop
 * source is available; these reusable panels use translucent tinted surfaces and
 * a delicate rim so lazy grids do not allocate a blur layer for every poster.
 */
@Composable
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(18.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val rim = scheme.onSurface.copy(alpha = 0.23f)
    // Raised highlight across the top edge, deeper translucent glass toward the foot.
    // Each card is still a single paint operation: no per-poster RenderEffect allocations.
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to scheme.onSurface.copy(alpha = 0.13f),
                0.18f to tint.copy(alpha = 0.61f),
                0.76f to tint.copy(alpha = 0.43f),
                1f to scheme.surface.copy(alpha = 0.36f),
            ),
        )
        .border(1.dp, rim, shape)
}

@Composable
fun glassPanelColor(): Color =
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.57f)

/** Consistent selected glass pill, independent of the user's accent hue. */
@Composable
fun glassSelectionColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

/** A neutral hairline for translucent panels on light artwork and OLED black. */
@Composable
fun glassRimColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

/**
 * Shared wallpaper-safe cinematic backdrop for handheld glass destinations.
 *
 * The original screen canvas (including a user's own artwork) is drawn first.
 * Two very low-frequency gradient blooms then provide depth under frosted panels.
 * No animated blur, per-card bitmap, or network resource is allocated.
 * TV layouts intentionally keep their existing canvas/navigation treatment.
 */
@Composable
fun Modifier.glassAtmosphere(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val customWallpaper = hasAppBackdrop()
    val strength = if (customWallpaper) 0.35f else 1f
    val cool = scheme.primary
    val warm = scheme.tertiary
    return this.appCanvas().drawBehind {
        val dimension = maxOf(size.width, size.height)
        drawRect(
            Brush.radialGradient(
                colors = listOf(cool.copy(alpha = 0.19f * strength), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.24f),
                radius = dimension * 0.78f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(warm.copy(alpha = 0.15f * strength), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.89f),
                radius = dimension * 0.72f,
            ),
        )
    }
}
