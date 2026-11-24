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
import com.anilili.ui.components.glassCard

/** Discrete focusable buttons work with touch, keyboard and TV D-pad. */
@Composable
internal fun GlassMaterialControls() {
    val material by SettingsStore.glassMaterial.collectAsState()
    val opacity by SettingsStore.glassOpacity.collectAsState()
    val blur by SettingsStore.glassBlur.collectAsState()
    val ambient by SettingsStore.glassAmbient.collectAsState()
    val borders by SettingsStore.glassBorders.collectAsState()
    Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Glass appearance", style = MaterialTheme.typography.titleMedium)
        Text("Independent of your colour theme", style = MaterialTheme.typography.bodySmall)
        GlassMaterial.entries.forEach { option ->
            OutlinedButton(onClick = { SettingsStore.setGlassMaterial(option) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (option == material) "✓  ${option.label}" else option.label)
            }
        }
        GlassAdjustment("Glass opacity", if (opacity < 0) (material.panelAlpha * 100).toInt() else opacity, SettingsStore::setGlassOpacity)
        if (!com.anilili.ui.adaptive.LocalAppDeviceProfile.current.isTv) {
            GlassAdjustment("Backdrop blur", if (blur < 0) (material.blurScale * 50).toInt() else blur, SettingsStore::setGlassBlur)
        } else Text("TV uses lightweight glass materials for smooth remote navigation.", style = MaterialTheme.typography.bodySmall)
        GlassAdjustment("Ambient colour", ambient, SettingsStore::setGlassAmbient)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Glass borders", Modifier.weight(1f))
            Switch(checked = borders, onCheckedChange = SettingsStore::setGlassBorders)
        }
        TextButton(onClick = {
            SettingsStore.setGlassMaterial(material)
            SettingsStore.setGlassAmbient(80)
            SettingsStore.setGlassBorders(true)
        }) { Text("Restore material defaults") }
    }
}

@Composable
private fun GlassAdjustment(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label · $value%", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onChange((value - 10).coerceAtLeast(0)) }, enabled = value > 0,
            contentPadding = PaddingValues(horizontal = 12.dp)) { Text("−") }
        OutlinedButton(onClick = { onChange((value + 10).coerceAtMost(100)) }, enabled = value < 100,
            contentPadding = PaddingValues(horizontal = 12.dp)) { Text("+") }
    }
}
