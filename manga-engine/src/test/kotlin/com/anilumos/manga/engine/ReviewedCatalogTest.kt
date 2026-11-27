package com.anilumos.manga.engine
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
class ReviewedCatalogTest {
    @Test fun liveProvidersAreConfiguredWithoutChapterGrants() {
        val profiles = LiveProviderProfiles.all
        val ids = profiles.map { it.scope.sourceName }
        assertTrue(ids.size > 3)
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.contains("ATSUMOE"))
        assertTrue(profiles.all { it.category in ParserRegistry.supportedCategories })
        assertEquals("Chico", LiveProviderProfiles.all.first().probeTitle)
    }
    @Test fun delegatedImageHostIsBoundedToProviderDomain() {
        val scope = SourceScope("MANGADEX", setOf("api.mangadex.org"), setOf("mangadex.network"))
        assertTrue(scope.permits("https://api.mangadex.org/".toHttpUrl()))
        assertTrue(scope.permits("https://node.mangadex.network/".toHttpUrl()))
        assertFalse(scope.permits("https://node.mangadex.network.example.com/".toHttpUrl()))
        assertFalse(scope.permits("http://node.mangadex.network/".toHttpUrl()))
    }
    @Test fun discoveredDomainsAndNetworkAddressesAreBounded() {
        assertEquals("example.org", LiveProviderProfiles.domain("Example.ORG"))
        listOf("localhost", "127.0.0.1", "foo.local", "https://example.org", "user@example.org").forEach {
            assertNull(LiveProviderProfiles.domain(it))
        }
        listOf("127.0.0.1", "192.168.1.1", "169.254.1.1", "100.64.0.1", "::1", "fc00::1").forEach {
            assertFalse(publicProviderAddress(java.net.InetAddress.getByName(it)))
        }
        assertTrue(publicProviderAddress(java.net.InetAddress.getByName("8.8.8.8")))
    }
    @Test fun historicalFixtureRestrictionsRemainTestable() {
        assertEquals(setOf(3f), permittedChapters(ReviewedTitleGrant("Chico", setOf(3f)), listOf(1f,2f,3f)))
    }
}
