package org.koitharu.kotatsu.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object FavoritesRoute

@Serializable
object HistoryRoute

@Serializable
data class ExploreRoute(
    val initialQuery: String = "",
    val initialTagId: String = ""
)

@Serializable
object BookmarksRoute

@Serializable
object SettingsRoute

@Serializable
object StatisticsRoute

@Serializable
data class MangaDetailsRoute(
    val mangaId: String,
    val sourceTypeName: String
)

@Serializable
data class ReaderRoute(
    val mangaId: String,
    val sourceTypeName: String,
    val chapterId: String,
    val initialPage: Int = 0
)

@Serializable
data class SourceBrowserRoute(
    val initialUrl: String,
    val title: String = ""
)
