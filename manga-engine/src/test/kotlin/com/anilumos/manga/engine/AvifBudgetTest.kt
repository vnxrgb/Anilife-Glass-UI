package com.anilumos.manga.engine

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class AvifBudgetTest {
    @Test fun boundsOutput() {
        assertEquals(500 to 1000, PageDecoder.avifOutputSize(2000, 4000, 500, 1000))
    }
    @Test fun rejectsOversizedSource() {
        assertTrue(runCatching { PageDecoder.avifOutputSize(8000, 8000, 2048, 4096) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { PageDecoder.avifOutputSize(0, 100, 2048, 4096) }.exceptionOrNull() is IOException)
    }
}
