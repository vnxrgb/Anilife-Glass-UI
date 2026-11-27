package com.anilumos.manga.engine

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.ParsersApi
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.model.*
import java.lang.reflect.Proxy

/** A synthetic parser, not a real website or a provider health certification. */
@OptIn(InternalParsersApi::class)
class RepositoryFixtureTest {
    private val source = object : ParserMangaSource("FIXTURE", "Fixture", "en", ContentType.MANGA, false) {}
    private val chapter = MangaChapter(10L, "One", 1f, 1, "/chapter", null, 0L, null, source)
    private val page = MangaPage(100L, "/page", null, source)

    private fun title(id: Long, rating: ContentRating?) = Manga(
        id = id, title = "Fixture $id", altTitles = emptySet(), url = "/title/$id",
        publicUrl = "https://example.org/title/$id", rating = 0f,
        contentRating = rating, coverUrl = null, tags = emptySet(), state = null,
        authors = emptySet(), source = source,
    )

    private fun bundle(raw: List<Manga>, image: String = "/images/page.png", supportsSearch: Boolean = true): ParsersBundle {
        val proxy = Proxy.newProxyInstance(MangaParser::class.java.classLoader, arrayOf(MangaParser::class.java)) { _, method, args ->
            when (method.name) {
                "getDomain" -> "example.org"
                "getSource" -> source
                "getAvailableSortOrders" -> setOf(SortOrder.POPULARITY)
                "getFilterCapabilities" -> MangaListFilterCapabilities(isSearchSupported = supportsSearch)
                "getList" -> raw
                "getDetails" -> (args!![0] as Manga).copy(chapters = listOf(chapter))
                "getPages" -> listOf(page)
                "getPageUrl" -> image
                else -> error("Unexpected fixture call: ${method.name}")
            }
        } as MangaParser
        return object : ParsersBundle {
            override val apiVersion = ParsersApi.VERSION
            override val version = 1L
            override val revision = "fixture"
            override val sources = listOf(source)
            override fun findSource(name: String): ParserMangaSource? = sources.firstOrNull { it.name == name }
            override fun newParser(source: ParserMangaSource, context: MangaLoaderContext): MangaParser = proxy
            override suspend fun resolveLink(context: MangaLoaderContext, link: HttpUrl): Manga? = null
        }
    }
    @Test fun advertisedGenreAndCategoryFiltersAreValidated() {
        val tag = MangaTag("action", "Action", source)
        val second = MangaTag("comedy", "Comedy", source)
        val options = MangaListFilterOptions(availableTags = setOf(tag, second),
            availableContentTypes = setOf(ContentType.MANGA))
        val caps = MangaListFilterCapabilities(isSearchSupported = true, isSearchWithFiltersSupported = true)
        val filter = reviewedFilter(source.name, " test ", setOf(tag), emptySet(), setOf(ContentType.MANGA),
            emptySet(), caps, options)
        assertEquals("test", filter.query)
        assertEquals(setOf(tag), filter.tags)
        assertThrows(UnsupportedOperationException::class.java) {
            reviewedFilter(source.name, "", setOf(tag, second), emptySet(), emptySet(), emptySet(), caps, options)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reviewedFilter(source.name, "", setOf(tag), setOf(tag), emptySet(), emptySet(), caps, options)
        }
    }
    @Test fun readerBridgePreservesPaginationAndRejectsForeignHandles() = runBlocking {
        val scope = SourceScope(source.name, setOf("example.org"))
        val registry = ParserRegistry(bundle(listOf(title(1, ContentRating.SAFE))))
        val a = SourceSession(scope, offline = true)
        val b = SourceSession(scope, offline = true)
        ReaderSessionBridge.fixture(ParserRepository(registry, scope, a), a).use { first ->
            ReaderSessionBridge.fixture(ParserRepository(registry, scope, b), b).use { second ->
                val list = first.browse("", 5, SortOrder.POPULARITY)
                assertEquals(6, list.nextOffset)
                assertEquals("1", list.titles.single().id)
                val detail = first.details(list.titles.single())
                assertEquals("10", detail.chapters.single().id)
                val pages = first.pages(detail.chapters.single())
                assertEquals(0, pages.single().index)
                assertTrue(runCatching { second.details(list.titles.single()) }.exceptionOrNull() is IllegalArgumentException)
                assertTrue(runCatching { second.pages(detail.chapters.single()) }.exceptionOrNull() is IllegalArgumentException)
                assertTrue(runCatching { second.decode(pages.single()) }.exceptionOrNull() is IllegalArgumentException)
            }
            first.close()
            assertTrue(runCatching { first.browse("", 0, SortOrder.POPULARITY) }.exceptionOrNull() is IllegalStateException)
        }
    }
    @Test fun browseOnlyParserCanSelectExactApprovedTitle() = runBlocking {
        val scope = SourceScope(source.name, setOf("example.org"))
        SourceSession(scope, offline = true).use { session ->
            val repo = ParserRepository(ParserRegistry(bundle(listOf(title(1, ContentRating.SAFE)), supportsSearch = false)), scope, session)
            assertFalse(repo.supportsSearch)
            assertEquals(1L, repo.approvedTitle("Fixture 1").title?.id)
            assertEquals(ProviderState.TITLE_NOT_FOUND, repo.approvedTitle("Other").state)
        }
    }

