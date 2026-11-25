package com.dualsub.player.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ResumeEntry::class, SubtitleSelectionEntry::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun resumeDao(): ResumeDao
    abstract fun subtitleSelectionDao(): SubtitleSelectionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS subtitle_selections (
                        videoName TEXT NOT NULL,
                        videoSize INTEGER NOT NULL,
                        s1Descriptor TEXT,
                        s2Descriptor TEXT,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(videoName, videoSize)
                    )""".trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dualsub.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
