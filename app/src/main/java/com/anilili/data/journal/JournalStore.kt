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
) {
    fun hasContent() = notes.isNotBlank() || review.isNotBlank() || rating > 0 || favoriteMoment.isNotBlank()
    fun asText(): String = buildString {
        appendLine(animeTitle.ifBlank { "Personal journal" })
        if (episode.isNotBlank()) appendLine("Episode: $episode")
        if (rating > 0) appendLine("Rating: $rating/10")
        if (notes.isNotBlank()) appendLine("\nNotes\n$notes")
        if (review.isNotBlank()) appendLine("\nReview\n$review")
        if (favoriteMoment.isNotBlank()) appendLine("\nFavorite moments\n$favoriteMoment")
    }
}

object JournalStore {
    const val PREFS = "anilumos_journal"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(JournalEntry.serializer())
    private lateinit var context: Context
    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries = _entries.asStateFlow()
    private var readable = true
    fun init(ctx: Context) {
        context = ctx.applicationContext
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
        if (next.size > 1000) return false
        val raw = json.encodeToString(serializer, next)
        if (raw.length > 1_500_000) return false
        prefs().edit().putString("entries", raw).apply()
        _entries.value = next
        return true
    }
    fun find(animeId: Int?, episode: String): JournalEntry? = entries.value.firstOrNull { it.animeId == animeId && it.episode == episode }
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
