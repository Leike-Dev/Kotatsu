package org.koitharu.kotatsu.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.domain.model.CatalogSort
import org.koitharu.kotatsu.domain.model.MangaItem
import org.koitharu.kotatsu.domain.model.MangaSourceType
import org.koitharu.kotatsu.domain.model.MangaTag

/**
 * Secondary zero-key public Manga & Manhwa discovery API (Kitsu JSON:API).
 * Enriches the multi-site search in Descobrir alongside MangaDex and Custom Web Sites.
 */
class KitsuApi(private val httpClient: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun searchManga(
        query: String,
        sort: CatalogSort,
        limit: Int = 16,
        offset: Int = 0
    ): List<MangaItem> = withContext(Dispatchers.IO) {
        val builder = "https://kitsu.io/api/edge/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page[limit]", limit.coerceIn(1, 20).toString())
            .addQueryParameter("page[offset]", offset.toString())

        if (query.isNotBlank()) {
            builder.addQueryParameter("filter[text]", query.trim())
        } else {
            val sortParam = when (sort) {
                CatalogSort.POPULAR -> "-userCount"
                CatalogSort.LATEST -> "-updatedAt"
                CatalogSort.RATING -> "-averageRating"
            }
            builder.addQueryParameter("sort", sortParam)
        }

        val req = Request.Builder()
            .url(builder.build())
            .header("Accept", "application/vnd.api+json")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string().orEmpty()
            val parsed = json.decodeFromString<KitsuListResponse>(body)
            parsed.data.map { it.toDomain() }
        }
    }

    suspend fun getMangaById(rawId: String): MangaItem? = withContext(Dispatchers.IO) {
        val numericId = rawId.removePrefix("kitsu_")
        val req = Request.Builder()
            .url("https://kitsu.io/api/edge/manga/$numericId")
            .header("Accept", "application/vnd.api+json")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string().orEmpty()
            val parsed = json.decodeFromString<KitsuSingleResponse>(body)
            parsed.data.toDomain()
        }
    }

    private fun KitsuMangaDto.toDomain(): MangaItem {
        val title = attributes.canonicalTitle?.takeIf { it.isNotBlank() }
            ?: attributes.titles.values.firstOrNull { it.isNotBlank() }
            ?: "Obra Kitsu #$id"
        val cover = attributes.posterImage?.large
            ?: attributes.posterImage?.medium
            ?: attributes.posterImage?.original
            ?: ""
        val subtypeRaw = attributes.subtype?.lowercase()
        val subtypeLabel = when (subtypeRaw) {
            "manga" -> "Mangá"
            "manhwa" -> "Manhwa"
            "manhua" -> "Manhua"
            "novel" -> "Light Novel"
            "oneshot" -> "One-shot"
            "doujin" -> "Doujinshi"
            "oel" -> "HQ Ocidental"
            else -> attributes.subtype?.replaceFirstChar { it.uppercase() }
        }
        val tags = listOfNotNull(
            subtypeLabel?.let { MangaTag(id = "kitsu_${subtypeRaw ?: it}", name = it) }
        )
        val statusText = when (attributes.status?.lowercase()) {
            "current" -> "ongoing"
            "finished" -> "completed"
            "tba", "unreleased", "upcoming" -> "upcoming"
            else -> attributes.status.orEmpty()
        }
        val slugUrl = attributes.slug?.let { "https://kitsu.io/manga/$it" }
        return MangaItem(
            id = "kitsu_$id",
            sourceType = MangaSourceType.ONLINE_KITSU,
            title = title,
            author = attributes.serialization.orEmpty(),
            description = attributes.synopsis.orEmpty(),
            coverUrl = cover,
            status = statusText,
            tags = tags,
            sourceUrl = slugUrl,
            sourceLabel = "KITSU"
        )
    }

    @Serializable
    private data class KitsuListResponse(
        val data: List<KitsuMangaDto> = emptyList()
    )

    @Serializable
    private data class KitsuSingleResponse(
        val data: KitsuMangaDto
    )

    @Serializable
    private data class KitsuMangaDto(
        val id: String,
        val attributes: KitsuAttributesDto
    )

    @Serializable
    private data class KitsuAttributesDto(
        val slug: String? = null,
        val synopsis: String? = null,
        val canonicalTitle: String? = null,
        val titles: Map<String, String> = emptyMap(),
        val status: String? = null,
        val subtype: String? = null,
        val serialization: String? = null,
        val posterImage: KitsuPosterDto? = null
    )

    @Serializable
    private data class KitsuPosterDto(
        val medium: String? = null,
        val large: String? = null,
        val original: String? = null
    )
}
