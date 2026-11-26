package com.anilili.ui.profile

import android.widget.Toast
import com.anilili.data.journal.JournalStore
import com.anilili.data.journal.wrappedTitle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anilili.data.library.HistoryEntry
import com.anilili.data.library.WatchActivityStore
import com.anilili.data.AppGraph
import com.anilili.data.model.Media
import com.anilili.ui.rethrowIfCancellation
import com.anilili.data.library.activityStreak
import com.anilili.ui.adaptive.LocalAppDeviceProfile
import com.anilili.ui.components.PageTitleBar
import com.anilili.ui.components.LocalAppChromeBottomInset
import com.anilili.ui.components.glassAtmosphere
import com.anilili.ui.components.glassCard
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

internal data class WrappedStats(
    val year: Int,
    val episodes: Int,
    val activeDays: Int,
    val streak: Int,
    val months: List<Int>,
)

internal fun wrappedStats(days: Map<Long, Int>, today: LocalDate): WrappedStats {
    val start = today.withDayOfYear(1).toEpochDay()
    val recorded = days.filter { (day, count) -> day in start..today.toEpochDay() && count > 0 }
    val months = (1..12).map { month ->
        recorded.filterKeys { LocalDate.ofEpochDay(it).monthValue == month }.values.sum()
    }
    return WrappedStats(today.year, recorded.values.sum(), recorded.size,
        activityStreak(days, today.toEpochDay()), months)
}

@Composable
internal fun WrappedSummaryCard(stats: WrappedStats, artwork: String?, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val journalName by JournalStore.journalName.collectAsState()
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth().glassCard(shape).then(if (onOpen != null) Modifier.clickable(onClickLabel = "View AniLumos Wrapped", onClick = onOpen) else Modifier)) {
        if (!artwork.isNullOrBlank()) {
            AsyncImage(model = artwork, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(shape))
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                listOf(Color.Black.copy(alpha = .93f), Color.Black.copy(alpha = .78f), Color.Black.copy(alpha = .50f)))))
        }
        val foreground = if (artwork.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else Color.White
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(wrappedTitle(journalName, stats.year), maxLines = 2, overflow = TextOverflow.Ellipsis, color = foreground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your anime journey in ${stats.year}", color = foreground.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                }
                if (onOpen != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "View statistics", tint = foreground)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WrappedMetric(stats.episodes.toString(), "Episodes", Modifier.weight(1f), foreground)
                WrappedMetric(stats.activeDays.toString(), "Active days", Modifier.weight(1f), foreground)
                WrappedMetric(stats.streak.toString(), "Day streak", Modifier.weight(1f), foreground)
            }
        }
    }
}

@Composable
private fun WrappedMetric(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = .78f))
    }
}

