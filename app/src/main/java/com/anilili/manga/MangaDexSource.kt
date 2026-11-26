package com.anilili.manga

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * First-party MangaDex adapter for public catalogue and chapter endpoints.
 * No extension APKs, user-installed plugins, or embedded scraper execution.
 * Public API: https://api.mangadex.org/docs/
 */
class MangaDexSource : MangaSource {
    override val id = "mangadex"
    override val displayName = "MangaDex"

    override suspend fun search(query: String, limit: Int): List<MangaTitle> =
        catalog(query, limit = limit)

    override suspend fun catalog(query: String, kind: MangaKind, order: MangaOrder, offset: Int, limit: Int): List<MangaTitle> {
        val filter = if (query.isBlank()) {
            if (order == MangaOrder.RECENT) "&order%5BupdatedAt%5D=desc" else "&order%5BfollowedCount%5D=desc"
        } else "&title=${enc(query.trim())}"
        val language = when (kind) {
            MangaKind.MANGA -> "&originalLanguage%5B%5D=ja"
            MangaKind.MANHWA -> "&originalLanguage%5B%5D=ko"
            MangaKind.MANHUA -> "&originalLanguage%5B%5D=zh&originalLanguage%5B%5D=zh-hk"
            else -> ""
        }
        val response = request("/manga?limit=${limit.coerceIn(1, 100)}&offset=${offset.coerceIn(0, 9900)}&includes%5B%5D=cover_art&contentRating%5B%5D=safe&contentRating%5B%5D=suggestive$filter$language")
        return response.getJSONArray("data").objects().map { manga ->
            val attributes = manga.getJSONObject("attributes")
            val title = attributes.getJSONObject("title")
            val cover = manga.optJSONArray("relationships")?.objects()
                ?.firstOrNull { it.optString("type") == "cover_art" }
                ?.optJSONObject("attributes")?.optString("fileName")
            MangaTitle(
                sourceId = id,
                id = manga.getString("id"),
                title = title.optString("en").takeIf { it.isNotBlank() && it != "null" }
                    ?: attributes.optJSONArray("altTitles")?.objects()?.map { it.optString("en") }?.firstOrNull { it.isNotBlank() && it != "null" }
                    ?: title.keys().asSequence().firstOrNull()?.let { title.optString(it) }.orEmpty().ifBlank { "Untitled" },
                description = attributes.optJSONObject("description")?.optString("en").orEmpty(),
                kind = MangaKind.fromLanguage(attributes.optString("originalLanguage")),
                genres = attributes.optJSONArray("tags")?.objects()?.mapNotNull { tag -> tag.optJSONObject("attributes")?.takeIf { it.optString("group") == "genre" }?.optJSONObject("name")?.optString("en")?.takeIf { it.isNotBlank() } }.orEmpty(),
                status = attributes.optString("status").takeUnless { it == "null" }.orEmpty(),
                year = attributes.optInt("year").takeIf { it > 0 },
                coverUrl = cover?.takeIf(String::isNotBlank)?.let {
                    "https://uploads.mangadex.org/covers/${manga.getString("id")}/$it"
                },
            )
        }
    }

    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> {
        val entries = mutableListOf<JSONObject>()
        var offset = 0
        var total = 0
        do {
            val response = request("/manga/${enc(mangaId)}/feed?limit=500&offset=$offset&translatedLanguage%5B%5D=${enc(language)}&order%5Bchapter%5D=desc&includes%5B%5D=scanlation_group&includeExternalUrl=0")
            total = response.optInt("total")
            val batch = response.getJSONArray("data").objects()
            entries.addAll(batch)
            offset += batch.size
            if (batch.isEmpty()) break
            if (offset < total) delay(300)
        } while (offset < total && offset < 10000)
        return entries.distinctBy { it.getString("id") }.map { chapter ->
            val attributes = chapter.getJSONObject("attributes")
            MangaChapter(
                sourceId = id,
                id = chapter.getString("id"),
                mangaId = mangaId,
                title = attributes.optString("title").takeUnless { it == "null" }.orEmpty(),
                chapterNumber = attributes.optString("chapter").takeUnless { it.isBlank() || it == "null" },
                language = attributes.optString("translatedLanguage", language),
                scanlationGroups = chapter.optJSONArray("relationships")?.objects()
                    ?.filter { it.optString("type") == "scanlation_group" }
                    ?.mapNotNull { it.optJSONObject("attributes")?.optString("name")?.takeIf(String::isNotBlank) }
                    .orEmpty(),
            )
        }
    }

    override suspend fun pages(chapterId: String): List<MangaPage> {
        val response = request("/at-home/server/${enc(chapterId)}")
        val base = response.getString("baseUrl")
        require(base.startsWith("https://")) { "Insecure page server" }
        val chapter = response.getJSONObject("chapter")
        val hash = chapter.getString("hash")
        return chapter.getJSONArray("data").strings().mapIndexed { index, filename ->
            MangaPage(index, "$base/data/$hash/$filename")
        }
    }

    private suspend fun request(path: String): JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/"))
        val connection = URL("https://api.mangadex.org$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "AniLumos/2.1.1 (Android; manga reader)")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) throw MangaSourceException("MangaDex HTTP $code")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}

class MangaSourceException(message: String) : Exception(message)
