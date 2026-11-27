package com.anilili.manga
/** Explicitly reviewed readers only. Descriptors do not themselves grant chapter access. */
internal data class ReviewedReaderProvider(val id: String, val label: String, val open: suspend () -> MangaSource)
