package com.anilili.data

import com.anilili.data.model.EpisodeItem
import com.anilili.data.model.SkipTimes
import com.anilili.data.remote.AniSkipClient
import com.anilili.data.remote.AniZipClient
import com.anilili.data.remote.JikanClient
import com.anilili.data.remote.tenraiEpisodeKind
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

internal data class MetadataPlayback(
    val anilistId: Int,
    val episode: Int,
    val durationSeconds: Int,
    val intro: String?,
    val outro: String?,
    val recap: String?,
    val classification: String?,
    val classificationSource: String?,
)

internal data class MetadataProbe(val provider: String, val result: String, val detail: String)

internal object MetadataDiagnostics {
    private val _last = MutableStateFlow<MetadataPlayback?>(null)
    val last = _last.asStateFlow()
    val providers = listOf("AniSkip", "TheIntroDB", "IntroDB", "Tenrai · Episodes", "Tenrai · Recommendations", "FillerLists", "AniFillerPedia")

    fun record(anilistId: Int, episode: EpisodeItem, durationMs: Long, skip: SkipTimes?) {
        if (episode.number < 1 || episode.number % 1.0 != 0.0) return
        _last.value = MetadataPlayback(anilistId, episode.number.toInt(), (durationMs / 1000).toInt(),
            skip?.introSource, skip?.outroSource, skip?.recapSource,
            episode.classification?.label ?: "Filler".takeIf { episode.filler },
            episode.classificationSource ?: "Streaming provider or existing metadata".takeIf { episode.filler })
    }

    /** Independent read-only probes; skip the playback cache and OkHttp disk cache. */
    suspend fun test(anilistId: Int, episode: Int, duration: Double, selected: String? = null,
        onResult: (MetadataProbe) -> Unit) = withContext(Dispatchers.IO) {
        val http = AppGraph.httpClient.newBuilder().cache(null)
            .callTimeout(12, TimeUnit.SECONDS).build()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val ani = AniSkipClient(http, json)
        val filler = JikanClient(http, json)
        val zip = AniZipClient(http, json, AppGraph.metadataCache)
        val mal = try { AppGraph.repository.animeInfo(anilistId)?.idMal?.takeIf { it > 0 } }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val mapped = if (selected == null || selected == "TheIntroDB" || selected == "IntroDB")
            try { zip.episodeReference(anilistId, episode) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            else null
        fun timing(value: SkipTimes?): Pair<String, String> {
            if (value == null) return "No usable data" to "No matching complete timestamp window returned"
            val windows = listOfNotNull(
                value.introEnd?.let { "Intro ${value.introStart ?: 0.0}–${it}s" },
                value.outroEnd?.let { "Outro ${value.outroStart}–${it}s" },
                value.recapEnd?.let { "Recap ${value.recapStart}–${it}s" })
            return if (windows.isEmpty()) "No usable data" to "No complete windows" else "Data found" to windows.joinToString(" · ")
        }
        fun classification(value: Map<Int, com.anilili.data.model.EpisodeClassification>?): Pair<String, String> =
            if (value == null) "No usable data" to "Unsupported mapping or empty response"
            else if (value[episode] == null) "No episode label" to "${value.size} classifications returned; none for this episode"
            else "Data found" to "${value[episode]?.label} · ${value.size} classifications"
        for (provider in providers.filter { selected == null || it == selected }) {
            val result = try {
                when (provider) {
                    "AniSkip" -> if (mal == null) "Mapping unavailable" to "No MAL ID" else timing(ani.skipTimes(mal, episode, duration))
                    "TheIntroDB" -> if (mapped?.tvdbId == null) "Mapping unavailable" to "No verified TVDB episode" else timing(ani.theIntroDbTimes(mapped.tvdbId!!, mapped.season, mapped.episode, duration))
                    "IntroDB" -> {
                        val imdb = mapped?.let { zip.imdbEpisodeReference(it) }
                        if (imdb == null) "Mapping unavailable" to "No unique title/date match in IMDb-based episode metadata"
                        else timing(ani.introDbTimes(imdb.imdbId, imdb.season, imdb.episode, duration)).let { (status, detail) ->
                            status to "S${imdb.season} E${imdb.episode} · $detail"
                        }
                    }
                    "Tenrai · Episodes" -> if (mal == null) "Mapping unavailable" to "No MAL ID" else {
                        val row = filler.tenraiEpisode(mal, episode)
                        if (row == null) "No data" to "Episode not found"
                        else {
                            val kind = tenraiEpisodeKind(row)
                            val flagsKnown = row["filler"]?.jsonPrimitive?.booleanOrNull != null &&
                                row["recap"]?.jsonPrimitive?.booleanOrNull != null
                            if (kind != null) "Data found" to kind.label
                            else if (flagsKnown) "Data found" to "Neither filler nor recap flagged (not necessarily canon)"
                            else "No episode label" to "Episode found, but classification flags missing"
                        }
                    }
                    "Tenrai · Recommendations" -> if (mal == null) "Mapping unavailable" to "No MAL ID" else {
                        val ids = filler.recommendationIds(mal)
                        if (ids.isEmpty()) "No data" to "No recommendations returned"
                        else "Data found" to "${ids.size} MAL recommendations"
                    }
                    "FillerLists" -> if (mal == null) "Mapping unavailable" to "No MAL ID" else classification(filler.fillerListsEpisodes(mal))
                    else -> if (mal == null) "Mapping unavailable" to "No MAL ID" else classification(filler.aniFillerPediaEpisodes(mal))
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val message = e.message.orEmpty()
                val httpCode = Regex("HTTP (\\d{3})").find(message)?.groupValues?.get(1)
                when {
                    httpCode == "429" -> "Rate limited" to "HTTP 429"
                    httpCode != null -> "Service error" to "HTTP $httpCode"
                    e is java.io.InterruptedIOException -> "Timeout" to "Request timed out"
                    else -> "Request failed" to e.javaClass.simpleName
                }
            }
            withContext(Dispatchers.Main) { onResult(MetadataProbe(provider, result.first, result.second)) }
        }
    }
}
