@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.koitharu.kotatsu.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.ReadingHistoryItem
import org.koitharu.kotatsu.ui.components.CoverPlaceholder
import org.koitharu.kotatsu.ui.components.EditorialEmptyState
import org.koitharu.kotatsu.ui.components.KotatsuPageHeader

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenMangaDetails: (MangaItem) -> Unit,
    onResumeChapter: (ReadingHistoryItem) -> Unit,
    onNavigateExplore: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    bottomClearance: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
            .testTag("history_screen")
    ) {
        KotatsuPageHeader(
            title = stringResource(R.string.history_title),
            countBadge = historyItems.size,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.padding(horizontal = 16.dp),
            actions = {
                if (historyItems.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { showClearDialog = true },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_history),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.clear_history),
                            maxLines = 1
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (historyItems.isEmpty()) {
            EditorialEmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.history_empty_title),
                subtitle = stringResource(R.string.history_empty_subtitle),
                actionLabel = stringResource(R.string.go_to_explore),
                onActionClick = onNavigateExplore,
                modifier = Modifier.widthIn(max = 640.dp),
                testTagName = "history_empty_state"
            )
        } else {
            // Adaptive 1-column on phones, 2-column on tablets/foldables
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = bottomClearance + 20.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_list")
            ) {
                items(historyItems, key = { it.manga.id }) { item ->
                    val relativeTime = remember(item.updatedAt) {
                        DateUtils.getRelativeTimeSpanString(
                            item.updatedAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    }
                    Card(
                        onClick = { onOpenMangaDetails(item.manga) },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .testTag("history_card_${item.manga.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(116.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                SubcomposeAsyncImage(
                                    model = item.manga.coverUrl,
                                    contentDescription = item.manga.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    loading = { CoverPlaceholder(item.manga.title) },
                                    error = { CoverPlaceholder(item.manga.title) }
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.manga.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeEntry(item.manga.id) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("remove_history_${item.manga.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = stringResource(R.string.remove_history_entry),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(
                                        R.string.history_chapter_page_format,
                                        item.chapterTitle,
                                        item.currentPage + 1,
                                        item.totalPages.coerceAtLeast(1)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = { item.progressFraction },
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = relativeTime,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = { onResumeChapter(item) },
                                        shapes = ButtonDefaults.shapes(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("resume_history_${item.manga.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.resume_reading),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.clear_history_confirm_title)) },
            text = { Text(stringResource(R.string.clear_history_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }
}
