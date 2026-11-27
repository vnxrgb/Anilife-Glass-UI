package com.anilumos.manga.engine

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** Bounded delivery through the same reviewed, isolated session as the parser.
 * Only bounded same-origin or explicitly configured CDN HTTPS redirects are accepted; access denials are not retried. */
internal class PageDelivery(private val scope: SourceScope, private val session: SourceSession) {
    suspend fun load(url: String): ByteArray = withContext(Dispatchers.IO) {
        var current = scope.requireUrl(url)
        val visited = mutableSetOf<HttpUrl>()
        repeat(4) { hop ->
            if (!visited.add(current)) throw IOException("Image redirect loop")
            val request = Request.Builder().url(current).header("Accept", imageAccept(Build.VERSION.SDK_INT)).build()
            session.client.newCall(request).execute().use { response ->
                if (response.code !in setOf(301, 302, 303, 307, 308)) return@withContext readImage(response)
                if (hop == 3) throw IOException("Image redirect limit")
                current = redirectTarget(scope, current, response.header("Location"))
            }
        }
        throw IOException("Image redirect limit")
    }
    companion object {
        internal fun imageAccept(api: Int): String =
            if (api >= 31) "image/avif,image/webp,image/png,image/jpeg,image/gif"
            else "image/webp,image/png,image/jpeg,image/gif"
        private const val MAX_BYTES = 16 * 1024 * 1024
        internal fun redirectTarget(scope: SourceScope, from: HttpUrl, location: String?): HttpUrl {
            val next = scope.requireUrl(location ?: throw IOException("Missing redirect location"), from.toString())
            if (!scope.permitsImageRedirect(from, next)) throw SourceScopeException()
            return next
        }
        internal fun readImage(response: Response): ByteArray {
            if (!response.isSuccessful) throw IOException("Image HTTP ${response.code}")
            val body = response.body
            val type = body.contentType()
            if (type?.type != "image" || type.subtype !in setOf("jpeg", "png", "webp", "gif", "avif"))
                throw IOException("Unsupported image content")
            if (body.contentLength() > MAX_BYTES) throw IOException("Image exceeds size limit")
            val input = body.source()
            input.request(MAX_BYTES.toLong() + 1)
            if (input.buffer.size > MAX_BYTES) throw IOException("Image exceeds size limit")
            val bytes = input.readByteArray()
            if (bytes.isEmpty()) throw IOException("Empty image")
            if (!matchesImageHeader(bytes, type.subtype)) throw IOException("Image signature mismatch")
            return bytes
        }
        // Header validation is not a substitute for full Android image decoding.
        internal fun matchesImageHeader(bytes: ByteArray, subtype: String): Boolean {
            fun at(offset: Int, vararg values: Int): Boolean =
                bytes.size >= offset + values.size && values.indices.all { (bytes[offset + it].toInt() and 255) == values[it] }
            fun ascii(offset: Int, value: String): Boolean = at(offset, *value.map { it.code }.toIntArray())
            return when (subtype) {
                "png" -> at(0, 137, 80, 78, 71, 13, 10, 26, 10)
                "jpeg" -> at(0, 255, 216, 255)
                "gif" -> ascii(0, "GIF87a") || ascii(0, "GIF89a")
                "webp" -> ascii(0, "RIFF") && ascii(8, "WEBP")
                "avif" -> ascii(4, "ftyp") && (ascii(8, "avif") || ascii(8, "avis"))
                else -> false
            }
        }
    }
}
