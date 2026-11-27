package com.anilili.manga
import org.junit.Assert.*
import org.junit.Test
class MangaProviderPolicyTest {
    private fun source(key: String, label: String) = object : MangaSource {
        override val id = key
        override val displayName = label
        override suspend fun search(query: String, limit: Int) = emptyList<MangaTitle>()
        override suspend fun chapters(mangaId: String, language: String) = emptyList<MangaChapter>()
        override suspend fun pages(chapterId: String) = emptyList<MangaPage>()
    }
    @Test fun shortlistDoesNotAutomaticallyFillWithUnknownProviders() {
        val atsu = source(REVIEWED_SOURCE_ID, "Atsu")
        val dex = source("engine:MANGADEX", "MangaDex")
        val other = source("engine:OTHER", "Other")
        val pinned = source("engine:PINNED", "Pinned")
        val ordered = MangaProviderPolicy.order(listOf(other, atsu, dex, pinned), setOf(pinned.id), emptyMap(), null)
        assertEquals(listOf(pinned.id, atsu.id, dex.id), MangaProviderPolicy.primary(ordered, setOf(pinned.id), emptyMap(), null).map { it.id })
        assertTrue(MangaProviderPolicy.primary(listOf(other), emptySet(), emptyMap(), null).isEmpty())
    }
    @Test fun himmyInspiredPrimarySetIncludesAllFive() {
        val ids = listOf(REVIEWED_SOURCE_ID, "engine:WEEBCENTRAL", "engine:MANGADEX", "engine:MANGAPILL", "engine:MANGAKATANA")
        val sources = ids.map { source(it, it) }
        val ordered = MangaProviderPolicy.order(sources.reversed(), emptySet(), emptyMap(), null)
        assertEquals(ids, MangaProviderPolicy.primary(ordered, emptySet(), emptyMap(), null).map { it.id })
    }
    @Test fun positiveCacheExpiresAfterSevenDays() {
        assertTrue(MangaTitleMatchCache.fresh(1000, 1000 + MangaTitleMatchCache.TTL - 1))
        assertFalse(MangaTitleMatchCache.fresh(1000, 1000 + MangaTitleMatchCache.TTL))
        assertFalse(MangaTitleMatchCache.fresh(1000, 999))
    }
}
