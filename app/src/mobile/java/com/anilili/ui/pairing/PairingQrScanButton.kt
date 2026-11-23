package com.anilili.ui.pairing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Camera scanning exists only on mobile; the TV build has no camera permission. */
@Composable
internal fun PairingQrScanButton(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onScanned)
    }
    Button(
        modifier = modifier,
        onClick = {
            launcher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Scan the QR code shown on your TV")
                    .setBeepEnabled(false)
                    .setBarcodeImageEnabled(false)
                    .setOrientationLocked(false),
            )
        },
    ) {
        Text("Scan TV QR")
    }
}
