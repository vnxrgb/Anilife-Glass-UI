package com.anilili.manga

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class SourceHealth(val label: String) {
    UNTESTED("Untested"), CATALOG("Catalog works"), VERIFIED("Page loaded"),
    RESTRICTED("Access restricted"), SERVER_TLS("Provider SSL error"), FAILED("Unavailable")
}
internal fun classifySourceFailure(error: Throwable): SourceHealth {
    val messages = generateSequence(error) { it.cause?.takeUnless { cause -> cause === it } }
        .take(8).joinToString(" ") { it.message.orEmpty() + " " + it.javaClass.simpleName }
    return when {
        Regex("\\b526\\b|\\b525\\b|SSLHandshakeException|SSLPeerUnverifiedException", RegexOption.IGNORE_CASE).containsMatchIn(messages) -> SourceHealth.SERVER_TLS
        Regex("\\b403\\b|\\b401\\b|\\b429\\b|AuthRequired|Authorization required|Browser is not available|Cloudflare|captcha", RegexOption.IGNORE_CASE).containsMatchIn(messages) -> SourceHealth.RESTRICTED
        else -> SourceHealth.FAILED
    }
}

/** Health observations are local to this device/network, expire, and never imply all titles work. */
internal class MangaSourceHealthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("manga_source_health_v1", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    val states = mutable.asStateFlow()
    private fun load(): Map<String, SourceHealth> {
        val now = System.currentTimeMillis()
        return prefs.all.mapNotNull { (id, value) ->
            val parts = (value as? String)?.split('|') ?: return@mapNotNull null
            val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            val status = runCatching { SourceHealth.valueOf(parts.first()) }.getOrNull() ?: return@mapNotNull null
            if (now - timestamp !in 0..86_400_000L) null else id to status
        }.toMap()
    }
    @Synchronized fun record(id: String, status: SourceHealth) {
        if (status == SourceHealth.CATALOG && mutable.value[id] == SourceHealth.VERIFIED) return
        prefs.edit().putString(id, "${status.name}|${System.currentTimeMillis()}").apply()
        mutable.value = mutable.value + (id to status)
    }
}
