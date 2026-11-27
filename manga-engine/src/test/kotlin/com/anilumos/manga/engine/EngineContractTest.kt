package com.anilumos.manga.engine

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.koitharu.kotatsu.parsers.ParsersApi
import java.io.IOException

class EngineContractTest {
    @Test fun bundleIsPresentAndUsesExpectedContract() {
        val registry = ParserRegistry()
        assertEquals(ParsersApi.VERSION, registry.bundle.apiVersion)
        val sources = registry.descriptors()
        assertTrue("Compiled descriptor catalog must not be empty", sources.isNotEmpty())
        assertEquals(sources.size, sources.map { it.id }.distinct().size)
        assertTrue(sources.all { it.id.isNotBlank() })
    }
    @Test fun reviewedHostsAreExactAndHttpsOnly() {
        val scope = SourceScope("FIXTURE", setOf("example.org", "cdn.example.org"))
        assertTrue(scope.permits("https://example.org/catalog".toHttpUrl()))
        assertTrue(scope.permits("https://cdn.example.org/page".toHttpUrl()))
        assertFalse(scope.permits("http://example.org/catalog".toHttpUrl()))
        assertFalse(scope.permits("https://example.org.evil.test/page".toHttpUrl()))
        assertFalse(scope.permits("https://user:pass@example.org/page".toHttpUrl()))
        assertFalse(scope.permits("https://example.org:8443/page".toHttpUrl()))
        assertThrows(IOException::class.java) { scope.requireUrl("//evil.test/page", "https://example.org/") }
        assertEquals("https://example.org/page", scope.requireUrl("/page", "https://example.org/").toString())
    }
    @Test fun emptyCatalogDoesNotInventNextPage() {
        val page = catalogPage(emptyList(), 24)
        assertEquals(0, page.consumed)
        assertNull(page.nextOffset)
    }
    @Test fun sourceSessionsDoNotShareCookies() {
        val scope = SourceScope("FIXTURE", setOf("example.org"))
        val url = "https://example.org/".toHttpUrl()
        val a = ScopedCookies(scope)
        val b = ScopedCookies(scope)
        val cookie = okhttp3.Cookie.Builder().name("session").value("fixture").hostOnlyDomain("example.org").path("/").secure().build()
        a.saveFromResponse(url, listOf(cookie))
        assertEquals(listOf(cookie), a.loadForRequest(url))
        assertTrue(b.loadForRequest(url).isEmpty())
        assertTrue(a.loadForRequest("https://other.test/".toHttpUrl()).isEmpty())
    }
}
