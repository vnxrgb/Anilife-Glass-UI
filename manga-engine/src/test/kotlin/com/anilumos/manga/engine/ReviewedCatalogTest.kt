package com.anilumos.manga.engine
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
class ReviewedCatalogTest {
    @Test fun liveProvidersAreConfiguredWithoutChapterGrants() {
        assertEquals(setOf("ATSUMOE", "MANGADEX", "COMICK_FUN"), LiveProviderProfiles.all.map { it.scope.sourceName }.toSet())
        assertEquals("Chico", LiveProviderProfiles.all.first().probeTitle)
    }
    @Test fun delegatedImageHostIsBoundedToProviderDomain() {
        val scope = LiveProviderProfiles.all.single { it.scope.sourceName == "MANGADEX" }.scope
        assertTrue(scope.permits("https://api.mangadex.org/".toHttpUrl()))
        assertTrue(scope.permits("https://node.mangadex.network/".toHttpUrl()))
        assertFalse(scope.permits("https://node.mangadex.network.example.com/".toHttpUrl()))
        assertFalse(scope.permits("http://node.mangadex.network/".toHttpUrl()))
    }
    @Test fun historicalFixtureRestrictionsRemainTestable() {
        assertEquals(setOf(3f), permittedChapters(ReviewedTitleGrant("Chico", setOf(3f)), listOf(1f,2f,3f)))
    }
}
