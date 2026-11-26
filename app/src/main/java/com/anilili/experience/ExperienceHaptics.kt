package com.anilili.experience

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.anilili.ui.adaptive.LocalAppDeviceProfile
import com.anilili.ui.dialogs.MidnightSettingSwitchRow

internal object ExperienceHaptics {
    private var lastPulse = 0L
    private fun prefs(context: Context) = context.getSharedPreferences("experience_haptics", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("enabled", enabled).apply() }

    fun pulse(view: View) {
        if (!enabled(view.context) || !view.isHapticFeedbackEnabled) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPulse < 55L) return
        // No bypass flags: the platform respects system touch-feedback settings.
        val type = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CLOCK_TICK
            else HapticFeedbackConstants.KEYBOARD_TAP
        if (view.performHapticFeedback(type)) lastPulse = now
    }
}

@Composable
internal fun rememberExperienceHaptic(): () -> Unit {
    val view = LocalView.current
    val isTv = LocalAppDeviceProfile.current.isTv
    return remember(view, isTv) { { if (!isTv) ExperienceHaptics.pulse(view) } }
}

@Composable
internal fun HapticFeedbackSetting() {
    val context = LocalContext.current
    if (LocalAppDeviceProfile.current.isTv) return
    var enabled by remember { mutableStateOf(ExperienceHaptics.enabled(context)) }
    MidnightSettingSwitchRow(
        title = "Haptic feedback",
        description = "Subtle touch feedback on navigation, settings and playback controls",
        checked = enabled,
        onCheckedChange = { enabled = it; ExperienceHaptics.setEnabled(context, it) }
    )
}
