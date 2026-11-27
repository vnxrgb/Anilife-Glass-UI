package com.anilili.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.anilili.ui.watch.findActivity
import com.anilili.ui.watch.PlayerSeekBar
import com.anilili.ui.dialogs.popupGlass
import com.anilili.ui.dialogs.rememberPopupBackdrop
import com.anilili.ui.home.HomeHeroAction
import com.anilili.ui.home.GenrePill
import com.anilili.ui.home.RailTitle
import com.anilili.ui.home.heroVisibleIndicatorIndices
import com.anilili.ui.detail.SharedDetailTabs
import com.anilili.ui.detail.GenreRow
import com.anilili.ui.detail.Description
import com.anilili.ui.watch.PlayerModalSheet
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.AsyncImage
import coil.request.ImageRequest
import okhttp3.Headers.Companion.toHeaders
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.anilili.data.profiles.Profiles
import com.anilili.ui.components.*
import com.anilili.ui.adaptive.LocalAppDeviceProfile
import kotlin.math.roundToInt

private enum class ReaderBackground(val label: String) { THEME("Match app"), LIGHT("Light"), DARK("Dark"), BLACK("AMOLED") }
private enum class MangaDetailTab(val label: String) { OVERVIEW("Overview"), CHAPTERS("Chapters") }
private enum class ReadingMode(val label: String) { WEBTOON("Webtoon"), VERTICAL("Vertical paged"), RTL("Right to left"), LTR("Left to right") }
private enum class LibraryFilter(val label: String) { ALL("All"), READING("Reading"), SAVED("Bookmarked") }
private suspend fun <T> resultOf(block: suspend () -> T): Result<T> =
    try { Result.success(block()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

private fun MangaTitle.identity() = "$sourceId:$id"
private fun MangaChapter.label() = chapterNumber?.let { "Chapter $it" } ?: title.takeIf { it.isNotBlank() } ?: "Special chapter"
private fun MangaTitle.metadata() = listOfNotNull(kind.label.takeUnless { kind == MangaKind.OTHER }, year?.toString(), status.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }).joinToString(" · ")
private fun MangaSaved.readLabel() = listOfNotNull(chapterNumber?.let { "Chapter $it" }, if (chapterId != null) "Page ${page + 1}" else "Bookmarked").joinToString(" · ")

@Composable
fun MangaScreen(onBack: () -> Unit, section: MangaSection = MangaSection.HOME, onHeroUnderBarsChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val profile by Profiles.activeId.collectAsState()
    val store = remember(context, profile) { MangaReadingStore(context, profile) }
    key(profile, section) { MangaDestination(store, section, onBack, onHeroUnderBarsChanged) }
}

@Composable
private fun MangaDestination(store: MangaReadingStore, section: MangaSection, onBack: () -> Unit, onHeroUnderBarsChanged: (Boolean) -> Unit) {
    val registry = remember { MangaSources(builtInKotatsuSources()) }
    val healthContext = LocalContext.current
    val health = remember(healthContext) { MangaSourceHealthStore(healthContext) }
    val healthStates by health.states.collectAsState()
    var verifiedIds by remember(healthContext) { mutableStateOf(MangaVerifiedSources.cached(healthContext)) }
    val sourcePrefs = remember(healthContext) { healthContext.getSharedPreferences("manga_source_selection", android.content.Context.MODE_PRIVATE) }
    val verifiedSources = remember(registry, verifiedIds) { registry.all().filter { it.id in verifiedIds && preferredMangaLanguage(it.languageTag) } }
    val selectableSources = remember(verifiedSources, healthStates) { verifiedSources.filter { healthStates[it.id] !in setOf(SourceHealth.RESTRICTED, SourceHealth.SERVER_TLS, SourceHealth.FAILED) } }
    var sourceId by remember { mutableStateOf(sourcePrefs.getString("provider", "").orEmpty()) }
    LaunchedEffect(healthContext) { verifiedIds = MangaVerifiedSources.refresh(healthContext) }
    LaunchedEffect(verifiedSources) {
        if (verifiedSources.none { it.id == sourceId }) sourceId = verifiedSources.firstOrNull { it.languageTag.startsWith("en") }?.id ?: verifiedSources.firstOrNull()?.id.orEmpty()
    }
    LaunchedEffect(sourceId) { if (sourceId.isNotEmpty()) sourcePrefs.edit().putString("provider", sourceId).apply() }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(MangaKind.ALL) }
    var order by rememberSaveable { mutableStateOf(MangaOrder.POPULAR) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<MangaTitle>>(emptyList()) }
    var recent by remember { mutableStateOf<List<MangaTitle>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var moreLoading by remember { mutableStateOf(false) }
    var catalogGeneration by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var offset by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<MangaTitle?>(null) }
    var readImmediately by remember { mutableStateOf(false) }
    var chapter by remember { mutableStateOf<MangaChapter?>(null) }
    var availableChapters by remember { mutableStateOf<List<MangaChapter>>(emptyList()) }
    var saved by remember(store) { mutableStateOf(store.all()) }
    var libraryFilter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var libraryGrid by rememberSaveable { mutableStateOf(true) }
    var librarySort by rememberSaveable { mutableStateOf("Recent") }
    var unavailableTitle by remember { mutableStateOf<MangaTitle?>(null) }
    var sourceMenu by remember { mutableStateOf(false) }
    var attribution by remember { mutableStateOf(false) }
    val openSaved: (MangaSaved, Boolean) -> Unit = { entry, resume ->
        if (verifiedSources.any { it.id == entry.title.sourceId }) { readImmediately = resume; selected = entry.title }
        else unavailableTitle = entry.title
    }
    val scope = rememberCoroutineScope()
    val provider = verifiedSources.firstOrNull { it.id == sourceId }
    val home = section == MangaSection.HOME
    val browse = section == MangaSection.BROWSE
    val library = section == MangaSection.LIBRARY
    val device = LocalAppDeviceProfile.current
    val bottom = LocalAppChromeBottomInset.current
    val revealChrome = LocalAppChromeReveal.current
    val listState = rememberLazyListState()
    val back: () -> Unit = {
        if (chapter != null) { chapter = null; saved = store.all() }
        else if (selected != null) { selected = null; readImmediately = false; saved = store.all(); revealChrome() }
        else onBack()
    }
    BackHandler(selected != null || chapter != null) { back() }
    LaunchedEffect(Unit) { saved = store.all() }
    LaunchedEffect(provider?.id, query, kind, order, retry, section) {
        if (!home && !browse) { loading = false; return@LaunchedEffect }
        if (browse && query.isNotBlank()) delay(350)
        listState.scrollToItem(0)
        catalogGeneration++; moreLoading = false
        loading = true; error = null; results = emptyList(); recent = emptyList(); offset = 0; hasMore = false
        if (provider == null) error = "No verified providers available. The provider list updates automatically."
        else resultOf { provider.catalog(if (browse) query.trim() else "", kind, order, limit = 24) }
            .onSuccess { results = it; offset = it.size; hasMore = it.size == 24; if (it.isNotEmpty()) health.record(provider.id, SourceHealth.CATALOG) }
            .onFailure { health.record(provider.id, classifySourceFailure(it)); error = healthError(it) }
        loading = false
        if (home && provider != null && error == null && results.isNotEmpty()) {
            delay(350)
            resultOf { provider.catalog("", kind, MangaOrder.RECENT, limit = 12) }.onSuccess { recent = it }
        }
    }
    if (sourceMenu) MangaSourcePicker(selectableSources, provider?.id, onDismiss = { sourceMenu = false }, onSelect = { source ->
        sourceId = source.id; kind = MangaKind.ALL; genre = null; sourceMenu = false; retry++
    }, onInfo = { sourceMenu = false; attribution = true })
    unavailableTitle?.let { unavailable -> AlertDialog(onDismissRequest = { unavailableTitle = null }, title = { Text("Provider unavailable") }, text = { Text("${unavailable.title} remains in your reading list. Its provider is not currently verified.") }, confirmButton = { TextButton(onClick = { unavailableTitle = null }) { Text("OK") } }) }
    if (attribution) AlertDialog(onDismissRequest = { attribution = false }, title = { Text("Built-in sources") },
        text = { Text("Catalog and chapters come from the built-in Kotatsu-Redo parser library. Adult categories and unconfirmed content ratings are excluded. Known broken sources are omitted. Automated verification checks one safe sample on the CI network; local checks may differ. Catalog success is not proof every chapter works. Availability and access rights vary by provider; no external extensions are installed. Chapter credits are shown when provided.") },
        confirmButton = { TextButton(onClick = { attribution = false }) { Text("Close") } })
    val title = selected
    val activeChapter = chapter
    val titleSource = title?.let { entry -> verifiedSources.firstOrNull { it.id == entry.sourceId } }
    if (title != null && titleSource != null) {
        SideEffect { onHeroUnderBarsChanged(false) }
        AppChromeTakeoverEffect()
        if (activeChapter != null) {
            val distinct = availableChapters.distinctBy { it.chapterNumber ?: it.id }
            val index = distinct.indexOfFirst { it.id == activeChapter.id || it.chapterNumber != null && it.chapterNumber == activeChapter.chapterNumber }
            MangaReader(title, activeChapter, titleSource, store, availableChapters, health, onBack = back,
                onChapter = { chapter = it },
                previousChapter = distinct.getOrNull(index + 1)?.takeIf { index >= 0 }?.let { entry -> { chapter = entry } },
                nextChapter = distinct.getOrNull(index - 1)?.let { entry -> { chapter = entry } })
        } else {
            MangaDetails(title, titleSource, store, saved.firstOrNull { it.title.identity() == title.identity() }, readImmediately, onBack = back,
                onBookmark = { value -> store.bookmark(title, value); saved = store.all() },
                onChapter = { entry, entries -> readImmediately = false; availableChapters = entries; chapter = entry })
        }
        return
    }
    val heroScrollClearance = with(androidx.compose.ui.platform.LocalDensity.current) { 100.dp.roundToPx() }
    val overHero = home && results.isNotEmpty() && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < heroScrollClearance
    SideEffect { onHeroUnderBarsChanged(overHero) }
    Scaffold(
        modifier = Modifier.fillMaxSize().glassAtmosphere(), containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            ScrollAwareTopBar {
                Column(Modifier.fillMaxWidth().then(if (home) Modifier.chromeFade(overHero) else Modifier.browseHeaderGlass(null))) {
                    if (home) {
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = device.pagePadding, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ContentModeWordmark(textColor = if (overHero) Color.White else MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { sourceMenu = true }) { Icon(Icons.Default.Storage, null, tint = if (overHero) Color.White else MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(4.dp)); Text("Providers", color = if (overHero) Color.White else MaterialTheme.colorScheme.onSurface) }
                        }
                    } else PageTitleBar(when (section) {
                        MangaSection.BROWSE -> "Browse"
                        MangaSection.LIBRARY -> "My List"
                        else -> "Downloads"
                    }, trailing = { if (browse) TextButton(onClick = { sourceMenu = true }) { Icon(Icons.Default.Storage, null); Spacer(Modifier.width(4.dp)); Text("Providers") } })

                }
            }
        }
    ) { padding ->
        val side = device.pagePadding
        val filtered = results.filter { genre == null || genre in it.genres }
        val list = saved.filter { entry -> registry.byId(entry.title.sourceId) != null }.filter { entry -> when (libraryFilter) {
            LibraryFilter.ALL -> entry.bookmarked || entry.chapterId != null
            LibraryFilter.READING -> entry.chapterId != null
            LibraryFilter.SAVED -> entry.bookmarked
        } }.filter { query.isBlank() || it.title.title.contains(query, ignoreCase = true) }.let { items -> when (librarySort) { "Title" -> items.sortedBy { it.title.title.lowercase() }; "Progress" -> items.sortedByDescending { if (it.pageCount > 0) (it.page + 1f) / it.pageCount else 0f }; else -> items } }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = ((maxWidth.value - side.value * 2 + 10) / (device.gridMinWidth.value + 10)).toInt().coerceIn(2, 7)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, top = if (home && results.isNotEmpty()) 0.dp else padding.calculateTopPadding() + 8.dp,
                    bottom = maxOf(padding.calculateBottomPadding(), bottom + AppChromeListBottomGap)),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (home && results.isNotEmpty()) {
                    item("featured") {
                        MangaHomeCarousel(results.take(5), Modifier.requiredWidth(maxWidth), padding.calculateTopPadding(),
                            onDetails = { readImmediately = false; selected = it },
                            onRead = { readImmediately = true; selected = it })
                    }
                }
                if (browse || library) item("search") {
                    PageSearchField(query, { query = it }, if (browse) "Search manga, manhwa, manhua" else "Search reading list", "Clear search")
                }
                if (home || browse) item("filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(MangaKind.ALL, MangaKind.MANGA, MangaKind.MANHWA, MangaKind.MANHUA)) { option ->
                                GenrePill(option.label, kind == option, { kind = option; genre = null })
                            }
                        }
                        val genres = results.flatMap { it.genres }.distinct().sorted()
                        if (browse) PageFilterRow {
                            PageFilterPill(genre ?: "Genres", listOf(PageFilterOption(null, "All genres")) + genres.map { PageFilterOption(it, it) }, { genre = it }, Modifier.weight(1f))
                            PageFilterPill(if (order == MangaOrder.POPULAR) "Popular" else "Recent", listOf(PageFilterOption("popular", "Popular"), PageFilterOption("recent", "Recently updated")), { value -> order = if (value == "recent") MangaOrder.RECENT else MangaOrder.POPULAR; genre = null }, Modifier.weight(1f))
                        } else if (genres.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { GenrePill("All genres", genre == null, { genre = null }) }
                            items(genres) { option -> GenrePill(option, genre == option, { genre = if (genre == option) null else option }) }
                        }
                    }
                }
                if (home && saved.any { it.chapterId != null && registry.byId(it.title.sourceId) != null }) item("continue") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MangaHeading("Continue Reading")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(saved.filter { it.chapterId != null && registry.byId(it.title.sourceId) != null }, key = { it.title.identity() }) { entry ->
                                MangaContinueCard(entry, Modifier.width(170.dp)) { openSaved(entry, true) }
                            }
                        }
                    }
                }
                if (home || browse) {
                    if (loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    error?.let { message -> item { MangaError(message) { retry++ } } }
                    if (!loading && error == null && filtered.isEmpty()) item { Text("No titles found.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (home && filtered.isNotEmpty()) item("popular") { MangaRail("Popular Now", filtered.take(15), onClick = { selected = it }) }
                    if (home && recent.isNotEmpty()) item("recent") { MangaRail("Recently Updated", recent.filter { genre == null || genre in it.genres }, onClick = { selected = it }) }
                    if (home && genre == null) filtered.flatMap { it.genres }.distinct().take(6).forEach { category ->
                        val shelf = filtered.filter { category in it.genres }
                        if (shelf.size >= 2) item("genre-$category") { MangaRail(category, shelf, onClick = { selected = it }) }
                    }
                    if (browse && filtered.isNotEmpty()) {
                        item { MangaHeading(if (query.isBlank()) "Discover" else "Search results") }
                        items(filtered.chunked(columns)) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { manga -> MangaPoster(manga, Modifier.weight(1f)) { selected = manga } }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if (hasMore && genre == null) item("more") {
                            OutlinedButton(onClick = {
                                if (provider != null && !moreLoading) scope.launch {
                                    val generation = catalogGeneration
                                    moreLoading = true
                                    resultOf { provider.catalog(query.trim(), kind, order, offset, 24) }.onSuccess { batch ->
                                        if (generation == catalogGeneration) { results = (results + batch).distinctBy { it.identity() }; offset += batch.size; hasMore = batch.size == 24 }
                                    }.onFailure { if (generation == catalogGeneration) error = healthError(it) }
                                    if (generation == catalogGeneration) moreLoading = false
                                }
                            }, enabled = !moreLoading, modifier = Modifier.fillMaxWidth(), colors = cinematicSecondaryButtonColors(), border = cinematicSecondaryButtonBorder()) { Text(if (moreLoading) "Loading…" else "Load more") }
                        }
                    }
                } else if (library) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LibraryFilter.entries) { filter -> FilterChip(selected = libraryFilter == filter, onClick = { libraryFilter = filter }, label = { Text(filter.label) }, colors = cinematicFilterChipColors(), shape = CircleShape) }
                        }
                    }
                    item {
                        PageFilterRow {
                            PageFilterPill("Sort: $librarySort", listOf("Recent", "Title", "Progress").map { PageFilterOption(it, it) }, { librarySort = it ?: "Recent" }, Modifier.weight(1f))
                            IconButton(onClick = { libraryGrid = !libraryGrid }) { Icon(if (libraryGrid) Icons.Default.ViewList else Icons.Default.GridView, if (libraryGrid) "List view" else "Grid view") }
                        }
                    }
                    if (list.isEmpty()) item { MangaEmpty("Your reading list is empty", "Bookmark a title or start a chapter to see it here.") }
                    if (libraryGrid) items(list.chunked(columns)) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { entry -> MangaPoster(entry.title, Modifier.weight(1f), subtitle = entry.readLabel(), progress = if (entry.pageCount > 0) (entry.page + 1f) / entry.pageCount else null) { openSaved(entry, false) } }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    } else items(list, key = { it.title.identity() }) { entry ->
                        Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(18.dp)).clickable { openSaved(entry, false) }.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(entry.title.coverUrl, entry.title.title, modifier = Modifier.width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)), imageLoader = MangaNetwork.imageLoader(LocalContext.current), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(entry.title.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(entry.readLabel(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (entry.pageCount > 0) LinearProgressIndicator(progress = { ((entry.page + 1f) / entry.pageCount).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                if (verifiedSources.none { it.id == entry.title.sourceId }) Text("Provider unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, "Details")
                        }
                    }
                } else item { MangaEmpty("No downloaded chapters", "Offline chapter downloads are not available in this preview.") }
                item("source") {
                    TextButton(onClick = { attribution = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Info, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Sources & credits", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaHeading(label: String) { RailTitle(label, dragHandle = null) }

@Composable
private fun MangaEmpty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().glassCard().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Book, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MangaHomeCarousel(titles: List<MangaTitle>, modifier: Modifier, topInset: androidx.compose.ui.unit.Dp, onDetails: (MangaTitle) -> Unit, onRead: (MangaTitle) -> Unit) {
    if (titles.isEmpty()) return
    val device = LocalAppDeviceProfile.current
    val pager = rememberPagerState(pageCount = { titles.size })
    val dragging by pager.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    val height = topInset + if (device.isExpanded) 370.dp else if (device.isTablet) 340.dp else 378.dp
    LaunchedEffect(titles.map { it.identity() }, pager.settledPage, dragging) {
        if (!dragging && titles.size > 1) {
            delay(7000)
            pager.animateScrollToPage((pager.settledPage + 1) % titles.size)
        }
    }
    Box(modifier.height(height).clip(RoundedCornerShape(0.dp))) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { titles[it].identity() }) { index ->
            val title = titles[index]
            val art = remember { HazeState() }
            Box(Modifier.fillMaxSize().clickable { onDetails(title) }) {
                Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.Black, .70f to Color.Black, .82f to Color.Black.copy(alpha = .82f), .93f to Color.Black.copy(alpha = .30f), 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }.hazeSource(art)) {
                    AsyncImage(title.largeCoverUrl ?: title.coverUrl, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, imageLoader = MangaNetwork.imageLoader(LocalContext.current))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .26f), .30f to Color.Black.copy(alpha = .06f), .50f to Color.Black.copy(alpha = .20f), .68f to Color.Black.copy(alpha = .54f), .84f to Color.Black.copy(alpha = .68f), 1f to Color.Black.copy(alpha = .35f))))
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(if (device.isExpanded) .8f else 1f).padding(horizontal = device.pagePadding).padding(top = 8.dp, bottom = if (titles.size > 1) 64.dp else 40.dp)) {
                    Box(Modifier.fillMaxWidth(if (device.isExpanded) .6f else .78f).height(if (device.isExpanded) 68.dp else 64.dp), contentAlignment = Alignment.CenterStart) {
                        ShrinkingTitle(text = title.title, style = (if (device.isExpanded) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.Black, lineHeight = if (device.isExpanded) 42.sp else 28.sp), color = Color.White, maxLines = 2, minFontSize = 17.sp)
                    }
                    Text(listOf(title.metadata(), title.genres.take(3).joinToString(" · ")).filter { it.isNotBlank() }.joinToString(" · "), color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val summary = remember(title.description) { org.jsoup.Jsoup.parse(title.description).text() }
                    if (summary.isNotBlank()) Text(summary, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).fillMaxWidth(.86f))
                    Row(Modifier.padding(top = 12.dp, bottom = 2.dp).widthIn(max = 286.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeHeroAction("Read Now", Icons.Default.Book, true, art, { onRead(title) }, Modifier.weight(1f))
                        HomeHeroAction("Details", Icons.Default.Info, false, art, { onDetails(title) }, Modifier.weight(1f))
                    }
                }
            }
        }
        if (titles.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            heroVisibleIndicatorIndices(pager.currentPage, titles.size).forEach { i -> Box(Modifier.size(21.dp).clickable { scope.launch { pager.animateScrollToPage(i) } }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (i == pager.currentPage) 12.dp else 4.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (i == pager.currentPage) 1f else .35f)))
            } }
        }
    }
}

