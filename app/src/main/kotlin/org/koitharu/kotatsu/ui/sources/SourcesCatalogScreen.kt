@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package org.koitharu.kotatsu.ui.sources

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.domain.model.CustomMangaSource
import org.koitharu.kotatsu.domain.model.SourceHealthCheckResult
import org.koitharu.kotatsu.domain.model.SourceHealthState
import org.koitharu.kotatsu.ui.components.AddCustomSourceDialog
import org.koitharu.kotatsu.ui.components.EditorialEmptyState
import org.koitharu.kotatsu.ui.components.SourceFaviconBadge

@Composable
fun SourcesCatalogScreen(
    viewModel: SourcesCatalogViewModel,
    onBack: () -> Unit,
    onOpenBrowser: (url: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val customSources by viewModel.customSources.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val regionFilter by viewModel.regionFilter.collectAsStateWithLifecycle()
    val healthResults by viewModel.healthResults.collectAsStateWithLifecycle()
    val isBatchTesting by viewModel.isBatchTesting.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCustomSource by remember { mutableStateOf<CustomMangaSource?>(null) }
    var configuringTemplate by remember { mutableStateOf<ManagedSourceItem?>(null) }

    LaunchedEffect(snackbarMsg) {
        if (!snackbarMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            viewModel.consumeSnackbar()
        }
    }

    val unifiedSources = remember(prefs, customSources, healthResults) {
        viewModel.buildUnifiedList(prefs, customSources, healthResults)
    }

    val filteredSources = remember(unifiedSources, query, regionFilter) {
        unifiedSources.filter { item ->
            val matchesRegion = when (regionFilter) {
                CatalogRegionFilter.ALL -> true
                CatalogRegionFilter.ACTIVE_ONLY -> item.isEnabled
                CatalogRegionFilter.PT_BR -> item.languageCode == "PT-BR" || item.languageCode == "MULTI"
                CatalogRegionFilter.EN -> item.languageCode == "EN" || item.languageCode == "MULTI"
                CatalogRegionFilter.ES -> item.languageCode == "ES" || item.languageCode == "MULTI"
                CatalogRegionFilter.GLOBAL_AND_OPDS ->
                    item.languageCode == "MULTI" || item.languageCode == "OPDS"
            }
            val q = query.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.baseUrl.lowercase().contains(q) ||
                item.engineLabel.lowercase().contains(q) ||
                item.description.lowercase().contains(q)
            matchesRegion && matchesQuery
        }
    }

    val activeCount = remember(unifiedSources) { unifiedSources.count { it.isEnabled } }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("sources_catalog_screen")
    ) { _ ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 328.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalIconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("sources_catalog_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.back_action)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.sources_catalog_screen_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    R.string.sources_catalog_screen_sub_format,
                                    activeCount,
                                    unifiedSources.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Action Buttons Row (FlowRow prevents clipping on narrow phones)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showAddDialog = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("catalog_add_custom_site_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.add_source_button))
                        }

                        FilledTonalButton(
                            onClick = { viewModel.testAllActiveSources(unifiedSources) },
                            enabled = !isBatchTesting && activeCount > 0,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("catalog_test_all_active_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NetworkCheck,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.sources_test_all_active))
                        }
                    }

                    AnimatedVisibility(visible = isBatchTesting) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val (done, total) = batchProgress
                            val progress = if (total > 0) done.toFloat() / total.toFloat() else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                text = stringResource(R.string.sources_testing_batch_progress, done, total),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Search Bar
                    TextField(
                        value = query,
                        onValueChange = viewModel::onSearchQueryChanged,
                        placeholder = { Text(stringResource(R.string.sources_search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = stringResource(R.string.clear_search)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sources_catalog_search_input")
                    )

                    // Region / Category Filter Strip
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(CatalogRegionFilter.entries) { filter ->
                            val selected = regionFilter == filter
                            val labelRes = when (filter) {
                                CatalogRegionFilter.ALL -> R.string.sources_filter_all
                                CatalogRegionFilter.ACTIVE_ONLY -> R.string.sources_filter_active
                                CatalogRegionFilter.PT_BR -> R.string.lang_pt_br
                                CatalogRegionFilter.EN -> R.string.lang_en
                                CatalogRegionFilter.ES -> R.string.lang_es
                                CatalogRegionFilter.GLOBAL_AND_OPDS -> R.string.sources_filter_global_opds
                            }
                            TonalToggleButton(
                                checked = selected,
                                onCheckedChange = { viewModel.onRegionFilterSelected(filter) },
                                shapes = ToggleButtonDefaults.shapes(),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("sources_region_${filter.name}")
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(stringResource(labelRes))
                            }
                        }
                    }
                }
            }

            if (filteredSources.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EditorialEmptyState(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.sources_empty_search_title),
                        subtitle = stringResource(R.string.sources_empty_search_sub),
                        actionLabel = stringResource(R.string.add_source_button),
                        onActionClick = { showAddDialog = true },
                        testTagName = "sources_catalog_empty"
                    )
                }
            } else {
                items(filteredSources, key = { it.key }) { item ->
                    ManagedSourceExpressiveCard(
                        item = item,
                        healthResult = healthResults[item.key],
                        onToggleEnabled = { enabled ->
                            if (item.requiresServerConfig && enabled) {
                                configuringTemplate = item
                            } else {
                                viewModel.toggleSourceItem(item, enabled)
                            }
                        },
                        onTestConnection = { viewModel.testSingleSource(item) },
                        onOpenBrowser = { onOpenBrowser(item.baseUrl, item.name) },
                        onEdit = {
                            if (item.savedCustomSource != null) {
                                editingCustomSource = item.savedCustomSource
                            } else {
                                configuringTemplate = item
                            }
                        },
                        onDelete = item.savedCustomSource?.let { saved ->
                            { viewModel.deleteCustomSource(saved.id, saved.name) }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomSourceDialog(
            onDismiss = { showAddDialog = false },
            onConfirmAdd = { name, url, tpl ->
                viewModel.addOrUpdateCustomSource(null, name, url, tpl)
                showAddDialog = false
            }
        )
    }

    editingCustomSource?.let { target ->
        AddCustomSourceDialog(
            initialSource = target,
            onDismiss = { editingCustomSource = null },
            onConfirmAdd = { name, url, tpl ->
                viewModel.addOrUpdateCustomSource(target.id, name, url, tpl, target.languageCode)
                editingCustomSource = null
            }
        )
    }

    configuringTemplate?.let { tplItem ->
        AddCustomSourceDialog(
            initialSource = CustomMangaSource(
                id = 0L,
                name = tplItem.name,
                baseUrl = tplItem.baseUrl,
                searchUrlTemplate = tplItem.searchTemplate,
                languageCode = tplItem.languageCode
            ),
            onDismiss = { configuringTemplate = null },
            onConfirmAdd = { name, url, tpl ->
                viewModel.addOrUpdateCustomSource(null, name, url, tpl, tplItem.languageCode)
                configuringTemplate = null
            }
        )
    }
}

@Composable
private fun ManagedSourceExpressiveCard(
    item: ManagedSourceItem,
    healthResult: SourceHealthCheckResult?,
    onToggleEnabled: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onOpenBrowser: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (item.isEnabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = if (item.isEnabled) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog_source_card_${item.key}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            // Top Row: Favicon + Name/Host + Enable Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SourceFaviconBadge(
                    name = item.name,
                    baseUrl = item.baseUrl,
                    explicitFaviconUrl = item.faviconUrl,
                    size = 46.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.isLockedNative) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.settings_builtin_active_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Switch(
                        checked = item.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("toggle_catalog_${item.key}")
                    )
                }
            }

            // Metadata Badges (Language + Engine)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = item.languageCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = item.engineLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Live Health Check Status Banner
            if (healthResult != null && healthResult.state != SourceHealthState.UNTESTED) {
                SourceHealthStatusBanner(healthResult = healthResult)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Bottom Actions Row (Wraps cleanly so no text or icon clips)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val isTesting = healthResult?.state == SourceHealthState.TESTING
                FilledTonalButton(
                    onClick = onTestConnection,
                    enabled = !isTesting,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("test_source_btn_${item.key}")
                ) {
                    if (isTesting) {
                        LoadingIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.source_test_and_favicon_action),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onOpenBrowser,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("browser_source_btn_${item.key}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.source_open_short),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (!item.isLockedNative && !item.isBuiltInKitsu) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_catalog_${item.key}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.edit_action)
                        )
                    }
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_catalog_${item.key}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.delete_action),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SourceHealthStatusBanner(
    healthResult: SourceHealthCheckResult,
    modifier: Modifier = Modifier
) {
    val containerColor = when (healthResult.state) {
        SourceHealthState.ONLINE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        SourceHealthState.CLOUDFLARE_PROTECTED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
        SourceHealthState.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        SourceHealthState.TESTING -> MaterialTheme.colorScheme.surfaceContainerHighest
        SourceHealthState.UNTESTED -> Color.Transparent
    }
    val contentColor = when (healthResult.state) {
        SourceHealthState.ONLINE -> MaterialTheme.colorScheme.onPrimaryContainer
        SourceHealthState.CLOUDFLARE_PROTECTED -> MaterialTheme.colorScheme.onTertiaryContainer
        SourceHealthState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (healthResult.state) {
        SourceHealthState.ONLINE -> Icons.Outlined.CheckCircle
        SourceHealthState.CLOUDFLARE_PROTECTED -> Icons.Outlined.Security
        SourceHealthState.ERROR -> Icons.Outlined.ErrorOutline
        else -> Icons.Outlined.NetworkCheck
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (healthResult.state == SourceHealthState.TESTING) {
                LoadingIndicator(modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.source_testing_now),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = healthResult.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
            }
        }
    }
}
