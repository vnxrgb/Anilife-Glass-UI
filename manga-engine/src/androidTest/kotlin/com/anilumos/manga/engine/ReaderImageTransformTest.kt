package com.anilumos.manga.engine

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderImageTransformTest {
    @Test fun rotatesAndCropsLocalRaster() = runBlocking {
        val original = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888)
        try {
            original.eraseColor(android.graphics.Color.BLUE)
            val rotated = ReaderImageTransform.rotateQuarterTurns(original, 1)
            try {
                assertEquals(32, rotated.width)
                assertEquals(16, rotated.height)
                val cropped = ReaderImageTransform.crop(rotated, 0, 0, 8, 8)
                try {
                    assertEquals(8, cropped.width)
                    assertEquals(android.graphics.Color.BLUE, cropped.getPixel(0, 0))
                } finally { if (cropped !== rotated) cropped.recycle() }
            } finally { if (rotated !== original) rotated.recycle() }
            assertFalse(original.isRecycled)
            assertTrue(runCatching { ReaderImageTransform.crop(original, 15, 0, 2, 1) }.exceptionOrNull() is IllegalArgumentException)
        } finally { original.recycle() }
    }
}
