package com.anilili.manga

import com.anilumos.manga.engine.ReviewedCatalog
import java.io.Closeable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun reviewedReaderSupported(): Boolean = true
internal fun reviewedReaderProviders(): List<ReviewedReaderProvider> = ReviewedCatalog.providers().map { provider ->
    ReviewedReaderProvider(reviewedSourceId(provider.id), provider.label) { EngineCatalogSource(provider.id, provider.label, ReviewedCatalog.open(provider.id)) }
}
private fun reviewedSourceId(id: String) = if (id == "ATSUMOE") REVIEWED_SOURCE_ID else "engine:" + id
internal suspend fun openReviewedReader(): MangaSource = reviewedReaderProviders().single { it.id == REVIEWED_SOURCE_ID }.open()

private class EngineCatalogSource(provider: String, override val displayName: String, private val catalog: ReviewedCatalog) : MangaSource, Closeable {
    override val id = reviewedSourceId(provider)
    override val languageTag = "en"
    override val sampleOnly = true
    override val chapterLanguages = listOf("und")
    private val covers = LinkedHashMap<String, android.graphics.Bitmap?>(4, .75f, true)
    private val lock = Mutex()
    override suspend fun artwork(mangaId: String): Any? = lock.withLock {
        if (!covers.containsKey(mangaId)) {
            if (covers.size >= 3) covers.remove(covers.keys.first())
            covers[mangaId] = catalog.cover(mangaId)
        }
        covers[mangaId]
    }
    private fun convert(title: ReviewedCatalog.Title) = MangaTitle(id, title.id, title.name,
        cleanMangaDescription(androidx.core.text.HtmlCompat.fromHtml(title.description, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString()), null,
        genres = title.genres, authors = title.authors, status = title.status, alternativeTitles = title.alternatives)
    override suspend fun search(query: String, limit: Int): List<MangaTitle> =
        catalogPage(query, MangaKind.ALL, MangaOrder.POPULAR, null, 0, limit.coerceIn(1, 30)).items
    override suspend fun catalogPage(query: String, kind: MangaKind, order: MangaOrder, genre: String?, offset: Int, limit: Int): MangaCatalogPage {
        val result = catalog.list(query.take(200), offset, limit, genre)
        return MangaCatalogPage(result.items.map(::convert), result.nextOffset)
    }
    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> =
        catalog.chapters(mangaId).map { MangaChapter(id, it.id, it.titleId, it.label, it.number, "und", listOfNotNull(it.scanlator)) }
    override suspend fun pages(chapterId: String): List<MangaPage> =
        List(catalog.pageCount(chapterId)) { index -> MangaPage(index, "", decode = { w, h -> catalog.decode(chapterId, index, w, h) }) }
    override fun close() = catalog.close()
}
