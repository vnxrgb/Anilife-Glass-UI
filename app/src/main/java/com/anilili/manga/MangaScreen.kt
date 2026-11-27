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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.anilili.ui.watch.findActivity
import com.anilili.ui.home.HomeHeroAction
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
private enum class ReadingMode(val label: String) { WEBTOON("Vertical"), RTL("Right to left"), LTR("Left to right") }
private enum class LibraryFilter(val label: String) { ALL("All"), READING("Reading"), SAVED("Bookmarked") }
private suspend fun <T> resultOf(block: suspend () -> T): Result<T> =
    try { Result.success(block()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

private fun MangaTitle.identity() = "$sourceId:$id"
private fun MangaChapter.label() = chapterNumber?.let { "Chapter $it" } ?: "Chapter"
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
                                FilterChip(selected = kind == option, onClick = { kind = option; genre = null }, label = { Text(option.label) }, colors = cinematicFilterChipColors(), shape = CircleShape)
                            }
                        }
                        val genres = results.flatMap { it.genres }.distinct().sorted()
                        if (browse) PageFilterRow {
                            PageFilterPill(genre ?: "Genres", listOf(PageFilterOption(null, "All genres")) + genres.map { PageFilterOption(it, it) }, { genre = it }, Modifier.weight(1f))
                            PageFilterPill(if (order == MangaOrder.POPULAR) "Popular" else "Recent", listOf(PageFilterOption("popular", "Popular"), PageFilterOption("recent", "Recently updated")), { value -> order = if (value == "recent") MangaOrder.RECENT else MangaOrder.POPULAR; genre = null }, Modifier.weight(1f))
                        } else if (genres.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = genre == null, onClick = { genre = null }, label = { Text("All genres") }, colors = cinematicFilterChipColors(), shape = CircleShape) }
                            items(genres) { option -> FilterChip(selected = genre == option, onClick = { genre = if (genre == option) null else option }, label = { Text(option) }, colors = cinematicFilterChipColors(), shape = CircleShape) }
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
private fun MangaHeading(label: String) {
    Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

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
    Box(modifier.height(height)) {
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
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = device.pagePadding).padding(bottom = if (titles.size > 1) 64.dp else 40.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(title.metadata(), title.genres.take(3).joinToString(" · ")).filter { it.isNotBlank() }.joinToString(" · "), color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val summary = remember(title.description) { org.jsoup.Jsoup.parse(title.description).text() }
                    if (summary.isNotBlank()) Text(summary, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 6.dp).widthIn(max = 286.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeHeroAction("Read Now", Icons.Default.Book, true, art, { onRead(title) }, Modifier.weight(1f))
                        HomeHeroAction("Details", Icons.Default.Info, false, art, { onDetails(title) }, Modifier.weight(1f))
                    }
                }
            }
        }
        if (titles.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            titles.indices.forEach { i -> Box(Modifier.size(24.dp).clickable { scope.launch { pager.animateScrollToPage(i) } }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (i == pager.currentPage) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (i == pager.currentPage) 1f else .35f)))
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
            Column(Modifier.weight(1f).glassCard(RoundedCornerShape(18.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
        topBar = { PageTitleBar("Details", leading = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MangaDetailTab.entries.forEach { option ->
                        val active = tab == option
                        Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape).background(if (active) glassSelectionColor() else glassPanelColor().copy(alpha = .38f)).border(1.dp, glassRimColor().copy(alpha = if (active) 1f else .45f), CircleShape).clickable { tab = option }, contentAlignment = Alignment.Center) {
                            Text(option.label, style = MaterialTheme.typography.labelLarge, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            val overview: @Composable () -> Unit = {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (details.genres.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(details.genres.distinct()) { genre -> Box(Modifier.glassCard(CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)) { Text(genre, style = MaterialTheme.typography.labelMedium) } }
                    }
                    Text("Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val synopsis = remember(details.description) { org.jsoup.Jsoup.parse(details.description).text() }
                    Text(synopsis.ifBlank { "No description available." }, maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (synopsis.length > 180) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Read more") }
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
    val haptic = LocalHapticFeedback.current
    val pageBackground = when (background) { ReaderBackground.THEME -> MaterialTheme.colorScheme.background; ReaderBackground.LIGHT -> Color.White; ReaderBackground.DARK -> Color(0xFF17171C); ReaderBackground.BLACK -> Color.Black }
    val chromeInk = when (background) { ReaderBackground.LIGHT -> Color(0xFF20212A); ReaderBackground.DARK, ReaderBackground.BLACK -> Color.White; else -> MaterialTheme.colorScheme.onSurface }
    val chromeSurface = if (background == ReaderBackground.THEME) MaterialTheme.colorScheme.surface else pageBackground
    LaunchedEffect(background, ambient, fullscreen, awake) {
        prefs.edit().putString("background", background.name).putBoolean("ambient", ambient).putBoolean("fullscreen", fullscreen).putBoolean("awake", awake).apply()
    }
    MangaReaderWindow(fullscreen, awake, background == ReaderBackground.LIGHT || (background == ReaderBackground.THEME && MaterialTheme.colorScheme.background.luminance() > .5f))
    BackHandler(fullscreen && !controls) { controls = true }
    var position by remember(chapter.id) { mutableIntStateOf(store.saved(title)?.takeIf { chapter.matchesSaved(it.chapterId) }?.page ?: 0) }
    LaunchedEffect(chapter.id, retry) {
        loading = true; error = null
        resultOf { source.pages(chapter.id) }.onSuccess { pages = it; if (it.isNotEmpty()) position = position.coerceIn(0, it.lastIndex) }.onFailure { error = healthError(it) }
        loading = false
    }
    if (chapterMenu) AlertDialog(onDismissRequest = { chapterMenu = false }, title = { Text("Chapters") },
        text = { LazyColumn(Modifier.heightIn(max = 400.dp)) { items(chapters, key = { it.id }) { entry ->
            TextButton(onClick = { chapterMenu = false; onChapter(entry) }, modifier = Modifier.fillMaxWidth()) { Text(entry.label() + if (entry.id == chapter.id) " · Reading" else "") }
        } } }, confirmButton = { TextButton(onClick = { chapterMenu = false }) { Text("Close") } })
    if (modeMenu) MangaReaderSettings(mode, background, ambient, fullscreen, magnify, awake, onMode = { mode = it }, onBackground = { background = it }, onAmbient = { ambient = it }, onFullscreen = { fullscreen = it }, onMagnify = { magnify = it }, onAwake = { awake = it }, onDismiss = { modeMenu = false })
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
                IconButton(onClick = { fullscreen = !fullscreen; haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (fullscreen) controls = false }) { Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Fullscreen", tint = chromeInk) }
                IconButton(onClick = { modeMenu = true }) { Icon(Icons.Default.Settings, "Reader settings", tint = chromeInk) }

            }
        }
        if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (error != null) Surface(color = MaterialTheme.colorScheme.surface) { MangaError(error.orEmpty()) { retry++ } }
        else if (pages.isEmpty()) Surface(color = MaterialTheme.colorScheme.surface) { Text("No pages available.", Modifier.padding(16.dp)) }
        else key(chapter.id, mode) {
            val scope = rememberCoroutineScope()
            val list = rememberLazyListState(initialFirstVisibleItemIndex = position.coerceIn(0, pages.lastIndex))
            val pager = rememberPagerState(initialPage = position.coerceIn(0, pages.lastIndex), pageCount = { pages.size })
            val jump: (Int) -> Unit = { target -> scope.launch { if (mode == ReadingMode.WEBTOON) list.scrollToItem(target) else pager.scrollToPage(target) } }
            LaunchedEffect(list, pager, mode) {
                snapshotFlow { if (mode == ReadingMode.WEBTOON) list.firstVisibleItemIndex else pager.settledPage }.distinctUntilChanged().collect { index ->
                    position = index; store.progress(title, chapter.id, index, chapter.chapterNumber, pages.size)
                }
            }
            var lensCenter by remember { mutableStateOf(Offset.Unspecified) }
            val lensModifier = if (magnify && Build.VERSION.SDK_INT >= 28) Modifier.magnifier(sourceCenter = { lensCenter }, zoom = if (Build.VERSION.SDK_INT >= 29) 2f else Float.NaN).pointerInput(magnify) {
                detectDragGestures(onDragStart = { lensCenter = it }, onDragEnd = { lensCenter = Offset.Unspecified }, onDragCancel = { lensCenter = Offset.Unspecified }) { change, _ -> lensCenter = change.position; change.consume() }
            } else Modifier
            Box(Modifier.weight(1f).fillMaxWidth().then(lensModifier), contentAlignment = Alignment.Center) {
                if (ambient && Build.VERSION.SDK_INT >= 31) {
                    val current = pages.getOrNull(position)
                    if (current != null) {
                        val ambientRequest = remember(context, current) { ImageRequest.Builder(context).data(current.imageUrl).headers(current.headers.toHeaders()).size(160, 240).build() }
                        AsyncImage(ambientRequest, null, imageLoader = MangaNetwork.imageLoader(context), modifier = Modifier.fillMaxSize().blur(42.dp), contentScale = ContentScale.Crop, alpha = .36f)
                    }
                }
                if (mode == ReadingMode.WEBTOON) LazyColumn(Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth().then(if (ambient) Modifier.padding(horizontal = 8.dp) else Modifier), state = list, userScrollEnabled = !magnify) {
                    items(pages, key = { it.index }) { page -> ReaderImage(page, Modifier.fillMaxWidth(), fit = false, onTap = { if (!magnify) controls = !controls }, magnifierActive = magnify, onLoaded = { health.record(source.id, SourceHealth.VERIFIED) }) }
                } else HorizontalPager(state = pager, reverseLayout = mode == ReadingMode.RTL, userScrollEnabled = !magnify, modifier = Modifier.fillMaxSize()) { index ->
                    ReaderImage(pages[index], Modifier.fillMaxSize(), fit = true, onTap = { if (!magnify) controls = !controls }, magnifierActive = magnify, onLoaded = { health.record(source.id, SourceHealth.VERIFIED) })
                }
            }
            AnimatedVisibility(visible = controls) {
                Column(Modifier.fillMaxWidth().background(chromeSurface).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { modeMenu = true }) { Icon(Icons.Default.MenuBook, "Reading mode", tint = chromeInk) }
                            IconButton(onClick = { background = ReaderBackground.entries[(background.ordinal + 1) % ReaderBackground.entries.size] }) { Icon(Icons.Default.DarkMode, "Change reader background", tint = chromeInk) }
                            if (Build.VERSION.SDK_INT >= 31) IconButton(onClick = { ambient = !ambient }) { Icon(Icons.Default.Lightbulb, "Ambient light", tint = if (ambient) MaterialTheme.colorScheme.primary else chromeInk) }
                            if (Build.VERSION.SDK_INT >= 28) IconButton(onClick = { magnify = !magnify; if (magnify) controls = true }) { Icon(Icons.Default.ZoomIn, "Magnifier", tint = if (magnify) MaterialTheme.colorScheme.primary else chromeInk) }
                        }
                        if (magnify) Text("Drag over the page to magnify. Turn off to scroll.", style = MaterialTheme.typography.labelSmall, color = chromeInk, modifier = Modifier.padding(horizontal = 8.dp))
                        var drag by remember { mutableStateOf<Float?>(null) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { jump((position - 1).coerceAtLeast(0)) }, enabled = position > 0) { Icon(Icons.Default.ChevronLeft, "Previous page") }
                            Slider(value = drag ?: position.toFloat(), onValueChange = { drag = it }, onValueChangeFinished = { drag?.let { jump(it.roundToInt().coerceIn(0, pages.lastIndex)) }; drag = null },
                                enabled = pages.size > 1, valueRange = 0f..pages.lastIndex.coerceAtLeast(1).toFloat(), modifier = Modifier.weight(1f))
                            IconButton(onClick = { jump((position + 1).coerceAtMost(pages.lastIndex)) }, enabled = position < pages.lastIndex) { Icon(Icons.Default.ChevronRight, "Next page") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = previousChapter != null, onClick = { previousChapter?.invoke() }) { Text("Prev chapter") }
                            Text("${(drag?.roundToInt() ?: position) + 1} / ${pages.size}", style = MaterialTheme.typography.labelMedium)
                            TextButton(enabled = nextChapter != null, onClick = { nextChapter?.invoke() }) { Text("Next chapter") }
                        }
                        Text(chapter.scanlationGroups.joinToString(", ").ifBlank { source.displayName }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun MangaReaderWindow(fullscreen: Boolean, awake: Boolean, light: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val oldLight = controller?.isAppearanceLightStatusBars
        val oldNavLight = controller?.isAppearanceLightNavigationBars
        val wasAwake = (window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) ?: 0) != 0
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (oldLight != null) controller?.isAppearanceLightStatusBars = oldLight
            if (oldNavLight != null) controller?.isAppearanceLightNavigationBars = oldNavLight
            if (wasAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(activity, fullscreen, awake, light) {
        val window = activity?.window ?: return@LaunchedEffect
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
private fun MangaReaderSettings(mode: ReadingMode, background: ReaderBackground, ambient: Boolean, fullscreen: Boolean, magnify: Boolean, awake: Boolean, onMode: (ReadingMode) -> Unit, onBackground: (ReaderBackground) -> Unit, onAmbient: (Boolean) -> Unit, onFullscreen: (Boolean) -> Unit, onMagnify: (Boolean) -> Unit, onAwake: (Boolean) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Reader settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Reading mode", style = MaterialTheme.typography.titleSmall)
            ReadingMode.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(18.dp)).clickable { onMode(option) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (option == ReadingMode.WEBTOON) Icons.Default.ViewStream else Icons.Default.MenuBook, null); Spacer(Modifier.width(10.dp)); Text(if (option == ReadingMode.WEBTOON) "Webtoon · Vertical" else "Comic · " + option.label, Modifier.weight(1f)); RadioButton(selected = mode == option, onClick = { onMode(option) })
                }
            }
            Text("Appearance", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { ReaderBackground.entries.forEach { option -> FilterChip(selected = background == option, onClick = { onBackground(option) }, label = { Text(option.label) }, shape = CircleShape, colors = cinematicFilterChipColors()) } }
            val toggles = buildList {
                add(Triple("Fullscreen", fullscreen, onFullscreen))
                add(Triple("Keep screen awake", awake, onAwake))
                if (Build.VERSION.SDK_INT >= 31) add(Triple("Ambient light", ambient, onAmbient))
                if (Build.VERSION.SDK_INT >= 28) add(Triple("Magnifier", magnify, onMagnify))
            }
            toggles.forEach { (name, checked, change) -> Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Switch(checked = checked, onCheckedChange = change) } }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = CircleShape, colors = cinematicPrimaryButtonColors()) { Text("Done") }
        }
    }
}

/** Paged reading delegates zoom and pan to Telephoto; vertical reading retains natural-height Coil pages. */
@Composable
private fun ReaderImage(page: MangaPage, modifier: Modifier, fit: Boolean, onTap: () -> Unit, magnifierActive: Boolean, onLoaded: () -> Unit) {
    var retry by remember(page.imageUrl) { mutableIntStateOf(0) }
    val tap by rememberUpdatedState(onTap)
    val loaded by rememberUpdatedState(onLoaded)
    val context = LocalContext.current
    val request = remember(context, page, retry) { ImageRequest.Builder(context).data(page.imageUrl).headers(page.headers.toHeaders()).build() }
    key(page.imageUrl, retry) {
        if (fit && !magnifierActive) {
            val state = rememberZoomableImageState()
            LaunchedEffect(state.isImageDisplayed) { if (state.isImageDisplayed) loaded() }
            Box(modifier, contentAlignment = Alignment.Center) {
                ZoomableAsyncImage(
                    model = request,
                    imageLoader = MangaNetwork.imageLoader(context),
                    contentDescription = "Page ${page.index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    onClick = { tap() },
                    contentScale = ContentScale.Fit,
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
                modifier = modifier.then(if (magnifierActive) Modifier else Modifier.pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }),
                contentScale = if (fit) ContentScale.Fit else ContentScale.FillWidth,
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

/** Virtualized source catalog: only the chosen parser is asked for content. */
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Providers") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageSearchField(query, { query = it }, "Search providers", "Clear search")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("All" to "All", "en" to "EN", "ja" to "JP", "zh" to "ZH", "ko" to "KO")) { (code, label) ->
                    FilterChip(selected = language == code, onClick = { language = code }, label = { Text(label) }, colors = cinematicFilterChipColors(), shape = CircleShape)
                }
            }
            Text("${filtered.size} available providers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(filtered, key = { it.id }) { source ->
                    Row(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable { onSelect(source) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).glassCard(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(source.displayName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f)) { Text(source.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(source.languageLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (source.id == selected) Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Default.ChevronRight, "Select provider", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (filtered.isEmpty()) item { Text("No available providers match this filter.", Modifier.padding(12.dp)) }
            }
            Text("Providers are automatically checked and updated.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }, dismissButton = { TextButton(onClick = onInfo) { Text("Credits") } })
}

private fun healthError(error: Throwable): String = when (classifySourceFailure(error)) {
    SourceHealth.RESTRICTED -> "This provider refused access or requires authorization. Try another source."
    SourceHealth.SERVER_TLS -> "The provider has a TLS/certificate error. Try another source; certificate verification remains enabled."
    else -> "The provider could not complete this request. Try another source or retry later."
}
