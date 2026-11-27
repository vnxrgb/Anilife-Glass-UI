package com.anilumos.manga.engine
import org.junit.Assert.*
import org.junit.Test

class ReviewedCatalogTest {
    @Test fun metadataPermissionDoesNotGrantChapters() {
        assertTrue(permittedChapters(ReviewedTitleGrant("Metadata", emptySet()), listOf(1f, 2f, 3f)).isEmpty())
        assertEquals(setOf(3f), permittedChapters(ReviewedTitleGrant("Chico", setOf(3f)), listOf(1f, 2f, 3f, 4f)))
    }
    @Test fun approvedProviderRegistryIsNotTheParserInventory() {
        assertEquals(listOf("ATSUMOE"), ReviewedCatalog.providers().map { it.id })
        assertTrue(ReviewedCatalogGrants.all.single().titles.single { it.name == "Lookism" }.chapters.isEmpty())
    }
}
