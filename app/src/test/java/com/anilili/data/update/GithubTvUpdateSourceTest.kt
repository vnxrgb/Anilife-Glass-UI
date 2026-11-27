package com.anilili.data.update

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GithubTvUpdateSourceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefix = "https://github.com/vnxrgb/anilumos-preview/releases/download/tv-alpha-signed-abcdef123456-run2/"
    private fun releases(name: String = "AniLumos-TV-ALPHA-signed.apk") = """[{"tag_name":"tv-alpha-signed-abcdef123456-run2","draft":false,"prerelease":true,"assets":[{"name":"$name","browser_download_url":"${prefix}AniLumos-TV-ALPHA-signed.apk","size":2048},{"name":"TV-UPDATE.json","browser_download_url":"${prefix}TV-UPDATE.json","size":256}]}]"""
    private val payload = """{"formFactor":"tv","packageName":"com.anilumos.app","asset":"AniLumos-TV-ALPHA-signed.apk","version":"1.2.3","build":124,"sha256":"${"a".repeat(64)}"}"""
    @Test fun refusesRedirectedOrInsecureReleaseResponses() {
        val official = "https://api.github.com/repos/vnxrgb/anilumos-preview/releases?per_page=30"
        val metadata = prefix + "TV-UPDATE.json"
        assertTrue(GithubTvUpdateSource.trustedResponseUrl(official, official))
        assertTrue(GithubTvUpdateSource.trustedResponseUrl(metadata, metadata))
        assertTrue(GithubTvUpdateSource.trustedResponseUrl(metadata, "https://release-assets.githubusercontent.com/github-production-release-asset/123?token=abc"))
        assertTrue(GithubTvUpdateSource.trustedResponseUrl(metadata, "https://objects.githubusercontent.com/github-production-release-asset/123?token=abc"))
        assertFalse(GithubTvUpdateSource.trustedResponseUrl(metadata, "https://release-assets.githubusercontent.com/other-object/123"))
        assertFalse(GithubTvUpdateSource.trustedResponseUrl(metadata, "https://release-assets.githubusercontent.com:8443/github-production-release-asset/123"))
        assertFalse(GithubTvUpdateSource.trustedResponseUrl(metadata, "https://user:password@objects.githubusercontent.com/github-production-release-asset/123"))
        assertFalse(GithubTvUpdateSource.trustedResponseUrl(metadata, "https://evil.example.com/asset"))
        assertFalse(GithubTvUpdateSource.trustedResponseUrl(metadata, "http://release-assets.githubusercontent.com/asset"))
        assertFalse(GithubTvUpdateSource.trustedResponseUrl(official, "https://release-assets.githubusercontent.com/asset"))
        assertFalse(GithubTvUpdateSource.trustedResponseUrl(official, "https://example.org/releases"))
        assertFalse(GithubTvUpdateSource.trustedResponseUrl(metadata, "https://example.org/TV-UPDATE.json"))
        assertFalse(GithubTvUpdateSource.trustedResponseUrl("http://api.github.com/repos/vnxrgb/anilumos-preview/releases?per_page=30", official))
        assertFalse(GithubTvUpdateSource.trustedResponseUrl(metadata + "?token=unexpected", metadata + "?token=unexpected"))
    }
    @Test fun selectsTvPreview() {
        val selected = GithubTvUpdateSource.select(releases(), json) { payload }
        assertEquals(124, selected?.build)
        assertEquals("tv", selected?.formFactor)
    }
    @Test fun rejectsMobileAndMissingMetadata() {
        assertNull(GithubTvUpdateSource.select(releases("Anilumos.apk"), json) { payload })
        assertNull(GithubTvUpdateSource.select(releases(), json) { null })
    }
    @Test fun rejectsCrossReleaseAssetUrlsAndUntrustedRedirectShapes() {
        assertNull(GithubTvUpdateSource.select(releases().replace("tv-alpha-signed-abcdef123456-run2/AniLumos-TV", "tv-alpha-signed-other-run2/AniLumos-TV"), json) { payload })
        assertNull(GithubTvUpdateSource.select(releases().replace("TV-UPDATE.json", "TV-UPDATE.json?token=untrusted"), json) { payload })
        assertNull(GithubTvUpdateSource.select(releases().replace("2048", "536870913"), json) { payload })
    }
    @Test fun rejectsMalformedTagAndIncompleteAssets() {
        assertNull(GithubTvUpdateSource.select(releases().replace("tv-alpha-signed-abcdef123456-run2", "tv-alpha-signed-../../other"), json) { payload })
        assertNull(GithubTvUpdateSource.select(releases().replace("tv-alpha-signed-abcdef123456-run2", "tv-alpha-signed-abcdef123456-run2-extra"), json) { payload })
        assertNull(GithubTvUpdateSource.select(releases().replace("\"size\":256", "\"size\":9000"), json) { payload })
        assertNull(GithubTvUpdateSource.select(releases().replace("\"name\":\"TV-UPDATE.json\"", "\"name\":\"TV-UPDATE.json\",\"state\":\"starter\""), json) { payload })
        assertNull(GithubTvUpdateSource.select(releases().replace("\"size\":2048", "\"state\":\"starter\",\"size\":2048"), json) { payload })
    }
    @Test fun rejectsWrongIdentityAndHash() {
        assertNull(GithubTvUpdateSource.select(releases(), json) { payload.replace("com.anilumos.app", "other.app") })
        assertNull(GithubTvUpdateSource.select(releases(), json) { payload.replace("a".repeat(64), "bad") })
    }
    @Test fun selectsHighestBuildRegardlessOfReleaseOrdering() {
        val older = releases().replace("run2", "run3")
        val newer = releases().replace("run2", "run4")
        val combined = older.dropLast(1) + "," + newer.drop(1)
        val selected = GithubTvUpdateSource.select(combined, json) { url ->
            if (url.contains("run4")) payload.replace("\"build\":124", "\"build\":125")
            else payload
        }
        assertEquals(125, selected?.build)
        assertTrue(selected?.url?.contains("run4") == true)
    }

    @Test fun skipsInvalidReleaseWithoutDiscardingValidCandidates() {
        val malformed = releases().replace("run2", "run5")
            .replace("\"size\":256", "\"size\":9000")
        val combined = malformed.dropLast(1) + "," + releases().drop(1)
        val selected = GithubTvUpdateSource.select(combined, json) { payload }
        assertEquals(124, selected?.build)
        assertTrue(selected?.url?.contains("run2") == true)
    }
}
