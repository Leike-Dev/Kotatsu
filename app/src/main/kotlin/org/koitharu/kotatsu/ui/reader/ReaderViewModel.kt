package org.koitharu.kotatsu.ui.reader

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.ChapterItem
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.PageItem
import org.koitharu.kotatsu.domain.model.ReaderBackground
import org.koitharu.kotatsu.domain.model.ReaderMode
import org.koitharu.kotatsu.domain.model.ReaderScaleMode

/**
 * Emits -1 for previous page (Volume Up) and +1 for next page (Volume Down).
 */
val LocalVolumeKeyEvents = staticCompositionLocalOf<SharedFlow<Int>> {
    MutableSharedFlow()
}

val LocalSetVolumeKeyCapture = staticCompositionLocalOf<(Boolean) -> Unit> {
    {}
}

data class ReaderUiState(
    val manga: MangaItem? = null,
    val chapters: List<ChapterItem> = emptyList(),
    val currentChapter: ChapterItem? = null,
    val pages: List<PageItem> = emptyList(),
    val currentPageIndex: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val previousChapter: ChapterItem?
        get() {
            val idx = chapters.indexOfFirst { it.id == currentChapter?.id }
            return if (idx > 0) chapters[idx - 1] else null
        }

    val nextChapter: ChapterItem?
        get() {
            val idx = chapters.indexOfFirst { it.id == currentChapter?.id }
            return if (idx in 0 until chapters.lastIndex) chapters[idx + 1] else null
        }
}

class ReaderViewModel(
    private val mangaId: String,
    private val sourceType: MangaSourceType,
    initialChapterId: String,
    initialPage: Int,
    private val repository: MangaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState(currentPageIndex = initialPage))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<AppPreferences> = repository.preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    private val currentChapterIdFlow = MutableStateFlow(initialChapterId)

    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarkedPages: StateFlow<Set<Int>> = currentChapterIdFlow
        .flatMapLatest { chId ->
            repository.observeBookmarkedPages(mangaId, chId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        initializeReader(initialChapterId, initialPage)
    }

    private fun initializeReader(chapterId: String, startPage: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val prefs = repository.preferencesRepository.preferencesFlow.first()
            runCatching {
                val manga = repository.getMangaDetails(mangaId, sourceType)
                val chapters = repository.getChapters(
                    manga = manga,
                    language = prefs.preferredLanguage,
                    recordInspection = true
                ).ifEmpty {
                    repository.getChapters(
                        manga = manga,
                        language = LanguageFilter.ALL,
                        recordInspection = true
                    )
                }
                val activeChapter = chapters.firstOrNull { it.id == chapterId }
                    ?: chapters.firstOrNull()
                    ?: ChapterItem(
                        id = chapterId,
                        mangaId = mangaId,
                        number = 1f,
                        numberLabel = "1",
                        title = "Capítulo",
                        language = "",
                        scanlator = "",
                        publishedAt = 0L
                    )
                val pages = repository.getChapterPages(manga, activeChapter.id)
                Triple(manga, chapters, activeChapter to pages)
            }.onSuccess { (manga, chapters, chapterAndPages) ->
                val (activeChapter, pages) = chapterAndPages
                val clampedPage = if (pages.isEmpty()) 0 else startPage.coerceIn(0, pages.lastIndex)
                currentChapterIdFlow.value = activeChapter.id
                _uiState.update {
                    it.copy(
                        manga = manga,
                        chapters = chapters,
                        currentChapter = activeChapter,
                        pages = pages,
                        currentPageIndex = clampedPage,
                        isLoading = false,
                        errorMessage = if (pages.isEmpty()) "Nenhuma página retornada para este capítulo." else null
                    )
                }
                if (pages.isNotEmpty()) {
                    repository.saveReadingProgress(
                        manga = manga,
                        chapterId = activeChapter.id,
                        chapterTitle = activeChapter.displayTitle,
                        currentPage = clampedPage,
                        totalPages = pages.size
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Falha ao carregar páginas do capítulo."
                    )
                }
            }
        }
    }

    fun retryLoad() {
        val chId = currentChapterIdFlow.value
        val page = _uiState.value.currentPageIndex
        initializeReader(chId, page)
    }

    fun switchToChapter(target: ChapterItem) {
        currentChapterIdFlow.value = target.id
        initializeReader(target.id, 0)
    }

    fun onPageChanged(newPageIndex: Int) {
        val snapshot = _uiState.value
        val manga = snapshot.manga ?: return
        val chapter = snapshot.currentChapter ?: return
        val total = snapshot.pages.size
        if (total <= 0) return
        val safePage = newPageIndex.coerceIn(0, total - 1)
        if (snapshot.currentPageIndex == safePage) return

        _uiState.update { it.copy(currentPageIndex = safePage) }
        viewModelScope.launch {
            repository.saveReadingProgress(
                manga = manga,
                chapterId = chapter.id,
                chapterTitle = chapter.displayTitle,
                currentPage = safePage,
                totalPages = total
            )
        }
    }

    fun toggleBookmarkOnCurrentPage() {
        val snapshot = _uiState.value
        val manga = snapshot.manga ?: return
        val chapter = snapshot.currentChapter ?: return
        val pageIdx = snapshot.currentPageIndex
        val pageUrl = snapshot.pages.getOrNull(pageIdx)?.imageUrl ?: manga.coverUrl
        val isMarked = pageIdx in bookmarkedPages.value

        viewModelScope.launch {
            repository.togglePageBookmark(
                manga = manga,
                chapterId = chapter.id,
                chapterTitle = chapter.displayTitle,
                pageIndex = pageIdx,
                totalPages = snapshot.pages.size,
                previewUrl = pageUrl,
                currentlyBookmarked = isMarked
            )
        }
    }

    fun setReaderMode(mode: ReaderMode) {
        viewModelScope.launch {
            repository.preferencesRepository.setDefaultReaderMode(mode)
        }
    }

    fun setReaderScaleMode(scaleMode: ReaderScaleMode) {
        viewModelScope.launch {
            repository.preferencesRepository.setReaderScaleMode(scaleMode)
        }
    }

    fun setReaderBackground(background: ReaderBackground) {
        viewModelScope.launch {
            repository.preferencesRepository.setReaderBackground(background)
        }
    }

    fun setBrightnessDim(dim: Float) {
        viewModelScope.launch {
            repository.preferencesRepository.setReaderBrightnessDim(dim)
        }
    }

    fun setGrayscale(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setReaderGrayscale(enabled)
        }
    }

    fun setInvertColors(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setReaderInvertColors(enabled)
        }
    }

    fun setShowThumbnails(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setShowReaderThumbnails(enabled)
        }
    }

    fun setShowMiniInfo(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setShowReaderMiniInfo(enabled)
        }
    }

    fun setWebtoonSeamlessGap(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setWebtoonSeamlessGap(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setKeepScreenOn(enabled)
        }
    }

    fun setVolumeKeysNavigation(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setVolumeKeysNavigation(enabled)
        }
    }

    fun setPageTransitions(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setPageTransitions(enabled)
        }
    }

    companion object {
        fun factory(
            mangaId: String,
            sourceType: MangaSourceType,
            initialChapterId: String,
            initialPage: Int,
            repository: MangaRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(
                    mangaId = mangaId,
                    sourceType = sourceType,
                    initialChapterId = initialChapterId,
                    initialPage = initialPage,
                    repository = repository
                ) as T
            }
        }
    }
}
