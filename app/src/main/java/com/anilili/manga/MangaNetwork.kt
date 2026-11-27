package com.anilili.manga
import android.content.Context
import coil.ImageLoader
import okhttp3.OkHttpClient
import java.io.IOException
/** Local archive preview only; reject network images. */
internal object MangaNetwork {
    @Volatile private var loader: ImageLoader? = null
    @Volatile private var catalogueLoader: ImageLoader? = null
    fun catalogueImageLoader(context: Context): ImageLoader = catalogueLoader ?: synchronized(this) {
        catalogueLoader ?: ImageLoader.Builder(context.applicationContext)
            .okHttpClient(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .addInterceptor { chain ->
                    if (catalogueImage(chain.request().url.toString()) == null) throw IOException("Catalogue image outside permitted CDN")
                    chain.proceed(chain.request())
                }.build())
            .build().also { catalogueLoader = it }
    }
    fun imageLoader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .okHttpClient(OkHttpClient.Builder().addInterceptor { throw IOException("Remote manga images disabled in local preview") }.build())
            .build().also { loader = it }
    }
}
