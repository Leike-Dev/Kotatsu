package org.koitharu.kotatsu.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KotatsuDao {

    // Mangas & Local CBZ
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertManga(manga: MangaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMangas(mangas: List<MangaEntity>)

    @Query("SELECT * FROM mangas WHERE id = :mangaId LIMIT 1")
    suspend fun getMangaById(mangaId: String): MangaEntity?

    @Query("SELECT * FROM mangas")
    suspend fun getAllMangasList(): List<MangaEntity>

    @Query("SELECT * FROM mangas WHERE sourceType = 'LOCAL_CBZ' ORDER BY updatedAt DESC")
    fun observeLocalMangas(): Flow<List<MangaEntity>>

    @Query("DELETE FROM mangas WHERE id = :mangaId")
    suspend fun deleteMangaById(mangaId: String)

    @Transaction
    suspend fun upsertMangaPreservingChapterStats(manga: MangaEntity) {
        val existing = getMangaById(manga.id)
        val merged = if (existing != null) {
            manga.copy(
                knownChaptersCount = if (manga.knownChaptersCount > 0) {
                    manga.knownChaptersCount
                } else {
                    existing.knownChaptersCount
                },
                newChaptersDelta = if (manga.knownChaptersCount > 0) {
                    manga.newChaptersDelta
                } else {
                    existing.newChaptersDelta
                }
            )
        } else {
            manga
        }
        upsertManga(merged)
    }

    @Query(
        """
        UPDATE mangas
        SET knownChaptersCount = :knownCount, newChaptersDelta = :newDelta
        WHERE id = :mangaId
        """
    )
    suspend fun updateMangaChapterTracking(mangaId: String, knownCount: Int, newDelta: Int)

    @Query("UPDATE mangas SET newChaptersDelta = 0 WHERE id = :mangaId")
    suspend fun clearNewChaptersDelta(mangaId: String)

    // Favorite Categories
    @Query(
        """
        SELECT c.id, c.name, c.sortOrder, COUNT(r.mangaId) AS mangaCount
        FROM favorite_categories c
        LEFT JOIN manga_category_refs r ON c.id = r.categoryId
        GROUP BY c.id
        ORDER BY c.sortOrder ASC, c.id ASC
        """
    )
    fun observeCategoriesWithCounts(): Flow<List<CategoryWithCountRow>>

    @Query("SELECT COUNT(*) FROM favorite_categories")
    suspend fun getCategoryCount(): Int

    @Query("SELECT * FROM favorite_categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllCategoriesList(): List<FavoriteCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: FavoriteCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<FavoriteCategoryEntity>)

    @Query("UPDATE favorite_categories SET name = :newName WHERE id = :categoryId")
    suspend fun renameCategory(categoryId: Long, newName: String)

    @Query("DELETE FROM favorite_categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: Long)

    @Query("DELETE FROM manga_category_refs WHERE categoryId = :categoryId")
    suspend fun deleteCrossRefsForCategory(categoryId: Long)

    @Transaction
    suspend fun deleteCategoryAndRefs(categoryId: Long) {
        deleteCrossRefsForCategory(categoryId)
        deleteCategory(categoryId)
    }

    // Favorites Membership
    @Query(
        """
        SELECT DISTINCT m.* FROM mangas m
        INNER JOIN manga_category_refs r ON m.id = r.mangaId
        ORDER BY r.addedAt DESC
        """
    )
    fun observeAllFavoriteMangas(): Flow<List<MangaEntity>>

    @Query(
        """
        SELECT m.* FROM mangas m
        INNER JOIN manga_category_refs r ON m.id = r.mangaId
        WHERE r.categoryId = :categoryId
        ORDER BY r.addedAt DESC
        """
    )
    fun observeMangasByCategory(categoryId: Long): Flow<List<MangaEntity>>

    @Query("SELECT categoryId FROM manga_category_refs WHERE mangaId = :mangaId")
    fun observeCategoryIdsForManga(mangaId: String): Flow<List<Long>>

    @Query("SELECT * FROM manga_category_refs")
    suspend fun getAllCategoryRefsList(): List<MangaCategoryCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMangaCategoryRef(ref: MangaCategoryCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMangaCategoryRefs(refs: List<MangaCategoryCrossRef>)

    @Query("DELETE FROM manga_category_refs WHERE mangaId = :mangaId")
    suspend fun clearCategoriesForManga(mangaId: String)

    @Transaction
    suspend fun setCategoriesForManga(manga: MangaEntity, categoryIds: Set<Long>) {
        upsertMangaPreservingChapterStats(manga)
        clearCategoriesForManga(manga.id)
        val now = System.currentTimeMillis()
        categoryIds.forEach { catId ->
            insertMangaCategoryRef(
                MangaCategoryCrossRef(
                    mangaId = manga.id,
                    categoryId = catId,
                    addedAt = now
                )
            )
        }
    }

    @Transaction
    suspend fun setCategoriesForMangasBatch(mangas: List<MangaEntity>, categoryIds: Set<Long>) {
        val now = System.currentTimeMillis()
        mangas.forEach { manga ->
            upsertMangaPreservingChapterStats(manga)
            clearCategoriesForManga(manga.id)
            categoryIds.forEach { catId ->
                insertMangaCategoryRef(
                    MangaCategoryCrossRef(
                        mangaId = manga.id,
                        categoryId = catId,
                        addedAt = now
                    )
                )
            }
        }
    }

    // Reading History & Completed Chapters
    @Transaction
    @Query("SELECT * FROM reading_history ORDER BY updatedAt DESC")
    fun observeReadingHistory(): Flow<List<HistoryWithMangaRow>>

    @Query("SELECT * FROM reading_history WHERE mangaId = :mangaId LIMIT 1")
    fun observeHistoryForManga(mangaId: String): Flow<ReadingHistoryEntity?>

    @Query("SELECT * FROM reading_history")
    suspend fun getAllHistoryList(): List<ReadingHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: ReadingHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistoryList(entries: List<ReadingHistoryEntity>)

    @Query("DELETE FROM reading_history WHERE mangaId = :mangaId")
    suspend fun deleteHistoryForManga(mangaId: String)

    @Query("DELETE FROM reading_history")
    suspend fun clearAllHistory()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markChapterCompleted(entry: CompletedChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedChapters(entries: List<CompletedChapterEntity>)

    @Query("DELETE FROM completed_chapters WHERE mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun unmarkChapterCompleted(mangaId: String, chapterId: String)

    @Query("DELETE FROM completed_chapters WHERE mangaId = :mangaId")
    suspend fun unmarkAllChaptersForManga(mangaId: String)

    @Query("SELECT chapterId FROM completed_chapters WHERE mangaId = :mangaId")
    fun observeCompletedChapterIds(mangaId: String): Flow<List<String>>

    @Query("SELECT mangaId, COUNT(*) AS completedCount FROM completed_chapters GROUP BY mangaId")
    fun observeCompletedCountsByManga(): Flow<List<MangaCompletedCountRow>>

    @Query("SELECT * FROM completed_chapters ORDER BY completedAt DESC")
    fun observeAllCompletedChapters(): Flow<List<CompletedChapterEntity>>

    @Query("SELECT * FROM completed_chapters")
    suspend fun getAllCompletedChaptersList(): List<CompletedChapterEntity>

    // Page Bookmarks
    @Transaction
    @Query("SELECT * FROM page_bookmarks ORDER BY createdAt DESC")
    fun observeAllBookmarks(): Flow<List<BookmarkWithMangaRow>>

    @Query("SELECT * FROM page_bookmarks")
    suspend fun getAllBookmarksList(): List<PageBookmarkEntity>

    @Query("SELECT pageIndex FROM page_bookmarks WHERE mangaId = :mangaId AND chapterId = :chapterId")
    fun observeBookmarkedPagesForChapter(mangaId: String, chapterId: String): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: PageBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<PageBookmarkEntity>)

    @Query(
        """
        DELETE FROM page_bookmarks
        WHERE mangaId = :mangaId AND chapterId = :chapterId AND pageIndex = :pageIndex
        """
    )
    suspend fun deleteBookmarkByPage(mangaId: String, chapterId: String, pageIndex: Int)

    @Query("DELETE FROM page_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: Long)

    @Query("DELETE FROM page_bookmarks WHERE mangaId = :mangaId")
    suspend fun deleteBookmarksForManga(mangaId: String)

    // Custom External Sources
    @Query("SELECT * FROM custom_sources ORDER BY createdAt DESC")
    fun observeCustomSources(): Flow<List<CustomSourceEntity>>

    @Query("SELECT * FROM custom_sources WHERE enabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabledCustomSources(): List<CustomSourceEntity>

    @Query("SELECT * FROM custom_sources ORDER BY createdAt DESC")
    suspend fun getAllCustomSourcesList(): List<CustomSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomSource(source: CustomSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomSources(sources: List<CustomSourceEntity>)

    @Query("UPDATE custom_sources SET enabled = :enabled WHERE id = :sourceId")
    suspend fun setCustomSourceEnabled(sourceId: Long, enabled: Boolean)

    @Query(
        """
        UPDATE custom_sources
        SET name = :name, baseUrl = :baseUrl, searchUrlTemplate = :searchTemplate
        WHERE id = :sourceId
        """
    )
    suspend fun updateCustomSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        searchTemplate: String
    )

    @Query("DELETE FROM custom_sources WHERE id = :sourceId")
    suspend fun deleteCustomSource(sourceId: Long)
}
