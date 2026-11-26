package com.anilili.manga

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.anilili.data.settings.ThemeBackground
import com.anilili.ui.theme.ThemeSpec
import com.anilili.ui.theme.appColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaThemeTest {
    @Test fun mangaCanvasUsesReadableSharedThemeRoles() {
        for (background in listOf(ThemeBackground.LIGHT, ThemeBackground.DARK, ThemeBackground.AMOLED)) {
            val scheme = appColors(ThemeSpec(background = background), wallpaperSeed = null).scheme
            val a = scheme.background.luminance()
            val b = scheme.onBackground.luminance()
            val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
            assertTrue("$background canvas text contrast = $ratio", ratio >= 4.5f)
            if (background == ThemeBackground.AMOLED) assertEquals(Color.Black, scheme.background)
        }
    }
}
