package com.anilili.data.wasm

import android.content.Context
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.PublicKey

/** A single atomic signed envelope. Re-authenticate the original manifest on every load. */
internal object WasmInstalledModules {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private fun directory(context: Context) = File(context.noBackupFilesDir, "reviewed-wasm")
    private fun approved(p: ApprovedWasmProvider) =
        WasmRegistry.select(listOf(p), p.languages.firstOrNull().orEmpty()) == listOf(p)

    @Synchronized
    fun install(context: Context, bundled: ApprovedWasmProvider, manifest: ByteArray,
                signature: ByteArray, module: ByteArray, pinnedKey: PublicKey): Boolean {
        if (!com.anilili.BuildConfig.DEBUG || !approved(bundled)) return false
        val existing = load(context, bundled, pinnedKey)?.first ?: bundled
        val candidate = WasmUpdatePolicy.verifiedCandidate(existing, manifest, signature, module, pinnedKey) ?: return false
        if (!WasmRegistry.permitsUpdate(bundled, candidate)) return false
        return runCatching {
            val dir = directory(context)
            require(dir.isDirectory || dir.mkdirs())
            val file = AtomicFile(File(dir, "${bundled.id}.signed"))
            val stream = file.startWrite()
            try {
                val output = DataOutputStream(stream)
                output.writeInt(1)
                for (part in listOf(manifest, signature, module)) {
                    output.writeInt(part.size)
                    output.write(part)
                }
                output.flush()
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun load(context: Context, bundled: ApprovedWasmProvider, pinnedKey: PublicKey? = null): Pair<ApprovedWasmProvider, ByteArray>? {
        if (!com.anilili.BuildConfig.DEBUG || pinnedKey == null || !approved(bundled)) return null
        return runCatching {
            val file = AtomicFile(File(directory(context), "${bundled.id}.signed"))
            DataInputStream(file.openRead()).use { input ->
                require(input.readInt() == 1)
                fun part(max: Int): ByteArray {
                    val length = input.readInt()
                    require(length in 1..max)
                    return ByteArray(length).also { input.readFully(it) }
                }
                val manifest = part(128 * 1024)
                val signature = part(1024)
                val module = part(MAX_BYTES)
                require(input.read() == -1)
                val candidate = requireNotNull(WasmUpdatePolicy.verifiedCandidate(bundled, manifest, signature, module, pinnedKey))
                candidate to module
            }
        }.getOrNull()
    }
}
