package com.anilili.manga

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anilili.ui.components.glassCard
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.SortOrder

internal interface NativeConfigurableProvider {
    fun configKeys(): List<ConfigKey<*>>
    fun sourceConfig(): NativeSourceConfig
    fun supportedSorts(): List<SortOrder>
    fun currentDomain(): String
    fun rawCatalogCount(): Int
}

/** Provider settings are read directly by the parser, not merely displayed in the UI. */
internal class NativeSourceConfig(context: Context?, source: String) : MangaSourceConfig {
    private val prefs = context?.getSharedPreferences("manga_parser_" + source.replace('/', '_'), Context.MODE_PRIVATE)
    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: ConfigKey<T>): T = when (key) {
        is ConfigKey.Domain -> prefs?.getString(key.key, null)?.takeIf { it in key.presetValues } ?: key.defaultValue
        is ConfigKey.UserAgent -> key.defaultValue
        is ConfigKey.SplitByTranslations -> prefs?.getBoolean(key.key, key.defaultValue) ?: key.defaultValue
        is ConfigKey.PreferredImageServer -> prefs?.getString(key.key, null)?.let { value -> key.presetValues.keys.firstOrNull { it.orEmpty() == value } } ?: key.defaultValue
        is ConfigKey.ShowSuspiciousContent -> false
        is ConfigKey.InterceptCloudflare -> false
        is ConfigKey.DisableUpdateChecking -> key.defaultValue
    } as T
    fun selectDomain(key: ConfigKey.Domain, domain: String) {
        require(domain in key.presetValues)
        prefs?.edit()?.putString(key.key, domain)?.apply()
    }
    fun split(key: ConfigKey.SplitByTranslations, value: Boolean) { prefs?.edit()?.putBoolean(key.key, value)?.apply() }
    fun imageServer(key: ConfigKey.PreferredImageServer, value: String?) {
        require(value in key.presetValues)
        prefs?.edit()?.putString(key.key, value.orEmpty())?.apply()
    }
    fun defaultSort(): SortOrder? = prefs?.getString("sort_order", null)?.let { value -> SortOrder.entries.firstOrNull { it.name == value } }
    fun sort(value: SortOrder?) { prefs?.edit()?.putString("sort_order", value?.name)?.apply() }
    fun reset() { prefs?.edit()?.clear()?.apply() }
}

@Composable
internal fun NativeProviderSettings(source: NativeConfigurableProvider, onChange: () -> Unit) {
    var revision by remember(source) { mutableIntStateOf(0) }
    val config = remember(source) { source.sourceConfig() }
    val keys = remember(source) { runCatching { source.configKeys() }.getOrDefault(emptyList()) }
    val sorts = remember(source) { runCatching { source.supportedSorts() }.getOrDefault(emptyList()) }
    val change = { revision++; onChange() }
    key(revision) {
        Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(18.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Parser configuration", style = MaterialTheme.typography.titleMedium)
            keys.forEach { key ->
                when (key) {
                    is ConfigKey.Domain -> if (key.presetValues.size > 1) ProviderChoice("Provider domain", config[key], key.presetValues.toList().map { it to it }) { config.selectDomain(key, it); change() }
                    is ConfigKey.SplitByTranslations -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Split by translations", Modifier.weight(1f)); Switch(checked = config[key], onCheckedChange = { config.split(key, it); change() })
                    }
                    is ConfigKey.PreferredImageServer -> ProviderChoice("Image server", config[key].orEmpty(), key.presetValues.map { (id, name) -> id.orEmpty() to (name ?: "Automatic") }) { id ->
                        config.imageServer(key, key.presetValues.keys.firstOrNull { it.orEmpty() == id }); change()
                    }
                    else -> Unit
                }
            }
            if (sorts.isNotEmpty()) ProviderChoice("Default browse order", config.defaultSort()?.name.orEmpty(), listOf("" to "Automatic") + sorts.map { it.name to it.name.lowercase().replace('_', ' ').replaceFirstChar { ch -> ch.uppercase() } }) { value ->
                config.sort(sorts.firstOrNull { it.name == value }); change()
            }
            TextButton(onClick = { config.reset(); change() }) { Text("Reset provider configuration") }
        }
    }
}

@Composable
private fun ProviderChoice(label: String, selected: String, values: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label + ": " + (values.firstOrNull { it.first == selected }?.second ?: "Automatic")) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) },
        text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(values.size) { index ->
                val (id, title) = values[index]
                TextButton(onClick = { onChange(id); open = false }, modifier = Modifier.fillMaxWidth()) { Text(if (id == selected) "✓ " + title else title) }
            }
        } }, confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } })
}
