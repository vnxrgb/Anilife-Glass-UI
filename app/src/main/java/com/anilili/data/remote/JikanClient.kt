package com.anilili.data.remote

import com.anilili.data.model.EpisodeClassification
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Community MAL metadata. The legacy class name is retained for wiring compatibility;
 * the discontinued Jikan host is never contacted. Tenrai v1 supplies the compatible schema.
 */
class JikanClient(
    private val client: OkHttpClient,
    private val json: Json,
) {
    fun fillerEpisodes(malId: Int): Set<Int> =
        episodeClassifications(malId).filterValues { it == EpisodeClassification.FILLER }.keys

    /** Fetch only explicitly flagged rows; no need to download every episode of a long show. */
    internal fun episodeClassifications(malId: Int): Map<Int, EpisodeClassification> {
        require(malId > 0)
        val result = mutableMapOf<Int, EpisodeClassification>()
        for (filter in listOf("filler", "recap")) {
            var page = 1
            while (page <= MAX_PAGES) {
                val root = tenraiJson("/anime/$malId/episodes?$filter=true&page=$page")
                    ?: if (page == 1) return emptyMap() else error("Tenrai episode pagination incomplete")
                val rows = root["data"] as? JsonArray ?: error("Tenrai episode data missing")
                rows.forEach { item ->
                    val row = item as? JsonObject ?: error("Tenrai invalid episode record")
                    val number = row["mal_id"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
                        ?: error("Tenrai invalid episode number")
                    val kind = tenraiEpisodeKind(row)
                    if (kind != null && (result[number] != EpisodeClassification.RECAP || kind == EpisodeClassification.RECAP)) {
                        result[number] = kind
                    }
                }
                val next = (root["pagination"] as? JsonObject)?.get("has_next_page")?.jsonPrimitive?.booleanOrNull
                    ?: error("Tenrai pagination missing")
                if (!next) break
                if (rows.isEmpty() || page == MAX_PAGES) error("Tenrai episode pagination incomplete")
                page++
            }
        }
        return result
    }

    /** A single episode probe, independent of list pagination and cached classifications. */
    internal fun tenraiEpisode(malId: Int, episode: Int): JsonObject? {
        require(malId > 0 && episode > 0)
        val root = tenraiJson("/anime/$malId/episodes/$episode") ?: return null
        val row = root["data"] as? JsonObject ?: error("Tenrai episode data missing")
        if (row["mal_id"]?.jsonPrimitive?.intOrNull != episode) error("Tenrai episode mapping mismatch")
        return row
    }

    private fun tenraiJson(path: String): JsonObject? = synchronized(tenraiGate) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < tenraiBlockedUntil) error("Tenrai HTTP 429 (cooldown)")
        val wait = (tenraiNextRequest - now).coerceAtLeast(0L)
        if (wait > 0) Thread.sleep(wait)
        tenraiNextRequest = android.os.SystemClock.elapsedRealtime() + 600L
        val request = Request.Builder().url("https://api.tenrai.org/v1$path")
            .header("Accept", "application/json")
            .header("User-Agent", "AniLumos Android (https://github.com/vnxrgb/Anilumos)").build()
        client.newCall(request).apply { timeout().timeout(5, java.util.concurrent.TimeUnit.SECONDS) }
            .execute().use { response ->
                if (response.code == 404) return@synchronized null
                if (response.code == 429) {
                    val seconds = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, 86400L) ?: 60L
                    tenraiBlockedUntil = android.os.SystemClock.elapsedRealtime() + seconds * 1000L
                }
                if (!response.isSuccessful) error("Tenrai HTTP ${response.code}")
                json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
                    ?: error("Tenrai invalid response")
            }
    }

    /**
     * FillerLists uses absolute broadcast numbering, so only explicitly mapped whole-run MAL
     * entries can use it. Split seasons must never inherit another season's filler flags.
     */
    internal fun fillerListsEpisodes(malId: Int): Map<Int, EpisodeClassification>? {
        val slug = WHOLE_RUN_SLUGS[malId] ?: return null
        val root = publicJson("https://fillerlists.com/data/$slug.json") as? JsonObject ?: return null
        val rows = root["episodes"] as? JsonArray ?: return null
        if (rows.isEmpty()) return null
        return rows.mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            val kind = classify(row["type"]?.jsonPrimitive?.contentOrNull) ?: return@mapNotNull null
            val number = row["n"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
            number to kind
        }.toMap()
    }

    /** Public, cited episode classifications; restricted to known whole-run mappings. */
    internal fun aniFillerPediaEpisodes(malId: Int): Map<Int, EpisodeClassification>? {
        if (malId !in WHOLE_RUN_SLUGS) return null
        val root = publicJson("https://anifillerpedia.wiki/api/v1/series?mal_id=$malId") as? JsonObject ?: return null
        val series = (root["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.filter { it["mal_id"]?.jsonPrimitive?.intOrNull == malId }?.singleOrNull() ?: return null
        val id = series["id"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return null
        val rows = publicJson("https://anifillerpedia.wiki/api/v1/series/$id/episodes") as? JsonArray ?: return null
        if (rows.isEmpty()) return null
        return rows.mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            if (row["series_id"]?.jsonPrimitive?.intOrNull != id ||
                row["citation"] !is JsonObject ||
                row["has_pending_contribution"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
            val kind = classify(row["status"]?.jsonPrimitive?.contentOrNull) ?: return@mapNotNull null
            val number = row["episode_number"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
            number to kind
        }.toMap()
    }

    private fun classify(value: String?): EpisodeClassification? = when (value) {
        "filler" -> EpisodeClassification.FILLER
        "mixed" -> EpisodeClassification.MIXED
        "canon" -> EpisodeClassification.CANON
        "anime-canon" -> EpisodeClassification.ANIME_CANON
        "recap" -> EpisodeClassification.RECAP
        else -> null
    }

    /** MAL community recommendations, ranked by votes, then mapped to AniList by exact MAL ID. */
    internal fun recommendationIds(malId: Int): List<Int> {
        require(malId > 0)
        val root = tenraiJson("/anime/$malId/recommendations")
            ?: return emptyList()
        return tenraiRecommendationIds(root, malId)
    }

    private fun publicJson(url: String): JsonElement? {
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "AniLumos Android (https://github.com/vnxrgb/Anilumos)").build()
        return client.newCall(request).apply { timeout().timeout(4, java.util.concurrent.TimeUnit.SECONDS) }
            .execute().use { response ->
                if (response.code == 404) return null
                if (!response.isSuccessful) error("Filler metadata HTTP ${response.code}")
                json.parseToJsonElement(response.body?.string().orEmpty())
            }
    }

    private companion object {
        private val WHOLE_RUN_SLUGS = mapOf(20 to "naruto", 1735 to "naruto-shippuden", 21 to "one-piece", 269 to "bleach", 34572 to "black-clover", 34566 to "boruto", 223 to "dragon-ball", 813 to "dragon-ball-z", 11061 to "hunter-x-hunter")

        const val MAX_PAGES = 15
        // Shared by playback and diagnostics instances. Respect server cooldown, no retry storm.
        val tenraiGate = Any()
        var tenraiNextRequest = 0L
        var tenraiBlockedUntil = 0L
    }
}
