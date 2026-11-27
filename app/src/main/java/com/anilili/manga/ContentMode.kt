package com.anilili.manga

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anilili.ui.components.glassCard

enum class ContentMode { ANIME, MANGA }
enum class MangaSection { HOME, BROWSE, LIBRARY, DOWNLOADS }

val LocalContentMode = staticCompositionLocalOf { ContentMode.ANIME }
val LocalSelectContentMode = staticCompositionLocalOf<(ContentMode) -> Unit> { {} }

@Composable
fun ContentModeSwitcher(modifier: Modifier = Modifier) {
    val selected = LocalContentMode.current
    val select = LocalSelectContentMode.current
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), horizontalArrangement = Arrangement.Center) {
        Row(Modifier.width(192.dp).glassCard(CircleShape, tint = MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(3.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            ContentMode.entries.forEach { mode ->
                val active = mode == selected
                val fill by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, label = "mode fill")
                val ink by animateColorAsState(if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "mode ink")
                Box(Modifier.weight(1f).heightIn(min = 34.dp).clip(CircleShape).background(fill)
                    .selectable(selected = active, role = Role.Tab, onClick = { select(mode) }),
                    contentAlignment = Alignment.Center) {
                    Text(if (mode == ContentMode.ANIME) "Anime" else "Manga", color = ink,
                        style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}
