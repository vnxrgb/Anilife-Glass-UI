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
    @Test fun rejectInvalidBytes() = runBlocking {
        assertTrue(runCatching { PageDecoder.decode(byteArrayOf(1, 2, 3)) }.exceptionOrNull() is IOException)
    }
}
