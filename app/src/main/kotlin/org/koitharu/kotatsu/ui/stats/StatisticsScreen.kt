@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package org.koitharu.kotatsu.ui.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.domain.model.DailyActivityPoint
import org.koitharu.kotatsu.domain.model.GenreStatItem
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaProgressStatItem
import org.koitharu.kotatsu.domain.model.ReadingAnalytics
import org.koitharu.kotatsu.domain.model.SourceStatItem
import org.koitharu.kotatsu.ui.components.CoverPlaceholder
import org.koitharu.kotatsu.ui.components.EditorialEmptyState

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit,
    onOpenManga: (MangaItem) -> Unit,
    onNavigateExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    var animateEntrance by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animateEntrance = true
    }

    val chartEntranceProgress by animateFloatAsState(
        targetValue = if (animateEntrance) 1f else 0f,
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "statsChartEntrance"
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("statistics_screen")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val isWideTablet = maxWidth >= 760.dp

            if (!analytics.hasAnyActivity) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    StatisticsTopBar(onBack = onBack)
                    Spacer(modifier = Modifier.height(24.dp))
                    EditorialEmptyState(
                        icon = Icons.Outlined.BarChart,
                        title = stringResource(R.string.stats_empty_title),
                        subtitle = stringResource(R.string.stats_empty_sub),
                        actionLabel = stringResource(R.string.go_to_explore),
                        onActionClick = onNavigateExplore,
                        modifier = Modifier.widthIn(max = 620.dp),
                        testTagName = "statistics_empty_state"
                    )
                }
            } else if (isWideTablet) {
                // Canonical Two-Pane Tablet Analytics Dashboard
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    StatisticsTopBar(onBack = onBack)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 28.dp)
                        ) {
                            ReaderHeroOverviewCard(
                                analytics = analytics,
                                entranceFraction = chartEntranceProgress
                            )
                            WeeklyActivityChartCard(
                                points = analytics.dailyActivity,
                                entranceFraction = chartEntranceProgress
                            )
                            if (analytics.sourceBreakdown.isNotEmpty()) {
                                SourceDistributionCard(
                                    sources = analytics.sourceBreakdown,
                                    entranceFraction = chartEntranceProgress
                                )
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 28.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            if (analytics.topGenres.isNotEmpty()) {
                                item {
                                    TopGenresCard(
                                        genres = analytics.topGenres,
                                        entranceFraction = chartEntranceProgress
                                    )
                                }
                            }
                            if (analytics.mangaProgressList.isNotEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.stats_active_progress_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                items(analytics.mangaProgressList, key = { it.manga.id }) { item ->
                                    MangaProgressStatRow(
                                        item = item,
                                        onClick = { onOpenManga(item.manga) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Compact Expressive Mobile Dashboard
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = 36.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        StatisticsTopBar(onBack = onBack)
                    }
                    item {
                        ReaderHeroOverviewCard(
                            analytics = analytics,
                            entranceFraction = chartEntranceProgress
                        )
                    }
                    item {
                        WeeklyActivityChartCard(
                            points = analytics.dailyActivity,
                            entranceFraction = chartEntranceProgress
                        )
                    }
                    if (analytics.topGenres.isNotEmpty()) {
                        item {
                            TopGenresCard(
                                genres = analytics.topGenres,
                                entranceFraction = chartEntranceProgress
                            )
                        }
                    }
                    if (analytics.sourceBreakdown.isNotEmpty()) {
                        item {
                            SourceDistributionCard(
                                sources = analytics.sourceBreakdown,
                                entranceFraction = chartEntranceProgress
                            )
                        }
                    }
                    if (analytics.mangaProgressList.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.stats_active_progress_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(analytics.mangaProgressList, key = { it.manga.id }) { item ->
                            MangaProgressStatRow(
                                item = item,
                                onClick = { onOpenManga(item.manga) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsTopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.testTag("stats_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.back_action)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.stats_screen_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.stats_screen_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReaderHeroOverviewCard(
    analytics: ReadingAnalytics,
    entranceFraction: Float
) {
    val burstShape = MaterialShapes.SoftBurst.toShape()
    val cookieShape = MaterialShapes.Cookie9Sided.toShape()

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_hero_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .size(156.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 34.dp, y = (-32).dp)
                    .clip(burstShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-22).dp, y = 22.dp)
                    .clip(cookieShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        text = stringResource(R.string.stats_hero_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatKpiTile(
                        icon = Icons.Outlined.CheckCircle,
                        value = analytics.completedChaptersCount.toString(),
                        label = stringResource(R.string.stats_chapters_completed),
                        modifier = Modifier.weight(1f)
                    )
                    StatKpiTile(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        value = analytics.estimatedPagesRead.toString(),
                        label = stringResource(R.string.stats_pages_read),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatKpiTile(
                        icon = Icons.Outlined.AutoStories,
                        value = analytics.inProgressWorksCount.toString(),
                        label = stringResource(R.string.stats_works_in_progress),
                        modifier = Modifier.weight(1f)
                    )
                    StatKpiTile(
                        icon = Icons.Outlined.DoneAll,
                        value = analytics.caughtUpWorksCount.toString(),
                        label = stringResource(R.string.stats_works_caught_up),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (analytics.shelfTotalChaptersSum > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val animatedCompletion = (analytics.shelfCompletionFraction * entranceFraction).coerceIn(0f, 1f)
                    val pct = (analytics.shelfCompletionFraction * 100).toInt()
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.stats_shelf_completion_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { animatedCompletion },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatKpiTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WeeklyActivityChartCard(
    points: List<DailyActivityPoint>,
    entranceFraction: Float
) {
    val primaryBarColor = MaterialTheme.colorScheme.primary
    val secondaryBarColor = MaterialTheme.colorScheme.secondary
    val trackBarColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    val maxDayCount = remember(points) {
        (points.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_weekly_chart_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_weekly_activity_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.stats_weekly_activity_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Value counts row above the Canvas bars
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                points.forEach { point ->
                    Text(
                        text = if (point.count > 0) point.count.toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (point.isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (point.isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Responsive DrawScope Canvas Bar Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
            ) {
                if (points.isEmpty()) return@Canvas
                val slotWidth = size.width / points.size.toFloat()
                val barWidth = (slotWidth * 0.48f).coerceAtMost(36.dp.toPx())
                val cornerPx = barWidth / 2f
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
                    val y = size.height * ratio
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }

                points.forEachIndexed { idx, point ->
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f

                    // Subtle background capsule track
                    drawRoundRect(
                        color = trackBarColor.copy(alpha = 0.55f),
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(cornerPx, cornerPx)
                    )

                    val rawFraction = if (point.count > 0) {
                        (point.count.toFloat() / maxDayCount.toFloat()).coerceIn(0.14f, 1f)
                    } else {
                        0.05f
                    }
                    val barHeight = (size.height * rawFraction * entranceFraction)
                        .coerceAtLeast(6.dp.toPx())
                    val top = size.height - barHeight

                    drawRoundRect(
                        color = when {
                            point.count == 0 -> trackBarColor
                            point.isToday -> primaryBarColor
                            else -> secondaryBarColor
                        },
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(cornerPx, cornerPx)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Weekday labels below Canvas
            Row(modifier = Modifier.fillMaxWidth()) {
                points.forEach { point ->
                    Text(
                        text = point.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (point.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (point.isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopGenresCard(
    genres: List<GenreStatItem>,
    entranceFraction: Float
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_genres_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_top_genres_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            genres.forEachIndexed { idx, genre ->
                val barColor = when (idx % 3) {
                    0 -> MaterialTheme.colorScheme.primary
                    1 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = genre.genreName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.manga_works_count,
                                genre.mangaCount,
                                genre.mangaCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { (genre.fractionOfMax * entranceFraction).coerceIn(0f, 1f) },
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceDistributionCard(
    sources: List<SourceStatItem>,
    entranceFraction: Float
) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primaryContainer
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_sources_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_sources_breakdown_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Segmented proportional bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(CircleShape)
            ) {
                sources.forEachIndexed { idx, src ->
                    val segColor = palette[idx % palette.size]
                    val weightVal = (src.fraction * entranceFraction).coerceAtLeast(0.06f)
                    Box(
                        modifier = Modifier
                            .weight(weightVal)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(segColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sources.forEachIndexed { idx, src ->
                    val dotColor = palette[idx % palette.size]
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${src.sourceLabel} • ${src.count}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaProgressStatRow(
    item: MangaProgressStatItem,
    onClick: () -> Unit
) {
    val pct = (item.completionFraction * 100).toInt()
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_manga_progress_${item.manga.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(82.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (item.manga.coverUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = item.manga.coverUrl,
                        contentDescription = item.manga.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { CoverPlaceholder(item.manga.title) },
                        error = { CoverPlaceholder(item.manga.title) }
                    )
                } else {
                    CoverPlaceholder(item.manga.title)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.manga.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (pct >= 100) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pct >= 100) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(
                        R.string.stats_chapters_ratio_format,
                        item.readChapters,
                        item.totalChapters
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { item.completionFraction },
                    color = if (pct >= 100) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}
