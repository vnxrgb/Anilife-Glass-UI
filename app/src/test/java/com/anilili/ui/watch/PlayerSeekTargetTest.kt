package com.anilili.ui.watch

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSeekTargetTest {
    @Test
    fun insetTimelineMapsThumbCentersToExactEndpoints() {
        assertEquals(0L, seekTargetOnInsetTrackMs(6f, 300f, 6f, 90_000L))
        assertEquals(45_000L, seekTargetOnInsetTrackMs(150f, 300f, 6f, 90_000L))
        assertEquals(90_000L, seekTargetOnInsetTrackMs(294f, 300f, 6f, 90_000L))
    }

    @Test
    fun tapsOutsideTrackClampWithoutOverflow() {
        assertEquals(0L, seekTargetOnInsetTrackMs(-10f, 300f, 6f, 90_000L))
        assertEquals(90_000L, seekTargetOnInsetTrackMs(500f, 300f, 6f, 90_000L))
        assertEquals(0L, seekTargetOnInsetTrackMs(5f, 10f, 6f, 90_000L))
        assertEquals(0L, seekTargetOnInsetTrackMs(10f, 300f, -3f, 90_000L))
    }

    @Test
    fun unknownDurationOrInvalidPointerIsSafe() {
        assertEquals(0L, seekTargetMs(Float.NaN, 300f, 90_000L))
        assertEquals(0L, seekTargetMs(Float.POSITIVE_INFINITY, 300f, 90_000L))
        assertEquals(0L, seekTargetMs(150f, Float.NaN, 90_000L))
        assertEquals(0L, seekTargetOnInsetTrackMs(150f, 300f, Float.NaN, 90_000L))
        assertEquals(0L, seekTargetOnInsetTrackMs(150f, 300f, 6f, -1L))
    }
}
