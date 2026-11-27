package com.anilumos.manga.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ProviderHealthTest {
    @Test fun failureIsIsolatedAndVisibilityExpires() = runBlocking {
        var clock = 100L
        val health = ProviderHealth { clock }
        assertFalse(health.visible("a"))
        health.check("a") { ProviderState.READY }
        health.check("b") { throw IOException("fixture") }
        assertTrue(health.visible("a"))
        assertFalse(health.visible("b"))
        clock += 900_001
        assertFalse(health.visible("a"))
        health.check("a") { ProviderState.READY }
        assertTrue(health.visible("a"))
    }
    @Test fun restrictionsUnsupportedAndCancellationAreDistinct() = runBlocking {
        val health = ProviderHealth()
        assertEquals(ProviderState.RESTRICTED, health.check("a") { throw ProviderAccessException(429) }.state)
        assertEquals(ProviderState.UNSUPPORTED, health.check("b") { throw UnsupportedOperationException() }.state)
        assertTrue(runCatching { health.check("c") { throw CancellationException() } }.exceptionOrNull() is CancellationException)
        assertFalse(health.visible("c"))
    }
}
