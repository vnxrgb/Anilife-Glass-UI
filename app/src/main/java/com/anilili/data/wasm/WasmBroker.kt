package com.anilili.data.wasm

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/** Per-call capability binder; never exports the app's general network client. */
internal class WasmBroker(private val provider: ApprovedWasmProvider) : Binder() {
    private val gateway = WasmHttpGateway(provider.hosts)
    @Volatile private var revoked = false
    fun revoke() { revoked = true; gateway.cancel() }
    private val deadline = SystemClock.elapsedRealtime() + WasmPolicy.MAX_CALL_MS
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
            require(response.body.size <= 32768)
            require(!revoked && SystemClock.elapsedRealtime() < deadline)
            JSONObject().put("status", response.status)
                .put("headers", JSONArray(response.headers.map { JSONArray(listOf(it.first, it.second)) }))
                .put("body", JSONArray(response.body.map { it.toInt() and 255 })).toString()
                .also { require(it.length <= 200000) }
        }.getOrElse { "" }
        reply?.writeNoException()
        reply?.writeString(result)
        return true
    }

    companion object {
        private const val DESCRIPTOR = "com.anilili.data.wasm.HttpBroker"
        private const val FETCH = IBinder.FIRST_CALL_TRANSACTION

        /** JNI calls this in the isolated process; binder capability is supplied by the host. */
        @JvmStatic fun fetchRemote(binder: IBinder, input: String): String {
            if (input.length > 128 * 1024) return ""
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(input)
                if (!binder.transact(FETCH, data, reply, 0)) "" else {
                    reply.readException()
                    reply.readString().orEmpty()
                }
            } catch (_: Exception) { "" }
            finally { data.recycle(); reply.recycle() }
        }
    }
}
