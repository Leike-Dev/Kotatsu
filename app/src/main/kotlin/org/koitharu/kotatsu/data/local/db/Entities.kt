package org.koitharu.kotatsu.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable
import org.koitharu.kotatsu.domain.model.CustomMangaSource
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag

@Serializable
@Entity(tableName = "mangas")
data class MangaEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String,
    val status: String,
    val tagsSerialized: String,
    val localPath: String?,
    val sourceUrl: String? = null,
    @ColumnInfo(defaultValue = "") val sourceLabel: String = "",
    @ColumnInfo(defaultValue = "0") val knownChaptersCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val newChaptersDelta: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): MangaItem {
        val parsedTags = if (tagsSerialized.isBlank()) {
            emptyList()
        } else {
            tagsSerialized.split("||").mapNotNull { token ->
                val parts = token.split("::", limit = 2)
                if (parts.size == 2) MangaTag(id = parts[0], name = parts[1]) else null
            }
        }
        val source = runCatching { MangaSourceType.valueOf(sourceType) }
            .getOrDefault(MangaSourceType.ONLINE_MANGADEX)
        return MangaItem(
            id = id,
            sourceType = source,
            title = title,
            author = author,
            description = description,
            coverUrl = coverUrl,
            status = status,
            tags = parsedTags,
            localPath = localPath,
            sourceUrl = sourceUrl,
            sourceLabel = sourceLabel,
            knownChaptersCount = knownChaptersCount,
            newChaptersDelta = newChaptersDelta
        )
    }

    companion object {
        fun fromDomain(item: MangaItem): MangaEntity {
            val serialized = item.tags.joinToString("||") { "${it.id}::${it.name}" }
            return MangaEntity(
                id = item.id,
                sourceType = item.sourceType.name,
                title = item.title,
                author = item.author,
                description = item.description,
                coverUrl = item.coverUrl,
                status = item.status,
                tagsSerialized = serialized,
                localPath = item.localPath,
                sourceUrl = item.sourceUrl,
                sourceLabel = item.sourceLabel,
                knownChaptersCount = item.knownChaptersCount,
                newChaptersDelta = item.newChaptersDelta,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}

@Serializable
@Entity(tableName = "favorite_categories")
data class FavoriteCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "manga_category_refs",
    primaryKeys = ["mangaId", "categoryId"],
    indices = [Index("categoryId"), Index("mangaId")]
)
data class MangaCategoryCrossRef(
    val mangaId: String,
    val categoryId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey val mangaId: String,
    val chapterId: String,
    val chapterTitle: String,
    val currentPage: Int,
    val totalPages: Int,
    val updatedAt: Long
)

@Serializable
@Entity(
    tableName = "completed_chapters",
    primaryKeys = ["mangaId", "chapterId"],
    indices = [Index("mangaId")]
)
data class CompletedChapterEntity(
    val mangaId: String,
    val chapterId: String,
    val completedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "page_bookmarks",
    indices = [Index(value = ["mangaId", "chapterId", "pageIndex"], unique = true)]
)
data class PageBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: String,
    val chapterId: String,
    val chapterTitle: String,
    val pageIndex: Int,
    val totalPages: Int,
    val previewUrl: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "custom_sources")
data class CustomSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val searchUrlTemplate: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): CustomMangaSource = CustomMangaSource(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchUrlTemplate = searchUrlTemplate,
        enabled = enabled
    )

    companion object {
        fun fromDomain(source: CustomMangaSource): CustomSourceEntity = CustomSourceEntity(
            id = source.id,
            name = source.name,
            baseUrl = source.baseUrl,
            searchUrlTemplate = source.searchUrlTemplate,
            enabled = source.enabled
        )
    }
}

data class HistoryWithMangaRow(
    @Embedded val history: ReadingHistoryEntity,
    @Relation(parentColumn = "mangaId", entityColumn = "id")
    val manga: MangaEntity?
)

data class BookmarkWithMangaRow(
    @Embedded val bookmark: PageBookmarkEntity,
    @Relation(parentColumn = "mangaId", entityColumn = "id")
    val manga: MangaEntity?
)

data class CategoryWithCountRow(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val mangaCount: Int
)

data class MangaCompletedCountRow(
    val mangaId: String,
    val completedCount: Int
)
