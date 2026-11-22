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
    val rim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.20f to tint.copy(alpha = 0.77f),
                1f to tint.copy(alpha = 0.57f),
            ),
        )
        .border(1.dp, rim, shape)
}

@Composable
fun glassPanelColor(): Color =
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)
