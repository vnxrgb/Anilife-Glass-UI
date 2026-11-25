package com.anilili.data.wasm
import org.junit.Assert.*
import org.junit.Test

class WasmRedirectPolicyTest {
    private val hosts = setOf("example.org")
    private fun next(location: String, method: String = "GET", hops: Int = 0, status: Int = 302) =
        WasmRedirectPolicy.next("https://example.org/start", location, status, method, hops, hosts)
    @Test fun onlyApprovedHttpsTargets() {
        assertEquals("https://example.org/next", next("/next"))
        assertEquals("https://cdn.example.org/next", next("https://cdn.example.org/next"))
        assertNull(next("https://evil.example/next"))
        assertNull(next("http://example.org/next"))
        assertNull(next("https://127.0.0.1/"))
        assertNull(next("https://user:secret@example.org/"))
        assertNull(next("https://example.org:8443/next"))
        assertNull(next("https://localhost/"))
        assertNull(next("https://192.168.1.2/"))
    }
    @Test fun methodAndHopLimits() {
        assertNull(next("/next", method = "POST"))
        assertNull(next("/next", hops = 6))
        assertNull(next("/next", status = 304))
        assertNotNull(next("/next", method = "HEAD"))
    }
}
