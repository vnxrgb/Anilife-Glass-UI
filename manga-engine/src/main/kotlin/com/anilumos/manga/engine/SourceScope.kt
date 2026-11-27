package com.anilumos.manga.engine

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.Locale

/** A reviewed source's permitted hosts. Not a claim that a parser is available or licensed. */
internal class SourceScope(val sourceName: String, hosts: Set<String>, delegatedDnsSuffixes: Set<String> = emptySet(), imageRedirectHosts: Set<String> = emptySet()) {
    private val allowed = hosts.map { it.trim().lowercase(Locale.ROOT) }.toSet()
    private val suffixes = delegatedDnsSuffixes.map { it.lowercase(Locale.ROOT) }.toSet()
    private val redirectHosts = imageRedirectHosts.map { it.lowercase(Locale.ROOT) }.toSet()
    init {
        require(redirectHosts.all { it in allowed || suffixes.any { suffix -> it.endsWith("." + suffix) } })
        require(suffixes.all { it.matches(Regex("[a-z0-9.-]+")) && it.contains('.') && !it.startsWith(".") && !it.endsWith(".") })
        require(sourceName.isNotBlank() && allowed.isNotEmpty())
        require(allowed.all { it.matches(Regex("[a-z0-9.-]+")) && !it.startsWith(".") && !it.endsWith(".") })
    }
    fun permits(url: HttpUrl): Boolean = url.isHttps && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() && (url.host in allowed || suffixes.any { url.host.endsWith("." + it) })

    fun permitsImageRedirect(from: HttpUrl, to: HttpUrl): Boolean = permits(from) && permits(to) &&
        (from.host == to.host || to.host in redirectHosts)

    fun requireUrl(value: String, base: String? = null): HttpUrl {
        val url = value.toHttpUrlOrNull() ?: base?.toHttpUrlOrNull()?.resolve(value)
        if (url == null || !permits(url)) throw SourceScopeException()
        return url
    }
}

internal class SourceScopeException : IOException("URL outside reviewed source scope")
