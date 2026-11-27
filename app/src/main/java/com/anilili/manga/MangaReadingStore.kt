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
                MangaTitle(if (j.getString("source") == "kotatsu:mangadex") "mangadex" else j.getString("source"), if (j.getString("source") == "kotatsu:mangadex") j.getString("id").substringAfter('|') else j.getString("id"), j.getString("title"), j.optString("description"), j.optString("cover").takeIf { it.isNotBlank() },
                    runCatching { MangaKind.valueOf(j.optString("kind")) }.getOrDefault(MangaKind.OTHER),
                    j.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(), j.optString("status"), j.optInt("year").takeIf { it > 0 },
                    j.optJSONArray("authors")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
                    j.optJSONArray("alternativeTitles")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(), j.optString("largeCover").takeIf { it.isNotBlank() }),
                j.optBoolean("bookmarked"), j.optString("chapter").takeIf { it.isNotBlank() },
                j.optInt("page").coerceAtLeast(0), j.optLong("updated"), j.optString("chapterNumber").takeIf { it.isNotBlank() }, j.optInt("pageCount")
            )
        }.getOrNull()
    }.sortedByDescending { it.updated }.distinctBy { key(it.title) }

    fun saved(title: MangaTitle): MangaSaved? = all().firstOrNull { it.title.sourceId == title.sourceId && it.title.id == title.id }

    fun bookmark(title: MangaTitle, value: Boolean) {
        val old = saved(title)
        write(title, value, old?.chapterId, old?.page ?: 0, old?.chapterNumber, old?.pageCount ?: 0)
    }

    fun progress(title: MangaTitle, chapterId: String, page: Int, chapterNumber: String? = null, pageCount: Int = 0) {
        write(title, saved(title)?.bookmarked ?: false, chapterId, page.coerceAtLeast(0), chapterNumber, pageCount)
    }

    private fun chapterBookmarkKey(title: MangaTitle, chapterId: String) = "chapter_bookmark:" + key(title) + ":" + chapterId
    fun isChapterBookmarked(title: MangaTitle, chapterId: String): Boolean = prefs.getBoolean(chapterBookmarkKey(title, chapterId), false)
    fun bookmarkChapter(title: MangaTitle, chapterId: String, value: Boolean) {
        prefs.edit().putBoolean(chapterBookmarkKey(title, chapterId), value).apply()
    }

    private fun pageBookmarkKey(title: MangaTitle, chapterId: String) = "page_bookmarks:" + key(title) + ":" + chapterId
    fun pageBookmarks(title: MangaTitle, chapterId: String): Set<Int> =
        prefs.getStringSet(pageBookmarkKey(title, chapterId), emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.filter { it >= 0 }.toSet()
    fun bookmarkPage(title: MangaTitle, chapterId: String, page: Int, value: Boolean) {
        val old = pageBookmarks(title, chapterId)
        val next = if (value) old + page else old - page
        prefs.edit().putStringSet(pageBookmarkKey(title, chapterId), next.map { it.toString() }.toSet()).apply()
    }
    private fun categoryKey(title: MangaTitle) = "category:" + key(title)
    fun category(title: MangaTitle): String? = prefs.getString(categoryKey(title), null)
    fun categories(): List<String> = prefs.all.filterKeys { it.startsWith("category:") }.values.filterIsInstance<String>().filter { it.isNotBlank() }.distinct().sorted()
    fun setCategory(title: MangaTitle, value: String?) {
        val clean = value?.trim()?.take(60).orEmpty()
        if (clean.isEmpty()) prefs.edit().remove(categoryKey(title)).apply()
        else prefs.edit().putString(categoryKey(title), clean).apply()
    }

    private fun write(title: MangaTitle, bookmarked: Boolean, chapterId: String?, page: Int, chapterNumber: String?, pageCount: Int) {
        val j = JSONObject().put("source", title.sourceId).put("id", title.id)
            .put("title", title.title).put("description", title.description).put("cover", title.coverUrl.orEmpty())
            .put("bookmarked", bookmarked).put("chapter", chapterId.orEmpty()).put("page", page)
            .put("updated", System.currentTimeMillis()).put("chapterNumber", chapterNumber.orEmpty()).put("pageCount", pageCount)
            .put("kind", title.kind.name).put("genres", JSONArray(title.genres)).put("status", title.status).put("year", title.year ?: 0)
            .put("authors", JSONArray(title.authors)).put("alternativeTitles", JSONArray(title.alternativeTitles)).put("largeCover", title.largeCoverUrl.orEmpty())
        prefs.edit().putString(key(title), j.toString()).apply()
    }
}
