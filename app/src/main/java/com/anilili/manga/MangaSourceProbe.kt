package com.anilili.manga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.IOException

internal data class SourceProbeResult(val health: SourceHealth, val stage: String)

/** One public, safe-rated sample; never solve challenges, log in, or weaken TLS. */
internal suspend fun probeMangaSource(source: MangaSource): SourceProbeResult {
    var stage = "catalog"
    return withTimeoutOrNull(90_000L) {
        try {
            val titles = source.catalog("", MangaKind.ALL, MangaOrder.POPULAR, limit = 5)
            if (titles.isEmpty()) return@withTimeoutOrNull SourceProbeResult(if ((source as? NativeCatalogProvider)?.rawCatalogCount()?.let { it > 0 } == true) SourceHealth.CATALOG else SourceHealth.FAILED, "no-safe-catalog")
            stage = "search"
            val safeSamples = titles.filterNot { it.unrated }
            if (safeSamples.isEmpty()) return@withTimeoutOrNull SourceProbeResult(SourceHealth.CATALOG, "publisher-catalog-unrated")
            val title = safeSamples.first()
            val matches = source.search(title.title.take(120), limit = 5)
            if (matches.isEmpty()) return@withTimeoutOrNull SourceProbeResult(SourceHealth.CATALOG, "search-empty")
            stage = "chapters"
            var candidate: MangaChapter? = null
            for (sample in safeSamples.take(3)) {
                val chapters = source.chapters(sample.id, if (source.id == "mangadex") "und" else source.chapterLanguages.firstOrNull() ?: "und")
                candidate = chapters.firstOrNull()
                if (candidate != null) break
            }
            val chapter = candidate ?: return@withTimeoutOrNull SourceProbeResult(SourceHealth.CATALOG, "no-chapters")
            stage = "pages"
            val page = source.pages(chapter.id).firstOrNull() ?: return@withTimeoutOrNull SourceProbeResult(SourceHealth.CATALOG, "no-pages")
            stage = "image"
            val valid = withContext(Dispatchers.IO) {
                val builder = Request.Builder().url(page.imageUrl)
                page.headers.forEach { (key, value) -> if (key.equals("Referer", true) || key.equals("User-Agent", true) || key.equals("Accept", true) || key.equals("Origin", true)) builder.header(key, value) }
                builder.header("Range", "bytes=0-63")
                MangaNetwork.client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val bytes = response.body.source().peek()
                    val prefix = ByteArray(16)
                    var count = 0
                    while (count < prefix.size) {
                        val n = bytes.read(prefix, count, prefix.size - count)
                        if (n <= 0) break
                        count += n
                    }
                    imageSignature(prefix, count)
                }
            }
            if (valid) SourceProbeResult(SourceHealth.VERIFIED, "image-header") else SourceProbeResult(SourceHealth.CATALOG, "invalid-image")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { SourceProbeResult(classifySourceFailure(e), stage) }
    } ?: SourceProbeResult(SourceHealth.FAILED, "timeout-$stage")
}

internal fun imageSignature(bytes: ByteArray, count: Int): Boolean {
    if (count < 3) return false
    fun b(i: Int) = bytes[i].toInt() and 255
    fun ascii(start: Int, value: String) = count >= start + value.length && value.indices.all { b(start + it) == value[it].code }
    return (b(0) == 255 && b(1) == 216 && b(2) == 255) ||
        (count >= 8 && b(0) == 137 && ascii(1, "PNG") && b(4) == 13 && b(5) == 10 && b(6) == 26 && b(7) == 10) ||
        ascii(0, "GIF87a") || ascii(0, "GIF89a") ||
        (ascii(0, "RIFF") && ascii(8, "WEBP")) ||
        (ascii(4, "ftyp") && (ascii(8, "avif") || ascii(8, "avis")))
}
