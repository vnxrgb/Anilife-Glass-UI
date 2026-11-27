package com.anilumos.manga.engine

import android.test.InstrumentationTestCase
import android.util.Base64
import kotlinx.coroutines.runBlocking

@Suppress("DEPRECATION")
class PageDecoderDeviceTest : InstrumentationTestCase() {
    fun testDecodeLocalPng() = runBlocking {
        val bytes = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS1sAAAAASUVORK5CYII=", Base64.DEFAULT)
        val bitmap = PageDecoder.decode(bytes)
        try {
            assertEquals(1, bitmap.width)
            assertEquals(1, bitmap.height)
        } finally { bitmap.recycle() }
    }
    fun testRejectInvalidBytes() = runBlocking {
        assertTrue(runCatching { PageDecoder.decode(byteArrayOf(1, 2, 3)) }.exceptionOrNull() is java.io.IOException)
    }
}
