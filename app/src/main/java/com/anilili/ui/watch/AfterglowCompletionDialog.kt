package com.anilili.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.anilili.data.AppGraph
import com.anilili.data.settings.SettingsStore
import com.anilili.ui.detail.buildWatchOrder
import com.anilili.ui.detail.seasonChain
import com.anilili.data.model.Media
import com.anilili.data.journal.JournalStore
import com.anilili.data.recs.Recommendations
import com.anilili.ui.components.EpisodeArtwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal fun afterglowSeriesComplete(status: String?, total: Int?, episode: Double, movie: Boolean): Boolean =
    status == "FINISHED" && (movie || (total != null && total > 0 && episode >= total))

@Composable
internal fun AfterglowCompletionDialog(
    data: WatchData,
    onDismiss: () -> Unit,
    onOpenRecommendation: (Int) -> Unit,
    onFranchise: () -> Unit,
    onHome: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hideAdult by SettingsStore.hideAdultContent.collectAsState()
    var suggestions by remember(data.anilistId) { mutableStateOf<List<Media>>(emptyList()) }
    var loading by remember(data.anilistId) { mutableStateOf(true) }
    var hasFranchise by remember(data.anilistId, hideAdult) { mutableStateOf(false) }
    var note by remember(data.anilistId, data.current.number) {
        mutableStateOf(JournalStore.episodeNote(data.anilistId, data.current.number.toString()))
    }
    var editing by remember { mutableStateOf(false) }
    var ratingExpanded by remember { mutableStateOf(false) }
    var noteStatus by remember { mutableStateOf("") }
    var rating by remember(data.anilistId) { mutableStateOf(JournalStore.find(data.anilistId, "")?.rating ?: 0) }
    LaunchedEffect(data.anilistId) {
        suggestions = try { withTimeoutOrNull(7000L) { Recommendations.afterglow(data.anilistId) }.orEmpty() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { emptyList() }
        loading = false
    }
    LaunchedEffect(data.anilistId, hideAdult) {
        hasFranchise = try {
            withTimeoutOrNull(7000L) {
                val info = AppGraph.repository.animeInfo(data.anilistId) ?: return@withTimeoutOrNull false
                val series = AppGraph.repository.animeSeries(info)
                buildWatchOrder(info = info, series = series, seasons = seasonChain(info, series), hideAdult = hideAdult).size > 1
            } ?: false
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val colors = MaterialTheme.colorScheme
        val shape = MaterialTheme.shapes.large
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            val availableHeight = maxHeight
            Column(Modifier.widthIn(max = if (landscape) 680.dp else 380.dp).fillMaxWidth()
                .heightIn(max = availableHeight)
                .clip(shape).background(colors.surface.copy(alpha = .94f))
                .border(.75.dp, Brush.verticalGradient(listOf(colors.onSurface.copy(alpha = .22f), colors.onSurface.copy(alpha = .08f))), shape)
                .padding(if (landscape) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (landscape) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(.85f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                AfterglowHeading(data.seriesTitle)
                                AfterglowActions(hasFranchise, { ratingExpanded = !ratingExpanded; editing = false }, { editing = !editing; ratingExpanded = false }, onFranchise)
                            }
                            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AfterglowRecommendations(data.seriesTitle, suggestions, loading, true, onOpenRecommendation)
                            }
                        }
                    } else {
                        AfterglowHeading(data.seriesTitle)
                        AfterglowRecommendations(data.seriesTitle, suggestions, loading, false, onOpenRecommendation)
                        AfterglowActions(hasFranchise, { ratingExpanded = !ratingExpanded; editing = false }, { editing = !editing; ratingExpanded = false }, onFranchise)
                    }
                    if (ratingExpanded) {
                        Text(if (rating == 0) "Rate this anime (optional)" else "Your rating: $rating/10", style = MaterialTheme.typography.labelMedium)
                        Slider(value = rating.toFloat(), onValueChange = { rating = it.toInt() }, valueRange = 0f..10f, steps = 9)
                        TextButton(onClick = {
                            val entry = JournalStore.find(data.anilistId, "") ?: com.anilili.data.journal.JournalEntry(animeId = data.anilistId, animeTitle = data.seriesTitle, artwork = data.artworkUrl)
                            val saved = JournalStore.save(entry.copy(rating = rating, updatedAt = System.currentTimeMillis()))
                            noteStatus = if (saved) "Rating saved to Anime Journal" else "Could not save rating"
                            if (saved) ratingExpanded = false
                        }) { Text("Save rating") }
                    }
                    if (editing) {
                        OutlinedTextField(value = note, onValueChange = { note = it.take(2000) }, label = { Text("My episode notes") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                        TextButton(onClick = {
                            val saved = JournalStore.saveEpisodeNote(data.anilistId, data.seriesTitle, data.current.number.toString(), note.trim())
                            noteStatus = if (saved) "Saved to Anime Journal" else "Could not save note"
                            if (saved) editing = false
                        }) { Text("Save note") }
                    }
                    if (noteStatus.isNotEmpty()) Text(noteStatus, style = MaterialTheme.typography.labelSmall)
                }
                // Always outside the scroll region, even with the keyboard or expanded journal.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onHome, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Return to Home", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Stay here", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun AfterglowHeading(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("AFTERGLOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("Every story leaves a glow.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AfterglowActions(hasFranchise: Boolean, onRate: () -> Unit, onNote: () -> Unit, onFranchise: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onRate, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Rate anime", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
            OutlinedButton(onClick = onNote, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Journal note", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
        }
        if (hasFranchise) OutlinedButton(onClick = onFranchise, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(50)) { Text("Franchise watch order", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun AfterglowRecommendations(title: String, suggestions: List<Media>, loading: Boolean, landscape: Boolean, onOpen: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Because you watched $title", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loading && suggestions.isEmpty()) Text("Recommendations are unavailable right now.", style = MaterialTheme.typography.bodySmall)
        if (suggestions.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.take(3).forEach { media ->
                Column(Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable { onOpen(media.id) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EpisodeArtwork(image = media.coverImage.best, blurred = false,
                        modifier = Modifier.widthIn(max = if (landscape) 78.dp else 100.dp).fillMaxWidth().aspectRatio(.72f).clip(MaterialTheme.shapes.small))
                    Text(media.title.preferred, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall,
                        maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
