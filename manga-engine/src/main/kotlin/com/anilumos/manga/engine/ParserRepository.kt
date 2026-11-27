package com.anilumos.manga.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder

/** Raw offset belongs to the provider result, not the number of displayed safe titles. */
internal data class CatalogPage(val titles: List<Manga>, val consumed: Int, val nextOffset: Int?)
internal fun catalogPage(raw: List<Manga>, offset: Int): CatalogPage {
    require(offset >= 0)
    return CatalogPage(raw.filter { it.contentRating == ContentRating.SAFE }, raw.size,
        if (raw.isEmpty()) null else Math.addExact(offset, raw.size))
}

/** A repository for a specifically reviewed source. No UI dependency and no provider-wide failure gate. */
internal class ParserRepository(
    registry: ParserRegistry,
    private val scope: SourceScope,
    private val session: SourceSession,
) {
    private val parser = registry.bundle.newParser(registry.requireSource(scope), AndroidParserContext(registry.bundle, session))
    init { scope.requireUrl("https://" + parser.domain + "/") }
    suspend fun browse(query: String, offset: Int, requested: SortOrder): CatalogPage = withContext(Dispatchers.IO) {
        require(offset >= 0)
        val clean = query.trim()
        if (clean.isNotEmpty()) if (!parser.filterCapabilities.isSearchSupported) throw UnsupportedOperationException("Search unsupported")
        val filter = MangaListFilter(query = clean.ifEmpty { null })
        val order = requested.takeIf { it in parser.availableSortOrders } ?: parser.availableSortOrders.first()
        val raw = parser.getList(offset, order, filter)
        check(raw.all { it.source.name == scope.sourceName }) { "Unexpected catalog source" }
        catalogPage(raw, offset)
    }
    suspend fun details(title: Manga): Manga = withContext(Dispatchers.IO) {
        require(title.source.name == scope.sourceName && title.contentRating == ContentRating.SAFE)
        val result = parser.getDetails(title)
        check(result.source.name == scope.sourceName && result.id == title.id && result.url == title.url)
        check((result.contentRating ?: title.contentRating) == ContentRating.SAFE)
        result.copy(contentRating = result.contentRating ?: title.contentRating)
    }
    suspend fun pages(title: Manga, chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        require(title.source.name == scope.sourceName && title.contentRating == ContentRating.SAFE)
        require(chapter.source.name == scope.sourceName && title.chapters.orEmpty().any { it == chapter })
        parser.getPages(chapter).also { pages -> check(pages.all { it.source.name == scope.sourceName }) }
    }
    suspend fun decodePage(page: MangaPage, width: Int = 2048, height: Int = 4096) =
        PageDecoder.decode(loadImage(page), width, height)
    suspend fun loadImage(page: MangaPage): ByteArray = PageDelivery(scope, session).load(imageUrl(page))
    suspend fun imageUrl(page: MangaPage): String = withContext(Dispatchers.IO) {
        require(page.source.name == scope.sourceName)
        scope.requireUrl(parser.getPageUrl(page), "https://" + parser.domain + "/").toString()
    }
}
