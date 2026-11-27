package com.anilili.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anilili.ui.components.*
import com.anilili.ui.adaptive.LocalAppDeviceProfile
import java.util.Locale

/** Presentation-only picker. A future provider engine supplies the available sources. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MangaProviderPicker(
    sources: List<MangaSource>, health: Map<String, SourceHealth>, selected: String?,
    onDismiss: () -> Unit, onSelect: (MangaSource) -> Unit, onInfo: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("All") }
    val available = sources.filter { it.available && sourceCanBrowse(health[it.id] ?: SourceHealth.UNTESTED) }
    val languages = available.map { it.languageLabel() }.distinct().sorted()
    val filtered = available.filter {
        (language == "All" || it.languageLabel() == language) &&
            (query.isBlank() || it.displayName.contains(query.trim(), true))
    }.sortedBy { it.displayName.lowercase(Locale.ROOT) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = LocalAppDeviceProfile.current.pagePadding), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Back") }
                    Text("Providers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onInfo) { Icon(Icons.Default.Info, "Sources and credits") }
                }
                PageSearchField(query, { query = it }, "Search providers", "Clear search")
                if (languages.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("All") + languages) { value ->
                        FilterChip(selected = language == value, onClick = { language = value }, label = { Text(value) }, shape = CircleShape)
                    }
                }
                if (filtered.isEmpty()) Text("No available providers match.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
                LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { source ->
                        val active = source.id == selected
                        Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                            .then(if (active) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier)
                            .clickable { onSelect(source) }.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            ProviderMonogram(source, Modifier.size(44.dp))
                            Text(source.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (active) "Selected" else source.languageLabel(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderMonogram(source: MangaSource, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val pairs = listOf(colors.primaryContainer to colors.onPrimaryContainer, colors.secondaryContainer to colors.onSecondaryContainer, colors.tertiaryContainer to colors.onTertiaryContainer)
    val pair = pairs[(source.id.hashCode() and Int.MAX_VALUE) % pairs.size]
    Box(modifier.clip(RoundedCornerShape(13.dp)).background(pair.first), contentAlignment = Alignment.Center) {
        Text(source.displayName.take(1).uppercase(Locale.ROOT), color = pair.second, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
