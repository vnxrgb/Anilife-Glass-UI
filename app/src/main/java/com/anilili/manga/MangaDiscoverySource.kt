package com.anilili.manga

import com.anilili.data.AppGraph
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Discovery data from the app's existing AniList client. External publisher links are not
 * in-app reader adapters and never pass through the parser engine. No persistent catalogue cache. */
internal class MangaDiscoverySource : MangaSource {
    companion object {
        const val ID = "catalog:anilist"
        val GENRES = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mystery",
            "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller")
    }
    override val id = ID
    override val displayName = "AniList catalogue"
    override val metadataOnly = true
    override val chapterLanguages = emptyList<String>()
    override suspend fun search(query: String, limit: Int): List<MangaTitle> =
        catalogPage(query, MangaKind.ALL, MangaOrder.POPULAR, null, 0, limit.coerceIn(1, 30)).items
    override suspend fun catalogPage(query: String, kind: MangaKind, order: MangaOrder, genre: String?, offset: Int, limit: Int): MangaCatalogPage {
        require(limit in 1..30 && offset >= 0 && offset % limit == 0)
        val country = when (kind) { MangaKind.MANGA -> "JP"; MangaKind.MANHWA -> "KR"; MangaKind.MANHUA -> "CN"; else -> null }
        val page = offset / limit + 1
        val response = AppGraph.aniList.mangaDiscovery(query.trim().take(200), country, genre, order == MangaOrder.RECENT, page, limit)
        return decodeDiscoveryPage(response, offset, limit)
    }
    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> = emptyList()
    override suspend fun pages(chapterId: String): List<MangaPage> = throw UnsupportedOperationException("Catalogue only")
}

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
private fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()

internal fun catalogueImage(value: String?): String? {
    val url = value?.toHttpUrlOrNull() ?: return null
    return url.toString().takeIf { url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
        url.host in setOf("s4.anilist.co", "s3.anilist.co", "s.anilist.co") }
}

internal fun officialCatalogueLink(value: String?, label: String): MangaOfficialLink? {
    val url = value?.toHttpUrlOrNull() ?: return null
    if (!url.isHttps || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    val allowed = setOf("anilist.co", "mangaplus.shueisha.co.jp", "www.viz.com", "viz.com", "www.webtoons.com",
        "webtoons.com", "tapas.io", "www.tappytoon.com", "tappytoon.com", "global.manga-up.com",
        "kmanga.kodansha.com", "www.lezhinus.com", "lezhinus.com", "www.azuki.co", "azuki.co")
    return if (url.host in allowed) MangaOfficialLink(label.take(60), url.toString()) else null
}

internal fun decodeDiscoveryPage(page: JsonObject, offset: Int, size: Int): MangaCatalogPage {
    val entries = page.array("media").mapNotNull { raw ->
        val item = raw as? JsonObject ?: return@mapNotNull null
        if ((item["isAdult"] as? JsonPrimitive)?.booleanOrNull != false) return@mapNotNull null
        val id = item.string("id")?.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        val names = item.obj("title") ?: return@mapNotNull null
        val name = listOf("userPreferred", "english", "romaji", "native").firstNotNullOfOrNull { names.string(it)?.takeIf(String::isNotBlank) } ?: return@mapNotNull null
        val cover = item.obj("coverImage")
        val links = item.array("externalLinks").mapNotNull { link ->
            val obj = link as? JsonObject ?: return@mapNotNull null
            if (obj.string("type") != "STREAMING") return@mapNotNull null
            officialCatalogueLink(obj.string("url"), obj.string("site") ?: "Official publisher")
        }.distinctBy { it.url }.take(8)
        MangaTitle(MangaDiscoverySource.ID, id.toString(), name, cleanMangaDescription(item.string("description").orEmpty()),
            catalogueImage(cover?.string("large")), kind = MangaKind.fromLanguage(when(item.string("countryOfOrigin")) { "JP" -> "ja"; "KR" -> "ko"; "CN" -> "zh"; else -> "" }),
            genres = item.array("genres").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.distinct(),
            status = item.string("status").orEmpty(), year = item.obj("startDate")?.string("year")?.toIntOrNull(),
            alternativeTitles = listOfNotNull(names.string("english"), names.string("romaji"), names.string("native")).filter { it != name }.distinct(),
            largeCoverUrl = catalogueImage(item.string("bannerImage")) ?: catalogueImage(cover?.string("extraLarge")),
            officialLinks = links + listOfNotNull(officialCatalogueLink(item.string("siteUrl"), "AniList title page")),
            knownChapterCount = item.string("chapters")?.toIntOrNull()?.takeIf { it > 0 })
    }.distinctBy { it.id }
    val next = (page.obj("pageInfo")?.get("hasNextPage") as? JsonPrimitive)?.booleanOrNull == true
    return MangaCatalogPage(entries, if (next && offset / size < 499) offset + size else null)
}

internal fun cleanMangaDescription(value: String): String =
    value.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("__([^_]+)__"), "$1")
        .replace(Regex("(?<!\\n)\\s+(?=\\d{1,3}\\.\\s)"), "\n")
        .trim()
