package com.anilumos.manga.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Foreground-only, serialized checks for explicitly supplied reviewed profiles.
 * No default provider list, background worker or unbounded retry loop. */
internal data class ReviewedProfile(val scope: SourceScope, val query: String, val chapterNumber: Float, val headers: SourceHeaders = SourceHeaders(scope)) {
    init { require(query.isNotBlank() && chapterNumber.isFinite() && chapterNumber >= 0f) }
}
internal class ProviderRefresh(
    private val health: ProviderHealth,
    private val now: () -> Long = System::currentTimeMillis,
    private val intervalMs: Long = 900_000L,
) {
    init { require(intervalMs > 0) }
    private val mutex = Mutex()
    private val last = mutableMapOf<String, ProviderCheck>()

    suspend fun refresh(profiles: List<ReviewedProfile>, probe: suspend (ReviewedProfile) -> ProviderCheck): Map<String, ProviderCheck> =
        mutex.withLock {
            require(profiles.map { it.scope.sourceName }.distinct().size == profiles.size)
            profiles.associate { profile ->
                val id = profile.scope.sourceName
                val previous = last[id]
                val result = if (previous != null && now() - previous.checkedAt in 0 until intervalMs) previous
                    else probe(profile).also { last[id] = it }
                id to result
            }
        }

    suspend fun refresh(registry: ParserRegistry, profiles: List<ReviewedProfile>): Map<String, ProviderCheck> =
        refresh(profiles) { health.probe(registry, it.scope, it.query, it.chapterNumber, it.headers) }
}
