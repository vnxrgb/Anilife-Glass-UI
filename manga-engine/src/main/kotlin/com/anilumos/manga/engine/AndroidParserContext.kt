package com.anilumos.manga.engine

import android.util.Base64
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.Locale

/** Explicit limitations instead of returning null and misreporting a source as broken.
 * Browser-dependent and protected-image paths require separately reviewed, authorized support. */
internal class AndroidParserContext(
    override val parsers: ParsersBundle,
    private val session: SourceSession,
    private val sourceConfig: ReviewedSourceConfig = ReviewedSourceConfig(),
) : MangaLoaderContext() {
    override val cookieJar get() = session.cookies
    override val httpClient get() = session.client
    override fun getDefaultUserAgent() = "AniLumos-Manga-Research/0.1"
    override fun getPreferredLocales() = listOf(Locale.getDefault(), Locale.ENGLISH).distinct()
    override fun encodeBase64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)
    override fun decodeBase64(data: String): ByteArray = Base64.decode(data, Base64.DEFAULT)
    override fun getConfig(source: MangaSource): MangaSourceConfig = sourceConfig
    @Deprecated("Provide base URL")
    override suspend fun evaluateJs(script: String): String? =
        session.evaluateLocalScript(script, 5_000L)
    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? {
        session.requireBrowserOrigin(baseUrl)
        return session.evaluateLocalScript(script, timeout)
    }
    override fun redrawImageResponse(response: Response, redraw: (Bitmap) -> Bitmap): Response =
        throw ProviderFeatureException(UnsupportedFeature.PARSER_IMAGE_TRANSFORM)
    override fun createBitmap(width: Int, height: Int): Bitmap =
        throw ProviderFeatureException(UnsupportedFeature.PARSER_IMAGE_TRANSFORM)

}
