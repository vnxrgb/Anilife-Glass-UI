package com.anilili.data.wasm

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class WasmPolicyTest {
    @Test fun rejectsCoreModulesAndWrongDigest() {
        val component = byteArrayOf(0, 97, 115, 109, 13, 0, 1, 0)
        val digest = MessageDigest.getInstance("SHA-256").digest(component).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertTrue(WasmPolicy.verify(component, digest))
        assertFalse(WasmPolicy.verify(component, "0".repeat(64)))
        assertFalse(WasmPolicy.componentHeaderValid(byteArrayOf(0, 97, 115, 109, 1, 0, 0, 0)))
        assertFalse(WasmPolicy.componentHeaderValid(byteArrayOf(0)))
    }

    @Test fun approvedHttpsDomainsOnly() {
        val allowed = setOf("example.org")
        assertTrue(WasmPolicy.allowsUrl("https://api.example.org/test", allowed))
        assertFalse(WasmPolicy.allowsUrl("https://evil-example.org/", allowed))
        assertFalse(WasmPolicy.allowsUrl("https://example.org.evil.com/", allowed))
        assertFalse(WasmPolicy.allowsUrl("https://example.org@evil.com/", allowed))
        assertFalse(WasmPolicy.allowsUrl("http://example.org/", allowed))
        assertFalse(WasmPolicy.allowsUrl("https://example.org:444/", allowed))
        assertFalse(WasmPolicy.allowsUrl("https://127.0.0.1/", setOf("127.0.0.1")))
        assertFalse(WasmPolicy.allowsUrl("https://foo.local/", setOf("foo.local")))
    }
}
