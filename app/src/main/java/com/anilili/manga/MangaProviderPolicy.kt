package com.anilili.manga

/** Curated first-pass candidates. These are existing parser adapters, not a new network stack.
 * Language filtering takes precedence over every priority, including pinned sources. */
internal object MangaProviderPolicy {
    const val PRIMARY_LIMIT = 7
    private val curated = listOf(REVIEWED_SOURCE_ID, "engine:WEEBCENTRAL", "engine:MANGADEX", "engine:MANGAPILL", "engine:MANGAKATANA")
    fun isCurated(id: String): Boolean = id in curated
    fun curatedRank(id: String): Int = curated.indexOf(id).takeIf { it >= 0 } ?: Int.MAX_VALUE
    fun order(sources: List<MangaSource>, pinned: Set<String>, status: Map<String, String>, last: String?): List<MangaSource> =
        sources.sortedWith(compareBy<MangaSource> { it.id != last }
            .thenBy { it.id !in pinned }
            .thenBy { !isCurated(it.id) }
            .thenBy { curatedRank(it.id) }
            .thenBy { providerRank(status[it.id]) }
            .thenBy { it.displayName })
    fun primary(ordered: List<MangaSource>, pinned: Set<String>, status: Map<String, String>, last: String?): List<MangaSource> =
        ordered.filter { isCurated(it.id) || it.id == last || it.id in pinned || status[it.id] == "READY" }
            .take(PRIMARY_LIMIT)
}
