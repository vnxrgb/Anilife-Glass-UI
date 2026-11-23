package com.anilili.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubUpdateSourceTest {
    private fun releases(vararg entries: String) =
        Json.parseToJsonElement(entries.joinToString(prefix = "[", postfix = "]")).jsonArray

    private fun release(
        version: String,
        asset: String = "Anilumos.apk",
        digest: String? = "sha256:" + "a".repeat(64),
        size: Long = 19_000_000L,
        prerelease: Boolean = false,
        draft: Boolean = false,
        owner: String = "vnxrgb/Anilumos",
    ): String = """
        {
          "tag_name":"v$version", "body":"Update from the app",
          "draft":$draft, "prerelease":$prerelease,
          "assets":[{
            "name":"$asset",
            "size":$size,
            "browser_download_url":"https://github.com/$owner/releases/download/v$version/$asset"
            ${if (digest == null) "" else ", \"digest\":\"$digest\""}
          }]
        }
    """.trimIndent()

    @Test fun `mobile sees newest stable AniLumos release`() {
        val info = GithubUpdateSource.selectRelease(
            releases(release("1.0.1"), release("1.0.2")),
            includeBeta = false, isTv = false,
        )
        assertEquals("1.0.2", info?.version)
        assertEquals("https://github.com/vnxrgb/Anilumos/releases/download/v1.0.2/Anilumos.apk", info?.apkUrl)
        assertEquals("a".repeat(64), info?.sha256)
        assertEquals("Update from the app", info?.changelog)
    }

    @Test fun `upstream or different repo cannot supply an update`() {
        assertNull(GithubUpdateSource.selectRelease(
            releases(release("1.0.9", owner = "anilifecommunity/android-and-tv")),
            includeBeta = false, isTv = false,
        ))
    }

    @Test fun `missing digest is rejected before download`() {
        assertNull(GithubUpdateSource.selectRelease(
            releases(release("1.0.3", digest = null)),
            includeBeta = false, isTv = false,
        ))
    }

    @Test fun `mobile ignores tv apk and tv ignores mobile apk`() {
        val tv = releases(release("1.0.2", asset = "Anilumos_tv.apk"))
        assertNull(GithubUpdateSource.selectRelease(tv, includeBeta = false, isTv = false))
        assertEquals("tv", GithubUpdateSource.selectRelease(tv, includeBeta = false, isTv = true)?.formFactor)
        assertNull(GithubUpdateSource.selectRelease(
            releases(release("1.0.2")), includeBeta = false, isTv = true,
        ))
    }

    @Test fun `draft and prerelease never become stable update offers`() {
        val versions = releases(
            release("1.0.2"),
            release("1.0.3", prerelease = true),
            release("1.0.4", draft = true),
        )
        assertEquals("1.0.2", GithubUpdateSource.selectRelease(
            versions, includeBeta = false, isTv = false,
        )?.version)
        assertEquals("1.0.3", GithubUpdateSource.selectRelease(
            versions, includeBeta = true, isTv = false,
        )?.version)
    }
}
