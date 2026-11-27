package com.anilili.manga
import org.junit.Assert.*
import org.junit.Test
class MangaProviderHealthCacheTest {
    @Test fun revisionAndTimeLimit() {
        assertTrue(MangaProviderHealthCache.decode("r|1000|READY", "r", 2000)!!.ready)
        assertNull(MangaProviderHealthCache.decode("r|1000|READY", "different", 2000))
        assertNull(MangaProviderHealthCache.decode("r|1000|READY", "r", 1801000))
        assertNull(MangaProviderHealthCache.decode("r|1000|READY", "r", 999))
        assertEquals(3599000L, MangaProviderHealthCache.decode("r|1000|RATE_LIMIT/SELECT_TITLE", "r", 2000)!!.remainingMs)
    }
}
