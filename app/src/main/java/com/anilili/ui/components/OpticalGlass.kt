package com.anilili.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight

// Only floating chrome samples the page; scrolling content must never sample itself.
val LocalOpticalBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
fun Modifier.opticalGlass(
    shape: CornerBasedShape,
    enabled: Boolean = true,
    navigation: Boolean = false,
): Modifier {
    val source = LocalOpticalBackdrop.current
    val surface = MaterialTheme.colorScheme.surface
    if (!enabled || source == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return clip(shape).background(surface, shape)
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .12f), shape)
    }
    val light = surface.luminance() > .5f
    // Navigation sits over arbitrary artwork. A theme-colored backing keeps dark
    // captions legible on black artwork (and white captions legible on bright art).
    // Leave other optical chrome unchanged and keep a visible refracted backdrop.
    val tint = if (navigation) surface else if (light) Color.White else Color.Black
    val tintAlpha = if (navigation) navigationGlassOpacity(light) else if (light) .38f else .40f
    return drawBackdrop(
        backdrop = source,
        shape = { shape },
        effects = {
            blur((if (navigation) 18.dp else 10.dp).toPx())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(refractionHeight = 14.dp.toPx(), refractionAmount = 12.dp.toPx(),
                    depthEffect = true, chromaticAberration = true)
            }
        },
        highlight = { Highlight(width = 0.4.dp) },
        onDrawSurface = { drawRect(tint.copy(alpha = tintAlpha)) },
    )
}
