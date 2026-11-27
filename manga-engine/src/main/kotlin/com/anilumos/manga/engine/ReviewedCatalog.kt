package com.anilumos.manga.engine

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException

// Historical restricted fixture helpers are retained for regression tests, not live access.
internal data class ReviewedTitleGrant(val name: String, val chapters: Set<Float>) {
    init { require(name.isNotBlank() && chapters.all { it.isFinite() && it >= 0f }) }
}
internal fun permittedChapters(grant: ReviewedTitleGrant, chapterNumbers: List<Float>): Set<Float> =
    chapterNumbers.filter { it in grant.chapters }.toSet()

internal data class LiveProviderProfile(val scope: SourceScope, val label: String, val locale: String = "", val category: String = "MANGA", val probeTitle: String? = null, val probeChapter: Float? = null)

/** Owner confirmed provider-wide content permission on 2026-11-27.
 * Discover technical candidates from the pinned parser bundle without network I/O.
 * Known multi-host providers retain narrowly scoped CDN overrides. */
internal object LiveProviderProfiles {
    private val known = listOf(
        LiveProviderProfile(SourceScope("ATSUMOE", setOf("atsu.moe", "cdn.atsu.moe")), "Atsu", "en", probeTitle = "Chico", probeChapter = 3f),
        LiveProviderProfile(SourceScope("MANGADEX", setOf("mangadex.org", "api.mangadex.org", "uploads.mangadex.org", "mangadex.network"), setOf("mangadex.network")), "MangaDex"),
        LiveProviderProfile(SourceScope("COMICK_FUN", setOf("comick.live", "comick.art")), "ComicK")
    ).associateBy { it.scope.sourceName }
    private val discovered by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { discover(ParserRegistry()) }
    val all: List<LiveProviderProfile> get() = discovered
    internal fun domain(value: String): String? {
        val normalized = value.trim().lowercase(java.util.Locale.ROOT)
        if (normalized.length !in 4..253 || !normalized.contains('.') ||
            normalized.endsWith(".local") || normalized.endsWith(".localhost") ||
            normalized.endsWith(".invalid") || normalized.endsWith(".test") ||
            normalized.endsWith(".internal") || normalized.split('.').last().all { it.isDigit() }) return null
        if (!normalized.split('.').all { label -> label.length in 1..63 &&
                label.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) }) return null
        return normalized
    }
    internal fun discover(registry: ParserRegistry): List<LiveProviderProfile> =
        registry.descriptors().asSequence()
            .filter { !it.markedBroken && it.category in ParserRegistry.supportedCategories }
            .mapNotNull { descriptor ->
                val override = known[descriptor.id]
                if (override != null) return@mapNotNull override.copy(locale = descriptor.locale, category = descriptor.category)
                try {
                    val offline = SourceScope(descriptor.id, setOf("offline.invalid"))
                    SourceSession(offline, offline = true).use { session ->
                        val parser = registry.bundle.newParser(registry.requireSource(offline), AndroidParserContext(registry.bundle, session))
                        val primary = domain(parser.domain) ?: return@use null
                        val hosts = (parser.configKeyDomain.presetValues.mapNotNull(::domain) + primary).toSet()
                        LiveProviderProfile(SourceScope(descriptor.id, hosts), descriptor.title, descriptor.locale, descriptor.category)
                    }
                } catch (e: LinkageError) { null }
                catch (e: Exception) { null }
            }.sortedWith(compareBy<LiveProviderProfile> {
                when (it.scope.sourceName) { "ATSUMOE" -> 0; "MANGADEX" -> 1; "COMICK_FUN" -> 2; else -> if (it.locale.substringBefore('-').substringBefore('_') == "en") 3 else 4 }
            }.thenBy { it.label }.thenBy { it.scope.sourceName }).toList()
}

