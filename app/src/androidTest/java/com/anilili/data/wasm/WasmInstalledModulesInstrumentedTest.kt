package com.anilili.data.wasm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature

@RunWith(AndroidJUnit4::class)
class WasmInstalledModulesInstrumentedTest {
    @Test fun signedUpdatePersistsAndCorruptionFailsClosed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "fixture-update-test"
        val dir = File(context.noBackupFilesDir, "reviewed-wasm")
        File(dir, "$id.signed").delete()
        File(dir, "$id.json").delete()
        val module = byteArrayOf(0, 97, 115, 109, 13, 0, 1, 0)
        val digest = MessageDigest.getInstance("SHA-256").digest(module).joinToString("") { "%02x".format(it.toInt() and 255) }
        val bundled = ApprovedWasmProvider(id, 1, digest, true, setOf("example.org"), setOf("en"))
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        fun manifest(version: Long) = JSONObject().put("schema", 1).put("id", id)
            .put("version", version).put("sha256", digest).put("abi", "community")
            .put("hosts", JSONArray(listOf("example.org")))
            .put("languages", JSONArray(listOf("en"))).toString().toByteArray()
        fun sign(bytes: ByteArray) = Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private); update(bytes); sign()
        }
        try {
            val update = manifest(2)
            assertTrue(WasmInstalledModules.install(context, bundled, update, sign(update), module, keys.public))
            assertEquals(2L, WasmInstalledModules.load(context, bundled, keys.public)?.first?.version)
            assertNull(WasmInstalledModules.load(context, bundled))
            assertNull(WasmInstalledModules.load(context, bundled, KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public))
            assertFalse(WasmInstalledModules.install(context, bundled, manifest(1), sign(manifest(1)), module, keys.public))
            File(dir, "$id.signed").writeBytes(module + byteArrayOf(42))
            assertNull(WasmInstalledModules.load(context, bundled, keys.public))
        } finally {
            File(dir, "$id.signed").delete()
            File(dir, "$id.json").delete()
        }
    }
}
