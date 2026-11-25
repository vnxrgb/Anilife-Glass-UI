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
    override fun onBind(intent: Intent?): IBinder = endpoint

    companion object {
        internal const val DESCRIPTOR = "com.anilili.data.wasm.Probe"
        internal const val TRANSACTION_PROBE = IBinder.FIRST_CALL_TRANSACTION
    }
}
