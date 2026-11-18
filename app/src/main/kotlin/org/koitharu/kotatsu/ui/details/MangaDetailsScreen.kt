@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package org.koitharu.kotatsu.ui.details

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.data.local.db.ReadingHistoryEntity
import org.koitharu.kotatsu.domain.model.ChapterDisplayMode
import org.koitharu.kotatsu.domain.model.ChapterItem
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag
import org.koitharu.kotatsu.ui.components.CategorySelectBottomSheet
import org.koitharu.kotatsu.ui.components.CoverPlaceholder
import org.koitharu.kotatsu.ui.components.ErrorRetryCard
import org.koitharu.kotatsu.ui.components.ExpressiveLoadingBox

@Composable
fun MangaDetailsScreen(
    viewModel: MangaDetailsViewModel,
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String, initialPage: Int) -> Unit,
    onOpenInBrowser: (url: String, title: String) -> Unit,
    onSearchTag: (tag: MangaTag) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val assignedCategoryIds by viewModel.assignedCategoryIds.collectAsStateWithLifecycle()
    val historyEntry by viewModel.historyEntry.collectAsStateWithLifecycle()
    val completedChapterIds by viewModel.completedChapterIds.collectAsStateWithLifecycle()

    var showCategorySheet by remember { mutableStateOf(false) }
    var synopsisExpanded by remember { mutableStateOf(false) }
    var showCoverPreview by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("manga_details_screen")
    ) {
        when {
            uiState.isLoading -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    ExpressiveLoadingBox()
                }
            }

            uiState.errorMessage != null || uiState.manga == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(12.dp)
                            .testTag("details_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back_action)
                        )
                    }
                    ErrorRetryCard(
                        message = uiState.errorMessage ?: stringResource(R.string.details_load_error),
                        onRetry = viewModel::loadAll
                    )
                }
            }

            else -> {
                val manga = uiState.manga!!
                val visibleChapters = remember(
                    uiState.chapters,
                    uiState.sortDescending,
                    uiState.unreadOnly,
                    uiState.chapterSearchQuery,
                    completedChapterIds
                ) {
                    uiState.filteredChapters(completedChapterIds)
                }

                val primaryTargetChapter = remember(uiState.chapters, historyEntry) {
                    val histId = historyEntry?.chapterId
                    uiState.chapters.firstOrNull { it.id == histId } ?: uiState.chapters.firstOrNull()
                }
                val primaryTargetPage = if (historyEntry?.chapterId == primaryTargetChapter?.id) {
                    historyEntry?.currentPage ?: 0
                } else {
                    0
                }

                fun shareManga() {
                    val shareText = buildString {
                        append(manga.title)
                        if (!manga.sourceUrl.isNullOrBlank()) {
                            append("\n").append(manga.sourceUrl)
                        }
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, manga.title))
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideLayout = maxWidth >= 720.dp
                    val gridColumnsPerRow = if (isWideLayout) 6 else 4

                    if (isWideLayout) {
                        // Canonical Two-Pane Tablet Layout
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(370.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(18.dp)
                            ) {
                                TopDetailsActionBar(
                                    manga = manga,
                                    onBack = onBack,
                                    onOpenInBrowser = {
                                        manga.sourceUrl?.let { onOpenInBrowser(it, manga.title) }
                                    },
                                    onShare = ::shareManga
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                MangaHeroSummarySection(
                                    manga = manga,
                                    isFavorited = assignedCategoryIds.isNotEmpty(),
                                    historyEntry = historyEntry,
                                    primaryChapter = primaryTargetChapter,
                                    synopsisExpanded = synopsisExpanded,
                                    onCoverClick = { if (manga.coverUrl.isNotBlank()) showCoverPreview = true },
                                    onToggleSynopsis = { synopsisExpanded = !synopsisExpanded },
                                    onPrimaryReadClick = {
                                        primaryTargetChapter?.let { ch ->
                                            onOpenChapter(ch.id, primaryTargetPage)
                                        }
                                    },
                                    onFavoriteClick = { showCategorySheet = true },
                                    onTagClick = onSearchTag
                                )
                            }

                            LazyColumn(
                                contentPadding = PaddingValues(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("chapters_list")
                            ) {
                                item {
                                    ChaptersSectionHeader(
                                        manga = manga,
                                        totalChapters = uiState.chapters.size,
                                        selectedLanguage = uiState.selectedLanguage,
                                        displayMode = prefs.chapterDisplayMode,
                                        sortDescending = uiState.sortDescending,
                                        unreadOnly = uiState.unreadOnly,
                                        searchQuery = uiState.chapterSearchQuery,
                                        isLoadingChapters = uiState.isChaptersLoading,
                                        onSelectLanguage = viewModel::onLanguageSelected,
                                        onSelectDisplayMode = viewModel::setChapterDisplayMode,
                                        onToggleOrder = viewModel::toggleChapterOrder,
                                        onToggleUnreadOnly = viewModel::toggleUnreadOnly,
                                        onSearchQueryChanged = viewModel::onChapterSearchChanged,
                                        onOpenWebFallback = {
                                            manga.sourceUrl?.let { onOpenInBrowser(it, manga.title) }
                                        }
                                    )
                                }

                                chapterItemsSection(
                                    chapters = visibleChapters,
                                    isChaptersLoading = uiState.isChaptersLoading,
                                    displayMode = prefs.chapterDisplayMode,
                                    gridColumns = gridColumnsPerRow,
                                    completedChapterIds = completedChapterIds,
                                    historyEntry = historyEntry,
                                    horizontalPadding = 0.dp,
                                    onOpenChapter = onOpenChapter,
                                    onToggleChapterRead = viewModel::toggleChapterRead
                                )
                            }
                        }
                    } else {
                        // Compact Mobile Expressive Stacked Layout
                        val burstAccentShape = MaterialShapes.SoftBurst.toShape()
                        val cookieAccentShape = MaterialShapes.Cookie9Sided.toShape()

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 36.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .testTag("chapters_list")
                        ) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (manga.coverUrl.isNotBlank()) {
                                        SubcomposeAsyncImage(
                                            model = manga.coverUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(265.dp)
                                                .alpha(0.24f)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(172.dp)
                                            .align(Alignment.TopEnd)
                                            .offset(x = 38.dp, y = (-24).dp)
                                            .clip(burstAccentShape)
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .align(Alignment.TopStart)
                                            .offset(x = (-24).dp, y = 40.dp)
                                            .clip(cookieAccentShape)
                                            .background(
                                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f)
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(265.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.background.copy(alpha = 0.18f),
                                                        MaterialTheme.colorScheme.background
                                                    )
                                                )
                                            )
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        TopDetailsActionBar(
                                            manga = manga,
                                            onBack = onBack,
                                            onOpenInBrowser = {
                                                manga.sourceUrl?.let { onOpenInBrowser(it, manga.title) }
                                            },
                                            onShare = ::shareManga
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        MangaHeroSummarySection(
                                            manga = manga,
                                            isFavorited = assignedCategoryIds.isNotEmpty(),
                                            historyEntry = historyEntry,
                                            primaryChapter = primaryTargetChapter,
                                            synopsisExpanded = synopsisExpanded,
                                            onCoverClick = { if (manga.coverUrl.isNotBlank()) showCoverPreview = true },
                                            onToggleSynopsis = { synopsisExpanded = !synopsisExpanded },
                                            onPrimaryReadClick = {
                                                primaryTargetChapter?.let { ch ->
                                                    onOpenChapter(ch.id, primaryTargetPage)
                                                }
                                            },
                                            onFavoriteClick = { showCategorySheet = true },
                                            onTagClick = onSearchTag
                                        )
                                    }
                                }
                            }

                            item {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    ChaptersSectionHeader(
                                        manga = manga,
                                        totalChapters = uiState.chapters.size,
                                        selectedLanguage = uiState.selectedLanguage,
                                        displayMode = prefs.chapterDisplayMode,
                                        sortDescending = uiState.sortDescending,
                                        unreadOnly = uiState.unreadOnly,
                                        searchQuery = uiState.chapterSearchQuery,
                                        isLoadingChapters = uiState.isChaptersLoading,
                                        onSelectLanguage = viewModel::onLanguageSelected,
                                        onSelectDisplayMode = viewModel::setChapterDisplayMode,
                                        onToggleOrder = viewModel::toggleChapterOrder,
                                        onToggleUnreadOnly = viewModel::toggleUnreadOnly,
                                        onSearchQueryChanged = viewModel::onChapterSearchChanged,
                                        onOpenWebFallback = {
                                            manga.sourceUrl?.let { onOpenInBrowser(it, manga.title) }
                                        }
                                    )
                                }
                            }

                            chapterItemsSection(
                                chapters = visibleChapters,
                                isChaptersLoading = uiState.isChaptersLoading,
                                displayMode = prefs.chapterDisplayMode,
                                gridColumns = gridColumnsPerRow,
                                completedChapterIds = completedChapterIds,
                                historyEntry = historyEntry,
                                horizontalPadding = 16.dp,
                                onOpenChapter = onOpenChapter,
                                onToggleChapterRead = viewModel::toggleChapterRead
                            )
                        }
                    }
                }

                if (showCoverPreview) {
                    Dialog(onDismissRequest = { showCoverPreview = false }) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 420.dp)
                                .clickable { showCoverPreview = false }
                        ) {
                            SubcomposeAsyncImage(
                                model = manga.coverUrl,
                                contentDescription = manga.title,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.extraLarge)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCategorySheet) {
        CategorySelectBottomSheet(
            categories = categories,
            initialSelectedIds = assignedCategoryIds,
            onDismiss = { showCategorySheet = false },
            onSaveSelection = { selected ->
                viewModel.saveFavoriteCategories(selected)
                showCategorySheet = false
            },
            onCreateCategory = viewModel::createNewCategory
        )
    }
}

