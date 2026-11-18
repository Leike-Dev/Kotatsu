package org.koitharu.kotatsu.data.repository

import android.content.Context
import android.net.Uri
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.data.local.backup.BackupManager
import org.koitharu.kotatsu.data.local.cbz.LocalCbzRepository
import org.koitharu.kotatsu.data.local.db.CompletedChapterEntity
import org.koitharu.kotatsu.data.local.db.CustomSourceEntity
import org.koitharu.kotatsu.data.local.db.FavoriteCategoryEntity
import org.koitharu.kotatsu.data.local.db.KotatsuDao
import org.koitharu.kotatsu.data.local.db.MangaEntity
import org.koitharu.kotatsu.data.local.db.PageBookmarkEntity
import org.koitharu.kotatsu.data.local.db.ReadingHistoryEntity
import org.koitharu.kotatsu.data.local.prefs.UserPreferencesRepository
import org.koitharu.kotatsu.data.remote.CustomSiteParser
import org.koitharu.kotatsu.data.remote.KitsuApi
import org.koitharu.kotatsu.data.remote.MangaDexApi
import org.koitharu.kotatsu.domain.model.CatalogSort
import org.koitharu.kotatsu.domain.model.ChapterItem
import org.koitharu.kotatsu.domain.model.CustomMangaSource
import org.koitharu.kotatsu.domain.model.FavoriteCategory
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.LibraryStats
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag
import org.koitharu.kotatsu.domain.model.PageBookmarkItem
import org.koitharu.kotatsu.domain.model.PageItem
import org.koitharu.kotatsu.domain.model.ReadingHistoryItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MangaRepository(
    private val appContext: Context,
    private val dao: KotatsuDao,
    private val api: MangaDexApi,
    private val kitsuApi: KitsuApi,
    private val customSiteParser: CustomSiteParser,
    private val localCbzRepository: LocalCbzRepository,
    private val backupManager: BackupManager,
    val preferencesRepository: UserPreferencesRepository
) {
    private val memoryMangaCache = ConcurrentHashMap<String, MangaItem>()
    private val memoryChaptersCache = ConcurrentHashMap<String, List<ChapterItem>>()
    private val chapterUrlCache = ConcurrentHashMap<String, String>()
    private val adHocPagesCache = ConcurrentHashMap<String, List<PageItem>>()

    val localMangasFlow: Flow<List<MangaItem>> = dao.observeLocalMangas().map { list ->
        list.map { it.toDomain().also { item -> memoryMangaCache[item.id] = item } }
    }

    val customSourcesFlow: Flow<List<CustomMangaSource>> = dao.observeCustomSources().map { list ->
        list.map { it.toDomain() }
    }

    val categoriesFlow: Flow<List<FavoriteCategory>> = dao.observeCategoriesWithCounts().map { rows ->
        rows.map {
            FavoriteCategory(
                id = it.id,
                name = it.name,
                sortOrder = it.sortOrder,
                mangaCount = it.mangaCount
            )
        }
    }

    val allFavoritesFlow: Flow<List<MangaItem>> = dao.observeAllFavoriteMangas().map { list ->
        list.map { it.toDomain().also { item -> memoryMangaCache[item.id] = item } }
    }

    val readingHistoryFlow: Flow<List<ReadingHistoryItem>> = dao.observeReadingHistory().map { rows ->
        rows.mapNotNull { row ->
            val manga = row.manga?.toDomain() ?: return@mapNotNull null
            memoryMangaCache[manga.id] = manga
            ReadingHistoryItem(
                manga = manga,
                chapterId = row.history.chapterId,
                chapterTitle = row.history.chapterTitle,
                currentPage = row.history.currentPage,
                totalPages = row.history.totalPages,
                updatedAt = row.history.updatedAt
            )
        }
    }

    val allBookmarksFlow: Flow<List<PageBookmarkItem>> = dao.observeAllBookmarks().map { rows ->
        rows.mapNotNull { row ->
            val manga = row.manga?.toDomain() ?: return@mapNotNull null
            memoryMangaCache[manga.id] = manga
            PageBookmarkItem(
                id = row.bookmark.id,
                manga = manga,
                chapterId = row.bookmark.chapterId,
                chapterTitle = row.bookmark.chapterTitle,
                pageIndex = row.bookmark.pageIndex,
                totalPages = row.bookmark.totalPages,
                previewUrl = row.bookmark.previewUrl,
                createdAt = row.bookmark.createdAt
            )
        }
    }

    val libraryStatsFlow: Flow<LibraryStats> = combine(
        allFavoritesFlow,
        readingHistoryFlow,
        allBookmarksFlow,
        localMangasFlow,
        customSourcesFlow
    ) { favs, hist, bms, locals, sources ->
        LibraryStats(
            favoritesCount = favs.size,
            historyCount = hist.size,
            bookmarksCount = bms.size,
            localCbzCount = locals.size,
            customSourcesCount = sources.size
        )
    }

    suspend fun runStartupInitialization() {
        // 1. Migrate legacy `kotatsu-db` SQLite if upgrading from older Kotatsu
        runCatching { backupManager.migrateLegacyKotatsuSqliteIfNeeded() }

        // 2. Ensure starter categories exist
        if (dao.getCategoryCount() == 0) {
            listOf("Lendo agora", "Planejo ler", "Favoritos").forEachIndexed { idx, title ->
                dao.insertCategory(FavoriteCategoryEntity(name = title, sortOrder = idx))
            }
        }

        // 3. Recover any saved CBZ/ZIP comic folders on disk from previous installations
        runCatching {
            val knownPaths = dao.getAllMangasList()
                .mapNotNull { it.localPath }
                .toSet()
            val recovered = localCbzRepository.recoverExistingLocalLibrary(knownPaths)
            if (recovered.isNotEmpty()) {
                dao.upsertMangas(recovered.map(MangaEntity::fromDomain))
            }
        }
    }

    fun observeFavoritesByCategory(categoryId: Long?): Flow<List<MangaItem>> {
        return if (categoryId == null) {
            allFavoritesFlow
        } else {
            dao.observeMangasByCategory(categoryId).map { list ->
                list.map { it.toDomain().also { item -> memoryMangaCache[item.id] = item } }
            }
        }
    }

    fun observeCategoryIdsForManga(mangaId: String): Flow<Set<Long>> =
        dao.observeCategoryIdsForManga(mangaId).map { it.toSet() }

    fun observeHistoryForManga(mangaId: String): Flow<ReadingHistoryEntity?> =
        dao.observeHistoryForManga(mangaId)

    fun observeCompletedChapters(mangaId: String): Flow<Set<String>> =
        dao.observeCompletedChapterIds(mangaId).map { it.toSet() }

    fun observeBookmarkedPages(mangaId: String, chapterId: String): Flow<Set<Int>> =
        dao.observeBookmarkedPagesForChapter(mangaId, chapterId).map { it.toSet() }

    suspend fun searchOnlineCatalog(
        query: String,
        language: LanguageFilter,
        sort: CatalogSort,
        tagId: String?,
        sourceFilterKey: String = SOURCE_FILTER_ALL,
        offset: Int = 0
    ): List<MangaItem> = coroutineScope {
        val prefs = preferencesRepository.preferencesFlow.first()

        val results: List<MangaItem> = when {
            sourceFilterKey == SOURCE_FILTER_MANGADEX -> {
                api.searchManga(
                    query = query,
                    language = language,
                    sort = sort,
                    tagId = tagId,
                    hideNsfw = prefs.hideNsfw,
                    offset = offset
                )
            }

            sourceFilterKey == SOURCE_FILTER_KITSU -> {
                kitsuApi.searchManga(query = query, sort = sort, offset = offset)
            }

            sourceFilterKey.startsWith(SOURCE_FILTER_CUSTOM_PREFIX) -> {
                if (offset > 0) emptyList()
                else {
                    val customId = sourceFilterKey.removePrefix(SOURCE_FILTER_CUSTOM_PREFIX).toLongOrNull()
                    val target = dao.getAllCustomSourcesList().firstOrNull { it.id == customId }?.toDomain()
                    if (target != null) customSiteParser.searchCustomSource(target, query) else emptyList()
                }
            }

            else -> {
                val mdDeferred = async {
                    runCatching {
                        api.searchManga(
                            query = query,
                            language = language,
                            sort = sort,
                            tagId = tagId,
                            hideNsfw = prefs.hideNsfw,
                            offset = offset
                        )
                    }.getOrDefault(emptyList())
                }

                val kitsuDeferred = async {
                    if (prefs.enableKitsuCatalog && offset == 0 && tagId == null && query.isNotBlank()) {
                        runCatching { kitsuApi.searchManga(query = query, sort = sort, limit = 8) }
                            .getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }

                val customDeferred = async {
                    if (offset == 0 && tagId == null) {
                        val activeCustom = dao.getEnabledCustomSources().map { it.toDomain() }
                        activeCustom.map { src ->
                            async {
                                runCatching { customSiteParser.searchCustomSource(src, query) }
                                    .getOrDefault(emptyList())
                                    .take(10)
                            }
                        }.awaitAll().flatten()
                    } else {
                        emptyList()
                    }
                }

                val mdItems = mdDeferred.await()
                val customItems = customDeferred.await()
                val kitsuItems = kitsuDeferred.await()

                if (query.isNotBlank()) {
                    (customItems + mdItems + kitsuItems).distinctBy { it.id }
                } else {
                    (mdItems + customItems).distinctBy { it.id }
                }
            }
        }

        results.forEach { item ->
            memoryMangaCache[item.id] = item
            if (item.sourceType == MangaSourceType.CUSTOM_WEB || item.sourceType == MangaSourceType.ONLINE_KITSU) {
                runCatching { dao.upsertManga(MangaEntity.fromDomain(item)) }
            }
        }
        results
    }

    suspend fun getPopularTags(): List<MangaTag> = api.getPopularTags()

    // Custom Sources CRUD
    suspend fun addCustomSource(
        name: String,
        baseUrl: String,
        searchTemplate: String
    ): Long {
        val cleanName = name.trim()
        val normalizedUrl = baseUrl.trim().let {
            if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
        }.trimEnd('/')
        if (cleanName.isBlank() || normalizedUrl.isBlank()) return -1L
        return dao.insertCustomSource(
            CustomSourceEntity(
                name = cleanName,
                baseUrl = normalizedUrl,
                searchUrlTemplate = searchTemplate.trim(),
                enabled = true
            )
        )
    }

    suspend fun updateCustomSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        searchTemplate: String
    ) {
        val cleanName = name.trim()
        val normalizedUrl = baseUrl.trim().let {
            if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
        }.trimEnd('/')
        if (cleanName.isBlank() || normalizedUrl.isBlank()) return
        dao.updateCustomSource(
            sourceId = sourceId,
            name = cleanName,
            baseUrl = normalizedUrl,
            searchTemplate = searchTemplate.trim()
        )
    }

    suspend fun setCustomSourceEnabled(sourceId: Long, enabled: Boolean) {
        dao.setCustomSourceEnabled(sourceId, enabled)
    }

    suspend fun deleteCustomSource(sourceId: Long) {
        dao.deleteCustomSource(sourceId)
    }

    suspend fun importLocalCbz(uri: Uri): MangaItem {
        val imported = localCbzRepository.importCbzUri(uri)
        memoryMangaCache[imported.id] = imported
        dao.upsertManga(MangaEntity.fromDomain(imported))
        return imported
    }

    suspend fun deleteLocalManga(manga: MangaItem) {
        localCbzRepository.deleteLocalFiles(manga.localPath)
        dao.clearCategoriesForManga(manga.id)
        dao.deleteHistoryForManga(manga.id)
        dao.deleteMangaById(manga.id)
        memoryMangaCache.remove(manga.id)
        memoryChaptersCache.remove(manga.id)
    }

    suspend fun getMangaDetails(mangaId: String, sourceType: MangaSourceType): MangaItem {
        val cached = memoryMangaCache[mangaId] ?: dao.getMangaById(mangaId)?.toDomain()

        return when (sourceType) {
            MangaSourceType.ONLINE_MANGADEX -> {
                if (cached != null && cached.description.isNotBlank()) {
                    memoryMangaCache[mangaId] = cached
                    cached
                } else {
                    val remote = api.getMangaById(mangaId)
                    memoryMangaCache[mangaId] = remote
                    remote
                }
            }

            MangaSourceType.ONLINE_KITSU -> {
                val remote = cached ?: kitsuApi.getMangaById(mangaId)
                    ?: throw IllegalStateException("Obra do catálogo Kitsu não encontrada.")
                memoryMangaCache[mangaId] = remote
                dao.upsertManga(MangaEntity.fromDomain(remote))
                remote
            }

            MangaSourceType.CUSTOM_WEB -> {
                val base = cached ?: throw IllegalStateException("Obra da fonte externa não encontrada.")
                if (base.sourceUrl != null) {
                    val (enriched, chapters) = runCatching {
                        customSiteParser.fetchMangaDetailsAndChapters(base)
                    }.getOrDefault(base to emptyList())
                    memoryMangaCache[mangaId] = enriched
                    dao.upsertManga(MangaEntity.fromDomain(enriched))
                    if (chapters.isNotEmpty()) {
                        chapters.forEach { ch ->
                            ch.chapterUrl?.let { url -> chapterUrlCache[ch.id] = url }
                        }
                        memoryChaptersCache["${mangaId}_ALL"] = chapters
                    }
                    enriched
                } else {
                    base
                }
            }

            MangaSourceType.LOCAL_CBZ -> {
                cached ?: throw IllegalStateException("Arquivo local CBZ não encontrado.")
            }
        }
    }

    suspend fun getChapters(
        manga: MangaItem,
        language: LanguageFilter,
        forceRefresh: Boolean = false
    ): List<ChapterItem> {
        val cacheKey = if (manga.sourceType == MangaSourceType.CUSTOM_WEB) {
            "${manga.id}_ALL"
        } else {
            "${manga.id}_${language.name}"
        }
        if (!forceRefresh) {
            memoryChaptersCache[cacheKey]?.let { return it }
        }
        val chapters = when (manga.sourceType) {
            MangaSourceType.LOCAL_CBZ -> localCbzRepository.getLocalChapters(manga)
            MangaSourceType.ONLINE_MANGADEX -> api.getChapters(manga.id, language)
            MangaSourceType.ONLINE_KITSU -> {
                val matches = runCatching {
                    api.searchManga(
                        query = manga.title,
                        language = language,
                        sort = CatalogSort.POPULAR,
                        tagId = null,
                        limit = 5
                    )
                }.getOrDefault(emptyList())
                val bestMatch = matches.firstOrNull()
                if (bestMatch != null) {
                    api.getChapters(bestMatch.id, language).map { it.copy(mangaId = manga.id) }
                } else {
                    emptyList()
                }
            }
            MangaSourceType.CUSTOM_WEB -> {
                val (_, parsedChapters) = runCatching {
                    customSiteParser.fetchMangaDetailsAndChapters(manga)
                }.getOrDefault(manga to emptyList())
                parsedChapters.forEach { ch ->
                    ch.chapterUrl?.let { url -> chapterUrlCache[ch.id] = url }
                }
                parsedChapters
            }
        }
        memoryChaptersCache[cacheKey] = chapters
        return chapters
    }

    suspend fun getChapterPages(
        manga: MangaItem,
        chapterId: String
    ): List<PageItem> {
        adHocPagesCache[chapterId]?.let { return it }

        return when (manga.sourceType) {
            MangaSourceType.LOCAL_CBZ -> localCbzRepository.getLocalPages(chapterId, manga)
            MangaSourceType.ONLINE_MANGADEX,
            MangaSourceType.ONLINE_KITSU -> {
                val dataSaver = preferencesRepository.preferencesFlow.first().dataSaver
                api.getChapterPages(chapterId, dataSaver)
            }
            MangaSourceType.CUSTOM_WEB -> {
                val url = chapterUrlCache[chapterId] ?: run {
                    val chapters = getChapters(manga, LanguageFilter.ALL, forceRefresh = false)
                    chapters.firstOrNull { it.id == chapterId }?.chapterUrl
                } ?: manga.sourceUrl.orEmpty()
                if (url.isBlank()) emptyList() else customSiteParser.fetchChapterPages(url)
            }
        }
    }

    suspend fun registerBrowserExtractedChapter(
        pageUrl: String,
        pageTitle: String,
        rawHtml: String
    ): Pair<MangaItem, ChapterItem>? {
        val pages = customSiteParser.extractPagesFromHtml(pageUrl, rawHtml)
        if (pages.isEmpty()) return null

        val shortHash = CustomSiteParser.sha1Short(pageUrl)
        val mangaId = "custom_$shortHash"
        val chapterId = "webch_$shortHash"
        val cleanTitle = pageTitle.trim().ifBlank { "Leitura Web" }

        val manga = MangaItem(
            id = mangaId,
            sourceType = MangaSourceType.CUSTOM_WEB,
            title = cleanTitle,
            author = "",
            description = pageUrl,
            coverUrl = pages.first().imageUrl,
            status = "online",
            tags = emptyList(),
            sourceUrl = pageUrl,
            sourceLabel = "NAVEGADOR"
        )
        val chapter = ChapterItem(
            id = chapterId,
            mangaId = mangaId,
            number = 1f,
            numberLabel = "1",
            title = cleanTitle,
            language = "",
            scanlator = "Navegador Protegido",
            publishedAt = System.currentTimeMillis(),
            chapterUrl = pageUrl
        )

        memoryMangaCache[mangaId] = manga
        dao.upsertManga(MangaEntity.fromDomain(manga))
        chapterUrlCache[chapterId] = pageUrl
        adHocPagesCache[chapterId] = pages
        memoryChaptersCache["${mangaId}_ALL"] = listOf(chapter)
        return manga to chapter
    }

    // Categories CRUD
    suspend fun createCategory(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return -1L
        val nextOrder = dao.getCategoryCount()
        return dao.insertCategory(FavoriteCategoryEntity(name = trimmed, sortOrder = nextOrder))
    }

    suspend fun renameCategory(categoryId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            dao.renameCategory(categoryId, trimmed)
        }
    }

    suspend fun deleteCategory(categoryId: Long) {
        dao.deleteCategoryAndRefs(categoryId)
    }

    suspend fun updateMangaCategories(manga: MangaItem, selectedCategoryIds: Set<Long>) {
        dao.setCategoriesForManga(MangaEntity.fromDomain(manga), selectedCategoryIds)
    }

    // Progress & History (respects Incognito Mode)
    suspend fun saveReadingProgress(
        manga: MangaItem,
        chapterId: String,
        chapterTitle: String,
        currentPage: Int,
        totalPages: Int
    ) {
        if (totalPages <= 0) return
        val prefs = preferencesRepository.preferencesFlow.first()
        if (prefs.incognitoMode) return

        dao.upsertManga(MangaEntity.fromDomain(manga))
        dao.upsertHistory(
            ReadingHistoryEntity(
                mangaId = manga.id,
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                currentPage = currentPage.coerceIn(0, totalPages - 1),
                totalPages = totalPages,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (currentPage >= totalPages - 1) {
            dao.markChapterCompleted(
                CompletedChapterEntity(
                    mangaId = manga.id,
                    chapterId = chapterId
                )
            )
        }
    }

    suspend fun toggleChapterCompleted(manga: MangaItem, chapterId: String, completed: Boolean) {
        dao.upsertManga(MangaEntity.fromDomain(manga))
        if (completed) {
            dao.markChapterCompleted(CompletedChapterEntity(mangaId = manga.id, chapterId = chapterId))
        } else {
            dao.unmarkChapterCompleted(manga.id, chapterId)
        }
    }

    suspend fun deleteHistoryItem(mangaId: String) {
        dao.deleteHistoryForManga(mangaId)
    }

    suspend fun clearAllHistory() {
        dao.clearAllHistory()
    }

    // Page Bookmarks
    suspend fun togglePageBookmark(
        manga: MangaItem,
        chapterId: String,
        chapterTitle: String,
        pageIndex: Int,
        totalPages: Int,
        previewUrl: String,
        currentlyBookmarked: Boolean
    ) {
        dao.upsertManga(MangaEntity.fromDomain(manga))
        if (currentlyBookmarked) {
            dao.deleteBookmarkByPage(manga.id, chapterId, pageIndex)
        } else {
            dao.insertBookmark(
                PageBookmarkEntity(
                    mangaId = manga.id,
                    chapterId = chapterId,
                    chapterTitle = chapterTitle,
                    pageIndex = pageIndex,
                    totalPages = totalPages,
                    previewUrl = previewUrl
                )
            )
        }
    }

    suspend fun removeBookmarkById(bookmarkId: Long) {
        dao.deleteBookmarkById(bookmarkId)
    }

    // Backup & Cache Management
    suspend fun exportBackup(uri: Uri): Int = backupManager.exportBackupToUri(uri)

    suspend fun restoreBackup(uri: Uri): Int = backupManager.restoreBackupFromUri(uri)

    suspend fun computeCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        computeDirSize(appContext.cacheDir)
    }

    suspend fun clearAllTemporaryCaches() = withContext(Dispatchers.IO) {
        memoryMangaCache.clear()
        memoryChaptersCache.clear()
        adHocPagesCache.clear()
        runCatching {
            val loader = SingletonImageLoader.get(appContext)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching {
            appContext.cacheDir.listFiles()?.forEach { child ->
                // Preserve WebView cookies/profile if active, delete image/http cache dirs
                if (!child.name.contains("WebView", ignoreCase = true)) {
                    child.deleteRecursively()
                }
            }
        }
    }

    private fun computeDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) total += file.length()
        }
        return total
    }

    companion object {
        const val SOURCE_FILTER_ALL = "ALL"
        const val SOURCE_FILTER_MANGADEX = "MANGADEX"
        const val SOURCE_FILTER_KITSU = "KITSU"
        const val SOURCE_FILTER_CUSTOM_PREFIX = "CUSTOM_"
    }
}
