package com.anilili.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

internal fun MangaSource.languageKey(): String = languageTag.trim().replace('_','-').substringBefore('-').lowercase(Locale.ROOT).let { if (it in setOf("und", "mul")) "" else it }
internal fun languageName(code: String): String = if (code.isBlank()) "Multilingual" else Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }

/** Explicit language selections never include other or unknown-language providers. */
internal fun providerLanguageMatches(key: String, filter: String, preferred: Set<String>): Boolean = when (filter) {
    "all" -> true
    "preferred" -> key in preferred
    "multilingual" -> key.isBlank()
    else -> key == filter
}

@Composable
internal fun MangaProviderDirectory(state: MangaProviderState, current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("preferred") }
    var editLanguages by remember { mutableStateOf(false) }
    val sources = state.sources.values.toList()
    val languages = sources.map { it.languageKey() }.filter { it.isNotBlank() }.distinct().sortedBy(::languageName)
    val visible = sources.filter { source ->
        val key = source.languageKey()
        val matchesLanguage = if (filter == "pinned") source.id in state.pinned else providerLanguageMatches(key, filter, state.languages)
        matchesLanguage && (source.displayName.contains(query, true) || source.languageLabel().contains(query, true))
    }.sortedWith(compareBy<MangaSource> { it.id !in state.pinned }.thenBy { providerRank(state.status[it.id]) }.thenBy { it.displayName.lowercase(Locale.ROOT) })
    val pinned = visible.filter { it.id in state.pinned }
    val groups = visible.filter { it.id !in state.pinned }.groupBy { it.languageKey() }.entries.sortedBy { languageName(it.key) }
    if (editLanguages) AlertDialog(
        onDismissRequest = { editLanguages = false },
        title = { Text("Preferred reading languages") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(languages, key = { it }) { code ->
                    Row(Modifier.fillMaxWidth().clickable { state.toggleLanguage(code) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(code in state.languages, { state.toggleLanguage(code) })
                        Text(languageName(code))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { editLanguages = false }) { Text("Done") } }
    )
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Providers") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it.take(80) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Find provider") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(24.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("preferred" to "Preferred", "all" to "All", "pinned" to "Pinned", "multilingual" to "Multilingual") + languages.map { it to languageName(it) }, key = { it.first }) { option ->
                    FilterChip(selected = filter == option.first, onClick = { filter = option.first }, label = { Text(option.second) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.discovering) "Loading providers…" else "${visible.size} of ${sources.size} providers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { editLanguages = true }) { Text("Languages") }
            }
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (query.isBlank()) {
                    item("discovery") { DirectoryRow("AniList", "Discover manga", current == MangaDiscoverySource.ID, null, null) { onSelect(MangaDiscoverySource.ID) } }
                    item("local") { DirectoryRow("Local library", "Imported comics", current == LocalMangaSource.ID, null, null) { onSelect(LocalMangaSource.ID) } }
                }
                if (pinned.isNotEmpty()) {
                    item("pinned-heading") { DirectoryHeading("Pinned", pinned.size) }
                    items(pinned, key = { it.id }) { source -> ProviderDirectoryRow(source, state, current, onSelect) }
                }
                groups.forEach { (language, entries) ->
                    item("language:" + language) { DirectoryHeading(languageName(language), entries.size) }
                    items(entries, key = { it.id }) { source -> ProviderDirectoryRow(source, state, current, onSelect) }
                }
                if (!state.discovering && visible.isEmpty()) item("empty") { Text("No matching providers", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun DirectoryHeading(label: String, count: Int) {
    Text("$label · $count", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun ProviderDirectoryRow(source: MangaSource, state: MangaProviderState, current: String, onSelect: (String) -> Unit) {
    val status = when (state.status[source.id]) {
        "READY" -> "Previously read"
        "CATALOGUE" -> "Previously used"
        null -> null
        else -> "Retry"
    }
    DirectoryRow(source.displayName, listOfNotNull(source.languageLabel(), status).joinToString(" · "), source.id == current, source.id in state.pinned, { state.togglePin(source.id) }) { onSelect(source.id) }
}

@Composable
private fun DirectoryRow(name: String, subtitle: String, selected: Boolean, pinned: Boolean?, onPin: (() -> Unit)?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .32f)
    ).clickable(onClick = onClick).padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(Locale.ROOT), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onPin != null) IconButton(onClick = onPin) {
            Icon(if (pinned == true) Icons.Default.Star else Icons.Default.StarBorder, if (pinned == true) "Unpin provider" else "Pin provider", tint = if (pinned == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        else if (onPin == null) Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp))
    }
}
