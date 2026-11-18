package org.koitharu.kotatsu.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.data.remote.BuiltInCatalogKind
import org.koitharu.kotatsu.data.remote.CatalogDirectoryEntry
import org.koitharu.kotatsu.data.remote.SourceCatalogDirectory
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.CustomMangaSource
import org.koitharu.kotatsu.domain.model.SourceHealthCheckResult
import org.koitharu.kotatsu.domain.model.SourceHealthState

enum class CatalogRegionFilter {
    ALL,
    ACTIVE_ONLY,
    PT_BR,
    EN,
    ES,
    GLOBAL_AND_OPDS
}

data class ManagedSourceItem(
    val key: String,
    val name: String,
    val baseUrl: String,
    val searchTemplate: String,
    val languageCode: String,
    val engineLabel: String,
    val description: String,
    val faviconUrl: String,
    val isEnabled: Boolean,
    val isLockedNative: Boolean = false,
    val isBuiltInKitsu: Boolean = false,
    val requiresServerConfig: Boolean = false,
    val savedCustomSource: CustomMangaSource? = null,
    val probeUrlOverride: String? = null
)

class SourcesCatalogViewModel(
    private val repository: MangaRepository
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = repository.preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    val customSources: StateFlow<List<CustomMangaSource>> = repository.customSourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _regionFilter = MutableStateFlow(CatalogRegionFilter.ALL)
    val regionFilter: StateFlow<CatalogRegionFilter> = _regionFilter.asStateFlow()

    private val _healthResults = MutableStateFlow<Map<String, SourceHealthCheckResult>>(emptyMap())
    val healthResults: StateFlow<Map<String, SourceHealthCheckResult>> = _healthResults.asStateFlow()

    private val _isBatchTesting = MutableStateFlow(false)
    val isBatchTesting: StateFlow<Boolean> = _isBatchTesting.asStateFlow()

    private val _batchProgress = MutableStateFlow(0 to 0)
    val batchProgress: StateFlow<Pair<Int, Int>> = _batchProgress.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onRegionFilterSelected(filter: CatalogRegionFilter) {
        _regionFilter.value = filter
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun buildUnifiedList(
        prefs: AppPreferences,
        savedSources: List<CustomMangaSource>,
        healthMap: Map<String, SourceHealthCheckResult>
    ): List<ManagedSourceItem> {
        val savedByNormalizedOrigin = savedSources.associateBy {
            SourceCatalogDirectory.normalizeOrigin(it.baseUrl).lowercase()
        }
        val matchedSavedIds = mutableSetOf<Long>()

        val catalogMapped = SourceCatalogDirectory.entries.map { entry ->
            val normOrigin = SourceCatalogDirectory.normalizeOrigin(entry.baseUrl).lowercase()
            val matchingSaved = savedByNormalizedOrigin[normOrigin]?.also { matchedSavedIds.add(it.id) }
            val key = matchingSaved?.let { "custom_${it.id}" } ?: entry.catalogId
            val healthFavicon = healthMap[key]?.resolvedFaviconUrl.orEmpty()

            when (entry.kind) {
                BuiltInCatalogKind.NATIVE_MANGADEX -> ManagedSourceItem(
                    key = entry.catalogId,
                    name = entry.name,
                    baseUrl = entry.baseUrl,
                    searchTemplate = entry.searchUrlTemplate,
                    languageCode = entry.languageCode,
                    engineLabel = entry.engineLabel,
                    description = entry.description,
                    faviconUrl = healthFavicon.ifBlank { entry.defaultFaviconUrl },
                    isEnabled = true,
                    isLockedNative = true,
                    probeUrlOverride = "https://api.mangadex.org/manga?limit=1"
                )
                BuiltInCatalogKind.NATIVE_KITSU -> ManagedSourceItem(
                    key = entry.catalogId,
                    name = entry.name,
                    baseUrl = entry.baseUrl,
                    searchTemplate = entry.searchUrlTemplate,
                    languageCode = entry.languageCode,
                    engineLabel = entry.engineLabel,
                    description = entry.description,
                    faviconUrl = healthFavicon.ifBlank { entry.defaultFaviconUrl },
                    isEnabled = prefs.enableKitsuCatalog,
                    isBuiltInKitsu = true,
                    probeUrlOverride = "https://kitsu.io/api/edge/manga?page[limit]=1"
                )
                BuiltInCatalogKind.OPDS_SERVER,
                BuiltInCatalogKind.CATALOG_TEMPLATE -> ManagedSourceItem(
                    key = key,
                    name = matchingSaved?.name ?: entry.name,
                    baseUrl = matchingSaved?.baseUrl ?: entry.baseUrl,
                    searchTemplate = matchingSaved?.searchUrlTemplate ?: entry.searchUrlTemplate,
                    languageCode = entry.languageCode,
                    engineLabel = entry.engineLabel,
                    description = entry.description,
                    faviconUrl = healthFavicon
                        .ifBlank { matchingSaved?.faviconUrl.orEmpty() }
                        .ifBlank { entry.defaultFaviconUrl },
                    isEnabled = matchingSaved?.enabled == true,
                    requiresServerConfig = entry.kind == BuiltInCatalogKind.OPDS_SERVER && matchingSaved == null,
                    savedCustomSource = matchingSaved
                )
            }
        }

        // User's own custom websites not part of built-in directory
        val extraCustomItems = savedSources
            .filter { it.id !in matchedSavedIds }
            .map { custom ->
                val key = "custom_${custom.id}"
                val healthFavicon = healthMap[key]?.resolvedFaviconUrl.orEmpty()
                ManagedSourceItem(
                    key = key,
                    name = custom.name,
                    baseUrl = custom.baseUrl,
                    searchTemplate = custom.searchUrlTemplate,
                    languageCode = custom.languageCode.ifBlank { "PERSONALIZADO" },
                    engineLabel = if (custom.searchUrlTemplate.contains("opds", ignoreCase = true)) {
                        "Servidor OPDS"
                    } else {
                        "Site Personalizado"
                    },
                    description = custom.baseUrl,
                    faviconUrl = healthFavicon
                        .ifBlank { custom.faviconUrl }
                        .ifBlank { SourceCatalogDirectory.defaultFaviconUrl(custom.baseUrl) },
                    isEnabled = custom.enabled,
                    savedCustomSource = custom
                )
            }

        return extraCustomItems + catalogMapped
    }

    fun toggleSourceItem(item: ManagedSourceItem, wantEnabled: Boolean) {
        viewModelScope.launch {
            when {
                item.isLockedNative -> Unit
                item.isBuiltInKitsu -> {
                    repository.preferencesRepository.setEnableKitsuCatalog(wantEnabled)
                }
                item.savedCustomSource != null -> {
                    repository.setCustomSourceEnabled(item.savedCustomSource.id, wantEnabled)
                }
                wantEnabled -> {
                    repository.addCustomSource(
                        name = item.name,
                        baseUrl = item.baseUrl,
                        searchTemplate = item.searchTemplate,
                        faviconUrl = item.faviconUrl,
                        languageCode = item.languageCode,
                        catalogType = item.engineLabel
                    )
                    _snackbarMessage.value = "\"${item.name}\" ativado nas suas fontes!"
                }
            }
        }
    }

    fun addOrUpdateCustomSource(
        existingId: Long?,
        name: String,
        baseUrl: String,
        searchTemplate: String,
        languageCode: String = "ALL"
    ) {
        viewModelScope.launch {
            if (existingId != null && existingId > 0L) {
                repository.updateCustomSource(
                    sourceId = existingId,
                    name = name,
                    baseUrl = baseUrl,
                    searchTemplate = searchTemplate
                )
                _snackbarMessage.value = "Fonte \"$name\" atualizada!"
            } else {
                val id = repository.addCustomSource(
                    name = name,
                    baseUrl = baseUrl,
                    searchTemplate = searchTemplate,
                    languageCode = languageCode
                )
                if (id > 0L) {
                    _snackbarMessage.value = "Fonte \"$name\" adicionada e ativada!"
                }
            }
        }
    }

    fun deleteCustomSource(sourceId: Long, name: String) {
        viewModelScope.launch {
            repository.deleteCustomSource(sourceId)
            _snackbarMessage.value = "Fonte \"$name\" removida."
        }
    }

    fun testSingleSource(item: ManagedSourceItem) {
        viewModelScope.launch {
            _healthResults.update { map ->
                map + (item.key to SourceHealthCheckResult(state = SourceHealthState.TESTING))
            }
            val result = repository.testSourceConnection(
                sourceId = item.savedCustomSource?.id,
                name = item.name,
                baseUrl = item.baseUrl,
                probeUrlOverride = item.probeUrlOverride
            )
            _healthResults.update { map -> map + (item.key to result) }
        }
    }

    fun testAllActiveSources(items: List<ManagedSourceItem>) {
        if (_isBatchTesting.value) return
        val targets = items.filter { it.isEnabled }
        if (targets.isEmpty()) return

        viewModelScope.launch {
            _isBatchTesting.value = true
            _batchProgress.value = 0 to targets.size
            targets.forEach { item ->
                _healthResults.update { map ->
                    map + (item.key to SourceHealthCheckResult(state = SourceHealthState.TESTING))
                }
            }

            var completed = 0
            var onlineCount = 0
            var protectedCount = 0

            coroutineScope {
                targets.chunked(4).forEach { chunk ->
                    val batchResults = chunk.map { item ->
                        async {
                            val res = repository.testSourceConnection(
                                sourceId = item.savedCustomSource?.id,
                                name = item.name,
                                baseUrl = item.baseUrl,
                                probeUrlOverride = item.probeUrlOverride
                            )
                            item.key to res
                        }
                    }.awaitAll()

                    batchResults.forEach { (key, res) ->
                        completed++
                        if (res.state == SourceHealthState.ONLINE) onlineCount++
                        if (res.state == SourceHealthState.CLOUDFLARE_PROTECTED) protectedCount++
                        _healthResults.update { map -> map + (key to res) }
                        _batchProgress.value = completed to targets.size
                    }
                }
            }

            _isBatchTesting.value = false
            _snackbarMessage.value =
                "Teste concluído: $onlineCount online, $protectedCount com anti-bot de ${targets.size} fontes."
        }
    }

    companion object {
        fun factory(repository: MangaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SourcesCatalogViewModel(repository) as T
                }
            }
    }
}
