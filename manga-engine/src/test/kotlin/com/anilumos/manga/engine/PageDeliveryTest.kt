package com.anilumos.manga.engine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PageDeliveryTest {
    private fun response(bytes: ByteArray, type: String, code: Int = 200) = Response.Builder()
        .request(Request.Builder().url("https://example.org/page").build())
        .protocol(Protocol.HTTP_1_1).code(code).message("fixture")
        .body(bytes.toResponseBody(type.toMediaType())).build()

    @Test fun imageBytesAreReturned() {
        val bytes = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS1sAAAAASUVORK5CYII=")
        response(bytes, "image/png").use { assertArrayEquals(bytes, PageDelivery.readImage(it)) }
    }
    @Test fun htmlEmptyRedirectAndOversizedBodiesAreRejected() {
        val cases = listOf(
            response(byteArrayOf(1), "text/html"),
            response(byteArrayOf(1), "image/png"),
            response(byteArrayOf(), "image/png"),
            response(byteArrayOf(1), "image/png", 302),
            response(ByteArray(16 * 1024 * 1024 + 1), "image/png")
        )
        cases.forEach { it.use { r -> assertTrue(runCatching { PageDelivery.readImage(r) }.exceptionOrNull() is IOException) } }
    }
    @Test fun redirectsStayOnReviewedOrigin() {
        val scope = SourceScope("FIXTURE", setOf("example.org", "cdn.example.org"))
        val from = "https://example.org/page".toHttpUrl()
        assertEquals("https://example.org/new", PageDelivery.redirectTarget(scope, from, "/new").toString())
        listOf("http://example.org/new", "https://cdn.example.org/new", "https://other.test/new", null).forEach {
            assertTrue(runCatching { PageDelivery.redirectTarget(scope, from, it) }.exceptionOrNull() is IOException)
        }
    }
}
