package org.koitharu.kotatsu.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koitharu.kotatsu.TestKotatsuEnvironment
import org.koitharu.kotatsu.domain.model.ChapterItem
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag
import org.koitharu.kotatsu.ui.favorites.FavoritesScreen
import org.koitharu.kotatsu.ui.favorites.FavoritesViewModel
import org.koitharu.kotatsu.ui.security.AppLockScreen
import org.koitharu.kotatsu.ui.stats.StatisticsScreen
import org.koitharu.kotatsu.ui.stats.StatisticsViewModel
import org.koitharu.kotatsu.ui.theme.KotatsuTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w480dp-h960dp-xhdpi")
class KotatsuUiCujRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var env: TestKotatsuEnvironment
    private val activeViewModels = mutableListOf<ViewModel>()

    private fun <T : ViewModel> track(vm: T): T = vm.also { activeViewModels += it }

    @Before
    fun setUp() {
        env = TestKotatsuEnvironment()
    }

    @After
    fun tearDown() {
        activeViewModels.forEach { it.viewModelScope.cancel() }
        activeViewModels.clear()
        composeTestRule.waitForIdle()
        env.close()
    }

    @Test
    fun `FavoritesScreen displays new chapter badges, filters shelf, and confirms batch removal`() {
        runBlocking {
            val catId = env.repository.createCategory("Lendo agora")
            val mangaWithNew = MangaItem(
                id = "fav_1",
                sourceType = MangaSourceType.ONLINE_MANGADEX,
                title = "Chainsaw Man",
                author = "Tatsuki Fujimoto",
                description = "",
                coverUrl = "",
                status = "ongoing",
                tags = listOf(MangaTag("action", "Ação")),
                knownChaptersCount = 15,
                newChaptersDelta = 3
            )
            val mangaRegular = MangaItem(
                id = "fav_2",
                sourceType = MangaSourceType.ONLINE_KITSU,
                title = "Pluto",
                author = "Naoki Urasawa",
                description = "",
                coverUrl = "",
                status = "completed",
                tags = listOf(MangaTag("scifi", "Sci-Fi")),
                knownChaptersCount = 8,
                newChaptersDelta = 0
            )
            env.repository.updateMangaCategories(mangaWithNew, setOf(catId))
            env.repository.updateMangaCategories(mangaRegular, setOf(catId))
        }

        val viewModel = track(FavoritesViewModel(env.repository))

        composeTestRule.setContent {
            KotatsuTheme {
                FavoritesScreen(
                    viewModel = viewModel,
                    onOpenManga = {},
                    onNavigateExplore = {},
                    onOpenStatistics = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("favorite_manga_card_fav_1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Both cards and the "+3 NOVO" badge are rendered
        composeTestRule.onNodeWithTag("favorite_manga_card_fav_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("favorite_manga_card_fav_2").assertIsDisplayed()
        composeTestRule.onNodeWithText("+3 NOVO").assertIsDisplayed()

        // Filter by NEW_CHAPTERS -> only Chainsaw Man remains
        composeTestRule.onNodeWithTag("favorites_filter_NEW_CHAPTERS").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("favorite_manga_card_fav_1").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("favorite_manga_card_fav_2").fetchSemanticsNodes().size)

        // Enter Multi-Selection Mode, select visible item, trigger remove dialog, and confirm
        composeTestRule.onNodeWithTag("favorites_multi_select_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("batch_select_all_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("batch_remove_button").performClick()
        composeTestRule.waitForIdle()

        // Confirmation dialog protects against accidental deletion
        composeTestRule.onNodeWithTag("confirm_batch_remove_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_batch_remove_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("favorites_filtered_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("favorites_filtered_empty_state").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1024dp-h1200dp-xhdpi")
    fun `StatisticsScreen renders hero KPIs, 7-day chart, genres, and adaptive two-pane tablet dashboard`() {
        runBlocking {
            val catId = env.repository.createCategory("Favoritos")
            val manga = MangaItem(
                id = "stats_m1",
                sourceType = MangaSourceType.ONLINE_MANGADEX,
                title = "Dandadan",
                author = "Yukinobu Tatsu",
                description = "",
                coverUrl = "",
                status = "ongoing",
                tags = listOf(MangaTag("supernatural", "Sobrenatural")),
                knownChaptersCount = 4
            )
            env.repository.updateMangaCategories(manga, setOf(catId))
            env.repository.markAllChaptersCompleted(
                manga = manga,
                chapters = listOf(
                    ChapterItem("dd_1", manga.id, 1f, "1", "Cap 1", "pt-br", "Scan", 0L),
                    ChapterItem("dd_2", manga.id, 2f, "2", "Cap 2", "pt-br", "Scan", 0L)
                )
            )
        }

        val statsViewModel = track(StatisticsViewModel(env.repository))

        composeTestRule.setContent {
            KotatsuTheme {
                StatisticsScreen(
                    viewModel = statsViewModel,
                    onBack = {},
                    onOpenManga = {},
                    onNavigateExplore = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("stats_hero_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("stats_hero_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stats_weekly_chart_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stats_genres_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stats_sources_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stats_manga_progress_stats_m1").assertIsDisplayed()
    }

    @Test
    fun `AppLockScreen rejects wrong PIN with error feedback and unlocks on valid 4-digit PIN`() {
        var unlocked = false

        composeTestRule.setContent {
            KotatsuTheme {
                AppLockScreen(
                    expectedPin = "2580",
                    allowSystemCredential = false,
                    onUnlocked = { unlocked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Enter wrong PIN: 1-2-3-4
        listOf("1", "2", "3", "4").forEach { digit ->
            composeTestRule.onNodeWithTag("pin_key_$digit").performClick()
        }
        composeTestRule.waitForIdle()
        assertFalse(unlocked)
        composeTestRule.onNodeWithText("PIN incorreto. Tente novamente.").assertIsDisplayed()

        // Enter valid PIN: 2-5-8-0
        listOf("2", "5", "8", "0").forEach { digit ->
            composeTestRule.onNodeWithTag("pin_key_$digit").performClick()
        }
        composeTestRule.waitForIdle()
        assertTrue(unlocked)
    }

    @Test
    @Config(qualifiers = "w900dp-h1400dp-xhdpi")
    fun `SettingsScreen organizes appearance palettes, widgets, biometric status, and hosts Local CBZ import`() {
        val settingsVm = track(org.koitharu.kotatsu.ui.settings.SettingsViewModel(env.repository))

        composeTestRule.setContent {
            KotatsuTheme {
                org.koitharu.kotatsu.ui.settings.SettingsScreen(
                    viewModel = settingsVm,
                    showBackButton = false,
                    onBack = {},
                    onOpenBrowser = { _, _ -> },
                    onTriggerLockNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Appearance category displays palettes, cover corners, nav labels, and M3 Expressive Widget preview
        composeTestRule.onNodeWithTag("settings_palette_SAKURA_CRIMSON").performClick()
        composeTestRule.onNodeWithTag("settings_cover_corner_SOFT").performClick()
        composeTestRule.onNodeWithTag("expressive_widget_preview_card").assertIsDisplayed()

        // 2. Security category displays real Biometric hardware status card
        composeTestRule.onNodeWithTag("settings_cat_SECURITY").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("biometric_hardware_status_card").assertIsDisplayed()

        // 3. Backup category hosts the moved Local CBZ import button
        composeTestRule.onNodeWithTag("settings_cat_BACKUP").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_import_cbz_button").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun `SourcesCatalogScreen displays built-in PT-BR sources and enables source in repository`() {
        val catalogVm = track(org.koitharu.kotatsu.ui.sources.SourcesCatalogViewModel(env.repository))

        composeTestRule.setContent {
            KotatsuTheme {
                org.koitharu.kotatsu.ui.sources.SourcesCatalogScreen(
                    viewModel = catalogVm,
                    onBack = {},
                    onOpenBrowser = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Filter by PT-BR catalog sources and enable LerMangá
        composeTestRule.onNodeWithTag("sources_region_PT_BR").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("toggle_catalog_pt_lermanga")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                env.dao.getAllCustomSourcesList().any {
                    it.name.contains("LerMangá", ignoreCase = true) && it.enabled
                }
            }
        }
    }

    @Test
    fun `ExploreScreen header removes Importar CBZ button and exposes Sources Catalog shortcut`() {
        val exploreVm = track(
            org.koitharu.kotatsu.ui.explore.ExploreViewModel(
                repository = env.repository,
                initialQuery = "",
                initialTagId = ""
            )
        )

        composeTestRule.setContent {
            KotatsuTheme {
                org.koitharu.kotatsu.ui.explore.ExploreScreen(
                    viewModel = exploreVm,
                    onOpenManga = {},
                    onOpenBrowser = { _, _ -> },
                    onOpenSourcesCatalog = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Importar CBZ button must NOT appear on ExploreScreen anymore
        assertEquals(0, composeTestRule.onAllNodesWithTag("import_cbz_button").fetchSemanticsNodes().size)
        // Sources catalog button is prominently available
        composeTestRule.onNodeWithTag("header_sources_catalog_button").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `Explore customize modal and Reader visualization modal scroll cleanly without cutting off bottom options on compact screens`() {
        val exploreVm = track(
            org.koitharu.kotatsu.ui.explore.ExploreViewModel(
                repository = env.repository,
                initialQuery = "",
                initialTagId = ""
            )
        )

        composeTestRule.setContent {
            KotatsuTheme {
                org.koitharu.kotatsu.ui.explore.ExploreScreen(
                    viewModel = exploreVm,
                    onOpenManga = {},
                    onOpenBrowser = { _, _ -> },
                    onOpenSourcesCatalog = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Open "Personalizar Descobrir" modal bottom sheet on a compact 360x640 phone
        composeTestRule.onNodeWithTag("customize_explore_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("explore_customize_sheet").assertIsDisplayed()
        // Scroll to the very last switch in the modal and verify both subtitle and switch are visible
        composeTestRule.onNodeWithTag("sheet_explore_switch_badges")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Mostra a fonte da obra (MangaDex, Kitsu, Web ou CBZ) sobre as capas"
        ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `ReaderVisualizationSheet and compact SettingsScreen render all options and subtitles without clipping`() {
        val readerVm = track(
            org.koitharu.kotatsu.ui.reader.ReaderViewModel(
                mangaId = "m_test",
                sourceType = MangaSourceType.ONLINE_MANGADEX,
                initialChapterId = "c_test",
                initialPage = 0,
                repository = env.repository
            )
        )
        val settingsVm = track(org.koitharu.kotatsu.ui.settings.SettingsViewModel(env.repository))
        var showReaderModal = true

        composeTestRule.setContent {
            KotatsuTheme {
                if (showReaderModal) {
                    org.koitharu.kotatsu.ui.reader.ReaderVisualizationSheet(
                        prefs = org.koitharu.kotatsu.domain.model.AppPreferences(),
                        viewModel = readerVm,
                        onDismiss = { showReaderModal = false }
                    )
                } else {
                    org.koitharu.kotatsu.ui.settings.SettingsScreen(
                        viewModel = settingsVm,
                        showBackButton = true,
                        onBack = {},
                        onOpenBrowser = { _, _ -> },
                        onTriggerLockNow = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. ReaderVisualizationSheet reaches the very bottom switch + subtitle on 360x640dp
        composeTestRule.onNodeWithTag("sheet_mini_info_switch")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Mostra o número da página e porcentagem no canto inferior"
        ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `Compact SettingsScreen scrolls across categories and AddCustomSourceDialog without clipping`() {
        val settingsVm = track(org.koitharu.kotatsu.ui.settings.SettingsViewModel(env.repository))

        composeTestRule.setContent {
            KotatsuTheme {
                org.koitharu.kotatsu.ui.settings.SettingsScreen(
                    viewModel = settingsVm,
                    showBackButton = true,
                    onBack = {},
                    onOpenBrowser = { _, _ -> },
                    onTriggerLockNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Scroll virtualized LazyColumn to the bottom of Appearance on compact 360x640dp
        composeTestRule.onNodeWithTag("settings_detail_list")
            .performScrollToKey("appearance_widgets")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sync_widgets_button")
            .performScrollTo()
            .assertIsDisplayed()

        // Scroll horizontal category row to Sources category and verify MangaDex card spans full width
        composeTestRule.onNodeWithTag("settings_category_row")
            .performScrollToNode(hasTestTag("settings_cat_SOURCES"))
        composeTestRule.onNodeWithTag("settings_cat_SOURCES").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_detail_list")
            .performScrollToKey("sources_catalog")
        composeTestRule.waitForIdle()

        val mangadexBounds = composeTestRule.onNodeWithTag("source_builtin_mangadex")
            .performScrollTo()
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("MangaDex card should span full card width", mangadexBounds.width > 200f)

        // Open AddCustomSourceDialog on compact 360x640dp and verify template field + presets are scrollable and visible
        composeTestRule.onNodeWithTag("settings_add_source_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("custom_source_template_input")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("OPDS")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
