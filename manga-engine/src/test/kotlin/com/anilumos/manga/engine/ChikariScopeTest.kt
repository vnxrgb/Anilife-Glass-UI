package com.anilumos.manga.engine

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ChikariScopeTest {
    @Test fun onlyObservedExactHostIsAddedToChikari() {
        val scope = ProviderCdnRegistry.scope("CHIKARI", setOf("chikari.moe"))
        val origin = "https://chikari.moe/".toHttpUrl()
        val image = "https://cdn.irakich.st/fixture.png".toHttpUrl()
        assertTrue(scope.permits(image))
        assertTrue(scope.permitsImageRedirect(origin, image))
        assertFalse(scope.permits("https://other.irakich.st/fixture.png".toHttpUrl()))
        assertFalse(scope.permits("https://cdn.irakich.st.example.org/fixture.png".toHttpUrl()))
        assertFalse(scope.permits("http://cdn.irakich.st/fixture.png".toHttpUrl()))
        assertFalse(ProviderCdnRegistry.scope("FIXTURE", setOf("example.org")).permits(image))
    }
}
