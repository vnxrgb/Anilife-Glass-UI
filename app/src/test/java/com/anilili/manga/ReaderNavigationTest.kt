package com.anilili.manga

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderNavigationTest {
    @Test fun groupsKeepAllPagesAndOddEnding() {
        assertEquals(listOf(listOf(0,1), listOf(2,3), listOf(4)), readerPageGroups(5, true, false))
        assertEquals(listOf(listOf(0), listOf(1,2), listOf(3,4)), readerPageGroups(5, true, true))
        assertEquals(listOf(listOf(0), listOf(1)), readerPageGroups(2, false, true))
        assertEquals(emptyList<List<Int>>(), readerPageGroups(0, true, true))
    }
    @Test fun tapZonesRespectReadingDirectionAndInversion() {
        assertEquals(1, readerTapDirection(.1f,.5f,ReaderTapZones.SIDES,true,false))
        assertEquals(-1, readerTapDirection(.1f,.5f,ReaderTapZones.SIDES,false,false))
        assertEquals(-1, readerTapDirection(.1f,.5f,ReaderTapZones.SIDES,true,true))
        assertEquals(0, readerTapDirection(.5f,.5f,ReaderTapZones.SIDES,true,false))
        assertEquals(1, readerTapDirection(.5f,.9f,ReaderTapZones.TOP_BOTTOM,false,false))
        assertEquals(0, readerTapDirection(.9f,.9f,ReaderTapZones.OFF,false,false))
    }
}
