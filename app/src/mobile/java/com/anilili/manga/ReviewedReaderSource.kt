package com.anilili.manga

import com.anilumos.manga.engine.ReviewedCatalog
import com.anilumos.manga.engine.LocalScriptRuntime
import android.content.Context
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.anilumos.manga.engine.ProviderCompatibilityException
import com.anilumos.manga.engine.classifyProviderFailure
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun reviewedReaderSupported(): Boolean = true
internal suspend fun reviewedReaderProviders(context: Context): List<ReviewedReaderProvider> {
    LocalScriptRuntime.initialize(context)
    val revision = ReviewedCatalog.compatibilityRevision()
    val health = MangaProviderHealthCache(context.getSharedPreferences("manga_provider_health_v2", Context.MODE_PRIVATE))
    return ReviewedCatalog.providers().map { provider ->
        ReviewedReaderProvider(reviewedSourceId(provider.id), provider.label, revision) { callback ->
            EngineCatalogSource(provider.id, provider.label, provider.locale, provider.category, health, revision, callback)
        }
    }
}
private fun reviewedSourceId(id: String) = if (id == "ATSUMOE") REVIEWED_SOURCE_ID else "engine:" + id
internal suspend fun openReviewedReader(): MangaSource {
    val provider = ReviewedCatalog.providers().single { it.id == "ATSUMOE" }
    return EngineCatalogSource(provider.id, provider.label, provider.locale, provider.category, null, ReviewedCatalog.compatibilityRevision()) { _, _ -> }
}

private class EngineCatalogSource(
    private val provider: String,
    override val displayName: String,
    override val languageTag: String,
    override val sourceCategory: String,
    private val health: MangaProviderHealthCache?,
    private val revision: String,
    private val onHealth: (String, ProviderDiagnostic?) -> Unit,
) : MangaSource, Closeable {
    override val id = reviewedSourceId(provider)
    @Volatile private var catalog: ReviewedCatalog? = null
    private val sessionLock = Mutex()
    @Volatile private var closed = false
    private suspend fun session(): ReviewedCatalog = sessionLock.withLock {
        check(!closed) { "Source session closed" }
        val prior = health?.read(id, revision)
        if (prior != null && !prior.ready && (prior.reason.startsWith("RATE_LIMIT") || prior.reason.startsWith("ACCESS_RESTRICTED"))) {
            throw ProviderCompatibilityException(prior.reason.substringBefore('/'), "COOLDOWN")
        }
        catalog ?: ReviewedCatalog.connect(provider).also { opened ->
            if (closed) { opened.close(); error("Source session closed") }
            catalog = opened
        }
    }
    private suspend fun success(readable: Boolean) {
        val previous = health?.read(id, revision)
        // Catalogue success is not proof of a readable chapter. Never downgrade a verified reader.
        if (!readable && previous?.reason == "READY") return
        val status = if (readable) "READY" else "CATALOGUE"
        health?.record(id, revision, true, status)
        withContext(Dispatchers.Main.immediate) { if (!closed) onHealth(status, null) }
    }
    private suspend fun failure(error: Exception) {
        // A cooldown is not a new access attempt; do not extend the original cooldown.
        if (error is ProviderCompatibilityException && error.stage == "COOLDOWN") return
        val reason = providerFailureReason(error)
        val diagnostic = providerDiagnostic(error)
        health?.record(id, revision, false, reason)
        withContext(Dispatchers.Main.immediate) { if (!closed) onHealth(reason, diagnostic) }
    }
    private suspend fun <T> checked(stage: String, accessOnly: Boolean = false, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        val classified = classifyProviderFailure(stage, e)
        if (!accessOnly || classified.reason in setOf("RATE_LIMIT", "ACCESS_RESTRICTED")) failure(classified)
        throw classified
    }
    override val hasNativeCatalogue = true
    override suspend fun genreOptions(): List<String> = checked("SELECT_TITLE", accessOnly = true) { session().genres() }
    override suspend fun kindOptions(): List<MangaKind> = checked("SELECT_TITLE", accessOnly = true) { session().kinds() }.mapNotNull { value -> MangaKind.entries.firstOrNull { it.name == value } }
    override suspend fun prepareTitle(title: MangaTitle): MangaTitle {
        check(session().restore(title.id, title.title)) { "Saved title could not be resolved" }
        return session().detail(title.id)?.let(::convert) ?: title
    }
    override val chapterLanguages = listOf("und")
    private val covers = LinkedHashMap<String, android.graphics.Bitmap?>(4, .75f, true)
    private val lock = Mutex()
    override suspend fun artwork(mangaId: String): Any? = lock.withLock {
        // Saved-list thumbnails must not open a new provider session in the background.
        val active = catalog ?: return@withLock null
        if (!covers.containsKey(mangaId)) {
            if (covers.size >= 3) covers.remove(covers.keys.first())
            covers[mangaId] = active.cover(mangaId)
        }
        covers[mangaId]
    }
    private fun convert(title: ReviewedCatalog.Title) = MangaTitle(id, title.id, title.name,
        cleanMangaDescription(androidx.core.text.HtmlCompat.fromHtml(title.description, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString()), null,
        genres = title.genres, authors = title.authors, status = title.status, alternativeTitles = title.alternatives)
    override suspend fun search(query: String, limit: Int): List<MangaTitle> =
        catalogPage(query, MangaKind.ALL, MangaOrder.POPULAR, null, 0, limit.coerceIn(1, 30)).items
    override suspend fun catalogPage(query: String, kind: MangaKind, order: MangaOrder, genre: String?, offset: Int, limit: Int): MangaCatalogPage {
        val initial = query.isBlank() && offset == 0 && genre == null && kind == MangaKind.ALL && order == MangaOrder.POPULAR
        val result = checked("SELECT_TITLE", accessOnly = !initial) { session().list(query.take(200), offset, limit, genre, order == MangaOrder.RECENT, kind.name.takeUnless { kind == MangaKind.ALL || kind == MangaKind.OTHER }) }
        if (initial && result.items.isNotEmpty()) success(false)
        return MangaCatalogPage(result.items.map { convert(it).copy(kind = if (kind == MangaKind.ALL) MangaKind.OTHER else kind) }, result.nextOffset)
    }
    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> =
        checked("CHAPTER", accessOnly = true) { session().chapters(mangaId) }.map { MangaChapter(id, it.id, it.titleId, it.label, it.number, "und", listOfNotNull(it.scanlator)) }
    override suspend fun pages(chapterId: String): List<MangaPage> =
        List(checked("PAGE_LIST") { session().pageCount(chapterId) }) { index -> MangaPage(index, "", decode = { w, h ->
            val image = checked("IMAGE_DECODE") { session().decode(chapterId, index, w, h) }
            if (health?.read(id, revision)?.reason != "READY") success(true)
            image
        }) }
    override fun close() { closed = true; catalog?.close(); catalog = null }
}
