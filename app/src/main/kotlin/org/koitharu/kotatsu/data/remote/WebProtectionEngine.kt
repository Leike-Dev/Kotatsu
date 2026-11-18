package org.koitharu.kotatsu.data.remote

import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.DohProvider
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Bidirectional CookieJar bridging Android WebView's CookieManager with OkHttpClient.
 * When a user solves a Cloudflare / anti-bot challenge in SourceBrowserScreen,
 * cf_clearance and session cookies are immediately available to OkHttp requests.
 */
class WebViewCookieJar : CookieJar {
    private val cookieManager: CookieManager? = runCatching {
        CookieManager.getInstance().apply { setAcceptCookie(true) }
    }.getOrNull()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cm = cookieManager ?: return
        val urlString = url.toString()
        cookies.forEach { cookie ->
            runCatching { cm.setCookie(urlString, cookie.toString()) }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cm = cookieManager ?: return emptyList()
        val rawCookieHeader = runCatching { cm.getCookie(url.toString()) }.getOrNull()
        if (rawCookieHeader.isNullOrBlank()) return emptyList()
        return rawCookieHeader.split(";")
            .mapNotNull { part -> Cookie.parse(url, part.trim()) }
    }
}

/**
 * Dynamic DNS-over-HTTPS (DoH) resolver supporting Cloudflare (1.1.1.1) and Google (8.8.8.8)
 * using RFC 8484 JSON endpoints to bypass ISP DNS blocks on manga sites, with fallback to system DNS.
 */
