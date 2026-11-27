package com.anilumos.manga.engine

import org.junit.Assert.*
import org.junit.Test

class CompatibleImageBrandTest {
    @Test fun avifCompatibleBrandIsRecognized() {
        val bytes = byteArrayOf(0,0,0,20) + "ftypmif1".toByteArray() + byteArrayOf(0,0,0,0) + "avif".toByteArray()
        assertTrue(PageDelivery.matchesImageHeader(bytes, "avif"))
        assertFalse(PageDelivery.matchesImageHeader(bytes.copyOf(16), "avif"))
        assertFalse(PageDelivery.matchesImageHeader("html response".toByteArray(), "avif"))
    }
}
