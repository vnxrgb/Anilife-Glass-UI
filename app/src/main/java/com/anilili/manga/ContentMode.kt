package com.anilili.manga

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import kotlin.math.abs

enum class ContentMode { ANIME, MANGA }
enum class MangaSection { HOME, BROWSE, LIBRARY, DOWNLOADS }

val LocalContentMode = staticCompositionLocalOf { ContentMode.ANIME }
val LocalSelectContentMode = staticCompositionLocalOf<(ContentMode) -> Unit> { {} }

// The old header switch is intentionally no longer rendered.
@Composable
fun ContentModeSwitcher(modifier: Modifier = Modifier) = Unit

internal const val CONTENT_MODE_HOLD_MS = 400L

fun Modifier.swipeHoldContentMode(enabled: Boolean): Modifier = composed {
    val mode by rememberUpdatedState(LocalContentMode.current)
    val select by rememberUpdatedState(LocalSelectContentMode.current)
    val view = LocalView.current
    if (!enabled) Modifier else Modifier.pointerInput(view) {
        val distance = 56.dp.toPx()
        val drift = 10.dp.toPx()
        val edge = 24.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x < edge || down.position.x > size.width - edge) return@awaitEachGesture
            val initialMode = mode
            val direction = if (initialMode == ContentMode.ANIME) -1 else 1
            var anchor = down.position
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed || event.changes.count { it.pressed } != 1) return@awaitEachGesture
                val delta = change.position - down.position
                if (abs(delta.y) > distance / 2 && abs(delta.y) > abs(delta.x) / 2) return@awaitEachGesture
                if (delta.x * direction < -distance / 2) return@awaitEachGesture
                if (delta.x * direction >= distance && abs(delta.x) > abs(delta.y) * 2) {
                    anchor = change.position
                    break
                }
            }
            // A timeout means the finger remained stationary; a release or reversal cancels.
            val cancelled = withTimeoutOrNull(CONTENT_MODE_HOLD_MS) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || event.changes.count { it.pressed } != 1 ||
                        (change.position - anchor).getDistance() > drift) break
                }
                true
            }
            if (cancelled == null && mode == initialMode) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                select(if (initialMode == ContentMode.ANIME) ContentMode.MANGA else ContentMode.ANIME)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}
