package com.anilili.data.wasm

/** Native entry point, called only by future isolated-service IPC. No fallback to in-process execution. */
internal object NativeSandbox {
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("anilumos_wasm_runtime") }.isSuccess
    }
    @JvmStatic external fun probe(bytes: ByteArray): Boolean
}
