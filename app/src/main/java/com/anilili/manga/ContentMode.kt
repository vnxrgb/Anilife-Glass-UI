package com.anilili.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anilili.ui.components.glassCard

enum class ContentMode { ANIME, MANGA }
enum class MangaSection { HOME, BROWSE, LIBRARY, DOWNLOADS }

val LocalContentMode = staticCompositionLocalOf { ContentMode.ANIME }
val LocalSelectContentMode = staticCompositionLocalOf<(ContentMode) -> Unit> { {} }

/** Static glass uses the same theme roles as the existing app chrome, without sampling itself. */
@Composable
fun ContentModeSwitcher(modifier: Modifier = Modifier) {
    val selected = LocalContentMode.current
    val select = LocalSelectContentMode.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.widthIn(max = 360.dp).glassCard(RoundedCornerShape(50)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ContentMode.entries.forEach { mode ->
                val label = if (mode == ContentMode.ANIME) "Anime" else "Manga"
                if (mode == selected) {
                    Button(onClick = { select(mode) }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                        Text(label)
                    }
                } else {
                    TextButton(onClick = { select(mode) }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(label) }
                }
            }
        }
    }
}
