package com.anilili.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared handheld action hierarchy for AniLife glass screens.
 *
 * Primary actions are deliberately independent of colorScheme.primary: themes with a
 * green accent must not wash the entire Watch/Continue CTA green. The primary action
 * uses a neutral, high-contrast surface rather than a fixed orange/purple/green fill. Supporting chips,
 * list toggles and tabs remain neutral glass, leaving the accent to indicate selection.
 * Keep TV's existing focus colours at the call sites.
 */
private val PrimaryFill = Color(0xFFF4F4F5)
private val PrimaryInk = Color(0xFF17181B)

@Composable
fun cinematicPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = PrimaryFill,
    contentColor = PrimaryInk,
    disabledContainerColor = PrimaryFill.copy(alpha = 0.24f),
    disabledContentColor = PrimaryInk.copy(alpha = 0.48f),
)

@Composable
fun cinematicSecondaryButtonColors(onArtwork: Boolean = false) =
    ButtonDefaults.outlinedButtonColors(
        containerColor = if (onArtwork) Color.Black.copy(alpha = 0.38f) else glassPanelColor(),
        contentColor = if (onArtwork) Color.White else MaterialTheme.colorScheme.onSurface,
    )

@Composable
fun cinematicSecondaryButtonBorder(onArtwork: Boolean = false) = BorderStroke(
    1.dp,
    if (onArtwork) Color.White.copy(alpha = 0.36f) else glassRimColor(),
)

@Composable
fun cinematicFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = glassPanelColor(),
    selectedContainerColor = glassSelectionColor(),
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
)
