package com.anilili.data.library

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * How many episodes were finished on this device, per day — what the account drawer's activity
 * grid draws.
 *
 * The rest of the library only knows when each title was *last* touched, which cannot say which days
 * someone watched on, and MyAnimeList's API keeps no history at all. So a day is counted here as it
 * happens. AniList accounts also carry the account's own history ([mergedActivity]); for a
 * MyAnimeList account this log is the whole picture, and it starts the day the feature shipped.
 *
 * Keyed by local epoch day. Bounded to [KEEP_DAYS] so it cannot grow for ever.
 */
object WatchActivityStore {
    private const val KEY = "watch_activity_days"
    private const val TIME_KEY = "watch_activity_time_v1"
    private const val KEEP_DAYS = 400L

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(Long.serializer(), Int.serializer())
    private lateinit var prefs: SharedPreferences

    private val _days = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val days = _days.asStateFlow()
    private val timeSerializer = MapSerializer(Long.serializer(), MapSerializer(Int.serializer(), Long.serializer()))
    private val _watchTime = MutableStateFlow<Map<Long, Map<Int, Long>>>(emptyMap())
    val watchTime = _watchTime.asStateFlow()
    private var lastSampleEnd = 0L
    private var pendingMs = 0L
    /** Count measured elapsed playback, not media position or episode length. */
    @Synchronized
    fun recordPlayback(animeId: Int, elapsedMs: Long, sampleEnd: Long, today: LocalDate = LocalDate.now()) {
        if (!::prefs.isInitialized || animeId == 0 || elapsedMs !in 1L..2_000L) return
        // Multiple attached surfaces may observe the same playback service; count each wall-clock interval once.
        val accepted = (sampleEnd - maxOf(sampleEnd - elapsedMs, lastSampleEnd)).coerceAtLeast(0L)
        lastSampleEnd = maxOf(lastSampleEnd, sampleEnd)
        if (accepted == 0L) return
        val day = today.toEpochDay()
        val titles = _watchTime.value[day].orEmpty()
        _watchTime.value = (_watchTime.value + (day to (titles + (animeId to ((titles[animeId] ?: 0L) + accepted)))))
            .filterKeys { it > day - 730L }
        pendingMs += accepted
        if (pendingMs >= 5_000L) flushPlayback()
    }

    @Synchronized
    fun flushPlayback() {
        if (!::prefs.isInitialized || pendingMs == 0L) return
        prefs.edit().putString(TIME_KEY, json.encodeToString(timeSerializer, _watchTime.value)).apply()
        pendingMs = 0L
    }

    internal fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        _watchTime.value = runCatching {
            prefs.getString(TIME_KEY, null)?.let { json.decodeFromString(timeSerializer, it) }
        }.getOrNull().orEmpty()
        _days.value = runCatching {
            prefs.getString(KEY, null)?.let { json.decodeFromString(serializer, it) }
        }.getOrNull().orEmpty()
    }

    /** Count a new completion; advancing an already-watched row is not another episode. */
    @Synchronized
    fun recordEpisode(existing: HistoryEntry, watchedEpisode: Double, today: LocalDate = LocalDate.now()) {
        if (!shouldRecordWatchActivity(existing, watchedEpisode)) return
        val day = today.toEpochDay()
        val updated = (_days.value + (day to (_days.value[day] ?: 0) + 1))
            .filterKeys { it > day - KEEP_DAYS }
        _days.value = updated
        prefs.edit().putString(KEY, json.encodeToString(serializer, updated)).apply()
    }
}

/** A late callback or a newly discovered next episode must not count the same completion again. */
internal fun shouldRecordWatchActivity(existing: HistoryEntry, watchedEpisode: Double): Boolean =
    existing.episodeNumber == watchedEpisode && watchedEpisode !in existing.watchedEpisodes

/**
 * This device's episodes and the account's own activity, one count per day. A day counted in both
 * keeps the larger number rather than the sum: the same evening is often in both, and adding them
 * would count it twice.
 */
internal fun mergedActivity(local: Map<Long, Int>, account: Map<Long, Int>): Map<Long, Int> =
    (local.keys + account.keys).associateWith { day -> maxOf(local[day] ?: 0, account[day] ?: 0) }
        .filterValues { it > 0 }

/** Consecutive active days ending today — or yesterday, so a streak survives until tonight's episode. */
internal fun activityStreak(days: Map<Long, Int>, today: Long): Int {
    var day = if ((days[today] ?: 0) > 0) today else today - 1
    var streak = 0
    while ((days[day] ?: 0) > 0) {
        streak++
        day--
    }
    return streak
}
