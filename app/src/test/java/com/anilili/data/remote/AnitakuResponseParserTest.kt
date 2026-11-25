package com.anilili.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AnitakuResponseParserTest {
    private fun obj(raw: String) = Json.parseToJsonElement(raw) as JsonObject

    @Test
    fun `selected dub uses only its own video and captions`() {
        val result = AnitakuResponseParser.sources(obj("""
            {
              "sub":{"streams":[{"url":"https://media.example/sub.m3u8","subtitles":[{"url":"https://media.example/sub.vtt","label":"English"}]}]},
              "dub":{"streams":[{"url":"https://media.example/dub.m3u8?token=abc","quality":"1080p",
                "headers":{"Referer":"https://player.example/watch","Origin":"https://player.example","Cookie":"not-forwarded"},
                "subtitles":[{"url":"https://media.example/dub.vtt","label":"English"}]}]}
            }
        """), "dub")
        val stream = result.streams.single()
        assertEquals("https://media.example/dub.m3u8?token=abc", stream.url)
        assertEquals("1080p", stream.quality)
        assertEquals("dub", stream.audio)
        assertEquals("https://player.example/watch", stream.referer)
        assertEquals(mapOf("Origin" to "https://player.example"), stream.headers)
        assertTrue(stream.isActive && stream.isHls)
        assertEquals("https://media.example/dub.vtt", result.subtitles.single().url)
        assertEquals("en", result.subtitles.single().language)
        assertNull(result.skip)
        assertNull(result.download)
    }

    @Test
    fun `missing dub does not silently switch to sub`() {
        val root = obj("""{"sub":{"streams":[{"url":"https://media.example/sub.m3u8"}]}}""")
        assertTrue(runCatching { AnitakuResponseParser.sources(root, "dub") }.exceptionOrNull() is IOException)
    }

    @Test
    fun `invalid or unsupported streams are skipped`() {
        val result = AnitakuResponseParser.sources(obj("""
            {"sub":{"streams":[
                {"url":"http://media.example/insecure.m3u8"},
                {"url":"https://user:password@media.example/private.m3u8"},
                {"url":"https://media.example/embed.html"},
                {"url":"https://media.example/valid.m3u8","subtitles":[
                    {"url":"http://media.example/insecure.vtt"},
                    {"url":"https://media.example/valid.vtt","label":"English"},
                    {"url":"https://media.example/valid.vtt","label":"Duplicate"}
                ]}
            ]}}
        """), "sub")
        assertEquals("https://media.example/valid.m3u8", result.streams.single().url)
        assertEquals("Auto", result.streams.single().quality)
        assertEquals(1, result.subtitles.size)
        assertEquals("text/vtt", result.subtitles.single().mimeType)
    }

    @Test
    fun `invalid origin does not reach playback`() {
        val root = obj("""{"sub":{"streams":[{"url":"https://media.example/sub.m3u8",
            "headers":{"Origin":"https://player.example/not-an-origin"}}]}}""")
        assertTrue(runCatching { AnitakuResponseParser.sources(root, "sub") }.exceptionOrNull() is IOException)
    }

    @Test
    fun `captions from a different encode are never combined`() {
        val root = obj("""{"sub":{"streams":[
          {"url":"https://media.example/first.m3u8"},
          {"url":"https://media.example/second.m3u8","subtitles":[{"url":"https://media.example/second.vtt"}]}
        ]}}""")
        val result = AnitakuResponseParser.sources(root, "sub")
        assertEquals("https://media.example/first.m3u8", result.streams.single().url)
        assertTrue(result.subtitles.isEmpty())
    }
}
