package com.anilili.ui.watch

import com.anilili.data.model.SourceVariant

/** A provider can offer multiple audio/subtitle variants. Each is a distinct list item. */
internal fun serverPickerKey(option: WatchSourceOption): String =
    "${option.provider}:${option.variant.route}"

/**
 * Keep the initial ranking (with the playing source first), then keep every previously
 * seen identity in place. New discoveries append; removed choices disappear, but if they
 * return they recover their prior position. Always use fresh option objects for status.
 */
internal fun stableServerPickerRows(
    seenKeys: List<String>,
    options: List<WatchSourceOption>,
    playingProvider: String,
    playingVariant: SourceVariant,
): List<WatchSourceOption> {
    val available = options.distinctBy(::serverPickerKey)
    val byKey = available.associateBy(::serverPickerKey)
    val remaining = available.filter { serverPickerKey(it) !in seenKeys }
    return if (seenKeys.isEmpty()) {
        remaining.sortedByDescending { it.provider == playingProvider && it.variant == playingVariant }
    } else {
        seenKeys.distinct().mapNotNull(byKey::get) + remaining
    }
}
