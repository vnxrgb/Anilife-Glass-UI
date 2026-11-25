package com.anilili.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGlassContrastTest {
    @Test fun themedBackingRetainsGlassButDominatesArtwork() {
        assertTrue(navigationGlassOpacity(true) in .80f..90f)
        assertTrue(navigationGlassOpacity(false) in .75f..90f)
    }
}