@Composable
private fun MangaDetailHero(title: MangaTitle, source: MangaSource) {
    val device = LocalAppDeviceProfile.current
    val height = if (device.isExpanded) 258.dp else if (device.isTablet) 224.dp else 195.dp
    Box(Modifier.fillMaxWidth().height(height)) {
        AsyncImage(title.largeCoverUrl ?: title.coverUrl, null, modifier = Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.White, .54f to Color.White, .75f to Color.White.copy(alpha = .78f), .92f to Color.White.copy(alpha = .22f), 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }, imageLoader = MangaNetwork.imageLoader(LocalContext.current), contentScale = ContentScale.Crop)
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = device.pagePadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            AsyncImage(title.coverUrl, title.title, modifier = Modifier.width(if (device.isTablet) 90.dp else 78.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(18.dp)), imageLoader = MangaNetwork.imageLoader(LocalContext.current), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ShrinkingTitle(text = title.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), maxLines = 3, minFontSize = 15.sp)
                Text(listOf(title.metadata(), source.displayName).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MangaPoster(title: MangaTitle, modifier: Modifier = Modifier, subtitle: String? = null, progress: Float? = null, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(.68f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            AsyncImage(title.coverUrl, title.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, imageLoader = MangaNetwork.imageLoader(LocalContext.current))
            if (progress != null) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        Text(title.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val label = subtitle ?: title.kind.label.takeUnless { title.kind == MangaKind.OTHER }.orEmpty()
        if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun MangaRail(label: String, titles: List<MangaTitle>, onClick: (MangaTitle) -> Unit) {
    val width = LocalAppDeviceProfile.current.posterWidth
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MangaHeading(label)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(titles, key = { it.identity() }) { title -> MangaPoster(title, Modifier.width(width)) { onClick(title) } }
        }
    }
}

@Composable
private fun MangaContinueCard(entry: MangaSaved, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(14.dp))) {
            AsyncImage(entry.title.coverUrl, entry.title.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, imageLoader = MangaNetwork.imageLoader(LocalContext.current))
            Box(Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = .58f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Book, "Continue reading", tint = Color.White)
            }
            if (entry.pageCount > 0) LinearProgressIndicator(progress = { ((entry.page + 1f) / entry.pageCount).coerceIn(0f, 1f) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        Text(entry.title.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(entry.readLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MangaDetails(title: MangaTitle, source: MangaSource, store: MangaReadingStore, saved: MangaSaved?, readImmediately: Boolean, onBack: () -> Unit, onBookmark: (Boolean) -> Unit, onChapter: (MangaChapter, List<MangaChapter>) -> Unit) {
    var chapters by remember(title) { mutableStateOf<List<MangaChapter>>(emptyList()) }
    var loading by remember(title) { mutableStateOf(true) }
    var error by remember(title) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var language by rememberSaveable(title.sourceId, title.id) { mutableStateOf(source.chapterLanguages.firstOrNull() ?: "und") }
    var ascending by rememberSaveable(title.id) { mutableStateOf(true) }
    var expanded by rememberSaveable(title.id) { mutableStateOf(false) }
    var details by remember(title) { mutableStateOf(title) }
    var tab by rememberSaveable(title.id) { mutableStateOf(MangaDetailTab.OVERVIEW) }
    var chapterQuery by rememberSaveable(title.id) { mutableStateOf("") }
    LaunchedEffect(title.id, language, retry) {
        chapters = emptyList(); loading = true; error = null
        resultOf { source.chapters(title.id, language) }.onSuccess { chapters = it; details = source.enrichedTitle(title) }.onFailure { error = healthError(it) }
        loading = false
    }
    val sorted = (if (ascending) chapters.reversed() else chapters).filter { chapterQuery.isBlank() || it.label().contains(chapterQuery, true) || it.title.contains(chapterQuery, true) }
    val resume = chapters.firstOrNull { it.matchesSaved(saved?.chapterId) }
    val first = resume ?: chapters.lastOrNull()
    LaunchedEffect(readImmediately, first?.id) { if (readImmediately && first != null) onChapter(first, chapters) }
    Scaffold(modifier = Modifier.fillMaxSize().glassAtmosphere(), containerColor = Color.Transparent,
        topBar = { PageTitleBar("", leading = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val tablet = maxWidth >= 720.dp
            val hero: @Composable () -> Unit = {
                MangaDetailHero(details, source)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { first?.let { onChapter(it, chapters) } }, enabled = first != null, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = CircleShape, colors = cinematicPrimaryButtonColors()) {
                        Icon(Icons.Default.Book, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (resume != null) "Continue" else "Read Now", maxLines = 1)
                    }
                    OutlinedButton(onClick = { onBookmark(saved?.bookmarked != true) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), colors = cinematicSecondaryButtonColors(), border = cinematicSecondaryButtonBorder(), shape = CircleShape) {
                        Icon(if (saved?.bookmarked == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (saved?.bookmarked == true) "Saved" else "My List", maxLines = 1)
                    }
                }
                SharedDetailTabs(tab, MangaDetailTab.entries, { tab = it }, { it.label })
            }
            val overview: @Composable () -> Unit = {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (details.genres.isNotEmpty()) GenreRow(details.genres.distinct())
                    Description(details.description)
                    Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Provider" to source.displayName, "Status" to details.status, "Authors" to details.authors.joinToString(", "), "Alternative titles" to details.alternativeTitles.joinToString(" · "), "Chapters" to chapters.size.takeIf { it > 0 }?.toString().orEmpty()).filter { it.second.isNotBlank() }.forEach { (name, value) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(.4f)); Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(.6f)) }
                        }
                    }
                    TextButton(onClick = { tab = MangaDetailTab.CHAPTERS }) { Text("View chapters"); Icon(Icons.Default.ChevronRight, null) }
                }
            }
            val chapterHeader: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MangaHeading("Chapters")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { ascending = !ascending }) { Icon(Icons.Default.Sort, null); Text(if (ascending) "Oldest" else "Newest") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        source.chapterLanguages.forEach { code ->
                            val label = if (code == "und") "All languages" else java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { code }
                            FilterChip(selected = language == code, onClick = { language = code }, label = { Text(label) }, colors = cinematicFilterChipColors(), shape = CircleShape)
                        }
                    }
                    PageSearchField(chapterQuery, { chapterQuery = it }, "Search chapters", "Clear chapter search")
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { MangaError(it) { retry++ } }
                    if (!loading && error == null && chapters.isEmpty()) Text("No readable chapters in this language.")
                }
            }
            val chapterRow: @Composable (MangaChapter) -> Unit = { entry ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChapter(entry, chapters) }.padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(listOf(entry.label(), entry.title.takeUnless { it.equals(entry.label(), true) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(entry.scanlationGroups.joinToString(", ").ifBlank { source.displayName }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (entry.matchesSaved(saved?.chapterId)) Text("Resume · Page ${(saved?.page ?: 0) + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(if (entry.matchesSaved(saved?.chapterId)) Icons.Default.Book else Icons.Default.ChevronRight, "Open chapter", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { hero() } }
                if (tab == MangaDetailTab.OVERVIEW) item { overview() }
                else {
                    item { Box(Modifier.padding(horizontal = 16.dp)) { chapterHeader() } }
                    items(sorted, key = { it.id }) { entry -> Box(Modifier.padding(horizontal = 16.dp)) { chapterRow(entry) } }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MangaReader(title: MangaTitle, chapter: MangaChapter, source: MangaSource, store: MangaReadingStore, chapters: List<MangaChapter>, health: MangaSourceHealthStore, onBack: () -> Unit, onChapter: (MangaChapter) -> Unit, previousChapter: (() -> Unit)? = null, nextChapter: (() -> Unit)? = null) {
    var pages by remember(chapter.id) { mutableStateOf<List<MangaPage>>(emptyList()) }
    var loading by remember(chapter.id) { mutableStateOf(true) }
    var error by remember(chapter.id) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val readerContext = LocalContext.current
    val readerPrefs = remember(readerContext) { readerContext.getSharedPreferences("manga_reader_options", android.content.Context.MODE_PRIVATE) }
    var mode by rememberSaveable { mutableStateOf(runCatching { ReadingMode.valueOf(readerPrefs.getString("mode", "").orEmpty()) }.getOrDefault(if (title.kind == MangaKind.MANHWA || title.kind == MangaKind.MANHUA) ReadingMode.WEBTOON else ReadingMode.RTL)) }
    LaunchedEffect(mode) { readerPrefs.edit().putString("mode", mode.name).apply() }
    var modeMenu by remember { mutableStateOf(false) }
    var chapterMenu by remember { mutableStateOf(false) }
    var controls by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("manga_reader_options", android.content.Context.MODE_PRIVATE) }
    var background by rememberSaveable { mutableStateOf(runCatching { ReaderBackground.valueOf(prefs.getString("background", "THEME").orEmpty()) }.getOrDefault(ReaderBackground.THEME)) }
    var ambient by rememberSaveable { mutableStateOf(prefs.getBoolean("ambient", false)) }
    var fullscreen by rememberSaveable { mutableStateOf(prefs.getBoolean("fullscreen", false)) }
    var awake by rememberSaveable { mutableStateOf(prefs.getBoolean("awake", true)) }
    var magnify by remember { mutableStateOf(false) }
    var brightness by rememberSaveable { mutableFloatStateOf(prefs.getFloat("brightness", -1f)) }
    var ambientStrength by rememberSaveable { mutableFloatStateOf(prefs.getFloat("ambient_strength", .55f).coerceIn(.15f, .85f)) }
    var pageMenu by remember { mutableStateOf(false) }
    val options = remember(prefs) { ReaderPreferences(prefs) }
    LaunchedEffect(options) { snapshotFlow { options.save() }.collect {} }
    var bookmarked by remember(title.id, chapter.id) { mutableStateOf(store.isChapterBookmarked(title, chapter.id)) }
    val toggleBookmark = { bookmarked = !bookmarked; store.bookmarkChapter(title, chapter.id, bookmarked) }
    val activity = context.findActivity()
    DisposableEffect(activity, options.rotation) {
        val previous = activity?.requestedOrientation
        options.rotation.orientation?.let { activity?.requestedOrientation = it }
        onDispose { if (previous != null) activity?.requestedOrientation = previous }
    }
    LaunchedEffect(brightness, ambientStrength) {
        prefs.edit().putFloat("brightness", brightness).putFloat("ambient_strength", ambientStrength).apply()
    }
    val haptic = LocalHapticFeedback.current
    val pageBackground = when (background) { ReaderBackground.THEME -> MaterialTheme.colorScheme.background; ReaderBackground.LIGHT -> Color.White; ReaderBackground.DARK -> Color(0xFF17171C); ReaderBackground.BLACK -> Color.Black }
    val chromeInk = when (background) { ReaderBackground.LIGHT -> Color(0xFF20212A); ReaderBackground.DARK, ReaderBackground.BLACK -> Color.White; else -> MaterialTheme.colorScheme.onSurface }
    val chromeSurface = if (background == ReaderBackground.THEME) MaterialTheme.colorScheme.surface else pageBackground
    LaunchedEffect(background, ambient, fullscreen, awake) {
        prefs.edit().putString("background", background.name).putBoolean("ambient", ambient).putBoolean("fullscreen", fullscreen).putBoolean("awake", awake).apply()
    }
    MangaReaderWindow(fullscreen, awake, brightness, background == ReaderBackground.LIGHT || (background == ReaderBackground.THEME && MaterialTheme.colorScheme.background.luminance() > .5f))
    BackHandler(!controls || magnify) { magnify = false; controls = true }
    var position by remember(chapter.id) { mutableIntStateOf(store.saved(title)?.takeIf { chapter.matchesSaved(it.chapterId) }?.page ?: 0) }
    LaunchedEffect(chapter.id, retry) {
        loading = true; error = null
        resultOf { source.pages(chapter.id) }.onSuccess { pages = it; if (it.isNotEmpty()) position = position.coerceIn(0, it.lastIndex) }.onFailure { error = healthError(it) }
        loading = false
    }
    if (chapterMenu) MangaChapterPicker(chapters, chapter, store, title, onDismiss = { chapterMenu = false }, onSelect = { chapterMenu = false; onChapter(it) })
    if (modeMenu) MangaReaderSettings(mode, background, ambient, fullscreen, magnify, awake, brightness, ambientStrength, options,
        onMode = { mode = it; magnify = false }, onBackground = { background = it }, onAmbient = { ambient = it },
        onFullscreen = { fullscreen = it; if (it) controls = false }, onMagnify = { magnify = it; controls = true },
        onAwake = { awake = it }, onBrightness = { brightness = it }, onAmbientStrength = { ambientStrength = it },
        onDismiss = { modeMenu = false })
    val barsPadding = if (fullscreen && !controls) Modifier else Modifier.statusBarsPadding().navigationBarsPadding()
    CompositionLocalProvider(LocalContentColor provides chromeInk) {
    Column(Modifier.fillMaxSize().background(pageBackground).then(barsPadding)) {
        AnimatedVisibility(visible = controls || loading || error != null) {
            Row(Modifier.fillMaxWidth().background(chromeSurface).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(title.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = chromeInk)
                    Text(chapter.label(), style = MaterialTheme.typography.labelMedium, color = chromeInk.copy(alpha = .75f))
                }
                IconButton(onClick = { chapterMenu = true }) { Icon(Icons.Default.List, "Select chapter", tint = chromeInk) }
                IconButton(onClick = { fullscreen = !fullscreen; haptic.performHapticFeedback(HapticFeedbackType.LongPress); controls = !fullscreen; magnify = false }) { Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Fullscreen", tint = chromeInk) }
                IconButton(onClick = { modeMenu = true }) { Icon(Icons.Default.Settings, "Reader settings", tint = chromeInk) }

            }
        }
        if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (error != null) Surface(color = MaterialTheme.colorScheme.surface) { MangaError(error.orEmpty()) { retry++ } }
        else if (pages.isEmpty()) Surface(color = MaterialTheme.colorScheme.surface) { Text("No pages available.", Modifier.padding(16.dp)) }
        else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val double = mode != ReadingMode.WEBTOON && when(options.layout) { ReaderLayout.SINGLE -> false; ReaderLayout.DOUBLE -> true; ReaderLayout.AUTO -> maxWidth > maxHeight }
            val groups = remember(pages.size, double, options.shift) { readerPageGroups(pages.size, double, options.shift) }
            key(chapter.id, mode, double, options.shift) {
            Column(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            val list = rememberLazyListState(initialFirstVisibleItemIndex = position.coerceIn(0, pages.lastIndex))
            val pager = rememberPagerState(initialPage = groups.indexOfFirst { position in it }.coerceAtLeast(0), pageCount = { groups.size })
            val jump: (Int) -> Unit = { target -> scope.launch {
                val index = target.coerceIn(0, pages.lastIndex)
                if (mode == ReadingMode.WEBTOON) list.scrollToItem(index) else {
                    val group = groups.indexOfFirst { index in it }.coerceAtLeast(0)
                    if (options.animate && kotlin.math.abs(group - pager.currentPage) == 1) pager.animateScrollToPage(group) else pager.scrollToPage(group)
                }
                position = index
            } }
            if (pageMenu) {
                var pageInput by remember { mutableStateOf((position + 1).toString()) }
                AlertDialog(onDismissRequest = { pageMenu = false }, title = { Text("Go to page") },
                    text = { OutlinedTextField(pageInput, { pageInput = it.filter(Char::isDigit).take(6) }, singleLine = true,
                        label = { Text("Page 1–" + pages.size) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) },
                    confirmButton = { TextButton(enabled = (pageInput.toIntOrNull() ?: 0) in 1..pages.size, onClick = { jump((pageInput.toIntOrNull() ?: 1) - 1); pageMenu = false }) { Text("Go") } },
                    dismissButton = { TextButton(onClick = { pageMenu = false }) { Text("Cancel") } })
            }
            LaunchedEffect(list, pager, mode) {
                snapshotFlow { if (mode == ReadingMode.WEBTOON) list.firstVisibleItemIndex else groups.getOrNull(pager.settledPage)?.firstOrNull() ?: 0 }.distinctUntilChanged().collect { index ->
                    position = index; store.progress(title, chapter.id, index, chapter.chapterNumber, pages.size)
                }
            }
            val turn: (Int) -> Unit = { direction ->
                if (mode == ReadingMode.WEBTOON) jump(position + direction)
                else groups.getOrNull(pager.currentPage + direction)?.firstOrNull()?.let(jump)
            }
            val currentTurn by rememberUpdatedState(turn)
            DisposableEffect(options.volume, modeMenu, chapterMenu, pageMenu) {
                if (options.volume && !modeMenu && !chapterMenu && !pageMenu) ReaderVolumeKeys.turn = { currentTurn(it) }
                onDispose { ReaderVolumeKeys.turn = null }
            }
            LaunchedEffect(options.autoHide, list, pager) {
                if (options.autoHide) snapshotFlow { if (mode == ReadingMode.WEBTOON) list.isScrollInProgress else pager.isScrollInProgress }.collect { if (it) controls = false }
            }
            val tapPage: (Float, Float) -> Unit = { x, y ->
                val dir = readerTapDirection(x, y, options.zones, mode == ReadingMode.RTL, options.invertZones)
                if (dir == 0) controls = !controls else turn(dir)
            }
            var lensCenter by remember { mutableStateOf(Offset.Unspecified) }
            val lensModifier = if (magnify && Build.VERSION.SDK_INT >= 28) Modifier.magnifier(sourceCenter = { lensCenter }, zoom = if (Build.VERSION.SDK_INT >= 29) 2f else Float.NaN).pointerInput(magnify) {
                detectDragGesturesAfterLongPress(onDragStart = { lensCenter = it }, onDragEnd = { lensCenter = Offset.Unspecified }, onDragCancel = { lensCenter = Offset.Unspecified }) { change, _ -> lensCenter = change.position; change.consume() }
            } else Modifier
            Box(Modifier.weight(1f).fillMaxWidth().then(lensModifier), contentAlignment = Alignment.Center) {
                if (ambient && Build.VERSION.SDK_INT >= 31) {
                    val current = pages.getOrNull(position)
                    if (current != null) {
                        val ambientRequest = remember(context, current) { ImageRequest.Builder(context).data(current.imageUrl).headers(current.headers.toHeaders()).size(160, 240).build() }
                        AsyncImage(ambientRequest, null, imageLoader = MangaNetwork.imageLoader(context), modifier = Modifier.fillMaxSize().blur(42.dp), contentScale = ContentScale.Crop, alpha = ambientStrength)
                    }
                }
                val pageModifier = Modifier.padding(horizontal = options.padding.dp)
                if (mode == ReadingMode.WEBTOON) LazyColumn(Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth().then(pageModifier).then(if (ambient) Modifier.padding(horizontal = 12.dp) else Modifier), state = list, verticalArrangement = Arrangement.spacedBy(options.gap.dp)) {
                    items(pages, key = { it.index }) { page -> ReaderImage(page, Modifier.fillMaxWidth(), fit = false, onTap = tapPage, magnifierActive = magnify, options = options, onLoaded = { health.record(source.id, SourceHealth.VERIFIED) }) }
                } else {
                    val spread: @Composable (Int) -> Unit = { index ->
                        val raw = groups[index]
                        val ordered = if ((mode == ReadingMode.RTL) xor options.reverseSpread) raw.reversed() else raw
                        Row(Modifier.fillMaxSize().then(pageModifier), horizontalArrangement = Arrangement.spacedBy(options.gap.dp), verticalAlignment = Alignment.CenterVertically) {
                            ordered.forEach { page ->
                                ReaderImage(pages[page], Modifier.weight(1f).fillMaxHeight(), fit = true, onTap = tapPage, magnifierActive = magnify, options = options, onLoaded = { health.record(source.id, SourceHealth.VERIFIED) })
                            }
                        }
                    }
                    if (mode == ReadingMode.VERTICAL) androidx.compose.foundation.pager.VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), pageContent = { spread(it) })
                    else HorizontalPager(state = pager, reverseLayout = mode == ReadingMode.RTL, modifier = Modifier.fillMaxSize()) { spread(it) }
                }
                if (options.pageNumber && !controls) Text("${position + 1} / ${pages.size}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = .65f)).padding(horizontal = 9.dp, vertical = 3.dp))

            }
            AnimatedVisibility(visible = controls) {
                val panel = Color(0xFF16171C)
                val ink = Color.White
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(22.dp)).background(panel)
                    .border(.5.dp, ink.copy(alpha = .18f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp)) {
                    var scrub by remember { mutableStateOf<Long?>(null) }
                    CompositionLocalProvider(LocalContentColor provides ink) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { turn(-1) }, enabled = position > 0) { Icon(Icons.Default.ChevronLeft, "Previous page") }
                            PlayerSeekBar(positionMs = position * 1000L, durationMs = pages.lastIndex * 1000L,
                                bufferedPositionMs = position * 1000L, onSeek = { jump((it / 1000f).roundToInt()) },
                                onScrubChange = { scrub = it }, modifier = Modifier.weight(1f), touchHeight = 28.dp)
                            IconButton(onClick = { turn(1) }, enabled = position < pages.lastIndex) { Icon(Icons.Default.ChevronRight, "Next page") }
                            TextButton(onClick = { pageMenu = true }) { Text("${(scrub?.let { (it / 1000f).roundToInt() } ?: position) + 1}/${pages.size}", color = ink) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(enabled = previousChapter != null, onClick = { previousChapter?.invoke() }) { Icon(Icons.Default.SkipPrevious, "Previous chapter") }
                            if (options.chapterShortcut) IconButton(onClick = { chapterMenu = true }) { Icon(Icons.Default.List, "Chapters") }
                            IconButton(onClick = { modeMenu = true }) { Icon(Icons.Default.Tune, "Reader settings") }
                            if (options.ambientShortcut && Build.VERSION.SDK_INT >= 31) IconButton(onClick = { ambient = !ambient }) { Icon(Icons.Default.Lightbulb, "Ambient light", tint = if (ambient) MaterialTheme.colorScheme.primaryContainer else ink) }
                            if (options.magnifierShortcut && Build.VERSION.SDK_INT >= 28) IconButton(onClick = { magnify = !magnify }) { Icon(Icons.Default.ZoomIn, "Magnifier", tint = if (magnify) MaterialTheme.colorScheme.primaryContainer else ink) }
                            if (options.bookmarkShortcut) IconButton(onClick = toggleBookmark) { Icon(if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Chapter bookmark") }
                            IconButton(enabled = nextChapter != null, onClick = { nextChapter?.invoke() }) { Icon(Icons.Default.SkipNext, "Next chapter") }
                        }
                        if (magnify) Text("Touch and hold, then drag to magnify · swipe normally to read", color = ink.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
                    }
                }
            }
            }
            }
        }
    }
    }
}

@Composable
private fun MangaReaderWindow(fullscreen: Boolean, awake: Boolean, brightness: Float, light: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val oldLight = controller?.isAppearanceLightStatusBars
        val oldNavLight = controller?.isAppearanceLightNavigationBars
        val oldBrightness = window?.attributes?.screenBrightness
        val wasAwake = (window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) ?: 0) != 0
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null && oldBrightness != null) window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
            if (oldLight != null) controller?.isAppearanceLightStatusBars = oldLight
            if (oldNavLight != null) controller?.isAppearanceLightNavigationBars = oldNavLight
            if (wasAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(activity, fullscreen, awake, brightness, light) {
        val window = activity?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply { screenBrightness = brightness }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        if (awake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaReaderSettings(mode: ReadingMode, background: ReaderBackground, ambient: Boolean, fullscreen: Boolean, magnify: Boolean, awake: Boolean, brightness: Float, ambientStrength: Float, options: ReaderPreferences, onMode: (ReadingMode) -> Unit, onBackground: (ReaderBackground) -> Unit, onAmbient: (Boolean) -> Unit, onFullscreen: (Boolean) -> Unit, onMagnify: (Boolean) -> Unit, onAwake: (Boolean) -> Unit, onBrightness: (Float) -> Unit, onAmbientStrength: (Float) -> Unit, onDismiss: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(ReaderSettingsTab.GENERAL) }
    PlayerModalSheet(title = "Reader settings", closeDescription = "Close reader settings", onDismiss = onDismiss) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderSettingsTab.entries.forEach { option -> FilterChip(selected = tab == option, onClick = { tab = option }, label = { Text(option.label) }, shape = CircleShape) }
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            when(tab) {
                ReaderSettingsTab.GENERAL -> {
                    ReaderChoices("Reading mode", ReadingMode.entries, mode, { it.label }, onMode)
                    ReaderChoices("Rotation", ReaderRotation.entries, options.rotation, { it.label }) { options.rotation = it }
                    ReaderChoices("Background", ReaderBackground.entries, background, { it.label }, onBackground)
                    ReaderToggle("Show page number", options.pageNumber) { options.pageNumber = it }
                    ReaderToggle("Keep screen awake", awake, onAwake)
                    ReaderToggle("Fullscreen", fullscreen, onFullscreen)
                    ReaderToggle("Hide controls when scrolling", options.autoHide) { options.autoHide = it }
                    ReaderToggle("Volume keys turn pages", options.volume) { options.volume = it }
                }
                ReaderSettingsTab.PAGES -> {
                    if (mode != ReadingMode.WEBTOON) {
                        ReaderChoices("Scale type", ReaderScale.entries, options.scale, { it.label }) { options.scale = it }
                        ReaderChoices("Page layout", ReaderLayout.entries, options.layout, { it.label }) { options.layout = it }
                        ReaderToggle("Shift double pages", options.shift) { options.shift = it }
                        ReaderToggle("Reverse double pages", options.reverseSpread) { options.reverseSpread = it }
                        ReaderToggle("Animate page transitions", options.animate) { options.animate = it }
                    }
                    ReaderChoices("Tap zones", ReaderTapZones.entries, options.zones, { it.label }) { options.zones = it }
                    ReaderToggle("Invert tap zones", options.invertZones) { options.invertZones = it }
                    Text("Side padding · ${options.padding.roundToInt()} dp", style = MaterialTheme.typography.titleSmall)
                    Slider(options.padding, { options.padding = it }, valueRange = 0f..32f)
                    Text("Page gap · ${options.gap.roundToInt()} dp", style = MaterialTheme.typography.titleSmall)
                    Slider(options.gap, { options.gap = it }, valueRange = 0f..32f)
                }
                ReaderSettingsTab.FILTERS -> {
                    ReaderToggle("Grayscale", options.grayscale) { options.grayscale = it }
                    ReaderToggle("Invert colours", options.invert) { options.invert = it }
                    Text("Warm reading filter", style = MaterialTheme.typography.titleSmall)
                    Slider(options.warm, { options.warm = it })
                    ReaderToggle("Custom brightness", brightness >= 0f) { onBrightness(if (it) .5f else -1f) }
                    if (brightness >= 0f) Slider(brightness, onBrightness, valueRange = .05f..1f)
                    if (Build.VERSION.SDK_INT >= 31) {
                        ReaderToggle("Ambient lighting", ambient, onAmbient)
                        if (ambient) Slider(ambientStrength, onAmbientStrength, valueRange = .15f..0.85f)
                    }
                }
                ReaderSettingsTab.TOOLBAR -> {
                    ReaderToggle("Chapter list", options.chapterShortcut) { options.chapterShortcut = it }
                    if (Build.VERSION.SDK_INT >= 31) ReaderToggle("Ambient shortcut", options.ambientShortcut) { options.ambientShortcut = it }
                    if (Build.VERSION.SDK_INT >= 28) {
                        ReaderToggle("Magnifier shortcut", options.magnifierShortcut) { options.magnifierShortcut = it }
                        ReaderToggle("Enable magnifier", magnify, onMagnify)
                    }
                    ReaderToggle("Chapter bookmark", options.bookmarkShortcut) { options.bookmarkShortcut = it }
                }
            }
        }
    }

}

@Composable
private fun ReaderToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = change)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ReaderChoices(label: String, choices: List<T>, selected: T, name: (T) -> String, change: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { choice -> FilterChip(selected = choice == selected, onClick = { change(choice) }, label = { Text(name(choice)) }, shape = CircleShape) }
    }
}

/** Paged reading delegates zoom and pan to Telephoto; vertical reading retains natural-height Coil pages. */
@Composable
private fun ReaderImage(page: MangaPage, modifier: Modifier, fit: Boolean, onTap: (Float, Float) -> Unit, magnifierActive: Boolean, options: ReaderPreferences, onLoaded: () -> Unit) {
    var retry by remember(page.imageUrl) { mutableIntStateOf(0) }
    val tap by rememberUpdatedState(onTap)
    val loaded by rememberUpdatedState(onLoaded)
    val context = LocalContext.current
    val request = remember(context, page, retry) { ImageRequest.Builder(context).data(page.imageUrl).headers(page.headers.toHeaders()).build() }
    var measured by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val imageModifier = modifier.then(Modifier.onSizeChanged { measured = it }).drawWithContent {
        if (!options.grayscale && !options.invert && options.warm == 0f) drawContent() else {
            val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(options.matrix()) }
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
            try { drawContent() } finally { drawContext.canvas.restore() }
        }
    }
    val handleTap: (Offset) -> Unit = { offset -> tap(offset.x / measured.width.coerceAtLeast(1), offset.y / measured.height.coerceAtLeast(1)) }
    key(page.imageUrl, retry) {
        if (fit && !magnifierActive) {
            val state = rememberZoomableImageState()
            LaunchedEffect(state.isImageDisplayed) { if (state.isImageDisplayed) loaded() }
            Box(imageModifier, contentAlignment = Alignment.Center) {
                ZoomableAsyncImage(
                    model = request,
                    imageLoader = MangaNetwork.imageLoader(context),
                    contentDescription = "Page ${page.index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    onClick = { offset -> handleTap(offset) },
                    contentScale = options.scale.scale,
                )
                if (!state.isImageDisplayed && !state.isPlaceholderDisplayed) {
                    var timedOut by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { delay(15000); timedOut = true }
                    if (timedOut) OutlinedButton(onClick = { retry++ }, colors = cinematicSecondaryButtonColors()) { Text("Retry page") }
                    else CircularProgressIndicator()
                }
            }
        } else {
            SubcomposeAsyncImage(
                model = request,
                imageLoader = MangaNetwork.imageLoader(context),
                onSuccess = { loaded() },
                contentDescription = "Page ${page.index + 1}",
                modifier = imageModifier.then(Modifier.pointerInput(Unit) { detectTapGestures(onTap = { handleTap(it) }) }),
                contentScale = if (fit) options.scale.scale else ContentScale.FillWidth,
                loading = { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                error = { Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { Button(onClick = { retry++ }) { Text("Retry page") } } },
            )
        }
    }
}

@Composable
private fun MangaError(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = retry) { Text("Try again") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaChapterPicker(chapters: List<MangaChapter>, current: MangaChapter, store: MangaReadingStore, title: MangaTitle, onDismiss: () -> Unit, onSelect: (MangaChapter) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var newest by rememberSaveable { mutableStateOf(true) }
    val groups = remember(chapters, query, newest) {
        val entries = if (newest) chapters else chapters.reversed()
        entries.filter { it.label().contains(query, true) || it.title.contains(query, true) }
            .groupBy { it.chapterNumber ?: "special:" + it.title.ifBlank { it.id } }.values.toList()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chapters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { newest = !newest }) { Text(if (newest) "Newest" else "Oldest") }
            }
            PageSearchField(query, { query = it }, "Find chapter", "Clear search")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                items(groups) { releases ->
                    val first = releases.first()
                    var expanded by remember(first.id) { mutableStateOf(releases.any { it.id == current.id }) }
                    val active = releases.any { it.id == current.id }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (active) glassSelectionColor() else Color.Transparent)
                        .clickable { if (releases.size == 1) onSelect(first) else expanded = !expanded }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(first.label(), style = MaterialTheme.typography.titleSmall)
                            Text(if (releases.size > 1) "${releases.size} releases" else first.scanlationGroups.joinToString(", ").ifBlank { first.language },
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (active) Icons.Default.CheckCircle else if (releases.size > 1) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null)
                    }
                    if (expanded && releases.size > 1) releases.forEach { release ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp).clip(RoundedCornerShape(10.dp)).clickable { onSelect(release) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(release.scanlationGroups.joinToString(", ").ifBlank { "Release " + (releases.indexOf(release) + 1) }, style = MaterialTheme.typography.bodyMedium)
                                Text(java.util.Locale.forLanguageTag(release.language).getDisplayLanguage().ifBlank { release.language }, style = MaterialTheme.typography.labelSmall)
                            }
                            var saved by remember(release.id) { mutableStateOf(store.isChapterBookmarked(title, release.id)) }
                            IconButton(onClick = { saved = !saved; store.bookmarkChapter(title, release.id, saved) }) { Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Bookmark chapter") }
                            if (release.id == current.id) Icon(Icons.Default.Check, "Current release", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                }
            }
        }
    }
}

/** Virtualized source catalog: only the chosen parser is asked for content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaSourcePicker(sources: List<MangaSource>, selected: String?, onDismiss: () -> Unit, onSelect: (MangaSource) -> Unit, onInfo: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("All") }
    val filtered = remember(sources, query, language) {
        sources.filter { source ->
            val tag = java.util.Locale.forLanguageTag(source.languageTag.replace('_', '-')).language
            (language == "All" || tag.isBlank() || tag == language) && (query.isBlank() || source.displayName.contains(query.trim(), true) || source.languageLabel().contains(query.trim(), true))
        }.sortedWith(compareBy<MangaSource> { it.id != selected }.thenBy { !it.languageTag.startsWith("en") }.thenBy { it.displayName.lowercase() })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Providers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onInfo) { Text("Credits") }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }
            PageSearchField(query, { query = it }, "Search providers", "Clear search")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("All" to "All", "en" to "EN", "ja" to "JP", "zh" to "ZH", "ko" to "KO")) { (code, label) ->
                    FilterChip(selected = language == code, onClick = { language = code }, label = { Text(label) }, colors = cinematicFilterChipColors(), shape = CircleShape)
                }
            }
            Text("${filtered.size} available providers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(filtered, key = { it.id }) { source ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (source.id == selected) glassSelectionColor() else Color.Transparent).clickable { onSelect(source) }.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoStories, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(19.dp)) }
                        Column(Modifier.weight(1f)) { Text(source.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(source.languageLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (source.id == selected) Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Default.ChevronRight, "Select provider", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (filtered.isEmpty()) item { Text("No available providers match this filter.", Modifier.padding(12.dp)) }
            }
            Text("Providers are automatically checked and updated.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun healthError(error: Throwable): String = when (classifySourceFailure(error)) {
    SourceHealth.RESTRICTED -> "This provider refused access or requires authorization. Try another source."
    SourceHealth.SERVER_TLS -> "The provider has a TLS/certificate error. Try another source; certificate verification remains enabled."
    else -> "The provider could not complete this request. Try another source or retry later."
}
