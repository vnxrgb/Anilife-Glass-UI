package com.anilumos.manga.engine

/** Explicit CDN declarations from the pinned parser source; do not trust arbitrary
 * hosts supplied by page JSON, redirects or DNS responses. */
internal object ProviderCdnRegistry {
    fun scope(id: String, declared: Set<String>): SourceScope = when {
        id.startsWith("MANGABALL_") -> SourceScope(id, declared + "bulbasaur.poke-black-and-white.net",
            setOf("poke-black-and-white.net"), setOf("bulbasaur.poke-black-and-white.net"))
        else -> SourceScope(id, declared)
    }
}
