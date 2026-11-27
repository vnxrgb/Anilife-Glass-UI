package com.anilumos.manga.engine

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.Closeable
import java.io.IOException
import java.util.Locale

/** Publisher-documented Pepper&Carrot API. The existing bounded image delivery is reused;
 * no scraping, parser impersonation, AI-derived artwork, or bulk downloads. */
public class OpenComicReader private constructor(
    private val session: SourceSession,
    public val episodes: List<Episode>,
) : Closeable {
    public data class Episode(val number: Int, val directory: String, val title: String, val pageCount: Int)
    private val delivery = PageDelivery(scope, session)

    public suspend fun decode(episodeNumber: Int, index: Int, width: Int, height: Int): Bitmap {
        val episode = episodes.firstOrNull { it.number == episodeNumber } ?: error("Episode unavailable")
        require(index in 0 until episode.pageCount && width in 1..2048 && height in 1..4096)
        return PageDecoder.decode(delivery.load(pageUrl(episode, index)), width, height)
    }
    public suspend fun cover(): Bitmap = decode(episodes.first().number, 0, 768, 1152)
    override fun close() = session.close()
    public companion object {
        private val scope = SourceScope("PEPPER_CARROT", setOf("www.peppercarrot.com"))
        private const val root = "https://www.peppercarrot.com/0_sources/"
        internal fun parseEpisodes(json: String): List<Episode> {
            val array = JSONArray(json)
            require(array.length() in 1..250)
            return (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val directory = item.optString("name")
                val match = Regex("ep([0-9]{2,3})_([A-Za-z0-9-]+)").matchEntire(directory) ?: return@mapNotNull null
                val number = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..250 } ?: return@mapNotNull null
                val pages = item.optInt("total_pages", -1).takeIf { it in 1..100 } ?: return@mapNotNull null
                val translations = item.optJSONArray("translated_languages") ?: return@mapNotNull null
                if (!(0 until translations.length()).any { translations.optString(it) == "en" }) return@mapNotNull null
                Episode(number, directory, match.groupValues[2].replace('-', ' '), pages + 1)
            }.distinctBy { it.number }.sortedBy { it.number }
        }
        internal fun pageUrl(episode: Episode, page: Int): String {
            require(page in 0 until episode.pageCount)
            require(Regex("ep[0-9]{2,3}_[A-Za-z0-9-]+").matches(episode.directory))
            val e = String.format(Locale.ROOT, "%02d", episode.number)
            val p = String.format(Locale.ROOT, "%02d", page)
            return root + episode.directory + "/hi-res/en_Pepper-and-Carrot_by-David-Revoy_E" + e + "P" + p + ".jpg"
        }
        public suspend fun open(): OpenComicReader = withContext(Dispatchers.IO) {
            val session = SourceSession(scope)
            try {
                val episodes = session.client.newCall(Request.Builder().url(root + "episodes.json").header("Accept", "application/json").build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Official comic index unavailable")
                    val input = response.body.source()
                    input.request(262145L)
                    if (input.buffer.size > 262144L) throw IOException("Comic index exceeds limit")
                    parseEpisodes(input.readUtf8())
                }
                require(episodes.isNotEmpty()) { "No English episodes available" }
                val reader = OpenComicReader(session, episodes)
                reader.decode(episodes.first().number, 0, 320, 480).recycle()
                reader
            } catch (e: Throwable) {
                session.close()
                throw e
            }
        }
    }
}
