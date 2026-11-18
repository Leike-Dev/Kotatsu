package org.koitharu.kotatsu.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.FavoriteCategory
import org.koitharu.kotatsu.domain.model.FavoritesFilterMode
import org.koitharu.kotatsu.domain.model.FavoritesSortMode
import org.koitharu.kotatsu.domain.model.MangaItem

data class FavoriteShelfItemUi(
    val manga: MangaItem,
    val unreadCount: Int,
    val newChaptersCount: Int,
    val completedCount: Int,
    val progressFraction: Float?,
    val lastReadAt: Long
)

sealed interface FavoritesFeedbackEvent {
    data object EmptyShelfForUpdates : FavoritesFeedbackEvent
    data class UpdatesFound(val totalNewChapters: Int, val mangasWithNewCount: Int) : FavoritesFeedbackEvent
    data class NoUpdatesFound(val checkedCount: Int) : FavoritesFeedbackEvent
    data class BatchCategoriesSaved(val count: Int) : FavoritesFeedbackEvent
    data class BatchCaughtUpSaved(val count: Int) : FavoritesFeedbackEvent
    data class BatchRemovedSaved(val count: Int) : FavoritesFeedbackEvent
}

class FavoritesViewModel(
    private val repository: MangaRepository
) : ViewModel() {

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterMode = MutableStateFlow(FavoritesFilterMode.ALL)
    val filterMode: StateFlow<FavoritesFilterMode> = _filterMode.asStateFlow()

    private val _sortMode = MutableStateFlow(FavoritesSortMode.ADDED_RECENT)
    val sortMode: StateFlow<FavoritesSortMode> = _sortMode.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedMangaIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMangaIds: StateFlow<Set<String>> = _selectedMangaIds.asStateFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    private val _updateProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val updateProgress: StateFlow<Pair<Int, Int>?> = _updateProgress.asStateFlow()

    private val _feedbackEvent = MutableStateFlow<FavoritesFeedbackEvent?>(null)
    val feedbackEvent: StateFlow<FavoritesFeedbackEvent?> = _feedbackEvent.asStateFlow()

    val preferences: StateFlow<AppPreferences> = repository.preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    val categories: StateFlow<List<FavoriteCategory>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allFavoritesCount: StateFlow<List<MangaItem>> = repository.allFavoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val categoryMangasFlow = _selectedCategoryId.flatMapLatest { catId ->
        repository.observeFavoritesByCategory(catId)
    }

    private data class FilterSortInputs(
        val query: String,
        val filter: FavoritesFilterMode,
        val sort: FavoritesSortMode
    )

    private val filterSortFlow = combine(
        _searchQuery,
        _filterMode,
        _sortMode
    ) { query, filter, sort ->
        FilterSortInputs(query = query, filter = filter, sort = sort)
    }

    val displayedShelfItems: StateFlow<List<FavoriteShelfItemUi>> = combine(
        categoryMangasFlow,
        repository.completedCountsByMangaFlow,
        repository.readingHistoryFlow,
        filterSortFlow
    ) { mangas, completedCounts, historyList, controls ->
        val historyMap = historyList.associateBy { it.manga.id }
        val enriched = mangas.map { manga ->
            val completed = completedCounts[manga.id] ?: 0
            val historyEntry = historyMap[manga.id]
            val knownTotal = manga.knownChaptersCount
            val unread = if (knownTotal > 0) {
                (knownTotal - completed).coerceAtLeast(0)
            } else {
                0
            }
            val progress = when {
                knownTotal > 0 && completed > 0 -> {
                    (completed.toFloat() / knownTotal.toFloat()).coerceIn(0f, 1f)
                }
                historyEntry != null -> historyEntry.progressFraction
                else -> null
            }
            FavoriteShelfItemUi(
                manga = manga,
                unreadCount = unread,
                newChaptersCount = manga.newChaptersDelta,
                completedCount = completed,
                progressFraction = progress,
                lastReadAt = historyEntry?.updatedAt ?: 0L
            )
        }

        val cleanQuery = controls.query.trim()
        val filtered = enriched.filter { item ->
            val matchesQuery = cleanQuery.isEmpty() ||
                item.manga.title.contains(cleanQuery, ignoreCase = true) ||
                item.manga.author.contains(cleanQuery, ignoreCase = true) ||
                item.manga.badgeText.contains(cleanQuery, ignoreCase = true) ||
                item.manga.tags.any { tag -> tag.name.contains(cleanQuery, ignoreCase = true) }

            val matchesFilter = when (controls.filter) {
                FavoritesFilterMode.ALL -> true
                FavoritesFilterMode.NEW_CHAPTERS -> item.newChaptersCount > 0
                FavoritesFilterMode.UNREAD -> item.unreadCount > 0 || item.newChaptersCount > 0
                FavoritesFilterMode.IN_PROGRESS -> {
                    val started = item.completedCount > 0 || item.lastReadAt > 0L
                    val notFinished = item.manga.knownChaptersCount == 0 ||
                        item.completedCount < item.manga.knownChaptersCount
                    started && notFinished
                }
                FavoritesFilterMode.CAUGHT_UP -> {
                    item.manga.knownChaptersCount > 0 &&
                        item.completedCount >= item.manga.knownChaptersCount &&
                        item.newChaptersCount == 0
                }
            }
            matchesQuery && matchesFilter
        }

        when (controls.sort) {
            FavoritesSortMode.ADDED_RECENT -> filtered
            FavoritesSortMode.LAST_READ -> filtered.sortedWith(
                compareByDescending<FavoriteShelfItemUi> { it.lastReadAt }
                    .thenBy { it.manga.title.lowercase() }
            )
            FavoritesSortMode.ALPHABETICAL -> filtered.sortedBy { it.manga.title.lowercase() }
            FavoritesSortMode.MOST_UNREAD -> filtered.sortedWith(
                compareByDescending<FavoriteShelfItemUi> { it.newChaptersCount }
                    .thenByDescending { it.unreadCount }
                    .thenBy { it.manga.title.lowercase() }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectFilterMode(mode: FavoritesFilterMode) {
        _filterMode.value = mode
    }

    fun selectSortMode(mode: FavoritesSortMode) {
        _sortMode.value = mode
    }

    fun clearActiveFilters() {
        _searchQuery.value = ""
        _filterMode.value = FavoritesFilterMode.ALL
    }

    fun checkUpdatesNow() {
        if (_isCheckingUpdates.value) return
        if (allFavoritesCount.value.isEmpty()) {
            _feedbackEvent.value = FavoritesFeedbackEvent.EmptyShelfForUpdates
            return
        }
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            _updateProgress.value = 0 to allFavoritesCount.value.size
            runCatching {
                repository.checkFavoritesUpdates { checked, total ->
                    _updateProgress.value = checked to total
                }
            }.onSuccess { summary ->
                _feedbackEvent.value = if (summary.totalNewChapters > 0) {
                    FavoritesFeedbackEvent.UpdatesFound(
                        totalNewChapters = summary.totalNewChapters,
                        mangasWithNewCount = summary.mangasWithNewCount
                    )
                } else {
                    FavoritesFeedbackEvent.NoUpdatesFound(summary.checkedCount)
                }
            }
            _isCheckingUpdates.value = false
            _updateProgress.value = null
        }
    }

    fun consumeFeedbackEvent() {
        _feedbackEvent.value = null
    }

    // Multi-selection & Batch Actions
    fun toggleSelectionMode() {
        if (_selectionMode.value) {
            clearSelection()
        } else {
            _selectionMode.value = true
        }
    }

    fun toggleMangaSelection(mangaId: String) {
        _selectedMangaIds.update { current ->
            val next = if (mangaId in current) current - mangaId else current + mangaId
            _selectionMode.value = next.isNotEmpty()
            next
        }
    }

    fun selectAllVisible() {
        val visibleIds = displayedShelfItems.value.map { it.manga.id }.toSet()
        if (visibleIds.isEmpty()) return
        _selectionMode.value = true
        _selectedMangaIds.value = if (_selectedMangaIds.value.containsAll(visibleIds)) {
            emptySet<String>().also { _selectionMode.value = false }
        } else {
            visibleIds
        }
    }

    fun clearSelection() {
        _selectedMangaIds.value = emptySet()
        _selectionMode.value = false
    }

    fun applyBatchCategories(targetCategoryIds: Set<Long>) {
        val ids = _selectedMangaIds.value
        val selectedMangas = allFavoritesCount.value.filter { it.id in ids }
        if (selectedMangas.isEmpty()) {
            clearSelection()
            return
        }
        viewModelScope.launch {
            repository.updateMangasCategoriesBatch(selectedMangas, targetCategoryIds)
            _feedbackEvent.value = if (targetCategoryIds.isEmpty()) {
                FavoritesFeedbackEvent.BatchRemovedSaved(selectedMangas.size)
            } else {
                FavoritesFeedbackEvent.BatchCategoriesSaved(selectedMangas.size)
            }
            clearSelection()
        }
    }

    fun markSelectedCaughtUp() {
        val ids = _selectedMangaIds.value
        val selectedMangas = allFavoritesCount.value.filter { it.id in ids }
        if (selectedMangas.isEmpty()) return
        viewModelScope.launch {
            repository.markMangasCaughtUpBatch(selectedMangas)
            _feedbackEvent.value = FavoritesFeedbackEvent.BatchCaughtUpSaved(selectedMangas.size)
            clearSelection()
        }
    }

    fun removeSelectedFromShelf() {
        applyBatchCategories(emptySet())
    }

    // Category CRUD
    fun createCategory(name: String) {
        viewModelScope.launch {
            val newId = repository.createCategory(name)
            if (newId > 0 && !_selectionMode.value) {
                _selectedCategoryId.value = newId
            }
        }
    }

    fun renameCategory(categoryId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameCategory(categoryId, newName)
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            if (_selectedCategoryId.value == categoryId) {
                _selectedCategoryId.value = null
            }
            repository.deleteCategory(categoryId)
        }
    }

    companion object {
        fun factory(repository: MangaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FavoritesViewModel(repository) as T
                }
            }
    }
}
