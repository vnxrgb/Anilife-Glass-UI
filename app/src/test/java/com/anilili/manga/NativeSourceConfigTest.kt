package com.anilili.manga

import org.junit.Assert.*
import org.junit.Test
import org.koitharu.kotatsu.parsers.config.ConfigKey

class NativeSourceConfigTest {
    @Test fun defaultSettingsRemainCompatibleWithoutAndroidContext() {
        val config = NativeSourceConfig(null, "TEST")
        assertEquals("example.org", config[ConfigKey.Domain("example.org", "example.net")])
        assertTrue(config[ConfigKey.SplitByTranslations(true)])
        assertFalse(config[ConfigKey.ShowSuspiciousContent(true)])
        assertFalse(config[ConfigKey.InterceptCloudflare(true)])
        assertNull(config.defaultSort())
    }
    @Test fun onlyDeclaredProviderDomainsAreAccepted() {
        val config = NativeSourceConfig(null, "TEST")
        val domain = ConfigKey.Domain("example.org", "example.net")
        config.selectDomain(domain, "example.net")
        assertThrows(IllegalArgumentException::class.java) { config.selectDomain(domain, "unlisted.example") }
    }
}
