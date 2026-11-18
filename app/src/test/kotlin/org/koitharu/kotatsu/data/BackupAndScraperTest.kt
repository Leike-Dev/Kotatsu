package org.koitharu.kotatsu.data

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koitharu.kotatsu.TestKotatsuEnvironment
import org.koitharu.kotatsu.data.remote.WebProtectionEngine
import org.koitharu.kotatsu.domain.model.AppThemeMode
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag
import org.koitharu.kotatsu.domain.model.ReaderMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackupAndScraperTest {

    private lateinit var sourceEnv: TestKotatsuEnvironment
    private lateinit var targetEnv: TestKotatsuEnvironment

    @Before
    fun setUp() {
        sourceEnv = TestKotatsuEnvironment()
        targetEnv = TestKotatsuEnvironment()
    }

    @After
    fun tearDown() {
        sourceEnv.close()
        targetEnv.close()
    }

    @Test
    fun `JSON v2 backup export and restore preserves shelf, chapter deltas, history, bookmarks, sources, and preferences`() = runBlocking {
        val catId = sourceEnv.repository.createCategory("Clássicos")
        val manga = MangaItem(
            id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            sourceType = MangaSourceType.ONLINE_MANGADEX,
            title = "Monster",
            author = "Naoki Urasawa",
            description = "Suspense médico",
            coverUrl = "https://mangadex.org/covers/monster.jpg",
            status = "completed",
            tags = listOf(MangaTag("thriller", "Suspense")),
            knownChaptersCount = 162,
            newChaptersDelta = 2
        )

        sourceEnv.repository.updateMangaCategories(manga, setOf(catId))
        sourceEnv.repository.saveReadingProgress(manga, "ch_40", "Cap. 40", 12, 24)
        sourceEnv.repository.toggleChapterCompleted(manga, "ch_39", completed = true)
        sourceEnv.repository.togglePageBookmark(
            manga = manga,
            chapterId = "ch_40",
            chapterTitle = "Cap. 40",
            pageIndex = 12,
            totalPages = 24,
            previewUrl = "https://mangadex.org/page13.jpg",
            currentlyBookmarked = false
        )
        sourceEnv.repository.addCustomSource(
            name = "Scan Teste",
            baseUrl = "https://scanteste.example.org:8443",
            searchTemplate = "https://scanteste.example.org:8443/?s={query}"
        )
        sourceEnv.preferencesRepository.setAmoledDark(true)
        sourceEnv.preferencesRepository.setDefaultReaderMode(ReaderMode.WEBTOON)

        val backupFile = File(sourceEnv.tempDir, "kotatsu_v2_backup.json")
        val exportedCount = sourceEnv.repository.exportBackup(Uri.fromFile(backupFile))
        assertEquals(3, exportedCount) // 1 manga + 1 bookmark + 1 custom source

        // Restore into clean target environment
        val restoredCount = targetEnv.repository.restoreBackup(Uri.fromFile(backupFile))
        assertEquals(3, restoredCount)

        val restoredFavs = targetEnv.repository.allFavoritesFlow.first()
        assertEquals(1, restoredFavs.size)
        assertEquals("Monster", restoredFavs.first().title)
        assertEquals(162, restoredFavs.first().knownChaptersCount)
        assertEquals(2, restoredFavs.first().newChaptersDelta)

        assertEquals(12, targetEnv.repository.observeHistoryForManga(manga.id).first()?.currentPage)
        assertTrue("ch_39" in targetEnv.repository.observeCompletedChapters(manga.id).first())
        assertEquals(1, targetEnv.repository.allBookmarksFlow.first().size)

        val restoredSources = targetEnv.repository.customSourcesFlow.first()
        assertEquals(1, restoredSources.size)
        assertEquals("https://scanteste.example.org:8443", restoredSources.first().baseUrl)

        val restoredPrefs = targetEnv.preferencesRepository.preferencesFlow.first()
        assertTrue(restoredPrefs.amoledDark)
        assertEquals(ReaderMode.WEBTOON, restoredPrefs.defaultReaderMode)
    }

    @Test
    fun `legacy official Kotatsu bak ZIP restore imports categories, favourites, history, bookmarks, settings, and discovers custom sources`() = runBlocking {
        val legacyBakFile = File(sourceEnv.tempDir, "kotatsu_legacy.bak")
        val mdUuid = "f9c33607-9180-4ba6-b85c-e4b5faee7192"

        ZipOutputStream(FileOutputStream(legacyBakFile)).use { zos ->
            zos.putJsonEntry(
                "categories",
                """[{"id": 7, "title": "Favoritos Antigos", "sort_key": 0, "created_at": 1700000000000}]"""
            )
            zos.putJsonEntry(
                "favourites",
                """
                [
                  {
                    "manga_id": 101,
                    "category_id": 7,
                    "created_at": 1700000001000,
                    "manga": {
                      "id": 101,
                      "title": "Vinland Saga",
                      "author": "Makoto Yukimura",
                      "public_url": "https://mangadex.org/title/$mdUuid/vinland-saga",
                      "cover_url": "https://mangadex.org/covers/vinland.jpg",
                      "state": "ONGOING",
                      "source": "MANGADEX"
                    }
                  },
                  {
                    "manga_id": 202,
                    "category_id": 7,
                    "created_at": 1700000002000,
                    "manga": {
                      "id": 202,
                      "title": "Solo Leveling Web",
                      "public_url": "https://leitorweb.example.com/manga/solo-leveling",
                      "cover_url": "https://leitorweb.example.com/cover.jpg",
                      "state": "FINISHED",
                      "source": "LEITORWEB"
                    }
                  }
                ]
                """.trimIndent()
            )
            zos.putJsonEntry(
                "history",
                """[{"manga_id": 101, "chapter_id": "ch_legacy_5", "page": 9, "percent": 0.5, "updated_at": 1700000005000}]"""
            )
            zos.putJsonEntry(
                "bookmarks",
                """[{"manga_id": 202, "bookmarks": [{"manga_id": 202, "chapter_id": "ch_web_1", "page": 3, "image_url": "https://leitorweb.example.com/p4.jpg"}]}]"""
            )
            zos.putJsonEntry(
                "settings",
                """{"theme": "LIGHT", "amoled_theme": false, "reading_mode": "RTL"}"""
            )
        }

        val imported = targetEnv.repository.restoreBackup(Uri.fromFile(legacyBakFile))
        assertTrue(imported >= 3)

        val favs = targetEnv.repository.allFavoritesFlow.first()
        assertEquals(2, favs.size)
        val mdManga = favs.firstOrNull { it.id == mdUuid }
        assertNotNull(mdManga)
        assertEquals(MangaSourceType.ONLINE_MANGADEX, mdManga!!.sourceType)

        // Verify auto-discovered custom web source from legacy backup
        val discoveredSources = targetEnv.repository.customSourcesFlow.first()
        assertEquals(1, discoveredSources.size)
        assertEquals("https://leitorweb.example.com", discoveredSources.first().baseUrl)

        // History page 9 at 50% => totalPages estimated to 20
        val restoredHist = targetEnv.repository.observeHistoryForManga(mdUuid).first()
        assertNotNull(restoredHist)
        assertEquals(9, restoredHist!!.currentPage)
        assertEquals(20, restoredHist.totalPages)

        // Preferences migrated from legacy settings
        val prefs = targetEnv.preferencesRepository.preferencesFlow.first()
        assertEquals(AppThemeMode.LIGHT, prefs.themeMode)
        assertEquals(ReaderMode.MANGA_RTL, prefs.defaultReaderMode)
    }

    @Test
    fun `local CBZ import extracts natural-sorted chapters and pages with accurate knownChaptersCount`() = runBlocking {
        val cbzFile = File(sourceEnv.tempDir, "Quadrinho_Especial.cbz")
        ZipOutputStream(FileOutputStream(cbzFile)).use { zos ->
            listOf("Cap_1/p10.jpg", "Cap_1/p2.jpg", "Cap_2/p1.png").forEach { entryName ->
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
                zos.closeEntry()
            }
        }

        val imported = sourceEnv.repository.importLocalCbz(Uri.fromFile(cbzFile))
        assertEquals("Quadrinho Especial", imported.title)
        assertEquals(2, imported.knownChaptersCount)

        val chapters = sourceEnv.repository.getChapters(imported, LanguageFilter.ALL)
        assertEquals(2, chapters.size)
        assertEquals("Cap 1", chapters[0].title)
        assertEquals("Cap 2", chapters[1].title)

        val ch1Pages = sourceEnv.repository.getChapterPages(imported, chapters[0].id)
        assertEquals(2, ch1Pages.size)
        // Natural sort must order p2.jpg BEFORE p10.jpg
        assertTrue(ch1Pages[0].imageUrl.endsWith("p2.jpg"))
        assertTrue(ch1Pages[1].imageUrl.endsWith("p10.jpg"))
    }

    @Test
    fun `CustomSiteParser and WebProtectionEngine filter ad networks and extract clean reader pages`() {
        val domHtml = """
            <html><body>
              <div class="reading-content">
                <img data-src="https://cdn.mangasite.org/ch10/001.webp" />
                <img src="https://cdn.mangasite.org/ch10/002.webp" />
                <img src="https://googlesyndication.com/pagead/ad_banner.jpg" />
                <img src="https://cdn.mangasite.org/assets/site-logo.png" />
              </div>
            </body></html>
        """.trimIndent()

        val domPages = sourceEnv.customSiteParser.extractPagesFromHtml("https://mangasite.org/ler/10", domHtml)
        assertEquals(2, domPages.size)
        assertEquals("https://cdn.mangasite.org/ch10/001.webp", domPages[0].imageUrl)
        assertEquals("https://cdn.mangasite.org/ch10/002.webp", domPages[1].imageUrl)

        val scriptFallbackHtml = """
            <html><body>
              <script>
                ts_reader.run({"sources":[{"images":[
                  "https:\/\/img.scan.org\/cap1\/p1.jpg",
                  "https:\/\/popads.net\/ad_interstitial.jpg",
                  "https:\/\/img.scan.org\/cap1\/p2.jpg"
                ]}]});
              </script>
            </body></html>
        """.trimIndent()

        val scriptPages = sourceEnv.customSiteParser.extractPagesFromHtml("https://scan.org/cap-1", scriptFallbackHtml)
        assertEquals(2, scriptPages.size)
        assertEquals("https://img.scan.org/cap1/p1.jpg", scriptPages[0].imageUrl)
        assertEquals("https://img.scan.org/cap1/p2.jpg", scriptPages[1].imageUrl)

        assertTrue(WebProtectionEngine.isAdOrAntiAdblockResource("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(WebProtectionEngine.isAdOrAntiAdblockResource("https://site.org/js/fuckadblock.min.js"))
        assertFalse(WebProtectionEngine.isAdOrAntiAdblockResource("https://uploads.mangadex.org/data/page1.png"))

        val stealthJs = WebProtectionEngine.buildStealthDomScript(antiAdblockBypass = true, blockPopups = true)
        assertTrue(stealthJs.contains("window.open = function()"))
        assertTrue(stealthJs.contains("window.FuckAdBlock"))
    }

    private fun ZipOutputStream.putJsonEntry(name: String, jsonBody: String) {
        putNextEntry(ZipEntry(name))
        write(jsonBody.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
