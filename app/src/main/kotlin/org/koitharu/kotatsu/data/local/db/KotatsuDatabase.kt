package org.koitharu.kotatsu.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MangaEntity::class,
        FavoriteCategoryEntity::class,
        MangaCategoryCrossRef::class,
        ReadingHistoryEntity::class,
        CompletedChapterEntity::class,
        PageBookmarkEntity::class,
        CustomSourceEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class KotatsuDatabase : RoomDatabase() {
    abstract fun kotatsuDao(): KotatsuDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mangas ADD COLUMN sourceUrl TEXT")
                db.execSQL("ALTER TABLE mangas ADD COLUMN sourceLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_sources` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `baseUrl` TEXT NOT NULL,
                        `searchUrlTemplate` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mangas ADD COLUMN knownChaptersCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE mangas ADD COLUMN newChaptersDelta INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_sources ADD COLUMN faviconUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE custom_sources ADD COLUMN languageCode TEXT NOT NULL DEFAULT 'ALL'")
                db.execSQL("ALTER TABLE custom_sources ADD COLUMN catalogType TEXT NOT NULL DEFAULT 'WEB'")
            }
        }

        fun build(context: Context): KotatsuDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                KotatsuDatabase::class.java,
                "kotatsu_compose.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }
}
