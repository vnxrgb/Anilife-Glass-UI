package com.anilili.ui.components

import android.os.Build
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anilili.ui.theme.LocalMidnightAccent
import com.anilili.ui.theme.Midnight

/** One destination; the label remains available to accessibility services. */
@Immutable
data class FloatingNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** The capsule's one height; scrolling no longer changes it. */
val FloatingNavBarExpandedHeight = 58.dp
val FloatingNavBarMargin = 12.dp

/** An idle icon's slot: the 48dp touch minimum, so the rest is free for the open pill's label. */
private val ItemSize = 48.dp
private val IconSize = 22.dp
private val BarPadding = 6.dp
private val BarShape = RoundedCornerShape(50)

// A gentle top light keeps the glass character without a white sheen across the icons.
// The nearly opaque center preserves contrast when bright artwork scrolls behind the bar.
// Built from the theme, so they are rebuilt only when the theme changes.
@Composable
private fun barGlass(): Brush {
    val top = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    val middle = MaterialTheme.colorScheme.surfaceContainerHigh
    val bottom = MaterialTheme.colorScheme.surfaceContainer
    return remember(top, middle, bottom) {
        Brush.verticalGradient(
            0f to top.copy(alpha = 0.76f),
            0.4f to middle.copy(alpha = 0.66f),
            1f to bottom.copy(alpha = 0.75f),
        )
    }
}

/**
 * Live glass: what scrolls behind the bar, blurred and darkened enough that the idle icons keep
 * their contrast over a white poster. The tint is the painted glass's own middle colour.
 */
@Composable
private fun barBlur(): HazeStyle {
    val background = MaterialTheme.colorScheme.surface
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh
    return remember(background, tint) {
        HazeStyle(
            backgroundColor = background,
            tint = HazeTint(tint.copy(alpha = 0.50f)),
            blurRadius = 24.dp,
            noiseFactor = 0.04f,
        )
    }
}

@Composable
private fun barRim(): Brush {
    val top = MaterialTheme.colorScheme.onSurface
    val middle = MaterialTheme.colorScheme.onSurface
    val bottom = MaterialTheme.colorScheme.onSurface
    return remember(top, middle, bottom) {
        Brush.verticalGradient(
            0f to top.copy(alpha = 0.18f),
            0.5f to middle.copy(alpha = 0.45f),
            1f to bottom.copy(alpha = 0.35f),
        )
    }
}

/**
 * A quiet, softly shaded glass surface. The current destination opens into a filled pill carrying
 * its name, so the page is named without a second row of text under every icon; the pill springs
 * open and its neighbours slide aside, which is what makes a tab change read as movement.
 *
 * One fixed height. The bar used to trim to a compact height on a downward scroll and grow back
 * on an upward one, a leftover of the Nuvio-style labelled bar; on this pill design the 4 dp step
 * read only as the bar twitching under the thumb.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun FloatingNavigationBar(
    items: List<FloatingNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    /** The content behind the bar, blurred through it on Android 12+; null keeps painted glass. */
    backdrop: HazeState? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = bottomInset + FloatingNavBarMargin),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // RenderEffect arrived in Android 12; below it Haze only lays a flat scrim, which is worse
        // than the painted glass, so older phones keep that.
        val blurred = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val inner = maxWidth - BarPadding * 2
        // Five stable, equal-width destinations. Selecting a tab must not displace
        // its neighbours or change the capsule width.
        val itemWidth = if (items.isEmpty()) ItemSize else inner / items.size
        val pillMax = itemWidth
        Row(
            // Full width, so the bar's edges stay put while the pill changes length per label;
            // the spare room is shared between the icons instead.
            modifier = Modifier
                .fillMaxWidth()
                .height(FloatingNavBarExpandedHeight)
                .shadow(10.dp, BarShape)
                .clip(BarShape)
                .then(
                    if (blurred) {
                        // A third of the resolution: the blur discards that detail anyway. On an
                        // S25 it cut the GPU 90th percentile while scrolling from 10ms to 6ms
                        // (painted glass: 4ms) with no visible difference.
                        Modifier.hazeEffect(backdrop!!, barBlur()) { inputScale = HazeInputScale.Fixed(0.33f) }
                    } else {
                        Modifier.background(barGlass())
                    },
                )
                .border(1.dp, barRim(), BarShape)
                .padding(horizontal = BarPadding)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                FloatingNavIcon(
                    item = item,
                    selected = item.route == selectedRoute,
                    width = itemWidth,
                    maxWidth = pillMax,
                    onClick = { onSelect(item.route) },
                )
            }
        }
    }
}

/**
 * A round action floating beside the capsule, in the capsule's own glass, rim and shadow, so it
 * reads as part of the same chrome rather than a Material FAB dropped on top of it. Blurred exactly
 * when the capsule is: with Navigation glass off, or below Android 12, it is the painted glass too.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun FloatingNavDisc(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: HazeState? = null,
    icon: @Composable () -> Unit,
) {
    val blurred = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier = modifier
            .size(FloatingNavDiscSize)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .then(
                if (blurred) {
                    Modifier.hazeEffect(backdrop!!, barBlur()) { inputScale = HazeInputScale.Fixed(0.33f) }
                } else {
                    Modifier.background(barGlass())
                },
            )
            .border(1.dp, barRim(), CircleShape)
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/** A touch under the capsule's 58dp, so the disc reads as its companion rather than its rival. */
val FloatingNavDiscSize = 52.dp

/**
 * Fixed-width navigation destination. Icons remain visible in every state, with a
 * small caption below; a translucent capsule highlights the selected tab without
 * forcing neighbouring items to shift as they did in the expanding-label version.
 */
@Composable
private fun FloatingNavIcon(
    item: FloatingNavItem,
    selected: Boolean,
    width: Dp,
    maxWidth: Dp,
    onClick: () -> Unit,
) {
    val iconColor = if (selected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 3.dp, horizontal = 2.dp)
            .clip(shape)
            .then(
                if (selected) Modifier
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.19f),
                            1f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                        ),
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f), shape)
                else Modifier
            )
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(IconSize),
            )
            Text(
                text = item.label,
                color = iconColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Critically damped enough not to overshoot the bar's width, soft enough to read as motion. */
private val PillSpring = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
private val PillShape = RoundedCornerShape(50)
private val PillPadding = 14.dp

/** 35dp of pill in the 56dp bar. */
private val PillInset = 10.5.dp
