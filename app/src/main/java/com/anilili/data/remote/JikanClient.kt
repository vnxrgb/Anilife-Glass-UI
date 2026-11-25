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
 * Minimal Jikan (MyAnimeList) client. Only used for per-episode filler flags,
 * which AniList doesn't track.
 */
class JikanClient(
    private val client: OkHttpClient,
    private val json: Json,
) {
    /**
     * Episode numbers marked as filler on MAL. Empty when the title has none.
     *
     * Throws when any page fails rather than answering with what it has: the answer is cached for a
     * week, and a Jikan outage (it answers 504 whenever MAL refuses it) used to be stored as
     * "no filler" — or as the first page's fillers only — for that whole week.
     */
    fun fillerEpisodes(malId: Int): Set<Int> = episodeClassifications(malId).filterValues { it == EpisodeClassification.FILLER }.keys

    internal fun episodeClassifications(malId: Int): Map<Int, EpisodeClassification> {
        val fillers = mutableMapOf<Int, EpisodeClassification>()
        var page = 1
        while (page <= MAX_PAGES) {
            // Jikan allows three requests a second; a long series is a dozen pages back to back.
            if (page > 1) Thread.sleep(PAGE_SPACING_MS)
            val request = Request.Builder()
                .url("https://api.jikan.moe/v4/anime/$malId/episodes?page=$page")
                .header("Accept", "application/json")
                .build()
            val root = client.newCall(request).execute().use { response ->
                // MAL has no episode list for this id: an answer, not a failure.
                if (response.code == 404 && page == 1) return emptyMap()
                if (response.code == 404) error("Jikan episode pagination incomplete")
                if (!response.isSuccessful) error("Jikan HTTP ${response.code}")
                json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            }
            val episodes = root["data"]?.jsonArray ?: error("Jikan episode data missing")
            episodes.forEach { element ->
                val episode = element.jsonObject
                val number = episode["mal_id"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return@forEach
                when {
                    episode["recap"]?.jsonPrimitive?.booleanOrNull == true -> fillers[number] = EpisodeClassification.RECAP
                    episode["filler"]?.jsonPrimitive?.booleanOrNull == true -> fillers[number] = EpisodeClassification.FILLER
                }
            }
            val hasNext = root["pagination"]?.jsonObject
                ?.get("has_next_page")?.jsonPrimitive?.booleanOrNull ?: false
            if (!hasNext) return fillers
            if (page == MAX_PAGES) error("Jikan episode pagination limit reached")
            page++
        }
        return fillers
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

        // 100 episodes per page; 15 pages covers the longest running series that matter.
        const val MAX_PAGES = 15
        const val PAGE_SPACING_MS = 400L
    }
}
