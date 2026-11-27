package com.anilili.manga

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
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

@Stable
class ContentModePreview {
    var target by mutableStateOf<ContentMode?>(null)
        internal set
    val progress = Animatable(0f)
    internal var animation: Job? = null
}

fun Modifier.swipeHoldContentMode(enabled: Boolean, preview: ContentModePreview): Modifier = composed {
    val scope = rememberCoroutineScope()
    val mode by rememberUpdatedState(LocalContentMode.current)
    val select by rememberUpdatedState(LocalSelectContentMode.current)
    val view = LocalView.current
    if (!enabled) Modifier else Modifier.pointerInput(view, preview) {
        val distance = 56.dp.toPx()
        val drift = 10.dp.toPx()
        val edge = 24.dp.toPx()
        val startBand = 72.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (preview.target != null || down.position.x < edge || down.position.x > size.width - edge) return@awaitEachGesture
            val initialMode = mode
            val direction = if (initialMode == ContentMode.ANIME) -1 else 1
            if (direction < 0 && down.position.x < size.width - startBand ||
                direction > 0 && down.position.x > startBand) return@awaitEachGesture
            val target = if (initialMode == ContentMode.ANIME) ContentMode.MANGA else ContentMode.ANIME
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
                    change.consume()
                    preview.target = target
                    preview.animation?.cancel()
                    preview.animation = scope.launch {
                        preview.progress.snapTo((abs(delta.x) / size.width).coerceIn(0.12f, 0.32f))
                        preview.progress.animateTo(0.34f, tween(CONTENT_MODE_HOLD_MS.toInt()))
                    }
                    break
                }
            }
            // A timeout means the finger remained stationary; a release or reversal cancels.
            val cancelled = withTimeoutOrNull(CONTENT_MODE_HOLD_MS) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed || event.changes.count { it.pressed } != 1 ||
                        (change.position - anchor).getDistance() > drift) break
                }
                true
            }
            if (cancelled == null && mode == initialMode) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                preview.animation?.cancel()
                preview.animation = scope.launch {
                    preview.progress.animateTo(1f, spring(dampingRatio = 0.95f, stiffness = 450f))
                    select(target)
                    // Keep the incoming page covering the navigation change for a frame.
                    androidx.compose.runtime.withFrameNanos { }
                    preview.target = null
                    preview.progress.snapTo(0f)
                }
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            } else {
                preview.animation?.cancel()
                preview.animation = scope.launch {
                    preview.progress.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 450f))
                    preview.target = null
                }
            }
        }
    }
}
