package com.anilumos.manga.engine
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.graphics.Color
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class CanvasBitmapDeviceTest {
    @Test fun wholeImageCompositionIsBounded() {
        CanvasBitmap.create(2,2).use { source ->
            source.pixels.eraseColor(Color.RED)
            CanvasBitmap.create(4,4).use { target ->
                target.drawBitmap(source, Rect(0,0,2,2), Rect(0,0,4,4))
                assertEquals(Color.RED, target.pixels.getPixel(2,2))
                assertThrows(IllegalArgumentException::class.java) { target.drawBitmap(source, Rect(0,0,1,1), Rect(0,0,2,2)) }
            }
        }
        assertThrows(IllegalArgumentException::class.java) { CanvasBitmap.create(4096,4096) }
    }
}
