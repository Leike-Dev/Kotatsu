package org.koitharu.kotatsu.ui.explore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.CatalogSort
import org.koitharu.kotatsu.domain.model.CustomMangaSource
import org.koitharu.kotatsu.domain.model.ExploreLayoutMode
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaTag

data class ExploreUiState(
    val searchQuery: String = "",
    val selectedSourceKey: String = MangaRepository.SOURCE_FILTER_ALL,
    val selectedLanguage: LanguageFilter = LanguageFilter.ALL,
    val selectedSort: CatalogSort = CatalogSort.POPULAR,
    val availableTags: List<MangaTag> = emptyList(),
    val selectedTagId: String? = null,
    val onlineMangas: List<MangaItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isImportingCbz: Boolean = false,
    val canLoadMore: Boolean = true,
    val errorMessage: String? = null,
    val statusSnackbarMessage: String? = null
)

class ExploreViewModel(
    private val repository: MangaRepository,
    initialQuery: String = "",
    initialTagId: String = ""
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExploreUiState(
            searchQuery = initialQuery,
            selectedTagId = initialTagId.ifBlank { null }
        )
    )
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<AppPreferences> = repository.preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    val localMangas: StateFlow<List<MangaItem>> = repository.localMangasFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customSources: StateFlow<List<CustomMangaSource>> = repository.customSourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchDebounceJob: Job? = null
    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            val prefs = repository.preferencesRepository.preferencesFlow.first()
            _uiState.update { it.copy(selectedLanguage = prefs.preferredLanguage) }
            loadTags()
            refreshCatalog()
        }
    }

    fun applyExternalFilter(query: String, tagId: String) {
        when {
            tagId.isNotBlank() && tagId != _uiState.value.selectedTagId -> {
                _uiState.update {
                    it.copy(
                        selectedTagId = tagId,
                        searchQuery = ""
                    )
                }
                refreshCatalog()
            }
            query.isNotBlank() && query != _uiState.value.searchQuery -> {
                _uiState.update {
                    it.copy(
                        searchQuery = query,
                        selectedTagId = null
                    )
                }
                refreshCatalog()
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            runCatching { repository.getPopularTags() }
                .onSuccess { tags ->
                    _uiState.update { it.copy(availableTags = tags) }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(380)
            refreshCatalog()
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        searchDebounceJob?.cancel()
        refreshCatalog()
    }

    fun onSourceSelected(sourceKey: String) {
        if (_uiState.value.selectedSourceKey == sourceKey) return
        _uiState.update { it.copy(selectedSourceKey = sourceKey) }
        refreshCatalog()
    }

    fun onLanguageSelected(language: LanguageFilter) {
        if (_uiState.value.selectedLanguage == language) return
        _uiState.update { it.copy(selectedLanguage = language) }
        viewModelScope.launch {
            repository.preferencesRepository.setPreferredLanguage(language)
        }
        refreshCatalog()
    }

    fun onSortSelected(sort: CatalogSort) {
        if (_uiState.value.selectedSort == sort) return
        _uiState.update { it.copy(selectedSort = sort) }
        refreshCatalog()
    }

    fun onTagToggled(tagId: String) {
        val nextTag = if (_uiState.value.selectedTagId == tagId) null else tagId
        _uiState.update { it.copy(selectedTagId = nextTag) }
        refreshCatalog()
    }

    fun addCustomSource(name: String, baseUrl: String, searchTemplate: String) {
        viewModelScope.launch {
            val id = repository.addCustomSource(name, baseUrl, searchTemplate)
            if (id > 0) {
                _uiState.update {
                    it.copy(
                        selectedSourceKey = "${MangaRepository.SOURCE_FILTER_CUSTOM_PREFIX}$id",
                        statusSnackbarMessage = "Site \"$name\" adicionado e selecionado!"
                    )
                }
                refreshCatalog()
            }
        }
    }

    // Explore UI Customization shortcuts
    fun setExploreLayoutMode(mode: ExploreLayoutMode) {
        viewModelScope.launch { repository.preferencesRepository.setExploreLayoutMode(mode) }
    }

    fun setShowSpotlight(enabled: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setShowExploreSpotlight(enabled) }
    }

    fun setShowSourcesBar(enabled: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setShowExploreSourcesBar(enabled) }
    }

    fun setShowGenres(enabled: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setShowExploreGenres(enabled) }
    }

    fun setShowLocalSection(enabled: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setShowExploreLocalSection(enabled) }
    }

    fun setShowSourceBadges(enabled: Boolean) {
        viewModelScope.launch { repository.preferencesRepository.setShowSourceBadges(enabled) }
    }

    fun refreshCatalog() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val snapshot = _uiState.value
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.searchOnlineCatalog(
                    query = snapshot.searchQuery,
                    language = snapshot.selectedLanguage,
                    sort = snapshot.selectedSort,
                    tagId = snapshot.selectedTagId,
                    sourceFilterKey = snapshot.selectedSourceKey,
                    offset = 0
                )
            }.onSuccess { results ->
                _uiState.update {
                    it.copy(
                        onlineMangas = results,
                        isLoading = false,
                        canLoadMore = results.size >= 16 &&
                            !snapshot.selectedSourceKey.startsWith(MangaRepository.SOURCE_FILTER_CUSTOM_PREFIX),
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Não foi possível carregar o catálogo online."
                    )
                }
            }
        }
    }

    fun loadNextPage() {
        val snapshot = _uiState.value
        if (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.canLoadMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            runCatching {
                repository.searchOnlineCatalog(
                    query = snapshot.searchQuery,
                    language = snapshot.selectedLanguage,
                    sort = snapshot.selectedSort,
                    tagId = snapshot.selectedTagId,
                    sourceFilterKey = snapshot.selectedSourceKey,
                    offset = snapshot.onlineMangas.size
                )
            }.onSuccess { nextBatch ->
                _uiState.update { state ->
                    val merged = (state.onlineMangas + nextBatch).distinctBy { it.id }
                    state.copy(
                        onlineMangas = merged,
                        isLoadingMore = false,
                        canLoadMore = nextBatch.size >= 16
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun importCbzFile(uri: Uri, onImported: (MangaItem) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingCbz = true, statusSnackbarMessage = null) }
            runCatching {
                repository.importLocalCbz(uri)
            }.onSuccess { imported ->
                _uiState.update {
                    it.copy(
                        isImportingCbz = false,
                        statusSnackbarMessage = "\"${imported.title}\" importado com sucesso!"
                    )
                }
                onImported(imported)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isImportingCbz = false,
                        statusSnackbarMessage = err.localizedMessage ?: "Falha ao importar arquivo CBZ."
                    )
                }
            }
        }
    }

    fun deleteLocalManga(manga: MangaItem) {
        viewModelScope.launch {
            repository.deleteLocalManga(manga)
            _uiState.update {
                it.copy(statusSnackbarMessage = "\"${manga.title}\" removido da biblioteca local.")
            }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(statusSnackbarMessage = null) }
    }

    companion object {
        fun factory(
            repository: MangaRepository,
            initialQuery: String = "",
            initialTagId: String = ""
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ExploreViewModel(repository, initialQuery, initialTagId) as T
                }
            }
    }
}
