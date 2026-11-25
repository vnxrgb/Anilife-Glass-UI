package com.anilili.data.wasm

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.DataOutputStream
import java.io.ByteArrayOutputStream

/** Per-invocation capability. HTTP bytes travel through a read-only FD, not Binder JSON. */
internal class WasmBroker(private val provider: ApprovedWasmProvider, private val cacheDir: File) : Binder() {
    private val gateway = WasmHttpGateway(provider.hosts)
    @Volatile private var revoked = false
    private val deadline = SystemClock.elapsedRealtime() + WasmPolicy.MAX_CALL_MS
    fun revoke() { revoked = true; gateway.cancel() }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != FETCH) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        val input = data.readString().orEmpty()
        val result = runCatching {
            require(!revoked && SystemClock.elapsedRealtime() < deadline)
            require(input.length <= 128 * 1024)
            val req = JSONObject(input)
            val headers = req.optJSONArray("headers") ?: JSONArray()
            require(headers.length() <= 32)
            val pairs = (0 until headers.length()).map { i ->
                val pair = headers.getJSONArray(i)
                pair.getString(0) to pair.getString(1)
            }
            val body = req.optJSONArray("body")?.let { array ->
                require(array.length() <= 32768)
                ByteArray(array.length()) { i -> array.getInt(i).also { require(it in 0..255) }.toByte() }
            }
            val response = gateway.fetch(req.getString("url"), req.getString("method"), pairs, body)
            require(response.body.size <= MAX_BODY)
            require(!revoked && SystemClock.elapsedRealtime() < deadline)
            val metadata = JSONObject().put("status", response.status)
                .put("headers", JSONArray(response.headers.map { JSONArray(listOf(it.first, it.second)) }))
                .toString().toByteArray(Charsets.UTF_8)
            require(metadata.size in 1..MAX_METADATA)
            val temp = File.createTempFile("wasm-http-", ".bin", cacheDir)
            try {
                DataOutputStream(temp.outputStream()).use { stream ->
                    stream.writeInt(metadata.size)
                    stream.write(metadata)
                    stream.write(response.body)
                }
                ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
            } finally {
                temp.delete()
            }
        }.getOrNull()
        reply?.writeNoException()
        try {
            reply?.writeInt(if (result == null) 0 else 1)
            if (result != null && reply != null) result.writeToParcel(reply, 0)
        } finally {
            result?.close()
        }
        return true
    }

    companion object {
        private const val DESCRIPTOR = "com.anilili.data.wasm.HttpBroker"
        private const val FETCH = IBinder.FIRST_CALL_TRANSACTION
        private const val MAX_BODY = 4 * 1024 * 1024
        private const val MAX_METADATA = 64 * 1024

        @JvmStatic fun fetchRemote(binder: IBinder, input: String): ByteArray {
            if (input.length > 128 * 1024) return ByteArray(0)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(input)
                if (!binder.transact(FETCH, data, reply, 0)) ByteArray(0) else {
                    reply.readException()
                    if (reply.readInt() != 1) ByteArray(0) else {
                        val fd = ParcelFileDescriptor.CREATOR.createFromParcel(reply)
                        ParcelFileDescriptor.AutoCloseInputStream(fd).use { stream ->
                            val output = ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            while (true) {
                                val n = stream.read(chunk)
                                if (n < 0) break
                                require(output.size() + n <= MAX_BODY + MAX_METADATA + 4)
                                output.write(chunk, 0, n)
                            }
                            output.toByteArray()
                        }
                    }
                }
            } catch (_: Exception) { ByteArray(0) }
            finally { data.recycle(); reply.recycle() }
        }
    }
}
