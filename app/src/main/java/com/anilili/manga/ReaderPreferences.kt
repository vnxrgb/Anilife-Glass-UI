package com.anilili.manga

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale

internal enum class ReaderScale(val label: String, val scale: ContentScale) {
    FIT("Fit screen", ContentScale.Fit), WIDTH("Fit width", ContentScale.FillWidth),
    HEIGHT("Fit height", ContentScale.FillHeight), ORIGINAL("Original size", ContentScale.None),
    STRETCH("Stretch", ContentScale.FillBounds)
}
internal enum class ReaderRotation(val label: String, val orientation: Int?) {
    SYSTEM("System", null), FREE("Free", ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR),
    PORTRAIT("Portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LANDSCAPE("Landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
}
internal enum class ReaderLayout(val label: String) { SINGLE("Single"), DOUBLE("Double"), AUTO("Automatic") }
internal enum class ReaderTapZones(val label: String) { SIDES("Left / right"), TOP_BOTTOM("Top / bottom"), OFF("Controls only") }
internal enum class ReaderSettingsTab(val label: String) { GENERAL("General"), PAGES("Pages"), FILTERS("Filters"), TOOLBAR("Toolbar") }

internal object ReaderVolumeKeys {
    var turn: ((Int) -> Unit)? = null
}

internal fun readerPageGroups(count: Int, double: Boolean, shift: Boolean): List<List<Int>> {
    if (count <= 0) return emptyList()
    val indices = (0 until count).toList()
    if (!double) return indices.map { listOf(it) }
    return if (shift) listOf(listOf(0)) + indices.drop(1).chunked(2) else indices.chunked(2)
}

internal fun readerTapDirection(x: Float, y: Float, zones: ReaderTapZones, rtl: Boolean, inverted: Boolean): Int {
    val raw = when (zones) {
        ReaderTapZones.OFF -> 0
        ReaderTapZones.SIDES -> when { x < .25f -> if (rtl) 1 else -1; x > .75f -> if (rtl) -1 else 1; else -> 0 }
        ReaderTapZones.TOP_BOTTOM -> when { y < .25f -> -1; y > .75f -> 1; else -> 0 }
    }
    return if (inverted) -raw else raw
}

internal class ReaderPreferences(private val prefs: SharedPreferences) {
    private inline fun <reified T : Enum<T>> enum(key: String, default: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, default.name).orEmpty()) }.getOrDefault(default)
    var scale by mutableStateOf(enum("scale", ReaderScale.FIT))
    var rotation by mutableStateOf(enum("rotation", ReaderRotation.SYSTEM))
    var layout by mutableStateOf(enum("layout", ReaderLayout.AUTO))
    var zones by mutableStateOf(enum("tap_zones", ReaderTapZones.OFF))
    var invertZones by mutableStateOf(prefs.getBoolean("invert_zones", false))
    var animate by mutableStateOf(prefs.getBoolean("animate_pages", true))
    var pageNumber by mutableStateOf(prefs.getBoolean("page_number", true))
    var volume by mutableStateOf(prefs.getBoolean("volume_keys", false))
    var shift by mutableStateOf(prefs.getBoolean("shift_spread", false))
    var reverseSpread by mutableStateOf(prefs.getBoolean("reverse_spread", false))
    var grayscale by mutableStateOf(prefs.getBoolean("grayscale", false))
    var invert by mutableStateOf(prefs.getBoolean("invert_colors", false))
    var warm by mutableFloatStateOf(prefs.getFloat("warm", 0f).coerceIn(0f, 1f))
    var padding by mutableFloatStateOf(prefs.getFloat("side_padding", 0f).coerceIn(0f, 32f))
    var gap by mutableFloatStateOf(prefs.getFloat("page_gap", 0f).coerceIn(0f, 32f))
    var autoHide by mutableStateOf(prefs.getBoolean("auto_hide", false))
    var chapterShortcut by mutableStateOf(prefs.getBoolean("shortcut_chapters", true))
    var ambientShortcut by mutableStateOf(prefs.getBoolean("shortcut_ambient", true))
    var magnifierShortcut by mutableStateOf(prefs.getBoolean("shortcut_magnifier", true))
    var bookmarkShortcut by mutableStateOf(prefs.getBoolean("shortcut_bookmark", false))
    fun save() {
        prefs.edit().putString("scale", scale.name).putString("rotation", rotation.name)
            .putString("layout", layout.name).putString("tap_zones", zones.name)
            .putBoolean("invert_zones", invertZones).putBoolean("animate_pages", animate)
            .putBoolean("page_number", pageNumber).putBoolean("volume_keys", volume)
            .putBoolean("shift_spread", shift).putBoolean("reverse_spread", reverseSpread)
            .putBoolean("grayscale", grayscale).putBoolean("invert_colors", invert)
            .putFloat("warm", warm).putFloat("side_padding", padding).putFloat("page_gap", gap)
            .putBoolean("auto_hide", autoHide).putBoolean("shortcut_chapters", chapterShortcut)
            .putBoolean("shortcut_ambient", ambientShortcut).putBoolean("shortcut_magnifier", magnifierShortcut)
            .putBoolean("shortcut_bookmark", bookmarkShortcut).apply()
    }
    fun matrix(): ColorMatrix {
        val matrix = ColorMatrix()
        if (grayscale) matrix.setToSaturation(0f)
        if (warm > 0f) matrix.timesAssign(ColorMatrix(floatArrayOf(
            1f,0f,0f,0f,0f, 0f,1f-warm*.12f,0f,0f,0f, 0f,0f,1f-warm*.36f,0f,0f, 0f,0f,0f,1f,0f
        )))
        if (invert) matrix.timesAssign(ColorMatrix(floatArrayOf(
            -1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f
        )))
        return matrix
    }
}
