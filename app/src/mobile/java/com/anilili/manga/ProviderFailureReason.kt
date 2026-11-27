package com.anilili.manga
import com.anilumos.manga.engine.ProviderCompatibilityException
internal fun providerFailureReason(error: Exception): String =
    if (error is ProviderCompatibilityException) error.reason + "/" + error.stage else "TIMEOUT_OR_FAILURE"
