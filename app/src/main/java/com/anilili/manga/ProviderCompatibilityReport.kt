package com.anilili.manga

/** Diagnostic metadata only. No content URLs, credentials, titles or paths. */
internal data class ProviderDiagnostic(val reason: String, val stage: String, val host: String? = null, val status: Int? = null)
internal fun providerCompatibilityReport(rows: Map<String, ProviderDiagnostic>): String = buildString {
    appendLine("AniLumos manga compatibility report")
    appendLine("Provider ID\tReason\tStage\tCandidate hostname (unverified)\tHTTP status")
    for ((id, row) in rows.toSortedMap()) {
        val safeId = id.take(128).replace(Regex("[^A-Za-z0-9_:-]"), "_")
        val safeReason = row.reason.take(60).replace(Regex("[^A-Z0-9_]"), "_")
        val safeStage = row.stage.take(60).replace(Regex("[^A-Z0-9_]"), "_")
        val host = row.host?.takeIf { it.length <= 253 && it.matches(Regex("[a-z0-9.-]+")) }.orEmpty()
        appendLine(listOf(safeId, safeReason, safeStage, host, row.status?.takeIf { it in 100..599 }?.toString().orEmpty()).joinToString("\t"))
    }
}
