package com.anilili.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AnivexaApiParserTest {
    private fun obj(raw: String) = Json.parseToJsonElement(raw).jsonObject

    @Test fun discoversOnlyReportedEpisodesAndTracks() {
        val root = obj("""{"animenosub":{"episodes":{"sub":[{"number":1,"audio":"sub"},{"number":3},{"number":0},{"number":4,"audio":"dub"}],"dub":[{"number":2}]}}}""")
        val result = AnivexaApiParser.availability(root, "animenosub")
        assertEquals(setOf(1, 3), result.sub)
        assertEquals(setOf(2), result.dub)
    }

    @Test fun dunyaDoesNotAdvertiseDub() {
        val root = obj("""{"animedunya":{"episodes":{"sub":[{"number":1}],"dub":[{"number":1}]}}}""")
        val result = AnivexaApiParser.availability(root, "animedunya")
        assertEquals(setOf(1), result.sub)
        assertTrue(result.dub.isEmpty())
        assertFalse(AnivexaApiIntegration.supports("animedunya", "dub"))
    }

    @Test(expected = IOException::class) fun errorCatalogIsNotAvailability() {
        AnivexaApiParser.availability(obj("""{"animenosub":{"error":"unavailable"}}"""), "animenosub")
    }

    @Test(expected = IOException::class) fun wrongEpisodeRejected() {
        AnivexaApiParser.sources(obj("""{"anilistId":9,"episode":2,"audio":"sub","streams":[]}"""), 9, 1, "sub")
    }

    @Test fun parsesDirectStreamAndCaptions() {
        val root = obj("""{"anilistId":9,"episode":1,"audio":"sub","streams":[{"url":"https://example.test/video.m3u8","type":"hls","subtitles":[{"url":"https://example.test/en.vtt","label":"English","srclang":"en"}]}]}""")
        val result = AnivexaApiParser.sources(root, 9, 1, "sub")
        assertEquals(1, result.streams.size)
        assertEquals(1, result.subtitles.size)
    }

    @Test fun requiresHttpsConfiguration() {
        assertNull(AnivexaApiIntegration.endpoint(""))
        assertNull(AnivexaApiIntegration.endpoint("http://example.test"))
        assertNull(AnivexaApiIntegration.endpoint("https://user:secret@example.test"))
        assertNotNull(AnivexaApiIntegration.endpoint("https://example.test/api"))
    }
}
