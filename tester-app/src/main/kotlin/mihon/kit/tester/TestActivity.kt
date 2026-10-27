package mihon.kit.tester

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatActivity
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.kit.MihonKit
import mihon.kit.download.DownloadStatus
import mihon.kit.model.LoadedExtension
import mihon.kit.ui.ExtensionFilterBottomSheet
import mihon.kit.ui.ExtensionPreferencesScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ArrowDownward
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.KeyboardArrowLeft
import mihon.icons.materialsymbols.rounded.Person
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.rounded.Settings
import java.io.File

class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF121212))) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var allExtensions by remember { mutableStateOf<List<LoadedExtension>>(emptyList()) }
    var loadedExtension by remember { mutableStateOf<LoadedExtension?>(null) }
    var selectedManga by remember { mutableStateOf<SManga?>(null) }
    var selectedChapter by remember { mutableStateOf<SChapter?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                MihonKit.initialize(context)
            } catch (_: Throwable) {}
        }
        try {
            val installed = MihonKit.loadAllInstalledExtensions(context)
            allExtensions = installed
            loadedExtension = installed.firstOrNull { it.packageName.contains("asmhentai") } ?: installed.firstOrNull()
        } catch (_: Throwable) {}
    }

    var showPreferences by remember { mutableStateOf(false) }

    val sourceId = loadedExtension?.sources?.firstOrNull()?.id ?: 0L

    when {
        showPreferences -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = "${loadedExtension?.name ?: "Extension"} Settings") },
                        navigationIcon = {
                            IconButton(onClick = { showPreferences = false }) {
                                Icon(MaterialSymbols.Rounded.KeyboardArrowLeft, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E)),
                    )
                },
            ) { padding ->
                BackHandler { showPreferences = false }
                ExtensionPreferencesScreen(
                    sourceId = sourceId,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        selectedChapter != null -> {
            ReaderScreen(
                sourceId = sourceId,
                chapter = selectedChapter!!,
                onBack = { selectedChapter = null },
            )
        }
        selectedManga != null -> {
            MangaDetailsScreen(
                sourceId = sourceId,
                manga = selectedManga!!,
                onBack = { selectedManga = null },
                onSelectChapter = { selectedChapter = it },
                onSearchTag = { query ->
                    searchQuery = query
                    selectedManga = null
                },
            )
        }
        else -> {
            MihonBrowseScreen(
                loadedExtension = loadedExtension,
                allExtensions = allExtensions,
                searchQuery = searchQuery,
                onSearchQueryChanged = { searchQuery = it },
                onSelectManga = { selectedManga = it },
                onSelectExtension = { ext ->
                    loadedExtension = ext
                    searchQuery = ""
                },
                onOpenPreferences = { showPreferences = true },
            )
        }
    }
}

