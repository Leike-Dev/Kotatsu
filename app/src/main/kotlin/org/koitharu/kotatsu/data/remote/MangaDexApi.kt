package org.koitharu.kotatsu.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.domain.model.CatalogSort
import org.koitharu.kotatsu.domain.model.ChapterItem
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag
import org.koitharu.kotatsu.domain.model.PageItem
import java.io.IOException
import java.time.Instant

class MangaDexApi(private val httpClient: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val rateLimitMutex = Mutex()
    private var lastRequestTimeMs = 0L

    private suspend fun executeGet(url: String): String = withContext(Dispatchers.IO) {
        rateLimitMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastRequestTimeMs
            if (elapsed in 0..219) {
                delay(220 - elapsed)
            }
            lastRequestTimeMs = System.currentTimeMillis()
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: falha ao consultar catálogo MangaDex")
            }
            body
        }
    }

    suspend fun searchManga(
        query: String,
        language: LanguageFilter,
        sort: CatalogSort,
        tagId: String?,
        hideNsfw: Boolean = false,
        limit: Int = 24,
        offset: Int = 0
    ): List<MangaItem> {
        val urlBuilder = "$BASE_URL/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("contentRating[]", "safe")
            .apply {
                if (!hideNsfw) {
                    addQueryParameter("contentRating[]", "suggestive")
                }
            }
            .addQueryParameter("hasAvailableChapters", "true")

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("title", query.trim())
        }

        language.apiCode?.let { langCode ->
            urlBuilder.addQueryParameter("availableTranslatedLanguage[]", langCode)
        }

        if (!tagId.isNullOrBlank()) {
            urlBuilder.addQueryParameter("includedTags[]", tagId)
        }

        when (sort) {
            CatalogSort.POPULAR -> urlBuilder.addQueryParameter("order[followedCount]", "desc")
            CatalogSort.LATEST -> urlBuilder.addQueryParameter("order[latestUploadedChapter]", "desc")
            CatalogSort.RATING -> urlBuilder.addQueryParameter("order[rating]", "desc")
        }

        val payload = executeGet(urlBuilder.build().toString())
        val parsed = json.decodeFromString<MangaListResponse>(payload)
        return parsed.data.map { it.toDomain() }
    }

    suspend fun getMangaById(mangaId: String): MangaItem {
        val url = "$BASE_URL/manga/$mangaId".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .build()
            .toString()

        val payload = executeGet(url)
        val parsed = json.decodeFromString<SingleMangaResponse>(payload)
        return parsed.data.toDomain()
    }

    suspend fun getPopularTags(): List<MangaTag> {
        val payload = executeGet("$BASE_URL/manga/tag")
        val parsed = json.decodeFromString<TagListResponse>(payload)
        return parsed.data
            .filter { it.attributes.group == "genre" || it.attributes.group == "theme" }
            .mapNotNull { dto ->
                val name = dto.attributes.name.localizedString()
                if (name.isBlank()) null else MangaTag(id = dto.id, name = translateGenrePt(name))
            }
            .sortedBy { it.name }
            .take(18)
    }

    suspend fun getChapters(
        mangaId: String,
        language: LanguageFilter
    ): List<ChapterItem> {
        val allChapters = mutableListOf<ChapterDto>()
        var offset = 0
        val pageLimit = 100

        while (offset < 300) {
            val builder = "$BASE_URL/manga/$mangaId/feed".toHttpUrl().newBuilder()
                .addQueryParameter("limit", pageLimit.toString())
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("order[chapter]", "asc")
                .addQueryParameter("includes[]", "scanlation_group")
                .addQueryParameter("contentRating[]", "safe")
                .addQueryParameter("contentRating[]", "suggestive")
                .addQueryParameter("includeExternalUrl", "0")

            val langCode = language.apiCode
            if (langCode != null) {
                builder.addQueryParameter("translatedLanguage[]", langCode)
            } else {
                builder.addQueryParameter("translatedLanguage[]", "pt-br")
                builder.addQueryParameter("translatedLanguage[]", "pt")
                builder.addQueryParameter("translatedLanguage[]", "en")
                builder.addQueryParameter("translatedLanguage[]", "es")
                builder.addQueryParameter("translatedLanguage[]", "es-la")
            }

            val payload = executeGet(builder.build().toString())
            val response = json.decodeFromString<ChapterFeedResponse>(payload)
            val validBatch = response.data.filter {
                it.attributes.externalUrl == null && it.attributes.pages > 0
            }
            allChapters.addAll(validBatch)

            offset += pageLimit
            if (offset >= response.total || response.data.isEmpty()) break
        }

        // Prefer PT-BR when multiple languages upload the exact same chapter number in ALL mode,
        // while keeping unique chapter numbers clean and navigable.
        return allChapters
            .map { it.toChapterDomain(mangaId) }
            .groupBy { "${it.numberLabel}_${it.language}" }
            .map { (_, list) -> list.first() }
            .sortedWith(compareBy<ChapterItem> { it.number }.thenBy { languagePriority(it.language) })
    }

    suspend fun getChapterPages(chapterId: String, dataSaver: Boolean): List<PageItem> {
        val payload = executeGet("$BASE_URL/at-home/server/$chapterId")
        val response = json.decodeFromString<AtHomeResponse>(payload)
        val files = if (dataSaver && response.chapter.dataSaver.isNotEmpty()) {
            response.chapter.dataSaver
        } else {
            response.chapter.data
        }
        val qualitySegment = if (dataSaver && response.chapter.dataSaver.isNotEmpty()) {
            "data-saver"
        } else {
            "data"
        }
        return files.mapIndexed { idx, fileName ->
            PageItem(
                index = idx,
                imageUrl = "${response.baseUrl}/$qualitySegment/${response.chapter.hash}/$fileName"
            )
        }
    }

    private fun languagePriority(lang: String): Int = when (lang.lowercase()) {
        "pt-br", "pt" -> 0
        "en" -> 1
        "es", "es-la" -> 2
        else -> 3
    }

    private fun MangaDto.toDomain(): MangaItem {
        val titleText = attributes.title.localizedString().ifBlank {
            attributes.altTitles.firstNotNullOfOrNull { it.localizedString().takeIf(String::isNotBlank) }
                ?: "Sem título"
        }
        val descText = attributes.description.localizedString()
        val coverFileName = relationships
            .firstOrNull { it.type == "cover_art" }
            ?.attributes?.fileName
        val coverUrl = if (!coverFileName.isNullOrBlank()) {
            "$UPLOADS_URL/covers/$id/$coverFileName.512.jpg"
        } else {
            ""
        }
        val authorName = relationships
            .firstOrNull { it.type == "author" }
            ?.attributes?.name
            .orEmpty()

        val domainTags = attributes.tags.mapNotNull { tagDto ->
            val raw = tagDto.attributes.name.localizedString()
            if (raw.isBlank()) null else MangaTag(id = tagDto.id, name = translateGenrePt(raw))
        }.take(6)

        return MangaItem(
            id = id,
            sourceType = MangaSourceType.ONLINE_MANGADEX,
            title = titleText,
            author = authorName,
            description = descText,
            coverUrl = coverUrl,
            status = attributes.status.orEmpty(),
            tags = domainTags,
            sourceUrl = "https://mangadex.org/title/$id",
            sourceLabel = "MANGADEX"
        )
    }

    private fun ChapterDto.toChapterDomain(mangaId: String): ChapterItem {
        val numLabel = attributes.chapter?.trim().orEmpty()
        val numFloat = numLabel.toFloatOrNull() ?: Float.MAX_VALUE
        val groupName = relationships
            .firstOrNull { it.type == "scanlation_group" }
            ?.attributes?.name
            .orEmpty()
        val epochMs = runCatching {
            attributes.publishAt?.let { Instant.parse(it).toEpochMilli() } ?: 0L
        }.getOrDefault(0L)

        return ChapterItem(
            id = id,
            mangaId = mangaId,
            number = numFloat,
            numberLabel = numLabel,
            title = attributes.title?.trim().orEmpty(),
            language = attributes.translatedLanguage?.uppercase().orEmpty(),
            scanlator = groupName,
            publishedAt = epochMs
        )
    }

    private fun JsonElement?.localizedString(): String {
        val obj = this as? JsonObject ?: return ""
        val preferredKeys = listOf("pt-br", "pt", "en", "es", "ja-ro", "ja")
        for (key in preferredKeys) {
            val value = (obj[key] as? JsonPrimitive)?.contentOrNull
            if (!value.isNullOrBlank()) return value.trim()
        }
        return obj.values
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
    }

    private fun translateGenrePt(enName: String): String = when (enName) {
        "Action" -> "Ação"
        "Adventure" -> "Aventura"
        "Aliens" -> "Alienígenas"
        "Animals" -> "Animais"
        "Award Winning" -> "Premiado"
        "Boys' Love" -> "Boys' Love"
        "Comedy" -> "Comédia"
        "Cooking" -> "Culinária"
        "Crime" -> "Policial"
        "Delinquents" -> "Delinquentes"
        "Demons" -> "Demônios"
        "Detective" -> "Investigação"
        "Drama" -> "Drama"
        "Fantasy" -> "Fantasia"
        "Full Color" -> "Colorido"
        "Genderswap" -> "Troca de Gênero"
        "Ghosts" -> "Fantasmas"
        "Girls' Love" -> "Girls' Love"
        "Gyaru" -> "Gyaru"
        "Harem" -> "Harém"
        "Historical" -> "Histórico"
        "Horror" -> "Terror"
        "Isekai" -> "Isekai"
        "Long Strip" -> "Tira Vertical"
        "Mafia" -> "Máfia"
        "Magic" -> "Magia"
        "Magical Girls" -> "Garotas Mágicas"
        "Martial Arts" -> "Artes Marciais"
        "Mecha" -> "Mecha"
        "Medical" -> "Medicina"
        "Military" -> "Militar"
        "Monster Girls" -> "Garotas Monstro"
        "Monsters" -> "Monstros"
        "Music" -> "Música"
        "Mystery" -> "Mistério"
        "Ninja" -> "Ninja"
        "Office Workers" -> "Vida Corporativa"
        "Oneshot" -> "One-shot"
        "Philosophical" -> "Filosófico"
        "Police" -> "Policial"
        "Post-Apocalyptic" -> "Pós-Apocalíptico"
        "Psychological" -> "Psicológico"
        "Reincarnation" -> "Reencarnação"
        "Reverse Harem" -> "Harém Reverso"
        "Romance" -> "Romance"
        "Samurai" -> "Samurai"
        "School Life" -> "Vida Escolar"
        "Sci-Fi" -> "Ficção Científica"
        "Self-Published" -> "Independente"
        "Slice of Life" -> "Cotidiano"
        "Sports" -> "Esportes"
        "Superhero" -> "Super-heróis"
        "Supernatural" -> "Sobrenatural"
        "Survival" -> "Sobrevivência"
        "Thriller" -> "Suspense"
        "Time Travel" -> "Viagem no Tempo"
        "Traditional Games" -> "Jogos Tradicionais"
        "Tragedy" -> "Tragédia"
        "Vampires" -> "Vampiros"
        "Video Games" -> "Jogos"
        "Villainess" -> "Vilã"
        "Virtual Reality" -> "Realidade Virtual"
        "Web Comic" -> "Webcomic"
        "Wuxia" -> "Wuxia"
        "Zombies" -> "Zumbis"
        "Adaptation" -> "Adaptação"
        else -> enName
    }

    @Serializable
    private data class MangaListResponse(
        val data: List<MangaDto> = emptyList()
    )

    @Serializable
    private data class SingleMangaResponse(
        val data: MangaDto
    )

    @Serializable
    private data class MangaDto(
        val id: String,
        val attributes: MangaAttributesDto,
        val relationships: List<RelationshipDto> = emptyList()
    )

    @Serializable
    private data class MangaAttributesDto(
        val title: JsonElement? = null,
        val altTitles: List<JsonElement> = emptyList(),
        val description: JsonElement? = null,
        val status: String? = null,
        val tags: List<TagDto> = emptyList()
    )

    @Serializable
    private data class TagListResponse(
        val data: List<TagDto> = emptyList()
    )

    @Serializable
    private data class TagDto(
        val id: String,
        val attributes: TagAttributesDto
    )

    @Serializable
    private data class TagAttributesDto(
        val name: JsonElement? = null,
        val group: String? = null
    )

    @Serializable
    private data class RelationshipDto(
        val id: String,
        val type: String,
        val attributes: RelationshipAttributesDto? = null
    )

    @Serializable
    private data class RelationshipAttributesDto(
        val fileName: String? = null,
        val name: String? = null
    )

    @Serializable
    private data class ChapterFeedResponse(
        val data: List<ChapterDto> = emptyList(),
        val total: Int = 0
    )

    @Serializable
    private data class ChapterDto(
        val id: String,
        val attributes: ChapterAttributesDto,
        val relationships: List<RelationshipDto> = emptyList()
    )

    @Serializable
    private data class ChapterAttributesDto(
        val title: String? = null,
        val chapter: String? = null,
        val translatedLanguage: String? = null,
        val pages: Int = 0,
        val externalUrl: String? = null,
        @SerialName("publishAt") val publishAt: String? = null
    )

    @Serializable
    private data class AtHomeResponse(
        val baseUrl: String,
        val chapter: AtHomeChapterDto
    )

    @Serializable
    private data class AtHomeChapterDto(
        val hash: String,
        val data: List<String> = emptyList(),
        val dataSaver: List<String> = emptyList()
    )

    companion object {
        private const val BASE_URL = "https://api.mangadex.org"
        private const val UPLOADS_URL = "https://uploads.mangadex.org"
    }
}
