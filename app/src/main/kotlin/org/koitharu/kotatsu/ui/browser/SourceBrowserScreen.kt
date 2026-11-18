@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.koitharu.kotatsu.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.json.JSONTokener
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.data.remote.WebProtectionEngine
import org.koitharu.kotatsu.data.repository.MangaRepository
import org.koitharu.kotatsu.domain.model.AppPreferences

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceBrowserScreen(
    initialUrl: String,
    initialTitle: String,
    repository: MangaRepository,
    onClose: () -> Unit,
    onOpenExtractedChapter: (mangaId: String, sourceTypeName: String, chapterId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by repository.preferencesRepository.preferencesFlow
        .collectAsStateWithLifecycle(initialValue = AppPreferences())
    val latestPrefs by rememberUpdatedState(prefs)

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentUrl by remember { mutableStateOf(initialUrl.ifBlank { "https://mangadex.org" }) }
    var addressInput by remember { mutableStateOf(currentUrl) }
    var pageTitle by remember { mutableStateOf(initialTitle) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var blockedCount by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { CookieManager.getInstance().flush() }
            webViewRef?.stopLoading()
        }
    }

    fun navigateInputUrl(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            "https://duckduckgo.com/?q=${Uri.encode("$trimmed manga")}"
        }
        addressInput = normalized
        webViewRef?.loadUrl(normalized)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 82.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("source_browser_screen")
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                // Top Protected Address & Anti-Adblock Shield Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.testTag("browser_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.close_action)
                                )
                            }

                            TextField(
                                value = addressInput,
                                onValueChange = { addressInput = it },
                                singleLine = true,
                                shape = CircleShape,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = { navigateInputUrl(addressInput) }
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("browser_url_input")
                            )

                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.testTag("browser_reload_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.reload_action)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (prefs.adShieldEnabled) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = if (prefs.adShieldEnabled) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val shieldText = when {
                                        !prefs.adShieldEnabled ->
                                            stringResource(R.string.browser_shield_off_status)
                                        prefs.antiAdblockBypass ->
                                            stringResource(R.string.browser_shield_stealth_status, blockedCount)
                                        else ->
                                            stringResource(R.string.browser_shield_active_status, blockedCount)
                                    }
                                    Text(
                                        text = shieldText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (prefs.adShieldEnabled) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }

                        if (loadProgress in 1..99) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { loadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Embedded Protected WebView
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadsImagesAutomatically = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString = latestPrefs.customUserAgent
                                setSupportMultipleWindows(latestPrefs.blockPopupsRedirects)
                                javaScriptCanOpenWindowsAutomatically = !latestPrefs.blockPopupsRedirects
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) {
                                        pageTitle = title
                                    }
                                }

                                // Block forced popup windows from ad/anti-adblock networks
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    if (latestPrefs.blockPopupsRedirects) {
                                        blockedCount++
                                        return false
                                    }
                                    return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    if (request == null) return null
                                    val snap = latestPrefs
                                    return WebProtectionEngine.interceptWebViewRequest(
                                        request = request,
                                        adShieldEnabled = snap.adShieldEnabled,
                                        antiAdblockBypass = snap.antiAdblockBypass,
                                        onBlocked = { blockedCount++ }
                                    ) ?: super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val reqUrl = request?.url?.toString().orEmpty()
                                    if (reqUrl.isBlank()) return false
                                    if (!reqUrl.startsWith("http://") && !reqUrl.startsWith("https://")) {
                                        blockedCount++
                                        return true
                                    }
                                    if (latestPrefs.adShieldEnabled && WebProtectionEngine.isAdOrAntiAdblockResource(reqUrl)) {
                                        blockedCount++
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                        addressInput = url
                                    }
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    val snap = latestPrefs
                                    if (snap.adShieldEnabled) {
                                        val stealthJs = WebProtectionEngine.buildStealthDomScript(
                                            antiAdblockBypass = snap.antiAdblockBypass,
                                            blockPopups = snap.blockPopupsRedirects
                                        )
                                        view?.evaluateJavascript(stealthJs, null)
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    runCatching { CookieManager.getInstance().flush() }
                                    val snap = latestPrefs
                                    if (snap.adShieldEnabled) {
                                        val stealthJs = WebProtectionEngine.buildStealthDomScript(
                                            antiAdblockBypass = snap.antiAdblockBypass,
                                            blockPopups = snap.blockPopupsRedirects
                                        )
                                        view?.evaluateJavascript(stealthJs, null)
                                    }
                                }
                            }

                            webViewRef = this
                            loadUrl(currentUrl)
                        }
                    },
                    update = { view ->
                        if (view.settings.userAgentString != latestPrefs.customUserAgent) {
                            view.settings.userAgentString = latestPrefs.customUserAgent
                        }
                        view.settings.setSupportMultipleWindows(latestPrefs.blockPopupsRedirects)
                        view.settings.javaScriptCanOpenWindowsAutomatically = !latestPrefs.blockPopupsRedirects
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            // Bottom Floating Expressive Toolbar for Reader Extraction & Saving Site
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back_action)
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = stringResource(R.string.forward_action)
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            val parsed = Uri.parse(currentUrl)
                            val host = parsed.host.orEmpty().removePrefix("www.")
                            val origin = "${parsed.scheme ?: "https"}://${parsed.host.orEmpty()}"
                            if (host.isNotBlank()) {
                                scope.launch {
                                    val siteName = host.substringBefore(".")
                                        .replaceFirstChar { it.uppercase() }
                                    repository.addCustomSource(
                                        name = siteName,
                                        baseUrl = origin,
                                        searchTemplate = "$origin/?s={query}"
                                    )
                                    snackbarHostState.showSnackbar("Site \"$siteName\" salvo nas suas Fontes!")
                                }
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("browser_save_site_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddLink,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.browser_save_source), maxLines = 1)
                    }

                    Button(
                        onClick = {
                            val view = webViewRef ?: return@Button
                            view.evaluateJavascript(
                                "(function(){ return document.documentElement.outerHTML; })();"
                            ) { encodedJsonHtml ->
                                val decodedHtml = runCatching {
                                    JSONTokener(encodedJsonHtml).nextValue()?.toString().orEmpty()
                                }.getOrDefault("")
                                scope.launch {
                                    val pair = repository.registerBrowserExtractedChapter(
                                        pageUrl = currentUrl,
                                        pageTitle = pageTitle,
                                        rawHtml = decodedHtml
                                    )
                                    if (pair != null) {
                                        val (manga, chapter) = pair
                                        onOpenExtractedChapter(
                                            manga.id,
                                            manga.sourceType.name,
                                            chapter.id
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            "Nenhuma página de capítulo detectada nesta tela."
                                        )
                                    }
                                }
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("browser_extract_reader_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.browser_extract_pages), maxLines = 1)
                    }
                }
            }
        }
    }
}