// =========================================================================
// 1. BROWSE SCREEN
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MihonBrowseScreen(
    loadedExtension: LoadedExtension?,
    allExtensions: List<LoadedExtension>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSelectManga: (SManga) -> Unit,
    onSelectExtension: (LoadedExtension) -> Unit,
    onOpenPreferences: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingNextPage by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mangas by remember { mutableStateOf<List<SManga>>(emptyList()) }
    var currentPage by remember { mutableStateOf(1) }
    var hasNextPage by remember { mutableStateOf(true) }
    val gridState = rememberLazyGridState()
    var filterList by remember { mutableStateOf<FilterList?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(searchQuery.isNotBlank()) }
    var showExtensionMenu by remember { mutableStateOf(false) }

    val loadManga = { query: String, currentFilters: FilterList?, pageToLoad: Int ->
        scope.launch {
            if (pageToLoad == 1) {
                isLoading = true
                errorMessage = null
            } else {
                isLoadingNextPage = true
            }
            try {
                val source = loadedExtension?.sources?.firstOrNull()
                if (source != null) {
                    val pageResult = if (query.isNotBlank() || currentFilters != null) {
                        MihonKit.searchManga(
                            sourceId = source.id,
                            page = pageToLoad,
                            query = query,
                            filters = currentFilters ?: source.getFilterList(),
                        )
                    } else {
                        MihonKit.getPopularManga(source.id, page = pageToLoad)
                    }

                    currentPage = pageToLoad
                    hasNextPage = pageResult.hasNextPage
                    mangas = if (pageToLoad == 1) {
                        pageResult.mangas
                    } else {
                        // Deduplicate in case source returns overlapping items
                        val existingUrls = mangas.map { it.url }.toSet()
                        mangas + pageResult.mangas.filter { it.url !in existingUrls }
                    }
                }
            } catch (e: Throwable) {
                if (pageToLoad == 1) {
                    errorMessage = e.message ?: "Failed to load manga"
                }
            } finally {
                isLoading = false
                isLoadingNextPage = false
            }
        }
    }

    LaunchedEffect(loadedExtension, searchQuery) {
        if (loadedExtension != null) {
            val source = loadedExtension.sources.firstOrNull()
            if (source != null) {
                filterList = source.getFilterList()
                if (searchQuery.isNotBlank()) {
                    isSearchActive = true
                }
                currentPage = 1
                hasNextPage = true
                loadManga(searchQuery, filterList, 1)
            }
        }
    }

    // Trigger pagination when user scrolls near the end
    LaunchedEffect(gridState, mangas.size, hasNextPage, isLoading, isLoadingNextPage) {
        snapshotFlow {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 6
        }.collect { isNearBottom ->
            if (isNearBottom && hasNextPage && !isLoading && !isLoadingNextPage) {
                loadManga(searchQuery, filterList, currentPage + 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("Search manga, tag, author...", color = Color.Gray) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { loadManga(searchQuery, filterList, 1) }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Box {
                            Column(
                                modifier = Modifier
                                    .clickable { showExtensionMenu = true }
                                    .padding(vertical = 4.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = loadedExtension?.name ?: "Select Extension",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Icon(
                                        MaterialSymbols.Rounded.ArrowDownward,
                                        contentDescription = "Switch Extension",
                                        modifier = Modifier.size(16.dp).padding(start = 4.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    text = loadedExtension?.sources?.firstOrNull()?.let { "${it.name} (${it.lang.uppercase()}) • Tap to switch" } ?: "Loading...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            DropdownMenu(
                                expanded = showExtensionMenu,
                                onDismissRequest = { showExtensionMenu = false },
                            ) {
                                allExtensions.forEach { ext ->
                                    val isSelected = ext.packageName == loadedExtension?.packageName
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = ext.name,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                )
                                                Text(
                                                    text = "${ext.sources.size} sources • ${ext.lang.uppercase()}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray,
                                                )
                                            }
                                        },
                                        onClick = {
                                            showExtensionMenu = false
                                            onSelectExtension(ext)
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            onSearchQueryChanged("")
                            loadManga("", filterList, 1)
                        }) {
                            Icon(MaterialSymbols.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(MaterialSymbols.Rounded.Search, contentDescription = "Search")
                        }
                    } else {
                        IconButton(onClick = { loadManga(searchQuery, filterList, 1) }) {
                            Icon(MaterialSymbols.Rounded.Search, contentDescription = "Execute search")
                        }
                    }
                    IconButton(onClick = { loadManga(searchQuery, filterList, 1) }) {
                        Icon(MaterialSymbols.Rounded.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(MaterialSymbols.Rounded.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = onOpenPreferences) {
                        Icon(MaterialSymbols.Rounded.Settings, contentDescription = "Extension Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                ),
            )
        },
    )
 { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Searching for \"$searchQuery\"..." else "Loading catalog through extension...",
                            color = Color.Gray,
                        )
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(text = "Error: $errorMessage", color = MaterialTheme.colorScheme.error)
                    }
                }
                mangas.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(text = "No results found for \"$searchQuery\"", color = Color.Gray)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(mangas) { manga ->
                            MangaCoverCard(manga = manga, onClick = { onSelectManga(manga) })
                        }

                        if (isLoadingNextPage) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet && filterList != null) {
        ExtensionFilterBottomSheet(
            filters = filterList!!,
            onDismissRequest = { showFilterSheet = false },
            onReset = {
                val source = loadedExtension?.sources?.firstOrNull()
                filterList = source?.getFilterList()
            },
            onApply = { updatedFilters ->
                filterList = updatedFilters
                loadManga(searchQuery, updatedFilters, 1)
            },
        )
    }
}

// =========================================================================
// 2. MANGA DETAILS & CHAPTER LIST SCREEN
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MangaDetailsScreen(
    sourceId: Long,
    manga: SManga,
    onBack: () -> Unit,
    onSelectChapter: (SChapter) -> Unit,
    onSearchTag: (String) -> Unit,
) {
    BackHandler { onBack() }

    var isLoading by remember { mutableStateOf(true) }
    var updatedManga by remember { mutableStateOf(manga) }
    var chapters by remember { mutableStateOf<List<SChapter>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(manga.url) {
        try {
            val update = MihonKit.getMangaUpdate(sourceId, manga)
            updatedManga = update.manga
            chapters = update.chapters
        } catch (e: Throwable) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadStates = remember { androidx.compose.runtime.mutableStateMapOf<String, DownloadStatus>() }

    val tags = remember(updatedManga.genre) {
        updatedManga.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Manga Details", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MaterialSymbols.Rounded.KeyboardArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E)),
            )
        },
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = "Error: $errorMessage", color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
            ) {
                // Header with Cover & Info
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = updatedManga.thumbnail_url,
                            contentDescription = updatedManga.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = updatedManga.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            updatedManga.author?.takeIf { it.isNotBlank() }?.let { author ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF2A2A2A))
                                        .clickable { onSearchTag(author) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Rounded.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Tag / Genre Chips
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Tags (tap to search):",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = { onSearchTag(tag) },
                                    label = {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color(0xFF222222),
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = Color(0xFF3A3A3A),
                                    ),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    updatedManga.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Chapters (${chapters.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Chapter items list
                items(chapters) { chapter ->
                    val chapterDownloadState = downloadStates[chapter.url]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectChapter(chapter) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chapter.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (chapter.scanlator != null) {
                                    Text(
                                        text = "Scanlator: ${chapter.scanlator}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                    )
                                }
                                if (chapterDownloadState is DownloadStatus.Downloading) {
                                    Text(
                                        text = "Downloading: ${(chapterDownloadState.progress * 100).toInt()}% (${chapterDownloadState.downloadedPages}/${chapterDownloadState.totalPages})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            // Download Action Button
                            when (chapterDownloadState) {
                                is DownloadStatus.Downloading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        progress = { chapterDownloadState.progress },
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.5.dp,
                                    )
                                }
                                is DownloadStatus.Completed -> {
                                    Icon(
                                        MaterialSymbols.Rounded.Check,
                                        contentDescription = "Downloaded",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                else -> {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val cleanTitle = updatedManga.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                                val cleanChapter = chapter.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                                val destDir = File(context.filesDir, "downloads/$cleanTitle/$cleanChapter")
                                                MihonKit.downloadChapter(sourceId, chapter, destDir).collect { status ->
                                                    downloadStates[chapter.url] = status
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            MaterialSymbols.Rounded.Download,
                                            contentDescription = "Download Chapter",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// =========================================================================
// 3. CHAPTER PAGE READER SCREEN
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    sourceId: Long,
    chapter: SChapter,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    var isLoading by remember { mutableStateOf(true) }
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chapter.url) {
        try {
            pages = MihonKit.getPageList(sourceId, chapter)
        } catch (e: Throwable) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = chapter.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MaterialSymbols.Rounded.KeyboardArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E)),
            )
        },
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading chapter pages from source...", color = Color.Gray)
                }
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = "Error: $errorMessage", color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(pages) { page ->
                    AsyncImage(
                        model = page.imageUrl,
                        contentDescription = "Page ${page.index + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    )
                }
            }
        }
    }
}

// =========================================================================
// 4. COVER CARD ITEM
// =========================================================================

@Composable
fun MangaCoverCard(manga: SManga, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF242424))
            .clickable { onClick() },
    ) {
        AsyncImage(
            model = manga.thumbnail_url,
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    ),
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
