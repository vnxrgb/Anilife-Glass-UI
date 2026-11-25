package com.anilili.ui.random

import com.anilili.data.model.Media

/** Require the selected genre; reject high-ranked contradictory themes when tags are available. */
internal object MoodPolicy {
    fun excludedGenres(genre: String): Set<String> = when (genre) {
        "Romance" -> setOf("Horror", "Thriller")
        "Slice of Life" -> setOf("Action", "Horror", "Thriller", "Psychological", "Ecchi")
        "Comedy" -> setOf("Horror", "Thriller", "Psychological")
        "Drama", "Psychological" -> setOf("Ecchi")
        "Sports" -> setOf("Horror")
        else -> emptySet()
    }

    fun matches(media: Media, genre: String): Boolean {
        if (genre !in media.genres || media.genres.any { it in excludedGenres(genre) }) return false
        if (media.status == "NOT_YET_RELEASED" || media.status == "CANCELLED") return false
        val strongTags = media.tags.filter { !it.isMediaSpoiler && !it.isGeneralSpoiler && (it.rank ?: 0) >= 65 }
            .map { it.name }.toSet()
        val contradictory = when (genre) {
            "Slice of Life" -> setOf("Gore", "Tragedy", "Bullying", "Survival", "Body Horror")
            "Comedy" -> setOf("Gore", "Body Horror")
            "Romance" -> setOf("Gore", "Body Horror")
            else -> emptySet()
        }
        return strongTags.none { it in contradictory }
    }
}
