package com.anilumos.manga.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProviderRefreshTest {
    @Test fun checksAreRateLimitedAndRecoveryIsPossible() = runBlocking {
        var clock = 10L
        var calls = 0
        val health = ProviderHealth { clock }
        val refresh = ProviderRefresh(health, { clock }, 100L)
        val profile = ReviewedProfile(SourceScope("FIXTURE", setOf("example.org")), "fixture", 1f)
        suspend fun probe(p: ReviewedProfile): ProviderCheck {
            calls++
            return health.record(p.scope.sourceName, if (calls == 1) ProviderState.NETWORK_ERROR else ProviderState.READY)
        }
        refresh.refresh(listOf(profile), ::probe)
        refresh.refresh(listOf(profile), ::probe)
        assertEquals(1, calls)
        assertFalse(health.visible("FIXTURE"))
        clock += 100
        refresh.refresh(listOf(profile), ::probe)
        assertEquals(2, calls)
        assertTrue(health.visible("FIXTURE"))
    }
}
