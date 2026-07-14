package com.securevault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, Entry::class],
    version = 9,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        //  Миграция для добавления полей гибкой защиты доступа
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE entries ADD COLUMN password_access_mode TEXT NOT NULL DEFAULT 'INHERIT'")
                database.execSQL("ALTER TABLE profiles ADD COLUMN password_access_mode TEXT NOT NULL DEFAULT 'PIN_REQUIRED'")
            }
        }

        //  Если у вас были миграции 6->7 и 7->8, добавьте их сюда через запятую:
        // .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
        // Для чистой установки или если старых миграций не было, оставляем только 8->9.

        fun getDatabase(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "securevault_database"
                )
                .addMigrations(MIGRATION_8_9)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
