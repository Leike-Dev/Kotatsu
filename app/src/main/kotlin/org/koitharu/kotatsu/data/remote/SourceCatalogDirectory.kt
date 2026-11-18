package org.koitharu.kotatsu.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

enum class BuiltInCatalogKind {
    NATIVE_MANGADEX,
    NATIVE_KITSU,
    CATALOG_TEMPLATE,
    OPDS_SERVER
}

data class CatalogDirectoryEntry(
    val catalogId: String,
    val name: String,
    val baseUrl: String,
    val searchUrlTemplate: String,
    val languageCode: String,
    val engineLabel: String,
    val description: String,
    val kind: BuiltInCatalogKind = BuiltInCatalogKind.CATALOG_TEMPLATE
) {
    val defaultFaviconUrl: String
        get() = SourceCatalogDirectory.defaultFaviconUrl(baseUrl)
}

object SourceCatalogDirectory {

    val entries: List<CatalogDirectoryEntry> = listOf(
        // Fontes Nativas de Alta Performance (JSON APIs)
        CatalogDirectoryEntry(
            catalogId = "builtin_mangadex",
            name = "MangaDex",
            baseUrl = "https://mangadex.org",
            searchUrlTemplate = "https://api.mangadex.org/manga?title={query}",
            languageCode = "MULTI",
            engineLabel = "API Oficial v5",
            description = "Maior acervo comunitário multilíngue com capítulos em PT-BR, Inglês e Espanhol.",
            kind = BuiltInCatalogKind.NATIVE_MANGADEX
        ),
        CatalogDirectoryEntry(
            catalogId = "builtin_kitsu",
            name = "Kitsu Global",
            baseUrl = "https://kitsu.io",
            searchUrlTemplate = "https://kitsu.io/api/edge/manga?filter[text]={query}",
            languageCode = "MULTI",
            engineLabel = "JSON:API",
            description = "Descoberta global de mangás, manhwas, manhuas e metadados editoriais.",
            kind = BuiltInCatalogKind.NATIVE_KITSU
        ),

        // Catálogos em Português (PT-BR)
        CatalogDirectoryEntry(
            catalogId = "pt_lermanga",
            name = "LerMangá",
            baseUrl = "https://lermanga.org",
            searchUrlTemplate = "https://lermanga.org/?s={query}",
            languageCode = "PT-BR",
            engineLabel = "WordPress Manga",
            description = "Portal brasileiro de mangás e manhwas traduzidos para português."
        ),
        CatalogDirectoryEntry(
            catalogId = "pt_mangahost",
            name = "MangaHost Br",
            baseUrl = "https://mangahosted.com",
            searchUrlTemplate = "https://mangahosted.com/find/{query}",
            languageCode = "PT-BR",
            engineLabel = "Portal Web",
            description = "Catálogo clássico em português com acervo amplo de mangás e webtoons."
        ),
        CatalogDirectoryEntry(
            catalogId = "pt_slimeread",
            name = "SlimeRead",
            baseUrl = "https://slimeread.com",
            searchUrlTemplate = "https://slimeread.com/pesquisar?termo={query}",
            languageCode = "PT-BR",
            engineLabel = "Portal Moderno",
            description = "Plataforma popular de scanlators brasileiros (suporte via Navegador Protegido)."
        ),
        CatalogDirectoryEntry(
            catalogId = "pt_central",
            name = "Central de Mangás",
            baseUrl = "https://centraldemangas.online",
            searchUrlTemplate = "https://centraldemangas.online/?s={query}&post_type=wp-manga",
            languageCode = "PT-BR",
            engineLabel = "WordPress Madara",
            description = "Catálogo Madara em português com atualizações frequentes de capítulos."
        ),
        CatalogDirectoryEntry(
            catalogId = "pt_flower",
            name = "Flower Manga",
            baseUrl = "https://flowermanga.net",
            searchUrlTemplate = "https://flowermanga.net/?s={query}&post_type=wp-manga",
            languageCode = "PT-BR",
            engineLabel = "WordPress Madara",
            description = "Manhwas, webtoons de romance, fantasia e shoujo em PT-BR."
        ),
        CatalogDirectoryEntry(
            catalogId = "pt_mangalivre_mirror",
            name = "MangaLivre Mirror",
            baseUrl = "https://mangalivre.tv",
            searchUrlTemplate = "https://mangalivre.tv/?s={query}&post_type=wp-manga",
            languageCode = "PT-BR",
            engineLabel = "WordPress Madara",
            description = "Espelho comunitário compatível com motor WordPress Madara em português."
        ),

        // Catálogos Internacionais / Inglês (EN)
        CatalogDirectoryEntry(
            catalogId = "en_comick",
            name = "ComicK",
            baseUrl = "https://comick.io",
            searchUrlTemplate = "https://comick.io/search?q={query}",
            languageCode = "MULTI",
            engineLabel = "Portal Multilíngue",
            description = "Diretório rápido de mangás e manhwas em múltiplos idiomas incluindo PT-BR e EN."
        ),
        CatalogDirectoryEntry(
            catalogId = "en_bato",
            name = "Bato.to",
            baseUrl = "https://bato.to",
            searchUrlTemplate = "https://bato.to/search?word={query}",
            languageCode = "MULTI",
            engineLabel = "Portal Multilíngue",
            description = "Catálogo internacional com traduções oficiais e comunitárias em dezenas de línguas."
        ),
        CatalogDirectoryEntry(
            catalogId = "en_mangafire",
            name = "MangaFire",
            baseUrl = "https://mangafire.to",
            searchUrlTemplate = "https://mangafire.to/filter?keyword={query}",
            languageCode = "EN",
            engineLabel = "Portal Web",
            description = "Acervo de alta resolução com páginas duplas e capítulos completos."
        ),
        CatalogDirectoryEntry(
            catalogId = "en_mangabuddy",
            name = "MangaBuddy",
            baseUrl = "https://mangabuddy.com",
            searchUrlTemplate = "https://mangabuddy.com/search?q={query}",
            languageCode = "EN",
            engineLabel = "MangaStream",
            description = "Catálogo ágil de mangás, manhwas e webtoons em inglês."
        ),
        CatalogDirectoryEntry(
            catalogId = "en_asura",
            name = "Asura Scans",
            baseUrl = "https://asuracomic.net",
            searchUrlTemplate = "https://asuracomic.net/series?name={query}",
            languageCode = "EN",
            engineLabel = "MangaStream",
            description = "Especializado em manhwas de ação, fantasia e regressão."
        ),
        CatalogDirectoryEntry(
            catalogId = "en_flame",
            name = "Flame Comics",
            baseUrl = "https://flamecomics.xyz",
            searchUrlTemplate = "https://flamecomics.xyz/?s={query}",
            languageCode = "EN",
            engineLabel = "MangaStream",
            description = "Scanlator focado em séries orientais e webtoons semanais."
        ),
        CatalogDirectoryEntry(
            catalogId = "en_weebcentral",
            name = "WeebCentral",
            baseUrl = "https://weebcentral.com",
            searchUrlTemplate = "https://weebcentral.com/search?text={query}",
            languageCode = "EN",
            engineLabel = "Portal Web",
            description = "Sucessor moderno do MangaSee com digitalizações oficiais em alta definição."
        ),
        CatalogDirectoryEntry(
            catalogId = "en_webtoons",
            name = "WEBTOON Oficial",
            baseUrl = "https://www.webtoons.com",
            searchUrlTemplate = "https://www.webtoons.com/en/search?keyword={query}",
            languageCode = "EN",
            engineLabel = "Portal Oficial",
            description = "Plataforma oficial de quadrinhos verticais contínuos e originais."
        ),
        CatalogDirectoryEntry(
            catalogId = "multi_mangaplus",
            name = "MANGA Plus Shueisha",
            baseUrl = "https://mangaplus.shueisha.co.jp",
            searchUrlTemplate = "https://mangaplus.shueisha.co.jp/GlobalSearch/{query}",
            languageCode = "MULTI",
            engineLabel = "Portal Oficial",
            description = "Lançamentos simultâneos oficiais em Português, Inglês e Espanhol."
        ),

        // Catálogos em Espanhol (ES)
        CatalogDirectoryEntry(
            catalogId = "es_zonatmo",
            name = "ZonaTMO (TuMangaOnline)",
            baseUrl = "https://zonatmo.com",
            searchUrlTemplate = "https://zonatmo.com/library?title={query}",
            languageCode = "ES",
            engineLabel = "Portal Hispano",
            description = "Principal biblioteca colaborativa de mangás e manhwas em espanhol."
        ),
        CatalogDirectoryEntry(
            catalogId = "es_leercapitulo",
            name = "LeerCapitulo",
            baseUrl = "https://www.leercapitulo.co",
            searchUrlTemplate = "https://www.leercapitulo.co/buscar/{query}/",
            languageCode = "ES",
            engineLabel = "Portal Web",
            description = "Leitura rápida de capítulos traduzidos para o espanhol."
        ),

        // Servidores Pessoais / OPDS
        CatalogDirectoryEntry(
            catalogId = "opds_komga",
            name = "Servidor Komga (OPDS)",
            baseUrl = "http://192.168.1.100:25600",
            searchUrlTemplate = "http://192.168.1.100:25600/opds/v1.2/catalog?search={query}",
            languageCode = "OPDS",
            engineLabel = "Servidor OPDS",
            description = "Conecte seu próprio servidor doméstico Komga via protocolo OPDS v1.2.",
            kind = BuiltInCatalogKind.OPDS_SERVER
        ),
        CatalogDirectoryEntry(
            catalogId = "opds_kavita",
            name = "Servidor Kavita (OPDS)",
            baseUrl = "http://192.168.1.100:5000",
            searchUrlTemplate = "http://192.168.1.100:5000/api/opds/seu-token?query={query}",
            languageCode = "OPDS",
            engineLabel = "Servidor OPDS",
            description = "Integre sua biblioteca auto-hospedada Kavita via feed OPDS XML.",
            kind = BuiltInCatalogKind.OPDS_SERVER
        )
    )

