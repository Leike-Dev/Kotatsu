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
import org.koitharu.kotatsu.data.remote.SourceCatalogDirectory
import org.koitharu.kotatsu.domain.model.CatalogSort
import org.koitharu.kotatsu.domain.model.ChapterItem
import org.koitharu.kotatsu.domain.model.CustomMangaSource
import org.koitharu.kotatsu.domain.model.DailyActivityPoint
import org.koitharu.kotatsu.domain.model.FavoriteCategory
import org.koitharu.kotatsu.domain.model.FavoritesUpdateSummary
import org.koitharu.kotatsu.domain.model.GenreStatItem
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.LibraryStats
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaProgressStatItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag
import org.koitharu.kotatsu.domain.model.PageBookmarkItem
import org.koitharu.kotatsu.domain.model.PageItem
import org.koitharu.kotatsu.domain.model.ReadingAnalytics
import org.koitharu.kotatsu.domain.model.ReadingHistoryItem
import org.koitharu.kotatsu.domain.model.SourceHealthCheckResult
import org.koitharu.kotatsu.domain.model.SourceStatItem
import org.koitharu.kotatsu.widget.KotatsuWidgetManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

    val completedCountsByMangaFlow: Flow<Map<String, Int>> = dao.observeCompletedCountsByManga().map { rows ->
        rows.associate { it.mangaId to it.completedCount }
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

    val readingAnalyticsFlow: Flow<ReadingAnalytics> = combine(
        allFavoritesFlow,
        readingHistoryFlow,
        dao.observeAllCompletedChapters(),
        allBookmarksFlow,
        completedCountsByMangaFlow
    ) { favs, hist, completedEntities, bookmarks, completedCounts ->
        buildReadingAnalytics(
            favorites = favs,
            history = hist,
            completedEntities = completedEntities,
            bookmarksCount = bookmarks.size,
            completedCounts = completedCounts
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
        KotatsuWidgetManager.refreshAllWidgetsAsync(appContext)
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
            val existing = memoryMangaCache[item.id]
            val merged = if (existing != null && item.knownChaptersCount == 0) {
                item.copy(
                    knownChaptersCount = existing.knownChaptersCount,
                    newChaptersDelta = existing.newChaptersDelta
                )
            } else {
                item
            }
            memoryMangaCache[item.id] = merged
            if (merged.sourceType == MangaSourceType.CUSTOM_WEB || merged.sourceType == MangaSourceType.ONLINE_KITSU) {
                runCatching { dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(merged)) }
            }
        }
        results
    }

    suspend fun getPopularTags(): List<MangaTag> = api.getPopularTags()

    // Custom Sources CRUD & Catalog Health / Favicon Discovery
    suspend fun addCustomSource(
        name: String,
        baseUrl: String,
        searchTemplate: String,
        faviconUrl: String = "",
        languageCode: String = "ALL",
        catalogType: String = "WEB"
    ): Long {
        val cleanName = name.trim()
        val normalizedUrl = baseUrl.trim().let {
            if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
        }.trimEnd('/')
        if (cleanName.isBlank() || normalizedUrl.isBlank()) return -1L
        val existing = dao.getAllCustomSourcesList().firstOrNull {
            it.baseUrl.trimEnd('/').equals(normalizedUrl, ignoreCase = true)
        }
        if (existing != null) {
            dao.setCustomSourceEnabled(existing.id, true)
            return existing.id
        }
        val resolvedFavicon = faviconUrl.trim().ifBlank {
            SourceCatalogDirectory.defaultFaviconUrl(normalizedUrl)
        }
        return dao.insertCustomSource(
            CustomSourceEntity(
                name = cleanName,
                baseUrl = normalizedUrl,
                searchUrlTemplate = searchTemplate.trim(),
                enabled = true,
                faviconUrl = resolvedFavicon,
                languageCode = languageCode,
                catalogType = catalogType
            )
        )
    }

    suspend fun updateCustomSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        searchTemplate: String,
        faviconUrl: String = ""
    ) {
        val cleanName = name.trim()
        val normalizedUrl = baseUrl.trim().let {
            if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
        }.trimEnd('/')
        if (cleanName.isBlank() || normalizedUrl.isBlank()) return
        val resolvedFavicon = faviconUrl.trim().ifBlank {
            SourceCatalogDirectory.defaultFaviconUrl(normalizedUrl)
        }
        dao.updateCustomSource(
            sourceId = sourceId,
            name = cleanName,
            baseUrl = normalizedUrl,
            searchTemplate = searchTemplate.trim(),
            faviconUrl = resolvedFavicon
        )
    }

    suspend fun setCustomSourceEnabled(sourceId: Long, enabled: Boolean) {
        dao.setCustomSourceEnabled(sourceId, enabled)
    }

    suspend fun deleteCustomSource(sourceId: Long) {
        dao.deleteCustomSource(sourceId)
    }

    suspend fun testSourceConnection(
        sourceId: Long? = null,
        name: String,
        baseUrl: String,
        probeUrlOverride: String? = null
    ): SourceHealthCheckResult {
        val result = customSiteParser.probeSiteHealthAndFavicon(
            baseUrl = baseUrl,
            sourceName = name,
            probeUrlOverride = probeUrlOverride
        )
        if (sourceId != null && sourceId > 0L && result.resolvedFaviconUrl.isNotBlank()) {
            runCatching {
                dao.updateCustomSourceFavicon(sourceId, result.resolvedFaviconUrl)
            }
        }
        return result
    }

    suspend fun importLocalCbz(uri: Uri): MangaItem {
        val imported = localCbzRepository.importCbzUri(uri)
        val localChapters = runCatching { localCbzRepository.getLocalChapters(imported) }.getOrDefault(emptyList())
        val withCount = imported.copy(knownChaptersCount = localChapters.size)
        memoryMangaCache[withCount.id] = withCount
        dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(withCount))
        return withCount
    }

    suspend fun deleteLocalManga(manga: MangaItem) {
        localCbzRepository.deleteLocalFiles(manga.localPath)
        dao.clearCategoriesForManga(manga.id)
        dao.deleteHistoryForManga(manga.id)
        dao.unmarkAllChaptersForManga(manga.id)
        dao.deleteBookmarksForManga(manga.id)
        dao.deleteMangaById(manga.id)
        memoryMangaCache.remove(manga.id)
        memoryChaptersCache.remove(manga.id)
    }

    suspend fun getMangaDetails(mangaId: String, sourceType: MangaSourceType): MangaItem {
        val dbItem = dao.getMangaById(mangaId)?.toDomain()
        val cached = dbItem ?: memoryMangaCache[mangaId]

        return when (sourceType) {
            MangaSourceType.ONLINE_MANGADEX -> {
                if (cached != null && cached.description.isNotBlank()) {
                    memoryMangaCache[mangaId] = cached
                    cached
                } else {
                    val remote = api.getMangaById(mangaId)
                    val merged = if (cached != null) {
                        remote.copy(
                            knownChaptersCount = cached.knownChaptersCount,
                            newChaptersDelta = cached.newChaptersDelta
                        )
                    } else {
                        remote
                    }
                    memoryMangaCache[mangaId] = merged
                    if (dbItem != null) {
                        dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(merged))
                    }
                    merged
                }
            }

            MangaSourceType.ONLINE_KITSU -> {
                val remote = cached ?: kitsuApi.getMangaById(mangaId)
                    ?: throw IllegalStateException("Obra do catálogo Kitsu não encontrada.")
                memoryMangaCache[mangaId] = remote
                dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(remote))
                remote
            }

            MangaSourceType.CUSTOM_WEB -> {
                val base = cached ?: throw IllegalStateException("Obra da fonte externa não encontrada.")
                if (base.sourceUrl != null) {
                    val (enrichedRaw, chapters) = runCatching {
                        customSiteParser.fetchMangaDetailsAndChapters(base)
                    }.getOrDefault(base to emptyList())
                    val enriched = enrichedRaw.copy(
                        knownChaptersCount = if (chapters.isNotEmpty()) chapters.size else base.knownChaptersCount,
                        newChaptersDelta = base.newChaptersDelta
                    )
                    memoryMangaCache[mangaId] = enriched
                    dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(enriched))
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
        forceRefresh: Boolean = false,
        recordInspection: Boolean = false
    ): List<ChapterItem> {
        val cacheKey = if (manga.sourceType == MangaSourceType.CUSTOM_WEB) {
            "${manga.id}_ALL"
        } else {
            "${manga.id}_${language.name}"
        }
        val chapters = if (!forceRefresh && memoryChaptersCache.containsKey(cacheKey)) {
            memoryChaptersCache[cacheKey].orEmpty()
        } else {
            val fetched = when (manga.sourceType) {
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
            memoryChaptersCache[cacheKey] = fetched
            fetched
        }

        if (recordInspection) {
            val knownCount = maxOf(chapters.size, manga.knownChaptersCount)
            val updatedManga = manga.copy(
                knownChaptersCount = knownCount,
                newChaptersDelta = 0
            )
            memoryMangaCache[manga.id] = updatedManga
            if (dao.getMangaById(manga.id) != null) {
                dao.updateMangaChapterTracking(
                    mangaId = manga.id,
                    knownCount = knownCount,
                    newDelta = 0
                )
            }
        }
        return chapters
    }

    suspend fun checkFavoritesUpdates(
        onProgress: (checked: Int, total: Int) -> Unit = { _, _ -> }
    ): FavoritesUpdateSummary = coroutineScope {
        val favorites = allFavoritesFlow.first()
        if (favorites.isEmpty()) {
            return@coroutineScope FavoritesUpdateSummary(
                checkedCount = 0,
                mangasWithNewCount = 0,
                totalNewChapters = 0
            )
        }
        val prefLang = preferencesRepository.preferencesFlow.first().preferredLanguage
        val checkedCounter = AtomicInteger(0)
        var mangasWithNew = 0
        var totalNew = 0

        onProgress(0, favorites.size)

        favorites.chunked(4).forEach { batch ->
            val batchDeltas = batch.map { fav ->
                async {
                    val chapters = runCatching {
                        getChapters(
                            manga = fav,
                            language = prefLang,
                            forceRefresh = true,
                            recordInspection = false
                        )
                    }.getOrNull()

                    var newlyDetected = 0
                    if (chapters != null) {
                        val fetchedCount = chapters.size
                        val previousKnown = fav.knownChaptersCount
                        newlyDetected = if (previousKnown > 0 && fetchedCount > previousKnown) {
                            fetchedCount - previousKnown
                        } else {
                            0
                        }
                        val nextKnown = maxOf(previousKnown, fetchedCount)
                        val nextDelta = fav.newChaptersDelta + newlyDetected
                        if (nextKnown != previousKnown || nextDelta != fav.newChaptersDelta) {
                            dao.updateMangaChapterTracking(
                                mangaId = fav.id,
                                knownCount = nextKnown,
                                newDelta = nextDelta
                            )
                            memoryMangaCache[fav.id] = fav.copy(
                                knownChaptersCount = nextKnown,
                                newChaptersDelta = nextDelta
                            )
                        }
                    }
                    val done = checkedCounter.incrementAndGet()
                    onProgress(done, favorites.size)
                    newlyDetected
                }
            }.awaitAll()

            batchDeltas.forEach { delta ->
                if (delta > 0) {
                    mangasWithNew++
                    totalNew += delta
                }
            }
        }

        KotatsuWidgetManager.refreshAllWidgetsAsync(appContext)
        FavoritesUpdateSummary(
            checkedCount = favorites.size,
            mangasWithNewCount = mangasWithNew,
            totalNewChapters = totalNew
        )
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

        memoryMangaCache[mangaId] = manga.copy(knownChaptersCount = 1)
        dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(manga.copy(knownChaptersCount = 1)))
        chapterUrlCache[chapterId] = pageUrl
        adHocPagesCache[chapterId] = pages
        memoryChaptersCache["${mangaId}_ALL"] = listOf(chapter)
        return manga to chapter
    }

    // Categories CRUD & Batch Shelf Operations
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
        val cachedKnown = memoryMangaCache[manga.id]?.knownChaptersCount ?: 0
        val enriched = if (manga.knownChaptersCount == 0 && cachedKnown > 0) {
            manga.copy(knownChaptersCount = cachedKnown)
        } else {
            manga
        }
        dao.setCategoriesForManga(MangaEntity.fromDomain(enriched), selectedCategoryIds)
        KotatsuWidgetManager.refreshAllWidgetsAsync(appContext)
    }

    suspend fun updateMangasCategoriesBatch(mangas: List<MangaItem>, selectedCategoryIds: Set<Long>) {
        if (mangas.isEmpty()) return
        dao.setCategoriesForMangasBatch(
            mangas = mangas.map(MangaEntity::fromDomain),
            categoryIds = selectedCategoryIds
        )
        KotatsuWidgetManager.refreshAllWidgetsAsync(appContext)
    }

    suspend fun markAllChaptersCompleted(manga: MangaItem, chapters: List<ChapterItem>) {
        val known = maxOf(manga.knownChaptersCount, chapters.size)
        val updated = manga.copy(knownChaptersCount = known, newChaptersDelta = 0)
        memoryMangaCache[manga.id] = updated
        dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(updated))
        if (chapters.isNotEmpty()) {
            val now = System.currentTimeMillis()
            dao.insertCompletedChapters(
                chapters.map { ch ->
                    CompletedChapterEntity(
                        mangaId = manga.id,
                        chapterId = ch.id,
                        completedAt = now
                    )
                }
            )
        }
        dao.updateMangaChapterTracking(mangaId = manga.id, knownCount = known, newDelta = 0)
    }

    suspend fun unmarkAllChaptersCompleted(mangaId: String) {
        dao.unmarkAllChaptersForManga(mangaId)
    }

    suspend fun markMangasCaughtUpBatch(mangas: List<MangaItem>) {
        if (mangas.isEmpty()) return
        val prefLang = preferencesRepository.preferencesFlow.first().preferredLanguage
        val now = System.currentTimeMillis()
        mangas.forEach { manga ->
            val chapters = runCatching {
                getChapters(manga, prefLang, forceRefresh = false, recordInspection = true)
            }.getOrDefault(emptyList())
            if (chapters.isNotEmpty()) {
                dao.insertCompletedChapters(
                    chapters.map { ch ->
                        CompletedChapterEntity(
                            mangaId = manga.id,
                            chapterId = ch.id,
                            completedAt = now
                        )
                    }
                )
                val known = maxOf(manga.knownChaptersCount, chapters.size)
                dao.updateMangaChapterTracking(manga.id, known, 0)
            } else {
                dao.clearNewChaptersDelta(manga.id)
            }
        }
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

        dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(manga))
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
        KotatsuWidgetManager.refreshAllWidgetsAsync(appContext)
    }

    suspend fun toggleChapterCompleted(manga: MangaItem, chapterId: String, completed: Boolean) {
        dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(manga))
        if (completed) {
            dao.markChapterCompleted(CompletedChapterEntity(mangaId = manga.id, chapterId = chapterId))
        } else {
            dao.unmarkChapterCompleted(manga.id, chapterId)
        }
        KotatsuWidgetManager.refreshAllWidgetsAsync(appContext)
    }

    suspend fun deleteHistoryItem(mangaId: String) {
        dao.deleteHistoryForManga(mangaId)
        KotatsuWidgetManager.refreshAllWidgetsAsync(appContext)
    }

    suspend fun clearAllHistory() {
        dao.clearAllHistory()
        KotatsuWidgetManager.refreshAllWidgetsAsync(appContext)
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
        dao.upsertMangaPreservingChapterStats(MangaEntity.fromDomain(manga))
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

    private fun buildReadingAnalytics(
        favorites: List<MangaItem>,
        history: List<ReadingHistoryItem>,
        completedEntities: List<CompletedChapterEntity>,
        bookmarksCount: Int,
        completedCounts: Map<String, Int>
    ): ReadingAnalytics {
        val historyByManga = history.associateBy { it.manga.id }
        val allKnownWorks = (favorites + history.map { it.manga }).distinctBy { it.id }

        // Pages read: active history pages + completed chapters (22 avg pages when not active history chapter)
        val activePagesSum = history.sumOf { (it.currentPage + 1).coerceAtLeast(1) }
        val extraCompletedChapters = completedEntities.count { entity ->
            historyByManga[entity.mangaId]?.chapterId != entity.chapterId
        }
        val estimatedPages = activePagesSum + (extraCompletedChapters * 22)

        var inProgressCount = 0
        var caughtUpCount = 0
        var shelfReadSum = 0
        var shelfTotalSum = 0

        favorites.forEach { fav ->
            val read = completedCounts[fav.id] ?: 0
            val hasHistory = historyByManga.containsKey(fav.id)
            val total = maxOf(fav.knownChaptersCount, read)
            shelfReadSum += read.coerceAtMost(total)
            shelfTotalSum += total

            when {
                total > 0 && read >= total -> caughtUpCount++
                read > 0 || hasHistory -> inProgressCount++
            }
        }

        // Also count non-favorited works actively in history as in-progress
        history.forEach { entry ->
            if (favorites.none { it.id == entry.manga.id }) {
                inProgressCount++
            }
        }

        // 7-Day Activity Buckets (oldest -> today)
        val localePt = Locale.forLanguageTag("pt-BR")
        val dayFmt = SimpleDateFormat("EEE", localePt)
        val todayStartCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMillis = todayStartCal.timeInMillis
        val dayMillis = 24L * 60L * 60L * 1000L

        val seenDayChapterKeys = HashSet<String>()
        val dayCounts = IntArray(7)

        completedEntities.forEach { item ->
            val diffDays = ((todayStartMillis - startOfDayMillis(item.completedAt)) / dayMillis).toInt()
            if (diffDays in 0..6) {
                val bucketIdx = 6 - diffDays
                val key = "${bucketIdx}_${item.mangaId}_${item.chapterId}"
                if (seenDayChapterKeys.add(key)) {
                    dayCounts[bucketIdx]++
                }
            }
        }
        history.forEach { item ->
            val diffDays = ((todayStartMillis - startOfDayMillis(item.updatedAt)) / dayMillis).toInt()
            if (diffDays in 0..6) {
                val bucketIdx = 6 - diffDays
                val key = "${bucketIdx}_${item.manga.id}_${item.chapterId}"
                if (seenDayChapterKeys.add(key)) {
                    dayCounts[bucketIdx]++
                }
            }
        }

        val dailyActivity = (0..6).map { idx ->
            val daysAgo = 6 - idx
            val cal = (todayStartCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            val rawLabel = dayFmt.format(cal.time)
                .trimEnd('.')
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(localePt) else it.toString() }
            DailyActivityPoint(
                dayLabel = rawLabel,
                count = dayCounts[idx],
                isToday = daysAgo == 0
            )
        }

        // Top Genres across user's shelf & history
        val genreCounts = mutableMapOf<String, Int>()
        allKnownWorks.forEach { work ->
            work.tags.forEach { tag ->
                val clean = tag.name.trim()
                if (clean.isNotEmpty()) {
                    genreCounts[clean] = (genreCounts[clean] ?: 0) + 1
                }
            }
        }
        val sortedGenres = genreCounts.entries.sortedByDescending { it.value }.take(6)
        val maxGenreCount = sortedGenres.firstOrNull()?.value?.coerceAtLeast(1) ?: 1
        val topGenres = sortedGenres.map { (name, count) ->
            GenreStatItem(
                genreName = name,
                mangaCount = count,
                fractionOfMax = (count.toFloat() / maxGenreCount.toFloat()).coerceIn(0.08f, 1f)
            )
        }

        // Source Breakdown
        val totalWorksCount = allKnownWorks.size.coerceAtLeast(1)
        val sourceBreakdown = allKnownWorks
            .groupBy { it.badgeText }
            .map { (label, list) ->
                SourceStatItem(
                    sourceLabel = label,
                    count = list.size,
                    fraction = (list.size.toFloat() / totalWorksCount.toFloat()).coerceIn(0f, 1f)
                )
            }
            .sortedByDescending { it.count }

        // Per-Manga Active Progress
        val mangaProgressList = allKnownWorks.mapNotNull { work ->
            val read = completedCounts[work.id] ?: 0
            val histEntry = historyByManga[work.id]
            val effectiveRead = if (read == 0 && histEntry != null && histEntry.progressFraction >= 0.9f) 1 else read
            val total = maxOf(work.knownChaptersCount, effectiveRead, if (histEntry != null) 1 else 0)
            if (total <= 0) {
                null
            } else {
                MangaProgressStatItem(
                    manga = work,
                    readChapters = effectiveRead,
                    totalChapters = total
                )
            }
        }.sortedWith(
            compareByDescending<MangaProgressStatItem> { it.readChapters > 0 }
                .thenByDescending { it.completionFraction }
                .thenByDescending { it.totalChapters }
        ).take(12)

        return ReadingAnalytics(
            favoritesCount = favorites.size,
            completedChaptersCount = completedEntities.size,
            estimatedPagesRead = estimatedPages,
            inProgressWorksCount = inProgressCount,
            caughtUpWorksCount = caughtUpCount,
            bookmarksCount = bookmarksCount,
            shelfReadChaptersSum = shelfReadSum,
            shelfTotalChaptersSum = shelfTotalSum,
            dailyActivity = dailyActivity,
            topGenres = topGenres,
            sourceBreakdown = sourceBreakdown,
            mangaProgressList = mangaProgressList
        )
    }

    private fun startOfDayMillis(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        const val SOURCE_FILTER_ALL = "ALL"
        const val SOURCE_FILTER_MANGADEX = "MANGADEX"
        const val SOURCE_FILTER_KITSU = "KITSU"
        const val SOURCE_FILTER_CUSTOM_PREFIX = "CUSTOM_"
    }
}
