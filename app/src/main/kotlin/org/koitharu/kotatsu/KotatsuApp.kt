package org.koitharu.kotatsu

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.data.local.backup.BackupManager
import org.koitharu.kotatsu.data.local.cbz.LocalCbzRepository
import org.koitharu.kotatsu.data.local.db.KotatsuDatabase
import org.koitharu.kotatsu.data.local.prefs.UserPreferencesRepository
import org.koitharu.kotatsu.data.remote.ConfigurableDns
import org.koitharu.kotatsu.data.remote.CustomSiteParser
import org.koitharu.kotatsu.data.remote.KitsuApi
import org.koitharu.kotatsu.data.remote.MangaDexApi
import org.koitharu.kotatsu.data.remote.WebProtectionEngine
import org.koitharu.kotatsu.data.remote.WebViewCookieJar
import org.koitharu.kotatsu.data.repository.MangaRepository
import java.util.concurrent.TimeUnit

class AppContainer(context: Application) {

    val preferencesRepository = UserPreferencesRepository(context)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(WebViewCookieJar())
        .dns(ConfigurableDns(WebProtectionEngine.activeDohProvider))
        .addInterceptor { chain ->
            val original = chain.request()
            val host = original.url.host.lowercase()
            val builder = original.newBuilder()

            if (original.header("User-Agent") == null) {
                val ua = if (host.contains("mangadex.org")) {
                    "KotatsuCompose/10.1 (Android; Mobile)"
                } else {
                    WebProtectionEngine.activeUserAgent.get()
                }
                builder.header("User-Agent", ua)
            }

            if (original.header("Referer") == null &&
                !host.contains("mangadex.org") &&
                !host.contains("kitsu.io")
            ) {
                builder.header("Referer", "${original.url.scheme}://${original.url.host}/")
            }

            chain.proceed(builder.build())
        }
        .build()

    private val database: KotatsuDatabase = KotatsuDatabase.build(context)
    private val mangaDexApi = MangaDexApi(okHttpClient)
    private val kitsuApi = KitsuApi(okHttpClient)
    private val customSiteParser = CustomSiteParser(okHttpClient)
    private val localCbzRepository = LocalCbzRepository(context)
    private val backupManager = BackupManager(
        context = context,
        dao = database.kotatsuDao(),
        preferencesRepository = preferencesRepository
    )

    val mangaRepository: MangaRepository = MangaRepository(
        appContext = context,
        dao = database.kotatsuDao(),
        api = mangaDexApi,
        kitsuApi = kitsuApi,
        customSiteParser = customSiteParser,
        localCbzRepository = localCbzRepository,
        backupManager = backupManager,
        preferencesRepository = preferencesRepository
    )
}

class KotatsuApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.mangaRepository.runStartupInitialization()
        }
        applicationScope.launch {
            container.preferencesRepository.preferencesFlow.collectLatest { prefs ->
                WebProtectionEngine.activeUserAgent.set(prefs.customUserAgent)
                WebProtectionEngine.activeDohProvider.set(prefs.dohProvider)
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
            }
            .crossfade(true)
            .build()
    }
}
