package com.anilili.ui.watch

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAmbientTintTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private val width = 40
    private val height = 24

    @Test
    fun blankAndGreyFramesKeepThePreviousAmbientColor() {
        assertNull(extractVideoAmbientTint(IntArray(width * height) { rgb(0, 0, 0) }, width, height))
        assertNull(extractVideoAmbientTint(IntArray(width * height) { rgb(110, 110, 110) }, width, height))
    }

    @Test
    fun saturatedSceneGivesDistinctMutedColorWithoutBlindingThePlayer() {
        val pixels = IntArray(width * height) { rgb(190, 55, 35) }
        val color = extractVideoAmbientTint(pixels, width, height)
        assertNotNull(color)
        assertTrue(color!!.red > color.green)
        assertTrue(color.green > color.blue)
        assertTrue(color.red < 0.4f)
    }

    @Test
    fun subtitleStripDoesNotPolluteSceneAmbient() {
        val pixels = IntArray(width * height) { index ->
            if (index / width >= height * 4 / 5) rgb(25, 220, 35)
            else rgb(35, 80, 200)
        }
        val color = extractVideoAmbientTint(pixels, width, height)
        assertNotNull(color)
        assertTrue(color!!.blue > color.green)
        assertTrue(color.blue > color.red)
    }

    @Test
    fun malformedFramesDoNotCrashSampling() {
        assertNull(extractVideoAmbientTint(intArrayOf(0), 40, 24))
        assertNull(extractVideoAmbientTint(intArrayOf(), 0, 0))
    }
}
