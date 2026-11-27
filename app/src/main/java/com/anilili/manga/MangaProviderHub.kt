package com.anilili.manga

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anilili.ui.components.*
import com.anilili.ui.adaptive.LocalAppDeviceProfile
import java.util.Locale
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class MangaProviderPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("manga_provider_preferences", Context.MODE_PRIVATE)
    var disabled by mutableStateOf(prefs.getStringSet("disabled", emptySet()).orEmpty().toSet())
        private set
    var enabled by mutableStateOf(prefs.getStringSet("enabled", emptySet()).orEmpty().toSet())
        private set
    var pinned by mutableStateOf(prefs.getStringSet("pinned", emptySet()).orEmpty().toSet())
        private set
    fun isEnabled(source: MangaSource): Boolean = source.available && source.id !in disabled &&
        (source.id in enabled || preferredMangaLanguage(source.languageTag))
    fun setEnabled(sources: Collection<MangaSource>, value: Boolean) {
        val ids = sources.filter { it.available }.map { it.id }.toSet()
        disabled = if (value) disabled - ids else disabled + ids
        enabled = if (value) enabled + ids else enabled - ids
        prefs.edit().putStringSet("disabled", disabled).putStringSet("enabled", enabled).apply()
    }
    var presets by mutableStateOf(readPresets())
        private set
    private fun readPresets(): Map<String, Set<String>> = prefs.all.filterKeys { it.startsWith("preset:") }.mapNotNull { (key, value) ->
        (value as? Set<*>)?.filterIsInstance<String>()?.toSet()?.let { key.removePrefix("preset:") to it }
    }.toMap()
    fun savePreset(name: String, sources: List<MangaSource>) {
        val clean = name.trim().take(50)
        if (clean.isEmpty()) return
        val ids = sources.filter { isEnabled(it) }.map { it.id }.toSet()
        prefs.edit().putStringSet("preset:" + clean, ids).apply()
        presets = presets + (clean to ids)
    }
    fun applyPreset(name: String, sources: List<MangaSource>) {
        val ids = presets[name] ?: return
        enabled = ids.intersect(sources.map { it.id }.toSet())
        disabled = sources.map { it.id }.toSet() - enabled
        prefs.edit().putStringSet("enabled", enabled).putStringSet("disabled", disabled).apply()
    }
    fun removePreset(name: String) {
        prefs.edit().remove("preset:" + name).apply()
        presets = presets - name
    }
    fun pin(source: MangaSource) {
        pinned = if (source.id in pinned) pinned - source.id else pinned + source.id
        prefs.edit().putStringSet("pinned", pinned).apply()
    }
}

