package com.anilili.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.anilili.data.settings.SettingsStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The fade the phone's top chrome is drawn in where hero artwork lies beneath it.
 *
 * The television header has always been a gradient laid over the hero: the artwork runs unbroken to
 * the top edge and the type stays legible because the fade is darkest exactly where it sits. An
 * opaque bar instead spends the top of the window before a single frame of the artwork appears, and
 * makes the hero read as a picture pasted into the page rather than as the page itself.
 *
 * The television bar runs two stops, 74% to nothing, because it has one row to cover. This one
 * carries up to two, so it holds the middle rather than falling away linearly — controls at the
 * bottom of a straight gradient sat on almost bare artwork and disappeared against a bright frame.
 * Black rather than the theme background because the app has no light scheme, and everything drawn
 * in here is white or purple either way.
 */
val AppChromeFade = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = .86f),
    0.62f to Color.Black.copy(alpha = .48f),
    1f to Color.Transparent,
)

/**
 * Draws [AppChromeFade] behind top chrome, backed by the page's own colour when [overHero] is false —
 * or, given a [backdrop] that can blur, by that page frosted (see the note in the body).
 *
 * [overHero] is what keeps the fade honest, and it is the part worth recording. Scrolled off the
 * artwork the bar sits over ordinary content, and the first version of this had Home's wordmark
 * landing on One Piece's sky with a score badge showing through it — translucent chrome over cover
 * art is illegible, not clean. So the bare gradient holds only while artwork is what lies beneath,
 * and off it a backing in the page's own colour crossfades up. The backing still lets its last
 * stretch go to nothing, so rows pass under a soft edge rather than being cut off by a line.
 *
 * Crossfaded rather than switched: the bar and the list it reacts to move at the same time, and a
 * hard swap between the two treatments reads as a flash behind whatever the bar is carrying.
 *
 * Shared so the two screens that use it cannot drift into two different gradients.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.chromeFade(overHero: Boolean, backdrop: HazeState? = null): Modifier {
    val scheme = MaterialTheme.colorScheme
    val backingColor = glassPanelColor()
    val material by SettingsStore.glassMaterial.collectAsState()
    val blur by SettingsStore.glassBlur.collectAsState()
    val radius = (if (blur < 0) 24f * material.blurScale else blur * .48f).dp
    val settled by animateFloatAsState(
        targetValue = if (overHero) 0f else 1f,
        animationSpec = tween(220),
        label = "chromeBacking",
    )
    // The scrolling header transitions from a readable artwork scrim to a neutral
    // translucent glass panel. Never paint an opaque canvas/background rectangle,
    // which previously appeared as a solid black band on every scrolled page.
    val glassBrush = remember(backingColor, scheme.onSurface) {
        Brush.verticalGradient(
            0f to backingColor.copy(alpha = 0.77f),
            0.78f to backingColor.copy(alpha = 0.67f),
            1f to backingColor.copy(alpha = 0.18f),
        )
    }
    val frosted = backdrop != null && backdropBlurAvailable
    val glass = if (frosted) {
        Modifier.hazeEffect(
            backdrop!!,
            HazeStyle(
                backgroundColor = scheme.surface,
                tint = HazeTint(backingColor),
                blurRadius = radius,
                noiseFactor = 0f,
            ),
        ) {
            alpha = settled
            mask = ChromeGlassMask
            inputScale = HazeInputScale.Fixed(0.33f)
            canDrawArea = { true }
        }
    } else Modifier

    return this
        .then(glass)
        .drawBehind {
            // Once the header is scrolled over ordinary content, the black hero
            // gradient fades out instead of permanently darkening the glass.
            if (settled < 1f) drawRect(AppChromeFade, alpha = 1f - settled)
            // Keep a painted-glass fallback for devices without live Haze blur.
            if (!frosted && settled > 0f) drawRect(glassBrush, alpha = settled)
        }
}

private val ChromeGlassMask = Brush.verticalGradient(
    0f to Color.Black,
    0.86f to Color.Black,
    1f to Color.Transparent,
)


/** Browse shares the page canvas at rest; this masked glass is only drawn over scrolled posters. */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.browseHeaderGlass(backdrop: HazeState?): Modifier {
    val scheme = MaterialTheme.colorScheme
    val tint = glassPanelColor()
    val material by SettingsStore.glassMaterial.collectAsState()
    val blur by SettingsStore.glassBlur.collectAsState()
    val radius = (if (blur < 0) 24f * material.blurScale else blur * .48f).dp
    return if (backdrop != null && backdropBlurAvailable) {
        this.hazeEffect(
            backdrop,
            HazeStyle(
                backgroundColor = scheme.surface,
                tint = HazeTint(tint),
                blurRadius = radius,
                noiseFactor = 0f,
            ),
        ) {
            mask = ChromeGlassMask
            inputScale = HazeInputScale.Fixed(0.33f)
            canDrawArea = { true }
        }
    } else {
        this.background(
            Brush.verticalGradient(
                0f to scheme.surface.copy(alpha = 0.96f),
                0.78f to scheme.surface.copy(alpha = 0.80f),
                1f to Color.Transparent,
            ),
        )
    }
}
