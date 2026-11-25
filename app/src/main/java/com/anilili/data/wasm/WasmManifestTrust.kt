package com.anilili.data.wasm

import java.security.PublicKey
import java.security.Signature

/** Verify exact manifest bytes before parsing. Trust key must come from the application, not the manifest. */
internal object WasmManifestTrust {
    private const val MAX_MANIFEST_BYTES = 128 * 1024
    fun verify(manifest: ByteArray, signature: ByteArray, pinnedKey: PublicKey): Boolean {
        if (manifest.isEmpty() || manifest.size > MAX_MANIFEST_BYTES || signature.size !in 64..1024) return false
        return runCatching {
            Signature.getInstance("SHA256withRSA").apply {
                initVerify(pinnedKey)
                update(manifest)
            }.verify(signature)
        }.getOrDefault(false)
    }
}
