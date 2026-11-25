package com.anilili.ui.watch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * How long "Skip Recap" and "Skip Intro" show by themselves once the playhead enters their window.
 *
 * Both used to appear only with the controls up, so the opening started with nothing on screen and
 * the button turned up only after a tap. Viewers asked for it to pop up the way "Skip Outro" does.
 * They were tied to the controls for a reason, though (e2216d98): a window lasts as long as the
 * opening it describes and can start at 0:00, and ReAnime's 0-89s intro kept the button over the
 * picture for a minute and a half with no way to dismiss it. So the button pops up for this long,
 * the way the streaming services do it, and after that it shows only with the controls.
 */
internal const val TIMED_SKIP_POPUP_MS = 6_000L

/**
 * Whether a timed skip is still within its pop-up after the playhead entered its window
 * ([startMs] until [endMs]).
 *
 * The clock runs on time spent playing, not wall time: the playhead sits at 0:00 inside an opening
 * while a stream loads or a resume seek lands, and a wall clock had used up the pop-up before the
 * first frame. Leaving the window re-arms it, so seeking back into an opening offers the button again.
 */
internal class TimedSkipPopup(private val remaining: State<Float>, private val inWindow: Boolean) {
    val fraction: Float get() = if (inWindow) remaining.value else 0f
    val showing: Boolean get() = fraction > 0f
}

@Composable
internal fun rememberTimedSkipPopup(inWindow: Boolean, playing: Boolean, startMs: Long?, endMs: Long?): TimedSkipPopup {
    val progress = remember(startMs, endMs) { Animatable(1f) }
    LaunchedEffect(inWindow, playing, progress) {
        if (!inWindow) {
            progress.snapTo(1f)
        } else if (playing && progress.value > 0f) {
            progress.animateTo(0f, tween(
                durationMillis = (TIMED_SKIP_POPUP_MS * progress.value).toInt().coerceAtLeast(1),
                easing = LinearEasing,
            ))
        }
    }
    return TimedSkipPopup(progress.asState(), inWindow)
}
