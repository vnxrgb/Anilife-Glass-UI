package com.anilili.data.wasm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Fixture-only mobile diagnostic. Never executes a module in the main app process. */
internal class SandboxProbeClient(context: Context) {
    private val app = context.applicationContext
    suspend fun probe(): Boolean = withContext(Dispatchers.IO) {
        val connected = CompletableDeferred<IBinder?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                connected.complete(service)
            }
            override fun onServiceDisconnected(name: ComponentName) {
                connected.complete(null)
            }
        }
        val bound = runCatching {
            app.bindService(Intent(app, ProviderSandboxService::class.java), connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) return@withContext false
        try {
            val binder = withTimeoutOrNull(5000) { connected.await() } ?: return@withContext false
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ProviderSandboxService.DESCRIPTOR)
                if (!binder.transact(ProviderSandboxService.TRANSACTION_PROBE, data, reply, 0)) false
                else {
                    reply.readException()
                    reply.readInt() == 1
                }
            } catch (_: Exception) {
                false
            } finally {
                data.recycle()
                reply.recycle()
            }
        } finally {
            runCatching { app.unbindService(connection) }
        }
    }
}
