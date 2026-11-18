package org.koitharu.kotatsu.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.data.local.db.ReadingHistoryEntity
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.ChapterDisplayMode
import org.koitharu.kotatsu.domain.model.ChapterItem
import org.koitharu.kotatsu.domain.model.FavoriteCategory
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType

data class MangaDetailsUiState(
    val manga: MangaItem? = null,
    val chapters: List<ChapterItem> = emptyList(),
    val selectedLanguage: LanguageFilter = LanguageFilter.ALL,
    val sortDescending: Boolean = true,
    val unreadOnly: Boolean = false,
    val chapterSearchQuery: String = "",
    val isLoading: Boolean = true,
    val isChaptersLoading: Boolean = true,
    val errorMessage: String? = null
) {
    fun filteredChapters(completedIds: Set<String>): List<ChapterItem> {
        val base = if (sortDescending) chapters.asReversed() else chapters
        return base.filter { ch ->
            val passUnread = !unreadOnly || (ch.id !in completedIds)
            val passQuery = chapterSearchQuery.isBlank() ||
                ch.displayTitle.contains(chapterSearchQuery.trim(), ignoreCase = true) ||
                ch.scanlator.contains(chapterSearchQuery.trim(), ignoreCase = true)
            passUnread && passQuery
        }
    }
}

class MangaDetailsViewModel(
    private val mangaId: String,
    private val sourceType: MangaSourceType,
    private val repository: MangaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MangaDetailsUiState())
    val uiState: StateFlow<MangaDetailsUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<AppPreferences> = repository.preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    val categories: StateFlow<List<FavoriteCategory>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val assignedCategoryIds: StateFlow<Set<Long>> = repository.observeCategoryIdsForManga(mangaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val historyEntry: StateFlow<ReadingHistoryEntity?> = repository.observeHistoryForManga(mangaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val completedChapterIds: StateFlow<Set<String>> = repository.observeCompletedChapters(mangaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isChaptersLoading = true, errorMessage = null) }
            val prefLang = repository.preferencesRepository.preferencesFlow.first().preferredLanguage
            runCatching {
                repository.getMangaDetails(mangaId, sourceType)
            }.onSuccess { item ->
                _uiState.update {
                    it.copy(
                        manga = item,
                        selectedLanguage = prefLang,
                        isLoading = false
                    )
                }
                loadChapters(item, prefLang, forceRefresh = false)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isChaptersLoading = false,
                        errorMessage = err.localizedMessage ?: "Falha ao carregar detalhes da obra."
                    )
                }
            }
        }
    }

    fun onLanguageSelected(language: LanguageFilter) {
        val currentManga = _uiState.value.manga ?: return
        if (_uiState.value.selectedLanguage == language) return
        _uiState.update { it.copy(selectedLanguage = language) }
        viewModelScope.launch {
            loadChapters(currentManga, language, forceRefresh = false)
        }
    }

    fun toggleChapterOrder() {
        _uiState.update { it.copy(sortDescending = !it.sortDescending) }
    }

    fun toggleUnreadOnly() {
        _uiState.update { it.copy(unreadOnly = !it.unreadOnly) }
    }

    fun onChapterSearchChanged(query: String) {
        _uiState.update { it.copy(chapterSearchQuery = query) }
    }

    fun setChapterDisplayMode(mode: ChapterDisplayMode) {
        viewModelScope.launch {
            repository.preferencesRepository.setChapterDisplayMode(mode)
        }
    }

    fun toggleChapterRead(chapterId: String, currentlyRead: Boolean) {
        val currentManga = _uiState.value.manga ?: return
        viewModelScope.launch {
            repository.toggleChapterCompleted(currentManga, chapterId, !currentlyRead)
        }
    }

    private suspend fun loadChapters(
        manga: MangaItem,
        language: LanguageFilter,
        forceRefresh: Boolean
    ) {
        _uiState.update { it.copy(isChaptersLoading = true) }
        runCatching {
            repository.getChapters(manga, language, forceRefresh)
        }.onSuccess { list ->
            _uiState.update {
                it.copy(
                    chapters = list,
                    isChaptersLoading = false
                )
            }
        }.onFailure {
            _uiState.update { it.copy(isChaptersLoading = false) }
        }
    }

    fun saveFavoriteCategories(selectedIds: Set<Long>) {
        val currentManga = _uiState.value.manga ?: return
        viewModelScope.launch {
            repository.updateMangaCategories(currentManga, selectedIds)
        }
    }

    fun createNewCategory(name: String) {
        val currentManga = _uiState.value.manga
        viewModelScope.launch {
            val createdId = repository.createCategory(name)
            if (createdId > 0 && currentManga != null) {
                val updated = assignedCategoryIds.value + createdId
                repository.updateMangaCategories(currentManga, updated)
            }
        }
    }

    companion object {
        fun factory(
            mangaId: String,
            sourceType: MangaSourceType,
            repository: MangaRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MangaDetailsViewModel(mangaId, sourceType, repository) as T
            }
        }
    }
}
