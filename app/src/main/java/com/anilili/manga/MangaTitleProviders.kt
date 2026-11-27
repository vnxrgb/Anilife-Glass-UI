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
    val candidates = source.search(reference.title, 20).filter { titleMatches(reference, it) }.distinctBy { it.id }
    val candidate = candidates.singleOrNull() ?: return null
    val prepared = source.prepareTitle(candidate)
    if (!titleMatches(reference, prepared)) return null
    val chapters = source.chapters(prepared.id, "und")
    return if (chapters.isEmpty()) null else MangaTitleMatch(prepared, chapters.size)
}

private data class TitleProviderMatch(val source: MangaSource, val title: MangaTitle, val count: Int)

internal const val TITLE_PROVIDER_BATCH = 100
internal const val TITLE_PROVIDER_CONCURRENCY = 4

@Composable
internal fun MangaTitleProviders(title: MangaTitle, onChoose: (MangaTitle) -> Unit) {
    val state = LocalMangaProviderState.current
    var filter by remember(title.id) { mutableStateOf("preferred") }
    var pages by remember(title.id, filter, state.languages) { mutableIntStateOf(1) }
    var matches by remember(title.id, filter, state.languages) { mutableStateOf<List<TitleProviderMatch>>(emptyList()) }
    var checked by remember(title.id, filter, state.languages) { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember(title.id, filter, state.languages) { mutableStateOf(false) }
    val all = state.sources.values.toList()
    val locales = all.map { it.languageKey() }.filter { it.isNotBlank() }.distinct().sortedBy(::languageName)
    val eligible = all.filter { source ->
        val key = source.languageKey()
        providerLanguageMatches(key, filter, state.languages)
    }.sortedWith(compareBy<MangaSource> { it.id !in state.pinned }.thenBy { providerRank(state.status[it.id]) }.thenBy { it.displayName })
    // Up to 100 selected-language candidates per user-requested batch, not 100 concurrent requests.
    // The current search is cancelled when the title, language filter or screen changes.
    LaunchedEffect(title.sourceId, title.id, filter, pages, state.discovering, all.size, state.languages) {
        if (state.discovering) return@LaunchedEffect
        busy = true
        try {
            val remaining = eligible.filter { it.id !in checked }.take(TITLE_PROVIDER_BATCH)
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
                            if (match != null) matches = matches + TitleProviderMatch(source, match.title, match.count)
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
        Text("Matches with available chapter lists · selected provider language", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        matches.sortedWith(compareBy<TitleProviderMatch> { it.source.languageLabel() }.thenBy { it.source.id !in state.pinned }.thenBy { it.source.displayName }).forEach { match ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                Row(Modifier.fillMaxWidth().clickable { onChoose(match.title) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(match.source.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("${match.source.languageLabel()} · ${match.count} chapter entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, "View chapters")
                }
            }
        }
        if (!busy && !state.discovering && matches.isEmpty()) Text("No matching chapter lists found in the sources checked.", style = MaterialTheme.typography.bodySmall)
        if (!busy && checked.size < eligible.size) OutlinedButton(onClick = { pages++ }, modifier = Modifier.fillMaxWidth()) { Text("Search next ${minOf(TITLE_PROVIDER_BATCH, eligible.size - checked.size)} providers") }
        Text("${checked.size} of ${eligible.size} matching-language providers checked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
