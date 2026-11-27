package com.anilumos.manga.engine

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.Locale

/** A reviewed source's permitted hosts. Not a claim that a parser is available or licensed. */
internal class SourceScope(val sourceName: String, hosts: Set<String>, delegatedDnsSuffixes: Set<String> = emptySet(), imageRedirectHosts: Set<String> = emptySet(), imageDomainHosts: Set<String> = emptySet()) {
    private val allowed = hosts.map { it.trim().lowercase(Locale.ROOT) }.toSet()
    private val suffixes = delegatedDnsSuffixes.map { it.lowercase(Locale.ROOT) }.toSet()
    private val redirectHosts = imageRedirectHosts.map { it.lowercase(Locale.ROOT) }.toSet()
    // Only explicitly declared provider domains, never roots inferred from received URLs.
    private val imageDomains = imageDomainHosts.map { it.lowercase(Locale.ROOT) }.filter { host ->
        host in allowed && "https://$host/".toHttpUrlOrNull()?.topPrivateDomain() != null
    }.toSet()
    private fun belongsTo(host: String, root: String) = host == root || host.endsWith(".$root")
    private fun secure(url: HttpUrl) = url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()
    fun permitsImage(url: HttpUrl): Boolean = permits(url) || (secure(url) && imageDomains.any { belongsTo(url.host, it) })
    init {
        require(redirectHosts.all { it in allowed || suffixes.any { suffix -> it.endsWith("." + suffix) } })
        require(suffixes.all { it.matches(Regex("[a-z0-9.-]+")) && it.contains('.') && !it.startsWith(".") && !it.endsWith(".") })
        require(sourceName.isNotBlank() && allowed.isNotEmpty())
        require(allowed.all { it.matches(Regex("[a-z0-9.-]+")) && !it.startsWith(".") && !it.endsWith(".") })
    }
    fun permits(url: HttpUrl): Boolean = url.isHttps && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() && (url.host in allowed || suffixes.any { url.host.endsWith("." + it) })

    fun permitsImageRedirect(from: HttpUrl, to: HttpUrl): Boolean = permitsImage(from) && permitsImage(to) &&
        (from.host == to.host || to.host in redirectHosts || imageDomains.any { belongsTo(from.host, it) && belongsTo(to.host, it) })

    fun denied(url: HttpUrl?): SourceScopeException {
        val host = url?.host?.takeIf { url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() }
            ?.let(LiveProviderProfiles::domain)
        return SourceScopeException(host)
    }
    fun requireImageUrl(value: String, base: String? = null): HttpUrl {
        val url = value.toHttpUrlOrNull() ?: base?.toHttpUrlOrNull()?.resolve(value)
        if (url == null || !permitsImage(url)) throw denied(url)
        return url
    }
    fun requireUrl(value: String, base: String? = null): HttpUrl {
        val url = value.toHttpUrlOrNull() ?: base?.toHttpUrlOrNull()?.resolve(value)
        if (url == null || !permits(url)) throw denied(url)
        return url
    }
}

internal class SourceScopeException(val candidateHost: String? = null) : IOException("URL outside reviewed source scope")
