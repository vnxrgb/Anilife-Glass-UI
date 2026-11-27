package com.anilumos.manga.engine

import org.junit.Assert.*
import org.junit.Test
import org.koitharu.kotatsu.parsers.config.ConfigKey

class ReviewedSourceConfigTest {
    @Test fun sourcePreferencesAreIsolatedAndRestrictionsRemainOff() {
        val a = ReviewedSourceConfig(splitTranslations = true)
        val b = ReviewedSourceConfig(splitTranslations = false)
        assertTrue(a[ConfigKey.SplitByTranslations(false)])
        assertFalse(b[ConfigKey.SplitByTranslations(true)])
        assertFalse(a[ConfigKey.ShowSuspiciousContent(true)])
        assertFalse(a[ConfigKey.InterceptCloudflare(true)])
        assertTrue(a[ConfigKey.DisableUpdateChecking(false)])
        assertEquals("example.org", a[ConfigKey.Domain("example.org", "other.example.org")])
    }
}
