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
