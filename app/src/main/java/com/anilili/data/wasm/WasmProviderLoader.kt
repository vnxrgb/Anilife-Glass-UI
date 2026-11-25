package com.anilili.data.wasm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import com.anilili.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Debug-only loader. Only entries from the trusted bundled registry are accepted by resolve().
 * No user-provided paths, hashes, or capabilities are accepted by the production-facing entry point.
 */
internal class WasmProviderLoader(context: Context) {
    private val app = context.applicationContext

    suspend fun resolve(providerId: String, language: String, query: WasmEpisodeQuery): JSONArray {
        if (!BuildConfig.DEBUG) return JSONArray()
        val approved = WasmRegistry.select(WasmRegistry.bundled, language)
            .singleOrNull { it.id == providerId } ?: return JSONArray()
        return withContext(Dispatchers.IO) {
            val bytes = runCatching {
                app.assets.open("wasm/providers/${approved.id}.wasm").use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        require(output.size() + n <= MAX_BYTES)
                        output.write(chunk, 0, n)
                    }
                    output.toByteArray()
                }
            }.getOrNull() ?: return@withContext JSONArray()
            resolveVerified(approved, bytes, query)
        }
    }

    /** Internal fixture entry; not exposed in UI, exported components or normal server selection. */
    internal suspend fun resolveVerified(approved: ApprovedWasmProvider, bytes: ByteArray, query: WasmEpisodeQuery): JSONArray =
        withContext(Dispatchers.IO) {
            serial.withLock {
                if (!BuildConfig.DEBUG || bytes.size > MAX_BYTES || !WasmPolicy.verify(bytes, approved.digest) ||
                    !query.valid() || approved.languages.isEmpty() ||
                    WasmRegistry.select(listOf(approved), approved.languages.first()) != listOf(approved)) {
                    return@withLock JSONArray()
                }
                val temp = File.createTempFile("wasm-", ".tmp", app.cacheDir)
                try {
                    temp.writeBytes(bytes)
                    ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        val connected = CompletableDeferred<IBinder?>()
                        val connection = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName, service: IBinder) { connected.complete(service) }
                            override fun onServiceDisconnected(name: ComponentName) { connected.complete(null) }
                        }
                        val bound = runCatching {
                            app.bindService(Intent(app, ProviderSandboxService::class.java), connection, Context.BIND_AUTO_CREATE)
                        }.getOrDefault(false)
                        if (!bound) return@withLock JSONArray()
                        try {
                            val binder = withTimeoutOrNull(5000) { connected.await() } ?: return@withLock JSONArray()
                            val data = Parcel.obtain()
                            val reply = Parcel.obtain()
                            val broker = WasmBroker(approved)
                            try {
                                data.writeInterfaceToken(ProviderSandboxService.DESCRIPTOR)
                                fd.writeToParcel(data, 0)
                                data.writeInt(if (approved.communityAbi) 1 else 0)
                                data.writeString(query.json())
                                data.writeStrongBinder(broker)
                                if (!binder.transact(ProviderSandboxService.TRANSACTION_RESOLVE, data, reply, 0)) JSONArray()
                                else {
                                    reply.readException()
                                    WasmResultPolicy.validated(reply.readString().orEmpty(), approved, query.dub)
                                }
                            } catch (_: Exception) { JSONArray() }
                            finally { data.recycle(); reply.recycle(); broker.revoke() }
                        } finally { runCatching { app.unbindService(connection) } }
                    }
                } finally { temp.delete() }
            }
        }

    private companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        val serial = Mutex()
    }
}

internal data class WasmEpisodeQuery(val anilistId: Long, val malId: Long?, val titles: List<String>, val episode: Double, val dub: Boolean) {
    fun valid(): Boolean = anilistId in 1..4294967295L &&
        (malId == null || malId in 1..4294967295L) &&
        titles.size in 1..32 && titles.all { it.isNotBlank() && it.toByteArray(Charsets.UTF_8).size <= 512 } &&
        episode.isFinite() && episode > 0 && episode <= 100000
    fun json(): String = JSONObject().put("anilist_id", anilistId)
        .put("mal_id", malId ?: JSONObject.NULL).put("titles", JSONArray(titles))
        .put("episode", episode).put("dub", dub).toString()
}
