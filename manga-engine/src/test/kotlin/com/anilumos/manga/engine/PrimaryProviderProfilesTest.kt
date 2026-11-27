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
    @Test fun unratedPolicyIsSpecificAndDoesNotRelabel() {
        assertTrue(PrimaryProviderProfiles.permitsRating("MANGAPILL", null))
        assertTrue(PrimaryProviderProfiles.permitsRating("MANGAKATANA", null))
        assertFalse(PrimaryProviderProfiles.permitsRating("ATSUMOE", null))
        assertFalse(PrimaryProviderProfiles.permitsRating("MANGAPILL", ContentRating.ADULT))
        assertFalse(PrimaryProviderProfiles.permitsRating("MANGAKATANA", ContentRating.SUGGESTIVE))
        assertTrue(PrimaryProviderProfiles.permitsRating("MANGAKATANA", ContentRating.SAFE))
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