    @Test fun approvedSelectionNeverFallsBackToUnrelatedTitle() = runBlocking {
        val scope = SourceScope(source.name, setOf("example.org"))
        SourceSession(scope, offline = true).use { session ->
            val repo = ParserRepository(ParserRegistry(bundle(listOf(title(1, ContentRating.SAFE)))), scope, session)
            assertEquals(ProviderState.TITLE_NOT_FOUND, repo.approvedTitle("Other").state)
        }
    }
    @Test fun ambiguousApprovedTitleIsRejected() = runBlocking {
        val scope = SourceScope(source.name, setOf("example.org"))
        val raw = listOf(title(1, ContentRating.SAFE), title(2, ContentRating.SAFE).copy(title = "Fixture 1"))
        SourceSession(scope, offline = true).use { session ->
            val repo = ParserRepository(ParserRegistry(bundle(raw)), scope, session)
            assertEquals(ProviderState.AMBIGUOUS_TITLE, repo.approvedTitle("Fixture 1").state)
        }
    }
    @Test fun fullyFilteredCatalogCanStillSearchForApprovedTitle() = runBlocking {
        val selection = selectApprovedTitle("Fixture 1", true, catalogPage(listOf(title(2, null)), 0)) {
            catalogPage(listOf(title(1, ContentRating.SAFE)), 0)
        }
        assertEquals(1L, selection.title?.id)
    }
    @Test fun rawOffsetSurvivesFilteringAndDetailsLeadToPages() = runBlocking {
        val scope = SourceScope(source.name, setOf("example.org"))
        SourceSession(scope).use { session ->
            val repo = ParserRepository(ParserRegistry(bundle(listOf(title(1, null), title(2, ContentRating.ADULT), title(3, ContentRating.SAFE)))), scope, session)
            val result = repo.browse("fixture", 24, SortOrder.RELEVANCE)
            assertEquals(3, result.consumed)
            assertEquals(27, result.nextOffset)
            assertEquals(listOf(3L), result.titles.map { it.id })
            val details = repo.details(result.titles.single())
            assertEquals(listOf(chapter), details.chapters)
            val pages = repo.pages(details, chapter)
            assertEquals(listOf(page), pages)
            assertEquals("https://example.org/images/page.png", repo.imageUrl(pages.single()))
        }
    }
    @Test fun fullyFilteredPageStillAdvancesRawOffset() = runBlocking {
        val scope = SourceScope(source.name, setOf("example.org"))
        SourceSession(scope).use { session ->
            val repo = ParserRepository(ParserRegistry(bundle(listOf(title(1, null), title(2, ContentRating.SUGGESTIVE)))), scope, session)
            val result = repo.browse("", 10, SortOrder.POPULARITY)
            assertTrue(result.titles.isEmpty())
            assertEquals(2, result.consumed)
            assertEquals(12, result.nextOffset)
        }
    }
    @Test fun pageUrlCannotEscapeReviewedHosts() = runBlocking {
        val scope = SourceScope(source.name, setOf("example.org"))
        SourceSession(scope).use { session ->
            val repo = ParserRepository(ParserRegistry(bundle(emptyList(), "https://unreviewed.test/image.png")), scope, session)
            val error = runCatching { repo.imageUrl(page) }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
        }
    }
}