    fun normalizeOrigin(rawUrl: String): String {
        val withScheme = rawUrl.trim().let {
            if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
        }
        val parsed = withScheme.toHttpUrlOrNull() ?: return withScheme.trimEnd('/')
        val portSuffix = if (parsed.port != 80 && parsed.port != 443) ":${parsed.port}" else ""
        return "${parsed.scheme}://${parsed.host}$portSuffix"
    }

    fun defaultFaviconUrl(baseUrl: String): String {
        val parsed = normalizeOrigin(baseUrl).toHttpUrlOrNull() ?: return ""
        val host = parsed.host.lowercase()
        // Unconfigured template LAN IP renders centered monogram immediately without timing out
        if (host == "192.168.1.100") return ""
        // Local LAN IPs shouldn't query external favicon services; use direct /favicon.ico
        if (host.startsWith("192.168.") || host.startsWith("10.") || host == "localhost" || host == "127.0.0.1") {
            return "${parsed.scheme}://${parsed.host}:${parsed.port}/favicon.ico"
        }
        return "https://www.google.com/s2/favicons?domain=$host&sz=128"
    }

    fun directOriginFaviconUrl(baseUrl: String): String {
        val origin = normalizeOrigin(baseUrl)
        return if (origin.isBlank()) "" else "$origin/favicon.ico"
    }

    fun extractFaviconFromDocument(doc: Document, pageUrl: String): String {
        val selectors = listOf(
            "link[rel~=(?i)apple-touch-icon]",
            "link[rel~=(?i)icon][sizes]",
            "link[rel~=(?i)shortcut icon]",
            "link[rel~=(?i)icon]",
            "meta[property=og:image]"
        )
        for (sel in selectors) {
            val el = doc.selectFirst(sel) ?: continue
            val candidate = if (el.tagName().equals("meta", ignoreCase = true)) {
                el.absUrl("content").ifBlank { el.attr("content") }
            } else {
                el.absUrl("href").ifBlank { el.attr("href") }
            }.trim()
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                return candidate
            }
        }
        return directOriginFaviconUrl(pageUrl)
    }
}
