package com.anilili.manga

/** A built-in source adapter, not a user-installed plugin or extension. */
interface MangaSource {
    val id: String
    val displayName: String
    suspend fun search(query: String, limit: Int = 20): List<MangaTitle>
    suspend fun chapters(mangaId: String, language: String = "en"): List<MangaChapter>
    suspend fun pages(chapterId: String): List<MangaPage>
}

data class MangaTitle(
    val sourceId: String,
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String?,
)

data class MangaChapter(
    val sourceId: String,
    val id: String,
    val mangaId: String,
    val title: String,
    val chapterNumber: String?,
    val language: String,
    val scanlationGroups: List<String> = emptyList(),
)

data class MangaPage(val index: Int, val imageUrl: String)

/** Explicit built-in registry. Future providers are added here, not installed as APKs. */
class MangaSources(private val sources: List<MangaSource>) {
    fun all(): List<MangaSource> = sources.toList()
    fun byId(id: String): MangaSource? = sources.firstOrNull { it.id == id }
}
