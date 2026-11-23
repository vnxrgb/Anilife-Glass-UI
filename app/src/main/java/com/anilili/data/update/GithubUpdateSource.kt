package com.anilili.data.update

import com.anilili.data.AppGraph
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * AniLumos's own GitHub releases, not the upstream AniLife/Nostr update channel.
 * The downloaded package is checked for its published SHA-256, package identity,
 * version and installed signing certificate by UpdateManager before installation.
 */
internal object GithubUpdateSource {
    private const val RELEASES_API =
        "https://api.github.com/repos/vnxrgb/Anilumos/releases?per_page=30"
    private const val RELEASE_DOWNLOAD_PREFIX =
        "https://github.com/vnxrgb/Anilumos/releases/download/"
    private val SHA256_HEX = Regex("^[a-fA-F0-9]{64}$")

    fun fetchLatest(client: OkHttpClient, json: Json, includeBeta: Boolean): UpdateManager.UpdateInfo {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AniLumos-Android")
            .build()
        val releases = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("AniLumos release check failed (HTTP ${response.code})")
            val body = response.body?.string() ?: error("Empty AniLumos release response")
            json.parseToJsonElement(body).jsonArray
        }
        return selectRelease(releases, includeBeta, AppGraph.isTv)
            ?: error("No compatible AniLumos release APK found")
    }

    /** Pure release selection, so mobile/TV segregation and checksum requirements are tested. */
    internal fun selectRelease(
        releases: JsonArray,
        includeBeta: Boolean,
        isTv: Boolean,
    ): UpdateManager.UpdateInfo? {
        val expectedName = if (isTv) "Anilumos_tv.apk" else "Anilumos.apk"
        return releases.mapNotNull { element ->
            val release = element as? JsonObject ?: return@mapNotNull null
            if (release["draft"]?.jsonPrimitive?.booleanOrNull != false) return@mapNotNull null
            val prerelease = release["prerelease"]?.jsonPrimitive?.booleanOrNull ?: return@mapNotNull null
            if (prerelease && !includeBeta) return@mapNotNull null
            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val version = parseVersion(tag) ?: return@mapNotNull null
            val assets = release["assets"] as? JsonArray ?: return@mapNotNull null
            val apk = assets.firstOrNull {
                (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull == expectedName
            } as? JsonObject ?: return@mapNotNull null
            val url = apk["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!url.startsWith("$RELEASE_DOWNLOAD_PREFIX$tag/") ||
                url != "$RELEASE_DOWNLOAD_PREFIX$tag/$expectedName"
            ) return@mapNotNull null
            val size = apk["size"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            if (size <= 0L) return@mapNotNull null
            val digest = apk["digest"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.startsWith("sha256:") }
                ?.removePrefix("sha256:")
                ?.takeIf(SHA256_HEX::matches)
                ?.lowercase() ?: return@mapNotNull null
            UpdateManager.UpdateInfo(
                version = version,
                changelog = release["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(8_192),
                apkUrl = url,
                sizeBytes = size,
                sha256 = digest,
                formFactor = if (isTv) "tv" else "mobile",
            )
        }.maxWithOrNull { left, right ->
            compareAppVersions(left.version, right.version)
        }
    }
}
