package com.anilumos.manga.engine

import org.junit.Assert.*
import org.junit.Test

class ImageAcceptTest {
    @Test fun olderAndroidDoesNotAdvertiseAvif() {
        assertFalse(PageDelivery.imageAccept(30).contains("avif"))
        assertTrue(PageDelivery.imageAccept(26).contains("image/webp"))
        assertTrue(PageDelivery.imageAccept(30).contains("image/jpeg"))
    }
    @Test fun newerAndroidAdvertisesAvif() {
        assertTrue(PageDelivery.imageAccept(31).contains("image/avif"))
        assertTrue(PageDelivery.imageAccept(34).contains("image/png"))
    }
}
