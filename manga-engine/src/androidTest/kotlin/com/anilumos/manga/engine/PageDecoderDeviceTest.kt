package com.anilumos.manga.engine

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PageDecoderDeviceTest {
    private fun fixture(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.BLUE)
            return ByteArrayOutputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
    @Test fun decodeLocalPng() = runBlocking {
        val bitmap = PageDecoder.decode(fixture(32, 64))
        try {
            assertEquals(32, bitmap.width)
            assertEquals(64, bitmap.height)
            assertEquals(android.graphics.Color.BLUE, bitmap.getPixel(0, 0))
        } finally { bitmap.recycle() }
    }
    @Test fun decodeDownsampledPage() = runBlocking {
        val bitmap = PageDecoder.decode(fixture(1024, 2048), 256, 512)
        try {
            assertTrue(bitmap.width <= 256)
            assertTrue(bitmap.height <= 512)
        } finally { bitmap.recycle() }
    }
    // Locally generated 16x16 blue AVIF, not a provider image.
    private val avifFixture = "AAAAIGZ0eXBhdmlmAAAAAGF2aWZtaWYxbWlhZk1BMUIAAADrbWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAcGljdAAAAAAAAAAAAAAAAAAAAAAOcGl0bQAAAAAAAQAAAB5pbG9jAAAAAEQAAAEAAQAAAAEAAAETAAAAKwAAAChpaW5mAAAAAAABAAAAGmluZmUCAAAAAAEAAGF2MDFDb2xvcgAAAABqaXBycAAAAEtpcGNvAAAAFGlzcGUAAAAAAAAAEAAAABAAAAAQcGl4aQAAAAADCAgIAAAADGF2MUOBAAwAAAAAE2NvbHJuY2x4AAEADQAGgAAAABdpcG1hAAAAAAAAAAEAAQQBAoMEAAAAM21kYXQSAAoJGAz/2iAhoNCAMhwUx4eGZQIIIJ5QAAAAOtxBfmJIya4wuDPFbwcg"
    @Test fun decodeLegacyAvifFixture() = runBlocking {
        val bytes = android.util.Base64.decode(avifFixture, android.util.Base64.DEFAULT)
        val bitmap = PageDecoder.decodeAvif(bytes, 8, 8)
        try {
            assertEquals(8, bitmap.width)
            assertEquals(8, bitmap.height)
            assertTrue(android.graphics.Color.blue(bitmap.getPixel(0, 0)) > 180)
        } finally { bitmap.recycle() }
        val automatic = PageDecoder.decode(bytes)
        try {
            assertEquals(16, automatic.width)
            assertEquals(16, automatic.height)
        } finally { automatic.recycle() }
    }
    @Test fun rejectInvalidAvif() = runBlocking {
        assertTrue(runCatching { PageDecoder.decodeAvif(byteArrayOf(1, 2, 3), 8, 8) }.exceptionOrNull() is IOException)
    }
    @Test fun rejectInvalidBytes() = runBlocking {
        assertTrue(runCatching { PageDecoder.decode(byteArrayOf(1, 2, 3)) }.exceptionOrNull() is IOException)
    }
}
