package com.anilili.manga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class MangaDiscoveryTest {
    @Test fun paginationUsesRawPageAndRejectsAdultOrUnknown() {
        val page = Json.parseToJsonElement("""{"pageInfo":{"hasNextPage":true},"media":[
            {"id":1,"isAdult":false,"title":{"userPreferred":"Example"},"countryOfOrigin":"KR","chapters":12,
             "externalLinks":[{"site":"Official","type":"STREAMING","url":"https://www.webtoons.com/en/example"},{"site":"Other","type":"STREAMING","url":"https://unreviewed.example/read"}]},
            {"id":2,"isAdult":true,"title":{"userPreferred":"Excluded"}},
            {"id":3,"title":{"userPreferred":"Unknown"}}
        ]}""").jsonObject
        val result = decodeDiscoveryPage(page, 24, 24)
        assertEquals(48, result.nextOffset)
        assertEquals(1, result.items.size)
        assertEquals(MangaKind.MANHWA, result.items.single().kind)
        assertEquals(12, result.items.single().knownChapterCount)
        assertEquals(1, result.items.single().officialLinks.size)
    }
    @Test fun scopedImageAndPublisherLinks() {
        assertNotNull(catalogueImage("https://s4.anilist.co/file/cover.jpg"))
        assertNull(catalogueImage("https://s4.anilist.co.example/cover.jpg"))
        assertNull(officialCatalogueLink("http://www.viz.com/read","Bad"))
        assertNull(officialCatalogueLink("https://user@www.viz.com/read","Bad"))
        assertEquals("Heading", cleanMangaDescription("**Heading**"))
    }
}
