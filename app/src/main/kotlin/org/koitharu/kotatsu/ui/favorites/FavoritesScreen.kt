@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package org.koitharu.kotatsu.ui.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.domain.model.FavoriteCategory
import org.koitharu.kotatsu.domain.model.FavoritesFilterMode
import org.koitharu.kotatsu.domain.model.FavoritesSortMode
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.ui.components.CategorySelectBottomSheet
import org.koitharu.kotatsu.ui.components.EditorialEmptyState
import org.koitharu.kotatsu.ui.components.KotatsuPageHeader
import org.koitharu.kotatsu.ui.components.MangaCoverCard

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpenManga: (MangaItem) -> Unit,
    onNavigateExplore: () -> Unit,
    onOpenStatistics: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    bottomClearance: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val allFavorites by viewModel.allFavoritesCount.collectAsStateWithLifecycle()
    val displayedItems by viewModel.displayedShelfItems.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterMode by viewModel.filterMode.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedMangaIds by viewModel.selectedMangaIds.collectAsStateWithLifecycle()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
    val feedbackEvent by viewModel.feedbackEvent.collectAsStateWithLifecycle()

    var showSearchBar by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showManageSheet by remember { mutableStateOf(false) }
    var showBatchCategorySheet by remember { mutableStateOf(false) }
    var categoryToRename by remember { mutableStateOf<FavoriteCategory?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val totalNewChaptersInShelf = remember(allFavorites) {
        allFavorites.sumOf { it.newChaptersDelta }
    }
    val worksWithNewCount = remember(allFavorites) {
        allFavorites.count { it.newChaptersDelta > 0 }
    }

    BackHandler(enabled = selectionMode || showSearchBar || searchQuery.isNotEmpty()) {
        if (selectionMode) {
            viewModel.clearSelection()
        } else {
            viewModel.onSearchQueryChanged("")
            showSearchBar = false
        }
    }

    LaunchedEffect(feedbackEvent) {
        val event = feedbackEvent ?: return@LaunchedEffect
        val message = when (event) {
            FavoritesFeedbackEvent.EmptyShelfForUpdates ->
                context.getString(R.string.favorites_updates_empty)
            is FavoritesFeedbackEvent.UpdatesFound ->
                context.getString(
                    R.string.favorites_updates_found_format,
                    event.totalNewChapters,
                    event.mangasWithNewCount
                )
            is FavoritesFeedbackEvent.NoUpdatesFound ->
                context.getString(R.string.favorites_updates_none, event.checkedCount)
            is FavoritesFeedbackEvent.BatchCategoriesSaved ->
                context.getString(R.string.favorites_batch_categories_saved, event.count)
            is FavoritesFeedbackEvent.BatchCaughtUpSaved ->
                context.getString(R.string.favorites_batch_caught_up_done, event.count)
            is FavoritesFeedbackEvent.BatchRemovedSaved ->
                context.getString(R.string.favorites_batch_removed_done, event.count)
        }
        viewModel.consumeFeedbackEvent()
        snackbarHostState.showSnackbar(message)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("favorites_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            KotatsuPageHeader(
                title = stringResource(R.string.favorites_title),
                subtitle = if (totalNewChaptersInShelf > 0) {
                    stringResource(
                        R.string.favorites_updates_found_format,
                        totalNewChaptersInShelf,
                        worksWithNewCount
                    )
                } else {
                    null
                },
                countBadge = allFavorites.size,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(horizontal = 16.dp),
                actions = {
                    FilledTonalIconButton(
                        onClick = viewModel::checkUpdatesNow,
                        enabled = !isCheckingUpdates,
                        modifier = Modifier.testTag("favorites_check_updates_button")
                    ) {
                        if (isCheckingUpdates) {
                            LoadingIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.favorites_check_updates_desc)
                            )
                        }
                    }

                    if (onOpenStatistics != null) {
                        FilledTonalIconButton(
                            onClick = onOpenStatistics,
                            modifier = Modifier.testTag("favorites_open_stats_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BarChart,
                                contentDescription = stringResource(R.string.stats_open_action)
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = {
                            if (showSearchBar && searchQuery.isNotEmpty()) {
                                viewModel.onSearchQueryChanged("")
                            }
                            showSearchBar = !showSearchBar
                        },
                        modifier = Modifier.testTag("favorites_toggle_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.favorites_search_toggle_desc)
                        )
                    }
                }
            )

            // Update Check Progress Banner
            AnimatedVisibility(
                visible = isCheckingUpdates && updateProgress != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val (done, total) = updateProgress ?: (0 to 1)
                val fraction = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = stringResource(R.string.favorites_checking_progress, done, total),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }

            // Collapsible Shelf Search Bar
            AnimatedVisibility(
                visible = showSearchBar || searchQuery.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = { Text(stringResource(R.string.favorites_search_placeholder)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    viewModel.onSearchQueryChanged("")
                                } else {
                                    showSearchBar = false
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.clear_search)
                            )
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
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("favorites_search_input")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // M3 Expressive Morphing Category ToggleButtons + Manage Categories Pill
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    ToggleButton(
                        checked = selectedCategoryId == null,
                        onCheckedChange = { viewModel.selectCategory(null) },
                        shapes = ToggleButtonDefaults.shapes(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("category_tab_all")
                    ) {
                        Text("${stringResource(R.string.all_favorites_tab)} (${allFavorites.size})")
                    }
                }
                items(categories, key = { it.id }) { category ->
                    ToggleButton(
                        checked = selectedCategoryId == category.id,
                        onCheckedChange = { viewModel.selectCategory(category.id) },
                        shapes = ToggleButtonDefaults.shapes(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("category_tab_${category.id}")
                    ) {
                        Text("${category.name} (${category.mangaCount})")
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = { showManageSheet = true },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("manage_categories_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.manage_categories),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.manage_categories),
                            maxLines = 1
                        )
                    }
                }
            }

            // Secondary Organization Strip: Quick Status Filters + Custom Sort + Multi-Select
            if (allFavorites.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Box {
                            OutlinedButton(
                                onClick = { showSortMenu = true },
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("favorites_sort_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SwapVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(sortModeLabelRes(sortMode)),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                FavoritesSortMode.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(sortModeLabelRes(option))) },
                                        leadingIcon = {
                                            if (sortMode == option) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectSortMode(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedToggleButton(
                            checked = selectionMode,
                            onCheckedChange = { viewModel.toggleSelectionMode() },
                            shapes = ToggleButtonDefaults.shapes(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("favorites_multi_select_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = stringResource(R.string.favorites_select_mode_desc),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    items(FavoritesFilterMode.entries) { mode ->
                        val selected = filterMode == mode
                        val label = when (mode) {
                            FavoritesFilterMode.ALL -> stringResource(R.string.favorites_filter_all)
                            FavoritesFilterMode.NEW_CHAPTERS -> {
                                val base = stringResource(R.string.favorites_filter_new)
                                if (worksWithNewCount > 0) "$base ($worksWithNewCount)" else base
                            }
                            FavoritesFilterMode.UNREAD -> stringResource(R.string.favorites_filter_unread)
                            FavoritesFilterMode.IN_PROGRESS -> stringResource(R.string.favorites_filter_reading)
                            FavoritesFilterMode.CAUGHT_UP -> stringResource(R.string.favorites_filter_caught_up)
                        }
                        TonalToggleButton(
                            checked = selected,
                            onCheckedChange = { viewModel.selectFilterMode(mode) },
                            shapes = ToggleButtonDefaults.shapes(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("favorites_filter_${mode.name}")
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(text = label)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val hasActiveQueryOrFilter = searchQuery.isNotBlank() || filterMode != FavoritesFilterMode.ALL

            if (displayedItems.isEmpty()) {
                if (allFavorites.isEmpty()) {
                    EditorialEmptyState(
                        icon = Icons.Outlined.FavoriteBorder,
                        title = stringResource(R.string.favorites_empty_title),
                        subtitle = stringResource(R.string.favorites_empty_subtitle),
                        actionLabel = stringResource(R.string.go_to_explore),
                        onActionClick = onNavigateExplore,
                        testTagName = "favorites_empty_state"
                    )
                } else if (hasActiveQueryOrFilter) {
                    EditorialEmptyState(
                        icon = Icons.Outlined.FilterAltOff,
                        title = stringResource(R.string.favorites_filter_empty_title),
                        subtitle = stringResource(R.string.favorites_filter_empty_sub),
                        actionLabel = stringResource(R.string.favorites_clear_filters),
                        onActionClick = viewModel::clearActiveFilters,
                        testTagName = "favorites_filtered_empty_state"
                    )
                } else {
                    EditorialEmptyState(
                        icon = Icons.Outlined.CollectionsBookmark,
                        title = stringResource(R.string.category_empty_title),
                        subtitle = stringResource(R.string.category_empty_subtitle),
                        actionLabel = stringResource(R.string.go_to_explore),
                        onActionClick = onNavigateExplore,
                        testTagName = "favorites_empty_state"
                    )
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isCheckingUpdates,
                    onRefresh = viewModel::checkUpdatesNow,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val extraBatchClearance = if (selectionMode) 84.dp else 0.dp
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 152.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = bottomClearance + extraBatchClearance + 20.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("favorites_grid")
                    ) {
                        items(displayedItems, key = { it.manga.id }) { shelfItem ->
                            val isCardSelected = shelfItem.manga.id in selectedMangaIds
                            MangaCoverCard(
                                manga = shelfItem.manga,
                                progressFraction = shelfItem.progressFraction,
                                showSourceBadge = prefs.showSourceBadges,
                                unreadCount = shelfItem.unreadCount,
                                newChaptersCount = shelfItem.newChaptersCount,
                                selectionMode = selectionMode,
                                isSelected = isCardSelected,
                                onClick = {
                                    if (selectionMode) {
                                        viewModel.toggleMangaSelection(shelfItem.manga.id)
                                    } else {
                                        onOpenManga(shelfItem.manga)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleMangaSelection(shelfItem.manga.id)
                                },
                                modifier = Modifier.animateItem(),
                                testTagName = "favorite_manga_card_${shelfItem.manga.id}"
                            )
                        }
                    }
                }
            }
        }

        // Expressive Multi-Select Batch Action Toolbar
        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = bottomClearance + 8.dp
                )
                .testTag("favorites_batch_toolbar")
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = viewModel::clearSelection,
                        modifier = Modifier.testTag("batch_close_selection_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.batch_exit_desc)
                        )
                    }

                    Text(
                        text = pluralStringResource(
                            R.plurals.selected_works_count,
                            selectedMangaIds.size,
                            selectedMangaIds.size
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    IconButton(
                        onClick = viewModel::selectAllVisible,
                        modifier = Modifier.testTag("batch_select_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SelectAll,
                            contentDescription = stringResource(R.string.batch_select_all_desc)
                        )
                    }

                    IconButton(
                        onClick = { if (selectedMangaIds.isNotEmpty()) showBatchCategorySheet = true },
                        enabled = selectedMangaIds.isNotEmpty(),
                        modifier = Modifier.testTag("batch_categories_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CollectionsBookmark,
                            contentDescription = stringResource(R.string.batch_move_categories)
                        )
                    }

                    IconButton(
                        onClick = viewModel::markSelectedCaughtUp,
                        enabled = selectedMangaIds.isNotEmpty(),
                        modifier = Modifier.testTag("batch_mark_caught_up_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DoneAll,
                            contentDescription = stringResource(R.string.batch_mark_caught_up)
                        )
                    }

                    IconButton(
                        onClick = viewModel::removeSelectedFromShelf,
                        enabled = selectedMangaIds.isNotEmpty(),
                        modifier = Modifier.testTag("batch_remove_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.batch_remove_shelf)
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = bottomClearance + (if (selectionMode) 82.dp else 12.dp)
                )
        )
    }

    if (showBatchCategorySheet) {
        CategorySelectBottomSheet(
            categories = categories,
            initialSelectedIds = selectedCategoryId?.let { setOf(it) } ?: emptySet(),
            onDismiss = { showBatchCategorySheet = false },
            onSaveSelection = { chosenIds ->
                viewModel.applyBatchCategories(chosenIds)
                showBatchCategorySheet = false
            },
            onCreateCategory = viewModel::createCategory
        )
    }

    if (showManageSheet) {
        var newName by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showManageSheet = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.manage_categories),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.add_category_title)) },
                        placeholder = { Text(stringResource(R.string.category_name_placeholder)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_category_input")
                    )
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                viewModel.createCategory(newName.trim())
                                newName = ""
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        enabled = newName.isNotBlank(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("create_category_button")
                    ) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.create_action))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(categories, key = { it.id }) { cat ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.manga_works_count,
                                            cat.mangaCount,
                                            cat.mangaCount
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { categoryToRename = cat },
                                    modifier = Modifier.testTag("rename_category_${cat.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.rename_category_title)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteCategory(cat.id) },
                                    modifier = Modifier.testTag("delete_category_${cat.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.delete_action),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    categoryToRename?.let { target ->
        var updatedText by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { categoryToRename = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.rename_category_title)) },
            text = {
                OutlinedTextField(
                    value = updatedText,
                    onValueChange = { updatedText = it },
                    label = { Text(stringResource(R.string.category_name_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (updatedText.isNotBlank()) {
                            viewModel.renameCategory(target.id, updatedText)
                        }
                        categoryToRename = null
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.save_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToRename = null }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }
}

private fun sortModeLabelRes(mode: FavoritesSortMode): Int = when (mode) {
    FavoritesSortMode.ADDED_RECENT -> R.string.favorites_sort_added
    FavoritesSortMode.LAST_READ -> R.string.favorites_sort_last_read
    FavoritesSortMode.ALPHABETICAL -> R.string.favorites_sort_alpha
    FavoritesSortMode.MOST_UNREAD -> R.string.favorites_sort_unread
}
