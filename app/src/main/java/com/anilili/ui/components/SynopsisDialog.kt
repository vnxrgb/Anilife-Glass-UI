package com.anilili.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anilili.ui.adaptive.LocalAppDeviceProfile
import com.anilili.ui.adaptive.focusFill
import com.anilili.ui.adaptive.rememberFocusFill
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/**
 * The full synopsis, for when the page it came from had room for five lines of it.
 *
 * A remote has no scrollbar to grab and no way to select text, so Up/Down are handled here
 * explicitly and drive the scroll a screenful at a time: relying on a focusable scroll container
 * to pick D-pad keys up leaves long descriptions unreadable past the first screen. Back closes it
 * through the dialog's own dismiss, and the close button takes focus on open so the remote always
 * has somewhere to be.
 */
@Composable
fun SynopsisDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
) {
    if (description.isBlank()) return
    val isTv = LocalAppDeviceProfile.current.isTv
    val scheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val closeFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val scrollStep by animateFloatAsState(600f, label = "synopsisScrollStep")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isTv) Color.Black.copy(alpha = 0.94f) else scheme.scrim.copy(alpha = 0.76f))
                .clickable(onClickLabel = "Close synopsis", onClick = onDismiss)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            scope.launch { scroll.animateScrollBy(scrollStep) }
                            true
                        }
                        Key.DirectionUp -> {
                            scope.launch { scroll.animateScrollBy(-scrollStep) }
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 900.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (isTv) 48.dp else 18.dp, vertical = if (isTv) 56.dp else 28.dp)
                    .then(
                        if (isTv) Modifier else Modifier.glassCard(RoundedCornerShape(26.dp))
                            .padding(horizontal = 22.dp, vertical = 24.dp)
                    )
                    .verticalScroll(scroll),
            ) {
                Text(
                    text = title,
                    color = if (isTv) Color.White else scheme.onSurface,
                    fontSize = if (isTv) 26.sp else 23.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = description,
                    color = if (isTv) Color.White.copy(alpha = 0.88f) else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            val controlFocus1 = rememberFocusFill()
            IconButton(
                colors = controlFocus1.iconButtonColors(IconButtonDefaults.iconButtonColors()),
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .focusRequester(closeFocus)
                    .focusFill(controlFocus1, CircleShape),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = controlFocus1.content(if (isTv) Color.White else scheme.onSurface))
            }
        }
    }

    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { closeFocus.requestFocus() }
    }
}
