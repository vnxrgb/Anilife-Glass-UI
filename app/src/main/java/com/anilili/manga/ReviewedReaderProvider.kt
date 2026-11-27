package com.anilili.manga

/** A lightweight, offline-discovered descriptor. Factory creation performs no provider I/O. */
internal data class ReviewedReaderProvider(
    val id: String,
    val label: String,
    val revision: String,
    val create: ((String, ProviderDiagnostic?) -> Unit) -> MangaSource,
)
