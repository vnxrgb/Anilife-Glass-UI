package com.anilili.ui.journal

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.anilili.data.journal.JournalEntry
import com.anilili.data.journal.JournalStore
import com.anilili.ui.components.glassCard
import kotlin.math.roundToInt

data class JournalContext(val animeId: Int? = null, val title: String = "", val episode: String = "", val fullscreen: Boolean = false)
val LocalJournalReport = compositionLocalOf<(JournalContext) -> Unit> { {} }

@Composable
fun JournalOverlay(visible: Boolean, anime: JournalContext, bottomSpace: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    val entries by JournalStore.entries.collectAsState()
    val stored = remember { JournalStore.position() }
    var fx by remember { mutableStateOf(stored.first.coerceIn(0f, 1f)) }
    var fy by remember { mutableStateOf(stored.second.coerceIn(0f, 1f)) }
    var dragging by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(JournalEntry()) }
    var library by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(true) }
    var exportText by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val success = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(exportText.toByteArray(Charsets.UTF_8)) } ?: error("No output")
            }.isSuccess
            Toast.makeText(context, if (success) "Journal exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
    fun edit(next: JournalEntry) {
        draft = next.copy(updatedAt = System.currentTimeMillis())
        saved = JournalStore.save(draft)
    }
    fun share(text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, "Share journal"))
    }
    fun begin() {
        val found = if (anime.animeId != null) JournalStore.find(anime.animeId, anime.episode) else null
        draft = found ?: JournalEntry(animeId = anime.animeId, animeTitle = anime.title, episode = anime.episode,
            notes = if (anime.animeId != null && anime.episode.isNotBlank()) JournalStore.episodeNote(anime.animeId, anime.episode) else "")
        library = anime.animeId == null
        saved = true
        open = true
    }
    fun close() { if (!library && draft.hasContent()) edit(draft); if (saved) open = false }
    LaunchedEffect(visible) { if (!visible) { open = false; dragging = false } }
    BackHandler(visible && open) { close() }
    val expansion by animateFloatAsState(if (open && visible) 1f else 0f, spring(dampingRatio = .86f, stiffness = 370f), label = "journal expansion")
    if (!visible) return
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = bottomSpace).zIndex(20f)) {
        val density = LocalDensity.current
        val maxX = with(density) { (maxWidth - 58.dp).toPx().coerceAtLeast(1f) }
        val maxY = with(density) { (maxHeight - 58.dp).toPx().coerceAtLeast(1f) }
        val animatedX by animateFloatAsState(fx, spring(dampingRatio = .85f, stiffness = 450f), label = "journal dock")
        if (expansion < .98f) {
            Box(Modifier.offset { IntOffset(((if (dragging) fx else animatedX) * maxX).roundToInt(), (fy * maxY).roundToInt()) }
                .size(54.dp).graphicsLayer { alpha = (1f - expansion).coerceIn(0f, 1f); scaleX = 1f - expansion * .2f; scaleY = scaleX }
                .glassCard(CircleShape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(onDragStart = { dragging = true }, onDragEnd = {
                        dragging = false; fx = if (fx < .5f) 0f else 1f; JournalStore.position(fx, fy)
                    }, onDragCancel = { dragging = false; JournalStore.position(fx, fy) }) { change, delta ->
                        change.consume(); fx = (fx + delta.x / maxX).coerceIn(0f, 1f); fy = (fy + delta.y / maxY).coerceIn(0f, 1f)
                    }
                }.clickable(enabled = !open, onClickLabel = "Open Anime Journal") { begin() },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Edit, contentDescription = "Anime Journal", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (expansion > .01f) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .42f * expansion.coerceIn(0f,1f)))
                .clickable(enabled = open, onClickLabel = "Close journal") { close() })
            Column(Modifier.align(Alignment.Center).padding(12.dp).widthIn(max = 520.dp).fillMaxWidth().fillMaxHeight(.94f)
                .graphicsLayer {
                    val t = expansion.coerceIn(0f,1f)
                    scaleX = .22f + .78f*t; scaleY = scaleX; alpha = t
                    translationX = (fx-.5f)*maxX*(1f-t); translationY = (fy-.5f)*maxY*(1f-t)
                }.glassCard(RoundedCornerShape(28.dp)).clickable(enabled = open) {}.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Anime Journal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { close() }) { Text("Close") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (!library && draft.hasContent()) edit(draft); library = true }) { Text("My journal") }
                    TextButton(onClick = { draft = JournalEntry(animeId=anime.animeId, animeTitle=anime.title, episode=anime.episode); library = false; saved = true }) { Text("New entry") }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (library) {
                        OutlinedTextField(query, { query = it }, label = { Text("Search journal") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        val filtered = entries.filter { it.asText().contains(query, ignoreCase = true) }
                        if (filtered.isEmpty()) Text("No entries yet. Start a new note.")
                        filtered.forEach { entry ->
                            Column(Modifier.fillMaxWidth().glassCard().clickable { draft = entry; saved = true; library = false }.padding(12.dp)) {
                                Text(entry.animeTitle.ifBlank { "Personal journal" }, style = MaterialTheme.typography.titleMedium)
                                Text(entry.notes.ifBlank { entry.review.ifBlank { entry.favoriteMoment } }.take(100), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        OutlinedTextField(draft.animeTitle, { edit(draft.copy(animeTitle=it.take(180))) }, label={ Text("Anime title") }, singleLine=true, modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(draft.episode, { edit(draft.copy(episode=it.take(40))) }, label={ Text("Episode (optional)") }, singleLine=true, modifier=Modifier.fillMaxWidth())
                        Text("Personal rating: " + if(draft.rating==0) "Unrated" else "${draft.rating}/10")
                        Slider(value=draft.rating.toFloat(), onValueChange={edit(draft.copy(rating=it.roundToInt()))}, valueRange=0f..10f, steps=9)
                        OutlinedTextField(draft.notes, { edit(draft.copy(notes=it.take(8000))) }, label={ Text("Your thoughts") }, minLines=3, modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(draft.review, { edit(draft.copy(review=it.take(8000))) }, label={ Text("Private review") }, minLines=3, modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(draft.favoriteMoment, { edit(draft.copy(favoriteMoment=it.take(4000))) }, label={ Text("Favorite moments / timestamps") }, minLines=2, modifier=Modifier.fillMaxWidth())
                    }
                }
                Text(if(saved) "Stored locally on this device" else "Could not save: storage limit or unreadable data", style=MaterialTheme.typography.labelSmall, color=if(saved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val text=if(library) entries.joinToString("\n----------------\n"){it.asText()} else draft.asText()
                    OutlinedButton(onClick={share(text)}, enabled=if(library) entries.isNotEmpty() else draft.hasContent(), modifier=Modifier.weight(1f)){Text("Share")}
                    Button(onClick={exportText=text; export.launch("AniLumos-Journal.txt")}, enabled=if(library) entries.isNotEmpty() else draft.hasContent(), modifier=Modifier.weight(1f)){Text("Export")}
                }
            }
        }
    }
}
