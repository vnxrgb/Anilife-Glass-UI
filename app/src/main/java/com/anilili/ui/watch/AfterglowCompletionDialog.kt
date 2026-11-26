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
    var suggestions by remember(data.anilistId) { mutableStateOf<List<Media>>(emptyList()) }
    var loading by remember(data.anilistId) { mutableStateOf(true) }
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
    Dialog(onDismissRequest = onDismiss) {
        val colors = MaterialTheme.colorScheme
        val shape = MaterialTheme.shapes.extraLarge
        Surface(modifier = Modifier.widthIn(max = 400.dp).border(.5.dp, colors.onSurface.copy(alpha = .16f), shape), shape = shape, color = colors.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)
                .background(if (colors.background == Color.Black) Color.Black else colors.surface)
                .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AFTERGLOW", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Every story leaves a glow.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(data.seriesTitle, style = MaterialTheme.typography.bodyMedium)
                Text("You've reached the end. What will you watch next?", style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (suggestions.isNotEmpty()) Text("Because you watched", style = MaterialTheme.typography.titleSmall)
                if (suggestions.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.take(3).forEach { media ->
                        Column(Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                            .clickable { onOpenRecommendation(media.id) }.padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            EpisodeArtwork(image = media.coverImage.best, blurred = false,
                                modifier = Modifier.fillMaxWidth().aspectRatio(.72f).clip(MaterialTheme.shapes.small))
                            Text(media.title.preferred, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (!loading && suggestions.isEmpty()) Text("Recommendations are unavailable right now.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ratingExpanded = !ratingExpanded }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Rate anime", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(onClick = { editing = !editing }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Journal note", style = MaterialTheme.typography.labelMedium) }
                }
                if (ratingExpanded) {
                Text(if (rating == 0) "Rate this anime (optional)" else "Your rating: $rating/10", style = MaterialTheme.typography.labelMedium)
                Slider(value = rating.toFloat(), onValueChange = { rating = it.toInt() }, valueRange = 0f..10f, steps = 9)
                TextButton(onClick = {
                    val entry = JournalStore.find(data.anilistId, "") ?: com.anilili.data.journal.JournalEntry(animeId = data.anilistId, animeTitle = data.seriesTitle, artwork = data.artworkUrl)
                    noteStatus = if (JournalStore.save(entry.copy(rating = rating, updatedAt = System.currentTimeMillis()))) "Rating saved to Anime Journal" else "Could not save rating"
                }) { Text("Save rating") }
                }
                OutlinedButton(onClick = onFranchise, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Franchise watch order") }
                if (editing) {
                    OutlinedTextField(value = note, onValueChange = { note = it.take(2000) }, label = { Text("My episode notes") }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        val saved = JournalStore.saveEpisodeNote(data.anilistId, data.seriesTitle, data.current.number.toString(), note.trim())
                        noteStatus = if (saved) "Saved to Anime Journal" else "Could not save note"
                        if (saved) editing = false
                    }) { Text("Save note") }
                }
                if (noteStatus.isNotEmpty()) Text(noteStatus, style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onHome, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Return to Home", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Stay here", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
