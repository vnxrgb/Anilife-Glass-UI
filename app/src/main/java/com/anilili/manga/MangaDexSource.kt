package com.anilili.manga

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlinx.coroutines.Dispatchers
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

    override suspend fun search(query: String, limit: Int): List<MangaTitle> {
        if (query.isBlank()) return emptyList()
        val response = request("/manga?title=${enc(query)}&limit=${limit.coerceIn(1, 100)}&includes%5B%5D=cover_art")
        return response.getJSONArray("data").objects().map { manga ->
            val attributes = manga.getJSONObject("attributes")
            val title = attributes.getJSONObject("title")
            val cover = manga.optJSONArray("relationships")?.objects()
                ?.firstOrNull { it.optString("type") == "cover_art" }
                ?.optJSONObject("attributes")?.optString("fileName")
            MangaTitle(
                sourceId = id,
                id = manga.getString("id"),
                title = title.optString("en").ifBlank { title.keys().asSequence().firstOrNull()?.let(title::optString).orEmpty() },
                description = attributes.optJSONObject("description")?.optString("en").orEmpty(),
                coverUrl = cover?.takeIf(String::isNotBlank)?.let {
                    "https://uploads.mangadex.org/covers/${manga.getString("id")}/$it"
                },
            )
        }
    }

    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> {
        val response = request("/manga/${enc(mangaId)}/feed?limit=100&translatedLanguage%5B%5D=${enc(language)}&order%5Bchapter%5D=desc&includes%5B%5D=scanlation_group")
        return response.getJSONArray("data").objects().map { chapter ->
            val attributes = chapter.getJSONObject("attributes")
            MangaChapter(
                sourceId = id,
                id = chapter.getString("id"),
                mangaId = mangaId,
                title = attributes.optString("title"),
                chapterNumber = attributes.optString("chapter").takeIf(String::isNotBlank),
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
