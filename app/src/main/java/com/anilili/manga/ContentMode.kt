package com.anilili.manga
import androidx.compose.runtime.staticCompositionLocalOf
enum class ContentMode { ANIME, MANGA }
enum class MangaSection { HOME, BROWSE, LIBRARY }
val LocalContentMode = staticCompositionLocalOf { ContentMode.ANIME }
