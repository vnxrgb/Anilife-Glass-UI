package com.anilili.ui.journal

import android.content.Intent
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.toArgb
import com.anilili.data.library.LibraryStore
import com.anilili.data.journal.journalEpisodeLabel
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.LocalContentColor
import com.anilili.ui.components.opticalGlass
import com.anilili.ui.theme.LocalThemeSpec
import com.anilili.data.settings.ThemeBackground
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlin.math.roundToInt

data class JournalContext(val animeId: Int? = null, val title: String = "", val episode: String = "", val fullscreen: Boolean = false, val artwork: String? = null)
val LocalJournalReport = compositionLocalOf<(JournalContext) -> Unit> { {} }
val LocalOpenJournal = compositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JournalOverlay(visible: Boolean, anime: JournalContext, bottomSpace: androidx.compose.ui.unit.Dp = 0.dp, openRequest: Int = 0) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val floatingEnabled by JournalStore.floatingEnabled.collectAsState()
    val minimized by JournalStore.minimized.collectAsState()
    val scope = rememberCoroutineScope()
    val history by LibraryStore.history.collectAsState()
    val watchlist by LibraryStore.watchlist.collectAsState()
    fun cover(entry: JournalEntry): String? = entry.artwork ?: history.firstOrNull { it.anilistId == entry.animeId }?.cover ?: watchlist.firstOrNull { it.anilistId == entry.animeId }?.cover
    val colors = MaterialTheme.colorScheme
    val amoled = LocalThemeSpec.current.background == ThemeBackground.AMOLED
    val light = colors.surface.luminance() > .5f
    // The editor must be opaque behind long-form text even when placed over bright anime artwork.
    // AMOLED uses the actual black canvas; raised controls use the app's near-black container roles.
    val editorSurface = if (amoled) Color.Black else colors.surface
    val fieldSurface = colors.surfaceContainerLow
    val rim = colors.onSurface.copy(alpha = if (light) .17f else .20f)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface,
        focusedContainerColor = fieldSurface, unfocusedContainerColor = fieldSurface,
        focusedBorderColor = colors.primary, unfocusedBorderColor = rim,
        focusedLabelColor = colors.primary, unfocusedLabelColor = colors.onSurfaceVariant,
        cursorColor = colors.primary,
    )
    val fieldShape = RoundedCornerShape(18.dp)
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
    var shareOpen by remember { mutableStateOf(false) }
    var includeNotes by remember { mutableStateOf(false) }
    var includeReview by remember { mutableStateOf(false) }
    var includeMoments by remember { mutableStateOf(false) }
    var includeRating by remember { mutableStateOf(true) }
    var includeEpisode by remember { mutableStateOf(true) }
    var includeArtwork by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }
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
        draft = found?.copy(artwork = found.artwork ?: anime.artwork, episode = journalEpisodeLabel(found.episode)) ?: JournalEntry(animeId = anime.animeId, animeTitle = anime.title, episode = journalEpisodeLabel(anime.episode), artwork = anime.artwork,
            notes = if (anime.animeId != null && anime.episode.isNotBlank()) JournalStore.episodeNote(anime.animeId, anime.episode) else "")
        library = anime.animeId == null
        saved = true
        open = true
    }
    var handledRequest by remember { mutableStateOf(0) }
    LaunchedEffect(openRequest, visible) {
        if (visible && openRequest != handledRequest) {
            handledRequest = openRequest
            begin()
            library = true
        }
    }
    fun close() { focus.clearFocus(); if (!library && draft.hasContent()) edit(draft); if (saved) open = false }
    LaunchedEffect(visible) { if (!visible) { open = false; dragging = false; shareOpen = false } }
    BackHandler(visible && open) { close() }
    val expansion by animateFloatAsState(if (open && visible) 1f else 0f, spring(dampingRatio = .86f, stiffness = 370f), label = "journal expansion")
    if (!visible) return
    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(bottom = bottomSpace).zIndex(20f)) {
        val density = LocalDensity.current
        val maxX = with(density) { (maxWidth - 58.dp).toPx().coerceAtLeast(1f) }
        val maxY = with(density) { (maxHeight - 58.dp).toPx().coerceAtLeast(1f) }
        val animatedX by animateFloatAsState(fx, spring(dampingRatio = .85f, stiffness = 450f), label = "journal dock")
        val dockProgress by animateFloatAsState(if (minimized) 1f else 0f, spring(dampingRatio = .86f, stiffness = 390f), label = "journal minimize")
        val dockT = dockProgress.coerceIn(0f, 1f)
        if (floatingEnabled && expansion < .98f) {
            Box(Modifier.offset { IntOffset((((if (dragging) fx else animatedX) * (1f-dockT) + dockT) * maxX).roundToInt(), ((fy * (1f-dockT) + dockT) * maxY).roundToInt()) }
                .size(54.dp).graphicsLayer { alpha = (1f - expansion).coerceIn(0f, 1f); scaleX = (1f - expansion * .2f) * (1f - .12f*dockT); scaleY = (1f - expansion * .2f) * (1f - .48f*dockT) }
                .opticalGlass(CircleShape, navigation = true)
                .border(.5.dp, rim, CircleShape)
                .pointerInput(maxX, maxY, minimized, open) {
                    if (minimized || open) return@pointerInput
                    detectDragGestures(onDragStart = { dragging = true }, onDragEnd = {
                        dragging = false; fx = if (fx < .5f) 0f else 1f; JournalStore.position(fx, fy)
                    }, onDragCancel = { dragging = false; JournalStore.position(fx, fy) }) { change, delta ->
                        change.consume(); fx = (fx + delta.x / maxX).coerceIn(0f, 1f); fy = (fy + delta.y / maxY).coerceIn(0f, 1f)
                    }
                }.combinedClickable(enabled = !open, onClickLabel = if (minimized) "Restore floating journal" else "Open Anime Journal",
                    onLongClickLabel = if (minimized) null else "Minimize journal",
                    onLongClick = if (minimized) null else ({ haptics.performHapticFeedback(HapticFeedbackType.LongPress); JournalStore.setMinimized(true) }),
                    onClick = { if (minimized) { JournalStore.setMinimized(false) } else begin() }),
                contentAlignment = Alignment.Center) {
                Icon(if (minimized) Icons.Default.KeyboardArrowUp else Icons.Default.Edit, contentDescription = if (minimized) "Restore Anime Journal" else "Anime Journal", tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer { scaleY = if (minimized) 1.5f else 1f })
            }
        }
        if (expansion > .01f) {
            Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = (if (light) .30f else .50f) * expansion.coerceIn(0f,1f)))
                .clickable(enabled = open, onClickLabel = "Close journal") { close() })
            Column(Modifier.align(Alignment.Center).padding(12.dp).widthIn(max = 520.dp).fillMaxWidth().fillMaxHeight(.94f)
                .graphicsLayer {
                    val t = expansion.coerceIn(0f,1f)
                    scaleX = .22f + .78f*t; scaleY = scaleX; alpha = t
                    translationX = (fx-.5f)*maxX*(1f-t); translationY = (fy-.5f)*maxY*(1f-t)
                }.clip(RoundedCornerShape(28.dp)).background(editorSurface).border(.75.dp, rim, RoundedCornerShape(28.dp)).clickable(enabled = open) {}.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Anime Journal", style = MaterialTheme.typography.titleLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
                    TextButton(onClick = { close() }, colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface)) { Text("Close") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { focus.clearFocus(); if (!library && draft.hasContent()) edit(draft); library = true }, modifier = Modifier.weight(1f)) { Text("My journal") }
                    FilledTonalButton(onClick = { focus.clearFocus(); draft = JournalEntry(animeId=anime.animeId, animeTitle=anime.title, episode=journalEpisodeLabel(anime.episode), artwork=anime.artwork); library = false; saved = true }, modifier = Modifier.weight(1f)) { Text("New entry") }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (library) {
                        OutlinedTextField(query, { query = it }, label = { Text("Search journal") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors, shape = fieldShape)
                        val filtered = entries.filter { it.asText().contains(query, ignoreCase = true) }
                        Text("${filtered.size} journal entries", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                        if (filtered.isEmpty()) Text("No entries yet. Start a new note.")
                        filtered.forEach { entry ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerLow).border(.5.dp, rim, RoundedCornerShape(18.dp)).clickable { draft = entry.copy(episode = journalEpisodeLabel(entry.episode)); saved = true; library = false }.padding(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(model = cover(entry), contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(46.dp, 66.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHighest))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(entry.animeTitle.ifBlank { "Personal journal" }, style = MaterialTheme.typography.titleMedium)
                                        Text(listOfNotNull(entry.episode.takeIf { it.isNotBlank() }?.let { "Episode ${journalEpisodeLabel(it)}" }, entry.rating.takeIf { it > 0 }?.let { "$it/10" }).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                        Text(entry.notes.ifBlank { entry.review.ifBlank { entry.favoriteMoment } }.take(100), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(draft.animeTitle, { edit(draft.copy(animeTitle=it.take(180))) }, label={ Text("Anime title") }, singleLine=true, modifier=Modifier.fillMaxWidth(), colors = fieldColors, shape = fieldShape)
                        OutlinedTextField(draft.episode, { edit(draft.copy(episode=it.take(40))) }, label={ Text("Episode (optional)") }, singleLine=true, modifier=Modifier.fillMaxWidth(), colors = fieldColors, shape = fieldShape)
                        Text("Personal rating: " + if(draft.rating==0) "Unrated" else "${draft.rating}/10")
                        Slider(value=draft.rating.toFloat(), onValueChange={edit(draft.copy(rating=it.roundToInt()))}, valueRange=0f..10f, steps=9, colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.surfaceContainerHighest))
                        OutlinedTextField(draft.notes, { edit(draft.copy(notes=it.take(8000))) }, label={ Text("Your thoughts") }, minLines=3, modifier=Modifier.fillMaxWidth(), colors = fieldColors, shape = fieldShape)
                        OutlinedTextField(draft.review, { edit(draft.copy(review=it.take(8000))) }, label={ Text("Private review") }, minLines=3, modifier=Modifier.fillMaxWidth(), colors = fieldColors, shape = fieldShape)
                        OutlinedTextField(draft.favoriteMoment, { edit(draft.copy(favoriteMoment=it.take(4000))) }, label={ Text("Favorite moments / timestamps") }, minLines=2, modifier=Modifier.fillMaxWidth(), colors = fieldColors, shape = fieldShape)
                    }
                }
                Text(if(saved) "Stored locally on this device" else "Could not save: storage limit or unreadable data", style=MaterialTheme.typography.labelSmall, color=if(saved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val text=if(library) entries.joinToString("\n----------------\n"){it.asText()} else draft.asText()
                    Button(onClick={focus.clearFocus(); includeNotes=false; includeReview=false; includeMoments=false; includeRating=true; includeEpisode=true; includeArtwork=true; shareOpen=true}, enabled=if(library) entries.isNotEmpty() else draft.hasContent(), modifier=Modifier.weight(1f)){Text("Share…")}
                    OutlinedButton(onClick={focus.clearFocus(); exportText=text; export.launch("AniLumos-Journal.txt")}, enabled=if(library) entries.isNotEmpty() else draft.hasContent(), modifier=Modifier.weight(1f)){Text("Export")}
                }
            }
        }
    }
}
    if (visible && open && shareOpen) {
        val selected = (if (library) entries else listOf(draft)).map { entry ->
            entry.copy(notes = if (includeNotes) entry.notes else "", review = if (includeReview) entry.review else "",
                favoriteMoment = if (includeMoments) entry.favoriteMoment else "", rating = if (includeRating) entry.rating else 0,
                episode = if (includeEpisode) journalEpisodeLabel(entry.episode) else "", artwork = if (includeArtwork) cover(entry) else null)
        }
        val accent = colors.primary.toArgb()
        AlertDialog(onDismissRequest = { if (!sharing) shareOpen = false },
            title = { Text("Share journal") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Choose what to include. Private writing is excluded by default.", style = MaterialTheme.typography.bodySmall)
                    listOf(Triple("Artwork", includeArtwork, { v: Boolean -> includeArtwork = v }),
                        Triple("Episode", includeEpisode, { v: Boolean -> includeEpisode = v }),
                        Triple("Rating", includeRating, { v: Boolean -> includeRating = v }),
                        Triple("Personal notes", includeNotes, { v: Boolean -> includeNotes = v }),
                        Triple("Private review", includeReview, { v: Boolean -> includeReview = v }),
                        Triple("Favorite moments / spoilers", includeMoments, { v: Boolean -> includeMoments = v })).forEach { (label, checked, update) ->
                        Row(Modifier.fillMaxWidth().clickable { update(!checked) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, onCheckedChange = update); Text(label, modifier = Modifier.weight(1f))
                        }
                    }
                    HorizontalDivider()
                    selected.take(3).forEach { item ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerLow).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (item.artwork != null) AsyncImage(model = item.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp, 70.dp).clip(RoundedCornerShape(8.dp)))
                            Text(item.asText(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (selected.size > 3) Text("Image shares use the first three entries. Text includes all selected entries.", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = { Button(enabled = !sharing && selected.isNotEmpty(), onClick = {
                sharing = true
                scope.launch {
                    try { shareJournalImage(context, selected.take(3), accent); shareOpen = false }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { Toast.makeText(context, "Couldn't create journal image", Toast.LENGTH_SHORT).show() }
                    finally { sharing = false }
                }
            }) { Text(if (sharing) "Creating…" else "Share image") } },
            dismissButton = { TextButton(enabled = !sharing, onClick = { share(selected.joinToString("\n----------------\n") { it.asText() }); shareOpen = false }) { Text("Share text") } }
        )
    }

}
