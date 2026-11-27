package com.anilili.manga

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.anilili.data.profiles.Profiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.os.SystemClock
import java.io.Closeable

internal class MangaProviderState {
    var source by mutableStateOf<MangaSource?>(null)
    var checking by mutableStateOf(false)
    var unavailable by mutableStateOf(false)
    var remoteSelected by mutableStateOf(true)
    var lastFailureAt = -1L
    var generation = 0L
}
internal val LocalMangaProviderState = staticCompositionLocalOf { MangaProviderState() }
internal val LocalMangaRegistry = staticCompositionLocalOf { MangaSources(emptyList()) }

/** A single foreground manga-mode session shared by Home, Browse and My List.
 * No provider checks when mode is disabled or lifecycle is stopped; failed checks recover after cooldown. */
@Composable
internal fun rememberMangaProviderState(enabled: Boolean): MangaProviderState {
    val profile by Profiles.activeId.collectAsState()
    val context = LocalContext.current
    val prefs = remember(context, profile) { context.getSharedPreferences("manga_provider_choice_" + (profile ?: "default"), android.content.Context.MODE_PRIVATE) }
    val state = remember(profile) { MangaProviderState().apply { remoteSelected = prefs.getBoolean("remote", true) } }
    LaunchedEffect(state, state.remoteSelected) { prefs.edit().putBoolean("remote", state.remoteSelected).apply() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state, enabled, lifecycle) {
        val generation = ++state.generation
        if (!enabled || !reviewedReaderSupported()) {
            state.source = null
            state.checking = false
            return@LaunchedEffect
        }
        while (true) {
            val remaining = if (state.lastFailureAt < 0) 0L else
                (900_000L - (SystemClock.elapsedRealtime() - state.lastFailureAt)).coerceAtLeast(0L)
            if (remaining > 0L) {
                state.unavailable = true
                delay(remaining)
            }
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
            currentCoroutineContext().ensureActive()
            state.checking = true
            state.unavailable = false
            var opened: MangaSource? = null
            try {
                opened = openReviewedReader()
                currentCoroutineContext().ensureActive()
                if (state.generation != generation) return@LaunchedEffect
                state.source = opened
                state.checking = false
                state.lastFailureAt = -1L
                awaitCancellation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (state.generation == generation) {
                    state.lastFailureAt = SystemClock.elapsedRealtime()
                    state.unavailable = true
                }
            } finally {
                if (state.generation == generation) {
                    state.source = null
                    state.checking = false
                }
                (opened as? Closeable)?.close()
            }
        }
    }
    return state
}
