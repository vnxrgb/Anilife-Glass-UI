package com.anilumos.manga.engine

import kotlinx.coroutines.CancellationException
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.IOException

internal enum class ProviderState { UNCHECKED, READY, EMPTY, FILTERED, NO_CHAPTERS, NO_PAGES, RESTRICTED, UNSUPPORTED, NETWORK_ERROR, FAILED }
internal data class ProviderCheck(val state: ProviderState, val checkedAt: Long)
internal class ProviderAccessException(val status: Int) : IOException("Provider access or rate limit: HTTP $status")

/** Only explicitly reviewed scopes may be probed. No automatic bulk network requests. */
internal class ProviderHealth(private val now: () -> Long = System::currentTimeMillis) {
    private val results = mutableMapOf<String, ProviderCheck>()
    @Synchronized fun visible(source: String, maxAgeMs: Long = 900_000L): Boolean {
        val check = results[source] ?: return false
        return check.state == ProviderState.READY && now() - check.checkedAt in 0..maxAgeMs
    }
    @Synchronized fun record(source: String, state: ProviderState): ProviderCheck =
        ProviderCheck(state, now()).also { results[source] = it }

    suspend fun check(source: String, operation: suspend () -> ProviderState): ProviderCheck {
        val state = try { operation() }
        catch (e: CancellationException) { throw e }
        catch (e: ProviderAccessException) { ProviderState.RESTRICTED }
        catch (e: UnsupportedOperationException) { ProviderState.UNSUPPORTED }
        catch (e: IOException) { ProviderState.NETWORK_ERROR }
        catch (e: Exception) { ProviderState.FAILED }
        return record(source, state)
    }

    suspend fun probe(registry: ParserRegistry, scope: SourceScope, query: String): ProviderCheck =
        check(scope.sourceName) {
            SourceSession(scope).use { session ->
                val repository = ParserRepository(registry, scope, session)
                val catalog = repository.browse("", 0, SortOrder.POPULARITY)
                if (catalog.consumed == 0) return@check ProviderState.EMPTY
                if (catalog.titles.isEmpty()) return@check ProviderState.FILTERED
                val search = repository.browse(query, 0, SortOrder.RELEVANCE)
                if (search.consumed == 0) return@check ProviderState.EMPTY
                val title = search.titles.firstOrNull() ?: return@check ProviderState.FILTERED
                val details = repository.details(title)
                val chapter = details.chapters.orEmpty().firstOrNull() ?: return@check ProviderState.NO_CHAPTERS
                val page = repository.pages(details, chapter).firstOrNull() ?: return@check ProviderState.NO_PAGES
                repository.loadImage(page)
                ProviderState.READY
            }
        }
}
