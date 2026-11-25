package com.anilili.data.remote

import com.anilili.BuildConfig
import com.anilili.data.model.SourcesResult
import com.anilili.data.model.StreamItem
import com.anilili.data.model.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Shared adapter for a deployment of the documented Anivexa API.
 * No default third-party host; configure an operator-authorized HTTPS deployment.
 */
internal object AnivexaApiIntegration {
    val providers = listOf("animenosub", "animedunya")
    val endpoint: HttpUrl? by lazy { endpoint(BuildConfig.ANIVEXA_PROVIDER_ENDPOINT) }
    fun endpoint(raw: String): HttpUrl? = raw.toHttpUrlOrNull()?.takeIf {
        it.isHttps && it.username.isEmpty() && it.password.isEmpty() &&
            it.query == null && it.fragment == null
    }
    fun supports(provider: String, audio: String): Boolean =
        provider in providers && (audio == "sub" || (provider == "animenosub" && audio == "dub"))
}

internal class AnivexaApiProvider(client: OkHttpClient, private val json: Json, private val base: HttpUrl) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .callTimeout(12, TimeUnit.SECONDS).build()

    private suspend fun get(vararg segments: String): JsonObject = runInterruptible(Dispatchers.IO) {
        val url = base.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()
        http.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
            .execute().use { response ->
                if (!response.isSuccessful) throw IOException("Anivexa API HTTP ${response.code}")
                val input = response.body?.source() ?: throw IOException("Missing response body")
                if (input.request(2L * 1024 * 1024 + 1)) throw IOException("Response too large")
                json.parseToJsonElement(input.readUtf8()) as? JsonObject
                    ?: throw IOException("Invalid Anivexa response")
            }
    }

    suspend fun availability(provider: String, id: Int): EpisodeAvailability {
        require(provider in AnivexaApiIntegration.providers && id > 0)
        return AnivexaApiParser.availability(get("episodes", provider, id.toString()), provider)
    }

    suspend fun sources(provider: String, id: Int, audio: String, episode: Int): SourcesResult {
        require(AnivexaApiIntegration.supports(provider, audio) && id > 0 && episode > 0)
        return AnivexaApiParser.sources(
            get("watch", provider, id.toString(), audio, "$provider-$episode"), id, episode, audio
        )
    }
}

internal object AnivexaApiParser {
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.number(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
    private fun safeUrl(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.any { it.code < 32 || it.code == 127 }) return null
        return raw.toHttpUrlOrNull()?.takeIf {
            it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null
        }?.toString()
    }

    fun availability(root: JsonObject, provider: String): EpisodeAvailability {
        val row = root[provider] as? JsonObject ?: throw IOException("Missing provider catalog")
        if ("error" in row) throw IOException("Provider catalog unavailable")
        val episodes = row["episodes"] as? JsonObject ?: throw IOException("Missing episodes")
        fun track(audio: String): Set<Int> {
            if (!AnivexaApiIntegration.supports(provider, audio)) return emptySet()
            val array = episodes[audio] as? JsonArray ?: return emptySet()
            return array.mapNotNull { item ->
                val ep = item as? JsonObject ?: return@mapNotNull null
                if (ep.string("audio")?.let { it != audio } == true) return@mapNotNull null
                ep.number("number")?.takeIf { it > 0 }
            }.toSet()
        }
        return EpisodeAvailability(track("sub"), track("dub"))
    }

    fun sources(root: JsonObject, id: Int, episode: Int, audio: String): SourcesResult {
        if (root.number("anilistId") != id || root.number("episode") != episode || root.string("audio") != audio)
            throw IOException("Anivexa episode identity mismatch")
        val streams = root["streams"] as? JsonArray ?: throw IOException("Missing streams")
        for (candidate in streams) {
            val item = candidate as? JsonObject ?: continue
            val type = item.string("type")?.lowercase() ?: continue
            if (type !in setOf("hls", "mp4", "dash")) continue
            val url = safeUrl(item.string("url")) ?: continue
            val headers = item["headers"] as? JsonObject
            val rawReferer = item.string("referer") ?: headers?.string("Referer")
            val referer = safeUrl(rawReferer)
            if (rawReferer != null && referer == null) continue
            val rawOrigin = headers?.string("Origin")
            val origin = safeUrl(rawOrigin)?.toHttpUrlOrNull()?.takeIf {
                it.encodedPath == "/" && it.query == null
            }?.toString()?.removeSuffix("/")
            if (rawOrigin != null && origin == null) continue
            val stream = StreamItem(url = url, type = type,
                quality = item.string("quality") ?: "Auto", audio = audio, referer = referer,
                isActive = true, width = null, height = null,
                headers = origin?.let { mapOf("Origin" to it) } ?: emptyMap())
            val subtitles = ((root["subtitles"] as? JsonArray).orEmpty() +
                (item["subtitles"] as? JsonArray).orEmpty()).mapNotNull { entry ->
                val sub = entry as? JsonObject ?: return@mapNotNull null
                val subUrl = safeUrl(sub.string("url")) ?: return@mapNotNull null
                if (!subUrl.toHttpUrlOrNull()!!.encodedPath.endsWith(".vtt", true)) return@mapNotNull null
                SubtitleItem(url = subUrl, label = sub.string("label") ?: "Subtitle",
                    language = sub.string("srclang") ?: sub.string("language") ?: "und",
                    mimeType = "text/vtt")
            }.distinctBy { it.url }
            return SourcesResult(listOf(stream), subtitles, null, null)
        }
        throw IOException("No supported direct stream for requested episode")
    }
}
