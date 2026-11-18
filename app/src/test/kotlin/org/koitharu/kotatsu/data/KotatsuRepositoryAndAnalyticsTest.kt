package org.koitharu.kotatsu.data

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koitharu.kotatsu.TestKotatsuEnvironment
import org.koitharu.kotatsu.data.local.db.MangaEntity
import org.koitharu.kotatsu.domain.model.ChapterItem
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class KotatsuRepositoryAndAnalyticsTest {

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
    fun `startup initialization creates starter categories and manages batch shelf assignments`() = runBlocking {
        env.repository.runStartupInitialization()

        val initialCategories = env.repository.categoriesFlow.first()
        assertEquals(3, initialCategories.size)
        assertEquals(
            listOf("Lendo agora", "Planejo ler", "Favoritos"),
            initialCategories.map { it.name }
        )

        val actionCategoryId = env.repository.createCategory("Shonen Épico")
        assertTrue(actionCategoryId > 0)

        val mangaA = sampleManga(id = "md_1", title = "Berserk", knownChapters = 12)
        val mangaB = sampleManga(id = "md_2", title = "Vagabond", knownChapters = 8)

        val firstCatId = initialCategories.first().id
        env.repository.updateMangaCategories(mangaA, setOf(firstCatId))
        env.repository.updateMangaCategories(mangaB, setOf(firstCatId))

        assertEquals(2, env.repository.observeFavoritesByCategory(firstCatId).first().size)

        // Move both in batch to the newly created category
        env.repository.updateMangasCategoriesBatch(listOf(mangaA, mangaB), setOf(actionCategoryId))

        assertEquals(0, env.repository.observeFavoritesByCategory(firstCatId).first().size)
        assertEquals(2, env.repository.observeFavoritesByCategory(actionCategoryId).first().size)
    }

    @Test
    fun `chapter tracking preserves newChaptersDelta across catalog upserts and clears on inspection`() = runBlocking {
        val catId = env.repository.createCategory("Acompanhando")
        val trackedManga = sampleManga(
            id = "local_tracked_1",
            sourceType = MangaSourceType.LOCAL_CBZ,
            title = "Dungeon Meshi",
            knownChapters = 10,
            newDelta = 4
        )
        env.repository.updateMangaCategories(trackedManga, setOf(catId))

        // Simulate a generic metadata upsert that arrives with 0 chapter stats
        val metadataOnlyCopy = trackedManga.copy(
            description = "Sinopse atualizada",
            knownChaptersCount = 0,
            newChaptersDelta = 0
        )
        env.dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(metadataOnlyCopy))

        val preserved = env.dao.getMangaById(trackedManga.id)?.toDomain()
        assertNotNull(preserved)
        assertEquals("Sinopse atualizada", preserved!!.description)
        assertEquals(10, preserved.knownChaptersCount)
        assertEquals(4, preserved.newChaptersDelta)

        // Inspecting chapters (opening Details or Reader) clears newChaptersDelta while keeping knownChaptersCount
        val cbzFolder = File(env.tempDir, "cbz_inspect").apply {
            mkdirs()
            File(this, "01.jpg").writeBytes(byteArrayOf(1, 2, 3))
        }
        val inspectTarget = preserved.copy(localPath = cbzFolder.absolutePath)
        env.repository.getChapters(
            manga = inspectTarget,
            language = LanguageFilter.ALL,
            forceRefresh = true,
            recordInspection = true
        )

        val afterInspection = env.dao.getMangaById(trackedManga.id)?.toDomain()
        assertNotNull(afterInspection)
        assertEquals(10, afterInspection!!.knownChaptersCount)
        assertEquals(0, afterInspection.newChaptersDelta)
    }

    @Test
    fun `reading progress marks final page completed, respects incognito mode, and cleans up deleted local CBZ`() = runBlocking {
        val localDir = File(env.tempDir, "local_comic_cleanup").apply {
            mkdirs()
            File(this, "page_1.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        }
        val localManga = sampleManga(
            id = "local_cleanup_1",
            sourceType = MangaSourceType.LOCAL_CBZ,
            title = "Akira Edição Local",
            knownChapters = 1
        ).copy(localPath = localDir.absolutePath)

        // Intermediate page saves history without completing chapter
        env.repository.saveReadingProgress(
            manga = localManga,
            chapterId = "ch_1",
            chapterTitle = "Cap. 1",
            currentPage = 4,
            totalPages = 15
        )
        assertEquals(4, env.repository.observeHistoryForManga(localManga.id).first()?.currentPage)
        assertFalse("ch_1" in env.repository.observeCompletedChapters(localManga.id).first())

        // Final page marks chapter completed
        env.repository.saveReadingProgress(
            manga = localManga,
            chapterId = "ch_1",
            chapterTitle = "Cap. 1",
            currentPage = 14,
            totalPages = 15
        )
        assertTrue("ch_1" in env.repository.observeCompletedChapters(localManga.id).first())

        // Add a page bookmark
        env.repository.togglePageBookmark(
            manga = localManga,
            chapterId = "ch_1",
            chapterTitle = "Cap. 1",
            pageIndex = 7,
            totalPages = 15,
            previewUrl = "file:///page_8.jpg",
            currentlyBookmarked = false
        )
        assertEquals(1, env.repository.allBookmarksFlow.first().size)

        // Enable Incognito Mode: progress updates must be ignored
        env.preferencesRepository.setIncognitoMode(true)
        val incognitoManga = sampleManga(id = "md_incognito", title = "Obra Incógnita")
        env.repository.saveReadingProgress(
            manga = incognitoManga,
            chapterId = "ch_secret",
            chapterTitle = "Cap. Secreto",
            currentPage = 3,
            totalPages = 20
        )
        assertNull(env.repository.observeHistoryForManga(incognitoManga.id).first())
        env.preferencesRepository.setIncognitoMode(false)

        // Deleting local CBZ removes files, history, completed chapters, and bookmarks cleanly
        env.repository.deleteLocalManga(localManga)
        assertFalse(localDir.exists())
        assertNull(env.repository.observeHistoryForManga(localManga.id).first())
        assertTrue(env.repository.observeCompletedChapters(localManga.id).first().isEmpty())
        assertTrue(env.repository.allBookmarksFlow.first().isEmpty())
    }

    @Test
    fun `reading analytics aggregates KPIs, shelf completion, 7-day activity, genres, and sources`() = runBlocking {
        val catId = env.repository.createCategory("Favoritos")

        val caughtUpManga = sampleManga(
            id = "md_frieren",
            sourceType = MangaSourceType.ONLINE_MANGADEX,
            title = "Sousou no Frieren",
            knownChapters = 2,
            tags = listOf(MangaTag("fantasy", "Fantasia"), MangaTag("adventure", "Aventura"))
        )
        val inProgressManga = sampleManga(
            id = "kitsu_op",
            sourceType = MangaSourceType.ONLINE_KITSU,
            title = "One Piece",
            knownChapters = 10,
            tags = listOf(MangaTag("adventure", "Aventura"), MangaTag("action", "Ação"))
        )

        env.repository.updateMangaCategories(caughtUpManga, setOf(catId))
        env.repository.updateMangaCategories(inProgressManga, setOf(catId))

        // Mark all 2 chapters of Frieren completed
        env.repository.markAllChaptersCompleted(
            manga = caughtUpManga,
            chapters = listOf(
                ChapterItem("fr_1", caughtUpManga.id, 1f, "1", "Jornada", "pt-br", "Scan", 0L),
                ChapterItem("fr_2", caughtUpManga.id, 2f, "2", "Magia", "pt-br", "Scan", 0L)
            )
        )

        // Read 1 chapter of One Piece + active progress on chapter 2
        env.repository.toggleChapterCompleted(inProgressManga, "op_1", completed = true)
        env.repository.saveReadingProgress(
            manga = inProgressManga,
            chapterId = "op_2",
            chapterTitle = "Cap. 2",
            currentPage = 9,
            totalPages = 20
        )

        val analytics = env.repository.readingAnalyticsFlow.first()
        assertTrue(analytics.hasAnyActivity)
        assertEquals(2, analytics.favoritesCount)
        assertEquals(3, analytics.completedChaptersCount)
        assertEquals(1, analytics.caughtUpWorksCount)
        assertEquals(1, analytics.inProgressWorksCount)
        // Active history pages = (9 + 1) = 10; 3 extra completed chapters not matching active history chapter * 22 = 66 => 76
        assertEquals(76, analytics.estimatedPagesRead)
        // Shelf chapters: read (2 + 1) = 3 out of total (2 + 10) = 12 => 0.25f
        assertEquals(0.25f, analytics.shelfCompletionFraction, 0.001f)
        assertEquals(7, analytics.dailyActivity.size)
        assertTrue(analytics.dailyActivity.last().isToday)
        assertTrue(analytics.dailyActivity.last().count >= 4)

        // "Aventura" appears in both works so it must be top genre
        assertEquals("Aventura", analytics.topGenres.first().genreName)
        assertEquals(2, analytics.topGenres.first().mangaCount)
        assertEquals(2, analytics.sourceBreakdown.size)
        assertEquals(2, analytics.mangaProgressList.size)
    }

    private fun sampleManga(
        id: String,
        sourceType: MangaSourceType = MangaSourceType.ONLINE_MANGADEX,
        title: String,
        knownChapters: Int = 0,
        newDelta: Int = 0,
        tags: List<MangaTag> = listOf(MangaTag("action", "Ação"))
    ) = MangaItem(
        id = id,
        sourceType = sourceType,
        title = title,
        author = "Autor Teste",
        description = "Descrição de $title",
        coverUrl = "https://example.org/covers/$id.jpg",
        status = "ongoing",
        tags = tags,
        knownChaptersCount = knownChapters,
        newChaptersDelta = newDelta
    )
}
