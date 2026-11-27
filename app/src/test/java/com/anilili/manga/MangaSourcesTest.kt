package com.anilili.manga

import org.junit.Assert.*
import org.junit.Test

class MangaSourcesTest {
    private class Fake(override val id: String) : MangaSource {
        override val displayName = id
        override suspend fun search(query: String, limit: Int) = emptyList<MangaTitle>()
        override suspend fun chapters(mangaId: String, language: String) = emptyList<MangaChapter>()
        override suspend fun pages(chapterId: String) = emptyList<MangaPage>()
    }

    @Test fun kotatsuCatalogExposesAllDescriptorsAndSingleMangaDex() {
        val sources = builtInKotatsuSources()
        assertTrue(sources.isNotEmpty())
        assertTrue(sources.all { it.available })
        assertEquals(sources.size, sources.map { it.id }.distinct().size)
        assertEquals(1, sources.count { it.id == "mangadex" })
        assertTrue(sources.any { it.languageTag == "en" })
        assertSame(MangaSources(sources).byId("mangadex"), MangaSources(sources).byId("kotatsu:mangadex"))
    }

    @Test fun adultAndBrokenSourcesExcluded() {
        assertFalse(sourceAllowed(org.koitharu.kotatsu.parsers.model.ContentType.HENTAI, "TEST", "Test", false))
        assertFalse(sourceAllowed(org.koitharu.kotatsu.parsers.model.ContentType.MANGA, "ADULT_TEST", "Test", false))
        assertFalse(sourceAllowed(org.koitharu.kotatsu.parsers.model.ContentType.MANGA, "TEST", "Test", true))
        assertTrue(sourceAllowed(org.koitharu.kotatsu.parsers.model.ContentType.MANGA, "TEST", "Test", false))
        assertEquals(SourceHealth.RESTRICTED, classifySourceFailure(IllegalStateException("HTTP 403")))
        assertEquals(SourceHealth.SERVER_TLS, classifySourceFailure(IllegalStateException("HTTP 526")))
    }

    @Test fun canonicalChapterMatchesPriorKotatsuIdentity() {
        val chapter = MangaChapter("mangadex", "uuid", "title", "", "1", "en", previousParserId = "42")
        assertTrue(chapter.matchesSaved("uuid"))
        assertTrue(chapter.matchesSaved("42"))
        assertFalse(chapter.matchesSaved(null))
    }

    @Test fun builtInProvidersResolveByStableId() {
        val first = Fake("first")
        val second = Fake("second")
        val registry = MangaSources(listOf(first, second))
        assertSame(first, registry.byId("first"))
        assertSame(second, registry.byId("second"))
        assertNull(registry.byId("missing"))
        assertEquals(listOf(first, second), registry.all())
    }
}
