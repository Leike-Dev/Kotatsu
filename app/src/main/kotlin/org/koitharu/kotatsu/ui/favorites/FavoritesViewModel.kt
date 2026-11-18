package org.koitharu.kotatsu.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.FavoriteCategory
import org.koitharu.kotatsu.domain.model.MangaItem

class FavoritesViewModel(
    private val repository: MangaRepository
) : ViewModel() {

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    val categories: StateFlow<List<FavoriteCategory>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allFavoritesCount: StateFlow<List<MangaItem>> = repository.allFavoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val displayedMangas: StateFlow<List<MangaItem>> = _selectedCategoryId
        .flatMapLatest { catId ->
            repository.observeFavoritesByCategory(catId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            val newId = repository.createCategory(name)
            if (newId > 0) {
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
