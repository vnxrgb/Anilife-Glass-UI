package com.anilumos.manga.engine
import org.junit.Assert.*
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl
class RemoteDocumentRuntimeTest {
    @Test fun remoteDocumentContainsInertMarkupOnly() {
        val out = RemoteDocumentRuntime.sanitize("""<h1 id="title" onclick="bad()">Manga</h1><img src="https://example.org/a"><script>alert(1)</script><iframe src="https://example.org"></iframe><form><input></form>""")
        assertTrue(out.contains("Manga"))
        assertFalse(out.contains("onclick"))
        assertFalse(out.contains("src="))
        assertFalse(out.contains("<script"))
        assertFalse(out.contains("<iframe"))
        assertFalse(out.contains("<form"))
    }
    @Test fun candidateHostnameDoesNotIncludePathOrSecrets() {
        val scope = SourceScope("FIXTURE", setOf("example.org"))
        val failure = compatibilityFailure(ProbeStage.PAGE_DOWNLOAD, scope.denied("https://cdn.example.net/private?secret=abc".toHttpUrl()))
        assertEquals("cdn.example.net", failure.candidateHost)
        assertFalse(failure.message!!.contains("secret"))
        assertNull(scope.denied("https://user:pass@cdn.example.net/a".toHttpUrl()).candidateHost)
    }
}
