package com.anilili.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anilili.data.MetadataDiagnostics
import com.anilili.data.MetadataProbe
import com.anilili.ui.dialogs.MidnightSettingRow
import com.anilili.ui.dialogs.MidnightSectionLabel
import com.anilili.ui.dialogs.MidnightHelper
import kotlinx.coroutines.launch

@Composable
internal fun MetadataDiagnosticsRow(title: String) {
    val last by MetadataDiagnostics.last.collectAsState()
    var open by remember { mutableStateOf(false) }
    MidnightSettingRow(title = title, description = "Test metadata sources and inspect the last played episode", onClick = { open = true })
    if (open) MetadataDiagnosticsDialog(onClose = { open = false })
}

@Composable
private fun MetadataDiagnosticsDialog(onClose: () -> Unit) {
    val last by MetadataDiagnostics.last.collectAsState()
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf(last?.anilistId?.toString() ?: "20") }
    var episode by remember { mutableStateOf(last?.episode?.toString() ?: "101") }
    var duration by remember { mutableStateOf(last?.durationSeconds?.toString() ?: "1440") }
    var results by remember { mutableStateOf(emptyMap<String, MetadataProbe>()) }
    var running by remember { mutableStateOf(false) }
    val valid = (id.toIntOrNull() ?: 0) > 0 && (episode.toIntOrNull() ?: 0) > 0 && (duration.toDoubleOrNull() ?: 0.0) in 1.0..86400.0
    fun run(provider: String?) {
        if (!valid || running) return
        val animeId = id.toInt(); val number = episode.toInt(); val seconds = duration.toDouble()
        running = true
        results = if (provider == null) emptyMap() else results - provider
        scope.launch {
            try { MetadataDiagnostics.test(animeId, number, seconds, provider) { results = results + (it.provider to it) } }
            finally { running = false }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Metadata Diagnostics") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MidnightSectionLabel("Last playback")
                last?.let {
                    MidnightHelper("AniList ${it.anilistId} · Episode ${it.episode}")
                    MidnightHelper("Intro: ${it.intro ?: "Unavailable / unknown"}")
                    MidnightHelper("Outro: ${it.outro ?: "Unavailable / unknown"}")
                    MidnightHelper("Recap: ${it.recap ?: "Unavailable / unknown"}")
                    MidnightHelper("Episode: ${it.classification ?: "Unknown"} · ${it.classificationSource ?: "No source"}")
                    MidnightSettingRow(title = "Use last episode", enabled = !running, onClick = {
                        id = it.anilistId.toString(); episode = it.episode.toString(); duration = it.durationSeconds.toString(); results = emptyMap()
                    })
                } ?: MidnightHelper("Play an episode first to see the actual metadata sources. The default test is Naruto.")
                MidnightSectionLabel("Live API tests")
                MidnightHelper("Direct read-only requests, bypassing the playback and HTTP disk caches. Results are for the entered episode, not proof of automatic fallback.")
                OutlinedTextField(id, { id = it.filter(Char::isDigit); results = emptyMap() }, label = { Text("AniList ID") }, singleLine = true, enabled = !running, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(episode, { episode = it.filter(Char::isDigit); results = emptyMap() }, label = { Text("Episode") }, singleLine = true, enabled = !running, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(duration, { duration = it.filter { c -> c.isDigit() || c == '.' }; results = emptyMap() }, label = { Text("Video duration (seconds)") }, singleLine = true, enabled = !running, modifier = Modifier.fillMaxWidth())
                MidnightSettingRow(title = "Test all APIs", description = if (running) "Testing…" else "Fresh requests to every service", enabled = valid && !running, busy = running, onClick = { run(null) })
                MetadataDiagnostics.providers.forEach { provider ->
                    val result = results[provider]
                    MidnightSettingRow(title = provider, description = result?.let { "${it.result} · ${it.detail}" } ?: "Not tested · Tap to test", enabled = valid && !running, onClick = { run(provider) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
