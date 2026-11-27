package com.anilumos.manga.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Decode a bounded preview/page; the caller owns and must release the returned bitmap. */
internal object PageDecoder {
    private const val MAX_PIXELS = 4_000_000L
    internal fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        require(width > 0 && height > 0 && targetWidth > 0 && targetHeight > 0)
        var sample = 1
        fun scaled(value: Int): Long = (value.toLong() + sample - 1) / sample
        while (scaled(width) * scaled(height) > MAX_PIXELS ||
            scaled(width) > targetWidth || scaled(height) > targetHeight) {
            sample *= 2
        }
        return sample
    }
    suspend fun decode(bytes: ByteArray, targetWidth: Int = 2048, targetHeight: Int = 4096): Bitmap =
        withContext(Dispatchers.Default) {
            require(targetWidth > 0 && targetHeight > 0)
            if (bytes.isEmpty() || bytes.size > 16 * 1024 * 1024) throw IOException("Invalid image size")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                val format = listOf("avif", "webp", "jpeg", "png", "gif").firstOrNull {
                    PageDelivery.matchesImageHeader(bytes, it)
                } ?: "unknown"
                throw IOException("Invalid or unsupported image dimensions (format=$format, API=${Build.VERSION.SDK_INT}, bytes=${bytes.size})")
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: throw IOException("Image decode failed")
            if (bitmap.width.toLong() * bitmap.height > MAX_PIXELS || bitmap.width > targetWidth || bitmap.height > targetHeight) {
                bitmap.recycle()
                throw IOException("Decoded bitmap exceeds budget")
            }
            bitmap
        }
}
