package com.anilili.data.wasm

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class WasmPipelineInstrumentedTest {
    @OptIn(UnstableApi::class)
    @Test fun verifiedComponentCrossesIsolatedBoundary() = runBlocking {
        assumeTrue(Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("wasm/fixture.wasm").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val approved = ApprovedWasmProvider("fixture", 1, digest, true, setOf("example.org"), setOf("en"))
        val query = WasmEpisodeQuery(1, null, listOf("Fixture"), 1.0, true)
        val loader = WasmProviderLoader(instrumentation.targetContext)
        assertEquals(0, loader.resolveVerified(approved.copy(digest = "0".repeat(64)), bytes, query).length())
        // Requires network access to example.org; returned URL is metadata, not playable media.
        val result = loader.resolveVerified(approved, bytes, query)
        assertEquals(1, result.length())
        assertTrue(result.getJSONObject(0).getBoolean("dub"))
        assertEquals("https://example.org/fixture.mp4", result.getJSONObject(0).getJSONArray("streams").getJSONObject(0).getString("url"))
        val protected = WasmPlaybackSource.fromValidated(approved, result, true).single()
        assertTrue(protected.handle.startsWith(WasmPlaybackSource.MEDIA_ID_PREFIX))
        assertEquals(protected.handle, protected.playerStream().url)
        assertFalse(protected.playerStream().url.contains("example.org"))
        assertNotNull(protected.mediaSource())
    }
}
