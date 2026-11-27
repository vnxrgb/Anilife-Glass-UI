package com.anilili.manga

/** A built-in source adapter, not a user-installed plugin or extension. */
interface MangaSource {
    val id: String
    val displayName: String
    val languageTag: String get() = ""
    val sourceCategory: String get() = "Manga"
    val available: Boolean get() = true
    fun websiteUrl(): String? = null
    val chapterLanguages: List<String> get() = listOf("en", "es", "fr")
    suspend fun search(query: String, limit: Int = 20): List<MangaTitle>
    suspend fun catalog(query: String, kind: MangaKind = MangaKind.ALL, order: MangaOrder = MangaOrder.POPULAR, offset: Int = 0, limit: Int = 24): List<MangaTitle> =
        if (offset == 0) search(query, limit).filter { kind == MangaKind.ALL || it.kind == kind } else emptyList()
    suspend fun catalogPage(query: String, kind: MangaKind, order: MangaOrder, genre: String?, offset: Int, limit: Int): MangaCatalogPage {
        val batch = catalog(query, kind, order, offset, limit)
        return MangaCatalogPage(batch.filter { genre == null || genre in it.genres }, if (batch.size == limit) offset + batch.size else null)
    }
    val metadataOnly: Boolean get() = false
    val hasNativeCatalogue: Boolean get() = false
    suspend fun genreOptions(): List<String> = emptyList()
    suspend fun kindOptions(): List<MangaKind> = emptyList()
    suspend fun prepareTitle(title: MangaTitle): MangaTitle = title
    /** Match on demand; engine adapters may use an ephemeral lookup session. */
    suspend fun findTitleMatch(reference: MangaTitle): MangaTitleMatch? = matchProviderTitle(this, reference)
    suspend fun chapters(mangaId: String, language: String = "en"): List<MangaChapter>
    suspend fun pages(chapterId: String): List<MangaPage>
    fun enrichedTitle(title: MangaTitle): MangaTitle = title
    /** Scoped artwork: a decoded bitmap or an app-private local URI, never a remote URL. */
    suspend fun artwork(mangaId: String): Any? = null
    val sampleOnly: Boolean get() = false
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
    val authors: List<String> = emptyList(),
    val alternativeTitles: List<String> = emptyList(),
    val largeCoverUrl: String? = null,
    val unrated: Boolean = false,
    val officialLinks: List<MangaOfficialLink> = emptyList(),
    val knownChapterCount: Int? = null,
)

data class MangaOfficialLink(val label: String, val url: String)
data class MangaCatalogPage(val items: List<MangaTitle>, val nextOffset: Int?)

data class MangaChapter(
    val sourceId: String,
    val id: String,
    val mangaId: String,
    val title: String,
    val chapterNumber: String?,
    val language: String,
    val scanlationGroups: List<String> = emptyList(),
    val previousParserId: String? = null,
)

internal const val REVIEWED_SOURCE_ID = "engine:ATSUMOE:chico3"
internal const val OPEN_COMIC_SOURCE_ID = "official:pepper-carrot"

data class MangaPage(val index: Int, val imageUrl: String, val headers: Map<String, String> = emptyMap(),
    val decode: (suspend (Int, Int) -> android.graphics.Bitmap)? = null, val sourceId: String? = null)

/** Explicit built-in registry. Future providers are added here, not installed as APKs. */
class MangaSources(private val sources: List<MangaSource>) {
    fun all(): List<MangaSource> = sources.toList()
    fun byId(id: String): MangaSource? = sources.firstOrNull { it.id == if (id == "kotatsu:mangadex") "mangadex" else id }
}

internal fun MangaSource.languageLabel(): String = languageTag.takeIf { it.isNotBlank() }?.let { tag ->
    java.util.Locale.forLanguageTag(tag.replace('_', '-')).getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { tag }
} ?: "Multilingual"

internal fun MangaChapter.matchesSaved(id: String?): Boolean = id != null && (this.id == id || previousParserId == id)
