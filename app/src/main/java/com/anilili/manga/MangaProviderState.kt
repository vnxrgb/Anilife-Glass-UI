package com.anilili.manga

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.anilili.data.profiles.Profiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import java.io.Closeable

internal class MangaProviderState {
    val sources = mutableStateMapOf<String, MangaSource>()
    val status = mutableStateMapOf<String, String>()
    val diagnostics = mutableStateMapOf<String, ProviderDiagnostic>()
    var selectedId by mutableStateOf(REVIEWED_SOURCE_ID)
    var discovering by mutableStateOf(true)
    var generation = 0L
}
internal val LocalMangaProviderState = staticCompositionLocalOf { MangaProviderState() }
internal val LocalMangaRegistry = staticCompositionLocalOf { MangaSources(emptyList()) }

internal fun providerRank(status: String?): Int = when (status) {
    "READY" -> 0
    "CATALOGUE" -> 1
    null -> 2
    else -> 3
}

/** All eligible parser descriptors are available immediately after offline discovery.
 * No exhaustive scanning, timer, or background provider traffic. Adapters are lazy. */
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
    LaunchedEffect(state, enabled) {
        val generation = ++state.generation
        state.sources.clear(); state.status.clear(); state.diagnostics.clear()
        state.discovering = enabled
        if (!enabled) return@LaunchedEffect
        val opened = mutableListOf<MangaSource>()
        try {
            val descriptors = reviewedReaderProviders(context).distinctBy { it.id }
            for (descriptor in descriptors) {
                health.read(descriptor.id, descriptor.revision)?.let { state.status[descriptor.id] = it.reason }
                val source = descriptor.create { status, diagnostic ->
                    if (state.generation == generation) {
                        state.status[descriptor.id] = status
                        if (diagnostic == null) state.diagnostics.remove(descriptor.id)
                        else state.diagnostics[descriptor.id] = diagnostic
                    }
                }
                opened.add(source)
                state.sources[descriptor.id] = source
            }
            state.discovering = false
            awaitCancellation()
        } catch (e: CancellationException) { throw e }
        finally {
            if (state.generation == generation) {
                state.discovering = false
                state.sources.clear()
            }
            opened.forEach { (it as? Closeable)?.close() }
        }
    }
    return state
}
