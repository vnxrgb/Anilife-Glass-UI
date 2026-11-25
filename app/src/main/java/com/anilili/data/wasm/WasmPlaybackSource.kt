package com.anilili.data.wasm

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import org.json.JSONArray
import com.anilili.data.model.SkipTimes

/**
 * A resolved server owns its transport. It cannot be converted to an unrestricted URL-only
 * source, shared download, external player or cast request through this API.
 */
@UnstableApi
internal class WasmPlaybackSource private constructor(
    val providerId: String,
    val serverName: String,
    val quality: String?,
    val skip: SkipTimes?,
    private val item: MediaItem,
    private val factory: DefaultMediaSourceFactory,
) {
    fun mediaSource(): MediaSource = factory.createMediaSource(item)

    companion object {
        const val MEDIA_ID_PREFIX = "wasm-protected:"
        internal fun fromValidated(
            provider: ApprovedWasmProvider, raw: JSONArray, dub: Boolean,
        ): List<WasmPlaybackSource> {
            val validated = WasmResultPolicy.validated(raw.toString(), provider, dub)
            val results = mutableListOf<WasmPlaybackSource>()
            for (i in 0 until validated.length()) {
                val server = validated.getJSONObject(i)
                val headers = server.getJSONArray("headers")
                val properties = buildMap<String, String> {
                    for (h in 0 until headers.length()) {
                        val pair = headers.getJSONArray(h)
                        put(pair.getString(0), pair.getString(1))
                    }
                }
                val transport = WasmMediaTransport.factory(provider, properties)
                val factory = DefaultMediaSourceFactory(transport)
                val subtitles = server.optJSONArray("subtitles") ?: JSONArray()
                val tracks = (0 until subtitles.length()).mapNotNull { s ->
                    val track = subtitles.getJSONObject(s)
                    val url = track.getString("url")
                    val mime = when (android.net.Uri.parse(url).path?.substringAfterLast('.')?.lowercase()) {
                        "vtt" -> MimeTypes.TEXT_VTT
                        "srt" -> MimeTypes.APPLICATION_SUBRIP
                        "ass", "ssa" -> MimeTypes.TEXT_SSA
                        "ttml" -> MimeTypes.APPLICATION_TTML
                        else -> null
                    } ?: return@mapNotNull null
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(url))
                        .setMimeType(mime).setLanguage(track.getString("language"))
                        .setLabel(track.getString("label")).build()
                }
                val intro = server.optJSONArray("skip_intro")
                val outro = server.optJSONArray("skip_outro")
                val skip = if (intro != null || outro != null) SkipTimes(
                    intro?.getDouble(0), intro?.getDouble(1), outro?.getDouble(0), outro?.getDouble(1),
                ) else null
                val streams = server.getJSONArray("streams")
                for (s in 0 until streams.length()) {
                    val stream = streams.getJSONObject(s)
                    val mime = when (stream.getString("format")) {
                        "hls" -> MimeTypes.APPLICATION_M3U8
                        "dash" -> MimeTypes.APPLICATION_MPD
                        "mp4" -> MimeTypes.VIDEO_MP4
                        else -> continue
                    }
                    val item = MediaItem.Builder().setMediaId(MEDIA_ID_PREFIX + provider.id + ":" + java.util.UUID.randomUUID()).setUri(stream.getString("url")).setMimeType(mime)
                        .setSubtitleConfigurations(tracks).build()
                    results.add(WasmPlaybackSource(provider.id, server.getString("name"),
                        if (stream.isNull("quality")) null else stream.optString("quality"), skip, item, factory))
                }
            }
            return results
        }
    }
}

/** Selects reviewed sources in registry order; native fallback stays with the caller. */
@UnstableApi
internal class WasmSourceResolver(context: Context) {
    private val loader = WasmProviderLoader(context)
    suspend fun resolve(language: String, query: WasmEpisodeQuery, disabled: Set<String>): List<WasmPlaybackSource> {
        for (provider in WasmRegistry.select(WasmRegistry.bundled, language)) {
            if (provider.id in disabled) continue
            val sources = runCatching {
                val result = loader.resolve(provider.id, language, query)
                WasmPlaybackSource.fromValidated(provider, result, query.dub)
            }.getOrDefault(emptyList())
            if (sources.isNotEmpty()) return sources
        }
        return emptyList()
    }
}
