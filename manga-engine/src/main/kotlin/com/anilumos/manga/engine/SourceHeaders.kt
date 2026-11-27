package com.anilumos.manga.engine

import okhttp3.Request
import java.util.Locale

/** Optional, explicitly reviewed defaults. No credentials, browser impersonation or cross-host defaults. */
internal class SourceHeaders(private val scope: SourceScope, byHost: Map<String, Map<String, String>> = emptyMap()) {
    private val allowedNames = setOf("accept", "accept-language")
    private val entries = byHost.mapKeys { it.key.lowercase(Locale.ROOT) }.mapValues { (_, headers) ->
        headers.mapKeys { it.key.lowercase(Locale.ROOT) }.also { normalized ->
            require(normalized.keys.all { it in allowedNames })
            require(normalized.values.all { value -> value.isNotBlank() && value.length <= 512 && value.all { it.code in 32..126 } })
        }
    }
    init { entries.keys.forEach { scope.requireUrl("https://$it/") } }
    fun apply(request: Request): Request {
        require(scope.permits(request.url))
        val builder = request.newBuilder()
        entries[request.url.host].orEmpty().forEach { (name, value) ->
            if (request.header(name) == null) builder.header(name, value)
        }
        return builder.build()
    }
}
