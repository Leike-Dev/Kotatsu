package org.koitharu.kotatsu.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MangaSourceType {
    ONLINE_MANGADEX,
    ONLINE_KITSU,
    CUSTOM_WEB,
    LOCAL_CBZ
}

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class ExploreLayoutMode {
    GRID_STANDARD,
    GRID_COMPACT,
    GRID_LARGE,
    LIST_DETAILED
}

enum class ChapterDisplayMode {
    LIST,
    DETAILED,
    GRID
}

enum class ReaderMode {
    STANDARD_LTR,
    MANGA_RTL,
    VERTICAL_PAGED,
    WEBTOON
}

enum class ReaderScaleMode {
    FIT_SCREEN,
    FIT_WIDTH,
    FIT_HEIGHT,
    CROP
}

enum class ReaderBackground {
    AMOLED,
    SUMI_INK,
    SEPIA,
    PAPER_LIGHT
}

enum class DohProvider {
    NONE,
    CLOUDFLARE,
    GOOGLE
}

enum class LanguageFilter(val apiCode: String?) {
    ALL(null),
    PT_BR("pt-br"),
    EN("en"),
    ES("es")
}

enum class CatalogSort {
    POPULAR,
    LATEST,
    RATING
}

@Serializable
data class MangaTag(
    val id: String,
    val name: String
)

@Serializable
data class CustomMangaSource(
    val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val searchUrlTemplate: String = "",
    val enabled: Boolean = true
)

data class MangaItem(
    val id: String,
    val sourceType: MangaSourceType,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String,
    val status: String,
    val tags: List<MangaTag>,
    val localPath: String? = null,
    val sourceUrl: String? = null,
    val sourceLabel: String = ""
) {
    val badgeText: String
        get() = when {
            sourceLabel.isNotBlank() -> sourceLabel.uppercase()
            sourceType == MangaSourceType.LOCAL_CBZ -> "LOCAL CBZ"
            sourceType == MangaSourceType.ONLINE_KITSU -> "KITSU"
            sourceType == MangaSourceType.CUSTOM_WEB -> "WEB"
            else -> "MANGADEX"
        }
}

data class ChapterItem(
    val id: String,
    val mangaId: String,
    val number: Float,
    val numberLabel: String,
    val title: String,
    val language: String,
    val scanlator: String,
    val publishedAt: Long,
    val localFolderOrZipPath: String? = null,
    val chapterUrl: String? = null
) {
    val displayTitle: String
        get() = buildString {
            if (numberLabel.isNotBlank()) {
                append("Cap. ").append(numberLabel)
                if (title.isNotBlank() && !title.equals("Cap. $numberLabel", ignoreCase = true)) {
                    append(" — ").append(title)
                }
            } else if (title.isNotBlank()) {
                append(title)
            } else {
                append("Capítulo Único")
            }
        }
}

data class PageItem(
    val index: Int,
    val imageUrl: String
)

data class FavoriteCategory(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val mangaCount: Int = 0
)

data class ReadingHistoryItem(
    val manga: MangaItem,
    val chapterId: String,
    val chapterTitle: String,
    val currentPage: Int,
    val totalPages: Int,
    val updatedAt: Long
) {
    val progressFraction: Float
        get() = if (totalPages <= 0) 0f else ((currentPage + 1).toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
}

data class PageBookmarkItem(
    val id: Long,
    val manga: MangaItem,
    val chapterId: String,
    val chapterTitle: String,
    val pageIndex: Int,
    val totalPages: Int,
    val previewUrl: String,
    val createdAt: Long
)

data class LibraryStats(
    val favoritesCount: Int = 0,
    val historyCount: Int = 0,
    val bookmarksCount: Int = 0,
    val localCbzCount: Int = 0,
    val customSourcesCount: Int = 0
)

data class AppPreferences(
    // Appearance
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val amoledDark: Boolean = false,
    val dynamicColors: Boolean = false,
    val showSourceBadges: Boolean = true,

    // Explore / Descobrir Customization
    val exploreLayoutMode: ExploreLayoutMode = ExploreLayoutMode.GRID_STANDARD,
    val showExploreSpotlight: Boolean = true,
    val showExploreSourcesBar: Boolean = true,
    val showExploreGenres: Boolean = true,
    val showExploreLocalSection: Boolean = true,
    val enableKitsuCatalog: Boolean = true,
    val hideNsfw: Boolean = true,
    val preferredLanguage: LanguageFilter = LanguageFilter.ALL,

    // Manga Details Visualization
    val chapterDisplayMode: ChapterDisplayMode = ChapterDisplayMode.LIST,

    // Reader Visualization & Controls
    val defaultReaderMode: ReaderMode = ReaderMode.STANDARD_LTR,
    val readerScaleMode: ReaderScaleMode = ReaderScaleMode.FIT_SCREEN,
    val readerBackground: ReaderBackground = ReaderBackground.SUMI_INK,
    val keepScreenOn: Boolean = true,
    val volumeKeysNavigation: Boolean = false,
    val pageTransitions: Boolean = true,
    val showReaderThumbnails: Boolean = true,
    val showReaderMiniInfo: Boolean = true,
    val webtoonSeamlessGap: Boolean = true,
    val readerBrightnessDim: Float = 0f,
    val readerGrayscale: Boolean = false,
    val readerInvertColors: Boolean = false,
    val dataSaver: Boolean = false,

    // Network & Anti-Adblock Protection
    val adShieldEnabled: Boolean = true,
    val antiAdblockBypass: Boolean = true,
    val blockPopupsRedirects: Boolean = true,
    val dohProvider: DohProvider = DohProvider.NONE,
    val customUserAgent: String = DEFAULT_BROWSER_UA,

    // Security & Privacy
    val appLockEnabled: Boolean = false,
    val appLockPin: String = "",
    val appLockSystemCredential: Boolean = true,
    val secureScreenEnabled: Boolean = false,
    val incognitoMode: Boolean = false
) {
    companion object {
        const val DEFAULT_BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"
    }
}
