package com.anilumos.manga.engine

import org.junit.Assert.*
import org.junit.Test

class PageDecoderTest {
    @Test fun samplingKeepsLargePagesWithinBudget() {
        assertEquals(1, PageDecoder.sampleSize(800, 1200, 2048, 4096))
        assertEquals(4, PageDecoder.sampleSize(8000, 8000, 2048, 4096))
        assertEquals(4, PageDecoder.sampleSize(1000, 16000, 2048, 4096))
    }
    @Test fun dimensionsMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { PageDecoder.sampleSize(0, 10, 100, 100) }
    }
}
