package com.anilili.data.wasm

import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Assert.*
import org.junit.Test

class WasmManifestTrustTest {
    @Test fun rejectsTamperingAndWrongKey() {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val key = generator.generateKeyPair()
        val data = "fixture manifest".toByteArray()
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(key.private)
            update(data)
        }.sign()
        assertTrue(WasmManifestTrust.verify(data, sig, key.public))
        assertFalse(WasmManifestTrust.verify("tampered".toByteArray(), sig, key.public))
        assertFalse(WasmManifestTrust.verify(data, sig, generator.generateKeyPair().public))
    }
}
