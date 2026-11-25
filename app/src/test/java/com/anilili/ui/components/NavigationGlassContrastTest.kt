package com.anilili.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGlassContrastTest {
    @Test fun themedBackingRetainsGlassButDominatesArtwork() {
        assertTrue(navigationGlassOpacity(true) in .60f..70f)
        assertTrue(navigationGlassOpacity(false) in .55f..70f)
    }
}
