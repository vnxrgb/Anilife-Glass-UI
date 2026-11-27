package com.anilili.manga

import com.anilumos.manga.engine.ApprovedReader
import java.io.Closeable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun reviewedReaderSupported(): Boolean = true
internal suspend fun openReviewedReader(): MangaSource = EngineSampleSource(ApprovedReader.openReviewedSample())

private class EngineSampleSource(private val reader: ApprovedReader) : MangaSource, Closeable {
    override val id = REVIEWED_SOURCE_ID
    override val displayName = "Atsu"
    override val languageTag = "en"
    override val sampleOnly = true
    private val coverLock = Mutex()
    private var artworkLoaded = false
    private var coverBitmap: android.graphics.Bitmap? = null
    override suspend fun artwork(mangaId: String): Any? = coverLock.withLock {
        require(mangaId == title.id)
        if (!artworkLoaded) {
            coverBitmap = reader.cover()
            artworkLoaded = true
        }
        coverBitmap
    }
    override val chapterLanguages = listOf("und")
    private val title = reader.title.let {
        MangaTitle(id, it.id, it.name, androidx.core.text.HtmlCompat.fromHtml(it.description,
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim(), null,
            genres = it.genres, authors = it.authors, status = it.status, alternativeTitles = it.alternatives)
    }
    private val chapter = reader.chapter.let {
        MangaChapter(id, it.id, title.id, it.label, it.number, "und", listOfNotNull(it.scanlator))
    }
    override suspend fun search(query: String, limit: Int): List<MangaTitle> =
        listOf(title).filter { it.title.contains(query.trim(), ignoreCase = true) }.take(limit)
    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> {
        require(mangaId == title.id)
        return listOf(chapter)
    }
    override suspend fun pages(chapterId: String): List<MangaPage> {
        require(chapterId == chapter.id)
        return List(reader.pageCount) { index ->
            MangaPage(index, "", decode = { width, height -> reader.decode(index, width, height) })
        }
    }
    override fun close() = reader.close()
}
