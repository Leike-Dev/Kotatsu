@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)

package org.koitharu.kotatsu.ui.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.PageItem
import org.koitharu.kotatsu.domain.model.ReaderBackground
import org.koitharu.kotatsu.domain.model.ReaderMode
import org.koitharu.kotatsu.domain.model.ReaderScaleMode
import org.koitharu.kotatsu.ui.components.ErrorRetryCard
import org.koitharu.kotatsu.ui.theme.resolveReaderPalette
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val bookmarkedPages by viewModel.bookmarkedPages.collectAsStateWithLifecycle()

    val volumeEvents = LocalVolumeKeyEvents.current
    val setVolumeCapture = LocalSetVolumeKeyCapture.current

    val palette = resolveReaderPalette(prefs.readerBackground)
    var hudVisible by remember { mutableStateOf(true) }
    var showConfigSheet by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Keep screen awake when enabled
    DisposableEffect(prefs.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (prefs.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Intercept hardware volume keys only while inside ReaderScreen & preference active
    DisposableEffect(prefs.volumeKeysNavigation) {
        setVolumeCapture(prefs.volumeKeysNavigation)
        onDispose {
            setVolumeCapture(false)
        }
    }

    val pageColorFilter = remember(prefs.readerGrayscale, prefs.readerInvertColors) {
        buildPageColorFilter(
            grayscale = prefs.readerGrayscale,
            invert = prefs.readerInvertColors
        )
    }

    val resolvedContentScale = remember(prefs.readerScaleMode) {
        when (prefs.readerScaleMode) {
            ReaderScaleMode.FIT_SCREEN -> ContentScale.Fit
            ReaderScaleMode.FIT_WIDTH -> ContentScale.FillWidth
            ReaderScaleMode.FIT_HEIGHT -> ContentScale.FillHeight
            ReaderScaleMode.CROP -> ContentScale.Crop
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.pageBackground)
            .testTag("reader_screen")
    ) {
        when {
            uiState.isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ContainedLoadingIndicator(modifier = Modifier.size(58.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.reader_loading_pages),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.pageContent
                    )
                }
            }

            uiState.errorMessage != null || uiState.pages.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ErrorRetryCard(
                        message = uiState.errorMessage.orEmpty(),
                        onRetry = viewModel::retryLoad,
                        modifier = Modifier.widthIn(max = 460.dp)
                    )
                }
            }

            else -> {
                // Keying by chapterId avoids stale pager/list indices on chapter transitions
                key(uiState.currentChapter?.id) {
                    when (prefs.defaultReaderMode) {
                        ReaderMode.WEBTOON -> {
                            val listState = rememberLazyListState(
                                initialFirstVisibleItemIndex = uiState.currentPageIndex.coerceIn(
                                    0,
                                    (uiState.pages.size - 1).coerceAtLeast(0)
                                )
                            )
                            val visibleIndex by remember {
                                derivedStateOf { listState.firstVisibleItemIndex }
                            }

                            LaunchedEffect(visibleIndex) {
                                if (visibleIndex in uiState.pages.indices) {
                                    viewModel.onPageChanged(visibleIndex)
                                }
                            }

                            LaunchedEffect(uiState.currentPageIndex) {
                                if (listState.firstVisibleItemIndex != uiState.currentPageIndex &&
                                    uiState.currentPageIndex in uiState.pages.indices
                                ) {
                                    listState.scrollToItem(uiState.currentPageIndex)
                                }
                            }

                            LaunchedEffect(prefs.volumeKeysNavigation) {
                                if (prefs.volumeKeysNavigation) {
                                    volumeEvents.collectLatest { direction ->
                                        listState.animateScrollBy(direction * 620f)
                                    }
                                }
                            }

                            var webtoonZoom by remember { mutableFloatStateOf(1f) }
                            var webtoonOffsetX by remember { mutableFloatStateOf(0f) }
                            var containerWidth by remember { mutableStateOf(1) }
                            val webtoonTransform = rememberTransformableState { zoomChange, panChange, _ ->
                                val nextScale = (webtoonZoom * zoomChange).coerceIn(1f, 2.5f)
                                webtoonZoom = nextScale
                                if (nextScale > 1.02f) {
                                    val maxX = (containerWidth * (nextScale - 1f)) / 2f
                                    webtoonOffsetX = (webtoonOffsetX + panChange.x).coerceIn(-maxX, maxX)
                                } else {
                                    webtoonOffsetX = 0f
                                }
                            }

                            LazyColumn(
                                state = listState,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(
                                    if (prefs.webtoonSeamlessGap) 0.dp else 10.dp
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onSizeChanged { containerWidth = it.width.coerceAtLeast(1) }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (webtoonZoom > 1.05f) {
                                                    webtoonZoom = 1f
                                                    webtoonOffsetX = 0f
                                                } else {
                                                    webtoonZoom = 1.85f
                                                }
                                            },
                                            onTap = { hudVisible = !hudVisible }
                                        )
                                    }
                                    .then(
                                        if (webtoonZoom > 1.02f) Modifier.transformable(webtoonTransform)
                                        else Modifier
                                    )
                                    .graphicsLayer {
                                        scaleX = webtoonZoom
                                        scaleY = webtoonZoom
                                        translationX = webtoonOffsetX
                                    }
                                    .testTag("webtoon_reader_list")
                            ) {
                                itemsIndexed(uiState.pages, key = { _, p -> p.index }) { _, page ->
                                    SubcomposeAsyncImage(
                                        model = page.imageUrl,
                                        contentDescription = stringResource(R.string.reader_page_alt, page.index + 1),
                                        contentScale = ContentScale.FillWidth,
                                        colorFilter = pageColorFilter,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 900.dp),
                                        loading = {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(340.dp)
                                            ) {
                                                LoadingIndicator(modifier = Modifier.size(36.dp))
                                            }
                                        },
                                        error = {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(220.dp)
                                                    .padding(16.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.reader_page_error),
                                                    color = palette.pageContent
                                                )
                                            }
                                        }
                                    )
                                }

                                item {
                                    EndOfChapterCard(
                                        nextChapterTitle = uiState.nextChapter?.displayTitle,
                                        textColor = palette.pageContent,
                                        onGoNext = {
                                            uiState.nextChapter?.let(viewModel::switchToChapter)
                                        }
                                    )
                                }
                            }
                        }

                        ReaderMode.VERTICAL_PAGED -> {
                            val pagerState = rememberPagerState(
                                initialPage = uiState.currentPageIndex.coerceIn(
                                    0,
                                    (uiState.pages.size - 1).coerceAtLeast(0)
                                ),
                                pageCount = { uiState.pages.size }
                            )

                            LaunchedEffect(pagerState.currentPage) {
                                viewModel.onPageChanged(pagerState.currentPage)
                            }

                            LaunchedEffect(uiState.currentPageIndex) {
                                if (pagerState.currentPage != uiState.currentPageIndex &&
                                    uiState.currentPageIndex in uiState.pages.indices
                                ) {
                                    pagerState.scrollToPage(uiState.currentPageIndex)
                                }
                            }

                            LaunchedEffect(prefs.volumeKeysNavigation) {
                                if (prefs.volumeKeysNavigation) {
                                    volumeEvents.collectLatest { delta ->
                                        val target = pagerState.currentPage + delta
                                        if (target in uiState.pages.indices) {
                                            if (prefs.pageTransitions) {
                                                pagerState.animateScrollToPage(target)
                                            } else {
                                                pagerState.scrollToPage(target)
                                            }
                                        } else if (delta > 0 && uiState.nextChapter != null) {
                                            uiState.nextChapter?.let(viewModel::switchToChapter)
                                        }
                                    }
                                }
                            }

                            VerticalPager(
                                state = pagerState,
                                beyondViewportPageCount = 1,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("vertical_paged_reader_pager")
                            ) { pageIdx ->
                                val page = uiState.pages[pageIdx]
                                ZoomableReaderPage(
                                    page = page,
                                    contentScale = resolvedContentScale,
                                    colorFilter = pageColorFilter,
                                    contentColor = palette.pageContent,
                                    onTapPosition = { _, fractionY ->
                                        when {
                                            fractionY < 0.28f -> {
                                                val target = pageIdx - 1
                                                if (target in uiState.pages.indices) {
                                                    coroutineScope.launch {
                                                        if (prefs.pageTransitions) pagerState.animateScrollToPage(target)
                                                        else pagerState.scrollToPage(target)
                                                    }
                                                }
                                            }

                                            fractionY > 0.72f -> {
                                                val target = pageIdx + 1
                                                if (target in uiState.pages.indices) {
                                                    coroutineScope.launch {
                                                        if (prefs.pageTransitions) pagerState.animateScrollToPage(target)
                                                        else pagerState.scrollToPage(target)
                                                    }
                                                } else if (uiState.nextChapter != null) {
                                                    uiState.nextChapter?.let(viewModel::switchToChapter)
                                                }
                                            }

                                            else -> hudVisible = !hudVisible
                                        }
                                    }
                                )
                            }
                        }

                        ReaderMode.STANDARD_LTR, ReaderMode.MANGA_RTL -> {
                            val isReversed = prefs.defaultReaderMode == ReaderMode.MANGA_RTL
                            val pagerState = rememberPagerState(
                                initialPage = uiState.currentPageIndex.coerceIn(
                                    0,
                                    (uiState.pages.size - 1).coerceAtLeast(0)
                                ),
                                pageCount = { uiState.pages.size }
                            )

                            LaunchedEffect(pagerState.currentPage) {
                                viewModel.onPageChanged(pagerState.currentPage)
                            }

                            LaunchedEffect(uiState.currentPageIndex) {
                                if (pagerState.currentPage != uiState.currentPageIndex &&
                                    uiState.currentPageIndex in uiState.pages.indices
                                ) {
                                    pagerState.scrollToPage(uiState.currentPageIndex)
                                }
                            }

                            LaunchedEffect(prefs.volumeKeysNavigation) {
                                if (prefs.volumeKeysNavigation) {
                                    volumeEvents.collectLatest { delta ->
                                        val target = pagerState.currentPage + delta
                                        if (target in uiState.pages.indices) {
                                            if (prefs.pageTransitions) {
                                                pagerState.animateScrollToPage(target)
                                            } else {
                                                pagerState.scrollToPage(target)
                                            }
                                        } else if (delta > 0 && uiState.nextChapter != null) {
                                            uiState.nextChapter?.let(viewModel::switchToChapter)
                                        } else if (delta < 0 && uiState.previousChapter != null) {
                                            uiState.previousChapter?.let(viewModel::switchToChapter)
                                        }
                                    }
                                }
                            }

                            HorizontalPager(
                                state = pagerState,
                                reverseLayout = isReversed,
                                beyondViewportPageCount = 1,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("paged_reader_pager")
                            ) { pageIdx ->
                                val page = uiState.pages[pageIdx]
                                ZoomableReaderPage(
                                    page = page,
                                    contentScale = resolvedContentScale,
                                    colorFilter = pageColorFilter,
                                    contentColor = palette.pageContent,
                                    onTapPosition = { fractionX, _ ->
                                        when {
                                            fractionX < 0.28f -> {
                                                val target = if (isReversed) pageIdx + 1 else pageIdx - 1
                                                if (target in uiState.pages.indices) {
                                                    coroutineScope.launch {
                                                        if (prefs.pageTransitions) pagerState.animateScrollToPage(target)
                                                        else pagerState.scrollToPage(target)
                                                    }
                                                } else if (isReversed && uiState.nextChapter != null) {
                                                    uiState.nextChapter?.let(viewModel::switchToChapter)
                                                }
                                            }

                                            fractionX > 0.72f -> {
                                                val target = if (isReversed) pageIdx - 1 else pageIdx + 1
                                                if (target in uiState.pages.indices) {
                                                    coroutineScope.launch {
                                                        if (prefs.pageTransitions) pagerState.animateScrollToPage(target)
                                                        else pagerState.scrollToPage(target)
                                                    }
                                                } else if (!isReversed && uiState.nextChapter != null) {
                                                    uiState.nextChapter?.let(viewModel::switchToChapter)
                                                }
                                            }

                                            else -> hudVisible = !hudVisible
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Night Brightness Dimming Overlay (non-interactive)
        if (prefs.readerBrightnessDim > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = prefs.readerBrightnessDim.coerceIn(0f, 0.65f)))
            )
        }

        // Subtle Mini Progress Pill when HUD is hidden
        AnimatedVisibility(
            visible = !hudVisible && prefs.showReaderMiniInfo && uiState.pages.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val pct = (((uiState.currentPageIndex + 1).toFloat() / uiState.pages.size.coerceAtLeast(1)) * 100f)
                .roundToInt()
            Surface(
                shape = CircleShape,
                color = palette.hudContainer.copy(alpha = 0.75f),
                contentColor = palette.hudContent
            ) {
                Text(
                    text = "${uiState.currentPageIndex + 1}/${uiState.pages.size} • $pct%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Top M3 Expressive Floating Pill HUD
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = palette.hudContainer,
                contentColor = palette.hudContent,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 780.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back_action)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(enabled = uiState.chapters.size > 1) {
                                showChaptersSheet = true
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = uiState.manga?.title.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.currentChapter?.displayTitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.hudContent.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (uiState.chapters.size > 1) {
                        IconButton(
                            onClick = { showChaptersSheet = true },
                            modifier = Modifier.testTag("reader_chapters_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                contentDescription = stringResource(R.string.reader_select_chapter_desc)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.setShowThumbnails(!prefs.showReaderThumbnails) },
                        enabled = uiState.pages.isNotEmpty(),
                        modifier = Modifier.testTag("reader_thumbnails_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ViewCarousel,
                            contentDescription = stringResource(R.string.reader_thumbnails_toggle),
                            tint = if (prefs.showReaderThumbnails) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                palette.hudContent
                            }
                        )
                    }

                    val isPageBookmarked = uiState.currentPageIndex in bookmarkedPages
                    IconButton(
                        onClick = viewModel::toggleBookmarkOnCurrentPage,
                        enabled = uiState.pages.isNotEmpty(),
                        modifier = Modifier.testTag("reader_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isPageBookmarked) {
                                Icons.Filled.Bookmark
                            } else {
                                Icons.Outlined.BookmarkAdd
                            },
                            contentDescription = stringResource(
                                if (isPageBookmarked) R.string.reader_bookmark_remove
                                else R.string.reader_bookmark_add
                            ),
                            tint = if (isPageBookmarked) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                palette.hudContent
                            }
                        )
                    }

                    IconButton(
                        onClick = { showConfigSheet = true },
                        modifier = Modifier.testTag("reader_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.reader_settings_action)
                        )
                    }
                }
            }
        }

        // Bottom M3 Expressive HorizontalFloatingToolbar HUD + Page Thumbnails Strip
        AnimatedVisibility(
            visible = hudVisible && uiState.pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 720.dp)
            ) {
                // Page Thumbnails Strip (Official Kotatsu Page Preview Bar)
                AnimatedVisibility(visible = prefs.showReaderThumbnails) {
                    val thumbListState = rememberLazyListState()
                    LaunchedEffect(uiState.currentPageIndex) {
                        if (uiState.currentPageIndex in uiState.pages.indices) {
                            thumbListState.animateScrollToItem(
                                (uiState.currentPageIndex - 2).coerceAtLeast(0)
                            )
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = palette.hudContainer,
                        tonalElevation = 4.dp,
                        shadowElevation = 5.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reader_thumbnails_strip")
                    ) {
                        LazyRow(
                            state = thumbListState,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(uiState.pages, key = { _, p -> p.index }) { idx, page ->
                                val selected = idx == uiState.currentPageIndex
                                Surface(
                                    onClick = { viewModel.onPageChanged(idx) },
                                    shape = MaterialTheme.shapes.small,
                                    border = if (selected) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        null
                                    },
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .width(52.dp)
                                        .height(74.dp)
                                        .testTag("reader_thumb_$idx")
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        SubcomposeAsyncImage(
                                            model = page.imageUrl,
                                            contentDescription = stringResource(R.string.reader_thumb_alt, idx + 1),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.Black.copy(alpha = 0.65f)
                                            },
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 3.dp)
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    Color.White
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Counter + Active Mode Pill
                Surface(
                    shape = CircleShape,
                    color = palette.hudContainer,
                    contentColor = palette.hudContent,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.reader_page_counter,
                                uiState.currentPageIndex + 1,
                                uiState.pages.size
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.hudContent.copy(alpha = 0.6f)
                        )
                        Text(
                            text = stringResource(
                                when (prefs.defaultReaderMode) {
                                    ReaderMode.STANDARD_LTR -> R.string.reader_pill_ltr
                                    ReaderMode.MANGA_RTL -> R.string.reader_pill_rtl
                                    ReaderMode.VERTICAL_PAGED -> R.string.reader_pill_vertical
                                    ReaderMode.WEBTOON -> R.string.reader_pill_webtoon
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.hudContent.copy(alpha = 0.85f)
                        )
                    }
                }

                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                        toolbarContainerColor = palette.hudContainer,
                        toolbarContentColor = palette.hudContent
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { uiState.previousChapter?.let(viewModel::switchToChapter) },
                            enabled = uiState.previousChapter != null,
                            modifier = Modifier.testTag("reader_prev_chapter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipPrevious,
                                contentDescription = stringResource(R.string.reader_prev_chapter)
                            )
                        }

                        val maxIndex = (uiState.pages.size - 1).coerceAtLeast(1)
                        val sliderDirection = if (prefs.defaultReaderMode == ReaderMode.MANGA_RTL) {
                            LayoutDirection.Rtl
                        } else {
                            LayoutDirection.Ltr
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides sliderDirection) {
                            Slider(
                                value = uiState.currentPageIndex.toFloat(),
                                onValueChange = { raw ->
                                    viewModel.onPageChanged(raw.roundToInt())
                                },
                                valueRange = 0f..maxIndex.toFloat(),
                                enabled = uiState.pages.size > 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                                    .testTag("reader_page_slider")
                            )
                        }

                        IconButton(
                            onClick = { uiState.nextChapter?.let(viewModel::switchToChapter) },
                            enabled = uiState.nextChapter != null,
                            modifier = Modifier.testTag("reader_next_chapter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipNext,
                                contentDescription = stringResource(R.string.reader_next_chapter)
                            )
                        }
                    }
                }
            }
        }
    }

    // Quick Chapter Selector Sheet
    if (showChaptersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChaptersSheet = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = stringResource(R.string.chapters_header_format, uiState.chapters.size),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) {
                    items(uiState.chapters, key = { it.id }) { chapter ->
                        val active = chapter.id == uiState.currentChapter?.id
                        Surface(
                            onClick = {
                                showChaptersSheet = false
                                if (!active) {
                                    viewModel.switchToChapter(chapter)
                                }
                            },
                            shape = MaterialTheme.shapes.large,
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.displayTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (active) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (chapter.scanlator.isNotBlank()) {
                                        Text(
                                            text = chapter.scanlator,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (active) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Complete Official Kotatsu Visualization Bottom Sheet
    if (showConfigSheet) {
        ReaderVisualizationSheet(
            prefs = prefs,
            viewModel = viewModel,
            onDismiss = { showConfigSheet = false }
        )
    }
}

@Composable
private fun ReaderVisualizationSheet(
    prefs: AppPreferences,
    viewModel: ReaderViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_settings_action),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Reader Mode
            Text(
                text = stringResource(R.string.default_reader_mode_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReaderMode.entries.forEach { mode ->
                    val selected = prefs.defaultReaderMode == mode
                    val labelRes = when (mode) {
                        ReaderMode.STANDARD_LTR -> R.string.reader_mode_standard
                        ReaderMode.MANGA_RTL -> R.string.reader_mode_reversed
                        ReaderMode.VERTICAL_PAGED -> R.string.reader_mode_vertical
                        ReaderMode.WEBTOON -> R.string.reader_mode_webtoon
                    }
                    TonalToggleButton(
                        checked = selected,
                        onCheckedChange = { viewModel.setReaderMode(mode) },
                        shapes = ToggleButtonDefaults.shapes(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sheet_reader_mode_${mode.name}")
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(stringResource(labelRes))
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Page Scale Mode
            Text(
                text = stringResource(R.string.reader_scale_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReaderScaleMode.entries.forEach { scaleMode ->
                    val selected = prefs.readerScaleMode == scaleMode
                    val labelRes = when (scaleMode) {
                        ReaderScaleMode.FIT_SCREEN -> R.string.reader_scale_fit
                        ReaderScaleMode.FIT_WIDTH -> R.string.reader_scale_width
                        ReaderScaleMode.FIT_HEIGHT -> R.string.reader_scale_height
                        ReaderScaleMode.CROP -> R.string.reader_scale_crop
                    }
                    TonalToggleButton(
                        checked = selected,
                        onCheckedChange = { viewModel.setReaderScaleMode(scaleMode) },
                        shapes = ToggleButtonDefaults.shapes(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sheet_reader_scale_${scaleMode.name}")
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(stringResource(labelRes))
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Background Palette
            Text(
                text = stringResource(R.string.reader_bg_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReaderBackground.entries.forEach { bg ->
                    val selected = prefs.readerBackground == bg
                    val labelRes = when (bg) {
                        ReaderBackground.AMOLED -> R.string.reader_bg_amoled
                        ReaderBackground.SUMI_INK -> R.string.reader_bg_ink
                        ReaderBackground.SEPIA -> R.string.reader_bg_sepia
                        ReaderBackground.PAPER_LIGHT -> R.string.reader_bg_light
                    }
                    TonalToggleButton(
                        checked = selected,
                        onCheckedChange = { viewModel.setReaderBackground(bg) },
                        shapes = ToggleButtonDefaults.shapes(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sheet_reader_bg_${bg.name}")
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(stringResource(labelRes))
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            // 4. Color Effects & Night Comfort
            Text(
                text = stringResource(R.string.reader_color_effects_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.reader_brightness_dim_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${(prefs.readerBrightnessDim * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = prefs.readerBrightnessDim,
                onValueChange = viewModel::setBrightnessDim,
                valueRange = 0f..0.65f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_brightness_dim_slider")
            )

            ReaderSwitchRow(
                label = stringResource(R.string.reader_grayscale_label),
                checked = prefs.readerGrayscale,
                onCheckedChange = viewModel::setGrayscale,
                testTagName = "sheet_grayscale_switch"
            )
            ReaderSwitchRow(
                label = stringResource(R.string.reader_invert_label),
                checked = prefs.readerInvertColors,
                onCheckedChange = viewModel::setInvertColors,
                testTagName = "sheet_invert_switch"
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // 5. Controls & Page Aids
            ReaderSwitchRow(
                label = stringResource(R.string.reader_thumbnails_toggle),
                checked = prefs.showReaderThumbnails,
                onCheckedChange = viewModel::setShowThumbnails,
                testTagName = "sheet_thumbnails_switch"
            )
            ReaderSwitchRow(
                label = stringResource(R.string.reader_webtoon_gap_label),
                checked = prefs.webtoonSeamlessGap,
                onCheckedChange = viewModel::setWebtoonSeamlessGap,
                testTagName = "sheet_webtoon_gap_switch"
            )
            ReaderSwitchRow(
                label = stringResource(R.string.reader_keep_screen_on_label),
                checked = prefs.keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn,
                testTagName = "sheet_keep_screen_on_switch"
            )
            ReaderSwitchRow(
                label = stringResource(R.string.reader_volume_keys_label),
                checked = prefs.volumeKeysNavigation,
                onCheckedChange = viewModel::setVolumeKeysNavigation,
                testTagName = "sheet_volume_keys_switch"
            )
            ReaderSwitchRow(
                label = stringResource(R.string.reader_transitions_label),
                checked = prefs.pageTransitions,
                onCheckedChange = viewModel::setPageTransitions,
                testTagName = "sheet_transitions_switch"
            )
            ReaderSwitchRow(
                label = stringResource(R.string.reader_mini_info_label),
                checked = prefs.showReaderMiniInfo,
                onCheckedChange = viewModel::setShowMiniInfo,
                testTagName = "sheet_mini_info_switch"
            )
        }
    }
}

@Composable
private fun ReaderSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTagName: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTagName)
        )
    }
}

@Composable
private fun ZoomableReaderPage(
    page: PageItem,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    contentColor: Color,
    onTapPosition: (fractionX: Float, fractionY: Float) -> Unit
) {
    var scale by remember(page.index) { mutableFloatStateOf(1f) }
    var offset by remember(page.index) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 3.4f)
        scale = nextScale
        if (nextScale > 1.02f) {
            val maxX = (containerSize.width * (nextScale - 1f)) / 2f
            val maxY = (containerSize.height * (nextScale - 1f)) / 2f
            offset = Offset(
                x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
            )
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(page.index) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.15f
                        }
                    },
                    onTap = { pos ->
                        val widthPx = size.width.coerceAtLeast(1)
                        val heightPx = size.height.coerceAtLeast(1)
                        onTapPosition(pos.x / widthPx.toFloat(), pos.y / heightPx.toFloat())
                    }
                )
            }
            .then(
                if (scale > 1.02f) Modifier.transformable(state = transformState) else Modifier
            )
    ) {
        SubcomposeAsyncImage(
            model = page.imageUrl,
            contentDescription = stringResource(R.string.reader_page_alt, page.index + 1),
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            loading = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    LoadingIndicator(modifier = Modifier.size(38.dp))
                }
            },
            error = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.reader_page_error),
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }
}

private fun buildPageColorFilter(grayscale: Boolean, invert: Boolean): ColorFilter? {
    if (!grayscale && !invert) return null
    val matrix = ColorMatrix()
    if (grayscale) {
        matrix.setToSaturation(0f)
    }
    if (invert) {
        val invertMatrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.timesAssign(invertMatrix)
    }
    return ColorFilter.colorMatrix(matrix)
}

@Composable
private fun EndOfChapterCard(
    nextChapterTitle: String?,
    textColor: Color,
    onGoNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp)
    ) {
        Text(
            text = stringResource(R.string.reader_end_of_chapter),
            style = MaterialTheme.typography.headlineSmall,
            color = textColor
        )
        Spacer(modifier = Modifier.height(14.dp))
        if (nextChapterTitle != null) {
            Button(
                onClick = onGoNext,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("end_of_chapter_next_button")
            ) {
                Text(text = stringResource(R.string.reader_go_next_chapter, nextChapterTitle))
                Spacer(modifier = Modifier.width(6.dp))
                Icon(imageVector = Icons.Outlined.SkipNext, contentDescription = null)
            }
        } else {
            Text(
                text = stringResource(R.string.reader_all_caught_up),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(72.dp))
    }
}
