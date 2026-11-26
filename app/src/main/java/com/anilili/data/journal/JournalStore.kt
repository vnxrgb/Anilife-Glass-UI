package com.anilili.data.journal

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val animeId: Int? = null,
    val animeTitle: String = "",
    val episode: String = "",
    val notes: String = "",
    val review: String = "",
    val rating: Int = 0,
    val favoriteMoment: String = "",
    val updatedAt: Long = 0,
    val artwork: String? = null,
) {
    fun hasContent() = notes.isNotBlank() || review.isNotBlank() || rating > 0 || favoriteMoment.isNotBlank()
    fun asText(): String = buildString {
        appendLine(animeTitle.ifBlank { "Personal journal" })
        if (episode.isNotBlank()) appendLine("Episode: ${journalEpisodeLabel(episode)}")
        if (rating > 0) appendLine("Rating: $rating/10")
        if (notes.isNotBlank()) appendLine("\nNotes\n$notes")
        if (review.isNotBlank()) appendLine("\nReview\n$review")
        if (favoriteMoment.isNotBlank()) appendLine("\nFavorite moments\n$favoriteMoment")
    }
}

fun journalEpisodeLabel(value: String): String = value.trim().let { raw ->
    raw.toDoubleOrNull()?.takeIf { it.isFinite() && it % 1.0 == 0.0 }?.let { it.toLong().toString() } ?: raw
}

fun journalTitle(name: String): String = name.trim().takeIf { it.isNotBlank() }?.let { "$it's Anime Journal" } ?: "My Anime Journal"

object JournalStore {
    const val PREFS = "anilumos_journal"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(JournalEntry.serializer())
    private lateinit var context: Context
    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries = _entries.asStateFlow()
    private val _journalName = MutableStateFlow("")
    val journalName = _journalName.asStateFlow()
    fun setJournalName(name: String) {
        val clean = name.filterNot { it.isISOControl() }.trim().take(48)
        prefs().edit().putString("journal_name", clean).apply()
        _journalName.value = clean
    }
    private val _floatingEnabled = MutableStateFlow(true)
    val floatingEnabled = _floatingEnabled.asStateFlow()
    private val _minimized = MutableStateFlow(false)
    val minimized = _minimized.asStateFlow()
    fun setFloatingEnabled(enabled: Boolean) {
        prefs().edit().putBoolean("floating_enabled", enabled).apply()
        _floatingEnabled.value = enabled
    }
    fun setMinimized(minimized: Boolean) {
        prefs().edit().putBoolean("minimized", minimized).apply()
        _minimized.value = minimized
    }
    private var readable = true
    fun init(ctx: Context) {
        context = ctx.applicationContext
        _journalName.value = prefs().getString("journal_name", "").orEmpty()
        _floatingEnabled.value = prefs().getBoolean("floating_enabled", true)
        _minimized.value = prefs().getBoolean("minimized", false)
        val raw = prefs().getString("entries", null)
        val parsed = runCatching { raw?.let { json.decodeFromString(serializer, it) }.orEmpty() }
        readable = parsed.isSuccess
        _entries.value = parsed.getOrDefault(emptyList())
    }
    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    @Synchronized fun save(entry: JournalEntry): Boolean {
        if (!readable) return false
        val next = (_entries.value.filterNot { it.id == entry.id } + listOfNotNull(entry.takeIf { it.hasContent() }))
            .sortedByDescending { it.updatedAt }
        return persist(next)
    }
    private fun persist(next: List<JournalEntry>): Boolean {
        if (!readable || next.size > 1000) return false
        val raw = json.encodeToString(serializer, next)
        if (raw.length > 1_500_000) return false
        prefs().edit().putString("entries", raw).apply()
        _entries.value = next
        return true
    }
    @Synchronized fun delete(ids: Set<String>): List<JournalEntry>? {
        val removed = _entries.value.filter { it.id in ids }
        if (removed.isEmpty()) return emptyList()
        if (!persist(_entries.value.filterNot { it.id in ids })) return null
        val legacy = context.getSharedPreferences("anilumos_episode_notes", Context.MODE_PRIVATE)
        val edit = legacy.edit()
        removed.forEach { entry ->
            val id = entry.animeId
            if (id != null && entry.episode.isNotBlank()) {
                legacy.all.keys.filter { key -> key.startsWith("$id:") &&
                    journalEpisodeLabel(key.substringAfter(':')) == journalEpisodeLabel(entry.episode) }.forEach { edit.remove(it) }
            }
        }
        edit.apply()
        return removed
    }
    @Synchronized fun restore(removed: List<JournalEntry>): Boolean {
        val current = _entries.value
        val ids = current.mapTo(hashSetOf()) { it.id }
        return persist((current + removed.filterNot { it.id in ids }).sortedByDescending { it.updatedAt })
    }
    fun find(animeId: Int?, episode: String): JournalEntry? = entries.value.firstOrNull { it.animeId == animeId && journalEpisodeLabel(it.episode) == journalEpisodeLabel(episode) }
    fun episodeNote(animeId: Int, episode: String): String = find(animeId, episode)?.notes
        ?: context.getSharedPreferences("anilumos_episode_notes", Context.MODE_PRIVATE).getString("$animeId:$episode", "").orEmpty()
    fun saveEpisodeNote(animeId: Int, title: String, episode: String, notes: String): Boolean {
        val entry = find(animeId, episode) ?: JournalEntry(animeId = animeId, animeTitle = title, episode = episode)
        val saved = save(entry.copy(notes = notes, updatedAt = System.currentTimeMillis()))
        if (saved) context.getSharedPreferences("anilumos_episode_notes", Context.MODE_PRIVATE).edit().remove("$animeId:$episode").apply()
        return saved
    }
    fun position(): Pair<Float, Float> = prefs().getFloat("x", 1f) to prefs().getFloat("y", .65f)
    fun position(x: Float, y: Float) { prefs().edit().putFloat("x", x).putFloat("y", y).apply() }
    fun merge(incoming: List<JournalEntry>): Int {
        var count = 0
        incoming.forEach { entry ->
            if (entries.value.none { it.id == entry.id && it.updatedAt >= entry.updatedAt } && save(entry)) count++
        }
        return count
    }
}
