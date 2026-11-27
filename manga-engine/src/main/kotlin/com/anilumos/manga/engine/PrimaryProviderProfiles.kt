package com.anilumos.manga.engine

import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaListFilter

/** First-class profiles for the five individual upstream parser implementations.
 * The existing scoped transport remains responsible for requests and image delivery.
 * No Himmy implementation or guessed CDN domains are used. */
internal object PrimaryProviderProfiles {
    val ids = listOf("ATSUMOE", "WEEBCENTRAL", "MANGADEX", "MANGAPILL", "MANGAKATANA")
    val profiles = listOf(
        LiveProviderProfile(SourceScope("ATSUMOE", setOf("atsu.moe", "cdn.atsu.moe"), imageRedirectHosts = setOf("cdn.atsu.moe")), "Atsu", "en", probeTitle = "Chico", probeChapter = 3f),
        LiveProviderProfile(SourceScope("WEEBCENTRAL", setOf("weebcentral.com")), "Weeb Central", "en"),
        LiveProviderProfile(SourceScope("MANGADEX", setOf("mangadex.org", "api.mangadex.org", "uploads.mangadex.org", "mangadex.network"), setOf("mangadex.network"), imageRedirectHosts = setOf("uploads.mangadex.org")), "MangaDex"),
        LiveProviderProfile(SourceScope("MANGAPILL", setOf("mangapill.com")), "MangaPill", "en"),
        LiveProviderProfile(SourceScope("MANGAKATANA", setOf("mangakatana.com")), "MangaKatana", "en")
    )
    fun restrictFilter(id: String, filter: MangaListFilter): MangaListFilter = when (id) {
        "MANGADEX", "WEEBCENTRAL" -> filter.copy(contentRating = setOf(ContentRating.SAFE))
        else -> filter
    }
    // Owner explicitly approved unrated catalogue entries for these two parsers.
    // Unknown is not equivalent to SAFE. Explicit non-safe ratings stay excluded.
    fun permitsRating(id: String, rating: ContentRating?): Boolean =
        rating == ContentRating.SAFE || (rating == null && id in setOf("MANGAPILL", "MANGAKATANA"))
}
