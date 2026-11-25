package com.anilili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Keep white Android status icons legible even when the scroll-aware toolbar is hidden. */
@Composable
internal fun ArtworkStatusBarScrim(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
        .background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .65f),
            1f to Color.Black.copy(alpha = .30f),
        )))
}
