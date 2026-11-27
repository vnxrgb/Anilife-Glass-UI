package com.anilumos.manga.engine
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
class CompatibilityDiagnosticsTest {
    @Test fun configuredRedirectOnly() {
        val scope = SourceScope("test", setOf("example.org", "cdn.example.org", "other.example.org"), imageRedirectHosts = setOf("cdn.example.org"))
        val from = "https://example.org/page".toHttpUrl()
        assertEquals("cdn.example.org", PageDelivery.redirectTarget(scope, from, "https://cdn.example.org/page").host)
        assertThrows(SourceScopeException::class.java) { PageDelivery.redirectTarget(scope, from, "https://other.example.org/page") }
    }
    @Test fun classificationsDoNotExposeRawErrors() {
        val error = compatibilityFailure(ProbeStage.PAGE_DOWNLOAD, SourceScopeException())
        assertEquals("HOST_SCOPE", error.reason)
        assertEquals("PAGE_DOWNLOAD", error.stage)
        assertEquals("RATE_LIMIT", compatibilityFailure(ProbeStage.SELECT_TITLE, ProviderAccessException(429)).reason)
        assertEquals("BROWSER_EXECUTION", compatibilityFailure(ProbeStage.DETAILS, ProviderFeatureException(UnsupportedFeature.BROWSER_EXECUTION)).reason)
    }
    @Test fun scriptResultsAreBounded() {
        assertEquals("hello", LocalScriptRuntime.decodeResult("\"hello\""))
        assertEquals("42", LocalScriptRuntime.decodeResult("42"))
        assertNull(LocalScriptRuntime.decodeResult("null"))
    }
}
