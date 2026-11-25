package com.anilili.data.wasm

import org.junit.Assert.*
import org.junit.Test

class WasmRegistryTest {
    private val sample = ApprovedWasmProvider("fixture", 1, "a".repeat(64), true, setOf("example.org"), setOf("en"))
    @Test fun selectsOnlyApprovedLanguageAndLatestVersion() {
        assertEquals(listOf(sample.copy(version = 2)), WasmRegistry.select(listOf(sample, sample.copy(version = 2)), "en"))
        assertTrue(WasmRegistry.select(listOf(sample), "es").isEmpty())
        assertTrue(WasmRegistry.select(listOf(sample.copy(hosts = setOf("127.0.0.1"))), "en").isEmpty())
        assertTrue(WasmRegistry.select(listOf(sample, sample.copy(digest = "b".repeat(64))), "en").isEmpty())
    }
    @Test fun preventsDowngradeAndCapabilityExpansion() {
        assertTrue(WasmRegistry.permitsUpdate(sample, sample.copy(version = 2)))
        assertFalse(WasmRegistry.permitsUpdate(sample, sample))
        assertFalse(WasmRegistry.permitsUpdate(sample, sample.copy(version = 2, hosts = setOf("example.org", "other.org"))))
        assertFalse(WasmRegistry.permitsUpdate(sample, sample.copy(version = 2, communityAbi = false)))
    }
}
