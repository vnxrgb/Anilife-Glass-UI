package com.anilumos.manga.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.aomedia.avif.android.AvifDecoder
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Decode a bounded preview/page; the caller owns and must release the returned bitmap. */
internal object PageDecoder {
    private const val MAX_PIXELS = 4_000_000L
    private const val HIGH_QUALITY_PIXELS = 8_000_000L
    internal fun pixelBudget(targetWidth: Int, targetHeight: Int): Long =
        if (targetWidth >= 3072 && targetHeight >= 8192) HIGH_QUALITY_PIXELS else MAX_PIXELS
    // Native AVIF decoding may allocate full-size intermediate planes before scaling.
    private const val MAX_AVIF_SOURCE_PIXELS = 16_000_000L
    private val avifMutex = Mutex()
    internal fun avifOutputSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Pair<Int, Int> {
        if (width !in 1..16384 || height !in 1..16384 ||
            width.toLong() * height > MAX_AVIF_SOURCE_PIXELS) throw IOException("AVIF source exceeds decode budget")
        val sample = sampleSize(width, height, targetWidth, targetHeight)
        return ((width.toLong() + sample - 1) / sample).toInt() to
            ((height.toLong() + sample - 1) / sample).toInt()
    }
    internal suspend fun decodeAvif(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap =
        avifMutex.withLock {
            if (bytes.isEmpty() || bytes.size > 16 * 1024 * 1024) throw IOException("Invalid AVIF size")
            try {
                val encoded = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); rewind() }
                val info = AvifDecoder.Info()
                if (!AvifDecoder.getInfo(encoded, bytes.size, info)) throw IOException("Invalid AVIF header")
                val (width, height) = avifOutputSize(info.width, info.height, targetWidth, targetHeight)
                encoded.rewind()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                var success = false
                try {
                    if (!AvifDecoder.decode(encoded, bytes.size, bitmap, 1)) throw IOException("AVIF fallback decode failed")
                    success = true
                    bitmap
                } finally {
                    if (!success) bitmap.recycle()
                }
            } catch (e: LinkageError) {
                throw IOException("AVIF fallback unavailable on this device ABI", e)
            }
        }
    internal fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        require(width > 0 && height > 0 && targetWidth > 0 && targetHeight > 0)
        var sample = 1
        fun scaled(value: Int): Long = (value.toLong() + sample - 1) / sample
        while (scaled(width) * scaled(height) > pixelBudget(targetWidth, targetHeight) ||
            scaled(width) > targetWidth || scaled(height) > targetHeight) {
            sample *= 2
        }
        return sample
    }
    suspend fun decode(bytes: ByteArray, targetWidth: Int = 2048, targetHeight: Int = 4096): Bitmap =
        withContext(Dispatchers.Default) {
            require(targetWidth > 0 && targetHeight > 0)
            if (bytes.isEmpty() || bytes.size > 16 * 1024 * 1024) throw IOException("Invalid image size")
            val isAvif = PageDelivery.matchesImageHeader(bytes, "avif")
            if (isAvif && Build.VERSION.SDK_INT < 31) return@withContext decodeAvif(bytes, targetWidth, targetHeight)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                if (isAvif) return@withContext decodeAvif(bytes, targetWidth, targetHeight)
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
                ?: if (isAvif) return@withContext decodeAvif(bytes, targetWidth, targetHeight)
                else throw IOException("Image decode failed")
            if (bitmap.width.toLong() * bitmap.height > pixelBudget(targetWidth, targetHeight) || bitmap.width > targetWidth || bitmap.height > targetHeight) {
                bitmap.recycle()
                throw IOException("Decoded bitmap exceeds budget")
            }
            bitmap
        }
}
