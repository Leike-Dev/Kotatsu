package org.koitharu.kotatsu.data.local.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.data.local.db.CompletedChapterEntity
import org.koitharu.kotatsu.data.local.db.CustomSourceEntity
import org.koitharu.kotatsu.data.local.db.FavoriteCategoryEntity
import org.koitharu.kotatsu.data.local.db.KotatsuDao
import org.koitharu.kotatsu.data.local.db.MangaCategoryCrossRef
import org.koitharu.kotatsu.data.local.db.MangaEntity
import org.koitharu.kotatsu.data.local.db.PageBookmarkEntity
import org.koitharu.kotatsu.data.local.db.ReadingHistoryEntity
import org.koitharu.kotatsu.data.local.prefs.UserPreferencesRepository
import org.koitharu.kotatsu.domain.model.AppThemeMode
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.ReaderMode
import org.koitharu.kotatsu.domain.model.ReaderScaleMode
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

@Serializable
data class KotatsuBackupPayload(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val mangas: List<MangaEntity> = emptyList(),
    val categories: List<FavoriteCategoryEntity> = emptyList(),
    val categoryRefs: List<MangaCategoryCrossRef> = emptyList(),
    val history: List<ReadingHistoryEntity> = emptyList(),
    val completedChapters: List<CompletedChapterEntity> = emptyList(),
    val bookmarks: List<PageBookmarkEntity> = emptyList(),
    val customSources: List<CustomSourceEntity> = emptyList(),
    val preferences: Map<String, String> = emptyMap()
)

