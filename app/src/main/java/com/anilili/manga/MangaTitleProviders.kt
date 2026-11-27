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

private data class TitleProviderMatch(val source: MangaSource, val title: MangaTitle, val count: Int)

@Composable
internal fun MangaTitleProviders(title: MangaTitle, onChoose: (MangaTitle) -> Unit) {
    val state = LocalMangaProviderState.current
    var filter by remember(title.id) { mutableStateOf("preferred") }
    var pages by remember(title.id, filter) { mutableIntStateOf(1) }
    var matches by remember(title.id, filter) { mutableStateOf<List<TitleProviderMatch>>(emptyList()) }
    var checked by remember(title.id, filter) { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember(title.id, filter) { mutableStateOf(false) }
    val all = state.sources.values.toList()
    val locales = all.map { it.languageKey() }.filter { it.isNotBlank() }.distinct().sortedBy(::languageName)
    val eligible = all.filter { source ->
        val key = source.languageKey()
        when (filter) { "preferred" -> state.languages.isEmpty() || key.isBlank() || key in state.languages; "all" -> true; else -> key == filter || key.isBlank() }
    }.sortedWith(compareBy<MangaSource> { it.id !in state.pinned }.thenBy { providerRank(state.status[it.id]) }.thenBy { it.displayName })
    // A first batch of four, then four at a time only when requested. No full-inventory lookup.
    LaunchedEffect(title.sourceId, title.id, filter, pages, state.discovering, all.size) {
        if (state.discovering) return@LaunchedEffect
        busy = true
        try {
            val remaining = eligible.filter { it.id !in checked }.take((pages * 4 - checked.size).coerceIn(0, 4))
            for (source in remaining) {
                val match = try {
                    withTimeoutOrNull(20_000L) {
                        val candidates = source.search(title.title, 20).filter { titleMatches(title, it) }.distinctBy { it.id }
                        val candidate = candidates.singleOrNull() ?: return@withTimeoutOrNull null
                        val prepared = source.prepareTitle(candidate)
                        if (!titleMatches(title, prepared)) return@withTimeoutOrNull null
                        val chapters = source.chapters(prepared.id, "und")
                        if (chapters.isEmpty()) null else TitleProviderMatch(source, prepared, chapters.size)
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                checked = checked + source.id
                if (match != null) matches = matches + match
                delay(350)
            }
        } finally { busy = false }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Reading providers", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("preferred" to "Preferred", "all" to "All languages") + locales.map { it to languageName(it) }, key = { it.first }) { option ->
                FilterChip(selected = filter == option.first, onClick = { filter = option.first }, label = { Text(option.second) })
            }
        }
        if (busy || state.discovering) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Matches with available chapter lists · provider language", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        matches.forEach { match ->
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
        if (!busy && checked.size < eligible.size) OutlinedButton(onClick = { pages++ }, modifier = Modifier.fillMaxWidth()) { Text("Search more providers") }
        Text("${checked.size} checked · no background scanning", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
