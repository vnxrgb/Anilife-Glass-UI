package com.anilili.data.wasm

import org.json.JSONObject
import java.security.PublicKey

/** Only an authenticated, capability-narrowing replacement of an already reviewed provider. */
internal object WasmUpdatePolicy {
    fun verifiedCandidate(
        installed: ApprovedWasmProvider,
        manifest: ByteArray,
        signature: ByteArray,
        module: ByteArray,
        pinnedKey: PublicKey,
    ): ApprovedWasmProvider? {
        if (!WasmManifestTrust.verify(manifest, signature, pinnedKey)) return null
        if (module.size !in 8..WasmPolicy.MAX_MODULE_BYTES) return null
        return runCatching {
            val json = JSONObject(manifest.toString(Charsets.UTF_8))
            require(json.getInt("schema") == 1)
            fun strings(key: String, max: Int): Set<String> {
                val array = json.getJSONArray(key)
                require(array.length() in 1..max)
                val list = (0 until array.length()).map { array.getString(it) }
                require(list.distinct().size == list.size)
                return list.toSet()
            }
            val abi = json.getString("abi")
            require(abi in setOf("community", "desktop"))
            val version = json.get("version")
            require(version is Int || version is Long)
            val candidate = ApprovedWasmProvider(
                id = json.getString("id"),
                version = (version as Number).toLong(),
                digest = json.getString("sha256"),
                communityAbi = abi == "community",
                hosts = strings("hosts", 64),
                languages = strings("languages", 32),
            )
            require(WasmRegistry.permitsUpdate(installed, candidate))
            require(WasmRegistry.select(listOf(candidate), candidate.languages.first()).singleOrNull() == candidate)
            require(WasmPolicy.verify(module, candidate.digest))
            candidate
        }.getOrNull()
    }
}