@Composable
private fun TopDetailsActionBar(
    manga: MangaItem,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.testTag("details_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.back_action)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!manga.sourceUrl.isNullOrBlank()) {
                FilledTonalIconButton(
                    onClick = onOpenInBrowser,
                    modifier = Modifier.testTag("details_open_browser_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInBrowser,
                        contentDescription = stringResource(R.string.open_in_webview)
                    )
                }
            }
            FilledTonalIconButton(
                onClick = onShare,
                modifier = Modifier.testTag("details_share_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.share_manga)
                )
            }
        }
    }
}

@Composable
private fun MangaHeroSummarySection(
    manga: MangaItem,
    isFavorited: Boolean,
    historyEntry: ReadingHistoryEntity?,
    primaryChapter: ChapterItem?,
    synopsisExpanded: Boolean,
    onCoverClick: () -> Unit,
    onToggleSynopsis: () -> Unit,
    onPrimaryReadClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onTagClick: (MangaTag) -> Unit
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .width(126.dp)
                .height(186.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onCoverClick)
        ) {
            SubcomposeAsyncImage(
                model = manga.coverUrl,
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { CoverPlaceholder(manga.title) },
                error = { CoverPlaceholder(manga.title) }
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = manga.badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }

                val statusLabel = when (manga.status.lowercase()) {
                    "ongoing", "current" -> stringResource(R.string.status_ongoing)
                    "completed", "finished" -> stringResource(R.string.status_completed)
                    "hiatus" -> stringResource(R.string.status_hiatus)
                    "cancelled", "canceled" -> stringResource(R.string.status_cancelled)
                    "upcoming", "unreleased", "tba" -> stringResource(R.string.status_upcoming)
                    "local" -> stringResource(R.string.status_local)
                    else -> manga.status
                }
                if (statusLabel.isNotBlank() && statusLabel != "online") {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = statusLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = manga.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = manga.author.ifBlank { stringResource(R.string.unknown_author) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // Expressive Balanced Primary Read + Favorite Buttons
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onPrimaryReadClick,
            enabled = primaryChapter != null,
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier
                .weight(1.2f)
                .minimumInteractiveComponentSize()
                .testTag("primary_read_button")
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            val label = if (historyEntry != null && primaryChapter != null) {
                stringResource(
                    R.string.continue_chapter_format,
                    primaryChapter.numberLabel.ifBlank { "1" }
                )
            } else {
                stringResource(R.string.start_reading)
            }
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        TonalToggleButton(
            checked = isFavorited,
            onCheckedChange = { onFavoriteClick() },
            shapes = ToggleButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("favorite_manga_button")
        ) {
            Icon(
                imageVector = if (isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (isFavorited) R.string.in_favorites_button else R.string.favorite_button
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // Interactive Genre Pills (clicking searches that genre in Descobrir!)
    if (manga.tags.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            manga.tags.forEach { tag ->
                Surface(
                    onClick = { onTagClick(tag) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    if (manga.description.isNotBlank()) {
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                Text(
                    text = stringResource(R.string.synopsis_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = manga.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (manga.description.length > 180) {
                    TextButton(
                        onClick = onToggleSynopsis,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("toggle_synopsis_button")
                    ) {
                        Text(
                            text = stringResource(
                                if (synopsisExpanded) R.string.collapse_synopsis else R.string.expand_synopsis
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChaptersSectionHeader(
    manga: MangaItem,
    totalChapters: Int,
    selectedLanguage: LanguageFilter,
    displayMode: ChapterDisplayMode,
    sortDescending: Boolean,
    unreadOnly: Boolean,
    searchQuery: String,
    isLoadingChapters: Boolean,
    onSelectLanguage: (LanguageFilter) -> Unit,
    onSelectDisplayMode: (ChapterDisplayMode) -> Unit,
    onToggleOrder: () -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onOpenWebFallback: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.chapters_header_format, totalChapters),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (isLoadingChapters) {
                    Spacer(modifier = Modifier.width(10.dp))
                    LoadingIndicator(modifier = Modifier.size(26.dp))
                }
            }

            // Chapter View Modes Switcher (List / Detailed Cards / Numbered Grid)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ChapterDisplayMode.entries.forEach { mode ->
                    val icon = when (mode) {
                        ChapterDisplayMode.LIST -> Icons.AutoMirrored.Outlined.ViewList
                        ChapterDisplayMode.DETAILED -> Icons.Outlined.ViewAgenda
                        ChapterDisplayMode.GRID -> Icons.Outlined.GridView
                    }
                    val labelRes = when (mode) {
                        ChapterDisplayMode.LIST -> R.string.chapter_view_list
                        ChapterDisplayMode.DETAILED -> R.string.chapter_view_detailed
                        ChapterDisplayMode.GRID -> R.string.chapter_view_grid
                    }
                    ToggleButton(
                        checked = displayMode == mode,
                        onCheckedChange = { onSelectDisplayMode(mode) },
                        shapes = ToggleButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("chapter_display_${mode.name}")
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(labelRes),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sort + Unread + Language Filter Strip
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                OutlinedToggleButton(
                    checked = !sortDescending,
                    onCheckedChange = { onToggleOrder() },
                    shapes = ToggleButtonDefaults.shapes(),
                    modifier = Modifier.testTag("sort_chapters_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(
                            if (sortDescending) R.string.chapters_order_desc
                            else R.string.chapters_order_asc
                        )
                    )
                }
            }

            item {
                OutlinedToggleButton(
                    checked = unreadOnly,
                    onCheckedChange = { onToggleUnreadOnly() },
                    shapes = ToggleButtonDefaults.shapes(),
                    modifier = Modifier.testTag("filter_unread_chapters_button")
                ) {
                    Text(stringResource(R.string.chapter_filter_unread))
                }
            }

            if (manga.sourceType == MangaSourceType.ONLINE_MANGADEX ||
                manga.sourceType == MangaSourceType.ONLINE_KITSU
            ) {
                items(LanguageFilter.entries) { lang ->
                    val selected = selectedLanguage == lang
                    val labelRes = when (lang) {
                        LanguageFilter.ALL -> R.string.lang_all
                        LanguageFilter.PT_BR -> R.string.lang_pt_br
                        LanguageFilter.EN -> R.string.lang_en
                        LanguageFilter.ES -> R.string.lang_es
                    }
                    TonalToggleButton(
                        checked = selected,
                        onCheckedChange = { onSelectLanguage(lang) },
                        shapes = ToggleButtonDefaults.shapes(),
                        modifier = Modifier.testTag("details_lang_${lang.name}")
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(stringResource(labelRes))
                    }
                }
            }
        }

        if (totalChapters > 8) {
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text(stringResource(R.string.chapter_search_placeholder)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(imageVector = Icons.Outlined.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chapter_search_input")
            )
        }

        if (!isLoadingChapters && totalChapters == 0 && !manga.sourceUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onOpenWebFallback,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Outlined.OpenInBrowser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.open_in_webview))
            }
        }
    }
}

private fun LazyListScope.chapterItemsSection(
    chapters: List<ChapterItem>,
    isChaptersLoading: Boolean,
    displayMode: ChapterDisplayMode,
    gridColumns: Int,
    completedChapterIds: Set<String>,
    historyEntry: ReadingHistoryEntity?,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onOpenChapter: (String, Int) -> Unit,
    onToggleChapterRead: (String, Boolean) -> Unit
) {
    if (!isChaptersLoading && chapters.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.no_chapters_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 20.dp)
            )
        }
        return
    }

    when (displayMode) {
        ChapterDisplayMode.GRID -> {
            val chunkedRows = chapters.chunked(gridColumns)
            items(chunkedRows, key = { row -> "grid_row_${row.first().id}" }) { rowChapters ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 4.dp)
                ) {
                    rowChapters.forEach { chapter ->
                        val isCompleted = chapter.id in completedChapterIds
                        val isCurrent = historyEntry?.chapterId == chapter.id
                        Surface(
                            onClick = {
                                val page = if (isCurrent) historyEntry?.currentPage ?: 0 else 0
                                onOpenChapter(chapter.id, page)
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isCompleted -> MaterialTheme.colorScheme.surfaceContainerLow
                                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.45f)
                                .testTag("chapter_grid_${chapter.id}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                Text(
                                    text = chapter.numberLabel.ifBlank { chapter.title.take(6) },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = when {
                                        isCurrent -> MaterialTheme.colorScheme.onPrimary
                                        isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    repeat(gridColumns - rowChapters.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        ChapterDisplayMode.DETAILED -> {
            items(chapters, key = { it.id }) { chapter ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding, vertical = 4.dp)
                        .animateItem()
                ) {
                    ChapterDetailedCardItem(
                        chapter = chapter,
                        isCompleted = chapter.id in completedChapterIds,
                        historyEntry = historyEntry,
                        onClick = {
                            val page = if (historyEntry?.chapterId == chapter.id) {
                                historyEntry?.currentPage ?: 0
                            } else 0
                            onOpenChapter(chapter.id, page)
                        },
                        onToggleRead = {
                            onToggleChapterRead(chapter.id, chapter.id in completedChapterIds)
                        }
                    )
                }
            }
        }

        ChapterDisplayMode.LIST -> {
            items(chapters, key = { it.id }) { chapter ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding, vertical = 3.dp)
                        .animateItem()
                ) {
                    ChapterRowItem(
                        chapter = chapter,
                        isCompleted = chapter.id in completedChapterIds,
                        historyEntry = historyEntry,
                        onClick = {
                            val page = if (historyEntry?.chapterId == chapter.id) {
                                historyEntry?.currentPage ?: 0
                            } else 0
                            onOpenChapter(chapter.id, page)
                        },
                        onToggleRead = {
                            onToggleChapterRead(chapter.id, chapter.id in completedChapterIds)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRowItem(
    chapter: ChapterItem,
    isCompleted: Boolean,
    historyEntry: ReadingHistoryEntity?,
    onClick: () -> Unit,
    onToggleRead: () -> Unit
) {
    val isCurrentHistoryChapter = historyEntry?.chapterId == chapter.id
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrentHistoryChapter) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("chapter_row_${chapter.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCompleted && !isCurrentHistoryChapter) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chapter.language.isNotBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = chapter.language,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
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
            }

            if (isCurrentHistoryChapter && historyEntry != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = stringResource(
                            R.string.chapter_progress_format,
                            historyEntry.currentPage + 1,
                            historyEntry.totalPages.coerceAtLeast(1)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            IconButton(
                onClick = onToggleRead,
                modifier = Modifier.testTag("toggle_read_${chapter.id}")
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                    contentDescription = stringResource(R.string.toggle_chapter_read),
                    tint = if (isCompleted) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChapterDetailedCardItem(
    chapter: ChapterItem,
    isCompleted: Boolean,
    historyEntry: ReadingHistoryEntity?,
    onClick: () -> Unit,
    onToggleRead: () -> Unit
) {
    val isCurrent = historyEntry?.chapterId == chapter.id
    val pubDate = remember(chapter.publishedAt) {
        if (chapter.publishedAt > 0L) {
            DateUtils.getRelativeTimeSpanString(
                chapter.publishedAt,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS
            ).toString()
        } else ""
    }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chapter_detailed_${chapter.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chapter.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCompleted && !isCurrent) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (chapter.language.isNotBlank()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = chapter.language,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (chapter.scanlator.isNotBlank()) {
                            Text(
                                text = chapter.scanlator,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (pubDate.isNotBlank()) {
                            Text(
                                text = "• $pubDate",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = onToggleRead) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                        contentDescription = stringResource(R.string.toggle_chapter_read),
                        tint = if (isCompleted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isCurrent && historyEntry != null && historyEntry.totalPages > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        ((historyEntry.currentPage + 1).toFloat() / historyEntry.totalPages.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}
