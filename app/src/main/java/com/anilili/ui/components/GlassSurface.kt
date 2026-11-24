package com.anilili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.anilili.ui.theme.appCanvas

/**
 * Static companion to the optical floating chrome. Content is inside the recorded
 * source layer, so sampling that layer here would create a feedback loop.
 * Use a subtle directional specular edge and neutral material instead.
 */
@Composable
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
): Modifier {
    val light = MaterialTheme.colorScheme.surface.luminance() > .5f
    val edge = if (light) Color.Black else Color.White
    return clip(shape)
        .background(tint.copy(alpha = .88f), shape)
        .background(Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (light) .24f else .075f),
            .32f to Color.Transparent,
            1f to Color.Transparent,
        ), shape)
        .border(.5.dp, Brush.verticalGradient(
            0f to edge.copy(alpha = .24f),
            .5f to edge.copy(alpha = .06f),
            1f to edge.copy(alpha = .12f),
        ), shape)
}

@Composable
fun glassPanelColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .92f)

@Composable
fun glassSelectionColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)

@Composable
fun glassRimColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .15f)

/** No artificial background blooms; keep existing wallpaper. */
@Composable
fun Modifier.glassAtmosphere(): Modifier = this.appCanvas()
