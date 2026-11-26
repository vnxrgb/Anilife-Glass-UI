package com.anilili.ui.watch

import org.junit.Assert.*
import org.junit.Test

class AfterglowCompletionTest {
    @Test fun onlyFinishedSeriesAtDeclaredEnd() {
        assertTrue(afterglowSeriesComplete("FINISHED", 12, 12.0, false))
        assertFalse(afterglowSeriesComplete("FINISHED", 12, 11.0, false))
        assertFalse(afterglowSeriesComplete("RELEASING", 12, 12.0, false))
        assertFalse(afterglowSeriesComplete("FINISHED", null, 12.0, false))
        assertTrue(afterglowSeriesComplete("FINISHED", null, 1.0, true))
    }
}
