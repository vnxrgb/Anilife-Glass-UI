package com.anilili.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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

private enum class ReaderBackground(val label: String) { THEME("Match app"), LIGHT("White"), BLACK("Black") }
private enum class ReadingMode(val label: String) { WEBTOON("Vertical"), RTL("Right to left"), LTR("Left to right") }
private enum class LibraryFilter(val label: String) { ALL("All"), READING("Reading"), SAVED("Bookmarked") }
private suspend fun <T> resultOf(block: suspend () -> T): Result<T> =
    try { Result.success(block()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

private fun MangaTitle.identity() = "$sourceId:$id"
private fun MangaChapter.label() = chapterNumber?.let { "Chapter $it" } ?: "Chapter"
private fun MangaTitle.metadata() = listOfNotNull(kind.label.takeUnless { kind == MangaKind.OTHER }, year?.toString(), status.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }).joinToString(" · ")
private fun MangaSaved.readLabel() = listOfNotNull(chapterNumber?.let { "Chapter $it" }, if (chapterId != null) "Page ${page + 1}" else "Bookmarked").joinToString(" · ")

@Composable
fun MangaScreen(onBack: () -> Unit, section: MangaSection = MangaSection.HOME) {
    val context = LocalContext.current
    val profile by Profiles.activeId.collectAsState()
    val store = remember(context, profile) { MangaReadingStore(context, profile) }
    key(profile, section) { MangaDestination(store, section, onBack) }
}

@Composable
private fun MangaDestination(store: MangaReadingStore, section: MangaSection, onBack: () -> Unit) {
    val registry = remember { MangaSources(listOf(MangaDexSource())) }
    var sourceId by rememberSaveable { mutableStateOf("mangadex") }
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
    var chapter by remember { mutableStateOf<MangaChapter?>(null) }
    var availableChapters by remember { mutableStateOf<List<MangaChapter>>(emptyList()) }
    var saved by remember(store) { mutableStateOf(store.all()) }
    var libraryFilter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var sourceMenu by remember { mutableStateOf(false) }
    var attribution by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val provider = registry.byId(sourceId)
    val home = section == MangaSection.HOME
    val browse = section == MangaSection.BROWSE
    val library = section == MangaSection.LIBRARY
    val device = LocalAppDeviceProfile.current
    val bottom = LocalAppChromeBottomInset.current
    val revealChrome = LocalAppChromeReveal.current
    val listState = rememberLazyListState()
    val back: () -> Unit = {
        if (chapter != null) { chapter = null; saved = store.all() }
        else if (selected != null) { selected = null; saved = store.all(); revealChrome() }
        else onBack()
    }
    BackHandler(selected != null || chapter != null) { back() }
    LaunchedEffect(Unit) { saved = store.all() }
    LaunchedEffect(sourceId, query, kind, order, retry, section) {
        if (!home && !browse) { loading = false; return@LaunchedEffect }
        if (browse && query.isNotBlank()) delay(350)
        listState.scrollToItem(0)
        catalogGeneration++; moreLoading = false
        loading = true; error = null; results = emptyList(); recent = emptyList(); offset = 0; hasMore = false
        if (provider == null) error = "Source unavailable"
        else resultOf { provider.catalog(if (browse) query.trim() else "", kind, order, limit = 24) }
            .onSuccess { results = it; offset = it.size; hasMore = it.size == 24 }
            .onFailure { error = it.message ?: "Unable to load titles" }
        loading = false
        if (home && provider != null) {
            delay(350)
            resultOf { provider.catalog("", kind, MangaOrder.RECENT, limit = 12) }.onSuccess { recent = it }
        }
    }
    if (attribution) AlertDialog(onDismissRequest = { attribution = false }, title = { Text("Built-in sources") },
        text = { Text("Catalog and chapters supplied by MangaDex and contributing scanlation groups. Availability varies by title. Chapter credits are shown in the details and reader.") },
        confirmButton = { TextButton(onClick = { attribution = false }) { Text("Close") } })
    val title = selected
    val activeChapter = chapter
    val titleSource = title?.let { registry.byId(it.sourceId) }
    if (title != null && titleSource != null) {
        AppChromeTakeoverEffect()
        if (activeChapter != null) {
            val distinct = availableChapters.distinctBy { it.chapterNumber ?: it.id }
            val index = distinct.indexOfFirst { it.id == activeChapter.id || it.chapterNumber != null && it.chapterNumber == activeChapter.chapterNumber }
            MangaReader(title, activeChapter, titleSource, store, availableChapters, onBack = back,
                onChapter = { chapter = it },
                previousChapter = distinct.getOrNull(index + 1)?.takeIf { index >= 0 }?.let { entry -> { chapter = entry } },
                nextChapter = distinct.getOrNull(index - 1)?.let { entry -> { chapter = entry } })
        } else {
            MangaDetails(title, titleSource, store, saved.firstOrNull { it.title.identity() == title.identity() }, onBack = back,
                onBookmark = { value -> store.bookmark(title, value); saved = store.all() },
                onChapter = { entry, entries -> availableChapters = entries; chapter = entry })
        }
        return
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().glassAtmosphere(), containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            ScrollAwareTopBar {
                Column(Modifier.fillMaxWidth().browseHeaderGlass(null)) {
                    if (home) {
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = device.pagePadding, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AnililiWordmark(Modifier.height(30.dp))
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { sourceMenu = true }) { Icon(Icons.Default.Storage, "Built-in sources") }
                        }
                    } else PageTitleBar(when (section) {
                        MangaSection.BROWSE -> "Browse"
                        MangaSection.LIBRARY -> "My List"
                        else -> "Downloads"
                    }, trailing = { if (browse) IconButton(onClick = { sourceMenu = true }) { Icon(Icons.Default.Storage, "Built-in sources") } })
                    ContentModeSwitcher()
                    Box {
                        DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                            registry.all().forEach { source -> DropdownMenuItem(text = { Text(source.displayName) }, onClick = { sourceId = source.id; sourceMenu = false }) }
                            DropdownMenuItem(text = { Text("Source information") }, onClick = { sourceMenu = false; attribution = true })
                        }
                    }
                }
            }
        }
    ) { padding ->
        val side = device.pagePadding
        val filtered = results.filter { genre == null || genre in it.genres }
        val list = saved.filter { entry -> when (libraryFilter) {
            LibraryFilter.ALL -> entry.bookmarked || entry.chapterId != null
            LibraryFilter.READING -> entry.chapterId != null
            LibraryFilter.SAVED -> entry.bookmarked
        } }.filter { query.isBlank() || it.title.title.contains(query, ignoreCase = true) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = ((maxWidth.value - side.value * 2 + 10) / (device.gridMinWidth.value + 10)).toInt().coerceIn(2, 7)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, top = padding.calculateTopPadding() + 8.dp,
                    bottom = maxOf(padding.calculateBottomPadding(), bottom + AppChromeListBottomGap)),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (home && results.isNotEmpty()) {
                    item("featured") {
                        val features = results.take(5)
                        val pager = rememberPagerState(pageCount = { features.size })
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { index ->
                                MangaHero(features[index], "Details", onRead = { selected = features[index] })
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                features.indices.forEach { i ->
                                    Box(Modifier.padding(3.dp).size(if (i == pager.currentPage) 14.dp else 5.dp, 5.dp)
                                        .clip(CircleShape).background(if (i == pager.currentPage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .32f)))
                                }
                            }
                        }
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
                        if (browse) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf(MangaOrder.POPULAR, MangaOrder.RECENT)) { option ->
                                    FilterChip(selected = order == option, onClick = { order = option; genre = null }, label = { Text(if (option == MangaOrder.POPULAR) "Popular" else "Recently updated") }, colors = cinematicFilterChipColors(), shape = CircleShape)
                                }
                            }
                            val genres = results.flatMap { it.genres }.distinct().sorted()
                            if (genres.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { FilterChip(selected = genre == null, onClick = { genre = null }, label = { Text("Genres in results") }, colors = cinematicFilterChipColors(), shape = CircleShape) }
                                items(genres) { option -> FilterChip(selected = genre == option, onClick = { genre = if (genre == option) null else option }, label = { Text(option) }, colors = cinematicFilterChipColors(), shape = CircleShape) }
                            }
                        }
                    }
                }
                if (home && saved.any { it.chapterId != null }) item("continue") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MangaHeading("Continue Reading")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(saved.filter { it.chapterId != null }, key = { it.title.identity() }) { entry ->
                                MangaContinueCard(entry, Modifier.width(170.dp)) { selected = results.firstOrNull { it.identity() == entry.title.identity() } ?: entry.title }
                            }
                        }
                    }
                }
                if (home || browse) {
                    if (loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    error?.let { message -> item { MangaError(message) { retry++ } } }
                    if (!loading && error == null && filtered.isEmpty()) item { Text("No titles found.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (home && filtered.isNotEmpty()) item("popular") { MangaRail("Popular Now", filtered.take(15), onClick = { selected = it }) }
                    if (home && recent.isNotEmpty()) item("recent") { MangaRail("Recently Updated", recent, onClick = { selected = it }) }
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
                                    }.onFailure { if (generation == catalogGeneration) error = it.message ?: "Unable to load more" }
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
                    if (list.isEmpty()) item { MangaEmpty("Your reading list is empty", "Bookmark a title or start a chapter to see it here.") }
                    items(list.chunked(columns)) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { entry -> MangaPoster(entry.title, Modifier.weight(1f), subtitle = entry.readLabel()) { selected = entry.title } }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
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
private fun MangaHero(title: MangaTitle, action: String, enabled: Boolean = true, onRead: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        AsyncImage(title.coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .08f), Color.Black.copy(alpha = .82f), Color.Black.copy(alpha = .95f)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(title.metadata(), title.genres.take(2).joinToString(" · ")).filter { it.isNotBlank() }.joinToString(" · "), color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Button(onClick = onRead, enabled = enabled, colors = cinematicPrimaryButtonColors(true), border = cinematicPrimaryButtonBorder(true), shape = CircleShape) {
                Icon(Icons.Default.Book, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(action)
            }
        }
    }
}

@Composable
private fun MangaPoster(title: MangaTitle, modifier: Modifier = Modifier, subtitle: String? = null, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        AsyncImage(title.coverUrl, title.title, Modifier.fillMaxWidth().aspectRatio(.68f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer), contentScale = ContentScale.Crop)
        Text(title.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle ?: title.kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(14.dp))) {
            AsyncImage(entry.title.coverUrl, entry.title.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = .58f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, "Continue reading", tint = Color.White)
            }
            if (entry.pageCount > 0) LinearProgressIndicator(progress = { ((entry.page + 1f) / entry.pageCount).coerceIn(0f, 1f) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        Text(entry.title.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(entry.readLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MangaDetails(title: MangaTitle, source: MangaSource, store: MangaReadingStore, saved: MangaSaved?, onBack: () -> Unit, onBookmark: (Boolean) -> Unit, onChapter: (MangaChapter, List<MangaChapter>) -> Unit) {
    var chapters by remember(title) { mutableStateOf<List<MangaChapter>>(emptyList()) }
    var loading by remember(title) { mutableStateOf(true) }
    var error by remember(title) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var language by rememberSaveable(title.id) { mutableStateOf("en") }
    var ascending by rememberSaveable(title.id) { mutableStateOf(true) }
    var expanded by rememberSaveable(title.id) { mutableStateOf(false) }
    LaunchedEffect(title.id, language, retry) {
        chapters = emptyList(); loading = true; error = null
        resultOf { source.chapters(title.id, language) }.onSuccess { chapters = it }.onFailure { error = it.message ?: "Unable to load chapters" }
        loading = false
    }
    val sorted = if (ascending) chapters.reversed() else chapters
    val resume = chapters.firstOrNull { it.id == saved?.chapterId }
    val first = resume ?: chapters.lastOrNull()
    Scaffold(modifier = Modifier.fillMaxSize().glassAtmosphere(), containerColor = Color.Transparent,
        topBar = { PageTitleBar("Details", leading = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val tablet = maxWidth >= 720.dp
            val overview: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MangaHero(title, if (resume != null) "Continue reading" else "Read now", enabled = first != null) { first?.let { onChapter(it, chapters) } }
                    OutlinedButton(onClick = { onBookmark(saved?.bookmarked != true) }, modifier = Modifier.fillMaxWidth(), colors = cinematicSecondaryButtonColors(), border = cinematicSecondaryButtonBorder(), shape = CircleShape) {
                        Icon(if (saved?.bookmarked == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null)
                        Spacer(Modifier.width(8.dp)); Text(if (saved?.bookmarked == true) "Bookmarked" else "Add to My List")
                    }
                    Text("Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(title.description.ifBlank { "No description available." }, maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (title.description.length > 180) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Read more") }
                    Text("Source · ${source.displayName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        listOf("en" to "English", "es" to "Spanish", "fr" to "French").forEach { (code, label) ->
                            FilterChip(selected = language == code, onClick = { language = code }, label = { Text(label) }, colors = cinematicFilterChipColors(), shape = CircleShape)
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { MangaError(it) { retry++ } }
                    if (!loading && error == null && chapters.isEmpty()) Text("No readable chapters in this language.")
                }
            }
            val chapterRow: @Composable (MangaChapter) -> Unit = { entry ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChapter(entry, chapters) }.padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(listOf(entry.label(), entry.title).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(entry.scanlationGroups.joinToString(", ").ifBlank { source.displayName }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (saved?.chapterId == entry.id) Text("Resume · Page ${saved.page + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(if (saved?.chapterId == entry.id) Icons.Default.PlayArrow else Icons.Default.ChevronRight, "Open chapter", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
            if (tablet) Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { overview() }
                LazyColumn(Modifier.weight(1.3f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { chapterHeader() }; items(sorted, key = { it.id }) { chapterRow(it) }
                }
            } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { overview() }; item { chapterHeader() }; items(sorted, key = { it.id }) { chapterRow(it) }
            }
        }
    }
}

@Composable
private fun MangaReader(title: MangaTitle, chapter: MangaChapter, source: MangaSource, store: MangaReadingStore, chapters: List<MangaChapter>, onBack: () -> Unit, onChapter: (MangaChapter) -> Unit, previousChapter: (() -> Unit)? = null, nextChapter: (() -> Unit)? = null) {
    var pages by remember(chapter.id) { mutableStateOf<List<MangaPage>>(emptyList()) }
    var loading by remember(chapter.id) { mutableStateOf(true) }
    var error by remember(chapter.id) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(if (title.kind == MangaKind.MANHWA || title.kind == MangaKind.MANHUA) ReadingMode.WEBTOON else ReadingMode.RTL) }
    var modeMenu by remember { mutableStateOf(false) }
    var chapterMenu by remember { mutableStateOf(false) }
    var controls by rememberSaveable { mutableStateOf(true) }
    var background by rememberSaveable { mutableStateOf(ReaderBackground.THEME) }
    val pageBackground = when (background) { ReaderBackground.THEME -> MaterialTheme.colorScheme.background; ReaderBackground.LIGHT -> Color.White; ReaderBackground.BLACK -> Color.Black }
    var position by remember(chapter.id) { mutableIntStateOf(store.saved(title)?.takeIf { it.chapterId == chapter.id }?.page ?: 0) }
    LaunchedEffect(chapter.id, retry) {
        loading = true; error = null
        resultOf { source.pages(chapter.id) }.onSuccess { pages = it; if (it.isNotEmpty()) position = position.coerceIn(0, it.lastIndex) }.onFailure { error = it.message ?: "Unable to load pages" }
        loading = false
    }
    if (chapterMenu) AlertDialog(onDismissRequest = { chapterMenu = false }, title = { Text("Chapters") },
        text = { LazyColumn(Modifier.heightIn(max = 400.dp)) { items(chapters, key = { it.id }) { entry ->
            TextButton(onClick = { chapterMenu = false; onChapter(entry) }, modifier = Modifier.fillMaxWidth()) { Text(entry.label() + if (entry.id == chapter.id) " · Reading" else "") }
        } } }, confirmButton = { TextButton(onClick = { chapterMenu = false }) { Text("Close") } })
    Column(Modifier.fillMaxSize().background(pageBackground).statusBarsPadding().navigationBarsPadding()) {
        AnimatedVisibility(visible = controls || loading || error != null) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(title.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Text(chapter.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { chapterMenu = true }) { Icon(Icons.Default.List, "Select chapter", tint = MaterialTheme.colorScheme.onSurface) }
                Box {
                    IconButton(onClick = { modeMenu = true }) { Icon(Icons.Default.Settings, "Reader settings", tint = MaterialTheme.colorScheme.onSurface) }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        ReadingMode.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label + if (mode == option) " ✓" else "") }, onClick = { mode = option; modeMenu = false }) }
                        HorizontalDivider()
                        ReaderBackground.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label + if (background == option) " ✓" else "") }, onClick = { background = option; modeMenu = false }) }
                    }
                }
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
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (mode == ReadingMode.WEBTOON) LazyColumn(Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth(), state = list) {
                    items(pages, key = { it.index }) { page -> ReaderImage(page, Modifier.fillMaxWidth(), fit = false, onTap = { controls = !controls }) }
                } else HorizontalPager(state = pager, reverseLayout = mode == ReadingMode.RTL, modifier = Modifier.fillMaxSize()) { index ->
                    ReaderImage(pages[index], Modifier.fillMaxSize(), fit = true, onTap = { controls = !controls })
                }
            }
            AnimatedVisibility(visible = controls) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
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

/** Paged reading delegates zoom and pan to Telephoto; vertical reading retains natural-height Coil pages. */
@Composable
private fun ReaderImage(page: MangaPage, modifier: Modifier, fit: Boolean, onTap: () -> Unit) {
    var retry by remember(page.imageUrl) { mutableIntStateOf(0) }
    val tap by rememberUpdatedState(onTap)
    val context = LocalContext.current
    key(page.imageUrl, retry) {
        if (fit) {
            val state = rememberZoomableImageState()
            val request = remember(context, page.imageUrl, retry) {
                ImageRequest.Builder(context).data(page.imageUrl).build()
            }
            Box(modifier, contentAlignment = Alignment.Center) {
                ZoomableAsyncImage(
                    model = request,
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
                model = page.imageUrl,
                contentDescription = "Page ${page.index + 1}",
                modifier = modifier.pointerInput(Unit) { detectTapGestures(onTap = { tap() }) },
                contentScale = ContentScale.FillWidth,
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
