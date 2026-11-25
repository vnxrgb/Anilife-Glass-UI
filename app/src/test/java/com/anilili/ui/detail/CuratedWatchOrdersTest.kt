package com.anilili.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CuratedWatchOrdersTest {
    @Test fun communityMatchesIdsAndPreservesOptionalSteps() {
        val json = """{"data":[{"watch_orders":[{"name":"Chronological","steps":[{"media":{"id":20}},{"media":{"id":10},"is_optional":true}]}]}]}"""
        val order = CuratedWatchOrders.parseCommunity(json, setOf(10)).single()
        assertEquals("Chronological", order.name)
        assertEquals(listOf(CuratedStep(20), CuratedStep(10, true)), order.steps)
        assertTrue(CuratedWatchOrders.parseCommunity(json, setOf(99)).isEmpty())
    }

    @Test fun indexMatchesOneBasedSeriesReference() {
        val index = listOf(
            "!anime-metadata-db-index\t1",
            "!series\tid\tfile\tfranchise\ttitles",
            "first\tfirst.yaml\t1\tFirst",
            "second\tsecond.yaml\t2\tSecond",
            "!works\tkind\tid\tseries\tanilist",
            "TV\twork\t2\t42",
        ).joinToString("\n")
        assertEquals(listOf("second.yaml"), CuratedWatchOrders.metadataFiles(index, setOf(42)))
        assertTrue(CuratedWatchOrders.metadataFiles(index, setOf(1)).isEmpty())
    }

    @Test fun metadataExpandsAuthoredSeriesReferencesWithoutInventingOrder() {
        val yaml = """
            franchise:
              id: test
              series:
                - id: later
                  seasons:
                    - id: later-s1
                      releaseYear: 2000
                      externalIds:
                        anilistId: 10
                - id: earlier
                  seasons:
                    - id: earlier-s1
                      releaseYear: 2010
                      externalIds:
                        anilistId: 20
              watchOrders:
                - name: Chronological
                  entries:
                    - ref: earlier
                    - ref: later
        """.trimIndent()
        assertEquals(listOf(CuratedStep(20), CuratedStep(10)), CuratedWatchOrders.parseMetadata(yaml, setOf(10)).single().steps)
        assertTrue(CuratedWatchOrders.parseMetadata(yaml, setOf(999)).isEmpty())
        assertTrue(CuratedWatchOrders.parseMetadata(yaml.replace("ref: later", "ref: missing"), setOf(10)).isEmpty())
    }
}
