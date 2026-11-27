package com.anilili.manga

import android.content.Context
import coil.ImageLoader
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** In-memory ordinary HTTP cookies, matched by OkHttp's domain, path, secure and expiry rules. */
internal class MangaSessionCookies : CookieJar {
    private val cookies = LinkedHashMap<String, Cookie>()
    private fun key(cookie: Cookie) = "${cookie.domain}|\u0000${cookie.path}|\u0000${cookie.name}"
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        this.cookies.entries.removeAll { it.value.expiresAt <= now }
        for (cookie in cookies) {
            if (cookie.expiresAt <= now) this.cookies.remove(key(cookie))
            else if (cookie.matches(url)) this.cookies[key(cookie)] = cookie
        }
        while (this.cookies.size > 512) this.cookies.remove(this.cookies.keys.first())
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.entries.removeAll { it.value.expiresAt <= now }
        return cookies.values.filter { it.matches(url) }.sortedByDescending { it.path.length }
    }
}

internal object MangaNetwork {
    val cookieJar: CookieJar = MangaSessionCookies()
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder().cookieJar(cookieJar)
            .addNetworkInterceptor { chain ->
                val original = chain.request()
                val request = if (original.url.isHttps) original else original.newBuilder()
                    .removeHeader("Authorization").removeHeader("Proxy-Authorization")
                    .removeHeader("Cookie").build()
                chain.proceed(request)
            }
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
    }
    @Volatile private var loader: ImageLoader? = null
    fun imageLoader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext).okHttpClient(client)
            .build().also { loader = it }
    }
}
