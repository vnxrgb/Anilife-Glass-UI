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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val failureReasons = mutableStateMapOf<String, String>()
    var generation = 0L
    var discovering by mutableStateOf(false)
    var candidateCount by mutableIntStateOf(0)
    var checkedCount by mutableIntStateOf(0)
}
internal val LocalMangaProviderState = staticCompositionLocalOf { MangaProviderState() }
internal val LocalMangaRegistry = staticCompositionLocalOf { MangaSources(emptyList()) }

/** Offline inventory is separate from live availability. Queue at most two foreground checks
 * at once; verified sources are lightweight until selected, and failures cool down. */
@Composable
internal fun rememberMangaProviderState(enabled: Boolean): MangaProviderState {
    val profile by Profiles.activeId.collectAsState()
    val context = LocalContext.current
    val prefs = remember(context, profile) { context.getSharedPreferences("manga_provider_choice_" + (profile ?: "default"), android.content.Context.MODE_PRIVATE) }
    val state = remember(profile) { MangaProviderState().apply {
        selectedId = prefs.getString("selected", REVIEWED_SOURCE_ID) ?: REVIEWED_SOURCE_ID
    } }
    LaunchedEffect(state, state.selectedId) { prefs.edit().putString("selected", state.selectedId).apply() }
    val health = remember(context) { MangaProviderHealthCache(context.getSharedPreferences("manga_provider_health_v2", android.content.Context.MODE_PRIVATE)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state, enabled, lifecycle) {
        val generation = ++state.generation
        if (!enabled) { state.sources.clear(); state.checking.clear(); state.discovering = false; return@LaunchedEffect }
        state.discovering = true
        state.checkedCount = 0
        val descriptors = try { reviewedReaderProviders(context).distinctBy { it.id } }
            finally { if (state.generation == generation) state.discovering = false }
        state.candidateCount = descriptors.size
        val permits = Semaphore(2)
        val pace = Mutex()
        var nextStart = 0L
        coroutineScope {
            descriptors.forEach { descriptor ->
                launch {
                    var first = true
                    var opened: MangaSource? = null
                    try {
                        val cached = health.read(descriptor.id, descriptor.revision)
                        if (cached != null) {
                            state.checkedCount++; first = false
                            if (cached.ready) {
                                val verified = descriptor.cached()
                                opened = verified
                                state.sources[descriptor.id] = verified
                                state.unavailable.remove(descriptor.id); state.failureReasons.remove(descriptor.id); state.failures.remove(descriptor.id)
                                awaitCancellation()
                            } else {
                                state.unavailable[descriptor.id] = true; state.failureReasons[descriptor.id] = cached.reason
                                delay(cached.remainingMs)
                                state.failures.remove(descriptor.id)
                            }
                        }
                        while (true) {
                            val last = state.failures[descriptor.id]
                            val remaining = if (last == null) 0L else (900_000L - (SystemClock.elapsedRealtime() - last)).coerceAtLeast(0L)
                            val savedRemaining = health.read(descriptor.id, descriptor.revision)?.takeUnless { it.ready }?.remainingMs ?: 0L
                            val waitMs = maxOf(remaining, savedRemaining)
                            if (waitMs > 0L) delay(waitMs)
                            try {
                                opened = permits.withPermit {
                                    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
                                    pace.withLock {
                                        val wait = nextStart - SystemClock.elapsedRealtime()
                                        if (wait > 0L) delay(wait)
                                        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
                                        nextStart = SystemClock.elapsedRealtime() + 2000L
                                    }
                                    currentCoroutineContext().ensureActive()
                                    state.checking[descriptor.id] = true
                                    var candidate: MangaSource? = null
                                    try {
                                        withTimeoutOrNull(90_000L) {
                                            descriptor.open().also { candidate = it }
                                        }.also { if (it == null) (candidate as? Closeable)?.close() }
                                    } catch (e: Throwable) {
                                        (candidate as? Closeable)?.close()
                                        throw e
                                    } finally { if (state.generation == generation) state.checking.remove(descriptor.id) }
                                }
                                currentCoroutineContext().ensureActive()
                                if (state.generation != generation) return@launch
                                val verified = opened ?: error("Provider check timed out")
                                require(verified.id == descriptor.id)
                                state.sources[descriptor.id] = verified
                                state.unavailable.remove(descriptor.id)
                                state.failureReasons.remove(descriptor.id)
                                state.failures.remove(descriptor.id)
                                health.record(descriptor.id, descriptor.revision, true, "READY")
                                if (first) { state.checkedCount++; first = false }
                                awaitCancellation()
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) {
                                if (state.generation == generation) {
                                    state.failures[descriptor.id] = SystemClock.elapsedRealtime()
                                    state.unavailable[descriptor.id] = true
                                    val reason = providerFailureReason(e)
                                    state.failureReasons[descriptor.id] = reason
                                    health.record(descriptor.id, descriptor.revision, false, reason)
                                    if (first) { state.checkedCount++; first = false }
                                }
                            } catch (e: LinkageError) {
                                if (state.generation == generation) {
                                    state.unavailable[descriptor.id] = true
                                    if (first) { state.checkedCount++; first = false }
                                }
                                return@launch
                            } finally {
                                if (state.generation == generation) { state.sources.remove(descriptor.id); state.checking.remove(descriptor.id) }
                                (opened as? Closeable)?.close()
                                opened = null
                            }
                        }
                    } finally {
                        if (state.generation == generation) { state.sources.remove(descriptor.id); state.checking.remove(descriptor.id) }
                        (opened as? Closeable)?.close()
                    }
                }
            }
        }
    }
    return state
}
