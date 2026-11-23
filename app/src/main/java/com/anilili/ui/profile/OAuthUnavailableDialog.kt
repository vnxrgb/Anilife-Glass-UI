package com.anilili.ui.profile

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Missing OAuth client configuration is a release packaging problem, not a login failure.
 * Show a recoverable explanation instead of invoking authorizeUrl(), which intentionally
 * rejects an unconfigured client. Keep this on both Settings and My List entry points.
 */
@Composable
fun OAuthUnavailableDialog(service: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$service sign-in unavailable") },
        text = {
            Text(
                "This AniLumos build is missing the $service sign-in configuration. " +
                    "Install a correctly configured update to connect your account. " +
                    "Your existing local library and downloads are unaffected.",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
