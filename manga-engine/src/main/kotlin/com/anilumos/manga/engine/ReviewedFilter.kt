package com.anilumos.manga.engine

import org.koitharu.kotatsu.parsers.model.*

/** Only provider-advertised filters are accepted; foreign tags and silent fallback are rejected. */
internal fun reviewedFilter(
    sourceId: String,
    query: String,
    tags: Set<MangaTag>,
    excluded: Set<MangaTag>,
    types: Set<ContentType>,
    states: Set<MangaState>,
    capabilities: MangaListFilterCapabilities,
    options: MangaListFilterOptions,
): MangaListFilter {
    val clean = query.trim()
    require(clean.length <= 256)
    require(tags.size + excluded.size <= 20)
    require((tags + excluded).all { it.source.name == sourceId && it in options.availableTags })
    require(tags.intersect(excluded).isEmpty())
    require(types.all { it in options.availableContentTypes && it.name in ParserRegistry.supportedCategories })
    require(states.all { it in options.availableStates })
    if (clean.isNotEmpty() && !capabilities.isSearchSupported) throw UnsupportedOperationException("Search unsupported")
    if (clean.isNotEmpty() && (tags.isNotEmpty() || excluded.isNotEmpty() || types.isNotEmpty() || states.isNotEmpty())
        && !capabilities.isSearchWithFiltersSupported) throw UnsupportedOperationException("Combined search and filters unsupported")
    if (tags.size > 1 && !capabilities.isMultipleTagsSupported) throw UnsupportedOperationException("Multiple genres unsupported")
    if (excluded.isNotEmpty() && !capabilities.isTagsExclusionSupported) throw UnsupportedOperationException("Genre exclusion unsupported")
    return MangaListFilter(query = clean.ifEmpty { null }, tags = tags, tagsExclude = excluded, types = types, states = states)
}
