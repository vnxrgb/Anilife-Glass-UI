package com.anilili.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class MetadataEpisodeMappingTest {
    private val ref = ExternalEpisodeRef("tt0409591", 123, 3, 18, "2004-09-08", setOf("Gotta See! Gotta Know!"))

    @Test fun imdbCoordinateMayDifferFromTvdb() {
        val root = Json.parseToJsonElement("""{"meta":{"id":"tt0409591","videos":[{"id":"tt0409591:4:101","season":4,"episode":101,"released":"2004-09-08T00:00:00Z","title":"Gotta See! Gotta Know!"}]}}""").jsonObject
        assertEquals(ImdbEpisodeRef("tt0409591", 4, 101), matchImdbEpisode(root, ref))
        assertNull(matchImdbEpisode(root, ref.copy(airDate = "2004-09-09")))
    }

    @Test fun rejectsAmbiguousEpisodeIdentity() {
        val root = Json.parseToJsonElement("""{"meta":{"id":"tt0409591","videos":[{"id":"tt0409591:4:101","season":4,"episode":101,"released":"2004-09-08","title":"Gotta See! Gotta Know!"},{"id":"tt0409591:3:18","season":3,"episode":18,"released":"2004-09-08","title":"Gotta See! Gotta Know!"}]}}""").jsonObject
        assertNull(matchImdbEpisode(root, ref))
    }
}
