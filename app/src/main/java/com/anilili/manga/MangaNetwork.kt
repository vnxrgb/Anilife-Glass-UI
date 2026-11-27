package com.anilili.manga

import android.content.Context
import coil.ImageLoader
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Ordinary HTTP cookies; only explicitly persistent HTTPS cookies survive process restart. */
internal class MangaSessionCookies : CookieJar {
    private val cookies = LinkedHashMap<String, Cookie>()
    private var storage: android.content.SharedPreferences? = null
    @Synchronized fun initialize(context: Context) {
        if (storage != null) return
        val prefs = context.getSharedPreferences("manga_http_cookies", Context.MODE_PRIVATE)
        storage = prefs
        val now = System.currentTimeMillis()
        prefs.all.values.filterIsInstance<String>().forEach { encoded ->
            runCatching {
                val json = org.json.JSONObject(encoded)
                val url = okhttp3.HttpUrl.Builder().scheme("https").host(json.getString("domain")).build()
                val cookie = Cookie.parse(url, json.getString("cookie"))
                if (cookie != null && cookie.persistent && cookie.secure && cookie.expiresAt > now) cookies[key(cookie)] = cookie
            }
        }
        persist()
    }
    private fun persist() {
        val editor = storage?.edit()?.clear() ?: return
        cookies.values.filter { it.persistent && it.secure && it.expiresAt > System.currentTimeMillis() }.forEach { cookie ->
            editor.putString(key(cookie), org.json.JSONObject().put("domain", cookie.domain).put("cookie", cookie.toString()).toString())
        }
        editor.apply()
    }
    private fun key(cookie: Cookie) = "${cookie.domain}|\u0000${cookie.path}|\u0000${cookie.name}"
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        this.cookies.entries.removeAll { it.value.expiresAt <= now }
        for (cookie in cookies) {
            if (cookie.expiresAt <= now) this.cookies.remove(key(cookie))
            else if (cookie.matches(url)) this.cookies[key(cookie)] = cookie
        }
        while (this.cookies.size > 512) this.cookies.remove(this.cookies.keys.first())
        persist()
    }
    @Synchronized fun clearDomain(host: String) {
        cookies.entries.removeAll { (_, cookie) -> host == cookie.domain || host.endsWith("." + cookie.domain) }
        persist()
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.entries.removeAll { it.value.expiresAt <= now }
        return cookies.values.filter { it.matches(url) }.sortedByDescending { it.path.length }
    }
}

internal object MangaNetwork {
    private val session = MangaSessionCookies()
    fun initialize(context: Context) { session.initialize(context) }
    val cookieJar: CookieJar = session
    fun clearDomain(host: String) { session.clearDomain(host) }
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
