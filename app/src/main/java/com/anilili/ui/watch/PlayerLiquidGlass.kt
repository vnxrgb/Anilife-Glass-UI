package com.anilili.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.anilili.data.settings.SettingsStore
import com.anilili.data.settings.GlassMaterial
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

@Composable
internal fun Modifier.playerLiquidGlass(
    shape: Shape,
    tone: PlayerLiquidGlassTone = PlayerLiquidGlassTone.SOFT,
): Modifier {
    // Media3 is a separate surface. Use a readable neutral overlay, never capture or distort video.
    val alpha = when (tone) {
        PlayerLiquidGlassTone.SOFT -> .42f
        PlayerLiquidGlassTone.DOCK -> .64f
        PlayerLiquidGlassTone.PRIMARY -> .52f
    }
    return clip(shape)
        .background(Color.Black.copy(alpha = alpha), shape)
        .border(0.5.dp, Color.White.copy(alpha = .18f), shape)
}
