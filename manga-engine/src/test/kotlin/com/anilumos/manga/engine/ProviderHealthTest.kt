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
    @Test fun diagnosticStagesDifferentiateImageFailureAndRestriction() = runBlocking {
        val health = ProviderHealth { 123L }
        val image = health.checkStaged("image") { stage ->
            stage(ProbeStage.IMAGE_DECODE)
            throw IOException("fixture secret URL")
        }
        assertEquals(ProviderState.IMAGE_ERROR, image.state)
        assertEquals(ProbeStage.IMAGE_DECODE, image.stage)
        val restricted = health.checkStaged("restricted") { stage ->
            stage(ProbeStage.PAGE_DOWNLOAD)
            throw ProviderAccessException(429)
        }
        assertEquals(429, restricted.httpStatus)
        assertEquals(ProbeStage.PAGE_DOWNLOAD, restricted.stage)
        val browser = health.checkStaged("browser") { stage ->
            stage(ProbeStage.SELECT_TITLE)
            throw ProviderFeatureException(UnsupportedFeature.BROWSER_EXECUTION)
        }
        assertEquals(ProviderState.UNSUPPORTED, browser.state)
        assertEquals(UnsupportedFeature.BROWSER_EXECUTION, browser.feature)
        assertFalse(health.snapshot().toString().contains("fixture secret URL"))
        health.checkStaged("good") { ProviderState.READY }
        assertTrue(health.visible("good"))
        assertEquals(ProbeStage.COMPLETE, health.snapshot()["good"]?.stage)
        assertFalse(health.visible("image"))
    }
    @Test fun restrictionsUnsupportedAndCancellationAreDistinct() = runBlocking {
        val health = ProviderHealth()
        assertEquals(ProviderState.RESTRICTED, health.check("a") { throw ProviderAccessException(429) }.state)
        assertEquals(ProviderState.UNSUPPORTED, health.check("b") { throw UnsupportedOperationException() }.state)
        assertTrue(runCatching { health.check("c") { throw CancellationException() } }.exceptionOrNull() is CancellationException)
        assertFalse(health.visible("c"))
    }
}
