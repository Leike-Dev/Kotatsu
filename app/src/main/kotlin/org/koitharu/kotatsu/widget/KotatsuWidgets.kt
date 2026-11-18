package org.koitharu.kotatsu.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.KotatsuApp
import org.koitharu.kotatsu.MainActivity
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.domain.model.DefaultStartTab
import java.io.File
import kotlin.math.roundToInt

class KotatsuContinueWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        KotatsuWidgetManager.refreshAllWidgetsAsync(context)
    }
}

class KotatsuShelfWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        KotatsuWidgetManager.refreshAllWidgetsAsync(context)
    }
}

object KotatsuWidgetManager {

    const val EXTRA_WIDGET_TAB = "kotatsu_widget_target_tab"
    const val EXTRA_WIDGET_MANGA_ID = "kotatsu_widget_manga_id"
    const val EXTRA_WIDGET_SOURCE_TYPE = "kotatsu_widget_source_type"
    const val EXTRA_WIDGET_CHAPTER_ID = "kotatsu_widget_chapter_id"
    const val EXTRA_WIDGET_PAGE_INDEX = "kotatsu_widget_page_index"

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshAllWidgetsAsync(context: Context) {
        val appCtx = context.applicationContext
        widgetScope.launch {
            runCatching { refreshAllWidgetsNow(appCtx) }
        }
    }

    suspend fun refreshAllWidgetsNow(context: Context) {
        val app = context.applicationContext as? KotatsuApp ?: return
        val repository = app.container.mangaRepository
        val manager = AppWidgetManager.getInstance(context)

        val continueIds = manager.getAppWidgetIds(
            ComponentName(context, KotatsuContinueWidgetProvider::class.java)
        )
        val shelfIds = manager.getAppWidgetIds(
            ComponentName(context, KotatsuShelfWidgetProvider::class.java)
        )

        if (continueIds.isEmpty() && shelfIds.isEmpty()) return

        val history = repository.readingHistoryFlow.first()
        val favorites = repository.allFavoritesFlow.first()
        val analytics = repository.readingAnalyticsFlow.first()

        if (continueIds.isNotEmpty()) {
            val latest = history.firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_kotatsu_continue)

            if (latest != null) {
                val pct = (latest.progressFraction * 100f).roundToInt().coerceIn(2, 100)
                views.setTextViewText(R.id.widget_continue_badge, context.getString(R.string.widget_continue_badge))
                views.setTextViewText(R.id.widget_continue_source, latest.manga.badgeText)
                views.setTextViewText(R.id.widget_continue_title, latest.manga.title)
                views.setTextViewText(
                    R.id.widget_continue_subtitle,
                    context.getString(
                        R.string.history_chapter_page_format,
                        latest.chapterTitle,
                        latest.currentPage + 1,
                        latest.totalPages.coerceAtLeast(1)
                    )
                )
                views.setProgressBar(R.id.widget_continue_progress, 100, pct, false)
                views.setTextViewText(R.id.widget_continue_cta, context.getString(R.string.widget_continue_cta_read))

                val localCoverBmp = decodeRoundedLocalCover(latest.manga.coverUrl)
                if (localCoverBmp != null) {
                    views.setImageViewBitmap(R.id.widget_continue_cover, localCoverBmp)
                    views.setViewPadding(R.id.widget_continue_cover, 0, 0, 0, 0)
                } else {
                    views.setImageViewResource(R.id.widget_continue_cover, R.drawable.ic_widget_book)
                }

                val resumeIntent = buildLaunchIntent(context).apply {
                    putExtra(EXTRA_WIDGET_MANGA_ID, latest.manga.id)
                    putExtra(EXTRA_WIDGET_SOURCE_TYPE, latest.manga.sourceType.name)
                    putExtra(EXTRA_WIDGET_CHAPTER_ID, latest.chapterId)
                    putExtra(EXTRA_WIDGET_PAGE_INDEX, latest.currentPage)
                }
                val pi = buildPendingIntent(context, 4101, resumeIntent)
                views.setOnClickPendingIntent(R.id.widget_continue_root, pi)
                views.setOnClickPendingIntent(R.id.widget_continue_cta, pi)
            } else {
                views.setTextViewText(R.id.widget_continue_badge, context.getString(R.string.widget_continue_badge))
                views.setTextViewText(R.id.widget_continue_source, "KOTATSU")
                views.setTextViewText(R.id.widget_continue_title, context.getString(R.string.widget_continue_empty_title))
                views.setTextViewText(R.id.widget_continue_subtitle, context.getString(R.string.widget_continue_empty_sub))
                views.setProgressBar(R.id.widget_continue_progress, 100, 0, false)
                views.setTextViewText(R.id.widget_continue_cta, context.getString(R.string.widget_continue_cta_explore))
                views.setImageViewResource(R.id.widget_continue_cover, R.drawable.ic_widget_book)

                val exploreIntent = buildLaunchIntent(context).apply {
                    putExtra(EXTRA_WIDGET_TAB, DefaultStartTab.EXPLORE.name)
                }
                val pi = buildPendingIntent(context, 4102, exploreIntent)
                views.setOnClickPendingIntent(R.id.widget_continue_root, pi)
                views.setOnClickPendingIntent(R.id.widget_continue_cta, pi)
            }

            continueIds.forEach { id -> manager.updateAppWidget(id, views) }
        }

