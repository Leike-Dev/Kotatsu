@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)

package org.koitharu.kotatsu.ui.explore

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.CatalogSort
import org.koitharu.kotatsu.domain.model.ExploreLayoutMode
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.ui.components.AddCustomSourceDialog
import org.koitharu.kotatsu.ui.components.EditorialEmptyState
import org.koitharu.kotatsu.ui.components.ErrorRetryCard
import org.koitharu.kotatsu.ui.components.ExpressiveLoadingBox
import org.koitharu.kotatsu.ui.components.FeaturedMangaHeroCard
import org.koitharu.kotatsu.ui.components.KotatsuPageHeader
import org.koitharu.kotatsu.ui.components.MangaCoverCard
import org.koitharu.kotatsu.ui.components.MangaDetailedListCard
import org.koitharu.kotatsu.ui.components.SourceFaviconBadge

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onOpenManga: (MangaItem) -> Unit,
    onOpenBrowser: (url: String, title: String) -> Unit,
    onOpenSourcesCatalog: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    bottomClearance: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val localMangas by viewModel.localMangas.collectAsStateWithLifecycle()
    val customSources by viewModel.customSources.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var showCustomizeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusSnackbarMessage) {
        val msg = uiState.statusSnackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbarMessage()
        }
    }

    val gridColumns = when (prefs.exploreLayoutMode) {
        ExploreLayoutMode.GRID_COMPACT -> GridCells.Adaptive(minSize = 112.dp)
        ExploreLayoutMode.GRID_STANDARD -> GridCells.Adaptive(minSize = 148.dp)
        ExploreLayoutMode.GRID_LARGE -> GridCells.Adaptive(minSize = 196.dp)
        ExploreLayoutMode.LIST_DETAILED -> GridCells.Adaptive(minSize = 320.dp)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomClearance)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val showHeroSpotlight = prefs.showExploreSpotlight &&
            !uiState.isLoading &&
            uiState.searchQuery.isBlank() &&
            uiState.selectedTagId == null &&
            uiState.onlineMangas.isNotEmpty()
        val spotlightManga = if (showHeroSpotlight) uiState.onlineMangas.first() else null
        val gridMangas = if (showHeroSpotlight) uiState.onlineMangas.drop(1) else uiState.onlineMangas

        LazyVerticalGrid(
            columns = gridColumns,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomClearance + 20.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("explore_grid")
        ) {
            // Balanced Expressive Header (Import CBZ moved cleanly to Settings)
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    KotatsuPageHeader(
                        title = stringResource(R.string.explore_title),
                        subtitle = stringResource(R.string.explore_subtitle),
                        onOpenSettings = onOpenSettings,
                        actions = {
                            FilledTonalIconButton(
                                onClick = onOpenSourcesCatalog,
                                modifier = Modifier.testTag("header_sources_catalog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = stringResource(R.string.open_sources_catalog_button)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { showCustomizeSheet = true },
                                modifier = Modifier.testTag("customize_explore_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DashboardCustomize,
                                    contentDescription = stringResource(R.string.customize_explore_desc)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Expressive Pill Search Bar
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = viewModel::clearSearch,
                                    modifier = Modifier.testTag("clear_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = stringResource(R.string.clear_search)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explore_search_input")
                    )

                    // Active Sources, Favicons & Built-in Sources Catalog Strip
                    if (prefs.showExploreSourcesBar) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                ToggleButton(
                                    checked = uiState.selectedSourceKey == MangaRepository.SOURCE_FILTER_ALL,
                                    onCheckedChange = { viewModel.onSourceSelected(MangaRepository.SOURCE_FILTER_ALL) },
                                    shapes = ToggleButtonDefaults.shapes(),
                                    modifier = Modifier.testTag("source_chip_all")
                                ) {
                                    Text(stringResource(R.string.source_all_active))
                                }
                            }
                            item {
                                ToggleButton(
                                    checked = uiState.selectedSourceKey == MangaRepository.SOURCE_FILTER_MANGADEX,
                                    onCheckedChange = { viewModel.onSourceSelected(MangaRepository.SOURCE_FILTER_MANGADEX) },
                                    shapes = ToggleButtonDefaults.shapes(),
                                    modifier = Modifier.testTag("source_chip_mangadex")
                                ) {
                                    SourceFaviconBadge(
                                        name = "MangaDex",
                                        baseUrl = "https://mangadex.org",
                                        size = 18.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("MangaDex")
                                }
                            }
                            if (prefs.enableKitsuCatalog) {
                                item {
                                    ToggleButton(
                                        checked = uiState.selectedSourceKey == MangaRepository.SOURCE_FILTER_KITSU,
                                        onCheckedChange = { viewModel.onSourceSelected(MangaRepository.SOURCE_FILTER_KITSU) },
                                        shapes = ToggleButtonDefaults.shapes(),
                                        modifier = Modifier.testTag("source_chip_kitsu")
                                    ) {
                                        SourceFaviconBadge(
                                            name = "Kitsu",
                                            baseUrl = "https://kitsu.io",
                                            size = 18.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Kitsu")
                                    }
                                }
                            }
                            items(customSources.filter { it.enabled }, key = { it.id }) { src ->
                                val key = "${MangaRepository.SOURCE_FILTER_CUSTOM_PREFIX}${src.id}"
                                ToggleButton(
                                    checked = uiState.selectedSourceKey == key,
                                    onCheckedChange = { viewModel.onSourceSelected(key) },
                                    shapes = ToggleButtonDefaults.shapes(),
                                    modifier = Modifier.testTag("source_chip_custom_${src.id}")
                                ) {
                                    SourceFaviconBadge(
                                        name = src.name,
                                        baseUrl = src.baseUrl,
                                        explicitFaviconUrl = src.faviconUrl,
                                        size = 18.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(src.name)
                                }
                            }
                            item {
                                OutlinedToggleButton(
                                    checked = false,
                                    onCheckedChange = { onOpenSourcesCatalog() },
                                    shapes = ToggleButtonDefaults.shapes(),
                                    modifier = Modifier.testTag("explore_open_sources_catalog_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.open_sources_catalog_button))
                                }
                            }
                            item {
                                OutlinedToggleButton(
                                    checked = false,
                                    onCheckedChange = { showAddSourceDialog = true },
                                    shapes = ToggleButtonDefaults.shapes(),
                                    modifier = Modifier.testTag("explore_add_source_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.add_source_button))
                                }
                            }
                            item {
                                OutlinedToggleButton(
                                    checked = false,
                                    onCheckedChange = {
                                        val activeCustom = customSources.firstOrNull {
                                            "${MangaRepository.SOURCE_FILTER_CUSTOM_PREFIX}${it.id}" == uiState.selectedSourceKey
                                        }
                                        val startUrl = activeCustom?.baseUrl ?: "https://mangadex.org"
                                        onOpenBrowser(startUrl, activeCustom?.name ?: "Navegador")
                                    },
                                    shapes = ToggleButtonDefaults.shapes(),
                                    modifier = Modifier.testTag("explore_open_browser_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.open_protected_browser))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Distinct Sort & Language Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Text(
                                text = "${stringResource(R.string.filter_sort_label)}:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }
                        items(CatalogSort.entries) { sort ->
                            val selected = uiState.selectedSort == sort
                            val labelRes = when (sort) {
                                CatalogSort.POPULAR -> R.string.sort_popular
                                CatalogSort.LATEST -> R.string.sort_latest
                                CatalogSort.RATING -> R.string.sort_rating
                            }
                            TonalToggleButton(
                                checked = selected,
                                onCheckedChange = { viewModel.onSortSelected(sort) },
                                shapes = ToggleButtonDefaults.shapes(),
                                colors = ToggleButtonDefaults.tonalToggleButtonColors(
                                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("sort_chip_${sort.name}")
                            ) {
                                Text(stringResource(labelRes))
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${stringResource(R.string.filter_language_label)}:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }
                        items(LanguageFilter.entries) { lang ->
                            val labelRes = when (lang) {
                                LanguageFilter.ALL -> R.string.lang_all
                                LanguageFilter.PT_BR -> R.string.lang_pt_br
                                LanguageFilter.EN -> R.string.lang_en
                                LanguageFilter.ES -> R.string.lang_es
                            }
                            TonalToggleButton(
                                checked = uiState.selectedLanguage == lang,
                                onCheckedChange = { viewModel.onLanguageSelected(lang) },
                                shapes = ToggleButtonDefaults.shapes(),
                                modifier = Modifier.testTag("lang_chip_${lang.name}")
                            ) {
                                Text(stringResource(labelRes))
                            }
                        }
                    }

                    // Optional Expressive Genre Toggle Strip
                    if (prefs.showExploreGenres && uiState.availableTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(uiState.availableTags, key = { it.id }) { tag ->
                                OutlinedToggleButton(
                                    checked = uiState.selectedTagId == tag.id,
                                    onCheckedChange = { viewModel.onTagToggled(tag.id) },
                                    shapes = ToggleButtonDefaults.shapes(),
                                    modifier = Modifier.testTag("tag_chip_${tag.id}")
                                ) {
                                    Text(tag.name)
                                }
                            }
                        }
                    }
                }
            }

            // Imported Local CBZ Section (when local works exist and section is enabled)
            if (prefs.showExploreLocalSection && localMangas.isNotEmpty() && uiState.searchQuery.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.local_library_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(localMangas, key = { it.id }) { localItem ->
                                Box(
                                    modifier = Modifier
                                        .width(148.dp)
                                        .animateItem()
                                ) {
                                    MangaCoverCard(
                                        manga = localItem,
                                        onClick = { onOpenManga(localItem) },
                                        showSourceBadge = prefs.showSourceBadges,
                                        testTagName = "local_manga_card_${localItem.id}"
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.deleteLocalManga(localItem) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("delete_local_${localItem.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = stringResource(R.string.delete_local_manga),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Asymmetric Featured Spotlight Card
            if (spotlightManga != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FeaturedMangaHeroCard(
                        manga = spotlightManga,
                        onOpen = { onOpenManga(spotlightManga) }
                    )
                }
            }

            // Online Catalog Section Header
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.online_catalog_section),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            when {
                uiState.isLoading -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ExpressiveLoadingBox()
                    }
                }

                uiState.errorMessage != null && uiState.onlineMangas.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ErrorRetryCard(
                            message = uiState.errorMessage.orEmpty(),
                            onRetry = viewModel::refreshCatalog
                        )
                    }
                }

                uiState.onlineMangas.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EditorialEmptyState(
                            icon = Icons.Outlined.Explore,
                            title = stringResource(R.string.explore_empty_title),
                            subtitle = stringResource(R.string.explore_empty_subtitle),
                            actionLabel = stringResource(R.string.open_sources_catalog_button),
                            onActionClick = onOpenSourcesCatalog,
                            testTagName = "explore_empty_state"
                        )
                    }
                }

                else -> {
                    items(gridMangas, key = { it.id }) { manga ->
                        if (prefs.exploreLayoutMode == ExploreLayoutMode.LIST_DETAILED) {
                            MangaDetailedListCard(
                                manga = manga,
                                onClick = { onOpenManga(manga) },
                                showSourceBadge = prefs.showSourceBadges,
                                modifier = Modifier.animateItem()
                            )
                        } else {
                            MangaCoverCard(
                                manga = manga,
                                onClick = { onOpenManga(manga) },
                                showSourceBadge = prefs.showSourceBadges,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    if (uiState.canLoadMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp)
                            ) {
                                if (uiState.isLoadingMore) {
                                    LoadingIndicator(modifier = Modifier.size(40.dp))
                                } else {
                                    FilledTonalButton(
                                        onClick = viewModel::loadNextPage,
                                        shapes = ButtonDefaults.shapes(),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("load_more_catalog_button")
                                    ) {
                                        Text(text = stringResource(R.string.load_more))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSourceDialog) {
        AddCustomSourceDialog(
            onDismiss = { showAddSourceDialog = false },
            onConfirmAdd = { name, url, tpl ->
                viewModel.addCustomSource(name, url, tpl)
                showAddSourceDialog = false
            }
        )
    }

    if (showCustomizeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCustomizeSheet = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.customize_explore_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = stringResource(R.string.layout_mode_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExploreLayoutMode.entries.forEach { mode ->
                        val selected = prefs.exploreLayoutMode == mode
                        val labelRes = when (mode) {
                            ExploreLayoutMode.GRID_STANDARD -> R.string.layout_grid_standard
                            ExploreLayoutMode.GRID_COMPACT -> R.string.layout_grid_compact
                            ExploreLayoutMode.GRID_LARGE -> R.string.layout_grid_large
                            ExploreLayoutMode.LIST_DETAILED -> R.string.layout_list_detailed
                        }
                        TonalToggleButton(
                            checked = selected,
                            onCheckedChange = { viewModel.setExploreLayoutMode(mode) },
                            shapes = ToggleButtonDefaults.shapes(),
                            modifier = Modifier
                                .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
                                .testTag("sheet_explore_layout_${mode.name}")
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Text(
                    text = stringResource(R.string.explore_sections_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                QuickCustomizeSwitch(
                    label = stringResource(R.string.toggle_spotlight_label),
                    checked = prefs.showExploreSpotlight,
                    onCheckedChange = viewModel::setShowSpotlight
                )
                QuickCustomizeSwitch(
                    label = stringResource(R.string.toggle_sources_bar_label),
                    checked = prefs.showExploreSourcesBar,
                    onCheckedChange = viewModel::setShowSourcesBar
                )
                QuickCustomizeSwitch(
                    label = stringResource(R.string.toggle_genres_strip_label),
                    checked = prefs.showExploreGenres,
                    onCheckedChange = viewModel::setShowGenres
                )
                QuickCustomizeSwitch(
                    label = stringResource(R.string.toggle_local_section_label),
                    checked = prefs.showExploreLocalSection,
                    onCheckedChange = viewModel::setShowLocalSection
                )
                QuickCustomizeSwitch(
                    label = stringResource(R.string.toggle_badges_label),
                    checked = prefs.showSourceBadges,
                    onCheckedChange = viewModel::setShowSourceBadges
                )
            }
        }
    }
}

@Composable
private fun QuickCustomizeSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
