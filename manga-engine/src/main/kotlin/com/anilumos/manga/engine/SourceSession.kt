package com.anilumos.manga.engine

import android.os.Looper
import kotlinx.coroutines.ensureActive
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import java.net.UnknownHostException
import java.net.InetAddress
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Cookies are isolated by source session; no shared anime credentials, no external browser cookies. */
internal class ScopedCookies(private val scope: SourceScope) : CookieJar {
    private val values = mutableListOf<Cookie>()
    private var sealed = false
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (sealed || !scope.permits(url)) return
        cookies.filter { it.secure && it.matches(url) }.forEach { cookie ->
            values.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) values.add(cookie)
        }
        while (values.size > 128) values.removeAt(0)
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (sealed) return emptyList()
        values.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return if (scope.permits(url)) values.filter { it.matches(url) } else emptyList()
    }
    @Synchronized fun clear() = values.clear()
    @Synchronized fun seal() { sealed = true; values.clear() }
}

internal class SourceSession(private val scope: SourceScope, cacheDirectory: File? = null, private val headers: SourceHeaders = SourceHeaders(scope), private val offline: Boolean = false) : Closeable {
    private val closed = AtomicBoolean(false)
    val cookies = ScopedCookies(scope)
    val client = OkHttpClient.Builder()
        .cookieJar(cookies)
        .dns(Dns { hostname ->
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || addresses.any { !publicProviderAddress(it) }) throw UnknownHostException("Provider resolved to a non-public address")
            addresses
        })
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .apply { if (cacheDirectory != null) cache(Cache(cacheDirectory, 16L * 1024 * 1024)) }
        .addInterceptor { chain ->
            if (closed.get()) throw IOException("Reader session closed")
            if (offline) throw IOException("Network disabled during offline audit")
            if (!scope.permits(chain.request().url)) throw SourceScopeException()
            val result = chain.proceed(headers.apply(chain.request()))
            if (result.code == 401 || result.code == 403 || result.code == 429 || result.code == 451) {
                val code = result.code
                result.close()
                throw ProviderAccessException(code)
            }
            result
        }.build()
    fun requireBrowserOrigin(baseUrl: String) { scope.requireUrl(baseUrl) }
    suspend fun evaluateLocalScript(script: String, timeout: Long): String? {
        if (offline || closed.get()) throw ProviderFeatureException(UnsupportedFeature.BROWSER_EXECUTION)
        return try { LocalScriptRuntime.evaluate(script, timeout) }
            catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                throw ProviderFeatureException(UnsupportedFeature.BROWSER_EXECUTION)
            }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Reject new calls/cookies immediately. TLS socket close and disk cache close may
        // perform I/O, so never execute these operations on the Android UI thread.
        cookies.seal()
        SessionDisposal.run {
            val steps = listOf<() -> Unit>(
                { client.dispatcher.cancelAll() },
                { client.connectionPool.evictAll() },
                { client.cache?.close() },
            )
            for (step in steps) {
                try { step() }
                catch (e: Exception) {
                    // Never include provider URLs, cookie contents or exception messages.
                    Log.w("MangaSession", "Session cleanup failed: " + e.javaClass.simpleName)
                }
            }
        }
    }
}

/** Shared worker outlives Compose scope cancellation. Non-UI callers retain synchronous cleanup. */
internal object SessionDisposal {
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "manga-session-disposal").apply { isDaemon = true }
    }
    fun run(cleanup: () -> Unit) {
        if (Looper.getMainLooper()?.thread === Thread.currentThread()) {
            worker.execute { cleanup() }
        } else {
            cleanup()
        }
    }
}

internal fun publicProviderAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress) return false
    val b = address.address.map { it.toInt() and 255 }
    if (b.size == 4) {
        return !(b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 ||
            (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) ||
            (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) ||
            (b[0] == 192 && b[1] == 0) || (b[0] == 198 && b[1] in 18..19) ||
            (b[0] == 198 && b[1] == 51 && b[2] == 100) || (b[0] == 203 && b[1] == 0 && b[2] == 113))
    }
    return b.size == 16 && b[0] in 0x20..0x3f &&
        !(b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8)
}
