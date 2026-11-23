package com.anilili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anilili.ui.adaptive.UnscaledUi

const val LaunchSplashFadeMillis = 250

/** Always-black Anilumos launch screen; only the A glow follows the app theme. */
@Composable
fun AppLaunchSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    animateFlame: Boolean = true,
) {
    val latestOnFinished = rememberUpdatedState(onFinished)
    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        UnscaledUi {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WaterFillLogoIndicator(
                    size = 152.dp,
                    playOnce = true,
                    animateFlame = animateFlame,
                    onFilled = { latestOnFinished.value() },
                )
                Text(
                    "ANILUMOS",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(2.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                )
            }
        }
    }
}
