package com.anilumos.manga.engine

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reader-only presentation operations on already decoded, authorized raster pixels.
 * Not used to reconstruct protected or scrambled provider images. Caller owns the input
 * and any distinct returned bitmap. */
internal object ReaderImageTransform {
    suspend fun resize(input: Bitmap, width: Int, height: Int): Bitmap = withContext(Dispatchers.Default) {
        require(!input.isRecycled && input.width.toLong() * input.height <= 4_000_000L)
        require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= 4_000_000L)
        Bitmap.createScaledBitmap(input, width, height, true)
    }
    suspend fun rotateQuarterTurns(input: Bitmap, turns: Int): Bitmap = withContext(Dispatchers.Default) {
        require(!input.isRecycled && input.width.toLong() * input.height <= 4_000_000L)
        val normalized = Math.floorMod(turns, 4)
        if (normalized == 0) input else Bitmap.createBitmap(input, 0, 0, input.width, input.height,
            Matrix().apply { postRotate(normalized * 90f) }, false)
    }
    suspend fun crop(input: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap =
        withContext(Dispatchers.Default) {
            require(!input.isRecycled && input.width.toLong() * input.height <= 4_000_000L)
            require(left >= 0 && top >= 0 && width > 0 && height > 0 &&
                left.toLong() + width <= input.width && top.toLong() + height <= input.height)
            Bitmap.createBitmap(input, left, top, width, height)
        }
}
