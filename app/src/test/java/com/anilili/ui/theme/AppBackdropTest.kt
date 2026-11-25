package com.anilili.ui.theme

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

class AppBackdropTest {

    @Test
    fun `light theme overlays protect text contrast at every dim setting`() {
        assertEquals(0.70f, backdropOverlayAlpha(0.50f, true), 0.001f)
        assertEquals(0.775f, backdropOverlayAlpha(0.65f, true), 0.001f)
        assertEquals(0.85f, backdropOverlayAlpha(0.80f, true), 0.001f)
    }

    @Test
    fun `dark theme retains original dim settings`() {
        for (dim in listOf(0.50f, 0.65f, 0.80f)) {
            assertEquals(dim, backdropOverlayAlpha(dim, false), 0.001f)
        }
    }


    @Test
    fun `a wide picture on a tall phone keeps its middle`() {
        // 1920x1080 onto 540x1200: the full height, a centred 486-wide column.
        assertEquals(IntRect(717, 0, 1203, 1080), centreCrop(1920, 1080, 540, 1200))
    }

    @Test
    fun `a tall picture on a television keeps its middle band`() {
        assertEquals(IntRect(0, 577, 1080, 1185), centreCrop(1080, 1763, 960, 540))
    }

    @Test
    fun `a picture already the window's shape is used whole`() {
        assertEquals(IntRect(0, 0, 1920, 1080), centreCrop(1920, 1080, 960, 540))
    }
}
