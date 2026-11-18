@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.koitharu.kotatsu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.DefaultStartTab
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.NavLabelBehavior
import org.koitharu.kotatsu.ui.bookmarks.BookmarksScreen
import org.koitharu.kotatsu.ui.bookmarks.BookmarksViewModel
import org.koitharu.kotatsu.ui.browser.SourceBrowserScreen
import org.koitharu.kotatsu.ui.details.MangaDetailsScreen
import org.koitharu.kotatsu.ui.details.MangaDetailsViewModel
import org.koitharu.kotatsu.ui.explore.ExploreScreen
import org.koitharu.kotatsu.ui.explore.ExploreViewModel
import org.koitharu.kotatsu.ui.favorites.FavoritesScreen
import org.koitharu.kotatsu.ui.favorites.FavoritesViewModel
import org.koitharu.kotatsu.ui.history.HistoryScreen
import org.koitharu.kotatsu.ui.history.HistoryViewModel
import org.koitharu.kotatsu.ui.navigation.BookmarksRoute
import org.koitharu.kotatsu.ui.navigation.ExploreRoute
import org.koitharu.kotatsu.ui.navigation.FavoritesRoute
import org.koitharu.kotatsu.ui.navigation.HistoryRoute
import org.koitharu.kotatsu.ui.navigation.MangaDetailsRoute
import org.koitharu.kotatsu.ui.navigation.ReaderRoute
import org.koitharu.kotatsu.ui.navigation.SettingsRoute
import org.koitharu.kotatsu.ui.navigation.SourceBrowserRoute
import org.koitharu.kotatsu.ui.navigation.SourcesCatalogRoute
import org.koitharu.kotatsu.ui.navigation.StatisticsRoute
import org.koitharu.kotatsu.ui.reader.ReaderScreen
import org.koitharu.kotatsu.ui.reader.ReaderViewModel
import org.koitharu.kotatsu.ui.settings.SettingsScreen
import org.koitharu.kotatsu.ui.settings.SettingsViewModel
import org.koitharu.kotatsu.ui.sources.SourcesCatalogScreen
import org.koitharu.kotatsu.ui.sources.SourcesCatalogViewModel
import org.koitharu.kotatsu.ui.stats.StatisticsScreen
import org.koitharu.kotatsu.ui.stats.StatisticsViewModel

