package com.anilili.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Official TV prerelease selection; no mobile or unsigned metadata is accepted. */
internal object GithubTvUpdateSource {
    private const val RELEASES = "https://api.github.com/repos/vnxrgb/anilumos-preview/releases?per_page=30"
    private const val APK = "AniLumos-TV-ALPHA-signed.apk"
    private const val META = "TV-UPDATE.json"
    private val TAG_PATTERN = Regex("tv-alpha-signed-[0-9a-f]{12}-run[1-9][0-9]*")
    private fun validUrl(url: String, tag: String, name: String): Boolean =
        url == "https://github.com/vnxrgb/anilumos-preview/releases/download/$tag/$name"

    fun select(releases: String, json: Json, metadata: (String) -> String?): UpdateManager.UpdateInfo? {
        val candidates = json.parseToJsonElement(releases).jsonArray.mapNotNull { item ->
            runCatching {
                val release = item.jsonObject
                if (release["draft"]?.jsonPrimitive?.content != "false" ||
                    release["prerelease"]?.jsonPrimitive?.content != "true" ||
                    !TAG_PATTERN.matches(release.getValue("tag_name").jsonPrimitive.content)) return@runCatching null
                val tag = release.getValue("tag_name").jsonPrimitive.content
                val assets = release.getValue("assets").jsonArray.map { it.jsonObject }
                if (assets.size > 100) return@runCatching null
                val apk = assets.singleOrNull { it["name"]?.jsonPrimitive?.content == APK } ?: return@runCatching null
                val meta = assets.singleOrNull { it["name"]?.jsonPrimitive?.content == META } ?: return@runCatching null
                val metaUrl = meta.getValue("browser_download_url").jsonPrimitive.content
                if (!validUrl(metaUrl, tag, META) ||
                    meta.getValue("size").jsonPrimitive.content.toLongOrNull()?.let { it in 1..8192L } != true) return@runCatching null
                val payload = metadata(metaUrl)?.takeIf { it.length <= 8192 } ?: return@runCatching null
                val details = json.parseToJsonElement(payload).jsonObject
                if (details["formFactor"]?.jsonPrimitive?.content != "tv" ||
                    details["packageName"]?.jsonPrimitive?.content != "com.anilumos.app" ||
                    details["asset"]?.jsonPrimitive?.content != APK) return@runCatching null
                val version = details.getValue("version").jsonPrimitive.content
                if (parseVersion(version) == null) return@runCatching null
                val build = details.getValue("build").jsonPrimitive.content.toIntOrNull()?.takeIf { it > 0 }
                    ?: return@runCatching null
                val sha = details.getValue("sha256").jsonPrimitive.content
                if (!sha.matches(Regex("[a-f0-9]{64}"))) return@runCatching null
                val url = apk.getValue("browser_download_url").jsonPrimitive.content
                if (!validUrl(url, tag, APK)) return@runCatching null
                if (apk["state"]?.jsonPrimitive?.content?.let { it != "uploaded" } == true) return@runCatching null
                val size = apk.getValue("size").jsonPrimitive.content.toLongOrNull()?.takeIf { it in 1..536870912L }
                    ?: return@runCatching null
                UpdateManager.UpdateInfo(version, "AniLumos TV preview", url, size,
                    build = build, sha256 = sha, formFactor = "tv")
            }.getOrNull()
        }
        return candidates.maxWithOrNull(compareBy<UpdateManager.UpdateInfo> { it.build ?: 0 }.thenBy { it.version })
    }

    fun fetch(client: OkHttpClient, json: Json): UpdateManager.UpdateInfo {
        fun get(url: String): String? = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful || (response.body?.contentLength() ?: 0) > 256000L) return@use null
                response.body?.string()?.takeIf { it.length <= 256000 }
            }
        }.getOrNull()
        val releases = get(RELEASES) ?: error("AniLumos TV preview channel unavailable")
        return select(releases, json) { get(it) }
            ?: error("No compatible TV preview with update metadata published")
    }
}
