package com.anilili.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeClassificationTest {
    private val episode = EpisodeItem(pipeId = "1", number = 1.0, title = null, image = null, filler = true)

    @Test fun mixedEpisodesAreNeverMarkedFiller() {
        val result = episode.withClassification(EpisodeClassification.MIXED)
        assertFalse(result.filler)
        assertEquals(EpisodeClassification.MIXED, result.classification)
    }

    @Test fun unknownPreservesExistingProviderMark() {
        assertTrue(episode.withClassification(null).filler)
    }

    @Test fun recapIsSeparateFromFiller() {
        assertFalse(episode.withClassification(EpisodeClassification.RECAP).filler)
    }
}
