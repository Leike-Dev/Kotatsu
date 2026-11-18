package org.koitharu.kotatsu.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.ReadingHistoryItem

class HistoryViewModel(
    private val repository: MangaRepository
) : ViewModel() {

    val historyItems: StateFlow<List<ReadingHistoryItem>> = repository.readingHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeEntry(mangaId: String) {
        viewModelScope.launch {
            repository.deleteHistoryItem(mangaId)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    companion object {
        fun factory(repository: MangaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(repository) as T
                }
            }
    }
}
