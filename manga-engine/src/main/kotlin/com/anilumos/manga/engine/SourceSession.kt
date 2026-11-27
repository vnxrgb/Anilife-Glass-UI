package com.anilumos.manga.engine

import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Cookies are isolated by source session; no shared anime credentials, no external browser cookies. */
internal class ScopedCookies(private val scope: SourceScope) : CookieJar {
    private val values = mutableListOf<Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!scope.permits(url)) return
        cookies.filter { it.secure && it.matches(url) }.forEach { cookie ->
            values.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) values.add(cookie)
        }
        while (values.size > 128) values.removeAt(0)
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        values.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return if (scope.permits(url)) values.filter { it.matches(url) } else emptyList()
    }
    @Synchronized fun clear() = values.clear()
}

internal class SourceSession(scope: SourceScope, cacheDirectory: File? = null, private val headers: SourceHeaders = SourceHeaders(scope)) : Closeable {
    val cookies = ScopedCookies(scope)
    val client = OkHttpClient.Builder()
        .cookieJar(cookies)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .apply { if (cacheDirectory != null) cache(Cache(cacheDirectory, 16L * 1024 * 1024)) }
        .addInterceptor { chain ->
            if (!scope.permits(chain.request().url)) throw IOException("Request outside reviewed source scope")
            val result = chain.proceed(headers.apply(chain.request()))
            if (result.code == 401 || result.code == 403 || result.code == 429 || result.code == 451) {
                val code = result.code
                result.close()
                throw ProviderAccessException(code)
            }
            result
        }.build()
    override fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.cache?.close()
        cookies.clear()
    }
}
