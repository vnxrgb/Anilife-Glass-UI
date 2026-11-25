package com.anilili.data.remote

import com.anilili.data.cache.AppCache
import com.anilili.diagnostics.DiagnosticsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
internal data class AniZipImage(val coverType: String? = null, val url: String? = null)

@Serializable
internal data class AniZipMappings(val images: List<AniZipImage> = emptyList())

/**
 * AniZip (api.ani.zip): a keyless service that answers for one AniList id with TheTVDB's artwork.
 *
 * The metadata tree already reads its clearlogos off the device (see `scripts/konoha_build.py`).
 * The app asks it live for one thing only — the "Fanart" backdrop, for the background picker — which
 * is a picker's worth of calls a week, far inside what a keyless service tolerates. It rate-limits
 * bursts with a 429, which the picker survives by showing TMDB's pictures alone.
 */
class AniZipClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val cache: AppCache,
) {
    /** TheTVDB's wide backdrop(s) for [anilistId], or empty. */
    suspend fun fanart(anilistId: Int): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            cache.getOrFetch(
                key = "anizipfanart:v1:$anilistId",
                serializer = ListSerializer(String.serializer()),
                ttlMs = FANART_TTL_MS,
            ) { fetchFanart(anilistId) }
        }
            .onFailure { DiagnosticsLog.throwable("AniZip fanart failed id=$anilistId", it) }
            .getOrDefault(emptyList())
    }

    private fun fetchFanart(anilistId: Int): List<String> {
        val request = Request.Builder()
            .url("$API_BASE/mappings?anilist_id=$anilistId")
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> emptyList()
                !response.isSuccessful -> error("AniZip HTTP ${response.code}")
                else -> json.decodeFromString(AniZipMappings.serializer(), response.body?.string().orEmpty())
                    .images
                    .filter { it.coverType.equals("Fanart", ignoreCase = true) }
                    .mapNotNull { it.url?.takeIf(String::isNotBlank) }
                    .distinct()
            }
        }
    }


    /** Exact episode cross-reference from AniZip; never infer a season or episode offset. */
    internal fun episodeReference(anilistId: Int, episode: Int): ExternalEpisodeRef? {
        val request = Request.Builder()
            .url("$API_BASE/mappings?anilist_id=$anilistId")
            .header("Accept", "application/json").build()
        return client.newCall(request).apply { timeout().timeout(4, java.util.concurrent.TimeUnit.SECONDS) }
            .execute().use { response ->
                if (response.code == 404) return null
                if (!response.isSuccessful) error("AniZip HTTP ${response.code}")
                val root = json.parseToJsonElement(response.body?.string().orEmpty()) as? kotlinx.serialization.json.JsonObject ?: return null
                val mappings = root["mappings"] as? kotlinx.serialization.json.JsonObject ?: return null
                if (mappings["anilist_id"]?.jsonPrimitive?.intOrNull != anilistId) return null
                val row = (root["episodes"] as? kotlinx.serialization.json.JsonObject)?.get(episode.toString()) as? kotlinx.serialization.json.JsonObject ?: return null
                val season = row["seasonNumber"]?.jsonPrimitive?.intOrNull ?: return null
                val number = row["episodeNumber"]?.jsonPrimitive?.intOrNull ?: return null
                if (season < 1 || number < 1) return null
                val titles = (row["title"] as? JsonObject)?.values?.mapNotNull {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                }?.toSet() ?: emptySet()
                ExternalEpisodeRef(mappings["imdb_id"]?.jsonPrimitive?.contentOrNull,
                    mappings["thetvdb_id"]?.jsonPrimitive?.intOrNull, season, number,
                    row["airDate"]?.jsonPrimitive?.contentOrNull?.take(10), titles)
            }
    }

    /** Resolve IMDb-based coordinates independently of TVDB order. Only accept a unique
     * episode whose original air date and title agree with the AniZip episode record.
     * This is a metadata-only request, not a streaming addon or provider integration.
     */
    internal fun imdbEpisodeReference(ref: ExternalEpisodeRef): ImdbEpisodeRef? {
        val imdb = ref.imdbId?.takeIf { Regex("tt[0-9]{7,10}").matches(it) } ?: return null
        if (ref.airDate.isNullOrBlank() || ref.titles.isEmpty()) return null
        val request = Request.Builder().url("https://v3-cinemeta.strem.io/meta/series/$imdb.json")
            .header("Accept", "application/json").build()
        return client.newCall(request).apply { timeout().timeout(5, java.util.concurrent.TimeUnit.SECONDS) }
            .execute().use { response ->
                if (response.code == 404) return null
                if (!response.isSuccessful) error("IMDb metadata HTTP ${response.code}")
                val root = json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject ?: return null
                matchImdbEpisode(root, ref)
            }
    }

    private companion object {
        const val API_BASE = "https://api.ani.zip"
        const val FANART_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}

internal data class ExternalEpisodeRef(
    val imdbId: String?, val tvdbId: Int?, val season: Int, val episode: Int,
    val airDate: String? = null, val titles: Set<String> = emptySet(),
)
internal data class ImdbEpisodeRef(val imdbId: String, val season: Int, val episode: Int)

internal fun matchImdbEpisode(root: JsonObject, ref: ExternalEpisodeRef): ImdbEpisodeRef? {
    val imdb = ref.imdbId ?: return null
    val date = ref.airDate?.takeIf { Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(it) } ?: return null
    fun normalize(title: String) = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFKD)
        .lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
    val titles = ref.titles.map(::normalize).filter { it.length >= 4 }.toSet()
    if (titles.isEmpty()) return null
    val meta = root["meta"] as? JsonObject ?: return null
    if (meta["id"]?.jsonPrimitive?.contentOrNull != imdb) return null
    val videos = meta["videos"] as? JsonArray ?: return null
    return videos.mapNotNull { item ->
        val row = item as? JsonObject ?: return@mapNotNull null
        val season = row["season"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val number = row["episode"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        if (season <= 0 || number <= 0 || row["id"]?.jsonPrimitive?.contentOrNull != "$imdb:$season:$number") return@mapNotNull null
        if (row["released"]?.jsonPrimitive?.contentOrNull?.take(10) != date) return@mapNotNull null
        val title = row["title"]?.jsonPrimitive?.contentOrNull ?: row["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        if (normalize(title) !in titles) return@mapNotNull null
        ImdbEpisodeRef(imdb, season, number)
    }.distinct().singleOrNull()
}
