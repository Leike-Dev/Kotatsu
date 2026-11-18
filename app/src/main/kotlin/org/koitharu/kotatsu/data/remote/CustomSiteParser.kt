package org.koitharu.kotatsu.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.domain.model.ChapterItem
import org.koitharu.kotatsu.domain.model.CustomMangaSource
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag
import org.koitharu.kotatsu.domain.model.PageItem
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Universal HTML & OPDS Manga Site Scraper using Jsoup + OkHttp.
 * Supports WordPress Madara, MangaStream/Themesia, generic comic/manga directories, and OPDS feeds.
 */
class CustomSiteParser(private val httpClient: OkHttpClient) {

    suspend fun searchCustomSource(
        source: CustomMangaSource,
        query: String
    ): List<MangaItem> = withContext(Dispatchers.IO) {
        val cleanBase = source.baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank()) return@withContext emptyList()

        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val targetUrls = buildList {
            if (query.isBlank()) {
                add(cleanBase)
            } else if (source.searchUrlTemplate.contains("{query}")) {
                add(source.searchUrlTemplate.replace("{query}", encodedQuery))
            } else {
                add("$cleanBase/?s=$encodedQuery&post_type=wp-manga")
                add("$cleanBase/?s=$encodedQuery")
                add("$cleanBase/search?q=$encodedQuery")
            }
        }

