package com.anilumos.manga.engine

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import org.koitharu.kotatsu.parsers.model.SortOrder

internal data class ReviewedTitleGrant(val name: String, val chapters: Set<Float>) {
    init { require(name.isNotBlank() && chapters.all { it.isFinite() && it >= 0f }) }
}
internal data class ReviewedCatalogGrant(val profile: ReviewedProfile, val titles: List<ReviewedTitleGrant>) {
    init {
        require(titles.isNotEmpty() && titles.size <= 100)
        require(titles.map { it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == titles.size)
        require(titles.any { it.name.equals(profile.query, true) && profile.chapterNumber in it.chapters })
    }
}
internal object ReviewedCatalogGrants {
    // Deliberately no wildcard chapter grants. Code-license permission is not publication permission.
    val all = listOf(ReviewedCatalogGrant(
        ReviewedProfile(SourceScope("ATSUMOE", setOf("atsu.moe", "cdn.atsu.moe")), "Chico", 3f),
        listOf(ReviewedTitleGrant("Chico", setOf(3f)), ReviewedTitleGrant("Lookism", emptySet()))
    ))
}
internal fun permittedChapters(grant: ReviewedTitleGrant, chapterNumbers: List<Float>): Set<Float> =
    chapterNumbers.filter { it in grant.chapters }.toSet()

/** Public presentation models are detached from parser-specific objects and transport URLs.
 * Registry entries are selected by compiled review ID, never by arbitrary host or parser name. */
public class ReviewedCatalog private constructor(
    private val grant: ReviewedCatalogGrant,
    private val bridge: ReaderSessionBridge,
    initial: ReaderSessionBridge.Details,
) : Closeable {
    public data class Provider(val id: String, val label: String)
    public data class Title(val id: String, val name: String, val description: String, val genres: List<String>,
        val authors: List<String>, val status: String, val alternatives: List<String>, val hasReadingGrant: Boolean)
    public data class Chapter(val id: String, val titleId: String, val label: String, val number: String?, val scanlator: String?)
    public data class Listing(val items: List<Title>, val nextOffset: Int?)
    private val gate = Mutex()
    private val details = LinkedHashMap<String, ReaderSessionBridge.Details>()
    private val chapterHandles = LinkedHashMap<String, ReaderSessionBridge.Chapter>()
    private val pageHandles = LinkedHashMap<String, List<ReaderSessionBridge.Page>>(4, .75f, true)
    @Volatile private var closed = false
    init { remember(initial) }
    private fun active() { check(!closed) { "Reviewed catalogue closed" } }
    private fun remember(entry: ReaderSessionBridge.Details) {
        details[entry.title.id] = entry
        entry.chapters.forEach { chapterHandles[it.id] = it }
    }
    private fun present(entry: ReaderSessionBridge.Details) = entry.title.let {
        Title(it.id, it.title, entry.description, it.genres, it.authors, it.status.orEmpty(), it.alternativeTitles, entry.chapters.isNotEmpty())
    }
    public suspend fun list(query: String, offset: Int, limit: Int, genre: String? = null): Listing = gate.withLock {
        active()
        require(offset >= 0 && limit in 1..30 && query.length <= 200)
        // Explicitly approved titles only. Resolve lazily; no scans of the unapproved catalogue.
        val matches = grant.titles.filter { it.name.contains(query.trim(), true) }
        val candidates = matches.drop(offset).take(limit)
        val result = candidates.mapNotNull { titleGrant ->
            details.values.firstOrNull { it.title.title.equals(titleGrant.name, true) } ?: try {
                bridge.grantedDetails(titleGrant).also(::remember)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { null }
        }.map(::present).filter { genre == null || genre in it.genres }
        Listing(result, (offset + candidates.size).takeIf { it < matches.size })
    }
    public suspend fun chapters(titleId: String): List<Chapter> = gate.withLock {
        active()
        val entry = details[titleId] ?: return@withLock emptyList()
        entry.chapters.map { Chapter(it.id, it.titleId, it.label, it.number, it.scanlator) }
    }
    public suspend fun pageCount(chapterId: String): Int = gate.withLock {
        active()
        val chapter = chapterHandles[chapterId] ?: error("Chapter not in reviewed set")
        val pages = pageHandles[chapterId] ?: bridge.pages(chapter).also {
            require(it.isNotEmpty() && it.size <= 2000)
            if (pageHandles.size >= 3) pageHandles.remove(pageHandles.keys.first())
            pageHandles[chapterId] = it
        }
        pages.size
    }
    public suspend fun decode(chapterId: String, index: Int, width: Int, height: Int): Bitmap = gate.withLock {
        active()
        require(width in 1..2048 && height in 1..4096)
        val chapter = chapterHandles[chapterId] ?: error("Chapter not in reviewed set")
        val pages = pageHandles[chapterId] ?: bridge.pages(chapter).also {
            require(it.isNotEmpty() && it.size <= 2000)
            if (pageHandles.size >= 3) pageHandles.remove(pageHandles.keys.first())
            pageHandles[chapterId] = it
        }
        require(index in pages.indices)
        bridge.decode(pages[index], width, height)
    }
    public suspend fun cover(titleId: String): Bitmap? = gate.withLock {
        active()
        val title = details[titleId]?.title ?: return@withLock null
        // Metadata-only grants do not include artwork retrieval.
        if (details[titleId]?.chapters.isNullOrEmpty()) return@withLock null
        bridge.cover(title)
    }
    override fun close() { closed = true; bridge.close() }
    public companion object {
        public fun providers(): List<Provider> = ReviewedCatalogGrants.all.map {
            Provider(it.profile.scope.sourceName, when (it.profile.scope.sourceName) { "ATSUMOE" -> "Atsu"; else -> it.profile.scope.sourceName })
        }
        public suspend fun open(providerId: String): ReviewedCatalog = withContext(Dispatchers.IO) {
            val grant = ReviewedCatalogGrants.all.singleOrNull { it.profile.scope.sourceName == providerId }
                ?: error("No reviewed grant for provider")
            val bridge = ReaderSessionBridge.open(ParserRegistry(), grant.profile)
            try {
                val seed = grant.titles.single { it.name.equals(grant.profile.query, true) }
                val initial = bridge.grantedDetails(seed)
                val chapter = initial.chapters.singleOrNull { it.raw.number == grant.profile.chapterNumber }
                    ?: error("Reviewed probe chapter unavailable")
                val pages = bridge.pages(chapter)
                require(pages.isNotEmpty() && pages.size <= 2000)
                bridge.decode(pages.first()).recycle()
                ReviewedCatalog(grant, bridge, initial)
            } catch (e: Throwable) { bridge.close(); throw e }
        }
    }
}
