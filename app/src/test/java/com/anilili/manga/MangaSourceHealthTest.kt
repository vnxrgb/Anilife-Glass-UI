package com.anilili.manga

import org.junit.Assert.*
import org.junit.Test

class MangaSourceHealthTest {
    @Test fun cooldownExpiresWithoutDeletingSource() {
        val start = 1_000_000L
        val failed = SourceObservation(SourceHealth.FAILED, start, 2)
        assertEquals(SourceHealth.FAILED, failed.current(start + 100))
        assertEquals(SourceHealth.UNTESTED, failed.current(failed.retryAfter()))
        assertTrue(sourceReady(failed.current(failed.retryAfter())))
    }
    @Test fun proofExpiresAndUnverifiedIsNotClaimedWorking() {
        val start = 1_000_000L
        assertEquals(SourceHealth.UNTESTED, SourceObservation(SourceHealth.VERIFIED, start).current(start + 86_400_001L))
        assertTrue(sourceHealthRank(SourceHealth.VERIFIED) < sourceHealthRank(SourceHealth.UNTESTED))
        assertFalse(sourceReady(SourceHealth.RESTRICTED))
        assertFalse(sourceReady(SourceHealth.SERVER_TLS))
        assertFalse(sourceReady(SourceHealth.RATE_LIMITED))
    }
    @Test fun classifiesRateLimitSeparately() {
        assertEquals(SourceHealth.RATE_LIMITED, classifySourceFailure(java.io.IOException("HTTP 429")))
        assertEquals(SourceHealth.RESTRICTED, classifySourceFailure(java.io.IOException("HTTP 403")))
    }
}
