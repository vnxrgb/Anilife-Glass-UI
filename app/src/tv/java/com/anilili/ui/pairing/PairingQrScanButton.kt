package com.anilili.ui.pairing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Pairing scanner is deliberately absent on televisions (no camera required). */
@Composable
internal fun PairingQrScanButton(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Unit,
) = Unit