class ConfigurableDns(
    private val providerRef: AtomicReference<DohProvider>
) : Dns {

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class DnsJsonResponse(
        val Answer: List<DnsAnswerDto> = emptyList()
    )

    @Serializable
    private data class DnsAnswerDto(
        val type: Int = 0,
        val data: String = ""
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val provider = providerRef.get()
        if (provider == DohProvider.NONE ||
            hostname == "cloudflare-dns.com" ||
            hostname == "dns.google"
        ) {
            return Dns.SYSTEM.lookup(hostname)
        }

        val endpoint = when (provider) {
            DohProvider.CLOUDFLARE -> "https://1.1.1.1/dns-query"
            DohProvider.GOOGLE -> "https://8.8.8.8/resolve"
            DohProvider.NONE -> return Dns.SYSTEM.lookup(hostname)
        }

        return runCatching {
            val url = endpoint.toHttpUrl().newBuilder()
                .addQueryParameter("name", hostname)
                .addQueryParameter("type", "A")
                .build()
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .build()

            bootstrapClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val body = resp.body?.string().orEmpty()
                val parsed = json.decodeFromString<DnsJsonResponse>(body)
                parsed.Answer
                    .filter { it.type == 1 && it.data.isNotBlank() }
                    .mapNotNull { ans ->
                        runCatching { InetAddress.getByName(ans.data) }.getOrNull()
                    }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: Dns.SYSTEM.lookup(hostname)
    }
}

/**
 * Multi-layered Anti-Adblock Bypass & Ad/Popup Protection Engine.
 * - Uses 200 OK JS & 1x1 GIF surrogates instead of empty network errors so anti-adblock scripts
 *   (FuckAdBlock, BlockAdBlock, Admiral, Google AdSense checks) think ads loaded normally.
 * - Injects stealth DOM bait preservation and scroll-unlocking scripts into WebView.
 */
object WebProtectionEngine {

    val activeUserAgent = AtomicReference(AppPreferences.DEFAULT_BROWSER_UA)
    val activeDohProvider = AtomicReference(DohProvider.NONE)

    private val blockedAdDomains = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adsterra.com",
        "exoclick.com",
        "juicyads.com",
        "trafficjunky.net",
        "hilltopads.net",
        "clickadu.com",
        "galaksion.com",
        "admaven.com",
        "outbrain.com",
        "taboola.com",
        "mgid.com",
        "revcontent.com",
        "yandex.ru/metrika",
        "admiral.com",
        "getadmiral.com",
        "adrecover.com",
        "blockadblock.com",
        "fuckadblock"
    )

    private val blockedPathPatterns = listOf(
        "/ads.js",
        "/adframe.js",
        "/adblock-detect",
        "/anti-adblock",
        "/fuckadblock",
        "/blockadblock",
        "/popunder",
        "/prebid"
    )

    private val transparentGifBytes: ByteArray = Base64.decode(
        "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
        Base64.DEFAULT
    )

    // Surrogate JS that satisfies common anti-adblock global variable and class checks
    private const val SURROGATE_JS = """
        (function() {
            window.canRunAds = true;
            window.isAdBlockActive = false;
            window.adBlockEnabled = false;
            window.google_ad_status = 1;
            window.adsbygoogle = window.adsbygoogle || { loaded: true, push: function(){} };
            var noop = function() { return this; };
            noop.prototype.on = noop;
            noop.prototype.onDetected = noop;
            noop.prototype.onNotDetected = function(fn) { if (typeof fn === 'function') fn(); return this; };
            noop.prototype.check = function() { return false; };
            window.FuckAdBlock = noop;
            window.BlockAdBlock = noop;
            window.fuckAdBlock = new noop();
            window.blockAdBlock = new noop();
        })();
    """

    fun isAdOrAntiAdblockResource(url: String): Boolean {
        val lower = url.lowercase()
        if (blockedAdDomains.any { lower.contains(it) }) return true
        if (blockedPathPatterns.any { lower.contains(it) }) return true
        return false
    }

    fun interceptWebViewRequest(
        request: WebResourceRequest,
        adShieldEnabled: Boolean,
        antiAdblockBypass: Boolean,
        onBlocked: () -> Unit
    ): WebResourceResponse? {
        if (!adShieldEnabled) return null
        val url = request.url?.toString().orEmpty()
        if (url.isBlank() || !isAdOrAntiAdblockResource(url)) return null

        onBlocked()
        val lowerPath = request.url?.path?.lowercase().orEmpty()
        val accept = request.requestHeaders["Accept"]?.lowercase().orEmpty()

        // If anti-adblock camouflage is active, return valid 200 OK surrogates so onload fires!
        if (antiAdblockBypass) {
            return when {
                lowerPath.endsWith(".js") || accept.contains("javascript") || accept.contains("script") -> {
                    WebResourceResponse(
                        "application/javascript",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        ByteArrayInputStream(SURROGATE_JS.toByteArray(Charsets.UTF_8))
                    )
                }

                lowerPath.endsWith(".png") || lowerPath.endsWith(".gif") ||
                    lowerPath.endsWith(".jpg") || lowerPath.endsWith(".webp") ||
                    accept.contains("image/") -> {
                    WebResourceResponse(
                        "image/gif",
                        null,
                        200,
                        "OK",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        ByteArrayInputStream(transparentGifBytes)
                    )
                }

                else -> {
                    WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
            }
        }

        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * JavaScript injected into the in-app WebView to neutralize anti-adblock modals,
     * keep bait elements reporting non-zero dimensions, and block forced popup redirects.
     */
    fun buildStealthDomScript(
        antiAdblockBypass: Boolean,
        blockPopups: Boolean
    ): String = buildString {
        append("(function(){ try { ")
        if (blockPopups) {
            append(
                """
                window.open = function() { return null; };
                """
            )
        }
        if (antiAdblockBypass) {
            append(SURROGATE_JS)
            append(
                """
                var cleanAntiAdblockWalls = function() {
                    var selectors = [
                        '[id*="adblock" i]', '[class*="adblock" i]',
                        '[id*="adb-modal" i]', '[class*="adb-overlay" i]',
                        '.fc-ab-root', '.admiral-modal', '#detect-adblock'
                    ];
                    selectors.forEach(function(sel) {
                        document.querySelectorAll(sel).forEach(function(el) {
                            if (el && el.tagName !== 'BODY' && el.tagName !== 'HTML') {
                                el.style.setProperty('display', 'none', 'important');
                                el.style.setProperty('visibility', 'hidden', 'important');
                                el.style.setProperty('pointer-events', 'none', 'important');
                            }
                        });
                    });
                    if (document.body) {
                        var ov = window.getComputedStyle(document.body).overflow;
                        if (ov === 'hidden') {
                            document.body.style.setProperty('overflow', 'auto', 'important');
                        }
                    }
                    if (document.documentElement) {
                        var hov = window.getComputedStyle(document.documentElement).overflow;
                        if (hov === 'hidden') {
                            document.documentElement.style.setProperty('overflow', 'auto', 'important');
                        }
                    }
                };
                cleanAntiAdblockWalls();
                setInterval(cleanAntiAdblockWalls, 1400);
                """
            )
        }
        append(" } catch(e) {} })();")
    }
}
