package com.anilili.manga

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class SourceHealth(val label: String) {
    UNTESTED("Not checked yet"), CATALOG("Catalog reachable"), VERIFIED("Reader verified"),
    RESTRICTED("Access restricted"), RATE_LIMITED("Rate limited"), SERVER_TLS("Provider SSL error"), FAILED("Temporarily unavailable")
}

internal data class SourceObservation(val status: SourceHealth, val checkedAt: Long, val failures: Int = 0) {
    fun retryAfter(): Long = checkedAt + when (status) {
        SourceHealth.RESTRICTED, SourceHealth.SERVER_TLS -> 60 * 60_000L
        SourceHealth.RATE_LIMITED -> 30 * 60_000L
        SourceHealth.FAILED -> (60_000L * (1L shl failures.coerceIn(0, 6))).coerceAtMost(60 * 60_000L)
        else -> 0L
    }
    fun current(now: Long): SourceHealth {
        if (now < checkedAt) return SourceHealth.UNTESTED
        return when (status) {
            SourceHealth.CATALOG, SourceHealth.VERIFIED -> if (now - checkedAt <= 86_400_000L) status else SourceHealth.UNTESTED
            SourceHealth.UNTESTED -> status
            else -> if (now < retryAfter()) status else SourceHealth.UNTESTED
        }
    }
}

internal fun sourceHealthRank(status: SourceHealth): Int = when (status) {
    SourceHealth.VERIFIED -> 0
    SourceHealth.CATALOG -> 1
    SourceHealth.UNTESTED -> 2
    SourceHealth.FAILED, SourceHealth.RATE_LIMITED -> 3
    SourceHealth.RESTRICTED, SourceHealth.SERVER_TLS -> 4
}
internal fun sourceReady(status: SourceHealth): Boolean = sourceHealthRank(status) <= 2
internal fun sourceCanBrowse(status: SourceHealth): Boolean = sourceReady(status) || status == SourceHealth.FAILED
internal fun downstreamHealth(previous: SourceHealth, failure: SourceHealth): SourceHealth =
    if (failure == SourceHealth.FAILED && previous in setOf(SourceHealth.CATALOG, SourceHealth.VERIFIED)) SourceHealth.CATALOG else failure
internal fun classifySourceFailure(error: Throwable): SourceHealth {
    val messages = generateSequence(error) { it.cause?.takeUnless { cause -> cause === it } }
        .take(8).joinToString(" ") { it.message.orEmpty() + " " + it.javaClass.simpleName }
    return when {
        Regex("\\b526\\b|\\b525\\b|SSLHandshakeException|SSLPeerUnverifiedException", RegexOption.IGNORE_CASE).containsMatchIn(messages) -> SourceHealth.SERVER_TLS
        Regex("\\b429\\b|TooManyRequests", RegexOption.IGNORE_CASE).containsMatchIn(messages) -> SourceHealth.RATE_LIMITED
        Regex("\\b403\\b|\\b401\\b|AuthRequired|Authorization required|Browser is not available|Cloudflare|captcha", RegexOption.IGNORE_CASE).containsMatchIn(messages) -> SourceHealth.RESTRICTED
        else -> SourceHealth.FAILED
    }
}

/** Device-local observations; a successful sample never guarantees every chapter works. */
internal class MangaSourceHealthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("manga_source_health_v1", Context.MODE_PRIVATE)
    private val observations = prefs.all.mapNotNull { (id, value) ->
        val parts = (value as? String)?.split('|') ?: return@mapNotNull null
        val time = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        val status = runCatching { SourceHealth.valueOf(parts.first()) }.getOrNull() ?: return@mapNotNull null
        id to SourceObservation(status, time, parts.getOrNull(2)?.toIntOrNull() ?: 0)
    }.toMap().toMutableMap()
    private val mutable = MutableStateFlow(snapshot())
    val states = mutable.asStateFlow()
    private fun snapshot(): Map<String, SourceHealth> = observations.mapValues { it.value.current(System.currentTimeMillis()) }
    @Synchronized fun refresh() { mutable.value = snapshot() }
    @Synchronized fun reset(id: String) { observations.remove(id); prefs.edit().remove(id).apply(); mutable.value = snapshot() }
    @Synchronized fun recordReaderFailure(id: String, failure: SourceHealth) {
        val prior = observations[id]?.current(System.currentTimeMillis()) ?: SourceHealth.UNTESTED
        val status = downstreamHealth(prior, failure)
        if (status != failure) {
            val next = SourceObservation(status, System.currentTimeMillis())
            observations[id] = next
            prefs.edit().putString(id, "${next.status.name}|${next.checkedAt}|${next.failures}").apply()
            mutable.value = snapshot()
        } else record(id, failure)
    }
    @Synchronized fun record(id: String, status: SourceHealth) {
        val old = observations[id]
        if (status == SourceHealth.CATALOG && old?.current(System.currentTimeMillis()) == SourceHealth.VERIFIED) return
        val count = if (sourceReady(status)) 0 else ((old?.failures ?: 0) + 1).coerceAtMost(6)
        val next = SourceObservation(status, System.currentTimeMillis(), count)
        observations[id] = next
        prefs.edit().putString(id, "${next.status.name}|${next.checkedAt}|${next.failures}").apply()
        mutable.value = snapshot()
    }
}
