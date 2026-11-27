package com.anilili.manga

import com.anilumos.manga.engine.ReviewedCatalog
import com.anilumos.manga.engine.LocalScriptRuntime
import android.content.Context
import java.io.Closeable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun reviewedReaderSupported(): Boolean = true
internal suspend fun reviewedReaderProviders(context: Context): List<ReviewedReaderProvider> {
    LocalScriptRuntime.initialize(context)
    val revision = ReviewedCatalog.compatibilityRevision()
    return ReviewedCatalog.providers().map { provider ->
        val factory = { EngineCatalogSource(provider.id, provider.label, provider.locale, provider.category) }
        ReviewedReaderProvider(reviewedSourceId(provider.id), provider.label, revision, factory) {
            ReviewedCatalog.open(provider.id).use { }
            factory()
        }
    }
}
private fun reviewedSourceId(id: String) = if (id == "ATSUMOE") REVIEWED_SOURCE_ID else "engine:" + id
internal suspend fun openReviewedReader(): MangaSource {
    val provider = ReviewedCatalog.providers().single { it.id == "ATSUMOE" }
    ReviewedCatalog.open(provider.id).use { }
    return EngineCatalogSource(provider.id, provider.label, provider.locale, provider.category)
}

private class EngineCatalogSource(private val provider: String, override val displayName: String, override val languageTag: String, override val sourceCategory: String) : MangaSource, Closeable {
    override val id = reviewedSourceId(provider)
    @Volatile private var catalog: ReviewedCatalog? = null
    private val sessionLock = Mutex()
    @Volatile private var closed = false
    private suspend fun session(): ReviewedCatalog = sessionLock.withLock {
        check(!closed) { "Source session closed" }
        catalog ?: ReviewedCatalog.connect(provider).also { opened ->
            if (closed) { opened.close(); error("Source session closed") }
            catalog = opened
        }
    }
    override val hasNativeCatalogue = true
    override suspend fun genreOptions(): List<String> = session().genres()
    override suspend fun kindOptions(): List<MangaKind> = session().kinds().mapNotNull { value -> MangaKind.entries.firstOrNull { it.name == value } }
    override suspend fun prepareTitle(title: MangaTitle): MangaTitle {
        check(session().restore(title.id, title.title)) { "Saved title could not be resolved" }
        return session().detail(title.id)?.let(::convert) ?: title
    }
    override val chapterLanguages = listOf("und")
    private val covers = LinkedHashMap<String, android.graphics.Bitmap?>(4, .75f, true)
    private val lock = Mutex()
    override suspend fun artwork(mangaId: String): Any? = lock.withLock {
        if (!covers.containsKey(mangaId)) {
            if (covers.size >= 3) covers.remove(covers.keys.first())
            covers[mangaId] = session().cover(mangaId)
        }
        covers[mangaId]
    }
    private fun convert(title: ReviewedCatalog.Title) = MangaTitle(id, title.id, title.name,
        cleanMangaDescription(androidx.core.text.HtmlCompat.fromHtml(title.description, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString()), null,
        genres = title.genres, authors = title.authors, status = title.status, alternativeTitles = title.alternatives)
    override suspend fun search(query: String, limit: Int): List<MangaTitle> =
        catalogPage(query, MangaKind.ALL, MangaOrder.POPULAR, null, 0, limit.coerceIn(1, 30)).items
    override suspend fun catalogPage(query: String, kind: MangaKind, order: MangaOrder, genre: String?, offset: Int, limit: Int): MangaCatalogPage {
        val result = session().list(query.take(200), offset, limit, genre, order == MangaOrder.RECENT, kind.name.takeUnless { kind == MangaKind.ALL || kind == MangaKind.OTHER })
        return MangaCatalogPage(result.items.map { convert(it).copy(kind = if (kind == MangaKind.ALL) MangaKind.OTHER else kind) }, result.nextOffset)
    }
    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> =
        session().chapters(mangaId).map { MangaChapter(id, it.id, it.titleId, it.label, it.number, "und", listOfNotNull(it.scanlator)) }
    override suspend fun pages(chapterId: String): List<MangaPage> =
        List(session().pageCount(chapterId)) { index -> MangaPage(index, "", decode = { w, h -> session().decode(chapterId, index, w, h) }) }
    override fun close() { closed = true; catalog?.close(); catalog = null }
}
