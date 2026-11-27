package com.anilili.manga
internal fun providerFailureReason(error: Exception): String = "UNAVAILABLE"

internal fun providerDiagnostic(error: Exception): ProviderDiagnostic = ProviderDiagnostic("UNAVAILABLE", "UNKNOWN")
