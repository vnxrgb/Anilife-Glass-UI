package com.anilili.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.anilili.data.profiles.Profiles

private enum class ReadingMode(val label: String) { WEBTOON("Vertical"), RTL("Right to left"), LTR("Left to right") }

private suspend fun <T> resultOf(block: suspend () -> T): Result<T> =
    try { Result.success(block()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile by Profiles.activeId.collectAsState()
    val store = remember(context, profile) { MangaReadingStore(context, profile) }
    val registry = remember { MangaSources(listOf(MangaDexSource())) }
    var sourceId by rememberSaveable { mutableStateOf("mangadex") }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<MangaTitle>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<MangaTitle?>(null) }
    var chapter by remember { mutableStateOf<MangaChapter?>(null) }
    var saved by remember(store) { mutableStateOf(store.all()) }
    var libraryOnly by rememberSaveable { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    val back: () -> Unit = {
        if (chapter != null) { chapter = null; saved = store.all() }
        else if (selected != null) { selected = null; saved = store.all() }
        else onBack()
    }
    BackHandler(selected != null || chapter != null) { back() }
    LaunchedEffect(sourceId, submitted, retry) {
        loading = true
        error = null
        results = emptyList()
        val source = registry.byId(sourceId)
        if (source != null) {
            resultOf { source.search(submitted) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "Unable to load titles" }
        } else error = "Source unavailable"
        loading = false
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (chapter != null) "Reader" else selected?.title ?: "Read", maxLines = 1) },
                navigationIcon = { TextButton(onClick = back) { Text("Back") } },
                actions = {
                    if (selected == null) {
                        Box {
                            TextButton(onClick = { sourceMenu = true }) { Text(registry.byId(sourceId)?.displayName ?: "Source") }
                            DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                                registry.all().forEach { provider ->
                                    DropdownMenuItem(text = { Text(provider.displayName) }, onClick = {
                                        sourceId = provider.id; sourceMenu = false; libraryOnly = false
                                    })
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        val title = selected
        val activeChapter = chapter
        val source = title?.let { registry.byId(it.sourceId) }
        if (title != null && source != null && activeChapter != null) {
            MangaReader(title, activeChapter, source, store, Modifier.padding(padding))
        } else if (title != null && source != null) {
            MangaDetails(title, source, store, saved.firstOrNull { it.title.id == title.id && it.title.sourceId == title.sourceId },
                onBookmark = { value -> store.bookmark(title, value); saved = store.all() },
                onChapter = { chapter = it }, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Manga, manhwa & manhua", style = MaterialTheme.typography.headlineSmall)
                    Text("Discover stories and pick up where you left off.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search titles") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp))
                        Button(onClick = { submitted = query.trim(); retry++; libraryOnly = false }) { Text("Search") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !libraryOnly, onClick = { libraryOnly = false }, label = { Text("Discover") })
                        FilterChip(selected = libraryOnly, onClick = { libraryOnly = true }, label = { Text("Reading library") })
                    }
                }
                if (libraryOnly) {
                    val library = saved.filter { it.bookmarked || it.chapterId != null }
                    if (library.isEmpty()) item { Text("Your bookmarks and reading history will appear here.") }
                    items(library, key = { it.title.sourceId + ":" + it.title.id }) { entry ->
                        MangaTile(entry.title, if (entry.chapterId != null) "Continue reading · Page ${entry.page + 1}" else "Bookmarked") { selected = entry.title }
                    }
                } else {
                    if (saved.any { it.chapterId != null }) {
                        item { Text("Continue reading", style = MaterialTheme.typography.titleLarge) }
                        items(saved.filter { it.chapterId != null }.take(4), key = { "resume:" + it.title.sourceId + ":" + it.title.id }) { entry ->
                            MangaTile(entry.title, "Page ${entry.page + 1}") { selected = entry.title }
                        }
                    }
                    item { Text(if (submitted.isBlank()) "Popular titles" else "Search results", style = MaterialTheme.typography.titleLarge) }
                    if (loading) item { CircularProgressIndicator() }
                    error?.let { message -> item { MangaError(message) { retry++ } } }
                    if (!loading && error == null && results.isEmpty()) item { Text("No titles found.") }
                    items(results, key = { it.sourceId + ":" + it.id }) { manga -> MangaTile(manga, "MangaDex") { selected = manga } }
                }
                item { Text("Catalog and chapters supplied by MangaDex and contributing scanlation groups. Availability varies by title.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun MangaTile(title: MangaTitle, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(title.coverUrl, title.title, Modifier.size(72.dp, 100.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text(title.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MangaDetails(title: MangaTitle, source: MangaSource, store: MangaReadingStore, saved: MangaSaved?, onBookmark: (Boolean) -> Unit, onChapter: (MangaChapter) -> Unit, modifier: Modifier = Modifier) {
    var chapters by remember(title) { mutableStateOf<List<MangaChapter>>(emptyList()) }
    var loading by remember(title) { mutableStateOf(true) }
    var error by remember(title) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var language by rememberSaveable(title.id) { mutableStateOf("en") }
    LaunchedEffect(title.id, language, retry) {
        chapters = emptyList(); loading = true; error = null
        resultOf { source.chapters(title.id, language) }.onSuccess { chapters = it }.onFailure { error = it.message ?: "Unable to load chapters" }
        loading = false
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(title.coverUrl, title.title, Modifier.size(120.dp, 168.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title.title, style = MaterialTheme.typography.headlineSmall)
                    Text(source.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { onBookmark(saved?.bookmarked != true) }) { Text(if (saved?.bookmarked == true) "Bookmarked" else "Bookmark") }
                    val resume = chapters.firstOrNull { it.id == saved?.chapterId }
                    if (resume != null) Button(onClick = { onChapter(resume) }) { Text("Resume") }
                }
            }
        }
        if (title.description.isNotBlank()) item { Text(title.description, style = MaterialTheme.typography.bodyMedium) }
        item {
            Text("Chapters", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("en" to "English", "es" to "Spanish", "fr" to "French").forEach { (code, label) ->
                    FilterChip(selected = language == code, onClick = { language = code }, label = { Text(label) })
                }
            }
        }
        if (loading) item { CircularProgressIndicator() }
        error?.let { message -> item { MangaError(message) { retry++ } } }
        if (!loading && error == null && chapters.isEmpty()) item { Text("No readable chapters in this language.") }
        items(chapters, key = { it.id }) { entry ->
            Card(onClick = { onChapter(entry) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(listOfNotNull(entry.chapterNumber?.let { "Chapter $it" }, entry.title.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "Chapter" }, style = MaterialTheme.typography.titleMedium)
                    Text(entry.scanlationGroups.joinToString(", ").ifBlank { source.displayName }, style = MaterialTheme.typography.bodySmall)
                    val read = store.saved(title)
                    if (read?.chapterId == entry.id) Text("Resume · Page ${read.page + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MangaReader(title: MangaTitle, chapter: MangaChapter, source: MangaSource, store: MangaReadingStore, modifier: Modifier = Modifier) {
    var pages by remember(chapter.id) { mutableStateOf<List<MangaPage>>(emptyList()) }
    var loading by remember(chapter.id) { mutableStateOf(true) }
    var error by remember(chapter.id) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(ReadingMode.WEBTOON) }
    var modeMenu by remember { mutableStateOf(false) }
    var position by remember(chapter.id) { mutableIntStateOf(store.saved(title)?.takeIf { it.chapterId == chapter.id }?.page ?: 0) }
    LaunchedEffect(chapter.id, retry) {
        loading = true; error = null
        resultOf { source.pages(chapter.id) }.onSuccess {
            pages = it
            if (it.isNotEmpty()) position = position.coerceIn(0, it.lastIndex)
        }.onFailure { error = it.message ?: "Unable to load pages" }
        loading = false
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Chapter ${chapter.chapterNumber.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                Text(chapter.scanlationGroups.joinToString(", ").ifBlank { source.displayName }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Box {
                TextButton(onClick = { modeMenu = true }) { Text(mode.label) }
                DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                    ReadingMode.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { mode = option; modeMenu = false }) }
                }
            }
        }
        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (error != null) {
            MangaError(error.orEmpty()) { retry++ }
        } else if (pages.isEmpty()) {
            Text("No pages available.", Modifier.padding(16.dp))
        } else {
            key(chapter.id, mode) {
                if (mode == ReadingMode.WEBTOON) {
                    val list = rememberLazyListState(initialFirstVisibleItemIndex = position.coerceIn(0, pages.lastIndex))
                    LaunchedEffect(list) {
                        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
                            position = index
                            store.progress(title, chapter.id, index)
                        }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color.Black), state = list) {
                        items(pages, key = { it.index }) { page -> ReaderImage(page, Modifier.fillMaxWidth(), fit = false) }
                    }
                } else {
                    val pager = rememberPagerState(initialPage = position.coerceIn(0, pages.lastIndex), pageCount = { pages.size })
                    LaunchedEffect(pager) {
                        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { index ->
                            position = index
                            store.progress(title, chapter.id, index)
                        }
                    }
                    HorizontalPager(state = pager, reverseLayout = mode == ReadingMode.RTL, modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) { index ->
                        ReaderImage(pages[index], Modifier.fillMaxSize(), fit = true)
                    }
                    val scope = rememberCoroutineScope()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(enabled = position > 0, onClick = { scope.launch { pager.animateScrollToPage((position - 1).coerceAtLeast(0)) } }) { Text("Previous") }
                        TextButton(enabled = position < pages.lastIndex, onClick = { scope.launch { pager.animateScrollToPage((position + 1).coerceAtMost(pages.lastIndex)) } }) { Text("Next") }
                    }
                }
            }
            LinearProgressIndicator(progress = { (position + 1f) / pages.size }, modifier = Modifier.fillMaxWidth())
            Text("Page ${position + 1} / ${pages.size}", Modifier.align(Alignment.CenterHorizontally).padding(6.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReaderImage(page: MangaPage, modifier: Modifier, fit: Boolean) {
    var scale by remember(page.imageUrl) { mutableFloatStateOf(1f) }
    var x by remember(page.imageUrl) { mutableFloatStateOf(0f) }
    var y by remember(page.imageUrl) { mutableFloatStateOf(0f) }
    var retry by remember(page.imageUrl) { mutableIntStateOf(0) }
    val zoom = if (fit) Modifier.pointerInput(page.imageUrl) {
        detectTransformGestures { _, pan, factor, _ ->
            scale = (scale * factor).coerceIn(1f, 4f)
            if (scale > 1f) { x += pan.x; y += pan.y } else { x = 0f; y = 0f }
        }
    } else Modifier
    key(retry) {
        SubcomposeAsyncImage(
            model = page.imageUrl, contentDescription = "Page ${page.index + 1}",
            modifier = modifier.then(zoom).clip(RoundedCornerShape(0.dp)).graphicsLayer { scaleX = scale; scaleY = scale; translationX = x; translationY = y },
            contentScale = if (fit) ContentScale.Fit else ContentScale.FillWidth,
            loading = { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
            error = { Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { Button(onClick = { retry++ }) { Text("Retry page") } } }
        )
    }
}

@Composable
private fun MangaError(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = retry) { Text("Try again") }
    }
}
