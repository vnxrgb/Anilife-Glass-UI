package com.anilili.manga
internal fun reviewedReaderSupported(): Boolean = false
internal suspend fun openReviewedReader(): MangaSource = throw UnsupportedOperationException("Online manga is mobile-only")
