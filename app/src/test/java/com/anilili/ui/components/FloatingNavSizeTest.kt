package com.anilili.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingNavSizeTest {
    @Test
    fun narrowWindowsKeepIconsWithoutTryingToFitUnreadableCaptions() {
        assertFalse(floatingNavShowsCaption(0.dp))
        assertFalse(floatingNavShowsCaption(44.dp))
        assertFalse(floatingNavShowsCaption(57.dp))
        assertTrue(floatingNavShowsCaption(58.dp))
        assertTrue(floatingNavShowsCaption(64.dp))
    }
}
