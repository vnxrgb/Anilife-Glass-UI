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
    @Test fun clearingProviderCookiesDoesNotClearOtherDomains() {
        val jar = MangaSessionCookies()
        val a = okhttp3.HttpUrl.Builder().scheme("https").host("example.org").build()
        val b = okhttp3.HttpUrl.Builder().scheme("https").host("example.net").build()
        jar.saveFromResponse(a, listOf(requireNotNull(okhttp3.Cookie.parse(a, "session=one; Secure; Path=/"))))
        jar.saveFromResponse(b, listOf(requireNotNull(okhttp3.Cookie.parse(b, "session=two; Secure; Path=/"))))
        jar.clearDomain(a.host)
        org.junit.Assert.assertTrue(jar.loadForRequest(a).isEmpty())
        org.junit.Assert.assertEquals("two", jar.loadForRequest(b).single().value)
    }
}
