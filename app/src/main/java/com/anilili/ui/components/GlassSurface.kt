package com.anilili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.anilili.ui.theme.appCanvas

/**
 * Neutral reset for content surfaces. These compatibility entry points deliberately
 * do not render the previous imitation glass treatment. Floating optical surfaces
 * will use a separate renderer; dense lists should remain inexpensive and readable.
 */
@Composable
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
): Modifier = clip(shape).background(tint.copy(alpha = 1f), shape)

@Composable
fun glassPanelColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
fun glassSelectionColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)

@Composable
fun glassRimColor(): Color = Color.Transparent

/** Retain the chosen wallpaper, remove the previous artificial colour blooms. */
@Composable
fun Modifier.glassAtmosphere(): Modifier = this.appCanvas()
