package com.anilili.manga

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalArchiveOrderTest {
    @Test fun naturalPageOrder() {
        val names = listOf("page10.png", "page2.png", "page1.png")
        assertEquals(listOf("page1.png", "page2.png", "page10.png"), names.sortedBy { LocalMangaSource.naturalKey(it) })
    }
}
