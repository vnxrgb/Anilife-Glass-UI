package com.anilili.data.remote

import com.anilili.data.model.EpisodeClassification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class TenraiParsingTest {
    @Test fun explicitFlagsOnly() {
        assertEquals(EpisodeClassification.FILLER, tenraiEpisodeKind(Json.parseToJsonElement("""{"mal_id":101,"filler":true,"recap":false}""").jsonObject))
        assertEquals(EpisodeClassification.RECAP, tenraiEpisodeKind(Json.parseToJsonElement("""{"filler":true,"recap":true}""").jsonObject))
        assertNull(tenraiEpisodeKind(Json.parseToJsonElement("""{"filler":false,"recap":false}""").jsonObject))
        assertNull(tenraiEpisodeKind(Json.parseToJsonElement("""{}""").jsonObject))
    }
    @Test fun recommendationsRankByVotesAndExcludeSeed() {
        val root = Json.parseToJsonElement("""{"data":[{"entry":{"mal_id":20},"votes":999},{"entry":{"mal_id":21},"votes":3},{"entry":{"mal_id":269},"votes":12},{"entry":{"mal_id":269},"votes":2}]}""").jsonObject
        assertEquals(listOf(269,21), tenraiRecommendationIds(root,20))
    }
    @Test fun emptyRecommendationsAreValid() {
        assertTrue(tenraiRecommendationIds(Json.parseToJsonElement("""{"data":[]}""").jsonObject,20).isEmpty())
    }
}
