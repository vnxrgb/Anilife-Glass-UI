package com.anilili.data.wasm

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.PublicKey

/**
 * Private, versioned module storage. The registry remains the authority: installed updates
 * can only narrow capabilities and must have been signed by the application's pinned key.
 * No URL fetch, user-selected key or exported installer is provided.
 */
internal object WasmInstalledModules {
    private const val MAX_BYTES = 4 * 1024 * 1024

    private fun directory(context: Context): File = File(context.noBackupFilesDir, "reviewed-wasm")

    fun install(
        context: Context,
        bundled: ApprovedWasmProvider,
        manifest: ByteArray,
        signature: ByteArray,
        module: ByteArray,
        pinnedKey: PublicKey,
    ): Boolean {
        if (!com.anilili.BuildConfig.DEBUG) return false
        if (WasmRegistry.select(listOf(bundled), bundled.languages.firstOrNull() ?: "") != listOf(bundled)) return false
        val existing = load(context, bundled)?.first ?: bundled
        val candidate = WasmUpdatePolicy.verifiedCandidate(existing, manifest, signature, module, pinnedKey) ?: return false
        if (!WasmRegistry.permitsUpdate(bundled, candidate)) return false
        return runCatching {
            val dir = directory(context)
            require(dir.isDirectory || dir.mkdirs())
            val payload = AtomicFile(File(dir, "${bundled.id}.wasm"))
            var stream = payload.startWrite()
            try {
                stream.write(module)
                payload.finishWrite(stream)
            } catch (error: Exception) {
                payload.failWrite(stream)
                throw error
            }
            val metadata = JSONObject()
                .put("id", candidate.id).put("version", candidate.version)
                .put("sha256", candidate.digest)
                .put("abi", if (candidate.communityAbi) "community" else "desktop")
                .put("hosts", JSONArray(candidate.hosts.sorted()))
                .put("languages", JSONArray(candidate.languages.sorted()))
            val metaFile = AtomicFile(File(dir, "${bundled.id}.json"))
            stream = metaFile.startWrite()
            try {
                stream.write(metadata.toString().toByteArray(Charsets.UTF_8))
                metaFile.finishWrite(stream)
            } catch (error: Exception) {
                metaFile.failWrite(stream)
                throw error
            }
            true
        }.getOrDefault(false)
    }

    fun load(context: Context, bundled: ApprovedWasmProvider): Pair<ApprovedWasmProvider, ByteArray>? {
        if (!com.anilili.BuildConfig.DEBUG) return null
        if (WasmRegistry.select(listOf(bundled), bundled.languages.firstOrNull() ?: "") != listOf(bundled)) return null
        return runCatching {
            val dir = directory(context)
            val metadata = AtomicFile(File(dir, "${bundled.id}.json")).openRead().use {
                require(it.available() <= 128 * 1024)
                JSONObject(it.reader(Charsets.UTF_8).readText())
            }
            fun strings(key: String): Set<String> {
                val array = metadata.getJSONArray(key)
                (0 until array.length()).map { array.getString(it) }.toSet()
            }
            val candidate = ApprovedWasmProvider(
                metadata.getString("id"), metadata.getLong("version"),
                metadata.getString("sha256"), metadata.getString("abi") == "community",
                strings("hosts"), strings("languages"),
            )
            require(WasmRegistry.permitsUpdate(bundled, candidate))
            require(WasmRegistry.select(listOf(candidate), candidate.languages.first()) == listOf(candidate))
            val file = AtomicFile(File(dir, "${bundled.id}.wasm"))
            val bytes = file.openRead().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    require(out.size() + n <= MAX_BYTES)
                    out.write(chunk, 0, n)
                }
                out.toByteArray()
            }
            require(WasmPolicy.verify(bytes, candidate.digest))
            candidate to bytes
        }.getOrNull()
    }
}