/** Generic provider session. Public metadata stays separate from raw URLs and credentials. */
public class ReviewedCatalog private constructor(
    private val bridge: ReaderSessionBridge,
    initial: ReaderSessionBridge.Details? = null,
) : Closeable {
    public data class Provider(val id: String, val label: String, val locale: String, val category: String)
    public data class Title(val id: String, val name: String, val description: String, val genres: List<String>,
        val authors: List<String>, val status: String, val alternatives: List<String>, val hasReadingGrant: Boolean)
    public data class Chapter(val id: String, val titleId: String, val label: String, val number: String?, val scanlator: String?)
    public data class Listing(val items: List<Title>, val nextOffset: Int?)
    private val gate = Mutex()
    private val titles = LinkedHashMap<String, ReaderSessionBridge.Title>(64, .75f, true)
    private val details = LinkedHashMap<String, ReaderSessionBridge.Details>(16, .75f, true)
    private val chapterHandles = LinkedHashMap<String, ReaderSessionBridge.Chapter>()
    private val pageHandles = LinkedHashMap<String, List<ReaderSessionBridge.Page>>(4, .75f, true)
    @Volatile private var closed = false
    init { initial?.let(::remember) }
    private fun active() { check(!closed) { "Catalogue closed" } }
    private fun rememberTitle(title: ReaderSessionBridge.Title) {
        titles[title.id] = title
        while (titles.size > 500) titles.remove(titles.keys.first())
    }
    private fun remember(entry: ReaderSessionBridge.Details) {
        rememberTitle(entry.title)
        details[entry.title.id] = entry
        entry.chapters.forEach { chapterHandles[it.id] = it }
        while (details.size > 8) {
            val evicted = details.remove(details.keys.first()) ?: continue
            evicted.chapters.forEach { chapterHandles.remove(it.id); pageHandles.remove(it.id) }
        }
    }
    private fun present(title: ReaderSessionBridge.Title) = Title(title.id, title.title, title.raw.description.orEmpty(), title.genres,
        title.authors, title.status.orEmpty(), title.alternativeTitles, true)
    public suspend fun genres(): List<String> = gate.withLock { active(); bridge.catalogueGenres() }
    public suspend fun kinds(): List<String> = gate.withLock { active(); bridge.catalogueKinds() }
    public suspend fun list(query: String, offset: Int, limit: Int, genre: String? = null, recent: Boolean = false, kind: String? = null): Listing = gate.withLock {
        active()
        require(offset >= 0 && limit in 1..60 && query.length <= 200)
        val result = bridge.catalogueBrowse(query.trim(), offset, recent, genre, kind)
        // Do not truncate the provider page: its raw consumed count drives pagination.
        require(result.titles.size <= 500) { "Catalogue page exceeds supported size" }
        result.titles.forEach(::rememberTitle)
        Listing(result.titles.distinctBy { it.id }.map(::present), result.nextOffset)
    }
    public suspend fun restore(titleId: String, name: String): Boolean = gate.withLock {
        active()
        if (titles.containsKey(titleId)) return@withLock true
        val restored = bridge.restoreTitle(titleId, name.take(200)) ?: return@withLock false
        rememberTitle(restored)
        true
    }
    public suspend fun detail(titleId: String): Title? = gate.withLock {
        active()
        val title = titles[titleId] ?: return@withLock null
        val entry = details[titleId] ?: bridge.details(title).also(::remember)
        present(entry.title).copy(description = entry.description, hasReadingGrant = entry.chapters.isNotEmpty())
    }
    public suspend fun chapters(titleId: String): List<Chapter> = gate.withLock {
        active()
        val title = titles[titleId] ?: error("Title not in current catalogue")
        val entry = details[titleId] ?: bridge.details(title).also(::remember)
        entry.chapters.distinctBy { it.id }.map { Chapter(it.id, it.titleId, it.label, it.number, it.scanlator) }
    }
    private suspend fun pagesLocked(chapterId: String): List<ReaderSessionBridge.Page> {
        val chapter = chapterHandles[chapterId] ?: error("Chapter not in current catalogue")
        return pageHandles[chapterId] ?: bridge.pages(chapter).also {
            require(it.isNotEmpty() && it.size <= 2000)
            if (pageHandles.size >= 3) pageHandles.remove(pageHandles.keys.first())
            pageHandles[chapterId] = it
        }
    }
    public suspend fun pageCount(chapterId: String): Int = gate.withLock { active(); pagesLocked(chapterId).size }
    public suspend fun decode(chapterId: String, index: Int, width: Int, height: Int): Bitmap = gate.withLock {
        active()
        require(width in 1..2048 && height in 1..4096)
        val pages = pagesLocked(chapterId)
        require(index in pages.indices)
        bridge.decode(pages[index], width, height)
    }
    public suspend fun cover(titleId: String): Bitmap? = gate.withLock {
        active()
        val title = titles[titleId] ?: return@withLock null
        bridge.cover(title)
    }
    override fun close() { closed = true; bridge.close() }
    public companion object {
        public suspend fun providers(): List<Provider> = withContext(Dispatchers.IO) {
            LiveProviderProfiles.all.map { Provider(it.scope.sourceName, it.label, it.locale, it.category) }
        }
        public suspend fun connect(providerId: String): ReviewedCatalog {
            var opened: ReviewedCatalog? = null
            try {
                return withContext(Dispatchers.IO) {
                    val profile = LiveProviderProfiles.all.singleOrNull { it.scope.sourceName == providerId }
                        ?: error("Provider is not configured")
                    ReviewedCatalog(ReaderSessionBridge.open(ParserRegistry(), profile.scope)).also { opened = it }
                }
            } catch (e: Throwable) { opened?.close(); throw e }
        }
        public suspend fun open(providerId: String): ReviewedCatalog {
            var opened: ReviewedCatalog? = null
            try { return withContext(Dispatchers.IO) {
            val profile = LiveProviderProfiles.all.singleOrNull { it.scope.sourceName == providerId }
                ?: error("Provider has not been configured")
            val bridge = ReaderSessionBridge.open(ParserRegistry(), profile.scope)
            try {
                // Known seed for Atsu, bounded catalogue/real page probe for other providers.
                val candidates = if (profile.probeTitle != null) {
                    bridge.catalogueBrowse(profile.probeTitle, 0, false, null, null).titles
                        .filter { it.title.equals(profile.probeTitle, true) }.take(1)
                } else bridge.catalogueBrowse("", 0, false, null, null).titles.take(3)
                for (title in candidates) {
                    try {
                        val detail = bridge.details(title)
                        val chapter = if (profile.probeChapter != null) detail.chapters.firstOrNull { it.raw.number == profile.probeChapter }
                            else detail.chapters.firstOrNull()
                        if (chapter == null) continue
                        val pages = bridge.pages(chapter)
                        if (pages.isEmpty() || pages.size > 2000) continue
                        bridge.decode(pages.first(), 320, 480).recycle()
                        return@withContext ReviewedCatalog(bridge, detail).also { opened = it }
                    } catch (e: CancellationException) { throw e }
                    catch (e: ProviderAccessException) { throw e }
                    catch (e: Exception) { continue }
                }
                throw IOException("Provider did not pass catalogue/chapter/image verification")
            } catch (e: Throwable) { bridge.close(); throw e }
            } } catch (e: Throwable) { opened?.close(); throw e }
        }
    }
}