class BackupManager(
    private val context: Context,
    private val dao: KotatsuDao,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    suspend fun exportBackupToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val payload = KotatsuBackupPayload(
            mangas = dao.getAllMangasList().filter { it.sourceType != "LOCAL_CBZ" },
            categories = dao.getAllCategoriesList(),
            categoryRefs = dao.getAllCategoryRefsList(),
            history = dao.getAllHistoryList(),
            completedChapters = dao.getAllCompletedChaptersList(),
            bookmarks = dao.getAllBookmarksList(),
            customSources = dao.getAllCustomSourcesList(),
            preferences = preferencesRepository.exportSimpleMap()
        )
        val encoded = json.encodeToString(payload)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(encoded.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IOException("Não foi possível gravar o arquivo de backup no local selecionado.")

        payload.mangas.size + payload.bookmarks.size + payload.customSources.size
    }

    /**
     * Restores BOTH:
     * 1) Kotatsu Compose JSON backups (.json v1 & v2)
     * 2) Official Legacy Kotatsu ZIP backups (.bak / .bk.zip / .zip containing categories, favourites, history, bookmarks, settings)
     */
    suspend fun restoreBackupFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val rawStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Não foi possível abrir o arquivo de backup selecionado.")

        rawStream.use { stream ->
            val buffered = BufferedInputStream(stream)
            buffered.mark(8)
            val b0 = buffered.read()
            val b1 = buffered.read()
            buffered.reset()

            // ZIP archive magic header: 'P' (0x50) 'K' (0x4B) — used by official Kotatsu .bak / .bk.zip backups
            if (b0 == 0x50 && b1 == 0x4B) {
                restoreFromZipArchive(buffered)
            } else {
                val rawText = buffered.bufferedReader(Charsets.UTF_8).readText()
                restoreFromJsonText(rawText)
            }
        }
    }

    private suspend fun restoreFromJsonText(rawText: String): Int {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            throw IOException("O arquivo de backup está vazio.")
        }
        val payload = json.decodeFromString<KotatsuBackupPayload>(trimmed)
        if (payload.mangas.isNotEmpty()) dao.upsertMangas(payload.mangas)
        if (payload.categories.isNotEmpty()) dao.insertCategories(payload.categories)
        if (payload.categoryRefs.isNotEmpty()) dao.insertMangaCategoryRefs(payload.categoryRefs)
        if (payload.history.isNotEmpty()) dao.upsertHistoryList(payload.history)
        if (payload.completedChapters.isNotEmpty()) dao.insertCompletedChapters(payload.completedChapters)
        if (payload.bookmarks.isNotEmpty()) dao.insertBookmarks(payload.bookmarks)
        if (payload.customSources.isNotEmpty()) dao.insertCustomSources(payload.customSources)
        if (payload.preferences.isNotEmpty()) {
            preferencesRepository.restoreFromSimpleMap(payload.preferences)
        }
        return payload.mangas.size + payload.bookmarks.size + payload.customSources.size
    }

    /**
     * Parses official Kotatsu .bak / .bk.zip archives (`categories`, `favourites`, `history`, `bookmarks`, `settings`).
     */
    private suspend fun restoreFromZipArchive(bufferedStream: BufferedInputStream): Int {
        val entriesText = linkedMapOf<String, String>()
        ZipInputStream(bufferedStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val key = entry.name
                        .substringAfterLast('/')
                        .substringBeforeLast('.')
                        .lowercase()
                    val text = zis.readBytes().toString(Charsets.UTF_8)
                    entriesText[key] = text
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (entriesText.isEmpty()) {
            throw IOException("O arquivo .bak/.zip não contém seções de backup válidas.")
        }

        // Check if this ZIP wraps a Kotatsu Compose JSON payload
        val composeJsonCandidate = entriesText.values.firstOrNull {
            it.trimStart().startsWith("{") && it.contains("\"mangas\"") && it.contains("\"version\"")
        }
        if (composeJsonCandidate != null) {
            return restoreFromJsonText(composeJsonCandidate)
        }

        val mangasById = linkedMapOf<String, MangaEntity>()
        val legacyNumericIdToMangaId = mutableMapOf<Long, String>()
        val categoriesToInsert = mutableListOf<FavoriteCategoryEntity>()
        val refsToInsert = mutableListOf<MangaCategoryCrossRef>()
        val historyToInsert = mutableListOf<ReadingHistoryEntity>()
        val bookmarksToInsert = mutableListOf<PageBookmarkEntity>()
        val discoveredCustomSources = linkedMapOf<String, CustomSourceEntity>()

        // Existing custom source base URLs to avoid duplicates
        val existingBaseUrls = dao.getAllCustomSourcesList()
            .map { it.baseUrl.trimEnd('/').lowercase() }
            .toMutableSet()

        // 1. Official Kotatsu `categories`
        entriesText["categories"]?.let { raw ->
            parseFlexibleJsonArray(raw).forEachIndexed { idx, obj ->
                val id = obj.optLong("id", (idx + 1).toLong())
                val title = obj.optString("title").ifBlank { obj.optString("name", "Categoria $id") }
                val sortKey = obj.optInt("sort_key", idx)
                val createdAt = obj.optLong("created_at", System.currentTimeMillis())
                categoriesToInsert.add(
                    FavoriteCategoryEntity(
                        id = id,
                        name = title,
                        sortOrder = sortKey,
                        createdAt = createdAt
                    )
                )
            }
        }

        // 2. Official Kotatsu `favourites`
        entriesText["favourites"]?.let { raw ->
            parseFlexibleJsonArray(raw).forEach { obj ->
                val mangaObj = obj.optJSONObject("manga")
                val parsedManga = mangaObj?.let {
                    parseLegacyKotatsuManga(it, existingBaseUrls, discoveredCustomSources)
                }
                if (parsedManga != null) {
                    mangasById[parsedManga.id] = parsedManga
                    val legacyId = mangaObj.optLong("id", obj.optLong("manga_id", 0L))
                    if (legacyId != 0L) legacyNumericIdToMangaId[legacyId] = parsedManga.id
                }
                val resolvedMangaId = parsedManga?.id
                    ?: legacyNumericIdToMangaId[obj.optLong("manga_id", 0L)]
                val catId = obj.optLong("category_id", 1L)
                val createdAt = obj.optLong("created_at", System.currentTimeMillis())
                if (!resolvedMangaId.isNullOrBlank()) {
                    refsToInsert.add(
                        MangaCategoryCrossRef(
                            mangaId = resolvedMangaId,
                            categoryId = catId,
                            addedAt = createdAt
                        )
                    )
                }
            }
        }

        // 3. Official Kotatsu `history`
        entriesText["history"]?.let { raw ->
            parseFlexibleJsonArray(raw).forEach { obj ->
                val mangaObj = obj.optJSONObject("manga")
                val parsedManga = mangaObj?.let {
                    parseLegacyKotatsuManga(it, existingBaseUrls, discoveredCustomSources)
                }
                if (parsedManga != null) {
                    mangasById[parsedManga.id] = parsedManga
                    val legacyId = mangaObj.optLong("id", obj.optLong("manga_id", 0L))
                    if (legacyId != 0L) legacyNumericIdToMangaId[legacyId] = parsedManga.id
                }
                val resolvedMangaId = parsedManga?.id
                    ?: legacyNumericIdToMangaId[obj.optLong("manga_id", 0L)]
                if (!resolvedMangaId.isNullOrBlank()) {
                    val chapterId = obj.opt("chapter_id")?.toString().orEmpty().ifBlank { "ch_1" }
                    val page = obj.optInt("page", 0).coerceAtLeast(0)
                    val percent = obj.optDouble("percent", 0.0).toFloat().coerceIn(0f, 1f)
                    val estimatedTotal = if (percent > 0.02f) {
                        ((page + 1) / percent).roundToInt().coerceAtLeast(page + 1)
                    } else {
                        (page + 1).coerceAtLeast(20)
                    }
                    val updatedAt = obj.optLong("updated_at", obj.optLong("created_at", System.currentTimeMillis()))
                    historyToInsert.add(
                        ReadingHistoryEntity(
                            mangaId = resolvedMangaId,
                            chapterId = chapterId,
                            chapterTitle = "Progresso restaurado",
                            currentPage = page,
                            totalPages = estimatedTotal,
                            updatedAt = updatedAt
                        )
                    )
                }
            }
        }

        // 4. Official Kotatsu `bookmarks`
        entriesText["bookmarks"]?.let { raw ->
            parseFlexibleJsonArray(raw).forEach { obj ->
                val mangaObj = obj.optJSONObject("manga")
                val parsedManga = mangaObj?.let {
                    parseLegacyKotatsuManga(it, existingBaseUrls, discoveredCustomSources)
                }
                if (parsedManga != null) {
                    mangasById[parsedManga.id] = parsedManga
                    val legacyId = mangaObj.optLong("id", 0L)
                    if (legacyId != 0L) legacyNumericIdToMangaId[legacyId] = parsedManga.id
                }
                val nestedArray = obj.optJSONArray("bookmarks")
                if (nestedArray != null) {
                    for (i in 0 until nestedArray.length()) {
                        val bm = nestedArray.optJSONObject(i) ?: continue
                        val resolvedId = parsedManga?.id
                            ?: legacyNumericIdToMangaId[bm.optLong("manga_id", 0L)]
                            ?: continue
                        val pageIdx = bm.optInt("page", 0).coerceAtLeast(0)
                        val chId = bm.opt("chapter_id")?.toString().orEmpty().ifBlank { "ch_1" }
                        val imgUrl = bm.optString("image_url").ifBlank { parsedManga?.coverUrl.orEmpty() }
                        bookmarksToInsert.add(
                            PageBookmarkEntity(
                                mangaId = resolvedId,
                                chapterId = chId,
                                chapterTitle = "Página salva",
                                pageIndex = pageIdx,
                                totalPages = (pageIdx + 1).coerceAtLeast(1),
                                previewUrl = imgUrl,
                                createdAt = bm.optLong("created_at", System.currentTimeMillis())
                            )
                        )
                    }
                } else {
                    val resolvedId = parsedManga?.id
                        ?: legacyNumericIdToMangaId[obj.optLong("manga_id", 0L)]
                    if (!resolvedId.isNullOrBlank()) {
                        val pageIdx = obj.optInt("page", 0).coerceAtLeast(0)
                        val chId = obj.opt("chapter_id")?.toString().orEmpty().ifBlank { "ch_1" }
                        bookmarksToInsert.add(
                            PageBookmarkEntity(
                                mangaId = resolvedId,
                                chapterId = chId,
                                chapterTitle = "Página salva",
                                pageIndex = pageIdx,
                                totalPages = (pageIdx + 1).coerceAtLeast(1),
                                previewUrl = obj.optString("image_url"),
                                createdAt = obj.optLong("created_at", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        }

        // 5. Official Kotatsu `settings`
        entriesText["settings"]?.let { raw ->
            runCatching {
                val settingsObj = if (raw.trimStart().startsWith("[")) {
                    JSONArray(raw).optJSONObject(0)
                } else {
                    JSONObject(raw)
                }
                if (settingsObj != null) {
                    val mappedPrefs = mutableMapOf<String, String>()
                    if (settingsObj.has("amoled_theme")) {
                        mappedPrefs["amoledDark"] = settingsObj.optBoolean("amoled_theme").toString()
                    }
                    if (settingsObj.has("dynamic_theme")) {
                        mappedPrefs["dynamicColors"] = settingsObj.optBoolean("dynamic_theme").toString()
                    }
                    when (settingsObj.optString("theme", "").uppercase()) {
                        "DARK", "2" -> mappedPrefs["themeMode"] = AppThemeMode.DARK.name
                        "LIGHT", "1" -> mappedPrefs["themeMode"] = AppThemeMode.LIGHT.name
                        "SYSTEM", "-1", "0" -> mappedPrefs["themeMode"] = AppThemeMode.SYSTEM.name
                    }
                    when (settingsObj.optString("reading_mode", "").uppercase()) {
                        "WEBTOON" -> mappedPrefs["defaultReaderMode"] = ReaderMode.WEBTOON.name
                        "REVERSED", "RTL" -> mappedPrefs["defaultReaderMode"] = ReaderMode.MANGA_RTL.name
                        "VERTICAL" -> mappedPrefs["defaultReaderMode"] = ReaderMode.VERTICAL_PAGED.name
                        "STANDARD", "LTR" -> mappedPrefs["defaultReaderMode"] = ReaderMode.STANDARD_LTR.name
                    }
                    when (settingsObj.optString("scale_mode", "").uppercase()) {
                        "FIT_WIDTH", "WIDTH" -> mappedPrefs["readerScaleMode"] = ReaderScaleMode.FIT_WIDTH.name
                        "FIT_HEIGHT", "HEIGHT" -> mappedPrefs["readerScaleMode"] = ReaderScaleMode.FIT_HEIGHT.name
                        "CROP" -> mappedPrefs["readerScaleMode"] = ReaderScaleMode.CROP.name
                        "FIT" -> mappedPrefs["readerScaleMode"] = ReaderScaleMode.FIT_SCREEN.name
                    }
                    if (settingsObj.has("keep_screen_on")) {
                        mappedPrefs["keepScreenOn"] = settingsObj.optBoolean("keep_screen_on").toString()
                    }
                    if (settingsObj.has("volume_keys")) {
                        mappedPrefs["volumeKeysNavigation"] = settingsObj.optBoolean("volume_keys").toString()
                    }
                    if (mappedPrefs.isNotEmpty()) {
                        preferencesRepository.restoreFromSimpleMap(mappedPrefs)
                    }
                }
            }
        }

        // Ensure default category exists if favourites reference a category not in `categories`
        if (categoriesToInsert.isEmpty() && refsToInsert.isNotEmpty()) {
            val distinctCats = refsToInsert.map { it.categoryId }.distinct()
            distinctCats.forEachIndexed { idx, catId ->
                categoriesToInsert.add(
                    FavoriteCategoryEntity(
                        id = catId,
                        name = "Favoritos Importados ${idx + 1}",
                        sortOrder = idx
                    )
                )
            }
        }

        if (categoriesToInsert.isNotEmpty()) dao.insertCategories(categoriesToInsert)
        if (mangasById.isNotEmpty()) dao.upsertMangas(mangasById.values.toList())
        if (refsToInsert.isNotEmpty()) dao.insertMangaCategoryRefs(refsToInsert)
        if (historyToInsert.isNotEmpty()) dao.upsertHistoryList(historyToInsert)
        if (bookmarksToInsert.isNotEmpty()) dao.insertBookmarks(bookmarksToInsert)
        if (discoveredCustomSources.isNotEmpty()) {
            dao.insertCustomSources(discoveredCustomSources.values.toList())
        }

        val totalImported = mangasById.size + bookmarksToInsert.size + discoveredCustomSources.size
        if (totalImported == 0 && categoriesToInsert.isEmpty()) {
            throw IOException("Nenhum registro compatível encontrado no arquivo de backup.")
        }
        return totalImported.coerceAtLeast(categoriesToInsert.size)
    }

    /**
     * One-time non-destructive migration if the device has an existing `kotatsu-db` SQLite database
     * from an older official Kotatsu installation.
     */
    suspend fun migrateLegacyKotatsuSqliteIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (preferencesRepository.isLegacyDbImported()) return@withContext 0
        val legacyDbFile: File = context.getDatabasePath("kotatsu-db")
        if (!legacyDbFile.exists() || !legacyDbFile.canRead()) {
            preferencesRepository.markLegacyDbImported()
            return@withContext 0
        }

        var importedCount = 0
        runCatching {
            SQLiteDatabase.openDatabase(
                legacyDbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                val legacyIdMap = mutableMapOf<Long, String>()
                val mangas = mutableListOf<MangaEntity>()

                // 1. Read `manga` table
                runCatching {
                    db.rawQuery(
                        "SELECT manga_id, title, author, url, public_url, cover_url, state, source FROM manga",
                        null
                    ).use { c ->
                        while (c.moveToNext()) {
                            val numId = c.getLong(0)
                            val title = c.getString(1).orEmpty()
                            val author = c.getString(2).orEmpty()
                            val url = c.getString(3).orEmpty()
                            val pubUrl = c.getString(4).orEmpty()
                            val cover = c.getString(5).orEmpty()
                            val state = c.getString(6).orEmpty().lowercase()
                            val source = c.getString(7).orEmpty()

                            val uuidMatch = MANGADEX_UUID_REGEX.find("$pubUrl $url")?.value
                            val isMangaDex = source.contains("MANGADEX", ignoreCase = true) && uuidMatch != null
                            val resolvedId = if (isMangaDex) {
                                uuidMatch!!
                            } else {
                                "legacy_${source.lowercase()}_$numId"
                            }
                            legacyIdMap[numId] = resolvedId
                            mangas.add(
                                MangaEntity(
                                    id = resolvedId,
                                    sourceType = if (isMangaDex) {
                                        MangaSourceType.ONLINE_MANGADEX.name
                                    } else {
                                        MangaSourceType.CUSTOM_WEB.name
                                    },
                                    title = title.ifBlank { "Obra #$numId" },
                                    author = author,
                                    description = "",
                                    coverUrl = cover,
                                    status = state,
                                    tagsSerialized = "",
                                    localPath = null,
                                    sourceUrl = pubUrl.ifBlank { if (url.startsWith("http")) url else null },
                                    sourceLabel = source.take(16)
                                )
                            )
                        }
                    }
                }

                // 2. Read `favourite_categories`
                val categories = mutableListOf<FavoriteCategoryEntity>()
                runCatching {
                    db.rawQuery(
                        "SELECT category_id, title, sort_key, created_at FROM favourite_categories",
                        null
                    ).use { c ->
                        while (c.moveToNext()) {
                            categories.add(
                                FavoriteCategoryEntity(
                                    id = c.getLong(0),
                                    name = c.getString(1).orEmpty().ifBlank { "Favoritos" },
                                    sortOrder = c.getInt(2),
                                    createdAt = c.getLong(3)
                                )
                            )
                        }
                    }
                }

                // 3. Read `favourites`
                val refs = mutableListOf<MangaCategoryCrossRef>()
                runCatching {
                    db.rawQuery(
                        "SELECT manga_id, category_id, created_at FROM favourites WHERE deleted_at = 0",
                        null
                    ).use { c ->
                        while (c.moveToNext()) {
                            val mId = legacyIdMap[c.getLong(0)] ?: continue
                            refs.add(
                                MangaCategoryCrossRef(
                                    mangaId = mId,
                                    categoryId = c.getLong(1),
                                    addedAt = c.getLong(2)
                                )
                            )
                        }
                    }
                }

                // 4. Read `history`
                val history = mutableListOf<ReadingHistoryEntity>()
                runCatching {
                    db.rawQuery(
                        "SELECT manga_id, chapter_id, page, percent, updated_at FROM history WHERE deleted_at = 0",
                        null
                    ).use { c ->
                        while (c.moveToNext()) {
                            val mId = legacyIdMap[c.getLong(0)] ?: continue
                            val chId = c.getLong(1).toString()
                            val page = c.getInt(2).coerceAtLeast(0)
                            val pct = c.getFloat(3).coerceIn(0f, 1f)
                            val total = if (pct > 0.02f) ((page + 1) / pct).roundToInt().coerceAtLeast(page + 1) else 20
                            history.add(
                                ReadingHistoryEntity(
                                    mangaId = mId,
                                    chapterId = chId,
                                    chapterTitle = "Progresso importado",
                                    currentPage = page,
                                    totalPages = total,
                                    updatedAt = c.getLong(4)
                                )
                            )
                        }
                    }
                }

                if (categories.isNotEmpty()) dao.insertCategories(categories)
                if (mangas.isNotEmpty()) dao.upsertMangas(mangas)
                if (refs.isNotEmpty()) dao.insertMangaCategoryRefs(refs)
                if (history.isNotEmpty()) dao.upsertHistoryList(history)
                importedCount = mangas.size
            }
        }
        preferencesRepository.markLegacyDbImported()
        importedCount
    }

    private fun parseFlexibleJsonArray(raw: String): List<JSONObject> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching {
            val out = mutableListOf<JSONObject>()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let(out::add)
                }
            } else if (trimmed.startsWith("{")) {
                out.add(JSONObject(trimmed))
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun parseLegacyKotatsuManga(
        obj: JSONObject,
        existingBaseUrls: MutableSet<String>,
        discoveredSources: MutableMap<String, CustomSourceEntity>
    ): MangaEntity? {
        val title = obj.optString("title").trim()
        if (title.isBlank()) return null

        val legacyNumericId = obj.optLong("id", title.hashCode().toLong())
        val rawUrl = obj.optString("url").trim()
        val publicUrl = obj.optString("public_url").trim()
        val bestHttpUrl = when {
            publicUrl.startsWith("http") -> publicUrl
            rawUrl.startsWith("http") -> rawUrl
            else -> ""
        }
        val coverUrl = obj.optString("large_cover_url").takeIf { it.isNotBlank() }
            ?: obj.optString("cover_url")
        val author = obj.optString("author")
        val sourceRaw = obj.optString("source", "WEB").uppercase()
        val status = when (obj.optString("state", "").uppercase()) {
            "ONGOING" -> "ongoing"
            "FINISHED" -> "completed"
            "PAUSED" -> "hiatus"
            "ABANDONED" -> "cancelled"
            else -> ""
        }

        val tagsBuilder = mutableListOf<String>()
        obj.optJSONArray("tags")?.let { tagsArr ->
            for (i in 0 until tagsArr.length()) {
                val tObj = tagsArr.optJSONObject(i) ?: continue
                val tTitle = tObj.optString("title").trim()
                val tKey = tObj.optString("key").ifBlank { tTitle.lowercase() }
                if (tTitle.isNotBlank()) {
                    tagsBuilder.add("$tKey::$tTitle")
                }
            }
        }

        val mdUuid = MANGADEX_UUID_REGEX.find("$publicUrl $rawUrl")?.value
        val isMangaDex = sourceRaw.contains("MANGADEX") && mdUuid != null

        if (!isMangaDex && bestHttpUrl.isNotBlank()) {
            runCatching {
                val uri = Uri.parse(bestHttpUrl)
                val scheme = uri.scheme ?: "https"
                val host = uri.host.orEmpty()
                if (host.isNotBlank()) {
                    val origin = "$scheme://$host"
                    val normOrigin = origin.lowercase()
                    if (normOrigin !in existingBaseUrls && normOrigin !in discoveredSources) {
                        existingBaseUrls.add(normOrigin)
                        val friendlyName = sourceRaw.takeIf { it != "WEB" && it != "UNKNOWN" }
                            ?.lowercase()
                            ?.replaceFirstChar { it.uppercase() }
                            ?: host.removePrefix("www.").substringBefore('.').replaceFirstChar { it.uppercase() }
                        discoveredSources[normOrigin] = CustomSourceEntity(
                            name = friendlyName,
                            baseUrl = origin,
                            searchUrlTemplate = "$origin/?s={query}",
                            enabled = true
                        )
                    }
                }
            }
        }

        val finalId = if (isMangaDex) {
            mdUuid!!
        } else {
            "legacy_${sourceRaw.lowercase()}_$legacyNumericId"
        }

        return MangaEntity(
            id = finalId,
            sourceType = if (isMangaDex) {
                MangaSourceType.ONLINE_MANGADEX.name
            } else {
                MangaSourceType.CUSTOM_WEB.name
            },
            title = title,
            author = author,
            description = obj.optString("description", ""),
            coverUrl = coverUrl,
            status = status,
            tagsSerialized = tagsBuilder.joinToString("||"),
            localPath = null,
            sourceUrl = bestHttpUrl.ifBlank { if (isMangaDex) "https://mangadex.org/title/$finalId" else null },
            sourceLabel = if (isMangaDex) "MANGADEX" else sourceRaw.take(14)
        )
    }

    companion object {
        private val MANGADEX_UUID_REGEX = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
        )
    }
}
