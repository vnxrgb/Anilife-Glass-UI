package com.anilili.manga

/** A built-in source adapter, not a user-installed plugin or extension. */
interface MangaSource {
    val id: String
    val displayName: String
    suspend fun search(query: String, limit: Int = 20): List<MangaTitle>
    suspend fun catalog(query: String, kind: MangaKind = MangaKind.ALL, order: MangaOrder = MangaOrder.POPULAR, offset: Int = 0, limit: Int = 24): List<MangaTitle> =
        if (offset == 0) search(query, limit).filter { kind == MangaKind.ALL || it.kind == kind } else emptyList()
    suspend fun chapters(mangaId: String, language: String = "en"): List<MangaChapter>
    suspend fun pages(chapterId: String): List<MangaPage>
}

enum class MangaKind(val label: String) {
    ALL("All"), MANGA("Manga"), MANHWA("Manhwa"), MANHUA("Manhua"), OTHER("Other");
    companion object {
        fun fromLanguage(language: String) = when (language) {
            "ja" -> MANGA
            "ko" -> MANHWA
            "zh", "zh-hk" -> MANHUA
            else -> OTHER
        }
    }
}

enum class MangaOrder { POPULAR, RECENT }

data class MangaTitle(
    val sourceId: String,
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String?,
    val kind: MangaKind = MangaKind.OTHER,
    val genres: List<String> = emptyList(),
    val status: String = "",
    val year: Int? = null,
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