private enum class ProviderFilter(val label: String) { ALL("All"), ENABLED("Enabled"), DISABLED("Disabled") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MangaProviderHub(
    sources: List<MangaSource>, prefs: MangaProviderPreferences, healthStore: MangaSourceHealthStore, health: Map<String, SourceHealth>, selected: String?,
    onDismiss: () -> Unit, onSelect: (MangaSource) -> Unit, onInfo: () -> Unit,
) {
    var manage by rememberSaveable { mutableStateOf(false) }
    var settingsId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("All") }
    var category by rememberSaveable { mutableStateOf("All") }
    var filter by rememberSaveable { mutableStateOf(ProviderFilter.ALL) }
    var bulk by remember { mutableStateOf<Boolean?>(null) }
    var presetsMenu by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    val settings = sources.firstOrNull { it.id == settingsId }
    val context = LocalContext.current
    val enabledCount = sources.count { prefs.isEnabled(it) }
    val matching = sources.filter {
        val tag = Locale.forLanguageTag(it.languageTag.replace('_', '-')).language
        (language == "All" || tag == language) && (category == "All" || it.sourceCategory == category) &&
            (query.isBlank() || it.displayName.contains(query.trim(), true) || it.languageLabel().contains(query.trim(), true))
    }
    val filtered = matching.filter {
        if (!manage) prefs.isEnabled(it) && sourceReady(health[it.id] ?: SourceHealth.UNTESTED)
        else when (filter) { ProviderFilter.ALL -> true; ProviderFilter.ENABLED -> prefs.isEnabled(it); ProviderFilter.DISABLED -> !prefs.isEnabled(it) }
    }.sortedWith(compareBy<MangaSource> { it.id !in prefs.pinned }.thenBy { sourceHealthRank(health[it.id] ?: SourceHealth.UNTESTED) }.thenBy { it.displayName.lowercase(Locale.ROOT) })
    val back: () -> Unit = { if (settingsId != null) settingsId = null else if (manage) { manage = false; query = ""; category = "All"; language = "All" } else onDismiss() }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { back() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = LocalAppDeviceProfile.current.pagePadding), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
                    Text(settings?.displayName ?: if (manage) "Manage providers" else "Providers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (settings == null) {
                        IconButton(onClick = { if (manage) { manage = false; query = ""; language = "All"; category = "All" } else manage = true }) { Icon(if (manage) Icons.Default.Check else Icons.Default.Settings, if (manage) "Done" else "Manage providers") }
                        IconButton(onClick = { presetsMenu = true }) { Icon(Icons.Default.CollectionsBookmark, "Provider presets") }
                        IconButton(onClick = onInfo) { Icon(Icons.Default.Info, "Sources and credits") }
                    }
                }
                if (settings != null) {
                    ProviderSettings(settings, prefs, healthStore, health[settings.id] ?: SourceHealth.UNTESTED, context)
                } else {
                    Text("$enabledCount of ${sources.size} enabled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PageSearchField(query, { query = it }, "Search providers", "Clear search")
                    if (manage) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProviderFilter.entries.forEach { value ->
                                FilterChip(selected = value == filter, onClick = { filter = value }, label = { Text(value.label) }, modifier = Modifier.weight(1f), shape = CircleShape)
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("All") + sources.map { Locale.forLanguageTag(it.languageTag.replace('_', '-')).language }.filter { it.isNotBlank() }.distinct().sorted()) { code ->
                            FilterChip(selected = language == code, onClick = { language = code }, label = { Text(if (code == "All") "Languages" else code.uppercase(Locale.ROOT)) }, shape = CircleShape)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("All") + sources.map { it.sourceCategory }.distinct().sorted()) { value ->
                            FilterChip(selected = category == value, onClick = { category = value }, label = { Text(if (value == "All") "All categories" else value) }, shape = CircleShape)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${filtered.size} shown", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        if (manage) {
                            TextButton(onClick = { bulk = true }, enabled = filtered.isNotEmpty()) { Text("Enable shown") }
                            TextButton(onClick = { bulk = false }, enabled = filtered.isNotEmpty()) { Text("Disable shown") }
                        } else TextButton(onClick = { manage = true }) { Text("Manage") }
                    }
                    if (filtered.isEmpty()) {
                        Text(if (manage) "No matching providers." else "No enabled providers match. Open Manage to enable more.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
                    }
                    if (manage) LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(filtered, key = { it.id }) { source ->
                            Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).clickable { settingsId = source.id }.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProviderMonogram(source, Modifier.size(42.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(source.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(source.languageLabel() + " · " + source.sourceCategory, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text((health[source.id] ?: SourceHealth.UNTESTED).label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { settingsId = source.id }) { Icon(Icons.Default.MoreVert, "Provider settings") }
                                Switch(checked = prefs.isEnabled(source), onCheckedChange = { prefs.setEnabled(listOf(source), it) })
                            }
                        }
                    } else LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.id }) { source ->
                            val active = source.id == selected
                            Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                                .then(if (active) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier)
                                .clickable { onSelect(source) }.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                ProviderMonogram(source, Modifier.size(44.dp))
                                Text(source.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (active) "Selected" else (health[source.id] ?: SourceHealth.UNTESTED).label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text("Availability is checked on this device. Known broken and restricted content categories are excluded.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
        if (presetsMenu) AlertDialog(onDismissRequest = { presetsMenu = false }, title = { Text("Provider presets") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Save your currently enabled providers as a reusable preset.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(presetName, { presetName = it.take(50) }, label = { Text("Preset name") }, singleLine = true)
                TextButton(onClick = { prefs.savePreset(presetName, sources); presetName = "" }, enabled = presetName.isNotBlank()) { Text("Save current selection") }
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(prefs.presets.keys.sorted()) { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { prefs.applyPreset(name, sources); presetsMenu = false }, modifier = Modifier.weight(1f)) { Text(name + " · " + prefs.presets[name].orEmpty().size) }
                            IconButton(onClick = { prefs.removePreset(name) }) { Icon(Icons.Default.DeleteOutline, "Delete preset") }
                        }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { presetsMenu = false }) { Text("Done") } })
        bulk?.let { value ->
            AlertDialog(onDismissRequest = { bulk = null }, title = { Text(if (value) "Enable providers?" else "Disable providers?") },
                text = { Text("Apply to ${filtered.size} providers currently shown. Your reading history is kept.") },
                confirmButton = { TextButton(onClick = { prefs.setEnabled(filtered, value); bulk = null }) { Text("Apply") } },
                dismissButton = { TextButton(onClick = { bulk = null }) { Text("Cancel") } })
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

@Composable
private fun ProviderSettings(source: MangaSource, prefs: MangaProviderPreferences, healthStore: MangaSourceHealthStore, health: SourceHealth, context: Context) {
    var message by remember(source.id) { mutableStateOf<String?>(null) }
    var website by remember(source.id) { mutableStateOf<String?>(null) }
    var revision by remember(source.id) { mutableIntStateOf(0) }
    var testing by remember(source.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(source.id, revision) { website = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { source.websiteUrl() } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(20.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderMonogram(source, Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text("Enable provider", style = MaterialTheme.typography.titleMedium)
                    Text(source.languageLabel() + " · " + source.sourceCategory, style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = prefs.isEnabled(source), onCheckedChange = { prefs.setEnabled(listOf(source), it) })
            }
        }
        item {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Text(health.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            website?.let { Text("Domain: " + (it.toHttpUrlOrNull()?.host ?: it), style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            OutlinedButton(enabled = !testing && sourceReady(health), onClick = {
                testing = true
                scope.launch {
                    try {
                        val result = probeMangaSource(source)
                        healthStore.record(source.id, result.health)
                        message = result.health.label + " · " + result.stage
                    } finally { testing = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (testing) "Checking catalog, chapters and image…" else "Test provider") }
        }
        if (source is NativeConfigurableProvider) item {
            NativeProviderSettings(source) { revision++; healthStore.reset(source.id) }
        }
        item {
            Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(18.dp)).clickable { prefs.pin(source) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (source.id in prefs.pinned) "Unpin provider" else "Pin provider", modifier = Modifier.weight(1f))
                Icon(if (source.id in prefs.pinned) Icons.Default.Star else Icons.Default.StarBorder, null)
            }
        }
        website?.let { url ->
            item {
                Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(18.dp)).clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { message = "No browser available." }
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Open provider website", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.OpenInNew, null)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(18.dp)).clickable {
                    url.toHttpUrlOrNull()?.host?.let { MangaNetwork.clearDomain(it) }; message = "Provider session cookies cleared."
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Clear session cookies"); Text("May sign you out of this provider.", style = MaterialTheme.typography.labelSmall) }
                    Icon(Icons.Default.DeleteOutline, null)
                }
            }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}
