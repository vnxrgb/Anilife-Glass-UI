package com.anilili.data.wasm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Dns
import okhttp3.HttpUrl
import java.net.InetAddress
import java.net.Proxy
import java.security.PublicKey
import java.util.concurrent.TimeUnit

/** Immutable app-owned configuration. Never accept a URL or key supplied by guest code. */
internal data class WasmUpdateEndpoint(val base: HttpUrl, val pinnedKey: PublicKey)

internal object WasmUpdateConfiguration {
    // No authenticated publisher key or update origin has been provisioned yet.
    val endpoint: WasmUpdateEndpoint? = null
}

internal class WasmRemoteUpdater(private val context: Context) {
    suspend fun refresh(providerId: String): Boolean = withContext(Dispatchers.IO) {
        if (!com.anilili.BuildConfig.DEBUG) return@withContext false
        val config = WasmUpdateConfiguration.endpoint ?: return@withContext false
        val provider = WasmRegistry.bundled.singleOrNull { it.id == providerId } ?: return@withContext false
        if (!WasmPolicy.allowsUrl(config.base.toString(), setOf(config.base.host))) return@withContext false
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .callTimeout(20, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    Dns.SYSTEM.lookup(hostname).also { addresses ->
                        require(hostname == config.base.host && addresses.isNotEmpty() && addresses.all(PublicAddress::allowed))
                    }
            }).build()
        runCatching {
            fun fetch(name: String, max: Int): ByteArray {
                val url = config.base.newBuilder().addPathSegment(provider.id).addPathSegment(name).build()
                require(url.host == config.base.host && WasmPolicy.allowsUrl(url.toString(), setOf(config.base.host)))
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    require(response.isSuccessful)
                    val body = requireNotNull(response.body)
                    require(body.contentLength() <= max)
                    val output = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            require(output.size() + n <= max)
                            output.write(chunk, 0, n)
                        }
                    }
                    output.toByteArray()
                }
            }
            val manifest = fetch("manifest.json", 128 * 1024)
            val signature = fetch("manifest.sig", 1024)
            require(WasmManifestTrust.verify(manifest, signature, config.pinnedKey))
            val module = fetch("provider.wasm", WasmPolicy.MAX_MODULE_BYTES)
            WasmInstalledModules.install(context, provider, manifest, signature, module, config.pinnedKey)
        }.getOrDefault(false)
    }
}
