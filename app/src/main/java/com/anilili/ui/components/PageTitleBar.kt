package com.anilili.ui.components

import com.anilili.ui.theme.appCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anilili.ui.adaptive.LocalAppDeviceProfile

/**
 * The title line every top-level page wears.
 *
 * There is one of these rather than a `TopAppBar` per screen because a `TopAppBar` cannot hold this
 * title. Its title slot is styled and vertically centred for a single line of `titleLarge` inside a
 * fixed 64dp box, so a headline set in it either clips or takes a baseline of the bar's choosing
 * rather than its neighbours' — which is what left My List's title reading a line below the account
 * pill beside it. A plain Row centred on its own content puts everything on one line and is only as
 * tall as its contents need.
 *
 * [leading] is for a back arrow, [trailing] for something belonging to the page as a whole, such as
 * the account pill on My List. Both are absent on most pages, which is the point: the pages differ
 * in what they list, not in where their name sits.
 */
@Composable
fun PageTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /**
     * Transparent where the bar sits on frosted glass that already provides the fill. Null paints
     * the parent canvas on phones; TV retains its own canvas.
     */
    background: Color? = null,
) {
    val device = LocalAppDeviceProfile.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when {
                    background != null -> Modifier.background(background)
                    device.isTv -> Modifier.appCanvas()
                    // Handheld pages already paint their own canvas or glass backdrop.
                    // Do not paint a second rectangular surface behind the title.
                    else -> Modifier
                },
            )
            // Keep a soft canvas-backed header without drawing another rectangular border.
            .statusBarsPadding()
            .padding(
                // A back arrow carries its own touch padding, so the page inset would set it in
                // from the edge twice over and leave it visibly further right than every title.
                start = if (leading != null) 4.dp else device.pagePadding,
                end = device.pagePadding,
                top = 8.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(
            Modifier.weight(1f).widthIn(min = 0.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                style = if (device.isTv) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Keep the trailing account/action control at the same vertical centre as the title.
        // Its own semantics and touch area remain owned by the caller.
        trailing?.invoke()
    }
}
