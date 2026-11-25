package com.anilili.data.remote

import com.anilili.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** An explicitly configured, finite test catalog. No inferred episode availability. */
internal class AnitakuConfiguration private constructor(
    val endpoint: HttpUrl,
    private val catalog: Map<Int, EpisodeAvailability>,
) {
    fun availability(id: Int): EpisodeAvailability =
        catalog[id] ?: EpisodeAvailability(emptySet(), emptySet())

    fun containsTitle(id: Int): Boolean = id in catalog

    fun permits(id: Int, audio: String, episode: Int): Boolean {
        val row = availability(id)
        return when (audio) {
            "sub" -> episode in row.sub
            "dub" -> episode in row.dub
            else -> false
        }
    }

    companion object {
        fun parse(endpoint: String, manifest: String): AnitakuConfiguration? {
            val url = endpoint.toHttpUrlOrNull()?.takeIf {
                it.isHttps && it.username.isEmpty() && it.password.isEmpty() &&
                    it.query == null && it.fragment == null
            } ?: return null
            if (manifest.isBlank() || manifest.length > 65536) return null
            return try {
                val root = Json.parseToJsonElement(manifest) as? JsonObject ?: return null
                val rows = mutableMapOf<Int, EpisodeAvailability>()
                for ((key, value) in root) {
                    val id = key.toIntOrNull()?.takeIf { it > 0 && it.toString() == key } ?: return null
                    val row = value as? JsonObject ?: return null
                    if (row.keys.any { it != "sub" && it != "dub" }) return null
                    fun numbers(audio: String): Set<Int>? {
                        val raw = row[audio] ?: return emptySet()
                        val array = raw as? JsonArray ?: return null
                        val result = mutableSetOf<Int>()
                        for (item in array) {
                            val number = (item as? JsonPrimitive)?.takeUnless { it.isString }
                                ?.intOrNull?.takeIf { it > 0 } ?: return null
                            result += number
                        }
                        return result
                    }
                    val sub = numbers("sub") ?: return null
                    val dub = numbers("dub") ?: return null
                    if (sub.isNotEmpty() || dub.isNotEmpty()) rows[id] = EpisodeAvailability(sub, dub)
                }
                if (rows.isEmpty()) null else AnitakuConfiguration(url, rows.toMap())
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

internal object AnitakuIntegration {
    const val PROVIDER = "anitaku"
    val config: AnitakuConfiguration? by lazy {
        AnitakuConfiguration.parse(BuildConfig.ANITAKU_ENDPOINT, BuildConfig.ANITAKU_TEST_CATALOG)
    }
}
