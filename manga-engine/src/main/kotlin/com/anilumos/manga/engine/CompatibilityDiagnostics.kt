package com.anilumos.manga.engine

import java.io.IOException

/** Only stable categories; never expose raw parser exceptions, headers, cookies or URLs. */
public class ProviderCompatibilityException(public val reason: String, public val stage: String, public val status: Int? = null, public val candidateHost: String? = null) :
    IOException("Provider compatibility: " + reason + " at " + stage)

internal fun compatibilityFailure(stage: ProbeStage, error: Exception): ProviderCompatibilityException = when (error) {
    is ProviderCompatibilityException -> error
    is SourceScopeException -> ProviderCompatibilityException("HOST_SCOPE", stage.name, candidateHost = error.candidateHost)
    is ProviderAccessException -> ProviderCompatibilityException(if (error.status == 429) "RATE_LIMIT" else "ACCESS_RESTRICTED", stage.name, error.status)
    is ProviderFeatureException -> ProviderCompatibilityException(error.feature.name, stage.name)
    is UnsupportedOperationException -> ProviderCompatibilityException("UNSUPPORTED_FEATURE", stage.name)
    is java.net.UnknownHostException -> ProviderCompatibilityException("DNS_FAILURE", stage.name)
    is java.net.SocketTimeoutException -> ProviderCompatibilityException("NETWORK_TIMEOUT", stage.name)
    is java.net.ConnectException -> ProviderCompatibilityException("CONNECTION_FAILURE", stage.name)
    is javax.net.ssl.SSLException -> ProviderCompatibilityException("TLS_FAILURE", stage.name)
    is java.io.IOException -> ProviderCompatibilityException(if (stage == ProbeStage.IMAGE_DECODE) "IMAGE_DECODE" else "NETWORK_OR_RESPONSE", stage.name)
    else -> ProviderCompatibilityException("PARSER_FAILURE", stage.name)
}

/** Sanitize errors from ordinary on-demand reader operations as well as explicit probes. */
public fun classifyProviderFailure(stage: String, error: Exception): ProviderCompatibilityException =
    compatibilityFailure(ProbeStage.entries.firstOrNull { it.name == stage } ?: ProbeStage.INITIALIZE, error)
