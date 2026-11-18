package org.koitharu.kotatsu.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.AppThemeMode
import org.koitharu.kotatsu.domain.model.PageBookmarkItem
import org.koitharu.kotatsu.domain.model.ReaderMode

class BookmarksViewModel(
    private val repository: MangaRepository
) : ViewModel() {

    val bookmarks: StateFlow<List<PageBookmarkItem>> = repository.allBookmarksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preferences: StateFlow<AppPreferences> = repository.preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    fun removeBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.removeBookmarkById(bookmarkId)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.preferencesRepository.setThemeMode(mode)
        }
    }

    fun setDefaultReaderMode(mode: ReaderMode) {
        viewModelScope.launch {
            repository.preferencesRepository.setDefaultReaderMode(mode)
        }
    }

    fun setDataSaver(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setDataSaver(enabled)
        }
    }

    companion object {
        fun factory(repository: MangaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BookmarksViewModel(repository) as T
                }
            }
    }
}
