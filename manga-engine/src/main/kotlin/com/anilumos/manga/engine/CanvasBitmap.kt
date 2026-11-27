package com.anilumos.manga.engine

import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import java.io.Closeable

/** Bounded implementation of the parser bitmap contract for ordinary, whole-image
 * scaling/composition. Not a descrambling adapter; partial-source tile reassembly is
 * deliberately not enabled. Only used on already decoded authorized images. */
internal class CanvasBitmap private constructor(internal val pixels: AndroidBitmap) : Bitmap, Closeable {
    override val width: Int get() = pixels.width
    override val height: Int get() = pixels.height
    override fun drawBitmap(sourceBitmap: Bitmap, src: Rect, dst: Rect) {
        val source = sourceBitmap as? CanvasBitmap ?: error("Unsupported bitmap implementation")
        check(!pixels.isRecycled && !source.pixels.isRecycled && pixels !== source.pixels)
        require(src.left == 0 && src.top == 0 && src.right == source.width && src.bottom == source.height) { "Partial source composition unsupported" }
        require(dst.left >= 0 && dst.top >= 0 && dst.right <= width && dst.bottom <= height && dst.width > 0 && dst.height > 0)
        Canvas(pixels).drawBitmap(source.pixels, AndroidRect(src.left, src.top, src.right, src.bottom),
            AndroidRect(dst.left, dst.top, dst.right, dst.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    override fun close() { if (!pixels.isRecycled) pixels.recycle() }
    companion object {
        fun create(width: Int, height: Int): CanvasBitmap {
            require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= 4_000_000L)
            return CanvasBitmap(AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888))
        }
        fun copyOf(input: AndroidBitmap): CanvasBitmap {
            require(!input.isRecycled && input.width.toLong() * input.height <= 4_000_000L)
            val copy = input.copy(AndroidBitmap.Config.ARGB_8888, false) ?: error("Bitmap copy failed")
            return CanvasBitmap(copy)
        }
    }
}
