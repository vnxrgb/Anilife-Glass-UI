package com.anilili.ui.profile

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.LocalDate

internal data class WrappedTitle(val title: String, val artwork: String?, val time: String)
internal data class WrappedPersonality(val title: String, val description: String, val symbol: String)

internal fun wrappedPersonality(stats: WrappedStats, titles: Int, genres: List<String>): WrappedPersonality = when {
    stats.episodes == 0 && titles == 0 -> WrappedPersonality("Your story begins", "Your viewing style will emerge as you watch.", "✦")
    stats.streak >= 14 -> WrappedPersonality("The Dedicated Fan", "You keep coming back to your anime journey.", "✦")
    stats.episodes >= 100 && stats.activeDays > 0 && stats.episodes.toDouble() / stats.activeDays >= 4 -> WrappedPersonality("The Binge Master", "Your active days are packed with episodes.", "▶")
    titles >= 10 && genres.size >= 5 -> WrappedPersonality("The Genre Explorer", "You explore different worlds and stories.", "◇")
    genres.firstOrNull() == "Slice of Life" -> WrappedPersonality("The Comfort Seeker", "You gravitate toward life's quieter stories.", "♡")
    genres.firstOrNull() == "Action" -> WrappedPersonality("The Action Fan", "Action leads your recorded genre rankings.", "⚡")
    else -> WrappedPersonality("The Anime Enthusiast", "Every episode adds to your own story.", "✦")
}

internal suspend fun shareWrappedImage(
    context: Context, stats: WrappedStats, watchTime: String, titleCount: Int,
    titles: List<WrappedTitle>, genres: List<String>, personality: WrappedPersonality, accent: Int,
) {
    val file = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "wrapped").apply { mkdirs() }
        val posters = titles.take(3).map { title ->
            try {
                if (title.artwork.isNullOrBlank()) null else {
                    val result = context.imageLoader.execute(ImageRequest.Builder(context)
                        .data(title.artwork).size(400, 500).allowHardware(false).build())
                    (result as? SuccessResult)?.drawable?.toBitmap()
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val white = Color.rgb(245, 247, 252)
        val muted = Color.rgb(174, 185, 204)
        fun tint(color: Int, alpha: Int) = (color and 0xFFFFFF) or (alpha shl 24)
        fun text(value: String, x: Float, y: Float, size: Float, bold: Boolean = false, color: Int = white, width: Float = 980f) {
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = color
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            val label = if (paint.measureText(value) <= width) value else {
                var end = value.length
                while (end > 0 && paint.measureText(value.take(end) + "…") > width) end--
                value.take(end) + "…"
            }
            canvas.drawText(label, x, y, paint)
        }
        fun panel(x: Float, y: Float, w: Float, h: Float, color: Int = accent) {
            val rect = RectF(x, y, x+w, y+h)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(x,y,x+w,y+h,intArrayOf(tint(color,39),Color.rgb(25,31,45),Color.rgb(19,24,36)),null,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect,24f,24f,paint)
            paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
            paint.color = tint(white,38); canvas.drawRoundRect(rect,24f,24f,paint); paint.style = Paint.Style.FILL
        }
        paint.shader = LinearGradient(0f,0f,1080f,1920f,intArrayOf(Color.rgb(13,18,30),Color.rgb(23,31,45),Color.rgb(11,15,25)),null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,1080f,1920f,paint); paint.shader = null
        text("ANILUMOS  /  ${stats.year}",56f,90f,28f,true,accent)
        text("AniLumos Wrapped",56f,174f,75f,true)
        text("Your anime year in review",56f,228f,32f,false,muted)
        val metrics = listOf("Watch time" to watchTime, "Episodes" to stats.episodes.toString(),
            "Anime played" to titleCount.toString(), "Active days" to stats.activeDays.toString(),
            "Current streak" to "${stats.streak} days", "Peak month" to
                (if (stats.episodes > 0) java.time.Month.of(stats.months.indexOf(stats.months.maxOrNull() ?: 0)+1).name.lowercase().replaceFirstChar { it.uppercase() } else "—"))
        metrics.forEachIndexed { i, (label,value) ->
            val x=56f+(i%3)*328f; val y=278f+(i/3)*160f
            panel(x,y,312f,144f); text(label,x+22f,y+44f,25f,false,muted,270f)
            text(value,x+22f,y+106f,46f,true,white,270f)
        }
        text("Top anime this year",56f,655f,38f,true)
        if (titles.isEmpty()) text("Your top anime will appear as you watch.",56f,720f,30f,false,muted)
        titles.take(3).forEachIndexed { i, title ->
            val x=56f+i*328f; val y=685f
            panel(x,y,312f,440f)
            val poster=posters[i]
            if (poster != null) {
                val dest=RectF(x+12f,y+12f,x+300f,y+332f)
                val scale=maxOf(dest.width()/poster.width,dest.height()/poster.height)
                val sw=(dest.width()/scale).toInt().coerceAtLeast(1); val sh=(dest.height()/scale).toInt().coerceAtLeast(1)
                val sx=(poster.width-sw)/2; val sy=(poster.height-sh)/2
                val clip=Path().apply { addRoundRect(dest,18f,18f,Path.Direction.CW) }
                canvas.save(); canvas.clipPath(clip)
                paint.shader=null; paint.color=Color.WHITE
                canvas.drawBitmap(poster,Rect(sx,sy,sx+sw,sy+sh),dest,paint); canvas.restore()
            }
            text("#${i+1}  ${title.title}",x+18f,y+376f,27f,true,white,276f)
            text(title.time,x+18f,y+415f,25f,false,muted,276f)
        }
        text("Top genres",56f,1190f,38f,true)
        panel(56f,1220f,968f,120f)
        text(genres.take(3).joinToString("   ·   ").ifBlank { "Not enough recorded activity yet" },80f,1294f,34f,true,white,920f)
        text("Your viewing personality",56f,1400f,38f,true)
        panel(56f,1430f,968f,230f)
        text(personality.symbol,82f,1520f,62f,true,accent)
        text(personality.title,170f,1518f,49f,true,white,820f)
        text(personality.description,82f,1594f,29f,false,muted,910f)
        text("Generated by AniLumos",56f,1760f,34f,true)
        text("Recorded on this device • measured native playback",56f,1810f,25f,false,muted)
        text("Older viewing history may be incomplete.",56f,1852f,25f,false,muted)
        File(directory,"AniLumos-Wrapped-${stats.year}-${System.currentTimeMillis()}.png").also { output ->
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            bitmap.recycle()
        }
    }
    val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
    val intent=Intent(Intent.ACTION_SEND).apply {
        type="image/png"; putExtra(Intent.EXTRA_STREAM,uri)
        clipData=ClipData.newUri(context.contentResolver,"AniLumos Wrapped",uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent,"Share AniLumos Wrapped"))
}
