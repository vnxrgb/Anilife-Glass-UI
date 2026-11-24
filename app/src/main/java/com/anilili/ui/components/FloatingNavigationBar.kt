package com.anilili.ui.components

import com.anilili.ui.tour.TourTarget
import com.anilili.ui.tour.tourTarget

import android.os.Build
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.anilili.data.settings.SettingsStore
import com.anilili.data.settings.GlassMaterial
import androidx.compose.runtime.collectAsState
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
val FloatingNavBarExpandedHeight = 52.dp
val FloatingNavBarMargin = 9.dp

/** An idle icon's slot: the 48dp touch minimum, so the rest is free for the open pill's label. */
private val ItemSize = 48.dp
private val IconSize = 22.dp
private val BarPadding = 3.dp
private val BarShape = RoundedCornerShape(50)

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
            .padding(bottom = bottomInset.coerceAtLeast(0.dp) + FloatingNavBarMargin),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // RenderEffect arrived in Android 12; below it Haze only lays a flat scrim, which is worse
        // than the painted glass, so older phones keep that.
        val inner = (maxWidth - BarPadding * 2).coerceAtLeast(0.dp)
        // Five stable, equal-width destinations. Selecting a tab must not displace
        // its neighbours or change the capsule width.
        val itemWidth = if (items.isEmpty()) ItemSize else inner / items.size
        val showLabels = floatingNavShowsCaption(itemWidth)
        Row(
            // Full width, so the bar's edges stay put while the pill changes length per label;
            // the spare room is shared between the icons instead.
            modifier = Modifier
                .fillMaxWidth()
                .height(FloatingNavBarExpandedHeight)
                // Reduce the dark halo on OLED; the rim carries the depth.
                .opticalGlass(BarShape, enabled = backdrop != null)
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
                    showLabel = showLabels,
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
    Box(
        modifier = modifier
            .size(FloatingNavDiscSize)
            .opticalGlass(CircleShape, enabled = backdrop != null)
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/**
 * On extremely narrow split-screen windows labels lose their slot before icons do.
 * Accessibility semantics still contain the full destination name.
 */
internal fun floatingNavShowsCaption(itemWidth: Dp): Boolean = itemWidth >= 58.dp

/** A touch under the capsule's 58dp, so the disc reads as its companion rather than its rival. */
val FloatingNavDiscSize = 48.dp

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
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val iconColor = if (selected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(50)
    // A persistent, animated neutral selection wash; accent is reserved for the soft rim.
    val selectionAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "navSelectionWash",
    )
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .then(when (item.label.lowercase()) {
                "home" -> Modifier.tourTarget(TourTarget.NAV_HOME)
                "my list" -> Modifier.tourTarget(TourTarget.NAV_LIST)
                "browse" -> Modifier.tourTarget(TourTarget.NAV_BROWSE)
                "downloads" -> Modifier.tourTarget(TourTarget.NAV_DOWNLOADS)
                "profile" -> Modifier.tourTarget(TourTarget.NAV_PROFILE)
                else -> Modifier
            })
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .11f * selectionAlpha))
                .padding(vertical = 3.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(IconSize),
            )
            if (showLabel) {
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
}

/** Critically damped enough not to overshoot the bar's width, soft enough to read as motion. */
private val PillSpring = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
private val PillShape = RoundedCornerShape(50)
private val PillPadding = 14.dp

/** 35dp of pill in the 56dp bar. */
private val PillInset = 10.5.dp