        for (candidateUrl in targetUrls) {
            val html = fetchHtmlSafe(candidateUrl, referer = cleanBase) ?: continue
            val parsed = parseCatalogHtml(
                html = html,
                baseUrl = candidateUrl,
                sourceName = source.name
            )
            if (parsed.isNotEmpty()) return@withContext parsed
        }
        emptyList()
    }

    suspend fun fetchMangaDetailsAndChapters(
        manga: MangaItem
    ): Pair<MangaItem, List<ChapterItem>> = withContext(Dispatchers.IO) {
        val pageUrl = manga.sourceUrl ?: return@withContext (manga to emptyList())
        val html = fetchHtmlSafe(pageUrl, referer = pageUrl)
            ?: return@withContext (manga to emptyList())

        val doc = Jsoup.parse(html, pageUrl)

        val enrichedDesc = doc.select(
            ".summary__content, .description-summary, .entry-content-single, [itemprop=description], .manga-excerpt, .synopsis"
        ).firstOrNull()?.text()?.trim().orEmpty().ifBlank { manga.description }

        val enrichedCover = doc.select(
            ".summary_image img, .thumb img, .manga-poster img, meta[property=og:image]"
        ).firstOrNull()?.let { el ->
            if (el.tagName().equals("meta", ignoreCase = true)) {
                el.absUrl("content").ifBlank { el.attr("content") }
            } else {
                extractBestImageUrl(el)
            }
        }.orEmpty().ifBlank { manga.coverUrl }

        val enrichedAuthor = doc.select(
            ".author-content a, .fmed:contains(Author) span, .fmed:contains(Autor) span, [rel=author]"
        ).firstOrNull()?.text()?.trim().orEmpty().ifBlank { manga.author }

        val tags = doc.select(".genres-content a, .mgen a, .seriestugenre a")
            .mapNotNull { a ->
                val text = a.text().trim()
                if (text.isBlank()) null else MangaTag(id = text.lowercase(), name = text)
            }
            .distinctBy { it.id }
            .take(8)
            .ifEmpty { manga.tags }

        val updatedManga = manga.copy(
            description = enrichedDesc,
            coverUrl = enrichedCover,
            author = enrichedAuthor,
            tags = tags
        )

        val chapterElements = doc.select(
            ".wp-manga-chapter a, #chapterlist li a, .eplister ul li a, .chapter-list a, .chbox a, a[href*='capitulo'], a[href*='chapter']"
        )

        val chapters = chapterElements
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").trim()
                if (href.isBlank() || href == pageUrl || href.endsWith("#")) return@mapNotNull null
                val rawTitle = anchor.select(".chapternum, .chapter-title").text().trim()
                    .ifBlank { anchor.text().trim() }
                if (rawTitle.isBlank()) return@mapNotNull null
                val numberMatch = Regex("""(\d+(?:\.\d+)?)""").find(rawTitle)?.groupValues?.getOrNull(1).orEmpty()
                val numFloat = numberMatch.toFloatOrNull() ?: 0f
                ChapterItem(
                    id = "webch_${sha1Short(href)}",
                    mangaId = updatedManga.id,
                    number = numFloat,
                    numberLabel = numberMatch,
                    title = rawTitle,
                    language = "",
                    scanlator = updatedManga.sourceLabel,
                    publishedAt = 0L,
                    chapterUrl = href
                )
            }
            .distinctBy { it.chapterUrl }

        val sortedChapters = if (chapters.size >= 2 && chapters.first().number > chapters.last().number) {
            chapters.reversed()
        } else {
            chapters
        }

        // If the page itself has comic images directly (single-shot / webcomic post), create a direct chapter!
        val finalChapters = if (sortedChapters.isEmpty()) {
            val inlinePages = extractImagesFromDocument(doc)
            if (inlinePages.isNotEmpty()) {
                listOf(
                    ChapterItem(
                        id = "webch_${sha1Short(pageUrl)}",
                        mangaId = updatedManga.id,
                        number = 1f,
                        numberLabel = "1",
                        title = updatedManga.title,
                        language = "",
                        scanlator = updatedManga.sourceLabel,
                        publishedAt = 0L,
                        chapterUrl = pageUrl
                    )
                )
            } else {
                emptyList()
            }
        } else {
            sortedChapters
        }

        updatedManga to finalChapters
    }

    suspend fun fetchChapterPages(chapterUrl: String): List<PageItem> = withContext(Dispatchers.IO) {
        val html = fetchHtmlSafe(chapterUrl, referer = chapterUrl) ?: return@withContext emptyList()
        extractPagesFromHtml(chapterUrl, html)
    }

    fun extractPagesFromHtml(baseUrl: String, html: String): List<PageItem> {
        val doc = Jsoup.parse(html, baseUrl)
        val domImages = extractImagesFromDocument(doc)
        if (domImages.isNotEmpty()) {
            return domImages.mapIndexed { idx, url -> PageItem(index = idx, imageUrl = url) }
        }

        // Fallback: MangaStream / Themesia ts_reader or JSON arrays of image URLs inside <script>
        val scriptUrls = Regex("""https?:\\?/\\?/[^"'\s<>]+\.(?:jpg|jpeg|png|webp)(?:\?[^"'\s<>]*)?""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.value.replace("\\/", "/") }
            .filter { candidate ->
                val lower = candidate.lowercase()
                !WebProtectionEngine.isAdOrAntiAdblockResource(lower) &&
                    !lower.contains("logo") &&
                    !lower.contains("avatar") &&
                    !lower.contains("icon") &&
                    !lower.contains("banner")
            }
            .distinct()
            .toList()

        return scriptUrls.mapIndexed { idx, url -> PageItem(index = idx, imageUrl = url) }
    }

    private fun extractImagesFromDocument(doc: Document): List<String> {
        val primaryContainers = doc.select(
            ".reading-content img, #readerarea img, .page-break img, .chapter-content img, .container-chapter-reader img, #vungdoc img, main img, article img"
        )
        val candidates = if (primaryContainers.isNotEmpty()) primaryContainers else doc.select("img")

        return candidates.mapNotNull { img ->
            val url = extractBestImageUrl(img)
            val lower = url.lowercase()
            if (url.isBlank() ||
                url.startsWith("data:") ||
                WebProtectionEngine.isAdOrAntiAdblockResource(lower) ||
                lower.contains("logo") ||
                lower.contains("avatar") ||
                lower.contains("favicon") ||
                lower.endsWith(".svg")
            ) {
                null
            } else {
                url
            }
        }.distinct()
    }

    private fun parseCatalogHtml(
        html: String,
        baseUrl: String,
        sourceName: String
    ): List<MangaItem> {
        // Support OPDS XML feeds cleanly as well
        if (html.trimStart().startsWith("<?xml") && html.contains("<feed")) {
            val xmlDoc = Jsoup.parse(html, baseUrl, org.jsoup.parser.Parser.xmlParser())
            val entries = xmlDoc.select("entry")
            if (entries.isNotEmpty()) {
                return entries.mapNotNull { entry ->
                    val title = entry.selectFirst("title")?.text()?.trim().orEmpty()
                    if (title.isBlank()) return@mapNotNull null
                    val href = entry.select("link").firstOrNull {
                        !it.attr("rel").contains("thumbnail") && !it.attr("type").startsWith("image/")
                    }?.absUrl("href").orEmpty()
                    val thumb = entry.select("link[type^=image/], link[rel*=image], link[rel*=thumbnail]")
                        .firstOrNull()?.absUrl("href").orEmpty()
                    val summary = entry.selectFirst("summary, content")?.text()?.trim().orEmpty()
                    val author = entry.selectFirst("author > name")?.text()?.trim().orEmpty()
                    val targetUrl = href.ifBlank { baseUrl }
                    MangaItem(
                        id = "custom_${sha1Short(targetUrl)}",
                        sourceType = MangaSourceType.CUSTOM_WEB,
                        title = title,
                        author = author,
                        description = summary,
                        coverUrl = thumb,
                        status = "online",
                        tags = emptyList(),
                        sourceUrl = targetUrl,
                        sourceLabel = sourceName
                    )
                }.take(30)
            }
        }

        val doc = Jsoup.parse(html, baseUrl)
        val cardElements = doc.select(
            ".c-tabs-item__content, .page-item-detail, .bsx, .utao, .manga-item, .book-item, .listupd .bs, article"
        )

        val results = mutableListOf<MangaItem>()
        val elementsToScan = if (cardElements.isNotEmpty()) cardElements else doc.select("a:has(img)")

        for (el in elementsToScan) {
            val linkEl = if (el.tagName() == "a") el else el.selectFirst("a[href]") ?: continue
            val href = linkEl.absUrl("href").trim()
            if (href.isBlank() || href.endsWith("#") || WebProtectionEngine.isAdOrAntiAdblockResource(href)) continue

            val imgEl = el.selectFirst("img")
            val cover = imgEl?.let(::extractBestImageUrl).orEmpty()

            val title = el.select(".post-title, .tt, .title, h3, h4, h2").firstOrNull()?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: linkEl.attr("title").trim().takeIf { it.isNotBlank() }
                ?: imgEl?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                ?: linkEl.text().trim()

            if (title.length < 2) continue

            results.add(
                MangaItem(
                    id = "custom_${sha1Short(href)}",
                    sourceType = MangaSourceType.CUSTOM_WEB,
                    title = title,
                    author = "",
                    description = "",
                    coverUrl = cover,
                    status = "online",
                    tags = emptyList(),
                    sourceUrl = href,
                    sourceLabel = sourceName
                )
            )
            if (results.size >= 28) break
        }

        return results.distinctBy { it.id }
    }

    private fun extractBestImageUrl(img: Element): String {
        val attrs = listOf("data-src", "data-lazy-src", "data-cfsrc", "data-original", "src")
        for (attr in attrs) {
            val abs = img.absUrl(attr).trim()
            if (abs.isNotBlank() && !abs.startsWith("data:")) return abs
            val raw = img.attr(attr).trim()
            if (raw.startsWith("http")) return raw
        }
        val srcset = img.attr("srcset").split(",").firstOrNull()?.trim()?.substringBefore(" ").orEmpty()
        if (srcset.startsWith("http")) return srcset
        return ""
    }

    private fun fetchHtmlSafe(url: String, referer: String): String? {
        val parsedUrl = url.toHttpUrlOrNull() ?: return null
        val origin = "${parsedUrl.scheme}://${parsedUrl.host}"
        val req = Request.Builder()
            .url(parsedUrl)
            .header("User-Agent", WebProtectionEngine.activeUserAgent.get())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Referer", referer.ifBlank { origin })
            .header("Upgrade-Insecure-Requests", "1")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    companion object {
        fun sha1Short(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
            return bytes.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
