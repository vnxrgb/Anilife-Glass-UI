package com.anilili.ui.profile

import android.content.Intent
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
internal fun WrappedSummaryCard(stats: WrappedStats, artwork: String?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth().glassCard(shape).clickable(onClickLabel = "View Anime Wrapped", onClick = onOpen)) {
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
                    Text("Anime Wrapped", color = foreground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your anime journey in ${stats.year}", color = foreground.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "View statistics", tint = foreground)
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
    // History stores lifetime completions and last-touch time, not the year of each completion.
    // Label it honestly, rather than deriving fictitious yearly favourites or elapsed watch time.
    val localHistory = remember(history) { history.filter { !it.fromRemote && it.watchedEpisodes.isNotEmpty() }.sortedByDescending { it.watchedEpisodes.size } }
    Column(Modifier.fillMaxSize().glassAtmosphere()) {
        PageTitleBar(title = "Anime Wrapped", subtitle = "${stats.year} · Your viewing activity",
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            trailing = { IconButton(onClick = {
                val recap = "AniLumos Wrapped ${stats.year}: ${stats.episodes} recorded episodes, ${stats.activeDays} active days, ${stats.streak}-day current streak."
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, recap) }
                context.startActivity(Intent.createChooser(intent, "Share Anime Wrapped"))
            }) { Icon(Icons.Default.Share, contentDescription = "Share recap") } })
        LazyColumn(contentPadding = PaddingValues(start = device.pagePadding, end = device.pagePadding,
            top = 8.dp, bottom = LocalAppChromeBottomInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                WrappedSummaryCard(stats, localHistory.firstOrNull()?.cover, onOpen = {})
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
                Text("Based on activity recorded on this device. Older history may be incomplete. Actual viewing time and yearly genre rankings are not recorded yet.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
