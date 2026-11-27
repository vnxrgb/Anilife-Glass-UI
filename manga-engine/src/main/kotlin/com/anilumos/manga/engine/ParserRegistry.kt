package com.anilumos.manga.engine

import org.koitharu.kotatsu.parsers.DirectParsersAccess
import org.koitharu.kotatsu.parsers.ParsersApi
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.core.ParsersBundleImpl
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.model.ContentType

internal data class SourceDescriptor(
    val id: String, val title: String, val locale: String,
    val category: String, val markedBroken: Boolean,
)

/** Stable identity is the source name, not its implementation class or enum ordinal.
 * This registry has no executable-download mechanism. One compiled bundle for this phase. */
@OptIn(DirectParsersAccess::class)
internal class ParserRegistry(val bundle: ParsersBundle = ParsersBundleImpl) {
    companion object {
        val supportedCategories = setOf(ContentType.MANGA, ContentType.MANHWA, ContentType.MANHUA, ContentType.COMICS, ContentType.ONE_SHOT).map { it.name }.toSet()
    }
    init { require(bundle.apiVersion == ParsersApi.VERSION) }
    val revision: String get() = bundle.revision
    fun descriptors(): List<SourceDescriptor> = bundle.sources.distinctBy { it.name }.map {
        SourceDescriptor(it.name, it.title, it.locale, it.contentType.name, it.isBroken)
    }
    fun requireSource(scope: SourceScope): ParserMangaSource {
        val source = bundle.findSource(scope.sourceName) ?: error("Unknown parser source")
        check(!source.isBroken) { "Source marked broken by its parser bundle" }
        check(source.contentType.name in supportedCategories) { "Source category not supported" }
        return source
    }
}
