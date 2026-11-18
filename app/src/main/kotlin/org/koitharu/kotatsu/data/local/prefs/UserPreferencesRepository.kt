package org.koitharu.kotatsu.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koitharu.kotatsu.domain.model.AppPreferences
import org.koitharu.kotatsu.domain.model.AppThemeMode
import org.koitharu.kotatsu.domain.model.ChapterDisplayMode
import org.koitharu.kotatsu.domain.model.DohProvider
import org.koitharu.kotatsu.domain.model.ExploreLayoutMode
import org.koitharu.kotatsu.domain.model.LanguageFilter
import org.koitharu.kotatsu.domain.model.ReaderBackground
import org.koitharu.kotatsu.domain.model.ReaderMode
import org.koitharu.kotatsu.domain.model.ReaderScaleMode

private val Context.kotatsuDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kotatsu_preferences"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val SHOW_SOURCE_BADGES = booleanPreferencesKey("show_source_badges")

        val EXPLORE_LAYOUT = stringPreferencesKey("explore_layout")
        val SHOW_EXPLORE_SPOTLIGHT = booleanPreferencesKey("show_explore_spotlight")
        val SHOW_EXPLORE_SOURCES = booleanPreferencesKey("show_explore_sources")
        val SHOW_EXPLORE_GENRES = booleanPreferencesKey("show_explore_genres")
        val SHOW_EXPLORE_LOCAL = booleanPreferencesKey("show_explore_local")
        val ENABLE_KITSU = booleanPreferencesKey("enable_kitsu_catalog")
        val HIDE_NSFW = booleanPreferencesKey("hide_nsfw")
        val LANGUAGE = stringPreferencesKey("preferred_language")

        val CHAPTER_DISPLAY_MODE = stringPreferencesKey("chapter_display_mode")

        val READER_MODE = stringPreferencesKey("reader_mode")
        val READER_SCALE = stringPreferencesKey("reader_scale")
        val READER_BG = stringPreferencesKey("reader_background")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VOLUME_KEYS_NAV = booleanPreferencesKey("volume_keys_nav")
        val PAGE_TRANSITIONS = booleanPreferencesKey("page_transitions")
        val SHOW_READER_THUMBNAILS = booleanPreferencesKey("show_reader_thumbnails")
        val SHOW_READER_MINI_INFO = booleanPreferencesKey("show_reader_mini_info")
        val WEBTOON_SEAMLESS_GAP = booleanPreferencesKey("webtoon_seamless_gap")
        val READER_BRIGHTNESS_DIM = floatPreferencesKey("reader_brightness_dim")
        val READER_GRAYSCALE = booleanPreferencesKey("reader_grayscale")
        val READER_INVERT = booleanPreferencesKey("reader_invert")
        val DATA_SAVER = booleanPreferencesKey("data_saver")

        val AD_SHIELD = booleanPreferencesKey("ad_shield_enabled")
        val ANTI_ADBLOCK_BYPASS = booleanPreferencesKey("anti_adblock_bypass")
        val BLOCK_POPUPS = booleanPreferencesKey("block_popups_redirects")
        val DOH_PROVIDER = stringPreferencesKey("doh_provider")
        val CUSTOM_UA = stringPreferencesKey("custom_user_agent")

        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        val APP_LOCK_SYSTEM_CRED = booleanPreferencesKey("app_lock_system_cred")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen_enabled")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")

        val LEGACY_DB_IMPORTED = booleanPreferencesKey("legacy_kotatsu_db_imported")
    }

    val preferencesFlow: Flow<AppPreferences> = context.kotatsuDataStore.data.map { prefs ->
        AppPreferences(
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.DARK,
            amoledDark = prefs[Keys.AMOLED_DARK] ?: false,
            dynamicColors = prefs[Keys.DYNAMIC_COLORS] ?: false,
            showSourceBadges = prefs[Keys.SHOW_SOURCE_BADGES] ?: true,

            exploreLayoutMode = prefs[Keys.EXPLORE_LAYOUT]
                ?.let { runCatching { ExploreLayoutMode.valueOf(it) }.getOrNull() }
                ?: ExploreLayoutMode.GRID_STANDARD,
            showExploreSpotlight = prefs[Keys.SHOW_EXPLORE_SPOTLIGHT] ?: true,
            showExploreSourcesBar = prefs[Keys.SHOW_EXPLORE_SOURCES] ?: true,
            showExploreGenres = prefs[Keys.SHOW_EXPLORE_GENRES] ?: true,
            showExploreLocalSection = prefs[Keys.SHOW_EXPLORE_LOCAL] ?: true,
            enableKitsuCatalog = prefs[Keys.ENABLE_KITSU] ?: true,
            hideNsfw = prefs[Keys.HIDE_NSFW] ?: true,
            preferredLanguage = prefs[Keys.LANGUAGE]
                ?.let { runCatching { LanguageFilter.valueOf(it) }.getOrNull() }
                ?: LanguageFilter.ALL,

            chapterDisplayMode = prefs[Keys.CHAPTER_DISPLAY_MODE]
                ?.let { runCatching { ChapterDisplayMode.valueOf(it) }.getOrNull() }
                ?: ChapterDisplayMode.LIST,

            defaultReaderMode = prefs[Keys.READER_MODE]
                ?.let { runCatching { ReaderMode.valueOf(it) }.getOrNull() }
                ?: ReaderMode.STANDARD_LTR,
            readerScaleMode = prefs[Keys.READER_SCALE]
                ?.let { runCatching { ReaderScaleMode.valueOf(it) }.getOrNull() }
                ?: ReaderScaleMode.FIT_SCREEN,
            readerBackground = prefs[Keys.READER_BG]
                ?.let { runCatching { ReaderBackground.valueOf(it) }.getOrNull() }
                ?: ReaderBackground.SUMI_INK,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            volumeKeysNavigation = prefs[Keys.VOLUME_KEYS_NAV] ?: false,
            pageTransitions = prefs[Keys.PAGE_TRANSITIONS] ?: true,
            showReaderThumbnails = prefs[Keys.SHOW_READER_THUMBNAILS] ?: true,
            showReaderMiniInfo = prefs[Keys.SHOW_READER_MINI_INFO] ?: true,
            webtoonSeamlessGap = prefs[Keys.WEBTOON_SEAMLESS_GAP] ?: true,
            readerBrightnessDim = (prefs[Keys.READER_BRIGHTNESS_DIM] ?: 0f).coerceIn(0f, 0.65f),
            readerGrayscale = prefs[Keys.READER_GRAYSCALE] ?: false,
            readerInvertColors = prefs[Keys.READER_INVERT] ?: false,
            dataSaver = prefs[Keys.DATA_SAVER] ?: false,

            adShieldEnabled = prefs[Keys.AD_SHIELD] ?: true,
            antiAdblockBypass = prefs[Keys.ANTI_ADBLOCK_BYPASS] ?: true,
            blockPopupsRedirects = prefs[Keys.BLOCK_POPUPS] ?: true,
            dohProvider = prefs[Keys.DOH_PROVIDER]
                ?.let { runCatching { DohProvider.valueOf(it) }.getOrNull() }
                ?: DohProvider.NONE,
            customUserAgent = prefs[Keys.CUSTOM_UA]?.takeIf { it.isNotBlank() }
                ?: AppPreferences.DEFAULT_BROWSER_UA,

            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            appLockPin = prefs[Keys.APP_LOCK_PIN] ?: "",
            appLockSystemCredential = prefs[Keys.APP_LOCK_SYSTEM_CRED] ?: true,
            secureScreenEnabled = prefs[Keys.SECURE_SCREEN] ?: false,
            incognitoMode = prefs[Keys.INCOGNITO_MODE] ?: false
        )
    }

    suspend fun isLegacyDbImported(): Boolean =
        context.kotatsuDataStore.data.first()[Keys.LEGACY_DB_IMPORTED] ?: false

    suspend fun markLegacyDbImported() {
        context.kotatsuDataStore.edit { it[Keys.LEGACY_DB_IMPORTED] = true }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.kotatsuDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.AMOLED_DARK] = enabled }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.DYNAMIC_COLORS] = enabled }
    }

    suspend fun setShowSourceBadges(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.SHOW_SOURCE_BADGES] = enabled }
    }

    suspend fun setExploreLayoutMode(mode: ExploreLayoutMode) {
        context.kotatsuDataStore.edit { it[Keys.EXPLORE_LAYOUT] = mode.name }
    }

    suspend fun setShowExploreSpotlight(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.SHOW_EXPLORE_SPOTLIGHT] = enabled }
    }

    suspend fun setShowExploreSourcesBar(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.SHOW_EXPLORE_SOURCES] = enabled }
    }

    suspend fun setShowExploreGenres(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.SHOW_EXPLORE_GENRES] = enabled }
    }

    suspend fun setShowExploreLocalSection(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.SHOW_EXPLORE_LOCAL] = enabled }
    }

    suspend fun setEnableKitsuCatalog(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.ENABLE_KITSU] = enabled }
    }

    suspend fun setHideNsfw(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.HIDE_NSFW] = enabled }
    }

    suspend fun setPreferredLanguage(language: LanguageFilter) {
        context.kotatsuDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setChapterDisplayMode(mode: ChapterDisplayMode) {
        context.kotatsuDataStore.edit { it[Keys.CHAPTER_DISPLAY_MODE] = mode.name }
    }

    suspend fun setDefaultReaderMode(mode: ReaderMode) {
        context.kotatsuDataStore.edit { it[Keys.READER_MODE] = mode.name }
    }

    suspend fun setReaderScaleMode(mode: ReaderScaleMode) {
        context.kotatsuDataStore.edit { it[Keys.READER_SCALE] = mode.name }
    }

    suspend fun setReaderBackground(background: ReaderBackground) {
        context.kotatsuDataStore.edit { it[Keys.READER_BG] = background.name }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setVolumeKeysNavigation(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.VOLUME_KEYS_NAV] = enabled }
    }

    suspend fun setPageTransitions(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.PAGE_TRANSITIONS] = enabled }
    }

    suspend fun setShowReaderThumbnails(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.SHOW_READER_THUMBNAILS] = enabled }
    }

    suspend fun setShowReaderMiniInfo(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.SHOW_READER_MINI_INFO] = enabled }
    }

    suspend fun setWebtoonSeamlessGap(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.WEBTOON_SEAMLESS_GAP] = enabled }
    }

    suspend fun setReaderBrightnessDim(dim: Float) {
        context.kotatsuDataStore.edit { it[Keys.READER_BRIGHTNESS_DIM] = dim.coerceIn(0f, 0.65f) }
    }

    suspend fun setReaderGrayscale(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.READER_GRAYSCALE] = enabled }
    }

    suspend fun setReaderInvertColors(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.READER_INVERT] = enabled }
    }

    suspend fun setDataSaver(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.DATA_SAVER] = enabled }
    }

    suspend fun setAdShieldEnabled(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.AD_SHIELD] = enabled }
    }

    suspend fun setAntiAdblockBypass(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.ANTI_ADBLOCK_BYPASS] = enabled }
    }

    suspend fun setBlockPopupsRedirects(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.BLOCK_POPUPS] = enabled }
    }

    suspend fun setDohProvider(provider: DohProvider) {
        context.kotatsuDataStore.edit { it[Keys.DOH_PROVIDER] = provider.name }
    }

    suspend fun setCustomUserAgent(userAgent: String) {
        context.kotatsuDataStore.edit {
            it[Keys.CUSTOM_UA] = userAgent.trim().ifBlank { AppPreferences.DEFAULT_BROWSER_UA }
        }
    }

    suspend fun configureAppLock(enabled: Boolean, pin: String) {
        context.kotatsuDataStore.edit {
            it[Keys.APP_LOCK_ENABLED] = enabled && pin.length == 4
            it[Keys.APP_LOCK_PIN] = if (enabled) pin else ""
        }
    }

    suspend fun setAppLockSystemCredential(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.APP_LOCK_SYSTEM_CRED] = enabled }
    }

    suspend fun setSecureScreenEnabled(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.SECURE_SCREEN] = enabled }
    }

    suspend fun setIncognitoMode(enabled: Boolean) {
        context.kotatsuDataStore.edit { it[Keys.INCOGNITO_MODE] = enabled }
    }

    suspend fun exportSimpleMap(): Map<String, String> {
        val current = preferencesFlow.first()
        return mapOf(
            "themeMode" to current.themeMode.name,
            "amoledDark" to current.amoledDark.toString(),
            "dynamicColors" to current.dynamicColors.toString(),
            "showSourceBadges" to current.showSourceBadges.toString(),
            "exploreLayoutMode" to current.exploreLayoutMode.name,
            "showExploreSpotlight" to current.showExploreSpotlight.toString(),
            "showExploreSourcesBar" to current.showExploreSourcesBar.toString(),
            "showExploreGenres" to current.showExploreGenres.toString(),
            "showExploreLocalSection" to current.showExploreLocalSection.toString(),
            "enableKitsuCatalog" to current.enableKitsuCatalog.toString(),
            "hideNsfw" to current.hideNsfw.toString(),
            "preferredLanguage" to current.preferredLanguage.name,
            "chapterDisplayMode" to current.chapterDisplayMode.name,
            "defaultReaderMode" to current.defaultReaderMode.name,
            "readerScaleMode" to current.readerScaleMode.name,
            "readerBackground" to current.readerBackground.name,
            "keepScreenOn" to current.keepScreenOn.toString(),
            "volumeKeysNavigation" to current.volumeKeysNavigation.toString(),
            "pageTransitions" to current.pageTransitions.toString(),
            "showReaderThumbnails" to current.showReaderThumbnails.toString(),
            "showReaderMiniInfo" to current.showReaderMiniInfo.toString(),
            "webtoonSeamlessGap" to current.webtoonSeamlessGap.toString(),
            "readerBrightnessDim" to current.readerBrightnessDim.toString(),
            "readerGrayscale" to current.readerGrayscale.toString(),
            "readerInvertColors" to current.readerInvertColors.toString(),
            "dataSaver" to current.dataSaver.toString(),
            "adShieldEnabled" to current.adShieldEnabled.toString(),
            "antiAdblockBypass" to current.antiAdblockBypass.toString(),
            "blockPopupsRedirects" to current.blockPopupsRedirects.toString(),
            "dohProvider" to current.dohProvider.name,
            "customUserAgent" to current.customUserAgent,
            "secureScreenEnabled" to current.secureScreenEnabled.toString()
        )
    }

    suspend fun restoreFromSimpleMap(map: Map<String, String>) {
        context.kotatsuDataStore.edit { prefs ->
            map["themeMode"]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?.let { prefs[Keys.THEME_MODE] = it.name }
            map["amoledDark"]?.toBooleanStrictOrNull()?.let { prefs[Keys.AMOLED_DARK] = it }
            map["dynamicColors"]?.toBooleanStrictOrNull()?.let { prefs[Keys.DYNAMIC_COLORS] = it }
            map["showSourceBadges"]?.toBooleanStrictOrNull()?.let { prefs[Keys.SHOW_SOURCE_BADGES] = it }
            map["exploreLayoutMode"]?.let { runCatching { ExploreLayoutMode.valueOf(it) }.getOrNull() }
                ?.let { prefs[Keys.EXPLORE_LAYOUT] = it.name }
            map["showExploreSpotlight"]?.toBooleanStrictOrNull()?.let { prefs[Keys.SHOW_EXPLORE_SPOTLIGHT] = it }
            map["showExploreSourcesBar"]?.toBooleanStrictOrNull()?.let { prefs[Keys.SHOW_EXPLORE_SOURCES] = it }
            map["showExploreGenres"]?.toBooleanStrictOrNull()?.let { prefs[Keys.SHOW_EXPLORE_GENRES] = it }
            map["showExploreLocalSection"]?.toBooleanStrictOrNull()?.let { prefs[Keys.SHOW_EXPLORE_LOCAL] = it }
            map["enableKitsuCatalog"]?.toBooleanStrictOrNull()?.let { prefs[Keys.ENABLE_KITSU] = it }
            map["hideNsfw"]?.toBooleanStrictOrNull()?.let { prefs[Keys.HIDE_NSFW] = it }
            map["preferredLanguage"]?.let { runCatching { LanguageFilter.valueOf(it) }.getOrNull() }
                ?.let { prefs[Keys.LANGUAGE] = it.name }
            map["chapterDisplayMode"]?.let { runCatching { ChapterDisplayMode.valueOf(it) }.getOrNull() }
                ?.let { prefs[Keys.CHAPTER_DISPLAY_MODE] = it.name }
            map["defaultReaderMode"]?.let { runCatching { ReaderMode.valueOf(it) }.getOrNull() }
                ?.let { prefs[Keys.READER_MODE] = it.name }
            map["readerScaleMode"]?.let { runCatching { ReaderScaleMode.valueOf(it) }.getOrNull() }
                ?.let { prefs[Keys.READER_SCALE] = it.name }
            map["readerBackground"]?.let { runCatching { ReaderBackground.valueOf(it) }.getOrNull() }
                ?.let { prefs[Keys.READER_BG] = it.name }
            map["keepScreenOn"]?.toBooleanStrictOrNull()?.let { prefs[Keys.KEEP_SCREEN_ON] = it }
            map["volumeKeysNavigation"]?.toBooleanStrictOrNull()?.let { prefs[Keys.VOLUME_KEYS_NAV] = it }
            map["pageTransitions"]?.toBooleanStrictOrNull()?.let { prefs[Keys.PAGE_TRANSITIONS] = it }
            map["showReaderThumbnails"]?.toBooleanStrictOrNull()?.let { prefs[Keys.SHOW_READER_THUMBNAILS] = it }
            map["showReaderMiniInfo"]?.toBooleanStrictOrNull()?.let { prefs[Keys.SHOW_READER_MINI_INFO] = it }
            map["webtoonSeamlessGap"]?.toBooleanStrictOrNull()?.let { prefs[Keys.WEBTOON_SEAMLESS_GAP] = it }
            map["readerBrightnessDim"]?.toFloatOrNull()
                ?.let { prefs[Keys.READER_BRIGHTNESS_DIM] = it.coerceIn(0f, 0.65f) }
            map["readerGrayscale"]?.toBooleanStrictOrNull()?.let { prefs[Keys.READER_GRAYSCALE] = it }
            map["readerInvertColors"]?.toBooleanStrictOrNull()?.let { prefs[Keys.READER_INVERT] = it }
            map["dataSaver"]?.toBooleanStrictOrNull()?.let { prefs[Keys.DATA_SAVER] = it }
            map["adShieldEnabled"]?.toBooleanStrictOrNull()?.let { prefs[Keys.AD_SHIELD] = it }
            map["antiAdblockBypass"]?.toBooleanStrictOrNull()?.let { prefs[Keys.ANTI_ADBLOCK_BYPASS] = it }
            map["blockPopupsRedirects"]?.toBooleanStrictOrNull()?.let { prefs[Keys.BLOCK_POPUPS] = it }
            map["dohProvider"]?.let { runCatching { DohProvider.valueOf(it) }.getOrNull() }
                ?.let { prefs[Keys.DOH_PROVIDER] = it.name }
            map["customUserAgent"]?.takeIf { it.isNotBlank() }?.let { prefs[Keys.CUSTOM_UA] = it }
            map["secureScreenEnabled"]?.toBooleanStrictOrNull()?.let { prefs[Keys.SECURE_SCREEN] = it }
        }
    }
}
