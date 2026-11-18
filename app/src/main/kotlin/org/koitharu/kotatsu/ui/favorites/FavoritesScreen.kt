@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package org.koitharu.kotatsu.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.domain.model.FavoriteCategory
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.ui.components.EditorialEmptyState
import org.koitharu.kotatsu.ui.components.KotatsuPageHeader
import org.koitharu.kotatsu.ui.components.MangaCoverCard

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpenManga: (MangaItem) -> Unit,
    onNavigateExplore: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    bottomClearance: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val allFavorites by viewModel.allFavoritesCount.collectAsStateWithLifecycle()
    val displayedMangas by viewModel.displayedMangas.collectAsStateWithLifecycle()

    var showManageSheet by remember { mutableStateOf(false) }
    var categoryToRename by remember { mutableStateOf<FavoriteCategory?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
            .testTag("favorites_screen")
    ) {
        KotatsuPageHeader(
            title = stringResource(R.string.favorites_title),
            countBadge = allFavorites.size,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.padding(horizontal = 16.dp),
            actions = {
                FilledTonalButton(
                    onClick = { showManageSheet = true },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("manage_categories_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.manage_categories),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.manage_categories),
                        maxLines = 1
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // M3 Expressive Morphing Category ToggleButtons
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                ToggleButton(
                    checked = selectedCategoryId == null,
                    onCheckedChange = { viewModel.selectCategory(null) },
                    shapes = ToggleButtonDefaults.shapes(),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("category_tab_all")
                ) {
                    Text("${stringResource(R.string.all_favorites_tab)} (${allFavorites.size})")
                }
            }
            items(categories, key = { it.id }) { category ->
                ToggleButton(
                    checked = selectedCategoryId == category.id,
                    onCheckedChange = { viewModel.selectCategory(category.id) },
                    shapes = ToggleButtonDefaults.shapes(),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("category_tab_${category.id}")
                ) {
                    Text("${category.name} (${category.mangaCount})")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (displayedMangas.isEmpty()) {
            EditorialEmptyState(
                icon = if (allFavorites.isEmpty()) {
                    Icons.Outlined.FavoriteBorder
                } else {
                    Icons.Outlined.CollectionsBookmark
                },
                title = stringResource(
                    if (allFavorites.isEmpty()) R.string.favorites_empty_title
                    else R.string.category_empty_title
                ),
                subtitle = stringResource(
                    if (allFavorites.isEmpty()) R.string.favorites_empty_subtitle
                    else R.string.category_empty_subtitle
                ),
                actionLabel = stringResource(R.string.go_to_explore),
                onActionClick = onNavigateExplore,
                testTagName = "favorites_empty_state"
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = bottomClearance + 20.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("favorites_grid")
            ) {
                items(displayedMangas, key = { it.id }) { manga ->
                    MangaCoverCard(
                        manga = manga,
                        onClick = { onOpenManga(manga) },
                        modifier = Modifier.animateItem(),
                        testTagName = "favorite_manga_card_${manga.id}"
                    )
                }
            }
        }
    }

    if (showManageSheet) {
        var newName by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showManageSheet = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.manage_categories),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.add_category_title)) },
                        placeholder = { Text(stringResource(R.string.category_name_placeholder)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_category_input")
                    )
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                viewModel.createCategory(newName.trim())
                                newName = ""
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        enabled = newName.isNotBlank(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("create_category_button")
                    ) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.create_action))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(categories, key = { it.id }) { cat ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.manga_works_count,
                                            cat.mangaCount,
                                            cat.mangaCount
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { categoryToRename = cat },
                                    modifier = Modifier.testTag("rename_category_${cat.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.rename_category_title)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteCategory(cat.id) },
                                    modifier = Modifier.testTag("delete_category_${cat.id}")
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
        }
    }

    categoryToRename?.let { target ->
        var updatedText by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { categoryToRename = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.rename_category_title)) },
            text = {
                OutlinedTextField(
                    value = updatedText,
                    onValueChange = { updatedText = it },
                    label = { Text(stringResource(R.string.category_name_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (updatedText.isNotBlank()) {
                            viewModel.renameCategory(target.id, updatedText)
                        }
                        categoryToRename = null
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.save_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToRename = null }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }
}
