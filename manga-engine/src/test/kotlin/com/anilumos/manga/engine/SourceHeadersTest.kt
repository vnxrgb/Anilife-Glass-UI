package com.anilumos.manga.engine

import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class SourceHeadersTest {
    private val scope = SourceScope("FIXTURE", setOf("example.org", "cdn.example.org"))
    @Test fun defaultsAreHostSpecificAndDoNotOverrideParserHeaders() {
        val headers = SourceHeaders(scope, mapOf("example.org" to mapOf("Accept-Language" to "en", "Accept" to "application/json")))
        val request = Request.Builder().url("https://example.org/list").header("Accept", "image/*").build()
        assertEquals("image/*", headers.apply(request).header("Accept"))
        assertEquals("en", headers.apply(request).header("Accept-Language"))
        assertNull(headers.apply(Request.Builder().url("https://cdn.example.org/page").build()).header("Accept-Language"))
    }
    @Test fun unsafeDefaultsAreRejected() {
        listOf("Authorization", "Cookie", "Host", "Referer", "User-Agent").forEach {
            assertTrue(runCatching { SourceHeaders(scope, mapOf("example.org" to mapOf(it to "fixture"))) }.isFailure)
        }
        assertTrue(runCatching { SourceHeaders(scope, mapOf("other.test" to mapOf("Accept" to "image/*"))) }.isFailure)
        assertTrue(runCatching { SourceHeaders(scope, mapOf("example.org" to mapOf("Accept" to "bad\nvalue"))) }.isFailure)
    }
}
