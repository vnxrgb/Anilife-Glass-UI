package com.anilili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.anilili.ui.adaptive.focusFill
import com.anilili.ui.adaptive.rememberFocusFill
import kotlinx.coroutines.android.awaitFrame

/** Full-screen cover viewer that reuses the same Coil URL/cache as the detail-page thumbnail. */
@Composable
fun CoverImagePreview(
    imageUrl: String?,
    title: String,
    onDismiss: () -> Unit,
) {
    if (imageUrl.isNullOrBlank()) return
    var zoom by remember(imageUrl) { mutableFloatStateOf(1f) }
    var pan by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    val closeFocusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f))
                .clipToBounds()
                .pointerInput(imageUrl) {
                    detectTransformGestures { _, movement, factor, _ ->
                        zoom = (zoom * factor).coerceIn(1f, 4f)
                        val x = size.width * (zoom - 1f) / 2f
                        val y = size.height * (zoom - 1f) / 2f
                        pan = if (zoom == 1f) Offset.Zero else Offset(
                            (pan.x + movement.x).coerceIn(-x, x),
                            (pan.y + movement.y).coerceIn(-y, y))
                    }
                }
                .pointerInput(imageUrl) {
                    detectTapGestures(onDoubleTap = {
                        zoom = if (zoom > 1f) 1f else 2f
                        pan = Offset.Zero
                    })
                },
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "$title cover",
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 36.dp)
                    .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
                contentScale = ContentScale.Fit,
            )
            val controlFocus1 = rememberFocusFill()
            IconButton(
                colors = controlFocus1.iconButtonColors(IconButtonDefaults.iconButtonColors()),
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .focusRequester(closeFocusRequester)
                    .focusFill(controlFocus1, CircleShape),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = controlFocus1.content(Color.White))
            }
        }
    }

    // Makes the preview immediately operable with a keyboard or TV remote as well as touch.
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { closeFocusRequester.requestFocus() }
    }
}
