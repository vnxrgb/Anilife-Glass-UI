package com.anilili.data.wasm

import com.anilili.data.remote.ProviderResponseLimitInterceptor
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/** Dedicated credential-free host client. No automatic redirects or proxy inheritance. */
internal class WasmHttpGateway(private val approvedHosts: Set<String>) {
    private companion object { const val MAX_BROKER_BODY_BYTES = 4 * 1024 * 1024 }
    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                if (addresses.isEmpty() || addresses.any { !PublicAddress.allowed(it) })
                    throw java.net.UnknownHostException("Non-public provider address")
            }
        })
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(ProviderResponseLimitInterceptor(MAX_BROKER_BODY_BYTES.toLong()))
        .build()

    fun cancel() { client.dispatcher.cancelAll() }

    data class Reply(val status: Int, val body: ByteArray, val headers: List<Pair<String, String>>)

    /** Every hop consumes the same quota. No browser engine, cookies, credentials or proxies. */
    private var requests = 0
    @Synchronized
    fun fetch(url: String, method: String, headers: List<Pair<String, String>>, body: ByteArray?, browserOrigin: String? = null): Reply {
        require(method in setOf("GET", "HEAD", "POST"))
        require((body?.size ?: 0) <= 256 * 1024)
        require(headers.size <= 32)
        if (browserOrigin != null) require(WasmPolicy.allowsUrl(browserOrigin, approvedHosts))
        require(headers.none { (name, value) -> name.any { it.code == 13 || it.code == 10 } || value.any { it.code == 13 || it.code == 10 } })
        var target = url
        var hops = 0
        val visited = mutableSetOf<String>()
        while (true) {
            if (++requests > 12 || !WasmPolicy.allowsUrl(target, approvedHosts) || !visited.add(target))
                throw IOException("Provider request denied")
            val builder = Request.Builder().url(target)
            for ((name, value) in headers) {
                require(name.length <= 64 && value.length <= 4096)
                if (name.lowercase() in setOf("accept", "accept-language", "content-type", "user-agent", "referer", "origin")) {
                    if (name.equals("referer", true) || name.equals("origin", true))
                        require(WasmPolicy.allowsUrl(value, approvedHosts))
                    // Never carry caller-supplied origin/referer to a redirected destination.
                    if (hops == 0 || (!name.equals("referer", true) && !name.equals("origin", true)))
                        builder.header(name, value)
                }
            }
            if (hops == 0 && browserOrigin != null) {
                if (headers.none { it.first.equals("referer", true) }) builder.header("Referer", browserOrigin)
                if (headers.none { it.first.equals("origin", true) }) {
                    val parsed = browserOrigin.toHttpUrlOrNull()!!
                    builder.header("Origin", "${parsed.scheme}://${parsed.host}")
                }
            }
            builder.method(method, if (method == "POST") (body ?: ByteArray(0)).toRequestBody() else null)
            var next: String? = null
            client.newCall(builder.build()).execute().use { response ->
                if (response.code in 300..399) {
                    // Only safe GET/HEAD redirects. Never replay a POST body at another URL.
                    next = WasmRedirectPolicy.next(target, response.header("Location"), response.code, method, hops, approvedHosts)
                        ?: throw IOException("Provider redirect denied")
                } else {
                    val safe = response.headers.names().filter { it.lowercase() in setOf("content-type", "content-language", "cache-control") }
                        .flatMap { name -> response.headers.values(name).map { name to it } }
                    val contentLength = response.body?.contentLength() ?: 0L
                    if (contentLength > MAX_BROKER_BODY_BYTES) throw IOException("Provider response too large")
                    val payload = response.body?.bytes() ?: ByteArray(0)
                    if (payload.size > MAX_BROKER_BODY_BYTES) throw IOException("Provider response too large")
                    return Reply(response.code, payload, safe)
                }
            }
            target = next ?: throw IOException("Missing redirect")
            hops++
        }
    }
}

internal object PublicAddress {
    fun allowed(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        if (b.size == 4) {
            val a = b[0]; val c = b[1]; val d = b[2]
            return a in 1..223 && a != 10 && a != 127 &&
                !(a == 100 && c in 64..127) && !(a == 169 && c == 254) &&
                !(a == 172 && c in 16..31) && !(a == 192 && c == 168) &&
                !(a == 192 && c == 0) && !(a == 192 && c == 88 && d == 99) &&
                !(a == 198 && c in 18..19) && !(a == 198 && c == 51 && d == 100) &&
                !(a == 203 && c == 0 && d == 113)
        }
        // Only global unicast IPv6; deny transition, documentation and special-purpose ranges.
        return b.size == 16 && b[0] in 0x20..0x3f &&
            !(b[0] == 0x20 && b[1] == 0x01 && (b[2] < 2 || (b[2] == 0x0d && b[3] == 0xb8))) &&
            !(b[0] == 0x20 && b[1] == 0x02) && !(b[0] == 0x3f && b[1] == 0xff)
    }
}
