package com.anilili.data.wasm

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * Reserved isolated process for the native component runtime.
 * No guest execution, networking or file access is enabled in this initial boundary.
 * The service is intentionally inert until a bounded IPC protocol and runtime are ready.
 */
class ProviderSandboxService : Service() {
    override fun onBind(intent: Intent?): IBinder = Binder()
}
