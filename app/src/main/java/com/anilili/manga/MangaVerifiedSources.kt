package com.anilili.manga

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** A bounded, data-only allowlist. It cannot install or activate an excluded parser. */
internal object MangaVerifiedSources {
    private const val URL = "https://raw.githubusercontent.com/vnxrgb/anilumos-preview/main/manga-source-health/verified.txt"
    private const val ASSET = "manga-verified-sources.txt"
    internal const val REVISION = "8e4b04f3df0c49ac7bc3fea8202c9c7e81a4e626"
    private const val MAX_AGE = 604_800_000L
    private val manifestClient = okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
    private fun validated(text: String, now: Long = System.currentTimeMillis()): Set<String>? {
        if (text.length > 100_000) return null
        val lines = text.trimEnd().lineSequence().take(2001).toList()
        if (lines.size > 2000) return null
        if (lines.firstOrNull() != "parser=$REVISION") return null
        val stamp = lines.getOrNull(1)?.takeIf { it.startsWith("checked=") } ?: return null
        val time = stamp.removePrefix("checked=").toLongOrNull() ?: return null
        if (now - time !in 0..MAX_AGE) return null
        val ids = lines.drop(2)
        if (ids.any { it != "mangadex" && !it.matches(Regex("kotatsu:[a-z0-9_]+")) } || ids.distinct().size != ids.size) return null
        return ids.toSet()
    }
    fun parse(text: String, now: Long = System.currentTimeMillis()): Set<String> = validated(text, now).orEmpty()
    fun cached(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("manga_verified_sources", Context.MODE_PRIVATE)
        return validated(prefs.getString("manifest", "").orEmpty()) ?: run {
            runCatching { context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptySet())
        }
    }
    suspend fun refresh(context: Context): Set<String> = withContext(Dispatchers.IO) {
        try {
            manifestClient.newCall(Request.Builder().url(URL).header("Accept", "text/plain").build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext cached(context)
                val body = response.body
                if (body.contentLength() > 100_000) return@withContext cached(context)
                val source = body.source()
                if (source.request(100_001L)) return@withContext cached(context)
                val text = source.readUtf8()
                if (text.length > 100_000) return@withContext cached(context)
                val ids = validated(text) ?: return@withContext cached(context)
                context.getSharedPreferences("manga_verified_sources", Context.MODE_PRIVATE).edit().putString("manifest", text).apply()
                ids
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { cached(context) }
    }
}
