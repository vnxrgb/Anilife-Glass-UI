package com.anilili.ui.random
import com.anilili.data.model.Media
import com.anilili.data.model.MediaTag
import org.junit.Assert.*
import org.junit.Test
class MoodPolicyTest {
    @Test fun comfortRejectsStrongContradictoryThemes() {
        assertFalse(MoodPolicy.matches(Media(id = 1, genres = listOf("Slice of Life"), tags = listOf(MediaTag(name = "Gore", rank = 90))), "Slice of Life"))
        assertTrue(MoodPolicy.matches(Media(id = 2, genres = listOf("Slice of Life", "Comedy")), "Slice of Life"))
    }
    @Test fun mindBendingRequiresPsychologicalGenre() {
        assertFalse(MoodPolicy.matches(Media(id = 3, genres = listOf("Sci-Fi")), "Psychological"))
    }
    @Test fun unreleasedAndCancelledTitlesAreExcluded() {
        assertFalse(MoodPolicy.matches(Media(id = 4, genres = listOf("Comedy"), status = "NOT_YET_RELEASED"), "Comedy"))
        assertFalse(MoodPolicy.matches(Media(id = 5, genres = listOf("Comedy"), status = "CANCELLED"), "Comedy"))
    }

    @Test fun contradictoryGenresAreRejectedEvenWhenSelectedGenreMatches() {
        assertFalse(MoodPolicy.matches(Media(id = 6, genres = listOf("Romance", "Horror")), "Romance"))
        assertFalse(MoodPolicy.matches(Media(id = 7, genres = listOf("Comedy", "Thriller")), "Comedy"))
        assertTrue(MoodPolicy.matches(Media(id = 8, genres = listOf("Romance", "Drama")), "Romance"))
    }

    @Test fun onlyStrongNonSpoilerContradictoryTagsExcludeTitles() {
        assertTrue(MoodPolicy.matches(Media(id = 9, genres = listOf("Slice of Life"), tags = listOf(MediaTag(name = "Gore", rank = 64))), "Slice of Life"))
        assertFalse(MoodPolicy.matches(Media(id = 10, genres = listOf("Slice of Life"), tags = listOf(MediaTag(name = "Gore", rank = 65))), "Slice of Life"))
        assertTrue(MoodPolicy.matches(Media(id = 11, genres = listOf("Slice of Life"), tags = listOf(MediaTag(name = "Gore", rank = 95, isMediaSpoiler = true))), "Slice of Life"))
    }
}
