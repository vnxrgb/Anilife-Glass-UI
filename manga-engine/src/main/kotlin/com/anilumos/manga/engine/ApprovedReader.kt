package com.anilumos.manga.engine

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

/** Narrow app-facing contract for the reviewed research sample only.
 * No parser objects, URLs, cookies or headers cross the module boundary. */
public class ApprovedReader private constructor(
    private val bridge: ReaderSessionBridge,
    private val handles: List<ReaderSessionBridge.Page>,
    public val title: Title,
    public val chapter: Chapter,
) : Closeable {
    public data class Title(val sourceId: String, val id: String, val name: String, val description: String,
        val genres: List<String>, val authors: List<String>, val status: String, val alternatives: List<String>)
    public data class Chapter(val id: String, val label: String, val number: String?, val scanlator: String?)
    public val pageCount: Int get() = handles.size
    /** Caller owns the returned bitmap. */
    public suspend fun decode(index: Int, width: Int = 2048, height: Int = 4096): Bitmap {
        require(index in handles.indices && width in 1..2048 && height in 1..4096)
        return bridge.decode(handles[index], width, height)
    }
    override fun close() = bridge.close()
    public companion object {
        /** Explicit foreground action. Scope limited to the previously authorized title/chapter. */
        public suspend fun openReviewedSample(): ApprovedReader = withContext(Dispatchers.IO) {
            val profile = ReviewedProfile(SourceScope("ATSUMOE", setOf("atsu.moe", "cdn.atsu.moe")), "Chico", 3f)
            val bridge = ReaderSessionBridge.open(ParserRegistry(), profile)
            try {
                val details = bridge.approvedDetails(profile.query, profile.chapterNumber)
                val chapter = details.chapters.single()
                val pages = bridge.pages(chapter)
                require(pages.isNotEmpty() && pages.size <= 2000) { "Invalid page list" }
                bridge.decode(pages.first()).recycle()
                val title = details.title
                ApprovedReader(bridge, pages, Title(title.sourceId, title.id, title.title, details.description,
                    title.genres, title.authors, title.status.orEmpty(), title.alternativeTitles),
                    Chapter(chapter.id, chapter.label, chapter.number, chapter.scanlator))
            } catch (e: Throwable) {
                bridge.close()
                throw e
            }
        }
    }
}
