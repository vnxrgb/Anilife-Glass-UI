package com.anilili.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Neutral frosted action hierarchy. The primary CTA is distinguished by a brighter
 * glass surface and rim, not an opaque white/green/orange fill. Artwork buttons stay
 * readable over any banner; regular surfaces follow the current light/dark theme.
 * TV focus styling stays with each caller.
 */
@Composable
fun cinematicPrimaryButtonColors(onArtwork: Boolean = false) = ButtonDefaults.buttonColors(
    containerColor = if (onArtwork) Color.White
        else MaterialTheme.colorScheme.primary,
    contentColor = if (onArtwork) Color.Black else MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = if (onArtwork) Color.Black.copy(alpha = 0.27f)
        else glassPanelColor().copy(alpha = 0.40f),
    disabledContentColor = if (onArtwork) Color.White.copy(alpha = 0.44f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.44f),
)

@Composable
fun cinematicPrimaryButtonBorder(onArtwork: Boolean = false) = BorderStroke(
    1.dp,
    if (onArtwork) Color.Transparent
        else Color.Transparent,
)

@Composable
fun cinematicSecondaryButtonColors(onArtwork: Boolean = false) =
    ButtonDefaults.outlinedButtonColors(
        containerColor = if (onArtwork) Color.Black.copy(alpha = 0.58f)
            else glassPanelColor(),
        contentColor = if (onArtwork) Color.White else MaterialTheme.colorScheme.onSurface,
    )

@Composable
fun cinematicSecondaryButtonBorder(onArtwork: Boolean = false) = BorderStroke(
    1.dp,
    if (onArtwork) Color.White.copy(alpha = 0.28f) else glassRimColor(),
)

@Composable
fun cinematicFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = glassPanelColor(),
    selectedContainerColor = glassSelectionColor(),
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
)
