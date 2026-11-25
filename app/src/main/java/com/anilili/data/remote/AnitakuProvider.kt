package com.anilili.data.remote

import com.anilili.data.model.SourcesResult
import com.anilili.data.model.StreamItem
import com.anilili.data.model.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Native, episode-level adapter for the Anisurge scraper response contract.
 * No extension runtime. The operator-supplied HTTPS endpoint must be authorized
 * for use by this application. Catalog discovery is deliberately separate:
 * the published scraper-anitaku pack defines only episode resolution.
 *
 * Contract reference: Anisurge/extensions, scraper-anitaku v1.0.1 (MIT).
 */
internal class AnitakuProvider(
    client: OkHttpClient,
    private val json: Json,
    private val endpoint: HttpUrl,
) {
    init {
        require(endpoint.isHttps && endpoint.username.isEmpty() && endpoint.password.isEmpty())
        require(endpoint.query == null && endpoint.fragment == null)
    }

    private val http = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun sources(anilistId: Int, audio: String, episode: Int): SourcesResult {
        require(anilistId > 0 && episode > 0)
        require(audio == "sub" || audio == "dub")
        val url = endpoint.newBuilder()
            .addQueryParameter("action", "batch_scrape")
            .addQueryParameter("anilistId", anilistId.toString())
            .addQueryParameter("episode", episode.toString())
            .addQueryParameter("source", "anitaku")
            .build()
        return runInterruptible(Dispatchers.IO) {
            val request = Request.Builder().url(url)
                .header("Accept", "application/json")
                .get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Anitaku HTTP ${response.code}")
                val body = response.body ?: throw IOException("Anitaku returned no response body")
                val input = body.source()
                if (input.request(MAX_RESPONSE_BYTES + 1L)) {
                    throw IOException("Anitaku response exceeds size limit")
                }
                val root = json.parseToJsonElement(input.readUtf8()) as? JsonObject
                    ?: throw IOException("Anitaku returned an invalid response")
                AnitakuResponseParser.sources(root, audio)
            }
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 1024L * 1024L
    }
}

/** Pure response mapping; uses only the requested audio and the selected encode's captions. */
internal object AnitakuResponseParser {
    fun sources(root: JsonObject, audio: String): SourcesResult {
        require(audio == "sub" || audio == "dub")
        val category = root[audio] as? JsonObject
        val candidates = (category?.get("streams") as? JsonArray).orEmpty()
        for (candidate in candidates) {
            val item = candidate as? JsonObject ?: continue
            val url = httpsUrl(item.string("url")) ?: continue
            if (!url.encodedPath.endsWith(".m3u8", ignoreCase = true)) continue
            val headers = item["headers"] as? JsonObject
            val referer = headerUrl(headers, "Referer")
            val origin = headerUrl(headers, "Origin")
            if (headers?.string("Referer")?.isNotBlank() == true && referer == null) continue
            if (headers?.string("Origin")?.isNotBlank() == true && origin == null) continue
            if (origin != null && (origin.encodedPath != "/" || origin.query != null || origin.fragment != null)) continue
            val transportHeaders = buildMap {
                origin?.let { put("Origin", it.toString().removeSuffix("/")) }
            }
            val stream = StreamItem(
                url = url.toString(),
                type = "hls",
                quality = item.string("quality")?.takeIf { it.isNotBlank() } ?: "Auto",
                audio = audio,
                referer = referer?.toString(),
                isActive = true,
                width = null,
                height = null,
                headers = transportHeaders,
            )
            val subtitles = (item["subtitles"] as? JsonArray).orEmpty()
                .mapNotNull { track ->
                    val caption = track as? JsonObject ?: return@mapNotNull null
                    val captionUrl = httpsUrl(caption.string("url")) ?: return@mapNotNull null
                    if (!captionUrl.encodedPath.endsWith(".vtt", ignoreCase = true)) return@mapNotNull null
                    val label = caption.string("label")?.takeIf { it.isNotBlank() } ?: "Subtitle"
                    SubtitleItem(
                        url = captionUrl.toString(),
                        label = label,
                        language = if (label.equals("English", true) || label.equals("en", true)) "en" else "und",
                        mimeType = "text/vtt",
                    )
                }.distinctBy { it.url }
            return SourcesResult(listOf(stream), subtitles, null, null)
        }
        throw IOException("Anitaku returned no playable ${audio.uppercase()} HLS source")
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun headerUrl(headers: JsonObject?, key: String): HttpUrl? =
        httpsUrl(headers?.string(key))

    private fun httpsUrl(raw: String?): HttpUrl? {
        if (raw.isNullOrBlank() || raw.any { it.code < 32 || it.code == 127 }) return null
        return raw.toHttpUrlOrNull()?.takeIf {
            it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null
        }
    }
}
