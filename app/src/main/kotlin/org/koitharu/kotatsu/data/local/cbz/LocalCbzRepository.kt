package org.koitharu.kotatsu.data.local.cbz

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.domain.model.ChapterItem
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag
import org.koitharu.kotatsu.domain.model.PageItem
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream

class LocalCbzRepository(private val context: Context) {

    private val libraryRoot: File
        get() = File(context.filesDir, "cbz_library").apply { mkdirs() }

    suspend fun importCbzUri(uri: Uri): MangaItem = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Quadrinho_${System.currentTimeMillis()}.cbz"
        val cleanTitle = displayName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Arquivo CBZ Importado" }

        val mangaId = "local_${UUID.nameUUIDFromBytes(cleanTitle.lowercase().toByteArray())}"
        val targetDir = File(libraryRoot, mangaId)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        extractZipStreamToDir(uri, targetDir)
        buildMangaItemFromExtractedDir(mangaId, cleanTitle, targetDir)
    }

    /**
     * Scans both `filesDir/cbz_library` and legacy Kotatsu local manga directories (`filesDir/manga`,
     * `getExternalFilesDir("manga")`) to automatically recover saved CBZ/ZIP files or extracted folders
     * from previous app versions without requiring manual re-import.
     */
    suspend fun recoverExistingLocalLibrary(knownLocalPaths: Set<String>): List<MangaItem> =
        withContext(Dispatchers.IO) {
            val recovered = mutableListOf<MangaItem>()

            // 1. Check extracted folders inside `cbz_library`
            libraryRoot.listFiles()?.forEach { child ->
                if (child.isDirectory && child.absolutePath !in knownLocalPaths) {
                    val images = collectSortedImages(child)
                    if (images.isNotEmpty()) {
                        val id = child.name.let { if (it.startsWith("local_")) it else "local_$it" }
                        val title = child.name.removePrefix("local_").replace('_', ' ').trim()
                            .ifBlank { "Quadrinho Local" }
                        recovered.add(buildMangaItemFromExtractedDir(id, title, child))
                    }
                }
            }

            // 2. Check legacy official Kotatsu local storage directories for .cbz / .zip files or folders
            val legacyDirs = listOfNotNull(
                File(context.filesDir, "manga").takeIf { it.exists() },
                context.getExternalFilesDir("manga")?.takeIf { it.exists() },
                context.getExternalFilesDir(null)?.resolve("manga")?.takeIf { it.exists() }
            ).distinctBy { it.absolutePath }

            legacyDirs.forEach { legacyRoot ->
                legacyRoot.listFiles()?.forEach { item ->
                    if (item.isFile && (item.extension.equals("cbz", true) || item.extension.equals("zip", true))) {
                        val cleanTitle = item.nameWithoutExtension
                            .replace('_', ' ')
                            .replace('-', ' ')
                            .trim()
                            .ifBlank { item.nameWithoutExtension }
                        val mangaId = "local_${UUID.nameUUIDFromBytes(cleanTitle.lowercase().toByteArray())}"
                        val targetDir = File(libraryRoot, mangaId)
                        if (!targetDir.exists() && targetDir.absolutePath !in knownLocalPaths) {
                            runCatching {
                                targetDir.mkdirs()
                                extractZipFileToDir(item, targetDir)
                                recovered.add(buildMangaItemFromExtractedDir(mangaId, cleanTitle, targetDir))
                            }.onFailure {
                                targetDir.deleteRecursively()
                            }
                        }
                    } else if (item.isDirectory && item.absolutePath !in knownLocalPaths) {
                        val images = collectSortedImages(item)
                        if (images.isNotEmpty()) {
                            val cleanTitle = item.name.replace('_', ' ').trim()
                            val mangaId = "local_${UUID.nameUUIDFromBytes(item.absolutePath.toByteArray())}"
                            recovered.add(buildMangaItemFromExtractedDir(mangaId, cleanTitle, item))
                        }
                    }
                }
            }

            recovered
        }

    private fun extractZipStreamToDir(uri: Uri, targetDir: File) {
        var extractedImages = 0
        val canonicalRoot = targetDir.canonicalPath + File.separator

        context.contentResolver.openInputStream(uri)?.use { rawStream ->
            ZipInputStream(rawStream.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.replace('\\', '/')
                    if (!entry.isDirectory && isSupportedImageEntry(entryName)) {
                        val destFile = File(targetDir, entryName)
                        val canonicalDest = destFile.canonicalPath
                        if (canonicalDest.startsWith(canonicalRoot)) {
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().buffered().use { out ->
                                zis.copyTo(out)
                            }
                            extractedImages++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw IOException("Não foi possível abrir o arquivo selecionado.")

        if (extractedImages == 0) {
            targetDir.deleteRecursively()
            throw IOException("O arquivo selecionado não contém páginas de imagem compatíveis (.jpg, .png, .webp, .avif).")
        }
    }

    private fun extractZipFileToDir(zipFile: File, targetDir: File) {
        var extractedImages = 0
        val canonicalRoot = targetDir.canonicalPath + File.separator
        zipFile.inputStream().buffered().use { rawStream ->
            ZipInputStream(rawStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.replace('\\', '/')
                    if (!entry.isDirectory && isSupportedImageEntry(entryName)) {
                        val destFile = File(targetDir, entryName)
                        if (destFile.canonicalPath.startsWith(canonicalRoot)) {
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().buffered().use { out -> zis.copyTo(out) }
                            extractedImages++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        if (extractedImages == 0) {
            throw IOException("Sem imagens válidas no arquivo CBZ.")
        }
    }

    private fun buildMangaItemFromExtractedDir(
        mangaId: String,
        cleanTitle: String,
        targetDir: File
    ): MangaItem {
        val allImages = collectSortedImages(targetDir)
        val coverUri = allImages.firstOrNull()?.toURI()?.toString().orEmpty()
        val chaptersCount = buildChapters(mangaId, targetDir).size

        return MangaItem(
            id = mangaId,
            sourceType = MangaSourceType.LOCAL_CBZ,
            title = cleanTitle,
            author = "Biblioteca Local",
            description = "Quadrinho local contendo ${allImages.size} páginas distribuídas em $chaptersCount capítulo(s).",
            coverUrl = coverUri,
            status = "local",
            tags = listOf(MangaTag("local_cbz", "CBZ Offline")),
            localPath = targetDir.absolutePath,
            knownChaptersCount = chaptersCount
        )
    }

    suspend fun getLocalChapters(manga: MangaItem): List<ChapterItem> = withContext(Dispatchers.IO) {
        val rootPath = manga.localPath ?: return@withContext emptyList()
        val rootDir = File(rootPath)
        if (!rootDir.exists()) return@withContext emptyList()
        buildChapters(manga.id, rootDir)
    }

    suspend fun getLocalPages(chapterId: String, manga: MangaItem): List<PageItem> = withContext(Dispatchers.IO) {
        val chapters = getLocalChapters(manga)
        val target = chapters.firstOrNull { it.id == chapterId } ?: chapters.firstOrNull()
        val folderPath = target?.localFolderOrZipPath ?: return@withContext emptyList()
        val folder = File(folderPath)
        if (!folder.exists()) return@withContext emptyList()

        val directImages = folder.listFiles()
            ?.filter { it.isFile && isSupportedImageEntry(it.name) }
            ?.sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }
            .orEmpty()

        val resolvedImages = directImages.ifEmpty { collectSortedImages(folder) }
        resolvedImages.mapIndexed { idx, file ->
            PageItem(index = idx, imageUrl = file.toURI().toString())
        }
    }

    suspend fun deleteLocalFiles(localPath: String?) = withContext(Dispatchers.IO) {
        if (!localPath.isNullOrBlank()) {
            runCatching { File(localPath).deleteRecursively() }
        }
    }

    private fun buildChapters(mangaId: String, rootDir: File): List<ChapterItem> {
        val chapterDirs = mutableSetOf<File>()
        rootDir.walkTopDown().forEach { file ->
            if (file.isFile && isSupportedImageEntry(file.name)) {
                file.parentFile?.let { chapterDirs.add(it) }
            }
        }

        val sortedDirs = chapterDirs.sortedWith { a, b ->
            NaturalOrderComparator.compare(
                a.relativeTo(rootDir).path,
                b.relativeTo(rootDir).path
            )
        }

        return sortedDirs.mapIndexed { index, dir ->
            val relName = dir.relativeTo(rootDir).path
                .replace('\\', '/')
                .trim('/')
            val chapterNumber = (index + 1).toFloat()
            val friendlyTitle = if (relName.isBlank()) {
                "Volume Completo"
            } else {
                relName.substringAfterLast('/').replace('_', ' ')
            }
            ChapterItem(
                id = "${mangaId}_ch_${index + 1}",
                mangaId = mangaId,
                number = chapterNumber,
                numberLabel = (index + 1).toString(),
                title = friendlyTitle,
                language = "LOCAL",
                scanlator = "Arquivo CBZ",
                publishedAt = dir.lastModified(),
                localFolderOrZipPath = dir.absolutePath
            )
        }
    }

    private fun collectSortedImages(dir: File): List<File> {
        return dir.walkTopDown()
            .filter { it.isFile && isSupportedImageEntry(it.name) }
            .sortedWith { a, b ->
                NaturalOrderComparator.compare(
                    a.relativeTo(dir).path,
                    b.relativeTo(dir).path
                )
            }
            .toList()
    }

    private fun isSupportedImageEntry(entryName: String): Boolean {
        val fileName = entryName.substringAfterLast('/')
        if (entryName.contains("__MACOSX") || fileName.startsWith(".")) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_IMAGE_EXTENSIONS
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private object NaturalOrderComparator : Comparator<String> {
        private val chunkRegex = Regex("(\\d+|\\D+)")

        override fun compare(a: String, b: String): Int {
            val aChunks = chunkRegex.findAll(a.lowercase()).map { it.value }.toList()
            val bChunks = chunkRegex.findAll(b.lowercase()).map { it.value }.toList()
            val minSize = minOf(aChunks.size, bChunks.size)
            for (i in 0 until minSize) {
                val left = aChunks[i]
                val right = bChunks[i]
                if (left == right) continue
                val leftNum = left.toBigIntegerOrNull()
                val rightNum = right.toBigIntegerOrNull()
                val cmp = if (leftNum != null && rightNum != null) {
                    leftNum.compareTo(rightNum)
                } else {
                    left.compareTo(right)
                }
                if (cmp != 0) return cmp
            }
            return aChunks.size.compareTo(bChunks.size)
        }
    }

    companion object {
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif"
        )
    }
}
