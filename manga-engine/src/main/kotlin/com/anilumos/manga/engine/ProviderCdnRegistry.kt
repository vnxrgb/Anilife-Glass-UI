package com.anilumos.manga.engine

/** Explicit CDN declarations from parser source or targeted device diagnostics; do not trust arbitrary
 * hosts supplied by page JSON, redirects or DNS responses. */
internal object ProviderCdnRegistry {
    fun scope(id: String, declared: Set<String>): SourceScope = when {
        id.startsWith("MANGABALL_") -> SourceScope(id, declared + "bulbasaur.poke-black-and-white.net",
            setOf("poke-black-and-white.net"), setOf("bulbasaur.poke-black-and-white.net"), imageDomainHosts = declared)
        // Exact candidate observed in a Chikari PAGE_DOWNLOAD report. Preview validation required.
        id == "CHIKARI" -> SourceScope(id, declared + "cdn.irakich.st",
            imageRedirectHosts = setOf("cdn.irakich.st"), imageDomainHosts = declared)
        else -> SourceScope(id, declared, imageDomainHosts = declared)
    }
}
