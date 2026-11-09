package mihon.kit.ui.reader

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mihon.kit.MihonKit
import mihon.kit.ui.reader.filter.ReaderColorFilter
import mihon.kit.ui.reader.filter.ReaderFilterMode
import mihon.kit.ui.reader.spread.DualPageMode
import mihon.kit.ui.reader.spread.ReaderSpread
import mihon.kit.ui.reader.spread.SpreadTransformer

/**
 * Supported reader view layout modes matching Mihon.
 */
enum class ViewerMode {
    /** Continuous vertical Webtoon strip with seamless edge joins. */
    Webtoon,

    /** Standard Right-to-Left paging (traditional Manga reading order). */
    PagedRtoL,

    /** Standard Left-to-Right paging (Western Comic / Manhwa reading order). */
    PagedLtoR,
}

/**
 * Advanced Jetpack Compose manga/comic reader component matching Mihon's native reader capabilities.
 *
 * Capabilities:
 * - **Viewing Modes**: Continuous Webtoon vertical strip (with hands-free auto-scroll) and Horizontal Paged (RTL and LTR).
 * - **Dual-Page Spreads**: Side-by-side page pairing in landscape orientation or forced dual mode.
 * - **Color Post-Processing**: Real-time shader/matrix filters for Invert colors, Grayscale, and Contrast boosts.
 * - **Hardware Navigation**: Volume key page flipping for single-handed reading.
 * - **Header Injection**: Anti-leech HTTP referer and user-agent header injection for image requests.
 * - **Gestures**: Double-tap zoom, pinch-to-zoom, and 3-zone tap navigation.
 *
 * @param sourceId The source identifier (used to look up required referer/cookie headers).
 * @param pages List of [Page] entries to display.
 * @param chapterTitle Human-readable chapter title displayed in the reader overlay.
 * @param modifier Composable modifier applied to the reader root container.
 * @param initialPage Zero-based page index to open initially (defaults to 0).
 * @param viewerMode Reading mode ([ViewerMode.Webtoon], [ViewerMode.PagedRtoL], [ViewerMode.PagedLtoR]).
 * @param dualPageMode Dual-page spread layout configuration ([DualPageMode.AutoLandscapeDual], [DualPageMode.DualPage], [DualPageMode.Single]).
 * @param filterMode Real-time color filter mode ([ReaderFilterMode.None], [ReaderFilterMode.Invert], [ReaderFilterMode.Grayscale]).
 * @param contrast Contrast adjustment factor (1.0 = normal, >1.0 = boosted contrast).
 * @param volumeKeysNavigation If true, volume up/down keys navigate backward/forward.
 * @param onClose Callback invoked when the user exits the reader via the top bar or back gesture.
 * @param onPageSelected Callback emitted whenever the active viewing page index changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MihonReader(
    sourceId: Long,
    pages: List<Page>,
    chapterTitle: String,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    viewerMode: ViewerMode = ViewerMode.Webtoon,
    dualPageMode: DualPageMode = DualPageMode.AutoLandscapeDual,
    filterMode: ReaderFilterMode = ReaderFilterMode.None,
    contrast: Float = 1.0f,
    volumeKeysNavigation: Boolean = true,
    onClose: () -> Unit = {},
    onPageSelected: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showOverlay by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf(viewerMode) }
    var currentFilter by remember { mutableStateOf(filterMode) }
    var currentContrast by remember { mutableFloatStateOf(contrast) }
    var currentPageIndex by remember { mutableStateOf(initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }

    // Auto-Scroll State for Webtoon
    var isAutoScrolling by remember { mutableStateOf(false) }
    var autoScrollSpeed by remember { mutableFloatStateOf(2.5f) } // px per tick

    val isDualSpread = remember(dualPageMode, isLandscape) {
        when (dualPageMode) {
            DualPageMode.Single -> false
            DualPageMode.DualPage -> true
            DualPageMode.AutoLandscapeDual -> isLandscape
        }
    }

    val colorFilter = remember(currentFilter, currentContrast) {
        ReaderColorFilter.createFilter(currentFilter, currentContrast)
    }

    val rawHeaders = remember(sourceId) { MihonKit.getSourceHeaders(sourceId) }
    val networkHeaders = remember(rawHeaders) {
        NetworkHeaders.Builder().apply {
            rawHeaders?.forEach { (name, value) ->
                set(name, value)
            }
        }.build()
    }

    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (!volumeKeysNavigation || keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.VolumeDown -> {
                        // Next page
                        if (currentPageIndex < pages.size - 1) {
                            currentPageIndex++
                            onPageSelected(currentPageIndex)
                        }
                        true
                    }
                    Key.VolumeUp -> {
                        // Previous page
                        if (currentPageIndex > 0) {
                            currentPageIndex--
                            onPageSelected(currentPageIndex)
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        when (currentMode) {
            ViewerMode.Webtoon -> {
                WebtoonViewer(
                    pages = pages,
                    networkHeaders = networkHeaders,
                    initialPage = currentPageIndex,
                    colorFilter = colorFilter,
                    isAutoScrolling = isAutoScrolling,
                    autoScrollSpeed = autoScrollSpeed,
                    onToggleOverlay = { showOverlay = !showOverlay },
                    onPageVisible = { page ->
                        currentPageIndex = page
                        onPageSelected(page)
                    },
                )
            }
            ViewerMode.PagedLtoR, ViewerMode.PagedRtoL -> {
                val isRtoL = currentMode == ViewerMode.PagedRtoL
                val spreads = remember(pages, isDualSpread, isRtoL) {
                    SpreadTransformer.createSpreads(
                        pages = pages,
                        dualPage = isDualSpread,
                        isRightToLeft = isRtoL,
                        isFirstPageCover = true,
                    )
                }

                SpreadPagedViewer(
                    spreads = spreads,
                    networkHeaders = networkHeaders,
                    initialPage = currentPageIndex,
                    isRtoL = isRtoL,
                    colorFilter = colorFilter,
                    onToggleOverlay = { showOverlay = !showOverlay },
                    onPageChanged = { page ->
                        currentPageIndex = page
                        onPageSelected(page)
                    },
                )
            }
        }

        // Floating Auto-Scroll Toggle Button for Webtoon
        if (currentMode == ViewerMode.Webtoon && !showOverlay) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isAutoScrolling) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp, end = 24.dp)
                    .clickable { isAutoScrolling = !isAutoScrolling },
            ) {
                Text(
                    text = if (isAutoScrolling) "⏸ Pause" else "▶ Auto",
                    color = if (isAutoScrolling) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // Top & Bottom Overlay Controls
        AnimatedVisibility(
            visible = showOverlay,
            modifier = Modifier.fillMaxSize(),
        ) {
            ReaderOverlay(
                chapterTitle = chapterTitle,
                currentPage = currentPageIndex + 1,
                totalPages = pages.size,
                currentMode = currentMode,
                currentFilter = currentFilter,
                onModeChanged = { currentMode = it },
                onFilterChanged = { currentFilter = it },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun WebtoonViewer(
    pages: List<Page>,
    networkHeaders: NetworkHeaders,
    initialPage: Int,
    colorFilter: ColorFilter?,
    isAutoScrolling: Boolean,
    autoScrollSpeed: Float,
    onToggleOverlay: () -> Unit,
    onPageVisible: (Int) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageVisible(listState.firstVisibleItemIndex)
    }

    // Hands-free continuous auto-scrolling loop
    LaunchedEffect(isAutoScrolling, autoScrollSpeed) {
        if (!isAutoScrolling) return@LaunchedEffect
        while (isActive) {
            listState.animateScrollBy(autoScrollSpeed)
            delay(16L) // ~60 FPS smooth auto scroll
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleOverlay() }
                )
            },
    ) {
        itemsIndexed(pages, key = { index, page -> "${page.index}_$index" }) { _, page ->
            ReaderPageImage(
                page = page,
                networkHeaders = networkHeaders,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpreadPagedViewer(
    spreads: List<ReaderSpread>,
    networkHeaders: NetworkHeaders,
    initialPage: Int,
    isRtoL: Boolean,
    colorFilter: ColorFilter?,
    onToggleOverlay: () -> Unit,
    onPageChanged: (Int) -> Unit,
) {
    val totalSpreads = spreads.size
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { totalSpreads },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        val spread = spreads.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val pageNum = when (spread) {
            is ReaderSpread.Single -> spread.page.index
            is ReaderSpread.Dual -> spread.first.index
        }
        onPageChanged(pageNum)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = isRtoL,
        ) { spreadIndex ->
            when (val spread = spreads[spreadIndex]) {
                is ReaderSpread.Single -> {
                    ZoomablePage(
                        page = spread.page,
                        networkHeaders = networkHeaders,
                        colorFilter = colorFilter,
                    )
                }
                is ReaderSpread.Dual -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ReaderPageImage(
                                page = spread.first,
                                networkHeaders = networkHeaders,
                                colorFilter = colorFilter,
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.Black))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ReaderPageImage(
                                page = spread.second,
                                networkHeaders = networkHeaders,
                                colorFilter = colorFilter,
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
        }

        // Left / Center / Right Tap Zones
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        scope.launch {
                            val target = if (isRtoL) pagerState.currentPage + 1 else pagerState.currentPage - 1
                            if (target in 0 until totalSpreads) {
                                pagerState.animateScrollToPage(target)
                            }
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onToggleOverlay()
                    }
            )
            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        scope.launch {
                            val target = if (isRtoL) pagerState.currentPage - 1 else pagerState.currentPage + 1
                            if (target in 0 until totalSpreads) {
                                pagerState.animateScrollToPage(target)
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun ZoomablePage(
    page: Page,
    networkHeaders: NetworkHeaders,
    colorFilter: ColorFilter?,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + offsetChange else Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        ReaderPageImage(
            page = page,
            networkHeaders = networkHeaders,
            colorFilter = colorFilter,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                )
        )
    }
}

@Composable
private fun ReaderPageImage(
    page: Page,
    networkHeaders: NetworkHeaders,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
) {
    val context = LocalContext.current
    var isError by remember { mutableStateOf(false) }

    val imageUrl = page.imageUrl ?: page.url
    val request = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .httpHeaders(networkHeaders)
            .build()
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = "Page ${page.index + 1}",
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = Modifier.fillMaxWidth(),
            onError = { isError = true },
        )

        if (isError) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF1E1E1E)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Failed to load page ${page.index + 1}",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ReaderOverlay(
    chapterTitle: String,
    currentPage: Int,
    totalPages: Int,
    currentMode: ViewerMode,
    currentFilter: ReaderFilterMode,
    onModeChanged: (ViewerMode) -> Unit,
    onFilterChanged: (ReaderFilterMode) -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.85f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Reader",
                        tint = Color.White,
                    )
                }
                Text(
                    text = chapterTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Bottom Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color.Black.copy(alpha = 0.85f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$currentPage / $totalPages",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Mode Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ViewerModeChip(
                        title = "Webtoon",
                        isSelected = currentMode == ViewerMode.Webtoon,
                        onClick = { onModeChanged(ViewerMode.Webtoon) },
                    )
                    ViewerModeChip(
                        title = "Manga (R-to-L)",
                        isSelected = currentMode == ViewerMode.PagedRtoL,
                        onClick = { onModeChanged(ViewerMode.PagedRtoL) },
                    )
                    ViewerModeChip(
                        title = "Paged (L-to-R)",
                        isSelected = currentMode == ViewerMode.PagedLtoR,
                        onClick = { onModeChanged(ViewerMode.PagedLtoR) },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Color Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ViewerModeChip(
                        title = "Normal",
                        isSelected = currentFilter == ReaderFilterMode.None,
                        onClick = { onFilterChanged(ReaderFilterMode.None) },
                    )
                    ViewerModeChip(
                        title = "Invert (Night)",
                        isSelected = currentFilter == ReaderFilterMode.Invert,
                        onClick = { onFilterChanged(ReaderFilterMode.Invert) },
                    )
                    ViewerModeChip(
                        title = "Grayscale",
                        isSelected = currentFilter == ReaderFilterMode.Grayscale,
                        onClick = { onFilterChanged(ReaderFilterMode.Grayscale) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerModeChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2C2C2C),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.Black else Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
