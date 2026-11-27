package com.anilumos.manga.engine

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ProviderImageScopeTest {
    @Test fun katanaImageSubdomainDoesNotExpandParserNetworkScope() {
        val scope = PrimaryProviderProfiles.profiles.single { it.scope.sourceName == "MANGAKATANA" }.scope
        val base = "https://mangakatana.com/".toHttpUrl()
        val image = "https://i1.mangakatana.com/example.png".toHttpUrl()
        assertFalse(scope.permits(image))
        assertTrue(scope.permitsImage(image))
        assertTrue(scope.permitsImageRedirect(base, image))
        assertFalse(scope.permitsImage("http://i1.mangakatana.com/example.png".toHttpUrl()))
        assertFalse(scope.permitsImage("https://i1.mangakatana.com.evil.org/example.png".toHttpUrl()))
        assertFalse(scope.permitsImage("https://unrelated.org/example.png".toHttpUrl()))
        assertFalse(scope.permitsImage("https://user:pass@i1.mangakatana.com/example.png".toHttpUrl()))
    }
    @Test fun declaredDomainOnlyAndNoPublicSuffixExpansion() {
        val scope = ProviderCdnRegistry.scope("FIXTURE", setOf("example.org"))
        assertTrue(scope.permitsImage("https://cdn.example.org/a.png".toHttpUrl()))
        assertFalse(scope.permits("https://cdn.example.org/a.png".toHttpUrl()))
        assertFalse(scope.permitsImage("https://cdn.other.org/a.png".toHttpUrl()))
        val publicSuffix = SourceScope("FIXTURE", setOf("co.uk"), imageDomainHosts = setOf("co.uk"))
        assertFalse(publicSuffix.permitsImage("https://other.co.uk/a.png".toHttpUrl()))
    }
    @Test fun highQualityKeepsMoreOriginalPixelsWithinBudget() {
        assertEquals(2, PageDecoder.sampleSize(2000, 3500, 2048, 4096))
        assertEquals(1, PageDecoder.sampleSize(2000, 3500, 3072, 8192))
        assertTrue(PageDecoder.sampleSize(8000, 8000, 3072, 8192) >= 4)
    }
}
