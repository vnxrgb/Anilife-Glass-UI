package com.anilili.ui.journal

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.anilili.data.journal.JournalEntry
import com.anilili.data.journal.journalEpisodeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Private text enters this renderer only after explicit share-preview selection. */
internal suspend fun renderJournalImage(context: Context, entries: List<JournalEntry>, accent: Int, title: String): File {
    require(entries.isNotEmpty())
    return withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "journal").apply { mkdirs() }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val posters = entries.map { entry ->
            try {
                entry.artwork?.let { url ->
                    val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).size(320, 460).allowHardware(false).build())
                    (result as? SuccessResult)?.drawable?.toBitmap()
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 33f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val headline = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 42f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        fun layout(text: String, paint: TextPaint, width: Int, lines: Int) = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setMaxLines(lines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).setLineSpacing(6f, 1f).build()
        val details = entries.map { entry -> buildString {
            if (entry.episode.isNotBlank()) appendLine("Episode ${journalEpisodeLabel(entry.episode)}")
            if (entry.rating > 0) appendLine("Personal rating  •  ${entry.rating}/10")
            if (entry.notes.isNotBlank()) appendLine("\nMY THOUGHTS\n${entry.notes}")
            if (entry.review.isNotBlank()) appendLine("\nREVIEW\n${entry.review}")
            if (entry.favoriteMoment.isNotBlank()) appendLine("\nFAVORITE MOMENTS\n${entry.favoriteMoment}")
        }.trim() }
        val titles = entries.mapIndexed { i, e -> layout(e.animeTitle.ifBlank { "Personal journal" }, headline, if (posters[i] == null) 900 else 640, 4) }
        val bodies = details.map { layout(it, body, 900, 18) }
        val heights = entries.indices.map { i -> maxOf(240, if (posters[i] != null) 300 else 0, titles[i].height + 75) + bodies[i].height + 60 }
        val titlePaint = TextPaint(headline).apply { textSize = 64f }
        val header = layout(title, titlePaint, 968, 4)
        val headerHeight = 140 + header.height + 35
        val height = headerHeight + heights.sum() + 26 * (entries.size - 1) + 135
        val bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        try {
            canvas.drawColor(Color.rgb(14, 12, 23))
            val glow = (accent and 0xFFFFFF) or (100 shl 24)
            paint.shader = RadialGradient(920f, 180f, 1050f, intArrayOf(glow, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,1080f,height.toFloat(),paint); paint.shader = null
            fun label(s: String,x: Float,y: Float,size: Float,color: Int=Color.WHITE) {
                paint.color=color; paint.textSize=size; paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
                canvas.drawText(s,x,y,paint)
            }
            fun drawText(layout: StaticLayout, x: Float, y: Float) { canvas.save(); canvas.translate(x,y); layout.draw(canvas); canvas.restore() }
            label("ANILUMOS",60f,90f,28f,Color.rgb(207,184,255))
            drawText(header,56f,120f)
            var y=headerHeight.toFloat()
            entries.forEachIndexed { i, _ ->
                val rect=RectF(56f,y,1024f,y+heights[i])
                paint.color=0x24FFFFFF; canvas.drawRoundRect(rect,32f,32f,paint)
                paint.style=Paint.Style.STROKE; paint.strokeWidth=2f; paint.color=0x35FFFFFF; canvas.drawRoundRect(rect,32f,32f,paint); paint.style=Paint.Style.FILL
                val poster=posters[i]
                if (poster != null) {
                    val dest=RectF(86f,y+30f,286f,y+290f)
                    val scale=maxOf(dest.width()/poster.width,dest.height()/poster.height)
                    val sw=(dest.width()/scale).toInt().coerceIn(1,poster.width); val sh=(dest.height()/scale).toInt().coerceIn(1,poster.height)
                    val sx=(poster.width-sw)/2; val sy=(poster.height-sh)/2
                    canvas.save(); canvas.clipPath(Path().apply { addRoundRect(dest,20f,20f,Path.Direction.CW) })
                    paint.color=Color.WHITE; canvas.drawBitmap(poster,Rect(sx,sy,sx+sw,sy+sh),dest,paint); canvas.restore()
                }
                drawText(titles[i],if(poster==null) 86f else 316f,y+36f)
                drawText(bodies[i],86f,y+maxOf(240,if(poster!=null) 300 else 0,titles[i].height+75))
                y+=heights[i]+26
            }
            label("My anime. My memories.",60f,height-66f,30f,Color.rgb(205,190,220))
            File(directory,"AniLumos-Journal-${System.currentTimeMillis()}.png").also { out -> out.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } }
        } finally { bitmap.recycle() }
    }
}

internal suspend fun shareJournalImage(context: Context, entries: List<JournalEntry>, accent: Int, title: String) {
    val file = renderJournalImage(context, entries, accent, title)
    val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type="image/png"; putExtra(Intent.EXTRA_STREAM,uri); clipData=ClipData.newUri(context.contentResolver,"AniLumos Journal",uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    },"Share Anime Journal"))
}
