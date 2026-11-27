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
        repository.decodePage(page.raw, width, height)
    }
    override fun close() {
        closed = true
        session.close()
    }
    companion object {
        /** Opening a session requires a reviewed profile; this factory does not discover or enable hosts. */
        fun open(registry: ParserRegistry, profile: ReviewedProfile): ReaderSessionBridge {
            val session = SourceSession(profile.scope, headers = profile.headers)
            return try {
                ReaderSessionBridge(session, ParserRepository(registry, profile.scope, session))
            } catch (e: Throwable) {
                session.close()
                throw e
            }
        }
        internal fun fixture(repository: ParserRepository, session: SourceSession) = ReaderSessionBridge(session, repository)
    }
}
