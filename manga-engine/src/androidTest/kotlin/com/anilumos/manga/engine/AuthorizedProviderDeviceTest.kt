package com.anilumos.manga.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koitharu.kotatsu.parsers.model.SortOrder

/** Explicit operator-approved single-title live test. Local CI never contacts provider sites. */
@RunWith(AndroidJUnit4::class)
class AuthorizedProviderDeviceTest {
    @Test fun atsuChicoChapterThreeDecodedPage() = runBlocking {
        assumeTrue("No one-shot Chico reader check requested", InstrumentationRegistry.getArguments().getString("atsuChico") == "true")
        val scope = SourceScope("ATSUMOE", setOf("atsu.moe", "cdn.atsu.moe"))
        val registry = ParserRegistry()
        SourceSession(scope).use { session ->
            val repository = ParserRepository(registry, scope, session)
            val search = repository.browse("Chico", 0, SortOrder.RELEVANCE)
            val matches = search.titles.filter { it.title.equals("Chico", ignoreCase = true) }.distinctBy { it.id }
            assertEquals("Expected one unambiguous safe Chico title", 1, matches.size)
            val details = repository.details(matches.single())
            val chapter = details.chapters.orEmpty().firstOrNull { it.number == 3f }
                ?: error("Approved chapter 3 not found; no fallback")
            val page = repository.pages(details, chapter).firstOrNull()
                ?: error("Approved chapter 3 returned no pages")
            val bitmap = repository.decodePage(page)
            try {
                assertTrue("Decoded page must have valid dimensions", bitmap.width > 0 && bitmap.height > 0)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun atsuLookismMetadataOnly() = runBlocking {
        assumeTrue("No one-shot metadata check requested", InstrumentationRegistry.getArguments().getString("atsuMetadata") == "true")
        val scope = SourceScope("ATSUMOE", setOf("atsu.moe"))
        val registry = ParserRegistry()
        SourceSession(scope).use { session ->
            val repository = ParserRepository(registry, scope, session)
            val catalog = repository.browse("", 0, SortOrder.POPULARITY)
            assertTrue("Atsu catalog returned no entries", catalog.consumed > 0)
            val search = repository.browse("Lookism", 0, SortOrder.RELEVANCE)
            assertTrue("Exact safe Lookism title not found", search.titles.any { it.title.equals("Lookism", ignoreCase = true) })
        }
    }

    @Test fun approvedTitleFromCatalogToDecodedPage() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val source = args.getString("liveSource").orEmpty()
        assumeTrue("No reviewed live source selected", source.isNotBlank())
        val title = args.getString("liveTitle").orEmpty()
        val hosts = args.getString("liveHosts").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        require(title.isNotBlank() && hosts.isNotEmpty())
        val registry = ParserRegistry()
        val scope = SourceScope(source, hosts)
        SourceSession(scope).use { session ->
            val repository = ParserRepository(registry, scope, session)
            val catalog = repository.browse("", 0, SortOrder.POPULARITY)
            assertTrue("Catalog returned no entries", catalog.consumed > 0)
            val search = repository.browse(title, 0, SortOrder.RELEVANCE)
            val match = search.titles.firstOrNull { it.title.equals(title, ignoreCase = true) }
                ?: error("Exact approved safe title absent from first search page")
            val details = repository.details(match)
            val chapter = details.chapters.orEmpty().firstOrNull() ?: error("No chapter for approved title")
            val page = repository.pages(details, chapter).firstOrNull() ?: error("No page for approved chapter")
            val bitmap = repository.decodePage(page)
            try {
                assertTrue(bitmap.width > 0 && bitmap.height > 0)
            } finally { bitmap.recycle() }
        }
    }
}
