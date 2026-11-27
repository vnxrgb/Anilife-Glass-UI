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
        assertTrue(sources.size >= 1000)
        assertEquals(sources.size, sources.map { it.id }.distinct().size)
        assertEquals(1, sources.count { it.id == "mangadex" })
        assertTrue(sources.any { it.languageTag == "en" })
        assertSame(MangaSources(sources).byId("mangadex"), MangaSources(sources).byId("kotatsu:mangadex"))
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
