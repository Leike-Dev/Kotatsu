package org.koitharu.kotatsu.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.AppColorPalette
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.AppThemeMode
import org.koitharu.kotatsu.domain.model.ChapterDisplayMode
import org.koitharu.kotatsu.domain.model.CoverCornerStyle
import org.koitharu.kotatsu.domain.model.CustomMangaSource
import org.koitharu.kotatsu.domain.model.DefaultStartTab
import org.koitharu.kotatsu.domain.model.DohProvider
import org.koitharu.kotatsu.domain.model.ExploreLayoutMode
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.LibraryStats
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.NavLabelBehavior
import org.koitharu.kotatsu.domain.model.ReaderBackground
import org.koitharu.kotatsu.domain.model.ReaderMode
import org.koitharu.kotatsu.domain.model.ReaderScaleMode
import org.koitharu.kotatsu.domain.model.SourceHealthCheckResult
import org.koitharu.kotatsu.domain.model.SourceHealthState
import org.koitharu.kotatsu.widget.KotatsuWidgetManager

enum class SettingsCategory {
    APPEARANCE,
    READER,
    EXPLORE,
    SOURCES,
    NETWORK,
    SECURITY,
    BACKUP
}

class SettingsViewModel(
    private val repository: MangaRepository
) : ViewModel() {

    private val prefsRepo = repository.preferencesRepository

    val preferences: StateFlow<AppPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    val customSources: StateFlow<List<CustomMangaSource>> = repository.customSourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val localMangas: StateFlow<List<MangaItem>> = repository.localMangasFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryStats: StateFlow<LibraryStats> = repository.libraryStatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryStats())

    private val _selectedCategory = MutableStateFlow(SettingsCategory.APPEARANCE)
    val selectedCategory: StateFlow<SettingsCategory> = _selectedCategory.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _isImportingCbz = MutableStateFlow(false)
    val isImportingCbz: StateFlow<Boolean> = _isImportingCbz.asStateFlow()

    private val _sourceTestResults = MutableStateFlow<Map<Long, SourceHealthCheckResult>>(emptyMap())
    val sourceTestResults: StateFlow<Map<Long, SourceHealthCheckResult>> = _sourceTestResults.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSizeBytes.value = repository.computeCacheSizeBytes()
        }
    }

    fun selectCategory(category: SettingsCategory) {
        _selectedCategory.value = category
    }

    fun postSnackbarMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    // Appearance, Palettes, Covers & Navigation
    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    fun setAmoledDark(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAmoledDark(enabled) }
    fun setDynamicColors(enabled: Boolean) = viewModelScope.launch { prefsRepo.setDynamicColors(enabled) }
    fun setColorPalette(palette: AppColorPalette) = viewModelScope.launch { prefsRepo.setColorPalette(palette) }
    fun setCoverCornerStyle(style: CoverCornerStyle) = viewModelScope.launch { prefsRepo.setCoverCornerStyle(style) }
    fun setNavLabelBehavior(behavior: NavLabelBehavior) = viewModelScope.launch { prefsRepo.setNavLabelBehavior(behavior) }
    fun setDefaultStartTab(tab: DefaultStartTab) = viewModelScope.launch { prefsRepo.setDefaultStartTab(tab) }
    fun setShowSourceBadges(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowSourceBadges(enabled) }
    fun setShowUnreadBadgesOnCovers(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowUnreadBadgesOnCovers(enabled) }
    fun setShowProgressOnCovers(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowProgressOnCovers(enabled) }

    fun syncWidgetsNow(context: Context) {
        viewModelScope.launch {
            runCatching { KotatsuWidgetManager.refreshAllWidgetsNow(context) }
            _snackbarMessage.value = "Widgets sincronizados com sua biblioteca!"
        }
    }

    // Reader
    fun setDefaultReaderMode(mode: ReaderMode) = viewModelScope.launch { prefsRepo.setDefaultReaderMode(mode) }
    fun setReaderScaleMode(mode: ReaderScaleMode) = viewModelScope.launch { prefsRepo.setReaderScaleMode(mode) }
    fun setReaderBackground(bg: ReaderBackground) = viewModelScope.launch { prefsRepo.setReaderBackground(bg) }
    fun setReaderBrightnessDim(dim: Float) = viewModelScope.launch { prefsRepo.setReaderBrightnessDim(dim) }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { prefsRepo.setKeepScreenOn(enabled) }
    fun setVolumeKeysNavigation(enabled: Boolean) = viewModelScope.launch { prefsRepo.setVolumeKeysNavigation(enabled) }
    fun setPageTransitions(enabled: Boolean) = viewModelScope.launch { prefsRepo.setPageTransitions(enabled) }
    fun setShowReaderThumbnails(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowReaderThumbnails(enabled) }
    fun setShowReaderMiniInfo(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowReaderMiniInfo(enabled) }
    fun setWebtoonSeamlessGap(enabled: Boolean) = viewModelScope.launch { prefsRepo.setWebtoonSeamlessGap(enabled) }
    fun setReaderGrayscale(enabled: Boolean) = viewModelScope.launch { prefsRepo.setReaderGrayscale(enabled) }
    fun setReaderInvertColors(enabled: Boolean) = viewModelScope.launch { prefsRepo.setReaderInvertColors(enabled) }
    fun setDataSaver(enabled: Boolean) = viewModelScope.launch { prefsRepo.setDataSaver(enabled) }

    // Explore & Details
    fun setExploreLayoutMode(mode: ExploreLayoutMode) = viewModelScope.launch { prefsRepo.setExploreLayoutMode(mode) }
    fun setShowExploreSpotlight(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowExploreSpotlight(enabled) }
    fun setShowExploreSourcesBar(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowExploreSourcesBar(enabled) }
    fun setShowExploreGenres(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowExploreGenres(enabled) }
    fun setShowExploreLocalSection(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowExploreLocalSection(enabled) }
    fun setEnableKitsuCatalog(enabled: Boolean) = viewModelScope.launch { prefsRepo.setEnableKitsuCatalog(enabled) }
    fun setHideNsfw(enabled: Boolean) = viewModelScope.launch { prefsRepo.setHideNsfw(enabled) }
    fun setPreferredLanguage(lang: LanguageFilter) = viewModelScope.launch { prefsRepo.setPreferredLanguage(lang) }
    fun setChapterDisplayMode(mode: ChapterDisplayMode) = viewModelScope.launch { prefsRepo.setChapterDisplayMode(mode) }

    // Custom Sources & Connectivity Testing
    fun addCustomSource(name: String, url: String, template: String) {
        viewModelScope.launch {
            repository.addCustomSource(name, url, template)
            _snackbarMessage.value = "Site \"$name\" adicionado às fontes de busca!"
        }
    }

    fun updateCustomSource(id: Long, name: String, url: String, template: String) {
        viewModelScope.launch {
            repository.updateCustomSource(id, name, url, template)
            _snackbarMessage.value = "Site \"$name\" atualizado!"
        }
    }

    fun toggleCustomSource(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setCustomSourceEnabled(id, enabled)
        }
    }

    fun deleteCustomSource(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomSource(id)
            _snackbarMessage.value = "Fonte removida."
        }
    }

    fun testCustomSource(source: CustomMangaSource) {
        viewModelScope.launch {
            _sourceTestResults.update {
                it + (source.id to SourceHealthCheckResult(state = SourceHealthState.TESTING))
            }
            val result = repository.testSourceConnection(
                sourceId = source.id,
                name = source.name,
                baseUrl = source.baseUrl
            )
            _sourceTestResults.update { it + (source.id to result) }
            val summary = result.message.ifBlank { "HTTP ${result.httpCode} • ${result.latencyMs} ms" }
            _snackbarMessage.value = "${source.name}: $summary"
        }
    }

    // Network & Anti-Adblock
    fun setAdShieldEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAdShieldEnabled(enabled) }
    fun setAntiAdblockBypass(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAntiAdblockBypass(enabled) }
    fun setBlockPopupsRedirects(enabled: Boolean) = viewModelScope.launch { prefsRepo.setBlockPopupsRedirects(enabled) }
    fun setDohProvider(provider: DohProvider) = viewModelScope.launch { prefsRepo.setDohProvider(provider) }
    fun setCustomUserAgent(ua: String) = viewModelScope.launch {
        prefsRepo.setCustomUserAgent(ua)
        _snackbarMessage.value = "User-Agent atualizado."
    }

    // Security & Privacy
    fun configureAppLock(enabled: Boolean, pin: String) = viewModelScope.launch {
        prefsRepo.configureAppLock(enabled, pin)
        _snackbarMessage.value = if (enabled) "Bloqueio por PIN ativado!" else "Bloqueio por PIN desativado."
    }

    fun setAppLockSystemCredential(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setAppLockSystemCredential(enabled)
    }

    fun setSecureScreenEnabled(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setSecureScreenEnabled(enabled)
    }

    fun setIncognitoMode(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setIncognitoMode(enabled)
    }

    // Local CBZ Import & Management (Moved from Explore to Settings)
    fun importCbzFile(uri: Uri, onImported: ((MangaItem) -> Unit)? = null) {
        viewModelScope.launch {
            _isImportingCbz.value = true
            runCatching { repository.importLocalCbz(uri) }
                .onSuccess { imported ->
                    _isImportingCbz.value = false
                    _snackbarMessage.value = "Arquivo \"${imported.title}\" importado com sucesso!"
                    onImported?.invoke(imported)
                }
                .onFailure { err ->
                    _isImportingCbz.value = false
                    _snackbarMessage.value = err.localizedMessage ?: "Falha ao importar arquivo CBZ/ZIP."
                }
        }
    }

    fun deleteLocalManga(manga: MangaItem) {
        viewModelScope.launch {
            repository.deleteLocalManga(manga)
            _snackbarMessage.value = "\"${manga.title}\" removido da biblioteca local."
        }
    }

    // Backup & Maintenance
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.exportBackup(uri) }
                .onSuccess { count ->
                    _snackbarMessage.value = "Backup exportado com sucesso ($count registros)!"
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Falha ao exportar backup."
                }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.restoreBackup(uri) }
                .onSuccess { count ->
                    _snackbarMessage.value = "Backup restaurado com sucesso ($count registros)!"
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Arquivo de backup inválido."
                }
        }
    }

    fun clearMemoryCaches() {
        viewModelScope.launch {
            repository.clearAllTemporaryCaches()
            _cacheSizeBytes.value = repository.computeCacheSizeBytes()
            _snackbarMessage.value = "Cache de imagens e catálogos limpo com sucesso!"
        }
    }

    companion object {
        fun factory(repository: MangaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(repository) as T
                }
            }
    }
}
