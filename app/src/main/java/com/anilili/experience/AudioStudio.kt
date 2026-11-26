package com.anilili.experience

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class AudioStudioConfig(
    val enabled: Boolean = false,
    val preset: String = "Balanced",
    val bands: List<Int> = List(5) { 0 },
    val bass: Int = 0,
    val gain: Int = 0,
)

internal object AudioStudioPreferences {
    private var prefs: android.content.SharedPreferences? = null
    private val mutable = MutableStateFlow(AudioStudioConfig())
    val config = mutable.asStateFlow()
    private val mutableStatus = MutableStateFlow("Local playback only")
    val status = mutableStatus.asStateFlow()
    fun status(text: String) { mutableStatus.value = text }
    @Synchronized fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("experience_audio_studio", Context.MODE_PRIVATE)
        prefs = p
        mutable.value = AudioStudioConfig(
            p.getBoolean("enabled", false), p.getString("preset", "Balanced") ?: "Balanced",
            List(5) { p.getInt("band" + it, 0).coerceIn(-6, 6) },
            p.getInt("bass", 0).coerceIn(0, 100), p.getInt("gain", 0).coerceIn(0, 6))
    }
    @Synchronized fun update(next: AudioStudioConfig) {
        mutable.value = next
        prefs?.edit()?.apply {
            putBoolean("enabled", next.enabled); putString("preset", next.preset)
            next.bands.forEachIndexed { i, v -> putInt("band" + i, v) }
            putInt("bass", next.bass); putInt("gain", next.gain)
        }?.apply()
    }
}

internal class AudioStudioSession {
    private var session = 0
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null

    fun apply(id: Int, config: AudioStudioConfig) {
        if (!config.enabled || id <= 0) {
            close()
            AudioStudioPreferences.status(if (config.enabled) "Waiting for local audio" else "Off")
            return
        }
        if (id != session) {
            close()
            session = id
            eq = runCatching { Equalizer(0, id) }.getOrNull()
            bass = runCatching { BassBoost(0, id) }.getOrNull()
            loud = runCatching { LoudnessEnhancer(id) }.getOrNull()
        }
        var applied = 0
        runCatching {
            eq?.let { effect ->
                val count = effect.numberOfBands.toInt()
                val range = effect.bandLevelRange
                if (count > 0) {
                    for (i in 0 until count) {
                        val index = if (count == 1) 2 else (i * 4f / (count - 1)).toInt().coerceIn(0, 4)
                        effect.setBandLevel(i.toShort(), (config.bands[index] * 100).coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                    }
                    effect.enabled = true
                    if (effect.enabled) applied++
                }
            }
        }.onFailure { runCatching { eq?.enabled = false } }
        runCatching {
            bass?.let {
                if (it.strengthSupported) {
                    it.setStrength((config.bass * 10).toShort())
                    it.enabled = config.bass > 0
                    if (it.enabled) applied++
                } else it.enabled = false
            }
        }.onFailure { runCatching { bass?.enabled = false } }
        runCatching {
            loud?.let {
                it.setTargetGain(config.gain * 100)
                it.enabled = config.gain > 0
                if (it.enabled) applied++
            }
        }.onFailure { runCatching { loud?.enabled = false } }
        AudioStudioPreferences.status(if (applied > 0) "Local audio effects active" else "No enabled effects available on this device")
    }
    fun close() {
        runCatching { eq?.release() }; runCatching { bass?.release() }; runCatching { loud?.release() }
        eq = null; bass = null; loud = null; session = 0
    }
}

@Composable
internal fun AudioStudioControls() {
    val context = LocalContext.current
    LaunchedEffect(context) { AudioStudioPreferences.init(context) }
    val cfg by AudioStudioPreferences.config.collectAsState()
    val status by AudioStudioPreferences.status.collectAsState()
    val presets = mapOf("Balanced" to listOf(0,0,0,0,0), "Cinema" to listOf(3,2,0,2,2),
        "Dialogue" to listOf(-2,0,3,3,0), "Bass" to listOf(4,3,0,0,0))
    fun update(next: AudioStudioConfig) = AudioStudioPreferences.update(next)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Enable Audio Studio")
            Switch(checked = cfg.enabled, onCheckedChange = { update(cfg.copy(enabled = it)) })
        }
        Text(status, style = MaterialTheme.typography.labelSmall)
        if (cfg.enabled) {
            presets.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (name, bands) ->
                        FilterChip(selected = cfg.preset == name, onClick = { update(cfg.copy(preset = name, bands = bands)) }, label = { Text(name) })
                    }
                }
            }
            cfg.bands.forEachIndexed { i, v ->
                Text(listOf("Low", "Low-mid", "Mid", "High-mid", "High")[i] + ": " + v + " dB")
                Slider(value = v.toFloat(), onValueChange = { n -> update(cfg.copy(preset = "Custom", bands = cfg.bands.mapIndexed { j, old -> if (i == j) n.toInt() else old })) }, valueRange = -6f..6f, steps = 11)
            }
            Text("Bass: " + cfg.bass + "%")
            Slider(value = cfg.bass.toFloat(), onValueChange = { update(cfg.copy(bass = it.toInt())) }, valueRange = 0f..100f, steps = 9)
            Text("Volume enhancement: +" + cfg.gain + " dB")
            Slider(value = cfg.gain.toFloat(), onValueChange = { update(cfg.copy(gain = it.toInt())) }, valueRange = 0f..6f, steps = 5)
            Text("Effects vary by device. Lower gain if audio distorts.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
