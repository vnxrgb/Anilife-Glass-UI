package com.anilumos.manga.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** Bounded delivery through the same reviewed, isolated session as the parser.
 * Redirects are intentionally not followed at this checkpoint. */
internal class PageDelivery(private val scope: SourceScope, private val session: SourceSession) {
    suspend fun load(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(scope.requireUrl(url)).header("Accept", "image/*").build()
        session.client.newCall(request).execute().use { readImage(it) }
    }
    companion object {
        private const val MAX_BYTES = 16 * 1024 * 1024
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
            return bytes
        }
    }
}
