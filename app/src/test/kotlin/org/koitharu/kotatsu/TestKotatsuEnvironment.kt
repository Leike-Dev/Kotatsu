package org.koitharu.kotatsu

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.data.local.backup.BackupManager
import org.koitharu.kotatsu.data.local.cbz.LocalCbzRepository
import org.koitharu.kotatsu.data.local.db.KotatsuDao
import org.koitharu.kotatsu.data.local.db.KotatsuDatabase
import org.koitharu.kotatsu.data.local.prefs.UserPreferencesRepository
import org.koitharu.kotatsu.data.remote.CustomSiteParser
import org.koitharu.kotatsu.data.remote.KitsuApi
import org.koitharu.kotatsu.data.remote.MangaDexApi
import org.koitharu.kotatsu.data.repository.MangaRepository
import java.io.File
import java.util.UUID

/**
 * Isolated local JVM/Robolectric test harness providing an in-memory Room database
 * and a dedicated temporary DataStore instance per test.
 */
class TestKotatsuEnvironment {
    val context: Context = ApplicationProvider.getApplicationContext()
    val tempDir: File = File(context.cacheDir, "kotatsu_test_${UUID.randomUUID()}").apply {
        mkdirs()
    }

    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
        File(tempDir, "test_kotatsu_prefs.preferences_pb")
    }

    val preferencesRepository = UserPreferencesRepository(dataStore)

    val database: KotatsuDatabase = Room.inMemoryDatabaseBuilder(
        context,
        KotatsuDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()

    val dao: KotatsuDao = database.kotatsuDao()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
    val customSiteParser = CustomSiteParser(okHttpClient)
    val localCbzRepository = LocalCbzRepository(context)
    val backupManager = BackupManager(
        context = context,
        dao = dao,
        preferencesRepository = preferencesRepository
    )

    val repository = MangaRepository(
        appContext = context,
        dao = dao,
        api = MangaDexApi(okHttpClient),
        kitsuApi = KitsuApi(okHttpClient),
        customSiteParser = customSiteParser,
        localCbzRepository = localCbzRepository,
        backupManager = backupManager,
        preferencesRepository = preferencesRepository
    )

    fun close() {
        database.close()
        dataStoreScope.cancel()
        tempDir.deleteRecursively()
    }
}
