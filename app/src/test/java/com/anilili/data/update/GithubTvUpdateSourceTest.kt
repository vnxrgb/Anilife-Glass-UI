package com.anilili.data.update

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GithubTvUpdateSourceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefix = "https://github.com/vnxrgb/anilumos-preview/releases/download/tv-alpha-signed-abc-run2/"
    private fun releases(name: String = "AniLumos-TV-ALPHA-signed.apk") = """[{"tag_name":"tv-alpha-signed-abc-run2","draft":false,"prerelease":true,"assets":[{"name":"$name","browser_download_url":"${prefix}AniLumos-TV-ALPHA-signed.apk","size":2048},{"name":"TV-UPDATE.json","browser_download_url":"${prefix}TV-UPDATE.json","size":256}]}]"""
    private val payload = """{"formFactor":"tv","packageName":"com.anilumos.app","asset":"AniLumos-TV-ALPHA-signed.apk","version":"1.2.3","build":124,"sha256":"${"a".repeat(64)}"}"""
    @Test fun selectsTvPreview() {
        val selected = GithubTvUpdateSource.select(releases(), json) { payload }
        assertEquals(124, selected?.build)
        assertEquals("tv", selected?.formFactor)
    }
    @Test fun rejectsMobileAndMissingMetadata() {
        assertNull(GithubTvUpdateSource.select(releases("Anilumos.apk"), json) { payload })
        assertNull(GithubTvUpdateSource.select(releases(), json) { null })
    }
    @Test fun rejectsWrongIdentityAndHash() {
        assertNull(GithubTvUpdateSource.select(releases(), json) { payload.replace("com.anilumos.app", "other.app") })
        assertNull(GithubTvUpdateSource.select(releases(), json) { payload.replace("a".repeat(64), "bad") })
    }
}
