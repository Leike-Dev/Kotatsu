@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.koitharu.kotatsu.ui.bookmarks

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.koitharu.kotatsu.domain.model.PageBookmarkItem
import org.koitharu.kotatsu.ui.components.CoverPlaceholder
import org.koitharu.kotatsu.ui.components.EditorialEmptyState
import org.koitharu.kotatsu.ui.components.KotatsuPageHeader

@Composable
fun BookmarksScreen(
    viewModel: BookmarksViewModel,
    onOpenBookmarkedPage: (PageBookmarkItem) -> Unit,
    onNavigateExplore: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    bottomClearance: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
            .testTag("bookmarks_screen")
    ) {
        KotatsuPageHeader(
            title = stringResource(R.string.bookmarks_title),
            subtitle = stringResource(R.string.bookmarks_section),
            countBadge = bookmarks.size,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (bookmarks.isEmpty()) {
            EditorialEmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = stringResource(R.string.bookmarks_empty_title),
                subtitle = stringResource(R.string.bookmarks_empty_subtitle),
                actionLabel = stringResource(R.string.go_to_explore),
                onActionClick = onNavigateExplore,
                modifier = Modifier.widthIn(max = 640.dp),
                testTagName = "bookmarks_empty_state"
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 330.dp),
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
                    .testTag("bookmarks_grid")
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Card(
                        onClick = { onOpenBookmarkedPage(bookmark) },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .testTag("bookmark_card_${bookmark.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(82.dp)
                                    .height(114.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                SubcomposeAsyncImage(
                                    model = bookmark.previewUrl.ifBlank { bookmark.manga.coverUrl },
                                    contentDescription = bookmark.manga.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    loading = { CoverPlaceholder(bookmark.manga.title) },
                                    error = { CoverPlaceholder(bookmark.manga.title) }
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "Pág. ${bookmark.pageIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bookmark.manga.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.history_chapter_page_format,
                                        bookmark.chapterTitle,
                                        bookmark.pageIndex + 1,
                                        bookmark.totalPages.coerceAtLeast(1)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { viewModel.removeBookmark(bookmark.id) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("delete_bookmark_${bookmark.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.remove_bookmark),
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
