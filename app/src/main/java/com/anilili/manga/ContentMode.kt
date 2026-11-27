package com.anilili.manga

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.anilili.ui.components.AnililiWordmark
import com.anilili.ui.components.glassRimColor
import com.anilili.ui.components.glassSelectionColor
import com.anilili.ui.dialogs.popupGlass
import com.anilili.ui.dialogs.rememberPopupBackdrop

enum class ContentMode { ANIME, MANGA }
enum class MangaSection { HOME, BROWSE, LIBRARY }

val LocalContentMode = staticCompositionLocalOf { ContentMode.ANIME }
val LocalSelectContentMode = staticCompositionLocalOf<(ContentMode) -> Unit> { {} }
val LocalContentModeSwitchEnabled = staticCompositionLocalOf { false }

@Composable
fun ContentModeWordmark(textColor: Color, modifier: Modifier = Modifier) {
    if (!LocalContentModeSwitchEnabled.current) {
        AnililiWordmark(modifier.height(30.dp), textColor = textColor)
        return
    }
    val selected = LocalContentMode.current
    val select = LocalSelectContentMode.current
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Box(modifier) {
        Row(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClickLabel = "Switch between Anime and Manga") {
                    expanded = true
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            AnililiWordmark(Modifier.height(30.dp), textColor = textColor)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null,
                tint = textColor.copy(alpha = .78f), modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 4.dp),
            shape = shape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            val backdrop = rememberPopupBackdrop()
            Column(Modifier.width(184.dp).clip(shape).popupGlass(backdrop)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (backdrop == null) 1f else .84f))
                .border(.5.dp, glassRimColor(), shape).padding(5.dp)) {
                ContentMode.entries.forEach { mode ->
                    val active = mode == selected
                    val label = if (mode == ContentMode.ANIME) "Anime" else "Manga"
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = { Icon(if (mode == ContentMode.ANIME) Icons.Default.PlayArrow else Icons.Default.MenuBook, null) },
                        trailingIcon = { if (active) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (active) glassSelectionColor() else Color.Transparent),
                        onClick = {
                            expanded = false
                            if (!active) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                select(mode)
                            }
                        },
                    )
                }
            }
        }
    }
}
