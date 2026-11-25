package com.anilili.data.wasm

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature

@RunWith(AndroidJUnit4::class)
class WasmUpdatePolicyInstrumentedTest {
    @Test fun acceptsOnlySignedMonotonicCapabilityNarrowingUpdates() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val module = byteArrayOf(0, 97, 115, 109, 13, 0, 1, 0)
        val digest = MessageDigest.getInstance("SHA-256").digest(module).joinToString("") { "%02x".format(it.toInt() and 255) }
        val original = ApprovedWasmProvider("fixture", 1, digest, true, setOf("example.org"), setOf("en"))
        fun manifest(version: Long, hosts: List<String> = listOf("example.org")) =
            JSONObject().put("schema", 1).put("id", "fixture").put("version", version)
                .put("sha256", digest).put("abi", "community").put("hosts", JSONArray(hosts))
                .put("languages", JSONArray(listOf("en"))).toString().toByteArray()
        fun sign(bytes: ByteArray): ByteArray = Signature.getInstance("SHA256withRSA").run {
            initSign(key.private); update(bytes); sign()
        }
        fun verify(bytes: ByteArray, signature: ByteArray = sign(bytes), binary: ByteArray = module) =
            WasmUpdatePolicy.verifiedCandidate(original, bytes, signature, binary, key.public)
        val valid = manifest(2)
        assertEquals(2L, verify(valid)?.version)
        assertNull(verify(manifest(1)))
        assertNull(verify(manifest(2, listOf("example.org", "other.org"))))
        assertNull(verify(manifest(3), sign(valid)))
        assertNull(verify(valid, binary = module + byteArrayOf(1)))
    }
}
