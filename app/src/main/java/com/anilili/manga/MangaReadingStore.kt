package com.anilili.manga

import android.content.Context
import org.json.JSONObject

data class MangaSaved(val title: MangaTitle, val bookmarked: Boolean, val chapterId: String?, val page: Int, val updated: Long)

/** Local reading library scoped to the active profile. IDs are namespaced by provider. */
class MangaReadingStore(context: Context, profileId: String?) {
    private val prefs = context.getSharedPreferences("manga_reading_" + (profileId ?: "default"), Context.MODE_PRIVATE)
    private fun key(title: MangaTitle) = "${title.sourceId}:${title.id}"

    fun all(): List<MangaSaved> = prefs.all.values.mapNotNull { value ->
        runCatching {
            val j = JSONObject(value as String)
            MangaSaved(
                MangaTitle(j.getString("source"), j.getString("id"), j.getString("title"), j.optString("description"), j.optString("cover").takeIf { it.isNotBlank() }),
                j.optBoolean("bookmarked"), j.optString("chapter").takeIf { it.isNotBlank() },
                j.optInt("page").coerceAtLeast(0), j.optLong("updated")
            )
        }.getOrNull()
    }.sortedByDescending { it.updated }

    fun saved(title: MangaTitle): MangaSaved? = all().firstOrNull { it.title.sourceId == title.sourceId && it.title.id == title.id }

    fun bookmark(title: MangaTitle, value: Boolean) {
        val old = saved(title)
        write(title, value, old?.chapterId, old?.page ?: 0)
    }

    fun progress(title: MangaTitle, chapterId: String, page: Int) {
        write(title, saved(title)?.bookmarked ?: false, chapterId, page.coerceAtLeast(0))
    }

    private fun write(title: MangaTitle, bookmarked: Boolean, chapterId: String?, page: Int) {
        val j = JSONObject().put("source", title.sourceId).put("id", title.id)
            .put("title", title.title).put("description", title.description).put("cover", title.coverUrl.orEmpty())
            .put("bookmarked", bookmarked).put("chapter", chapterId.orEmpty()).put("page", page)
            .put("updated", System.currentTimeMillis())
        prefs.edit().putString(key(title), j.toString()).apply()
    }
}
