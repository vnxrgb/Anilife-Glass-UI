package com.anilili.manga

import com.anilumos.manga.engine.OpenComicReader
import java.io.Closeable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal suspend fun openOpenComicSource(): MangaSource = OpenComicSource(OpenComicReader.open())

private class OpenComicSource(private val reader: OpenComicReader) : MangaSource, Closeable {
    override val id = OPEN_COMIC_SOURCE_ID
    override val displayName = "Pepper & Carrot"
    override val languageTag = "en"
    override val chapterLanguages = listOf("en")
    override val sourceCategory = "Open webcomic"
    private val title = MangaTitle(id, "pepper-carrot", "Pepper & Carrot",
        "A fantasy webcomic by David Revoy and contributors, published under Creative Commons Attribution 4.0. Original English episode images, including credits pages. https://www.peppercarrot.com — https://creativecommons.org/licenses/by/4.0/",
        null, genres = listOf("Fantasy", "Adventure"), authors = listOf("David Revoy and contributors"),
        officialLinks = listOf(MangaOfficialLink("Official webcomic and credits", "https://www.peppercarrot.com/en/"), MangaOfficialLink("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/")),
        knownChapterCount = reader.episodes.size)
    private val coverLock = Mutex()
    private var cachedCover: android.graphics.Bitmap? = null
    override suspend fun artwork(mangaId: String): Any? = coverLock.withLock {
        require(mangaId == title.id)
        cachedCover ?: reader.cover().also { cachedCover = it }
    }
    override suspend fun search(query: String, limit: Int): List<MangaTitle> =
        listOf(title).filter { it.title.contains(query.trim(), true) }.take(limit)
    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> {
        require(mangaId == title.id)
        return reader.episodes.asReversed().map {
            MangaChapter(id, it.number.toString(), title.id, it.title, it.number.toString(), "en", listOf("David Revoy and contributors · CC BY 4.0"))
        }
    }
    override suspend fun pages(chapterId: String): List<MangaPage> {
        val episode = reader.episodes.singleOrNull { it.number.toString() == chapterId } ?: error("Episode unavailable")
        return List(episode.pageCount) { index -> MangaPage(index, "", decode = { w, h -> reader.decode(episode.number, index, w, h) }) }
    }
    override fun close() { reader.close() }
}
