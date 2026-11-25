package com.anilili.data.wasm

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process

/** Experimental, same-app-only isolated endpoint. Accepts only the synthetic runtime fixture. */
class ProviderSandboxService : Service() {
    private val endpoint = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == TRANSACTION_RESOLVE) return resolveFixture(data, reply)
            if (code != TRANSACTION_PROBE) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            // This endpoint must never execute in the application's privileged process.
            val isolated = Process.myUid() % 100000 in 90000..99999
            val ok = isolated && runCatching {
                NativeSandbox.available && NativeSandbox.probe(byteArrayOf(0, 97, 115, 109, 13, 0, 1, 0))
            }.getOrDefault(false)
            reply?.writeNoException()
            reply?.writeInt(if (ok) 1 else 0)
            return true
        }
    }
    // Debug-only experimental path. The production registry remains empty.
    private fun resolveFixture(data: Parcel, reply: Parcel?): Boolean {
        data.enforceInterface(DESCRIPTOR)
        if (!com.anilili.BuildConfig.DEBUG || Process.myUid() % 100000 !in 90000..99999) {
            reply?.writeNoException()
            reply?.writeString("")
            return true
        }
        val timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        val watchdog = timer.schedule({ Process.killProcess(Process.myPid()) }, WasmPolicy.MAX_CALL_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        val result = try { runCatching {
            require(com.anilili.BuildConfig.DEBUG)
            require(Process.myUid() % 100000 in 90000..99999)
            require(NativeSandbox.available)
            val fd = android.os.ParcelFileDescriptor.CREATOR.createFromParcel(data)
            val bytes = android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    require(buffer.size() + n <= WasmPolicy.MAX_MODULE_BYTES)
                    buffer.write(chunk, 0, n)
                }
                buffer.toByteArray()
            }
            require(WasmPolicy.componentHeaderValid(bytes))
            val community = data.readInt() == 1
            val query = data.readString().orEmpty()
            require(query.length <= 32768)
            val broker = requireNotNull(data.readStrongBinder())
            NativeSandbox.resolve(bytes, community, query, broker, WasmBroker::class.java).orEmpty()
                .also { require(it.length <= 200000) }
        }.getOrDefault("") } finally {
            watchdog.cancel(false)
            timer.shutdownNow()
        }
        reply?.writeNoException()
        reply?.writeString(result)
        return true
    }

    override fun onBind(intent: Intent?): IBinder = endpoint

    companion object {
        internal const val DESCRIPTOR = "com.anilili.data.wasm.Probe"
        internal const val TRANSACTION_PROBE = IBinder.FIRST_CALL_TRANSACTION
        internal const val TRANSACTION_RESOLVE = IBinder.FIRST_CALL_TRANSACTION + 1
    }
}