@Composable
internal fun AnimeWrappedScreen(days: Map<Long, Int>, history: List<HistoryEntry>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val device = LocalAppDeviceProfile.current
    val context = LocalContext.current
    val today = LocalDate.now()
    val stats = remember(days, today) { wrappedStats(days, today) }
    val watchTime by WatchActivityStore.watchTime.collectAsState()
    val annualTime = remember(watchTime, today) {
        val totals = mutableMapOf<Int, Long>()
        watchTime.filterKeys { it in today.withDayOfYear(1).toEpochDay()..today.toEpochDay() }
            .values.forEach { titles -> titles.forEach { (id, ms) -> totals[id] = (totals[id] ?: 0L) + ms } }
        totals.toMap()
    }
    val totalMs = annualTime.values.sum()
    val ids = remember(annualTime.keys) { annualTime.keys.sorted() }
    val metadata by produceState<List<Media>>(emptyList(), ids) {
        value = try {
            ids.chunked(50).flatMap { AppGraph.repository.mediaByIds(it) }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            emptyList()
        }
    }
    val byId = remember(metadata) { metadata.associateBy { it.id } }
    val genreTime = remember(annualTime, metadata) {
        val totals = mutableMapOf<String, Long>()
        metadata.forEach { media ->
            val ms = annualTime[media.id] ?: 0L
            media.genres.distinct().forEach { genre -> totals[genre] = (totals[genre] ?: 0L) + ms }
        }
        totals.entries.sortedByDescending { it.value }.take(5)
    }
    // History stores lifetime completions and last-touch time, not the year of each completion.
    // Label it honestly, rather than deriving fictitious yearly favourites or elapsed watch time.
    val localHistory = remember(history) { history.filter { !it.fromRemote && it.watchedEpisodes.isNotEmpty() }.sortedByDescending { it.watchedEpisodes.size } }
    val personality = wrappedPersonality(stats, annualTime.size, genreTime.map { it.key })
    val topTitles = annualTime.entries.sortedByDescending { it.value }.take(5).map { entry ->
        val media = byId[entry.key]
        val cached = history.firstOrNull { it.anilistId == entry.key }
        WrappedTitle(media?.title?.preferred ?: cached?.title ?: "Anime #${entry.key}",
            media?.coverImage?.best ?: cached?.cover, formatWrappedTime(entry.value))
    }
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    var exporting by remember { mutableStateOf(false) }
    val journalName by JournalStore.journalName.collectAsState()
    var renameOpen by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var shareOpen by remember { mutableStateOf(false) }
    val includeNameDefault by JournalStore.includeNameDefault.collectAsState()
    var includeName by remember { mutableStateOf(false) }
    if (renameOpen) {
        AlertDialog(onDismissRequest = { renameOpen = false },
            title = { Text("Personalize your Wrapped") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This name is shared with your Anime Journal. Leave it blank for the default branding.")
                OutlinedTextField(nameDraft, { nameDraft = it.filterNot { ch -> ch.isISOControl() }.take(48) },
                    label = { Text("Name or nickname") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                Text(wrappedTitle(nameDraft, stats.year), style = MaterialTheme.typography.titleMedium)
            } },
            confirmButton = { Button(onClick = { JournalStore.setJournalName(nameDraft); renameOpen = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } })
    }
    if (shareOpen) {
        AlertDialog(onDismissRequest = { shareOpen = false },
            title = { Text("Share your Wrapped") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your name is optional on the shared recap.")
                if (journalName.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeName, onCheckedChange = { includeName = it })
                    Text("Include my name")
                }
                Text(wrappedTitle(if (includeName) journalName else "", stats.year), style = MaterialTheme.typography.titleMedium)
            } },
            confirmButton = { Button(onClick = {
                val sharedName = if (includeName) journalName else ""
                shareOpen = false
                exporting = true
                scope.launch {
                    try {
                        shareWrappedImage(context, stats, formatWrappedTime(totalMs), annualTime.size,
                            topTitles, genreTime.map { it.key }, personality, accent, sharedName)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { Toast.makeText(context, "Could not create recap image", Toast.LENGTH_SHORT).show() }
                    finally { exporting = false }
                }
            }) { Text("Share image") } },
            dismissButton = { TextButton(onClick = { shareOpen = false }) { Text("Cancel") } })
    }
    Column(Modifier.fillMaxSize().glassAtmosphere()) {
        PageTitleBar(title = "Wrapped", subtitle = "${stats.year} · AniLumos",
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            trailing = {
                IconButton(enabled = !exporting, onClick = { includeName = includeNameDefault; shareOpen = true }) {
                    if (exporting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Share, contentDescription = "Share recap image")
                }
            })
        LazyColumn(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth().align(Alignment.CenterHorizontally), contentPadding = PaddingValues(start = device.pagePadding, end = device.pagePadding,
            top = 8.dp, bottom = LocalAppChromeBottomInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                WrappedSummaryCard(stats, localHistory.firstOrNull()?.cover, onOpen = null)
                TextButton(onClick = { nameDraft = journalName; renameOpen = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Personalize name")
                }
            }
            item {
                Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(26.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Measured watch time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(formatWrappedTime(totalMs), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${annualTime.size} anime played this year · Native playback only",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Recorded since watch-time tracking was enabled.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (annualTime.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(26.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Top anime this year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            topTitles.take(3).forEachIndexed { index, title ->
                                Column(Modifier.weight(1f).glassCard(RoundedCornerShape(18.dp)).padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box {
                                        AsyncImage(model = title.artwork, contentDescription = title.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(.70f).clip(RoundedCornerShape(14.dp)))
                                        Text("#${index + 1}", color = Color.White, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(4.dp).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(10.dp)).padding(6.dp))
                                    }
                                    Text(title.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(title.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (genreTime.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(26.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Favourite genres this year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        genreTime.forEach { (genre, ms) ->
                            Text("$genre · ${formatWrappedTime(ms)}", style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(progress = { (ms.toFloat() / totalMs.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
                        }
                        Text("Anime may belong to multiple genres; totals overlap.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(26.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Monthly activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Recorded episode completions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val peak = (stats.months.maxOrNull() ?: 0).coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                        stats.months.forEachIndexed { index, count ->
                            val month = Month.of(index + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            Column(Modifier.weight(1f).semantics { contentDescription = "$month: $count episodes" },
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth(.68f).height((if (count == 0) 2f else (100f * count / peak).coerceAtLeast(4f)).dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (count == 0) .15f else .82f)))
                                }
                                Text(month.take(3), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Clip)
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(26.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Local watch history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${localHistory.size} anime with recorded completions · All time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (localHistory.isEmpty()) {
                        Text("Complete an episode to start building your recap.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(localHistory.take(6), key = { it.anilistId }) { entry ->
                                Column(Modifier.width(108.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    AsyncImage(model = entry.cover, contentDescription = entry.title, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(.70f).clip(RoundedCornerShape(16.dp)))
                                    Text(entry.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${entry.watchedEpisodes.size} episodes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(26.dp)).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(personality.symbol, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Your viewing personality", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (journalName.isBlank()) personality.title else "$journalName — ${personality.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(personality.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text("Based on activity recorded on this device. Older history may be incomplete. Watch time measures supported native playback from the tracking update onward; older time cannot be recovered.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatWrappedTime(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}
