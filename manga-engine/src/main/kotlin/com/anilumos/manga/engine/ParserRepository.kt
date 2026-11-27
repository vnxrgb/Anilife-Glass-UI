package com.anilumos.manga.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder

/** Raw offset belongs to the provider result, not the number of displayed permitted titles. */
internal data class CatalogPage(val titles: List<Manga>, val consumed: Int, val nextOffset: Int?)
internal fun catalogPage(raw: List<Manga>, offset: Int): CatalogPage {
    require(offset >= 0)
    return CatalogPage(raw.filter { PrimaryProviderProfiles.permitsRating(it.contentRating) }, raw.size,
        if (raw.isEmpty()) null else Math.addExact(offset, raw.size))
}

/** A repository for a specifically reviewed source. No UI dependency and no provider-wide failure gate. */
internal class ParserRepository(
    registry: ParserRegistry,
    private val scope: SourceScope,
    private val session: SourceSession,
    config: ReviewedSourceConfig = ReviewedSourceConfig(),
) {
    private val parser = registry.bundle.newParser(registry.requireSource(scope), AndroidParserContext(registry.bundle, session, config))
    init { scope.requireUrl("https://" + parser.domain + "/") }
    val supportsSearch: Boolean get() = parser.filterCapabilities.isSearchSupported
    suspend fun approvedTitle(query: String): ApprovedTitleResult {
        val catalog = browse("", 0, SortOrder.POPULARITY)
        return selectApprovedTitle(query, supportsSearch, catalog) { browse(it, 0, SortOrder.RELEVANCE) }
    }
    suspend fun browse(query: String, offset: Int, requested: SortOrder): CatalogPage = withContext(Dispatchers.IO) {
        require(offset >= 0)
        val clean = query.trim()
        if (clean.isNotEmpty()) if (!supportsSearch) throw UnsupportedOperationException("Search unsupported")
        val filter = MangaListFilter(query = clean.ifEmpty { null })
        val order = requested.takeIf { it in parser.availableSortOrders } ?: parser.availableSortOrders.first()
        val raw = parser.getList(offset, order, PrimaryProviderProfiles.restrictFilter(scope.sourceName, filter))
        check(raw.all { it.source.name == scope.sourceName }) { "Unexpected catalog source" }
        catalogPage(raw, offset)
    }
    private var cachedOptions: org.koitharu.kotatsu.parsers.model.MangaListFilterOptions? = null
    suspend fun catalogueOptions(): org.koitharu.kotatsu.parsers.model.MangaListFilterOptions =
        cachedOptions ?: filterOptions().also { cachedOptions = it }
    suspend fun catalogueBrowse(query: String, offset: Int, recent: Boolean, genre: String?, kind: String?): CatalogPage {
        val requested = if (query.isNotBlank() && SortOrder.RELEVANCE in parser.availableSortOrders) SortOrder.RELEVANCE else if (recent) SortOrder.UPDATED else SortOrder.POPULARITY
        if (genre == null && kind == null) return browse(query, offset, requested)
        val options = catalogueOptions()
        val tags = if (genre == null) emptySet() else setOf(options.availableTags.firstOrNull { it.title.equals(genre, true) }
            ?: throw UnsupportedOperationException("Genre unavailable"))
        val types = if (kind == null) emptySet() else setOf(options.availableContentTypes.firstOrNull { it.name == kind }
            ?: throw UnsupportedOperationException("Content type unavailable"))
        return browseFiltered(query, offset, requested, tags = tags, types = types)
    }
    suspend fun filterOptions(): org.koitharu.kotatsu.parsers.model.MangaListFilterOptions = withContext(Dispatchers.IO) {
        parser.getFilterOptions().also { options ->
            check(options.availableTags.all { it.source.name == scope.sourceName }) { "Unexpected genre source" }
        }
    }
    suspend fun browseFiltered(
        query: String, offset: Int, requested: SortOrder,
        tags: Set<org.koitharu.kotatsu.parsers.model.MangaTag> = emptySet(),
        excluded: Set<org.koitharu.kotatsu.parsers.model.MangaTag> = emptySet(),
        types: Set<org.koitharu.kotatsu.parsers.model.ContentType> = emptySet(),
        states: Set<org.koitharu.kotatsu.parsers.model.MangaState> = emptySet(),
    ): CatalogPage = withContext(Dispatchers.IO) {
        require(offset >= 0)
        val filter = reviewedFilter(scope.sourceName, query, tags, excluded, types, states,
            parser.filterCapabilities, catalogueOptions())
        if (requested !in parser.availableSortOrders) throw UnsupportedOperationException("Sort unsupported")
        val raw = parser.getList(offset, requested, PrimaryProviderProfiles.restrictFilter(scope.sourceName, filter))
        check(raw.all { it.source.name == scope.sourceName })
        catalogPage(raw, offset)
    }
    suspend fun details(title: Manga): Manga = withContext(Dispatchers.IO) {
        require(title.source.name == scope.sourceName && PrimaryProviderProfiles.permitsRating(title.contentRating))
        val result = parser.getDetails(title)
        check(result.source.name == scope.sourceName && result.id == title.id && result.url == title.url)
        check(PrimaryProviderProfiles.permitsRating(result.contentRating ?: title.contentRating))
        result.copy(contentRating = result.contentRating ?: title.contentRating)
    }
    suspend fun pages(title: Manga, chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        require(title.source.name == scope.sourceName && PrimaryProviderProfiles.permitsRating(title.contentRating))
        require(chapter.source.name == scope.sourceName && title.chapters.orEmpty().any { it == chapter })
        parser.getPages(chapter).also { pages -> check(pages.all { it.source.name == scope.sourceName }) }
    }
    suspend fun coverBytes(title: Manga): ByteArray? {
        require(title.source.name == scope.sourceName && PrimaryProviderProfiles.permitsRating(title.contentRating))
        val url = title.largeCoverUrl ?: title.coverUrl ?: return null
        return PageDelivery(scope, session).load(scope.requireUrl(url, "https://" + parser.domain + "/").toString())
    }
    suspend fun decodePage(page: MangaPage, width: Int = 2048, height: Int = 4096) =
        PageDecoder.decode(loadImage(page), width, height)
    suspend fun loadImage(page: MangaPage): ByteArray = PageDelivery(scope, session).load(imageUrl(page))
    suspend fun imageUrl(page: MangaPage): String = withContext(Dispatchers.IO) {
        require(page.source.name == scope.sourceName)
        scope.requireUrl(parser.getPageUrl(page), "https://" + parser.domain + "/").toString()
    }
}
