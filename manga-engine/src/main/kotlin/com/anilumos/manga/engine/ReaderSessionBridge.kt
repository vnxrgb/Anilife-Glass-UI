package com.anilumos.manga.engine

import android.graphics.Bitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.Closeable

/** Internal UI-facing boundary. No direct image URLs or parser headers in presentation models.
 * Handles remain valid only for their owning reader session. Persist source + title/chapter IDs,
 * not these session objects. A reopened session must re-resolve metadata before reading. */
internal class ReaderSessionBridge private constructor(
    private val session: SourceSession,
    private val repository: ParserRepository,
) : Closeable {
    private val owner = Any()
    private val lock = Mutex()
    @Volatile private var closed = false
    private val encodedPages = LinkedHashMap<MangaPage, ByteArray>(4, .75f, true)
    private var encodedBytes = 0
    private val covers = LinkedHashMap<String, ByteArray?>(4, .75f, true)

    internal class Title internal constructor(internal val owner: Any, internal val raw: Manga) {
        val sourceId get() = raw.source.name
        val id get() = raw.id.toString()
        val title get() = raw.title
        val genres get() = raw.tags.map { it.title }.distinct().sorted()
        val authors get() = raw.authors.sorted()
        val status get() = raw.state?.name
        val alternativeTitles get() = raw.altTitles.sorted()
    }
    internal class Chapter internal constructor(internal val owner: Any, internal val title: Title, internal val raw: MangaChapter) {
        val id get() = raw.id.toString()
        val titleId get() = title.id
        val sourceId get() = title.sourceId
        val label get() = raw.title.orEmpty()
        val number get() = raw.numberString()
        val translation get() = raw.branch
        val scanlator get() = raw.scanlator
    }
    internal class Page internal constructor(internal val owner: Any, internal val raw: MangaPage, val index: Int)
    internal data class Listing(val titles: List<Title>, val consumed: Int, val nextOffset: Int?)
    internal data class Details(val title: Title, val chapters: List<Chapter>, val description: String)

    private fun active() { check(!closed) { "Reader session closed" } }
    private fun owned(token: Any) { active(); require(token === owner) { "Handle belongs to another reader session" } }

    suspend fun browse(query: String, offset: Int, order: SortOrder): Listing = lock.withLock {
        active()
        val result = repository.browse(query, offset, order)
        Listing(result.titles.map { Title(owner, it) }, result.consumed, result.nextOffset)
    }
    suspend fun catalogueBrowse(query: String, offset: Int, recent: Boolean, genre: String?, kind: String?): Listing = lock.withLock {
        active()
        val result = repository.catalogueBrowse(query, offset, recent, genre, kind)
        Listing(result.titles.map { Title(owner, it) }, result.consumed, result.nextOffset)
    }
    suspend fun catalogueGenres(): List<String> = lock.withLock {
        active()
        repository.catalogueOptions().availableTags.map { it.title }.distinct().sorted()
    }
    suspend fun catalogueKinds(): List<String> = lock.withLock {
        active()
        repository.catalogueOptions().availableContentTypes.map { it.name }.filter { it in setOf("MANGA", "MANHWA", "MANHUA") }
    }
    suspend fun restoreTitle(id: String, name: String): Title? = lock.withLock {
        active()
        val result = repository.browse(name, 0, SortOrder.POPULARITY)
        result.titles.firstOrNull { it.id.toString() == id }?.let { Title(owner, it) }
    }
    suspend fun approvedDetails(query: String, chapterNumber: Float): Details = lock.withLock {
        active()
        val selection = repository.approvedTitle(query)
        val raw = selection.title ?: error("Approved title unavailable: ${selection.state}")
        val detailed = Title(owner, repository.details(raw))
        val chapter = detailed.raw.chapters.orEmpty().filter { it.number == chapterNumber }.distinctBy { it.id }.singleOrNull()
            ?: error("Approved chapter missing or ambiguous")
        Details(detailed, listOf(Chapter(owner, detailed, chapter)), detailed.raw.description.orEmpty())
    }
    suspend fun grantedDetails(grant: ReviewedTitleGrant): Details = lock.withLock {
        active()
        val selected = repository.approvedTitle(grant.name).title ?: error("Reviewed title unavailable")
        val detailed = Title(owner, repository.details(selected))
        val allowed = permittedChapters(grant, detailed.raw.chapters.orEmpty().map { it.number })
        val chapters = detailed.raw.chapters.orEmpty().filter { it.number in allowed }
            .groupBy { it.number }.values.mapNotNull { entries -> entries.distinctBy { it.id }.singleOrNull() }
        Details(detailed, chapters.map { Chapter(owner, detailed, it) }, detailed.raw.description.orEmpty())
    }
    suspend fun details(title: Title): Details = lock.withLock {
        owned(title.owner)
        val detailed = Title(owner, repository.details(title.raw))
        Details(detailed, detailed.raw.chapters.orEmpty().map { Chapter(owner, detailed, it) }, detailed.raw.description.orEmpty())
    }
    suspend fun pages(chapter: Chapter): List<Page> = lock.withLock {
        owned(chapter.owner)
        repository.pages(chapter.title.raw, chapter.raw).mapIndexed { index, page -> Page(owner, page, index) }
    }
    /** Caller owns the bounded decoded bitmap; the Compose reader must recycle it on disposal. */
    suspend fun decode(page: Page, width: Int = 2048, height: Int = 4096): Bitmap = lock.withLock {
        owned(page.owner)
        val bytes = encodedPages[page.raw] ?: repository.loadImage(page.raw).also { fresh ->
            if (!closed && fresh.size <= 12 * 1024 * 1024) {
                while (encodedPages.isNotEmpty() && (encodedPages.size >= 3 || encodedBytes + fresh.size > 12 * 1024 * 1024)) {
                    val key = encodedPages.keys.first()
                    encodedBytes -= encodedPages.remove(key)!!.size
                }
                encodedPages[page.raw] = fresh
                encodedBytes += fresh.size
            }
        }
        val bitmap = PageDecoder.decode(bytes, width, height)
        if (closed) { bitmap.recycle(); error("Reader session closed") }
        bitmap
    }
    suspend fun cover(title: Title): Bitmap? = lock.withLock {
        owned(title.owner)
        if (!covers.containsKey(title.id)) {
            val bytes = try { repository.coverBytes(title.raw)?.takeIf { it.size <= 4 * 1024 * 1024 } }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { null }
            if (covers.size >= 3) covers.remove(covers.keys.first())
            covers[title.id] = bytes
        }
        val bytes = covers[title.id] ?: return@withLock null
        val bitmap = PageDecoder.decode(bytes, 768, 1152)
        if (closed) { bitmap.recycle(); error("Reader session closed") }
        bitmap
    }
    override fun close() {
        closed = true
        session.close()
    }
    companion object {
        /** Opening a session requires a reviewed profile; this factory does not discover or enable hosts. */
        fun open(registry: ParserRegistry, profile: ReviewedProfile): ReaderSessionBridge {
            return open(registry, profile.scope, profile.headers)
        }
        fun open(registry: ParserRegistry, scope: SourceScope, headers: SourceHeaders = SourceHeaders(scope)): ReaderSessionBridge {
            val session = SourceSession(scope, headers = headers)
            return try {
                ReaderSessionBridge(session, ParserRepository(registry, scope, session))
            } catch (e: Throwable) {
                session.close()
                throw e
            }
        }
        internal fun fixture(repository: ParserRepository, session: SourceSession) = ReaderSessionBridge(session, repository)
    }
}
