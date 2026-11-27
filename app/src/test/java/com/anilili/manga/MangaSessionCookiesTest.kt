package com.anilili.manga

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class MangaSessionCookiesTest {
    @Test fun cookiesAreScopedAndRespectSecureTransport() {
        val jar = MangaSessionCookies()
        val url = "https://example.com/chapter".toHttpUrl()
        val cookie = Cookie.Builder().name("session").value("test").hostOnlyDomain("example.com").path("/").secure().build()
        jar.saveFromResponse(url, listOf(cookie))
        assertEquals(listOf(cookie), jar.loadForRequest(url))
        assertTrue(jar.loadForRequest("https://other.example/chapter".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://example.com/chapter".toHttpUrl()).isEmpty())
    }
}
