package com.anilili.data.wasm

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object WasmRedirectPolicy {
    fun next(current: String, location: String?, status: Int, method: String, hops: Int, hosts: Set<String>): String? {
        if (method !in setOf("GET", "HEAD") || status !in setOf(301, 302, 303, 307, 308) ||
            hops >= 6 || location.isNullOrBlank() || location.length > 4096) return null
        if (!WasmPolicy.allowsUrl(current, hosts)) return null
        val destination = current.toHttpUrlOrNull()?.resolve(location)?.toString() ?: return null
        return destination.takeIf { WasmPolicy.allowsUrl(it, hosts) }
    }
}
