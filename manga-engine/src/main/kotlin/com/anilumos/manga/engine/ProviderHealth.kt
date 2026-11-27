package com.anilumos.manga.engine

import kotlinx.coroutines.CancellationException
import java.io.IOException

internal enum class ProviderState { UNCHECKED, READY, EMPTY, FILTERED, TITLE_NOT_FOUND, AMBIGUOUS_TITLE, NO_CHAPTERS, NO_PAGES, RESTRICTED, UNSUPPORTED, NETWORK_ERROR, IMAGE_ERROR, FAILED }
internal enum class ProbeStage { INITIALIZE, SELECT_TITLE, DETAILS, CHAPTER, PAGE_LIST, PAGE_DOWNLOAD, IMAGE_DECODE, COMPLETE }
internal enum class UnsupportedFeature { BROWSER_EXECUTION, PARSER_IMAGE_TRANSFORM }
internal class ProviderFeatureException(val feature: UnsupportedFeature) : UnsupportedOperationException(feature.name)
internal data class ProviderCheck(
    val state: ProviderState, val checkedAt: Long, val stage: ProbeStage? = null,
    val httpStatus: Int? = null, val feature: UnsupportedFeature? = null,
)
internal class ProviderAccessException(val status: Int) : IOException("Provider access or rate limit: HTTP $status")

/** No credentials, URLs, image bytes or exception message text in the diagnostic snapshot. */
internal class ProviderHealth(private val now: () -> Long = System::currentTimeMillis) {
    private val results = mutableMapOf<String, ProviderCheck>()
    @Synchronized fun snapshot(): Map<String, ProviderCheck> = results.toMap()
    @Synchronized fun visible(source: String, maxAgeMs: Long = 900_000L): Boolean {
        val check = results[source] ?: return false
        return check.state == ProviderState.READY && now() - check.checkedAt in 0..maxAgeMs
    }
    @Synchronized fun record(source: String, state: ProviderState, stage: ProbeStage? = null,
        httpStatus: Int? = null, feature: UnsupportedFeature? = null): ProviderCheck =
        ProviderCheck(state, now(), stage, httpStatus, feature).also { results[source] = it }

    suspend fun check(source: String, operation: suspend () -> ProviderState): ProviderCheck =
        checkStaged(source) { operation() }

    internal suspend fun checkStaged(source: String, operation: suspend ((ProbeStage) -> Unit) -> ProviderState): ProviderCheck {
        var stage = ProbeStage.INITIALIZE
        return try {
            val state = operation { stage = it }
            record(source, state, if (state == ProviderState.READY) ProbeStage.COMPLETE else stage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderAccessException) {
            record(source, ProviderState.RESTRICTED, stage, httpStatus = e.status)
        } catch (e: ProviderFeatureException) {
            record(source, ProviderState.UNSUPPORTED, stage, feature = e.feature)
        } catch (e: UnsupportedOperationException) {
            record(source, ProviderState.UNSUPPORTED, stage)
        } catch (e: IOException) {
            record(source, if (stage == ProbeStage.IMAGE_DECODE) ProviderState.IMAGE_ERROR else ProviderState.NETWORK_ERROR, stage)
        } catch (e: Exception) {
            record(source, ProviderState.FAILED, stage)
        } catch (e: LinkageError) {
            record(source, ProviderState.UNSUPPORTED, stage)
        }
    }

    suspend fun probe(registry: ParserRegistry, scope: SourceScope, query: String, chapterNumber: Float,
        headers: SourceHeaders = SourceHeaders(scope)): ProviderCheck =
        checkStaged(scope.sourceName) { stage ->
            SourceSession(scope, headers = headers).use { session ->
                val repository = ParserRepository(registry, scope, session)
                stage(ProbeStage.SELECT_TITLE)
                val selection = repository.approvedTitle(query)
                val title = selection.title ?: return@checkStaged selection.state
                stage(ProbeStage.DETAILS)
                val details = repository.details(title)
                stage(ProbeStage.CHAPTER)
                val chapter = details.chapters.orEmpty().firstOrNull { it.number == chapterNumber } ?: return@checkStaged ProviderState.NO_CHAPTERS
                stage(ProbeStage.PAGE_LIST)
                val page = repository.pages(details, chapter).firstOrNull() ?: return@checkStaged ProviderState.NO_PAGES
                stage(ProbeStage.PAGE_DOWNLOAD)
                val bytes = repository.loadImage(page)
                stage(ProbeStage.IMAGE_DECODE)
                val bitmap = PageDecoder.decode(bytes)
                bitmap.recycle()
                ProviderState.READY
            }
        }
}
