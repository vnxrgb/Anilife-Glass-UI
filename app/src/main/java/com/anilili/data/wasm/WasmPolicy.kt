package com.anilili.data.wasm

import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Trusted host policy. Provider self-declarations never grant permissions. */
internal object WasmPolicy {
    const val MAX_MODULE_BYTES = 64 * 1024 * 1024
    const val MAX_RESPONSE_BYTES = 16L * 1024 * 1024
    const val MAX_CALL_MS = 45_000L
    private val componentHeader = byteArrayOf(0, 97, 115, 109, 13, 0, 1, 0)

    fun componentHeaderValid(bytes: ByteArray): Boolean =
        bytes.size in 8..MAX_MODULE_BYTES && componentHeader.indices.all { bytes[it] == componentHeader[it] }

    /** Expected digest must originate in an authenticated manifest or bundled resource. */
    fun verify(bytes: ByteArray, expectedSha256: String): Boolean {
        if (!componentHeaderValid(bytes) || !expectedSha256.matches(Regex("[0-9a-fA-F]{64}"))) return false
        val expected = ByteArray(32) { expectedSha256.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes), expected)
    }

    fun allowsUrl(value: String, approvedHosts: Set<String>): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        if (!url.isHttps || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        val host = url.host
        if (!validDomain(host)) return false
        return approvedHosts.any { approved ->
            validDomain(approved) && (host == approved || host.endsWith(".$approved"))
        }
    }

    private fun validDomain(host: String): Boolean =
        host.length <= 253 && host.contains('.') && !host.endsWith(".") &&
            host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") &&
            !host.endsWith(".internal") && !host.endsWith(".home.arpa") &&
            host.last().isLetter() && host.split('.').all {
                it.length in 1..63 && it.first().isLetterOrDigit() && it.last().isLetterOrDigit() &&
                    it.all { c -> c in 'a'..'z' || c in '0'..'9' || c == '-' }
            }
}
