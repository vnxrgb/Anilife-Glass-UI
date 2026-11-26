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
    titles: List<WrappedTitle>, genres: List<String>, personality: WrappedPersonality, accent: Int, name: String = "",
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
        val purple = Color.rgb(109,61,242)
        val gold = Color.rgb(255,210,63)
        val personalityColor = when (personality.title) {
            "The Dedicated Fan" -> gold
            "The Binge Master" -> Color.rgb(255,134,100)
            "The Genre Explorer" -> Color.rgb(122,211,255)
            "The Comfort Seeker" -> Color.rgb(144,222,184)
            "The Action Fan" -> Color.rgb(255,108,136)
            else -> Color.rgb(190,162,255)
        }
        fun artworkAccent(): Int {
            val poster = posters.firstOrNull() ?: return purple
            val bins = FloatArray(12)
            val hsv = FloatArray(3)
            for (y in 0 until poster.height step maxOf(1,poster.height/32)) {
                for (x in 0 until poster.width step maxOf(1,poster.width/32)) {
                    Color.colorToHSV(poster.getPixel(x,y),hsv)
                    if (hsv[1] > .3f && hsv[2] > .25f) bins[(hsv[0]/30f).toInt().coerceIn(0,11)] += hsv[1]*hsv[2]
                }
            }
            val bin = bins.indices.maxByOrNull { bins[it] } ?: return purple
            return if (bins[bin] == 0f) purple else Color.HSVToColor(floatArrayOf(bin*30f+15f,.65f,.8f))
        }
        fun panel(x: Float,y: Float,w: Float,h: Float, color: Int = white) {
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = tint(color,20)
            val rect = RectF(x,y,x+w,y+h)
            canvas.drawRoundRect(rect,30f,30f,paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
            paint.color = tint(color,66); canvas.drawRoundRect(rect,30f,30f,paint)
            paint.style = Paint.Style.FILL
        }
        fun logo(x: Float,y: Float,size: Float) {
            canvas.save(); canvas.translate(x,y); canvas.scale(size/100f,size/100f)
            paint.shader = null; paint.style = Paint.Style.FILL; paint.color = purple
            canvas.drawRoundRect(RectF(0f,0f,100f,100f),22f,22f,paint)
            paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 12f
            paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(Path().apply { moveTo(28f,74f); lineTo(50f,26f); lineTo(72f,74f) },paint)
            paint.style = Paint.Style.FILL; paint.color = gold; canvas.drawCircle(50f,64f,6.5f,paint)
            canvas.restore()
        }
        fun wrappedLines(value: String,x: Float,y: Float,size: Float,width: Float) {
            paint.typeface = Typeface.create("sans-serif",Typeface.BOLD); paint.textSize = size
            val words = value.split(Regex("\\s+"))
            var line = ""; var row = 0
            for (word in words) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(next) > width && line.isNotEmpty() && row == 0) {
                    text(line,x,y,size,true,white,width); row++; line = word
                } else line = next
            }
            text(line,x,y+row*(size+7f),size,true,white,width)
        }
        canvas.drawColor(Color.rgb(13,12,23))
        paint.shader = RadialGradient(920f,410f,1050f,intArrayOf(tint(artworkAccent(),100),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,1080f,1920f,paint); paint.shader = null
        logo(56f,54f,66f)
        text("ANILUMOS",140f,96f,27f,true)
        text(stats.year.toString(),886f,96f,30f,true,gold)
        text("WRAPPED",56f,213f,112f,true,personalityColor)
        text(if (name.isBlank()) "YOUR ANIME YEAR IN REVIEW" else "$name's anime year in review",60f,264f,30f,true,muted,968f)
        panel(56f,310f,520f,410f,personalityColor)
        text("TIME SPENT IN",86f,369f,26f,true,muted)
        text("ANIME WORLDS",86f,406f,26f,true,muted)
        text(watchTime,82f,549f,106f,true,white,460f)
        text("Recorded watch time",86f,613f,26f,false,muted,460f)
        text("this year",86f,651f,26f,false,muted)
        val metrics = mutableListOf("Anime played" to titleCount.toString(),"Episodes completed" to stats.episodes.toString())
        if (stats.activeDays > 0) metrics += "Active days" to stats.activeDays.toString()
        if (stats.streak > 0) metrics += "Streak" to "${stats.streak} days"
        val metricHeight = (410f-12f*(metrics.size-1))/metrics.size
        metrics.forEachIndexed { i,(label,value) ->
            val y=310f+i*(metricHeight+12f)
            panel(592f,y,432f,metricHeight)
            text(label,616f,y+31f,23f,false,muted,384f)
            text(value,616f,y+metricHeight-14f,if (metrics.size > 2) 34f else 60f,true,white,384f)
        }
        text("TOP ANIME",56f,804f,36f,true)
        if (titles.isEmpty()) text("Your top anime will appear as you watch.",56f,880f,30f,false,muted)
        titles.take(3).forEachIndexed { i,title ->
            val x=56f+i*328f; val y=833f
            val dest=RectF(x,y,x+312f,y+350f)
            panel(x,y,312f,350f)
            val poster=posters[i]
            if (poster != null) {
                val scale=maxOf(dest.width()/poster.width,dest.height()/poster.height)
                val sw=(dest.width()/scale).toInt().coerceIn(1,poster.width)
                val sh=(dest.height()/scale).toInt().coerceIn(1,poster.height)
                val sx=(poster.width-sw)/2; val sy=(poster.height-sh)/2
                canvas.save(); canvas.clipPath(Path().apply { addRoundRect(dest,24f,24f,Path.Direction.CW) })
                paint.shader=null; paint.color=Color.WHITE
                canvas.drawBitmap(poster,Rect(sx,sy,sx+sw,sy+sh),dest,paint); canvas.restore()
            }
            paint.color=Color.rgb(15,13,25); canvas.drawRoundRect(RectF(x+12f,y+12f,x+77f,y+70f),16f,16f,paint)
            text("0${i+1}",x+22f,y+53f,32f,true,personalityColor)
            wrappedLines(title.title,x+2f,y+390f,28f,310f)
            text(title.time,x+2f,y+458f,24f,false,muted)
        }
        text("YOUR TOP GENRES",56f,1360f,28f,true,muted)
        val visibleGenres = genres.take(3)
        if (visibleGenres.isEmpty()) {
            text("Still discovering",56f,1414f,32f,true,muted)
        } else {
            val genreWidth = (968f-12f*(visibleGenres.size-1))/visibleGenres.size
            visibleGenres.forEachIndexed { i,genre ->
                val x=56f+i*(genreWidth+12f)
                panel(x,1380f,genreWidth,60f,personalityColor)
                text(genre,x+20f,1420f,29f,true,white,genreWidth-40f)
            }
        }
        panel(56f,1460f,968f,260f,personalityColor)
        text("YOUR VIEWING PERSONALITY",86f,1515f,25f,true,personalityColor)
        text(personality.symbol,86f,1600f,62f,true,personalityColor)
        text(if (name.isBlank()) personality.title else "$name — ${personality.title}",170f,1598f,if (name.isBlank()) 48f else 36f,true,white,820f)
        text(personality.description,86f,1674f,28f,false,muted,910f)
        logo(56f,1775f,60f)
        text("Made with AniLumos",132f,1812f,27f,true)
        text("Recorded on this device. Older viewing history may be incomplete.",56f,1870f,24f,false,muted,968f)
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
