package com.anilili.manga
/** Lightweight source descriptor. Cached factories only restore previously verified health. */
internal data class ReviewedReaderProvider(
    val id: String, val label: String,
    val revision: String,
    val cached: () -> MangaSource,
    val open: suspend () -> MangaSource,
)
