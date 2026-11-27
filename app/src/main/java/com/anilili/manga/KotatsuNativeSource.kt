package com.anilili.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koitharu.kotatsu.parsers.DirectParsersAccess
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.core.ParsersBundleImpl
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter as ParserChapter
import org.koitharu.kotatsu.parsers.model.MangaSource as ParserSource
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.Locale

/** No executable bundle loader, browser automation, or external extension installations. */
@OptIn(DirectParsersAccess::class)
private class NativeParserContext : MangaLoaderContext() {
    override val cookieJar: CookieJar = CookieJar.NO_COOKIES
    override val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar).connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    override val parsers: ParsersBundle = ParsersBundleImpl
    override fun getDefaultUserAgent() = "AniLumos (Android; manga reader)"
    override fun getPreferredLocales(): List<Locale> = listOf(Locale.ENGLISH)
    override fun getConfig(source: ParserSource): MangaSourceConfig = object : MangaSourceConfig {
        override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
    }
    @Deprecated("No browser scripting in native preview")
    override suspend fun evaluateJs(script: String): String? = null
    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? = null
    override fun redrawImageResponse(response: Response, redraw: (image: Bitmap) -> Bitmap): Response =
        throw UnsupportedOperationException("Image transformations are not supported by this preview")
    override fun createBitmap(width: Int, height: Int): Bitmap =
        throw UnsupportedOperationException("Image transformations are not supported by this preview")
}

/**
 * The compiled library is pinned. Expose a deliberately small initial catalog, not every
 * parser indiscriminately. Sources requiring authentication, protected content, browser
 * challenges or image transformations are not enabled in this preview.
 */
internal fun builtInKotatsuSources(): List<MangaSource> {
    val context = NativeParserContext()
    return listOf("MANGADEX", "WEBTOONS_EN").mapNotNull { name ->
        context.parsers.findSource(name)?.takeUnless { it.isBroken }?.let { KotatsuNativeSource(context, it) }
    }
}

private class KotatsuNativeSource(
    context: NativeParserContext,
    private val descriptor: ParserMangaSource,
) : MangaSource {
    override val id = "kotatsu:" + descriptor.name.lowercase(Locale.ROOT)
    override val displayName = descriptor.title + " · Kotatsu"
    private val parser by lazy { context.newParserInstance(descriptor) }
    private val catalog = ConcurrentHashMap<String, Manga>()
    private val chapterCache = ConcurrentHashMap<String, ParserChapter>()
    private fun key(manga: Manga) = "${manga.id}|${manga.url}"
    override suspend fun search(query: String, limit: Int) = catalog(query, MangaKind.ALL, MangaOrder.POPULAR, 0, limit)
    override suspend fun catalog(query: String, kind: MangaKind, order: MangaOrder, offset: Int, limit: Int): List<MangaTitle> = withContext(Dispatchers.IO) {
        val requested = when {
            query.isNotBlank() -> SortOrder.RELEVANCE
            order == MangaOrder.RECENT -> SortOrder.UPDATED
            else -> SortOrder.POPULARITY
        }
        val sort = requested.takeIf { it in parser.availableSortOrders } ?: parser.availableSortOrders.first()
        val filter = MangaListFilter(query = query.trim().takeIf { it.isNotEmpty() }, contentRating = setOf(ContentRating.SAFE))
        parser.getList(offset, sort, filter).filter { it.contentRating != ContentRating.ADULT && it.contentRating != ContentRating.SUGGESTIVE }.map { manga ->
            val mangaId = key(manga)
            catalog[mangaId] = manga
            MangaTitle(id, mangaId, manga.title, manga.description.orEmpty(), manga.coverUrl,
                kind = if (descriptor.name == "WEBTOONS_EN") MangaKind.MANHWA else MangaKind.OTHER,
                genres = manga.tags.map { it.title }, status = manga.state?.name?.lowercase(Locale.ROOT).orEmpty())
        }.filter { kind == MangaKind.ALL || it.kind == kind }.take(limit)
    }
    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> = withContext(Dispatchers.IO) {
        if (language != "en") return@withContext emptyList()
        val manga = catalog[mangaId] ?: run {
            val separator = mangaId.indexOf('|')
            require(separator > 0) { "Invalid saved manga identity" }
            Manga(id = mangaId.substring(0, separator).toLong(), title = "", altTitles = emptySet(),
                url = mangaId.substring(separator + 1), publicUrl = "", rating = 0f,
                contentRating = null, coverUrl = null, tags = emptySet(), state = null,
                authors = emptySet(), source = descriptor)
        }
        val details = parser.getDetails(manga)
        catalog[mangaId] = details
        details.chapters.orEmpty().filter { chapter ->
            descriptor.name != "MANGADEX" || chapter.branch.isNullOrBlank() || chapter.branch.equals("English", true) || chapter.branch.equals("en", true)
        }.sortedByDescending { it.number }.map { chapter ->
            val chapterId = chapter.id.toString()
            chapterCache[chapterId] = chapter
            MangaChapter(id, chapterId, mangaId, chapter.title.orEmpty(), chapter.numberString(), language, listOfNotNull(chapter.scanlator))
        }
    }
    override suspend fun pages(chapterId: String): List<MangaPage> = withContext(Dispatchers.IO) {
        val chapter = chapterCache[chapterId] ?: error("Open chapter list again to reload this chapter")
        val headers = parser.getRequestHeaders().toMultimap().mapValues { it.value.joinToString(", ") }
        parser.getPages(chapter).mapIndexed { index, page ->
            val url = parser.getPageUrl(page)
            require(url.startsWith("https://")) { "Insecure page URL" }
            MangaPage(index, url, headers)
        }
    }
}
