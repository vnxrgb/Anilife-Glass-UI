package com.anilili.manga

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class MangaProviderVerificationTest {
    @Test fun verifyPublicSafeSources() {
        val output = System.getenv("MANGA_HEALTH_OUTPUT")
        assumeTrue("Network checks are opt-in", !output.isNullOrBlank())
        val shard = System.getenv("MANGA_HEALTH_SHARD")?.toIntOrNull() ?: 0
        val shards = System.getenv("MANGA_HEALTH_SHARDS")?.toIntOrNull() ?: 1
        require(shards in 1..32 && shard in 0 until shards)
        val sources = builtInKotatsuSources().filter { preferredMangaLanguage(it.languageTag) }.sortedBy { it.id }.filterIndexed { index, _ -> index % shards == shard }
        val file = File(requireNotNull(output))
        file.parentFile?.mkdirs()
        file.writeText("id\tstatus\tstage\n")
        runBlocking {
            for (source in sources) {
                val result = probeMangaSource(source)
                file.appendText("${source.id}\t${result.health.name}\t${result.stage}\n")
                delay(1500)
            }
        }
    }

    @Test fun normalizesLegacyPageUrls() {
        val base = "https://example.com/chapter/1/"
        org.junit.Assert.assertEquals("http://cdn.example.com/p.jpg", normalizePageUrl("http://cdn.example.com/p.jpg", base))
        org.junit.Assert.assertEquals("https://cdn.example.com/p.jpg", normalizePageUrl("//cdn.example.com/p.jpg", base))
        org.junit.Assert.assertEquals("https://example.com/chapter/1/p.jpg", normalizePageUrl("p.jpg", base))
        org.junit.Assert.assertNull(normalizePageUrl("file:///etc/passwd", base))
        org.junit.Assert.assertNull(normalizePageUrl("https://user:pass@example.com/p.jpg", base))
        org.junit.Assert.assertTrue(preferredMangaLanguage("zh-HK"))
        org.junit.Assert.assertFalse(preferredMangaLanguage("es"))
    }

    @Test fun imageHeadersAndVerifiedManifest() {
        org.junit.Assert.assertTrue(imageSignature(byteArrayOf(-1, -40, -1), 3))
        org.junit.Assert.assertFalse(imageSignature("<html>".toByteArray(), 6))
        val now = 1_800_000_000_000L
        val valid = "parser=${MangaVerifiedSources.REVISION}\nchecked=$now\nmangadex\nkotatsu:webtoons_en\n../../invalid"
        org.junit.Assert.assertEquals(setOf("mangadex", "kotatsu:webtoons_en"), MangaVerifiedSources.parse(valid, now))
        org.junit.Assert.assertTrue(MangaVerifiedSources.parse(valid, now + 259_200_001L).isEmpty())
    }
}
