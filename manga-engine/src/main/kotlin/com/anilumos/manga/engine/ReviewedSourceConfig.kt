package com.anilumos.manga.engine

import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig

/** Source-local presentation preference. Domain, user agent, image server and access
 * controls cannot be overridden by this configuration. */
internal class ReviewedSourceConfig(
    private val splitTranslations: Boolean? = null,
) : MangaSourceConfig {
    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: ConfigKey<T>): T = when (key) {
        is ConfigKey.ShowSuspiciousContent -> false
        is ConfigKey.InterceptCloudflare -> false
        is ConfigKey.DisableUpdateChecking -> true
        is ConfigKey.SplitByTranslations -> splitTranslations ?: key.defaultValue
        else -> key.defaultValue
    } as T
}
