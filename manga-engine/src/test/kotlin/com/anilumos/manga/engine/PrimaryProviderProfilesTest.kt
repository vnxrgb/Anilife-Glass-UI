package com.anilumos.manga.engine

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.junit.Assert.*
import org.junit.Test

class PrimaryProviderProfilesTest {
    @Test fun allFiveProfilesExistInPinnedInventory() {
        val descriptors = ParserRegistry().descriptors().associateBy { it.id }
        assertEquals(5, PrimaryProviderProfiles.profiles.size)
        for (profile in PrimaryProviderProfiles.profiles) {
            assertNotNull(descriptors[profile.scope.sourceName])
        }
    }
    @Test fun unratedPolicyIsGlobalAndDoesNotRelabel() {
        assertTrue(PrimaryProviderProfiles.permitsRating(null))
        assertTrue(PrimaryProviderProfiles.permitsRating(ContentRating.SAFE))
        assertFalse(PrimaryProviderProfiles.permitsRating(ContentRating.ADULT))
        assertFalse(PrimaryProviderProfiles.permitsRating(ContentRating.SUGGESTIVE))
    }
    @Test fun safeFiltersAndScopedHosts() {
        assertEquals(setOf(ContentRating.SAFE), PrimaryProviderProfiles.restrictFilter("MANGADEX", MangaListFilter()).contentRating)
        assertEquals(setOf(ContentRating.SAFE), PrimaryProviderProfiles.restrictFilter("WEEBCENTRAL", MangaListFilter()).contentRating)
        assertTrue(PrimaryProviderProfiles.restrictFilter("MANGAPILL", MangaListFilter()).contentRating.isEmpty())
        val scope = PrimaryProviderProfiles.profiles.single { it.scope.sourceName == "WEEBCENTRAL" }.scope
        assertTrue(scope.permits("https://weebcentral.com/".toHttpUrl()))
        assertFalse(scope.permits("https://other.example.org/".toHttpUrl()))
    }
}