data class WidgetLaunchRequest(
    val targetTab: DefaultStartTab? = null,
    val mangaId: String? = null,
    val sourceTypeName: String? = null,
    val chapterId: String? = null,
    val pageIndex: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

private data class TopLevelNavTab<T : Any>(
    val route: T,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private fun resolveStartRoute(tab: DefaultStartTab): Any = when (tab) {
    DefaultStartTab.EXPLORE -> ExploreRoute()
    DefaultStartTab.FAVORITES -> FavoritesRoute
    DefaultStartTab.HISTORY -> HistoryRoute
    DefaultStartTab.BOOKMARKS -> BookmarksRoute
}

@Composable
fun KotatsuAppRoot(
    repository: MangaRepository,
    widthSizeClass: WindowWidthSizeClass,
    widgetLaunchRequest: WidgetLaunchRequest? = null,
    onConsumeWidgetLaunch: () -> Unit = {},
    onTriggerLockNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val scope = rememberCoroutineScope()

    val prefs by repository.preferencesRepository.preferencesFlow
        .collectAsStateWithLifecycle(initialValue = AppPreferences())

    val initialStartDestination = remember {
        resolveStartRoute(prefs.defaultStartTab)
    }

    val mobileTabs = listOf(
        TopLevelNavTab(
            route = FavoritesRoute,
            labelRes = R.string.nav_favorites,
            selectedIcon = Icons.Filled.Favorite,
            unselectedIcon = Icons.Outlined.FavoriteBorder,
            testTag = "nav_tab_favorites"
        ),
        TopLevelNavTab(
            route = HistoryRoute,
            labelRes = R.string.nav_history,
            selectedIcon = Icons.Filled.History,
            unselectedIcon = Icons.Outlined.History,
            testTag = "nav_tab_history"
        ),
        TopLevelNavTab(
            route = ExploreRoute(),
            labelRes = R.string.nav_explore,
            selectedIcon = Icons.Filled.Explore,
            unselectedIcon = Icons.Outlined.Explore,
            testTag = "nav_tab_explore"
        ),
        TopLevelNavTab(
            route = BookmarksRoute,
            labelRes = R.string.nav_bookmarks,
            selectedIcon = Icons.Filled.Bookmarks,
            unselectedIcon = Icons.Outlined.Bookmarks,
            testTag = "nav_tab_bookmarks"
        )
    )

    val railTabs = mobileTabs + TopLevelNavTab(
        route = SettingsRoute,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )

    val useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact

    val isMobileDockDestination = currentDestination?.let { dest ->
        dest.hasRoute<FavoritesRoute>() ||
            dest.hasRoute<HistoryRoute>() ||
            dest.hasRoute<ExploreRoute>() ||
            dest.hasRoute<BookmarksRoute>()
    } ?: true

    val isRailDestination = isMobileDockDestination ||
        (currentDestination?.hasRoute<SettingsRoute>() == true)

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(widgetLaunchRequest) {
        val req = widgetLaunchRequest ?: return@LaunchedEffect
        if (!req.mangaId.isNullOrBlank() &&
            !req.sourceTypeName.isNullOrBlank() &&
            !req.chapterId.isNullOrBlank()
        ) {
            navController.navigate(
                ReaderRoute(
                    mangaId = req.mangaId,
                    sourceTypeName = req.sourceTypeName,
                    chapterId = req.chapterId,
                    initialPage = req.pageIndex
                )
            ) {
                launchSingleTop = true
            }
        } else if (req.targetTab != null) {
            navigateToTab(resolveStartRoute(req.targetTab))
        }
        onConsumeWidgetLaunch()
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        if (useNavigationRail && isRailDestination) {
            val cookieShape = MaterialShapes.Cookie9Sided.toShape()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    header = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .size(48.dp)
                                .clip(cookieShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    railTabs.forEach { tab ->
                        val selected = currentDestination?.hasRoute(tab.route::class) == true
                        val showLabel = when (prefs.navLabelBehavior) {
                            NavLabelBehavior.ALWAYS -> true
                            NavLabelBehavior.SELECTED_ONLY -> selected
                            NavLabelBehavior.HIDDEN -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            alwaysShowLabel = prefs.navLabelBehavior == NavLabelBehavior.ALWAYS,
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = if (showLabel) {
                                { Text(stringResource(tab.labelRes), maxLines = 1) }
                            } else {
                                null
                            },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (prefs.incognitoMode) {
                        IncognitoStatusBanner(
                            onDisable = {
                                scope.launch {
                                    repository.preferencesRepository.setIncognitoMode(false)
                                }
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        KotatsuNavHost(
                            navController = navController,
                            startDestination = initialStartDestination,
                            repository = repository,
                            useNavigationRail = true,
                            bottomDockClearance = 24.dp,
                            onSwitchToExplore = { navigateToTab(ExploreRoute()) },
                            onOpenSettings = { navigateToTab(SettingsRoute) },
                            onTriggerLockNow = onTriggerLockNow
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                val applyTopInsets = isMobileDockDestination
                Column(
                    modifier = if (applyTopInsets) {
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                    } else {
                        Modifier.fillMaxSize()
                    }
                ) {
                    if (prefs.incognitoMode && isMobileDockDestination) {
                        IncognitoStatusBanner(
                            onDisable = {
                                scope.launch {
                                    repository.preferencesRepository.setIncognitoMode(false)
                                }
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        KotatsuNavHost(
                            navController = navController,
                            startDestination = initialStartDestination,
                            repository = repository,
                            useNavigationRail = false,
                            bottomDockClearance = if (isMobileDockDestination) 96.dp else 0.dp,
                            onSwitchToExplore = { navigateToTab(ExploreRoute()) },
                            onOpenSettings = { navController.navigate(SettingsRoute) },
                            onTriggerLockNow = onTriggerLockNow
                        )
                    }
                }

                // M3 Expressive Floating Pill Dock anchored above navigation bars on compact phones
                AnimatedVisibility(
                    visible = isMobileDockDestination,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            mobileTabs.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                val showTabText = when (prefs.navLabelBehavior) {
                                    NavLabelBehavior.SELECTED_ONLY -> selected
                                    NavLabelBehavior.ALWAYS -> true
                                    NavLabelBehavior.HIDDEN -> false
                                }
                                ToggleButton(
                                    checked = selected,
                                    onCheckedChange = { navigateToTab(tab.route) },
                                    shapes = ToggleButtonDefaults.shapes(),
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(
                                        horizontal = when {
                                            prefs.navLabelBehavior == NavLabelBehavior.ALWAYS -> 10.dp
                                            showTabText -> 14.dp
                                            else -> 12.dp
                                        },
                                        vertical = 10.dp
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
                                        .testTag(tab.testTag)
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (showTabText) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            style = if (prefs.navLabelBehavior == NavLabelBehavior.ALWAYS) {
                                                MaterialTheme.typography.labelMedium
                                            } else {
                                                MaterialTheme.typography.labelLarge
                                            },
                                            maxLines = 1,
                                            softWrap = false
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

@Composable
private fun IncognitoStatusBanner(onDisable: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .testTag("incognito_status_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.incognito_active_banner),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
            TextButton(onClick = onDisable) {
                Text(
                    text = stringResource(R.string.incognito_disable_action),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun KotatsuNavHost(
    navController: androidx.navigation.NavHostController,
    startDestination: Any,
    repository: MangaRepository,
    useNavigationRail: Boolean,
    bottomDockClearance: Dp,
    onSwitchToExplore: () -> Unit,
    onOpenSettings: () -> Unit,
    onTriggerLockNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openSettingsOrNull: (() -> Unit)? = if (useNavigationRail) null else onOpenSettings

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(210)) + scaleIn(initialScale = 0.965f, animationSpec = tween(210))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(150))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(190)) + scaleIn(initialScale = 0.97f, animationSpec = tween(190))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.965f, animationSpec = tween(150))
        },
        modifier = modifier
    ) {
        composable<ExploreRoute> { entry ->
            val args = entry.toRoute<ExploreRoute>()
            val vm: ExploreViewModel = viewModel(
                factory = ExploreViewModel.factory(repository, args.initialQuery, args.initialTagId)
            )
            LaunchedEffect(args.initialQuery, args.initialTagId) {
                if (args.initialQuery.isNotBlank() || args.initialTagId.isNotBlank()) {
                    vm.applyExternalFilter(args.initialQuery, args.initialTagId)
                }
            }
            ExploreScreen(
                viewModel = vm,
                bottomClearance = bottomDockClearance,
                onOpenSettings = openSettingsOrNull,
                onOpenSourcesCatalog = {
                    navController.navigate(SourcesCatalogRoute)
                },
                onOpenBrowser = { url, title ->
                    navController.navigate(SourceBrowserRoute(initialUrl = url, title = title))
                },
                onOpenManga = { manga ->
                    navController.navigate(
                        MangaDetailsRoute(
                            mangaId = manga.id,
                            sourceTypeName = manga.sourceType.name
                        )
                    )
                }
            )
        }

        composable<FavoritesRoute> {
            val vm: FavoritesViewModel = viewModel(
                factory = FavoritesViewModel.factory(repository)
            )
            FavoritesScreen(
                viewModel = vm,
                bottomClearance = bottomDockClearance,
                onOpenStatistics = { navController.navigate(StatisticsRoute) },
                onOpenSettings = openSettingsOrNull,
                onOpenManga = { manga ->
                    navController.navigate(
                        MangaDetailsRoute(
                            mangaId = manga.id,
                            sourceTypeName = manga.sourceType.name
                        )
                    )
                },
                onNavigateExplore = onSwitchToExplore
            )
        }

        composable<HistoryRoute> {
            val vm: HistoryViewModel = viewModel(
                factory = HistoryViewModel.factory(repository)
            )
            HistoryScreen(
                viewModel = vm,
                bottomClearance = bottomDockClearance,
                onOpenStatistics = { navController.navigate(StatisticsRoute) },
                onOpenSettings = openSettingsOrNull,
                onOpenMangaDetails = { manga ->
                    navController.navigate(
                        MangaDetailsRoute(
                            mangaId = manga.id,
                            sourceTypeName = manga.sourceType.name
                        )
                    )
                },
                onResumeChapter = { entry ->
                    navController.navigate(
                        ReaderRoute(
                            mangaId = entry.manga.id,
                            sourceTypeName = entry.manga.sourceType.name,
                            chapterId = entry.chapterId,
                            initialPage = entry.currentPage
                        )
                    )
                },
                onNavigateExplore = onSwitchToExplore
            )
        }

        composable<BookmarksRoute> {
            val vm: BookmarksViewModel = viewModel(
                factory = BookmarksViewModel.factory(repository)
            )
            BookmarksScreen(
                viewModel = vm,
                bottomClearance = bottomDockClearance,
                onNavigateExplore = onSwitchToExplore,
                onOpenSettings = openSettingsOrNull,
                onOpenBookmarkedPage = { bookmark ->
                    navController.navigate(
                        ReaderRoute(
                            mangaId = bookmark.manga.id,
                            sourceTypeName = bookmark.manga.sourceType.name,
                            chapterId = bookmark.chapterId,
                            initialPage = bookmark.pageIndex
                        )
                    )
                }
            )
        }

        composable<SettingsRoute> {
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(repository)
            )
            SettingsScreen(
                viewModel = vm,
                showBackButton = !useNavigationRail,
                onBack = { navController.popBackStack() },
                onOpenBrowser = { url, title ->
                    navController.navigate(SourceBrowserRoute(initialUrl = url, title = title))
                },
                onOpenSourcesCatalog = {
                    navController.navigate(SourcesCatalogRoute)
                },
                onOpenManga = { manga ->
                    navController.navigate(
                        MangaDetailsRoute(
                            mangaId = manga.id,
                            sourceTypeName = manga.sourceType.name
                        )
                    )
                },
                onTriggerLockNow = onTriggerLockNow,
                bottomClearance = 24.dp,
                modifier = Modifier.fillMaxSize()
            )
        }

        composable<SourcesCatalogRoute> {
            val vm: SourcesCatalogViewModel = viewModel(
                factory = SourcesCatalogViewModel.factory(repository)
            )
            SourcesCatalogScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenBrowser = { url, title ->
                    navController.navigate(SourceBrowserRoute(initialUrl = url, title = title))
                }
            )
        }

        composable<StatisticsRoute> {
            val vm: StatisticsViewModel = viewModel(
                factory = StatisticsViewModel.factory(repository)
            )
            StatisticsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenManga = { manga ->
                    navController.navigate(
                        MangaDetailsRoute(
                            mangaId = manga.id,
                            sourceTypeName = manga.sourceType.name
                        )
                    )
                },
                onNavigateExplore = {
                    navController.popBackStack()
                    onSwitchToExplore()
                }
            )
        }

        composable<SourceBrowserRoute> { entry ->
            val args = entry.toRoute<SourceBrowserRoute>()
            SourceBrowserScreen(
                initialUrl = args.initialUrl,
                initialTitle = args.title,
                repository = repository,
                onClose = { navController.popBackStack() },
                onOpenExtractedChapter = { mangaId, sourceTypeName, chapterId ->
                    navController.navigate(
                        ReaderRoute(
                            mangaId = mangaId,
                            sourceTypeName = sourceTypeName,
                            chapterId = chapterId,
                            initialPage = 0
                        )
                    )
                }
            )
        }

        composable<MangaDetailsRoute> { entry ->
            val args = entry.toRoute<MangaDetailsRoute>()
            val sourceType = runCatching { MangaSourceType.valueOf(args.sourceTypeName) }
                .getOrDefault(MangaSourceType.ONLINE_MANGADEX)
            val vm: MangaDetailsViewModel = viewModel(
                key = "details_${args.mangaId}",
                factory = MangaDetailsViewModel.factory(
                    mangaId = args.mangaId,
                    sourceType = sourceType,
                    repository = repository
                )
            )
            MangaDetailsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenChapter = { chapterId, initialPage ->
                    navController.navigate(
                        ReaderRoute(
                            mangaId = args.mangaId,
                            sourceTypeName = sourceType.name,
                            chapterId = chapterId,
                            initialPage = initialPage
                        )
                    )
                },
                onOpenInBrowser = { url, title ->
                    navController.navigate(SourceBrowserRoute(initialUrl = url, title = title))
                },
                onSearchTag = { tag ->
                    val isUuidTag = tag.id.length == 36 && tag.id.contains('-')
                    val targetRoute = if (isUuidTag) {
                        ExploreRoute(initialTagId = tag.id)
                    } else {
                        ExploreRoute(initialQuery = tag.name)
                    }
                    navController.navigate(targetRoute) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<ReaderRoute> { entry ->
            val args = entry.toRoute<ReaderRoute>()
            val sourceType = runCatching { MangaSourceType.valueOf(args.sourceTypeName) }
                .getOrDefault(MangaSourceType.ONLINE_MANGADEX)
            val vm: ReaderViewModel = viewModel(
                key = "reader_${args.mangaId}_${args.chapterId}",
                factory = ReaderViewModel.factory(
                    mangaId = args.mangaId,
                    sourceType = sourceType,
                    initialChapterId = args.chapterId,
                    initialPage = args.initialPage,
                    repository = repository
                )
            )
            ReaderScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
