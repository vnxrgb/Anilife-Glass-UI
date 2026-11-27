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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.os.SystemClock
import java.io.Closeable

internal class MangaProviderState {
    val sources = mutableStateMapOf<String, MangaSource>()
    val checking = mutableStateMapOf<String, Boolean>()
    val unavailable = mutableStateMapOf<String, Boolean>()
    var selectedId by mutableStateOf(REVIEWED_SOURCE_ID)
    val failures = mutableMapOf<String, Long>()
    var generation = 0L
}
internal val LocalMangaProviderState = staticCompositionLocalOf { MangaProviderState() }
internal val LocalMangaRegistry = staticCompositionLocalOf { MangaSources(emptyList()) }

/** Per-source lifecycles are independent and only explicitly reviewed descriptors are probed.
 * No global parser scans; no automatic activation from an offline parser inventory. */
@Composable
internal fun rememberMangaProviderState(enabled: Boolean): MangaProviderState {
    val profile by Profiles.activeId.collectAsState()
    val context = LocalContext.current
    val prefs = remember(context, profile) { context.getSharedPreferences("manga_provider_choice_" + (profile ?: "default"), android.content.Context.MODE_PRIVATE) }
    val state = remember(profile) { MangaProviderState().apply {
        selectedId = prefs.getString("selected", REVIEWED_SOURCE_ID) ?: REVIEWED_SOURCE_ID
    } }
    LaunchedEffect(state, state.selectedId) { prefs.edit().putString("selected", state.selectedId).apply() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state, enabled, lifecycle) {
        val generation = ++state.generation
        if (!enabled) { state.sources.clear(); state.checking.clear(); return@LaunchedEffect }
        coroutineScope {
            reviewedReaderProviders().distinctBy { it.id }.forEach { descriptor ->
                launch {
                    while (true) {
                        val remaining = (900_000L - (SystemClock.elapsedRealtime() - (state.failures[descriptor.id] ?: -900_000L))).coerceAtLeast(0L)
                        if (remaining > 0L) { state.unavailable[descriptor.id] = true; delay(remaining) }
                        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
                        currentCoroutineContext().ensureActive()
                        state.checking[descriptor.id] = true
                        state.unavailable[descriptor.id] = false
                        var opened: MangaSource? = null
                        try {
                            opened = descriptor.open()
                            require(opened.id == descriptor.id)
                            currentCoroutineContext().ensureActive()
                            if (state.generation != generation) return@launch
                            state.sources[descriptor.id] = opened
                            state.checking[descriptor.id] = false
                            state.failures.remove(descriptor.id)
                            awaitCancellation()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            if (state.generation == generation) {
                                state.failures[descriptor.id] = SystemClock.elapsedRealtime()
                                state.unavailable[descriptor.id] = true
                            }
                        } finally {
                            if (state.generation == generation) { state.sources.remove(descriptor.id); state.checking.remove(descriptor.id) }
                            (opened as? Closeable)?.close()
                        }
                    }
                }
            }
        }
    }
    return state
}
