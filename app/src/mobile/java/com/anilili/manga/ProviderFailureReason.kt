package com.anilili.manga
import com.anilumos.manga.engine.ProviderCompatibilityException
internal fun providerFailureReason(error: Exception): String =
    if (error is ProviderCompatibilityException) error.reason + "/" + error.stage else "TIMEOUT_OR_FAILURE"

internal fun providerDiagnostic(error: Exception): ProviderDiagnostic =
    if (error is ProviderCompatibilityException) ProviderDiagnostic(error.reason, error.stage, error.candidateHost, error.status)
    else ProviderDiagnostic("TIMEOUT_OR_FAILURE", "UNKNOWN")
