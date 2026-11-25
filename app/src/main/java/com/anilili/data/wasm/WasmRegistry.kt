package com.anilili.data.wasm

/** A reviewed manifest, not guest metadata, is the authority for capabilities. */
internal data class ApprovedWasmProvider(
    val id: String,
    val version: Long,
    val digest: String,
    val communityAbi: Boolean,
    val hosts: Set<String>,
    val languages: Set<String>,
)

internal object WasmRegistry {
    // Deliberately empty until source/license, domain and signed manifest review are completed.
    val bundled: List<ApprovedWasmProvider> = emptyList()

    fun select(providers: List<ApprovedWasmProvider>, language: String): List<ApprovedWasmProvider> =
        providers.filter { language in it.languages }
            .filter { it.id.matches(Regex("[a-z0-9-]{1,64}")) && it.version > 0 &&
                it.digest.matches(Regex("[a-f0-9]{64}")) &&
                it.hosts.isNotEmpty() && it.hosts.size <= 64 &&
                it.hosts.all { host -> WasmPolicy.allowsUrl("https://$host/", setOf(host)) } }
            .groupBy { it.id }.values.mapNotNull { versions ->
                val newest = versions.maxOf { it.version }
                versions.filter { it.version == newest }.distinct().singleOrNull()
            }.sortedBy { it.id }

    fun permitsUpdate(installed: ApprovedWasmProvider, candidate: ApprovedWasmProvider): Boolean =
        installed.id == candidate.id && candidate.version > installed.version &&
            installed.communityAbi == candidate.communityAbi &&
            installed.hosts.containsAll(candidate.hosts) &&
            installed.languages.containsAll(candidate.languages)
}
