package com.anilumos.manga.engine

import org.koitharu.kotatsu.parsers.model.Manga

/** Select only the explicitly approved safe title, never an arbitrary first result.
 * Browse-only providers are eligible when the authorized title appears in their catalogue.
 * No unbounded pagination or search simulation. */
internal data class ApprovedTitleResult(val state: ProviderState, val title: Manga? = null)

internal suspend fun selectApprovedTitle(
    query: String,
    supportsSearch: Boolean,
    catalog: CatalogPage,
    search: suspend (String) -> CatalogPage,
): ApprovedTitleResult {
    require(query.isNotBlank())
    if (catalog.consumed == 0) return ApprovedTitleResult(ProviderState.EMPTY)
    val candidates = if (supportsSearch) search(query) else catalog
    if (candidates.consumed == 0) return ApprovedTitleResult(ProviderState.EMPTY)
    val matches = candidates.titles.filter { it.title.equals(query, ignoreCase = true) }.distinctBy { it.id }
    return when {
        matches.size > 1 -> ApprovedTitleResult(ProviderState.AMBIGUOUS_TITLE)
        matches.isEmpty() -> ApprovedTitleResult(ProviderState.TITLE_NOT_FOUND)
        else -> ApprovedTitleResult(ProviderState.READY, matches.single())
    }
}
