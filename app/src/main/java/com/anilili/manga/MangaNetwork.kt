package com.anilili.manga
import android.content.Context
import coil.ImageLoader
import okhttp3.OkHttpClient
import java.io.IOException
/** Local archive preview only; reject network images. */
internal object MangaNetwork {
    @Volatile private var loader: ImageLoader? = null
    fun imageLoader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .okHttpClient(OkHttpClient.Builder().addInterceptor { throw IOException("Remote manga images disabled in local preview") }.build())
            .build().also { loader = it }
    }
}
