package com.anilili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.anilili.data.settings.SettingsStore
import com.anilili.data.settings.GlassMaterial
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val material by SettingsStore.glassMaterial.collectAsState()
    val opacity by SettingsStore.glassOpacity.collectAsState()
    val borders by SettingsStore.glassBorders.collectAsState()
    val custom = opacity >= 0
    val liquid = material == GlassMaterial.LIQUID
    val alpha = if (custom) (opacity / 100f).coerceIn(.15f, 1f) else material.panelAlpha
    val fill = if (material == GlassMaterial.SMOKED) scheme.surface else tint
    val rim = scheme.onSurface.copy(alpha = if (liquid) .32f else .16f)
    val highlight = scheme.onSurface.copy(alpha = if (liquid) .11f else .035f)
    // Raised highlight across the top edge, deeper translucent glass toward the foot.
    // Each card is still a single paint operation: no per-poster RenderEffect allocations.
    return this
        .shadow(if (liquid) 3.dp else 1.dp, shape, clip = false)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to fill.copy(alpha = alpha),
                0.18f to fill.copy(alpha = alpha * if (liquid) .82f else .98f),
                0.75f to fill.copy(alpha = alpha * if (liquid) .74f else .95f),
                1f to scheme.surface.copy(alpha = alpha),
            ),
        )
        // One rim avoids a double hairline at nested corners and saves overdraw on poster grids.
        .background(Brush.verticalGradient(0f to highlight, .20f to Color.Transparent, 1f to Color.Transparent))
        .then(if (borders) Modifier.border(1.dp, Brush.verticalGradient(0f to rim, .55f to rim.copy(alpha = rim.alpha * .35f), 1f to rim.copy(alpha = rim.alpha * .65f)), shape) else Modifier)
}

@Composable
fun glassPanelColor(): Color {
    val material by SettingsStore.glassMaterial.collectAsState()
    val opacity by SettingsStore.glassOpacity.collectAsState()
    val scheme = MaterialTheme.colorScheme
    return (if (material == GlassMaterial.SMOKED) scheme.surface else scheme.surfaceContainerHigh)
        .copy(alpha = if (opacity < 0) material.panelAlpha else (opacity / 100f).coerceIn(.15f, 1f))
}

/** Consistent selected glass pill, independent of the user's accent hue. */
@Composable
fun glassSelectionColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

/** A neutral hairline for translucent panels on light artwork and OLED black. */
@Composable
fun glassRimColor(): Color {
    val borders by SettingsStore.glassBorders.collectAsState()
    return MaterialTheme.colorScheme.onSurface.copy(alpha = if (borders) .20f else 0f)
}

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
    // Keep custom wallpapers visible while allowing extra cinematic depth on the default canvas.
    val ambient by SettingsStore.glassAmbient.collectAsState()
    val strength = ambient / 100f * if (customWallpaper) .225f else 1f
    val cool = scheme.primary
    val warm = scheme.tertiary
    return this.appCanvas().drawBehind {
        val dimension = maxOf(size.width, size.height)
        // A zero-size first layout pass must not construct a zero-radius radial shader.
        if (dimension <= 0f) return@drawBehind
        drawRect(
            Brush.radialGradient(
                colors = listOf(cool.copy(alpha = 0.09f * strength), Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.21f),
                radius = dimension * 0.64f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(warm.copy(alpha = 0.07f * strength), Color.Transparent),
                center = Offset(size.width * 0.10f, size.height * 0.95f),
                radius = dimension * 0.66f,
            ),
        )
    }
}
