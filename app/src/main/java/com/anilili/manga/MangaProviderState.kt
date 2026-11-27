package com.anilili.manga

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.anilili.data.profiles.Profiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import android.os.SystemClock
import java.io.Closeable

internal class MangaProviderState {
    var source by mutableStateOf<MangaSource?>(null)
    var checking by mutableStateOf(false)
    var unavailable by mutableStateOf(false)
    var remoteSelected by mutableStateOf(true)
    var retry by mutableIntStateOf(0)
    var lastFailureAt = -1L
}
internal val LocalMangaProviderState = staticCompositionLocalOf { MangaProviderState() }
internal val LocalMangaRegistry = staticCompositionLocalOf { MangaSources(emptyList()) }

/** A single foreground manga-mode session shared by Home, Browse and My List.
 * No provider checks when the mode is disabled; no background polling. */
@Composable
internal fun rememberMangaProviderState(enabled: Boolean): MangaProviderState {
    val profile by Profiles.activeId.collectAsState()
    val context = LocalContext.current
    val prefs = remember(context, profile) { context.getSharedPreferences("manga_provider_choice_" + (profile ?: "default"), android.content.Context.MODE_PRIVATE) }
    val state = remember(profile) { MangaProviderState().apply { remoteSelected = prefs.getBoolean("remote", true) } }
    LaunchedEffect(state, state.remoteSelected) { prefs.edit().putBoolean("remote", state.remoteSelected).apply() }
    LaunchedEffect(state, enabled, state.retry) {
        if (!enabled || !reviewedReaderSupported()) return@LaunchedEffect
        if (state.lastFailureAt >= 0 && SystemClock.elapsedRealtime() - state.lastFailureAt < 900_000L) {
            state.unavailable = true
            return@LaunchedEffect
        }
        state.checking = true
        state.unavailable = false
        var opened: MangaSource? = null
        try {
            opened = openReviewedReader()
            currentCoroutineContext().ensureActive()
            state.source = opened
            state.checking = false
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.lastFailureAt = SystemClock.elapsedRealtime()
            state.unavailable = true
        } finally {
            state.source = null
            state.checking = false
            (opened as? Closeable)?.close()
        }
    }
    return state
}
