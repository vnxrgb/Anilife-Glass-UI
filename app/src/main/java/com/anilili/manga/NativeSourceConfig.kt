package com.anilili.manga

import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig

/** Temporary adapter contract; independent of any provider settings screen. */
internal interface NativeCatalogProvider {
    fun rawCatalogCount(): Int
}

/** Temporary default parser configuration until the replacement engine is integrated. */
internal object NativeSourceConfig : MangaSourceConfig {
    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: ConfigKey<T>): T = when (key) {
        is ConfigKey.ShowSuspiciousContent -> false
        is ConfigKey.InterceptCloudflare -> false
        else -> key.defaultValue
    } as T
}
