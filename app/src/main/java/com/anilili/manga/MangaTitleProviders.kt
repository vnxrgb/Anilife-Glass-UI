package com.anilili.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.anilili.data.profiles.Profiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import java.util.Locale

internal fun normalizedMangaName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

internal fun titleMatches(reference: MangaTitle, candidate: MangaTitle): Boolean {
    val names = (listOf(reference.title) + reference.alternativeTitles).map(::normalizedMangaName).filter { it.isNotBlank() }.toSet()
    if ((listOf(candidate.title) + candidate.alternativeTitles).map(::normalizedMangaName).none { it in names }) return false
    if (reference.year != null && candidate.year != null && reference.year != candidate.year) return false
    if (reference.kind != MangaKind.OTHER && candidate.kind != MangaKind.OTHER && reference.kind != candidate.kind) return false
    if (reference.authors.isNotEmpty() && candidate.authors.isNotEmpty() &&
        reference.authors.map(::normalizedMangaName).intersect(candidate.authors.map(::normalizedMangaName).toSet()).isEmpty()) return false
    return true
}

data class MangaTitleMatch(val title: MangaTitle, val count: Int)

internal suspend fun matchProviderTitle(source: MangaSource, reference: MangaTitle): MangaTitleMatch? {
    val queries = (listOf(reference.title) + if (MangaProviderPolicy.isCurated(source.id)) reference.alternativeTitles else emptyList())
        .filter { it.isNotBlank() }.distinctBy(::normalizedMangaName).take(2)
    var candidate: MangaTitle? = null
    for (query in queries) {
        val candidates = source.search(query, 20).filter { titleMatches(reference, it) }.distinctBy { it.id }
        if (candidates.size > 1) return null
        if (candidates.size == 1) { candidate = candidates.single(); break }
    }
    val selected = candidate ?: return null
    val prepared = source.prepareTitle(selected)
    if (!titleMatches(reference, prepared)) return null
    val chapters = source.chapters(prepared.id, "und")
    return if (chapters.isEmpty()) null else MangaTitleMatch(prepared, chapters.size)
}

private data class TitleProviderMatch(val source: MangaSource, val title: MangaTitle, val count: Int, val cached: Boolean = false)

internal const val TITLE_PROVIDER_BATCH = 100
internal const val TITLE_PROVIDER_CONCURRENCY = 4

@Composable
internal fun MangaTitleProviders(title: MangaTitle, onChoose: (MangaTitle) -> Unit) {
    val state = LocalMangaProviderState.current
    val context = LocalContext.current
    val profile by Profiles.activeId.collectAsState()
    val cache = remember(context, profile) { MangaTitleMatchCache(context.getSharedPreferences("manga_title_matches_" + (profile ?: "default"), android.content.Context.MODE_PRIVATE)) }
    var filter by remember(title.id) { mutableStateOf("preferred") }
    var batch by remember(title.id, filter, state.languages) { mutableIntStateOf(0) }
    var refresh by remember(title.id, filter, state.languages) { mutableIntStateOf(0) }
    var matches by remember(title.id, filter, state.languages) { mutableStateOf<List<TitleProviderMatch>>(emptyList()) }
    var checked by remember(title.id, filter, state.languages) { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember(title.id, filter, state.languages) { mutableStateOf(false) }
    val all = state.sources.values.toList()
    val locales = all.map { it.languageKey() }.filter { it.isNotBlank() }.distinct().sortedBy(::languageName)
    val eligible = MangaProviderPolicy.order(all.filter { providerLanguageMatches(it.languageKey(), filter, state.languages) },
        state.pinned, state.status.toMap(), cache.last(title))
    // Only a curated/pinned/previously-read shortlist is searched automatically.
    // Larger lookups are explicit and keep the previous 100-provider batch cap.
    LaunchedEffect(title.sourceId, title.id, filter, batch, refresh, state.discovering, all.size, state.languages) {
        if (state.discovering) return@LaunchedEffect
        busy = true
        try {
            if (batch == 0) {
                checked = emptySet()
                matches = emptyList()
                if (refresh == 0) {
                    for (source in eligible) {
                        val revision = state.revisions[source.id] ?: continue
                        val saved = cache.get(title, source, revision) ?: continue
                        checked = checked + source.id
                        matches = matches + TitleProviderMatch(source, saved.title, saved.count, cached = true)
                    }
                }
            }
            val remaining = if (batch == 0) MangaProviderPolicy.primary(eligible, state.pinned, state.status.toMap(), cache.last(title))
                .filter { it.id !in checked }
                else eligible.filter { it.id !in checked }.take(TITLE_PROVIDER_BATCH)
            for (group in remaining.chunked(TITLE_PROVIDER_CONCURRENCY)) {
                coroutineScope {
                    group.mapIndexed { index, source ->
                        async {
                            delay(index * 350L)
                            val match = try {
                                withTimeoutOrNull(20_000L) { source.findTitleMatch(title) }
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { null }
                            catch (_: LinkageError) { null }
                            checked = checked + source.id
                            if (match != null) {
                                val revision = state.revisions[source.id]
                                if (revision != null) cache.put(title, source, revision, match)
                                matches = matches.filterNot { it.source.id == source.id } + TitleProviderMatch(source, match.title, match.count)
                            } else if (refresh > 0) cache.forget(title, source)
                        }
                    }.awaitAll()
                }
                delay(350L)
            }
        } finally { busy = false }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Reading providers", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("preferred" to "Preferred", "all" to "All languages", "multilingual" to "Multilingual") + locales.map { it to languageName(it) }, key = { it.first }) { option ->
                FilterChip(selected = filter == option.first, onClick = { filter = option.first }, label = { Text(option.second) })
            }
        }
        if (busy || state.discovering) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Preferred sources first · search other sources only when needed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        matches.sortedWith(compareBy<TitleProviderMatch> { it.source.id != cache.last(title) }.thenBy { it.source.id !in state.pinned }.thenBy { !MangaProviderPolicy.isCurated(it.source.id) }.thenBy { it.source.languageLabel() }.thenBy { it.source.displayName }).forEach { match ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                Row(Modifier.fillMaxWidth().clickable { cache.choose(title, match.source); onChoose(match.title) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(match.source.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("${match.source.languageLabel()} · ${match.count} chapter entries" + (if (match.title.unrated) " · Unrated" else "") + (if (match.cached) " · Previously found" else ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, "View chapters")
                }
            }
        }
        if (!busy && !state.discovering && matches.isEmpty()) Text(if (checked.isEmpty()) "No preferred sources for this language. Search other providers below." else "No matching chapter lists found in the sources checked.", style = MaterialTheme.typography.bodySmall)
        if (!busy && checked.size < eligible.size) OutlinedButton(onClick = { batch++ }, modifier = Modifier.fillMaxWidth()) { Text("Search next ${minOf(TITLE_PROVIDER_BATCH, eligible.size - checked.size)} providers") }
        if (!busy && matches.any { it.cached }) TextButton(onClick = { batch = 0; refresh++ }) { Text("Refresh preferred matches") }
        Text("${checked.size} of ${eligible.size} matching-language providers checked or remembered", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
