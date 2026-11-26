package com.anilili.experience

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anilili.ui.dialogs.MidnightSettingRow

internal object PrivacyLockPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("experience_privacy", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun delayMinutes(context: Context) = prefs(context).getInt("delay_minutes", 5).takeIf { it in listOf(0, 1, 5, 10, 15, 30) } ?: 5
    fun setDelayMinutes(context: Context, minutes: Int) { require(minutes in listOf(0, 1, 5, 10, 15, 30)); prefs(context).edit().putInt("delay_minutes", minutes).apply() }
    fun setEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("enabled", enabled).apply() }
}

@Composable
internal fun PrivacyLockSetting() {
    val context = LocalContext.current
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    var enabled by remember { mutableStateOf(PrivacyLockPreferences.enabled(context)) }
    var target by remember { mutableStateOf(false) }
    var delay by remember { mutableIntStateOf(PrivacyLockPreferences.delayMinutes(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            PrivacyLockPreferences.setEnabled(context, target)
            enabled = target
        }
    }
    MidnightSettingRow(
        title = "Privacy Lock",
        description = "Require your device screen lock when reopening AniLumos",
        value = if (enabled) "On" else "Off",
        onClick = {
            val request = keyguard?.createConfirmDeviceCredentialIntent("Privacy Lock", "Confirm to change app lock")
            if (request == null) Toast.makeText(context, "Set a secure device screen lock first", Toast.LENGTH_SHORT).show()
            else {
                target = !enabled
                runCatching { launcher.launch(request) }.onFailure {
                    Toast.makeText(context, "Authentication unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
    if (enabled) {
        Text("Lock after leaving AniLumos", style = MaterialTheme.typography.labelMedium)
        listOf(0, 1, 5, 10, 15, 30).forEach { minutes ->
            val label = if (minutes == 0) "Immediately" else "After $minutes minute" + if (minutes == 1) "" else "s"
            MidnightSettingRow(title = label, value = if (delay == minutes) "Selected" else "", onClick = {
                PrivacyLockPreferences.setDelayMinutes(context, minutes)
                delay = minutes
            })
        }
    }
}

@Composable
internal fun PrivacyLockScreen(onUnlock: () -> Unit) {
    BackHandler { }
    LaunchedEffect(Unit) { onUnlock() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("AniLumos is locked", style = MaterialTheme.typography.headlineSmall)
            Text("Authenticate using your device screen lock.")
            CircularProgressIndicator()
        }
    }
}
