package com.anilili.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anilili.data.settings.GlassMaterial
import com.anilili.data.settings.SettingsStore
import com.anilili.ui.adaptive.LocalAppDeviceProfile
import com.anilili.ui.adaptive.focusFill
import com.anilili.ui.adaptive.rememberFocusFill
import com.anilili.ui.components.glassCard
import com.anilili.ui.components.cinematicSecondaryButtonColors
import com.anilili.ui.components.cinematicSecondaryButtonBorder

@Composable
internal fun GlassMaterialControls() {
    val material by SettingsStore.glassMaterial.collectAsState()
    val opacity by SettingsStore.glassOpacity.collectAsState()
    val blur by SettingsStore.glassBlur.collectAsState()
    val ambient by SettingsStore.glassAmbient.collectAsState()
    val borders by SettingsStore.glassBorders.collectAsState()
    Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(28.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Optical glass", style = MaterialTheme.typography.titleLarge)
        Text("Experimental optical glass is used in floating navigation. Other controls retain readable neutral surfaces.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassMaterial.entries.forEach { option ->
            com.anilili.ui.dialogs.MidnightSettingRow(
                title = option.label,
                description = when (option) {
                    GlassMaterial.LIQUID -> "Optical refraction on supported devices"
                    GlassMaterial.FROSTED -> "Soft blur preference for compatible surfaces"
                    GlassMaterial.SMOKED -> "Dark tint preference for compatible surfaces"
                },
                value = if (option == material) "Selected" else null,
                onClick = { SettingsStore.setGlassMaterial(option) },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
        GlassAdjustment("Glass opacity", if (opacity < 0) (material.panelAlpha * 100).toInt() else opacity, SettingsStore::setGlassOpacity)
        GlassAdjustment("Backdrop blur", if (blur < 0) (material.blurScale * 50).toInt() else blur, SettingsStore::setGlassBlur)
        // Previous artificial interface blooms have been removed. Video ambient lighting is independent.
        com.anilili.ui.dialogs.MidnightSettingSwitchRow(
            title = "Glass borders", checked = borders, onCheckedChange = SettingsStore::setGlassBorders,
        )
        val focus = rememberFocusFill()
        OutlinedButton(onClick = {
            SettingsStore.setGlassMaterial(material)
            SettingsStore.setGlassAmbient(80)
            SettingsStore.setGlassBorders(true)
        }, modifier = Modifier.fillMaxWidth().focusFill(focus, RoundedCornerShape(50)),
            shape = RoundedCornerShape(50), colors = focus.buttonColors(cinematicSecondaryButtonColors()),
            border = cinematicSecondaryButtonBorder()) { Text("Restore material defaults") }
    }
}

@Composable
private fun GlassAdjustment(label: String, value: Int, onChange: (Int) -> Unit) {
    val isTv = LocalAppDeviceProfile.current.isTv
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("$value%", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isTv) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val less = rememberFocusFill()
                val more = rememberFocusFill()
                OutlinedButton(onClick = { onChange((value - 10).coerceAtLeast(0)) },
                    enabled = value > 0, modifier = Modifier.weight(1f).focusFill(less, RoundedCornerShape(50)),
                    shape = RoundedCornerShape(50), colors = less.buttonColors(cinematicSecondaryButtonColors()),
                    border = cinematicSecondaryButtonBorder()) { Text("−") }
                OutlinedButton(onClick = { onChange((value + 10).coerceAtMost(100)) },
                    enabled = value < 100, modifier = Modifier.weight(1f).focusFill(more, RoundedCornerShape(50)),
                    shape = RoundedCornerShape(50), colors = more.buttonColors(cinematicSecondaryButtonColors()),
                    border = cinematicSecondaryButtonBorder()) { Text("+") }
            }
        } else {
            var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
            Slider(value = draft, onValueChange = { draft = it },
                onValueChangeFinished = { onChange(draft.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .15f),
                ))
        }
    }
}
