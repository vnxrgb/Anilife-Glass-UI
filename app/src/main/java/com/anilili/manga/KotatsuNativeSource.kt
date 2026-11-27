package com.anilili.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.Locale

/** No executable bundle loader, browser automation, or external extension installations. */
@OptIn(DirectParsersAccess::class)
private class NativeParserContext : MangaLoaderContext() {
    override val cookieJar: CookieJar = MangaNetwork.cookieJar
    override val httpClient = MangaNetwork.client
    override val parsers: ParsersBundle = ParsersBundleImpl
    override fun getDefaultUserAgent() = "AniLumos (Android; manga reader)"
    override fun getPreferredLocales(): List<Locale> = listOf(Locale.getDefault(), Locale.ENGLISH).distinct()
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

/** Expose every compiled descriptor, including upstream-broken entries as unavailable. */
internal fun builtInKotatsuSources(): List<MangaSource> {
    val context = NativeParserContext()
    return context.parsers.sources.filter { source -> sourceAllowed(source.contentType, source.name, source.title, source.isBroken) }
        .distinctBy { it.name }.map { KotatsuNativeSource(context, it) }
        .sortedWith(compareBy<MangaSource> { it.id != "mangadex" }.thenBy { !it.available }.thenBy { it.displayName.lowercase(Locale.ROOT) })
}

private class KotatsuNativeSource(
    context: NativeParserContext,
    private val descriptor: ParserMangaSource,
) : MangaSource {
    private val isMangaDex = descriptor.name == "MANGADEX"
    override val id = if (isMangaDex) "mangadex" else "kotatsu:" + descriptor.name.lowercase(Locale.ROOT)
    override val displayName = descriptor.title
    override val languageTag = descriptor.locale
    override val sourceCategory = descriptor.contentType.name.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { it.uppercase() }
    override val available = !descriptor.isBroken
    override val chapterLanguages = when {
        isMangaDex -> listOf("en", "es", "fr", "ja", "ko", "zh", "pt", "de", "it", "id", "ru", "ar", "th", "vi", "tr", "und")
        languageTag.isBlank() -> listOf("und")
        else -> listOf(languageTag.replace('_', '-'))
    }
    private val sourceKind = when {
        descriptor.name == "STONESCAPE" || descriptor.name.startsWith("WEBTOONS_") || descriptor.name.startsWith("LINEWEBTOONS_") -> MangaKind.MANHWA
        descriptor.contentType == ContentType.MANHWA -> MangaKind.MANHWA
        descriptor.contentType == ContentType.MANHUA -> MangaKind.MANHUA
        descriptor.contentType == ContentType.MANGA -> MangaKind.MANGA
        else -> MangaKind.OTHER
    }
    private val parser by lazy { context.newParserInstance(descriptor) }
    override fun websiteUrl(): String? = runCatching { "https://${parser.domain}/".toHttpUrlOrNull()?.toString() }.getOrNull()
    private val catalog = ConcurrentHashMap<String, Manga>()
    private val chapterCache = ConcurrentHashMap<String, ParserChapter>()
    private val chapterBases = ConcurrentHashMap<String, String>()
    private fun key(manga: Manga) = if (isMangaDex) manga.url.removePrefix("/") else "${manga.id}|${manga.url}"
    override suspend fun search(query: String, limit: Int) = catalog(query, MangaKind.ALL, MangaOrder.POPULAR, 0, limit)
    override suspend fun catalog(query: String, kind: MangaKind, order: MangaOrder, offset: Int, limit: Int): List<MangaTitle> = withContext(Dispatchers.IO) {
        check(available) { "This source is marked unavailable upstream" }
        val requested = when {
            query.isNotBlank() -> SortOrder.RELEVANCE
            order == MangaOrder.RECENT -> SortOrder.UPDATED
            else -> SortOrder.POPULARITY
        }
        val sort = requested.takeIf { it in parser.availableSortOrders } ?: parser.availableSortOrders.first()
        val originalLocale = if (isMangaDex) when (kind) {
            MangaKind.MANGA -> Locale.JAPANESE
            MangaKind.MANHWA -> Locale.KOREAN
            MangaKind.MANHUA -> Locale.CHINESE
            else -> null
        } else null
        if (!isMangaDex && kind != MangaKind.ALL && kind != sourceKind) return@withContext emptyList()
        val filter = MangaListFilter(query = query.trim().takeIf { it.isNotEmpty() }, originalLocale = originalLocale, contentRating = setOf(ContentRating.SAFE))
        parser.getList(offset, sort, filter).filter { it.contentRating == ContentRating.SAFE }.map { manga ->
            val mangaId = key(manga)
            catalog[mangaId] = manga
            MangaTitle(id, mangaId, manga.title, manga.description.orEmpty(), manga.coverUrl,
                kind = if (isMangaDex) kind.takeUnless { it == MangaKind.ALL } ?: MangaKind.OTHER else sourceKind,
                genres = manga.tags.map { it.title }, status = manga.state?.name?.lowercase(Locale.ROOT).orEmpty(),
                authors = manga.authors.toList(), alternativeTitles = manga.altTitles.toList(), largeCoverUrl = manga.largeCoverUrl)
        }.filter { kind == MangaKind.ALL || it.kind == kind }.take(limit)
    }
    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> = withContext(Dispatchers.IO) {
        check(available) { "This source is marked unavailable upstream" }
        val manga = catalog[mangaId] ?: run {
            val separator = mangaId.indexOf('|')
            require(isMangaDex || separator > 0) { "Invalid saved manga identity" }
            Manga(id = if (separator > 0) mangaId.substring(0, separator).toLong() else 0L, title = "", altTitles = emptySet(),
                url = if (separator > 0) mangaId.substring(separator + 1) else mangaId, publicUrl = "", rating = 0f,
                contentRating = null, coverUrl = null, tags = emptySet(), state = null,
                authors = emptySet(), source = descriptor)
        }
        val details = parser.getDetails(manga)
        require(details.contentRating == ContentRating.SAFE || (details.contentRating == null && manga.contentRating == ContentRating.SAFE)) { "Title is not confirmed safe" }
        catalog[mangaId] = details
        details.chapters.orEmpty().filter { chapter ->
            if (!isMangaDex || language == "und") true else {
                val locale = Locale.forLanguageTag(language)
                val branch = chapter.branch?.substringBefore(" (").orEmpty()
                branch.equals(locale.getDisplayName(locale), true) || branch.equals(language, true)
            }
        }.sortedByDescending { it.number }.map { chapter ->
            val chapterId = if (isMangaDex) chapter.url.removePrefix("/") else chapter.id.toString()
            chapterCache[chapterId] = chapter
            chapterBases[chapterId] = normalizePageUrl(chapter.url, manga.publicUrl.toHttpUrlOrNull()?.toString() ?: "https://${parser.domain}/") ?: "https://${parser.domain}/"
            MangaChapter(id, chapterId, mangaId, chapter.title.orEmpty(), chapter.numberString(), language, listOfNotNull(chapter.scanlator), previousParserId = if (isMangaDex) chapter.id.toString() else null)
        }
    }
    override fun enrichedTitle(title: MangaTitle): MangaTitle {
        val details = catalog[title.id] ?: return title
        return title.copy(description = details.description?.takeIf { it.isNotBlank() } ?: title.description,
            coverUrl = details.coverUrl ?: title.coverUrl, largeCoverUrl = details.largeCoverUrl,
            authors = details.authors.filter { it.isNotBlank() }, alternativeTitles = details.altTitles.filter { it.isNotBlank() },
            genres = details.tags.map { it.title }.ifEmpty { title.genres },
            status = details.state?.name?.lowercase(Locale.ROOT) ?: title.status)
    }
    override suspend fun pages(chapterId: String): List<MangaPage> = withContext(Dispatchers.IO) {
        val chapter = chapterCache[chapterId] ?: error("Open chapter list again to reload this chapter")
        val headers = parser.getRequestHeaders().toMultimap().mapValues { it.value.joinToString(", ") }
        val base = chapterBases[chapterId] ?: "https://${parser.domain}/"
        parser.getPages(chapter).mapIndexed { index, page ->
            val url = normalizePageUrl(parser.getPageUrl(page), base)
                ?: error("Provider returned an unsupported image URL")
            MangaPage(index, url, headers)
        }
    }
}

/** Fail closed for categories outside ordinary manga/comics and known adult source identifiers. */
internal fun sourceAllowed(type: ContentType, name: String, title: String, broken: Boolean): Boolean {
    if (name.equals("MANGAGO", true) || name.startsWith("XCOMIC", true)) return false // Mixed-content sources pending separate review.
    if (broken || type !in setOf(ContentType.MANGA, ContentType.MANHWA, ContentType.MANHUA, ContentType.COMICS, ContentType.ONE_SHOT)) return false
    val label = (name + " " + title).lowercase(Locale.ROOT)
    return !Regex("hentai|hent[a-z]*|porn|adult|nsfw|18\\s*\\+|doujin|eromanga|ecchi|rule34|xxx|yaoi18|smut|lezhin18").containsMatchIn(label)
}

/** Accept ordinary HTTP/HTTPS plus relative URLs; reject non-web schemes and embedded credentials. */
internal fun normalizePageUrl(raw: String, base: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val parsed = value.toHttpUrlOrNull() ?: base.toHttpUrlOrNull()?.resolve(value) ?: return null
    if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
    return parsed.toString()
}

internal fun preferredMangaLanguage(tag: String): Boolean = tag.isBlank() || Locale.forLanguageTag(tag.replace('_', '-')).language in setOf("en", "ja", "zh", "ko")
