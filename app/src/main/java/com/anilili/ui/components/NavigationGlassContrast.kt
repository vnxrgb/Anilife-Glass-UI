package com.anilili.ui.components

/** Pure contrast policy; kept independent of Android/Compose backdrop classes for JVM tests. */
internal fun navigationGlassOpacity(light: Boolean): Float = if (light) .84f else .78f
