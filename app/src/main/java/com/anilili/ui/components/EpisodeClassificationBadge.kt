package com.anilili.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anilili.data.model.EpisodeClassification
import com.anilili.data.model.EpisodeItem

/** Keep the existing filler badge and only display verified additional categories. */
@Composable
fun EpisodeClassificationBadge(episode: EpisodeItem, compact: Boolean = false, modifier: Modifier = Modifier) {
    val category = episode.classification
    if (episode.filler || category == EpisodeClassification.FILLER) {
        EpisodeFillerBadge(compact = compact, modifier = modifier)
    } else if (category != null) {
        PosterTag(text = if (compact && category == EpisodeClassification.ANIME_CANON) "ANIME" else category.label.uppercase(), modifier = modifier)
    }
}
