package com.anilili.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnitakuConfigurationTest {
    private val endpoint = "https://provider.example/api"

    @Test
    fun `no configuration means disabled`() {
        assertNull(AnitakuConfiguration.parse("", ""))
        assertNull(AnitakuConfiguration.parse(endpoint, "{}"))
        assertNull(AnitakuConfiguration.parse(endpoint, """{"10":{"sub":[]}}"""))
    }

    @Test
    fun `only explicitly listed audio and episodes are available`() {
        val config = requireNotNull(AnitakuConfiguration.parse(endpoint,
            """{"10":{"sub":[1,3,3],"dub":[2]},"20":{"dub":[4]}}"""))
        assertEquals(setOf(1, 3), config.availability(10).sub)
        assertEquals(setOf(2), config.availability(10).dub)
        assertTrue(config.permits(10, "sub", 3))
        assertTrue(config.permits(20, "dub", 4))
        assertFalse(config.permits(10, "sub", 2))
        assertFalse(config.permits(10, "dub", 3))
        assertFalse(config.permits(10, "ssub", 1))
        assertFalse(config.containsTitle(30))
        assertFalse(config.permits(30, "sub", 1))
    }

    @Test
    fun `invalid catalog never partially enables provider`() {
        listOf(
            """{"10":{"sub":[1]},"20":{"sub":[0]}}""",
            """{"10":{"sub":["1"]}}""",
            """{"10":{"dub":true}}""",
            """{"10":{"sub":[1.5]}}""",
            """{"01":{"sub":[1]}}""",
            """{"10":{"sub":[1],"unknown":[2]}}""",
            "not json"
        ).forEach { assertNull(AnitakuConfiguration.parse(endpoint, it)) }
    }

    @Test
    fun `endpoint must be https without credentials query or fragment`() {
        val catalog = """{"10":{"sub":[1]}}"""
        listOf("http://provider.example/api", "https://user:pass@provider.example/api",
            "https://provider.example/api?token=secret", "https://provider.example/api#fragment"
        ).forEach { assertNull(AnitakuConfiguration.parse(it, catalog)) }
    }
}
