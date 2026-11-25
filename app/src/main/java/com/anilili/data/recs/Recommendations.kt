package com.anilili.data.recs

import android.content.Context
import android.util.AtomicFile
import com.anilili.data.AppGraph
import com.anilili.data.library.LibraryStore
import com.anilili.data.model.Media
import com.anilili.data.remote.KonohaClient
import com.anilili.data.settings.SettingsStore
import com.anilili.diagnostics.DiagnosticsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.GZIPInputStream

/** A row of recommended titles, and — for "Because you watched X" — the X. */
data class RecommendationRow(val items: List<Media>, val seed: Media? = null)

/**
 * The device half of recommendations: keeps the published [NeighbourGraph] on disk, refreshes it
 * daily, and turns [TasteEngine]'s ids into cards.
 *
 * The graph is kept as the downloaded file, not in [com.anilili.data.cache.AppCache]: it is 1.5 MB
 * of gzip that has to survive offline launches whole, and parsing it is the only cost worth
 * avoiding twice, so the parsed copy is held in memory for the life of the process.
 */
object Recommendations {
    private const val PATH = "recs/neighbours-v1.tsv.gz"

    /** The build writes it daily; a day-old graph recommends the same things. */
    private const val REFRESH_AFTER_MS = 24L * 60 * 60 * 1000

    /** A bound on a body, not a budget: the file is ~1.5 MB. Stops a mis-served page being parsed. */
    private const val MAX_BYTES = 24L * 1024 * 1024

    private lateinit var appContext: Context
    private lateinit var client: OkHttpClient
    private lateinit var konoha: KonohaClient
    private val lock = Mutex()

    @Volatile
    private var graph: NeighbourGraph? = null

    @Volatile
    private var checkedAt = 0L

    fun attach(context: Context, httpClient: OkHttpClient, konohaClient: KonohaClient) {
        appContext = context.applicationContext
        client = httpClient
        konoha = konohaClient
    }

    private fun file() = AtomicFile(File(appContext.filesDir, PATH))

    /**
     * The graph, from memory, then disk, then the CDN. Null only when none of the three has one —
     * a fresh install offline, or before the build has ever published.
     */
    suspend fun graph(): NeighbourGraph? = lock.withLock {
        if (!::appContext.isInitialized) return@withLock null
        withContext(Dispatchers.IO) {
            val stored = file()
            if (graph == null && stored.baseFile.exists()) {
                graph = runCatching { parse(stored.readFully()) }
                    .onFailure { DiagnosticsLog.throwable("Neighbour graph on disk unreadable", it) }
                    .getOrNull()
            }
            val now = System.currentTimeMillis()
            val age = now - stored.baseFile.lastModified()
            if ((graph == null || age > REFRESH_AFTER_MS) && now - checkedAt > CHECK_INTERVAL_MS) {
                checkedAt = now
                runCatching { download(stored) }
                    .onFailure { DiagnosticsLog.throwable("Neighbour graph download failed", it) }
            }
            graph
        }
    }

    private fun download(stored: AtomicFile) {
        val request = Request.Builder()
            .url(konoha.dataUrl(PATH))
            // jsDelivr sends max-age=604800 and the shared HTTP cache honours it, which would pin
            // a week-old graph. Revalidating is a 304 when nothing changed.
            .cacheControl(CacheControl.Builder().noCache().build())
            .build()
        val bytes = client.newCall(request).execute().use { response ->
            if (response.code == 404) return
            check(response.isSuccessful) { "Neighbour graph HTTP ${response.code}" }
            val source = checkNotNull(response.body).source()
            require(!source.request(MAX_BYTES + 1)) { "Neighbour graph too large" }
            source.readByteArray()
        }
        // Parsed before it replaces anything: a body that will not parse must not overwrite a
        // graph that does.
        val parsed = parse(bytes)
        val out = stored.startWrite()
        try {
            out.write(bytes)
            stored.finishWrite(out)
        } catch (error: Throwable) {
            stored.failWrite(out)
            throw error
        }
        graph = parsed
        DiagnosticsLog.event("Neighbour graph updated generated=${parsed.generated} titles=${parsed.size} edges=${parsed.edgeCount}")
    }

    private fun parse(gzipped: ByteArray): NeighbourGraph =
        GZIPInputStream(gzipped.inputStream()).use { NeighbourGraph.parse(it) }

    /** Everything the viewer has on a list, played here, or saved: never worth recommending. */
    private fun known(): Set<Int> = buildSet {
        addAll(LibraryStore.remoteStatuses.value.keys)
        LibraryStore.history.value.forEach { add(it.anilistId) }
        LibraryStore.watchlist.value.forEach { add(it.anilistId) }
        TasteStore.ratings.value.forEach { add(it.anilistId) }
    }

    /** Titles actually played on this device — account-seeded rows are already in the list. */
    private fun played(): List<PlayedTitle> = LibraryStore.history.value
        .filterNot { it.fromRemote }
        .map { PlayedTitle(it.anilistId, it.watchedEpisodes.size, it.updatedAt) }

    private suspend fun <T> safe(default: T, block: suspend () -> T): T = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { DiagnosticsLog.throwable("Recommendation source unavailable", e); default }

    private fun seeds(signals: List<TasteSignal>, graph: NeighbourGraph?): List<TasteSignal> =
        signals.filter { it.weight >= .35 && it.anilistId > 0 }
            .sortedByDescending { it.weight }
            .distinctBy { graph?.rootOf(it.anilistId) ?: it.anilistId }.take(2)

