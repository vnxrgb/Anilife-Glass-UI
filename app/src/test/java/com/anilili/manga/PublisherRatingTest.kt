package com.anilili.manga

import org.junit.Assert.*
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.ContentRating

class PublisherRatingTest {
    @Test fun publicPublisherMissingMetadataIsDistinctFromExplicitAdult() {
        assertTrue(publisherRatingAllowed("MANGAPLUSPARSER_EN", "mangaplus.shueisha.co.jp", null))
        assertTrue(publisherRatingAllowed("WEBTOONS_EN", "webtoons.com", null))
        assertFalse(publisherRatingAllowed("WEBTOONS_EN", "webtoons.com", ContentRating.ADULT))
        assertFalse(publisherRatingAllowed("WEBTOONS_EN", "webtoons.com", ContentRating.SUGGESTIVE))
    }
    @Test fun unknownThirdPartySourcesStayFiltered() {
        assertFalse(publisherRatingAllowed("UNREVIEWED", "example.org", null))
        assertFalse(publisherRatingAllowed("WEBTOONS_EN", "example.org", null))
        assertTrue(publisherRatingAllowed("UNREVIEWED", "example.org", ContentRating.SAFE))
    }
}
