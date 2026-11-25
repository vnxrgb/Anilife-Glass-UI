package com.anilili.ui.watch

import androidx.compose.runtime.compositionLocalOf
import com.anilili.data.wasm.WasmPlaybackSource

/** Private local-player capability. UI stream records carry only an opaque handle. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal val LocalProtectedWasmSource = compositionLocalOf<WasmPlaybackSource?> { null }
