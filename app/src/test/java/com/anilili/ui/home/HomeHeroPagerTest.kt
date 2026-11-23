package com.anilili.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeroPagerTest {
    @Test
    fun spotlightDotsNeverOverflowOnLongCarousels() {
        assertEquals(0..6, heroVisibleIndicatorIndices(0, 27))
        assertEquals(7..13, heroVisibleIndicatorIndices(10, 27))
        assertEquals(20..26, heroVisibleIndicatorIndices(26, 27))
        assertEquals(0..2, heroVisibleIndicatorIndices(1, 3))
        assertEquals(0..4, heroVisibleIndicatorIndices(2, 20, maxDots = 5))
        assertEquals(8..12, heroVisibleIndicatorIndices(10, 20, maxDots = 5))
        assertEquals(15..19, heroVisibleIndicatorIndices(19, 20, maxDots = 5))
        assertEquals(0..0, heroVisibleIndicatorIndices(0, 1))
        assertEquals(0..4, heroVisibleIndicatorIndices(-10, 20, maxDots = 5))
        assertEquals(IntRange.EMPTY, heroVisibleIndicatorIndices(0, 0))
        assertEquals(IntRange.EMPTY, heroVisibleIndicatorIndices(0, 7, 0))
    }

    @Test
    fun nextPageAdvancesAndWraps() {
        assertEquals(1, nextHeroPage(currentPage = 0, pageCount = 4))
        assertEquals(0, nextHeroPage(currentPage = 3, pageCount = 4))
    }

    @Test
    fun nextPageHandlesEmptyAndSingleItemLists() {
        assertEquals(0, nextHeroPage(currentPage = 0, pageCount = 0))
        assertEquals(0, nextHeroPage(currentPage = 0, pageCount = 1))
    }

    @Test
    fun nextPageClampsAStalePageAfterItemsChange() {
        assertEquals(0, nextHeroPage(currentPage = 10, pageCount = 3))
    }

    @Test
    fun slidingWrapsInBothDirections() {
        assertEquals(1, slideHeroPage(currentPage = 0, pageCount = 4, step = 1))
        assertEquals(0, slideHeroPage(currentPage = 3, pageCount = 4, step = 1))
        assertEquals(2, slideHeroPage(currentPage = 3, pageCount = 4, step = -1))
        // Left at the first slide reaches the last rather than stopping dead.
        assertEquals(3, slideHeroPage(currentPage = 0, pageCount = 4, step = -1))
    }

    @Test
    fun slidingHandlesEmptySingleAndStalePages() {
        assertEquals(0, slideHeroPage(currentPage = 0, pageCount = 0, step = 1))
        assertEquals(0, slideHeroPage(currentPage = 0, pageCount = 1, step = -1))
        // A page left over from a longer spotlight clamps to the last slide first, so Left from
        // it lands on the one before that rather than jumping to the start.
        assertEquals(1, slideHeroPage(currentPage = 10, pageCount = 3, step = -1))
    }
}
