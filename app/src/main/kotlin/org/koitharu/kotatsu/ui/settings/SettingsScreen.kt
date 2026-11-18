@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package org.koitharu.kotatsu.ui.settings

import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.AppThemeMode
import org.koitharu.kotatsu.domain.model.ChapterDisplayMode
import org.koitharu.kotatsu.domain.model.CustomMangaSource
import org.koitharu.kotatsu.domain.model.DohProvider
import org.koitharu.kotatsu.domain.model.ExploreLayoutMode
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.LibraryStats
import org.koitharu.kotatsu.domain.model.ReaderBackground
import org.koitharu.kotatsu.domain.model.ReaderMode
import org.koitharu.kotatsu.domain.model.ReaderScaleMode
import org.koitharu.kotatsu.ui.components.AddCustomSourceDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class CategoryMeta(
    val category: SettingsCategory,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onOpenBrowser: (url: String, title: String) -> Unit,
    onTriggerLockNow: () -> Unit,
    bottomClearance: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val customSources by viewModel.customSources.collectAsStateWithLifecycle()
    val stats by viewModel.libraryStats.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val detailListState = rememberLazyListState()
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<CustomMangaSource?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }

    val formattedCacheSize = remember(cacheSizeBytes, context) {
        Formatter.formatShortFileSize(context, cacheSizeBytes.coerceAtLeast(0L))
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreBackup(uri)
    }

    LaunchedEffect(selectedCategory) {
        detailListState.scrollToItem(0)
        if (selectedCategory == SettingsCategory.BACKUP) {
            viewModel.refreshCacheSize()
        }
    }

    LaunchedEffect(snackbarMsg) {
        if (!snackbarMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            viewModel.consumeSnackbar()
        }
    }

    val categories = remember {
        listOf(
            CategoryMeta(
                SettingsCategory.APPEARANCE,
                R.string.settings_cat_appearance,
                R.string.settings_cat_appearance_sub,
                Icons.Outlined.Palette
            ),
            CategoryMeta(
                SettingsCategory.READER,
                R.string.settings_cat_reader,
                R.string.settings_cat_reader_sub,
                Icons.Outlined.AutoStories
            ),
            CategoryMeta(
                SettingsCategory.EXPLORE,
                R.string.settings_cat_explore,
                R.string.settings_cat_explore_sub,
                Icons.Outlined.Explore
            ),
            CategoryMeta(
                SettingsCategory.SOURCES,
                R.string.settings_cat_sources,
                R.string.settings_cat_sources_sub,
                Icons.Outlined.Language
            ),
            CategoryMeta(
                SettingsCategory.NETWORK,
                R.string.settings_cat_network,
                R.string.settings_cat_network_sub,
                Icons.Outlined.Shield
            ),
            CategoryMeta(
                SettingsCategory.SECURITY,
                R.string.settings_cat_security,
                R.string.settings_cat_security_sub,
                Icons.Outlined.Lock
            ),
            CategoryMeta(
                SettingsCategory.BACKUP,
                R.string.settings_cat_backup,
                R.string.settings_cat_backup_sub,
                Icons.Outlined.Backup
            )
        )
    }

    fun launchDatedBackupExport() {
        val dateSuffix = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        exportBackupLauncher.launch("kotatsu_backup_$dateSuffix.json")
    }

    val motionScheme = MaterialTheme.motionScheme

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomClearance)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) { _ ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val isTabletTwoPane = maxWidth >= 720.dp
            val cookieShape = MaterialShapes.Cookie9Sided.toShape()

            if (isTabletTwoPane) {
                // Canonical Two-Pane Tablet Settings Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = bottomClearance + 16.dp),
                        modifier = Modifier
                            .width(308.dp)
                            .fillMaxHeight()
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                if (showBackButton) {
                                    FilledTonalIconButton(
                                        onClick = onBack,
                                        modifier = Modifier.testTag("settings_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                            contentDescription = stringResource(R.string.back_action)
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.settings_title),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        items(categories, key = { it.category.name }) { meta ->
                            val selected = selectedCategory == meta.category
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { viewModel.selectCategory(meta.category) }
                                    .testTag("settings_cat_${meta.category.name}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(cookieShape)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            )
                                    ) {
                                        Icon(
                                            imageVector = meta.icon,
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(meta.titleRes),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(meta.subtitleRes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LazyColumn(
                        state = detailListState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomClearance + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        item {
                            AnimatedContent(
                                targetState = selectedCategory,
                                transitionSpec = {
                                    (fadeIn(motionScheme.defaultEffectsSpec()) +
                                        slideInVertically(motionScheme.fastSpatialSpec()) { it / 16 }) togetherWith
                                        fadeOut(motionScheme.fastEffectsSpec())
                                },
                                label = "tabletSettingsPaneTransition"
                            ) { activeCat ->
                                SettingsDetailPaneContent(
                                    category = activeCat,
                                    prefs = prefs,
                                    customSources = customSources,
                                    stats = stats,
                                    formattedCacheSize = formattedCacheSize,
                                    viewModel = viewModel,
                                    onShowAddSource = { showAddSourceDialog = true },
                                    onEditSource = { editingSource = it },
                                    onShowPinSetup = { showPinDialog = true },
                                    onOpenBrowser = onOpenBrowser,
                                    onTriggerLockNow = onTriggerLockNow,
                                    onExportBackup = ::launchDatedBackupExport,
                                    onRestoreBackup = {
                                        restoreBackupLauncher.launch(arrayOf("*/*"))
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Compact Mobile Settings Layout
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (showBackButton) {
                            FilledTonalIconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("settings_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.back_action)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories, key = { it.category.name }) { meta ->
                            val selected = selectedCategory == meta.category
                            ToggleButton(
                                checked = selected,
                                onCheckedChange = { viewModel.selectCategory(meta.category) },
                                shapes = ToggleButtonDefaults.shapes(),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .animateContentSize(motionScheme.fastSpatialSpec())
                                    .testTag("settings_cat_${meta.category.name}")
                            ) {
                                Icon(
                                    imageVector = meta.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(meta.titleRes))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        state = detailListState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = bottomClearance + 28.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            AnimatedContent(
                                targetState = selectedCategory,
                                transitionSpec = {
                                    (fadeIn(motionScheme.defaultEffectsSpec()) +
                                        slideInVertically(motionScheme.fastSpatialSpec()) { it / 16 }) togetherWith
                                        fadeOut(motionScheme.fastEffectsSpec())
                                },
                                label = "mobileSettingsPaneTransition"
                            ) { activeCat ->
                                SettingsDetailPaneContent(
                                    category = activeCat,
                                    prefs = prefs,
                                    customSources = customSources,
                                    stats = stats,
                                    formattedCacheSize = formattedCacheSize,
                                    viewModel = viewModel,
                                    onShowAddSource = { showAddSourceDialog = true },
                                    onEditSource = { editingSource = it },
                                    onShowPinSetup = { showPinDialog = true },
                                    onOpenBrowser = onOpenBrowser,
                                    onTriggerLockNow = onTriggerLockNow,
                                    onExportBackup = ::launchDatedBackupExport,
                                    onRestoreBackup = {
                                        restoreBackupLauncher.launch(arrayOf("*/*"))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSourceDialog) {
        AddCustomSourceDialog(
            onDismiss = { showAddSourceDialog = false },
            onConfirmAdd = { name, url, tpl ->
                viewModel.addCustomSource(name, url, tpl)
                showAddSourceDialog = false
            }
        )
    }

    editingSource?.let { sourceToEdit ->
        AddCustomSourceDialog(
            initialSource = sourceToEdit,
            onDismiss = { editingSource = null },
            onConfirmAdd = { name, url, tpl ->
                viewModel.updateCustomSource(sourceToEdit.id, name, url, tpl)
                editingSource = null
            }
        )
    }

    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        val mismatch = pinInput.length == 4 &&
            confirmPinInput.length == 4 &&
            pinInput != confirmPinInput
        val canConfirmPin = pinInput.length == 4 && confirmPinInput == pinInput

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.setup_pin_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { raw ->
                            pinInput = raw.filter { it.isDigit() }.take(4)
                        },
                        label = { Text(stringResource(R.string.setup_pin_input_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_pin_input")
                    )

                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { raw ->
                            confirmPinInput = raw.filter { it.isDigit() }.take(4)
                        },
                        label = { Text(stringResource(R.string.setup_pin_confirm_label)) },
                        singleLine = true,
                        isError = mismatch,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_pin_confirm_input")
                    )

                    if (mismatch) {
                        Text(
                            text = stringResource(R.string.setup_pin_mismatch),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (canConfirmPin) {
                            viewModel.configureAppLock(true, pinInput)
                            showPinDialog = false
                        }
                    },
                    enabled = canConfirmPin,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("confirm_setup_pin_button")
                ) {
                    Text(stringResource(R.string.save_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPinDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }
}

@Composable
private fun SettingsDetailPaneContent(
    category: SettingsCategory,
    prefs: AppPreferences,
    customSources: List<CustomMangaSource>,
    stats: LibraryStats,
    formattedCacheSize: String,
    viewModel: SettingsViewModel,
    onShowAddSource: () -> Unit,
    onEditSource: (CustomMangaSource) -> Unit,
    onShowPinSetup: () -> Unit,
    onOpenBrowser: (String, String) -> Unit,
    onTriggerLockNow: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            when (category) {
                SettingsCategory.APPEARANCE -> {
                    SectionHeadline(stringResource(R.string.settings_cat_appearance))

                    ChoiceGroupLabel(stringResource(R.string.theme_mode_label))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val selected = prefs.themeMode == mode
                            val labelRes = when (mode) {
                                AppThemeMode.DARK -> R.string.theme_dark
                                AppThemeMode.LIGHT -> R.string.theme_light
                                AppThemeMode.SYSTEM -> R.string.theme_system
                            }
                            ExpressiveOptionToggle(
                                selected = selected,
                                label = stringResource(labelRes),
                                onClick = { viewModel.setThemeMode(mode) },
                                tag = "settings_theme_${mode.name}"
                            )
                        }
                    }

                    SettingsSwitchRow(
                        title = stringResource(R.string.amoled_dark_label),
                        subtitle = stringResource(R.string.amoled_dark_sub),
                        checked = prefs.amoledDark,
                        onCheckedChange = viewModel::setAmoledDark,
                        tag = "switch_amoled_dark"
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.dynamic_colors_label),
                            subtitle = stringResource(R.string.dynamic_colors_sub),
                            checked = prefs.dynamicColors,
                            onCheckedChange = viewModel::setDynamicColors,
                            tag = "switch_dynamic_colors"
                        )
                    }

                    SettingsSwitchRow(
                        title = stringResource(R.string.toggle_badges_label),
                        subtitle = stringResource(R.string.toggle_badges_sub),
                        checked = prefs.showSourceBadges,
                        onCheckedChange = viewModel::setShowSourceBadges,
                        tag = "switch_source_badges"
                    )
                }

                SettingsCategory.READER -> {
                    SectionHeadline(stringResource(R.string.settings_cat_reader))

                    ChoiceGroupLabel(stringResource(R.string.default_reader_mode_label))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderMode.entries.forEach { mode ->
                            val labelRes = when (mode) {
                                ReaderMode.STANDARD_LTR -> R.string.reader_mode_standard
                                ReaderMode.MANGA_RTL -> R.string.reader_mode_reversed
                                ReaderMode.VERTICAL_PAGED -> R.string.reader_mode_vertical
                                ReaderMode.WEBTOON -> R.string.reader_mode_webtoon
                            }
                            ExpressiveOptionToggle(
                                selected = prefs.defaultReaderMode == mode,
                                label = stringResource(labelRes),
                                onClick = { viewModel.setDefaultReaderMode(mode) },
                                tag = "settings_reader_mode_${mode.name}"
                            )
                        }
                    }

                    ChoiceGroupLabel(stringResource(R.string.reader_scale_label))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderScaleMode.entries.forEach { scale ->
                            val labelRes = when (scale) {
                                ReaderScaleMode.FIT_SCREEN -> R.string.reader_scale_fit
                                ReaderScaleMode.FIT_WIDTH -> R.string.reader_scale_width
                                ReaderScaleMode.FIT_HEIGHT -> R.string.reader_scale_height
                                ReaderScaleMode.CROP -> R.string.reader_scale_crop
                            }
                            ExpressiveOptionToggle(
                                selected = prefs.readerScaleMode == scale,
                                label = stringResource(labelRes),
                                onClick = { viewModel.setReaderScaleMode(scale) },
                                tag = "settings_reader_scale_${scale.name}"
                            )
                        }
                    }

                    ChoiceGroupLabel(stringResource(R.string.reader_bg_label))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderBackground.entries.forEach { bg ->
                            val labelRes = when (bg) {
                                ReaderBackground.AMOLED -> R.string.reader_bg_amoled
                                ReaderBackground.SUMI_INK -> R.string.reader_bg_ink
                                ReaderBackground.SEPIA -> R.string.reader_bg_sepia
                                ReaderBackground.PAPER_LIGHT -> R.string.reader_bg_light
                            }
                            ExpressiveOptionToggle(
                                selected = prefs.readerBackground == bg,
                                label = stringResource(labelRes),
                                onClick = { viewModel.setReaderBackground(bg) },
                                tag = "settings_reader_bg_${bg.name}"
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    ChoiceGroupLabel(stringResource(R.string.reader_color_effects_label))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.reader_brightness_dim_label),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(prefs.readerBrightnessDim * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = prefs.readerBrightnessDim,
                            onValueChange = viewModel::setReaderBrightnessDim,
                            valueRange = 0f..0.65f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_brightness_dim_slider")
                        )
                    }

                    SettingsSwitchRow(
                        title = stringResource(R.string.reader_grayscale_label),
                        subtitle = stringResource(R.string.reader_grayscale_sub),
                        checked = prefs.readerGrayscale,
                        onCheckedChange = viewModel::setReaderGrayscale,
                        tag = "switch_reader_grayscale"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.reader_invert_label),
                        subtitle = stringResource(R.string.reader_invert_sub),
                        checked = prefs.readerInvertColors,
                        onCheckedChange = viewModel::setReaderInvertColors,
                        tag = "switch_reader_invert"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    SettingsSwitchRow(
                        title = stringResource(R.string.reader_keep_screen_on_label),
                        subtitle = stringResource(R.string.reader_keep_screen_on_sub),
                        checked = prefs.keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn,
                        tag = "switch_keep_screen_on"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.reader_volume_keys_label),
                        subtitle = stringResource(R.string.reader_volume_keys_sub),
                        checked = prefs.volumeKeysNavigation,
                        onCheckedChange = viewModel::setVolumeKeysNavigation,
                        tag = "switch_volume_keys"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.reader_thumbnails_toggle),
                        subtitle = stringResource(R.string.reader_thumbnails_sub),
                        checked = prefs.showReaderThumbnails,
                        onCheckedChange = viewModel::setShowReaderThumbnails,
                        tag = "switch_reader_thumbs"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.reader_transitions_label),
                        subtitle = stringResource(R.string.reader_transitions_sub),
                        checked = prefs.pageTransitions,
                        onCheckedChange = viewModel::setPageTransitions,
                        tag = "switch_page_transitions"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.reader_mini_info_label),
                        subtitle = stringResource(R.string.reader_mini_info_sub),
                        checked = prefs.showReaderMiniInfo,
                        onCheckedChange = viewModel::setShowReaderMiniInfo,
                        tag = "switch_mini_info"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.reader_webtoon_gap_label),
                        subtitle = stringResource(R.string.reader_webtoon_gap_sub),
                        checked = prefs.webtoonSeamlessGap,
                        onCheckedChange = viewModel::setWebtoonSeamlessGap,
                        tag = "switch_webtoon_gap"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.data_saver_label),
                        subtitle = stringResource(R.string.data_saver_subtitle),
                        checked = prefs.dataSaver,
                        onCheckedChange = viewModel::setDataSaver,
                        tag = "switch_data_saver"
                    )
                }

                SettingsCategory.EXPLORE -> {
                    SectionHeadline(stringResource(R.string.settings_cat_explore))

                    ChoiceGroupLabel(stringResource(R.string.layout_mode_label))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExploreLayoutMode.entries.forEach { mode ->
                            val labelRes = when (mode) {
                                ExploreLayoutMode.GRID_STANDARD -> R.string.layout_grid_standard
                                ExploreLayoutMode.GRID_COMPACT -> R.string.layout_grid_compact
                                ExploreLayoutMode.GRID_LARGE -> R.string.layout_grid_large
                                ExploreLayoutMode.LIST_DETAILED -> R.string.layout_list_detailed
                            }
                            ExpressiveOptionToggle(
                                selected = prefs.exploreLayoutMode == mode,
                                label = stringResource(labelRes),
                                onClick = { viewModel.setExploreLayoutMode(mode) },
                                tag = "settings_explore_layout_${mode.name}"
                            )
                        }
                    }

                    ChoiceGroupLabel(stringResource(R.string.chapters_default_mode_label))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChapterDisplayMode.entries.forEach { mode ->
                            val labelRes = when (mode) {
                                ChapterDisplayMode.LIST -> R.string.chapter_view_list
                                ChapterDisplayMode.DETAILED -> R.string.chapter_view_detailed
                                ChapterDisplayMode.GRID -> R.string.chapter_view_grid
                            }
                            ExpressiveOptionToggle(
                                selected = prefs.chapterDisplayMode == mode,
                                label = stringResource(labelRes),
                                onClick = { viewModel.setChapterDisplayMode(mode) },
                                tag = "settings_chapter_mode_${mode.name}"
                            )
                        }
                    }

                    ChoiceGroupLabel(stringResource(R.string.filter_language_label))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageFilter.entries.forEach { lang ->
                            val labelRes = when (lang) {
                                LanguageFilter.ALL -> R.string.lang_all
                                LanguageFilter.PT_BR -> R.string.lang_pt_br
                                LanguageFilter.EN -> R.string.lang_en
                                LanguageFilter.ES -> R.string.lang_es
                            }
                            ExpressiveOptionToggle(
                                selected = prefs.preferredLanguage == lang,
                                label = stringResource(labelRes),
                                onClick = { viewModel.setPreferredLanguage(lang) },
                                tag = "settings_pref_lang_${lang.name}"
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    SettingsSwitchRow(
                        title = stringResource(R.string.toggle_spotlight_label),
                        subtitle = stringResource(R.string.toggle_spotlight_sub),
                        checked = prefs.showExploreSpotlight,
                        onCheckedChange = viewModel::setShowExploreSpotlight,
                        tag = "switch_explore_spotlight"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.toggle_sources_bar_label),
                        subtitle = stringResource(R.string.toggle_sources_bar_sub),
                        checked = prefs.showExploreSourcesBar,
                        onCheckedChange = viewModel::setShowExploreSourcesBar,
                        tag = "switch_explore_sources"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.toggle_genres_strip_label),
                        subtitle = stringResource(R.string.toggle_genres_strip_sub),
                        checked = prefs.showExploreGenres,
                        onCheckedChange = viewModel::setShowExploreGenres,
                        tag = "switch_explore_genres"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.toggle_local_section_label),
                        subtitle = stringResource(R.string.toggle_local_section_sub),
                        checked = prefs.showExploreLocalSection,
                        onCheckedChange = viewModel::setShowExploreLocalSection,
                        tag = "switch_explore_local"
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.hide_nsfw_label),
                        subtitle = stringResource(R.string.hide_nsfw_sub),
                        checked = prefs.hideNsfw,
                        onCheckedChange = viewModel::setHideNsfw,
                        tag = "switch_hide_nsfw"
                    )
                }

                SettingsCategory.SOURCES -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SectionHeadline(stringResource(R.string.settings_cat_sources))
                        Button(
                            onClick = onShowAddSource,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_add_source_button")
                        ) {
                            Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.add_source_button))
                        }
                    }

                    // Primary Built-in MangaDex Catalog Card (non-deceptive active status + open in browser)
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("source_builtin_mangadex")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_mangadex_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_mangadex_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onOpenBrowser("https://mangadex.org", "MangaDex") }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.OpenInBrowser,
                                    contentDescription = stringResource(R.string.open_in_webview)
                                )
                            }
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
                        }
                    }

                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_kitsu_title),
                        subtitle = stringResource(R.string.settings_kitsu_sub),
                        checked = prefs.enableKitsuCatalog,
                        onCheckedChange = viewModel::setEnableKitsuCatalog,
                        tag = "source_builtin_kitsu"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Text(
                        text = stringResource(R.string.settings_custom_sources_header, customSources.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (customSources.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_custom_sources_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        customSources.forEach { src ->
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = src.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = src.baseUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onOpenBrowser(src.baseUrl, src.name) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.OpenInBrowser,
                                            contentDescription = stringResource(R.string.open_in_webview)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onEditSource(src) },
                                        modifier = Modifier.testTag("edit_custom_source_${src.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = stringResource(R.string.edit_action)
                                        )
                                    }
                                    Switch(
                                        checked = src.enabled,
                                        onCheckedChange = { viewModel.toggleCustomSource(src.id, it) }
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteCustomSource(src.id) },
                                        modifier = Modifier.testTag("delete_custom_source_${src.id}")
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

                SettingsCategory.NETWORK -> {
                    SectionHeadline(stringResource(R.string.settings_cat_network))

                    SettingsSwitchRow(
                        title = stringResource(R.string.ad_shield_label),
                        subtitle = stringResource(R.string.ad_shield_sub),
                        checked = prefs.adShieldEnabled,
                        onCheckedChange = viewModel::setAdShieldEnabled,
                        tag = "switch_ad_shield"
                    )

                    SettingsSwitchRow(
                        title = stringResource(R.string.anti_adblock_bypass_label),
                        subtitle = stringResource(R.string.anti_adblock_bypass_sub),
                        checked = prefs.antiAdblockBypass,
                        onCheckedChange = viewModel::setAntiAdblockBypass,
                        enabled = prefs.adShieldEnabled,
                        tag = "switch_anti_adblock"
                    )

                    SettingsSwitchRow(
                        title = stringResource(R.string.popup_block_label),
                        subtitle = stringResource(R.string.popup_block_sub),
                        checked = prefs.blockPopupsRedirects,
                        onCheckedChange = viewModel::setBlockPopupsRedirects,
                        tag = "switch_popup_block"
                    )

                    ChoiceGroupLabel(stringResource(R.string.doh_label))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DohProvider.entries.forEach { provider ->
                            val labelRes = when (provider) {
                                DohProvider.NONE -> R.string.doh_none
                                DohProvider.CLOUDFLARE -> R.string.doh_cloudflare
                                DohProvider.GOOGLE -> R.string.doh_google
                            }
                            ExpressiveOptionToggle(
                                selected = prefs.dohProvider == provider,
                                label = stringResource(labelRes),
                                onClick = { viewModel.setDohProvider(provider) },
                                tag = "settings_doh_${provider.name}"
                            )
                        }
                    }

                    var uaDraft by remember(prefs.customUserAgent) { mutableStateOf(prefs.customUserAgent) }
                    ChoiceGroupLabel(stringResource(R.string.user_agent_label))
                    OutlinedTextField(
                        value = uaDraft,
                        onValueChange = { uaDraft = it },
                        singleLine = false,
                        maxLines = 3,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_user_agent_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.setCustomUserAgent(uaDraft) },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(stringResource(R.string.apply_user_agent))
                        }
                        OutlinedButton(
                            onClick = {
                                uaDraft = AppPreferences.DEFAULT_BROWSER_UA
                                viewModel.setCustomUserAgent(AppPreferences.DEFAULT_BROWSER_UA)
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(stringResource(R.string.reset_default))
                        }
                    }
                }

                SettingsCategory.SECURITY -> {
                    SectionHeadline(stringResource(R.string.settings_cat_security))

                    SettingsSwitchRow(
                        title = stringResource(R.string.app_lock_enable_label),
                        subtitle = stringResource(R.string.app_lock_enable_sub),
                        checked = prefs.appLockEnabled,
                        onCheckedChange = { wantEnable ->
                            if (wantEnable) {
                                onShowPinSetup()
                            } else {
                                viewModel.configureAppLock(false, "")
                            }
                        },
                        tag = "switch_app_lock"
                    )

                    if (prefs.appLockEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(
                                onClick = onShowPinSetup,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(stringResource(R.string.change_pin_button))
                            }
                            Button(
                                onClick = onTriggerLockNow,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("lock_app_now_button")
                            ) {
                                Icon(imageVector = Icons.Outlined.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.lock_now_button))
                            }
                        }

                        SettingsSwitchRow(
                            title = stringResource(R.string.lock_screen_system_auth),
                            subtitle = stringResource(R.string.lock_screen_system_auth_sub),
                            checked = prefs.appLockSystemCredential,
                            onCheckedChange = viewModel::setAppLockSystemCredential,
                            tag = "switch_system_credential"
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    SettingsSwitchRow(
                        title = stringResource(R.string.incognito_mode_label),
                        subtitle = stringResource(R.string.incognito_mode_sub),
                        checked = prefs.incognitoMode,
                        onCheckedChange = viewModel::setIncognitoMode,
                        tag = "switch_incognito_mode"
                    )

                    SettingsSwitchRow(
                        title = stringResource(R.string.secure_screen_label),
                        subtitle = stringResource(R.string.secure_screen_sub),
                        checked = prefs.secureScreenEnabled,
                        onCheckedChange = viewModel::setSecureScreenEnabled,
                        tag = "switch_secure_screen"
                    )
                }

                SettingsCategory.BACKUP -> {
                    SectionHeadline(stringResource(R.string.settings_cat_backup))

                    // Expressive Library Statistics Grid
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatBadgeCard(stringResource(R.string.stat_favorites), stats.favoritesCount)
                        StatBadgeCard(stringResource(R.string.stat_history), stats.historyCount)
                        StatBadgeCard(stringResource(R.string.stat_bookmarks), stats.bookmarksCount)
                        StatBadgeCard(stringResource(R.string.stat_local_cbz), stats.localCbzCount)
                        StatBadgeCard(stringResource(R.string.stat_custom_sites), stats.customSourcesCount)
                    }

                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.backup_compat_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onExportBackup,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("export_backup_button")
                        ) {
                            Icon(imageVector = Icons.Outlined.Backup, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.export_backup_json), maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = onRestoreBackup,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("restore_backup_button")
                        ) {
                            Icon(imageVector = Icons.Outlined.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.restore_backup_any), maxLines = 1)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    OutlinedButton(
                        onClick = viewModel::clearMemoryCaches,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("clear_cache_button")
                    ) {
                        Icon(imageVector = Icons.Outlined.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.clear_cache_button_format, formattedCacheSize))
                    }

                    Text(
                        text = stringResource(R.string.about_version_footer),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeadline(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ChoiceGroupLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ExpressiveOptionToggle(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    TonalToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        shapes = ToggleButtonDefaults.shapes(),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
            .testTag(tag)
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = label)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun StatBadgeCard(label: String, value: Int) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(126.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp)
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
