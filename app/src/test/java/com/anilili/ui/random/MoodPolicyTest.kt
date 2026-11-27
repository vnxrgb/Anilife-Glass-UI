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
}
