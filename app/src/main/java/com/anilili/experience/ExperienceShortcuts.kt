package com.anilili.experience

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.anilili.MainActivity
import com.anilili.R
import com.anilili.ui.nav.Routes

internal object ExperienceShortcuts {
    fun install(context: Context) {
        if (Build.VERSION.SDK_INT < 25) return
        runCatching {
            val manager = context.getSystemService(ShortcutManager::class.java) ?: return
            val actions = listOf(
                Triple("search_anime", "Search", Routes.SEARCH),
                Triple("discover_anime", "Discover", Routes.SEARCH),
                Triple("downloads", "Downloads", Routes.DOWNLOADS),
                Triple("continue_watching", "Continue watching", Routes.HOME)
            )
            val limit = (manager.maxShortcutCountPerActivity - manager.manifestShortcuts.size).coerceAtLeast(0)
            val shortcuts = actions.take(limit).map { (id, label, route) ->
                ShortcutInfo.Builder(context, id)
                    .setShortLabel(label)
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra(Routes.EXTRA_ROUTE, route)
                    }).build()
            }
            manager.addDynamicShortcuts(shortcuts)
        }
    }
}