    /** The local graph remains the strongest signal. Live lists add independent community evidence. */
    private suspend fun hybrid(
        graph: NeighbourGraph?, local: List<Recommendation>, seeds: List<TasteSignal>, excluded: Set<Int>, limit: Int,
        negatives: List<TasteSignal> = emptyList(),
    ): List<Media> {
        fun root(id: Int) = graph?.rootOf(id) ?: id
        val excludedRoots = (excluded + seeds.map { it.anilistId }).map(::root).toSet()
        val hideAdult = SettingsStore.hideAdultContent.value
        val ranks = mutableListOf(local.map { root(it.anilistId) } to 1.6)
        val available = mutableMapOf<Int, Media>()
        val penalties = mutableMapOf<Int, Double>()
        if (graph != null) negatives.filter { it.weight < 0 }.forEach { signal ->
            graph.forEachNeighbour(signal.anilistId) { id, strength ->
                val key = root(id)
                penalties[key] = (penalties[key] ?: 0.0) + (-signal.weight * strength / 100.0)
            }
        }
        for (seed in seeds) {
            // Only selected public title IDs are sent. Full history and feedback remain on device.
            val a = safe(emptyList<Media>()) { AppGraph.repository.communityAniListRecommendations(seed.anilistId) }
            val b = safe(emptyList<Media>()) { AppGraph.repository.communityTenraiRecommendations(seed.anilistId) }
            for ((items, weight) in listOf(a to 1.0, b to .8)) {
                val eligible = items.filter { it.id > 0 && it.status != "NOT_YET_RELEASED" &&
                    it.status != "CANCELLED" && (!hideAdult || !it.isAdult) }
                eligible.forEach { available[it.id] = it }
                ranks += eligible.map { root(it.id) }.distinct() to (weight * seed.weight.coerceIn(.35, 2.0))
            }
        }
        val blocked = excludedRoots + penalties.filterValues { it >= .65 }.keys
        val ids = HybridRanking.rank(ranks, blocked, limit * 2)
            .filterNot { graph?.isUnreleased(it) == true || (hideAdult && graph?.isAdult(it) == true) }
        val missing = ids.filterNot { it in available }
        safe(emptyList<Media>()) { cards(missing) }.forEach { available[it.id] = it }
        return ids.mapNotNull { available[it] }.filter { it.status != "NOT_YET_RELEASED" &&
            it.status != "CANCELLED" && (!hideAdult || !it.isAdult) }.take(limit)
    }

    suspend fun forYou(limit: Int = 30): RecommendationRow? {
        val graph = safe<NeighbourGraph?>(null) { graph() }
        val signals = TasteEngine.signals(TasteStore.ratings.value, played(), TasteStore.feedback.value)
        val excluded = known() + signals.map { it.anilistId }
        val local = if (graph == null) emptyList() else withContext(Dispatchers.Default) {
            TasteEngine.recommend(graph, signals, excluded, hideAdult = SettingsStore.hideAdultContent.value, limit = limit * 2)
        }
        return RecommendationRow(hybrid(graph, local, seeds(signals, graph), excluded, limit, signals))
            .takeIf { it.items.isNotEmpty() }
    }

    suspend fun becauseYouWatched(limit: Int = 24): RecommendationRow? {
        val graph = safe<NeighbourGraph?>(null) { graph() }
        val ratings = TasteStore.ratings.value
        val played = played()
        val signals = TasteEngine.signals(ratings, played, TasteStore.feedback.value)
        val liked = signals.filter { it.weight > 0 }.map { it.anilistId }.toSet()
        val seed = graph?.let { TasteEngine.featuredSeed(it, ratings, played) }
            ?: ratings.filter { it.status.uppercase() in setOf("COMPLETED", "REPEATING") && it.anilistId in liked }
                .maxByOrNull { it.updatedAtMs }?.anilistId
            ?: played.filter { it.episodesWatched >= 3 && it.anilistId in liked }
                .maxByOrNull { it.updatedAtMs }?.anilistId
            ?: return null
        val excluded = known() + seed
        val local = if (graph == null) emptyList() else withContext(Dispatchers.Default) {
            TasteEngine.because(graph, seed, excluded, hideAdult = SettingsStore.hideAdultContent.value, limit = limit * 2)
        }
        val seedMedia = safe<Media?>(null) { AppGraph.repository.animeInfo(seed) } ?: return null
        val items = hybrid(graph, local, listOf(TasteSignal(seed, 1.0)), excluded, limit, signals)
        return RecommendationRow(items, seedMedia).takeIf { it.items.isNotEmpty() }
    }

    suspend fun diceCandidates(count: Int = 12): List<Media> =
        forYou(limit = DICE_POOL)?.items.orEmpty().shuffled().take(count)

    /** AniList cards for [ids], in [ids]' order; one request per 50, cached a day. */
    private suspend fun cards(ids: List<Int>): List<Media> {
        val byId = AppGraph.repository.mediaByIds(ids).associateBy { it.id }
        val hideAdult = SettingsStore.hideAdultContent.value
        return ids.mapNotNull { byId[it] }.filterNot { hideAdult && it.isAdult }
    }

    /** How often a failed or unchanged check may be retried within one process. */
    private const val CHECK_INTERVAL_MS = 15L * 60 * 1000
    private const val DICE_POOL = 60
}
