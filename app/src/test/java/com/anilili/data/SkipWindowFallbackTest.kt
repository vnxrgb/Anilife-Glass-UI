package com.anilili.data

import com.anilili.data.model.SkipTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkipWindowFallbackTest {
    @Test fun fillsMissingOutroWithoutOverwritingIntro() {
        val primary = SkipTimes(introStart = 10.0, introEnd = 90.0, introSkipId = "primary")
        val fallback = SkipTimes(introStart = 20.0, introEnd = 100.0, outroStart = 1200.0, outroEnd = 1290.0, outroSkipId = "fallback")
        val result = mergeSkipWindows(primary, fallback)!!
        assertEquals(10.0, result.introStart!!, 0.0)
        assertEquals("primary", result.introSkipId)
        assertEquals(1200.0, result.outroStart!!, 0.0)
        assertEquals("fallback", result.outroSkipId)
    }

    @Test fun replacesIncompleteWindowAsAUnit() {
        val result = mergeSkipWindows(
            SkipTimes(introStart = 10.0),
            SkipTimes(introStart = 20.0, introEnd = 100.0)
        )!!
        assertEquals(20.0, result.introStart!!, 0.0)
        assertEquals(100.0, result.introEnd!!, 0.0)
    }

    @Test fun noSourcesReturnsNull() {
        assertNull(mergeSkipWindows(null, null))
    }
}
