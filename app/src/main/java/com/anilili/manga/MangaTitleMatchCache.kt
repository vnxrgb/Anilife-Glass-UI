package com.anilili.manga

import android.content.SharedPreferences
import org.json.JSONObject

/** Stores only source-owned title identity and observed chapter count, not AniList metadata
 * or raw page URLs. Successful chapter lists are historical observations, not live proof. */
internal class MangaTitleMatchCache(private val prefs: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    companion object {
        const val TTL = 604_800_000L
        const val MAX_ENTRIES = 300
        internal fun fresh(time: Long, now: Long): Boolean = time > 0 && now >= time && now - time < TTL
    }
    private fun key(reference: MangaTitle, source: MangaSource) = "match:" + reference.sourceId + ":" + reference.id + ":" + source.id
    fun get(reference: MangaTitle, source: MangaSource, revision: String): MangaTitleMatch? = runCatching {
        val j = JSONObject(prefs.getString(key(reference, source), null) ?: return null)
        if (j.getString("revision") != revision || !fresh(j.getLong("time"), now())) return null
        val id = j.getString("id").takeIf { it.isNotBlank() && it.length <= 200 } ?: return null
        val name = j.getString("name").takeIf { it.isNotBlank() && it.length <= 500 } ?: return null
        val count = j.getInt("count").takeIf { it > 0 } ?: return null
        MangaTitleMatch(MangaTitle(source.id, id, name, "", null), count)
    }.getOrNull()
    fun put(reference: MangaTitle, source: MangaSource, revision: String, match: MangaTitleMatch) {
        if (match.title.sourceId != source.id || match.count <= 0 || match.title.id.length > 200 || match.title.title.length > 500) return
        val editor = prefs.edit()
        val key = key(reference, source)
        val entries = prefs.all.filterKeys { it.startsWith("match:") && it != key }
        if (entries.size >= MAX_ENTRIES) {
            entries.entries.sortedBy { runCatching { JSONObject(it.value as String).optLong("time") }.getOrDefault(0) }
                .take(entries.size - MAX_ENTRIES + 1).forEach { editor.remove(it.key) }
        }
        editor.putString(key, JSONObject().put("revision", revision).put("time", now())
            .put("id", match.title.id).put("name", match.title.title).put("count", match.count).toString()).apply()
    }
    fun forget(reference: MangaTitle, source: MangaSource) { prefs.edit().remove(key(reference, source)).apply() }
    fun last(reference: MangaTitle): String? = prefs.getString("last:" + reference.sourceId + ":" + reference.id, null)
    fun choose(reference: MangaTitle, source: MangaSource) {
        val key = "last:" + reference.sourceId + ":" + reference.id
        val editor = prefs.edit()
        val keys = prefs.all.keys.filter { it.startsWith("last:") && it != key }
        if (keys.size >= MAX_ENTRIES) keys.take(keys.size - MAX_ENTRIES + 1).forEach { editor.remove(it) }
        editor.putString(key, source.id).apply()
    }
}
