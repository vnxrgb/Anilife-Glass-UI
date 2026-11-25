package com.anilili.data.wasm

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Separate transport for reviewed WASM media. Every request, including manifests, segments,
 * encryption-key requests and subtitles, uses the same host and public-DNS restrictions.
 * Redirects are denied rather than following to a destination outside the approved policy.
 * Do not wrap in a local-file resolver, shared playback cache or unrestricted fallback.
 */
@UnstableApi
internal object WasmMediaTransport {
    fun factory(provider: ApprovedWasmProvider, headers: Map<String, String> = emptyMap()): DataSource.Factory {
        // The request properties may also come from internal callers; enforce the same
        // restrictions as the validated WASM result rather than trusting the caller.
        require(headers.keys.all { it.lowercase(java.util.Locale.ROOT) in allowedHeaders })
        require(headers.keys.map { it.lowercase(java.util.Locale.ROOT) }.distinct().size == headers.size)
        require(headers.values.all { it.length <= 4096 && '\r' !in it && '\n' !in it })
        for ((name, value) in headers) {
            if (name.equals("referer", true) || name.equals("origin", true)) {
                require(WasmPolicy.allowsUrl(value, provider.hosts))
            }
        }
        val client = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    Dns.SYSTEM.lookup(hostname).also { addresses ->
                        if (addresses.isEmpty() || addresses.any { !PublicAddress.allowed(it) })
                            throw java.net.UnknownHostException("Non-public media address")
                    }
            })
            .addInterceptor { chain ->
                val request = chain.request()
                if (!WasmPolicy.allowsUrl(request.url.toString(), provider.hosts))
                    throw IOException("Unapproved media destination")
                if (request.method !in setOf("GET", "HEAD"))
                    throw IOException("Unapproved media method")
                val safe = request.newBuilder()
                for (name in request.headers.names()) {
                    if (name.lowercase(java.util.Locale.ROOT) !in allowedHeaders) safe.removeHeader(name)
                }
                for (name in listOf("Referer", "Origin")) {
                    val value = request.header(name)
                    if (value != null && !WasmPolicy.allowsUrl(value, provider.hosts))
                        throw IOException("Unapproved media origin")
                }
                val response = chain.proceed(safe.build())
                if (response.code in 300..399) {
                    response.close()
                    throw IOException("Media redirect denied")
                }
                response
            }.build()
        val upstream = OkHttpDataSource.Factory(client).setDefaultRequestProperties(headers)
        return DataSource.Factory { upstream.createDataSource() }
    }

    private val allowedHeaders = setOf("accept", "accept-encoding", "range", "user-agent", "referer", "origin")
}