        if (shelfIds.isNotEmpty()) {
            val views = RemoteViews(context.packageName, R.layout.widget_kotatsu_shelf)
            val totalNewDelta = favorites.sumOf { it.newChaptersDelta.coerceAtLeast(0) }
            val badgeText = if (totalNewDelta > 0) {
                context.getString(R.string.badge_new_chapters_format, totalNewDelta)
            } else {
                context.getString(R.string.widget_shelf_up_to_date)
            }

            views.setTextViewText(R.id.widget_shelf_updates_badge, badgeText)
            views.setTextViewText(R.id.widget_shelf_fav_count, favorites.size.toString())
            views.setTextViewText(R.id.widget_shelf_history_count, analytics.inProgressWorksCount.toString())
            views.setTextViewText(R.id.widget_shelf_chapters_count, analytics.completedChaptersCount.toString())

            val recentNames = favorites.take(3).joinToString(" • ") { it.title }
            views.setTextViewText(
                R.id.widget_shelf_recent_line,
                if (recentNames.isNotBlank()) {
                    recentNames
                } else {
                    context.getString(R.string.widget_shelf_empty_hint)
                }
            )

            val favPi = buildPendingIntent(
                context,
                4201,
                buildLaunchIntent(context).putExtra(EXTRA_WIDGET_TAB, DefaultStartTab.FAVORITES.name)
            )
            val expPi = buildPendingIntent(
                context,
                4202,
                buildLaunchIntent(context).putExtra(EXTRA_WIDGET_TAB, DefaultStartTab.EXPLORE.name)
            )
            views.setOnClickPendingIntent(R.id.widget_shelf_root, favPi)
            views.setOnClickPendingIntent(R.id.widget_shelf_btn_favorites, favPi)
            views.setOnClickPendingIntent(R.id.widget_shelf_btn_explore, expPi)

            shelfIds.forEach { id -> manager.updateAppWidget(id, views) }
        }
    }

    fun requestPinContinueWidget(context: Context): Boolean {
        return requestPinProvider(context, ComponentName(context, KotatsuContinueWidgetProvider::class.java))
    }

    fun requestPinShelfWidget(context: Context): Boolean {
        return requestPinProvider(context, ComponentName(context, KotatsuShelfWidgetProvider::class.java))
    }

    private fun requestPinProvider(context: Context, provider: ComponentName): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return runCatching {
            manager.requestPinAppWidget(provider, null, null)
        }.getOrDefault(false)
    }

    private fun buildLaunchIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    private fun buildPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    private fun decodeRoundedLocalCover(coverUrl: String): Bitmap? {
        if (coverUrl.isBlank()) return null
        val path = when {
            coverUrl.startsWith("file://") -> coverUrl.removePrefix("file://")
            coverUrl.startsWith("/") -> coverUrl
            else -> return null
        }
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 220 || bounds.outHeight / sample > 320) {
                sample *= 2
            }
            val raw = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null
            val out = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            val radius = raw.width * 0.16f
            canvas.drawRoundRect(
                RectF(0f, 0f, raw.width.toFloat(), raw.height.toFloat()),
                radius,
                radius,
                paint
            )
            if (raw != out) raw.recycle()
            out
        }.getOrNull()
    }
}
