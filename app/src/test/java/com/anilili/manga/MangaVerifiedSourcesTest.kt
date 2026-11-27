package com.anilili.manga

import org.junit.Assert.*
import org.junit.Test

class MangaVerifiedSourcesTest {
    private val now = 1_800_000_000_000L
    private fun manifest(ids: String, time: Long = now) = "parser=${MangaVerifiedSources.REVISION}\nchecked=$time\n$ids"
    @Test fun newlyVerifiedBuiltInIdsAreAccepted() {
        assertEquals(setOf("mangadex", "kotatsu:new_provider"), MangaVerifiedSources.parse(manifest("mangadex\nkotatsu:new_provider\n"), now))
    }
    @Test fun missingStaleOrInvalidManifestDoesNotEnableUnknownSources() {
        assertTrue(MangaVerifiedSources.parse(manifest("kotatsu:new_provider", now - 604_800_001L), now).isEmpty())
        assertTrue(MangaVerifiedSources.parse(manifest("https://example.org"), now).isEmpty())
        assertTrue(MangaVerifiedSources.parse(manifest("mangadex\nmangadex"), now).isEmpty())
        assertTrue(MangaVerifiedSources.parse(manifest("mangadex").replace(MangaVerifiedSources.REVISION, "wrong"), now).isEmpty())
    }
}
