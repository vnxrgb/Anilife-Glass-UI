package com.anilili.manga

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

data class MangaSaved(val title: MangaTitle, val bookmarked: Boolean, val chapterId: String?, val page: Int, val updated: Long, val chapterNumber: String? = null, val pageCount: Int = 0)

/** Local reading library scoped to the active profile. IDs are namespaced by provider. */
class MangaReadingStore(context: Context, profileId: String?) {
    private val prefs = context.getSharedPreferences("manga_reading_" + (profileId ?: "default"), Context.MODE_PRIVATE)
    private fun key(title: MangaTitle) = "${title.sourceId}:${title.id}"

    fun all(): List<MangaSaved> = prefs.all.values.mapNotNull { value ->
        runCatching {
            val j = JSONObject(value as String)
            MangaSaved(
                MangaTitle(j.getString("source"), j.getString("id"), j.getString("title"), j.optString("description"), j.optString("cover").takeIf { it.isNotBlank() },
                    runCatching { MangaKind.valueOf(j.optString("kind")) }.getOrDefault(MangaKind.OTHER),
                    j.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(), j.optString("status"), j.optInt("year").takeIf { it > 0 }),
                j.optBoolean("bookmarked"), j.optString("chapter").takeIf { it.isNotBlank() },
                j.optInt("page").coerceAtLeast(0), j.optLong("updated"), j.optString("chapterNumber").takeIf { it.isNotBlank() }, j.optInt("pageCount")
            )
        }.getOrNull()
    }.sortedByDescending { it.updated }

    fun saved(title: MangaTitle): MangaSaved? = all().firstOrNull { it.title.sourceId == title.sourceId && it.title.id == title.id }

    fun bookmark(title: MangaTitle, value: Boolean) {
        val old = saved(title)
        write(title, value, old?.chapterId, old?.page ?: 0, old?.chapterNumber, old?.pageCount ?: 0)
    }

    fun progress(title: MangaTitle, chapterId: String, page: Int, chapterNumber: String? = null, pageCount: Int = 0) {
        write(title, saved(title)?.bookmarked ?: false, chapterId, page.coerceAtLeast(0), chapterNumber, pageCount)
    }

    private fun write(title: MangaTitle, bookmarked: Boolean, chapterId: String?, page: Int, chapterNumber: String?, pageCount: Int) {
        val j = JSONObject().put("source", title.sourceId).put("id", title.id)
            .put("title", title.title).put("description", title.description).put("cover", title.coverUrl.orEmpty())
            .put("bookmarked", bookmarked).put("chapter", chapterId.orEmpty()).put("page", page)
            .put("updated", System.currentTimeMillis()).put("chapterNumber", chapterNumber.orEmpty()).put("pageCount", pageCount)
            .put("kind", title.kind.name).put("genres", JSONArray(title.genres)).put("status", title.status).put("year", title.year ?: 0)
        prefs.edit().putString(key(title), j.toString()).apply()
    }
}
