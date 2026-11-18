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
    version = 2,
    exportSchema = false
)
abstract class KotatsuDatabase : RoomDatabase() {
    abstract fun kotatsuDao(): KotatsuDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        fun build(context: Context): KotatsuDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                KotatsuDatabase::class.java,
                "kotatsu_compose.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }
}
