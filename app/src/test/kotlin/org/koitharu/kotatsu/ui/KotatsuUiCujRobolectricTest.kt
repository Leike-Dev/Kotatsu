package org.koitharu.kotatsu.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Before
    fun setUp() {
        env = TestKotatsuEnvironment()
    }

    @After
    fun tearDown() {
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

        val viewModel = FavoritesViewModel(env.repository)

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

        val statsViewModel = StatisticsViewModel(env.repository)

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
}
