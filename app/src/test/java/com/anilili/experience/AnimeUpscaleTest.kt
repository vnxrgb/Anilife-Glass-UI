package com.anilili.experience

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeUpscaleTest {
    @Test fun caps720pAt1080p() {
        val size = upscaleSize(1280, 720, AnimeUpscaleMode.HIGH, 4096, false)
        assertEquals(1920, size.width)
        assertEquals(1080, size.height)
    }
    @Test fun doublesLowResolution() {
        val size = upscaleSize(640, 360, AnimeUpscaleMode.HIGH, 4096, false)
        assertEquals(1280, size.width)
        assertEquals(720, size.height)
    }
    @Test fun bypassesHdrAndFullHd() {
        assertEquals(720, upscaleSize(1280,720,AnimeUpscaleMode.HIGH,4096,true).height)
        assertEquals(1080, upscaleSize(1920,1080,AnimeUpscaleMode.HIGH,4096,false).height)
    }
}
